/*
 * ArchiveTune (2026)
 * © ArchiveTuneFork contributors — github.com/vossgraves/ArchiveTune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package app.atf.media.ui.screens.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import app.atf.media.LocalDatabase
import app.atf.media.LocalPlayerAwareWindowInsets
import app.atf.media.R
import app.atf.media.constants.ChipSortTypeKey
import app.atf.media.constants.DisableBlurKey
import app.atf.media.constants.LibraryFilter
import app.atf.media.constants.ShowSpotifyPlaylistsKey
import app.atf.media.constants.ShowTagsInLibraryKey
import app.atf.media.db.entities.TagEntity
import app.atf.media.ui.component.TagsManagementDialog
import app.atf.media.utils.rememberEnumPreference
import app.atf.media.utils.rememberPreference

internal val LibraryHeaderContentPadding = 64.dp
internal val LibraryPullToRefreshIndicatorOffset = 0.dp

@Composable
fun LibraryScreen(navController: NavController) {
    val defaultFilter by rememberEnumPreference(ChipSortTypeKey, LibraryFilter.LIBRARY)
    val database = LocalDatabase.current
    val (selectedTagIds, onSelectedTagIdsChange) = rememberPlaylistTagFilterState(database)
    val allTags by database.allTags().collectAsStateWithLifecycle(initialValue = emptyList())
    val (showTagsInLibrary) = rememberPreference(ShowTagsInLibraryKey, defaultValue = true)
    val (showSpotifyPlaylists) = rememberPreference(ShowSpotifyPlaylistsKey, defaultValue = false)
    val (disableBlur) = rememberPreference(DisableBlurKey, false)
    var showTagsManagementDialog by rememberSaveable { mutableStateOf(false) }
    val activeSelectedTagIds = if (showTagsInLibrary) selectedTagIds else emptySet()
    val libraryFilters =
        remember(showSpotifyPlaylists) {
            if (showSpotifyPlaylists) {
                listOf(
                    LibraryFilter.LIBRARY,
                    LibraryFilter.PLAYLISTS,
                    LibraryFilter.SPOTIFY,
                    LibraryFilter.SONGS,
                    LibraryFilter.ARTISTS,
                    LibraryFilter.ALBUMS,
                )
            } else {
                listOf(
                    LibraryFilter.LIBRARY,
                    LibraryFilter.PLAYLISTS,
                    LibraryFilter.SONGS,
                    LibraryFilter.ARTISTS,
                    LibraryFilter.ALBUMS,
                )
            }
        }

    if (showTagsManagementDialog) {
        TagsManagementDialog(
            onDismiss = { showTagsManagementDialog = false },
        )
    }

    val pagerState =
        rememberPagerState(
            initialPage = libraryFilters.indexOf(defaultFilter).takeIf { it >= 0 } ?: 0,
        ) { libraryFilters.size }

    val currentFilter = libraryFilters.getOrElse(pagerState.currentPage) { LibraryFilter.LIBRARY }

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val tonalStart = MaterialTheme.colorScheme.primaryContainer
    val tonalMiddle = MaterialTheme.colorScheme.secondaryContainer

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        if (!disableBlur) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(430.dp)
                        .align(Alignment.TopCenter)
                        .drawWithCache {
                            val brush =
                                Brush.verticalGradient(
                                    0f to tonalStart.copy(alpha = 0.30f),
                                    0.42f to tonalMiddle.copy(alpha = 0.14f),
                                    1f to Color.Transparent,
                                )
                            onDrawBehind { drawRect(brush) }
                        },
            )
        }

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    // Apply ONLY Top + Horizontal insets to the root Column so the LazyColumn
                    // inside extends to the very bottom of the screen and content visibly scrolls
                    // BEHIND the floating navigation bar / mini player. The bottom inset (nav bar
                    // height + mini player height + safe inset) is applied to each sub-screen's
                    // LazyColumn contentPadding instead, so the LAST items can be scrolled above
                    // the bar (minimum-height clearance) instead of being permanently hidden
                    // behind it. Per user spec: "scrollable behind navigation bar too (full
                    // screen width) and when I reach the bottom apply a minimum height so that
                    // it doesn't get overlapped by mini player and navigation bar".
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Top,
                        ),
                    ),
        ) {
            val tabListState = rememberLazyListState()
            val coroutineScope = rememberCoroutineScope()

            LaunchedEffect(defaultFilter, libraryFilters) {
                val selectedFilter = defaultFilter.takeIf { it in libraryFilters } ?: LibraryFilter.LIBRARY
                val selectedPage = libraryFilters.indexOf(selectedFilter).takeIf { it >= 0 } ?: 0
                if (pagerState.currentPage != selectedPage) {
                    pagerState.scrollToPage(selectedPage)
                }
            }

            // Sync Pager -> Preference & lazy list centering
            LaunchedEffect(pagerState.currentPage, libraryFilters) {
                val targetPage = pagerState.currentPage.coerceIn(0, libraryFilters.lastIndex)
                val targetFilter = libraryFilters.getOrElse(targetPage) { LibraryFilter.LIBRARY }

                // Centering the tab chip scroll alignment
                val tabWidth =
                    when (targetFilter) {
                        LibraryFilter.LIBRARY -> 116.dp
                        LibraryFilter.PLAYLISTS -> 132.dp
                        LibraryFilter.SPOTIFY -> 168.dp
                        LibraryFilter.SONGS -> 102.dp
                        LibraryFilter.ARTISTS -> 116.dp
                        LibraryFilter.ALBUMS -> 110.dp
                        else -> 116.dp
                    }
                val screenWidth = configuration.screenWidthDp.dp
                val targetOffsetDp = (screenWidth - tabWidth) / 2
                val targetOffsetPx = with(density) { targetOffsetDp.roundToPx() }

                tabListState.animateScrollToItem(targetPage, scrollOffset = -targetOffsetPx)
            }

            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                when (libraryFilters.getOrElse(page) { LibraryFilter.LIBRARY }) {
                    LibraryFilter.LIBRARY -> {
                        LibraryMixScreen(
                            navController = navController,
                            filterContent =
                                if (showTagsInLibrary) {
                                    {
                                        PlaylistTagFilterRow(
                                            tags = allTags,
                                            selectedTagIds = selectedTagIds,
                                            onSelectedTagIdsChange = onSelectedTagIdsChange,
                                            onManageTagsClick = { showTagsManagementDialog = true },
                                        )
                                    }
                                } else {
                                    null
                                },
                            selectedTagIds = activeSelectedTagIds,
                            onTabSelected = { targetFilter ->
                                coroutineScope.launch {
                                    val targetPage = libraryFilters.indexOf(targetFilter)
                                    pagerState.animateScrollToPage(targetPage.takeIf { it >= 0 } ?: 0)
                                }
                            },
                        )
                    }

                    LibraryFilter.PLAYLISTS -> {
                        LibraryPlaylistsScreen(
                            navController = navController,
                            filterContent =
                                if (showTagsInLibrary) {
                                    {
                                        PlaylistTagFilterRow(
                                            tags = allTags,
                                            selectedTagIds = selectedTagIds,
                                            onSelectedTagIdsChange = onSelectedTagIdsChange,
                                            onManageTagsClick = { showTagsManagementDialog = true },
                                        )
                                    }
                                } else {
                                    null
                                },
                            selectedTagIds = activeSelectedTagIds,
                        )
                    }

                    LibraryFilter.SPOTIFY -> {
                        LibrarySpotifyPlaylistsScreen(navController = navController)
                    }

                    LibraryFilter.SONGS -> {
                        LibrarySongsScreen(
                            navController = navController,
                            onDeselect = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(0)
                                }
                            },
                        )
                    }

                    LibraryFilter.ARTISTS -> {
                        LibraryArtistsScreen(
                            navController = navController,
                            onDeselect = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(0)
                                }
                            },
                        )
                    }

                    LibraryFilter.ALBUMS -> {
                        LibraryAlbumsScreen(
                            navController = navController,
                            onDeselect = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(0)
                                }
                            },
                        )
                    }
                }
                }

                // Tier 1: origin segmented control (My Library vs Spotify)
                var lastNonSpotify by rememberSaveable { mutableStateOf(LibraryFilter.LIBRARY) }
                LaunchedEffect(currentFilter) { if (currentFilter != LibraryFilter.SPOTIFY) lastNonSpotify = currentFilter }
                val isSpotifyOrigin = currentFilter == LibraryFilter.SPOTIFY
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ExpressiveTabChip(
                        label = stringResource(R.string.filter_library),
                        iconRes = R.drawable.graphic_eq,
                        selected = !isSpotifyOrigin,
                        onClick = {
                            coroutineScope.launch {
                                val target = libraryFilters.indexOf(lastNonSpotify).takeIf { it >= 0 } ?: 0
                                pagerState.animateScrollToPage(target)
                            }
                        },
                    )
                    if (LibraryFilter.SPOTIFY in libraryFilters) {
                        ExpressiveTabChip(
                            label = stringResource(R.string.spotify_playlists),
                            iconRes = R.drawable.spotify_icon,
                            selected = isSpotifyOrigin,
                            onClick = {
                                val target = libraryFilters.indexOf(LibraryFilter.SPOTIFY)
                                if (target >= 0) coroutineScope.launch { pagerState.animateScrollToPage(target) }
                            },
                        )
                    }
                }
                // Tier 2: content-type chips scoped to selected origin
                if (!isSpotifyOrigin) {
                    val contentFilters = libraryFilters.filter { it != LibraryFilter.SPOTIFY }
                    LazyRow(
                        state = tabListState,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        items(
                            items = contentFilters,
                            key = { filter -> filter.name },
                            contentType = { "library_filter_chip" },
                        ) { filter ->
                            val page = libraryFilters.indexOf(filter)
                            val label = when (filter) {
                                LibraryFilter.LIBRARY -> stringResource(R.string.filter_library)
                                LibraryFilter.PLAYLISTS -> stringResource(R.string.playlists)
                                LibraryFilter.SONGS -> stringResource(R.string.songs)
                                LibraryFilter.ARTISTS -> stringResource(R.string.artists)
                                LibraryFilter.ALBUMS -> stringResource(R.string.albums)
                                else -> filter.name
                            }
                            val iconRes = when (filter) {
                                LibraryFilter.LIBRARY -> R.drawable.graphic_eq
                                LibraryFilter.PLAYLISTS -> R.drawable.queue_music
                                LibraryFilter.SONGS -> R.drawable.music_note
                                LibraryFilter.ARTISTS -> R.drawable.person
                                LibraryFilter.ALBUMS -> R.drawable.album
                                else -> R.drawable.graphic_eq
                            }
                            ExpressiveTabChip(
                                label = label,
                                iconRes = iconRes,
                                selected = currentFilter == filter,
                                onClick = { coroutineScope.launch { pagerState.animateScrollToPage(page) } },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistTagFilterRow(
    tags: List<TagEntity>,
    selectedTagIds: Set<String>,
    onSelectedTagIdsChange: (Set<String>) -> Unit,
    onManageTagsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item(key = "all_playlist_tags", contentType = "playlist_tag_filter_action") {
            PlaylistTagFilterChip(
                label = stringResource(R.string.filter_all),
                selected = selectedTagIds.isEmpty(),
                iconRes = R.drawable.filter_alt,
                onClick = { onSelectedTagIdsChange(emptySet()) },
            )
        }

        items(
            items = tags,
            key = TagEntity::id,
            contentType = { "playlist_tag_filter" },
        ) { tag ->
            PlaylistTagFilterChip(
                label = tag.name,
                selected = tag.id in selectedTagIds,
                accentColor =
                    remember(tag.color) {
                        runCatching { Color(tag.color.toColorInt()) }.getOrDefault(Color.Unspecified)
                    },
                onClick = {
                    val nextSelection =
                        if (tag.id in selectedTagIds) {
                            selectedTagIds - tag.id
                        } else {
                            selectedTagIds + tag.id
                        }
                    onSelectedTagIdsChange(nextSelection)
                },
            )
        }

        item(key = "manage_playlist_tags", contentType = "playlist_tag_filter_action") {
            PlaylistTagFilterChip(
                label = stringResource(R.string.manage_tags),
                selected = false,
                iconRes = R.drawable.add,
                onClick = onManageTagsClick,
            )
        }
    }
}

@Composable
private fun PlaylistTagFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconRes: Int? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary,
) {
    val resolvedAccentColor =
        if (accentColor == Color.Unspecified) {
            MaterialTheme.colorScheme.primary
        } else {
            accentColor
        }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue =
            if (isPressed) {
                0.92f
            } else if (selected) {
                1.05f
            } else {
                1.0f
            },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "PlaylistTagFilterChipScale",
    )
    val containerColor by animateColorAsState(
        targetValue =
            if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "PlaylistTagFilterChipContainerColor",
    )
    val contentColor by animateColorAsState(
        targetValue =
            if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "PlaylistTagFilterChipContentColor",
    )

    Row(
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }.heightIn(min = 48.dp)
                .clip(CircleShape)
                .background(containerColor)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (iconRes != null) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
        } else {
            Box(
                modifier =
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (selected) contentColor else resolvedAccentColor),
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = contentColor,
        )
    }
}

@Composable
fun ExpressiveTabChip(
    label: String,
    iconRes: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue =
            if (isPressed) {
                0.92f
            } else if (selected) {
                1.05f
            } else {
                1.0f
            },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "TabChipScale",
    )

    val bgColor by animateColorAsState(
        targetValue =
            if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "TabChipBgColor",
    )

    val contentColor by animateColorAsState(
        targetValue =
            if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "TabChipContentColor",
    )

    Row(
        modifier =
            Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }.clip(CircleShape)
                .background(bgColor)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ).padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style =
                MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                ),
            color = contentColor,
        )
    }
}
