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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.ui.AspectRatioFrameLayout
import moe.rukamori.archivetune.R

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
 * Inline video player + controls overlay.
 *
 * Uses a SINGLE [VideoArtworkState] (created via [rememberVideoArtworkState])
 * that is shared between the inline surface and the fullscreen Dialog.
 * Toggling fullscreen does NOT recreate the ExoPlayer or re-resolve the
 * stream URL — the video continues playing seamlessly as the surface moves
 * between the inline slot and the fullscreen Dialog.
 *
 * When the user enters fullscreen:
 *   - The inline surface is removed from composition (the ExoPlayer's
 *     surface is detached from the inline view).
 *   - A fullscreen [Dialog] is rendered with a new [VideoArtworkSurface]
 *     that attaches to the same ExoPlayer.
 *   - The device is rotated to landscape orientation.
 *   - System bars (status + nav) are hidden for immersive playback.
 *
 * Controls: quality picker + fullscreen toggle. Captions button has been
 * removed per user request.
 *
 * @param onPlaybackFailed Called when the player cannot play the video.
 *   The parent should fall back to album artwork.
 */
@Composable
fun InlineVideoPlayer(
    videoId: String,
    isPlaying: Boolean,
    positionProvider: () -> Long,
    modifier: Modifier = Modifier,
    onPlaybackFailed: () -> Unit = {},
    onRequestPauseMain: () -> Unit = {},
    onRequestResumeMain: () -> Unit = {},
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
) {
    if (videoId.isBlank()) {
        onPlaybackFailed()
        return
    }

    var isFullscreen by rememberSaveable { mutableStateOf(false) }
    var preferredHeight by rememberSaveable { mutableStateOf<Int?>(null) }
    var availableHeights by remember { mutableStateOf<List<Int>>(emptyList()) }
    var qualityMenuOpen by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    // ── ONE shared ExoPlayer + state ──
    //
    // The state is created once per videoId and survives the inline ↔
    // fullscreen transition. Only the VideoArtworkSurface (the view layer)
    // moves; the ExoPlayer underneath keeps running. This means:
    //   - NO re-resolving the stream URL on fullscreen toggle
    //   - NO re-buffering / loading spinner on fullscreen toggle
    //   - NO audio continuing while video pauses
    //   - The video just keeps playing as the surface changes parents
    val state =
        rememberVideoArtworkState(
            videoId = videoId,
            isPlaying = isPlaying,
            positionProvider = positionProvider,
            preferredHeight = preferredHeight,
            onStreamResolved = { info ->
                availableHeights = info?.availableHeights.orEmpty()
            },
            onPlaybackFailed = onPlaybackFailed,
            onLoadingStateChange = { isLoading = it },
            onRequestPauseMain = onRequestPauseMain,
            onRequestResumeMain = onRequestResumeMain,
        )

    // ── Inline surface + controls (rendered only when NOT fullscreen) ──
    //
    // When isFullscreen is true, we skip rendering the inline surface
    // entirely. The VideoArtworkSurface in the fullscreen Dialog takes
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
            if (isLoading) {
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
            // (Captions button removed per user request.)
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
                                    preferredHeight = null
                                    qualityMenuOpen = false
                                },
                            )
                            availableHeights
                                .sortedDescending()
                                .forEach { h ->
                                    DropdownMenuItem(
                                        text = { Text(formatHeightLabel(h)) },
                                        onClick = {
                                            preferredHeight = h
                                            qualityMenuOpen = false
                                        },
                                    )
                                }
                        }
                    }
                }

                // Fullscreen toggle.
                IconButton(
                    onClick = { isFullscreen = true },
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

    // ── Fullscreen Dialog (rendered only when fullscreen) ──
    //
    // The Dialog renders its own VideoArtworkSurface attached to the SAME
    // ExoPlayer. Because the inline surface was removed from composition
    // (the `if (!isFullscreen)` block above), only ONE surface is attached
    // to the ExoPlayer at a time — the fullscreen surface.
    //
    // The ExoPlayer does NOT re-resolve the URL or re-buffer. The video
    // continues playing from exactly where it was — the surface just
    // changes parents.
    if (isFullscreen) {
        VideoFullscreenDialog(
            state = state,
            isLoading = isLoading,
            availableHeights = availableHeights,
            onDismiss = { isFullscreen = false },
            onQualityChange = { preferredHeight = it },
            resizeMode = resizeMode,
        )
    }
}

/**
 * Fullscreen video Dialog.
 *
 * Renders the video in a system-immersive (fullscreen, no status/nav bar)
 * [Dialog] with the SAME [VideoArtworkState] used by the inline player.
 * The ExoPlayer is shared — no re-creation, no re-loading. The video
 * continues playing seamlessly as the surface moves from the inline slot
 * to this Dialog.
 *
 * Forces landscape orientation on entry, restores the original orientation
 * on exit. System bars are hidden for immersive playback and restored on
 * dismiss.
 *
 * Controls: quality picker + fullscreen-exit button. (Captions button
 * removed per user request.)
 */
@Composable
private fun VideoFullscreenDialog(
    state: VideoArtworkState,
    isLoading: Boolean,
    availableHeights: List<Int>,
    onDismiss: () -> Unit,
    onQualityChange: (Int?) -> Unit,
    resizeMode: Int,
) {
    val context = LocalContext.current
    var qualityMenuOpen by remember { mutableStateOf(false) }

    // Dismiss the dialog if playback fails — don't leave the user stuck
    // in a fullscreen black screen with no way out.
    LaunchedEffect(state.hasPlaybackFailed) {
        if (state.hasPlaybackFailed) {
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
            ),
    ) {
        // ── Force landscape orientation + hide system bars ──
        //
        // On enter: set orientation to SENSOR_LANDSCAPE (allows both
        // landscape orientations based on device tilt) and hide system
        // bars for true immersive playback.
        // On dispose: restore the original orientation and show system bars.
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

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black),
        ) {
            // Same ExoPlayer — just a different surface. NO re-loading.
            VideoArtworkSurface(
                state = state,
                resizeMode = resizeMode,
                modifier = Modifier.fillMaxSize(),
            )

            // Loading overlay — same rationale as the inline player.
            if (isLoading) {
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

            // Top-right controls: quality | fullscreen-exit.
            // statusBarsPadding keeps the buttons clear of the (hidden)
            // status bar area in case the user swipes to reveal it.
            // (Captions button removed per user request.)
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
                                    onQualityChange(null)
                                    qualityMenuOpen = false
                                },
                            )
                            availableHeights
                                .sortedDescending()
                                .forEach { h ->
                                    DropdownMenuItem(
                                        text = { Text(formatHeightLabel(h)) },
                                        onClick = {
                                            onQualityChange(h)
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
        }
    }
}

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
