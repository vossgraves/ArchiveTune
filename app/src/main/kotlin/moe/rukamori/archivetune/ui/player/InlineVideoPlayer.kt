/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(androidx.media3.common.util.UnstableApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Job
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.SliderStyle
import moe.rukamori.archivetune.constants.SliderStyleKey
import moe.rukamori.archivetune.constants.VideoAmbientModeKey
import moe.rukamori.archivetune.constants.VideoAspectRatio
import moe.rukamori.archivetune.constants.VideoAspectRatioKey
import moe.rukamori.archivetune.constants.VideoPlaybackSpeedKey
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference

/**
 * Walk the [ContextWrapper] chain to find the hosting [Activity].
 */
private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

/**
 * Holder for the video fullscreen state, shared between the inline
 * [InlineVideoPlayer] and the host [BottomSheetPlayer] which renders the
 * [FullscreenVideoOverlay].
 *
 * WHY THIS EXISTS:
 * The fullscreen flag is hoisted above the BottomSheet so that the
 * [FullscreenVideoOverlay] (rendered as a sibling of the BottomSheet) can
 * read it. The overlay is NOT a Dialog — it's a regular composable that
 * fills the screen. This avoids the Compose `Dialog` window's orientation
 * issues that caused the "horizontal for a split second then back" flicker.
 *
 * The ExoPlayer itself is hoisted even higher — to the BottomSheetPlayer
 * function level via [rememberVideoArtworkStateOrNull] — so it survives
 * orientation changes, sheet collapse/expand, and fullscreen toggles
 * without being released or recreated.
 */
@Stable
class VideoFullscreenStateHolder {
    var isFullscreen: Boolean by mutableStateOf(false)
        internal set
}

/**
 * CompositionLocal that provides the [VideoFullscreenStateHolder] to all
 * [InlineVideoPlayer] instances in the subtree. The host (e.g.
 * [BottomSheetPlayer]) is responsible for providing this via
 * [ProvideVideoFullscreenState].
 *
 * If no provider is found, a no-op holder is used (fullscreen button will
 * still work locally but the overlay won't be rendered by the host).
 */
val LocalVideoFullscreenState = compositionLocalOf {
    VideoFullscreenStateHolder()
}

/**
 * CompositionLocal that provides the hoisted [VideoArtworkState] to all
 * [InlineVideoPlayer] instances in the subtree.
 *
 * This allows composables deep in the tree (e.g. V8PlayerContent,
 * AppleMusicPlayer, Thumbnail) to access the shared ExoPlayer without
 * needing it passed as an explicit parameter through every layer.
 *
 * If no provider is found, null is used — [InlineVideoPlayer] will call
 * [onPlaybackFailed] and render nothing.
 */
val LocalVideoArtworkState = compositionLocalOf<VideoArtworkState?> { null }

/**
 * CompositionLocal for the user's preferred video quality (null = auto).
 * @see LocalVideoArtworkState
 */
val LocalVideoPreferredHeight = compositionLocalOf<Int?> { null }

/**
 * CompositionLocal for the callback when the user changes the preferred
 * video quality.
 * @see LocalVideoArtworkState
 */
val LocalVideoOnPreferredHeightChange = compositionLocalOf<(Int?) -> Unit> { {} }

/**
 * CompositionLocal for the list of available video heights from YouTube.
 * @see LocalVideoArtworkState
 */
val LocalVideoAvailableHeights = compositionLocalOf<List<Int>> { emptyList() }

/**
 * Provides a [VideoFullscreenStateHolder] to the content subtree.
 *
 * The holder is created with `remember` (not `rememberSaveable`) because
 * the host Activity declares `configChanges="orientation|screenSize|..."` in
 * the manifest, which means the Activity (and its Compose tree) is NOT
 * recreated on orientation changes. `remember` is therefore sufficient to
 * survive orientation changes. `rememberSaveable` would require a custom
 * Saver and adds complexity for no benefit in this configuration.
 */
@Composable
fun ProvideVideoFullscreenState(content: @Composable () -> Unit) {
    val holder = remember { VideoFullscreenStateHolder() }
    CompositionLocalProvider(LocalVideoFullscreenState provides holder) {
        content()
    }
}

/**
 * Inline video player + controls overlay.
 *
 * This composable renders ONLY the inline surface + controls. It does NOT
 * create the [VideoArtworkState] — that's hoisted to the host
 * ([BottomSheetPlayer]) via [rememberVideoArtworkStateOrNull] so the
 * ExoPlayer survives orientation changes and sheet collapse/expand.
 *
 * The fullscreen overlay is also rendered by the host (as a sibling of the
 * BottomSheet), NOT by this composable. This avoids the Compose `Dialog`
 * window's orientation issues that caused the "horizontal for a split second
 * then back" flicker.
 *
 * Controls: quality picker + captions toggle + fullscreen button.
 *
 * @param state The hoisted [VideoArtworkState] (ExoPlayer + playback state).
 *   Null when there's no music video.
 * @param preferredHeight The user's preferred video quality (null = auto).
 * @param onPreferredHeightChange Called when the user picks a new quality.
 * @param availableHeights The list of available video heights from YouTube.
 * @param onPlaybackFailed Called when the player cannot play the video.
 *   The parent should fall back to album artwork.
 */
@Composable
fun InlineVideoPlayer(
    state: VideoArtworkState? = LocalVideoArtworkState.current,
    preferredHeight: Int? = LocalVideoPreferredHeight.current,
    onPreferredHeightChange: (Int?) -> Unit = LocalVideoOnPreferredHeightChange.current,
    availableHeights: List<Int> = LocalVideoAvailableHeights.current,
    modifier: Modifier = Modifier,
    onPlaybackFailed: () -> Unit = {},
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
) {
    if (state == null) {
        onPlaybackFailed()
        return
    }

    val fullscreenHolder = LocalVideoFullscreenState.current
    val isFullscreen = fullscreenHolder.isFullscreen

    var qualityMenuOpen by remember { mutableStateOf(false) }

    // Ambient mode is intentionally NOT applied here. The drifting blurred
    // backdrop only makes sense in the fullscreen landscape overlay where
    // the video is letterboxed against a black background — in the inline
    // portrait player the artwork is already displayed next to the video,
    // so an additional blurred copy would just be visual noise. The
    // fullscreen overlay reads the same preference and applies it.
    val playerConnection = LocalPlayerConnection.current
    val fallbackMetadataFlow = remember { kotlinx.coroutines.flow.MutableStateFlow<MediaMetadata?>(null) }
    val mediaMetadata by (playerConnection?.mediaMetadata ?: fallbackMetadataFlow).collectAsState()
    val thumbnailUrl = mediaMetadata?.thumbnailUrl

    // ── Inline surface + controls (rendered only when NOT fullscreen) ──
    //
    // When isFullscreen is true, we skip rendering the inline surface
    // entirely. The FullscreenVideoOverlay (rendered by the host) takes
    // over — attaching to the same ExoPlayer. This ensures only ONE
    // surface is attached to the ExoPlayer at any time.
    if (!isFullscreen) {
        Box(modifier = modifier) {
            VideoArtworkSurface(
                state = state,
                resizeMode = resizeMode,
                ambientMode = false,
                thumbnailUrl = thumbnailUrl,
                modifier = Modifier.fillMaxSize(),
            )

            // Loading overlay — shown during initial load, quality swap,
            // and seekbar resync. The video surface's alpha also animates
            // to 0 while loading.
            if (isLoadingState(state)) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(48.dp),
                    )
                }
            }

            // Controls overlay: quality picker + fullscreen button.
            // (Captions button removed per spec — captions are no longer
            // user-togglable.)
            Row(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Quality picker — only render if YouTube offered more than one height.
                if (availableHeights.size > 1) {
                    Box {
                        IconButton(
                            onClick = { qualityMenuOpen = true },
                            modifier =
                                Modifier
                                    .background(
                                        color = Color.Black.copy(alpha = 0.45f),
                                        shape = CircleShape,
                                    ).size(40.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.player_quality),
                                contentDescription = stringResource(R.string.video_quality),
                                tint = Color.White,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        DropdownMenu(
                            expanded = qualityMenuOpen,
                            onDismissRequest = { qualityMenuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.video_quality_auto)) },
                                onClick = {
                                    onPreferredHeightChange(null)
                                    qualityMenuOpen = false
                                },
                            )
                            availableHeights
                                .sortedDescending()
                                .forEach { h ->
                                    DropdownMenuItem(
                                        text = { Text(formatHeightLabel(h)) },
                                        onClick = {
                                            onPreferredHeightChange(h)
                                            qualityMenuOpen = false
                                        },
                                    )
                                }
                        }
                    }
                }

                // Fullscreen toggle — writes to the hoisted holder so the
                // host can render the FullscreenVideoOverlay.
                IconButton(
                    onClick = { fullscreenHolder.isFullscreen = true },
                    modifier =
                        Modifier
                            .background(
                                color = Color.Black.copy(alpha = 0.45f),
                                shape = CircleShape,
                            ).size(40.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.player_fullscreen),
                        contentDescription = stringResource(R.string.video_fullscreen),
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

/**
 * Fullscreen video overlay — a regular composable (NOT a Dialog).
 *
 * Renders the video in a system-immersive (fullscreen, no status/nav bar)
 * overlay using the SAME [VideoArtworkState] used by the inline player.
 * The ExoPlayer is shared — no re-creation, no re-loading. The video
 * continues playing seamlessly as the surface moves from the inline slot
 * to this overlay.
 *
 * WHY NOT A DIALOG:
 * The previous implementation used a Compose `Dialog`, which creates a
 * separate window. Setting `Activity.requestedOrientation = SENSOR_LANDSCAPE`
 * affects the Activity's main window, but the Dialog's window may not
 * reliably follow — causing the "horizontal for a split second then back"
 * flicker. By rendering this overlay as a regular composable in the same
 * window, the orientation change applies cleanly to the entire Activity.
 *
 * Forces landscape orientation on entry, restores the original orientation
 * on exit. System bars are hidden for immersive playback and restored on
 * dismiss.
 *
 * Controls (YouTube-style overlay):
 *   - HIDDEN by default. Tap the video to reveal. Tap again to hide.
 *   - Auto-hide after [FullscreenControlsAutoHideMs] of inactivity.
 *   - Top row: quality picker (if >1 height) + fullscreen-exit.
 *   - Center row: previous | play/pause | next.
 *   - Bottom row: seekbar + current/total time labels.
 *
 * Transport controls (play/pause/next/prev/seek) drive the MAIN audio player
 * via [LocalPlayerConnection]. The video follows via the existing A/V sync
 * logic in [rememberVideoArtworkState] — the audio player is the source of
 * truth for position, and the video drift poller keeps them aligned.
 */
@Composable
fun FullscreenVideoOverlay(
    state: VideoArtworkState,
    preferredHeight: Int?,
    onPreferredHeightChange: (Int?) -> Unit,
    availableHeights: List<Int>,
    onDismiss: () -> Unit,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    var qualityMenuOpen by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(false) }
    var isUserSeeking by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableStateOf<Long?>(null) }
    var showOverflowSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    // ── Gesture state ──
    // Transient on-screen indicator shown when the user performs a swipe
    // (brightness / volume) or double-tap (seek ±10s) gesture. Auto-clears
    // a short delay after the gesture ends.
    var gestureFeedback by remember { mutableStateOf<GestureFeedback?>(null) }

    // Brightness drag tracker — set to true while the user is actively
    // dragging on the left half of the screen, used to gate the onVerticalDrag
    // callback (which fires for both halves).
    var brightnessDragActive by remember { mutableStateOf(false) }

    // Volume drag tracker — same pattern as brightness, for the right half.
    var volumeDragActive by remember { mutableStateOf(false) }

    // The Job holders use `mutableStateOf<Job?>` but are NEVER read in the
    // composable body — only inside event lambdas — so changes to them do
    // not trigger recomposition.
    var brightnessGestureJob by remember { mutableStateOf<Job?>(null) }
    var volumeGestureJob by remember { mutableStateOf<Job?>(null) }

    // ── User-tunable fullscreen-overlay preferences ──
    // Slider style: shared with the main player via SliderStyleKey — same 5 styles.
    // Playback speed: applied to BOTH the audio ExoPlayer (here) and the video
    //   ExoPlayer (in rememberVideoArtworkState via a sibling LaunchedEffect).
    // Ambient mode: toggles the blurred-thumbnail backdrop behind the video.
    // Aspect ratio: cycles FIT / CROP / STRETCH / FILL — overrides [resizeMode]
    //   passed in by the host when the user picks a non-default aspect.
    val (sliderStyle, onSliderStyleChange) = rememberEnumPreference(SliderStyleKey, defaultValue = SliderStyle.Standard)
    val (playbackSpeed, onPlaybackSpeedChange) = rememberPreference(VideoPlaybackSpeedKey, defaultValue = 1.0f)
    val (ambientMode, onAmbientModeChange) = rememberPreference(VideoAmbientModeKey, defaultValue = false)
    val (aspectRatio, onAspectRatioChange) = rememberEnumPreference(VideoAspectRatioKey, defaultValue = VideoAspectRatio.FIT)

    // Effective resize mode: the user's aspect-ratio pick overrides the host's
    // default. This lets the user cycle through Fit/Crop/Stretch/Fill without
    // the host needing to know.
    val effectiveResizeMode = aspectRatio.toExoResizeMode()

    // Intercept the back button so it dismisses the fullscreen overlay
    // instead of collapsing the BottomSheet (which is what the sheet's own
    // BackHandler would do). This BackHandler takes priority because it's
    // composed after the BottomSheet's BackHandler.
    BackHandler { onDismiss() }

    // Dismiss the overlay if playback fails — don't leave the user stuck
    // in a fullscreen black screen with no way out.
    LaunchedEffect(state.hasPlaybackFailed) {
        if (state.hasPlaybackFailed) {
            onDismiss()
        }
    }

    // ── Apply playback speed to the AUDIO ExoPlayer ──
    // The video ExoPlayer is updated by a sibling LaunchedEffect in
    // rememberVideoArtworkState reading the same preference. Keeping both
    // at the same speed is what keeps audio + video aligned at non-1.0x.
    LaunchedEffect(playbackSpeed) {
        if (playerConnection == null) return@LaunchedEffect
        val safeSpeed = playbackSpeed.coerceIn(0.25f, 2f)
        val current = playerConnection.player.playbackParameters.speed
        if (kotlin.math.abs(current - safeSpeed) > 0.001f) {
            playerConnection.player.playbackParameters =
                PlaybackParameters(
                    safeSpeed,
                    playerConnection.player.playbackParameters.pitch,
                )
        }
    }

    // Mirror the audio player's current speed back into the preference so
    // the overflow menu shows the right value if it was changed elsewhere
    // (e.g. via the existing TempoPitchDialog). Poll every 2s — cheap.
    //
    // rememberUpdatedState is used so the while-loop polls the latest
    // playbackSpeed value across recompositions (a LaunchedEffect(Unit)
    // would otherwise capture the initial value forever).
    val latestPlaybackSpeed by rememberUpdatedState(playbackSpeed)
    val latestOnPlaybackSpeedChange by rememberUpdatedState(onPlaybackSpeedChange)
    LaunchedEffect(Unit) {
        if (playerConnection == null) return@LaunchedEffect
        while (true) {
            val currentSpeed = playerConnection.player.playbackParameters.speed
            if (kotlin.math.abs(currentSpeed - latestPlaybackSpeed) > 0.01f) {
                latestOnPlaybackSpeedChange(currentSpeed)
            }
            delay(2_000)
        }
    }

    // ── Force landscape orientation + hide system bars ──
    //
    // On enter: set orientation to SENSOR_LANDSCAPE (allows both
    // landscape orientations based on device tilt) and hide system
    // bars for true immersive playback.
    // On dispose: restore the original orientation and show system bars.
    //
    // This is a DisposableEffect tied to the overlay's lifecycle. As long
    // as the overlay is in the composition tree, the orientation stays
    // landscape. When the overlay is removed (onDismiss sets isFullscreen
    // = false → host stops rendering this composable), the orientation
    // is restored.
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        val originalOrientation = activity?.requestedOrientation
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val originalBehavior = controller?.systemBarsBehavior

        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        if (controller != null) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }

        onDispose {
            activity?.requestedOrientation =
                originalOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            if (controller != null) {
                controller.show(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    originalBehavior ?: WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            }
        }
    }

    // ── Auto-hide controls after inactivity ──
    //
    // Each time controlsVisible flips to true, start (or restart) a timer.
    // When it fires, hide the controls — UNLESS the user is actively
    // dragging the seekbar, has the quality menu open, or has the 3-dot
    // overflow sheet open (those interactions need the controls to stay
    // visible).
    LaunchedEffect(controlsVisible, isUserSeeking, qualityMenuOpen, showOverflowSheet) {
        if (controlsVisible && !isUserSeeking && !qualityMenuOpen && !showOverflowSheet) {
            kotlinx.coroutines.delay(FullscreenControlsAutoHideMs)
            controlsVisible = false
        }
    }

    // Thumbnail URL — collected once at the top level so we can pass it
    // into VideoArtworkSurface for ambient mode. Uses a fallback empty
    // flow when playerConnection is null so collectAsState has a stable
    // call site (Compose requires composables to be called unconditionally).
    val fallbackMetadataFlow = remember { kotlinx.coroutines.flow.MutableStateFlow<MediaMetadata?>(null) }
    val headerMetadata by (playerConnection?.mediaMetadata ?: fallbackMetadataFlow).collectAsState()
    val thumbnailUrl = headerMetadata?.thumbnailUrl

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                // Tap detector — toggles controls on single tap, dismisses
                // the overflow sheet if open, and seeks ±10s on double tap
                // (left half rewind, right half forward).
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset ->
                            if (showOverflowSheet) {
                                // Tapping outside the sheet dismisses it instead of toggling overlay.
                                scope.launch { sheetState.hide() }.invokeOnCompletion {
                                    if (!sheetState.isVisible) showOverflowSheet = false
                                }
                            } else {
                                // Single tap toggles the controls overlay.
                                controlsVisible = !controlsVisible
                                // Reset any visible gesture feedback so it doesn't linger.
                                gestureFeedback = null
                            }
                        },
                        onDoubleTap = { offset ->
                            // Double-tap seek: left half rewinds 10s, right half skips 10s.
                            // Suppress when the overflow sheet is open.
                            if (!showOverflowSheet && playerConnection != null) {
                                val width = size.width.toFloat()
                                val isLeftHalf = offset.x < width / 2f
                                val cur = playerConnection.player.currentPosition
                                val dur = playerConnection.player.duration
                                val target =
                                    if (isLeftHalf) {
                                        (cur - 10_000L).coerceAtLeast(0L)
                                    } else {
                                        (cur + 10_000L).coerceAtMost(
                                            if (dur > 0 && dur != C.TIME_UNSET) dur else Long.MAX_VALUE,
                                        )
                                    }
                                playerConnection.player.seekTo(target)
                                state.requestResync(target, playerConnection.player.playWhenReady)
                                gestureFeedback =
                                    GestureFeedback.Seek(
                                        forward = !isLeftHalf,
                                        showAt = System.currentTimeMillis(),
                                    )
                                // Auto-clear the seek feedback shortly after release.
                                volumeGestureJob?.cancel()
                                brightnessGestureJob?.cancel()
                                brightnessGestureJob =
                                    scope.launch {
                                        delay(GestureFeedbackLingerMs)
                                        if (gestureFeedback is GestureFeedback.Seek) {
                                            gestureFeedback = null
                                        }
                                    }
                                // Keep the controls visible so the user sees the seekbar move.
                                controlsVisible = true
                            }
                        },
                    )
                }
                // Vertical drag detector — routes by initial x position:
                // left half = screen brightness, right half = media volume.
                // Stacked as a separate pointerInput so it doesn't fight the
                // tap detector above — Compose dispatches the same pointer
                // events to both, and each only consumes what it handles
                // (tap detector ignores drags beyond slop, drag detector
                // ignores taps below slop).
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            val isLeftHalf = offset.x < size.width / 2f
                            if (isLeftHalf) {
                                brightnessDragActive = true
                            } else {
                                volumeDragActive = true
                            }
                        },
                        onDragEnd = {
                            if (brightnessDragActive) {
                                brightnessDragActive = false
                                brightnessGestureJob?.cancel()
                                brightnessGestureJob =
                                    scope.launch {
                                        delay(GestureFeedbackLingerMs)
                                        if (gestureFeedback is GestureFeedback.Brightness) {
                                            gestureFeedback = null
                                        }
                                    }
                            }
                            if (volumeDragActive) {
                                volumeDragActive = false
                                volumeGestureJob?.cancel()
                                volumeGestureJob =
                                    scope.launch {
                                        delay(GestureFeedbackLingerMs)
                                        if (gestureFeedback is GestureFeedback.Volume) {
                                            gestureFeedback = null
                                        }
                                    }
                            }
                        },
                        onDragCancel = {
                            brightnessDragActive = false
                            volumeDragActive = false
                            gestureFeedback = null
                        },
                        onVerticalDrag = { change, dragAmount ->
                            val isLeftHalf = change.position.x < size.width / 2f
                            if (isLeftHalf && brightnessDragActive) {
                                // dragAmount.y is the incremental delta
                                // since the last event. We invert it so
                                // swiping UP (negative y) increases
                                // brightness. Each 600px of cumulative
                                // drag sweeps the full 0..1 range.
                                val delta = -dragAmount / 600f
                                val next = (currentWindowBrightness(context) + delta).coerceIn(0f, 1f)
                                applyWindowBrightness(context, next)
                                gestureFeedback =
                                    GestureFeedback.Brightness(
                                        percent = (next * 100f).toInt(),
                                        showAt = System.currentTimeMillis(),
                                    )
                                controlsVisible = false
                            } else if (!isLeftHalf && volumeDragActive) {
                                val maxVol = maxMediaVolume(context)
                                if (maxVol > 0) {
                                    val delta = (-dragAmount / 600f) * maxVol
                                    val currentVol = audioManager(context)?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
                                    val next = (currentVol + delta).toInt().coerceIn(0, maxVol)
                                    setMediaVolume(context, next)
                                    gestureFeedback =
                                        GestureFeedback.Volume(
                                            percent = (next * 100 / maxVol),
                                            showAt = System.currentTimeMillis(),
                                        )
                                    controlsVisible = false
                                }
                            }
                        },
                    )
                },
    ) {
        // Same ExoPlayer — just a different surface. NO re-loading.
        // The user's aspect-ratio pick overrides the host's resizeMode.
        // Ambient mode renders a blurred-thumbnail backdrop behind the video
        // when toggled on in the 3-dot overflow menu. Ambient mode is only
        // applied here in the fullscreen landscape overlay — the inline
        // portrait player ignores the ambientMode preference (see
        // [InlineVideoPlayer]).
        VideoArtworkSurface(
            state = state,
            resizeMode = effectiveResizeMode,
            ambientMode = ambientMode,
            thumbnailUrl = thumbnailUrl,
            modifier = Modifier.fillMaxSize(),
        )

        // ── Gesture feedback overlay (brightness / volume / double-tap seek) ──
        // Renders a pill-shaped indicator near the center of the screen with
        // the appropriate icon + percentage or "+10s / -10s" label. Fades out
        // 800ms after the gesture ends (handled by the onDragEnd / onDoubleTap
        // launch above).
        gestureFeedback?.let { feedback -> GestureFeedbackBubble(feedback) }

        // Loading overlay — same rationale as the inline player. Always
        // visible during loading regardless of controlsVisible.
        if (isLoadingState(state)) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(56.dp),
                )
            }
        }

        // ── Controls overlay (YouTube-style) ──
        //
        // Fades in/out based on controlsVisible. Four regions:
        //   - Top-left: song title + artist (marquee)
        //   - Top-right: quality picker (if >1 height) + 3-dot overflow + fullscreen-exit
        //   - Center: previous | play/pause | next
        //   - Bottom: seekbar (uses selected slider style) + time labels
        AnimatedVisibility(
            visible = controlsVisible && !showOverflowSheet,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))) {
                // ── Top-left: song title + artist ──
                // Displayed only when controls are visible, alongside the
                // top-right action row. Marquee-clipped to one line.
                // Uses the hoisted [headerMetadata] collected above (avoids
                // a second collectAsState call here).
                headerMetadata?.let { meta ->
                    Column(
                        modifier =
                            Modifier
                                .align(Alignment.TopStart)
                                .statusBarsPadding()
                                .padding(start = 16.dp, top = 8.dp, end = 160.dp),
                    ) {
                        Text(
                            text = meta.title,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.basicMarquee(),
                        )
                        val artistText = meta.artists.joinToString(", ") { it.name }
                        if (artistText.isNotBlank()) {
                            Text(
                                text = artistText,
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.basicMarquee(),
                            )
                        }
                    }
                }

                // ── Top-right row: quality picker + 3-dot overflow + fullscreen-exit ──
                Row(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (availableHeights.size > 1) {
                        Box {
                            IconButton(
                                onClick = { qualityMenuOpen = true },
                                modifier =
                                    Modifier
                                        .background(
                                            color = Color.Black.copy(alpha = 0.45f),
                                            shape = CircleShape,
                                        ).size(44.dp),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.player_quality),
                                    contentDescription = stringResource(R.string.video_quality),
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                            DropdownMenu(
                                expanded = qualityMenuOpen,
                                onDismissRequest = { qualityMenuOpen = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.video_quality_auto)) },
                                    onClick = {
                                        onPreferredHeightChange(null)
                                        qualityMenuOpen = false
                                    },
                                )
                                availableHeights
                                    .sortedDescending()
                                    .forEach { h ->
                                        DropdownMenuItem(
                                            text = { Text(formatHeightLabel(h)) },
                                            onClick = {
                                                onPreferredHeightChange(h)
                                                qualityMenuOpen = false
                                            },
                                        )
                                    }
                            }
                        }
                    }

                    // 3-dot overflow button — opens the ModalBottomSheet
                    // with slider style / playback speed / ambient mode /
                    // aspect ratio options. Uses the same player_more_vert
                    // drawable as the rest of the app for visual consistency.
                    IconButton(
                        onClick = { showOverflowSheet = true },
                        modifier =
                            Modifier
                                .background(
                                    color = Color.Black.copy(alpha = 0.45f),
                                    shape = CircleShape,
                                ).size(44.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.player_more_vert),
                            contentDescription = stringResource(R.string.video_overflow_menu),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier =
                            Modifier
                                .background(
                                    color = Color.Black.copy(alpha = 0.45f),
                                    shape = CircleShape,
                                ).size(44.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.player_fullscreen_exit),
                            contentDescription = stringResource(R.string.video_exit_fullscreen),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }

                // ── Center row: previous | play/pause | next ──
                //
                // Transport controls drive the MAIN audio player. The video
                // follows via the A/V sync logic. If the user pauses, both
                // audio and video pause; if the user skips, the new song's
                // video loads with the "start together" hold.
                //
                // Uses the SAME player_skip_previous / player_skip_next /
                // player_play / player_pause / player_replay drawables as
                // the normal (portrait) player so the icons match across
                // layouts.
                if (playerConnection != null) {
                    val isPlaying by playerConnection.isPlaying.collectAsState()
                    val canSkipNext by playerConnection.canSkipNext.collectAsState()
                    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsState()
                    val playbackStateFs by playerConnection.playbackState.collectAsState()

                    Row(
                        modifier =
                            Modifier
                                .align(Alignment.Center)
                                .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(32.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = { playerConnection.seekToPrevious() },
                            enabled = canSkipPrevious,
                            modifier = Modifier.size(56.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.player_skip_previous),
                                contentDescription = stringResource(R.string.video_fs_previous),
                                tint = if (canSkipPrevious) Color.White else Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.size(40.dp),
                            )
                        }
                        IconButton(
                            onClick = {
                                if (playbackStateFs == Player.STATE_ENDED) {
                                    playerConnection.player.seekTo(0, 0)
                                    playerConnection.player.playWhenReady = true
                                } else {
                                    playerConnection.player.togglePlayPause()
                                }
                            },
                            modifier =
                                Modifier
                                    .background(
                                        color = Color.Black.copy(alpha = 0.55f),
                                        shape = CircleShape,
                                    ).size(72.dp),
                        ) {
                            val playIcon =
                                when {
                                    playbackStateFs == Player.STATE_ENDED -> R.drawable.player_replay
                                    isPlaying -> R.drawable.player_pause
                                    else -> R.drawable.player_play
                                }
                            Icon(
                                painter = painterResource(playIcon),
                                contentDescription = stringResource(R.string.video_fs_play_pause),
                                tint = Color.White,
                                modifier = Modifier.size(44.dp),
                            )
                        }
                        IconButton(
                            onClick = { playerConnection.seekToNext() },
                            enabled = canSkipNext,
                            modifier = Modifier.size(56.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.player_skip_next),
                                contentDescription = stringResource(R.string.video_fs_next),
                                tint = if (canSkipNext) Color.White else Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.size(40.dp),
                            )
                        }
                    }

                    // ── Bottom row: seekbar + time labels ──
                    //
                    // The seekbar reads from the MAIN audio player's position
                    // (source of truth). On seek-finish, it calls
                    // playerConnection.player.seekTo() and then
                    // state.requestResync() so the video performs a
                    // pause-load-resume to the new position.
                    //
                    // The slider uses [StyledPlaybackSlider] with the user's
                    // selected [sliderStyle] — same 5 styles (Standard / Wavy
                    // / Thick / Circular / Simple) as the main player.
                    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
                    val currentPosition = remember(mediaMetadata?.id) {
                        mutableLongStateOf(playerConnection.player.currentPosition)
                    }
                    val totalDuration = remember(mediaMetadata?.id) {
                        mutableLongStateOf(playerConnection.player.duration)
                    }
                    val playbackState by playerConnection.playbackState.collectAsState()
                    LaunchedEffect(mediaMetadata?.id, playbackState) {
                        if (playbackState == Player.STATE_READY) {
                            while (isActive) {
                                delay(100)
                                if (!isUserSeeking) {
                                    currentPosition.longValue = playerConnection.player.currentPosition
                                    totalDuration.longValue = playerConnection.player.duration
                                }
                            }
                        }
                    }

                    val duration = totalDuration.longValue
                    val seekEnabled = duration > 0L && duration != C.TIME_UNSET
                    val displayPosition = sliderPosition ?: currentPosition.longValue.coerceIn(0L, duration.coerceAtLeast(0L))

                    Column(
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        StyledPlaybackSlider(
                            sliderStyle = sliderStyle,
                            value = if (seekEnabled) displayPosition.toFloat() else 0f,
                            valueRange = if (seekEnabled) 0f..duration.toFloat() else 0f..1f,
                            onValueChange = { newValue ->
                                if (seekEnabled) {
                                    isUserSeeking = true
                                    sliderPosition = newValue.toLong()
                                }
                            },
                            onValueChangeFinished = {
                                sliderPosition?.let { target ->
                                    playerConnection.player.seekTo(target)
                                    // Request a pause-load-resume on the video so it
                                    // jumps to the new position in sync with audio.
                                    state.requestResync(target, playerConnection.player.playWhenReady)
                                }
                                isUserSeeking = false
                                sliderPosition = null
                            },
                            activeColor = Color.White,
                            isPlaying = playerConnection.player.playWhenReady,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = formatTime(displayPosition),
                                color = Color.White,
                                fontSize = 13.sp,
                            )
                            Text(
                                text = formatTime(duration),
                                color = Color.White,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }
        }
    }

    // ── 3-dot overflow bottom sheet ──
    // Renders on top of the fullscreen overlay when showOverflowSheet is
    // true. Tapping outside the sheet dismisses it.
    if (showOverflowSheet) {
        ModalBottomSheet(
            onDismissRequest = { showOverflowSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            VideoOverflowSheetContent(
                sliderStyle = sliderStyle,
                onSliderStyleChange = onSliderStyleChange,
                playbackSpeed = playbackSpeed,
                onPlaybackSpeedChange = onPlaybackSpeedChange,
                ambientMode = ambientMode,
                onAmbientModeChange = onAmbientModeChange,
                aspectRatio = aspectRatio,
                onAspectRatioChange = onAspectRatioChange,
            )
        }
    }
}

