/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.constants.PoolApiKeyKey
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Fetches shared premium **accounts** (not just instances) from the community Source Pool website's
 * `/api/sources` endpoint and caches their credentials for the Tidal/Qobuz resolvers.
 *
 * This is the account-consuming counterpart to [moe.rukamori.archivetune.tidal.TidalInstanceHealthManager],
 * which only handles instance base URLs. Where instances are proxy servers, accounts are real
 * subscriber tokens that let the app resolve full-quality FLAC directly against the official APIs
 * without anyone hosting a restream server.
 *
 * Security model:
 *  - The pool exposes account tokens as AES-256-GCM ciphertext (E2E). We decrypt locally with
 *    [PoolCrypto], which uses `BuildConfig.POOL_CLIENT_KEY`. When the pool has no client key
 *    configured the values arrive in plaintext and [PoolCrypto.maybeDecrypt] passes them through.
 *  - When the pool enforces read keys, we present `BuildConfig.SOURCE_PROVIDER_KEY` as a bearer.
 *
 * Behaviour:
 *  - Disabled entirely when no `SOURCE_PROVIDER_URL` is baked in (mirrors instance discovery).
 *  - Results are cached in memory for the resolvers (synchronous getters) and persisted to the
 *    DataStore so accounts are available immediately on the next cold start, before the network
 *    refresh completes.
 *  - [refresh] is throttled so it hits the network at most once per [MIN_REFRESH_INTERVAL_MS]
 *    unless `force` is set.
 */
object PoolAccountManager {
    private const val TAG = "PoolAccounts"
    // Pool credentials change slowly (submissions + hourly health sweeps on the server). Fetching
    // more than once a day mostly re-reads the same bytes, so 24h keeps the pool's database from
    // being woken for nothing on every app start. `force = true` (the settings refresh button)
    // still bypasses this.
    private const val MIN_REFRESH_INTERVAL_MS = 24 * 60 * 60 * 1000L
    // …but only once every service actually has something cached. The 24h throttle was gated on
    // `hasAccounts()`, which is true as soon as *any one* service is populated — so a pool that
    // served Tidal accounts locked Deezer and Qobuz out for a full day, and "Check source" (which
    // refreshes without `force`) could never discover them however many times it was tapped. When
    // any service is still empty, retry on this much shorter interval instead.
    private const val MIN_PARTIAL_REFRESH_INTERVAL_MS = 15 * 60 * 1000L

    private val CACHE_TIDAL_KEY = stringPreferencesKey("poolTidalAccounts")
    private val CACHE_QOBUZ_KEY = stringPreferencesKey("poolQobuzAccounts")
    private val CACHE_DEEZER_KEY = stringPreferencesKey("poolDeezerAccounts")
    private val CACHE_APPLE_KEY = stringPreferencesKey("poolAppleMusicAccounts")

    /** Last resolved read key, so fire-and-forget /api/report calls use the same identity. */
    @Volatile
    private var poolApiKey: String? = null

    /** A shared Tidal subscriber token contributed to the pool. */
    data class TidalPoolAccount(
        val id: Long?,
        val token: String,
        val refreshToken: String?,
        val countryCode: String?,
        val premium: Boolean,
    )

    /** A shared Qobuz subscriber credential. [appSecret] is required to sign stream URLs. */
    data class QobuzPoolAccount(
        val id: Long?,
        val token: String,
        val appId: String,
        val appSecret: String,
        val premium: Boolean,
    )

    /**
     * A shared Deezer subscriber credential.
     *
     * Deezer authenticates with the `arl` cookie rather than a bearer token, and [premium] false means
     * the account has no lossless entitlement, so it can still serve MP3 but will refuse FLAC.
     */
    data class DeezerPoolAccount(
        val id: Long?,
        val arl: String,
        val premium: Boolean,
        /** Optional override for the Blowfish key salt; null means use the salt the app ships with. */
        val masterSecret: String? = null,
    )

