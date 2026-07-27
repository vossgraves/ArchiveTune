/*
 * Copyright (C) 2024 Rukamori
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 */
package moe.rukamori.archivetune.download

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.audiosource.DirectStream
import moe.rukamori.archivetune.audiosource.TitleMatch
import moe.rukamori.archivetune.constants.AudioSourceType
import moe.rukamori.archivetune.qobuz.QobuzAudioProvider
import okhttp3.OkHttpClient
// Aliased: this file's own public Request type would otherwise shadow OkHttp's.
import okhttp3.Request as HttpRequest
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Downloads a full lossless file (FLAC/ALAC) from a direct source to a user-picked SAF folder.
 *
 * WHY THIS IS SEPARATE FROM PLAYBACK
 * ----------------------------------
 * Streaming and downloading look similar but are mechanically different. Playback resolves a stream
 * and hands a [androidx.media3.datasource.DataSpec] to ExoPlayer, which reads it in ranged chunks on
 * a loader thread it may interrupt at any moment. A download has no player, no loader thread and no
 * ranged reads: the proxy's `download-music` endpoint returns a URL to a COMPLETE file, so the
 * correct implementation is a plain HTTP GET streamed to disk. Trying to reuse the ExoPlayer path
 * would mean fighting its interrupt/seek semantics for no benefit.
 *
 * This is the same approach SpotiFLAC uses, and it is why downloads can produce a real, taggable
 * `.flac` on disk rather than the transcoded audio the YouTube download path yields.
 */
object LosslessDownloader {
    private const val TAG = "LosslessDownloader"

    /** Proxy links carry a short-lived `etsp` expiry, so a stale URL is expected, not exceptional. */
    private const val MAX_ATTEMPTS = 3
    private const val BUFFER_BYTES = 64 * 1024

