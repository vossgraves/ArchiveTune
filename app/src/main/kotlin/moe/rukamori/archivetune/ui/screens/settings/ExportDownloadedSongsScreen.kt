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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.download.CacheExporter
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.SongListItem
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.viewmodels.ExportDownloadedSongsViewModel

/**
 * Lets the user pick WHICH downloaded songs to export, as opposed to the all-or-nothing
 * "Export downloaded songs" action in [StorageSettings].
 *
 * This is a front-end only. The copy/tag/cancel work stays in [CacheExporter], which already accepts
 * an arbitrary song list, so both entry points share one engine and one progress surface. That also
 * means an export started here keeps running after navigating away, and is still reported when the
 * user comes back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportDownloadedSongsScreen(
    navController: NavController,
    viewModel: ExportDownloadedSongsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val exportProgress by CacheExporter.progress.collectAsStateWithLifecycle()

    // Survives process death so a selection is not silently lost while the SAF folder picker — a
    // separate activity — is in the foreground. Saved through an explicit List<String> saver: a raw
    // Set has no built-in saver, so relying on the default would depend on the concrete set
    // implementation happening to be Serializable.
    val selectedIds =
        rememberSaveable(
            stateSaver =
                listSaver<Set<String>, String>(
                    save = { it.toList() },
                    restore = { it.toSet() },
                ),
        ) { mutableStateOf(emptySet<String>()) }

    // Songs load asynchronously and a download can be cleared elsewhere, so drop ids that no longer
    // exist rather than counting them towards the selection and exporting nothing for them.
    val availableIds = remember(songs) { songs.map { it.song.id }.toSet() }
    LaunchedEffect(availableIds) {
        selectedIds.value = selectedIds.value intersect availableIds
    }

    val isExporting = exportProgress?.running == true
    val allSelected = songs.isNotEmpty() && selectedIds.value.size == songs.size

    val exportFolderLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
            treeUri?.let { viewModel.export(it, selectedIds.value) }
        }

    LaunchedEffect(exportProgress?.running) {
        val finished =
            exportProgress?.takeIf { !it.running && it.processed > 0 } ?: return@LaunchedEffect
        Toast.makeText(context, finished.summary(context), Toast.LENGTH_LONG).show()
        CacheExporter.clearProgress()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.export_downloaded_songs)) },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(
                            painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                },
                actions = {
                    if (songs.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                selectedIds.value =
                                    if (allSelected) emptySet() else availableIds
                            },
                        ) {
                            Text(
                                stringResource(
                                    if (allSelected) R.string.deselect_all else R.string.select_all,
                                ),
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                ),
        ) {
            when {
                loading ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }

                songs.isEmpty() ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.no_downloads),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                else ->
                    LazyColumn(Modifier.weight(1f)) {
                        items(songs, key = { it.song.id }) { song ->
                            val isSelected = song.song.id in selectedIds.value
                            SongListItem(
                                song = song,
                                showDownloadIcon = false,
                                trailingContent = {
                                    Checkbox(
                                        checked = isSelected,
                                        // The whole row toggles; a nested clickable target here would
                                        // just intercept taps meant for the row.
                                        onCheckedChange = null,
                                    )
                                },
                                modifier =
                                    Modifier.clickable(enabled = !isExporting) {
                                        selectedIds.value =
                                            if (isSelected) {
                                                selectedIds.value - song.song.id
                                            } else {
                                                selectedIds.value + song.song.id
                                            }
                                    },
                            )
                        }
                    }
            }

            if (songs.isNotEmpty()) {
                ExportActionBar(
                    selectedCount = selectedIds.value.size,
                    totalCount = songs.size,
                    progress = exportProgress,
                    onExport = { exportFolderLauncher.launch(null) },
                    onCancel = CacheExporter::cancel,
                )
            }
        }
    }
}

/**
 * Pinned footer: selection count, live progress and the export/cancel trigger.
 *
 * Kept out of the scrolling list so the primary action stays reachable in a library of thousands of
 * songs, where a footer inside the list would sit thousands of rows down.
 */
@Composable
private fun ExportActionBar(
    selectedCount: Int,
    totalCount: Int,
    progress: CacheExporter.Progress?,
    onExport: () -> Unit,
    onCancel: () -> Unit,
) {
    val isExporting = progress?.running == true

    Surface(tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            HorizontalDivider(Modifier.padding(bottom = 12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text =
                            stringResource(
                                R.string.export_selected_of_total,
                                selectedCount,
                                totalCount,
                            ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (isExporting && progress != null) {
                        Text(
                            text =
                                stringResource(
                                    R.string.export_in_progress,
                                    progress.processed,
                                    progress.total,
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (isExporting) {
                    TextButton(onClick = onCancel) {
                        Text(stringResource(R.string.cancel_button))
                    }
                } else {
                    Button(onClick = onExport, enabled = selectedCount > 0) {
                        Text(stringResource(R.string.export))
                    }
                }
            }

            if (isExporting && progress != null && progress.total > 0) {
                LinearProgressIndicator(
                    progress = { progress.processed.toFloat() / progress.total },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        }
    }
}
</content>
