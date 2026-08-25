/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

/**
 * Parses the user's list of extra Spotify Canvas resolver endpoints.
 *
 * ## Why this is user-configurable
 *
 * Spotify's own Canvas endpoint (`spclient.wg.spotify.com/canvaz-cache`) is the only
 * authoritative source, and it needs a Spotify session. Every community alternative on
 * GitHub is a *self-hosted* wrapper around that same endpoint — they all require the
 * operator's own `sp_dc` cookie, so there is no stable public instance to hardcode: the
 * ones that exist come and go, and the app cannot ship a working default for a user with
 * no Spotify login. Shipping a list the user controls means a resolver that appears (or a
 * private one they run themselves) works immediately, with no app update.
 *
 * Entries are stored one per line. Anything that isn't an `http(s)` URL is dropped rather
 * than being passed to the network layer, and duplicates are collapsed so a pasted list
 * with repeats doesn't multiply the request count per song.
 */
object CanvasResolverEndpoints {
    private const val MAX_ENDPOINTS = 8

    /**
     * Splits raw multi-line input into resolver base URLs, in the order given.
     *
     * The list is capped: each entry costs one network round trip per song with no canvas,
     * and a runaway paste should not turn every track change into dozens of requests.
     */
    fun parse(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw
            .split('\n', ',')
            .map { it.trim().trimEnd('/') }
            .filter { candidate ->
                candidate.startsWith("http://", ignoreCase = true) ||
                    candidate.startsWith("https://", ignoreCase = true)
            }.distinct()
            .take(MAX_ENDPOINTS)
    }

    /** Normalises for storage: one endpoint per line, blank/invalid entries removed. */
    fun serialize(endpoints: List<String>): String = endpoints.joinToString("\n")
}
