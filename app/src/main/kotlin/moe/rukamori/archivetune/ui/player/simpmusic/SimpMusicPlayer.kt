/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

/*
 * SimpMusic player style.
 *
 * The layout is SimpMusic's default now-playing screen — its `NowPlayingContentSpotify`
 * (https://github.com/maxrave-dev/SimpMusic, GPL-3.0). The thing that makes it that screen, and
 * which the first version of this file missed entirely, is that IT SCROLLS: the artwork, info row,
 * scrubber and transport are one screen-height hero, and below the fold sit three cards — lyrics,
 * artist, and track info. Scroll past the hero and a compact toolbar sticks to the top.
 *
 * The hero's vertical rhythm is measured, not guessed. SimpMusic computes
 *
 *     gap = (screenHeight - topBarHeight - artworkHeight - infoLayoutHeight - 30dp) / 2
 *
 * and spends that gap twice: once above the artwork, once below it, where the current lyric line
 * lives. That is why the artwork sits slightly high with the controls gathered under it rather than
 * floating in the middle of an empty screen. The first version here used a `weight(1f)` artwork and
 * a bottom-anchored control stack, which centred the sleeve in ALL the leftover space and left the
 * dead band the screenshot shows.
 *
 * REWRITTEN, not transliterated. SimpMusic is Compose Multiplatform and this screen is one
 * ~1,700-line composable carrying its own state model (NowPlayingScreenData, ControlState, TimeLine,
 * GenericCastState) and re-running Palette on every adjacent pager page. None of that survives:
 *
 *  - it reads ArchiveTune's PlayerConnection directly, so there is no second state model to keep in
 *    sync with the engine;
 *  - the palette comes from the shared rememberMeshPalette, which caches across every caller, so
 *    swiping a queue back and forth re-extracts nothing;
 *  - the playhead is read through a provider inside draw/derived scopes, so a position tick
 *    repaints instead of recomposing the screen;
 *  - it is split into small composables, so a change to one row does not invalidate the rest.
 *
 * Belongs exclusively to this style, per the self-containment rule; what it shares is the app's
 * playback substrate (the one PlayerConnection, queue, like state and lyrics), deliberately.
 */

package moe.rukamori.archivetune.ui.player.simpmusic

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.SimpMusicLyricsKey
import moe.rukamori.archivetune.db.entities.FormatEntity
import moe.rukamori.archivetune.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.MediaInfo
import moe.rukamori.archivetune.lyrics.LyricsUtils.findCurrentLineIndex
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.component.BottomSheetPageState
import moe.rukamori.archivetune.ui.component.BottomSheetState
import moe.rukamori.archivetune.ui.component.LyricsEnhanced
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.ui.menu.PlayerMenu
import moe.rukamori.archivetune.ui.player.AppleMusicQueueSheet
import moe.rukamori.archivetune.ui.player.LosslessOrStats
import moe.rukamori.archivetune.ui.player.rememberInlineLyricLines
import moe.rukamori.archivetune.ui.player.rememberMeshPalette
import moe.rukamori.archivetune.ui.utils.ShowMediaInfo
import moe.rukamori.archivetune.ui.utils.highRes
import moe.rukamori.archivetune.utils.makeTimeString
import moe.rukamori.archivetune.utils.rememberPreference
import java.util.Locale

/** The dark ground the palette gradient fades into — SimpMusic's own backdrop colour. */
private val Backdrop = Color(0xFF121212)

/** The artist card's panel, the one card SimpMusic keeps off the palette. */
private val CardPanel = Color(0xFF212121)

/** A YouTube video id: 11 chars of the URL-safe alphabet. Nothing else resolves in getMediaInfo. */
private val YOUTUBE_ID = Regex("^[A-Za-z0-9_-]{11}$")

/** Side gutter for everything below the artwork, and for the cards. */
private val Gutter = 20.dp

/**
 * Floor for the gap above and below the artwork. SimpMusic's `minimumPaddingDp`: when the screen is
 * too short for the computed gap, the artwork shrinks into this rather than the controls sliding off.
 */
