/*
 * ArchiveTune (2026)
 * © ArchiveTuneFork contributors — github.com/vossgraves/ArchiveTune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package app.atf.media.ui.player

import kotlin.math.abs

internal const val AppleMusicLyricsControlsAutoHideDelayMs = 5_000L

internal fun shouldAutoHideAppleMusicControls(
    lyricsOpen: Boolean,
    queueOpen: Boolean,
    enabled: Boolean,
): Boolean = enabled && (lyricsOpen || queueOpen)

internal fun isAppleMusicLyricsDismissDrag(
    deltaX: Float,
    deltaY: Float,
    threshold: Float,
): Boolean = deltaY >= threshold && deltaY >= abs(deltaX)
