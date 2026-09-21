/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 * Portions © vossgraves — github.com/vossgraves
 */

package moe.rukamori.archivetune.ui.screens


import android.graphics.Color as AndroidColor
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.StatPeriod
import moe.rukamori.archivetune.db.entities.Album
import moe.rukamori.archivetune.db.entities.Artist
import moe.rukamori.archivetune.db.entities.ListeningBySlot
import moe.rukamori.archivetune.db.entities.ListeningSummary
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.innertube.pages.HistoryPage
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.spotify.SpotifyLibraryViewModel
import moe.rukamori.archivetune.spotify.SpotifyMapper
import moe.rukamori.archivetune.spotify.isSpotifyRateLimitMessage
import moe.rukamori.archivetune.spotify.models.SpotifyPlayHistory
import moe.rukamori.archivetune.spotify.playedAtMillis
import moe.rukamori.archivetune.ui.component.ChoiceChipsRow
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.ItemThumbnail
import moe.rukamori.archivetune.ui.component.LocalAlbumsGrid
import moe.rukamori.archivetune.ui.component.LocalArtistsGrid
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.menu.AlbumMenu
import moe.rukamori.archivetune.ui.menu.ArtistMenu
import moe.rukamori.archivetune.ui.menu.SongMenu
import moe.rukamori.archivetune.ui.screens.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.yumaClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.joinByBullet
import moe.rukamori.archivetune.utils.makeTimeString
import moe.rukamori.archivetune.viewmodels.HistoryViewModel
import moe.rukamori.archivetune.viewmodels.RemoteHistoryUiState
import moe.rukamori.archivetune.viewmodels.StatsPeriodSelection
import moe.rukamori.archivetune.viewmodels.StatsScreenState
import moe.rukamori.archivetune.viewmodels.StatsUiData
import moe.rukamori.archivetune.viewmodels.StatsViewModel

/** How many ranked songs the list shows before it offers the rest; the "Show top 5" string matches it. */
private const val COLLAPSED_SONG_COUNT = 5

