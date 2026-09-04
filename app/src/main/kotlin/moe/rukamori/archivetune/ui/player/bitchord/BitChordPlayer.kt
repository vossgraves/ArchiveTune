/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

/*
 * Bitchord player style — the player itself.
 *
 * Ported from BitChord (https://github.com/kushagrasinghx/BitChord),
 * ui/player/NowPlayingScreen.kt (layout, animations, gestures, dimensions,
 * transport, volume, lyric strip, lyrics panel, inline queue, hero artwork).
 *
 * Adaptations for ArchiveTune (each documented at its site):
 *  - data: BitChord's Song/queue/lyrics services are replaced by ArchiveTune's
 *    PlayerConnection (ExoPlayer timeline windows), MusicDatabase lyrics
 *    (parsed with LyricsUtils then mapped to BitChord's LyricLine), and
 *    FormatEntity for the codec/lossless line.
 *  - the motion-artwork (Canvas video) layer is omitted: ArchiveTune's canvas
 *    stack belongs to the other player styles, and the Bitchord style is kept
 *    fully self-contained per the player-style separation rule. The full-bleed
 *    still-artwork hero treatment is ported unchanged.
 *  - BitChord's AutoPlay (infinity) toggle + queue section are omitted:
 *    ArchiveTune has no autoplay engine. The bottom row keeps shuffle,
 *    repeat and queue.
 *  - window insets: the cached status-bar top (LocalStableSystemBarsTopPadding,
 *    which floors with the display cutout) replaces WindowInsets.statusBars so
 *    the player never collides with the notch while the status bar is hidden.
 *  - the sheet the player sits in is ArchiveTune's own BottomSheet; drags on
 *    the dismiss band are left unconsumed exactly as in BitChord so the sheet
 *    reads them and closes.
 *
 * Everything else — geometry, springs, haptics, the sleeve collapse, the queue
 * travel, the sweep, the shimmer — is BitChord's, at BitChord's dimensions.
 *
 * Belongs exclusively to the Bitchord player style; not shared with any other
 * player style, per the self-containment rule for player styles (2026-09-01).
 */

package moe.rukamori.archivetune.ui.player.bitchord

import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt
import moe.rukamori.archivetune.LocalStableSystemBarsTopPadding
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.db.entities.FormatEntity
import moe.rukamori.archivetune.db.entities.LyricsEntity
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.lyrics.LyricsUtils
import moe.rukamori.archivetune.constants.AutoTranslateExcludedLanguagesKey
import moe.rukamori.archivetune.constants.AutoTranslateLyricsKey
import moe.rukamori.archivetune.constants.TranslatorTargetLangKey
import moe.rukamori.archivetune.ui.component.BottomSheetPageState
import moe.rukamori.archivetune.ui.component.BottomSheetState
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.ui.utils.ShowMediaInfo
import moe.rukamori.archivetune.ui.menu.PlayerMenu
import moe.rukamori.archivetune.ui.menu.LyricsMenu
import moe.rukamori.archivetune.ui.utils.resize
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.LyricsMenuViewModel
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.ui.player.MeshBackdrop
import moe.rukamori.archivetune.ui.player.rememberMeshPalette
import moe.rukamori.archivetune.constants.LyricsMode
import moe.rukamori.archivetune.constants.LyricsModeKey
import moe.rukamori.archivetune.ui.component.LyricsEnhanced
import moe.rukamori.archivetune.ui.component.LyricsV2
import moe.rukamori.archivetune.utils.rememberEnumPreference
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.layout.onSizeChanged
import androidx.navigation.NavController
import androidx.datastore.preferences.core.booleanPreferencesKey

// ── Geometry & timing constants (verbatim from BitChord's NowPlayingScreen) ──

/** Comfortably over the sleeve's drawn size on a phone, without wasting bytes. */
internal const val ART_PX = 1200

/**
 * How close the player's reported position has to get to a released scrub
 * handle before the handle stops being drawn where it was dropped.
 */
internal const val SEEK_SETTLE_TOLERANCE_MS = 1_500L

/** Backstop for a seek that never settles. */
internal const val SEEK_SETTLE_TIMEOUT_MS = 4_000L

internal val THUMB_SIZE = 54.dp
internal val HEADER_HEIGHT = 60.dp
internal val ART_TITLE_GAP = 20.dp

/**
 * How long the sleeve takes to travel the whole way between the full player and
 * the queue's header, spent in proportion rather than in full.
 */
internal const val QUEUE_TRAVEL_MS = 420

/**
 * How far up the sleeve has to have been dragged for a release to carry on
 * opening the queue rather than falling back.
 */
internal const val QUEUE_CARRY_FRACTION = 0.3f

/** A flick this fast decides the queue on its own. */
internal const val QUEUE_FLICK_VELOCITY = 450f

/**
 * The handle strip above the artwork, which always hands drags to the sheet.
 */
internal val DISMISS_STRIP_HEIGHT = 44.dp

/** The breathing room above the sleeve, needed twice: once to apply, once to measure past. */
internal val ART_BOX_TOP_PAD = 14.dp

/**
 * Share of the motion-artwork banner's height given over to its dissolve.
 * Generous on purpose: the banner has no card edge to stop at.
 */
internal const val HERO_FADE_FRACTION = 0.42f

/** The player's side margin. Scrollable panels reach back across it. */
internal val PLAYER_GUTTER = 30.dp

/**
 * How wide the player's content is ever allowed to get. A sleeve and a volume
 * slider stretched right across a tablet aren't a bigger player, just a coarser
 * one; past this the column stops growing and centres itself instead.
 */
internal val PLAYER_MAX_WIDTH = 560.dp

/**
 * How far a tall screen is allowed to push the transport from the blocks either
 * side of it. The spare height has to land somewhere, and above and below the
 * play button is where it reads as room rather than as a hole.
 */
internal val CONTROL_GAP_SPREAD_MAX = 48.dp

/**
 * The spread the player settled on the last time it was laid out, so the first
 * frame of each open doesn't show the unspread gaps and then step to the real
 * ones. A cache of a measurement, not state anything observes.
 */
private var lastControlSpread: Dp = 0.dp

/** After this much of the track, previous restarts it instead of stepping back. */
internal const val BACK_RESTARTS_AFTER_MS = 10_000L

/** Share of a lyric line's own length spent fading out, and its bounds. */
internal const val LYRIC_FADE_FRACTION = 0.28f
internal const val LYRIC_FADE_MIN_MS = 160f
internal const val LYRIC_FADE_MAX_MS = 700f

/**
 * How far back the part of the playing line that hasn't been sung yet is held.
 * The strip above the scrubber gets less of a gap than the full panel.
 */
internal const val UNSUNG_ALPHA = 0.45f
internal const val UNSUNG_ALPHA_STRIP = 0.55f

/**
 * The bloom behind the line being sung, at its very strongest. Kept well under
 * half strength: the halo is drawn from the same white as the text, so at full
 * alpha it stops reading as light and starts reading as a second, badly
 * printed copy of the words.
 */
internal const val GLOW_ALPHA = 0.62f
internal val GLOW_RADIUS = 9.dp

/**
 * How far behind the sweep's leading edge the bloom reaches, at full strength.
 * The glow belongs to the word being sung, not to everything sung so far.
 */
internal val GLOW_TRAIL = 62.dp
internal const val GLOW_TRAIL_FLOOR = 0.55f

/**
 * Room reserved inside each copy of a line for the halo to spread into. Every
 * copy carries the same inset so they still lay out identically.
 */
internal val GLOW_ROOM = 10.dp

/**
 * How the answering vocal is drawn: smaller than the lead and a shade behind
 * it, the way Apple Music hangs a backing line under the one it answers.
 */
internal val BACKING_FONT_SIZE = 19.sp
internal val BACKING_LINE_HEIGHT = 24.sp
internal const val BACKING_ALPHA = 0.72f

/**
 * How the romanisation and translation voices are drawn (ArchiveTune
 * addition): a step below the answering vocal — smaller and dimmer still — so
 * the three tiers of a row read as lead, echo, gloss.
 */
