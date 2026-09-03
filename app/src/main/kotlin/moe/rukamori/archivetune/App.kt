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
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.allowHardware
import coil3.request.crossfade
import moe.rukamori.archivetune.utils.isLowRamDevice
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
import moe.rukamori.archivetune.constants.*
import moe.rukamori.archivetune.deezer.DeezerAudioProvider
import moe.rukamori.archivetune.extensions.*
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.YouTubeLocale
import moe.rukamori.archivetune.kugou.KuGou
import moe.rukamori.archivetune.lastfm.LastFM
import moe.rukamori.archivetune.lyrics.JapaneseLanguagePackManager
import moe.rukamori.archivetune.canvas.AppleMusicProvider
import moe.rukamori.archivetune.canvas.SpotifyCanvasProvider
import moe.rukamori.archivetune.morideobfuscator.ytdlp.YtDlpJavaScriptRuntime
import moe.rukamori.archivetune.paxsenix.PaxsenixLyrics
import moe.rukamori.archivetune.scrobbling.LastFmServiceConfig
import moe.rukamori.archivetune.spotify.Spotify
import moe.rukamori.archivetune.spotify.SpotifyLibraryRepository
import moe.rukamori.archivetune.storage.StorageFolderKind
import moe.rukamori.archivetune.storage.StorageLocationRepository
import moe.rukamori.archivetune.tidal.TidalAudioProvider
import moe.rukamori.archivetune.tidal.TidalInstanceHealthManager
import moe.rukamori.archivetune.qobuz.QobuzAudioProvider
import moe.rukamori.archivetune.ui.player.CanvasArtworkPlaybackCache
import moe.rukamori.archivetune.ui.screens.settings.ThemePalettes
import moe.rukamori.archivetune.ui.theme.ThemeSeedPalette
import moe.rukamori.archivetune.ui.theme.ThemeSeedPaletteCodec
import moe.rukamori.archivetune.utils.CanvasResolverEndpoints
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
import okhttp3.ConnectionPool
import okhttp3.Dns
import okhttp3.OkHttpClient
import timber.log.Timber
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.net.Proxy
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.system.exitProcess

