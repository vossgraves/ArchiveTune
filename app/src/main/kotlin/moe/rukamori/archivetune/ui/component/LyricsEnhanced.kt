/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)

package moe.rukamori.archivetune.ui.component

import android.app.Activity
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.mocharealm.accompanist.lyrics.core.model.ISyncedLine
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeAlignment
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeSyllable
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.KaraokeLyricsView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.player.LocalLyricsScrollListener
import moe.rukamori.archivetune.constants.LyricsClickKey
import moe.rukamori.archivetune.constants.LyricsLineBlurKey
import moe.rukamori.archivetune.constants.LyricsRomanizeChineseKey
import moe.rukamori.archivetune.constants.LyricsRomanizeHindiKey
import moe.rukamori.archivetune.constants.LyricsRomanizeJapaneseKey
import moe.rukamori.archivetune.constants.LyricsRomanizeKoreanKey
import moe.rukamori.archivetune.constants.LyricsRomanizeOtherLanguagesKey
import moe.rukamori.archivetune.constants.LyricsTextSizeKey
import moe.rukamori.archivetune.constants.PlayerBackgroundStyle
import moe.rukamori.archivetune.constants.PlayerBackgroundStyleKey
import moe.rukamori.archivetune.db.entities.LyricsEntity
import moe.rukamori.archivetune.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import moe.rukamori.archivetune.lyrics.LyricsEntry
import moe.rukamori.archivetune.lyrics.LyricsRomanizationPreferences
import moe.rukamori.archivetune.lyrics.LyricsUtils.hasTrueWordSync
import moe.rukamori.archivetune.lyrics.LyricsUtils.isLineSyncedLrc
import moe.rukamori.archivetune.lyrics.LyricsUtils.isTtml
import moe.rukamori.archivetune.lyrics.LyricsUtils.parseLyrics
import moe.rukamori.archivetune.lyrics.LyricsUtils.parseTtml
import moe.rukamori.archivetune.lyrics.LyricsUtils.providedRomanizedTextForEntry
import moe.rukamori.archivetune.lyrics.LyricsUtils.providedRomanizedWordsForEntry
import moe.rukamori.archivetune.lyrics.LyricsUtils.providedTranslationTextForEntry
import moe.rukamori.archivetune.lyrics.LyricsUtils.romanizeLyricsLine
import moe.rukamori.archivetune.lyrics.LyricsUtils.romanizeWordsForLine
import moe.rukamori.archivetune.lyrics.LyricsUtils.shouldRomanizeLyricsLine
import moe.rukamori.archivetune.lyrics.WordTimestamp
import moe.rukamori.archivetune.ui.component.shimmer.ShimmerHost
import moe.rukamori.archivetune.ui.component.shimmer.TextPlaceholder
import moe.rukamori.archivetune.ui.theme.rememberArchiveTuneLyricsFontFamily
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.utils.reportException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private const val LRC_LEAD_MS = 300L
private const val TTML_LEAD_MS = 0L
private const val LYRIC_VISUAL_TUNING_OFFSET_MS = 150L
private const val MANUAL_SCROLL_TIMEOUT_MS = 3000L
private const val MANUAL_SCROLL_DEBOUNCE_MS = 50L
private const val LYRIC_FOCUS_TOP_ANCHOR_RATIO = 0.08f
// Guards define the "close enough" zone inside which the custom scroll is skipped.
// The top guard MUST be smaller than LYRIC_FOCUS_TOP_ANCHOR_RATIO so the resting
// position (8%) is inside the zone — otherwise the custom scroll would re-fire on
// every line change to "fix" a position that's already correct.
private const val LYRIC_FOCUS_TOP_GUARD_RATIO = 0.04f
private const val LYRIC_FOCUS_BOTTOM_GUARD_RATIO = 0.30f
private const val LYRIC_FOCUS_MIN_SCROLL_PX = 6
// Auto-scroll deltas up to this fraction of the viewport height snap instantly
// (no tween). A typical line-advance scroll moves the focus point from ~30%
// (bottom guard) to ~8% (anchor) = 22% of viewport. Setting the threshold to
// 40% covers all normal-playback line advances AND small-to-medium seeks,
// while larger jumps (return from manual scroll, large seeks) still animate.
// This eliminates the per-frame LazyColumn re-layout cost of the 280ms tween
// for the common case — which was the single biggest source of "auto-scroll
// lag" because the tween was competing with the 60Hz karaoke syllable sweep
// for frame budget on every line change.
private const val LYRIC_FOCUS_INSTANT_SCROLL_RATIO = 0.40f
private const val LYRIC_FOCUS_ANIMATED_DISTANCE = 4
private const val SMOOTH_PLAYBACK_MAX_FORWARD_DRIFT_MS = 80L
private const val SMOOTH_PLAYBACK_MAX_BACKWARD_DRIFT_MS = 180L
private const val SMOOTH_PLAYBACK_DRIFT_CORRECTION = 0.55f
// Reduced from 520ms to 280ms. The previous 520ms tween was long enough that
// it was STILL running when the next line change fired (especially on tracks
// with short lines), causing collectLatest to cancel + restart the animation
// repeatedly — visible as stuttery, non-monotonic scroll motion. 280ms is
// short enough to settle before the next line change in almost all songs,
// while still looking smooth rather than instant.
private const val LYRIC_FOCUS_SCROLL_DURATION_MS = 280
private const val MIN_KARAOKE_SYLLABLE_DURATION_MS = 1
// Minimum backward position jump (in ms) that we treat as a "reset" trigger —
// large enough to ride through ExoPlayer's normal position jitter (which is
// well under 200ms even on a stuttering device) and minor playback corrections,
// small enough to catch any real seek-backward or REPEAT_MODE_ONE wrap (which
// is typically a jump from near the end of the song back to 0, i.e. minutes).
private const val POSITION_RESET_BACKWARD_THRESHOLD_MS = 1000L

