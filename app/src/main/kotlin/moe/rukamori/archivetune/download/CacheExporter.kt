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
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheSpan
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.Song
import timber.log.Timber
import java.io.FileInputStream
import java.util.NavigableSet

/**
 * Copies already-downloaded songs out of the Media3 cache into a user-picked SAF folder.
 *
 * WHY THIS IS NOT PART OF THE SCREEN THAT TRIGGERS IT
 * ---------------------------------------------------
 * This used to live inline in CachePlaylistScreen on a `rememberCoroutineScope()`. Exporting a large
 * library takes minutes, and that scope dies with the composition — so navigating away mid-export
 * abandoned the run partway through, leaving the folder in a state the user could not distinguish
 * from a finished one. The work is owned here instead, on a process-lived scope, and progress is
 * published as state so any screen can observe a run it did not start.
 *
 * WHAT IT DOES NOT DO
 * -------------------
 * It does not re-download anything. The cache holds whatever was actually streamed, so a
 * YouTube-sourced track exports as Opus/AAC — only tracks fetched from a lossless source are
 * lossless on the way out. Use the download path for a guaranteed-lossless copy.
 */
object CacheExporter {
    private const val TAG = "CacheExporter"

    /** Characters no common filesystem accepts, replaced rather than dropped so names stay readable. */
    private val ILLEGAL_FILENAME_CHARS = Regex("[\\\\/:*?\"<>|]")

    /**
     * Scope owning in-flight exports. See the class note: not the caller's composition scope.
     */
    private val exportScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Tracks the running export so a second request cannot interleave writes into the same folder. */
    private var job: Job? = null

    private val _progress = MutableStateFlow<Progress?>(null)

    /** Progress of the current or most recent export; null before the first one. */
    val progress: StateFlow<Progress?> = _progress.asStateFlow()

    /**
     * @param exported files successfully written.
     * @param skipped songs with nothing in the cache — not an error, just not downloaded.
     * @param failed songs whose export was attempted and genuinely went wrong.
     */
    data class Progress(
        val total: Int,
        val exported: Int = 0,
        val skipped: Int = 0,
        val failed: Int = 0,
        val running: Boolean = true,
    ) {
        val processed: Int get() = exported + skipped + failed

        /**
         * Human-readable outcome, e.g. "Exported 12 songs, 3 not downloaded".
         *
         * Skipped and failed are reported separately and only when non-zero: lumping them together
         * would tell the user an export broke when in fact those tracks were simply never downloaded.
         */
        fun summary(context: Context): String =
            buildList {
                add(
                    context.resources.getQuantityString(
                        R.plurals.export_result_exported,
                        exported,
                        exported,
                    ),
                )
                if (skipped > 0) add(context.getString(R.string.export_result_skipped, skipped))
                if (failed > 0) add(context.getString(R.string.export_result_failed, failed))
            }.joinToString(", ")
    }

    /** True while an export is in flight, so callers can disable their trigger. */
    val isRunning: Boolean get() = job?.isActive == true

    /**
     * Starts exporting [songs] into [treeUri]. No-ops when an export is already running.
     *
     * @param embedTags writes title/artist/album and cover art into each exported file.
     */
    fun export(
        context: Context,
        cache: Cache,
        treeUri: Uri,
        songs: List<Song>,
        embedTags: Boolean,
    ) {
        // Two exports into one folder would race on identical filenames, and the progress state can
        // only describe one run. Dropping the second request is friendlier than corrupting both.
        if (isRunning) {
            Timber.tag(TAG).w("export already running, ignoring request for %d songs", songs.size)
            return
        }

        // Resolved once up front: it is the same folder for every song, and a bad tree uri should fail
        // the whole run immediately rather than be reported as N individual song failures.
        val folder = DocumentFile.fromTreeUri(context, treeUri)
        if (folder == null || !folder.canWrite()) {
            Timber.tag(TAG).w("export target is not writable: %s", treeUri)
            _progress.value = Progress(total = songs.size, failed = songs.size, running = false)
            return
        }

        _progress.value = Progress(total = songs.size)
        job =
            exportScope.launch {
                for (song in songs) {
                    // Cooperative cancellation: checked before each file so a cancelled export stops
                    // promptly instead of finishing the whole queue.
                    if (!isActive) break
                    val outcome =
                        runCatching {
                            exportOne(context, folder, cache, song, embedTags)
                        }
                    // A cancelled export must not be recorded as a per-song failure.
                    outcome.exceptionOrNull()?.let { if (it is CancellationException) throw it }

                    _progress.update { current ->
                        when {
                            outcome.isFailure -> {
                                Timber
                                    .tag(TAG)
                                    .w(outcome.exceptionOrNull(), "export failed for %s", song.title)
                                current.copy(failed = current.failed + 1)
                            }
                            outcome.getOrNull() == true -> current.copy(exported = current.exported + 1)
                            else -> current.copy(skipped = current.skipped + 1)
                        }
                    }
                }
                _progress.update { it.copy(running = false) }
                Timber.tag(TAG).i("export finished: %s", _progress.value)
            }
    }

