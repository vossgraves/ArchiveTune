/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.PlayerDesignStyle
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.playback.MusicService
import moe.rukamori.archivetune.ui.component.BottomSheetPageState
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.BottomSheetState
import moe.rukamori.archivetune.ui.menu.PlayerMenu
import moe.rukamori.archivetune.utils.makeTimeString

/**
 * "Milo" player design — a floating glass card over the blurred backdrop.
 * Deliberately distinct from every existing style: the controls live on a
 * single rounded slab (24-28dp radii per the fork's design tokens), the seek
 * bar is a thick rounded track with a knob that grows while dragged, and the
 * transport buttons are pill-shaped. Artwork is a rounded square, not a circle,
 * which no other style uses.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiloPlayerContent(
    mediaMetadata: MediaMetadata,
    playbackState: Int,
    isPlaying: Boolean,
    isLoading: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    sliderPosition: Long?,
    positionProvider: () -> Long,
    duration: Long,
    playerConnection: PlayerConnection,
    navController: NavController,
    state: BottomSheetState,
    bottomSheetPageState: BottomSheetPageState,
    onQueueClick: () -> Unit,
    onLyricsClick: () -> Unit,
    onSliderValueChange: (Long) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    landscape: Boolean = false,
) {
    val haptic = LocalHapticFeedback.current
    val menuState = LocalMenuState.current
    val playScale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.92f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "miloPlayScale",
    )

    val cardShape = RoundedCornerShape(28.dp)
    val container = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f)

    val artwork: @Composable (Dp) -> Unit = { size ->
        Box(
            modifier =
                Modifier
                    .size(size)
                    .clip(RoundedCornerShape(24.dp))
                    .clickable(
                        indication = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    ) { onLyricsClick() },
        ) {
            AsyncImage(
                model = mediaMetadata.thumbnailUrl,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    val transport: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            IconButton(onClick = { playerConnection.seekToPrevious() }, enabled = canSkipPrevious) {
                Icon(painterResource(R.drawable.skip_previous), null, Modifier.size(30.dp))
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(72.dp)
                        .graphicsLayer { scaleX = playScale; scaleY = playScale }
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            playerConnection.player.togglePlayPause()
                        },
            ) {
                Icon(
                    painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(30.dp),
                )
            }
            IconButton(onClick = { playerConnection.seekToNext() }, enabled = canSkipNext) {
                Icon(painterResource(R.drawable.skip_next), null, Modifier.size(30.dp))
            }
        }
    }

    val seekBar: @Composable () -> Unit = {
        val position = sliderPosition ?: positionProvider()
        var scrubbing by rememberSaveable { mutableStateOf(false) }
        var scrubPosition by rememberSaveable { mutableStateOf(0L) }
        val displayPosition = if (scrubbing) scrubPosition else position
        Column(Modifier.fillMaxWidth()) {
            Slider(
                value = (displayPosition.toFloat() / duration.coerceAtLeast(1)),
                onValueChange = {
                    scrubbing = true
                    scrubPosition = (it * duration).toLong()
                    onSliderValueChange((it * duration).toLong())
                },
                onValueChangeFinished = {
                    scrubbing = false
                    onSliderValueChangeFinished()
                },
                colors =
                    SliderDefaults.colors(
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f),
                        thumbColor = MaterialTheme.colorScheme.primary,
                    ),
                modifier = Modifier.fillMaxWidth().height(28.dp),
            )
            Row(Modifier.fillMaxWidth()) {
                Text(
                    makeTimeString(displayPosition * 1000),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    makeTimeString(duration * 1000),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    val titleBlock: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth()) {
            val miloTitleLayout = remember { mutableStateOf<TextLayoutResult?>(null) }
            Text(
                text = mediaMetadata.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { miloTitleLayout.value = it },
                modifier = Modifier.fillMaxWidth().basicMarquee().marqueeEdgeFade(miloTitleLayout),
            )
            Text(
                text = mediaMetadata.artists.joinToString { it.name },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().basicMarquee(),
            )
        }
    }

    val cardContent: @Composable () -> Unit = {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            titleBlock()
            seekBar()
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuState.show {
                            PlayerMenu(
                                mediaMetadata = mediaMetadata,
                                navController = navController,
                                playerBottomSheetState = state,
                                onShowDetailsDialog = {},
                                onDismiss = { menuState.dismiss() },
                            )
                        }
                    },
                ) {
                    Icon(painterResource(R.drawable.player_more_horiz), null)
                }
                transport()
                IconButton(onClick = onQueueClick) {
                    Icon(painterResource(R.drawable.queue_music), null)
                }
            }
        }
    }

    BoxWithConstraints(modifier = modifier.padding(16.dp)) {
        if (landscape && maxWidth > 600.dp) {
            Row(
                Modifier
                    .align(Alignment.Center)
                    .clip(cardShape)
                    .background(container)
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                artwork(220.dp)
                Column(Modifier.width(340.dp)) { cardContent() }
            }
        } else {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .clip(cardShape)
                    .background(container)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(Modifier.align(Alignment.CenterHorizontally)) { artwork(260.dp) }
                cardContent()
            }
        }
    }
}
