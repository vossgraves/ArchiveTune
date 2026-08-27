/*
 * ArchiveTune (2026)
 * © ArchiveTuneFork contributors — github.com/vossgraves/ArchiveTune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 License Section 4 & Section 5
 */

package app.atf.media.ui.screens.search

import app.atf.media.constants.SearchProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineSearchNavigationTest {
    @Test
    fun spotifyProviderIsEncodedAlongsideQuery() {
        val route = onlineSearchResultRoute("Beyoncé / live", SearchProvider.SPOTIFY)

        assertTrue(route.endsWith("?provider=SPOTIFY"))
        assertEquals("Beyoncé / live", decodeOnlineSearchQuery(route.substringAfter("search/").substringBefore("?")))
    }

    @Test
    fun youtubeRemainsTheDefaultForExistingCallers() {
        assertTrue(onlineSearchResultRoute("query").endsWith("?provider=YOUTUBE"))
    }
}