private val MinGap = 30.dp

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
    val density = LocalDensity.current
    val queueWindows by playerConnection.queueWindows.collectAsStateWithLifecycle()
    val currentWindowIndex by playerConnection.currentWindowIndex.collectAsStateWithLifecycle()

    val artUrl =
        remember(mediaMetadata.id, mediaMetadata.thumbnailUrl) {
            mediaMetadata.thumbnailUrl?.highRes()
        }
    val palette = rememberMeshPalette(artUrl)
    val startColor = palette.colors.getOrNull(0) ?: Backdrop
    val endColor = palette.colors.getOrNull(1) ?: lerp(startColor, Backdrop, 0.6f)

    // The two lower cards are YouTube facts about the track, and only a YouTube id can produce
    // them. Gated on the id SHAPE rather than fired blindly: a Tidal, Qobuz, Spotify or local id
    // can never resolve here, so without this every skip on those sources spent a network
    // round-trip to be told so. Each card still hides itself when the lookup returns nothing.
    var mediaInfo by remember(mediaMetadata.id) { mutableStateOf<MediaInfo?>(null) }
    LaunchedEffect(mediaMetadata.id) {
        if (!YOUTUBE_ID.matches(mediaMetadata.id)) return@LaunchedEffect
        mediaInfo = runCatching { YouTube.getMediaInfo(mediaMetadata.id).getOrNull() }.getOrNull()
    }

    val scrollState = rememberScrollState()
    // Latched, not a live predicate: once the reader has gone below the fold, keep the card's
    // contents mounted rather than tearing the renderer down every time they scroll back up.
    var hasScrolled by remember { mutableStateOf(false) }
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value > 0 }.first { it }
        hasScrolled = true
    }
    var queueOpen by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = queueOpen) { queueOpen = false }

    // Measured, not guessed — see the file header. Held in dp so the gap survives a rotation.
    // Only the two rows whose height depends on their CONTENT are measured; the artwork is derived
    // from what is left, which is what keeps this from being a layout feedback loop.
    var topBarHeight by remember { mutableStateOf(0.dp) }
    var infoHeight by remember { mutableStateOf(0.dp) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // The viewport, captured OUTSIDE the scrolling Column: inside it the height constraint is
        // Infinity, so this is the only place the screen height can be read.
        val screenHeight = maxHeight
        // A square as wide as the gutters allow — unless the screen is too SHORT for that square
        // plus the two rows and their minimum gaps, in which case the square gives way rather than
        // the controls sliding off the bottom. SimpMusic sizes the artwork on width alone and
        // clips the controls on a short screen; there is no reason to reproduce that.
        val artworkSide =
            (maxWidth - Gutter * 2)
                .coerceAtMost(screenHeight - topBarHeight - infoHeight - MinGap * 2)
                .coerceAtLeast(0.dp)
        val gap =
            ((screenHeight - topBarHeight - artworkSide - infoHeight - MinGap) / 2)
                .coerceAtLeast(MinGap)
        val screenHeightPx = with(density) { screenHeight.toPx() }

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Backdrop)
                    .simpMusicHeroWash(startColor, endColor, screenHeightPx)
                    // Gated on the sheet being expanded, so a drag on the collapsed mini-player is
                    // never eaten by this. Up-drags at the top still reach the sheet through the
                    // nested-scroll connection the caller attached.
                    .verticalScroll(scrollState, enabled = state.isExpanded),
        ) {
            // ── HERO: exactly one screen ─────────────────────────────────────────────────────
            Box(modifier = Modifier.fillMaxWidth().height(screenHeight)) {
                SimpMusicArtworkPager(
                    queueWindows = queueWindows,
                    currentWindowIndex = currentWindowIndex,
                    fallback = mediaMetadata,
                    playerConnection = playerConnection,
                    topInset = topBarHeight + gap,
                    side = artworkSide,
                    modifier = Modifier.fillMaxWidth().height(screenHeight),
                )

                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(topBarHeight))
                    Spacer(Modifier.height(gap))

                    // Reserves the artwork's space without drawing it — the pager behind owns the
                    // pixels. A Spacer takes no pointer input, so swipes fall through to the pager.
                    Spacer(Modifier.fillMaxWidth().height(artworkSide))

                    // The current lyric line lives INSIDE the lower gap rather than adding height
                    // of its own, so the controls below never move when a line arrives or leaves.
                    SimpMusicLyricLine(
                        playerConnection = playerConnection,
                        // Collapsed, this composable stays composed but nothing it draws is on
                        // screen, so the poll below is pure background cost. `active` stops it.
                        active = isPlaying && state.isExpanded,
                        modifier = Modifier.fillMaxWidth().height(gap),
                    )

                    Column(
                        modifier =
                            Modifier.onGloballyPositioned {
                                infoHeight = with(density) { it.size.height.toDp() }
                            },
                    ) {
                        SimpMusicTrackInfoRow(
                            mediaMetadata = mediaMetadata,
                            playerConnection = playerConnection,
                            navController = navController,
                            state = state,
                            menuState = menuState,
                            bottomSheetPageState = bottomSheetPageState,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter),
                        )

                        Spacer(Modifier.height(15.dp))

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

                        SimpMusicActionRow(
                            mediaId = mediaMetadata.id,
                            bottomSheetPageState = bottomSheetPageState,
                            onOpenQueue = { queueOpen = true },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter),
                        )
                    }
                }

                SimpMusicTopBar(
                    title = mediaMetadata.title,
                    onCollapse = state::collapseSoft,
                    onMenu = {
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
                    modifier =
                        Modifier.fillMaxWidth().onGloballyPositioned {
                            topBarHeight = with(density) { it.size.height.toDp() }
                        },
                )
            }

            // ── BELOW THE FOLD ───────────────────────────────────────────────────────────────
            Column(modifier = Modifier.padding(horizontal = Gutter)) {
                SimpMusicLyricsCard(
                    playerConnection = playerConnection,
                    containerColor = startColor,
                    // The card's frame is composed from the start so the page has something to
                    // scroll to; the RENDERER inside it only mounts once the user actually scrolls.
                    // Both renderers drive their own frame clock — LyricsEnhanced polls at 16ms on
                    // a word-synced track — and `verticalScroll` composes every child regardless of
                    // visibility, so without this a karaoke loop ran permanently, from the moment
                    // the player opened, for a card nobody had scrolled to.
                    renderLyrics = hasScrolled,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Spacer(Modifier.height(10.dp))
                SimpMusicArtistCard(
                    info = mediaInfo,
                    onOpenArtist = { id -> navController.navigate("artist/$id") },
                )
                Spacer(Modifier.height(10.dp))
                SimpMusicInfoCard(info = mediaInfo, containerColor = startColor)
                Spacer(Modifier.height(10.dp))
                Spacer(
                    Modifier.height(
                        WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
                    ),
                )
            }
        }

        // Sticky compact header, once the hero's controls have scrolled away.
        // Appears once the hero's controls have gone, so the toolbar is what replaces them rather
        // than something that overlaps them. Derived from the hero's own height, not a magic number.
        val toolbarVisible by remember(screenHeightPx) {
            derivedStateOf { scrollState.value > screenHeightPx * 0.6f }
        }
        AnimatedVisibility(
            visible = toolbarVisible && state.isExpanded,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically(),
        ) {
            SimpMusicStickyToolbar(
                mediaMetadata = mediaMetadata,
                isPlaying = isPlaying,
                playerConnection = playerConnection,
                containerColor = lerp(startColor, Color.Black, 0.18f),
            )
        }

        if (queueOpen) {
            // Scrim first: the sheet's own rows are translucent, so without something behind them
            // the player's artwork and controls read straight through the queue.
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))) {
                AppleMusicQueueSheet(
                    navController = navController,
                    playerBottomSheetState = state,
                    onClose = { queueOpen = false },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/**
 * The diagonal palette gradient over the first screen height only, fading into [Backdrop].
 *
 * `drawBehind` rather than two `background()` calls: a background brush stretches over the whole
 * scrollable content, which for a page several screens tall means the gradient never actually
 * finishes — it just keeps going as you scroll. Painting a fixed [screenHeightPx]-tall rect pins it
 * to the hero, which is the only place it belongs.
 */
private fun Modifier.simpMusicHeroWash(
    start: Color,
    end: Color,
    screenHeightPx: Float,
): Modifier =
    this.drawBehind {
        val area = Size(size.width, screenHeightPx)
        drawRect(
            brush =
                Brush.linearGradient(
                    colors = listOf(start, end),
                    start = Offset.Zero,
                    end = Offset(size.width, screenHeightPx),
                ),
            size = area,
        )
        // Held opaque from 95% down so the hero meets the cards below with no seam. A stop on
        // the diagonal brush above could not do this: it would arrive in one corner only and
        // leave a visible diagonal edge across the width.
        drawRect(
            brush =
                Brush.verticalGradient(
                    0.0f to Backdrop.copy(alpha = 0f),
                    0.55f to Backdrop.copy(alpha = 0.45f),
                    0.95f to Backdrop,
                    startY = 0f,
                    endY = screenHeightPx,
                ),
            size = area,
        )
    }

/** Collapse chevron, "NOW PLAYING" over the track title, and the overflow menu. */
@Composable
private fun SimpMusicTopBar(
    title: String,
    onCollapse: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .padding(WindowInsets.statusBars.asPaddingValues())
                .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onCollapse) {
            Icon(
                painter = painterResource(R.drawable.player_expand_more),
                contentDescription = stringResource(R.string.collapse),
                tint = Color.White,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.now_playing).uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onMenu) {
            Icon(
                painter = painterResource(R.drawable.player_more_vert),
                contentDescription = stringResource(R.string.more_options),
                tint = Color.White,
            )
        }
    }
}

