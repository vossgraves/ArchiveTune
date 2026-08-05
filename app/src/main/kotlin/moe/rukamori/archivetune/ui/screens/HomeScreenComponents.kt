/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import kotlinx.coroutines.CoroutineScope
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.GridThumbnailHeight
import moe.rukamori.archivetune.constants.ListItemHeight
import moe.rukamori.archivetune.constants.ListThumbnailSize
import moe.rukamori.archivetune.constants.ThumbnailCornerRadius
import moe.rukamori.archivetune.db.entities.Album
import moe.rukamori.archivetune.db.entities.Artist
import moe.rukamori.archivetune.db.entities.LocalItem
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.innertube.pages.HomePage
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.models.SimilarRecommendation
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.ui.component.AlbumGridItem
import moe.rukamori.archivetune.ui.component.ArtistGridItem
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.ui.component.SongGridItem
import moe.rukamori.archivetune.ui.component.SongListItem
import moe.rukamori.archivetune.ui.component.SpeedDialGridItem
import moe.rukamori.archivetune.ui.component.YouTubeGridItem
import moe.rukamori.archivetune.ui.menu.AlbumMenu
import moe.rukamori.archivetune.ui.menu.ArtistMenu
import moe.rukamori.archivetune.ui.menu.PlaylistMenu
import moe.rukamori.archivetune.ui.menu.SongMenu
import moe.rukamori.archivetune.ui.menu.YouTubeAlbumMenu
import moe.rukamori.archivetune.ui.menu.YouTubeArtistMenu
import moe.rukamori.archivetune.ui.menu.YouTubePlaylistMenu
import moe.rukamori.archivetune.ui.menu.YouTubeSongMenu
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeCategoryChips(
    chips: List<HomePage.Chip>,
    selectedChip: HomePage.Chip?,
    onChipSelected: (HomePage.Chip) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 10.dp),
    ) {
        chips.forEach { chip ->
            val selected = chip == selectedChip
            FilterChip(
                selected = selected,
                onClick = { onChipSelected(chip) },
                label = {
                    Text(
                        text = chip.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingIcon =
                    if (selected) {
                        {
                            Icon(
                                painter = painterResource(R.drawable.done),
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize),
                            )
                        }
                    } else {
                        null
                    },
                shapes = FilterChipDefaults.shapes(),
                colors =
                    FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.78f),
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.94f),
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                border = null,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    thumbnail: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 24.dp, vertical = 6.dp),
    ) {
        leadingIcon?.invoke()
        thumbnail?.invoke()
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.weight(1f),
        ) {
            label?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onClick != null) {
            Icon(
                painter = painterResource(R.drawable.arrow_forward),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

private const val SpeedDialGridRows = 3
private const val SpeedDialGridColumns = 3
private const val SpeedDialItemsPerPage = SpeedDialGridRows * SpeedDialGridColumns

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SpeedDialSection(
    speedDialItems: List<LocalItem>,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    haptic: HapticFeedback,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    data class SpeedDialTile(
        val key: String,
        val localItem: LocalItem?,
        val ytItem: YTItem?,
    )

    val distinctSpeedDial =
        remember(speedDialItems) {
            speedDialItems
                .distinctBy {
                    when (it) {
                        is Song -> "song_${it.id}"
                        is Album -> "album_${it.id}"
                        is Artist -> "artist_${it.id}"
                        is Playlist -> "playlist_${it.id}"
                    }
                }.take(24)
        }
    val speedDialSongs = remember(distinctSpeedDial) { distinctSpeedDial.filterIsInstance<Song>() }
    val speedDialSongIndexById =
        remember(speedDialSongs) {
            speedDialSongs.mapIndexed { index, song -> song.id to index }.toMap()
        }
    val spacing = 10.dp

    val tiles =
        remember(distinctSpeedDial) {
            buildList {
                distinctSpeedDial.forEach { localItem ->
                    val key =
                        when (localItem) {
                            is Song -> "song_${localItem.id}"
                            is Album -> "album_${localItem.id}"
                            is Artist -> "artist_${localItem.id}"
                            is Playlist -> "playlist_${localItem.id}"
                        }
                    val ytItem =
                        when (localItem) {
                            is Song -> {
                                SongItem(
                                    id = localItem.id,
                                    title = localItem.title,
                                    artists =
                                        localItem.artists.map {
                                            moe.rukamori.archivetune.innertube.models
                                                .Artist(name = it.name, id = it.id)
                                        },
                                    thumbnail = localItem.song.thumbnailUrl.orEmpty(),
                                    explicit = localItem.song.explicit,
                                )
                            }

                            is Album -> {
                                AlbumItem(
                                    browseId = localItem.id,
                                    playlistId = localItem.album.playlistId.orEmpty(),
                                    title = localItem.title,
                                    artists =
                                        localItem.artists.map {
                                            moe.rukamori.archivetune.innertube.models
                                                .Artist(name = it.name, id = it.id)
                                        },
                                    year = localItem.album.year,
                                    thumbnail = localItem.album.thumbnailUrl.orEmpty(),
                                )
                            }

                            is Artist -> {
                                ArtistItem(
                                    id = localItem.id,
                                    title = localItem.title,
                                    thumbnail = localItem.artist.thumbnailUrl,
                                    channelId = localItem.artist.channelId,
                                    playEndpoint = null,
                                    shuffleEndpoint = null,
                                    radioEndpoint = null,
                                )
                            }

                            is Playlist -> {
                                PlaylistItem(
                                    id = localItem.id,
                                    title = localItem.title,
                                    author = null,
                                    songCountText = localItem.songCount.toString(),
                                    thumbnail = localItem.thumbnails.firstOrNull(),
                                    playEndpoint = null,
                                    shuffleEndpoint = null,
                                    radioEndpoint = null,
                                    isEditable = localItem.playlist.isEditable,
                                )
                            }
                        }
                    add(SpeedDialTile(key = key, localItem = localItem, ytItem = ytItem))
                }
                add(SpeedDialTile(key = "random", localItem = null, ytItem = null))
            }
        }
    val tilePages =
        remember(tiles) {
            tiles.chunked(SpeedDialItemsPerPage)
        }
    val visibleGridRows =
        remember(tilePages) {
            if (tilePages.size == 1) {
                ((tilePages.first().size + SpeedDialGridColumns - 1) / SpeedDialGridColumns)
                    .coerceIn(1, SpeedDialGridRows)
            } else {
                SpeedDialGridRows
            }
        }
    val pagerState =
        rememberPagerState(
            pageCount = { tilePages.size },
        )

    fun playSpeedDialQueue(startIndex: Int) {
        if (speedDialSongs.isEmpty()) return
        playerConnection.playQueue(
            ListQueue(
                title = context.getString(R.string.speed_dial),
                items = speedDialSongs.map { it.toMediaItem() },
                startIndex = startIndex,
            ),
        )
    }

    val selectedDotIndex by
        remember(pagerState, tilePages) {
            derivedStateOf {
                (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                    .roundToInt()
                    .coerceIn(0, (tilePages.size - 1).coerceAtLeast(0))
            }
        }
    val motionScheme = MaterialTheme.motionScheme

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 1.dp,
        modifier =
            modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            BoxWithConstraints(
                modifier =
                    Modifier
                        .padding(horizontal = 12.dp)
                        .fillMaxWidth(),
            ) {
                val tileSize = (maxWidth - spacing * (SpeedDialGridColumns - 1)) / SpeedDialGridColumns
                val gridHeight = (tileSize * visibleGridRows) + (spacing * (visibleGridRows - 1))

                HorizontalPager(
                    state = pagerState,
                    pageSize = PageSize.Fill,
                    pageSpacing = spacing,
                    key = { page -> tilePages[page].firstOrNull()?.key ?: "speed_dial_page_$page" },
                    verticalAlignment = Alignment.Top,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(gridHeight),
                ) { page ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(spacing),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        tilePages[page]
                            .chunked(SpeedDialGridColumns)
                            .forEach { rowTiles ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(spacing),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    rowTiles.forEach { tile ->
                                        val localItem = tile.localItem
                                        val ytItem = tile.ytItem
                                        if (localItem == null || ytItem == null) {
                                            SpeedDialRandomTile(
                                                onClick = {
                                                    if (speedDialSongs.isNotEmpty()) {
                                                        playSpeedDialQueue(Random.nextInt(speedDialSongs.size))
                                                    }
                                                },
                                                modifier = Modifier.size(tileSize),
                                            )
                                        } else {
                                            val isActive =
                                                when (localItem) {
                                                    is Song -> localItem.id == mediaMetadata?.id
                                                    is Album -> localItem.id == mediaMetadata?.album?.id
                                                    is Artist -> false
                                                    is Playlist -> false
                                                }
                                            val songIndex =
                                                if (localItem is Song) speedDialSongIndexById[localItem.id] ?: 0 else 0

                                            Box(
                                                modifier =
                                                    Modifier
                                                        .size(tileSize)
                                                        .clip(MaterialTheme.shapes.large)
                                                        .focusable()
                                                        .combinedClickable(
                                                            onClick = {
                                                                when (localItem) {
                                                                    is Song -> {
                                                                        if (isActive) {
                                                                            playerConnection.player.togglePlayPause()
                                                                        } else {
                                                                            playSpeedDialQueue(songIndex)
                                                                        }
                                                                    }

                                                                    is Album -> {
                                                                        navController.navigate("album/${localItem.id}")
                                                                    }

                                                                    is Artist -> {
                                                                        navController.navigate("artist/${localItem.id}")
                                                                    }

                                                                    is Playlist -> {
                                                                        navController.navigate("local_playlist/${localItem.id}")
                                                                    }
                                                                }
                                                            },
                                                            onLongClick = {
                                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                menuState.show {
                                                                    when (localItem) {
                                                                        is Song -> {
                                                                            SongMenu(
                                                                                originalSong = localItem,
                                                                                navController = navController,
                                                                                onDismiss = menuState::dismiss,
                                                                            )
                                                                        }

                                                                        is Album -> {
                                                                            AlbumMenu(
                                                                                originalAlbum = localItem,
                                                                                navController = navController,
                                                                                onDismiss = menuState::dismiss,
                                                                            )
                                                                        }

                                                                        is Artist -> {
                                                                            ArtistMenu(
                                                                                originalArtist = localItem,
                                                                                coroutineScope = scope,
                                                                                onDismiss = menuState::dismiss,
                                                                            )
                                                                        }

                                                                        is Playlist -> {
                                                                            PlaylistMenu(
                                                                                playlist = localItem,
                                                                                coroutineScope = scope,
                                                                                onDismiss = menuState::dismiss,
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                            },
                                                        ),
                                            ) {
                                                SpeedDialGridItem(
                                                    item = ytItem,
                                                    isPinned = true,
                                                    isActive = isActive,
                                                    isPlaying = isPlaying,
                                                )
                                            }
                                        }
                                    }
                                    repeat(SpeedDialGridColumns - rowTiles.size) {
                                        Spacer(modifier = Modifier.size(tileSize))
                                    }
                                }
                            }
                    }
                }
            }

            if (tilePages.size > 1) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    repeat(tilePages.size) { index ->
                        val isSelected = index == selectedDotIndex
                        val dotColor by animateColorAsState(
                            targetValue =
                                if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                },
                            animationSpec = motionScheme.defaultEffectsSpec(),
                            label = "speedDialDotColor",
                        )
                        val dotWidth by animateDpAsState(
                            targetValue = if (isSelected) 22.dp else 8.dp,
                            animationSpec = motionScheme.defaultSpatialSpec(),
                            label = "speedDialDotWidth",
                        )
                        Surface(
                            color = dotColor,
                            shape = MaterialTheme.shapes.extraLarge,
                            modifier =
                                Modifier
                                    .width(dotWidth)
                                    .height(8.dp),
                        ) {}
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpeedDialRandomTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 2.dp,
        modifier =
            modifier
                .aspectRatio(1f)
                .combinedClickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                repeat(3) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                        modifier = Modifier.size(18.dp),
                    ) {}
                }
            }
        }
    }
}

