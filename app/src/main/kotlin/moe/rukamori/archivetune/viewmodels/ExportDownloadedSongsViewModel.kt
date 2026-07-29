/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.datasource.cache.Cache
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.LosslessDownloadTagKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.di.DownloadCache
import moe.rukamori.archivetune.download.CacheExporter
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import javax.inject.Inject

/**
 * Backs the export picker: lists what is actually downloaded and hands a chosen subset to
 * [CacheExporter].
 *
 * Deliberately does NOT own an export engine. `CacheExporter.export` already takes an arbitrary
 * `List<Song>`, runs on a process-lived scope and publishes progress, so a selection screen only
 * needs to supply the subset — duplicating the copy/tag logic here would fork the tagging and
 * cancellation behaviour that the existing "export everything" path already has.
 */
@HiltViewModel
class ExportDownloadedSongsViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val database: MusicDatabase,
        @DownloadCache private val downloadCache: Cache,
    ) : ViewModel() {
        private val _songs = MutableStateFlow<List<Song>>(emptyList())

        /** Downloaded songs, newest download first. Empty until [refresh] completes. */
        val songs: StateFlow<List<Song>> = _songs.asStateFlow()

        private val _loading = MutableStateFlow(true)
        val loading: StateFlow<Boolean> = _loading.asStateFlow()

        init {
            refresh()
        }

        /**
         * Re-reads the download cache.
         *
         * Loaded once per call rather than polled on a timer: an export picker is a snapshot the user
         * is actively selecting within, and swapping rows underneath a part-made selection would
         * silently change what "export" is about to write.
         */
        fun refresh() {
            viewModelScope.launch {
                _loading.value = true
                _songs.value =
                    withContext(Dispatchers.IO) {
                        // Cache keys can be path-prefixed, so normalise before matching the db —
                        // same normalisation ExportDownloadsUseCase does for the export-all path.
                        val ids = downloadCache.keys.map { it.substringAfterLast("/") }.distinct()
                        if (ids.isEmpty()) {
                            emptyList()
                        } else {
                            database
                                .getSongsByIds(ids)
                                .sortedByDescending { it.song.dateDownload }
                        }
                    }
                _loading.value = false
            }
        }

        /**
         * Exports [selectedIds] into [treeUri]. Ignored when nothing is selected.
         *
         * Progress and the final summary come from [CacheExporter.progress], which the screen
         * observes directly, because the run outlives this ViewModel.
         */
        fun export(
            treeUri: Uri,
            selectedIds: Set<String>,
        ) {
            if (selectedIds.isEmpty() || CacheExporter.isRunning) return
            val selected = _songs.value.filter { it.song.id in selectedIds }
            if (selected.isEmpty()) return

            viewModelScope.launch {
                val embedTags = context.dataStore.get(LosslessDownloadTagKey, true)
                CacheExporter.export(
                    context = context,
                    cache = downloadCache,
                    treeUri = treeUri,
                    songs = selected,
                    embedTags = embedTags,
                )
            }
        }
    }
