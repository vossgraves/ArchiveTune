/*
 * ArchiveTune (2026)
 * © ArchiveTuneFork contributors — github.com/vossgraves/ArchiveTune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package app.atf.media.ui.screens.search

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.atf.media.R
import app.atf.media.constants.ListThumbnailSize
import app.atf.media.constants.ThumbnailCornerRadius
import app.atf.media.spotify.SpotifySearchItem
import app.atf.media.spotify.models.SpotifyTrack
import app.atf.media.spotify.models.SpotifyAlbum
import app.atf.media.spotify.models.SpotifyArtist
import app.atf.media.spotify.models.SpotifyPlaylist
import app.atf.media.ui.component.ItemThumbnail
import app.atf.media.ui.component.ListItem
import app.atf.media.ui.component.SpotifyTrackListItem
import app.atf.media.utils.joinByBullet

@Composable
internal fun SpotifySearchItemRow(
    item: SpotifySearchItem,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    when (item) {
        is SpotifySearchItem.Track -> {
            SpotifyTrackListItem(
                track = item.value,
                isActive = isActive,
                isPlaying = isPlaying,
                trailingContent = {
                    trailingContent()
                    SpotifyProviderIcon()
                },
                modifier = modifier,
            )
        }

        is SpotifySearchItem.Album -> {
            val album = item.value
            SpotifyCatalogRow(
                title = album.name,
                subtitle = albumSubtitle(album),
                thumbnailUrl = album.images.firstOrNull()?.url,
                modifier = modifier,
                trailingContent = trailingContent,
            )
        }

        is SpotifySearchItem.Artist -> {
            val artist = item.value
            SpotifyCatalogRow(
                title = artist.name,
                subtitle = stringResource(R.string.artist_subtitle),
                thumbnailUrl = artist.images.firstOrNull()?.url,
                modifier = modifier,
                trailingContent = trailingContent,
            )
        }

        is SpotifySearchItem.Playlist -> {
            val playlist = item.value
            SpotifyCatalogRow(
                title = playlist.name,
                subtitle = playlistSubtitle(playlist),
                thumbnailUrl = playlist.images.firstOrNull()?.url,
                modifier = modifier,
                trailingContent = trailingContent,
            )
        }
    }
}

@Composable
private fun SpotifyCatalogRow(
    title: String,
    subtitle: String?,
    thumbnailUrl: String?,
    modifier: Modifier,
    trailingContent: @Composable RowScope.() -> Unit,
) {
    ListItem(
        title = title,
        subtitle = subtitle,
        thumbnailContent = {
            ItemThumbnail(
                thumbnailUrl = thumbnailUrl,
                isActive = false,
                isPlaying = false,
                shape = RoundedCornerShape(ThumbnailCornerRadius),
                placeholderIconRes = R.drawable.album,
                modifier = Modifier.size(ListThumbnailSize),
            )
        },
        trailingContent = {
            trailingContent()
            SpotifyProviderIcon()
        },
        modifier = modifier,
    )
}

@Composable
private fun SpotifyProviderIcon() {
    Icon(
        painter = painterResource(R.drawable.spotify_icon),
        contentDescription = stringResource(R.string.spotify_account),
        modifier = Modifier.size(18.dp),
    )
}

private fun albumSubtitle(album: SpotifyAlbum): String? =
    joinByBullet(
        album.artists.joinToString { it.name },
        album.releaseDate?.take(4),
    )

private fun playlistSubtitle(playlist: SpotifyPlaylist): String? =
    joinByBullet(
        playlist.owner?.displayName,
        playlist.tracks?.total?.takeIf { it > 0 }?.let { count -> "$count ${if (count == 1) "track" else "tracks"}" },
    )

internal fun SpotifySearchItem.queryText(): String =
    when (this) {
        is SpotifySearchItem.Track -> {
            val track: SpotifyTrack = value
            listOf(track.artists.firstOrNull()?.name, track.name)
                .filter { !it.isNullOrBlank() }
                .joinToString(" ")
        }

        is SpotifySearchItem.Album -> value.name
        is SpotifySearchItem.Artist -> value.name
        is SpotifySearchItem.Playlist -> value.name
    }
