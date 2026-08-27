/*
 * ArchiveTune (2026)
 * © ArchiveTuneFork contributors — github.com/vossgraves/ArchiveTune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package app.atf.media.viewmodels

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import app.atf.media.aicontentfilter.FilterAiContentUseCase
import app.atf.media.aicontentfilter.LoadAiContentFilterPolicyUseCase
import app.atf.media.constants.HideExplicitKey
import app.atf.media.constants.HideVideoKey
import app.atf.media.db.MusicDatabase
import app.atf.media.extensions.filterBlockedArtists
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.AlbumReleaseType
import moe.rukamori.archivetune.innertube.models.filterExplicit
import moe.rukamori.archivetune.innertube.models.filterVideo
import app.atf.media.utils.dataStore
import app.atf.media.utils.get
import app.atf.media.utils.reportException
import javax.inject.Inject

@Immutable
data class NewReleaseContent(
    val albums: List<AlbumItem>,
    val singles: List<AlbumItem>,
    val eps: List<AlbumItem>,
) {
    val totalReleases: Int
        get() = albums.size + singles.size + eps.size

    val isEmpty: Boolean
        get() = totalReleases == 0
}

sealed interface NewReleaseUiState {
    data object Loading : NewReleaseUiState

    data class Success(
        val content: NewReleaseContent,
    ) : NewReleaseUiState

    data object Empty : NewReleaseUiState

    data object Error : NewReleaseUiState
}

@HiltViewModel
class NewReleaseViewModel
    @Inject
    constructor(
        @ApplicationContext val context: Context,
        private val database: MusicDatabase,
        private val loadAiContentFilterPolicy: LoadAiContentFilterPolicyUseCase,
        private val filterAiContent: FilterAiContentUseCase,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<NewReleaseUiState>(NewReleaseUiState.Loading)
        val uiState = _uiState.asStateFlow()

        init {
            load()
        }

        fun retry() {
            load()
        }

        private fun load() {
            viewModelScope.launch(Dispatchers.IO) {
                _uiState.value = NewReleaseUiState.Loading
                try {
                    val albums = YouTube.newReleaseAlbums().getOrThrow()
                    val blockedArtistIds = database.getBlockedArtistIds().toSet()
                    val aiContentFilterPolicy = loadAiContentFilterPolicy()
                    val artistRanks: MutableMap<String, Int> = mutableMapOf()
                    val favouriteArtistRanks: MutableMap<String, Int> = mutableMapOf()
                    database.allArtistsByPlayTime().first().let { list ->
                        var favIndex = 0
                        for ((artistsIndex, artist) in list.withIndex()) {
                            artistRanks[artist.id] = artistsIndex
                            if (artist.artist.bookmarkedAt != null) {
                                favouriteArtistRanks[artist.id] = favIndex
                                favIndex++
                            }
                        }
                    }
                    val filtered =
                        filterAiContent(
                            albums
                                .sortedBy { album ->
                                    val artistIds = album.artists.orEmpty().mapNotNull { it.id }
                                    val firstArtistKey =
                                        artistIds.firstNotNullOfOrNull { artistId ->
                                            favouriteArtistRanks[artistId] ?: artistRanks[artistId]
                                        } ?: Int.MAX_VALUE
                                    firstArtistKey
                                }.filterExplicit(context.dataStore.get(HideExplicitKey, false))
                                .filterVideo(context.dataStore.get(HideVideoKey, false))
                                .filterBlockedArtists(blockedArtistIds),
                            aiContentFilterPolicy,
                        ).distinctBy { it.id }
                    val content = filtered.toNewReleaseContent()
                    _uiState.value =
                        if (content.isEmpty) {
                            NewReleaseUiState.Empty
                        } else {
                            NewReleaseUiState.Success(content)
                        }
                } catch (t: Throwable) {
                    reportException(t)
                    _uiState.value = NewReleaseUiState.Error
                }
            }
        }

        private fun List<AlbumItem>.toNewReleaseContent(): NewReleaseContent =
            NewReleaseContent(
                albums = filter { it.releaseType == AlbumReleaseType.ALBUM },
                singles = filter { it.releaseType == AlbumReleaseType.SINGLE },
                eps = filter { it.releaseType == AlbumReleaseType.EP },
            )
    }
