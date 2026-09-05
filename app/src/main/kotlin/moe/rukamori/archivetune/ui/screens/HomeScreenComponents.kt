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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import moe.rukamori.archivetune.ui.component.ItemThumbnail
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.ui.component.SpeedDialGridItem
import moe.rukamori.archivetune.ui.menu.AlbumMenu
import moe.rukamori.archivetune.ui.menu.ArtistMenu
import moe.rukamori.archivetune.ui.menu.PlaylistMenu
import moe.rukamori.archivetune.ui.menu.SongMenu
import moe.rukamori.archivetune.ui.menu.YouTubeAlbumMenu
import moe.rukamori.archivetune.ui.menu.YouTubeArtistMenu
import moe.rukamori.archivetune.ui.menu.YouTubePlaylistMenu
import moe.rukamori.archivetune.ui.menu.YouTubeSongMenu
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
                .padding(horizontal = HomeFeedGutter)
                .padding(vertical = 10.dp),
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
    // BitChord-style section header (2026-09-03 redesign): a heavy 22sp W700
    // title with an optional subtitle line, typography-led rather than
    // chrome-led, sitting at the 10dp page gutter. The fork's existing
    // affordances (leading glyph, account avatar, tap-to-navigate) render
    // inline before the title. See HomeFeedSectionHeader.
    HomeFeedSectionHeader(
        title = title,
        subtitle = label.orEmpty(),
        thumbnail = thumbnail,
        leadingIcon = leadingIcon,
        onClick = onClick,
        modifier = modifier,
    )
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
    playerConnection: PlayerConnection?,
    onPlayQueue: (moe.rukamori.archivetune.playback.queues.Queue) -> Unit = { playerConnection?.playQueue(it) },
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
        onPlayQueue(
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
                                                                            playerConnection?.player?.togglePlayPause()
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
 * Keep Listening section — the BitChord compact shelf (2026-09-03 redesign):
 * a single row of square 150dp cards for the mixed Song/Album/Artist items.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KeepListeningSection(
    keepListening: List<LocalItem>,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection?,
    onPlayQueue: (moe.rukamori.archivetune.playback.queues.Queue) -> Unit = { playerConnection?.playQueue(it) },
    menuState: MenuState,
    haptic: HapticFeedback,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // Per user request (2026-08-28): "Whenever I play a song from forgotten
    // favourites, keep listening or any other section I've to play each of
    // them manually because the queue for each song is different. it should
    // be same. For example if I play a song in recently listened all the
    // other next songs in queue should be from recently listened one by one
    // in order".
    //
    // Filter keepListening to songs only (the section is a mix of Song /
    // Album / Artist / Playlist) so we can build a ListQueue from just the
    // playable items. When a song is tapped, we look up its index in this
    // filtered list and pass it to the card as the startIndex.
    val songsInSection = remember(keepListening) { keepListening.filterIsInstance<Song>() }

    fun playFromSection(songId: String) {
        val index = songsInSection.indexOfFirst { it.id == songId }
        if (index < 0 || songsInSection.isEmpty()) return
        onPlayQueue(
            ListQueue(
                title = context.getString(R.string.keep_listening),
                items = songsInSection.map { it.toMediaItem() },
                startIndex = index,
            ),
        )
    }

    LazyRow(
        contentPadding = PaddingValues(horizontal = HomeFeedGutter),
        horizontalArrangement = Arrangement.spacedBy(HomeShelfCardSpacing),
        modifier = modifier.fillMaxWidth(),
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
            HomeFeedLocalItemCard(
                item = item,
                mediaMetadata = mediaMetadata,
                isPlaying = isPlaying,
                navController = navController,
                playerConnection = playerConnection,
                menuState = menuState,
                haptic = haptic,
                scope = scope,
                onPlaySongFromSection = ::playFromSection,
            )
        }
    }
}

