/*
 * ArchiveTune (2026)
 * © ArchiveTuneFork contributors — github.com/vossgraves/ArchiveTune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package app.atf.media.ui.menu

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.atf.media.R
import app.atf.media.ui.player.CanvasSourceResult
import app.atf.media.ui.player.fetchAllCanvasSourcesForSong
import app.atf.media.utils.CanvasSaver
import app.atf.media.utils.CanvasSaveResult

/**
 * Dialog that lists every available canvas source for a song and lets the
 * user save any of them to internal storage (Movies/ArchiveTune Canvas/).
 *
 * On open, it launches a coroutine to query every canvas source
 * (Spotify Canvas via mlc.kouzu.in, Apple Music via AMP) in parallel via
 * [fetchAllCanvasSourcesForSong]. While loading, a spinner is shown. Once
 * results arrive, each source is rendered as a row with:
 * - source name (Spotify Canvas / Apple Music)
 * - the regular canvas URL (or "Unavailable" if not present)
 * - the vertical canvas URL (or "Unavailable")
 * - a "Save regular" and "Save vertical" button for each available variant
 *
 * Tapping a Save button downloads the video via [CanvasSaver.saveCanvasVideo]
 * and toasts the result. HLS `.m3u8` URLs (typically Apple Music) are
 * rejected up-front with a toast explaining they can't be saved.
 *
 * NOTE: The codebase currently has no Tidal canvas implementation —
 * only Spotify Canvas + Apple Music. When/if Tidal canvas is added,
 * it should be queried in [fetchAllCanvasSourcesForSong] and will
 * automatically appear here.
 */
