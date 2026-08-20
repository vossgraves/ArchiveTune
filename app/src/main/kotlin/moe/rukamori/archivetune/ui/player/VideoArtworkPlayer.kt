/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package moe.rukamori.archivetune.ui.player

import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.media3.common.PlaybackParameters
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
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.VideoPlaybackSpeedKey
import moe.rukamori.archivetune.innertube.NewPipeUtils
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_65_10
import moe.rukamori.archivetune.innertube.models.YouTubeClient.Companion.WEB_REMIX
import moe.rukamori.archivetune.innertube.models.response.PlayerResponse
import moe.rukamori.archivetune.utils.ImageBlurUtils
import moe.rukamori.archivetune.utils.StreamClientUtils
import moe.rukamori.archivetune.utils.rememberPreference
import okhttp3.OkHttpClient
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Below this absolute drift, NO correction is applied — the desync is
 * imperceptible to the user.
 *
 * Human perception thresholds for A/V desync (industry research, ITU-R
 * BT.1359-1): ~50ms is imperceptible, ~100ms is noticeable to trained
 * viewers, ~200ms is noticeable to casual viewers, >400ms is annoying.
 * 60ms sits safely in the "imperceptible" band.
 *
 * The previous tolerance was 2000ms — that is WHY the user perceived
 * constant desync: drifts of 300–1500ms (very common with VP9/AV1
 * hardware decoder lag) were silently ignored. Only pause/resume or a
 * quality change (which run the full pause-load-resume protocol) would
 * resync. This tight 60ms floor replaces that ignore-everything policy.
 */
private const val VideoSyncIgnoreToleranceMs = 60L

/**
 * Drift above this threshold triggers a **soft seek** (a re-anchor) instead
 * of speed-based correction. Below this, drift is corrected gradually by
 * adjusting the video ExoPlayer's playback speed (see
 * [VideoSyncSpeedCorrectionFactorMax]); above it, the video is seeked
 * straight to the audio's current position while the audio keeps playing.
 *
 * The previous 400ms value was a regression: a re-anchor seek re-buffers
 * (a visible 1–2s stall), and a 400ms threshold fired repeatedly during
 * the first seconds of playback — the video's decoder warms up slightly
 * behind the audio, so the poller kept seeking it back, which kept
 * re-buffering it → "lags for the initial seconds after first play".
 *
 * 2000ms keeps the seek-based correction for *genuine* large drift while
 * never firing during normal warm-up. The user's actual "video is laggy"
 * problem is NOT a position error — it's a stale/frozen presentation on
 * the surface (their pause/resume "fix" doesn't seek either). That is
 * handled separately by [VideoArtworkState.kickRenderer] (a video-only
 * pause/resume micro-cycle), which the surface re-attach path and the
 * frozen-renderer detector use. The seek here is the last resort for real
 * desync (e.g. a long decode stall) where a brief re-buffer stall is
 * preferable to staying out of sync.
 */
private const val VideoSoftSeekDriftThresholdMs = 2000L

/**
 * Maximum proportional speed adjustment applied for drift correction.
 *
 * When drift is in the [VideoSyncIgnoreToleranceMs]..
 * [VideoSoftSeekDriftThresholdMs] band, the video ExoPlayer's playback
 * speed is adjusted by up to ±20% to gently catch up / slow down to the
 * audio clock — WITHOUT seeking, WITHOUT re-buffering. This is the same
 * approach MPV and VLC use for their default A/V sync (MPV's
 * `video-sync=audio` mode).
 *
 * The correction is proportional: drift = 0 → factor = 1.0; drift =
 * +1000ms (video ahead) → factor ≈ 0.90; drift = -1000ms (video behind)
 * → factor ≈ 1.10; drift = ±2000ms → factor = 0.80 / 1.20 (capped).
 *
 * The factor is recomputed every poll cycle ([VideoSyncPollIntervalMs]
 * = 250ms), so it continuously narrows as drift approaches zero. Once
 * drift drops below [VideoSyncIgnoreToleranceMs] the factor resets to
 * 1.0 and the video returns to the user's preferred playback speed.
 *
 * The normalization is over [VideoSoftSeekDriftThresholdMs] (2000ms), so
 * a moderate drift converges within a few seconds — gently, with no
 * re-buffer stall. Above 2000ms the drift poller switches to a re-anchor
 * seek (see [VideoSoftSeekDriftThresholdMs]).
 *
 * 20% is noticeable to the user IF sustained, but because it's
 * proportional and only sustained while drift is being corrected (a
 * few seconds at most), it reads as "the video caught up" rather than
 * "the video is playing at the wrong speed".
 */
private const val VideoSyncSpeedCorrectionFactorMax = 0.20f

/**
 * Time during which drift checks are suppressed after a seek (soft seek,
 * manual resync, or the initial-load snap in [onRenderedFirstFrame]).
 *
 * After a seek the video ExoPlayer enters STATE_BUFFERING while it
 * re-decodes from the new position. During this window its
 * `currentPosition` is stale and the audio player keeps advancing, so a
 * drift check would always fire and either re-seek (infinite loop) or
 * apply a huge speed correction that immediately gets undone when the
 * video catches up. Suppressing checks for 2s lets the video re-buffer
 * fully before we trust its position again.
 *
 * 2s is empirically enough for 1080p VP9/AV1 to re-buffer on a typical
 * mobile connection; the prior 20s stuck-buffering timeout
 * ([VideoStuckBufferingTimeoutMs]) still catches genuinely stuck
 * decoders separately.
 */
private const val VideoSeekSettlingTimeMs = 2000L

/**
 * Minimum time between [VideoArtworkState.kickRenderer] micro-cycles.
 *
 * When the video surface is re-attached while the player is already
 * playing (fullscreen toggle, orientation change, window resize), or a
 * frozen renderer is detected by the drift poller, we restart the video
 * renderer with a video-only pause/resume micro-cycle (the same action as
 * the user's manual "pause and resume" fix, but without touching the
 * audio and without re-buffering).
 *
 * That pause/resume can itself produce another `onRenderedFirstFrame`
 * (and a renderer that immediately re-sticks would re-trigger the
 * detector), so the min-interval guard in [VideoArtworkState.kickRenderer]
 * stamps [VideoArtworkState.lastSurfaceReanchorAtMs] and drops any
 * request within 2s of the previous one.
 */
private const val SurfaceReanchorMinIntervalMs = 2000L

/**
 * Number of consecutive drift-poll cycles over which the video position
 * must fail to advance (while the audio position advances) before we
 * conclude the renderer is frozen and fire a
 * [VideoArtworkState.kickRenderer] micro-cycle.
 *
 * With a [VideoSyncPollIntervalMs] of 250ms, this detects a frozen
 * renderer within ~750ms — BEFORE its accumulated drift reaches the
 * [VideoSoftSeekDriftThresholdMs] re-anchor threshold, so the normal
 * path is a no-stall renderer restart instead of a re-buffer seek.
 */
private const val VideoFrozenRendererCycles = 3

/**
 * A video position is considered "not advancing" in the frozen-renderer
 * detector if it moved less than this many milliseconds over a poll cycle.
 * A renderer that is presenting frames advances its position by roughly
 * the poll interval each cycle; a frozen one barely moves at all.
 */
