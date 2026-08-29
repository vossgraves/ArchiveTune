/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.applemusic

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import moe.rukamori.archivetune.canvas.AppleMusicProvider
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Apple Music full-track playback source.
 *
 * Resolution pipeline (all verified against the live API with a Media-User-Token):
 *  1. Catalog search via the public AMP API (`/v1/catalog/{storefront}/search`) using the
 *     dev (Bearer) JWT + Media-User-Token — same endpoints as the canvas/lyrics providers.
 *  2. `POST https://play.itunes.apple.com/WebObjects/MZPlay.woa/wa/webPlayback` with
 *     `{"salableAdamId": <songId>}` + both tokens → per-flavor asset list. The `ctrp`
 *     (AES-CTR "clear-mode") flavors are the playable ones; `cbcp` flavors are FairPlay
 *     (`skd://`) and cannot be played on Android.
 *  3. The chosen asset URL serves an HLS playlist whose segments are byteranges of a single
 *     encrypted CENC fMP4 file (`METHOD=ISO-23001-7`). [AppleMusicProgressiveDataSource]
 *     serves that file progressively; the decryption key is obtained at the codec level via
 *     a Widevine L3 license exchange against the response's `hls-key-server-url`
 *     (`wa/acquireWebPlaybackLicense`) — the same protocol Chrome/EME speaks.
 *
 * The Media-User-Token must belong to an account with an active Apple Music subscription.
 */
object AppleMusicAudioProvider {
    private const val TAG = "AppleMusicSource"

    private const val AMP_BASE = "https://amp-api.music.apple.com"
    private const val WEB_PLAYBACK_URL =
        "https://play.itunes.apple.com/WebObjects/MZPlay.woa/wa/webPlayback"
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/145.0.0.0 Safari/537.36"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client =
        OkHttpClient
            .Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .build()

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    /** Storefront resolved from the Media-User-Token (`/v1/me/storefront`), cached 24 h. */
    private const val STOREFRONT_TTL_MS = 24 * 60 * 60 * 1000L
    @Volatile private var cachedStorefront: String? = null
    @Volatile private var cachedStorefrontAtMs = 0L
    private val storefrontMutex = Mutex()

    /** Resolved storefront for the token (es/jp/…); "us" when it cannot be determined. */
    suspend fun resolveStorefront(): String {
        val now = System.currentTimeMillis()
        cachedStorefront?.let { if (now - cachedStorefrontAtMs < STOREFRONT_TTL_MS) return it }
        return storefrontMutex.withLock {
            val stillFresh = cachedStorefront?.takeIf { now - cachedStorefrontAtMs < STOREFRONT_TTL_MS }
            if (stillFresh != null) return@withLock stillFresh
            val media = mediaUserToken()?.takeIf { it.isNotBlank() } ?: return@withLock cachedStorefront ?: "us"
            val dev = devToken() ?: return@withLock cachedStorefront ?: "us"
            fetchedStorefront(media, dev)?.let { fetched ->
                cachedStorefront = fetched
                cachedStorefrontAtMs = System.currentTimeMillis()
                return@withLock fetched
            }
            cachedStorefront ?: "us"
        }
    }