/** How many artists the breakdown chart and its count cover. */
private const val TOP_ARTIST_COUNT = 5

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
)
@Composable
fun StatsScreen(
    navController: NavController,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    var selectedSource by rememberSaveable { mutableStateOf(StatsSource.LOCAL) }
    var songsExpanded by rememberSaveable { mutableStateOf(false) }
    val historyViewModel: HistoryViewModel = hiltViewModel()
    val spotifyViewModel: SpotifyLibraryViewModel = hiltViewModel()
    val localState by viewModel.screenState.collectAsStateWithLifecycle()
    val remoteHistoryState by historyViewModel.remoteHistoryState.collectAsStateWithLifecycle()
    val spotifyHistory by spotifyViewModel.recentlyPlayed.collectAsStateWithLifecycle()
    val period by viewModel.periodSelection.collectAsStateWithLifecycle()
    val isYearPickerOpen by viewModel.yearPickerOpen.collectAsStateWithLifecycle()
    val rateLimitedMessage = stringResource(R.string.stats_remote_rate_limited)
    val unknownArtistLabel = stringResource(R.string.stats_unknown_artist)
    val remoteFailureMessage = stringResource(R.string.stats_remote_load_failed)
    val currentDate = remember { LocalDateTime.now() }

    LaunchedEffect(selectedSource) {
        when (selectedSource) {
            StatsSource.LOCAL -> Unit
            StatsSource.YOUTUBE -> historyViewModel.fetchRemoteHistory()
            StatsSource.SPOTIFY -> spotifyViewModel.loadRecentlyPlayed()
        }
    }

    // A new period ranks a different set of songs, so the list returns to its collapsed top.
    LaunchedEffect(period) { songsExpanded = false }

    val onRetry: () -> Unit = {
        when (selectedSource) {
            StatsSource.LOCAL -> viewModel.retry()
            StatsSource.YOUTUBE -> historyViewModel.fetchRemoteHistory()
            StatsSource.SPOTIFY -> spotifyViewModel.loadRecentlyPlayed(force = true)
        }
    }

    // The only place that knows which source is showing. Each one normalises its own feed into the
    // same dashboard, so everything below draws one layout and switching sources moves no card: a
    // source that cannot fill a card leaves it to draw its empty state.
    val content =
        when (selectedSource) {
            StatsSource.LOCAL ->
                when (val state = localState) {
                    StatsScreenState.Loading -> {
                        StatsStatusScreen(
                            navController = navController,
                            loading = true,
                            selectedSource = selectedSource,
                            onSourceSelected = { selectedSource = it },
                        )
                        return
                    }

                    StatsScreenState.Empty -> {
                        StatsStatusScreen(
                            navController = navController,
                            selectedSource = selectedSource,
                            onSourceSelected = { selectedSource = it },
                        )
                        return
                    }

                    is StatsScreenState.Error -> {
                        StatsStatusScreen(
                            navController = navController,
                            errorMessage = stringResource(state.messageResId),
                            onRetry = onRetry,
                            selectedSource = selectedSource,
                            onSourceSelected = { selectedSource = it },
                        )
                        return
                    }

                    is StatsScreenState.Success -> {
                        val dashboard = state.data.toDashboard()
                        StatsScreenContent(
                            dashboard = dashboard,
                            // The local library bounds its own range chips; nothing here has changed.
                            rangeChips = StatsRangeChips(period.option, dashboard.firstPlay, currentDate),
                            rangeIndex = period.index,
                        )
                    }
                }

            StatsSource.YOUTUBE ->
                when (val state = remoteHistoryState) {
                    RemoteHistoryUiState.Loading -> {
                        StatsStatusScreen(
                            navController = navController,
                            loading = true,
                            selectedSource = selectedSource,
                            onSourceSelected = { selectedSource = it },
                        )
                        return
                    }

                    RemoteHistoryUiState.Empty -> {
                        StatsStatusScreen(
                            navController = navController,
                            selectedSource = selectedSource,
                            onSourceSelected = { selectedSource = it },
                        )
                        return
                    }

                    RemoteHistoryUiState.Error -> {
                        StatsStatusScreen(
                            navController = navController,
                            errorMessage = remoteFailureMessage,
                            onRetry = onRetry,
                            selectedSource = selectedSource,
                            onSourceSelected = { selectedSource = it },
                        )
                        return
                    }

                    is RemoteHistoryUiState.Success -> {
                        val plays = remember(state.page) { state.page.historyPlays(unknownArtistLabel) }
                        val history = rememberStatsScreenContent(plays, period, currentDate)
                        if (history == null) {
                            StatsStatusScreen(
                                navController = navController,
                                selectedSource = selectedSource,
                                onSourceSelected = { selectedSource = it },
                            )
                            return
                        }
                        history
                    }
                }

            StatsSource.SPOTIFY -> {
                val recentlyPlayed = spotifyHistory.items
                when {
                    recentlyPlayed != null -> {
                        val plays = remember(recentlyPlayed) { recentlyPlayed.spotifyPlays(unknownArtistLabel) }
                        val history = rememberStatsScreenContent(plays, period, currentDate)
                        if (history == null) {
                            StatsStatusScreen(
                                navController = navController,
                                selectedSource = selectedSource,
                                onSourceSelected = { selectedSource = it },
                            )
                            return
                        }
                        history
                    }

                    spotifyHistory.errorMessage != null -> {
                        StatsStatusScreen(
                            navController = navController,
                            // Spotify's raw 429 body is not something to put in front of a reader, and the
                            // retry cooldown means waiting is the actual remedy.
                            errorMessage =
                                spotifyHistory.errorMessage?.let { message ->
                                    if (isSpotifyRateLimitMessage(message)) rateLimitedMessage else message
                                },
                            onRetry = onRetry,
                            selectedSource = selectedSource,
                            onSourceSelected = { selectedSource = it },
                        )
                        return
                    }

                    else -> {
                        StatsStatusScreen(
                            navController = navController,
                            loading = true,
                            selectedSource = selectedSource,
                            onSourceSelected = { selectedSource = it },
                        )
                        return
                    }
                }
            }
        }

    val dashboard = content.dashboard
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current
    val isPlayingFlow: Flow<Boolean> =
        remember(playerConnection) { playerConnection?.isPlaying ?: flowOf(false) }
    val mediaMetadataFlow: Flow<MediaMetadata?> =
        remember(playerConnection) { playerConnection?.mediaMetadata ?: flowOf(null) }
    val isPlaying by isPlayingFlow.collectAsStateWithLifecycle(initialValue = false)
    val mediaMetadata by mediaMetadataFlow.collectAsStateWithLifecycle(initialValue = null)
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val availableYears =
        remember(currentDate, dashboard.firstPlay) {
            val startYear = dashboard.firstPlay?.year ?: currentDate.year
            (currentDate.year downTo startYear).toList()
        }

    val visibleSongs = if (songsExpanded) dashboard.songs else dashboard.songs.take(COLLAPSED_SONG_COUNT)
    val queueItems =
        remember(dashboard.songs) {
            dashboard.songs.mapNotNull { song -> song.entity?.toMediaMetadata()?.toMediaItem() }
        }

    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier =
            Modifier
                .fillMaxSize()
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
                title = {
                    Text(
                        text = stringResource(R.string.stats),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                },
                actions = {
                    IconButton(
                        onClick = viewModel::showYearPicker,
                        onLongClick = {},
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Icon(
                            painterResource(R.drawable.auto_awesome),
                            contentDescription = stringResource(R.string.year_in_music),
                        )
                    }
                },
                scrollBehavior = topAppBarScrollBehavior,
            )
        },
    ) { scaffoldPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                contentPadding =
                    LocalPlayerAwareWindowInsets.current
                        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                        .asPaddingValues(),
                modifier =
                    Modifier
                        .widthIn(max = 1040.dp)
                        .fillMaxHeight()
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(top = scaffoldPadding.calculateTopPadding()),
            ) {
                item(key = "sourceControls", contentType = "controls") {
                    StatsSourceSelector(
                        selectedSource = selectedSource,
                        onSourceSelected = { selectedSource = it },
                        modifier = Modifier.animateItem(),
                    )
                }

                item(key = "rangeControls", contentType = "controls") {
                    StatsFilterPanel(modifier = Modifier.animateItem()) {
                        ChoiceChipsRow(
                            chips = content.rangeChips,
                            options =
                                listOf(
                                    OptionStats.CONTINUOUS to stringResource(R.string.continuous),
                                    OptionStats.WEEKS to stringResource(R.string.weeks),
                                    OptionStats.MONTHS to stringResource(R.string.months),
                                    OptionStats.YEARS to stringResource(R.string.years),
                                ),
                            selectedOption = period.option,
                            onSelectionChange = viewModel::onOptionSelected,
                            currentValue = content.rangeIndex,
                            onValueUpdate = viewModel::onChipIndexChanged,
                        )
                    }
                }

                item(key = "overview", contentType = "overview") {
                    StatsSummarySection(
                        summary = dashboard.summary,
                        modifier = Modifier.animateItem(),
                    )
                }

                item(key = "artistDistribution", contentType = "insights") {
                    Column(modifier = Modifier.animateItem()) {
                        StatsSectionHeader(
                            title = stringResource(R.string.stats_artist_breakdown),
                            supportingText = dashboard.artists.take(TOP_ARTIST_COUNT).size.toString(),
                        )
                        if (dashboard.artists.isEmpty()) {
                            StatsEmptyCard(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        } else {
                            SegmentedArtistChart(
                                slices = dashboard.artists.take(TOP_ARTIST_COUNT),
                                totalTimeListened = dashboard.summary.totalTimeListened,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                    }
                }

                item(key = "spotlights", contentType = "spotlights") {
                    StatsHighlightsSection(
                        topArtist = dashboard.artists.firstOrNull(),
                        topSong = dashboard.songs.firstOrNull(),
                        navController = navController,
                        modifier = Modifier.animateItem(),
                    )
                }

                item(key = "listeningPatterns", contentType = "insights") {
                    StatsListeningPatterns(
                        daySlots = dashboard.daySlots,
                        hourSlots = dashboard.hourSlots,
                        currentDayOfWeek = remember { LocalDateTime.now().dayOfWeek.value % 7 },
                        modifier = Modifier.animateItem(),
                    )
                }

                item(key = "mostPlayedSongsHeader", contentType = "sectionHeader") {
                    StatsSongsHeader(
                        title = stringResource(R.string.stats_top_songs),
                        count = dashboard.rankedSongCount,
                        shuffleEnabled = playerConnection != null && queueItems.isNotEmpty(),
                        onShuffle = {
                            playerConnection?.playQueue(
                                ListQueue(
                                    title = context.getString(R.string.most_played_songs),
                                    items = queueItems.shuffled(),
                                ),
                            )
                        },
                        modifier = Modifier.animateItem(),
                    )
                }

                itemsIndexed(
                    items = visibleSongs,
                    key = { _, song -> song.id },
                    contentType = { _, _ -> "ranked_song" },
                ) { index, song ->
                    StatsRankedRow(
                        song = song,
                        rank = index + 1,
                        count = visibleSongs.size,
                        isActive = song.id == mediaMetadata?.id,
                        isPlaying = isPlaying,
                        onClick = {
                            val entity = song.entity
                            if (song.id == mediaMetadata?.id) {
                                playerConnection?.player?.togglePlayPause()
                            } else if (entity != null) {
                                playerConnection?.playQueue(
                                    YouTubeQueue(
                                        endpoint = WatchEndpoint(song.id),
                                        preloadItem = entity.toMediaMetadata(),
                                    ),
                                )
                            }
                        },
                        onLongClick = {
                            val entity = song.entity
                            if (entity != null) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuState.show {
                                    SongMenu(
                                        originalSong = entity,
                                        navController = navController,
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            }
                        },
                        modifier = Modifier.animateItem(),
                    )
                }

                if (dashboard.rankedSongCount > COLLAPSED_SONG_COUNT) {
                    item(key = "songListExpansion", contentType = "sectionAction") {
                        TextButton(
                            onClick = { songsExpanded = !songsExpanded },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .animateItem(),
                        ) {
                            Icon(
                                painter =
                                    painterResource(
                                        if (songsExpanded) {
                                            R.drawable.expand_less
                                        } else {
                                            R.drawable.expand_more
                                        },
                                    ),
                                contentDescription = null,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text =
                                    if (songsExpanded) {
                                        stringResource(R.string.stats_show_top_songs)
                                    } else {
                                        stringResource(R.string.stats_show_all_songs, dashboard.rankedSongCount)
                                    },
                            )
                        }
                    }
                }

                item(key = "mostPlayedArtists", contentType = "sectionHeader") {
                    StatsSectionHeader(
                        title = stringResource(R.string.artists),
                        supportingText = dashboard.artists.size.toString(),
                        modifier = Modifier.animateItem(),
                    )
                }

                item(key = "artistsShelf", contentType = "artists_shelf") {
                    if (dashboard.artists.isEmpty()) {
                        StatsEmptyCard(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        )
                    } else {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(
                                items = dashboard.artists,
                                key = { artist -> artist.id },
                                contentType = { "artist" },
                            ) { artist ->
                                LocalArtistsGrid(
                                    title = artist.name,
                                    subtitle =
                                        joinByBullet(
                                            pluralStringResource(R.plurals.n_time, artist.songCount, artist.songCount),
                                            makeTimeString(artist.timeListenedMs),
                                        ),
                                    thumbnailUrl = artist.thumbnailUrl,
                                    modifier =
                                        Modifier
                                            .width(164.dp)
                                            .combinedClickable(
                                                // A remote feed names artists without a library row, so
                                                // there is nothing to open and the item stays inert.
                                                enabled = artist.entity != null,
                                                onClick = { navController.navigate("artist/${artist.id}") },
                                                onLongClick = {
                                                    val entity = artist.entity
                                                    if (entity != null) {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        menuState.show {
                                                            ArtistMenu(
                                                                originalArtist = entity,
                                                                coroutineScope = coroutineScope,
                                                                onDismiss = menuState::dismiss,
                                                            )
                                                        }
                                                    }
                                                },
                                            ),
                                )
                            }
                        }
                    }
                }

                item(key = "mostPlayedAlbumsHeader", contentType = "sectionHeader") {
                    StatsSectionHeader(
                        title = stringResource(R.string.albums),
                        supportingText = dashboard.albums.size.toString(),
                        modifier = Modifier.animateItem(),
                    )
                }

                item(key = "albumsRow", contentType = "albums_row") {
                    if (dashboard.albums.isEmpty()) {
                        StatsEmptyCard(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        )
                    } else {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            itemsIndexed(
                                items = dashboard.albums,
                                key = { _, album -> album.id },
                                contentType = { _, _ -> "album_grid" },
                            ) { index, album ->
                                LocalAlbumsGrid(
                                    title = "${index + 1}. ${album.title}",
                                    subtitle =
                                        joinByBullet(
                                            pluralStringResource(R.plurals.n_time, album.playCount, album.playCount),
                                            makeTimeString(album.timeListenedMs),
                                        ),
                                    thumbnailUrl = album.thumbnailUrl,
                                    isActive = album.id == mediaMetadata?.album?.id,
                                    isPlaying = isPlaying,
                                    modifier =
                                        Modifier
                                            .width(172.dp)
                                            .combinedClickable(
                                                enabled = album.entity != null,
                                                onClick = { navController.navigate("album/${album.id}") },
                                                onLongClick = {
                                                    val entity = album.entity
                                                    if (entity != null) {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        menuState.show {
                                                            AlbumMenu(
                                                                originalAlbum = entity,
                                                                navController = navController,
                                                                onDismiss = menuState::dismiss,
                                                            )
                                                        }
                                                    }
                                                },
                                            ).animateItem(),
                                )
                            }
                        }
                    }
                }
            }

            if (isYearPickerOpen) {
                StatsYearPickerDialog(
                    availableYears = availableYears,
                    selectedYear = currentDate.year,
                    onSelectYear = { year ->
                        viewModel.dismissYearPicker()
                        navController.navigate("year_in_music?year=$year")
                    },
                    onDismiss = viewModel::dismissYearPicker,
                )
            }
        }
    }
}

