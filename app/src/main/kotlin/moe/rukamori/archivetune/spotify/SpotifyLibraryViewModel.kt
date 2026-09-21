/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 * Portions © vossgraves — github.com/vossgraves
 */

package moe.rukamori.archivetune.spotify

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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

        /**
         * Populate the playlist list the first time the Library's Spotify tab opens with an empty
         * cache — disk cache first, network only if still empty. See
         * [SpotifyLibraryRepository.ensurePlaylists].
         */
        fun ensurePlaylists() {
            viewModelScope.launch(Dispatchers.IO) {
                repository.ensurePlaylists()
            }
        }

        /** Successful empty sections are cached too; only [force] requests a refresh. */
        fun loadLikedSongs(force: Boolean = false) = load(force, _likedSongs) { repository.likedSongs() }

        fun loadArtists(force: Boolean = false) = load(force, _artists) { repository.libraryArtists() }

        fun loadAlbums(force: Boolean = false) = load(force, _albums) { repository.libraryAlbums() }

        /**
         * Loads the Spotify play history for the History (and Stats) source.
         *
         * Seeds the section from disk first so history renders offline and instantly, then lets the
         * repository's expiry decide whether a read is owed: a restored-and-fresh cache is left
         * alone, a restored-but-stale one is refreshed. The section loader would otherwise treat
         * "rows exist" as "never fetch again" and keep a week-old history on screen for the life of
         * the process. The read itself — its single-flight and its rate-limit gate — belongs to
         * [SpotifyLibraryRepository.recentlyPlayed], which is why a stale refresh is safe to ask for.
         */
        fun loadRecentlyPlayed(force: Boolean = false) {
            sectionScope.launch {
                if (_recentlyPlayed.value.items == null) {
                    repository.restoreCachedRecentlyPlayed()?.let { cached ->
                        _recentlyPlayed.value = SpotifyLibrarySectionState(items = cached)
                    }
                }
                val stale = _recentlyPlayed.value.items != null && !repository.recentlyPlayedIsFresh()
                loadSpotifySection(target = _recentlyPlayed, force = force || stale) {
                    repository.recentlyPlayed(force = force)
                }
            }
        }

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
    /** When the last attempt failed, as elapsed-realtime millis. Drives [SpotifyRetryCooldownMs]. */
    val failedAtMillis: Long? = null,
)

/**
 * How long a failed section waits before a screen visit may retry it.
 *
 * A failure leaves `items` null, so without this every recomposition that calls a load function
 * fetches again. Against a 429 that is the worst possible response: the retries are what keep the
 * limit in force. Spotify's own limit is a rolling 30-second window, so a minute clears it while
 * still feeling responsive. Pulling to refresh passes `force` and ignores this.
 */
private const val SpotifyRetryCooldownMs = 60_000L

/**
 * Whether a Spotify error message means the rate limit was hit.
 *
 * The client has three shapes for this: the REST client throws the literal "Rate limited"
 * (`spotifycore` Spotify.kt), the auth layer surfaces "Request failed with HTTP 429", and a raw
 * response body can carry "Too Many Requests". Matching only the status code left the retry
 * cooldown and the friendly stats message dead for the common case, so every reader now goes
 * through here.
 */
internal fun isSpotifyRateLimitMessage(message: String?): Boolean =
    message != null &&
        (
            message.contains("429") ||
                message.contains("Too Many Requests", ignoreCase = true) ||
                message.contains("Rate limit", ignoreCase = true)
        )

/**
 * Monotonic milliseconds for the retry cooldown.
 *
 * Deliberately not `android.os.SystemClock`: nothing here needs the Android clock, and reaching for
 * it made the whole function untestable off-device — the stub android.jar throws on every unmocked
 * call, so `SpotifyLibraryViewModelTest` failed with a "not mocked" RuntimeException instead of
 * asserting anything about the cooldown it exists to pin.
 */
private fun monotonicMillis(): Long = System.nanoTime() / 1_000_000

@androidx.annotation.MainThread
internal suspend fun <T> loadSpotifySection(
    target: MutableStateFlow<SpotifyLibrarySectionState<T>>,
    force: Boolean = false,
    fetch: suspend () -> List<T>,
) {
    currentCoroutineContext().ensureActive()
    val previous = target.value
    if (previous.isLoading || (!force && previous.items != null)) return
    if (!force) {
        val failedAt = previous.failedAtMillis
        val cooldown = if (isSpotifyRateLimitMessage(previous.errorMessage)) SpotifyRetryCooldownMs * 5 else SpotifyRetryCooldownMs
        if (failedAt != null && monotonicMillis() - failedAt < cooldown) return
    }
    val loading = previous.copy(isLoading = true, errorMessage = null, failedAtMillis = null)
    target.value = loading
    try {
        val items = fetch()
        currentCoroutineContext().ensureActive()
        target.value = SpotifyLibrarySectionState(items = items)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        currentCoroutineContext().ensureActive()
        target.value =
            previous.copy(
                errorMessage = error.message ?: error.javaClass.simpleName,
                failedAtMillis = monotonicMillis(),
            )
    } finally {
        // An account change may already have reset the state or started its replacement request.
        if (target.value === loading) target.value = previous
    }
}
