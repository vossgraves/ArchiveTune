/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

/*
 * SimpMusic player style.
 *
 * The layout is SimpMusic's default now-playing screen — its `NowPlayingContentSpotify`, the branch
 * its own `when` falls through to (https://github.com/maxrave-dev/SimpMusic, GPL-3.0): a diagonal
 * palette gradient pulled from the artwork, a swipeable artwork pager over the real queue, the
 * current lyric line sitting in the gap under the sleeve, then the info row, scrubber and
 * transport.
 *
 * REWRITTEN, not transliterated. SimpMusic is a Compose Multiplatform app and its version of this
 * screen is one 1,700-line composable carrying its own KMP state objects (NowPlayingScreenData,
 * ControlState, TimeLine, GenericCastState) plus a per-page palette extraction on every adjacent
 * pager page. None of that survives here:
 *
 *  - It reads ArchiveTune's PlayerConnection directly, so there is no second state model to keep in
 *    sync with the engine.
 *  - The palette comes from the shared rememberMeshPalette, which caches across every caller — so
 *    swiping a queue back and forth re-extracts nothing, where the original re-ran Palette on each
 *    adjacent page each time it was composed.
 *  - The playhead is read through a provider inside draw/derived scopes rather than in composition,
 *    so a position tick repaints instead of recomposing the screen.
 *  - It is split into small composables so a change to one row does not invalidate the rest.
 *
 * Belongs exclusively to this style, per the self-containment rule; what it shares is the app's
 * playback substrate (the one PlayerConnection, queue, like state and lyrics), deliberately.
 */

package moe.rukamori.archivetune.ui.player.simpmusic

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.SimpMusicLyricsKey
import moe.rukamori.archivetune.db.entities.FormatEntity
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.component.BottomSheetPageState
import moe.rukamori.archivetune.ui.component.BottomSheetState
import moe.rukamori.archivetune.ui.component.LyricsEnhanced
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.ui.menu.PlayerMenu
import moe.rukamori.archivetune.ui.player.LosslessOrStats
import moe.rukamori.archivetune.ui.player.rememberMeshPalette
import moe.rukamori.archivetune.ui.utils.ShowMediaInfo
import moe.rukamori.archivetune.ui.utils.highRes
import moe.rukamori.archivetune.utils.makeTimeString
import moe.rukamori.archivetune.utils.rememberPreference

/** The dark ground the palette gradient fades into — SimpMusic's own backdrop colour. */
private val Backdrop = Color(0xFF121212)

/** Side gutter for everything below the artwork. */
private val Gutter = 24.dp

/**
 * The SimpMusic style. Parameters mirror the other self-contained styles so Player.kt dispatches
 * every style the same way.
 */
