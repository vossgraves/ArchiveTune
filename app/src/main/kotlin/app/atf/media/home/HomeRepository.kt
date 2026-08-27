/*
 * ArchiveTune (2026)
 * © ArchiveTuneFork contributors — github.com/vossgraves/ArchiveTune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package app.atf.media.home

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import app.atf.media.constants.DisableBlurKey
import app.atf.media.constants.MinimalHomeModeKey
import app.atf.media.constants.QuickPicks
import app.atf.media.constants.QuickPicksKey
import app.atf.media.constants.QuickPicksDisplayMode
import app.atf.media.constants.QuickPicksDisplayModeKey
import app.atf.media.constants.ShowHomeCategoryChipsKey
import app.atf.media.extensions.toEnum
import app.atf.media.utils.dataStore
import javax.inject.Inject

class HomeRepository
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        val showCategoryChips: Flow<Boolean> =
            context.dataStore.data
                .map { preferences -> preferences[ShowHomeCategoryChipsKey] ?: true }
                .distinctUntilChanged()

        val quickPicksDisplayMode: Flow<QuickPicksDisplayMode> =
            context.dataStore.data
                .map { preferences ->
                    preferences[QuickPicksDisplayModeKey].toEnum(QuickPicksDisplayMode.CARD)
                }.distinctUntilChanged()

        val quickPicksMode: Flow<QuickPicks> =
            context.dataStore.data
                .map { preferences -> preferences[QuickPicksKey].toEnum(QuickPicks.QUICK_PICKS) }
                .distinctUntilChanged()

        val showTonalBackdrop: Flow<Boolean> =
            context.dataStore.data
                .map { preferences -> preferences[DisableBlurKey] != true }
                .distinctUntilChanged()

        /**
         * When `true`, the Home feed collapses to a focused subset
         * (hero + Recently Played + Keep Listening + Live Performances).
         * See [MinimalHomeModeKey] for the full description.
         */
        val minimalHomeMode: Flow<Boolean> =
            context.dataStore.data
                .map { preferences -> preferences[MinimalHomeModeKey] ?: false }
                .distinctUntilChanged()
    }