/**
 * Auto-hide delay for the fullscreen controls overlay. Matches YouTube's
 * ~3s feel — long enough to read the time labels, short enough to not
 * obscure the video.
 */
private const val FullscreenControlsAutoHideMs = 3_500L

/**
 * Content of the 3-dot overflow [ModalBottomSheet] shown from the fullscreen
 * video overlay. Renders four user-tunable options:
 *
 *  1. **Slider style** — 5 pill toggles (Standard / Wavy / Thick / Circular /
 *     Simple). Shares [SliderStyleKey] with the main player so a change here
 *     applies globally to the next render of any seekbar using
 *     [StyledPlaybackSlider].
 *  2. **Playback speed** — Slider (0.25–2.0x, 0.25 step) + "Normal" pill.
 *     Applies to BOTH the audio ExoPlayer (in MusicService) and the video
 *     ExoPlayer (in VideoArtworkState) so audio + video stay aligned at
 *     non-1.0x speeds.
 *  3. **Ambient mode** — Switch. When on, a slowly drifting blurred copy of
 *     the song thumbnail is rendered behind the video surface so the
 *     letterbox bars glow with the artwork's colors (YouTube-style ambient
 *     mode).
 *  4. **Aspect ratio** — 4 pill toggles (Fit / Crop / Stretch / Fill).
 *     Maps to ExoPlayer's AspectRatioFrameLayout resize modes.
 */
