/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.common.collect.ImmutableList
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.aicontentfilter.FilterAiContentUseCase
import moe.rukamori.archivetune.aicontentfilter.LoadAiContentFilterPolicyUseCase
import moe.rukamori.archivetune.aicontentfilter.ObserveAiContentFilterUseCase
import moe.rukamori.archivetune.auth.SwitchSavedYouTubeAccountUseCase
import moe.rukamori.archivetune.constants.AccountChannelHandleKey
import moe.rukamori.archivetune.constants.AccountEmailKey
import moe.rukamori.archivetune.constants.AccountNameKey
import moe.rukamori.archivetune.constants.ContentCountryKey
import moe.rukamori.archivetune.constants.ContentLanguageKey
import moe.rukamori.archivetune.constants.DataSyncIdKey
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.constants.HideVideoKey
import moe.rukamori.archivetune.constants.HiddenHomeItemsKey
import moe.rukamori.archivetune.constants.InnerTubeCookieKey
import moe.rukamori.archivetune.constants.QuickPicks
import moe.rukamori.archivetune.constants.QuickPicksKey
import moe.rukamori.archivetune.constants.SpeedDialSongIdsKey
import moe.rukamori.archivetune.constants.YouTubeMusicRegionKey
import moe.rukamori.archivetune.constants.YtmSyncKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.*
import moe.rukamori.archivetune.extensions.filterBlockedArtists
import moe.rukamori.archivetune.extensions.toEnum
import moe.rukamori.archivetune.home.HomeAction
import moe.rukamori.archivetune.home.HomePresentationPreferences
import moe.rukamori.archivetune.home.HomeScreenState
import moe.rukamori.archivetune.home.HomeUiState
import moe.rukamori.archivetune.home.ObserveHomePresentationPreferencesUseCase
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.AccountChannel
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.innertube.models.filterExplicit
import moe.rukamori.archivetune.innertube.models.filterVideo
import moe.rukamori.archivetune.innertube.pages.HomePage
import moe.rukamori.archivetune.innertube.utils.completed
import moe.rukamori.archivetune.innertube.utils.hasYouTubeLoginCookie
import moe.rukamori.archivetune.models.SimilarRecommendation
import moe.rukamori.archivetune.utils.SavedAccount
import moe.rukamori.archivetune.utils.SpeedDialPinType
import moe.rukamori.archivetune.utils.SyncUtils
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import moe.rukamori.archivetune.utils.parseSpeedDialPins
import moe.rukamori.archivetune.utils.reportException
import moe.rukamori.archivetune.utils.toPlaybackAuthState
import timber.log.Timber
import javax.inject.Inject

sealed interface AccountChannelsState {
    data object Loading : AccountChannelsState

    data class Success(
        val channels: AccountChannelCollection,
    ) : AccountChannelsState

    data object Empty : AccountChannelsState

    data class Error(
        val message: String,
    ) : AccountChannelsState
}

@Immutable
data class AccountChannelCollection(
    val items: List<AccountChannelUiModel>,
)

@Immutable
data class AccountChannelUiModel(
    val name: String,
    val byline: String,
    val channelHandle: String,
    val thumbnailUrl: String?,
    val dataSyncId: String,
    val isSelected: Boolean,
)

private data class HomeLocalContent(
    val quickPicks: List<Song>,
    val speedDialItems: List<LocalItem>,
    val forgottenFavorites: List<Song>,
    val keepListening: List<LocalItem>,
    val recentlyPlayed: List<Song>,
    val heroPicks: List<Song>,
)

/**
 * Intermediate bundle used to stage the first 5 nullable home-content flows
 * before combining with `heroPicks`. Kotlin's `combine()` only natively
 * supports up to 5 flows, so we split the 6-way combine into two stages
 * to keep type inference working.
 */
private data class HomeLocalContentStage(
    val quickPicks: List<Song>?,
    val speedDialItems: List<LocalItem>,
    val forgottenFavorites: List<Song>?,
    val keepListening: List<LocalItem>?,
    val recentlyPlayed: List<Song>?,
)

private data class HomeRemoteContent(
    val homePage: HomePage?,
    val remoteQuickPicks: HomePage.Section?,
    val similarRecommendations: List<SimilarRecommendation>,
    val accountPlaylists: List<PlaylistItem>,
    val accountName: String,
    val accountImageUrl: String?,
)

private data class HomeContent(
    val local: HomeLocalContent,
    val remote: HomeRemoteContent,
    val selectedChip: HomePage.Chip?,
) {
    /**
     * `true` when there is at least one section [HomeContent] the composable
     * will actually render on screen.
     *
     * IMPORTANT: this must stay in sync with the `if (...) item { ... }`
     * guards in [HomeScreen.HomeContent]. The home composable renders:
     *   - heroPicks (always, when non-empty)
     *   - category chips (full mode only)
     *   - remote/local quick picks (full mode only)
     *   - recentlyPlayed (size > 1)
     *   - speedDialItems (full mode only)
     *   - keepListening
     *   - accountPlaylists (full mode only)
     *   - forgottenFavorites (full mode only)
     *   - similarRecommendations (full mode only)
     *   - ALL homePage.sections (full mode) or only "Live performance" (minimal mode)
     *
     * If you add a new section to the composable, mirror its visibility guard
     * here. If you remove one, drop it from here too.
     */
    val hasContent: Boolean
        get() =
            local.heroPicks.isNotEmpty() ||
                local.quickPicks.isNotEmpty() ||
                local.speedDialItems.isNotEmpty() ||
                local.forgottenFavorites.isNotEmpty() ||
                local.keepListening.isNotEmpty() ||
                (local.recentlyPlayed?.size ?: 0) > 1 ||
                remote.remoteQuickPicks?.items?.isNotEmpty() == true ||
                remote.similarRecommendations.isNotEmpty() ||
                remote.accountPlaylists.isNotEmpty() ||
                remote.homePage?.sections?.isNotEmpty() == true
}

