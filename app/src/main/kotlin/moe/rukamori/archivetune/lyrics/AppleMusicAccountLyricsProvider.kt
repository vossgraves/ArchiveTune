/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.lyrics

import android.content.Context
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import moe.rukamori.archivetune.canvas.AppleMusicProvider
import moe.rukamori.archivetune.constants.AppleMusicMediaUserTokenKey
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import timber.log.Timber

/**
 * Apple Music lyrics via the user's own Media-User-Token.
 * Uses the official amp-api.music.apple.com lyrics endpoint discovered
 * with the ES test account: `/v1/catalog/{storefront}/songs/{id}/lyrics`.
 * The requested catalog id is resolved via search with the same tokens,
 * and the TTML is converted to LRC. No pool, no Paxsenix.
 */
object AppleMusicAccountLyricsProvider : LyricsProvider {
    override val name = "Apple Music"

    override fun isEnabled(context: Context): Boolean {
        // Only show when the user actually pasted a Media User Token (0.Ap...)
        // The dev JWT is optional because the app has a fallback web token.
        val token = context.dataStore[AppleMusicMediaUserTokenKey]?.trim().orEmpty()
        return token.isNotBlank()
    }

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> = runCatching {
        val ttml = fetchTtml(title, artist, album) ?: throw IllegalStateException("No Apple Music lyrics for $title — $artist")
        ttmlToLrc(ttml)
    }

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
        callback: (String) -> Unit,
    ) {
        getLyrics(id, title, artist, album, duration).onSuccess(callback)
    }

    // ── Network ──
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    private val client by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout) {
                connectTimeoutMillis = 12_000
                requestTimeoutMillis = 18_000
                socketTimeoutMillis = 18_000
            }
            expectSuccess = false
        }
    }

    private const val AMP_BASE = "https://amp-api.music.apple.com"
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36"

    private suspend fun fetchTtml(title: String, artist: String, album: String?): String? {
        // Resolve Apple Music song id via search with the user's tokens.
        val storefront = resolveStorefront()
        val devToken = AppleMusicProvider.devTokenProvider?.invoke() ?: fetchFallbackToken()
        val mediaToken = AppleMusicProvider.mediaUserTokenProvider?.invoke()?.trim() ?: return null
        if (mediaToken.isBlank()) return null

        val query = if (title.contains(artist, ignoreCase = true)) title else "$artist $title"
        val searchResp = client.get("$AMP_BASE/v1/catalog/$storefront/search") {
            header("Authorization", "Bearer $devToken")
            header("Media-User-Token", mediaToken)
            header("Origin", "https://music.apple.com")
            header("Referer", "https://music.apple.com/")
            header("User-Agent", UA)
            parameter("term", query)
            parameter("types", "songs")
            parameter("limit", "5")
        }
        if (!searchResp.status.isSuccess()) {
            Timber.tag("AppleMusicLyrics").w("search failed ${searchResp.status}")
            return null
        }
        val root = searchResp.body<JsonObject>()
        val songs = root["results"]?.jsonObject?.get("songs")?.jsonObject?.get("data")?.jsonArray ?: return null
        val best = songs.firstOrNull()?.jsonObject ?: return null
        val songId = best["id"]?.jsonPrimitive?.contentOrNull ?: return null

        // Try syllable-lyrics first (word sync), fall back to lyrics (line sync).
        for (ep in listOf("syllable-lyrics", "lyrics")) {
            val resp = client.get("$AMP_BASE/v1/catalog/$storefront/songs/$songId/$ep") {
                header("Authorization", "Bearer $devToken")
                header("Media-User-Token", mediaToken)
                header("Origin", "https://music.apple.com")
                header("Referer", "https://music.apple.com/")
                header("User-Agent", UA)
            }
            if (!resp.status.isSuccess()) continue
            val body = resp.body<JsonObject>()
            val ttml = body["data"]?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("attributes")?.jsonObject?.get("ttml")?.jsonPrimitive?.contentOrNull
                ?: body["data"]?.jsonArray?.firstOrNull()?.jsonObject?.get("attributes")?.jsonObject
                    ?.get("ttml")?.jsonPrimitive?.contentOrNull
            if (!ttml.isNullOrBlank()) return ttml
            // Alternative: body is already TTML string?
            val raw = resp.bodyAsText()
            if (raw.contains("<tt")) return raw
        }
        return null
    }

    private suspend fun resolveStorefront(): String {
        // Reuse AppleMusicProvider's resolved storefront via reflection fallback to "us".
        // We go through the same /v1/me/storefront so ES token hits "es".
        return try {
            // Access via public method if we expose it; for now duplicate logic:
            val media = AppleMusicProvider.mediaUserTokenProvider?.invoke()?.trim()?.takeIf { it.isNotBlank() } ?: return "us"
            val dev = AppleMusicProvider.devTokenProvider?.invoke()?.takeIf { it.isNotBlank() } ?: fetchFallbackToken()
            val resp = client.get("$AMP_BASE/v1/me/storefront") {
                header("Authorization", "Bearer $dev")
                header("Media-User-Token", media)
                header("Origin", "https://music.apple.com")
                header("Referer", "https://music.apple.com/")
                header("User-Agent", UA)
            }
            if (!resp.status.isSuccess()) return "us"
            val root = resp.body<JsonObject>()
            root["data"]?.jsonArray?.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull ?: "us"
        } catch (_: Exception) {
            "us"
        }
    }

    private suspend fun fetchFallbackToken(): String {
        // Use the fallback from AppleMusicProvider via ensureToken path – simplest is to trigger provider's token.
        // We can't call private ensureTokenFresh, so scrape fallback directly from AppleMusicProvider's constant via reflection?
        // Fallback to hardcoded known-good token as last resort (same as canvas fallback).
        return AppleMusicProvider.devTokenProvider?.invoke()?.takeIf { it.isNotBlank() }
            ?: "eyJ0eXAiOiJKV1QiLCJhbGciOiJFUzI1NiIsImtpZCI6IldlYlBsYXlLaWQifQ.eyJpc3MiOiJBTVBXZWJQbGF5IiwiaWF0IjoxNzg2NjMyOTI0LCJleHAiOjE3OTI2ODA5MjQsInJvb3RfaHR0cHNfb3JpZ2luIjpbImFwcGxlLmNvbSJdfQ.hBgj61sZf-y7bmuvT-joXAUAcf7TVJ51732xnH5vFkLHOmsQHxVqGMYUuI4h8c0-RX3fRY3moylhLW8fewFJyw"
    }

    private fun ttmlToLrc(ttml: String): String {
        // TTML <p begin="27.395" end="28.960">I been tryna call</p> -> [00:27.39]I been tryna call
        // Also supports word-level <span> – we flatten to line text.
        val pRegex = Regex("""<p[^>]*begin="([^"]+)"[^>]*>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)
        val spanRegex = Regex("""<span[^>]*>.*?</span>""")
        val sb = StringBuilder()
        for (m in pRegex.findAll(ttml)) {
            val begin = m.groupValues[1]
            var text = m.groupValues[2]
            // Strip inner spans but keep text
            text = text.replace(Regex("""<span[^>]*>"""), "")
                .replace("</span>", " ")
                .replace(Regex("""<[^>]+>"""), "")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .trim()
                .replace(Regex("""\s+"""), " ")
            if (text.isBlank()) continue
            val sec = parseTimeSec(begin)
            sb.append(formatLrc(sec)).append(text).append('\n')
        }
        if (sb.isEmpty()) {
            // Fallback: extract any text between tags if no <p> found
            return ttml.replace(Regex("""<[^>]+>"""), "\n").trim()
        }
        return sb.toString().trimEnd()
    }

    private fun parseTimeSec(raw: String): Double {
        // "27.395" or "1:00.964" or "1:02:03.123"
        val parts = raw.split(":")
        return try {
            when (parts.size) {
                1 -> parts[0].toDouble()
                2 -> parts[0].toDouble() * 60 + parts[1].toDouble()
                3 -> parts[0].toDouble() * 3600 + parts[1].toDouble() * 60 + parts[2].toDouble()
                else -> 0.0
            }
        } catch (_: Exception) { 0.0 }
    }

    private fun formatLrc(sec: Double): String {
        val totalMs = (sec * 1000).toLong()
        val min = totalMs / 60000
        val secPart = (totalMs % 60000) / 1000
        val ms = totalMs % 1000
        return String.format("[%02d:%02d.%02d]", min, secPart, ms / 10)
    }
}