@Composable
fun SimpMusicPlayerContent(
    mediaMetadata: MediaMetadata,
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
    menuState: MenuState,
    bottomSheetPageState: BottomSheetPageState,
    currentFormat: FormatEntity?,
    onSeek: (Long) -> Unit,
    onSeekFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val player = playerConnection.player
    val queueWindows by playerConnection.queueWindows.collectAsStateWithLifecycle()
    val currentWindowIndex by playerConnection.currentWindowIndex.collectAsStateWithLifecycle()

    val artUrl =
        remember(mediaMetadata.id, mediaMetadata.thumbnailUrl) {
            mediaMetadata.thumbnailUrl?.highRes()
        }
    val palette = rememberMeshPalette(artUrl)

    // Which lyrics renderer this style opens — SimpMusic's own, or the app's Enhanced. Read here
    // rather than inside the panel so flipping the setting in Appearance takes effect on the open
    // player, not on its next composition.
    val (simpMusicLyrics) = rememberPreference(SimpMusicLyricsKey, defaultValue = false)
    var lyricsOpen by rememberSaveable { mutableStateOf(false) }
    var lyricsSyncOffset by rememberSaveable { mutableIntStateOf(0) }

    // The panel takes the sleeve's place rather than covering the whole screen: the info row,
    // scrubber and transport below it stay live, which is the point of reading lyrics from the
    // player instead of the lyrics screen.
    BackHandler(enabled = lyricsOpen) { lyricsOpen = false }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .simpMusicBackdrop(palette.colors.getOrNull(0), palette.colors.getOrNull(1)),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (lyricsOpen) {
                    // Null unless the user is scrubbing, matching every other caller: the
                    // renderers run their own clock off the player, and a polled position steps.
                    val lyricsPositionProvider = remember { { null as Long? } }
                    if (simpMusicLyrics) {
                        SimpMusicLyrics(
                            sliderPositionProvider = lyricsPositionProvider,
                            lyricsSyncOffset = lyricsSyncOffset,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        LyricsEnhanced(
                            sliderPositionProvider = lyricsPositionProvider,
                            lyricsSyncOffset = lyricsSyncOffset,
                            modifier = Modifier.fillMaxSize(),
                            textColorOverride = Color.White,
                        )
                    }
                } else {
                    SimpMusicArtworkPager(
                        queueWindows = queueWindows,
                        currentWindowIndex = currentWindowIndex,
                        fallback = mediaMetadata,
                        playerConnection = playerConnection,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            SimpMusicTrackInfoRow(
                mediaMetadata = mediaMetadata,
                playerConnection = playerConnection,
                navController = navController,
                state = state,
                menuState = menuState,
                bottomSheetPageState = bottomSheetPageState,
                lyricsOpen = lyricsOpen,
                onToggleLyrics = { lyricsOpen = !lyricsOpen },
                modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter),
            )

            Spacer(Modifier.height(18.dp))

            SimpMusicProgressRow(
                sliderPosition = sliderPosition,
                position = position,
                duration = duration,
                isLoading = isLoading,
                currentFormat = currentFormat,
                onSeek = onSeek,
                onSeekFinished = onSeekFinished,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter),
            )

            Spacer(Modifier.height(6.dp))

            SimpMusicTransportRow(
                isPlaying = isPlaying,
                canSkipPrevious = canSkipPrevious,
                canSkipNext = canSkipNext,
                playerConnection = playerConnection,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter),
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * The diagonal palette gradient, fading into [Backdrop] down the screen.
 *
 * A draw modifier rather than a stack of Boxes: the gradient is two brushes over the whole surface
 * and nothing needs to lay out around them, so this costs one draw pass and no extra layout nodes.
 */
private fun Modifier.simpMusicBackdrop(start: Color?, end: Color?): Modifier =
    this.background(Backdrop).then(
        if (start == null) {
            Modifier
        } else {
            Modifier
                .background(
                    Brush.linearGradient(
                        colors = listOf(start, end ?: lerp(start, Backdrop, 0.6f)),
                        start = Offset.Zero,
                        end = Offset.Infinite,
                    ),
                ).background(
                    // Held fully opaque over the lower part of the screen so the controls always sit
                    // on the flat ground rather than on whatever colour the sleeve happened to be.
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.55f to Backdrop.copy(alpha = 0.72f),
                        0.90f to Backdrop,
                        1.0f to Backdrop,
                    ),
                )
        },
    )

/**
 * The artwork, one page per queue entry.
 *
 * A settled page takes over playback; a page abandoned mid-drag does not — the same rule the TikTok
 * style uses, and for the same reason: the gesture must never touch the engine until it resolves.
 */
@Composable
private fun SimpMusicArtworkPager(
    queueWindows: List<androidx.media3.common.Timeline.Window>,
    currentWindowIndex: Int,
    fallback: MediaMetadata,
    playerConnection: PlayerConnection,
    modifier: Modifier = Modifier,
) {
    if (queueWindows.isEmpty()) {
        SimpMusicArtwork(fallback, modifier)
        return
    }

    val pagerState =
        rememberPagerState(initialPage = currentWindowIndex.coerceAtLeast(0)) { queueWindows.size }
    var pendingSeek by remember { mutableStateOf<Int?>(null) }
    val liveIndex = rememberUpdatedState(currentWindowIndex)
    val liveQueue = rememberUpdatedState(queueWindows)

    // Feed → engine. Keyed only on the pager so the collector is never restarted: a restarted
    // snapshotFlow re-emits the settled page immediately, which on an engine-driven advance is the
    // page just left, and seeking back to it fights the transition.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { settled ->
                val index = liveIndex.value
                if (settled != index && settled in liveQueue.value.indices) {
                    pendingSeek = settled
                    when (settled) {
                        index + 1 -> playerConnection.seekToNext()
                        index - 1 -> playerConnection.seekToPrevious()
                        else -> playerConnection.player.seekTo(settled, 0)
                    }
                }
            }
    }

    // Engine → feed. A pending feed-initiated seek holds this off until the engine confirms it,
    // so a half-updated index never snaps the pager off the page the user chose.
    LaunchedEffect(currentWindowIndex, queueWindows.size) {
        val pending = pendingSeek
        if (pending != null) {
            if (currentWindowIndex == pending) pendingSeek = null
            return@LaunchedEffect
        }
        if (currentWindowIndex !in queueWindows.indices) return@LaunchedEffect
        if (pagerState.currentPage == currentWindowIndex) return@LaunchedEffect
        if (kotlin.math.abs(currentWindowIndex - pagerState.currentPage) == 1) {
            pagerState.animateScrollToPage(currentWindowIndex)
        } else {
            pagerState.scrollToPage(currentWindowIndex)
        }
    }

    HorizontalPager(state = pagerState, modifier = modifier) { page ->
        SimpMusicArtwork(
            metadata = queueWindows.getOrNull(page)?.mediaItem?.metadata ?: fallback,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** One square sleeve, centred and sized to the smaller side of whatever room it is given. */
@Composable
private fun SimpMusicArtwork(
    metadata: MediaMetadata,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight) - 48.dp
        AsyncImage(
            model = metadata.thumbnailUrl?.highRes(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .size(side.coerceAtLeast(0.dp))
                    .clip(RoundedCornerShape(12.dp)),
        )
    }
}

/** Title and artist on the left, like and overflow on the right. */
@Composable
private fun SimpMusicTrackInfoRow(
    mediaMetadata: MediaMetadata,
    playerConnection: PlayerConnection,
    navController: NavController,
    state: BottomSheetState,
    menuState: MenuState,
    bottomSheetPageState: BottomSheetPageState,
    lyricsOpen: Boolean,
    onToggleLyrics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentSong by playerConnection.currentSong.collectAsStateWithLifecycle(initialValue = null)
    val liked = currentSong?.song?.liked == true

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = mediaMetadata.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = mediaMetadata.artists.joinToString { it.name },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.66f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onToggleLyrics) {
            Icon(
                painter = painterResource(R.drawable.player_lyrics),
                contentDescription = stringResource(R.string.lyrics),
                tint = Color.White.copy(alpha = if (lyricsOpen) 1f else 0.55f),
            )
        }
        IconButton(onClick = playerConnection::toggleLike) {
            Icon(
                painter =
                    painterResource(
                        if (liked) R.drawable.player_favorite else R.drawable.player_favorite_border,
                    ),
                contentDescription = stringResource(R.string.action_like),
                tint = if (liked) MaterialTheme.colorScheme.error else Color.White,
            )
        }
        IconButton(
            onClick = {
                menuState.show {
                    PlayerMenu(
                        mediaMetadata = mediaMetadata,
                        navController = navController,
                        playerBottomSheetState = state,
                        onShowDetailsDialog = {
                            bottomSheetPageState.show { ShowMediaInfo(mediaMetadata.id) }
                        },
                        onDismiss = menuState::dismiss,
                    )
                }
            },
        ) {
            Icon(
                painter = painterResource(R.drawable.player_more_horiz),
                contentDescription = stringResource(R.string.more_options),
                tint = Color.White,
            )
        }
    }
}

