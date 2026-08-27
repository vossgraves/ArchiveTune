/*
 * ArchiveTune (2026)
 * © ArchiveTuneFork contributors — github.com/vossgraves/ArchiveTune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package app.atf.media.playback.stream

import android.content.Context
import android.net.ConnectivityManager
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import app.atf.media.constants.PlayerStreamClient
import app.atf.media.utils.YTPlayerUtils
import app.atf.media.utils.retryWithoutPlaybackLoginContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NativeStreamRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : AudioStreamRepository {
        private val connectivityManager = checkNotNull(context.getSystemService<ConnectivityManager>())

        override suspend fun resolve(request: AudioStreamRequest): ResolvedAudioStream {
            val playbackData =
                context.retryWithoutPlaybackLoginContext {
                    when (request.purpose) {
                        StreamPurpose.DOWNLOAD ->
                            YTPlayerUtils.playerResponseForDownload(
                                videoId = request.mediaId,
                                playlistId = request.playlistId,
                                audioQuality = request.quality,
                                connectivityManager = connectivityManager,
                                networkMetered = request.networkMetered,
                            )

                        StreamPurpose.PLAYBACK ->
                            YTPlayerUtils.playerResponseForPlayback(
                                videoId = request.mediaId,
                                playlistId = request.playlistId,
                                audioQuality = request.quality,
                                connectivityManager = connectivityManager,
                                preferredStreamClient = PlayerStreamClient.WEB_REMIX,
                                networkMetered = request.networkMetered,
                            )
                    }
                }.getOrThrow()
            val format = playbackData.format
            val codecs =
                format.mimeType
                    .substringAfter("codecs=", "")
                    .removeSurrounding("\"")
                    .substringBefore("\"")
            return ResolvedAudioStream(
                url = playbackData.streamUrl,
                requestHeaders = emptyMap(),
                formatId = format.itag,
                mimeType = format.mimeType.substringBefore(';'),
                codecs = codecs,
                bitrate = format.bitrate,
                sampleRate = format.audioSampleRate,
                contentLength = format.contentLength ?: 0L,
                expiresAtMs = System.currentTimeMillis() + playbackData.streamExpiresInSeconds * 1000L,
                authFingerprint = playbackData.authFingerprint,
                source = StreamSource.NATIVE_INNERTUBE,
                title = playbackData.videoDetails?.title,
                durationSeconds = playbackData.videoDetails?.lengthSeconds?.toIntOrNull(),
                thumbnailUrl = playbackData.videoDetails?.thumbnail?.thumbnails?.lastOrNull()?.url,
                loudnessDb = playbackData.audioConfig?.loudnessDb,
                perceptualLoudnessDb = playbackData.audioConfig?.perceptualLoudnessDb,
                playbackTrackingUrl = playbackData.playbackTracking?.videostatsPlaybackUrl?.baseUrl,
            )
        }
    }
