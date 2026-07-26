/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * "Apple Music" player design: edge-to-edge artwork on top with a blurred continuation of the
 * artwork behind the lower controls (progressive-blur look), bold white title/artist with star and
 * "more" chips, a thin scrubber with elapsed/-remaining times, bare oversized transport glyphs, a
 * flat volume slider, and a bottom lyrics / output / queue icon row. Everything is tinted by the
 * artwork itself (no palette extraction needed — the blur provides the color).
 */

package moe.rukamori.archivetune.ui.player

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.ripple
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player.STATE_ENDED
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.component.BottomSheetPageState
import moe.rukamori.archivetune.ui.component.BottomSheetState
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.menu.PlayerMenu
import moe.rukamori.archivetune.ui.utils.ShowMediaInfo
import moe.rukamori.archivetune.ui.utils.highRes
import moe.rukamori.archivetune.utils.makeTimeString
import moe.rukamori.archivetune.utils.rememberLowDataModeActive

private val AppleMusicContentPadding = 28.dp
private val AppleMusicChipSize = 34.dp
private val AppleMusicTransportIconSize = 44.dp
private val AppleMusicPlayPauseIconSize = 52.dp
private val AppleMusicBottomIconSize = 24.dp

@Composable
fun AppleMusicPlayerContent(
    mediaMetadata: MediaMetadata,
    playbackState: Int,
    isPlaying: Boolean,
    isLoading: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    sliderPosition: Long?,
    position: Long,
    duration: Long,
    playerConnection: PlayerConnection,
    navController: NavController,
    state: BottomSheetState,
    bottomSheetPageState: BottomSheetPageState,
    currentSongLiked: Boolean,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    canvasPrimaryUrl: String?,
    canvasFallbackUrl: String?,
    contentBottomPadding: Dp,
    onQueueClick: () -> Unit,
    onLyricsClick: () -> Unit,
    onSliderValueChange: (Long) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    landscape: Boolean = false,
) {
    val baseArtworkUrl = mediaMetadata.thumbnailUrl?.highRes()
    val thumbnailSwapState =
        rememberThumbnailSwapState(
            videoId = mediaMetadata.id,
            ytmUrl = baseArtworkUrl,
            lowDataMode = rememberLowDataModeActive(),
            isMusicVideo = mediaMetadata.isMusicVideo,
        )
    val artworkUrl = thumbnailSwapState.displayUrl
    val artworkRequest = rememberOfflineArtworkImageRequest(artworkUrl)
    val titleActions = rememberPlayerTitleActions(mediaMetadata, navController, state)
    val menuState = LocalMenuState.current
    val context = LocalContext.current

    val onPlayPauseClick = {
        if (playbackState == STATE_ENDED) {
            playerConnection.player.seekTo(0, 0)
            playerConnection.player.playWhenReady = true
        } else {
            playerConnection.player.togglePlayPause()
        }
    }
    val onMoreClick = {
        menuState.show {
            PlayerMenu(
                mediaMetadata = mediaMetadata,
                navController = navController,
                playerBottomSheetState = state,
                onShowDetailsDialog = {
                    mediaMetadata.id.let {
                        bottomSheetPageState.show {
                            ShowMediaInfo(it)
                        }
                    }
                },
                onDismiss = menuState::dismiss,
            )
        }
    }
    val onOutputClick = {
        // Best-effort media output switcher (the "AirPlay" slot): the system output panel where
        // available, else the volume panel.
        val launched =
            runCatching {
                context.startActivity(Intent("com.android.settings.panel.action.MEDIA_OUTPUT"))
            }.isSuccess
        if (!launched) {
            runCatching {
                context.startActivity(Intent(Settings.Panel.ACTION_VOLUME))
            }
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val sharpArtworkHeight = if (landscape) maxHeight else maxHeight * 0.55f

        // 1. Blurred artwork fills the whole player as the base layer. On Android 12+ this is a
        // real gaussian blur; below that the scrim alone carries the contrast.
        AsyncImage(
            model = artworkRequest ?: artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .matchParentSize()
                    .then(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            Modifier.blur(72.dp)
                        } else {
                            Modifier
                        },
                    ).graphicsLayer {
                        scaleX = 1.2f
                        scaleY = 1.2f
                    },
        )
        // Contrast scrim over the blur, heavier at the bottom where the controls sit.
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.10f else 0.35f),
                            0.55f to Color.Black.copy(alpha = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.28f else 0.55f),
                            1f to Color.Black.copy(alpha = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.45f else 0.7f),
                        ),
                    ),
        )

        if (landscape) {
            Row(Modifier.fillMaxSize()) {
                AppleMusicSharpArtwork(
                    artworkRequest = artworkRequest,
                    artworkUrl = artworkUrl,
                    canvasPrimaryUrl = canvasPrimaryUrl,
                    canvasFallbackUrl = canvasFallbackUrl,
                    isPlaying = isPlaying,
                    fadeBottom = false,
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                )
                AppleMusicControlsColumn(
                    mediaMetadata = mediaMetadata,
                    isPlaying = isPlaying,
                    isLoading = isLoading,
                    canSkipPrevious = canSkipPrevious,
                    canSkipNext = canSkipNext,
                    sliderPosition = sliderPosition,
                    position = position,
                    duration = duration,
                    playerConnection = playerConnection,
                    currentSongLiked = currentSongLiked,
                    volume = volume,
                    onVolumeChange = onVolumeChange,
                    titleActions = titleActions,
                    onPlayPauseClick = onPlayPauseClick,
                    onMoreClick = onMoreClick,
                    onOutputClick = onOutputClick,
                    onQueueClick = onQueueClick,
                    onLyricsClick = onLyricsClick,
                    onSliderValueChange = onSliderValueChange,
                    onSliderValueChangeFinished = onSliderValueChangeFinished,
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(bottom = contentBottomPadding),
                )
            }
        } else {
            // 2. Sharp artwork occupies the top, fading into the blurred continuation below it.
            AppleMusicSharpArtwork(
                artworkRequest = artworkRequest,
                artworkUrl = artworkUrl,
                canvasPrimaryUrl = canvasPrimaryUrl,
                canvasFallbackUrl = canvasFallbackUrl,
                isPlaying = isPlaying,
                fadeBottom = true,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(sharpArtworkHeight)
                        .align(Alignment.TopCenter),
            )

            // 3. Controls anchored to the bottom.
            AppleMusicControlsColumn(
                mediaMetadata = mediaMetadata,
                isPlaying = isPlaying,
                isLoading = isLoading,
                canSkipPrevious = canSkipPrevious,
                canSkipNext = canSkipNext,
                sliderPosition = sliderPosition,
                position = position,
                duration = duration,
                playerConnection = playerConnection,
                currentSongLiked = currentSongLiked,
                volume = volume,
                onVolumeChange = onVolumeChange,
                titleActions = titleActions,
                onPlayPauseClick = onPlayPauseClick,
                onMoreClick = onMoreClick,
                onOutputClick = onOutputClick,
                onQueueClick = onQueueClick,
                onLyricsClick = onLyricsClick,
                onSliderValueChange = onSliderValueChange,
                onSliderValueChangeFinished = onSliderValueChangeFinished,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.5f)
                        .align(Alignment.BottomCenter)
                        .padding(bottom = contentBottomPadding),
            )
        }
    }
}

