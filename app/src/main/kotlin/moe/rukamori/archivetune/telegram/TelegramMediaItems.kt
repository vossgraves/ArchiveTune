/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.telegram

import moe.rukamori.archivetune.db.entities.FormatEntity
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
        // Prefer the full-resolution album cover (fetched lazily from TDLib via the Coil
        // tgthumb:// fetcher); fall back to the tiny embedded minithumbnail when there is none.
        thumbnailUrl =
            telegramThumbnailModel(thumbnailFileId)
                ?: TelegramClient.cacheArtwork(
                    uniqueKey = fileUniqueId.ifEmpty { "$chatId-$messageId" },
                    data = albumCoverMinithumbnail,
                ),
    )

/**
 * Seed a [FormatEntity] for a Telegram track so the player-details / Nerd Stats screen shows file
 * size, container and an approximate bitrate immediately instead of hanging on "Loading format".
 * Sample rate is left null here and refined from the downloaded file during playback (see the
 * telegram branch of PlayerConnection's metadata-extraction job). itag = -1 marks a non-YouTube
 * source, matching the local-media convention.
 */
fun TelegramTrack.toFormatEntity(): FormatEntity {
    val extension = fileExtension(fileName)
    val mime =
        mimeType.substringBefore(";").ifBlank {
            when (extension) {
                "flac" -> "audio/flac"
                "wav", "wave" -> "audio/wav"
                "aiff", "aif" -> "audio/aiff"
                "alac" -> "audio/alac"
                "ape" -> "audio/x-ape"
                "wv" -> "audio/x-wavpack"
                "mp3" -> "audio/mpeg"
                "m4a" -> "audio/mp4"
                "ogg", "oga" -> "audio/ogg"
                "opus" -> "audio/opus"
                else -> "audio/*"
            }
        }
    val averageBitrate =
        if (durationSeconds > 0 && sizeBytes > 0) {
            (sizeBytes * 8 / durationSeconds).toInt().coerceAtLeast(0)
        } else {
            0
        }
    return FormatEntity(
        id = mediaId,
        itag = -1,
        mimeType = mime,
        codecs = extension,
        bitrate = averageBitrate,
        sampleRate = null,
        contentLength = sizeBytes,
        loudnessDb = null,
        perceptualLoudnessDb = null,
        playbackUrl = null,
    )
}