/**
 * Keep Listening section - horizontal grid of local items
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KeepListeningSection(
    keepListening: List<LocalItem>,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    haptic: HapticFeedback,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    val rows = if (keepListening.size > 6) 2 else 1
    val gridHeight =
        (
            GridThumbnailHeight +
                with(LocalDensity.current) {
                    MaterialTheme.typography.bodyLarge.lineHeight
                        .toDp() * 2 +
                        MaterialTheme.typography.bodyMedium.lineHeight
                            .toDp() * 2
                }
        ) * rows

    LazyHorizontalGrid(
        state = rememberLazyGridState(),
        rows = GridCells.Fixed(rows),
        contentPadding = PaddingValues(horizontal = 24.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .height(gridHeight),
    ) {
        items(
            items = keepListening,
            key = { item ->
                when (item) {
                    is Song -> "song_${item.id}"
                    is Album -> "album_${item.id}"
                    is Artist -> "artist_${item.id}"
                    is Playlist -> "playlist_${item.id}"
                }
            },
            contentType = { item -> item::class },
        ) { item ->
            LocalGridItem(
                item = item,
                mediaMetadata = mediaMetadata,
                isPlaying = isPlaying,
                navController = navController,
                playerConnection = playerConnection,
                menuState = menuState,
                haptic = haptic,
                scope = scope,
            )
        }
    }
}

/**
 * Forgotten Favorites section - horizontal grid of songs
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ForgottenFavoritesSection(
    forgottenFavorites: List<Song>,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    horizontalLazyGridItemWidth: Dp,
    lazyGridState: LazyGridState,
    snapLayoutInfoProvider: SnapLayoutInfoProvider,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    haptic: HapticFeedback,
    modifier: Modifier = Modifier,
) {
    val rows = min(4, forgottenFavorites.size)
    val distinctForgottenFavorites = remember(forgottenFavorites) { forgottenFavorites.distinctBy { it.id } }

    LazyHorizontalGrid(
        state = lazyGridState,
        rows = GridCells.Fixed(rows),
        flingBehavior = rememberSnapFlingBehavior(snapLayoutInfoProvider),
        contentPadding = PaddingValues(horizontal = 24.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .height(ListItemHeight * rows),
    ) {
        items(
            items = distinctForgottenFavorites,
            key = { it.id },
            contentType = { "forgotten_favorite_song" },
        ) { song ->
            SongListItem(
                song = song,
                showInLibraryIcon = true,
                isActive = song.id == mediaMetadata?.id,
                isPlaying = isPlaying,
                isSwipeable = false,
                trailingContent = {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuState.show {
                                SongMenu(
                                    originalSong = song,
                                    navController = navController,
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.more_vert),
                            contentDescription = null,
                        )
                    }
                },
                modifier =
                    Modifier
                        .width(horizontalLazyGridItemWidth)
                        .focusable()
                        .combinedClickable(
                            onClick = {
                                if (song.id == mediaMetadata?.id) {
                                    playerConnection.player.togglePlayPause()
                                } else {
                                    playerConnection.playQueue(
                                        if (song.song.isLocal) {
                                            ListQueue(items = listOf(song.toMediaItem()))
                                        } else {
                                            YouTubeQueue.radio(song.toMediaMetadata())
                                        },
                                    )
                                }
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuState.show {
                                    SongMenu(
                                        originalSong = song,
                                        navController = navController,
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            },
                        ),
            )
        }
    }
}

/**
 * Account Playlists section - horizontal row of YouTube playlists
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AccountPlaylistsSection(
    accountPlaylists: List<PlaylistItem>,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    haptic: HapticFeedback,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    val distinctPlaylists = remember(accountPlaylists) { accountPlaylists.distinctBy { it.id } }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 24.dp),
        modifier = modifier,
    ) {
        items(
            items = distinctPlaylists,
            key = { it.id },
            contentType = { "account_playlist" },
        ) { item ->
            YouTubeGridItemWrapper(
                item = item,
                mediaMetadata = mediaMetadata,
                isPlaying = isPlaying,
                navController = navController,
                playerConnection = playerConnection,
                menuState = menuState,
                haptic = haptic,
                scope = scope,
            )
        }
    }
}

/**
 * Similar Recommendations section
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SimilarRecommendationsSection(
    recommendation: SimilarRecommendation,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    haptic: HapticFeedback,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 24.dp),
        modifier = modifier,
    ) {
        items(
            items = recommendation.items,
            key = { it.id },
            contentType = { item -> item::class },
        ) { item ->
            YouTubeGridItemWrapper(
                item = item,
                mediaMetadata = mediaMetadata,
                isPlaying = isPlaying,
                navController = navController,
                playerConnection = playerConnection,
                menuState = menuState,
                haptic = haptic,
                scope = scope,
            )
        }
    }
}

/**
 * HomePage Section - a single section from YouTube home page
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomePageSectionContent(
    section: HomePage.Section,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    haptic: HapticFeedback,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 24.dp),
        modifier = modifier,
    ) {
        items(
            items = section.items,
            key = { it.id },
            contentType = { item -> item::class },
        ) { item ->
            YouTubeGridItemWrapper(
                item = item,
                mediaMetadata = mediaMetadata,
                isPlaying = isPlaying,
                navController = navController,
                playerConnection = playerConnection,
                menuState = menuState,
                haptic = haptic,
                scope = scope,
            )
        }
    }
}

// ============== Helper Composables ==============

/**
 * Wrapper for YouTube grid items with click handling
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun YouTubeGridItemWrapper(
    item: YTItem,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    haptic: HapticFeedback,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    YouTubeGridItem(
        item = item,
        isActive = item.id in listOf(mediaMetadata?.album?.id, mediaMetadata?.id),
        isPlaying = isPlaying,
        coroutineScope = scope,
        modifier =
            modifier
                .focusable()
                .combinedClickable(
                    onClick = {
                        when (item) {
                            is SongItem -> {
                                playerConnection.playQueue(
                                    YouTubeQueue(
                                        item.endpoint ?: WatchEndpoint(videoId = item.id),
                                        item.toMediaMetadata(),
                                    ),
                                )
                            }

                            is AlbumItem -> {
                                navController.navigate("album/${item.id}")
                            }

                            is ArtistItem -> {
                                navController.navigate("artist/${item.id}")
                            }

                            is PlaylistItem -> {
                                navController.navigate("online_playlist/${item.id}")
                            }
                        }
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuState.show {
                            when (item) {
                                is SongItem -> {
                                    YouTubeSongMenu(
                                        song = item,
                                        navController = navController,
                                        onDismiss = menuState::dismiss,
                                    )
                                }

                                is AlbumItem -> {
                                    YouTubeAlbumMenu(
                                        albumItem = item,
                                        navController = navController,
                                        onDismiss = menuState::dismiss,
                                    )
                                }

                                is ArtistItem -> {
                                    YouTubeArtistMenu(
                                        artist = item,
                                        onDismiss = menuState::dismiss,
                                    )
                                }

                                is PlaylistItem -> {
                                    YouTubePlaylistMenu(
                                        playlist = item,
                                        coroutineScope = scope,
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            }
                        }
                    },
                ),
    )
}

/**
 * Local item grid item for songs, albums, artists
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LocalGridItem(
    item: LocalItem,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    haptic: HapticFeedback,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    when (item) {
        is Song -> {
            SongGridItem(
                song = item,
                modifier =
                    modifier
                        .fillMaxWidth()
                        .focusable()
                        .combinedClickable(
                            onClick = {
                                if (item.id == mediaMetadata?.id) {
                                    playerConnection.player.togglePlayPause()
                                } else {
                                    playerConnection.playQueue(YouTubeQueue.radio(item.toMediaMetadata()))
                                }
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuState.show {
                                    SongMenu(
                                        originalSong = item,
                                        navController = navController,
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            },
                        ),
                isActive = item.id == mediaMetadata?.id,
                isPlaying = isPlaying,
            )
        }

        is Album -> {
            AlbumGridItem(
                album = item,
                isActive = item.id == mediaMetadata?.album?.id,
                isPlaying = isPlaying,
                coroutineScope = scope,
                modifier =
                    modifier
                        .fillMaxWidth()
                        .focusable()
                        .combinedClickable(
                            onClick = { navController.navigate("album/${item.id}") },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuState.show {
                                    AlbumMenu(
                                        originalAlbum = item,
                                        navController = navController,
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            },
                        ),
            )
        }

        is Artist -> {
            ArtistGridItem(
                artist = item,
                modifier =
                    modifier
                        .fillMaxWidth()
                        .focusable()
                        .combinedClickable(
                            onClick = { navController.navigate("artist/${item.id}") },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuState.show {
                                    ArtistMenu(
                                        originalArtist = item,
                                        coroutineScope = scope,
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            },
                        ),
            )
        }

        is Playlist -> { /* Not displayed */ }
    }
}