    /** Cancels an in-flight export. Files already written are left in place. */
    fun cancel() {
        job?.cancel()
        job = null
        _progress.update { it.copy(running = false) }
    }

    /** Clears finished progress so the UI can stop showing a stale summary. */
    fun clearProgress() {
        if (!isRunning) _progress.value = null
    }

    /** Returns true when a file was written, false when the song had nothing cached to export. */
    private fun exportOne(
        context: Context,
        folder: DocumentFile,
        cache: Cache,
        song: Song,
        embedTags: Boolean,
    ): Boolean {
        val spans = cachedSpansFor(cache, song.id)
        if (spans.isEmpty()) return false

        // Spans arrive unordered; concatenating them out of order silently produces a corrupt file.
        val ordered = spans.sortedBy { it.position }
        val safeTitle =
            song.title.trim().replace(ILLEGAL_FILENAME_CHARS, "_").ifBlank { "audio" }
        // Sniff the real container rather than assuming: these caches hold Opus/AAC as well as FLAC,
        // and a name that contradicts the bytes makes some players reject the file outright.
        val container = detectContainer(ordered)
        val fileName = "$safeTitle.${container?.extension ?: "m4a"}"

        // DocumentFile.createFile, not DocumentsContract.createDocument: the latter needs a *document*
        // uri, and passing the tree uri straight through throws on most providers.
        val destUri =
            folder.createFile(container?.mimeType ?: "audio/mp4", fileName)?.uri
                ?: throw IllegalStateException("Could not create $fileName")

        context.contentResolver.openOutputStream(destUri, "w")?.use { output ->
            ordered.forEach { span ->
                FileInputStream(span.file).use { input -> input.copyTo(output) }
            }
            output.flush()
        } ?: throw IllegalStateException("Could not open $fileName for writing")

        if (embedTags) {
            // Best-effort: a correct audio file with no tags still beats reporting a failure.
            runCatching {
                AudioFileTagger.tag(
                    context = context,
                    target = destUri,
                    fileName = fileName,
                    request =
                        LosslessDownloader.Request(
                            mediaId = song.id,
                            title = song.title,
                            artists = song.artists.map { it.name },
                            album = song.song.albumName,
                            durationMs = song.song.duration.takeIf { it > 0 }?.times(1000L),
                            year = song.song.year?.toString(),
                            artworkUrl = song.song.thumbnailUrl,
                        ),
                    sourceLabel = "Cache export",
                )
            }.onFailure { Timber.tag(TAG).w(it, "tagging failed for %s", fileName) }
        }
        return true
    }

    /**
     * Sniffs the container from the span at position 0, the only one holding the magic bytes.
     *
     * Returns null when it cannot be determined, leaving the fallback to the caller.
     */
    internal fun detectContainer(orderedSpans: List<CacheSpan>): AudioContainer? {
        val first = orderedSpans.firstOrNull { it.position == 0L } ?: return null
        val header = ByteArray(AudioContainer.PROBE_BYTES)
        val read =
            runCatching { FileInputStream(first.file).use { it.read(header) } }.getOrDefault(0)
        if (read <= 0) return null
        return AudioContainer.detect(header.copyOf(read))
    }

    /**
     * Resolves cached spans for [songId].
     *
     * Tries the id directly, then scans all keys: entries written by different code paths can be
     * prefixed (e.g. `.../id`), so a direct miss does not mean the audio is absent.
     */
    internal fun cachedSpansFor(
        cache: Cache,
        songId: String,
    ): NavigableSet<CacheSpan> {
        val direct = cache.getCachedSpans(songId)
        if (direct.isNotEmpty()) return direct
        for (key in cache.keys) {
            if (key.substringAfterLast("/") == songId || key == songId) {
                val spans = cache.getCachedSpans(key)
                if (spans.isNotEmpty()) return spans
            }
        }
        return direct
    }

    /**
     * Updates non-null progress in place.
     *
     * Exists because every mutation here happens mid-run, where the state is guaranteed non-null;
     * inlining `?.let` at each call site obscured that invariant.
     */
    private fun MutableStateFlow<Progress?>.update(transform: (Progress) -> Progress) {
        val current = value ?: return
        value = transform(current)
    }
}
