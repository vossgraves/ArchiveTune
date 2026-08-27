/*
 * ArchiveTune (2026)
 * © ArchiveTuneFork contributors — github.com/vossgraves/ArchiveTune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package app.atf.media.downloads

import androidx.media3.exoplayer.offline.Download
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import app.atf.media.db.MusicDatabase
import app.atf.media.db.entities.Album
import app.atf.media.db.entities.Playlist
import app.atf.media.db.entities.PlaylistSongMap
import app.atf.media.db.entities.Song
import app.atf.media.playback.DownloadUtil
import javax.inject.Inject
import javax.inject.Singleton

data class DownloadRepositorySnapshot(
    val songs: List<Song>,
    val albums: List<Album>,
    val playlists: List<Playlist>,
    val playlistSongMaps: List<PlaylistSongMap>,
    val downloads: Map<String, Download>,
)

interface DownloadRepository {
    fun observeDownloads(): Flow<DownloadRepositorySnapshot>

    fun pause(songIds: Collection<String>)

    fun resume(songIds: Collection<String>)

    fun remove(songIds: Collection<String>)
}

@Singleton
class Media3DownloadRepository
    @Inject
    constructor(
        private val database: MusicDatabase,
        private val downloadUtil: DownloadUtil,
    ) : DownloadRepository {
        override fun observeDownloads(): Flow<DownloadRepositorySnapshot> =
            combine(
                database.allSongs(),
                database.allAlbumsForDownloads(),
                database.allPlaylistsForDownloads(),
                database.allPlaylistSongMapsForDownloads(),
            ) { songs, albums, playlists, playlistSongMaps ->
                DownloadRepositorySnapshot(
                    songs = songs,
                    albums = albums,
                    playlists = playlists,
                    playlistSongMaps = playlistSongMaps,
                    downloads = emptyMap(),
                )
            }.combine(downloadUtil.downloads) { snapshot, downloads ->
                snapshot.copy(downloads = downloads)
            }.flowOn(Dispatchers.IO)

        override fun pause(songIds: Collection<String>) {
            songIds.distinct().forEach { songId ->
                downloadUtil.downloadManager.setStopReason(songId, PAUSED_STOP_REASON)
            }
        }

        override fun resume(songIds: Collection<String>) {
            songIds.distinct().forEach { songId ->
                val download = downloadUtil.downloads.value[songId]
                if (download?.state == Download.STATE_FAILED) {
                    downloadUtil.downloadManager.addDownload(download.request)
                } else {
                    downloadUtil.downloadManager.setStopReason(songId, NO_STOP_REASON)
                }
            }
        }

        override fun remove(songIds: Collection<String>) {
            songIds.distinct().forEach(downloadUtil.downloadManager::removeDownload)
        }

        private companion object {
            const val NO_STOP_REASON = 0
            const val PAUSED_STOP_REASON = 1
        }
    }
