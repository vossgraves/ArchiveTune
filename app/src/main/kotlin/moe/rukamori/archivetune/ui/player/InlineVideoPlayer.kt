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
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ClosedCaptionOff
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
import moe.rukamori.archivetune.innertube.models.response.PlayerResponse

/**
 * Walk the [ContextWrapper] chain to find the hosting [Activity].
 *
 * Needed because [Dialog] creates its own window, but the system bar
 * insets controller we want to hide is the host Activity's. Hiding
 * the Activity's system bars cascades to child windows (the Dialog)
 * on most Android versions.
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
 * Wraps [VideoArtworkPlayer] with three icon buttons in the top-right corner:
 *  - Fullscreen toggle (enter/exit a system-immersive fullscreen Dialog)
 *  - Captions toggle (only visible if at least one caption track exists)
 *  - Quality picker (dropdown listing Auto + every resolution YouTube offered)
 *
 * State (fullscreen, captions, quality) is hoisted to this composable so
 * that it survives configuration changes and is consistent between the
 * inline and fullscreen surfaces.
 *
 * When the user enters fullscreen, a [VideoFullscreenDialog] is rendered
 * on top of everything. Both the inline surface and the fullscreen surface
 * use the same parameters (videoId, isPlaying, positionProvider, captions,
 * quality), so the video appears to "expand" seamlessly — in practice the
 * inline ExoPlayer pauses (because the fullscreen Dialog covers it and
 * gets the lifecycle focus) and the fullscreen ExoPlayer takes over,
 * seeking to the same main-player position. The brief rebuffer is
 * acceptable for a music app where the audio keeps playing through the
 * main MusicService regardless.
 *
 * @param onPlaybackFailed Called when the inline player cannot play the
 *   video (e.g. stream resolution exhausted all clients, or ExoPlayer
 *   emitted an error). The parent should fall back to album artwork.
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
    var isFullscreen by rememberSaveable { mutableStateOf(false) }
    var captionsEnabled by rememberSaveable { mutableStateOf(false) }
    var preferredHeight by rememberSaveable { mutableStateOf<Int?>(null) }
    var availableHeights by remember { mutableStateOf<List<Int>>(emptyList()) }
    var captionTracks by remember { mutableStateOf<List<PlayerResponse.CaptionTrack>>(emptyList()) }
    var qualityMenuOpen by remember { mutableStateOf(false) }
    // True while the inline VideoArtworkPlayer is (re)loading its stream —
    // either on initial mount or after the user picks a different quality.
    // We render a centered CircularProgressIndicator over the video surface
    // while this is true, so the user sees clear feedback that the video is
    // swapping resolutions. The main MusicService audio keeps playing
    // underneath, so the song doesn't skip.
    var isLoading by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        VideoArtworkPlayer(
            videoId = videoId,
            isPlaying = isPlaying,
            positionProvider = positionProvider,
            preferredHeight = preferredHeight,
            captionsEnabled = captionsEnabled,
            onStreamResolved = { info ->
                availableHeights = info?.availableHeights.orEmpty()
                captionTracks = info?.captionTracks.orEmpty()
            },
            onPlaybackFailed = onPlaybackFailed,
            onLoadingStateChange = { isLoading = it },
            onRequestPauseMain = onRequestPauseMain,
            onRequestResumeMain = onRequestResumeMain,
            resizeMode = resizeMode,
            modifier = Modifier.fillMaxSize(),
        )

        // ── Loading overlay ──
        //
        // Shown on initial load AND during a quality swap. The video
        // surface's alpha also animates to 0 while loading (handled inside
        // VideoArtworkPlayer), so the user just sees a black box + spinner.
        // The audio keeps playing through the main MusicService.
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

        // ── Controls overlay ──
        Row(
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Captions toggle — only render if at least one caption track exists.
            if (captionTracks.isNotEmpty()) {
                IconButton(
                    onClick = { captionsEnabled = !captionsEnabled },
                    modifier =
                        Modifier
                            .background(
                                color = Color.Black.copy(alpha = 0.45f),
                                shape = CircleShape,
                            ).size(40.dp),
                ) {
                    Icon(
                        imageVector =
                            if (captionsEnabled) {
                                Icons.Filled.ClosedCaption
                            } else {
                                Icons.Filled.ClosedCaptionOff
                            },
                        contentDescription = stringResource(R.string.video_captions),
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

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

    if (isFullscreen) {
        VideoFullscreenDialog(
            videoId = videoId,
            isPlaying = isPlaying,
            positionProvider = positionProvider,
            preferredHeight = preferredHeight,
            captionsEnabled = captionsEnabled,
            captionTracks = captionTracks,
            availableHeights = availableHeights,
            onDismiss = { isFullscreen = false },
            onCaptionsToggle = { captionsEnabled = !captionsEnabled },
            onQualityChange = { preferredHeight = it },
            onRequestPauseMain = onRequestPauseMain,
            onRequestResumeMain = onRequestResumeMain,
        )
    }
}

/**
 * Fullscreen video Dialog.
 *
 * Renders the video in a system-immersive (fullscreen, no status/nav bar)
 * [Dialog] with its own [VideoArtworkPlayer] instance. The inline player
 * keeps running underneath but is occluded; when the user exits fullscreen
 * the inline player is still alive and resumes immediately.
 *
 * Renders its own copy of the controls overlay (fullscreen-exit button
 * replaces fullscreen-enter, otherwise identical captions + quality
 * controls) so the user can adjust settings without leaving fullscreen.
 */
