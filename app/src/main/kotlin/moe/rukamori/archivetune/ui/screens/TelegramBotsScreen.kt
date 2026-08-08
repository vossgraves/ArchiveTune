/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * "Telegram bots" management screen: paste a bot's @username or t.me link to add it; tap an
 * existing bot to open the chat screen (where you paste song links). Long-press a bot to remove
 * it. Bots persist across launches via TelegramBotsKey.
 *
 * This screen sits behind the "Telegram bots" pill/entry that lives below the "Browse channels"
 * section in TelegramSettings — see the TelegramBot section in TelegramSettings.kt.
 */

package moe.rukamori.archivetune.ui.screens

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
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.TelegramBotForwardToChannelKey
import moe.rukamori.archivetune.constants.TelegramBotsKey
import moe.rukamori.archivetune.telegram.TelegramBot
import moe.rukamori.archivetune.telegram.TelegramBotClient
import moe.rukamori.archivetune.telegram.TelegramBotCodec
import moe.rukamori.archivetune.telegram.TelegramClient
import moe.rukamori.archivetune.telegram.parseBotUsername
import moe.rukamori.archivetune.ui.component.FrostedTopAppBar
import moe.rukamori.archivetune.ui.component.IconButton as ATIconButton
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.rememberPreference
import org.drinkless.tdlib.TdApi
import java.util.UUID

const val TELEGRAM_BOTS_ROUTE = "telegram/bots"
const val TELEGRAM_BOT_CHAT_ROUTE_BASE = "telegram/bot"

/** Routes a single bot chat screen — `telegram/bot/<botId>`. */
fun telegramBotChatRoute(botId: String) = "$TELEGRAM_BOT_CHAT_ROUTE_BASE/$botId"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramBotsScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val (rawBots, onBotsChange) = rememberPreference(TelegramBotsKey, "")
    val (forwardToChannel, onForwardToChannelChange) = rememberPreference(
        TelegramBotForwardToChannelKey,
        true,
    )

    val authState by TelegramClient.authState.collectAsState()
    val isReady = authState is moe.rukamori.archivetune.telegram.TelegramAuthState.Ready

    val bots = remember(rawBots) { TelegramBotCodec.decode(rawBots) }
    val botsState = remember { mutableStateListOf<TelegramBot>() }
    LaunchedEffect(bots) {
        botsState.clear()
        botsState.addAll(bots)
    }

    var newBotInput by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var pendingRemove by remember { mutableStateOf<TelegramBot?>(null) }

    fun persistBots(updated: List<TelegramBot>) {
        botsState.clear()
        botsState.addAll(updated)
        onBotsChange(TelegramBotCodec.encode(updated))
    }

    fun addBot() {
        val username = parseBotUsername(newBotInput) ?: run {
            Toast.makeText(context, R.string.telegram_bots_add_invalid, Toast.LENGTH_SHORT).show()
            return
        }
        if (botsState.any { it.username == username }) {
            Toast.makeText(
                context,
                context.getString(R.string.telegram_bots_already_added, username),
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        adding = true
        coroutineScope.launch {
            val chat = TelegramBotClient.resolveBot(username)
            adding = false
            if (chat == null) {
                Toast.makeText(context, R.string.telegram_bots_resolve_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val title = runCatching {
                val type = chat.type as TdApi.ChatTypePrivate
                TelegramClient.send(TdApi.GetUser(type.userId)).firstName
            }.getOrNull()?.takeIf { it.isNotBlank() } ?: "@$username"
            val bot = TelegramBot(
                id = UUID.randomUUID().toString(),
                username = username,
                chatId = chat.id,
                title = title,
                addedAtMs = System.currentTimeMillis(),
            )
            persistBots(botsState + bot)
            newBotInput = ""
        }
    }

    if (pendingRemove != null) {
        val removing = pendingRemove!!
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text(stringResource(R.string.telegram_bots_remove)) },
            text = {
                Text(stringResource(R.string.telegram_bots_remove_confirm, removing.username))
            },
            confirmButton = {
                TextButton(onClick = {
                    persistBots(botsState.filter { it.id != removing.id })
                    pendingRemove = null
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemove = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            FrostedTopAppBar(
                titleRes = R.string.telegram_bots_title,
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
                    value = newBotInput,
                    onValueChange = { newBotInput = it },
                    placeholder = { Text(stringResource(R.string.telegram_bots_add_hint)) },
                    singleLine = true,
                    enabled = isReady && !adding,
                    trailingIcon = {
                        IconButton(onClick = ::addBot, enabled = isReady && !adding && newBotInput.isNotBlank()) {
                            if (adding) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(painterResource(R.drawable.add), contentDescription = null)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Bot list / empty state. Each branch takes the full remaining vertical space via
            // Modifier.weight(1f) so the "Auto-forward to my channel" pill below sits at a STABLE
            // position regardless of whether the list is empty or contains bots — this is what the
            // user asked for ("when I find a bot the auto forward pill shifts down automatically.
            // it shouldn't"). Without weight(1f) on the empty branch, the empty-state Box would
            // collapse to its content height and the pill would jump down when a bot is added.
            if (!isReady) {
                Box(
                    Modifier.fillMaxWidth().weight(1f).padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.telegram_login_required))
                }
            } else if (botsState.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().weight(1f).padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.telegram_bots_empty))
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    items(botsState, key = TelegramBot::id) { bot ->
                        BotRow(
                            bot = bot,
                            onClick = { navController.navigate(telegramBotChatRoute(bot.id)) },
                            onRemove = { pendingRemove = bot },
                        )
                    }
                }
            }

            SwitchPreference(
                title = { Text(stringResource(R.string.telegram_bot_forward_to_channel)) },
                description = stringResource(R.string.telegram_bot_forward_to_channel_summary),
                icon = { Icon(painterResource(R.drawable.solar_chat_round_linear), contentDescription = null) },
                checked = forwardToChannel,
                onCheckedChange = onForwardToChannelChange,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun BotRow(
    bot: TelegramBot,
    onClick: () -> Unit,
    onRemove: () -> Unit,
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
            Icon(
                painter = painterResource(R.drawable.solar_chat_round_linear),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = bot.title.ifBlank { bot.displayHandle },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = bot.displayHandle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        ATIconButton(onClick = onRemove, onLongClick = {}) {
            Icon(painterResource(R.drawable.solar_trash_bin_minimalistic_linear), contentDescription = null)
        }
    }
}
