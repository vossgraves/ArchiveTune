/*
 * ArchiveTune (2026)
 * © ArchiveTuneFork contributors — github.com/vossgraves/ArchiveTune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package app.atf.media.lyrics

import android.content.Context
import app.atf.media.constants.EnablePaxsenixLyricsKey
import moe.rukamori.archivetune.paxsenix.PaxsenixLyrics
import app.atf.media.utils.dataStore
import app.atf.media.utils.get

object PaxsenixLyricsProvider : LyricsProvider {
    override val name = "Paxsenix (Auto)"

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnablePaxsenixLyricsKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> = PaxsenixLyrics.getLyrics(title, artist, duration)

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
        callback: (String) -> Unit,
    ) {
        PaxsenixLyrics.getAllLyrics(title, artist, duration, callback)
    }
}
