/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.audiosource

import java.text.Normalizer
import moe.rukamori.archivetune.constants.AudioSourceType

/**
 * A resolved direct-playable stream from any audio source. All providers normalize their result
 * into this shape so the playback layer can treat them uniformly.
 */
data class DirectStream(
    val uri: String,
    val mimeType: String,
    val codecs: String,
    val contentLength: Long?,
    /** Human-readable label for logging/UI, e.g. "Tidal (account) HI_RES". */
    val label: String,
    val source: AudioSourceType,
    /**
     * The title of the track the provider actually matched, used by the playback layer to gate on
     * title-match accuracy (see [TitleMatch]). Null when the provider resolved a stream by a trusted
     * direct id and no candidate title was scored, in which case the match is treated as exact.
     */
    val matchedTitle: String? = null,
)

/**
 * Title-only fuzzy match scoring used to gate lossless source playback. The playback layer only
 * switches away from YouTube to a lossless source (Tidal/Qobuz) when the resolved track's title
 * matches the requested title with a high similarity ratio, and — when several sources qualify —
 * the source with the highest title similarity wins. Artist/album are deliberately ignored here so
 * that a correct recording is not rejected because of differing artist credit formatting.
 */
object TitleMatch {
    /** Similarity ratio (0.0..1.0) required to accept a lossless source over YouTube. */
    const val ACCEPT_THRESHOLD = 0.95

    /**
     * Normalizes a title for comparison: lowercased, diacritics stripped, feat/version qualifiers
     * removed, and reduced to a compact `a-z0-9 ` token string. Mirrors the per-provider title
     * normalization so the gate agrees with each provider's own candidate scoring.
     */
    fun normalize(value: String?): String =
        (value ?: "")
            .lowercase()
            .let { Normalizer.normalize(it, Normalizer.Form.NFD) }
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9]+"), " ")
            .replace(Regex("""\b(feat|ft|featuring)\b.*$"""), "")
            .replace(Regex("""\b(explicit|clean|remaster|remastered|version|audio|official)\b"""), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    /**
     * Returns the title-match ratio in 0.0..1.0 between [wanted] and [candidate] after
     * normalization, using a Levenshtein-based similarity (1 - distance / longestLength). Two blank
     * titles are treated as a non-match (0.0) so missing metadata never passes the gate.
     */
    fun ratio(
        wanted: String?,
        candidate: String?,
    ): Double {
        val a = normalize(wanted)
        val b = normalize(candidate)
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        val distance = levenshtein(a, b)
        val longest = maxOf(a.length, b.length)
        if (longest == 0) return 0.0
        return 1.0 - distance.toDouble() / longest.toDouble()
    }

    private fun levenshtein(
        a: String,
        b: String,
    ): Int {
        val m = a.length
        val n = b.length
        if (m == 0) return n
        if (n == 0) return m
        var previous = IntArray(n + 1) { it }
        var current = IntArray(n + 1)
        for (i in 1..m) {
            current[0] = i
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] =
                    minOf(
                        current[j - 1] + 1,
                        previous[j] + 1,
                        previous[j - 1] + cost,
                    )
            }
            val tmp = previous
            previous = current
            current = tmp
        }
        return previous[n]
    }
}

/**
 * Pure helpers for the multi-source framework. These operate on already-read preference values so
 * they stay free of any DataStore/Android dependencies and are trivially testable.
 */
object AudioSourceConfig {
    /** Sources that can actually stream lossless/hi-res, in their built-in default priority. */
    val DEFAULT_ORDER: List<AudioSourceType> =
        listOf(
            AudioSourceType.TIDAL,
            AudioSourceType.QOBUZ,
            AudioSourceType.YOUTUBE,
        )

    /** YouTube is the guaranteed fallback and is always enabled, but its position is user-controlled. */
    private val ALWAYS_ENABLED = setOf(AudioSourceType.YOUTUBE)

    private fun parseType(name: String): AudioSourceType? =
        runCatching { AudioSourceType.valueOf(name.trim().uppercase()) }.getOrNull()

    /**
     * Resolves the effective ordered list of ALL sources from the stored CSV, preserving the user's
     * chosen order (including where they placed YouTube) and appending any sources missing from the
     * stored order (e.g. after an app update introduces a new one) in default order. YouTube is
     * guaranteed to be present, but its position is user-controlled: placing it earlier means the
     * app prefers YouTube's own stream over the lossless override sources listed after it.
     */
    fun parseOrder(rawOrder: String?): List<AudioSourceType> {
        val parsed =
            rawOrder
                ?.split(',')
                ?.mapNotNull { parseType(it) }
                ?.distinct()
                .orEmpty()
        val merged = LinkedHashSet(parsed)
        // Append any sources not present in the stored order in their default position. When nothing
        // is stored this yields DEFAULT_ORDER (Tidal, Qobuz, YouTube), i.e. YouTube stays last.
        DEFAULT_ORDER.forEach { merged.add(it) }
        return merged.toList()
    }

    /**
     * Whether a source is enabled. If the stored set is null (never configured), fall back to the
     * provided per-source defaults. YouTube is always enabled.
     */
    fun isEnabled(
        source: AudioSourceType,
        enabledSet: Set<String>?,
        default: Boolean,
    ): Boolean {
        if (source in ALWAYS_ENABLED) return true
        val set = enabledSet ?: return default
        return set.any { parseType(it) == source }
    }

    /**
     * The ordered list of sources to actually attempt for playback resolution: enabled sources in
     * the user's chosen priority order. The single stored order is authoritative — the source at
     * the top of the list is the preferred source — so there is no separate "primary" control to
     * reconcile.
     */
    fun resolutionChain(
        rawOrder: String?,
        enabledSet: Set<String>?,
        defaults: Map<AudioSourceType, Boolean>,
    ): List<AudioSourceType> =
        parseOrder(rawOrder).filter { source ->
            isEnabled(source, enabledSet, defaults[source] ?: false)
        }
}

/**
 * Codec for the per-song "play from" overrides. Stored as `songId=SOURCE` entries joined by `;` in a
 * single preference string so it is picked up by Settings backups. The playback layer forces the
 * chosen source for that song (still subject to the 95% title-match gate); YOUTUBE as an override
 * means "always use YouTube for this song".
 */
object SongSourceOverride {
    fun parse(raw: String?): Map<String, AudioSourceType> {
        if (raw.isNullOrBlank()) return emptyMap()
        val out = LinkedHashMap<String, AudioSourceType>()
        raw.split(';').forEach { entry ->
            val idx = entry.indexOf('=')
            if (idx <= 0) return@forEach
            val id = entry.substring(0, idx).trim()
            val source =
                runCatching { AudioSourceType.valueOf(entry.substring(idx + 1).trim().uppercase()) }
                    .getOrNull()
            if (id.isNotEmpty() && source != null) out[id] = source
        }
        return out
    }

    fun serialize(map: Map<String, AudioSourceType>): String =
        map.entries.joinToString(";") { "${it.key}=${it.value.name}" }

    fun get(
        raw: String?,
        songId: String,
    ): AudioSourceType? = parse(raw)[songId]

    /** Returns the updated raw string with [songId] set to [source], or cleared when [source] is null. */
    fun withOverride(
        raw: String?,
        songId: String,
        source: AudioSourceType?,
    ): String {
        val map = LinkedHashMap(parse(raw))
        if (source == null) map.remove(songId) else map[songId] = source
        return serialize(map)
    }
}
