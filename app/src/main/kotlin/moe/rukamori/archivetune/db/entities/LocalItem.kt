/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 * Portions © vossgraves — github.com/vossgraves
 */

package moe.rukamori.archivetune.db.entities

import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.EpisodeItem
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.PodcastItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.YTItem

sealed class LocalItem {
    abstract val id: String
    abstract val title: String
    abstract val thumbnailUrl: String?
}

/** Type-prefixed key so a song cannot collide with an album/artist/playlist of the same id. */
fun LocalItem.lazyKey(): String =
    when (this) {
        is Song -> "song_$id"
        is Album -> "album_$id"
        is Artist -> "artist_$id"
        is Playlist -> "playlist_$id"
    }

fun YTItem.lazyKey(): String =
    when (this) {
        is SongItem -> "song_$id"
        is AlbumItem -> "album_$id"
        is ArtistItem -> "artist_$id"
        is PlaylistItem -> "playlist_$id"
        is PodcastItem -> "podcast_$id"
        is EpisodeItem -> "episode_$id"
    }
