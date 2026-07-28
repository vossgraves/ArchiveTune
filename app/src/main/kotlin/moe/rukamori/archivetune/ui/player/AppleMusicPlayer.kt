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
 *
 * Gesture ownership matters here. Swipe gestures live on the artwork ONLY, never on the controls
 * column: a drag detector wrapping the controls consumes events before the buttons and sliders
 * beneath it can see a tap, which is what previously left the queue button dead.
 */

package moe.rukamori.archivetune.ui.player

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.input.pointer.positionChange
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
import moe.rukamori.archivetune.constants.BackdropBlurAmountKey
import moe.rukamori.archivetune.constants.BackdropEnabledKey
import moe.rukamori.archivetune.constants.DisableBlurKey
import moe.rukamori.archivetune.constants.PlayerButtonsStyle
import moe.rukamori.archivetune.constants.PlayerButtonsStyleKey
import moe.rukamori.archivetune.constants.ShowPlayerVolumeBarKey
import moe.rukamori.archivetune.constants.SwipeThumbnailKey
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
import moe.rukamori.archivetune.utils.makeTimeString
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberLowDataModeActive
import moe.rukamori.archivetune.utils.rememberPreference

private val AppleMusicContentPadding = 28.dp
private val AppleMusicChipSize = 34.dp
private val AppleMusicTransportIconSize = 52.dp
private val AppleMusicPlayPauseIconSize = 62.dp
private val AppleMusicBottomIconSize = 24.dp

