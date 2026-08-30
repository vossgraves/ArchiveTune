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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.PasteListUrlsKey
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Fetches and parses user-configured community **paste lists** — rentry/gist/pastebin style
 * pages that tabulate shared tokens/ARLs in markdown tables (the pattern popularised by
 * Firehawk52's lists and the `marl` pool consumer: fetch the raw page, change-detect via
 * SHA-256, parse the table, prune dead entries, rotate).
 *
 * This is a *second* pool source next to the Source Pool website: the URLs are opt-in
 * (default empty — nothing is fetched until the user adds one), credentials parsed from the
 * page stay on-device, and the resulting accounts are merged into [PoolAccountManager]'s
 * caches with `id = null` so playback-failure reports are never sent for them.
 *
 * Recognised credentials (per table row / cell):
 *  - Deezer ARL: 32 hex chars (deezer-bucketed context)
 *  - Apple media-user-token: `0.`-prefixed token
 *  - Tidal bearer JWT: `eyJ…` triple-segment token in a Tidal-bucketed context
 *  - Qobuz UAT: JWT in a Qobuz-bucketed row that also carries an app id + secret
 *    (without both, the app cannot sign stream URLs, so the entry is skipped)
 *
 * Context bucketing: the most recent markdown heading / bold line that mentions a service
 * name, else the table header keywords (`arl` → deezer, `qobuz`/`app_id` → qobuz,
 * `tidal` → tidal, `apple`/`media` → apple), else Tidal for bare JWTs.
 */
object PasteListPoolSource {
    private const val TAG = "PasteListPool"

    /** Paste lists change slowly (community-maintained); 6h matches the "slow token" cadence. */
    private const val MIN_REFRESH_INTERVAL_MS = 6 * 60 * 60 * 1000L

    private val HASHES_KEY = stringPreferencesKey("pasteListHashes")
    private val LAST_KEY = stringPreferencesKey("pasteListLastRefresh")
    private val TIDAL_KEY = stringPreferencesKey("pasteListTidal")
    private val QOBUZ_KEY = stringPreferencesKey("pasteListQobuz")
    private val DEEZER_KEY = stringPreferencesKey("pasteListDeezer")
    private val APPLE_KEY = stringPreferencesKey("pasteListApple")

    private val ARL_REGEX = Regex("^[a-fA-F0-9]{32}$")
    private val JWT_REGEX = Regex("^eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]*$")
    private val APPLE_TOKEN_REGEX = Regex("^0\\.[A-Za-z0-9._-]{10,}$")
    private val QOBUZ_APP_ID_REGEX = Regex("^\\d{5,8}$")
    private val HEX_SECRET_REGEX = Regex("^[a-fA-F0-9]{32,64}$")

    private val client =
        OkHttpClient
            .Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()

    @Volatile
    private var lastRefreshAt = 0L

    @Volatile
    private var tidalCache: List<PoolAccountManager.TidalPoolAccount> = emptyList()

    @Volatile
    private var qobuzCache: List<PoolAccountManager.QobuzPoolAccount> = emptyList()

    @Volatile
    private var deezerCache: List<PoolAccountManager.DeezerPoolAccount> = emptyList()

    @Volatile
    private var appleCache: List<PoolAccountManager.AppleMusicPoolAccount> = emptyList()

    suspend fun hasUrls(context: Context): Boolean = cachedUrls(context).isNotEmpty()

    private suspend fun cachedUrls(context: Context): List<String> =
        context.dataStore
            .getAsync(PasteListUrlsKey)
            .orEmpty()
            .split('\n')
            .map { it.trim() }
            .filter { it.startsWith("http://") || it.startsWith("https://") }
            .distinct()

    fun tidalAccounts(): List<PoolAccountManager.TidalPoolAccount> = tidalCache

    fun qobuzAccounts(): List<PoolAccountManager.QobuzPoolAccount> = qobuzCache

    fun deezerAccounts(): List<PoolAccountManager.DeezerPoolAccount> = deezerCache

    fun appleMusicAccounts(): List<PoolAccountManager.AppleMusicPoolAccount> = appleCache

    /** Loads persisted paste-list accounts into memory. Cheap, no network. */
    suspend fun loadCached(context: Context) {
        withContext(Dispatchers.IO) {
            runCatching {
                context.dataStore.getAsync(TIDAL_KEY)?.takeIf { it.isNotBlank() }?.let {
                    tidalCache = parseTidalJson(it)
                }
                context.dataStore.getAsync(QOBUZ_KEY)?.takeIf { it.isNotBlank() }?.let {
                    qobuzCache = parseQobuzJson(it)
                }
                context.dataStore.getAsync(DEEZER_KEY)?.takeIf { it.isNotBlank() }?.let {
                    deezerCache = parseDeezerJson(it)
                }
                context.dataStore.getAsync(APPLE_KEY)?.takeIf { it.isNotBlank() }?.let {
                    appleCache = parseAppleJson(it)
                }
            }.onFailure { Timber.tag(TAG).w(it, "Failed to load cached paste-list accounts") }
        }
    }

