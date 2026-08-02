/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package moe.rukamori.archivetune.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.NewPipeUtils
import moe.rukamori.archivetune.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_65_10
import moe.rukamori.archivetune.innertube.models.YouTubeClient.Companion.WEB_REMIX
import moe.rukamori.archivetune.innertube.models.response.PlayerResponse
import moe.rukamori.archivetune.utils.StreamClientUtils
import okhttp3.OkHttpClient
import timber.log.Timber
import java.util.Locale

/**
 * Maximum allowed drift between the main audio player's position and the
 * video surface's position before we force a re-seek. Anything above this
 * is perceptible to the user as audio/video desync.
 */
private const val VideoSyncDriftToleranceMs = 1_500L

/**
 * How often to poll the main player's position and re-sync the video.
 */
private const val VideoSyncPollIntervalMs = 1_000L

/**
 * Native inline video surface for music-video playback.
 *
 * Renders the video track of the currently playing song in place of the
 * album artwork — mirroring how YouTube Music shows music videos. The
 * underlying audio continues to play through the main [MusicService]
 * ExoPlayer (which is audio-only), so all transport controls (play/pause,
 * next/prev, seek bar, queue) work exactly as they do for songs. This
 * composable only renders the video frames, with its own audio track
 * disabled to avoid double-audio.
 *
 * Stream resolution mirrors the audio pipeline: we call the YouTube InnerTube
 * player endpoint for the song's videoId, then pick a combined video+audio
 * format (itag 18/22/59) or fall back to a video-only adaptive format. The
 * URL is deobfuscated via [NewPipeUtils.getStreamUrl] (handles both direct
 * URLs and signatureCipher formats).
 *
 * Position sync: on mount we seek to the main player's current position,
 * and a periodic poller re-seeks if drift exceeds [VideoSyncDriftToleranceMs].
 * Play/pause follows [isPlaying].
 *
 * @param videoId The YouTube video ID (same as the song's mediaId for YouTube Music songs).
 * @param isPlaying Whether the main audio player is currently playing.
 * @param positionProvider Returns the main audio player's current position in ms.
 * @param modifier Modifier for the surface.
 * @param resizeMode AspectRatioFrameLayout resize mode (default FIT to letterbox within the artwork slot).
 */
