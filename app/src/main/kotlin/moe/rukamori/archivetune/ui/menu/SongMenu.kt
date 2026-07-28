/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.menu

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.media3.datasource.cache.CacheSpan
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalDownloadUtil
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.LocalSyncUtils
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.ArtistSeparatorsKey
import moe.rukamori.archivetune.constants.AskDownloadQualityKey
import moe.rukamori.archivetune.constants.ExternalDownloaderEnabledKey
import moe.rukamori.archivetune.constants.ExternalDownloaderPackageKey
import moe.rukamori.archivetune.constants.ListThumbnailSize
import moe.rukamori.archivetune.constants.LosslessDownloadFolderKey
import moe.rukamori.archivetune.constants.LosslessDownloadTagKey
import moe.rukamori.archivetune.constants.QobuzAudioQuality
import moe.rukamori.archivetune.constants.QobuzAudioQualityKey
import moe.rukamori.archivetune.constants.toFormatId
import moe.rukamori.archivetune.constants.SpeedDialSongIdsKey
import moe.rukamori.archivetune.db.entities.ArtistEntity
import moe.rukamori.archivetune.db.entities.Event
import moe.rukamori.archivetune.db.entities.PlaylistSong
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.download.LosslessDownloader
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.ExoDownloadService
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.telegram.isTelegramMediaId
import moe.rukamori.archivetune.ui.component.DownloadQualityChoice
import moe.rukamori.archivetune.ui.component.DownloadQualityDialog
import moe.rukamori.archivetune.ui.component.ExportFormatDialog
import moe.rukamori.archivetune.ui.component.ListDialog
import moe.rukamori.archivetune.ui.component.LocalBottomSheetPageState
import moe.rukamori.archivetune.ui.component.MenuSurfaceSection
import moe.rukamori.archivetune.ui.component.NewAction
import moe.rukamori.archivetune.ui.component.NewActionGrid
import moe.rukamori.archivetune.ui.component.SongListItem
import moe.rukamori.archivetune.ui.component.TextFieldDialog
import moe.rukamori.archivetune.ui.utils.ShowMediaInfo
import moe.rukamori.archivetune.ui.utils.YtimgResizePolicy
import moe.rukamori.archivetune.ui.utils.resize
import moe.rukamori.archivetune.utils.SpeedDialPin
import moe.rukamori.archivetune.utils.SpeedDialPinType
import moe.rukamori.archivetune.utils.parseSpeedDialPins
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.utils.serializeSpeedDialPins
import moe.rukamori.archivetune.utils.shareLocalAudio
import moe.rukamori.archivetune.utils.toggleSpeedDialPin
import moe.rukamori.archivetune.viewmodels.CachePlaylistViewModel

