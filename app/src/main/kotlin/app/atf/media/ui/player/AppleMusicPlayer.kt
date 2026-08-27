/*
 * ArchiveTune (2026)
 * © ArchiveTuneFork contributors — github.com/vossgraves/ArchiveTune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * "Apple Music" player design: edge-to-edge artwork on top with a blurred continuation of the
 * artwork behind the lower controls (progressive-blur look), bold white title/artist with star and
 * "more" chips, a thin scrubber with elapsed/-remaining times, bare oversized transport glyphs, a
 * flat volume slider, and a bottom lyrics / output / queue icon row. Everything is tinted by the
 * artwork itself (no palette extraction needed — the blur provides the color).
 */

package app.atf.media.ui.player

import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.OverlayClip
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import kotlin.math.abs
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player.STATE_ENDED
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size as CoilSize
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import app.atf.media.LocalAnimationsDisabled
import app.atf.media.LocalPlayerConnection
import app.atf.media.LocalStableSystemBarsTopPadding
import app.atf.media.R
import app.atf.media.constants.AutoTranslateExcludedLanguagesKey
import app.atf.media.constants.AutoTranslateLyricsKey
import app.atf.media.constants.AutoHideLyricsPlayerControlsKey
import app.atf.media.constants.ThumbnailCornerRadiusKey
import app.atf.media.constants.TranslatorTargetLangKey
import app.atf.media.db.entities.FormatEntity
import app.atf.media.db.entities.LyricsEntity
import app.atf.media.db.entities.codecLabel
import app.atf.media.db.entities.isLossless
import app.atf.media.extensions.togglePlayPause
import app.atf.media.lyrics.LyricsUtils
import app.atf.media.models.MediaMetadata
import app.atf.media.playback.PlayerConnection
import app.atf.media.ui.component.BottomSheetPageState
import app.atf.media.ui.component.BottomSheetState
import app.atf.media.ui.component.LocalMenuState
import app.atf.media.ui.component.LyricsEnhanced
import app.atf.media.ui.component.LyricsV2
import app.atf.media.constants.LyricsMode
import app.atf.media.constants.LyricsModeKey
import app.atf.media.constants.ShowLyricsPlayerControlsKey
import app.atf.media.utils.rememberEnumPreference
import app.atf.media.ui.menu.LyricsMenu
import app.atf.media.ui.menu.PlayerMenu
import app.atf.media.ui.menu.rememberCastPlayerMenuAction
import app.atf.media.ui.utils.ShowMediaInfo
import app.atf.media.ui.utils.highRes
import app.atf.media.utils.ImageBlurUtils
import app.atf.media.utils.isLocalMediaId
import app.atf.media.utils.makeTimeString
import app.atf.media.utils.rememberLowDataModeActive
import app.atf.media.utils.rememberPreference
import app.atf.media.viewmodels.LyricsMenuViewModel

private val AppleMusicContentPadding = 28.dp
private val AppleMusicChipSize = 34.dp
private val AppleMusicTransportIconSize = 52.dp
private val AppleMusicPlayPauseIconSize = 62.dp
// Bottom action row (lyrics / cast / queue) — reduced "just a bit" from the
// previous 30dp/56dp per user request. 26dp icons in 48dp boxes are still
// comfortably above the 48dp minimum touch target while looking less bulky.
private val AppleMusicBottomIconSize = 26.dp
private val AppleMusicBottomButtonSize = 48.dp
private val AppleMusicMiniArtworkSize = 56.dp

// ─── Lyrics backdrop "moving blur" wander ────────────────────────────
// The drift itself lives in [BlurWanderDrift], shared with MovingBlurBackground
// in LyricsScreen.kt so both lyrics surfaces move identically. Offsets are in dp
// and are applied as graphicsLayer translations (see driftGraphicsLayer below);
// the walk also rotates, which is what lets a colour reach the far side of the
// screen at all.
//
// [AmLyricsBlurDriftScale] has to cover the drift plus the blur radius, measured
// to the container's furthest *corner* because the layer rotates:
//     1.2 * max(W, H) - blurRadius * scale  >=  hypot(W, H) / 2 + maxDrift
// ContentScale.Crop renders the square artwork at side max(W, H), so at 2.4x its
// inscribed circle has radius 1.2 * max(W, H) and the outer 64 * 2.4 = 154dp of
// that is softened by the blur. For a 360x800 phone:
//     1.2 * 800 - 154 = 806dp  >=  439 + 120 = 559dp.  ✔
// (320x480, the smallest screen this could ever run on, still clears it: 422 vs 408.)
private const val AmLyricsBlurDriftScale = 2.4f

// Scale of the blurred backdrop in the COVER/QUEUE states. The LYRICS state
// zooms from here to [AmLyricsBlurDriftScale] over [AmLyricsBackdropMorphMs].
private const val AmCoverBlurScale = 1.2f

// How long the backdrop takes to travel between the COVER look (no drift, 1.2x)
// and the LYRICS look (drifting, 2.4x). Also the duration of the canvas →
// still-artwork cross-dissolve, and the delay before the canvas TextureView is
// torn down, so the video surface only disappears once the still artwork that
// replaces it is fully opaque.
private const val AmLyricsBackdropMorphMs = 650

// Blur radius of the artwork backdrop. Applied INSIDE the drift/zoom transform
// (see driftGraphicsLayer), so the on-screen radius is this times the current
// scale: ~77dp in COVER, ~154dp with lyrics open. Keeping the radius itself
// constant means the RenderEffect is built once instead of being rebuilt on
// every frame of the zoom.
private val AmBackdropBlurRadius = 64.dp

private const val AppleMusicLyricsContentDeferMs = 160L

// How far above the bottom of the artwork stage the blurred canvas starts dissolving into the
// still blurred album art beneath it. See canvasSeamFade in AppleMusicPlayerContent.
private val AmCanvasSeamFadeDp = 88.dp

/**
 * A [Shape] that interpolates the corner radius based on the element's size.
 * At [smallSize], the corner radius is [smallRadius]; at [largeSize], it's
 * [largeRadius]. In between, it smoothly interpolates. This ensures
 * consistent visual corner curvature during SharedTransition morphs where
 * the element's size changes dramatically (e.g., from a large cover artwork
 * to a small mini thumbnail).
 *
 * Without this, a fixed-Dp [RoundedCornerShape] (e.g., 16dp) looks "sharp"
 * on a large element (16dp on 320dp = barely visible) but "very curved" on
 * a small element (16dp on 56dp = 28% radius). This adaptive shape
 * eliminates that discrepancy by scaling the radius with the element's
 * size, so the corners look the SAME proportionally throughout the morph —
 * no "sharp at start, curved at end" effect.
 *
 * The interpolation is clamped: below [smallSize] the radius stays at
 * [smallRadius], and above [largeSize] it stays at [largeRadius]. This
 * ensures the overlay's clip matches the source (COVER) and target (LYRICS)
 * clips at the endpoints, with NO visible snap when the overlay is applied
 * or removed.
 */
private class AdaptiveCornerShape(
    private val smallRadius: Dp,
    private val smallSize: Dp,
    private val largeRadius: Dp,
    private val largeSize: Dp,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val elementSize = minOf(size.width, size.height)
        val smallSizePx = with(density) { smallSize.toPx() }
        val largeSizePx = with(density) { largeSize.toPx() }
        val t =
            if (largeSizePx > smallSizePx) {
                ((elementSize - smallSizePx) / (largeSizePx - smallSizePx)).coerceIn(0f, 1f)
            } else {
                1f
            }
        val smallRadiusPx = with(density) { smallRadius.toPx() }
        val largeRadiusPx = with(density) { largeRadius.toPx() }
        val radius = smallRadiusPx + (largeRadiusPx - smallRadiusPx) * t
        return Outline.Rounded(
            RoundRect(
                left = 0f,
                top = 0f,
                right = size.width,
                bottom = size.height,
                cornerRadius = CornerRadius(radius, radius),
            ),
        )
    }
}

/**
 * Internal visual state of the Apple Music player. Mirrors ViviMusic's
 * `PlayerInternalState` enum — COVER shows the full-screen artwork + title
 * row, QUEUE morphs the artwork into a mini header and reveals the in-place
 * queue sheet, LYRICS morphs the same way but reveals the inline lyrics
 * composable instead of the queue list.
 */
