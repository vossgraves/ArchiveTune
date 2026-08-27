/*
 * ArchiveTune (2026)
 * © ArchiveTuneFork contributors — github.com/vossgraves/ArchiveTune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package app.atf.media.ads.domain

import javax.inject.Inject

internal enum class SupportPageOpenResult {
    Opened,
    Unavailable,
}

internal interface SupportPageRepository {
    fun openSupportPage(): SupportPageOpenResult
}

internal class OpenSupportPageUseCase
    @Inject
    constructor(
        private val repository: SupportPageRepository,
    ) {
        operator fun invoke(): SupportPageOpenResult = repository.openSupportPage()
    }
