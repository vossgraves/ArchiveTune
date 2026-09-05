/*
 * YumaPlayer (2026) | Modified work by MuwMx
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify

import androidx.compose.runtime.Immutable
import moe.rukamori.archivetune.spotify.models.SpotifyHomeFeedItem
import moe.rukamori.archivetune.spotify.models.SpotifyTrack

/**
 * One shelf on the Spotify home.
 *
 * There are exactly two layouts — a grid of track rows, or a row of cards — so the section is one
 * of two things rather than a single class with a type enum and four lists of which three are
 * always empty. That shape forced every shelf to be homogeneous: the old code counted the item
 * kinds, kept the majority, and threw the rest away, so a mixed shelf lost tiles AND dispatched
 * every surviving click through the majority's handler — which is how tapping a tile could open
 * somebody else's playlist.
 *
 * [Cards] therefore keeps the feed's items as they arrived, each carrying its own kind, and the
 * click is decided per item.
 */
@Immutable
sealed interface SpotifyHomeSection {
    val title: String

    /** Track shelves come from `topTracks`, which returns one kind, so this list stays typed. */
    @Immutable
    data class Tracks(
        override val title: String,
        val tracks: List<SpotifyTrack>,
    ) : SpotifyHomeSection

    /** Playlists, albums and artists, in the order the feed sent them. */
    @Immutable
    data class Cards(
        override val title: String,
        val items: List<SpotifyHomeFeedItem>,
    ) : SpotifyHomeSection
}
