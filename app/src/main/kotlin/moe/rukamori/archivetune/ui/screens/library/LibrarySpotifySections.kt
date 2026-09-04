/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.library

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.spotify.SpotifyLibraryViewModel
import moe.rukamori.archivetune.spotify.SpotifyPlaybackResolver
import moe.rukamori.archivetune.spotify.SpotifySearchItem
import moe.rukamori.archivetune.ui.component.ExpressivePullToRefreshBox
import moe.rukamori.archivetune.ui.screens.search.SpotifySearchItemRow

/**
 * The Library's Songs, Artists and Albums sections on the Spotify source.
 *
 * Each is deliberately a plain list rather than a Spotify-flavoured copy of the local section's
 * sorting, filtering, multi-select and grid toggle: none of those mean anything against a remote
 * library the app cannot reorder or tag, and the local screens' machinery is what makes them long.
 *
 * Rows reuse [SpotifySearchItemRow], the same renderer Spotify search results use, so a Spotify
 * track looks the same wherever it turns up.
 */
@Composable
fun LibrarySpotifySongsScreen(viewModel: SpotifyLibraryViewModel = hiltViewModel()) {
    val tracks by viewModel.likedSongs.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loadLikedSongs() }

    SpotifySectionList(
        items = remember(tracks) { tracks.map(SpotifySearchItem::Track) },
        isRefreshing = viewModel.isLoadingSection.collectAsStateWithLifecycle().value,
        onRefresh = { viewModel.loadLikedSongs(force = true) },
    )
}

@Composable
fun LibrarySpotifyArtistsScreen(viewModel: SpotifyLibraryViewModel = hiltViewModel()) {
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loadArtists() }

    SpotifySectionList(
        items = remember(artists) { artists.map(SpotifySearchItem::Artist) },
        isRefreshing = viewModel.isLoadingSection.collectAsStateWithLifecycle().value,
        onRefresh = { viewModel.loadArtists(force = true) },
    )
}

@Composable
fun LibrarySpotifyAlbumsScreen(viewModel: SpotifyLibraryViewModel = hiltViewModel()) {
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loadAlbums() }

    SpotifySectionList(
        items = remember(albums) { albums.map(SpotifySearchItem::Album) },
        isRefreshing = viewModel.isLoadingSection.collectAsStateWithLifecycle().value,
        onRefresh = { viewModel.loadAlbums(force = true) },
    )
}

@Composable
private fun SpotifySectionList(
    items: List<SpotifySearchItem>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
) {
    val playerAwareBottomPadding =
        LocalPlayerAwareWindowInsets.current
            .only(WindowInsetsSides.Bottom)
            .asPaddingValues()
            .calculateBottomPadding() + 12.dp

    ExpressivePullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
        indicatorOffset = LibraryPullToRefreshIndicatorOffset,
    ) {
        LazyColumn(
            state = rememberLazyListState(),
            contentPadding =
                PaddingValues(
                    top = LibraryHeaderContentPadding,
                    bottom = playerAwareBottomPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "library_source_pills", contentType = "library_source_pills") {
                LibrarySourcePills(modifier = Modifier.padding(bottom = 4.dp))
            }

            if (items.isEmpty() && !isRefreshing) {
                item(key = "spotify_section_empty", contentType = "spotify_section_empty") {
                    Text(
                        text = stringResource(R.string.spotify_no_sources),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    )
                }
            }

            items(
                items = items,
                key = SpotifySearchItem::key,
                contentType = { "spotify_section_row" },
            ) { item ->
                SpotifySectionRow(item)
            }
        }
    }
}

@Composable
private fun SpotifySectionRow(item: SpotifySearchItem) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    val coroutineScope = rememberCoroutineScope()
    val mediaMetadata by
        playerConnection
            ?.mediaMetadata
            ?.collectAsStateWithLifecycle()
            ?: remember { mutableStateOf(null) }
    val isPlaying by
        playerConnection
            ?.isPlaying
            ?.collectAsStateWithLifecycle()
            ?: remember { mutableStateOf(false) }
    var resolving by remember(item.key) { mutableStateOf(false) }

    val onClick: () -> Unit = {
        when (item) {
            is SpotifySearchItem.Track -> {
                val track = item.value
                if (mediaMetadata?.spotifyTrackId == track.id && playerConnection != null) {
                    playerConnection.player.togglePlayPause()
                } else if (playerConnection != null && !resolving) {
                    resolving = true
                    coroutineScope.launch {
                        try {
                            val metadata =
                                withContext(Dispatchers.IO) {
                                    SpotifyPlaybackResolver.resolveToMetadata(track)
                                }
                            if (metadata != null) {
                                playerConnection.playQueue(YouTubeQueue.radio(metadata))
                            } else {
                                Toast
                                    .makeText(
                                        context,
                                        context.getString(R.string.spotify_track_unavailable),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                            }
                        } finally {
                            resolving = false
                        }
                    }
                }
            }

            // Same as Spotify search does with these: the app has no in-app Spotify album or
            // artist page to open, so the row hands them to Spotify itself rather than pretending.
            is SpotifySearchItem.Album ->
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/album/${item.id}")),
                    )
                }

            is SpotifySearchItem.Artist ->
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/artist/${item.id}")),
                    )
                }

            is SpotifySearchItem.Playlist -> Unit
        }
    }

    SpotifySearchItemRow(
        item = item,
        isActive = item is SpotifySearchItem.Track && mediaMetadata?.spotifyTrackId == item.id,
        isPlaying = isPlaying,
        trailingContent = {
            if (resolving) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
