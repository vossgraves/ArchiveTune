/*
 * ArchiveTune (2026)
 * © ArchiveTuneFork contributors — github.com/vossgraves/ArchiveTune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package app.atf.media.library

import kotlinx.coroutines.flow.Flow
import app.atf.media.repository.LibraryTopMixRepository
import javax.inject.Inject

class ObserveLibraryTopMixesUseCase
    @Inject
    constructor(
        private val repository: LibraryTopMixRepository,
    ) {
        operator fun invoke(): Flow<List<LibraryTopMix>> = repository.observePersistedTopMixes()
    }
