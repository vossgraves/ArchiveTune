/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.spotify.SpotifyPlaybackResolver
import moe.rukamori.archivetune.spotify.SpotifySearchItem
import moe.rukamori.archivetune.ui.screens.search.SpotifySearchItemRow

/**
 * One Spotify item as a tappable row: a track resolves to its YouTube match and plays, an album or
 * artist opens in Spotify itself — the app has no in-app Spotify album or artist page to send them
 * to, and pretending otherwise would be worse than handing them over.
 *
 * Shared by the Library's Spotify sections and the History screen's Spotify source, so a Spotify
 * track looks and behaves the same wherever it turns up.
 */
@Composable
fun SpotifyPlayableRow(item: SpotifySearchItem) {
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