    private val client: OkHttpClient by lazy {
        OkHttpClient
            .Builder()
            // No overall call timeout: a hi-res FLAC is 20-100MB and may legitimately take minutes.
            // Bound only the idle phases, so a dead connection still fails fast.
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /** What the caller wants downloaded, independent of which source ends up serving it. */
    data class Request(
        val mediaId: String,
        val title: String,
        val artists: List<String>,
        val album: String?,
        val durationMs: Long?,
        val trackNumber: Int? = null,
        val year: String? = null,
        /** Remote artwork URL; fetched and embedded when tagging is enabled. */
        val artworkUrl: String? = null,
    )

    sealed interface Result {
        data class Success(
            val uri: Uri,
            val fileName: String,
            val bytes: Long,
            val sourceLabel: String,
        ) : Result

        /** No configured source could serve a full (non-preview) file for this track. */
        data object NotAvailable : Result

        data class Failed(val reason: String, val cause: Throwable? = null) : Result
    }

    /**
     * Resolves [request] against the direct sources and writes the file into [folderUri].
     *
     * Re-resolves and retries when the link has expired: these proxy URLs are signed with an `etsp`
     * timestamp, so a URL that sat in cache too long returns 403/410 rather than bytes. Retrying the
     * same dead URL would be pointless, so each attempt asks for a fresh one.
     */
    suspend fun download(
        context: Context,
        request: Request,
        folderUri: Uri,
        formatId: Int,
        embedTags: Boolean,
        onProgress: (bytesRead: Long, total: Long?) -> Unit = { _, _ -> },
    ): Result =
        withContext(Dispatchers.IO) {
            var lastFailure: String? = null

            repeat(MAX_ATTEMPTS) { attempt ->
                // forceFresh on retries: attempt 0 may reuse the provider's cached stream, but once a
                // URL has failed we must bypass that cache or we would just re-fetch the dead link.
                val stream =
                    resolve(request, formatId, forceFresh = attempt > 0)
                        ?: return@withContext if (lastFailure == null) {
                            Result.NotAvailable
                        } else {
                            Result.Failed(lastFailure!!)
                        }

                val attemptResult =
                    runCatching {
                        writeToFolder(context, request, stream, folderUri, embedTags, onProgress)
                    }

                attemptResult.getOrNull()?.let { return@withContext it }

                val error = attemptResult.exceptionOrNull()
                lastFailure = error?.message ?: "download failed"
                if (error is ExpiredLinkException) {
                    Timber.tag(TAG).w("link expired for \"%s\", re-resolving", request.title)
                } else {
                    Timber.tag(TAG).w(error, "attempt %d failed for \"%s\"", attempt + 1, request.title)
                }
            }

            Result.Failed(lastFailure ?: "download failed after $MAX_ATTEMPTS attempts")
        }

    /**
     * Asks the direct sources for a full-file URL, applying the same title-match gate playback uses
     * so a download can never silently save the wrong song.
     */
    private fun resolve(
        request: Request,
        formatId: Int,
        forceFresh: Boolean,
    ): DirectStream? {
        val query =
            QobuzAudioProvider.Query(
                mediaId = request.mediaId,
                title = request.title,
                artists = request.artists,
                album = request.album,
                durationMs = request.durationMs,
            )
        if (forceFresh) QobuzAudioProvider.invalidate(query, formatId)

        val stream = QobuzAudioProvider.resolve(query, formatId) ?: return null

        // evaluate() already short-circuits on trustedDirectId, so no need to pre-check it here.
        val match =
            TitleMatch.evaluate(
                wantedTitle = request.title,
                wantedArtists = request.artists,
                wantedAlbum = request.album,
                wantedDurationMs = request.durationMs,
                stream = stream,
            )
        if (!match.accepted) {
            Timber.tag(TAG).w(
                "rejected \"%s\" -> \"%s\" (%s)",
                request.title,
                stream.matchedTitle,
                match.reason,
            )
            return null
        }
        return stream
    }

    /** Thrown when the signed URL has expired, signalling "re-resolve" rather than "give up". */
    private class ExpiredLinkException(message: String) : IOException(message)

    private fun writeToFolder(
        context: Context,
        request: Request,
        stream: DirectStream,
        folderUri: Uri,
        embedTags: Boolean,
        onProgress: (Long, Long?) -> Unit,
    ): Result {
        val folder =
            DocumentFile.fromTreeUri(context, folderUri)
                ?: return Result.Failed("Download folder is no longer accessible")
        if (!folder.canWrite()) {
            return Result.Failed("No write permission for the selected folder. Pick it again.")
        }

        val extension = extensionFor(stream)
        val fileName = buildFileName(request, extension)

        val response =
            client.newCall(HttpRequest.Builder().url(stream.uri).get().build()).execute()

        response.use { res ->
            if (!res.isSuccessful) {
                // 403/410 on a signed proxy URL means the etsp window closed.
                if (res.code == 403 || res.code == 410) {
                    throw ExpiredLinkException("signed link expired (HTTP ${res.code})")
                }
                throw IOException("HTTP ${res.code} fetching ${stream.label}")
            }
            val body = res.body ?: throw IOException("Empty response body")
            val total = body.contentLength().takeIf { it > 0 }

            // Replace any previous partial/complete file with the same name so a retry cannot leave
            // "Song (1).flac" clutter behind.
            folder.findFile(fileName)?.delete()
            val target =
                folder.createFile(stream.mimeType.ifBlank { "audio/flac" }, fileName)
                    ?: throw IOException("Could not create $fileName in the selected folder")

            var written = 0L
            try {
                context.contentResolver.openOutputStream(target.uri, "w").use { out ->
                    if (out == null) throw IOException("Could not open $fileName for writing")
                    val buffer = ByteArray(BUFFER_BYTES)
                    body.byteStream().use { input ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            out.write(buffer, 0, read)
                            written += read
                            onProgress(written, total)
                        }
                    }
                    out.flush()
                }
            } catch (e: Throwable) {
                // Never leave a truncated file that looks like a real download.
                runCatching { target.delete() }
                throw e
            }

            if (written == 0L) {
                runCatching { target.delete() }
                throw IOException("Source returned no data")
            }
            // A "full" file that is suspiciously small is the classic 30s preview clip.
            if (total != null && written < total) {
                runCatching { target.delete() }
                throw IOException("Incomplete download ($written of $total bytes)")
            }

            if (embedTags) {
                // Tagging is best-effort: a valid audio file with no tags still beats no file.
                runCatching {
                    AudioFileTagger.tag(context, target.uri, fileName, request, stream)
                }.onFailure { Timber.tag(TAG).w(it, "tagging failed for %s", fileName) }
            }

            Timber.tag(TAG).i("saved %s (%d bytes) via %s", fileName, written, stream.label)
            return Result.Success(
                uri = target.uri,
                fileName = fileName,
                bytes = written,
                sourceLabel = stream.label,
            )
        }
    }

    /**
     * Picks the extension from the resolved stream rather than assuming one.
     *
     * The old export path hardcoded `.mp3`/`audio/mpeg`, which mislabelled every lossless file and
     * made other players mis-handle them.
     */
    private fun extensionFor(stream: DirectStream): String =
        when {
            stream.mimeType.contains("flac", true) -> "flac"
            stream.mimeType.contains("mp4", true) || stream.codecs.contains("alac", true) -> "m4a"
            stream.mimeType.contains("mpeg", true) -> "mp3"
            stream.uri.contains(".flac", true) -> "flac"
            else -> "flac"
        }

    private fun buildFileName(
        request: Request,
        extension: String,
    ): String {
        val artist = request.artists.firstOrNull()?.takeIf { it.isNotBlank() }
        val base = if (artist != null) "$artist - ${request.title}" else request.title
        return sanitize(base) + "." + extension
    }

    /**
     * Strips characters that are illegal on FAT32/exFAT SD cards (a common download target), and
     * caps the length so the name survives filesystems with a 255-byte limit.
     */
    private fun sanitize(raw: String): String {
        val cleaned =
            raw
                .replace(Regex("""[/\\:*?"<>|\u0000-\u001f]"""), "_")
                .replace(Regex("\\s+"), " ")
                .trim()
                .trimEnd('.')
        return cleaned.take(180).ifBlank { "track" }
    }
}
