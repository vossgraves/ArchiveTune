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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.canvas.models.CanvasArtwork
import moe.rukamori.archivetune.constants.MyTopFilter
import moe.rukamori.archivetune.db.MusicDatabase
import javax.inject.Inject

@HiltViewModel
class TopPlaylistViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        database: MusicDatabase,
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
            // Waits for the first non-empty period, then resolves the canvas its header loops.
            // A canvas is decoration: it never holds up the chart itself.
            viewModelScope.launch(Dispatchers.IO) {
                val first = topSongs.first { it.isNotEmpty() }.firstOrNull() ?: return@launch
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
    }