/**
 * Forgotten Favorites section — the BitChord compact shelf (2026-09-03
 * redesign): a single row of square 150dp song cards. Tap plays from a queue
 * built over the whole section; hold opens the song menu.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ForgottenFavoritesSection(
    forgottenFavorites: List<Song>,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection?,
    onPlayQueue: (moe.rukamori.archivetune.playback.queues.Queue) -> Unit = { playerConnection?.playQueue(it) },
    menuState: MenuState,
    haptic: HapticFeedback,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val distinctForgottenFavorites = remember(forgottenFavorites) { forgottenFavorites.distinctBy { it.id } }

    // Per user request (2026-08-28): "Whenever I play a song from
    // forgotten favourites, keep listening or any other section I've to
    // play each of them manually because the queue for each song is
    // different. it should be same. For example if I play a song in
    // recently listened all the other next songs in queue should be
    // from recently listened one by one in order".
    //
    // Build the queue from the entire section list (with the tapped song
    // as the startIndex) so Next/Previous walks the section list in
    // order.
    fun playSectionQueue(startIndex: Int) {
        if (distinctForgottenFavorites.isEmpty()) return
        val safeStart = startIndex.coerceIn(0, distinctForgottenFavorites.lastIndex)
        onPlayQueue(
            ListQueue(
                title = context.getString(R.string.forgotten_favorites),
                items = distinctForgottenFavorites.map { it.toMediaItem() },
                startIndex = safeStart,
            ),
        )
    }

    LazyRow(
        contentPadding = PaddingValues(horizontal = HomeFeedGutter),
        horizontalArrangement = Arrangement.spacedBy(HomeShelfCardSpacing),
        modifier = modifier.fillMaxWidth(),
    ) {
        itemsIndexed(
            items = distinctForgottenFavorites,
            key = { _, song -> song.id },
            contentType = { _, _ -> "forgotten_favorite_song" },
        ) { index, song ->
            HomeFeedSongCard(
                song = song,
                mediaMetadata = mediaMetadata,
                isPlaying = isPlaying,
                navController = navController,
                playerConnection = playerConnection,
                menuState = menuState,
                haptic = haptic,
                onPlayFromSection = { playSectionQueue(index) },
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
    playerConnection: PlayerConnection?,
    onPlayQueue: (moe.rukamori.archivetune.playback.queues.Queue) -> Unit = { playerConnection?.playQueue(it) },
    menuState: MenuState,
    haptic: HapticFeedback,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    val distinctPlaylists = remember(accountPlaylists) { accountPlaylists.distinctBy { it.id } }

    LazyRow(
        contentPadding = PaddingValues(horizontal = HomeFeedGutter),
        horizontalArrangement = Arrangement.spacedBy(HomeShelfCardSpacing),
        modifier = modifier,
    ) {
        items(
            items = distinctPlaylists,
            key = { it.id },
            contentType = { "account_playlist" },
        ) { item ->
            HomeFeedYTItemCard(
                item = item,
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
    playerConnection: PlayerConnection?,
    onPlayQueue: (moe.rukamori.archivetune.playback.queues.Queue) -> Unit = { playerConnection?.playQueue(it) },
    menuState: MenuState,
    haptic: HapticFeedback,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = HomeFeedGutter),
        horizontalArrangement = Arrangement.spacedBy(HomeShelfCardSpacing),
        modifier = modifier,
    ) {
        items(
            items = recommendation.items,
            key = { it.id },
            contentType = { item -> item::class },
        ) { item ->
            HomeFeedYTItemCard(
                item = item,
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
    playerConnection: PlayerConnection?,
    onPlayQueue: (moe.rukamori.archivetune.playback.queues.Queue) -> Unit = { playerConnection?.playQueue(it) },
    menuState: MenuState,
    haptic: HapticFeedback,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // Per user request (2026-08-28): "Whenever I play a song from
    // forgotten favourites, keep listening or any other section I've to
    // play each of them manually because the queue for each song is
    // different. it should be same."
    //
    // For remote YouTube home sections (Quick Picks / Live Performances
    // / Other Remote shelves), build the queue from the section's
    // SongItem entries only (AlbumItem/ArtistItem/PlaylistItem navigate
    // to detail pages, not playback) with the tapped song as the
    // startIndex.
    val songsInSection = remember(section) { section.items.filterIsInstance<SongItem>() }
    val sectionTitle = remember(section) { section.title.takeIf { it.isNotBlank() } }

    fun playFromSection(songId: String) {
        val index = songsInSection.indexOfFirst { it.id == songId }
        if (index < 0 || songsInSection.isEmpty()) return
        onPlayQueue(
            ListQueue(
                title = sectionTitle ?: context.getString(R.string.quick_picks),
                items = songsInSection.map { it.toMediaItem() },
                startIndex = index,
            ),
        )
    }

    LazyRow(
        contentPadding = PaddingValues(horizontal = HomeFeedGutter),
        horizontalArrangement = Arrangement.spacedBy(HomeShelfCardSpacing),
        modifier = modifier,
    ) {
        items(
            items = section.items,
            key = { it.id },
            contentType = { item -> item::class },
        ) { item ->
            HomeFeedYTItemCard(
                item = item,
                mediaMetadata = mediaMetadata,
                isPlaying = isPlaying,
                navController = navController,
                menuState = menuState,
                haptic = haptic,
                scope = scope,
                onPlaySongFromSection = ::playFromSection,
            )
        }
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
    val context = LocalContext.current
    val thumbSizePx =
        with(LocalDensity.current) {
            ListThumbnailSize.roundToPx().coerceAtLeast(1)
        }
    HomeSectionHeader(
        label = stringResource(R.string.similar_to),
        title = recommendation.title.title,
        // Thumbnail (album art) removed per user request — the "Similar to"
        // label + artist/album title is enough context without the leading
        // image. Keeps these headers visually consistent with the other
        // text-only section headers on the home page.
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
    val context = LocalContext.current
    val thumbSizePx =
        with(LocalDensity.current) {
            ListThumbnailSize.roundToPx().coerceAtLeast(1)
        }
    HomeSectionHeader(
        title = section.title,
        label = section.label,
        leadingIcon = {
            // Every remote HomePage section now gets a leading icon — matches
            // the Recently Played (history) and Keep Listening (listening)
            // pattern so all home-section headers have a recognisable
            // affordance before the title text. Live performances get a
            // microphone; algorithmic shelves (Fresh finds, Old favourites,
            // Quick picks, etc.) get an auto_awesome sparkle.
            val iconRes =
                when {
                    section.title.contains("Live performance", ignoreCase = true) -> R.drawable.mic
                    section.title.contains("Quick pick", ignoreCase = true) -> R.drawable.discover_tune
                    section.title.contains("Fresh", ignoreCase = true) -> R.drawable.fire
                    section.title.contains("Old", ignoreCase = true) ||
                        section.title.contains("favourite", ignoreCase = true) ||
                        section.title.contains("forgotten", ignoreCase = true) -> R.drawable.cached
                    section.title.contains("New release", ignoreCase = true) -> R.drawable.new_release
                    section.title.contains("Trending", ignoreCase = true) -> R.drawable.trending_up
                    else -> R.drawable.auto_awesome
                }
            HomeSectionLeadingIcon(iconRes = iconRes)
        },
        thumbnail =
            section.thumbnail?.let { thumbnailUrl ->
                {
                    // Sized ImageRequest — same rationale as in
                    // SimilarRecommendationsTitle: request a thumbnail bucket
                    // close to 56dp instead of the original full-res artwork
                    // the CDN would otherwise serve. This is the slow-loading
                    // "playlist thumbnail" the user reported on the home feed.
                    val imageRequest =
                        remember(thumbnailUrl, thumbSizePx) {
                            ImageRequest
                                .Builder(context)
                                .data(thumbnailUrl)
                                .size(Size(thumbSizePx, thumbSizePx))
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .crossfade(true)
                                .build()
                        }
                    AsyncImage(
                        model = imageRequest,
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
        // BitChord displayLarge (2026-09-03 redesign): 34sp W800 with tight
        // -0.8 tracking — the page-title scale the big in-list header owns.
        fontSize = 34.sp,
        fontWeight = FontWeight.W800,
        letterSpacing = (-0.8).sp,
        color = foreground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = HomeFeedGutter)
                .padding(vertical = 8.dp),
    )
}

/**
 * "Jump back in" hero shelf — the BitChord lead-shelf treatment (2026-09-03
 * redesign): near-page-width cards (70% of the row, capped at 320dp, 0.92
 * aspect, 18dp corners) that page sideways, with the title/artist caption
 * laid over a scrim on the artwork itself.
 *
 * Uses [recentlyPlayed] (the listening-preference hero picks). Falls back
 * gracefully if fewer are available.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun JumpBackInHeroSection(
    recentlyPlayed: List<Song>,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection?,
    onPlayQueue: (moe.rukamori.archivetune.playback.queues.Queue) -> Unit = { playerConnection?.playQueue(it) },
    menuState: MenuState,
    haptic: HapticFeedback,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    if (recentlyPlayed.isEmpty()) return

    // Per user request (2026-08-28): "Whenever I play a song from
    // forgotten favourites, keep listening or any other section I've to
    // play each of them manually because the queue for each song is
    // different. it should be same."
    //
    // Build the queue from the entire hero list with the tapped song as the
    // startIndex, so Next/Previous walks the shelf in order.
    fun playFromSection(startIndex: Int) {
        if (recentlyPlayed.isEmpty()) return
        val safeStart = startIndex.coerceIn(0, recentlyPlayed.lastIndex)
        onPlayQueue(
            ListQueue(
                title = context.getString(R.string.home_jump_back_in_badge),
                items = recentlyPlayed.map { it.toMediaItem() },
                startIndex = safeStart,
            ),
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        HomeFeedSectionHeader(title = stringResource(R.string.home_jump_back_in_badge))
        // Measured rather than taken as a share of the parent, because the
        // card has a ceiling as well as a fraction — see homeHeroCardWidth().
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val cardWidth = homeHeroCardWidth(maxWidth)
            LazyRow(
                state = rememberLazyListState(),
                contentPadding = PaddingValues(horizontal = HomeFeedGutter),
                horizontalArrangement = Arrangement.spacedBy(HomeShelfCardSpacing),
            ) {
                itemsIndexed(
                    items = recentlyPlayed,
                    key = { index, song -> "hero_${song.id}_$index" },
                    contentType = { _, _ -> "hero_song" },
                ) { index, song ->
                    HomeFeedHeroCard(
                        thumbnailUrl = song.song.thumbnailUrl,
                        title = song.song.title,
                        subtitle = song.artists.joinToString { it.name },
                        isActive = song.id == mediaMetadata?.id,
                        isPlaying = isPlaying,
                        onClick = {
                            if (song.id == mediaMetadata?.id) {
                                playerConnection?.player?.togglePlayPause()
                            } else {
                                playFromSection(index)
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
                        modifier = Modifier.width(cardWidth),
                    )
                }
            }
        }
    }
}

/**
 * "Recently Played" section — the BitChord compact shelf (2026-09-03
 * redesign): a single row of square 150dp cards, 12dp corners, hairline
 * thumbnail border, with the title and artist beneath the artwork. Tap plays
 * from a queue built over the whole section; hold opens the song menu.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecentlyPlayedSection(
    recentlyPlayed: List<Song>,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection?,
    onPlayQueue: (moe.rukamori.archivetune.playback.queues.Queue) -> Unit = { playerConnection?.playQueue(it) },
    menuState: MenuState,
    haptic: HapticFeedback,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val distinctSongs = remember(recentlyPlayed) { recentlyPlayed.distinctBy { it.id } }
    if (distinctSongs.isEmpty()) return

    // Per user request (2026-08-28): "Whenever I play a song from forgotten
    // favourites, keep listening or any other section I've to play each of
    // them manually because the queue for each song is different. it should
    // be same. For example if I play a song in recently listened all the
    // other next songs in queue should be from recently listened one by one
    // in order".
    fun playFromSection(startIndex: Int) {
        if (distinctSongs.isEmpty()) return
        val safeStart = startIndex.coerceIn(0, distinctSongs.lastIndex)
        onPlayQueue(
            ListQueue(
                title = context.getString(R.string.recently_played),
                items = distinctSongs.map { it.toMediaItem() },
                startIndex = safeStart,
            ),
        )
    }

    LazyRow(
        contentPadding = PaddingValues(horizontal = HomeFeedGutter),
        horizontalArrangement = Arrangement.spacedBy(HomeShelfCardSpacing),
        modifier = modifier.fillMaxWidth(),
    ) {
        itemsIndexed(
            items = distinctSongs,
            key = { _, song -> "recent_${song.id}" },
            contentType = { _, _ -> "recent_song" },
        ) { index, song ->
            HomeFeedSongCard(
                song = song,
                mediaMetadata = mediaMetadata,
                isPlaying = isPlaying,
                navController = navController,
                playerConnection = playerConnection,
                menuState = menuState,
                haptic = haptic,
                onPlayFromSection = { playFromSection(index) },
            )
        }
    }
}

/**
 * Helper that renders the small neutral glyph used as the leading icon for
 * section headers (clock for "Recently Played", bolt for "Speed Dial").
 * Restyled for the BitChord header design (2026-09-03): the icon now renders
 * inline at 20dp with no circular container, so the header reads as
 * typography first with a quiet affordance before it.
 */
@Composable
fun HomeSectionLeadingIcon(
    iconRes: Int,
    modifier: Modifier = Modifier,
) {
    Icon(
        painter = painterResource(iconRes),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.size(20.dp),
    )
}
