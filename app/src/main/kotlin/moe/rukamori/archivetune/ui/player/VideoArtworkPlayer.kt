/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package moe.rukamori.archivetune.ui.player

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.text.CueGroup
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
 *
 * NOTE: This was previously 400ms, which was too tight for VP9/AV1 hardware
 * decoders on mobile. The decoder naturally lags behind the audio player by
 * 300–800ms due to frame rendering pipeline depth. A tight tolerance causes
 * a seekTo on every poll cycle, which re-buffers, which causes MORE drift,
 * creating an infinite re-sync loop (the "video keeps pausing" bug).
 *
 * 2000ms is wide enough to absorb normal decoder lag while still catching
 * genuine desync from network hiccups or background throttling.
 */
private const val VideoSyncDriftToleranceMs = 2000L

/**
 * Drift threshold above which we fire a "soft" seek (seek the video WITHOUT
 * pausing the main audio player). The video may freeze briefly while it
 * re-buffers, but the audio continues uninterrupted.
 *
 * This is intentionally NOT a pause-load-resume — that protocol was removed
 * from the automatic drift path because it caused the "video keeps pausing
 * repeatedly" bug: the resync would pause both audio+video, the video would
 * re-buffer, both would resume, and then the next poll cycle would detect
 * drift again (because the video is naturally behind from decoder lag),
 * triggering another resync — an infinite loop.
 *
 * The pause-load-resume protocol is now ONLY triggered by an explicit call
 * to [VideoArtworkState.requestResync], which is wired to the seekbar's
 * onValueChangeFinished callback.
 */
private const val VideoSoftSeekDriftThresholdMs = 3000L

/**
 * Drift threshold above which we fire a HARD pause-load-resume resync even
 * without an explicit seekbar seek. Above this threshold the soft-seek is
 * unreliable (the re-buffer after a multi-second seek takes too long and
 * the drift keeps growing), so we fall back to the coordinated
 * pause-load-resume protocol: pause both audio + video, seek the video to
 * the audio's position, wait for the first frame, then resume both together.
 *
 * This is guarded by [VideoHardResyncCooldownMs] to prevent the infinite
 * loop that previously plagued the automatic resync path. If two hard
 * resyncs fire within the cooldown window, we assume the decoder is
 * fundamentally stuck and stop trying to resync automatically (the user
 * can still seek manually).
 */
private const val VideoHardResyncThresholdMs = 5000L

/**
 * Minimum time between automatic hard resyncs. If a hard resync fires and
 * another is requested within this window, the second request is dropped and
 * a "stuck" flag is set that disables automatic hard resync until the video
 * id changes.
 */
private const val VideoHardResyncCooldownMs = 30_000L

/**
 * How often to poll the main player's position and re-sync the video.
 */
private const val VideoSyncPollIntervalMs = 500L

/**
 * Maximum time the video ExoPlayer is allowed to stay in STATE_BUFFERING
 * before we force a re-prepare to break out of a stuck state.
 *
 * NOTE: Was 8000ms, which was too aggressive — normal network hiccups can
 * cause 5–10s of buffering, and the re-prepare itself causes a brief pause.
 * This contributed to the "video keeps pausing" bug. 20s is long enough to
 * ride out transient network issues while still catching genuinely stuck
 * states.
 */
private const val VideoStuckBufferingTimeoutMs = 20000L

/**
 * Hard cap on the resolution we will ever attempt to play.
 *
 * Capped at 1080p — 4K (2160) VP9 decoding on mobile chipsets causes
 * persistent ~500ms decoder lag that makes the video fall behind audio,
 * triggering constant re-syncs. 1080p is more than sufficient for a phone
 * screen and decodes fast enough to keep drift under the tolerance.
 */
private const val MaxVideoHeightCap = 1080

/**
 * Maximum time the main audio player stays paused while we wait for the
 * video's first frame. After this the hold is released and playback falls
 * back to audio-only (with the artwork) so the user is never stuck in
 * silence if the video is slow to resolve or the network is degraded.
 */
