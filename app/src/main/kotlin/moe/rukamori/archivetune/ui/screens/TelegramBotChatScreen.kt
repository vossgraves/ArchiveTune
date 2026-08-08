/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * One-on-one chat screen with a saved Telegram bot. The user pastes a song link, the app sends it
 * to the bot via [TdApi.SendMessage], then waits on [TelegramBotClient.messagesForChat] for audio
 * replies. Each reply is persisted as a Song + Format row so it can be played / downloaded /
 * added-to-playlist through the existing infrastructure.
 *
 * When the user adds a bot-fetched song to a Telegram-channel playlist (LPtg<chatId>) AND the
 * "Auto-forward to my channel" toggle is on, the original bot message is forwarded to that channel
 * via [TdApi.ForwardMessages] — matching the user's spec: "if I add it to my telegram playlist the
 * song should also get forwarded to my own channel automatically".
 *
 * "Streaming a lot of files": the collector returns every audio reply that arrives within the
 * timeout window, and the "Play all" button loads them all into the player queue at once.
 */

package moe.rukamori.archivetune.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalDownloadUtil
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.TelegramBotForwardToChannelKey
import moe.rukamori.archivetune.constants.TelegramBotsKey
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.playback.ExoDownloadService
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.telegram.TelegramBot
import moe.rukamori.archivetune.telegram.TelegramBotClient
import moe.rukamori.archivetune.telegram.TelegramBotCodec
import moe.rukamori.archivetune.telegram.TelegramTrack
import moe.rukamori.archivetune.telegram.toFormatEntity
import moe.rukamori.archivetune.telegram.toMediaMetadata
import moe.rukamori.archivetune.ui.component.FrostedTopAppBar
import moe.rukamori.archivetune.ui.menu.AddToPlaylistDialog
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.rememberPreference
import androidx.core.net.toUri
import kotlinx.coroutines.flow.first
import moe.rukamori.archivetune.LocalDatabase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramBotChatScreen(
    botId: String,
    navController: NavController,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current
    val downloadUtil = LocalDownloadUtil.current
    val coroutineScope = rememberCoroutineScope()
    val (rawBots, onBotsChange) = rememberPreference(TelegramBotsKey, "")
    val (forwardToChannel) = rememberPreference(TelegramBotForwardToChannelKey, true)

    val bot: TelegramBot? = remember(rawBots) {
        TelegramBotCodec.decode(rawBots).find { it.id == botId }
    }

    var songLink by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var noReply by remember { mutableStateOf(false) }
    val results = remember { mutableStateListOf<TelegramTrack>() }

    // Add-to-playlist dialog state. The "pending track" is the track the user wants to add; when
    // set, the dialog is shown.
    var addToPlaylistTrack by remember { mutableStateOf<TelegramTrack?>(null) }

    if (bot == null) {
        // Bot not found — the user must have removed it from another device. Bounce back.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            TextButton(onClick = { navController.navigateUp() }) {
                Text("Bot not found — tap to go back")
            }
        }
        return
    }

    fun persistBot(updated: TelegramBot) {
        val list = TelegramBotCodec.decode(rawBots).map { if (it.id == updated.id) updated else it }
        onBotsChange(TelegramBotCodec.encode(list))
    }

    suspend fun ensureBotChatId(): TelegramBot {
        if (bot.chatId != 0L) return bot
        val resolved = TelegramBotClient.resolveBot(bot.username) ?: return bot
        val title = runCatching {
            val type = resolved.type as org.drinkless.tdlib.TdApi.ChatTypePrivate
            moe.rukamori.archivetune.telegram.TelegramClient.send(
                org.drinkless.tdlib.TdApi.GetUser(type.userId),
            ).firstName
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: bot.title
        val updated = bot.copy(chatId = resolved.id, title = title.ifBlank { bot.title })
        persistBot(updated)
        return updated
    }

    fun send() {
        val link = songLink.trim()
        if (link.isBlank() || sending) return
        sending = true
        noReply = false
        results.clear()
        coroutineScope.launch {
            val activeBot = ensureBotChatId()
            if (activeBot.chatId == 0L) {
                sending = false
                Toast.makeText(context, R.string.telegram_bots_resolve_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val sent = runCatching {
                TelegramBotClient.sendTextMessage(activeBot.chatId, link)
            }.getOrElse {
                sending = false
                Toast.makeText(
                    context,
                    context.getString(R.string.telegram_error, it.message ?: "?"),
                    Toast.LENGTH_SHORT,
                ).show()
                return@launch
            }
            val tracks = TelegramBotClient.collectAudioReplies(
                chatId = activeBot.chatId,
                afterMessageId = sent.id,
                expectedCount = 0,
            )
            sending = false
            if (tracks.isEmpty()) {
                noReply = true
                return@launch
            }
            // Persist each track as a song + format row so playback / download / playlist add work
            // through the existing code paths.
            database.withTransaction {
                tracks.forEach { track ->
                    insert(track.toMediaMetadata(activeBot.title))
                    upsert(track.toFormatEntity())
                }
            }
            results.clear()
            results.addAll(tracks)
        }
    }

    fun playTrack(track: TelegramTrack) {
        if (playerConnection == null) return
        val mediaItem = track.toMediaMetadata(bot.title).toMediaItem()
        playerConnection.playQueue(ListQueue(title = bot.title, items = listOf(mediaItem)))
    }

    fun playAll() {
        if (playerConnection == null || results.isEmpty()) return
        val items = results.map { it.toMediaMetadata(bot.title).toMediaItem() }
        playerConnection.playQueue(ListQueue(title = bot.title, items = items))
    }

    fun downloadTrack(track: TelegramTrack) {
        // DownloadRequest uses the song id as both the media id and the URI. The same
        // SchemeRoutingDataSource that powers streaming also handles downloads for telegram:// URIs.
        runCatching { downloadUtil.downloadCache.removeResource(track.mediaId) }
        val request = DownloadRequest.Builder(track.mediaId, track.mediaId.toUri())
            .setCustomCacheKey(track.mediaId)
            .setData(track.displayTitle.toByteArray())
            .build()
        runCatching {
            DownloadService.sendAddDownload(context, ExoDownloadService::class.java, request, false)
        }.onFailure {
            Toast.makeText(context, R.string.telegram_error_generic, Toast.LENGTH_SHORT).show()
        }
    }

    fun addToPlaylist(track: TelegramTrack) {
        addToPlaylistTrack = track
    }

    // After AddToPlaylistDialog closes, check whether the song was added to any Telegram-channel
    // playlist (LPtg…) and forward the original bot message to those channels. This is the
    // "auto-forward to my channel" behavior the user asked for.
    suspend fun maybeForwardToTelegramChannels(track: TelegramTrack) {
        if (!forwardToChannel) return
        // playlistDuplicates returns the song ids present in the playlist, so a non-empty result
        // means the song was added. Snapshot the playlist list once, then probe each Telegram-
        // channel playlist (LPtg<chatId>) for the just-added song.
        val playlists = database.playlistsByCreateDateAsc().first()
        val tgPlaylists = playlists.filter { it.id.startsWith("LPtg") }
        for (playlist in tgPlaylists) {
            val inPlaylist = database.withTransaction {
                playlistDuplicates(playlist.id, listOf(track.mediaId)).isNotEmpty()
            }
            if (!inPlaylist) continue
            val chatId = playlist.id.removePrefix("LPtg").toLongOrNull() ?: continue
            runCatching {
                TelegramBotClient.forwardMessage(
                    toChatId = chatId,
                    fromChatId = track.chatId,
                    messageId = track.messageId,
                )
            }.onSuccess {
                Toast.makeText(context, R.string.telegram_bot_forwarded, Toast.LENGTH_SHORT).show()
            }.onFailure { e ->
                Toast.makeText(
                    context,
                    context.getString(R.string.telegram_bot_forward_failed, e.message ?: "?"),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    addToPlaylistTrack?.let { track ->
        AddToPlaylistDialog(
            isVisible = true,
            onGetSong = { listOf(track.mediaId) },
            onDismiss = { addToPlaylistTrack = null },
            onAddComplete = { _, _ ->
                // Auto-forward to Telegram-channel playlists if enabled. The dialog runs the add
                // inside its own transaction; we re-snapshot on completion.
                if (forwardToChannel) {
                    coroutineScope.launch { maybeForwardToTelegramChannels(track) }
                }
            },
        )
    }

    Scaffold(
        topBar = {
            FrostedTopAppBar(
                title = { Text(stringResource(R.string.telegram_bot_chat_title, bot.username)) },
                onBack = navController::navigateUp,
                onBackLongClick = navController::backToMain,
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding())
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                        ),
                    ),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = songLink,
                    onValueChange = { songLink = it },
                    placeholder = { Text(stringResource(R.string.telegram_bot_chat_hint)) },
                    singleLine = true,
                    enabled = !sending,
                    trailingIcon = {
                        IconButton(onClick = ::send, enabled = !sending && songLink.isNotBlank()) {
                            if (sending) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (sending) {
                Box(
                    Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.telegram_bot_waiting))
                    }
                }
            }

            if (noReply) {
                Box(
                    Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.telegram_bot_no_reply),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (results.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.telegram_bot_results, results.size),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                    )
                    if (results.size > 1) {
                        TextButton(onClick = ::playAll) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.telegram_bot_play_all))
                        }
                    }
                }
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    items(results, key = TelegramTrack::mediaId) { track ->
                        BotResultRow(
                            track = track,
                            onStream = { playTrack(track) },
                            onDownload = { downloadTrack(track) },
                            onAddToPlaylist = { addToPlaylist(track) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BotResultRow(
    track: TelegramTrack,
    onStream: () -> Unit,
    onDownload: () -> Unit,
    onAddToPlaylist: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = track.displayTitle,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val performer = track.performer ?: track.lookupMetadata.artist
            if (!performer.isNullOrBlank()) {
                Text(
                    text = performer,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onStream) {
            Icon(Icons.Outlined.PlayArrow, contentDescription = stringResource(R.string.telegram_bot_stream))
        }
        IconButton(onClick = onDownload) {
            Icon(Icons.Outlined.Download, contentDescription = stringResource(R.string.telegram_bot_download))
        }
        IconButton(onClick = onAddToPlaylist) {
            Icon(Icons.Outlined.PlaylistAdd, contentDescription = stringResource(R.string.telegram_bot_add_to_playlist))
        }
    }
}
