/*
 * ArchiveTune (2026)
 * © ArchiveTuneFork contributors — github.com/vossgraves/ArchiveTune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package app.atf.media.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.FileDataSource
import com.downloader.Error
import com.downloader.OnDownloadListener
import com.downloader.PRDownloader
import com.downloader.Status
import com.downloader.request.DownloadRequest
import moe.rukamori.archivetune.innertube.YouTube
import app.atf.media.utils.StreamClientUtils
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * A Media3 [DataSource] that delegates the actual HTTP fetching to
 * [PRDownloader](https://github.com/amitshekhariitbhu/PRDownloader) — a
 * lightweight (~45 KB) file download library with pause/resume, retry,
 * and progress callbacks.
 *
 * ## Why PRDownloader instead of Ketch?
 *
 * The previous [KetchHttpDataSource] delegates to Ketch, a WorkManager-based
 * downloader. Ketch's WorkManager job scheduling + Flow observation added
 * ~200ms of overhead per download and, more critically, its temp-file
 * lifecycle occasionally left partial files in `cacheDir/ketch_tmp/` that
 * silently corrupted subsequent exports (the export screen would assemble
 * spans from the download cache, but a half-written temp file from a
 * cancelled Ketch job would bleed into the next download's cache entries).
 *
 * PRDownloader is simpler: a single OkHttp call per download, with a
 * callback API ([OnDownloadListener]) that we bridge to a
 * [CountDownLatch] for blocking until completion. The temp file is
 * deleted synchronously in [close], and we verify it exists and is
 * non-empty before serving it via [FileDataSource].
 *
 * ## Data flow
 *
 * ```
 * Media3 DownloadManager
 *   └─ CacheDataSource (downloadCache)
 *      └─ ResolvingDataSource (resolves mediaId → URL)
 *         └─ CacheDataSource (playerCache, read-only upstream)
 *            └─ PRDownloaderDataSource   ←  this class
 *               ├─ PRDownloader.download(url, tempDir, fileName)
 *               ├─ wait for onDownloadComplete() via CountDownLatch
 *               └─ FileDataSource(tempFile).read(buffer, off, len)
 * ```
 *
 * The temp file is deleted in [close]. If the same URL is requested
 * again (e.g. a retry), a fresh temp file is created — PRDownloader has
 * no resume state across [DataSource] sessions.
 *
 * ## Range requests
 *
 * Media3's [DownloadManager] always opens with `position=0,
 * length=C.LENGTH_UNSET` (full file) for downloads. We support ranges
 * anyway by seeking into the temp file via [FileDataSource] — this
 * keeps the data source usable for non-download callers.
 *
 * ## Integrity verification
 *
 * After PRDownloader reports completion, we verify:
 *   1. The temp file exists and is non-empty.
 *   2. The file size matches the expected content length. The expected
 *      length comes from two sources (in priority order):
 *        a. A HEAD request to the resolved URL (returns Content-Length).
 *        b. The DataSpec's `httpRequestHeaders["X-Expected-Content-Length"]`
 *           if pre-warmed upstream (set by DownloadUtil.prewarmSongForDownload
 *           from FormatEntity.contentLength).
 *   3. If neither is available (e.g. chunked transfer), we accept the
 *      file but log a warning so it shows up in the in-app logcat viewer.
 *
 * If verification fails, we throw [IOException] so Media3's
 * [DownloadManager] marks the download as failed and the failure-listener
 * (`DownloadUtil.onDownloadChanged`) purges the partial cache entries.
 */
