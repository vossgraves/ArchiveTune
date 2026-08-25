/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.qobuz

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Catalogue lookups for the **Qobuz backup** source (the community-hosted
 * `mlc-ytify.kouzu.in` mirror, which serves a FLAC per YouTube video id).
 *
 * Streaming itself lives in `MusicService.resolveQobuzBackupStream` — this object
 * only covers the parts the *UI* needs, which the resolver has no use for:
 *
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

    /** Drops cached search results. Called when the user clears app caches. */
    fun clearCaches() {
        searchCache.clear()
    }
}
