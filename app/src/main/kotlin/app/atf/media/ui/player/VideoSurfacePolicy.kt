/*
 * ArchiveTune (2026)
 * © ArchiveTuneFork contributors — github.com/vossgraves/ArchiveTune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package app.atf.media.ui.player

import android.os.Build
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/**
 * Port of Flow's [io.github.aedev.flow.player.surface.VideoSurfacePolicy].
 *
 * Decides which surface type (SurfaceView vs TextureView) to use for video playback
 * and whether a re-attached surface needs a same-position seek to resync the codec.
 *
 * Why this exists:
 *  - SurfaceView is hardware-accelerated and doesn't hold stale frames, but on
 *    older API levels swapping the codec's output surface requires re-creating
 *    the codec (an expensive operation that visibly stalls playback for ~1–2s).
 *  - From API 34 (UPSIDE_DOWN_CAKE) onward ExoPlayer's `setOutputSurface` path
 *    can swap surfaces in place without re-creating the codec — making
 *    SurfaceView strictly better on those devices.
 *  - Below API 34 the TextureView is preferred for the inline-then-fullscreen
 *    toggle path because it's a single GPU texture that can move between
 *    parents without re-creating the codec; the trade-off is the well-known
 *    stale-frame issue (mitigated separately by the renderer-kick path).
 *
 * The `shouldResyncOnSurfaceReattach` heuristic mirrors Flow's: when the player
 * is paused (not playWhenReady), not on a live stream, and currently READY or
 * BUFFERING, a same-position seek flushes the codec so the new surface shows a
 * fresh frame aligned to the playhead instead of whatever sample-stream read
 * position the codec had cached before the detach.
 */
@UnstableApi
object VideoSurfacePolicy {
    /** Use SurfaceView for SDK 34+ (supports seamless surface switching). */
    fun usesSurfaceView(sdkInt: Int): Boolean = sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    /**
     * Whether we can restore the video output without a same-position seek after a
     * surface re-attach. Flow's original condition:
     *   isDisplayInteractive && (!usesSurfaceView(sdkInt) || isSurfaceValid)
     */
    fun canRestoreVideoOutput(
        sdkInt: Int,
        isDisplayInteractive: Boolean,
        isSurfaceValid: Boolean,
    ): Boolean = isDisplayInteractive && (!usesSurfaceView(sdkInt) || isSurfaceValid)

    /**
     * Whether a same-position seek is needed after a destroyed video surface comes back.
     *
     * Devices that cannot switch a codec's output surface in place (Media3's
     * `setOutputSurface` workaround list, e.g. many Xiaomi models) re-create the video
     * codec on every surface swap. A re-created codec resumes from the sample-stream
     * read position, not the playhead — while paused that read position can be several
     * seconds ahead, so resuming playback shows a frozen frame until the clock catches
     * up. A same-position seek flushes the codec and realigns video with the playhead.
     *
     * Live streams are excluded: seeking there shifts the live-edge window.
     */
    fun shouldResyncOnSurfaceReattach(
        playWhenReady: Boolean,
        isLive: Boolean,
        playbackState: Int,
    ): Boolean =
        !playWhenReady && !isLive &&
            (playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING)
}