private const val VideoReadyHoldTimeoutMs = 10000L

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
 * The ExoPlayer itself is created ONCE for the whole composition lifetime
 * and reused across video changes (see [rememberVideoArtworkState]) — this
 * is what fixes the "switching videos crashes the app" bug. Recreating the
 * player per video meant releasing the old player while its surface was
 * still attached, which raced with the surface detaching and threw
 * IllegalStateException.
 *
 * @see rememberVideoArtworkState
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

    /**
     * Caption tracks offered by YouTube for the current video, from the
     * resolved [VideoStreamInfo]. Empty when the video has no captions.
     */
    var captionTracks: List<PlayerResponse.CaptionTrack> by mutableStateOf(emptyList())
        internal set

    /**
     * The caption track the user has chosen to display. Null = captions off.
     * Selecting a non-null track re-loads the media item with that track's
     * WebVTT URL embedded as a subtitle configuration.
     */
    var selectedCaptionTrack: PlayerResponse.CaptionTrack? by mutableStateOf(null)

    /**
     * The text of the currently active caption cue (if [selectedCaptionTrack]
     * is set and the track is producing cues), used by [VideoArtworkSurface]
     * to render a subtitle overlay. Null when captions are off or idle.
     */
    var currentCaptionText: String? by mutableStateOf(null)
        internal set

    /**
     * Pending resync request set by [requestResync]. Consumed by a
     * [LaunchedEffect] in [rememberVideoArtworkState] which performs the
     * actual pause-load-resume protocol.
     *
     * This indirection exists because [requestResync] is called from outside
     * the composable (e.g. from the seekbar's onValueChangeFinished in
     * BottomSheetPlayer) but the resync needs access to composable-scoped
     * state (the [exoPlayer], the pause/resume callbacks, etc.).
     *
     * The tuple is (position, wasPlaying, isAutomatic). [isAutomatic] marks
     * resyncs triggered by the drift poller (as opposed to explicit seekbar
     * seeks) so the cooldown logic can suppress runaway auto-resync loops.
     */
    internal var pendingResync: Triple<Long, Boolean, Boolean>? by mutableStateOf(null)

    /**
     * Epoch-millis timestamp of the last automatic (drift-triggered) hard
     * resync. Used together with [VideoHardResyncCooldownMs] to suppress
     * runaway resync loops. Reset to 0 when the video id changes.
     */
    internal var lastAutoResyncAtMs: Long by mutableStateOf(0L)

    /**
     * Sticky flag set when two automatic hard resyncs fire within
     * [VideoHardResyncCooldownMs]. While true, the drift poller stops
     * requesting automatic hard resyncs (the decoder is clearly stuck and
     * re-syncing just makes it worse). The user can still seek manually.
     * Reset when the video id changes.
     */
    internal var autoResyncDisabled: Boolean by mutableStateOf(false)

    /**
     * Request a pause-load-resume resync to [position].
     *
     * This is the SEEKBAR resync path: when the user drags the seekbar and
     * releases, the host calls this method. It:
     *   1. Pauses the main audio player (if [isPlaying]).
     *   2. Pauses the video ExoPlayer.
     *   3. Seeks the video to [position].
     *   4. Waits for the first frame to render (see [onRenderedFirstFrame]).
     *   5. Resumes both the audio and video together.
     *
     * Explicit (seekbar-triggered) resyncs bypass the cooldown — the user
     * always wins. Automatic (drift-triggered) resyncs are rate-limited via
     * [requestAutoResync] to prevent the infinite-loop bug that previously
     * plagued this path.
     */
    fun requestResync(position: Long, isPlaying: Boolean) {
        if (hasPlaybackFailed) return
        if (isResyncing) return
        pendingResync = Triple(position, isPlaying, false)
    }

    /**
     * Request an automatic (drift-triggered) hard resync. Subject to a
     * cooldown: if two auto-resyncs fire within [VideoHardResyncCooldownMs],
     * [autoResyncDisabled] is latched true and subsequent auto-resync
     * requests are dropped until the video id changes.
     */
    internal fun requestAutoResync(position: Long, isPlaying: Boolean) {
        if (hasPlaybackFailed) return
        if (isResyncing) return
        if (autoResyncDisabled) return
        val now = SystemClock.elapsedRealtime()
        if (lastAutoResyncAtMs != 0L && now - lastAutoResyncAtMs < VideoHardResyncCooldownMs) {
            Timber
                .tag(VideoPlaybackLogTag)
                .w("Auto-resync cooldown hit — disabling automatic resync for this video")
            autoResyncDisabled = true
            return
        }
        lastAutoResyncAtMs = now
        pendingResync = Triple(position, isPlaying, true)
    }
}