private const val VideoFrozenRendererMaxAdvanceMs = 50L

/**
 * Tight drift tolerance used ONLY for the initial sync in
 * [onRenderedFirstFrame]. When the video's first frame renders, we snap the
 * video to the audio's current position — but only if the drift exceeds this
 * threshold. 200ms is imperceptible to the user while avoiding a redundant
 * seekTo (which would trigger a brief re-buffer) when the video is already
 * close enough.
 */
private const val VideoInitialSyncToleranceMs = 200L

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
 *
 * 250ms (previously 500ms) makes the frozen-renderer detector respond
 * within ~750ms (3 cycles) and keeps speed correction responsive. The
 * polling cost is negligible (a couple of reads every 250ms).
 */
private const val VideoSyncPollIntervalMs = 250L

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
 * Delay between the video's first frame rendering and the actual resume
 * of both audio and video playback.
 *
 * When a music video loads (initial load OR quality change OR resync),
 * the main audio player is paused (see [beginAudioHold] /
 * `state.isChangingQuality` / `state.isResyncing`) and the video
 * ExoPlayer is held paused while it buffers. When the video's first
 * frame renders ([Player.Listener.onRenderedFirstFrame]) we DON'T
 * resume immediately — instead we schedule the resume for
 * [VideoLoadResumeDelayMs] ms later (see the `pendingResumeAtMs`
 * LaunchedEffect in [rememberVideoArtworkState]).
 *
 * This deliberate 1-second pause-after-ready window is the user's
 * explicit request: it gives both the audio decoder (which had gone
 * idle while paused) and the video decoder (which just finished
 * buffering) time to fully pre-roll in parallel BEFORE either starts
 * advancing its clock. Both then start together from a known-aligned
 * position, eliminating the "audio starts first, video catches up"
 * desync that occurred when audio resumed immediately on first frame
 * while the video decoder was still spinning up.
 *
 * 1000ms is long enough for both decoders to fully pre-roll on a
 * typical mobile chipset, and short enough that the user perceives it
 * as a brief loading pause rather than a stall.
 */