@Composable
fun LyricsEnhanced(
    sliderPositionProvider: () -> Long?,
    lyricsSyncOffset: Int,
    modifier: Modifier = Modifier,
    textColorOverride: Color? = null,
    lyricsLineBlurOverride: Boolean? = null,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val player = playerConnection.player
    val context = LocalContext.current
    val animationsDisabled = LocalAnimationsDisabled.current

    // collectAsStateWithLifecycle: pauses StateFlow collection when the
    // composable's lifecycle drops below STARTED (e.g. user navigates away
    // from the player, or the app goes to background). Without this, these
    // flows continue pushing values into state during background playback,
    // causing recompositions that compete with the karaoke frame loop for
    // main-thread time when the user returns. This is especially important
    // for `currentLyrics` below, which can emit during background lyrics
    // prefetch / AI translation completion.
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val playbackParameters by playerConnection.playbackParameters.collectAsStateWithLifecycle()

    val (lyricsClick) = rememberPreference(LyricsClickKey, defaultValue = true)
    val (lyricsTextSize) = rememberPreference(LyricsTextSizeKey, defaultValue = 26f)
    // Per-line RenderEffect blur (see useBlurEffect below) is the single heaviest
    // per-frame cost in the karaoke view -- default it OFF so word-synced lyrics
    // are smooth out of the box; users who want the effect can re-enable it in
    // Settings > Lyrics.
    val (lyricsLineBlurPreference) = rememberPreference(LyricsLineBlurKey, defaultValue = false)
    val (romanizeChinese) = rememberPreference(LyricsRomanizeChineseKey, defaultValue = true)
    val (romanizeHindi) = rememberPreference(LyricsRomanizeHindiKey, defaultValue = true)
    val (romanizeJapanese) = rememberPreference(LyricsRomanizeJapaneseKey, defaultValue = true)
    val (romanizeKorean) = rememberPreference(LyricsRomanizeKoreanKey, defaultValue = true)
    val (romanizeOtherLanguages) = rememberPreference(LyricsRomanizeOtherLanguagesKey, defaultValue = true)

    val romanizationPreferences =
        remember(
            romanizeJapanese,
            romanizeKorean,
            romanizeChinese,
            romanizeHindi,
            romanizeOtherLanguages,
        ) {
            LyricsRomanizationPreferences(
                romanizeJapanese = romanizeJapanese,
                romanizeKorean = romanizeKorean,
                romanizeChinese = romanizeChinese,
                romanizeHindi = romanizeHindi,
                romanizeOther = romanizeOtherLanguages,
            )
        }

    val lyricsFontFamily = rememberArchiveTuneLyricsFontFamily()

    val playerBackground by rememberEnumPreference(PlayerBackgroundStyleKey, PlayerBackgroundStyle.DEFAULT)
    // Apple Music style and all lyrics backgrounds always use white text so lyrics stay
    // readable on the dark blurred backdrop regardless of system theme.
    val textColor = textColorOverride ?: Color.White
    val lyricsLineBlur = lyricsLineBlurOverride ?: lyricsLineBlurPreference

    var isSelectionModeActive by rememberSaveable { mutableStateOf(false) }
    val selectedLineKeys = remember { mutableStateListOf<String>() }
    var showMaxSelectionToast by remember { mutableStateOf(false) }
    val maxSelectionLimit = 5
    var showShareDialog by remember { mutableStateOf(false) }
    var shareDialogData by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    var showShareImageDialog by remember { mutableStateOf(false) }

    val currentLyrics by playerConnection.currentLyrics.collectAsStateWithLifecycle(initialValue = null)
    val lyrics =
        remember(currentLyrics, mediaMetadata?.id) {
            currentLyrics
                ?.takeIf { lyricsEntity -> lyricsEntity.id == mediaMetadata?.id }
                ?.lyrics
        }
    val showTranslations =
        remember(currentLyrics?.source) {
            currentLyrics?.source == LyricsEntity.Source.AI_TRANSLATION.value
        }

    // Restart tick: bumps when the SAME song restarts (auto-repeat, manual replay, seek-to-zero)
    // so the karaoke subtree tears down and re-animates from the beginning. Without this,
    // mediaId + lyrics text are unchanged on restart, so lyricsSessionKey stays the same and
    // all per-session Animatable instances retain their settled values — no intro animation replays.
    val playbackState by playerConnection.playbackState.collectAsState()
    var restartTick by remember { mutableIntStateOf(0) }
    var lastPlaybackState by remember { mutableStateOf(Player.STATE_READY) }
    LaunchedEffect(mediaMetadata?.id, playbackState) {
        if (playbackState == Player.STATE_READY &&
            lastPlaybackState == Player.STATE_ENDED &&
            player.currentPosition < 1_000L
        ) {
            restartTick++
        } else if (playbackState == Player.STATE_READY &&
            lastPlaybackState == Player.STATE_BUFFERING &&
            player.currentPosition < 500L &&
            restartTick > 0
        ) {
            // Manual replay from the queue / notification while the song was already playing.
            // Only count it as a restart if we've already started once (restartTick > 0)
            // to avoid firing on the very first load.
            restartTick++
        }
        lastPlaybackState = playbackState
    }
    val lyricsSessionKey =
        remember(mediaMetadata?.id, lyrics, restartTick) {
            Triple(mediaMetadata?.id.orEmpty(), lyrics, restartTick)
        }

    val isSynced = remember(lyrics) { lyrics != null && (isLineSyncedLrc(lyrics!!) || isTtml(lyrics!!)) }
    val isTtmlFormat = remember(lyrics) { lyrics != null && isTtml(lyrics!!) }

    val lyricsEntries: List<LyricsEntry> =
        remember(lyrics) {
            if (lyrics == null || lyrics == LYRICS_NOT_FOUND) return@remember emptyList()
            when {
                isTtml(lyrics!!) -> {
                    parseTtml(lyrics!!)
                }

                isLineSyncedLrc(lyrics!!) -> {
                    parseLyrics(lyrics!!)
                }

                else -> {
                    lyrics!!
                        .lines()
                        .filter { it.isNotBlank() }
                        .map { line -> LyricsEntry(time = -1L, text = line.trim()) }
                }
            }
        }

    var syncedLyrics by remember(lyricsEntries, isTtmlFormat) {
        mutableStateOf(buildSyncedLyrics(lyricsEntries, isTtmlFormat, emptyMap()))
    }

    LaunchedEffect(lyricsEntries, romanizationPreferences) {
        // Everything below (scanning + romanizing every line, often one job per word for TTML)
        // used to inherit the main dispatcher and could stutter the karaoke animation on track
        // change; run the whole batch on Default and only publish the results back.
        withContext(Dispatchers.Default) {
        syncedLyrics = buildSyncedLyrics(lyricsEntries, isTtmlFormat, emptyMap())
        if (!romanizationPreferences.isEnabled) return@withContext

        val toRomanize =
            lyricsEntries.mapIndexedNotNull { index, entry ->
                val hasProviderRomanization =
                    providedRomanizedTextForEntry(entry, romanizationPreferences) != null
                if (hasProviderRomanization || shouldRomanizeLyricsLine(entry.text, romanizationPreferences)) {
                    index to entry
                } else {
                    null
                }
            }
        if (toRomanize.isEmpty()) return@withContext

        val jobs =
            toRomanize.map { (index, entry) ->
                async {
                    val romanized: List<String?> =
                        try {
                            if (isTtmlFormat && entry.words != null) {
                                val mainWordCount = entry.words!!.count { !it.isBackground }
                                providedRomanizedWordsForEntry(entry, mainWordCount, romanizationPreferences)
                                    ?: romanizeWordsForLine(
                                        // Japanese: single-pass line tokenization (6x fewer
                                        // Kuromoji calls than per-word). Other languages:
                                        // per-word character-by-character (already cheap).
                                        words = entry.words!!.filter { !it.isBackground }.map { it.text },
                                        lineText = entry.text,
                                        preferences = romanizationPreferences,
                                    )
                            } else {
                                listOf(
                                    providedRomanizedTextForEntry(entry, romanizationPreferences)
                                        ?: romanizeLyricsLine(entry.text, romanizationPreferences),
                                )
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            reportException(e)
                            if (isTtmlFormat && entry.words != null) {
                                List(entry.words!!.count { !it.isBackground }) { null }
                            } else {
                                listOf(null)
                            }
                        }
                    index to romanized
                }
            }
        val tempMap = mutableMapOf<Int, List<String?>>()
        jobs.awaitAll().forEach { (index, romanized) ->
            tempMap[index] = romanized
        }
        // Publishing new lyrics as state (instead of bumping a key that tears the whole karaoke
        // subtree down mid-playback) lets the view pick up romanization without a re-layout hitch.
        syncedLyrics = buildSyncedLyrics(lyricsEntries, isTtmlFormat, tempMap)
        }
    }

    val leadMs = if (isTtmlFormat) TTML_LEAD_MS else LRC_LEAD_MS

    val latestSliderPositionProvider = rememberUpdatedState(sliderPositionProvider)
    val latestLyricsSyncOffset = rememberUpdatedState(lyricsSyncOffset)
    val latestLeadMs = rememberUpdatedState(leadMs)
    val latestPlaybackSpeed = rememberUpdatedState(playbackParameters.speed)
    val playbackPositionMs =
        remember(player) {
            mutableLongStateOf(player.currentPosition.coerceAtLeast(0L))
        }
    val playbackSyncPosition: () -> Int =
        remember {
            {
                (
                    playbackPositionMs.longValue +
                        latestLyricsSyncOffset.value.toLong() +
                        latestLeadMs.value +
                        LYRIC_VISUAL_TUNING_OFFSET_MS
                ).coerceIn(0L, Int.MAX_VALUE.toLong())
                    .toInt()
            }
        }
    val currentLineIndexState = remember { mutableIntStateOf(-1) }
    // rememberUpdatedState so the playback loop sees the latest syncedLyrics (which can be
    // rebuilt when romanization finishes) without re-launching the loop and stuttering the
    // 60 Hz position interpolation.
    val latestSyncedLyrics = rememberUpdatedState(syncedLyrics)
    // Incremented every time we detect a backward position discontinuity larger
    // than 1 second — this happens when REPEAT_MODE_ONE wraps the song back to
    // the start (or when the user seeks backward by more than a second). The
    // KaraokeLyricsView from com.mocharealm.accompanist:lyrics-ui doesn't reset
    // its internal karaoke-progress / current-line state when the player
    // position jumps backward, so the highlight gets stuck at whatever line
    // was last active before the wrap. Forcing a re-key here disposes the old
    // view instance and creates a fresh one, which restarts the karaoke
    // animation from the current line. The user-visible symptom of the bug is
    // "lyrics stuck at start, no highlight, only fixed by closing and
    // reopening the lyrics sheet".
    var positionResetCounter by remember { mutableIntStateOf(0) }
    var isManualScrolling by remember { mutableStateOf(false) }
    var lastManualScrollTime by remember { mutableLongStateOf(0L) }
    // The scroll state is recreated together with the subtree that owns it.
    //
    // The karaoke view is re-keyed on [positionResetCounter] (see the KaraokeLyricsView
    // call site) because the mocharealm library keeps no per-play state of its own. That
    // re-key disposes one LazyColumn and composes another; keeping a single hoisted
    // LazyListState across the swap left the state briefly bound to two lazy layouts and
    // then owned by the disposed one, so scrolls silently went nowhere and layoutInfo
    // reported a stale viewport — the list snapped to the first line and then sat there,
    // which is the "resets on repeat but never animates again, fixed only by reopening
    // the lyrics" symptom. Recreating the state with its layout keeps the two in step,
    // and every effect that drives scrolling is keyed on the same counter so they all
    // observe the live state.
    val listState = key(lyricsSessionKey, positionResetCounter) { rememberLazyListState() }

    LaunchedEffect(lyricsSessionKey) {
        playbackPositionMs.longValue = player.currentPosition.coerceAtLeast(0L)
        isManualScrolling = false
        lastManualScrollTime = 0L
        isSelectionModeActive = false
        selectedLineKeys.clear()
        // Reset the cached line index so the auto-scroll effect force-scrolls to the
        // new track's first active line instead of inheriting the previous track's index.
        currentLineIndexState.intValue = -1
    }

    LaunchedEffect(player, lyricsSessionKey, animationsDisabled, playbackParameters.speed) {
        var wasSliderActive = false
        var anchorPlayerPositionMs = player.currentPosition.coerceAtLeast(0L)
        var anchorFrameNanos = 0L
        var lastRawPositionMs = player.currentPosition.coerceAtLeast(0L)
        // Cache the current line index + boundary timestamps to avoid calling
        // findLastStartedLineIndex (binary search) every frame. In the common
        // case the position stays within the current line for several seconds,
        // so we only need to re-search when the position crosses a line
        // boundary. This reduces per-frame work from O(log N) to O(1) and,
        // more importantly, avoids the `playbackSyncPosition()` lambda call
        // (3 State reads + arithmetic) on the vast majority of frames — we
        // reuse the `effectivePositionMs` already computed for the playback
        // interpolation instead of re-reading `playbackPositionMs.longValue`.
        //
        // The cache is invalidated when `syncedLyrics` changes (e.g. when
        // romanization finishes) by tracking the identity of the last-seen
        // SyncedLyrics object.
        var lastLyricsRef: SyncedLyrics? = null
        var cachedLineIdx = -1
        var cachedCurrentLineStart = Int.MIN_VALUE
        var cachedNextLineStart = Int.MAX_VALUE
        while (isActive) {
            val sliderPosition = latestSliderPositionProvider.value()
            val isSliderActive = sliderPosition != null
            if (isSliderActive && !wasSliderActive) {
                isManualScrolling = false
            }
            wasSliderActive = isSliderActive

            val rawPosition = (sliderPosition ?: player.currentPosition).coerceAtLeast(0L)
            // Detect a backward position discontinuity, measured against the
            // PLAYER's clock rather than the slider-provided value. This catches
            // both REPEAT_MODE_ONE wraps (duration -> 0) and explicit backward
            // seeks, while the 1s threshold excludes playback jitter and minor
            // ExoPlayer position corrections.
            //
            // This used to additionally require `sliderPosition == null`, which
            // disabled restart detection entirely in the Apple Music player —
            // that player always installs a slider provider, so on repeat the
            // lyrics snapped back to the first line but never re-animated until
            // the overlay was reopened.
            val rawPlayerPosition = player.currentPosition.coerceAtLeast(0L)
            if (lastRawPositionMs - rawPlayerPosition > POSITION_RESET_BACKWARD_THRESHOLD_MS) {
                positionResetCounter += 1
            }
            lastRawPositionMs = rawPlayerPosition
            // effectivePositionMs is the synced position (offset + lead +
            // tuning) that we'd otherwise compute via playbackSyncPosition().
                       // Computing it here inline avoids a redundant State read of
            // playbackPositionMs.longValue (we already have the value in
            // `rawPosition` / `nextPosition`).
            val effectivePositionMs: Long
            if (sliderPosition != null || !player.isPlaying || animationsDisabled) {
                anchorPlayerPositionMs = rawPosition
                anchorFrameNanos = 0L
                if (playbackPositionMs.longValue != rawPosition) {
                    playbackPositionMs.longValue = rawPosition
                }
                effectivePositionMs =
                    (rawPosition + latestLyricsSyncOffset.value.toLong() +
                        latestLeadMs.value + LYRIC_VISUAL_TUNING_OFFSET_MS)
                        .coerceIn(0L, Int.MAX_VALUE.toLong())
                if (sliderPosition == null) {
                    delay(100L)
                } else {
                    withFrameNanos { }
                }
            } else {
                val frameNanos = withFrameNanos { frameTimeNanos -> frameTimeNanos }
                if (anchorFrameNanos == 0L) {
                    anchorFrameNanos = frameNanos
                    anchorPlayerPositionMs = rawPosition
                }

                val elapsedMs = ((frameNanos - anchorFrameNanos) / 1_000_000f) * latestPlaybackSpeed.value
                val projectedPosition = anchorPlayerPositionMs + elapsedMs.roundToLong()
                val driftMs = rawPosition - projectedPosition
                val nextPosition =
                    when {
                        driftMs > SMOOTH_PLAYBACK_MAX_FORWARD_DRIFT_MS ||
                            driftMs < -SMOOTH_PLAYBACK_MAX_BACKWARD_DRIFT_MS -> {
                            anchorPlayerPositionMs = rawPosition
                            anchorFrameNanos = frameNanos
                            rawPosition
                        }

                        driftMs != 0L -> {
                            projectedPosition + (driftMs * SMOOTH_PLAYBACK_DRIFT_CORRECTION).roundToLong()
                        }

                        else -> {
                            projectedPosition
                        }
                    }.coerceAtLeast(0L)

                if (playbackPositionMs.longValue != nextPosition) {
                    playbackPositionMs.longValue = nextPosition
                }
                effectivePositionMs =
                    (nextPosition + latestLyricsSyncOffset.value.toLong() +
                        latestLeadMs.value + LYRIC_VISUAL_TUNING_OFFSET_MS)
                        .coerceIn(0L, Int.MAX_VALUE.toLong())
            }

            // Line-index tracking: only re-search when the position crosses a
            // line boundary OR the lyrics object identity changed (romanization
            // finished). This is the key per-frame optimization — the binary
            // search is skipped entirely on the vast majority of frames, and
            // we reuse `effectivePositionMs` instead of calling the
            // `playbackSyncPosition()` lambda (which would do 3 more State
            // reads + arithmetic on every frame).
            val syncedLyricsNow = latestSyncedLyrics.value
            if (syncedLyricsNow.lines.isNotEmpty()) {
                val lyricsChanged = syncedLyricsNow !== lastLyricsRef
                if (lyricsChanged) {
                    lastLyricsRef = syncedLyricsNow
                    cachedLineIdx = -1
                }
                val pos = effectivePositionMs.toInt()
                val needsResearch =
                    cachedLineIdx == -1 ||
                        pos >= cachedNextLineStart ||
                        pos < cachedCurrentLineStart
                if (needsResearch) {
                    val newLineIdx = syncedLyricsNow.findLastStartedLineIndex(pos)
                    cachedLineIdx = newLineIdx
                    cachedCurrentLineStart =
                        if (newLineIdx >= 0) syncedLyricsNow.lines[newLineIdx].start else Int.MIN_VALUE
                    cachedNextLineStart =
                        syncedLyricsNow.lines.getOrNull(newLineIdx + 1)?.start ?: Int.MAX_VALUE
                    if (newLineIdx != currentLineIndexState.intValue) {
                        currentLineIndexState.intValue = newLineIdx
                    }
                }
            }
        }
    }

    val nestedScrollConnection =
        remember {
            var lastUserScrollEventMs = 0L
            object : NestedScrollConnection {
                private fun markManualScroll() {
                    val now = System.currentTimeMillis()
                    if (now - lastUserScrollEventMs >= MANUAL_SCROLL_DEBOUNCE_MS) {
                        isManualScrolling = true
                        lastManualScrollTime = now
                        lastUserScrollEventMs = now
                    }
                }

                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (!isSelectionModeActive && source == NestedScrollSource.UserInput) {
                        markManualScroll()
                    }
                    return Offset.Zero
                }

                override suspend fun onPostFling(
                    consumed: Velocity,
                    available: Velocity,
                ): Velocity {
                    if (!isSelectionModeActive && isManualScrolling) {
                        lastManualScrollTime = System.currentTimeMillis()
                    }
                    return Velocity.Zero
                }
            }
        }

    LaunchedEffect(isManualScrolling, lastManualScrollTime) {
        if (isManualScrolling) {
            delay(MANUAL_SCROLL_TIMEOUT_MS)
            isManualScrolling = false
        }
    }

    // Forward the user-scroll signal up to LyricsScreen via LocalLyricsScrollListener so the
    // Apple Music-style bottom controls can slide in when the user scrolls lyrics.
    val onLyricsScroll = LocalLyricsScrollListener.current
    LaunchedEffect(isManualScrolling) {
        onLyricsScroll(isManualScrolling)
    }

    // NOTE: this LaunchedEffect used to key on `syncedLyrics` as well. That
    // was wrong: every time `syncedLyrics` was reassigned (which happens up to
    // 3× per track — initial build, empty-romanization placeholder, final
    // romanization map; plus once more if AI translation completes mid-playback),
    // the entire snapshotFlow + collectLatest was torn down and re-created,
    // forcing `forceNextScroll = true` and triggering a visible instant-jump
    // `scrollToItem` to the current line. Each re-launch also re-awaited the
    // viewport-ready snapshotFlow, adding a 1-2 frame gap where auto-scroll
    // was inactive. With AI translation completing ~30-60s after the user
    // opens lyrics (typical LLM response time), this produced a visible
    // "lyrics jump + brief stutter" right around the 1-minute mark — exactly
    // the symptom the user reported as "lags after a minute or so".
    //
    // Fix: hoist `syncedLyrics` into a rememberUpdatedState so the LaunchedEffect
    // does NOT re-launch on content changes. The snapshotFlow block reads the
    // latest value through the State, and `distinctUntilChanged` on the emitted
    // index naturally filters out content-only updates (the line index doesn't
    // change just because phonetic/translation text was added). If the line
    // index DOES change (e.g. the new lyrics have different line timings), the
    // playback loop's `lyricsChanged` branch already invalidates `cachedLineIdx`
    // and updates `currentLineIndexState`, which flows through the snapshotFlow
    // and triggers a normal (non-forced) scroll.
    val latestSyncedLyricsForScroll = rememberUpdatedState(syncedLyrics)
    // Restart this collector after a repeat. The karaoke view is re-keyed at
    // the same time, and the fresh collector resets its focus state before it
    // observes the first new active line. Keeping listState stable means the
    // collector always targets the list currently on screen.
    LaunchedEffect(lyricsSessionKey, isSynced, positionResetCounter) {
        if (!isSynced || latestSyncedLyricsForScroll.value.lines.isEmpty()) return@LaunchedEffect
        snapshotFlow {
            listState.layoutInfo.viewportEndOffset > listState.layoutInfo.viewportStartOffset
        }.first { it }

        var forceNextScroll = true
        snapshotFlow {
            if (isManualScrolling || isSelectionModeActive) {
                null
            } else {
                currentLineIndexState.intValue
                    .takeIf { index -> index in latestSyncedLyricsForScroll.value.lines.indices }
            }
        }.distinctUntilChanged()
            .collectLatest { index ->
                if (index == null) {
                    forceNextScroll = true
                    return@collectLatest
                }

                listState.scrollLyricIntoFocus(
                    index = index,
                    animateToNearbyItem = !forceNextScroll,
                    force = forceNextScroll,
                )
                forceNextScroll = false
            }
    }

    // Clear the tracked active line after a backward discontinuity so the re-keyed
    // auto-scroll collector above force-scrolls to the first line of the new
    // play-through instead of treating the previous line index as still current.
    //
    // No explicit scroll here: [listState] is recreated alongside the karaoke subtree on
    // the same counter, so the fresh list already starts at the top. Scrolling the old
    // state was both redundant and a way to touch a state whose layout was being
    // disposed.
    LaunchedEffect(positionResetCounter) {
        if (positionResetCounter > 0) {
            currentLineIndexState.intValue = -1
        }
    }

    BackHandler(enabled = isSelectionModeActive) {
        isSelectionModeActive = false
        selectedLineKeys.clear()
    }

    LaunchedEffect(showMaxSelectionToast) {
        if (showMaxSelectionToast) {
            Toast
                .makeText(
                    context,
                    context.getString(R.string.max_selection_limit, maxSelectionLimit),
                    Toast.LENGTH_SHORT,
                ).show()
            showMaxSelectionToast = false
        }
    }

    val activity = context as? Activity
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Remembered: fresh TextStyle identities every recomposition forced the karaoke view to
    // re-measure all lines on unrelated state changes (scroll flags, selection, etc.).
    val typography = MaterialTheme.typography
    val normalTextStyle =
        remember(typography, lyricsTextSize, lyricsFontFamily) {
            typography.headlineMedium.copy(
                fontSize = lyricsTextSize.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = lyricsFontFamily ?: typography.headlineMedium.fontFamily,
            )
        }
    val accompanimentTextStyle =
        remember(typography, lyricsTextSize, lyricsFontFamily) {
            typography.titleLarge.copy(
                fontSize = (lyricsTextSize * 0.82f).sp,
                fontFamily = lyricsFontFamily ?: typography.titleLarge.fontFamily,
            )
        }
    val phoneticTextStyle =
        remember(typography, lyricsTextSize) {
            typography.bodyMedium.copy(
                fontSize = (lyricsTextSize * 0.55f).sp,
                fontWeight = FontWeight.Normal,
            )
        }
    val plainLyrics =
        remember(lyricsEntries, isSynced) {
            PlainLyrics(
                items =
                    if (isSynced) {
                        emptyList()
                    } else {
                        lyricsEntries.mapIndexedNotNull { index, entry ->
                            val text = entry.text.trim()
                            if (text.isBlank()) {
                                null
                            } else {
                                val selectionId = "plain:$index:${text.hashCode()}"
                                PlainLyricLine(
                                    itemId = "$selectionId#$index",
                                    selectionId = selectionId,
                                    text = text,
                                )
                            }
                        }
                    },
            )
        }
    val selectionLines =
        remember(isSynced, syncedLyrics, plainLyrics) {
            if (isSynced) {
                syncedLyrics.lines.mapIndexedNotNull { index, line ->
                    val text = line.lineText()
                    if (text.isBlank()) {
                        null
                    } else {
                        val selectionId = line.selectionKey(text)
                        LyricSelectionLine(
                            itemId = "$selectionId#$index",
                            selectionId = selectionId,
                            text = text,
                        )
                    }
                }
            } else {
                plainLyrics.items.map { line ->
                    LyricSelectionLine(
                        itemId = line.itemId,
                        selectionId = line.selectionId,
                        text = line.text,
                    )
                }
            }
        }
    val selectedLineKeySnapshot = selectedLineKeys.toList()
    val selectedLineKeySet = remember(selectedLineKeySnapshot) { selectedLineKeySnapshot.toSet() }
    val dismissSelection = {
        isSelectionModeActive = false
        selectedLineKeys.clear()
    }
    val toggleSelectedLine: (String) -> Unit = { lineKey ->
        if (selectedLineKeys.contains(lineKey)) {
            selectedLineKeys.remove(lineKey)
            if (selectedLineKeys.isEmpty()) isSelectionModeActive = false
        } else if (selectedLineKeys.size < maxSelectionLimit) {
            selectedLineKeys.add(lineKey)
        } else {
            showMaxSelectionToast = true
        }
    }
    val shareSelectedLyrics: () -> Unit = {
        val metadata = mediaMetadata
        if (metadata != null) {
            val selectedLyricsText =
                selectionLines
                    .filter { line -> line.selectionId in selectedLineKeySet }
                    .joinToString("\n") { line -> line.text }
            if (selectedLyricsText.isNotBlank()) {
                shareDialogData =
                    Triple(
                        selectedLyricsText,
                        metadata.title,
                        metadata.artists.joinToString { it.name },
                    )
                showShareDialog = true
            }
        }
        dismissSelection()
    }

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier =
            modifier
                .fillMaxSize()
                .padding(bottom = 12.dp),
    ) {
        when {
            lyrics == LYRICS_NOT_FOUND -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.lyrics_not_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            lyrics == null -> {
                ShimmerHost {
                    repeat(6) { TextPlaceholder() }
                }
            }

            isSynced && syncedLyrics.lines.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.lyrics_not_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            !isSynced && plainLyrics.items.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.lyrics_not_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            !isSynced -> {
                PlainLyricsView(
                    lines = plainLyrics,
                    listState = listState,
                    selectedLineKeys = selectedLineKeySet,
                    textColor = textColor,
                    textStyle = normalTextStyle,
                    onLineClicked = { lineKey ->
                        if (isSelectionModeActive) toggleSelectedLine(lineKey)
                    },
                    onLinePressed = { lineKey ->
                        if (!isSelectionModeActive) {
                            isSelectionModeActive = true
                            if (!selectedLineKeys.contains(lineKey)) {
                                selectedLineKeys.add(lineKey)
                            }
                        } else if (!selectedLineKeys.contains(lineKey)) {
                            toggleSelectedLine(lineKey)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            else -> {
                BoxWithConstraints(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .nestedScroll(nestedScrollConnection),
                ) {
                    val lyricsViewportOffset = remember(maxHeight) { maxHeight * 0.08f }

                    // Keyed on the session + a position-reset counter so that when
                    // the song repeats (REPEAT_MODE_ONE wraps position to 0) or the
                    // user seeks backward by more than 1 second, the
                    // KaraokeLyricsView instance is disposed and recreated — the
                    // library's internal highlight state doesn't reset on
                    // backward position jumps, so without this re-key the
                    // karaoke animation freezes at the line that was active
                    // just before the repeat.
                    key(lyricsSessionKey, positionResetCounter) {
                        KaraokeLyricsView(
                            listState = listState,
                            lyrics = syncedLyrics,
                            currentPosition = playbackSyncPosition,
                            onLineClicked = { line ->
                                if (isSelectionModeActive) {
                                    toggleSelectedLine(line.selectionKey())
                                } else if (lyricsClick && isSynced && line.start > 0) {
                                    player.seekTo(line.start.toLong())
                                }
                            },
                            onLinePressed = { line ->
                                val lineKey = line.selectionKey()
                                if (!isSelectionModeActive) {
                                    isSelectionModeActive = true
                                    if (!selectedLineKeys.contains(lineKey)) {
                                        selectedLineKeys.add(lineKey)
                                    }
                                } else if (!selectedLineKeys.contains(lineKey)) {
                                    toggleSelectedLine(lineKey)
                                }
                            },
                            textColor = textColor,
                            normalLineTextStyle = normalTextStyle,
                            accompanimentLineTextStyle = accompanimentTextStyle,
                            phoneticTextStyle = phoneticTextStyle,
                            blendMode = BlendMode.SrcOver,
                            // Per-line RenderEffect blur is the single heaviest per-frame cost in
                            // this view; drop it when animations are disabled (low-RAM default).
                            useBlurEffect = lyricsLineBlur && !animationsDisabled,
                            showTranslation = showTranslations,
                            showPhonetic = romanizationPreferences.isEnabled,
                            offset = lyricsViewportOffset,
                            // Reduced from 36.dp → 20.dp → 8.dp. The keepAliveZone controls how
                            // many items outside the viewport are kept composed (not
                            // disposed) — each kept-alive item still participates in
                            // the per-frame measure pass during auto-scroll. The
                            // mocharealm KaraokeLyricsView library measures every
                            // kept-alive karaoke line on every scroll event; each line
                            // contains N syllables that each need a fill-ratio
                            // computation. 8dp keeps at most ~1 line alive on each
                            // side of the viewport, minimizing the measure cost
                            // during the instant `scrollBy` snap on line changes.
                            // This is the single biggest lever we have for reducing
                            // the word-synced lyrics lag in Enhanced style (V2 uses
                            // its own renderer and doesn't have this cost).
                            keepAliveZone = 8.dp,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }

    if (isSelectionModeActive && selectionLines.isNotEmpty()) {
        LyricsSelectionBottomSheet(
            lines = selectionLines,
            selectedLineKeys = selectedLineKeySet,
            onToggleLine = toggleSelectedLine,
            onDismissRequest = dismissSelection,
            onShareSelected = shareSelectedLyrics,
        )
    }

    if (showShareDialog && shareDialogData != null) {
        val (lyricsText, songTitle, artists) = shareDialogData!!
        BasicAlertDialog(onDismissRequest = { showShareDialog = false }) {
            Card(
                shape = RoundedCornerShape(28.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier =
                    Modifier
                        .padding(16.dp)
                        .fillMaxWidth(0.85f),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = stringResource(R.string.share_lyrics),
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    shareLyricsAsText(
                                        context = context,
                                        payload = LyricsSharePayload(lyricsText, songTitle, artists),
                                        songId = mediaMetadata?.id,
                                    )
                                    showShareDialog = false
                                }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.share),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.share_as_text),
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    shareDialogData = Triple(lyricsText, songTitle, artists)
                                    showShareImageDialog = true
                                    showShareDialog = false
                                }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.share),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.share_as_image),
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Text(
                            text = stringResource(R.string.cancel),
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium,
                            modifier =
                                Modifier
                                    .clickable { showShareDialog = false }
                                    .padding(vertical = 8.dp, horizontal = 12.dp),
                        )
                    }
                }
            }
        }
    }

    if (showShareImageDialog && shareDialogData != null) {
        val (lyricsText, songTitle, artists) = shareDialogData!!
        LyricsShareImageDialog(
            mediaMetadata = mediaMetadata,
            payload = LyricsSharePayload(lyricsText, songTitle, artists),
            onDismissRequest = { showShareImageDialog = false },
        )
    }
}

@Immutable
private data class PlainLyrics(
    val items: List<PlainLyricLine>,
)

@Immutable
private data class PlainLyricLine(
    val itemId: String,
    val selectionId: String,
    val text: String,
)

@Immutable
private data class LyricSelectionLine(
    val itemId: String,
    val selectionId: String,
    val text: String,
)

@Composable
private fun PlainLyricsView(
    lines: PlainLyrics,
    listState: LazyListState,
    selectedLineKeys: Set<String>,
    textColor: Color,
    textStyle: TextStyle,
    onLineClicked: (String) -> Unit,
    onLinePressed: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentPadding =
        remember {
            PaddingValues(
                start = 12.dp,
                top = 120.dp,
                end = 12.dp,
                bottom = 96.dp,
            )
        }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(
            items = lines.items,
            key = { line -> line.itemId },
            contentType = { "plain_lyric_line" },
        ) { line ->
            PlainLyricLineItem(
                line = line,
                selected = line.selectionId in selectedLineKeys,
                textColor = textColor,
                textStyle = textStyle,
                onLineClicked = onLineClicked,
                onLinePressed = onLinePressed,
            )
        }
    }
}

