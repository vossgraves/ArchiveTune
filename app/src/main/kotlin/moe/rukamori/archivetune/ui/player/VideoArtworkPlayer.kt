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
 * video surface's position before we force a re-seek. Anything above this
 * is perceptible to the user as audio/video desync.
 */
private const val VideoSyncDriftToleranceMs = 1_500L

/**
 * How often to poll the main player's position and re-sync the video.
 */
private const val VideoSyncPollIntervalMs = 1_000L

/**
 * Hard cap on the resolution we will ever attempt to play. YouTube's video
 * catalog for music videos goes up to 2160p (4K). Going beyond that would
 * just waste bandwidth on devices that can't display it.
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
 * player endpoint for the song's videoId, then pick a video format honoring
 * [preferredHeight] (null = auto, picks the highest available up to 4K).
 * The URL is deobfuscated via [NewPipeUtils.getStreamUrl] (handles both
 * direct URLs and signatureCipher formats).
 *
 * Captions: the caption track URL is resolved internally alongside the
 * video stream URL and always side-loaded into the [MediaItem] as a WebVTT
 * subtitle. The [DefaultTrackSelector] enables/disables the text track type
 * based on [captionsEnabled] — when disabled, the subtitle is loaded but
 * not rendered; when enabled, it's rendered. This avoids a MediaItem rebuild
 * (and the associated rebuffer) when the user toggles captions.
 *
 * Position sync: on mount we seek to the main player's current position,
 * and a periodic poller re-seeks if drift exceeds [VideoSyncDriftToleranceMs].
 * Play/pause follows [isPlaying].
 *
 * @param videoId The YouTube video ID (same as the song's mediaId for YouTube Music songs).
 * @param isPlaying Whether the main audio player is currently playing.
 * @param positionProvider Returns the main audio player's current position in ms.
 * @param preferredHeight Desired video height in px (e.g. 720 for 720p). null = auto-best up to 4K.
 * @param captionsEnabled Whether to render captions. The caption track URL is
 *   resolved internally alongside the video stream URL so the [MediaItem] is
 *   built with the subtitle side-load from the very first prepare() — this
 *   avoids a rebuffer when the user toggles captions on later. Toggling this
 *   parameter only updates the [DefaultTrackSelector] to enable/disable the
 *   text track type; no MediaItem rebuild is needed.
 * @param onStreamResolved Invoked when the stream URL has been resolved, with the
 *   list of all heights YouTube offered (so the parent can render a quality picker)
 *   and the list of caption tracks (so the parent can render a captions picker).
 * @param onPlaybackFailed Invoked when playback fails (e.g. stream URL resolution
 *   exhausted all clients, or ExoPlayer emitted an error) so the parent can fall
 *   back to showing album artwork.
 * @param modifier Modifier for the surface.
 * @param resizeMode AspectRatioFrameLayout resize mode (default FIT to letterbox within the artwork slot).
 */
