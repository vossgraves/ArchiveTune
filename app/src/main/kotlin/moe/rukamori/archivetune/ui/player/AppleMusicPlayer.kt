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
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import kotlin.math.abs
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
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size as CoilSize
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.ThumbnailCornerRadiusKey
import moe.rukamori.archivetune.db.entities.FormatEntity
import moe.rukamori.archivetune.db.entities.codecLabel
import moe.rukamori.archivetune.db.entities.isLossless
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.component.BottomSheetPageState
import moe.rukamori.archivetune.ui.component.BottomSheetState
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.menu.PlayerMenu
import moe.rukamori.archivetune.ui.menu.rememberCastPlayerMenuAction
import moe.rukamori.archivetune.ui.utils.ShowMediaInfo
import moe.rukamori.archivetune.ui.utils.highRes
import moe.rukamori.archivetune.utils.ImageBlurUtils
import moe.rukamori.archivetune.utils.isLocalMediaId
import moe.rukamori.archivetune.utils.makeTimeString
import moe.rukamori.archivetune.utils.rememberLowDataModeActive
import moe.rukamori.archivetune.utils.rememberPreference

private val AppleMusicContentPadding = 28.dp
private val AppleMusicChipSize = 34.dp
private val AppleMusicTransportIconSize = 52.dp
private val AppleMusicPlayPauseIconSize = 62.dp
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
    currentFormat: FormatEntity?,
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
    // The "AirPlay" slot opens the Cast route picker on flavors that ship Cast (gms). This also
    // renders the route-picker bottom sheet when it becomes visible. On flavors without Cast (foss)
    // rememberCastPlayerMenuAction() returns null and we fall back to the system output switcher.
    val castAction = rememberCastPlayerMenuAction()
    val onOutputClick: () -> Unit = castAction?.onClick ?: {
        // Cast-less flavors (foss): open the system media-output switcher panel.
        runCatching {
            context.startActivity(Intent("android.settings.panel.action.MEDIA_OUTPUT"))
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val sharpArtworkHeight = if (landscape) maxHeight else maxHeight * 0.55f

        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .background(Color.Black),
        )

        val videoShowing =
            LocalVideoArtworkState.current != null &&
                mediaMetadata.isMusicVideo &&
                !mediaMetadata.id.isLocalMediaId()
        val isPreS = Build.VERSION.SDK_INT < Build.VERSION_CODES.S
        val canvasActive =
            !canvasPrimaryUrl.isNullOrBlank() || !canvasFallbackUrl.isNullOrBlank()
        // When a Spotify Canvas (or any canvas artwork) is playing, render the canvas video
        // itself as the blurred backdrop — "Apple Music player style". On Android 12+,
        // Modifier.blur works on the TextureView surface that CanvasArtworkPlayer uses, so
        // the backdrop mirrors the canvas video in real time. Pre-Android-12 falls back to
        // the album-art blur (RenderEffect is unavailable, so blurring a video surface
        // efficiently isn't possible).
        val useCanvasBackdrop = canvasActive && !videoShowing && !isPreS
        val context = LocalContext.current
        val imageLoader = context.imageLoader
        val preBlurredBitmap by produceState<Bitmap?>(null, artworkUrl) {
            if (!isPreS || artworkUrl.isNullOrBlank() || videoShowing || canvasActive) {
                value = null
                return@produceState
            }
            value = withContext(Dispatchers.IO) {
                try {
                    val request = ImageRequest.Builder(context)
                        .data(artworkUrl)
                        .allowHardware(false)
                        .memoryCacheKey("$artworkUrl#amplayer")
                        .diskCacheKey("$artworkUrl#amplayer")
                        .size(CoilSize(720, 720))
                        .build()
                    val result = imageLoader.execute(request)
                    if (result is SuccessResult) {
                        val bitmap = result.image.toBitmap()
                            .copy(Bitmap.Config.ARGB_8888, true)
                        val density = context.resources.displayMetrics.density
                        ImageBlurUtils.blur(bitmap, 72f * density)
                    } else null
                } catch (_: Exception) {
                    null
                }
            }
        }

        if (!videoShowing) {
            if (useCanvasBackdrop) {
                // Canvas-driven backdrop: a second CanvasArtworkPlayer instance rendered
                // behind the controls with a heavy blur. This makes the backdrop follow
                // the canvas video in real time — matching Apple Music's player where the
                // ambient blur behind the controls mirrors whatever is on screen.
                CanvasArtworkPlayer(
                    primaryUrl = canvasPrimaryUrl,
                    fallbackUrl = canvasFallbackUrl,
                    isPlaying = isPlaying,
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                    modifier =
                        Modifier
                            .matchParentSize()
                            .blur(72.dp)
                            .graphicsLayer {
                                scaleX = 1.2f
                                scaleY = 1.2f
                            },
                )
            } else if (isPreS && preBlurredBitmap != null) {
                Image(
                    bitmap = preBlurredBitmap!!.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .matchParentSize()
                            .graphicsLayer {
                                scaleX = 1.2f
                                scaleY = 1.2f
                            },
                )
            } else {
                // Either Android 12+ (use Modifier.blur) or pre-S but the pre-blur hasn't
                // resolved yet (render sharp + heavier scrim for now).
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
            }
            val preBlurLoading = isPreS && preBlurredBitmap == null && !canvasActive
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = if (useCanvasBackdrop || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.42f else if (preBlurLoading) 0.62f else 0.52f),
                                0.5f to Color.Black.copy(alpha = if (useCanvasBackdrop || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.60f else if (preBlurLoading) 0.74f else 0.68f),
                                1f to Color.Black.copy(alpha = if (useCanvasBackdrop || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.82f else if (preBlurLoading) 0.90f else 0.86f),
                            ),
                        ),
            )
        }

        if (landscape) {
            Row(Modifier.fillMaxSize()) {
                AppleMusicSharpArtwork(
                    artworkRequest = artworkRequest,
                    artworkUrl = artworkUrl,
                    canvasPrimaryUrl = canvasPrimaryUrl,
                    canvasFallbackUrl = canvasFallbackUrl,
                    isPlaying = isPlaying,
                    fadeBottom = false,
                    videoId = mediaMetadata.id.takeIf { !it.isLocalMediaId() },
                    isMusicVideo = mediaMetadata.isMusicVideo,
                    landscape = true,
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
                    currentFormat = currentFormat,
                    onQualityChipClick = {
                        bottomSheetPageState.show { ShowMediaInfo(mediaMetadata.id) }
                    },
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(bottom = contentBottomPadding),
                )
            }
        } else {
            AppleMusicSharpArtwork(
                artworkRequest = artworkRequest,
                artworkUrl = artworkUrl,
                canvasPrimaryUrl = canvasPrimaryUrl,
                canvasFallbackUrl = canvasFallbackUrl,
                isPlaying = isPlaying,
                fadeBottom = !videoShowing,
                videoId = mediaMetadata.id.takeIf { !it.isLocalMediaId() },
                isMusicVideo = mediaMetadata.isMusicVideo,
                landscape = false,
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
                currentFormat = currentFormat,
                onQualityChipClick = {
                    bottomSheetPageState.show { ShowMediaInfo(mediaMetadata.id) }
                },
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
    videoId: String? = null,
    isMusicVideo: Boolean = false,
    landscape: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current
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
        val videoArtworkState = LocalVideoArtworkState.current
        val showVideo =
            videoArtworkState != null &&
                isMusicVideo &&
                !videoId.isNullOrBlank() &&
                playerConnection != null
        // "Immersive extended" — when there is no Spotify Canvas (or any animated
        // artwork) AND no music video, render the still cover as a square using
        // the SAME sizing formula as the Material Extended (V9) player, instead of
        // stretching the cover to fill the rectangular stage. When a canvas or
        // video IS available, we keep the full-bleed display so the animated
        // artwork can fill the stage.
        val hasCanvas = !canvasPrimaryUrl.isNullOrBlank() || !canvasFallbackUrl.isNullOrBlank()
        val immersiveExtendedCard = !showVideo && !hasCanvas
        if (showVideo) {
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .background(Color.Black),
            )
        } else if (immersiveExtendedCard) {
            // "Immersive extended" — render the still cover as a square using the
            // SAME sizing formula and thresholds as the Material Extended (V9)
            // player (see V9PortraitContent in PlayerComponents.kt), centered
            // inside the artwork stage. V9 computes its cap against the FULL
            // player height, but inside AppleMusicSharpArtwork maxHeight is only
            // the stage height (55% of the player height in portrait, full
            // height in landscape) — so we re-derive the full height first and
            // run ALL the compact-height thresholds against that full height.
            // Without this, a typical 800dp-tall player would see its 440dp
            // stage trip the "veryCompact" branch and shrink the artwork from
            // 0.40 * H to 0.32 * H, making it noticeably smaller than V9.
            BoxWithConstraints(modifier = Modifier.matchParentSize()) {
                val horizontalPadding = if (maxWidth < 380.dp) 16.dp else 20.dp
                val effectiveFullHeight = if (landscape) maxHeight else maxHeight / 0.55f
                val compactHeight = effectiveFullHeight < 760.dp
                val veryCompactHeight = effectiveFullHeight < 700.dp
                // Honor the global thumbnail corner-radius preference (the same
                // one the "Adjust corner radius" dialog in Appearance settings
                // controls). The slider's max is 45dp, but for a ~320dp artwork
                // that would be over-round, so we cap at 32dp — matching V9's
                // RoundedCornerShape(30.dp) default while still letting the
                // user drop it to 0 for sharp corners.
                val (thumbnailCornerRadius, _) = rememberPreference(
                    ThumbnailCornerRadiusKey,
                    defaultValue = 16f,
                )
                val artworkCornerRadiusDp = thumbnailCornerRadius.coerceAtMost(32f).dp
                val artworkMinSize =
                    when {
                        veryCompactHeight -> 200.dp
                        compactHeight -> 216.dp
                        else -> 236.dp
                    }
                val artworkHeightLimit =
                    effectiveFullHeight *
                        when {
                            veryCompactHeight -> 0.32f
                            compactHeight -> 0.35f
                            else -> 0.40f
                        }
                val artworkSize =
                    (maxWidth - horizontalPadding * 2)
                        .coerceAtMost(artworkHeightLimit)
                        .coerceAtLeast(artworkMinSize)
                Box(
                    modifier = Modifier.matchParentSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = artworkRequest ?: artworkUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .size(artworkSize)
                                .shadow(8.dp, RoundedCornerShape(artworkCornerRadiusDp))
                                .clip(RoundedCornerShape(artworkCornerRadiusDp)),
                    )
                }
            }
        } else {
            AsyncImage(
                model = artworkRequest ?: artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }

        if (!showVideo &&
            (!canvasPrimaryUrl.isNullOrBlank() || !canvasFallbackUrl.isNullOrBlank())
        ) {
            CanvasArtworkPlayer(
                primaryUrl = canvasPrimaryUrl,
                fallbackUrl = canvasFallbackUrl,
                isPlaying = isPlaying,
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                modifier = Modifier.matchParentSize(),
            )
        }

        if (showVideo) {
            InlineVideoPlayer(
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
    // Stream format for the quality chip. Null = no chip rendered.
    currentFormat: FormatEntity?,
    // Clicked when the user taps the quality chip — opens the song-detail
    // bottom sheet (ShowMediaInfo), mirroring how tapping the title/artist
    // in Apple Music's stock UI opens the song info page.
    onQualityChipClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var swipeUpAccumulated by remember { mutableFloatStateOf(0f) }
    val swipeUpThreshold = 120f
    val swipeActivationThreshold = 72f
    val resetSwipeUp = remember {
        {
            if (swipeUpAccumulated != 0f) swipeUpAccumulated = 0f
        }
    }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(300); resetSwipeUp() }

    Column(
        modifier = modifier
            .padding(horizontal = AppleMusicContentPadding)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var accumulated = 0f
                    var swipeActivated = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull() ?: break
                        if (change.changedToUp()) break

                        val dragDelta = change.positionChange().y

                        if (!swipeActivated) {
                            // Track upward movement but don't consume yet — let child taps win.
                            if (dragDelta < 0f) {
                                accumulated += dragDelta
                            }
                            if (abs(accumulated) > swipeActivationThreshold) {
                                swipeActivated = true
                                swipeUpAccumulated = accumulated
                                change.consume()
                            }
                        } else {
                            // Swipe is confirmed — consume to prevent child handling.
                            if (dragDelta < 0f) {
                                swipeUpAccumulated =
                                    (swipeUpAccumulated + dragDelta).coerceAtLeast(-swipeUpThreshold * 1.5f)
                            }
                            change.consume()
                        }
                    }

                    if (swipeActivated && swipeUpAccumulated < -swipeUpThreshold) {
                        onQueueClick()
                    }
                    swipeUpAccumulated = 0f
                }
            },
        // Use spacedBy with Top alignment so the title row sits at the very top
        // of the controls column (flush with the artwork's fade-out zone)
        // instead of being pushed down by the equal "before" spacing that
        // SpaceEvenly adds. A weighted spacer before the bottom action row
        // keeps the lyrics / cast / queue icons anchored at the bottom.
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.Top),
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
                iconRes = if (currentSongLiked) R.drawable.player_star_filled else R.drawable.player_star,
                tint = Color.White,
                contentDescription = null,
                onClick = playerConnection::toggleLike,
            )
            Spacer(Modifier.width(10.dp))
            AppleMusicChip(
                iconRes = R.drawable.player_more_horiz,
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
            Spacer(Modifier.height(8.dp))
            // Mirror the Immersive V8 layout: elapsed time on the left, quality
            // chip (Lossless / AAC / OPUS) centered, -remaining on the right.
            // The chip is tappable and opens the song-detail bottom sheet.
            Box(Modifier.fillMaxWidth()) {
                Text(
                    text = makeTimeString(sliderPosition ?: position),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.align(Alignment.CenterStart),
                )
                if (currentFormat != null) {
                    AppleMusicQualityChip(
                        currentFormat = currentFormat,
                        onClick = onQualityChipClick,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                Text(
                    text = "-" + makeTimeString((duration - (sliderPosition ?: position)).coerceAtLeast(0L)),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.align(Alignment.CenterEnd),
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
                iconRes = R.drawable.player_fast_forward,
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
                        iconRes = if (isPlaying) R.drawable.player_pause else R.drawable.player_play,
                        enabled = true,
                        mirrored = false,
                        iconSize = AppleMusicPlayPauseIconSize,
                        onClick = onPlayPauseClick,
                    )
                }
            }
            AppleMusicTransportButton(
                iconRes = R.drawable.player_fast_forward,
                enabled = canSkipNext,
                mirrored = false,
                iconSize = AppleMusicTransportIconSize,
                onClick = playerConnection::seekToNext,
            )
        }

        // Flat volume slider with speaker glyphs. Uses the shared AppleMusicVolumeRow
        // which has proper drag tracking (dragging state + rememberUpdatedState) so the
        // fill follows the finger during a drag instead of lagging behind the rounded
        // device-volume step.
        AppleMusicVolumeRow(
            volume = volume,
            onVolumeChange = onVolumeChange,
            modifier = Modifier.fillMaxWidth(),
        )

        // Weighted spacer: absorbs leftover vertical space so the bottom
        // action row (lyrics / cast / queue) stays anchored at the bottom
        // of the controls column, while the title row stays flush with the
        // artwork's fade-out zone at the top.
        Spacer(Modifier.weight(1f))

        // Bottom action row: lyrics / media output / queue.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppleMusicBottomButton(
                iconRes = R.drawable.player_lyrics,
                contentDescription = stringResource(R.string.lyrics),
                onClick = onLyricsClick,
            )
            AppleMusicBottomButton(
                iconRes = R.drawable.cast,
                contentDescription = null,
                onClick = onOutputClick,
            )
            AppleMusicBottomButton(
                iconRes = R.drawable.player_queue_music,
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
/** NOTE: The local AppleMusicVolumeSlider was removed in favor of the shared
 *  AppleMusicVolumeRow (in AppleMusicSlider.kt) which has proper drag tracking
 *  via `dragging` state + `rememberUpdatedState`. The old local slider used
 *  `pointerInput(Unit)` which captured stale callbacks and didn't track drag
 *  state, causing the fill to lag behind the finger. */

/**
 * Quality chip rendered between the elapsed and -remaining timestamps on the
 * Apple Music player's seek-bar row. Mirrors the Immersive V8 player's
 * `V8QualityChip` (PlayerComponents.kt:2762) — same pill shape, same waveform
 * icon (`R.drawable.player_graphic_eq`), same `codecLabel()` text — but uses
 * `Color.White` as the foreground because the Apple Music player renders on
 * top of artwork-on-black, not a themed surface.
 *
 * Tapping the chip opens the song-detail bottom sheet (`ShowMediaInfo`),
 * matching how Apple Music's stock UI exposes the song info page.
 */
@Composable
private fun AppleMusicQualityChip(
    currentFormat: FormatEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = remember(currentFormat.mimeType, currentFormat.codecs) {
        currentFormat.codecLabel()
    }
    val lossless = remember(currentFormat.codecs, currentFormat.mimeType) {
        currentFormat.isLossless()
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color.White.copy(alpha = 0.1f),
        border = BorderStroke(width = 1.dp, color = Color.White.copy(alpha = 0.13f)),
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Icon(
                painter = painterResource(
                    if (lossless) R.drawable.ic_mqa else R.drawable.player_graphic_eq,
                ),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.72f),
                modifier = Modifier.size(if (lossless) 18.dp else 15.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.72f),
                maxLines = 1,
            )
        }
    }
}
