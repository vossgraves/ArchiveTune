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

package moe.rukamori.archivetune.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalCenteredHeroCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.spotify.SpotifyHomeSection
import moe.rukamori.archivetune.spotify.SpotifyMapper
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import moe.rukamori.archivetune.ui.component.pressScaleClickable

/**
 * The Spotify home's answer to the YouTube home's Quick Picks.
 *
 * Spotify's feed has no quick-picks shelf of its own, so the first track shelf it does send — the
 * personalised one, whatever the account's locale calls it — is promoted out of the ordinary
 * section list and drawn as the same swipeable hero carousel the Rukamori YouTube home uses. It is
 * deliberately the same component rather than a lookalike: the two homes should slide identically.
 *
 * The promoted shelf is then skipped where it would otherwise have appeared, so nothing is shown
 * twice. [pickQuickPicksSection] is what decides, and the caller filters on the same value.
 */
fun pickQuickPicksSection(sections: List<SpotifyHomeSection>): SpotifyHomeSection.Tracks? =
    sections.filterIsInstance<SpotifyHomeSection.Tracks>().firstOrNull { it.tracks.size >= MinimumHeroTracks }

/** Below this a carousel reads as a mistake rather than a shelf, so the section stays in place. */
private const val MinimumHeroTracks = 3

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SpotifyQuickPicksCarousel(
    tracks: List<SpotifyTrack>,
    activeTrackId: String?,
    isPlaying: Boolean,
    resolvingItemKey: String?,
    onTrackClick: (SpotifyTrack) -> Unit,
    modifier: Modifier = Modifier,
) {
    val distinctTracks = remember(tracks) { tracks.distinctBy { it.id } }
    if (distinctTracks.isEmpty()) return

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val heroHeight =
            when {
                maxWidth >= 840.dp -> 380.dp
                maxWidth >= 600.dp -> 356.dp
                else -> 332.dp
            }
        val heroMaxWidth =
            (maxWidth - 48.dp)
                .coerceAtLeast(232.dp)
                .coerceAtMost(440.dp)
        val density = LocalDensity.current
        val requestWidthPx = with(density) { heroMaxWidth.roundToPx().coerceAtLeast(1) }
        val requestHeightPx = with(density) { heroHeight.roundToPx().coerceAtLeast(1) }

        HorizontalCenteredHeroCarousel(
            state = rememberCarouselState { distinctTracks.size },
            maxItemWidth = heroMaxWidth,
            itemSpacing = 10.dp,
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(heroHeight),
        ) { index ->
            val track = distinctTracks[index]
            val isActive = track.id == activeTrackId
            val context = LocalContext.current
            val imageRequest =
                remember(track.id, requestWidthPx, requestHeightPx) {
                    ImageRequest
                        .Builder(context)
                        .data(SpotifyMapper.getTrackThumbnail(track))
                        .size(Size(requestWidthPx, requestHeightPx))
                        .crossfade(true)
                        .build()
                }

            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(MaterialTheme.shapes.extraLarge)
                        .focusable()
                        .pressScaleClickable(onClick = { onTrackClick(track) }),
            ) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )

                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    0.48f to Color.Black.copy(alpha = 0.08f),
                                    1f to Color.Black.copy(alpha = 0.84f),
                                ),
                            ),
                )

                if (resolvingItemKey == "track:${track.id}") {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(18.dp)
                                .size(24.dp),
                    )
                } else if (isActive && isPlaying) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = CircleShape,
                        tonalElevation = 2.dp,
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(14.dp)
                                .size(36.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.volume_up),
                                contentDescription = null,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                    }
                }

                Column(
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                ) {
                    Text(
                        text = track.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = track.artists.joinToString { it.name },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
