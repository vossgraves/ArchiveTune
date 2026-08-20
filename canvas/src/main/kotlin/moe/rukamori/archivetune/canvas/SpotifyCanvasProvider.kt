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
 * Fetches Spotify Canvas looping videos for songs by their YouTube Music video ID.
 *
 * The provider delegates to the third-party `mlc-ytify.kouzu.in` resolver, which maps a
 * YouTube Music video ID → the song's Spotify Canvas URL. The resolver endpoint is
 * `https://mlc-ytify.kouzu.in/api/canvas?id=<videoId>` (same resolver pattern as the Qobuz
 * backup server) — it returns a JSON envelope with the actual canvas video URL on the
 * `velamhere-img.hf.space` CDN.
 *
 * The resolver also returns the matched song name and artist name, which we surface in the
 * resulting [CanvasArtwork] for identity verification by the caller.
 *
 * The `x-request-source: muzo` header is required on every kouzu.in request — without it
 * the server rate-limits the client. The header is injected centrally by the
 * `MusicService.mediaOkHttpClient` interceptor for any kouzu.in host request, but the canvas
 * provider uses its own Ktor client (with OkHttp engine) — so we add the header manually
 * here via the `defaultRequest` block.
 *
 * The response shape is tolerant — we accept any of the common field names seen across
 * resolvers of this kind (`url` / `canvas_url` / `video_url`, `song` / `name` / `title`,
 * `artist` / `artists`).
 *
 * Results are cached in-memory for 1 hour per video ID to avoid hammering the resolver
 * on every recomposition / replay.
 */
object SpotifyCanvasProvider {
    /**
     * Resolver base URL. The full URL is `$BASE_URL?id=<videoId>` — the video ID is passed
     * as a query parameter. The `x-request-source: muzo` header is added to every request
     * to bypass the server's rate-limiting.
     */
    private const val BASE_URL = "https://mlc-ytify.kouzu.in/api/canvas"
    private const val CACHE_TTL_MS = 60L * 60 * 1000 // 1 hour

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
            // Adding it here via defaultRequest means every request the client makes
            // to the resolver includes the header.
            defaultRequest {
                header("x-request-source", "muzo")
                header("User-Agent", "ArchiveTune-Android")
                header("Accept", "application/json")
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
     * Looks up the Spotify Canvas for [videoId] (the YouTube Music video ID of the
     * currently playing song). Returns `null` if the resolver has no canvas for the
     * song, the song isn't on Spotify, or the request fails.
     *
     * The returned [CanvasArtwork] has its [CanvasArtwork.videoUrl] /
     * [CanvasArtwork.videoUrlVertical] populated with the canvas URL so the player
     * can loop it as artwork. `name` and `artist` are populated from the resolver
     * response when available so the caller can do identity verification.
     */
    suspend fun getByVideoId(videoId: String): CanvasArtwork? {
        if (videoId.isBlank()) return null

        cache[videoId]?.let { entry ->
            if (entry.expiresAtMs > System.currentTimeMillis()) return entry.value
            cache.remove(videoId)
        }

        val artwork =
            try {
                // The resolver URL pattern: `https://mlc-ytify.kouzu.in/api/canvas?id=<videoId>`.
                // The video ID is passed as a query parameter. The x-request-source: muzo
                // header is added to every request by the client's defaultRequest block.
                val response = client.get(BASE_URL) {
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

    private fun parseCanvasArtwork(body: JsonObject, videoId: String): CanvasArtwork? {
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