@HiltAndroidApp
class App :
    Application(),
    SingletonImageLoader.Factory {

    /**
     * Injected only so the canvas provider can mint a Spotify token on demand — see
     * [SpotifyLibraryRepository.ensureAccessToken].
     */
    @Inject
    lateinit var spotifyLibraryRepository: SpotifyLibraryRepository

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Volatile private var isInitialized = false

    // Latest Apple Music account tokens (see collector wired to AppleMusicProvider below).
    @Volatile private var appleMusicDevTokenCache: String = ""
    @Volatile private var appleMusicMediaUserTokenCache: String = ""

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
        YtDlpJavaScriptRuntime.initialize(this)
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
        runCatching {
            val config = com.downloader.PRDownloaderConfig.newBuilder()
                .setReadTimeout(60_000)
                .setConnectTimeout(15_000)
                .setUserAgent("ArchiveTune/${BuildConfig.VERSION_NAME}")
                .build()
            com.downloader.PRDownloader.initialize(this, config)
        }
        CanvasArtworkPlaybackCache.init(this)
        PaxsenixLyrics.setUserAgent("ArchiveTune", BuildConfig.VERSION_NAME)
        // Route PaxsenixLyrics diagnostic logs through GlobalLog so they show up
        // in the in-app logcat viewer with the proper tag, instead of going to
        // System.err (which Android redirects to logcat one line at a time as
        // `W/System.err`, with synchronized I/O that causes contention during
        // parallel lyrics prefetch).
        PaxsenixLyrics.logger = { message ->
            moe.rukamori.archivetune.utils.GlobalLog.append(
                android.util.Log.INFO,
                "PaxsenixLyrics",
                message,
            )
        }

        // Route AppleMusicProvider (canvas) diagnostic logs through GlobalLog
        // too — same rationale as PaxsenixLyrics.logger above. Previously the
        // canvas module used `println(...)` which Android redirects to logcat
        // as `I/System.out:` with no tag/level, bypassing the in-app log viewer.
        AppleMusicProvider.logger = { level, tag, message ->
            moe.rukamori.archivetune.utils.GlobalLog.append(level, tag, message)
        }

        // Feed the user's own Apple Music account tokens (pasted on the Apple
        // Music settings page) into the canvas module's AMP requests: their
        // dev JWT replaces the scraped web token, their media-user-token rides
        // along as the Media-User-Token header. DataStore lives in the app
        // layer; a collector keeps cached values current and the providers
        // hand back the latest without ever blocking the caller's thread.
        applicationScope.launch(Dispatchers.IO) {
            var lastMedia = ""
            dataStore.data.collect { prefs ->
                val newDev = prefs[AppleMusicDevTokenKey]?.trim().orEmpty()
                val newMedia = prefs[AppleMusicMediaUserTokenKey]?.trim().orEmpty()
                if (newMedia != lastMedia) {
                    lastMedia = newMedia
                    AppleMusicProvider.clearStorefrontCache()
                }
                appleMusicDevTokenCache = newDev
                appleMusicMediaUserTokenCache = newMedia
            }
        }
        AppleMusicProvider.devTokenProvider = {
            appleMusicDevTokenCache.ifBlank { null }
        }
        AppleMusicProvider.mediaUserTokenProvider = {
            appleMusicMediaUserTokenCache.ifBlank { null }
                // Fall back to shared Apple Music accounts contributed to the Source Pool when
                // the user hasn't signed in personally — enables lyrics/canvas/playback for
                // pool users without their own login.
                ?: PoolAccountManager.appleMusicAccounts().firstOrNull()?.mediaUserToken
        }

        // Spotify Canvas. The canvas module deliberately has no dependency on the
        // app's Spotify code, so it takes the access token and the song → Spotify
        // track mapping as injected callbacks. Both yield null when the user has
        // no Spotify session, in which case the provider falls back to the
        // kouzu.in resolver on its own.
        SpotifyCanvasProvider.logger = { message ->
            moe.rukamori.archivetune.utils.GlobalLog.append(
                android.util.Log.INFO,
                "SpotifyCanvas",
                message,
            )
        }
        // Mint/refresh on demand rather than reading the `Spotify.accessToken` global:
        // that global is only set as a side effect of an earlier Spotify library call, so
        // on a fresh launch a connected user still had no token here and the official
        // Canvas endpoint was skipped entirely.
        SpotifyCanvasProvider.tokenProvider = { spotifyLibraryRepository.ensureAccessToken() }
        SpotifyCanvasProvider.trackUriResolver = { _, title, artist ->
            // Same reason: identifying the track on Spotify needs the session too.
            spotifyLibraryRepository.ensureAccessToken()
            resolveSpotifyTrackUri(title, artist)
        }
        SpotifyCanvasProvider.extraResolverEndpointsProvider = {
            CanvasResolverEndpoints.parse(dataStore.get(CanvasResolverEndpointsKey, ""))
        }

        // Pre-warm the Apple Music web player JWT on startup so the first
        // lyrics lookup and canvas resolution don't pay the extra ~300ms scrape
        // latency. The refresh is throttled and mutex-guarded inside the
        // provider, so this is safe to call fire-and-forget.
        applicationScope.launch(Dispatchers.IO) {
            runCatching {
                AppleMusicProvider.refreshToken()
                PaxsenixLyrics.refreshAmpToken()
            }
        }

        runCatching { moe.rukamori.archivetune.telegram.TelegramClient.ensureStarted(this) }

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
            try {
                val prefs = dataStore.data.first()

                prefs[ContentCountryKey]?.takeIf { it != SYSTEM_DEFAULT }?.let { country ->
                    YouTube.locale = YouTube.locale.copy(gl = country)
                }
                prefs[ContentLanguageKey]?.takeIf { it != SYSTEM_DEFAULT }?.let { lang ->
                    YouTube.locale = YouTube.locale.copy(hl = lang)
                }
                // Restore the YouTube Music region override. BOTH halves have to come back: the
                // `gl` locale override *and* `regionSpooferActive`, which is what forces the
                // region-sensitive endpoints (home, search, charts, explore, moods, new releases)
                // to go out anonymously so `gl` is authoritative. Restoring only `gl` — as this
                // used to — meant spoofing silently stopped working after the very first restart,
                // including the automatic one that picking a region triggers: the account context
                // came back and YouTube went on serving the account's home country.
                prefs[YouTubeMusicRegionKey]?.takeIf { it != SYSTEM_DEFAULT }?.let { regionValue ->
                    YouTube.locale = YouTube.locale.copy(gl = regionValue)
                    YouTube.regionSpooferActive = true
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

                // Re-install the rotating proxy pool when the user left IP rotation on. Without
                // this the toggle in Internet Settings read as ON after every restart while no
                // proxy was actually installed, so rotation appeared to do nothing. Fetching and
                // validating the pool is network-bound, so it runs here in the deferred IO block
                // and not on the startup critical path. A pool that validates to nothing leaves
                // rotation off; requests then go out directly, exactly as before.
                if (prefs[IpRotationEnabledKey] == true) {
                    runCatching { YouTube.enableIpRotation() }
                        .onFailure { Timber.w(it, "IP rotation restore failed") }
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

        // Mirrors the manually signed-in Deezer account into the provider. A collector rather than a
        // one-shot read so signing in or out takes effect immediately, and so the value survives the
        // pool refresh that replaces the pooled account cache.
        applicationScope.launch(Dispatchers.IO) {
            dataStore.data
                .map { (it[DeezerArlKey] ?: "") to (it[DeezerAccountPremiumKey] ?: false) }
                .distinctUntilChanged()
                .collect { (arl, premium) ->
                    DeezerAudioProvider.setManualArl(arl, premium)
                }
        }

        applicationScope.launch(Dispatchers.IO) {
            dataStore.data
                .map { it[DeezerProxyModeKey] ?: DeezerProxyMode.AUTO.name }
                .distinctUntilChanged()
                .collect { modeName ->
                    val mode = runCatching { DeezerProxyMode.valueOf(modeName) }.getOrDefault(DeezerProxyMode.AUTO)
                    DeezerAudioProvider.setProxyMode(mode)
                }
        }

        // Observe the user-configured Paxsenix API key + endpoint and apply
        // them to PaxsenixLyrics. When the user changes the key in Settings
        // → Lyrics → Providers → Paxsenix API key, this collector fires and
        // PaxsenixLyrics.setApiKey()/setEndpoint() take effect immediately
        // (the Ktor client reads these vars at request time via
        // defaultRequest {}).
        applicationScope.launch(Dispatchers.IO) {
            dataStore.data
                .map { (it[PaxsenixApiKeyKey] ?: "") to (it[PaxsenixEndpointKey] ?: "") }
                .distinctUntilChanged()
                .collect { (key, endpoint) ->
                    PaxsenixLyrics.setApiKey(key)
                    PaxsenixLyrics.setEndpoint(normalizePaxsenixEndpoint(endpoint))
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
        val lowRam = isLowRamDevice()

        val diskCache =
            DiskCache
                .Builder()
                .directory(StorageLocationRepository.cacheDirectory(this, StorageFolderKind.IMAGE_CACHE))
                .maxSizeBytes(imageCacheConfig.maxSizeBytes)
                .build()

        if (smartTrimmer && imageCacheConfig.policy == CachePolicy.ENABLED && didRunImageCacheTrim.compareAndSet(false, true)) {
            applicationScope.launch(Dispatchers.IO) { trimImageDiskCache(diskCache) }
        }

        // Tuned OkHttp client dedicated to image fetching. Defaults in Coil 3
        // / OkHttp use a small connection pool (5 idle, 5 min keepalive) and
        // 10s connect/read/write timeouts — fine for a single image, but the
        // home feed fires 20+ thumbnail requests in parallel on cold start,
        // which exhausts the pool and serialises behind connect timeouts.
        //
        // This config:
        //  * Raises the pool to 20 idle / 5 min keepalive so all parallel
        //    thumbnail fetches reuse kept-alive sockets (no extra TLS
        //    handshakes after the first hit to lh3.googleusercontent.com /
        //    i.ytimg.com / mosaic.scdn.co).
        //  * Drops connect timeout to 6s (don't hang the UI for 10s on a
        //    dead CDN edge — let the next retry bucket kick in fast).
        //  * Keeps read/write at 10s (large high-res thumbnails can still
        //    take a moment on slow networks; we don't want to abort them).
        //  * Enables retryOnConnectionFailure + followRedirects so CDN
        //    302s (e.g. googleusercontent -> ggpht) resolve transparently.
        //
        // Combined with explicit `.size()` on every AsyncImage call (so
        // Coil requests the smallest bucket the server offers instead of
        // pulling maxresdefault for a 56dp tile), this makes thumbnails
        // load near-instantly after the first cache miss.
        val imageHttpClient =
            OkHttpClient
                .Builder()
                .connectionPool(ConnectionPool(20, 5, TimeUnit.MINUTES))
                .connectTimeout(6, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()

        return ImageLoader
            .Builder(this)
            .components {
                add(moe.rukamori.archivetune.telegram.TelegramThumbnailFetcher.Factory())
                add(OkHttpNetworkFetcherFactory(imageHttpClient))
            }
            .crossfade(!lowRam)
            .allowHardware(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            .memoryCache {
                MemoryCache
                    .Builder()
                    .maxSizePercent(this@App, if (lowRam) 0.15 else 0.25)
                    .build()
            }.memoryCachePolicy(CachePolicy.ENABLED)
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

/**
 * Per-provider sub-paths served by the Paxsenix ("Lyrically") API, longest first
 * so `apple-music/cache/cleanup` is stripped before `apple-music/cache`.
 *
 * [PaxsenixLyrics] appends the sub-path for whichever provider is being queried,
 * so the configured endpoint has to be the *service root*.
 */
private val PAXSENIX_PROVIDER_PATHS =
    listOf(
        "apple-music/cache/cleanup",
        "apple-music/lyrics",
        "apple-music/cache",
        "musixmatch/lyrics",
        "spotify/lyrics",
        "spotify/search",
        "spotify/home",
        "netease/lyrics",
        "netease/search",
        "youtube/lyrics",
        "youtube/search",
        "deezer/lyrics",
        "genius/lyrics",
        "kugou/lyrics",
        "kugou/search",
        "qq/lyrics",
        "qq/search",
        "api/stats",
        "playground",
        "docs",
    )

/**
 * Normalises a user-supplied Paxsenix endpoint down to the service root.
 *
 * The API documents a *separate* URL per lyrics provider
 * (`…/apple-music/lyrics`, `…/spotify/lyrics`, and so on), so users reasonably
 * paste one of those into the endpoint field. [PaxsenixLyrics] then appends its
 * own provider sub-path, producing `…/apple-music/lyrics/spotify/lyrics` and a
 * 404 for every request. Stripping any known provider path (plus query string
 * and fragment) means pasting any documented URL resolves to the same working
 * root, and a blank value still falls through to the built-in default.
 */
private fun normalizePaxsenixEndpoint(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return ""

    var url = trimmed.substringBefore('?').substringBefore('#').trimEnd('/')
    for (path in PAXSENIX_PROVIDER_PATHS) {
        if (url.endsWith("/$path", ignoreCase = true)) {
            url = url.removeRange(url.length - path.length - 1, url.length)
            break
        }
        if (url.endsWith(path, ignoreCase = true)) {
            url = url.removeRange(url.length - path.length, url.length)
            break
        }
    }
    return url.trimEnd('/').ifBlank { "" }
}

/**
 * Maps a now-playing song to its `spotify:track:<id>` URI so [SpotifyCanvasProvider]
 * can ask Spotify for the track's Canvas.
 *
 * Returns null when the user has no Spotify session, when there is nothing to
 * search on, or when no result looks like the same song. The artist check matters:
 * an unrelated track's canvas is worse than no canvas, and the search is a plain
 * text match that will happily return a cover or a remix.
 *
 * Callers are rate-limited by [SpotifyCanvasProvider]'s own one-hour result cache,
 * so this runs at most once per song per hour.
 */
private suspend fun resolveSpotifyTrackUri(
    title: String?,
    artist: String?,
): String? {
    if (!Spotify.isAuthenticated()) return null
    val cleanTitle = title?.trim().orEmpty()
    val cleanArtist = artist?.trim().orEmpty()
    if (cleanTitle.isBlank()) return null

    val query = listOf(cleanTitle, cleanArtist).filter { it.isNotBlank() }.joinToString(" ")
    val tracks =
        Spotify
            .search(query = query, types = listOf("track"), limit = 5)
            .getOrNull()
            ?.tracks
            ?.items
            .orEmpty()
    if (tracks.isEmpty()) return null

    fun matchesTitle(name: String): Boolean =
        name.equals(cleanTitle, ignoreCase = true) ||
            name.contains(cleanTitle, ignoreCase = true) ||
            cleanTitle.contains(name, ignoreCase = true)

    fun matchesArtist(names: List<String>): Boolean =
        cleanArtist.isBlank() ||
            names.any { candidate ->
                candidate.isNotBlank() &&
                    (
                        candidate.equals(cleanArtist, ignoreCase = true) ||
                            candidate.contains(cleanArtist, ignoreCase = true) ||
                            cleanArtist.contains(candidate, ignoreCase = true)
                    )
            }

    val match =
        tracks.firstOrNull { track ->
            matchesTitle(track.name) && matchesArtist(track.artists.map { it.name })
        } ?: return null

    return match.uri?.takeIf { it.startsWith("spotify:track:") }
        ?: match.id.takeIf { it.isNotBlank() }?.let { "spotify:track:$it" }
}

internal fun resolveImageDiskCacheConfig(maxImageCacheSizeMb: Int?): ImageDiskCacheConfig {
    val sizeMb = maxImageCacheSizeMb ?: 512
    if (sizeMb == 0) return ImageDiskCacheConfig(policy = CachePolicy.DISABLED, maxSizeBytes = 1L)
    if (sizeMb < 0) return ImageDiskCacheConfig(policy = CachePolicy.ENABLED, maxSizeBytes = Long.MAX_VALUE)
    val bytesPerMb = 1024L * 1024L
    val safeSizeMb = sizeMb.toLong().coerceAtMost(Long.MAX_VALUE / bytesPerMb)
    return ImageDiskCacheConfig(policy = CachePolicy.ENABLED, maxSizeBytes = safeSizeMb * bytesPerMb)
}
