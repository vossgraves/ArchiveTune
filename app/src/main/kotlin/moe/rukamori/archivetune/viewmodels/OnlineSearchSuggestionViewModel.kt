/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import android.content.Context
import kotlinx.coroutines.CancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.aicontentfilter.FilterAiContentUseCase
import moe.rukamori.archivetune.aicontentfilter.LoadAiContentFilterPolicyUseCase
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.constants.HideVideoKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.SearchHistory
import kotlinx.coroutines.flow.combine
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.innertube.models.filterExplicit
import moe.rukamori.archivetune.innertube.models.filterVideo
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import moe.rukamori.archivetune.constants.SearchProvider
import moe.rukamori.archivetune.spotify.SpotifyLibraryRepository
import moe.rukamori.archivetune.spotify.SpotifySearchItem
import moe.rukamori.archivetune.spotify.toSearchItems
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OnlineSearchSuggestionViewModel
    @Inject
    constructor(
        @ApplicationContext val context: Context,
        private val database: MusicDatabase,
        private val loadAiContentFilterPolicy: LoadAiContentFilterPolicyUseCase,
        private val filterAiContent: FilterAiContentUseCase,
        private val spotifyRepository: SpotifyLibraryRepository,
    ) : ViewModel() {
        private val query = MutableStateFlow("")
        private val provider = MutableStateFlow(SearchProvider.YOUTUBE)
        private val _viewState = MutableStateFlow(SearchSuggestionViewState())
        val viewState = _viewState.asStateFlow()

        init {
            viewModelScope.launch {
                query
                    .combine(provider) { query, provider -> query to provider }
                    .flatMapLatest { (query, provider) ->
                        if (query.isEmpty()) {
                            database.searchHistory().map { history ->
                                SearchSuggestionViewState(history = history)
                            }
                        } else if (provider == SearchProvider.SPOTIFY) {
                            val spotifyItems =
                                try {
                                    spotifyRepository
                                        .search(query = query, limit = 8)
                                        .toSearchItems()
                                        .take(8)
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (_: Throwable) {
                                    emptyList()
                                }
                            database.searchHistory(query).map { history ->
                                SearchSuggestionViewState(
                                    history = history.take(3),
                                    spotifyItems = spotifyItems,
                                )
                            }
                        } else {
                            val result = YouTube.searchSuggestions(query).getOrNull()
                            val aiContentFilterPolicy = loadAiContentFilterPolicy()
                            database
                                .searchHistory(query)
                                .map { it.take(3) }
                                .map { history ->
                                    SearchSuggestionViewState(
                                        history = history,
                                        suggestions =
                                            result
                                                ?.queries
                                                ?.filter { suggestion ->
                                                    history.none { it.query == suggestion }
                                                }.orEmpty(),
                                        items =
                                            filterAiContent(
                                                result
                                                    ?.recommendedItems
                                                    ?.filterExplicit(
                                                        context.dataStore.get(
                                                            HideExplicitKey,
                                                            false,
                                                        ),
                                                    )?.filterVideo(context.dataStore.get(HideVideoKey, false))
                                                    .orEmpty(),
                                                aiContentFilterPolicy,
                                            ),
                                    )
                                }
                        }
                    }.collect {
                        _viewState.value = it
                    }
            }
        }

        fun updateQuery(query: String) {
            this.query.value = query
        }

        fun updateProvider(provider: SearchProvider) {
            this.provider.value = provider
        }

        fun deleteHistory(history: SearchHistory) {
            database.query {
                delete(history)
            }
        }
    }

data class SearchSuggestionViewState(
    val history: List<SearchHistory> = emptyList(),
    val spotifyItems: List<SpotifySearchItem> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val items: List<YTItem> = emptyList(),
)
