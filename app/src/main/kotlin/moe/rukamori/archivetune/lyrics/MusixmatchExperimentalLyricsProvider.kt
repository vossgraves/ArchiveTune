/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.lyrics

import android.content.Context
import android.util.Log
import moe.rukamori.archivetune.constants.EnableMusixmatchExperimentalKey
import moe.rukamori.archivetune.musixmatch.Musixmatch
import moe.rukamori.archivetune.utils.GlobalLog
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get

/**
 * App-layer wrapper around the native [Musixmatch] lyrics module.
 *
 * Enabled only when the experimental toggle ([EnableMusixmatchExperimentalKey]) is
 * on; otherwise reports disabled so [LyricsHelper] skips it during the priority
 * race. When enabled, delegates the actual network call to the JVM-only [Musixmatch]
 * object — no Android imports leak into the module.
 *
 * Output type priority (matches the tracker doc):
 *  1. TTML richsync (word-synced)
 *  2. LRC subtitle (line-synced)
 *  3. Plain lyrics
 *
 * Falls through with `Result.failure` if Musixmatch has no match. The native
 * module already does a single token retry on auth failure, so callers don't
 * need their own retry loop.
 */
object MusixmatchExperimentalLyricsProvider : LyricsProvider {
    // Wire the native module's log sink into GlobalLog so its diagnostic messages
    // (token fetch, macro HTTP status, parse failures, output-format selection) are
    // surfaced in the in-app log viewer. Mirrors the pattern used by
    // BetterLyricsProvider and UnisonLyricsProvider.
    init {
        Musixmatch.logger = { message ->
            GlobalLog.append(Log.INFO, "Musixmatch", message)
        }
    }

    override val name = "Musixmatch (experimental)"

    override fun isEnabled(context: Context): Boolean =
        context.dataStore[EnableMusixmatchExperimentalKey] ?: false

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> =
        Musixmatch.getLyrics(
            title = title,
            artist = artist,
            album = album,
            duration = duration,
        )

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
        callback: (String) -> Unit,
    ) {
        Musixmatch.getAllLyrics(
            title = title,
            artist = artist,
            album = album,
            duration = duration,
            callback = callback,
        )
    }
}
