/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.constants

/**
 * Which service the Library's sections read from.
 *
 * Spotify used to be a tab of its own, sitting between Playlists and Songs and holding only
 * playlists — so a Spotify playlist was three taps from a YouTube one and there was nowhere for
 * Spotify songs, artists or albums to go at all. It is a per-section source now: each section
 * offers the same pair of pills and shows that service's version of itself.
 *
 * One choice shared by every section rather than one per section. The Library then reads as one
 * service at a time, the way the Home tab does, and switching once switches everything — which is
 * what someone flicking between their two libraries actually wants.
 */
enum class LibrarySource {
    YTM,
    SPOTIFY,
}
