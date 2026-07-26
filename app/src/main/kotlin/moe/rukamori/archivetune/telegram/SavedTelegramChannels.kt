/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Persistence for the Telegram channels the user has opened, so they can be reached again from the
 * Library → Playlists screen. Stored as a JSON list in DataStore (SavedTelegramChannelsKey),
 * mirroring utils/SavedAccounts.kt. Only lightweight identity fields are stored — the avatar is
 * re-fetched live from TDLib when a tile is shown.
 */

package moe.rukamori.archivetune.telegram

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Immutable
@Serializable
data class SavedTelegramChannel(
    val chatId: Long,
    val title: String,
    val username: String? = null,
    val isBroadcastChannel: Boolean = true,
)

private const val MAX_SAVED_CHANNELS = 30

private val savedChannelJson = Json { ignoreUnknownKeys = true }

fun decodeSavedTelegramChannels(raw: String): List<SavedTelegramChannel> {
    if (raw.isBlank()) return emptyList()
    return runCatching { savedChannelJson.decodeFromString<List<SavedTelegramChannel>>(raw) }
        .getOrDefault(emptyList())
}

fun encodeSavedTelegramChannels(channels: List<SavedTelegramChannel>): String =
    savedChannelJson.encodeToString(channels)

/**
 * Returns [existing] with [channel] moved to the front (most-recent first), de-duplicated by
 * chatId and capped at [MAX_SAVED_CHANNELS].
 */
fun upsertSavedTelegramChannel(
    existing: List<SavedTelegramChannel>,
    channel: SavedTelegramChannel,
): List<SavedTelegramChannel> =
    (listOf(channel) + existing.filterNot { it.chatId == channel.chatId }).take(MAX_SAVED_CHANNELS)

fun removeSavedTelegramChannel(
    existing: List<SavedTelegramChannel>,
    chatId: Long,
): List<SavedTelegramChannel> = existing.filterNot { it.chatId == chatId }