    /**
     * A shared Apple Music credential. [mediaUserToken] is the personal `0.Ap…` token from the
     * contributor's Apple Music web session; it unlocks user-scoped AMP API calls (lyrics,
     * personal storefront) and — with an active subscription — full-track playback via the
     * web-playback endpoint. The dev (Bearer) JWT is NOT pooled: the app self-scrapes one.
     */
    data class AppleMusicPoolAccount(
        val id: Long?,
        val mediaUserToken: String,
        val premium: Boolean,
    )

    @Volatile
    private var tidalCache: List<TidalPoolAccount> = emptyList()

    @Volatile
    private var qobuzCache: List<QobuzPoolAccount> = emptyList()

    @Volatile
    private var deezerCache: List<DeezerPoolAccount> = emptyList()

    @Volatile
    private var appleMusicCache: List<AppleMusicPoolAccount> = emptyList()

    @Volatile
    private var lastRefreshAt = 0L

    @Volatile
    private var loadedFromDisk = false

    private val refreshMutex = Mutex()

    private val client =
        OkHttpClient
            .Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .build()

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaTypeOrNull()

    /** Fire-and-forget reports survive app lifecycle (SupervisorJob); reports never gate playback. */
    private val reportScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** True when a Source Pool URL is configured, i.e. account discovery is possible. */
    val isEnabled: Boolean
        get() = BuildConfig.SOURCE_PROVIDER_URL.isNotBlank()

    private val poolBaseUrl: String?
        get() =
            BuildConfig.SOURCE_PROVIDER_URL
                .trim()
                .trimEnd('/')
                .takeIf { it.isNotEmpty() }

    /**
     * Credentials-only feed of the split pool. The server also serves a combined legacy feed at
     * /api/sources (accounts + instance URLs together) for older app builds; this client asks
     * for the narrow account feed so tokens and instance URLs arrive over separate URLs.
     * Falls back to the legacy URL only when the pool deployment predates the split (404).
     */
    private val accountsUrl: String? get() = poolBaseUrl?.let { "$it/api/accounts" }

    private val legacySourcesUrl: String? get() = poolBaseUrl?.let { "$it/api/sources" }

    /** Premium accounts first; callers try them in order. Paste-list accounts follow pool ones. Never throws. */
    fun tidalAccounts(): List<TidalPoolAccount> = (tidalCache + PasteListPoolSource.tidalAccounts()).sortedByDescending { it.premium }

    fun qobuzAccounts(): List<QobuzPoolAccount> = (qobuzCache + PasteListPoolSource.qobuzAccounts()).sortedByDescending { it.premium }

    fun deezerAccounts(): List<DeezerPoolAccount> = (deezerCache + PasteListPoolSource.deezerAccounts()).sortedByDescending { it.premium }

    fun appleMusicAccounts(): List<AppleMusicPoolAccount> = (appleMusicCache + PasteListPoolSource.appleMusicAccounts()).sortedByDescending { it.premium }

    fun hasAccounts(): Boolean =
        tidalCache.isNotEmpty() || qobuzCache.isNotEmpty() || deezerCache.isNotEmpty() || appleMusicCache.isNotEmpty() ||
            PasteListPoolSource.tidalAccounts().isNotEmpty() || PasteListPoolSource.qobuzAccounts().isNotEmpty() ||
            PasteListPoolSource.deezerAccounts().isNotEmpty() || PasteListPoolSource.appleMusicAccounts().isNotEmpty()

    /** True when every pooled service has at least one account, i.e. nothing is left to discover. */
    private fun hasEveryService(): Boolean =
        (tidalCache.isNotEmpty() || PasteListPoolSource.tidalAccounts().isNotEmpty()) &&
            (qobuzCache.isNotEmpty() || PasteListPoolSource.qobuzAccounts().isNotEmpty()) &&
            (deezerCache.isNotEmpty() || PasteListPoolSource.deezerAccounts().isNotEmpty()) &&
            (appleMusicCache.isNotEmpty() || PasteListPoolSource.appleMusicAccounts().isNotEmpty())