@Composable
private fun AppleMusicSharpArtwork(
    artworkRequest: coil3.request.ImageRequest?,
    artworkUrl: String?,
    canvasPrimaryUrl: String?,
    canvasFallbackUrl: String?,
    isPlaying: Boolean,
    fadeBottom: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier.then(
                if (fadeBottom) {
                    // Fade the sharp artwork's lower edge into the blurred layer beneath.
                    Modifier
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                brush =
                                    Brush.verticalGradient(
                                        0.62f to Color.Black,
                                        1f to Color.Transparent,
                                    ),
                                blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                            )
                        }
                } else {
                    Modifier
                },
            ),
    ) {
        AsyncImage(
            model = artworkRequest ?: artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        if (!canvasPrimaryUrl.isNullOrBlank() || !canvasFallbackUrl.isNullOrBlank()) {
            CanvasArtworkPlayer(
                primaryUrl = canvasPrimaryUrl,
                fallbackUrl = canvasFallbackUrl,
                isPlaying = isPlaying,
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

@Composable
private fun AppleMusicControlsColumn(
    mediaMetadata: MediaMetadata,
    isPlaying: Boolean,
    isLoading: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    sliderPosition: Long?,
    position: Long,
    duration: Long,
    playerConnection: PlayerConnection,
    currentSongLiked: Boolean,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    titleActions: PlayerTitleActions,
    onPlayPauseClick: () -> Unit,
    onMoreClick: () -> Unit,
    onOutputClick: () -> Unit,
    onQueueClick: () -> Unit,
    onLyricsClick: () -> Unit,
    onSliderValueChange: (Long) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = AppleMusicContentPadding),
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        // Title / artist row with star + more chips.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = mediaMetadata.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = titleActions.onTitleClick,
                        ),
                )
                Text(
                    text = mediaMetadata.artists.joinToString { it.name },
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.64f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            mediaMetadata.artists.firstOrNull()?.id?.let(titleActions.onArtistClick)
                        },
                )
            }
            Spacer(Modifier.width(12.dp))
            AppleMusicChip(
                iconRes = R.drawable.star,
                tint = if (currentSongLiked) Color(0xFFFFD60A) else Color.White,
                contentDescription = null,
                onClick = playerConnection::toggleLike,
            )
            Spacer(Modifier.width(10.dp))
            AppleMusicChip(
                iconRes = R.drawable.more_horiz,
                tint = Color.White,
                contentDescription = null,
                onClick = onMoreClick,
            )
        }

        // Thin scrubber + elapsed / -remaining.
        Column {
            AppleMusicSeekBar(
                position = sliderPosition ?: position,
                duration = duration,
                onScrub = onSliderValueChange,
                onScrubFinished = onSliderValueChangeFinished,
            )
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                Text(
                    text = makeTimeString(sliderPosition ?: position),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.55f),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "-" + makeTimeString((duration - (sliderPosition ?: position)).coerceAtLeast(0L)),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.55f),
                )
            }
        }

        // Bare transport glyphs.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppleMusicTransportButton(
                iconRes = R.drawable.fast_forward,
                enabled = canSkipPrevious,
                mirrored = true,
                iconSize = AppleMusicTransportIconSize,
                onClick = playerConnection::seekToPrevious,
            )
            Box(contentAlignment = Alignment.Center) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(AppleMusicPlayPauseIconSize),
                        strokeWidth = 3.dp,
                    )
                } else {
                    AppleMusicTransportButton(
                        iconRes = if (isPlaying) R.drawable.pause else R.drawable.play,
                        enabled = true,
                        mirrored = false,
                        iconSize = AppleMusicPlayPauseIconSize,
                        onClick = onPlayPauseClick,
                    )
                }
            }
            AppleMusicTransportButton(
                iconRes = R.drawable.fast_forward,
                enabled = canSkipNext,
                mirrored = false,
                iconSize = AppleMusicTransportIconSize,
                onClick = playerConnection::seekToNext,
            )
        }

        // Flat volume slider with speaker glyphs.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                painter = painterResource(R.drawable.volume_off),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.55f),
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(12.dp))
            AppleMusicVolumeSlider(
                volume = volume,
                onVolumeChange = onVolumeChange,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Icon(
                painter = painterResource(R.drawable.volume_up),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.55f),
                modifier = Modifier.size(18.dp),
            )
        }

        // Bottom action row: lyrics / media output / queue.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppleMusicBottomButton(
                iconRes = R.drawable.lyrics,
                contentDescription = stringResource(R.string.lyrics),
                onClick = onLyricsClick,
            )
            AppleMusicBottomButton(
                iconRes = R.drawable.airplay,
                contentDescription = null,
                onClick = onOutputClick,
            )
            AppleMusicBottomButton(
                iconRes = R.drawable.queue_music,
                contentDescription = stringResource(R.string.queue),
                onClick = onQueueClick,
            )
        }
    }
}

