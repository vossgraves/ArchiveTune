/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ai

import android.os.SystemClock
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
 */
object AiRateLimiter {
    private const val HourMs = 60L * 60_000L

    enum class Feature(
        val label: String,
        val minIntervalMs: Long,
        val maxPerHour: Int,
        val smoothingWaitMs: Long,
    ) {
        /** Chunked batches within one translation are smoothed, not refused. */
        LYRICS_TRANSLATION(label = "lyrics translation", minIntervalMs = 1_000L, maxPerHour = 40, smoothingWaitMs = 5_000L),

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
        return block()
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
        calls.addLast(now)
        return 0L
    }
}
