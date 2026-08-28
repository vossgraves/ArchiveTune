/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playlist

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.QobuzTokensKey
import moe.rukamori.archivetune.constants.TidalAccessTokenKey
import moe.rukamori.archivetune.constants.TidalCountryCodeKey
import moe.rukamori.archivetune.qobuz.QobuzToken
import moe.rukamori.archivetune.utils.PoolAccountManager
import moe.rukamori.archivetune.utils.dataStore

/**
 * Collects the credentials [CrossServicePlaylistImporter] needs for the
 * services whose playlist APIs reject anonymous reads (Tidal and Qobuz).
 *
 * Mirrors the precedence used by the playback resolvers: the user's own
 * linked account first, then a shared community Source Pool account. Nothing
 * here throws — a missing credential simply comes back null and the importer
 * turns it into a "sign in first" message.
 */
object CrossServiceImportCredentials {

    suspend fun load(context: Context): CrossServicePlaylistImporter.Credentials =
        withContext(Dispatchers.IO) {
            // Warm the pool cache from disk so a cold start still has accounts.
            runCatching { PoolAccountManager.loadCached(context) }

            val prefs = runCatching { context.dataStore.data.first() }.getOrNull()

            val userTidalToken = prefs?.get(TidalAccessTokenKey)?.takeIf { it.isNotBlank() }
            val poolTidal = PoolAccountManager.tidalAccounts().firstOrNull()
            val tidalToken = userTidalToken ?: poolTidal?.token?.takeIf { it.isNotBlank() }
            val tidalCountry = prefs?.get(TidalCountryCodeKey)?.takeIf { it.isNotBlank() }
                ?: poolTidal?.countryCode?.takeIf { it.isNotBlank() }
                ?: "US"

            // Qobuz needs the app_id alongside the auth token; a token without
            // one can't sign requests, so only complete pairs are used.
            val qobuz = QobuzToken.listFromJson(prefs?.get(QobuzTokensKey))
                .firstOrNull { it.token.isNotBlank() && it.appId.isNotBlank() }
                ?.let { it.appId to it.token }
                ?: PoolAccountManager.qobuzAccounts()
                    .firstOrNull { it.token.isNotBlank() && it.appId.isNotBlank() }
                    ?.let { it.appId to it.token }

            CrossServicePlaylistImporter.Credentials(
                tidalAccessToken = tidalToken,
                tidalCountryCode = tidalCountry,
                qobuzAppId = qobuz?.first,
                qobuzAuthToken = qobuz?.second,
            )
        }
}
