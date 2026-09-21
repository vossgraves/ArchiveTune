/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.spotify

import kotlinx.coroutines.test.runTest
import moe.rukamori.archivetune.innertube.models.Album
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.AlbumReleaseType
import moe.rukamori.archivetune.innertube.models.SongItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SpotifyReleaseResolutionTest {
    private fun song(albumId: String) =
        SongItem(
            id = "Upb5THRHleA",
            title = "Sinners On The Moon",
            artists = emptyList(),
            album = Album(name = "Sinners On The Moon", id = albumId),
            thumbnail = "https://lh3.googleusercontent.com/x",
        )

    @Test
    fun oneTrackReleaseWithoutAlbumPageResolvesThroughTheSongIndex() = runTest {
        val browseId =
            resolveSpotifyReleaseAlbumId(
                query = "Sinners On The Moon Sam Feldt",
                searchAlbum = { null },
                searchSong = { song("MPREb_IQWedkSYpdu") },
            )

        assertEquals("MPREb_IQWedkSYpdu", browseId)
    }

    @Test
    fun albumPageIsPreferredAndTheSongIndexIsNotAsked() = runTest {
        var songIndexAsked = false

        val browseId =
            resolveSpotifyReleaseAlbumId(
                query = "BULLY Kanye West",
                searchAlbum = {
                    AlbumItem(
                        browseId = "MPREb_zj6sl6cWjTN",
                        playlistId = "OLAK5uy_n5jKc0HVWgKUXaLuFXiCHNROpUcnjncIA",
                        title = "BULLY",
                        artists = emptyList(),
                        thumbnail = "https://lh3.googleusercontent.com/y",
                        releaseType = AlbumReleaseType.ALBUM,
                    )
                },
                searchSong = {
                    songIndexAsked = true
                    song("MPREb_IQWedkSYpdu")
                },
            )

        assertEquals("MPREb_zj6sl6cWjTN", browseId)
        assertFalse(songIndexAsked)
    }
}
