/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.audiosource

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.canvas.AppleMusicProvider
import moe.rukamori.archivetune.spotify.Spotify
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Universal ISRC (International Standard Recording Code) discovery engine.
 *
 * Resolves verified 12-character ISRCs (e.g. "USUM72401994") for incoming track
 * metadata by querying Spotify as primary and Apple Music AMP search as
 * automatic zero-login fallback.
 *
 * Enforces a strict 4-Rule Sanity Gate:
 *  1. 5-Second Duration Gate: Runtime must be within ±5000ms of wanted duration.
 *  2. Version Mismatch Guard: Rejects unexpected live, remix, acoustic, or instrumental takes.
 *  3. Artist Similarity: Token overlap >= 70%.
 *  4. Blacklist Guard: Rejects tribute bands, karaoke, sped up, and cover uploads.
 *
 * Resolved ISRCs are cached in-memory and can be pre-warmed for upcoming queue items.
 */
object IsrcResolver {
    private const val TAG = "IsrcResolver"
    private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L // 24 hours
    private const val DURATION_GATE_MS = 5_000L // 5 seconds
    private const val MIN_ARTIST_OVERLAP = 0.70

    private val STOP_WORDS =
        setOf("the", "a", "an", "of", "and", "feat", "ft", "featuring", "with")

    private val VERSION_TOKENS =
        setOf(
            "remix",
            "acoustic",
            "live",
            "instrumental",
            "demo",
            "edit",
            "mix",
            "version",
            "slowed",
            "reverb",
            "sped",
            "karaoke",
            "tribute",
            "cover",
        )

    private val BLACKLIST_TOKENS =
        setOf(
            "karaoke",
            "tribute",
            "cover band",
            "in the style of",
            "originally performed",
            "piano tribute",
            "guitar tribute",
            "instrumental tribute",
        )

    private val ISRC_REGEX = Regex("[A-Z]{2}[A-Z0-9]{3}[0-9]{7}")

    private val client =
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .build()

    private data class CachedIsrc(
        val isrc: String?,
        val expiresAt: Long,
    )

    private val cache = ConcurrentHashMap<String, CachedIsrc>()

    /**
     * Resolves the verified ISRC for [title] + [artists] + [durationMs].
     * Returns null if no candidate passes the 4-rule sanity gate.
     */
    suspend fun resolve(
        mediaId: String?,
        title: String,
        artists: List<String>,
        durationMs: Long?,
    ): String? {
        if (title.isBlank()) return null
        val cacheKey = cacheKey(mediaId, title, artists)
        val now = System.currentTimeMillis()
        cache[cacheKey]?.let { cached ->
            if (cached.expiresAt > now) return cached.isrc
            cache.remove(cacheKey)
        }

        val resolved = withContext(Dispatchers.IO) {
            // 1. Primary: Spotify Search
            resolveFromSpotify(title, artists, durationMs)
                // 2. Secondary: Apple Music AMP Search (Zero-login fallback)
                ?: resolveFromAppleMusic(title, artists, durationMs)
        }

        cache[cacheKey] = CachedIsrc(resolved, now + CACHE_TTL_MS)
        if (resolved != null) {
            Timber.tag(TAG).i("Resolved ISRC \"%s\" for \"%s - %s\"", resolved, artists.firstOrNull(), title)
        } else {
            Timber.tag(TAG).d("No verified ISRC found for \"%s - %s\"", artists.firstOrNull(), title)
        }
        return resolved
    }

    /** Blocking bridge for non-suspending call sites (e.g. loader threads). */
    fun resolveBlocking(
        mediaId: String?,
        title: String,
        artists: List<String>,
        durationMs: Long?,
    ): String? =
        runBlocking(Dispatchers.IO) {
            resolve(mediaId, title, artists, durationMs)
        }

