/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.dabmusic

import moe.rukamori.archivetune.audiosource.TrackMatching
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Resolves playable lossless streams for a track from the DabMusic catalog at
 * https://dabmusic.xyz.
 *
 * DabMusic is a community-operated catalog+restream service: the app POSTs a search query, the
 * service replies with a list of matching tracks (each carrying a track id and metadata), and a
 * second call to the stream endpoint resolves a direct playable URL. There are no per-user
 * accounts — DabMusic is a public proxy — so unlike [moe.rukamori.archivetune.deezer.DeezerAudioProvider]
 * there is no ARL/bearer credential to manage and no [moe.rukamori.archivetune.utils.PoolAccountManager]
 * tier. The provider is purely a network client.
 *
 * The service is fronted by a Cloudflare interstitial on its HTML routes; the REST endpoints under
 * the `api` path are expected to be reachable from a non-browser User-Agent. When Cloudflare still
 * challenges the request (HTTP 403 with the "Just a moment…" body) the provider logs and returns
 * null so the playback layer falls through to the next source in the chain — never throwing.
 *
 * All calls run blocking network I/O and must not be made from the main thread.
 */
object DabMusicAudioProvider {
    const val DEFAULT_BASE_URL = "https://dabmusic.xyz"

    private const val TAG = "DabMusic"
    private const val SEARCH_LIMIT = 10
    private const val SEARCH_CACHE_MS = 10 * 60 * 1000L
    private const val STREAM_CACHE_MS = 5 * 60 * 1000L
    private const val FAILURE_CACHE_MS = 5 * 60 * 1000L

    private const val MIME_FLAC = "audio/flac"
    private const val MIME_MPEG = "audio/mpeg"

    /**
     * A desktop Chrome User-Agent. The DabMusic gateway is behind Cloudflare and serves the
     * interstitial to clients it identifies as bots, so we present as a browser to maximise the
     * chance the REST endpoint is reachable at all.
     */
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Safari/537.36"

    private val client =
        OkHttpClient
            .Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()

    /** What the player asked for. Mirrors the shape of the other providers' Query types. */
    data class Query(
        val mediaId: String,
        val title: String,
        val artists: List<String>,
        val album: String?,
        val durationMs: Long?,
    ) {
        fun cacheKey(): String = "$mediaId|${title.lowercase()}|${artists.joinToString(",").lowercase()}"
    }

    /** A resolved DabMusic stream, before mapping to the playback layer's [moe.rukamori.archivetune.audiosource.DirectStream]. */
    data class Resolved(
        val uri: String,
        val mimeType: String,
        val codecs: String,
        val contentLength: Long?,
        val label: String,
        val matchedTitle: String?,
        val matchedArtist: String?,
        val matchedAlbum: String?,
        val matchedDurationMs: Long?,
        val sampleRate: Int?,
        val bitDepth: Int?,
    )

    @Volatile
    private var baseUrl: String = DEFAULT_BASE_URL

    @Volatile
    var lastResolvedTrackId: String? = null
        private set

    private data class CachedSearch(val candidate: TrackMatching.Candidate?, val expiresAt: Long)
    private data class CachedStream(val stream: Resolved, val expiresAt: Long)

    private val searchCache = ConcurrentHashMap<String, CachedSearch>()
    private val streamCache = ConcurrentHashMap<String, CachedStream>()
    private val failureCache = ConcurrentHashMap<String, Long>()