@Composable
fun VideoArtworkPlayer(
    videoId: String,
    isPlaying: Boolean,
    positionProvider: () -> Long,
    modifier: Modifier = Modifier,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
) {
    if (videoId.isBlank()) return

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val shouldPlay by rememberUpdatedState(isPlaying)
    val currentPosition by rememberUpdatedState(positionProvider)

    var streamUrl by remember(videoId) { mutableStateOf<String?>(null) }
    var isVideoReady by remember(videoId) { mutableStateOf(false) }
    var hasPlaybackFailed by remember(videoId) { mutableStateOf(false) }

    // ── OkHttp client with the YouTube stream proxy + request profile headers ──
    val okHttpClient =
        remember {
            OkHttpClient
                .Builder()
                .proxy(YouTube.streamOkHttpProxy)
                .addInterceptor { chain ->
                    val request = chain.request()
                    val host = request.url.host
                    val isYouTubeMediaHost =
                        host.endsWith("googlevideo.com") ||
                            host.endsWith("googleusercontent.com") ||
                            host.endsWith("youtube.com") ||
                            host.endsWith("youtube-nocookie.com") ||
                            host.endsWith("ytimg.com")

                    if (!isYouTubeMediaHost) {
                        return@addInterceptor chain.proceed(
                            request
                                .newBuilder()
                                .header("User-Agent", VideoPlaybackUserAgent)
                                .build(),
                        )
                    }

                    val requestProfile = StreamClientUtils.resolveRequestProfile(request.url)
                    chain.proceed(
                        StreamClientUtils
                            .applyRequestProfile(
                                request.newBuilder(),
                                requestProfile,
                            ).build(),
                    )
                }.build()
        }

    val mediaSourceFactory =
        remember(okHttpClient) {
            DefaultMediaSourceFactory(
                DefaultDataSource.Factory(
                    context,
                    OkHttpDataSource.Factory(okHttpClient),
                ),
            )
        }

    val renderersFactory =
        remember(context) {
            DefaultRenderersFactory(context).setEnableDecoderFallback(true)
        }

    // Disable the audio track — the main MusicService ExoPlayer is the
    // source of truth for audio. Playing audio here would double it.
    val trackSelector =
        remember(context) {
            DefaultTrackSelector(context).apply {
                setParameters(
                    buildUponParameters()
                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                        .setForceHighestSupportedBitrate(true)
                        .build(),
                )
            }
        }

    val exoPlayer =
        remember(videoId, mediaSourceFactory, renderersFactory, trackSelector) {
            ExoPlayer
                .Builder(context)
                .setMediaSourceFactory(mediaSourceFactory)
                .setRenderersFactory(renderersFactory)
                .setTrackSelector(trackSelector)
                .build()
                .apply {
                    volume = 0f
                    playWhenReady = isPlaying
                }
        }

    // ── Resolve the video stream URL off the main thread ──
    //
    // We try multiple YouTube clients in order, preferring ones that return
    // direct stream URLs (no signatureCipher). The signature deobfuscation
    // path (MoriCipherRuntime → NewPipeExtractor) is fragile because YouTube
    // periodically changes the player JS in ways that break the regex-based
    // function discovery. Clients that don't use a signature timestamp
    // (e.g. ANDROID_VR) get direct URLs from YouTube and bypass that entire
    // failure mode.
    LaunchedEffect(videoId) {
        streamUrl = null
        isVideoReady = false
        hasPlaybackFailed = false

        val resolved =
            withContext(Dispatchers.IO) {
                resolveVideoStreamUrl(videoId)
            }

        if (resolved.isNullOrBlank()) {
            hasPlaybackFailed = true
        } else {
            streamUrl = resolved
        }
    }

    // ── Load the resolved URL into the ExoPlayer ──
    LaunchedEffect(streamUrl, exoPlayer) {
        val url = streamUrl ?: return@LaunchedEffect
        isVideoReady = false
        hasPlaybackFailed = false

        val lowercaseUrl = url.lowercase(Locale.ROOT)
        val mimeType =
            when {
                lowercaseUrl.contains("m3u8") -> MimeTypes.APPLICATION_M3U8
                lowercaseUrl.contains("mp4") || lowercaseUrl.contains("avc") -> MimeTypes.VIDEO_MP4
                lowercaseUrl.contains("webm") || lowercaseUrl.contains("vp9") -> MimeTypes.VIDEO_WEBM
                lowercaseUrl.contains("av01") || lowercaseUrl.contains("av1") -> MimeTypes.VIDEO_AV1
                else -> MimeTypes.VIDEO_MP4
            }

        val mediaItem =
            MediaItem
                .Builder()
                .setUri(url)
                .setMimeType(mimeType)
                .build()

        exoPlayer.stop()
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()

        // Seek to the main player's current position so the video starts
        // in sync with the audio that's already playing.
        val targetPosition = currentPosition()
        if (targetPosition > 0) {
            exoPlayer.seekTo(targetPosition)
        }
        exoPlayer.setVideoPlayback(shouldPlay)
    }

    // ── Play/pause follower ──
    LaunchedEffect(isPlaying) {
        if (hasPlaybackFailed) {
            exoPlayer.pause()
        } else {
            exoPlayer.setVideoPlayback(isPlaying)
        }
    }

    // ── Periodic position sync ──
    // If the video drifts away from the main audio player by more than the
    // tolerance, re-seek. This handles edge cases like the video stalling
    // while audio continues, or the user seeking the audio while the video
    // was buffering.
    LaunchedEffect(streamUrl, isPlaying, exoPlayer) {
        if (streamUrl == null) return@LaunchedEffect
        while (isActive) {
            delay(VideoSyncPollIntervalMs)
            if (!shouldPlay || hasPlaybackFailed) continue
            val mainPos = currentPosition()
            val videoPos = exoPlayer.currentPosition
            val drift = kotlin.math.abs(videoPos - mainPos)
            if (drift > VideoSyncDriftToleranceMs && mainPos > 0) {
                Timber
                    .tag(VideoPlaybackLogTag)
                    .d("Re-syncing video: drift=${drift}ms (main=$mainPos, video=$videoPos)")
                exoPlayer.seekTo(mainPos)
            }
        }
    }

    // ── Lifecycle observer — resume on ON_START/ON_RESUME ──
    DisposableEffect(exoPlayer, lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (
                    (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME) &&
                    !hasPlaybackFailed &&
                    exoPlayer.playerError == null &&
                    streamUrl != null
                ) {
                    exoPlayer.setVideoPlayback(shouldPlay)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // ── Player event listener ──
    DisposableEffect(exoPlayer, streamUrl) {
        val listener =
            object : Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    Timber.tag(VideoPlaybackLogTag).w(error, "Video playback failed for $videoId")
                    hasPlaybackFailed = true
                    isVideoReady = false
                }

                override fun onRenderedFirstFrame() {
                    isVideoReady = true
                    if (shouldPlay && !hasPlaybackFailed && exoPlayer.playerError == null) {
                        exoPlayer.setVideoPlayback(isPlaying = true)
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        // Loop back to the main player's position — the audio
                        // may have moved on to a new song or repeated.
                        val mainPos = currentPosition()
                        exoPlayer.seekTo(mainPos)
                        exoPlayer.setVideoPlayback(shouldPlay)
                    } else if (shouldPlay && !hasPlaybackFailed && exoPlayer.playerError == null) {
                        exoPlayer.setVideoPlayback(isPlaying = true)
                    }
                }
            }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    // ── Release the ExoPlayer when the composable leaves the tree ──
    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (isVideoReady) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "videoAlpha",
    )

    ContentFrame(
        player = exoPlayer,
        surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
        contentScale = resizeMode.toContentScale(),
        keepContentOnReset = false,
        shutter = {},
        modifier = modifier.alpha(alpha),
    )
}

