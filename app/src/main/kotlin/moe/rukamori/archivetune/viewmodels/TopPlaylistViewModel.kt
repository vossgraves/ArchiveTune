/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 * Portions © vossgraves — github.com/vossgraves
 */

package moe.rukamori.archivetune.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import moe.rukamori.archivetune.canvas.models.CanvasArtwork
import moe.rukamori.archivetune.constants.MyTopFilter
import moe.rukamori.archivetune.db.MusicDatabase
import javax.inject.Inject

@HiltViewModel
class TopPlaylistViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val database: MusicDatabase,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        val top = savedStateHandle.get<String>("top")!!

        val topPeriod = MutableStateFlow(MyTopFilter.ALL_TIME)

        @OptIn(ExperimentalCoroutinesApi::class)
        val topSongs =
            topPeriod
                .flatMapLatest { period ->
                    database.mostPlayedSongs(period.toTimeMillis(), top.toInt())
                }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        private val _canvasArtwork = MutableStateFlow<CanvasArtwork?>(null)
        val canvasArtwork: StateFlow<CanvasArtwork?> = _canvasArtwork.asStateFlow()

        init {
            // Resolves the canvas its header loops, once per period. A canvas is decoration: it
            // never holds up the chart, and the period is what names the song, so switching the
            // period has to re-run the lookup rather than leave the header looping a song from a
            // chart that is no longer on screen.
            viewModelScope.launch(Dispatchers.IO) {
                topPeriod.collectLatest { period -> resolveCanvas(period) }
            }
        }

        private suspend fun resolveCanvas(period: MyTopFilter) {
            // The previous period's loop is a song from a chart the user has left, so it goes as
            // soon as the period does; a period with nothing playable resolves to no canvas at all.
            _canvasArtwork.value = null
            val first =
                withTimeoutOrNull(PLAYLIST_CANVAS_LOOKUP_TIMEOUT_MS) {
                    database.mostPlayedSongs(period.toTimeMillis(), top.toInt()).first { it.isNotEmpty() }
                }?.firstOrNull() ?: return
            _canvasArtwork.value =
                fetchPlaylistCanvasArtwork(
                    context = context,
                    firstSongId = first.song.id,
                    firstSongTitle = first.song.title,
                    firstSongArtist = first.artists.firstOrNull()?.name,
                    firstSongAlbumTitle = first.album?.title,
                )
        }
    }