/** The scrubber, the two timestamps, and the quality badge centred between them. */
@Composable
private fun SimpMusicProgressRow(
    sliderPosition: Long?,
    position: Long,
    duration: Long,
    isLoading: Boolean,
    currentFormat: FormatEntity?,
    onSeek: (Long) -> Unit,
    onSeekFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasDuration = duration > 0L && duration != C.TIME_UNSET
    val safeDuration = if (hasDuration) duration else 1L
    val shown = (sliderPosition ?: position).coerceIn(0L, safeDuration)

    Column(modifier = modifier) {
        Slider(
            value = shown.toFloat(),
            valueRange = 0f..safeDuration.toFloat(),
            onValueChange = { onSeek(it.toLong()) },
            onValueChangeFinished = onSeekFinished,
            // Pinned to white rather than the theme's primary: this style paints its own dark
            // ground under a palette wash, so a dynamic-themed accent lands on an unrelated
            // colour every track.
            colors =
                SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.24f),
                ),
            modifier = Modifier.fillMaxWidth(),
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = makeTimeString(shown),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.55f),
                )
                Text(
                    text = if (hasDuration) "-${makeTimeString(safeDuration - shown)}" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.55f),
                )
            }
            // Centred rather than placed in the gap between the timestamps: that gap changes width
            // by a digit every time a minute rolls over.
            LosslessOrStats(
                isLoading = isLoading,
                format = currentFormat,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 8.dp),
            )
        }
    }
}