internal val AUX_FONT_SIZE = 16.sp
internal val AUX_LINE_HEIGHT = 20.sp
internal const val AUX_ALPHA = 0.6f

/** Stands in for an instrumental stretch on the strip. */
internal const val INSTRUMENTAL_MARK = "Instrumental"

/**
 * Shown on the strip during the intro, before the first sung line — one picked
 * at random per track, so the wait for the vocals has some character to it.
 */
internal val INTRO_LINES = listOf(
    "Beat's landing",
    "Song's starting",
    "Intro's cooking",
    "Warming up",
    "Here we go",
    "Setting the mood",
    "Drums are in",
    "Bass first, words later",
    "Turn it up",
    "Vibe check",
    "Wait for it",
    "Feel that build",
    "Let it ride",
    "Just the groove for now",
    "Speakers breathing",
    "Rolling in",
    "Hold tight",
    "Riff o'clock",
    "Strings first",
    "Hook's on the way",
    "Eyes closed",
    "Loading the vibe",
    "Almost words",
    "Pure heat, no words",
    "Tuning in",
    "Buckle up",
    "Let it breathe",
    "That opening though",
    "Bass is talking",
    "Lyrics loading",
    "Give it a sec",
    "Building something",
    "Cue the vocals",
    "Slow burn",
    "First notes in",
    "Nod along",
    "Groove's on deck",
    "Melody first",
    "Ease into it",
    "Big things coming",
    "Stage is set",
    "The calm before",
    "Sit with it",
    "Any second now",
    "Volume up, phone down",
    "Drums doing the talking",
    "Locked in",
    "Something's brewing",
    "Finding its feet",
    "Deep breath",
)

/**
 * Shown on the strip while a lyrics lookup is still in flight — one picked
 * at random per track, in the same spirit as [INTRO_LINES].
 */
internal val LYRICS_LOADING_LINES = listOf(
    "Getting lyrics",
    "Chasing the words",
    "Digging up the lyrics",
    "Words incoming",
    "On the hunt for lyrics",
    "Fetching the verses",
    "Tracking down the words",
    "Lyrics loading",
    "Reading between the lines",
    "Scanning for lyrics",
    "Words on the way",
    "Looking this one up",
    "Checking the lyric sheet",
    "Pulling up the words",
    "Searching the songbook",
    "Lining up the lyrics",
    "One sec, finding the words",
    "Combing through for lyrics",
    "Lyrics inbound",
    "Sourcing the verses",
    "Cross-checking the words",
    "Rounding up the lyrics",
    "Text hunt in progress",
    "Syncing up the words",
    "Peeking at the lyric sheet",
    "Almost got the words",
    "Fishing for lyrics",
    "Grabbing the transcript",
    "Lyrics, one moment",
    "Tuning in the words",
    "Locating the verses",
    "Words are en route",
    "Checking the archives",
    "Piecing the lyrics together",
    "Loading up the words",
    "Lyric search underway",
    "Finding the right words",
    "Tracking the lyric sheet",
    "Verses incoming",
    "Getting the words lined up",
    "Hang tight, fetching lyrics",
    "Looking for the hook",
    "Words are loading",
    "Lyrics on their way",
    "Checking what's sung here",
    "Reading the room for lyrics",
    "Lyric lookup in progress",
    "Bringing up the words",
    "Just a sec, finding words",
    "Lyrics coming together",
)

internal const val LYRICS_UNAVAILABLE_HOLD_MS = 5_000L
internal const val LYRICS_UNAVAILABLE_FADE_MS = 900

// ── The player ────────────────────────────────────────────────────────────────

/**
 * The Bitchord player: Apple Music's Now Playing, closely — artwork that
 * shrinks when paused, a hairline scrubber with elapsed / remaining either
 * side, oversized transport glyphs, a volume capsule flanked by speaker
 * icons, and lyrics + queue along the bottom.
 */
