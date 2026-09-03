/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.telegram

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Loads TDLib's native library, fetching it on demand when the build did not bundle it.
 *
 * Why this exists: `libtdjni.so` is 21.7 MB per ABI — 8.7 MB compressed into the APK and the full
 * 21.7 MB extracted on install, since the app packages native libs with `useLegacyPackaging`. That
 * is paid by every user, while Telegram is an optional integration most never sign in to. A build
 * made with `-PslimTdlib=true` omits it and this fetches it the first time someone actually opens
 * Telegram.
 *
 * The default build still bundles it, so nothing changes unless that flag is set — see
 * [BuildConfig.TDLIB_BUNDLED].
 *
 * Not Play Feature Delivery: that needs App Bundles and Play, and this app ships APKs on GitHub
 * releases. The download is therefore hand-rolled, which is also why the digests below are
 * compiled in rather than fetched — a manifest downloaded over the same channel as the payload
 * verifies nothing.
 */
object TdLibNativeLibrary {
    private const val TAG = "TdLibNative"

    /** Must match the `com.github.tdlibx:td` version in app/build.gradle.kts. */
    const val VERSION = "1.8.56"

    private const val LIB_NAME = "tdjni"
    private const val FILE_NAME = "libtdjni.so"

    /**
     * SHA-256 of each ABI's library as shipped in the td AAR, taken from the artifact this build
     * resolves. A download that does not match one of these is discarded — the digests are the
     * only thing standing between the app and whatever the release host serves.
     */
    private val DIGESTS =
        mapOf(
            "arm64-v8a" to "7c1751197b35a64261e3b3f21764874c9ee8795e4b6118c23a74499426c44b91",
            "armeabi-v7a" to "56bcd646dae3442a2aeefee3ce28b72c14dc257488d267d4ed76e7e01e08f158",
            "x86" to "4c1d128b862a35c293dc96a20cb9f41ffa33144c80b9ded858028bc3f9ca93ec",
            "x86_64" to "567bb5aaccdcc1d8280577f2f9fe8e82178908c72f513436972493fd6ad6dabd",
        )

    @Volatile
    private var loaded = false

    private val client =
        OkHttpClient
            .Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            // No callTimeout: this is a 21 MB body and the deadline that matters is per-read.
            .build()

    /**
     * The device's ABI, as one of the four the AAR ships. `SUPPORTED_ABIS` is ordered best-first,
     * so a 64-bit device that also lists armeabi-v7a still picks arm64-v8a.
     */
    private val abi: String?
        get() = Build.SUPPORTED_ABIS.firstOrNull { it in DIGESTS }

    private fun target(context: Context): File =
        File(File(context.filesDir, "tdlib-native"), "$VERSION-${abi.orEmpty()}-$FILE_NAME")

    /** True once the library is usable in this process. */
    val isLoaded: Boolean get() = loaded

    /** True when the library still has to be downloaded before Telegram can start. */
    fun needsDownload(context: Context): Boolean =
        !loaded && !BuildConfig.TDLIB_BUNDLED && !target(context).isFile

    /**
     * Loads the library if it can be, without touching the network. Returns false on a slim build
     * that has not fetched it yet — the caller should offer [download].
     */
    @Synchronized
    fun ensureLoaded(context: Context): Boolean {
        if (loaded) return true

        // Bundled build: it is inside the APK, and the linker already knows where to find it.
        // Tried first (and unconditionally) so a build that reverts the slim flag keeps working
        // even with a stale download still sitting in filesDir.
        if (runCatching { System.loadLibrary(LIB_NAME) }.isSuccess) {
            loaded = true
            return true
        }

        val file = target(context)
        if (!file.isFile) return false
        // Re-verify on every cold start rather than trusting the file's presence: it is loaded as
        // executable code, and a truncated write or a tampered file is exactly what must not run.
        if (!matchesDigest(file)) {
            Timber.tag(TAG).w("Cached %s failed its digest check; deleting", file.name)
            file.delete()
            return false
        }
        return runCatching {
            System.load(file.absolutePath)
            loaded = true
            true
        }.getOrElse {
            Timber.tag(TAG).e(it, "Loading %s failed", file.absolutePath)
            false
        }
    }

    /**
     * Downloads the library for this device's ABI, verifies it, and loads it.
     *
     * [onProgress] receives 0f..1f, or -1f while the total size is unknown. Returns false on any
     * failure; the partial file is always cleaned up, so a retry starts clean rather than
     * resuming into a file whose first half came from a different response.
     */
    suspend fun download(
        context: Context,
        onProgress: (Float) -> Unit = {},
    ): Boolean =
        withContext(Dispatchers.IO) {
            if (ensureLoaded(context)) return@withContext true

            val abi = abi
            if (abi == null) {
                Timber.tag(TAG).e("No supported ABI among %s", Build.SUPPORTED_ABIS.joinToString())
                return@withContext false
            }
            val base = BuildConfig.TDLIB_NATIVE_BASE_URL.trim().trimEnd('/')
            if (base.isEmpty()) {
                Timber.tag(TAG).e("This build has no TDLIB_NATIVE_BASE_URL to download from")
                return@withContext false
            }

            val url = "$base/libtdjni-$VERSION-$abi.so"
            val destination = target(context)
            destination.parentFile?.mkdirs()
            val partial = File(destination.absolutePath + ".part")
            partial.delete()

            val ok =
                runCatching {
                    client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                        if (!response.isSuccessful) {
                            Timber.tag(TAG).e("Download of %s failed: HTTP %d", url, response.code)
                            return@use false
                        }
                        val body = response.body ?: return@use false
                        val total = body.contentLength()
                        var read = 0L
                        body.byteStream().use { input ->
                            partial.outputStream().use { output ->
                                val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
                                while (true) {
                                    val n = input.read(buffer)
                                    if (n < 0) break
                                    output.write(buffer, 0, n)
                                    read += n
                                    onProgress(if (total > 0) read.toFloat() / total else -1f)
                                }
                            }
                        }
                        true
                    }
                }.getOrElse {
                    Timber.tag(TAG).e(it, "Download of %s failed", url)
                    false
                }

            if (!ok || !matchesDigest(partial)) {
                if (ok) Timber.tag(TAG).e("Downloaded %s did not match its expected digest", url)
                partial.delete()
                return@withContext false
            }

            // Rename only after the digest passes, so `destination` never exists in a bad state
            // and ensureLoaded can treat its presence as "worth verifying" rather than "unknown".
            if (!partial.renameTo(destination)) {
                Timber.tag(TAG).e("Could not move the verified library into place")
                partial.delete()
                return@withContext false
            }
            ensureLoaded(context)
        }

    private fun matchesDigest(file: File): Boolean {
        val expected = DIGESTS[abi] ?: return false
        val digest =
            runCatching {
                val md = MessageDigest.getInstance("SHA-256")
                file.inputStream().use { input ->
                    val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        md.update(buffer, 0, n)
                    }
                }
                md.digest().joinToString("") { "%02x".format(it) }
            }.getOrNull() ?: return false
        return digest.equals(expected, ignoreCase = true)
    }

    private const val DOWNLOAD_BUFFER_BYTES = 64 * 1024
}
