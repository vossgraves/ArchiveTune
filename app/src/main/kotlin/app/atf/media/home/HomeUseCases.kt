/*
 * ArchiveTune (2026)
 * © ArchiveTuneFork contributors — github.com/vossgraves/ArchiveTune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package app.atf.media.home

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import app.atf.media.constants.QuickPicks
import app.atf.media.constants.QuickPicksDisplayMode
import javax.inject.Inject

class ObserveHomePresentationPreferencesUseCase
    @Inject
    constructor(
        private val repository: HomeRepository,
    ) {
        operator fun invoke(): Flow<HomePresentationPreferences> =
            combine(
                repository.showCategoryChips,
                repository.quickPicksDisplayMode,
                repository.quickPicksMode,
                repository.showTonalBackdrop,
                repository.minimalHomeMode,
            ) { showCategoryChips, quickPicksDisplayMode, quickPicksMode, showTonalBackdrop, minimalHomeMode ->
                HomePresentationPreferences(
                    showCategoryChips = showCategoryChips,
                    quickPicksDisplayMode = quickPicksDisplayMode,
                    quickPicksMode = quickPicksMode,
                    showTonalBackdrop = showTonalBackdrop,
                    minimalHomeMode = minimalHomeMode,
                )
            }
    }

@Immutable
data class HomePresentationPreferences(
    val showCategoryChips: Boolean,
    val quickPicksDisplayMode: QuickPicksDisplayMode,
    val quickPicksMode: QuickPicks,
    val showTonalBackdrop: Boolean,
    val minimalHomeMode: Boolean = false,
)