@Composable
fun SongMenu(
    originalSong: Song,
    event: Event? = null,
    navController: NavController,
    playlistSong: PlaylistSong? = null,
    playlistBrowseId: String? = null,
    onDismiss: () -> Unit,
    isFromCache: Boolean = false,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val songState = database.song(originalSong.id).collectAsState(initial = originalSong)
    val song = songState.value ?: originalSong
    val download by LocalDownloadUtil.current
        .getDownload(originalSong.id)
        .collectAsState(initial = null)
    val coroutineScope = rememberCoroutineScope()
    val syncUtils = LocalSyncUtils.current
    var refetchIconDegree by remember { mutableFloatStateOf(0f) }

    val cacheViewModel = hiltViewModel<CachePlaylistViewModel>()

    val downloadUtil = LocalDownloadUtil.current

    // ---- Lossless (Qobuz) download -------------------------------------------------------------
    // Separate from the ExoPlayer download above: this fetches a complete .flac over plain HTTP into
    // a folder the user owns, so the result is a real file other apps can read.
    var losslessFolder by rememberPreference(LosslessDownloadFolderKey, "")
    val embedTags by rememberPreference(LosslessDownloadTagKey, defaultValue = true)
    var qobuzQuality by rememberPreference(QobuzAudioQualityKey, QobuzAudioQuality.FLAC.name)
    // The result callback fires after the download finishes, which may be long after this sheet is
    // gone, so the toast must not hold the Activity context.
    val appContext = remember(context) { context.applicationContext }

    // Observed from the downloader rather than held locally, so the spinner stays correct even if the
    // menu is closed and reopened while the download is still running.
    val activeLosslessDownloads by LosslessDownloader.active.collectAsState()
    val losslessInProgress = song.id in activeLosslessDownloads

    // The global preference is the starting point for the per-download prompt, and the fallback when
    // the user has asked not to be prompted.
    val globalQobuzQuality =
        remember(qobuzQuality) {
            runCatching { QobuzAudioQuality.valueOf(qobuzQuality) }
                .getOrDefault(QobuzAudioQuality.FLAC)
        }

    // Set when the user picks a tier in the dialog; null means "use the global preference".
    var chosenDownloadQuality by remember { mutableStateOf<DownloadQualityChoice?>(null) }
    var showDownloadQualityDialog by rememberSaveable { mutableStateOf(false) }
    var askDownloadQuality by rememberPreference(AskDownloadQualityKey, defaultValue = true)

    // Export: the real container is sniffed from the cached bytes before the dialog opens, so the
    // dialog can grey out targets the app cannot honestly produce.
    var showExportFormatDialog by rememberSaveable { mutableStateOf(false) }
    var exportSourceExtension by rememberSaveable { mutableStateOf<String?>(null) }
    var exportBaseName by rememberSaveable { mutableStateOf("audio") }

    // Runs the download; shared by the "already have a folder" and "just picked one" paths.
    val startLosslessDownload: (Uri) -> Unit = { folderUri ->
        Toast
            .makeText(context, context.getString(R.string.lossless_download_started), Toast.LENGTH_SHORT)
            .show()
        val formatId =
            (chosenDownloadQuality?.qobuzQuality ?: globalQobuzQuality).toFormatId()
        // enqueue, not launch: this must outlive the bottom sheet. A 100MB hi-res FLAC would
        // otherwise be cancelled the moment the user dismisses the menu.
        LosslessDownloader.enqueue(
            context = context,
            request =
                LosslessDownloader.Request(
                    mediaId = song.id,
                    title = song.song.title,
                    artists = song.artists.map { it.name },
                    album = song.song.albumName,
                    durationMs = song.song.duration.takeIf { it > 0 }?.times(1000L),
                    year = song.song.year?.toString(),
                    artworkUrl = song.song.thumbnailUrl,
                ),
            folderUri = folderUri,
            formatId = formatId,
            embedTags = embedTags,
        ) { result ->
            val message =
                when (result) {
                    is LosslessDownloader.Result.Success ->
                        appContext.getString(R.string.lossless_download_saved, result.fileName)
                    LosslessDownloader.Result.NotAvailable ->
                        appContext.getString(R.string.lossless_download_unavailable)
                    is LosslessDownloader.Result.Failed ->
                        appContext.getString(R.string.lossless_download_failed, result.reason)
                }
            Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()
        }
    }

    // SAF folder picker. Scoped storage gives no real path, so we persist the tree Uri and take a
    // persistable grant so the choice survives reboots and we only ask once.
    val pickLosslessFolderLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
            if (treeUri == null) return@rememberLauncherForActivityResult
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            losslessFolder = treeUri.toString()
            startLosslessDownload(treeUri)
        }

    // Resolves the destination folder, then downloads. Re-prompts if the saved grant was revoked,
    // otherwise the write would fail with a confusing permission error.
    val resolveFolderAndDownload: () -> Unit = {
        val saved = losslessFolder
        val usable =
            saved.isNotEmpty() &&
                context.contentResolver
                    .persistedUriPermissions
                    .any { it.uri.toString() == saved && it.isWritePermission }
        if (usable) {
            startLosslessDownload(saved.toUri())
        } else {
            pickLosslessFolderLauncher.launch(null)
        }
    }

    if (showDownloadQualityDialog) {
        DownloadQualityDialog(
            initialChoice = DownloadQualityChoice.forQobuzQuality(globalQobuzQuality),
            onDismiss = { showDownloadQualityDialog = false },
            onConfirm = { choice, rememberChoice ->
                chosenDownloadQuality = choice
                if (rememberChoice) {
                    // Make the choice the new global default and stop prompting, so opting out of
                    // the dialog does not silently revert to a different tier next time.
                    qobuzQuality = choice.qobuzQuality.name
                    askDownloadQuality = false
                }
                resolveFolderAndDownload()
            },
        )
    }

    // Direct export to the device's Downloads folder (via SAF CreateDocument).
    // "audio/*" rather than a concrete type: the real container is sniffed from the cached bytes at
    // launch time, so committing to audio/mpeg here would contradict the extension we pass in.
    val exportToDownloadsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/*")) { destUri ->
            if (destUri == null) return@rememberLauncherForActivityResult
            val songId = song.id
            val songTitle = song.song.title
            coroutineScope.launch {
                val result = exportDownloadedSongToUri(context, downloadUtil, destUri, songId, songTitle)
                val msgResId = result.fold(
                    onSuccess = { R.string.export_to_downloads_success },
                    onFailure = { R.string.export_to_folder_failed },
                )
                Toast.makeText(context, context.getString(msgResId), Toast.LENGTH_SHORT).show()
            }
        }

    if (showExportFormatDialog) {
        ExportFormatDialog(
            sourceExtension = exportSourceExtension,
            onDismiss = { showExportFormatDialog = false },
            onConfirm = { format ->
                exportToDownloadsLauncher.launch("$exportBaseName.${format.extension}")
            },
            // Re-downloading from a lossless provider is the only honest route to a real FLAC, so
            // hand the user straight over to the existing lossless download flow.
            onRedownloadLossless = {
                showExportFormatDialog = false
                if (askDownloadQuality) {
                    showDownloadQualityDialog = true
                } else {
                    resolveFolderAndDownload()
                }
            },
        )
    }

    val rotationAnimation by animateFloatAsState(
        targetValue = refetchIconDegree,
        animationSpec = tween(durationMillis = 800),
        label = "",
    )

    // Artist separators for splitting artist names
    val (artistSeparators) = rememberPreference(ArtistSeparatorsKey, defaultValue = ",;/&")
    val (externalDownloaderEnabled) = rememberPreference(ExternalDownloaderEnabledKey, defaultValue = false)
    val (externalDownloaderPackage) = rememberPreference(ExternalDownloaderPackageKey, defaultValue = "")
    val (speedDialSongIds, onSpeedDialSongIdsChange) = rememberPreference(SpeedDialSongIdsKey, "")
    val speedDialPins = remember(speedDialSongIds) { parseSpeedDialPins(speedDialSongIds) }
    val songPin = remember(song.id) { SpeedDialPin(type = SpeedDialPinType.SONG, id = song.id) }
    val isInSpeedDial =
        remember(speedDialPins, songPin) {
            speedDialPins.any { it.type == songPin.type && it.id == songPin.id }
        }

    val orderedArtists by produceState(initialValue = emptyList<ArtistEntity>(), song) {
        withContext(Dispatchers.IO) {
            val artistMaps = database.songArtistMap(song.id).sortedBy { it.position }
            val sorted =
                artistMaps.mapNotNull { map ->
                    song.artists.firstOrNull { it.id == map.artistId }
                }
            value = sorted
        }
    }

    // Split artists by configured separators
    data class SplitArtist(
        val name: String,
        val originalArtist: ArtistEntity?,
    )

    val splitArtists =
        remember(orderedArtists, artistSeparators) {
            if (artistSeparators.isEmpty()) {
                orderedArtists.map { SplitArtist(it.name, it) }
            } else {
                val separatorRegex = "[${Regex.escape(artistSeparators)}]".toRegex()
                orderedArtists.flatMap { artist ->
                    val parts =
                        artist.name
                            .split(separatorRegex)
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                    if (parts.size > 1) {
                        // If the name contains separators, create split artists
                        // The first part keeps the original artist reference for navigation
                        parts.mapIndexed { index, name ->
                            SplitArtist(name, if (index == 0) artist else null)
                        }
                    } else {
                        listOf(SplitArtist(artist.name, artist))
                    }
                }
            }
        }

    var showEditDialog by rememberSaveable {
        mutableStateOf(false)
    }

    val TextFieldValueSaver: Saver<TextFieldValue, *> =
        Saver(
            save = { it.text },
            restore = { text -> TextFieldValue(text, TextRange(text.length)) },
        )

    var titleField by rememberSaveable(stateSaver = TextFieldValueSaver) {
        mutableStateOf(TextFieldValue(song.song.title))
    }

    var artistField by rememberSaveable(stateSaver = TextFieldValueSaver) {
        mutableStateOf(
            TextFieldValue(
                song.artists
                    .firstOrNull()
                    ?.name
                    .orEmpty(),
            ),
        )
    }

    if (showEditDialog) {
        TextFieldDialog(
            icon = {
                Icon(
                    painter = painterResource(R.drawable.edit),
                    contentDescription = null,
                )
            },
            title = {
                Text(text = stringResource(R.string.edit_song))
            },
            textFields =
                listOf(
                    stringResource(R.string.song_title) to titleField,
                    stringResource(R.string.artist_name) to artistField,
                ),
            onTextFieldsChange = { index, newValue ->
                if (index == 0) {
                    titleField = newValue
                } else {
                    artistField = newValue
                }
            },
            onDoneMultiple = { values ->
                val newTitle = values[0]
                val newArtist = values[1]

                coroutineScope.launch {
                    database.query {
                        update(song.song.copy(title = newTitle))
                        val artist = song.artists.firstOrNull()
                        if (artist != null) {
                            update(artist.copy(name = newArtist))
                        }
                    }

                    showEditDialog = false
                    onDismiss()
                }
            },
            onDismiss = { showEditDialog = false },
        )
    }

    var showChoosePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var showErrorPlaylistAddDialog by rememberSaveable {
        mutableStateOf(false)
    }

    AddToPlaylistDialog(
        isVisible = showChoosePlaylistDialog,
        onGetSong = {
            listOf(song.id)
        },
        onDismiss = {
            showChoosePlaylistDialog = false
        },
        onAddComplete = { songCount, playlistNames ->
            val message =
                when {
                    playlistNames.size == 1 -> context.getString(R.string.added_to_playlist, playlistNames.first())
                    else -> context.getString(R.string.added_to_n_playlists, playlistNames.size)
                }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        },
    )

    if (showErrorPlaylistAddDialog) {
        ListDialog(
            onDismiss = {
                showErrorPlaylistAddDialog = false
                onDismiss()
            },
        ) {
            item {
                ListItem(
                    headlineContent = { Text(text = stringResource(R.string.already_in_playlist)) },
                    leadingContent = {
                        Image(
                            painter = painterResource(R.drawable.close),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
                            modifier = Modifier.size(ListThumbnailSize),
                        )
                    },
                    modifier = Modifier.clickable { showErrorPlaylistAddDialog = false },
                )
            }

            items(listOf(song)) { song ->
                SongListItem(song = song)
            }
        }
    }

    var showSelectArtistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showSelectArtistDialog) {
        ListDialog(
            onDismiss = { showSelectArtistDialog = false },
        ) {
            items(
                items = splitArtists.distinctBy { it.name },
                key = { it.name },
            ) { splitArtist ->
                ListItem(
                    headlineContent = {
                        Text(
                            text = splitArtist.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingContent = {
                        AsyncImage(
                            model =
                                splitArtist.originalArtist?.thumbnailUrl?.resize(
                                    width = 200,
                                    height = 200,
                                    ytimgResizePolicy = YtimgResizePolicy.PreserveOriginal,
                                ),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier =
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape),
                        )
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                splitArtist.originalArtist?.let { artist ->
                                    navController.navigate("artist/${artist.id}")
                                    showSelectArtistDialog = false
                                    onDismiss()
                                }
                            },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        SongListItem(
            song = song,
            badges = {},
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            trailingContent = {
                IconButton(
                    onClick = {
                        val s = song.song.toggleLike()
                        database.query {
                            update(s)
                        }
                        syncUtils.likeSong(s)
                    },
                ) {
                    Icon(
                        painter = painterResource(if (song.song.liked) R.drawable.favorite else R.drawable.favorite_border),
                        tint = if (song.song.liked) MaterialTheme.colorScheme.error else LocalContentColor.current,
                        contentDescription = null,
                    )
                }
            },
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    val bottomSheetPageState = LocalBottomSheetPageState.current
    val isLocalSong = song.song.isLocal
    // Telegram tracks have no YouTube watch endpoint, so YouTube-only actions (e.g. Start radio)
    // are hidden for them — they still support play next / add to queue / add to playlist.
    val isTelegramSong = song.song.id.isTelegramMediaId()

    val startRadioText = stringResource(R.string.start_radio)
    val playNextText = stringResource(R.string.play_next)
    val addToQueueText = stringResource(R.string.add_to_queue)
    val addToPlaylistText = stringResource(R.string.add_to_playlist)
    val shareText = stringResource(R.string.share)
    val editText = stringResource(R.string.edit)

    val primaryActions =
        remember(
            song,
            startRadioText,
            playNextText,
            addToQueueText,
            addToPlaylistText,
            shareText,
            editText,
            isLocalSong,
            onDismiss,
            playerConnection,
        ) {
            buildList {
                if (!isLocalSong && !isTelegramSong) {
                    add(
                        NewAction(
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.radio),
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            text = startRadioText,
                            onClick = {
                                onDismiss()
                                playerConnection.playQueue(YouTubeQueue.radio(song.toMediaMetadata()))
                            },
                        ),
                    )
                }
                add(
                    NewAction(
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.playlist_play),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        text = playNextText,
                        onClick = {
                            onDismiss()
                            playerConnection.playNext(song.toMediaItem())
                        },
                    ),
                )
                add(
                    NewAction(
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.queue_music),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        text = addToQueueText,
                        onClick = {
                            onDismiss()
                            playerConnection.addToQueue(song.toMediaItem())
                        },
                    ),
                )
                add(
                    NewAction(
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.playlist_add),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        text = addToPlaylistText,
                        onClick = { showChoosePlaylistDialog = true },
                    ),
                )
                add(
                    NewAction(
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.share),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        text = shareText,
                        onClick = {
                            onDismiss()
                            if (isLocalSong) {
                                shareLocalAudio(context, song.id, song.format?.mimeType)
                            } else {
                                val intent =
                                    Intent().apply {
                                        action = Intent.ACTION_SEND
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, "https://music.youtube.com/watch?v=${song.id}")
                                    }
                                context.startActivity(Intent.createChooser(intent, null))
                            }
                        },
                    ),
                )
                add(
                    NewAction(
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.edit),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        text = editText,
                        onClick = { showEditDialog = true },
                    ),
                )
            }
        }

    val showMutationSection = event != null || playlistSong != null || isFromCache || !isLocalSong

    LazyColumn(
        contentPadding =
            PaddingValues(
                start = 0.dp,
                top = 0.dp,
                end = 0.dp,
                bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
            ),
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            MenuSurfaceSection(modifier = Modifier.padding(vertical = 6.dp)) {
                NewActionGrid(
                    actions = primaryActions,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (!isLocalSong) {
            item {
                MenuSurfaceSection(modifier = Modifier.padding(vertical = 6.dp)) {
                    ListItem(
                        headlineContent = {
                            Text(
                                text =
                                    stringResource(
                                        if (song.song.inLibrary == null) {
                                            R.string.add_to_library
                                        } else {
                                            R.string.remove_from_library
                                        },
                                    ),
                            )
                        },
                        leadingContent = {
                            Icon(
                                painter =
                                    painterResource(
                                        if (song.song.inLibrary == null) {
                                            R.drawable.library_add
                                        } else {
                                            R.drawable.library_add_check
                                        },
                                    ),
                                contentDescription = null,
                            )
                        },
                        modifier =
                            Modifier.clickable {
                                onDismiss()
                                database.query {
                                    update(song.song.toggleLibrary())
                                }
                            },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        item {
            MenuSurfaceSection(modifier = Modifier.padding(vertical = 6.dp)) {
                ListItem(
                    headlineContent = {
                        Text(
                            text =
                                stringResource(
                                    if (isInSpeedDial) {
                                        R.string.remove_from_speed_dial
                                    } else {
                                        R.string.pin_to_speed_dial
                                    },
                                ),
                        )
                    },
                    leadingContent = {
                        Icon(
                            painter = painterResource(if (isInSpeedDial) R.drawable.bookmark_filled else R.drawable.bookmark),
                            contentDescription = null,
                        )
                    },
                    modifier =
                        Modifier.clickable {
                            val updatedPins = toggleSpeedDialPin(speedDialPins, songPin)
                            onSpeedDialSongIdsChange(serializeSpeedDialPins(updatedPins))
                            onDismiss()
                        },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (showMutationSection) {
            item {
                MenuSurfaceSection(modifier = Modifier.padding(vertical = 6.dp)) {
                    val dividerModifier = Modifier.padding(start = 56.dp)
                    Column {
                        if (event != null) {
                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = stringResource(R.string.remove_from_history),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                },
                                leadingContent = {
                                    Icon(
                                        painter = painterResource(R.drawable.delete),
                                        tint = MaterialTheme.colorScheme.error,
                                        contentDescription = null,
                                    )
                                },
                                modifier =
                                    Modifier.clickable {
                                        onDismiss()
                                        database.query {
                                            delete(event)
                                        }
                                    },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }

                        if (event != null) {
                            HorizontalDivider(
                                modifier = dividerModifier,
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }

                        if (playlistSong != null) {
                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = stringResource(R.string.remove_from_playlist),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                },
                                leadingContent = {
                                    Icon(
                                        painter = painterResource(R.drawable.delete),
                                        tint = MaterialTheme.colorScheme.error,
                                        contentDescription = null,
                                    )
                                },
                                modifier =
                                    Modifier.clickable {
                                        val map = playlistSong.map
                                        coroutineScope.launch(Dispatchers.IO) {
                                            val browseId = playlistBrowseId
                                            if (browseId != null) {
                                                val remoteResult = removeSongFromRemotePlaylist(browseId, map)
                                                if (remoteResult.isFailure) {
                                                    withContext(Dispatchers.Main) {
                                                        Toast
                                                            .makeText(
                                                                context,
                                                                context.getString(R.string.error_unknown),
                                                                Toast.LENGTH_SHORT,
                                                            ).show()
                                                        onDismiss()
                                                    }
                                                    return@launch
                                                }
                                            }
                                            database.withTransaction {
                                                val maxPosition = maxPlaylistSongPosition(map.playlistId) ?: map.position
                                                if (map.position < maxPosition) {
                                                    move(map.playlistId, map.position, maxPosition)
                                                }
                                                delete(map)
                                            }
                                            withContext(Dispatchers.Main) {
                                                onDismiss()
                                            }
                                        }
                                    },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )

                            HorizontalDivider(
                                modifier = dividerModifier,
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }

                        if (isFromCache) {
                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = stringResource(R.string.remove_from_cache),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                },
                                leadingContent = {
                                    Icon(
                                        painter = painterResource(R.drawable.delete),
                                        tint = MaterialTheme.colorScheme.error,
                                        contentDescription = null,
                                    )
                                },
                                modifier =
                                    Modifier.clickable {
                                        onDismiss()
                                        cacheViewModel.removeSongFromCache(song.id)
                                    },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )

                            HorizontalDivider(
                                modifier = dividerModifier,
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }

                        if (!isLocalSong) {
                            when (download?.state) {
                                Download.STATE_COMPLETED -> {
                                    ListItem(
                                        headlineContent = {
                                            Text(
                                                text = stringResource(R.string.remove_download),
                                                color = MaterialTheme.colorScheme.error,
                                            )
                                        },
                                        leadingContent = {
                                            Icon(
                                                painter = painterResource(R.drawable.offline),
                                                tint = MaterialTheme.colorScheme.error,
                                                contentDescription = null,
                                            )
                                        },
                                        modifier =
                                            Modifier.clickable {
                                                DownloadService.sendRemoveDownload(
                                                    context,
                                                    ExoDownloadService::class.java,
                                                    song.id,
                                                    false,
                                                )
                                            },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    )
                                }

                                Download.STATE_QUEUED, Download.STATE_DOWNLOADING -> {
                                    ListItem(
                                        headlineContent = { Text(text = stringResource(R.string.downloading)) },
                                        leadingContent = {
                                            CircularWavyProgressIndicator(
                                                modifier = Modifier.size(24.dp),
                                            )
                                        },
                                        modifier =
                                            Modifier.clickable {
                                                DownloadService.sendRemoveDownload(
                                                    context,
                                                    ExoDownloadService::class.java,
                                                    song.id,
                                                    false,
                                                )
                                            },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    )
                                }

                                else -> {
                                    ListItem(
                                        headlineContent = { Text(text = stringResource(R.string.action_download)) },
                                        leadingContent = {
                                            Icon(
                                                painter = painterResource(R.drawable.download),
                                                contentDescription = null,
                                            )
                                        },
                                        modifier =
                                            Modifier.clickable {
                                                // Clear any failed/queued download and its partial
                                                // bytes before starting a fresh one. A stale cache
                                                // entry makes the server reject the resumed range
                                                // with HTTP 416 (Range Not Satisfiable) once the
                                                // stream URL or content-length changes between
                                                // attempts, so the retry can never succeed.
                                                //
                                                // Copied into a local because `download` is a
                                                // delegated property and cannot be smart-cast.
                                                val existing = download
                                                if (existing != null && existing.state != Download.STATE_COMPLETED) {
                                                    DownloadService.sendRemoveDownload(
                                                        context,
                                                        ExoDownloadService::class.java,
                                                        song.id,
                                                        false,
                                                    )
                                                }
                                                // Wrapped, as elsewhere in the app: removeResource
                                                // throws if the resource is still held open.
                                                runCatching { downloadUtil.downloadCache.removeResource(song.id) }
                                                val downloadRequest =
                                                    DownloadRequest
                                                        .Builder(song.id, song.id.toUri())
                                                        .setCustomCacheKey(song.id)
                                                        .setData(song.song.title.toByteArray())
                                                        .build()
                                                DownloadService.sendAddDownload(
                                                    context,
                                                    ExoDownloadService::class.java,
                                                    downloadRequest,
                                                    false,
                                                )
                                            },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    )
                                }
                            }
                            // Lossless download. Hidden for Telegram tracks, which are already a
                            // direct file source and are not resolvable through the Qobuz proxies.
                            if (!isTelegramSong) {
                                ListItem(
                                    headlineContent = {
                                        Text(text = stringResource(R.string.action_download_lossless))
                                    },
                                    supportingContent = {
                                        Text(text = stringResource(R.string.action_download_lossless_desc))
                                    },
                                    leadingContent = {
                                        if (losslessInProgress) {
                                            CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
                                        } else {
                                            Icon(
                                                painter = painterResource(R.drawable.download),
                                                contentDescription = null,
                                            )
                                        }
                                    },
                                    modifier =
                                        Modifier.clickable(enabled = !losslessInProgress) {
                                            // Ask which tier to fetch, unless the user opted out of
                                            // the prompt, in which case the global preference wins.
                                            if (askDownloadQuality) {
                                                showDownloadQualityDialog = true
                                            } else {
                                                resolveFolderAndDownload()
                                            }
                                        },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                )
                            }
                            // Export — only shown when the download has actually completed.
                            if (download?.state == Download.STATE_COMPLETED) {
                                val safeTitle = song.song.title.trim()
                                    .replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "audio" }
                                ListItem(
                                    headlineContent = {
                                        Text(text = stringResource(R.string.export))
                                    },
                                    leadingContent = {
                                        Icon(
                                            painter = painterResource(R.drawable.download),
                                            contentDescription = null,
                                        )
                                    },
                                    modifier =
                                        Modifier.clickable {
                                            // Sniffing reads the cache off disk, so keep it off the
                                            // main thread, then let the user confirm the container.
                                            coroutineScope.launch {
                                                exportSourceExtension =
                                                    withContext(Dispatchers.IO) {
                                                        detectCachedExtension(
                                                            downloadUtil.downloadCache,
                                                            song.id,
                                                        )
                                                    }
                                                exportBaseName = safeTitle
                                                showExportFormatDialog = true
                                            }
                                        },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                )
                            }
                            if (externalDownloaderEnabled) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 56.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                                ListItem(
                                    headlineContent = { Text(text = stringResource(R.string.open_with_downloader)) },
                                    leadingContent = {
                                        Icon(
                                            painter = painterResource(R.drawable.download),
                                            contentDescription = null,
                                        )
                                    },
                                    modifier =
                                        Modifier.clickable {
                                            onDismiss()
                                            val url = "https://music.youtube.com/watch?v=${song.id}"
                                            if (externalDownloaderPackage.isBlank()) {
                                                Toast
                                                    .makeText(
                                                        context,
                                                        context.getString(R.string.external_downloader_not_configured),
                                                        Toast.LENGTH_LONG,
                                                    ).show()
                                                return@clickable
                                            }
                                            val intent =
                                                android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                                    setPackage(externalDownloaderPackage)
                                                    data = android.net.Uri.parse(url)
                                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                            try {
                                                context.startActivity(intent)
                                            } catch (e: android.content.ActivityNotFoundException) {
                                                Toast
                                                    .makeText(
                                                        context,
                                                        context.getString(R.string.external_downloader_not_installed),
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                            }
                                        },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        item {
            MenuSurfaceSection(modifier = Modifier.padding(vertical = 6.dp)) {
                Column {
                    ListItem(
                        headlineContent = { Text(text = stringResource(R.string.view_artist)) },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.artist),
                                contentDescription = null,
                            )
                        },
                        modifier =
                            Modifier.clickable {
                                if (splitArtists.size == 1 && splitArtists[0].originalArtist != null) {
                                    navController.navigate("artist/${splitArtists[0].originalArtist!!.id}")
                                    onDismiss()
                                } else {
                                    showSelectArtistDialog = true
                                }
                            },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )

                    if (song.song.albumId != null) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )

                        ListItem(
                            headlineContent = { Text(text = stringResource(R.string.view_album)) },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.album),
                                    contentDescription = null,
                                )
                            },
                            modifier =
                                Modifier.clickable {
                                    onDismiss()
                                    navController.navigate("album/${song.song.albumId}")
                                },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            MenuSurfaceSection(modifier = Modifier.padding(vertical = 6.dp)) {
                Column {
                    if (!isLocalSong) {
                        ListItem(
                            headlineContent = { Text(text = stringResource(R.string.refetch)) },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.sync),
                                    contentDescription = null,
                                    modifier = Modifier.graphicsLayer(rotationZ = rotationAnimation),
                                )
                            },
                            modifier =
                                Modifier.clickable {
                                    refetchIconDegree -= 360
                                    coroutineScope.launch(Dispatchers.IO) {
                                        YouTube.queue(listOf(song.id)).onSuccess {
                                            val newSong = it.firstOrNull()
                                            if (newSong != null) {
                                                database.transaction {
                                                    update(song, newSong.toMediaMetadata())
                                                }
                                            }
                                        }
                                    }
                                },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }

                    ListItem(
                        headlineContent = { Text(text = stringResource(R.string.details)) },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.info),
                                contentDescription = null,
                            )
                        },
                        modifier =
                            Modifier.clickable {
                                onDismiss()
                                bottomSheetPageState.show {
                                    ShowMediaInfo(song.id)
                                }
                            },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        }
    }
}


/**
 * Exports a downloaded song to a pre-existing [destUri] (e.g. from CreateDocument).
 */
