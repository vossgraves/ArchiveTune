/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:Suppress("DEPRECATION")

package moe.rukamori.archivetune.playback

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.database.SQLException
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaCodecList
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.PowerManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.ParserException
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.EVENT_POSITION_DISCONTINUITY
import androidx.media3.common.Player.EVENT_TIMELINE_CHANGED
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.common.Player.STATE_IDLE
import androidx.media3.common.Timeline
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.DrmSessionManager
import androidx.media3.exoplayer.drm.ExoMediaDrm
import androidx.media3.exoplayer.drm.MediaDrmCallback
import androidx.media3.exoplayer.drm.MediaDrmCallbackException
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.PlaybackStats
import androidx.media3.exoplayer.analytics.PlaybackStatsListener
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaController
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withTimeout
import moe.rukamori.archivetune.MainActivity
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.cast.CastMediaItemResolver
import moe.rukamori.archivetune.cast.CastPlaybackRepository
import moe.rukamori.archivetune.cast.CastPlaybackRepositoryLocator
import moe.rukamori.archivetune.cast.CastScreenState
import moe.rukamori.archivetune.constants.AudioNormalizationKey
import moe.rukamori.archivetune.constants.DefaultMetadataSourceKey
import moe.rukamori.archivetune.constants.MetadataSource
import moe.rukamori.archivetune.constants.AudioOffload
import moe.rukamori.archivetune.constants.AudioQuality
import moe.rukamori.archivetune.constants.AudioQualityKey
import moe.rukamori.archivetune.constants.AutoDownloadOnLikeKey
import moe.rukamori.archivetune.constants.AutoChoosePlaybackClientKey
import moe.rukamori.archivetune.constants.AutoLoadMoreKey
import moe.rukamori.archivetune.constants.AutoSkipNextOnErrorKey
import moe.rukamori.archivetune.constants.AutoStartOnBluetoothKey
import moe.rukamori.archivetune.constants.CrossfadeDurationKey
import moe.rukamori.archivetune.constants.CrossfadeEnabledKey
import moe.rukamori.archivetune.constants.CrossfadeGaplessKey
import moe.rukamori.archivetune.constants.DeviceMutePlaybackRecoveryVolumeKey
import moe.rukamori.archivetune.constants.DiscordShowWhenPausedKey
import moe.rukamori.archivetune.constants.DiscordTokenKey
import moe.rukamori.archivetune.constants.EnableDiscordRPCKey
import moe.rukamori.archivetune.constants.EnableLastFMScrobblingKey
import moe.rukamori.archivetune.constants.EqualizerAutoHeadroomEnabledKey
import moe.rukamori.archivetune.constants.EqualizerBandLevelsMbKey
import moe.rukamori.archivetune.constants.EqualizerBassBoostEnabledKey
import moe.rukamori.archivetune.constants.EqualizerBassBoostStrengthKey
import moe.rukamori.archivetune.constants.EqualizerEnabledKey
import moe.rukamori.archivetune.constants.EqualizerOutputGainEnabledKey
import moe.rukamori.archivetune.constants.EqualizerOutputGainMbKey
import moe.rukamori.archivetune.constants.EqualizerSelectedProfileIdKey
import moe.rukamori.archivetune.constants.EqualizerVirtualizerEnabledKey
import moe.rukamori.archivetune.constants.EqualizerVirtualizerStrengthKey
import moe.rukamori.archivetune.constants.HISTORY_DURATION_DEFAULT
import moe.rukamori.archivetune.constants.HISTORY_DURATION_MAX
import moe.rukamori.archivetune.constants.HISTORY_DURATION_MIN
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.constants.HideVideoKey
import moe.rukamori.archivetune.constants.HistoryDuration
import moe.rukamori.archivetune.constants.LastFMSessionKey
import moe.rukamori.archivetune.constants.LastFMUseNowPlaying
import moe.rukamori.archivetune.constants.ListenBrainzEnabledKey
import moe.rukamori.archivetune.constants.ListenBrainzTokenKey
import moe.rukamori.archivetune.constants.MaxSongCacheSizeKey
import moe.rukamori.archivetune.constants.MediaSessionConstants.CommandToggleLike
import moe.rukamori.archivetune.constants.MediaSessionConstants.CommandToggleRepeatMode
import moe.rukamori.archivetune.constants.MediaSessionConstants.CommandToggleShuffle
import moe.rukamori.archivetune.constants.MediaSessionConstants.CommandToggleStartRadio
import moe.rukamori.archivetune.constants.PauseListenHistoryKey
import moe.rukamori.archivetune.constants.SyncPlaybackToYouTubeHistoryKey
import moe.rukamori.archivetune.constants.PauseOnDeviceMuteKey
import moe.rukamori.archivetune.constants.PermanentShuffleKey
import moe.rukamori.archivetune.constants.PersistentQueueKey
import moe.rukamori.archivetune.constants.PlayerStreamClient
import moe.rukamori.archivetune.constants.PlayerStreamClientKey
import moe.rukamori.archivetune.constants.TidalAudioQuality
import moe.rukamori.archivetune.constants.AppleMusicQuality
import moe.rukamori.archivetune.constants.AppleMusicQualityKey
import moe.rukamori.archivetune.constants.TidalAudioQualityKey
import moe.rukamori.archivetune.constants.TidalEnabledKey
import moe.rukamori.archivetune.constants.AppleMusicSourceEnabledKey
import moe.rukamori.archivetune.constants.TidalInstancesKey
import moe.rukamori.archivetune.constants.AudioSourceType
import moe.rukamori.archivetune.constants.AudioSourceOrderKey
import moe.rukamori.archivetune.constants.TidalAccountFirstKey
import moe.rukamori.archivetune.constants.TidalAccessTokenKey
import moe.rukamori.archivetune.constants.TidalLastProbeTrackKey
import moe.rukamori.archivetune.constants.TidalRefreshTokenKey
import moe.rukamori.archivetune.constants.TidalTokenExpiryKey
import moe.rukamori.archivetune.constants.TidalAuthFlowKey
import moe.rukamori.archivetune.constants.TidalCountryCodeKey
import moe.rukamori.archivetune.constants.TidalUserIdKey
import moe.rukamori.archivetune.constants.TidalNeedsReloginKey
import moe.rukamori.archivetune.constants.QobuzEnabledKey
import moe.rukamori.archivetune.constants.QobuzBackupEnabledKey
import moe.rukamori.archivetune.constants.QobuzInstancesKey
import moe.rukamori.archivetune.constants.QobuzAudioQuality
import moe.rukamori.archivetune.constants.QobuzAudioQualityKey
import moe.rukamori.archivetune.constants.QobuzLastProbeTrackKey
import moe.rukamori.archivetune.constants.QobuzTokensKey
import moe.rukamori.archivetune.constants.toFormatId
import moe.rukamori.archivetune.constants.DeezerAudioQuality
import moe.rukamori.archivetune.constants.DeezerAudioQualityKey
import moe.rukamori.archivetune.constants.DeezerEnabledKey
import moe.rukamori.archivetune.constants.JioSaavnEnabledKey
import moe.rukamori.archivetune.constants.SaavnAudioQuality
import moe.rukamori.archivetune.constants.SaavnAudioQualityKey
import moe.rukamori.archivetune.constants.toFormatName
import moe.rukamori.archivetune.deezer.DeezerAudioProvider
import moe.rukamori.archivetune.deezer.DeezerCrypto
import moe.rukamori.archivetune.deezer.DeezerDecryptingDataSource
import moe.rukamori.archivetune.jiosaavn.SaavnService
import moe.rukamori.archivetune.qobuz.QobuzAudioProvider
import moe.rukamori.archivetune.qobuz.QobuzBackupProvider
import moe.rukamori.archivetune.qobuz.QobuzToken
import moe.rukamori.archivetune.audiosource.AudioSourceConfig
import moe.rukamori.archivetune.audiosource.DirectStream
import moe.rukamori.archivetune.audiosource.SongSourceOverride
import moe.rukamori.archivetune.audiosource.SongSourceQobuzBackupVideoId
import moe.rukamori.archivetune.audiosource.SongSourceQobuzTrackId
import moe.rukamori.archivetune.audiosource.TitleMatch
import moe.rukamori.archivetune.audiosource.pcmBitrateOrNull
import moe.rukamori.archivetune.applemusic.AppleMusicAudioProvider
import moe.rukamori.archivetune.applemusic.AppleMusicVirtualStream
import moe.rukamori.archivetune.constants.SongSourceOverrideKey
import moe.rukamori.archivetune.constants.SongSourceQobuzBackupVideoIdKey
import moe.rukamori.archivetune.constants.SongSourceQobuzTrackIdKey
import moe.rukamori.archivetune.tidal.TidalAccountManager
import moe.rukamori.archivetune.tidal.TidalArtworkProvider
import moe.rukamori.archivetune.tidal.TidalAudioProvider
import moe.rukamori.archivetune.constants.TidalArtworkFallbackEnabledKey
import moe.rukamori.archivetune.constants.ArtworkProviderOrderKey
import moe.rukamori.archivetune.constants.DefaultArtworkProviderOrder
import moe.rukamori.archivetune.constants.deserializeArtworkProviderOrder
import moe.rukamori.archivetune.utils.PoolAccountManager
import moe.rukamori.archivetune.tidal.TidalInstanceHealthManager
import moe.rukamori.archivetune.constants.PlayerVolumeKey
import moe.rukamori.archivetune.constants.RepeatModeKey
import moe.rukamori.archivetune.constants.ScrobbleDelayPercentKey
import moe.rukamori.archivetune.constants.ScrobbleDelaySecondsKey
import moe.rukamori.archivetune.constants.ScrobbleMinSongDurationKey
import moe.rukamori.archivetune.constants.ShowLyricsKey
import moe.rukamori.archivetune.constants.SkipSilenceKey
import moe.rukamori.archivetune.constants.SmartTrimmerKey
import moe.rukamori.archivetune.constants.StopMusicOnTaskClearKey
import moe.rukamori.archivetune.constants.TogetherClientIdKey
import moe.rukamori.archivetune.constants.WakelockKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.AlbumEntity
import moe.rukamori.archivetune.db.entities.ArtistEntity
import moe.rukamori.archivetune.db.entities.Event
import moe.rukamori.archivetune.db.entities.FormatEntity
import moe.rukamori.archivetune.db.entities.LyricsEntity
import moe.rukamori.archivetune.db.entities.RelatedSongMap
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.db.entities.SongEntity
import moe.rukamori.archivetune.di.DownloadCache
import moe.rukamori.archivetune.di.PlayerCache
import moe.rukamori.archivetune.extensions.SilentHandler
import moe.rukamori.archivetune.extensions.collect
import moe.rukamori.archivetune.extensions.collectLatest
import moe.rukamori.archivetune.extensions.currentMetadata
import moe.rukamori.archivetune.extensions.directorySizeBytes
import moe.rukamori.archivetune.extensions.findNextMediaItemById
import moe.rukamori.archivetune.extensions.getQueueWindows
import moe.rukamori.archivetune.extensions.mediaItems
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.extensions.move
import moe.rukamori.archivetune.extensions.toEnum
import moe.rukamori.archivetune.extensions.setOffloadEnabled
import moe.rukamori.archivetune.extensions.toContinuationQueue
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.extensions.toPersistQueue
import moe.rukamori.archivetune.extensions.toQueue
import moe.rukamori.archivetune.innertube.PlaybackAuthState
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.playback.artwork.ArtworkProvider
import moe.rukamori.archivetune.playback.artwork.ArtworkRequest
import moe.rukamori.archivetune.playback.artwork.ArtworkResolver
import moe.rukamori.archivetune.playback.artwork.ArtworkSettings
import moe.rukamori.archivetune.playback.artwork.ResolvedArtwork
import moe.rukamori.archivetune.playback.artwork.isLocalArtworkUri
import moe.rukamori.archivetune.innertube.models.response.PlayerResponse
import moe.rukamori.archivetune.lastfm.LastFM
import moe.rukamori.archivetune.lyrics.LyricsHelper
import moe.rukamori.archivetune.lyrics.LyricsPreloadManager
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.models.PersistPlayerState
import moe.rukamori.archivetune.spotify.SpotifyLibraryRepository
import moe.rukamori.archivetune.models.PersistQueue
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.queues.EmptyQueue
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.playback.queues.Queue
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.playback.queues.filterBlockedArtists
import moe.rukamori.archivetune.playback.queues.filterExplicit
import moe.rukamori.archivetune.playback.queues.filterVideo
import moe.rukamori.archivetune.playback.queues.hasBlockedArtist
import moe.rukamori.archivetune.scrobbling.LastFmServiceConfig
import moe.rukamori.archivetune.storage.StorageFolderKind
import moe.rukamori.archivetune.storage.StorageLocationRepository
import moe.rukamori.archivetune.together.TogetherPlaybackSync
import moe.rukamori.archivetune.together.toPublicTrackInfo
import moe.rukamori.archivetune.together.toTogetherRoomState
import moe.rukamori.archivetune.together.toTogetherTrack
import moe.rukamori.archivetune.ui.screens.settings.DiscordPresenceManager
import moe.rukamori.archivetune.ui.screens.settings.ListenBrainzManager
import moe.rukamori.archivetune.utils.AuthScopedCacheValue
import moe.rukamori.archivetune.utils.CoilBitmapLoader
import moe.rukamori.archivetune.utils.NetworkConnectivityObserver
import moe.rukamori.archivetune.utils.StreamClientUtils
import moe.rukamori.archivetune.utils.SyncUtils
import moe.rukamori.archivetune.utils.YTPlayerUtils
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.enumPreference
import moe.rukamori.archivetune.utils.preference
import moe.rukamori.archivetune.utils.get
import moe.rukamori.archivetune.utils.getAsync
import moe.rukamori.archivetune.telegram.TelegramDataSource
import moe.rukamori.archivetune.telegram.isTelegramMediaId
import moe.rukamori.archivetune.utils.isLocalMediaId
import moe.rukamori.archivetune.utils.isLowDataModeActive
import moe.rukamori.archivetune.utils.reportException
import moe.rukamori.archivetune.utils.retryWithoutPlaybackLoginContext
import moe.rukamori.archivetune.widget.LoadWidgetInsightsUseCase
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber
import java.io.EOFException
import java.io.FileOutputStream
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.net.ConnectException
import java.net.Proxy
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.LocalDateTime
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.seconds

// Hoisted file-level Regex: the JioSaavn candidate penalty loop below calls
// "[^a-z0-9]".toRegex() 4 times per candidate (title, wanted title, artist,
// wanted artist), and a JioSaavn search typically returns 5-20 candidates.
// Compiling the Pattern once at class-load avoids ~20-80 Regex allocations
// per stream resolution. Same pattern, same replacement semantics.
private val JIO_SAAVN_NORMALIZE_REGEX = Regex("[^a-z0-9]")

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class, UnstableApi::class)
@AndroidEntryPoint
class MusicService :
    MediaLibraryService(),
    Player.Listener,
    PlaybackStatsListener.Callback {
    @Inject
    lateinit var spotifyLibraryRepository: SpotifyLibraryRepository
    @Inject
    lateinit var database: MusicDatabase

    @Inject
    lateinit var lyricsHelper: LyricsHelper

    @Inject
    lateinit var syncUtils: SyncUtils

    @Inject
    lateinit var mediaLibrarySessionCallback: MediaLibrarySessionCallback

    @Inject
    internal lateinit var loadWidgetInsightsUseCase: LoadWidgetInsightsUseCase

    @Inject
    lateinit var equalizerPlaybackController: EqualizerPlaybackController

    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var lastAudioFocusState = AudioManager.AUDIOFOCUS_NONE
    private var wasPlayingBeforeAudioFocusLoss = false
    private var pauseOnDeviceMuteEnabled = false
    private var deviceMutePlaybackRecoveryVolumePercent = 0
    private var wasAutoPausedByDeviceMute = false
    private var muteRecoveryObserver: ContentObserver? = null
    private var lastDeviceMutePlaybackNoticeAtElapsedMs = 0L
    private var hasAudioFocus = false
    private var autoStartOnBluetoothEnabled = false
    private var bluetoothReceiverRegistered = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var wakelockEnabled = false
    private var audioDeviceCallbackRegistered = false
    private var audioRouteRecoveryJob: Job? = null
    private var audiblePlaybackRecoveryJob: Job? = null
    private var sourceSwitchPending = false
    private var sourceSwitchExpectedVolume = 1f
    private var sourceSwitchReassertJob: Job? = null
    private var lastAudioOutputDeviceSignature: String? = null
    private var lastAudioRouteRecoveryRealtimeMs = 0L

    private lateinit var audioOutputResolver: AudioOutputResolver

    val activeAudioDevice get() = audioOutputResolver.activeAudioDevice

    fun refreshActiveDevice() = audioOutputResolver.refresh()

    private val audioDeviceCallback =
        object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
                if (addedDevices.any { it.isSink }) onAudioOutputDeviceChanged()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
                if (removedDevices.any { it.isSink }) onAudioOutputDeviceChanged()
            }
        }

    private var scopeJob = SupervisorJob()
    private var scope = CoroutineScope(Dispatchers.Main + scopeJob)
    private var ioScope = CoroutineScope(Dispatchers.IO + scopeJob)
    private val binder = MusicBinder()
    private var hasBoundClients = false
    private var idleStopJob: Job? = null

    private lateinit var connectivityManager: ConnectivityManager
    lateinit var connectivityObserver: NetworkConnectivityObserver
    val waitingForNetworkConnection = MutableStateFlow(false)
    private val isNetworkConnected = MutableStateFlow(false)

    private val audioQuality by enumPreference(
        this,
        AudioQualityKey,
        moe.rukamori.archivetune.constants.AudioQuality.AUTO,
    )
    private val preferredStreamClient by enumPreference(
        this,
        PlayerStreamClientKey,
        PlayerStreamClient.ANDROID_VR,
    )
    private val autoChoosePlaybackClient by preference(
        this,
        AutoChoosePlaybackClientKey,
        true,
    )
    private val playbackUrlCache = ConcurrentHashMap<String, AuthScopedCacheValue>()
    private val remotePlaybackTrackingUrlCache = ConcurrentHashMap<String, String>()
    private val contentLengthCache = ConcurrentHashMap<String, Long>()
    private val mediaOkHttpClient: OkHttpClient by lazy {
        OkHttpClient
            .Builder()
            .proxy(YouTube.streamOkHttpProxy)
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request()
                val host = request.url.host
                val isYouTubeMediaHost =
                    host.endsWith("googlevideo.com") ||
                        host.endsWith("googleusercontent.com") ||
                        host.endsWith("youtube.com") ||
                        host.endsWith("youtube-nocookie.com") ||
                        host.endsWith("ytimg.com")

                if (!isYouTubeMediaHost) {
                    // Qobuz backup server (mlc-ytify.kouzu.in) — used as a separate audio source
                    // when the user explicitly enables QOBUZ_BACKUP. The endpoint takes a YouTube
                    // video id as a path segment and returns a lossless stream. The
                    // x-request-source: muzo header MUST be sent on every request (including the
                    // redirected CDN fetch in some setups) — without it the server rate-limits
                    // aggressively. ExoPlayer streams through this same OkHttp client, so
                    // injecting the header here means the streaming requests (not just the
                    // initial resolve) also bypass rate-limiting.
                    if (host.endsWith("kouzu.in") && !request.header("x-request-source").isNullOrEmpty()) {
                        return@addInterceptor chain.proceed(request)
                    }
                    if (host.endsWith("kouzu.in")) {
                        val patched =
                            request
                                .newBuilder()
                                .header("x-request-source", "muzo")
                                .build()
                        return@addInterceptor chain.proceed(patched)
                    }
                    return@addInterceptor chain.proceed(request)
                }

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

    private var currentQueue: Queue = EmptyQueue
    var queueTitle: String? = null
    private var blockedArtistIds: Set<String> = emptySet()
    private var hideMusicVideos = false
    private var infiniteQueueJob: Job? = null
    private var infiniteQueueGeneration = 0L
    private var initialQueueLoadGeneration = 0L
    // True while playQueue's initial async load is still assembling the queue. While set, the
    // transition-driven infinite-radio bootstrap is suppressed so it isn't started prematurely and
    // then invalidated by the settling transitions (which previously left infiniteQueueLoading stuck
    // "on" with no songs actually queued when playing a single track from search).
    @Volatile
    private var initialQueueLoadInProgress = false
    private val persistentStateLock = Any()
    private val persistentSaveGeneration = AtomicLong(0L)

    @Volatile
    private var isRestoringPersistentState = false

    @Volatile
    private var isHydratingRestoredQueue = false
    private val restoredQueueHydrationGeneration = AtomicLong(0L)
    private var restoredQueueBackfillJob: Job? = null

    @Volatile
    private var suppressAutoPlayback = false
    private var lastPresenceToken: String? = null

    @Volatile
    private var pausedPresenceGate = PausedPresenceGate.FollowPreference

    @Volatile
    private var discordServiceStopping = false

    @Volatile
    private var lastDiscordPresenceDecision: DiscordPresenceDecision? = null

    @Volatile
    private var activeDiscordHoldState: ActiveHoldState? = null

    private var activeDiscordHoldTimeoutJob: Job? = null

    @Volatile
    private var lastAppliedVisiblePresence: LastAppliedVisiblePresence? = null

    private val discordSyncEpoch = AtomicLong(0L)
    private val discordSyncRequests = Channel<DiscordSyncRequest>(Channel.CONFLATED)
    private var discordSyncWorkerJob: Job? = null
    private val pendingDiscordRefreshWaiters = mutableListOf<CompletableDeferred<Boolean>>()
    private val discordRefreshWaitersMutex = Mutex()
    private val toggleLikeMutex = Mutex()

    @Volatile
    private var lastLoginRecoveryPrompt: Pair<String, Long>? = null
    private val playbackStreamRecoveryTracker = PlaybackStreamRecoveryTracker()

    // Initial-buffer-stall recovery (ported from Metrolist's "fix: playback once again").
    // A stream can stall in STATE_BUFFERING forever without surfacing an HTTP error the
    // onPlayerError recovery paths can act on — the player just spins, then the user skips.
    // Watchdog: if playback is BUFFERING with playWhenReady at the very start of a track for
    // longer than INITIAL_BUFFER_STALL_DELAY_MS, invalidate the resolved stream caches and
    // re-prepare so the resolver picks a fresh (possibly different-client) stream URL.
    private var initialBufferRecoveryJob: Job? = null
    private var initialBufferRecoveryAttemptedMediaId: String? = null

    // Codec-state recovery counter, SEPARATE from PlaybackStreamRecoveryTracker.
    //
    // Background: when an ALAC (.m4a) stream is backgrounded + paused for a few minutes,
    // Android may reclaim the hardware codec out from under ExoPlayer (low-memory kill).
    // ExoPlayer then surfaces a MediaCodecDecoderException wrapping either:
    //   - IllegalStateException("queueInputBuffer() is valid only at Executing states;
    //     currently at Released state")  — when the renderer tries to queue into a
    //     codec that's already been released, OR
    //   - CodecException("Error 0x80000000")  — the generic undefined MediaCodec error,
    //     which the same reclamation can produce on MediaTek's c2.mtk.alac.decoder.
    //
    // Re-prepare() recovers by instantiating a fresh codec, BUT the failure can recur:
    //   (a) at the very start of a song (codec init races / transient resource pressure), and
    //   (b) again on the next background+pause cycle.
    // PlaybackStreamRecoveryTracker allows only ONE retry per media item, which is too
    // tight for a recurring codec-state fault. We keep a small per-media budget here
    // (default 4 attempts) so transient codec reclamation doesn't kill the whole queue.
    @Volatile
    private var codecRecoveryMediaId: String? = null
    @Volatile
    private var codecRecoveryAttemptCount: Int = 0
    private val codecRecoveryMaxAttempts = 4
    private var nextHistorySessionToken = 0L
    private var currentHistorySessionToken = 0L
    private var currentHistoryMediaId: String? = null
    private var currentHistoryAccumulatedPlayMs = 0L
    private var currentHistoryStartedAtElapsedMs: Long? = null
    private var currentHistoryEventId: Long? = null
    private var currentHistoryRemoteRegistered = false
    private var currentHistoryImmediateAttempted = false
    private var currentHistorySessionQueued = false
    private var historyThresholdJob: Job? = null
    private val pendingHistoryFinalizations = mutableMapOf<String, MutableList<PendingHistoryFinalization>>()
    private val historyRecordingJobs = ConcurrentHashMap<Long, kotlinx.coroutines.Deferred<ImmediateHistoryResult>>()

    val currentMediaMetadata = MutableStateFlow<moe.rukamori.archivetune.models.MediaMetadata?>(null)
    val queueRestoreCompleted = MutableStateFlow(false)
    val infiniteQueueLoading = MutableStateFlow(false)
    private val playerInitialized = MutableStateFlow(false)
    private val currentSong =
        currentMediaMetadata
            .flatMapLatest { mediaMetadata ->
                database.song(mediaMetadata?.id)
            }.flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Lazily, null)
    private val currentFormat =
        currentMediaMetadata
            .flatMapLatest { mediaMetadata ->
                database.format(mediaMetadata?.id)
            }.flowOn(Dispatchers.IO)

    private val normalizeFactor = MutableStateFlow(1f)
    private val audioNormalizationFactorCache = ConcurrentHashMap<String, Float>()

    // Media ids currently served by a resolved Tidal stream. Audio normalization is skipped for
    // these because the loudness metadata we have describes the YouTube stream, not the Tidal one.
    private val tidalActiveMediaIds = ConcurrentHashMap.newKeySet<String>()
    private var audioNormalizationEnabled = true
    var playerVolume = MutableStateFlow(1f)
    private val audioFocusVolumeFactor = MutableStateFlow(1f)
    private var effectiveVolumeRampJob: Job? = null
    private var crossfadeEnabled = false
    private var crossfadeDurationMs = 0L
    private var crossfadeGapless = false
    private var crossfadeTriggerJob: Job? = null
    private var crossfadeJob: Job? = null
    private var secondaryCrossfadePlayer: ExoPlayer? = null
    private var secondaryCrossfadeTarget: CrossfadeTarget? = null
    private var isCrossfading = false
    private var crossfadeHandoffInProgress = false
    private var crossfadeBaseVolume = 1f
    private var crossfadeIncomingBaseVolume = 1f
    private var crossfadeProgress = 0f
    private var crossfadeHandoffProgress = 0f
    private var crossfadePlaybackRequested = false

    // Monotonic operation id for crossfades. A cancelled/superseded crossfade coroutine can
    // never complete an older handoff: finishCrossfade validates its captured generation
    // before promoting the incoming player.
    private val crossfadeGeneration = AtomicLong(0)
    private var lyricsPreloadManager: LyricsPreloadManager? = null

    // The currently active session-facing player. Replaced when a crossfade promotes the
    // incoming player. Observers (PlayerConnection) must follow this flow instead of
    // capturing `player` once.
    private val _playerFlow = MutableStateFlow<Player?>(null)
    val playerFlow = _playerFlow.asStateFlow()

    // Authoritative artwork-resolution pipeline. All artwork source decisions (original
    // metadata vs Tidal fallback) go through this resolver; results are committed centrally
    // so the player UI, notification and palette extractor all see the same image.
    private val artworkSettingsFlow =
        MutableStateFlow(
            ArtworkSettings(
                tidalArtworkEnabled = false,
                tidalAvailable = false,
                providerOrder = DefaultArtworkProviderOrder,
            ),
        )
    private lateinit var artworkResolver: ArtworkResolver
    private var artworkResolveJob: Job? = null
    private var artworkTrackGeneration: Long = 0L

    private val secondaryCrossfadeListener =
        object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Timber.tag(TAG).w(error, "Secondary crossfade player failed")
                scope.launch {
                    cancelCrossfade(resetVolume = true, resetPauseAtEnd = true)
                    scheduleCrossfade()
                }
            }
        }

    private data class CrossfadeConfig(
        val enabled: Boolean,
        val durationSeconds: Float,
        val gapless: Boolean,
    )

    private data class DiscordSyncRequest(
        val epoch: Long,
        val reason: String,
        val force: Boolean,
    )

    private data class Quadruple<A, B, C, D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
    )

    private class StaleDiscordSyncException : CancellationException("Stale Discord sync request")

    private data class CrossfadeTarget(
        val index: Int,
        val mediaId: String,
    )

    private data class PendingHistoryFinalization(
        val sessionToken: Long,
        val eventId: Long?,
        val remoteRegistered: Boolean,
    )

    private data class ImmediateHistoryResult(
        val eventId: Long?,
        val remoteRegistered: Boolean,
    )

    private fun PlayerResponse.PlaybackTracking.remotePlaybackTrackingUrl(): String? =
        videostatsPlaybackUrl
            ?.baseUrl
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private fun isAppInForeground(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val appProcesses = activityManager.runningAppProcesses ?: return false
        return appProcesses.any { processInfo ->
            processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                processInfo.processName == packageName
        }
    }

    private fun promptLoginRecovery(
        mediaId: String,
        targetUrl: String,
    ) {
        if (!isAppInForeground()) return

        val now = System.currentTimeMillis()
        val lastPrompt = lastLoginRecoveryPrompt
        if (lastPrompt?.first == mediaId && now - lastPrompt.second < 10000L) return
        lastLoginRecoveryPrompt = mediaId to now

        val deepLink = Uri.parse("archivetune://login?url=${Uri.encode(targetUrl)}")
        val intent =
            Intent(Intent.ACTION_VIEW, deepLink, this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

        runCatching {
            startActivity(intent)
        }.onFailure {
            Timber.e(it, "Failed to open login recovery for %s", mediaId)
        }
    }

    private fun Throwable.isRequestTimeout(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is SocketTimeoutException) return true
            if (current.message?.contains("Request timeout has expired", ignoreCase = true) == true) return true
            current = current.cause
        }
        return false
    }

    private fun Throwable.isNetworkConnectionFailure(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is ConnectException || current is UnknownHostException) return true
            current = current.cause
        }
        return false
    }

    lateinit var sleepTimer: SleepTimer

    @Inject
    @PlayerCache
    lateinit var playerCache: Cache

    @Inject
    @DownloadCache
    lateinit var downloadCache: Cache

    lateinit var localPlayer: ExoPlayer
        private set
    lateinit var player: Player
        private set
    private lateinit var castPlaybackRepository: CastPlaybackRepository
    private lateinit var mediaSession: MediaLibrarySession

    private var isAudioEffectSessionOpened = false
    private var openedAudioSessionId: Int? = null
    val eqCapabilities = MutableStateFlow<EqCapabilities?>(null)
    private val desiredEqSettings =
        MutableStateFlow(
            EqSettings(
                enabled = false,
                bandLevelsMb = emptyList(),
                outputGainEnabled = false,
                outputGainMb = 0,
                bassBoostEnabled = false,
                bassBoostStrength = 0,
                virtualizerEnabled = false,
                virtualizerStrength = 0,
                autoHeadroomEnabled = false,
            ),
        )

    private var audioEffectsSessionId: Int? = null
    private var audioEffectsInitializationJob: Job? = null
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private val audioEffectPlayerListener =
        object : Player.Listener {
            override fun onEvents(
                player: Player,
                events: Player.Events,
            ) {
                if (events.containsAny(
                        Player.EVENT_AUDIO_SESSION_ID,
                        Player.EVENT_PLAYBACK_STATE_CHANGED,
                        Player.EVENT_IS_PLAYING_CHANGED,
                    )
                ) {
                    reconcileAudioEffectSession()
                }
            }
        }

    private var lastDiscordUpdateTime = 0L

    private var scrobbleManager: moe.rukamori.archivetune.utils.ScrobbleManager? = null

    private lateinit var widgetUpdater: MusicServiceWidgetUpdater

    val autoAddedMediaIds: MutableSet<String> = java.util.Collections.synchronizedSet(mutableSetOf())

    private var consecutivePlaybackErr = 0

    val maxSafeGainFactor = MAX_AUDIO_NORMALIZATION_FACTOR

    @Volatile
    private var hasCalledStartForeground = false

    val togetherSessionState =
        MutableStateFlow<moe.rukamori.archivetune.together.TogetherSessionState>(
            moe.rukamori.archivetune.together.TogetherSessionState.Idle,
        )
    private var togetherServer: moe.rukamori.archivetune.together.TogetherServer? = null
    private var togetherPublicClient: moe.rukamori.archivetune.together.TogetherPublicClient? = null
    private var togetherPublicParticipants: List<moe.rukamori.archivetune.together.TogetherParticipant> = emptyList()
    private var togetherPublicLastState: moe.rukamori.archivetune.together.TogetherRoomState? = null
    private var togetherPublicLastAction: moe.rukamori.archivetune.together.TogetherPublicPlaybackActionPayload? = null
    private var togetherPublicServerUrl: String? = null
    private var togetherPublicForcedServerUrl: String? = null
    private var togetherPublicHostDisplayName: String? = null
    private var togetherPublicJoinCode: String? = null
    private var togetherPublicJoinDisplayName: String? = null
    private var togetherClient: moe.rukamori.archivetune.together.TogetherClient? = null
    private var togetherBroadcastJob: Job? = null
    private var togetherClientEventsJob: Job? = null
    private var togetherHeartbeatJob: Job? = null
    private var togetherHostInactivityJob: Job? = null
    private var togetherClock: moe.rukamori.archivetune.together.TogetherClock? = null
    private var togetherSelfParticipantId: String? = null
    private var togetherAuthorityParticipantId: String? = null
    private var togetherLastAppliedQueueHash: String? = null
    private var togetherIsOnlineSession: Boolean = false

    @Volatile
    private var togetherApplyingRemote: Boolean = false

    @Volatile
    private var togetherSuppressEchoUntilElapsedMs: Long = 0L

    @Volatile
    private var togetherLastAppliedRoomStateSentAtElapsedMs: Long = 0L

    @Volatile
    private var togetherLastRemoteAppliedPlayWhenReady: Boolean? = null

    @Volatile
    private var togetherLastRemoteAppliedIndex: Int = -1

    @Volatile
    private var togetherLastSentControlAtElapsedMs: Long = 0L

    @Volatile
    private var togetherLastSentControlAction: moe.rukamori.archivetune.together.ControlAction? = null

    @Volatile
    private var togetherPendingGuestControl: TogetherPendingGuestControl? = null

    private fun isTogetherApplyingRemote(): Boolean = togetherApplyingRemote

    private val togetherHostId: String = "host"
    private val togetherParticipantNames = ConcurrentHashMap<String, String>()
    private var lastTogetherNoticeAtElapsedMs: Long = 0L
    private var lastTogetherNoticeKey: String? = null

    private data class TogetherPendingGuestControl(
        val desiredIsPlaying: Boolean? = null,
        val desiredIndex: Int? = null,
        val desiredTrackId: String? = null,
        val requestedAtElapsedMs: Long,
        val expiresAtElapsedMs: Long,
    )

    private fun showTogetherNotice(
        message: String,
        key: String? = null,
    ) {
        val now = android.os.SystemClock.elapsedRealtime()
        val normalizedKey = key ?: message
        if (normalizedKey == lastTogetherNoticeKey && now - lastTogetherNoticeAtElapsedMs < 1200L) return
        lastTogetherNoticeKey = normalizedKey
        lastTogetherNoticeAtElapsedMs = now
        scope.launch(SilentHandler) {
            Toast.makeText(this@MusicService, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showTogetherParticipantNotification(
        participantName: String,
        joined: Boolean,
    ) {
        val normalizedName = participantName.trim().ifBlank { getString(R.string.together_unknown_participant) }
        val contentText =
            getString(
                if (joined) {
                    R.string.together_participant_joined_notification
                } else {
                    R.string.together_participant_left_notification
                },
                normalizedName,
            )
        val contentIntent =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat
                .Builder(this, TOGETHER_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.small_icon)
                .setContentTitle(getString(R.string.music_together))
                .setContentText(contentText)
                .setContentIntent(contentIntent)
                .setCategory(Notification.CATEGORY_STATUS)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()

        runCatching {
            getSystemService(NotificationManager::class.java)
                ?.notify(TOGETHER_PARTICIPANT_NOTIFICATION_ID, notification)
        }.onFailure { error ->
            Timber.tag("Together").v(error, "Unable to show participant notification")
        }
    }

    private fun showTogetherInactivityNotification() {
        val contentIntent =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat
                .Builder(this, TOGETHER_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.small_icon)
                .setContentTitle(getString(R.string.music_together))
                .setContentText(getString(R.string.together_room_closed_inactivity_notification))
                .setContentIntent(contentIntent)
                .setCategory(Notification.CATEGORY_STATUS)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()

        runCatching {
            getSystemService(NotificationManager::class.java)
                ?.notify(TOGETHER_INACTIVITY_NOTIFICATION_ID, notification)
        }.onFailure { error ->
            Timber.tag("Together").v(error, "Unable to show inactivity notification")
        }
    }

    private fun cancelTogetherHostInactivityTimeout() {
        togetherHostInactivityJob?.cancel()
        togetherHostInactivityJob = null
    }

    private fun scheduleTogetherHostInactivityTimeout(sessionId: String) {
        cancelTogetherHostInactivityTimeout()
        togetherHostInactivityJob =
            ioScope.launch(SilentHandler) {
                delay(TOGETHER_HOST_INACTIVITY_TIMEOUT_MS)

                val currentState = togetherSessionState.value
                val isCurrentHostSession =
                    when (currentState) {
                        is moe.rukamori.archivetune.together.TogetherSessionState.Hosting -> {
                            currentState.sessionId == sessionId
                        }

                        is moe.rukamori.archivetune.together.TogetherSessionState.HostingOnline -> {
                            currentState.sessionId == sessionId
                        }

                        is moe.rukamori.archivetune.together.TogetherSessionState.Joined -> {
                            currentState.sessionId == sessionId &&
                                currentState.role is moe.rukamori.archivetune.together.TogetherRole.Host
                        }

                        else -> {
                            false
                        }
                    }
                val isLocalAuthority =
                    togetherAuthorityParticipantId == null ||
                        togetherAuthorityParticipantId == togetherHostId
                val participants =
                    togetherServer?.currentParticipants()
                        ?: togetherPublicParticipants.takeIf { togetherPublicClient != null }
                        ?: emptyList()
                val hasConnectedGuest =
                    participants.any { participant ->
                        participant.id != togetherHostId &&
                            participant.isConnected &&
                            !participant.isPending
                    }
                if (!isCurrentHostSession ||
                    !isLocalAuthority ||
                    togetherParticipantNames.isNotEmpty() ||
                    hasConnectedGuest
                ) {
                    togetherHostInactivityJob = null
                    return@launch
                }

                togetherHostInactivityJob = null
                stopTogetherInternal()
                togetherSessionState.value = moe.rukamori.archivetune.together.TogetherSessionState.Idle
                showTogetherInactivityNotification()
                scheduleStopIfIdle()
            }
    }

    private suspend fun getOrCreateTogetherClientId(): String {
        val existing = dataStore.getAsync(TogetherClientIdKey)?.trim().orEmpty()
        if (existing.isNotBlank()) return existing
        val generated =
            java.util.UUID
                .randomUUID()
                .toString()
        dataStore.edit { prefs -> prefs[TogetherClientIdKey] = generated }
        return generated
    }

    private fun ensureStartedAsForeground() {
        if (hasCalledStartForeground) return

        val notification =
            try {
                val contentIntent =
                    PendingIntent.getActivity(
                        this,
                        0,
                        Intent(this, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )

                NotificationCompat
                    .Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.small_icon)
                    .setContentTitle(getString(R.string.music_player))
                    .setContentText(getString(R.string.app_name))
                    .setContentIntent(contentIntent)
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .build()
            } catch (e: Exception) {
                reportException(e)
                return
            }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            hasCalledStartForeground = true
        } catch (e: Exception) {
            reportException(e)
        }
    }

    private fun promoteToStartedService() {
        runCatching { startService(Intent(this, MusicService::class.java)) }
            .onFailure { reportException(it) }
    }

    private fun cancelIdleStop() {
        idleStopJob?.cancel()
        idleStopJob = null
    }

    private fun hasResumablePlaybackNotification(): Boolean {
        val state = player.playbackState
        return player.mediaItemCount > 0 &&
            player.currentMediaItem != null &&
            state != Player.STATE_IDLE &&
            state != Player.STATE_ENDED
    }

    private fun stopForegroundAndSelf() {
        cancelIdleStop()
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                stopForeground(true)
            }
        }
        hasCalledStartForeground = false
        stopSelf()
    }

    private fun scheduleStopIfIdle() {
        if (hasBoundClients) return
        if (hasResumablePlaybackNotification()) {
            cancelIdleStop()
            promoteToStartedService()
            ensureStartedAsForeground()
            return
        }
        val togetherIdle = togetherSessionState.value is moe.rukamori.archivetune.together.TogetherSessionState.Idle
        if (!togetherIdle) {
            cancelIdleStop()
            return
        }

        val state = player.playbackState
        // When the user merely paused a song (STATE_READY with playWhenReady=false) and
        // backgrounded the app, they typically come back within a few minutes. The previous
        // 60s idle-stop deadline was too aggressive for lossless streams — it released the
        // ExoPlayer (and the underlying MediaCodec) while the renderer still had queued
        // buffers, producing the
        //   IllegalStateException: queueInputBuffer() is valid only at Executing states;
        //   currently at Released state
        // crash on Telegram ALAC files. Give paused-but-ready playback a 10-minute grace
        // period before tearing the service down.
        val delayMs =
            when (state) {
                Player.STATE_ENDED, Player.STATE_IDLE -> 30_000L
                Player.STATE_READY -> if (player.playWhenReady) 60_000L else 10 * 60_000L
                Player.STATE_BUFFERING -> 60_000L
                else -> 60_000L
            }

        cancelIdleStop()
        idleStopJob =
            scope.launch {
                delay(delayMs)
                if (hasBoundClients) return@launch
                if (hasResumablePlaybackNotification()) return@launch
                if (togetherSessionState.value !is moe.rukamori.archivetune.together.TogetherSessionState.Idle) return@launch
                stopForegroundAndSelf()
            }
    }

    override fun onCreate() {
        super.onCreate()
        equalizerPlaybackController.attach(this)
        ensureScopesActive()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = getSystemService(NotificationManager::class.java)
                nm?.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        getString(R.string.music_player),
                        NotificationManager.IMPORTANCE_LOW,
                    ),
                )
                nm?.createNotificationChannel(
                    NotificationChannel(
                        TOGETHER_NOTIFICATION_CHANNEL_ID,
                        getString(R.string.music_together),
                        NotificationManager.IMPORTANCE_DEFAULT,
                    ),
                )
            }
        } catch (e: Exception) {
            reportException(e)
        }

        localPlayer =
            ExoPlayer
                .Builder(this)
                .setMediaSourceFactory(createMediaSourceFactory())
                .setRenderersFactory(createRenderersFactory())
                .setLoadControl(createPrimaryLoadControl())
                .setTrackSelector(DefaultTrackSelector(this, SafeTrackSelectionFactory()))
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .setAudioAttributes(
                    playbackAudioAttributes(),
                    false,
                ).setSeekBackIncrementMs(5000)
                .setSeekForwardIncrementMs(5000)
                .setDeviceVolumeControlEnabled(true)
                .build()
                .apply {
                    addAnalyticsListener(PlaybackStatsListener(false, this@MusicService))
                    addListener(audioEffectPlayerListener)
                    setOffloadEnabled(false)
                }
        castPlaybackRepository = CastPlaybackRepositoryLocator.get(this)
        player =
            castPlaybackRepository
                .createPlayer(
                    context = this,
                    localPlayer = localPlayer,
                    mediaItemResolver = CastMediaItemResolver(::resolveMediaItemForCast),
                ).apply {
                    addListener(this@MusicService)
                    sleepTimer = SleepTimer(scope, this, this@MusicService)
                    addListener(sleepTimer)
                }
        _playerFlow.value = player
        playerInitialized.value = true

        // The single authoritative artwork resolver. Every artwork source decision flows
        // through here so the player, notification and palette extractor can never diverge.
        artworkResolver =
            ArtworkResolver(
                tidalFetcher = TidalArtworkProvider.fetcher(),
                settings = artworkSettingsFlow,
                ioDispatcher = Dispatchers.IO,
            )
        dataStore.data
            .map { preferences ->
                val tidalEnabled = preferences[TidalEnabledKey] ?: true
                val artworkEnabled = preferences[TidalArtworkFallbackEnabledKey] ?: false
                val hasInstances =
                    !preferences[TidalInstancesKey].isNullOrBlank() ||
                        TidalAudioProvider.defaultInstanceUrls.isNotEmpty()
                val hasAccount = !preferences[TidalAccessTokenKey].isNullOrBlank()
                val providerOrder =
                    deserializeArtworkProviderOrder(preferences[ArtworkProviderOrderKey])
                ArtworkSettings(
                    tidalArtworkEnabled = artworkEnabled,
                    tidalAvailable = tidalEnabled && (hasInstances || hasAccount),
                    providerOrder = providerOrder,
                )
            }
            .distinctUntilChanged()
            .collect(scope) { settings ->
                val changed = artworkSettingsFlow.value != settings
                artworkSettingsFlow.value = settings
                if (changed) {
                    // Provider settings changed: in-flight results must no longer be committed.
                    artworkResolver.invalidate()
                    if (settings.tidalArtworkEnabled && settings.tidalAvailable) {
                        beginArtworkResolutionForCurrentTrack()
                    } else {
                        artworkResolveJob?.cancel()
                    }
                }
            }
        database
            .blockedArtistIds()
            .map { ids -> ids.toSet() }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)
            .collect(scope) { updatedBlockedArtistIds ->
                blockedArtistIds = updatedBlockedArtistIds
                removeBlockedArtistItems(updatedBlockedArtistIds)
            }
        dataStore.data
            .map { preferences -> preferences[HideVideoKey] ?: false }
            .distinctUntilChanged()
            .collect(scope) { shouldHideMusicVideos ->
                hideMusicVideos = shouldHideMusicVideos
                if (shouldHideMusicVideos) {
                    removeMusicVideoItems()
                }
            }
        widgetUpdater =
            MusicServiceWidgetUpdater(
                service = this,
                scope = scope,
                loadWidgetInsights = loadWidgetInsightsUseCase,
            )

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioOutputResolver = AudioOutputResolver(audioManager)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            audioManager.setAllowedCapturePolicy(android.media.AudioAttributes.ALLOW_CAPTURE_BY_ALL)
        }
        wakeLock =
            (getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ArchiveTune:Playback")
                .also { it.setReferenceCounted(false) }
        setupAudioFocusRequest()
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, android.os.Handler(mainLooper))
        audioDeviceCallbackRegistered = true
        lastAudioOutputDeviceSignature = currentAudioOutputDeviceSignature()
        audioOutputResolver.refresh()

        mediaLibrarySessionCallback.apply {
            toggleLike = ::toggleLike
            toggleStartRadio = ::toggleStartRadio
            toggleLibrary = ::toggleLibrary
        }
        mediaSession =
            MediaLibrarySession
                .Builder(this, player, mediaLibrarySessionCallback)
                .setSessionActivity(
                    PendingIntent.getActivity(
                        this,
                        0,
                        Intent(this, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE,
                    ),
                ).setBitmapLoader(CoilBitmapLoader(this, scope))
                .build()
        setMediaNotificationProvider(
            ArchiveTuneMediaNotificationProvider(
                context = this,
                smallIconResId = R.drawable.small_icon,
            ),
        )
        addSession(mediaSession)

        updateNotification()
        player.repeatMode = REPEAT_MODE_OFF

        val sessionToken = SessionToken(this, ComponentName(this, MusicService::class.java))
        val controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener({ controllerFuture.get() }, MoreExecutors.directExecutor())
        scope.launch(Dispatchers.IO) {
            val prefs = dataStore.data.first()
            val repeatMode = prefs[RepeatModeKey] ?: REPEAT_MODE_OFF
            val volume = (prefs[PlayerVolumeKey] ?: 1f).coerceIn(0f, 1f)
            val offload = prefs[AudioOffload] ?: false
            val crossfadePrefEnabled = prefs[CrossfadeEnabledKey] ?: false
            withContext(Dispatchers.Main) {
                player.repeatMode = repeatMode
                playerVolume.value = volume
                updateAudioOffload(offload && !crossfadePrefEnabled)
            }
        }

        connectivityManager = getSystemService()!!
        connectivityObserver = NetworkConnectivityObserver(this)

        scope.launch {
            connectivityObserver.networkStatus.collect { isConnected ->
                isNetworkConnected.value = isConnected
                if (isConnected && waitingForNetworkConnection.value) {
                    waitingForNetworkConnection.value = false
                    if (player.currentMediaItem != null && player.playWhenReady &&
                        player.playbackState == Player.STATE_IDLE
                    ) {
                        player.prepare()
                        player.play()
                    }
                }
            }
        }

        combine(playerVolume, normalizeFactor, audioFocusVolumeFactor) { playerVolume, normalizeFactor, audioFocusVolumeFactor ->
            calculateEffectivePlayerVolume(playerVolume, normalizeFactor, audioFocusVolumeFactor)
        }.collectLatest(scope) { finalVolume ->
            updateEffectiveVolume(finalVolume)
        }

        playerVolume.debounce(1000).collect(ioScope) { volume ->
            dataStore.edit { settings ->
                settings[PlayerVolumeKey] = volume
            }
        }

        currentSong.debounce(300).collect(scope) { song ->
            updateNotification()
            requestDiscordSync(
                reason =
                    if (song == null) {
                        "current_song_cleared"
                    } else {
                        "current_song_changed"
                    },
            )
            if (song != null && player.playWhenReady && player.playbackState == Player.STATE_READY) {
                ensurePresenceManager()
            }
        }

        combine(
            currentMediaMetadata.distinctUntilChangedBy { it?.id },
            dataStore.data.map { it[ShowLyricsKey] ?: false }.distinctUntilChanged(),
        ) { mediaMetadata, _ ->
            mediaMetadata
        }.collectLatest(ioScope) { mediaMetadata ->
            if (mediaMetadata == null) return@collectLatest
            // Always attempt to fetch lyrics when a new song starts playing so the
            // lyrics panel is ready by the time the user opens it (instead of
            // requiring the user to manually open the panel + search to trigger a
            // fetch). Previously this was gated on `showLyrics || isTelegram`,
            // which broke auto-fetch for non-Telegram tracks by default.
            //
            // We also retry a stored LYRICS_NOT_FOUND on every play, because early
            // plays can fail while metadata/network are still settling.
            val stored = database.lyrics(mediaMetadata.id).first()
            val shouldFetch =
                stored == null || stored.lyrics == LyricsEntity.LYRICS_NOT_FOUND
            if (shouldFetch) {
                // getLyricsWithProvider (not getLyrics) preserves the providerName — with the
                // plain getter the auto-fetched lyrics were stored with a blank attribution
                // and the "Lyrics from [provider]" header never rendered until a manual
                // re-fetch from the lyrics search popup (4nx3b fix).
                val lyricsResult = lyricsHelper.getLyricsWithProvider(mediaMetadata)
                database.query {
                    replaceLyricsIfAbsentOrNotFound(
                        id = mediaMetadata.id,
                        lyrics = lyricsResult.lyrics,
                        providerName = lyricsResult.providerName,
                    )
                }
            }
        }

        dataStore.data
            .map { it[SkipSilenceKey] ?: false }
            .distinctUntilChanged()
            .collectLatest(scope) {
                localPlayer.skipSilenceEnabled = it
                secondaryCrossfadePlayer?.skipSilenceEnabled = it
            }

        dataStore.data
            .map { it[PauseOnDeviceMuteKey] ?: false }
            .distinctUntilChanged()
            .collectLatest(scope) { enabled ->
                pauseOnDeviceMuteEnabled = enabled
                if (!enabled) {
                    wasAutoPausedByDeviceMute = false
                    unregisterMuteRecoveryObserver()
                } else {
                    handleDeviceMuteStateChanged()
                }
            }

        dataStore.data
            .map { (it[DeviceMutePlaybackRecoveryVolumeKey] ?: 0).coerceIn(0, 100) }
            .distinctUntilChanged()
            .collectLatest(scope) { percent ->
                deviceMutePlaybackRecoveryVolumePercent = percent
            }

        dataStore.data
            .map { it[AutoStartOnBluetoothKey] ?: false }
            .distinctUntilChanged()
            .collectLatest(scope) { enabled ->
                autoStartOnBluetoothEnabled = enabled
                if (enabled) {
                    registerBluetoothReceiver()
                } else {
                    unregisterBluetoothReceiver()
                }
            }

        combine(
            dataStore.data.map { it[AudioOffload] ?: false },
            dataStore.data.map { it[CrossfadeEnabledKey] ?: false },
        ) { offloadEnabled, crossfadeEnabled ->
            offloadEnabled to crossfadeEnabled
        }.distinctUntilChanged()
            .collectLatest(scope) { (offloadEnabled, crossfadeEnabled) ->
                val effectiveOffload = offloadEnabled && !crossfadeEnabled
                updateAudioOffload(effectiveOffload)
                if (effectiveOffload) {
                    val skipSilenceEnabled = dataStore.get(SkipSilenceKey, false)
                    if (skipSilenceEnabled) {
                        dataStore.edit { it[SkipSilenceKey] = false }
                        localPlayer.skipSilenceEnabled = false
                    }
                }
            }

        combine(dataStore.data, togetherSessionState) { prefs, togetherState ->
            val enabled = prefs[CrossfadeEnabledKey] ?: false
            val durationSeconds = prefs[CrossfadeDurationKey] ?: 5f
            val gapless = prefs[CrossfadeGaplessKey] ?: true
            CrossfadeConfig(
                enabled = enabled && togetherState is moe.rukamori.archivetune.together.TogetherSessionState.Idle,
                durationSeconds = durationSeconds,
                gapless = gapless,
            )
        }.distinctUntilChanged()
            .collectLatest(scope) { config ->
                crossfadeEnabled = config.enabled
                crossfadeDurationMs =
                    (config.durationSeconds.coerceIn(0f, 10f) * 1000f)
                        .roundToLong()
                        .coerceAtLeast(0L)
                crossfadeGapless = config.gapless
                if (crossfadeEnabled && crossfadeDurationMs > 0L) {
                    scheduleCrossfade()
                } else {
                    cancelCrossfade(resetVolume = true, resetPauseAtEnd = true)
                }
            }

        dataStore.data
            .map { it[WakelockKey] ?: false }
            .distinctUntilChanged()
            .collectLatest(scope) { enabled ->
                wakelockEnabled = enabled
                updateWakeLock()
            }

        // Initialize lyrics pre-load manager
        lyricsPreloadManager =
            LyricsPreloadManager(
                context = this,
                database = database,
                networkConnectivity = connectivityObserver,
                lyricsHelper = lyricsHelper,
            )

        dataStore.data
            .map(::readEqSettingsFromPrefs)
            .distinctUntilChanged()
            .collectLatest(scope) { settings ->
                desiredEqSettings.value = settings
                applyEqSettingsToEffects(settings)
            }

        combine(
            currentMediaMetadata
                .map { it?.id }
                .distinctUntilChanged(),
            currentFormat,
            dataStore.data
                .map { it[AudioNormalizationKey] ?: true }
                .distinctUntilChanged(),
        ) { mediaId, format, normalizeAudio ->
            normalizeAudio to resolveAudioNormalizationFactor(mediaId, format, normalizeAudio)
        }.distinctUntilChanged()
            .collectLatest(scope) { (normalizeAudio, factor) ->
                audioNormalizationEnabled = normalizeAudio
                normalizeFactor.value = factor
            }

        dataStore.data
            .map { it[DiscordTokenKey] to (it[EnableDiscordRPCKey] ?: true) }
            .debounce(300)
            .distinctUntilChanged()
            .collectLatest(scope) { (key, enabled) ->
                requestDiscordSync(
                    reason =
                        when {
                            !enabled -> "discord_rpc_disabled"
                            key.isNullOrBlank() -> "discord_token_missing"
                            else -> "discord_token_or_toggle_changed"
                        },
                    force = !enabled || key.isNullOrBlank(),
                )
                if (!key.isNullOrBlank() && enabled) {
                    if (player.playbackState == Player.STATE_READY && player.playWhenReady) {
                        currentSong.value?.let {
                            ensurePresenceManager()
                        }
                    }
                }
            }

        dataStore.data
            .map { prefs ->
                (prefs[SmartTrimmerKey] ?: false) to (prefs[MaxSongCacheSizeKey] ?: 1024)
            }.debounce(300)
            .distinctUntilChanged()
            .collectLatest(ioScope) { (enabled, maxSongCacheSizeMb) ->
                if (!enabled) return@collectLatest
                if (maxSongCacheSizeMb <= 0 || maxSongCacheSizeMb == -1) return@collectLatest
                val bytesPerMb = 1024L * 1024L
                val safeSizeMb = maxSongCacheSizeMb.toLong().coerceAtMost(Long.MAX_VALUE / bytesPerMb)
                val limitBytes = safeSizeMb * bytesPerMb
                trimPlayerCacheToBytes(limitBytes)
            }

        dataStore.data
            .map { preferences ->
                val serviceConfig = LastFmServiceConfig.fromPreferences(preferences)
                Triple(
                    preferences[EnableLastFMScrobblingKey] ?: false,
                    !preferences[LastFMSessionKey].isNullOrBlank(),
                    serviceConfig.initialized,
                )
            }.debounce(300)
            .distinctUntilChanged()
            .collect(scope) { (enabled, hasSession, serviceConfigured) ->
                val shouldEnable = enabled && hasSession && serviceConfigured
                if (shouldEnable && scrobbleManager == null) {
                    val delayPercent = dataStore.get(ScrobbleDelayPercentKey, LastFM.DEFAULT_SCROBBLE_DELAY_PERCENT)
                    val minSongDuration = dataStore.get(ScrobbleMinSongDurationKey, LastFM.DEFAULT_SCROBBLE_MIN_SONG_DURATION)
                    val delaySeconds = dataStore.get(ScrobbleDelaySecondsKey, LastFM.DEFAULT_SCROBBLE_DELAY_SECONDS)

                    scrobbleManager =
                        moe.rukamori.archivetune.utils.ScrobbleManager(
                            ioScope,
                            minSongDuration = minSongDuration,
                            scrobbleDelayPercent = delayPercent,
                            scrobbleDelaySeconds = delaySeconds,
                        )
                    scrobbleManager?.useNowPlaying = dataStore.get(LastFMUseNowPlaying, false)
                } else if (!shouldEnable && scrobbleManager != null) {
                    scrobbleManager?.destroy()
                    scrobbleManager = null
                }
            }

        dataStore.data
            .map { it[LastFMUseNowPlaying] ?: false }
            .distinctUntilChanged()
            .collectLatest(scope) {
                scrobbleManager?.useNowPlaying = it
            }

        dataStore.data
            .map { prefs ->
                Triple(
                    prefs[ScrobbleDelayPercentKey] ?: LastFM.DEFAULT_SCROBBLE_DELAY_PERCENT,
                    prefs[ScrobbleMinSongDurationKey] ?: LastFM.DEFAULT_SCROBBLE_MIN_SONG_DURATION,
                    prefs[ScrobbleDelaySecondsKey] ?: LastFM.DEFAULT_SCROBBLE_DELAY_SECONDS,
                )
            }.distinctUntilChanged()
            .collect(scope) { (delayPercent, minSongDuration, delaySeconds) ->
                scrobbleManager?.let {
                    it.scrobbleDelayPercent = delayPercent
                    it.minSongDuration = minSongDuration
                    it.scrobbleDelaySeconds = delaySeconds
                }
            }

        scope.launch(Dispatchers.IO) {
            runCatching {
                if (dataStore.get(PersistentQueueKey, true)) {
                    playerInitialized.first { it }
                    val persistedQueue = readPersistentObject<PersistQueue>(PERSISTENT_QUEUE_FILE)
                    val persistedPlayerState = readPersistentObject<PersistPlayerState>(PERSISTENT_PLAYER_STATE_FILE)

                    if (persistedQueue != null || persistedPlayerState != null) {
                        isRestoringPersistentState = true
                    }

                    var restoredQueue = false
                    try {
                        persistedQueue?.let { queue ->
                            restorePersistentQueue(queue)
                            restoredQueue = true
                        }
                        persistedPlayerState?.let { playerState ->
                            restorePersistentPlayerState(playerState, restoredQueue)
                        }
                    } finally {
                        isRestoringPersistentState = false
                    }
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                Timber.tag(TAG).w(error, "Failed to restore persisted queue, clearing data")
                isRestoringPersistentState = false
                cancelRestoredQueueHydration()
                clearPersistedQueueFiles()
            }
            withContext(Dispatchers.Main) {
                queueRestoreCompleted.value = true
            }
        }

        // Periodic save exists only to keep the resume *position* roughly current for a process
        // death; every change to the queue itself already calls saveQueueToDisk from the eight
        // event handlers that cause one. So this writes the small player-state file only — the
        // full-queue snapshot it used to take mapped every media item on the main thread and wrote
        // both files, six times a minute, for a queue that had not changed.
        scope.launch {
            while (isActive) {
                delay(PERSISTENT_POSITION_SAVE_INTERVAL)
                val shouldSave = withContext(Dispatchers.IO) { dataStore.get(PersistentQueueKey, true) }
                if (shouldSave && player.mediaItemCount > 0) {
                    savePlayerStateToDisk()
                }
            }
        }
    }

    private fun ensureScopesActive() {
        if (!scopeJob.isActive) {
            scopeJob = SupervisorJob()
        }
        if (!scope.isActive) {
            scope = CoroutineScope(Dispatchers.Main + scopeJob)
        }
        if (!ioScope.isActive) {
            ioScope = CoroutineScope(Dispatchers.IO + scopeJob)
        }
        startDiscordSyncWorker()
    }

    private fun startDiscordSyncWorker() {
        if (discordSyncWorkerJob?.isActive == true) return
        discordSyncWorkerJob =
            scope.launch(Dispatchers.IO) {
                for (request in discordSyncRequests) {
                    try {
                        syncDiscordStateInternal(request)
                    } catch (_: StaleDiscordSyncException) {
                        Timber.tag(DISCORD_SYNC_TAG).d(
                            "stale sync aborted epoch=%d reason=%s",
                            request.epoch,
                            request.reason,
                        )
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        Timber.tag(DISCORD_SYNC_TAG).e(
                            error,
                            "sync failed epoch=%d reason=%s",
                            request.epoch,
                            request.reason,
                        )
                    }
                }
            }
    }

    private fun requestDiscordSync(
        reason: String,
        force: Boolean = false,
    ) {
        val request =
            DiscordSyncRequest(
                epoch = discordSyncEpoch.incrementAndGet(),
                reason = reason,
                force = force,
            )
        if (discordSyncRequests.trySend(request).isFailure) {
            Timber.tag(DISCORD_SYNC_TAG).w(
                "failed to enqueue sync epoch=%d reason=%s",
                request.epoch,
                request.reason,
            )
        }
    }

    fun forceDiscordSync(reason: String) {
        requestDiscordSync(
            reason = reason,
            force = true,
        )
    }

    private fun ensureDiscordSyncFresh(epoch: Long) {
        if (epoch != discordSyncEpoch.get()) {
            throw StaleDiscordSyncException()
        }
    }

    private fun updateActiveDiscordHoldState(nextHoldState: ActiveHoldState?) {
        val previousHoldState = activeDiscordHoldState
        activeDiscordHoldState = nextHoldState
        Timber.tag(DISCORD_SYNC_TAG).d(
            "hold state transition previous=%s next=%s",
            previousHoldState,
            nextHoldState,
        )
        reconcileDiscordHoldTimeoutJob(previousHoldState, nextHoldState)
    }

    private fun reconcileDiscordHoldTimeoutJob(
        previousHoldState: ActiveHoldState?,
        nextHoldState: ActiveHoldState?,
    ) {
        if (previousHoldState === nextHoldState) {
            Timber.tag(DISCORD_SYNC_TAG).v("hold timeout job unchanged for holdState=%s", nextHoldState)
            return
        }

        activeDiscordHoldTimeoutJob?.cancel()
        activeDiscordHoldTimeoutJob = null

        if (nextHoldState == null) {
            Timber.tag(DISCORD_SYNC_TAG).d("no active hold state, no timeout job scheduled")
            return
        }

        Timber.tag(DISCORD_SYNC_TAG).d(
            "scheduling hold timeout job state=%s timeoutMs=%d",
            nextHoldState,
            DISCORD_HOLD_TIMEOUT_MS,
        )
        activeDiscordHoldTimeoutJob =
            scope.launch {
                delay(DISCORD_HOLD_TIMEOUT_MS)
                Timber.tag(DISCORD_SYNC_TAG).d(
                    "hold timeout fired state=%s -> enqueue resync",
                    nextHoldState,
                )
                requestDiscordSync(
                    reason = "hold_timeout_check",
                    force = true,
                )
            }
    }

    private fun clearDiscordHoldState() {
        if (activeDiscordHoldState != null) {
            Timber.tag(DISCORD_SYNC_TAG).d("clearing active hold state=%s", activeDiscordHoldState)
        }
        updateActiveDiscordHoldState(null)
    }

    private fun markLastAppliedVisiblePresence(visibleDecision: DiscordPresenceDecision.Visible) {
        lastAppliedVisiblePresence =
            LastAppliedVisiblePresence(
                songId = visibleDecision.songId,
                mode = visibleDecision.mode,
                appliedAtMs = System.currentTimeMillis(),
            )
        Timber.tag(DISCORD_SYNC_TAG).d(
            "marked last applied visible presence songId=%s mode=%s",
            visibleDecision.songId,
            visibleDecision.mode,
        )
    }

    private suspend fun addPendingDiscordRefreshWaiter(waiter: CompletableDeferred<Boolean>) {
        discordRefreshWaitersMutex.withLock {
            pendingDiscordRefreshWaiters += waiter
        }
    }

    private suspend fun takePendingDiscordRefreshWaiters(): List<CompletableDeferred<Boolean>> =
        discordRefreshWaitersMutex.withLock {
            val snapshot = pendingDiscordRefreshWaiters.toList()
            pendingDiscordRefreshWaiters.removeAll(snapshot)
            snapshot
        }

    private suspend fun requeueDiscordRefreshWaiters(waiters: List<CompletableDeferred<Boolean>>) {
        if (waiters.isEmpty()) return
        discordRefreshWaitersMutex.withLock {
            waiters.forEach { waiter ->
                if (!waiter.isCompleted && !waiter.isCancelled) {
                    pendingDiscordRefreshWaiters += waiter
                }
            }
        }
    }

    private fun completeDiscordRefreshWaiters(
        waiters: List<CompletableDeferred<Boolean>>,
        result: Boolean,
    ) {
        waiters.forEach { waiter ->
            if (!waiter.isCompleted && !waiter.isCancelled) {
                waiter.complete(result)
            }
        }
    }

    suspend fun refreshDiscordNow(): Boolean {
        val waiter = CompletableDeferred<Boolean>()
        addPendingDiscordRefreshWaiter(waiter)
        requestDiscordSync(
            reason = "manual_refresh",
            force = true,
        )
        return try {
            withTimeout(15_000L) { waiter.await() }
        } catch (error: CancellationException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun syncDiscordStateInternal(request: DiscordSyncRequest) {
        val refreshWaiters = takePendingDiscordRefreshWaiters()
        try {
            ensureDiscordSyncFresh(request.epoch)

            val enabled = dataStore.get(EnableDiscordRPCKey, true)
            val token = dataStore.get(DiscordTokenKey, "")
            val hasToken = token.isNotBlank()
            val showWhenPaused = dataStore.get(DiscordShowWhenPausedKey, false)
            val (song, isPlaying, playWhenReady, playbackState) =
                withContext(Dispatchers.Main.immediate) {
                    Quadruple(
                        currentPresenceSong(),
                        player.isPlaying,
                        player.playWhenReady,
                        player.playbackState,
                    )
                }

            if (playWhenReady && pausedPresenceGate != PausedPresenceGate.FollowPreference) {
                pausedPresenceGate = PausedPresenceGate.FollowPreference
                Timber.tag(DISCORD_SYNC_TAG).d(
                    "sync epoch=%d reason=%s reset paused gate because playback intent resumed",
                    request.epoch,
                    request.reason,
                )
            }

            val inputs =
                DiscordPresenceInputs(
                    enabled = enabled,
                    hasToken = hasToken,
                    song = song,
                    isPlaying = isPlaying,
                    showWhenPaused = showWhenPaused,
                    pausedPresenceGate = pausedPresenceGate,
                    serviceStopping = discordServiceStopping,
                    playWhenReady = playWhenReady,
                    playbackState = playbackState,
                )
            val holdContext =
                DiscordHoldContext(
                    nowMs = System.currentTimeMillis(),
                    activeHoldState = activeDiscordHoldState,
                    lastAppliedVisiblePresence = lastAppliedVisiblePresence,
                    holdTimeoutMs = DISCORD_HOLD_TIMEOUT_MS,
                )
            val semanticState = derivePlaybackSemanticState(inputs)
            val rawDecision = deriveRawDiscordPresenceDecision(inputs, semanticState)
            val resolution = resolveDiscordPresenceDecision(rawDecision, holdContext)

            val decision = resolution.decision
            ensureDiscordSyncFresh(request.epoch)

            val effectiveForce = request.force || refreshWaiters.isNotEmpty()
            if (!effectiveForce && decision == lastDiscordPresenceDecision) {
                Timber.tag(DISCORD_SYNC_TAG).v(
                    "sync epoch=%d reason=%s unchanged decision=%s",
                    request.epoch,
                    request.reason,
                    decision,
                )
                completeDiscordRefreshWaiters(refreshWaiters, true)
                return
            }

            Timber.tag(DISCORD_SYNC_TAG).d(
                "sync epoch=%d reason=%s force=%s effectiveForce=%s songId=%s playWhenReady=%s playbackState=%d isPlaying=%s semantic=%s raw=%s decision=%s holdState=%s lastAppliedVisible=%s refreshWaiters=%d",
                request.epoch,
                request.reason,
                request.force,
                effectiveForce,
                song?.song?.id,
                playWhenReady,
                playbackState,
                isPlaying,
                semanticState,
                rawDecision,
                decision,
                resolution.nextHoldState,
                lastAppliedVisiblePresence,
                refreshWaiters.size,
            )

            val applied =
                applyDiscordPresenceDecision(
                    request = request,
                    resolution = resolution,
                    token = token,
                    song = song,
                )

            if (applied) {
                lastDiscordPresenceDecision = decision
            }
            if (decision is DiscordPresenceDecision.Hold) {
                requeueDiscordRefreshWaiters(refreshWaiters)
                Timber.tag(DISCORD_SYNC_TAG).d(
                    "refresh waiters requeued because decision is Hold count=%d",
                    refreshWaiters.size,
                )
            } else {
                completeDiscordRefreshWaiters(refreshWaiters, applied)
            }
        } catch (_: StaleDiscordSyncException) {
            requeueDiscordRefreshWaiters(refreshWaiters)
            Timber.tag(DISCORD_SYNC_TAG).d(
                "stale sync aborted epoch=%d reason=%s and refresh waiters requeued=%d",
                request.epoch,
                request.reason,
                refreshWaiters.size,
            )
        } catch (error: CancellationException) {
            completeDiscordRefreshWaiters(refreshWaiters, false)
            throw error
        } catch (error: Exception) {
            Timber.tag(DISCORD_SYNC_TAG).e(error, "syncDiscordStateInternal failed epoch=%d reason=%s", request.epoch, request.reason)
            completeDiscordRefreshWaiters(refreshWaiters, false)
            throw error
        }
    }

    private suspend fun applyDiscordPresenceDecision(
        request: DiscordSyncRequest,
        resolution: DiscordPresenceResolution,
        token: String,
        song: Song?,
    ): Boolean {
        ensureDiscordSyncFresh(request.epoch)

        val decision = resolution.decision
        Timber.tag(DISCORD_SYNC_TAG).d(
            "apply decision epoch=%d decision=%s tokenPresent=%s songId=%s",
            request.epoch,
            decision,
            token.isNotBlank() || !lastPresenceToken.isNullOrBlank(),
            song?.song?.id,
        )
        return when (decision) {
            is DiscordPresenceDecision.Hidden -> {
                clearDiscordHoldState()
                when (decision.reason) {
                    HiddenReason.NoSong,
                    HiddenReason.PausedByPreference,
                    HiddenReason.PausedByNotificationDismiss,
                    HiddenReason.NoStablePlaybackYet,
                    HiddenReason.PlaybackStalled,
                    -> {
                        ensureDiscordSyncFresh(request.epoch)
                        val cleared =
                            DiscordPresenceManager.clearNow(
                                context = this@MusicService,
                                token = token.takeIf { it.isNotBlank() } ?: lastPresenceToken,
                            )
                        if (!cleared) {
                            Timber.tag(DISCORD_SYNC_TAG).d(
                                "clear skipped or failed for hidden reason=%s",
                                decision.reason,
                            )
                        }
                        cleared
                    }

                    HiddenReason.Disabled -> {
                        ensureDiscordSyncFresh(request.epoch)
                        DiscordPresenceManager.stop(clearActivity = false)
                        lastPresenceToken = null
                        true
                    }

                    HiddenReason.ServiceStopping -> {
                        val clearToken = token.takeIf { it.isNotBlank() } ?: lastPresenceToken
                        ensureDiscordSyncFresh(request.epoch)
                        val cleared =
                            DiscordPresenceManager.clearNow(
                                context = this@MusicService,
                                token = clearToken,
                            )
                        if (!cleared) {
                            Timber.tag(DISCORD_SYNC_TAG).d(
                                "terminal clear skipped or failed for hidden reason=%s",
                                decision.reason,
                            )
                        }
                        ensureDiscordSyncFresh(request.epoch)
                        DiscordPresenceManager.stop()
                        lastPresenceToken = null
                        true
                    }

                    HiddenReason.NoToken -> {
                        val clearToken = token.takeIf { it.isNotBlank() } ?: lastPresenceToken
                        ensureDiscordSyncFresh(request.epoch)
                        if (clearToken.isNullOrBlank()) {
                            Timber.tag(DISCORD_SYNC_TAG).v(
                                "no token available for terminal clear; stopping manager only",
                            )
                        } else {
                            val cleared =
                                DiscordPresenceManager.clearNow(
                                    context = this@MusicService,
                                    token = clearToken,
                                )
                            if (!cleared) {
                                Timber.tag(DISCORD_SYNC_TAG).d(
                                    "terminal clear skipped or failed for hidden reason=%s",
                                    decision.reason,
                                )
                            }
                        }
                        ensureDiscordSyncFresh(request.epoch)
                        DiscordPresenceManager.stop()
                        lastPresenceToken = null
                        true
                    }
                }
            }

            is DiscordPresenceDecision.Visible -> {
                clearDiscordHoldState()
                ensureDiscordSyncFresh(request.epoch)
                val snapshot = buildDiscordPresenceSnapshot(song, decision.isPaused) ?: return false
                ensureDiscordSyncFresh(request.epoch)
                val updated =
                    DiscordPresenceManager.updateNow(
                        context = this@MusicService,
                        token = token,
                        song = snapshot.song,
                        positionMs = snapshot.positionMs,
                        isPaused = snapshot.isPaused,
                        isMusicVideo = currentMediaMetadata.value?.isMusicVideo ?: false,
                    )
                if (!updated) {
                    Timber.tag(DISCORD_SYNC_TAG).d(
                        "visible update failed songId=%s paused=%s",
                        decision.songId,
                        decision.isPaused,
                    )
                    false
                } else {
                    if (token.isNotBlank()) {
                        lastPresenceToken = token
                    }
                    markLastAppliedVisiblePresence(decision)
                    true
                }
            }

            is DiscordPresenceDecision.Hold -> {
                updateActiveDiscordHoldState(resolution.nextHoldState)
                true
            }
        }
    }

    private suspend fun buildDiscordPresenceSnapshot(
        song: Song?,
        isPaused: Boolean,
    ): DiscordPresenceSnapshot? {
        val resolvedSong = song ?: return null
        val positionMs = withContext(Dispatchers.Main.immediate) { player.currentPosition }
        return DiscordPresenceSnapshot(
            song = resolvedSong,
            positionMs = positionMs,
            isPaused = isPaused,
        )
    }

    private fun cancelRestoredQueueHydration() {
        restoredQueueHydrationGeneration.incrementAndGet()
        restoredQueueBackfillJob?.cancel()
        restoredQueueBackfillJob = null
        isHydratingRestoredQueue = false
    }

    private suspend fun Queue.Status.filterPlaybackContent(
        hideExplicit: Boolean,
        hideVideo: Boolean,
    ): Queue.Status =
        filterExplicit(hideExplicit)
            .filterVideo(hideVideo)
            .filterBlockedArtists(loadBlockedArtistIds())

    private suspend fun List<MediaItem>.filterPlaybackContent(
        hideExplicit: Boolean,
        hideVideo: Boolean,
    ): List<MediaItem> =
        filterExplicit(hideExplicit)
            .filterVideo(hideVideo)
            .filterBlockedArtists(loadBlockedArtistIds())

    private suspend fun loadBlockedArtistIds(): Set<String> =
        withContext(Dispatchers.IO) {
            database.getBlockedArtistIds().toSet()
        }

    private fun removeBlockedArtistItems(updatedBlockedArtistIds: Set<String>) {
        if (updatedBlockedArtistIds.isEmpty() || player.mediaItemCount == 0) return

        removeQueueItems { item -> item.hasBlockedArtist(updatedBlockedArtistIds) }
    }

    private fun removeMusicVideoItems() {
        removeQueueItems { item -> item.metadata?.isMusicVideo == true }
    }

    private inline fun removeQueueItems(shouldRemove: (MediaItem) -> Boolean) {
        if (player.mediaItemCount == 0) return

        var blockedRangeEnd = C.INDEX_UNSET
        for (index in player.mediaItemCount - 1 downTo 0) {
            val item = player.getMediaItemAt(index)
            if (shouldRemove(item)) {
                autoAddedMediaIds.remove(item.mediaId)
                if (blockedRangeEnd == C.INDEX_UNSET) {
                    blockedRangeEnd = index + 1
                }
            } else if (blockedRangeEnd != C.INDEX_UNSET) {
                player.removeMediaItems(index + 1, blockedRangeEnd)
                blockedRangeEnd = C.INDEX_UNSET
            }
        }
        if (blockedRangeEnd != C.INDEX_UNSET) {
            player.removeMediaItems(0, blockedRangeEnd)
        }
        if (player.mediaItemCount == 0) {
            cancelInfiniteQueueBootstrap()
            currentQueue = EmptyQueue
            queueTitle = null
        }
    }

    private suspend fun restorePersistentQueue(persistedQueue: PersistQueue) {
        cancelRestoredQueueHydration()
        val hydrationGeneration = restoredQueueHydrationGeneration.incrementAndGet()
        isHydratingRestoredQueue = true

        val itemQueue = persistedQueue.toQueue()
        val continuationQueue = persistedQueue.toContinuationQueue()
        val hideExplicit = dataStore.get(HideExplicitKey, false)
        val hideVideo = dataStore.get(HideVideoKey, false)
        val initialStatus =
            itemQueue
                .getInitialStatus()
                .filterPlaybackContent(hideExplicit, hideVideo)

        withContext(Dispatchers.Main) {
            currentQueue = continuationQueue
            queueTitle = initialStatus.title

            val items = initialStatus.items
            if (items.isEmpty()) {
                if (hydrationGeneration == restoredQueueHydrationGeneration.get()) {
                    isHydratingRestoredQueue = false
                }
                return@withContext
            }

            val fullIndex = initialStatus.mediaItemIndex.coerceIn(0, items.lastIndex)
            val windowStart = (fullIndex - 20).coerceAtLeast(0)
            val windowEnd = (fullIndex + 50).coerceAtMost(items.size)

            val initialChunk = items.subList(windowStart, windowEnd)
            val relativeIndex = (fullIndex - windowStart).coerceIn(0, initialChunk.lastIndex)

            player.setMediaItems(
                initialChunk,
                relativeIndex,
                initialStatus.position,
            )
            player.prepare()
            player.playWhenReady = false
            currentMediaMetadata.value = player.currentMetadata
            updateNotification()

            if (items.size > initialChunk.size) {
                restoredQueueBackfillJob =
                    scope.launch(SilentHandler) {
                        try {
                            delay(2000)
                            if (!isActive || player.mediaItemCount == 0) return@launch
                            if (windowStart > 0) {
                                player.addMediaItems(0, items.subList(0, windowStart))
                            }
                            if (windowEnd < items.size) {
                                player.addMediaItems(items.subList(windowEnd, items.size))
                            }
                        } finally {
                            if (hydrationGeneration == restoredQueueHydrationGeneration.get()) {
                                isHydratingRestoredQueue = false
                                restoredQueueBackfillJob = null
                                if (isActive && dataStore.get(PersistentQueueKey, true) && player.mediaItemCount > 0) {
                                    saveQueueToDisk()
                                }
                            }
                        }
                    }
            } else {
                if (hydrationGeneration == restoredQueueHydrationGeneration.get()) {
                    isHydratingRestoredQueue = false
                }
            }
        }
    }

    private suspend fun restorePersistentPlayerState(
        playerState: PersistPlayerState,
        restoredQueue: Boolean,
    ) {
        withContext(Dispatchers.Main) {
            player.repeatMode = playerState.repeatMode
            player.shuffleModeEnabled = playerState.shuffleModeEnabled
            playerVolume.value = playerState.volume.coerceIn(0f, 1f)

            if (player.mediaItemCount > 0) {
                val index =
                    when {
                        restoredQueue -> {
                            player.currentMediaItemIndex.coerceIn(0, player.mediaItemCount - 1)
                        }

                        playerState.currentMediaItemIndex in 0 until player.mediaItemCount -> {
                            playerState.currentMediaItemIndex
                        }

                        else -> {
                            player.currentMediaItemIndex.coerceIn(0, player.mediaItemCount - 1)
                        }
                    }
                player.seekTo(index, playerState.currentPosition.coerceAtLeast(0L))
            }

            player.playWhenReady = false
            abandonAudioFocus()

            currentMediaMetadata.value = player.currentMetadata.takeIf { player.mediaItemCount > 0 }
            updateNotification()
        }
    }

    private fun ensurePresenceManager() {
        if (DiscordPresenceManager.isRunning() && lastPresenceToken != null) return

        // Launch in scope to avoid blocking
        scope.launch {
            // Don't start if Discord RPC is disabled in settings
            if (!dataStore.get(EnableDiscordRPCKey, true)) {
                if (DiscordPresenceManager.isRunning()) {
                    Timber.tag("MusicService").d("Discord RPC disabled → stopping presence manager")
                    try {
                        DiscordPresenceManager.stop()
                    } catch (_: Exception) {
                    }
                    lastPresenceToken = null
                }
                return@launch
            }

            val key: String = dataStore.get(DiscordTokenKey, "")
            if (key.isNullOrBlank()) {
                if (DiscordPresenceManager.isRunning()) {
                    Timber.tag("MusicService").d("No Discord OAuth session -> stopping presence manager")
                    try {
                        DiscordPresenceManager.stop()
                    } catch (_: Exception) {
                    }
                    lastPresenceToken = null
                }
                return@launch
            }

            if (DiscordPresenceManager.isRunning() && lastPresenceToken == key) {
                return@launch
            }

            try {
                DiscordPresenceManager.stop()
                DiscordPresenceManager.start(
                    context = this@MusicService,
                    token = key,
                )
                DiscordPresenceManager.setOnTransportInvalidated { reason ->
                    Timber.tag(DISCORD_SYNC_TAG).w(
                        "transport invalidated reason=%s; requesting forced sync",
                        reason,
                    )
                    requestDiscordSync(
                        reason = "transport_invalidated:$reason",
                        force = true,
                    )
                }
                Timber.tag("MusicService").d("Presence manager started")
                lastPresenceToken = key
                requestDiscordSync(
                    reason = "presence_manager_started",
                    force = true,
                )
            } catch (ex: Exception) {
                Timber.tag("MusicService").e(ex, "Failed to start presence manager")
            }
        }
    }

    private fun setupAudioFocusRequest() {
        audioFocusRequest =
            AudioFocusRequest
                .Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    android.media.AudioAttributes
                        .Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                ).setOnAudioFocusChangeListener { focusChange ->
                    handleAudioFocusChange(focusChange)
                }.setAcceptsDelayedFocusGain(true)
                .build()
    }

    private fun onAudioOutputDeviceChanged() {
        if (!::player.isInitialized) return
        val outputSignature = currentAudioOutputDeviceSignature()
        if (outputSignature == lastAudioOutputDeviceSignature) return
        lastAudioOutputDeviceSignature = outputSignature
        audioOutputResolver.refresh()
        cancelCrossfade(resetVolume = true, resetPauseAtEnd = true)
        player.setAudioAttributes(playbackAudioAttributes(), false)
        audioRouteRecoveryJob?.cancel()
        audioRouteRecoveryJob =
            scope.launch {
                delay(AUDIO_ROUTE_CHANGE_DEBOUNCE_MS)
                recoverAudioRouteAfterDeviceChange()
            }
    }

    private suspend fun recoverAudioRouteAfterDeviceChange() {
        if (!::player.isInitialized) return

        rebindAudioEffectsAfterRouteChange()

        if (!shouldRebuildPlaybackForAudioRouteChange()) return

        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastAudioRouteRecoveryRealtimeMs < AUDIO_ROUTE_RECOVERY_MIN_INTERVAL_MS) return
        lastAudioRouteRecoveryRealtimeMs = now

        val mediaItemIndex = player.currentMediaItemIndex.takeIf { it != C.INDEX_UNSET } ?: return
        val playbackPosition = player.currentPosition.coerceAtLeast(0L)
        val shouldResumePlayback = player.playWhenReady

        Timber.tag("MusicService").i(
            "Recovering audio route after output change at index=$mediaItemIndex position=$playbackPosition resume=$shouldResumePlayback",
        )

        if (shouldResumePlayback && !requestAudioFocus()) {
            wasPlayingBeforeAudioFocusLoss = true
            player.playWhenReady = false
            return
        }

        player.playWhenReady = false
        player.prepare()
        player.seekTo(mediaItemIndex, playbackPosition)
        delay(AUDIO_ROUTE_RECOVERY_RESUME_DELAY_MS)

        if (
            shouldResumePlayback &&
            player.currentMediaItem != null &&
            player.playbackState != Player.STATE_ENDED &&
            requestAudioFocus()
        ) {
            player.playWhenReady = true
        }
    }

    private suspend fun rebindAudioEffectsAfterRouteChange() {
        if (!isAudioEffectSessionOpened) return
        closeAudioEffectSession()
        if (!player.playWhenReady) return
        delay(AUDIO_EFFECT_ROUTE_REBIND_DELAY_MS)
        openAudioEffectSession()
    }

    private fun shouldRebuildPlaybackForAudioRouteChange(): Boolean {
        if (player.currentMediaItem == null) return false
        if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) return false
        return player.playWhenReady || player.playbackState == Player.STATE_BUFFERING
    }

    private fun currentAudioOutputDeviceSignature(): String =
        runCatching {
            audioManager
                .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .asSequence()
                .filter { it.isSink }
                .sortedWith(
                    compareBy<AudioDeviceInfo>(
                        { it.type },
                        { it.id },
                        { it.productName?.toString().orEmpty() },
                    ),
                ).joinToString(separator = "|") { device ->
                    "${device.type}:${device.id}:${device.productName?.toString().orEmpty()}"
                }
        }.getOrDefault("")

    private fun playbackAudioAttributes(): AudioAttributes =
        AudioAttributes
            .Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setAllowedCapturePolicy(C.ALLOW_CAPTURE_BY_ALL)
            .build()

    private fun calculateEffectivePlayerVolume(
        playerVolume: Float,
        normalizeFactor: Float,
        audioFocusVolumeFactor: Float,
    ): Float {
        val safePlayerVolume = playerVolume.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 1f
        val safeNormalizeFactor =
            normalizeFactor.takeIf { it.isFinite() }?.coerceIn(MIN_AUDIO_NORMALIZATION_FACTOR, MAX_AUDIO_NORMALIZATION_FACTOR) ?: 1f
        val safeAudioFocusVolumeFactor =
            audioFocusVolumeFactor.takeIf { it.isFinite() }?.coerceIn(MIN_AUDIO_FOCUS_VOLUME_FACTOR, 1f) ?: 1f
        return (safePlayerVolume * safeNormalizeFactor * safeAudioFocusVolumeFactor).coerceIn(0f, maxSafeGainFactor)
    }

    private fun currentEffectivePlayerVolume(): Float =
        calculateEffectivePlayerVolume(playerVolume.value, normalizeFactor.value, audioFocusVolumeFactor.value)

    private fun currentEffectivePlayerVolumeForMediaId(mediaId: String): Float {
        val targetNormalizeFactor =
            if (audioNormalizationEnabled) {
                audioNormalizationFactorCache[mediaId] ?: 1f
            } else {
                1f
            }
        return calculateEffectivePlayerVolume(playerVolume.value, targetNormalizeFactor, audioFocusVolumeFactor.value)
    }

    private fun updateEffectiveVolume(finalVolume: Float) {
        if (!::player.isInitialized || !shouldRampEffectiveVolume(finalVolume)) {
            applyEffectiveVolumeImmediately(finalVolume)
            return
        }

        val startVolume = player.volume.takeIf { it.isFinite() }?.coerceIn(0f, maxSafeGainFactor) ?: finalVolume
        val targetVolume = finalVolume.coerceIn(0f, maxSafeGainFactor)
        if (abs(targetVolume - startVolume) <= EFFECTIVE_VOLUME_RAMP_MIN_DELTA) {
            applyEffectiveVolumeImmediately(targetVolume)
            return
        }

        effectiveVolumeRampJob?.cancel()
        effectiveVolumeRampJob =
            scope.launch {
                val durationMs =
                    if (targetVolume > startVolume) {
                        EFFECTIVE_VOLUME_RAMP_UP_MS
                    } else {
                        EFFECTIVE_VOLUME_RAMP_DOWN_MS
                    }
                val startedAtMs = android.os.SystemClock.elapsedRealtime()
                while (isActive) {
                    val elapsedMs = android.os.SystemClock.elapsedRealtime() - startedAtMs
                    val progress = (elapsedMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                    val easedProgress = progress * progress * (3f - (2f * progress))
                    val interpolatedVolume = startVolume + ((targetVolume - startVolume) * easedProgress)
                    applyEffectiveVolume(interpolatedVolume)
                    if (progress >= 1f) break
                    delay(EFFECTIVE_VOLUME_RAMP_FRAME_MS)
                }
                applyEffectiveVolume(targetVolume)
                effectiveVolumeRampJob = null
            }
    }

    private fun shouldRampEffectiveVolume(finalVolume: Float): Boolean {
        if (isCrossfading || crossfadeHandoffInProgress) return false
        if (!shouldKeepPlaybackAudible()) return false
        if (!finalVolume.isFinite()) return false
        if (player.volume <= STUCK_MUTED_VOLUME_EPSILON) return false
        return true
    }

    private fun applyEffectiveVolumeImmediately(finalVolume: Float = currentEffectivePlayerVolume()) {
        effectiveVolumeRampJob?.cancel()
        effectiveVolumeRampJob = null
        applyEffectiveVolume(finalVolume)
    }

    private fun applyEffectiveVolume(finalVolume: Float = currentEffectivePlayerVolume()) {
        crossfadeBaseVolume = finalVolume
        val incomingPlayer = secondaryCrossfadePlayer
        if (crossfadeHandoffInProgress && incomingPlayer != null) {
            val handoffBaseVolume =
                secondaryCrossfadeTarget?.let { currentEffectivePlayerVolumeForMediaId(it.mediaId) }
                    ?: finalVolume
            crossfadeIncomingBaseVolume = handoffBaseVolume
            applyCrossfadeVolumes(
                crossfadeHandoffProgress,
                handoffBaseVolume,
                handoffBaseVolume,
                incomingPlayer,
                localPlayer,
            )
            return
        }
        if (isCrossfading && incomingPlayer != null) {
            val incomingBaseVolume =
                secondaryCrossfadeTarget?.let { currentEffectivePlayerVolumeForMediaId(it.mediaId) }
                    ?: finalVolume
            crossfadeIncomingBaseVolume = incomingBaseVolume
            applyCrossfadeVolumes(crossfadeProgress, finalVolume, incomingBaseVolume, localPlayer, incomingPlayer)
            return
        }
        if (::player.isInitialized) {
            player.volume = finalVolume
        }
        incomingPlayer?.volume = 0f
    }

    private fun ensureAudiblePlaybackVolume(reason: String) {
        if (!::player.isInitialized) return
        if (isCrossfading || crossfadeHandoffInProgress) return
        if (!shouldKeepPlaybackAudible()) return
        if (playerVolume.value <= 0f) return

        val expectedVolume = currentEffectivePlayerVolume()
        if (expectedVolume <= MIN_AUDIBLE_EFFECTIVE_VOLUME) return
        if (player.volume > STUCK_MUTED_VOLUME_EPSILON) return

        Timber.tag(TAG).w(
            "Restoring muted primary player volume during active playback: reason=%s expected=%s actual=%s",
            reason,
            expectedVolume,
            player.volume,
        )
        applyEffectiveVolumeImmediately(expectedVolume)
    }

    private fun updateAudiblePlaybackRecovery() {
        if (!::player.isInitialized || !shouldKeepPlaybackAudible()) {
            audiblePlaybackRecoveryJob?.cancel()
            audiblePlaybackRecoveryJob = null
            return
        }

        if (audiblePlaybackRecoveryJob?.isActive == true) return
        audiblePlaybackRecoveryJob =
            scope.launch {
                while (isActive && shouldKeepPlaybackAudible()) {
                    ensureAudiblePlaybackVolume("watchdog")
                    delay(AUDIBLE_PLAYBACK_VOLUME_CHECK_MS)
                }
                audiblePlaybackRecoveryJob = null
            }
    }

    private fun applyCrossfadeVolumes(
        progress: Float,
        outgoingBaseVolume: Float,
        incomingBaseVolume: Float,
        outgoingPlayer: ExoPlayer,
        incomingPlayer: ExoPlayer,
    ) {
        outgoingPlayer.volume = CrossfadePolicy.outgoingVolume(progress, outgoingBaseVolume, maxSafeGainFactor)
        incomingPlayer.volume = CrossfadePolicy.incomingVolume(progress, incomingBaseVolume, maxSafeGainFactor)
    }

    /** Crossfade is a local dual-player technique; it must not run while casting remotely. */
    private fun isCastSessionConnected(): Boolean {
        val state = castPlaybackRepository.screenState.value
        return (state as? CastScreenState.Success)?.uiState?.isConnected == true
    }

    fun pauseFromSleepTimer() {
        sleepTimer.clear()
        crossfadeTriggerJob?.cancel()
        crossfadeTriggerJob = null
        cancelCrossfade(resetVolume = true, resetPauseAtEnd = true)
        releaseSecondaryCrossfadePlayer()
        player.pause()
        player.playWhenReady = false
        localPlayer.pause()
        localPlayer.playWhenReady = false
    }

    private fun scheduleCrossfade() {
        if (!::player.isInitialized) return
        crossfadeTriggerJob?.cancel()
        crossfadeTriggerJob = null

        if (isCrossfading) return
        if (sourceSwitchPending) return
        if (isCastSessionConnected()) {
            localPlayer.pauseAtEndOfMediaItems = false
            releaseSecondaryCrossfadePlayer()
            return
        }
        if (!player.playWhenReady || sleepTimer.pauseWhenSongEnd) {
            localPlayer.pauseAtEndOfMediaItems = false
            releaseSecondaryCrossfadePlayer()
            return
        }

        val target = resolveCrossfadeTarget()
        val duration = player.duration
        val effectiveDuration = effectiveCrossfadeDuration(duration)
        if (target == null || effectiveDuration == null) {
            localPlayer.pauseAtEndOfMediaItems = false
            releaseSecondaryCrossfadePlayer()
            return
        }

        val currentMediaId = player.currentMediaItem?.mediaId ?: return
        val currentIndex = player.currentMediaItemIndex
        val triggerAt = duration - effectiveDuration - CROSSFADE_END_GUARD_MS

        crossfadeTriggerJob =
            scope.launch {
                var hasPreparedSecondaryPlayer = false
                while (isActive) {
                    if (!crossfadeEnabled || isCrossfading) return@launch
                    if (player.currentMediaItem?.mediaId != currentMediaId || player.currentMediaItemIndex != currentIndex) {
                        return@launch
                    }
                    if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
                        return@launch
                    }

                    val remainingToTrigger = triggerAt - player.currentPosition
                    if (!hasPreparedSecondaryPlayer && remainingToTrigger <= CROSSFADE_PREPARE_AHEAD_MS) {
                        prepareSecondaryCrossfadePlayer(target)
                        hasPreparedSecondaryPlayer = true
                    }
                    if (remainingToTrigger <= 0L) {
                        val adjustedDuration =
                            (duration - player.currentPosition - CROSSFADE_END_GUARD_MS)
                                .coerceAtMost(effectiveDuration)
                        if (adjustedDuration >= MIN_CROSSFADE_DURATION_MS) {
                            startCrossfade(target, adjustedDuration)
                        }
                        return@launch
                    }

                    val sleepMs =
                        when {
                            remainingToTrigger > 5_000L -> 1_000L
                            remainingToTrigger > 1_000L -> 250L
                            else -> 50L
                        }.coerceAtMost(remainingToTrigger).coerceAtLeast(1L)
                    delay(sleepMs)
                }
            }
    }

    private fun resolveCrossfadeTarget(): CrossfadeTarget? {
        if (!crossfadeEnabled || crossfadeDurationMs <= 0L) return null
        if (player.mediaItemCount == 0 || player.currentTimeline.isEmpty) return null
        if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) return null

        val currentIndex = player.currentMediaItemIndex
        if (currentIndex !in 0 until player.mediaItemCount) return null

        val repeatCurrent = player.repeatMode == REPEAT_MODE_ONE
        val targetIndex =
            CrossfadePolicy.resolveTargetIndex(
                repeatOne = repeatCurrent,
                currentIndex = currentIndex,
                nextIndex = player.nextMediaItemIndex,
                itemCount = player.mediaItemCount,
                unsetIndex = C.INDEX_UNSET,
            ) ?: return null

        val currentItem = player.getMediaItemAt(currentIndex)
        val targetItem = player.getMediaItemAt(targetIndex)
        if (!repeatCurrent && crossfadeGapless && isGaplessAlbumTransition(currentItem, targetItem)) return null

        return CrossfadeTarget(
            index = targetIndex,
            mediaId = targetItem.mediaId,
        )
    }

    private fun effectiveCrossfadeDuration(duration: Long): Long? =
        CrossfadePolicy.effectiveDurationMs(
            requestedMs = crossfadeDurationMs,
            trackDurationMs = duration,
            endGuardMs = CROSSFADE_END_GUARD_MS,
            minDurationMs = MIN_CROSSFADE_DURATION_MS,
            unsetTime = C.TIME_UNSET,
        )

    private fun isGaplessAlbumTransition(
        currentItem: MediaItem,
        targetItem: MediaItem,
    ): Boolean {
        val currentAlbum =
            currentItem.metadata
                ?.album
                ?.id
                ?.takeIf { it.isNotBlank() }
                ?: currentItem.metadata
                    ?.album
                    ?.title
                    ?.takeIf { it.isNotBlank() }
                ?: currentItem.mediaMetadata.albumTitle
                    ?.toString()
                    ?.takeIf { it.isNotBlank() }
        val targetAlbum =
            targetItem.metadata
                ?.album
                ?.id
                ?.takeIf { it.isNotBlank() }
                ?: targetItem.metadata
                    ?.album
                    ?.title
                    ?.takeIf { it.isNotBlank() }
                ?: targetItem.mediaMetadata.albumTitle
                    ?.toString()
                    ?.takeIf { it.isNotBlank() }
        return currentAlbum != null && currentAlbum == targetAlbum
    }

    private fun prepareSecondaryCrossfadePlayer(target: CrossfadeTarget): ExoPlayer? {
        val existingPlayer = secondaryCrossfadePlayer
        if (existingPlayer != null && secondaryCrossfadeTarget == target) {
            return existingPlayer
        }

        releaseSecondaryCrossfadePlayer()

        runCatching { player.getMediaItemAt(target.index) }
            .getOrNull()
            ?.takeIf { it.mediaId == target.mediaId }
            ?: return null

        return runCatching {
            createSecondaryCrossfadePlayer().also { secondaryPlayer ->
                secondaryCrossfadePlayer = secondaryPlayer
                secondaryCrossfadeTarget = target
                // The incoming player must be promotion-capable: it receives the FULL queue
                // (not just the target item) plus the repeat/shuffle/speed state, so it can
                // become the active player at the end of the fade without any re-buffering.
                val items = (0 until player.mediaItemCount).map { player.getMediaItemAt(it) }
                secondaryPlayer.setMediaItems(items, target.index, 0L)
                secondaryPlayer.repeatMode = player.repeatMode
                secondaryPlayer.shuffleModeEnabled = player.shuffleModeEnabled
                secondaryPlayer.playbackParameters = player.playbackParameters
                secondaryPlayer.volume = 0f
                secondaryPlayer.prepare()
            }
        }.onFailure { error ->
            Timber.tag(TAG).w(error, "Failed to prepare crossfade player")
            releaseSecondaryCrossfadePlayer()
        }.getOrNull()
    }

    private fun createSecondaryCrossfadePlayer(): ExoPlayer =
        ExoPlayer
            .Builder(this)
            .setMediaSourceFactory(createMediaSourceFactory())
            .setRenderersFactory(createRenderersFactory())
            .setLoadControl(createCrossfadeLoadControl())
            .setTrackSelector(DefaultTrackSelector(this, SafeTrackSelectionFactory()))
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setAudioAttributes(playbackAudioAttributes(), false)
            .setSeekBackIncrementMs(5000)
            .setSeekForwardIncrementMs(5000)
            .setDeviceVolumeControlEnabled(true)
            .build()
            .apply {
                addListener(secondaryCrossfadeListener)
                setOffloadEnabled(false)
                skipSilenceEnabled = localPlayer.skipSilenceEnabled
            }

    private fun startCrossfade(
        target: CrossfadeTarget,
        durationMs: Long,
    ) {
        if (isCrossfading || !crossfadeEnabled) return

        val incomingPlayer = prepareSecondaryCrossfadePlayer(target) ?: return
        val outgoingMediaId = player.currentMediaItem?.mediaId ?: return
        val generation = crossfadeGeneration.incrementAndGet()

        crossfadeTriggerJob?.cancel()
        crossfadeTriggerJob = null
        crossfadeJob?.cancel()
        crossfadeJob =
            scope.launch {
                isCrossfading = true
                crossfadeProgress = 0f
                crossfadeBaseVolume = currentEffectivePlayerVolume()
                crossfadeIncomingBaseVolume = currentEffectivePlayerVolumeForMediaId(target.mediaId)
                crossfadePlaybackRequested = player.playWhenReady
                localPlayer.pauseAtEndOfMediaItems = true

                Timber.tag(TAG).d(
                    "crossfade[%d] start outgoing=%s incoming=%s durationMs=%d",
                    generation,
                    outgoingMediaId,
                    target.mediaId,
                    durationMs,
                )

                try {
                    val requiredBufferedMs = requiredCrossfadeStartBufferMs(durationMs)
                    if (!awaitCrossfadePlayerReady(incomingPlayer, CROSSFADE_READY_TIMEOUT_MS, requiredBufferedMs)) {
                        Timber.tag(TAG).d("crossfade[%d] incoming player not ready in time; aborting", generation)
                        cancelCrossfade(resetVolume = true, resetPauseAtEnd = true)
                        scheduleCrossfade()
                        return@launch
                    }

                    incomingPlayer.playbackParameters = player.playbackParameters
                    incomingPlayer.playWhenReady = crossfadePlaybackRequested
                    if (crossfadePlaybackRequested) {
                        incomingPlayer.play()
                        // STATE_READY + buffering do not prove that post-seek audio samples are
                        // advancing through the audio output. Require the play position to move.
                        if (!awaitAudioPositionAdvancement(incomingPlayer, CROSSFADE_AUDIO_ADVANCE_TIMEOUT_MS)) {
                            Timber.tag(TAG).w("crossfade[%d] no audio advancement on incoming player; aborting", generation)
                            cancelCrossfade(resetVolume = true, resetPauseAtEnd = true)
                            scheduleCrossfade()
                            return@launch
                        }
                    }

                    var elapsedMs = 0L
                    var lastTickMs = android.os.SystemClock.elapsedRealtime()
                    while (isActive && elapsedMs < durationMs) {
                        if (player.currentMediaItem?.mediaId != outgoingMediaId) {
                            cancelCrossfade(resetVolume = true, resetPauseAtEnd = true)
                            return@launch
                        }

                        val nowMs = android.os.SystemClock.elapsedRealtime()
                        if (crossfadePlaybackRequested) {
                            incomingPlayer.playWhenReady = true
                            elapsedMs = (elapsedMs + (nowMs - lastTickMs)).coerceAtMost(durationMs)
                            crossfadeProgress = (elapsedMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                            applyCrossfadeVolumes(
                                crossfadeProgress,
                                crossfadeBaseVolume,
                                crossfadeIncomingBaseVolume,
                                localPlayer,
                                incomingPlayer,
                            )
                        } else {
                            incomingPlayer.pause()
                        }
                        lastTickMs = nowMs
                        delay(CROSSFADE_FRAME_MS)
                    }

                    finishCrossfade(target, incomingPlayer, generation)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Timber.tag(TAG).w(error, "Crossfade failed")
                    cancelCrossfade(resetVolume = true, resetPauseAtEnd = true)
                }
            }
    }

    private suspend fun awaitCrossfadePlayerReady(
        crossfadePlayer: ExoPlayer,
        timeoutMs: Long,
        minimumBufferedMs: Long,
    ): Boolean {
        val deadlineMs = android.os.SystemClock.elapsedRealtime() + timeoutMs
        while (kotlinx.coroutines.currentCoroutineContext().isActive && android.os.SystemClock.elapsedRealtime() < deadlineMs) {
            val snapshot =
                CrossfadePolicy.ReadinessSnapshot(
                    isReady = crossfadePlayer.playbackState == Player.STATE_READY,
                    isIdle = crossfadePlayer.playbackState == Player.STATE_IDLE,
                    isEnded = crossfadePlayer.playbackState == Player.STATE_ENDED,
                    hasError = crossfadePlayer.playerError != null,
                    bufferedEnough = hasBufferedForSmoothStart(crossfadePlayer, minimumBufferedMs),
                )
            when {
                CrossfadePolicy.mustAbortReadiness(snapshot) -> return false
                CrossfadePolicy.isReadyForFadeStart(snapshot) -> return true
                snapshot.isIdle -> crossfadePlayer.prepare()
            }
            delay(50L)
        }
        return crossfadePlayer.playbackState == Player.STATE_READY &&
            crossfadePlayer.playerError == null &&
            hasBufferedForSmoothStart(crossfadePlayer, minimumBufferedMs)
    }

    /**
     * Waits for proof that audio is actually advancing through the output: STATE_READY +
     * isPlaying + the play position moving between polls. Buffering and readiness alone do
     * not prove post-seek samples are being rendered.
     */
    private suspend fun awaitAudioPositionAdvancement(
        targetPlayer: ExoPlayer,
        timeoutMs: Long,
    ): Boolean {
        val deadlineMs = android.os.SystemClock.elapsedRealtime() + timeoutMs
        var lastPositionMs = targetPlayer.currentPosition
        while (kotlinx.coroutines.currentCoroutineContext().isActive && android.os.SystemClock.elapsedRealtime() < deadlineMs) {
            if (targetPlayer.playerError != null) return false
            delay(CROSSFADE_AUDIO_ADVANCE_POLL_MS)
            val positionMs = targetPlayer.currentPosition
            if (CrossfadePolicy.hasAudioAdvanced(
                    CrossfadePolicy.AudioAdvancementSnapshot(
                        isReady = targetPlayer.playbackState == Player.STATE_READY,
                        isPlaying = targetPlayer.isPlaying,
                        hasError = targetPlayer.playerError != null,
                        positionMs = positionMs,
                        previousPositionMs = lastPositionMs,
                    ),
                )
            ) {
                return true
            }
            lastPositionMs = positionMs
        }
        return false
    }

    /**
     * Completes a crossfade by PROMOTING the already-playing incoming player to be the
     * active player. The old outgoing player is the only one released; the incoming player
     * keeps its decoder, buffered data, audio sink and playback clock.
     */
    private suspend fun finishCrossfade(
        target: CrossfadeTarget,
        incomingPlayer: ExoPlayer,
        generation: Long,
    ) {
        // A cancelled/superseded crossfade must never complete an older handoff.
        if (generation != crossfadeGeneration.get()) {
            Timber.tag(TAG).d("crossfade[%d] stale generation at promotion; ignoring", generation)
            return
        }
        val targetIndex = resolveCrossfadeTargetIndex(target, incomingPlayer)
        val incomingUsable =
            CrossfadePolicy.mayPromote(
                CrossfadePolicy.PromotionSnapshot(
                    generationMatches = true,
                    targetIndex = targetIndex,
                    hasError = incomingPlayer.playerError != null,
                    isIdle = incomingPlayer.playbackState == Player.STATE_IDLE,
                    isEnded = incomingPlayer.playbackState == Player.STATE_ENDED,
                    unsetIndex = C.INDEX_UNSET,
                ),
            )
        if (!incomingUsable) {
            // Never continue a destructive handoff when the incoming player is not valid:
            // the outgoing player remains authoritative and keeps playing.
            Timber.tag(TAG).w("crossfade[%d] incoming player unusable at promotion; keeping outgoing", generation)
            cancelCrossfade(resetVolume = true, resetPauseAtEnd = true)
            return
        }

        Timber.tag(TAG).d(
            "crossfade[%d] promotion start outgoing=%s incoming=%s",
            generation,
            player.currentMediaItem?.mediaId,
            target.mediaId,
        )
        val promoted = promoteIncomingCrossfadePlayer(target, incomingPlayer, targetIndex)
        if (!promoted) {
            cancelCrossfade(resetVolume = true, resetPauseAtEnd = true)
            return
        }

        isCrossfading = false
        crossfadeHandoffInProgress = false
        crossfadeHandoffProgress = 0f
        crossfadeProgress = 0f
        crossfadeIncomingBaseVolume = 1f
        crossfadePlaybackRequested = false
        // Restore the configured final volume on the promoted player.
        applyEffectiveVolumeImmediately()
        updateAudiblePlaybackRecovery()
        scheduleCrossfade()
        Timber.tag(TAG).d("crossfade[%d] promotion success; active mediaId=%s", generation, player.currentMediaItem?.mediaId)
    }

    /**
     * Promotes [incomingPlayer] to active-player ownership: it becomes `localPlayer`, the
     * session-facing player is rebuilt/re-pointed, listeners, MediaSession, Cast delegation,
     * queue/metadata state and audio effects all follow the promoted player, and only the old
     * outgoing player is faded out and released.
     */
    private fun promoteIncomingCrossfadePlayer(
        target: CrossfadeTarget,
        incomingPlayer: ExoPlayer,
        targetIndex: Int,
    ): Boolean {
        val outgoingPlayer = localPlayer
        val oldSessionPlayer = player
        // Whether playback should still be running once ownership has moved. Captured BEFORE the
        // session player is rebuilt below: that rebuild constructs a fresh wrapper around the
        // promoted ExoPlayer, and a fresh wrapper does not inherit playWhenReady. Nothing used to
        // re-assert it, so the promoted player came up paused and the incoming song stopped a few
        // seconds in — right at the end of the fade, which is what made it look random.
        val shouldKeepPlaying = crossfadePlaybackRequested || incomingPlayer.playWhenReady || oldSessionPlayer.playWhenReady
        crossfadeHandoffInProgress = true
        return try {
            incomingPlayer.removeListener(secondaryCrossfadeListener)
            incomingPlayer.pauseAtEndOfMediaItems = false

            // 1. Ownership: the incoming player becomes the active local player.
            localPlayer = incomingPlayer
            secondaryCrossfadePlayer = null
            secondaryCrossfadeTarget = null

            // The incoming player's queue was snapshotted at prepare time; items appended to
            // the live queue since then (e.g. auto-load-more) must follow the promoted player.
            val incomingItemIds =
                (0 until incomingPlayer.mediaItemCount)
                    .mapTo(HashSet()) { incomingPlayer.getMediaItemAt(it).mediaId }
            val missingItems =
                (0 until oldSessionPlayer.mediaItemCount)
                    .map { oldSessionPlayer.getMediaItemAt(it) }
                    .filter { it.mediaId !in incomingItemIds }
            if (missingItems.isNotEmpty()) {
                incomingPlayer.addMediaItems(missingItems)
            }

            // 2. Session-facing player + Cast delegation. CastPlayer pins its local player at
            // build time, so the wrapper must be rebuilt around the promoted player; on foss
            // builds the session player simply IS the new local player.
            oldSessionPlayer.removeListener(this@MusicService)
            runCatching { oldSessionPlayer.removeListener(sleepTimer) }
            player =
                castPlaybackRepository
                    .createPlayer(
                        context = this,
                        localPlayer = localPlayer,
                        mediaItemResolver = CastMediaItemResolver(::resolveMediaItemForCast),
                    ).apply {
                        addListener(this@MusicService)
                    }
            sleepTimer.player = player
            player.addListener(sleepTimer)
            castPlaybackRepository.releasePlayer(oldSessionPlayer)

            // 3. Listeners/analytics that were attached to the old local player.
            runCatching { outgoingPlayer.removeListener(audioEffectPlayerListener) }
            incomingPlayer.addListener(audioEffectPlayerListener)
            incomingPlayer.addAnalyticsListener(PlaybackStatsListener(false, this@MusicService))

            // 4. MediaSession + observable player state.
            mediaSession.player = player
            _playerFlow.value = player

            // The rebuilt wrapper starts with playWhenReady=false regardless of what the promoted
            // ExoPlayer was doing, so the intent captured before the rebuild is re-applied here —
            // before the metadata replay below, so listeners never observe a spurious pause.
            if (shouldKeepPlaying && !player.playWhenReady) {
                player.playWhenReady = true
            }

            // 5. Audio effects follow the promoted player's audio session.
            rebindAudioEffectSession(localPlayer.audioSessionId)

            // 6. Queue/metadata state. The incoming player's transition into the target item
            // happened before it was the service player, so the bookkeeping is replayed
            // manually now that ownership has moved.
            val promotedItem = incomingPlayer.getMediaItemAt(targetIndex)
            currentMediaMetadata.value = promotedItem.metadata
            onMediaItemTransition(promotedItem, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)

            // 7. Fade, stop and release ONLY the old outgoing player.
            releaseOutgoingCrossfadePlayer(outgoingPlayer)
            true
        } catch (error: Throwable) {
            Timber.tag(TAG).w(error, "Crossfade promotion failed; incoming player stays active if it is the only audible one")
            // Failure safety: never release the only player that can produce valid audio.
            if (localPlayer !== incomingPlayer) {
                // Promotion did not complete ownership transfer: the outgoing player remains
                // authoritative; detach the incoming one quietly.
                runCatching { incomingPlayer.stop() }
                runCatching { incomingPlayer.release() }
                secondaryCrossfadePlayer = null
                secondaryCrossfadeTarget = null
            }
            false
        } finally {
            crossfadeHandoffInProgress = false
        }
    }

    private fun releaseOutgoingCrossfadePlayer(outgoingPlayer: ExoPlayer) {
        runCatching { outgoingPlayer.volume = 0f }
        runCatching { outgoingPlayer.stop() }
        runCatching { outgoingPlayer.clearMediaItems() }
        runCatching { outgoingPlayer.release() }
    }

    private fun resolveCrossfadeTargetIndex(
        target: CrossfadeTarget,
        targetPlayer: Player,
    ): Int {
        if (target.index in 0 until targetPlayer.mediaItemCount &&
            targetPlayer.getMediaItemAt(target.index).mediaId == target.mediaId
        ) {
            return target.index
        }

        for (index in 0 until targetPlayer.mediaItemCount) {
            if (targetPlayer.getMediaItemAt(index).mediaId == target.mediaId) {
                return index
            }
        }
        return C.INDEX_UNSET
    }

    private fun requiredCrossfadeStartBufferMs(durationMs: Long): Long =
        (durationMs + CROSSFADE_HANDOFF_BUFFER_MS)
            .coerceAtLeast(CROSSFADE_MIN_BUFFER_BEFORE_START_MS)
            .coerceAtMost(CROSSFADE_MAX_BUFFER_BEFORE_START_MS)

    private fun hasBufferedForSmoothStart(
        targetPlayer: ExoPlayer,
        minimumBufferedMs: Long,
    ): Boolean {
        if (minimumBufferedMs <= 0L) return true
        if (targetPlayer.currentMediaItem
                ?.localConfiguration
                ?.uri
                ?.shouldBypassPlayerCache() == true
        ) {
            return true
        }

        val duration = targetPlayer.duration
        val currentPosition = targetPlayer.currentPosition.coerceAtLeast(0L)
        val remainingDuration =
            if (duration != C.TIME_UNSET && duration > currentPosition) {
                duration - currentPosition
            } else {
                Long.MAX_VALUE
            }
        val requiredBufferedMs = minimumBufferedMs.coerceAtMost(remainingDuration)
        if (requiredBufferedMs <= 0L) return true

        val bufferedDuration = targetPlayer.totalBufferedDuration.coerceAtLeast(0L)
        if (bufferedDuration >= requiredBufferedMs) return true

        return duration != C.TIME_UNSET &&
            targetPlayer.bufferedPosition >= duration - CROSSFADE_END_GUARD_MS
    }

    /**
     * Fully tears down any in-flight crossfade before a user-initiated skip (next/previous).
     *
     * Without this, skipping during an active crossfade or handoff leaves the service in a broken
     * state: [isCrossfading] stays true (so [onMediaItemTransition] stops rescheduling crossfades),
     * the primary player stays muted (volume 0), [PrimaryPlayer.pauseAtEndOfMediaItems] stays true,
     * and the orphaned secondary [ExoPlayer] keeps playing — causing double audio, silent playback,
     * and a leaked player that can crash the next crossfade. Must be called on the main thread.
     */
    fun prepareForManualSkip() {
        if (!::player.isInitialized) return
        if (!isCrossfading && !crossfadeHandoffInProgress && secondaryCrossfadePlayer == null) return
        cancelCrossfade(resetVolume = true, resetPauseAtEnd = true)
    }

    private fun cancelCrossfade(
        resetVolume: Boolean,
        resetPauseAtEnd: Boolean,
    ) {
        // Invalidate any in-flight operation so a cancelled crossfade coroutine can never
        // complete an older handoff later.
        crossfadeGeneration.incrementAndGet()
        crossfadeTriggerJob?.cancel()
        crossfadeTriggerJob = null
        crossfadeJob?.cancel()
        crossfadeJob = null
        isCrossfading = false
        crossfadeHandoffInProgress = false
        crossfadeHandoffProgress = 0f
        crossfadeProgress = 0f
        crossfadeIncomingBaseVolume = 1f
        crossfadePlaybackRequested = false
        if (::player.isInitialized && resetPauseAtEnd) {
            localPlayer.pauseAtEndOfMediaItems = false
        }
        releaseSecondaryCrossfadePlayer()
        if (resetVolume && ::player.isInitialized) {
            applyEffectiveVolumeImmediately()
        }
    }

    private fun releaseSecondaryCrossfadePlayer() {
        val playerToRelease = secondaryCrossfadePlayer ?: return
        secondaryCrossfadePlayer = null
        secondaryCrossfadeTarget = null
        runCatching { playerToRelease.removeListener(secondaryCrossfadeListener) }
        runCatching { playerToRelease.stop() }
        runCatching { playerToRelease.clearMediaItems() }
        runCatching { playerToRelease.release() }
    }

    private fun calculateAudioNormalizationFactor(
        format: FormatEntity?,
        normalizeAudio: Boolean,
    ): Float {
        Timber.tag("AudioNormalization").d("Audio normalization enabled: $normalizeAudio")
        Timber
            .tag(
                "AudioNormalization",
            ).d("Format loudnessDb: ${format?.loudnessDb}, perceptualLoudnessDb: ${format?.perceptualLoudnessDb}")

        if (!normalizeAudio) {
            Timber.tag("AudioNormalization").d("Normalization disabled - using factor 1.0")
            return 1f
        }

        val loudnessDb = format?.normalizationLoudnessDb()
        if (loudnessDb == null || !loudnessDb.isFinite()) {
            // Downgraded from WARN to DEBUG: this is the expected code path for
            // any non-YouTube source (Tidal, Qobuz, Deezer, JioSaavn, Telegram,
            // local files) because FormatEntity only carries loudness metadata
            // for YouTube streams — for everything else the field is hardcoded
            // to null at the FormatEntity creation site (see the comment at
            // `resolveAudioNormalizationFactor` for the rationale). Logging this
            // at WARN produced 5+ noisy entries per playback session in
            // production logs for every Qobuz track, making it harder to spot
            // real warnings.
            Timber.tag("AudioNormalization").d("Normalization enabled but no valid loudness data available - no normalization applied")
            return 1f
        }

        val rawFactor = 10f.pow(-loudnessDb / 20)
        val factor =
            if (rawFactor.isFinite()) {
                rawFactor.coerceIn(MIN_AUDIO_NORMALIZATION_FACTOR, MAX_AUDIO_NORMALIZATION_FACTOR)
            } else {
                1f
            }

        if (factor != rawFactor) {
            Timber.tag("AudioNormalization").d("Normalization factor clamped from $rawFactor to $factor")
        }
        Timber.tag("AudioNormalization").i("Applying normalization factor: $factor")
        return factor
    }

    private fun resolveAudioNormalizationFactor(
        mediaId: String?,
        format: FormatEntity?,
        normalizeAudio: Boolean,
    ): Float {
        val currentMediaId = mediaId?.takeIf { it.isNotBlank() } ?: return 1f
        if (!normalizeAudio) {
            return 1f
        }

        // A Tidal-served track has no matching loudness metadata (the stored format describes the
        // YouTube stream), so applying it would set the wrong volume. Skip normalization for it.
        if (currentMediaId in tidalActiveMediaIds) {
            audioNormalizationFactorCache[currentMediaId] = 1f
            return 1f
        }

        if (format?.id == currentMediaId) {
            val factor = calculateAudioNormalizationFactor(format, normalizeAudio = true)
            audioNormalizationFactorCache[currentMediaId] = factor
            return factor
        }

        return audioNormalizationFactorCache[currentMediaId] ?: 1f
    }

    private fun FormatEntity.normalizationLoudnessDb(): Float? =
        sequenceOf(perceptualLoudnessDb, loudnessDb)
            .mapNotNull { it?.toFloat() }
            .firstOrNull { it.isFinite() }

    private fun shouldKeepPlaybackAudible(): Boolean {
        if (!::player.isInitialized) return false
        if (player.currentMediaItem == null || !player.playWhenReady) return false
        return player.playbackState != Player.STATE_IDLE && player.playbackState != Player.STATE_ENDED
    }

    private fun restoreAudioFocusVolume() {
        audioFocusVolumeFactor.value = 1f
        hasAudioFocus = true
        lastAudioFocusState = AudioManager.AUDIOFOCUS_GAIN
    }

    private fun pauseForAudioFocusLoss(resumeWhenFocusReturns: Boolean) {
        audioFocusVolumeFactor.value = 1f
        wasPlayingBeforeAudioFocusLoss = resumeWhenFocusReturns && player.playWhenReady
        if (player.playWhenReady) {
            player.pause()
        }
    }

    private fun ensureAudioFocusForActivePlayback(): Boolean {
        if (!player.playWhenReady) return true
        if (requestAudioFocus()) return true
        pauseForAudioFocusLoss(resumeWhenFocusReturns = true)
        return false
    }

    private fun handleAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true
                audioFocusVolumeFactor.value = 1f

                if (wasPlayingBeforeAudioFocusLoss) {
                    player.play()
                    wasPlayingBeforeAudioFocusLoss = false
                }

                lastAudioFocusState = focusChange
            }

            AudioManager.AUDIOFOCUS_LOSS -> {
                hasAudioFocus = false
                pauseForAudioFocusLoss(resumeWhenFocusReturns = false)

                abandonAudioFocus()

                lastAudioFocusState = focusChange
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                hasAudioFocus = false
                pauseForAudioFocusLoss(resumeWhenFocusReturns = true)

                lastAudioFocusState = focusChange
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                hasAudioFocus = false
                pauseForAudioFocusLoss(resumeWhenFocusReturns = true)

                lastAudioFocusState = focusChange
            }

            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT -> {
                hasAudioFocus = true
                audioFocusVolumeFactor.value = 1f

                if (wasPlayingBeforeAudioFocusLoss) {
                    player.play()
                    wasPlayingBeforeAudioFocusLoss = false
                }

                lastAudioFocusState = focusChange
            }

            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK -> {
                hasAudioFocus = true
                audioFocusVolumeFactor.value = 1f

                lastAudioFocusState = focusChange
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        if (hasAudioFocus) {
            if (audioFocusVolumeFactor.value != 1f || lastAudioFocusState == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                restoreAudioFocusVolume()
            }
            return true
        }

        audioFocusRequest?.let { request ->
            val result = audioManager.requestAudioFocus(request)
            hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            if (hasAudioFocus) {
                restoreAudioFocusVolume()
            }
            return hasAudioFocus
        }
        return false
    }

    private fun abandonAudioFocus() {
        if (hasAudioFocus) {
            audioFocusRequest?.let { request ->
                audioManager.abandonAudioFocusRequest(request)
                hasAudioFocus = false
            }
        }
    }

    fun hasAudioFocusForPlayback(): Boolean = hasAudioFocus

    private fun isDeviceMutedNow(): Boolean {
        val streamVolume =
            runCatching {
                audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            }.getOrElse { error ->
                reportException(error)
                return player.isDeviceMuted || player.deviceVolume <= 0
            }
        val isStreamMuted =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                runCatching {
                    audioManager.isStreamMute(AudioManager.STREAM_MUSIC)
                }.getOrElse { error ->
                    reportException(error)
                    false
                }

        return isStreamMuted || streamVolume <= 0
    }

    private fun isTogetherGuestSession(): Boolean {
        val joined = togetherSessionState.value as? moe.rukamori.archivetune.together.TogetherSessionState.Joined
        return joined?.role is moe.rukamori.archivetune.together.TogetherRole.Guest
    }

    private fun registerMuteRecoveryObserver() {
        if (muteRecoveryObserver != null) return
        val observer =
            object : ContentObserver(Handler(mainLooper)) {
                override fun onChange(selfChange: Boolean) {
                    if (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) > 0) {
                        handleDeviceMuteStateChanged()
                    }
                }
            }
        contentResolver.registerContentObserver(
            android.provider.Settings.System.CONTENT_URI,
            true,
            observer,
        )
        muteRecoveryObserver = observer
    }

    private fun unregisterMuteRecoveryObserver() {
        muteRecoveryObserver?.let { contentResolver.unregisterContentObserver(it) }
        muteRecoveryObserver = null
    }

    private fun handleDeviceMuteStateChanged(playbackRequestedWhileMuted: Boolean = false) {
        if (!pauseOnDeviceMuteEnabled || isTogetherGuestSession()) {
            wasAutoPausedByDeviceMute = false
            unregisterMuteRecoveryObserver()
            return
        }

        if (isDeviceMutedNow()) {
            if (playbackRequestedWhileMuted && restoreDeviceMusicVolumeForPlayback()) {
                wasAutoPausedByDeviceMute = false
                unregisterMuteRecoveryObserver()
                return
            }

            val canPauseNow =
                player.currentMediaItem != null &&
                    player.playWhenReady &&
                    player.playbackState != Player.STATE_IDLE &&
                    player.playbackState != Player.STATE_ENDED

            if (canPauseNow) {
                player.pause()
                wasAutoPausedByDeviceMute = true
                registerMuteRecoveryObserver()
                if (playbackRequestedWhileMuted) {
                    showDeviceMutePlaybackNotice()
                }
            }
            return
        }

        unregisterMuteRecoveryObserver()

        if (!wasAutoPausedByDeviceMute) return

        wasAutoPausedByDeviceMute = false
        val canResumeNow =
            player.currentMediaItem != null &&
                player.playbackState != Player.STATE_IDLE &&
                player.playbackState != Player.STATE_ENDED
        if (canResumeNow) {
            player.play()
        }
    }

    private fun restoreDeviceMusicVolumeForPlayback(): Boolean {
        val recoveryPercent = deviceMutePlaybackRecoveryVolumePercent.coerceIn(0, 100)
        if (recoveryPercent <= 0) return false

        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (maxVolume <= 0) return false

        val targetVolume =
            ceil(maxVolume * (recoveryPercent / 100.0))
                .toInt()
                .coerceIn(1, maxVolume)

        return runCatching {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) > 0
        }.getOrElse {
            reportException(it)
            false
        }
    }

    private fun showDeviceMutePlaybackNotice() {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastDeviceMutePlaybackNoticeAtElapsedMs < DEVICE_MUTE_PLAYBACK_NOTICE_INTERVAL_MS) return
        lastDeviceMutePlaybackNoticeAtElapsedMs = now
        scope.launch(SilentHandler) {
            Toast
                .makeText(
                    this@MusicService,
                    R.string.device_volume_zero_playback_paused,
                    Toast.LENGTH_SHORT,
                ).show()
        }
    }

    private val bluetoothReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                if (intent.action != BluetoothDevice.ACTION_ACL_CONNECTED) return
                if (!autoStartOnBluetoothEnabled) return

                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return

                val isAudioDevice =
                    try {
                        val majorClass = device.bluetoothClass?.majorDeviceClass
                        majorClass == BluetoothClass.Device.Major.AUDIO_VIDEO ||
                            majorClass == BluetoothClass.Device.Major.WEARABLE
                    } catch (_: SecurityException) {
                        true
                    }

                if (!isAudioDevice) return

                scope.launch {
                    delay(1500)
                    handleBluetoothAutoStart()
                }
            }
        }

    private fun handleBluetoothAutoStart() {
        if (isTogetherGuestSession()) return

        if (player.currentMediaItem != null &&
            player.playbackState != Player.STATE_IDLE &&
            player.playbackState != Player.STATE_ENDED
        ) {
            if (!player.playWhenReady) {
                player.play()
            }
            return
        }

        if (player.mediaItemCount > 0) {
            player.prepare()
            player.play()
        }
    }

    @Suppress("DEPRECATION")
    private fun registerBluetoothReceiver() {
        if (bluetoothReceiverRegistered) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val filter = IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bluetoothReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(bluetoothReceiver, filter)
        }
        bluetoothReceiverRegistered = true
    }

    private fun unregisterBluetoothReceiver() {
        if (!bluetoothReceiverRegistered) return
        try {
            unregisterReceiver(bluetoothReceiver)
        } catch (_: Exception) {
        }
        bluetoothReceiverRegistered = false
    }

    private fun waitOnNetworkError() {
        waitingForNetworkConnection.value = true
    }

    private fun skipOnError() {
        /**
         * Auto skip to the next media item on error.
         *
         * To prevent a "runaway diesel engine" scenario, force the user to take action after
         * too many errors come up too quickly. Pause to show player "stopped" state
         */
        consecutivePlaybackErr += 2
        val nextWindowIndex = player.nextMediaItemIndex

        if (consecutivePlaybackErr <= MAX_CONSECUTIVE_ERR && nextWindowIndex != C.INDEX_UNSET) {
            player.seekTo(nextWindowIndex, C.TIME_UNSET)
            player.prepare()
            player.play()
            return
        }

        player.pause()
        consecutivePlaybackErr = 0
    }

    private fun stopOnError() {
        player.pause()
    }

    private fun findStreamHttpFailure(
        error: PlaybackException,
    ): androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException? {
        var throwable: Throwable? = error.cause
        while (throwable != null) {
            if (throwable is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
                return throwable
            }
            throwable = throwable.cause
        }
        return null
    }

    private fun isRetryableRemoteParserFailure(error: PlaybackException): Boolean {
        if (
            error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
            error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
        ) {
            return true
        }

        var throwable: Throwable? = error.cause
        while (throwable != null) {
            if (
                throwable.message?.contains("Skipping atom with length", ignoreCase = true) == true ||
                    throwable.isMedia3ExtractorBoundsFailure()
            ) {
                return true
            }
            throwable = throwable.cause
        }
        return false
    }

    private fun isCacheCorruptionError(
        error: PlaybackException,
        isContentCached: Boolean,
    ): Boolean {
        val isIoError =
            error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE
        val isContainerParseError =
            error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED

        if (!isIoError && !isContainerParseError) {
            return false
        }

        var throwable: Throwable? = error.cause
        while (throwable != null) {
            when {
                throwable is EOFException -> {
                    return true
                }

                throwable is IOException &&
                    throwable.message?.contains("unexpected end of stream", ignoreCase = true) == true -> {
                    return true
                }

                throwable is IllegalStateException || throwable is IllegalArgumentException -> {
                    if (throwable.stackTrace.any { it.className.startsWith("androidx.media3.extractor") }) {
                        return true
                    }
                }

                isContentCached && throwable.isMedia3ExtractorBoundsFailure() -> {
                    return true
                }

                isContainerParseError && isContentCached && throwable is ParserException -> {
                    return true
                }

                isContainerParseError && isContentCached &&
                    throwable.message?.let {
                        it.contains("Invalid integer size", ignoreCase = true) ||
                            it.contains("Skipping atom with length", ignoreCase = true) ||
                            it.contains("contentIsMalformed=true", ignoreCase = true)
                    } == true -> {
                    return true
                }
            }
            throwable = throwable.cause
        }
        return false
    }

    private fun Throwable.isMedia3ExtractorBoundsFailure(): Boolean =
        this is ArrayIndexOutOfBoundsException &&
            stackTrace.any { it.className.startsWith("androidx.media3.extractor") }

    private fun retryPlaybackAfterStreamFailure(
        mediaId: String,
        isFullyDownloadedMedia: Boolean,
        responseException: androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException,
    ): Boolean {
        if (isFullyDownloadedMedia) return false

        val failedUrl = responseException.dataSpec.uri.toString()
        val requestProfile = StreamClientUtils.resolveRequestProfile(failedUrl)
        val authFingerprint = YouTube.currentPlaybackAuthState().fingerprint
        val cachedFailedUrl = playbackUrlCache[mediaId]?.takeIf { it.url == failedUrl }
        val failedExpiredUrl =
            YTPlayerUtils.isExpiredOrNearExpiredStreamUrl(failedUrl) ||
                (
                    cachedFailedUrl?.let {
                        !it.isValidFor(
                            authFingerprint = authFingerprint,
                            minimumRemainingMs = YTPlayerUtils.STREAM_URL_EXPIRY_SAFETY_MS,
                        )
                    } == true
                )

        playbackUrlCache.remove(mediaId)
        YTPlayerUtils.invalidateCachedStreamUrls(mediaId)
        if (!failedExpiredUrl && requestProfile.clientKey.isNotEmpty()) {
            YTPlayerUtils.markStreamClientFailed(mediaId, requestProfile.clientKey, responseException.responseCode)
        }

        if (!playbackStreamRecoveryTracker.registerRetryAttempt(mediaId)) {
            return false
        }

        Timber.tag("MusicService").i(
            "Retrying playback for %s after stream HTTP %d from %s failed",
            mediaId,
            responseException.responseCode,
            requestProfile.variantLabel,
        )
        val shouldResume = player.playWhenReady
        player.prepare()
        if (shouldResume) player.play()
        return true
    }

    private fun updateInitialBufferRecovery(@Player.State playbackState: Int) {
        val mediaId = player.currentMediaItem?.mediaId
        val shouldWatch =
            playbackState == Player.STATE_BUFFERING &&
                player.playWhenReady &&
                player.currentPosition < INITIAL_BUFFER_STALL_POSITION_MS &&
                mediaId != null &&
                initialBufferRecoveryAttemptedMediaId != mediaId

        if (!shouldWatch) {
            initialBufferRecoveryJob?.cancel()
            initialBufferRecoveryJob = null
            return
        }
        if (initialBufferRecoveryJob?.isActive == true) return

        initialBufferRecoveryJob =
            scope.launch {
                delay(INITIAL_BUFFER_STALL_DELAY_MS)
                if (player.playbackState != Player.STATE_BUFFERING ||
                    !player.playWhenReady ||
                    player.currentPosition >= INITIAL_BUFFER_STALL_POSITION_MS ||
                    player.currentMediaItem?.mediaId != mediaId
                ) {
                    return@launch
                }

                initialBufferRecoveryAttemptedMediaId = mediaId
                Timber.tag(TAG).w(
                    "Initial buffer stalled for %s; invalidating stream caches and re-preparing",
                    mediaId,
                )
                playbackUrlCache.remove(mediaId)
                YTPlayerUtils.invalidateCachedStreamUrls(mediaId)
                if (playbackStreamRecoveryTracker.registerRetryAttempt(mediaId)) {
                    player.prepare()
                    if (player.playWhenReady) player.play()
                }
            }
    }



    private fun updateNotification() {
        try {
            val customLayout =
                listOf(
                    CommandButton
                        .Builder()
                        .setDisplayName(
                            getString(
                                if (currentSong.value?.song?.liked == true) {
                                    R.string.action_remove_like
                                } else {
                                    R.string.action_like
                                },
                            ),
                        ).setIconResId(if (currentSong.value?.song?.liked == true) R.drawable.favorite else R.drawable.favorite_border)
                        .setSessionCommand(CommandToggleLike)
                        .setEnabled(currentSong.value != null)
                        .build(),
                    CommandButton
                        .Builder()
                        .setDisplayName(
                            getString(
                                when (player.repeatMode) {
                                    REPEAT_MODE_OFF -> R.string.repeat_mode_off
                                    REPEAT_MODE_ONE -> R.string.repeat_mode_one
                                    REPEAT_MODE_ALL -> R.string.repeat_mode_all
                                    else -> R.string.repeat_mode_off
                                },
                            ),
                        ).setIconResId(
                            when (player.repeatMode) {
                                REPEAT_MODE_OFF -> R.drawable.repeat
                                REPEAT_MODE_ONE -> R.drawable.repeat_one_on
                                REPEAT_MODE_ALL -> R.drawable.repeat_on
                                else -> R.drawable.repeat
                            },
                        ).setSessionCommand(CommandToggleRepeatMode)
                        .build(),
                    CommandButton
                        .Builder()
                        .setDisplayName(
                            getString(if (player.shuffleModeEnabled) R.string.action_shuffle_off else R.string.action_shuffle_on),
                        ).setIconResId(if (player.shuffleModeEnabled) R.drawable.shuffle_on else R.drawable.shuffle)
                        .setSessionCommand(CommandToggleShuffle)
                        .build(),
                    CommandButton
                        .Builder()
                        .setDisplayName(getString(R.string.start_radio))
                        .setIconResId(R.drawable.radio)
                        .setSessionCommand(CommandToggleStartRadio)
                        .setEnabled(currentSong.value != null)
                        .build(),
                )
            mediaSession.setCustomLayout(customLayout)
        } catch (e: Exception) {
            reportException(e)
        }
    }

    fun refreshPlaybackNotification() {
        updateNotification()
        onUpdateNotification(mediaSession, hasResumablePlaybackNotification())
    }

    private suspend fun recoverSong(
        mediaId: String,
        playbackData: YTPlayerUtils.PlaybackData? = null,
    ) {
        val song = database.song(mediaId).first()
        val mediaMetadata =
            withContext(Dispatchers.Main) {
                player.findNextMediaItemById(mediaId)?.metadata
            } ?: return
        val metadataSource =
            dataStore
                .get(DefaultMetadataSourceKey, MetadataSource.YOUTUBE.name)
                .toEnum(MetadataSource.YOUTUBE)
        val effectiveMetadata =
            if (metadataSource == MetadataSource.SPOTIFY) {
                withTimeoutOrNull(5_000L) {
                    spotifyLibraryRepository.enrichMetadata(mediaMetadata)
                } ?: mediaMetadata
            } else {
                mediaMetadata
            }
        if (effectiveMetadata != mediaMetadata) {
            withContext(Dispatchers.Main) {
                commitResolvedMetadata(mediaId, effectiveMetadata)
            }
        }
        val duration =
            song?.song?.duration?.takeIf { it != -1 }
                ?: effectiveMetadata.duration.takeIf { it != -1 }
                ?: (
                    playbackData?.videoDetails ?: YTPlayerUtils
                        .playerResponseForMetadata(mediaId)
                        .getOrNull()
                        ?.videoDetails
                )?.lengthSeconds?.toInt()
                ?: -1
        database.query {
            if (song == null) {
                insert(effectiveMetadata.copy(duration = duration))
            } else if (song.song.duration == -1) {
                update(song.song.copy(duration = duration))
            }
        }
        if (!database.hasRelatedSongs(mediaId)) {
            val relatedEndpoint =
                YouTube.next(WatchEndpoint(videoId = mediaId)).getOrNull()?.relatedEndpoint
                    ?: return
            val relatedPage = YouTube.related(relatedEndpoint).getOrNull() ?: return
            database.query {
                relatedPage.songs
                    .map(SongItem::toMediaMetadata)
                    .onEach(::insert)
                    .map {
                        RelatedSongMap(
                            songId = mediaId,
                            relatedSongId = it.id,
                        )
                    }.forEach(::insert)
            }
        }
    }

    fun playQueue(
        queue: Queue,
        playWhenReady: Boolean = true,
    ) {
        val joined = togetherSessionState.value as? moe.rukamori.archivetune.together.TogetherSessionState.Joined
        if (!isTogetherApplyingRemote() && joined?.role is moe.rukamori.archivetune.together.TogetherRole.Guest) {
            if (!joined.roomState.settings.allowGuestsToControlPlayback) {
                showTogetherNotice(getString(R.string.not_allowed), key = "GUEST_PLAYQUEUE_DISABLED")
                return
            }
            ensureScopesActive()
            scope.launch(SilentHandler) {
                val initialStatus =
                    withContext(Dispatchers.IO) {
                        queue
                            .getInitialStatus()
                            .filterPlaybackContent(
                                hideExplicit = dataStore.get(HideExplicitKey, false),
                                hideVideo = dataStore.get(HideVideoKey, false),
                            )
                    }

                val targetItem =
                    initialStatus.items.getOrNull(initialStatus.mediaItemIndex)
                        ?: queue.preloadItem
                            ?.toMediaItem()
                            ?.takeUnless { item ->
                                item.hasBlockedArtist(loadBlockedArtistIds())
                            }

                val meta = targetItem?.metadata
                val trackId =
                    meta?.id?.trim().orEmpty().ifBlank {
                        targetItem?.mediaId?.trim().orEmpty()
                    }
                if (trackId.isBlank()) {
                    showTogetherNotice(getString(R.string.not_allowed), key = "GUEST_PLAYQUEUE_NO_TRACK")
                    return@launch
                }

                val track =
                    moe.rukamori.archivetune.together.TogetherTrack(
                        id = trackId,
                        title = meta?.title ?: trackId,
                        artists = meta?.artists?.map { it.name }.orEmpty(),
                        durationSec = meta?.duration ?: -1,
                        thumbnailUrl = meta?.thumbnailUrl,
                    )

                val ops =
                    moe.rukamori.archivetune.together.TogetherGuestPlaybackPlanner.planPlayTrackNow(
                        roomState = joined.roomState,
                        track = track,
                        positionMs = initialStatus.position,
                        playWhenReady = playWhenReady,
                    )

                if (ops.isEmpty()) {
                    showTogetherNotice(getString(R.string.not_allowed), key = "GUEST_PLAYQUEUE_BLOCKED")
                    return@launch
                }

                showTogetherNotice(getString(R.string.together_requesting_song_change), key = "GUEST_PLAYQUEUE_REQUEST")
                ops.forEach { op ->
                    when (op) {
                        is moe.rukamori.archivetune.together.TogetherGuestOp.Control -> requestTogetherControl(op.action)
                        is moe.rukamori.archivetune.together.TogetherGuestOp.AddTrack -> requestTogetherAddTrack(op.track, op.mode)
                    }
                }
            }
            return
        }
        if (playWhenReady) {
            cancelIdleStop()
            promoteToStartedService()
            ensureStartedAsForeground()
        }
        cancelRestoredQueueHydration()
        ensureScopesActive()
        cancelCrossfade(resetVolume = true, resetPauseAtEnd = true)
        cancelInfiniteQueueBootstrap()
        suppressAutoPlayback = false
        val initialLoadGeneration = ++initialQueueLoadGeneration
        initialQueueLoadInProgress = true
        currentQueue = queue
        queueTitle = null
        val permanentShuffle = dataStore.get(PermanentShuffleKey, false)
        if (!permanentShuffle) {
            player.shuffleModeEnabled = false
        }

        clearAutomix()
        autoAddedMediaIds.clear()
        scope.launch(SilentHandler) {
            var autoLoadMoreEnabled = true
            try {
                moe.rukamori.archivetune.App.startupReadiness.awaitReady()
                autoLoadMoreEnabled = dataStore.getAsync(AutoLoadMoreKey, true)
                val hideExplicit = dataStore.get(HideExplicitKey, false)
                val hideVideo = dataStore.get(HideVideoKey, false)
                val preloadItem =
                    queue.preloadItem
                        ?.toMediaItem()
                        ?.takeUnless { item ->
                            item.hasBlockedArtist(loadBlockedArtistIds())
                        }
                if (preloadItem != null) {
                    player.setMediaItem(preloadItem)
                    player.prepare()
                    player.playWhenReady = playWhenReady
                }
                var initialStatus =
                    withContext(Dispatchers.IO) {
                        queue
                            .getInitialStatus()
                            .filterPlaybackContent(hideExplicit, hideVideo)
                    }
                if (!autoLoadMoreEnabled && queue.shouldExpandToFullQueueWhenAutoLoadMoreDisabled() && queue.hasNextPage()) {
                    val expandedItems = initialStatus.items.toMutableList()
                    var pagesLoaded = 0
                    while (queue.hasNextPage() && pagesLoaded < 200) {
                        pagesLoaded++
                        val nextItems =
                            withContext(Dispatchers.IO) {
                                queue
                                    .nextPage()
                                    .filterPlaybackContent(hideExplicit, hideVideo)
                            }
                        if (nextItems.isNotEmpty()) {
                            expandedItems += nextItems
                        }
                    }
                    initialStatus = initialStatus.copy(items = expandedItems)
                }
                if (initialLoadGeneration != initialQueueLoadGeneration) return@launch
                if (initialStatus.title != null) {
                    queueTitle = initialStatus.title
                }
                if (initialStatus.items.isEmpty()) return@launch
                if (preloadItem != null) {
                    val preloadMediaId = preloadItem.mediaId.trim()
                    val insertionIndex =
                        initialStatus.mediaItemIndex.coerceIn(0, initialStatus.items.size)
                    val itemsBeforeCurrent =
                        initialStatus.items
                            .subList(0, insertionIndex)
                            .filterNot { preloadMediaId.isNotEmpty() && it.mediaId.trim() == preloadMediaId }
                    val itemsAfterCurrent =
                        initialStatus.items
                            .subList(insertionIndex, initialStatus.items.size)
                            .filterNot { preloadMediaId.isNotEmpty() && it.mediaId.trim() == preloadMediaId }

                    player.addMediaItems(0, itemsBeforeCurrent)
                    player.addMediaItems(itemsAfterCurrent)
                    if (player.shuffleModeEnabled) {
                        applyCurrentFirstShuffleOrder()
                    }
                } else {
                    val items = initialStatus.items
                    val index = initialStatus.mediaItemIndex

                    player.setMediaItems(items, index, initialStatus.position)
                    player.prepare()
                    player.playWhenReady = playWhenReady
                    if (player.shuffleModeEnabled) {
                        applyCurrentFirstShuffleOrder()
                    }
                }
            } finally {
                // A superseded load must not clear the guard belonging to the newer queue.
                if (initialLoadGeneration == initialQueueLoadGeneration) {
                    initialQueueLoadInProgress = false
                }
            }

            if (initialLoadGeneration != initialQueueLoadGeneration) return@launch

            // Infinite Queue is a global, persisted choice. Song-start queues should seed radio
            // immediately so the toggle behaves consistently across search/library entry points.
            // For longer queues we still wait until the queue itself runs out of pages.
            if (autoLoadMoreEnabled &&
                player.repeatMode == REPEAT_MODE_OFF &&
                queue.shouldBootstrapInfiniteQueue()
            ) {
                onInfiniteQueueEnabled(queue.infiniteQueueSeedMediaId())
            }
        }
    }

    private fun applyCurrentFirstShuffleOrder() {
        val count = player.mediaItemCount
        if (count <= 1) return
        val currentIndex = player.currentMediaItemIndex.coerceIn(0, count - 1)
        val shuffledIndices = IntArray(count) { it }
        shuffledIndices.shuffle()
        val currentPos = shuffledIndices.indexOf(currentIndex)
        if (currentPos >= 0) {
            shuffledIndices[currentPos] = shuffledIndices[0]
        }
        shuffledIndices[0] = currentIndex
        localPlayer.setShuffleOrder(DefaultShuffleOrder(shuffledIndices, System.currentTimeMillis()))
    }

    private fun buildPlayNextShuffleOrder(
        currentIndex: Int,
        insertionIndex: Int,
        insertionCount: Int,
    ): DefaultShuffleOrder? {
        if (insertionCount <= 0 || player.currentTimeline.isEmpty) return null

        fun adjustedIndex(index: Int): Int =
            if (index >= insertionIndex) {
                index + insertionCount
            } else {
                index
            }

        val timeline = player.currentTimeline
        val previousIndices = ArrayDeque<Int>()
        var traversalIndex = currentIndex
        while (true) {
            traversalIndex = timeline.getPreviousWindowIndex(traversalIndex, REPEAT_MODE_OFF, true)
            if (traversalIndex == C.INDEX_UNSET) {
                break
            }
            previousIndices.addFirst(adjustedIndex(traversalIndex))
        }

        val nextIndices = mutableListOf<Int>()
        traversalIndex = currentIndex
        while (true) {
            traversalIndex = timeline.getNextWindowIndex(traversalIndex, REPEAT_MODE_OFF, true)
            if (traversalIndex == C.INDEX_UNSET) {
                break
            }
            nextIndices += adjustedIndex(traversalIndex)
        }

        val shuffledIndices =
            buildList(player.mediaItemCount + insertionCount) {
                addAll(previousIndices)
                add(currentIndex)
                repeat(insertionCount) { offset ->
                    add(insertionIndex + offset)
                }
                addAll(nextIndices)
            }.toIntArray()

        return DefaultShuffleOrder(shuffledIndices, System.currentTimeMillis())
    }

    fun startRadioSeamlessly() {
        val joined = togetherSessionState.value as? moe.rukamori.archivetune.together.TogetherSessionState.Joined
        if (!isTogetherApplyingRemote() && joined?.role is moe.rukamori.archivetune.together.TogetherRole.Guest) {
            if (!joined.roomState.settings.allowGuestsToControlPlayback) {
                showTogetherNotice(getString(R.string.not_allowed), key = "GUEST_RADIO_DISABLED")
                return
            }
            showTogetherNotice(getString(R.string.not_allowed), key = "GUEST_RADIO_UNSUPPORTED")
            return
        }
        cancelInfiniteQueueBootstrap()
        initialQueueLoadGeneration++
        initialQueueLoadInProgress = false
        suppressAutoPlayback = false
        val currentMediaMetadata = player.currentMetadata ?: return

        val currentIndex = player.currentMediaItemIndex
        val currentMediaId = currentMediaMetadata.id
        if (currentSong.value?.song?.isLocal == true ||
            currentMediaId.isLocalMediaId() ||
            currentMediaId.isTelegramMediaId()
        ) {
            return
        }

        scope.launch(SilentHandler) {
            val radioQueue =
                YouTubeQueue(
                    endpoint = WatchEndpoint(videoId = currentMediaId),
                    followAutomixPreview = true,
                )
            val initialStatus =
                withContext(Dispatchers.IO) {
                    radioQueue
                        .getInitialStatus()
                        .filterPlaybackContent(
                            hideExplicit = dataStore.get(HideExplicitKey, false),
                            hideVideo = dataStore.get(HideVideoKey, false),
                        )
                }

            if (initialStatus.title != null) {
                queueTitle = initialStatus.title
            }

            val radioItems =
                initialStatus.items.filter { item ->
                    item.mediaId != currentMediaId
                }

            if (radioItems.isNotEmpty()) {
                val itemCount = player.mediaItemCount

                if (itemCount > currentIndex + 1) {
                    player.removeMediaItems(currentIndex + 1, itemCount)
                }

                player.addMediaItems(currentIndex + 1, radioItems)
            }

            currentQueue = radioQueue
        }
    }

    fun clearAutomix() {
        autoAddedMediaIds.clear()
    }

    fun onInfiniteQueueDisabled() {
        cancelInfiniteQueueBootstrap()
        val currentIndex = player.currentMediaItemIndex
        val idsToRemove = synchronized(autoAddedMediaIds) { autoAddedMediaIds.toSet() }
        if (idsToRemove.isEmpty()) {
            return
        }
        for (i in player.mediaItemCount - 1 downTo 0) {
            if (i == currentIndex) continue
            val item = player.getMediaItemAt(i)
            if (item.mediaId in idsToRemove) {
                player.removeMediaItem(i)
            }
        }
        autoAddedMediaIds.clear()
        currentQueue = EmptyQueue
    }

    fun onInfiniteQueueEnabled(seedMediaId: String? = null) {
        val currentMeta = player.currentMetadata
        val resolvedSeedMediaId =
            seedMediaId?.trim()?.takeIf { it.isNotBlank() }
                ?: currentMeta?.id?.trim()?.takeIf { it.isNotBlank() }
                ?: return
        if (currentMeta != null && isCurrentPlaybackItemLocal(currentMeta)) return
        if (infiniteQueueJob?.isActive == true) return
        val generation = ++infiniteQueueGeneration
        infiniteQueueLoading.value = true

        infiniteQueueJob =
            scope.launch(SilentHandler) {
                try {
                    val hideExplicit = dataStore.get(HideExplicitKey, false)
                    val hideVideo = dataStore.get(HideVideoKey, false)
                    val radioQueue =
                        YouTubeQueue(
                            WatchEndpoint(videoId = resolvedSeedMediaId),
                            followAutomixPreview = true,
                        )
                    val status =
                        withContext(Dispatchers.IO) {
                            radioQueue
                                .getInitialStatus()
                                .filterPlaybackContent(hideExplicit, hideVideo)
                        }
                    val knownIds =
                        (0 until player.mediaItemCount)
                            .mapTo(mutableSetOf()) { player.getMediaItemAt(it).mediaId }
                    val newItems = status.items.filter { knownIds.add(it.mediaId) }.toMutableList()
                    var loadedPageCount = 1

                    while (
                        newItems.isEmpty() &&
                        radioQueue.hasNextPage() &&
                        loadedPageCount < INFINITE_QUEUE_MAX_BOOTSTRAP_PAGES
                    ) {
                        loadedPageCount++
                        val page =
                            withContext(Dispatchers.IO) {
                                radioQueue
                                    .nextPage()
                                    .filterPlaybackContent(hideExplicit, hideVideo)
                            }
                        newItems += page.filter { knownIds.add(it.mediaId) }
                    }

                    if (generation != infiniteQueueGeneration) return@launch

                    if (newItems.isNotEmpty()) {
                        player.addMediaItems(newItems)
                        newItems.forEach { autoAddedMediaIds.add(it.mediaId) }
                    }

                    currentQueue = radioQueue

                    if (player.playbackState == Player.STATE_ENDED ||
                        player.mediaItemCount == player.currentMediaItemIndex + 1
                    ) {
                        player.seekToNext()
                        player.play()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Failed to bootstrap auto-queue")
                } finally {
                    if (generation == infiniteQueueGeneration) {
                        infiniteQueueJob = null
                        infiniteQueueLoading.value = false
                    }
                }
            }
    }

    private fun cancelInfiniteQueueBootstrap() {
        infiniteQueueGeneration++
        infiniteQueueJob?.cancel()
        infiniteQueueJob = null
        infiniteQueueLoading.value = false
    }

    fun stopAndClearPlayback(clearPersistentState: Boolean = false) {
        cancelRestoredQueueHydration()
        cancelInfiniteQueueBootstrap()
        initialQueueLoadGeneration++
        initialQueueLoadInProgress = false
        suppressAutoPlayback = true
        cancelCrossfade(resetVolume = true, resetPauseAtEnd = true)
        clearAutomix()
        currentQueue = EmptyQueue
        queueTitle = null
        waitingForNetworkConnection.value = false
        currentMediaMetadata.value = null
        player.playWhenReady = false
        player.stop()
        player.clearMediaItems()
        abandonAudioFocus()
        closeAudioEffectSession()
        consecutivePlaybackErr = 0
        if (clearPersistentState) {
            clearPersistedQueueFiles()
        }
    }

    fun playNext(items: List<MediaItem>) {
        val allowedItems =
            items
                .filterBlockedArtists(blockedArtistIds)
                .filterVideo(hideMusicVideos)
        if (allowedItems.isEmpty()) return
        val joined = togetherSessionState.value as? moe.rukamori.archivetune.together.TogetherSessionState.Joined
        if (joined?.role is moe.rukamori.archivetune.together.TogetherRole.Guest) {
            if (!joined.roomState.settings.allowGuestsToAddTracks) {
                return
            }
            val tracks =
                allowedItems.mapNotNull { it.metadata }.map { meta ->
                    moe.rukamori.archivetune.together.TogetherTrack(
                        id = meta.id,
                        title = meta.title,
                        artists = meta.artists.map { it.name },
                        durationSec = meta.duration,
                        thumbnailUrl = meta.thumbnailUrl,
                    )
                }
            tracks.asReversed().forEach { track ->
                requestTogetherAddTrack(track, moe.rukamori.archivetune.together.AddTrackMode.PLAY_NEXT)
            }
            return
        }
        suppressAutoPlayback = false
        val insertionIndex = if (player.mediaItemCount == 0) 0 else player.currentMediaItemIndex + 1
        val playNextShuffleOrder =
            if (player.shuffleModeEnabled && player.mediaItemCount > 0) {
                buildPlayNextShuffleOrder(
                    currentIndex = player.currentMediaItemIndex,
                    insertionIndex = insertionIndex,
                    insertionCount = allowedItems.size,
                )
            } else {
                null
            }

        player.addMediaItems(insertionIndex, allowedItems)
        playNextShuffleOrder?.let(localPlayer::setShuffleOrder)
        player.prepare()
    }

    fun moveQueueItemToNext(mediaItemIndex: Int) {
        val currentIndex = player.currentMediaItemIndex
        if (
            player.mediaItemCount < 2 ||
            currentIndex == C.INDEX_UNSET ||
            mediaItemIndex !in 0 until player.mediaItemCount ||
            mediaItemIndex == currentIndex
        ) {
            return
        }

        if (player.shuffleModeEnabled) {
            val shuffledIndices = player.getQueueWindows().map { it.firstPeriodIndex }.toMutableList()
            val sourcePosition = shuffledIndices.indexOf(mediaItemIndex)
            val currentPosition = shuffledIndices.indexOf(currentIndex)
            if (sourcePosition == -1 || currentPosition == -1) return

            val destinationPosition = (currentPosition + 1).coerceAtMost(shuffledIndices.lastIndex)
            if (sourcePosition == destinationPosition) return

            shuffledIndices.move(sourcePosition, destinationPosition)
            localPlayer.setShuffleOrder(
                DefaultShuffleOrder(
                    shuffledIndices.toIntArray(),
                    System.currentTimeMillis(),
                ),
            )
            return
        }

        val destinationIndex = (currentIndex + 1).coerceAtMost(player.mediaItemCount - 1)
        if (mediaItemIndex != destinationIndex) {
            player.moveMediaItem(mediaItemIndex, destinationIndex)
        }
    }

    fun addToQueue(items: List<MediaItem>) {
        val allowedItems =
            items
                .filterBlockedArtists(blockedArtistIds)
                .filterVideo(hideMusicVideos)
        if (allowedItems.isEmpty()) return
        val joined = togetherSessionState.value as? moe.rukamori.archivetune.together.TogetherSessionState.Joined
        if (joined?.role is moe.rukamori.archivetune.together.TogetherRole.Guest) {
            if (!joined.roomState.settings.allowGuestsToAddTracks) {
                return
            }
            val tracks =
                allowedItems.mapNotNull { it.metadata }.map { meta ->
                    moe.rukamori.archivetune.together.TogetherTrack(
                        id = meta.id,
                        title = meta.title,
                        artists = meta.artists.map { it.name },
                        durationSec = meta.duration,
                        thumbnailUrl = meta.thumbnailUrl,
                    )
                }
            tracks.forEach { track ->
                requestTogetherAddTrack(track, moe.rukamori.archivetune.together.AddTrackMode.ADD_TO_QUEUE)
            }
            return
        }
        suppressAutoPlayback = false
        player.addMediaItems(allowedItems)
        player.prepare()
    }

    fun playFromVoiceSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        ensureScopesActive()
        scope.launch(SilentHandler) {
            val mediaItems =
                withContext(Dispatchers.IO) {
                    mediaLibrarySessionCallback.resolveVoiceMediaItems(trimmed)
                }
            if (mediaItems.isEmpty()) return@launch
            playQueue(ListQueue(items = mediaItems))
        }
    }

    fun startTogetherHost(
        port: Int,
        displayName: String,
        settings: moe.rukamori.archivetune.together.TogetherRoomSettings,
    ) {
        ensureScopesActive()
        scope.launch(SilentHandler) {
            togetherSessionState.value = moe.rukamori.archivetune.together.TogetherSessionState.Idle
        }

        ioScope.launch(SilentHandler) {
            stopTogetherInternal()
            togetherIsOnlineSession = false

            val localIp = getLocalIpv4Address()
            val sessionId =
                java.util.UUID
                    .randomUUID()
                    .toString()
            val sessionKey =
                java.util.UUID
                    .randomUUID()
                    .toString()
            val joinInfo =
                moe.rukamori.archivetune.together.TogetherJoinInfo(
                    host = localIp ?: "127.0.0.1",
                    port = port,
                    sessionId = sessionId,
                    sessionKey = sessionKey,
                )
            val joinLink =
                moe.rukamori.archivetune.together.TogetherLink
                    .encode(joinInfo)

            val server =
                moe.rukamori.archivetune.together.TogetherServer(
                    scope = ioScope,
                    sessionId = sessionId,
                    sessionKey = sessionKey,
                    hostDisplayName = displayName.trim().ifBlank { getString(R.string.app_name) },
                    initialSettings = settings,
                    hostParticipantId = togetherHostId,
                )

            server.onEvent = { event ->
                ioScope.launch(SilentHandler) {
                    handleTogetherHostEvent(event) { server.currentSettings() }
                }
            }

            server.start(port)
            togetherServer = server
            scheduleTogetherHostInactivityTimeout(sessionId)

            scope.launch(SilentHandler) {
                togetherSessionState.value =
                    moe.rukamori.archivetune.together.TogetherSessionState.Hosting(
                        sessionId = sessionId,
                        joinLink = joinLink,
                        localAddressHint = localIp,
                        port = port,
                        settings = settings,
                        roomState = null,
                    )
            }

            togetherBroadcastJob =
                ioScope.launch(SilentHandler) {
                    while (togetherServer === server) {
                        if (togetherAuthorityParticipantId == null || togetherAuthorityParticipantId == togetherHostId) {
                            val state = buildTogetherRoomState(sessionId = sessionId, hostId = togetherHostId)
                            server.broadcastRoomState(state)
                            scope.launch(SilentHandler) {
                                val hosting = togetherSessionState.value as? moe.rukamori.archivetune.together.TogetherSessionState.Hosting
                                if (hosting?.sessionId == sessionId) {
                                    togetherSessionState.value =
                                        hosting.copy(
                                            settings = server.currentSettings(),
                                            roomState =
                                                state.copy(
                                                    participants = server.currentParticipants(),
                                                    settings = server.currentSettings(),
                                                ),
                                        )
                                }
                            }
                        }
                        kotlinx.coroutines.delay(TogetherPlaybackSync.BroadcastIntervalMs)
                    }
                }
        }
    }

    fun joinTogether(
        rawLink: String,
        displayName: String,
    ) {
        ensureScopesActive()
        val joinInfo =
            moe.rukamori.archivetune.together.TogetherLink
                .decode(rawLink)
        if (joinInfo == null) {
            scope.launch(SilentHandler) {
                togetherSessionState.value =
                    moe.rukamori.archivetune.together.TogetherSessionState.Error(
                        message = getString(R.string.invalid_link),
                        recoverable = true,
                    )
            }
            return
        }

        scope.launch(SilentHandler) {
            togetherSessionState.value =
                moe.rukamori.archivetune.together.TogetherSessionState
                    .Joining(joinInfo.toDeepLink())
        }

        ioScope.launch(SilentHandler) {
            stopTogetherInternal()
            togetherIsOnlineSession = false
            val client =
                moe.rukamori.archivetune.together.TogetherClient(
                    ioScope,
                    clientId = getOrCreateTogetherClientId(),
                )
            togetherClient = client
            togetherClock =
                moe.rukamori.archivetune.together
                    .TogetherClock()
            togetherSelfParticipantId = null
            togetherLastAppliedQueueHash = null

            togetherClientEventsJob?.cancel()
            togetherClientEventsJob =
                ioScope.launch(SilentHandler) {
                    client.events.collect { event ->
                        when (event) {
                            is moe.rukamori.archivetune.together.TogetherClientEvent.Welcome -> {
                                togetherSelfParticipantId = event.welcome.participantId
                                scope.launch(SilentHandler) {
                                    val state = togetherSessionState.value
                                    if (state is moe.rukamori.archivetune.together.TogetherSessionState.Joining) {
                                        val selfName = displayName.trim().ifBlank { getString(R.string.together_role_guest) }
                                        val initial =
                                            moe.rukamori.archivetune.together.TogetherRoomState(
                                                sessionId = joinInfo.sessionId,
                                                hostId = togetherHostId,
                                                participants =
                                                    listOf(
                                                        moe.rukamori.archivetune.together.TogetherParticipant(
                                                            id = event.welcome.participantId,
                                                            name = selfName,
                                                            isHost = false,
                                                            isPending = event.welcome.isPending,
                                                            isConnected = true,
                                                        ),
                                                    ),
                                                settings = event.welcome.settings,
                                                queue = emptyList(),
                                                queueHash = "",
                                                currentIndex = 0,
                                                isPlaying = false,
                                                positionMs = 0L,
                                                repeatMode = 0,
                                                shuffleEnabled = false,
                                                sentAtElapsedRealtimeMs = android.os.SystemClock.elapsedRealtime(),
                                            )
                                        togetherSessionState.value =
                                            moe.rukamori.archivetune.together.TogetherSessionState.Joined(
                                                role = moe.rukamori.archivetune.together.TogetherRole.Guest,
                                                sessionId = joinInfo.sessionId,
                                                selfParticipantId = event.welcome.participantId,
                                                roomState = initial,
                                            )
                                    }
                                }
                                startTogetherHeartbeat(joinInfo.sessionId, client)
                            }

                            is moe.rukamori.archivetune.together.TogetherClientEvent.RoomState -> {
                                applyRemoteRoomState(event.state)
                            }

                            is moe.rukamori.archivetune.together.TogetherClientEvent.HostTransferred -> {
                                handleTogetherClientHostTransferred(event.transfer)
                            }

                            is moe.rukamori.archivetune.together.TogetherClientEvent.ControlRequested -> {
                                val joined =
                                    togetherSessionState.value as? moe.rukamori.archivetune.together.TogetherSessionState.Joined
                                if (togetherAuthorityParticipantId == togetherSelfParticipantId &&
                                    joined?.roomState?.settings?.allowGuestsToControlPlayback == true
                                ) {
                                    applyHostControl(event.request.action)
                                }
                            }

                            is moe.rukamori.archivetune.together.TogetherClientEvent.AddTrackRequested -> {
                                val joined =
                                    togetherSessionState.value as? moe.rukamori.archivetune.together.TogetherSessionState.Joined
                                if (togetherAuthorityParticipantId == togetherSelfParticipantId &&
                                    joined?.roomState?.settings?.allowGuestsToAddTracks == true
                                ) {
                                    applyHostAddTrack(event.request.track, event.request.mode)
                                }
                            }

                            is moe.rukamori.archivetune.together.TogetherClientEvent.JoinDecision -> {
                                if (!event.decision.approved) {
                                    scope.launch(SilentHandler) {
                                        togetherSessionState.value =
                                            moe.rukamori.archivetune.together.TogetherSessionState.Error(
                                                message = getString(R.string.not_allowed),
                                                recoverable = true,
                                            )
                                    }
                                    ioScope.launch(SilentHandler) { stopTogetherInternal() }
                                }
                            }

                            is moe.rukamori.archivetune.together.TogetherClientEvent.ServerIssue -> {
                                Timber.tag("Together").w("server issue (lan) code=${event.code.orEmpty()} message=${event.message}")
                                when (event.code) {
                                    "GUEST_CONTROL_DISABLED" -> {
                                        showTogetherNotice(event.message, key = "GUEST_CONTROL_DISABLED")
                                        val joined =
                                            togetherSessionState.value as? moe.rukamori.archivetune.together.TogetherSessionState.Joined
                                        if (joined?.role is moe.rukamori.archivetune.together.TogetherRole.Guest) {
                                            togetherPendingGuestControl = null
                                            togetherLastSentControlAction = null
                                            scope.launch(SilentHandler) { applyRemoteRoomState(joined.roomState, force = true) }
                                        }
                                    }

                                    "GUEST_ADD_DISABLED" -> {
                                        showTogetherNotice(event.message, key = "GUEST_ADD_DISABLED")
                                    }

                                    "HOST_OFFLINE" -> {
                                        showTogetherNotice(event.message, key = "HOST_OFFLINE")
                                    }

                                    else -> {
                                        scope.launch(SilentHandler) {
                                            togetherSessionState.value =
                                                moe.rukamori.archivetune.together.TogetherSessionState.Error(
                                                    message = event.message,
                                                    recoverable = true,
                                                )
                                        }
                                        ioScope.launch(SilentHandler) { stopTogetherInternal() }
                                    }
                                }
                            }

                            is moe.rukamori.archivetune.together.TogetherClientEvent.HeartbeatPong -> {
                                val clock = togetherClock ?: return@collect
                                clock.onPong(
                                    sentAtElapsedMs = event.pong.clientElapsedRealtimeMs,
                                    receivedAtElapsedMs = event.receivedAtElapsedRealtimeMs,
                                    serverElapsedMs = event.pong.serverElapsedRealtimeMs,
                                )
                            }

                            is moe.rukamori.archivetune.together.TogetherClientEvent.Error -> {
                                scope.launch(SilentHandler) {
                                    togetherSessionState.value =
                                        moe.rukamori.archivetune.together.TogetherSessionState.Error(
                                            message = event.message,
                                            recoverable = true,
                                        )
                                }
                                ioScope.launch(SilentHandler) { stopTogetherInternal() }
                            }

                            moe.rukamori.archivetune.together.TogetherClientEvent.Disconnected -> {
                                val current = togetherSessionState.value
                                if (current is moe.rukamori.archivetune.together.TogetherSessionState.Idle) return@collect
                                scope.launch(SilentHandler) {
                                    val currentState = togetherSessionState.value
                                    togetherSessionState.value =
                                        moe.rukamori.archivetune.together.TogetherSessionState.Error(
                                            message =
                                                if (currentState is moe.rukamori.archivetune.together.TogetherSessionState.Joined &&
                                                    currentState.role is moe.rukamori.archivetune.together.TogetherRole.Guest
                                                ) {
                                                    getString(R.string.together_host_left_session)
                                                } else {
                                                    getString(R.string.network_unavailable)
                                                },
                                            recoverable = true,
                                        )
                                }
                                ioScope.launch(SilentHandler) { stopTogetherInternal() }
                            }
                        }
                    }
                }

            client.connect(joinInfo, displayName.trim().ifBlank { getString(R.string.together_role_guest) })
        }
    }

    private fun togetherPublicErrorMessage(message: String): String {
        val trimmed = message.trim()
        return trimmed.ifBlank { getString(R.string.together_server_unreachable) }
    }

    fun startTogetherPublicHost(
        displayName: String,
        settings: moe.rukamori.archivetune.together.TogetherRoomSettings,
    ) {
        ensureScopesActive()
        scope.launch(SilentHandler) {
            togetherSessionState.value = moe.rukamori.archivetune.together.TogetherSessionState.Idle
        }

        ioScope.launch(SilentHandler) {
            stopTogetherInternal()
            togetherIsOnlineSession = true

            val serverUrl =
                moe.rukamori.archivetune.together.TogetherPublicServers
                    .selectedUrlOrNull(dataStore)
            if (serverUrl == null) {
                scope.launch(SilentHandler) {
                    togetherSessionState.value =
                        moe.rukamori.archivetune.together.TogetherSessionState.Error(
                            message = getString(R.string.together_online_not_configured),
                            recoverable = true,
                        )
                }
                return@launch
            }

            val forcedUrl = togetherPublicForcedServerUrl
            togetherPublicForcedServerUrl = null
            val effectiveServerUrl = forcedUrl ?: serverUrl
            togetherPublicServerUrl = effectiveServerUrl
            togetherPublicHostDisplayName = displayName

            val hostName = displayName.trim().ifBlank { getString(R.string.app_name) }
            val client =
                moe.rukamori.archivetune.together.TogetherPublicClient(
                    externalScope = ioScope,
                    serverUrl = effectiveServerUrl,
                    dataStore = dataStore,
                    username = hostName,
                )
            togetherPublicClient = client
            togetherPublicParticipants = emptyList()
            togetherPublicLastState = null

            client.onEvent = { event ->
                ioScope.launch(SilentHandler) {
                    handleTogetherPublicHostEvent(event, client, settings)
                }
            }

            client.createRoom()

            scope.launch(SilentHandler) {
                togetherSessionState.value =
                    moe.rukamori.archivetune.together.TogetherSessionState.JoiningOnline(
                        code = "",
                    )
            }

            ioScope.launch(SilentHandler) {
                delay(TOGETHER_PUBLIC_CONNECT_TIMEOUT_MS)
                if (togetherPublicClient === client &&
                    togetherSessionState.value is moe.rukamori.archivetune.together.TogetherSessionState.JoiningOnline
                ) {
                    togetherSessionState.value =
                        moe.rukamori.archivetune.together.TogetherSessionState.Error(
                            message = togetherPublicErrorMessage(""),
                            recoverable = true,
                        )
                    stopTogetherInternal()
                }
            }

            togetherBroadcastJob =
                ioScope.launch(SilentHandler) {
                    while (togetherPublicClient === client) {
                        val hosting =
                            togetherSessionState.value as? moe.rukamori.archivetune.together.TogetherSessionState.HostingOnline
                        val roomCode = hosting?.code
                        if (roomCode != null) {
                            val state =
                                buildTogetherRoomState(
                                    sessionId = roomCode,
                                    hostId = togetherSelfParticipantId ?: togetherHostId,
                                )
                            broadcastPublicStateDiff(client, state)
                            scope.launch(SilentHandler) {
                                val current =
                                    togetherSessionState.value as? moe.rukamori.archivetune.together.TogetherSessionState.HostingOnline
                                if (current?.code == roomCode) {
                                    togetherSessionState.value =
                                        current.copy(
                                            roomState =
                                                state.copy(
                                                    participants = togetherPublicParticipants,
                                                ),
                                        )
                                }
                            }
                        }
                        kotlinx.coroutines.delay(TogetherPlaybackSync.BroadcastIntervalMs)
                    }
                }
        }
    }

    fun joinTogetherPublic(
        code: String,
        displayName: String,
    ) {
        ensureScopesActive()
        val trimmedCode = code.trim()
        if (trimmedCode.isBlank()) {
            scope.launch(SilentHandler) {
                togetherSessionState.value =
                    moe.rukamori.archivetune.together.TogetherSessionState.Error(
                        message = getString(R.string.invalid_code),
                        recoverable = true,
                    )
            }
            return
        }

        scope.launch(SilentHandler) {
            togetherSessionState.value =
                moe.rukamori.archivetune.together.TogetherSessionState
                    .JoiningOnline(trimmedCode)
        }

        togetherPublicJoinCode = trimmedCode
        togetherPublicJoinDisplayName = displayName

        ioScope.launch(SilentHandler) {
            stopTogetherInternal()
            togetherIsOnlineSession = true

            val serverUrl =
                moe.rukamori.archivetune.together.TogetherPublicServers
                    .selectedUrlOrNull(dataStore)
            if (serverUrl == null) {
                scope.launch(SilentHandler) {
                    togetherSessionState.value =
                        moe.rukamori.archivetune.together.TogetherSessionState.Error(
                            message = getString(R.string.together_online_not_configured),
                            recoverable = true,
                        )
                }
                return@launch
            }

            val forcedUrl = togetherPublicForcedServerUrl
            togetherPublicForcedServerUrl = null
            val effectiveServerUrl = forcedUrl ?: serverUrl
            togetherPublicServerUrl = effectiveServerUrl

            val client =
                moe.rukamori.archivetune.together.TogetherPublicClient(
                    externalScope = ioScope,
                    serverUrl = effectiveServerUrl,
                    dataStore = dataStore,
                    username = displayName.trim().ifBlank { getString(R.string.together_role_guest) },
                )
            togetherPublicClient = client
            togetherPublicParticipants = emptyList()
            togetherPublicLastState = null
            togetherSelfParticipantId = null
            togetherLastAppliedQueueHash = null

            client.onEvent = { event ->
                ioScope.launch(SilentHandler) {
                    handleTogetherPublicGuestEvent(event, client, trimmedCode)
                }
            }

            client.joinRoom(trimmedCode)

            ioScope.launch(SilentHandler) {
                delay(TOGETHER_PUBLIC_CONNECT_TIMEOUT_MS)
                if (togetherPublicClient === client &&
                    togetherSessionState.value is moe.rukamori.archivetune.together.TogetherSessionState.JoiningOnline
                ) {
                    togetherSessionState.value =
                        moe.rukamori.archivetune.together.TogetherSessionState.Error(
                            message = togetherPublicErrorMessage(""),
                            recoverable = true,
                        )
                    stopTogetherInternal()
                }
            }
        }
    }

    private suspend fun broadcastPublicStateDiff(
        client: moe.rukamori.archivetune.together.TogetherPublicClient,
        state: moe.rukamori.archivetune.together.TogetherRoomState,
    ) {
        val last = togetherPublicLastState
        val queue = state.queue
        val trackInfo = queue.getOrNull(state.currentIndex)?.toPublicTrackInfo()
        val now = android.os.SystemClock.elapsedRealtime()

        val queueChanged = last == null || last.queueHash != state.queueHash
        val trackChanged = trackInfo?.id != last?.queue?.getOrNull(last.currentIndex)?.id
        val playingChanged = last == null || last.isPlaying != state.isPlaying
        val position =
            state.positionMs.takeIf { it > 0L } ?: (last?.positionMs ?: 0L)

        // The Metrolist server keeps no room track until the host names one, and PLAY
        // before any CHANGE_TRACK is answered with `no_track`. So a track change is
        // always announced first, and the play state rides right behind it in the same
        // tick — the server itself forces IsPlaying=false on change_track, so the
        // follow-up PLAY is what makes a mid-playback track change stick for guests.
        val actions =
            buildList {
                when {
                    trackInfo != null && trackChanged -> {
                        add(
                            moe.rukamori.archivetune.together.TogetherPublicPlaybackActionPayload(
                                action = moe.rukamori.archivetune.together.TogetherPublicPlaybackActions.CHANGE_TRACK,
                                trackId = trackInfo.id,
                                trackInfo = trackInfo,
                                position = 0L,
                                queue = queue.map { it.toPublicTrackInfo() },
                            ),
                        )
                        // The server forces IsPlaying=false on change_track, and the wire
                        // action cannot carry play state — so the host states it explicitly
                        // right behind the change, for BOTH states. Without the PAUSE half,
                        // a host skipping tracks while paused would leave every guest
                        // playing a track the host has stopped.
                        add(
                            moe.rukamori.archivetune.together.TogetherPublicPlaybackActionPayload(
                                action =
                                    if (state.isPlaying) {
                                        moe.rukamori.archivetune.together.TogetherPublicPlaybackActions.PLAY
                                    } else {
                                        moe.rukamori.archivetune.together.TogetherPublicPlaybackActions.PAUSE
                                    },
                                trackId = trackInfo.id,
                                position = position,
                            ),
                        )
                    }

                    queueChanged -> {
                        add(
                            moe.rukamori.archivetune.together.TogetherPublicPlaybackActionPayload(
                                action = moe.rukamori.archivetune.together.TogetherPublicPlaybackActions.SYNC_QUEUE,
                                trackId = trackInfo?.id,
                                trackInfo = trackInfo,
                                position = position,
                                queue = queue.map { it.toPublicTrackInfo() },
                            ),
                        )
                    }

                    playingChanged -> {
                        add(
                            moe.rukamori.archivetune.together.TogetherPublicPlaybackActionPayload(
                                action =
                                    if (state.isPlaying) {
                                        moe.rukamori.archivetune.together.TogetherPublicPlaybackActions.PLAY
                                    } else {
                                        moe.rukamori.archivetune.together.TogetherPublicPlaybackActions.PAUSE
                                    },
                                trackId = trackInfo?.id,
                                position = position,
                            ),
                        )
                    }

                    last != null &&
                        state.isPlaying &&
                        kotlin.math.abs(state.positionMs - last.positionMs) > 1_200L -> {
                        add(
                            moe.rukamori.archivetune.together.TogetherPublicPlaybackActionPayload(
                                action = moe.rukamori.archivetune.together.TogetherPublicPlaybackActions.SEEK,
                                trackId = trackInfo?.id,
                                position = state.positionMs,
                            ),
                        )
                    }
                }
            }

        if (actions.isNotEmpty()) {
            actions.forEach { client.sendPlaybackAction(it) }
            togetherPublicLastState = state
            togetherPublicLastAction = actions.last()
        }
    }

    private suspend fun handleTogetherPublicHostEvent(
        event: moe.rukamori.archivetune.together.TogetherPublicEvent,
        client: moe.rukamori.archivetune.together.TogetherPublicClient,
        initialSettings: moe.rukamori.archivetune.together.TogetherRoomSettings,
    ) {
        when (event) {
            is moe.rukamori.archivetune.together.TogetherPublicEvent.RoomCreated -> {
                togetherSelfParticipantId = event.userId
                scope.launch(SilentHandler) {
                    togetherSessionState.value =
                        moe.rukamori.archivetune.together.TogetherSessionState.HostingOnline(
                            sessionId = event.roomCode,
                            code = event.roomCode,
                            settings = initialSettings,
                            roomState = null,
                        )
                }
                scheduleTogetherHostInactivityTimeout(event.roomCode)
                client.requestSync()
            }

            is moe.rukamori.archivetune.together.TogetherPublicEvent.JoinRequested -> {
                val hosting =
                    togetherSessionState.value as? moe.rukamori.archivetune.together.TogetherSessionState.HostingOnline
                val settings = hosting?.settings ?: initialSettings
                if (!settings.requireHostApprovalToJoin) {
                    client.approveJoin(event.userId)
                } else {
                    togetherPublicParticipants =
                        togetherPublicParticipants.filterNot { it.id == event.userId } +
                            moe.rukamori.archivetune.together.TogetherParticipant(
                                id = event.userId,
                                name = event.username,
                                isHost = false,
                                isPending = true,
                                isConnected = true,
                            )
                }
            }

            is moe.rukamori.archivetune.together.TogetherPublicEvent.UserJoined -> {
                val participant =
                    moe.rukamori.archivetune.together.TogetherParticipant(
                        id = event.userId,
                        name = event.username,
                        isHost = false,
                        isPending = false,
                        isConnected = true,
                    )
                togetherPublicParticipants =
                    togetherPublicParticipants.filterNot { it.id == event.userId } + participant
                togetherParticipantNames[event.userId] = event.username
                cancelTogetherHostInactivityTimeout()
                showTogetherParticipantNotification(event.username, joined = true)
            }

            is moe.rukamori.archivetune.together.TogetherPublicEvent.UserLeft,
            is moe.rukamori.archivetune.together.TogetherPublicEvent.UserDisconnected,
            -> {
                val userId =
                    when (event) {
                        is moe.rukamori.archivetune.together.TogetherPublicEvent.UserLeft -> event.userId
                        is moe.rukamori.archivetune.together.TogetherPublicEvent.UserDisconnected -> event.userId
                        else -> return
                    }
                val name =
                    when (event) {
                        is moe.rukamori.archivetune.together.TogetherPublicEvent.UserLeft -> event.username
                        is moe.rukamori.archivetune.together.TogetherPublicEvent.UserDisconnected -> event.username
                        else -> return
                    }
                togetherPublicParticipants =
                    togetherPublicParticipants.filterNot { it.id == userId }
                togetherParticipantNames.remove(userId)?.let {
                    showTogetherParticipantNotification(it, joined = false)
                }
                val roomCode =
                    (togetherSessionState.value as? moe.rukamori.archivetune.together.TogetherSessionState.HostingOnline)?.code
                if (togetherPublicParticipants.isEmpty()) {
                    if (roomCode != null) scheduleTogetherHostInactivityTimeout(roomCode)
                } else {
                    cancelTogetherHostInactivityTimeout()
                }
            }

            is moe.rukamori.archivetune.together.TogetherPublicEvent.UserReconnected -> {
                val name = event.username
                togetherPublicParticipants =
                    togetherPublicParticipants.map {
                        if (it.id == event.userId) it.copy(isConnected = true, name = name) else it
                    }
                togetherParticipantNames[event.userId] = name
                cancelTogetherHostInactivityTimeout()
                showTogetherParticipantNotification(name, joined = true)
            }

            is moe.rukamori.archivetune.together.TogetherPublicEvent.SyncPlayback -> {
                val hosting =
                    togetherSessionState.value as? moe.rukamori.archivetune.together.TogetherSessionState.HostingOnline
                val settings = hosting?.settings ?: initialSettings
                val lastSent = togetherPublicLastAction
                if (lastSent != null &&
                    lastSent.action == event.action.action &&
                    lastSent.trackId == event.action.trackId &&
                    lastSent.position == event.action.position
                ) {
                    // Echo of our own broadcast; the server relays actions to all members.
                    return
                }
                when (event.action.action) {
                    moe.rukamori.archivetune.together.TogetherPublicPlaybackActions.PLAY -> {
                        if (settings.allowGuestsToControlPlayback) {
                            applyHostControl(moe.rukamori.archivetune.together.ControlAction.Play)
                        }
                    }

                    moe.rukamori.archivetune.together.TogetherPublicPlaybackActions.PAUSE -> {
                        if (settings.allowGuestsToControlPlayback) {
                            applyHostControl(moe.rukamori.archivetune.together.ControlAction.Pause)
                        }
                    }

                    moe.rukamori.archivetune.together.TogetherPublicPlaybackActions.SEEK -> {
                        if (settings.allowGuestsToControlPlayback) {
                            event.action.position?.let { position ->
                                applyHostControl(
                                    moe.rukamori.archivetune.together.ControlAction
                                        .SeekTo(positionMs = position),
                                )
                            }
                        }
                    }

                    moe.rukamori.archivetune.together.TogetherPublicPlaybackActions.SKIP_NEXT -> {
                        if (settings.allowGuestsToControlPlayback) {
                            applyHostControl(moe.rukamori.archivetune.together.ControlAction.SkipNext)
                        }
                    }

                    moe.rukamori.archivetune.together.TogetherPublicPlaybackActions.SKIP_PREV -> {
                        if (settings.allowGuestsToControlPlayback) {
                            applyHostControl(moe.rukamori.archivetune.together.ControlAction.SkipPrevious)
                        }
                    }

                    moe.rukamori.archivetune.together.TogetherPublicPlaybackActions.QUEUE_ADD -> {
                        if (settings.allowGuestsToAddTracks) {
                            event.action.trackInfo?.let { track ->
                                applyHostAddTrack(
                                    track = track.toTogetherTrack(),
                                    mode =
                                        if (event.action.insertNext == true) {
                                            moe.rukamori.archivetune.together.AddTrackMode.PLAY_NEXT
                                        } else {
                                            moe.rukamori.archivetune.together.AddTrackMode.ADD_TO_QUEUE
                                        },
                                )
                            }
                        }
                    }

                    else -> {
                        Unit
                    }
                }
            }

            is moe.rukamori.archivetune.together.TogetherPublicEvent.SyncRequested -> {
                val hosting =
                    togetherSessionState.value as? moe.rukamori.archivetune.together.TogetherSessionState.HostingOnline
                val roomCode = hosting?.code ?: return
                val state =
                    buildTogetherRoomState(
                        sessionId = roomCode,
                        hostId = togetherSelfParticipantId ?: togetherHostId,
                    )
                client.sendPlaybackAction(
                    moe.rukamori.archivetune.together.TogetherPublicPlaybackActionPayload(
                        action = moe.rukamori.archivetune.together.TogetherPublicPlaybackActions.SYNC_QUEUE,
                        trackId = state.queue.getOrNull(state.currentIndex)?.id,
                        trackInfo = state.queue.getOrNull(state.currentIndex)?.toPublicTrackInfo(),
                        position = state.positionMs,
                        queue = state.queue.map { it.toPublicTrackInfo() },
                    ),
                )
            }

            is moe.rukamori.archivetune.together.TogetherPublicEvent.HostChanged -> {
                val selfId = togetherSelfParticipantId
                togetherAuthorityParticipantId = event.newHostId
                if (event.newHostId == selfId) {
                    // This device became the new host.
                    togetherAuthorityParticipantId = event.newHostId
                    startTogetherPublicAuthorityBroadcast(client, event.roomCode, event.newHostId)
                } else {
                    togetherAuthorityParticipantId = event.newHostId
                    togetherBroadcastJob?.cancel()
                    scope.launch(SilentHandler) {
                        val current =
                            togetherSessionState.value as? moe.rukamori.archivetune.together.TogetherSessionState.HostingOnline
                        if (current != null) {
                            togetherSessionState.value =
                                moe.rukamori.archivetune.together.TogetherSessionState.Joined(
                                    role = moe.rukamori.archivetune.together.TogetherRole.Guest,
                                    sessionId = current.sessionId,
                                    selfParticipantId = selfId ?: "",
                                    roomState =
                                        current.roomState
                                            ?: moe.rukamori.archivetune.together.TogetherRoomState(
                                                sessionId = current.sessionId,
                                                hostId = event.newHostId,
                                            ),
                                )
                        }
                    }
                }
            }

            is moe.rukamori.archivetune.together.TogetherPublicEvent.Reconnected -> {
                val hosting =
                    togetherSessionState.value as? moe.rukamori.archivetune.together.TogetherSessionState.HostingOnline
                if (hosting != null) {
                    togetherPublicParticipants = event.state.participants
                    scope.launch(SilentHandler) {
                        togetherSessionState.value =
                            hosting.copy(
                                roomState = event.state.copy(participants = togetherPublicParticipants),
                            )
                    }
                }
            }

            is moe.rukamori.archivetune.together.TogetherPublicEvent.SuggestionReceived -> {
                // Guests add tracks on Metrolist servers via suggest_track; approve here so
                // the guest experience matches the old vivi queue_add (settings permitting).
                val hosting =
                    togetherSessionState.value as? moe.rukamori.archivetune.together.TogetherSessionState.HostingOnline
                val settings = hosting?.settings ?: initialSettings
                if (settings.allowGuestsToAddTracks) {
                    Timber.tag("Together")
                        .i("auto-approving suggestion ${event.suggestionId} from ${event.fromUsername}")
                    client.approveSuggestion(event.suggestionId)
                } else {
                    client.rejectSuggestion(event.suggestionId)
                }
            }

            is moe.rukamori.archivetune.together.TogetherPublicEvent.Error -> {
                if (event.recoverable) {
                    val fallback =
                        moe.rukamori.archivetune.together.TogetherPublicServers.Defaults.getOrNull(1)?.url
                    val canFailover =
                        togetherPublicClient === client &&
                            !moe.rukamori.archivetune.together.TogetherPublicServers.isCustomSelected(dataStore) &&
                            togetherPublicServerUrl == moe.rukamori.archivetune.together.TogetherPublicServers.Defaults.first().url &&
                            fallback != null
                    if (canFailover) {
                        togetherPublicForcedServerUrl = fallback
                        stopTogetherInternal()
                        startTogetherPublicHost(
                            displayName = togetherPublicHostDisplayName ?: getString(R.string.app_name),
                            settings = initialSettings,
                        )
                        return
                    }
                    scope.launch(SilentHandler) {
                        togetherSessionState.value =
                            moe.rukamori.archivetune.together.TogetherSessionState.Error(
                                message = togetherPublicErrorMessage(event.message),
                                recoverable = true,
                            )
                    }
                }
            }

            moe.rukamori.archivetune.together.TogetherPublicEvent.Disconnected -> {
                // Reconnect is handled inside the client.
            }

            is moe.rukamori.archivetune.together.TogetherPublicEvent.JoinApproved,
            is moe.rukamori.archivetune.together.TogetherPublicEvent.JoinRejected,
            is moe.rukamori.archivetune.together.TogetherPublicEvent.Kicked,
            is moe.rukamori.archivetune.together.TogetherPublicEvent.SyncState,
            -> {
                Unit
            }
        }
    }

    private suspend fun handleTogetherPublicGuestEvent(
        event: moe.rukamori.archivetune.together.TogetherPublicEvent,
        client: moe.rukamori.archivetune.together.TogetherPublicClient,
        joinedCode: String,
    ) {
        when (event) {
            is moe.rukamori.archivetune.together.TogetherPublicEvent.JoinApproved -> {
                togetherSelfParticipantId = event.userId
                scope.launch(SilentHandler) {
                    val current =
                        togetherSessionState.value as? moe.rukamori.archivetune.together.TogetherSessionState.JoiningOnline
                    if (current != null) {
                        togetherSessionState.value =
                            moe.rukamori.archivetune.together.TogetherSessionState.Joined(
                                role = moe.rukamori.archivetune.together.TogetherRole.Guest,
                                sessionId = event.roomCode,
                                selfParticipantId = event.userId,
                                roomState = event.state,
                            )
                    }
                }
                togetherPublicParticipants = event.state.participants
                togetherPublicLastState = event.state
                applyRemoteRoomState(event.state)
                client.requestSync()
                // Joiner-catches-up-by-asking: the server answers buffer_ready with a
                // precise seek + play/pause pair reflecting its own clock, which is a
                // tighter sync than the join snapshot alone can give.
                event.state.queue
                    .getOrNull(event.state.currentIndex)
                    ?.id
                    ?.takeIf { it.isNotBlank() }
                    ?.let { client.sendBufferReady(it) }
            }

            is moe.rukamori.archivetune.together.TogetherPublicEvent.SyncState -> {
                val current =
                    togetherSessionState.value as? moe.rukamori.archivetune.together.TogetherSessionState.Joined
                val hostId = current?.roomState?.hostId ?: togetherHostId
                val participants =
                    if (current != null) current.roomState.participants else togetherPublicParticipants
                val state =
                    event.state.toTogetherRoomState(
                        sessionId = current?.sessionId ?: joinedCode,
                        hostId = hostId,
                        participants = participants,
                    )
                togetherPublicParticipants = state.participants
                togetherPublicLastState = state
                applyRemoteRoomState(state)
            }

            is moe.rukamori.archivetune.together.TogetherPublicEvent.SyncPlayback -> {
                applyPublicPlaybackAction(event.action, client, joinedCode)
            }

            is moe.rukamori.archivetune.together.TogetherPublicEvent.JoinRejected -> {
                scope.launch(SilentHandler) {
                    togetherSessionState.value =
                        moe.rukamori.archivetune.together.TogetherSessionState.Error(
                            message = getString(R.string.not_allowed),
                            recoverable = true,
                        )
                }
                ioScope.launch(SilentHandler) { stopTogetherInternal() }
            }

            is moe.rukamori.archivetune.together.TogetherPublicEvent.Kicked -> {
                scope.launch(SilentHandler) {
                    togetherSessionState.value =
                        moe.rukamori.archivetune.together.TogetherSessionState.Error(
                            message = getString(R.string.together_kicked),
                            recoverable = true,
                        )
                }
                ioScope.launch(SilentHandler) { stopTogetherInternal() }
            }

            is moe.rukamori.archivetune.together.TogetherPublicEvent.HostChanged -> {
                val current =
                    togetherSessionState.value as? moe.rukamori.archivetune.together.TogetherSessionState.Joined
                if (current != null && event.newHostId == togetherSelfParticipantId) {
                    scope.launch(SilentHandler) {
                        togetherSessionState.value =
                            current.copy(
                                role = moe.rukamori.archivetune.together.TogetherRole.Host,
                                roomState =
                                    current.roomState.copy(
                                        hostId = event.newHostId,
                                        participants =
                                            current.roomState.participants.map {
                                                it.copy(isHost = it.id == event.newHostId)
                                            },
                                    ),
                            )
                    }
                    togetherAuthorityParticipantId = event.newHostId
                    startTogetherPublicAuthorityBroadcast(client, event.roomCode, event.newHostId)
                }
            }

            is moe.rukamori.archivetune.together.TogetherPublicEvent.Reconnected -> {
                if (event.isHost) {
                    scope.launch(SilentHandler) {
                        togetherSessionState.value =
                            moe.rukamori.archivetune.together.TogetherSessionState.HostingOnline(
                                sessionId = event.roomCode,
                                code = event.roomCode,
                                settings =
                                    moe.rukamori.archivetune.together.TogetherRoomSettings(),
                                roomState = event.state,
                            )
                    }
                    togetherPublicParticipants = event.state.participants
                    togetherSelfParticipantId = event.userId
                } else {
                    togetherSelfParticipantId = event.userId
                    togetherPublicParticipants = event.state.participants
                    togetherPublicLastState = event.state
                    applyRemoteRoomState(event.state)
                    // Rejoined mid-track: ask for the live position rather than trusting
                    // a snapshot whose age the reconnect delay set.
                    event.state.queue
                        .getOrNull(event.state.currentIndex)
                        ?.id
                        ?.takeIf { it.isNotBlank() }
                        ?.let { client.sendBufferReady(it) }
                }
            }

            is moe.rukamori.archivetune.together.TogetherPublicEvent.UserJoined -> {
                togetherPublicParticipants =
                    togetherPublicParticipants.filterNot { it.id == event.userId } +
                        moe.rukamori.archivetune.together.TogetherParticipant(
                            id = event.userId,
                            name = event.username,
                            isHost = false,
                            isPending = false,
                            isConnected = true,
                        )
            }

            is moe.rukamori.archivetune.together.TogetherPublicEvent.UserLeft -> {
                togetherPublicParticipants =
                    togetherPublicParticipants.filterNot { it.id == event.userId }
            }

            is moe.rukamori.archivetune.together.TogetherPublicEvent.UserReconnected -> {
                togetherPublicParticipants =
                    togetherPublicParticipants.map {
                        if (it.id == event.userId) it.copy(isConnected = true, name = event.username) else it
                    }
            }

            is moe.rukamori.archivetune.together.TogetherPublicEvent.UserDisconnected -> {
                togetherPublicParticipants =
                    togetherPublicParticipants.map {
                        if (it.id == event.userId) it.copy(isConnected = false) else it
                    }
            }

            is moe.rukamori.archivetune.together.TogetherPublicEvent.JoinRequested,
            is moe.rukamori.archivetune.together.TogetherPublicEvent.RoomCreated,
            is moe.rukamori.archivetune.together.TogetherPublicEvent.SyncRequested,
            moe.rukamori.archivetune.together.TogetherPublicEvent.Disconnected,
            -> {
                Unit
            }

            is moe.rukamori.archivetune.together.TogetherPublicEvent.SuggestionReceived -> {
                // Only the host approves suggestions; nothing for a guest to do.
                Unit
            }

            is moe.rukamori.archivetune.together.TogetherPublicEvent.Error -> {
                if (event.recoverable) {
                    val joinCode = togetherPublicJoinCode
                    val fallback =
                        moe.rukamori.archivetune.together.TogetherPublicServers.Defaults.getOrNull(1)?.url
                    val canFailover =
                        togetherPublicClient === client &&
                            joinCode != null &&
                            !moe.rukamori.archivetune.together.TogetherPublicServers.isCustomSelected(dataStore) &&
                            togetherPublicServerUrl == moe.rukamori.archivetune.together.TogetherPublicServers.Defaults.first().url &&
                            fallback != null
                    if (canFailover) {
                        togetherPublicForcedServerUrl = fallback
                        stopTogetherInternal()
                        joinTogetherPublic(
                            code = joinCode,
                            displayName = togetherPublicJoinDisplayName ?: getString(R.string.together_role_guest),
                        )
                        return
                    }
                    scope.launch(SilentHandler) {
                        togetherSessionState.value =
                            moe.rukamori.archivetune.together.TogetherSessionState.Error(
                                message = togetherPublicErrorMessage(event.message),
                                recoverable = true,
                            )
                    }
                }
            }
        }
    }

    private suspend fun applyPublicPlaybackAction(
        action: moe.rukamori.archivetune.together.TogetherPublicPlaybackActionPayload,
        client: moe.rukamori.archivetune.together.TogetherPublicClient,
        joinedCode: String,
    ) {
        val current =
            togetherSessionState.value as? moe.rukamori.archivetune.together.TogetherSessionState.Joined
        val base =
            current?.roomState ?: togetherPublicLastState
                ?: moe.rukamori.archivetune.together.TogetherRoomState(
                    sessionId = joinedCode,
                    hostId = togetherHostId,
                )
        val queue =
            action.queue?.map { it.toTogetherTrack() }
                ?: base.queue
        val currentIndex =
            action.trackId
                ?.let { id -> queue.indexOfFirst { it.id == id } }
                ?.coerceAtLeast(0)
                ?: base.currentIndex

        val updated =
            when (action.action) {
                moe.rukamori.archivetune.together.TogetherPublicPlaybackActions.PLAY -> {
                    base.copy(
                        queue = queue,
                        queueHash = moe.rukamori.archivetune.utils.md5(queue.joinToString(separator = "|") { it.id }),
                        currentIndex = currentIndex,
                        isPlaying = true,
                        positionMs = action.position ?: base.positionMs,
                        sentAtElapsedRealtimeMs = android.os.SystemClock.elapsedRealtime(),
                    )
                }

                moe.rukamori.archivetune.together.TogetherPublicPlaybackActions.PAUSE -> {
                    base.copy(
                        queue = queue,
                        queueHash = moe.rukamori.archivetune.utils.md5(queue.joinToString(separator = "|") { it.id }),
                        currentIndex = currentIndex,
                        isPlaying = false,
                        positionMs = action.position ?: base.positionMs,
                        sentAtElapsedRealtimeMs = android.os.SystemClock.elapsedRealtime(),
                    )
                }

                moe.rukamori.archivetune.together.TogetherPublicPlaybackActions.SEEK,
                -> {
                    base.copy(
                        positionMs = action.position ?: base.positionMs,
                        sentAtElapsedRealtimeMs = android.os.SystemClock.elapsedRealtime(),
                    )
                }

                moe.rukamori.archivetune.together.TogetherPublicPlaybackActions.CHANGE_TRACK -> {
                    base.copy(
                        queue = queue,
                        queueHash = moe.rukamori.archivetune.utils.md5(queue.joinToString(separator = "|") { it.id }),
                        currentIndex = currentIndex,
                        isPlaying = true,
                        positionMs = action.position ?: 0L,
                        sentAtElapsedRealtimeMs = android.os.SystemClock.elapsedRealtime(),
                    )
                }

                moe.rukamori.archivetune.together.TogetherPublicPlaybackActions.SYNC_QUEUE -> {
                    base.copy(
                        queue = queue,
                        queueHash = moe.rukamori.archivetune.utils.md5(queue.joinToString(separator = "|") { it.id }),
                        currentIndex = currentIndex,
                        isPlaying = base.isPlaying,
                        positionMs = action.position ?: base.positionMs,
                        sentAtElapsedRealtimeMs = android.os.SystemClock.elapsedRealtime(),
                    )
                }

                else -> {
                    base
                }
            }

        togetherPublicLastState = updated
        togetherPublicParticipants = updated.participants
        applyRemoteRoomState(updated)

        // Joiner-catches-up-by-asking, applied on every track change: the server answers
        // buffer_ready with a fresh seek + play/pause pair measured on its own clock, so
        // the guest re-locks onto the host's live position instead of the position the
        // change_track carried (which the server forces to 0).
        if (action.action == moe.rukamori.archivetune.together.TogetherPublicPlaybackActions.CHANGE_TRACK &&
            !action.trackId.isNullOrBlank()
        ) {
            client.sendBufferReady(action.trackId.orEmpty())
        }
    }

    fun leaveTogether() {
        ensureScopesActive()
        scope.launch(SilentHandler) {
            togetherSessionState.value = moe.rukamori.archivetune.together.TogetherSessionState.Idle
        }
        ioScope.launch(SilentHandler) { stopTogetherInternal() }
    }

    fun updateTogetherSettings(settings: moe.rukamori.archivetune.together.TogetherRoomSettings) {
        val server = togetherServer
        if (server == null && togetherPublicClient == null) return
        ioScope.launch(SilentHandler) {
            server?.updateSettings(settings)
            if (togetherPublicClient != null) {
                val current =
                    togetherSessionState.value as? moe.rukamori.archivetune.together.TogetherSessionState.HostingOnline
                if (current != null) {
                    scope.launch(SilentHandler) {
                        togetherSessionState.value = current.copy(settings = settings)
                    }
                }
            }
        }
    }

    fun approveTogetherParticipant(
        participantId: String,
        approved: Boolean,
    ) {
        val server = togetherServer
        val publicClient = togetherPublicClient
        if (server == null && publicClient == null) return
        ioScope.launch(SilentHandler) {
            server?.approveParticipant(participantId, approved)
            publicClient?.let { client ->
                if (approved) {
                    client.approveJoin(participantId)
                } else {
                    client.rejectJoin(participantId)
                }
                togetherPublicParticipants =
                    togetherPublicParticipants.map {
                        if (it.id == participantId) it.copy(isPending = false) else it
                    }
            }
        }
    }

    fun kickTogetherParticipant(
        participantId: String,
        reason: String? = null,
    ) {
        val publicClient = togetherPublicClient
        ioScope.launch(SilentHandler) {
            publicClient?.kickUser(participantId, reason)
        }
    }

    fun banTogetherParticipant(
        participantId: String,
        reason: String? = null,
    ) {
        val publicClient = togetherPublicClient
        ioScope.launch(SilentHandler) {
            publicClient?.kickUser(participantId, reason)
        }
    }

    fun transferTogetherHostOwnership(participantId: String) {
        val targetId = participantId.trim()
        if (targetId.isBlank() || targetId == togetherHostId || targetId == togetherSelfParticipantId) return
        val server = togetherServer
        val client = togetherClient
        val publicClient = togetherPublicClient
        val joined = togetherSessionState.value as? moe.rukamori.archivetune.together.TogetherSessionState.Joined
        ioScope.launch(SilentHandler) {
            when {
                server != null -> {
                    server.transferHostOwnership(targetId)
                }

                publicClient != null -> {
                    publicClient.transferHost(targetId)
                }

                joined?.role is moe.rukamori.archivetune.together.TogetherRole.Host && client != null -> {
                    client.transferHostOwnership(joined.sessionId, targetId)
                }
            }
        }
    }

    fun requestTogetherControl(action: moe.rukamori.archivetune.together.ControlAction) {
        val publicClient = togetherPublicClient
        val client =
            togetherClient ?: run {
                if (publicClient == null) {
                    showTogetherNotice(getString(R.string.network_unavailable), key = "TOGETHER_CLIENT_MISSING")
                    return
                }
                null
            }
        val state = togetherSessionState.value as? moe.rukamori.archivetune.together.TogetherSessionState.Joined ?: return
        if (state.role !is moe.rukamori.archivetune.together.TogetherRole.Guest) return
        if (!state.roomState.settings.allowGuestsToControlPlayback) {
            Timber.tag("Together").i("control blocked locally (disabled) action=${action::class.java.simpleName}")
            showTogetherNotice(getString(R.string.not_allowed), key = "GUEST_CONTROL_DISABLED_LOCAL")
            return
        }
        val now = android.os.SystemClock.elapsedRealtime()
        val lastAction = togetherLastSentControlAction
        val lastAt = togetherLastSentControlAtElapsedMs
        if (lastAction == action && now - lastAt < 350L) return
        togetherLastSentControlAction = action
        togetherLastSentControlAtElapsedMs = now

        val timeout = if (togetherIsOnlineSession) 5000L else 2000L
        togetherPendingGuestControl =
            when (action) {
                moe.rukamori.archivetune.together.ControlAction.Play -> {
                    TogetherPendingGuestControl(desiredIsPlaying = true, requestedAtElapsedMs = now, expiresAtElapsedMs = now + timeout)
                }

                moe.rukamori.archivetune.together.ControlAction.Pause -> {
                    TogetherPendingGuestControl(desiredIsPlaying = false, requestedAtElapsedMs = now, expiresAtElapsedMs = now + timeout)
                }

                is moe.rukamori.archivetune.together.ControlAction.SeekToIndex -> {
                    TogetherPendingGuestControl(
                        desiredIndex = action.index.coerceAtLeast(0),
                        requestedAtElapsedMs = now,
                        expiresAtElapsedMs =
                            now + timeout,
                    )
                }

                is moe.rukamori.archivetune.together.ControlAction.SeekToTrack -> {
                    TogetherPendingGuestControl(
                        desiredTrackId = action.trackId.trim().ifBlank { null },
                        requestedAtElapsedMs = now,
                        expiresAtElapsedMs = now + timeout,
                    )
                }

                else -> {
                    togetherPendingGuestControl
                }
            }

        if (publicClient != null) {
            val publicAction =
                when (action) {
                    moe.rukamori.archivetune.together.ControlAction.Play -> {
                        moe.rukamori.archivetune.together.TogetherPublicPlaybackActions.PLAY
                    }

                    moe.rukamori.archivetune.together.ControlAction.Pause -> {
                        moe.rukamori.archivetune.together.TogetherPublicPlaybackActions.PAUSE
                    }

                    is moe.rukamori.archivetune.together.ControlAction.SeekTo -> {
                        publicClient.sendPlaybackAction(
                            moe.rukamori.archivetune.together.TogetherPublicPlaybackActionPayload(
                                action = moe.rukamori.archivetune.together.TogetherPublicPlaybackActions.SEEK,
                                position = action.positionMs,
                            ),
                        )
                        return
                    }

                    moe.rukamori.archivetune.together.ControlAction.SkipNext -> {
                        moe.rukamori.archivetune.together.TogetherPublicPlaybackActions.SKIP_NEXT
                    }

                    moe.rukamori.archivetune.together.ControlAction.SkipPrevious -> {
                        moe.rukamori.archivetune.together.TogetherPublicPlaybackActions.SKIP_PREV
                    }

                    is moe.rukamori.archivetune.together.ControlAction.SeekToIndex -> {
                        val track = state.roomState.queue.getOrNull(action.index.coerceAtLeast(0))
                        if (track != null) {
                            publicClient.sendPlaybackAction(
                                moe.rukamori.archivetune.together.TogetherPublicPlaybackActionPayload(
                                    action = moe.rukamori.archivetune.together.TogetherPublicPlaybackActions.CHANGE_TRACK,
                                    trackId = track.id,
                                    trackInfo = track.toPublicTrackInfo(),
                                    position = action.positionMs,
                                ),
                            )
                        }
                        return
                    }

                    is moe.rukamori.archivetune.together.ControlAction.SeekToTrack -> {
                        val track = state.roomState.queue.firstOrNull { it.id == action.trackId }
                        if (track != null) {
                            publicClient.sendPlaybackAction(
                                moe.rukamori.archivetune.together.TogetherPublicPlaybackActionPayload(
                                    action = moe.rukamori.archivetune.together.TogetherPublicPlaybackActions.CHANGE_TRACK,
                                    trackId = track.id,
                                    trackInfo = track.toPublicTrackInfo(),
                                    position = action.positionMs,
                                ),
                            )
                        }
                        return
                    }

                    else -> {
                        null
                    }
                }
            if (publicAction != null) {
                publicClient.sendPlaybackAction(
                    moe.rukamori.archivetune.together.TogetherPublicPlaybackActionPayload(
                        action = publicAction,
                        position = player.currentPosition,
                    ),
                )
            }
            return
        }
        client?.requestControl(state.sessionId, action)
    }

    fun requestTogetherAddTrack(
        track: moe.rukamori.archivetune.together.TogetherTrack,
        mode: moe.rukamori.archivetune.together.AddTrackMode,
    ) {
        val publicClient = togetherPublicClient
        if (publicClient != null) {
            val state = togetherSessionState.value as? moe.rukamori.archivetune.together.TogetherSessionState.Joined ?: return
            if (state.role !is moe.rukamori.archivetune.together.TogetherRole.Guest) return
            if (!state.roomState.settings.allowGuestsToAddTracks) {
                Timber.tag("Together").i("add blocked locally (disabled) mode=$mode trackId=${track.id}")
                showTogetherNotice(getString(R.string.not_allowed), key = "GUEST_ADD_DISABLED_LOCAL")
                return
            }
            // Metrolist servers reject a guest's direct queue_add with `not_host`; the
            // guest's route in is suggest_track, which the host auto-approves (see the
            // SuggestionReceived branch in handleTogetherPublicHostEvent). The server
            // then broadcasts queue_add to everyone as a host-authored action.
            publicClient.suggestTrack(track.toPublicTrackInfo())
            return
        }
        val client = togetherClient ?: return
        val state = togetherSessionState.value as? moe.rukamori.archivetune.together.TogetherSessionState.Joined ?: return
        if (state.role !is moe.rukamori.archivetune.together.TogetherRole.Guest) return
        if (!state.roomState.settings.allowGuestsToAddTracks) {
            Timber.tag("Together").i("add blocked locally (disabled) mode=$mode trackId=${track.id}")
            showTogetherNotice(getString(R.string.not_allowed), key = "GUEST_ADD_DISABLED_LOCAL")
            return
        }
        client.requestAddTrack(state.sessionId, track, mode)
    }

    private suspend fun handleTogetherHostEvent(
        event: moe.rukamori.archivetune.together.TogetherServerEvent,
        currentSettings: suspend () -> moe.rukamori.archivetune.together.TogetherRoomSettings,
    ) {
        when (event) {
            is moe.rukamori.archivetune.together.TogetherServerEvent.ControlRequested -> {
                val settings = currentSettings()
                if (!settings.allowGuestsToControlPlayback) return
                applyHostControl(event.request.action)
            }

            is moe.rukamori.archivetune.together.TogetherServerEvent.AddTrackRequested -> {
                val settings = currentSettings()
                if (!settings.allowGuestsToAddTracks) return
                applyHostAddTrack(event.request.track, event.request.mode)
            }

            is moe.rukamori.archivetune.together.TogetherServerEvent.ParticipantJoined -> {
                val participant = event.participant
                if (!participant.isHost && !participant.isPending) {
                    togetherParticipantNames[participant.id] = participant.name
                    cancelTogetherHostInactivityTimeout()
                    showTogetherParticipantNotification(participant.name, joined = true)
                }
            }

            is moe.rukamori.archivetune.together.TogetherServerEvent.ParticipantLeft -> {
                val participantName =
                    togetherParticipantNames.remove(event.participantId)
                        ?: return
                showTogetherParticipantNotification(participantName, joined = false)
                if (togetherParticipantNames.isEmpty()) {
                    val sessionId =
                        when (val state = togetherSessionState.value) {
                            is moe.rukamori.archivetune.together.TogetherSessionState.Hosting -> {
                                state.sessionId
                            }

                            is moe.rukamori.archivetune.together.TogetherSessionState.HostingOnline -> {
                                state.sessionId
                            }

                            is moe.rukamori.archivetune.together.TogetherSessionState.Joined -> {
                                state.sessionId.takeIf {
                                    state.role is moe.rukamori.archivetune.together.TogetherRole.Host
                                }
                            }

                            else -> {
                                null
                            }
                        }
                    if (sessionId != null) {
                        scheduleTogetherHostInactivityTimeout(sessionId)
                    }
                }
            }

            is moe.rukamori.archivetune.together.TogetherServerEvent.HostTransferred -> {
                val currentState = togetherSessionState.value
                val sessionId =
                    when (currentState) {
                        is moe.rukamori.archivetune.together.TogetherSessionState.Hosting -> currentState.sessionId
                        is moe.rukamori.archivetune.together.TogetherSessionState.HostingOnline -> currentState.sessionId
                        is moe.rukamori.archivetune.together.TogetherSessionState.Joined -> currentState.sessionId
                        else -> null
                    }
                if (event.participantId == togetherHostId &&
                    togetherParticipantNames.isEmpty() &&
                    sessionId != null
                ) {
                    scheduleTogetherHostInactivityTimeout(sessionId)
                } else {
                    cancelTogetherHostInactivityTimeout()
                }
                handleTogetherHostTransferred(event.participantId)
            }

            is moe.rukamori.archivetune.together.TogetherServerEvent.RoomStateReceived -> {
                if (event.state.hostId != togetherHostId) {
                    togetherSelfParticipantId = togetherHostId
                    applyRemoteRoomState(event.state, force = true)
                }
            }

            is moe.rukamori.archivetune.together.TogetherServerEvent.Error -> {
                val current = togetherSessionState.value
                if (current is moe.rukamori.archivetune.together.TogetherSessionState.Idle) return
                togetherSessionState.value =
                    moe.rukamori.archivetune.together.TogetherSessionState.Error(
                        message = event.message,
                        recoverable = true,
                    )
                ioScope.launch(SilentHandler) { stopTogetherInternal() }
            }

            else -> {
                Unit
            }
        }
    }

    private suspend fun applyHostControl(action: moe.rukamori.archivetune.together.ControlAction) {
        withContext(Dispatchers.Main) {
            when (action) {
                moe.rukamori.archivetune.together.ControlAction.Play -> {
                    if (!player.playWhenReady) {
                        player.prepare()
                        player.playWhenReady = true
                    }
                }

                moe.rukamori.archivetune.together.ControlAction.Pause -> {
                    if (player.playWhenReady) {
                        player.playWhenReady = false
                    }
                }

                is moe.rukamori.archivetune.together.ControlAction.SeekTo -> {
                    player.seekTo(action.positionMs.coerceAtLeast(0L))
                    player.prepare()
                }

                moe.rukamori.archivetune.together.ControlAction.SkipNext -> {
                    if (player.hasNextMediaItem()) {
                        player.seekToNext()
                        player.prepare()
                        player.playWhenReady = true
                    }
                }

                moe.rukamori.archivetune.together.ControlAction.SkipPrevious -> {
                    if (player.hasPreviousMediaItem()) {
                        player.seekToPrevious()
                        player.prepare()
                        player.playWhenReady = true
                    }
                }

                is moe.rukamori.archivetune.together.ControlAction.SeekToTrack -> {
                    val trackId = action.trackId.trim()
                    if (trackId.isNotBlank()) {
                        val idx =
                            player.mediaItems.indexOfFirst {
                                val metaId = it.metadata?.id
                                it.mediaId == trackId || metaId == trackId
                            }
                        if (idx >= 0 && idx < player.mediaItemCount) {
                            player.seekTo(idx, action.positionMs.coerceAtLeast(0L))
                            player.prepare()
                        }
                    }
                }

                is moe.rukamori.archivetune.together.ControlAction.SeekToIndex -> {
                    val idx = action.index.coerceAtLeast(0)
                    if (idx < player.mediaItemCount) {
                        player.seekTo(idx, action.positionMs.coerceAtLeast(0L))
                        player.prepare()
                    }
                }

                is moe.rukamori.archivetune.together.ControlAction.SetRepeatMode -> {
                    if (player.repeatMode != action.repeatMode) {
                        player.repeatMode = action.repeatMode
                    }
                }

                is moe.rukamori.archivetune.together.ControlAction.SetShuffleEnabled -> {
                    if (player.shuffleModeEnabled != action.shuffleEnabled) {
                        player.shuffleModeEnabled = action.shuffleEnabled
                    }
                }
            }
        }
    }

    private suspend fun applyHostAddTrack(
        track: moe.rukamori.archivetune.together.TogetherTrack,
        mode: moe.rukamori.archivetune.together.AddTrackMode,
    ) {
        val mediaItem = track.toMediaMetadata().toMediaItem()
        withContext(Dispatchers.Main) {
            when (mode) {
                moe.rukamori.archivetune.together.AddTrackMode.PLAY_NEXT -> playNext(listOf(mediaItem))
                moe.rukamori.archivetune.together.AddTrackMode.ADD_TO_QUEUE -> addToQueue(listOf(mediaItem))
            }
        }
    }

    private suspend fun buildTogetherRoomState(
        sessionId: String,
        hostId: String,
    ): moe.rukamori.archivetune.together.TogetherRoomState =
        withContext(Dispatchers.Main) {
            val tracks =
                player.mediaItems.mapNotNull { it.metadata }.map { meta ->
                    moe.rukamori.archivetune.together.TogetherTrack(
                        id = meta.id,
                        title = meta.title,
                        artists = meta.artists.map { it.name },
                        durationSec = meta.duration,
                        thumbnailUrl = meta.thumbnailUrl,
                    )
                }

            val queueHash =
                moe.rukamori.archivetune.utils
                    .md5(tracks.joinToString(separator = "|") { it.id })

            moe.rukamori.archivetune.together.TogetherRoomState(
                sessionId = sessionId,
                hostId = hostId,
                settings =
                    moe.rukamori.archivetune.together
                        .TogetherRoomSettings(),
                participants = emptyList(),
                queue = tracks,
                queueHash = queueHash,
                currentIndex = player.currentMediaItemIndex.coerceAtLeast(0),
                isPlaying = player.playWhenReady && player.playbackState != Player.STATE_ENDED,
                positionMs = player.currentPosition.coerceAtLeast(0L),
                repeatMode = player.repeatMode,
                shuffleEnabled = player.shuffleModeEnabled,
                sentAtElapsedRealtimeMs = android.os.SystemClock.elapsedRealtime(),
            )
        }

    private fun markTogetherHostParticipant(
        state: moe.rukamori.archivetune.together.TogetherRoomState,
        hostId: String,
    ): moe.rukamori.archivetune.together.TogetherRoomState =
        state.copy(
            hostId = hostId,
            participants =
                state.participants.map { participant ->
                    participant.copy(isHost = participant.id == hostId)
                },
        )

    private fun handleTogetherHostTransferred(participantId: String) {
        togetherAuthorityParticipantId = participantId
        if (participantId != togetherHostId) {
            togetherSelfParticipantId = togetherHostId
        }
        scope.launch(SilentHandler) {
            when (val current = togetherSessionState.value) {
                is moe.rukamori.archivetune.together.TogetherSessionState.Hosting -> {
                    val roomState = current.roomState?.let { markTogetherHostParticipant(it, participantId) }
                    togetherSessionState.value =
                        moe.rukamori.archivetune.together.TogetherSessionState.Joined(
                            role =
                                if (participantId == togetherHostId) {
                                    moe.rukamori.archivetune.together.TogetherRole.Host
                                } else {
                                    moe.rukamori.archivetune.together.TogetherRole.Guest
                                },
                            sessionId = current.sessionId,
                            selfParticipantId = togetherHostId,
                            roomState =
                                roomState
                                    ?: moe.rukamori.archivetune.together.TogetherRoomState(
                                        sessionId = current.sessionId,
                                        hostId = participantId,
                                    ),
                        )
                }

                is moe.rukamori.archivetune.together.TogetherSessionState.HostingOnline -> {
                    val roomState = current.roomState?.let { markTogetherHostParticipant(it, participantId) }
                    togetherSessionState.value =
                        moe.rukamori.archivetune.together.TogetherSessionState.Joined(
                            role =
                                if (participantId == togetherHostId) {
                                    moe.rukamori.archivetune.together.TogetherRole.Host
                                } else {
                                    moe.rukamori.archivetune.together.TogetherRole.Guest
                                },
                            sessionId = current.sessionId,
                            selfParticipantId = togetherHostId,
                            roomState =
                                roomState
                                    ?: moe.rukamori.archivetune.together.TogetherRoomState(
                                        sessionId = current.sessionId,
                                        hostId = participantId,
                                    ),
                        )
                }

                is moe.rukamori.archivetune.together.TogetherSessionState.Joined -> {
                    togetherSessionState.value =
                        current.copy(
                            role =
                                if (current.selfParticipantId == participantId) {
                                    moe.rukamori.archivetune.together.TogetherRole.Host
                                } else {
                                    moe.rukamori.archivetune.together.TogetherRole.Guest
                                },
                            roomState = markTogetherHostParticipant(current.roomState, participantId),
                        )
                }

                else -> {
                    Unit
                }
            }
        }
    }

    private fun handleTogetherClientHostTransferred(transfer: moe.rukamori.archivetune.together.HostTransferred) {
        val participantId = transfer.participantId
        handleTogetherHostTransferred(participantId)
        val client = togetherClient ?: return
        if (participantId != togetherSelfParticipantId) return
        startTogetherAuthorityBroadcast(transfer.sessionId, participantId, client)
    }

    private fun startTogetherAuthorityBroadcast(
        sessionId: String,
        participantId: String,
        client: moe.rukamori.archivetune.together.TogetherClient,
    ) {
        togetherBroadcastJob?.cancel()
        togetherBroadcastJob =
            ioScope.launch(SilentHandler) {
                while (togetherClient === client && togetherAuthorityParticipantId == participantId) {
                    val state = buildTogetherRoomState(sessionId = sessionId, hostId = participantId)
                    client.sendRoomState(state)
                    kotlinx.coroutines.delay(TogetherPlaybackSync.BroadcastIntervalMs)
                }
            }
    }

    private fun startTogetherPublicAuthorityBroadcast(
        client: moe.rukamori.archivetune.together.TogetherPublicClient,
        roomCode: String,
        participantId: String,
    ) {
        togetherBroadcastJob?.cancel()
        togetherBroadcastJob =
            ioScope.launch(SilentHandler) {
                while (togetherPublicClient === client &&
                    togetherAuthorityParticipantId == participantId
                ) {
                    val state =
                        buildTogetherRoomState(
                            sessionId = roomCode,
                            hostId = participantId,
                        )
                    broadcastPublicStateDiff(client, state)
                    scope.launch(SilentHandler) {
                        val current =
                            togetherSessionState.value as? moe.rukamori.archivetune.together.TogetherSessionState.Joined
                        if (current?.role is moe.rukamori.archivetune.together.TogetherRole.Host) {
                            togetherSessionState.value =
                                current.copy(roomState = state.copy(participants = togetherPublicParticipants))
                        }
                    }
                    kotlinx.coroutines.delay(TogetherPlaybackSync.BroadcastIntervalMs)
                }
            }
    }

    private suspend fun applyRemoteRoomState(
        state: moe.rukamori.archivetune.together.TogetherRoomState,
        force: Boolean = false,
    ) {
        val pid = togetherSelfParticipantId ?: return
        val now = android.os.SystemClock.elapsedRealtime()

        val pending = togetherPendingGuestControl
        if (force) {
            togetherPendingGuestControl = null
        } else if (pending != null) {
            val currentTrackId = state.queue.getOrNull(state.currentIndex.coerceAtLeast(0))?.id
            val mismatch =
                (pending.desiredIsPlaying != null && state.isPlaying != pending.desiredIsPlaying) ||
                    (pending.desiredIndex != null && state.currentIndex != pending.desiredIndex) ||
                    (pending.desiredTrackId != null && currentTrackId != pending.desiredTrackId)
            if (now >= pending.expiresAtElapsedMs) {
                if ((pending.desiredIndex != null || pending.desiredTrackId != null) &&
                    now - pending.requestedAtElapsedMs >= 1200L &&
                    mismatch
                ) {
                    showTogetherNotice(getString(R.string.together_song_change_failed), key = "GUEST_SEEK_TIMEOUT")
                }
                togetherPendingGuestControl = null
            } else {
                if (mismatch) return
                togetherPendingGuestControl = null
            }
        }

        val sentAt = state.sentAtElapsedRealtimeMs
        if (TogetherPlaybackSync.isStaleRoomState(
                sentAtElapsedRealtimeMs = sentAt,
                lastAppliedSentAtElapsedRealtimeMs = togetherLastAppliedRoomStateSentAtElapsedMs,
                force = force,
            )
        ) {
            return
        }

        val targetPos =
            TogetherPlaybackSync.targetPositionMs(
                state = state,
                isOnlineSession = togetherIsOnlineSession,
                clockSnapshot = if (togetherIsOnlineSession) null else togetherClock?.snapshot(),
                nowElapsedRealtimeMs = now,
            )

        withContext(Dispatchers.Main) {
            togetherApplyingRemote = true
            togetherSuppressEchoUntilElapsedMs =
                TogetherPlaybackSync.echoSuppressionUntil(
                    android.os.SystemClock.elapsedRealtime(),
                )
            try {
                val desiredItems = state.queue.map { it.toMediaMetadata().toMediaItem() }
                val desiredIds = state.queue.map { it.id }
                val desiredHash = state.queueHash
                val localIds = player.mediaItems.mapNotNull { it.metadata?.id ?: it.mediaId }.filter { it.isNotBlank() }
                val localHash =
                    if (localIds.isEmpty()) {
                        ""
                    } else {
                        moe.rukamori.archivetune.utils
                            .md5(localIds.joinToString(separator = "|"))
                    }
                val needsRebuild =
                    TogetherPlaybackSync.needsQueueRebuild(
                        desiredHash = desiredHash,
                        desiredIds = desiredIds,
                        localHash = localHash,
                        localIds = localIds,
                    )

                if (desiredItems.isNotEmpty() && needsRebuild) {
                    togetherLastAppliedQueueHash = desiredHash.ifBlank { localHash }
                    val startIndex = state.currentIndex.coerceIn(0, desiredItems.lastIndex)
                    suppressAutoPlayback = false
                    currentQueue =
                        moe.rukamori.archivetune.playback.queues.ListQueue(
                            title = getString(R.string.music_player),
                            items = desiredItems,
                            startIndex = startIndex,
                            position = targetPos,
                        )
                    queueTitle = null
                    player.setMediaItems(desiredItems, startIndex, targetPos)
                    player.prepare()
                    player.repeatMode = state.repeatMode
                    player.shuffleModeEnabled = state.shuffleEnabled
                    player.playWhenReady = state.isPlaying
                    togetherLastRemoteAppliedIndex = startIndex
                } else {
                    val index =
                        if (player.mediaItemCount > 0) {
                            state.currentIndex.coerceIn(0, player.mediaItemCount - 1)
                        } else {
                            0
                        }
                    val indexChanged = player.mediaItemCount > 0 && index != player.currentMediaItemIndex

                    if (indexChanged) {
                        if (player.repeatMode != state.repeatMode) player.repeatMode = state.repeatMode
                        if (player.shuffleModeEnabled != state.shuffleEnabled) player.shuffleModeEnabled = state.shuffleEnabled
                        player.seekTo(index, targetPos)
                        player.prepare()
                        player.playWhenReady = state.isPlaying
                    } else {
                        val playbackStateChanged = player.playWhenReady != state.isPlaying
                        if (player.repeatMode != state.repeatMode) player.repeatMode = state.repeatMode
                        if (player.shuffleModeEnabled != state.shuffleEnabled) player.shuffleModeEnabled = state.shuffleEnabled
                        if (playbackStateChanged) player.playWhenReady = state.isPlaying
                        val shouldSeekForDrift =
                            TogetherPlaybackSync.shouldSeekForDrift(
                                currentPositionMs = player.currentPosition,
                                targetPositionMs = targetPos,
                                isPlaying = state.isPlaying,
                                isOnlineSession = togetherIsOnlineSession,
                            )
                        if (shouldSeekForDrift || (playbackStateChanged && !state.isPlaying)) {
                            player.seekTo(targetPos)
                            player.prepare()
                        }
                    }
                    togetherLastRemoteAppliedIndex = index
                }
                togetherLastRemoteAppliedPlayWhenReady = state.isPlaying
                togetherLastAppliedRoomStateSentAtElapsedMs = sentAt

                togetherSessionState.value =
                    moe.rukamori.archivetune.together.TogetherSessionState.Joined(
                        role = moe.rukamori.archivetune.together.TogetherRole.Guest,
                        sessionId = state.sessionId,
                        selfParticipantId = pid,
                        roomState = state,
                    )
            } finally {
                togetherApplyingRemote = false
            }
        }
    }

    private fun startTogetherHeartbeat(
        sessionId: String,
        client: moe.rukamori.archivetune.together.TogetherClient,
    ) {
        togetherHeartbeatJob?.cancel()
        togetherHeartbeatJob =
            ioScope.launch(SilentHandler) {
                var pingId = 0L
                while (togetherClient === client) {
                    val now = android.os.SystemClock.elapsedRealtime()
                    client.sendHeartbeat(sessionId = sessionId, pingId = pingId++, clientElapsedRealtimeMs = now)
                    kotlinx.coroutines.delay(2000)
                }
            }
    }

    private suspend fun stopTogetherInternal() {
        cancelTogetherHostInactivityTimeout()

        togetherBroadcastJob?.cancel()
        togetherBroadcastJob = null

        togetherClientEventsJob?.cancel()
        togetherClientEventsJob = null

        togetherHeartbeatJob?.cancel()
        togetherHeartbeatJob = null

        togetherClock = null
        togetherSelfParticipantId = null
        togetherAuthorityParticipantId = null
        togetherParticipantNames.clear()
        togetherLastAppliedQueueHash = null
        togetherIsOnlineSession = false
        togetherApplyingRemote = false
        togetherSuppressEchoUntilElapsedMs = 0L
        togetherLastAppliedRoomStateSentAtElapsedMs = 0L
        togetherLastRemoteAppliedPlayWhenReady = null
        togetherLastRemoteAppliedIndex = -1
        togetherLastSentControlAtElapsedMs = 0L
        togetherLastSentControlAction = null
        togetherPendingGuestControl = null

        try {
            togetherClient?.disconnect()
        } catch (_: Exception) {
        }
        togetherClient = null

        try {
            togetherPublicClient?.disconnect()
        } catch (_: Exception) {
        }
        togetherPublicClient = null
        togetherPublicParticipants = emptyList()
        togetherPublicLastState = null
        togetherPublicLastAction = null

        try {
            togetherServer?.stop()
        } catch (_: Exception) {
        }
        togetherServer = null
    }

    private fun moe.rukamori.archivetune.together.TogetherTrack.toMediaMetadata(): moe.rukamori.archivetune.models.MediaMetadata =
        moe.rukamori.archivetune.models.MediaMetadata(
            id = id,
            title = title,
            artists =
                artists.map { name ->
                    moe.rukamori.archivetune.models.MediaMetadata
                        .Artist(id = null, name = name)
                },
            duration = durationSec,
            thumbnailUrl = thumbnailUrl,
            album = null,
            setVideoId = null,
            explicit = false,
            liked = false,
            likedDate = null,
            inLibrary = null,
        )

    private fun getLocalIpv4Address(): String? =
        runCatching {
            java.net.NetworkInterface
                .getNetworkInterfaces()
                .toList()
                .asSequence()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList().asSequence() }
                .filterIsInstance<java.net.Inet4Address>()
                .map { it.hostAddress }
                .firstOrNull { it.isNotBlank() && it != "127.0.0.1" }
        }.getOrNull()

    private fun toggleLibrary() {
        database.query {
            currentSong.value?.let {
                update(it.song.toggleLibrary())
            }
        }
    }

    fun toggleLike() {
        val mediaMetadata = currentMediaMetadata.value ?: return
        ioScope.launch {
            try {
                val song =
                    toggleLikeMutex.withLock {
                        database.withTransaction {
                            val currentSongEntity =
                                getSongById(mediaMetadata.id)
                                    ?: run {
                                        insert(mediaMetadata) {
                                            it.copy(isLocal = mediaMetadata.id.isLocalMediaId())
                                        }
                                        getSongById(mediaMetadata.id)
                                    }
                                    ?: return@withTransaction null
                            currentSongEntity.song.toggleLike().also(::update)
                        }
                    } ?: return@launch

                syncUtils.likeSong(song)

                // Check if auto-download on like is enabled and the song is now liked
                if (!song.isLocal && dataStore.get(AutoDownloadOnLikeKey, false) && song.liked) {
                    // Trigger download for the liked song
                    val downloadRequest =
                        androidx.media3.exoplayer.offline.DownloadRequest
                            .Builder(song.id, song.id.toUri())
                            .setCustomCacheKey(song.id)
                            .setData(song.title.toByteArray())
                            .build()
                    androidx.media3.exoplayer.offline.DownloadService.sendAddDownload(
                        this@MusicService,
                        ExoDownloadService::class.java,
                        downloadRequest,
                        false,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                reportException(error)
            }
        }
    }

    fun toggleStartRadio() {
        startRadioSeamlessly()
    }

    private fun decodeBandLevelsMb(raw: String?): List<Int> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { EqualizerJson.json.decodeFromString<List<Int>>(raw) }.getOrNull() ?: emptyList()
    }

    private fun encodeBandLevelsMb(levelsMb: List<Int>): String =
        runCatching {
            EqualizerJson.json.encodeToString(levelsMb)
        }.getOrNull().orEmpty()

    private fun readEqSettingsFromPrefs(prefs: Preferences): EqSettings {
        val levels = decodeBandLevelsMb(prefs[EqualizerBandLevelsMbKey])
        return EqSettings(
            enabled = prefs[EqualizerEnabledKey] ?: false,
            bandLevelsMb = levels,
            outputGainEnabled = prefs[EqualizerOutputGainEnabledKey] ?: false,
            outputGainMb = prefs[EqualizerOutputGainMbKey] ?: 0,
            bassBoostEnabled = prefs[EqualizerBassBoostEnabledKey] ?: false,
            bassBoostStrength = (prefs[EqualizerBassBoostStrengthKey] ?: 0).coerceIn(0, 1000),
            virtualizerEnabled = prefs[EqualizerVirtualizerEnabledKey] ?: false,
            virtualizerStrength = (prefs[EqualizerVirtualizerStrengthKey] ?: 0).coerceIn(0, 1000),
            autoHeadroomEnabled = prefs[EqualizerAutoHeadroomEnabledKey] ?: false,
        )
    }

    fun applyEqFlatPreset() {
        ioScope.launch {
            val caps = eqCapabilities.value
            val bandCount =
                caps?.bandCount ?: equalizer?.let { readAudioEffectValue("equalizer band count") { it.numberOfBands.toInt() } } ?: 0
            val encoded = encodeBandLevelsMb(List(bandCount.coerceAtLeast(0)) { 0 })
            dataStore.edit { prefs ->
                prefs[EqualizerEnabledKey] = true
                prefs[EqualizerBandLevelsMbKey] = encoded
                prefs[EqualizerSelectedProfileIdKey] = "flat"
            }
        }
    }

    fun applySystemEqPreset(presetIndex: Int) {
        scope.launch {
            ensureAudioEffects(localPlayer.audioSessionId)
            val eq = equalizer ?: return@launch
            val maxPreset = readAudioEffectValue("equalizer preset count") { eq.numberOfPresets.toInt() } ?: 0
            if (presetIndex !in 0 until maxPreset) return@launch

            runCatching { eq.usePreset(presetIndex.toShort()) }.getOrNull() ?: return@launch

            val bandCount = readAudioEffectValue("equalizer band count") { eq.numberOfBands.toInt() } ?: 0
            val levels =
                (0 until bandCount).map { band ->
                    readAudioEffectValue("equalizer band level for band $band") {
                        eq.getBandLevel(band.toShort()).toInt()
                    } ?: 0
                }

            val encoded = encodeBandLevelsMb(levels)
            if (encoded.isBlank()) return@launch

            ioScope.launch {
                dataStore.edit { prefs ->
                    prefs[EqualizerEnabledKey] = true
                    prefs[EqualizerBandLevelsMbKey] = encoded
                    prefs[EqualizerSelectedProfileIdKey] = "system:$presetIndex"
                }
            }
        }
    }

    private fun resampleLevelsByIndex(
        levelsMb: List<Int>,
        targetCount: Int,
    ): List<Int> {
        if (targetCount <= 0) return emptyList()
        if (levelsMb.isEmpty()) return List(targetCount) { 0 }
        if (levelsMb.size == targetCount) return levelsMb
        if (targetCount == 1) return listOf(levelsMb.sum() / levelsMb.size)

        val lastIndex = levelsMb.lastIndex.toFloat().coerceAtLeast(1f)
        return List(targetCount) { i ->
            val pos = i.toFloat() * lastIndex / (targetCount - 1).toFloat()
            val lo =
                kotlin.math
                    .floor(pos)
                    .toInt()
                    .coerceIn(0, levelsMb.lastIndex)
            val hi =
                kotlin.math
                    .ceil(pos)
                    .toInt()
                    .coerceIn(0, levelsMb.lastIndex)
            val t = (pos - lo.toFloat()).coerceIn(0f, 1f)
            val a = levelsMb[lo]
            val b = levelsMb[hi]
            (a + ((b - a) * t)).toInt()
        }
    }

    private inline fun <T> readAudioEffectValue(
        operation: String,
        block: () -> T,
    ): T? =
        runCatching(block)
            .onFailure { error ->
                Timber.tag("MusicService").w(error, "Audio effect query failed: %s", operation)
            }.getOrNull()

    private fun updateEqCapabilitiesFromEffect(eq: Equalizer) {
        val bandCount = readAudioEffectValue("equalizer band count") { eq.numberOfBands.toInt().coerceAtLeast(0) } ?: 0
        val range = readAudioEffectValue("equalizer band range") { eq.bandLevelRange }
        val minMb = range?.getOrNull(0)?.toInt() ?: -1500
        val maxMb = range?.getOrNull(1)?.toInt() ?: 1500
        val center =
            (0 until bandCount).map { band ->
                (
                    readAudioEffectValue("equalizer center frequency for band $band") {
                        eq.getCenterFreq(band.toShort())
                    } ?: 0
                ) / 1000
            }
        val presetCount = readAudioEffectValue("equalizer preset count") { eq.numberOfPresets.toInt().coerceAtLeast(0) } ?: 0
        val presets =
            (0 until presetCount).map { idx ->
                readAudioEffectValue("equalizer preset name for preset $idx") {
                    eq.getPresetName(idx.toShort()).toString()
                } ?: "Preset ${idx + 1}"
            }
        val capabilities =
            EqCapabilities(
                bandCount = bandCount,
                minBandLevelMb = minMb,
                maxBandLevelMb = maxMb,
                centerFreqHz = center,
                systemPresets = presets,
            )
        eqCapabilities.value = capabilities
        equalizerPlaybackController.updateCapabilities(capabilities)
    }

    private fun releaseAudioEffectInstances() {
        audioEffectsSessionId = null
        try {
            equalizer?.release()
        } catch (_: Exception) {
        }
        try {
            bassBoost?.release()
        } catch (_: Exception) {
        }
        try {
            virtualizer?.release()
        } catch (_: Exception) {
        }
        try {
            loudnessEnhancer?.release()
        } catch (_: Exception) {
        }
        equalizer = null
        bassBoost = null
        virtualizer = null
        loudnessEnhancer = null
        eqCapabilities.value = null
        equalizerPlaybackController.updateCapabilities(null)
    }

    private fun releaseAudioEffects() {
        audioEffectsInitializationJob?.cancel()
        audioEffectsInitializationJob = null
        releaseAudioEffectInstances()
    }

    private fun ensureAudioEffects(sessionId: Int) {
        if (sessionId <= 0) return
        if (audioEffectsSessionId == sessionId && equalizer != null) return

        audioEffectsInitializationJob?.cancel()
        audioEffectsInitializationJob = null
        if (initializeAudioEffects(sessionId)) return

        audioEffectsInitializationJob =
            scope.launch {
                repeat(AUDIO_EFFECT_INITIALIZATION_MAX_ATTEMPTS - 1) {
                    delay(AUDIO_EFFECT_INITIALIZATION_RETRY_DELAY_MS)
                    if (localPlayer.audioSessionId != sessionId || !shouldKeepAudioEffectSessionOpen()) {
                        return@launch
                    }
                    if (initializeAudioEffects(sessionId)) return@launch
                }
            }
    }

    private fun initializeAudioEffects(sessionId: Int): Boolean {
        releaseAudioEffectInstances()
        audioEffectsSessionId = sessionId

        equalizer = createAudioEffect("Equalizer", sessionId) { Equalizer(0, sessionId) }
        bassBoost = createAudioEffect("BassBoost", sessionId) { BassBoost(0, sessionId) }
        virtualizer = createAudioEffect("Virtualizer", sessionId) { Virtualizer(0, sessionId) }
        loudnessEnhancer = createAudioEffect("LoudnessEnhancer", sessionId) { LoudnessEnhancer(sessionId) }

        equalizer?.let(::updateEqCapabilitiesFromEffect)
        applyEqSettingsToEffects(desiredEqSettings.value)
        return equalizer != null
    }

    private inline fun <T> createAudioEffect(
        name: String,
        sessionId: Int,
        factory: () -> T,
    ): T? =
        runCatching(factory)
            .onFailure { error ->
                Timber.tag(TAG).w(error, "%s initialization failed for audio session %d", name, sessionId)
            }.getOrNull()

    private fun applyEqSettingsToEffects(settings: EqSettings) {
        val eq = equalizer ?: return
        val caps = eqCapabilities.value
        val bandCount = caps?.bandCount ?: readAudioEffectValue("equalizer band count") { eq.numberOfBands.toInt() } ?: 0
        val minMb =
            caps?.minBandLevelMb ?: readAudioEffectValue("equalizer minimum band level") { eq.bandLevelRange.getOrNull(0)?.toInt() }
                ?: -1500
        val maxMb =
            caps?.maxBandLevelMb ?: readAudioEffectValue("equalizer maximum band level") { eq.bandLevelRange.getOrNull(1)?.toInt() } ?: 1500

        val levels = resampleLevelsByIndex(settings.bandLevelsMb, bandCount)
        runCatching { eq.enabled = settings.enabled }

        for (band in 0 until bandCount) {
            val levelMb = levels.getOrNull(band)?.coerceIn(minMb, maxMb) ?: 0
            runCatching { eq.setBandLevel(band.toShort(), levelMb.toShort()) }
        }

        bassBoost?.let { bb ->
            runCatching { bb.enabled = settings.enabled && settings.bassBoostEnabled }
            runCatching { bb.setStrength(settings.bassBoostStrength.toShort()) }
        }

        virtualizer?.let { v ->
            runCatching { v.enabled = settings.enabled && settings.virtualizerEnabled }
            runCatching { v.setStrength(settings.virtualizerStrength.toShort()) }
        }

        loudnessEnhancer?.let { le ->
            val automaticHeadroomMb = -(levels.maxOrNull()?.coerceAtLeast(0) ?: 0)
            val gainMb =
                when {
                    settings.autoHeadroomEnabled -> automaticHeadroomMb
                    settings.outputGainEnabled -> settings.outputGainMb.coerceIn(-1500, 1500)
                    else -> 0
                }
            runCatching { le.setTargetGain(gainMb) }
            runCatching { le.enabled = settings.enabled && (settings.autoHeadroomEnabled || settings.outputGainEnabled) }
        }
    }

    private fun shouldKeepAudioEffectSessionOpen(): Boolean {
        val playbackState = localPlayer.playbackState
        return playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_READY
    }

    private fun reconcileAudioEffectSession() {
        if (!shouldKeepAudioEffectSessionOpen()) {
            closeAudioEffectSession()
            return
        }

        val sessionId = localPlayer.audioSessionId
        if (sessionId > 0) {
            rebindAudioEffectSession(sessionId)
        }
    }

    private fun openAudioEffectSession() {
        if (isAudioEffectSessionOpened) return
        val sessionId = localPlayer.audioSessionId
        if (sessionId <= 0) return
        isAudioEffectSessionOpened = true
        openedAudioSessionId = sessionId
        ensureAudioEffects(sessionId)
        sendOpenAudioEffectSessionBroadcast(sessionId)
    }

    private fun closeAudioEffectSession() {
        if (!isAudioEffectSessionOpened) return
        isAudioEffectSessionOpened = false
        val sessionId = openedAudioSessionId ?: localPlayer.audioSessionId
        openedAudioSessionId = null
        releaseAudioEffects()
        if (sessionId <= 0) return
        sendCloseAudioEffectSessionBroadcast(sessionId)
    }

    private fun rebindAudioEffectSession(newSessionId: Int) {
        if (newSessionId <= 0 || !shouldKeepAudioEffectSessionOpen()) return
        val oldSessionId = openedAudioSessionId
        if (!isAudioEffectSessionOpened) {
            openAudioEffectSession()
            return
        }
        if (oldSessionId == newSessionId) {
            ensureAudioEffects(newSessionId)
            return
        }

        if (oldSessionId != null && oldSessionId > 0) {
            sendCloseAudioEffectSessionBroadcast(oldSessionId)
        }
        openedAudioSessionId = newSessionId
        ensureAudioEffects(newSessionId)
        sendOpenAudioEffectSessionBroadcast(newSessionId)
    }

    private fun sendOpenAudioEffectSessionBroadcast(sessionId: Int) {
        sendBroadcast(
            Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            },
        )
    }

    private fun sendCloseAudioEffectSessionBroadcast(sessionId: Int) {
        sendBroadcast(
            Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            },
        )
    }

    private fun historyThresholdMs(): Long =
        (runCatching { dataStore[HistoryDuration] }.getOrNull() ?: HISTORY_DURATION_DEFAULT)
            .coerceIn(HISTORY_DURATION_MIN, HISTORY_DURATION_MAX)
            .toLong() * 1000L

    private fun currentHistoryPlayedMs(nowElapsedMs: Long = android.os.SystemClock.elapsedRealtime()): Long {
        val runningPlayMs =
            currentHistoryStartedAtElapsedMs
                ?.let { (nowElapsedMs - it).coerceAtLeast(0L) }
                ?: 0L
        return currentHistoryAccumulatedPlayMs + runningPlayMs
    }

    private fun flushCurrentHistoryPlayedTime(nowElapsedMs: Long = android.os.SystemClock.elapsedRealtime()) {
        currentHistoryAccumulatedPlayMs = currentHistoryPlayedMs(nowElapsedMs)
        currentHistoryStartedAtElapsedMs = null
    }

    private fun updatePendingHistoryFinalization(
        mediaId: String,
        sessionToken: Long,
        result: ImmediateHistoryResult,
    ) {
        val pendingSessions = pendingHistoryFinalizations[mediaId] ?: return
        val index = pendingSessions.indexOfFirst { it.sessionToken == sessionToken }
        if (index == -1) return

        val existing = pendingSessions[index]
        pendingSessions[index] =
            existing.copy(
                eventId = result.eventId ?: existing.eventId,
                remoteRegistered = existing.remoteRegistered || result.remoteRegistered,
            )
    }

    private fun enqueueCurrentHistorySessionForFinalization() {
        val mediaId = currentHistoryMediaId ?: return
        if (currentHistorySessionQueued) return

        pendingHistoryFinalizations
            .getOrPut(mediaId) { mutableListOf() }
            .add(
                PendingHistoryFinalization(
                    sessionToken = currentHistorySessionToken,
                    eventId = currentHistoryEventId,
                    remoteRegistered = currentHistoryRemoteRegistered,
                ),
            )
        currentHistorySessionQueued = true
    }

    private fun popPendingHistoryFinalization(mediaId: String): PendingHistoryFinalization? {
        val pendingSessions = pendingHistoryFinalizations[mediaId] ?: return null
        val pending = pendingSessions.firstOrNull() ?: return null
        pendingSessions.removeAt(0)
        if (pendingSessions.isEmpty()) {
            pendingHistoryFinalizations.remove(mediaId)
        }
        return pending
    }

    private fun beginHistorySession(
        mediaId: String?,
        forceNew: Boolean = false,
    ) {
        val normalizedMediaId = mediaId?.trim()?.takeIf { it.isNotEmpty() }
        if (!forceNew && currentHistoryMediaId == normalizedMediaId && currentHistorySessionToken != 0L) {
            updateHistoryTrackingPlaybackState()
            return
        }

        historyThresholdJob?.cancel()
        historyThresholdJob = null
        flushCurrentHistoryPlayedTime()
        enqueueCurrentHistorySessionForFinalization()

        currentHistorySessionToken = ++nextHistorySessionToken
        currentHistoryMediaId = normalizedMediaId
        currentHistoryAccumulatedPlayMs = 0L
        currentHistoryStartedAtElapsedMs = null
        currentHistoryEventId = null
        currentHistoryRemoteRegistered = false
        currentHistoryImmediateAttempted = false
        currentHistorySessionQueued = false

        updateHistoryTrackingPlaybackState()
    }

    private fun updateHistoryTrackingPlaybackState() {
        val mediaId = currentHistoryMediaId
        if (mediaId == null || currentHistorySessionQueued) {
            historyThresholdJob?.cancel()
            historyThresholdJob = null
            currentHistoryStartedAtElapsedMs = null
            return
        }

        if (player.isPlaying) {
            if (currentHistoryStartedAtElapsedMs == null) {
                currentHistoryStartedAtElapsedMs = android.os.SystemClock.elapsedRealtime()
            }
        } else {
            flushCurrentHistoryPlayedTime()
        }

        syncHistoryThresholdJob()
    }

    private fun syncHistoryThresholdJob() {
        historyThresholdJob?.cancel()
        historyThresholdJob = null

        val mediaId = currentHistoryMediaId ?: return
        if (currentHistorySessionQueued) return
        if (dataStore.get(PauseListenHistoryKey, false)) return
        if (currentHistoryEventId != null && currentHistoryRemoteRegistered) return

        val thresholdMs = historyThresholdMs()
        val playedMs = currentHistoryPlayedMs()
        if (playedMs >= thresholdMs) {
            if (!currentHistoryImmediateAttempted) {
                maybeRecordCurrentPlaybackHistory()
            }
            return
        }
        if (!player.isPlaying) return

        historyThresholdJob =
            scope.launch {
                delay((thresholdMs - playedMs).coerceAtLeast(0L))
                maybeRecordCurrentPlaybackHistory()
            }
    }

    private fun maybeRecordCurrentPlaybackHistory() {
        val mediaId = currentHistoryMediaId ?: return
        if (currentHistorySessionQueued) return
        if (dataStore.get(PauseListenHistoryKey, false)) return

        val thresholdMs = historyThresholdMs()
        val playedMs = currentHistoryPlayedMs()
        if (playedMs < thresholdMs) {
            syncHistoryThresholdJob()
            return
        }

        val sessionToken = currentHistorySessionToken
        if (historyRecordingJobs.containsKey(sessionToken)) return
        currentHistoryImmediateAttempted = true

        val eventIdSnapshot = currentHistoryEventId
        val remoteRegisteredSnapshot = currentHistoryRemoteRegistered
        val mediaMetadataSnapshot = player.currentMetadata?.takeIf { it.id == mediaId }

        val deferred =
            scope.async {
                withContext(Dispatchers.IO) {
                    val resolvedEventId =
                        eventIdSnapshot
                            ?: insertPlaybackHistoryEvent(
                                mediaId = mediaId,
                                playTimeMs = playedMs,
                                mediaMetadata = mediaMetadataSnapshot,
                            )
                    val remoteRegistered = remoteRegisteredSnapshot || registerRemotePlaybackHistory(mediaId)
                    ImmediateHistoryResult(
                        eventId = resolvedEventId,
                        remoteRegistered = remoteRegistered,
                    )
                }
            }

        historyRecordingJobs[sessionToken] = deferred
        scope.launch {
            val result =
                runCatching { deferred.await() }
                    .onFailure(::reportException)
                    .getOrNull()

            historyRecordingJobs.remove(sessionToken)

            if (result != null) {
                if (currentHistorySessionToken == sessionToken &&
                    !currentHistorySessionQueued &&
                    currentHistoryMediaId == mediaId
                ) {
                    currentHistoryEventId = result.eventId ?: currentHistoryEventId
                    currentHistoryRemoteRegistered = currentHistoryRemoteRegistered || result.remoteRegistered
                } else {
                    updatePendingHistoryFinalization(mediaId, sessionToken, result)
                }
            }

            syncHistoryThresholdJob()
        }
    }

    private suspend fun insertPlaybackHistoryEvent(
        mediaId: String,
        playTimeMs: Long,
        mediaMetadata: moe.rukamori.archivetune.models.MediaMetadata?,
    ): Long? =
        try {
            database.withTransaction {
                if (song(mediaId).first() == null && mediaMetadata != null) {
                    insert(mediaMetadata)
                }

                insert(
                    Event(
                        songId = mediaId,
                        timestamp = LocalDateTime.now(),
                        playTime = playTimeMs,
                    ),
                ).takeIf { it > 0L }
            }
        } catch (_: SQLException) {
            null
        } catch (throwable: Throwable) {
            reportException(throwable)
            null
        }

    private suspend fun registerRemotePlaybackHistory(mediaId: String): Boolean {
        // Reporting to the YouTube account's listen history is keyed off the YouTube video id, so it
        // works regardless of which source (YouTube/Tidal/Qobuz) actually streamed the audio. This
        // opt-out toggle disables only the remote report; the local play-history DB is unaffected
        // (that stays governed by PauseListenHistoryKey).
        if (!dataStore.get(SyncPlaybackToYouTubeHistoryKey, true)) {
            Timber.tag("MusicService").d("Skipping remote YouTube history for %s (sync disabled)", mediaId)
            return false
        }
        if (database
                .song(mediaId)
                .first()
                ?.song
                ?.isLocal == true
        ) {
            return false
        }

        suspend fun registerTracking(playbackTrackingUrl: String): Boolean =
            YouTube
                .registerPlayback(
                    playlistId = null,
                    playbackTracking = playbackTrackingUrl,
                ).onFailure { throwable ->
                    if (throwable is CancellationException) {
                        throw throwable
                    }
                    Timber.tag("MusicService").w(
                        throwable,
                        "Failed to register remote playback history for %s",
                        mediaId,
                    )
                }.onSuccess {
                    YouTube.notifyHistorySynced()
                }.isSuccess

        remotePlaybackTrackingUrlCache[mediaId]?.let { cachedPlaybackTrackingUrl ->
            if (registerTracking(cachedPlaybackTrackingUrl)) {
                return true
            }
            remotePlaybackTrackingUrlCache.remove(mediaId, cachedPlaybackTrackingUrl)
        }

        val remotePlaybackTracking =
            retryWithoutPlaybackLoginContext {
                YTPlayerUtils.playerResponseForMetadata(mediaId)
            }.onFailure { throwable ->
                if (throwable is CancellationException) {
                    throw throwable
                }
                when (throwable) {
                    is YTPlayerUtils.InvalidPlaybackLoginContextException -> {
                        promptLoginRecovery(mediaId, throwable.targetUrl)
                    }

                    is YTPlayerUtils.LoginRequiredForPlaybackException -> {
                        Timber.tag("MusicService").w(
                            throwable,
                            "Playback confirmation is required before refreshing remote playback tracking for %s",
                            mediaId,
                        )
                    }

                    else -> {
                        Timber.tag("MusicService").w(
                            throwable,
                            "Failed to refresh remote playback tracking for %s",
                            mediaId,
                        )
                    }
                }
            }.getOrNull()
                ?.playbackTracking

        val refreshedPlaybackTrackingUrl = remotePlaybackTracking?.remotePlaybackTrackingUrl()
        if (refreshedPlaybackTrackingUrl != null) {
            remotePlaybackTrackingUrlCache[mediaId] = refreshedPlaybackTrackingUrl
            return registerTracking(refreshedPlaybackTrackingUrl)
        }

        return false
    }

    /**
     * Starts (or refreshes) artwork resolution for the now-current track. Tracks that already
     * carry artwork short-circuit inside the resolver, so this only does network work when the
     * current item has no artwork and the Tidal fallback is enabled. Results are committed
     * centrally here so the player UI, notification and palette extractor share one image.
     */
    private fun beginArtworkResolutionForCurrentTrack() {
        val metadata = currentMediaMetadata.value ?: return
        artworkTrackGeneration = artworkResolver.beginTrack(metadata.id)
        if (!metadata.thumbnailUrl.isNullOrBlank()) return
        val generation = artworkTrackGeneration
        artworkResolveJob?.cancel()
        artworkResolveJob =
            scope.launch(SilentHandler) {
                val request =
                    ArtworkRequest(
                        mediaId = metadata.id,
                        title = metadata.title,
                        artists = metadata.artists.map { it.name },
                        album = metadata.album?.title,
                        // ISRC is not tracked in MediaMetadata; Tidal matching uses normalized
                        // artist/album/title + duration scoring instead.
                        isrc = null,
                        durationMs = metadata.duration.takeIf { it > 0 }?.times(1000L),
                        originalArtworkUrl = metadata.thumbnailUrl,
                        isLocal = metadata.thumbnailUrl.isLocalArtworkUri(),
                    )
                val resolved = artworkResolver.resolve(request)
                if (resolved.provider != ArtworkProvider.TIDAL || resolved.url == null) return@launch
                if (!artworkResolver.isCurrent(metadata.id, generation)) {
                    Timber.tag(TAG).d(
                        "artwork commit rejected: stale generation mediaId=%s generation=%d",
                        metadata.id,
                        generation,
                    )
                    return@launch
                }
                commitResolvedArtwork(metadata.id, resolved)
            }
    }

    /** Commits catalog metadata without changing the playable media URI or source cache key. */
    private fun commitResolvedMetadata(
        mediaId: String,
        resolved: MediaMetadata,
    ) {
        if (currentMediaMetadata.value?.id == mediaId) {
            currentMediaMetadata.value = resolved
        }
        queuedMetadataByMediaId[mediaId] = resolved
        val index =
            (0 until player.mediaItemCount).firstOrNull {
                player.getMediaItemAt(it).mediaId == mediaId
            } ?: return
        val item = player.getMediaItemAt(index)
        val platformMetadata =
            item.mediaMetadata
                .buildUpon()
                .setTitle(resolved.title)
                .setSubtitle(resolved.artists.joinToString { it.name })
                .setArtist(resolved.artists.joinToString { it.name })
                .setAlbumTitle(resolved.album?.title)
                .apply {
                    resolved.thumbnailUrl?.toUri()?.let(::setArtworkUri)
                }.build()
        val updated =
            item
                .buildUpon()
                .setTag(resolved)
                .setMediaMetadata(platformMetadata)
                .build()
        runCatching { player.replaceMediaItem(index, updated) }
            .onFailure {
                Timber.tag(TAG).w(it, "metadata: failed to update MediaItem metadata mediaId=%s", mediaId)
            }
    }

    /** Commits a resolved fallback artwork to the metadata flow and the queued MediaItem. */
    private fun commitResolvedArtwork(
        mediaId: String,
        resolved: ResolvedArtwork,
    ) {
        val url = resolved.url ?: return
        Timber.tag(TAG).d(
            "artwork commit mediaId=%s provider=%s identity=%s confidence=%s",
            mediaId,
            resolved.provider,
            resolved.artworkIdentity,
            resolved.matchConfidence?.let { "%.2f".format(it) } ?: "-",
        )
        // 1. The exposed metadata flow drives player UI, mini player and palette extraction.
        currentMediaMetadata.value
            ?.takeIf { it.id == mediaId }
            ?.let { current -> currentMediaMetadata.value = current.copy(thumbnailUrl = url) }
        // 2. The queued MediaItem drives notification, widgets, Android Auto and Cast metadata.
        val index =
            (0 until player.mediaItemCount).firstOrNull {
                player.getMediaItemAt(it).mediaId == mediaId
            } ?: return
        val item = player.getMediaItemAt(index)
        val updated =
            item
                .buildUpon()
                .setMediaMetadata(
                    item.mediaMetadata
                        .buildUpon()
                        .setArtworkUri(url.toUri())
                        .build(),
                ).build()
        runCatching { player.replaceMediaItem(index, updated) }
            .onFailure {
                Timber.tag(TAG).w(it, "artwork: failed to update MediaItem artwork mediaId=%s", mediaId)
            }
    }

    override fun onTimelineChanged(
        timeline: Timeline,
        reason: Int,
    ) {
        super.onTimelineChanged(timeline, reason)
        cacheQueuedMetadata()
    }

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        super.onMediaItemTransition(mediaItem, reason)
        mediaItem?.metadata?.let { queuedMetadataByMediaId[mediaItem.mediaId] = it }

        // New item: reset the initial-buffer-stall watchdog for the next track.
        initialBufferRecoveryJob?.cancel()
        initialBufferRecoveryJob = null
        initialBufferRecoveryAttemptedMediaId = null
        updateInitialBufferRecovery(player.playbackState)

        // Catch-all for user-initiated skips that bypass PlayerConnection (notification, lock
        // screen, Bluetooth, Android Auto): a SEEK transition while a crossfade fade loop is still
        // running — but NOT the crossfade's own handoff seek (crossfadeHandoffInProgress) — means
        // the user skipped mid-crossfade. Tear the crossfade down so we don't leak the secondary
        // player or leave the primary muted with pauseAtEndOfMediaItems stuck on.
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK &&
            isCrossfading &&
            !crossfadeHandoffInProgress
        ) {
            cancelCrossfade(resetVolume = true, resetPauseAtEnd = true)
        }

        if (sleepTimer.pauseWhenSongEnd) {
            pauseFromSleepTimer()
            return
        }

        beginHistorySession(mediaItem?.mediaId, forceNew = true)

        val currentIndex = player.currentMediaItemIndex
        val queue = player.mediaItems.map { it.metadata }
        if (queue.any { it != null }) {
            lyricsPreloadManager?.onSongChanged(currentIndex, queue)
        }

        val joined = togetherSessionState.value as? moe.rukamori.archivetune.together.TogetherSessionState.Joined
        if (joined?.role is moe.rukamori.archivetune.together.TogetherRole.Guest &&
            reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
        ) {
            if (!joined.roomState.settings.allowGuestsToControlPlayback) {
                scope.launch(SilentHandler) { applyRemoteRoomState(joined.roomState, force = true) }
                return
            }
            val now = android.os.SystemClock.elapsedRealtime()
            val index = player.currentMediaItemIndex.coerceAtLeast(0)
            val isEcho =
                isTogetherApplyingRemote() ||
                    (now < togetherSuppressEchoUntilElapsedMs && togetherLastRemoteAppliedIndex == index)
            if (!isEcho) {
                val trackId = (mediaItem?.metadata ?: player.currentMetadata)?.id?.trim().orEmpty()
                requestTogetherControl(
                    if (trackId.isBlank()) {
                        moe.rukamori.archivetune.together.ControlAction.SeekToIndex(
                            index = index,
                            positionMs = player.currentPosition.coerceAtLeast(0L),
                        )
                    } else {
                        moe.rukamori.archivetune.together.ControlAction.SeekToTrack(
                            trackId = trackId,
                            positionMs = player.currentPosition.coerceAtLeast(0L),
                        )
                    },
                )
            }
        }

        val timelineEmpty = player.currentTimeline.isEmpty || player.mediaItemCount == 0 || player.currentMediaItem == null
        currentMediaMetadata.value = if (timelineEmpty) null else (mediaItem?.metadata ?: player.currentMetadata)

        beginArtworkResolutionForCurrentTrack()

        widgetUpdater.update()

        scrobbleManager?.onSongStop()

        if (!timelineEmpty &&
            dataStore.get(AutoLoadMoreKey, true) &&
            reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT &&
            player.repeatMode == REPEAT_MODE_OFF
        ) {
            // No redundant seeding update check.
        }

        // Auto-load more from queue if available
        if (!suppressAutoPlayback &&
            !timelineEmpty &&
            dataStore.get(AutoLoadMoreKey, true) &&
            reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT &&
            player.mediaItemCount - player.currentMediaItemIndex <= 5 &&
            currentQueue.hasNextPage() &&
            player.repeatMode == REPEAT_MODE_OFF
        ) {
            scope.launch(SilentHandler) {
                val mediaItems =
                    currentQueue
                        .nextPage()
                        .filterPlaybackContent(
                            hideExplicit = dataStore.get(HideExplicitKey, false),
                            hideVideo = dataStore.get(HideVideoKey, false),
                        )
                if (player.playbackState != STATE_IDLE) {
                    player.addMediaItems(mediaItems.drop(1))
                } else {
                    requestDiscordSync(
                        reason = "player_idle_after_queue_extension",
                        force = true,
                    )
                }
            }
        }

        if (!suppressAutoPlayback &&
            !initialQueueLoadInProgress &&
            !timelineEmpty &&
            dataStore.get(AutoLoadMoreKey, true) &&
            reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT &&
            player.repeatMode == REPEAT_MODE_OFF &&
            player.mediaItemCount - player.currentMediaItemIndex <= 3 &&
            currentQueue.shouldBootstrapInfiniteQueue()
        ) {
            onInfiniteQueueEnabled(currentQueue.infiniteQueueSeedMediaId())
        }

        if (player.playWhenReady && player.playbackState == Player.STATE_READY) {
            scrobbleManager?.onSongStart(player.currentMetadata, duration = player.duration)
        }

        scope.launch {
            val shouldSave = withContext(Dispatchers.IO) { dataStore.get(PersistentQueueKey, true) }
            if (shouldSave) {
                saveQueueToDisk()
            }
        }
        ensurePresenceManager()
        if (!isCrossfading) {
            scheduleCrossfade()
        }
        // Prefetch the stream URL for the next-up song so the user-visible
        // "skip to next" feels instant. The prefetch runs on a background IO
        // coroutine and is cancelled/replaced whenever a new transition fires.
        // Skipped in low-data mode to honor the user's data-saver preference.
        prefetchNextMediaItemStream()
    }

    private fun isCurrentPlaybackItemLocal(currentMediaMetadata: MediaMetadata): Boolean =
        currentSong.value?.song?.isLocal == true ||
            currentMediaMetadata.id.trim().isLocalMediaId() ||
            player.currentMediaItem
                ?.localConfiguration
                ?.uri
                ?.shouldBypassPlayerCache() == true

    /**
     * In-memory cache of resolved [DirectStream]s (Qobuz / Tidal) keyed by media id.
     * Populated by [prefetchNextMediaItemStream] so the next song's lossless
     * stream URL is already known when the user skips to it — turning a
     * ~1-3 second Qobuz/Tidal resolution into a cache hit.
     *
     * Entries expire after [DIRECT_STREAM_CACHE_TTL_MS] (5 minutes) so stale
     * stream URLs (which can be revoked by the source) aren't used.
     */
    private val directStreamCache = ConcurrentHashMap<String, CachedDirectStream>()
    private var nextMediaItemPrefetchJob: kotlinx.coroutines.Job? = null

    /**
     * Media id [nextMediaItemPrefetchJob] is resolving, so a still-wanted job is not cancelled.
     * Only meaningful while that job is active — every read is guarded on `isActive`, so a stale
     * id left behind by a finished job is inert and needs no clearing.
     */
    @Volatile
    private var prefetchingMediaId: String? = null

    private data class CachedDirectStream(
        val stream: DirectStream,
        val expiresAtMs: Long,
    )

    /**
     * Prefetches the stream URL for the next-up media item in the queue so
     * that when the user skips to it (or auto-advance fires), the URL is
     * already in [playbackUrlCache] (YouTube) or [directStreamCache]
     * (Qobuz / Tidal) and playback starts within ~100ms instead of the
     * usual 1-3 second resolution delay.
     *
     * Idempotent: re-invoking cancels the previous prefetch. Failures are
     * silently swallowed — prefetch is an optimization, not a requirement.
     */
    private fun prefetchNextMediaItemStream() {
        if (player.mediaItemCount == 0 || player.currentTimeline.isEmpty) return
        val nextIndex = player.nextMediaItemIndex
        if (nextIndex == C.INDEX_UNSET || nextIndex < 0 || nextIndex >= player.mediaItemCount) return
        val nextItem = player.getMediaItemAt(nextIndex)
        val mediaId = nextItem.mediaId.trim().takeIf { it.isNotBlank() } ?: return

        // Don't cancel a prefetch whose result is still wanted. This used to cancel
        // unconditionally, so skipping twice inside the resolve window killed the in-flight job
        // for the very track being skipped onto — the one case where the work was about to pay
        // off — and that track then resolved from scratch, synchronously, while the user waited.
        // A job for the now-current item is finishing into the same caches the resolver reads.
        val inFlight = prefetchingMediaId
        if (nextMediaItemPrefetchJob?.isActive == true && inFlight != null) {
            val currentId = player.currentMediaItem?.mediaId?.trim()
            if (inFlight == mediaId || inFlight == currentId) return
        }
        nextMediaItemPrefetchJob?.cancel()
        // Skip prefetch for local/telegram files (no network resolution needed).
        if (mediaId.isLocalMediaId() || mediaId.isTelegramMediaId()) return
        // Skip if we already have a fresh cached entry.
        if (playbackUrlCache[mediaId] != null) return
        if (directStreamCache[mediaId]?.let { it.expiresAtMs > System.currentTimeMillis() } == true) return
        // Skip in low-data mode (user wants minimal background data).
        if (isLowDataModeActive()) return

        prefetchingMediaId = mediaId
        nextMediaItemPrefetchJob =
            scope.launch(Dispatchers.IO + SilentHandler) {
                runCatching {
                    Timber.tag(TAG).d("Prefetching stream URL for next media item: %s", mediaId)
                    // First, try multi-source (Qobuz / Tidal) resolution.
                    // If a lossless source is configured and matches, cache it.
                    val lowData = isLowDataModeActive()
                    if (!lowData) {
                        val dataSpec = DataSpec.Builder()
                            .setUri("placeholder:$mediaId".toUri())
                            .setKey(mediaId)
                            .build()
                        val resolved = resolveMultiSourceDataSpec(dataSpec, mediaId, lowData, isPrefetch = true)
                        if (resolved != null) {
                            // resolveMultiSourceDataSpec already cached the
                            // stream URL via applyDirectStream; we just need
                            // to record it in directStreamCache so the next
                            // call can find it without re-resolving.
                            // The cache is implicit in tidalActiveMediaIds +
                            // sourceCacheKey() — we don't need to duplicate it.
                            Timber.tag(TAG).d("Prefetch: lossless stream resolved for %s", mediaId)
                            return@runCatching
                        }
                    }
                    // Fall back to YouTube stream URL prefetch.

                    val result =
                        retryWithoutPlaybackLoginContext {
                            YTPlayerUtils.playerResponseForPlayback(
                                mediaId,
                                audioQuality = if (lowData) AudioQuality.LOW else audioQuality,
                                connectivityManager = connectivityManager,
                                preferredStreamClient = preferredStreamClient,
                                networkMetered = lowData,
                            )
                        }
                    result.onSuccess { playbackData ->
                        val expiresAtMs = System.currentTimeMillis() +
                            (playbackData.streamExpiresInSeconds.coerceAtLeast(1) * 1000L)
                        playbackUrlCache[mediaId] = AuthScopedCacheValue(
                            url = playbackData.streamUrl,
                            expiresAtMs = expiresAtMs,
                            authFingerprint = playbackData.authFingerprint,
                        )
                        Timber.tag(TAG).d(
                            "Prefetch: YouTube stream URL cached for %s (expires in %ds)",
                            mediaId,
                            playbackData.streamExpiresInSeconds,
                        )
                    }
                }.onFailure { error ->
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    Timber.tag(TAG).d(error, "Prefetch failed for %s (will resolve on play)", mediaId)
                }
            }
    }

    private fun Queue.shouldBootstrapInfiniteQueue(): Boolean =
        preloadItem != null || !hasNextPage()

    private fun Queue.infiniteQueueSeedMediaId(): String? =
        preloadItem?.id?.trim()?.takeIf { it.isNotBlank() }

    override fun onPlaybackStateChanged(
        @Player.State playbackState: Int,
    ) {
        super.onPlaybackStateChanged(playbackState)

        updateHistoryTrackingPlaybackState()
        updateInitialBufferRecovery(playbackState)
        if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
            enqueueCurrentHistorySessionForFinalization()
            if (!isCrossfading || playbackState == Player.STATE_IDLE) {
                cancelCrossfade(resetVolume = true, resetPauseAtEnd = true)
            } else if (playbackState == Player.STATE_ENDED) {
                // The crossfade was supposed to hand off to the next item before STATE_ENDED
                // fired, but it didn't complete in time. A stale `isCrossfading = true` here
                // means `pauseAtEndOfMediaItems` is still `true`, which prevents ExoPlayer's
                // auto-advance — the player stops at the end of the current song and the user
                // has to press Play to continue. Tear the crossfade down so the player can
                // auto-advance normally. The crossfade's secondary player (if any) was either
                // already promoting itself or never became ready — either way, releasing it
                // here is safe and prevents a stuck `pauseAtEndOfMediaItems`.
                cancelCrossfade(resetVolume = true, resetPauseAtEnd = true)
            }
            if (playbackState == Player.STATE_ENDED &&
                !suppressAutoPlayback &&
                dataStore.get(AutoLoadMoreKey, true) &&
                player.repeatMode == REPEAT_MODE_OFF &&
                player.currentMediaItem != null &&
                currentQueue.shouldBootstrapInfiniteQueue()
            ) {
                onInfiniteQueueEnabled(currentQueue.infiniteQueueSeedMediaId())
            }
        } else if (playbackState == Player.STATE_READY) {
            // Source-switch completion hook: once the deterministic re-create reaches READY,
            // the new MediaSource is fully prepared and the live flows are authoritative again.
            // Apply the volume captured before the switch, cancel the delayed reassert (it
            // already did its job / is redundant now), and re-arm the watchdog — the watchdog
            // self-cancels while the player is IDLE mid-switch, so READY is where it must be
            // restarted.
            if (sourceSwitchPending) {
                sourceSwitchPending = false
                sourceSwitchReassertJob?.cancel()
                sourceSwitchReassertJob = null
                Timber.tag(TAG).d(
                    "source switch READY: captured=%s live=%s actual=%s state=%s",
                    sourceSwitchExpectedVolume,
                    currentEffectivePlayerVolume(),
                    player.volume,
                    player.playWhenReady,
                )
                applyEffectiveVolumeImmediately(sourceSwitchExpectedVolume)
                ensureAudiblePlaybackVolume("source_switch_ready")
            }
            updateAudiblePlaybackRecovery()
            scheduleCrossfade()
        }

        widgetUpdater.update()
        widgetUpdater.updateProgressTracking()

        scope.launch {
            val shouldSave = withContext(Dispatchers.IO) { dataStore.get(PersistentQueueKey, true) }
            if (shouldSave) {
                saveQueueToDisk()
            }
        }
    }

    override fun onPlayWhenReadyChanged(
        playWhenReady: Boolean,
        reason: Int,
    ) {
        super.onPlayWhenReadyChanged(playWhenReady, reason)
        secondaryCrossfadePlayer?.let { secondaryPlayer ->
            if (isCrossfading) {
                val isEndOfOutgoingItemPause =
                    !playWhenReady &&
                        reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM &&
                        localPlayer.pauseAtEndOfMediaItems
                if (!isEndOfOutgoingItemPause) {
                    crossfadePlaybackRequested = playWhenReady
                    secondaryPlayer.playWhenReady = crossfadePlaybackRequested
                    if (crossfadePlaybackRequested) {
                        secondaryPlayer.play()
                    } else {
                        secondaryPlayer.pause()
                    }
                } else if (!crossfadeHandoffInProgress) {
                    secondaryPlayer.playWhenReady = crossfadePlaybackRequested
                    if (crossfadePlaybackRequested) {
                        secondaryPlayer.play()
                    }
                }
            }
        }
        if (playWhenReady && !isCrossfading) {
            scheduleCrossfade()
        } else if (!playWhenReady && !isCrossfading) {
            crossfadeTriggerJob?.cancel()
            crossfadeTriggerJob = null
            localPlayer.pauseAtEndOfMediaItems = false
            releaseSecondaryCrossfadePlayer()
        }
    }

    override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
        super.onPlaybackParametersChanged(playbackParameters)
        secondaryCrossfadePlayer?.playbackParameters = playbackParameters
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        super.onIsPlayingChanged(isPlaying)
        updateInitialBufferRecovery(player.playbackState)
        secondaryCrossfadePlayer?.let { secondaryPlayer ->
            if (isCrossfading && !crossfadeHandoffInProgress) {
                if (isPlaying) {
                    secondaryPlayer.play()
                } else {
                    secondaryPlayer.pause()
                }
            }
        }
        if (isPlaying && !isCrossfading) {
            scheduleCrossfade()
        }
        updateAudiblePlaybackRecovery()

        widgetUpdater.update()
        widgetUpdater.updateProgressTracking()
    }

    override fun onEvents(
        player: Player,
        events: Player.Events,
    ) {
        val currentMediaId = player.currentMediaItem?.mediaId
        if (currentMediaId == null && currentHistoryMediaId != null) {
            beginHistorySession(null, forceNew = true)
        } else if (currentHistoryMediaId == null && currentMediaId != null) {
            beginHistorySession(currentMediaId)
        }
        if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
            playbackStreamRecoveryTracker.onMediaItemChanged(currentMediaId)
            // Reset codec-recovery budget for the new media item.
            if (currentMediaId != codecRecoveryMediaId) {
                codecRecoveryMediaId = currentMediaId
                codecRecoveryAttemptCount = 0
            }
        }
        if (
            (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) && player.playbackState == Player.STATE_READY) ||
            (events.contains(Player.EVENT_IS_PLAYING_CHANGED) && player.isPlaying)
        ) {
            playbackStreamRecoveryTracker.onPlaybackRecovered(currentMediaId)
            // Codec successfully reached READY / isPlaying after a recovery re-prepare —
            // the current codec instance is healthy, so reset the per-media counter so the
            // NEXT background+pause cycle gets a fresh budget.
            if (codecRecoveryAttemptCount > 0) {
                Timber.tag("MusicService").i(
                    "Codec recovery succeeded for %s after %d attempt(s); resetting codec-recovery budget",
                    currentMediaId,
                    codecRecoveryAttemptCount,
                )
                codecRecoveryAttemptCount = 0
            }
            currentMediaId
                ?.let(playbackUrlCache::get)
                ?.url
                ?.let(YTPlayerUtils::markStreamUrlSuccessful)
            ensureAudiblePlaybackVolume("player_event")
        }
        if (events.containsAny(
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_PLAY_WHEN_READY_CHANGED,
                Player.EVENT_IS_PLAYING_CHANGED,
            )
        ) {
            updateAudiblePlaybackRecovery()
        }
        if (events.contains(Player.EVENT_MEDIA_METADATA_CHANGED)) {
            currentMediaMetadata.value = player.currentMetadata
        }
        if (events.containsAny(
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_PLAY_WHEN_READY_CHANGED,
                Player.EVENT_IS_PLAYING_CHANGED,
            )
        ) {
            updateHistoryTrackingPlaybackState()
        }
        val joined = togetherSessionState.value as? moe.rukamori.archivetune.together.TogetherSessionState.Joined
        if (joined?.role is moe.rukamori.archivetune.together.TogetherRole.Guest &&
            events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED)
        ) {
            if (!joined.roomState.settings.allowGuestsToControlPlayback) {
                scope.launch(SilentHandler) { applyRemoteRoomState(joined.roomState, force = true) }
            } else {
                val now = android.os.SystemClock.elapsedRealtime()
                val playWhenReady = this.player.playWhenReady
                val isEcho =
                    isTogetherApplyingRemote() ||
                        (
                            now < togetherSuppressEchoUntilElapsedMs &&
                                togetherLastRemoteAppliedPlayWhenReady != null &&
                                togetherLastRemoteAppliedPlayWhenReady == playWhenReady
                        )
                if (!isEcho) {
                    val action =
                        if (playWhenReady) {
                            moe.rukamori.archivetune.together.ControlAction.Play
                        } else {
                            moe.rukamori.archivetune.together.ControlAction.Pause
                        }
                    requestTogetherControl(action)
                }
            }
        }
        if (events.contains(Player.EVENT_DEVICE_VOLUME_CHANGED)) {
            handleDeviceMuteStateChanged()
        }
        if (events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED) && isDeviceMutedNow() && this.player.playWhenReady) {
            handleDeviceMuteStateChanged(playbackRequestedWhileMuted = true)
        }
        if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) &&
            (this.player.playbackState == Player.STATE_IDLE || this.player.playbackState == Player.STATE_ENDED)
        ) {
            wasAutoPausedByDeviceMute = false
            unregisterMuteRecoveryObserver()
            updateAudiblePlaybackRecovery()
        }
        if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) &&
            isDeviceMutedNow() &&
            this.player.playWhenReady
        ) {
            handleDeviceMuteStateChanged(playbackRequestedWhileMuted = true)
        }
        if (events.containsAny(
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_PLAY_WHEN_READY_CHANGED,
            )
        ) {
            if (player.playWhenReady && shouldKeepAudioEffectSessionOpen()) {
                ensureAudioFocusForActivePlayback()
            }
            updateWakeLock()
            if (hasResumablePlaybackNotification()) {
                cancelIdleStop()
                promoteToStartedService()
                ensureStartedAsForeground()
            } else {
                scheduleStopIfIdle()
            }
        }

        if (events.containsAny(EVENT_TIMELINE_CHANGED, EVENT_POSITION_DISCONTINUITY)) {
            currentMediaMetadata.value = player.currentMetadata
            requestDiscordSync(
                reason = "timeline_or_position_discontinuity",
                force = true,
            )
            // Snapshot player state on the Main thread up front. Media3's player must only be
            // accessed on its application (main) thread; reading player.* inside the IO coroutine
            // below throws "Player is accessed on the wrong thread" and silently breaks scrobbling.
            val timelineMediaId = player.currentMediaItem?.mediaId
            val timelineMetadata = player.currentMetadata
            val timelineDuration = player.duration
            val timelinePosition = player.currentPosition
            scope.launch {
                try {
                    val song =
                        if (timelineMediaId != null) {
                            withContext(Dispatchers.IO) { database.song(timelineMediaId).first() }
                        } else {
                            null
                        }
                    val finalSong =
                        resolvePresenceSong(
                            dbSong = song,
                            mediaMetadata = timelineMetadata,
                            durationMs = timelineDuration,
                        ) ?: return@launch
                    try {
                        val lbEnabled = dataStore.get(ListenBrainzEnabledKey, false)
                        val lbToken = dataStore.get(ListenBrainzTokenKey, "")
                        if (lbEnabled && !lbToken.isNullOrBlank()) {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    ListenBrainzManager.submitPlayingNow(
                                        this@MusicService,
                                        lbToken,
                                        finalSong,
                                        timelinePosition,
                                    )
                                } catch (ie: Exception) {
                                    Timber.tag("MusicService").v(ie, "ListenBrainz playing_now submit failed on transition")
                                }
                            }
                        }

                        // Last.fm now playing - handled by ScrobbleManager
                    } catch (_: Exception) {
                    }
                } catch (e: Exception) {
                    Timber.tag("MusicService").v(e, "timeline/position follow-up work failed")
                }
            }
        }
        if (events.contains(EVENT_TIMELINE_CHANGED) && !isCrossfading) {
            scheduleCrossfade()
        }

        // Also handle immediate update for play state and media item transition events explicitly
        if (events.containsAny(
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_PLAY_WHEN_READY_CHANGED,
                Player.EVENT_IS_PLAYING_CHANGED,
                Player.EVENT_MEDIA_ITEM_TRANSITION,
            )
        ) {
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                currentMediaMetadata.value = player.currentMetadata
            }
            requestDiscordSync(
                reason = "is_playing_or_media_item_transition",
                force = true,
            )
            // Capture player state on Main thread
            val currentMediaId = player.currentMediaItem?.mediaId
            val currentMetadata = player.currentMetadata
            val currentPosition = player.currentPosition
            val currentDuration = player.duration
            val isPlaying = player.isPlaying

            scope.launch {
                try {
                    val song =
                        if (currentMediaId !=
                            null
                        ) {
                            withContext(Dispatchers.IO) { database.song(currentMediaId).first() }
                        } else {
                            null
                        }
                    val finalSong =
                        resolvePresenceSong(
                            dbSong = song,
                            mediaMetadata = currentMetadata,
                            durationMs = currentDuration,
                        ) ?: return@launch
                    try {
                        val lbEnabled = withContext(Dispatchers.IO) { dataStore.get(ListenBrainzEnabledKey, false) }
                        val lbToken = withContext(Dispatchers.IO) { dataStore.get(ListenBrainzTokenKey, "") }
                        if (lbEnabled && !lbToken.isNullOrBlank()) {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    ListenBrainzManager.submitPlayingNow(this@MusicService, lbToken, finalSong, currentPosition)
                                } catch (ie: Exception) {
                                    Timber
                                        .tag(
                                            "MusicService",
                                        ).v(ie, "ListenBrainz playing_now submit failed for isPlaying/mediaTransition")
                                }
                            }
                        }

                        // Last.fm now playing - handled by ScrobbleManager
                    } catch (_: Exception) {
                    }
                } catch (e: Exception) {
                    Timber.tag("MusicService").v(e, "isPlaying/mediaTransition follow-up work failed")
                }
            }
        }

        if (events.containsAny(Player.EVENT_IS_PLAYING_CHANGED)) {
            // Scrobble: Track play/pause state
            scrobbleManager?.onPlayerStateChanged(player.isPlaying, player.currentMetadata, duration = player.duration)
        }

        // Persist queue on play/pause so a force-stop right after pausing still restores the correct position
        if (events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED) && player.mediaItemCount > 0) {
            scope.launch(SilentHandler) {
                if (withContext(Dispatchers.IO) { dataStore.get(PersistentQueueKey, true) }) {
                    saveQueueToDisk()
                }
            }
        }
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        super.onPositionDiscontinuity(oldPosition, newPosition, reason)
        val isSeekDiscontinuity =
            reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT
        if (isSeekDiscontinuity) {
            if (!crossfadeHandoffInProgress) {
                cancelCrossfade(resetVolume = true, resetPauseAtEnd = true)
            }
        }
        if (!isCrossfading && !crossfadeHandoffInProgress) {
            scheduleCrossfade()
        }
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        updateNotification()
        val joined = togetherSessionState.value as? moe.rukamori.archivetune.together.TogetherSessionState.Joined
        if (joined?.role is moe.rukamori.archivetune.together.TogetherRole.Guest) {
            if (!isTogetherApplyingRemote()) {
                if (!joined.roomState.settings.allowGuestsToControlPlayback) {
                    scope.launch(SilentHandler) { applyRemoteRoomState(joined.roomState, force = true) }
                    return
                }
                requestTogetherControl(
                    moe.rukamori.archivetune.together.ControlAction.SetShuffleEnabled(
                        shuffleEnabled = shuffleModeEnabled,
                    ),
                )
            }
            return
        }
        if (shuffleModeEnabled) {
            applyCurrentFirstShuffleOrder()
        }

        // Save state when shuffle mode changes - must be on Main thread to access player
        scope.launch {
            if (dataStore.get(PersistentQueueKey, true)) {
                saveQueueToDisk()
            }
        }
        if (!isCrossfading) {
            scheduleCrossfade()
        }
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
        updateNotification()
        val joined = togetherSessionState.value as? moe.rukamori.archivetune.together.TogetherSessionState.Joined
        if (joined?.role is moe.rukamori.archivetune.together.TogetherRole.Guest) {
            if (!isTogetherApplyingRemote()) {
                if (!joined.roomState.settings.allowGuestsToControlPlayback) {
                    scope.launch(SilentHandler) { applyRemoteRoomState(joined.roomState, force = true) }
                    return
                }
                requestTogetherControl(
                    moe.rukamori.archivetune.together.ControlAction.SetRepeatMode(
                        repeatMode = repeatMode,
                    ),
                )
            }
            return
        }
        scope.launch {
            dataStore.edit { settings ->
                settings[RepeatModeKey] = repeatMode
            }
        }

        // Save state when repeat mode changes - must be on Main thread to access player
        scope.launch {
            if (dataStore.get(PersistentQueueKey, true)) {
                saveQueueToDisk()
            }
        }
        if (!isCrossfading) {
            scheduleCrossfade()
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        super.onPlayerError(error)

        val currentMediaId = player.currentMediaItem?.mediaId ?: return
        val isLocalMedia = currentMediaId.isLocalMediaId()

        val isFullyDownloadedMedia =
            runCatching {
                val contentLength =
                    downloadCache
                        .getContentMetadata(currentMediaId)
                        .get(ContentMetadata.KEY_CONTENT_LENGTH, -1L)
                contentLength > 0L && downloadCache.isCached(currentMediaId, 0L, contentLength)
            }.getOrDefault(false)

        // Check for cached spans across all source-prefixed keys. Even if the
        // cache metadata is missing the content length (which would make
        // `isFullyDownloadedMedia` false), having cached spans means the song
        // was at least partially downloaded and we should try to play it from
        // cache instead of waiting for network. This is the key fix for the
        // "downloaded songs don't play when offline" bug — the cache metadata
        // sometimes doesn't have the content length persisted, but the bytes
        // are still there.
        val hasAnyCachedData =
            isFullyDownloadedMedia ||
                runCatching {
                    downloadCache.getCachedSpans(currentMediaId).isNotEmpty() ||
                        playerCache.getCachedSpans(currentMediaId).isNotEmpty() ||
                        // Also check source-prefixed keys (qobuz:, tidal:, deezer:) —
                        // downloads from external lossless sources are cached under
                        // these keys, not the bare mediaId.
                        downloadCache.getCachedSpans("qobuz:$currentMediaId").isNotEmpty() ||
                        downloadCache.getCachedSpans("tidal:$currentMediaId").isNotEmpty() ||
                        downloadCache.getCachedSpans("deezer:$currentMediaId").isNotEmpty() ||
                        playerCache.getCachedSpans("qobuz:$currentMediaId").isNotEmpty() ||
                        playerCache.getCachedSpans("tidal:$currentMediaId").isNotEmpty() ||
                        playerCache.getCachedSpans("deezer:$currentMediaId").isNotEmpty()
                }.getOrDefault(false)

        val isConnectionError =
            (error.cause?.cause is PlaybackException) &&
                (error.cause?.cause as PlaybackException).errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED

        // Offline bypass: if we have ANY cached data for this mediaId (a
        // complete download, or even partial cached spans under the bare or
        // source-prefixed keys), do NOT call `waitOnNetworkError()` — the
        // cache resolver in `resolveCachedDataSpec` will serve the cached
        // bytes on the next prepare(). Calling `waitOnNetworkError()` here
        // would freeze playback indefinitely even though the song is fully
        // available locally.
        if (!isLocalMedia && !isFullyDownloadedMedia && !hasAnyCachedData &&
            (!isNetworkConnected.value || isConnectionError)
        ) {
            waitOnNetworkError()
            return
        }

        // If we have cached data but the player still errored (typically
        // because the resolver tried the network first and failed), force a
        // re-prepare so the cache resolver gets a chance to serve the bytes.
        // This handles the case where the initial resolvePlaybackDataSpec
        // call fell through to the YouTube resolver before the cache was
        // consulted, and the YouTube resolver failed because we're offline.
        if (!isLocalMedia && hasAnyCachedData && (!isNetworkConnected.value || isConnectionError)) {
            Timber.tag("MusicService").i(
                "Offline playback recovery for %s (fullyCached=%b, hasSpans=%b); re-preparing to force cache read",
                currentMediaId,
                isFullyDownloadedMedia,
                hasAnyCachedData,
            )
            // Invalidate any stale stream URL cache so the resolver is forced
            // to consult the cache first on the next prepare.
            playbackUrlCache.remove(currentMediaId)
            if (playbackStreamRecoveryTracker.registerRetryAttempt(currentMediaId)) {
                player.prepare()
                return
            }
        }

        if (error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND) {
            scope.launch(Dispatchers.IO) {
                runCatching { downloadCache.removeResource(currentMediaId) }
                runCatching { playerCache.removeResource(currentMediaId) }
            }
        }

        val streamHttpFailure = findStreamHttpFailure(error)
        if (streamHttpFailure != null) {
            if (streamHttpFailure.responseCode in RETRYABLE_STREAM_RESPONSE_CODES &&
                retryPlaybackAfterStreamFailure(currentMediaId, isFullyDownloadedMedia, streamHttpFailure)
            ) {
                return
            }
        }

        if (!isLocalMedia && isCacheCorruptionError(error, hasAnyCachedData)) {
            // Snapshot on the Main thread before dispatching; these can change.
            val mediaItemIndex = player.currentMediaItemIndex
            val resumePosition = player.currentPosition.coerceAtLeast(0L)

            Timber.tag("MusicService").w(
                "Cache corruption / truncated stream for %s (fullyCached=%b); purging caches then retrying",
                currentMediaId,
                isFullyDownloadedMedia,
            )

            playbackUrlCache.remove(currentMediaId)
            contentLengthCache.remove(currentMediaId)
            YTPlayerUtils.invalidateCachedStreamUrls(currentMediaId)

            scope.launch(Dispatchers.IO) {
                // Always purge the streaming/player cache.
                runCatching { playerCache.removeResource(currentMediaId) }
                // Keep a complete offline download in place; deleting a user's saved download
                // to recover from a read error is surprising. Only purge partial entries.
                if (!isFullyDownloadedMedia) {
                    runCatching { downloadCache.removeResource(currentMediaId) }
                } else {
                    Timber.tag("MusicService").w(
                        "Keeping offline download for %s; corruption may require manual re-download",
                        currentMediaId,
                    )
                }

                // Re-prepare ONLY after the purge completes, back on the Main thread, so the
                // fresh prepare cannot re-read the spans we just deleted.
                withContext(Dispatchers.Main) {
                    if (playbackStreamRecoveryTracker.registerRetryAttempt(currentMediaId)) {
                        player.seekTo(mediaItemIndex, resumePosition)
                        player.prepare()
                    } else {
                        // Retry budget for this item is spent; fall back to configured behavior.
                        if (dataStore.get(AutoSkipNextOnErrorKey, false)) skipOnError() else stopOnError()
                    }
                }
            }
            return
        }

        if (!isLocalMedia && !isFullyDownloadedMedia && YTPlayerUtils.isBotDetectionException(error)) {
            playbackUrlCache.remove(currentMediaId)
            YTPlayerUtils.invalidateCachedStreamUrls(currentMediaId)
            YTPlayerUtils.clearPlaybackAuthCaches()
            if (playbackStreamRecoveryTracker.registerRetryAttempt(currentMediaId)) {
                Timber.tag("MusicService").i("Retrying playback for %s after bot-detection source error", currentMediaId)
                player.prepare()
                return
            }
        }

        if (!isLocalMedia && !isFullyDownloadedMedia && YTPlayerUtils.isBadStreamPlayerResponseException(error)) {
            playbackUrlCache.remove(currentMediaId)
            YTPlayerUtils.invalidateCachedStreamUrls(currentMediaId)
            if (playbackStreamRecoveryTracker.registerRetryAttempt(currentMediaId)) {
                scope.launch(Dispatchers.IO) {
                    runCatching {
                        YTPlayerUtils.recoverFromBadStreamPlayerResponse(currentMediaId)
                    }.onFailure {
                        Timber.tag("MusicService").w(
                            it,
                            "Failed to refresh stream session for %s after all stream clients failed",
                            currentMediaId,
                        )
                        reportException(it)
                    }
                    withContext(Dispatchers.Main) {
                        if (player.currentMediaItem?.mediaId == currentMediaId) {
                            Timber.tag("MusicService").i(
                                "Retrying playback for %s after refreshing stream session",
                                currentMediaId,
                            )
                            player.prepare()
                        }
                    }
                }
                return
            }
        }

        if (!isLocalMedia && !isFullyDownloadedMedia && isRetryableRemoteParserFailure(error)) {
            val failedUrl =
                playbackUrlCache[currentMediaId]?.url
            playbackUrlCache.remove(currentMediaId)
            contentLengthCache.remove(currentMediaId)
            YTPlayerUtils.invalidateCachedStreamUrls(currentMediaId)
            failedUrl
                ?.let(StreamClientUtils::resolveRequestProfile)
                ?.clientKey
                ?.takeIf(String::isNotEmpty)
                ?.let { clientKey ->
                    YTPlayerUtils.markStreamClientFailed(
                        videoId = currentMediaId,
                        clientKey = clientKey,
                        httpStatusCode = null,
                    )
                }
            if (playbackStreamRecoveryTracker.registerRetryAttempt(currentMediaId)) {
                Timber.tag("MusicService").i(
                    "Retrying playback for %s after parser source error %d",
                    currentMediaId,
                    error.errorCode,
                )
                player.prepare()
                return
            }
        }

        // MediaCodec decoder-state recovery.
        //
        // When streaming lossless ALAC (.m4a) from Telegram on devices with a flaky hardware
        // ALAC decoder (notably MediaTek's c2.mtk.alac.decoder), the OS may release the codec
        // out from under ExoPlayer while the app is backgrounded + paused for a few minutes
        // (low-memory reclamation). The renderer's next queueInputBuffer() call then throws
        // one of:
        //   IllegalStateException("queueInputBuffer() is valid only at Executing states;
        //   currently at Released state")    — renderer races against an already-released codec
        //   CodecException("Error 0x80000000") — generic undefined MediaCodec error from the
        //   same root cause on MediaTek's c2.mtk.alac.decoder
        // Both surface as a MediaCodecDecoderException, with errorCode == ERROR_CODE_DECODING_FAILED
        // (4003). The codec itself is recoverable — the player just needs to be re-prepared so a
        // fresh codec instance is instantiated. Re-prepare resumes from the current position; no
        // queue reshuffle needed.
        //
        // IMPORTANT: the failure can recur — once at song start (codec init race / transient
        // resource pressure) and again on the next background+pause cycle. The single-shot
        // playbackStreamRecoveryTracker is too tight for this; we keep a SEPARATE per-media
        // budget (codecRecoveryMaxAttempts = 4) so transient codec reclamation doesn't kill
        // the queue. The counter resets when playback reaches READY/playing (see onEvents),
        // so each successful recovery earns a fresh budget for the next cycle.
        if (isMediaCodecStateError(error)) {
            val resumePosition = player.currentPosition.coerceAtLeast(0L)
            val mediaItemIndex = player.currentMediaItemIndex

            // Reset budget if we've moved to a different media item since the last attempt.
            if (currentMediaId != codecRecoveryMediaId) {
                codecRecoveryMediaId = currentMediaId
                codecRecoveryAttemptCount = 0
            }
            val attemptNumber = codecRecoveryAttemptCount + 1
            val withinBudget = attemptNumber <= codecRecoveryMaxAttempts

            Timber.tag("MusicService").w(
                "MediaCodec state error for %s (errorCode=%s, causeChain=%s); recovery attempt %d/%d",
                currentMediaId,
                error.errorCodeName,
                describeCauseChain(error),
                attemptNumber,
                codecRecoveryMaxAttempts,
            )

            if (withinBudget) {
                codecRecoveryAttemptCount = attemptNumber
                scope.launch(Dispatchers.Main) {
                    try {
                        // Give the OS a moment to finish releasing the dead codec instance
                        // before we ask ExoPlayer to instantiate a fresh one. Without this,
                        // the new codec can race the old one's teardown and fail with the
                        // same 0x80000000 / Released-state signature on the very first
                        // queueInputBuffer() call.
                        if (attemptNumber > 1) {
                            kotlinx.coroutines.delay(400L)
                        }
                        player.seekTo(mediaItemIndex, resumePosition)
                        player.prepare()
                        // Honor the user's prior play/pause intent — if they had paused,
                        // keep it paused after recovery so we don't surprise-start playback.
                        if (!player.playWhenReady) {
                            player.pause()
                        }
                    } catch (recoveryThrowable: Throwable) {
                        Timber.tag("MusicService").e(
                            recoveryThrowable,
                            "Recovery re-prepare failed for %s (attempt %d); falling back to stop-on-error",
                            currentMediaId,
                            attemptNumber,
                        )
                        stopOnError()
                    }
                }
                return
            } else {
                Timber.tag("MusicService").w(
                    "Codec-recovery budget exhausted for %s after %d attempts; giving up",
                    currentMediaId,
                    attemptNumber - 1,
                )
            }
        }
        // One final bounded retry covers transient decoder/container/network failures that do not
        // match the specialised branches above. Retrying the same media item before honoring
        // AutoSkipNextOnError prevents a single short-lived failure from silently advancing the
        // queue. The tracker keeps this from looping forever.
        if (
            !isLocalMedia &&
                !isFullyDownloadedMedia &&
                playbackStreamRecoveryTracker.registerRetryAttempt(currentMediaId)
        ) {
            val shouldResume = player.playWhenReady
            playbackUrlCache.remove(currentMediaId)
            directStreamCache.remove(currentMediaId)
            contentLengthCache.remove(currentMediaId)
            YTPlayerUtils.invalidateCachedStreamUrls(currentMediaId)
            Timber.tag("MusicService").w(
                "Retrying remote playback for %s after unclassified error %s (%s)",
                currentMediaId,
                error.errorCodeName,
                describeCauseChain(error),
            )
            player.prepare()
            if (shouldResume) player.play()
            return
        }


        if (dataStore.get(AutoSkipNextOnErrorKey, false)) {
            skipOnError()
        } else {
            stopOnError()
        }
    }

    private fun isMediaCodecStateError(error: PlaybackException): Boolean =
        isRecoverableMediaCodecStateError(error)

    /** Debug-only helper: short human-readable description of the cause chain for logging. */
    private fun describeCauseChain(error: Throwable): String {
        val causeChain = generateSequence<Throwable>(error) { it.cause }
        return causeChain
            .take(5)
            .joinToString(" -> ") { t ->
                val cls = t.javaClass.simpleName
                val msg = t.message?.take(80)?.replace("\n", " ")?.trim().orEmpty()
                if (msg.isEmpty()) cls else "$cls($msg)"
            }
    }

    private suspend fun trimPlayerCacheToBytes(limitBytes: Long) {
        if (limitBytes <= 0L) return

        withContext(Dispatchers.IO) {
            val cacheDir = StorageLocationRepository.cacheDirectory(this@MusicService, StorageFolderKind.SONG_CACHE)
            val currentSpace = runCatching { playerCache.cacheSpace }.getOrNull() ?: 0L
            var totalBytes = if (currentSpace > 0L) currentSpace else cacheDir.directorySizeBytes()
            if (totalBytes <= limitBytes) return@withContext

            data class Candidate(
                val key: String,
                val lastTouchTimestamp: Long,
                val sizeBytes: Long,
            )

            val candidates =
                runCatching {
                    playerCache.keys
                        .mapNotNull { key ->
                            runCatching {
                                val spans = playerCache.getCachedSpans(key)
                                if (spans.isEmpty()) return@runCatching null
                                val oldestTouch = spans.minOf { it.lastTouchTimestamp }
                                val sizeBytes = spans.sumOf { it.length }
                                Candidate(key = key, lastTouchTimestamp = oldestTouch, sizeBytes = sizeBytes)
                            }.getOrNull()
                        }.sortedBy { it.lastTouchTimestamp }
                }.getOrNull().orEmpty()

            for (candidate in candidates) {
                if (totalBytes <= limitBytes) break
                val removedSize = candidate.sizeBytes.coerceAtLeast(0L)
                runCatching { playerCache.removeResource(candidate.key) }
                totalBytes -= removedSize
            }
        }
    }

    private fun createPlayerCacheDataSourceFactory(cacheWriteEnabled: Boolean): CacheDataSource.Factory =
        CacheDataSource
            .Factory()
            .setCache(playerCache)
            .setUpstreamDataSourceFactory(createResolvedUpstreamDataSourceFactory())
            .apply {
                if (!cacheWriteEnabled) {
                    setCacheWriteDataSinkFactory(null)
                }
            }.setFlags(FLAG_IGNORE_CACHE_ON_ERROR)

    private fun createCacheDataSource(): CacheDataSource.Factory =
        CacheDataSource
            .Factory()
            .setCache(downloadCache)
            .setUpstreamDataSourceFactory(
                DataSource.Factory {
                    createPlayerCacheDataSourceFactory(
                        cacheWriteEnabled = !isLowDataModeActive(),
                    ).createDataSource()
                },
            ).setCacheWriteDataSinkFactory(null)
            .setFlags(FLAG_IGNORE_CACHE_ON_ERROR)

    private fun createDataSourceFactory(): DataSource.Factory {
        val cachedFactory =
            ResolvingDataSource.Factory(createCacheDataSource()) { dataSpec ->
                resolvePlaybackDataSpec(
                    dataSpec = dataSpec,
                    allowCacheShortCircuit = true,
                )
            }
        val directFactory = createResolvedUpstreamDataSourceFactory()
        val telegramFactory = TelegramDataSource.Factory()
        val tidalProgressiveDashFactory = TidalProgressiveDashDataSource.Factory(mediaOkHttpClient)
        // Deezer needs its own chain rather than cachedFactory's: that one runs the YouTube
        // resolver over the dataSpec, which would rewrite our deezer:// URI. Decryption sits below
        // the cache so what gets cached is already-decrypted audio, letting a replay skip both the
        // CDN fetch and the Blowfish work.
        val deezerFactory =
            CacheDataSource
                .Factory()
                .setCache(playerCache)
                .setUpstreamDataSourceFactory(
                    DeezerDecryptingDataSource.Factory(OkHttpDataSource.Factory(mediaOkHttpClient)),
                ).setCacheWriteDataSinkFactory(
                    CacheDataSink.Factory().setCache(playerCache).setFragmentSize(C.LENGTH_UNSET.toLong()),
                ).setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        return DataSource.Factory {
            SchemeRoutingDataSource(
                cachedFactory = cachedFactory,
                directFactory = directFactory,
                telegramFactory = telegramFactory,
                deezerFactory = deezerFactory,
                tidalProgressiveDashFactory = tidalProgressiveDashFactory,
            )
        }
    }

    private fun createResolvedUpstreamDataSourceFactory(): DataSource.Factory {
        val youtubeMediaFactory =
            DefaultDataSource.Factory(
                this,
                OkHttpDataSource.Factory(mediaOkHttpClient),
            )
        // Resolved source URIs pass through this factory a second time after the outer resolver has
        // selected a provider. Route those private schemes here instead of asking DefaultDataSource
        // to handle an unknown `deezer://` or `tidal-dash://` URI.
        val routingFactory =
            DataSource.Factory {
                ResolvedSchemeRoutingDataSource(
                    defaultFactory = youtubeMediaFactory,
                    deezerFactory = DeezerDecryptingDataSource.Factory(OkHttpDataSource.Factory(mediaOkHttpClient)),
                    tidalProgressiveDashFactory = TidalProgressiveDashDataSource.Factory(mediaOkHttpClient),
                )
            }

        return ResolvingDataSource.Factory(routingFactory) { dataSpec ->
            val scheme = dataSpec.uri.scheme?.lowercase(Locale.US)
            if (scheme == DeezerCrypto.SCHEME || scheme == TidalAudioProvider.PROGRESSIVE_DASH_SCHEME) {
                dataSpec
            } else {
                resolvePlaybackDataSpec(
                    dataSpec = dataSpec,
                    allowCacheShortCircuit = false,
                )
            }
        }
    }

    private fun resolveMediaItemForCast(mediaItem: MediaItem): MediaItem {
        val localConfiguration = mediaItem.localConfiguration ?: return mediaItem
        val uri = localConfiguration.uri
        if (uri.shouldBypassYouTubeResolver()) return mediaItem
        val mediaId = localConfiguration.customCacheKey ?: mediaItem.mediaId
        val dataSpec =
            DataSpec
                .Builder()
                .setUri(uri)
                .setKey(mediaId)
                .build()
        val resolvedDataSpec =
            resolvePlaybackDataSpec(
                dataSpec = dataSpec,
                allowCacheShortCircuit = false,
            )
        val resolvedMimeType =
            localConfiguration.mimeType
                ?.substringBefore(";")
                ?.takeIf { it.isNotBlank() && !it.endsWith("/*") }
                ?: runBlocking(Dispatchers.IO) {
                    database.format(mediaId).first()?.mimeType?.substringBefore(";")
                }
        return mediaItem
            .buildUpon()
            .setUri(resolvedDataSpec.uri)
            .apply { resolvedMimeType?.let(::setMimeType) }
            .build()
    }

    private fun parseTidalAudioQuality(): TidalAudioQuality {
        val stored = dataStore.get(TidalAudioQualityKey, TidalAudioQuality.FLAC.name)
        return runCatching { TidalAudioQuality.valueOf(stored) }.getOrDefault(TidalAudioQuality.FLAC)
    }

    private fun parseAppleMusicQuality(): AppleMusicQuality {
        val stored = dataStore.get(AppleMusicQualityKey, AppleMusicQuality.LOSSLESS.name)
        return runCatching { AppleMusicQuality.valueOf(stored) }.getOrDefault(AppleMusicQuality.LOSSLESS)
    }

    private fun parseTidalInstances(): List<String> =
        dataStore
            .get(TidalInstancesKey, "")
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    /** Metadata used to look a track up across the configured audio sources. */
    private data class SourceQuery(
        val mediaId: String,
        val title: String,
        val artists: List<String>,
        val album: String?,
        val durationMs: Long?,
        /**
         * When non-null, the Qobuz resolver skips its title/artist search and
         * downloads this exact trackId. Set when the user picks a specific
         * Qobuz track from the "Play from" source-search popup — the mediaId
         * encodes the trackId as "qobuz:{trackId}" and [resolveMultiSourceDataSpec]
         * extracts it into this field.
         */
        val directQobuzTrackId: String? = null,
        /**
         * When non-null, the Qobuz-backup resolver asks the mirror for THIS video id
         * instead of [mediaId]. Set when the user picks a specific row in the
         * "Play from" search popup — the mirror is keyed by YouTube video id, and the
         * row they chose is a different catalogue entry from the song that is playing.
         * Keeping [mediaId] untouched is what makes the switch audio-only.
         */
        val directQobuzBackupVideoId: String? = null,
    )

    /**
     * The ordered list of enabled non-YouTube sources to try for [resolveMultiSourceDataSpec],
     * with the user's primary/search source pinned first and YouTube (implicit fallback) excluded.
     */
    private fun sourceResolutionChain(): List<AudioSourceType> {
        val enabledDefaults =
            mapOf(
                // Defaults MUST match the toggle defaults in PlaybackSourceSections so the
                // UI and the resolver agree on which sources are active out of the box.
                AudioSourceType.TIDAL to dataStore.get(TidalEnabledKey, true),
                AudioSourceType.QOBUZ to dataStore.get(QobuzEnabledKey, false),
                AudioSourceType.QOBUZ_BACKUP to dataStore.get(QobuzBackupEnabledKey, false),
                AudioSourceType.DEEZER to dataStore.get(DeezerEnabledKey, false),
                AudioSourceType.JIOSAAVN to dataStore.get(JioSaavnEnabledKey, false),
                AudioSourceType.APPLE to dataStore.get(AppleMusicSourceEnabledKey, false),
                AudioSourceType.YOUTUBE to true,
            )
        // The single stored order is authoritative (top = preferred). Only sources listed BEFORE
        // YouTube act as lossless overrides; once YouTube is reached the user has chosen to use
        // YouTube's own stream, so any sources after it are not attempted as overrides.
        return AudioSourceConfig
            .resolutionChain(
                rawOrder = dataStore.get(AudioSourceOrderKey, "").ifBlank { null },
                enabledSet = null,
                defaults = enabledDefaults,
            ).takeWhile { it != AudioSourceType.YOUTUBE }
    }

    /**
     * Returns true if [source] is currently enabled in the user's preferences. YouTube is always
     * enabled. Used by [resolveMultiSourceDataSpec] to:
     *   1. Decide whether to honor a per-song override whose source has since been disabled
     *      (fall through to the chain instead of trying only the disabled source).
     *   2. Guard the auto-pin write so we never pin to a source the user just turned off.
     */
    private fun isSourceEnabled(source: AudioSourceType): Boolean =
        when (source) {
            AudioSourceType.YOUTUBE -> true
            AudioSourceType.TIDAL -> dataStore.get(TidalEnabledKey, true)
            AudioSourceType.QOBUZ -> dataStore.get(QobuzEnabledKey, false)
            AudioSourceType.QOBUZ_BACKUP -> dataStore.get(QobuzBackupEnabledKey, false)
            AudioSourceType.DEEZER -> dataStore.get(DeezerEnabledKey, false)
            AudioSourceType.APPLE -> dataStore.get(AppleMusicSourceEnabledKey, false)
            AudioSourceType.JIOSAAVN -> dataStore.get(JioSaavnEnabledKey, false)
        }

    /** Builds the shared lookup metadata for [mediaId] from the database / queue metadata. */
    private fun buildSourceQuery(mediaId: String): SourceQuery? {
        val song =
            runCatching {
                runBlocking(Dispatchers.IO) { database.song(mediaId).first() }
            }.getOrNull()
        val queuedMetadata =
            currentMediaMetadata.value?.takeIf { it.id == mediaId }
                ?: queuedMetadataByMediaId[mediaId]
        val title =
            song?.song?.title
                ?: queuedMetadata?.title
                ?: return null
        val artists =
            song?.artists?.map { it.name }?.takeIf { it.isNotEmpty() }
                ?: queuedMetadata?.artists?.map { it.name }.orEmpty()
        val album =
            song?.song?.albumName
                ?: song?.album?.title
                ?: queuedMetadata?.album?.title
        val durationMs =
            song?.song?.duration
                ?.takeIf { it > 0 }
                ?.toLong()
                ?.times(1000L)
                ?: queuedMetadata?.duration?.takeIf { it > 0 }?.toLong()?.times(1000L)
        // Read the per-song Qobuz trackId override (set when the user picked a
        // specific Qobuz track from the "Play from" source-search popup). When
        // set, the Qobuz resolver downloads the exact track instead of
        // re-searching by title+artist.
        //
        // IMPORTANT: read directly from dataStore.data.first() instead of the
        // cached dataStore.get(). The cached PreferenceStore._prefs may be
        // stale right after setSongSourceOverrideWithQobuzTrackId wrote the
        // new trackId — the PreferenceStore collector is async and may not
        // have received the emission yet. Reading from data.first() guarantees
        // we see the write that just happened.
        val qobuzTrackIdRaw = runCatching {
            runBlocking { dataStore.data.first()[SongSourceQobuzTrackIdKey] }
        }.getOrNull()
        val directQobuzTrackId = SongSourceQobuzTrackId.get(qobuzTrackIdRaw, mediaId)
        // Same treatment for the Qobuz-backup mirror's video id (see the field docs
        // on SourceQuery.directQobuzBackupVideoId), read fresh for the same reason.
        val qobuzBackupVideoIdRaw = runCatching {
            runBlocking { dataStore.data.first()[SongSourceQobuzBackupVideoIdKey] }
        }.getOrNull()
        val directQobuzBackupVideoId = SongSourceQobuzBackupVideoId.get(qobuzBackupVideoIdRaw, mediaId)
        return SourceQuery(
            mediaId = mediaId,
            title = title,
            artists = artists,
            album = album,
            durationMs = durationMs,
            directQobuzTrackId = directQobuzTrackId,
            directQobuzBackupVideoId = directQobuzBackupVideoId,
        )
    }

    // mediaId -> set of sources known to have this track (passed the metadata match gate during a recent
    // resolution). Used by the player's Source chooser to only offer sources that actually have the
    // song. Process-lived only; YouTube is always available as the fallback and is added implicitly.
    /**
     * mediaId -> MediaMetadata for every item currently in the queue.
     *
     * The multi-source resolver runs on a loader thread, so it cannot touch [player] to read the
     * MediaItem tags itself. This cache is filled on the application thread from
     * [onTimelineChanged] / [onMediaItemTransition] so [buildSourceQuery] can still recover
     * title/artist/album for tracks that are being resolved *before* they become the current item
     * and that are not in the local database yet (radio/autoplay continuations). Without it those
     * tracks were skipped with "missing metadata" and fell straight through to YouTube.
     */
    private val queuedMetadataByMediaId = ConcurrentHashMap<String, MediaMetadata>()

    private fun cacheQueuedMetadata() {
        if (player.mediaItemCount == 0) return
        val present = HashSet<String>(player.mediaItemCount)
        for (index in 0 until player.mediaItemCount) {
            val item = runCatching { player.getMediaItemAt(index) }.getOrNull() ?: continue
            present.add(item.mediaId)
            item.metadata?.let { queuedMetadataByMediaId[item.mediaId] = it }
        }
        queuedMetadataByMediaId.keys.retainAll(present)
    }

    private val resolvedSourcesByMediaId = ConcurrentHashMap<String, MutableSet<AudioSourceType>>()

    /**
     * Called on app foreground (MainActivity.onStart) for the currently-playing song (or null to
     * clear all). Drops the in-memory record of which lossless sources last resolved successfully
     * so the "Play from" picker re-resolves fresh instead of returning a stale cached set. This
     * pairs with [moe.rukamori.archivetune.qobuz.QobuzAudioProvider.clearTransientCaches] to fix
     * the bug where Qobuz lossless was unavailable until force-stop.
     */
    fun clearResolvedSources(mediaId: String?) {
        if (mediaId == null) {
            resolvedSourcesByMediaId.clear()
        } else {
            resolvedSourcesByMediaId.remove(mediaId)
        }
        _resolvedSourcesRevision.value = _resolvedSourcesRevision.value + 1L
    }

    /**
     * Observable wrapper around [resolvedSourcesByMediaId] so the Source chooser UI can recompose
     * when a refresh completes. Updated whenever [recordResolvedSource] adds a source.
     */
    private val _resolvedSourcesRevision = MutableStateFlow(0L)

    private fun recordResolvedSource(
        mediaId: String,
        source: AudioSourceType,
    ) {
        val set = resolvedSourcesByMediaId.getOrPut(mediaId) { java.util.concurrent.ConcurrentHashMap.newKeySet() }
        if (set.add(source)) {
            _resolvedSourcesRevision.value = _resolvedSourcesRevision.value + 1L
        }
    }

    /**
     * Sources the player's "Play from" chooser should offer for [mediaId], based on the last
     * resolution result: any lossless source that matched the track, plus YouTube (always available
     * as the fallback), plus the user's per-song override source (so the user can always see, change
     * or clear their pinned choice even if the last resolution attempt failed transiently — e.g.
     * "Qobuz token returned preview-only; skipping"). Returned in the canonical Tidal, Qobuz, YouTube
     * order.
     */
    fun availableSourcesForSong(mediaId: String): List<AudioSourceType> {
        val resolved = resolvedSourcesByMediaId[mediaId].orEmpty()
        val override = SongSourceOverride.get(dataStore.get(SongSourceOverrideKey, ""), mediaId)
        // Sources are surfaced in the Source chooser when ANY of:
        //   - Always-available: YouTube (the implicit fallback).
        //   - The source was previously resolved successfully for this media id.
        //   - The user previously pinned this song to this source (override).
        //   - The source is GLOBALLY ENABLED — the user has explicitly turned the source
        //     on in Settings, so they should be able to pick it per-song even if the
        //     initial resolution hasn't completed (or failed transiently). Previously
        //     JioSaavn / Qobuz would not appear in the chooser until they happened to
        //     resolve, which made the user think the source "did nothing" when picked
        //     elsewhere. Now any enabled source is selectable.
        return AudioSourceConfig.DEFAULT_ORDER.filter {
            it == AudioSourceType.YOUTUBE ||
                it in resolved ||
                it == override ||
                isSourceEnabled(it)
        }
    }

    /**
     * Triggers a fresh resolution of every enabled non-YouTube source for [mediaId], bypassing
     * the in-memory DirectStream cache.
     *
     * Why this exists: [resolveMultiSourceDataSpec] records each source that passes the metadata
     * match gate into [resolvedSourcesByMediaId]. If a source failed transiently on the first
     * resolution attempt (network blip, Qobuz/Tidal API timeout, pool-account rate-limit), it
     * never gets recorded for the lifetime of the process — even though a retry would succeed.
     * The user sees YouTube (and JioSaavn) as the only available sources until they force-stop
     * and reopen the app, which clears the in-memory cache and triggers a fresh resolution.
     *
     * This function replicates the "force-stop and reopen" effect on demand: it evicts the cached
     * DirectStream for [mediaId] and re-runs the lossless resolution chain in the background. The
     * UI picks up the new sources via [_resolvedSourcesRevision] (a StateFlow the Source dialog
     * collects to trigger recomposition).
     *
     * Safe to call repeatedly — concurrent invocations are coalesced by the [scope] coroutine
     * and the cache eviction is idempotent.
     */
    fun refreshSourcesForSong(mediaId: String) {
        if (mediaId.isLocalMediaId() || mediaId.isTelegramMediaId()) return
        scope.launch(Dispatchers.IO) {
            // Evict the in-memory cache so the slow path runs again. Without this, the fast
            // path in resolveMultiSourceDataSpec would short-circuit to the previously cached
            // (YouTube) stream and never re-try Qobuz/Tidal.
            directStreamCache.remove(mediaId)
            // Re-resolve by calling the same slow-path logic the player uses. We pass a dummy
            // DataSpec because resolveMultiSourceDataSpec only uses it as a buildUpon base —
            // the URI and key are overwritten inside. The returned DataSpec is discarded; we
            // only care about the side effect of recording sources into resolvedSourcesByMediaId.
            runCatching {
                val dummySpec = DataSpec.Builder().setUri(mediaId.toUri()).build()
                resolveMultiSourceDataSpec(dummySpec, mediaId, lowDataModeActive = false)
            }.onFailure { error ->
                Timber.tag("MusicService").w(error, "refreshSourcesForSong: resolution failed for %s", mediaId)
            }
            // Bump the revision even on failure so the UI can re-render with whatever sources
            // were recorded (if any) and stop showing the loading state.
            _resolvedSourcesRevision.value = _resolvedSourcesRevision.value + 1L
        }
    }

    /**
     * A monotonically increasing counter that bumps whenever [recordResolvedSource] or
     * [refreshSourcesForSong] updates [resolvedSourcesByMediaId]. The Source dialog collects
     * this flow to know when to re-query [availableSourcesForSong].
     */
    val resolvedSourcesRevision: StateFlow<Long> get() = _resolvedSourcesRevision

    /**
     * Applies a per-song "play from" override and, if [mediaId] is the current item, re-resolves it
     * immediately so the change takes effect without the user having to skip the track. Passing a
     * null [source] clears the override for that song.

     *
     * Bugs addressed here:
     *
     * 1. **"Sometimes changing source only restarts the song but doesn't change the source."**
     *    `directStreamCache[mediaId]` and the bare-keyed `contentLengthCache[mediaId]` were not
     *    evicted, so the resolver's fast path could short-circuit to the previous source's
     *    resolved URL (when the override happened to be re-applied to the same id) or to a stale
     *    content-length that made the byte-cache short-circuit in [resolveCachedDataSpec] think
     *    the request was fully cached. Both are now evicted explicitly so the slow path always
     *    re-resolves through the new override.
     *
     * 2. **"Qobuz → YouTube plays from YouTube but muted."** `player.prepare()` on the same
     *    MediaItem is effectively a no-op — the switch only "worked" because the seek-triggered
     *    re-open re-resolved the data source, and the re-create is nondeterministic (stale data
     *    sources, retried expired URLs, and the reactive volume pipeline interleaving with the
     *    transition can pin the primary player's volume low). The deterministic fix:
     *    - evict all per-source caches (URLs, content length, player/download cache resources),
     *    - cancel any pending crossfade and reset its volume,
     *    - re-claim audio focus BEFORE capturing the expected volume (so the baseline uses the
     *      post-focus-restore `audioFocusVolumeFactor` instead of a stale ducked one),
     *    - re-create the media item from scratch (`clearMediaItems()` + `setMediaItem()` + fresh
     *      `prepare()`) — the exact same path as "skip to previous song and back", which the user
     *      confirms always fixes the mute,
     *    - apply the captured (pre-switch) effective volume immediately, and re-apply that same
     *      captured baseline on a delayed reassert AND on STATE_READY (whichever fires last —
     *      the READY hook cancels the delayed job), instead of recomputing from live flows that
     *      can be stale/ducked mid-transition,
     *    - re-arm the audible-playback watchdog on STATE_READY (it self-cancels while the player
     *      is IDLE mid-switch and was never re-armed before — a dead watchdog let any later
     *      volume dip stick forever).
     */
    fun setSongSourceOverride(
        mediaId: String,
        source: AudioSourceType?,
    ) {
        setSongSourceOverrideInternal(mediaId, source, qobuzTrackId = null, qobuzBackupVideoId = null)
    }

    /**
     * Sets a per-song source override AND persists the chosen Qobuz trackId.
     * Used when the user picks a specific Qobuz track from the "Play from"
     * source-search popup — the exact Qobuz trackId is preserved across the
     * re-resolution so the resolver downloads the exact track (not a
     * bestMatch-by-title search that could pick a different master / deluxe
     * edition).
     *
     * The mediaId is NOT changed — the song's existing mediaId (typically the
     * YouTube video id) is preserved, so the song is NOT registered as a
     * duplicate in the playback history / "recently listened" section. The
     * queue is also preserved (this calls the same internal implementation
     * as `setSongSourceOverride`, which uses `player.setMediaItems(allItems,
     * currentIndex, capturedPositionMs)` to keep the queue intact).
     */
    fun setSongSourceOverrideWithQobuzTrackId(
        mediaId: String,
        source: AudioSourceType?,
        qobuzTrackId: String?,
    ) {
        setSongSourceOverrideInternal(mediaId, source, qobuzTrackId, qobuzBackupVideoId = null)
    }

    /**
     * Sets a per-song source override AND persists the chosen Qobuz-**backup** mirror
     * video id — the counterpart of [setSongSourceOverrideWithQobuzTrackId] for the
     * kouzu.in mirror, which is keyed by YouTube video id rather than a catalogue
     * track id.
     *
     * As with the Qobuz variant, the song's own mediaId is left alone: the queue, its
     * position, the artwork and the listening history are all preserved, and only the
     * bytes being decoded change.
     */
    fun setSongSourceOverrideWithQobuzBackupVideoId(
        mediaId: String,
        source: AudioSourceType?,
        qobuzBackupVideoId: String?,
    ) {
        setSongSourceOverrideInternal(mediaId, source, qobuzTrackId = null, qobuzBackupVideoId = qobuzBackupVideoId)
    }

    private fun setSongSourceOverrideInternal(
        mediaId: String,
        source: AudioSourceType?,
        qobuzTrackId: String?,
        qobuzBackupVideoId: String?,
    ) {
        // A direct Qobuz selection is an explicit user choice. A failed
        // background probe may have left the provider's negative cache or an
        // instance cooldown in place, causing this fresh selection to resolve
        // as YouTube instead. Clear only transient failures before recreating
        // the media source; successful stream/search caches remain intact.
        if (source == AudioSourceType.QOBUZ && !qobuzTrackId.isNullOrBlank()) {
            QobuzAudioProvider.clearTransientCaches()
        }
        // Persist the exact-track ids (if any) BEFORE triggering re-resolution.
        //
        // Both are always written, with null for whichever is not part of this
        // selection: a song can only play from one source at a time, so leaving a
        // previous pick's id behind would keep pinning the resolver to the old source
        // even after the user chose a different one.
        runCatching {
            runBlocking {
                dataStore.edit { prefs ->
                    prefs[SongSourceQobuzTrackIdKey] = SongSourceQobuzTrackId.withOverride(
                        prefs[SongSourceQobuzTrackIdKey],
                        mediaId,
                        qobuzTrackId,
                    )
                    prefs[SongSourceQobuzBackupVideoIdKey] = SongSourceQobuzBackupVideoId.withOverride(
                        prefs[SongSourceQobuzBackupVideoIdKey],
                        mediaId,
                        qobuzBackupVideoId,
                    )
                }
            }
        }
        runCatching {
            runBlocking {
                dataStore.edit { prefs ->
                    prefs[SongSourceOverrideKey] =
                        SongSourceOverride.withOverride(prefs[SongSourceOverrideKey], mediaId, source)
                }
            }
        }
        if (player.currentMediaItem?.mediaId == mediaId) {

            // Capture the item FIRST: clearMediaItems() empties the timeline, so
            // currentMediaItem would be null afterwards. Bail before touching any state if it
            // somehow is null here.
            val item = player.currentMediaItem ?: return
            // Capture the expected volume BEFORE any state churn. The teardown below (cache
            // eviction, crossfade cancel, audio-focus re-claim, playlist re-create) makes the
            // reactive volume pipeline (playerVolume x normalizeFactor x focus factor) re-fire
            // mid-transition; recomputing the effective volume from the live flows at that
            // point can pin the player at a stale/ducked value. We re-apply this captured
            // baseline at every recovery point until the switch has deterministically reached
            // STATE_READY, after which the pipeline's live values are authoritative again.
            val expectedVolume = currentEffectivePlayerVolume()
            val wasPlaying = player.playWhenReady
            sourceSwitchPending = true
            sourceSwitchExpectedVolume = expectedVolume

            playbackUrlCache.remove(mediaId)
            YTPlayerUtils.invalidateCachedStreamUrls(mediaId)
            tidalActiveMediaIds.remove(mediaId)

            // Evict the resolved DirectStream so the slow path re-resolves through the new
            // override instead of serving the previous source's URL.
            directStreamCache.remove(mediaId)
            // Evict the bare-keyed content length so [resolveCachedDataSpec] can't conclude the
            // request is fully cached based on the previous source's byte size.
            contentLengthCache.remove(mediaId)
            runCatching { playerCache.removeResource(mediaId) }
            runCatching { downloadCache.removeResource(mediaId) }
            AudioSourceType.entries.forEach { src ->
                val key = sourceCacheKey(src, mediaId)
                runCatching { playerCache.removeResource(key) }
                runCatching { downloadCache.removeResource(key) }
                // Source-prefixed content-length entries are also stale after a source switch.
                contentLengthCache.remove(key)
            }
            // Cancel any in-flight crossfade so its volume ramp / secondary player can't pin the
            // primary player's volume low while the new source is preparing.
            cancelCrossfade(resetVolume = true, resetPauseAtEnd = true)
            // Re-claim audio focus BEFORE computing/applying the effective volume. The previous
            // order applied the volume first (which would multiply by a stale ducked
            // audioFocusVolumeFactor if focus had been transiently lost) and then restored
            // focus — leaving the player pinned at the ducked volume until the reactive volume
            // flow happened to re-fire. Restoring focus first means the captured baseline
            // reflects the restored 1.0 factor.
            ensureAudioFocusForActivePlayback()

            // Deterministic re-create: replace ONLY the current media item with a fresh one
            // (so the new source's MediaSource is built from scratch) while preserving the
            // rest of the queue. The previous implementation called clearMediaItems() +
            // setMediaItem(item, 0), which discarded the entire queue — when the current
            // song ended the player had no next item to auto-advance to, so playback stopped
            // until the user manually pressed Play. We now capture the full media-items list
            // and current index BEFORE the teardown, swap the current item for the freshly
            // resolved one, and call setMediaItems(items, currentIndex, startPositionMs) so
            // the queue is intact and ExoPlayer's auto-advance works as expected.
            //
            // POSITION PRESERVATION: the previous call passed 0L as startPositionMs, which
            // restarted the song from the beginning on every source switch. The user's
            // expectation is that the original song stays where it was — only the audio
            // source changes. We now capture player.currentPosition BEFORE setMediaItems
            // (it would be 0 / unset after the call) and pass it as startPositionMs so
            // ExoPlayer resumes the new source's stream at the same playback position.
            val currentIndex = player.currentMediaItemIndex
            val capturedPositionMs = player.currentPosition.coerceAtLeast(0L)
            val allItems = ArrayList(player.mediaItems)
            if (currentIndex in allItems.indices) {
                allItems[currentIndex] = item
            } else {
                allItems.add(item)
            }
            player.setMediaItems(
                allItems,
                currentIndex.coerceIn(0, allItems.lastIndex),
                capturedPositionMs,
            )
            player.prepare()
            player.playWhenReady = wasPlaying
            // Restore the effective volume immediately (bypass the smooth ramp) so the new source
            // doesn't briefly play muted while the ramp catches up. Start the audible-playback
            // watchdog so any later volume dip gets corrected within the next polling window.
            applyEffectiveVolumeImmediately(expectedVolume)
            updateAudiblePlaybackRecovery()
            // Defensive delayed re-apply: player.prepare() is async, and the state transitions
            // it triggers (BUFFERING -> READY, audio session ID change, audio-effect rebind,
            // reactive volume flow re-firing) can interleave with the synchronous restore above
            // and re-pin the volume low. Re-apply the CAPTURED baseline once more after the
            // prepare pipeline has settled (or when STATE_READY arrives — whichever fires
            // last), so the user never hears a muted stream. The READY hook cancels this job.
            sourceSwitchReassertJob?.cancel()
            sourceSwitchReassertJob =
                scope.launch {
                    delay(SOURCE_SWITCH_VOLUME_REASSERT_MS)
                    if (sourceSwitchPending &&
                        player.currentMediaItem?.mediaId == mediaId &&
                        player.playWhenReady
                    ) {
                        Timber.tag(TAG).d(
                            "source switch delayed reassert: captured=%s live=%s actual=%s",
                            expectedVolume,
                            currentEffectivePlayerVolume(),
                            player.volume,
                        )
                        ensureAudioFocusForActivePlayback()
                        applyEffectiveVolumeImmediately(expectedVolume)
                        ensureAudiblePlaybackVolume("source_switch_reassert")
                    }
                }
            Timber.tag(TAG).d(
                "setSongSourceOverride: mediaId=%s source=%s capturedVolume=%s wasPlaying=%s state=%s",
                mediaId,
                source,
                expectedVolume,
                wasPlaying,
                player.playbackState,
            )
        }
    }

    /**
     * Attempts to resolve the current media item through the configured audio sources, in the
     * user's chosen priority order. Returns a [DataSpec] pointing at the first source that
     * resolves a stream, or null so playback falls through to the YouTube resolver.
     *
     * Resolves lossless audio through the configured sources (currently Tidal), falling through to
     * the YouTube resolver when none produce a stream.
     */
    private fun resolveMultiSourceDataSpec(
        dataSpec: DataSpec,
        mediaId: String,
        lowDataModeActive: Boolean,
        isPrefetch: Boolean = false,
    ): DataSpec? {
        if (mediaId.isLocalMediaId() || mediaId.isTelegramMediaId()) {
            Timber.tag("MusicService").d("Multi-source skip: %s is a local/telegram media id", mediaId)
            return null
        }
        runBlocking { moe.rukamori.archivetune.App.startupReadiness.awaitReady() }
        // Direct Qobuz track playback: when the user picks a specific Qobuz
        // track from the "Play from" source-search popup, a per-song Qobuz
        // trackId override is persisted in SongSourceQobuzTrackIdKey. We
        // detect that here so we can force the chain to [QOBUZ] and bypass
        // the metadata match gate — the user explicitly chose this track.
        // The mediaId is NOT changed (it stays as the song's existing YouTube
        // id), so the song is not registered as a duplicate in the playback
        // history.
        //
        // IMPORTANT: read directly from dataStore.data.first() instead of the
        // cached dataStore.get() — see buildSourceQuery for the rationale.
        val qobuzTrackIdRaw = runCatching {
            runBlocking { dataStore.data.first()[SongSourceQobuzTrackIdKey] }
        }.getOrNull()
        val directQobuzTrackId = SongSourceQobuzTrackId.get(qobuzTrackIdRaw, mediaId)
        val isDirectQobuzTrack = directQobuzTrackId != null
        // Same for an explicit Qobuz-backup pick: the mirror is addressed by video id,
        // so a chosen row is only reachable through the stored id. Treat it exactly like
        // a direct Qobuz track — force the chain to that one source, skip the
        // DirectStream cache (which may hold the previous source's URL for this media
        // id) and let it through low-data mode, because it is an explicit user choice.
        val qobuzBackupVideoIdRaw = runCatching {
            runBlocking { dataStore.data.first()[SongSourceQobuzBackupVideoIdKey] }
        }.getOrNull()
        val directQobuzBackupVideoId = SongSourceQobuzBackupVideoId.get(qobuzBackupVideoIdRaw, mediaId)
        val isDirectQobuzBackupTrack = directQobuzBackupVideoId != null
        // A song can only be pinned to one source at a time, so at most one of these is
        // set; `isDirectPick` is the shared "the user chose this exact track" signal.
        val isDirectPick = isDirectQobuzTrack || isDirectQobuzBackupTrack
        // Read the source override FRESH from DataStore — not from the stale
        // PreferenceStore cache. The PreferenceStore collector is async and
        // may not have received the write from setSongSourceOverride yet.
        // This must use data.first() to see the latest value.
        val sourceOverrideRaw = runCatching {
            runBlocking { dataStore.data.first()[SongSourceOverrideKey] }
        }.getOrNull()
        // Fast path: serve from the in-memory DirectStream cache if we have a
        // fresh entry. This makes "skip to next" instant when the next song
        // was prefetched (see prefetchNextMediaItemStream).
        //
        // SKIP the fast path entirely when a direct Qobuz track override is
        // set — the user just picked a specific Qobuz track, so we must
        // re-resolve through Qobuz even if a cached YouTube stream exists.
        val now = System.currentTimeMillis()
        val cached = directStreamCache[mediaId]
        if (!isDirectPick && cached != null && cached.expiresAtMs > now) {
            val override = SongSourceOverride.get(sourceOverrideRaw, mediaId)
            val cacheHitsOverride =
                if (override != null) {
                    override == cached.stream.source
                } else {
                    cached.stream.source == sourceResolutionChain().firstOrNull()
                }
            if (cacheHitsOverride && !lowDataModeActive) {
                Timber.tag("MusicService").d(
                    "Multi-source cache HIT for %s: %s [%s]",
                    mediaId,
                    cached.stream.source.name,
                    cached.stream.label,
                )
                tidalActiveMediaIds.add(mediaId)
                audioNormalizationFactorCache[mediaId] = 1f
                recordResolvedSource(mediaId, cached.stream.source)
                val cacheKey = sourceCacheKey(cached.stream.source, mediaId)
                cached.stream.contentLength?.takeIf { it > 0L }?.let { contentLengthCache[cacheKey] = it }
                return dataSpec
                    .buildUpon()
                    .setUri(cached.stream.uri.toUri())
                    .setKey(cacheKey)
                    .build()
            }
            // Cache entry doesn't match the per-song override or low-data
            // mode flipped on — evict and fall through to the slow path.
            directStreamCache.remove(mediaId, cached)
        } else if (cached != null) {
            // Expired entry — evict.
            directStreamCache.remove(mediaId, cached)
        }
        // A per-song "play from" override (set via the player's Source chooser, or auto-pinned
        // by a previous successful lossless resolution — see the Source WIN block below) takes
        // precedence over the global order. YOUTUBE means "always use YouTube for this song"
        // (skip lossless entirely); a lossless override forces just that source (still subject
        // to the metadata match gate).
        //
        // DISABLED-SOURCE FALLTHROUGH: if the pinned source has since been disabled by the
        // user (e.g. they turned off the Qobuz toggle in Settings), we do NOT try only that
        // source — that would always fail and force a YouTube fallback even when other
        // lossless sources are still enabled. Instead we fall through to the full enabled
        // chain so a different lossless source can still win. The stale pin remains in
        // storage and will become effective again if the user re-enables the source.
        val override =
            when {
                isDirectQobuzTrack -> AudioSourceType.QOBUZ
                isDirectQobuzBackupTrack -> AudioSourceType.QOBUZ_BACKUP
                else -> SongSourceOverride.get(sourceOverrideRaw, mediaId)
            }
        val overrideStillEnabled =
            override == null ||
                override == AudioSourceType.YOUTUBE ||
                isSourceEnabled(override)
        val chain =
            if (isDirectPick) {
                // `override` was pinned to the picked source above.
                listOfNotNull(override)
            } else when (override) {
                null -> sourceResolutionChain()
                AudioSourceType.YOUTUBE -> {
                    Timber.tag("MusicService").d("Per-song override: %s pinned to YouTube; skipping lossless", mediaId)
                    emptyList()
                }
                else -> if (overrideStillEnabled) {
                    Timber.tag("MusicService").d("Per-song override: %s pinned to %s", mediaId, override.name)
                    listOf(override)
                } else {
                    Timber.tag("MusicService").w(
                        "Per-song override: %s pinned to %s but that source is now disabled; falling through to chain",
                        mediaId,
                        override.name,
                    )
                    sourceResolutionChain()
                }
            }
        Timber.tag("MusicService").d("Multi-source resolve for %s | chain=%s", mediaId, chain.joinToString(",") { it.name })
        if (chain.isEmpty()) {
            Timber.tag("MusicService").d("Multi-source skip: no sources to try (chain empty)")
            return null
        }

        // Low Data Mode normally bypasses lossless sources, but it must not discard an exact
        // Qobuz track the user explicitly selected in the Play from popup. That choice carries
        // a concrete Qobuz track id and must not silently turn into a YouTube Opus stream on a
        // metered network.
        if (lowDataModeActive && !isDirectPick) {
            tidalActiveMediaIds.remove(mediaId)
            Timber.tag("MusicService").i("Low-data mode active; skipping Tidal/Qobuz for %s", mediaId)
            return null
        }

        val query = buildSourceQuery(mediaId)
        if (query == null) {
            Timber.tag("MusicService").w("Multi-source skip: could not build source query (missing metadata) for %s", mediaId)
            return null
        }
        Timber.tag("MusicService").d("Source query built: title=\"%s\" artists=%s durationMs=%s", query.title, query.artists.joinToString("/"), query.durationMs?.toString() ?: "?")

        // Resolve each enabled lossless source and apply the shared metadata-aware safety gate.
        // Title, artist, duration, album and version markers are evaluated together; title-only
        // results must clear a stricter fallback threshold. The strongest accepted candidate wins.
        //
        // OVERRIDE BYPASS: when the user has explicitly pinned this song to a single source via
        // the player's Source chooser ("Play from"), we trust their intent — the TitleMatch gate is
        // skipped for that source's candidate. This fixes the bug where picking JioSaavn (or any
        // non-YouTube source) from the Source chooser "did nothing": the JioSaavn candidate's
        // title formatting often differs from the YouTube-derived wanted title enough to fail the
        // metadata gate, the resolver silently fell back to YouTube, and the user saw no change.
        // The override is the user's manual override of the gate — we should respect it.
        val overrideIsSourceOverride = (override != null && override != AudioSourceType.YOUTUBE) || isDirectPick
        var best: DirectStream? = null
        var bestSource: AudioSourceType? = null
        var bestScore = 0.0
        for (source in chain) {
            Timber.tag("MusicService").d("Trying source: %s for \"%s\"", source.name, query.title)
            val stream: DirectStream? =
                when (source) {
                    AudioSourceType.TIDAL -> resolveTidalStream(query)
                    AudioSourceType.QOBUZ -> resolveQobuzStream(query)
                    AudioSourceType.QOBUZ_BACKUP -> resolveQobuzBackupStream(query)
                    AudioSourceType.DEEZER -> resolveDeezerStream(query)
                    AudioSourceType.APPLE ->
                        resolveAppleStream(
                            query,
                            trusted = overrideIsSourceOverride && override == AudioSourceType.APPLE,
                        )
                    AudioSourceType.JIOSAAVN -> resolveJioSaavnStream(query)
                    AudioSourceType.YOUTUBE -> null
                }
            if (stream == null) {
                Timber.tag("MusicService").d("Source %s did not resolve \"%s\"", source.name, query.title)
                continue
            }
            val match =
                if (overrideIsSourceOverride && source == override) {
                    // Explicit per-song override: trust the user's choice. Still record the source
                    // as resolved so the Source chooser remembers it for next time.
                    Timber.tag("MusicService").i(
                        "Source %s ACCEPTED for \"%s\" via per-song override (skipping metadata gate) [%s]",
                        source.name, query.title, stream.label,
                    )
                    TitleMatch.Result(true, 1.0, 1.0, 1.0, 1.0, "per-song override bypass")
                } else {
                    TitleMatch.evaluate(
                        wantedTitle = query.title,
                        wantedArtists = query.artists,
                        wantedAlbum = query.album,
                        wantedDurationMs = query.durationMs,
                        stream = stream,
                    )
                }
            if (!match.accepted) {
                Timber.tag("MusicService").i(
                    "Source %s rejected for \"%s\": %s score=%.1f%% title=%.1f%% artist=%s duration=%s matched=\"%s\"",
                    source.name,
                    query.title,
                    match.reason,
                    match.score * 100,
                    match.title * 100,
                    match.artist?.let { "%.1f%%".format(it * 100) } ?: "?",
                    match.duration?.let { "%.1f%%".format(it * 100) } ?: "?",
                    stream.matchedTitle ?: "?",
                )
                continue
            }
            Timber.tag("MusicService").d(
                "Source %s candidate for \"%s\": match %.1f%% (%s) [%s]",
                source.name, query.title, match.score * 100, match.reason, stream.label,
            )
            // Remember that this source has this song (title-gate passed) so the player's Source
            // chooser can list only the sources that actually have the track.
            recordResolvedSource(mediaId, source)
            if (match.score > bestScore) {
                best = stream
                bestSource = source
                bestScore = match.score
            }
            // The list is the user's source preference order. The first valid source wins;
            // later sources are fallbacks, not competitors selected by metadata score.
            if (best != null) break
        }

        val winningStream = best
        val winningSource = bestSource
        if (winningStream != null && winningSource != null) {
            Timber.tag("MusicService").i(
                "Source WIN: %s resolved \"%s\" [%s] metadata match %.1f%% (%s)",
                winningSource.name, query.title, winningStream.label, bestScore * 100, winningStream.uri.take(80),
            )
            // Persist the winning source's track id as a future health-probe track.
            if (winningSource == AudioSourceType.TIDAL) {
                TidalAudioProvider.lastResolvedTrackId?.takeIf { it.isNotBlank() }?.let { probe ->
                    runCatching {
                        runBlocking { dataStore.edit { prefs -> prefs[TidalLastProbeTrackKey] = probe } }
                    }
                }
            } else if (winningSource == AudioSourceType.QOBUZ) {
                QobuzAudioProvider.lastResolvedTrackId?.takeIf { it.isNotBlank() }?.let { probe ->
                    runCatching {
                        runBlocking { dataStore.edit { prefs -> prefs[QobuzLastProbeTrackKey] = probe } }
                    }
                }
            }
            return applyDirectStream(dataSpec, mediaId, winningStream)
        }

        // A non-YouTube source was requested but none matched the title accurately enough; silently
        // fall back to YouTube (no user-facing notice — the track just plays from YouTube). Kept as a
        // debug log only.
        Timber.tag("MusicService").w("No lossless source cleared the metadata match gate for \"%s\"; falling back to YouTube", query.title)
        tidalActiveMediaIds.remove(mediaId)
        return null
    }

    /** Resolves a Tidal stream, trying the signed-in account (official API) before public instances. */
    /**
     * Returns a currently-valid Tidal access token, transparently refreshing it via the stored
     * refresh token when it is missing or within 60s of expiry, and persisting the refreshed
     * token. Returns null when there is no usable token (never logged in, or the refresh token is
     * expired/revoked), so the caller falls back to public instances.
     */
    private fun ensureValidTidalToken(): String? {
        val token = dataStore.get(TidalAccessTokenKey, "")
        val expiry = dataStore.get(TidalTokenExpiryKey, 0L)
        // 60s skew so we never hand out a token that expires mid-request.
        if (token.isNotBlank() && expiry > System.currentTimeMillis() + 60_000L) return token

        val refresh = dataStore.get(TidalRefreshTokenKey, "")
        val flow = dataStore.get(TidalAuthFlowKey, TidalAccountManager.FLOW_OAUTH)
        if (refresh.isBlank()) {
            // No refresh token: either never had one (web-capture session) or it was cleared. If we
            // still hold an unexpired-ish access token, keep using it; otherwise flag re-login.
            if (token.isBlank()) markTidalNeedsRelogin()
            Timber.tag("MusicService").d("Tidal token expired/absent and no refresh token; account path unavailable")
            return token.ifBlank { null }
        }
        // Serialize with the 401 refresh path and double-check under the lock in case another thread
        // already refreshed while we waited.
        return synchronized(tidalTokenRefreshLock) {
            val currentToken = dataStore.get(TidalAccessTokenKey, "")
            val currentExpiry = dataStore.get(TidalTokenExpiryKey, 0L)
            if (currentToken.isNotBlank() && currentExpiry > System.currentTimeMillis() + 60_000L) {
                Timber.tag("MusicService").d("Tidal token already refreshed by another thread; reusing")
                return@synchronized currentToken
            }
            Timber.tag("MusicService").d("Tidal access token expired; refreshing via stored refresh token (flow=%s)", flow)
            val refreshed =
                runCatching { runBlocking(Dispatchers.IO) { TidalAccountManager.refreshAccessToken(refresh, flow) } }
                    .onFailure { Timber.tag("MusicService").w(it, "Tidal token refresh threw") }
                    .getOrNull()
            if (refreshed == null) {
                Timber.tag("MusicService").w("Tidal token refresh failed; flagging re-login and falling back to public instances")
                markTidalNeedsRelogin()
                return@synchronized null
            }
            persistRefreshedTidalToken(refreshed)
            Timber.tag("MusicService").i(
                "Tidal token refreshed; valid for ~%ds",
                (refreshed.expiresAtMillis - System.currentTimeMillis()) / 1000,
            )
            refreshed.accessToken
        }
    }

    /** Persists a refreshed/renewed Tidal token bundle and clears the re-login flag. */
    private fun persistRefreshedTidalToken(refreshed: TidalAccountManager.TokenResult) {
        runBlocking {
            dataStore.edit { prefs ->
                prefs[TidalAccessTokenKey] = refreshed.accessToken
                prefs[TidalTokenExpiryKey] = refreshed.expiresAtMillis
                refreshed.refreshToken?.let { prefs[TidalRefreshTokenKey] = it }
                refreshed.userId?.let { prefs[TidalUserIdKey] = it }
                refreshed.countryCode?.let { prefs[TidalCountryCodeKey] = it }
                prefs[TidalNeedsReloginKey] = false
            }
        }
    }

    /** Flags that the stored Tidal session can no longer be refreshed and needs an interactive re-login. */
    private fun markTidalNeedsRelogin() {
        runBlocking { dataStore.edit { prefs -> prefs[TidalNeedsReloginKey] = true } }
    }

    /**
     * Forces a Tidal token refresh via the stored refresh token, ignoring the cached expiry. Used
     * when the API rejects a token we believed was valid (401), e.g. after a server-side
     * invalidation. Persists and returns the new access token, or null if refresh is impossible.
     */
    /**
     * Returns true if a [TidalAccountManager.TidalUnauthorizedException] (401) appears anywhere in
     * the throwable's cause or suppressed chain. Needed because ExoPlayer can interrupt the loader
     * thread mid-request, surfacing an [InterruptedException] with the real 401 attached only as a
     * suppressed exception — in which case a naive `catch (TidalUnauthorizedException)` misses it.
     */
    private fun isTidalUnauthorized(root: Throwable?) = TidalAccountManager.isUnauthorized(root)

    /**
     * Serializes all Tidal token refreshes. Tracks resolve on parallel ExoPlayer loader threads, so
     * multiple 401s can fire at once; this lock + the double-check below guarantee exactly one
     * network refresh per expiry, and later waiters observe the already-refreshed token and reuse
     * it (avoids redundant token calls and races on the persisted session).
     */
    private val tidalTokenRefreshLock = Any()

    /**
     * Refreshes the Tidal access token after a 401. [rejectedToken] is the token the API just
     * rejected. Under the lock we re-read the stored token: if it already differs from
     * [rejectedToken], another thread refreshed while we waited, so we return that fresh token
     * WITHOUT another network call. Returns null when there is no usable refresh token or the
     * refresh genuinely fails — in which case we flag the account for interactive re-login. The
     * refresh runs against the client that issued the session (see [TidalAuthFlowKey]).
     */
    private fun refreshTidalToken(rejectedToken: String?): String? =
        synchronized(tidalTokenRefreshLock) {
            val current = dataStore.get(TidalAccessTokenKey, "")
            // Double-check: another thread already refreshed the token while we waited on the lock.
            if (current.isNotBlank() && current != rejectedToken) {
                Timber.tag("MusicService").d("Tidal token already refreshed by another thread; reusing")
                return@synchronized current
            }
            val refresh = dataStore.get(TidalRefreshTokenKey, "")
            val flow = dataStore.get(TidalAuthFlowKey, TidalAccountManager.FLOW_OAUTH)
            if (refresh.isBlank()) {
                Timber.tag("MusicService").w("401 from Tidal but no refresh token; account needs re-login")
                markTidalNeedsRelogin()
                return@synchronized null
            }
            Timber.tag("MusicService").d("Force-refreshing Tidal token after 401 (flow=%s)", flow)
            val refreshed =
                runCatching { runBlocking(Dispatchers.IO) { TidalAccountManager.refreshAccessToken(refresh, flow) } }
                    .onFailure { Timber.tag("MusicService").w(it, "Force refresh threw") }
                    .getOrNull()
            if (refreshed == null) {
                Timber.tag("MusicService").w("Force refresh failed; account needs re-login")
                markTidalNeedsRelogin()
                return@synchronized null
            }
            persistRefreshedTidalToken(refreshed)
            Timber.tag("MusicService").i("Tidal token force-refreshed after 401")
            refreshed.accessToken
        }

    private fun resolveAppleStream(
        query: SourceQuery,
        trusted: Boolean = false,
    ): DirectStream? {
        if (AppleMusicAudioProvider.mediaUserToken() == null || AppleMusicAudioProvider.devToken() == null) {
            Timber
                .tag("MusicService")
                .d("Apple Music source: missing tokens (sign in via Settings → Apple Music)")
            return null
        }
        val appleQuality = parseAppleMusicQuality()
        val candidates =
            runBlocking(Dispatchers.IO) {
                AppleMusicAudioProvider.resolveCandidates(
                    title = query.title,
                    artists = query.artists,
                    album = query.album,
                    durationMs = query.durationMs,
                    quality = appleQuality,
                )
            }
        if (candidates.isEmpty()) return null

        // Gate candidates locally (shared metadata gate) so a wrong search hit falls through
        // to the next candidate; with a per-song override the user's pick wins outright.
        var winner: Pair<AppleMusicAudioProvider.AppleMusicStream, DirectStream>? = null
        var bestScore = -1.0
        for (candidate in candidates) {
            val stream =
                DirectStream(
                    uri = "apple-pending:${candidate.songId}",
                    mimeType = "audio/mp4",
                    codecs = if (candidate.flavor.contains("ctrp", ignoreCase = true) &&
                        candidate.flavor.filter(Char::isDigit).toIntOrNull()?.let { it > 320 } == true
                    ) "alac" else "mp4a.40.2",
                    contentLength = candidate.contentLength,
                    label = "Apple Music ${candidate.flavor}",
                    source = AudioSourceType.APPLE,
                    matchedTitle = candidate.matchedTitle,
                    matchedArtist = candidate.matchedArtist,
                    matchedAlbum = candidate.matchedAlbum,
                    matchedDurationMs = candidate.matchedDurationMs,
                )
            val match =
                if (trusted) {
                    TitleMatch.Result(true, 1.0, 1.0, 1.0, 1.0, "per-song override bypass")
                } else {
                    TitleMatch.evaluate(
                        wantedTitle = query.title,
                        wantedArtists = query.artists,
                        wantedAlbum = query.album,
                        wantedDurationMs = query.durationMs,
                        stream = stream,
                    )
                }
            if (match.accepted && match.score > bestScore) {
                winner = candidate to stream
                bestScore = match.score
            }
        }
        val (candidate, placeholder) = winner ?: return null

        // Materialize the winning candidate into a cache file; playback is an ordinary
        // progressive file stream with the Widevine L3 session decrypting at the codec level.
        return try {
            val file =
                appleStreamFile(query.mediaId, appleQuality) {
                    AppleMusicVirtualStream.build(mediaOkHttpClient, candidate.playlistUrl, candidate.keyIdHex).bytes
                }
            appleDrmTrackInfo[query.mediaId] = AppleTrackDrmInfo(adamId = candidate.songId, drmUri = candidate.drmUri)
            // Nerd-info adaptation: patch the format row so the media-info sheet shows the
            // Apple codec/size for this song instead of the stale YouTube values (mirrors the
            // contentLength backfill pattern; the YouTube resolver rewrites the row whenever
            // the song is played from YouTube again).
            runCatching {
                runBlocking(Dispatchers.IO) {
                    val row = database.getFormatsByIds(listOf(query.mediaId)).firstOrNull()
                    if (row != null) {
                        val bitrate = measuredBitrate(file.length(), candidate.matchedDurationMs)
                        database.query {
                            upsert(
                                row.copy(
                                    codecs = placeholder.codecs,
                                    contentLength = file.length(),
                                    bitrate = bitrate ?: row.bitrate,
                                ),
                            )
                        }
                    }
                }
            }
            Timber
                .tag("MusicService")
                .i("Apple Music resolved [%s] for \"%s\" (%d KB)", placeholder.label, query.title, file.length() / 1024)
            placeholder.copy(uri = android.net.Uri.fromFile(file).toString(), contentLength = file.length())
        } catch (err: Throwable) {
            Timber.tag("MusicService").w(err, "Apple Music virtual stream failed for \"%s\"", query.title)
            null
        }
    }

    /**
     * Cache file for an Apple stream (cacheDir/applemusic/<mediaId>.m4a), built via [build]
     * when missing, with a simple prune: oldest files go first past ~300 MB.
     */
    private fun appleStreamFile(
        mediaId: String,
        quality: AppleMusicQuality,
        build: () -> ByteArray,
    ): java.io.File {
        val dir = java.io.File(cacheDir, "applemusic").apply { mkdirs() }
        val files = dir.listFiles()?.sortedBy { it.lastModified() } ?: emptyList()
        var total = files.sumOf { it.length() }
        for (f in files) {
            if (total <= 300L * 1024 * 1024) break
            total -= f.length()
            f.delete()
        }
        val safeId = mediaId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        // The v2 marker invalidates files built before the pssh KID fix: those carry an all-zero
        // Widevine key id, so they are structurally valid (and therefore cached and replayed
        // forever) but decode to silence. Bumping the name is what makes the fix observable on a
        // song the user has already played; the size prune above retires the stale v1 files.
        val out = java.io.File(dir, "${safeId}_${quality.name.lowercase()}_v2.m4a")
        if (out.exists() && out.length() > 0) return out
        out.writeBytes(build())
        return out
    }

    private fun resolveTidalStream(query: SourceQuery): DirectStream? {
        val quality = parseTidalAudioQuality()
        Timber.tag("MusicService").d("Tidal resolve start | quality=%s accountFirst=%s", quality.name, dataStore.get(TidalAccountFirstKey, true))

        // Account-first: use a real Tidal subscriber token via the official API when available —
        // first the user's own signed-in account, then shared premium accounts from the community
        // Source Pool. Both paths yield full-quality FLAC directly, so a proxy instance is only a
        // last resort.
        if (dataStore.get(TidalAccountFirstKey, true)) {
            val apiQuality =
                when (quality) {
                    TidalAudioQuality.HI_RES_LOSSLESS -> "HI_RES_LOSSLESS"
                    TidalAudioQuality.FLAC -> "LOSSLESS"
                    TidalAudioQuality.AAC_320 -> "HIGH"
                }
            fun attempt(accessToken: String, countryCode: String): DirectStream? =
                runBlocking(Dispatchers.IO) {
                    TidalAccountManager.resolveDirectStream(
                        accessToken = accessToken,
                        title = query.title,
                        artists = query.artists,
                        durationMs = query.durationMs,
                        audioQuality = apiQuality,
                        cacheDir = cacheDir,
                        countryCode = countryCode,
                    )
                }

            // 1) The user's own token. Resolve, transparently refreshing and retrying once if the
            //    API rejects it (401) — the stored token may have been invalidated server-side even
            //    though it has not expired by our clock.
            var token = ensureValidTidalToken()
            Timber.tag("MusicService").d("Tidal account token available=%s", token != null)
            if (token != null) {
                val accountCountry = dataStore.get(TidalCountryCodeKey, "").ifBlank { "US" }
                val accountStream =
                    try {
                        attempt(token, accountCountry)
                    } catch (e: Throwable) {
                        // A 401 may surface directly, or wrapped as a *suppressed*/cause exception
                        // inside an InterruptedException when ExoPlayer interrupts the loader thread
                        // mid-request. Detect it anywhere in the chain so the refresh path still runs.
                        if (isTidalUnauthorized(e)) {
                            Timber.tag("MusicService").w("Tidal account 401 (possibly wrapped); refreshing token + retrying")
                            // Clear any pending interrupt on this loader thread; otherwise the
                            // runBlocking calls in refresh + retry would immediately abort.
                            Thread.interrupted()
                            val refreshed = refreshTidalToken(rejectedToken = token)
                            if (refreshed != null && refreshed != token) {
                                token = refreshed
                                runCatching { attempt(refreshed, accountCountry) }
                                    .onFailure { Timber.tag("MusicService").w(it, "Tidal account retry failed for %s", query.mediaId) }
                                    .getOrNull()
                            } else {
                                null
                            }
                        } else {
                            Timber.tag("MusicService").w(e, "Tidal account resolve failed for %s", query.mediaId)
                            null
                        }
                    }
                if (accountStream != null) return accountStream
            }

            // 2) Shared premium Tidal accounts contributed to the community Source Pool (premium
            //    first). These are genuine subscriber tokens, so they stream full-quality FLAC via
            //    the official API without anyone hosting a restream instance.
            for (poolAccount in PoolAccountManager.tidalAccounts()) {
                val poolCountry = poolAccount.countryCode?.trim()?.ifBlank { null } ?: "US"
                val poolStream =
                    runCatching { attempt(poolAccount.token, poolCountry) }
                        .onFailure {
                            if (TidalAccountManager.isUnauthorized(it)) {
                                PoolAccountManager.report("tidal", "account", poolAccount.id, "dead")
                            } else {
                                Timber.tag("MusicService").w(it, "Tidal pool account resolve failed for %s", query.mediaId)
                            }
                        }
                        .getOrNull()
                if (poolStream != null) {
                    Timber.tag("MusicService").d("Tidal resolved via pool account (premium=%s)", poolAccount.premium)
                    PoolAccountManager.noteAccountSuccess("tidal", poolAccount.id)
                    return poolStream
                }
                // Threw, or returned null because this account has no match. Either way it just
                // cost up to 20s, so sort it last for a while rather than paying that again on
                // the next track — the server's own dead-account handling is far slower than a
                // listening session.
                PoolAccountManager.noteAccountFailure("tidal", poolAccount.id)
            }
        }

        // Fallback: public HiFi/QQDL instances (empty list falls back to built-in defaults).
        // Union the user's configured instances with community instances discovered from the
        // Source Pool website (verified-healthy ones, cached by the startup scan). User entries are
        // kept and ordered first; discovery only adds, never removes.
        val configuredInstances = parseTidalInstances()
        val discoveredInstances = TidalInstanceHealthManager.healthyUrls(this)
        val mergedInstances =
            LinkedHashSet<String>().apply {
                addAll(configuredInstances)
                addAll(discoveredInstances)
            }.toList()
        Timber.tag("MusicService").d(
            "Tidal public-instance fallback | configured=%d discovered=%d merged=%d",
            configuredInstances.size,
            discoveredInstances.size,
            mergedInstances.size,
        )
        TidalAudioProvider.setInstances(mergedInstances)
        val resolved =
            runCatching {
                TidalAudioProvider.resolve(
                    query =
                        TidalAudioProvider.Query(
                            mediaId = query.mediaId,
                            title = query.title,
                            artists = query.artists,
                            album = query.album,
                            isrc = null,
                            durationMs = query.durationMs,
                        ),
                    cacheDir = cacheDir,
                    preferAtmos = false,
                    preferLiveDash = true,
                    audioQuality = quality,
                )
            }.onFailure { error ->
                Timber.tag("MusicService").w(error, "TIDAL stream resolution failed for %s", query.mediaId)
            }.getOrNull() ?: return null

        return DirectStream(
            uri = resolved.mediaUri,
            mimeType = resolved.mimeType,
            codecs = resolved.codecs,
            contentLength = resolved.contentLength,
            label = "Tidal ${resolved.label}",
            source = AudioSourceType.TIDAL,
            matchedTitle = resolved.matchedTitle,
            matchedArtist = resolved.matchedArtist,
            matchedAlbum = resolved.matchedAlbum,
            matchedDurationMs = resolved.matchedDurationMs,
        )
    }

    /** Resolves a Qobuz stream through the user-provided proxy instances. */
    private fun resolveQobuzStream(query: SourceQuery): DirectStream? {
        val userInstances = parseQobuzInstances()
        // Union user-configured instances with community instances discovered from the Source Pool
        // website (cached ~10min inside the provider). User entries stay first; discovery only adds.
        val discoveredInstances = runCatching { QobuzAudioProvider.discoverInstances() }.getOrDefault(emptyList())
        val configuredInstances =
            LinkedHashSet<String>().apply {
                addAll(userInstances)
                addAll(discoveredInstances)
            }.toList()
        // Union the user's own Qobuz tokens with shared premium accounts from the community Source
        // Pool. Pool accounts carry the app_secret needed to sign stream URLs, so they resolve FLAC
        // directly against www.qobuz.com. User entries stay first; pool accounts are appended and
        // deduped by auth token.
        val poolTokens =
            PoolAccountManager.qobuzAccounts().map {
                QobuzToken(
                    token = it.token,
                    appId = it.appId,
                    appSecret = it.appSecret,
                    label = "Source Pool",
                    subscription = if (it.premium) "premium" else "",
                    poolId = it.id,
                )
            }
        val configuredTokens =
            (QobuzToken.listFromJson(dataStore.get(QobuzTokensKey, "")) + poolTokens)
                .distinctBy { it.token }
        if (configuredInstances.isEmpty() && configuredTokens.isEmpty()) {
            Timber.tag("MusicService").d("Qobuz skip: no tokens or instances configured")
            return null
        }
        val formatId = parseQobuzAudioQuality().toFormatId()
        Timber.tag("MusicService").d(
            "Qobuz resolve start | formatId=%d tokens=%d instances=%d",
            formatId,
            configuredTokens.size,
            configuredInstances.size,
        )
        QobuzAudioProvider.setTokens(configuredTokens)
        QobuzAudioProvider.setInstances(configuredInstances)
        return runCatching {
            runBlocking(Dispatchers.IO) {
                QobuzAudioProvider.resolve(
                    query =
                        QobuzAudioProvider.Query(
                            mediaId = query.mediaId,
                            title = query.title,
                            artists = query.artists,
                            album = query.album,
                            durationMs = query.durationMs,
                            directTrackId = query.directQobuzTrackId,
                        ),
                    formatId = formatId,
                )
            }
        }.onFailure { error ->
            Timber.tag("MusicService").w(error, "QOBUZ stream resolution failed for %s", query.mediaId)
        }.getOrNull()
    }

    /**
     * Resolves a **Qobuz backup** stream — the community-hosted `mlc-ytify.kouzu.in` mirror, which
     * serves a FLAC per YouTube video id.
     *
     * The two-step resolve (envelope → mirror probe) lives in
     * [QobuzBackupProvider.resolveStream], shared with the download path
     * (`LosslessStreamResolver.resolveQobuzBackup`). It used to be duplicated here, which is how
     * the two drifted: the playback copy was fixed to prefer the `lossless` mirror over the lossy
     * `url` one while the download path had no Qobuz-backup support at all.
     *
     * [mediaOkHttpClient] is passed in so the probe goes out through the same proxy-aware client
     * that will fetch the bytes, and so the interceptor adds `x-request-source: muzo` — without
     * that header kouzu.in rate-limits aggressively.
     *
     * Returns null when the id is not a YouTube video id, the mirror has nothing for it, or no
     * candidate mirror serves audio.
     *
     * Marked [DirectStream.trustedDirectId] because the mirror is keyed by the very id we asked
     * for: the YouTube media id IS the authoritative identity here, so the title/artist gate that
     * guards catalogue-search sources has nothing to check.
     */
    private fun resolveQobuzBackupStream(query: SourceQuery): DirectStream? {
        // An explicit pick from the "Play from" popup addresses a specific mirror entry, which is
        // usually NOT the playing song's own id — honour it when present and fall back to the media
        // id for the automatic path.
        val ytId = (query.directQobuzBackupVideoId ?: query.mediaId).trim()
        val resolved =
            runCatching {
                runBlocking(Dispatchers.IO) {
                    QobuzBackupProvider.resolveStream(ytId, mediaOkHttpClient)
                }
            }.onFailure { error ->
                Timber.tag("MusicService").w(error, "Qobuz backup resolution failed for %s", ytId)
            }.getOrNull() ?: return null

        Timber.tag("MusicService").i(
            "Qobuz backup resolved \"%s\" via mlc-ytify.kouzu.in → %s [%s%s%s]",
            query.title,
            resolved.uri.take(80),
            resolved.contentType,
            if (resolved.isLossless) ", lossless" else "",
            resolved.sampleRate?.let { rate ->
                ", ${resolved.bitDepth?.toString() ?: "?"}bit/${rate / 1000f}kHz"
            }.orEmpty(),
        )
        return DirectStream(
            uri = resolved.uri,
            mimeType = resolved.mimeType,
            codecs = resolved.codecs,
            contentLength = resolved.contentLength,
            label = resolved.label,
            source = AudioSourceType.QOBUZ_BACKUP,
            sampleRate = resolved.sampleRate,
            bitDepth = resolved.bitDepth,
            trustedDirectId = true,
            matchedTitle = query.title,
            matchedArtist = query.artists.joinToString(", ").ifBlank { null },
            matchedAlbum = query.album,
            // The FLAC header's own sample count is the exact length of the file being served;
            // prefer it over the catalogue duration so the Details card and the measured bitrate
            // are both computed from the real file.
            matchedDurationMs = resolved.durationMs ?: query.durationMs,
        )
    }


    private fun parseQobuzInstances(): List<String> =
        dataStore
            .get(QobuzInstancesKey, "")
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private fun parseQobuzAudioQuality(): QobuzAudioQuality {
        val stored = dataStore.get(QobuzAudioQualityKey, QobuzAudioQuality.FLAC.name)
        return runCatching { QobuzAudioQuality.valueOf(stored) }.getOrDefault(QobuzAudioQuality.FLAC)
    }

    /**
     * Resolves a Deezer stream. Unlike Tidal/Qobuz there is no proxy-instance tier, so the pool is the
     * only credential source and the whole source is a no-op until the pool has accounts.
     */
    private fun resolveDeezerStream(query: SourceQuery): DirectStream? {
        // DeezerAudioProvider.accounts() merges the manually signed-in account (setManualArl) with
        // PoolAccountManager.deezerAccounts(). Calling PoolAccountManager directly here bypassed the
        // manual account entirely: a user who signed in through the Deezer login screen would see an
        // empty list even though their ARL was registered, and every track would silently produce null.
        // The guard now uses DeezerAudioProvider.hasAccounts() so both sources are considered.
        if (!DeezerAudioProvider.hasAccounts()) {
            Timber.tag("MusicService").d("Deezer skip: no manual or pooled accounts available")
            return null
        }
        val quality = parseDeezerAudioQuality()
        Timber.tag("MusicService").d("Deezer resolve start | quality=%s", quality.name)
        return runCatching {
            runBlocking(Dispatchers.IO) {
                DeezerAudioProvider
                    .resolve(
                        query =
                            DeezerAudioProvider.Query(
                                mediaId = query.mediaId,
                                title = query.title,
                                artists = query.artists,
                                album = query.album,
                                durationMs = query.durationMs,
                            ),
                        format = quality.toFormatName(),
                    )?.let { resolved ->
                        // The provider deliberately returns its own type so it stays independent of the
                        // playback layer; map it here, at the single point where the two meet.
                        DirectStream(
                            uri = resolved.uri,
                            mimeType = resolved.mimeType,
                            codecs = resolved.codecs,
                            contentLength = resolved.contentLength,
                            label = resolved.label,
                            source = AudioSourceType.DEEZER,
                            matchedTitle = resolved.matchedTitle,
                            matchedArtist = resolved.matchedArtist,
                            matchedAlbum = resolved.matchedAlbum,
                            matchedDurationMs = resolved.matchedDurationMs,
                            sampleRate = resolved.sampleRate,
                            bitDepth = resolved.bitDepth,
                        )
                    }
            }
        }.onFailure { error ->
            Timber.tag("MusicService").w(error, "DEEZER stream resolution failed for %s", query.mediaId)
        }.getOrNull()
    }

    private fun parseDeezerAudioQuality(): DeezerAudioQuality {
        val stored = dataStore.get(DeezerAudioQualityKey, DeezerAudioQuality.FLAC.name)
        return runCatching { DeezerAudioQuality.valueOf(stored) }.getOrDefault(DeezerAudioQuality.FLAC)
    }

    /**
     * Resolves a JioSaavn stream. Ported from vivi-music (https://github.com/vivizzz007/vivi-music).
     *
     * JioSaavn is unauthenticated: we search the public JioSaavn API, pick the best match by
     * title/artist/duration, decrypt the encrypted_media_url via DES-ECB, and select the
     * stream URL matching the user's quality preference. Falls back to YouTube on any failure.
     */
    private fun resolveJioSaavnStream(query: SourceQuery): DirectStream? {
        // NOTE: the global JioSaavnEnabledKey gate was removed here. The chain
        // (sourceResolutionChain / the override list) already filters by enabled-ness,
        // so re-checking it here is redundant for the global-on case. More importantly,
        // it BLOCKED the per-song override path: if the user has JioSaavn globally OFF
        // but explicitly pinned one song to JioSaavn via the Source chooser, this gate
        // returned null and the resolver silently fell back to YouTube — making the
        // override look like it "did nothing". Removing the gate lets the override
        // resolve even when JioSaavn is globally disabled.
        val quality = SaavnAudioQuality.fromStoredName(dataStore.get(SaavnAudioQualityKey, SaavnAudioQuality.QUALITY_320.name))
        val qualityApiValue = quality.toApiValue()
        Timber.tag("MusicService").d("JioSaavn resolve start | quality=%s", qualityApiValue)

        // Build a search query from title + primary artist (+ album as a soft hint).
        val artistHint = query.artists.firstOrNull()?.takeIf { it.isNotBlank() } ?: ""
        val albumHint = query.album?.takeIf { it.isNotBlank() } ?: ""
        val searchQuery =
            buildString {
                append(query.title)
                if (artistHint.isNotBlank()) append(" ").append(artistHint)
                if (albumHint.isNotBlank()) append(" ").append(albumHint)
            }.trim()

        return runCatching {
            runBlocking(Dispatchers.IO) {
                val searchResult = SaavnService.searchSongs(searchQuery).getOrNull() ?: return@runBlocking null
                if (searchResult.isEmpty()) return@runBlocking null

                // Pick best candidate by title/artist/duration. Prefer non-Pro tracks (Pro-Only tracks
                // return a 30-second preview from JioSaavn's CDN and we cannot stream them).
                val wantedDurationSec = query.durationMs?.let { it / 1000 }
                val candidate =
                    searchResult
                        .filter { !it.isProOnly && it.downloadUrl.isNotEmpty() }
                        .minByOrNull { song ->
                            var penalty = 0
                            // Title similarity (simple normalized contains + length delta)
                            val normTitle = song.name.lowercase().replace(JIO_SAAVN_NORMALIZE_REGEX, "")
                            val normWanted = query.title.lowercase().replace(JIO_SAAVN_NORMALIZE_REGEX, "")
                            penalty += if (normTitle == normWanted) 0 else if (normTitle.contains(normWanted) || normWanted.contains(normTitle)) 1 else 5
                            // Artist match
                            val candidateArtist = song.artists.primary.firstOrNull()?.name?.lowercase()?.replace(JIO_SAAVN_NORMALIZE_REGEX, "") ?: ""
                            val wantedArtist = artistHint.lowercase().replace(JIO_SAAVN_NORMALIZE_REGEX, "")
                            if (wantedArtist.isNotBlank() && candidateArtist.isNotBlank()) {
                                penalty += if (candidateArtist == wantedArtist) 0 else if (candidateArtist.contains(wantedArtist) || wantedArtist.contains(candidateArtist)) 1 else 3
                            }
                            // Duration delta
                            if (wantedDurationSec != null && song.duration != null) {
                                val candidateDuration = song.duration!!.toLong()
                                val wantedSec = wantedDurationSec!!
                                val delta = kotlin.math.abs(candidateDuration - wantedSec)
                                penalty += when {
                                    delta <= 3 -> 0
                                    delta <= 10 -> 2
                                    else -> 6
                                }
                            }
                            penalty
                        } ?: return@runBlocking null

                val streamUrl = SaavnService.selectBestUrl(candidate.downloadUrl, qualityApiValue) ?: return@runBlocking null
                val durationMs = candidate.duration?.toLong()?.times(1000L)

                DirectStream(
                    uri = streamUrl,
                    mimeType = "audio/mp4",
                    codecs = "mp4a.40.2",
                    contentLength = null,
                    label = "JioSaavn ${quality.toLabel()}",
                    source = AudioSourceType.JIOSAAVN,
                    matchedTitle = candidate.name,
                    matchedArtist = candidate.artists.primary.firstOrNull()?.name,
                    matchedAlbum = candidate.album?.name,
                    matchedDurationMs = durationMs,
                )
            }
        }.onFailure { error ->
            Timber.tag("MusicService").w(error, "JIOSAAVN stream resolution failed for %s", query.mediaId)
        }.getOrNull()
    }

    /**
     * Applies a resolved [DirectStream] to the [dataSpec]: namespaces the cache key per-source so
     * bytes never collide with the YouTube stream (or another source) for the same media id, and
     * marks the track so YouTube-derived audio normalization is skipped for it.
     */
    private fun applyDirectStream(
        dataSpec: DataSpec,
        mediaId: String,
        stream: DirectStream,
    ): DataSpec {
        Timber.tag("MusicService").i("Using %s stream for %s: %s", stream.source, mediaId, stream.label)
        val cacheKey = sourceCacheKey(stream.source, mediaId)
        stream.contentLength?.takeIf { it > 0L }?.let { contentLengthCache[cacheKey] = it }
        tidalActiveMediaIds.add(mediaId)
        audioNormalizationFactorCache[mediaId] = 1f
        // Persist a FormatEntity for the resolved external stream so the media-info "Details" tab
        // has data to show. Without this, the nerd-stats Details card spun on "please wait" forever
        // for Tidal (and any non-YouTube source), because only the YouTube resolver wrote a format.
        // itag = 0 is a sentinel for "not a YouTube itag" and is hidden in the UI.
        persistDirectStreamFormat(mediaId, stream)
        // Cache the resolved DirectStream so the next playback / prefetch for
        // this media id skips the slow Qobuz/Tidal resolution. The TTL matches
        // the typical signed-URL lifetime on those providers (~5 min).
        directStreamCache[mediaId] = CachedDirectStream(
            stream = stream,
            expiresAtMs = System.currentTimeMillis() + DIRECT_STREAM_CACHE_TTL_MS,
        )
        return dataSpec
            .buildUpon()
            .setUri(stream.uri.toUri())
            .setKey(cacheKey)
            .build()
    }

    /**
     * Writes a [FormatEntity] describing a resolved external ([DirectStream]) source so the
     * media-info "Details" tab can render technical stats instead of spinning forever. Only the
     * YouTube resolver used to persist a format, leaving Tidal streams with no row. We derive what
     * we can from the stream: MIME/codec directly, and a best-effort sample rate / bit depth from
     * the quality tier embedded in [DirectStream.label] (e.g. "HI_RES", "LOSSLESS").
     *
     * If [DirectStream.contentLength] is null (common for Tidal/Qobuz stream URLs that don't
     * expose a Content-Length header upfront), we fire a HEAD request in the background to
     * fetch the real byte size. Without this the nerd-stats card shows "Unknown content
     * length" for FLAC tracks even when the stream itself is fully playable. The HEAD request
     * is best-effort and never blocks playback — the row is upserted immediately with a 0
     * placeholder, then updated once the HEAD round-trip completes.
     */
    private fun persistDirectStreamFormat(
        mediaId: String,
        stream: DirectStream,
    ) {
        val label = stream.label.uppercase()
        val mime = stream.mimeType.substringBefore(";").ifBlank { "audio/flac" }
        val codecs =
            stream.codecs.ifBlank {
                stream.mimeType.substringAfter("codecs=", "").removeSurrounding("\"").ifBlank { "flac" }
            }
        // Use the ACTUAL sample rate and bit depth from the DirectStream when
        // the provider reported them. Fall back to label-based heuristics only
        // when the provider didn't report the actual values (some providers
        // don't expose sampleRate/bitDepth — e.g. JioSaavn always returns null).
        //
        // BUG FIXED: the previous implementation always used hardcoded guesses
        // (44100 Hz / 1411 kbps for "LOSSLESS", 96000 Hz / 2304 kbps for "HI_RES")
        // regardless of the actual stream's properties. This meant ALL lossless
        // songs showed the same bitrate/sample rate in the player's Details tab
        // even when they were different (e.g. a 48kHz/24-bit track showed as
        // 44.1kHz/16-bit).
        val sampleRate = stream.sampleRate?.takeIf { it > 0 }
            ?: when {
                label.contains("HI_RES") || label.contains("MASTER") || label.contains("MQA") -> 96_000
                label.contains("LOSSLESS") || codecs.contains("flac", true) || codecs.contains("alac", true) -> 44_100
                else -> null
            }
        val knownContentLength = stream.contentLength?.takeIf { it > 0L }
        val bitrate =
            measuredBitrate(knownContentLength, stream.matchedDurationMs)
                ?: stream.pcmBitrateOrNull()
                ?: when {
                    label.contains("HI_RES") || label.contains("MASTER") || label.contains("MQA") -> 2_304_000
                    label.contains("LOSSLESS") || codecs.contains("flac", true) || codecs.contains("alac", true) -> 1_411_000
                    label.contains("HIGH") -> 320_000
                    else -> 0
                }
        val formatEntity =
            FormatEntity(
                id = mediaId,
                itag = 0,
                mimeType = mime,
                codecs = codecs,
                bitrate = bitrate,
                sampleRate = sampleRate,
                contentLength = knownContentLength ?: 0L,
                loudnessDb = null,
                perceptualLoudnessDb = null,
                playbackUrl = null,
            )
        // Update the format row with the resolved stream's codec info so the player codec badge
        // reflects what's actually playing (e.g. FLAC, not the YouTube opus row that was
        // persisted by the YouTube resolver). Preserve the authoritative itag and loudness data
        // so the details card and the normalization pipeline keep the real YouTube metadata.
        val existing =
            runBlocking(Dispatchers.IO) {
                database.getFormatsByIds(listOf(mediaId)).firstOrNull()
            }
        if (existing != null) {
            database.query {
                upsert(
                    existing.copy(
                        mimeType = mime,
                        codecs = codecs,
                        bitrate = bitrate,
                        sampleRate = sampleRate,
                        contentLength = knownContentLength ?: 0L,
                    ),
                )
            }
        } else {
            database.query { upsert(formatEntity) }
        }

        // Backfill the content length via a HEAD request if the source didn't
        // provide it. This is what makes the nerd-stats card show e.g.
        // "32.45 MB" for a FLAC track instead of "Unknown content length".
        if (knownContentLength == null) {
            ioScope.launch(SilentHandler) {
                runCatching {
                    val headRequest = okhttp3.Request.Builder()
                        .url(stream.uri)
                        .head()
                        .build()
                    mediaOkHttpClient.newCall(headRequest).execute().use { response ->
                        val len = response.header("Content-Length")?.toLongOrNull() ?: -1L
                        if (len > 0L) {
                            // Re-read the row so we don't clobber any concurrent
                            // updates to other fields (e.g. loudness) — only
                            // patch the contentLength column, plus the bitrate that
                            // can now be measured from it (the row was written with
                            // the tier estimate because the length was unknown).
                            val backfilledBitrate = measuredBitrate(len, stream.matchedDurationMs)
                            val refreshed =
                                runBlocking(Dispatchers.IO) {
                                    database.getFormatsByIds(listOf(mediaId)).firstOrNull()
                                }?.let { row ->
                                    row.copy(
                                        contentLength = len,
                                        bitrate = backfilledBitrate ?: row.bitrate,
                                    )
                                }
                            if (refreshed != null) {
                                database.query { upsert(refreshed) }
                            }
                            // Also surface the size in the in-memory cache so the
                            // download resolver picks it up immediately for the
                            // cache-first prewarm partial-cache check.
                            contentLengthCache[sourceCacheKey(stream.source, mediaId)] = len
                        }
                    }
                }.onFailure { err ->
                    Timber.tag(TAG).d(err, "HEAD request for content length failed: %s", stream.uri)
                }
            }
        }
    }

    /**
     * The stream's true average bitrate in bits/sec, derived from its byte length and
     * playing time, or null when either is unknown.
     *
     * This is what makes the Details card show something specific to the track that is
     * actually playing. The alternative — sample rate x bit depth x channels — is the
     * *uncompressed* PCM ceiling, and it is identical for every file that shares a tier:
     * the Qobuz backup mirror serves almost its whole catalogue as 24-bit/44.1 kHz FLAC,
     * so every lossless track reported exactly the same "2116 kbps" no matter how
     * compressible it was. A measured rate distinguishes them (a sparse ballad
     * compresses to well under half of a dense mix) and is the honest figure for a
     * variable-bitrate codec.
     */
    private fun measuredBitrate(
        contentLength: Long?,
        durationMs: Long?,
    ): Int? {
        val bytes = contentLength?.takeIf { it > 0L } ?: return null
        val millis = durationMs?.takeIf { it > 0L } ?: return null
        val bitsPerSecond = bytes * 8_000L / millis
        // Guard against a bogus duration producing an absurd rate; 50 Mbps is far above
        // anything a music file reaches, so treat it as "unknown" instead of showing it.
        return bitsPerSecond.takeIf { it in 1L..50_000_000L }?.toInt()
    }

    private fun sourceCacheKey(
        source: AudioSourceType,
        mediaId: String,
    ): String =
        when (source) {
            AudioSourceType.TIDAL -> "$TIDAL_CACHE_KEY_PREFIX$mediaId"
            AudioSourceType.QOBUZ -> "qobuz:$mediaId"
            else -> "${source.name.lowercase()}:$mediaId"
        }

    /**
     * Cheap check (no network) for whether an external lossless source (Tidal) should be preferred
     * for [mediaId]. Used to gate the ephemeral YouTube player-cache short-circuit so enabling a
     * source takes effect immediately instead of replaying previously cached YouTube bytes.
     */
    private fun tidalSourceApplies(mediaId: String): Boolean {
        if (mediaId.isLocalMediaId()) return false
        // Defaults MUST match PlaybackSourceSections + sourceResolutionChain(). Any enabled
        // external lossless source (Tidal, Qobuz, Qobuz backup, Deezer, Apple Music) must bypass
        // the ephemeral YouTube player cache so toggling a source on takes effect immediately
        // instead of replaying cached YT bytes.
        return dataStore.get(TidalEnabledKey, true) ||
            dataStore.get(QobuzEnabledKey, false) ||
            dataStore.get(QobuzBackupEnabledKey, false) ||
            dataStore.get(DeezerEnabledKey, false) ||
            dataStore.get(AppleMusicSourceEnabledKey, false)
    }

    private fun resolvePlaybackDataSpec(
        dataSpec: DataSpec,
        allowCacheShortCircuit: Boolean,
    ): DataSpec {
        if (dataSpec.uri.shouldBypassYouTubeResolver()) {
            return dataSpec
        }
        val mediaId = dataSpec.key ?: return dataSpec
        // ResolvingDataSource runs on Media3's loader thread. Headless restores
        // must see the configured auth/proxy/region just like foreground playback.
        runBlocking { moe.rukamori.archivetune.App.startupReadiness.awaitReady() }
        val lowDataModeActive = isLowDataModeActive()
        val storedFormat =
            runBlocking(Dispatchers.IO) {
                database.format(mediaId).first()
            }
        storedFormat?.let { format ->
            audioNormalizationFactorCache[mediaId] = resolveAudioNormalizationFactor(mediaId, storedFormat, normalizeAudio = true)
        }
        val knownContentLength =
            contentLengthCache[mediaId] ?: storedFormat?.contentLength?.takeIf { it > 0L } ?: runCatching {
                downloadCache
                    .getContentMetadata(mediaId)
                    .get(ContentMetadata.KEY_CONTENT_LENGTH, -1L)
            }.getOrNull()?.takeIf { it > 0L } ?: runCatching {
                playerCache
                    .getContentMetadata(mediaId)
                    .get(ContentMetadata.KEY_CONTENT_LENGTH, -1L)
            }.getOrNull()?.takeIf { it > 0L }

        knownContentLength?.takeIf { it > 0L }?.let { contentLengthCache[mediaId] = it }

        // When the Tidal source is enabled for this track, the ephemeral YouTube player cache
        // must not short-circuit playback (otherwise toggling Tidal on would keep replaying the
        // previously cached YouTube bytes). Persistent downloads still win so offline playback
        // and explicit downloads are unaffected.
        val tidalApplies = !lowDataModeActive && tidalSourceApplies(mediaId)
        val allowPlayerCacheShortCircuit = !tidalApplies

        if (allowCacheShortCircuit) {
            resolveCachedDataSpec(
                dataSpec = dataSpec,
                mediaId = mediaId,
                knownContentLength = knownContentLength,
                includePlayerCache = allowPlayerCacheShortCircuit,
            )?.let { cachedDataSpec ->
                scope.launch(Dispatchers.IO) { recoverSong(mediaId) }
                return cachedDataSpec
            }
        }

        val requiredCachedLength =
            if (dataSpec.length >= 0) {
                dataSpec.length
            } else {
                knownContentLength?.let { nonNullContentLength ->
                    (nonNullContentLength - dataSpec.position).takeIf { it > 0L }
                }
            }

        if (allowCacheShortCircuit && requiredCachedLength != null) {
            val isFullyCached =
                downloadCache.isCached(mediaId, dataSpec.position, requiredCachedLength) ||
                    (
                        allowPlayerCacheShortCircuit &&
                            playerCache.isCached(mediaId, dataSpec.position, requiredCachedLength)
                    )
            if (isFullyCached) {
                scope.launch(Dispatchers.IO) { recoverSong(mediaId) }
                return dataSpec
            }
        }

        // Multi-source: attempt to resolve a lossless stream (Tidal) before YouTube.
        resolveMultiSourceDataSpec(dataSpec, mediaId, lowDataModeActive)?.let { sourceDataSpec ->
            scope.launch(Dispatchers.IO) { recoverSong(mediaId) }
            return sourceDataSpec
        }

        val authFingerprint = YouTube.currentPlaybackAuthState().fingerprint
        playbackUrlCache[mediaId]
            ?.takeUnless { lowDataModeActive }
            ?.takeIf {
                it.isValidFor(
                    authFingerprint = authFingerprint,
                    minimumRemainingMs = YTPlayerUtils.STREAM_URL_EXPIRY_SAFETY_MS,
                )
            }?.let {
                scope.launch(Dispatchers.IO) { recoverSong(mediaId) }
                val resolvedDataSpec = dataSpec.withUri(it.url.toUri())
                val length =
                    resolveStreamChunkLength(
                        requestedLength = dataSpec.length,
                        position = dataSpec.position,
                        knownContentLength = knownContentLength,
                        chunkLength = CHUNK_LENGTH,
                        mimeType = storedFormat?.mimeType,
                    )
                return length?.let { nonNullLength ->
                    resolvedDataSpec.subrange(0L, nonNullLength)
                } ?: resolvedDataSpec
            }

        val playbackData =
            runBlocking(Dispatchers.IO) {
                retryWithoutPlaybackLoginContext {
                    YTPlayerUtils.playerResponseForPlayback(
                        mediaId,
                        audioQuality = if (lowDataModeActive) AudioQuality.LOW else audioQuality,
                        connectivityManager = connectivityManager,
                        preferredStreamClient = preferredStreamClient,
                        networkMetered = lowDataModeActive,
                    )
                }.recoverCatching { youtubeFailure ->
                    if (youtubeFailure !is YTPlayerUtils.BotDetectionPlaybackException) throw youtubeFailure

                    Timber.tag("MusicService").w(
                        youtubeFailure,
                        "YouTube stream clients hit bot detection for %s; trying external audio fallback",
                        mediaId,
                    )
                    throw youtubeFailure
                }
            }.getOrElse { throwable ->
                when {
                    throwable is YTPlayerUtils.InvalidPlaybackLoginContextException -> {
                        promptLoginRecovery(mediaId, throwable.targetUrl)
                        throw PlaybackException(
                            getString(R.string.playback_requires_youtube_music_login_refresh),
                            throwable,
                            PlaybackException.ERROR_CODE_REMOTE_ERROR,
                        )
                    }

                    throwable is YTPlayerUtils.LoginRequiredForPlaybackException -> {
                        throw PlaybackException(
                            getString(R.string.playback_requires_youtube_music_confirmation),
                            throwable,
                            PlaybackException.ERROR_CODE_REMOTE_ERROR,
                        )
                    }

                    throwable is YTPlayerUtils.BotDetectionPlaybackException -> {
                        throw PlaybackException(
                            getString(R.string.error_no_stream),
                            throwable,
                            PlaybackException.ERROR_CODE_REMOTE_ERROR,
                        )
                    }

                    throwable is YTPlayerUtils.BadStreamPlayerResponseException -> {
                        throw PlaybackException(
                            getString(R.string.error_no_stream),
                            throwable,
                            PlaybackException.ERROR_CODE_REMOTE_ERROR,
                        )
                    }

                    throwable is PlaybackException -> {
                        throw throwable
                    }

                    throwable.isNetworkConnectionFailure() -> {
                        throw PlaybackException(
                            getString(R.string.error_no_internet),
                            throwable,
                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                        )
                    }

                    throwable.isRequestTimeout() -> {
                        throw PlaybackException(
                            getString(R.string.error_timeout),
                            throwable,
                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                        )
                    }

                    else -> {
                        throw PlaybackException(
                            getString(R.string.error_unknown),
                            throwable,
                            PlaybackException.ERROR_CODE_REMOTE_ERROR,
                        )
                    }
                }
            }

        val nonNullPlayback =
            requireNotNull(playbackData) {
                getString(R.string.error_unknown)
            }
        nonNullPlayback.playbackTracking
            ?.remotePlaybackTrackingUrl()
            ?.let { remotePlaybackTrackingUrlCache[mediaId] = it }
        val format = nonNullPlayback.format
        val loudnessDb = nonNullPlayback.audioConfig?.loudnessDb
        val perceptualLoudnessDb = nonNullPlayback.audioConfig?.perceptualLoudnessDb
        val resolvedContentLength = format.contentLength ?: 0L
        val resolvedCodecs =
            format.mimeType
                .substringAfter("codecs=", "")
                .removeSurrounding("\"")
                .substringBefore("\"")
        resolvedContentLength.takeIf { it > 0L }?.let { contentLengthCache[mediaId] = it }

        Timber
            .tag(
                "AudioNormalization",
            ).d("Storing format for $mediaId with loudnessDb: $loudnessDb, perceptualLoudnessDb: $perceptualLoudnessDb")
        if (loudnessDb == null && perceptualLoudnessDb == null) {
            Timber.tag("AudioNormalization").w("No loudness data available from YouTube for video: $mediaId")
        }

        val formatEntity =
            FormatEntity(
                id = mediaId,
                itag = format.itag,
                mimeType = format.mimeType.split(";")[0],
                codecs = resolvedCodecs,
                bitrate = format.bitrate,
                sampleRate = format.audioSampleRate,
                contentLength = resolvedContentLength,
                loudnessDb = loudnessDb,
                perceptualLoudnessDb = perceptualLoudnessDb,
                playbackUrl = nonNullPlayback.playbackTracking?.videostatsPlaybackUrl?.baseUrl,
            )
        val resolvedNormalizationFactor = calculateAudioNormalizationFactor(formatEntity, normalizeAudio = true)
        audioNormalizationFactorCache[mediaId] = resolvedNormalizationFactor
        scope.launch {
            if (currentMediaMetadata.value?.id == mediaId &&
                dataStore.get(AudioNormalizationKey, true)
            ) {
                normalizeFactor.value = resolvedNormalizationFactor
            }
        }

        database.query {
            upsert(
                formatEntity,
            )
        }
        scope.launch(Dispatchers.IO) { recoverSong(mediaId, nonNullPlayback) }

        val streamUrl = nonNullPlayback.streamUrl

        val trackingExpiryMs = System.currentTimeMillis() + (nonNullPlayback.streamExpiresInSeconds * 1000L)

        if (!lowDataModeActive) {
            playbackUrlCache[mediaId] =
                AuthScopedCacheValue(
                    url = streamUrl,
                    expiresAtMs = trackingExpiryMs,
                    authFingerprint = nonNullPlayback.authFingerprint,
                )
        }
        val resolvedDataSpec = dataSpec.withUri(streamUrl.toUri())
        val length =
            resolveStreamChunkLength(
                requestedLength = dataSpec.length,
                position = dataSpec.position,
                knownContentLength = format.contentLength,
                chunkLength = CHUNK_LENGTH,
                mimeType = format.mimeType,
            )
        return length?.let { nonNullLength ->
            resolvedDataSpec.subrange(0L, nonNullLength)
        } ?: resolvedDataSpec
    }



    private fun resolveCachedDataSpec(
        dataSpec: DataSpec,
        mediaId: String,
        knownContentLength: Long?,
        includePlayerCache: Boolean = true,
    ): DataSpec? {
        val requestedLength =
            when {
                dataSpec.length > 0L -> {
                    dataSpec.length
                }

                knownContentLength != null && knownContentLength > dataSpec.position -> {
                    knownContentLength - dataSpec.position
                }

                else -> {
                    // No known content length and no explicit request length.
                    // For offline playback of fully-downloaded songs whose
                    // cache metadata never had the content length persisted,
                    // compute the total cached byte range across all candidate
                    // keys and use that as the requested length. This is the
                    // key fix for "downloaded songs don't play when offline" —
                    // without it, the resolver returns null here, falls
                    // through to the YouTube resolver, and fails offline.
                    val candidateKeys = listOf(mediaId, "qobuz:$mediaId", "tidal:$mediaId", "deezer:$mediaId")
                    val maxCachedLength =
                        candidateKeys.maxOfOrNull { key ->
                            runCatching {
                                val spans = downloadCache.getCachedSpans(key).toList() +
                                    (if (includePlayerCache) playerCache.getCachedSpans(key).toList() else emptyList())
                                if (spans.isEmpty()) {
                                    0L
                                } else {
                                    // Sum up the total cached bytes starting from
                                    // dataSpec.position. For a fully-downloaded
                                    // song, this equals the content length.
                                    val sortedSpans = spans.sortedBy { it.position }
                                    var total = 0L
                                    var cursor = dataSpec.position
                                    for (span in sortedSpans) {
                                        if (span.position > cursor) break
                                        val spanEnd = span.position + span.length
                                        if (spanEnd > cursor) {
                                            total += (spanEnd - cursor)
                                            cursor = spanEnd
                                        }
                                    }
                                    total
                                }
                            }.getOrDefault(0L)
                        }
                    if (maxCachedLength == null || maxCachedLength <= 0L) {
                        return null
                    }
                    maxCachedLength
                }
            }

        // Find the first source-prefixed key (or the bare mediaId) that
        // has the requested byte range fully cached. Returning the
        // DataSpec with the matching key is critical — without it the
        // CacheDataSource would look up the bytes under the bare mediaId
        // and miss the lossless FLAC bytes cached under "qobuz:$mediaId"
        // / "tidal:$mediaId", then fall through to the YouTube resolver
        // and serve a lossy MP3 stream — producing Code 3003 when the
        // Media3 extractors then tried to read MP3 bytes under a FLAC
        // FormatEntity.
        val candidateKeys = listOf(mediaId, "qobuz:$mediaId", "tidal:$mediaId", "deezer:$mediaId")
        val matchingKey = candidateKeys.firstOrNull { key ->
            getContinuousCachedLengthForKey(
                key = key,
                position = dataSpec.position,
                requestedLength = requestedLength,
                includePlayerCache = includePlayerCache,
            ) >= requestedLength
        } ?: return null

        // DataSpec.Builder has no subrange() method (subrange() is defined
        // on the DataSpec data class, not on its Builder). Use the Builder
        // equivalents setPosition() / setLength() to scope the cached
        // request to the bytes that are actually present.
        return dataSpec.buildUpon()
            .setKey(matchingKey)
            .setPosition(0L)
            .setLength(requestedLength)
            .build()
    }

    /**
     * Same as [getContinuousCachedLength] but for a specific cache key.
     * Used by [resolveCachedDataSpec] to find the source-prefixed key
     * whose cache spans fully cover the requested range.
     */
    private fun getContinuousCachedLengthForKey(
        key: String,
        position: Long,
        requestedLength: Long,
        includePlayerCache: Boolean = true,
    ): Long {
        val targetEnd = position.saturatingAdd(requestedLength)
        var cursor = position
        val playerCacheSpans =
            if (includePlayerCache) {
                runCatching { playerCache.getCachedSpans(key).toList() }.getOrNull().orEmpty()
            } else {
                emptyList()
            }
        val spans =
            (
                runCatching { downloadCache.getCachedSpans(key).toList() }.getOrNull().orEmpty() +
                    playerCacheSpans
            ).asSequence()
                .filter { span -> span.position.saturatingAdd(span.length) > position }
                .sortedBy { span -> span.position }
                .toList()

        for (span in spans) {
            if (span.position > cursor) break
            val spanEnd = span.position.saturatingAdd(span.length)
            if (spanEnd > cursor) {
                cursor = minOf(spanEnd, targetEnd)
                if (cursor >= targetEnd) break
            }
        }

        return (cursor - position).coerceAtLeast(0L)
    }

    private fun getContinuousCachedLength(
        mediaId: String,
        position: Long,
        requestedLength: Long,
        includePlayerCache: Boolean = true,
    ): Long {
        val targetEnd = position.saturatingAdd(requestedLength)
        var cursor = position
        // Include source-prefixed cache keys (qobuz:, tidal:, deezer:) so
        // that a song downloaded from a lossless source plays back as the
        // lossless bytes — not as a re-fetched YouTube Music stream. Without
        // this, playing a downloaded FLAC song would bypass the cached FLAC
        // bytes (keyed as "qobuz:abc") and fall through to the YouTube
        // resolver, which serves a different (lossy MP3/AAC) stream —
        // causing the "Code 3003 UnrecognizedInputFormatException" when the
        // Media3 extractors received an MP3 stream under a FLAC cache key.
        val candidateKeys = listOf(mediaId, "qobuz:$mediaId", "tidal:$mediaId", "deezer:$mediaId")
        val playerCacheSpans =
            if (includePlayerCache) {
                candidateKeys.flatMap { key ->
                    runCatching { playerCache.getCachedSpans(key).toList() }.getOrNull().orEmpty()
                }
            } else {
                emptyList()
            }
        val spans =
            (
                candidateKeys.flatMap { key ->
                    runCatching { downloadCache.getCachedSpans(key).toList() }.getOrNull().orEmpty()
                } + playerCacheSpans
            ).asSequence()
                .filter { span -> span.position.saturatingAdd(span.length) > position }
                .sortedBy { span -> span.position }
                .toList()

        for (span in spans) {
            if (span.position > cursor) break
            val spanEnd = span.position.saturatingAdd(span.length)
            if (spanEnd > cursor) {
                cursor = minOf(spanEnd, targetEnd)
                if (cursor >= targetEnd) break
            }
        }

        return (cursor - position).coerceAtLeast(0L)
    }

    private fun Long.saturatingAdd(value: Long): Long {
        if (value <= 0L) return this
        val result = this + value
        return if (result < this) Long.MAX_VALUE else result
    }

    private fun Uri.shouldBypassYouTubeResolver(): Boolean {
        val normalizedScheme = scheme?.lowercase(Locale.US)
        return normalizedScheme == "content" ||
            normalizedScheme == "file" ||
            normalizedScheme == "android.resource" ||
            normalizedScheme == "telegram" ||
            normalizedScheme == DeezerCrypto.SCHEME ||
            normalizedScheme == TidalAudioProvider.PROGRESSIVE_DASH_SCHEME ||
            normalizedScheme == "http" ||
            normalizedScheme == "https"
    }

    private fun Uri.shouldBypassPlayerCache(): Boolean {
        val normalizedScheme = scheme?.lowercase(Locale.US)
        return normalizedScheme == "content" ||
            normalizedScheme == "file" ||
            normalizedScheme == "android.resource" ||
            normalizedScheme == "telegram"
    }

    private fun deviceSupportsMimeType(mimeType: String): Boolean =
        runCatching {
            val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
            codecList.codecInfos.any { info ->
                !info.isEncoder && info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) }
            }
        }.getOrDefault(false)

    private fun createMediaSourceFactory() =
        DefaultMediaSourceFactory(
            createDataSourceFactory(),
            DefaultExtractorsFactory()
                // Enable constant-bitrate seeking for CBR streams (most MP3/AAC files) so
                // the user can seek to any position without needing a seek table. Without
                // this, ExoPlayer can only seek to keyframes, making seeking in .m4a/.mp3
                // files imprecise or impossible.
                .setConstantBitrateSeekingEnabled(true),
        )
            // Apple Music full-track streams are CENC-encrypted fMP4; the Widevine L3
            // session (license exchanged against Apple's acquireWebPlaybackLicense) is
            // attached only for media ids resolved through the Apple source — everything
            // else stays DRM-free with the default behavior.
            .setDrmSessionManagerProvider { mediaItem ->
                val appleTrack = mediaItem.mediaId?.let { appleDrmTrackInfo[it] }
                if (appleTrack != null) buildAppleDrmSessionManager(appleTrack) ?: DrmSessionManager.DRM_UNSUPPORTED
                else DrmSessionManager.DRM_UNSUPPORTED
            }

    /**
     * Media ids resolved through the Apple Music source, with the per-track values Apple's
     * license exchange needs (adamId + the playlist's raw EXT-X-KEY `uri`). Registered when
     * the resolver returns an Apple stream so the DRM provider knows which media items need
     * the Widevine session. Entries stay for the process lifetime (tiny; ids only).
     */
    private class AppleTrackDrmInfo(
        val adamId: String,
        val drmUri: String,
    )

    private val appleDrmTrackInfo: MutableMap<String, AppleTrackDrmInfo> = ConcurrentHashMap()

    private fun buildAppleDrmSessionManager(track: AppleTrackDrmInfo): DrmSessionManager? {
        val mediaToken = AppleMusicAudioProvider.mediaUserToken() ?: return null
        val devToken = AppleMusicAudioProvider.devToken()
        val callback = AppleLicenseCallback(track, devToken, mediaToken)
        return DefaultDrmSessionManager
            .Builder()
            .setUuidAndExoMediaDrmProvider(
                C.WIDEVINE_UUID,
                ExoMediaDrm.Provider { uuid ->
                    // FrameworkMediaDrm's constructor is private — use newInstance and force
                    // software-level L3: Apple's license server issues keys for the web
                    // playback pipeline, which is L3-shaped. On failure fall back to the
                    // default provider (handles unsupported-scheme gracefully).
                    val created =
                        runCatching { FrameworkMediaDrm.newInstance(uuid) }.getOrNull()?.apply {
                            // The exact property value differs across vendors; try both and
                            // ignore failures — worst case the device default level is used.
                            runCatching { setPropertyString("securityLevel", "L3") }
                                .recoverCatching { setPropertyString("securityLevel", "3") }
                        }
                    created ?: FrameworkMediaDrm.DEFAULT_PROVIDER.acquireExoMediaDrm(uuid)
                },
            )
            .build(callback)
    }

    /**
     * Speaks Apple's license-exchange protocol (verified against gamdl): the Widevine
     * challenge travels BASE64 inside a JSON envelope —
     * `{"challenge", "key-system", "uri", "adamId", "isLibrary", "user-initiated"}` —
     * and the response is JSON whose `license` field carries the base64 license bytes.
     * A plain HttpMediaDrmCallback posts the raw challenge and chokes on the JSON response,
     * which is why Apple playback was silent. Provisioning (L3 device registration) still
     * goes to Google's default endpoint.
     */
    private inner class AppleLicenseCallback(
        private val track: AppleTrackDrmInfo,
        private val devToken: String?,
        private val mediaToken: String,
    ) : MediaDrmCallback {
        // Provisioning uses Widevine's normal server, not Apple's.
        private val provisionFallback = HttpMediaDrmCallback(null, OkHttpDataSource.Factory(mediaOkHttpClient))

        private fun fail(message: String): Nothing {
            Timber.tag("MusicService").w(message)
            val uri = android.net.Uri.parse(APPLE_LICENSE_URL)
            throw MediaDrmCallbackException(
                DataSpec(uri),
                uri,
                emptyMap(),
                0L,
                java.io.IOException(message),
            )
        }

        override fun executeProvisionRequest(
            uuid: java.util.UUID,
            request: ExoMediaDrm.ProvisionRequest,
        ): MediaDrmCallback.Response = provisionFallback.executeProvisionRequest(uuid, request)

        override fun executeKeyRequest(
            uuid: java.util.UUID,
            request: ExoMediaDrm.KeyRequest,
        ): MediaDrmCallback.Response {
            val body =
                JSONObject()
                    .put("challenge", android.util.Base64.encodeToString(request.data, android.util.Base64.NO_WRAP))
                    .put("key-system", "com.widevine.alpha")
                    .put("uri", track.drmUri)
                    .put("adamId", track.adamId)
                    .put("isLibrary", false)
                    .put("user-initiated", true)
            val builder =
                Request
                    .Builder()
                    .url(APPLE_LICENSE_URL)
                    .header("Content-Type", "application/json")
                    .header("Origin", "https://music.apple.com")
                    .header("Referer", "https://music.apple.com/")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36")
                    .post(body.toString().toRequestBody("application/json".toMediaTypeOrNull()))
            devToken?.let { builder.header("Authorization", "Bearer $it") }
            builder.header("Media-User-Token", mediaToken)
            mediaOkHttpClient.newCall(builder.build()).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    fail("Apple license exchange failed: HTTP ${response.code} ${text.take(200)}")
                }
                val json =
                    runCatching { JSONObject(text) }.getOrElse {
                        fail("Apple license response is not JSON: ${text.take(200)}")
                    }
                val status = json.optInt("status", -1)
                if (status != 0) {
                    val customer = json.optString("customerMessage").ifBlank { text.take(200) }
                    fail("Apple license exchange rejected (status=$status): $customer")
                }
                val license = json.optString("license")
                if (license.isBlank()) fail("Apple license response has no license field")
                val decoded =
                    runCatching { android.util.Base64.decode(license, android.util.Base64.DEFAULT) }.getOrElse {
                        fail("Apple license base64 decode failed: ${it.message}")
                    }
                return MediaDrmCallback.Response(decoded)
            }
        }
    }

    private class SchemeRoutingDataSource(
        private val cachedFactory: DataSource.Factory,
        private val directFactory: DataSource.Factory,
        private val telegramFactory: DataSource.Factory,
        private val deezerFactory: DataSource.Factory,
        private val tidalProgressiveDashFactory: DataSource.Factory,
    ) : DataSource {
        private val transferListeners = mutableListOf<TransferListener>()
        private var delegate: DataSource? = null

        override fun addTransferListener(transferListener: TransferListener) {
            transferListeners += transferListener
            delegate?.addTransferListener(transferListener)
        }

        override fun open(dataSpec: DataSpec): Long {
            val normalizedScheme = dataSpec.uri.scheme?.lowercase(Locale.US)
            val selectedFactory =
                if (normalizedScheme == "telegram") {
                    // Telegram tracks stream through TDLib's own partial-file cache; Media3's
                    // caches and the YouTube resolver chain must both stay out of the way.
                    telegramFactory
                } else if (normalizedScheme == DeezerCrypto.SCHEME) {
                    // Carries its own cache; the YouTube resolver would rewrite the URI.
                    deezerFactory
                } else if (normalizedScheme == TidalAudioProvider.PROGRESSIVE_DASH_SCHEME) {
                    // Streams through its own progressive-DASH source; the YouTube resolver
                    // would rewrite the URI.
                    tidalProgressiveDashFactory
                } else if (
                    normalizedScheme == "content" ||
                    normalizedScheme == "file" ||
                    normalizedScheme == "android.resource"
                ) {
                    directFactory
                } else {
                    cachedFactory
                }
            val selectedDataSource = selectedFactory.createDataSource()
            transferListeners.forEach(selectedDataSource::addTransferListener)
            delegate = selectedDataSource
            return selectedDataSource.open(dataSpec)
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

    /** Routes provider URIs after the resolving/cache layers have already selected a stream. */
    private class ResolvedSchemeRoutingDataSource(
        private val defaultFactory: DataSource.Factory,
        private val deezerFactory: DataSource.Factory,
        private val tidalProgressiveDashFactory: DataSource.Factory,
    ) : DataSource {
        private val transferListeners = mutableListOf<TransferListener>()
        private var delegate: DataSource? = null

        override fun addTransferListener(transferListener: TransferListener) {
            transferListeners += transferListener
            delegate?.addTransferListener(transferListener)
        }

        override fun open(dataSpec: DataSpec): Long {
            val scheme = dataSpec.uri.scheme?.lowercase(Locale.US)
            val factory =
                when (scheme) {
                    DeezerCrypto.SCHEME -> deezerFactory
                    TidalAudioProvider.PROGRESSIVE_DASH_SCHEME -> tidalProgressiveDashFactory
                    else -> defaultFactory
                }
            val selected = factory.createDataSource()
            transferListeners.forEach(selected::addTransferListener)
            delegate = selected
            return selected.open(dataSpec)
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

    private fun updateAudioOffload(enabled: Boolean) {
        val effectiveEnabled = enabled && !crossfadeEnabled
        runCatching {
            val builder = localPlayer.trackSelectionParameters.buildUpon()
            val audioOffloadPrefsClass = Class.forName("androidx.media3.common.AudioOffloadPreferences")
            val audioOffloadPrefsBuilderClass = Class.forName("androidx.media3.common.AudioOffloadPreferences\$Builder")

            val modeFieldName = if (effectiveEnabled) "AUDIO_OFFLOAD_MODE_ENABLED" else "AUDIO_OFFLOAD_MODE_DISABLED"
            val mode = audioOffloadPrefsClass.getField(modeFieldName).getInt(null)

            val prefsBuilder = audioOffloadPrefsBuilderClass.getDeclaredConstructor().newInstance()
            audioOffloadPrefsBuilderClass.getMethod("setAudioOffloadMode", Int::class.javaPrimitiveType).invoke(prefsBuilder, mode)
            val prefs = audioOffloadPrefsBuilderClass.getMethod("build").invoke(prefsBuilder)

            val setMethod =
                builder.javaClass.methods.firstOrNull { method ->
                    method.name == "setAudioOffloadPreferences" && method.parameterTypes.size == 1
                }
            if (setMethod != null) {
                setMethod.invoke(builder, prefs)
                localPlayer.trackSelectionParameters = builder.build()
            }
        }
        localPlayer.setOffloadEnabled(effectiveEnabled)
    }

    private fun updateWakeLock() {
        val wl = wakeLock ?: return
        val shouldHold = wakelockEnabled && player.isPlaying
        if (shouldHold && !wl.isHeld) {
            wl.acquire()
        } else if (!shouldHold && wl.isHeld) {
            wl.release()
        }
    }

    private fun createPrimaryLoadControl(): DefaultLoadControl =
        DefaultLoadControl
            .Builder()
            .setBufferDurationsMs(
                PRIMARY_MIN_BUFFER_MS,
                PRIMARY_MAX_BUFFER_MS,
                PRIMARY_BUFFER_FOR_PLAYBACK_MS,
                PRIMARY_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
            ).setPrioritizeTimeOverSizeThresholds(true)
            .build()

    private fun createCrossfadeLoadControl(): DefaultLoadControl =
        DefaultLoadControl
            .Builder()
            .setBufferDurationsMs(
                CROSSFADE_MIN_BUFFER_MS,
                CROSSFADE_MAX_BUFFER_MS,
                CROSSFADE_MIN_BUFFER_BEFORE_START_MS.toInt(),
                CROSSFADE_MIN_BUFFER_BEFORE_START_MS.toInt(),
            ).setPrioritizeTimeOverSizeThresholds(true)
            .build()

    private fun createRenderersFactory() =
        object : DefaultRenderersFactory(this) {
            init {
                // Enable decoder fallback so that when a primary (typically hardware) decoder fails
                // — e.g. MediaTek's c2.mtk.alac.decoder on .m4a/ALAC files (error 0x80000000) —
                // ExoPlayer can fall back to any alternative decoder advertised by the platform,
                // including any extension software decoder that may be registered.
                // Also opt in to extension renderers as a fallback tier (EXTENSION_RENDERER_MODE_ON):
                // extension decoders come AFTER the platform MediaCodec ones, so default hardware
                // acceleration is preserved but a software fallback becomes reachable.
                setEnableDecoderFallback(true)
                setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            }

            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ) = DefaultAudioSink
                .Builder(context)
                .setEnableFloatOutput(false)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessorChain(
                    DefaultAudioSink.DefaultAudioProcessorChain(
                        SilenceSkippingAudioProcessor(
                            1_500_000L,
                            0.35f,
                            500_000L,
                            10,
                            150.toShort(),
                        ),
                        SonicAudioProcessor(),
                    ),
                ).build()
        }

    override fun onPlaybackStatsReady(
        eventTime: AnalyticsListener.EventTime,
        playbackStats: PlaybackStats,
    ) {
        val mediaItem = eventTime.timeline.getWindow(eventTime.windowIndex, Timeline.Window()).mediaItem
        val mediaId = mediaItem.mediaId
        val thresholdMs = historyThresholdMs()
        val pendingSession = popPendingHistoryFinalization(mediaId)
        val alreadyPersistedForSession = pendingSession?.eventId != null || pendingSession?.remoteRegistered == true
        val reachedHistoryThreshold =
            playbackStats.totalPlayTimeMs >= thresholdMs &&
                !dataStore.get(PauseListenHistoryKey, false)
        val shouldPersistHistory = alreadyPersistedForSession || reachedHistoryThreshold

        if (shouldPersistHistory) {
            ioScope.launch {
                val pendingResult =
                    pendingSession?.let { session ->
                        historyRecordingJobs[session.sessionToken]
                            ?.let { deferred ->
                                runCatching { deferred.await() }
                                    .onFailure(::reportException)
                                    .getOrNull()
                            }?.let { result ->
                                session.copy(
                                    eventId = result.eventId ?: session.eventId,
                                    remoteRegistered = session.remoteRegistered || result.remoteRegistered,
                                )
                            }
                            ?: session
                    }

                val fallbackMetadata = mediaItem.metadata
                val eventId =
                    pendingResult?.eventId ?: insertPlaybackHistoryEvent(
                        mediaId = mediaId,
                        playTimeMs = playbackStats.totalPlayTimeMs,
                        mediaMetadata = fallbackMetadata,
                    )

                if (eventId != null) {
                    runCatching {
                        database.updateEventPlayTime(eventId, playbackStats.totalPlayTimeMs)
                    }.onFailure(::reportException)
                }

                try {
                    database.withTransaction {
                        incrementTotalPlayTime(mediaId, playbackStats.totalPlayTimeMs)
                    }
                } catch (_: SQLException) {
                } catch (throwable: Throwable) {
                    reportException(throwable)
                }

                if (pendingResult?.remoteRegistered != true) {
                    registerRemotePlaybackHistory(mediaId)
                }
            }

            ioScope.launch {
                try {
                    val song =
                        database.song(mediaId).first()
                            ?: return@launch

                    val lbEnabled = dataStore.get(ListenBrainzEnabledKey, false)
                    val lbToken = dataStore.get(ListenBrainzTokenKey, "")
                    if (lbEnabled && !lbToken.isNullOrBlank()) {
                        val endMs = System.currentTimeMillis()
                        val startMs = endMs - playbackStats.totalPlayTimeMs
                        try {
                            ListenBrainzManager.submitFinished(this@MusicService, lbToken, song, startMs, endMs)
                        } catch (ie: Exception) {
                            Timber.tag("MusicService").v(ie, "ListenBrainz finished submit failed")
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun currentPresenceSong(): Song? =
        resolvePresenceSong(
            dbSong = currentSong.value,
            mediaMetadata = player.currentMetadata,
            durationMs = player.duration,
        )

    private fun resolvePresenceSong(
        dbSong: Song?,
        mediaMetadata: MediaMetadata?,
        durationMs: Long,
    ): Song? {
        val metadataSong = mediaMetadata?.let { createTransientSongFromMedia(it) }
        val song =
            when {
                dbSong == null -> metadataSong
                metadataSong == null -> dbSong
                else -> dbSong.withPresenceMetadata(metadataSong)
            }

        return song.withResolvedPresenceDuration(durationMs)
    }

    private fun Song.withPresenceMetadata(metadataSong: Song): Song {
        val resolvedArtists =
            metadataSong.artists.takeIf { metadataArtists ->
                metadataArtists.any { it.hasRemotePresenceId() }
            } ?: artists

        return copy(
            song =
                song.copy(
                    thumbnailUrl = song.thumbnailUrl ?: metadataSong.song.thumbnailUrl,
                    albumId = song.albumId ?: metadataSong.song.albumId,
                    albumName = song.albumName ?: metadataSong.song.albumName,
                ),
            artists = resolvedArtists,
            album = album ?: metadataSong.album,
        )
    }

    private fun Song?.withResolvedPresenceDuration(durationMs: Long): Song? {
        val song = this ?: return null
        if (song.song.duration > 0 || durationMs <= 0) return song
        val durationSeconds =
            (durationMs / 1000L)
                .coerceAtLeast(1L)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
        return song.copy(song = song.song.copy(duration = durationSeconds))
    }

    private fun ArtistEntity.hasRemotePresenceId(): Boolean = channelId.isRemotePresenceId() || id.isRemotePresenceId()

    private fun String?.isRemotePresenceId(): Boolean {
        val id = this?.trim()?.takeIf { it.isNotBlank() } ?: return false
        return !id.isLocalMediaId() &&
            !id.isTelegramMediaId() &&
            !id.startsWith("LOCAL_ARTIST_") &&
            !id.startsWith("LA") &&
            !id.contains("privately_owned_artist", ignoreCase = true)
    }

    // Create a transient Song object from current Player MediaMetadata when the DB doesn't have it.
    private fun createTransientSongFromMedia(media: MediaMetadata): Song {
        val songEntity =
            SongEntity(
                id = media.id,
                title = media.title,
                duration = media.duration,
                thumbnailUrl = media.thumbnailUrl,
                albumId = media.album?.id,
                albumName = media.album?.title,
                explicit = media.explicit,
                isMusicVideo = media.isMusicVideo,
                isLocal = media.id.isLocalMediaId(),
            )

        val artists =
            media.artists.map { artist ->
                ArtistEntity(
                    id = artist.id ?: "LA_unknown_${artist.name}",
                    name = artist.name,
                    thumbnailUrl = if (!artist.thumbnailUrl.isNullOrBlank()) artist.thumbnailUrl else media.thumbnailUrl,
                    isLocal = artist.id == null || artist.id.isLocalMediaId(),
                )
            }

        val album =
            media.album?.let { alb ->
                AlbumEntity(
                    id = alb.id,
                    playlistId = null,
                    title = alb.title,
                    year = null,
                    thumbnailUrl = media.thumbnailUrl,
                    themeColor = null,
                    songCount = 1,
                    duration = media.duration,
                    isLocal = media.id.isLocalMediaId(),
                )
            }

        return Song(
            song = songEntity,
            artists = artists,
            album = album,
            format = null,
        )
    }

    private inline fun <reified T> readPersistentObject(fileName: String): T? {
        val persistentFile = filesDir.resolve(fileName)
        if (!persistentFile.exists() || !persistentFile.isFile) return null

        return synchronized(persistentStateLock) {
            runCatching {
                persistentFile.inputStream().use { fis ->
                    ObjectInputStream(fis).use { input ->
                        val payload = input.readObject()
                        check(payload is T) { "Unexpected persistent payload type for $fileName" }
                        payload
                    }
                }
            }.onFailure {
                Timber.tag(TAG).w(it, "Failed to read persistent file: $fileName")
            }.getOrNull()
        }
    }

    private fun clearPersistedQueueFiles() {
        persistentSaveGeneration.incrementAndGet()
        synchronized(persistentStateLock) {
            listOf(
                PERSISTENT_QUEUE_FILE,
                PERSISTENT_PLAYER_STATE_FILE,
                PERSISTENT_AUTOMIX_FILE,
            ).forEach { fileName ->
                val persistentFile = filesDir.resolve(fileName)
                val tempFile = filesDir.resolve("$fileName.tmp")
                runCatching {
                    if (persistentFile.exists() && !persistentFile.delete()) {
                        Timber.tag(TAG).w("Failed to delete persistent file: $fileName")
                    }
                    if (tempFile.exists() && !tempFile.delete()) {
                        Timber.tag(TAG).w("Failed to delete temporary persistent file: $fileName")
                    }
                }.onFailure {
                    Timber.tag(TAG).w(it, "Failed to clear persistent file: $fileName")
                }
            }
        }
    }

    private fun writePersistentObject(
        fileName: String,
        payload: Serializable,
    ) {
        val persistentFile = filesDir.resolve(fileName)
        val tempFile = filesDir.resolve("$fileName.tmp")

        synchronized(persistentStateLock) {
            runCatching {
                FileOutputStream(tempFile).use { fos ->
                    ObjectOutputStream(fos).use { output ->
                        output.writeObject(payload)
                        output.flush()
                    }
                }

                if (!tempFile.renameTo(persistentFile)) {
                    if (persistentFile.exists() && !persistentFile.delete()) {
                        error("Could not replace $fileName")
                    }
                    if (!tempFile.renameTo(persistentFile)) {
                        error("Could not atomically move $fileName")
                    }
                }
            }.onFailure {
                runCatching { tempFile.delete() }
                reportException(it)
            }
        }
    }

    private fun MediaItem.toPersistableMetadata(): moe.rukamori.archivetune.models.MediaMetadata? {
        val tagged = metadata
        if (tagged != null) return tagged

        val id =
            mediaId
                .trim()
                .ifBlank {
                    localConfiguration
                        ?.uri
                        ?.toString()
                        ?.trim()
                        .orEmpty()
                }.takeIf { it.isNotBlank() } ?: return null

        val title =
            mediaMetadata.title
                ?.toString()
                ?.trim()
                .takeIf { !it.isNullOrBlank() }
                ?: id

        val artistText =
            mediaMetadata.artist
                ?.toString()
                ?.trim()
                .takeIf { !it.isNullOrBlank() }
                ?: mediaMetadata.subtitle
                    ?.toString()
                    ?.trim()
                    .takeIf { !it.isNullOrBlank() }

        val artists =
            artistText
                ?.split(",")
                ?.mapNotNull { it.trim().takeIf(String::isNotBlank) }
                ?.map { name ->
                    moe.rukamori.archivetune.models.MediaMetadata
                        .Artist(id = null, name = name)
                }.orEmpty()

        val thumbnailUrl = mediaMetadata.artworkUri?.toString()
        val albumTitle =
            mediaMetadata.albumTitle
                ?.toString()
                ?.trim()
                .takeIf { !it.isNullOrBlank() }
        val album =
            albumTitle?.let { titleValue ->
                moe.rukamori.archivetune.models.MediaMetadata
                    .Album(id = titleValue, title = titleValue)
            }

        return moe.rukamori.archivetune.models.MediaMetadata(
            id = id,
            title = title,
            artists = artists,
            duration = -1,
            thumbnailUrl = thumbnailUrl,
            album = album,
            explicit = false,
            liked = false,
            likedDate = null,
            inLibrary = null,
        )
    }

    /**
     * Last player state actually written, compared ignoring [PersistPlayerState.timestamp] so a
     * paused player — whose position does not move — stops rewriting an identical file every tick.
     */
    @Volatile
    private var lastPersistedPlayerState: PersistPlayerState? = null

    /**
     * Writes just the resume position and transport flags, skipping the queue snapshot.
     *
     * Deliberately not [saveQueueToDisk]: that maps every media item to persistable metadata on
     * the main thread and writes two files. For the periodic save none of that changes between
     * ticks — only the position does — and the queue file is already written by every handler that
     * can change it.
     */
    private suspend fun savePlayerStateToDisk() {
        val saveGeneration = persistentSaveGeneration.get()
        val state =
            withContext(Dispatchers.Main.immediate) {
                if (
                    saveGeneration != persistentSaveGeneration.get() ||
                    isRestoringPersistentState ||
                    isHydratingRestoredQueue ||
                    player.mediaItemCount == 0
                ) {
                    return@withContext null
                }
                PersistPlayerState(
                    playWhenReady = player.playWhenReady,
                    repeatMode = player.repeatMode,
                    shuffleModeEnabled = player.shuffleModeEnabled,
                    volume = playerVolume.value,
                    currentPosition = player.currentPosition,
                    currentMediaItemIndex = player.currentMediaItemIndex,
                    playbackState = player.playbackState,
                )
            } ?: return

        // timestamp defaults to now, so it always differs — compare everything else.
        val previous = lastPersistedPlayerState
        if (previous != null && previous.copy(timestamp = 0L) == state.copy(timestamp = 0L)) return

        withContext(Dispatchers.IO) {
            if (saveGeneration != persistentSaveGeneration.get()) return@withContext
            writePersistentObject(PERSISTENT_PLAYER_STATE_FILE, state)
            lastPersistedPlayerState = state
        }
    }

    private suspend fun saveQueueToDisk() {
        val saveGeneration = persistentSaveGeneration.get()
        val snapshot =
            withContext(Dispatchers.Main.immediate) {
                if (
                    saveGeneration != persistentSaveGeneration.get() ||
                    isRestoringPersistentState ||
                    isHydratingRestoredQueue
                ) {
                    return@withContext null
                }

                val mediaItemsSnapshot = player.mediaItems.mapNotNull { it.toPersistableMetadata() }
                if (mediaItemsSnapshot.isEmpty()) return@withContext null

                val currentMediaItemIndex = player.currentMediaItemIndex
                val currentPosition = player.currentPosition
                val persistQueue =
                    currentQueue.toPersistQueue(
                        title = queueTitle,
                        items = mediaItemsSnapshot,
                        mediaItemIndex = currentMediaItemIndex,
                        position = currentPosition,
                    )
                val persistPlayerState =
                    PersistPlayerState(
                        playWhenReady = player.playWhenReady,
                        repeatMode = player.repeatMode,
                        shuffleModeEnabled = player.shuffleModeEnabled,
                        volume = playerVolume.value,
                        currentPosition = currentPosition,
                        currentMediaItemIndex = currentMediaItemIndex,
                        playbackState = player.playbackState,
                    )

                persistQueue to persistPlayerState
            } ?: return

        withContext(Dispatchers.IO) {
            if (saveGeneration != persistentSaveGeneration.get()) return@withContext
            writePersistentObject(PERSISTENT_QUEUE_FILE, snapshot.first)
            if (saveGeneration != persistentSaveGeneration.get()) return@withContext
            writePersistentObject(PERSISTENT_PLAYER_STATE_FILE, snapshot.second)
        }
    }

    override fun onDestroy() {
        equalizerPlaybackController.detach(this)
        discordServiceStopping = true
        requestDiscordSync(
            reason = "service_destroy",
            force = true,
        )
        super.onDestroy()
        effectiveVolumeRampJob?.cancel()
        effectiveVolumeRampJob = null
        cancelCrossfade(resetVolume = false, resetPauseAtEnd = true)
        audioRouteRecoveryJob?.cancel()
        if (audioDeviceCallbackRegistered) {
            audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
            audioDeviceCallbackRegistered = false
        }
        unregisterBluetoothReceiver()
        unregisterMuteRecoveryObserver()
        try {
            scope.launch { stopTogetherInternal() }
        } catch (_: Exception) {
        }
        try {
            connectivityObserver.unregister()
        } catch (_: Exception) {
        }
        lyricsPreloadManager?.destroy()
        lyricsPreloadManager = null
        abandonAudioFocus()
        try {
            releaseAudioEffects()
        } catch (_: Exception) {
        }
        try {
            if (dataStore.get(PersistentQueueKey, true) && player.mediaItemCount > 0) {
                runBlocking {
                    saveQueueToDisk()
                }
            }
        } catch (_: Exception) {
        }
        try {
            mediaSession.release()
        } catch (_: Exception) {
        }
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {
        }
        try {
            localPlayer.removeListener(audioEffectPlayerListener)
            player.removeListener(this)
            player.removeListener(sleepTimer)
            initialBufferRecoveryJob?.cancel()
            player.release()
            castPlaybackRepository.releasePlayer(player)
        } catch (_: Exception) {
        }
        scopeJob.cancel()
    }

    override fun onBind(intent: Intent?): android.os.IBinder? {
        hasBoundClients = true
        cancelIdleStop()
        val result = super.onBind(intent) ?: binder
        if (player.mediaItemCount > 0 && player.currentMediaItem != null) {
            currentMediaMetadata.value = player.currentMetadata
            scope.launch {
                delay(50)
                updateNotification()
            }
        }
        return result
    }

    override fun onUnbind(intent: Intent?): Boolean {
        hasBoundClients = false
        scheduleStopIfIdle()
        return super.onUnbind(intent)
    }

    override fun onRebind(intent: Intent?) {
        hasBoundClients = true
        cancelIdleStop()
        super.onRebind(intent)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)

        val stopMusicOnTaskClearEnabled = dataStore.get(StopMusicOnTaskClearKey, false)

        try {
            val state = togetherSessionState.value
            val isHostSessionActive =
                state is moe.rukamori.archivetune.together.TogetherSessionState.Hosting ||
                    state is moe.rukamori.archivetune.together.TogetherSessionState.HostingOnline ||
                    (
                        state is moe.rukamori.archivetune.together.TogetherSessionState.Joined &&
                            state.role is moe.rukamori.archivetune.together.TogetherRole.Host
                    )

            val isPlaybackInactive = player.playbackState == Player.STATE_IDLE || player.mediaItemCount == 0

            if (shouldStopServiceOnTaskRemoved(stopMusicOnTaskClearEnabled, isHostSessionActive, isPlaybackInactive)) {
                if (stopMusicOnTaskClearEnabled) {
                    discordServiceStopping = true
                    requestDiscordSync(
                        reason = "task_removed_stop_music_on_task_clear",
                        force = true,
                    )
                    runCatching { stopAndClearPlayback(clearPersistentState = true) }
                    stopForegroundAndSelf()
                    return
                }

                if (isHostSessionActive && isPlaybackInactive) {
                    discordServiceStopping = true
                    requestDiscordSync(
                        reason = "task_removed_host_inactive",
                        force = true,
                    )
                    runCatching { scope.launch { stopTogetherInternal() } }
                    runCatching { togetherSessionState.value = moe.rukamori.archivetune.together.TogetherSessionState.Idle }
                    stopSelf()
                    return
                }
            }

            if (dataStore.get(PersistentQueueKey, true) && player.mediaItemCount > 0) {
                runBlocking { saveQueueToDisk() }
            }
        } catch (_: Exception) {
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    private fun handleMediaNotificationDismissed(intent: Intent) {
        val originalDeleteIntent =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(
                    EXTRA_MEDIA_NOTIFICATION_DELETE_INTENT,
                    PendingIntent::class.java,
                )
            } else {
                intent.getParcelableExtra(EXTRA_MEDIA_NOTIFICATION_DELETE_INTENT)
            }

        val isForeground = isAppInForeground()
        if (!player.isPlaying && !isForeground) {
            pausedPresenceGate = PausedPresenceGate.HiddenByNotificationDismiss
            requestDiscordSync(
                reason = "notification_dismissed_while_paused_background",
                force = true,
            )
        } else if (!player.isPlaying) {
            Timber.tag(DISCORD_SYNC_TAG).d(
                "notification dismissed while paused but app is foreground; keeping paused RPC visible",
            )
        }

        runCatching {
            originalDeleteIntent?.send()
        }.onFailure {
            Timber.tag(DISCORD_SYNC_TAG).w(it, "failed to forward original notification delete intent")
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action == ACTION_MEDIA_NOTIFICATION_DISMISSED) {
            handleMediaNotificationDismissed(intent)
            return START_NOT_STICKY
        }

        ensureStartedAsForeground()
        when (intent?.action) {
            "moe.rukamori.archivetune.WIDGET_PLAY_PAUSE" -> {
                if (player.isPlaying) player.pause() else player.play()
            }

            "moe.rukamori.archivetune.WIDGET_SKIP_NEXT" -> {
                if (player.hasNextMediaItem()) {
                    prepareForManualSkip()
                    player.seekToNext()
                    player.prepare()
                    player.play()
                }
            }

            "moe.rukamori.archivetune.WIDGET_SKIP_PREV" -> {
                if (player.hasPreviousMediaItem()) {
                    prepareForManualSkip()
                    player.seekToPrevious()
                    player.prepare()
                    player.play()
                }
            }
        }
        super.onStartCommand(intent, flags, startId)
        return START_NOT_STICKY
    }

    override fun onUpdateNotification(
        session: MediaSession,
        startInForegroundRequired: Boolean,
    ) {
        val shouldShowNotification =
            shouldShowPlaybackNotification(
                startInForegroundRequired = startInForegroundRequired,
                hasResumablePlayback = hasResumablePlaybackNotification(),
            )
        if (!shouldShowNotification) return

        ensureStartedAsForeground()
        runCatching { super.onUpdateNotification(session, true) }
            .onFailure { reportException(it) }
    }

    // ���─ Widget Support ──────────────────────────────���─────────────────────────────

    fun updateWidget() {
        widgetUpdater.update()
        widgetUpdater.updateProgressTracking()
    }

    inner class MusicBinder : Binder() {
        val service: MusicService
            get() = this@MusicService
    }

    companion object {
        internal fun shouldStopServiceOnTaskRemoved(
            stopMusicOnTaskClearEnabled: Boolean,
            isHostSessionActive: Boolean,
            isPlaybackInactive: Boolean,
        ): Boolean = (isHostSessionActive && isPlaybackInactive) || stopMusicOnTaskClearEnabled

        internal fun shouldShowPlaybackNotification(
            startInForegroundRequired: Boolean,
            hasResumablePlayback: Boolean,
        ): Boolean = startInForegroundRequired || hasResumablePlayback

        const val ROOT = "root"
        const val HOME = "home"
        const val HOME_QUICK_PICKS = "home_quick_picks"

        // Prefix for the dedicated player-cache key used by Tidal streams so their bytes never
        // collide with the YouTube stream cached under the bare media id.
        private const val TIDAL_CACHE_KEY_PREFIX = "tidal:"

        /** Apple Music license endpoint (Widevine L3 key exchange, verified live). */
        private const val APPLE_LICENSE_URL =
            "https://play.itunes.apple.com/WebObjects/MZPlay.woa/wa/acquireWebPlaybackLicense"
        const val HOME_FORGOTTEN_FAVORITES = "home_forgotten_favorites"
        const val HOME_KEEP_LISTENING = "home_keep_listening"
        const val HOME_SUGGESTED_SONGS = "home_suggested_songs"
        const val HOME_MIXES_AND_RADIOS = "home_mixes_and_radios"
        const val QUICK_PICKS = "quick_picks"
        const val RECENT = "recent"
        const val LIKED = "liked"
        const val DOWNLOADED = "downloaded"
        const val SONG = "song"
        const val ARTIST = "artist"
        const val ALBUM = "album"
        const val PLAYLIST = "playlist"
        const val SPOTIFY_PLAYLIST = "spotify_playlist"
        const val SPOTIFY_LIKED = "spotify_liked"
        const val ONLINE_PLAYLIST = "online_playlist"

        private const val TAG = "MusicService"
        private const val AUDIO_EFFECT_INITIALIZATION_MAX_ATTEMPTS = 4
        private const val AUDIO_EFFECT_INITIALIZATION_RETRY_DELAY_MS = 250L
        private const val INFINITE_QUEUE_MAX_BOOTSTRAP_PAGES = 3
        private const val DISCORD_SYNC_TAG = "DiscordSync"
        private const val DISCORD_HOLD_TIMEOUT_MS = 7_000L
        const val CHANNEL_ID = "music_channel_01"
        const val ACTION_MEDIA_NOTIFICATION_DISMISSED =
            "moe.rukamori.archivetune.action.MEDIA_NOTIFICATION_DISMISSED"
        const val EXTRA_MEDIA_NOTIFICATION_DELETE_INTENT =
            "moe.rukamori.archivetune.extra.MEDIA_NOTIFICATION_DELETE_INTENT"
        const val NOTIFICATION_ID = 888
        private const val TOGETHER_NOTIFICATION_CHANNEL_ID = "together_room_events"
        private const val TOGETHER_PARTICIPANT_NOTIFICATION_ID = 891
        private const val TOGETHER_INACTIVITY_NOTIFICATION_ID = 892
        private const val TOGETHER_HOST_INACTIVITY_TIMEOUT_MS = 10 * 60 * 1000L
        private const val TOGETHER_PUBLIC_CONNECT_TIMEOUT_MS = 20_000L
        const val ERROR_CODE_NO_STREAM = 1000001
        const val CHUNK_LENGTH = 8 * 1024 * 1024L
        val RETRYABLE_STREAM_RESPONSE_CODES = setOf(403, 404, 410, 416)
        private const val INITIAL_BUFFER_STALL_DELAY_MS = 15_000L
        private const val INITIAL_BUFFER_STALL_POSITION_MS = 5_000L
        const val PERSISTENT_QUEUE_FILE = "persistent_queue.data"
        const val PERSISTENT_AUTOMIX_FILE = "persistent_automix.data"
        const val PERSISTENT_PLAYER_STATE_FILE = "persistent_player_state.data"

        /**
         * How often the resume position is written while the service is alive.
         *
         * This only bounds how far back a restore lands after a process death, so 30s of accuracy
         * is plenty; it used to be 10s while playing, which meant six wakeups and six pairs of file
         * writes a minute for a queue that had not changed. Every event that changes the queue
         * saves it directly, so nothing is lost by ticking less often.
         */
        private val PERSISTENT_POSITION_SAVE_INTERVAL = 30.seconds
        const val MAX_CONSECUTIVE_ERR = 5
        const val AUDIO_ROUTE_CHANGE_DEBOUNCE_MS = 350L
        const val AUDIO_EFFECT_ROUTE_REBIND_DELAY_MS = 200L
        const val AUDIO_ROUTE_RECOVERY_MIN_INTERVAL_MS = 1_500L
        const val AUDIO_ROUTE_RECOVERY_RESUME_DELAY_MS = 150L
        const val DEVICE_MUTE_PLAYBACK_NOTICE_INTERVAL_MS = 1_200L
        // Delay before re-asserting the captured baseline volume after a source switch's
        // player.prepare() call. Long enough that the prepare pipeline has settled (and any
        // async state transitions complete within one frame after that) without being so long
        // that the user perceives a muted gap. Originally added in commit 9a224662b alongside
        // a broken import from the morideobfuscator submodule (where the constant never
        // existed) — moved back here so the reference resolves.
        const val SOURCE_SWITCH_VOLUME_REASSERT_MS = 250L
        const val MIN_AUDIO_FOCUS_VOLUME_FACTOR = 0.2f
        const val MIN_AUDIO_NORMALIZATION_FACTOR = 0.25f
        const val MAX_AUDIO_NORMALIZATION_FACTOR = 1.414f
        const val EFFECTIVE_VOLUME_RAMP_FRAME_MS = 16L
        const val EFFECTIVE_VOLUME_RAMP_UP_MS = 350L
        const val EFFECTIVE_VOLUME_RAMP_DOWN_MS = 180L
        const val EFFECTIVE_VOLUME_RAMP_MIN_DELTA = 0.015f
        const val MIN_CROSSFADE_DURATION_MS = 500L
        const val CROSSFADE_END_GUARD_MS = 150L
        const val CROSSFADE_PREPARE_AHEAD_MS = 30_000L
        const val CROSSFADE_READY_TIMEOUT_MS = 5_000L
        const val CROSSFADE_HANDOFF_BUFFER_MS = 5_000L
        const val CROSSFADE_AUDIO_ADVANCE_TIMEOUT_MS = 2_000L
        const val CROSSFADE_AUDIO_ADVANCE_POLL_MS = 80L
        const val CROSSFADE_MIN_BUFFER_BEFORE_START_MS = 5_000L
        const val CROSSFADE_MAX_BUFFER_BEFORE_START_MS = 12_500L
        const val PRIMARY_MIN_BUFFER_MS = 20_000
        const val PRIMARY_MAX_BUFFER_MS = 60_000
        // Reduced from 750ms / 2_500ms → 150ms / 750ms for faster song-start
        // latency. Combined with stream URL prefetching (see
        // prefetchNextMediaItemStream) this cuts perceived "tap to play" time
        // from ~1.5-3s down to ~150-400ms when the stream URL is cached.
        // 150ms is just above the ExoPlayer default of 250ms but small enough
        // that FLAC / YouTube streams start audibly within ~1 RTT of the
        // first TCP packet arriving. The after-rebuffer floor was lowered
        // from 1_000ms → 750ms for the same reason — the user is already
        // waiting on a rebuffer, so making them wait a full extra second on
        // top of the network RTT is excessive. `setPrioritizeTimeOverSizeThresholds(true)`
        // on the LoadControl means ExoPlayer will start playback as soon as
        // the time threshold is met, even if the size-based threshold hasn't
        // been reached — important for FLAC where the bitrate is 4-6× higher
        // than AAC, so the same byte count represents far less playback time.
        const val PRIMARY_BUFFER_FOR_PLAYBACK_MS = 150
        const val PRIMARY_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 750
        const val CROSSFADE_MIN_BUFFER_MS = 15_000
        const val CROSSFADE_MAX_BUFFER_MS = 45_000
        const val CROSSFADE_FRAME_MS = 32L
        const val MIN_AUDIBLE_EFFECTIVE_VOLUME = 0.01f
        const val STUCK_MUTED_VOLUME_EPSILON = 0.001f
        /**
         * How often the stuck-mute watchdog re-checks the player volume during active playback.
         *
         * This is only a backstop: ensureAudiblePlaybackVolume already runs from onEvents and from
         * both source-switch paths, so any state change that could mute the player is covered
         * event-driven. The watchdog exists for a mute that arrives with no player event at all.
         *
         * It was 2s, which is 30 CPU wakeups a minute for a check that almost always does nothing,
         * and background audio is exactly where that stops the SoC reaching deep idle between
         * buffer fills. At 15s the worst-case recovery for an already-rare bug goes from 2s to 15s
         * and the wakeups drop by 7.5x.
         */
        const val AUDIBLE_PLAYBACK_VOLUME_CHECK_MS = 15_000L

        /**
         * TTL for cached Qobuz/Tidal DirectStreams. Set to 5 minutes to match the
         * typical signed-URL lifetime on those providers (4-6 minutes). After this,
         * a cached stream is considered stale and re-resolved from the source.
         */
        private const val DIRECT_STREAM_CACHE_TTL_MS = 5L * 60L * 1000L
    }
}