/**
 * Pick the best video format from a [PlayerResponse].
 *
 * Preference order:
 *  1. Formats with a direct `url` (no signatureCipher). These never need
 *     deobfuscation, so they're immune to YouTube player-JS breakage that
 *     periodically breaks the Mori/NewPipe regex-based fallbacks. We pick
 *     the highest-resolution direct-URL format ≤ 720p.
 *  2. As a last resort, formats with `signatureCipher`/`cipher` — same 720p
 *     cap. The deobfuscation pipeline (MoriCipherRuntime → NewPipeExtractor)
 *     will be attempted by [NewPipeUtils.getStreamUrl]; if it fails, the
 *     caller falls through to the next client.
 *
 * Returns null if no video format is available (e.g. the video is audio-only
 * on YouTube Music, which happens for some ATV tracks).
 */
private fun pickVideoFormat(playerResponse: PlayerResponse): PlayerResponse.StreamingData.Format? {
    val streamingData = playerResponse.streamingData ?: return null

    val allVideoFormats =
        (streamingData.formats.orEmpty() + streamingData.adaptiveFormats.orEmpty())
            .asSequence()
            .filter {
                val h = it.height
                h != null && h > 0
            }
            .filter { (it.height ?: 0) <= 720 }
            .filter { it.url != null || it.signatureCipher != null || it.cipher != null }
            .toList()

    if (allVideoFormats.isEmpty()) return null

    // Sort priority:
    //   1. Direct URL (url != null) — bypasses the broken signature deobfuscation.
    //   2. Combined (muxed) format (audioQuality != null) — single stream, no DASH.
    //   3. Higher resolution first.
    val comparator =
        compareByDescending<PlayerResponse.StreamingData.Format> { it.url != null }
            .thenByDescending { it.audioQuality != null } // combined (muxed) preferred
            .thenByDescending { it.height ?: 0 }

    return allVideoFormats.sortedWith(comparator).firstOrNull()
}

/**
 * Resolve a playable video stream URL for [videoId] by trying multiple YouTube
 * clients in order. Prefers clients that return direct stream URLs (no
 * signatureCipher) so we sidestep the fragile deobfuscation path entirely.
 *
 * Returns null if no client yields a usable stream URL — in that case the
 * caller should fall back to showing album artwork instead of a black
 * video surface.
 */
private suspend fun resolveVideoStreamUrl(videoId: String): String? {
    // ANDROID_VR doesn't use a signature timestamp, so YouTube returns direct
    // URLs for video formats (no signatureCipher). This is the most reliable
    // path and bypasses the Mori/NewPipe deobfuscation entirely.
    // WEB_REMIX is the fallback (matches the audio pipeline); it sometimes
    // returns direct URLs for combined formats and only falls back to
    // signatureCipher for adaptive video-only formats.
    val clients = listOf(ANDROID_VR_1_65_10 to null, WEB_REMIX to "sts")

    for ((client, stsMode) in clients) {
        val result =
            runCatching {
                val sts =
                    if (stsMode == null) {
                        null
                    } else {
                        NewPipeUtils.getSignatureTimestamp(videoId).getOrNull()
                    }
                val playerResponse =
                    YouTube
                        .player(
                            videoId = videoId,
                            client = client,
                            signatureTimestamp = sts,
                        ).getOrThrow()
                val format = pickVideoFormat(playerResponse) ?: return@runCatching null
                NewPipeUtils
                    .getStreamUrl(
                        format = format,
                        videoId = videoId,
                        client = client,
                    ).getOrNull()
            }

        val url = result.getOrNull()
        if (!url.isNullOrBlank()) {
            Timber
                .tag(VideoPlaybackLogTag)
                .i("Resolved video stream for $videoId via ${client.clientName}")
            return url
        }

        // Log the failure for diagnostics but continue to the next client.
        result.exceptionOrNull()?.let { error ->
            Timber
                .tag(VideoPlaybackLogTag)
                .w(error, "Video stream resolution failed for $videoId via ${client.clientName}")
        }
    }

    Timber.tag(VideoPlaybackLogTag).w("All video stream clients exhausted for $videoId")
    return null
}

private fun Int.toContentScale(): ContentScale =
    when (this) {
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> ContentScale.Crop
        AspectRatioFrameLayout.RESIZE_MODE_FILL -> ContentScale.FillBounds
        AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH,
        AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT,
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        -> ContentScale.Fit
        else -> ContentScale.Fit
    }

private fun ExoPlayer.setVideoPlayback(isPlaying: Boolean) {
    if (isPlaying) {
        if (playbackState == Player.STATE_IDLE && mediaItemCount > 0) prepare()
        if (playbackState == Player.STATE_ENDED) seekTo(0)
        play()
    } else {
        pause()
    }
}

private const val VideoPlaybackLogTag = "VideoArtworkPlayback"
private const val VideoPlaybackUserAgent =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Mobile Safari/537.36"