private enum class AppleMusicPlayerState { COVER, QUEUE, LYRICS }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppleMusicPlayerContent(
    mediaMetadata: MediaMetadata,
    playbackState: Int,
    isPlaying: Boolean,
    isLoading: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    sliderPosition: Long?,
    // Deferred position provider — reads the 100ms-polled playback position
    // via a stable lambda so this composable does NOT recompose on every poll
    // tick. Only AppleMusicControlsColumn reads it, and only when it's actually
    // composed (visible). When lyrics is open and controls auto-hide after 3s,
    // no recomposition happens at all — eliminating the wasted frame budget
    // that caused the "smooth for first few seconds, then laggy" auto-scroll
    // symptom in the inline Enhanced lyrics view.
    positionProvider: () -> Long,
    duration: Long,
    playerConnection: PlayerConnection,
    navController: NavController,
    state: BottomSheetState,
    bottomSheetPageState: BottomSheetPageState,
    currentSongLiked: Boolean,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    canvasPrimaryUrl: String?,
    canvasFallbackUrl: String?,
    currentFormat: FormatEntity?,
    contentBottomPadding: Dp,
    onQueueClick: () -> Unit,
    onLyricsClick: () -> Unit,
    onSliderValueChange: (Long) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
    lyricsSyncOffset: Int = 0,
    onLyricsSyncOffsetChange: (Int) -> Unit = {},
    // ISSUE 1 FIX: report inline-lyrics visibility upward so back-stack screens
    // (playlist/album/artist) can suspend their LiquidGlass layerBackdrop +
    // CanvasArtworkPlayer GPU work during the COVER→LYRICS morph. Without this,
    // those screens keep spending GPU frame budget behind the player sheet,
    // competing with the sharedBounds morph and causing the reported "sometimes
    // lags" stutter. The standalone MikoLyricsTransition overlay already reports
    // via this same callback — we're extending it to Apple Music's INLINE lyrics.
    onLyricsVisibilityChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    landscape: Boolean = false,
) {
    // In-place queue morph state. When the user taps the queue button (or
    // swipes up on the controls area), we toggle this state instead of
    // opening the separate queue BottomSheet. The artwork + title row then
    // morph into a compact mini header while the queue list fades in below —
    // matching ViviMusic's Player_v2 ↔ Queue_v2 transition exactly.
    var queueOpen by remember { mutableStateOf(false) }
    // In-place lyrics morph state. Same animation as the queue morph — the
    // artwork shrinks into the mini header and the lyrics composable fades
    // in below. Clicking the mini header artwork restores the COVER state.
    var lyricsOpen by remember { mutableStateOf(false) }

    // Low-RAM / "reduce animations" signal -- also used to gate the karaoke
    // line-blur RenderEffect (see LyricsEnhanced/LyricsV2). Reused below to drop
    // the slide component of the auto-hide controls transition, since a slide
    // forces an extra layout pass on top of whatever the lyrics view is already
    // spending its frame budget on.
    val animationsDisabled = LocalAnimationsDisabled.current
    val showLyricsPlayerControlsState = rememberPreference(ShowLyricsPlayerControlsKey, defaultValue = true)
    val showLyricsPlayerControls by showLyricsPlayerControlsState
    val (autoHideLyricsPlayerControls, onAutoHideLyricsPlayerControlsChange) =
        rememberPreference(AutoHideLyricsPlayerControlsKey, defaultValue = true)

    // Toggling one closes the other — queue and lyrics are mutually exclusive
    // (only one morph target can be active at a time).
    val toggleQueue = {
        lyricsOpen = false
        queueOpen = !queueOpen
    }
    val toggleLyrics = {
        queueOpen = false
        lyricsOpen = !lyricsOpen
    }

    // Back handler: when the in-place queue or lyrics is open, back closes
    // it first (before the outer player-collapse BackHandler in Player.kt).
    val morphOpen = queueOpen || lyricsOpen
    androidx.activity.compose.BackHandler(enabled = morphOpen) {
        if (lyricsOpen) lyricsOpen = false
        if (queueOpen) queueOpen = false
    }

    // The morph target state: COVER (default), QUEUE, or LYRICS.
    val morphState =
        when {
            queueOpen -> AppleMusicPlayerState.QUEUE
            lyricsOpen -> AppleMusicPlayerState.LYRICS
            else -> AppleMusicPlayerState.COVER
        }

    // Clicking the mini header artwork restores the COVER state (main player).
    val restoreCover = {
        queueOpen = false
        lyricsOpen = false
    }

    // Show controls when lyrics or queue opens, then honor the shared five-second
    // auto-hide setting.
    var playerControlsExpanded by remember(mediaMetadata.id) { mutableStateOf(true) }
    var playerControlsVisibilityTick by remember(mediaMetadata.id) { mutableIntStateOf(0) }
    val autoHideDelayMs = AppleMusicLyricsControlsAutoHideDelayMs

    LaunchedEffect(lyricsOpen, autoHideLyricsPlayerControls, showLyricsPlayerControls) {
        if (lyricsOpen) {
            playerControlsExpanded = showLyricsPlayerControls
            if (showLyricsPlayerControls && autoHideLyricsPlayerControls) playerControlsVisibilityTick++
        } else {
            playerControlsExpanded = true
        }
    }
    LaunchedEffect(queueOpen, autoHideLyricsPlayerControls) {
        if (queueOpen) {
            playerControlsExpanded = true
            if (autoHideLyricsPlayerControls) playerControlsVisibilityTick++
        } else {
            playerControlsExpanded = true
        }
    }

    // ISSUE 1 FIX: propagate inline-lyrics visibility to the parent so back-stack
    // screens suspend their GPU work during the morph.
    LaunchedEffect(lyricsOpen) {
        onLyricsVisibilityChange(lyricsOpen)
    }
    DisposableEffect(Unit) {
        onDispose { onLyricsVisibilityChange(false) }
    }
    // Auto-hide: show controls for five seconds, then hide. Fires for both lyrics and queue.
    LaunchedEffect(
        lyricsOpen,
        queueOpen,
        playerControlsVisibilityTick,
        autoHideLyricsPlayerControls,
        showLyricsPlayerControls,
    ) {
        if (!shouldAutoHideAppleMusicControls(lyricsOpen, queueOpen, autoHideLyricsPlayerControls)) {
            playerControlsExpanded = if (lyricsOpen) showLyricsPlayerControls else true
            return@LaunchedEffect
        }
        if (lyricsOpen && !showLyricsPlayerControls) {
            playerControlsExpanded = false
            return@LaunchedEffect
        }
        playerControlsExpanded = true
        delay(autoHideDelayMs)
        playerControlsExpanded = false
    }

    // Deferred canvas-visible state: when lyrics opens, the canvas
    // TextureView teardown (visible = false) + ExoPlayer pause are delayed so
    // they don't compete with the COVER→LYRICS sharedBounds morph for the main
    // thread on the same frame. Without this deferral, the TextureView
    // teardown + static image composition + lyrics composable initialization
    // AND the morph animation all fire simultaneously, causing a visible
    // stutter in the thumbnail transition (issue 3).
    //
    // The delay is [AmLyricsBackdropMorphMs] — the length of the backdrop
    // cross-dissolve — so the canvas surface is only removed once the still
    // blurred artwork underneath has faded up to full opacity. Shorter delays
    // (this used to be 250ms while the still artwork appeared at 350ms) left a
    // window where the canvas was already gone and the still artwork had not
    // arrived, which is what made the backdrop visibly jump. When lyrics
    // closes, the canvas restores immediately so there's no visible gap.
    var canvasVisibleForLyrics by remember { mutableStateOf(true) }
    LaunchedEffect(lyricsOpen) {
        if (lyricsOpen) {
            // Keep canvas visible while the backdrop cross-dissolve runs.
            canvasVisibleForLyrics = true
            delay(AmLyricsBackdropMorphMs.toLong())
            canvasVisibleForLyrics = false
        } else {
            canvasVisibleForLyrics = true
        }
    }
    // True while the lyrics backdrop (zoom + drift) is on screen OR still
    // animating back out. Gates the wander frame loop so it isn't burning a
    // frame callback every 16ms while the player sits in the COVER state.
    var lyricsBackdropActive by remember { mutableStateOf(false) }
    LaunchedEffect(lyricsOpen) {
        if (lyricsOpen) {
            lyricsBackdropActive = true
        } else {
            delay(AmLyricsBackdropMorphMs.toLong())
            lyricsBackdropActive = false
        }
    }
    var lyricsContentReady by remember { mutableStateOf(false) }
    LaunchedEffect(lyricsOpen) {
        if (!lyricsOpen) {
            lyricsContentReady = false
            return@LaunchedEffect
        }
        // Composing LyricsEnhanced/LyricsV2 for the first time is the most expensive frame in the
        // whole overlay. Let the artwork morph and the canvas handoff get a head start so that
        // burst cannot land on the same frame as the shared-bounds animation.
        //
        // 350ms used to be needed because the TTML/LRC parse and buildSyncedLyrics both ran
        // synchronously inside that first composition; both now run on Dispatchers.Default, so only
        // the composition itself is left and a shorter head start is enough. Keeping it long also
        // hurt: the AnimatedVisibility fadeIn below is 400ms, so almost all of it was spent on an
        // empty box and the lyrics arrived as a pop rather than a fade. The renderers' own
        // first-focus fade now covers the remainder.
        lyricsContentReady = false
        delay(AppleMusicLyricsContentDeferMs)
        lyricsContentReady = true
    }
    val pokePlayerControlsVisibility = remember(lyricsOpen, queueOpen) {
        {
            if (lyricsOpen || queueOpen) {
                playerControlsExpanded = true
                playerControlsVisibilityTick++
            }
        }
    }

    // === Deferred position reads for the lyrics overlay ===
    // `sliderPosition` is non-null ONLY while the user is actively scrubbing
    // the seekbar. When null, LyricsEnhanced/LyricsV2 fall back to reading
    // `player.currentPosition` directly inside their own 60Hz `withFrameNanos`
    // interpolation loop — which is what gives the karaoke syllable fill its
    // smooth, continuous sweep.
    //
    // CRITICAL: we must NOT pass `position` (the 100ms-polled value from
    // Player.kt) as a fallback. If we do, the provider always returns non-null,
    // which makes LyricsEnhanced think the slider is ALWAYS active. It then
    // skips its 60Hz interpolation loop and just snaps `playbackPositionMs`
    // to the polled `position` every 100ms — making the karaoke fill visibly
    // step instead of smoothly progressing. This was the root cause of the
    // "lyrics smooth for first few seconds, then janky after ~20s" report:
    // the snapping is barely visible on the first line (short duration) but
    // becomes very noticeable once a long sustained line is active.
    //
    // The standalone LyricsScreen.kt does exactly the same thing — it passes
    // `{ sliderPosition }` (nullable), NOT `{ sliderPosition ?: position }`.
    //
    // We wrap `sliderPosition` in `rememberUpdatedState` so the stable
    // `remember`'d lambda always sees the latest value without the overlay's
    // content lambda needing to recompose on every scrub tick.
    val sliderPositionState = rememberUpdatedState(sliderPosition)
    val lyricsPosProvider = remember {
        { sliderPositionState.value }
    }

    // === Moving blur wander for the backdrop when lyrics is open ===
    // Mirrors the MovingBlurBackground from LyricsScreen: the blurred artwork
    // wanders behind the lyrics. See [BlurWanderDrift] for the path itself —
    // random waypoints joined by eased legs, so the colours always finish the
    // leg they are on, settle, and then set off again somewhere new, instead of
    // suddenly doubling back the way a closed periodic path does.
    //
    // The offsets are bounded by BlurWanderDrift.WanderRadiusDp (150dp), which
    // is what [AmLyricsBlurDriftScale] is sized against: at 2.4x the artwork
    // overhangs 0.7*W per edge (252dp on a 360dp screen), covering the 150dp of
    // drift plus the 64dp blur with margin to spare. A smaller scale would let
    // the trailing edge pull inside the parent at maximum drift, and the blur
    // would sample transparent pixels — the flickering dark band at the screen
    // edges that an earlier 1.4x scale produced.
    //
    // CRITICAL PERF: the offsets stay wrapped in FloatState and are read ONLY
    // inside Modifier.graphicsLayer { } lambdas (draw-phase deferred reads).
    // Reading them during composition — e.g.
    // `val driftX = if (lyricsOpen) wander.xDp.floatValue else 0f` — would
    // invalidate the ENTIRE AppleMusicPlayerContent composable every frame
    // (SharedTransitionLayout, AnimatedContent, ControlsColumn, and the inline
    // LyricsEnhanced all recompose at ~60fps), stealing the frame budget from
    // the karaoke syllable sweep. This was the root cause of the Apple-Music-
    // style-only lyrics jank.
    //
    // Gated on lyricsBackdropActive: the walk is driven by a frame loop, and
    // leaving it running while the player sits on the cover (which is what
    // rememberInfiniteTransition did) keeps the choreographer — and with it the
    // whole Compose frame pipeline — awake for a value nothing is reading.
    val blurWander = rememberBlurWanderDrift(active = lyricsBackdropActive)
    // Pre-compute dp→px once (graphicsLayer.translationX is in pixels). Density
    // doesn't change per-frame so this is a one-time composition-phase read.
    val density = LocalDensity.current
    val driftDpToPx = with(density) { 1.dp.toPx() }
    val lyricsSwipeDismissThresholdPx = with(density) { 96.dp.toPx() }

    // === COVER ↔ LYRICS backdrop hand-off ===
    // The backdrop used to be built from two different nodes: 72dp blur at 1.2x
    // for COVER/QUEUE, and a separate "64dp blur at 2.4x with drift" node that
    // replaced it the instant `lyricsContentReady` flipped, 350ms after the
    // morph began. Swapping nodes is what made the background change abruptly:
    //
    //  • the effective on-screen blur more than doubled in one frame (64dp
    //    applied inside a 2.4x zoom reads as ~154dp, against 72dp at 1.2x),
    //  • the artwork jumped from 1.2x to 2.4x — a 2x zoom with no in-between,
    //  • and the drift snapped straight to wherever the always-running wander
    //    had got to, i.e. up to 150dp of instant translation,
    //  • while on canvas songs the video surface had already been pulled 100ms
    //    earlier, so the un-drifted fallback flashed in between.
    //
    // Now there is ONE backdrop node whose graphicsLayer interpolates between
    // the two looks off this progress value: scale 1.2 → 2.4, drift amplitude
    // 0 → 1, and (for canvas songs) the video's opacity 1 → 0 underneath it.
    // Both endpoints look exactly as they did before; only the trip between
    // them changed, from one frame to [AmLyricsBackdropMorphMs].
    val lyricsBackdropProgress =
        animateFloatAsState(
            targetValue = if (lyricsOpen) 1f else 0f,
            animationSpec =
                tween(
                    durationMillis = AmLyricsBackdropMorphMs,
                    easing = FastOutSlowInEasing,
                ),
            label = "am-lyrics-backdrop-progress",
        )

    // Hoist the thumbnail corner radius preference so it can be used both
    // for the COVER state's artwork clip AND for the sharedBounds overlay
    // clip during morph transitions. Previously this was read INSIDE
    // AppleMusicSharpArtwork's immersiveExtendedCard branch, which meant
    // the sharedBounds modifier (in this parent composable) could NOT
    // access it — so the SharedTransition overlay used the default
    // RectangleShape clip, causing the artwork to flash sharp corners
    // for the duration of the spring bounds animation (1-2s) after
    // expanding from the mini header. See clipInOverlayDuringTransition
    // on the sharedBounds modifiers below.
    val (thumbnailCornerRadius, _) = rememberPreference(
        ThumbnailCornerRadiusKey,
        defaultValue = 16f,
    )
    val artworkCornerRadiusDp = thumbnailCornerRadius.coerceAtMost(32f).dp

    val baseArtworkUrl = mediaMetadata.thumbnailUrl?.highRes()
    val thumbnailSwapState =
        rememberThumbnailSwapState(
            videoId = mediaMetadata.id,
            ytmUrl = baseArtworkUrl,
            lowDataMode = rememberLowDataModeActive(),
            isMusicVideo = mediaMetadata.isMusicVideo,
        )
    val artworkUrl = thumbnailSwapState.displayUrl
    val artworkRequest = rememberOfflineArtworkImageRequest(artworkUrl)
    val titleActions = rememberPlayerTitleActions(mediaMetadata, navController, state)
    val menuState = LocalMenuState.current
    val context = LocalContext.current

    // Current lyrics for the LyricsMenu (shown when lyrics is open and the
    // user taps the overflow "more" button).
    val currentLyrics by playerConnection.currentLyrics.collectAsStateWithLifecycle(initialValue = null)

    // ─── Automatic AI translation ───────────────────────────────────────
    // Mirrors the same LaunchedEffect in LyricsScreen.kt. The Apple Music
    // player uses an inline LyricsV2/LyricsEnhanced view (not LyricsScreen),
    // so it needs its own trigger; these two effects are the whole of
    // auto-translation. (There is no background path: an `AutoLyricsTranslator`
    // used to sit in `ai/` looking like one, injected nowhere and called by
    // nothing — it has been removed, because the only place that read the
    // exclusion setting correctly being dead code is what made that setting
    // appear implemented while doing nothing.)
    val (autoTranslateLyrics) = rememberPreference(AutoTranslateLyricsKey, defaultValue = false)
    val (translatorTargetLang) = rememberPreference(TranslatorTargetLangKey, defaultValue = "")
    // "Don't auto translate these languages". Read here and passed explicitly below — leaving it to
    // shouldAutoTranslate's old default was exactly how this setting came to do nothing.
    val (autoTranslateExcludedLanguages) =
        rememberPreference(AutoTranslateExcludedLanguagesKey, defaultValue = emptySet())
    val lyricsMenuViewModel: LyricsMenuViewModel = hiltViewModel()
    // Observe the set of media IDs the user has dismissed translation for.
    // When a user clicks "Undo Translation", the mediaId is added to this set;
    // auto-translate is suppressed for dismissed songs until the user manually
    // triggers translation again (which clears the dismissal in the ViewModel).
    val translationDismissedMediaIds by lyricsMenuViewModel.translationDismissedMediaIds
        .collectAsStateWithLifecycle()
    LaunchedEffect(
        mediaMetadata.id,
        currentLyrics?.lyrics,
        currentLyrics?.source,
        autoTranslateLyrics,
        translatorTargetLang,
        // In the key list so unticking a language re-evaluates the current track instead of waiting
        // for the next one.
        autoTranslateExcludedLanguages,
        translationDismissedMediaIds,
    ) {
        if (!autoTranslateLyrics) return@LaunchedEffect
        val snapshot = currentLyrics ?: return@LaunchedEffect
        val text = snapshot.lyrics ?: return@LaunchedEffect
        if (text.isBlank() || text == LyricsEntity.LYRICS_NOT_FOUND) return@LaunchedEffect
        // Skip if these lyrics were already AI-translated AND actually contain
        // translation content. The `hasTranslation` guard allows retrying when a
        // previous attempt no-op'd (AI returned the same text — a common failure
        // mode for CJK lyrics that were previously mangled by the span-joining
        // bug in AiLyricsDocument.readTtmlLineText). Without this, those songs
        // would be blocked from retrying forever.
        if (snapshot.source == LyricsEntity.Source.AI_TRANSLATION.value &&
            LyricsUtils.hasTranslation(text)
        ) return@LaunchedEffect

        // Skip auto-translate if the user has dismissed translation for this
        // song. The user clicked "Undo Translation" — they explicitly do not
        // want the translation back. Auto-translate will resume only after the
        // user manually triggers translation (which clears the dismissal).
        if (mediaMetadata.id in translationDismissedMediaIds) return@LaunchedEffect

        if (!LyricsUtils.shouldAutoTranslate(
                lyrics = text,
                targetLanguage = translatorTargetLang,
                excludedLanguageCodes = autoTranslateExcludedLanguages,
            )
        ) {
            return@LaunchedEffect
        }
        lyricsMenuViewModel.translateLyricsWithAi(
            mediaMetadata = mediaMetadata,
            lyrics = text,
            targetLanguage = translatorTargetLang,
        )
    }

    val onPlayPauseClick = {
        if (playbackState == STATE_ENDED) {
            playerConnection.player.seekTo(0, 0)
            playerConnection.player.playWhenReady = true
        } else {
            playerConnection.player.togglePlayPause()
        }
    }
    val onMoreClick = {
        if (lyricsOpen) {
            // When lyrics is open, the overflow menu shows lyric actions. Control visibility is governed
            // by the shared Lyrics settings, so the Apple Music style does not duplicate those toggles.
            menuState.show {
                LyricsMenu(
                    lyricsProvider = { currentLyrics },
                    mediaMetadataProvider = { mediaMetadata },
                    lyricsSyncOffset = lyricsSyncOffset,
                    onLyricsSyncOffsetChange = onLyricsSyncOffsetChange,
                    showPlayerControlsState = showLyricsPlayerControlsState,
                    onShowPlayerControlsChange = { showControls ->
                        showLyricsPlayerControlsState.value = showControls
                        playerControlsExpanded = showControls
                    },
                    onAutoHidePlayerControlsChange = { enabled ->
                        onAutoHideLyricsPlayerControlsChange(enabled)
                        playerControlsExpanded = true
                        if (enabled) playerControlsVisibilityTick++
                    },
                    onDismiss = menuState::dismiss,
                    showControlsToggles = true,
                )
            }
        } else {
            menuState.show {
                PlayerMenu(
                    mediaMetadata = mediaMetadata,
                    navController = navController,
                    playerBottomSheetState = state,
                    onShowDetailsDialog = {
                        mediaMetadata.id.let {
                            bottomSheetPageState.show {
                                ShowMediaInfo(it)
                            }
                        }
                    },
                    onDismiss = menuState::dismiss,
                )
            }
        }
    }
    // The "AirPlay" slot opens the Cast route picker on flavors that ship Cast (gms). This also
    // renders the route-picker bottom sheet when it becomes visible. On flavors without Cast (foss)
    // rememberCastPlayerMenuAction() returns null and we fall back to the system output switcher.
    val castAction = rememberCastPlayerMenuAction()
    val onOutputClick: () -> Unit = castAction?.onClick ?: {
        // Cast-less flavors (foss): open the system media-output switcher panel.
        runCatching {
            context.startActivity(Intent("android.settings.panel.action.MEDIA_OUTPUT"))
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val sharpArtworkHeight = if (landscape) maxHeight else maxHeight * 0.55f
        // The FULL player height — used as the artwork sizing reference so the
        // artwork stays a constant size whether the system navigation bar is
        // visible or hidden. In portrait the morph area is weight(1f), so its
        // height shrinks when the nav bar inset is consumed by the controls'
        // `navigationBarsPadding()`. Using the outer maxHeight here (which is
        // fillMaxSize — the entire player area) decouples artwork sizing from
        // that inset. See `fullPlayerHeight` parameter in AppleMusicSharpArtwork.
        val fullPlayerHeightForArtwork: Dp? = if (landscape) null else maxHeight

        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .background(Color.Black),
        )

        // Height of the artwork stage (the weight(1f) morph area). Captured here so the backdrop —
        // which is composed before the Column and so cannot see its layout — knows where the
        // bottom controls begin. Read only from a draw-phase lambda, so a change costs a redraw
        // rather than a recomposition.
        var morphAreaHeightPx by remember { mutableIntStateOf(0) }

        val videoShowing =
            LocalVideoArtworkState.current != null &&
                mediaMetadata.isMusicVideo &&
                !mediaMetadata.id.isLocalMediaId()
        val isPreS = Build.VERSION.SDK_INT < Build.VERSION_CODES.S
        val canvasActive =
            !canvasPrimaryUrl.isNullOrBlank() || !canvasFallbackUrl.isNullOrBlank()
        // When a Spotify Canvas (or any canvas artwork) is playing, render the canvas video
        // itself as the blurred backdrop — "Apple Music player style". On Android 12+,
        // Modifier.blur works on the TextureView surface that CanvasArtworkPlayer uses, so
        // the backdrop mirrors the canvas video in real time. Pre-Android-12 falls back to
        // the album-art blur (RenderEffect is unavailable, so blurring a video surface
        // efficiently isn't possible).
        val useCanvasBackdrop = canvasActive && !videoShowing && !isPreS
        val context = LocalContext.current
        val imageLoader = context.imageLoader
        val preBlurredBitmap by produceState<Bitmap?>(null, artworkUrl) {
            // Pre-S has no RenderEffect, so Modifier.blur is a no-op there and the
            // blur has to be baked into a bitmap off the main thread instead.
            // Gated on useCanvasBackdrop rather than canvasActive: pre-S never
            // uses the canvas as its backdrop (blurring a video surface without
            // RenderEffect isn't possible), so a canvas song still needs the
            // blurred artwork — without this it fell through to an unblurred
            // AsyncImage and showed a sharp backdrop.
            if (!isPreS || artworkUrl.isNullOrBlank() || videoShowing || useCanvasBackdrop) {
                value = null
                return@produceState
            }
            value = withContext(Dispatchers.IO) {
                try {
                    val request = ImageRequest.Builder(context)
                        .data(artworkUrl)
                        .allowHardware(false)
                        .memoryCacheKey("$artworkUrl#amplayer")
                        .diskCacheKey("$artworkUrl#amplayer")
                        .size(CoilSize(720, 720))
                        .build()
                    val result = imageLoader.execute(request)
                    if (result is SuccessResult) {
                        val bitmap = result.image.toBitmap()
                            .copy(Bitmap.Config.ARGB_8888, true)
                        val density = context.resources.displayMetrics.density
                        ImageBlurUtils.blur(bitmap, 72f * density)
                    } else null
                } catch (_: Exception) {
                    null
                }
            }
        }

        if (!videoShowing) {
            // Backdrop rendering — one blurred-artwork node for every state,
            // plus (on canvas songs) the live canvas video composited over it.
            //
            // • COVER / QUEUE state — the artwork sits at [AmCoverBlurScale]
            //   with no drift. On canvas songs the canvas video covers it
            //   completely, so what the user sees is the blurred canvas; the
            //   artwork underneath is the fallback for while the canvas video
            //   is still buffering.
            //
            // • LYRICS state — the same artwork node zooms to
            //   [AmLyricsBlurDriftScale] and starts drifting, and the canvas
            //   fades out over it before its TextureView is torn down (the
            //   ExoPlayer instance is retained either way, so closing lyrics
            //   resumes the canvas instantly instead of reloading it).
            //
            // Everything animates off `lyricsBackdropProgress`, and every read
            // of it happens inside a graphicsLayer lambda — i.e. in the draw
            // phase — so the zoom/drift/fade costs a redraw of an
            // already-rasterized blur layer per frame and NOT a recomposition.
            val driftGraphicsLayer: GraphicsLayerScope.() -> Unit = {
                // Deferred state reads: draw phase only. See the comment on
                // lyricsBackdropProgress for why this is a continuous ramp
                // rather than the `if (lyricsOpen && lyricsContentReady)` step
                // it replaced.
                val progress = lyricsBackdropProgress.value
                // Scale [AmLyricsBlurDriftScale] (lyrics fully open) leaves the
                // solid part of the rotated image covering a circle of radius
                // 806dp on a 360x800 screen, against the 559dp the corner plus
                // drift(±120) asks for — see the constant's own comment for the
                // full budget. A smaller scale would let a corner expose
                // transparent areas, which the blur then sampled as a flickering
                // dark band.
                //
                // Drift and rotation both scale with the same progress, so
                // neither can outrun the zoom that has to cover them: at p = 0
                // there is no translation and no rotation at all, and both reach
                // full amplitude only once the zoom does.
                //
                // At low p the overhang is short of the *blur radius* alone (36dp
                // vs 64dp at p = 0) — but that is the COVER state, which has zero
                // drift and zero rotation and looked exactly this way before the
                // ramp existed (a 72dp blur at a fixed 1.2x, i.e. a slightly
                // larger shortfall). What the ramp has to avoid is motion
                // outrunning the zoom, and it does.
                val scale = AmCoverBlurScale + (AmLyricsBlurDriftScale - AmCoverBlurScale) * progress
                scaleX = scale
                scaleY = scale
                if (progress > 0f) {
                    // Ramping the amplitude with `progress` is what keeps the
                    // wander from snapping in: the phase advances the whole
                    // time lyrics is open, so without the multiplier the very
                    // first drifted frame would teleport the artwork by
                    // however far along the path the phase already was. The
                    // rotation is ramped for exactly the same reason.
                    translationX = blurWander.xDp.floatValue * driftDpToPx * progress
                    translationY = blurWander.yDp.floatValue * driftDpToPx * progress
                    // Rotation is what actually carries a colour from the top of
                    // the screen to the bottom — translation alone moves every
                    // colour by the same vector, so the arrangement stays rigid
                    // and the top stays the top. See BlurWanderDrift.
                    rotationZ = blurWander.rotationDeg.floatValue * progress
                }
                // Force an offscreen compositing layer so the (expensive)
                // Modifier.blur RenderEffect applied to this same node is
                // rasterized ONCE into an offscreen buffer and only the
                // cheap translation/scale transform re-runs every frame as
                // the drift values change. Without this, some GPU drivers
                // re-compute the 64dp blur on every frame because the
                // layer's transform changed — stealing GPU frame budget
                // from the 60Hz karaoke syllable fill animation in the
                // lyrics overlay (Enhanced style only, since V2 renders
                // its own syllables and is less sensitive to GPU pressure).
                compositingStrategy = CompositingStrategy.Offscreen
            }

            // ── Canvas → still-art seam ──
            // The blurred canvas layer used to run the full height of the player, so a
            // BetterLyrics/Spotify canvas kept playing — blurred — behind the bottom controls.
            // Apple Music ends the animated artwork at the artwork stage and carries the same
            // colours on underneath the controls as a still gradient.
            //
            // This dissolves the canvas over the last [AmCanvasSeamFadeDp] of the artwork stage
            // using the same DstIn trick AppleMusicSharpArtwork's fadeBottom uses. Below the seam
            // what shows is the blurred album art already rendered underneath the canvas — same
            // scrim on top, colours continuing across the join with nothing to give the seam
            // away, and nothing moving behind the controls.
            //
            // Portrait only: in landscape the controls sit beside the artwork, not below it, so
            // there is no seam to hide.
            val canvasSeamFade: Modifier =
                if (landscape) {
                    Modifier
                } else {
                    Modifier
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            val seam = morphAreaHeightPx.toFloat()
                            // Before the stage has been measured there is nothing to fade to.
                            if (seam <= 0f || seam >= size.height) return@drawWithContent
                            val fadeStart = ((seam - AmCanvasSeamFadeDp.toPx()) / size.height).coerceIn(0f, 1f)
                            drawRect(
                                brush =
                                    Brush.verticalGradient(
                                        fadeStart to Color.Black,
                                        (seam / size.height) to Color.Transparent,
                                    ),
                                blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                            )
                        }
                }

            // The artwork backdrop. Composed in every state so there is no node
            // swap (and so no re-decode, no new RenderEffect, no one-frame
            // discontinuity) when lyrics opens or closes.
            if (isPreS && preBlurredBitmap != null) {
                // Pre-Android-12 has no RenderEffect, so the blur was baked
                // into the bitmap on a background thread instead.
                Image(
                    bitmap = preBlurredBitmap!!.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .matchParentSize()
                            .graphicsLayer(driftGraphicsLayer),
                )
            } else {
                AsyncImage(
                    model = artworkRequest ?: artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .matchParentSize()
                            // graphicsLayer OUTSIDE blur: the blur is applied to
                            // the centered image (inside the layer), then the
                            // scale + translation is applied to the blurred
                            // result. Blurring after the transform would sample
                            // the translated image's edges instead.
                            .graphicsLayer(driftGraphicsLayer)
                            .then(
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    Modifier.blur(AmBackdropBlurRadius)
                                } else {
                                    Modifier
                                },
                            ),
                )
            }

            if (useCanvasBackdrop) {
                // Canvas backdrop — the ExoPlayer is ALWAYS retained (never
                // disposed across lyrics open/close) so the canvas resumes
                // instantly when lyrics closes — no multi-second reload delay.
                //
                // PERFORMANCE (lyrics lag fix): the TextureView is HIDDEN
                // (`visible = canvasVisibleForLyrics`) once lyrics is open. A
                // paused TextureView with Modifier.blur still costs a full
                // per-frame GPU composite + blur pass because the RenderEffect
                // is re-applied every frame even when the surface content
                // hasn't changed. This steals the frame budget from the karaoke
                // syllable sweep, causing the "lyrics lag after ~20s" symptom.
                // Hiding the TextureView entirely frees that budget; the
                // blurred artwork behind it has already faded up by then.
                //
                // The alpha ramp (draw phase) is what makes that hand-off
                // invisible: the canvas dissolves into the artwork over
                // [AmLyricsBackdropMorphMs] while the artwork zooms, instead of
                // the surface being yanked out from under a hard-swapped
                // overlay.
                CanvasArtworkPlayer(
                    primaryUrl = canvasPrimaryUrl,
                    fallbackUrl = canvasFallbackUrl,
                    isPlaying = isPlaying && canvasVisibleForLyrics,
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                    visible = canvasVisibleForLyrics,
                    modifier =
                        Modifier
                            .matchParentSize()
                            // Outside the blur so the mask is applied to the blurred result.
                            .then(canvasSeamFade)
                            .blur(72.dp)
                            .graphicsLayer {
                                // Fixed scale (no drift) — the canvas is on its
                                // way out by the time the drift matters.
                                scaleX = AmCoverBlurScale
                                scaleY = AmCoverBlurScale
                                alpha = 1f - lyricsBackdropProgress.value
                            },
                )
            }
            val preBlurLoading = isPreS && preBlurredBitmap == null && !canvasActive
            // Brightened scrim — matches ViviMusic's brighter aesthetic.
            // Previous alphas (0.42/0.60/0.82) were too dark; reduced to
            // 0.25/0.40/0.65 so the blurred artwork's color shows through.
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = if (useCanvasBackdrop || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.25f else if (preBlurLoading) 0.55f else 0.40f),
                                0.5f to Color.Black.copy(alpha = if (useCanvasBackdrop || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.40f else if (preBlurLoading) 0.65f else 0.55f),
                                1f to Color.Black.copy(alpha = if (useCanvasBackdrop || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.65f else if (preBlurLoading) 0.85f else 0.75f),
                            ),
                        ),
            )
        }

        if (landscape) {
            Row(Modifier.fillMaxSize()) {
                AppleMusicSharpArtwork(
                    artworkRequest = artworkRequest,
                    artworkUrl = artworkUrl,
                    canvasPrimaryUrl = canvasPrimaryUrl,
                    canvasFallbackUrl = canvasFallbackUrl,
                    isPlaying = isPlaying,
                    fadeBottom = false,
                    videoId = mediaMetadata.id.takeIf { !it.isLocalMediaId() },
                    isMusicVideo = mediaMetadata.isMusicVideo,
                    landscape = true,
                    artworkCornerRadiusDp = artworkCornerRadiusDp,
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                )
                AnimatedVisibility(
                    visible = (!lyricsOpen && !queueOpen) ||
                        (queueOpen && playerControlsExpanded) ||
                        (lyricsOpen && showLyricsPlayerControls && playerControlsExpanded),
                    enter = fadeIn(tween(120)),
                    exit = fadeOut(tween(100)),
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                ) {
                    AppleMusicControlsColumn(
                        mediaMetadata = mediaMetadata,
                        isPlaying = isPlaying,
                        isLoading = isLoading,
                        canSkipPrevious = canSkipPrevious,
                        canSkipNext = canSkipNext,
                        sliderPosition = sliderPosition,
                        positionProvider = positionProvider,
                        duration = duration,
                        playerConnection = playerConnection,
                        currentSongLiked = currentSongLiked,
                        volume = volume,
                        onVolumeChange = onVolumeChange,
                        titleActions = titleActions,
                        onPlayPauseClick = onPlayPauseClick,
                        onMoreClick = onMoreClick,
                        onOutputClick = onOutputClick,
                        onQueueClick = onQueueClick,
                        onLyricsClick = onLyricsClick,
                        onSliderValueChange = onSliderValueChange,
                        onSliderValueChangeFinished = onSliderValueChangeFinished,
                        currentFormat = currentFormat,
                        onQualityChipClick = {
                            bottomSheetPageState.show { ShowMediaInfo(mediaMetadata.id) }
                        },
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(bottom = contentBottomPadding),
                    )
                }
            }
        } else {
            // Portrait layout with ViviMusic-style in-place queue morph.
            //
            // The artwork + title row live inside a SharedTransitionLayout so they
            // can morph (large → mini) when the user toggles the queue. The playback
            // controls (seekbar + transport + volume + bottom row) live outside the
            // SharedTransitionLayout so they stay anchored at the bottom — matching
            // ViviMusic's Player_v2 layout exactly.
            //
            // The lyrics composable is rendered as a SEPARATE overlay ON TOP of the
            // SharedTransitionLayout (inside the same weighted Box), but OUTSIDE
            // the SharedTransitionLayout itself. This is critical for two reasons:
            //
            // 1. PERFORMANCE: rendering LyricsEnhanced inside the SharedTransitionLayout
            //    caused the karaoke syllable fill animation to stutter because the
            //    shared transition machinery's per-frame tracking stole frame budget.
            //    The overlay approach gives the lyrics animations the full frame
            //    budget, matching the standalone LyricsScreen's performance.
            //
            // 2. TOUCH ROUTING: the lyrics overlay is bounded to the weighted Box
            //    (the morph area), so it CANNOT extend over the controls below.
            //    Previously the overlay used fillMaxSize on the outer Box, covering
            //    the controls and making them uninteractable when lyrics was open.
            //    Now the overlay is a sibling of SharedTransitionLayout inside the
            //    weighted Box, and the controls live in a separate AnimatedVisibility
            //    below the Box — touches on the controls go directly to the controls.
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                BoxWithConstraints(
                    // Reports the artwork stage height to canvasSeamFade above.
                    modifier = Modifier.weight(1f).onSizeChanged { morphAreaHeightPx = it.height },
                ) {
                // Mini header height = artwork size + vertical padding (8.dp top + 8.dp bottom)
                // + top system bar inset (status bar / notch). The overlay must start
                // BELOW this height so it doesn't intercept taps on the mini header's
                // artwork (restore cover) and favourite/overflow chips.
                val topInset = LocalStableSystemBarsTopPadding.current
                val miniHeaderHeight = AppleMusicMiniArtworkSize + 16.dp + topInset
                SharedTransitionLayout(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    AnimatedContent(
                        targetState = morphState,
                        transitionSpec = {
                            // Symmetric crossfade with shared-element morph. Both the
                            // COVER (source) and QUEUE/LYRICS (target) fade over 600ms so
                            // their opacities are always complementary — at any point in
                            // the transition, the dark COVER masks the pink blurred
                            // backdrop of the entering LYRICS/QUEUE state.
                            //
                            // PREVIOUS APPROACH: fadeOut was 200ms (fast) so the COVER
                            // disappeared while the LYRICS state was still at ~33% opacity.
                            // The sudden removal of the dark COVER revealed the LYRICS
                            // state's pink blurred backdrop at low opacity, perceived as a
                            // "pink flash at the end of the transition". Matching the
                            // fadeOut duration to the fadeIn (both 600ms) eliminates this
                            // flash because the COVER stays visible long enough to mask
                            // the pink backdrop until both states are at ~50% opacity.
                            //
                            // The "thumbnail stays square" concern from the previous
                            // fast-fadeOut approach is no longer relevant because the
                            // AdaptiveCornerShape OverlayClip on the sharedBounds modifier
                            // keeps the artwork's corners rounded throughout the morph —
                            // the COVER's content never appears "square" even while it's
                            // fading out.
                            fadeIn(tween(600, easing = FastOutSlowInEasing)) togetherWith
                                fadeOut(tween(600, easing = FastOutSlowInEasing))
                        },
                        modifier = Modifier.fillMaxSize(),
                        label = "AppleMusicMorph",
                    ) { targetState ->
                        if (targetState == AppleMusicPlayerState.COVER) {
                            // COVER state: large sharp artwork fills the morph area.
                            // The canvas lives inside AppleMusicSharpArtwork (default
                            // showCanvas = true). It IS disposed/recreated on morph
                            // transitions, but hoisting it outside AnimatedContent
                            // breaks touch routing (see comment above the lyrics
                            // overlay for details).
                            Box(modifier = Modifier.fillMaxSize()) {
                                AppleMusicSharpArtwork(
                                    artworkRequest = artworkRequest,
                                    artworkUrl = artworkUrl,
                                    canvasPrimaryUrl = canvasPrimaryUrl,
                                    canvasFallbackUrl = canvasFallbackUrl,
                                    isPlaying = isPlaying,
                                    fadeBottom = !videoShowing,
                                    videoId = mediaMetadata.id.takeIf { !it.isLocalMediaId() },
                                    isMusicVideo = mediaMetadata.isMusicVideo,
                                    landscape = false,
                                    // Pass the FULL player height so the artwork
                                    // size doesn't shrink when the system nav bar
                                    // eats into the morph area (weight 1f).
                                    fullPlayerHeight = fullPlayerHeightForArtwork,
                                    // Pass the preference-derived corner radius so
                                    // the artwork clip inside
                                    // AppleMusicSharpArtwork matches the overlay
                                    // clip on the sharedBounds modifier below.
                                    artworkCornerRadiusDp = artworkCornerRadiusDp,
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .sharedBounds(
                                                sharedContentState =
                                                    rememberSharedContentState(key = "amCoverArt"),
                                                animatedVisibilityScope = this@AnimatedContent,
                                                // CRITICAL: explicitly set the overlay
                                                // clip to a RoundedCornerShape matching
                                                // the COVER artwork's own clip. The
                                                // default is RectangleShape, which
                                                // causes the shared element to flash
                                                // sharp corners for the entire duration
                                                // of the spring bounds animation (1-2s)
                                                // when morphing from the mini header
                                                // (8dp rounded) to the large cover
                                                // (preference-based radius). This was
                                                // the root cause of the "sharp squared
                                                // for a few seconds then becomes
                                                // rounded" bug.
                                                clipInOverlayDuringTransition =
                                                    OverlayClip(
                                                        AdaptiveCornerShape(
                                                            smallRadius = 8.dp,
                                                            smallSize = AppleMusicMiniArtworkSize,
                                                            largeRadius = artworkCornerRadiusDp,
                                                            largeSize = 400.dp,
                                                        ),
                                                    ),
                                                // Snappy non-bouncy spring — restores
                                                // the original morph feel that the
                                                // 600ms tween replaced. StiffnessMediumLow
                                                // is the SharedTransition framework
                                                // default stiffness, so the duration
                                                // matches the original out-of-the-box
                                                // morph; DampingRatioNoBouncy eliminates
                                                // the default LowBouncy overshoot that
                                                // was causing per-frame OverlayClip
                                                // re-renders (visible as a millisecond
                                                // stutter during the bounds animation).
                                                //
                                                // PINK-FLASH SAFETY: the pink flash
                                                // root cause was the ASYMMETRIC fade
                                                // (fadeOut 200ms vs fadeIn 600ms) — the
                                                // COVER disappeared at 200ms while the
                                                // LYRICS state (with its pink backdrop)
                                                // was still at ~33% opacity. That is
                                                // fixed by the symmetric 600ms fades
                                                // above (fadeIn + fadeOut both 600ms),
                                                // which keep the dark COVER visible
                                                // throughout the entire crossfade to
                                                // mask the pink backdrop. The
                                                // boundsTransform duration does NOT
                                                // affect the pink flash — only the
                                                // fade symmetry does — so reverting it
                                                // to the fast spring is safe.
                                                boundsTransform =
                                                    BoundsTransform { _, _ ->
                                                        spring(
                                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                                            stiffness = Spring.StiffnessMediumLow,
                                                        )
                                                    },
                                            ),
                                )
                            }
                        } else {
                            // QUEUE / LYRICS state: mini header + content below.
                            //
                            // The Spotify Canvas continues playing behind the
                            // queue/lyrics content so it doesn't "stop" when the
                            // user opens the queue. The canvas is rendered without
                            // blur (the haze overlay from the queue list provides the
                            // frosted-glass effect), and the lyrics composable has
                            // its own scrim for readability.
                            //
                            // Apply the stable top inset (notch / status bar / display-cutout
                            // top) so the mini header + content sit below the physical notch
                            // even when the status bar is hidden app-wide for immersive mode.
                            // The COVER state deliberately runs full-bleed (artwork under the
                            // status bar), but the QUEUE/LYRICS state shows interactive UI
                            // (title, pills, list/lyrics) that must not collide with the notch.
                            // LocalStableSystemBarsTopPadding is computed in MainActivity and
                            // floors against displayCutout so it stays non-zero when the status
                            // bar is hidden — mirroring the pattern used by every other screen.
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .windowInsetsPadding(WindowInsets(top = LocalStableSystemBarsTopPadding.current)),
                            ) {
                                // The blurred backdrop (rendered at the top level
                                // above) already contains the canvas with
                                // Modifier.blur(72.dp) when useCanvasBackdrop is
                                // true. We intentionally do NOT render a second
                                // non-blurred canvas here — the previous
                                // implementation did that, which covered the
                                // blurred backdrop and made it look like the
                                // blur "went away" when the queue opened.
                                // The queue sheet renders on a transparent
                                // background so the blurred canvas shows through.
                                Column(modifier = Modifier.fillMaxSize()) {
                                    AppleMusicMiniHeader(
                                        artworkRequest = artworkRequest,
                                        artworkUrl = artworkUrl,
                                        mediaMetadata = mediaMetadata,
                                        currentSongLiked = currentSongLiked,
                                        titleActions = titleActions,
                                        onToggleLike = playerConnection::toggleLike,
                                        onMoreClick = onMoreClick,
                                        onArtworkClick = restoreCover,
                                        onCloseClick = if (targetState == AppleMusicPlayerState.LYRICS) restoreCover else null,
                                        animatedVisibilityScope = this@AnimatedContent,
                                        // Pass the COVER artwork's corner radius so
                                        // the mini header's OverlayClip can use it
                                        // during COVER→LYRICS transitions. Without
                                        // this, the overlay uses the mini's own 8dp
                                        // radius, which looks sharp on the large
                                        // cover bounds at the start of the morph —
                                        // causing "corners gradually become rounded
                                        // at the end" (issue 2). Using the larger
                                        // cover radius ensures corners are properly
                                        // rounded from the very first frame.
                                        artworkCornerRadiusDp = artworkCornerRadiusDp,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    if (targetState == AppleMusicPlayerState.QUEUE) {
                                        AppleMusicQueueSheet(
                                            navController = navController,
                                            playerBottomSheetState = state,
                                            modifier =
                                                Modifier
                                                    .fillMaxSize()
                                                    .animateEnterExit(
                                                        enter = slideInVertically(
                                                            animationSpec = tween(600, easing = FastOutSlowInEasing),
                                                        ) { it / 4 } + fadeIn(tween(600)),
                                                        exit = fadeOut(tween(400)) +
                                                            slideOutVertically(
                                                                animationSpec = tween(400, easing = FastOutSlowInEasing),
                                                            ) { it / 4 },
                                                    ),
                                        )
                                    }
                                    // NOTE: The LyricsEnhanced/LyricsV2 composable is
                                    // intentionally NOT rendered here inside the
                                    // SharedTransitionLayout/AnimatedContent. Rendering it
                                    // here caused the karaoke syllable fill animation to
                                    // stutter because the SharedTransitionLayout's per-frame
                                    // shared-element tracking stole frame budget from the
                                    // lyrics animation. Instead, the lyrics composable is
                                    // rendered as a separate overlay BELOW the
                                    // SharedTransitionLayout — completely outside the shared
                                    // transition machinery. This gives the lyrics animations
                                    // the full frame budget, matching the standalone
                                    // LyricsScreen's performance.
                                }
                            }
                        }
                    }
                }

                // === Foreground sharp canvas ===
                //
                // NOTE: The foreground canvas (sharp, inside AppleMusicSharpArtwork)
                // is NOT hoisted here. Hoisting it outside the AnimatedContent would
                // keep the ExoPlayer alive across morph transitions (avoiding reload
                // delay), BUT an always-composed TextureView with alpha=0 still
                // participates in Compose's hit-testing and would intercept touches
                // on the mini header (QUEUE/LYRICS state) and the queue sheet —
                // breaking the thumbnail-click-to-restore-cover and queue scrolling.
                //
                // Compose does NOT propagate unconsumed pointer events to siblings,
                // so the only way to keep the mini header clickable is to ensure the
                // canvas is NOT the topmost composable at the mini header's touch
                // point. The canvas therefore stays inside AppleMusicSharpArtwork
                // (in the AnimatedContent's COVER branch), which means it IS
                // disposed/recreated on morph transitions. The reload delay is
                // accepted as a trade-off for correct touch routing.
                //
                // The BACKDROP canvas (blurred, rendered above in the if (!videoShowing)
                // block) IS always alive — it lives outside the AnimatedContent, so
                // it doesn't interfere with the morph state changes or touch routing.
                // That fixes the "behind of bottom controls are black" issue.

                // Lyrics overlay — INSIDE the weighted Box, ON TOP of the
                // SharedTransitionLayout but BOUNDED to the lyrics area (below
                // the mini header). This means the overlay CANNOT extend over
                // the mini header (so the mini header's artwork + favourite +
                // overflow chips remain tappable) NOR over the controls below
                // (which live in a separate AnimatedVisibility outside this Box).
                //
                // PREVIOUS APPROACH & BUG: the overlay used fillMaxSize with a
                // clickable top Box (height = mini header height) that called
                // restoreCover(). That top Box intercepted ALL taps on the mini
                // header area — including taps on the favourite (star) and
                // overflow (more) chips — so tapping those chips dismissed the
                // lyrics instead of performing the chip's action.
                //
                // FIX: the overlay's Column is now sized to (maxHeight -
                // miniHeaderHeight) and offset down by miniHeaderHeight. This
                // leaves the mini header area (top of the weighted Box) EXPOSED
                // — taps on the mini header fall through to the
                // SharedTransitionLayout below, where the AppleMusicMiniHeader's
                // artwork (restoreCover) and favourite/overflow chips receive
                // the taps normally.
                //
                // NOTE: we use the standalone AnimatedVisibility (androidx.compose.
                // animation.AnimatedVisibility) — NOT the ColumnScope extension —
                // because this is inside a BoxWithConstraints, not a Column.
                androidx.compose.animation.AnimatedVisibility(
                    visible = lyricsOpen,
                    enter = fadeIn(tween(400, easing = FastOutSlowInEasing)),
                    exit = fadeOut(tween(300, easing = FastOutSlowInEasing)),
                ) {
                    val lyricsMode by rememberEnumPreference(LyricsModeKey, LyricsMode.ENHANCED)
                    // Lyrics area — poke controls on touch, lyrics scroll.
                    // The Column is sized to fill the area BELOW the mini header
                    // (maxHeight - miniHeaderHeight) and offset down by
                    // miniHeaderHeight so it doesn't cover the mini header.
                    //
                    // HORIZONTAL INSETS: deliberately NOT applying
                    // `windowInsetsPadding(systemBars.only(Horizontal))` here.
                    // The mini header above (AppleMusicMiniHeader's Row, line ~1798)
                    // has NO horizontal systemBars padding — its parent Box only
                    // applies `windowInsetsPadding(WindowInsets(top = ...))` for the
                    // notch/status bar TOP inset. So the album art's LEFT edge sits
                    // at exactly AppleMusicContentPadding (28dp) from the screen edge.
                    //
                    // If we added systemBars horizontal padding to this lyrics overlay
                    // Box, the lyrics' left edge would be pushed further right than
                    // the album art (by systemBars.left), breaking alignment. Skipping
                    // the horizontal inset keeps the lyrics overlay's bounds identical
                    // to the mini header's bounds — both at 0..screenWidth with no
                    // system-bar horizontal inset — so the inner 28dp-based padding
                    // calculations align perfectly.
                    //
                    // (Vertical inset is also intentionally skipped: the overlay is
                    // explicitly positioned via .offset(y = miniHeaderHeight) below
                    // the mini header, and the bottom inset is handled by the parent
                    // weighted Box / navigationBarsPadding on the controls below.)
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(maxHeight - miniHeaderHeight)
                                .offset(y = miniHeaderHeight)
                                .pointerInput(lyricsOpen, lyricsSwipeDismissThresholdPx) {
                                    if (!lyricsOpen) return@pointerInput
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        pokePlayerControlsVisibility()
                                        while (true) {
                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                            if (!change.pressed) break

                                            val deltaX = change.position.x - down.position.x
                                            val deltaY = change.position.y - down.position.y
                                            if (deltaY < 0f || abs(deltaX) > abs(deltaY)) break
                                            if (deltaY > 0f) change.consume()
                                            if (isAppleMusicLyricsDismissDrag(deltaX, deltaY, lyricsSwipeDismissThresholdPx)) {
                                                restoreCover()
                                                break
                                            }
                                        }
                                    }
                                },
                    ) {
                        // HORIZONTAL PADDING — compensate for the mocharealm
                        // KaraokeLineText library's INTERNAL 16dp horizontal padding
                        // (see lyrics-ui-android sources: KaraokeLineText.kt line ~514
                        // applies `padding(vertical = 8.dp, horizontal = 16.dp)` to its
                        // Column for non-accompaniment lines).
                        //
                        // The album art's left edge sits at AppleMusicContentPadding
                        // (28dp) from the screen edge (via AppleMusicMiniHeader's Row
                        // padding). For the lyrics TEXT left edge to align EXACTLY with
                        // the album art's left edge, we need:
                        //
                        //   our_padding + library_padding(16dp) = AppleMusicContentPadding(28dp)
                        //   our_padding = 12dp
                        //
                        // Previous fix (commit 7e8503806) used AppleMusicContentPadding
                        // (28dp) directly, which left the lyrics text at 28 + 16 = 44dp
                        // from the screen edge — 16dp to the RIGHT of the album art.
                        // The user reported this as "still shifted towards right a bit
                        // and not aligned".
                        //
                        // Using AppleMusicContentPadding - 16.dp (= 12dp) as our padding
                        // makes the lyrics text left edge land at 12 + 16 = 28dp from
                        // the screen edge, EXACTLY aligned with the album art.
                        //
                        // LIBRARY WRAP BEHAVIOUR: the mocharealm library DOES wrap long
                        // lines — `calculateBalancedLines` (LyricsLayoutCalculator.kt
                        // line ~273) uses Knuth's optimal line-breaking algorithm with
                        // the availableWidthPx from BoxWithConstraints inside
                        // KaraokeLineText. Long word-synced lines that exceed the
                        // available width are automatically broken into multiple visual
                        // rows. By reducing our padding from 28dp to 12dp, we give the
                        // library MORE width to work with (screen_width - 24dp instead
                        // of screen_width - 56dp), so wrap triggers later for
                        // medium-length lines (fewer awkward wraps) but still triggers
                        // for genuinely long lines (matching the user's request:
                        // "whenever a song with enhanced style word synced lyrics have
                        // long enough lines that cross the display area, shift the
                        // words into another line").
                        //
                        // NO clipToBounds(): mirrors the standalone LyricsScreen's
                        // AppleMusicLyricsPane (LyricsScreen.kt:1208-1217), which uses
                        // fillMaxSize() + padding(horizontal = ...) with NO clip. Any
                        // residual draw-phase overflow (e.g., the swell animation
                        // scaling a syllable by ~10% beyond its layout width) is
                        // allowed to extend into the empty padded area and only gets
                        // clipped by the physical screen edge if truly necessary.
                        val lyricsHorizontalPadding = AppleMusicContentPadding - 16.dp
                        when (lyricsMode) {
                            LyricsMode.V2 -> LyricsV2(
                                sliderPositionProvider = lyricsPosProvider,
                                lyricsSyncOffset = lyricsSyncOffset,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = lyricsHorizontalPadding),
                            )
                            LyricsMode.ENHANCED -> LyricsEnhanced(
                                sliderPositionProvider = lyricsPosProvider,
                                lyricsSyncOffset = lyricsSyncOffset,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = lyricsHorizontalPadding),
                            )
                        }
                    }
                }
                } // end weighted BoxWithConstraints (SharedTransitionLayout + lyrics overlay)

                // Persistent playback controls — anchored at the bottom.
                // When the queue or lyrics is open, the title row is hidden (it's in the
                // mini header above) so only the seekbar + transport + volume + bottom
                // row render.
                //
                // Auto-hide follows the standalone LyricsScreen preference (5s when enabled).
                // The mini header remains visible, so the user can always return to the player.
                // Slide requires an extra layout pass on top of the fade; skip it when
                // animations are reduced so the auto-hide/show cycle doesn't compete with
                // the karaoke lyrics view for frame budget on lower-end devices.
                AnimatedVisibility(
                    visible = (!lyricsOpen && !queueOpen) ||
                        (queueOpen && playerControlsExpanded) ||
                        (lyricsOpen && showLyricsPlayerControls && playerControlsExpanded),
                    enter = if (animationsDisabled) {
                        fadeIn(tween(120))
                    } else {
                        fadeIn(tween(180)) + slideInVertically(tween(180)) { it / 6 }
                    },
                    exit = if (animationsDisabled) {
                        fadeOut(tween(100))
                    } else {
                        fadeOut(tween(140)) + slideOutVertically(tween(140)) { it / 8 }
                    },
                ) {
                    AppleMusicControlsColumn(
                        mediaMetadata = mediaMetadata,
                        isPlaying = isPlaying,
                        isLoading = isLoading,
                        canSkipPrevious = canSkipPrevious,
                        canSkipNext = canSkipNext,
                        sliderPosition = sliderPosition,
                        positionProvider = positionProvider,
                        duration = duration,
                        playerConnection = playerConnection,
                        currentSongLiked = currentSongLiked,
                        volume = volume,
                        onVolumeChange = onVolumeChange,
                        titleActions = titleActions,
                        onPlayPauseClick = onPlayPauseClick,
                        onMoreClick = onMoreClick,
                        onOutputClick = onOutputClick,
                        onQueueClick = toggleQueue,
                        onLyricsClick = toggleLyrics,
                        onSliderValueChange = onSliderValueChange,
                        onSliderValueChangeFinished = onSliderValueChangeFinished,
                        currentFormat = currentFormat,
                        onQualityChipClick = {
                            bottomSheetPageState.show { ShowMediaInfo(mediaMetadata.id) }
                        },
                        showTitleRow = !morphOpen,
                        isQueueActive = queueOpen,
                        isLyricsActive = lyricsOpen,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                // NOTE: no navigationBarsPadding() here — contentBottomPadding
                                // already includes the system-bars bottom inset via
                                // collapsedBound (= dynamicQueuePeekHeight + systemBarsBottom).
                                // Adding navigationBarsPadding() on top double-counts the
                                // inset and makes the controls jump up when the nav bar
                                // appears.
                                .padding(bottom = contentBottomPadding),
                    )
                }
            } // end Column (morph area + controls)
        }
    }
}

