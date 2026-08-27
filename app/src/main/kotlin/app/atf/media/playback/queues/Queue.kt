/*
 * ArchiveTune (2026)
 * © ArchiveTuneFork contributors — github.com/vossgraves/ArchiveTune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package app.atf.media.playback.queues

import androidx.media3.common.MediaItem
import app.atf.media.extensions.ExtraIsMusicVideo
import app.atf.media.extensions.metadata
import app.atf.media.models.MediaMetadata

interface Queue {
    val preloadItem: MediaMetadata?

    suspend fun getInitialStatus(): Status

    fun shouldExpandToFullQueueWhenAutoLoadMoreDisabled(): Boolean = false

    fun hasNextPage(): Boolean

    suspend fun nextPage(): List<MediaItem>

    data class Status(
        val title: String?,
        val items: List<MediaItem>,
        val mediaItemIndex: Int,
        val position: Long = 0L,
    ) {
        fun filterExplicit(enabled: Boolean = true) =
            if (enabled) {
                filterItems { it.metadata?.explicit != true }
            } else {
                this
            }

        fun filterVideo(enabled: Boolean = true) =
            if (enabled) {
                filterItems { it.mediaMetadata.extras?.getBoolean(ExtraIsMusicVideo, false) != true }
            } else {
                this
            }

        fun filterBlockedArtists(blockedArtistIds: Set<String>) =
            if (blockedArtistIds.isEmpty()) {
                this
            } else {
                filterItems { !it.hasBlockedArtist(blockedArtistIds) }
            }

        private inline fun filterItems(keep: (MediaItem) -> Boolean): Status {
            if (items.isEmpty()) return this

            val currentIndex = mediaItemIndex.coerceIn(items.indices)
            var filteredIndex = 0
            val filteredItems =
                buildList(items.size) {
                    items.forEachIndexed { index, item ->
                        if (keep(item)) {
                            if (index < currentIndex) {
                                filteredIndex++
                            }
                            add(item)
                        }
                    }
                }

            if (filteredItems.isEmpty()) {
                return copy(items = emptyList(), mediaItemIndex = 0)
            }

            return copy(
                items = filteredItems,
                mediaItemIndex = filteredIndex.coerceIn(filteredItems.indices),
            )
        }
    }
}

fun List<MediaItem>.filterExplicit(enabled: Boolean = true) =
    if (enabled) {
        filterNot {
            it.metadata?.explicit == true
        }
    } else {
        this
    }

fun List<MediaItem>.filterVideo(enabled: Boolean = true) =
    if (enabled) {
        filterNot {
            it.mediaMetadata.extras?.getBoolean(ExtraIsMusicVideo, false) == true
        }
    } else {
        this
    }

fun List<MediaItem>.filterBlockedArtists(blockedArtistIds: Set<String>) =
    if (blockedArtistIds.isEmpty()) {
        this
    } else {
        filterNot { it.hasBlockedArtist(blockedArtistIds) }
    }

fun MediaItem.hasBlockedArtist(blockedArtistIds: Set<String>): Boolean =
    metadata?.artists?.any { artist -> artist.id != null && artist.id in blockedArtistIds } == true
