/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package moe.rukamori.archivetune.ui.player

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
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
import moe.rukamori.archivetune.innertube.NewPipeUtils
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_65_10
import moe.rukamori.archivetune.innertube.models.YouTubeClient.Companion.WEB_REMIX
import moe.rukamori.archivetune.innertube.models.response.PlayerResponse
import moe.rukamori.archivetune.utils.StreamClientUtils
import okhttp3.OkHttpClient
import timber.log.Timber
import java.util.Locale

/**
 * Maximum allowed drift between the main audio player's position and the
 * video surface's position before we force a re-seek.
 */
private const val VideoSyncDriftToleranceMs = 400L

/**
 * Drift threshold above which we assume the user dragged the seekbar.
 * Triggers the pause-load-resume protocol (see [rememberVideoArtworkState]).
 */
private const val UserSeekDriftThresholdMs = 1500L

/**
 * How often to poll the main player's position and re-sync the video.
 */
private const val VideoSyncPollIntervalMs = 250L

/**
 * Maximum time the video ExoPlayer is allowed to stay in STATE_BUFFERING
 * before we force a re-prepare to break out of a stuck state.
 */
private const val VideoStuckBufferingTimeoutMs = 8000L

/**
 * Hard cap on the resolution we will ever attempt to play.
 */
private const val MaxVideoHeightCap = 2160

/**
 * Resolved information about a video stream — the playable URL plus the
 * menu of formats YouTube offered, so the user can pick a different
 * quality after playback has started.
 */
data class VideoStreamInfo(
    val streamUrl: String,
    val availableHeights: List<Int>,
    val captionTracks: List<PlayerResponse.CaptionTrack>,
)

/**
 * State holder for the video artwork player.
 *
 * Holds the [ExoPlayer] instance and all mutable playback state. Created
 * once per [videoId] via [rememberVideoArtworkState] and **shared between
 * the inline and fullscreen surfaces** — so toggling fullscreen does NOT
 * recreate the ExoPlayer or re-resolve the stream URL. The video continues
 * playing seamlessly as the surface moves between the inline slot and the
 * fullscreen Dialog.
 *
 * This is the key architectural change from the previous design where two
 * separate ExoPlayer instances were created (one inline, one fullscreen),
 * causing the video to reload and pause on every fullscreen toggle.
 */
@Stable
class VideoArtworkState internal constructor(
    val exoPlayer: ExoPlayer,
) {
    var streamUrl: String? by mutableStateOf(null)
        internal set
    var isVideoReady: Boolean by mutableStateOf(false)
        internal set
    var hasPlaybackFailed: Boolean by mutableStateOf(false)
        internal set
    var isChangingQuality: Boolean by mutableStateOf(false)
        internal set
    var wasPlayingBeforeQualityChange: Boolean by mutableStateOf(false)
        internal set
    var isResyncing: Boolean by mutableStateOf(false)
        internal set
    var wasPlayingBeforeResync: Boolean by mutableStateOf(false)
        internal set
    var isResolvingUrl: Boolean by mutableStateOf(true)
        internal set
    var bufferingStartedAtMs: Long by mutableStateOf(0L)
        internal set
}

/**
 * Create and remember a [VideoArtworkState] for the given [videoId].
 *
 * This composable owns the ExoPlayer lifecycle: it creates the player,
 * resolves the stream URL, sets up event listeners, runs the periodic
 * position-sync poller, and releases the player when the composable leaves
 * the tree (or when [videoId] changes).
 *
 * The returned [VideoArtworkState] is stable across recompositions and
 * across fullscreen toggles — the ExoPlayer is NOT recreated when the
 * parent switches between inline and fullscreen surfaces. Only the
 * [VideoArtworkSurface] (the view layer) moves; the player underneath
 * keeps running.
 *
 * Seekbar pause-load-resume protocol: when the periodic poller detects
 * drift > [UserSeekDriftThresholdMs] and the main player is playing, it
 * pauses the main audio player, seeks the video, waits for the first frame
 * to render, then resumes both together.
 *
 * Stuck-buffering recovery: if the ExoPlayer stays in STATE_BUFFERING for
 * longer than [VideoStuckBufferingTimeoutMs], the poller forces a
 * re-prepare to break out of the stall.
 */
