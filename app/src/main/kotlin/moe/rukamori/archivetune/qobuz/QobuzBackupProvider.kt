/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.qobuz

import moe.rukamori.archivetune.audiosource.FlacStreamInfo
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Catalogue lookups for the **Qobuz backup** source (the community-hosted
 * `mlc-ytify.kouzu.in` mirror, which serves a FLAC per YouTube video id).
 *
 * Covers everything that talks to the mirror:
 *
 *  - [resolveStream] turns a YouTube video id into a playable stream. Both the playback path
 *    (`MusicService.resolveQobuzBackupStream`) and the download path
 *    (`LosslessStreamResolver.resolveQobuzBackup`) call it, so a fix to mirror selection or
 *    format detection reaches both. Callers pass their own [OkHttpClient] when they need one —
 *    playback streams the resolved bytes through the service's proxy-aware client.
 *  - [searchCandidates] backs the player's "Play from" search popup. The popup
 *    used to hard-exclude this source with a "search backend not yet available"
 *    empty state, on the assumption that the mirror could only be addressed by
 *    video id. It does in fact expose `GET /api/search?q=&limit=`, which returns
 *    the mirror's own indexed catalogue (~64k tracks), so results can be listed
 *    and picked like any other source.
 *
 * The `x-request-source: muzo` header is required on every kouzu.in request —
 * without it the server rate-limits aggressively. `MusicService.mediaOkHttpClient`
 * injects it for playback traffic via an interceptor; this object uses its own
 * client, so it sets the header explicitly.
 */
object QobuzBackupProvider {
    private const val BASE_URL = "https://mlc-ytify.kouzu.in"
    private const val USER_AGENT = "ArchiveTune-Android"
    private const val SEARCH_CACHE_MS = 10 * 60 * 1000L

    /** YouTube video ids are exactly 11 chars of `[A-Za-z0-9_-]`. */
    private val VIDEO_ID_REGEX = Regex("^[A-Za-z0-9_-]{11}$")

    private val client =
        OkHttpClient
            .Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()

    /**
     * One track from the backup mirror's index.
     *
     * [videoId] is a YouTube video id and doubles as the mirror's primary key:
     * the stream URLs are `…/lossless/<videoId>` and `…/song/<videoId>`, and it
     * is what `resolveQobuzBackupStream` needs. It is deliberately NOT assumed to
     * equal the currently playing song's media id — picking a row in the popup
     * pins this id for the current song (see `SongSourceQobuzBackupVideoId`).
     */
    data class Candidate(
        val videoId: String,
        val title: String,
        val artist: String?,
        /** True when the mirror advertises a FLAC mirror for this track. */
        val isLossless: Boolean,
    ) {
        /**
         * Cover art. The mirror's search response carries no artwork, but its key
         * *is* a YouTube video id, so the standard i.ytimg thumbnail always
         * exists. `hqdefault` (480x360) is the largest size guaranteed to be
         * present for every video — `maxresdefault` 404s for a large share of
         * the catalogue, which would leave the row with a placeholder icon.
         */
        val thumbnailUrl: String
            get() = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
    }

    private data class CachedSearch(
        val candidates: List<Candidate>,
        val expiresAt: Long,
    )

    private val searchCache = ConcurrentHashMap<String, CachedSearch>()