/**
 * Account playlist navigation title with image
 */
@Composable
fun AccountPlaylistsTitle(
    accountName: String,
    accountImageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HomeSectionHeader(
        label = stringResource(R.string.your_youtube_playlists),
        title = accountName.ifBlank { stringResource(R.string.account) },
        thumbnail = {
            if (accountImageUrl != null) {
                val context = LocalContext.current
                val avatarSizePx =
                    with(LocalDensity.current) {
                        ListThumbnailSize.roundToPx().coerceAtLeast(1)
                    }
                val imageRequest =
                    remember(accountImageUrl, avatarSizePx) {
                        ImageRequest
                            .Builder(context)
                            .data(accountImageUrl)
                            .size(Size(avatarSizePx, avatarSizePx))
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .diskCacheKey(accountImageUrl)
                            .crossfade(true)
                            .build()
                    }
                AsyncImage(
                    model = imageRequest,
                    placeholder = painterResource(id = R.drawable.person),
                    error = painterResource(id = R.drawable.person),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .size(ListThumbnailSize)
                            .clip(RoundedCornerShape(ThumbnailCornerRadius)),
                )
            } else {
                Icon(
                    painter = painterResource(id = R.drawable.person),
                    contentDescription = null,
                    modifier = Modifier.size(ListThumbnailSize),
                )
            }
        },
        onClick = onClick,
        modifier = modifier,
    )
}