    /** Manually primes the cache with a known ISRC (e.g., from DB). */
    fun cacheIsrc(
        mediaId: String?,
        title: String,
        artists: List<String>,
        isrc: String,
    ) {
        val normalized = normalizeIsrc(isrc) ?: return
        val key = cacheKey(mediaId, title, artists)
        cache[key] = CachedIsrc(normalized, System.currentTimeMillis() + CACHE_TTL_MS)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Spotify Search
    // ─────────────────────────────────────────────────────────────────────────────

    private suspend fun resolveFromSpotify(
        title: String,
        artists: List<String>,
        durationMs: Long?,
    ): String? =
        runCatching {
            val cleanTitle = cleanSearchTitle(title)
            val primaryArtist = artists.firstOrNull()?.let { cleanArtist(it) }.orEmpty()
            val query = if (primaryArtist.isNotBlank()) "$cleanTitle $primaryArtist" else cleanTitle

            val searchResult = Spotify.search(
                query = query,
                types = listOf("track"),
                limit = 5,
            ).getOrNull() ?: return@runCatching null

            val tracks = searchResult.tracks?.items.orEmpty()
            for (track in tracks) {
                val candidateIsrc = normalizeIsrc(track.isrc) ?: continue
                val candidateArtist = track.artists.joinToString(", ") { it.name }
                val candidateDurationMs = track.durationMs.toLong().takeIf { it > 0 }

                if (verifySanityGate(
                        wantedTitle = title,
                        wantedArtists = artists,
                        wantedDurationMs = durationMs,
                        candidateTitle = track.name,
                        candidateArtist = candidateArtist,
                        candidateDurationMs = candidateDurationMs,
                    )
                ) {
                    Timber.tag(TAG).d("Spotify match verified: %s (ISRC: %s)", track.name, candidateIsrc)
                    return@runCatching candidateIsrc
                }
            }
            null
        }.onFailure {
            Timber.tag(TAG).d(it, "Spotify ISRC lookup failed for \"%s\"", title)
        }.getOrNull()

    // ─────────────────────────────────────────────────────────────────────────────
    // Apple Music AMP Search (Zero-Login Fallback)
    // ─────────────────────────────────────────────────────────────────────────────

    private suspend fun resolveFromAppleMusic(
        title: String,
        artists: List<String>,
        durationMs: Long?,
    ): String? =
        runCatching {
            val devToken = AppleMusicProvider.getDevToken()
            if (devToken.isBlank()) return@runCatching null

            val cleanTitle = cleanSearchTitle(title)
            val primaryArtist = artists.firstOrNull()?.let { cleanArtist(it) }.orEmpty()
            val query = if (primaryArtist.isNotBlank()) "$cleanTitle $primaryArtist" else cleanTitle

            val url = "https://amp-api.music.apple.com/v1/catalog/us/search"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("types", "songs")
                .addQueryParameter("term", query)
                .addQueryParameter("limit", "5")
                .build()

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $devToken")
                .header("Origin", "https://music.apple.com")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@runCatching null

                val root = JSONObject(body)
                val songs = root.optJSONObject("results")
                    ?.optJSONObject("songs")
                    ?.optJSONArray("data") ?: return@runCatching null

                for (i in 0 until songs.length()) {
                    val song = songs.optJSONObject(i) ?: continue
                    val attributes = song.optJSONObject("attributes") ?: continue
                    val rawIsrc = attributes.optString("isrc")
                    val candidateIsrc = normalizeIsrc(rawIsrc) ?: continue
                    val candidateName = attributes.optString("name")
                    val candidateArtist = attributes.optString("artistName")
                    val candidateDurationMs = attributes.optLong("durationInMillis").takeIf { it > 0 }

                    if (verifySanityGate(
                            wantedTitle = title,
                            wantedArtists = artists,
                            wantedDurationMs = durationMs,
                            candidateTitle = candidateName,
                            candidateArtist = candidateArtist,
                            candidateDurationMs = candidateDurationMs,
                        )
                    ) {
                        Timber.tag(TAG).d("Apple Music match verified: %s (ISRC: %s)", candidateName, candidateIsrc)
                        return@runCatching candidateIsrc
                    }
                }
            }
            null
        }.onFailure {
            Timber.tag(TAG).d(it, "Apple Music ISRC lookup failed for \"%s\"", title)
        }.getOrNull()

