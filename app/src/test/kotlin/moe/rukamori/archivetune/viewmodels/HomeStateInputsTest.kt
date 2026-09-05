/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import moe.rukamori.archivetune.constants.QuickPicks
import moe.rukamori.archivetune.constants.QuickPicksDisplayMode
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.db.entities.SongEntity
import moe.rukamori.archivetune.home.HomePresentationPreferences
import moe.rukamori.archivetune.home.HomeScreenState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeStateInputsTest {
    @Test
    fun localContentIsVisibleWhileRemoteHomeIsStillLoading() {
        val state = inputs(hasLocalContent = true, initialComplete = false, loading = true)
            .toScreenState(isRefreshing = false, isLoadingMore = false)
        assertTrue(state is HomeScreenState.Success)
        assertEquals("local-song", (state as HomeScreenState.Success).uiState.heroPicks.single().id)
    }

    @Test
    fun failedRemoteRefreshDoesNotReplaceAvailableContent() {
        val state = inputs(hasLocalContent = true, initialComplete = true, loading = false, error = 7)
            .toScreenState(isRefreshing = false, isLoadingMore = false)
        assertTrue(state is HomeScreenState.Success)
    }

    @Test
    fun emptyInitialLoadKeepsItsLoadingState() {
        val state = inputs(hasLocalContent = false, initialComplete = false, loading = true)
            .toScreenState(isRefreshing = false, isLoadingMore = false)
        assertEquals(HomeScreenState.Loading, state)
    }

    @Test
    fun offlineEmptyLibraryHasAnActionableErrorState() {
        val state = inputs(hasLocalContent = false, initialComplete = true, loading = false, error = 7)
            .toScreenState(isRefreshing = false, isLoadingMore = false)
        assertEquals(HomeScreenState.Error(7), state)
    }

    @Test
    fun successfulEmptyLibraryStopsLoading() {
        val state = inputs(hasLocalContent = false, initialComplete = true, loading = false)
            .toScreenState(isRefreshing = false, isLoadingMore = false)
        assertEquals(HomeScreenState.Empty, state)
    }

    private fun inputs(
        hasLocalContent: Boolean,
        initialComplete: Boolean,
        loading: Boolean,
        error: Int? = null,
    ): HomeStateInputs {
        val songs = if (hasLocalContent) {
            listOf(Song(SongEntity(id = "local-song", title = "Local song", isLocal = true), emptyList()))
        } else emptyList()
        return HomeStateInputs(
            content = HomeContent(
                local = HomeLocalContent(
                    quickPicks = songs,
                    speedDialItems = emptyList(),
                    forgottenFavorites = emptyList(),
                    keepListening = emptyList(),
                    recentlyPlayed = emptyList(),
                    heroPicks = songs,
                ),
                remote = HomeRemoteContent(
                    homePage = null,
                    remoteQuickPicks = null,
                    similarRecommendations = emptyList(),
                    accountPlaylists = emptyList(),
                    accountName = "",
                    accountImageUrl = null,
                ),
                selectedChip = null,
            ),
            preferences = HomePresentationPreferences(
                showCategoryChips = true,
                quickPicksDisplayMode = QuickPicksDisplayMode.entries.first(),
                quickPicksMode = QuickPicks.QUICK_PICKS,
                showTonalBackdrop = false,
            ),
            isLoading = loading,
            isInitialLoadComplete = initialComplete,
            loadError = error,
        )
    }
}
