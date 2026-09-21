package moe.rukamori.archivetune.mediainfo

import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

class ObserveMediaInfoUseCase @Inject constructor(private val repository: MediaInfoRepository) {
    operator fun invoke(videoId: String): Flow<MediaInfoData> =
        flow {
            val local =
                repository.observeLocal(videoId)
                    .map<LocalMediaInfo, MediaInfoState<LocalMediaInfo>> { MediaInfoState.Success(it) }
                    .catch { failure -> emit(failure.asMediaInfoError()) }
            val initialLocal = local.first()
            val isLocal = (initialLocal as? MediaInfoState.Success)?.data?.isLocal == true
            emitAll(
                combine(local.onStart { emit(initialLocal) }, remote(videoId, isLocal)) { details, states ->
                    MediaInfoData(details, states.metadata, states.statistics)
                },
            )
        }

    /**
     * Metadata and statistics are two views of the same watch page, so they come out of one read and
     * share a state: a read that fails cannot leave one of them reporting success.
     */
    private fun remote(
        videoId: String,
        isLocal: Boolean,
    ): Flow<RemoteMediaStates> =
        flow {
            if (isLocal) {
                // A file on the device has no watch page; the panel shows its own rows alone.
                emit(RemoteMediaStates(MediaInfoState.Empty, MediaInfoState.Empty))
                return@flow
            }
            emit(RemoteMediaStates(MediaInfoState.Loading, MediaInfoState.Loading))
            val info = repository.mediaInfo(videoId)
            emit(
                RemoteMediaStates(
                    metadata = info.metadata.stateOrEmpty(),
                    statistics = info.statistics.stateOrEmpty(),
                ),
            )
        }.catch { failure ->
            val error = failure.asMediaInfoError()
            emit(RemoteMediaStates(error, error))
        }
}

private data class RemoteMediaStates(
    val metadata: MediaInfoState<MediaInfoMetadata>,
    val statistics: MediaInfoState<MediaInfoStatistics>,
)

private fun MediaInfoMetadata.stateOrEmpty(): MediaInfoState<MediaInfoMetadata> =
    if (title.isNullOrBlank() && author.isNullOrBlank() && description.isNullOrBlank() && subscribers.isNullOrBlank()) {
        MediaInfoState.Empty
    } else {
        MediaInfoState.Success(this)
    }

private fun MediaInfoStatistics.stateOrEmpty(): MediaInfoState<MediaInfoStatistics> =
    if (views == null && likes == null && dislikes == null) {
        MediaInfoState.Empty
    } else {
        MediaInfoState.Success(this)
    }

internal fun Throwable.asMediaInfoError(): MediaInfoState.Error {
    if (this is CancellationException) throw this
    return MediaInfoState.Error(
        if (this is IOException) MediaInfoError.Network else MediaInfoError.Unavailable,
    )
}
