/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.spotify

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.spotify.models.SpotifyAlbum
import moe.rukamori.archivetune.spotify.models.SpotifyArtist
import moe.rukamori.archivetune.spotify.models.SpotifyPlayHistory
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylist
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import javax.inject.Inject

@HiltViewModel
class SpotifyLibraryViewModel
    @Inject
    constructor(
        private val repository: SpotifyLibraryRepository,
    ) : ViewModel() {
        val playlists: StateFlow<List<SpotifyPlaylist>> =
            repository.playlists.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        val isRefreshing: StateFlow<Boolean> =
            repository.isRefreshing.stateIn(viewModelScope, SharingStarted.Lazily, false)

        val errorMessage: StateFlow<String?> =
            repository.errorMessage.stateIn(viewModelScope, SharingStarted.Lazily, null)

        // Songs, artists and albums back the Library's other sections on the Spotify source. Held
        // here rather than in the repository because, unlike playlists, they are not cached to disk
        // and nothing outside the Library reads them — a screen that is never opened never fetches.
        private val _likedSongs = MutableStateFlow<List<SpotifyTrack>>(emptyList())
        val likedSongs: StateFlow<List<SpotifyTrack>> = _likedSongs.asStateFlow()

        private val _artists = MutableStateFlow<List<SpotifyArtist>>(emptyList())
        val artists: StateFlow<List<SpotifyArtist>> = _artists.asStateFlow()

        private val _albums = MutableStateFlow<List<SpotifyAlbum>>(emptyList())
        val albums: StateFlow<List<SpotifyAlbum>> = _albums.asStateFlow()

        // Play history, for the Spotify pill on the History screen.
        private val _recentlyPlayed = MutableStateFlow<List<SpotifyPlayHistory>>(emptyList())
        val recentlyPlayed: StateFlow<List<SpotifyPlayHistory>> = _recentlyPlayed.asStateFlow()

        private val _isLoadingSection = MutableStateFlow(false)
        val isLoadingSection: StateFlow<Boolean> = _isLoadingSection.asStateFlow()

        init {
            viewModelScope.launch(Dispatchers.IO) {
                repository.restoreCachedPlaylists()
            }
        }

        fun refreshPlaylists() {
            viewModelScope.launch(Dispatchers.IO) {
                repository.refreshPlaylists()
            }
        }

        /**
         * Loads a section's contents, skipping the fetch when it already holds something unless
         * [force] — so paging back and forth between the Library's tabs does not re-fetch the whole
         * library each time, while pull-to-refresh still does.
         */
        fun loadLikedSongs(force: Boolean = false) = load(force, _likedSongs) { repository.likedSongs() }

        fun loadArtists(force: Boolean = false) = load(force, _artists) { repository.libraryArtists() }

        fun loadAlbums(force: Boolean = false) = load(force, _albums) { repository.libraryAlbums() }

        fun loadRecentlyPlayed(force: Boolean = false) =
            load(force, _recentlyPlayed) { repository.recentlyPlayed() }

        private fun <T> load(
            force: Boolean,
            target: MutableStateFlow<List<T>>,
            fetch: suspend () -> List<T>,
        ) {
            if (!force && target.value.isNotEmpty()) return
            viewModelScope.launch(Dispatchers.IO) {
                _isLoadingSection.value = true
                // Swallowed on purpose: an expired session or a network blip leaves the section
                // empty, which the screens already render as "nothing here" rather than crashing
                // the Library out from under a user who only wanted their local songs.
                runCatching { fetch() }.onSuccess { target.value = it }
                _isLoadingSection.value = false
            }
        }
    }