    /**
     * How long a non-forced [refresh] may be skipped for. Full caches are re-read once a day; a
     * cache that is still missing a service is retried far more eagerly so that service can appear
     * without the user having to hunt for the manual refresh button.
     */
    private fun refreshIntervalMs(): Long =
        if (hasEveryService()) MIN_REFRESH_INTERVAL_MS else MIN_PARTIAL_REFRESH_INTERVAL_MS

    /**
     * Loads the persisted account cache into memory (cheap, no network). Safe to call repeatedly;
     * only reads the DataStore once. Call early on startup so resolvers have data before the first
     * network [refresh] finishes.
     */
    suspend fun loadCached(context: Context) {
        if (loadedFromDisk) return
        withContext(Dispatchers.IO) {
            runCatching {
                PasteListPoolSource.loadCached(context)
                cached(context, CACHE_TIDAL_KEY)?.takeIf { it.isNotBlank() }?.let {
                    tidalCache = parseTidal(JSONArray(it))
                }
                cached(context, CACHE_QOBUZ_KEY)?.takeIf { it.isNotBlank() }?.let {
                    qobuzCache = parseQobuz(JSONArray(it))
                }
                cached(context, CACHE_DEEZER_KEY)?.takeIf { it.isNotBlank() }?.let {
                    deezerCache = parseDeezer(JSONArray(it))
                }
                cached(context, CACHE_APPLE_KEY)?.takeIf { it.isNotBlank() }?.let {
                    appleMusicCache = parseAppleMusic(JSONArray(it))
                }
                loadedFromDisk = true
                Timber.tag(TAG).d(
                    "Loaded cached accounts: tidal=%d qobuz=%d deezer=%d apple=%d",
                    tidalCache.size,
                    qobuzCache.size,
                    deezerCache.size,
                    appleMusicCache.size,
                )
            }.onFailure { Timber.tag(TAG).w(it, "Failed to load cached pool accounts") }
        }
    }

    /**
     * Fetches `/api/sources`, decrypts credentials, and refreshes the in-memory + persisted caches.
     * Returns true when the cache is populated (either freshly fetched or already warm). Throttled
     * unless [force] is set. Never throws.
     */
    suspend fun refresh(
        context: Context,
        force: Boolean = false,
    ): Boolean =
        withContext(Dispatchers.IO) {
            // Paste lists work with no Source Pool URL baked in — only skip when there is
            // neither a pool URL nor any paste-list URL configured.
            if (!isEnabled && !PasteListPoolSource.hasUrls(context)) return@withContext false
            loadCached(context)

            val now = System.currentTimeMillis()
            if (!force && hasAccounts() && now - lastRefreshAt < refreshIntervalMs()) {
                return@withContext true
            }

            refreshMutex.withLock {
                // Re-check the throttle inside the lock in case another caller just refreshed.
                if (!force && hasAccounts() && System.currentTimeMillis() - lastRefreshAt < refreshIntervalMs()) {
                    return@withLock true
                }
                val url = accountsUrl ?: legacySourcesUrl
                if (url == null) {
                    // No Source Pool URL baked in — paste lists are the only account source.
                    Timber.tag(TAG).d("No Source Pool URL configured; refreshing paste lists only")
                } else {
                    runCatching {
                        // A key pasted by the user on-device (pool site /dashboard → copy) wins over
                        // the CI-baked build key, so personal accounts work without a custom APK.
                        val readKey =
                                cached(context, PoolApiKeyKey)?.trim().orEmpty()
                                .ifBlank { BuildConfig.SOURCE_PROVIDER_KEY }
                        poolApiKey = readKey.ifBlank { null }
                        val fetched = fetchAccounts(context, url, readKey)
                        if (fetched == null && url == accountsUrl && legacySourcesUrl != null) {
                            // Pool deployment predates the split feed — retry the combined URL.
                            Timber.tag(TAG).d("/api/accounts unavailable; falling back to legacy /api/sources")
                            fetchAccounts(context, legacySourcesUrl!!, readKey)
                        } else {
                            fetched
                        }
                    }.onFailure { Timber.tag(TAG).w(it, "Pool account refresh failed") }
                } // else (pool URL configured)

                // Second source: user-configured community paste lists (rentry/gist tables).
                // Runs inside the same mutex so the settings refresh button covers both.
                runCatching {
                    PasteListPoolSource.refresh(context, force = force)
                }.onFailure { Timber.tag(TAG).w(it, "Paste-list refresh failed") }
                hasAccounts()
            }
        }