    /**
     * Fetches every configured URL, re-parses only pages whose bytes changed, and persists the
     * merged result. Throttled unless [force]. Never throws; returns true when any account parsed.
     */
    suspend fun refresh(
        context: Context,
        force: Boolean = false,
    ): Boolean =
        withContext(Dispatchers.IO) {
            loadCached(context)
            val urls = cachedUrls(context)
            if (urls.isEmpty()) return@withContext false

            val now = System.currentTimeMillis()
            if (!force && now - lastRefreshAt < MIN_REFRESH_INTERVAL_MS) {
                return@withContext tidalCache.isNotEmpty() || qobuzCache.isNotEmpty() ||
                    deezerCache.isNotEmpty() || appleCache.isNotEmpty()
            }

            val hashesJson = runCatching { JSONObject(context.dataStore.getAsync(HASHES_KEY).orEmpty()) }
                .getOrDefault(JSONObject())
            val newHashes = JSONObject()
            val accounts = Accounts()

            for (url in urls) {
                runCatching {
                    val body =
                        client.newCall(
                            Request
                                .Builder()
                                .url(url)
                                .header("User-Agent", "ArchiveTune-Android")
                                .get()
                                .build(),
                        ).execute().use { response ->
                            if (!response.isSuccessful) {
                                Timber.tag(TAG).w("Paste list %s returned HTTP %d", url, response.code)
                                return@runCatching
                            }
                            response.body?.string().orEmpty()
                        }
                    val hash = sha256(body)
                    newHashes.put(url, hash)
                    if (!force && hashesJson.optString(url) == hash) {
                        Timber.tag(TAG).d("Paste list %s unchanged — skipping parse", url)
                        return@runCatching
                    }
                    mergeInto(accounts, parse(body))
                }.onFailure { Timber.tag(TAG).w(it, "Paste list fetch failed: %s", url) }
            }

            runCatching {
                context.dataStore.edit { prefs ->
                    prefs[HASHES_KEY] = newHashes.toString()
                    prefs[LAST_KEY] = now.toString()
                    prefs[TIDAL_KEY] = accounts.tidal.toString()
                    prefs[QOBUZ_KEY] = accounts.qobuz.toString()
                    prefs[DEEZER_KEY] = accounts.deezer.toString()
                    prefs[APPLE_KEY] = accounts.apple.toString()
                }
                tidalCache = parseTidalJson(accounts.tidal.toString())
                qobuzCache = parseQobuzJson(accounts.qobuz.toString())
                deezerCache = parseDeezerJson(accounts.deezer.toString())
                appleCache = parseAppleJson(accounts.apple.toString())
                lastRefreshAt = System.currentTimeMillis()
                Timber.tag(TAG).i(
                    "Paste lists refreshed: tidal=%d qobuz=%d deezer=%d apple=%d",
                    tidalCache.size,
                    qobuzCache.size,
                    deezerCache.size,
                    appleCache.size,
                )
            }.onFailure { Timber.tag(TAG).w(it, "Failed to persist paste-list accounts") }

            tidalCache.isNotEmpty() || qobuzCache.isNotEmpty() ||
                deezerCache.isNotEmpty() || appleCache.isNotEmpty()
        }

    private class Accounts {
        val tidal = JSONArray()
        val qobuz = JSONArray()
        val deezer = JSONArray()
        val apple = JSONArray()
    }

    private fun mergeInto(
        target: Accounts,
        parsed: Accounts,
    ) {
        listOf(target.tidal to parsed.tidal, target.qobuz to parsed.qobuz, target.deezer to parsed.deezer, target.apple to parsed.apple)
            .forEach { (into, from) ->
                for (i in 0 until from.length()) into.put(from.optJSONObject(i) ?: continue)
            }
    }

