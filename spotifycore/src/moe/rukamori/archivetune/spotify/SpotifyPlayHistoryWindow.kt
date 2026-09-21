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
 * Longest wait a `Retry-After` may impose, used only as a sanity bound on a broken header.
 *
 * Spotify really does send day-long values: Stash (the other Kotlin client that logs in with `sp_dc`
 * + TOTP, like this one) records `Retry-After: 86400` on the public Web API for exactly this token
 * class and gates its whole Web API prong on it, noting that "Spotify hands out absurd Retry-Afters
 * (observed 86400s = 24h)". So a day is honoured, not clamped — retrying inside a block cannot
 * succeed, and with a cached window waiting costs nothing. Anything past a day is treated as a stuck
 * value, because nothing observed has ever needed longer.
 */
const val SPOTIFY_RATE_LIMIT_MAX_MS = 24 * 60 * 60 * 1000L

/**
 * Longest wait a history read will sit out before asking again.
 *
 * A `Retry-After` is an instruction, and a short one is worth obeying: waiting it out is the
 * difference between an empty screen and a filled one. A day-long one is not — no read is worth
 * holding a screen's coroutine open for hours — so a block past this leaves the read to its cached
 * window and its error, which is what a block that long deserves.
 */
const val SPOTIFY_HISTORY_RETRY_MAX_WAIT_MS = 60_000L

/**
 * How stale a delta read's cursor may get before the next read takes the whole window instead.
 *
 * A delta read is only as good as its cursor: a play Spotify inserts *behind* one we already returned
 * is invisible to `after` forever. Listory's collector found exactly that and gave up on cursors for
 * new listens — "the Spotify WEB Api was sometimes not adding the listens in the right order, causing
 * us to miss some listens" — and a full window read every half hour bounds the damage to that window.
 * With a five-minute cache expiry that is one window read in six; the other five stay deltas.
 */
const val SPOTIFY_HISTORY_FULL_READ_INTERVAL_MS = 30 * 60 * 1000L

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
 * Whether a read should take the whole window rather than a delta.
 *
 * False only when the cursor is both present and recent and a full read has happened recently; see
 * [SPOTIFY_HISTORY_FULL_READ_INTERVAL_MS] for why the second condition exists. A caller that has
 * never taken a full read passes 0 for [lastFullReadAtMillis], which reads as "long ago" and asks for
 * one — the right answer for a cold start, and for a process that restarted with a disk cache.
 */
fun needsFullHistoryRead(
    newestPlayedAtMillis: Long?,
    lastFullReadAtMillis: Long,
    nowMillis: Long,
    intervalMillis: Long = SPOTIFY_HISTORY_FULL_READ_INTERVAL_MS,
): Boolean =
    newestPlayedAtMillis == null ||
        nowMillis - newestPlayedAtMillis > intervalMillis ||
        nowMillis - lastFullReadAtMillis > intervalMillis

/**
 * How long to stay off the history endpoint after a 429.
 *
 * [retryAfterSec] is Spotify's `Retry-After` and wins whenever it is usable, because only Spotify
 * knows when the window clears. When the header is missing — the docs only promise it "normally" —
 * the documented rolling 30-second window is the floor, so a short value cannot shorten the wait.
 * See [SPOTIFY_RATE_LIMIT_MAX_MS] for the one value that is not honoured.
 */
fun rateLimitCooldownMillis(retryAfterSec: Long?): Long {
    val fromHeader = retryAfterSec?.takeIf { it > 0 }?.let { it * 1000L }
    return when {
        fromHeader == null -> SPOTIFY_RATE_LIMIT_FALLBACK_MS
        fromHeader > SPOTIFY_RATE_LIMIT_MAX_MS -> SPOTIFY_RATE_LIMIT_MAX_MS
        else -> maxOf(fromHeader, SPOTIFY_RATE_LIMIT_FALLBACK_MS)
    }
}

/**
 * How long the one retry of a rate-limited history read should wait, or null when it must not be
 * retried at all.
 *
 * Only a usable `Retry-After` earns that retry: it is Spotify naming the moment the endpoint
 * reopens, and a read with nothing to show is better off waiting for it than surfacing "rate
 * limited" while the plays are seconds away. What is waited out is the *cooldown*, not the header
 * verbatim, so the wait is the same window the rest of the app is gated on and the retry cannot be
 * turned away by that gate — see [rateLimitCooldownMillis] for why the header is floored and
 * [SPOTIFY_HISTORY_RETRY_MAX_WAIT_MS] for what a longer block does instead. A read that already has
 * rows to show is never retried — those rows are on screen, and holding the reader open for a
 * refresh gains nothing — and neither is one whose 429 named no usable window, because guessing at
 * the window is what turns one 429 into a loop.
 */
fun historyRetryWaitMillis(
    retryAfterSec: Long?,
    hasCachedRows: Boolean,
): Long? {
    if (hasCachedRows) return null
    if (retryAfterSec == null || retryAfterSec <= 0) return null
    return rateLimitCooldownMillis(retryAfterSec).takeIf { it <= SPOTIFY_HISTORY_RETRY_MAX_WAIT_MS }
}
