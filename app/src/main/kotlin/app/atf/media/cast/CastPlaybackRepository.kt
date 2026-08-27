/*
 * ArchiveTune (2026)
 * © ArchiveTuneFork contributors — github.com/vossgraves/ArchiveTune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package app.atf.media.cast

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.StateFlow

fun interface CastMediaItemResolver {
    fun resolveForCast(mediaItem: MediaItem): MediaItem
}

interface CastPlaybackRepository {
    val screenState: StateFlow<CastScreenState>

    fun createPlayer(
        context: Context,
        localPlayer: ExoPlayer,
        mediaItemResolver: CastMediaItemResolver,
    ): Player

    /**
     * Releases a session-facing player previously returned by [createPlayer] after it has been
     * replaced (e.g. crossfade promotion swapped the active local player). No-op for
     * implementations whose player IS the local player.
     */
    fun releasePlayer(player: Player) {}

    fun disconnect()

    fun setVolume(volume: Float)
}
