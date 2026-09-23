/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import kotlin.math.abs

/**
 * Drift-correction decision for the crossfade handoff.
 *
 * This is the one piece of upstream 15.0.0's crossfade policy the fork lacked:
 * equal-power gains and position-advanced checks already exist in
 * [CrossfadePolicy] (outgoingGain/incomingGain, hasAudioAdvanced) and the live
 * MusicService drives through those, so this file carries only the genuinely
 * new drift guard. Upstream wires it at the crossfade handoff to re-seek the
 * secondary player when the two players drift apart mid-fade.
 */
internal fun needsCorrectiveCrossfadeSeek(
    primaryPositionMs: Long,
    secondaryPositionMs: Long,
    maximumDriftMs: Long,
): Boolean {
    require(maximumDriftMs >= 0L)
    return abs(primaryPositionMs - secondaryPositionMs) > maximumDriftMs
}
