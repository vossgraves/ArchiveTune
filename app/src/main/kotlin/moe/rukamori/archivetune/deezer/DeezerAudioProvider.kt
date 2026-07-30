/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.deezer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.audiosource.DirectStream
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Resolves a Deezer track to a directly streamable URL.
 *
 * ## Why this exists
 *
 * [DownloadSource.AUTO] tries Qobuz → Tidal → Deezer → YouTube Music in
 * order, picking the first source that can serve a full-quality stream
 * for the track. Deezer is included so that the AUTO source ordering
 * honours the user's explicit "prefer FLAC" intent when both Qobuz and
 * Tidal fail to match a track.
 *
 * ## Current limitations
 *
 * Deezer's public catalogue API (`https://api.deezer.com/search`) does
 * NOT expose a streamable FLAC URL — full-track streaming requires an
 * authenticated Premium session (Deezer access tokens are not stored
 * anywhere in ArchiveTune at the time of writing). The provider
 * therefore resolves a track id + metadata for downstream use (ISRC
 * matching, artwork lookup) but returns `null` for the actual stream
 * until Premium credentials are configured.
 *
 * When Premium support is added in the future, only
 * [DeezerAudioProvider.resolve] needs to change — the rest of the
 * download pipeline already routes through [DirectStream] uniformly.
 *
 * ## Public API
 *
 * Mirrors [moe.rukamori.archivetune.qobuz.QobuzAudioProvider] /
 * [moe.rukamori.archivetune.tidal.TidalAudioProvider] so the download
 * resolver can treat all three sources uniformly.
 */
object DeezerAudioProvider {

    /**
     * Lookup metadata for a track, mirroring Qobuz/Tidal query shape.
     */
    data class Query(
        val mediaId: String,
        val title: String,
        val artists: List<String>,
        val album: String?,
        val durationMs: Long?,
    )

