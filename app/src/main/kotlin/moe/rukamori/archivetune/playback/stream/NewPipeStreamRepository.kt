/*
 * ArchiveTune (2026)
 * © vossgraves — github.com/vossgraves
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback.stream

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.YouTubeClient
import moe.rukamori.archivetune.innertube.models.YouTubeClient.Companion.ANDROID_MUSIC
import moe.rukamori.archivetune.innertube.models.YouTubeClient.Companion.IOS
import moe.rukamori.archivetune.innertube.models.YouTubeClient.Companion.WEB_REMIX
import moe.rukamori.archivetune.utils.YTPlayerUtils
import timber.log.Timber

/**
 * The NewPipe tier of [ResolveAudioStreamUseCase]: asks for a player response as an anonymous
 * visitor and mints the stream URL through NewPipe's JavaScript player (the injected
 * [StreamUrlExtractor], bound to [NewPipeStreamUrlExtractor]), so it needs neither the external
 * yt-dlp plugin APK nor the signed-in session.
 *
 * It deliberately keeps none of the native tier's machinery: no cookie login, no client-health
 * bookkeeping and no per-client login recovery. That is the point of a second tier — a track the
 * signed-in pipeline refuses (403, age gate, a client that demands auth) is often served to a plain
 * visitor, and a tier that reused the failing session would fail with it. Format selection is the
 * shared quality-aware [YTPlayerUtils.selectAudioFormatCandidates], so both tiers rank the same
 * candidates the same way.
 */
@Singleton
class NewPipeStreamRepository
    @Inject
    constructor(
        private val extractor: StreamUrlExtractor,
    ) : AudioStreamRepository {

        override suspend fun resolve(request: AudioStreamRequest): ResolvedAudioStream {
            var failure: Throwable? = null
            for (client in ANONYMOUS_CLIENTS) {
                if (client.loginRequired) continue
                val outcome = runCatching { resolveWithClient(request, client) }
                outcome.getOrNull()?.let { return it }
                val error = outcome.exceptionOrNull() ?: continue
                if (error is CancellationException) throw error
                failure?.let(error::addSuppressed)
                failure = error
                Timber.tag(TAG).d(error, "NewPipe extraction failed with %s for %s", client.clientName, request.mediaId)
            }
            throw NewPipeExtractionException("NewPipe could not resolve a stream for ${request.mediaId}", failure)
        }

        private suspend fun resolveWithClient(
            request: AudioStreamRequest,
            client: YouTubeClient,
        ): ResolvedAudioStream? {
            val authState = request.authState
            val playerResponse =
                YouTube
                    .player(
                        videoId = request.mediaId,
                        playlistId = request.playlistId,
                        client = client,
                        signatureTimestamp =
                            if (client.useSignatureTimestamp) {
                                extractor.signatureTimestamp(request.mediaId).getOrNull()
                            } else {
                                null
                            },
                        setLogin = false,
                        authState = authState,
                    ).getOrThrow()
            val status = playerResponse.playabilityStatus.status
            if (status != "OK") {
                Timber.tag(TAG).d(
                    "%s returned %s for %s: %s",
                    client.clientName,
                    status,
                    request.mediaId,
                    playerResponse.playabilityStatus.reason.orEmpty(),
                )
                return null
            }

            val candidates =
                YTPlayerUtils.selectAudioFormatCandidates(
                    playerResponse = playerResponse,
                    audioQuality = request.quality,
                    networkMetered = request.networkMetered,
                )
            for (candidate in candidates) {
                val url =
                    extractor
                        .streamUrl(candidate, request.mediaId, client, authState)
                        .getOrNull()
                if (url == null) {
                    Timber.tag(TAG).d("NewPipe could not mint itag %s for %s", candidate.itag, request.mediaId)
                    continue
                }
                return ResolvedAudioStream(
                    url = url,
                    requestHeaders = emptyMap(),
                    formatId = candidate.itag,
                    mimeType = candidate.mimeType.substringBefore(';'),
                    codecs =
                        candidate.mimeType
                            .substringAfter("codecs=", "")
                            .removeSurrounding("\"")
                            .substringBefore("\""),
                    bitrate = candidate.bitrate,
                    sampleRate = candidate.audioSampleRate,
                    contentLength = candidate.contentLength ?: 0L,
                    expiresAtMs =
                        System.currentTimeMillis() +
                            (playerResponse.streamingData?.expiresInSeconds ?: DEFAULT_EXPIRES_IN_SECONDS) * 1000L,
                    authFingerprint = authState.streamCacheFingerprint,
                    source = StreamSource.NEWPIPE,
                    title = playerResponse.videoDetails?.title,
                    durationSeconds = playerResponse.videoDetails?.lengthSeconds?.toIntOrNull(),
                    thumbnailUrl = playerResponse.videoDetails?.thumbnail?.thumbnails?.lastOrNull()?.url,
                    loudnessDb = playerResponse.playerConfig?.audioConfig?.loudnessDb,
                    perceptualLoudnessDb = playerResponse.playerConfig?.audioConfig?.perceptualLoudnessDb,
                    playbackTrackingUrl = playerResponse.playbackTracking?.videostatsPlaybackUrl?.baseUrl,
                )
            }
            return null
        }

        private companion object {
            const val TAG = "NewPipeStreamRepository"

            /**
             * Visitor-capable clients only, most likely first. NewPipe's deobfuscation is what makes
             * a cipher-only response usable here, so this favours clients that still hand back
             * ciphered formats over the ones the native tier's login recovery needs.
             */
            val ANONYMOUS_CLIENTS = listOf(ANDROID_MUSIC, IOS, WEB_REMIX)

            /** YouTube's own streaming URLs age out in about six hours; used when the response omits it. */
            const val DEFAULT_EXPIRES_IN_SECONDS = 6 * 60 * 60
        }
    }

class NewPipeExtractionException(message: String, cause: Throwable? = null) : Exception(message, cause)
