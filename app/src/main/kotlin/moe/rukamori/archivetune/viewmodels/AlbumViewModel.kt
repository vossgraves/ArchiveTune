/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.canvas.AppleMusicProvider
import moe.rukamori.archivetune.canvas.models.CanvasArtwork
import moe.rukamori.archivetune.constants.AlbumCanvasEnabledKey
import moe.rukamori.archivetune.constants.HideVideoKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.extensions.filterBlockedArtists
import moe.rukamori.archivetune.extensions.filterVideo
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import moe.rukamori.archivetune.utils.isLowDataModeActive
import moe.rukamori.archivetune.utils.reportException
import javax.inject.Inject

sealed interface AlbumUiState {
    data object Loading : AlbumUiState

    data object Content : AlbumUiState

    data object Empty : AlbumUiState

    data class Error(
        val isNotFound: Boolean = false,
    ) : AlbumUiState
}

private sealed interface FetchState {
    data object Pending : FetchState

    data object Success : FetchState

    data class Failed(
        val isNotFound: Boolean = false,
    ) : FetchState
}

@HiltViewModel
class AlbumViewModel
    @Inject
    constructor(
        @ApplicationContext context: Context,
        private val database: MusicDatabase,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        val albumId = savedStateHandle.get<String>("albumId")!!
        val playlistId = MutableStateFlow("")
        val albumWithSongs =
            combine(
                database.albumWithSongs(albumId),
                context.dataStore.data
                    .map { preferences -> preferences[HideVideoKey] ?: false }
                    .distinctUntilChanged(),
            ) { album, hideVideo ->
                album?.copy(
                    songs =
                        if (album.artists.any { it.blockedAt != null }) {
                            emptyList()
                        } else {
                            album.songs
                                .filterBlockedArtists()
                                .filterVideo(hideVideo)
                        },
                )
            }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
        var otherVersions = MutableStateFlow<List<AlbumItem>>(emptyList())

        // Looping animated canvas (Apple Music-style animated cover art) for
        // the album thumbnail. Fetched once per albumId via
        // `AppleMusicProvider.getByAlbumId` (Apple Music's animated-art API)
        // with a fallback to `getByAlbumArtist` when the
        // album id isn't an Apple Music id (e.g. a YouTube `MPRE…` id).
        // Gated on the same `ArchiveTuneCanvasKey` preference the song
        // player uses, plus LowDataMode (skip the network fetch on metered
        // connections). Null until the fetch completes (or fails silently —
        // failures leave the value at null so the album hero renders the
        // static thumbnail with no canvas overlay).
        private val _canvasArtwork = MutableStateFlow<CanvasArtwork?>(null)
        val canvasArtwork: StateFlow<CanvasArtwork?> = _canvasArtwork.asStateFlow()

        private val _fetchState = MutableStateFlow<FetchState>(FetchState.Pending)

        val uiState: StateFlow<AlbumUiState> =
            combine(albumWithSongs, _fetchState) { data, fetch ->
                when {
                    data != null && data.songs.isNotEmpty() -> AlbumUiState.Content
                    fetch is FetchState.Pending -> AlbumUiState.Loading
                    fetch is FetchState.Failed && data == null -> AlbumUiState.Error(fetch.isNotFound)
                    fetch is FetchState.Success && data != null && data.songs.isEmpty() -> AlbumUiState.Empty
                    fetch is FetchState.Failed && data != null && data.songs.isNotEmpty() -> AlbumUiState.Content
                    else -> AlbumUiState.Loading
                }
            }.stateIn(viewModelScope, SharingStarted.Eagerly, AlbumUiState.Loading)

        init {
            retry()
            fetchAlbumCanvas(context)
        }

        fun retry() {
            viewModelScope.launch {
                _fetchState.value = FetchState.Pending
                val album = database.album(albumId).first()
                YouTube
                    .album(albumId)
                    .onSuccess {
                        playlistId.value = it.album.playlistId
                        val blockedArtistIds = database.getBlockedArtistIds().toSet()
                        otherVersions.value =
                            it.otherVersions.filter { version ->
                                version.artists.orEmpty().none { artist -> artist.id in blockedArtistIds }
                            }
                        database.withTransaction {
                            if (album == null) {
                                insert(it)
                            } else {
                                update(album.album, it, album.artists)
                            }
                        }
                        _fetchState.value = FetchState.Success
                    }.onFailure {
                        reportException(it)
                        val isNotFound = it.message?.contains("NOT_FOUND") == true
                        if (isNotFound) {
                            database.query {
                                album?.album?.let(::delete)
                            }
                        }
                        _fetchState.value = FetchState.Failed(isNotFound = isNotFound)
                    }
            }
        }

        /**
         * Fetches the album's looping animated canvas (Apple Music animated
         * cover art) and exposes it via [canvasArtwork]. Best-effort: any
         * failure (network, no canvas for this album, user has the feature
         * disabled, low-data mode) leaves the value at null, which causes
         * the album hero to render only the static thumbnail — no canvas
         * overlay.
         *
         * We try `AppleMusicProvider.getByAlbumId` first (direct id lookup,
         * fast for Apple Music ids), then fall back to
         * `getByAlbumArtist` (title + artist name lookup) for YouTube
         * `MPRE…` ids that aren't Apple Music ids. The fetch runs on
         * Dispatchers.IO via `viewModelScope.launch` so it doesn't block
         * the UI thread.
         */
        private fun fetchAlbumCanvas(context: Context) {
            viewModelScope.launch {
                // Gated on its own preference (Appearance → "Enable canvas in albums page")
                // rather than the player-level `ArchiveTuneCanvasKey`: the album loop starts
                // as soon as the page opens, whether or not anything is playing, so it is a
                // separate cost and a separate choice. Default on, matching Apple Music.
                if (!context.dataStore.get(AlbumCanvasEnabledKey, true)) return@launch

                // Still respect LowDataMode (skip the network fetch on metered
                // connections) since the canvas is a short video loop with non-trivial
                // bandwidth.
                if (context.isLowDataModeActive()) return@launch

                // Wait for the album to load in the DB (it might not be
                // there yet on first open — `albumWithSongs` starts at null
                // and is populated by `retry()` running in parallel).
                val loaded = albumWithSongs.first { it != null } ?: return@launch
                val album = loaded.album
                val firstArtist = loaded.artists.firstOrNull()?.name
                val firstSongTitle = loaded.songs.firstOrNull()?.song?.title

                _canvasArtwork.value = resolveAlbumCanvas(album.id, album.title, firstArtist, firstSongTitle)
            }
        }

        /**
         * Walks the Apple Music motion-artwork lookups from most to least specific and
         * returns the first hit, or null when the album simply has no motion artwork.
         *
         * The ladder exists because the id we hold is almost never an Apple Music id — a
         * YouTube album is an `MPREb…` browse id, so [AppleMusicProvider.getByAlbumId]
         * only succeeds for the rare album that came from an Apple-shaped id, and
         * everything else has to be matched by name. Each extra rung recovers a class of
         * album the previous one misses:
         *
         *  - **exact title + artist** — the normal path.
         *  - **title stripped of edition suffixes** — Apple's catalogue carries "1989"
         *    where YouTube has "1989 (Taylor's Version) [Deluxe]", and an exact-name
         *    search for the decorated form finds nothing.
         *  - **a track from the album** — singles and EPs are frequently catalogued under
         *    the track's own name, and a song lookup also picks up motion artwork attached
         *    to the song rather than the album.
         */
        private suspend fun resolveAlbumCanvas(
            albumId: String,
            albumTitle: String,
            artist: String?,
            firstSongTitle: String?,
        ): CanvasArtwork? {
            runCatching { AppleMusicProvider.getByAlbumId(albumId) }
                .getOrNull()
                ?.let { return it }

            if (artist.isNullOrBlank()) return null

            val titleCandidates =
                linkedSetOf(albumTitle, stripAlbumEditionSuffixes(albumTitle))
                    .filter { it.isNotBlank() }
            for (title in titleCandidates) {
                runCatching { AppleMusicProvider.getByAlbumArtist(album = title, artist = artist) }
                    .getOrNull()
                    ?.let { return it }
            }

            if (!firstSongTitle.isNullOrBlank()) {
                runCatching {
                    AppleMusicProvider.getBySongArtist(
                        song = firstSongTitle,
                        artist = artist,
                        album = albumTitle,
                    )
                }.getOrNull()?.let { return it }
            }
            return null
        }

        /**
         * Drops the release-edition decoration YouTube album titles carry and Apple's
         * catalogue titles usually do not: parenthesised/bracketed qualifiers
         * ("(Deluxe Edition)", "[Remastered 2011]") and a trailing " - EP" / " - Single".
         */
        private fun stripAlbumEditionSuffixes(title: String): String =
            title
                .replace(Regex("\\s*[\\(\\[][^)\\]]*[\\)\\]]\\s*$"), "")
                .replace(Regex("\\s*-\\s*(EP|Single|Deluxe|Remaster(ed)?)\\s*$", RegexOption.IGNORE_CASE), "")
                .trim()
    }