@Composable
private fun AppleMusicSharpArtwork(
    artworkRequest: coil3.request.ImageRequest?,
    artworkUrl: String?,
    canvasPrimaryUrl: String?,
    canvasFallbackUrl: String?,
    isPlaying: Boolean,
    fadeBottom: Boolean,
    videoId: String? = null,
    isMusicVideo: Boolean = false,
    landscape: Boolean = false,
    // When false, the CanvasArtworkPlayer is NOT rendered inside this composable.
    // The caller is responsible for rendering the canvas separately (hoisted
    // outside the AnimatedContent) to keep the ExoPlayer alive across morph
    // state transitions. Used by the portrait Apple Music layout.
    showCanvas: Boolean = true,
    // The FULL player height (from the outer BoxWithConstraints), used for
    // artwork sizing so the artwork stays a consistent size regardless of
    // the system navigation bar inset. When the nav bar is visible, the
    // morph area (weight 1f) shrinks, but the artwork should NOT shrink
    // with it — this parameter decouples artwork size from morph area height.
    // Null = fall back to the local maxHeight (landscape or legacy callers).
    fullPlayerHeight: Dp? = null,
    // The preference-derived corner radius for the immersiveExtendedCard
    // artwork. Hoisted from the parent (AppleMusicPlayerContent) so the
    // same value can be used for the sharedBounds overlay clip — keeping
    // the overlay's clip in sync with the artwork's own clip during morph
    // transitions. See clipInOverlayDuringTransition on the sharedBounds
    // modifier in AppleMusicPlayerContent.
    artworkCornerRadiusDp: Dp = 16.dp,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current
    Box(
        modifier =
            modifier.then(
                if (fadeBottom) {
                    // Fade the sharp artwork's lower edge into the blurred layer beneath.
                    Modifier
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                brush =
                                    Brush.verticalGradient(
                                        0.62f to Color.Black,
                                        1f to Color.Transparent,
                                    ),
                                blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                            )
                        }
                } else {
                    Modifier
                },
            ),
    ) {
        val videoArtworkState = LocalVideoArtworkState.current
        val showVideo =
            videoArtworkState != null &&
                !videoArtworkState.hasPlaybackFailed &&
                isMusicVideo &&
                !videoId.isNullOrBlank() &&
                playerConnection != null
        // "Immersive extended" — when there is no Spotify Canvas (or any animated
        // artwork) AND no music video, render the still cover as a square using
        // the SAME sizing formula as the Material Extended (V9) player, instead of
        // stretching the cover to fill the rectangular stage. When a canvas or
        // video IS available, we keep the full-bleed display so the animated
        // artwork can fill the stage.
        val hasCanvas = !canvasPrimaryUrl.isNullOrBlank() || !canvasFallbackUrl.isNullOrBlank()
        val immersiveExtendedCard = !showVideo && !hasCanvas
        if (showVideo) {
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .background(Color.Black),
            )
        } else if (immersiveExtendedCard) {
            // "Immersive extended" — render the still cover as a square using the
            // SAME sizing formula and thresholds as the Material Extended (V9)
            // player (see V9PortraitContent in PlayerComponents.kt), centered
            // inside the artwork stage. V9 computes its cap against the FULL
            // player height, but inside AppleMusicSharpArtwork maxHeight is only
            // the stage height (55% of the player height in portrait, full
            // height in landscape) — so we re-derive the full height first and
            // run ALL the compact-height thresholds against that full height.
            // Without this, a typical 800dp-tall player would see its 440dp
            // stage trip the "veryCompact" branch and shrink the artwork from
            // 0.40 * H to 0.32 * H, making it noticeably smaller than V9.
            //
            // CRITICAL (nav-bar fix): when fullPlayerHeight is provided (portrait
            // Apple Music layout), we use it DIRECTLY instead of dividing
            // maxHeight by 0.55f. The 0.55f heuristic was only correct when the
            // morph area was exactly 55% of the player — but with a weight(1f)
            // morph area, the actual ratio changes when the system nav bar
            // appears (the controls eat the nav-bar inset, shrinking the morph
            // area). Using the real full height keeps the artwork at a constant
            // size whether the nav bar is visible or hidden.
            BoxWithConstraints(modifier = Modifier.matchParentSize()) {
                val horizontalPadding = if (maxWidth < 380.dp) 16.dp else 20.dp
                val effectiveFullHeight = fullPlayerHeight ?: if (landscape) maxHeight else maxHeight / 0.55f
                val compactHeight = effectiveFullHeight < 760.dp
                val veryCompactHeight = effectiveFullHeight < 700.dp
                // artworkCornerRadiusDp is now hoisted from the parent
                // (AppleMusicPlayerContent) so the same value can be used
                // for the sharedBounds overlay clip during morph transitions.
                // Previously this was read locally via rememberPreference,
                // which meant the sharedBounds modifier couldn't access it —
                // causing the overlay to use the default RectangleShape and
                // flash sharp corners during the bounds animation.
                val artworkMinSize =
                    when {
                        veryCompactHeight -> 200.dp
                        compactHeight -> 216.dp
                        else -> 236.dp
                    }
                // Two-sided cap: the full-player-height cap keeps the artwork
                // a constant size regardless of nav-bar visibility (so it
                // doesn't visibly "shrink" when the nav bar appears), while
                // the morph-area cap (maxHeight * 0.82f) guarantees the
                // artwork ALWAYS fits inside the morph area — even when the
                // nav bar eats into the bottom and shrinks the weight(1f)
                // area. Without the morph-area cap, the centered artwork
                // overflows upward into the notch/cutout on devices that have
                // one. The multipliers are kept at the original 0.40/0.35/0.32
                // (NOT reduced) — the user explicitly said "no need to make
                // anything smaller"; the notch collision is fixed by the
                // morph-area cap alone, not by shrinking the artwork.
                val artworkHeightLimitFromFull =
                    effectiveFullHeight *
                        when {
                            veryCompactHeight -> 0.32f
                            compactHeight -> 0.35f
                            else -> 0.40f
                        }
                val artworkHeightLimitFromMorph = maxHeight * 0.82f
                val artworkHeightLimit =
                    minOf(artworkHeightLimitFromFull, artworkHeightLimitFromMorph)
                val artworkSize =
                    (maxWidth - horizontalPadding * 2)
                        .coerceAtMost(artworkHeightLimit)
                        .coerceAtLeast(artworkMinSize)
                // Pause-scale animation (non-canvas songs only). When the
                // music is paused, the artwork shrinks slightly (~8%) to
                // mirror Apple Music's behavior. When playback resumes, it
                // restores to full size. This only applies to the
                // immersiveExtendedCard branch (static artwork — no Spotify
                // Canvas, no music video). Canvas songs continue playing
                // their loop regardless of audio play state, so shrinking
                // them would look wrong.
                val artworkPauseScale by animateFloatAsState(
                    targetValue = if (isPlaying) 1f else 0.92f,
                    animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
                    label = "artworkPauseScale",
                )
                Box(
                    modifier = Modifier.matchParentSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = artworkRequest ?: artworkUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .size(artworkSize)
                                .graphicsLayer {
                                    scaleX = artworkPauseScale
                                    scaleY = artworkPauseScale
                                    // Apply shadow elevation + clip in a single
                                    // graphicsLayer instead of separate .shadow()
                                    // + .clip() modifiers. During SharedTransition
                                    // the overlay renders the shared element in its
                                    // own layer; separate .shadow() creates an
                                    // additional shadow layer that can flash as a
                                    // dark rectangle during the 200ms crossfade
                                    // (issue: "flash animation for a split second
                                    // during thumbnail transition"). Combining
                                    // shadowElevation + clip + shape into one
                                    // graphicsLayer ensures the shadow is clipped
                                    // to the rounded shape and rendered as part of
                                    // the same layer the overlay manages.
                                    shadowElevation = 8f
                                    clip = true
                                    shape = RoundedCornerShape(artworkCornerRadiusDp)
                                },
                    )
                }
            }
        } else {
            AsyncImage(
                model = artworkRequest ?: artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }

        if (showCanvas && !showVideo &&
            (!canvasPrimaryUrl.isNullOrBlank() || !canvasFallbackUrl.isNullOrBlank())
        ) {
            CanvasArtworkPlayer(
                primaryUrl = canvasPrimaryUrl,
                fallbackUrl = canvasFallbackUrl,
                isPlaying = isPlaying,
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                modifier = Modifier.matchParentSize(),
            )
        }

        if (showVideo) {
            InlineVideoPlayer(
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

@Composable
private fun AppleMusicControlsColumn(
    mediaMetadata: MediaMetadata,
    isPlaying: Boolean,
    isLoading: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    sliderPosition: Long?,
    positionProvider: () -> Long,
    duration: Long,
    playerConnection: PlayerConnection,
    currentSongLiked: Boolean,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    titleActions: PlayerTitleActions,
    onPlayPauseClick: () -> Unit,
    onMoreClick: () -> Unit,
    onOutputClick: () -> Unit,
    onQueueClick: () -> Unit,
    onLyricsClick: () -> Unit,
    onSliderValueChange: (Long) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
    // Stream format for the quality chip. Null = no chip rendered.
    currentFormat: FormatEntity?,
    // Clicked when the user taps the quality chip — opens the song-detail
    // bottom sheet (ShowMediaInfo), mirroring how tapping the title/artist
    // in Apple Music's stock UI opens the song info page.
    onQualityChipClick: () -> Unit,
    // When false, the title/artist row is hidden — used in QUEUE/LYRICS state
    // where the title lives in the mini header above.
    showTitleRow: Boolean = true,
    // Whether the in-place queue is currently open. Highlights the queue button.
    isQueueActive: Boolean = false,
    // Whether the in-place lyrics view is currently open. Highlights the lyrics button.
    isLyricsActive: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var swipeUpAccumulated by remember { mutableFloatStateOf(0f) }
    val swipeUpThreshold = 120f
    val swipeActivationThreshold = 72f
    val resetSwipeUp = remember {
        {
            if (swipeUpAccumulated != 0f) swipeUpAccumulated = 0f
        }
    }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(300); resetSwipeUp() }

    // The controls cluster (title → bottom action row) is bottom-anchored by
    // the caller. Gaps between rows are controlled by explicit Spacers below
    // (not by verticalArrangement) so they stay predictable and compact.
    // Previously Arrangement.SpaceEvenly stretched the rows across the entire
    // slot — and Arrangement.spacedBy() stacked on top of the explicit Spacers
    // doubling the gaps. Arrangement.Bottom lets the Spacers be the single
    // source of truth for spacing.
    //
    // Gap values are calibrated to match the Apple Music reference layout:
    // ~20-22dp between rows on standard screens for comfortable breathing
    // room without being loose, compressed on shorter devices to prevent
    // overflow.
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val compactHeight = screenHeight < 720.dp
    val veryCompactHeight = screenHeight < 620.dp
    val titleToScrubberGap = if (veryCompactHeight) 8.dp else if (compactHeight) 14.dp else 20.dp
    val scrubberToTransportGap = if (veryCompactHeight) 12.dp else if (compactHeight) 16.dp else 22.dp
    val transportToVolumeGap = if (veryCompactHeight) 8.dp else if (compactHeight) 14.dp else 20.dp
    val volumeToActionsGap = if (veryCompactHeight) 12.dp else if (compactHeight) 16.dp else 22.dp

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = AppleMusicContentPadding)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var accumulated = 0f
                        var swipeActivated = false
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull() ?: break
                            if (change.changedToUp()) break

                            val dragDelta = change.positionChange().y

                            if (!swipeActivated) {
                                // Track upward movement but don't consume yet — let child taps win.
                                if (dragDelta < 0f) {
                                    accumulated += dragDelta
                                }
                                if (abs(accumulated) > swipeActivationThreshold) {
                                    swipeActivated = true
                                    swipeUpAccumulated = accumulated
                                    change.consume()
                                }
                            } else {
                                // Swipe is confirmed — consume to prevent child handling.
                                if (dragDelta < 0f) {
                                    swipeUpAccumulated =
                                        (swipeUpAccumulated + dragDelta).coerceAtLeast(-swipeUpThreshold * 1.5f)
                                }
                                change.consume()
                            }
                        }

                        if (swipeActivated && swipeUpAccumulated < -swipeUpThreshold) {
                            onQueueClick()
                        }
                        swipeUpAccumulated = 0f
                    }
            },
        // Bottom-aligned — gaps between rows are controlled by explicit
        // Spacers below (titleToScrubberGap, scrubberToTransportGap, etc.).
        // Do NOT use spacedBy here — it would stack on top of the Spacers and
        // double the gaps (previous regression: spacedBy(14.dp) + 28dp Spacer
        // = 42dp total gap, way too much).
        verticalArrangement = Arrangement.Bottom,
    ) {
    // Title / artist row with star + more chips.
    // Hidden when showTitleRow = false (queue is open — the title lives in
    // the mini header above the queue list).
    if (showTitleRow) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlayerTextBackdrop(
                textColor = Color.White,
                modifier = Modifier.weight(1f),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    val titleLayout = remember { mutableStateOf<TextLayoutResult?>(null) }
                    Text(
                        text = mediaMetadata.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { titleLayout.value = it },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .basicMarquee()
                                
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = titleActions.onTitleClick,
                                ),
                    )
                    Text(
                        text = mediaMetadata.artists.joinToString { it.name },
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.64f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .basicMarquee()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    mediaMetadata.artists.firstOrNull()?.id?.let(titleActions.onArtistClick)
                                },
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            AppleMusicChip(
                iconRes = if (currentSongLiked) R.drawable.player_star_filled else R.drawable.player_star,
                tint = Color.White,
                contentDescription = null,
                onClick = playerConnection::toggleLike,
            )
            Spacer(Modifier.width(10.dp))
            AppleMusicChip(
                iconRes = R.drawable.player_more_horiz,
                tint = Color.White,
                contentDescription = null,
                onClick = onMoreClick,
            )
        }
    }

    Spacer(Modifier.height(titleToScrubberGap))

    // Read the polled playback position through the deferred provider. This is
    // the ONLY place in AppleMusicControlsColumn that reads the 100ms-polled
    // position — by reading it here (inside the controls column that is only
    // composed when visible), we ensure the parent AppleMusicPlayerContent
    // does not recompose on every poll tick. When lyrics is open and controls
    // are auto-hidden, this composable is not composed at all, so the state
    // read never fires and no recomposition happens.
    val currentPosition = positionProvider()

    // Thin scrubber + elapsed / -remaining.
    Column {
        AppleMusicSeekBar(
            position = sliderPosition ?: currentPosition,
            duration = duration,
            onScrub = onSliderValueChange,
            onScrubFinished = onSliderValueChangeFinished,
        )
        Spacer(Modifier.height(6.dp))
        // Mirror the Immersive V8 layout: elapsed time on the left, quality
        // chip (Lossless / AAC / OPUS) centered, -remaining on the right.
        // The chip is tappable and opens the song-detail bottom sheet.
        Box(Modifier.fillMaxWidth()) {
            Text(
                text = makeTimeString(sliderPosition ?: currentPosition),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.55f),
                modifier = Modifier.align(Alignment.CenterStart),
            )
            if (currentFormat != null) {
                AppleMusicQualityChip(
                    currentFormat = currentFormat,
                    onClick = onQualityChipClick,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            Text(
                text = "-" + makeTimeString((duration - (sliderPosition ?: currentPosition)).coerceAtLeast(0L)),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.55f),
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }

    Spacer(Modifier.height(scrubberToTransportGap))

    // Bare transport glyphs.
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppleMusicTransportButton(
            iconRes = R.drawable.player_fast_forward,
            enabled = canSkipPrevious,
            mirrored = true,
            iconSize = AppleMusicTransportIconSize,
            onClick = playerConnection::seekToPrevious,
        )
        // Center slot MUST keep the same outer Box size (iconSize + 20.dp) in both
        // the loading and playing states — otherwise SpaceEvenly redistributes the
        // 20dp gap across the row and prev/next visually slide outward when the
        // spinner replaces the play button (user-reported "compact during loading").
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(AppleMusicPlayPauseIconSize + 20.dp)
                    .clip(CircleShape),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(AppleMusicPlayPauseIconSize),
                    strokeWidth = 3.dp,
                )
            } else {
                AppleMusicTransportButton(
                    iconRes = if (isPlaying) R.drawable.player_pause else R.drawable.player_play,
                    enabled = true,
                    mirrored = false,
                    iconSize = AppleMusicPlayPauseIconSize,
                    onClick = onPlayPauseClick,
                )
            }
        }
        AppleMusicTransportButton(
            iconRes = R.drawable.player_fast_forward,
            enabled = canSkipNext,
            mirrored = false,
            iconSize = AppleMusicTransportIconSize,
            onClick = playerConnection::seekToNext,
        )
    }

    Spacer(Modifier.height(transportToVolumeGap))

    // Flat volume slider with speaker glyphs. Uses the shared AppleMusicVolumeRow
    // which has proper drag tracking (dragging state + rememberUpdatedState) so the
    // fill follows the finger during a drag instead of lagging behind the rounded
    // device-volume step.
    AppleMusicVolumeRow(
        volume = volume,
        onVolumeChange = onVolumeChange,
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(volumeToActionsGap))

    // Bottom action row: lyrics / media output / queue.
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppleMusicBottomButton(
            iconRes = R.drawable.player_lyrics,
            contentDescription = stringResource(R.string.lyrics),
            onClick = onLyricsClick,
            tint = if (isLyricsActive) Color.White else Color.White.copy(alpha = 0.85f),
        )
        AppleMusicBottomButton(
            iconRes = R.drawable.cast,
            contentDescription = null,
            onClick = onOutputClick,
        )
        AppleMusicBottomButton(
            iconRes = R.drawable.player_queue_music,
            contentDescription = stringResource(R.string.queue),
            onClick = onQueueClick,
            tint = if (isQueueActive) Color.White else Color.White.copy(alpha = 0.85f),
        )
    }
    }
}