    /**
     * Fetches and parses one credentials feed (/api/accounts, or the accounts half of the legacy
     * /api/sources). Applies the parsed accounts to the in-memory caches and persists them
     * encrypted. Returns null when the request failed with a retryable status (404 — split feed
     * not deployed yet) so the caller can fall back; other failures are logged and swallowed.
     */
    private suspend fun fetchAccounts(
        context: Context,
        url: String,
        readKey: String,
    ): JSONObject? {
        val builder = Request.Builder().url(url).header("User-Agent", "ArchiveTune-Android")
        if (readKey.isNotBlank()) {
            builder.header("Authorization", "Bearer $readKey")
        }
        client.newCall(builder.get().build()).execute().use { response ->
            if (!response.isSuccessful) {
                if (response.code == 404) {
                    return null
                }
                // HTTP 401 specifically means the pool's read-key enforcement rejected this
                // build (SOURCE_PROVIDER_KEY is missing or wrong in BuildConfig). Surface that
                // explicitly so the failure mode is obvious in logs instead of looking like an
                // empty pool.
                if (response.code == 401) {
                    Timber.tag(TAG).w(
                        "Pool account feed rejected the request as unauthorized (HTTP 401). " +
                            "The build's SOURCE_PROVIDER_KEY is missing or invalid; " +
                            "the pool returned 0 accounts. Check that the CI workflow " +
                            "injects SOURCE_PROVIDER_KEY/POOL_CLIENT_KEY secrets.",
                    )
                } else {
                    Timber.tag(TAG).w("Pool account feed %s returned HTTP %d", url, response.code)
                }
                return null
            }
            val root = JSONObject(response.body?.string().orEmpty())
            val tidal = parseTidal(accountsArray(root, "tidal"))
            val qobuz = parseQobuz(accountsArray(root, "qobuz"))
            val deezer = parseDeezer(accountsArray(root, "deezer"))
            val apple = parseAppleMusic(accountsArray(root, "apple-music"))
            // Don't overwrite the in-memory cache with an empty list when the pool returns a
            // 200 with a partial/empty response (rate-limit, transient server bug, captive-portal
            // interception, malformed JSON). The user symptom is "Qobuz and other source
            // providers disappear all of a sudden while playing songs" — and the only way to
            // recover was force-stop + re-open. Only update the cache when at least one list is
            // non-empty. Otherwise keep the previous (non-empty) cache so playback keeps working.
            val allEmpty = tidal.isEmpty() && qobuz.isEmpty() && deezer.isEmpty() && apple.isEmpty()
            if (allEmpty && hasAccounts()) {
                Timber
                    .tag(TAG)
                    .w("Pool returned empty account lists — keeping existing cache to avoid mid-playback source disappearance")
            } else {
                tidalCache = tidal
                qobuzCache = qobuz
                deezerCache = deezer
                appleMusicCache = apple
                lastRefreshAt = System.currentTimeMillis()
                persist(context, tidal, qobuz, deezer, apple)
            }
            Timber.tag(TAG).i(
                "Pool accounts refreshed: tidal=%d qobuz=%d deezer=%d apple=%d",
                tidal.size,
                qobuz.size,
                deezer.size,
                apple.size,
            )
            return root
        }
    }

