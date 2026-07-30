/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalDownloadUtil
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.detectAudioExtensionFromSpans
import moe.rukamori.archivetune.db.entities.extensionToMimeType
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.utils.backToMain

/**
 * A single downloadable song surfaced in the export picker. The [songId] is the
 * raw media id (no source prefix); the actual cached spans may live under
 * "qobuz:$songId" or "tidal:$songId" — see [resolveSpans].
 */
private data class DownloadedSongRow(
    val songId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
    val durationText: String?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportDownloadedSongsScreen(navController: NavController) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val downloadUtil = LocalDownloadUtil.current
    val coroutineScope = rememberCoroutineScope()

    var songs by remember { mutableStateOf<List<DownloadedSongRow>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isExporting by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var exportedCount by remember { mutableStateOf(0) }
    var deletedCount by remember { mutableStateOf(0) }
    var totalCount by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    val selectedIds: SnapshotStateList<String> = remember { mutableStateListOf() }

    // Filter songs by search query (title or artist, case-insensitive).
    // Empty query shows all songs.
    val displayedSongs = remember(songs, searchQuery) {
        if (searchQuery.isBlank()) songs
        else {
            val q = searchQuery.lowercase().trim()
            songs.filter {
                it.title.lowercase().contains(q) || it.artist.lowercase().contains(q)
            }
        }
    }

    // Load the list of downloaded songs from the cache + database.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val cache = downloadUtil.downloadCache
            // De-duplicate cache keys by the underlying song id so "qobuz:<id>"
            // and "<id>" don't both show up as separate rows.
            val songIds =
                cache.keys
                    .map { it.substringAfter(":") }
                    .filter { it.isNotBlank() }
                    .distinct()
            val rows =
                songIds.mapNotNull { songId ->
                    // Only include songs that actually have cached spans.
                    val hasSpans =
                        listOf("qobuz:$songId", "tidal:$songId", "deezer:$songId", songId).any { key ->
                            cache.getCachedSpans(key).isNotEmpty()
                        }
                    if (!hasSpans) return@mapNotNull null
                    val songEntity = database.getSongByIdBlocking(songId)
                    val title =
                        songEntity?.song?.title?.takeIf { it.isNotBlank() }
                            ?: "Unknown song ($songId)"
                    val artist =
                        songEntity?.artists?.firstOrNull()?.name?.takeIf { it.isNotBlank() }
                            ?: songEntity?.album?.title?.takeIf { it.isNotBlank() }
                            ?: ""
                    val thumb = songEntity?.song?.thumbnailUrl
                    DownloadedSongRow(
                        songId = songId,
                        title = title,
                        artist = artist,
                        thumbnailUrl = thumb,
                        durationText = null,
                    )
                }.sortedBy { it.title.lowercase() }
            songs = rows
            isLoading = false
        }
    }

    // SAF folder picker — fires when the user taps "Pick export folder".
    val pickFolderLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
            if (treeUri == null) return@rememberLauncherForActivityResult
            val toExport = songs.filter { it.songId in selectedIds }
            if (toExport.isEmpty()) {
                Toast.makeText(
                    context,
                    context.getString(R.string.export_downloaded_songs_pick_folder_first),
                    Toast.LENGTH_SHORT,
                ).show()
                return@rememberLauncherForActivityResult
            }
            isExporting = true
            totalCount = toExport.size
            exportedCount = 0
            coroutineScope.launch {
                var exported = 0
                var failed = 0
                var skippedWebm = 0
                try {
                    withContext(Dispatchers.IO) {
                        val cache = downloadUtil.downloadCache
                        val parentDocUri =
                            android.provider.DocumentsContract.buildDocumentUriUsingTree(
                                treeUri,
                                android.provider.DocumentsContract.getTreeDocumentId(treeUri),
                            )
                        val tempDir = java.io.File(context.cacheDir, "export_tmp").apply { mkdirs() }
                        loop@ for (row in toExport) {
                            val resolved = resolveSpansWithSource(cache, row.songId) ?: run { failed++; continue@loop }
                            val spans = resolved.spans
                            val detectedExt = detectAudioExtensionFromSpans(spans)
                            // Skip legacy WebM/Opus caches — these were downloaded
                            // before the preferM4A fix in YTPlayerUtils and contain
                            // Opus audio in a Matroska container. jaudiotagger
                            // cannot read WebM (no reader registered), so we can't
                            // tag them, and renaming the bytes to .mp3 would
                            // produce a file most players refuse to play (the
                            // container format is wrong, not just the extension).
                            //
                            // The user's request: "when i export the songs that
                            // were downloaded from youtube music, its not in .webm
                            // format but always .mp3 file". The proper fix is at
                            // the download path (codec-rank-first comparator in
                            // YTPlayerUtils — see that file). For OLD caches that
                            // are already .webm, skipping with a clear count is
                            // the safest path. The user can delete and re-download
                            // the affected songs to get fresh .m4a bytes.
                            if (detectedExt == "webm" || detectedExt == "opus") {
                                skippedWebm++
                                failed++
                                continue@loop
                            }
                            // Determine the user-visible extension:
                            //   - YouTube-sourced downloads (no source prefix on
                            //     the cache key) are exported as .mp3 per the
                            //     user's explicit request. The cached bytes are
                            //     AAC/MP4 (after the codec-rank fix), which most
                            //     Android players detect by magic bytes — they'll
                            //     play correctly even with a .mp3 extension, and
                            //     jaudiotagger reads the file by content (not
                            //     extension) so MP4 tags are written correctly.
                            //   - Lossless sources (Qobuz/Tidal/Deezer) keep
                            //     their native extension (.flac) — the user's
                            //     complaint is specifically about YouTube Music
                            //     exports, not lossless.
                            val isYouTubeSource = resolved.sourceKey == null
                            val exportExt =
                                if (isYouTubeSource && detectedExt == "m4a") "mp3" else detectedExt
                            val mime = extensionToMimeType(exportExt)
                            val safeTitle =
                                row.title
                                    .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                                    .ifBlank { "audio_${row.songId}" }
                            // Stage 1: assemble spans into a single temp file
                            // so jaudiotagger can write metadata tags onto it.
                            // The temp file is named with the *real* detected
                            // extension (m4a/flac/...) so jaudiotagger's
                            // AudioFileIO.read() — which uses the extension to
                            // pick a reader — finds the right format. The final
                            // SAF document is renamed to exportExt (.mp3 for YT)
                            // after tagging is complete.
                            val tempFile = java.io.File(tempDir, "${row.songId}.$detectedExt")
                            try {
                                runCatching {
                                    java.io.FileOutputStream(tempFile).use { output ->
                                        spans.sortedBy { it.position }.forEach { span ->
                                            java.io.FileInputStream(span.file).use { input ->
                                                input.copyTo(output)
                                            }
                                        }
                                        output.flush()
                                    }
                                }.getOrElse {
                                    tempFile.delete()
                                    failed++
                                    continue@loop
                                }
                                // Stage 2: ALWAYS write metadata tags (title, artist,
                                // album, year, track, artwork) via jaudiotagger.
                                //
                                // We always attempt tagging — never skip — because the
                                // user's primary complaint was "all exported songs show
                                // unknown artist, unknown album, no song artwork".
                                // Failure is non-fatal: AudioTagger.tag() wraps every
                                // operation in runCatching, so an unsupported format
                                // just leaves the file untagged without aborting the
                                // export.
                                //
                                // Metadata source priority:
                                //   1. Database SongEntity (populated when the user
                                //      clicked Download — title/artists/album come
                                //      from YouTube Music's browse response OR from
                                //      persistPlaybackMetadata which now inserts an
                                //      ArtistEntity + SongArtistMap from
                                //      videoDetails.author for direct YT downloads).
                                //   2. YouTube.getMediaInfo(videoId) fallback — when
                                //      the database has no artist/album (e.g. the song
                                //      was downloaded via a playlist and the artist
                                //      relation wasn't persisted). Fetches title +
                                //      author + thumbnail from the watch endpoint.
                                //   3. Thumbnail URL from row.thumbnailUrl as the
                                //      embedded artwork bytes.
                                val resolvedMetadata = resolveExportMetadata(database, row)
                                moe.rukamori.archivetune.playback.AudioTagger.tag(tempFile, resolvedMetadata)
                                // Stage 3: copy the tagged temp file to the
                                // user-selected SAF folder. The document is created
                                // with the export extension (which may be .mp3 for
                                // YouTube-sourced files even though the underlying
                                // bytes are AAC/MP4 — see comment on exportExt above).
                                val destUri =
                                    android.provider.DocumentsContract.createDocument(
                                        context.contentResolver,
                                        parentDocUri,
                                        mime,
                                        "$safeTitle.$exportExt",
                                    ) ?: run { failed++; continue@loop }
                                runCatching {
                                    context.contentResolver.openOutputStream(destUri, "w")?.use { output ->
                                        java.io.FileInputStream(tempFile).use { input ->
                                            input.copyTo(output)
                                        }
                                        output.flush()
                                    }
                                }.onSuccess {
                                    exported++
                                    exportedCount = exported
                                }.onFailure { failed++ }
                            } finally {
                                tempFile.delete()
                            }
                        }
                        // Best-effort cleanup of stale temp files from a
                        // previous interrupted export run.
                        runCatching { tempDir.listFiles()?.forEach { it.delete() } }
                    }
                } finally {
                    isExporting = false
                }
                val failedMsg = if (failed > 0) ", $failed failed" else ""
                val webmMsg = if (skippedWebm > 0) {
                    " ($skippedWebm skipped — re-download to enable MP3 export)"
                } else {
                    ""
                }
                Toast.makeText(
                    context,
                    context.getString(
                        R.string.export_downloaded_songs_complete,
                        exported,
                        failedMsg + webmMsg,
                    ),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }

    val allSelected = displayedSongs.isNotEmpty() && selectedIds.size == displayedSongs.size

    /**
     * Deletes the cached spans for the currently-selected song IDs. Releases
     * the underlying cache entries via [Cache.removeResource] for every
     * source-prefixed key the song may live under (qobuz:, tidal:, bare id).
     * Updates the in-memory list so the UI reflects the deletion immediately.
     */
    fun deleteSelected() {
        val toDelete = songs.filter { it.songId in selectedIds }
        if (toDelete.isEmpty()) return
        isDeleting = true
        totalCount = toDelete.size
        deletedCount = 0
        coroutineScope.launch {
            var deleted = 0
            var failed = 0
            try {
                withContext(Dispatchers.IO) {
                    val cache = downloadUtil.downloadCache
                    val playerCache = downloadUtil.playerCache
                    for (row in toDelete) {
                        var removed = false
                        for (key in listOf("qobuz:${row.songId}", "tidal:${row.songId}", "deezer:${row.songId}", row.songId)) {
                            runCatching { cache.removeResource(key) }.onSuccess { removed = true }
                            runCatching { playerCache.removeResource(key) }.onSuccess { removed = true }
                        }
                        if (removed) deleted++ else failed++
                        deletedCount = deleted
                    }
                    // Also cancel any pending Media3 download requests for these ids
                    // so they don't immediately re-create the cache entries.
                    runCatching {
                        toDelete.forEach { row ->
                            downloadUtil.downloadManager.removeDownload(row.songId)
                        }
                    }
                }
            } finally {
                isDeleting = false
            }
            // Refresh the song list to reflect deletions.
            val failedMsg = if (failed > 0) ", $failed failed" else ""
            Toast.makeText(
                context,
                context.getString(
                    R.string.export_downloaded_songs_delete_complete,
                    deleted,
                    failedMsg,
                ),
                Toast.LENGTH_LONG,
            ).show()
            // Update the local list + clear selection.
            val deletedIds = toDelete.map { it.songId }.toSet()
            songs = songs.filterNot { it.songId in deletedIds }
            selectedIds.removeAll(deletedIds)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text(stringResource(R.string.search)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(28.dp),
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(
                                            painter = painterResource(R.drawable.close),
                                            contentDescription = stringResource(R.string.clear_search),
                                        )
                                    }
                                }
                            },
                        )
                    } else {
                        Text(stringResource(R.string.export_downloaded_songs))
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isSearchActive) {
                                isSearchActive = false
                                searchQuery = ""
                            } else {
                                navController.navigateUp()
                            }
                        },
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(
                            painter = painterResource(
                                if (isSearchActive) R.drawable.arrow_back else R.drawable.arrow_back,
                            ),
                            contentDescription = null,
                        )
                    }
                },
                actions = {
                    if (!isSearchActive && songs.isNotEmpty()) {
                        // Search button — toggles the search bar in the title slot.
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(
                                painter = painterResource(R.drawable.search),
                                contentDescription = stringResource(R.string.search),
                            )
                        }
                        IconButton(
                            onClick = {
                                if (allSelected) selectedIds.clear()
                                else {
                                    selectedIds.clear()
                                    selectedIds.addAll(displayedSongs.map { it.songId })
                                }
                            },
                            onLongClick = {},
                        ) {
                            Icon(
                                painter =
                                    painterResource(
                                        if (allSelected) R.drawable.player_deselect else R.drawable.select_all,
                                    ),
                                contentDescription = null,
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (songs.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 4.dp,
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(
                                    LocalPlayerAwareWindowInsets.current.only(
                                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                                    ),
                                ).padding(16.dp),
                    ) {
                        // Count + progress line — full width so the "X of Y selected"
                        // text never gets squeezed into a vertical strip by the two
                        // action buttons below it (previously rendered as "1 / o / f / 1 / …").
                        Text(
                            text =
                                stringResource(
                                    R.string.export_downloaded_songs_selected_count,
                                    selectedIds.size,
                                    displayedSongs.size,
                                ),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                        )
                        if (isExporting || isDeleting) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text =
                                    if (isExporting) {
                                        stringResource(
                                            R.string.export_downloaded_songs_progress,
                                            exportedCount,
                                            totalCount,
                                        )
                                    } else {
                                        stringResource(
                                            R.string.export_downloaded_songs_delete_progress,
                                            deletedCount,
                                            totalCount,
                                        )
                                    },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Delete button — destructive action, gated by a confirmation dialog.
                            OutlinedButton(
                                onClick = { showDeleteConfirm = true },
                                enabled = !isExporting && !isDeleting && selectedIds.isNotEmpty(),
                                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error,
                                ),
                                modifier = Modifier.weight(1f),
                            ) {
                                if (isDeleting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                } else {
                                    Icon(
                                        painter = painterResource(R.drawable.delete),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(stringResource(R.string.export_downloaded_songs_delete))
                            }
                            FilledTonalButton(
                                onClick = { pickFolderLauncher.launch(null) },
                                enabled = !isExporting && !isDeleting && selectedIds.isNotEmpty(),
                                modifier = Modifier.weight(1f),
                            ) {
                                if (isExporting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                } else {
                                    Icon(
                                        painter = painterResource(R.drawable.send),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(stringResource(R.string.export_downloaded_songs_pick_folder))
                            }
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }
            songs.isEmpty() -> {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_download),
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.export_downloaded_songs_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            displayedSongs.isEmpty() -> {
                // Search returned no matches — distinct empty state from "no songs at all".
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.search_off),
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.export_downloaded_songs_search_empty, searchQuery),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding =
                        PaddingValues(
                            top = innerPadding.calculateTopPadding(),
                            bottom = 120.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(displayedSongs, key = { it.songId }) { row ->
                        val isSelected = row.songId in selectedIds
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isSelected) selectedIds.remove(row.songId)
                                        else selectedIds.add(row.songId)
                                    }.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (!row.thumbnailUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = row.thumbnailUrl,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop,
                                    )
                                } else {
                                    Icon(
                                        painter = painterResource(R.drawable.music_note),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = row.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (row.artist.isNotBlank()) {
                                    Text(
                                        text = row.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Box(
                                modifier =
                                    Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant
                                            },
                                        ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isSelected) {
                                    Icon(
                                        painter = painterResource(R.drawable.check),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete confirmation dialog — destructive action needs an explicit OK.
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.export_downloaded_songs_delete_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.export_downloaded_songs_delete_confirm_message,
                        selectedIds.size,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        deleteSelected()
                    },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(R.string.export_downloaded_songs_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

/**
 * Returns the cached spans for [songId], preferring source-prefixed keys
 * ("qobuz:$songId" / "tidal:$songId") so the export pulls the lossless FLAC
 * bytes when available, falling back to the bare media id.
 */
private fun resolveSpans(
    cache: androidx.media3.datasource.cache.Cache,
    songId: String,
): java.util.NavigableSet<androidx.media3.datasource.cache.CacheSpan>? =
    resolveSpansWithSource(cache, songId)?.spans

/**
 * Same as [resolveSpans] but also returns the matched cache key so the caller
 * can determine whether the cached bytes came from a lossless source
 * ("qobuz:$songId" / "tidal:$songId" / "deezer:$songId") or from YouTube
 * Music (bare media id, no prefix).
 *
 * The caller uses [sourceKey] to decide the user-visible export extension:
 * YouTube-sourced AAC caches are renamed to .mp3 (per the user's explicit
 * request), while lossless sources keep their native .flac extension.
 *
 * [sourceKey] is null when the bytes were cached under the bare media id
 * (i.e. YouTube Music is the source). It is non-null for source-prefixed
 * keys (Qobuz/Tidal/Deezer).
 */
private data class ResolvedSpansWithSource(
    val spans: java.util.NavigableSet<androidx.media3.datasource.cache.CacheSpan>,
    val sourceKey: String?,
)

private fun resolveSpansWithSource(
    cache: androidx.media3.datasource.cache.Cache,
    songId: String,
): ResolvedSpansWithSource? {
    for (key in listOf("qobuz:$songId", "tidal:$songId", "deezer:$songId", songId)) {
        val spans = cache.getCachedSpans(key)
        if (spans.isNotEmpty()) {
            val sourceKey = key.takeIf { it != songId }
            return ResolvedSpansWithSource(spans, sourceKey)
        }
    }
    return null
}

/**
 * Fetches the raw bytes of an album-art image from [url] so they can be
 * embedded into the exported audio file via [AudioTagger].
 *
 * Uses a basic [HttpURLConnection][java.net.HttpURLConnection] with a
 * 10-second connect + 15-second read timeout — fast enough to not stall
 * the export pipeline on a slow thumbnail CDN, generous enough to fetch
 * a typical 500×500 JPEG (~50 KB) on a flaky mobile connection.
 *
 * Returns `null` on any error (HTTP non-2xx, IO failure, timeout). The
 * caller treats null artwork as non-fatal — the audio file is still
 * exported, just without embedded artwork.
 *
 * The URL is typically the song's `thumbnailUrl` from the database,
 * which points at iTunes/Qobuz/Tidal/Deezer/YouTube CDN. All of these
 * serve CORS-friendly JPEG/PNG over HTTPS.
 */
private fun fetchArtworkBytes(url: String): ByteArray? = runCatching {
    val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
    connection.connectTimeout = 10_000
    connection.readTimeout = 15_000
    connection.requestMethod = "GET"
    connection.setRequestProperty("User-Agent", "ArchiveTune")
    connection.instanceFollowRedirects = true
    connection.useCaches = true
    try {
        val responseCode = connection.responseCode
        if (responseCode !in 200..299) return@runCatching null
        val contentType = connection.contentType ?: ""
        // Only accept image/* content types — a misconfigured CDN
        // returning HTML (e.g. a login redirect) would corrupt the
        // embedded artwork if we wrote it verbatim.
        if (!contentType.startsWith("image/")) return@runCatching null
        connection.inputStream.use { it.readBytes() }
    } finally {
        connection.disconnect()
    }
}.getOrNull()

/**
 * Resolves the metadata to embed into an exported audio file.
 *
 * Source priority:
 *   1. **Database SongEntity** — populated when the user clicked Download.
 *      Carries title, artists (from song_artist_map), album (from
 *      song_album_map), year, thumbnailUrl.
 *   2. **YouTube.getMediaInfo(videoId) fallback** — when the database row
 *      is incomplete (e.g. the song was downloaded via a playlist and the
 *      artist relation wasn't persisted, or the song row only has the title
 *      because the browse endpoint didn't return album metadata). Fetches
 *      title + author + thumbnail URL from the YouTube watch endpoint.
 *   3. **Row fallback** — uses the [DownloadedSongRow.title] and
 *      [DownloadedSongRow.thumbnailUrl] that were already loaded for the
 *      list display. These come from the same DB row but are always
 *      non-null, so we have a final safety net.
 *
 * Artwork bytes are fetched from the resolved thumbnail URL via
 * [fetchArtworkBytes]. Failure is non-fatal — the audio file is still
 * exported with text tags but no embedded artwork.
 *
 * This function NEVER returns null fields when a fallback exists —
 * the user's complaint was "all exported songs show unknown artist,
 * unknown album, no song artwork", and this resolver exists specifically
 * to fill those gaps before handing the metadata to [AudioTagger.tag].
 *
 * Runs on the calling thread (already on Dispatchers.IO inside the export
 * pipeline). Network calls (YouTube.getMediaInfo + fetchArtworkBytes) have
 * their own timeouts.
 */
private suspend fun resolveExportMetadata(
    database: moe.rukamori.archivetune.db.MusicDatabase,
    row: DownloadedSongRow,
): moe.rukamori.archivetune.playback.AudioTagger.Metadata {
    val songEntity = database.getSongByIdBlocking(row.songId)

    // Title — fall back to the row's title (which already has a sensible
    // "Unknown song (id)" default), then to YouTube.getMediaInfo.
    val dbTitle = songEntity?.song?.title?.takeIf(String::isNotBlank)
    val title = dbTitle ?: row.title.takeIf { it.isNotBlank() }

    // Artist — from song_artist_map, then YouTube.getMediaInfo's author,
    // then empty (AudioTagger skips blank fields).
    val dbArtists = songEntity?.artists?.mapNotNull { it.name.takeIf(String::isNotBlank) }
        ?.takeIf { it.isNotEmpty() }
    val dbArtistStr = dbArtists?.joinToString(", ")

    // Album — from song_album_map → song.album.title, then song.albumName,
    // then null (skipped).
    val dbAlbum = songEntity?.album?.title?.takeIf(String::isNotBlank)
        ?: songEntity?.song?.albumName?.takeIf(String::isNotBlank)

    // Year — from song.year (set when the album was browsed).
    val dbYear = songEntity?.song?.year?.takeIf { it > 0 }

    // Thumbnail URL — prefer the DB row's URL (which is the high-quality
    // version set when the song was inserted), fall back to the row's
    // thumbnailUrl (loaded at screen entry — may be the same or a
    // lower-res variant).
    val dbThumb = songEntity?.song?.thumbnailUrl?.takeIf(String::isNotBlank)
    val thumbUrl = dbThumb ?: row.thumbnailUrl?.takeIf(String::isNotBlank)

    // Fast path: if the DB has all the metadata we need, skip the
    // YouTube.getMediaInfo() network call entirely.
    val hasFullMetadata = title != null && !dbArtistStr.isNullOrBlank() && thumbUrl != null
    if (hasFullMetadata) {
        val artworkBytes = thumbUrl?.let { fetchArtworkBytes(it) }
        return moe.rukamori.archivetune.playback.AudioTagger.Metadata(
            title = title,
            artist = dbArtistStr,
            albumArtist = dbArtists?.firstOrNull(),
            album = dbAlbum,
            year = dbYear,
            artworkBytes = artworkBytes,
        )
    }

    // Fallback: query YouTube.getMediaInfo() for title/author/thumbnail.
    // This is the key fix for "all exported songs show unknown artist" —
    // when the DB row was inserted from a playlist context (not a full
    // browse), the artist relation often isn't persisted. The watch
    // endpoint always returns the author.
    val mediaInfo = runCatching {
        moe.rukamori.archivetune.innertube.YouTube.getMediaInfo(row.songId).getOrNull()
    }.getOrNull()

    val resolvedTitle = title
        ?: mediaInfo?.title?.takeIf(String::isNotBlank)
        ?: row.title
    val resolvedArtist = dbArtistStr
        ?: mediaInfo?.author?.takeIf(String::isNotBlank)
        ?: ""
    val resolvedThumb = thumbUrl
        ?: mediaInfo?.authorThumbnail?.takeIf(String::isNotBlank)

    val artworkBytes = resolvedThumb?.let { fetchArtworkBytes(it) }

    return moe.rukamori.archivetune.playback.AudioTagger.Metadata(
        title = resolvedTitle?.takeIf(String::isNotBlank),
        artist = resolvedArtist.takeIf(String::isNotBlank),
        albumArtist = (dbArtists?.firstOrNull() ?: mediaInfo?.author)?.takeIf(String::isNotBlank),
        album = dbAlbum,
        year = dbYear,
        artworkBytes = artworkBytes,
    )
}
