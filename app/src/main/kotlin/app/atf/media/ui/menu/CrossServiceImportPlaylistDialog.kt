/*
 * ArchiveTune (2026)
 * © ArchiveTuneFork contributors — github.com/vossgraves/ArchiveTune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.atf.media.ui.menu

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.atf.media.LocalDatabase
import app.atf.media.R
import app.atf.media.constants.InnerTubeCookieKey
import app.atf.media.constants.YtmSyncKey
import app.atf.media.db.entities.PlaylistEntity
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.utils.hasYouTubeLoginCookie
import app.atf.media.models.MediaMetadata
import app.atf.media.playlist.CrossServiceImportCredentials
import app.atf.media.playlist.CrossServicePlaylistImporter
import app.atf.media.ui.component.DefaultDialog
import app.atf.media.utils.dataStore
import timber.log.Timber
import java.time.LocalDateTime

/**
 * Dialog that imports a playlist from a foreign music service URL
 * (Apple Music, Amazon Music, Tidal, Deezer, or YouTube Music). The user
 * pastes a URL, we resolve the source playlist's tracks, then look up
 * each track on YouTube Music and add the resolved song ids to a new
 * (or existing, if the URL was previously imported) local playlist.
 *
 * This entry point lives in the Integration settings screen so the user
 * can pull their library into ArchiveTune without leaving the app.
 */
