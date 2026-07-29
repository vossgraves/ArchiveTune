/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.menu

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.playlist.CrossServicePlaylistImporter
import moe.rukamori.archivetune.ui.component.DefaultDialog

/**
 * Imports a playlist from another streaming service (Apple Music, Amazon Music, Tidal, Deezer, or
 * YouTube Music) by URL.
 *
 * This dialog owns only the *resolution* half of the flow: take a URL, work out which service it
 * came from, pull the tracklist, and match each track to a YouTube Music song id. Once it has ids
 * it hands off to [ImportPlaylistDialog], which already owns the *persistence* half -- letting the
 * user rename the playlist, detecting a playlist that was already imported, and writing the rows.
 * Reimplementing that here would leave two copies of the same duplicate-detection logic to drift
 * apart, and would silently drop the rename step.
 */
@Composable
fun CrossServiceImportPlaylistDialog(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    snackbarHostState: SnackbarHostState? = null,
) {
    val coroutineScope = rememberCoroutineScope()

    var urlValue by remember { mutableStateOf(TextFieldValue("")) }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var resolvedCount by remember { mutableIntStateOf(0) }
    var totalCount by remember { mutableIntStateOf(0) }

    // Non-null once resolution succeeds, which advances the flow to stage 2.
    var resolvedTitle by remember { mutableStateOf<String?>(null) }
    var resolvedSongIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var resolvedBrowseId by remember { mutableStateOf<String?>(null) }

    fun resetState() {
        urlValue = TextFieldValue("")
        isLoading = false
        statusMessage = null
        errorMessage = null
        resolvedCount = 0
        totalCount = 0
        resolvedTitle = null
        resolvedSongIds = emptyList()
        resolvedBrowseId = null
    }

    // Stage 2: ids in hand, so reuse the standard import dialog for naming and persistence.
    val pendingTitle = resolvedTitle
    if (pendingTitle != null) {
        ImportPlaylistDialog(
            isVisible = true,
            onGetSong = { resolvedSongIds },
            playlistTitle = pendingTitle,
            browseId = resolvedBrowseId,
            snackbarHostState = snackbarHostState,
            onDismiss = {
                resetState()
                onDismiss()
            },
        )
        return
    }

    if (!isVisible) return

    // Resolved eagerly: these are read inside a coroutine, where stringResource is unavailable.
    val unsupportedMessage = stringResource(R.string.cross_service_import_unsupported)
    val noTracksMessage = stringResource(R.string.cross_service_import_no_tracks)
    val noMatchesMessage = stringResource(R.string.cross_service_import_no_matches)
    val resolvingMessage = stringResource(R.string.cross_service_import_resolving_playlist)

    // Stage 1: URL entry and resolution progress.
    DefaultDialog(
        onDismiss = {
            if (!isLoading) {
                resetState()
                onDismiss()
            }
        },
        title = { Text(text = stringResource(R.string.cross_service_import_playlist_title)) },
        icon = { Icon(painter = painterResource(R.drawable.playlist_import), contentDescription = null) },
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                OutlinedTextField(
                    value = urlValue,
                    onValueChange = {
                        urlValue = it
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.cross_service_import_playlist_url_label)) },
                    placeholder = { Text(stringResource(R.string.cross_service_import_playlist_url_placeholder)) },
                    singleLine = true,
                    enabled = !isLoading,
                    isError = errorMessage != null,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.cross_service_import_playlist_supported),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth(),
                )

                errorMessage?.let { message ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (isLoading) {
                    Spacer(modifier = Modifier.height(16.dp))
                    CircularWavyProgressIndicator(modifier = Modifier.size(36.dp))

                    statusMessage?.let { message ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    if (totalCount > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(
                                R.string.cross_service_import_progress,
                                resolvedCount,
                                totalCount,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        buttons = {
            TextButton(
                enabled = !isLoading,
                onClick = {
                    resetState()
                    onDismiss()
                },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(text = stringResource(android.R.string.cancel))
            }

            Button(
                enabled = !isLoading && urlValue.text.isNotBlank(),
                onClick = {
                    val url = urlValue.text.trim()

                    // Validate before any network work, so a typo gives an instant localized
                    // message rather than a failed request several seconds later.
                    if (CrossServicePlaylistImporter.detectSource(url) ==
                        CrossServicePlaylistImporter.ImportSource.UNKNOWN
                    ) {
                        errorMessage = unsupportedMessage
                        return@Button
                    }

                    isLoading = true
                    errorMessage = null
                    statusMessage = resolvingMessage

                    coroutineScope.launch(Dispatchers.IO) {
                        val outcome = runCatching {
                            val resolved = CrossServicePlaylistImporter.fetchPlaylist(url).getOrThrow()
                            if (resolved.tracks.isEmpty()) error(noTracksMessage)

                            withContext(Dispatchers.Main) {
                                statusMessage = null
                                totalCount = resolved.tracks.size
                                resolvedCount = 0
                            }

                            // YouTube Music tracks already carry their song ids, so this returns
                            // straight away for that source instead of re-searching every title.
                            val ids = CrossServicePlaylistImporter.resolveToYouTubeMusic(
                                tracks = resolved.tracks,
                                onProgress = { done, total ->
                                    resolvedCount = done
                                    totalCount = total
                                },
                            )
                            if (ids.isEmpty()) error(noMatchesMessage)

                            resolved to ids
                        }

                        withContext(Dispatchers.Main) {
                            isLoading = false
                            statusMessage = null
                            outcome
                                .onSuccess { (resolved, ids) ->
                                    resolvedSongIds = ids
                                    // Only YouTube Music has a browseId the rest of the app can
                                    // use: browseId drives "online_playlist/$browseId" navigation
                                    // and YouTube cover lookups, so a synthetic value would break
                                    // both. Leaving it null also marks the playlist editable, which
                                    // is right for a one-off import from a foreign service.
                                    resolvedBrowseId = resolved.sourcePlaylistId.takeIf {
                                        resolved.source ==
                                            CrossServicePlaylistImporter.ImportSource.YOUTUBE_MUSIC
                                    }
                                    // Assigned last: this is what advances the dialog to stage 2.
                                    resolvedTitle = resolved.title.ifBlank { resolved.source.displayName }
                                }
                                .onFailure { throwable ->
                                    errorMessage = throwable.message ?: unsupportedMessage
                                }
                        }
                    }
                },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(text = stringResource(R.string.cross_service_import_action))
            }
        },
    )
}
