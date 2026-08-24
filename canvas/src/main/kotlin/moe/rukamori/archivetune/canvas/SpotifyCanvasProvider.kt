/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.canvas

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import moe.rukamori.archivetune.canvas.models.CanvasArtwork
import java.util.concurrent.ConcurrentHashMap

/**
 * Fetches Spotify Canvas looping videos for the currently playing song.
 *
 * Two sources are tried, in order:
 *
 * 1. **Spotify's own Canvas endpoint** (`spclient.wg.spotify.com/canvaz-cache`).
 *    This is the authoritative source — it is the same endpoint the Spotify
 *    clients use, and it returns the real canvas mp4. It needs a Spotify access
 *    token and the song's `spotify:track:<id>` URI, both supplied by the host app
 *    through [tokenProvider] / [trackUriResolver] (the canvas module deliberately
 *    has no dependency on the app's Spotify or player code). When the user has no
 *    Spotify session this source is simply unavailable.
 *
 * 2. **The `mlc-ytify.kouzu.in` resolver**, keyed by YouTube Music video ID.
 *    Kept as a fallback for users without a Spotify login. Note that this
 *    resolver serves canvases only out of its own cache and currently reports
 *    zero cached canvases (`/api/stats` → `"canvas": 0`), answering every lookup
 *    with `404 {"detail":"No cached canvas"}` — which is why Spotify Canvas
 *    appeared completely broken when it was the *only* source.
 *
 * The `x-request-source: muzo` header is required on every kouzu.in request —
 * without it the server rate-limits the client. It is injected centrally by the
 * `MusicService.mediaOkHttpClient` interceptor for kouzu.in hosts, but this
 * provider uses its own Ktor client, so the header is added here via
 * `defaultRequest`.
 *
 * Results are cached in-memory for 1 hour per video ID to avoid hammering either
 * source on every recomposition / replay. A negative result is cached too, so a
 * song with no canvas doesn't re-query on every replay.
 */
object SpotifyCanvasProvider {
    /**
     * Fallback resolver base URL. The full URL is `$BASE_URL?id=<videoId>`.
     */
    private const val BASE_URL = "https://mlc-ytify.kouzu.in/api/canvas"

    /** Spotify's Canvas endpoint. Speaks protobuf — see [SpotifyCanvazProtocol]. */
    private const val CANVAZ_URL = "https://spclient.wg.spotify.com/canvaz-cache/v0/canvases"

    private const val CACHE_TTL_MS = 60L * 60 * 1000 // 1 hour

    /**
     * Supplies a Spotify access token, or null when the user has no Spotify
     * session. Set by the host app (see `App.kt`); left null in tests and in
     * standalone use of this module.
     */
    @Volatile
    var tokenProvider: (suspend () -> String?)? = null

    /**
     * Maps the currently playing song to a `spotify:track:<id>` URI, or null when
     * it can't be identified on Spotify. Set by the host app.
     */
    @Volatile
    var trackUriResolver: (suspend (videoId: String, title: String?, artist: String?) -> String?)? = null

    /** Optional diagnostic sink, mirroring [AppleMusicProvider.logger]. */
    @Volatile
    var logger: ((message: String) -> Unit)? = null

