/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Coil fetcher for Telegram artwork. Thumbnails are addressed by a `tgthumb://<fileId>` model
 * string so the full-resolution album cover / channel photo can be pulled from TDLib lazily and
 * only for images that are actually shown — instead of eagerly baking the tiny embedded
 * minithumbnail into every media item (which looked blurry once scaled up on the player).
 */

package moe.rukamori.archivetune.telegram

import coil3.ImageLoader
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import okio.Path.Companion.toPath

private const val TELEGRAM_THUMB_SCHEME = "tgthumb"

/** Builds the Coil model string for a Telegram thumbnail file id, or null when there is none. */
fun telegramThumbnailModel(fileId: Int): String? =
    if (fileId > 0) "$TELEGRAM_THUMB_SCHEME://$fileId" else null

class TelegramThumbnailFetcher(
    private val data: Uri,
    private val options: Options,
) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val fileId = data.authority?.toIntOrNull() ?: return null
        val path = TelegramClient.downloadFileBlocking(fileId) ?: return null
        return SourceFetchResult(
            source = ImageSource(file = path.toPath(), fileSystem = options.fileSystem),
            mimeType = null,
            dataSource = DataSource.DISK,
        )
    }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(
            data: Uri,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher? {
            if (data.scheme != TELEGRAM_THUMB_SCHEME) return null
            return TelegramThumbnailFetcher(data, options)
        }
    }
}