@Composable
fun BitChordPlayerContent(
    mediaMetadata: MediaMetadata,
    isPlaying: Boolean,
    isLoading: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    position: Long,
    duration: Long,
    playerConnection: PlayerConnection,
    navController: NavController,
    state: BottomSheetState,
    menuState: MenuState,
    bottomSheetPageState: BottomSheetPageState,
    currentFormat: FormatEntity?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptics = rememberHaptics()
    val database = LocalDatabase.current
    val player = playerConnection.player

    val reduceAnimations = LocalAnimationsDisabled.current

    // ── Lyrics, out of ArchiveTune's database ──
    // The music service stores (and backfills) fetched lyrics with their
    // provider name; LYRICS_NOT_FOUND marks a lookup that came back empty.
    // Parsing routes through every format the lyrics table can hold — LRC,
    // QRC, TTML and plain text — the same way the other lyrics surfaces route,
    // so a plain-text or TTML result picked from the lyrics search sheet
    // actually renders here too (user report 2026-09-02: "if I choose a
    // different lyrics from a provider nothing shows up").
    val lyricsEntity by database.lyrics(mediaMetadata.id)
        .collectAsStateWithLifecycle(initialValue = null)
    val parsedLyrics = remember(lyricsEntity?.lyrics, mediaMetadata.duration) {
        val raw = lyricsEntity?.lyrics
        if (raw == null || raw == LyricsEntityNotFound) {
            null
        } else {
            parseBitChordLyrics(raw, mediaMetadata.duration)
        }
    }
    val lyrics = parsedLyrics?.lines
    val lyricsSynced = parsedLyrics?.isSynced ?: true
    val lyricsProviderName = lyricsEntity?.providerName.orEmpty()
    val lyricsUnavailable = lyricsEntity?.lyrics == LyricsEntityNotFound

    // ── Auto translation (ArchiveTune addition, user request 2026-09-02) ──
    // The same gate the standalone lyrics screen (LyricsScreen.kt) runs: when
    // "Auto translate lyrics" is on, and this track's lyrics are in a language
    // the user hasn't excluded, hand them to the AI translator and write the
    // result back into the lyrics table this player already observes. The
    // manual Translate action in the lyrics options menu shares the ViewModel,
    // so an undo here suppresses auto-translate exactly as it does there.
    val lyricsMenuViewModel: LyricsMenuViewModel = hiltViewModel()
    val (autoTranslateLyrics) = rememberPreference(AutoTranslateLyricsKey, defaultValue = false)
    val (translatorTargetLang) = rememberPreference(TranslatorTargetLangKey, defaultValue = "")
    val (autoTranslateExcludedLanguages) =
        rememberPreference(AutoTranslateExcludedLanguagesKey, defaultValue = emptySet())
    val translationDismissedMediaIds by lyricsMenuViewModel.translationDismissedMediaIds
        .collectAsStateWithLifecycle()
    LaunchedEffect(
        mediaMetadata.id,
        lyricsEntity?.lyrics,
        lyricsEntity?.source,
        autoTranslateLyrics,
        translatorTargetLang,
        autoTranslateExcludedLanguages,
        translationDismissedMediaIds,
    ) {
        if (!autoTranslateLyrics) return@LaunchedEffect
        val snapshot = lyricsEntity ?: return@LaunchedEffect
        val text = snapshot.lyrics
        if (text.isBlank() || text == LyricsEntity.LYRICS_NOT_FOUND) return@LaunchedEffect
        // Already AI-translated with real translation content — don't re-bill.
        if (snapshot.source == LyricsEntity.Source.AI_TRANSLATION.value &&
            LyricsUtils.hasTranslation(text)
        ) {
            return@LaunchedEffect
        }
        // The user undid a translation for this song — respect it.
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

    // ── The queue, out of the ExoPlayer timeline ──
    val queueWindows by playerConnection.queueWindows.collectAsStateWithLifecycle()
    val queueIndex by playerConnection.currentWindowIndex.collectAsStateWithLifecycle()
    val queue = remember(queueWindows) {
        queueWindows.map { window ->
            val meta = window.mediaItem.metadata
            BitChordQueueSong(
                id = window.mediaItem.mediaId,
                title = meta?.title.orEmpty(),
                artist = meta?.artists?.joinToString(", ") { it.name }.orEmpty(),
                thumbnailUrl = meta?.thumbnailUrl,
            )
        }
    }

    val repeatMode by playerConnection.repeatMode.collectAsStateWithLifecycle()
    val shuffleEnabled by playerConnection.shuffleModeEnabled.collectAsStateWithLifecycle()
    val currentSong by playerConnection.currentSong.collectAsStateWithLifecycle(initialValue = null)
    val currentSongLiked = currentSong?.song?.liked == true

    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableFloatStateOf(0f) }
    // The queue lives inside the player, Apple-style, rather than in a sheet.
    var queueOpen by remember { mutableStateOf(false) }
    var lyricsOpen by remember { mutableStateOf(false) }
    LaunchedEffect(mediaMetadata.id) { lyricsOpen = false }

    // Sync offset for the lyrics, held per track exactly as the standalone lyrics
    // screen holds it (Player.kt). The lyrics options bottom sheet (LyricsMenu)
    // edits it, and the panel + the current-lyric strip both read it so a nudge
    // moves the highlight the way it does everywhere else in the app.
    var lyricsSyncOffset by rememberSaveable(mediaMetadata.id) {
        mutableIntStateOf(0)
    }
    // The position the lyrics follow: the player's own, nudged by the offset.
    val lyricsPosition = (position + lyricsSyncOffset.toLong()).coerceAtLeast(0L)

    // Back out of the lyrics panel to the player, and only from the player
    // itself out to the mini player. The sheet the player is drawn in keeps
    // its own back handling while no panel is open (predictive-back shrink).
    BackHandler(enabled = lyricsOpen) { lyricsOpen = false }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val view = LocalView.current
        DisposableEffect(view, lyricsOpen) {
            val callback = if (lyricsOpen) {
                OverlayBack.register(view) { lyricsOpen = false }
            } else {
                null
            }
            onDispose { OverlayBack.unregister(view, callback) }
        }
    }

    // 0 = full sleeve, 1 = queue. Everything that moves reads off this.
    val queueSlide = remember { mutableFloatStateOf(0f) }
    val queueProgress = queueSlide.floatValue
    var queueDragging by remember { mutableStateOf(false) }
    var queueReleased by remember { mutableIntStateOf(0) }
    LaunchedEffect(queueOpen, queueDragging, queueReleased) {
        if (queueDragging) return@LaunchedEffect
        val target = if (queueOpen) 1f else 0f
        val from = queueSlide.floatValue
        if (from == target) return@LaunchedEffect
        animate(
            initialValue = from,
            targetValue = target,
            animationSpec = tween(
                durationMillis = (QUEUE_TRAVEL_MS * abs(target - from)).roundToInt(),
                easing = FastOutSlowInEasing,
            ),
        ) { value, _ -> queueSlide.floatValue = value }
    }

    // Horizontal fling anywhere on the player skips tracks; the artwork
    // follows the finger so the gesture has something to hold on to.
    val swipeThreshold = with(density) { 72.dp.toPx() }
    var swipeOffset by remember { mutableFloatStateOf(0f) }
    val swipeSettle by animateFloatAsState(
        targetValue = swipeOffset,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "swipeOffset",
    )

    // After releasing the scrubber the player needs to buffer before it
    // reports the new position. Keep showing where the user dropped it so the
    // handle doesn't snap back and then jump forward once loading finishes.
    var pendingSeek by remember { mutableStateOf<Float?>(null) }

    val fraction = if (duration > 0) position.toFloat() / duration else 0f
    val shown = when {
        scrubbing -> scrubValue
        pendingSeek != null -> pendingSeek!!
        else -> fraction.coerceIn(0f, 1f)
    }

    // Released as soon as the player's own position agrees with where the
    // handle was dropped — and unconditionally a few seconds later.
    LaunchedEffect(position, duration, pendingSeek) {
        val target = pendingSeek ?: return@LaunchedEffect
        if (duration > 0 && abs(position - (target * duration).toLong()) < SEEK_SETTLE_TOLERANCE_MS) {
            pendingSeek = null
        }
    }
    LaunchedEffect(pendingSeek) {
        if (pendingSeek == null) return@LaunchedEffect
        delay(SEEK_SETTLE_TIMEOUT_MS)
        pendingSeek = null
    }
    LaunchedEffect(mediaMetadata.id) { pendingSeek = null }

    // Signature Apple Music touch: the sleeve shrinks back while paused.
    val artScale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.86f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "artScale",
    )

    // ── Volume, exactly BitChord's own AudioManager approach ──
    val audioManager = remember(context) {
        context.getSystemService(AudioManager::class.java)
    }
    val maxVolume = remember(audioManager) {
        audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 15
    }
    val scope = rememberCoroutineScope()
    // Animatable rather than plain state: a hardware volume step is a jump of
    // 1/15th of the bar, which reads as a stutter unless it's tweened.
    val volume = remember {
        Animatable(
            (audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0).toFloat() / maxVolume,
        )
    }
    var volumeDragging by remember { mutableStateOf(false) }
    var systemVolume by remember { mutableFloatStateOf(volume.value) }

    // Glide to the level the system reports, but never fight the finger — a
    // drag writes the stream, which calls straight back through here.
    LaunchedEffect(systemVolume) {
        if (!volumeDragging) {
            volume.animateTo(systemVolume, tween(durationMillis = 220, easing = FastOutSlowInEasing))
        }
    }

    // Hardware volume keys and the system panel change the stream behind our
    // back — watch Settings for changes so the bar tracks them live.
    DisposableEffect(audioManager) {
        val observer = object : android.database.ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                val current = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: return
                systemVolume = current.toFloat() / maxVolume
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.System.CONTENT_URI,
            true,
            observer,
        )
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }

    // 0 = the ordinary square sleeve, 1 = the artwork as a full-bleed banner.
    val p by animateFloatAsState(
        targetValue = if (lyricsOpen || queueOpen) 1f else 0f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "sleeveCollapse",
    )
    // BitChord's fullBleedArtwork defaults to true; a phone-width player is
    // exactly the idiom it is drawn for.
    val heroMode = playerFillsWindow(LocalConfiguration.current.screenWidthDp.dp)
    // Whether there's a still image to blow out — a placeholder tile is a card
    // or it is nothing. Keyed on the artwork rather than on the track: two
    // tracks off one album share a cover.
    val artUrl = remember(mediaMetadata.id, mediaMetadata.thumbnailUrl) {
        mediaMetadata.thumbnailUrl?.resize(width = ART_PX, height = ART_PX, maxresAllowed = true)
    }
    var artLoaded by remember(artUrl) { mutableStateOf(false) }
    // Sticky, unlike [artLoaded]: the banner is the shape of the player rather
    // than a property of the track in it.
    var heroSettled by remember { mutableStateOf(false) }
    LaunchedEffect(artLoaded) {
        if (artLoaded) heroSettled = true
    }
    val heroT by animateFloatAsState(
        targetValue = if (heroMode && (artLoaded || heroSettled)) 1f else 0f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "heroCanvas",
    )

    /**
     * How much of the banner is actually on screen: its own fade, dissolved by
     * the collapse rather than after it, so one movement covers both.
     */
    val heroVisible = heroT * (1f - p)
    // How tall that banner is, worked out down in the layout where the sleeve's
    // own geometry is known.
    var heroHeight by remember { mutableStateOf(0.dp) }
    // ArchiveTune's cached status-bar top, floored with the display cutout, so
    // the banner never starts under the notch even with the status bar hidden.
    val statusBarTop = LocalStableSystemBarsTopPadding.current
    // What sits between the status bar and the artwork: the drag strip in the
    // sheet, plain padding in a pane. Read in three places which all have to
    // agree or the artwork and the credits under it move.
    val topStrip = DISMISS_STRIP_HEIGHT

    // The band of the player a vertical drag belongs to rather than to whatever
    // is under it: from the top of the artwork to the bottom of the credits, in
    // root coordinates.
    var dismissBandTop by remember { mutableFloatStateOf(0f) }
    var dismissBandBottom by remember { mutableFloatStateOf(0f) }
    val dismissBandSpace = remember { mutableStateOf<LayoutCoordinates?>(null) }

    val meshColors = rememberMeshPalette(artUrl)

    val onPlayPause = {
        if (player.isPlaying) player.pause() else player.play()
    }
    val onSeekFraction: (Float) -> Unit = { f ->
        // The scrubber is the one caller that knows where along the bar it
        // wants to go; the conversion uses the player's own freshest duration.
        val d = player.duration
        if (d > 0 && d != androidx.media3.common.C.TIME_UNSET) {
            player.seekTo((f * d).toLong())
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Keyed on the track: the backdrop drifts when the player opens and on
        // every skip, then rests.
        MeshBackdrop(
            palette = meshColors,
            trackKey = mediaMetadata.id,
            reduceAnimation = reduceAnimations,
        )

        // The artwork, edge to edge and running up behind the status bar,
        // dissolving into the backdrop where the sleeve's bottom edge would
        // have been. It lives out here rather than in the sleeve because that
        // is the only way to escape the player's side gutter and its
        // status-bar inset — a banner that stops short of either reads as a
        // misplaced card rather than as the artwork the screen is made of.
        if (heroHeight > 0.dp) {
            if (heroMode && (p < 0.5f || heroVisible > 0.001f)) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(artUrl)
                        .size(ART_PX)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .height(heroHeight)
                        .graphicsLayer {
                            alpha = heroVisible
                            // The DstIn mask below erases part of what this layer
                            // drew; without an offscreen buffer of its own it
                            // instead erases everything already on screen in its
                            // rect — the mesh backdrop included — which is what
                            // drew the black band and the hard cut at the banner's
                            // bottom edge (user report 2026-09-01: the artwork
                            // "doesn't blend with the bottom controls"). With
                            // Offscreen, the fade only fades the artwork, and the
                            // mesh gradient stays behind it exactly as in BitChord.
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color.Black, Color.Transparent),
                                    startY = size.height * (1f - HERO_FADE_FRACTION),
                                    endY = size.height,
                                ),
                                blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                            )
                        },
                )
            }

            // The clock, the signal bars and the drag handle are all white, and
            // the banner puts whatever the artwork happens to have up there
            // directly behind them — a bright frame or a pale sleeve leaves the
            // top of the screen unreadable. Faded in with the banner and gone
            // with it.
            if (heroVisible > 0.01f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .height(statusBarTop + topStrip)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.38f * heroVisible),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets(top = statusBarTop))
                .navigationBarsPadding()
                .pointerInput(Unit) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onDragCancel = { swipeOffset = 0f },
                        onDragEnd = {
                            // The same two buzzes the transport glyphs give, so
                            // swiping the sleeve and tapping skip feel like one
                            // gesture with two spellings.
                            when {
                                total <= -swipeThreshold && canSkipNext -> {
                                    haptics.play(Haptic.SkipNext)
                                    playerConnection.seekToNext()
                                }
                                total >= swipeThreshold && canSkipPrevious -> {
                                    haptics.play(Haptic.SkipPrevious)
                                    playerConnection.seekToPrevious()
                                }
                            }
                            swipeOffset = 0f
                        },
                        onHorizontalDrag = { _, delta ->
                            total += delta
                            // Damped: it's a hint, not a drag-to-position.
                            swipeOffset = total * 0.35f
                        },
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The only strip that passes drags through to the sheet, so the
            // player closes from the handle and the space around it — not from
            // a stray downward swipe on the artwork or the controls.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(topStrip),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .width(38.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.32f)),
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // Swallow vertical drags before the sheet can read them as
                    // "dismiss me". Children that scroll consume first, so the
                    // lists are unaffected. This sits outside the side padding
                    // on purpose: inside it, the two gutters were left as bare
                    // sheet, and a swipe that strayed into one closed the whole
                    // player instead of scrolling the lyrics or the queue.
                    //
                    // With one hole in it, and where that hole is depends on
                    // which screen of the player is up:
                    //
                    //  * The main player — the artwork-and-credits block. Down is
                    //    left unconsumed for the sheet to dismiss with, so the
                    //    player closes from the picture as well as from the
                    //    handle; up is taken here and drags the queue in.
                    //  * The queue or the lyrics — the header those panels sit
                    //    below, and nothing else. Down closes the player, up does
                    //    nothing: there is no sleeve left to pull away from.
                    //
                    // The header is worked out from the state rather than read
                    // off the sleeve, which is the whole point of doing it here.
                    .onGloballyPositioned { dismissBandSpace.value = it }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            // Unconsumed on purpose, as the blanket version was:
                            // the collapsed sleeve's own clickable — the way back
                            // out of the queue — has taken the press by the time
                            // an ancestor sees it.
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val space = dismissBandSpace.value
                            val y = space
                                ?.let { it.positionInRoot().y + down.position.y }
                                ?: down.position.y
                            // A panel is up from the moment it is asked for to
                            // the moment the sleeve has finished growing back —
                            // never mind where the sleeve is in between.
                            val panelUp = queueOpen || lyricsOpen ||
                                queueSlide.floatValue > 0.01f
                            val bandTop: Float
                            val bandBottom: Float
                            if (panelUp) {
                                bandTop = space?.positionInRoot()?.y ?: 0f
                                bandBottom = bandTop +
                                    (ART_BOX_TOP_PAD + HEADER_HEIGHT).toPx()
                            } else {
                                bandTop = dismissBandTop
                                bandBottom = dismissBandBottom
                            }
                            if (y >= bandTop && y <= bandBottom) {
                                if (!panelUp) {
                                    dragQueueIn(
                                        down = down,
                                        travel = bandBottom - bandTop -
                                            HEADER_HEIGHT.toPx(),
                                        slide = queueSlide,
                                        onHold = { queueDragging = it },
                                        onSettle = { open ->
                                            if (open != queueOpen) {
                                                haptics.play(
                                                    if (open) Haptic.Expand else Haptic.Tap,
                                                )
                                                queueOpen = open
                                            }
                                            queueReleased++
                                        },
                                    )
                                }
                                return@awaitEachGesture
                            }
                            // What detectVerticalDragGestures does, minus the
                            // callbacks: cross the slop, then hold the gesture
                            // to the end so nothing downstream of the first
                            // event reaches the sheet either.
                            val drag = awaitVerticalTouchSlopOrCancellation(down.id) { change, _ ->
                                change.consume()
                            }
                            if (drag != null) verticalDrag(drag.id) { it.consume() }
                        }
                    }
                    .padding(horizontal = PLAYER_GUTTER),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
            var controlSpread by remember { mutableStateOf(lastControlSpread) }
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = PLAYER_MAX_WIDTH)
                    .fillMaxWidth()
                    .padding(top = ART_BOX_TOP_PAD, bottom = 18.dp),
            ) {
                // The height this box would have if the controls at the foot of
                // the screen were at their natural size. They aren't: they are
                // holding [controlSpread] of extra gap, which came out of here,
                // so adding it back cancels the only thing down there that
                // depends on what is decided up here.
                val roomy = maxHeight + if (lyricsOpen) 0.dp else controlSpread
                // The sleeve is square, so it is bounded by whichever of the
                // two axes runs out first: the player's width on a phone, or —
                // on a tablet, where there is width to spare — the height left
                // over once the credits row and the gap above it have had
                // theirs.
                val wantArt = minOf(maxWidth, roomy - ART_TITLE_GAP - HEADER_HEIGHT)
                // Held to what the box has actually got, for the single frame it
                // takes the gaps below to catch up with a change in their own
                // height.
                val fullArt = minOf(wantArt, maxHeight - ART_TITLE_GAP - HEADER_HEIGHT)
                    .coerceAtLeast(THUMB_SIZE)
                // What's left over once the sleeve, the gap and the credits have
                // had theirs. Handed to the two gaps around the transport row
                // instead, which is where a tall screen should be doing its
                // breathing.
                val slack = (roomy - wantArt - ART_TITLE_GAP - HEADER_HEIGHT)
                    .coerceAtLeast(0.dp)
                if (!lyricsOpen) {
                    val target = with(density) {
                        val half = slack
                            .coerceAtMost(CONTROL_GAP_SPREAD_MAX * 2)
                            .toPx()
                            .div(2f)
                            .roundToInt()
                        (half * 2).toDp()
                    }
                    // Stepped towards [target] rather than jumped there in one
                    // grant, so a late cancellation decays instead of standing.
                    val granted = with(density) {
                        val steppedPx = (controlSpread.toPx() +
                            (target.toPx() - controlSpread.toPx()) * 0.4f)
                            .roundToInt()
                        steppedPx.toDp()
                    }
                    if (granted != controlSpread) {
                        SideEffect {
                            controlSpread = granted
                            lastControlSpread = granted
                        }
                    }
                }
                // Artwork and the title row travel together as one block, so
                // the pair sits centred while the queue is closed.
                val groupTop = (maxHeight - fullArt - ART_TITLE_GAP - HEADER_HEIGHT)
                    .coerceAtLeast(0.dp) / 2
                val artSize = lerp(fullArt, THUMB_SIZE, p)
                val artTop = lerp(groupTop, 0.dp, p)
                // Expanded and height-bound, the sleeve is narrower than the
                // player and has to be centred in it; collapsed, it belongs
                // hard against the left edge with the credits beside it.
                val artStart = lerp((maxWidth - fullArt) / 2, 0.dp, p)
                val titleTop = lerp(groupTop + fullArt + ART_TITLE_GAP, 0.dp, p)
                val titleStart = lerp(0.dp, THUMB_SIZE + 12.dp, p)

                // How far down the *screen* the sleeve's bottom edge sits, which
                // is where the full-bleed banner has to stop for the credits
                // below it not to move when it appears.
                val bannerBottom = statusBarTop + topStrip + ART_BOX_TOP_PAD +
                    groupTop + fullArt + ART_TITLE_GAP / 2
                if (bannerBottom != heroHeight) {
                    SideEffect { heroHeight = bannerBottom }
                }

                // Empty state lives on this Box, not the AsyncImage: a
                // background *and* a painter both trying to fill the same
                // clipped shape is what read as two overlapping squares
                // whenever there was nothing to paint.
                Box(
                    modifier = Modifier
                        // The lambda overload deliberately: the Dp one reads
                        // its arguments at composition, so an animated offset
                        // recomposes and re-measures this Box — cover, clip and
                        // all — once per frame.
                        .offset { IntOffset(artStart.roundToPx(), artTop.roundToPx()) }
                        .size(artSize)
                        // Where the dismiss band starts. Read here, above the
                        // paused shrink below, so the band covers the sleeve's
                        // slot rather than the 86% of it that is drawn while
                        // paused.
                        .onGloballyPositioned { dismissBandTop = it.boundsInRoot().top }
                        .graphicsLayer {
                            // The paused shrink and the swipe nudge only make
                            // sense on the full sleeve.
                            val idle = artScale + (1f - artScale) * p
                            scaleX = idle
                            scaleY = idle
                            translationX = swipeSettle * (1f - p)
                        }
                        // Collapsed, the sleeve is the way back: tapping the
                        // thumbnail puts the queue or the lyrics away again.
                        .then(
                            if (queueOpen || lyricsOpen) {
                                Modifier.clickable {
                                    queueOpen = false
                                    lyricsOpen = false
                                }
                            } else {
                                Modifier
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    // The sleeve proper. Held fully opaque until this track's
                    // own art is in, regardless of [heroT]: the banner is
                    // sticky across skips by design, but its still image is
                    // not — a new track's cover has to come from somewhere
                    // while the banner waits on Coil, and the sleeve
                    // underneath, with its loading icon, is that somewhere.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = if (artLoaded) 1f - heroVisible else 1f }
                            // A drop shadow grounds a photo; on the flat
                            // placeholder tile it has nothing to sit behind, so
                            // it just reads as a second, darker square ringing
                            // the first. Only cast it once there's actually art.
                            .shadow(
                                if (artLoaded) lerp(14.dp, 6.dp, p) else 0.dp,
                                RoundedCornerShape(lerp(10.dp, 7.dp, p)),
                            )
                            .clip(RoundedCornerShape(lerp(10.dp, 7.dp, p)))
                            .background(Color.Black.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!artLoaded) {
                            Icon(
                                imageVector = BitChordIcons.MusicNote,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.35f),
                                modifier = Modifier.size(lerp(40.dp, 20.dp, p)),
                            )
                        }
                        AsyncImage(
                            // Decode at the sleeve's *expanded* size, always.
                            // Coil otherwise sizes the decode to however large
                            // this is when the request goes out — and changing
                            // track from the queue does that while the sleeve is
                            // collapsed to a thumbnail, leaving a
                            // thumbnail-sized bitmap to be blown back up when
                            // the queue closes.
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(artUrl)
                                .size(ART_PX)
                                .build(),
                            contentDescription = null,
                            // Video thumbnails are 16:9; letterboxing them inside
                            // the square sleeve looks like a broken frame.
                            contentScale = ContentScale.Crop,
                            onState = { artLoaded = it is AsyncImagePainter.State.Success },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    // The measured codec/quality line used to be drawn here too, pinned to the
                    // sleeve's bottom edge. It read as a caption floating loose over the artwork,
                    // anchored to nothing the eye could see, and it duplicated LosslessOrStats in
                    // the transport row below — which is where the same information already sits,
                    // in a slot that visibly holds it.
                }

                // Sits in the gap under the sleeve, clear of its rounded
                // corners and shadow — just a glyph that fades in with the drag
                // to hint which way a release would skip.
                val swipeHintProgress = (abs(swipeSettle) / swipeThreshold)
                    .coerceIn(0f, 1f) * (1f - p)
                if (swipeHintProgress > 0.01f) {
                    val showNext = swipeSettle < 0f
                    val enabled = if (showNext) canSkipNext else canSkipPrevious
                    Icon(
                        imageVector = if (showNext) Icons.Rounded.FastForward else Icons.Rounded.FastRewind,
                        contentDescription = null,
                        tint = Color.White.copy(
                            alpha = swipeHintProgress * if (enabled) 0.85f else 0.3f,
                        ),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = artTop + artSize + (ART_TITLE_GAP - 16.dp) / 2)
                            .size(16.dp),
                    )
                }

                // ---- Title + menu ----
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = titleTop)
                        .padding(start = titleStart)
                        .height(HEADER_HEIGHT)
                        // Where the dismiss band ends — see its top on the
                        // artwork above. Taken from the row rather than added
                        // up from the sleeve so the gap between the two is
                        // inside the band as well.
                        .onGloballyPositioned { dismissBandBottom = it.boundsInRoot().bottom },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        // Shrinks as the header collapses, so the queue's
                        // heading doesn't have to compete with it.
                        val titleSize = lerp(20.sp, 16.sp, p)
                        Text(
                            text = mediaMetadata.title,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = titleSize,
                            ),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.opensPage(
                                mediaMetadata.album?.id,
                                onOpen = { navController.navigate("album/${it}") },
                            ),
                        )
                        Text(
                            text = mediaMetadata.artists.joinToString(", ") { it.name },
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.W500,
                                fontSize = titleSize,
                            ),
                            color = Color.White.copy(alpha = 0.55f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.opensPage(
                                mediaMetadata.artists.firstOrNull()?.id,
                                onOpen = { navController.navigate("artist/${it}") },
                            ),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    // Beside the credits rather than down in the toggle row:
                    // liking is about *this song*, and the row below is about
                    // how the queue plays.
                    CircleGlyph(
                        icon = if (currentSongLiked) BitChordIcons.HeartFilled else BitChordIcons.Heart,
                        contentDescription = if (currentSongLiked) "Remove from Liked Music" else "Like",
                        onClick = { playerConnection.toggleLike() },
                        active = currentSongLiked,
                        haptic = if (currentSongLiked) Haptic.ToggleOff else Haptic.ToggleOn,
                    )
                    Spacer(Modifier.width(8.dp))
                    CircleGlyph(
                        icon = Icons.Rounded.MoreHoriz,
                        contentDescription = "More",
                        onClick = {
                            menuState.show {
                                PlayerMenu(
                                    mediaMetadata = mediaMetadata,
                                    navController = navController,
                                    playerBottomSheetState = state,
                                    onShowDetailsDialog = {
                                        bottomSheetPageState.show {
                                            ShowMediaInfo(mediaMetadata.id)
                                        }
                                    },
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                    )
                }

                if (lyricsOpen) {
                    // The app's own lyrics view, in whichever style the Lyrics settings select —
                    // the same component every other player style opens.
                    //
                    // BitChord shipped its own panel, which swept the highlight through a line by
                    // fractional CHARACTER index: within a word it interpolated linearly across
                    // that word's span, so the light crept through the middle of letters instead of
                    // landing on words. It also ignored the lyrics-style preference outright, so
                    // choosing Enhanced or Spotify changed every surface in the app except this
                    // one. Routing to the shared renderer fixes both, and drops ~450 lines of a
                    // second implementation of scrolling, follow, tap-to-seek and romanisation.
                    //
                    // The one-line strip on the collapsed player keeps BitChord's sweep: it shows a
                    // single line with no list around it, which is what that treatment was for.
                    val lyricsMode by rememberEnumPreference(LyricsModeKey, LyricsMode.V2)
                    val panelModifier = Modifier
                        .fillMaxSize()
                        .padding(top = HEADER_HEIGHT + 10.dp)
                        // Arrives once the sleeve has finished collapsing into the header, the
                        // same beat the queue below already waits for — fading lyrics in over a
                        // sleeve still mid-collapse doubled the same movement in two places on
                        // screen at once.
                        .graphicsLayer {
                            alpha = ((p - 0.45f) / 0.55f).coerceIn(0f, 1f)
                            translationY = (1f - p) * 26.dp.toPx()
                        }
                    // Null unless the user is scrubbing, matching LyricsScreen: the renderers run
                    // their own frame clock off the player, and a polled position would step.
                    val lyricsPositionProvider = remember { { null as Long? } }
                    when (lyricsMode) {
                        LyricsMode.ENHANCED ->
                            LyricsEnhanced(
                                sliderPositionProvider = lyricsPositionProvider,
                                lyricsSyncOffset = lyricsSyncOffset,
                                modifier = panelModifier,
                                textColorOverride = Color.White,
                            )

                        LyricsMode.SPOTIFY ->
                            LyricsV2(
                                sliderPositionProvider = lyricsPositionProvider,
                                lyricsSyncOffset = lyricsSyncOffset,
                                modifier = panelModifier,
                                textColorOverride = Color.White,
                                spotifyStyle = true,
                            )

                        LyricsMode.V2 ->
                            LyricsV2(
                                sliderPositionProvider = lyricsPositionProvider,
                                lyricsSyncOffset = lyricsSyncOffset,
                                modifier = panelModifier,
                                textColorOverride = Color.White,
                            )
                    }
                }

                // Toggles and the queue arrive after the sleeve has finished
                // travelling, and leave before it starts coming back.
                if (!lyricsOpen && queueProgress > 0.01f) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = HEADER_HEIGHT + 10.dp)
                            .graphicsLayer {
                                alpha = ((queueProgress - 0.45f) / 0.55f).coerceIn(0f, 1f)
                                translationY = (1f - queueProgress) * 26.dp.toPx()
                            },
                    ) {
                        InlineQueue(
                            queue = queue,
                            currentIndex = queueIndex,
                            onJumpTo = { index -> player.seekTo(index, 0) },
                            onRemove = { index -> player.removeMediaItem(index) },
                            onMove = { from, to -> player.moveMediaItem(from, to) },
                            onClear = {
                                // Keep the playing track, drop everything else.
                                val size = player.mediaItemCount
                                for (i in (size - 1) downTo (queueIndex + 1)) {
                                    player.removeMediaItem(i)
                                }
                                for (i in (queueIndex - 1) downTo 0) {
                                    player.removeMediaItem(i)
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // ---- Bottom: lyric strip, scrubber, transport, volume, toggles ----
            // One block, measured at its natural height and pinned to the foot
            // of the player. Whatever is left over above it is the artwork's,
            // which is what keeps this row of controls in the same place on
            // every screen instead of being shoved off the bottom of a tall one.
            Column(
                modifier = Modifier
                    .widthIn(max = PLAYER_MAX_WIDTH)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
            // Current lyric, one line, directly above the scrubber. It stays in
            // the layout — and stays fully visible — whether or not the queue
            // is open: dropping it would shorten this block and the controls
            // under it would jump the moment the queue started sliding in.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // The slider's touch target reaches ~13dp above the drawn
                    // bar, so the strip reads as further off it than it is.
                    .offset(y = 6.dp),
            ) {
                if (!lyrics.isNullOrEmpty()) {
                    CurrentLyricLine(
                        lines = lyrics,
                        trackKey = mediaMetadata.id,
                        positionMs = lyricsPosition,
                        isPlaying = isPlaying,
                        durationMs = duration,
                        // Still visible over the queue, so still a valid way
                        // in: opens the same full lyrics panel it always has,
                        // closing the queue behind it.
                        onClick = {
                            queueOpen = false
                            lyricsOpen = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        synced = lyricsSynced,
                    )
                } else if (lyricsUnavailable) {
                    LyricsUnavailableLine(
                        trackKey = mediaMetadata.id,
                        modifier = Modifier.fillMaxWidth(),
                        // The strip is the way into the lyrics page when this
                        // track has no lyrics at all — the panel's ellipsis
                        // button is where refetch / search live (user request
                        // 2026-09-02).
                        onClick = { lyricsOpen = true },
                    )
                } else {
                    LyricsLoadingLine(
                        trackKey = mediaMetadata.id,
                        modifier = Modifier.fillMaxWidth(),
                        // Same: the loading line is tappable so the lyrics
                        // page is reachable mid-lookup (user request
                        // 2026-09-02).
                        onClick = { lyricsOpen = true },
                    )
                }
            }
            ThinSlider(
                value = shown,
                onValueChange = {
                    scrubbing = true
                    scrubValue = it
                },
                onValueChangeFinished = {
                    // On release only. Ticking the whole way along the bar turns
                    // a scrub into a rattle, and the beat that matters is the
                    // one that says where the playhead landed.
                    haptics.play(Haptic.Select)
                    pendingSeek = scrubValue
                    onSeekFraction(scrubValue)
                    scrubbing = false
                },
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // The slider's touch target extends well past the drawn
                    // bar, so pull the labels back up under it.
                    .offset(y = (-9).dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = formatTime((shown * duration).toLong()),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.55f),
                    )
                    Text(
                        text = "-" + formatTime(duration - (shown * duration).toLong()),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.55f),
                    )
                }
                // Pinned to the box's own center rather than squeezed into the
                // gap between the two timestamps: that gap's width changes by a
                // digit's worth every time a minute rolls over.
                LosslessOrStats(
                    isLoading = isLoading,
                    format = currentFormat,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 8.dp),
                )
            }

            if (lyricsOpen) {
                Spacer(Modifier.height(16.dp))
                // The credit, and beside it the way out. Tapping the sleeve
                // above also closes the panel, but that is an invisible target
                // you have to be told about; the button says so.
                //
                // The ellipsis button beside the close button is the way into
                // the lyrics options bottom sheet — the same slide-up LyricsMenu
                // (edit / refetch / translate / AI romanise / search / sync
                // offset) the standalone lyrics page opens for the other player
                // styles, so the Bitchord style's lyrics page has it too (user
                // request 2026-09-01). The menu writes straight into the lyrics
                // table and the sync-offset state, both of which this panel
                // already follows, so refetching or editing updates the panel
                // live.
                Row(
                    modifier = Modifier.height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(percent = 50))
                            .background(Color.White.copy(alpha = 0.10f))
                            .padding(horizontal = 18.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = when {
                                lyricsProviderName.isNotBlank() -> "Lyrics by $lyricsProviderName"
                                lyrics == null -> "No lyrics found"
                                else -> "Lyrics"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            // Height from the row, width from the height: a
                            // circle, not an oval, whatever the pill measures.
                            .fillMaxHeight()
                            .aspectRatio(1f, matchHeightConstraintsFirst = true)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.10f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                haptics.play(Haptic.Tap)
                                menuState.show {
                                    LyricsMenu(
                                        lyricsProvider = { lyricsEntity },
                                        mediaMetadataProvider = { mediaMetadata },
                                        lyricsSyncOffset = lyricsSyncOffset,
                                        onLyricsSyncOffsetChange = { lyricsSyncOffset = it },
                                        showPlayerControlsState = null,
                                        onShowPlayerControlsChange = null,
                                        onAutoHidePlayerControlsChange = {},
                                        onDismiss = menuState::dismiss,
                                        showControlsToggles = false,
                                    )
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreHoriz,
                            contentDescription = "Lyrics options",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            // Height from the row, width from the height: a
                            // circle, not an oval, whatever the pill measures.
                            .fillMaxHeight()
                            .aspectRatio(1f, matchHeightConstraintsFirst = true)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.10f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                haptics.play(Haptic.Tap)
                                lyricsOpen = false
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close lyrics",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
            } else {

            // The transport rides midway between the two blocks it separates:
            // the scrubber above it, and the volume bar and toggle row below,
            // which sit close enough together to read as one.
            Spacer(Modifier.height(14.dp + controlSpread / 2))

            // ---- Transport ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TransportGlyph(
                    icon = Icons.Rounded.FastRewind,
                    contentDescription = "Previous",
                    size = 46.dp,
                    onClick = { playerConnection.seekToPrevious() },
                    // Lit whenever back has something to do — either a track to
                    // step to, or enough elapsed for it to restart this one.
                    enabled = canSkipPrevious || position > BACK_RESTARTS_AFTER_MS,
                    haptic = Haptic.SkipPrevious,
                )
                // While the stream URL resolves and buffers, the play glyph
                // would be a lie — show progress instead.
                if (isLoading) {
                    // Same footprint as TransportGlyph(62.dp) — a smaller box
                    // here would shunt everything below it on every load.
                    Box(Modifier.size(74.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(38.dp),
                        )
                    }
                } else {
                    TransportGlyph(
                        icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        size = 62.dp,
                        onClick = onPlayPause,
                        haptic = if (isPlaying) Haptic.Pause else Haptic.Resume,
                    )
                }
                TransportGlyph(
                    icon = Icons.Rounded.FastForward,
                    contentDescription = "Next",
                    size = 46.dp,
                    onClick = { playerConnection.seekToNext() },
                    enabled = canSkipNext,
                    haptic = Haptic.SkipNext,
                )
            }

            Spacer(Modifier.height(18.dp + controlSpread / 2))

            // ---- Volume ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.VolumeDown,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                ThinSlider(
                    value = volume.value,
                    onValueChange = {
                        volumeDragging = true
                        // Follow the finger exactly; only external changes tween.
                        scope.launch { volume.snapTo(it) }
                        audioManager?.setStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            (it * maxVolume).roundToInt(),
                            0,
                        )
                    },
                    onValueChangeFinished = { volumeDragging = false },
                    idleHeight = 6.dp,
                    activeHeight = 10.dp,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                Icon(
                    Icons.AutoMirrored.Rounded.VolumeUp,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(Modifier.height(24.dp))

            // ---- Shuffle · Repeat · Queue ----
            // These live here rather than in the queue panel so their state is
            // readable without opening anything.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BottomGlyph(
                    icon = BitChordIcons.Shuffle,
                    contentDescription = if (shuffleEnabled) "Shuffle on" else "Shuffle off",
                    onClick = { player.shuffleModeEnabled = !shuffleEnabled },
                    highlighted = shuffleEnabled,
                    haptic = if (shuffleEnabled) Haptic.ToggleOff else Haptic.ToggleOn,
                )
                BottomGlyph(
                    icon = if (repeatMode == Player.REPEAT_MODE_ONE) null else BitChordIcons.Repeat,
                    label = if (repeatMode == Player.REPEAT_MODE_ONE) "1" else null,
                    contentDescription = when (repeatMode) {
                        Player.REPEAT_MODE_ONE -> "Repeat one"
                        Player.REPEAT_MODE_ALL -> "Repeat all"
                        else -> "Repeat off"
                    },
                    onClick = {
                        player.repeatMode = when (repeatMode) {
                            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                            else -> Player.REPEAT_MODE_OFF
                        }
                    },
                    highlighted = repeatMode != Player.REPEAT_MODE_OFF,
                    // Three states, so the buzz tracks the edges of the cycle.
                    haptic = when (repeatMode) {
                        Player.REPEAT_MODE_OFF -> Haptic.ToggleOn
                        Player.REPEAT_MODE_ONE -> Haptic.ToggleOff
                        else -> Haptic.Select
                    },
                )
                BottomGlyph(
                    icon = Icons.AutoMirrored.Rounded.QueueMusic,
                    contentDescription = "Up next",
                    onClick = {
                        lyricsOpen = false
                        queueOpen = !queueOpen
                    },
                    highlighted = queueOpen,
                    haptic = if (queueOpen) Haptic.Tap else Haptic.Expand,
                )
            }

            Spacer(Modifier.height(18.dp))
            }
            }
            }
        }
    }
}