/** Distance a drag must travel before it counts as a deliberate swipe rather than a stray move. */
private val AppleMusicSwipeThreshold = 72.dp

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

    // Preferences this design honours. Read here rather than threaded down from BottomSheetPlayer so
    // the design stays self-contained, matching how Thumbnail.kt and MiniPlayer.kt read the same
    // keys. DataStore is still the single source of truth, so the values cannot diverge.
    //
    // Deliberately NOT honoured, because each would erase what makes this design recognisable — and
    // the design is itself an explicit user choice via PlayerDesignStyleKey:
    //   SliderStyleKey            — Wavy/Circular would replace the signature flat scrubber.
    //   PlayerBackgroundStyleKey  — the artwork-tinted panel *is* the Apple Music background.
    //                               (BottomSheetPlayer already forces DEFAULT for this design.)
    //   ThumbnailCornerRadiusKey  — the sharp square artwork is the defining trait.
    val (disableBlur) = rememberPreference(DisableBlurKey, defaultValue = false)
    // Backdrop strength uses BackdropEnabled/BackdropBlurAmount, the same pair Thumbnail.kt and
    // Player.kt read for player backdrops. Not BlurRadiusKey, which despite the similar name is a
    // separate setting scoped to the lyrics sheet background (Player.kt hands it to LyricsScreen and
    // nowhere else). This full-screen artwork is a player backdrop, so it belongs to the former pair.
    val (backdropEnabled) = rememberPreference(BackdropEnabledKey, defaultValue = true)
    val (backdropBlurAmount) = rememberPreference(BackdropBlurAmountKey, defaultValue = 60)
    val (showPlayerVolumeBar) = rememberPreference(ShowPlayerVolumeBarKey, defaultValue = true)
    val (swipeThumbnail) = rememberPreference(SwipeThumbnailKey, defaultValue = true)
    val playerButtonsStyle by rememberEnumPreference(
        key = PlayerButtonsStyleKey,
        defaultValue = PlayerButtonsStyle.DEFAULT,
    )

    // The chips carry their own background, so an accent stays legible there. The oversized
    // transport glyphs sit directly on artwork and stay white for contrast — the same compromise
    // Player.kt makes for the V7/V8 designs.
    val chipBackground =
        when (playerButtonsStyle) {
            PlayerButtonsStyle.DEFAULT -> Color.White.copy(alpha = 0.14f)
            PlayerButtonsStyle.SECONDARY -> MaterialTheme.colorScheme.secondary
        }
    val chipTint =
        when (playerButtonsStyle) {
            PlayerButtonsStyle.DEFAULT -> Color.White
            PlayerButtonsStyle.SECONDARY -> MaterialTheme.colorScheme.onSecondary
        }

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

        // 1. Blurred artwork fills the whole player as the base layer.
        //
        // Mirrors the strategy Player.kt uses for every other design rather than inventing a third
        // one. On API 31+ Modifier.blur compiles to a hardware RenderEffect, so a live blur is cheap
        // and stays sharp at any radius. Below 31 there is no RenderEffect, so Compose falls back to
        // re-running a software gaussian over a full-screen bitmap EVERY frame -- that is what made
        // the lyrics transition choppy in this design specifically, since it hardcoded a live
        // blur(72.dp) with no API split at all. BackdropBlurApi30 blurs once into a cached 500px
        // bitmap off the main thread instead, so the per-frame cost there drops to zero.
        val hasBlur = !disableBlur && backdropEnabled && backdropBlurAmount > 0
        if (!hasBlur) {
            // Blur off: the scrim below carries all the contrast on its own.
            AsyncImage(
                model = artworkRequest ?: artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AsyncImage(
                model = artworkRequest ?: artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .matchParentSize()
                        // 0..100 mapped onto a 60dp ceiling, the same math Thumbnail.kt:559 uses for
                        // its full-screen backdrop. Deliberately NOT the 25 BackdropBlurApi30 uses
                        // internally: that radius applies to a small cached bitmap and is rescaled
                        // along with it, so it is not in the same units as a dp radius spread across
                        // a full-screen image. Reusing 25 here would leave API 31+ looking barely
                        // blurred, and well short of the 72dp this design once hardcoded.
                        .blur((backdropBlurAmount * 60 / 100f).coerceIn(1f, 60f).dp),
            )
        } else {
            BackdropBlurApi30(
                model = artworkUrl,
                blurAmount = backdropBlurAmount,
                modifier = Modifier.matchParentSize(),
            )
        }
        // Deep contrast scrim over the blur: the Apple Music sheet reads as a dark, artwork-tinted
        // panel rather than a bright blur, so the whole surface is pulled well down in brightness
        // and pushed darker still toward the bottom where the controls sit. Without a blur the
        // artwork stays sharp underneath, so the scrim has to work harder to keep text legible.
        // Keyed on hasBlur, not disableBlur alone: the backdrop can also be off because the user
        // turned it off or set its amount to zero, and a sharp artwork needs the heavier scrim in
        // every one of those cases for the white text to stay legible.
        val scrimBoost = if (hasBlur) 0f else 0.10f
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.42f + scrimBoost),
                            0.5f to Color.Black.copy(alpha = 0.60f + scrimBoost),
                            1f to Color.Black.copy(alpha = 0.82f + scrimBoost),
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
                    swipeThumbnail = swipeThumbnail,
                    canSkipPrevious = canSkipPrevious,
                    canSkipNext = canSkipNext,
                    onSkipPrevious = playerConnection::seekToPrevious,
                    onSkipNext = playerConnection::seekToNext,
                    onQueueClick = onQueueClick,
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
                    showVolumeBar = showPlayerVolumeBar,
                    chipBackground = chipBackground,
                    chipTint = chipTint,
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
                swipeThumbnail = swipeThumbnail,
                canSkipPrevious = canSkipPrevious,
                canSkipNext = canSkipNext,
                onSkipPrevious = playerConnection::seekToPrevious,
                onSkipNext = playerConnection::seekToNext,
                onQueueClick = onQueueClick,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(sharpArtworkHeight)
                        .align(Alignment.TopCenter),
            )

            // 3. Controls anchored to the bottom. No gesture detector wraps this — see file header.
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
                showVolumeBar = showPlayerVolumeBar,
                chipBackground = chipBackground,
                chipTint = chipTint,
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
    swipeThumbnail: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onQueueClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The detectors below are keyed on values that rarely change, so a lambda captured directly gets
    // pinned to the composition that created it. openQueue upstream is a
    // remember(state, queueSheetState), so if either sheet state is recreated a captured copy would
    // keep calling expandSoft() on the orphaned one -- a silently dead swipe. Reading through
    // rememberUpdatedState keeps the current lambda without making it a pointerInput key, which
    // would otherwise tear down the gesture mid-drag.
    val currentOnSkipNext by rememberUpdatedState(onSkipNext)
    val currentOnSkipPrevious by rememberUpdatedState(onSkipPrevious)
    val currentOnQueueClick by rememberUpdatedState(onQueueClick)
    Box(
        modifier =
            modifier
                .then(
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
                )
                // Swipe gestures live here, on artwork with no interactive children, so consuming
                // events cannot steal taps from a button. The two detectors each wait for slop on
                // their own axis, so whichever axis the finger commits to wins cleanly.
                .pointerInput(swipeThumbnail, canSkipPrevious, canSkipNext) {
                    if (!swipeThumbnail) return@pointerInput
                    val threshold = AppleMusicSwipeThreshold.toPx()
                    var travelled = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { travelled = 0f },
                        onDragCancel = { travelled = 0f },
                        onDragEnd = {
                            when {
                                travelled <= -threshold && canSkipNext -> currentOnSkipNext()
                                travelled >= threshold && canSkipPrevious -> currentOnSkipPrevious()
                            }
                            travelled = 0f
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            travelled += dragAmount
                        },
                    )
                }.pointerInput(Unit) {
                    // Hand-rolled rather than detectVerticalDragGestures, which claims the gesture
                    // on BOTH axes' slop and would swallow downward drags. BottomSheet runs its own
                    // detectVerticalDragGestures, and this Box is its descendant, so a child that
                    // consumes every vertical move stops the user from dragging the artwork down to
                    // collapse the player. Only upward travel is consumed here; a downward drag is
                    // left completely untouched so the sheet still receives it.
                    val threshold = AppleMusicSwipeThreshold.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var travelled = 0f
                        var opened = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            travelled += change.positionChange().y
                            // Clearly heading down: bail out without ever consuming, leaving the
                            // gesture to the sheet.
                            if (!opened && travelled > threshold) break
                            if (travelled <= -threshold) {
                                if (!opened) {
                                    opened = true
                                    currentOnQueueClick()
                                }
                            }
                            // Consume only once this is committed to being our swipe, so the sheet
                            // does not also act on the same finger movement.
                            if (opened) change.consume()
                        }
                    }
                },
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
    showVolumeBar: Boolean,
    chipBackground: Color,
    chipTint: Color,
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
                iconRes = if (currentSongLiked) R.drawable.player_star_filled else R.drawable.player_star,
                background = chipBackground,
                tint = if (currentSongLiked) Color(0xFFFFD700) else chipTint,
                contentDescription = null,
                onClick = playerConnection::toggleLike,
            )
            Spacer(Modifier.width(10.dp))
            AppleMusicChip(
                iconRes = R.drawable.player_more_horiz,
                background = chipBackground,
                tint = chipTint,
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

        // Flat volume slider with speaker glyphs, shared with the lyrics screen.
        if (showVolumeBar) {
            AppleMusicVolumeRow(
                volume = volume,
                onVolumeChange = onVolumeChange,
                modifier = Modifier.fillMaxWidth(),
            )
        }

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
                iconRes = R.drawable.player_airplay,
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
    background: Color,
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
                .background(background)
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
                // 48dp, not the icon's 22dp: this is the Material minimum touch target, and the
                // queue button on this row is the one that was unreachable, so it is worth the few
                // extra dp of hit area even though the glyph itself stays small.
                .size(48.dp)
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

/** Thin Apple-Music-style scrubber: rounded track, no thumb, tap + drag to seek. */
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
