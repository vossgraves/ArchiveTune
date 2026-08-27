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

    @Volatile
    private var tidalCache: List<TidalPoolAccount> = emptyList()

    @Volatile
    private var qobuzCache: List<QobuzPoolAccount> = emptyList()

    @Volatile
    private var deezerCache: List<DeezerPoolAccount> = emptyList()

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

    private val sourcesUrl: String?
        get() =
            BuildConfig.SOURCE_PROVIDER_URL
                .trim()
                .trimEnd('/')
                .takeIf { it.isNotEmpty() }
                ?.let { "$it/api/sources" }

    /** Premium accounts first; callers try them in order. Never throws. */
    fun tidalAccounts(): List<TidalPoolAccount> = tidalCache.sortedByDescending { it.premium }

    fun qobuzAccounts(): List<QobuzPoolAccount> = qobuzCache.sortedByDescending { it.premium }

    fun deezerAccounts(): List<DeezerPoolAccount> = deezerCache.sortedByDescending { it.premium }

    fun hasAccounts(): Boolean = tidalCache.isNotEmpty() || qobuzCache.isNotEmpty() || deezerCache.isNotEmpty()

    /** True when every pooled service has at least one account, i.e. nothing is left to discover. */
    private fun hasEveryService(): Boolean =
        tidalCache.isNotEmpty() && qobuzCache.isNotEmpty() && deezerCache.isNotEmpty()

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
                context.dataStore.getAsync(CACHE_TIDAL_KEY)?.takeIf { it.isNotBlank() }?.let {
                    tidalCache = parseTidal(JSONArray(it))
                }
                context.dataStore.getAsync(CACHE_QOBUZ_KEY)?.takeIf { it.isNotBlank() }?.let {
                    qobuzCache = parseQobuz(JSONArray(it))
                }
                context.dataStore.getAsync(CACHE_DEEZER_KEY)?.takeIf { it.isNotBlank() }?.let {
                    deezerCache = parseDeezer(JSONArray(it))
                }
                loadedFromDisk = true
                Timber.tag(TAG).d(
                    "Loaded cached accounts: tidal=%d qobuz=%d deezer=%d",
                    tidalCache.size,
                    qobuzCache.size,
                    deezerCache.size,
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
            if (!isEnabled) return@withContext false
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
                val url = sourcesUrl ?: return@withLock false
                runCatching {
                    // A key pasted by the user on-device (pool site /dashboard → copy) wins over
                    // the CI-baked build key, so personal accounts work without a custom APK.
                    val readKey =
                        context.dataStore.getAsync(PoolApiKeyKey)?.trim().orEmpty()
                            .ifBlank { BuildConfig.SOURCE_PROVIDER_KEY }
                    poolApiKey = readKey.ifBlank { null }
                    val builder = Request.Builder().url(url).header("User-Agent", "ArchiveTune-Android")
                    if (readKey.isNotBlank()) {
                        builder.header("Authorization", "Bearer $readKey")
                    }
                    client.newCall(builder.get().build()).execute().use { response ->
                        if (!response.isSuccessful) {
                            // HTTP 401 specifically means the pool's read-key enforcement
                            // rejected this build (SOURCE_PROVIDER_KEY is missing or wrong in
                            // BuildConfig). Surface that explicitly so the failure mode is
                            // obvious in logs instead of looking like an empty pool.
                            if (response.code == 401) {
                                Timber.tag(TAG).w(
                                    "Pool /api/sources rejected the request as unauthorized (HTTP 401). " +
                                        "The build's SOURCE_PROVIDER_KEY is missing or invalid; " +
                                        "the pool returned 0 accounts. Check that the CI workflow " +
                                        "injects SOURCE_PROVIDER_KEY/POOL_CLIENT_KEY secrets.",
                                )
                            } else {
                                Timber.tag(TAG).w("Pool /api/sources returned HTTP %d", response.code)
                            }
                            return@withLock hasAccounts()
                        }
                        val root = JSONObject(response.body?.string().orEmpty())
                        val tidal = parseTidal(root.optJSONObject("tidal")?.optJSONArray("accounts"))
                        val qobuz = parseQobuz(root.optJSONObject("qobuz")?.optJSONArray("accounts"))
                        val deezer = parseDeezer(root.optJSONObject("deezer")?.optJSONArray("accounts"))
                        // Don't overwrite the in-memory cache with an empty list when the pool
                        // returns a 200 with a partial/empty response (rate-limit, transient
                        // server bug, captive-portal interception, malformed JSON). The user
                        // symptom is "Qobuz and other source providers disappear all of a sudden
                        // while playing songs" — and the only way to recover was force-stop +
                        // re-open. Only update the cache when at least one list is non-empty.
                        // Otherwise keep the previous (non-empty) cache so playback keeps working.
                        val allEmpty = tidal.isEmpty() && qobuz.isEmpty() && deezer.isEmpty()
                        if (allEmpty && hasAccounts()) {
                            Timber
                                .tag(TAG)
                                .w("Pool returned empty account lists — keeping existing cache to avoid mid-playback source disappearance")
                        } else {
                            tidalCache = tidal
                            qobuzCache = qobuz
                            deezerCache = deezer
                            lastRefreshAt = System.currentTimeMillis()
                            persist(context, tidal, qobuz, deezer)
                        }
                        Timber.tag(TAG).i(
                            "Pool accounts refreshed: tidal=%d qobuz=%d deezer=%d",
                            tidal.size,
                            qobuz.size,
                            deezer.size,
                        )
                    }
                }.onFailure { Timber.tag(TAG).w(it, "Pool account refresh failed") }
                hasAccounts()
            }
        }

    private suspend fun persist(
        context: Context,
        tidal: List<TidalPoolAccount>,
        qobuz: List<QobuzPoolAccount>,
        deezer: List<DeezerPoolAccount>,
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
        runCatching {
            context.dataStore.edit { prefs ->
                prefs[CACHE_TIDAL_KEY] = tidalJson
                prefs[CACHE_QOBUZ_KEY] = qobuzJson
                prefs[CACHE_DEEZER_KEY] = deezerJson
            }
        }.onFailure { Timber.tag(TAG).w(it, "Failed to persist pool accounts") }
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
        val base = sourcesUrl?.removeSuffix("/api/sources") ?: return
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
}