private data class HomeStateInputs(
    val content: HomeContent,
    val preferences: HomePresentationPreferences,
    val isLoading: Boolean,
    val isInitialLoadComplete: Boolean,
    val loadError: Int?,
) {
    fun toScreenState(
        isRefreshing: Boolean,
        isLoadingMore: Boolean,
    ): HomeScreenState {
        if (!content.hasContent) {
            if (loadError != null && isInitialLoadComplete) {
                return HomeScreenState.Error(loadError)
            }
            if (isLoading || !isInitialLoadComplete) {
                return HomeScreenState.Loading
            }
            return HomeScreenState.Empty
        }

        return HomeScreenState.Success(
            HomeUiState(
                quickPicks = ImmutableList.copyOf(content.local.quickPicks),
                speedDialItems = ImmutableList.copyOf(content.local.speedDialItems),
                forgottenFavorites = ImmutableList.copyOf(content.local.forgottenFavorites),
                keepListening = ImmutableList.copyOf(content.local.keepListening),
                recentlyPlayed = ImmutableList.copyOf(content.local.recentlyPlayed),
                heroPicks = ImmutableList.copyOf(content.local.heroPicks),
                similarRecommendations = ImmutableList.copyOf(content.remote.similarRecommendations),
                accountPlaylists = ImmutableList.copyOf(content.remote.accountPlaylists),
                homePage = content.remote.homePage,
                remoteQuickPicks = content.remote.remoteQuickPicks,
                selectedChip = content.selectedChip,
                accountName = content.remote.accountName,
                accountImageUrl = content.remote.accountImageUrl,
                quickPicksDisplayMode = preferences.quickPicksDisplayMode,
                quickPicksMode = preferences.quickPicksMode,
                showCategoryChips = preferences.showCategoryChips,
                showTonalBackdrop = preferences.showTonalBackdrop,
                minimalHomeMode = preferences.minimalHomeMode,
                isRefreshing = isRefreshing,
                isLoadingMore = isLoadingMore,
            ),
        )
    }
}

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val database: MusicDatabase,
        private val syncUtils: SyncUtils,
        private val switchSavedYouTubeAccount: SwitchSavedYouTubeAccountUseCase,
        observeHomePresentationPreferences: ObserveHomePresentationPreferencesUseCase,
        observeAiContentFilter: ObserveAiContentFilterUseCase,
        private val loadAiContentFilterPolicy: LoadAiContentFilterPolicyUseCase,
        private val filterAiContent: FilterAiContentUseCase,
    ) : ViewModel() {
        private val isRefreshing = MutableStateFlow(false)
        private val isLoading = MutableStateFlow(false)
        private val isInitialLoadComplete = MutableStateFlow(false)
        private val loadError = MutableStateFlow<Int?>(null)
        private val isLoadingMore = MutableStateFlow(false)

        private val quickPicksMode =
            context.dataStore.data
                .map {
                    it[QuickPicksKey].toEnum(QuickPicks.QUICK_PICKS)
                }.distinctUntilChanged()

        private val quickPicks = MutableStateFlow<List<Song>?>(null)
        private val speedDialItems = MutableStateFlow<List<LocalItem>>(emptyList())
        private val forgottenFavorites = MutableStateFlow<List<Song>?>(null)
        private val keepListening = MutableStateFlow<List<LocalItem>?>(null)
        private val recentlyPlayed = MutableStateFlow<List<Song>?>(null)

        // IDs of items the user has hidden from the "Keep Listening" section.
        // Combined with keepListening to produce a filtered flow that excludes
        // hidden items. The user can hide items via the long-press menu.
        private val hiddenHomeItems =
            context.dataStore.data
                .map { it[HiddenHomeItemsKey] ?: emptySet() }
                .distinctUntilChanged()
        private val filteredKeepListening =
            combine(keepListening, hiddenHomeItems) { items, hidden ->
                if (items == null || hidden.isEmpty()) items
                else items.filter { item ->
                    val id = when (item) {
                        is Song -> item.id
                        is Album -> item.id
                        is Artist -> item.id
                        is Playlist -> item.id
                    }
                    id !in hidden
                }
            }
        // Three random songs from `quickPicks`, re-shuffled on every refresh.
        // Drives the "Jump back in" hero so the home page surfaces fresh
        // listening-preference-based picks each time the user opens the app
        // or pulls to refresh, instead of always showing the last-played 3.
        private val heroPicks = MutableStateFlow<List<Song>>(emptyList())
        private val similarRecommendations = MutableStateFlow<List<SimilarRecommendation>?>(null)
        private val accountPlaylists = MutableStateFlow<List<PlaylistItem>?>(null)
        private val homePage = MutableStateFlow<HomePage?>(null)
        private val remoteQuickPicks = MutableStateFlow<HomePage.Section?>(null)
        private val selectedChip = MutableStateFlow<HomePage.Chip?>(null)
        private val previousHomePage = MutableStateFlow<HomePage?>(null)
        private val previousRemoteQuickPicks = MutableStateFlow<HomePage.Section?>(null)

        private val _allLocalItems = MutableStateFlow<List<LocalItem>>(emptyList())
        val allLocalItems: StateFlow<List<LocalItem>> = _allLocalItems.asStateFlow()
        private val _allYtItems = MutableStateFlow<List<YTItem>>(emptyList())
        val allYtItems: StateFlow<List<YTItem>> = _allYtItems.asStateFlow()

        private val _accountName = MutableStateFlow("")
        val accountName: StateFlow<String> = _accountName.asStateFlow()
        private val _accountImageUrl = MutableStateFlow<String?>(null)
        val accountImageUrl: StateFlow<String?> = _accountImageUrl.asStateFlow()
        private val _accountChannelsState = MutableStateFlow<AccountChannelsState>(AccountChannelsState.Empty)
        val accountChannelsState: StateFlow<AccountChannelsState> = _accountChannelsState.asStateFlow()

        private val presentationPreferences = observeHomePresentationPreferences()
        private val aiContentFilterSettings =
            observeAiContentFilter()
                .map { (settings, _) -> settings }
                .distinctUntilChanged()

        private val localContent =
            combine(
                quickPicks,
                speedDialItems,
                forgottenFavorites,
                filteredKeepListening,
                recentlyPlayed,
            ) { quickPicks, speedDialItems, forgottenFavorites, keepListening, recentlyPlayed ->
                HomeLocalContentStage(
                    quickPicks = quickPicks,
                    speedDialItems = speedDialItems,
                    forgottenFavorites = forgottenFavorites,
                    keepListening = keepListening,
                    recentlyPlayed = recentlyPlayed,
                )
            }.combine(heroPicks) { stage, heroPicks ->
                HomeLocalContent(
                    quickPicks = stage.quickPicks.orEmpty(),
                    speedDialItems = stage.speedDialItems,
                    forgottenFavorites = stage.forgottenFavorites.orEmpty(),
                    keepListening = stage.keepListening.orEmpty(),
                    recentlyPlayed = stage.recentlyPlayed.orEmpty(),
                    heroPicks = heroPicks,
                )
            }

        private val remoteContent =
            combine(
                homePage,
                remoteQuickPicks,
                similarRecommendations,
                accountPlaylists,
                accountName,
            ) { homePage, remoteQuickPicks, similarRecommendations, accountPlaylists, accountName ->
                HomeRemoteContent(
                    homePage = homePage,
                    remoteQuickPicks = remoteQuickPicks,
                    similarRecommendations = similarRecommendations.orEmpty(),
                    accountPlaylists = accountPlaylists.orEmpty(),
                    accountName = accountName,
                    accountImageUrl = null,
                )
            }.combine(accountImageUrl) { content, accountImageUrl ->
                content.copy(accountImageUrl = accountImageUrl)
            }

        private val homeContent =
            combine(
                localContent,
                remoteContent,
                selectedChip,
            ) { localContent, remoteContent, selectedChip ->
                HomeContent(
                    local = localContent,
                    remote = remoteContent,
                    selectedChip = selectedChip,
                )
            }

        val screenState: StateFlow<HomeScreenState> =
            combine(
                homeContent,
                presentationPreferences,
                isLoading,
                isInitialLoadComplete,
                loadError,
            ) { content, preferences, isLoading, isInitialLoadComplete, loadError ->
                HomeStateInputs(
                    content = content,
                    preferences = preferences,
                    isLoading = isLoading,
                    isInitialLoadComplete = isInitialLoadComplete,
                    loadError = loadError,
                )
            }.combine(
                combine(isRefreshing, isLoadingMore) { isRefreshing, isLoadingMore ->
                    isRefreshing to isLoadingMore
                },
            ) { inputs, loadingState ->
                inputs.toScreenState(
                    isRefreshing = loadingState.first,
                    isLoadingMore = loadingState.second,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = HomeScreenState.Loading,
            )

        private var wasLoggedIn = false
        private var chipLoadJob: Job? = null

        private fun filterHomeChips(chips: List<HomePage.Chip>?): List<HomePage.Chip>? =
            chips?.filterNot {
                it.title.contains("podcasts", ignoreCase = true)
            }

        private fun HomePage.extractQuickPicks(): Pair<HomePage, HomePage.Section?> {
            val quickPicksIndex = sections.indexOfFirst { section ->
                section.title.equals(context.getString(R.string.quick_picks), ignoreCase = true) ||
                    section.title.contains("quick pick", ignoreCase = true)
            }
            if (quickPicksIndex < 0) return this to null

            return copy(sections = sections.toMutableList().apply { removeAt(quickPicksIndex) }) to sections[quickPicksIndex]
        }

        private fun List<Song>.toQuickPickSample(): List<Song> =
            filter { song -> song.artists.none { it.blockedAt != null } }
                .distinctBy { it.id }
                .shuffled()
                .take(20)

        private fun List<Song>.hasSameSongIdsAs(other: List<Song>): Boolean {
            if (size != other.size) return false

            val ids = HashSet<String>(size)
            for (song in this) {
                ids += song.id
            }
            for (song in other) {
                if (!ids.remove(song.id)) return false
            }
            return ids.isEmpty()
        }

        private fun Flow<List<Song>>.distinctUntilSongIdsChanged(): Flow<List<Song>> =
            distinctUntilChanged { old, new -> old.hasSameSongIdsAs(new) }

        private fun updateAllLocalItems() {
            _allLocalItems.value =
                (quickPicks.value.orEmpty() + forgottenFavorites.value.orEmpty() + keepListening.value.orEmpty() + recentlyPlayed.value.orEmpty())
                    .filter { it is Song || it is Album }
        }

        private suspend fun quickPicksWithFallback(primary: List<Song>): List<Song> {
            val primaryPicks = primary.toQuickPickSample()
            if (primaryPicks.isNotEmpty()) return primaryPicks

            val recentPicks = database.recentSongs(limit = 60).first().toQuickPickSample()
            if (recentPicks.isNotEmpty()) return recentPicks

            return database.allSongs().first().toQuickPickSample()
        }

        private fun lastListenQuickPicksFlow(): Flow<List<Song>> =
            database
                .lastEventSongId()
                .distinctUntilChanged()
                .flatMapLatest { lastSongId ->
                    flow {
                        if (!lastSongId.isNullOrBlank() && database.hasRelatedSongs(lastSongId)) {
                            val relatedSongs = database.getRelatedSongs(lastSongId).first().toQuickPickSample()
                            if (relatedSongs.isNotEmpty()) {
                                emit(relatedSongs)
                                return@flow
                            }
                        }

                        emitAll(
                            database
                                .quickPicks()
                                .distinctUntilSongIdsChanged()
                                .map { songs -> quickPicksWithFallback(songs) },
                        )
                    }
                }

        private fun observeQuickPicks() {
            viewModelScope.launch(Dispatchers.IO) {
                quickPicksMode
                    .flatMapLatest { mode ->
                        when (mode) {
                            QuickPicks.QUICK_PICKS -> {
                                database
                                    .quickPicks()
                                    .distinctUntilSongIdsChanged()
                                    .map { songs -> quickPicksWithFallback(songs) }
                            }

                            QuickPicks.LAST_LISTEN -> {
                                lastListenQuickPicksFlow()
                            }

                            QuickPicks.DONT_SHOW -> {
                                flowOf(null)
                            }
                        }
                    }.catch { throwable ->
                        reportException(throwable)
                        emit(quickPicksWithFallback(emptyList()))
                    }.collect { picks ->
                        quickPicks.value = picks
                        refreshHeroPicks(picks.orEmpty())
                        updateAllLocalItems()
                    }
            }
        }

        private suspend fun refreshQuickPicks() {
            val picks =
                when (quickPicksMode.first()) {
                    QuickPicks.QUICK_PICKS -> {
                        quickPicksWithFallback(database.quickPicks().first())
                    }

                    QuickPicks.LAST_LISTEN -> {
                        lastListenQuickPicksFlow().first()
                    }

                    QuickPicks.DONT_SHOW -> {
                        null
                    }
                }
            quickPicks.value = picks
            refreshHeroPicks(picks.orEmpty())
            updateAllLocalItems()
        }

        /**
         * Re-shuffles the hero picks from the current quickPicks pool. Called on every
         * quickPicks update and on every manual refresh so the "Jump back in" hero at
         * the top of the home page surfaces fresh listening-preference-based songs
         * each visit, instead of always showing the last-played three.
         *
         * If quickPicks is empty, falls back to recentlyPlayed so the hero still shows
         * something on a fresh install where the user has listening history but no
         * quickPicks computation yet.
         */
        private fun refreshHeroPicks(pool: List<Song>) {
            val source = if (pool.isNotEmpty()) pool else recentlyPlayed.value.orEmpty()
            heroPicks.value =
                if (source.isEmpty()) {
                    emptyList()
                } else {
                    source.shuffled().take(3)
                }
        }

        private suspend fun loadSpeedDialItems() {
            val pins = parseSpeedDialPins(context.dataStore.get(SpeedDialSongIdsKey, ""))
            if (pins.isEmpty()) {
                speedDialItems.value = emptyList()
                return
            }
            val songIds = pins.filter { it.type == SpeedDialPinType.SONG }.map { it.id }
            val albumIds = pins.filter { it.type == SpeedDialPinType.ALBUM }.map { it.id }
            val artistIds = pins.filter { it.type == SpeedDialPinType.ARTIST }.map { it.id }
            val playlistIds = pins.filter { it.type == SpeedDialPinType.PLAYLIST }.map { it.id }

            val songsById = database.getSongsByIds(songIds).associateBy { it.id }
            val albumsById = albumIds.mapNotNull { id -> database.album(id).first() }.associateBy { it.id }
            val artistsById = artistIds.mapNotNull { id -> database.artist(id).first() }.associateBy { it.id }
            val playlistsById = playlistIds.mapNotNull { id -> database.getPlaylistById(id) }.associateBy { it.id }

            speedDialItems.value =
                pins
                    .mapNotNull { pin ->
                        when (pin.type.value) {
                            SpeedDialPinType.SONG.value -> songsById[pin.id]
                            SpeedDialPinType.ALBUM.value -> albumsById[pin.id]
                            SpeedDialPinType.ARTIST.value -> artistsById[pin.id]
                            SpeedDialPinType.PLAYLIST.value -> playlistsById[pin.id]
                            else -> null
                        }
                    }.filter { item ->
                        when (item) {
                            is Song -> item.artists.none { it.blockedAt != null }
                            is Album -> item.artists.none { it.blockedAt != null }
                            is Artist -> item.artist.blockedAt == null
                            else -> true
                        }
                    }
        }

        private suspend fun load() {
            if (isLoading.value) return
            isLoading.value = true
            loadError.value = null

            try {
                val aiContentFilterPolicy = loadAiContentFilterPolicy()
                supervisorScope {
                    val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                    val hideVideo = context.dataStore.get(HideVideoKey, false)
                    val blockedArtistIds = database.getBlockedArtistIds().toSet()
                    val fromTimeStamp = System.currentTimeMillis() - 86400000 * 7 * 2

                    launch { loadSpeedDialItems() }
                    launch {
                        forgottenFavorites.value =
                            database
                                .forgottenFavorites()
                                .first()
                                .filter { song -> song.artists.none { it.blockedAt != null } }
                                .shuffled()
                                .take(20)
                    }

                    launch {
                        // Recently played — chronological recents, used by the
                        // "Recently Played" square-card row. Filter out blocked
                        // artists and cap at 30 so the row has enough to draw
                        // from without over-fetching. (The "Jump back in" hero
                        // at the top of the home page now uses `heroPicks` —
                        // random songs from listening preference — instead of
                        // the top 3 of this list.)
                        recentlyPlayed.value =
                            database
                                .recentSongs(limit = 30)
                                .first()
                                .filter { song -> song.artists.none { it.blockedAt != null } }
                                .distinctBy { it.id }
                                .take(30)
                    }

                    launch {
                        // Initial hero picks — re-shuffled from the current
                        // quickPicks pool (or recentlyPlayed fallback). On
                        // subsequent manual refreshes, refresh() re-shuffles
                        // again so the hero rotates fresh picks each visit.
                        val pool = quickPicks.value.orEmpty()
                        val source = if (pool.isNotEmpty()) {
                            pool
                        } else {
                            // Wait briefly for recentlyPlayed to populate so
                            // we don't fall back to empty on a cold start.
                            kotlinx.coroutines.delay(100L)
                            recentlyPlayed.value.orEmpty()
                        }
                        refreshHeroPicks(source)
                    }

                    launch {
                        val keepListeningSongs =
                            database
                                .mostPlayedSongs(fromTimeStamp, limit = 15, offset = 5)
                                .first()
                                .filter { song -> song.artists.none { it.blockedAt != null } }
                                .shuffled()
                                .take(10)
                        val keepListeningAlbums =
                            database
                                .mostPlayedAlbums(fromTimeStamp, limit = 8, offset = 2)
                                .first()
                                .filter { it.album.thumbnailUrl != null && it.artists.none { artist -> artist.blockedAt != null } }
                                .shuffled()
                                .take(5)
                        val keepListeningArtists =
                            database
                                .mostPlayedArtists(fromTimeStamp)
                                .first()
                                .filter {
                                    it.artist.blockedAt == null &&
                                        it.artist.isYouTubeArtist &&
                                        it.artist.thumbnailUrl != null
                                }.shuffled()
                                .take(5)
                        keepListening.value = (keepListeningSongs + keepListeningAlbums + keepListeningArtists).shuffled()
                    }

                    launch {
                        YouTube
                            .home()
                            .onSuccess { page ->
                                val filteredPage =
                                    page.copy(
                                        chips = filterHomeChips(page.chips),
                                        sections =
                                            page.sections.map { section ->
                                                section.copy(
                                                    items =
                                                        filterAiContent(
                                                            section.items
                                                                .filterExplicit(hideExplicit)
                                                                .filterVideo(hideVideo)
                                                                .filterBlockedArtists(blockedArtistIds),
                                                            aiContentFilterPolicy,
                                                        ),
                                                )
                                            },
                                    )
                                val (pageWithoutQuickPicks, quickPicksSection) = filteredPage.extractQuickPicks()
                                remoteQuickPicks.value = quickPicksSection
                                homePage.value = pageWithoutQuickPicks
                            }.onFailure {
                                reportException(it)
                                loadError.value = R.string.error_unknown
                            }
                    }
                }

                updateAllLocalItems()

                viewModelScope.launch(Dispatchers.IO) {
                    loadSimilarRecommendations()
                }

                _allYtItems.value = similarRecommendations.value?.flatMap { it.items }.orEmpty() +
                    remoteQuickPicks.value?.items.orEmpty() +
                    homePage.value
                        ?.sections
                        ?.flatMap { it.items }
                        .orEmpty()

                isInitialLoadComplete.value = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportException(e)
                loadError.value = R.string.error_unknown
            } finally {
                isInitialLoadComplete.value = true
                isLoading.value = false
            }
        }

        private suspend fun loadSimilarRecommendations() {
            val hideExplicit = context.dataStore.get(HideExplicitKey, false)
            val hideVideo = context.dataStore.get(HideVideoKey, false)
            val blockedArtistIds = database.getBlockedArtistIds().toSet()
            val aiContentFilterPolicy = loadAiContentFilterPolicy()
            val fromTimeStamp = System.currentTimeMillis() - 86400000 * 7 * 2

            val artistRecommendations =
                database
                    .mostPlayedArtists(fromTimeStamp, limit = 10)
                    .first()
                    .filter { it.artist.blockedAt == null && it.artist.isYouTubeArtist }
                    .shuffled()
                    .take(3)
                    .mapNotNull {
                        val items = mutableListOf<YTItem>()
                        YouTube.artist(it.id).onSuccess { page ->
                            items +=
                                page.sections
                                    .getOrNull(page.sections.size - 2)
                                    ?.items
                                    .orEmpty()
                            items +=
                                page.sections
                                    .lastOrNull()
                                    ?.items
                                    .orEmpty()
                        }
                        SimilarRecommendation(
                            title = it,
                            items =
                                filterAiContent(
                                    items
                                        .filterExplicit(hideExplicit)
                                        .filterVideo(hideVideo)
                                        .filterBlockedArtists(blockedArtistIds),
                                    aiContentFilterPolicy,
                                ).shuffled()
                                    .ifEmpty { return@mapNotNull null },
                        )
                    }

            val songRecommendations =
                database
                    .mostPlayedSongs(fromTimeStamp, limit = 10)
                    .first()
                    .filter { it.album != null }
                    .shuffled()
                    .take(2)
                    .mapNotNull { song ->
                        val endpoint =
                            YouTube.next(WatchEndpoint(videoId = song.id)).getOrNull()?.relatedEndpoint
                                ?: return@mapNotNull null
                        val page = YouTube.related(endpoint).getOrNull() ?: return@mapNotNull null
                        SimilarRecommendation(
                            title = song,
                            items =
                                filterAiContent(
                                    (
                                        page.songs.shuffled().take(8) +
                                            page.albums.shuffled().take(4) +
                                            page.artists.shuffled().take(4) +
                                            page.playlists.shuffled().take(4)
                                    ).filterExplicit(hideExplicit)
                                        .filterVideo(hideVideo)
                                        .filterBlockedArtists(blockedArtistIds),
                                    aiContentFilterPolicy,
                                ).shuffled()
                                    .ifEmpty { return@mapNotNull null },
                        )
                    }

            similarRecommendations.value = (artistRecommendations + songRecommendations).shuffled()

            _allYtItems.value = similarRecommendations.value?.flatMap { it.items }.orEmpty() +
                remoteQuickPicks.value?.items.orEmpty() +
                homePage.value
                    ?.sections
                    ?.flatMap { it.items }
                    .orEmpty()
        }

        private fun clearAccountData() {
            _accountName.value = ""
            _accountImageUrl.value = null
            accountPlaylists.value = null
            _accountChannelsState.value = AccountChannelsState.Empty
        }

        private fun prepareYouTubeAccount(cookie: String): Boolean =
            try {
                YouTube.cookie = cookie
                true
            } catch (e: Exception) {
                Timber.e(e, "Failed to set YouTube cookie")
                false
            }

        private suspend fun refreshAccountIdentity() {
            _accountName.value = ""
            _accountImageUrl.value = null
            _accountChannelsState.value = AccountChannelsState.Loading

            try {
                YouTube
                    .accountInfo()
                    .onSuccess { info ->
                        _accountName.value = info.name
                        _accountImageUrl.value = info.thumbnailUrl
                    }.onFailure { error ->
                        Timber.w(error, "Failed to fetch account info")
                    }

                YouTube
                    .accountChannels()
                    .onSuccess { channels ->
                        _accountChannelsState.value = channels
                            .map { it.toUiModel() }
                            .takeIf { it.size > 1 }
                            ?.let { AccountChannelsState.Success(AccountChannelCollection(it)) }
                            ?: AccountChannelsState.Empty
                    }.onFailure { error ->
                        Timber.w(error, "Failed to fetch account channels")
                        reportException(error)
                        _accountChannelsState.value = AccountChannelsState.Error(error.message.orEmpty())
                    }
            } catch (e: CancellationException) {
                _accountChannelsState.value = AccountChannelsState.Empty
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Exception fetching account info")
                reportException(e)
                _accountChannelsState.value = AccountChannelsState.Error(e.message.orEmpty())
            }
        }

        private fun AccountChannel.toUiModel(): AccountChannelUiModel =
            AccountChannelUiModel(
                name = name,
                byline = byline.orEmpty(),
                channelHandle = channelHandle.orEmpty(),
                thumbnailUrl = thumbnailUrl,
                dataSyncId = dataSyncId,
                isSelected = isSelected,
            )

        private suspend fun refreshAccountPlaylistsInternal() {
            try {
                YouTube
                    .library("FEmusic_liked_playlists")
                    .completed()
                    .onSuccess {
                        val lists =
                            it.items.filterIsInstance<PlaylistItem>().filterNot { playlist ->
                                playlist.id == "SE"
                            }
                        accountPlaylists.value = lists
                    }.onFailure { error ->
                        if (error is CancellationException) throw error
                        Timber.w(error, "Failed to fetch account playlists")
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Exception fetching account playlists")
            }
        }

        private fun loadMoreYouTubeItems(continuation: String?) {
            if (continuation == null || isLoadingMore.value) return
            val hideExplicit = context.dataStore.get(HideExplicitKey, false)
            val hideVideo = context.dataStore.get(HideVideoKey, false)

            viewModelScope.launch(Dispatchers.IO) {
                isLoadingMore.value = true
                try {
                    val blockedArtistIds = database.getBlockedArtistIds().toSet()
                    val aiContentFilterPolicy = loadAiContentFilterPolicy()
                    val nextSections = YouTube.home(continuation).getOrNull() ?: return@launch
                    val mergedSections = homePage.value?.sections.orEmpty() + nextSections.sections
                    val mergedPage =
                        nextSections.copy(
                            chips = homePage.value?.chips,
                            sections =
                                mergedSections.map { section ->
                                    section.copy(
                                        items =
                                            filterAiContent(
                                                section.items
                                                    .filterExplicit(hideExplicit)
                                                    .filterVideo(hideVideo)
                                                    .filterBlockedArtists(blockedArtistIds),
                                                aiContentFilterPolicy,
                                            ),
                                    )
                                },
                        )
                    val (pageWithoutQuickPicks, quickPicksSection) = mergedPage.extractQuickPicks()
                    quickPicksSection?.let { remoteQuickPicks.value = it }
                    homePage.value = pageWithoutQuickPicks
                } finally {
                    isLoadingMore.value = false
                }
            }
        }

        private fun toggleChip(chip: HomePage.Chip?) {
            chipLoadJob?.cancel()
            if (chip == null || chip == selectedChip.value && previousHomePage.value != null) {
                homePage.value = previousHomePage.value
                remoteQuickPicks.value = previousRemoteQuickPicks.value
                previousHomePage.value = null
                previousRemoteQuickPicks.value = null
                selectedChip.value = null
                return
            }

            if (selectedChip.value == null) {
                previousHomePage.value = homePage.value
                previousRemoteQuickPicks.value = remoteQuickPicks.value
            }

            chipLoadJob =
                viewModelScope.launch(Dispatchers.IO) {
                    val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                    val hideVideo = context.dataStore.get(HideVideoKey, false)
                    val blockedArtistIds = database.getBlockedArtistIds().toSet()
                    val aiContentFilterPolicy = loadAiContentFilterPolicy()
                    val nextSections = YouTube.home(params = chip?.endpoint?.params).getOrNull() ?: return@launch
                    val filteredPage =
                        nextSections.copy(
                            chips = homePage.value?.chips,
                            sections =
                                nextSections.sections.map { section ->
                                    section.copy(
                                        items =
                                            filterAiContent(
                                                section.items
                                                    .filterExplicit(hideExplicit)
                                                    .filterVideo(hideVideo)
                                                    .filterBlockedArtists(blockedArtistIds),
                                                aiContentFilterPolicy,
                                            ),
                                    )
                                },
                        )
                    val (pageWithoutQuickPicks, quickPicksSection) = filteredPage.extractQuickPicks()
                    remoteQuickPicks.value = quickPicksSection
                    homePage.value = pageWithoutQuickPicks
                    selectedChip.value = chip
                }
        }

        fun onAction(action: HomeAction) {
            when (action) {
                HomeAction.Refresh -> refresh()
                is HomeAction.SelectChip -> toggleChip(action.chip)
                is HomeAction.LoadMore -> loadMoreYouTubeItems(action.continuation)
            }
        }

        private fun refresh() {
            if (isRefreshing.value) return
            viewModelScope.launch(Dispatchers.IO) {
                isRefreshing.value = true
                try {
                    supervisorScope {
                        launch { load() }
                        launch { refreshQuickPicks() }
                        // Re-shuffle hero picks on every manual pull-to-refresh so the
                        // "Jump back in" hero at the top of the home page surfaces fresh
                        // listening-preference-based songs each visit. Uses the current
                        // quickPicks pool (or recentlyPlayed fallback) so it works even
                        // before refreshQuickPicks() finishes.
                        launch {
                            val pool = quickPicks.value.orEmpty()
                            val source = if (pool.isNotEmpty()) {
                                pool
                            } else {
                                recentlyPlayed.value.orEmpty()
                            }
                            // Tiny delay so quickPicks refresh can race ahead and
                            // populate the pool first if it's faster than this launch.
                            kotlinx.coroutines.delay(50L)
                            val finalPool = quickPicks.value.orEmpty()
                            val finalSource = if (finalPool.isNotEmpty()) finalPool else source
                            refreshHeroPicks(finalSource)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    reportException(e)
                } finally {
                    isRefreshing.value = false
                }
            }
        }

        fun switchToAccount(
            account: SavedAccount,
            forceSyncOnSwitch: Boolean,
        ) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val authState = switchSavedYouTubeAccount(account).getOrThrow()

                    if (forceSyncOnSwitch && account.ytmSync && authState.hasLoginCookie) {
                        syncUtils.performFullSync(authoritative = true)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error switching account")
                    reportException(e)
                }
            }
        }

        fun switchToAccountChannel(
            channel: AccountChannelUiModel,
            forceSyncOnSwitch: Boolean,
        ) {
            if (channel.dataSyncId.isBlank()) return

            viewModelScope.launch(Dispatchers.IO) {
                try {
                    _accountChannelsState.value = AccountChannelsState.Loading

                    context.dataStore.edit { preferences ->
                        preferences[DataSyncIdKey] = channel.dataSyncId
                        preferences[AccountNameKey] = channel.name
                        preferences[AccountChannelHandleKey] = channel.channelHandle
                        if (channel.byline.contains("@")) {
                            preferences[AccountEmailKey] = channel.byline
                        }
                    }

                    val authState =
                        context.dataStore.data
                            .first()
                            .toPlaybackAuthState()
                    YouTube.authState = authState

                    supervisorScope {
                        launch { refreshAccountIdentity() }
                        launch { refreshAccountPlaylistsInternal() }
                    }

                    if (forceSyncOnSwitch && context.dataStore.get(YtmSyncKey, true) && authState.hasLoginCookie) {
                        syncUtils.performFullSync(authoritative = true)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Error switching account channel")
                    reportException(e)
                    _accountChannelsState.value = AccountChannelsState.Error(e.message.orEmpty())
                }
            }
        }

        init {
            observeQuickPicks()

            viewModelScope.launch(Dispatchers.IO) {
                load()
            }

            // Re-fetch the home feed whenever the YT Music region (or content country/language)
            // changes. This is what makes the "YouTube Music region" setting in Internet
            // Settings actually take effect on the home screen: when the user picks a country,
            // InternetSettings writes YouTubeMusicRegionKey to the DataStore AND mutates
            // YouTube.locale.gl in-memory — we observe the preference here and trigger a
            // fresh YouTube.home() call. Without this, the user would have to manually
            // pull-to-refresh after changing the region.
            //
            // We observe ContentCountryKey and ContentLanguageKey too, since those also
            // mutate YouTube.locale (via App.kt's initializeDeferredAsync) and therefore
            // affect what the home feed returns.
            //
            // drop(1) so we don't re-fetch on initial subscription (the load() above
            // already covers the cold-start case).
            viewModelScope.launch(Dispatchers.IO) {
                context.dataStore.data
                    .map { Triple(it[YouTubeMusicRegionKey], it[ContentCountryKey], it[ContentLanguageKey]) }
                    .distinctUntilChanged()
                    .drop(1)
                    .collectLatest {
                        isLoading.filter { loading -> !loading }.first()
                        load()
                    }
            }

            viewModelScope.launch(Dispatchers.IO) {
                aiContentFilterSettings
                    .drop(1)
                    .collectLatest {
                        isLoading.filter { loading -> !loading }.first()
                        load()
                    }
            }

            viewModelScope.launch(Dispatchers.IO) {
                context.dataStore.data
                    .map { it[SpeedDialSongIdsKey].orEmpty() }
                    .distinctUntilChanged()
                    .collect {
                        loadSpeedDialItems()
                    }
            }

            viewModelScope.launch(Dispatchers.IO) {
                kotlinx.coroutines.delay(3000)

                syncUtils.cleanupDuplicatePlaylists()
            }

            viewModelScope.launch(Dispatchers.IO) {
                context.dataStore.data
                    .map { it[InnerTubeCookieKey] }
                    .distinctUntilChanged()
                    .collect { cookie ->
                        try {
                            val isLoggedIn = hasYouTubeLoginCookie(cookie)
                            val loginTransition = isLoggedIn && !wasLoggedIn
                            wasLoggedIn = isLoggedIn

                            if (isLoggedIn && cookie != null && cookie.isNotEmpty()) {
                                if (!prepareYouTubeAccount(cookie)) {
                                    clearAccountData()
                                    return@collect
                                }

                                supervisorScope {
                                    kotlinx.coroutines.delay(100)
                                    launch { refreshAccountIdentity() }
                                    launch { refreshAccountPlaylistsInternal() }
                                }

                                if (loginTransition) {
                                    launch {
                                        try {
                                            if (context.dataStore.get(YtmSyncKey, true)) {
                                                syncUtils.performFullSync()
                                            }
                                        } catch (e: Exception) {
                                            Timber.e(e, "Error during login-triggered sync")
                                            reportException(e)
                                        }
                                    }
                                }
                            } else {
                                clearAccountData()
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Timber.e(e, "Error processing cookie change")
                            clearAccountData()
                        }
                    }
            }
        }
    }