@Composable
private fun PlainLyricLineItem(
    line: PlainLyricLine,
    selected: Boolean,
    textColor: Color,
    textStyle: TextStyle,
    onLineClicked: (String) -> Unit,
    onLinePressed: (String) -> Unit,
) {
    val contentColor =
        if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            textColor
        }

    Text(
        text = line.text,
        style = textStyle,
        color = contentColor,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .combinedClickable(
                    onClick = { onLineClicked(line.selectionId) },
                    onLongClick = { onLinePressed(line.selectionId) },
                ).padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun LyricsSelectionBottomSheet(
    lines: List<LyricSelectionLine>,
    selectedLineKeys: Set<String>,
    onToggleLine: (String) -> Unit,
    onDismissRequest: () -> Unit,
    onShareSelected: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val firstSelectedIndex =
        remember(lines, selectedLineKeys) {
            lines
                .indexOfFirst { line -> line.selectionId in selectedLineKeys }
                .coerceAtLeast(0)
        }
    val sheetListState =
        rememberLazyListState(
            initialFirstVisibleItemIndex = firstSelectedIndex,
        )
    val selectedCount = selectedLineKeys.size

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.share_selected),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = pluralStringResource(R.plurals.n_element, selectedCount, selectedCount),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(R.string.close))
                }
            }

            LazyColumn(
                state = sheetListState,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    items = lines,
                    key = { line -> line.itemId },
                    contentType = { "lyric_selection_line" },
                ) { line ->
                    LyricsSelectionLineItem(
                        line = line,
                        selected = line.selectionId in selectedLineKeys,
                        onClick = { onToggleLine(line.selectionId) },
                    )
                }
            }

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(R.string.cancel))
                }
                Button(
                    onClick = onShareSelected,
                    enabled = selectedCount > 0,
                    shape = MaterialTheme.shapes.extraLarge,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.share),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(text = stringResource(R.string.share_selected))
                }
            }
        }
    }
}