private suspend fun exportDownloadedSongToUri(
    context: android.content.Context,
    downloadUtil: moe.rukamori.archivetune.playback.DownloadUtil,
    destUri: Uri,
    songId: String,
    songTitle: String,
): Result<Uri> = runCatching {
    withContext(Dispatchers.IO) {
        val cache = downloadUtil.downloadCache
        val spans = getCachedSpansForKey(cache, songId)
        if (spans.isEmpty()) {
            throw IllegalStateException("Download cache is empty for this song")
        }
        writeSpansToUri(context, destUri, spans)
        destUri
    }
}

/**
 * Resolves cached spans for a given [songId]. Tries the key directly first,
 * then falls back to searching all cache keys for a matching entry.
 */
private fun getCachedSpansForKey(
    cache: androidx.media3.datasource.cache.Cache,
    songId: String,
): java.util.NavigableSet<androidx.media3.datasource.cache.CacheSpan> {
    var spans = cache.getCachedSpans(songId)
    if (spans.isNotEmpty()) return spans
    // Fallback: the download may have been stored under a slightly different
    // key (e.g. with a URI prefix). Search all keys for one that ends with
    // the songId or equals it after URI decoding.
    for (key in cache.keys) {
        val cleanKey = key.substringAfterLast("/")
        if (cleanKey == songId || key == songId) {
            spans = cache.getCachedSpans(key)
            if (spans.isNotEmpty()) return spans
        }
    }
    return spans
}