    private suspend fun fetchedStorefront(mediaToken: String, devToken: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val request =
                    Request.Builder()
                        .url("$AMP_BASE/v1/me/storefront")
                        .header("Authorization", "Bearer $devToken")
                        .header("Media-User-Token", mediaToken)
                        .header("Origin", "https://music.apple.com")
                        .header("Referer", "https://music.apple.com/")
                        .header("User-Agent", UA)
                        .get()
                        .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "storefront fetch failed: %d".format(response.code))
                        return@use null
                    }
                    val root = json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
                    root["data"]?.jsonArray?.firstOrNull()
                        ?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
                }
            }.getOrNull()
        }

    fun devToken(): String? = AppleMusicProvider.devTokenProvider?.invoke()?.trim()?.takeIf { it.isNotBlank() }

    fun mediaUserToken(): String? = AppleMusicProvider.mediaUserTokenProvider?.invoke()?.trim()?.takeIf { it.isNotBlank() }

    /** True when both tokens are present — the source cannot resolve anything otherwise. */
    fun isAvailable(): Boolean = devToken() != null && mediaUserToken() != null

    /**
     * One playable Apple Music candidate. [playlistUrl] serves the HLS playlist whose
     * segments are byteranges of the single encrypted fMP4 at [mediaUrl].
     */
    data class AppleMusicStream(
        val songId: String,
        val playlistUrl: String,
        val mediaUrl: String,
        val licenseUrl: String,
        val keyIdHex: String?,
        val flavor: String,
        val contentLength: Long?,
        val matchedTitle: String,
        val matchedArtist: String?,
        val matchedAlbum: String?,
        val matchedDurationMs: Long?,
    )

    /**
     * Search the catalog and resolve every plausible candidate to a playable stream. The
     * caller applies the shared metadata-match gate ([moe.rukamori.archivetune.audiosource
     * .TitleMatch]) to pick the winner, so candidates are returned in search-rank order.
     * Empty when the tokens are missing/unauthorized or nothing resolved.
     */
    suspend fun resolveCandidates(
        title: String,
        artists: List<String>,
        album: String?,
        durationMs: Long?,
    ): List<AppleMusicStream> =
        withContext(Dispatchers.IO) {
            runCatching {
                val mediaToken = mediaUserToken() ?: return@runCatching emptyList()
                val devToken = devToken() ?: return@runCatching emptyList()
                val storefront = resolveStorefront()

                val query = if (title.contains(artists.firstOrNull().orEmpty(), ignoreCase = true)) {
                    title
                } else {
                    "${artists.firstOrNull().orEmpty()} $title".trim()
                }
                val songIds = searchSongIds(query, storefront, devToken, mediaToken)
                if (songIds.isEmpty()) return@runCatching emptyList()

                // Resolve up to the first few candidates; stop early once we have three
                // playable ones (webPlayback is the expensive call).
                val out = mutableListOf<AppleMusicStream>()
                for (id in songIds.take(5)) {
                    if (out.size >= 3) break
                    webPlayback(id, devToken, mediaToken, storefront)?.let { out += it }
                }
                out
            }.onFailure { Log.w(TAG, "resolve failed: ${it.message}") }.getOrDefault(emptyList())
        }

    /** Top catalog song ids for [query], in Apple's search-rank order. */
    private fun searchSongIds(
        query: String,
        storefront: String,
        devToken: String,
        mediaToken: String,
    ): List<String> {
        val url =
            "$AMP_BASE/v1/catalog/$storefront/search".toHttpUrl()
                .newBuilder()
                .addQueryParameter("term", query)
                .addQueryParameter("types", "songs")
                .addQueryParameter("limit", "5")
                .build()
        val request =
            Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $devToken")
                .header("Media-User-Token", mediaToken)
                .header("Origin", "https://music.apple.com")
                .header("Referer", "https://music.apple.com/")
                .header("User-Agent", UA)
                .get()
                .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "search failed: %d".format(response.code))
                return emptyList()
            }
            val root = json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
            val songs =
                root["results"]?.jsonObject?.get("songs")?.jsonObject?.get("data")?.jsonArray
                    ?: return emptyList()
            return songs.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull }
        }
    }

    /** Resolve one catalog id through the web-playback endpoint to a playable playlist. */
    private fun webPlayback(
        songId: String,
        devToken: String,
        mediaToken: String,
        storefront: String,
    ): AppleMusicStream? {
        val body = """{"salableAdamId":$songId,"language":"en-us"}""".toRequestBody(JSON_MEDIA)
        val request =
            Request.Builder()
                .url(WEB_PLAYBACK_URL)
                .post(body)
                .header("Authorization", "Bearer $devToken")
                .header("Media-User-Token", mediaToken)
                .header("Origin", "https://music.apple.com")
                .header("Referer", "https://music.apple.com/")
                .header("User-Agent", UA)
                .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "webPlayback failed for %s: %d".format(songId, response.code))
                return null
            }
            val root = runCatching { json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject }
                .getOrNull() ?: return null
            val song = root["songList"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
            val licenseUrl = song["hls-key-server-url"]?.jsonPrimitive?.contentOrNull
            val assets = song["assets"]?.jsonArray ?: return null

            // Pick the highest-bitrate ctrp (AES-CTR) asset. cbcp flavors are FairPlay
            // (`skd://` keys) and cannot be decrypted on Android.
            data class Asset(val flavor: String, val url: String, val kbps: Int)

            val candidates =
                assets.mapNotNull { element ->
                    val asset = element.jsonObject
                    val flavor = asset["flavor"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val url = asset["URL"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    if (!flavor.contains("ctrp")) return@mapNotNull null
                    val kbps = Regex("(\\d+)$").find(flavor)?.groupValues?.last()?.toIntOrNull() ?: 0
                    Asset(flavor, url, kbps)
                }.sortedByDescending { it.kbps }
            val asset = candidates.firstOrNull() ?: return null

            // The asset URL serves the HLS playlist; segments are byteranges of one mp4.
            val playlistUrl = asset.url
            val parsed = parsePlaylist(playlistUrl) ?: return null
            val metadata = song["metadata"] as? JsonObject
            return AppleMusicStream(
                songId = songId,
                playlistUrl = playlistUrl,
                mediaUrl = parsed.mediaUrl,
                licenseUrl = licenseUrl ?: WEB_PLAYBACK_URL.replace("webPlayback", "acquireWebPlaybackLicense"),
                keyIdHex = parsed.keyIdHex,
                flavor = asset.flavor,
                contentLength = parsed.contentLength,
                matchedTitle = metadata?.title() ?: songId,
                matchedArtist = metadata?.artist(),
                matchedAlbum = metadata?.album(),
                matchedDurationMs = metadata?.durationMs(),
            )
        }
    }

    private fun JsonObject.title(): String? = this["title"]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.artist(): String? =
        this["artistName"]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.album(): String? =
        this["albumName"]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.durationMs(): Long? =
        this["trackDuration"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()

    private class ParsedPlaylist(
        val mediaUrl: String,
        val keyIdHex: String?,
        val contentLength: Long?,
    )

    /**
     * Fetch the playlist and derive the absolute mp4 URL. The playlist's segments are
     * byteranges of a single fMP4 next to it; the EXT-X-KEY `data:` URI carries the KID
     * (verified to equal the tenc default_KID), and `#EXT-X-MAP` names the mp4 file.
     */
    private fun parsePlaylist(playlistUrl: String): ParsedPlaylist? {
        val request = Request.Builder().url(playlistUrl).header("User-Agent", UA).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "playlist fetch failed: %d".format(response.code))
                return null
            }
            val text = response.body?.string().orEmpty()
            var keyIdHex: String? = null
            var mediaName: String? = null
            val dataLines = mutableListOf<String>()
            for (rawLine in text.lineSequence()) {
                val line = rawLine.trim()
                when {
                    line.startsWith("#EXT-X-KEY") && keyIdHex == null -> {
                        Regex("URI=\"data:[^\"]*base64,([^\"]+)\"").find(line)?.let { match ->
                            keyIdHex =
                                runCatching {
                                    java.util.Base64.getDecoder().decode(match.groupValues[1].trim())
                                }.getOrNull()?.joinToString("") { "%02x".format(it) }
                        }
                    }
                    line.startsWith("#EXT-X-MAP") && mediaName == null -> {
                        Regex("URI=\"([^\"]+)\"").find(line)?.let { match -> mediaName = match.groupValues[1] }
                    }
                    !line.startsWith("#") && line.isNotBlank() -> dataLines += line
                }
            }
            // Prefer the EXT-X-MAP name; segments reference the same file.
            val name = mediaName ?: dataLines.firstOrNull() ?: return null
            val mediaUrl = playlistUrl.substringBeforeLast('/').trimEnd('/') + "/" + name
            return ParsedPlaylist(mediaUrl, keyIdHex, null)
        }
    }
}