/** The LyricsEntity's not-found sentinel. */
private const val LyricsEntityNotFound = "LYRICS_NOT_FOUND"

/** Whether a player given the whole of a window this wide is still narrow enough
 * to run its artwork edge to edge. */
internal fun playerFillsWindow(windowWidth: Dp): Boolean =
    windowWidth <= PLAYER_MAX_WIDTH + PLAYER_GUTTER * 2

// ── Glyphs & helpers (verbatim from BitChord's NowPlayingScreen.kt) ───────────

/**
 * The upward half of the sleeve's vertical gesture: dragged up, the artwork
 * block pulls the queue in behind it, following the finger the whole way and
 * settling to whichever end it was nearer on release.
 *
 * Downward is deliberately not ours. The sheet the player sits in is what closes
 * when the sleeve is dragged that way, and it can only read a drag it was
 * allowed to see — so a downward crossing of the touch slop is left entirely
 * alone and this returns having consumed nothing at all.
 */
private suspend fun AwaitPointerEventScope.dragQueueIn(
    down: PointerInputChange,
    travel: Float,
    slide: MutableFloatState,
    onHold: (Boolean) -> Unit,
    onSettle: (Boolean) -> Unit,
) {
    // A block with nowhere to travel — a player not yet measured — would divide
    // by nothing and snap the queue open on the first pixel of movement.
    if (travel < 1f) return

    var pulled = 0f
    val drag = awaitVerticalTouchSlopOrCancellation(down.id) { change, overSlop ->
        if (overSlop < 0f) {
            pulled = -overSlop
            change.consume()
        }
    }
    if (drag == null || pulled <= 0f) return

    onHold(true)
    val velocity = VelocityTracker()
    velocity.addPointerInputChange(drag)
    slide.floatValue = (pulled / travel).coerceIn(0f, 1f)
    verticalDrag(drag.id) { change ->
        velocity.addPointerInputChange(change)
        pulled -= change.positionChange().y
        slide.floatValue = (pulled / travel).coerceIn(0f, 1f)
        change.consume()
    }

    // A flick decides on its own — it says "open" without asking the finger to
    // travel at all. Anything slower goes to whichever end it got nearer to.
    val flick = -velocity.calculateVelocity().y
    val open = when {
        flick >= QUEUE_FLICK_VELOCITY -> true
        flick <= -QUEUE_FLICK_VELOCITY -> false
        else -> slide.floatValue >= QUEUE_CARRY_FRACTION
    }
    onHold(false)
    onSettle(open)
}