    /**
     * Searches the backup mirror's catalogue. Blocking — call from `Dispatchers.IO`,
     * matching [QobuzAudioProvider.searchCandidates].
     *
     * Returns an empty list when the query is too short, the mirror is
     * unreachable, or it has nothing indexed for the query. Callers treat that as
     * "no results", never as a hard error.
     */
    fun searchCandidates(
        query: String,
        limit: Int = 8,
    ): List<Candidate> {
        val trimmed = query.trim()
        if (trimmed.length < 2 || limit <= 0) return emptyList()

        val cacheKey = "${trimmed.lowercase()}|$limit"
        val now = System.currentTimeMillis()
        searchCache[cacheKey]?.takeIf { it.expiresAt > now }?.let { return it.candidates }

        val url =
            "$BASE_URL/api/search"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("q", trimmed)
                .addQueryParameter("limit", limit.toString())
                .build()
        val request =
            Request
                .Builder()
                .url(url)
                .get()
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .header("x-request-source", "muzo")
                .build()

        val candidates =
            runCatching {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Timber.tag("QobuzBackup").d(
                            "search \"%s\" failed: HTTP %d",
                            trimmed,
                            response.code,
                        )
                        return@use emptyList()
                    }
                    parseSearchResponse(response.body?.string().orEmpty(), limit)
                }
            }.onFailure { error ->
                Timber.tag("QobuzBackup").d(error, "search \"%s\" failed", trimmed)
            }.getOrDefault(emptyList())

        // Cache successes only: a transient network failure should not pin an
        // empty result for the next ten minutes.
        if (candidates.isNotEmpty()) {
            searchCache[cacheKey] = CachedSearch(candidates, now + SEARCH_CACHE_MS)
        }
        return candidates
    }

    /**
     * Parses the mirror's search payload:
     * `[{"id":"J7p4bzqLvCw","name":"Blinding Lights","artists":"The Weeknd",
     *    "lossless":"4:flac","stream_sources":"m4a"}, …]`
     *
     * Rows without a usable video id or title are skipped rather than surfaced as
     * unplayable entries.
     */
    private fun parseSearchResponse(
        body: String,
        limit: Int,
    ): List<Candidate> {
        if (body.isBlank()) return emptyList()
        val array = runCatching { JSONArray(body) }.getOrNull() ?: return emptyList()
        val out = mutableListOf<Candidate>()
        for (index in 0 until array.length()) {
            if (out.size >= limit) break
            val item = array.optJSONObject(index) ?: continue
            val videoId = item.optString("id").trim()
            if (!VIDEO_ID_REGEX.matches(videoId)) continue
            val title = item.optString("name").trim()
            if (title.isEmpty()) continue
            val artist = item.optString("artists").trim().takeIf { it.isNotEmpty() }
            // "lossless" is a tier descriptor ("4:flac") when a FLAC mirror exists
            // and absent/blank when only the lossy m4a transcode is available.
            val isLossless = item.optString("lossless").contains("flac", ignoreCase = true)
            out.add(
                Candidate(
                    videoId = videoId,
                    title = title,
                    artist = artist,
                    isLossless = isLossless,
                ),
            )
        }
        return out
    }

    /**
     * A mirror that answered a probe with real audio bytes.
     *
     * [contentLength] is the *full* resource size taken from the `Content-Range` total
     * (`bytes 0-41/40802970`), not the 42 bytes the probe asked for — ExoPlayer and the downloader
     * both want the whole-resource length.
     *
     * [sampleRate], [bitDepth] and [durationMs] come from the FLAC STREAMINFO block that the probe
     * already downloads, so they describe the actual file rather than a tier guess. All three are
     * null for the lossy mirror.
     */
    data class ResolvedStream(
        val uri: String,
        val mimeType: String,
        val codecs: String,
        val contentLength: Long?,
        val contentType: String,
        val isLossless: Boolean,
        val sampleRate: Int? = null,
        val bitDepth: Int? = null,
        val durationMs: Long? = null,
    ) {
        /** Label for logs and the media-info card. */
        val label: String
            get() = if (isLossless) "Qobuz backup (lossless)" else "Qobuz backup (kouzu.in)"
    }

    /**
     * Resolves a playable stream for [videoId] (a YouTube video id — the mirror's primary key).
     *
     * Two steps, because the mirror is a resolver in front of a CDN:
     *  1. `GET /api/stream?id=<videoId>` returns a small JSON envelope naming the mirrors for the
     *     track. GET, not HEAD — the endpoint answers HEAD with `405 Method Not Allowed`.
     *  2. Each candidate mirror is probed with a ranged GET and the first that serves real audio
     *     wins. Also a GET: the CDN rejects HEAD the same way. The probe reads
     *     [FlacStreamInfo.REQUIRED_BYTES] bytes, which is both cheap and exactly enough to read the
     *     FLAC header, so the container is determined from the bytes rather than from a
     *     `Content-Type` the CDN often gets wrong.
     *
     * Returns null when the id is not a video id, the mirror has nothing indexed for it, or no
     * candidate serves audio. Blocking — call from `Dispatchers.IO`.
     *
     * @param client the HTTP client to use. Defaults to this object's own; pass a proxy-aware one
     *   to route the probe the same way the resolved bytes will be fetched.
     */
    fun resolveStream(
        videoId: String,
        client: OkHttpClient = this.client,
    ): ResolvedStream? {
        val id = videoId.trim()
        if (!VIDEO_ID_REGEX.matches(id)) {
            Timber.tag("QobuzBackup").d("skip: \"%s\" is not a YouTube video id", id)
            return null
        }

        val candidates = fetchMirrorCandidates(id, client)
        if (candidates.isEmpty()) return null

        val resolved = candidates.firstNotNullOfOrNull { candidate -> probeMirror(candidate, client) }
        if (resolved == null) {
            Timber.tag("QobuzBackup").d(
                "CDN miss for %s: no candidate mirror served audio (tried %d)",
                id,
                candidates.size,
            )
            return null
        }
        Timber.tag("QobuzBackup").i(
            "resolved %s → %s [%s%s]",
            id,
            resolved.uri.take(80),
            resolved.contentType,
            if (resolved.isLossless) ", lossless" else "",
        )
        return resolved
    }

    /**
     * Reads the resolver envelope and returns its mirror URLs, best first.
     *
     * The envelope carries more than one mirror for the same track:
     *   `"lossless"` → `…/lossless/<id>`, the real FLAC
     *   `"url"`      → `…/song/<id>`, a lossy AAC-in-MP4 transcode
     * Reading only `url` is why "Qobuz backup" used to never actually play lossless. The FLAC
     * mirrors are listed first and the lossy one is kept as a fallback for entries that have no
     * FLAC yet.
     */
    private fun fetchMirrorCandidates(
        videoId: String,
        client: OkHttpClient,
    ): List<String> {
        val url =
            "$BASE_URL/api/stream"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("id", videoId)
                .build()
        val request =
            Request
                .Builder()
                .url(url)
                .get()
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .header("x-request-source", "muzo")
                .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.tag("QobuzBackup").d("resolver miss for %s: HTTP %d", videoId, response.code)
                    return@use emptyList()
                }
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) {
                    Timber.tag("QobuzBackup").d("resolver miss for %s: empty body", videoId)
                    return@use emptyList()
                }
                val root = runCatching { JSONObject(body) }.getOrNull()
                if (root == null) {
                    Timber.tag("QobuzBackup").d(
                        "resolver miss for %s: body is not JSON (first 100 chars: %s)",
                        videoId,
                        body.take(100),
                    )
                    return@use emptyList()
                }
                buildList {
                    root.optString("lossless").takeIf { it.isNotBlank() }?.let(::add)
                    root.optString("flac").takeIf { it.isNotBlank() }?.let(::add)
                    root.optString("url").takeIf { it.isNotBlank() }?.let(::add)
                    root.optString("canvas_url").takeIf { it.isNotBlank() }?.let(::add)
                    root.optString("video_url").takeIf { it.isNotBlank() }?.let(::add)
                }.distinct().also { mirrors ->
                    if (mirrors.isEmpty()) {
                        Timber.tag("QobuzBackup").d(
                            "resolver miss for %s: no url field in response (first 200 chars: %s)",
                            videoId,
                            body.take(200),
                        )
                    }
                }
            }
        }.onFailure { error ->
            Timber.tag("QobuzBackup").d(error, "resolver call failed for %s", videoId)
        }.getOrDefault(emptyList())
    }

    /**
     * Probes one mirror with a ranged GET and describes it, or returns null when it does not serve
     * audio for this track.
     */
    private fun probeMirror(
        url: String,
        client: OkHttpClient,
    ): ResolvedStream? =
        runCatching {
            val request =
                Request
                    .Builder()
                    .url(url)
                    .get()
                    .header("User-Agent", USER_AGENT)
                    .header("x-request-source", "muzo")
                    .header("Range", "bytes=0-${FlacStreamInfo.REQUIRED_BYTES - 1}")
                    .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val headerBytes = runCatching { response.body?.bytes() }.getOrNull()
                val contentType = response.header("Content-Type")?.lowercase().orEmpty()
                // A JSON body here is the CDN's {"detail":"Not Found"} envelope for a mirror that
                // has not been populated for this track yet.
                if (contentType.contains("json") || contentType.contains("html")) return@use null
                if (contentType.isNotBlank() &&
                    !contentType.startsWith("audio/") &&
                    !contentType.startsWith("video/") &&
                    !contentType.contains("octet-stream")
                ) {
                    return@use null
                }
                // Prefer the Content-Range total ("bytes 0-41/40802970") — Content-Length on a 206
                // is just the probed bytes.
                val totalLength =
                    response
                        .header("Content-Range")
                        ?.substringAfter('/', "")
                        ?.trim()
                        ?.toLongOrNull()
                        ?: response.header("Content-Length")?.toLongOrNull()?.takeIf { response.code != 206 }
                // Trust the bytes over the labels: a mirror that serves FLAC is FLAC even when the
                // CDN mislabels the Content-Type, and the header is already in hand.
                val streamInfo = headerBytes?.let(FlacStreamInfo::parse)
                val isFlac = streamInfo != null || contentType.contains("flac") || url.contains("/lossless/")
                ResolvedStream(
                    uri = url,
                    mimeType = if (isFlac) "audio/flac" else "audio/mp4",
                    codecs = if (isFlac) "flac" else "mp4a.40.2",
                    contentLength = totalLength?.takeIf { it > 0 },
                    contentType = contentType.ifBlank { "unknown" },
                    isLossless = isFlac,
                    sampleRate = streamInfo?.sampleRate,
                    bitDepth = streamInfo?.bitDepth,
                    durationMs = streamInfo?.durationMs,
                )
            }
        }.onFailure { error ->
            Timber.tag("QobuzBackup").d(error, "mirror probe failed for %s", url.take(80))
        }.getOrNull()

    /** Drops cached search results. Called when the user clears app caches. */
    fun clearCaches() {
        searchCache.clear()
    }
}
