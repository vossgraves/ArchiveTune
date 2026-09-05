/*
 * YumaPlayer (2026) | Modified work by MuwMx
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import moe.rukamori.archivetune.utils.reportException
import moe.rukamori.archivetune.spotify.models.SpotifyAlbum
import moe.rukamori.archivetune.spotify.models.SpotifyArtist
import moe.rukamori.archivetune.spotify.models.SpotifyHomeFeedItem
import moe.rukamori.archivetune.spotify.models.SpotifyHomeFeedSection
import moe.rukamori.archivetune.spotify.models.SpotifyImage
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylist
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylistOwner
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylistTracksRef
import javax.inject.Inject

sealed interface SpotifyRecentItem {
    val id: String
    val name: String
    val imageUrl: String?

    data class Playlist(
        override val id: String,
        override val name: String,
        override val imageUrl: String?
    ) : SpotifyRecentItem

    data class Album(
        override val id: String,
        override val name: String,
        override val imageUrl: String?,
        val artists: List<SpotifyArtist>
    ) : SpotifyRecentItem
}

sealed interface SpotifyHomeScreenState {
    data object Loading : SpotifyHomeScreenState
    data class Success(
        val sections: List<SpotifyHomeSection>,
        val recentItems: List<SpotifyRecentItem> = emptyList(),
        val frequentArtists: List<SpotifyArtist> = emptyList()
    ) : SpotifyHomeScreenState
    data object Empty : SpotifyHomeScreenState
    data class Error(val messageResId: Int, val notAuthenticated: Boolean = false) : SpotifyHomeScreenState
}

sealed interface SpotifyHomeNavigationEvent {
    data class OpenAlbum(val browseId: String) : SpotifyHomeNavigationEvent
    data class OpenArtist(val id: String) : SpotifyHomeNavigationEvent
    data class PlayTracks(val queue: SpotifyTracksQueue) : SpotifyHomeNavigationEvent
    data class ShowMessage(val messageResId: Int) : SpotifyHomeNavigationEvent
}

sealed interface SpotifyHomeAction {
    data object Refresh : SpotifyHomeAction
    data class TrackClick(
        val track: SpotifyTrack,
        val tracks: List<SpotifyTrack>,
        val title: String,
    ) : SpotifyHomeAction
    // Identity plus the words the catalogue search needs, rather than a whole Spotify model. The
    // callers hold four different shapes for the same album (feed item, recent item, search
    // result), and every one of them was rebuilding a SpotifyAlbum just to be taken apart again.
    data class AlbumClick(val id: String, val name: String, val artist: String?) : SpotifyHomeAction
    data class ArtistClick(val id: String, val name: String) : SpotifyHomeAction
}

@HiltViewModel
class SpotifyHomeViewModel @Inject constructor(
    private val repository: SpotifyLibraryRepository,
) : ViewModel() {

    private val _screenState = MutableStateFlow<SpotifyHomeScreenState>(SpotifyHomeScreenState.Loading)
    val screenState: StateFlow<SpotifyHomeScreenState> = _screenState.asStateFlow()

    private val _navigationEvents = MutableSharedFlow<SpotifyHomeNavigationEvent>()
    val navigationEvents: SharedFlow<SpotifyHomeNavigationEvent> = _navigationEvents.asSharedFlow()

    private val _resolvingItemKey = MutableStateFlow<String?>(null)
    val resolvingItemKey = _resolvingItemKey.asStateFlow()
    private var selectionJob: Job? = null
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            repository.accountChanges.collect { load() }
        }
    }

    fun onAction(action: SpotifyHomeAction) {
        when (action) {
            SpotifyHomeAction.Refresh -> load()
            is SpotifyHomeAction.TrackClick -> resolveSelection(
                key = "track:${action.track.id}",
                unavailableMessageResId = R.string.spotify_track_unavailable,
            ) {
                SpotifyPlaybackResolver.resolveToMetadata(action.track)?.let { metadata ->
                    SpotifyHomeNavigationEvent.PlayTracks(
                        SpotifyTracksQueue(
                            title = action.title,
                            initialTracks = action.tracks,
                            startIndex = action.tracks.indexOf(action.track).coerceAtLeast(0),
                            preloadItem = metadata,
                        ),
                    )
                }
            }
            is SpotifyHomeAction.AlbumClick -> resolveSelection("album:${action.id}") {
                val query = listOfNotNull(action.name, action.artist)
                    .filter(String::isNotBlank)
                    .joinToString(" ")
                searchCatalogItem<AlbumItem>(query, YouTube.SearchFilter.FILTER_ALBUM)
                    ?.let { SpotifyHomeNavigationEvent.OpenAlbum(it.browseId) }
            }
            is SpotifyHomeAction.ArtistClick -> resolveSelection("artist:${action.id}") {
                searchCatalogItem<ArtistItem>(action.name, YouTube.SearchFilter.FILTER_ARTIST)
                    ?.let { SpotifyHomeNavigationEvent.OpenArtist(it.id) }
            }
        }
    }

    fun cancelSelection() {
        selectionJob?.cancel()
        selectionJob = null
        _resolvingItemKey.value = null
    }

    private fun resolveSelection(
        key: String,
        unavailableMessageResId: Int = R.string.no_results_found,
        resolve: suspend () -> SpotifyHomeNavigationEvent?,
    ) {
        if (_resolvingItemKey.value == key && selectionJob?.isActive == true) return
        cancelSelection()
        _resolvingItemKey.value = key
        selectionJob = viewModelScope.launch {
            try {
                val event = withTimeoutOrNull(20_000L) {
                    withContext(Dispatchers.IO) { resolve() }
                }
                currentCoroutineContext().ensureActive()
                _navigationEvents.emit(event ?: SpotifyHomeNavigationEvent.ShowMessage(unavailableMessageResId))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                reportException(error)
                _navigationEvents.emit(SpotifyHomeNavigationEvent.ShowMessage(unavailableMessageResId))
            } finally {
                if (currentCoroutineContext().isActive) _resolvingItemKey.value = null
            }
        }
    }

    private suspend inline fun <reified T : YTItem> searchCatalogItem(
        query: String,
        filter: YouTube.SearchFilter,
    ): T? {
        val anonymous = YouTube.search(query, filter, useAccountContext = false)
        currentCoroutineContext().ensureActive()
        anonymous.getOrNull()?.items?.filterIsInstance<T>()?.firstOrNull()?.let { return it }
        val fallback = YouTube.search(query, filter)
        currentCoroutineContext().ensureActive()
        return fallback.getOrThrow().items.filterIsInstance<T>().firstOrNull()
    }

    private fun load() {
        loadJob?.cancel()
        cancelSelection()
        _screenState.value = SpotifyHomeScreenState.Loading
        loadJob = viewModelScope.launch(Dispatchers.IO) {

            try {
                val session = repository.restoreSession()
                if (!session.isAuthenticated) {
                    _screenState.update { SpotifyHomeScreenState.Error(R.string.spotify_not_connected, notAuthenticated = true) }
                    return@launch
                }

                val sections = mutableListOf<SpotifyHomeSection>()
                var frequentArtists = emptyList<SpotifyArtist>()

                val topTracksDeferred = async { Spotify.topTracks(limit = 20) }
                val newReleasesDeferred = async { Spotify.newReleases(limit = 20) }
                val homeDeferred = async { Spotify.home(sectionItemsLimit = 10) }
                val topArtistsDeferred = async { Spotify.topArtists(limit = 20) }

                val topTracksResult = topTracksDeferred.await()
                val newReleasesResult = newReleasesDeferred.await()
                val homeResult = homeDeferred.await()
                val topArtistsResult = topArtistsDeferred.await()
                currentCoroutineContext().ensureActive()

                topTracksResult.onSuccess { topTracks ->
                    if (topTracks.items.isNotEmpty()) {
                        sections.add(
                            SpotifyHomeSection.Tracks(
                                title = "spotify_top_tracks",
                                tracks = topTracks.items,
                            )
                        )
                    }
                }

                newReleasesResult.onSuccess { newReleases ->
                    val albums = newReleases.albums?.items.orEmpty()
                    if (albums.isNotEmpty()) {
                        sections.add(
                            SpotifyHomeSection.Cards(
                                title = "spotify_new_releases",
                                items = albums.map { album ->
                                    SpotifyHomeFeedItem.Album(
                                        uri = album.uri.orEmpty(),
                                        id = album.id,
                                        name = album.name,
                                        albumType = album.albumType,
                                        artists = album.artists,
                                        imageUrl = album.images.maxByOrNull { it.width ?: 0 }?.url,
                                    )
                                },
                            )
                        )
                    }
                }

                topArtistsResult.onSuccess { topArtists ->
                    frequentArtists = topArtists.items
                }

                var recentItems = emptyList<SpotifyRecentItem>()

                homeResult.onSuccess { feed ->
                    feed.sections.forEach { raw ->
                        // Recognised by the section URI alone. It used to also match the title
                        // against "Jump back in", "Recently" and five Russian phrases — which meant
                        // the shelf was only ever recognised in two of the forty-odd languages the
                        // app ships, and Spotify returns titles in the account's language. The URI
                        // is the same string whatever the user reads.
                        if (raw.sectionUri.contains("recent", ignoreCase = true)) {
                            recentItems = raw.items.mapNotNull { item ->
                                when (item) {
                                    is SpotifyHomeFeedItem.Album -> SpotifyRecentItem.Album(
                                        id = item.id,
                                        name = item.name,
                                        imageUrl = item.imageUrl,
                                        artists = item.artists.map { SpotifyArtist(id = it.id.orEmpty(), name = it.name, uri = it.uri) }
                                    )
                                    is SpotifyHomeFeedItem.Playlist -> SpotifyRecentItem.Playlist(
                                        id = item.id,
                                        name = item.name,
                                        imageUrl = item.imageUrl
                                    )
                                    else -> null
                                }
                            }
                        } else {
                            val converted = convertHomeSection(raw)
                            if (converted != null) {
                                sections.add(converted)
                            }
                        }
                    }
                }

                currentCoroutineContext().ensureActive()
                if (sections.isEmpty() && recentItems.isEmpty() && frequentArtists.isEmpty()) {
                    _screenState.update { SpotifyHomeScreenState.Empty }
                } else {
                    _screenState.update {
                        SpotifyHomeScreenState.Success(
                            sections = sections,
                            recentItems = recentItems,
                            frequentArtists = frequentArtists,
                        )
                    }
                }

            } catch (error: CancellationException) {
                throw error
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                if (e is Spotify.SpotifyException && e.statusCode == 401) {
                    _screenState.update { SpotifyHomeScreenState.Error(R.string.spotify_not_connected, notAuthenticated = true) }
                } else {
                    _screenState.update { SpotifyHomeScreenState.Error(R.string.error_unknown) }
                }
            }
        }
    }

    /**
     * A feed shelf, kept whole. Every item stays, in the order Spotify sent it, carrying its own
     * kind — so a shelf mixing albums with playlists renders both and each tile opens its own
     * thing. The previous version kept only the majority kind and dropped the rest.
     */
    private fun convertHomeSection(feedSection: SpotifyHomeFeedSection): SpotifyHomeSection? {
        val title = feedSection.title ?: return null
        if (feedSection.items.isEmpty()) return null
        return SpotifyHomeSection.Cards(title = title, items = feedSection.items)
    }
}