@Composable
private fun VideoFullscreenDialog(
    videoId: String,
    isPlaying: Boolean,
    positionProvider: () -> Long,
    preferredHeight: Int?,
    captionsEnabled: Boolean,
    captionTracks: List<PlayerResponse.CaptionTrack>,
    availableHeights: List<Int>,
    onDismiss: () -> Unit,
    onCaptionsToggle: () -> Unit,
    onQualityChange: (Int?) -> Unit,
    onRequestPauseMain: () -> Unit = {},
    onRequestResumeMain: () -> Unit = {},
) {
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
        var qualityMenuOpen by remember { mutableStateOf(false) }
        var fsLoading by remember { mutableStateOf(false) }
        val context = LocalContext.current

        // Hide system bars for true immersive fullscreen. Restored on dispose
        // (when the Dialog dismisses). Uses BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        // so a swipe from the edge temporarily reveals the bars — same pattern
        // the app uses for AOD mode in MainActivity.
        DisposableEffect(Unit) {
            val activity = context.findActivity()
            val window = activity?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                val originalBehavior = controller.systemBarsBehavior
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
                onDispose {
                    controller.show(WindowInsetsCompat.Type.systemBars())
                    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                }
            } else {
                onDispose {}
            }
        }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black),
        ) {
            VideoArtworkPlayer(
                videoId = videoId,
                isPlaying = isPlaying,
                positionProvider = positionProvider,
                preferredHeight = preferredHeight,
                captionsEnabled = captionsEnabled,
                onLoadingStateChange = { fsLoading = it },
                onRequestPauseMain = onRequestPauseMain,
                onRequestResumeMain = onRequestResumeMain,
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT,
                modifier = Modifier.fillMaxSize(),
            )

            // Loading overlay — same rationale as the inline player.
            if (fsLoading) {
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

            // Top-right controls: captions | quality | fullscreen-exit.
            // statusBarsPadding keeps the buttons clear of the (hidden)
            // status bar area in case the user swipes to reveal it.
            Row(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (captionTracks.isNotEmpty()) {
                    IconButton(
                        onClick = onCaptionsToggle,
                        modifier =
                            Modifier
                                .background(
                                    color = Color.Black.copy(alpha = 0.45f),
                                    shape = CircleShape,
                                ).size(44.dp),
                    ) {
                        Icon(
                            imageVector =
                                if (captionsEnabled) {
                                    Icons.Filled.ClosedCaption
                                } else {
                                    Icons.Filled.ClosedCaptionOff
                                },
                            contentDescription = stringResource(R.string.video_captions),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
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
