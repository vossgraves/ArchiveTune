/*
 * YumaPlayer (2026) | Modified work by MuwMx
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify

import androidx.media3.common.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.queues.Queue
import moe.rukamori.archivetune.spotify.models.SpotifyTrack

class SpotifyTracksQueue(
    private val title: String? = null,
    private val initialTracks: List<SpotifyTrack> = emptyList(),
    private val startIndex: Int = 0,
    override val preloadItem: MediaMetadata? = null,
) : Queue {
    private val allTracks = initialTracks.toList()

    override suspend fun getInitialStatus(): Queue.Status =
        withContext(Dispatchers.IO) {
            if (allTracks.isEmpty()) {
                return@withContext Queue.Status(title = title, items = emptyList(), mediaItemIndex = 0)
            }

            val targetIndex = startIndex.coerceIn(allTracks.indices)
            val resolvedEntries = resolveTrackEntries(allTracks)
            val resolvedItems = resolvedEntries.map { it.second }
            Queue.Status(
                title = title,
                items = resolvedItems,
                mediaItemIndex = resolvedEntries.indexOfFirst { it.first >= targetIndex }
                    .takeIf { it >= 0 } ?: resolvedItems.lastIndex.coerceAtLeast(0),
            )
        }

    override fun hasNextPage(): Boolean = false

    override suspend fun nextPage(): List<MediaItem> = emptyList()

    private suspend fun resolveTrackEntries(tracks: List<SpotifyTrack>): List<Pair<Int, MediaItem>> =
        buildList {
            tracks.chunked(RESOLVE_BATCH_SIZE).forEachIndexed { chunkIndex, chunk ->
                val chunkOffset = chunkIndex * RESOLVE_BATCH_SIZE
                val resolvedChunk =
                    coroutineScope {
                        chunk
                            .mapIndexed { index, track ->
                                async {
                                    val mediaItem = preloadItem
                                        ?.takeIf { it.spotifyTrackId == track.id }
                                        ?.toMediaItem()
                                        ?: SpotifyPlaybackResolver.resolveToMediaItem(track)
                                    mediaItem?.let { chunkOffset + index to it }
                                }
                            }.awaitAll()
                            .filterNotNull()
                    }
                addAll(resolvedChunk)
            }
        }

    companion object {
        private const val RESOLVE_BATCH_SIZE = 4
    }
}
