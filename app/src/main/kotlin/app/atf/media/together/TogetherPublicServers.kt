/*
 * ArchiveTune (2026)
 * © ArchiveTuneFork contributors — github.com/vossgraves/ArchiveTune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package app.atf.media.together

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import app.atf.media.constants.TogetherPublicServerUrlKey
import app.atf.media.utils.getAsync
import java.net.URI

internal data class TogetherPublicServer(
    val name: String,
    val url: String,
)

/**
 * Public Listen Together servers operated by the vivimusic community.
 * No auth required; a custom URL can be configured per user.
 */
internal object TogetherPublicServers {
    val Defaults =
        listOf(
            TogetherPublicServer(
                name = "HF (Europe)",
                url = "wss://devilmi-vivi-music-listen-together.hf.space",
            ),
            TogetherPublicServer(
                name = "Render (USA)",
                url = "wss://vivimusic-listen-together.onrender.com",
            ),
        )

    suspend fun selectedUrlOrNull(dataStore: DataStore<Preferences>): String? {
        val custom = dataStore.getAsync(TogetherPublicServerUrlKey)?.trim().orEmpty()
        val candidate = custom.ifBlank { Defaults.first().url }
        val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
        val scheme = uri.scheme?.trim()?.lowercase()
        if (scheme != "ws" && scheme != "wss") return null
        val host = uri.host?.trim().orEmpty()
        if (host.isBlank()) return null
        return candidate.trimEnd('/')
    }

    suspend fun isCustomSelected(dataStore: DataStore<Preferences>): Boolean =
        dataStore.getAsync(TogetherPublicServerUrlKey)?.trim()?.isNotBlank() == true

    suspend fun setCustomUrl(dataStore: DataStore<Preferences>, url: String) {
        dataStore.edit { prefs ->
            if (url.isBlank()) {
                prefs.remove(TogetherPublicServerUrlKey)
            } else {
                prefs[TogetherPublicServerUrlKey] = url.trim().trimEnd('/')
            }
        }
    }
}
