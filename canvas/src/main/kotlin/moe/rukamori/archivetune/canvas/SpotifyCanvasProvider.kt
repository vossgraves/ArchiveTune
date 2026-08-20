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
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
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
 * The provider delegates to the third-party `velamhere-img.hf.space` CDN, which serves the
 * Spotify Canvas video for a given YouTube Music video ID directly. The CDN endpoint is
 * `https://velamhere-img.hf.space/id/<videoId>` — the video ID is appended as a path segment
 * after the fixed `/id` prefix.
 *
 * The response shape is tolerant — we accept any of the common field names seen across
 * resolvers of this kind (`url` / `canvas_url` / `video_url`, `song` / `name` / `title`,
 * `artist` / `artists`). When the response is direct video bytes (no JSON envelope), we
 * fall back to using the request URL itself as the canvas video URL — the CDN serves the
 * raw video at that URL.
 *
 * Results are cached in-memory for 1 hour per video ID to avoid hammering the CDN on every
 * recomposition / replay.
 */
object SpotifyCanvasProvider {
    /**
     * CDN base URL. The full URL is `$BASE_URL/<videoId>` — the video ID is appended as a
     * path segment. The `/id` segment is part of the fixed CDN path, so the full URL pattern
     * is `https://velamhere-img.hf.space/id/<videoId>`.
     */
    private const val BASE_URL = "https://velamhere-img.hf.space/id"
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
                // The CDN URL pattern is `https://velamhere-img.hf.space/id/<videoId>` —
                // the video ID is appended as a path segment after the fixed `/id` prefix.
                val cdnUrl = "$BASE_URL/$videoId"
                val response = client.get(cdnUrl)
                if (response.status != HttpStatusCode.OK) {
                    cache[videoId] = CacheEntry(null, System.currentTimeMillis() + CACHE_TTL_MS)
                    return null
                }
                // Two response shapes are supported:
                //   1. JSON envelope with a `url` / `canvas_url` / `video_url` field pointing
                //      at the actual canvas video. parseCanvasArtwork extracts it.
                //   2. Direct video bytes (the CDN serves the raw video at the request URL).
                //      In this case JSON parsing fails — fall back to using cdnUrl as the
                //      video URL directly.
                val bodyText = response.bodyAsText()
                val artwork = parseCanvasArtwork(bodyText, videoId, cdnUrl)
                artwork
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                // Network/parse failure — don't cache, allow a retry next time.
                return null
            }

        cache[videoId] = CacheEntry(artwork, System.currentTimeMillis() + CACHE_TTL_MS)
        return artwork
    }

    /**
     * Parse the CDN response into a [CanvasArtwork].
     *
     * The response may be either:
     *   - A JSON object (possibly nested under a `data` / `result` envelope) with a
     *     `url` / `canvas_url` / `video_url` field — the value of that field is the
     *     actual canvas video URL.
     *   - Direct video bytes (no JSON envelope) — in this case the request URL itself
     *     serves the raw video, and we fall back to using [fallbackVideoUrl] as the
     *     canvas video URL.
     *
     * The JSON shape is tolerant — we accept any of the common field names seen across
     * resolvers of this kind (`url` / `canvas_url` / `video_url`, `song` / `name` / `title`,
     * `artist` / `artists`).
     */
    private fun parseCanvasArtwork(bodyText: String, videoId: String, fallbackVideoUrl: String): CanvasArtwork? {
        // Try JSON parsing first.
        val payload = runCatching {
            val obj = json.parseToJsonElement(bodyText).jsonObject
            obj["data"]?.jsonObject ?: obj["result"]?.jsonObject ?: obj
        }.getOrNull()

        // JSON path: extract url / song / artist from the envelope.
        if (payload != null) {
            val videoUrl =
                payload["url"]?.jsonPrimitive?.contentOrNull
                    ?: payload["canvas_url"]?.jsonPrimitive?.contentOrNull
                    ?: payload["video_url"]?.jsonPrimitive?.contentOrNull
                    ?: payload["canvas"]?.jsonPrimitive?.contentOrNull
                    ?: fallbackVideoUrl // JSON envelope but no url field — use the CDN URL.
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

        // Non-JSON response: the CDN serves the raw video at the request URL itself.
        // Use fallbackVideoUrl as the canvas video URL. We don't have song/artist
        // metadata, so we leave those null — the caller's identity verification will
        // skip the song/artist match step (the YouTube song's title/artist from the
        // queue is used as the source of truth).
        return CanvasArtwork(
            name = null,
            artist = null,
            albumId = "yt:$videoId",
            albumName = null,
            static = null,
            animated = null,
            animatedVertical = null,
            videoUrl = fallbackVideoUrl,
            videoUrlVertical = fallbackVideoUrl,
        )
    }
}
