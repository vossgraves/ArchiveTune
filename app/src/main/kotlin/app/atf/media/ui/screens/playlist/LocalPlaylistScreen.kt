/*
 * ArchiveTune (2026)
 * © ArchiveTuneFork contributors — github.com/vossgraves/ArchiveTune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.atf.media.ui.screens.playlist

import android.annotation.SuppressLint
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastSumBy
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.atf.media.LocalDatabase
import app.atf.media.LocalDownloadUtil
import app.atf.media.LocalPlayerAwareWindowInsets
import app.atf.media.LocalPlayerConnection
import app.atf.media.LocalStableSystemBarsTopPadding
import app.atf.media.R
import app.atf.media.constants.LiquidGlassEnabledKey
import app.atf.media.constants.PlaylistEditLockKey
import app.atf.media.constants.PlaylistSongSortType
import app.atf.media.constants.SwipeToSongKey
import app.atf.media.db.entities.PlaylistSong
import app.atf.media.extensions.move
import app.atf.media.extensions.toMediaItem
import app.atf.media.extensions.togglePlayPause
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import app.atf.media.playback.queues.ListQueue
import app.atf.media.playback.queues.LocalMixQueue
import app.atf.media.playback.queues.YouTubeQueue
import app.atf.media.ui.component.AssignTagsDialog
import app.atf.media.ui.component.DefaultDialog
import app.atf.media.ui.component.DraggableScrollbar
import app.atf.media.ui.component.EditPlaylistDialog
import app.atf.media.ui.component.EmptyPlaceholder
import app.atf.media.ui.component.ExpressivePullToRefreshBox
import app.atf.media.ui.component.IconButton
import app.atf.media.ui.component.LiquidGlassActionPill
import app.atf.media.ui.component.LiquidGlassIconButton
import app.atf.media.ui.component.LocalMenuState
import app.atf.media.ui.component.MediaDetailAction
import app.atf.media.ui.component.MediaDetailHero
import app.atf.media.ui.component.layerBackdrop
import app.atf.media.ui.component.rememberBackdrop
import app.atf.media.ui.component.MediaDetailIconAction
import app.atf.media.ui.component.SongListItem
import app.atf.media.ui.component.SortHeader
import app.atf.media.ui.menu.PlaylistMenu
import app.atf.media.ui.menu.SelectionSongMenu
import app.atf.media.ui.menu.SongMenu
import app.atf.media.ui.menu.removeSongFromRemotePlaylist
import app.atf.media.ui.screens.playlist.PlaylistSuggestionsSection
import app.atf.media.ui.screens.TELEGRAM_BOTS_ROUTE
import app.atf.media.ui.utils.HeaderDownloadItem
import app.atf.media.ui.utils.HeaderDownloadProgressIndicator
import app.atf.media.ui.utils.HeaderDownloadState
import app.atf.media.ui.utils.backToMain
import app.atf.media.ui.utils.formatCompactCount
import app.atf.media.ui.utils.headerDownloadState
import app.atf.media.ui.utils.sendAddMissingDownloads
import app.atf.media.ui.utils.sendRemoveDownloads
import app.atf.media.utils.makeTimeString
import app.atf.media.utils.rememberPreference
import app.atf.media.viewmodels.LocalPlaylistViewModel
import app.atf.media.viewmodels.PlaylistCoverEvent
import app.atf.media.viewmodels.PlaylistCoverState
import app.atf.media.ui.player.LocalPlayerLyricsFullScreen
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.time.LocalDateTime

@SuppressLint("RememberReturnType")
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LocalPlaylistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: LocalPlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val songs by viewModel.playlistSongs.collectAsStateWithLifecycle()
    val viewCounts by viewModel.viewCounts.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val coverState by viewModel.coverState.collectAsStateWithLifecycle()
    val mutableSongs = remember { mutableStateListOf<PlaylistSong>() }
    val playlistLength =
        remember(songs) {
            songs.fastSumBy { it.song.song.duration }
        }
    val sortType by viewModel.sortType.collectAsStateWithLifecycle()
    val sortDescending by viewModel.sortDescending.collectAsStateWithLifecycle()
    val onSortTypeChange: (PlaylistSongSortType) -> Unit = { viewModel.updateSortPreference(it, sortDescending) }
    val onSortDescendingChange: (Boolean) -> Unit = { viewModel.updateSortPreference(sortType, it) }
    var locked by rememberPreference(PlaylistEditLockKey, defaultValue = true)
    val swipeToSongEnabled by rememberPreference(SwipeToSongKey, defaultValue = true)
    // Liquid Glass master toggle. When off, the Liquid Glass header pills are
    // not shown and the standard TopAppBar is used instead. The kyant
    // RuntimeShader stack requires Android 12+.
    val liquidGlassEnabled by rememberPreference(LiquidGlassEnabledKey, defaultValue = false)
    val liquidGlassHeaderActive =
        liquidGlassEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    // Suspend the LiquidGlass header + layerBackdrop while the full-screen
    // lyrics overlay is open on top of this screen. The overlay is opaque,
    // so this screen's pixels are never visible — but without this gate the
    // kyant layerBackdrop keeps recording the entire LazyColumn into a
    // GraphicsLayer every frame, and the LiquidGlass header pills keep
    // sampling that backdrop through a RuntimeShader (vibrancy + blur +
    // lens). That per-frame GPU work starves the 60 Hz karaoke lyrics
    // sweep running on top, causing the "enhanced word-synced lyrics lag
    // when launched from a playlist page" bug. HomeScreen doesn't have
    // LiquidGlass, which is why the same lyrics path doesn't lag from home.
    val lyricsFullScreen = LocalPlayerLyricsFullScreen.current
    val layerBackdropActive = liquidGlassHeaderActive && !lyricsFullScreen
    var showAssignTagsDialog by remember { mutableStateOf(false) }

    if (showAssignTagsDialog && playlist != null) {
        AssignTagsDialog(
            playlistId = playlist!!.id,
            onDismiss = { showAssignTagsDialog = false },
        )
    }

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.coverEvents.collect { event ->
            when (event) {
                is PlaylistCoverEvent.ShowMessage -> {
                    snackbarHostState.showSnackbar(context.getString(event.messageRes))
                }
            }
        }
    }

    // Stable top inset: does not collapse to 0 when the status bar is transiently hidden,
    // so the search bar offset and the playlist header always stay anchored below the TopAppBar.
    val systemBarsTopPadding = LocalStableSystemBarsTopPadding.current

    var isSearching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }

    val filteredSongs =
        remember(songs, query) {
            if (query.text.isEmpty()) {
                songs
            } else {
                songs.filter { song ->
                    song.song.song.title
                        .contains(query.text, ignoreCase = true) ||
                        song.song.artists.fastAny { it.name.contains(query.text, ignoreCase = true) }
                }
            }
        }

    val focusRequester = remember { FocusRequester() }

    // Save scroll position when entering search, restore when leaving.
    // The header item collapses to height 0 when isSearching becomes true, which
    // shifts all items below it. By saving firstVisibleItemIndex + scrollOffset
    // BEFORE the collapse and restoring them AFTER the expand, the visible
    // position is preserved across open/close search.
    // (The LaunchedEffect that uses these is declared further below, AFTER
    // lazyListState is created — Kotlin requires vals to be declared before use.)
    var savedScrollIndex by remember { mutableIntStateOf(0) }
    var savedScrollOffset by remember { mutableIntStateOf(0) }
    LaunchedEffect(isSearching) {
        if (isSearching) {
            focusRequester.requestFocus()
        }
    }

    var selection by remember { mutableStateOf(false) }
    var selectedSongMapIds by remember { mutableStateOf(emptySet<Int>()) }
    val visibleSongMapIds =
        remember(filteredSongs) {
            filteredSongs.map { it.map.id }.toSet()
        }
    val selectedPlaylistSongs =
        remember(filteredSongs, selectedSongMapIds) {
            filteredSongs.filter { it.map.id in selectedSongMapIds }
        }

    LaunchedEffect(selection, visibleSongMapIds) {
        if (selection) {
            val visibleSelectedSongMapIds = selectedSongMapIds.intersect(visibleSongMapIds)
            if (visibleSelectedSongMapIds.size != selectedSongMapIds.size) {
                selectedSongMapIds = visibleSelectedSongMapIds
            }
        } else if (selectedSongMapIds.isNotEmpty()) {
            selectedSongMapIds = emptySet()
        }
    }

    if (isSearching) {
        BackHandler {
            isSearching = false
            query = TextFieldValue()
        }
    } else if (selection) {
        BackHandler {
            selection = false
        }
    }

    val downloadUtil = LocalDownloadUtil.current
    var downloads by remember { mutableStateOf<Map<String, Download>>(emptyMap()) }
    var downloadState by remember { mutableStateOf<HeaderDownloadState>(HeaderDownloadState.None) }
    val globalDownloadState = remember(downloads) {
        val activeDownloads = downloads.values.filter {
            it.state == Download.STATE_DOWNLOADING ||
            it.state == Download.STATE_QUEUED ||
            it.state == Download.STATE_RESTARTING ||
            it.state == Download.STATE_STOPPED
        }
        if (activeDownloads.isEmpty()) {
            HeaderDownloadState.None
        } else {
            var progressTotal = 0f
            var hasRunning = false
            var hasPaused = false
            activeDownloads.forEach { download ->
                val progress = download.percentDownloaded.takeIf { it >= 0f }?.div(100f) ?: 0f
                progressTotal += progress.coerceIn(0f, 1f)
                if (download.state == Download.STATE_STOPPED) {
                    hasPaused = hasPaused || download.stopReason == 1
                } else {
                    hasRunning = true
                }
            }
            HeaderDownloadState.Partial(
                progress = progressTotal / activeDownloads.size,
                paused = hasPaused && !hasRunning,
            )
        }
    }

    val editable: Boolean = playlist?.playlist?.isEditable == true
    val isReorderingEnabled =
        editable &&
            sortType == PlaylistSongSortType.CUSTOM &&
            !locked &&
            !selection &&
            !isSearching
    val isSwipeToDeleteEnabled =
        editable &&
            !locked &&
            !selection &&
            !swipeToSongEnabled &&
            !isReorderingEnabled

    LaunchedEffect(songs) {
        mutableSongs.apply {
            clear()
            addAll(songs)
        }
        val songIds = songs.map { it.song.id }
        downloadUtil.downloads.collect { currentDownloads ->
            downloads = currentDownloads
            downloadState = headerDownloadState(songIds, currentDownloads)
        }
    }

    val pickCoverLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(viewModel::updateCover)
        }

    var showEditDialog by remember { mutableStateOf(false) }

    if (showEditDialog) {
        playlist?.let { playlistData ->
            EditPlaylistDialog(
                initialName = playlistData.playlist.name,
                onDismiss = { showEditDialog = false },
                onSave = { name ->
                    database.query {
                        update(
                            playlistData.playlist.copy(
                                name = name,
                                lastUpdateTime = LocalDateTime.now(),
                            ),
                        )
                    }
                    viewModel.viewModelScope.launch(Dispatchers.IO) {
                        playlistData.playlist.browseId?.let { YouTube.renamePlaylist(it, name) }
                    }
                },
            )
        }
    }

    var showRemoveDownloadDialog by remember { mutableStateOf(false) }

    if (showRemoveDownloadDialog) {
        DefaultDialog(
            onDismiss = { showRemoveDownloadDialog = false },
            content = {
                Text(
                    text =
                        stringResource(
                            R.string.remove_download_playlist_confirm,
                            playlist?.playlist!!.name,
                        ),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                TextButton(
                    onClick = { showRemoveDownloadDialog = false },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }

                TextButton(
                    onClick = {
                        showRemoveDownloadDialog = false
                        if (!editable) {
                            database.transaction {
                                playlist?.id?.let { clearPlaylist(it) }
                            }
                        }
                        sendRemoveDownloads(
                            context = context,
                            songIds = songs.map { it.song.id },
                        )
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }

    var showDeletePlaylistDialog by remember { mutableStateOf(false) }
    if (showDeletePlaylistDialog) {
        DefaultDialog(
            onDismiss = { showDeletePlaylistDialog = false },
            content = {
                Text(
                    text =
                        stringResource(
                            R.string.delete_playlist_confirm,
                            playlist?.playlist!!.name,
                        ),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                TextButton(
                    onClick = { showDeletePlaylistDialog = false },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }
                TextButton(
                    onClick = {
                        showDeletePlaylistDialog = false
                        database.query {
                            playlist?.let { delete(it.playlist) }
                        }
                        viewModel.viewModelScope.launch(Dispatchers.IO) {
                            playlist?.playlist?.browseId?.let { YouTube.deletePlaylist(it) }
                        }
                        navController.popBackStack()
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }

    val headerItems by remember {
        derivedStateOf {
            val current = playlist
            val hasContent =
                current != null &&
                    (current.songCount > 0 || current.playlist.remoteSongCount != 0)
            if (hasContent && !isSearching) 2 else 0
        }
    }
    val lazyListState = rememberLazyListState()
    var dragInfo by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val reorderableState =
        rememberReorderableLazyListState(
            lazyListState = lazyListState,
            scrollThresholdPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        ) { from, to ->
            if (to.index >= headerItems && from.index >= headerItems) {
                val currentDragInfo = dragInfo
                dragInfo =
                    if (currentDragInfo == null) {
                        (from.index - headerItems) to (to.index - headerItems)
                    } else {
                        currentDragInfo.first to (to.index - headerItems)
                    }
                mutableSongs.move(from.index - headerItems, to.index - headerItems)
            }
        }

    // Save scroll position when entering search, restore when leaving.
    // The header item collapses to height 0 when isSearching becomes true, which
    // shifts all items below it. By saving firstVisibleItemIndex + scrollOffset
    // BEFORE the collapse and restoring them AFTER the expand, the visible
    // position is preserved across open/close search.
    LaunchedEffect(isSearching) {
        if (isSearching) {
            savedScrollIndex = lazyListState.firstVisibleItemIndex
            savedScrollOffset = lazyListState.firstVisibleItemScrollOffset
        } else {
            // Wait one frame for the header to re-expand before restoring.
            withFrameNanos {}
            lazyListState.scrollToItem(savedScrollIndex, savedScrollOffset)
        }
    }

    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging) {
            dragInfo?.let { (from, to) ->
                val orderedBeforeMove = songs
                val browseId =
                    viewModel.playlist.value
                        ?.playlist
                        ?.browseId
                val movedSetVideoId = orderedBeforeMove.getOrNull(from)?.map?.setVideoId
                val successorIndex = if (from > to) to else to + 1
                val successorSetVideoId = orderedBeforeMove.getOrNull(successorIndex)?.map?.setVideoId

                coroutineScope.launch(Dispatchers.IO) {
                    database.withTransaction {
                        move(viewModel.playlistId, from, to)
                    }

                    if (browseId != null && movedSetVideoId != null) {
                        runCatching {
                            YouTube
                                .moveSongPlaylist(
                                    browseId,
                                    movedSetVideoId,
                                    successorSetVideoId,
                                ).getOrThrow()
                        }.onFailure {
                            withContext(Dispatchers.Main) {
                                snackbarHostState.showSnackbar(
                                    message = context.getString(R.string.error_unknown),
                                    withDismissAction = true,
                                )
                            }
                        }
                    }
                }
                dragInfo = null
            }
        }
    }

    val showTopBarTitle by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex > 0
        }
    }

    val surfaceColor = MaterialTheme.colorScheme.surface

    val transparentAppBar by remember {
        derivedStateOf {
            !selection && !showTopBarTitle && !isSearching
        }
    }

    // Liquid Glass backdrop: created unconditionally (cheap — just a GraphicsLayer
    // handle). The actual content recording only happens when
    // `Modifier.layerBackdrop(artworkBackdrop)` is applied to the LazyColumn below,
    // which is gated on `liquidGlassHeaderActive`.
    val artworkBackdrop = rememberBackdrop(Color.Black)

    ExpressivePullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = viewModel::refresh,
        modifier =
            Modifier
                .fillMaxSize()
                .background(surfaceColor),
    ) {
        LazyColumn(
            state = lazyListState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .then(
                        if (layerBackdropActive) {
                            Modifier.layerBackdrop(artworkBackdrop)
                        } else {
                            Modifier
                        },
                    ),
            contentPadding =
                PaddingValues(
                    // When searching, the header item collapses to zero height and the
                    // TopAppBar (which renders the search field) is overlaid on top of
                    // the LazyColumn. Reserve top space so the first songs aren't hidden
                    // behind the TopAppBar + status bar.
                    top = if (isSearching) systemBarsTopPadding + 64.dp else 0.dp,
                    bottom =
                        LocalPlayerAwareWindowInsets.current
                            .union(WindowInsets.ime)
                            .asPaddingValues()
                            .calculateBottomPadding(),
                ),
        ) {
            playlist?.let { playlist ->
                if (playlist.songCount == 0 && playlist.playlist.remoteSongCount == 0) {
                    item {
                        EmptyPlaceholder(
                            icon = R.drawable.music_note,
                            text = stringResource(R.string.playlist_is_empty),
                        )
                    }
                } else {
                    item(key = "header") {
                        if (!isSearching) {
                            val songCount =
                                if (
                                    playlist.songCount == 0 &&
                                    playlist.playlist.remoteSongCount != null
                                ) {
                                    playlist.playlist.remoteSongCount
                                } else {
                                    playlist.songCount
                                }
                            val metadata =
                                listOfNotNull(
                                    pluralStringResource(
                                        R.plurals.n_song,
                                        songCount,
                                        songCount,
                                    ),
                                    playlistLength
                                        .takeIf { it > 0 }
                                        ?.let { makeTimeString(it * 1000L) },
                                ).joinToString(MediaDetailMetadataSeparator)
                            val isBookmarked = playlist.playlist.bookmarkedAt != null

                            // SimpMusic-style liquid glass backdrop source: the
                            // LazyColumn itself carries the layerBackdrop modifier
                            // (see the LazyColumn definition above), so the entire
                            // scrolling content is recorded into the backdrop. The
                            // floating Liquid Glass back button (top-start) and
                            // search+more pill (top-end) are siblings of the
                            // LazyColumn (children of the ExpressivePullToRefreshBox),
                            // so they sample the backdrop without being recorded into
                            // it. They are PERSISTENT — they stay at the top of the
                            // screen no matter how far the user scrolls.
                            //
                            // The hero item itself just renders the MediaDetailHero;
                            // no inner Box / layerBackdrop wrapper is needed here.
                            MediaDetailHero(
                                title = playlist.playlist.name,
                                thumbnailUrl =
                                    playlist.playlist.thumbnailUrl
                                        ?: playlist.thumbnails.firstOrNull(),
                                fallbackIcon = R.drawable.queue_music,
                                systemBarsTopPadding = systemBarsTopPadding,
                                metadata = metadata,
                                isAdded = isBookmarked,
                                addContentDescription = R.string.add_to_library,
                                removeContentDescription = R.string.remove_from_library,
                                onShuffle =
                                    if (songs.isEmpty()) {
                                        null
                                    } else {
                                        {
                                            playerConnection.playQueue(
                                                ListQueue(
                                                    title = playlist.playlist.name,
                                                    items =
                                                        songs
                                                            .shuffled()
                                                            .map { it.song.toMediaItem() },
                                                ),
                                            )
                                        }
                                    },
                                onPlay =
                                    if (songs.isEmpty()) {
                                        null
                                    } else {
                                        {
                                            playerConnection.playQueue(
                                                ListQueue(
                                                    title = playlist.playlist.name,
                                                    items = songs.map { it.song.toMediaItem() },
                                                ),
                                            )
                                        }
                                    },
                                onToggleAdd = null,
                                additionalPrimaryActions = { contentColor ->
                                    if (songs.isNotEmpty()) {
                                        MediaDetailAction(
                                            contentDescription =
                                                if (downloadState == HeaderDownloadState.Completed) {
                                                    R.string.remove_download
                                                } else {
                                                    R.string.download
                                                },
                                            contentColor = contentColor,
                                            onClick = {
                                                when (downloadState) {
                                                    HeaderDownloadState.Completed -> {
                                                        showRemoveDownloadDialog = true
                                                    }
                                                    is HeaderDownloadState.Partial -> {
                                                        sendRemoveDownloads(
                                                            context = context,
                                                            songIds = songs.map { it.song.id },
                                                        )
                                                    }
                                                    HeaderDownloadState.None -> {
                                                        sendAddMissingDownloads(
                                                            context = context,
                                                            songs =
                                                songs.map {
                                                                    HeaderDownloadItem(
                                                                        id = it.song.id,
                                                                        title = it.song.song.title,
                                                                    )
                                                                },
                                                            downloads = downloads,
                                                        )
                                                    }
                                                }
                                            },
                                        ) {
                                            when (val state = downloadState) {
                                                HeaderDownloadState.Completed -> {
                                                    Icon(
                                                        painter = painterResource(R.drawable.offline),
                                                        contentDescription = null,
                                                        modifier = Modifier.size(22.dp),
                                                    )
                                                }
                                                is HeaderDownloadState.Partial -> {
                                                    HeaderDownloadProgressIndicator(
                                                        progress = state.progress,
                                                        paused = state.paused,
                                                        icon = R.drawable.download,
                                                    )
                                                }
                                                HeaderDownloadState.None -> {
                                                    Icon(
                                                        painter = painterResource(R.drawable.download),
                                                        contentDescription = null,
                                                        modifier = Modifier.size(22.dp),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                },
                                // REMOVED Modifier.animateItem(): the header is a
                                // static first item that never needs placement
                                // animation. animateItem() was causing the header
                                // to briefly shift position ("goes up for a split
                                // second and comes back") when a LiquidGlass header
                                // icon was clicked — the state change triggered a
                                // LazyColumn layout pass, and animateItem()
                                // animated the resulting placement delta.
                                useBlurredPlayButton = liquidGlassHeaderActive,
                            )
                        }
                    }

                    // Sort Header
                    item(key = "sort_header") {
                        if (!isSearching) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 16.dp),
                            ) {
                                SortHeader(
                                    sortType = sortType,
                                    sortDescending = sortDescending,
                                    onSortTypeChange = onSortTypeChange,
                                    onSortDescendingChange = onSortDescendingChange,
                                    sortTypeText = { sortType ->
                                        when (sortType) {
                                            PlaylistSongSortType.CUSTOM -> R.string.sort_by_custom
                                            PlaylistSongSortType.CREATE_DATE -> R.string.sort_by_create_date
                                            PlaylistSongSortType.NAME -> R.string.sort_by_name
                                            PlaylistSongSortType.ARTIST -> R.string.sort_by_artist
                                            PlaylistSongSortType.PLAY_TIME -> R.string.sort_by_play_time
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                if (editable && sortType == PlaylistSongSortType.CUSTOM) {
                                    IconButton(
                                        onClick = { locked = !locked },
                                        onLongClick = {},
                                        modifier = Modifier.padding(horizontal = 6.dp),
                                    ) {
                                        Icon(
                                            painter = painterResource(if (locked) R.drawable.lock else R.drawable.lock_open),
                                            contentDescription = null,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Songs List
            if (!selection) {
                itemsIndexed(
                    items = if (isSearching) filteredSongs else mutableSongs,
                    key = { _, song -> song.map.id },
                ) { index, song ->
                    ReorderableItem(
                        state = reorderableState,
                        key = song.map.id,
                        modifier =
                            Modifier.graphicsLayer {
                                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                            },
                    ) {
                        val currentItem by rememberUpdatedState(song)

                        fun deleteFromPlaylist() {
                            val map = currentItem.map
                            val browseId = playlist?.playlist?.browseId
                            coroutineScope.launch(Dispatchers.IO) {
                                if (browseId != null) {
                                    val remoteResult = removeSongFromRemotePlaylist(browseId, map)
                                    if (remoteResult.isFailure) {
                                        withContext(Dispatchers.Main) {
                                            snackbarHostState.showSnackbar(
                                                message = context.getString(R.string.error_unknown),
                                                withDismissAction = true,
                                            )
                                        }
                                        return@launch
                                    }
                                }
                                database.withTransaction {
                                    move(map.playlistId, map.position, Int.MAX_VALUE)
                                    delete(map.copy(position = Int.MAX_VALUE))
                                }
                            }
                        }

                        val dismissBoxState =
                            rememberSwipeToDismissBoxState(
                                positionalThreshold = { totalDistance -> totalDistance },
                                confirmValueChange = { targetValue ->
                                    targetValue == SwipeToDismissBoxValue.Settled || !lazyListState.isScrollInProgress
                                },
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
                                deleteFromPlaylist()
                            }
                            if (dv == SwipeToDismissBoxValue.Settled) {
                                processedDismiss = false
                            }
                        }

                        val content: @Composable () -> Unit = {
                            SongListItem(
                                song = song.song,
                                viewCountText =
                                    viewCounts[song.song.id]?.let { count -> formatCompactCount(count.toLong()) },
                                isActive = song.song.id == mediaMetadata?.id,
                                isPlaying = isPlaying,
                                showInLibraryIcon = true,
                                trailingContent = {
                                    IconButton(
                                        onClick = {
                                            menuState.show {
                                                SongMenu(
                                                    originalSong = song.song,
                                                    playlistSong = song,
                                                    playlistBrowseId = playlist?.playlist?.browseId,
                                                    navController = navController,
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }
                                        },
                                        onLongClick = {},
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.more_vert),
                                            contentDescription = null,
                                        )
                                    }

                                    if (isReorderingEnabled) {
                                        IconButton(
                                            onClick = { },
                                            onLongClick = {},
                                            modifier =
                                                Modifier
                                                    .draggableHandle()
                                                    .graphicsLayer { alpha = 0.99f },
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.drag_handle),
                                                contentDescription = null,
                                            )
                                        }
                                    }
                                },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = {
                                                if (song.song.id == mediaMetadata?.id) {
                                                    playerConnection.player.togglePlayPause()
                                                } else {
                                                    playerConnection.playQueue(
                                                        ListQueue(
                                                            title = playlist!!.playlist.name,
                                                            items = songs.map { it.song.toMediaItem() },
                                                            startIndex = songs.indexOfFirst { it.map.id == song.map.id },
                                                        ),
                                                    )
                                                }
                                            },
                                            onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                if (!selection) {
                                                    selection = true
                                                }
                                                selectedSongMapIds = setOf(song.map.id)
                                            },
                                        ),
                            )
                        }

                        if (isSwipeToDeleteEnabled) {
                            SwipeToDismissBox(
                                state = dismissBoxState,
                                backgroundContent = {},
                            ) {
                                content()
                            }
                        } else {
                            content()
                        }
                    }
                }
            } else {
                itemsIndexed(
                    items = filteredSongs,
                    key = { _, song -> song.map.id },
                ) { index, song ->
                    ReorderableItem(
                        state = reorderableState,
                        key = song.map.id,
                        modifier =
                            Modifier.graphicsLayer {
                                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                            },
                    ) {
                        val content: @Composable () -> Unit = {
                            SongListItem(
                                song = song.song,
                                viewCountText =
                                    viewCounts[song.song.id]?.let { count -> formatCompactCount(count.toLong()) },
                                isActive = song.song.id == mediaMetadata?.id,
                                isPlaying = isPlaying,
                                showInLibraryIcon = true,
                                trailingContent = {
                                    IconButton(
                                        onClick = {
                                            menuState.show {
                                                SongMenu(
                                                    originalSong = song.song,
                                                    playlistBrowseId = playlist?.playlist?.browseId,
                                                    navController = navController,
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }
                                        },
                                        onLongClick = {},
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.more_vert),
                                            contentDescription = null,
                                        )
                                    }
                                },
                                isSelected = song.map.id in selectedSongMapIds,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = {
                                                if (!selection) {
                                                    if (song.song.id == mediaMetadata?.id) {
                                                        playerConnection.player.togglePlayPause()
                                                    } else {
                                                        playerConnection.playQueue(
                                                            ListQueue(
                                                                title = playlist!!.playlist.name,
                                                                items = songs.map { it.song.toMediaItem() },
                                                                startIndex = index,
                                                            ),
                                                        )
                                                    }
                                                } else {
                                                    selectedSongMapIds =
                                                        if (song.map.id in selectedSongMapIds) {
                                                            selectedSongMapIds - song.map.id
                                                        } else {
                                                            selectedSongMapIds + song.map.id
                                                        }
                                                }
                                            },
                                            onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                if (!selection) {
                                                    selection = true
                                                }
                                                selectedSongMapIds = setOf(song.map.id)
                                            },
                                        ),
                            )
                        }

                        content()
                    }
                }
            }

            // Playlist Suggestions Section ("You might like") — hidden for Telegram-channel
            // playlists (LPtg<chatId>) because the suggestions are YouTube-Music queries built
            // from the playlist name + songs, which is meaningless for a Telegram channel whose
            // songs are bot-fetched files (titles/performers often don't match YT Music). The
            // section would either show irrelevant results or fail to load, polluting the
            // playlist screen. Telegram playlists are managed via "Refresh from Telegram" /
            // "Add songs" (in the overflow menu) instead.
            val isTelegramPlaylist = playlist?.playlist?.id?.startsWith("LPtg") == true
            if (!selection && !isSearching && !isTelegramPlaylist) {
                item {
                    PlaylistSuggestionsSection(
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }
        }

        DraggableScrollbar(
            modifier =
                Modifier
                    .padding(
                        LocalPlayerAwareWindowInsets.current
                            .union(WindowInsets.ime)
                            .asPaddingValues(),
                    ).align(Alignment.CenterEnd),
            scrollState = lazyListState,
            headerItems = headerItems,
        )

        // Persistent Liquid Glass header buttons. Siblings of the LazyColumn
        // (children of the ExpressivePullToRefreshBox), positioned at top-start
        // and top-end. They sample the artworkBackdrop (which captures the
        // entire scrolling content via Modifier.layerBackdrop on the LazyColumn)
        // to render the frosted-glass effect. PERSISTENT — stay at the top no
        // matter how far the user scrolls.
        //
        // Shown only when:
        //  - Liquid Glass master toggle is on (liquidGlassHeaderActive)
        //  - Not in selection mode
        //  - Not searching
        //  - Playlist is loaded
        // Capture playlist in a local val so the compiler can smart-cast it
        // to non-null inside the block (playlist is a delegate, so the
        // compiler can't smart-cast the property directly).
        val currentPlaylist = playlist
        if (layerBackdropActive && !selection && !isSearching && currentPlaylist != null) {
            LiquidGlassIconButton(
                backdrop = artworkBackdrop,
                painter = painterResource(R.drawable.arrow_back),
                contentDescription = null,
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 12.dp, top = systemBarsTopPadding + 12.dp)
                        .size(48.dp),
                onClick = { navController.navigateUp() },
            )
            LiquidGlassActionPill(
                backdrop = artworkBackdrop,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 12.dp, top = systemBarsTopPadding + 12.dp),
            ) {
                // Search
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.IconButton(onClick = { isSearching = true }) {
                        Icon(
                            painter = painterResource(R.drawable.search),
                            contentDescription = null,
                            tint = Color.White,
                        )
                    }
                }
                // More
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.IconButton(onClick = {
                        menuState.show {
                            PlaylistMenu(
                                playlist = currentPlaylist,
                                coroutineScope = coroutineScope,
                                onDismiss = menuState::dismiss,
                                onChangeCover =
                                    if (
                                        currentPlaylist.playlist.isEditable == true &&
                                        coverState !is PlaylistCoverState.Loading
                                    ) {
                                        {
                                            menuState.dismiss()
                                            pickCoverLauncher.launch(arrayOf("image/*"))
                                        }
                                    } else {
                                        null
                                    },
                                onRemoveCover =
                                    if (
                                        currentPlaylist.playlist.hasCustomCover &&
                                        coverState !is PlaylistCoverState.Loading
                                    ) {
                                        {
                                            menuState.dismiss()
                                            viewModel.removeCover()
                                        }
                                    } else {
                                        null
                                    },
                                onAddSongs = {
                                    navController.navigate(TELEGRAM_BOTS_ROUTE)
                                },
                            )
                        }
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.more_horiz),
                            contentDescription = null,
                            tint = Color.White,
                        )
                    }
                }
            }
        }

        // Top App Bar: shown when Liquid Glass is disabled, OR in selection mode,
        // OR when searching. When Liquid Glass is active and not in selection mode
        // and not searching, the persistent Liquid Glass buttons above handle
        // navigation and actions, so the TopAppBar is hidden entirely.
        if (!liquidGlassHeaderActive || selection || isSearching) {
        // Top App Bar
        val topAppBarColors =
            if (transparentAppBar) {
                TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                    navigationIconContentColor = Color.White,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White,
                )
            } else {
                TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = Color.Transparent,
                )
            }

        TopAppBar(
            colors = topAppBarColors,
            windowInsets =
                WindowInsets(top = systemBarsTopPadding)
                    .union(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)),
            title = {
                if (selection) {
                    val count = selectedPlaylistSongs.size
                    Text(
                        text = pluralStringResource(R.plurals.n_song, count, count),
                        style = MaterialTheme.typography.titleLarge,
                    )
                } else if (isSearching) {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.search),
                                style = MaterialTheme.typography.titleLarge,
                            )
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleLarge,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        colors =
                            TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                            ),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                    )
                } else if (showTopBarTitle) {
                    Text(playlist?.playlist?.name.orEmpty())
                }
            },
            navigationIcon = {
                // Show the back/close arrow when:
                //  - Searching
                //  - In selection mode
                //  - Scrolled past the hero (showTopBarTitle)
                //  - Liquid Glass is OFF (the persistent LiquidGlass back button
                //    isn't there, so the TopAppBar must provide back navigation
                //    even when the hero is visible)
                if (isSearching || selection || showTopBarTitle || !liquidGlassHeaderActive) {
                    IconButton(
                        onClick = {
                            if (isSearching) {
                                isSearching = false
                                query = TextFieldValue()
                            } else if (selection) {
                                selection = false
                            } else {
                                navController.navigateUp()
                            }
                        },
                        onLongClick = {
                            if (!isSearching) {
                                navController.backToMain()
                            }
                        },
                    ) {
                        Icon(
                            painter =
                                painterResource(
                                    if (selection || isSearching) R.drawable.close else R.drawable.arrow_back,
                                ),
                            contentDescription = null,
                        )
                    }
                }
            },
            actions = {
                if (selection) {
                    val count = selectedPlaylistSongs.size
                    IconButton(
                        onClick = {
                            if (count == filteredSongs.size) {
                                selectedSongMapIds = emptySet()
                            } else {
                                selectedSongMapIds = visibleSongMapIds
                            }
                        },
                        onLongClick = {},
                    ) {
                        Icon(
                            painter =
                                painterResource(
                                    if (count == filteredSongs.size) R.drawable.deselect else R.drawable.select_all,
                                ),
                            contentDescription = null,
                        )
                    }

                    IconButton(
                        onClick = {
                            menuState.show {
                                SelectionSongMenu(
                                    songSelection =
                                        selectedPlaylistSongs.map { it.song },
                                    songPosition =
                                        selectedPlaylistSongs.map { it.map },
                                    onDismiss = menuState::dismiss,
                                    clearAction = {
                                        selection = false
                                        selectedSongMapIds = emptySet()
                                    },
                                )
                            }
                        },
                        onLongClick = {},
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.more_vert),
                            contentDescription = null,
                        )
                    }
                } else if (!isSearching) {
                    // Show search + more when:
                    //  - Scrolled past the hero (showTopBarTitle)
                    //  - Liquid Glass is OFF (the persistent LiquidGlass pill
                    //    isn't there, so the TopAppBar must provide search+more
                    //    even when the hero is visible)
                    if (showTopBarTitle || !liquidGlassHeaderActive) {
                        IconButton(
                            onClick = { isSearching = true },
                            onLongClick = {},
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.search),
                                contentDescription = null,
                            )
                        }
                        playlist?.let { currentPlaylist ->
                            IconButton(
                                onClick = {
                                    menuState.show {
                                        PlaylistMenu(
                                            playlist = currentPlaylist,
                                            coroutineScope = coroutineScope,
                                            onDismiss = menuState::dismiss,
                                            onChangeCover =
                                                if (
                                                    currentPlaylist.playlist.isEditable == true &&
                                                    coverState !is PlaylistCoverState.Loading
                                                ) {
                                                    {
                                                        menuState.dismiss()
                                                        pickCoverLauncher.launch(arrayOf("image/*"))
                                                    }
                                                } else {
                                                    null
                                                },
                                            onRemoveCover =
                                                if (
                                                    currentPlaylist.playlist.hasCustomCover &&
                                                    coverState !is PlaylistCoverState.Loading
                                                ) {
                                                    {
                                                        menuState.dismiss()
                                                        viewModel.removeCover()
                                                    }
                                                } else {
                                                    null
                                                },
                                            onAddSongs = {
                                                navController.navigate(TELEGRAM_BOTS_ROUTE)
                                            },
                                        )
                                    }
                                },
                                onLongClick = {},
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.more_horiz),
                                    contentDescription = stringResource(R.string.more_options),
                                )
                            }
                        }
                    }
                }
            },
        )
        } // end if (!liquidGlassHeaderActive || selection || isSearching)

        SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier
                    .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime))
                    .align(Alignment.BottomCenter),
        )
    }
}

private const val MediaDetailMetadataSeparator = "  •  "
