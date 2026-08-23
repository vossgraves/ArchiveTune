/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import moe.rukamori.archivetune.constants.PlayerStreamClient
import moe.rukamori.archivetune.innertube.PlaybackAuthState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YTPlayerUtilsTest {
    @Test
    fun manualModeUsesOnlySelectedClient() {
        val clients =
            YTPlayerUtils.buildStreamClientOrder(
                preferredStreamClient = PlayerStreamClient.IOS,
                authState = PlaybackAuthState.EMPTY,
                autoChoosePlaybackClient = false,
            )

        assertEquals(1, clients.size)
        assertEquals("IOS", clients.single().clientName)
    }

    @Test
    fun automaticModeIsBoundedAndStartsWithSelectedClient() {
        val clients =
            YTPlayerUtils.buildStreamClientOrder(
                preferredStreamClient = PlayerStreamClient.ANDROID_MUSIC,
                authState = PlaybackAuthState.EMPTY,
                autoChoosePlaybackClient = true,
            )

        assertTrue(clients.isNotEmpty())
        assertTrue(clients.size <= 6)
        assertEquals("ANDROID_MUSIC", clients.first().clientName)
    }
}