/**
 * Create and remember a [VideoArtworkState] for the given [videoId].
 *
 * This composable owns the ExoPlayer lifecycle: it creates the player,
 * resolves the stream URL, sets up event listeners, runs the periodic
 * position-sync poller, and releases the player when the composable leaves
 * the tree (or when [videoId] changes).
 *
 * NOTE on the crash fix: the ExoPlayer is created ONCE and reused for every
 * [videoId] this composable is shown with. Previously the player was keyed on
 * [videoId], so switching from one music video to another created a brand-new
 * ExoPlayer and released the old one while its surface was still attached —
 * a race that crashed the app with IllegalStateException. Reusing the player
 * means switching videos is just "stop the old media item, load the new one".
 *
 * The returned [VideoArtworkState] is stable across recompositions and
 * across fullscreen toggles — the ExoPlayer is NOT recreated when the
 * parent switches between inline and fullscreen surfaces. Only the
 * [VideoArtworkSurface] (the view layer) moves; the player underneath
 * keeps running.
 *
 * "Start together" audio hold: while a music video is loading, the main
 * audio player is paused (via [onRequestPauseMain]) so that audio does not
 * play alone ahead of the video. Once the video's first frame is ready the
 * audio is resumed (via [onRequestResumeMain]) and both start together.
 * If the video fails or takes longer than [VideoReadyHoldTimeoutMs], the
 * hold is released and playback falls back to audio-only.
 *
 * Seekbar pause-load-resume protocol: triggered by an explicit call to
 * [VideoArtworkState.requestResync] (wired to the seekbar's
 * onValueChangeFinished in BottomSheetPlayer). It pauses the main audio
 * player, seeks the video, waits for the first frame to render, then
 * resumes both together. This is the ONLY path that triggers a
 * pause-load-resume — the automatic drift-based resync was removed because
 * it caused the "video keeps pausing repeatedly" bug.
 *
 * Automatic drift handling: the periodic poller detects drift between the
 * audio and video positions. For large drifts (> [VideoSoftSeekDriftThresholdMs])
 * while playing, it performs a "soft seek" — seeking the video WITHOUT
 * pausing the main audio. The video may freeze briefly while it re-buffers,
 * but the audio continues uninterrupted.
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
    holdAudioUntilVideoReady: Boolean,
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
    val updatedHoldAudioUntilVideoReady by rememberUpdatedState(holdAudioUntilVideoReady)

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
    // Text tracks (captions) are explicitly ENABLED so that the
    // onCues(CueGroup) listener fires when a caption track is selected.
    // Without setTrackTypeDisabled(TEXT, false) some default track
    // selector configurations leave text tracks off, which is why
    // captions were detected but never displayed.
    val trackSelector =
        remember(context) {
            DefaultTrackSelector(context).apply {
                setParameters(
                    buildUponParameters()
                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setForceHighestSupportedBitrate(true)
                        .build(),
                )
            }
        }

    // ── Single ExoPlayer for the whole composition lifetime ──
    //
    // Deliberately NOT keyed on videoId: recreating the player per video
    // released the old player while its surface was still attached, which
    // crashed the app when switching between two music videos. Reusing the
    // player means switching videos just loads a new media item into the
    // same player. The player is only released when this composable (and
    // therefore the whole video UI) leaves the tree.
    val state =
        remember(mediaSourceFactory, renderersFactory, trackSelector) {
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

    // "Start together" audio hold state.
    //
    // While a music video is loading we pause the main audio player and hold
    // it until the video's first frame is rendered, then resume both. This
    // prevents the "audio starts first, video loads later" desync.
    var awaitingVideoReady by remember { mutableStateOf(false) }
    var resumeAudioAfterVideoReady by remember { mutableStateOf(false) }

    // Track what's currently loaded into the ExoPlayer so we can detect
    // caption-only changes (stream URL unchanged, caption track changed).
    // When a caption-only change is detected, we pause both audio and video
    // before reloading the media item, and resume both only after the first
    // frame renders — matching the quality-change pause-load-resume protocol.
    var lastLoadedStreamUrl by remember { mutableStateOf<String?>(null) }
    var lastLoadedCaptionTrack by remember { mutableStateOf<PlayerResponse.CaptionTrack?>(null) }

    fun beginAudioHold() {
        if (!updatedHoldAudioUntilVideoReady) return
        if (awaitingVideoReady) return
        awaitingVideoReady = true
        resumeAudioAfterVideoReady = shouldPlay
        if (shouldPlay) {
            Timber
                .tag(VideoPlaybackLogTag)
                .d("Holding main audio while video for $videoId loads")
            updatedOnRequestPauseMain()
        }
    }

    fun releaseAudioHold() {
        if (!awaitingVideoReady) return
        awaitingVideoReady = false
        val shouldResume = resumeAudioAfterVideoReady
        resumeAudioAfterVideoReady = false
        if (shouldResume) {
            Timber
                .tag(VideoPlaybackLogTag)
                .d("Video ready — resuming main audio")
            updatedOnRequestResumeMain()
        }
    }

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
        state.selectedCaptionTrack = null
        state.captionTracks = emptyList()
        state.currentCaptionText = null
        // Reset the automatic-resync cooldown trackers for the new video.
        // A new video gets a fresh chance at automatic drift resync even if
        // the previous video's decoder got stuck.
        state.lastAutoResyncAtMs = 0L
        state.autoResyncDisabled = false
        // Reset the "last loaded" trackers so the next media-item load is
        // treated as a fresh load (not a caption-only change).
        lastLoadedStreamUrl = null
        lastLoadedCaptionTrack = null

        beginAudioHold()

        val resolved =
            withContext(Dispatchers.IO) {
                resolveVideoStreamUrl(videoId, updatedPreferredHeight)
            }

        state.isResolvingUrl = false

        if (resolved == null) {
            state.hasPlaybackFailed = true
            releaseAudioHold()
            updatedOnPlaybackFailed()
            updatedOnStreamResolved(null)
        } else {
            state.streamUrl = resolved.streamUrl
            state.captionTracks = resolved.captionTracks
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
            state.captionTracks = resolved.captionTracks
            updatedOnStreamResolved(resolved)
        } else {
            state.isChangingQuality = false
            val fallback =
                withContext(Dispatchers.IO) {
                    resolveVideoStreamUrl(videoId, updatedPreferredHeight)
                }
            if (fallback != null) {
                state.streamUrl = fallback.streamUrl
                state.captionTracks = fallback.captionTracks
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
    //
    // Reloads when the stream URL changes (new video / quality swap) and when
    // the selected caption track changes (so the caption's WebVTT URL can be
    // embedded as a subtitle configuration on the media item).
    //
    // CAPTION-CHANGE PAUSE-LOAD-RESUME: When ONLY the caption track changes
    // (stream URL stays the same), we still need to rebuild and reload the
    // media item (ExoPlayer cannot hot-swap subtitle configurations on a
    // playing item). This reload causes the video to buffer again. To match
    // the quality-change behavior — and the user's explicit requirement that
    // "neither audio nor video should resume on its own until both are
    // loaded" — we set isChangingQuality=true and pause the main audio player
    // BEFORE reloading. The hold is released in onRenderedFirstFrame.
    LaunchedEffect(state.streamUrl, state.selectedCaptionTrack, exoPlayer) {
        val url = state.streamUrl ?: return@LaunchedEffect

        // Detect caption-only change: stream URL is unchanged but the caption
        // track differs from what's currently loaded. We track this with a
        // remembered "last loaded caption" so we can pause the main audio
        // before the reload and resume it after the first frame renders.
        val captionBeingChanged =
            url == lastLoadedStreamUrl &&
                state.selectedCaptionTrack != lastLoadedCaptionTrack
        if (captionBeingChanged) {
            state.wasPlayingBeforeQualityChange = shouldPlay
            state.isChangingQuality = true
            exoPlayer.pause()
            if (shouldPlay) updatedOnRequestPauseMain()
        }
        lastLoadedStreamUrl = url
        lastLoadedCaptionTrack = state.selectedCaptionTrack

        state.isVideoReady = false
        state.hasPlaybackFailed = false
        state.currentCaptionText = null

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

        // Embed the user-selected caption track. YouTube's timedtext endpoint
        // returns WebVTT when `fmt=vtt` is appended (see CaptionTrack.webVttUrl),
        // which ExoPlayer parses natively. Marking it SELECTION_FLAG_DEFAULT
        // makes the default track selector pick it automatically so the
        // onCues listener below receives the subtitle text.
        state.selectedCaptionTrack?.let { track ->
            mediaItemBuilder.setSubtitleConfigurations(
                listOf(
                    MediaItem
                        .SubtitleConfiguration
                        .Builder(track.webVttUrl().toUri())
                        .setMimeType(MimeTypes.TEXT_VTT)
                        .setLanguage(track.languageCode)
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build(),
                ),
            )
        }

        val mediaItem = mediaItemBuilder.build()

        exoPlayer.stop()
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()

        val targetPosition = currentPosition()
        if (targetPosition > 0) {
            exoPlayer.seekTo(targetPosition)
        }
        exoPlayer.playWhenReady = shouldPlay && !awaitingVideoReady
    }

    // ── Play/pause follower ──
    LaunchedEffect(isPlaying, awaitingVideoReady, state.isChangingQuality) {
        if (state.hasPlaybackFailed) {
            exoPlayer.pause()
        } else if (awaitingVideoReady) {
            // The video isn't ready yet — keep both paused so audio cannot
            // run ahead of the video (and vice-versa). If the user pressed
            // play during the hold, immediately re-pause the main player.
            if (isPlaying) updatedOnRequestPauseMain()
            exoPlayer.pause()
        } else if (state.isChangingQuality) {
            // A quality or caption change is in progress — keep both paused
            // until the first frame of the new media item renders. This
            // ensures neither audio nor video resumes on its own while the
            // other is still loading.
            if (isPlaying) updatedOnRequestPauseMain()
            exoPlayer.pause()
        } else {
            exoPlayer.setVideoPlayback(isPlaying)
        }
    }

    // ── Update track selector when caption selection changes ──
    //
    // Setting the preferred text language on the DefaultTrackSelector makes
    // it actively select the caption track that matches the user's choice.
    // Without this, even with SELECTION_FLAG_DEFAULT on the subtitle config,
    // the track selector may not pick it up if there are multiple text
    // tracks or if the default selection logic doesn't match. When captions
    // are off (selectedCaptionTrack == null), we clear the preferred language
    // so no text track is selected.
    LaunchedEffect(state.selectedCaptionTrack, trackSelector) {
        val lang = state.selectedCaptionTrack?.languageCode
        val params =
            trackSelector
                .buildUponParameters()
                .apply {
                    if (!lang.isNullOrBlank()) {
                        setPreferredTextLanguage(lang)
                    }
                }.build()
        trackSelector.setParameters(params)
    }

    // ── Safety net: release the audio hold if the video never becomes ready ──
    LaunchedEffect(state.streamUrl) {
        if (state.streamUrl == null) return@LaunchedEffect
        delay(VideoReadyHoldTimeoutMs)
        if (awaitingVideoReady && !state.isVideoReady) {
            Timber
                .tag(VideoPlaybackLogTag)
                .w("Video not ready within ${VideoReadyHoldTimeoutMs}ms — releasing audio hold")
            releaseAudioHold()
        }
    }

    // ── Manual resync (seekbar) ──
    //
    // Triggered by [VideoArtworkState.requestResync], which is called from
    // the seekbar's onValueChangeFinished in BottomSheetPlayer. This is the
    // ONLY path that performs a pause-load-resume. The automatic drift-based
    // resync was removed because it caused the "video keeps pausing repeatedly"
    // bug (infinite resync loop).
    LaunchedEffect(state.pendingResync) {
        val (position, wasPlaying, isAutomatic) = state.pendingResync ?: return@LaunchedEffect
        // Consume the request immediately so subsequent calls re-trigger.
        state.pendingResync = null
        if (state.hasPlaybackFailed || state.isResyncing) return@LaunchedEffect
        Timber
            .tag(VideoPlaybackLogTag)
            .d(
                "Resync to ${position}ms (wasPlaying=$wasPlaying, auto=$isAutomatic) — pause-load-resume",
            )
        state.wasPlayingBeforeResync = wasPlaying
        state.isResyncing = true
        state.isVideoReady = false
        if (wasPlaying) updatedOnRequestPauseMain()
        exoPlayer.pause()
        exoPlayer.seekTo(position)
        state.bufferingStartedAtMs = SystemClock.elapsedRealtime()
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

            // Drift detection — three tiers:
            //
            // 1. SMALL drift (<= VideoSoftSeekDriftThresholdMs while playing,
            //    <= VideoSyncDriftToleranceMs while paused):
            //    IGNORE. The video self-corrects because both play at 1x.
            //    Decoder lag of 300–800ms is normal and imperceptible.
            //
            // 2. MEDIUM drift (> VideoSoftSeekDriftThresholdMs while playing):
            //    SOFT seek — seek the video WITHOUT pausing the main audio.
            //    The video may freeze briefly while re-buffering, but the
            //    audio continues uninterrupted. Good tradeoff for a music app.
            //
            // 3. LARGE drift (> VideoHardResyncThresholdMs while playing OR
            //    paused): HARD pause-load-resume via [requestAutoResync].
            //    The soft seek is unreliable at multi-second drift (the
            //    re-buffer takes too long and drift keeps growing), so we
            //    coordinate a full pause-load-resume. This is rate-limited
            //    by [VideoHardResyncCooldownMs] and latches
            //    [VideoArtworkState.autoResyncDisabled] if two fire within
            //    the cooldown — preventing the infinite-loop bug that
            //    previously plagued automatic resync.
            //
            // For explicit seekbar seeks, the user-initiated
            // [VideoArtworkState.requestResync] bypasses the cooldown.
            if (state.isChangingQuality) continue
            if (state.isResyncing) continue
            if (!state.isVideoReady) continue
            if (awaitingVideoReady) continue

            val mainPos = currentPosition()
            if (mainPos <= 0) continue
            val videoPos = exoPlayer.currentPosition
            val drift = kotlin.math.abs(videoPos - mainPos)

            if (drift > VideoHardResyncThresholdMs) {
                Timber
                    .tag(VideoPlaybackLogTag)
                    .w("Hard resync: drift=${drift}ms (main=$mainPos, video=$videoPos, playing=$shouldPlay)")
                state.requestAutoResync(mainPos, shouldPlay)
            } else if (drift > VideoSoftSeekDriftThresholdMs && shouldPlay) {
                Timber
                    .tag(VideoPlaybackLogTag)
                    .d("Soft seek: drift=${drift}ms (main=$mainPos, video=$videoPos)")
                exoPlayer.seekTo(mainPos)
            } else if (drift > VideoSyncDriftToleranceMs && !shouldPlay) {
                exoPlayer.seekTo(mainPos)
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
                    releaseAudioHold()
                    updatedOnPlaybackFailed()
                }

                override fun onCues(cueGroup: CueGroup) {
                    val text =
                        cueGroup.cues
                            .joinToString("\n") { it.text?.toString().orEmpty() }
                            .takeIf { it.isNotBlank() }
                    state.currentCaptionText = text
                }

                override fun onRenderedFirstFrame() {
                    state.isVideoReady = true
                    releaseAudioHold()
                    val wasChangingQuality = state.isChangingQuality
                    state.isChangingQuality = false
                    val wasResync = state.isResyncing
                    state.isResyncing = false
                    val wasPlayingBeforeResyncLocal = state.wasPlayingBeforeResync
                    state.wasPlayingBeforeResync = false
                    state.bufferingStartedAtMs = 0L

                    // ── Fix: guarantee video & audio start at the SAME position ──
                    //
                    // Before resuming, snap the video to the audio player's
                    // CURRENT position. During the hold the audio was paused,
                    // but its position may have advanced by a few hundred ms
                    // between the hold beginning and the video URL resolving.
                    // Without this snap, the video resumes at the position it
                    // was loaded at (which may be stale), causing the
                    // "video plays first, audio catches up later" mismatch.
                    //
                    // We skip this snap during a manual (seekbar) resync
                    // because the seekTo(position) in the pendingResync
                    // consumer already placed the video exactly where the
                    // user wants it.
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

                    // Decide whether to resume playback. During a quality or
                    // caption change, the main audio player was paused — so
                    // shouldPlay (which tracks the main player's state) is
                    // false at this point. We must use wasPlayingBeforeQualityChange
                    // to decide whether to resume BOTH the video and the main
                    // audio together.
                    val effectiveShouldPlay =
                        shouldPlay ||
                            (wasResync && wasPlayingBeforeResyncLocal) ||
                            (wasChangingQuality && state.wasPlayingBeforeQualityChange)
                    if (effectiveShouldPlay && !state.hasPlaybackFailed && exoPlayer.playerError == null) {
                        // Resume the MAIN audio player FIRST. The audio is
                        // the source of truth for position — if we start the
                        // video first, the video advances while the audio is
                        // still spinning up, creating the "video plays first"
                        // mismatch the user reported. By resuming audio first
                        // and the video immediately after (synchronously on
                        // the same main-looper tick), both start at the same
                        // instant from the same position.
                        //
                        // We call updatedOnRequestResumeMain() in ALL three
                        // branches (quality change, resync, and normal play)
                        // because in the normal-play path the audio hold may
                        // have just been released by releaseAudioHold()
                        // above — calling resume again is a safe no-op if
                        // the audio is already playing, but ensures we don't
                        // leave the audio paused if the hold released but
                        // the resume hasn't propagated yet.
                        if (wasChangingQuality && state.wasPlayingBeforeQualityChange) {
                            updatedOnRequestResumeMain()
                        }
                        if (wasResync && wasPlayingBeforeResyncLocal) {
                            updatedOnRequestResumeMain()
                        }
                        if (shouldPlay) {
                            updatedOnRequestResumeMain()
                        }
                        exoPlayer.playWhenReady = true
                        exoPlayer.play()
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
                            // During a quality or caption change, the main
                            // audio player is paused (shouldPlay == false),
                            // so we must consult wasPlayingBeforeQualityChange
                            // to decide whether to resume the video. The main
                            // audio itself is resumed in onRenderedFirstFrame.
                            val effectiveShouldPlay =
                                shouldPlay ||
                                    (state.isResyncing && state.wasPlayingBeforeResync) ||
                                    (state.isChangingQuality && state.wasPlayingBeforeQualityChange)
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
    //
    // The release is deferred to the next main-looper pass so any surface
    // still attached in this frame (e.g. when switching to a non-video song)
    // can detach cleanly first — accessing a released player throws.
    //
    // The audio hold is released IMMEDIATELY (not deferred): if the hold was
    // active (main audio paused while waiting for the video's first frame)
    // and this composable leaves the tree — e.g. the user skips from a music
    // video to a non-video song while the video is still loading — the main
    // audio must be resumed right away or it would stay paused forever.
    DisposableEffect(exoPlayer) {
        onDispose {
            releaseAudioHold()
            Handler(Looper.getMainLooper()).post {
                exoPlayer.release()
            }
        }
    }

    return state
}

/**
 * Conditional wrapper around [rememberVideoArtworkState] that returns `null`
 * when [videoId] is blank.
 *
 * This is used by the host (BottomSheetPlayer) to hoist the [VideoArtworkState]
 * above the `when (orientation)` block AND above the BottomSheet content — so
 * the ExoPlayer survives:
 *   - Orientation changes (the `when` block can switch freely without
 *     releasing the ExoPlayer).
 *   - Sheet collapse/expand (the ExoPlayer is not tied to the sheet's content
 *     lifecycle).
 *   - Fullscreen toggle (the fullscreen overlay is a sibling of the BottomSheet,
 *     sharing the same state).
 *
 * When [videoId] is blank (no music video playing), this returns `null` and
 * does NOT create an ExoPlayer — avoiding unnecessary resource usage.
 */