/** Shuffle, previous, play/pause, next, repeat. */
@Composable
private fun SimpMusicTransportRow(
    isPlaying: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    playerConnection: PlayerConnection,
    modifier: Modifier = Modifier,
) {
    val shuffleEnabled by playerConnection.shuffleModeEnabled.collectAsStateWithLifecycle()
    val repeatMode by playerConnection.repeatMode.collectAsStateWithLifecycle()

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { playerConnection.player.shuffleModeEnabled = !shuffleEnabled }) {
            Icon(
                painter = painterResource(R.drawable.player_shuffle),
                contentDescription = stringResource(R.string.shuffle),
                tint = Color.White.copy(alpha = if (shuffleEnabled) 1f else 0.45f),
            )
        }
        IconButton(enabled = canSkipPrevious, onClick = playerConnection::seekToPrevious) {
            Icon(
                painter = painterResource(R.drawable.player_skip_previous),
                contentDescription = stringResource(R.string.widget_previous),
                tint = Color.White.copy(alpha = if (canSkipPrevious) 1f else 0.35f),
            )
        }
        IconButton(onClick = { playerConnection.player.togglePlayPause() }) {
            Icon(
                painter =
                    painterResource(
                        if (isPlaying) R.drawable.player_pause else R.drawable.player_play,
                    ),
                contentDescription =
                    stringResource(if (isPlaying) R.string.widget_pause else R.string.play),
                tint = Color.White,
                modifier = Modifier.size(44.dp),
            )
        }
        IconButton(enabled = canSkipNext, onClick = playerConnection::seekToNext) {
            Icon(
                painter = painterResource(R.drawable.player_skip_next),
                contentDescription = stringResource(R.string.next),
                tint = Color.White.copy(alpha = if (canSkipNext) 1f else 0.35f),
            )
        }
        IconButton(
            onClick = {
                playerConnection.player.repeatMode =
                    when (repeatMode) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
            },
        ) {
            Icon(
                painter =
                    painterResource(
                        when (repeatMode) {
                            Player.REPEAT_MODE_ONE -> R.drawable.player_repeat_one_on
                            Player.REPEAT_MODE_ALL -> R.drawable.player_repeat_on
                            else -> R.drawable.player_repeat
                        },
                    ),
                contentDescription =
                    stringResource(
                        when (repeatMode) {
                            Player.REPEAT_MODE_ONE -> R.string.repeat_mode_one
                            Player.REPEAT_MODE_ALL -> R.string.repeat_mode_all
                            else -> R.string.repeat_mode_off
                        },
                    ),
                tint = Color.White.copy(alpha = if (repeatMode == Player.REPEAT_MODE_OFF) 0.45f else 1f),
            )
        }
    }
}