/**
 * Sniffs the container of a cached download so the exported file gets a truthful extension.
 *
 * Reads only the first span's header — every span after it is mid-stream and has no magic bytes.
 * Falls back to "m4a" because YouTube audio is overwhelmingly AAC/Opus in an MP4 container, which is
 * a far better default than the ".mp3" this used to hardcode.
 */
private fun detectCachedExtension(
    cache: androidx.media3.datasource.cache.Cache,
    songId: String,
): String {
    val spans = getCachedSpansForKey(cache, songId)
    val first = spans.sortedBy { it.position }.firstOrNull { it.position == 0L } ?: return "m4a"
    val header = ByteArray(moe.rukamori.archivetune.download.AudioContainer.PROBE_BYTES)
    val read =
        runCatching {
            java.io.FileInputStream(first.file).use { it.read(header) }
        }.getOrDefault(0)
    if (read <= 0) return "m4a"
    return moe.rukamori.archivetune.download.AudioContainer
        .detect(header.copyOf(read))
        ?.extension ?: "m4a"
}

/**
 * Writes cached [spans] (sorted by position) to the output stream at [destUri].
 */
private fun writeSpansToUri(
    context: android.content.Context,
    destUri: Uri,
    spans: java.util.NavigableSet<androidx.media3.datasource.cache.CacheSpan>,
) {
    context.contentResolver.openOutputStream(destUri, "w")?.use { output ->
        spans.sortedBy { it.position }.forEach { span ->
            java.io.FileInputStream(span.file).use { input ->
                input.copyTo(output)
            }
        }
        output.flush()
    } ?: throw IllegalStateException("Could not open destination stream")
}
