/*
 * ArchiveTune (2026)
 * © ArchiveTuneFork contributors — github.com/vossgraves/ArchiveTune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package app.atf.media.ui.screens.search

import android.content.Intent
import android.widget.Toast
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.atf.media.LocalPlayerAwareWindowInsets
import app.atf.media.LocalPlayerConnection
import app.atf.media.R
import app.atf.media.constants.AppBarHeight
import app.atf.media.extensions.togglePlayPause
import app.atf.media.playback.queues.YouTubeQueue
import app.atf.media.spotify.SpotifyPlaybackResolver
import app.atf.media.spotify.SpotifySearchItem
import app.atf.media.ui.component.ChipsRow
import app.atf.media.ui.component.EmptyPlaceholder
import app.atf.media.ui.component.LocalMenuState
import app.atf.media.viewmodels.SpotifySearchViewModel

private enum class SpotifySearchFilter {
    ALL,
    TRACKS,
    ALBUMS,
    ARTISTS,
    PLAYLISTS,
}

@Composable
internal fun SpotifyOnlineSearchResult(
    navController: NavController,
    viewModel: SpotifySearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current
    val mediaMetadata by playerConnection?.mediaMetadata?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(null) }
    val isPlaying by playerConnection?.isPlaying?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(false) }
    val lazyListState = rememberLazyListState()
    var filter by rememberSaveable { mutableStateOf(SpotifySearchFilter.ALL) }

    val visibleItems =
        remember(state.items, filter) {
            state.items.filter { item ->
                when (filter) {
                    SpotifySearchFilter.ALL -> true
                    SpotifySearchFilter.TRACKS -> item is SpotifySearchItem.Track
                    SpotifySearchFilter.ALBUMS -> item is SpotifySearchItem.Album
                    SpotifySearchFilter.ARTISTS -> item is SpotifySearchItem.Artist
                    SpotifySearchFilter.PLAYLISTS -> item is SpotifySearchItem.Playlist
                }
            }
        }

    LaunchedEffect(lazyListState, state.hasMore, state.isLoading) {
        if (!state.hasMore) return@LaunchedEffect
        snapshotFlow { lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastIndex ->
                if (lastIndex != null && lastIndex >= visibleItems.lastIndex - 2) {
                    viewModel.loadMore()
                }
            }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding())
                    .padding(top = AppBarHeight),
        ) {
            ChipsRow(
                chips =
                    listOf(
                        SpotifySearchFilter.ALL to stringResource(R.string.filter_all),
                        SpotifySearchFilter.TRACKS to stringResource(R.string.filter_songs),
                        SpotifySearchFilter.ALBUMS to stringResource(R.string.filter_albums),
                        SpotifySearchFilter.ARTISTS to stringResource(R.string.filter_artists),
                        SpotifySearchFilter.PLAYLISTS to stringResource(R.string.filter_playlists),
                    ),
                currentValue = filter,
                onValueUpdate = { filter = it },
            )
        }

        when {
            state.isLoading && state.items.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            state.errorMessage != null && state.items.isEmpty() -> {
                EmptyPlaceholder(
                    icon = R.drawable.spotify_icon,
                    text = state.errorMessage ?: stringResource(R.string.no_results_found),
                    modifier = Modifier.fillMaxSize(),
                )
            }

            visibleItems.isEmpty() -> {
                EmptyPlaceholder(
                    icon = R.drawable.search,
                    text = stringResource(R.string.no_results_found),
                    modifier = Modifier.fillMaxSize(),
                )
            }

            else -> {
                LazyColumn(
                    state = lazyListState,
                    contentPadding =
                        LocalPlayerAwareWindowInsets.current
                            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                            .add(WindowInsets(top = 8.dp))
                            .asPaddingValues(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    item(key = "spotify_result_label") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.search_spotify),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    itemsIndexed(
                        items = visibleItems,
                        key = { _, item -> item.key },
                        contentType = { _, item -> item::class },
                    ) { _, item ->
                        SpotifySearchResultRow(
                            item = item,
                            navController = navController,
                            mediaMetadata = mediaMetadata,
                            isPlaying = isPlaying,
                            playerConnection = playerConnection,
                            coroutineScope = coroutineScope,
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        )
                    }
                    if (state.isLoading) {
                        item(key = "spotify_loading_more") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpotifySearchResultRow(
    playerConnection: app.atf.media.playback.PlayerConnection?,
    item: SpotifySearchItem,
    navController: NavController,
    mediaMetadata: app.atf.media.models.MediaMetadata?,
    isPlaying: Boolean,
    coroutineScope: CoroutineScope,
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    var resolving by remember(item.key) { mutableStateOf(false) }
    val openExternal = {
        val type =
            when (item) {
                is SpotifySearchItem.Album -> "album"
                is SpotifySearchItem.Artist -> "artist"
                is SpotifySearchItem.Playlist -> "playlist"
                is SpotifySearchItem.Track -> "track"
            }
        runCatching {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://open.spotify.com/$type/${item.id}"),
                ),
            )
        }
    }

    val onClick: () -> Unit = {
        when (item) {
            is SpotifySearchItem.Track -> {
                val track = item.value
                if (mediaMetadata?.spotifyTrackId == track.id && playerConnection != null) {
                    playerConnection.player.togglePlayPause()
                } else if (playerConnection != null && !resolving) {
                    resolving = true
                    menuState.dismiss()
                    coroutineScope.launch {
                        try {
                            val metadata =
                                withContext(Dispatchers.IO) {
                                    SpotifyPlaybackResolver.resolveToMetadata(track)
                                }
                            if (metadata != null) {
                                playerConnection.playQueue(YouTubeQueue.radio(metadata))
                            } else {
                                Toast.makeText(
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

            is SpotifySearchItem.Playlist -> navController.navigate("spotify_playlist/${item.id}")
            is SpotifySearchItem.Album, is SpotifySearchItem.Artist -> openExternal()
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
