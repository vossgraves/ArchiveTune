package moe.rukamori.archivetune.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStreamRecoveryTrackerTest {
    @Test
    fun `allows bounded retries for a stream that repeatedly fails`() {
        val tracker = PlaybackStreamRecoveryTracker(maxAttemptsPerMediaItem = 3)

        assertTrue(tracker.registerRetryAttempt("video-id"))
        assertTrue(tracker.registerRetryAttempt("video-id"))
        assertTrue(tracker.registerRetryAttempt("video-id"))
        assertFalse(tracker.registerRetryAttempt("video-id"))
    }

    @Test
    fun `resets retry budget after recovery or media change`() {
        val tracker = PlaybackStreamRecoveryTracker(maxAttemptsPerMediaItem = 1)

        assertTrue(tracker.registerRetryAttempt("first"))
        assertFalse(tracker.registerRetryAttempt("first"))
        tracker.onPlaybackRecovered("first")
        assertTrue(tracker.registerRetryAttempt("first"))
        assertTrue(tracker.registerRetryAttempt("second"))
    }
}
