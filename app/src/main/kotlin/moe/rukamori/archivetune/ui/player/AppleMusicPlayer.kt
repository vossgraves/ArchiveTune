/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * "Apple Music" player design: edge-to-edge artwork on top with a blurred continuation of the
 * artwork behind the lower controls (progressive-blur look), bold white title/artist with star and
 * "more" chips, a thin scrubber with elapsed/-remaining times, bare oversized transport glyphs, a
 * flat volume slider, and a bottom lyrics / output / queue icon row. Everything is tinted by the
 * artwork itself (no palette extraction needed — the blur provides the color).
 */

package moe.rukamori.archivetune.ui.player

import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.GraphicsLayerScope
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
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
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.LocalStableSystemBarsTopPadding
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AutoTranslateLyricsKey
import moe.rukamori.archivetune.constants.ThumbnailCornerRadiusKey
import moe.rukamori.archivetune.constants.TranslatorTargetLangKey
import moe.rukamori.archivetune.db.entities.FormatEntity
import moe.rukamori.archivetune.db.entities.LyricsEntity
import moe.rukamori.archivetune.db.entities.codecLabel
import moe.rukamori.archivetune.db.entities.isLossless
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.lyrics.LyricsUtils
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.component.BottomSheetPageState
import moe.rukamori.archivetune.ui.component.BottomSheetState
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.LyricsEnhanced
import moe.rukamori.archivetune.ui.component.LyricsV2
import moe.rukamori.archivetune.constants.LyricsMode
import moe.rukamori.archivetune.constants.LyricsModeKey
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.ui.menu.LyricsMenu
import moe.rukamori.archivetune.ui.menu.PlayerMenu
import moe.rukamori.archivetune.ui.menu.rememberCastPlayerMenuAction
import moe.rukamori.archivetune.ui.utils.ShowMediaInfo
import moe.rukamori.archivetune.ui.utils.highRes
import moe.rukamori.archivetune.utils.ImageBlurUtils
import moe.rukamori.archivetune.utils.isLocalMediaId
import moe.rukamori.archivetune.utils.makeTimeString
import moe.rukamori.archivetune.utils.rememberLowDataModeActive
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.LyricsMenuViewModel

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
    position: Long,
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

    // === Auto-hide player controls (always-on in Apple Music style) ===
    // The in-place Apple Music lyrics view ALWAYS auto-hides the bottom
    // controls (seekbar + transport + volume + action row) after 3 seconds.
    // Touching anywhere on the lyrics restores them and restarts the timer.
    // This is hardcoded behavior — there is no toggle in the LyricsMenu
    // overflow because the Apple Music style is designed to auto-hide.
    // The standalone LyricsScreen still has the toggle (for other player
    // styles that use it).
    var playerControlsExpanded by remember(mediaMetadata.id) { mutableStateOf(true) }
    var playerControlsVisibilityTick by remember(mediaMetadata.id) { mutableIntStateOf(0) }
    val autoHideDelayMs = 3_000L

    LaunchedEffect(lyricsOpen) {
        if (lyricsOpen) {
            playerControlsExpanded = true
            playerControlsVisibilityTick++
        } else {
            playerControlsExpanded = true
        }
    }
    LaunchedEffect(lyricsOpen, playerControlsVisibilityTick) {
        if (!lyricsOpen) return@LaunchedEffect
        playerControlsExpanded = true
        delay(autoHideDelayMs)
        playerControlsExpanded = false
    }
    val pokePlayerControlsVisibility = remember {
        {
            if (lyricsOpen) {
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

    // === Moving blur drift for the backdrop when lyrics is open ===
    // Mirrors the MovingBlurBackground from LyricsScreen: the blurred artwork
    // slowly drifts horizontally and vertically, creating an ambient motion
    // behind the lyrics. Only active when lyricsOpen = true.
    //
    // CRITICAL PERF: we keep the State<Float> objects (NOT `by` delegation) so
    // the animation values are read ONLY inside Modifier.graphicsLayer { }
    // lambdas (draw-phase deferred reads). Reading them during composition —
    // e.g. `val backdropDriftX = if (lyricsOpen) blurDriftX else 0f` — would
    // invalidate the ENTIRE AppleMusicPlayerContent composable every frame
    // (SharedTransitionLayout, AnimatedContent, ControlsColumn, and the inline
    // LyricsEnhanced all recompose at ~60fps), stealing the frame budget from
    // the karaoke syllable sweep. This was the root cause of the Apple-Music-
    // style-only lyrics jank — LyricsScreen.kt doesn't have it because its
    // drift lives in a separate MovingBlurBackground composable.
    val blurTransition = rememberInfiniteTransition(label = "am-lyrics-blur-drift")
    val blurDriftXState = blurTransition.animateFloat(
        initialValue = -80f,
        targetValue = 80f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 19_000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "am-lyrics-drift-x",
    )
    val blurDriftYState = blurTransition.animateFloat(
        initialValue = -60f,
        targetValue = 60f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 27_000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "am-lyrics-drift-y",
    )
    // Pre-compute dp→px once (graphicsLayer.translationX is in pixels). Density
    // doesn't change per-frame so this is a one-time composition-phase read.
    val driftDpToPx = with(LocalDensity.current) { 1.dp.toPx() }

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
    val currentLyrics by playerConnection.currentLyrics.collectAsState(initial = null)

    // ─── Automatic AI translation ───────────────────────────────────────
    // Mirrors the same LaunchedEffect in LyricsScreen.kt. The Apple Music
    // player uses an inline LyricsV2/LyricsEnhanced view (not LyricsScreen),
    // so without this effect, auto-translate only fires from the background
    // MusicService.onLyricsFetched() path — which is skipped if lyrics were
    // already cached before the user enabled auto-translate. This foreground
    // trigger ensures translation fires when the user opens the inline lyrics
    // view, matching the behavior of every other player style.
    val (autoTranslateLyrics) = rememberPreference(AutoTranslateLyricsKey, defaultValue = false)
    val (translatorTargetLang) = rememberPreference(TranslatorTargetLangKey, defaultValue = "")
    val lyricsMenuViewModel: LyricsMenuViewModel = hiltViewModel()
    LaunchedEffect(
        mediaMetadata.id,
        currentLyrics?.lyrics,
        currentLyrics?.source,
        autoTranslateLyrics,
        translatorTargetLang,
    ) {
        if (!autoTranslateLyrics) return@LaunchedEffect
        val snapshot = currentLyrics ?: return@LaunchedEffect
        val text = snapshot.lyrics ?: return@LaunchedEffect
        if (text.isBlank() || text == LyricsEntity.LYRICS_NOT_FOUND) return@LaunchedEffect
        if (snapshot.source == LyricsEntity.Source.AI_TRANSLATION.value) return@LaunchedEffect
        if (!LyricsUtils.shouldAutoTranslate(text, translatorTargetLang)) return@LaunchedEffect
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
            // When lyrics is open, the overflow menu shows the LyricsMenu
            // (Edit / Refetch / Translate / Sync offset / Search) instead of
            // the regular PlayerMenu. The "Show player controls" and
            // "Auto-hide player controls" toggles are NOT passed at all —
            // the in-place Apple Music lyrics view does not support auto-hide
            // (controls are always visible), so those toggles would be no-ops.
            // showControlsToggles = false hides them from the UI.
            menuState.show {
                LyricsMenu(
                    lyricsProvider = { currentLyrics },
                    mediaMetadataProvider = { mediaMetadata },
                    lyricsSyncOffset = lyricsSyncOffset,
                    onLyricsSyncOffsetChange = onLyricsSyncOffsetChange,
                    onDismiss = menuState::dismiss,
                    showControlsToggles = false,
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
            if (!isPreS || artworkUrl.isNullOrBlank() || videoShowing || canvasActive) {
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
            // Backdrop rendering — keeps the canvas ExoPlayer alive across
            // lyrics open/close to avoid the multi-second reload delay that
            // left a black gap behind the bottom controls.
            //
            // • COVER / QUEUE state (canvas) — render the live canvas with
            //   heavy blur (72.dp), fixed 1.2x scale, no drift. The canvas
            //   keeps playing continuously across COVER↔QUEUE morphs because
            //   it lives outside the SharedTransitionLayout / AnimatedContent.
            //
            // • LYRICS state (canvas) — the canvas is PAUSED (isPlaying=false)
            //   to free GPU for the karaoke syllable sweep, but the ExoPlayer
            //   instance is retained (no disposal). A static AsyncImage with
            //   blur + drift is overlaid ON TOP of the paused canvas so the
            //   user sees the moving-blur aesthetic. When lyrics closes, the
            //   canvas resumes instantly — no reload delay.
            //
            // • Non-canvas backdrop (no Spotify Canvas) — same as before:
            //   static album art with blur, drift enabled during lyrics.
            //
            // Helper: deferred-read graphicsLayer for the drift. Reads the
            // animation State<Float> inside the lambda (draw phase) so the
            // parent composable is NOT invalidated every frame.
            val driftGraphicsLayer: GraphicsLayerScope.() -> Unit = {
                // lyricsOpen is a stable Boolean state — reading it here is a
                // deferred read that only triggers a layer update on toggle.
                val active = lyricsOpen
                val scale = if (active) 1.4f else 1.2f
                scaleX = scale
                scaleY = scale
                if (active) {
                    // State.value reads are deferred to the draw phase.
                    translationX = blurDriftXState.value * driftDpToPx
                    translationY = blurDriftYState.value * driftDpToPx
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
            if (useCanvasBackdrop) {
                // Fallback: blurred album art BEHIND the canvas. Visible while
                // the canvas video is loading (isVideoReady = false → canvas
                // alpha = 0). Without this, the user sees a black gap behind
                // the bottom controls during the initial song load (when the
                // canvas ExoPlayer is first created and buffering). Once the
                // canvas is ready, it covers this fallback entirely.
                AsyncImage(
                    model = artworkRequest ?: artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .matchParentSize()
                            .then(
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    Modifier.blur(72.dp)
                                } else {
                                    Modifier
                                },
                            ).graphicsLayer {
                                scaleX = 1.2f
                                scaleY = 1.2f
                            },
                )
                // Canvas backdrop — the ExoPlayer is ALWAYS retained (never
                // disposed across lyrics open/close) so the canvas resumes
                // instantly when lyrics closes — no multi-second reload delay.
                //
                // PERFORMANCE (lyrics lag fix): the TextureView is HIDDEN
                // (`visible = !lyricsOpen`) when lyrics is open. A paused
                // TextureView with Modifier.blur(72.dp) still costs a full
                // per-frame GPU composite + blur pass because the RenderEffect
                // is re-applied every frame even when the surface content
                // hasn't changed. This steals the frame budget from the
                // karaoke syllable sweep, causing the "lyrics lag after ~20s"
                // symptom. Hiding the TextureView entirely frees that budget.
                // The static image overlay below visually replaces the canvas
                // so the user still sees the moving-blur aesthetic.
                CanvasArtworkPlayer(
                    primaryUrl = canvasPrimaryUrl,
                    fallbackUrl = canvasFallbackUrl,
                    isPlaying = isPlaying && !lyricsOpen,
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                    visible = !lyricsOpen,
                    modifier =
                        Modifier
                            .matchParentSize()
                            .blur(72.dp)
                            .graphicsLayer {
                                // Fixed 1.2x scale (no drift) — the canvas is
                                // paused during lyrics so drift would be wasted.
                                scaleX = 1.2f
                                scaleY = 1.2f
                            },
                )
                // Static image overlay for lyrics — rendered ON TOP of the
                // (paused) canvas so the user sees the moving-blur aesthetic.
                // Without this, the paused canvas's last frame would show.
                if (lyricsOpen) {
                    if (isPreS && preBlurredBitmap != null) {
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
                                    .then(
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                            Modifier.blur(64.dp)
                                        } else {
                                            Modifier
                                        },
                                    ).graphicsLayer(driftGraphicsLayer),
                        )
                    }
                }
            } else if (lyricsOpen) {
                // Non-canvas backdrop, LYRICS state: static image with blur + drift.
                if (isPreS && preBlurredBitmap != null) {
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
                                .then(
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        Modifier.blur(64.dp)
                                    } else {
                                        Modifier
                                    },
                                ).graphicsLayer(driftGraphicsLayer),
                    )
                }
            } else if (isPreS && preBlurredBitmap != null) {
                // Non-canvas backdrop, COVER/QUEUE state, pre-S with pre-blurred bitmap.
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
                // Non-canvas backdrop, COVER/QUEUE state, S+ or loading.
                AsyncImage(
                    model = artworkRequest ?: artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .matchParentSize()
                            .then(
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    Modifier.blur(72.dp)
                                } else {
                                    Modifier
                                },
                            ).graphicsLayer(driftGraphicsLayer),
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
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                )
                AppleMusicControlsColumn(
                    mediaMetadata = mediaMetadata,
                    isPlaying = isPlaying,
                    isLoading = isLoading,
                    canSkipPrevious = canSkipPrevious,
                    canSkipNext = canSkipNext,
                    sliderPosition = sliderPosition,
                    position = position,
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
                            .weight(1f)
                            .fillMaxHeight()
                            // NOTE: no navigationBarsPadding() here — contentBottomPadding
                            // already includes the system-bars bottom inset via
                            // collapsedBound (= dynamicQueuePeekHeight + systemBarsBottom).
                            // Adding navigationBarsPadding() on top double-counts the
                            // inset and makes the controls jump up when the nav bar
                            // appears.
                            .padding(bottom = contentBottomPadding),
                )
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
                BoxWithConstraints(modifier = Modifier.weight(1f)) {
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
                            // Crossfade with shared-element morph. The COVER fades out
                            // quickly (200ms) so the large square artwork disappears fast
                            // and the rounded mini artwork becomes visible sooner. The
                            // entering state (QUEUE/LYRICS) fades in over 600ms for a
                            // smooth reveal. This fixes the "thumbnail stays square for
                            // most of the transition" issue — the square COVER content
                            // is gone in 200ms instead of lingering for 600ms.
                            fadeIn(tween(600, easing = FastOutSlowInEasing)) togetherWith
                                fadeOut(tween(200, easing = FastOutSlowInEasing))
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
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .sharedBounds(
                                                rememberSharedContentState(key = "amCoverArt"),
                                                animatedVisibilityScope = this@AnimatedContent,
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
                                        animatedVisibilityScope = this@AnimatedContent,
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
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(maxHeight - miniHeaderHeight)
                                .offset(y = miniHeaderHeight)
                                .pointerInput(lyricsOpen) {
                                    if (!lyricsOpen) return@pointerInput
                                    awaitEachGesture {
                                        awaitFirstDown(requireUnconsumed = false)
                                        pokePlayerControlsVisibility()
                                    }
                                },
                    ) {
                        when (lyricsMode) {
                            LyricsMode.V2 -> LyricsV2(
                                sliderPositionProvider = lyricsPosProvider,
                                lyricsSyncOffset = lyricsSyncOffset,
                                modifier = Modifier.fillMaxSize(),
                            )
                            LyricsMode.ENHANCED -> LyricsEnhanced(
                                sliderPositionProvider = lyricsPosProvider,
                                lyricsSyncOffset = lyricsSyncOffset,
                                modifier = Modifier.fillMaxSize(),
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
                // Auto-hide: when lyrics is open, the controls auto-hide after 3s
                // (always-on in Apple Music style — no toggle). Touching the lyrics
                // overlay above restores them.
                // Slide requires an extra layout pass on top of the fade; skip it when
                // animations are reduced so the auto-hide/show cycle doesn't compete with
                // the karaoke lyrics view for frame budget on lower-end devices.
                AnimatedVisibility(
                    visible = !lyricsOpen || playerControlsExpanded,
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
                        position = position,
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
                // Honor the global thumbnail corner-radius preference (the same
                // one the "Adjust corner radius" dialog in Appearance settings
                // controls). The slider's max is 45dp, but for a ~320dp artwork
                // that would be over-round, so we cap at 32dp — matching V9's
                // RoundedCornerShape(30.dp) default while still letting the
                // user drop it to 0 for sharp corners.
                val (thumbnailCornerRadius, _) = rememberPreference(
                    ThumbnailCornerRadiusKey,
                    defaultValue = 16f,
                )
                val artworkCornerRadiusDp = thumbnailCornerRadius.coerceAtMost(32f).dp
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
                                }
                                .shadow(8.dp, RoundedCornerShape(artworkCornerRadiusDp))
                                .clip(RoundedCornerShape(artworkCornerRadiusDp)),
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
    position: Long,
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
            Column(Modifier.weight(1f)) {
                Text(
                    text = mediaMetadata.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier.clickable(
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
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            mediaMetadata.artists.firstOrNull()?.id?.let(titleActions.onArtistClick)
                        },
                )
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

    // Thin scrubber + elapsed / -remaining.
    Column {
        AppleMusicSeekBar(
            position = sliderPosition ?: position,
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
                text = makeTimeString(sliderPosition ?: position),
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
                text = "-" + makeTimeString((duration - (sliderPosition ?: position)).coerceAtLeast(0L)),
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
        Box(contentAlignment = Alignment.Center) {
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
                        rememberSharedContentState(key = "amCoverArt"),
                        animatedVisibilityScope = animatedVisibilityScope,
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
        Column(Modifier.weight(1f)) {
            Text(
                text = mediaMetadata.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier.clickable(
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
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        mediaMetadata.artists.firstOrNull()?.id?.let(titleActions.onArtistClick)
                    },
            )
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
