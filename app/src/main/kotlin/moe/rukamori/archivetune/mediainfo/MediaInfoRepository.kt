/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.mediainfo

import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.innertube.YouTube

/** The panel's remote half: one watch page answers for both of these. */
data class RemoteMediaInfo(
    val metadata: MediaInfoMetadata,
    val statistics: MediaInfoStatistics,
)

class MediaInfoRepository @Inject constructor(private val database: MusicDatabase) {
    fun observeLocal(videoId: String): Flow<LocalMediaInfo> =
        combine(database.song(videoId), database.format(videoId)) { song, format ->
            LocalMediaInfo(
                title = song?.title,
                artists = song?.artists?.takeIf { it.isNotEmpty() }?.joinToString { it.name },
                artwork = song?.thumbnailUrl,
                isLocal = song?.song?.isLocal == true,
                format =
                    format?.let {
                        MediaInfoFormat(
                            itag = it.itag,
                            mimeType = it.mimeType,
                            codecs = it.codecs,
                            bitrate = it.bitrate,
                            sampleRate = it.sampleRate,
                            loudnessDb = it.loudnessDb,
                            contentLength = it.contentLength,
                        )
                    },
            )
        }.flowOn(Dispatchers.IO)

    // Upstream reads the two halves through YouTube.getMediaMetadata and YouTube.getMediaStatistics,
    // which rukamori/core declares separately; this fork's core exposes one getMediaInfo carrying the
    // same fields, so the panel reads it once and maps both halves out of that answer.
    suspend fun mediaInfo(videoId: String): RemoteMediaInfo =
        withContext(Dispatchers.IO) {
            val info = YouTube.getMediaInfo(videoId).getOrThrow()
            RemoteMediaInfo(
                metadata =
                    MediaInfoMetadata(
                        title = info.title,
                        author = info.author,
                        artwork = info.authorThumbnail,
                        description = info.description?.takeIf(String::isNotBlank),
                        subscribers = info.subscribers,
                    ),
                statistics =
                    MediaInfoStatistics(
                        views = info.viewCount,
                        likes = info.like,
                        dislikes = info.dislike,
                    ),
            )
        }
}
