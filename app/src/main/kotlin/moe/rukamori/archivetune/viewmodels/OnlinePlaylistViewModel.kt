/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 * Portions © vossgraves — github.com/vossgraves
 */

package moe.rukamori.archivetune.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import moe.rukamori.archivetune.canvas.models.CanvasArtwork
import moe.rukamori.archivetune.constants.HideVideoKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.extensions.filterBlockedArtists
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.distinctByPlaylistEntry
import moe.rukamori.archivetune.innertube.models.filterVideo
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import moe.rukamori.archivetune.utils.reportException
import javax.inject.Inject

@HiltViewModel
class OnlinePlaylistViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        savedStateHandle: SavedStateHandle,
        private val database: MusicDatabase,
    ) : ViewModel() {
        // Community-playlist search results occasionally surface items whose `id` is null or
        // blank. Fall back to an empty string and let #load() surface a typed error to the UI
        // instead of crashing on tap.
        private val playlistId: String = savedStateHandle.get<String>("playlistId").orEmpty()

        private val _playlist = MutableStateFlow<PlaylistItem?>(null)
        val playlist = _playlist.asStateFlow()

        private val _playlistSongs = MutableStateFlow<List<SongItem>>(emptyList())
        val playlistSongs = _playlistSongs.asStateFlow()

        private val _isLoading = MutableStateFlow(false)
        val isLoading = _isLoading.asStateFlow()

        private val _isRefreshing = MutableStateFlow(false)
        val isRefreshing = _isRefreshing.asStateFlow()

        private val _error = MutableStateFlow<String?>(null)
        val error = _error.asStateFlow()

        private val _isLoadingMore = MutableStateFlow(false)
        val isLoadingMore = _isLoadingMore.asStateFlow()

        val dbPlaylist =
            database
                .playlistByBrowseId(playlistId)
                .stateIn(viewModelScope, SharingStarted.Lazily, null)

        private val _viewCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
        val viewCounts = _viewCounts.asStateFlow()

        private val _canvasArtwork = MutableStateFlow<CanvasArtwork?>(null)
        val canvasArtwork: StateFlow<CanvasArtwork?> = _canvasArtwork.asStateFlow()

        private val viewCountsMutex = Mutex()
        private val viewCountsInFlight = mutableSetOf<String>()
        private val viewCountsSemaphore = Semaphore(permits = 4)

        var continuation: String? = null
            private set

        init {
            load(initial = true)
            fetchPlaylistCanvas()
        }

        fun refresh() {
            load(initial = false)
        }

        fun loadMoreSongs() {
            val nextContinuation = continuation ?: return
            if (!_isLoadingMore.compareAndSet(expect = false, update = true)) return

            viewModelScope.launch(Dispatchers.IO) {
                try {
                    YouTube
                        .playlistContinuation(nextContinuation, playlistId)
                        .onSuccess { playlistContinuationPage ->
                            val visibleSongs =
                                playlistContinuationPage.songs
                                    .filterVideo(context.dataStore.get(HideVideoKey, false))
                                    .filterBlockedArtists(database.getBlockedArtistIds().toSet())
                            _playlistSongs.update { currentSongs ->
                                (currentSongs + visibleSongs).distinctByPlaylistEntry()
                            }
                            continuation = playlistContinuationPage.continuation
                            prefetchViewCounts(visibleSongs.map { song -> song.id })
                        }.onFailure { throwable ->
                            reportException(throwable)
                        }
                } finally {
                    _isLoadingMore.value = false
                }
            }
        }

        fun retry() {
            load(initial = true)
        }

        /**
         * Waits for the first page to land, then resolves the canvas its header loops. Loading the
         * playlist is what names the song the lookup is keyed on, so this is deliberately a second
         * pass rather than part of [load] — a canvas is decoration and must never hold up the list.
         */
        private fun fetchPlaylistCanvas() {
            viewModelScope.launch(Dispatchers.IO) {
                // The wait is bounded: a playlist whose songs are all filtered out never becomes
                // non-empty, and there is no canvas to resolve for it either way.
                val firstSong =
                    withTimeoutOrNull(PLAYLIST_CANVAS_LOOKUP_TIMEOUT_MS) {
                        _playlistSongs.first { it.isNotEmpty() }
                    }?.firstOrNull() ?: return@launch
                _canvasArtwork.value =
                    fetchPlaylistCanvasArtwork(
                        context = context,
                        firstSongId = firstSong.id,
                        firstSongTitle = firstSong.title,
                        firstSongArtist = firstSong.artists.firstOrNull()?.name,
                    )
            }
        }

        private fun load(initial: Boolean) {
            // Bail out early with a typed error if there's no playlist ID to load — happens
            // when a community-playlist search result had a null/blank id, so the user gets an
            // error state with a retry button rather than a crash.
            if (playlistId.isBlank()) {
                _error.value = "This playlist could not be opened (missing id)."
                _isLoading.value = false
                _isRefreshing.value = false
                return
            }

            if (initial) {
                if (_isLoading.value) return
                _isLoading.value = true
            } else {
                if (_isRefreshing.value || _isLoading.value || _isLoadingMore.value) return
                _isRefreshing.value = true
            }

            viewModelScope.launch(Dispatchers.IO) {
                _error.value = null

                YouTube
                    .playlist(playlistId)
                    .onSuccess { playlistPage ->
                        val visibleSongs =
                            playlistPage.songs
                                .filterVideo(context.dataStore.get(HideVideoKey, false))
                                .filterBlockedArtists(database.getBlockedArtistIds().toSet())
                                .distinctByPlaylistEntry()
                        _playlist.value = playlistPage.playlist
                        _playlistSongs.value = visibleSongs
                        continuation = playlistPage.songsContinuation ?: playlistPage.continuation
                        prefetchViewCounts(visibleSongs.map { song -> song.id })
                    }.onFailure { throwable ->
                        _error.value = throwable.message ?: "Failed to load playlist"
                        reportException(throwable)
                    }

                if (initial) {
                    _isLoading.value = false
                } else {
                    _isRefreshing.value = false
                }
            }
        }

        private fun prefetchViewCounts(videoIds: List<String>) {
            val uniqueIds = videoIds.distinct().filter { it.isNotBlank() }
            if (uniqueIds.isEmpty()) return

            viewModelScope.launch(Dispatchers.IO) {
                coroutineScope {
                    uniqueIds
                        .map { videoId ->
                            async {
                                val shouldFetch =
                                    viewCountsMutex.withLock {
                                        if (_viewCounts.value.containsKey(videoId) || viewCountsInFlight.contains(videoId)) {
                                            false
                                        } else {
                                            viewCountsInFlight.add(videoId)
                                            true
                                        }
                                    }

                                if (!shouldFetch) return@async

                                try {
                                    viewCountsSemaphore.withPermit {
                                        val count = YouTube.getMediaInfo(videoId).getOrNull()?.viewCount
                                        if (count != null && count >= 0) {
                                            _viewCounts.update { current -> current + (videoId to count) }
                                        }
                                    }
                                } finally {
                                    viewCountsMutex.withLock { viewCountsInFlight.remove(videoId) }
                                }
                            }
                        }.awaitAll()
                }
            }
        }
    }
