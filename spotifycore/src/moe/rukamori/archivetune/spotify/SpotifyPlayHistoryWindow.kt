/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.spotify

import java.time.Instant
import moe.rukamori.archivetune.spotify.models.SpotifyPlayHistory

/**
 * How many plays Spotify's history endpoint will return in one window.
 *
 * `limit` above this is clamped server-side, so it is also the ceiling of any local cache: asking
 * for more cannot happen, and holding more would only be possible by walking `before` backwards
 * into the endpoint's very limit.
 */
const val SPOTIFY_HISTORY_WINDOW = 50

/**
 * How long a 429 keeps us off the history endpoint when Spotify sends no usable `Retry-After`.
 *
 * Spotify documents its Web API limit as a rolling 30-second window, so 30s is the shortest wait
 * that can actually clear it. Retrying inside that window is what turns one 429 into a loop.
 */
const val SPOTIFY_RATE_LIMIT_FALLBACK_MS = 30_000L

/**
 * Longest wait a `Retry-After` may impose. A header past this is clamped rather than obeyed: a
 * misconfigured or stuck value would pin history off for hours, and no observed Spotify 429 has
 * needed minutes for a play-history read.
 */
const val SPOTIFY_RATE_LIMIT_MAX_MS = 15 * 60 * 1000L

/**
 * Epoch millis of the newest play in the list, or null when no entry carries a usable timestamp.
 *
 * This is the endpoint's `after` cursor: it turns "re-read the last 50 plays" into "ask for plays
 * newer than what we already hold", which is what lets a cached window be refreshed for one delta
 * read instead of a full one.
 */
fun List<SpotifyPlayHistory>.newestPlayedAtMillis(): Long? =
    asSequence()
        .mapNotNull { it.playedAt?.let(::playedAtMillis) }
        .maxOrNull()

/**
 * Epoch millis for a `played_at` stamp (`2026-09-20T12:34:56.789Z`), or null when it is absent or
 * unparseable. Null is not an error here: a play without a timestamp still belongs in the history,
 * it just cannot anchor a cursor.
 */
fun playedAtMillis(playedAt: String): Long? =
    runCatching { Instant.parse(playedAt).toEpochMilli() }.getOrNull()

/**
 * Identifies one play: the track, plus when it happened.
 *
 * `played_at` has millisecond precision and two plays of one track never share a millisecond, so the
 * pair is unique. A play with no timestamp falls back to the track alone, which at worst merges
 * duplicate rows of that track instead of duplicating them.
 */
private fun SpotifyPlayHistory.playKey(): String =
    (track?.uri ?: track?.id ?: track?.name.orEmpty()) + "@" + playedAt.orEmpty()

/**
 * [newer] merged in front of [cached], newest first, deduplicated, capped at [limit].
 *
 * A delta read returns only the plays after the cursor, so this merge is what the caller shows
 * instead: the same 50 rows a full window read would have produced, from a request that carried only
 * the new plays. [newer] wins a tie because it came from Spotify most recently and so carries the
 * fresher track metadata. Entries with no timestamp sort last rather than being dropped — they are
 * still plays.
 */
fun mergePlayHistory(
    newer: List<SpotifyPlayHistory>,
    cached: List<SpotifyPlayHistory>,
    limit: Int = SPOTIFY_HISTORY_WINDOW,
): List<SpotifyPlayHistory> {
    val seen = HashSet<String>(newer.size + cached.size)
    val merged = ArrayList<SpotifyPlayHistory>(newer.size + cached.size)
    for (item in newer.asSequence() + cached.asSequence()) {
        if (seen.add(item.playKey())) merged += item
    }
    return merged
        .sortedByDescending { it.playedAt?.let(::playedAtMillis) ?: Long.MIN_VALUE }
        .take(limit)
}

/**
 * How long to stay off the history endpoint after a 429.
 *
 * [retryAfterSec] is Spotify's `Retry-After` and wins whenever it is usable, because only Spotify
 * knows when the window clears. When the header is missing — the docs only promise it "normally" —
 * the documented rolling 30-second window is the floor. See [SPOTIFY_RATE_LIMIT_MAX_MS] for why an
 * absurd value is clamped instead of honoured.
 */
fun rateLimitCooldownMillis(retryAfterSec: Long?): Long {
    val fromHeader = retryAfterSec?.takeIf { it > 0 }?.let { it * 1000L }
    return when {
        fromHeader == null -> SPOTIFY_RATE_LIMIT_FALLBACK_MS
        fromHeader > SPOTIFY_RATE_LIMIT_MAX_MS -> SPOTIFY_RATE_LIMIT_MAX_MS
        else -> maxOf(fromHeader, SPOTIFY_RATE_LIMIT_FALLBACK_MS)
    }
}