/**
 * One pane of glass for a stats card. Stats cards stand alone rather than stacking, so the stroke
 * is the single-segment one; the corner radius varies by card size.
 */
@Composable
private fun StatsGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = SettingsDimensions.GlassCornerRadius,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val yuma = LocalYumaColors.current
    val glass =
        modifier
            .fillMaxWidth()
            .yumaGlassCard(
                shape = RoundedCornerShape(cornerRadius),
                backgroundColor = yuma.glassBackground,
                borderColor = yuma.glassBorder,
            )
    if (onClick == null) {
        Box(modifier = glass, content = content)
    } else {
        Box(modifier = glass.yumaClickable(onClick = onClick), content = content)
    }
}

@Composable
private fun StatsStatusScreen(
    navController: NavController,
    loading: Boolean = false,
    errorMessage: String? = null,
    onRetry: (() -> Unit)? = null,
    selectedSource: StatsSource,
    onSourceSelected: (StatsSource) -> Unit,
) {
    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.stats)) },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
        ) {
            StatsSourceSelector(
                selectedSource = selectedSource,
                onSourceSelected = onSourceSelected,
            )
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (loading) {
                    LoadingIndicator(modifier = Modifier.size(48.dp))
                } else {
                    Column(
                        modifier = Modifier.widthIn(max = 420.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text =
                                if (errorMessage == null) {
                                    stringResource(R.string.stats_empty_title)
                                } else {
                                    errorMessage
                                },
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center,
                        )
                        if (errorMessage == null) {
                            Text(
                                text = stringResource(R.string.stats_empty_message),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                        if (onRetry != null) {
                            Button(onClick = onRetry) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class StatsSource(
    @StringRes val labelRes: Int,
) {
    LOCAL(R.string.stats_source_local),
    YOUTUBE(R.string.stats_source_youtube),
    SPOTIFY(R.string.stats_source_spotify),
}

@Composable
private fun StatsSourceSelector(
    selectedSource: StatsSource,
    onSourceSelected: (StatsSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatsSource.entries.forEach { source ->
            val selected = source == selectedSource
            Surface(
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSourceSelected(source) },
                shape = RoundedCornerShape(14.dp),
                color =
                    if (selected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
            ) {
                Text(
                    text = stringResource(source.labelRes),
                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                    color =
                        if (selected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        }
    }
}

/**
 * One artist the dashboard ranks, with the local library row behind it when the source has one.
 *
 * A remote feed carries artists as names, so [entity] stays null there and the artist's cards draw
 * without opening anything: there is no library row behind the name.
 */
@Immutable
private data class StatsArtistRank(
    val id: String,
    val name: String,
    val thumbnailUrl: String?,
    val songCount: Int,
    val timeListenedMs: Long,
    val entity: Artist? = null,
)

/** One album the dashboard ranks; [entity] is present only for the local library's own rows. */
@Immutable
private data class StatsAlbumRank(
    val id: String,
    val title: String,
    val thumbnailUrl: String?,
    val playCount: Int,
    val timeListenedMs: Long,
    val entity: Album? = null,
)

/** One song the dashboard ranks; [entity] is what makes a row playable and its menu reachable. */
@Immutable
private data class StatsSongRank(
    val id: String,
    val title: String,
    val thumbnailUrl: String?,
    val playCount: Int,
    val timeListenedMs: Long,
    val entity: Song? = null,
)

/**
 * Everything the one dashboard draws, whichever source filled it.
 *
 * A card a source cannot fill keeps whatever that source did supply — usually an empty list — and
 * draws its own empty state, so no card appears, moves or disappears with the source.
 */
@Immutable
private data class StatsDashboardData(
    val summary: ListeningSummary,
    val songs: List<StatsSongRank>,
    /** What the top-songs header counts: the local list drops ranks whose song has no library row. */
    val rankedSongCount: Int,
    val artists: List<StatsArtistRank>,
    val albums: List<StatsAlbumRank>,
    val daySlots: List<ListeningBySlot>,
    val hourSlots: List<ListeningBySlot>,
    /** The earliest play the source knows about, before any range filter bounds the range chips. */
    val firstPlay: LocalDateTime?,
)

/**
 * A source's dashboard and the range controls that produced it.
 *
 * The chips are bound by the source's own earliest play, so the row always has something to select;
 * [rangeIndex] is that selection clamped to them, which keeps the highlighted chip and the numbers
 * below it describing one window after a source switch.
 */
@Immutable
private data class StatsScreenContent(
    val dashboard: StatsDashboardData,
    val rangeChips: List<Pair<Int, String>>,
    val rangeIndex: Int,
)

/**
 * Composes one remote source's plays into the dashboard: the feed's own earliest play bounds the
 * range chips, the selection clamps to them, and only what that window covers is ranked.
 *
 * Returns null when the window holds nothing, which is the same empty screen the local library puts
 * up for a period it was not listened to in.
 */
@Composable
private fun rememberStatsScreenContent(
    plays: List<StatsPlay>,
    period: StatsPeriodSelection,
    currentDate: LocalDateTime,
): StatsScreenContent? {
    val firstPlay = remember(plays) { plays.firstPlay() }
    val rangeChips = StatsRangeChips(period.option, firstPlay ?: currentDate, currentDate)
    val rangeIndex = period.index.coerceIn(0, (rangeChips.size - 1).coerceAtLeast(0))
    val window = remember(period.option, rangeIndex) { period.windowMillis(rangeIndex) }
    val dashboard = remember(plays, window) { plays.toDashboard(window) }
    if (dashboard.summary.totalPlayCount == 0) return null
    return StatsScreenContent(
        dashboard = dashboard,
        rangeChips = rangeChips,
        rangeIndex = rangeIndex,
    )
}

/**
 * The range chips for one selection, from the earliest play the source knows about.
 *
 * A source's own feed bounds these rather than the local library: the chips have to describe the
 * feed the dashboard is drawing, or a remote source would offer windows it can never fill.
 */
@Composable
private fun StatsRangeChips(
    option: OptionStats,
    firstPlay: LocalDateTime?,
    currentDate: LocalDateTime,
): List<Pair<Int, String>> =
    when (option) {
        OptionStats.CONTINUOUS ->
            listOf(
                StatPeriod.WEEK_1.ordinal to pluralStringResource(R.plurals.n_week, 1, 1),
                StatPeriod.MONTH_1.ordinal to pluralStringResource(R.plurals.n_month, 1, 1),
                StatPeriod.MONTH_3.ordinal to pluralStringResource(R.plurals.n_month, 3, 3),
                StatPeriod.MONTH_6.ordinal to pluralStringResource(R.plurals.n_month, 6, 6),
                StatPeriod.YEAR_1.ordinal to pluralStringResource(R.plurals.n_year, 1, 1),
                StatPeriod.ALL.ordinal to stringResource(R.string.filter_all),
            )

        OptionStats.WEEKS -> remember(firstPlay, currentDate) { weeklyRangeChips(firstPlay, currentDate) }
        OptionStats.MONTHS -> remember(firstPlay, currentDate) { monthlyRangeChips(firstPlay, currentDate) }
        OptionStats.YEARS -> remember(firstPlay, currentDate) { yearlyRangeChips(firstPlay, currentDate) }
    }

/** The week windows, newest first, back to the week [firstPlay] fell in. */
private fun weeklyRangeChips(
    firstPlay: LocalDateTime?,
    currentDate: LocalDateTime,
): List<Pair<Int, String>> {
    val first = firstPlay ?: return emptyList()
    return generateSequence(currentDate) { it.minusWeeks(1) }
        .takeWhile { it.isAfter(first.minusWeeks(1)) }
        .mapIndexed { index, date ->
            val endDate = date.plusWeeks(1).minusDays(1).coerceAtMost(currentDate)
            val formatter = DateTimeFormatter.ofPattern("dd MMM")
            val startDateFormatted = formatter.format(date)
            val endDateFormatted = formatter.format(endDate)
            val text =
                when {
                    date.year != currentDate.year -> "$startDateFormatted, ${date.year} - $endDateFormatted, ${endDate.year}"
                    date.month != endDate.month -> "$startDateFormatted - $endDateFormatted"
                    else -> "${date.dayOfMonth} - $endDateFormatted"
                }
            Pair(index, text)
        }.toList()
}

/** The month windows, newest first, back to the month [firstPlay] fell in. */
private fun monthlyRangeChips(
    firstPlay: LocalDateTime?,
    currentDate: LocalDateTime,
): List<Pair<Int, String>> {
    val first = firstPlay ?: return emptyList()
    return generateSequence(currentDate.plusMonths(1).withDayOfMonth(1).minusDays(1)) { it.minusMonths(1) }
        .takeWhile { it.isAfter(first.withDayOfMonth(1)) }
        .mapIndexed { index, date ->
            val formatter = DateTimeFormatter.ofPattern("MMM")
            val text = if (date.year != currentDate.year) "${formatter.format(date)} ${date.year}" else formatter.format(date)
            Pair(index, text)
        }.toList()
}

/** The year windows, newest first, back to the year [firstPlay] fell in. */
private fun yearlyRangeChips(
    firstPlay: LocalDateTime?,
    currentDate: LocalDateTime,
): List<Pair<Int, String>> {
    val first = firstPlay ?: return emptyList()
    return generateSequence(currentDate.plusYears(1).withDayOfYear(1).minusDays(1)) { it.minusYears(1) }
        .takeWhile { it.isAfter(first) }
        .mapIndexed { index, date -> Pair(index, "${date.year}") }
        .toList()
}

/** The muted line a card shows in place of content its source had none of. */
@Composable
private fun StatsEmptyText() {
    Text(
        text = stringResource(R.string.stats_card_empty),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * A whole card standing in for a list its source has none of.
 *
 * Cards that a source cannot fill keep their place in the dashboard and show this, so a source
 * switch never moves the layout — it only swaps the numbers.
 */
@Composable
private fun StatsEmptyCard(modifier: Modifier = Modifier) {
    StatsGlassCard(modifier = modifier, cornerRadius = 22.dp) {
        Column(modifier = Modifier.padding(20.dp)) {
            StatsEmptyText()
        }
    }
}

/**
 * One listen as a remote feed reports it: the track, its artist and album, how long it lasted and
 * when it happened.
 *
 * A YouTube history section names the day of a whole shelf and nothing finer, so [hasClockTime] is
 * false there and the hour chart is left empty rather than filled with a midnight no play claimed.
 */
@Immutable
private data class StatsPlay(
    val trackId: String,
    val title: String,
    val thumbnailUrl: String?,
    val artistName: String,
    val album: StatsPlayAlbum?,
    val durationMs: Long,
    val playedAt: Instant?,
    val hasClockTime: Boolean = true,
)

/** An album as a feed reports it: what to group its plays by, and what an album row shows. */
@Immutable
private data class StatsPlayAlbum(
    val id: String,
    val title: String,
    val thumbnailUrl: String?,
)

/**
 * The YouTube history page as a play feed.
 *
 * Its sections carry the songs and date the whole shelf to a day, which is all the day chart needs
 * and all the hour chart must not use.
 */
private fun HistoryPage.historyPlays(unknownArtistLabel: String): List<StatsPlay> =
    sections.orEmpty().flatMap { section ->
        val playedAt = section.playedAt()
        section.songs.map { song ->
            StatsPlay(
                trackId = song.id,
                title = song.title,
                thumbnailUrl = song.thumbnail,
                artistName = song.artists.joinToString { it.name }.ifBlank { unknownArtistLabel },
                album =
                    song.album
                        ?.takeIf { it.name.isNotBlank() }
                        ?.let { album ->
                            // A song's own artwork is its album cover on YouTube Music; the album
                            // node nested in the item carries no artwork of its own.
                            StatsPlayAlbum(
                                id = album.id.ifBlank { album.name },
                                title = album.name,
                                thumbnailUrl = song.thumbnail,
                            )
                        },
                durationMs = (song.duration ?: 0).toLong() * 1_000L,
                playedAt = playedAt,
                hasClockTime = false,
            )
        }
    }

/** Spotify's recently played as a play feed. Every entry is stamped with a wall-clock instant. */
private fun List<SpotifyPlayHistory>.spotifyPlays(unknownArtistLabel: String): List<StatsPlay> =
    mapNotNull { played ->
        val track = played.track ?: return@mapNotNull null
        val artwork = SpotifyMapper.getTrackThumbnail(track)
        StatsPlay(
            trackId = track.id,
            title = track.name,
            thumbnailUrl = artwork,
            artistName = track.artists.joinToString { it.name }.ifBlank { unknownArtistLabel },
            album =
                track.album
                    ?.takeIf { it.name.isNotBlank() }
                    ?.let { album ->
                        StatsPlayAlbum(
                            id = album.id.ifBlank { album.name },
                            title = album.name,
                            thumbnailUrl = artwork,
                        )
                    },
            durationMs = track.durationMs.toLong(),
            playedAt = played.playedAtMillis()?.let(Instant::ofEpochMilli),
        )
    }

/** The day a history section's title names, or null when it names one this code cannot read. */
private fun HistoryPage.HistorySection.sectionDate(): LocalDate? {
    val title = title.trim().lowercase(Locale.getDefault())
    return when {
        title == "today" -> LocalDate.now()
        title == "yesterday" -> LocalDate.now().minusDays(1)
        title.contains("this week") -> LocalDate.now()
        title.contains("last week") -> LocalDate.now().minusDays(7)
        else -> parseHistoryDate(title)
    }
}

/** A section dates its songs to the day it names, which is what midnight of that day marks. */
private fun HistoryPage.HistorySection.playedAt(): Instant? =
    sectionDate()?.atStartOfDay(ZoneId.systemDefault())?.toInstant()

/** The earliest play the feed knows about; it bounds the range chips. */
private fun List<StatsPlay>.firstPlay(): LocalDateTime? =
    mapNotNull { it.playedAt }.minOrNull()?.let { earliest -> LocalDateTime.ofInstant(earliest, ZoneId.systemDefault()) }

/**
 * The local library's ranking as the dashboard model.
 *
 * Room has already windowed and ordered these, so this only carries each row's library entity
 * across — which is what lets a local card open, queue or menu what it is showing.
 */
private fun StatsUiData.toDashboard(): StatsDashboardData {
    val songsById = mostPlayedSongs.associateBy(Song::id)
    // A rank the entity query left out is a blocked artist's song: nothing behind it could be
    // opened or queued, so it is not a row the local list can draw.
    val songs =
        rankedSongs.mapNotNull { ranked ->
            songsById[ranked.id]?.let { entity ->
                StatsSongRank(
                    id = ranked.id,
                    title = ranked.title,
                    thumbnailUrl = ranked.thumbnailUrl,
                    playCount = ranked.songCountListened,
                    timeListenedMs = ranked.timeListened ?: 0L,
                    entity = entity,
                )
            }
        }
    return StatsDashboardData(
        summary = listeningSummary,
        songs = songs,
        rankedSongCount = rankedSongs.size,
        artists =
            mostPlayedArtists.map { artist ->
                StatsArtistRank(
                    id = artist.id,
                    name = artist.artist.name,
                    thumbnailUrl = artist.artist.thumbnailUrl,
                    songCount = artist.songCount,
                    timeListenedMs = artist.timeListened?.toLong() ?: 0L,
                    entity = artist,
                )
            },
        albums =
            mostPlayedAlbums.map { album ->
                StatsAlbumRank(
                    id = album.id,
                    title = album.album.title,
                    thumbnailUrl = album.album.thumbnailUrl,
                    playCount = album.songCountListened ?: 0,
                    timeListenedMs = album.timeListened?.toLong() ?: 0L,
                    entity = album,
                )
            },
        daySlots = listeningByDayOfWeek,
        hourSlots = listeningByHour,
        firstPlay = firstEvent?.event?.timestamp,
    )
}

/**
 * Ranks a remote feed into the dashboard model, keeping only the plays [window] covers.
 *
 * Ordering matches the local queries: songs by plays then time, artists and albums by time. A play
 * the feed cannot date is kept for the unbounded window and dropped by any other, because a range
 * with a start has nowhere to put a listen that never said when it happened.
 */
private fun List<StatsPlay>.toDashboard(window: LongRange): StatsDashboardData {
    val inWindow = filter { play -> play.playedAt?.let { it.toEpochMilli() in window } ?: (window.first == 0L) }
    val songs =
        inWindow
            .groupBy(StatsPlay::trackId)
            .map { (id, plays) ->
                val first = plays.first()
                StatsSongRank(
                    id = id,
                    title = first.title,
                    thumbnailUrl = first.thumbnailUrl,
                    playCount = plays.size,
                    timeListenedMs = plays.sumOf(StatsPlay::durationMs),
                )
            }
            .sortedWith(compareByDescending<StatsSongRank> { it.playCount }.thenByDescending { it.timeListenedMs })
    val artists =
        inWindow
            .groupBy(StatsPlay::artistName)
            .map { (name, plays) ->
                StatsArtistRank(
                    id = name,
                    name = name,
                    // A history feed names an artist without an id or artwork to show.
                    thumbnailUrl = null,
                    songCount = plays.map(StatsPlay::trackId).distinct().size,
                    timeListenedMs = plays.sumOf(StatsPlay::durationMs),
                )
            }
            .sortedWith(compareByDescending<StatsArtistRank> { it.timeListenedMs }.thenByDescending { it.songCount })
    val albums =
        inWindow
            .mapNotNull { play -> play.album?.let { album -> album to play } }
            .groupBy { (album, _) -> album.id }
            .map { (id, entries) ->
                val album = entries.first().first
                StatsAlbumRank(
                    id = id,
                    title = album.title,
                    thumbnailUrl = album.thumbnailUrl,
                    playCount = entries.size,
                    timeListenedMs = entries.sumOf { (_, play) -> play.durationMs },
                )
            }
            .sortedWith(compareByDescending<StatsAlbumRank> { it.timeListenedMs }.thenByDescending { it.playCount })
    return StatsDashboardData(
        summary =
            ListeningSummary(
                totalPlayCount = inWindow.size,
                totalTimeListened = inWindow.sumOf(StatsPlay::durationMs),
                uniqueSongsCount = songs.size,
                uniqueArtistsCount = artists.size,
                uniqueAlbumsCount = albums.size,
            ),
        songs = songs,
        rankedSongCount = songs.size,
        artists = artists,
        albums = albums,
        daySlots =
            slotsOf(
                plays = inWindow,
                slot = { play -> play.playedAt?.atZone(ZoneId.systemDefault())?.dayOfWeek?.value?.rem(7) },
                millis = StatsPlay::durationMs,
            ),
        hourSlots =
            slotsOf(
                plays = inWindow.filter(StatsPlay::hasClockTime),
                slot = { play -> play.playedAt?.atZone(ZoneId.systemDefault())?.hour },
                millis = StatsPlay::durationMs,
            ),
        firstPlay = firstPlay(),
    )
}

private fun parseHistoryDate(title: String): LocalDate? {
    val year = LocalDate.now().year
    val fullDateFormatters =
        listOf(
            DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMMM d, uuuu", Locale.ENGLISH),
        )
    fullDateFormatters.firstNotNullOfOrNull { formatter ->
        runCatching { LocalDate.parse(title.replaceFirstChar { it.uppercase() }, formatter) }.getOrNull()
    }?.let { return it }
    return listOf("MMM d", "MMMM d").firstNotNullOfOrNull { pattern ->
        runCatching {
            LocalDate.parse(
                "${title.replaceFirstChar { it.uppercase() }}, $year",
                DateTimeFormatter.ofPattern("$pattern, uuuu", Locale.ENGLISH),
            )
        }.getOrNull()
    }?.let { parsed -> if (parsed.isAfter(LocalDate.now())) parsed.minusYears(1) else parsed }
}

/**
 * Groups plays into the slot shape the local charts expect.
 *
 * Weighted by listening time rather than play count, because that is what the local charts measure:
 * `SUM(playTime)` per slot. Counting plays instead would draw a bar of the same height for a
 * thirty-second skip and a ten-minute track.
 */
private fun <T> slotsOf(
    plays: List<T>,
    slot: (T) -> Int?,
    millis: (T) -> Long,
): List<ListeningBySlot> =
    plays
        .mapNotNull { play -> slot(play)?.let { it to millis(play) } }
        .groupingBy { it.first }
        .fold(0L) { total, (_, ms) -> total + ms }
        .entries
        .sortedBy { it.key }
        .map { (slot, total) -> ListeningBySlot(slot = slot, timeListened = total) }

@Composable
private fun StatsFilterPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    StatsGlassCard(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        cornerRadius = SettingsDimensions.SegmentedCornerLarge,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.stats_time_range),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            content()
        }
    }
}

@Composable
private fun StatsSongsHeader(
    title: String,
    count: Int,
    shuffleEnabled: Boolean,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 24.dp, end = 16.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FilledTonalButton(
            onClick = onShuffle,
            enabled = shuffleEnabled,
        ) {
            Icon(
                painter = painterResource(R.drawable.shuffle),
                contentDescription = null,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.shuffle))
        }
    }
}

/**
 * The day and hour charts, both always drawn.
 *
 * A source that cannot fill one — YouTube's history carries no clock time — gets that chart's empty
 * state instead, so switching sources never takes a card away.
 */
@Composable
private fun StatsListeningPatterns(
    daySlots: List<ListeningBySlot>,
    hourSlots: List<ListeningBySlot>,
    currentDayOfWeek: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.stats_listening_patterns),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        BoxWithConstraints {
            if (maxWidth >= 720.dp && daySlots.isNotEmpty() && hourSlots.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ListeningByDayChart(
                        slots = daySlots,
                        currentDayOfWeek = currentDayOfWeek,
                        modifier = Modifier.weight(1f),
                    )
                    ListeningByHourChart(
                        slots = hourSlots,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ListeningByDayChart(
                        slots = daySlots,
                        currentDayOfWeek = currentDayOfWeek,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ListeningByHourChart(
                        slots = hourSlots,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsSectionHeader(
    title: String,
    supportingText: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = supportingText,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One row of the top-songs list for any source.
 *
 * The row draws only what the dashboard model carries, so a remote feed's ranking looks like the
 * local one. Playing the song, and the menu behind a long press, need the library row the local
 * source has and a remote feed does not, and simply do nothing without it.
 */
@Composable
private fun StatsRankedRow(
    song: StatsSongRank,
    rank: Int,
    count: Int,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowShapes = ListItemDefaults.segmentedShapes(index = rank - 1, count = count)
    val click = remember(song.id, onClick) { onClick }
    val longClick = remember(song.id, onLongClick) { onLongClick }

    SegmentedListItem(
        onClick = click,
        onLongClick = longClick,
        shapes = rowShapes,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 1.dp),
        colors =
            ListItemDefaults.segmentedColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        leadingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = rank.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color =
                        if (rank <= 3) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(24.dp),
                )
                ItemThumbnail(
                    thumbnailUrl = song.thumbnailUrl,
                    isActive = isActive,
                    isPlaying = isPlaying,
                    shape = MaterialTheme.shapes.small,
                    maxSizePx = 200,
                    modifier = Modifier.size(56.dp),
                )
            }
        },
        supportingContent = {
            Text(
                text =
                    joinByBullet(
                        pluralStringResource(
                            R.plurals.n_time,
                            song.playCount,
                            song.playCount,
                        ),
                        makeTimeString(song.timeListenedMs),
                    ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    ) {
        Text(
            text = song.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatsYearPickerDialog(
    availableYears: List<Int>,
    selectedYear: Int,
    onSelectYear: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.year_in_music),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(
                    items = availableYears,
                    key = { year -> year },
                    contentType = { "year_chip" },
                ) { year ->
                    val isSelected = year == selectedYear
                    Text(
                        text = year.toString(),
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    },
                                ).clickable { onSelectYear(year) }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color =
                            if (isSelected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.dismiss))
            }
        },
    )
}

@Composable
private fun StatsSummarySection(
    summary: ListeningSummary,
    modifier: Modifier = Modifier,
) {
    if (summary.totalPlayCount == 0) return

    StatsGlassCard(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        cornerRadius = 28.dp,
    ) {
        BoxWithConstraints(modifier = Modifier.padding(20.dp)) {
            val expanded = maxWidth >= 680.dp
            if (expanded) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatsListeningTimeHero(
                        summary = summary,
                        modifier = Modifier.weight(1.2f),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        StatMetricCard(
                            label = stringResource(R.string.stats_total_plays),
                            value = summary.totalPlayCount.toString(),
                        )
                        StatMetricCard(
                            label = stringResource(R.string.stats_unique_songs),
                            value = summary.uniqueSongsCount.toString(),
                        )
                        StatMetricCard(
                            label = stringResource(R.string.stats_unique_artists),
                            value = summary.uniqueArtistsCount.toString(),
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatsListeningTimeHero(summary = summary)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatMetricCard(
                            label = stringResource(R.string.stats_total_plays),
                            value = summary.totalPlayCount.toString(),
                            modifier = Modifier.weight(1f),
                        )
                        StatMetricCard(
                            label = stringResource(R.string.stats_unique_songs),
                            value = summary.uniqueSongsCount.toString(),
                            modifier = Modifier.weight(1f),
                        )
                        StatMetricCard(
                            label = stringResource(R.string.stats_unique_artists),
                            value = summary.uniqueArtistsCount.toString(),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsListeningTimeHero(
    summary: ListeningSummary,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.stats_total_time_listened),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = makeTimeString(summary.totalTimeListened) ?: "-",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun StatMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The spotlights pair, for any source.
 *
 * The local cards open the library row behind them; a history feed carries no such row, so a remote
 * artist's card is informational and simply does not navigate. The card itself is drawn either way.
 */
@Composable
private fun StatsHighlightsSection(
    topArtist: StatsArtistRank?,
    topSong: StatsSongRank?,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    if (topArtist == null && topSong == null) return

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (topArtist != null) {
            StatsHighlightCard(
                title = stringResource(R.string.stats_favourite_artist),
                mainText = topArtist.name,
                subText = "${topArtist.songCount} ${stringResource(
                    R.string.songs,
                ).lowercase()} • ${makeTimeString(topArtist.timeListenedMs)}",
                imageUrl = topArtist.thumbnailUrl,
                useCircleShape = true,
                onClick = {
                    topArtist.entity?.let { artist -> navController.navigate("artist/${artist.id}") }
                },
            )
        }
        if (topSong != null) {
            StatsHighlightCard(
                title = stringResource(R.string.stats_favourite_song),
                mainText = topSong.title,
                subText = "${pluralStringResource(
                    R.plurals.n_time,
                    topSong.playCount,
                    topSong.playCount,
                )} • ${makeTimeString(topSong.timeListenedMs)}",
                imageUrl = topSong.thumbnailUrl,
                useCircleShape = false,
                onClick = {},
            )
        }
    }
}

@Composable
private fun StatsHighlightCard(
    title: String,
    mainText: String,
    subText: String,
    imageUrl: String?,
    useCircleShape: Boolean,
    onClick: () -> Unit,
) {
    StatsGlassCard(
        cornerRadius = SettingsDimensions.SegmentedCornerLarge,
        onClick = onClick,
    ) {
        Row(
            modifier =
                Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .size(80.dp)
                        .clip(if (useCircleShape) CircleShape else MaterialTheme.shapes.medium),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = mainText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SegmentedArtistChart(
    slices: List<StatsArtistRank>,
    totalTimeListened: Long,
    modifier: Modifier = Modifier,
) {
    val visibleArtistTime = remember(slices) { slices.sumOf(StatsArtistRank::timeListenedMs) }
    val displayTotalTime =
        remember(totalTimeListened, visibleArtistTime) {
            totalTimeListened.takeIf { it > 0L } ?: visibleArtistTime
        }
    if (visibleArtistTime == 0L) return

    val segmentData =
        remember(slices, visibleArtistTime) {
            val rawSegments =
                slices.mapNotNull { slice ->
                    val time = slice.timeListenedMs
                    if (time <= 0L) return@mapNotNull null
                    slice to (time.toFloat() / visibleArtistTime) * 360f
                }

            if (rawSegments.isEmpty()) {
                emptyList()
            } else {
                val topArtistId = rawSegments.maxByOrNull { it.second }?.first?.id
                val retainedSegments =
                    rawSegments
                        .filter { (_, sweep) -> sweep >= 1f }
                        .ifEmpty { listOf(rawSegments.maxBy { it.second }) }
                val retainedSweep = retainedSegments.sumOf { it.second.toDouble() }.toFloat()
                val remainderSweep = (360f - retainedSweep).coerceAtLeast(0f)
                val completedSegments =
                    retainedSegments.map { (slice, sweep) ->
                        slice to
                            if (slice.id == topArtistId) {
                                sweep + remainderSweep
                            } else {
                                sweep
                            }
                    }

                var startAngle = -90f
                completedSegments.map { (slice, sweep) ->
                    Triple(slice, startAngle, sweep).also {
                        startAngle += sweep
                    }
                }
            }
        }

    val primaryColor = MaterialTheme.colorScheme.primary
    val segmentColors =
        remember(primaryColor, segmentData.size) {
            createDistinctArtistColors(
                seedColor = primaryColor,
                count = segmentData.size,
            )
        }

    StatsGlassCard(modifier = modifier, cornerRadius = 28.dp) {
        Row(
            modifier =
                Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(140.dp)
                        .drawWithCache {
                            val strokeWidth = size.width * 0.18f
                            val inset = strokeWidth / 2f
                            val arcRect =
                                Rect(
                                    left = inset,
                                    top = inset,
                                    right = size.width - inset,
                                    bottom = size.height - inset,
                                )
                            onDrawBehind {
                                segmentData.forEachIndexed { i, (_, startAngle, sweep) ->
                                    val gapDeg = if (segmentData.size > 1) 2f else 0f
                                    drawArc(
                                        color = segmentColors[i % segmentColors.size],
                                        startAngle = startAngle + gapDeg / 2f,
                                        sweepAngle = (sweep - gapDeg).coerceAtLeast(0f),
                                        useCenter = false,
                                        topLeft = arcRect.topLeft,
                                        size = Size(arcRect.width, arcRect.height),
                                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                                    )
                                }
                            }
                        },
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f),
            ) {
                segmentData.forEachIndexed { i, (slice, _, sweep) ->
                    val percentage = (sweep / 360f * 100).toInt()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(segmentColors[i % segmentColors.size]),
                        )
                        Text(
                            text = slice.name,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "$percentage%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = makeTimeString(displayTotalTime) ?: "-",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.stats_total_time_listened),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun createDistinctArtistColors(
    seedColor: Color,
    count: Int,
): List<Color> {
    if (count <= 0) return emptyList()

    val seedHsv = FloatArray(3)
    AndroidColor.colorToHSV(seedColor.toArgb(), seedHsv)
    val saturation = seedHsv[1].coerceAtLeast(0.62f)
    val brightness = seedHsv[2].coerceIn(0.68f, 0.88f)
    val hueStep = 360f / count

    return List(count) { index ->
        Color.hsv(
            hue = (seedHsv[0] + hueStep * index) % 360f,
            saturation = saturation,
            value = brightness,
        )
    }
}

@Composable
private fun ListeningByDayChart(
    slots: List<ListeningBySlot>,
    currentDayOfWeek: Int,
    modifier: Modifier = Modifier,
) {
    val dayLabels =
        listOf(
            R.string.day_sun,
            R.string.day_mon,
            R.string.day_tue,
            R.string.day_wed,
            R.string.day_thu,
            R.string.day_fri,
            R.string.day_sat,
        )
    val slotMap = remember(slots) { slots.associateBy { it.slot } }
    val maxTime = remember(slots) { slots.maxOfOrNull { it.timeListened } ?: 1L }
    val primaryColor = MaterialTheme.colorScheme.primary
    val containerColor = MaterialTheme.colorScheme.secondaryContainer

    StatsGlassCard(modifier = modifier, cornerRadius = 28.dp) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.stats_listening_by_day),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (slots.isEmpty()) {
                StatsEmptyText()
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    for (day in 0..6) {
                        val time = slotMap[day]?.timeListened ?: 0L
                        val fraction = time.toFloat() / maxTime
                        val barColor = if (day == currentDayOfWeek) primaryColor else containerColor
                        val animatedFraction by animateFloatAsState(
                            targetValue = fraction,
                            animationSpec = tween(400),
                            label = "bar_$day",
                        )
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .width(24.dp)
                                        .height(80.dp),
                                contentAlignment = Alignment.BottomCenter,
                            ) {
                                Box(
                                    modifier =
                                        Modifier
                                            .width(24.dp)
                                            .height((80 * animatedFraction).dp.coerceAtLeast(2.dp))
                                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                            .background(barColor),
                                )
                            }
                            Text(
                                text = stringResource(dayLabels[day]),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (day == currentDayOfWeek) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (day == currentDayOfWeek) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ListeningByHourChart(
    slots: List<ListeningBySlot>,
    modifier: Modifier = Modifier,
) {
    val slotMap = remember(slots) { slots.associateBy { it.slot } }
    val maxTime = remember(slots) { slots.maxOfOrNull { it.timeListened } ?: 1L }
    val peakSlot = remember(slots) { slots.maxByOrNull { it.timeListened }?.slot }
    val primaryColor = MaterialTheme.colorScheme.primary
    val containerColor = MaterialTheme.colorScheme.primaryContainer

    val peakLabel =
        remember(peakSlot) {
            val formatter = DateTimeFormatter.ofPattern("ha")
            peakSlot?.let { LocalTime.of(it, 0).format(formatter) }
        }
    val timeLabels =
        remember {
            val formatter = DateTimeFormatter.ofPattern("ha")
            listOf(0, 6, 12, 18, 0).map { hour ->
                LocalTime.of(hour, 0).format(formatter)
            }
        }

    StatsGlassCard(modifier = modifier, cornerRadius = 28.dp) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.stats_listening_by_hour),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                )
                if (peakLabel != null) {
                    Text(
                        text = stringResource(R.string.stats_peak_hour, peakLabel),
                        style = MaterialTheme.typography.labelSmall,
                        color = primaryColor,
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (slots.isEmpty()) {
                StatsEmptyText()
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    for (hour in 0..23) {
                        val time = slotMap[hour]?.timeListened ?: 0L
                        val fraction = time.toFloat() / maxTime
                        val isPeak = hour == peakSlot
                        val barColor = if (isPeak) primaryColor else containerColor.copy(alpha = 0.6f + fraction * 0.4f)
                        val animatedFraction by animateFloatAsState(
                            targetValue = fraction,
                            animationSpec = tween(400),
                            label = "hour_$hour",
                        )
                        Box(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .height(48.dp),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height((48 * animatedFraction).dp.coerceAtLeast(2.dp))
                                        .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                                        .background(barColor),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    timeLabels.forEach { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

enum class OptionStats { WEEKS, MONTHS, YEARS, CONTINUOUS }
