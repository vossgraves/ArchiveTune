/*
 * YumaPlayer (2026) | Modified work by MuwMx
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens

import moe.rukamori.archivetune.spotify.isSpotifyDj
import moe.rukamori.archivetune.spotify.SPOTIFY_DJ_PLAYLIST_ID
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import android.net.Uri
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import moe.rukamori.archivetune.constants.GridThumbnailCornerRadius
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.ui.component.GridItem
import moe.rukamori.archivetune.ui.component.ItemThumbnail
import moe.rukamori.archivetune.utils.joinByBullet
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.pressScaleClickable
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.Artist
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.spotify.SectionType
import moe.rukamori.archivetune.spotify.SpotifyHomeAction
import moe.rukamori.archivetune.spotify.SpotifyHomeNavigationEvent
import moe.rukamori.archivetune.spotify.SpotifyHomeSection
import moe.rukamori.archivetune.spotify.SpotifyHomeScreenState
import moe.rukamori.archivetune.spotify.SpotifyHomeViewModel
import moe.rukamori.archivetune.spotify.SpotifyRecentItem
import moe.rukamori.archivetune.spotify.SpotifyTracksQueue
import moe.rukamori.archivetune.spotify.models.SpotifyAlbum
import moe.rukamori.archivetune.spotify.models.SpotifyArtist
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylist
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import moe.rukamori.archivetune.constants.SpotifyHomeStyle
import moe.rukamori.archivetune.constants.SpotifyHomeStyleKey
import moe.rukamori.archivetune.ui.component.ExpressivePullToRefreshBox
import moe.rukamori.archivetune.ui.component.SpotifyTrackListItem
import moe.rukamori.archivetune.ui.component.YouTubeGridItem
import moe.rukamori.archivetune.utils.rememberEnumPreference

/**
 * The geometry that separates the three [SpotifyHomeStyle] looks. The sections themselves are the
 * same Spotify data in the same order under every style — only how densely they are laid out
 * changes, which is also the only thing that actually differs between the two YouTube homes.
 *
 * Holding it as one value rather than branching inside each row keeps the four section rows to a
 * single implementation apiece; three copies of each would drift the first time one is touched.
 */
@androidx.compose.runtime.Immutable
data class SpotifyHomeMetrics(
    /** Rows deep the track grid runs. Spotify stacks two; the Rukamori home packs four. */
    val trackRows: Int,
    val trackItemWidth: Dp,
    /** Height of one row of the track grid; total grid height is this times [trackRows]. */
    val trackRowHeight: Dp,
    /** Width of an album/playlist card. */
    val cardWidth: Dp,
    val artistSize: Dp,
    val contentPadding: Dp,
    val itemSpacing: Dp,
)