@Composable
fun rememberVideoArtworkStateOrNull(
    videoId: String?,
    isPlaying: Boolean,
    positionProvider: () -> Long,
    preferredHeight: Int?,
    holdAudioUntilVideoReady: Boolean,
    onStreamResolved: (VideoStreamInfo?) -> Unit,
    onPlaybackFailed: () -> Unit,
    onLoadingStateChange: (Boolean) -> Unit,
    onRequestPauseMain: () -> Unit,
    onRequestResumeMain: () -> Unit,
): VideoArtworkState? {
    return if (videoId.isNullOrBlank()) {
        onPlaybackFailed()
        null
    } else {
        rememberVideoArtworkState(
            videoId = videoId,
            isPlaying = isPlaying,
            positionProvider = positionProvider,
            preferredHeight = preferredHeight,
            holdAudioUntilVideoReady = holdAudioUntilVideoReady,
            onStreamResolved = onStreamResolved,
            onPlaybackFailed = onPlaybackFailed,
            onLoadingStateChange = onLoadingStateChange,
            onRequestPauseMain = onRequestPauseMain,
            onRequestResumeMain = onRequestResumeMain,
        )
    }
}

/**
 * Renders the video surface for the given [state].
 *
 * This is a pure view composable — it reads [VideoArtworkState.isVideoReady]
 * for the alpha animation and renders a [ContentFrame] attached to
 * [VideoArtworkState.exoPlayer]. It does NOT create or manage the ExoPlayer;
 * that's the responsibility of [rememberVideoArtworkState].
 *
 * Because the ExoPlayer is external, this composable can be freely mounted
 * in different parents (inline slot, fullscreen overlay) without causing
 * the video to reload. The ExoPlayer's surface is detached from the old
 * view and attached to the new one — a fast operation that does NOT
 * interrupt playback.
 *
 * When a caption track is selected, the currently active cue text is
 * rendered as a subtitle overlay along the bottom edge.
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

        // Caption overlay — rendered from the ExoPlayer's current cue text.
        val captionText = state.currentCaptionText
        if (state.selectedCaptionTrack != null && !captionText.isNullOrBlank()) {
            androidx.compose.material3.Text(
                text = captionText,
                color = Color.White,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
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
                        ).getOrNull()
                        ?.let { StreamClientUtils.patchClientVersion(it, client.clientVersion) }
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