@Composable
fun rememberVideoArtworkState(
    videoId: String,
    isPlaying: Boolean,
    positionProvider: () -> Long,
    preferredHeight: Int?,
    onStreamResolved: (VideoStreamInfo?) -> Unit,
    onPlaybackFailed: () -> Unit,
    onLoadingStateChange: (Boolean) -> Unit,
    onRequestPauseMain: () -> Unit,
    onRequestResumeMain: () -> Unit,
): VideoArtworkState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val shouldPlay by rememberUpdatedState(isPlaying)
    val currentPosition by rememberUpdatedState(positionProvider)
    val updatedPreferredHeight by rememberUpdatedState(preferredHeight)
    val updatedOnStreamResolved by rememberUpdatedState(onStreamResolved)
    val updatedOnPlaybackFailed by rememberUpdatedState(onPlaybackFailed)
    val updatedOnLoadingStateChange by rememberUpdatedState(onLoadingStateChange)
    val updatedOnRequestPauseMain by rememberUpdatedState(onRequestPauseMain)
    val updatedOnRequestResumeMain by rememberUpdatedState(onRequestResumeMain)

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

    val state =
        remember(videoId, mediaSourceFactory, renderersFactory, trackSelector) {
            val exoPlayer =
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
            VideoArtworkState(exoPlayer)
        }

    val exoPlayer = state.exoPlayer

    // Propagate loading state to the parent so it can render a spinner.
    LaunchedEffect(
        state.isVideoReady,
        state.isChangingQuality,
        state.isResyncing,
        state.streamUrl,
        state.hasPlaybackFailed,
        state.isResolvingUrl,
    ) {
        val loading =
            !state.hasPlaybackFailed &&
                (
                    state.isResolvingUrl ||
                        state.isResyncing ||
                        (state.streamUrl != null && !state.isVideoReady)
                )
        updatedOnLoadingStateChange(loading)
    }

    // ── Resolve the video stream URL off the main thread ──
    LaunchedEffect(videoId) {
        state.streamUrl = null
        state.isVideoReady = false
        state.hasPlaybackFailed = false
        state.isResolvingUrl = true
        state.isResyncing = false
        state.wasPlayingBeforeResync = false
        state.bufferingStartedAtMs = 0L

        val resolved =
            withContext(Dispatchers.IO) {
                resolveVideoStreamUrl(videoId, updatedPreferredHeight)
            }

        state.isResolvingUrl = false

        if (resolved == null) {
            state.hasPlaybackFailed = true
            updatedOnPlaybackFailed()
            updatedOnStreamResolved(null)
        } else {
            state.streamUrl = resolved.streamUrl
            updatedOnStreamResolved(resolved)
        }
    }

    // ── Re-resolve when the user changes preferred quality ──
    LaunchedEffect(preferredHeight) {
        if (state.streamUrl == null) return@LaunchedEffect
        state.wasPlayingBeforeQualityChange = shouldPlay
        state.isChangingQuality = true
        state.isVideoReady = false
        state.isResolvingUrl = true
        exoPlayer.pause()
        updatedOnRequestPauseMain()
        state.streamUrl = null
        val resolved =
            withContext(Dispatchers.IO) {
                resolveVideoStreamUrl(videoId, updatedPreferredHeight)
            }
        state.isResolvingUrl = false
        if (resolved != null) {
            state.streamUrl = resolved.streamUrl
            updatedOnStreamResolved(resolved)
        } else {
            state.isChangingQuality = false
            val fallback =
                withContext(Dispatchers.IO) {
                    resolveVideoStreamUrl(videoId, updatedPreferredHeight)
                }
            if (fallback != null) {
                state.streamUrl = fallback.streamUrl
                updatedOnStreamResolved(fallback)
            } else {
                state.hasPlaybackFailed = true
                updatedOnPlaybackFailed()
            }
            if (state.wasPlayingBeforeQualityChange) {
                updatedOnRequestResumeMain()
            }
        }
    }

    // ── Load the resolved URL into the ExoPlayer ──
    LaunchedEffect(state.streamUrl, exoPlayer) {
        val url = state.streamUrl ?: return@LaunchedEffect
        state.isVideoReady = false
        state.hasPlaybackFailed = false

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

        val targetPosition = currentPosition()
        if (targetPosition > 0) {
            exoPlayer.seekTo(targetPosition)
        }
        exoPlayer.playWhenReady = shouldPlay
    }

    // ── Play/pause follower ──
    LaunchedEffect(isPlaying) {
        if (state.hasPlaybackFailed) {
            exoPlayer.pause()
        } else {
            exoPlayer.setVideoPlayback(isPlaying)
        }
    }

    // ── Periodic position sync + stuck-buffering recovery ──
    LaunchedEffect(state.streamUrl, exoPlayer) {
        if (state.streamUrl == null) return@LaunchedEffect
        while (isActive) {
            delay(VideoSyncPollIntervalMs)
            if (state.hasPlaybackFailed) continue

            // Stuck-buffering recovery
            if (state.bufferingStartedAtMs > 0L) {
                val bufferingForMs = SystemClock.elapsedRealtime() - state.bufferingStartedAtMs
                if (bufferingForMs > VideoStuckBufferingTimeoutMs) {
                    Timber
                        .tag(VideoPlaybackLogTag)
                        .w("Video stuck in BUFFERING for ${bufferingForMs}ms — forcing re-prepare")
                    state.bufferingStartedAtMs = SystemClock.elapsedRealtime()
                    exoPlayer.prepare()
                }
            }

            // Drift / seek detection
            if (state.isChangingQuality) continue
            if (state.isResyncing) continue
            if (!state.isVideoReady) continue

            val mainPos = currentPosition()
            if (mainPos <= 0) continue
            val videoPos = exoPlayer.currentPosition
            val drift = kotlin.math.abs(videoPos - mainPos)

            if (drift > VideoSyncDriftToleranceMs) {
                if (drift > UserSeekDriftThresholdMs && shouldPlay && !state.hasPlaybackFailed) {
                    Timber
                        .tag(VideoPlaybackLogTag)
                        .d("User seek detected: drift=${drift}ms — pausing main, rebuffering video")
                    state.wasPlayingBeforeResync = shouldPlay
                    state.isResyncing = true
                    state.isVideoReady = false
                    updatedOnRequestPauseMain()
                    exoPlayer.pause()
                    exoPlayer.seekTo(mainPos)
                    state.bufferingStartedAtMs = SystemClock.elapsedRealtime()
                } else {
                    Timber
                        .tag(VideoPlaybackLogTag)
                        .d("Re-syncing video: drift=${drift}ms (main=$mainPos, video=$videoPos)")
                    exoPlayer.seekTo(mainPos)
                    exoPlayer.setVideoPlayback(shouldPlay)
                }
            }
        }
    }

    // ── Lifecycle observer ──
    DisposableEffect(exoPlayer, lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (
                    (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME) &&
                    !state.hasPlaybackFailed &&
                    exoPlayer.playerError == null &&
                    state.streamUrl != null
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
    DisposableEffect(exoPlayer, state.streamUrl) {
        val listener =
            object : Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    Timber.tag(VideoPlaybackLogTag).w(error, "Video playback failed for $videoId")
                    state.hasPlaybackFailed = true
                    state.isVideoReady = false
                    state.isChangingQuality = false
                    state.isResyncing = false
                    state.bufferingStartedAtMs = 0L
                    updatedOnPlaybackFailed()
                }

                override fun onRenderedFirstFrame() {
                    state.isVideoReady = true
                    val wasChangingQuality = state.isChangingQuality
                    state.isChangingQuality = false
                    val wasResync = state.isResyncing
                    state.isResyncing = false
                    val wasPlayingBeforeResyncLocal = state.wasPlayingBeforeResync
                    state.wasPlayingBeforeResync = false
                    state.bufferingStartedAtMs = 0L

                    if (!wasResync) {
                        val mainPos = currentPosition()
                        if (mainPos > 0) {
                            val videoPos = exoPlayer.currentPosition
                            val drift = kotlin.math.abs(videoPos - mainPos)
                            if (drift > VideoSyncDriftToleranceMs) {
                                exoPlayer.seekTo(mainPos)
                            }
                        }
                    }

                    val effectiveShouldPlay = shouldPlay || (wasResync && wasPlayingBeforeResyncLocal)
                    if (effectiveShouldPlay && !state.hasPlaybackFailed && exoPlayer.playerError == null) {
                        exoPlayer.playWhenReady = true
                        exoPlayer.play()
                    }

                    if (wasChangingQuality && state.wasPlayingBeforeQualityChange) {
                        updatedOnRequestResumeMain()
                    }

                    if (wasResync && wasPlayingBeforeResyncLocal) {
                        updatedOnRequestResumeMain()
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_BUFFERING -> {
                            if (state.bufferingStartedAtMs == 0L) {
                                state.bufferingStartedAtMs = SystemClock.elapsedRealtime()
                            }
                        }
                        Player.STATE_READY -> {
                            state.bufferingStartedAtMs = 0L
                            val effectiveShouldPlay =
                                shouldPlay || (state.isResyncing && state.wasPlayingBeforeResync)
                            if (effectiveShouldPlay && !state.hasPlaybackFailed &&
                                exoPlayer.playerError == null
                            ) {
                                exoPlayer.playWhenReady = true
                                exoPlayer.play()
                            }
                        }
                        Player.STATE_ENDED -> {
                            state.bufferingStartedAtMs = 0L
                            val mainPos = currentPosition()
                            exoPlayer.seekTo(mainPos)
                            exoPlayer.setVideoPlayback(shouldPlay)
                        }
                        Player.STATE_IDLE -> {
                            state.bufferingStartedAtMs = 0L
                        }
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

    return state
}

/**
 * Renders the video surface for the given [state].
 *
 * This is a pure view composable — it reads [VideoArtworkState.isVideoReady]
 * for the alpha animation and renders a [ContentFrame] attached to
 * [VideoArtworkState.exoPlayer]. It does NOT create or manage the ExoPlayer;
 * that's the responsibility of [rememberVideoArtworkState].
 *
 * Because the ExoPlayer is external, this composable can be freely moved
 * between different parents (e.g. inline slot ↔ fullscreen Dialog) without
 * causing the video to reload. The ExoPlayer's surface is detached from
 * the old view and attached to the new one — a fast operation that does
 * NOT interrupt playback.
 */
@Composable
fun VideoArtworkSurface(
    state: VideoArtworkState,
    modifier: Modifier = Modifier,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
) {
    val alpha by animateFloatAsState(
        targetValue = if (state.isVideoReady) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "videoAlpha",
    )

    Box(modifier = modifier) {
        ContentFrame(
            player = state.exoPlayer,
            surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
            contentScale = resizeMode.toContentScale(),
            keepContentOnReset = false,
            shutter = {},
            modifier = Modifier.fillMaxSize().alpha(alpha),
        )
    }
}

/**
 * Pick the best video format from a [PlayerResponse], honoring a preferred
 * height.
 */
private fun pickVideoFormat(
    playerResponse: PlayerResponse,
    preferredHeight: Int?,
): PlayerResponse.StreamingData.Format? {
    val streamingData = playerResponse.streamingData ?: return null

    val allVideoFormats =
        (streamingData.formats.orEmpty() + streamingData.adaptiveFormats.orEmpty())
            .asSequence()
            .filter {
                val h = it.height
                h != null && h > 0
            }
            .filter { (it.height ?: 0) <= MaxVideoHeightCap }
            .filter { it.url != null || it.signatureCipher != null || it.cipher != null }
            .toList()

    if (allVideoFormats.isEmpty()) return null

    val heightFiltered =
        if (preferredHeight != null) {
            val atOrBelow = allVideoFormats.filter { (it.height ?: 0) <= preferredHeight }
            if (atOrBelow.isNotEmpty()) atOrBelow else allVideoFormats.sortedBy { it.height ?: 0 }
        } else {
            allVideoFormats
        }

    val comparator =
        compareByDescending<PlayerResponse.StreamingData.Format> { it.height ?: 0 }
            .thenByDescending { it.url != null }
            .thenByDescending { it.audioQuality != null }

    return heightFiltered.sortedWith(comparator).firstOrNull()
}

/**
 * Resolve a playable video stream URL for [videoId] by trying multiple YouTube
 * clients in order.
 */
private suspend fun resolveVideoStreamUrl(
    videoId: String,
    preferredHeight: Int?,
): VideoStreamInfo? {
    val clients = listOf(ANDROID_VR_1_65_10 to null, WEB_REMIX to "sts")

    var lastAvailableHeights: List<Int> = emptyList()
    var lastCaptionTracks: List<PlayerResponse.CaptionTrack> = emptyList()

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

                lastAvailableHeights =
                    (playerResponse.streamingData?.formats.orEmpty() +
                        playerResponse.streamingData?.adaptiveFormats.orEmpty())
                        .mapNotNull { it.height?.takeIf { h -> h > 0 } }
                        .distinct()
                        .sorted()

                lastCaptionTracks =
                    playerResponse.captions
                        ?.playerCaptionsTracklistRenderer
                        ?.captionTracks
                        .orEmpty()

                val format = pickVideoFormat(playerResponse, preferredHeight) ?: return@runCatching null
                val url =
                    NewPipeUtils
                        .getStreamUrl(
                            format = format,
                            videoId = videoId,
                            client = client,
                        ).getOrNull()
                if (url.isNullOrBlank()) null else url
            }

        val url = result.getOrNull()
        if (!url.isNullOrBlank()) {
            Timber
                .tag(VideoPlaybackLogTag)
                .i("Resolved video stream for $videoId via ${client.clientName}")
            return VideoStreamInfo(
                streamUrl = url,
                availableHeights = lastAvailableHeights,
                captionTracks = lastCaptionTracks,
            )
        }

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

internal const val VideoPlaybackLogTag = "VideoArtworkPlayback"
private const val VideoPlaybackUserAgent =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Mobile Safari/537.36"
