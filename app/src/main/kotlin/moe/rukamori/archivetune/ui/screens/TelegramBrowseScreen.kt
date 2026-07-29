/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Telegram channel search. Tapping a result materialises the channel into a local playlist (its
 * audio files become songs) and opens the normal playlist screen, so channels behave exactly like
 * the app's other playlists — same tile, same rich screen, same search / radio / download / menus.
 */

package moe.rukamori.archivetune.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.TelegramLosslessOnlyKey
import moe.rukamori.archivetune.telegram.TelegramChannel
import moe.rukamori.archivetune.telegram.TelegramChannelSync
import moe.rukamori.archivetune.telegram.TelegramClient
import moe.rukamori.archivetune.telegram.TelegramJoinRequestPendingException
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.rememberPreference
import java.util.Locale

const val TELEGRAM_BROWSE_ROUTE = "telegram/browse"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramBrowseScreen(navController: NavController) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()
    val (losslessOnly) = rememberPreference(TelegramLosslessOnlyKey, true)

    var query by rememberSaveable { mutableStateOf("") }
    val results = remember { mutableStateListOf<TelegramChannel>() }
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var opening by remember { mutableStateOf(false) }

    fun search() {
        val trimmed = query.trim()
        if (trimmed.isEmpty() || searching) return
        searching = true
        coroutineScope.launch {
            val found =
                runCatching { TelegramClient.searchChannels(trimmed) }
                    .onFailure { e ->
                        // An approval-required invite link is a distinct outcome, not an error:
                        // reporting it as a generic failure looked identical to a bad link.
                        val message =
                            if (e is TelegramJoinRequestPendingException) {
                                context.getString(R.string.telegram_join_request_pending, e.chatTitle)
                            } else {
                                context.getString(R.string.telegram_error, e.message ?: "?")
                            }
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }.getOrDefault(emptyList())
            results.clear()
            results.addAll(found)
            searching = false
            searched = true
        }
    }

    fun openChannel(channel: TelegramChannel) {
        if (opening) return
        opening = true
        coroutineScope.launch {
            val playlistId =
                runCatching { TelegramChannelSync.ensurePlaylist(database, channel.chatId, channel.title) }
                    .getOrNull()
            opening = false
            if (playlistId == null) {
                Toast.makeText(context, R.string.telegram_error_generic, Toast.LENGTH_SHORT).show()
                return@launch
            }
            // Fill the playlist in the background; the playlist screen updates as songs arrive.
            TelegramChannelSync.syncAsync(database, channel.chatId, channel.title, losslessOnly)
            navController.navigate("local_playlist/$playlistId")
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
                                enabled = !opening,
                                onClick = { openChannel(channel) },
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
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
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