@Composable
fun VideoArtworkPlayer(
    videoId: String,
    isPlaying: Boolean,
    positionProvider: () -> Long,
    modifier: Modifier = Modifier,
    preferredHeight: Int? = null,
    captionsEnabled: Boolean = false,
    onStreamResolved: (VideoStreamInfo?) -> Unit = {},
    onPlaybackFailed: () -> Unit = {},
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
) {
    if (videoId.isBlank()) {
        onPlaybackFailed()
        return
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val shouldPlay by rememberUpdatedState(isPlaying)
    val currentPosition by rememberUpdatedState(positionProvider)
    val updatedPreferredHeight by rememberUpdatedState(preferredHeight)

    var streamUrl by remember(videoId) { mutableStateOf<String?>(null) }
    var resolvedCaptionUrl by remember(videoId) { mutableStateOf<String?>(null) }
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
    // Text tracks are enabled only when the user has toggled captions on
    // AND a caption URL has been resolved.
    val trackSelector =
        remember(context) {
            DefaultTrackSelector(context).apply {
                setParameters(
                    buildUponParameters()
                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !captionsEnabled)
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
        resolvedCaptionUrl = null
        isVideoReady = false
        hasPlaybackFailed = false

        val resolved =
            withContext(Dispatchers.IO) {
                resolveVideoStreamUrl(videoId, updatedPreferredHeight)
            }

        if (resolved == null) {
            hasPlaybackFailed = true
            onPlaybackFailed()
            onStreamResolved(null)
        } else {
            streamUrl = resolved.streamUrl
            // Pick the best caption track up front so the MediaItem can be
            // built with the subtitle side-load from the very first prepare()
            // call. Preference: non-ASR English > non-ASR any > English ASR > any.
            resolvedCaptionUrl =
                resolved.captionTracks
                    .firstOrNull { !it.isAutoGenerated && it.languageCode == "en" }
                    ?.webVttUrl()
                    ?: resolved.captionTracks.firstOrNull { !it.isAutoGenerated }?.webVttUrl()
                    ?: resolved.captionTracks.firstOrNull { it.languageCode == "en" }?.webVttUrl()
                    ?: resolved.captionTracks.firstOrNull()?.webVttUrl()
            onStreamResolved(resolved)
        }
    }

    // ── Re-resolve when the user changes preferred quality ──
    //
    // We don't want to re-resolve on every recomposition, only when the
    // user explicitly picks a different quality. The remember(videoId)
    // guard above handles initial resolution; this LaunchedEffect handles
    // subsequent user-driven changes.
    LaunchedEffect(preferredHeight) {
        // Skip the initial run — handled by the LaunchedEffect(videoId) above.
        if (streamUrl == null) return@LaunchedEffect
        val resolved =
            withContext(Dispatchers.IO) {
                resolveVideoStreamUrl(videoId, updatedPreferredHeight)
            }
        if (resolved != null) {
            streamUrl = resolved.streamUrl
            onStreamResolved(resolved)
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

        val mediaItemBuilder =
            MediaItem
                .Builder()
                .setUri(url)
                .setMimeType(mimeType)

        // Always side-load the caption track when we resolved one. The
        // track selector below controls whether the text track is actually
        // rendered (gated on `captionsEnabled`), so side-loading is free:
        // when captions are disabled the subtitle is just ignored.
        // This avoids a MediaItem rebuild (and the associated rebuffer)
        // when the user toggles captions on later.
        val activeCaptionUrl = resolvedCaptionUrl
        if (!activeCaptionUrl.isNullOrBlank()) {
            mediaItemBuilder.setSubtitleConfigurations(
                listOf(
                    MediaItem.SubtitleConfiguration
                        .Builder(android.net.Uri.parse(activeCaptionUrl))
                        .setMimeType(MimeTypes.TEXT_VTT)
                        .setLanguage("en")
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build(),
                ),
            )
        }

        exoPlayer.stop()
        exoPlayer.setMediaItem(mediaItemBuilder.build())
        exoPlayer.prepare()

        // Seek to the main player's current position so the video starts
        // in sync with the audio that's already playing.
        val targetPosition = currentPosition()
        if (targetPosition > 0) {
            exoPlayer.seekTo(targetPosition)
        }
        exoPlayer.setVideoPlayback(shouldPlay)
    }

    // ── Toggle text-track disabling when captions toggle changes ──
    LaunchedEffect(captionsEnabled) {
        trackSelector.setParameters(
            trackSelector
                .buildUponParameters()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !captionsEnabled)
                .build(),
        )
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
                    onPlaybackFailed()
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
 * Pick the best video format from a [PlayerResponse], honoring a preferred
 * height.
 *
 * Selection algorithm:
 *  - If [preferredHeight] is null → pick the highest-resolution format
 *    available, capped at [MaxVideoHeightCap] (2160p). This is the "Auto"
 *    mode and is what the user gets by default.
 *  - If [preferredHeight] is non-null → pick the format whose height is
 *    closest to but not greater than [preferredHeight]. If every available
 *    format is taller than the preferred height, pick the smallest one
 *    (downscale is better than refusing to play).
 *
 * Within the height constraint, we prefer (in order):
 *  1. Formats with a direct `url` (no signatureCipher) — bypasses the broken
 *     signature deobfuscation pipeline entirely.
 *  2. Combined (muxed) format (audioQuality != null) — single stream, no DASH.
 *  3. Higher resolution first.
 *
 * Returns null if no video format is available.
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
            // Prefer the tallest format <= preferredHeight.
            // If none, fall back to the shortest available (downscale).
            val atOrBelow = allVideoFormats.filter { (it.height ?: 0) <= preferredHeight }
            if (atOrBelow.isNotEmpty()) atOrBelow else allVideoFormats.sortedBy { it.height ?: 0 }
        } else {
            allVideoFormats
        }

    // Sort priority:
    //   1. Direct URL (url != null) — bypasses the broken signature deobfuscation.
    //   2. Combined (muxed) format (audioQuality != null) — single stream, no DASH.
    //   3. Higher resolution first.
    val comparator =
        compareByDescending<PlayerResponse.StreamingData.Format> { it.url != null }
            .thenByDescending { it.audioQuality != null }
            .thenByDescending { it.height ?: 0 }

    return heightFiltered.sortedWith(comparator).firstOrNull()
}

/**
 * Resolve a playable video stream URL for [videoId] by trying multiple YouTube
 * clients in order. Prefers clients that return direct stream URLs (no
 * signatureCipher) so we sidestep the fragile deobfuscation path entirely.
 *
 * Returns a [VideoStreamInfo] containing the resolved URL plus the full
 * menu of available heights (so the caller can render a quality picker)
 * and the list of caption tracks (so the caller can render a captions
 * picker). Returns null if no client yields a usable stream URL — in that
 * case the caller should fall back to showing album artwork.
 *
 * The [preferredHeight] parameter is honored by [pickVideoFormat]: null
 * means "pick the best available up to 4K"; a specific value picks the
 * closest match.
 */
private suspend fun resolveVideoStreamUrl(
    videoId: String,
    preferredHeight: Int?,
): VideoStreamInfo? {
    // ANDROID_VR doesn't use a signature timestamp, so YouTube returns direct
    // URLs for video formats (no signatureCipher). This is the most reliable
    // path and bypasses the Mori/NewPipe deobfuscation entirely.
    // WEB_REMIX is the fallback (matches the audio pipeline); it sometimes
    // returns direct URLs for combined formats and only falls back to
    // signatureCipher for adaptive video-only formats.
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

                // Capture the full menu of heights + caption tracks regardless
                // of whether our specific pick succeeds — the parent UI uses
                // these to render the quality/captions pickers.
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