private const val VideoLoadResumeDelayMs = 1000L

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
    var bufferingStartedAtMs: Long by mutableLongStateOf(0L)
        internal set

    /**
     * Current speed-correction factor applied on top of the user's
     * preferred playback speed ([VideoPlaybackSpeedKey]) to gently bring
     * the video back into sync with the main audio clock.
     *
     * 1.0 = no correction (in sync or within
     * [VideoSyncIgnoreToleranceMs]). Between 0.80 and 1.20 = actively
     * correcting drift: <1.0 means the video is ahead of the audio and
     * is being slowed down; >1.0 means the video is behind and is being
     * sped up.
     *
     * The effective video speed is `userSpeed * currentSpeedCorrectionFactor`,
     * computed in the speed-follower LaunchedEffect in
     * [rememberVideoArtworkState]. The drift poller updates this factor
     * every [VideoSyncPollIntervalMs]; the speed follower re-applies it
     * whenever it changes.
     *
     * See [VideoSyncSpeedCorrectionFactorMax] for the proportional
     * control rationale.
     */
    var currentSpeedCorrectionFactor by mutableStateOf(1.0f)
        internal set

    /**
     * Epoch-millis (from [SystemClock.elapsedRealtime]) of the last
     * seek performed on the video ExoPlayer — soft seek, manual resync,
     * or the initial-load snap in onRenderedFirstFrame.
     *
     * The drift poller consults this to suppress drift checks for
     * [VideoSeekSettlingTimeMs] after any seek, preventing the re-buffer
     * loop where the video's stale position during re-buffer immediately
     * re-triggers another seek.
     *
     * Reset to 0 when the video id changes (the new video gets a fresh
     * settling window from its own initial-load snap).
     */
    var lastSeekAtMs: Long by mutableLongStateOf(0L)
        internal set

    /**
     * Epoch-millis (from [SystemClock.elapsedRealtime]) of the last
     * **renderer restart** — a video-only pause/resume micro-cycle
     * performed by [kickRenderer], either from
     * [Player.Listener.onRenderedFirstFrame] when the video surface is
     * re-attached while already playing (fullscreen toggle / orientation
     * change) or by the drift poller's frozen-renderer detector.
     *
     * [SurfaceReanchorMinIntervalMs] uses this to rate-limit the
     * micro-cycles: the pause/resume can itself produce another
     * `onRenderedFirstFrame` (and a renderer that immediately re-sticks
     * would re-trigger the detector), so without the interval guard the
     * restart could loop. The drift poller also stamps this when it
     * performs a large-drift re-anchor seek, so that seek's follow-up
     * `onRenderedFirstFrame` stands down instead of double-restarting.
     *
     * Reset to 0 when the video id changes (the new video's own first-frame
     * flow handles its initial sync).
     */
    var lastSurfaceReanchorAtMs: Long by mutableLongStateOf(0L)
        internal set

    /**
     * Epoch-millis (from [SystemClock.elapsedRealtime]) at which the
     * pending resume should fire. Set by [Player.Listener.onRenderedFirstFrame]
     * to `now + [VideoLoadResumeDelayMs]` when the first frame renders.
     *
     * A `LaunchedEffect(state.pendingResumeAtMs)` in
     * [rememberVideoArtworkState] watches this field; when it transitions
     * to a non-zero value the effect `delay`s until the scheduled time
     * and then resumes both the main audio player and the video
     * ExoPlayer together (see [state.pendingResumeMainAudio] and
     * [state.pendingResumeVideo]).
     *
     * Setting this to a new non-zero value automatically cancels any
     * previously-pending resume (LaunchedEffect re-launches on key
     * change), so it's safe to overwrite if a new load completes while
     * an old resume is still pending.
     *
     * Reset to 0 when the video id changes (no resume should fire for
     * the previous video).
     */
    var pendingResumeAtMs: Long by mutableLongStateOf(0L)
        internal set

    /**
     * Whether the pending resume (see [pendingResumeAtMs]) should call
     * `updatedOnRequestResumeMain()` to resume the main audio player.
     *
     * True for the initial-load path (where [beginAudioHold] paused the
     * main audio), for the quality-change path (where the
     * `state.isChangingQuality` block paused it), and for the resync
     * path (where `state.isResyncing` paused it). All three paths set
     * this to true when scheduling the resume.
     */
    var pendingResumeMainAudio: Boolean by mutableStateOf(false)
        internal set

    /**
     * Whether the pending resume (see [pendingResumeAtMs]) should
     * `exoPlayer.play()` the video ExoPlayer.
     *
     * Mirrors [pendingResumeMainAudio] but for the video side. Kept as a
     * separate flag in case a future code path wants to resume only one
     * of the two (currently they always resume together).
     */
    var pendingResumeVideo: Boolean by mutableStateOf(false)
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
    internal var lastAutoResyncAtMs: Long by mutableLongStateOf(0L)

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
     *
     * @return `true` if the resync was accepted (a pending pause-load-resume
     *   will run), `false` if it was dropped (cooldown / latch / already
     *   resyncing / failed). The drift poller falls back to a plain re-anchor
     *   seek when this returns `false` so the video still self-heals without
     *   ever pausing the audio.
     */
    internal fun requestAutoResync(position: Long, isPlaying: Boolean): Boolean {
        if (hasPlaybackFailed) return false
        if (isResyncing) return false
        if (autoResyncDisabled) return false
        val now = SystemClock.elapsedRealtime()
        if (lastAutoResyncAtMs != 0L && now - lastAutoResyncAtMs < VideoHardResyncCooldownMs) {
            Timber
                .tag(VideoPlaybackLogTag)
                .w("Auto-resync cooldown hit — disabling automatic resync for this video")
            autoResyncDisabled = true
            return false
        }
        lastAutoResyncAtMs = now
        pendingResync = Triple(position, isPlaying, true)
        return true
    }

    /**
     * Video-only pause/resume micro-cycle — the automated version of the
     * user's manual "pause and resume" that fixes a laggy video.
     *
     * The video can look laggy/frozen while the audio keeps playing even
     * though its *position* is fine (or frozen) — the surface is holding a
     * stale frame or the renderer has stopped presenting frames. This is
     * NOT a position error, so the drift poller's seek-based correction
     * can't fix it (and a seek makes it worse by adding a re-buffer stall).
     *
     * Pausing and resuming the video player forces the renderer to
     * re-sync its presentation clock to the current position and start
     * presenting fresh frames — WITHOUT pausing the main audio and
     * WITHOUT seeking/re-buffering. This is exactly what the manual
     * pause/resume fix does, minus the audio interruption.
     *
     * Called deterministically when the video surface is re-created
     * (fullscreen toggle / orientation change / window resize, via
     * [Player.Listener.onRenderedFirstFrame]) and by the drift poller when
     * it detects a frozen renderer.
     *
     * Rate-limited by [SurfaceReanchorMinIntervalMs] so a renderer that
     * immediately re-sticks can't cause a busy loop.
     *
     * @return `true` if the micro-cycle was performed, `false` if it was
     *   dropped (not playing / not ready / resyncing / failed / within the
     *   rate-limit window).
     */
    internal fun kickRenderer(now: Long = SystemClock.elapsedRealtime()): Boolean {
        if (hasPlaybackFailed) return false
        if (isResyncing) return false
        if (exoPlayer.playbackState != Player.STATE_READY) return false
        if (!exoPlayer.playWhenReady) return false
        if (lastSurfaceReanchorAtMs != 0L && now - lastSurfaceReanchorAtMs < SurfaceReanchorMinIntervalMs) {
            return false
        }
        lastSurfaceReanchorAtMs = now
        // Pause then resume in the same call. ExoPlayer processes these in
        // order on the playback thread: the renderer stops presenting,
        // re-derives its presentation clock from the current position, and
        // resumes presenting fresh frames. No seek → no re-buffer stall.
        exoPlayer.pause()
        exoPlayer.play()
        return true
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
 * audio and video positions. Small drifts (> [VideoSyncIgnoreToleranceMs],
 * up to [VideoSoftSeekDriftThresholdMs]) are corrected by gently adjusting
 * the video's playback speed. Larger drifts (> [VideoSoftSeekDriftThresholdMs])
 * are corrected with an immediate "re-anchor" — seeking the video to the
 * audio's current position WITHOUT pausing the main audio. The threshold is
 * deliberately high (2000ms) so the re-buffer stall a seek causes only
 * happens for genuine large drift, never during normal warm-up. Corrections
 * only run while the video is in STATE_READY (its position is trustworthy)
 * and are suppressed for [VideoSeekSettlingTimeMs] after any seek, so a
 * re-anchor can never re-trigger itself in a loop.
 *
 * Frozen/laggy renderer handling: a video can LOOK laggy or frozen while
 * the audio plays on even though its position is fine (or frozen) — the
 * surface is holding a stale frame or the renderer stopped presenting
 * frames. That is NOT a position drift, so seeking can't fix it (and a
 * seek makes it worse with a re-buffer stall). The surface re-attach path
 * (fullscreen toggle / orientation change) restarts the renderer with a
 * video-only pause/resume micro-cycle ([VideoArtworkState.kickRenderer]),
 * and the poller's frozen-renderer detector does the same mid-playback —
 * both are the automated version of the user's manual "pause and resume"
 * fix, without pausing the audio and without re-buffering.
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
    //
    // MIRRORS MusicService.mediaOkHttpClient — the audio player's client. The previous
    // implementation was a stripped-down version that was missing:
    //   - explicit followRedirects / followSslRedirects (OkHttp's default is true, but
    //     being explicit makes the intent clear and matches the audio client)
    //   - explicit connectTimeout / readTimeout (OkHttp's default is 10s, which is too
    //     short for a 1080p video load on a slow connection — the audio client uses 30s)
    //   - the kouzu.in x-request-source: muzo header branch (not strictly needed for
    //     video, but matches the audio client so the two stay in sync)
    //
    // The user's crash log showed HTTP 403 from googlevideo.com when ExoPlayer tried to
    // load the ANDROID_VR video URL, while the audio URL (also ANDROID_VR) loaded fine.
    // The audio and video clients had different setups — matching them eliminates that
    // variable. If the 403 still happens after this fix, the root cause is YouTube
    // rejecting the ANDROID_VR video URL specifically (signature/IP-rate-limit/etc.) and
    // we'd need to fall back to a different client (see resolveVideoStreamUrl's clients
    // list — it now tries ANDROID_VR first, then WEB_REMIX; the WEB_REMIX fallback kicks
    // in when ANDROID_VR's URL resolution itself fails, not when its URL gives 403 on
    // playback — for the latter, a more invasive fix would be needed: detect 403 on
    // ExoPlayer's load and re-resolve via the next client).
    val okHttpClient =
        remember {
            OkHttpClient
                .Builder()
                .proxy(YouTube.streamOkHttpProxy)
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
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
                        // Match the audio client: pass through non-YouTube hosts unchanged
                        // (no User-Agent override). The audio client doesn't override the
                        // User-Agent for non-YouTube hosts either.
                        return@addInterceptor chain.proceed(request)
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
        // PAUSE the main audio player for the duration of the video
        // load + the [VideoLoadResumeDelayMs] settling period after the
        // first frame renders. Both audio and video resume together
        // after the 1-second delay — see the `pendingResumeAtMs`
        // LaunchedEffect below.
        //
        // This is the user's explicit request: when a music video
        // loads, pause BOTH audio and video for ~1 second, then resume
        // them together. This eliminates the "audio starts first,
        // video catches up" desync that occurred when the audio played
        // continuously during load (the previous behavior) AND the
        // "video starts first, audio catches up" desync that occurred
        // when only the audio was paused and resumed immediately on
        // the first frame (the behavior before that). Both decoders
        // pre-roll in parallel during the pause; when they resume
        // together their clocks are aligned from the start.
        if (shouldPlay) updatedOnRequestPauseMain()
        Timber
            .tag(VideoPlaybackLogTag)
            .d("Video for $videoId loading — audio paused for 1s settling")
    }

    fun releaseAudioHold() {
        if (!awaitingVideoReady) return
        awaitingVideoReady = false
        // DON'T clear `resumeAudioAfterVideoReady` here —
        // `onRenderedFirstFrame` captures it BEFORE calling this
        // function so it can decide whether to schedule an audio
        // resume. Clearing it now would race with that capture. It
        // gets cleared after the scheduled resume fires (or when the
        // video id changes).
        //
        // No audio resume here — let the `pendingResumeAtMs`
        // LaunchedEffect resume it after the 1-second settling delay
        // so both audio and video start together.
        Timber
            .tag(VideoPlaybackLogTag)
            .d("Video ready — clearing hold flag (resume scheduled)")
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
        // Reset the drift-correction state for the new video. The speed
        // factor returns to 1.0 (no correction) and the settling timer
        // is cleared so the drift poller can run as soon as the video's
        // first frame renders (the initial-load snap in
        // onRenderedFirstFrame will set lastSeekAtMs to start a fresh
        // settling window if it actually seeks).
        state.currentSpeedCorrectionFactor = 1.0f
        state.lastSeekAtMs = 0L
        // Clear the surface re-anchor timestamp — the new video's own
        // first-frame flow handles its initial sync, so a stale timestamp
        // from the previous video must not suppress it.
        state.lastSurfaceReanchorAtMs = 0L
        // Cancel any pending resume from the previous video. Setting
        // `pendingResumeAtMs = 0L` causes the LaunchedEffect keyed on
        // it to re-launch and immediately return (the early-return
        // guard), so no stale resume fires for the new video. The new
        // video will schedule its own resume when its first frame
        // renders.
        state.pendingResumeAtMs = 0L
        state.pendingResumeMainAudio = false
        state.pendingResumeVideo = false
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
            // Start a settling window so the drift poller doesn't
            // immediately re-seek while the new media item buffers. This
            // covers BOTH the initial-load and quality-change paths.
            state.lastSeekAtMs = SystemClock.elapsedRealtime()
        }
        exoPlayer.playWhenReady = shouldPlay && !awaitingVideoReady
    }

    // ── Play/pause follower ──
    LaunchedEffect(isPlaying, awaitingVideoReady, state.isChangingQuality) {
        if (state.hasPlaybackFailed) {
            exoPlayer.pause()
        } else if (awaitingVideoReady) {
            // The video isn't ready yet — keep the video paused. The
            // main audio is ALSO paused (see [beginAudioHold], which
            // calls `updatedOnRequestPauseMain()` when the load begins).
            // Both stay paused until the video's first frame renders,
            // at which point `onRenderedFirstFrame` schedules a
            // delayed resume via `state.pendingResumeAtMs` — both
            // audio and video resume together 1 second later. This is
            // the user's explicit "pause both for a second, then
            // resume" request and eliminates the audio-first /
            // video-first desync that occurred under previous
            // behaviors.
            exoPlayer.pause()
        } else if (state.isChangingQuality) {
            // A quality or caption change is in progress — keep BOTH
            // paused until the first frame of the new media item
            // renders. This ensures neither audio nor video resumes on
            // its own while the other is still loading. The actual
            // resume is scheduled by `onRenderedFirstFrame` (see the
            // `pendingResumeAtMs` LaunchedEffect).
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
            // Capture whether the audio was paused and should be resumed
            // before releaseAudioHold() (which no longer clears the flag).
            val shouldResumeAudio = resumeAudioAfterVideoReady
            releaseAudioHold()
            // Cancel any pending resume — the video isn't coming, so
            // don't let a stale scheduled resume fire later.
            state.pendingResumeAtMs = 0L
            state.pendingResumeMainAudio = false
            state.pendingResumeVideo = false
            resumeAudioAfterVideoReady = false
            // Resume the main audio immediately so the user isn't stuck
            // in silence — playback falls back to audio-only with the
            // artwork.
            if (shouldResumeAudio) updatedOnRequestResumeMain()
        }
    }

    // ── Playback speed follower (user preference × drift-correction factor) ──
    //
    // The video ExoPlayer's effective speed is the product of:
    //   - the user's preferred playback speed ([VideoPlaybackSpeedKey]),
    //     which the audio-side follower in FullscreenVideoOverlay also
    //     applies to the main MusicService ExoPlayer so both stay at the
    //     same nominal speed; AND
    //   - [VideoArtworkState.currentSpeedCorrectionFactor], a proportional
    //     correction factor (0.80–1.20) updated by the drift poller below
    //     to gently bring the video into sync with the audio clock WITHOUT
    //     seeking. This is the MPV / VLC approach to A/V sync.
    //
    // When drift is within [VideoSyncIgnoreToleranceMs] the factor is 1.0
    // and the video plays at exactly the user's preferred speed. When the
    // drift poller detects desync it nudges the factor up (video behind) or
    // down (video ahead); this LaunchedEffect re-applies the resulting
    // effective speed to the ExoPlayer.
    val (videoPlaybackSpeed, _) = rememberPreference(VideoPlaybackSpeedKey, defaultValue = 1.0f)
    LaunchedEffect(videoPlaybackSpeed, exoPlayer, state.currentSpeedCorrectionFactor) {
        val safeSpeed = videoPlaybackSpeed.coerceIn(0.25f, 2f)
        val effectiveSpeed =
            (safeSpeed * state.currentSpeedCorrectionFactor).coerceIn(0.1f, 4f)
        val current = exoPlayer.playbackParameters.speed
        if (kotlin.math.abs(current - effectiveSpeed) > 0.001f) {
            exoPlayer.playbackParameters = PlaybackParameters(effectiveSpeed)
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
        // Reset the speed-correction factor — the seek will bring the video
        // back into sync, so any in-flight speed nudge is no longer needed.
        // The drift poller is also suppressed for [VideoSeekSettlingTimeMs]
        // via [lastSeekAtMs] so it won't fight the seek during re-buffer.
        state.currentSpeedCorrectionFactor = 1.0f
        state.lastSeekAtMs = SystemClock.elapsedRealtime()
        if (wasPlaying) updatedOnRequestPauseMain()
        exoPlayer.pause()
        exoPlayer.seekTo(position)
        state.bufferingStartedAtMs = SystemClock.elapsedRealtime()
    }

    // ── Delayed resume after the video's first frame renders ──
    //
    // The user's explicit request: when a music video loads, pause BOTH
    // audio and video for ~1 second, then resume them together.
    //
    // `onRenderedFirstFrame` (above) sets `state.pendingResumeAtMs` to
    // `now + [VideoLoadResumeDelayMs]` and stores the resume flags in
    // `state.pendingResumeMainAudio` / `state.pendingResumeVideo`. This
    // LaunchedEffect watches `pendingResumeAtMs` and fires the resume
    // when the scheduled time arrives.
    //
    // Using a LaunchedEffect (vs. `Handler.postDelayed`) gives us
    // automatic cancellation: if a NEW resume is scheduled before this
    // one fires (e.g. the user changes quality while a load is
    // settling), the LaunchedEffect re-launches on the key change and
    // the old `delay` is cancelled. If the composable leaves the
    // tree (user navigates away), the effect is also cancelled — no
    // stale resume fires.
    //
    // We also re-check failure / error conditions after the delay
    // elapses, in case the video failed during the 1-second window.
    LaunchedEffect(state.pendingResumeAtMs) {
        if (state.pendingResumeAtMs == 0L) return@LaunchedEffect
        val resumeMainAudio = state.pendingResumeMainAudio
        val resumeVideo = state.pendingResumeVideo
        val now = SystemClock.elapsedRealtime()
        val delayMs = (state.pendingResumeAtMs - now).coerceAtLeast(0L)
        delay(delayMs)
        // Re-check: if playback failed or the player errored during the
        // delay window, don't resume. The user will see the error UI.
        if (state.hasPlaybackFailed || exoPlayer.playerError != null) {
            Timber
                .tag(VideoPlaybackLogTag)
                .w("Pending resume cancelled — playback failed during delay window")
            state.pendingResumeAtMs = 0L
            state.pendingResumeMainAudio = false
            state.pendingResumeVideo = false
            return@LaunchedEffect
        }
        Timber
            .tag(VideoPlaybackLogTag)
            .d("Firing delayed resume (audio=$resumeMainAudio, video=$resumeVideo)")
        if (resumeMainAudio) {
            updatedOnRequestResumeMain()
        }
        if (resumeVideo) {
            exoPlayer.playWhenReady = true
            exoPlayer.play()
        }
        // Clear the pending state so the effect doesn't re-fire on
        // recomposition.
        state.pendingResumeAtMs = 0L
        state.pendingResumeMainAudio = false
        state.pendingResumeVideo = false
    }

    // ── Periodic position sync + stuck-buffering recovery ──
    LaunchedEffect(state.streamUrl, exoPlayer) {
        if (state.streamUrl == null) return@LaunchedEffect
        // Positions from the previous poll cycle, used by the frozen-renderer
        // detector below to tell whether the video advanced at all while the
        // audio did. Declared inside the effect so they reset whenever the
        // video (streamUrl) changes.
        var prevVideoPos = -1L
        var prevAudioPos = -1L
        var frozenCycles = 0
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

            // ── Drift detection — four tiers ──
            //
            // 0. SETTLING (just seeked): suppress all drift checks for
            //    [VideoSeekSettlingTimeMs] after any seek (soft seek,
            //    manual resync, or initial-load snap). During re-buffer
            //    the video's currentPosition is stale and would
            //    immediately re-trigger a seek — the infinite-loop bug
            //    that previously plagued this path.
            //
            // 1. IGNORE (|drift| <= [VideoSyncIgnoreToleranceMs]):
            //    No correction. 60ms is imperceptible (ITU-R BT.1359-1).
            //    Reset the speed-correction factor to 1.0 so the video
            //    returns to the user's preferred speed.
            //
            // 2. SPEED-CORRECT
            //    ([VideoSyncIgnoreToleranceMs] < |drift| <=
            //    [VideoSoftSeekDriftThresholdMs]):
            //    Proportional speed adjustment — up to ±20% (capped) on
            //    top of the user's preferred speed. Video behind audio →
            //    speed up; video ahead → slow down. No seek, no
            //    re-buffer. Normalized over [VideoSoftSeekDriftThresholdMs]
            //    (2000ms), so a moderate drift converges within a few
            //    seconds.
            //
            // 3. RE-ANCHOR / SOFT SEEK
            //    ([VideoSoftSeekDriftThresholdMs] < |drift| <=
            //    [VideoHardResyncThresholdMs]):
            //    Seek the video to the audio's current position WITHOUT
            //    pausing the main audio. Reserved for GENUINE large drift
            //    (a long decode stall), where the brief re-buffer stall a
            //    seek causes is preferable to staying out of sync. The
            //    threshold is deliberately high (2000ms) so it never fires
            //    during normal decoder warm-up — a low threshold here was
            //    the regression that made first-play "lag for the initial
            //    seconds". Stamps [lastSeekAtMs] (SETTLING) and
            //    [lastSurfaceReanchorAtMs] (so onRenderedFirstFrame's
            //    kickRenderer call doesn't double-restart).
            //
            // 4. HARD RESYNC (|drift| > [VideoHardResyncThresholdMs]):
            //    Coordinated pause-load-resume via [requestAutoResync].
            //    Rate-limited by [VideoHardResyncCooldownMs] and latches
            //    [VideoArtworkState.autoResyncDisabled] if two fire
            //    within the cooldown. If the request is dropped (cooldown
            //    exhausted), we STILL fall back to a plain re-anchor seek
            //    rather than giving up — the video must never be allowed
            //    to stay desynced with no self-healing path.
            //
            // The "video is laggy / frozen, only pause/resume fixes it"
            // complaint is NOT a position error — it's a stale/frozen
            // presentation, detected separately below (frozen-renderer
            // detector) and fixed with a no-stall pause/resume micro-cycle
            // ([VideoArtworkState.kickRenderer]) rather than a seek.
            //
            // For explicit seekbar seeks, the user-initiated
            // [VideoArtworkState.requestResync] bypasses the cooldown.
            if (state.isChangingQuality) continue
            if (state.isResyncing) continue
            if (!state.isVideoReady) continue
            if (awaitingVideoReady) continue

            // Tier 0: settling — suppress drift checks for 2s after any
            // seek so the video has time to re-buffer fully before we
            // trust its currentPosition again.
            val now = SystemClock.elapsedRealtime()
            if (state.lastSeekAtMs > 0L && now - state.lastSeekAtMs < VideoSeekSettlingTimeMs) {
                // Also clamp the correction factor to 1.0 during settling
                // so we don't fight the seek with a stale speed nudge.
                if (state.currentSpeedCorrectionFactor != 1.0f) {
                    state.currentSpeedCorrectionFactor = 1.0f
                }
                continue
            }

            // The video must be actively rendering. While it is BUFFERING
            // (or ENDED/IDLE) its currentPosition is stale — it has frozen
            // at the last rendered frame while the audio kept advancing, so
            // any drift computed now is meaningless and would either trigger
            // a redundant seek into still-loading data or a runaway
            // correction. The stuck-buffering recovery above handles
            // genuinely stuck decoders; once the video returns to READY the
            // tiers below snap it back into sync (drift has meanwhile grown
            // past the re-anchor threshold, which is what we want).
            if (exoPlayer.playbackState != Player.STATE_READY) {
                if (state.currentSpeedCorrectionFactor != 1.0f) {
                    state.currentSpeedCorrectionFactor = 1.0f
                }
                continue
            }

            val mainPos = currentPosition()
            if (mainPos <= 0) continue
            val videoPos = exoPlayer.currentPosition
            // SIGNED drift: positive = video AHEAD of audio, negative =
            // video BEHIND audio. The sign drives the direction of the
            // speed correction (tier 2).
            val signedDrift = videoPos - mainPos
            val absDrift = kotlin.math.abs(signedDrift)

            // ── Frozen-renderer detection ──
            //
            // A video renderer can get STUCK while still reporting
            // STATE_READY: it stops presenting frames to the surface (the
            // displayed frame freezes) and its own clock stops advancing.
            // The audio clock keeps going, so the user sees "the video is
            // laggy/frozen" and their only fix is to pause and resume.
            //
            // This is NOT a position drift (which the tiers above seek- or
            // speed-correct); it's a presentation stall. We detect it by
            // comparing the video's position across poll cycles: if the
            // audio advanced a full poll interval while the video barely
            // moved at all, sustained over [VideoFrozenRendererCycles]
            // consecutive cycles (~750ms), the renderer is frozen. The fix
            // is a video-only pause/resume micro-cycle
            // ([VideoArtworkState.kickRenderer]) — the same action as the
            // user's manual pause/resume — which restarts the presentation
            // clock without a re-buffer stall. The micro-cycle is
            // rate-limited inside kickRenderer, so a renderer that
            // immediately re-sticks can't busy-loop.
            //
            // After the restart we re-arm the detector (prevVideoPos = -1)
            // so it can't fire again on the same frozen stretch.
            if (exoPlayer.playWhenReady && prevVideoPos >= 0L) {
                val audioAdvanced = mainPos - prevAudioPos
                val videoAdvanced = videoPos - prevVideoPos
                if (audioAdvanced >= VideoSyncPollIntervalMs && videoAdvanced <= VideoFrozenRendererMaxAdvanceMs) {
                    frozenCycles++
                    if (frozenCycles >= VideoFrozenRendererCycles) {
                        Timber
                            .tag(VideoPlaybackLogTag)
                            .w(
                                "Video renderer frozen: position stuck at ${videoPos}ms while " +
                                    "audio advanced to ${mainPos}ms (${frozenCycles} cycles) — " +
                                    "restarting renderer",
                            )
                        frozenCycles = 0
                        prevVideoPos = -1L
                        state.kickRenderer(now)
                    }
                } else {
                    frozenCycles = 0
                }
            } else if (!exoPlayer.playWhenReady) {
                // Video paused — a paused position naturally doesn't advance.
                // Reset the freeze counter so a pause doesn't look like a
                // frozen renderer.
                frozenCycles = 0
            }
            prevVideoPos = videoPos
            prevAudioPos = mainPos

            if (absDrift > VideoHardResyncThresholdMs) {
                // Tier 4: hard resync.
                Timber
                    .tag(VideoPlaybackLogTag)
                    .w("Hard resync: drift=${signedDrift}ms (main=$mainPos, video=$videoPos, playing=$shouldPlay)")
                state.currentSpeedCorrectionFactor = 1.0f
                val accepted = state.requestAutoResync(mainPos, shouldPlay)
                if (!accepted) {
                    // Cooldown/latch hit — never leave the video desynced
                    // with no automatic path. Fall back to a plain re-anchor
                    // seek (no audio pause) so it still self-heals.
                    exoPlayer.seekTo(mainPos)
                    state.lastSeekAtMs = now
                    state.lastSurfaceReanchorAtMs = now
                }
            } else if (absDrift > VideoSoftSeekDriftThresholdMs) {
                // Tier 3: re-anchor / soft seek. Triggered for both playing
                // AND paused states — when paused, a large drift means the
                // user seeked the audio while the video was idle, so we need
                // to snap the video to the new audio position.
                Timber
                    .tag(VideoPlaybackLogTag)
                    .d("Re-anchor: drift=${signedDrift}ms (main=$mainPos, video=$videoPos)")
                state.currentSpeedCorrectionFactor = 1.0f
                exoPlayer.seekTo(mainPos)
                state.lastSeekAtMs = now
                // Stamp the re-anchor so the onRenderedFirstFrame triggered by
                // this seek doesn't perform a redundant (and self-perpetuating)
                // forced re-anchor on top of it.
                state.lastSurfaceReanchorAtMs = now
            } else if (absDrift > VideoSyncIgnoreToleranceMs) {
                // Tier 2: proportional speed correction.
                //
                // normalizedDrift ∈ [-1, 1]: +1 = video ahead by
                // [VideoSoftSeekDriftThresholdMs]; -1 = video behind by
                // the same. We cap at ±1 so a borderline drift just
                // before the re-anchor threshold still applies the max
                // correction (no runaway).
                val normalizedDrift =
                    (signedDrift.toFloat() / VideoSoftSeekDriftThresholdMs.toFloat())
                        .coerceIn(-1f, 1f)
                // If drift > 0 (video ahead), we want factor < 1 (slow
                // down) → subtract. If drift < 0 (video behind), we
                // want factor > 1 (speed up) → the subtraction of a
                // negative adds. Hence the single formula:
                //   factor = 1 - (normalizedDrift * maxFactor)
                val targetFactor =
                    1.0f - (normalizedDrift * VideoSyncSpeedCorrectionFactorMax)
                if (kotlin.math.abs(state.currentSpeedCorrectionFactor - targetFactor) > 0.005f) {
                    Timber
                        .tag(VideoPlaybackLogTag)
                        .d("Speed-correct: drift=${signedDrift}ms factor=$targetFactor (main=$mainPos, video=$videoPos)")
                    state.currentSpeedCorrectionFactor = targetFactor
                }
            } else {
                // Tier 1: within ignore tolerance. Reset the factor so
                // the video returns to the user's preferred speed once
                // drift is corrected.
                if (state.currentSpeedCorrectionFactor != 1.0f) {
                    state.currentSpeedCorrectionFactor = 1.0f
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
                    // `wasAlreadyReady` distinguishes a re-render on an
                    // already-playing player (surface re-attached during a
                    // fullscreen toggle / orientation change / window resize,
                    // or a seek re-rendered a new frame) from the first frame
                    // of a fresh load / quality change / resync. It must be
                    // captured BEFORE `isVideoReady` is set true below.
                    val wasAlreadyReady = state.isVideoReady
                    state.isVideoReady = true
                    // Capture `resumeAudioAfterVideoReady` BEFORE
                    // releaseAudioHold() — releaseAudioHold() clears
                    // `awaitingVideoReady` (which lets the play/pause
                    // follower stop force-pausing the video) but no
                    // longer clears `resumeAudioAfterVideoReady`, so
                    // we can read it here to decide whether to
                    // schedule an audio resume.
                    val shouldResumeAudioAfterHold = resumeAudioAfterVideoReady
                    releaseAudioHold()
                    val wasChangingQuality = state.isChangingQuality
                    state.isChangingQuality = false
                    val wasResync = state.isResyncing
                    state.isResyncing = false
                    val wasPlayingBeforeResyncLocal = state.wasPlayingBeforeResync
                    state.wasPlayingBeforeResync = false
                    state.bufferingStartedAtMs = 0L

                    // ── Snap video to the audio's CURRENT position ──
                    //
                    // During the initial load the audio was paused (see
                    // [beginAudioHold]), so its position has been frozen
                    // while the video buffered. The video was seeked to
                    // the audio's position when the URL was loaded; both
                    // should still be at that position. We re-snap to
                    // the audio's position only if drift exceeds the
                    // tight initial-sync tolerance, in case the audio
                    // decoder advanced a few ms during its pre-roll.
                    //
                    // We use a tight 200ms tolerance (vs. the 60ms
                    // ignore tolerance used by the continuous drift
                    // poller) because the initial sync must be
                    // near-exact — anything wider lets the video start
                    // audibly behind the audio.
                    //
                    // We skip this snap during a manual (seekbar) resync
                    // because the seekTo(position) in the pendingResync
                    // consumer already placed the video exactly where
                    // the user wants it.
                    val now = SystemClock.elapsedRealtime()
                    if (!wasResync) {
                        val mainPos = currentPosition()
                        if (mainPos > 0) {
                            if (wasAlreadyReady) {
                                // ── Surface re-attach: restart the renderer ──
                                //
                                // The video was already rendering and its
                                // surface got re-created (fullscreen toggle,
                                // orientation change, window resize). A fresh
                                // TextureView can hold a STALE frame while the
                                // video clock keeps advancing — the video looks
                                // frozen/laggy while the drift poller sees no
                                // drift (it compares clocks, not displayed
                                // frames). This is the "entering/exiting
                                // fullscreen makes the video laggy, only
                                // pause/resume fixes it" bug.
                                //
                                // We fix it with a video-only pause/resume
                                // micro-cycle ([state.kickRenderer]) — the SAME
                                // action as the user's manual pause/resume, but
                                // without touching the audio and without a
                                // re-buffer stall. A seekTo here would re-buffer
                                // the video for 1–2s on every toggle (that was
                                // the regression: fullscreen ALWAYS lagged).
                                //
                                // The pause/resume itself can produce another
                                // onRenderedFirstFrame; the min-interval guard
                                // inside kickRenderer (SurfaceReanchorMinIntervalMs)
                                // breaks that self-triggering loop.
                                if (state.kickRenderer(now)) {
                                    Timber
                                        .tag(VideoPlaybackLogTag)
                                        .d("Surface re-attached while playing — restarted video renderer")
                                }
                            } else {
                                val videoPos = exoPlayer.currentPosition
                                val drift = kotlin.math.abs(videoPos - mainPos)
                                if (drift > VideoInitialSyncToleranceMs) {
                                    exoPlayer.seekTo(mainPos)
                                    // Start the settling window so the drift
                                    // poller doesn't immediately re-seek while
                                    // the video re-buffers from this snap.
                                    state.lastSeekAtMs = now
                                }
                            }
                        }
                    }

                    // ── Schedule the resume after a 1-second settling delay ──
                    //
                    // The user's explicit request: when a music video
                    // loads, pause BOTH audio and video for ~1 second,
                    // then resume them together. We DON'T resume here
                    // on the first frame — instead we set
                    // `state.pendingResumeAtMs` to `now + VideoLoadResumeDelayMs`,
                    // and a LaunchedEffect in
                    // `rememberVideoArtworkState` watches that field
                    // and fires the resume when the time arrives.
                    //
                    // The 1-second pause gives both the audio decoder
                    // (which was idle while paused) and the video
                    // decoder (which just finished buffering) time to
                    // fully pre-roll in parallel BEFORE either starts
                    // advancing its clock. Both then start together
                    // from a known-aligned position, eliminating the
                    // "audio starts first, video catches up" desync
                    // that occurred when audio resumed immediately on
                    // the first frame.
                    //
                    // `shouldPlay` (which tracks the main player's
                    // state) is false during quality-change and resync
                    // flows because the main audio was paused; we must
                    // consult `wasPlayingBeforeQualityChange` /
                    // `wasPlayingBeforeResyncLocal` /
                    // `shouldResumeAudioAfterHold` to decide whether
                    // to resume.
                    val effectiveShouldPlay =
                        shouldPlay ||
                            (wasResync && wasPlayingBeforeResyncLocal) ||
                            (wasChangingQuality && state.wasPlayingBeforeQualityChange) ||
                            shouldResumeAudioAfterHold
                    // A pure surface re-attach (video already playing) has
                    // nothing to resume — no audio was paused and the video
                    // never stopped. Skipping the delayed resume here avoids
                    // a pointless 1s "resume" log and any edge-case double
                    // play() call.
                    val nothingToResume = wasAlreadyReady && !wasChangingQuality && !wasResync
                    if (effectiveShouldPlay && !nothingToResume && !state.hasPlaybackFailed &&
                        exoPlayer.playerError == null
                    ) {
                        // Compute the resume flags. The main audio
                        // should be resumed if it was paused — which
                        // is true for all three paths now that
                        // `beginAudioHold` also pauses the audio for
                        // the initial-load path.
                        val resumeMainAudio =
                            (wasChangingQuality && state.wasPlayingBeforeQualityChange) ||
                                (wasResync && wasPlayingBeforeResyncLocal) ||
                                shouldResumeAudioAfterHold
                        state.pendingResumeMainAudio = resumeMainAudio
                        state.pendingResumeVideo = true
                        state.pendingResumeAtMs =
                            SystemClock.elapsedRealtime() + VideoLoadResumeDelayMs
                        Timber
                            .tag(VideoPlaybackLogTag)
                            .d(
                                "First frame rendered — resume scheduled in ${VideoLoadResumeDelayMs}ms " +
                                    "(audio=$resumeMainAudio, video=true)",
                            )
                    } else if (!nothingToResume) {
                        // We're not resuming (e.g. the user paused
                        // during loading, or playback failed). Clear
                        // any stale pending resume so a previously-
                        // scheduled one doesn't fire on this state
                        // change.
                        state.pendingResumeAtMs = 0L
                        state.pendingResumeMainAudio = false
                        state.pendingResumeVideo = false
                    }
                    // When `nothingToResume` (pure surface re-attach), leave
                    // any in-flight pending resume untouched — e.g. a load's
                    // first-frame already scheduled the resume and a surface
                    // re-attach happened inside the 1s window; cancelling it
                    // here would strand the main audio paused forever.
                    // `resumeAudioAfterVideoReady` has now served its
                    // purpose (captured into `shouldResumeAudioAfterHold`
                    // above). Clear it so it doesn't leak into the next
                    // load cycle.
                    resumeAudioAfterVideoReady = false
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
                            // The player has buffered enough to start
                            // playing. We DON'T resume here — we let
                            // `onRenderedFirstFrame` schedule the
                            // delayed resume (see above) so both audio
                            // and video resume together after the
                            // [VideoLoadResumeDelayMs] settling period.
                            //
                            // `onRenderedFirstFrame` typically fires
                            // within a few ms of STATE_READY (once the
                            // surface has drawn the first decoded
                            // frame). If for some reason it never fires
                            // (e.g. surface not attached), the
                            // [VideoStuckBufferingTimeoutMs] safety net
                            // in the periodic position sync poller
                            // forces a re-prepare, which will re-enter
                            // STATE_READY and eventually trigger the
                            // first-frame callback.
                            //
                            // We DO schedule a pending resume here as a
                            // fallback — if `onRenderedFirstFrame`
                            // fires later it will overwrite this
                            // schedule (the LaunchedEffect re-launches
                            // on the key change, cancelling the old
                            // delay). If `onRenderedFirstFrame` never
                            // fires, this schedule still fires the
                            // resume after 1s.
                            val effectiveShouldPlay =
                                shouldPlay ||
                                    (state.isResyncing && state.wasPlayingBeforeResync) ||
                                    (state.isChangingQuality && state.wasPlayingBeforeQualityChange) ||
                                    resumeAudioAfterVideoReady
                            if (effectiveShouldPlay && !state.hasPlaybackFailed &&
                                exoPlayer.playerError == null
                            ) {
                                val resumeMainAudio =
                                    (state.isChangingQuality && state.wasPlayingBeforeQualityChange) ||
                                        (state.isResyncing && state.wasPlayingBeforeResync) ||
                                        resumeAudioAfterVideoReady
                                state.pendingResumeMainAudio = resumeMainAudio
                                state.pendingResumeVideo = true
                                state.pendingResumeAtMs =
                                    SystemClock.elapsedRealtime() + VideoLoadResumeDelayMs
                            }
                        }
                        Player.STATE_ENDED -> {
                            state.bufferingStartedAtMs = 0L
                            val mainPos = currentPosition()
                            exoPlayer.seekTo(mainPos)
                            // The video ended (likely a brief stall at the
                            // end of a chunk); snap it back to the audio
                            // position and start a settling window so the
                            // drift poller doesn't immediately re-seek.
                            state.lastSeekAtMs = SystemClock.elapsedRealtime()
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
 *
 * When [ambientMode] is true and [thumbnailUrl] is non-blank, a slowly
 * drifting blurred copy of the song thumbnail is rendered BEHIND the video
 * surface so the letterboxed black area around a FIT video glows with the
 * artwork's dominant colors — mimicking YouTube's "ambient mode" effect.
 *
 * @param ambientMode When true, render the blurred-thumbnail backdrop behind the video.
 * @param thumbnailUrl URL of the song thumbnail to use for ambient mode. Required if ambientMode = true.
 */
@Composable
fun VideoArtworkSurface(
    state: VideoArtworkState,
    modifier: Modifier = Modifier,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
    ambientMode: Boolean = false,
    thumbnailUrl: String? = null,
) {
    val alpha by animateFloatAsState(
        targetValue = if (state.isVideoReady) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "videoAlpha",
    )

    // NOTE: A previous attempt ported Flow's PlayerView + SurfaceManager pattern
    // here (inflating PlayerView from XML and managing SurfaceHolder.Callbacks
    // explicitly with PlaceholderSurface). It compiled but produced a black
    // screen — the explicit surface wiring raced with the PlayerView's own
    // internal surface lifecycle and the codec never attached. We reverted
    // to Media3's ContentFrame composable with SURFACE_TYPE_TEXTURE_VIEW,
    // which was the working implementation. The Flow files (VideoSurfacePolicy,
    // VideoSurfaceManager, the two XML layouts) are kept in the tree for a
    // future attempt — the PlaceholderSurface codec-preservation insight is
    // sound; the wiring just needs more careful integration with the
    // VideoArtworkState's existing Player.Listener + kickRenderer paths.
    Box(modifier = modifier) {
        // ── Ambient mode background ──
        // Render a slowly drifting, blurred copy of the song thumbnail
        // behind the video surface. The black letterbox area around a FIT
        // video then glows with the artwork's colors instead of being pure
        // black — mimicking YouTube's "ambient mode" effect.
        if (ambientMode && !thumbnailUrl.isNullOrBlank()) {
            VideoAmbientBackdrop(
                thumbnailUrl = thumbnailUrl,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // Plain black background so letterbox bars look clean.
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black),
            )
        }

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
 * Soft drifting blurred artwork backdrop for ambient mode.
 *
 * On Android S+ we use a graphicsLayer translation to drift a pre-blurred
 * bitmap (the bitmap is blurred once via [ImageBlurUtils] using a CPU
 * stack-blur, then animated on the GPU). On pre-S we render the same bitmap
 * but without the drift animation (animating a CPU-blurred bitmap every
 * frame causes visible tearing on older devices).
 *
 * The bitmap is loaded via Coil with hardware-acceleration DISABLED so we
 * can copy it to an ARGB_8888 bitmap and run the CPU stack-blur. We use a
 * dedicated cache key prefix ("ambient:") so the ambient-mode bitmap isn't
 * shared with the regular thumbnail cache (different size + blur).
 */
@Composable
private fun VideoAmbientBackdrop(
    thumbnailUrl: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isPreS = Build.VERSION.SDK_INT < Build.VERSION_CODES.S

    val transition = rememberInfiniteTransition(label = "video-ambient-drift")
    val animatedDriftX by transition.animateFloat(
        initialValue = -90f,
        targetValue = 90f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 19_000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "video-ambient-x",
    )
    val animatedDriftY by transition.animateFloat(
        initialValue = -60f,
        targetValue = 60f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 27_000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "video-ambient-y",
    )
    val driftX = if (isPreS) 0f else animatedDriftX
    val driftY = if (isPreS) 0f else animatedDriftY

    val blurredBitmap by produceState<Bitmap?>(null, thumbnailUrl) {
        value =
            withContext(Dispatchers.IO) {
                runCatching {
                    val request =
                        ImageRequest
                            .Builder(context)
                            .data(thumbnailUrl)
                            .allowHardware(false)
                            .memoryCacheKey("ambient:$thumbnailUrl")
                            .diskCacheKey("ambient:$thumbnailUrl")
                            .size(Size(540, 540))
                            .build()
                    val result = context.imageLoader.execute(request)
                    if (result is SuccessResult) {
                        val bitmap = result.image.toBitmap().copy(Bitmap.Config.ARGB_8888, true)
                        val density = context.resources.displayMetrics.density
                        ImageBlurUtils.blur(bitmap, 48f * density)
                    } else {
                        null
                    }
                }.getOrNull()
            }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .clipToBounds()
                .background(Color.Black),
    ) {
        blurredBitmap?.let { bm ->
            Image(
                bitmap = bm.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            translationX = driftX,
                            translationY = driftY,
                            scaleX = 1.4f,
                            scaleY = 1.4f,
                            alpha = 0.85f,
                        ),
            )
        }
        // Darkening scrim so the ambient glow doesn't compete with the
        // video for attention — keeps the video the focal point.
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
        )
    }
}

/**
 * Pick the best video format from a [PlayerResponse], honoring a preferred
 * height.
 *
 * # Audio + video loaded SEPARATELY
 *
 * The video ExoPlayer loads a **video-only** adaptive format from
 * `streamingData.adaptiveFormats`. The MAIN MusicService ExoPlayer is the
 * sole source of audio — it plays the YouTube Music audio stream
 * independently. The video ExoPlayer's audio track is disabled (see
 * [rememberVideoArtworkState] —
 * `setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)`), so even if a
 * candidate happens to be muxed its audio is demuxed but not rendered.
 *
 * This split-stream approach was the user's explicit request — they tried
 * a muxed (single-stream) preference and reverted because (a) YouTube
 * caps muxed formats at 720p and (b) the muxed audio wasn't reliably
 * silenced in an earlier attempt. Loading audio + video separately keeps
 * the audio path identical to non-music-video playback (no muting hooks,
 * no risk of dual audio) and lets the user pick any adaptive video
 * resolution up to [MaxVideoHeightCap] (1080p).
 *
 * The picker scans BOTH `formats` and `adaptiveFormats` (YouTube
 * occasionally lists a video-only entry in `formats` on some clients),
 * filters to those at or below [MaxVideoHeightCap], and picks the highest
 * resolution at or below the user's [preferredHeight] (falling back to
 * the smallest available if the preferred height is below everything
 * YouTube offered).
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
                // Local val enables smart-casting — `height` is a public
                // API property declared in a different module (core), so
                // Kotlin cannot smart-cast `it.height` directly after the
                // null check. Using `h` works around that limitation.
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