    /**
     * Parses raw paste-list text into pool-shaped account JSON.
     * Deduplicates by token so repeated rows collapse.
     */
    private fun parse(text: String): Accounts {
        val out = Accounts()
        val seenTidal = mutableSetOf<String>()
        val seenQobuz = mutableSetOf<String>()
        val seenArl = mutableSetOf<String>()
        val seenApple = mutableSetOf<String>()
        var bucket = ""

        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach

            // Heading / bold lines re-bucket everything below them.
            val heading = headingText(line)
            if (heading != null) {
                bucket = heading
                return@forEach
            }

            val isTableRow = line.contains('|')
            val cells =
                if (isTableRow) {
                    line.split('|').map { it.trim() }.filter { it.isNotEmpty() }
                } else {
                    listOf(line)
                }
            if (cells.isEmpty()) return@forEach

            // A header row ("Region | ARL | Expiry") refines the bucket and is not data.
            if (cells.none { looksLikeCredential(it) } && cells.any { it.lowercase() in HEADER_KEYWORDS }) {
                bucket = (cells.joinToString(" ") + " " + bucket).trim()
                return@forEach
            }

            val service = serviceFor(bucket, cells)
            for (cell in cells) {
                when {
                    // `0.`-prefixed tokens are Apple media-user-tokens regardless of section.
                    APPLE_TOKEN_REGEX.matches(cell) -> {
                        if (seenApple.add(cell)) {
                            out.apple.put(JSONObject().put("token", cell).put("premium", true))
                        }
                    }

                    ARL_REGEX.matches(cell) && service == "deezer" -> {
                        if (seenArl.add(cell.lowercase())) {
                            out.deezer.put(JSONObject().put("arl", cell.lowercase()).put("premium", true))
                        }
                    }

                    JWT_REGEX.matches(cell) && service == "qobuz" -> {
                        // Qobuz needs an app id + secret from the same row to be usable.
                        val appId = cells.firstOrNull { QOBUZ_APP_ID_REGEX.matches(it) }
                        val appSecret =
                            cells.firstOrNull {
                                HEX_SECRET_REGEX.matches(it) && it != cell
                            }
                        if (appId != null && appSecret != null && seenQobuz.add(cell)) {
                            out.qobuz.put(
                                JSONObject()
                                    .put("token", cell)
                                    .put("appId", appId)
                                    .put("appSecret", appSecret)
                                    .put("premium", true),
                            )
                        }
                    }

                    JWT_REGEX.matches(cell) && service == "tidal" -> {
                        if (seenTidal.add(cell)) {
                            out.tidal.put(JSONObject().put("token", cell).put("premium", true))
                        }
                    }
                }
            }
        }
        return out
    }

    private val HEADER_KEYWORDS =
        setOf("arl", "token", "app_id", "appid", "app secret", "appsecret", "secret", "region", "country", "expiry", "plan", "thanks")

    private fun looksLikeCredential(cell: String): Boolean =
        ARL_REGEX.matches(cell) ||
            JWT_REGEX.matches(cell) ||
            APPLE_TOKEN_REGEX.matches(cell) ||
            QOBUZ_APP_ID_REGEX.matches(cell)

    /** Strips markdown decorations from a heading-ish line; null when it is not a heading. */
    private fun headingText(line: String): String? =
        when {
            line.startsWith("#") -> line.trimStart('#', ' ', '*', '_')
            line.startsWith("**") && line.endsWith("**") && line.length > 4 -> line.trim('*', ' ')
            else -> null
        }

    private fun serviceFor(
        bucket: String,
        cells: List<String>,
    ): String {
        val haystack = (bucket + " " + cells.joinToString(" ")).lowercase()
        return when {
            "qobuz" in haystack -> "qobuz"
            "tidal" in haystack -> "tidal"
            "deezer" in haystack || "arl" in haystack -> "deezer"
            "apple" in haystack || "media-user" in haystack || "music" in haystack -> "apple"
            else -> "tidal" // bare JWTs are most commonly Tidal bearers in these lists
        }
    }

    private fun sha256(text: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    // ── Plain-text JSON (de)serialisation — same shapes PoolAccountManager persists. ──

    private fun parseTidalJson(json: String): List<PoolAccountManager.TidalPoolAccount> {
        val arr = runCatching { JSONArray(json) }.getOrDefault(JSONArray())
        return (0 until arr.length()).mapNotNull { i ->
            val obj = arr.optJSONObject(i) ?: return@mapNotNull null
            val token = obj.optString("token").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            PoolAccountManager.TidalPoolAccount(
                id = null,
                token = token,
                refreshToken = obj.optString("refreshToken").takeIf { it.isNotBlank() },
                countryCode = obj.optString("countryCode").takeIf { it.isNotBlank() },
                premium = obj.optBoolean("premium", true),
            )
        }
    }

    private fun parseQobuzJson(json: String): List<PoolAccountManager.QobuzPoolAccount> {
        val arr = runCatching { JSONArray(json) }.getOrDefault(JSONArray())
        return (0 until arr.length()).mapNotNull { i ->
            val obj = arr.optJSONObject(i) ?: return@mapNotNull null
            val token = obj.optString("token").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val appId = obj.optString("appId").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val appSecret = obj.optString("appSecret").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            PoolAccountManager.QobuzPoolAccount(
                id = null,
                token = token,
                appId = appId,
                appSecret = appSecret,
                premium = obj.optBoolean("premium", true),
            )
        }
    }

    private fun parseDeezerJson(json: String): List<PoolAccountManager.DeezerPoolAccount> {
        val arr = runCatching { JSONArray(json) }.getOrDefault(JSONArray())
        return (0 until arr.length()).mapNotNull { i ->
            val obj = arr.optJSONObject(i) ?: return@mapNotNull null
            val arl = obj.optString("arl").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            PoolAccountManager.DeezerPoolAccount(
                id = null,
                arl = arl,
                premium = obj.optBoolean("premium", true),
            )
        }
    }

    private fun parseAppleJson(json: String): List<PoolAccountManager.AppleMusicPoolAccount> {
        val arr = runCatching { JSONArray(json) }.getOrDefault(JSONArray())
        return (0 until arr.length()).mapNotNull { i ->
            val obj = arr.optJSONObject(i) ?: return@mapNotNull null
            val token = obj.optString("token").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            if (!token.startsWith("0.")) return@mapNotNull null
            PoolAccountManager.AppleMusicPoolAccount(
                id = null,
                mediaUserToken = token,
                premium = obj.optBoolean("premium", true),
            )
        }
    }
}
