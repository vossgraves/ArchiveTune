/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.request.CachePolicy
import coil3.request.allowHardware
import coil3.request.crossfade
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.canvas.ArchiveTuneCanvas
import moe.rukamori.archivetune.constants.*
import moe.rukamori.archivetune.extensions.*
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.YouTubeLocale
import moe.rukamori.archivetune.kugou.KuGou
import moe.rukamori.archivetune.lastfm.LastFM
import moe.rukamori.archivetune.lyrics.JapaneseLanguagePackManager
import moe.rukamori.archivetune.morideobfuscator.MoriCipherConfig
import moe.rukamori.archivetune.morideobfuscator.MoriCipherRuntime
import moe.rukamori.archivetune.paxsenix.PaxsenixLyrics
import moe.rukamori.archivetune.scrobbling.LastFmServiceConfig
import moe.rukamori.archivetune.storage.StorageFolderKind
import moe.rukamori.archivetune.storage.StorageLocationRepository
import moe.rukamori.archivetune.tidal.TidalAudioProvider
import moe.rukamori.archivetune.tidal.TidalInstanceHealthManager
import moe.rukamori.archivetune.qobuz.QobuzAudioProvider
import moe.rukamori.archivetune.ui.player.CanvasArtworkPlaybackCache
import moe.rukamori.archivetune.ui.screens.settings.ThemePalettes
import moe.rukamori.archivetune.ui.theme.ThemeSeedPalette
import moe.rukamori.archivetune.ui.theme.ThemeSeedPaletteCodec
import moe.rukamori.archivetune.utils.MoriCipherUpdateScheduler
import moe.rukamori.archivetune.utils.PoolAccountManager
import moe.rukamori.archivetune.utils.PreferenceStore
import moe.rukamori.archivetune.utils.ProxyUtils
import moe.rukamori.archivetune.utils.YTPlayerUtils
import moe.rukamori.archivetune.utils.clearPlaybackAuthSession
import moe.rukamori.archivetune.utils.clearPlaybackWebAuthSession
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import moe.rukamori.archivetune.utils.potoken.BotGuardTokenGenerator
import moe.rukamori.archivetune.utils.reportException
import moe.rukamori.archivetune.utils.toPlaybackAuthState
import okhttp3.Dns
import timber.log.Timber
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.net.Proxy
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.system.exitProcess