/**
 * Translucent circular button used for the track menu and the like control.
 *
 * [active] brightens the disc rather than only the glyph: this sits on album
 * artwork of any colour, and a white icon on a white-ish sleeve has no tint
 * change left to make. The filled heart carries the state as a shape too.
 */
@Composable
private fun CircleGlyph(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    active: Boolean = false,
    haptic: Haptic = Haptic.Tap,
) {
    val haptics = rememberHaptics()
    val discAlpha by animateFloatAsState(
        targetValue = if (active) 0.34f else 0.18f,
        label = "glyphDisc",
    )
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = discAlpha))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                haptics.play(haptic)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(19.dp),
        )
    }
}

/**
 * Transport / bottom glyphs. The circular clip belongs on the touch target,
 * never on the [Icon] — clipping the icon itself shaves the corners off wide
 * glyphs like fast-forward and the queue list.
 */
@Composable
private fun TransportGlyph(
    icon: ImageVector,
    contentDescription: String,
    size: Dp,
    onClick: () -> Unit,
    enabled: Boolean = true,
    haptic: Haptic = Haptic.Tap,
) {
    val haptics = rememberHaptics()
    // Faded rather than hidden: the row keeps its shape at the ends of a queue.
    val alpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.3f,
        label = "transportAlpha",
    )
    Box(
        modifier = Modifier
            .size(size + 12.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
            ) {
                haptics.play(haptic)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White.copy(alpha = alpha),
            modifier = Modifier.size(size),
        )
    }
}