@Composable
private fun LyricsSelectionLineItem(
    line: LyricSelectionLine,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val containerColor =
        if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    val contentColor =
        if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 18.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = line.text,
                style = MaterialTheme.typography.headlineSmall,
                color = contentColor,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            )
        }
    }
}

private suspend fun LazyListState.scrollLyricIntoFocus(
    index: Int,
    animateToNearbyItem: Boolean,
    force: Boolean,
) {
    val itemCount = layoutInfo.totalItemsCount
    if (itemCount == 0) return

    val targetIndex = index.coerceIn(0, itemCount - 1)
    var itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { item -> item.index == targetIndex }
    if (itemInfo == null) {
        val distance = abs(targetIndex - firstVisibleItemIndex)
        if (animateToNearbyItem && distance <= LYRIC_FOCUS_ANIMATED_DISTANCE) {
            animateScrollToItem(targetIndex)
        } else {
            scrollToItem(targetIndex)
        }
        withFrameNanos { }
        itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { item -> item.index == targetIndex }
    }

    itemInfo ?: return

    val viewportStart = layoutInfo.viewportStartOffset
    val viewportEnd = layoutInfo.viewportEndOffset
    val viewportHeight = viewportEnd - viewportStart
    if (viewportHeight <= 0) return

    val itemFocusPoint = itemInfo.offset
    val topGuard = viewportStart + (viewportHeight * LYRIC_FOCUS_TOP_GUARD_RATIO).roundToInt()
    val bottomGuard = viewportEnd - (viewportHeight * LYRIC_FOCUS_BOTTOM_GUARD_RATIO).roundToInt()
    if (!force && itemFocusPoint in topGuard..bottomGuard) return

    val targetFocusPoint = viewportStart + (viewportHeight * LYRIC_FOCUS_TOP_ANCHOR_RATIO).roundToInt()
    val scrollDelta = itemFocusPoint - targetFocusPoint
    if (abs(scrollDelta) > LYRIC_FOCUS_MIN_SCROLL_PX) {
        // For deltas up to 40% of the viewport (which covers ALL normal
        // line-advance scrolls — they're typically ~22% of viewport), snap
        // instantly instead of running a 280ms tween. The tween triggers a
        // LazyColumn re-layout every frame for its entire duration, which
        // steals frame budget from the 60Hz karaoke syllable sweep — the
        // root cause of "auto-scroll lag". The snap is imperceptible because
        // the karaoke fill animation already provides visual continuity.
        // Larger deltas (return from manual scroll, large seeks) still
        // animate so the motion stays smooth over long distances.
        val instantThreshold = (viewportHeight * LYRIC_FOCUS_INSTANT_SCROLL_RATIO).roundToInt()
        if (abs(scrollDelta) <= instantThreshold && !force) {
            scrollBy(scrollDelta.toFloat())
        } else {
            animateScrollBy(
                value = scrollDelta.toFloat(),
                animationSpec =
                    tween(
                        durationMillis = LYRIC_FOCUS_SCROLL_DURATION_MS,
                        easing = FastOutSlowInEasing,
                    ),
            )
        }
    }
}