@Composable
private fun VideoOverflowSheetContent(
    sliderStyle: SliderStyle,
    onSliderStyleChange: (SliderStyle) -> Unit,
    playbackSpeed: Float,
    onPlaybackSpeedChange: (Float) -> Unit,
    ambientMode: Boolean,
    onAmbientModeChange: (Boolean) -> Unit,
    aspectRatio: VideoAspectRatio,
    onAspectRatioChange: (VideoAspectRatio) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .systemBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // ── 1. Slider style ──
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.video_slider_style),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SliderStyle.entries.forEach { style ->
                    val labelRes =
                        when (style) {
                            SliderStyle.Standard -> R.string.slider_style_standard
                            SliderStyle.Wavy -> R.string.slider_style_wavy
                            SliderStyle.Thick -> R.string.slider_style_thick
                            SliderStyle.Circular -> R.string.slider_style_circular
                            SliderStyle.Simple -> R.string.slider_style_simple
                        }
                    PillToggle(
                        text = stringResource(labelRes),
                        selected = style == sliderStyle,
                        onClick = { onSliderStyleChange(style) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // ── 2. Playback speed ──
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.video_playback_speed),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.video_playback_speed_value, playbackSpeed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Slider(
                value = playbackSpeed.coerceIn(0.25f, 2f),
                onValueChange = { onPlaybackSpeedChange(it) },
                valueRange = 0.25f..2f,
                steps = 6, // 0.25-step granularity: 0.25, 0.50, 0.75, 1.00, 1.25, 1.50, 1.75, 2.00
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.video_playback_speed_value, 0.25f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PillToggle(
                    text = stringResource(R.string.video_playback_speed_normal),
                    selected = kotlin.math.abs(playbackSpeed - 1f) < 0.01f,
                    onClick = { onPlaybackSpeedChange(1f) },
                )
                Text(
                    text = stringResource(R.string.video_playback_speed_value, 2f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ── 3. Ambient mode ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.video_ambient_mode),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.video_ambient_mode_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = ambientMode,
                onCheckedChange = onAmbientModeChange,
            )
        }

        // ── 4. Aspect ratio ──
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.video_aspect_ratio),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AspectRatioOption(VideoAspectRatio.FIT, aspectRatio, onAspectRatioChange, R.string.video_aspect_fit, Modifier.weight(1f))
                AspectRatioOption(VideoAspectRatio.CROP, aspectRatio, onAspectRatioChange, R.string.video_aspect_crop, Modifier.weight(1f))
                AspectRatioOption(VideoAspectRatio.STRETCH, aspectRatio, onAspectRatioChange, R.string.video_aspect_stretch, Modifier.weight(1f))
                AspectRatioOption(VideoAspectRatio.FILL, aspectRatio, onAspectRatioChange, R.string.video_aspect_fill, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AspectRatioOption(
    value: VideoAspectRatio,
    current: VideoAspectRatio,
    onSelect: (VideoAspectRatio) -> Unit,
    labelRes: Int,
    modifier: Modifier = Modifier,
) {
    val selected = value == current
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier =
            modifier
                .background(bg, CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onSelect(value) },
                ).padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        // Add a semantic description so screen readers announce which
        // option is currently selected when the user navigates here.
    ) {
        // Leading check icon — visible ONLY on the currently selected
        // aspect-ratio option. This makes the active choice unmistakable
        // at a glance, even with the colored pill background.
        if (selected) {
            Icon(
                painter = painterResource(R.drawable.check),
                contentDescription = stringResource(R.string.video_aspect_selected),
                tint = fg,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = stringResource(labelRes),
            color = fg,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PillToggle(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier =
            modifier
                .background(bg, CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ).padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = fg,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Format milliseconds as M:SS or H:MM:SS.
 */
private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

/**
 * Compute the loading state from the [VideoArtworkState].
 *
 * The player is considered "loading" when:
 *   - It hasn't failed (failed states show an error, not a spinner).
 *   - AND any of:
 *     - The stream URL is being resolved ([VideoArtworkState.isResolvingUrl]).
 *     - A seekbar resync is in progress ([VideoArtworkState.isResyncing]).
 *     - The stream URL is set but the first frame hasn't rendered yet
 *       ([VideoArtworkState.streamUrl] != null && ![VideoArtworkState.isVideoReady]).
 *
 * Quality changes are NOT included here because the inline surface stays
 * visible (with the old frame) during a quality swap — only the fullscreen
 * overlay shows a spinner during quality changes.
 */
private fun isLoadingState(state: VideoArtworkState): Boolean =
    !state.hasPlaybackFailed &&
        (
            state.isResolvingUrl ||
                state.isResyncing ||
                (state.streamUrl != null && !state.isVideoReady)
        )

/**
 * Format a video height (in px) as a human-readable label.
 * 1440 → "1440p (QHD)"
 * 2160 → "2160p (4K)"
 * 720  → "720p (HD)"
 * 480  → "480p (SD)"
 * 360  → "360p"
 * 240  → "240p"
 * 144  → "144p"
 */
private fun formatHeightLabel(height: Int): String {
    val qualityName =
        when (height) {
            2160 -> " (4K)"
            1440 -> " (QHD)"
            1080 -> " (FHD)"
            720 -> " (HD)"
            480 -> " (SD)"
            else -> ""
        }
    return "${height}p$qualityName"
}

// ─────────────────────────────────────────────────────────────────────────────
// Fullscreen overlay gesture support
// ─────────────────────────────────────────────────────────────────────────────

/**
 * How long the gesture feedback bubble stays visible after the gesture ends.
 * Tuned to match YouTube's ~700ms feel — long enough to read, short enough
 * not to feel laggy.
 */
private const val GestureFeedbackLingerMs = 800L

/**
 * Sealed hierarchy of transient on-screen indicators shown when the user
 * performs a swipe (brightness / volume) or double-tap (seek ±10s) gesture
 * on the fullscreen video overlay.
 *
 * Each variant carries a `showAt` epoch-millis timestamp so the renderer
 * can animate the bubble's fade-out relative to the gesture's end time.
 */
private sealed interface GestureFeedback {
    val showAt: Long

    /**
     * Brightness gesture. [percent] is the current window brightness as an
     * integer 0..100 (rounded from the underlying 0..1 float).
     */
    data class Brightness(val percent: Int, override val showAt: Long) : GestureFeedback

    /**
     * Volume gesture. [percent] is the current media volume as an integer
     * 0..100 of the device's max media volume.
     */
    data class Volume(val percent: Int, override val showAt: Long) : GestureFeedback

    /**
     * Double-tap seek gesture. [forward] is true for skip-forward (right
     * half) and false for rewind (left half).
     */
    data class Seek(val forward: Boolean, override val showAt: Long) : GestureFeedback
}

/**
 * Renders the transient gesture feedback bubble centered on the screen.
 *
 * Layout: a dark pill with the appropriate icon on top and a percentage
 * (brightness / volume) or "+10s / -10s" label below. The bubble does NOT
 * auto-animate out — that's handled by the caller, which clears the
 * [GestureFeedback] state after [GestureFeedbackLingerMs] via a coroutine.
 *
 * Must be called from inside a [Box] composable so the [BoxScope.align]
 * modifier can position the bubble at the center.
 */
@Composable
private fun BoxScope.GestureFeedbackBubble(feedback: GestureFeedback) {
    val (iconRes, label) =
        when (feedback) {
            is GestureFeedback.Brightness -> {
                val res =
                    when {
                        feedback.percent <= 0 -> R.drawable.brightness_low
                        feedback.percent >= 100 -> R.drawable.brightness_high
                        else -> R.drawable.brightness_auto
                    }
                res to stringResource(R.string.percentage_format, feedback.percent)
            }
            is GestureFeedback.Volume -> {
                val res =
                    if (feedback.percent <= 0) R.drawable.player_volume_off else R.drawable.player_volume_up
                res to stringResource(R.string.percentage_format, feedback.percent)
            }
            is GestureFeedback.Seek -> {
                val res =
                    if (feedback.forward) R.drawable.player_fast_forward else R.drawable.player_fast_rewind
                val textRes =
                    if (feedback.forward) R.string.video_gesture_seek_forward else R.string.video_gesture_seek_backward
                res to stringResource(textRes)
            }
        }
    Box(
        modifier =
            Modifier
                .align(Alignment.Center)
                .background(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(16.dp),
                ).padding(horizontal = 24.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(32.dp),
            )
            Text(
                text = label,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ── Brightness helpers ──
//
// The screen brightness is applied to the Activity's window via
// WindowManager.LayoutParams.screenBrightness. A value of
// BRIGHTNESS_OVERRIDE_NONE (-1f) means "use system default"; any value in
// 0..1f overrides it for the duration of this window.
//
// We do NOT persist the brightness — the change is local to the fullscreen
// overlay's window and reverts automatically when the overlay is dismissed
// (the host Activity recreates the window attributes). This matches the
// behavior of most video player apps: the manual brightness only applies
// while watching a video.

/**
 * Read the current window brightness. Returns a value in 0..1f, or the
 * system default (interpreted as 0.5f for UI purposes) if the window is
 * using BRIGHTNESS_OVERRIDE_NONE.
 */
private fun currentWindowBrightness(context: Context): Float {
    val activity = context.findActivity() ?: return 0.5f
    val attrs = activity.window.attributes
    return if (attrs.screenBrightness == WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) {
        // Read the system brightness as the baseline.
        val system =
            try {
                Settings.System.getInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            } catch (e: Settings.SettingNotFoundException) {
                128
            }
        // System brightness is 0..255 — normalize to 0..1.
        system / 255f
    } else {
        attrs.screenBrightness
    }
}

/**
 * Apply a window brightness override in the 0..1 range. Values outside
 * that range are coerced. A value of 0 fully dims the screen (the
 * Backlight can't actually go to 0 on most devices, but 0.01f is close
 * enough for UX purposes).
 */
private fun applyWindowBrightness(context: Context, brightness: Float) {
    val activity = context.findActivity() ?: return
    val window = activity.window ?: return
    val params = window.attributes
    params.screenBrightness = brightness.coerceIn(0f, 1f)
    window.attributes = params
}

// ── Volume helpers ──
//
// We adjust STREAM_MUSIC via AudioManager — this is the same stream the
// main MusicService ExoPlayer uses, so the change is global and persists
// after the overlay is dismissed. This matches user expectations: they
// explicitly asked to change the volume, so the change should stick.

private fun audioManager(context: Context): AudioManager? =
    context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

private fun maxMediaVolume(context: Context): Int =
    audioManager(context)?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 0

private fun setMediaVolume(context: Context, volume: Int) {
    val am = audioManager(context) ?: return
    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    am.setStreamVolume(
        AudioManager.STREAM_MUSIC,
        volume.coerceIn(0, max),
        AudioManager.FLAG_SHOW_UI,
    )
}
