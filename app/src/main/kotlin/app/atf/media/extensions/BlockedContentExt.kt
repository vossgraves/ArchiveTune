/*
 * ArchiveTune (2026)
 * © ArchiveTuneFork contributors — github.com/vossgraves/ArchiveTune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package app.atf.media.extensions

import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.innertube.pages.BrowseResult

fun <T : YTItem> List<T>.filterBlockedArtists(blockedArtistIds: Set<String>): List<T> {
    if (blockedArtistIds.isEmpty()) return this

    return filter { item ->
        when (item) {
            is ArtistItem -> item.id !in blockedArtistIds
            is SongItem -> item.artists.none { it.id in blockedArtistIds }
            is AlbumItem -> item.artists.orEmpty().none { it.id in blockedArtistIds }
            is PlaylistItem -> item.author?.id !in blockedArtistIds
        }
    }
}

/**
 * Filters out songs whose IDs appear in [blockedSongIds]. The "Don't recommend this song again"
 * overflow menu item populates that set. Songs are filtered by their own ID — the artist is
 * NOT blocked, only this specific track, so the user can keep discovering other songs from
 * the same artist.
 *
 * Non-[SongItem] items (artists, albums, playlists) pass through unchanged.
 */
fun <T : YTItem> List<T>.filterBlockedSongs(blockedSongIds: Set<String>): List<T> {
    if (blockedSongIds.isEmpty()) return this
    return filter { item ->
        when (item) {
            is SongItem -> item.id !in blockedSongIds
            else -> true
        }
    }
}

fun BrowseResult.filterBlockedArtists(blockedArtistIds: Set<String>): BrowseResult {
    if (blockedArtistIds.isEmpty()) return this

    return copy(
        items =
            items.mapNotNull { section ->
                section.copy(
                    items =
                        section.items
                            .filterBlockedArtists(blockedArtistIds)
                            .ifEmpty { return@mapNotNull null },
                )
            },
    )
}

fun BrowseResult.filterBlockedSongs(blockedSongIds: Set<String>): BrowseResult {
    if (blockedSongIds.isEmpty()) return this

    return copy(
        items =
            items.mapNotNull { section ->
                section.copy(
                    items =
                        section.items
                            .filterBlockedSongs(blockedSongIds)
                            .ifEmpty { return@mapNotNull null },
                )
            },
    )
}