private fun ISyncedLine.lineText(): String =
    when (this) {
        is KaraokeLine -> syllables.joinToString("") { it.content }
        is SyncedLine -> content
        else -> ""
    }

private fun ISyncedLine.selectionKey(text: String = lineText()): String = "$start:$end:${text.hashCode()}"

private fun SyncedLyrics.findLastStartedLineIndex(time: Int): Int {
    var low = 0
    var high = lines.lastIndex
    var result = -1

    while (low <= high) {
        val mid = low + (high - low) / 2
        if (lines[mid].start <= time) {
            result = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }

    return result
}

private fun List<WordTimestamp>.toKaraokeSyllables(phonetics: List<String?>): List<KaraokeSyllable> =
    mapIndexed { index, word ->
        val start = word.startTime.toMilliseconds()
        val nextStart = getOrNull(index + 1)?.startTime?.toMilliseconds()
        val rawEnd = word.endTime.toMilliseconds()
        val end =
            nextStart
                ?.let { minOf(rawEnd, it) }
                ?: rawEnd

        KaraokeSyllable(
            content = word.text,
            start = start,
            end = end.coerceAtLeast(start + MIN_KARAOKE_SYLLABLE_DURATION_MS),
            phonetic = phonetics.getOrNull(index),
        )
    }

private fun Double.toMilliseconds(): Int = (this * 1000.0).roundToInt().coerceAtLeast(0)

private fun buildSyncedLyrics(
    entries: List<LyricsEntry>,
    isTtml: Boolean,
    romanizationMap: Map<Int, List<String?>>,
): SyncedLyrics {
    if (entries.isEmpty()) return SyncedLyrics(emptyList())
    val lines = mutableListOf<ISyncedLine>()

    entries.forEachIndexed { index, entry ->
        if (entry.time < 0L) return@forEachIndexed
        if (entry.isInstrumental) return@forEachIndexed
        if (entry.text.isBlank() && entry.words.isNullOrEmpty()) return@forEachIndexed

        if (isTtml && entry.words != null && hasTrueWordSync(entry)) {
            val translation = providedTranslationTextForEntry(entry)
            val mainWords = entry.words!!.filter { !it.isBackground }
            val bgWords = entry.words!!.filter { it.isBackground }
            val alignment =
                when (entry.agent?.lowercase()) {
                    "v2" -> KaraokeAlignment.End
                    else -> KaraokeAlignment.Start
                }

            val wordsForMain = if (mainWords.isNotEmpty()) mainWords else entry.words!!
            val wordPhonetics = romanizationMap[index] ?: emptyList()
            val mainSyllables = wordsForMain.toKaraokeSyllables(wordPhonetics)

            val lineStart = mainSyllables.first().start
            val lineEnd = mainSyllables.last().end
            if (lineEnd <= lineStart) return@forEachIndexed

            val accompanimentLines =
                if (mainWords.isNotEmpty() && bgWords.isNotEmpty()) {
                    val bgSyllables = bgWords.toKaraokeSyllables(emptyList())
                    val bgStart = bgSyllables.first().start
                    val bgEnd = bgSyllables.last().end
                    if (bgEnd > bgStart) {
                        listOf(
                            KaraokeLine.AccompanimentKaraokeLine(
                                syllables = bgSyllables,
                                translation = null,
                                alignment = alignment,
                                start = bgStart,
                                end = bgEnd,
                                phonetic = null,
                            ),
                        )
                    } else {
                        null
                    }
                } else {
                    null
                }

            lines.add(
                KaraokeLine.MainKaraokeLine(
                    syllables = mainSyllables,
                    translation = translation,
                    alignment = alignment,
                    start = lineStart,
                    end = lineEnd,
                    phonetic = null,
                    accompanimentLines = accompanimentLines,
                ),
            )
        } else {
            val nextEntry = entries.getOrNull(index + 1)
            val lineEnd =
                if (nextEntry != null && nextEntry.time > entry.time) {
                    val gap = nextEntry.time - entry.time
                    if (gap > 3000L) {
                        minOf((nextEntry.time - 1L).toInt(), (entry.time + 4000L).toInt())
                            .coerceAtLeast(entry.time.toInt() + 1)
                    } else {
                        (nextEntry.time - 1L).coerceAtLeast(entry.time + 1L).toInt()
                    }
                } else {
                    (entry.time + 4000L).toInt()
                }
            lines.add(
                buildLineSyncedLrcLine(
                    entry = entry,
                    romanizedText = romanizationMap[index]?.firstOrNull(),
                    start = entry.time.toInt(),
                    end = lineEnd,
                ),
            )
        }
    }

    return SyncedLyrics(lines = lines)
}

private fun buildLineSyncedLrcLine(
    entry: LyricsEntry,
    romanizedText: String?,
    start: Int,
    end: Int,
): ISyncedLine {
    val translation = providedTranslationTextForEntry(entry)
    val normalizedRomanizedText = romanizedText?.trim()?.takeIf { it.isNotEmpty() }

    if (normalizedRomanizedText == null) {
        return SyncedLine(
            content = entry.text,
            translation = translation,
            start = start,
            end = end,
        )
    }

    val syllables =
        buildWrappingKaraokeSyllables(
            content = entry.text,
            romanizedText = normalizedRomanizedText,
            start = start,
            end = end,
        )

    return KaraokeLine.MainKaraokeLine(
        syllables = syllables,
        translation = translation,
        alignment = KaraokeAlignment.Start,
        start = start,
        end = end,
    )
}

private fun buildWrappingKaraokeSyllables(
    content: String,
    romanizedText: String,
    start: Int,
    end: Int,
): List<KaraokeSyllable> {
    val contentUnits = content.toLyricsWrappingUnits().ifEmpty { listOf(content) }
    val phoneticWords = romanizedText.split(Regex("\\s+")).filter(String::isNotEmpty)
    val phoneticAnchorIndices =
        contentUnits.indices.filter { index ->
            contentUnits[index].any(Char::isLetterOrDigit)
        }
    val phoneticsByUnit = MutableList<String?>(contentUnits.size) { null }

    if (phoneticAnchorIndices.isNotEmpty()) {
        phoneticWords.forEachIndexed { wordIndex, word ->
            val anchorIndex = wordIndex * phoneticAnchorIndices.size / phoneticWords.size
            val unitIndex = phoneticAnchorIndices[anchorIndex]
            phoneticsByUnit[unitIndex] = listOfNotNull(phoneticsByUnit[unitIndex], word).joinToString(" ")
        }
    }

    val duration = (end - start).coerceAtLeast(contentUnits.size)
    return contentUnits.mapIndexed { index, unit ->
        val unitStart = start + (duration.toLong() * index / contentUnits.size).toInt()
        val unitEnd = start + (duration.toLong() * (index + 1) / contentUnits.size).toInt()
        KaraokeSyllable(
            content = unit,
            start = unitStart,
            end = unitEnd.coerceAtLeast(unitStart + MIN_KARAOKE_SYLLABLE_DURATION_MS),
            phonetic = phoneticsByUnit[index],
        )
    }
}
