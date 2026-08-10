/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.canvas

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.KotlinxSerializationConverter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import moe.rukamori.archivetune.canvas.models.CanvasArtwork
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object AppleMusicProvider {
    // ── Logging ──────────────────────────────────────────────────────────────────────
    //
    // Routed through GlobalLog so the canvas diagnostic lines show up in the
    // in-app logcat viewer with the proper tag. Previously this used
    // `println(...)` which Android redirects to logcat as `I/System.out:` —
    // losing the tag and the log level, and bypassing the host app's log
    // pipeline (which is what allows the in-app log viewer to filter by tag).
    //
    // The logger callback is set by the host app (App.kt) on startup, mirroring
    // how PaxsenixLyrics.logger is wired. When null we fall back to plain
    // println so this module stays standalone-testable without the host app.

    private const val LOG_TAG = "AppleMusicCanvas"

    // Log levels mirroring android.util.Log so this pure-JVM module doesn't
    // depend on the Android framework. The host app (App.kt) maps these to
    // android.util.Log when routing through GlobalLog.
    private const val LOG_LEVEL_VERBOSE = 2
    private const val LOG_LEVEL_DEBUG = 3
    private const val LOG_LEVEL_INFO = 4
    private const val LOG_LEVEL_WARN = 5
    private const val LOG_LEVEL_ERROR = 6

    var logger: ((level: Int, tag: String, message: String) -> Unit)? = null

    private object Log {
        fun d(msg: String) {
            val logger = AppleMusicProvider.logger
            if (logger != null) {
                logger(AppleMusicProvider.LOG_LEVEL_DEBUG, AppleMusicProvider.LOG_TAG, msg)
            } else {
                println("${AppleMusicProvider.LOG_TAG}: D: $msg")
            }
        }

        fun w(msg: String) {
            val logger = AppleMusicProvider.logger
            if (logger != null) {
                logger(AppleMusicProvider.LOG_LEVEL_WARN, AppleMusicProvider.LOG_TAG, msg)
            } else {
                println("${AppleMusicProvider.LOG_TAG}: W: $msg")
            }
        }

        fun e(
            t: Throwable,
            msg: String,
        ) {
            val logger = AppleMusicProvider.logger
            if (logger != null) {
                logger(AppleMusicProvider.LOG_LEVEL_ERROR, AppleMusicProvider.LOG_TAG, "$msg: ${t.message}")
            } else {
                println("${AppleMusicProvider.LOG_TAG}: E: $msg")
                t.printStackTrace()
            }
        }
    }

    // ── Constants ────────────────────────────────────────────────────────────────────

    // Fallback Apple Music web player JWT — publicly distributed by Apple in
    // their web player JavaScript bundle. Apple rotates it roughly every ~6
    // months; the previous hardcoded value expired on 2026-06-17, which
    // caused every AMP API request to silently 401 (and produced the
    // `D/CanvasArtwork: No playable canvas resolved for <mediaId>` log lines).
    //
    // We now scrape a fresh token on first use (and on 401) from the Apple
    // Music web player JS bundle via [ensureTokenFresh]. The hardcoded value
    // is kept only as a last-resort fallback in case scraping fails (e.g.
    // offline) so we don't NPE on the very first call.
    private val fallbackAppleMusicToken: String =
        "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6IldlYlBsYXlLaWQifQ" +
            ".eyJpc3MiOiJBTVBXZWJQbGF5IiwiaWF0IjoxNzc0NDU2MzgyLCJleHAiOjE3ODE3" +
            "MTM5ODIsInJvb3RfaHR0cHNfb3JpZ2luIjpbImFwcGxlLmNvbSJdfQ" +
            ".4n8qYF4qa18sL1E0G9A3qX35cD8wQ-IJcS9Bh8ZT8JV_yLBtVq46B-9-2ZS3EvWHuw3yK9BYFYAhAdTaDm38vQ"

    @Volatile
    private var appleMusicToken: String = fallbackAppleMusicToken

    // JWT decoded `exp` epoch seconds, or 0 if unknown / unparseable.
    @Volatile
    private var appleMusicTokenExpAtSec: Long = 0L

    // Last time we attempted to refresh the token, used to throttle retries
    // when the Apple Music web player is unreachable.
    @Volatile
    private var appleMusicTokenLastRefreshAtMs: Long = 0L

    private val tokenRefreshMutex = Mutex()

    private const val APPLE_MUSIC_WEB_HOME = "https://music.apple.com/"
    private const val AMP_BASE_URL = "https://amp-api.music.apple.com"
    private const val CACHE_TTL_MS = 1000L * 60 * 60 * 24 // 24 hours
    private const val APPLE_MUSIC_WEB_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36"

    private val tokenClient by lazy {
        HttpClient(OkHttp) {
            install(HttpTimeout) {
                requestTimeoutMillis = 10_000
                connectTimeoutMillis = 8_000
                socketTimeoutMillis = 10_000
            }
            defaultRequest {
                header("User-Agent", APPLE_MUSIC_WEB_UA)
                header("Accept", "text/html,application/xhtml+xml,application/javascript,*/*;q=0.8")
                header("Accept-Language", "en-US,en;q=0.9")
            }
            expectSuccess = false
        }
    }

    /**
     * Forces a refresh of the cached Apple Music web player JWT by scraping it
     * from the music.apple.com JavaScript bundle.
     *
     * Public so the host app can pre-warm the token on startup, but it's also
     * called automatically by [ensureTokenFresh] when the cached token is
     * missing or close to expiry.
     */
    suspend fun refreshToken(): String? =
        tokenRefreshMutex.withLock {
            appleMusicTokenLastRefreshAtMs = System.currentTimeMillis()
            val fresh = scrapeTokenFromWeb()
            if (fresh != null) {
                appleMusicToken = fresh
                appleMusicTokenExpAtSec = decodeJwtExpSec(fresh)
                Log.d("Apple Music token refreshed from web player (exp=${appleMusicTokenExpAtSec}s)")
            } else {
                Log.w("Apple Music token refresh failed — falling back to hardcoded token")
            }
            fresh
        }

    /**
     * Returns a usable Apple Music JWT, refreshing first if the cached one is
     * missing or past its expiry. Refresh failures fall back to whatever we
     * have on hand (including the hardcoded fallback).
     */
    private suspend fun ensureTokenFresh(): String {
        val nowSec = System.currentTimeMillis() / 1000L
        val needsRefresh =
            appleMusicTokenExpAtSec == 0L || appleMusicTokenExpAtSec - nowSec < 60L * 60L * 24L
        if (!needsRefresh) return appleMusicToken

        // Throttle: don't hammer music.apple.com more than once a minute.
        val sinceLast = System.currentTimeMillis() - appleMusicTokenLastRefreshAtMs
        if (sinceLast in 1..60_000L) return appleMusicToken

        // If the current token is still strictly valid, serve it and let the
        // 401 handler in [searchAndFetchMotion] force a refresh on rejection.
        if (appleMusicTokenExpAtSec == 0L || appleMusicTokenExpAtSec > nowSec) {
            return appleMusicToken
        }

        return refreshToken() ?: appleMusicToken
    }

    private suspend fun scrapeTokenFromWeb(): String? =
        try {
            val homeResponse = tokenClient.get(APPLE_MUSIC_WEB_HOME)
            if (homeResponse.status != HttpStatusCode.OK) {
                Log.w("Apple Music home fetch failed: ${homeResponse.status}")
                return null
            }
            val html = homeResponse.bodyAsText()

            directJwtRegex.find(html)?.let { return it.value }

            val jsBundleMatch = jsBundleRegex.find(html) ?: run {
                Log.w("Apple Music token: no JS bundle URL found in home HTML")
                return null
            }
            val rawJsUrl = jsBundleMatch.groupValues[1]
            val jsBundleUrl =
                when {
                    rawJsUrl.startsWith("http") -> rawJsUrl
                    rawJsUrl.startsWith("//") -> "https:$rawJsUrl"
                    rawJsUrl.startsWith("/") -> "https://music.apple.com$rawJsUrl"
                    else -> "https://music.apple.com/$rawJsUrl"
                }

            val jsResponse = tokenClient.get(jsBundleUrl)
            if (jsResponse.status != HttpStatusCode.OK) {
                Log.w("Apple Music token: JS bundle fetch failed: ${jsResponse.status}")
                return null
            }
            val js = jsResponse.bodyAsText()
            directJwtRegex.find(js)?.value.also {
                if (it == null) Log.w("Apple Music token: no JWT found in JS bundle $jsBundleUrl")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(e, "Apple Music token scrape error")
            null
        }

    // Apple Music web player JWTs are ES256-signed with 3 base64url segments.
    private val directJwtRegex: Regex =
        Regex("""eyJ[A-Za-z0-9_-]{8,}\.eyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}""")

    private val jsBundleRegex: Regex =
        Regex("""(?:src|href)=["']([^"']*index-[A-Za-z0-9._-]+\.js)["']""")

    /** Decodes the `exp` claim of a JWT without verifying the signature. */
    private fun decodeJwtExpSec(jwt: String): Long {
        val parts = jwt.split(".")
        if (parts.size != 3) return 0L
        val payload =
            runCatching {
                val normalized = parts[1].replace('-', '+').replace('_', '/')
                val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
                String(java.util.Base64.getDecoder().decode(padded))
            }.getOrNull() ?: return 0L
        val expMatch = """"exp"\s*:\s*(\d+)"""".toRegex().find(payload) ?: return 0L
        return expMatch.groupValues[1].toLongOrNull() ?: 0L
    }

    // ── Networking ───────────────────────────────────────────────────────────────────

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
        }

    private val client by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(json)
                register(ContentType.Text.JavaScript, KotlinxSerializationConverter(json))
            }
            install(HttpTimeout) {
                connectTimeoutMillis = 15_000
                requestTimeoutMillis = 25_000
                socketTimeoutMillis = 25_000
            }
            install(ContentEncoding) {
                gzip()
                deflate()
            }
            install(HttpCache)
            expectSuccess = false
        }
    }

    // ── Cache ────────────────────────────────────────────────────────────────────────

    private data class CacheEntry(
        val value: CanvasArtwork?,
        val expiresAtMs: Long,
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    private fun cacheKey(
        prefix: String,
        vararg parts: String,
    ): String = "$prefix|" + parts.joinToString("|") { it.trim().lowercase(Locale.ROOT) }

    // ── Public API ───────────────────────────────────────────────────────────────────

    suspend fun getByAlbumArtist(
        album: String,
        artist: String,
        storefront: String = "us",
    ): CanvasArtwork? {
        Log.d("getByAlbumArtist: album='$album', artist='$artist'")
        val key = cacheKey("sa", album, artist, storefront)
        cache[key]?.takeIf { it.expiresAtMs > System.currentTimeMillis() }?.let { return it.value }
        val result = searchAndFetchMotion(album, artist, album, storefront, "albums")
        if (result != null) cache[key] = CacheEntry(result, System.currentTimeMillis() + CACHE_TTL_MS)
        return result
    }

    suspend fun getBySongArtist(
        song: String,
        artist: String,
        album: String? = null,
        storefront: String = "us",
        forceRefresh: Boolean = false,
    ): CanvasArtwork? {
        val key = cacheKey("song", song, artist, album ?: "", storefront)
        if (forceRefresh) {
            cache.remove(key)
        } else {
            cache[key]?.takeIf { it.expiresAtMs > System.currentTimeMillis() }?.let { return it.value }
        }
        val result = searchAndFetchMotion(song, artist, album, storefront, "songs", forceRefresh)
        if (result != null) cache[key] = CacheEntry(result, System.currentTimeMillis() + CACHE_TTL_MS)
        return result
    }

    suspend fun getByAlbumId(
        albumId: String,
        storefront: String = "us",
    ): CanvasArtwork? {
        val key = cacheKey("id", albumId, storefront)
        cache[key]?.takeIf { it.expiresAtMs > System.currentTimeMillis() }?.let { return it.value }
        val result = fetchMotionArtwork(albumId, storefront, null)
        cache[key] = CacheEntry(result, System.currentTimeMillis() + CACHE_TTL_MS)
        return result
    }

    // ── Core Logic ───────────────────────────────────────────────────────────────────

    /**
     * Searches via AMP API and tries to fetch motion artwork.
     * This is faster than iTunes search + AMP lookup.
     */
    private suspend fun searchAndFetchMotion(
        term: String,
        artist: String,
        album: String?,
        storefront: String,
        type: String, // "albums" or "songs"
        forceRefresh: Boolean = false,
    ): CanvasArtwork? {
        return runCatching {
            Log.d("searching for $type: $term (album: $album) in $storefront")
            var query = if (term.contains(artist, ignoreCase = true)) term else "$artist $term"
            if (!album.isNullOrBlank() && !query.contains(album, ignoreCase = true)) query = "$query $album"

            val searchUrl = "$AMP_BASE_URL/v1/catalog/$storefront/search"
            var token = ensureTokenFresh()
            var response =
                client.get(searchUrl) {
                    header("Authorization", "Bearer $token")
                    header("Origin", "https://music.apple.com")
                    header("Referer", "https://music.apple.com/")
                    header("User-Agent", APPLE_MUSIC_WEB_UA)
                    parameter("term", query)
                    parameter("types", type)
                    parameter("limit", "10")
                    parameter("extend", "editorialVideo")
                    parameter("include", "albums")
                    if (forceRefresh) header("Cache-Control", "no-cache")
                }
            if (response.status == HttpStatusCode.Unauthorized) {
                // Token likely expired between refresh and use — force-refresh and retry once.
                Log.w("AMP search returned 401 — force-refreshing token and retrying once")
                token = refreshToken() ?: token
                response =
                    client.get(searchUrl) {
                        header("Authorization", "Bearer $token")
                        header("Origin", "https://music.apple.com")
                        header("Referer", "https://music.apple.com/")
                        header("User-Agent", APPLE_MUSIC_WEB_UA)
                        parameter("term", query)
                        parameter("types", type)
                        parameter("limit", "10")
                        parameter("extend", "editorialVideo")
                        parameter("include", "albums")
                        if (forceRefresh) header("Cache-Control", "no-cache")
                    }
            }
            if (response.status != HttpStatusCode.OK) {
                Log.w("search failed with status ${response.status}")
                return@runCatching null
            }

            val root = response.body<JsonObject>()
            val results =
                root["results"]
                    ?.jsonObject
                    ?.get(type)
                    ?.jsonObject
                    ?.get("data")
                    ?.jsonArray
                    ?: return@runCatching null

            val scoredResults =
                results
                    .mapNotNull { scoreAndFilterItem(it.jsonObject, term, artist, album) }
                    .sortedByDescending { it.first }

            Log.d("Found ${scoredResults.size} scored results for term '$term'")

            for ((score, obj) in scoredResults) {
                if (score < 12) {
                    Log.d("skipping result with low score: $score")
                    continue
                }

                val attributes = obj["attributes"]?.jsonObject ?: continue
                val resultName = attributes["name"]?.jsonPrimitive?.contentOrNull ?: ""
                val resultArtistName = attributes["artistName"]?.jsonPrimitive?.contentOrNull ?: ""
                val itemType = obj["type"]?.jsonPrimitive?.contentOrNull

                val targetAlbumId = resolveAlbumId(obj, attributes, itemType, resultName)
                if (targetAlbumId == null || targetAlbumId.startsWith("pl.")) {
                    Log.d("skipping null or playlist albumId ($targetAlbumId) for $resultName ($resultArtistName)")
                    continue
                }

                Log.d("trying resolve for $targetAlbumId (from $itemType)")

                // Check for immediate motion in search result
                val ev = attributes["editorialVideo"]?.jsonObject
                if (ev != null) {
                    val videoUrls = extractEditorialVideoUrls(ev)
                    if (!videoUrls.animated.isNullOrBlank() || !videoUrls.animatedVertical.isNullOrBlank()) {
                        val name = attributes["name"]?.jsonPrimitive?.contentOrNull
                        val collName = attributes["collectionName"]?.jsonPrimitive?.contentOrNull
                        val resolvedAlbumName = if (itemType == "songs") collName else name
                        Log.d("Found direct editorialVideo for $name (ID: $targetAlbumId)")
                        return@runCatching CanvasArtwork(
                            name = name,
                            artist = resultArtistName,
                            albumId = targetAlbumId,
                            albumName = resolvedAlbumName,
                            animated = videoUrls.animated,
                            animatedVertical = videoUrls.animatedVertical,
                        )
                    }
                }

                // Full lookup with metadata preservation
                val fetched =
                    fetchMotionArtwork(
                        albumId = targetAlbumId,
                        storefront = storefront,
                        fallbackArtist = resultArtistName,
                        titleOverride = if (itemType == "songs") attributes["name"]?.jsonPrimitive?.contentOrNull else null,
                        artistOverride = if (itemType == "songs") resultArtistName else null,
                    )
                if (fetched != null) return@runCatching fetched
            }
            Log.d("no canvas found in resolution/lookup for $term after ${scoredResults.size} results")
            null
        }.onFailure {
            if (it is CancellationException) throw it
            Log.e(it, "error in searchAndFetchMotion for $term")
        }.getOrNull()
    }

    private suspend fun fetchMotionArtwork(
        albumId: String,
        storefront: String,
        fallbackArtist: String?,
        titleOverride: String? = null,
        artistOverride: String? = null,
    ): CanvasArtwork? {
        if (albumId.startsWith("pl.")) {
            Log.d("fetchMotionArtwork: ignoring playlist id $albumId")
            return null
        }
        return runCatching {
            Log.d("fetching album $albumId")
            val albumUrl = "$AMP_BASE_URL/v1/catalog/$storefront/albums/$albumId"
            var token = ensureTokenFresh()
            var response =
                client.get(albumUrl) {
                    header("Authorization", "Bearer $token")
                    header("Origin", "https://music.apple.com")
                    header("Referer", "https://music.apple.com/")
                    header("User-Agent", APPLE_MUSIC_WEB_UA)
                    parameter("extend", "editorialVideo")
                    parameter("include", "tracks")
                }
            if (response.status == HttpStatusCode.Unauthorized) {
                Log.w("album fetch returned 401 — force-refreshing token and retrying once")
                token = refreshToken() ?: token
                response =
                    client.get(albumUrl) {
                        header("Authorization", "Bearer $token")
                        header("Origin", "https://music.apple.com")
                        header("Referer", "https://music.apple.com/")
                        header("User-Agent", APPLE_MUSIC_WEB_UA)
                        parameter("extend", "editorialVideo")
                        parameter("include", "tracks")
                    }
            }
            if (response.status != HttpStatusCode.OK) {
                Log.w("album fetch failed for $albumId: ${response.status}")
                return@runCatching null
            }

            val root = response.body<JsonObject>()
            val data = root["data"]?.jsonArray
            if (data.isNullOrEmpty()) return@runCatching null

            val albumObj = data.firstOrNull()?.jsonObject ?: return@runCatching null
            val attributes = albumObj["attributes"]?.jsonObject
            val albumName = attributes?.get("name")?.jsonPrimitive?.contentOrNull ?: ""
            val artistName = attributes?.get("artistName")?.jsonPrimitive?.contentOrNull ?: fallbackArtist

            val nameLower = albumName.lowercase(Locale.ROOT)
            val isBlacklisted =
                nameLower.contains("playlist") || nameLower.contains("set list") ||
                    nameLower.contains("essentials") || nameLower.contains("dj mix") ||
                    nameLower.contains("mixed") || nameLower.contains("apple music") ||
                    nameLower.contains("today's hits") || nameLower.contains("session")
            if (isBlacklisted) {
                Log.d("fetchMotionArtwork: ignoring blacklisted album '$albumName' ($albumId)")
                return@runCatching null
            }

            val finalTitle = titleOverride ?: albumName
            val finalArtist = artistOverride ?: artistName

            val ev = attributes?.get("editorialVideo")?.jsonObject
            if (ev != null) {
                val videoUrls = extractEditorialVideoUrls(ev)
                if (!videoUrls.animated.isNullOrBlank() || !videoUrls.animatedVertical.isNullOrBlank()) {
                    Log.d("found editorialVideo for $finalTitle (album: $albumName, id: $albumId)")
                    return@runCatching CanvasArtwork(
                        name = finalTitle,
                        artist = finalArtist,
                        albumId = albumId,
                        albumName = albumName,
                        animated = videoUrls.animated,
                        animatedVertical = videoUrls.animatedVertical,
                    )
                }
            }

            Log.d("no editorialVideo for $albumId (available keys: ${attributes?.keys})")
            null
        }.onFailure {
            if (it is CancellationException) throw it
            Log.e(it, "error in fetchMotionArtwork for $albumId")
        }.getOrNull()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────

    /**
     * Scores and filters a single search result item.
     * Returns null if the item should be excluded from consideration.
     */
    private fun scoreAndFilterItem(
        obj: JsonObject,
        term: String,
        artist: String,
        album: String?,
    ): Pair<Int, JsonObject>? {
        val attributes = obj["attributes"]?.jsonObject ?: return null
        val resultArtistName = attributes["artistName"]?.jsonPrimitive?.contentOrNull ?: ""
        val resultName = attributes["name"]?.jsonPrimitive?.contentOrNull ?: ""
        val resultCollectionName = attributes["collectionName"]?.jsonPrimitive?.contentOrNull ?: ""

        val nameLower = resultName.lowercase(Locale.ROOT)
        val collectionLower = resultCollectionName.lowercase(Locale.ROOT)
        val isBlacklisted =
            nameLower.contains("playlist") || nameLower.contains("set list") ||
                collectionLower.contains("playlist") || collectionLower.contains("set list") ||
                nameLower.contains("essentials") || collectionLower.contains("essentials") ||
                collectionLower.contains("dj mix") || collectionLower.contains("mixed") ||
                collectionLower.contains("apple music") || collectionLower.contains("today's hits") ||
                nameLower.contains("session") || collectionLower.contains("session")
        if (isBlacklisted) {
            Log.d("  - Skipping blacklisted result: '$resultName' (Album: '$resultCollectionName')")
            return null
        }

        val artistMatch = resultArtistName.equals(artist, ignoreCase = true)
        val artistFuzzy =
            resultArtistName.contains(artist, ignoreCase = true) ||
                artist.contains(resultArtistName, ignoreCase = true)
        if (!artistFuzzy) return null

        var score = if (artistMatch) 10 else 5

        val nameMatch = resultName.equals(term, ignoreCase = true)
        val nameFuzzy = resultName.contains(term, ignoreCase = true) || term.contains(resultName, ignoreCase = true)
        score +=
            when {
                nameMatch -> 15
                nameFuzzy -> 7
                else -> -10
            }

        // Special editions handling (Deluxe, Expanded, etc.)
        val editionWords = listOf("deluxe", "expanded", "remastered", "remix", "version", "edit", "mix", "bonus")
        for (word in editionWords) {
            val inTerm = term.contains(word, ignoreCase = true)
            val inResult = resultName.contains(word, ignoreCase = true)
            score +=
                when {
                    inTerm && inResult -> 5
                    inTerm != inResult && inResult -> -3
                    else -> 0
                }
        }

        // Album matching — very strong signal
        if (!album.isNullOrBlank() && resultCollectionName.isNotBlank()) {
            val albumMatch = resultCollectionName.equals(album, ignoreCase = true)
            val albumFuzzy =
                resultCollectionName.contains(album, ignoreCase = true) ||
                    album.contains(resultCollectionName, ignoreCase = true)
            score +=
                when {
                    albumMatch -> 20
                    albumFuzzy -> 10
                    else -> 0
                }
        }

        Log.d("  - Result: '$resultName' by '$resultArtistName' (Album: '$resultCollectionName', ID: ${obj["id"]}) -> Score: $score")
        return score to obj
    }

    /**
     * Resolves the Apple Music album ID from a search result item.
     * Handles both song and album result types, with URL-parsing as a last resort.
     */
    private fun resolveAlbumId(
        obj: JsonObject,
        attributes: JsonObject,
        itemType: String?,
        resultName: String,
    ): String? {
        if (itemType == "albums") return obj["id"]?.jsonPrimitive?.contentOrNull
        if (itemType != "songs") return null

        val relationships = obj["relationships"]?.jsonObject
        var albumId =
            relationships
                ?.get("albums")
                ?.jsonObject
                ?.get("data")
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?.get("id")
                ?.jsonPrimitive
                ?.contentOrNull
                ?: attributes["collectionId"]?.jsonPrimitive?.contentOrNull

        // Fallback: parse album ID from the track URL
        // URL format: https://music.apple.com/region/album/name/ID?i=songId
        if (albumId == null) {
            val url = attributes["url"]?.jsonPrimitive?.contentOrNull
            if (url != null) {
                val albumPart = url.substringAfter("/album/", "").substringBefore("?")
                val id = albumPart.substringAfterLast("/", "")
                if (id.isNotBlank() && id.all { it.isDigit() }) albumId = id
            }
        }

        if (albumId == null) Log.d("relationships keys for $resultName: ${relationships?.keys}")
        return albumId
    }

    private data class EditorialVideoUrls(
        val animated: String?,
        val animatedVertical: String?,
    )

    private fun extractEditorialVideoUrls(ev: JsonObject): EditorialVideoUrls {
        fun JsonObject.videoUrl(): String? =
            this["video"]?.jsonPrimitive?.contentOrNull
                ?: this["videoUrl"]?.jsonPrimitive?.contentOrNull
                ?: this["hlsUrl"]?.jsonPrimitive?.contentOrNull
                ?: this["url"]?.jsonPrimitive?.contentOrNull

        val raw = ev["motionDetailRaw"]?.jsonObject?.videoUrl()
        val square = ev["motionDetailSquare"]?.jsonObject?.videoUrl()
        val tall = ev["motionDetailTall"]?.jsonObject?.videoUrl()
        val static = ev["motionDetailStatic"]?.jsonObject?.videoUrl()
        val animated = raw ?: square ?: static ?: tall

        if (animated.isNullOrBlank() && tall.isNullOrBlank()) {
            Log.d("editorialVideo found but no video link in assets: ${ev.keys}")
        }

        return EditorialVideoUrls(
            animated = animated,
            animatedVertical = tall,
        )
    }
}