/**
 * Similar recommendations navigation title
 */
@Composable
fun SimilarRecommendationsTitle(
    recommendation: SimilarRecommendation,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    HomeSectionHeader(
        label = stringResource(R.string.similar_to),
        title = recommendation.title.title,
        thumbnail =
            recommendation.title.thumbnailUrl?.let { thumbnailUrl ->
                {
                    AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = null,
                        modifier =
                            Modifier
                                .size(ListThumbnailSize)
                                .clip(RoundedCornerShape(ThumbnailCornerRadius)),
                    )
                }
            },
        onClick = {
            when (recommendation.title) {
                is Song -> {
                    navController.navigate("album/${recommendation.title.album!!.id}")
                }

                is Album -> {
                    navController.navigate("album/${recommendation.title.id}")
                }

                is Artist -> {
                    navController.navigate("artist/${recommendation.title.id}")
                }

                is Playlist -> {}
            }
        },
        modifier = modifier,
    )
}

/**
 * HomePage section navigation title
 */
@Composable
fun HomePageSectionTitle(
    section: HomePage.Section,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    HomeSectionHeader(
        title = section.title,
        label = section.label,
        thumbnail =
            section.thumbnail?.let { thumbnailUrl ->
                {
                    AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = null,
                        modifier =
                            Modifier
                                .size(ListThumbnailSize)
                                .clip(RoundedCornerShape(ThumbnailCornerRadius)),
                    )
                }
            },
        onClick =
            section.endpoint?.browseId?.let { browseId ->
                {
                    if (browseId == "FEmusic_moods_and_genres") {
                        navController.navigate(Screens.MoodAndGenres.route)
                    } else {
                        navController.navigate("browse/$browseId")
                    }
                }
            },
        modifier = modifier,
    )
}