    /**
     * The resolved Deezer track metadata. The [previewUrl] is the
     * 30-second preview MP3 that Deezer exposes publicly — useful for
     * matching verification but NOT for full-song downloads.
     */
    data class Resolved(
        val trackId: String,
        val title: String,
        val artist: String?,
        val album: String?,
        val isrc: String?,
        val durationMs: Long?,
        val previewUrl: String?,
        val coverUrl: String?,
    )

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /**
     * Searches Deezer's public catalogue for [query] and returns the
     * best-matching track's metadata. Returns null when no match is
     * found or the Deezer API is unreachable.
     *
     * This does NOT return a streamable URL — full-track streaming
     * requires Deezer Premium credentials, which ArchiveTune does not
     * currently store. The returned [Resolved] is used for:
     *   - ISRC lookup (more accurate YouTube Music matching)
     *   - Artwork URL fallback
     *   - Downloaded-song metadata enrichment (thumbnail, album name)
     */
    suspend fun lookup(query: Query): Resolved? = withContext(Dispatchers.IO) {
        runCatching {
            val q = buildSearchQuery(query)
            val req = Request.Builder()
                .url("https://api.deezer.com/search?q=${java.net.URLEncoder.encode(q, "UTF-8")}&limit=10")
                .header("Accept", "application/json")
                .build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) {
                    Timber.tag("Deezer").w("search HTTP %d for '%s'", res.code, q)
                    return@use null
                }
                val body = res.body?.string() ?: return@use null
                val root = JSONObject(body)
                val data = root.optJSONArray("data") ?: return@use null
                if (data.length() == 0) return@use null

                // Score each candidate by normalized title + artist similarity.
                val candidates = (0 until data.length()).mapNotNull { i ->
                    val obj = data.optJSONObject(i) ?: return@mapNotNull null
                    val title = obj.optString("title").ifBlank { return@mapNotNull null }
                    val artist = obj.optJSONObject("artist")?.optString("name")?.ifBlank { null }
                    val album = obj.optJSONObject("album")?.optString("title")?.ifBlank { null }
                    val durationSec = obj.optLong("duration", 0L).takeIf { it > 0 }
                    val isrc = obj.optString("isrc").ifBlank { null }
                    val preview = obj.optString("preview").ifBlank { null }
                    val cover = obj.optJSONObject("album")?.optString("cover_big")?.ifBlank { null }
                    val score = scoreCandidate(query, title, artist, album, durationSec)
                    Resolved(
                        trackId = obj.optLong("id").toString(),
                        title = title,
                        artist = artist,
                        album = album,
                        isrc = isrc,
                        durationMs = durationSec?.times(1000L),
                        previewUrl = preview,
                        coverUrl = cover,
                    ) to score
                }
                val best = candidates.maxByOrNull { it.second } ?: return@use null
                if (best.second < MIN_MATCH_SCORE) {
                    Timber.tag("Deezer").d("best candidate score %.2f below threshold for '%s'", best.second, q)
                    return@use null
                }
                best.first
            }
        }.getOrNull()
    }

    /**
     * Resolves a [DirectStream] for [query]. Returns null because
     * Deezer does not expose full-track stream URLs publicly — see
     * class docs. Kept for API symmetry with the other providers.
     */
    suspend fun resolve(
        query: Query,
        @Suppress("UNUSED_PARAMETER") preferredQuality: String = "FLAC",
    ): DirectStream? {
        // Public API only returns a 30-second preview — not usable for
        // full downloads. Return null so the AUTO resolver falls
        // through to the next source.
        val resolved = lookup(query) ?: return null
        Timber
            .tag("Deezer")
            .d("resolved track %s ('%s') but full stream requires Premium — skipping", resolved.trackId, resolved.title)
        return null
    }

    private fun buildSearchQuery(query: Query): String {
        val parts = mutableListOf<String>()
        query.artists.firstOrNull()?.takeIf(String::isNotBlank)?.let { parts.add("artist:\"$it\"") }
        parts.add("track:\"${query.title}\"")
        query.album?.takeIf(String::isNotBlank)?.let { parts.add("album:\"$it\"") }
        return parts.joinToString(" ")
    }

    private fun scoreCandidate(
        query: Query,
        candidateTitle: String,
        candidateArtist: String?,
        candidateAlbum: String?,
        candidateDurationSec: Long?,
    ): Double {
        val titleScore = normalizedSimilarity(query.title, candidateTitle)
        val artistScore = query.artists.firstOrNull()?.let { a ->
            candidateArtist?.let { c -> normalizedSimilarity(a, c) }
        } ?: 0.0
        val albumScore = query.album?.let { q ->
            candidateAlbum?.let { c -> normalizedSimilarity(q, c) }
        } ?: 0.5
        val durationScore = query.durationMs?.let { qd ->
            candidateDurationSec?.let { cs ->
                val cd = cs * 1000L
                val diff = kotlin.math.abs(qd - cd)
                when {
                    diff < 2_000L -> 1.0
                    diff < 5_000L -> 0.85
                    diff < 10_000L -> 0.6
                    else -> 0.2
                }
            }
        } ?: 0.5

        // Title is the most important signal, then artist, then album, then duration.
        return titleScore * 0.45 + artistScore * 0.30 + albumScore * 0.15 + durationScore * 0.10
    }

    private fun normalizedSimilarity(a: String, b: String): Double {
        val na = a.lowercase().trim().replace(Regex("[^a-z0-9 ]"), "")
        val nb = b.lowercase().trim().replace(Regex("[^a-z0-9 ]"), "")
        if (na == nb) return 1.0
        if (na.isBlank() || nb.isBlank()) return 0.0
        // Simple token-overlap similarity (Jaccard).
        val sa = na.split(" ").toSet()
        val sb = nb.split(" ").toSet()
        val inter = sa.intersect(sb).size.toDouble()
        val union = sa.union(sb).size.toDouble()
        return if (union == 0.0) 0.0 else inter / union
    }

    /** Cache-key prefix used by DownloadUtil for Deezer-resolved streams. */
    const val CACHE_KEY_PREFIX = "deezer:"

    private const val MIN_MATCH_SCORE = 0.55
}
