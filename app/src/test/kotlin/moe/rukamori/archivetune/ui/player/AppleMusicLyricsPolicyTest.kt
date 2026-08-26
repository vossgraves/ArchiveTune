package moe.rukamori.archivetune.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppleMusicLyricsPolicyTest {
    @Test
    fun `auto hide uses five seconds only when an overlay is open and enabled`() {
        assertEquals(5_000L, AppleMusicLyricsControlsAutoHideDelayMs)
        assertTrue(shouldAutoHideAppleMusicControls(lyricsOpen = true, queueOpen = false, enabled = true))
        assertTrue(shouldAutoHideAppleMusicControls(lyricsOpen = false, queueOpen = true, enabled = true))
        assertFalse(shouldAutoHideAppleMusicControls(lyricsOpen = true, queueOpen = false, enabled = false))
        assertFalse(shouldAutoHideAppleMusicControls(lyricsOpen = false, queueOpen = false, enabled = true))
    }

    @Test
    fun `only a downward vertical drag past the threshold dismisses lyrics`() {
        assertTrue(isAppleMusicLyricsDismissDrag(deltaX = 8f, deltaY = 96f, threshold = 96f))
        assertFalse(isAppleMusicLyricsDismissDrag(deltaX = 0f, deltaY = 95f, threshold = 96f))
        assertFalse(isAppleMusicLyricsDismissDrag(deltaX = 97f, deltaY = 96f, threshold = 96f))
        assertFalse(isAppleMusicLyricsDismissDrag(deltaX = 0f, deltaY = -96f, threshold = 96f))
    }
}
