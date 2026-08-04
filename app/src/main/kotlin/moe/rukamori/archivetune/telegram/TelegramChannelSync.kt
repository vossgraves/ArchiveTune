/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Materialises a Telegram channel into a real local playlist so it reuses the normal playlist UI
 * (list tile, rich playlist screen, search / radio / download / menus) instead of a bespoke screen.
 * Opening a channel creates (or reuses) a playlist with a deterministic id, then pages through the
 * channel's audio files in the background, inserting each as a song + playlist membership and
 * seeding its format row. The playlist screen updates reactively as songs arrive.
 */

package moe.rukamori.archivetune.telegram

import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.PlaylistEntity
import moe.rukamori.archivetune.db.entities.PlaylistSongMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.drinkless.tdlib.TdApi
import timber.log.Timber
import java.time.LocalDateTime

object TelegramChannelSync {
    private const val TAG = "TelegramChannelSync"
    private const val PLAYLIST_ID_PREFIX = "LPtg"
    private const val PAGE_FETCH_LIMIT = 100

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Guards against two concurrent syncs of the same channel racing on membership positions.
    private val inFlight = mutableSetOf<Long>()
    private val inFlightLock = Mutex()

    fun playlistId(chatId: Long): String = "$PLAYLIST_ID_PREFIX$chatId"

    /**
     * Creates the playlist row for [chatId] if missing (so it exists before navigation) and returns
     * its id. Safe to call repeatedly — an existing playlist keeps its songs and is just retitled.
     */
    suspend fun ensurePlaylist(
        database: MusicDatabase,
        chatId: Long,
        title: String,
    ): String {
        val id = playlistId(chatId)
        // insert() ignores on conflict, so an existing playlist (and any user tweaks to it) is left
        // untouched; only a brand-new channel creates a row.
        database.withTransaction {
            insert(
                PlaylistEntity(
                    id = id,
                    name = title.ifBlank { "Telegram channel" },
                    browseId = null,
                    isEditable = true,
                    bookmarkedAt = LocalDateTime.now(),
                ),
            )
        }
        return id
    }

    /** Kicks off a background sync of [chatId]'s audio files into its playlist. */
    fun syncAsync(
        database: MusicDatabase,
        chatId: Long,
        title: String,
        losslessOnly: Boolean,
    ) {
        scope.launch {
            val started =
                inFlightLock.withLock {
                    if (chatId in inFlight) false else inFlight.add(chatId)
                }
            if (!started) return@launch
            try {
                sync(database, chatId, title, losslessOnly)
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Sync failed for chat %d", chatId)
            } finally {
                inFlightLock.withLock { inFlight.remove(chatId) }
            }
        }
    }

    private suspend fun sync(
        database: MusicDatabase,
        chatId: Long,
        title: String,
        losslessOnly: Boolean,
    ) {
        val playlistId = playlistId(chatId)
        val filters = listOf(TdApi.SearchMessagesFilterAudio(), TdApi.SearchMessagesFilterDocument())
        var inserted = 0
        val seen = mutableSetOf<Long>()

        // No MAX_TRACKS cap — page through the entire channel. The `page.nextFromMessageId == 0L`
        // check terminates the loop once TDLib reports there are no more audio messages to
        // return, so this is bounded by the actual size of the channel.
        for (filter in filters) {
            var fromMessageId = 0L
            while (true) {
                val page =
                    runCatching {
                        TelegramClient.fetchAudioPage(chatId, fromMessageId, PAGE_FETCH_LIMIT, filter)
                    }.getOrNull() ?: break

                for (track in page.tracks) {
                    if (losslessOnly && !track.isLossless) continue
                    if (!seen.add(track.messageId)) continue
                    if (insertTrack(database, playlistId, track, title)) inserted++
                }

                if (page.nextFromMessageId == 0L) break
                fromMessageId = page.nextFromMessageId
            }
        }
        Timber.tag(TAG).d("Materialised %d tracks for chat %d", inserted, chatId)
    }

    /** Inserts one track as a song + playlist membership + format row. Skips duplicates. */
    private suspend fun insertTrack(
        database: MusicDatabase,
        playlistId: String,
        track: TelegramTrack,
        channelTitle: String,
    ): Boolean =
        database.withTransaction {
            if (checkInPlaylist(playlistId, track.mediaId) > 0) return@withTransaction false
            insert(track.toMediaMetadata(channelTitle))
            upsert(track.toFormatEntity())
            val position = (maxPlaylistSongPosition(playlistId) ?: -1) + 1
            insert(
                PlaylistSongMap(
                    songId = track.mediaId,
                    playlistId = playlistId,
                    position = position,
                ),
            )
            true
        }
}
