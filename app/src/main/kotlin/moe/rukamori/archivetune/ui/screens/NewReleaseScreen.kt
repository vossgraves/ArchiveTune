/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.CoroutineScope
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.GridThumbnailHeight
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.ui.component.IconButton as AppIconButton
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.YouTubeGridItem
import moe.rukamori.archivetune.ui.component.shimmer.GridItemPlaceHolder
import moe.rukamori.archivetune.ui.component.shimmer.ShimmerHost
import moe.rukamori.archivetune.ui.menu.YouTubeAlbumMenu
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.viewmodels.NewReleaseContent
import moe.rukamori.archivetune.viewmodels.NewReleaseUiState
import moe.rukamori.archivetune.viewmodels.NewReleaseViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NewReleaseScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: NewReleaseViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by rememberSaveable { mutableStateOf(NewReleaseTab.All) }
    // Local search state — filters releases by album/artist name. The search
    // icon in the top app bar toggles a search field; typing filters the
    // visible grid in-place. Empty query = show all releases.
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            // Plain top bar — no frosted pills. Modern, minimal.
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.new_releases),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    AppIconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                },
                actions = {
                    // Using Material3's standard IconButton here (not the
                    // custom AppIconButton) because the custom one uses
                    // combinedClickable which can fail to register taps in
                    // the LargeFlexibleTopAppBar actions slot on some
                    // Material3 1.5.0-alpha builds. The standard IconButton
                    // uses a plain clickable and is more reliable here.
                    IconButton(
                        onClick = {
                            isSearchActive = !isSearchActive
                            if (!isSearchActive) searchQuery = ""
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.search),
                            contentDescription = stringResource(R.string.search),
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = LocalPlayerAwareWindowInsets.current,
    ) { paddingValues ->
        AnimatedContent(
            targetState = uiState,
            transitionSpec = {
                fadeIn(tween(300)) togetherWith fadeOut(tween(150))
            },
            modifier = Modifier.fillMaxSize(),
            label = "NewReleaseContent",
        ) { state ->
            when (state) {
                NewReleaseUiState.Loading -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = GridThumbnailHeight + 24.dp),
                        contentPadding = paddingValues,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(12) {
                            ShimmerHost {
                                GridItemPlaceHolder(fillMaxWidth = true)
                            }
                        }
                    }
                }

                is NewReleaseUiState.Success -> {
                    NewReleaseGridContent(
                        content = state.content,
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it },
                        paddingValues = paddingValues,
                        activeAlbumId = mediaMetadata?.album?.id,
                        isPlaying = isPlaying,
                        coroutineScope = coroutineScope,
                        searchQuery = searchQuery,
                        isSearchActive = isSearchActive,
                        onSearchQueryChange = { searchQuery = it },
                        onClearSearch = {
                            searchQuery = ""
                            isSearchActive = false
                        },
                        onReleaseClick = { album -> navController.navigate("album/${album.id}") },
                        onReleaseLongClick = { album ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuState.show {
                                YouTubeAlbumMenu(
                                    albumItem = album,
                                    navController = navController,
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                        onRefresh = viewModel::retry,
                    )
                }

                NewReleaseUiState.Error -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(paddingValues)
                                .padding(horizontal = 24.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                painter = painterResource(R.drawable.error),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(48.dp),
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "New releases are temporarily unavailable",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "ArchiveTune could not load this YouTube Music section. Try again later.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(24.dp))
                            Button(
                                onClick = viewModel::retry,
                                shapes = ButtonDefaults.shapes(),
                            ) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                }

                NewReleaseUiState.Empty -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(paddingValues)
                                .padding(horizontal = 24.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.no_results_found),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(24.dp))
                            Button(
                                onClick = viewModel::retry,
                                shapes = ButtonDefaults.shapes(),
                            ) {
                                Text(stringResource(R.string.refresh))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Immutable
private enum class NewReleaseTab(
    @StringRes val titleRes: Int,
    val icon: ImageVector,
    val contentType: String,
) {
    All(
        titleRes = R.string.filter_all,
        icon = Icons.Outlined.LibraryMusic,
        contentType = "new_release_all_grid_item",
    ),
    Albums(
        titleRes = R.string.albums,
        icon = Icons.Outlined.Album,
        contentType = "new_release_album_grid_item",
    ),
    Singles(
        titleRes = R.string.singles,
        icon = Icons.Outlined.MusicNote,
        contentType = "new_release_single_grid_item",
    ),
    Ep(
        titleRes = R.string.ep,
        icon = Icons.Outlined.QueueMusic,
        contentType = "new_release_ep_grid_item",
    ),
}

@Immutable
private data class NewReleaseSection(
    val tab: NewReleaseTab,
    val releases: List<AlbumItem>,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NewReleaseGridContent(
    content: NewReleaseContent,
    selectedTab: NewReleaseTab,
    onTabSelected: (NewReleaseTab) -> Unit,
    paddingValues: PaddingValues,
    activeAlbumId: String?,
    isPlaying: Boolean,
    coroutineScope: CoroutineScope,
    searchQuery: String,
    isSearchActive: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onReleaseClick: (AlbumItem) -> Unit,
    onReleaseLongClick: (AlbumItem) -> Unit,
    onRefresh: () -> Unit,
) {
    val allSections =
        remember(content) {
            content.releaseSections()
        }
    val releases =
        remember(content, selectedTab) {
            if (selectedTab == NewReleaseTab.All) emptyList() else content.releasesFor(selectedTab)
        }

    // Apply search filter to releases — matches album title OR artist name,
    // case-insensitive. Empty query = no filtering.
    val query = searchQuery.trim()
    fun matchesQuery(album: AlbumItem): Boolean {
        if (query.isEmpty()) return true
        val title = album.title.lowercase()
        val artists = album.artists?.joinToString(" ") { it.name }?.lowercase().orEmpty()
        val q = query.lowercase()
        return title.contains(q) || artists.contains(q)
    }

    val filteredReleases = remember(releases, query) { releases.filter(::matchesQuery) }
    val filteredAllSections = remember(allSections, query) {
        if (query.isEmpty()) allSections
        else allSections.map { it.copy(releases = it.releases.filter(::matchesQuery)) }.filter { it.releases.isNotEmpty() }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = GridThumbnailHeight + 24.dp),
        contentPadding = paddingValues,
        modifier = Modifier.fillMaxSize(),
    ) {
        // Search field — only shown when the user has tapped the search icon.
        // Renders as a full-width row above the summary header.
        if (isSearchActive) {
            item(
                key = "new_release_search_field",
                span = { GridItemSpan(maxLineSpan) },
                contentType = "new_release_search_field",
            ) {
                NewReleaseSearchField(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    onClose = onClearSearch,
                )
            }
        }

        item(
            key = "new_release_summary",
            span = { GridItemSpan(maxLineSpan) },
            contentType = "new_release_summary",
        ) {
            NewReleaseSummaryHeader(
                content = content,
                selectedTab = selectedTab,
                onTabSelected = onTabSelected,
            )
        }

        if (query.isNotEmpty() && filteredAllSections.isEmpty() && filteredReleases.isEmpty()) {
            item(
                key = "new_release_search_empty",
                span = { GridItemSpan(maxLineSpan) },
                contentType = "new_release_empty",
            ) {
                NewReleaseCategoryEmptyState(onRefresh = onRefresh)
            }
        } else if (selectedTab == NewReleaseTab.All) {
            filteredAllSections.forEach { section ->
                item(
                    key = "new_release_section_header_${section.tab.name}",
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "new_release_section_header",
                ) {
                    NewReleaseSectionHeader(
                        title = stringResource(section.tab.titleRes),
                        count = section.releases.size,
                        leadingIcon = section.tab.icon,
                    )
                }

                item(
                    key = "new_release_section_${section.tab.name}",
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "new_release_horizontal_section",
                ) {
                    NewReleaseHorizontalSection(
                        releases = section.releases,
                        contentType = section.tab.contentType,
                        activeAlbumId = activeAlbumId,
                        isPlaying = isPlaying,
                        coroutineScope = coroutineScope,
                        onReleaseClick = onReleaseClick,
                        onReleaseLongClick = onReleaseLongClick,
                    )
                }
            }
        } else if (filteredReleases.isEmpty()) {
            item(
                key = "new_release_empty_${selectedTab.name}",
                span = { GridItemSpan(maxLineSpan) },
                contentType = "new_release_empty",
            ) {
                NewReleaseCategoryEmptyState(onRefresh = onRefresh)
            }
        } else {
            items(
                items = filteredReleases,
                key = { it.id },
                contentType = { selectedTab.contentType },
            ) { album ->
                YouTubeGridItem(
                    item = album,
                    isActive = activeAlbumId == album.id,
                    isPlaying = isPlaying,
                    fillMaxWidth = true,
                    coroutineScope = coroutineScope,
                    modifier =
                        Modifier
                            .animateItem()
                            .combinedClickable(
                                onClick = { onReleaseClick(album) },
                                onLongClick = { onReleaseLongClick(album) },
                            ),
                )
            }
        }
    }
}

@Composable
private fun NewReleaseSectionHeader(
    title: String,
    count: Int,
    leadingIcon: ImageVector? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Leading icon in a circular container — matches the Home page's
            // HomeSectionLeadingIcon pattern (e.g. clock for Recently Played,
            // bolt for Speed Dial) so every section header across the app has
            // a recognisable affordance before its title.
            if (leadingIcon != null) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(8.dp))
            // Compact count chip — small rounded background with the count.
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NewReleaseHorizontalSection(
    releases: List<AlbumItem>,
    contentType: String,
    activeAlbumId: String?,
    isPlaying: Boolean,
    coroutineScope: CoroutineScope,
    onReleaseClick: (AlbumItem) -> Unit,
    onReleaseLongClick: (AlbumItem) -> Unit,
) {
    LazyHorizontalGrid(
        rows = GridCells.Fixed(1),
        contentPadding = PaddingValues(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .height(216.dp),
    ) {
        items(
            items = releases,
            key = { it.id },
            contentType = { contentType },
        ) { album ->
            YouTubeGridItem(
                item = album,
                isActive = activeAlbumId == album.id,
                isPlaying = isPlaying,
                fillMaxWidth = false,
                coroutineScope = coroutineScope,
                modifier =
                    Modifier
                        .animateItem()
                        .combinedClickable(
                            onClick = { onReleaseClick(album) },
                            onLongClick = { onReleaseLongClick(album) },
                        ),
            )
        }
    }
}

/**
 * Modern summary header — replaces the old frosted-glass summary card.
 *
 * Layout:
 *  - Top row: "Total releases" label + count number grouped together on the
 *    left (so the number sits beside the label, not floating at the right
 *    edge — user-requested fix), with a search affordance icon on the right
 *  - Bottom: tab strip as a horizontally-scrollable row of clean tonal chips
 *    (scrollable so 4 tabs never truncate "Albums" → "Albu" on narrow screens)
 *
 * No frosted glass, no oversized rounded container — just typography +
 * a clean tab strip.
 */
@Composable
private fun NewReleaseSummaryHeader(
    content: NewReleaseContent,
    selectedTab: NewReleaseTab,
    onTabSelected: (NewReleaseTab) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 8.dp),
    ) {
        // Total releases — label and count grouped together on the LEFT so
        // the count number reads as part of the label (e.g. "Total releases 200")
        // rather than floating alone at the right edge of the screen. The
        // search affordance icon is rendered by the top app bar instead.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Small leading icon — matches the section header pattern so the
            // summary header has the same visual language as the per-section
            // headers below it.
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.LibraryMusic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = stringResource(R.string.total_releases),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Count number — bold and prominent, immediately after the label.
            Text(
                text = content.totalReleases.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Spacer(Modifier.height(16.dp))

        // Modern tab strip — clean chips with no frosted pill background.
        // Horizontally scrollable so all 4 tab labels ("All", "Albums",
        // "Singles", "EP") are fully visible regardless of screen width.
        NewReleaseTabs(
            selectedTab = selectedTab,
            onTabSelected = onTabSelected,
        )
    }
}

@Composable
private fun NewReleaseTabs(
    selectedTab: NewReleaseTab,
    onTabSelected: (NewReleaseTab) -> Unit,
) {
    val tabs = remember { NewReleaseTab.entries.toList() }
    val selectedContainer = MaterialTheme.colorScheme.primary
    val selectedContentColor = MaterialTheme.colorScheme.onPrimary
    val unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    val scrollState = rememberScrollState()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
    ) {
        tabs.forEach { tab ->
            val selected = tab == selectedTab
            val title = stringResource(tab.titleRes)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .wrapContentWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) selectedContainer else Color.Transparent)
                    .combinedClickable(onClick = { onTabSelected(tab) })
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = title,
                    modifier = Modifier.size(18.dp),
                    tint = if (selected) selectedContentColor else unselectedContentColor,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (selected) selectedContentColor else unselectedContentColor,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun NewReleaseCategoryEmptyState(onRefresh: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 56.dp),
    ) {
        Text(
            text = stringResource(R.string.no_releases_found),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        FilledTonalButton(
            onClick = onRefresh,
            shape = RoundedCornerShape(20.dp),
        ) {
            Text(stringResource(R.string.refresh))
        }
    }
}

private fun NewReleaseContent.releasesFor(tab: NewReleaseTab): List<AlbumItem> =
    when (tab) {
        NewReleaseTab.All -> emptyList()
        NewReleaseTab.Albums -> albums
        NewReleaseTab.Singles -> singles
        NewReleaseTab.Ep -> eps
    }

private fun NewReleaseContent.releaseSections(): List<NewReleaseSection> =
    buildList {
        if (albums.isNotEmpty()) {
            add(NewReleaseSection(NewReleaseTab.Albums, albums))
        }
        if (singles.isNotEmpty()) {
            add(NewReleaseSection(NewReleaseTab.Singles, singles))
        }
        if (eps.isNotEmpty()) {
            add(NewReleaseSection(NewReleaseTab.Ep, eps))
        }
    }

/**
 * Inline search field for the New Releases screen — rendered as a full-width
 * row above the summary header when the user taps the search icon in the top
 * app bar. Filters releases by album title / artist name in real time.
 *
 * Layout mirrors the SearchScreen's input row (search icon + text field +
 * clear button) but is rendered inside the grid content rather than as a
 * top app bar, so it appears in-line with the rest of the page content.
 *
 * Auto-focuses the text field on appearance so the keyboard opens
 * immediately — the user doesn't have to tap the field after tapping the
 * search icon in the top app bar.
 */
@Composable
private fun NewReleaseSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    val focusRequester = remember { FocusRequester() }

    // Auto-focus the text field when this composable first appears so the
    // keyboard opens immediately. This makes the search feel responsive —
    // tap search icon → field appears → keyboard is already up → start typing.
    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 4.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        // Use Material3's standard IconButton (not the custom AppIconButton)
        // for consistency with the top app bar search icon — and because
        // these buttons don't need long-press support.
        IconButton(onClick = onClose) {
            Icon(
                painter = painterResource(R.drawable.arrow_back),
                contentDescription = null,
                tint = onSurfaceVariant,
            )
        }
        androidx.compose.foundation.text.BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle =
                MaterialTheme.typography.bodyLarge.copy(color = onSurface),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(primary),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            decorationBox = { innerTextField ->
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.search),
                            style = MaterialTheme.typography.bodyLarge,
                            color = onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    innerTextField()
                }
            },
        )
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }) {
                Icon(
                    painter = painterResource(R.drawable.close),
                    contentDescription = null,
                    tint = onSurfaceVariant,
                )
            }
        }
    }
}
