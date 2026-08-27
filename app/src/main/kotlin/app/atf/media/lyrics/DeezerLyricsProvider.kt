/*
 * ArchiveTune (2026)
 * © ArchiveTuneFork contributors — github.com/vossgraves/ArchiveTune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package app.atf.media.lyrics

import android.content.Context
import app.atf.media.constants.EnableDeezerLyricsKey
import app.atf.media.deezer.DeezerAudioProvider
import app.atf.media.utils.dataStore
import app.atf.media.utils.get

object DeezerLyricsProvider : LyricsProvider {
    override val name = "Deezer"

    override fun isEnabled(context: Context): Boolean =
        (context.dataStore[EnableDeezerLyricsKey] ?: true) && DeezerAudioProvider.hasBackends()

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> =
        DeezerAudioProvider.getLyrics(
            title = title,
            artist = artist,
            album = album,
            durationMs = duration.takeIf { it > 0 }?.times(1000L),
        )
}