@Composable
private fun AppleMusicChip(
    iconRes: Int,
    tint: Color,
    contentDescription: String?,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(AppleMusicChipSize)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.14f))
                .clickable(onClick = onClick),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun AppleMusicTransportButton(
    iconRes: Int,
    enabled: Boolean,
    mirrored: Boolean,
    iconSize: Dp,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(iconSize + 20.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false, radius = iconSize / 2 + 10.dp),
                    enabled = enabled,
                    onClick = onClick,
                ),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = Color.White.copy(alpha = if (enabled) 1f else 0.4f),
            modifier =
                Modifier
                    .size(iconSize)
                    .graphicsLayer { if (mirrored) scaleX = -1f },
        )
    }
}

@Composable
private fun AppleMusicBottomButton(
    iconRes: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    tint: Color = Color.White.copy(alpha = 0.85f),
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(AppleMusicBottomButtonSize)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false, radius = AppleMusicBottomButtonSize / 2),
                    onClick = onClick,
                ),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(AppleMusicBottomIconSize),
        )
    }
}

/**
 * Mini header shown at the top of the QUEUE / LYRICS state. Contains a small
 * artwork (shared element with the large COVER artwork), compact title/artist,
 * and like + more buttons. Mirrors ViviMusic's Player_v2 mini header exactly.
 *
 * @param onArtworkClick Called when the mini artwork is tapped. Restores the
 *   COVER state (morphs back to the full main player).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedTransitionScope.AppleMusicMiniHeader(
    artworkRequest: coil3.request.ImageRequest?,
    artworkUrl: String?,
    mediaMetadata: MediaMetadata,
    currentSongLiked: Boolean,
    titleActions: PlayerTitleActions,
    onToggleLike: () -> Unit,
    onMoreClick: () -> Unit,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onArtworkClick: () -> Unit = {},
    onCloseClick: (() -> Unit)? = null,
    // The COVER artwork's corner radius, used for the OverlayClip during
    // COVER→LYRICS transitions. The mini header's own clip stays at 8dp
    // (its visual style), but the SharedTransition overlay uses this larger
    // radius so corners look properly rounded on the large cover bounds at
    // the start of the morph. See the call site for the full rationale.
    artworkCornerRadiusDp: Dp = 16.dp,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .padding(horizontal = AppleMusicContentPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Mini artwork — shared element with the large COVER artwork.
        // Tapping it restores the COVER state (morphs back to the main player).
        //
        // OVERLAY CLIP: uses an AdaptiveCornerShape that interpolates the
        // corner radius based on the element's current size. At the mini
        // header size (56dp) the radius is 8dp (matching this Box's own
        // 8dp clip); at the large cover size (~400dp) the radius is
        // artworkCornerRadiusDp (typically 16dp, matching the COVER's clip).
        // In between, it smoothly interpolates. This eliminates the
        // "sharp at start, curved at end" bug caused by a fixed-Dp
        // RoundedCornerShape looking disproportionate on different element
        // sizes (16dp on 320dp looks sharp, 16dp on 56dp looks very curved).
        // The mini header's own 8dp clip (below) only takes effect once the
        // transition completes and the overlay is removed — so the mini
        // header's final visual is unchanged.
        //
        // boundsTransform: 600ms tween matching the crossfade duration,
        // identical to the COVER state's. See the COVER state's sharedBounds
        // modifier for the full rationale.
        Box(
            modifier =
                Modifier
                    .size(AppleMusicMiniArtworkSize)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = false, radius = AppleMusicMiniArtworkSize / 2),
                        onClick = onArtworkClick,
                    )
                    .sharedBounds(
                        sharedContentState = rememberSharedContentState(key = "amCoverArt"),
                        animatedVisibilityScope = animatedVisibilityScope,
                        clipInOverlayDuringTransition =
                            OverlayClip(
                                AdaptiveCornerShape(
                                    smallRadius = 8.dp,
                                    smallSize = AppleMusicMiniArtworkSize,
                                    largeRadius = artworkCornerRadiusDp,
                                    largeSize = 400.dp,
                                ),
                            ),
                        // Match the COVER state's boundsTransform (non-bouncy
                        // spring at default StiffnessMediumLow) so the morph
                        // duration and feel are identical in both directions
                        // and the overlay clip doesn't stutter on oscillation.
                        // See the COVER state's sharedBounds modifier for the
                        // full rationale (including pink-flash safety).
                        boundsTransform =
                            BoundsTransform { _, _ ->
                                spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
                                )
                            },
                    ),
        ) {
            AsyncImage(
                model = artworkRequest ?: artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.width(12.dp))
        PlayerTextBackdrop(
            textColor = Color.White,
            modifier = Modifier.weight(1f),
        ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            val miniTitleLayout = remember { mutableStateOf<TextLayoutResult?>(null) }
            Text(
                text = mediaMetadata.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { miniTitleLayout.value = it },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .basicMarquee()
                        
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = titleActions.onTitleClick,
                        ),
            )
                Text(
                    text = mediaMetadata.artists.joinToString { it.name },
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .basicMarquee()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                mediaMetadata.artists.firstOrNull()?.id?.let(titleActions.onArtistClick)
                            },
                )
            }
        }
        AppleMusicChip(
            iconRes = if (currentSongLiked) R.drawable.player_star_filled else R.drawable.player_star,
            tint = Color.White,
            contentDescription = null,
            onClick = onToggleLike,
        )
        Spacer(Modifier.width(8.dp))
        AppleMusicChip(
            iconRes = R.drawable.player_more_horiz,
            tint = Color.White,
            contentDescription = null,
            onClick = onMoreClick,
        )
        onCloseClick?.let { closeClick ->
            Spacer(Modifier.width(8.dp))
            AppleMusicChip(
                iconRes = R.drawable.close,
                tint = Color.White,
                contentDescription = stringResource(R.string.close),
                onClick = closeClick,
            )
        }
    }
}

/** Thin Apple-Music-style scrubber: rounded 6dp track, no thumb, tap + drag to seek. */
@Composable
private fun AppleMusicSeekBar(
    position: Long,
    duration: Long,
    onScrub: (Long) -> Unit,
    onScrubFinished: () -> Unit,
) {
    val enabled = duration > 0L
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val playedFraction =
        if (duration > 0L) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val shownFraction = if (dragging) dragFraction else playedFraction

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(26.dp)
                .pointerInput(enabled, duration) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { offset ->
                        val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                        onScrub((fraction * duration).toLong())
                        onScrubFinished()
                    }
                }.pointerInput(enabled, duration) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragging = true
                            dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                            onScrub((dragFraction * duration).toLong())
                        },
                        onDragEnd = {
                            dragging = false
                            onScrubFinished()
                        },
                        onDragCancel = { dragging = false },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                            onScrub((dragFraction * duration).toLong())
                        },
                    )
                }.drawWithContent {
                    val trackHeight = if (dragging) 10.dp.toPx() else 7.dp.toPx()
                    val top = (size.height - trackHeight) / 2f
                    val radius = CornerRadius(trackHeight / 2f)
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.28f),
                        topLeft = Offset(0f, top),
                        size = Size(size.width, trackHeight),
                        cornerRadius = radius,
                    )
                    drawRoundRect(
                        color = Color.White.copy(alpha = if (dragging) 1f else 0.85f),
                        topLeft = Offset(0f, top),
                        size = Size(size.width * shownFraction, trackHeight),
                        cornerRadius = radius,
                    )
                },
    )
}

