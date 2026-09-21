/*
 * ArchiveTune (2026)
 * © vossgraves — github.com/vossgraves
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback.stream

import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.cipher.PlayerConfigRepository
import com.metrolist.innertubex.cipher.RemotePlayerConfigStore
import com.metrolist.innertubex.cipher.YouTubeCipherService
import com.metrolist.innertubex.extraction.AudioQuality as InnerTuneXAudioQuality
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.extraction.InnerTubeExtractor
import com.metrolist.innertubex.extraction.YtConfigParserImpl
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import moe.rukamori.archivetune.constants.AudioQuality
import moe.rukamori.archivetune.innertube.YouTube
import timber.log.Timber

/**
 * The InnerTuneX tier of [ResolveAudioStreamUseCase], and the first extractor tried after the native
 * path. InnerTuneX (`com.github.MetrolistGroup.innertubex`, GPL-3.0) is used as a library — its
 * `StreamExtractor` is adapted here, never forked — and brings its own InnerTube client, SABR/cipher
 * stack and client-fallback strategy, so this tier needs no `:core` involvement at all.
 *
 * The player consumes plain media URLs, so a segmented SABR result is refused rather than handed
 * on: it would fail at fetch time inside the player, which is worse than falling through to the
 * next tier.
 */
@Singleton
class InnerTuneXStreamRepository
    @Inject
    constructor() : AudioStreamRepository {

        private val httpClient by lazy { createHttpClient() }

        private val extractor: InnerTubeExtractor by lazy { createExtractor() }

        @Volatile
        private var prewarmed = false

        override suspend fun resolve(request: AudioStreamRequest): ResolvedAudioStream {
            prewarmOnce()
            val stream =
                extractor.extract(
                    videoId = request.mediaId,
                    hints = ContentHints(wantVideo = false),
                    audioQuality = request.quality.toInnerTuneXQuality(),
                ) ?: throw InnerTuneXExtractionException("InnerTuneX returned no stream for ${request.mediaId}")
            if (stream.sabrBootstrap != null) {
                throw InnerTuneXExtractionException(
                    "InnerTuneX returned a segmented SABR stream for ${request.mediaId}",
                )
            }
            return ResolvedAudioStream(
                url = stream.audioUrl,
                requestHeaders = stream.headers,
                formatId = stream.itag,
                mimeType = stream.mimeType?.substringBefore(';').orEmpty(),
                codecs = stream.codecs.orEmpty(),
                bitrate = stream.bitrate ?: 0,
                sampleRate = stream.sampleRate,
                contentLength = stream.contentLengthBytes ?: 0L,
                expiresAtMs = stream.expiresAt?.toEpochMilliseconds() ?: (System.currentTimeMillis() + DEFAULT_EXPIRES_IN_MS),
                authFingerprint = request.authState.streamCacheFingerprint,
                source = StreamSource.INNERTUBE_X,
                title = stream.mediaMetadata?.title,
                durationSeconds = stream.mediaMetadata?.durationSeconds?.toInt(),
                loudnessDb = stream.loudnessDb,
                perceptualLoudnessDb = stream.perceptualLoudnessDb,
            )
        }

        /** Warming the visitor data and player config is an optimisation: never let it fail the tier. */
        private suspend fun prewarmOnce() {
            if (prewarmed) return
            prewarmed = true
            runCatching { extractor.prewarm() }
                .onFailure { Timber.tag(TAG).d(it, "InnerTuneX prewarm failed") }
        }

        private fun createExtractor(): InnerTubeExtractor {
            val innerTube = InnerTube(httpClient = httpClient)
            val playerConfigStore =
                RemotePlayerConfigStore(
                    httpClient = httpClient,
                    repository = PlayerConfigRepository.disabled(),
                )
            return InnerTubeExtractor(
                configParser = YtConfigParserImpl(httpClient, innerTube, playerConfigStore),
                cipherService = YouTubeCipherService(httpClient, playerConfigStore),
                innerTube = innerTube,
            )
        }

        /** Mirrors [moe.rukamori.archivetune.innertube.InnerTube]'s client: the same JSON conventions. */
        @OptIn(ExperimentalSerializationApi::class)
        private fun createHttpClient() =
            HttpClient(OkHttp) {
                expectSuccess = true

                install(ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            explicitNulls = false
                            encodeDefaults = true
                        },
                    )
                }

                install(HttpTimeout) {
                    requestTimeoutMillis = REQUEST_TIMEOUT_MS
                    connectTimeoutMillis = CONNECT_TIMEOUT_MS
                    socketTimeoutMillis = REQUEST_TIMEOUT_MS
                }

                engine {
                    config {
                        proxy(YouTube.proxy)
                    }
                }
            }

        private fun AudioQuality.toInnerTuneXQuality(): InnerTuneXAudioQuality =
            when (this) {
                AudioQuality.AUTO -> InnerTuneXAudioQuality.AUTO
                AudioQuality.LOW -> InnerTuneXAudioQuality.LOW
                // InnerTuneX has no distinct highest tier; HIGH is its best lossy audio.
                AudioQuality.HIGH, AudioQuality.HIGHEST -> InnerTuneXAudioQuality.HIGH
            }

        private companion object {
            const val TAG = "InnerTuneXStreamRepository"
            const val CONNECT_TIMEOUT_MS = 15_000L
            const val REQUEST_TIMEOUT_MS = 30_000L

            /** Used when InnerTuneX reports no expiry, matching YouTube's usual streaming-URL lifetime. */
            const val DEFAULT_EXPIRES_IN_MS = 6 * 60 * 60 * 1000L
        }
    }

class InnerTuneXExtractionException(message: String, cause: Throwable? = null) : Exception(message, cause)
