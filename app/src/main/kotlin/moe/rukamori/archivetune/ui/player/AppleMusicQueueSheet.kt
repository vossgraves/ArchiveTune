/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * ViviMusic-style in-place queue sheet for the Apple Music player design.
 * Ported from vivi-music Queue_v2.kt (commits 31797caf + e556187d) —
 * https://github.com/vivizzz007/vivi-music — and adapted to ArchiveTune's
 * package structure, PlayerConnection API, and shared component library.
 *
 * The sheet renders three fixed "pill" controls (Shuffle / Repeat / Sleep
 * Timer), a "Queue" header row with an edit-lock toggle, and a reorderable
 * LazyColumn of the current + upcoming queue items. It is designed to be
 * hosted inside the Apple Music player's morphing content area — it does
 * NOT manage its own BottomSheet (the player morphs to reveal this content
 * in place, ViviMusic-style).
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package moe.rukamori.archivetune.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.QueueEditLockKey
import moe.rukamori.archivetune.extensions.move
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.extensions.toggleRepeatMode
import moe.rukamori.archivetune.ui.component.BottomSheetState
import moe.rukamori.archivetune.ui.component.LocalBottomSheetPageState
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.MediaMetadataListItem
import moe.rukamori.archivetune.ui.menu.PlayerMenu
import moe.rukamori.archivetune.ui.utils.ShowMediaInfo
import moe.rukamori.archivetune.utils.makeTimeString
import moe.rukamori.archivetune.utils.rememberPreference
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private val QueuePillHeight = 48.dp
private val QueuePillCornerRadius = 16.dp

/**
 * In-place queue sheet for the Apple Music player. Ported from ViviMusic's
 * QueueV2 with the following adaptations:
 *
 * - Uses ArchiveTune's [LocalPlayerConnection] / [LocalMenuState] /
 *   [LocalBottomSheetPageState] instead of ViviMusic's locals.
 * - Renders on a transparent background — the Apple Music player's blurred
 *   artwork + scrim shows through, matching ViviMusic's "queue-on-blur"
 *   look exactly.
 * - Uses [PlayerMenu] (with `isQueueTrigger = true`) instead of ViviMusic's
 *   dedicated QueueMenu — ArchiveTune consolidates both menus.
 * - Uses ArchiveTune's [MediaMetadataListItem]; wraps it in a clipped Box
 *   so the active / idle pill-style background matches ViviMusic.
 * - Defaults the edit-lock to TRUE (ArchiveTune's default), unlike
 *   ViviMusic which defaults to false. Users who want swipe-to-remove can
 *   unlock explicitly via the lock toggle in the header.
 *
 * @param navController The app's NavController, forwarded to PlayerMenu for
 *   "go to album / artist" actions.
 * @param playerBottomSheetState The outer player BottomSheetState, forwarded
 *   to PlayerMenu so it can collapse the player before navigating.
 * @param modifier The modifier applied to the root Column.
 */
