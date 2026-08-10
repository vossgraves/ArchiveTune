/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.lyrics

import android.content.Context
import moe.rukamori.archivetune.constants.EnableQobuzLyricsKey
import moe.rukamori.archivetune.constants.QobuzTokensKey
import moe.rukamori.archivetune.qobuz.QobuzAudioProvider
import moe.rukamori.archivetune.qobuz.QobuzToken
import moe.rukamori.archivetune.utils.PoolAccountManager
import moe.rukamori.archivetune.utils.PreferenceStore
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get

object QobuzLyricsProvider : LyricsProvider {
    override val name = "Qobuz"

    private fun firstToken(): QobuzToken? {
        val userTokens = QobuzToken.listFromJson(PreferenceStore.get(QobuzTokensKey))
        userTokens.firstOrNull()?.let { return it }
        PoolAccountManager.qobuzAccounts().firstOrNull()?.let { pool ->
            return QobuzToken(
                token = pool.token,
                appId = pool.appId,
                appSecret = pool.appSecret,
                label = "Pool",
            )
        }
        return null
    }

    override fun isEnabled(context: Context): Boolean =
        (context.dataStore[EnableQobuzLyricsKey] ?: true) && firstToken() != null

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> {
        val token = firstToken() ?: return Result.failure(java.io.IOException("no Qobuz token"))
        return QobuzAudioProvider.getLyrics(
            token = token,
            title = title,
            artist = artist,
            album = album,
            durationMs = duration.takeIf { it > 0 }?.times(1000L),
        )
    }
}