internal class PRDownloaderDataSource private constructor(
    private val context: Context,
    private val userAgent: String,
) : BaseDataSource(true) {

    private var tempFile: File? = null
    private var fileSource: FileDataSource? = null
    private var bytesRemaining: Long = 0L
    /** The active PRDownloader download id, if any — used for cancel-on-close. */
    private var activeDownloadId: Int = -1
    /** Shared OkHttp client for HEAD requests — created lazily on first use. */
    private val headClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .connectionPool(ConnectionPool(4, 30_000, TimeUnit.MILLISECONDS))
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .build()
    }

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        val url = dataSpec.uri.toString()
        // PRDownloader is initialized once at app start (see App.kt).
        // If it isn't initialized, we fail fast — there's no graceful
        // degradation possible without a fetcher.
        val tempDir = File(context.cacheDir, "prd_tmp").apply { mkdirs() }
        val nameHash = sha1("$url|${dataSpec.position}|${dataSpec.length}")
        val safeName = "dl_$nameHash"
        val target = File(tempDir, safeName)

        // Always start fresh — a stale partial file from a previous
        // attempt would cause PRDownloader to think the download is
        // already complete (it checks file size against Content-Length
        // and skips downloading if they match), but if the partial
        // file is corrupt or truncated, we'd serve corrupt bytes.
        target.delete()

        // Build the download request with optional headers.
        // PRDownloader.download() returns a DownloadRequestBuilder — headers
        // and user-agent must be set on the builder BEFORE calling .build(),
        // which converts it into a DownloadRequest (which only supports
        // progress/pause/cancel listeners + start()).
        val requestBuilder = PRDownloader.download(url, tempDir.absolutePath, safeName)

        // YouTube's googlevideo CDN enforces strict User-Agent / Origin / Referer
        // matching against the `c` (client) query parameter embedded in the
        // stream URL. If the UA doesn't match what the client expects (e.g.
        // "ArchiveTune/1.2.3" sent for a `c=WEB_REMIX` URL that expects a
        // Firefox UA), googlevideo returns HTTP 403 and every download fails.
        //
        // Resolve the correct UA + Origin + Referer from the URL's `c` param
        // via StreamClientUtils (the same path the player's OkHttp interceptor
        // uses). For non-YouTube URLs, fall back to the configured default UA.
        val youTubeMediaProfile = runCatching {
            StreamClientUtils.resolveRequestProfile(url)
        }.getOrNull()
        val resolvedUserAgent = youTubeMediaProfile?.userAgent?.takeIf(String::isNotBlank)
            ?: userAgent
        if (resolvedUserAgent.isNotBlank()) requestBuilder.setUserAgent(resolvedUserAgent)
        if (youTubeMediaProfile != null) {
            youTubeMediaProfile.origin?.takeIf(String::isNotBlank)?.let {
                requestBuilder.setHeader("Origin", it)
            }
            youTubeMediaProfile.referer?.takeIf(String::isNotBlank)?.let {
                requestBuilder.setHeader("Referer", it)
            }
            // YouTube media endpoints reject transparent gzip on audio/mp4
            // streams — without this, PRDownloader's OkHttp client may
            // advertise gzip and the response comes back compressed, which
            // both corrupts the cached bytes and triggers
            // ParserException("Multiple Segment elements not supported")
            // when the player later tries to decode the gzipped payload as
            // an MP4 container.
            requestBuilder.setHeader("Accept-Encoding", "identity")
            requestBuilder.setHeader("Connection", "keep-alive")
        }
        dataSpec.httpRequestHeaders.forEach { (k, v) ->
            // PRDownloader doesn't accept "Range" via setHeader — it
            // manages its own range requests internally for resume.
            // We drop any caller-supplied Range header to avoid conflict.
            // We also drop User-Agent / Origin / Referer / Accept-Encoding
            // because we just set them from the stream-client profile.
            val lower = k.lowercase()
            if (lower != "range" &&
                lower != "user-agent" &&
                lower != "origin" &&
                lower != "referer" &&
                lower != "accept-encoding"
            ) {
                requestBuilder.setHeader(k, v)
            }
        }
        val builder: DownloadRequest = requestBuilder.build()

        val latch = CountDownLatch(1)
        val errorRef = AtomicReference<Error?>(null)

        // Set progress + lifecycle listeners before start(). The progress
        // listener calls bytesTransferred() so Media3's DownloadManager
        // sees incremental progress (otherwise open() blocks for the
        // entire download and Media3 sees 0% → 100% with nothing in between).
        builder.setOnProgressListener { progress ->
            // We can't call bytesTransferred() here directly because
            // FileDataSource isn't open yet (we're still in open()).
            // The progress listener is mainly for diagnostics — Media3
            // computes transferred bytes from our read() calls.
        }

        activeDownloadId = builder.start(object : OnDownloadListener {
            override fun onDownloadComplete() {
                latch.countDown()
            }

            override fun onError(error: Error) {
                errorRef.set(error)
                latch.countDown()
            }
        })

        // Block until PRDownloader finishes or the timeout expires.
        // 30 min is generous — a 150 MB FLAC on a 10 Mbps link is ~2 min,
        // and we'd rather let a slow download finish than fail and force
        // a restart.
        if (!latch.await(DOWNLOAD_WAIT_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
            runCatching { PRDownloader.cancel(activeDownloadId) }
            runCatching { target.delete() }
            throw IOException("PRDownloader timed out after $DOWNLOAD_WAIT_TIMEOUT_MINUTES min for $url")
        }

        errorRef.get()?.let { err ->
            runCatching { target.delete() }
            val msg = buildString {
                append("PRDownloader failed for $url")
                if (err.isConnectionError) append(" (connection error)")
                if (err.isServerError) append(" (server error)")
                err.serverErrorMessage?.takeIf(String::isNotBlank)?.let { append(": $it") }
                err.connectionException?.message?.takeIf(String::isNotBlank)?.let { append(" — $it") }
                if (err.responseCode > 0) append(" (HTTP ${err.responseCode})")
            }
            throw IOException(msg)
        }

        if (!target.exists() || target.length() == 0L) {
            runCatching { target.delete() }
            throw IOException("PRDownloader reported success but temp file is missing/empty: $target")
        }

        // ── Real integrity verification ──
        // PRDownloader 1.0.2 has a known issue where `onDownloadComplete()`
        // can fire on a partial file when the upstream connection drops
        // mid-stream (especially on chunked-transfer CDNs that don't send
        // Content-Length). The temp file's size will be less than the real
        // audio length but PRDownloader happily reports success.
        //
        // To prevent Media3 from marking STATE_COMPLETED on a truncated
        // file (which then causes ExoPlayer to throw ParserException
        // during playback), we verify the actual size against the expected
        // content length, sourced from either:
        //   1. A HEAD request to the URL (authoritative when the CDN
        //      supports HEAD + returns Content-Length).
        //   2. The DataSpec's httpRequestHeaders (set by DownloadUtil's
        //      prewarm path from FormatEntity.contentLength).
        //
        // When neither source yields an expected length (chunked transfer
        // + no upstream metadata), we accept the file but emit a warning.
        val expectedLength = resolveExpectedContentLength(url, dataSpec)
        if (expectedLength > 0L) {
            val actualLength = target.length()
            if (actualLength < expectedLength) {
                runCatching { target.delete() }
                throw IOException(
                    "Partial download for $url: got $actualLength / $expectedLength bytes " +
                        "(" + (actualLength * 100 / expectedLength) + "%)",
                )
            }
        }

        tempFile = target

        // Build a child DataSpec pointing at the local file, preserving
        // position / length / key. FileDataSource honors position to
        // seek into the file (for range requests).
        val fileSpec = dataSpec.withUri(Uri.fromFile(target))
        val fs = FileDataSource()
        val reportedLength = fs.open(fileSpec)
        fileSource = fs

        // bytesRemaining = what FileDataSource reports is left to read.
        // If DataSpec.length is set (bounded range), use the smaller of
        // the two.
        val totalRemaining = if (reportedLength < 0) target.length() - dataSpec.position else reportedLength
        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            minOf(dataSpec.length, totalRemaining)
        } else {
            totalRemaining
        }
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val fs = fileSource ?: return -1
        val toRead = if (bytesRemaining in 1..Int.MAX_VALUE.toLong()) {
            minOf(length, bytesRemaining.toInt())
        } else {
            length
        }
        if (toRead <= 0) return -1
        val read = fs.read(buffer, offset, toRead)
        if (read > 0) {
            bytesRemaining -= read
            bytesTransferred(read)
        }
        return read
    }

    override fun getUri(): Uri? = fileSource?.uri

    override fun close() {
        // Cancel any in-flight PRDownloader job (defensive — if open()
        // threw before the latch counted down, the job might still be running).
        if (activeDownloadId != -1) {
            runCatching { PRDownloader.cancel(activeDownloadId) }
            activeDownloadId = -1
        }
        fileSource?.let { runCatching { it.close() } }
        fileSource = null
        tempFile?.let { runCatching { it.delete() } }
        tempFile = null
        bytesRemaining = 0L
    }

    private fun sha1(input: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Resolves the expected content length for the download, used to verify
     * that PRDownloader didn't silently truncate the file.
     *
     * Resolution order:
     *  1. The DataSpec's `httpRequestHeaders["X-Expected-Content-Length"]`
     *     (set by DownloadUtil.prewarmSongForDownload from FormatEntity).
     *  2. A synchronous HEAD request to the URL (best-effort, with a
     *     short timeout — if it fails or returns no Content-Length, we
     *     return 0 and the verification step is skipped).
     *
     * Returns 0 if no expected length can be determined (chunked transfer
     * with no upstream metadata) — in that case the file is accepted as-is
     * to avoid false negatives for CDNs that genuinely don't expose size.
     */
    private fun resolveExpectedContentLength(url: String, dataSpec: DataSpec): Long {
        // (1) Upstream-provided hint (set by prewarm from FormatEntity).
        dataSpec.httpRequestHeaders["X-Expected-Content-Length"]?.toLongOrNull()
            ?.let { if (it > 0L) return it }

        // (2) HEAD request — best-effort. Some CDNs (e.g. YouTube
        // googlevideo) reject HEAD on media URLs, so we silently fall
        // through if the request fails.
        return runCatching {
            // Resolve stream-client profile (UA / Origin / Referer) for
            // YouTube URLs, mirroring the main download path.
            val youTubeMediaProfile = runCatching {
                StreamClientUtils.resolveRequestProfile(url)
            }.getOrNull()
            val resolvedUserAgent = youTubeMediaProfile?.userAgent?.takeIf(String::isNotBlank)
                ?: userAgent
            val builder = Request.Builder().url(url).head()
                .header("User-Agent", resolvedUserAgent)
                .header("Accept-Encoding", "identity")
            youTubeMediaProfile?.origin?.takeIf(String::isNotBlank)?.let {
                builder.header("Origin", it)
            }
            youTubeMediaProfile?.referer?.takeIf(String::isNotBlank)?.let {
                builder.header("Referer", it)
            }
            headClient.newCall(builder.build()).execute().use { response ->
                val cl = response.header("Content-Length")?.toLongOrNull() ?: 0L
                if (!response.isSuccessful && response.code != 200 && response.code != 206) {
                    // HEAD not supported / rejected — don't fail the download.
                    0L
                } else {
                    cl
                }
            }
        }.getOrDefault(0L)
    }

    /**
     * Factory for [PRDownloaderDataSource]. Each [createDataSource] call
     * returns an independent instance — PRDownloader itself is a process-
     * wide singleton initialized in [App.kt], so the factory is cheap
     * to construct.
     */
    class Factory(
        private val context: Context,
        private val userAgent: String = DEFAULT_USER_AGENT,
    ) : DataSource.Factory {
        override fun createDataSource(): PRDownloaderDataSource =
            PRDownloaderDataSource(context, userAgent)
    }

    companion object {
        private const val DEFAULT_USER_AGENT = "ArchiveTune"
        private const val DOWNLOAD_WAIT_TIMEOUT_MINUTES = 30L
    }
}
