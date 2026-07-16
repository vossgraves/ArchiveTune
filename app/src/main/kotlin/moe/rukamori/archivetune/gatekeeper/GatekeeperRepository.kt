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

/**
 * Fork override of upstream's network gatekeeper.
 *
 * Upstream ships this as a remote kill-switch: it phones home to the maintainer's admin server
 * (`DATA_SERVER_URL`/`API_BEARER_TOKEN`) on startup and blocks ALL network access unless that
 * server approves the specific package/version. This is a self-hosted, community-pool fork with no
 * such server, so the check is neutralised — it always returns [GatekeeperResult.Allowed] and
 * unblocks the [NetworkGatekeeper] interceptor.
 *
 * GPL-3.0 (§2/§4/§5) grants the right to modify and run modified versions; the © Rukamori notice
 * above is preserved unaltered as required.
 */
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
