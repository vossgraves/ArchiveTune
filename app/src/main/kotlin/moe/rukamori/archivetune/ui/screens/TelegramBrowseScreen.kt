/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Telegram channel browser: search public channels, open one, and stream its audio files.
 * Audio messages and audio-typed documents are merged (two independent TDLib paging cursors);
 * the "lossless only" preference filters the listing down to FLAC/WAV/AIFF/… files.
 */

package moe.rukamori.archivetune.ui.screens

import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.TelegramLosslessOnlyKey
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.telegram.TelegramChannel
import moe.rukamori.archivetune.telegram.TelegramClient
import moe.rukamori.archivetune.telegram.TelegramTrack
import moe.rukamori.archivetune.telegram.fileExtension
import moe.rukamori.archivetune.telegram.toMediaMetadata
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.rememberPreference
import org.drinkless.tdlib.TdApi
import java.util.Locale

const val TELEGRAM_BROWSE_ROUTE = "telegram/browse"
const val TELEGRAM_CHANNEL_ROUTE = "telegram/channel/{chatId}"

fun telegramChannelRoute(chatId: Long) = "telegram/channel/$chatId"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramBrowseScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var query by rememberSaveable { mutableStateOf("") }
    val results = remember { mutableStateListOf<TelegramChannel>() }
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }

    fun search() {
        val trimmed = query.trim()
        if (trimmed.isEmpty() || searching) return
        searching = true
        coroutineScope.launch {
            val found =
                runCatching { TelegramClient.searchChannels(trimmed) }
                    .onFailure { e ->
                        Toast
                            .makeText(
                                context,
                                context.getString(R.string.telegram_error, e.message ?: "?"),
                                Toast.LENGTH_SHORT,
                            ).show()
                    }.getOrDefault(emptyList())
            results.clear()
            results.addAll(found)
            searching = false
            searched = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.telegram_browse_channels)) },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                },
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
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.telegram_search_channels_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search() }),
                    trailingIcon = {
                        IconButton(onClick = ::search, onLongClick = {}) {
                            Icon(painterResource(R.drawable.search), contentDescription = null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            when {
                searching -> {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                searched && results.isEmpty() -> {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.telegram_no_results))
                    }
                }

                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(results, key = TelegramChannel::chatId) { channel ->
                            TelegramChannelRow(
                                channel = channel,
                                onClick = { navController.navigate(telegramChannelRoute(channel.chatId)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TelegramChannelRow(
    channel: TelegramChannel,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            val thumb = channel.photoMinithumbnail
            if (thumb != null) {
                AsyncImage(
                    model = thumb,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    painterResource(R.drawable.provider_telegram),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = channel.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val kind =
                stringResource(
                    if (channel.isBroadcastChannel) R.string.telegram_channel else R.string.telegram_group,
                )
            val details =
                buildList {
                    channel.username?.let { add("@$it") }
                    add(kind)
                    if (channel.memberCount > 0) {
                        add(
                            stringResource(
                                R.string.telegram_members,
                                String.format(Locale.getDefault(), "%,d", channel.memberCount),
                            ),
                        )
                    }
                }.joinToString(" • ")
            Text(
                text = details,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramChannelScreen(
    navController: NavController,
    chatId: Long,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current ?: return
    val (losslessOnly) = rememberPreference(TelegramLosslessOnlyKey, true)

    var channel by remember { mutableStateOf<TelegramChannel?>(null) }
    val tracks = remember { mutableStateListOf<TelegramTrack>() }
    var loading by remember { mutableStateOf(false) }
    var initialLoadDone by remember { mutableStateOf(false) }
    var audioCursor by remember { mutableStateOf(0L) }
    var docCursor by remember { mutableStateOf(0L) }
    var audioExhausted by remember { mutableStateOf(false) }
    var docExhausted by remember { mutableStateOf(false) }
    val seenMessageIds = remember { mutableSetOf<Long>() }

    val allExhausted = audioExhausted && docExhausted

    suspend fun loadMore() {
        if (loading) return
        loading = true
        try {
            var added = 0
            var rounds = 0
            while (added < PAGE_TARGET && !(audioExhausted && docExhausted) && rounds < MAX_LOAD_ROUNDS) {
                rounds++
                if (!audioExhausted) {
                    val page =
                        TelegramClient.fetchAudioPage(
                            chatId = chatId,
                            fromMessageId = audioCursor,
                            limit = PAGE_FETCH_LIMIT,
                            filter = TdApi.SearchMessagesFilterAudio(),
                        )
                    audioCursor = page.nextFromMessageId
                    if (page.nextFromMessageId == 0L) audioExhausted = true
                    page.tracks.forEach { track ->
                        if ((!losslessOnly || track.isLossless) && seenMessageIds.add(track.messageId)) {
                            tracks.add(track)
                            added++
                        }
                    }
                }
                if (!docExhausted) {
                    val page =
                        TelegramClient.fetchAudioPage(
                            chatId = chatId,
                            fromMessageId = docCursor,
                            limit = PAGE_FETCH_LIMIT,
                            filter = TdApi.SearchMessagesFilterDocument(),
                        )
                    docCursor = page.nextFromMessageId
                    if (page.nextFromMessageId == 0L) docExhausted = true
                    page.tracks.forEach { track ->
                        if ((!losslessOnly || track.isLossless) && seenMessageIds.add(track.messageId)) {
                            tracks.add(track)
                            added++
                        }
                    }
                }
            }
            tracks.sortWith(compareByDescending(TelegramTrack::dateSeconds).thenByDescending(TelegramTrack::messageId))
        } catch (e: Exception) {
            Toast
                .makeText(
                    context,
                    context.getString(R.string.telegram_error, e.message ?: "?"),
                    Toast.LENGTH_SHORT,
                ).show()
        } finally {
            loading = false
            initialLoadDone = true
        }
    }

    fun playFrom(track: TelegramTrack?) {
        val queueTracks = tracks.toList()
        if (queueTracks.isEmpty()) return
        val startIndex = track?.let(queueTracks::indexOf)?.coerceAtLeast(0) ?: 0
        coroutineScope.launch {
            val items =
                queueTracks.map { it.toMediaMetadata(channel?.title).toMediaItem() }
            playerConnection.playQueue(
                ListQueue(
                    title = channel?.title,
                    items = items,
                    startIndex = startIndex,
                ),
            )
        }
    }

    LaunchedEffect(chatId) {
        channel = TelegramClient.channelInfo(chatId)
        if (tracks.isEmpty()) {
            loadMore()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = channel?.title ?: stringResource(R.string.telegram_channel),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                },
                actions = {
                    if (tracks.isNotEmpty()) {
                        IconButton(onClick = { playFrom(null) }, onLongClick = {}) {
                            Icon(painterResource(R.drawable.play), contentDescription = stringResource(R.string.telegram_play_all))
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
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
            when {
                !initialLoadDone -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                tracks.isEmpty() -> {
                    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.telegram_no_tracks))
                    }
                }

                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(tracks, key = TelegramTrack::messageId) { track ->
                            TelegramTrackRow(
                                track = track,
                                onClick = { playFrom(track) },
                            )
                        }
                        if (!allExhausted) {
                            item(key = "load_more") {
                                Box(
                                    Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (loading) {
                                        CircularProgressIndicator()
                                    } else {
                                        Button(onClick = { coroutineScope.launch { loadMore() } }) {
                                            Text(stringResource(R.string.telegram_load_more))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TelegramTrackRow(
    track: TelegramTrack,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            val thumb = track.albumCoverMinithumbnail
            if (thumb != null) {
                AsyncImage(
                    model = thumb,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    painterResource(R.drawable.music_note),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = track.displayTitle,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val details =
                buildList {
                    track.performer?.let(::add)
                    fileExtension(track.fileName).takeIf(String::isNotEmpty)?.let { add(it.uppercase(Locale.US)) }
                    if (track.durationSeconds > 0) add(formatDuration(track.durationSeconds))
                    if (track.sizeBytes > 0) add(Formatter.formatShortFileSize(context, track.sizeBytes))
                }.joinToString(" • ")
            Text(
                text = details,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (track.isLossless) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.telegram_lossless_badge),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, secs)
}

private const val PAGE_TARGET = 25
private const val PAGE_FETCH_LIMIT = 50
private const val MAX_LOAD_ROUNDS = 6
