/*
 * ArchiveTune (2026)
 * © vossgraves — github.com/vossgraves
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import android.content.Context
import moe.rukamori.archivetune.canvas.models.CanvasArtwork
import moe.rukamori.archivetune.constants.AlbumCanvasEnabledKey
import moe.rukamori.archivetune.ui.player.resolveCanvasArtworkForPlayback
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import moe.rukamori.archivetune.utils.isLowDataModeActive
import java.util.Locale

/**
 * Resolves the looping animated canvas a playlist page plays behind its header.
 *
 * A playlist has no artwork of its own to look a canvas up by, so the first song stands in for it —
 * the same trade the album page makes with its first track.
 *
 * The loop is gated on its own preference rather than the player-level canvas switch: it starts as
 * soon as the page opens, whether or not anything is playing, so it is a separate cost and a
 * separate choice.
 *
 * It renders through `MediaDetailHero`, which is where both playlist heroes meet, so a playlist
 * page only has to pass the artwork along — that header's Apple Music Experience branch
 * deliberately drops the backdrop, which is why the loop is a classic-hero affair.
 */
internal suspend fun fetchPlaylistCanvasArtwork(
    context: Context,
    firstSongId: String?,
    firstSongTitle: String?,
    firstSongArtist: String?,
    firstSongAlbumTitle: String? = null,
): CanvasArtwork? {
    if (firstSongId.isNullOrBlank() || firstSongTitle.isNullOrBlank()) return null

    if (!context.dataStore.get(AlbumCanvasEnabledKey, true)) return null

    if (context.isLowDataModeActive()) return null

    val country = Locale.getDefault().country
    val storefront = if (country.length == 2) country.lowercase(Locale.ROOT) else "us"

    return resolveCanvasArtworkForPlayback(
        mediaId = firstSongId,
        songTitleRaw = firstSongTitle,
        artistNameRaw = firstSongArtist.orEmpty(),
        storefront = storefront,
        requireVertical = false,
        allowNetwork = true,
        albumTitle = firstSongAlbumTitle,
        trySpotifyCanvas = true,
    )
}