// ============================================================
// Apple Music–style Home Redesign Components
// ============================================================

/**
 * Personalized greeting header — "Good morning/afternoon/evening, [name]".
 *
 * Mirrors the Apple Music / Muzo-style home header: a single line with a
 * large bold greeting and the user's name highlighted in the primary accent
 * color. The time-of-day prefix is computed from the system clock at
 * composition time so it stays correct without needing to observe a flow.
 */
@Composable
fun HomeGreetingHeader(
    accountName: String,
    modifier: Modifier = Modifier,
) {
    val hour = remember {
        java.util.Calendar
            .getInstance()
            .get(java.util.Calendar.HOUR_OF_DAY)
    }
    val greetingRes =
        when (hour) {
            in 5..11 -> R.string.greeting_morning
            in 12..16 -> R.string.greeting_afternoon
            in 17..21 -> R.string.greeting_evening
            else -> R.string.greeting_night
        }
    val greeting = stringResource(greetingRes)
    val displayName = accountName.ifBlank { stringResource(R.string.greeting_default_name) }
    val accent = MaterialTheme.colorScheme.primary
    val foreground = MaterialTheme.colorScheme.onSurface

    val text =
        remember(greeting, displayName, accent, foreground) {
            buildAnnotatedString {
                withStyle(SpanStyle(color = foreground, fontWeight = FontWeight.Bold)) {
                    append("$greeting, ")
                }
                withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold)) {
                    append(displayName)
                }
            }
        }

    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium.copy(fontSize = 32.sp),
        fontWeight = FontWeight.Bold,
        color = foreground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 14.dp),
    )
}