@Composable
fun CrossServiceImportPlaylistDialog(
    isVisible: Boolean,
    onDismiss: () -> Unit,
) {
    val database = LocalDatabase.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var urlValue by remember { mutableStateOf(TextFieldValue("")) }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var resolvedCount by remember { mutableStateOf(0) }
    var totalCount by remember { mutableStateOf(0) }

    if (!isVisible) return

    fun resetState() {
        urlValue = TextFieldValue("")
        isLoading = false
        statusMessage = null
        resolvedCount = 0
        totalCount = 0
    }

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
                    onValueChange = { urlValue = it },
                    label = { Text(stringResource(R.string.cross_service_import_playlist_url_label)) },
                    placeholder = { Text(stringResource(R.string.cross_service_import_playlist_url_placeholder)) },
                    singleLine = true,
                    enabled = !isLoading,
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
                if (statusMessage != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = statusMessage!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (isLoading) {
                    Spacer(modifier = Modifier.height(16.dp))
                    CircularWavyProgressIndicator(modifier = Modifier.size(36.dp))
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
                    if (url.isBlank()) return@Button
                    isLoading = true
                    statusMessage = context.getString(R.string.cross_service_import_resolving_playlist)
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            // Tidal/Qobuz playlist reads need an account token;
                            // the other services resolve anonymously.
                            val credentials = CrossServiceImportCredentials.load(context)
                            val resolved = CrossServicePlaylistImporter.fetchPlaylist(url, credentials)
                                .getOrElse { e ->
                                    withContext(Dispatchers.Main) {
                                        statusMessage = null
                                        isLoading = false
                                        Toast.makeText(
                                            context,
                                            context.getString(
                                                R.string.cross_service_import_failed,
                                                e.message ?: "Unknown error",
                                            ),
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    }
                                    return@launch
                                }

                            if (resolved.tracks.isEmpty()) {
                                withContext(Dispatchers.Main) {
                                    statusMessage = null
                                    isLoading = false
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.cross_service_import_no_tracks),
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                                return@launch
                            }

                            // Resolve tracks all the way to fully-populated
                            // MediaMetadata (title/artists/album/thumbnail)
                            // so we can insert them into the `song` table
                            // BEFORE linking them to a playlist. Without this,
                            // addSongToPlaylist() trips the
                            // playlist_song_map.songId → song.id FOREIGN KEY
                            // constraint and the whole import fails with
                            // "FOREIGN KEY constraint failed (code 787)".
                            //
                            // YouTube Music already has the song ids natively
                            // (no per-track search needed) but we still fetch
                            // the full SongItems via fetchYouTubePlaylistSongs
                            // so we have the metadata to populate the song row.
                            val songs: List<MediaMetadata> =
                                if (resolved.source == CrossServicePlaylistImporter.ImportSource.YOUTUBE_MUSIC) {
                                    CrossServicePlaylistImporter.fetchYouTubePlaylistSongs(resolved.sourcePlaylistId)
                                } else {
                                    withContext(Dispatchers.Main) {
                                        statusMessage = context.getString(
                                            R.string.cross_service_import_searching_yt,
                                            resolved.tracks.size,
                                        )
                                        totalCount = resolved.tracks.size
                                        resolvedCount = 0
                                    }
                                    CrossServicePlaylistImporter.resolveToYouTubeMusicMetadata(
                                        tracks = resolved.tracks,
                                        onProgress = { done, total ->
                                            resolvedCount = done
                                            totalCount = total
                                        },
                                    )
                                }

                            if (songs.isEmpty()) {
                                withContext(Dispatchers.Main) {
                                    statusMessage = null
                                    isLoading = false
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.cross_service_import_no_matches),
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                                return@launch
                            }

                            // === Foreign-key-safe insert ===
                            // Insert every resolved song into the `song` table
                            // (plus its artist rows via the @Transaction insert
                            // overload) inside a single transaction so a
                            // mid-import crash doesn't leave half the songs
                            // behind. After this, addSongToPlaylist() can
                            // safely create the playlist_song_map rows.
                            database.withTransaction {
                                songs.forEach { meta -> insert(meta) }
                            }
                            val songIds = songs.map { it.id }

                            // Create the local playlist and insert the song ids.
                            val playlistName = resolved.title.ifBlank {
                                "${resolved.source.displayName} Import"
                            }
                            // Re-use an existing playlist if we've imported this URL before.
                            // The synthetic browseId below is just a dedupe key for the *first*
                            // import — once we successfully create a remote YT Music playlist
                            // further down, we overwrite it with the real "VLPL…" browseId so
                            // the playlist becomes server-side and survives local data clears.
                            val syntheticBrowseId = "import:${resolved.source.name}:${resolved.sourcePlaylistId}"
                            val existing = database.playlistByBrowseId(syntheticBrowseId).firstOrNull()
                            val targetPlaylistId = if (existing != null) {
                                database.query {
                                    update(
                                        existing.playlist.copy(
                                            name = playlistName,
                                            bookmarkedAt = existing.playlist.bookmarkedAt ?: LocalDateTime.now(),
                                            lastUpdateTime = LocalDateTime.now(),
                                        ),
                                    )
                                }
                                existing.playlist.id
                            } else {
                                val newPlaylist = PlaylistEntity(
                                    name = playlistName,
                                    browseId = syntheticBrowseId,
                                    isEditable = true,
                                    bookmarkedAt = LocalDateTime.now(),
                                    thumbnailUrl = resolved.thumbnailUrl,
                                )
                                database.query { insert(newPlaylist) }
                                newPlaylist.id
                            }

                            val playlist = database.playlist(targetPlaylistId).firstOrNull()
                            if (playlist != null) {
                                database.addSongToPlaylist(playlist, songIds)
                            }

                            // === Sync the imported playlist to the user's YT Music account ===
                            //
                            // Before this block, imported playlists lived only in the local
                            // database — the synthetic "import:…" browseId is not a real YT
                            // Music playlist id, so when the user cleared app data, reinstalled,
                            // or switched devices, the imported playlist would silently vanish
                            // (the "imported playlists disappeared after some time" report).
                            //
                            // If the user is signed in to YT Music and YT sync is enabled, we
                            // create a real server-side playlist via YouTube.createPlaylist
                            // (which uses the /playlist/create endpoint and accepts the initial
                            // videoIds in the same call), then rewrite the local playlist's
                            // browseId to the returned "VLPL…" id. From that point on:
                            //   - The playlist exists on music.youtube.com and survives local
                            //     data loss.
                            //   - LocalPlaylistViewModel.refresh() will sync server → local
                            //     because browseId is now a real YT Music playlist id.
                            //   - SyncUtils periodic sync will keep it up to date.
                            //
                            // If the user is not signed in or YT sync is disabled, we keep the
                            // synthetic browseId and the playlist stays local-only — same as
                            // the previous behavior, no regression.
                            val preferences = context.dataStore.data.firstOrNull()
                            val isSignedIn = preferences != null &&
                                hasYouTubeLoginCookie(preferences[InnerTubeCookieKey].orEmpty())
                            val isYtSyncEnabled = preferences == null || (preferences[YtmSyncKey] ?: true)

                            if (isSignedIn && isYtSyncEnabled && songIds.isNotEmpty()) {
                                // YouTube.createPlaylist already returns Result<String> (it is
                                // defined as `= runCatching { ... }`), so we call .onSuccess /
                                // .onFailure directly on it. Wrapping it in another runCatching
                                // would produce Result<Result<String>> and break compilation.
                                YouTube.createPlaylist(playlistName, songIds)
                                    .onSuccess { remoteBrowseId ->
                                        if (remoteBrowseId.isNotBlank()) {
                                            val toUpdate = database.playlist(targetPlaylistId).firstOrNull()
                                            if (toUpdate != null) {
                                                database.query {
                                                    update(
                                                        toUpdate.playlist.copy(
                                                            browseId = remoteBrowseId,
                                                            isEditable = true,
                                                            bookmarkedAt = toUpdate.playlist.bookmarkedAt ?: LocalDateTime.now(),
                                                            lastUpdateTime = LocalDateTime.now(),
                                                        ),
                                                    )
                                                }
                                            }
                                        }
                                    }.onFailure { error ->
                                        // Don't fail the whole import — the local playlist is
                                        // already created and populated. The user just doesn't get
                                        // server-side sync this time. They can pull-to-refresh on
                                        // the playlist later to retry, or sign in and re-import.
                                        Timber.w(
                                            error,
                                            "Remote YT Music playlist creation failed during import; " +
                                                "playlist remains local-only.",
                                        )
                                    }
                            }

                            withContext(Dispatchers.Main) {
                                isLoading = false
                                statusMessage = null
                                Toast.makeText(
                                    context,
                                    context.getString(
                                        R.string.cross_service_import_success,
                                        songIds.size,
                                        resolved.tracks.size,
                                        playlistName,
                                    ),
                                    Toast.LENGTH_LONG,
                                ).show()
                                resetState()
                                onDismiss()
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                statusMessage = null
                                isLoading = false
                                Toast.makeText(
                                    context,
                                    context.getString(
                                        R.string.cross_service_import_failed,
                                        e.message ?: "Unknown error",
                                    ),
                                    Toast.LENGTH_LONG,
                                ).show()
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