@Composable
private fun BottomGlyph(
    icon: ImageVector?,
    contentDescription: String,
    onClick: () -> Unit,
    highlighted: Boolean = false,
    haptic: Haptic = Haptic.Tap,
    label: String? = null,
) {
    val haptics = rememberHaptics()
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(
                if (highlighted) Color.White.copy(alpha = 0.20f) else Color.Transparent,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                haptics.play(haptic)
                onClick()
            }
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        val tint = Color.White.copy(alpha = if (highlighted) 1f else 0.75f)
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(26.dp),
            )
        } else if (label != null) {
            Text(
                text = label,
                color = tint,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** A credit that links somewhere, when [browseId] is known. */
private fun Modifier.opensPage(browseId: String?, onOpen: (String) -> Unit): Modifier =
    if (browseId == null) {
        this
    } else {
        clip(RoundedCornerShape(6.dp)).clickable { onOpen(browseId) }
    }

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return "%d:%02d".format(Locale.ROOT, minutes, seconds)
}

// ── The codec / quality badge (adapted to ArchiveTune's FormatEntity) ─────────

/**
 * The gap between the two timestamps under the seek bar: just the "Lossless"
 * badge when one applies, and nothing otherwise. The measured stats line lives
 * inside the sleeve instead — the badge is a claim, the sleeve is where the
 * evidence is.
 */
@Composable
private fun LosslessOrStats(
    isLoading: Boolean,
    format: FormatEntity?,
    modifier: Modifier = Modifier,
) {
    val lossless = format?.isLossless() == true
    val hiRes = lossless && (format?.sampleRate ?: 0) >= 88_200
    val hiQuality = !lossless && (format?.bitrate ?: 0) >= 250_000
    when {
        // Still resolving — nothing measured yet to confirm with, so this is a
        // statement of intent, not a result.
        format == null || (isLoading && !lossless) -> LosslessLabel(
            text = "Upgrading Quality",
            animated = false,
            modifier = modifier,
        )
        lossless -> LosslessLabel(
            // Same line Tidal, Qobuz and Apple Music draw it at.
            text = if (hiRes) "Hi-Res Lossless" else "Lossless",
            // Shimmer is reserved for the thing that was asked for and
            // confirmed. It is what makes the badge read as an achievement
            // rather than a label, which only one of these two is.
            animated = true,
            modifier = modifier,
        )
        // Lossy, but the good end of lossy.
        hiQuality -> LosslessLabel(
            text = "Hi-Quality",
            animated = false,
            modifier = modifier,
        )
        else -> {}
    }
}

/** Whether the stream is a lossless codec. */
private fun FormatEntity.isLossless(): Boolean =
    mimeType.endsWith("flac") || mimeType.endsWith("alac")

/**
 * "FLAC · 320 kbps · 48.0 kHz" — whichever of those the format actually
 * reports. A figure it hasn't is dropped rather than filled in, so a short
 * line means little was known, never that something was invented.
 */
internal fun FormatEntity.describe(): String {
    val parts = buildList {
        codecLabel(mimeType)?.let(::add)
        if (!isLossless()) add("${bitrate / 1000} kbps")
        sampleRate?.let { add("%.1f kHz".format(Locale.ROOT, it / 1000f)) }
    }
    return parts.joinToString(" · ").takeIf { it.isNotEmpty() } ?: ""
}

/** The codec under its usual name rather than its MIME type. */
internal fun codecLabel(mimeType: String?): String? = when {
    mimeType == null -> null
    mimeType.endsWith("opus") -> "Opus"
    mimeType.endsWith("mp4a-latm") -> "AAC"
    mimeType.endsWith("vorbis") -> "Vorbis"
    mimeType.endsWith("mpeg") -> "MP3"
    mimeType.endsWith("flac") -> "FLAC"
    mimeType.endsWith("alac") -> "ALAC"
    else -> mimeType.substringAfter('/').uppercase(Locale.ROOT)
}

/** A headphone glyph ahead of the quality tag — "Upgrading Quality", "Hi-Quality", "Lossless". */
@Composable
private fun LosslessLabel(text: String, animated: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Headphones,
            contentDescription = null,
            tint = Color.White.copy(alpha = if (animated) 0.7f else 0.45f),
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(4.dp))
        if (animated) {
            ShimmerText(text = text)
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = (MaterialTheme.typography.labelMedium.fontSize.value + 1).sp,
                ),
                color = Color.White.copy(alpha = 0.45f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * "Lossless", with a highlight band sweeping left to right across it every
 * three seconds — confirmed, not just claimed, so it's worth the shine.
 *
 * The band's width is measured off the text itself via [onSizeChanged]
 * rather than assumed, so the sweep always clears the word fully at both
 * ends instead of being sized for whatever length happened to be typical.
 */
@Composable
private fun ShimmerText(text: String) {
    var widthPx by remember { mutableIntStateOf(0) }
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "lossless-shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = tween(durationMillis = 3_000, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart,
        ),
        label = "lossless-shimmer-progress",
    )
    val baseColor = Color.White.copy(alpha = 0.55f)
    val brush = if (widthPx <= 0) {
        Brush.linearGradient(listOf(baseColor, baseColor))
    } else {
        val band = widthPx * 0.6f
        val center = -band + progress * (widthPx + 2 * band)
        Brush.linearGradient(
            colorStops = arrayOf(0f to baseColor, 0.5f to Color.White, 1f to baseColor),
            start = Offset(center - band, 0f),
            end = Offset(center + band, 0f),
        )
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(
            brush = brush,
            fontWeight = FontWeight.SemiBold,
            fontSize = (MaterialTheme.typography.labelMedium.fontSize.value + 1).sp,
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.onSizeChanged { widthPx = it.width },
    )
}

// ── OverlayBack (verbatim) ────────────────────────────────────────────────────

/**
 * A back callback that outranks whatever else the window has registered —
 * here, the sheet the player is drawn in. See the call site in
 * [BitChordPlayerContent] for why it takes that.
 *
 * Everything that names an `android.window` type lives in this object so those
 * classes, which don't exist below API 33, are only ever *loaded* on a device
 * that has them.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private object OverlayBack {
    /** The registered callback, to hand back to [unregister]; null if it couldn't be. */
    fun register(view: View, onBack: () -> Unit): Any? {
        val dispatcher = view.findOnBackInvokedDispatcher() ?: return null
        val callback = OnBackInvokedCallback { onBack() }
        dispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_OVERLAY,
            callback,
        )
        return callback
    }

    fun unregister(view: View, callback: Any?) {
        if (callback !is OnBackInvokedCallback) return
        view.findOnBackInvokedDispatcher()?.unregisterOnBackInvokedCallback(callback)
    }
}
