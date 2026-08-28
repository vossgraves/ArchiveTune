/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.models.MediaMetadata

/**
 * Renders a comma-separated artist line where **each artist name is individually tappable**.
 *
 * This is a generic, single-purpose leaf primitive (think "a smarter Text") — it owns the only
 * tricky bit of the artist line: mapping a tap position back to the artist span underneath it.
 * It carries no style opinions of its own, so every player style can reuse it without this file
 * ever needing changes when a new style is added.
 *
 * Note on marquee: callers typically apply [androidx.compose.foundation.basicMarquee] via
 * [modifier]. Hit-testing uses the static [TextLayoutResult], so a tap landing during the marquee
 * scroll resolves against the un-scrolled layout. This matches the pre-existing behavior of the
 * classic player and is acceptable for the short, rarely-scrolling artist line.
 */
@Composable
fun ClickableArtists(
    artists: List<MediaMetadata.Artist>,
    onArtistClick: (artistId: String) -> Unit,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    onLongClick: (() -> Unit)? = null,
    artistThreshold: Int = 24,
    fadeWidth: Dp = 24.dp,
) {
    val annotatedString =
        remember(artists) {
            buildAnnotatedString {
                artists.forEachIndexed { index, artist ->
                    pushStringAnnotation(tag = "artist_${artist.id.orEmpty()}", annotation = artist.id.orEmpty())
                    append(artist.name)
                    pop()
                    if (index != artists.lastIndex) append(", ")
                }
            }
        }

    // Shared layout state: drives tap detection; fade is gated by length and
    // lives on the BOX viewport, not the Text, so the gradient stays fixed at
    // the box edges while the marquee scrolls underneath.
    val layoutState = remember { mutableStateOf<TextLayoutResult?>(null) }
    val layoutResult = layoutState.value
    val shouldFade = annotatedString.text.length > artistThreshold

    Box(
        modifier = (if (shouldFade) modifier.viewportEdgeFade(fadeWidth) else modifier).clipToBounds(),
    ) {
        Text(
            text = annotatedString,
            style = style,
            color = color,
            textAlign = textAlign,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { layoutState.value = it },
            modifier =
                Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE)
                    .pointerInput(annotatedString) {
                    detectTapGestures(
                        onTap = { offset ->
                            val layout = layoutResult ?: return@detectTapGestures
                            val position = layout.getOffsetForPosition(offset)
                            annotatedString
                                .getStringAnnotations(position, position)
                                .firstOrNull()
                                ?.let { onArtistClick(it.item) }
                        },
                        onLongPress = onLongClick?.let { handler -> { handler() } },
                    )
                },
        )
    }
}
