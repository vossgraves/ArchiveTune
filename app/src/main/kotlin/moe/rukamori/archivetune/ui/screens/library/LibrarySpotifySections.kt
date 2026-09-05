/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.spotify.SpotifyLibraryViewModel
import moe.rukamori.archivetune.spotify.SpotifySearchItem
import moe.rukamori.archivetune.ui.component.ExpressivePullToRefreshBox
import moe.rukamori.archivetune.ui.component.SpotifyPlayableRow

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
                SpotifyPlayableRow(item)
            }
        }
    }
}
