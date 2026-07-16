/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.gatekeeper

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import moe.rukamori.archivetune.innertube.NetworkGatekeeper
import javax.inject.Inject
import javax.inject.Singleton

sealed interface GatekeeperResult {
    data object Allowed : GatekeeperResult

    data class Blocked(
        val message: String,
    ) : GatekeeperResult
}

@Singleton
class GatekeeperRepository
    @Inject
    constructor(
        @Suppress("unused") @ApplicationContext private val context: Context,
    ) {
        suspend fun checkAccess(): GatekeeperResult {
            NetworkGatekeeper.setConnectionBlocked(false)
            return GatekeeperResult.Allowed
        }
    }
