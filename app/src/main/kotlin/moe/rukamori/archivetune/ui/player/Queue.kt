/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.player

import android.annotation.SuppressLint
import android.view.ViewTreeObserver
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AutoLoadMoreKey
import moe.rukamori.archivetune.constants.EnableHapticFeedbackKey
import moe.rukamori.archivetune.constants.ListItemHeight
import moe.rukamori.archivetune.constants.PlayerDesignStyle
import moe.rukamori.archivetune.constants.PlayerDesignStyleKey
import moe.rukamori.archivetune.constants.QueueEditLockKey
import moe.rukamori.archivetune.db.entities.PlaylistEntity
import moe.rukamori.archivetune.db.entities.PlaylistSongMap
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.extensions.move
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.extensions.toggleRepeatMode
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.ui.component.BottomSheet
import moe.rukamori.archivetune.ui.component.BottomSheetState
import moe.rukamori.archivetune.ui.component.LocalBottomSheetPageState
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.TextFieldDialog
import moe.rukamori.archivetune.ui.menu.AddToPlaylistDialog
import moe.rukamori.archivetune.ui.menu.PlayerMenu
import moe.rukamori.archivetune.ui.utils.ShowMediaInfo
import moe.rukamori.archivetune.utils.oem.SystemMediaControlResolver
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.time.LocalDateTime

