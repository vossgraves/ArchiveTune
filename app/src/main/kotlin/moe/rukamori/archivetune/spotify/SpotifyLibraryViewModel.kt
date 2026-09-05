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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
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
        val playlists: StateFlow<List<SpotifyPlaylist>> = repository.playlists
        val isRefreshing: StateFlow<Boolean> = repository.isRefreshing
        val errorMessage: StateFlow<String?> = repository.errorMessage

        // Songs, artists and albums back the Library's other sections on the Spotify source. Held
        // here rather than in the repository because, unlike playlists, they are not cached to disk
        // and nothing outside the Library reads them — a screen that is never opened never fetches.
        private val _likedSongs = MutableStateFlow(SpotifyLibrarySectionState<SpotifyTrack>())
        val likedSongs = _likedSongs.asStateFlow()

        private val _artists = MutableStateFlow(SpotifyLibrarySectionState<SpotifyArtist>())
        val artists = _artists.asStateFlow()

        private val _albums = MutableStateFlow(SpotifyLibrarySectionState<SpotifyAlbum>())
        val albums = _albums.asStateFlow()

        // Play history, for the Spotify pill on the History screen.
        private val _recentlyPlayed = MutableStateFlow(SpotifyLibrarySectionState<SpotifyPlayHistory>())
        val recentlyPlayed = _recentlyPlayed.asStateFlow()

        private val sectionScope = CoroutineScope(
            viewModelScope.coroutineContext + SupervisorJob(viewModelScope.coroutineContext[Job]),
        )
        private val _accountRevision = MutableStateFlow(0)
        val accountRevision = _accountRevision.asStateFlow()

        init {
            viewModelScope.launch(Dispatchers.IO) {
                repository.restoreCachedPlaylists()
            }
            viewModelScope.launch {
                repository.accountChanges.drop(1).collect {
                    sectionScope.coroutineContext.cancelChildren()
                    _likedSongs.value = SpotifyLibrarySectionState()
                    _artists.value = SpotifyLibrarySectionState()
                    _albums.value = SpotifyLibrarySectionState()
                    _recentlyPlayed.value = SpotifyLibrarySectionState()
                    _accountRevision.value += 1
                }
            }
        }

        fun refreshPlaylists() {
            viewModelScope.launch(Dispatchers.IO) {
                repository.refreshPlaylists()
            }
        }

        /** Successful empty sections are cached too; only [force] requests a refresh. */
        fun loadLikedSongs(force: Boolean = false) = load(force, _likedSongs) { repository.likedSongs() }

        fun loadArtists(force: Boolean = false) = load(force, _artists) { repository.libraryArtists() }

        fun loadAlbums(force: Boolean = false) = load(force, _albums) { repository.libraryAlbums() }

        fun loadRecentlyPlayed(force: Boolean = false) =
            load(force, _recentlyPlayed) { repository.recentlyPlayed() }

        private fun <T> load(
            force: Boolean,
            target: MutableStateFlow<SpotifyLibrarySectionState<T>>,
            fetch: suspend () -> List<T>,
        ) {
            sectionScope.launch {
                loadSpotifySection(target, force, fetch)
            }
        }
    }

data class SpotifyLibrarySectionState<T>(
    val items: List<T>? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

@androidx.annotation.MainThread
internal suspend fun <T> loadSpotifySection(
    target: MutableStateFlow<SpotifyLibrarySectionState<T>>,
    force: Boolean = false,
    fetch: suspend () -> List<T>,
) {
    currentCoroutineContext().ensureActive()
    val previous = target.value
    if (previous.isLoading || (!force && previous.items != null)) return
    val loading = previous.copy(isLoading = true, errorMessage = null)
    target.value = loading
    try {
        val items = fetch()
        currentCoroutineContext().ensureActive()
        target.value = SpotifyLibrarySectionState(items = items)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        currentCoroutineContext().ensureActive()
        target.value = previous.copy(errorMessage = error.message ?: error.javaClass.simpleName)
    } finally {
        // An account change may already have reset the state or started its replacement request.
        if (target.value === loading) target.value = previous
    }
}
