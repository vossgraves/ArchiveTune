/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.lyrics

import android.content.Context
import moe.rukamori.archivetune.constants.EnablePaxsenixNeteaseLyricsKey
import moe.rukamori.archivetune.paxsenix.PaxsenixLyrics
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get

object PaxsenixNeteaseLyricsProvider : LyricsProvider {
    override val name = "Paxsenix: NetEase"

    // Paxsenix retired every provider endpoint except /apple-music/lyrics: the
    // others answer 403 with "This endpoint is no longer available due to the
    // massive amount of traffic and the lack of support needed to keep it
    // running." Verified against the live API — the 403 is unconditional and
    // is returned with a valid key, an invalid key and no key at all, so it is
    // not a quota or auth problem.
    //
    // The default is therefore false: left on, this provider burned one
    // guaranteed-failing round trip per song before the lyrics chain could
    // fall through to a working provider. Users who front the API with their
    // own mirror can still switch it back on.
    override fun isEnabled(context: Context): Boolean = context.dataStore[EnablePaxsenixNeteaseLyricsKey] ?: false

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> = PaxsenixLyrics.getNeteaseLyrics(title, artist, duration)

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
        callback: (String) -> Unit,
    ) {
        getLyrics(id, title, artist, album, duration).onSuccess(callback)
    }
}