    // ─────────────────────────────────────────────────────────────────────────────
    // 4-Rule Sanity Gate
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Returns true only if the candidate satisfies all 4 verification rules.
     */
    fun verifySanityGate(
        wantedTitle: String,
        wantedArtists: List<String>,
        wantedDurationMs: Long?,
        candidateTitle: String,
        candidateArtist: String,
        candidateDurationMs: Long?,
    ): Boolean {
        if (candidateTitle.isBlank()) return false

        // 1. Blacklist check (tribute bands, karaoke, cover bands)
        val candidateArtistNorm = normalize(candidateArtist)
        val candidateTitleNorm = normalize(candidateTitle)
        if (BLACKLIST_TOKENS.any { candidateArtistNorm.contains(it) || candidateTitleNorm.contains(it) }) {
            Timber.tag(TAG).v("Rejected candidate \"%s\" by \"%s\": Blacklisted token", candidateTitle, candidateArtist)
            return false
        }

        // 2. 5-Second Duration Gate
        if (wantedDurationMs != null && candidateDurationMs != null) {
            val diff = abs(wantedDurationMs - candidateDurationMs)
            if (diff > DURATION_GATE_MS) {
                Timber.tag(TAG).v("Rejected candidate \"%s\": Duration diff %d ms > 5s gate", candidateTitle, diff)
                return false
            }
        }

        // 3. Strict Version Mismatch Guard
        val wantedPadded = " ${normalize(wantedTitle)} "
        val candidatePadded = " $candidateTitleNorm "
        if (hasVersionMismatch(wantedPadded, candidatePadded)) {
            Timber.tag(TAG).v("Rejected candidate \"%s\": Version mismatch with \"%s\"", candidateTitle, wantedTitle)
            return false
        }

        // 4. Artist Similarity Overlap
        if (wantedArtists.isNotEmpty() && candidateArtist.isNotBlank()) {
            val candidateTokens = significantTokens(candidateArtistNorm)
            val wantedAllTokens = wantedArtists.flatMap { significantTokens(normalize(it)) }.toSet()
            val fullOverlap = tokenOverlap(wantedAllTokens, candidateTokens)
            val anyArtistContained = wantedArtists.any { wanted ->
                val wantedTokens = significantTokens(normalize(wanted))
                wantedTokens.isNotEmpty() && (wantedTokens.all { it in candidateTokens } || tokenOverlap(wantedTokens, candidateTokens) >= MIN_ARTIST_OVERLAP)
            }

            if (fullOverlap < MIN_ARTIST_OVERLAP && !anyArtistContained) {
                Timber.tag(TAG).v("Rejected candidate \"%s\": Artist mismatch (fullOverlap=%.2f, contained=%s)", candidateTitle, fullOverlap, anyArtistContained)
                return false
            }
        }

        return true
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Text Normalization & Helpers
    // ─────────────────────────────────────────────────────────────────────────────

    fun normalizeIsrc(value: String?): String? {
        val compact = value?.uppercase(Locale.US)?.replace(Regex("[^A-Z0-9]"), "") ?: return null
        return ISRC_REGEX.find(compact)?.value
    }

    private fun cacheKey(
        mediaId: String?,
        title: String,
        artists: List<String>,
    ): String =
        mediaId?.takeIf { it.isNotBlank() }
            ?: (normalize(title) + "|" + artists.joinToString(",") { normalize(it) })

    private fun cleanSearchTitle(title: String): String =
        title
            .replace(Regex("""\s*[\[(]\s*(?:official\s*(?:music\s*)?video|video|audio|lyrics?|visualizer|mv|hd|4k|remaster(?:ed)?)\s*[\])]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*[\[(]\s*(?:feat\.?|ft\.?|featuring)\b.*?[\])]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*-\s*(?:official|audio|video|lyrics?)\b.*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun cleanArtist(artist: String): String =
        artist
            .replace(Regex("""\s*-\s*Topic$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*(?:feat\.?|ft\.?|featuring)\b.*$""", RegexOption.IGNORE_CASE), "")
            .trim()

    private fun hasVersionMismatch(
        wanted: String,
        candidate: String,
    ): Boolean =
        VERSION_TOKENS.any { token ->
            wanted.contains(" $token ") != candidate.contains(" $token ")
        }

    private fun significantTokens(value: String): Set<String> =
        value
            .split(' ')
            .map { it.trim() }
            .filter { it.length >= 2 && it !in STOP_WORDS }
            .toSet()

    private fun tokenOverlap(
        wanted: Set<String>,
        candidate: Set<String>,
    ): Double {
        if (wanted.isEmpty() || candidate.isEmpty()) return 0.0
        val shared = wanted.intersect(candidate).size
        return shared.toDouble() / wanted.size.coerceAtLeast(candidate.size).toDouble()
    }

    private fun normalize(value: String?): String =
        value
            ?.lowercase(Locale.US)
            ?.let { Normalizer.normalize(it, Normalizer.Form.NFD) }
            ?.replace(Regex("\\p{Mn}+"), "")
            ?.replace(Regex("[^a-z0-9]+"), " ")
            ?.trim()
            .orEmpty()
}
