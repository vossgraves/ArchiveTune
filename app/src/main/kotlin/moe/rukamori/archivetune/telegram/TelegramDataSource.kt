/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Media3 DataSource that streams a Telegram file through TDLib's partial-download cache.
 * open() kicks off (or re-targets) a download at the requested byte offset; read() serves bytes
 * out of the already-downloaded prefix via ReadFilePart, waiting for the download to catch up
 * when the player reads faster than the network. Seeking simply re-opens the source at the new
 * position, which TDLib translates into a new download offset — so FLAC seeking works without
 * waiting for the whole file.
 *
 * TDLib keeps the partial file in its own cache, so pause/resume and replays don't re-download.
 */

package moe.rukamori.archivetune.telegram

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.drinkless.tdlib.TdApi
import timber.log.Timber
import java.io.IOException

class TelegramDataSource(
    private val appContext: Context,
) : BaseDataSource(true) {
    private var currentUri: Uri? = null
    private var mediaId: TelegramMediaId? = null
    private var fileId: Int = 0
    private var fileSize: Long = 0
    private var position: Long = 0
    private var bytesRemaining: Long = C.LENGTH_UNSET.toLong()
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        val decoded =
            TelegramMediaId.decode(dataSpec.uri.toString())
                ?: throw IOException("Not a Telegram media id: ${dataSpec.uri}")
        // Await the session instead of testing isReady: on a cold start (process death, or the first
        // play after boot) TDLib is still loading its database, so a bare isReady check failed for
        // users who were in fact logged in. awaitReady starts the client if needed and returns false
        // only when there is genuinely no usable session.
        if (!runBlocking { TelegramClient.awaitReady(appContext) }) {
            throw IOException("Telegram is not logged in")
        }
        currentUri = dataSpec.uri
        mediaId = decoded
        transferInitializing(dataSpec)

        // Bounded: this runs on a Media3 loader thread, and an unbounded runBlocking would wedge
        // playback forever if TDLib never answers. resolveFile surfaces its own failure cause so a
        // missing/inaccessible message reports why rather than a bare "file unavailable".
        val file =
            try {
                runBlocking {
                    withTimeout(OPEN_TIMEOUT_MS) { resolveFile(decoded) }
                }
            } catch (e: IOException) {
                throw e
            } catch (e: Exception) {
                throw IOException("Failed to resolve Telegram file for ${dataSpec.uri}", e)
            } ?: throw IOException("Telegram file unavailable for ${dataSpec.uri}")
        fileId = file.id
        fileSize = if (file.size > 0) file.size else file.expectedSize
        position = dataSpec.position

        if (fileSize in 1 until position) {
            throw IOException("Position $position beyond Telegram file size $fileSize")
        }

        try {
            runBlocking {
                withTimeout(OPEN_TIMEOUT_MS) { ensureDownloading(position) }
            }
        } catch (e: Exception) {
            throw IOException("Failed to start Telegram download for ${dataSpec.uri}", e)
        }

        bytesRemaining =
            when {
                dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
                fileSize > 0 -> fileSize - position
                else -> C.LENGTH_UNSET.toLong()
            }
        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        var toRead = length.toLong()
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            toRead = minOf(toRead, bytesRemaining)
        }
        if (fileSize > 0) {
            val untilEof = fileSize - position
            if (untilEof <= 0) return C.RESULT_END_OF_INPUT
            toRead = minOf(toRead, untilEof)
        }

        val data =
            try {
                runBlocking {
                    withTimeout(READ_TIMEOUT_MS) {
                        awaitAndRead(position, toRead)
                    }
                }
            } catch (e: Exception) {
                throw IOException("Telegram stream read failed at $position", e)
            }

        // awaitAndRead only returns an empty array once the requested offset is at or past EOF, so
        // treat it as end of input. Returning 0 here (the old behaviour) told Media3 "no progress,
        // call me again", which spun the loader thread at 100% CPU whenever the size was still
        // unknown — draining the battery without ever making progress.
        if (data.isEmpty()) {
            return C.RESULT_END_OF_INPUT
        }

        System.arraycopy(data, 0, buffer, offset, data.size)
        position += data.size
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            bytesRemaining -= data.size
        }
        bytesTransferred(data.size)
        return data.size
    }

    override fun getUri(): Uri? = currentUri

    override fun close() {
        if (opened) {
            opened = false
            transferEnded()
            val id = fileId
            // Stop pulling data once the player lets go of the stream; the partial file stays in
            // TDLib's cache, so resuming later picks up where this left off.
            //
            // close() must not throw or stall: it runs on release/seek paths (often the player
            // thread), so a hung TDLib call here would freeze the UI and mask the real error. Bound
            // it tightly and swallow failures — the worst case is TDLib keeps prefetching briefly.
            runCatching {
                runBlocking {
                    withTimeout(CLOSE_TIMEOUT_MS) { TelegramClient.cancelDownload(id) }
                }
            }.onFailure { Timber.tag(TAG).w(it, "Failed to cancel Telegram download %d", id) }
        }
        currentUri = null
        mediaId = null
        fileId = 0
        fileSize = 0
        position = 0
        bytesRemaining = C.LENGTH_UNSET.toLong()
    }

    /**
     * Looks the file up by its stored TDLib file id, falling back to re-resolving the original
     * channel message when the id has gone stale (file ids don't survive TDLib database rebuilds).
     */
    private suspend fun resolveFile(decoded: TelegramMediaId): TdApi.File? {
        runCatching { TelegramClient.getFile(decoded.fileId) }
            .onSuccess { file ->
                if (decoded.fileUniqueId.isEmpty() || file.remote?.uniqueId == decoded.fileUniqueId) {
                    return file
                }
            }
        Timber.tag(TAG).i(
            "Stale Telegram file id %d, re-resolving message %d in chat %d",
            decoded.fileId,
            decoded.messageId,
            decoded.chatId,
        )
        return TelegramClient.resolveTrackFile(decoded.chatId, decoded.messageId)
    }

    private suspend fun ensureDownloading(offset: Long) {
        val file = TelegramClient.getFile(fileId)
        val local = file.local
        if (local.isDownloadingCompleted) return
        val covered =
            local.downloadOffset <= offset &&
                local.downloadOffset + local.downloadedPrefixSize > offset
        if (!local.isDownloadingActive || !covered) {
            TelegramClient.startDownload(fileId, offset)
        }
    }

    /**
     * Waits until [count] bytes at [offset] are present in TDLib's partial download, then reads
     * them. Returns fewer bytes near EOF; the overall wait is bounded by the caller's timeout.
     */
    private suspend fun awaitAndRead(
        offset: Long,
        count: Long,
    ): ByteArray {
        // Fast path: the bytes may already be present, in which case we never suspend at all.
        readIfAvailable(offset, count, TelegramClient.getFile(fileId))?.let { return it }
        if (!TelegramClient.getFile(fileId).local.isDownloadingActive) {
            TelegramClient.startDownload(fileId, offset)
        }

        // Driven by TDLib's UpdateFile rather than a fixed 150ms poll: a read resumes the moment the
        // needed bytes land instead of up to one poll interval later, and waiting costs no wakeups.
        // transformWhile completes the flow as soon as a read succeeds, which cancels the underlying
        // subscription — so nothing outlives this call. first() then yields that single result.
        return TelegramClient
            .fileUpdates
            .transformWhile { file ->
                if (file.id != fileId) return@transformWhile true
                val data = readIfAvailable(offset, count, file)
                if (data != null) {
                    emit(data)
                    false
                } else {
                    if (!file.local.isDownloadingActive && !file.local.isDownloadingCompleted) {
                        TelegramClient.startDownload(fileId, offset)
                    }
                    true
                }
            }.first()
    }

    /**
     * Returns the requested bytes when [file]'s downloaded prefix already covers them (or an empty
     * array at EOF), else null to keep waiting.
     */
    private suspend fun readIfAvailable(
        offset: Long,
        count: Long,
        file: TdApi.File,
    ): ByteArray? {
        val local = file.local
        if (file.size > 0) {
            fileSize = file.size
        }
        var wanted = count
        if (fileSize > 0) {
            val untilEof = fileSize - offset
            if (untilEof <= 0) return ByteArray(0)
            wanted = minOf(wanted, untilEof)
        }
        val end = offset + wanted
        val available =
            local.isDownloadingCompleted ||
                (
                    local.downloadOffset <= offset &&
                        local.downloadOffset + local.downloadedPrefixSize >= end
                )
        return if (available) TelegramClient.readFilePart(fileId, offset, wanted) else null
    }

    /**
     * @param context used only to start/restore the TDLib session on a cold start. Stored as an
     *   application Context, so the long-lived factory never retains an Activity or Service.
     */
    class Factory(
        context: Context,
    ) : DataSource.Factory {
        private val appContext: Context = context.applicationContext

        override fun createDataSource(): DataSource = TelegramDataSource(appContext)
    }

    companion object {
        private const val TAG = "TelegramDataSource"
        private const val READ_TIMEOUT_MS = 40_000L

        /**
         * Bounds open(): session restore on a cold start plus resolving the message. Generous because
         * a cold start legitimately takes several seconds, but finite so a dead connection surfaces
         * as a playback error instead of hanging the loader thread indefinitely.
         */
        private const val OPEN_TIMEOUT_MS = 45_000L

        /** Bounds close(). Short: releasing a stream must never block the caller noticeably. */
        private const val CLOSE_TIMEOUT_MS = 5_000L
    }
}