@SuppressLint("UnrememberedMutableState")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Queue(
    state: BottomSheetState,
    playerBottomSheetState: BottomSheetState,
    navController: NavController,
    modifier: Modifier = Modifier,
    backgroundColor: Color,
    onBackgroundColor: Color,
    TextBackgroundColor: Color,
    textButtonColor: Color,
    iconButtonColor: Color,
    onShowLyrics: () -> Unit = {},
    pureBlack: Boolean,
) {
    val (enableHapticFeedback) = rememberPreference(EnableHapticFeedbackKey, true)
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val clipboardManager = LocalClipboard.current
    val menuState = LocalMenuState.current
    val bottomSheetPageState = LocalBottomSheetPageState.current

    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val repeatMode by playerConnection.repeatMode.collectAsStateWithLifecycle()

    val currentWindowIndex by playerConnection.currentWindowIndex.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val currentSong by playerConnection.currentSong.collectAsStateWithLifecycle(initialValue = null)
    val currentSongLiked = currentSong?.song?.liked == true

    val currentFormat by playerConnection.currentFormat.collectAsStateWithLifecycle(initialValue = null)
    val queueTitle by playerConnection.queueTitle.collectAsStateWithLifecycle()

    val selectedSongs = remember { mutableStateListOf<MediaMetadata>() }
    val selectedItems = remember { mutableStateListOf<Timeline.Window>() }
    var selection by remember { mutableStateOf(false) }

    fun clearSelection() {
        selection = false
        selectedSongs.clear()
        selectedItems.clear()
    }

    if (selection) {
        BackHandler {
            clearSelection()
        }
    }

    var locked by rememberPreference(QueueEditLockKey, defaultValue = true)
    var infiniteQueueEnabled by rememberPreference(AutoLoadMoreKey, defaultValue = true)
    val infiniteQueueLoading by playerConnection.service.infiniteQueueLoading.collectAsStateWithLifecycle()
    val togetherSessionState by playerConnection.service.togetherSessionState.collectAsStateWithLifecycle()
    val togetherForcesLock =
        togetherSessionState is moe.rukamori.archivetune.together.TogetherSessionState.Joined &&
            (togetherSessionState as moe.rukamori.archivetune.together.TogetherSessionState.Joined).role is moe.rukamori.archivetune.together.TogetherRole.Guest
    val effectiveLocked = locked || togetherForcesLock

    val playerDesignStyle by rememberEnumPreference(
        key = PlayerDesignStyleKey,
        defaultValue = PlayerDesignStyle.V4,
    )

    val snackbarHostState = remember { SnackbarHostState() }
    var dismissJob: Job? by remember { mutableStateOf(null) }
    val coroutineScope = rememberCoroutineScope()
    val database = LocalDatabase.current
    var showChoosePlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var showCreateQueuePlaylistDialog by rememberSaveable { mutableStateOf(false) }

    AddToPlaylistDialog(
        isVisible = showChoosePlaylistDialog,
        onGetSong = {
            selectedSongs.map {
                database.withTransaction {
                    insert(it)
                }
                it.id
            }
        },
        onDismiss = { showChoosePlaylistDialog = false },
        onAddComplete = { songCount, playlistNames ->
            val message =
                when {
                    songCount == 1 && playlistNames.size == 1 -> {
                        context.getString(R.string.added_to_playlist, playlistNames.first())
                    }

                    songCount > 1 && playlistNames.size == 1 -> {
                        context.getString(
                            R.string.added_n_songs_to_playlist,
                            songCount,
                            playlistNames.first(),
                        )
                    }

                    songCount == 1 -> {
                        context.getString(R.string.added_to_n_playlists, playlistNames.size)
                    }

                    else -> {
                        context.getString(R.string.added_n_songs_to_n_playlists, songCount, playlistNames.size)
                    }
                }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            clearSelection()
        },
    )

    if (showCreateQueuePlaylistDialog) {
        TextFieldDialog(
            icon = {
                Icon(
                    painter = painterResource(R.drawable.player_queue_music),
                    contentDescription = null,
                )
            },
            title = { Text(text = stringResource(R.string.create_playlist)) },
            placeholder = { Text(text = stringResource(R.string.playlist_name)) },
            initialTextFieldValue = TextFieldValue(queueTitle ?: context.getString(R.string.queue)),
            isInputValid = { it.trim().isNotEmpty() && selectedSongs.isNotEmpty() },
            onDismiss = { showCreateQueuePlaylistDialog = false },
            onDone = onDone@{ rawPlaylistName ->
                val playlistName = rawPlaylistName.trim()
                val songs = selectedSongs.toList()
                if (playlistName.isEmpty() || songs.isEmpty()) return@onDone

                coroutineScope.launch(Dispatchers.IO) {
                    val playlist =
                        PlaylistEntity(
                            name = playlistName,
                            bookmarkedAt = LocalDateTime.now(),
                            isEditable = true,
                        )

                    database.withTransaction {
                        insert(playlist)
                        songs.forEachIndexed { index, song ->
                            insert(song)
                            insert(
                                PlaylistSongMap(
                                    playlistId = playlist.id,
                                    songId = song.id,
                                    position = index,
                                    setVideoId = song.setVideoId,
                                ),
                            )
                        }
                    }

                    withContext(Dispatchers.Main) {
                        val message =
                            if (songs.size == 1) {
                                context.getString(R.string.added_to_playlist, playlistName)
                            } else {
                                context.getString(R.string.added_n_songs_to_playlist, songs.size, playlistName)
                            }
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        clearSelection()
                    }
                }
            },
        )
    }

    val queueWindows by playerConnection.queueWindows.collectAsStateWithLifecycle()
    val currentWindow =
        remember(currentWindowIndex, queueWindows) {
            queueWindows.getOrNull(currentWindowIndex)
        }

    val onRemoveWithUndo: (Timeline.Window) -> Unit = { window ->
        val index = window.firstPeriodIndex
        playerConnection.player.removeMediaItem(index)
        dismissJob?.cancel()
        dismissJob =
            coroutineScope.launch {
                val snackbarResult =
                    snackbarHostState.showSnackbar(
                        message =
                            context.getString(
                                R.string.removed_song_from_queue,
                                window.mediaItem.metadata?.title,
                            ),
                        actionLabel = context.getString(R.string.undo),
                        duration = SnackbarDuration.Short,
                    )
                if (snackbarResult == SnackbarResult.ActionPerformed) {
                    playerConnection.player.addMediaItem(window.mediaItem)
                    playerConnection.player.moveMediaItem(
                        playerConnection.player.mediaItemCount - 1,
                        index,
                    )
                }
            }
    }

    val onRemoveMultipleWithUndo: (List<Timeline.Window>) -> Unit = { windows ->
        if (windows.isNotEmpty()) {
            val sortedWindows = windows.sortedBy { it.firstPeriodIndex }
            var i = 0
            sortedWindows.forEach { window ->
                playerConnection.player.removeMediaItem(window.firstPeriodIndex - i++)
            }
            dismissJob?.cancel()
            dismissJob =
                coroutineScope.launch {
                    val snackbarResult =
                        snackbarHostState.showSnackbar(
                            message =
                                if (windows.size == 1) {
                                    context.getString(
                                        R.string.removed_song_from_queue,
                                        windows
                                            .first()
                                            .mediaItem.metadata
                                            ?.title,
                                    )
                                } else {
                                    context.getString(R.string.removed_n_songs_from_queue, windows.size)
                                },
                            actionLabel = context.getString(R.string.undo),
                            duration = SnackbarDuration.Short,
                        )
                    if (snackbarResult == SnackbarResult.ActionPerformed) {
                        sortedWindows.forEach { window ->
                            playerConnection.player.addMediaItem(window.mediaItem)
                            playerConnection.player.moveMediaItem(
                                playerConnection.player.mediaItemCount - 1,
                                window.firstPeriodIndex,
                            )
                        }
                    }
                }
        }
    }

    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var sleepTimerValue by remember { mutableStateOf(30f) }
    val sleepTimerEnabled =
        remember(
            playerConnection.service.sleepTimer.triggerTime,
            playerConnection.service.sleepTimer.pauseWhenSongEnd,
        ) {
            playerConnection.service.sleepTimer.isActive
        }
    var sleepTimerTimeLeft by remember { mutableLongStateOf(0L) }

    val (showCodecOnPlayer) =
        rememberPreference(
            key = booleanPreferencesKey("show_codec_on_player"),
            defaultValue = false,
        )

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
    var scrollToCurrentRequested by remember { mutableStateOf(true) }
    val openQueue =
        remember(playerBottomSheetState, state) {
            {
                scrollToCurrentRequested = true
                if (!playerBottomSheetState.isExpandedOrExpanding) {
                    playerBottomSheetState.expandSoft()
                }
                state.expandSoft()
            }
        }

    BottomSheet(
        state = state,
        // Pass the actual background color (surfaceContainer / Black) instead of
        // Color.Unspecified, AND set opaqueBackground = true so the outer sheet
        // background is fully opaque as soon as the sheet starts sliding up.
        //
        // Why: non-Apple-Music player styles render a zoomed/gradient/blur
        // artwork backdrop behind the player (PlayerBackground composable).
        // The previous Color.Unspecified made the outer sheet transparent, and
        // the inner content's graphicsLayer alpha fade (which only reaches 1.0
        // at progress = 0.5) let that artwork bleed through during the slide-up
        // drag. With opaqueBackground = true, the outer background is opaque
        // from the very first pixel of drag, fully covering the player artwork,
        // while the inner queue rows still fade in smoothly via their own
        // graphicsLayer alpha. Apple-Music style was unaffected because it
        // doesn't render PlayerBackground — its player backdrop is already a
        // solid surface color.
        backgroundColor = backgroundColor,
        opaqueBackground = true,
        modifier = modifier,
        morphMode = true,
        // Keep the queue list composed while the sheet is collapsed at the peek
        // height — otherwise the `!state.isCollapsed` gate unmounts the whole
        // LazyColumn (and its scroll/selection state) the moment a drag reaches
        // the peek, which combined with the old no-slide morph made the queue
        // vanish while dragging it down.
        //
        // Apple Music is excluded: it renders its queue via the in-place
        // SharedTransitionLayout morph in AppleMusicPlayer (peek height is 0dp,
        // so this sheet never shows). Keeping it alive there would compose a
        // second, invisible reorderable queue list behind the morph.
        keepContentAlive = playerDesignStyle != PlayerDesignStyle.APPLE_MUSIC,
        onCollapsedContentClick = openQueue,
        collapsedContent = {
            when (playerDesignStyle) {
                PlayerDesignStyle.V2 -> {
                    QueueCollapsedContentV2(
                        showCodecOnPlayer = showCodecOnPlayer,
                        currentFormat = currentFormat,
                        textBackgroundColor = TextBackgroundColor,
                        textButtonColor = textButtonColor,
                        iconButtonColor = iconButtonColor,
                        sleepTimerEnabled = sleepTimerEnabled,
                        sleepTimerTimeLeft = sleepTimerTimeLeft,
                        repeatMode = repeatMode,
                        mediaMetadata = mediaMetadata,
                        onExpandQueue = openQueue,
                        onSleepTimerClick = {
                            if (sleepTimerEnabled) {
                                playerConnection.service.sleepTimer.clear()
                            } else {
                                showSleepTimerDialog = true
                            }
                        },
                        onShowLyrics = onShowLyrics,
                        onRepeatModeClick = { playerConnection.player.toggleRepeatMode() },
                        onMenuClick = {
                            menuState.show {
                                PlayerMenu(
                                    mediaMetadata = mediaMetadata,
                                    navController = navController,
                                    playerBottomSheetState = playerBottomSheetState,
                                    onShowDetailsDialog = {
                                        mediaMetadata?.id?.let {
                                            bottomSheetPageState.show {
                                                ShowMediaInfo(it)
                                            }
                                        }
                                    },
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                    )
                }

                PlayerDesignStyle.V3 -> {
                    QueueCollapsedContentV3(
                        showCodecOnPlayer = showCodecOnPlayer,
                        currentFormat = currentFormat,
                        textBackgroundColor = TextBackgroundColor,
                        sleepTimerEnabled = sleepTimerEnabled,
                        sleepTimerTimeLeft = sleepTimerTimeLeft,
                        onExpandQueue = openQueue,
                        onSleepTimerClick = {
                            if (sleepTimerEnabled) {
                                playerConnection.service.sleepTimer.clear()
                            } else {
                                showSleepTimerDialog = true
                            }
                        },
                        onShowLyrics = onShowLyrics,
                        onMenuClick = {
                            menuState.show {
                                PlayerMenu(
                                    mediaMetadata = mediaMetadata,
                                    navController = navController,
                                    playerBottomSheetState = playerBottomSheetState,
                                    onShowDetailsDialog = {
                                        mediaMetadata?.id?.let {
                                            bottomSheetPageState.show {
                                                ShowMediaInfo(it)
                                            }
                                        }
                                    },
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                    )
                }

                PlayerDesignStyle.V5 -> {
                    // V5 keeps its collapsed peek bar empty, matching the APPLE_MUSIC approach.
                    // The LittlePlayer (rendered inside Player.kt) already exposes queue, like,
                    // and more-menu buttons with proper 48dp touch targets. Previously this
                    // branch rendered QueueCollapsedContentV3 inside a 0dp-tall peek Box —
                    // the button row overflowed the parent and its touch zone was clipped/
                    // competed-for by the BottomSheet wrapper's own clickable, which caused
                    // "queue button doesn't work at all" reports on V5. Sleep timer, lyrics,
                    // and other controls remain reachable via the LittlePlayer's more-menu.
                }

                PlayerDesignStyle.V4 -> {
                    QueueCollapsedContentV4(
                        showCodecOnPlayer = showCodecOnPlayer,
                        currentFormat = currentFormat,
                        textBackgroundColor = TextBackgroundColor,
                        textButtonColor = textButtonColor,
                        iconButtonColor = iconButtonColor,
                        sleepTimerEnabled = sleepTimerEnabled,
                        sleepTimerTimeLeft = sleepTimerTimeLeft,
                        mediaMetadata = mediaMetadata,
                        onExpandQueue = openQueue,
                        onSleepTimerClick = {
                            if (sleepTimerEnabled) {
                                playerConnection.service.sleepTimer.clear()
                            } else {
                                showSleepTimerDialog = true
                            }
                        },
                        onShowLyrics = onShowLyrics,
                    )
                }

                PlayerDesignStyle.V1 -> {
                    QueueCollapsedContentV1(
                        showCodecOnPlayer = showCodecOnPlayer,
                        currentFormat = currentFormat,
                        textBackgroundColor = TextBackgroundColor,
                        sleepTimerEnabled = sleepTimerEnabled,
                        sleepTimerTimeLeft = sleepTimerTimeLeft,
                        onExpandQueue = openQueue,
                        onSleepTimerClick = {
                            if (sleepTimerEnabled) {
                                playerConnection.service.sleepTimer.clear()
                            } else {
                                showSleepTimerDialog = true
                            }
                        },
                        onShowLyrics = onShowLyrics,
                    )
                }

                PlayerDesignStyle.V6 -> {
                    QueueCollapsedContentV4(
                        showCodecOnPlayer = showCodecOnPlayer,
                        currentFormat = currentFormat,
                        textBackgroundColor = TextBackgroundColor,
                        textButtonColor = textButtonColor,
                        iconButtonColor = iconButtonColor,
                        sleepTimerEnabled = sleepTimerEnabled,
                        sleepTimerTimeLeft = sleepTimerTimeLeft,
                        mediaMetadata = mediaMetadata,
                        onExpandQueue = openQueue,
                        onSleepTimerClick = {
                            if (sleepTimerEnabled) {
                                playerConnection.service.sleepTimer.clear()
                            } else {
                                showSleepTimerDialog = true
                            }
                        },
                        onShowLyrics = onShowLyrics,
                    )
                }

                PlayerDesignStyle.APPLE_MUSIC -> {
                    // The Apple Music style keeps its collapsed peek bar empty: the queue, lyrics and
                    // output controls all live in the player's own bottom row, so no extra pills here.
                }

                PlayerDesignStyle.V9 -> {
                    val shuffleModeEnabled by playerConnection.shuffleModeEnabled.collectAsStateWithLifecycle()
                    QueueCollapsedContentV9(
                        showCodecOnPlayer = showCodecOnPlayer,
                        currentFormat = currentFormat,
                        textBackgroundColor = TextBackgroundColor,
                        sleepTimerEnabled = sleepTimerEnabled,
                        sleepTimerTimeLeft = sleepTimerTimeLeft,
                        shuffleModeEnabled = shuffleModeEnabled,
                        repeatMode = repeatMode,
                        onShuffleClick = {
                            // Auto-disable repeat when turning shuffle on (mutually exclusive UX).
                            if (!shuffleModeEnabled) {
                                playerConnection.player.repeatMode = Player.REPEAT_MODE_OFF
                            }
                            playerConnection.player.shuffleModeEnabled = !shuffleModeEnabled
                        },
                        onRepeatModeClick = { playerConnection.player.toggleRepeatMode() },
                        onMenuClick = {
                            menuState.show {
                                PlayerMenu(
                                    mediaMetadata = mediaMetadata,
                                    navController = navController,
                                    playerBottomSheetState = playerBottomSheetState,
                                    onShowDetailsDialog = {
                                        mediaMetadata?.id?.let {
                                            bottomSheetPageState.show {
                                                ShowMediaInfo(it)
                                            }
                                        }
                                    },
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                        onSleepTimerClick = {
                            if (sleepTimerEnabled) {
                                playerConnection.service.sleepTimer.clear()
                            } else {
                                showSleepTimerDialog = true
                            }
                        },
                    )
                }

                PlayerDesignStyle.V7, PlayerDesignStyle.V8 -> {
                    val audioDevice by playerConnection.service.activeAudioDevice.collectAsStateWithLifecycle()

                    val view = LocalView.current
                    DisposableEffect(view) {
                        val listener =
                            ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
                                if (hasFocus) playerConnection.service.refreshActiveDevice()
                            }
                        view.viewTreeObserver.addOnWindowFocusChangeListener(listener)
                        onDispose { view.viewTreeObserver.removeOnWindowFocusChangeListener(listener) }
                    }

                    QueueCollapsedContentV7(
                        showCodecOnPlayer = showCodecOnPlayer,
                        currentFormat = currentFormat,
                        textBackgroundColor = TextBackgroundColor,
                        sleepTimerEnabled = sleepTimerEnabled,
                        sleepTimerTimeLeft = sleepTimerTimeLeft,
                        onExpandQueue = openQueue,
                        onShowLyrics = onShowLyrics,
                        onSleepTimerClick = {
                            if (sleepTimerEnabled) {
                                playerConnection.service.sleepTimer.clear()
                            } else {
                                showSleepTimerDialog = true
                            }
                        },
                        onDeviceClick = {
                            SystemMediaControlResolver.openMediaOutputSwitcher(context)
                        },
                        device = audioDevice,
                    )
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
                    initialValue = sleepTimerValue,
                )
            }
        },
    ) {
        val queueWindows by playerConnection.queueWindows.collectAsStateWithLifecycle()
        val currentPlayingUid =
            remember(currentWindowIndex, queueWindows) {
                if (currentWindowIndex in queueWindows.indices) {
                    queueWindows[currentWindowIndex].uid
                } else {
                    null
                }
            }
        // Display list: current song at index 0, upcoming next, then previously-played
        // songs in reverse chronological order. See the sync LaunchedEffect below for
        // the full rationale. The initial value is pre-reordered (Apple Music) or
        // in timeline order (non-Apple Music) so the first frame doesn't flicker.
        val mutableQueueWindows =
            remember {
                mutableStateListOf<Timeline.Window>().apply {
                    if (playerDesignStyle == PlayerDesignStyle.APPLE_MUSIC) {
                        addAll(reorderedForDisplay(queueWindows, currentPlayingUid))
                    } else {
                        addAll(queueWindows)
                    }
                }
            }
        val queueLength by remember {
            derivedStateOf {
                queueWindows.sumOf { it.mediaItem.metadata?.duration ?: 0 }
            }
        }

        val headerItems = 1
        val lazyListState = rememberLazyListState()
        var dragInfo by remember { mutableStateOf<QueueDragInfo?>(null) }
        // UIDs of items that we just committed via moveMediaItem. Used to skip the
        // mutableQueueWindows reset for ONE LaunchedEffect cycle so the player's
        // timeline update has time to propagate. Without this, the else-branch
        // resets mutableQueueWindows to the OLD queueWindows (before the move),
        // causing the dragged item to "snap back" to its original position.
        var justCommittedDragUid by remember { mutableStateOf<Any?>(null) }

        val nextPlayingUid =
            remember(currentWindowIndex, queueWindows) {
                queueWindows.getOrNull(currentWindowIndex + 1)?.uid
            }
        val nextPlayingUid =
            remember(currentWindowIndex, queueWindows) {
                queueWindows.getOrNull(currentWindowIndex + 1)?.uid
            }

        val reorderableState =
            rememberReorderableLazyListState(
                lazyListState = lazyListState,
                scrollThresholdPadding =
                    WindowInsets.systemBars
                        .only(WindowInsetsSides.Bottom)
                        .add(
                            WindowInsets(
                                bottom = ListItemHeight,
                            ),
                        ).asPaddingValues(),
            ) onMove@{ from, to ->
                val fromQueueIndex = from.index - headerItems
                val toQueueIndex = to.index - headerItems
                if (
                    fromQueueIndex !in mutableQueueWindows.indices ||
                    toQueueIndex !in mutableQueueWindows.indices
                ) {
                    return@onMove
                }

                val draggedItemUid = dragInfo?.draggedItemUid ?: mutableQueueWindows[fromQueueIndex].uid
                val actualFromQueueIndex = mutableQueueWindows.indexOfFirst { it.uid == draggedItemUid }
                if (actualFromQueueIndex == -1) return@onMove

                mutableQueueWindows.move(actualFromQueueIndex, toQueueIndex)
                dragInfo =
                    QueueDragInfo(
                        draggedItemUid = draggedItemUid,
                        destination =
                            if (toQueueIndex == 0) {
                                if (playerDesignStyle == PlayerDesignStyle.APPLE_MUSIC) {
                                    // In the filtered Apple Music queue list the current song
                                    // is always at index 0, so dropping at position 0 means
                                    // "make this the next song after the current one" rather
                                    // than "move to the very start of the full timeline".
                                    currentPlayingUid?.let { QueueDragDestination.After(itemUid = it) }
                                        ?: QueueDragDestination.Start
                                } else {
                                    // Non-Apple Music styles: match upstream behavior exactly —
                                    // dropping at index 0 moves the song to the very start of
                                    // the full timeline.
                                    QueueDragDestination.Start
                                }
                            } else {
                                QueueDragDestination.After(
                                    itemUid = mutableQueueWindows[toQueueIndex - 1].uid,
                                )
                            },
                    )

                if (selection) {
                    val currentItem = queueWindows.getOrNull(currentWindowIndex)

                    if (currentItem?.uid == draggedItemUid) {
                        val newIndex = mutableQueueWindows.indexOfFirst { it.uid == draggedItemUid }
                        if (newIndex != -1) {
                            selectedSongs.clear()
                            selectedItems.clear()
                            mutableQueueWindows.getOrNull(newIndex)?.let { window ->
                                window.mediaItem.metadata?.let { metadata ->
                                    selectedSongs.add(metadata)
                                    selectedItems.add(window)
                                }
                            }
                        }
                    }
                }
            }

        LaunchedEffect(queueWindows, currentWindowIndex, reorderableState.isAnyItemDragging) {
            if (reorderableState.isAnyItemDragging) return@LaunchedEffect

            val completedDrag = dragInfo
            if (completedDrag != null) {
                val sourceIndex = queueWindows.indexOfFirst { it.uid == completedDrag.draggedItemUid }
                val destinationIndex = completedDrag.destination.resolveIndex(queueWindows, sourceIndex)
                dragInfo = null

                if (
                    sourceIndex != -1 &&
                    destinationIndex != null &&
                    sourceIndex != destinationIndex
                ) {
                    // Mark this drag as just-committed so the next LaunchedEffect
                    // invocation (triggered by the player's onTimelineChanged)
                    // doesn't reset mutableQueueWindows to the pre-move order.
                    justCommittedDragUid = completedDrag.draggedItemUid
                    if (!playerConnection.player.shuffleModeEnabled) {
                        playerConnection.player.moveMediaItem(sourceIndex, destinationIndex)
                    } else {
                        playerConnection.localPlayer.setShuffleOrder(
                            DefaultShuffleOrder(
                                queueWindows
                                    .map { it.firstPeriodIndex }
                                    .toMutableList()
                                    .move(sourceIndex, destinationIndex)
                                    .toIntArray(),
                                System.currentTimeMillis(),
                            ),
                        )
                    }
                    return@LaunchedEffect
                }
            }

            // If we just committed a drag, skip the reset for one cycle to let
            // the player's timeline update propagate. The next LaunchedEffect
            // invocation (with the updated queueWindows) will clear the flag
            // and apply the reset using the post-move order.
            if (justCommittedDragUid != null) {
                justCommittedDragUid = null
                // Keep the local drag move visible until the player's queue
                // update arrives. The next LaunchedEffect (triggered by the
                // queueWindows change from onTimelineChanged) will do the reset.
                return@LaunchedEffect
            }

            // Apple Music style: display the current song and upcoming songs
            // first, then previously-played songs in reverse chronological
            // order (SimpMusic / Spotify style "Continue Playing" header).
            // All other player styles: show the full queue timeline in
            // timeline order (matches upstream rukamori/ArchiveTune exactly).
            // The full `queueWindows` (including played songs) is still used
            // for queue stats, clear-queue, and drag-source resolution.
            val displayList =
                if (playerDesignStyle == PlayerDesignStyle.APPLE_MUSIC) {
                    reorderedForDisplay(queueWindows, currentPlayingUid)
                } else {
                    queueWindows
                }
            Snapshot.withMutableSnapshot {
                mutableQueueWindows.clear()
                mutableQueueWindows.addAll(displayList)
            }
        }

        // Tracks the previous collapsed state so we can detect the exact
        // moment the queue sheet transitions from collapsed → expanded
        // (whether via the queue button or a swipe-up gesture) and scroll
        // to the currently playing song. Without this, the queue opens
        // scrolled to the top, forcing the user to manually find what's
        // playing. We avoid re-scrolling on every `currentPlayingUid`
        // change so the user is free to browse the queue after opening it
        // without being yanked back to the current song mid-scroll.
        var prevIsCollapsed by remember { mutableStateOf(state.isCollapsed) }

        LaunchedEffect(
            state.isCollapsed,
            scrollToCurrentRequested,
            currentPlayingUid,
            reorderableState.isAnyItemDragging,
        ) {
            val justOpened = prevIsCollapsed && !state.isCollapsed
            prevIsCollapsed = state.isCollapsed
            val shouldScroll =
                !state.isCollapsed &&
                    (justOpened || scrollToCurrentRequested) &&
                    currentPlayingUid != null &&
                    !reorderableState.isAnyItemDragging
            if (shouldScroll) {
                // Wait briefly for the queue windows to populate after the
                // sheet expands. The first composition after expand often has
                // an empty `mutableQueueWindows` (the Snapshot.withMutableSnapshot
                // that copies `queueWindows` into `mutableQueueWindows` runs
                // in a separate LaunchedEffect that hasn't fired yet). A short
                // retry loop lets the index lookup succeed.
                var attempts = 0
                while (attempts < 8) {
                    val indexInMutableList =
                        mutableQueueWindows.indexOfFirst { it.uid == currentPlayingUid }
                    if (indexInMutableList != -1) {
                        lazyListState.scrollToItem(
                            (indexInMutableList + headerItems).coerceAtLeast(0),
                        )
                        scrollToCurrentRequested = false
                        break
                    }
                    kotlinx.coroutines.delay(50L)
                    attempts++
                }
            }
        }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(backgroundColor),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                CompactQueueHeader(
                    sheetState = state,
                    songCount = queueWindows.size,
                    queueDuration = queueLength,
                    locked = effectiveLocked,
                    infiniteQueueEnabled = infiniteQueueEnabled,
                    infiniteQueueLoading = infiniteQueueLoading,
                    onBackgroundColor = onBackgroundColor,
                    onClearQueueClick = {
                        val windowsToRemove =
                            if (currentWindowIndex in queueWindows.indices) {
                                queueWindows.filterIndexed { index, _ -> index != currentWindowIndex }
                            } else {
                                emptyList()
                            }

                        if (windowsToRemove.isNotEmpty()) {
                            onRemoveMultipleWithUndo(windowsToRemove)
                            selection = false
                            selectedSongs.clear()
                            selectedItems.clear()
                        }

                        if (infiniteQueueEnabled) {
                            // Clear the current auto-generated items without changing the user's
                            // persisted global Infinite Queue choice. The next queue will respect
                            // the same saved setting.
                            playerConnection.service.onInfiniteQueueDisabled()
                        }
                    },
                    // NOTE: shuffle/repeat moved out of the queue header — the
                    // player's own bottom row already exposes them. The
                    // "auto-disable repeat when shuffle on" UX from the old
                    // header is preserved in the player's shuffle button
                    // (see BottomSheetPlayer shuffle handler).
                    onLockClick = {
                        if (togetherForcesLock) {
                            Toast.makeText(context, R.string.not_allowed, Toast.LENGTH_SHORT).show()
                        } else {
                            locked = !locked
                        }
                    },
                    onInfiniteQueueClick = {
                        val nextInfiniteQueueEnabled = !infiniteQueueEnabled
                        infiniteQueueEnabled = nextInfiniteQueueEnabled
                        if (nextInfiniteQueueEnabled) {
                            playerConnection.service.onInfiniteQueueEnabled()
                        } else {
                            playerConnection.service.onInfiniteQueueDisabled()
                        }
                    },
                )

                LazyColumn(
                    state = lazyListState,
                    contentPadding =
                        WindowInsets.systemBars
                            .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                            .add(
                                WindowInsets(
                                    bottom = ListItemHeight + if (selection) 88.dp else 8.dp,
                                ),
                            ).asPaddingValues(),
                    modifier =
                        Modifier
                            .weight(1f)
                            .nestedScroll(state.preUpPostDownNestedScrollConnection),
                ) {
                    item(
                        key = "queue_selection_spacer",
                        contentType = "queue_selection_spacer",
                    ) {
                        Spacer(
                            modifier =
                                Modifier
                                    .animateContentSize()
                                    .height(if (selection) 48.dp else 0.dp),
                        )
                    }

                    itemsIndexed(
                        items = mutableQueueWindows,
                        key = { _, item -> item.queueItemKey },
                        contentType = { _, _ -> "queue_item" },
                    ) { index, window ->
                        ReorderableItem(
                            state = reorderableState,
                            key = window.queueItemKey,
                        ) {
                            val currentItem by rememberUpdatedState(window)
                            val isActive = window.uid == currentPlayingUid
                            val dismissBoxState =
                                rememberSwipeToDismissBoxState(
                                    positionalThreshold = { totalDistance -> totalDistance },
                                )

                            var processedDismiss by remember { mutableStateOf(false) }
                            LaunchedEffect(dismissBoxState.currentValue) {
                                val dv = dismissBoxState.currentValue
                                if (!processedDismiss && (
                                        dv == SwipeToDismissBoxValue.StartToEnd ||
                                            dv == SwipeToDismissBoxValue.EndToStart
                                    )
                                ) {
                                    processedDismiss = true
                                    onRemoveWithUndo(currentItem)
                                }
                                if (dv == SwipeToDismissBoxValue.Settled) {
                                    processedDismiss = false
                                }
                            }

                            val content: @Composable () -> Unit = content@{
                                val shouldLoadImages by remember {
                                    derivedStateOf {
                                        state.value > state.collapsedBound + 80.dp
                                    }
                                }

                                val trackMetadata = window.mediaItem.metadata
                                if (trackMetadata == null) return@content
                                val onPlayNextFromQueue =
                                    remember(
                                        window.uid,
                                        window.firstPeriodIndex,
                                        currentPlayingUid,
                                        nextPlayingUid,
                                    ) {
                                        if (window.uid != currentPlayingUid && window.uid != nextPlayingUid) {
                                            {
                                                playerConnection.moveQueueItemToNext(window.firstPeriodIndex)
                                            }
                                        } else {
                                            null
                                        }
                                    }
                                CompactQueueItem(
                                    mediaMetadata = trackMetadata,
                                    isActive = isActive,
                                    isPlaying = isPlaying && isActive,
                                    isSelected = selection && trackMetadata in selectedSongs,
                                    shouldLoadImage = shouldLoadImages,
                                    onBackgroundColor = onBackgroundColor,
                                    onClick = {
                                        if (selection) {
                                            if (trackMetadata in selectedSongs) {
                                                selectedSongs.remove(trackMetadata)
                                                selectedItems.remove(currentItem)
                                                if (selectedSongs.isEmpty()) {
                                                    selection = false
                                                }
                                            } else {
                                                selectedSongs.add(trackMetadata)
                                                selectedItems.add(currentItem)
                                            }
                                        } else {
                                            if (isActive) {
                                                playerConnection.player.togglePlayPause()
                                            } else {
                                                val joined =
                                                    togetherSessionState as? moe.rukamori.archivetune.together.TogetherSessionState.Joined
                                                val isGuest = joined?.role is moe.rukamori.archivetune.together.TogetherRole.Guest
                                                if (isGuest) {
                                                    if (joined?.roomState?.settings?.allowGuestsToControlPlayback != true) {
                                                        Toast
                                                            .makeText(
                                                                context,
                                                                R.string.not_allowed,
                                                                Toast.LENGTH_SHORT,
                                                            ).show()
                                                        return@CompactQueueItem
                                                    }
                                                    val trackId =
                                                        window.mediaItem.metadata?.id?.trim().orEmpty().ifBlank {
                                                            window.mediaItem.mediaId.trim()
                                                        }
                                                    if (trackId.isBlank()) return@CompactQueueItem
                                                    Toast
                                                        .makeText(
                                                            context,
                                                            R.string.together_requesting_song_change,
                                                            Toast.LENGTH_SHORT,
                                                        ).show()
                                                    playerConnection.service.requestTogetherControl(
                                                        moe.rukamori.archivetune.together.ControlAction.SeekToTrack(
                                                            trackId = trackId,
                                                            positionMs = 0L,
                                                        ),
                                                    )
                                                } else {
                                                    playerConnection.player.seekToDefaultPosition(
                                                        window.firstPeriodIndex,
                                                    )
                                                    playerConnection.player.playWhenReady = true
                                                }
                                            }
                                        }
                                    },
                                    onLongClick = {
                                        if (enableHapticFeedback) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (!selection) {
                                            selection = true
                                        }
                                        selectedSongs.clear()
                                        selectedItems.clear()
                                        selectedSongs.add(trackMetadata)
                                        selectedItems.add(currentItem)
                                    },
                                    onMenuClick = {
                                        menuState.show {
                                            PlayerMenu(
                                                mediaMetadata = trackMetadata,
                                                navController = navController,
                                                playerBottomSheetState = playerBottomSheetState,
                                                isQueueTrigger = true,
                                                onPlayNextFromQueue = onPlayNextFromQueue,
                                                onRemoveFromQueue = {
                                                    onRemoveWithUndo(window)
                                                },
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
                                    dragHandle = {
                                        if (!effectiveLocked) {
                                            IconButton(
                                                onClick = { },
                                                modifier =
                                                    Modifier
                                                        .size(36.dp)
                                                        .draggableHandle(),
                                            ) {
                                                Icon(
                                                    painter = painterResource(R.drawable.player_drag_handle),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp),
                                                    tint = onBackgroundColor.copy(alpha = 0.6f),
                                                )
                                            }
                                        }
                                    },
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .background(Color.Transparent),
                                )
                            }

                            if (effectiveLocked) {
                                content()
                            } else {
                                SwipeToDismissBox(
                                    state = dismissBoxState,
                                    backgroundContent = {},
                                ) {
                                    content()
                                }
                            }
                        }
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier =
                        Modifier
                            .padding(
                                bottom =
                                    (if (selection) ListItemHeight * 2 + 16.dp else ListItemHeight) +
                                        WindowInsets.systemBars
                                            .asPaddingValues()
                                            .calculateBottomPadding(),
                            ).align(Alignment.BottomCenter),
                )

                AnimatedVisibility(
                    visible = selection,
                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(
                                bottom =
                                    ListItemHeight +
                                        WindowInsets.systemBars
                                            .asPaddingValues()
                                            .calculateBottomPadding(),
                            ),
                ) {
                    QueueSelectionFloatingToolbar(
                        allSelected = selectedSongs.size == mutableQueueWindows.size,
                        pureBlack = pureBlack,
                        onClose = ::clearSelection,
                        onToggleSelectAll = {
                            if (selectedSongs.size == mutableQueueWindows.size) {
                                clearSelection()
                            } else {
                                selectedSongs.clear()
                                selectedItems.clear()
                                mutableQueueWindows.forEach { window ->
                                    window.mediaItem.metadata?.let { metadata ->
                                        selectedSongs.add(metadata)
                                        selectedItems.add(window)
                                    }
                                }
                            }
                        },
                        onAddToPlaylist = { showChoosePlaylistDialog = true },
                        onCreatePlaylist = { showCreateQueuePlaylistDialog = true },
                        onDelete = {
                            onRemoveMultipleWithUndo(selectedItems.toList())
                            clearSelection()
                        },
                        modifier =
                            Modifier
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

private val Timeline.Window.queueItemKey: Long
    get() =
        (uid.hashCode().toLong() shl Int.SIZE_BITS) xor
            (mediaItem.mediaId.hashCode().toLong() and UInt.MAX_VALUE.toLong())

/**
 * Reorders [windows] for queue display so that:
 *  1. The currently playing song (identified by [currentPlayingUid]) is at
 *     index 0 (top of the list).
 *  2. Upcoming songs follow in playback order.
 *  3. Previously-played songs are at the end, in reverse chronological order
 *     (most recently played first), so the user can swipe up to scroll through
 *     upcoming and then review what just played.
 *
 * The underlying [windows] list is assumed to be in timeline order
 * ([previous, current, upcoming]) as produced by
 * [moe.rukamori.archivetune.extensions.getQueueWindows]. If [currentPlayingUid]
 * is null or not found, the list is returned unchanged.
 */
private fun reorderedForDisplay(
    windows: List<Timeline.Window>,
    currentPlayingUid: Any?,
): List<Timeline.Window> {
    if (currentPlayingUid == null) return windows
    val currentIdx = windows.indexOfFirst { it.uid == currentPlayingUid }
    if (currentIdx == -1) return windows
    val current = windows[currentIdx]
    val upcoming = windows.drop(currentIdx + 1)
    val previous = windows.take(currentIdx).asReversed()
    return buildList {
        add(current)
        addAll(upcoming)
        addAll(previous)
    }
}

@Immutable
private data class QueueDragInfo(
    public val draggedItemUid: Any,
    public val destination: QueueDragDestination,
)

@Immutable
private sealed interface QueueDragDestination {
    public data object Start : QueueDragDestination

    public data class After(
        public val itemUid: Any,
    ) : QueueDragDestination
}

private fun QueueDragDestination.resolveIndex(
    queueWindows: List<Timeline.Window>,
    sourceIndex: Int,
): Int? =
    when (this) {
        QueueDragDestination.Start -> if (queueWindows.isEmpty()) null else 0
        is QueueDragDestination.After -> {
            val anchorIndex = queueWindows.indexOfFirst { it.uid == itemUid }
            when {
                sourceIndex !in queueWindows.indices -> null
                anchorIndex == -1 -> null
                sourceIndex < anchorIndex -> anchorIndex
                else -> (anchorIndex + 1).coerceAtMost(queueWindows.lastIndex)
            }
        }
    }

@Composable
private fun QueueSelectionFloatingToolbar(
    allSelected: Boolean,
    pureBlack: Boolean,
    onClose: () -> Unit,
    onToggleSelectAll: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onCreatePlaylist: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val toolbarContainerColor = if (pureBlack) Color.Black else colorScheme.surfaceContainerHigh
    // Always use white text/icons on the queue selection toolbar to stay readable on the
    // dark blurred backdrop (matches Apple Music style).
    val toolbarContentColor = Color.White
    val fabContainerColor = if (pureBlack) Color.White.copy(alpha = 0.12f) else colorScheme.surfaceContainerHighest
    val fabContentColor = Color.White

    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier.widthIn(max = 420.dp),
        floatingActionButton = {
            FloatingToolbarDefaults.VibrantFloatingActionButton(
                onClick = onClose,
                containerColor = fabContainerColor,
                contentColor = fabContentColor,
            ) {
                Icon(
                    painter = painterResource(R.drawable.player_close),
                    contentDescription = stringResource(R.string.close),
                    modifier = Modifier.size(22.dp),
                )
            }
        },
        colors =
            FloatingToolbarDefaults.standardFloatingToolbarColors(
                toolbarContainerColor = toolbarContainerColor,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QueueSelectionToolbarAction(
                icon = if (allSelected) R.drawable.player_deselect else R.drawable.player_select_all,
                contentDescription = null,
                tint = toolbarContentColor,
                onClick = onToggleSelectAll,
            )

            QueueSelectionToolbarAction(
                icon = R.drawable.player_playlist_add,
                contentDescription = stringResource(R.string.add_to_playlist),
                tint = colorScheme.primary,
                onClick = onAddToPlaylist,
            )

            QueueSelectionToolbarAction(
                icon = R.drawable.player_queue_music,
                contentDescription = stringResource(R.string.create_playlist),
                tint = colorScheme.primary,
                onClick = onCreatePlaylist,
            )

            QueueSelectionToolbarAction(
                icon = R.drawable.player_delete,
                contentDescription = stringResource(R.string.delete),
                tint = colorScheme.error,
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun QueueSelectionToolbarAction(
    icon: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            modifier = Modifier.size(22.dp),
            tint = tint,
        )
    }
}
