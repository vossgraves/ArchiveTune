/*
 * ArchiveTune (2026)
 * © ArchiveTuneFork contributors — github.com/vossgraves/ArchiveTune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package app.atf.media.playlist

import android.net.Uri
import app.atf.media.repository.PlaylistCoverRepository
import javax.inject.Inject

class UpdatePlaylistCoverUseCase
    @Inject
    constructor(
        private val repository: PlaylistCoverRepository,
    ) {
        suspend operator fun invoke(
            playlistId: String,
            uri: Uri,
        ) {
            val playlist = requireNotNull(repository.getPlaylist(playlistId))
            if (playlist.browseId == null) {
                repository.setLocalCover(playlist, uri)
            } else {
                repository.setRemoteCover(playlist, uri)
            }
        }
    }

class RemovePlaylistCoverUseCase
    @Inject
    constructor(
        private val repository: PlaylistCoverRepository,
    ) {
        suspend operator fun invoke(playlistId: String) {
            val playlist = requireNotNull(repository.getPlaylist(playlistId))
            if (playlist.browseId == null) {
                repository.removeLocalCover(playlist)
            } else {
                repository.removeRemoteCover(playlist)
            }
        }
    }
