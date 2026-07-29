/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.models

import java.io.Serializable

data class PersistQueue(
    val title: String?,
    val items: List<MediaMetadata>,
    val mediaItemIndex: Int,
    val position: Long,
    val queueType: QueueType = QueueType.LIST,
    val queueData: QueueData? = null,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Java serialization instantiates a *fresh* object for each `object` declaration it reads, so a
 * restored [PersistQueue] would carry copies that are not referentially equal to `QueueType.LIST` &
 * co. `object`s inherit identity equality, so every `queueType == QueueType.X` check downstream
 * silently failed after a process restart and the queue was rebuilt as a plain list. `readResolve`
 * makes the runtime hand back the canonical singletons instead.
 */
sealed class QueueType : Serializable {
    object LIST : QueueType() {
        private const val serialVersionUID = 1L

        private fun readResolve(): Any = LIST
    }

    object YOUTUBE : QueueType() {
        private const val serialVersionUID = 1L

        private fun readResolve(): Any = YOUTUBE
    }

    object YOUTUBE_ALBUM_RADIO : QueueType() {
        private const val serialVersionUID = 1L

        private fun readResolve(): Any = YOUTUBE_ALBUM_RADIO
    }

    object LOCAL_ALBUM_RADIO : QueueType() {
        private const val serialVersionUID = 1L

        private fun readResolve(): Any = LOCAL_ALBUM_RADIO
    }
}

sealed class QueueData : Serializable {
    data class YouTubeData(
        val videoId: String? = null,
        val playlistId: String? = null,
        val endpointParams: String? = null,
        val followAutomixPreview: Boolean = false,
    ) : QueueData() {
        companion object {
            private const val serialVersionUID = 2L
        }
    }

    data class YouTubeAlbumRadioData(
        val playlistId: String,
        val albumSongCount: Int = 0,
        val continuation: String? = null,
        val firstTimeLoaded: Boolean = false,
    ) : QueueData() {
        companion object {
            private const val serialVersionUID = 1L
        }
    }

    data class LocalAlbumRadioData(
        val albumId: String,
        val startIndex: Int = 0,
        val playlistId: String? = null,
        val continuation: String? = null,
        val firstTimeLoaded: Boolean = false,
    ) : QueueData() {
        companion object {
            private const val serialVersionUID = 1L
        }
    }
}