    private suspend fun persist(
        context: Context,
        tidal: List<TidalPoolAccount>,
        qobuz: List<QobuzPoolAccount>,
        deezer: List<DeezerPoolAccount>,
        apple: List<AppleMusicPoolAccount>,
    ) {
        val tidalJson =
            JSONArray().apply {
                tidal.forEach {
                    put(
                        JSONObject()
                            .put("id", it.id)
                            .put("token", it.token)
                            .put("refreshToken", it.refreshToken)
                            .put("countryCode", it.countryCode)
                            .put("premium", it.premium),
                    )
                }
            }.toString()
        val qobuzJson =
            JSONArray().apply {
                qobuz.forEach {
                    put(
                        JSONObject()
                            .put("id", it.id)
                            .put("token", it.token)
                            .put("appId", it.appId)
                            .put("appSecret", it.appSecret)
                            .put("premium", it.premium),
                    )
                }
            }.toString()
        val deezerJson =
            JSONArray().apply {
                deezer.forEach {
                    put(
                        JSONObject()
                            .put("id", it.id)
                            .put("arl", it.arl)
                            .put("masterSecret", it.masterSecret)
                            .put("premium", it.premium),
                    )
                }
            }.toString()
        val appleJson =
            JSONArray().apply {
                apple.forEach {
                    put(
                        JSONObject()
                            .put("id", it.id)
                            .put("token", it.mediaUserToken)
                            .put("premium", it.premium),
                    )
                }
            }.toString()
        runCatching {
            context.dataStore.edit { prefs ->
                prefs[CACHE_TIDAL_KEY] = PoolCacheCrypto.encrypt(tidalJson)
                prefs[CACHE_QOBUZ_KEY] = PoolCacheCrypto.encrypt(qobuzJson)
                prefs[CACHE_DEEZER_KEY] = PoolCacheCrypto.encrypt(deezerJson)
                prefs[CACHE_APPLE_KEY] = PoolCacheCrypto.encrypt(appleJson)
            }
        }.onFailure { Timber.tag(TAG).w(it, "Failed to persist pool accounts") }
    }

    private suspend fun cached(context: Context, key: androidx.datastore.preferences.core.Preferences.Key<String>): String? {
        val raw = context.dataStore.getAsync(key)?.takeIf { it.isNotBlank() } ?: return null
        PoolCacheCrypto.decrypt(raw)?.let { return it }
        // Migrate old plaintext cache entries immediately; never write them back.
        if (key == PoolApiKeyKey) {
            context.dataStore.edit { prefs -> prefs[key] = PoolCacheCrypto.encrypt(raw) }
        }
        return raw
    }

