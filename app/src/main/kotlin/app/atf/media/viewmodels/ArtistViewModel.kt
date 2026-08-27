/*
 * ArchiveTune (2026)
 * © ArchiveTuneFork contributors — github.com/vossgraves/ArchiveTune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package app.atf.media.viewmodels

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.atf.media.R
import app.atf.media.artist.ArtistBlockRequest
import app.atf.media.artist.ObserveArtistBlockedUseCase
import app.atf.media.artist.SetArtistBlockedUseCase
import app.atf.media.constants.HideExplicitKey
import app.atf.media.constants.HideVideoKey
import app.atf.media.db.MusicDatabase
import app.atf.media.extensions.filterBlockedArtists
import app.atf.media.extensions.filterExplicit
import app.atf.media.extensions.filterExplicitAlbums
import app.atf.media.extensions.filterVideo
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.filterExplicit
import moe.rukamori.archivetune.innertube.models.filterVideo
import moe.rukamori.archivetune.innertube.pages.ArtistPage
import app.atf.media.utils.dataStore
import app.atf.media.utils.get
import app.atf.media.utils.reportException
import javax.inject.Inject

sealed interface ArtistBlockState {
    data object Loading : ArtistBlockState

    @Immutable
    data class Success(
        val isBlocked: Boolean,
    ) : ArtistBlockState

    data object Empty : ArtistBlockState

    @Immutable
    data class Error(
        @StringRes val messageRes: Int,
    ) : ArtistBlockState
}

sealed interface ArtistAction {
    data object Share : ArtistAction

    data object CopyLink : ArtistAction

    data object ToggleBlock : ArtistAction
}

sealed interface ArtistEvent {
    @Immutable
    data class Share(
        val link: String,
    ) : ArtistEvent

    @Immutable
    data class CopyLink(
        val link: String,
    ) : ArtistEvent

    @Immutable
    data class ShowMessage(
        @StringRes val messageRes: Int,
    ) : ArtistEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ArtistViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val database: MusicDatabase,
        observeArtistBlocked: ObserveArtistBlockedUseCase,
        private val setArtistBlocked: SetArtistBlockedUseCase,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        val artistId = savedStateHandle.get<String>("artistId")!!
        var artistPage by mutableStateOf<ArtistPage?>(null)
        private val eventChannel = Channel<ArtistEvent>(capacity = Channel.BUFFERED)
        val events = eventChannel.receiveAsFlow()
        private var blockJob: Job? = null

        val libraryArtist =
            database
                .artist(artistId)
                .stateIn(viewModelScope, SharingStarted.Lazily, null)
        val blockState =
            observeArtistBlocked(artistId)
                .map { blocked ->
                    if (blocked == null) {
                        ArtistBlockState.Empty
                    } else {
                        ArtistBlockState.Success(isBlocked = blocked)
                    }
                }.catch {
                    emit(ArtistBlockState.Error(R.string.error_unknown))
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArtistBlockState.Loading)
        val librarySongs =
            context.dataStore.data
                .map { preferences ->
                    (preferences[HideExplicitKey] ?: false) to (preferences[HideVideoKey] ?: false)
                }
                .distinctUntilChanged()
                .flatMapLatest { (hideExplicit, hideVideo) ->
                    database.artistSongsByCreateDateAsc(artistId).map {
                        it.filterExplicit(hideExplicit).filterVideo(hideVideo)
                    }
                }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        val libraryAlbums =
            context.dataStore.data
                .map { it[HideExplicitKey] ?: false }
                .distinctUntilChanged()
                .flatMapLatest { hideExplicit ->
                    database.artistAlbumsPreview(artistId).map { it.filterExplicitAlbums(hideExplicit) }
                }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        init {
            viewModelScope.launch {
                context.dataStore.data
                    .map { preferences ->
                        (preferences[HideExplicitKey] ?: false) to (preferences[HideVideoKey] ?: false)
                    }
                    .distinctUntilChanged()
                    .collect {
                        fetchArtistsFromYTM()
                    }
            }
        }

        fun fetchArtistsFromYTM() {
            viewModelScope.launch {
                val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                val hideVideo = context.dataStore.get(HideVideoKey, false)
                val blockedArtistIds = database.getBlockedArtistIds().toSet()
                YouTube
                    .artist(artistId)
                    .onSuccess { page ->
                        val filteredSections =
                            page.sections
                                .map { section ->
                                    section.copy(
                                        items =
                                            section.items
                                                .filterExplicit(hideExplicit)
                                                .filterVideo(hideVideo)
                                                .filterBlockedArtists(blockedArtistIds),
                                    )
                                }

                        artistPage = page.copy(sections = filteredSections)

                        withContext(Dispatchers.IO) {
                            database.artist(artistId).firstOrNull()?.artist?.let { artistEntity ->
                                database.update(artistEntity, page)
                            }
                        }
                    }.onFailure {
                        reportException(it)
                    }
            }
        }

        fun onAction(action: ArtistAction) {
            when (action) {
                ArtistAction.Share -> eventChannel.trySend(ArtistEvent.Share(artistShareLink()))
                ArtistAction.CopyLink -> eventChannel.trySend(ArtistEvent.CopyLink(artistShareLink()))
                ArtistAction.ToggleBlock -> toggleBlocked()
            }
        }

        private fun toggleBlocked() {
            if (blockJob?.isActive == true) return

            val pageArtist = artistPage?.artist
            val localArtist = libraryArtist.value?.artist
            val artistName = pageArtist?.title ?: localArtist?.name ?: return
            val currentlyBlocked = (blockState.value as? ArtistBlockState.Success)?.isBlocked == true

            blockJob =
                viewModelScope.launch {
                    try {
                        setArtistBlocked(
                            ArtistBlockRequest(
                                id = artistId,
                                name = artistName,
                                channelId = pageArtist?.channelId ?: localArtist?.channelId,
                                thumbnailUrl = pageArtist?.thumbnail ?: localArtist?.thumbnailUrl,
                                blocked = !currentlyBlocked,
                            ),
                        )
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (throwable: Throwable) {
                        reportException(throwable)
                        eventChannel.send(ArtistEvent.ShowMessage(R.string.error_unknown))
                    }
                }
        }

        private fun artistShareLink(): String = artistPage?.artist?.shareLink ?: "https://music.youtube.com/channel/$artistId"
    }
