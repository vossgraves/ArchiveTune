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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import kotlin.math.abs
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.LocalStableSystemBarsTopPadding
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
import moe.rukamori.archivetune.ui.component.LyricsEnhanced
import moe.rukamori.archivetune.ui.component.LyricsV2
import moe.rukamori.archivetune.constants.LyricsMode
import moe.rukamori.archivetune.constants.LyricsModeKey
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.ui.menu.LyricsMenu
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
private val AppleMusicMiniArtworkSize = 56.dp

/**
 * Internal visual state of the Apple Music player. Mirrors ViviMusic's
 * `PlayerInternalState` enum — COVER shows the full-screen artwork + title
 * row, QUEUE morphs the artwork into a mini header and reveals the in-place
 * queue sheet, LYRICS morphs the same way but reveals the inline lyrics
 * composable instead of the queue list.
 */
private enum class AppleMusicPlayerState { COVER, QUEUE, LYRICS }

@OptIn(ExperimentalSharedTransitionApi::class)
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
    lyricsSyncOffset: Int = 0,
    onLyricsSyncOffsetChange: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
    landscape: Boolean = false,
) {
    // In-place queue morph state. When the user taps the queue button (or
    // swipes up on the controls area), we toggle this state instead of
    // opening the separate queue BottomSheet. The artwork + title row then
    // morph into a compact mini header while the queue list fades in below —
    // matching ViviMusic's Player_v2 ↔ Queue_v2 transition exactly.
    var queueOpen by remember { mutableStateOf(false) }
    // In-place lyrics morph state. Same animation as the queue morph — the
    // artwork shrinks into the mini header and the lyrics composable fades
    // in below. Clicking the mini header artwork restores the COVER state.
    var lyricsOpen by remember { mutableStateOf(false) }

    // Toggling one closes the other — queue and lyrics are mutually exclusive
    // (only one morph target can be active at a time).
    val toggleQueue = {
        lyricsOpen = false
        queueOpen = !queueOpen
    }
    val toggleLyrics = {
        queueOpen = false
        lyricsOpen = !lyricsOpen
    }

    // Back handler: when the in-place queue or lyrics is open, back closes
    // it first (before the outer player-collapse BackHandler in Player.kt).
    val morphOpen = queueOpen || lyricsOpen
    androidx.activity.compose.BackHandler(enabled = morphOpen) {
        if (lyricsOpen) lyricsOpen = false
        if (queueOpen) queueOpen = false
    }

    // The morph target state: COVER (default), QUEUE, or LYRICS.
    val morphState =
        when {
            queueOpen -> AppleMusicPlayerState.QUEUE
            lyricsOpen -> AppleMusicPlayerState.LYRICS
            else -> AppleMusicPlayerState.COVER
        }

    // Clicking the mini header artwork restores the COVER state (main player).
    val restoreCover = {
        queueOpen = false
        lyricsOpen = false
    }

    // === Auto-hide player controls (always-on in Apple Music style) ===
    // The in-place Apple Music lyrics view ALWAYS auto-hides the bottom
    // controls (seekbar + transport + volume + action row) after 3 seconds.
    // Touching anywhere on the lyrics restores them and restarts the timer.
    // This is hardcoded behavior — there is no toggle in the LyricsMenu
    // overflow because the Apple Music style is designed to auto-hide.
    // The standalone LyricsScreen still has the toggle (for other player
    // styles that use it).
    var playerControlsExpanded by remember(mediaMetadata.id) { mutableStateOf(true) }
    var playerControlsVisibilityTick by remember(mediaMetadata.id) { mutableIntStateOf(0) }
    val autoHideDelayMs = 3_000L

    LaunchedEffect(lyricsOpen) {
        if (lyricsOpen) {
            playerControlsExpanded = true
            playerControlsVisibilityTick++
        } else {
            playerControlsExpanded = true
        }
    }
    LaunchedEffect(lyricsOpen, playerControlsVisibilityTick) {
        if (!lyricsOpen) return@LaunchedEffect
        playerControlsExpanded = true
        delay(autoHideDelayMs)
        playerControlsExpanded = false
    }
    val pokePlayerControlsVisibility = remember {
        {
            if (lyricsOpen) {
                playerControlsExpanded = true
                playerControlsVisibilityTick++
            }
        }
    }

    // === Deferred position reads for the lyrics overlay ===
    // `position` updates every 100ms (from Player.kt's polling loop) and
    // `sliderPosition` changes during scrubbing. If the lyrics overlay's
    // AnimatedVisibility content lambda reads them directly, it recomposes
    // every 100ms — recreating the lyricsPosProvider lambda, re-invoking
    // the Box/Column/LyricsEnhanced calls, and stealing frame budget from
    // the karaoke syllable sweep. By wrapping them in rememberUpdatedState
    // and capturing them in a stable remember'd lambda, the overlay content
    // never recomposes on position changes — only the State objects update,
    // and the lambda reads them lazily when the lyrics frame loop polls.
    val sliderPositionState = rememberUpdatedState(sliderPosition)
    val positionState = rememberUpdatedState(position)
    val lyricsPosProvider = remember {
        { sliderPositionState.value ?: positionState.value }
    }

    // === Moving blur drift for the backdrop when lyrics is open ===
    // Mirrors the MovingBlurBackground from LyricsScreen: the blurred artwork
    // slowly drifts horizontally and vertically, creating an ambient motion
    // behind the lyrics. Only active when lyricsOpen = true.
    //
    // CRITICAL PERF: we keep the State<Float> objects (NOT `by` delegation) so
    // the animation values are read ONLY inside Modifier.graphicsLayer { }
    // lambdas (draw-phase deferred reads). Reading them during composition —
    // e.g. `val backdropDriftX = if (lyricsOpen) blurDriftX else 0f` — would
    // invalidate the ENTIRE AppleMusicPlayerContent composable every frame
    // (SharedTransitionLayout, AnimatedContent, ControlsColumn, and the inline
    // LyricsEnhanced all recompose at ~60fps), stealing the frame budget from
    // the karaoke syllable sweep. This was the root cause of the Apple-Music-
    // style-only lyrics jank — LyricsScreen.kt doesn't have it because its
    // drift lives in a separate MovingBlurBackground composable.
    val blurTransition = rememberInfiniteTransition(label = "am-lyrics-blur-drift")
    val blurDriftXState = blurTransition.animateFloat(
        initialValue = -80f,
        targetValue = 80f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 19_000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "am-lyrics-drift-x",
    )
    val blurDriftYState = blurTransition.animateFloat(
        initialValue = -60f,
        targetValue = 60f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 27_000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "am-lyrics-drift-y",
    )
    // Pre-compute dp→px once (graphicsLayer.translationX is in pixels). Density
    // doesn't change per-frame so this is a one-time composition-phase read.
    val driftDpToPx = with(LocalDensity.current) { 1.dp.toPx() }

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

    // Current lyrics for the LyricsMenu (shown when lyrics is open and the
    // user taps the overflow "more" button).
    val currentLyrics by playerConnection.currentLyrics.collectAsState(initial = null)

    val onPlayPauseClick = {
        if (playbackState == STATE_ENDED) {
            playerConnection.player.seekTo(0, 0)
            playerConnection.player.playWhenReady = true
        } else {
            playerConnection.player.togglePlayPause()
        }
    }
    val onMoreClick = {
        if (lyricsOpen) {
            // When lyrics is open, the overflow menu shows the LyricsMenu
            // (Edit / Refetch / Translate / Sync offset / Search) instead of
            // the regular PlayerMenu. The "Show player controls" and
            // "Auto-hide player controls" toggles are NOT passed at all —
            // the in-place Apple Music lyrics view does not support auto-hide
            // (controls are always visible), so those toggles would be no-ops.
            // showControlsToggles = false hides them from the UI.
            menuState.show {
                LyricsMenu(
                    lyricsProvider = { currentLyrics },
                    mediaMetadataProvider = { mediaMetadata },
                    lyricsSyncOffset = lyricsSyncOffset,
                    onLyricsSyncOffsetChange = onLyricsSyncOffsetChange,
                    onDismiss = menuState::dismiss,
                    showControlsToggles = false,
                )
            }
        } else {
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
            // Backdrop rendering is state-aware to avoid jank:
            //
            // • COVER state — render the live canvas (or static artwork) with
            //   heavy blur, no drift. The drift values collapse to 0/1.2 when
            //   lyricsOpen=false, so the offset/scale modifiers are no-ops.
            //
            // • QUEUE state — same as COVER: live canvas backdrop (blurred).
            //   A second non-blurred canvas in the QUEUE state Box makes the
            //   canvas visibly continue playing behind the queue list's haze
            //   overlay. (Both canvases run without drift animation, so the
            //   frame budget is fine.)
            //
            // • LYRICS state — DON'T render the live canvas backdrop. A
            //   TextureView + Modifier.blur + per-frame animated offset is the
            //   single biggest source of GPU stalls on Android — it makes the
            //   karaoke syllable sweep, instrumental pulsing dots, and the
            //   moving blur itself all stutter. Instead, render a STATIC
            //   AsyncImage (album art) with Modifier.blur + drift. The drift
            //   on a static image is cheap (just a matrix transform), and the
            //   visual matches the standalone LyricsScreen.kt MovingBlurBackground
            //   exactly. The canvas is also removed from the LYRICS state Box
            //   below, so there's exactly zero video decoders running while
            //   the user reads lyrics — maximum frame budget for lyrics
            //   animations.
            // Helper: deferred-read graphicsLayer for the drift. Reads the
            // animation State<Float> inside the lambda (draw phase) so the
            // parent composable is NOT invalidated every frame.
            val driftGraphicsLayer: GraphicsLayerScope.() -> Unit = {
                // lyricsOpen is a stable Boolean state — reading it here is a
                // deferred read that only triggers a layer update on toggle.
                val active = lyricsOpen
                val scale = if (active) 1.4f else 1.2f
                scaleX = scale
                scaleY = scale
                if (active) {
                    // State.value reads are deferred to the draw phase.
                    translationX = blurDriftXState.value * driftDpToPx
                    translationY = blurDriftYState.value * driftDpToPx
                }
            }
            if (lyricsOpen) {
                // LYRICS: static image with blur + drift (matches LyricsScreen.kt's MovingBlurBackground).
                if (isPreS && preBlurredBitmap != null) {
                    Image(
                        bitmap = preBlurredBitmap!!.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .matchParentSize()
                                .graphicsLayer(driftGraphicsLayer),
                    )
                } else {
                    AsyncImage(
                        model = artworkRequest ?: artworkUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .matchParentSize()
                                .then(
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        Modifier.blur(64.dp)
                                    } else {
                                        Modifier
                                    },
                                ).graphicsLayer(driftGraphicsLayer),
                    )
                }
            } else if (useCanvasBackdrop) {
                // COVER / QUEUE: live canvas backdrop (blurred), no drift.
                // The driftGraphicsLayer lambda collapses translationX/Y to 0
                // when lyricsOpen=false, so only the fixed 1.2x scale applies —
                // effectively a static blur on a moving video surface.
                CanvasArtworkPlayer(
                    primaryUrl = canvasPrimaryUrl,
                    fallbackUrl = canvasFallbackUrl,
                    isPlaying = isPlaying,
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                    modifier =
                        Modifier
                            .matchParentSize()
                            .blur(72.dp)
                            .graphicsLayer(driftGraphicsLayer),
                )
            } else if (isPreS && preBlurredBitmap != null) {
                Image(
                    bitmap = preBlurredBitmap!!.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .matchParentSize()
                            .graphicsLayer(driftGraphicsLayer),
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
                            ).graphicsLayer(driftGraphicsLayer),
                )
            }
            val preBlurLoading = isPreS && preBlurredBitmap == null && !canvasActive
            // Brightened scrim — matches ViviMusic's brighter aesthetic.
            // Previous alphas (0.42/0.60/0.82) were too dark; reduced to
            // 0.25/0.40/0.65 so the blurred artwork's color shows through.
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = if (useCanvasBackdrop || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.25f else if (preBlurLoading) 0.55f else 0.40f),
                                0.5f to Color.Black.copy(alpha = if (useCanvasBackdrop || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.40f else if (preBlurLoading) 0.65f else 0.55f),
                                1f to Color.Black.copy(alpha = if (useCanvasBackdrop || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.65f else if (preBlurLoading) 0.85f else 0.75f),
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
                            .navigationBarsPadding()
                            .padding(bottom = contentBottomPadding),
                )
            }
        } else {
            // Portrait layout with ViviMusic-style in-place queue morph.
            //
            // The artwork + title row live inside a SharedTransitionLayout so they
            // can morph (large → mini) when the user toggles the queue. The playback
            // controls (seekbar + transport + volume + bottom row) live outside the
            // SharedTransitionLayout so they stay anchored at the bottom — matching
            // ViviMusic's Player_v2 layout exactly.
            //
            // The lyrics composable is rendered as a SEPARATE overlay ON TOP of the
            // SharedTransitionLayout (inside the same weighted Box), but OUTSIDE
            // the SharedTransitionLayout itself. This is critical for two reasons:
            //
            // 1. PERFORMANCE: rendering LyricsEnhanced inside the SharedTransitionLayout
            //    caused the karaoke syllable fill animation to stutter because the
            //    shared transition machinery's per-frame tracking stole frame budget.
            //    The overlay approach gives the lyrics animations the full frame
            //    budget, matching the standalone LyricsScreen's performance.
            //
            // 2. TOUCH ROUTING: the lyrics overlay is bounded to the weighted Box
            //    (the morph area), so it CANNOT extend over the controls below.
            //    Previously the overlay used fillMaxSize on the outer Box, covering
            //    the controls and making them uninteractable when lyrics was open.
            //    Now the overlay is a sibling of SharedTransitionLayout inside the
            //    weighted Box, and the controls live in a separate AnimatedVisibility
            //    below the Box — touches on the controls go directly to the controls.
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                SharedTransitionLayout(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    AnimatedContent(
                        targetState = morphState,
                        transitionSpec = {
                            // Crossfade with shared-element morph. The COVER fades out
                            // quickly (200ms) so the large square artwork disappears fast
                            // and the rounded mini artwork becomes visible sooner. The
                            // entering state (QUEUE/LYRICS) fades in over 600ms for a
                            // smooth reveal. This fixes the "thumbnail stays square for
                            // most of the transition" issue — the square COVER content
                            // is gone in 200ms instead of lingering for 600ms.
                            fadeIn(tween(600, easing = FastOutSlowInEasing)) togetherWith
                                fadeOut(tween(200, easing = FastOutSlowInEasing))
                        },
                        modifier = Modifier.fillMaxSize(),
                        label = "AppleMusicMorph",
                    ) { targetState ->
                        if (targetState == AppleMusicPlayerState.COVER) {
                            // COVER state: large sharp artwork fills the morph area.
                            Box(modifier = Modifier.fillMaxSize()) {
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
                                            .fillMaxSize()
                                            .sharedBounds(
                                                rememberSharedContentState(key = "amCoverArt"),
                                                animatedVisibilityScope = this@AnimatedContent,
                                            ),
                                )
                            }
                        } else {
                            // QUEUE / LYRICS state: mini header + content below.
                            //
                            // The Spotify Canvas continues playing behind the
                            // queue/lyrics content so it doesn't "stop" when the
                            // user opens the queue. The canvas is rendered without
                            // blur (the haze overlay from the queue list provides the
                            // frosted-glass effect), and the lyrics composable has
                            // its own scrim for readability.
                            //
                            // Apply the stable top inset (notch / status bar / display-cutout
                            // top) so the mini header + content sit below the physical notch
                            // even when the status bar is hidden app-wide for immersive mode.
                            // The COVER state deliberately runs full-bleed (artwork under the
                            // status bar), but the QUEUE/LYRICS state shows interactive UI
                            // (title, pills, list/lyrics) that must not collide with the notch.
                            // LocalStableSystemBarsTopPadding is computed in MainActivity and
                            // floors against displayCutout so it stays non-zero when the status
                            // bar is hidden — mirroring the pattern used by every other screen.
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .windowInsetsPadding(WindowInsets(top = LocalStableSystemBarsTopPadding.current)),
                            ) {
                                // The blurred backdrop (rendered at the top level
                                // above) already contains the canvas with
                                // Modifier.blur(72.dp) when useCanvasBackdrop is
                                // true. We intentionally do NOT render a second
                                // non-blurred canvas here — the previous
                                // implementation did that, which covered the
                                // blurred backdrop and made it look like the
                                // blur "went away" when the queue opened.
                                // The queue sheet renders on a transparent
                                // background so the blurred canvas shows through.
                                Column(modifier = Modifier.fillMaxSize()) {
                                    AppleMusicMiniHeader(
                                        artworkRequest = artworkRequest,
                                        artworkUrl = artworkUrl,
                                        mediaMetadata = mediaMetadata,
                                        currentSongLiked = currentSongLiked,
                                        titleActions = titleActions,
                                        onToggleLike = playerConnection::toggleLike,
                                        onMoreClick = onMoreClick,
                                        onArtworkClick = restoreCover,
                                        animatedVisibilityScope = this@AnimatedContent,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    if (targetState == AppleMusicPlayerState.QUEUE) {
                                        AppleMusicQueueSheet(
                                            navController = navController,
                                            playerBottomSheetState = state,
                                            modifier =
                                                Modifier
                                                    .fillMaxSize()
                                                    .animateEnterExit(
                                                        enter = slideInVertically(
                                                            animationSpec = tween(600, easing = FastOutSlowInEasing),
                                                        ) { it / 4 } + fadeIn(tween(600)),
                                                        exit = fadeOut(tween(400)) +
                                                            slideOutVertically(
                                                                animationSpec = tween(400, easing = FastOutSlowInEasing),
                                                            ) { it / 4 },
                                                    ),
                                        )
                                    }
                                    // NOTE: The LyricsEnhanced/LyricsV2 composable is
                                    // intentionally NOT rendered here inside the
                                    // SharedTransitionLayout/AnimatedContent. Rendering it
                                    // here caused the karaoke syllable fill animation to
                                    // stutter because the SharedTransitionLayout's per-frame
                                    // shared-element tracking stole frame budget from the
                                    // lyrics animation. Instead, the lyrics composable is
                                    // rendered as a separate overlay BELOW the
                                    // SharedTransitionLayout — completely outside the shared
                                    // transition machinery. This gives the lyrics animations
                                    // the full frame budget, matching the standalone
                                    // LyricsScreen's performance.
                                }
                            }
                        }
                    }
                }

                // Lyrics overlay — INSIDE the weighted Box, ON TOP of the
                // SharedTransitionLayout but BOUNDED to this Box's area. This means
                // the lyrics CANNOT extend over the controls below (which live in a
                // separate AnimatedVisibility outside this Box). Touches on the
                // controls area go directly to the controls, not to the lyrics.
                // The mini header (inside SharedTransitionLayout) handles the artwork
                // morph; this overlay only renders the lyrics content itself,
                // positioned below where the mini header sits.
                //
                // NOTE: we use the standalone AnimatedVisibility (androidx.compose.
                // animation.AnimatedVisibility) — NOT the ColumnScope extension —
                // because this is inside a Box, not a Column. The ColumnScope variant
                // would be a compile error here.
                androidx.compose.animation.AnimatedVisibility(
                    visible = lyricsOpen,
                    enter = fadeIn(tween(400, easing = FastOutSlowInEasing)),
                    exit = fadeOut(tween(300, easing = FastOutSlowInEasing)),
                ) {
                    val lyricsMode by rememberEnumPreference(LyricsModeKey, LyricsMode.ENHANCED)
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .windowInsetsPadding(WindowInsets(top = LocalStableSystemBarsTopPadding.current))
                                .pointerInput(lyricsOpen) {
                                    if (!lyricsOpen) return@pointerInput
                                    awaitEachGesture {
                                        awaitFirstDown(requireUnconsumed = false)
                                        pokePlayerControlsVisibility()
                                    }
                                },
                    ) {
                        // Spacer to push lyrics below the mini header. The mini header
                        // height is approx artwork size + padding; we use the same
                        // content padding for alignment.
                        Column(modifier = Modifier.fillMaxSize()) {
                            Spacer(modifier = Modifier.height(AppleMusicMiniArtworkSize + 16.dp))
                            when (lyricsMode) {
                                LyricsMode.V2 -> LyricsV2(
                                    sliderPositionProvider = lyricsPosProvider,
                                    lyricsSyncOffset = lyricsSyncOffset,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                LyricsMode.ENHANCED -> LyricsEnhanced(
                                    sliderPositionProvider = lyricsPosProvider,
                                    lyricsSyncOffset = lyricsSyncOffset,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
                } // end weighted Box (SharedTransitionLayout + lyrics overlay)

                // Persistent playback controls — anchored at the bottom.
                // When the queue or lyrics is open, the title row is hidden (it's in the
                // mini header above) so only the seekbar + transport + volume + bottom
                // row render.
                //
                // Auto-hide: when lyrics is open, the controls auto-hide after 3s
                // (always-on in Apple Music style — no toggle). Touching the lyrics
                // overlay above restores them.
                AnimatedVisibility(
                    visible = !lyricsOpen || playerControlsExpanded,
                    enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { it / 6 },
                    exit = fadeOut(tween(140)) + slideOutVertically(tween(140)) { it / 8 },
                ) {
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
                        onQueueClick = toggleQueue,
                        onLyricsClick = toggleLyrics,
                        onSliderValueChange = onSliderValueChange,
                        onSliderValueChangeFinished = onSliderValueChangeFinished,
                        currentFormat = currentFormat,
                        onQualityChipClick = {
                            bottomSheetPageState.show { ShowMediaInfo(mediaMetadata.id) }
                        },
                        showTitleRow = !morphOpen,
                        isQueueActive = queueOpen,
                        isLyricsActive = lyricsOpen,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(bottom = contentBottomPadding),
                    )
                }
            } // end Column (morph area + controls)
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
    // When false, the title/artist row is hidden — used in QUEUE/LYRICS state
    // where the title lives in the mini header above.
    showTitleRow: Boolean = true,
    // Whether the in-place queue is currently open. Highlights the queue button.
    isQueueActive: Boolean = false,
    // Whether the in-place lyrics view is currently open. Highlights the lyrics button.
    isLyricsActive: Boolean = false,
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

    // The controls cluster (title → bottom action row) is ~300dp tall and is
    // bottom-anchored by the caller (it sits below the weight(1f) morph area,
    // so it must wrap its height — never fillMaxSize). Gaps tighten on short
    // screens so the rows never squeeze together or spill past the bottom
    // edge (SpaceEvenly did both once the transport icons grew to 52/62dp).
    //
    // Gap values are calibrated to match the Apple Music reference layout:
    // generous ~32-40dp between rows on standard screens, compressed on
    // shorter devices to prevent overflow.
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val compactHeight = screenHeight < 720.dp
    val veryCompactHeight = screenHeight < 620.dp
    val titleToScrubberGap = if (veryCompactHeight) 10.dp else if (compactHeight) 18.dp else 28.dp
    val scrubberToTransportGap = if (veryCompactHeight) 12.dp else if (compactHeight) 20.dp else 32.dp
    val transportToVolumeGap = if (veryCompactHeight) 10.dp else if (compactHeight) 18.dp else 28.dp
    val volumeToActionsGap = if (veryCompactHeight) 12.dp else if (compactHeight) 18.dp else 28.dp

    Column(
        modifier =
            modifier
                .fillMaxWidth()
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
    ) {
    // Title / artist row with star + more chips.
    // Hidden when showTitleRow = false (queue is open — the title lives in
    // the mini header above the queue list).
    if (showTitleRow) {
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
    }

    Spacer(Modifier.height(titleToScrubberGap))

    // Thin scrubber + elapsed / -remaining.
    Column {
        AppleMusicSeekBar(
            position = sliderPosition ?: position,
            duration = duration,
            onScrub = onSliderValueChange,
            onScrubFinished = onSliderValueChangeFinished,
        )
        Spacer(Modifier.height(6.dp))
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

    Spacer(Modifier.height(scrubberToTransportGap))

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

    Spacer(Modifier.height(transportToVolumeGap))

    // Flat volume slider with speaker glyphs. Uses the shared AppleMusicVolumeRow
    // which has proper drag tracking (dragging state + rememberUpdatedState) so the
    // fill follows the finger during a drag instead of lagging behind the rounded
    // device-volume step.
    AppleMusicVolumeRow(
        volume = volume,
        onVolumeChange = onVolumeChange,
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(volumeToActionsGap))

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
            tint = if (isLyricsActive) Color.White else Color.White.copy(alpha = 0.85f),
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
            tint = if (isQueueActive) Color.White else Color.White.copy(alpha = 0.85f),
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
    tint: Color = Color.White.copy(alpha = 0.85f),
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
            tint = tint,
            modifier = Modifier.size(AppleMusicBottomIconSize),
        )
    }
}

/**
 * Mini header shown at the top of the QUEUE / LYRICS state. Contains a small
 * artwork (shared element with the large COVER artwork), compact title/artist,
 * and like + more buttons. Mirrors ViviMusic's Player_v2 mini header exactly.
 *
 * @param onArtworkClick Called when the mini artwork is tapped. Restores the
 *   COVER state (morphs back to the full main player).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedTransitionScope.AppleMusicMiniHeader(
    artworkRequest: coil3.request.ImageRequest?,
    artworkUrl: String?,
    mediaMetadata: MediaMetadata,
    currentSongLiked: Boolean,
    titleActions: PlayerTitleActions,
    onToggleLike: () -> Unit,
    onMoreClick: () -> Unit,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onArtworkClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .padding(horizontal = AppleMusicContentPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Mini artwork — shared element with the large COVER artwork.
        // Tapping it restores the COVER state (morphs back to the main player).
        Box(
            modifier =
                Modifier
                    .size(AppleMusicMiniArtworkSize)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = false, radius = AppleMusicMiniArtworkSize / 2),
                        onClick = onArtworkClick,
                    )
                    .sharedBounds(
                        rememberSharedContentState(key = "amCoverArt"),
                        animatedVisibilityScope = animatedVisibilityScope,
                    ),
        ) {
            AsyncImage(
                model = artworkRequest ?: artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = mediaMetadata.title,
                style = MaterialTheme.typography.titleMedium,
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
                style = MaterialTheme.typography.titleSmall,
                color = Color.White.copy(alpha = 0.7f),
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
        AppleMusicChip(
            iconRes = if (currentSongLiked) R.drawable.player_star_filled else R.drawable.player_star,
            tint = Color.White,
            contentDescription = null,
            onClick = onToggleLike,
        )
        Spacer(Modifier.width(8.dp))
        AppleMusicChip(
            iconRes = R.drawable.player_more_horiz,
            tint = Color.White,
            contentDescription = null,
            onClick = onMoreClick,
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
