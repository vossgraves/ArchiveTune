/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.constants

/**
 * The Library's sections.
 *
 * SPOTIFY used to be one of them — a tab holding Spotify playlists and nothing else. Spotify is a
 * SOURCE now, not a section: every section offers YTM/Spotify pills and shows that service's
 * version of itself. See [LibrarySource]. A stored SPOTIFY value for the default-chip preference
 * falls back to the default, which `toEnum` already does for any unknown name.
 */
enum class LibraryFilter {
    SONGS,
    ARTISTS,
    ALBUMS,
    PLAYLISTS,
    LIBRARY,
}
