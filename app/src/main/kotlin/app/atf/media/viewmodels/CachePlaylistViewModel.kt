/*
 * ArchiveTune (2026)
 * © ArchiveTuneFork contributors — github.com/vossgraves/ArchiveTune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package app.atf.media.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.datasource.cache.Cache
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import app.atf.media.constants.HideExplicitKey
import app.atf.media.constants.HideVideoKey
import app.atf.media.db.MusicDatabase
import app.atf.media.db.entities.Song
import app.atf.media.di.DownloadCache
import app.atf.media.di.PlayerCache
import app.atf.media.extensions.filterExplicit
import app.atf.media.extensions.filterVideo
import app.atf.media.utils.dataStore
import app.atf.media.utils.get
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class CachePlaylistViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val database: MusicDatabase,
        @PlayerCache private val playerCache: Cache,
        @DownloadCache private val downloadCache: Cache,
    ) : ViewModel() {
        private val _cachedSongs = MutableStateFlow<List<Song>>(emptyList())
        val cachedSongs: StateFlow<List<Song>> = _cachedSongs

        init {
            viewModelScope.launch(Dispatchers.IO) {
                while (true) {
                    val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                    val hideVideo = context.dataStore.get(HideVideoKey, false)
                    val cachedIds = playerCache.keys.toSet()
                    val downloadedIds = downloadCache.keys.toSet()
                    val pureCacheIds = cachedIds.subtract(downloadedIds)

                    val songs =
                        if (pureCacheIds.isNotEmpty()) {
                            database.getSongsByIds(pureCacheIds.toList())
                        } else {
                            emptyList()
                        }

                    val completeSongs =
                        songs.filter {
                            val contentLength = it.format?.contentLength
                            contentLength != null && playerCache.isCached(it.song.id, 0, contentLength)
                        }

                    if (completeSongs.isNotEmpty()) {
                        database.query {
                            completeSongs.forEach {
                                if (it.song.dateDownload == null) {
                                    update(it.song.copy(dateDownload = LocalDateTime.now()))
                                }
                            }
                        }
                    }

                    _cachedSongs.value =
                        completeSongs
                            .filter { it.song.dateDownload != null }
                            .sortedByDescending { it.song.dateDownload }
                            .filterExplicit(hideExplicit)
                            .filterVideo(hideVideo)

                    delay(1000)
                }
            }
        }

        fun removeSongFromCache(songId: String) {
            playerCache.removeResource(songId)
        }
    }
