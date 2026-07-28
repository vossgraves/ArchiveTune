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
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.AndroidArtwork
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Writes metadata and cover art into a downloaded audio file.
 *
 * WHY IT COPIES TO A TEMP FILE
 * ----------------------------
 * jaudiotagger operates on [java.io.File] with random access, because editing tags means rewriting
 * headers in place. A SAF `content://` Uri gives us only a stream, and under scoped storage there is
 * no real filesystem path we can hand to the library. So we copy out to cache, tag the copy, then
 * stream it back over the original document. The copy is always cleaned up, including on failure.
 */
internal object AudioFileTagger {
    private const val TAG = "AudioFileTagger"

    private val artworkClient: OkHttpClient by lazy {
        OkHttpClient
            .Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Takes [sourceLabel] rather than the whole [moe.rukamori.archivetune.audiosource.DirectStream]:
     * the label is all that was ever read from it, and depending on the type would tie tagging to the
     * download path. Cache export has no stream to hand over, only bytes that are already on disk.
     */
    fun tag(
        context: Context,
        target: Uri,
        fileName: String,
        request: LosslessDownloader.Request,
        sourceLabel: String,
    ) {
        // jaudiotagger picks its reader from the extension, so preserve it on the temp file.
        val extension = fileName.substringAfterLast('.', "flac")
        val temp = File.createTempFile("tagging_", ".$extension", context.cacheDir)
        try {
            context.contentResolver.openInputStream(target)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IllegalStateException("Could not read $fileName back for tagging")

            val audioFile = AudioFileIO.read(temp)
            val tag = audioFile.tagOrCreateAndSetDefault

            tag.setField(FieldKey.TITLE, request.title)
            request.artists.firstOrNull()?.takeIf { it.isNotBlank() }?.let {
                tag.setField(FieldKey.ARTIST, it)
            }
            // ALBUM_ARTIST keeps compilations grouped correctly in other players.
            request.artists.firstOrNull()?.takeIf { it.isNotBlank() }?.let {
                tag.setField(FieldKey.ALBUM_ARTIST, it)
            }
            request.album?.takeIf { it.isNotBlank() }?.let { tag.setField(FieldKey.ALBUM, it) }
            request.trackNumber?.takeIf { it > 0 }?.let { tag.setField(FieldKey.TRACK, it.toString()) }
            request.year?.takeIf { it.isNotBlank() }?.let { tag.setField(FieldKey.YEAR, it) }
            tag.setField(FieldKey.COMMENT, "Source: $sourceLabel")

            request.artworkUrl?.let { url ->
                // Best-effort: never fail the whole tagging pass because artwork could not be fetched.
                runCatching { embedArtwork(tag, url, context) }
                    .onFailure { Timber.tag(TAG).w(it, "artwork embed failed") }
            }

            audioFile.commit()

            context.contentResolver.openOutputStream(target, "wt").use { output ->
                if (output == null) throw IllegalStateException("Could not reopen $fileName to write tags")
                temp.inputStream().use { it.copyTo(output) }
                output.flush()
            }
            Timber.tag(TAG).i("tagged %s", fileName)
        } finally {
            if (!temp.delete()) temp.deleteOnExit()
        }
    }

    private fun embedArtwork(
        tag: org.jaudiotagger.tag.Tag,
        url: String,
        context: Context,
    ) {
        val response =
            artworkClient.newCall(Request.Builder().url(url).get().build()).execute()
        val bytes =
            response.use { res ->
                if (!res.isSuccessful) return
                res.body?.bytes() ?: return
            }
        if (bytes.isEmpty()) return

        // AndroidArtwork (from the Android fork) avoids the java.awt ImageIO path that would crash.
        val artworkFile = File.createTempFile("cover_", ".jpg", context.cacheDir)
        try {
            artworkFile.writeBytes(bytes)
            val artwork = AndroidArtwork.createArtworkFromFile(artworkFile)
            tag.deleteArtworkField()
            tag.setField(artwork)
        } finally {
            if (!artworkFile.delete()) artworkFile.deleteOnExit()
        }
    }
}
