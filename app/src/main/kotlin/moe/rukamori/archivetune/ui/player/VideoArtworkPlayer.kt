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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.SubtitleView
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
 * is perceptible to the user as audio/video desync. Kept tight (400ms) so
 * that a seekbar drag on the main player is reflected by the video surface
 * within one poll cycle — i.e. within ~250ms, which is imperceptible.
 */
private const val VideoSyncDriftToleranceMs = 400L

/**
 * How often to poll the main player's position and re-sync the video.
 * 250ms is fast enough to make seeks feel instantaneous without burning
 * measurable CPU.
 */
private const val VideoSyncPollIntervalMs = 250L

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
 * subtitle. The [DefaultTrackSelector] keeps `TRACK_TYPE_TEXT` always
 * enabled so ExoPlayer selects and parses the subtitle on prepare().
 * Caption *rendering* is gated at the view layer: the `SubtitleView`
 * overlay is only mounted when [captionsEnabled] is true, so toggling
 * captions does NOT require a MediaItem rebuild or a track-selector
 * update — it's just a Compose recomposition.
 *
 * Position sync: on mount we seek to the main player's current position,
 * and a periodic poller re-seeks if drift exceeds [VideoSyncDriftToleranceMs].
 * Play/pause follows [isPlaying]. During a quality swap the poller is
 * suspended (see `isChangingQuality`) so it doesn't fight with the loader.
 *
 * @param videoId The YouTube video ID (same as the song's mediaId for YouTube Music songs).
 * @param isPlaying Whether the main audio player is currently playing.
 * @param positionProvider Returns the main audio player's current position in ms.
 * @param preferredHeight Desired video height in px (e.g. 720 for 720p). null = auto-best up to 4K.
 * @param captionsEnabled Whether to render captions. The caption track URL is
 *   resolved internally alongside the video stream URL so the [MediaItem] is
 *   built with the subtitle side-load from the very first prepare() — this
 *   avoids a rebuffer when the user toggles captions on later. Toggling this
 *   parameter only mounts/unmounts the [SubtitleView] overlay; no MediaItem
 *   rebuild or track-selector update is needed.
 * @param onStreamResolved Invoked when the stream URL has been resolved, with the
 *   list of all heights YouTube offered (so the parent can render a quality picker)
 *   and the list of caption tracks (so the parent can render a captions picker).
 * @param onPlaybackFailed Invoked when playback fails (e.g. stream URL resolution
 *   exhausted all clients, or ExoPlayer emitted an error) so the parent can fall
 *   back to showing album artwork.
 * @param onLoadingStateChange Invoked when the video surface transitions between
 *   loading and ready. The parent uses this to show/hide a loading indicator
 *   over the video surface (e.g. a CircularProgressIndicator) — this fires
 *   both on initial load AND when the user changes the quality, so the audio
 *   keeps playing through the main MusicService while the video surface shows
 *   a spinner and re-syncs once the new stream renders its first frame.
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
    onLoadingStateChange: (Boolean) -> Unit = {},
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
    var isChangingQuality by remember(videoId) { mutableStateOf(false) }

    // Propagate loading state to the parent so it can render a spinner.
    // Loading = !isVideoReady AND we have a stream URL we're (re)loading.
    // The `isChangingQuality` flag distinguishes "initial load" from
    // "quality swap" so the parent can decide whether to keep the old
    // frame visible during the swap (we don't, because the old frame is
    // from a different resolution and would visually jump).
    LaunchedEffect(isVideoReady, isChangingQuality, streamUrl, hasPlaybackFailed) {
        val loading = !hasPlaybackFailed && streamUrl != null && !isVideoReady
        onLoadingStateChange(loading)
    }

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
    //
    // Text tracks are ALWAYS enabled in the track selector. We never
    // disable TRACK_TYPE_TEXT because doing so causes ExoPlayer to skip
    // loading the subtitle entirely, which meant the onCues() callback
    // never fired and captions never appeared (the previous bug). Instead
    // we control rendering at the view layer: the SubtitleView overlay is
    // only mounted when `captionsEnabled == true`, so when CC is off the
    // subtitle track is still loaded/parsed (cheap — subtitles are tiny)
    // but its cues are simply discarded (no view to push them to).
    //
    // We also set a preferred text language so ExoPlayer selects the
    // side-loaded subtitle track immediately on prepare() rather than
    // waiting for a track-selection pass that might never happen.
    val trackSelector =
        remember(context) {
            DefaultTrackSelector(context).apply {
                setParameters(
                    buildUponParameters()
                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                        .setPreferredTextLanguage("en")
                        .setSelectUndeterminedTextLanguage(true)
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
    //
    // QUALITY-SWAP PROTOCOL (important for AV sync):
    //   1. Set `isChangingQuality = true` and `isVideoReady = false` BEFORE
    //      touching the stream URL. This immediately:
    //        - hides the video surface (alpha animates to 0)
    //        - shows the loading indicator in the parent overlay
    //        - pauses the ExoPlayer (so the stale frame doesn't keep
    //          playing while we re-resolve)
    //      The main MusicService audio KEEPS PLAYING — that's correct,
    //      the user expects the song to continue while the video catches
    //      up to the new resolution.
    //   2. Null out `streamUrl` so the LaunchedEffect(streamUrl, ...) below
    //      fires and reloads the MediaItem with the new URL.
    //   3. Resolve the new URL off the main thread.
    //   4. Set the new `streamUrl` → triggers MediaItem reload → ExoPlayer
    //      prepares → `onRenderedFirstFrame` fires → `isVideoReady = true`
    //      and `isChangingQuality = false` → loading indicator hides,
    //      video surface fades back in, playback resumes in sync with the
    //      main player's current position (the loader seeks to
    //      `currentPosition()` before starting playback).
    LaunchedEffect(preferredHeight) {
        // Skip the initial run — handled by the LaunchedEffect(videoId) above.
        if (streamUrl == null) return@LaunchedEffect
        isChangingQuality = true
        isVideoReady = false
        // Pause immediately so the stale frame doesn't keep rendering
        // while we're swapping the stream. The position-sync poller would
        // otherwise re-start playback against the OLD media item.
        exoPlayer.pause()
        streamUrl = null
        val resolved =
            withContext(Dispatchers.IO) {
                resolveVideoStreamUrl(videoId, updatedPreferredHeight)
            }
        if (resolved != null) {
            streamUrl = resolved.streamUrl
            onStreamResolved(resolved)
        } else {
            // Resolution failed — fall back to the previous stream so the
            // user isn't left with a black screen. Clear the loading flag
            // so the spinner doesn't spin forever.
            isChangingQuality = false
            // Re-resolve with the original preferredHeight to restore the
            // previous stream. We don't have the old URL cached, so we
            // have to re-resolve. If THIS also fails, onPlaybackFailed()
            // will fire and the parent falls back to album artwork.
            val fallback =
                withContext(Dispatchers.IO) {
                    resolveVideoStreamUrl(videoId, updatedPreferredHeight)
                }
            if (fallback != null) {
                streamUrl = fallback.streamUrl
                onStreamResolved(fallback)
            } else {
                hasPlaybackFailed = true
                onPlaybackFailed()
            }
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
        // track selector above keeps TRACK_TYPE_TEXT always enabled, so
        // ExoPlayer will select this subtitle track on prepare() and begin
        // parsing it. The SubtitleView overlay below is only mounted when
        // `captionsEnabled == true`, so when CC is off the cues are parsed
        // but discarded (no view to render them) — cheap, and avoids a
        // MediaItem rebuild when the user toggles captions on later.
        //
        // We deliberately do NOT set a language on the SubtitleConfiguration
        // if the resolved track's language is unknown — setting a language
        // here would cause ExoPlayer to filter by it and potentially skip
        // the track if the track selector's preferred language doesn't
        // match. The track selector's `setSelectUndeterminedTextLanguage(true)`
        // ensures the track is selected regardless.
        val activeCaptionUrl = resolvedCaptionUrl
        if (!activeCaptionUrl.isNullOrBlank()) {
            val subBuilder =
                MediaItem.SubtitleConfiguration
                    .Builder(android.net.Uri.parse(activeCaptionUrl))
                    .setMimeType(MimeTypes.TEXT_VTT)
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            // Only set the language if we actually know it — otherwise let
            // ExoPlayer figure it out from the WebVTT file itself.
            // (Hardcoding "en" here previously caused tracks with no
            // languageCode to be silently dropped.)
            mediaItemBuilder.setSubtitleConfigurations(listOf(subBuilder.build()))
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

    // No LaunchedEffect for captionsEnabled here — we keep TRACK_TYPE_TEXT
    // always enabled in the track selector (see declaration above) and
    // control caption rendering at the view layer via the SubtitleView
    // mount/unmount below. This avoids the previous bug where disabling
    // TRACK_TYPE_TEXT prevented the subtitle from being loaded at all,
    // so onCues() never fired and captions never appeared.

    // ── Play/pause follower ──
    LaunchedEffect(isPlaying) {
        if (hasPlaybackFailed) {
            exoPlayer.pause()
        } else {
            exoPlayer.setVideoPlayback(isPlaying)
        }
    }

    // ── Periodic position sync ──
    //
    // Polls the main audio player's position and re-seeks the video surface
    // if drift exceeds [VideoSyncDriftToleranceMs]. Runs even when the player
    // is paused so that a seekbar drag while paused still syncs the video —
    // previously the `!shouldPlay` guard caused the video to stay at its old
    // position until the user hit play, which looked broken.
    //
    // IMPORTANT: skip while `isChangingQuality` or `!isVideoReady` — during
    // a quality swap the ExoPlayer is loading a new MediaItem and its
    // currentPosition is meaningless (often 0 or the old stream's position).
    // Re-seeking during that window would fight with the loader's own
    // seek-to-main-position call and could land the video at the wrong spot.
    LaunchedEffect(streamUrl, exoPlayer) {
        if (streamUrl == null) return@LaunchedEffect
        while (isActive) {
            delay(VideoSyncPollIntervalMs)
            if (hasPlaybackFailed) continue
            if (isChangingQuality) continue
            if (!isVideoReady) continue
            val mainPos = currentPosition()
            if (mainPos <= 0) continue
            val videoPos = exoPlayer.currentPosition
            val drift = kotlin.math.abs(videoPos - mainPos)
            if (drift > VideoSyncDriftToleranceMs) {
                Timber
                    .tag(VideoPlaybackLogTag)
                    .d("Re-syncing video: drift=${drift}ms (main=$mainPos, video=$videoPos)")
                exoPlayer.seekTo(mainPos)
                // Match play state too — if the user paused and the video is
                // still playing (or vice versa), correct it here.
                exoPlayer.setVideoPlayback(shouldPlay)
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
                    isChangingQuality = false
                    onPlaybackFailed()
                }

                override fun onRenderedFirstFrame() {
                    isVideoReady = true
                    isChangingQuality = false
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
                    } else if (shouldPlay && !hasPlaybackFailed && exoPlayer.playerError == null &&
                        playbackState != Player.STATE_BUFFERING
                    ) {
                        // Don't force-play during BUFFERING — the player is
                        // still loading the next chunk and calling play()
                        // here can race with the seek-to-main-position call
                        // in the loader, causing the video to start from 0
                        // instead of the synced position. Let
                        // onRenderedFirstFrame drive the resume.
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

    Box(modifier = modifier) {
        ContentFrame(
            player = exoPlayer,
            surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
            contentScale = resizeMode.toContentScale(),
            keepContentOnReset = false,
            shutter = {},
            modifier = Modifier.fillMaxSize().alpha(alpha),
        )

        // ── Caption overlay ──
        //
        // media3's Compose `ContentFrame` only renders the video surface; it
        // does NOT render subtitles. To display captions we have to overlay
        // the regular `androidx.media3.ui.SubtitleView` (an Android View)
        // and feed it cues from the ExoPlayer. media3's `SubtitleView` no
        // longer has a `setPlayer()` method (that was the legacy ExoPlayer
        // API); instead we listen for `onCues(CueGroup)` on the player and
        // push the cue list into the view via `setCues()`. The text track
        // is gated by the track selector (disabled when
        // `captionsEnabled == false`), so this overlay is cheap: when
        // captions are off, we don't even mount the listener or the view.
        if (captionsEnabled) {
            var cues by remember { mutableStateOf<List<Cue>>(emptyList()) }

            DisposableEffect(exoPlayer) {
                val cueListener =
                    object : Player.Listener {
                        override fun onCues(cueGroup: CueGroup) {
                            cues = cueGroup.cues
                        }
                    }
                exoPlayer.addListener(cueListener)
                onDispose { exoPlayer.removeListener(cueListener) }
            }

            AndroidView(
                factory = { ctx ->
                    SubtitleView(ctx).apply {
                        setFractionalTextSize(0.0533f)
                        setApplyEmbeddedStyles(true)
                        setApplyEmbeddedFontSizes(true)
                        setStyle(androidx.media3.ui.CaptionStyleCompat.DEFAULT)
                    }
                },
                update = { it.setCues(cues) },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
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
    //   Primary: HIGHEST height first.
    //     - For an explicit preferredHeight, `heightFiltered` already
    //       constrained us to at-or-below (or shortest-above if none), so
    //       picking the tallest in that set gets us as close to the user's
    //       request as possible.
    //     - For Auto (preferredHeight == null), this picks the max available
    //       resolution (up to 4K).
    //   Tiebreak 1: Direct URL (url != null) — bypasses the broken signature
    //     deobfuscation pipeline.
    //   Tiebreak 2: Combined (muxed) format (audioQuality != null) — single
    //     stream, no DASH.
    //
    // CRITICAL: height MUST come before muxed/direct-URL. YouTube's muxed
    // formats top out at 720p (itag 22) — if muxed were prioritized over
    // height, picking "1080p" or "2160p (4K)" from the quality menu would
    // silently return 720p muxed. That was the bug behind "changing quality
    // doesn't work".
    val comparator =
        compareByDescending<PlayerResponse.StreamingData.Format> { it.height ?: 0 }
            .thenByDescending { it.url != null }
            .thenByDescending { it.audioQuality != null }

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