@Composable
fun SaveCanvasDialog(
    mediaId: String,
    songTitle: String,
    artistName: String,
    albumTitle: String?,
    storefront: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(true) }
    var sources by remember { mutableStateOf<List<CanvasSourceResult>>(emptyList()) }
    var loadFailed by remember { mutableStateOf(false) }
    var savingSourceIndex by remember { mutableStateOf<Int?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(mediaId, songTitle, artistName) {
        isLoading = true
        loadFailed = false
        try {
            val results =
                withContext(Dispatchers.IO) {
                    fetchAllCanvasSourcesForSong(
                        mediaId = mediaId,
                        songTitleRaw = songTitle,
                        artistNameRaw = artistName,
                        storefront = storefront,
                        albumTitle = albumTitle,
                    )
                }
            sources = results
        } catch (e: Exception) {
            loadFailed = true
        } finally {
            isLoading = false
        }
    }

    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = { Text(text = stringResource(R.string.save_canvas_dialog_title)) },
        text = {
            Box(modifier = Modifier.fillMaxWidth()) {
                when {
                    isLoading -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Text(
                                text = stringResource(R.string.save_canvas_loading),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }

                    loadFailed -> {
                        Text(
                            text = stringResource(R.string.save_canvas_loading_failed),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }

                    sources.isEmpty() -> {
                        Text(
                            text = stringResource(R.string.save_canvas_no_sources),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                            contentPadding = PaddingValues(vertical = 4.dp),
                        ) {
                            items(sources.size) { index ->
                                val source = sources[index]
                                CanvasSourceRow(
                                    source = source,
                                    isSavingThis = savingSourceIndex == index,
                                    isSavingAny = isSaving,
                                    onSaveRegular = {
                                        if (isSaving) {
                                            Toast.makeText(
                                                context,
                                                R.string.save_canvas_already_downloading,
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                            return@CanvasSourceRow
                                        }
                                        val url = source.artwork.preferredAnimationUrl
                                        saveCanvas(
                                            url = url,
                                            sourceName = source.sourceName,
                                            songTitle = songTitle,
                                            context = context,
                                            coroutineScope = coroutineScope,
                                            onSavingChange = { isSaving = it },
                                            onIndexChange = { savingSourceIndex = if (it) index else null },
                                            onDismiss = onDismiss,
                                        )
                                    },
                                    onSaveVertical = {
                                        if (isSaving) {
                                            Toast.makeText(
                                                context,
                                                R.string.save_canvas_already_downloading,
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                            return@CanvasSourceRow
                                        }
                                        val url = source.artwork.preferredVerticalAnimationUrl
                                        saveCanvas(
                                            url = url,
                                            sourceName = source.sourceName,
                                            songTitle = songTitle,
                                            context = context,
                                            coroutineScope = coroutineScope,
                                            onSavingChange = { isSaving = it },
                                            onIndexChange = { savingSourceIndex = if (it) index else null },
                                            onDismiss = onDismiss,
                                        )
                                    },
                                )
                                if (index < sources.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun CanvasSourceRow(
    source: CanvasSourceResult,
    isSavingThis: Boolean,
    isSavingAny: Boolean,
    onSaveRegular: () -> Unit,
    onSaveVertical: () -> Unit,
) {
    val regularUrl = source.artwork.preferredAnimationUrl
    val verticalUrl = source.artwork.preferredVerticalAnimationUrl
    val regularDownloadable = CanvasSaver.isDownloadableUrl(regularUrl)
    val verticalDownloadable = CanvasSaver.isDownloadableUrl(verticalUrl)

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = source.sourceName,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "${stringResource(R.string.save_canvas_variant_regular)}: " +
                if (regularDownloadable) {
                    regularUrl!!.take(60) + if (regularUrl.length > 60) "…" else ""
                } else {
                    stringResource(R.string.save_canvas_variant_unavailable)
                },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${stringResource(R.string.save_canvas_variant_vertical)}: " +
                if (verticalDownloadable) {
                    verticalUrl!!.take(60) + if (verticalUrl.length > 60) "…" else ""
                } else {
                    stringResource(R.string.save_canvas_variant_unavailable)
                },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isSavingThis) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(
                    text = stringResource(R.string.save_canvas_saving),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                if (regularDownloadable) {
                    TextButton(onClick = onSaveRegular, enabled = !isSavingAny) {
                        Text(text = stringResource(R.string.save_canvas_variant_regular))
                    }
                }
                if (verticalDownloadable) {
                    TextButton(onClick = onSaveVertical, enabled = !isSavingAny) {
                        Text(text = stringResource(R.string.save_canvas_variant_vertical))
                    }
                }
            }
        }
    }
}

private fun saveCanvas(
    url: String?,
    sourceName: String,
    songTitle: String,
    context: android.content.Context,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onSavingChange: (Boolean) -> Unit,
    onIndexChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    if (url.isNullOrBlank()) {
        Toast.makeText(context, R.string.save_canvas_not_downloadable, Toast.LENGTH_SHORT).show()
        return
    }
    if (!CanvasSaver.isDownloadableUrl(url)) {
        Toast.makeText(context, R.string.save_canvas_not_downloadable, Toast.LENGTH_SHORT).show()
        return
    }
    onSavingChange(true)
    onIndexChange(true)
    Toast.makeText(context, R.string.save_canvas_saving, Toast.LENGTH_SHORT).show()
    coroutineScope.launch {
        val result =
            withContext(Dispatchers.IO) {
                CanvasSaver.saveCanvasVideo(
                    context = context,
                    videoUrl = url,
                    songTitle = songTitle,
                    sourceName = sourceName,
                )
            }
        onSavingChange(false)
        onIndexChange(false)
        when (result) {
            is CanvasSaveResult.Success -> {
                Toast.makeText(context, R.string.save_canvas_saved, Toast.LENGTH_LONG).show()
                onDismiss()
            }
            is CanvasSaveResult.Failure -> {
                Toast.makeText(
                    context,
                    context.getString(R.string.save_canvas_save_failed, result.message),
                    Toast.LENGTH_LONG,
                ).show()
            }
            is CanvasSaveResult.NotDownloadable -> {
                Toast.makeText(context, R.string.save_canvas_not_downloadable, Toast.LENGTH_LONG).show()
            }
        }
    }
}
