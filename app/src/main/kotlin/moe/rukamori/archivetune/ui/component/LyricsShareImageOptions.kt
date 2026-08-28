/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import moe.rukamori.archivetune.R
import kotlin.math.roundToInt

enum class LyricsShareAspectRatio(
    @StringRes val labelRes: Int,
    val exportWidth: Int,
    val exportHeight: Int,
) {
    Square(
        labelRes = R.string.lyrics_share_layout_square,
        exportWidth = 1080,
        exportHeight = 1080,
    ),
    Portrait(
        labelRes = R.string.lyrics_share_layout_portrait,
        exportWidth = 1080,
        exportHeight = 1350,
    ),
    Story(
        labelRes = R.string.lyrics_share_layout_story,
        exportWidth = 1080,
        exportHeight = 1920,
    ),
    ;

    val previewAspectRatio: Float
        get() = exportWidth.toFloat() / exportHeight.toFloat()
}

@Immutable
data class LyricsShareImageOptions(
    val aspectRatio: LyricsShareAspectRatio = LyricsShareAspectRatio.Square,
    val blurRadius: Float = 24f,
    val dimAmount: Float = 1f,
    val showArtwork: Boolean = true,
    /**
     * When true, the share image is rendered in the "vinyl" layout — the album
     * cover sits on the left, a vinyl record (with the song title and artist on
     * its center label) peeks out from behind it on the right, and the lyrics
     * text is omitted entirely. Matches the "MD Vinyl" reference screenshot the
     * user uploaded. The other options (blurRadius, dimAmount, aspectRatio,
     * showArtwork) are ignored when this is true — vinyl mode has its own fixed
     * layout.
     */
    val vinylMode: Boolean = false,
) {
    val sanitizedBlurRadius: Float
        get() = blurRadius.coerceIn(0f, 48f)

    val sanitizedDimAmount: Float
        get() = dimAmount.coerceIn(0.6f, 1.6f)

    val previewBlurRadius: Int
        get() = sanitizedBlurRadius.roundToInt().coerceIn(0, 48)
}

@Immutable
data class LyricsSharePayload(
    val lyricsText: String,
    val songTitle: String,
    val artists: String,
)