    /**
     * Sets the base URL used to compose DabMusic endpoints. Blank resets to [DEFAULT_BASE_URL].
     * Trailing slashes are trimmed. Cheap and idempotent; safe to push the current preference
     * value on every settings change.
     */
    fun setBaseUrl(url: String?) {
        val trimmed = url?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }
        baseUrl = trimmed ?: DEFAULT_BASE_URL
    }

    /** Always true: DabMusic is a public proxy with no per-user accounts. */
    fun hasAccounts(): Boolean = true

    /**
     * Resolves a playable stream for [query]. Returns null when the catalog has no acceptable
     * match, when the gateway is unreachable, or when Cloudflare challenges the request. Never
     * throws — failures are cached for [FAILURE_CACHE_MS] so we don't hammer the gateway on
     * every retry.
     */
    fun resolve(
        query: Query,
        format: String,
    ): Resolved? {
        val now = System.currentTimeMillis()
        val cacheKey = query.cacheKey() + ":" + format
        streamCache[cacheKey]?.let { cached ->
            if (cached.expiresAt > now) return cached.stream
            streamCache.remove(cacheKey)
        }
        failureCache[cacheKey]?.let { failedUntil ->
            if (failedUntil > now) return null
            failureCache.remove(cacheKey)
        }

        val match =
            runCatching { search(query) }
                .onFailure { Timber.tag(TAG).w(it, "search failed for \"%s\"", query.title) }
                .getOrNull()
        if (match == null) {
            failureCache[cacheKey] = now + FAILURE_CACHE_MS
            return null
        }

        val stream =
            runCatching { requestStream(match.id, format) }
                .onFailure { Timber.tag(TAG).w(it, "stream resolve failed for track %s", match.id) }
                .getOrNull()
        if (stream == null) {
            failureCache[cacheKey] = now + FAILURE_CACHE_MS
            return null
        }

        lastResolvedTrackId = match.id
        val resolved =
            Resolved(
                uri = stream.url,
                mimeType = if (stream.flac) MIME_FLAC else MIME_MPEG,
                codecs = if (stream.flac) "flac" else "mp3",
                contentLength = stream.contentLength,
                label =
                    when {
                        stream.flac -> "DabMusic FLAC"
                        format.equals("MP3_320", true) -> "DabMusic MP3 320"
                        else -> "DabMusic MP3"
                    },
                matchedTitle = match.title,
                matchedArtist = match.artists.firstOrNull(),
                matchedAlbum = match.album,
                matchedDurationMs = match.durationMs,
                // DabMusic's gateway does not report sample rate / bit depth; assume CD-quality
                // for FLAC (the catalog's lossless tier is 16-bit/44.1 kHz) and leave MP3 to the
                // playback layer's tier heuristic.
                sampleRate = if (stream.flac) 44_100 else null,
                bitDepth = if (stream.flac) 16 else null,
            )
        streamCache[cacheKey] = CachedStream(resolved, now + STREAM_CACHE_MS)
        return resolved
    }

    /** Evicts cached search/stream/failure entries for [query]+[format]. */
    fun invalidate(
        query: Query,
        format: String,
    ) {
        val key = query.cacheKey() + ":" + format
        streamCache.remove(key)
        failureCache.remove(key)
    }

    // ---------------------------------------------------------------------------------------------
    // Search
    // ---------------------------------------------------------------------------------------------

    /** Finds the DabMusic track id that best matches [query], or null when nothing scores high enough. */
    private fun search(query: Query): TrackMatching.Candidate? {
        val key = query.cacheKey()
        val now = System.currentTimeMillis()
        searchCache[key]?.let { cached ->
            if (cached.expiresAt > now) return cached.candidate
            searchCache.remove(key)
        }

        val terms = listOf(TrackMatching.searchTitle(query.title)) + query.artists.take(1).map { TrackMatching.searchArtist(it) }
        val queryString = terms.filter { it.isNotBlank() }.joinToString(" ").trim()
        if (queryString.isBlank()) return null

        val url =
            baseUrl
                .toHttpUrl()
                .newBuilder()
                .addPathSegment("api")
                .addPathSegment("search")
                .addQueryParameter("q", queryString)
                .addQueryParameter("limit", SEARCH_LIMIT.toString())
                .build()
        val request = baseRequest(url).get().build()

        val candidates =
            runCatching {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Timber.tag(TAG).d("search HTTP %d for \"%s\"", response.code, queryString)
                        return@use emptyList<TrackMatching.Candidate>()
                    }
                    val body = response.body?.string().orEmpty()
                    parseSearchResults(body)
                }
            }.onFailure { Timber.tag(TAG).w(it, "search network failed") }
                .getOrDefault(emptyList())

        val winner = TrackMatching.best(
            TrackMatching.Target(
                title = query.title,
                artists = query.artists,
                album = query.album,
                durationMs = query.durationMs,
            ),
            candidates,
        )
        searchCache[key] = CachedSearch(winner, now + SEARCH_CACHE_MS)
        return winner
    }

    private fun parseSearchResults(body: String): List<TrackMatching.Candidate> {
        val trimmed = body.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("<")) {
            // Cloudflare interstitial HTML — not a JSON response.
            if (trimmed.contains("Just a moment", ignoreCase = true)) {
                Timber.tag(TAG).w("DabMusic gateway returned the Cloudflare interstitial; skipping source")
            }
            return emptyList()
        }
        val root =
            runCatching { JSONObject(trimmed) }
                .getOrElse {
                    // Some deployments return a bare array; tolerate it.
                    runCatching { JSONArray(trimmed) }.getOrNull()?.let { arr ->
                        return (0 until arr.length()).mapNotNull { i ->
                            arr.optJSONObject(i)?.toCandidate()
                        }
                    } ?: return emptyList()
                }
        // Accept either { "results": [...] }, { "data": [...] }, or a top-level array.
        val data =
            root.optJSONArray("results")
                ?: root.optJSONArray("data")
                ?: root.optJSONArray("tracks")
                ?: root.optJSONArray("items")
                ?: JSONArray()
        return (0 until data.length()).mapNotNull { i ->
            data.optJSONObject(i)?.toCandidate()
        }
    }

    private fun JSONObject.toCandidate(): TrackMatching.Candidate? {
        val id = optString("id").ifBlank { optString("trackId") }.ifBlank { optString("track_id") }
        if (id.isBlank()) return null
        val title = optString("title").ifBlank { optString("name") }
        if (title.isBlank()) return null
        val artist =
            optString("artist").ifBlank {
                optJSONArray("artists")?.optString(0)?.ifBlank { null }
            }
        val durationMs = optLong("durationMs", 0L).takeIf { it > 0 }
            ?: optLong("duration_ms", 0L).takeIf { it > 0 }
            ?: (optLong("duration", 0L).takeIf { it > 0 }?.times(1000L))
        val album = optString("album").ifBlank { optJSONObject("album")?.optString("title") }
        return TrackMatching.Candidate(
            id = id,
            title = title,
            artists = listOfNotNull(artist),
            album = album?.takeIf { it.isNotBlank() },
            durationMs = durationMs,
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Stream
    // ---------------------------------------------------------------------------------------------

    private data class MediaUrl(
        val url: String,
        val flac: Boolean,
        val contentLength: Long?,
        val format: String,
    )

    private fun requestStream(
        trackId: String,
        format: String,
    ): MediaUrl? {
        val url =
            baseUrl
                .toHttpUrl()
                .newBuilder()
                .addPathSegment("api")
                .addPathSegment("stream")
                .addQueryParameter("id", trackId)
                .addQueryParameter("format", format)
                .build()
        val request = baseRequest(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Timber.tag(TAG).d("stream HTTP %d for track %s", response.code, trackId)
                return null
            }
            val body = response.body?.string().orEmpty()
            val root =
                runCatching { JSONObject(body) }.getOrNull()
                    ?: return null
            val streamUrl = root.optString("url").ifBlank { root.optString("streamUrl") }
            if (streamUrl.isBlank()) return null
            val servedFormat = root.optString("format", format)
            val flac = servedFormat.equals("FLAC", true) ||
                root.optBoolean("flac", false) ||
                streamUrl.substringAfterLast('.', "").equals("flac", true)
            val contentLength = root.optLong("contentLength", 0L).takeIf { it > 0 }
                ?: root.optLong("content_length", 0L).takeIf { it > 0 }
            return MediaUrl(
                url = streamUrl,
                flac = flac,
                contentLength = contentLength,
                format = servedFormat,
            )
        }
    }

    private fun baseRequest(url: okhttp3.HttpUrl): Request.Builder =
        Request
            .Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .header("Accept-Language", "en-US,en;q=0.5")
}
