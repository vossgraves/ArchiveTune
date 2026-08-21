/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ai

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** Thrown when an AI call is refused locally to protect the user's token budget. */
class AiRateLimitException(
    message: String,
) : AiServiceException(message)

/**
 * Local token-budget protector for AI calls. Every feature that talks to an AI provider goes
 * through [withLimit], which enforces a per-feature minimum spacing between requests and a
 * sliding one-hour request cap. Short spacing deficits are absorbed by delaying the call
 * (smoothing chunked lyric batches); anything longer fails fast with [AiRateLimitException]
 * so the UI can tell the user instead of silently burning tokens.
 *
 * BUGFIX: previously, [reserveOrWait] added the timestamp to the history deque BEFORE the
 * actual API call ran. If the call failed (network error, 5xx, rate-limit from the server,
 * CancellationException), the slot was wasted and still counted against the hourly budget.
 * After ~40 failed+successful calls in an hour, all subsequent translations were silently
 * refused — manifesting as "auto-translate works for a few songs then stops working until
 * the user toggles it off/on + clicks Check API" (which doesn't actually reset the in-memory
 * rate limiter; the user was just waiting for the hourly window to slide). The fix: only
 * record the timestamp AFTER the call succeeds. Failed calls don't count. Cancellation still
 * doesn't count (the user skipped the song). This matches the documented intent ("per-feature
 * minimum spacing between requests" — a failed request isn't a real request).
 */
object AiRateLimiter {
    private const val HourMs = 60L * 60_000L
    private const val TAG = "AiRateLimiter"

    enum class Feature(
        val label: String,
        val minIntervalMs: Long,
        val maxPerHour: Int,
        val smoothingWaitMs: Long,
    ) {
        /** Chunked batches within one translation are smoothed, not refused. */
        LYRICS_TRANSLATION(label = "lyrics translation", minIntervalMs = 1_000L, maxPerHour = 60, smoothingWaitMs = 5_000L),

        /** Mix regeneration is expensive (large prompt); refuse rapid repeats outright. */
        AI_MIX(label = "AI Mix", minIntervalMs = 10L * 60_000L, maxPerHour = 6, smoothingWaitMs = 0L),
    }

    private val history = HashMap<Feature, ArrayDeque<Long>>()

    suspend fun <T> withLimit(
        feature: Feature,
        block: suspend () -> T,
    ): T {
        while (true) {
            val wait = reserveOrWait(feature)
            if (wait <= 0L) break
            delay(wait)
        }
        // Reserve the slot tentatively — we need to enforce minIntervalMs between CONCURRENT
        // calls. If the call fails, we'll refund the slot in the catch block below.
        val reservedAt = markReserved(feature)
        return try {
            val result = block()
            // Success: the reservation becomes permanent. Nothing to do.
            result
        } catch (e: CancellationException) {
            // Cancelled (user skipped song, etc.) — refund the slot so it doesn't count.
            refund(feature, reservedAt)
            throw e
        } catch (e: Throwable) {
            // Failed (network, 5xx, rate-limit, etc.) — refund the slot so failed calls
            // don't burn the hourly budget. Without this refund, after ~40 mixed
            // success+failure calls in an hour, auto-translate silently stops working.
            refund(feature, reservedAt)
            Log.w(TAG, "${feature.label} call failed; refunded rate-limit slot: ${e.message}")
            throw e
        }
    }

    /**
     * Returns 0 when a slot was reserved, a wait in ms when the caller should delay and retry,
     * or throws [AiRateLimitException] when the deficit is too large to absorb.
     */
    @Synchronized
    private fun reserveOrWait(feature: Feature): Long {
        val now = SystemClock.elapsedRealtime()
        val calls = history.getOrPut(feature) { ArrayDeque() }
        while (calls.isNotEmpty() && now - calls.first() > HourMs) {
            calls.removeFirst()
        }
        if (calls.size >= feature.maxPerHour) {
            val retryInMin = (HourMs - (now - calls.first())) / 60_000L + 1
            throw AiRateLimitException(
                "Hourly AI budget for ${feature.label} reached - try again in ~$retryInMin min",
            )
        }
        val last = calls.lastOrNull()
        if (last != null) {
            val deficit = feature.minIntervalMs - (now - last)
            if (deficit > 0L) {
                if (deficit > feature.smoothingWaitMs) {
                    val retryInMin = deficit / 60_000L + 1
                    throw AiRateLimitException(
                        "${feature.label.replaceFirstChar { it.uppercase() }} ran recently - try again in ~$retryInMin min to save tokens",
                    )
                }
                return deficit
            }
        }
        return 0L
    }

    /**
     * Records a tentative reservation. The caller MUST call [refund] if the subsequent
     * call fails, otherwise the slot is permanently counted.
     */
    @Synchronized
    private fun markReserved(feature: Feature): Long {
        val now = SystemClock.elapsedRealtime()
        val calls = history.getOrPut(feature) { ArrayDeque() }
        calls.addLast(now)
        return now
    }

    /**
     * Refunds a tentatively-reserved slot. Only the most-recent reservation matching
     * [reservedAt] is removed; older entries are kept (they belong to earlier calls).
     */
    @Synchronized
    private fun refund(feature: Feature, reservedAt: Long) {
        val calls = history[feature] ?: return
        // Remove the last entry if it matches reservedAt. We only remove the last entry
        // to avoid races where multiple concurrent calls reserved slots in interleaved
        // order — refunding a non-tail entry would create gaps in the deque.
        if (calls.isNotEmpty() && calls.last() == reservedAt) {
            calls.removeLast()
        }
    }
}