@Composable
private fun AppleMusicChip(
    iconRes: Int,
    tint: Color,
    contentDescription: String?,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(AppleMusicChipSize)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.14f))
                .clickable(onClick = onClick),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun AppleMusicTransportButton(
    iconRes: Int,
    enabled: Boolean,
    mirrored: Boolean,
    iconSize: Dp,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(iconSize + 20.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false, radius = iconSize / 2 + 10.dp),
                    enabled = enabled,
                    onClick = onClick,
                ),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = Color.White.copy(alpha = if (enabled) 1f else 0.4f),
            modifier =
                Modifier
                    .size(iconSize)
                    .graphicsLayer { if (mirrored) scaleX = -1f },
        )
    }
}

@Composable
private fun AppleMusicBottomButton(
    iconRes: Int,
    contentDescription: String?,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false, radius = 26.dp),
                    onClick = onClick,
                ),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(AppleMusicBottomIconSize),
        )
    }
}

/** Thin Apple-Music-style scrubber: rounded 6dp track, no thumb, tap + drag to seek. */
@Composable
private fun AppleMusicSeekBar(
    position: Long,
    duration: Long,
    onScrub: (Long) -> Unit,
    onScrubFinished: () -> Unit,
) {
    val enabled = duration > 0L
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val playedFraction =
        if (duration > 0L) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val shownFraction = if (dragging) dragFraction else playedFraction

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(26.dp)
                .pointerInput(enabled, duration) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { offset ->
                        val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                        onScrub((fraction * duration).toLong())
                        onScrubFinished()
                    }
                }.pointerInput(enabled, duration) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragging = true
                            dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                            onScrub((dragFraction * duration).toLong())
                        },
                        onDragEnd = {
                            dragging = false
                            onScrubFinished()
                        },
                        onDragCancel = { dragging = false },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                            onScrub((dragFraction * duration).toLong())
                        },
                    )
                }.drawWithContent {
                    val trackHeight = if (dragging) 10.dp.toPx() else 7.dp.toPx()
                    val top = (size.height - trackHeight) / 2f
                    val radius = CornerRadius(trackHeight / 2f)
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.28f),
                        topLeft = Offset(0f, top),
                        size = Size(size.width, trackHeight),
                        cornerRadius = radius,
                    )
                    drawRoundRect(
                        color = Color.White.copy(alpha = if (dragging) 1f else 0.85f),
                        topLeft = Offset(0f, top),
                        size = Size(size.width * shownFraction, trackHeight),
                        cornerRadius = radius,
                    )
                },
    )
}

/** Flat volume slider matching the scrubber's look. */
@Composable
private fun AppleMusicVolumeSlider(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .height(26.dp)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        onVolumeChange((offset.x / size.width).coerceIn(0f, 1f))
                    }
                }.pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        onVolumeChange((change.position.x / size.width).coerceIn(0f, 1f))
                    }
                }.drawWithContent {
                    val trackHeight = 6.dp.toPx()
                    val top = (size.height - trackHeight) / 2f
                    val radius = CornerRadius(trackHeight / 2f)
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.28f),
                        topLeft = Offset(0f, top),
                        size = Size(size.width, trackHeight),
                        cornerRadius = radius,
                    )
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.85f),
                        topLeft = Offset(0f, top),
                        size = Size(size.width * volume.coerceIn(0f, 1f), trackHeight),
                        cornerRadius = radius,
                    )
                },
    )
}
