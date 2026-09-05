/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.spotify

import androidx.media3.common.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.queues.Queue
import moe.rukamori.archivetune.spotify.models.SpotifyTrack

internal const val SPOTIFY_LIKED_SONGS_ID = "__spotify_liked_songs__"

/**
 * Spotify's AI DJ, which shows up on the home feed shaped like a playlist and is not one.
 *
 * There is no track list behind this id. The DJ is generated per listening session on Spotify's
 * side — the running order is chosen live and the spoken commentary between tracks is synthesised
 * as you go — and none of it exists until Spotify's own client asks for it. Fetching the "playlist"
 * returns nothing to play, and even if it returned a running order ArchiveTune would only have the
 * songs: the commentary, which is the entire point of the DJ, is audio only Spotify can produce.
 *
 * So the tile is not opened in the app. It hands off to Spotify, the way Spotify albums and artists
 * already do, instead of dead-ending on an empty playlist page.
 */
const val SPOTIFY_DJ_PLAYLIST_ID = "37i9dQZF1EYkqdzj48dyYq"

/** True for the DJ tile, whether it arrives as a bare id or a `spotify:playlist:…` uri. */
fun isSpotifyDj(idOrUri: String): Boolean = idOrUri.substringAfterLast(":") == SPOTIFY_DJ_PLAYLIST_ID

class SpotifyPlaylistQueue(
    private val playlistId: String,
    private val title: String? = null,
    private val initialTracks: List<SpotifyTrack> = emptyList(),
    private val startIndex: Int = 0,
    override val preloadItem: MediaMetadata? = null,
) : Queue {
    private val allTracks = mutableListOf<SpotifyTrack>()
    private var resolveOffset = 0
    private var apiFetchOffset = 0
    private var apiTotal = 0
    private var apiHasMore = true

    override suspend fun getInitialStatus(): Queue.Status =
        withContext(Dispatchers.IO) {
            if (initialTracks.isNotEmpty()) {
                allTracks += initialTracks
                apiTotal = initialTracks.size
                apiFetchOffset = apiTotal
                apiHasMore = false
            } else {
                fetchNextApiPage()
            }

            while (startIndex >= allTracks.size && apiHasMore) {
                fetchNextApiPage()
            }

            if (allTracks.isEmpty()) {
                return@withContext Queue.Status(title = title, items = emptyList(), mediaItemIndex = 0)
            }

            val targetIndex = startIndex.coerceIn(allTracks.indices)
            val resolvedEntries = resolveTrackEntries(allTracks)
            val resolvedItems = resolvedEntries.map { it.second }

            resolveOffset = allTracks.size
            if (resolvedItems.isEmpty()) {
                return@withContext Queue.Status(title = title, items = emptyList(), mediaItemIndex = 0)
            }

            Queue.Status(
                title = title,
                items = resolvedItems,
                mediaItemIndex =
                    resolvedEntries
                        .indexOfFirst { it.first >= targetIndex }
                        .takeIf { it >= 0 }
                        ?: resolvedItems.lastIndex,
            )
        }

    override fun hasNextPage(): Boolean = resolveOffset < allTracks.size || apiHasMore

    override suspend fun nextPage(): List<MediaItem> =
        withContext(Dispatchers.IO) {
            if (resolveOffset >= allTracks.size && apiHasMore) {
                fetchNextApiPage()
            }
            if (resolveOffset >= allTracks.size) return@withContext emptyList()

            val end = (resolveOffset + RESOLVE_BATCH_SIZE).coerceAtMost(allTracks.size)
            val batch = allTracks.subList(resolveOffset, end)
            resolveOffset = end
            resolveTracks(batch)
        }

    private suspend fun resolveTracks(tracks: List<SpotifyTrack>): List<MediaItem> = resolveTrackEntries(tracks).map { it.second }

    private suspend fun resolveTrackEntries(tracks: List<SpotifyTrack>): List<Pair<Int, MediaItem>> =
        buildList {
            tracks.chunked(RESOLVE_BATCH_SIZE).forEachIndexed { chunkIndex, chunk ->
                val chunkOffset = chunkIndex * RESOLVE_BATCH_SIZE
                val resolvedChunk =
                    coroutineScope {
                        chunk
                            .mapIndexed { index, track ->
                                async {
                                    SpotifyPlaybackResolver
                                        .resolveToMediaItem(track)
                                        ?.let { mediaItem -> chunkOffset + index to mediaItem }
                                }
                            }.awaitAll()
                            .filterNotNull()
                    }
                addAll(resolvedChunk)
            }
        }

    private suspend fun fetchNextApiPage() {
        if (!apiHasMore) return
        if (playlistId == SPOTIFY_LIKED_SONGS_ID) {
            val page = Spotify.likedSongs(limit = SPOTIFY_PAGE_SIZE, offset = apiFetchOffset).getOrThrow()
            apiTotal = page.total
            allTracks += page.items.mapNotNull { it.track.takeUnless(SpotifyTrack::isLocal) }
            apiFetchOffset += page.items.size
            apiHasMore = apiFetchOffset < apiTotal
            return
        }
        val result =
            Spotify
                .playlistTracks(
                    playlistId = playlistId,
                    limit = SPOTIFY_PAGE_SIZE,
                    offset = apiFetchOffset,
                ).getOrThrow()
        apiTotal = result.total
        val fetched = result.items.mapNotNull { it.track?.takeUnless(SpotifyTrack::isLocal) }
        allTracks += fetched
        apiFetchOffset += result.items.size
        apiHasMore = apiFetchOffset < apiTotal
    }

    companion object {
        private const val SPOTIFY_PAGE_SIZE = 50
        private const val RESOLVE_BATCH_SIZE = 20
    }
}
