/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.telegram

import moe.rukamori.archivetune.models.MediaMetadata

/**
 * Bridges a Telegram channel track into the app's playback model. The media id is the encoded
 * telegram:// URI, so the player's scheme router hands the item straight to [TelegramDataSource].
 */
fun TelegramTrack.toMediaMetadata(channelTitle: String? = null): MediaMetadata =
    MediaMetadata(
        id = mediaId,
        title = displayTitle,
        artists =
            listOf(
                MediaMetadata.Artist(
                    id = null,
                    name = performer ?: channelTitle ?: "Telegram",
                ),
            ),
        duration = durationSeconds,
        thumbnailUrl =
            TelegramClient.cacheArtwork(
                uniqueKey = fileUniqueId.ifEmpty { "$chatId-$messageId" },
                data = albumCoverMinithumbnail,
            ),
    )
