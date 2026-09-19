/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.qqmusic

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.constants.QqAudioQuality
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * QQ Music playback — Tencent's partner program only.
 *
 * QQ Music's catalogue and playback are available through the TME partner programme (OpenAPI /
 * QPlay), which issues an app id, an app key and a signing scheme to approved partners. There is no
 * public personal-developer playback API. This file therefore talks to whatever endpoint the
 * maintainer's partnership documents, with the signing the partnership specifies, and to nothing
 * else:
 *
 *  - no `u.y.qq.com` musicu.fcg call and no vkey construction from a leaked scheme — those are the
 *    unofficial paths this source is explicitly not built on;
 *  - no decryption of `mflac`/`mgg` or any other encrypted container. When the API offers only an
 *    encrypted format for a track, that track is reported unavailable and playback falls through,
 *    which is the boundary-compliant outcome rather than a limitation to work around;
 *  - no ad or limit circumvention: non-VIP qualities are simply not requested.
 *
 * **Inert by default.** [BuildConfig.QQ_PARTNER_APP_ID] and [BuildConfig.QQ_PARTNER_API_BASE] are
 * blank in every build that has not been given a partnership, and every entry point returns empty
 * or null in that case. There is deliberately no baked-in default host: naming a QQ endpoint this
 * repository cannot verify would be inventing an integration, and the partner's own documentation
 * is the only correct source for it.
 */
object QqMusicProvider {
    private const val TAG = "QqMusicProvider"

    /** True when this build carries a partner application and the endpoint its documents specify. */
    fun isConfigured(): Boolean =
        BuildConfig.QQ_PARTNER_APP_ID.isNotBlank() && BuildConfig.QQ_PARTNER_API_BASE.isNotBlank()

    /**
     * One catalogue hit.
     *
     * [mid] is the song's catalogue id, which is what playback is requested by. [title]/[artist] are
     * what the playback layer's match gate needs; a hit that arrives without them cannot be verified
     * and is declined by the caller unless the user picked this source by hand.
     */
    data class QqCandidate(
        val mid: String,
        val title: String?,
        val artist: String?,
        val album: String?,
        val durationMs: Long?,
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client =
        OkHttpClient
            .Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

    /**
     * The partner signature: HMAC-SHA256 over the request's parameters in the documented canonical
     * order, hex-encoded, using the partner app key.
     *
     * Kept in one place with an explicit parameter list so the canonicalisation is reviewable — a
     * signing bug is the one failure here that returns a *plausible* response rather than an error,
     * so it is the part that most deserves to be pinned by a test.
     */
    internal fun sign(
        appId: String,
        appKey: String,
        timestampSeconds: Long,
        params: Map<String, String>,
    ): String {
        val canonical =
            buildString {
                append("app_id=").append(appId)
                append("&timestamp=").append(timestampSeconds)
                params.toSortedMap().forEach { (key, value) ->
                    append('&').append(key).append('=').append(value)
                }
            }
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(appKey.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(canonical.toByteArray(Charsets.UTF_8)).joinToString("") { byte ->
            "%02x".format(byte)
        }
    }

    /** Catalogue search. Empty when unconfigured or when the response is not a shape we understand. */
    suspend fun searchCandidates(
        query: String,
        quality: QqAudioQuality,
        limit: Int = 5,
    ): List<QqCandidate> =
        withContext(Dispatchers.IO) {
            if (!isConfigured()) return@withContext emptyList()
            runCatching {
                val params = mapOf("keyword" to query, "num" to limit.toString(), "quality" to quality.name)
                val url = buildUrl("search", params) ?: return@withContext emptyList()
                val request =
                    Request
                        .Builder()
                        .url(url)
                        .header("User-Agent", "ArchiveTune/1.0")
                        .get()
                        .build()
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        Log.w(TAG, "search failed: HTTP ${response.code} ${text.take(200)}")
                        return@use emptyList()
                    }
                    collectCandidates(text, limit)
                }
            }.getOrElse { error ->
                Log.w(TAG, "search error: ${error.message}")
                emptyList()
            }
        }

