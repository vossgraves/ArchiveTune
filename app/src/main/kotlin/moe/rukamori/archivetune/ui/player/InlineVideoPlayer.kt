/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package moe.rukamori.archivetune.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.extensions.togglePlayPause
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf

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
                                imageVector = Icons.Filled.HighQuality,
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
                        imageVector = Icons.Filled.Fullscreen,
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
    // dragging the seekbar or has the quality menu open (those interactions
    // need the controls to stay visible).
    LaunchedEffect(controlsVisible, isUserSeeking, qualityMenuOpen) {
        if (controlsVisible && !isUserSeeking && !qualityMenuOpen) {
            kotlinx.coroutines.delay(FullscreenControlsAutoHideMs)
            controlsVisible = false
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            // Single tap toggles the controls overlay.
                            controlsVisible = !controlsVisible
                        },
                    )
                },
    ) {
        // Same ExoPlayer — just a different surface. NO re-loading.
        VideoArtworkSurface(
            state = state,
            resizeMode = resizeMode,
            modifier = Modifier.fillMaxSize(),
        )

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
        // Fades in/out based on controlsVisible. Three rows:
        //   - Top: quality picker + fullscreen-exit (statusBarsPadding)
        //   - Center: previous | play/pause | next
        //   - Bottom: seekbar + time labels (navigationBarsPadding)
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))) {
                // ── Top row: quality picker + fullscreen-exit ──
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
                                    imageVector = Icons.Filled.HighQuality,
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
                            imageVector = Icons.Filled.FullscreenExit,
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
                if (playerConnection != null) {
                    val isPlaying by playerConnection.isPlaying.collectAsState()
                    val canSkipNext by playerConnection.canSkipNext.collectAsState()
                    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsState()

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
                                imageVector = Icons.Filled.SkipPrevious,
                                contentDescription = stringResource(R.string.video_fs_previous),
                                tint = if (canSkipPrevious) Color.White else Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.size(40.dp),
                            )
                        }
                        IconButton(
                            onClick = { playerConnection.player.togglePlayPause() },
                            modifier =
                                Modifier
                                    .background(
                                        color = Color.Black.copy(alpha = 0.55f),
                                        shape = CircleShape,
                                    ).size(72.dp),
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
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
                                imageVector = Icons.Filled.SkipNext,
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
                        Slider(
                            value = if (seekEnabled) displayPosition.toFloat() else 0f,
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
                            valueRange = if (seekEnabled) 0f..duration.toFloat() else 0f..1f,
                            enabled = seekEnabled,
                            colors =
                                SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = Color.White,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                                ),
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
}

/**
 * Auto-hide delay for the fullscreen controls overlay. Matches YouTube's
 * ~3s feel — long enough to read the time labels, short enough to not
 * obscure the video.
 */
private const val FullscreenControlsAutoHideMs = 3_500L

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