@Composable
fun AppleMusicQueueSheet(
    navController: NavController,
    playerBottomSheetState: BottomSheetState,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val bottomSheetPageState = LocalBottomSheetPageState.current

    val queueWindows by playerConnection.queueWindows.collectAsState()
    val currentWindowIndex by playerConnection.currentWindowIndex.collectAsState()
    val shuffleModeEnabled by playerConnection.shuffleModeEnabled.collectAsState()
    val repeatMode by playerConnection.repeatMode.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()

    var locked by rememberPreference(QueueEditLockKey, defaultValue = true)

    // Sleep timer state — mirrors Queue.kt's sleep-timer block so the pill
    // shows the remaining time and toggles the timer correctly.
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    val sleepTimerEnabled =
        remember(
            playerConnection.service.sleepTimer.triggerTime,
            playerConnection.service.sleepTimer.pauseWhenSongEnd,
        ) {
            playerConnection.service.sleepTimer.isActive
        }
    var sleepTimerTimeLeft by remember { mutableStateOf(0L) }

    LaunchedEffect(sleepTimerEnabled) {
        if (sleepTimerEnabled) {
            while (isActive) {
                sleepTimerTimeLeft =
                    if (playerConnection.service.sleepTimer.pauseWhenSongEnd) {
                        playerConnection.player.duration - playerConnection.player.currentPosition
                    } else {
                        playerConnection.service.sleepTimer.triggerTime - System.currentTimeMillis()
                    }
                delay(1000L)
            }
        }
    }

    // Adaptive text colors — the sheet renders on the Apple Music blurred
    // artwork, so foreground is always white-tinted (mirrors ViviMusic's
    // adaptivePrimary/Secondary for non-DEFAULT backgrounds).
    val adaptivePrimary = Color.White
    val adaptiveSecondary = Color.White.copy(alpha = 0.7f)
    val adaptiveSurface = Color.White.copy(alpha = 0.2f)

    val lazyListState = rememberLazyListState()
    val mutableQueueWindows = remember { mutableStateListOf<Timeline.Window>() }
    var dragInfo by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    val currentPlayingUid =
        remember(currentWindowIndex, queueWindows) {
            if (currentWindowIndex in queueWindows.indices) {
                queueWindows[currentWindowIndex].uid
            } else {
                null
            }
        }

    // Mirror Queue.kt's filter: show only the current song + upcoming songs.
    // Previously-played songs are excluded so the currently playing track is
    // always at the top — matching Apple Music / Spotify behavior.
    LaunchedEffect(queueWindows, currentWindowIndex) {
        Snapshot.withMutableSnapshot {
            mutableQueueWindows.clear()
            val startIndex = currentWindowIndex.coerceAtLeast(0)
            mutableQueueWindows.addAll(queueWindows.drop(startIndex))
        }
    }

    // Auto-scroll to the current song when the queue first appears. We use
    // a "has scrolled once" guard so the user is free to browse the queue
    // after opening it without being yanked back.
    var hasScrolledToCurrent by remember { mutableStateOf(false) }
    LaunchedEffect(mutableQueueWindows.size, currentPlayingUid) {
        if (!hasScrolledToCurrent && currentPlayingUid != null) {
            val idx = mutableQueueWindows.indexOfFirst { it.uid == currentPlayingUid }
            if (idx != -1) {
                lazyListState.scrollToItem(idx)
                hasScrolledToCurrent = true
            }
        }
    }

    val reorderableState =
        rememberReorderableLazyListState(
            lazyListState = lazyListState,
        ) { from, to ->
            val currentDragInfo = dragInfo
            dragInfo =
                if (currentDragInfo == null) {
                    from.index to to.index
                } else {
                    currentDragInfo.first to to.index
                }

            val safeFrom = from.index.coerceIn(0, mutableQueueWindows.lastIndex)
            val safeTo = to.index.coerceIn(0, mutableQueueWindows.lastIndex)
            if (safeFrom in mutableQueueWindows.indices && safeTo in mutableQueueWindows.indices) {
                mutableQueueWindows.move(safeFrom, safeTo)
            }
        }

    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging) {
            dragInfo?.let { (from, to) ->
                val safeFrom = from.coerceIn(0, queueWindows.lastIndex)
                val safeTo = to.coerceIn(0, queueWindows.lastIndex)
                if (safeFrom != safeTo) {
                    if (!shuffleModeEnabled) {
                        playerConnection.player.moveMediaItem(safeFrom, safeTo)
                    } else {
                        playerConnection.localPlayer.setShuffleOrder(
                            DefaultShuffleOrder(
                                queueWindows
                                    .map { it.firstPeriodIndex }
                                    .toMutableList()
                                    .move(safeFrom, safeTo)
                                    .toIntArray(),
                                System.currentTimeMillis(),
                            ),
                        )
                    }
                }
                dragInfo = null
            }
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Transparent),
    ) {
        // Fixed top control pills: Shuffle / Repeat / Sleep Timer.
        // Matches ViviMusic QueueV2's pill row exactly — same 24/12 padding,
        // same 8dp spacing, same active/inactive alpha treatment.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val pillShape = RoundedCornerShape(QueuePillCornerRadius)
            val activeColor = adaptivePrimary.copy(alpha = 0.25f)
            val inactiveColor = adaptivePrimary.copy(alpha = 0.1f)

            // Shuffle pill — uses the Material "all_inclusive" (∞) drawable
            // instead of the crossed-arrows shuffle icon, per user request.
            // The pill still toggles shuffleModeEnabled; only the visual
            // changes. Using the 24dp vector drawable (same as the Repeat
            // and Sleep Timer pills) so the infinity glyph matches the size
            // of the other icons exactly — the previous Text-based ∞ glyph
            // rendered smaller than 24dp because of font metrics.
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(QueuePillHeight)
                        .background(if (shuffleModeEnabled) activeColor else inactiveColor, pillShape)
                        .clip(pillShape)
                        .clickable {
                            playerConnection.player.shuffleModeEnabled = !shuffleModeEnabled
                        },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.all_inclusive),
                    contentDescription = "Shuffle",
                    tint = adaptivePrimary,
                    modifier = Modifier.size(24.dp),
                )
            }
            // Repeat pill.
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(QueuePillHeight)
                        .background(if (repeatMode != Player.REPEAT_MODE_OFF) activeColor else inactiveColor, pillShape)
                        .clip(pillShape)
                        .clickable { playerConnection.player.toggleRepeatMode() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter =
                        painterResource(
                            when (repeatMode) {
                                Player.REPEAT_MODE_ONE -> R.drawable.repeat_one
                                else -> R.drawable.repeat
                            },
                        ),
                    contentDescription = "Repeat",
                    tint = adaptivePrimary,
                    modifier = Modifier.size(24.dp),
                )
            }
            // Sleep timer pill.
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(QueuePillHeight)
                        .background(if (sleepTimerEnabled) activeColor else inactiveColor, pillShape)
                        .clip(pillShape)
                        .clickable {
                            if (sleepTimerEnabled) {
                                playerConnection.service.sleepTimer.clear()
                            } else {
                                showSleepTimerDialog = true
                            }
                        },
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.bedtime),
                        contentDescription = "Sleep Timer",
                        tint = adaptivePrimary,
                        modifier = Modifier.size(24.dp),
                    )
                    if (sleepTimerEnabled) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = makeTimeString(sleepTimerTimeLeft.coerceAtLeast(0L)),
                            style = MaterialTheme.typography.labelSmall,
                            color = adaptivePrimary,
                        )
                    }
                }
            }
        }

        // Queue header row: title + edit-lock toggle.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.queue),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = adaptivePrimary,
            )
            IconButton(onClick = { locked = !locked }) {
                Icon(
                    painter = painterResource(if (locked) R.drawable.lock else R.drawable.lock_open),
                    contentDescription = if (locked) "Unlock Queue" else "Lock Queue",
                    tint = adaptiveSecondary,
                )
            }
        }

        // Reorderable queue list.
        LazyColumn(
            state = lazyListState,
            contentPadding = PaddingValues(bottom = 16.dp, top = 4.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(
                items = mutableQueueWindows,
                key = { _, item -> item.uid.hashCode() },
            ) { index, window ->
                ReorderableItem(
                    state = reorderableState,
                    key = window.uid.hashCode(),
                ) {
                    val isActive = window.uid == currentPlayingUid
                    val metadata = window.mediaItem.metadata ?: return@ReorderableItem

                    val dismissBoxState =
                        rememberSwipeToDismissBoxState(
                            positionalThreshold = { totalDistance -> totalDistance },
                        )
                    var processedDismiss by remember { mutableStateOf(false) }

                    LaunchedEffect(dismissBoxState.currentValue) {
                        val dv = dismissBoxState.currentValue
                        if (!processedDismiss &&
                            (dv == SwipeToDismissBoxValue.StartToEnd || dv == SwipeToDismissBoxValue.EndToStart)
                        ) {
                            processedDismiss = true
                            playerConnection.player.removeMediaItem(window.firstPeriodIndex)
                        }
                        if (dv == SwipeToDismissBoxValue.Settled) {
                            processedDismiss = false
                        }
                    }

                    val content: @Composable () -> Unit = {
                        // ViviMusic-style glassy pill background for each row.
                        // Active row is more opaque than idle, matching the
                        // adaptiveSurface.copy(alpha = 0.4f / 0.15f) treatment
                        // from QueueV2.kt — but toned down (0.22 / 0.10) because
                        // the original 0.4 was reported as too bright/glitchy
                        // against the frosted-glass blur behind the sheet.
                        val rowBg =
                            if (isActive) adaptiveSurface.copy(alpha = 0.22f) else adaptiveSurface.copy(alpha = 0.10f)
                        val rowShape = RoundedCornerShape(12.dp)

                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .clip(rowShape)
                                    .background(rowBg),
                        ) {
                            MediaMetadataListItem(
                                mediaMetadata = metadata,
                                isSelected = false,
                                isActive = isActive,
                                isPlaying = isPlaying && isActive,
                                shouldLoadImage = true,
                                // The sheet already paints a glassy pill behind the row
                                // (rowBg = adaptiveSurface.copy(alpha = 0.4f / 0.15f)).
                                // Letting ListItem also paint secondaryContainer on top
                                // produced a bright, opaque, glitchy highlight that
                                // fought the glass tint. Suppress the container so only
                                // the glass pill shows.
                                showActiveContainer = false,
                                trailingContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = {
                                                menuState.show {
                                                    PlayerMenu(
                                                        mediaMetadata = metadata,
                                                        navController = navController,
                                                        playerBottomSheetState = playerBottomSheetState,
                                                        isQueueTrigger = true,
                                                        onShowDetailsDialog = {
                                                            window.mediaItem.mediaId.let {
                                                                bottomSheetPageState.show {
                                                                    ShowMediaInfo(it)
                                                                }
                                                            }
                                                        },
                                                        onDismiss = menuState::dismiss,
                                                    )
                                                }
                                            },
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.more_vert),
                                                contentDescription = "Options",
                                                tint = adaptivePrimary,
                                            )
                                        }

                                        if (!locked) {
                                            IconButton(
                                                onClick = { },
                                                modifier = Modifier.draggableHandle(),
                                            ) {
                                                Icon(
                                                    painter = painterResource(R.drawable.drag_handle),
                                                    contentDescription = "Drag to reorder",
                                                    tint = adaptivePrimary,
                                                )
                                            }
                                        }
                                    }
                                },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            playerConnection.player.seekToDefaultPosition(window.firstPeriodIndex)
                                            playerConnection.player.playWhenReady = true
                                        },
                            )
                        }
                    }

                    if (locked) {
                        content()
                    } else {
                        SwipeToDismissBox(
                            state = dismissBoxState,
                            backgroundContent = {
                                val color by animateColorAsState(
                                    targetValue =
                                        when (dismissBoxState.targetValue) {
                                            SwipeToDismissBoxValue.Settled -> Color.Transparent
                                            else -> MaterialTheme.colorScheme.error
                                        },
                                    label = "swipeDismissBg",
                                )
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .padding(vertical = 4.dp, horizontal = 16.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(color),
                                    contentAlignment = Alignment.CenterEnd,
                                ) {
                                    val iconAlpha by animateFloatAsState(
                                        targetValue = if (dismissBoxState.targetValue != SwipeToDismissBoxValue.Settled) 1f else 0f,
                                        label = "iconAlpha",
                                    )
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Remove",
                                        modifier =
                                            Modifier
                                                .padding(end = 16.dp)
                                                .alpha(iconAlpha),
                                        tint = MaterialTheme.colorScheme.onError,
                                    )
                                }
                            },
                            content = { content() },
                            enableDismissFromStartToEnd = false,
                        )
                    }
                }
            }
        }
    }

    if (showSleepTimerDialog) {
        SleepTimerDialog(
            onDismiss = { showSleepTimerDialog = false },
            onConfirm = { minutes ->
                showSleepTimerDialog = false
                playerConnection.service.sleepTimer.start(minutes)
            },
            onEndOfSong = {
                showSleepTimerDialog = false
                playerConnection.service.sleepTimer.start(-1)
            },
            initialValue = 30f,
        )
    }
}