/** Flat volume slider matching the scrubber's look. */
/** NOTE: The local AppleMusicVolumeSlider was removed in favor of the shared
 *  AppleMusicVolumeRow (in AppleMusicSlider.kt) which has proper drag tracking
 *  via `dragging` state + `rememberUpdatedState`. The old local slider used
 *  `pointerInput(Unit)` which captured stale callbacks and didn't track drag
 *  state, causing the fill to lag behind the finger. */

/**
 * Quality chip rendered between the elapsed and -remaining timestamps on the
 * Apple Music player's seek-bar row. Mirrors the Immersive V8 player's
 * `V8QualityChip` (PlayerComponents.kt:2762) — same pill shape, same waveform
 * icon (`R.drawable.player_graphic_eq`), same `codecLabel()` text — but uses
 * `Color.White` as the foreground because the Apple Music player renders on
 * top of artwork-on-black, not a themed surface.
 *
 * Tapping the chip opens the song-detail bottom sheet (`ShowMediaInfo`),
 * matching how Apple Music's stock UI exposes the song info page.
 */
@Composable
private fun AppleMusicQualityChip(
    currentFormat: FormatEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = remember(currentFormat.mimeType, currentFormat.codecs) {
        currentFormat.codecLabel()
    }
    val lossless = remember(currentFormat.codecs, currentFormat.mimeType) {
        currentFormat.isLossless()
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color.White.copy(alpha = 0.1f),
        border = BorderStroke(width = 1.dp, color = Color.White.copy(alpha = 0.13f)),
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Icon(
                painter = painterResource(
                    if (lossless) R.drawable.ic_mqa else R.drawable.player_graphic_eq,
                ),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.72f),
                modifier = Modifier.size(if (lossless) 18.dp else 15.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.72f),
                maxLines = 1,
            )
        }
    }
}