    private fun log(message: String) {
        logger?.invoke(message)
    }

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
        }

    private val client by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout) {
                connectTimeoutMillis = 10_000
                requestTimeoutMillis = 15_000
                socketTimeoutMillis = 15_000
            }
            install(ContentEncoding) {
                gzip()
                deflate()
            }
            install(HttpCache)
            // The x-request-source: muzo header is REQUIRED on every kouzu.in
            // request — without it the server rate-limits the client aggressively.
            defaultRequest {
                header("x-request-source", "muzo")
                header("User-Agent", "ArchiveTune-Android")
                header("Accept", "application/json")
            }
            expectSuccess = false
        }
    }

    /**
     * Separate client for Spotify's Canvas endpoint.
     *
     * Deliberately not [client]: that one's `defaultRequest` block pins
     * `x-request-source: muzo` and `Accept: application/json` for the kouzu.in
     * resolver. Sending the muzo header to Spotify would be wrong, and the JSON
     * Accept header would fight the protobuf one this endpoint needs.
     */
    private val spotifyClient by lazy {
        HttpClient(OkHttp) {
            install(HttpTimeout) {
                connectTimeoutMillis = 10_000
                requestTimeoutMillis = 15_000
                socketTimeoutMillis = 15_000
            }
            expectSuccess = false
        }
    }

    private data class CacheEntry(
        val value: CanvasArtwork?,
        val expiresAtMs: Long,
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    /**
     * Looks up the Spotify Canvas for the song identified by [videoId] (the
     * YouTube Music video ID of the currently playing song). [songTitle] and
     * [artistName] are used only to identify the song on Spotify for the official
     * endpoint; the fallback resolver keys off [videoId] alone.
     *
     * Returns `null` if neither source has a canvas for the song, the song isn't
     * on Spotify, or both requests fail.
     */
    suspend fun getByVideoId(
        videoId: String,
        songTitle: String? = null,
        artistName: String? = null,
    ): CanvasArtwork? {
        if (videoId.isBlank()) return null

        cache[videoId]?.let { entry ->
            if (entry.expiresAtMs > System.currentTimeMillis()) return entry.value
            cache.remove(videoId)
        }

        // Source 1: Spotify's own Canvas endpoint.
        val official =
            try {
                fetchOfficialCanvas(videoId, songTitle, artistName)
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                log("Official Spotify Canvas lookup failed for $videoId: ${throwable.message}")
                null
            }
        if (official != null) {
            cache[videoId] = CacheEntry(official, System.currentTimeMillis() + CACHE_TTL_MS)
            return official
        }

        // Source 2: the kouzu.in resolver.
        val artwork =
            try {
                val response =
                    client.get(BASE_URL) {
                        parameter("id", videoId)
                    }
                if (response.status != HttpStatusCode.OK) {
                    cache[videoId] = CacheEntry(null, System.currentTimeMillis() + CACHE_TTL_MS)
                    return null
                }
                val body: JsonObject = response.body()
                parseCanvasArtwork(body, videoId)
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                // Network/parse failure — don't cache, allow a retry next time.
                return null
            }

        cache[videoId] = CacheEntry(artwork, System.currentTimeMillis() + CACHE_TTL_MS)
        return artwork
    }

    /**
     * Asks Spotify directly for the canvas of the current song.
     *
     * Returns null (without caching) when the host app hasn't wired up a token /
     * track resolver, when the user has no Spotify session, when the song can't
     * be matched on Spotify, or when Spotify has no canvas for the track.
     */
    private suspend fun fetchOfficialCanvas(
        videoId: String,
        songTitle: String?,
        artistName: String?,
    ): CanvasArtwork? {
        val resolveTrackUri = trackUriResolver ?: return null
        val provideToken = tokenProvider ?: return null

        val token = provideToken()?.takeIf { it.isNotBlank() } ?: return null
        val trackUri =
            resolveTrackUri(videoId, songTitle, artistName)?.takeIf { it.isNotBlank() } ?: return null

        val response =
            spotifyClient.post(CANVAZ_URL) {
                header("Authorization", "Bearer $token")
                header("Accept", "application/x-protobuf")
                header("Content-Type", "application/x-protobuf")
                header("User-Agent", "ArchiveTune-Android")
                setBody(SpotifyCanvazProtocol.encodeRequest(listOf(trackUri)))
            }
        if (response.status != HttpStatusCode.OK) {
            log("Spotify canvaz returned ${response.status.value} for $trackUri")
            return null
        }

        val entries = SpotifyCanvazProtocol.decodeResponse(response.body<ByteArray>())
        val canvasUrl =
            entries
                .firstOrNull { it.entityUri == trackUri && !it.url.isNullOrBlank() }
                ?.url
                ?: entries.firstNotNullOfOrNull { entry -> entry.url?.takeIf { it.isNotBlank() } }
                ?: return null

        log("Spotify canvaz resolved $trackUri → $canvasUrl")
        return CanvasArtwork(
            name = songTitle,
            artist = artistName,
            // Stable placeholder so CanvasArtworkPlaybackCache dedupes correctly.
            albumId = "yt:$videoId",
            albumName = null,
            static = null,
            animated = null,
            animatedVertical = null,
            videoUrl = canvasUrl,
            videoUrlVertical = canvasUrl,
        )
    }

    private fun parseCanvasArtwork(
        body: JsonObject,
        videoId: String,
    ): CanvasArtwork? {
        // The resolver may either return the canvas fields at the top level or nested
        // under a `data` / `result` envelope. Handle both.
        val payload = body["data"]?.jsonObject ?: body["result"]?.jsonObject ?: body

        val videoUrl =
            payload["url"]?.jsonPrimitive?.contentOrNull
                ?: payload["canvas_url"]?.jsonPrimitive?.contentOrNull
                ?: payload["video_url"]?.jsonPrimitive?.contentOrNull
                ?: payload["canvas"]?.jsonPrimitive?.contentOrNull
                ?: return null
        if (videoUrl.isBlank()) return null

        val songName =
            payload["song"]?.jsonPrimitive?.contentOrNull
                ?: payload["name"]?.jsonPrimitive?.contentOrNull
                ?: payload["title"]?.jsonPrimitive?.contentOrNull
                ?: payload["track"]?.jsonPrimitive?.contentOrNull

        val artistName =
            payload["artist"]?.jsonPrimitive?.contentOrNull
                ?: payload["artists"]?.jsonPrimitive?.contentOrNull
                ?: payload["author"]?.jsonPrimitive?.contentOrNull

        return CanvasArtwork(
            name = songName,
            artist = artistName,
            // Use the YouTube videoId as a stable albumId placeholder so the cache key
            // machinery in CanvasArtworkPlaybackCache dedupes correctly.
            albumId = "yt:$videoId",
            albumName = null,
            static = null,
            animated = null,
            animatedVertical = null,
            videoUrl = videoUrl,
            videoUrlVertical = videoUrl,
        )
    }
}