/**
 * "Jump back in" hero section — a two-column layout with one large hero card
 * on the left (~58% width) and two stacked smaller cards on the right (~42%
 * width). The hero card has a "JUMP BACK IN" pill badge at the top-left and
 * title/artist overlaid at the bottom. The smaller cards have title/artist
 * overlaid on the image.
 *
 * Uses the top 3 [recentlyPlayed] songs. Falls back gracefully if fewer are
 * available (collapses to 1 or 2 cards).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun JumpBackInHeroSection(
    recentlyPlayed: List<Song>,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    haptic: HapticFeedback,
    modifier: Modifier = Modifier,
) {
    if (recentlyPlayed.isEmpty()) return
    val hero = recentlyPlayed.first()
    val sideCards = recentlyPlayed.drop(1).take(2)
    if (sideCards.isEmpty()) {
        // No side cards — render a single full-width hero card.
        BoxWithConstraints(modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            val heroWidth = maxWidth
            val heroHeight = (heroWidth * 0.75f).coerceIn(180.dp, 260.dp)
            JumpBackInHeroCard(
                song = hero,
                isHero = true,
                width = heroWidth,
                height = heroHeight,
                mediaMetadata = mediaMetadata,
                isPlaying = isPlaying,
                playerConnection = playerConnection,
                menuState = menuState,
                haptic = haptic,
                navController = navController,
            )
        }
        return
    }

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
    ) {
        val spacing = 10.dp
        val availableWidth = maxWidth - spacing
        // Hero ~58% width, side column ~42% width — both deterministic.
        val heroWidth = availableWidth * 0.58f
        val sideColumnWidth = availableWidth * 0.42f
        // Side cards are rounded squares (matching the Recently Played style)
        // — both equal in shape so the "two pills" look identical.
        val sideCardHeight = sideColumnWidth
        val sideColumnHeight =
            (sideCardHeight * sideCards.size) + (spacing * (sideCards.size - 1))
        // Match hero height to the side column so the row has no empty gap.
        val heroHeight = sideColumnHeight

        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing),
            modifier = Modifier.fillMaxWidth(),
        ) {
            JumpBackInHeroCard(
                song = hero,
                isHero = true,
                width = heroWidth,
                height = heroHeight,
                mediaMetadata = mediaMetadata,
                isPlaying = isPlaying,
                playerConnection = playerConnection,
                menuState = menuState,
                haptic = haptic,
                navController = navController,
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(spacing),
                modifier = Modifier.width(sideColumnWidth),
            ) {
                sideCards.forEach { song ->
                    JumpBackInHeroCard(
                        song = song,
                        isHero = false,
                        width = sideColumnWidth,
                        height = sideCardHeight,
                        mediaMetadata = mediaMetadata,
                        isPlaying = isPlaying,
                        playerConnection = playerConnection,
                        menuState = menuState,
                        haptic = haptic,
                        navController = navController,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun JumpBackInHeroCard(
    song: Song,
    isHero: Boolean,
    width: Dp,
    height: Dp,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    haptic: HapticFeedback,
    navController: NavController,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val requestWidthPx = with(density) { width.roundToPx().coerceAtLeast(1) }
    val requestHeightPx = with(density) { height.roundToPx().coerceAtLeast(1) }
    val isActive = song.id == mediaMetadata?.id
    val imageRequest =
        remember(song.song.thumbnailUrl, requestWidthPx, requestHeightPx) {
            ImageRequest
                .Builder(context)
                .data(song.song.thumbnailUrl)
                .size(Size(requestWidthPx, requestHeightPx))
                .crossfade(true)
                .build()
        }

    Box(
        modifier =
            Modifier
                .size(width = width, height = height)
                .clip(RoundedCornerShape(20.dp))
                .combinedClickable(
                    onClick = {
                        if (isActive) {
                            playerConnection.player.togglePlayPause()
                        } else {
                            playerConnection.playQueue(
                                if (song.song.isLocal) {
                                    ListQueue(items = listOf(song.toMediaItem()))
                                } else {
                                    YouTubeQueue.radio(song.toMediaMetadata())
                                },
                            )
                        }
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuState.show {
                            SongMenu(
                                originalSong = song,
                                navController = navController,
                                onDismiss = menuState::dismiss,
                            )
                        }
                    },
                ),
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // Bottom-to-top scrim so the overlaid text stays legible on any artwork.
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.55f to Color.Black.copy(alpha = 0.10f),
                            1f to Color.Black.copy(alpha = 0.70f),
                        ),
                    ),
        )
        // "JUMP BACK IN" badge — only on the hero card.
        if (isHero) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(14.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.history),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = stringResource(R.string.home_jump_back_in_badge).uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 0.8.sp,
                )
            }
        }
        // Title + artist overlay at the bottom.
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(14.dp),
        ) {
            Text(
                text = song.song.title,
                style = if (isHero) MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp) else MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.artists.joinToString { it.name },
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = if (isHero) 15.sp else 13.sp),
                color = Color.White.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Active / playing indicator — small dot at top-right.
        if (isActive) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        ),
            )
        }
    }
}

/**
 * "Recently Played" section — horizontal row of square cards. Each card is
 * album-art-dominant with a 3-dot menu floating at the top-right corner and
 * title/artist beneath the artwork (matching the screenshot).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecentlyPlayedSection(
    recentlyPlayed: List<Song>,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    haptic: HapticFeedback,
    modifier: Modifier = Modifier,
) {
    val distinctSongs = remember(recentlyPlayed) { recentlyPlayed.distinctBy { it.id } }
    if (distinctSongs.isEmpty()) return

    LazyRow(
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        items(
            items = distinctSongs,
            key = { "recent_${it.id}" },
            contentType = { "recent_song" },
        ) { song ->
            RecentlyPlayedCard(
                song = song,
                mediaMetadata = mediaMetadata,
                isPlaying = isPlaying,
                playerConnection = playerConnection,
                menuState = menuState,
                haptic = haptic,
                navController = navController,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentlyPlayedCard(
    song: Song,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    haptic: HapticFeedback,
    navController: NavController,
) {
    val context = LocalContext.current
    val cardWidth = 165.dp
    val artworkSize = cardWidth
    val isActive = song.id == mediaMetadata?.id
    val artworkSizePx = with(LocalDensity.current) { artworkSize.roundToPx() }
    val imageRequest =
        remember(song.song.thumbnailUrl, artworkSizePx) {
            ImageRequest
                .Builder(context)
                .data(song.song.thumbnailUrl)
                .size(Size(artworkSizePx, artworkSizePx))
                .crossfade(true)
                .build()
        }

    Column(
        modifier =
            Modifier
                .width(cardWidth)
                .combinedClickable(
                    onClick = {
                        if (isActive) {
                            playerConnection.player.togglePlayPause()
                        } else {
                            playerConnection.playQueue(
                                if (song.song.isLocal) {
                                    ListQueue(items = listOf(song.toMediaItem()))
                                } else {
                                    YouTubeQueue.radio(song.toMediaMetadata())
                                },
                            )
                        }
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuState.show {
                            SongMenu(
                                originalSong = song,
                                navController = navController,
                                onDismiss = menuState::dismiss,
                            )
                        }
                    },
                ),
    ) {
        Box(
            modifier =
                Modifier
                    .size(width = artworkSize, height = artworkSize)
                    .clip(RoundedCornerShape(20.dp)),
        ) {
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // 3-dot menu floating at top-right of the artwork.
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuState.show {
                                SongMenu(
                                    originalSong = song,
                                    navController = navController,
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.more_vert),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
            // Active / playing indicator — small dot at top-left.
            if (isActive) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            ),
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = song.song.title,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = song.artists.joinToString { it.name },
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Helper that renders a small neutral-tinted circular icon used as the leading
 * icon for section headers (clock for "Recently Played", bolt for "Quick Picks").
 * Uses surfaceContainerHigh so the accent color is reserved for the username,
 * play button, active indicators, and progress bars — per the redesign spec.
 */
@Composable
fun HomeSectionLeadingIcon(
    iconRes: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(18.dp),
        )
    }
}
