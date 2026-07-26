/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * A horizontal band of the Telegram channels the user has opened, shown at the top of the Library
 * → Playlists screen. Tapping a tile reopens the channel's file browser. Backed by the saved-
 * channels list in DataStore (SavedTelegramChannelsKey).
 */

package moe.rukamori.archivetune.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.SavedTelegramChannelsKey
import moe.rukamori.archivetune.telegram.SavedTelegramChannel
import moe.rukamori.archivetune.telegram.decodeSavedTelegramChannels
import moe.rukamori.archivetune.ui.screens.telegramChannelRoute
import moe.rukamori.archivetune.utils.rememberPreference

@Composable
fun TelegramChannelsSection(navController: NavController) {
    val (savedJson) = rememberPreference(SavedTelegramChannelsKey, "")
    val channels = remember(savedJson) { decodeSavedTelegramChannels(savedJson) }
    if (channels.isEmpty()) return

    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        Text(
            text = stringResource(R.string.telegram_channels),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(channels, key = SavedTelegramChannel::chatId) { channel ->
                TelegramChannelChip(
                    channel = channel,
                    onClick = { navController.navigate(telegramChannelRoute(channel.chatId)) },
                )
            }
        }
    }
}

@Composable
private fun TelegramChannelChip(
    channel: SavedTelegramChannel,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier
                .width(72.dp)
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClick = onClick)
                .padding(vertical = 4.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.provider_telegram),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = channel.title,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
