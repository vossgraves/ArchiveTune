/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import moe.rukamori.archivetune.playback.queues.Queue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import kotlinx.coroutines.CoroutineScope
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.QuickPicks
import moe.rukamori.archivetune.home.HomeAction
import moe.rukamori.archivetune.home.HomeScreenState
import moe.rukamori.archivetune.home.HomeUiState
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.viewmodels.HomeViewModel
import dev.chrisbanes.haze.hazeSource

private val HomeFeedMaxWidth = 1_200.dp

/** Gap between the end of one shelf and the header of the next (BitChord: 26dp). */
private val HomeSectionSpacing = 26.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    navController: NavController,
    headerScrollConnection: NestedScrollConnection? = null,
    listState: LazyListState? = null,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val playerConnection = LocalPlayerConnection.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current

    val screenState by viewModel.screenState.collectAsStateWithLifecycle()
    val isPlaying = playerConnection?.isPlaying?.collectAsStateWithLifecycle()?.value ?: false
    val mediaMetadata = playerConnection?.mediaMetadata?.collectAsStateWithLifecycle()?.value
    var pendingQueue by remember { mutableStateOf<Queue?>(null) }
    val onPlayQueue: (Queue) -> Unit = { queue ->
        if (playerConnection == null) pendingQueue = queue else playerConnection.playQueue(queue)
    }
    LaunchedEffect(playerConnection, pendingQueue) {
        val connection = playerConnection ?: return@LaunchedEffect
        val queue = pendingQueue ?: return@LaunchedEffect
        pendingQueue = null
        connection.playQueue(queue)
    }
    androidx.activity.compose.ReportDrawnWhen { screenState !is HomeScreenState.Loading }

    val lazyListState = listState ?: rememberLazyListState()
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop =
        backStackEntry
            ?.savedStateHandle
            ?.getStateFlow("scrollToTop", false)
            ?.collectAsStateWithLifecycle()

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            lazyListState.animateScrollToItem(0)
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    val successState = screenState as? HomeScreenState.Success
    val uiState = successState?.uiState
    val selectedChip = uiState?.selectedChip

    LaunchedEffect(uiState?.homePage?.continuation) {
        val continuation = uiState?.homePage?.continuation ?: return@LaunchedEffect
        snapshotFlow {
            val layoutInfo = lazyListState.layoutInfo
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index
            lastVisibleIndex != null && lastVisibleIndex >= layoutInfo.totalItemsCount - 3
        }.collect { shouldLoadMore ->
            if (shouldLoadMore) {
                viewModel.onAction(HomeAction.LoadMore(continuation))
            }
        }
    }

    if (selectedChip != null) {
        BackHandler {
            viewModel.onAction(HomeAction.SelectChip(selectedChip))
        }
    }

    LaunchedEffect(uiState?.showCategoryChips, selectedChip) {
        if (uiState?.showCategoryChips == false && selectedChip != null) {
            viewModel.onAction(HomeAction.SelectChip(selectedChip))
        }
    }

    // Attach the shell's floating-header connection inside this screen (Step 2b) so
    // Home's scroll/fling writes Home's own header state and can't leak into another
    // route's header. Bubbling reaches this ancestor Box before any shell connection.
    //
    // The same Box is the haze source for the BitChord-style progressive top-fade
    // blur the shell renders over the Home route (see LocalHomeHazeState): it covers
    // the full window area including the strip under the pinned top bar, which is
    // exactly what the blur samples.
    val homeHazeState = LocalHomeHazeState.current
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .let { m -> if (homeHazeState != null) m.hazeSource(homeHazeState) else m }
                .then(
                    if (headerScrollConnection != null) {
                        Modifier.nestedScroll(headerScrollConnection)
                    } else {
                        Modifier
                    },
                ),
    ) {
        when (val state = screenState) {
            HomeScreenState.Loading -> {
                // BitChord behaviour (2026-09-03 redesign): the first page of shelves
                // is stood in for by shimmer skeletons laid out to the real metrics,
                // rather than a centered spinner that throws the layout away and
                // snaps everything down when the data lands.
                HomeSkeletonFeed()
            }

            HomeScreenState.Empty -> {
                HomeStatePane(
                    iconResId = R.drawable.music_note,
                    messageResId = R.string.no_results_found,
                    actionResId = R.string.retry,
                    onAction = { viewModel.onAction(HomeAction.Refresh) },
                )
            }

            is HomeScreenState.Error -> {
                HomeStatePane(
                    iconResId = R.drawable.info,
                    messageResId = state.messageResId,
                    actionResId = R.string.retry,
                    onAction = { viewModel.onAction(HomeAction.Refresh) },
                )
            }

            is HomeScreenState.Success -> {
                HomeContent(
                    uiState = state.uiState,
                    mediaMetadata = mediaMetadata,
                    isPlaying = isPlaying,
                    navController = navController,
                    playerConnection = playerConnection,
                    onPlayQueue = onPlayQueue,
                    menuState = menuState,
                    haptic = haptic,
                    scope = scope,
                    lazyListState = lazyListState,
                    onAction = viewModel::onAction,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HomeStatePane(
    @DrawableRes iconResId: Int?,
    @StringRes messageResId: Int?,
    modifier: Modifier = Modifier,
    @StringRes actionResId: Int? = null,
    showLoadingIndicator: Boolean = false,
    onAction: (() -> Unit)? = null,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .fillMaxSize()
                .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues()),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            if (showLoadingIndicator) {
                LoadingIndicator()
            } else {
                iconResId?.let {
                    Icon(
                        painter = painterResource(it),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp),
                    )
                }
                messageResId?.let {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(it),
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (actionResId != null && onAction != null) {
                    Spacer(Modifier.height(20.dp))
                    FilledTonalButton(onClick = onAction) {
                        Text(stringResource(actionResId))
                    }
                }
            }
        }
    }
}

@OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalMaterial3Api::class,
)
@Composable
private fun HomeContent(
    uiState: HomeUiState,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection?,
    onPlayQueue: (Queue) -> Unit,
    menuState: MenuState,
    haptic: HapticFeedback,
    scope: CoroutineScope,
    lazyListState: androidx.compose.foundation.lazy.LazyListState,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val remoteQuickPicks =
        uiState
            .takeIf { it.quickPicksMode == QuickPicks.QUICK_PICKS }
            ?.remoteQuickPicks
    Box(modifier = modifier.fillMaxSize()) {
        // ── Tonal backdrop gradient (fade effect) removed ───────────────────
        // Per user request (2026-08-28): "Remove the home liquid glass buttons
        // and fade effect". The 430dp verticalGradient that lived here (fed by
        // `uiState.showTonalBackdrop`) is the "fade effect" being removed —
        // the home page now sits on a flat surface colour so the hero cards
        // and section headers are the only visual rhythm at the top of the
        // screen, matching the rest of the redesigned pages.
        //
        // ── BitChord pull-to-refresh (2026-09-03 redesign) ──────────────────
        // The usual circular puck is suppressed; the drag feedback is the
        // loader line along the bottom edge of the top bar instead (rendered
        // as an overlay below, at the bar's bottom edge = the content's top
        // inset). It fills left-to-right as the pull approaches the threshold,
        // then sweeps indefinitely once the refresh is away.
        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { onAction(HomeAction.Refresh) },
            state = pullState,
            indicator = {},
            modifier = Modifier.fillMaxSize(),
        ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {

                // Partition remote sections into Live-performance and other.
                // Hoisted outside the LazyColumn content lambda (which is NOT a
                // @Composable scope) so `remember` is valid here. Without this,
                // the two `.filter` calls would allocate fresh lists on every
                // recomposition of HomeContent even when the sections hadn't
                // changed — a measurable contributor to home-screen jank.
                val allRemoteSections = uiState.homePage?.sections.orEmpty()
                val (livePerformanceSections, otherRemoteSections) =
                    remember(allRemoteSections) {
                        val live = allRemoteSections.filter { section ->
                            section.title.contains("Live performance", ignoreCase = true)
                        }
                        val other = allRemoteSections.filter { section ->
                            !section.title.contains("Live performance", ignoreCase = true)
                        }
                        live to other
                    }

                LazyColumn(
                    state = lazyListState,
                    contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
                    modifier =
                        Modifier
                            .widthIn(max = HomeFeedMaxWidth)
                            .fillMaxWidth()
                            .align(Alignment.TopCenter),
                ) {
                    // ── Big in-list page title (BitChord displayLarge) ──────────
                    // The greeting owns the page title at rest; once the list
                    // scrolls, the shell's top-bar title fades in over the blur
                    // (BitChord's Apple Music behaviour — the two never fight
                    // because the bar's title only exists while scrolled).
                    item(
                        key = "home_greeting_title",
                        contentType = "greeting_title",
                    ) {
                        HomeGreetingHeader(
                            accountName = uiState.accountName,
                            modifier = Modifier.animateItem(),
                        )
                    }

                    // Home feed layout policy:
                    //
                    //  * "Jump back in" hero is ALWAYS rendered (when there are
                    //    hero picks) regardless of Minimal Mode, so the top of
                    //    the home screen always has the big artwork card.
                    //
                    //  * When `uiState.minimalHomeMode == true`, the feed
                    //    collapses to:
                    //      hero -> Recently Played -> Keep Listening
                    //      -> Speed Dial -> Live Performances
                    //    All other shelves (category chips, remote/local quick
                    //    picks, account playlists, forgotten favorites, similar
                    //    recommendations, and non-Live remote sections) are
                    //    hidden. Speed Dial is preserved in minimal mode (placed
                    //    directly below Keep Listening) so the user keeps one-tap
                    //    access to their pinned items.
                    //
                    //  * When `uiState.minimalHomeMode == false` (default, also
                    //    matches upstream rukamori/ArchiveTune), the feed shows
                    //    the full set:
                    //      hero -> category chips -> remote/local quick picks
                    //      -> Recently Played -> Speed Dial -> Keep Listening
                    //      -> Account Playlists -> Forgotten Favourites
                    //      -> Similar Recommendations -> ALL remote homePage
                    //      sections (including but not limited to "Live
                    //      performance").
                    //
                    //  * "Live performance" shelves are extracted from the
                    //    remote homePage sections and rendered as a dedicated
                    //    block IMMEDIATELY after Speed Dial in BOTH modes — so
                    //    Live Performances always stays below Speed Dial whether
                    //    Minimal Mode is on or off. Non-Live remote shelves
                    //    continue to render at the bottom in full mode only.
                    //
                    // The hero and Recently Played sections are 4nx3b fork
                    // additions; everything else mirrors upstream so a fresh
                    // install (with only remote content available) sees a
                    // populated home screen.

                    val minimalMode = uiState.minimalHomeMode

                    // "Jump back in" hero — large card + 2 stacked side cards.
                    // Uses `heroPicks` (3 random songs from listening-preference
                    // based quickPicks) instead of the last-played 3, so the hero
                    // rotates fresh picks each visit. Mirrors the Apple Music /
                    // Only renders once there is a Spotify session to switch to; see
                    // HomeSourceSwitcher. Above the hero so the two homes agree on where it lives.
                    item(
                        key = "home_source_switcher",
                        contentType = "source_switcher",
                    ) {
                        HomeSourceSwitcher(modifier = Modifier.animateItem())
                    }

                    // Muzo home hero. Skipped entirely if the user has no
                    // listening history yet (e.g. fresh install). PERSISTENT —
                    // renders in both full and minimal modes.
                    if (uiState.heroPicks.isNotEmpty()) {
                        item(
                            key = "home_jump_back_in",
                            contentType = "jump_back_in",
                        ) {
                            JumpBackInHeroSection(
                                recentlyPlayed = uiState.heroPicks,
                                mediaMetadata = mediaMetadata,
                                isPlaying = isPlaying,
                                navController = navController,
                                playerConnection = playerConnection,
                                onPlayQueue = onPlayQueue,
                                menuState = menuState,
                                haptic = haptic,
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }

                    if (!minimalMode && uiState.showCategoryChips) {
                        item(
                            key = "home_category_chips",
                            contentType = "category_chips",
                        ) {
                            HomeCategoryChips(
                                chips = uiState.homePage?.chips.orEmpty(),
                                selectedChip = uiState.selectedChip,
                                onChipSelected = { onAction(HomeAction.SelectChip(it)) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }

                    if (!minimalMode) {
                        if (remoteQuickPicks?.items?.isNotEmpty() == true) {
                            sectionSpacer("remote_quick_picks")
                            item(
                                key = "home_remote_quick_picks_header",
                                contentType = "section_header",
                            ) {
                                HomeSectionHeader(
                                    title = remoteQuickPicks.title,
                                    leadingIcon = {
                                        HomeSectionLeadingIcon(iconRes = R.drawable.discover_tune)
                                    },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                            item(
                                key = "home_remote_quick_picks",
                                contentType = "media_shelf",
                            ) {
                                HomePageSectionContent(
                                    section = remoteQuickPicks,
                                    mediaMetadata = mediaMetadata,
                                    isPlaying = isPlaying,
                                    navController = navController,
                                    playerConnection = playerConnection,
                                    onPlayQueue = onPlayQueue,
                                    menuState = menuState,
                                    haptic = haptic,
                                    scope = scope,
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                        // Note: the local "Quick Picks" shelf (driven by
                        // `uiState.quickPicks` and rendered by upstream's
                        // `QuickPicksSection` composable) was intentionally
                        // removed from this fork in commit 9bf3c6bd2 in favour
                        // of the "Jump back in" hero (which is built from the
                        // same listening-preference-based picks) plus the
                        // remote YouTube Music "Quick picks" shelf above.
                        // Restoring upstream's `QuickPicksSection` would
                        // require porting back the composable and its
                        // carousel dependencies — out of scope for this fix.
                    }

                    // "Recently Played" — horizontal square-card row with a
                    // clock-icon header. Renders in both full and minimal modes.
                    if (uiState.recentlyPlayed.size > 1) {
                        sectionSpacer("recently_played")
                        item(
                            key = "home_recently_played_header",
                            contentType = "section_header",
                        ) {
                            HomeSectionHeader(
                                title = stringResource(R.string.home_recently_played),
                                leadingIcon = {
                                    HomeSectionLeadingIcon(iconRes = R.drawable.history)
                                },
                                modifier = Modifier.animateItem(),
                            )
                        }
                        item(
                            key = "home_recently_played",
                            contentType = "recently_played",
                        ) {
                            RecentlyPlayedSection(
                                recentlyPlayed = uiState.recentlyPlayed,
                                mediaMetadata = mediaMetadata,
                                isPlaying = isPlaying,
                                navController = navController,
                                playerConnection = playerConnection,
                                onPlayQueue = onPlayQueue,
                                menuState = menuState,
                                haptic = haptic,
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }

                    // In FULL mode, Speed Dial sits above Keep Listening (matches
                    // upstream rukamori/ArchiveTune order). In MINIMAL mode, it is
                    // relocated to sit directly below Keep Listening — the user
                    // explicitly requested Speed Dial stay visible in minimal mode,
                    // placed right under Keep Listening so they keep one-tap access
                    // to their pinned items without re-enabling the full feed.
                    if (!minimalMode && uiState.speedDialItems.isNotEmpty()) {
                        sectionSpacer("speed_dial")
                        item(
                            key = "home_speed_dial_header",
                            contentType = "section_header",
                        ) {
                            HomeSectionHeader(
                                title = stringResource(R.string.speed_dial),
                                leadingIcon = {
                                    HomeSectionLeadingIcon(iconRes = R.drawable.bolt)
                                },
                                modifier = Modifier.animateItem(),
                            )
                        }
                        item(
                            key = "home_speed_dial",
                            contentType = "speed_dial",
                        ) {
                            SpeedDialSection(
                                speedDialItems = uiState.speedDialItems,
                                mediaMetadata = mediaMetadata,
                                isPlaying = isPlaying,
                                navController = navController,
                                playerConnection = playerConnection,
                                onPlayQueue = onPlayQueue,
                                menuState = menuState,
                                haptic = haptic,
                                scope = scope,
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }

                    // Keep Listening — renders in both full and minimal modes.
                    if (uiState.keepListening.isNotEmpty()) {
                        sectionSpacer("keep_listening")
                        item(
                            key = "home_keep_listening_header",
                            contentType = "section_header",
                        ) {
                            HomeSectionHeader(
                                title = stringResource(R.string.keep_listening),
                                leadingIcon = {
                                    HomeSectionLeadingIcon(iconRes = R.drawable.listening)
                                },
                                modifier = Modifier.animateItem(),
                            )
                        }
                        item(
                            key = "home_keep_listening",
                            contentType = "media_shelf",
                        ) {
                            KeepListeningSection(
                                keepListening = uiState.keepListening,
                                mediaMetadata = mediaMetadata,
                                isPlaying = isPlaying,
                                navController = navController,
                                playerConnection = playerConnection,
                                onPlayQueue = onPlayQueue,
                                menuState = menuState,
                                haptic = haptic,
                                scope = scope,
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }

                    // MINIMAL-mode-only Speed Dial placement: directly below
                    // Keep Listening. Uses distinct item keys (`_minimal`
                    // suffix) so LazyColumn doesn't try to reuse the full-mode
                    // Speed Dial items when the toggle flips.
                    if (minimalMode && uiState.speedDialItems.isNotEmpty()) {
                        sectionSpacer("speed_dial_minimal")
                        item(
                            key = "home_speed_dial_header_minimal",
                            contentType = "section_header",
                        ) {
                            HomeSectionHeader(
                                title = stringResource(R.string.speed_dial),
                                leadingIcon = {
                                    HomeSectionLeadingIcon(iconRes = R.drawable.bolt)
                                },
                                modifier = Modifier.animateItem(),
                            )
                        }
                        item(
                            key = "home_speed_dial_minimal",
                            contentType = "speed_dial",
                        ) {
                            SpeedDialSection(
                                speedDialItems = uiState.speedDialItems,
                                mediaMetadata = mediaMetadata,
                                isPlaying = isPlaying,
                                navController = navController,
                                playerConnection = playerConnection,
                                onPlayQueue = onPlayQueue,
                                menuState = menuState,
                                haptic = haptic,
                                scope = scope,
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }

                    // Live Performances — extracted from the remote homePage
                    // sections and rendered as a dedicated block IMMEDIATELY
                    // after Speed Dial in BOTH modes. This guarantees Live
                    // Performances always stays below Speed Dial whether
                    // Minimal Mode is on or off (user-requested invariant).
                    livePerformanceSections.forEachIndexed { index, section ->
                        val sectionKey = "${section.endpoint?.browseId ?: section.title}_$index"
                        sectionSpacer("live_performances_$sectionKey")
                        item(
                            key = "home_live_performances_header_$sectionKey",
                            contentType = "section_header",
                        ) {
                            HomePageSectionTitle(
                                section = section,
                                navController = navController,
                                modifier = Modifier.animateItem(),
                            )
                        }
                        item(
                            key = "home_live_performances_$sectionKey",
                            contentType = "media_shelf",
                        ) {
                            HomePageSectionContent(
                                section = section,
                                mediaMetadata = mediaMetadata,
                                isPlaying = isPlaying,
                                navController = navController,
                                playerConnection = playerConnection,
                                onPlayQueue = onPlayQueue,
                                menuState = menuState,
                                haptic = haptic,
                                scope = scope,
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }

                    if (!minimalMode && uiState.accountPlaylists.isNotEmpty()) {
                        sectionSpacer("account_playlists")
                        item(
                            key = "home_account_playlists",
                            contentType = "media_shelf",
                        ) {
                            Column(modifier = Modifier.animateItem()) {
                                AccountPlaylistsTitle(
                                    accountName = uiState.accountName,
                                    accountImageUrl = uiState.accountImageUrl,
                                    onClick = { navController.navigate("account") },
                                )
                                AccountPlaylistsSection(
                                    accountPlaylists = uiState.accountPlaylists,
                                    mediaMetadata = mediaMetadata,
                                    isPlaying = isPlaying,
                                    navController = navController,
                                    menuState = menuState,
                                    haptic = haptic,
                                    scope = scope,
                                )
                            }
                        }
                    }

                    if (!minimalMode && uiState.forgottenFavorites.isNotEmpty()) {
                        sectionSpacer("forgotten_favorites")
                        item(
                            key = "home_forgotten_favorites_header",
                            contentType = "section_header",
                        ) {
                            HomeSectionHeader(
                                title = stringResource(R.string.forgotten_favorites),
                                leadingIcon = {
                                    HomeSectionLeadingIcon(iconRes = R.drawable.cached)
                                },
                                modifier = Modifier.animateItem(),
                            )
                        }
                        item(
                            key = "home_forgotten_favorites",
                            contentType = "song_shelf",
                        ) {
                            ForgottenFavoritesSection(
                                forgottenFavorites = uiState.forgottenFavorites,
                                mediaMetadata = mediaMetadata,
                                isPlaying = isPlaying,
                                navController = navController,
                                playerConnection = playerConnection,
                                onPlayQueue = onPlayQueue,
                                menuState = menuState,
                                haptic = haptic,
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }

                    if (!minimalMode) {
                        uiState.similarRecommendations.forEach { recommendation ->
                            sectionSpacer("similar_${recommendation.title.id}")
                            item(
                                key = "home_similar_header_${recommendation.title.id}",
                                contentType = "section_header",
                            ) {
                                SimilarRecommendationsTitle(
                                    recommendation = recommendation,
                                    navController = navController,
                                    modifier = Modifier.animateItem(),
                                )
                            }
                            item(
                                key = "home_similar_${recommendation.title.id}",
                                contentType = "media_shelf",
                            ) {
                                SimilarRecommendationsSection(
                                    recommendation = recommendation,
                                    mediaMetadata = mediaMetadata,
                                    isPlaying = isPlaying,
                                    navController = navController,
                                    menuState = menuState,
                                    haptic = haptic,
                                    scope = scope,
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }

                    // Other Remote homePage sections (non-Live-performance).
                    //
                    //  * Minimal mode: HIDDEN — Live performances are already
                    //    rendered above (right after Speed Dial). All other
                    //    remote shelves are filtered out in minimal mode.
                    //
                    //  * Full mode: render ALL non-Live remote shelves (e.g.
                    //    "Fresh finds", "Old favourites", and any other
                    //    algorithmic shelves YouTube Music returns) — matches
                    //    upstream rukamori/ArchiveTune.
                    if (!minimalMode) {
                        otherRemoteSections.forEachIndexed { index, section ->
                            val sectionKey = "${section.endpoint?.browseId ?: section.title}_$index"
                            sectionSpacer("remote_$sectionKey")
                            item(
                                key = "home_remote_header_$sectionKey",
                                contentType = "section_header",
                            ) {
                                HomePageSectionTitle(
                                    section = section,
                                    navController = navController,
                                    modifier = Modifier.animateItem(),
                                )
                            }
                            item(
                                key = "home_remote_$sectionKey",
                                contentType = "media_shelf",
                            ) {
                                HomePageSectionContent(
                                    section = section,
                                    mediaMetadata = mediaMetadata,
                                    isPlaying = isPlaying,
                                    navController = navController,
                                    playerConnection = playerConnection,
                                    onPlayQueue = onPlayQueue,
                                    menuState = menuState,
                                    haptic = haptic,
                                    scope = scope,
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }

                    // BitChord load-more behaviour (2026-09-03): another page of
                    // shelves is stood in for by a single shelf-shaped skeleton at the
                    // tail rather than a spinner block.
                    if (uiState.isLoadingMore) {
                        homeFeedMoreSkeleton()
                    }
                }
        }
        }

        // The loader line at the bottom edge of the pinned top bar (BitChord
        // RefreshLine). The home content's top inset is exactly the bar's height,
        // so offsetting the line by that lands it on the bar's bottom edge.
        HomePullRefreshLine(
            refreshing = uiState.isRefreshing,
            distanceFraction = { pullState.distanceFraction },
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateTopPadding()),
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.sectionSpacer(key: String) {
    item(
        key = "home_section_spacer_$key",
        contentType = "section_spacer",
    ) {
        Spacer(Modifier.height(HomeSectionSpacing))
    }
}

/**
 * The home feed while the first page is still loading (BitChord behaviour,
 * 2026-09-03): the greeting is stood in for by a title-shaped block and the
 * shelves by skeleton cards laid out to the real metrics, so nothing jumps
 * when the data lands.
 */
@Composable
internal fun HomeSkeletonFeed(
    modifier: Modifier = Modifier,
    contentPadding: androidx.compose.foundation.layout.PaddingValues = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
) {
    LazyColumn(
        contentPadding = contentPadding,
        modifier =
            modifier
                .widthIn(max = HomeFeedMaxWidth)
                .fillMaxWidth(),
    ) {
        item(key = "home_skeleton_greeting") {
            HomeShimmerBox(
                modifier =
                    Modifier
                        .padding(horizontal = HomeFeedGutter)
                        .padding(vertical = 14.dp)
                        .fillMaxWidth(0.55f)
                        .height(34.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
            )
        }
        homeFeedSkeleton()
    }
}