    /**
     * Playback URL for [mid], or null when it cannot be played within the boundaries.
     *
     * Null means one of: unconfigured, no URL in the response, or the API offering the track only in
     * an encrypted container — the last is reported as unavailable rather than decrypted.
     */
    suspend fun resolveStream(
        mid: String,
        quality: QqAudioQuality,
    ): String? =
        withContext(Dispatchers.IO) {
            if (!isConfigured()) return@withContext null
            runCatching {
                val params = mapOf("mid" to mid, "quality" to quality.name)
                val url = buildUrl("playback", params) ?: return@withContext null
                val request =
                    Request
                        .Builder()
                        .url(url)
                        .header("User-Agent", "ArchiveTune/1.0")
                        .get()
                        .build()
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        Log.w(TAG, "playback failed: HTTP ${response.code} ${text.take(200)}")
                        return@use null
                    }
                    val found = firstStringNamed(text, "url", "playUrl", "streamUrl", "src")
                    if (found == null || !found.startsWith("http")) {
                        Log.i(TAG, "no playable URL for $mid (encrypted-only or unavailable)")
                        return@use null
                    }
                    // Encrypted containers are never fetched: they cannot be played without
                    // decrypting them, and decrypting them is out of bounds.
                    if (found.endsWith(".mflac") || found.endsWith(".mgg") || found.endsWith(".mgg1")) {
                        Log.i(TAG, "track $mid is offered only encrypted — declining")
                        return@use null
                    }
                    found
                }
            }.getOrElse { error ->
                Log.w(TAG, "playback error: ${error.message}")
                null
            }
        }

    /** Builds a signed request URL for [path] under the partner's documented base. */
    private fun buildUrl(
        path: String,
        params: Map<String, String>,
    ) = runCatching {
        val base = "${BuildConfig.QQ_PARTNER_API_BASE.trimEnd('/')}/$path".toHttpUrlOrNull()
            ?: return@runCatching null
        val timestamp = System.currentTimeMillis() / 1000
        val signature = sign(BuildConfig.QQ_PARTNER_APP_ID, BuildConfig.QQ_PARTNER_APP_KEY, timestamp, params)
        base
            .newBuilder()
            .addQueryParameter("app_id", BuildConfig.QQ_PARTNER_APP_ID)
            .addQueryParameter("timestamp", timestamp.toString())
            .addQueryParameter("sign", signature)
            .apply { params.toSortedMap().forEach { (key, value) -> addQueryParameter(key, value) } }
            .build()
    }.getOrNull()

    /** Every track-shaped object in the response, in document order, deduplicated by id. */
    private fun collectCandidates(
        text: String,
        limit: Int,
    ): List<QqCandidate> {
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() ?: return emptyList()
        val out = LinkedHashMap<String, QqCandidate>()

        fun textOf(
            obj: kotlinx.serialization.json.JsonObject,
            vararg names: String,
        ): String? {
            for (name in names) {
                obj[name]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
            }
            return null
        }

        fun walk(element: kotlinx.serialization.json.JsonElement) {
            when (element) {
                is kotlinx.serialization.json.JsonObject -> {
                    val mid = textOf(element, "mid", "songmid", "id")
                    if (mid != null && !out.containsKey(mid)) {
                        out[mid] =
                            QqCandidate(
                                mid = mid,
                                title = textOf(element, "title", "songname", "name"),
                                artist = textOf(element, "singer", "artist", "singername"),
                                album = textOf(element, "album", "albumname"),
                                durationMs =
                                    textOf(element, "interval", "duration", "durationMs")
                                        ?.toLongOrNull()
                                        // `interval` is seconds in Tencent's schema; anything
                                        // under a few hours is a duration, not a millisecond stamp.
                                        ?.let { if (it in 1..36_000) it * 1_000 else it },
                            )
                    }
                    for ((_, child) in element) walk(child)
                }

                is kotlinx.serialization.json.JsonArray -> element.jsonArray.forEach(::walk)

                else -> Unit
            }
        }
        walk(root)
        return out.values.take(limit)
    }

    private fun firstStringNamed(
        text: String,
        vararg names: String,
    ): String? {
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() ?: return null
        fun walk(element: kotlinx.serialization.json.JsonElement): String? =
            when (element) {
                is kotlinx.serialization.json.JsonObject -> {
                    for (name in names) {
                        element[name]?.jsonPrimitive?.contentOrNull
                            ?.takeIf { it.isNotBlank() }
                            ?.let { return it }
                    }
                    for ((_, child) in element) walk(child)?.let { return it }
                    null
                }

                is kotlinx.serialization.json.JsonArray -> {
                    for (child in element.jsonArray) walk(child)?.let { return it }
                    null
                }

                else -> null
            }
        return walk(root)
    }
}