@Composable
fun rememberSpotifyHomeMetrics(): SpotifyHomeMetrics {
    val style by rememberEnumPreference(SpotifyHomeStyleKey, defaultValue = SpotifyHomeStyle.SPOTIFY)
    return remember(style) {
        when (style) {
            // Spotify's own proportions, and the values this screen shipped with.
            SpotifyHomeStyle.SPOTIFY ->
                SpotifyHomeMetrics(
                    trackRows = 2,
                    trackItemWidth = 240.dp,
                    trackRowHeight = 128.dp,
                    cardWidth = 150.dp,
                    artistSize = 140.dp,
                    contentPadding = 16.dp,
                    itemSpacing = 12.dp,
                )
            // Matches HomeScreen: single-row carousels, 12dp gutters, GridThumbnailHeight cards.
            SpotifyHomeStyle.DEFAULT ->
                SpotifyHomeMetrics(
                    trackRows = 1,
                    trackItemWidth = 300.dp,
                    trackRowHeight = 72.dp,
                    cardWidth = 128.dp,
                    artistSize = 128.dp,
                    contentPadding = 12.dp,
                    itemSpacing = 8.dp,
                )
            // Matches RukamoriHomeScreen: deep grids, small cards, tight gutters.
            SpotifyHomeStyle.RUKAMORI ->
                SpotifyHomeMetrics(
                    trackRows = 4,
                    trackItemWidth = 280.dp,
                    trackRowHeight = 64.dp,
                    cardWidth = 112.dp,
                    artistSize = 104.dp,
                    contentPadding = 8.dp,
                    itemSpacing = 6.dp,
                )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
/**
 * Opens a Spotify playlist tile, or hands the DJ off to Spotify.
 *
 * The DJ arrives shaped like a playlist and is not one — see [isSpotifyDj]. Navigating to it gave
 * an empty playlist page, which read as the tile being broken; Spotify's own app is the only place
 * it can actually play.
 */
@Composable
private fun rememberOpenSpotifyPlaylist(navController: NavController): (String) -> Unit {
    val context = LocalContext.current
    return remember(context, navController) {
        { playlistId: String ->
            if (isSpotifyDj(playlistId)) {
                Toast
                    .makeText(context, context.getString(R.string.spotify_dj_unsupported), Toast.LENGTH_LONG)
                    .show()
                runCatching {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://open.spotify.com/playlist/$SPOTIFY_DJ_PLAYLIST_ID"),
                        ),
                    )
                }
            } else {
                navController.navigate("spotify_playlist/$playlistId")
            }
            Unit
        }
    }
}

@Composable
fun SpotifyHomeScreen(
    navController: NavController,
    headerScrollConnection: NestedScrollConnection? = null,
    viewModel: SpotifyHomeViewModel = hiltViewModel(),
) {
    val openSpotifyPlaylist = rememberOpenSpotifyPlaylist(navController)
    val playerConnection = LocalPlayerConnection.current ?: return
    val context = LocalContext.current
    val screenState by viewModel.screenState.collectAsStateWithLifecycle()
    val resolvingItemKey by viewModel.resolvingItemKey.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val metrics = rememberSpotifyHomeMetrics()
    val onSwitchToYoutube = rememberSwitchToYouTube()

    DisposableEffect(viewModel) {
        onDispose { viewModel.cancelSelection() }
    }

    LaunchedEffect(viewModel, navController, playerConnection, context) {
        viewModel.navigationEvents.collect { event ->
            when (event) {
                is SpotifyHomeNavigationEvent.OpenAlbum -> navController.navigate("album/${event.browseId}")
                is SpotifyHomeNavigationEvent.OpenArtist -> navController.navigate("artist/${event.id}")
                is SpotifyHomeNavigationEvent.PlayTracks -> playerConnection.playQueue(event.queue)
                is SpotifyHomeNavigationEvent.ShowMessage ->
                    Toast.makeText(context, event.messageResId, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (headerScrollConnection != null) {
                    Modifier.nestedScroll(headerScrollConnection)
                } else {
                    Modifier
                }
            )
    ) {
        when (val state = screenState) {
            SpotifyHomeScreenState.Loading -> {
                HomeStatePane(
                    iconResId = null,
                    messageResId = null,
                    showLoadingIndicator = true,
                )
            }
            SpotifyHomeScreenState.Empty -> {
                HomeStatePane(
                    iconResId = R.drawable.music_note,
                    messageResId = R.string.no_results_found,
                    actionResId = R.string.retry,
                    onAction = { viewModel.onAction(SpotifyHomeAction.Refresh) },
                )
            }
            is SpotifyHomeScreenState.Error -> {
                if (state.notAuthenticated == true) {
                    HomeStatePane(
                        iconResId = R.drawable.ic_about,
                        messageResId = R.string.spotify_not_connected,
                        actionResId = R.string.home_switch_to_yt,
                        onAction = onSwitchToYoutube,
                    )
                } else {
                    HomeStatePane(
                        iconResId = R.drawable.ic_about,
                        messageResId = state.messageResId,
                        actionResId = R.string.retry,
                        onAction = { viewModel.onAction(SpotifyHomeAction.Refresh) },
                    )
                }
            }
            is SpotifyHomeScreenState.Success -> {
                ExpressivePullToRefreshBox(
                    isRefreshing = false,
                    onRefresh = { viewModel.onAction(SpotifyHomeAction.Refresh) },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item(key = "spotify_recent_panel", contentType = "recent_panel") {
                            SpotifyRecentPanel(
                                recentItems = state.recentItems,
                                frequentArtists = state.frequentArtists,
                                onPlaylistClick = { playlist ->
                                    viewModel.cancelSelection()
                                    openSpotifyPlaylist(playlist.id)
                                },
                                onAlbumClick = { album -> 
                                    viewModel.onAction(SpotifyHomeAction.AlbumClick(
                                        moe.rukamori.archivetune.spotify.models.SpotifyAlbum(
                                            id = album.id,
                                            name = album.name,
                                            artists = album.artists.map { moe.rukamori.archivetune.spotify.models.SpotifySimpleArtist(id = it.id, name = it.name, uri = it.uri) },
                                            images = listOfNotNull(album.imageUrl?.let { moe.rukamori.archivetune.spotify.models.SpotifyImage(it, null, null) })
                                        )
                                    )) 
                                },
                                onArtistClick = { artist -> viewModel.onAction(SpotifyHomeAction.ArtistClick(artist)) },
                                resolvingItemKey = resolvingItemKey,
                                modifier = Modifier.animateItem()
                            )
                        }

                        state.sections.forEachIndexed { index, section ->
                            item(
                                key = "spotify_section_title_${section.title}_$index",
                                contentType = "section_header"
                            ) {
                                HomeSectionHeader(
                                    title = resolveSpotifySectionTitle(section),
                                    modifier = Modifier.animateItem()
                                )
                            }

                            item(
                                key = "spotify_section_content_${section.title}_$index",
                                contentType = "section_content"
                            ) {
                                when (section.type) {
                                    SectionType.TRACKS -> {
                                        val sectionTitle = resolveSpotifySectionTitle(section)
                                        SpotifyTrackSectionRow(
                                            tracks = section.tracks,
                                            metrics = metrics,
                                            onTrackClick = { track ->
                                                if (mediaMetadata?.spotifyTrackId == track.id) {
                                                    viewModel.cancelSelection()
                                                    playerConnection.player.togglePlayPause()
                                                } else {
                                                    viewModel.onAction(
                                                        SpotifyHomeAction.TrackClick(track, section.tracks, sectionTitle),
                                                    )
                                                }
                                            },
                                            activeTrackId = mediaMetadata?.spotifyTrackId,
                                            isPlaying = isPlaying,
                                            resolvingItemKey = resolvingItemKey,
                                            modifier = Modifier.animateItem(),
                                        )
                                    }
                                    SectionType.ARTISTS -> {
                                        SpotifyArtistSectionRow(
                                            artists = section.artists,
                                            metrics = metrics,
                                            resolvingItemKey = resolvingItemKey,
                                            onArtistClick = { artist -> viewModel.onAction(SpotifyHomeAction.ArtistClick(artist)) },
                                            modifier = Modifier.animateItem()
                                        )
                                    }
                                    SectionType.ALBUMS -> {
                                        SpotifyAlbumSectionRow(
                                            albums = section.albums,
                                            metrics = metrics,
                                            resolvingItemKey = resolvingItemKey,
                                            onAlbumClick = { album -> viewModel.onAction(SpotifyHomeAction.AlbumClick(album)) },
                                            modifier = Modifier.animateItem()
                                        )
                                    }
                                    SectionType.PLAYLISTS -> {
                                        SpotifyPlaylistSectionRow(
                                            playlists = section.playlists,
                                            metrics = metrics,
                                            onPlaylistClick = { playlist ->
                                                viewModel.cancelSelection()
                                                openSpotifyPlaylist(playlist.id)
                                            },
                                            modifier = Modifier.animateItem()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun resolveSpotifySectionTitle(section: SpotifyHomeSection): String {
    val title = section.title
    return when {
        title.startsWith("spotify_because_you_like:") -> {
            val artistName = title.removePrefix("spotify_because_you_like:")
            stringResource(R.string.spotify_because_you_like, artistName)
        }
        title == "spotify_top_tracks" -> stringResource(R.string.spotify_top_tracks)
        title == "spotify_top_artists" -> stringResource(R.string.spotify_top_artists)
        title == "spotify_made_for_you" -> stringResource(R.string.spotify_made_for_you)
        title == "spotify_discover" -> stringResource(R.string.spotify_discover)
        title == "spotify_your_playlists" -> stringResource(R.string.spotify_your_playlists)
        title == "spotify_new_releases" -> stringResource(R.string.spotify_new_releases)
        else -> title
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SpotifyTrackSectionRow(
    tracks: List<SpotifyTrack>,
    metrics: SpotifyHomeMetrics,
    onTrackClick: (SpotifyTrack) -> Unit,
    modifier: Modifier = Modifier,
    activeTrackId: String? = null,
    isPlaying: Boolean = false,
    resolvingItemKey: String? = null,
) {
    if (tracks.isEmpty()) return
    val rowCount = metrics.trackRows.coerceAtMost(tracks.size).coerceAtLeast(1)
    LazyHorizontalGrid(
        state = rememberLazyGridState(),
        rows = GridCells.Fixed(rowCount),
        contentPadding = PaddingValues(horizontal = metrics.contentPadding),
        modifier = modifier
            .fillMaxWidth()
            .height(metrics.trackRowHeight * rowCount),
    ) {
        itemsIndexed(
            items = tracks,
            key = { index, track -> "spotify_track_${track.id}_$index" },
            contentType = { _, _ -> "spotify_track" },
        ) { _, track ->
            SpotifyTrackListItem(
                track = track,
                isActive = activeTrackId == track.id,
                isPlaying = isPlaying,
                trailingContent = {
                    if (resolvingItemKey == "track:${track.id}") SpotifySelectionIndicator()
                },
                modifier = Modifier
                    .width(metrics.trackItemWidth)
                    .fillMaxHeight()
                    .pressScaleClickable(onClick = { onTrackClick(track) }),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SpotifyArtistSectionRow(
    artists: List<SpotifyArtist>,
    metrics: SpotifyHomeMetrics,
    onArtistClick: (SpotifyArtist) -> Unit,
    modifier: Modifier = Modifier,
    resolvingItemKey: String? = null,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = metrics.contentPadding),
        horizontalArrangement = Arrangement.spacedBy(metrics.itemSpacing),
        modifier = modifier,
    ) {
        items(
            items = artists,
            key = { "spotify_artist_${it.id}" },
            contentType = { "spotify_artist" }
        ) { artist ->
            val thumbnail = remember(artist.id) {
                artist.images.maxByOrNull { it.width ?: 0 }?.url
                    ?: artist.images.firstOrNull()?.url
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(metrics.artistSize)
                    .pressScaleClickable(onClick = { onArtistClick(artist) }),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model = thumbnail,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(metrics.artistSize)
                            .clip(CircleShape),
                    )
                    if (resolvingItemKey == "artist:${artist.id}") SpotifySelectionIndicator()
                }
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SpotifyAlbumSectionRow(
    albums: List<SpotifyAlbum>,
    metrics: SpotifyHomeMetrics,
    onAlbumClick: (SpotifyAlbum) -> Unit,
    modifier: Modifier = Modifier,
    resolvingItemKey: String? = null,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = metrics.contentPadding),
        horizontalArrangement = Arrangement.spacedBy(metrics.itemSpacing),
        modifier = modifier,
    ) {
        items(
            items = albums,
            key = { "spotify_album_${it.id}" },
            contentType = { "spotify_album" },
        ) { album ->
            SpotifyHomeCard(
                title = album.name,
                subtitle = album.artists.joinToString { it.name },
                thumbnailUrl = album.images.maxByOrNull { it.width ?: 0 }?.url,
                isResolving = resolvingItemKey == "album:${album.id}",
                onClick = { onAlbumClick(album) },
                modifier = Modifier.width(metrics.cardWidth),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SpotifyPlaylistSectionRow(
    playlists: List<SpotifyPlaylist>,
    metrics: SpotifyHomeMetrics,
    onPlaylistClick: (SpotifyPlaylist) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = metrics.contentPadding),
        horizontalArrangement = Arrangement.spacedBy(metrics.itemSpacing),
        modifier = modifier,
    ) {
        items(
            items = playlists,
            key = { "spotify_playlist_${it.id}" },
            contentType = { "spotify_playlist" }
        ) { playlist ->
            SpotifyHomeCard(
                title = playlist.name,
                subtitle = joinByBullet(playlist.owner?.displayName, playlist.tracks?.total?.toString()),
                thumbnailUrl = playlist.images.maxByOrNull { it.width ?: 0 }?.url,
                onClick = { onPlaylistClick(playlist) },
                modifier = Modifier.width(metrics.cardWidth),
            )
        }
    }
}

@Composable
private fun SpotifyHomeCard(
    title: String,
    subtitle: String,
    thumbnailUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isResolving: Boolean = false,
) {
    GridItem(
        title = title,
        subtitle = subtitle,
        fillMaxWidth = true,
        thumbnailContent = {
            ItemThumbnail(
                thumbnailUrl = thumbnailUrl,
                isActive = false,
                isPlaying = false,
                shape = RoundedCornerShape(GridThumbnailCornerRadius),
                placeholderIconRes = R.drawable.music_note,
            )
            if (isResolving) SpotifySelectionIndicator()
        },
        modifier = modifier.pressScaleClickable(onClick = onClick),
    )
}

@Composable
private fun SpotifySelectionIndicator() {
    val loadingLabel = stringResource(R.string.loading)
    CircularProgressIndicator(
        strokeWidth = 2.dp,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surface, CircleShape)
            .padding(6.dp)
            .size(20.dp)
            .semantics { contentDescription = loadingLabel },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HomeStatePane(
    iconResId: Int?,
    messageResId: Int?,
    modifier: Modifier = Modifier,
    actionResId: Int? = null,
    showLoadingIndicator: Boolean = false,
    onAction: (() -> Unit)? = null,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues()),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            if (showLoadingIndicator) {
                androidx.compose.material3.LoadingIndicator()
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
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (actionResId != null && onAction != null) {
                    Spacer(Modifier.height(20.dp))
                    androidx.compose.material3.FilledTonalButton(onClick = onAction) {
                        Text(stringResource(actionResId))
                    }
                }
            }
        }
    }
}

@Composable
fun SpotifyRecentPanel(
    recentItems: List<SpotifyRecentItem>,
    frequentArtists: List<SpotifyArtist>,
    onPlaylistClick: (SpotifyRecentItem.Playlist) -> Unit,
    onAlbumClick: (SpotifyRecentItem.Album) -> Unit,
    onArtistClick: (SpotifyArtist) -> Unit,
    modifier: Modifier = Modifier,
    resolvingItemKey: String? = null,
) {
    Column(modifier = modifier) {
        if (recentItems.isNotEmpty()) {
            HomeSectionHeader(
                title = stringResource(R.string.spotify_recently_played),
            )
            SpotifyQuickGrid(
                items = recentItems,
                maxItems = 8,
                columns = 2
            ) { item ->
                when (item) {
                    is SpotifyRecentItem.Playlist -> {
                        SpotifyQuickGridCell(
                            title = item.name,
                            imageUrl = item.imageUrl,
                            onClick = { onPlaylistClick(item) },
                            isArtist = false
                        )
                    }
                    is SpotifyRecentItem.Album -> {
                        SpotifyQuickGridCell(
                            title = item.name,
                            imageUrl = item.imageUrl,
                            onClick = { onAlbumClick(item) },
                            isArtist = false,
                            isResolving = resolvingItemKey == "album:${item.id}",
                        )
                    }
                }
            }
        }

        if (frequentArtists.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            HomeSectionHeader(
                title = stringResource(R.string.spotify_frequently_listened),
            )
            SpotifyQuickGrid(
                items = frequentArtists,
                maxItems = 8,
                columns = 2
            ) { artist ->
                val thumbnail = remember(artist.id) {
                    artist.images.maxByOrNull { it.width ?: 0 }?.url
                        ?: artist.images.firstOrNull()?.url
                }
                SpotifyQuickGridCell(
                    title = artist.name,
                    imageUrl = thumbnail,
                    onClick = { onArtistClick(artist) },
                    isArtist = true,
                    isResolving = resolvingItemKey == "artist:${artist.id}",
                )
            }
        }
    }
}

@Composable
private fun <T> SpotifyQuickGrid(
    items: List<T>,
    maxItems: Int = 8,
    columns: Int = 2,
    itemContent: @Composable (T) -> Unit
) {
    val displayItems = items.take(maxItems)
    if (displayItems.isEmpty()) return
    
    val rows = displayItems.chunked(columns)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
    ) {
        rows.forEachIndexed { rowIndex, rowItems ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                rowItems.forEach { item ->
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        itemContent(item)
                    }
                }
                val emptyCells = columns - rowItems.size
                repeat(emptyCells) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            if (rowIndex < rows.lastIndex) {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun SpotifyQuickGridCell(
    title: String,
    imageUrl: String?,
    onClick: () -> Unit,
    isArtist: Boolean,
    isResolving: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .pressScaleClickable(onClick = onClick)
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                // Если артист - круг, если альбом - скругляем только левые углы под форму плашки
                .clip(if (isArtist) CircleShape else RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(
                color = Color.White,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
        if (isResolving) SpotifySelectionIndicator()
    }
}
