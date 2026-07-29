/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import android.content.Context
import android.net.ConnectivityManager
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import android.net.Uri
import androidx.media3.database.DatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import moe.rukamori.archivetune.constants.AudioQuality
import moe.rukamori.archivetune.constants.AudioQualityKey
import moe.rukamori.archivetune.constants.DeezerAudioQuality
import moe.rukamori.archivetune.constants.DeezerAudioQualityKey
import moe.rukamori.archivetune.constants.DownloadSource
import moe.rukamori.archivetune.constants.DownloadSourceKey
import moe.rukamori.archivetune.constants.QobuzAudioQuality
import moe.rukamori.archivetune.constants.QobuzAudioQualityKey
import moe.rukamori.archivetune.constants.TidalAudioQuality
import moe.rukamori.archivetune.constants.TidalAudioQualityKey
import moe.rukamori.archivetune.constants.toFormatId
import moe.rukamori.archivetune.constants.toFormatName
import moe.rukamori.archivetune.deezer.DeezerAudioProvider
import moe.rukamori.archivetune.deezer.DeezerCrypto
import moe.rukamori.archivetune.deezer.DeezerDecryptingDataSource
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.FormatEntity
import moe.rukamori.archivetune.db.entities.SongEntity
import moe.rukamori.archivetune.di.DownloadCache
import moe.rukamori.archivetune.di.PlayerCache
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.qobuz.QobuzAudioProvider
import moe.rukamori.archivetune.tidal.TidalAudioProvider
import moe.rukamori.archivetune.utils.AuthScopedCacheValue
import moe.rukamori.archivetune.utils.StreamClientUtils
import moe.rukamori.archivetune.utils.YTPlayerUtils
import moe.rukamori.archivetune.utils.enumPreference
import moe.rukamori.archivetune.utils.isLowDataModeActive
import moe.rukamori.archivetune.utils.retryWithoutPlaybackLoginContext
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.time.LocalDateTime
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadUtil
    @Inject
    constructor(
        @ApplicationContext context: Context,
        val database: MusicDatabase,
        val databaseProvider: DatabaseProvider,
        @DownloadCache val downloadCache: Cache,
        @PlayerCache val playerCache: Cache,
    ) {
        private val connectivityManager = context.getSystemService<ConnectivityManager>()!!
        private val audioQuality by enumPreference(context, AudioQualityKey, AudioQuality.AUTO)
        private val downloadSource by enumPreference(context, DownloadSourceKey, DownloadSource.YOUTUBE_MUSIC)
        private val qobuzAudioQuality by enumPreference(context, QobuzAudioQualityKey, QobuzAudioQuality.FLAC)
        private val tidalAudioQuality by enumPreference(context, TidalAudioQualityKey, TidalAudioQuality.FLAC)
        private val deezerAudioQuality by enumPreference(context, DeezerAudioQualityKey, DeezerAudioQuality.FLAC)
        private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val songUrlCache = ConcurrentHashMap<String, AuthScopedCacheValue>()
        private val downloadExecutor = Executors.newFixedThreadPool(DEFAULT_MAX_PARALLEL_DOWNLOADS)

        private val mediaOkHttpClient: OkHttpClient by lazy {
            OkHttpClient
                .Builder()
                .proxy(YouTube.streamOkHttpProxy)
                .followRedirects(true)
                .followSslRedirects(true)
                .retryOnConnectionFailure(true)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .dispatcher(
                    okhttp3.Dispatcher().apply {
                        maxRequests = MAX_DOWNLOAD_HTTP_REQUESTS
                        maxRequestsPerHost = MAX_DOWNLOAD_HTTP_REQUESTS
                    },
                ).connectionPool(
                    ConnectionPool(
                        MAX_IDLE_DOWNLOAD_CONNECTIONS,
                        DOWNLOAD_CONNECTION_KEEP_ALIVE_MINUTES,
                        TimeUnit.MINUTES,
                    ),
                ).addInterceptor { chain ->
                    val request = chain.request()
                    val host = request.url.host
                    val isYouTubeMediaHost =
                        host.endsWith("googlevideo.com") ||
                            host.endsWith("googleusercontent.com") ||
                            host.endsWith("youtube.com") ||
                            host.endsWith("youtube-nocookie.com") ||
                            host.endsWith("ytimg.com")

                    if (!isYouTubeMediaHost) return@addInterceptor chain.proceed(request)

                    val requestProfile = StreamClientUtils.resolveRequestProfile(request.url)
                    chain.proceed(
                        StreamClientUtils
                            .applyRequestProfile(
                                request.newBuilder(),
                                requestProfile,
                            ).build(),
                    )
                }.build()
        }

        val downloads = MutableStateFlow<Map<String, Download>>(emptyMap())

        private val youtubeDataSourceFactory =
            ResolvingDataSource.Factory(
                CacheDataSource
                    .Factory()
                    .setCache(playerCache)
                    .setUpstreamDataSourceFactory(
                        OkHttpDataSource.Factory(
                            mediaOkHttpClient,
                        ),
                    ).setCacheWriteDataSinkFactory(
                        CacheDataSink.Factory().setCache(playerCache).setBufferSize(DOWNLOAD_WRITE_BUFFER_SIZE),
                    ),
            ) { dataSpec ->
                val mediaId = dataSpec.key ?: error("No media id")
                val length = if (dataSpec.length >= 0) dataSpec.length else 1
                if (playerCache.isCached(mediaId, dataSpec.position, length)) {
                    return@Factory dataSpec
                }
                // Reuse lossless bytes already sitting in the player cache from a Qobuz/Tidal
                // stream, so downloading a song you just streamed doesn't re-fetch it from
                // YouTube. Keys match MusicService's source-scoped cache keys.
                for (sourcePrefix in listOf("qobuz:", "tidal:")) {
                    val sourceKey = "$sourcePrefix$mediaId"
                    if (playerCache.isCached(sourceKey, dataSpec.position, length)) {
                        return@Factory dataSpec.buildUpon().setKey(sourceKey).build()
                    }
                }
                val lowDataModeActive = context.isLowDataModeActive()
                // Lossless sources are large; honour low-data mode by falling through to
                // YouTube's quality-capped stream instead.
                if (!lowDataModeActive) {
                    resolvePreferredDownloadDataSpec(dataSpec, mediaId)?.let { return@Factory it }
                }
                val requestedAudioQuality = resolveDownloadAudioQuality(lowDataModeActive)
                val streamCacheKey = buildSongUrlCacheKey(mediaId, requestedAudioQuality)
                val authFingerprint = YouTube.currentPlaybackAuthState().fingerprint
                songUrlCache[streamCacheKey]
                    ?.takeIf {
                        it.isValidFor(
                            authFingerprint = authFingerprint,
                            minimumRemainingMs = YTPlayerUtils.STREAM_URL_EXPIRY_SAFETY_MS,
                        )
                    }?.let {
                        return@Factory dataSpec.withUri(it.url.toUri())
                    }
                val playbackData =
                    runBlocking(Dispatchers.IO) {
                        context.retryWithoutPlaybackLoginContext {
                            YTPlayerUtils.playerResponseForDownload(
                                mediaId,
                                audioQuality = requestedAudioQuality,
                                connectivityManager = connectivityManager,
                                networkMetered = lowDataModeActive,
                            )
                        }
                    }.getOrThrow()
                persistPlaybackMetadata(mediaId, playbackData)

                val streamUrl = playbackData.streamUrl

                songUrlCache[streamCacheKey] =
                    AuthScopedCacheValue(
                        url = streamUrl,
                        expiresAtMs = System.currentTimeMillis() + (playbackData.streamExpiresInSeconds * 1000L),
                        authFingerprint = playbackData.authFingerprint,
                    )
                dataSpec.withUri(streamUrl.toUri())
            }

        // Route downloads by scheme: telegram:// tracks stream through TDLib (same as playback),
        // deezer:// through the Blowfish-decrypting source, everything else through the
        // YouTube-resolving factory above.
        private val telegramDataSourceFactory =
            moe.rukamori.archivetune.telegram.TelegramDataSource.Factory(context)

        // Deezer downloads deliberately reuse this DownloadManager rather than running their own
        // fetch loop, so the decrypting source sits inside the same pipeline as every other download
        // and inherits its cache, retry and progress handling. The bytes written to disk are already
        // decrypted, so a downloaded Deezer track plays back like any local file.
        private val deezerDataSourceFactory =
            DeezerDecryptingDataSource.Factory(OkHttpDataSource.Factory(mediaOkHttpClient))

        private val dataSourceFactory =
            DataSource.Factory {
                DownloadSchemeRoutingDataSource(
                    youtubeFactory = youtubeDataSourceFactory,
                    telegramFactory = telegramDataSourceFactory,
                    deezerFactory = deezerDataSourceFactory,
                )
            }

        val downloadNotificationHelper =
            DownloadNotificationHelper(context, ExoDownloadService.CHANNEL_ID)

        val downloadManager: DownloadManager =
            DownloadManager(
                context,
                databaseProvider,
                downloadCache,
                dataSourceFactory,
                downloadExecutor,
            ).apply {
                maxParallelDownloads = DEFAULT_MAX_PARALLEL_DOWNLOADS
                addListener(
                    object : DownloadManager.Listener {
                        override fun onDownloadChanged(
                            downloadManager: DownloadManager,
                            download: Download,
                            finalException: Exception?,
                        ) {
                            downloads.update { map ->
                                map.toMutableMap().apply {
                                    set(download.request.id, download)
                                }
                            }
                        }

                        override fun onDownloadRemoved(
                            downloadManager: DownloadManager,
                            download: Download,
                        ) {
                            downloads.update { map -> map - download.request.id }
                        }
                    },
                )
            }

        init {
            downloadScope.launch {
                val result = mutableMapOf<String, Download>()
                val cursor = downloadManager.downloadIndex.getDownloads()
                while (cursor.moveToNext()) {
                    result[cursor.download.request.id] = cursor.download
                }
                downloads.value = result
            }
            downloadScope.launch {
                var previousFingerprint: String? = null
                YouTube.authStateFlow
                    .map { it.fingerprint }
                    .distinctUntilChanged()
                    .collect { fingerprint ->
                        if (previousFingerprint != null && previousFingerprint != fingerprint) {
                            songUrlCache.clear()
                        }
                        previousFingerprint = fingerprint
                    }
            }
        }

        fun getDownload(songId: String): Flow<Download?> = downloads.map { it[songId] }

        private data class ResolvedStream(
            val uri: String,
            val mimeType: String,
            val codecs: String,
            val contentLength: Long?,
        )

        /**
         * Redirects a download to the user's preferred external source.
         *
         * For [DownloadSource.AUTO] each lossless provider is tried in turn and the first hit wins;
         * an explicit source resolves only itself. Returns null to mean "use YouTube Music", which
         * covers the default setting, a song we have no local row for, and every provider failing —
         * so a Qobuz outage degrades to a normal YouTube download instead of failing outright.
         *
         * Runs on the download thread, which the ResolvingDataSource already treats as blocking.
         */
        private fun resolvePreferredDownloadDataSpec(
            dataSpec: DataSpec,
            mediaId: String,
        ): DataSpec? {
            val chain = downloadSource.losslessChain()
            if (chain.isEmpty()) return null
            val song = database.getSongByIdBlocking(mediaId) ?: return null
            val queryTitle = song.song.title.takeIf { it.isNotBlank() } ?: return null
            val artists = song.artists.mapNotNull { it.name.takeIf(String::isNotBlank) }
            val album = song.album?.title?.takeIf { it.isNotBlank() }
            val durationMs = song.song.duration.takeIf { it > 0 }?.toLong()?.times(1000L)

            for (source in chain) {
                // Per-source runCatching: one dead provider must not abort the whole chain.
                val resolvedStream =
                    runCatching { resolveFromSource(source, mediaId, queryTitle, artists, album, durationMs) }
                        .getOrNull() ?: continue

                persistSourceFormatEntity(
                    mediaId = mediaId,
                    mimeType = resolvedStream.mimeType,
                    codecs = resolvedStream.codecs,
                    contentLength = resolvedStream.contentLength,
                )
                // Key by the source that actually resolved, never by `downloadSource` — under AUTO
                // that would file Qobuz and Tidal bytes under one "auto:" key and let them collide.
                return dataSpec
                    .buildUpon()
                    .setUri(resolvedStream.uri.toUri())
                    .setKey("${source.name.lowercase(Locale.US)}:$mediaId")
                    .build()
            }
            return null
        }

        private fun resolveFromSource(
            source: DownloadSource,
            mediaId: String,
            queryTitle: String,
            artists: List<String>,
            album: String?,
            durationMs: Long?,
        ): ResolvedStream? =
            when (source) {
                DownloadSource.QOBUZ ->
                    QobuzAudioProvider
                        .resolve(
                            QobuzAudioProvider.Query(mediaId, queryTitle, artists, album, durationMs),
                            qobuzAudioQuality.toFormatId(),
                        )?.let { ResolvedStream(it.uri, it.mimeType, it.codecs, it.contentLength) }
                DownloadSource.TIDAL ->
                    TidalAudioProvider
                        .resolve(
                            TidalAudioProvider.Query(mediaId, queryTitle, artists, album, null, durationMs),
                            audioQuality = tidalAudioQuality,
                        ).let { ResolvedStream(it.mediaUri, it.mimeType, it.codecs, it.contentLength) }
                // Yields a deezer:// URI, not a CDN URL — DownloadSchemeRoutingDataSource sends it
                // through the decrypting source so the bytes hitting disk are plain audio.
                DownloadSource.DEEZER ->
                    DeezerAudioProvider
                        .resolve(
                            DeezerAudioProvider.Query(mediaId, queryTitle, artists, album, durationMs),
                            deezerAudioQuality.toFormatName(),
                        )?.let { ResolvedStream(it.uri, it.mimeType, it.codecs, it.contentLength) }
                DownloadSource.AUTO, DownloadSource.YOUTUBE_MUSIC -> null
            }

        /**
         * Records the resolved stream's real MIME/codec in a [FormatEntity] so later exports and the
         * player's codec badge report FLAC instead of defaulting to MP3.
         */
        private fun persistSourceFormatEntity(
            mediaId: String,
            mimeType: String,
            codecs: String,
            contentLength: Long?,
        ) {
            val normalizedMime = mimeType.ifBlank { "audio/flac" }.substringBefore(";")
            downloadScope.launch {
                runCatching {
                    database.query {
                        upsert(
                            FormatEntity(
                                id = mediaId,
                                itag = 0,
                                mimeType = normalizedMime,
                                codecs = codecs,
                                bitrate = 0,
                                sampleRate = null,
                                contentLength = contentLength ?: 0L,
                                loudnessDb = null,
                                perceptualLoudnessDb = null,
                                playbackUrl = null,
                            ),
                        )
                    }
                }
            }
        }

        private fun resolveDownloadAudioQuality(lowDataModeActive: Boolean): AudioQuality =
            if (lowDataModeActive) AudioQuality.LOW else audioQuality

        private fun buildSongUrlCacheKey(
            mediaId: String,
            requestedAudioQuality: AudioQuality,
        ): String = "$mediaId:${requestedAudioQuality.name}"

        private fun persistPlaybackMetadata(
            mediaId: String,
            playbackData: YTPlayerUtils.PlaybackData,
        ) {
            downloadScope.launch {
                runCatching {
                    val format = playbackData.format
                    val contentLength = format.contentLength ?: 0L
                    val resolvedCodecs =
                        format.mimeType
                            .substringAfter("codecs=", "")
                            .removeSurrounding("\"")
                            .substringBefore("\"")

                    database.query {
                        upsert(
                            FormatEntity(
                                id = mediaId,
                                itag = format.itag,
                                mimeType = format.mimeType.split(";")[0],
                                codecs = resolvedCodecs,
                                bitrate = format.bitrate,
                                sampleRate = format.audioSampleRate,
                                contentLength = contentLength,
                                loudnessDb = playbackData.audioConfig?.loudnessDb,
                                perceptualLoudnessDb = playbackData.audioConfig?.perceptualLoudnessDb,
                                playbackUrl = playbackData.playbackTracking?.videostatsPlaybackUrl?.baseUrl,
                            ),
                        )

                        val now = LocalDateTime.now()
                        val existing = getSongByIdBlocking(mediaId)?.song
                        val resolvedThumbnailUrl =
                            playbackData.videoDetails
                                ?.thumbnail
                                ?.thumbnails
                                ?.lastOrNull()
                                ?.url
                                ?.takeIf { it.isNotBlank() }

                        val updatedSong =
                            if (existing != null) {
                                existing.copy(
                                    thumbnailUrl = existing.thumbnailUrl?.takeIf { it.isNotBlank() } ?: resolvedThumbnailUrl,
                                    dateDownload = existing.dateDownload ?: now,
                                )
                            } else {
                                SongEntity(
                                    id = mediaId,
                                    title = playbackData.videoDetails?.title ?: "Unknown",
                                    duration = playbackData.videoDetails?.lengthSeconds?.toIntOrNull() ?: 0,
                                    thumbnailUrl = resolvedThumbnailUrl,
                                    dateDownload = now,
                                )
                            }

                        upsert(updatedSong)
                    }
                }
            }
        }

        /**
         * Picks the download upstream by URI scheme: `telegram://` tracks go through TDLib (mirroring
         * playback's SchemeRoutingDataSource), everything else through the YouTube-resolving factory.
         */
        private class DownloadSchemeRoutingDataSource(
            private val youtubeFactory: DataSource.Factory,
            private val telegramFactory: DataSource.Factory,
            private val deezerFactory: DataSource.Factory,
        ) : DataSource {
            private val transferListeners = mutableListOf<TransferListener>()
            private var delegate: DataSource? = null

            override fun addTransferListener(transferListener: TransferListener) {
                transferListeners += transferListener
                delegate?.addTransferListener(transferListener)
            }

            override fun open(dataSpec: DataSpec): Long {
                val scheme = dataSpec.uri.scheme?.lowercase(java.util.Locale.US)
                val selected =
                    when (scheme) {
                        "telegram" -> telegramFactory
                        DeezerCrypto.SCHEME -> deezerFactory
                        else -> youtubeFactory
                    }
                val source = selected.createDataSource()
                transferListeners.forEach(source::addTransferListener)
                delegate = source
                return source.open(dataSpec)
            }

            override fun read(
                buffer: ByteArray,
                offset: Int,
                length: Int,
            ): Int = checkNotNull(delegate).read(buffer, offset, length)

            override fun getUri(): Uri? = delegate?.uri

            override fun getResponseHeaders(): Map<String, List<String>> = delegate?.responseHeaders ?: emptyMap()

            override fun close() {
                delegate?.close()
                delegate = null
            }
        }

        companion object {
            private const val DEFAULT_MAX_PARALLEL_DOWNLOADS = 16
            private const val MAX_IDLE_DOWNLOAD_CONNECTIONS = 32
            private const val MAX_DOWNLOAD_HTTP_REQUESTS = 64
            private const val DOWNLOAD_CONNECTION_KEEP_ALIVE_MINUTES = 5L
            private const val DOWNLOAD_WRITE_BUFFER_SIZE = 1024 * 1024
        }
    }
