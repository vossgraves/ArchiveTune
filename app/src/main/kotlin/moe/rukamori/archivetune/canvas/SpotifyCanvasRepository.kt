/*
 * ArchiveTune (2026)
 * © vossgraves — github.com/vossgraves
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.canvas

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import moe.rukamori.archivetune.constants.SpotifySpDcKey
import moe.rukamori.archivetune.utils.dataStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Spotify Canvas session state + lookups without the WebView harvester.
 *
 * Upstream 15.0.0 gates lookups on a SpotifyCanvasSessionRepository that scrapes
 * a web-player token out of a WebView. The fork already mints the same
 * web-player internal token through its App.kt token bridge
 * (SpotifyLibraryRepository.ensureAccessToken), so the WebView is pure overhead
 * here: connected-ness is just "an sp_dc cookie exists", and lookups go
 * straight to the provider that already holds the bridge token.
 */
@Singleton
class SpotifyCanvasRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val connected =
        context.dataStore.data
            .map { it[SpotifySpDcKey].orEmpty().isNotBlank() }
            .distinctUntilChanged()

    suspend fun isHealthy(): Boolean = SpotifyCanvasProvider.isHealthy()
}