@HiltAndroidApp
class App :
    Application(),
    SingletonImageLoader.Factory {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Volatile private var isInitialized = false
    private val didRunImageCacheTrim = AtomicBoolean(false)

    private fun currentProcessName(): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            val pid = android.os.Process.myPid()
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            activityManager
                ?.runningAppProcesses
                ?.firstOrNull { it.pid == pid }
                ?.processName
        }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()
        instance = this
        if (currentProcessName()?.endsWith(":crash") == true) {
            Timber.plant(Timber.DebugTree())
            return
        }
        BotGuardTokenGenerator.initialize(this)
        PreferenceStore.start(this)
        JapaneseLanguagePackManager.initialize(this)
        Timber.plant(Timber.DebugTree())
        try {
            Timber.plant(
                moe.rukamori.archivetune.utils
                    .GlobalLogTree(),
            )
        } catch (_: Exception) {
        }

        initializeCriticalSync()
        initializeDeferredAsync()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // WebView cleanup happens automatically on process death
    }

    private fun initializeCriticalSync() {
        MoriCipherRuntime.initialize(
            MoriCipherConfig(
                cacheDirectory = File(noBackupFilesDir, "mori_cipher"),
                proxyProvider = { YouTube.streamProxy },
            ),
        )
        MoriCipherUpdateScheduler.schedule(this)
        // PRDownloader is the file downloader used by PRDownloaderDataSource
        // (the upstream HTTP fetcher inside Media3's download CacheDataSource
        // chain). Initialized once at app start with tuned timeouts so
        // downloads benefit from PRDownloader's pause/resume + retry support
        // without paying init cost on the first download. Replaces Ketch —
        // see PRDownloaderDataSource.kt for the rationale.
        //
        // Tuned for parallel-throughput: PRDownloader internally caps each
        // download at 1 concurrent connection (it's a sequential fetcher),
        // but it can run many downloads in parallel — the OkHttp dispatcher
        // inside PRDownloader honors `setUserAgent` for proper HTTP/2
        // connection reuse, and the read/connect timeouts are generous
        // enough for large FLAC files on slow links.
        runCatching {
            val config = com.downloader.PRDownloaderConfig.newBuilder()
                .setReadTimeout(60_000)
                .setConnectTimeout(15_000)
                .setUserAgent("ArchiveTune/${BuildConfig.VERSION_NAME}")
                .build()
            com.downloader.PRDownloader.initialize(this, config)
        }
        CanvasArtworkPlaybackCache.init(this)
        ArchiveTuneCanvas.initialize(BuildConfig.CANVAS_BEARER_TOKEN)
        PaxsenixLyrics.setUserAgent("ArchiveTune", BuildConfig.VERSION_NAME)

        val locale = Locale.getDefault()
        val languageTag = locale.toLanguageTag().replace("-Hant", "")
        YouTube.locale =
            YouTubeLocale(
                gl = locale.country.takeIf { it in CountryCodeToName } ?: "US",
                hl =
                    locale.language.takeIf { it in LanguageCodeToName }
                        ?: languageTag.takeIf { it in LanguageCodeToName }
                        ?: "en",
            )
        if (languageTag == "zh-TW") {
            KuGou.useTraditionalChinese = true
        }
        LastFM.initialize(
            apiKey = BuildConfig.LASTFM_API_KEY,
            secret = BuildConfig.LASTFM_SECRET,
        )
    }

    private fun initializeDeferredAsync() {
        applicationScope.launch(Dispatchers.IO) {
            MoriCipherRuntime
                .refresh(force = false)
                .onFailure { Timber.w(it, "Mori cipher background initialization failed") }
        }
        applicationScope.launch(Dispatchers.IO) {
            try {
                val prefs = dataStore.data.first()

                prefs[ContentCountryKey]?.takeIf { it != SYSTEM_DEFAULT }?.let { country ->
                    YouTube.locale = YouTube.locale.copy(gl = country)
                }
                prefs[ContentLanguageKey]?.takeIf { it != SYSTEM_DEFAULT }?.let { lang ->
                    YouTube.locale = YouTube.locale.copy(hl = lang)
                }
                // Region spoofer from Internet Settings — applied AFTER ContentCountryKey so
                // the Internet/region setting wins when both are set. SYSTEM_DEFAULT falls back
                // to whatever the device locale or ContentCountryKey resolved to above.
                prefs[YouTubeMusicRegionKey]?.takeIf { it != SYSTEM_DEFAULT }?.let { country ->
                    YouTube.locale = YouTube.locale.copy(gl = country)
                }

                LastFmServiceConfig.fromPreferences(prefs).apply(prefs[LastFMSessionKey])

                ProxyUtils.applyYouTubeProxy(
                    enabled = prefs[ProxyEnabledKey] == true,
                    type = prefs[ProxyTypeKey].toEnum(defaultValue = Proxy.Type.HTTP),
                    host = prefs[ProxyHostKey],
                    port = prefs[ProxyPortKey],
                    username = prefs[ProxyUsernameKey],
                    password = prefs[ProxyPasswordKey],
                )
                YouTube.streamBypassProxy = YouTube.proxy != null && prefs[StreamBypassProxyKey] == true

                if (prefs[IpRotationEnabledKey] == true) {
                    try {
                        YouTube.enableIpRotation()
                    } catch (e: Exception) {
                        reportException(e)
                    }
                }

                if (prefs[UseLoginForBrowse] != false) {
                    YouTube.useLoginForBrowse = true
                }

                // Apply random theme on startup if enabled
                if (prefs[RandomThemeOnStartupKey] == true) {
                    val randomPalette = ThemePalettes.generateRandomPalette()
                    val seedPalette =
                        ThemeSeedPalette(
                            primary = randomPalette.primary,
                            secondary = randomPalette.secondary,
                            tertiary = randomPalette.tertiary,
                            neutral = randomPalette.neutral,
                        )
                    val encodedPalette = ThemeSeedPaletteCodec.encodeForPreference(seedPalette, "Random")
                    dataStore.edit { settings ->
                        settings[CustomThemeColorKey] = encodedPalette
                    }
                }

                isInitialized = true
            } catch (e: Exception) {
                Timber.e(e, "Error during deferred initialization")
                reportException(e)
            }
        }

        // Subtly verify the public Tidal instances on startup so dead / preview-only (unsubscribed)
        // mirrors are pruned before the user plays anything. The scan is staggered (one instance at
        // a time with a small delay) so it never competes with critical startup work, and its
        // results are cached for the settings screen and future launches.
        applicationScope.launch(Dispatchers.IO) {
            try {
                if (dataStore.get(TidalEnabledKey, true)) {
                    // Restore the last probe track so the very first scan can classify preview vs full.
                    dataStore.get(TidalLastProbeTrackKey)?.takeIf { it.isNotBlank() }?.let {
                        if (TidalAudioProvider.lastResolvedTrackId.isNullOrBlank()) {
                            TidalAudioProvider.seedProbeTrack(it)
                        }
                    }
                    // When a community Source Pool URL is baked in, auto-discover its
                    // health-checked instances on startup so playback is seamless without any
                    // manual setup. With no provider configured this stays a cheap re-verify.
                    val autoDiscover = BuildConfig.SOURCE_PROVIDER_URL.isNotBlank()
                    TidalInstanceHealthManager.refresh(this@App, includeDiscovery = autoDiscover, staggered = true)
                }
            } catch (e: Exception) {
                Timber.w(e, "Tidal instance startup health scan failed")
            }
        }

        // Pull shared premium ACCOUNTS (real subscriber tokens) from the community Source Pool so the
        // resolvers can stream full-quality FLAC directly against the official Tidal/Qobuz APIs — no
        // self-hosted restream instance required. Loads the persisted cache first (instant), then
        // refreshes over the network. Disabled automatically when no Source Pool URL is baked in.
        applicationScope.launch(Dispatchers.IO) {
            try {
                if (PoolAccountManager.isEnabled) {
                    PoolAccountManager.loadCached(this@App)
                    PoolAccountManager.refresh(this@App)
                }
            } catch (e: Exception) {
                Timber.w(e, "Pool account startup refresh failed")
            }
        }

        // Restore the Qobuz health-probe track so the settings "Test" action can distinguish a
        // fully-working instance from a preview-only (unsubscribed) one on the first probe.
        applicationScope.launch(Dispatchers.IO) {
            try {
                if (dataStore.get(QobuzEnabledKey, false)) {
                    dataStore.get(QobuzLastProbeTrackKey)?.takeIf { it.isNotBlank() }?.let {
                        QobuzAudioProvider.seedProbeTrack(it)
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "Qobuz probe-track restore failed")
            }
        }

        applicationScope.launch(Dispatchers.IO) {
            dataStore.data
                .map {
                    Triple(
                        it[EnableDnsOverHttpsKey] ?: false,
                        it[DnsOverHttpsProviderKey] ?: "Cloudflare",
                        it[stringPreferencesKey("customDnsUrl")] ?: "https://",
                    )
                }.distinctUntilChanged()
                .collect { (enabled, provider, customUrl) ->
                    if (enabled) {
                        val dnsProviderUrls =
                            mapOf(
                                "Google" to "https://dns.google/dns-query",
                                "Cloudflare" to "https://cloudflare-dns.com/dns-query",
                                "AdGuard" to "https://dns.adguard.com/dns-query",
                                "Quad9" to "https://dns.quad9.net/dns-query",
                            )
                        val url = if (provider == "Custom") customUrl else dnsProviderUrls[provider]
                        if (!url.isNullOrBlank() && url.startsWith("https://")) {
                            runCatching {
                                YouTube.dns = YouTube.createDnsOverHttps(url)
                            }
                        } else {
                            YouTube.dns = Dns.SYSTEM
                        }
                    } else {
                        YouTube.dns = Dns.SYSTEM
                    }
                }
        }

        applicationScope.launch(Dispatchers.IO) {
            dataStore.data
                .map { it.toPlaybackAuthState() }
                .distinctUntilChanged()
                .collect { authState ->
                    val previousFingerprint = YouTube.currentPlaybackAuthState().fingerprint
                    YouTube.authState = authState
                    if (previousFingerprint != authState.fingerprint) {
                        YTPlayerUtils.clearPlaybackAuthCaches()
                        val sessionId = authState.sessionId
                        if (!sessionId.isNullOrBlank()) {
                            BotGuardTokenGenerator.preWarm(sessionId)
                        }
                    }
                }
        }

        applicationScope.launch(Dispatchers.IO) {
            dataStore.data
                .map { it.toPlaybackAuthState().visitorData }
                .distinctUntilChanged()
                .collect { visitorData ->
                    if (!visitorData.isNullOrBlank()) return@collect
                    YouTube
                        .visitorData()
                        .onFailure {
                            reportException(it)
                        }.getOrNull()
                        ?.also { newVisitorData ->
                            dataStore.edit { settings ->
                                settings[VisitorDataKey] = newVisitorData
                            }
                        }
                }
        }

        try {
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    val sw = StringWriter()
                    val pw = PrintWriter(sw)
                    throwable.printStackTrace(pw)
                    val stack = sw.toString()

                    val intent =
                        Intent(this@App, DebugActivity::class.java).apply {
                            putExtra(DebugActivity.EXTRA_STACK_TRACE, stack)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        }
                    startActivity(intent)
                    try {
                        Thread.sleep(100)
                    } catch (_: InterruptedException) {
                    }
                } catch (e: Exception) {
                    reportException(e)
                } finally {
                    android.os.Process.killProcess(android.os.Process.myPid())
                    exitProcess(2)
                }
            }
        } catch (e: Exception) {
            reportException(e)
        }
        applicationScope.launch(Dispatchers.IO) {
            dataStore.data
                .map { prefs ->
                    LastFmServiceConfig.fromPreferences(prefs) to prefs[LastFMSessionKey]
                }.distinctUntilChanged()
                .collect { (serviceConfig, sessionKey) ->
                    serviceConfig.apply(sessionKey)
                }
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val smartTrimmer = dataStore[SmartTrimmerKey] ?: false
        val imageCacheConfig = resolveImageDiskCacheConfig(dataStore[MaxImageCacheSizeKey])

        val diskCache =
            DiskCache
                .Builder()
                .directory(StorageLocationRepository.cacheDirectory(this, StorageFolderKind.IMAGE_CACHE))
                .maxSizeBytes(imageCacheConfig.maxSizeBytes)
                .build()

        if (smartTrimmer && imageCacheConfig.policy == CachePolicy.ENABLED && didRunImageCacheTrim.compareAndSet(false, true)) {
            applicationScope.launch(Dispatchers.IO) { trimImageDiskCache(diskCache) }
        }

        return ImageLoader
            .Builder(this)
            .components {
                // Resolve `tgthumb://<fileId>` artwork models through TDLib (Telegram source).
                add(moe.rukamori.archivetune.telegram.TelegramThumbnailFetcher.Factory())
            }.crossfade(true)
            .allowHardware(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            .diskCache(diskCache)
            .diskCachePolicy(imageCacheConfig.policy)
            .build()
    }

    private fun trimImageDiskCache(diskCache: DiskCache) {
        try {
            val limitBytes = diskCache.maxSize
            if (limitBytes <= 0L || limitBytes == Long.MAX_VALUE) return

            val dir = java.io.File(diskCache.directory.toString())
            if (!dir.exists()) return

            val files =
                dir
                    .walkTopDown()
                    .filter { it.isFile }
                    .sortedBy { it.lastModified() }
                    .toList()
            var currentSize = files.sumOf { it.length() }
            if (currentSize <= limitBytes) return

            for (file in files) {
                if (currentSize <= limitBytes) break
                val size = file.length()
                if (runCatching { file.delete() }.getOrDefault(false)) currentSize -= size
            }
        } catch (_: Exception) {
        }
    }

    companion object {

        lateinit var instance: App
            private set

        fun forgetAccount(
            context: Context,
            clearWebAuthSession: Boolean = true,
        ) {
            if (clearWebAuthSession) {
                clearPlaybackWebAuthSession(context)
            }
            CoroutineScope(Dispatchers.IO).launch {
                context.dataStore.edit { settings ->
                    settings.clearPlaybackAuthSession()
                }
            }
        }
    }
}

internal data class ImageDiskCacheConfig(
    val policy: CachePolicy,
    val maxSizeBytes: Long,
)

internal fun resolveImageDiskCacheConfig(maxImageCacheSizeMb: Int?): ImageDiskCacheConfig {
    val sizeMb = maxImageCacheSizeMb ?: 512
    if (sizeMb == 0) return ImageDiskCacheConfig(policy = CachePolicy.DISABLED, maxSizeBytes = 1L)
    if (sizeMb < 0) return ImageDiskCacheConfig(policy = CachePolicy.ENABLED, maxSizeBytes = Long.MAX_VALUE)
    val bytesPerMb = 1024L * 1024L
    val safeSizeMb = sizeMb.toLong().coerceAtMost(Long.MAX_VALUE / bytesPerMb)
    return ImageDiskCacheConfig(policy = CachePolicy.ENABLED, maxSizeBytes = safeSizeMb * bytesPerMb)
}