    /**
     * Reports playback-time observations back to the pool (`dead` / `not_premium`) so entries that
     * fail for real users stop being leased without waiting for the next server sweep — and so the
     * pool's database is not hit repeatedly by every app probing every credential. Fire-and-forget:
     * a report must never break playback or block a resolver, hence non-suspend + own scope.
     * Manual accounts (id == null) are never reported.
     */
    fun report(
        service: String,
        kind: String,
        id: Long?,
        reportType: String,
    ) {
        if (id == null) return
        val base = poolBaseUrl ?: return
        reportScope.launch {
            runCatching {
                val body =
                    JSONObject()
                        .put("service", service)
                        .put("kind", kind)
                        .put("id", id)
                        .put("report", reportType)
                        .toString()
                val builder =
                    Request
                        .Builder()
                        .url("$base/api/report")
                        .header("User-Agent", "ArchiveTune-Android")
                        .post(body.toRequestBody(JSON_MEDIA))
                    val readKey = poolApiKey?.takeIf { it.isNotBlank() } ?: BuildConfig.SOURCE_PROVIDER_KEY
                    if (readKey.isNotBlank()) {
                        builder.header("Authorization", "Bearer $readKey")
                    }
                client.newCall(builder.build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        Timber.tag(TAG).d("Pool report %s/%s/%s returned HTTP %d", service, reportType, id, response.code)
                    }
                }
            }.onFailure { Timber.tag(TAG).w(it, "Pool report failed") }
        }
    }

    /**
     * Accounts array for one service, tolerant of both feed shapes: the canonical
     * `{ "tidal": { "accounts": [...] } }` (mirrors the legacy /api/sources account half) and a
     * bare `{ "tidal": [...] } }. Null when the service is absent or empty.
     */
    private fun accountsArray(root: JSONObject, service: String): JSONArray? =
        root.optJSONObject(service)?.optJSONArray("accounts") ?: root.optJSONArray(service)

    /** Decrypts a sensitive field. Empty/blank blobs and decrypt failures yield null. */
    private fun field(obj: JSONObject, key: String): String? {
        val raw = obj.optString(key, "").takeIf { it.isNotBlank() } ?: return null
        val decoded = PoolCrypto.maybeDecrypt(raw)?.takeIf { it.isNotBlank() }
        if (decoded == null && PoolCrypto.isEncrypted(raw)) {
            Timber.tag(TAG).w("Dropped encrypted pool field %s because the client key could not decrypt it", key)
        }
        return decoded
    }

    private fun parseTidal(arr: JSONArray?): List<TidalPoolAccount> {
        if (arr == null) return emptyList()
        val out = mutableListOf<TidalPoolAccount>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val token = field(obj, "token") ?: continue
            out +=
                TidalPoolAccount(
                    id = entryId(obj),
                    token = token,
                    refreshToken = field(obj, "refreshToken"),
                    countryCode = field(obj, "countryCode"),
                    premium = obj.optBoolean("premium", false),
                )
        }
        return out
    }

    private fun parseQobuz(arr: JSONArray?): List<QobuzPoolAccount> {
        if (arr == null) return emptyList()
        val out = mutableListOf<QobuzPoolAccount>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val token = field(obj, "token") ?: continue
            val appId = field(obj, "appId") ?: continue
            // Without an app secret the app cannot sign Qobuz stream URLs, so such an account is
            // useless for playback and is skipped rather than cached as a dead entry.
            val appSecret = field(obj, "appSecret") ?: continue
            out +=
                QobuzPoolAccount(
                    id = entryId(obj),
                    token = token,
                    appId = appId,
                    appSecret = appSecret,
                    premium = obj.optBoolean("premium", false),
                )
        }
        return out
    }

    private fun parseDeezer(arr: JSONArray?): List<DeezerPoolAccount> {
        if (arr == null) return emptyList()
        val out = mutableListOf<DeezerPoolAccount>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val arl = field(obj, "arl") ?: continue
            out +=
                DeezerPoolAccount(
                    id = entryId(obj),
                    arl = arl,
                    premium = obj.optBoolean("premium", false),
                    masterSecret = field(obj, "masterSecret"),
                )
        }
        return out
    }

    /** Pool entry id from /api/sources (positive when present); null for manual/legacy entries. */
    private fun entryId(obj: JSONObject): Long? =
        obj.optLong("id", 0L).takeIf { it > 0L }

    private fun parseAppleMusic(arr: JSONArray?): List<AppleMusicPoolAccount> {
        if (arr == null) return emptyList()
        val out = mutableListOf<AppleMusicPoolAccount>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val token = field(obj, "token") ?: continue
            if (!token.startsWith("0.")) continue // media-user-tokens always start with "0."
            out +=
                AppleMusicPoolAccount(
                    id = entryId(obj),
                    mediaUserToken = token,
                    premium = obj.optBoolean("premium", true),
                )
        }
        return out
    }
}
