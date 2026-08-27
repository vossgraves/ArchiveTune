/*
 * ArchiveTune (2026)
 * © ArchiveTuneFork contributors — github.com/vossgraves/ArchiveTune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package app.atf.media.playback

/**
 * Keeps YouTube stream recovery bounded while allowing a fresh client/URL to
 * recover from more than one transient failure. A single retry is frequently
 * insufficient: a 403 can invalidate one client and the replacement URL can
 * still be stale or rejected before the next client is selected.
 */
internal class PlaybackStreamRecoveryTracker(
    private val maxAttemptsPerMediaItem: Int = 3,
) {
    private var attemptedMediaId: String? = null
    private var attemptCount = 0

    fun registerRetryAttempt(mediaId: String): Boolean {
        if (attemptedMediaId != mediaId) {
            attemptedMediaId = mediaId
            attemptCount = 0
        }
        if (attemptCount >= maxAttemptsPerMediaItem) return false
        attemptCount++
        return true
    }

    fun onPlaybackRecovered(mediaId: String?) {
        if (mediaId != null && attemptedMediaId == mediaId) {
            attemptedMediaId = null
            attemptCount = 0
        }
    }

    fun onMediaItemChanged(currentMediaId: String?) {
        if (attemptedMediaId != currentMediaId) {
            attemptedMediaId = null
            attemptCount = 0
        }
    }
}