/**
 * The artwork, one page per queue entry, sitting at [topInset] down a full-screen-tall pager.
 *
 * A settled page takes over playback; a page abandoned mid-drag does not — the same rule the TikTok
 * style uses, and for the same reason: the gesture must never touch the engine until it resolves.
 */
@Composable
private fun SimpMusicArtworkPager(
    queueWindows: List<Timeline.Window>,
    currentWindowIndex: Int,
    fallback: MediaMetadata,
    playerConnection: PlayerConnection,
    topInset: androidx.compose.ui.unit.Dp,
    side: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    if (queueWindows.isEmpty()) {
        SimpMusicArtwork(fallback, topInset, side, modifier)
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

    HorizontalPager(state = pagerState, beyondViewportPageCount = 1, modifier = modifier) { page ->
        SimpMusicArtwork(
            metadata = queueWindows.getOrNull(page)?.mediaItem?.metadata ?: fallback,
            topInset = topInset,
            side = side,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** One [side]-square sleeve, placed at [topInset] so it lands on the space the hero reserved. */
@Composable
private fun SimpMusicArtwork(
    metadata: MediaMetadata,
    topInset: androidx.compose.ui.unit.Dp,
    side: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(topInset))
        AsyncImage(
            model = metadata.thumbnailUrl?.highRes(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(side).clip(RoundedCornerShape(8.dp)),
        )
    }
}

/**
 * The line being sung, centred in the gap under the sleeve. Empty when there is nothing synced —
 * [rememberInlineLyricLines] is the shared "which formats count as synced" decision, so this line
 * appears in exactly the cases the other styles' inline lyrics do.
 */
@Composable
private fun SimpMusicLyricLine(
    playerConnection: PlayerConnection,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val lines = rememberInlineLyricLines(playerConnection)
    var line by remember(lines) { mutableStateOf("") }

    // Polled rather than derived from a recomposing position: a line changes a few times a minute,
    // so a 200ms tick is already far finer than it needs while costing almost nothing. Stops
    // entirely when there is nothing to show, or when nothing is watching.
    LaunchedEffect(lines, active) {
        if (lines.isEmpty() || !active) {
            line = ""
            return@LaunchedEffect
        }
        while (true) {
            val index = findCurrentLineIndex(lines, playerConnection.player.currentPosition)
            line = lines.getOrNull(index)?.text.orEmpty()
            delay(200L)
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Crossfade(targetState = line, animationSpec = tween(300), label = "simpMusicLyricLine") { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                maxLines = 1,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Gutter)
                        .basicMarquee(
                            iterations = Int.MAX_VALUE,
                            animationMode = MarqueeAnimationMode.Immediately,
                        ),
            )
        }
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

/** Track details on the left, the queue on the right — SimpMusic's row under the transport. */
@Composable
private fun SimpMusicActionRow(
    mediaId: String,
    bottomSheetPageState: BottomSheetPageState,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { bottomSheetPageState.show { ShowMediaInfo(mediaId) } }) {
            Icon(
                painter = painterResource(R.drawable.info),
                contentDescription = stringResource(R.string.details),
                tint = Color.White.copy(alpha = 0.8f),
            )
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onOpenQueue) {
            Icon(
                painter = painterResource(R.drawable.player_queue_music),
                contentDescription = stringResource(R.string.queue),
                tint = Color.White.copy(alpha = 0.8f),
            )
        }
    }
}

/**
 * The lyrics card. Which renderer it holds is the SimpMusic-lyrics setting — SimpMusic's own view,
 * or the app's Enhanced one. Fixed height, like SimpMusic's: a card that grew with the lyric count
 * would push the two cards under it an unpredictable distance down the page.
 */
@Composable
private fun SimpMusicLyricsCard(
    playerConnection: PlayerConnection,
    containerColor: Color,
    renderLyrics: Boolean,
    modifier: Modifier = Modifier,
) {
    val (simpMusicLyrics) = rememberPreference(SimpMusicLyricsKey, defaultValue = false)
    // Null unless the user is scrubbing, matching every other caller: the renderers run their own
    // clock off the player, and a polled position would step.
    val lyricsPositionProvider = remember { { null as Long? } }

    // A renderer with nothing to render still fills its 300dp box, so without this the card was a
    // blank panel on every track with no lyrics — and it pushed the two real cards down behind it.
    val lyricsEntity by playerConnection.currentLyrics.collectAsStateWithLifecycle(initialValue = null)
    val hasLyrics =
        lyricsEntity?.lyrics?.isNotBlank() == true && lyricsEntity?.lyrics != LYRICS_NOT_FOUND
    if (!hasLyrics) return

    ElevatedCard(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.elevatedCardColors().copy(containerColor = containerColor),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(15.dp)) {
            Text(
                text = stringResource(R.string.lyrics),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
            )
            Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                if (!renderLyrics) {
                    // Deliberately empty, and deliberately still 300dp: the height is what keeps
                    // the page scrollable so `renderLyrics` can ever become true.
                } else if (simpMusicLyrics) {
                    SimpMusicLyrics(
                        sliderPositionProvider = lyricsPositionProvider,
                        lyricsSyncOffset = 0,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    LyricsEnhanced(
                        sliderPositionProvider = lyricsPositionProvider,
                        lyricsSyncOffset = 0,
                        modifier = Modifier.fillMaxSize(),
                        textColorOverride = Color.White,
                    )
                }
            }
        }
    }
}

/** Artist photo, name and subscriber count. Hidden when the track has no YouTube author. */
@Composable
private fun SimpMusicArtistCard(
    info: MediaInfo?,
    onOpenArtist: (String) -> Unit,
) {
    AnimatedVisibility(visible = info?.author != null) {
        val author = info?.author.orEmpty()
        val authorId = info?.authorId
        ElevatedCard(
            onClick = { authorId?.let(onOpenArtist) },
            enabled = authorId != null,
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.elevatedCardColors().copy(containerColor = CardPanel),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.fillMaxWidth().height(250.dp)) {
                    AsyncImage(
                        model = info?.authorThumbnail,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // Artist photos are often bright at the top, which swallowed the label.
                    Box(
                        modifier =
                            Modifier
                                .matchParentSize()
                                .background(
                                    Brush.verticalGradient(
                                        0f to Color.Black.copy(alpha = 0.6f),
                                        0.4f to Color.Transparent,
                                    ),
                                ),
                    )
                    Text(
                        text = stringResource(R.string.artists),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.TopStart).padding(15.dp),
                    )
                }
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 12.dp)) {
                    Text(
                        text = author,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                    info?.subscribers?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
    }
}

/** Publish date, view count, likes and the description. Hidden when none of it resolved. */
@Composable
private fun SimpMusicInfoCard(
    info: MediaInfo?,
    containerColor: Color,
) {
    AnimatedVisibility(visible = info?.viewCount != null || info?.description != null) {
        ElevatedCard(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.elevatedCardColors().copy(containerColor = containerColor),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(15.dp).fillMaxWidth()) {
                info?.uploadDate?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = stringResource(R.string.published_on, it),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                    )
                    Spacer(Modifier.height(10.dp))
                }
                info?.viewCount?.let {
                    Text(
                        text = stringResource(R.string.view_count_value, groupDigits(it)),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                    )
                    Spacer(Modifier.height(10.dp))
                }
                if (info?.like != null || info?.dislike != null) {
                    Text(
                        text =
                            stringResource(
                                R.string.like_and_dislike,
                                groupDigits(info.like ?: 0),
                                groupDigits(info.dislike ?: 0),
                            ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                    Spacer(Modifier.height(10.dp))
                }
                info?.description?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = stringResource(R.string.description),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

/** Compact header that sticks to the top once the hero's controls have scrolled away. */
@Composable
private fun SimpMusicStickyToolbar(
    mediaMetadata: MediaMetadata,
    isPlaying: Boolean,
    playerConnection: PlayerConnection,
    containerColor: Color,
) {
    val currentSong by playerConnection.currentSong.collectAsStateWithLifecycle(initialValue = null)
    val liked = currentSong?.song?.liked == true

    ElevatedCard(
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.elevatedCardColors().copy(containerColor = containerColor),
        elevation = CardDefaults.elevatedCardElevation(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier =
                Modifier
                    .padding(WindowInsets.statusBars.asPaddingValues())
                    .padding(start = Gutter, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mediaMetadata.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = mediaMetadata.artists.joinToString { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.66f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
            IconButton(onClick = { playerConnection.player.togglePlayPause() }) {
                Icon(
                    painter =
                        painterResource(
                            if (isPlaying) R.drawable.player_pause else R.drawable.player_play,
                        ),
                    contentDescription =
                        stringResource(if (isPlaying) R.string.widget_pause else R.string.play),
                    tint = Color.White,
                )
            }
        }
    }
}

/** `%,d` without pulling a formatter in — the counts here are plain integers. */
private fun groupDigits(value: Int): String = String.format(Locale.getDefault(), "%,d", value)
