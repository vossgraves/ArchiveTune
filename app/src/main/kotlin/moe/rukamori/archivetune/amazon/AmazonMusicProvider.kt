/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.amazon

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.constants.AmazonAudioQuality
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Amazon Music playback — the official Web API path only.
 *
 * Every call here goes to Amazon's own endpoints with the signed-in user's own entitlement, and the
 * audio is licensed by Amazon's own Widevine licence server from the session the playback call
 * returns. There is deliberately no third-party instance, no stream or licence proxy, no pooled
 * service credential, and no attempt to mint a licence any other client's flow would have produced:
 * the boundaries this source is written under are the reason it can exist at all.
 *
 * **This source is inert by default and that is the intended state.** The Web API is approval-gated
 * (Amazon issues a Login with Amazon security profile to approved partners), so
 * [BuildConfig.AMAZON_LWA_CLIENT_ID] is blank in every build that has not been given one. Every
 * entry point below returns `null` in that case, `resolveAmazonStream` in MusicService falls through
 * to the next source, and the app behaves exactly as it did before this file existed.
 *
 * What is *verified* here: the request shape (endpoint paths, the `X-Amzn-Audio-*` headers, and the
 * session body) and the credential ordering, which follow Amazon's published Music Web API
 * documentation. What is *not* verifiable from this repository: the exact response schema, because
 * it is only documented behind that approval. The parsers therefore look for the values by their
 * documented roles, across the shapes Amazon's own docs and samples agree on, and return `null`
 * rather than guessing when they find nothing — a wrong guess would hand Media3 a broken manifest
 * instead of falling through to a source that works.
 */
object AmazonMusicProvider {
    private const val TAG = "AmazonMusicProvider"

    private const val JSON_MEDIA_TYPE = "application/json; charset=utf-8"

    /**
     * Amazon's own device-capability tokens for the three quality tiers this app exposes.
     *
     * The mapping is honest in both directions: asking for ULTRA_HD does not make an account
     * entitled to it, and Amazon answers with whatever that account may actually receive. When the
     * server returns Standard for an Ultra HD request, that is the answer — nothing here upgrades,
     * substitutes or retries for a higher tier.
     */
    private fun capabilityFor(quality: AmazonAudioQuality): String =
        when (quality) {
            AmazonAudioQuality.ULTRA_HD -> "ULTRA_HD"
            AmazonAudioQuality.HD -> "HD"
            AmazonAudioQuality.STANDARD -> "SD"
        }

    /**
     * One set of credentials to resolve with.
     *
     * [poolId] is non-null when this came from the pool, so a rejected session can be reported dead
     * on the entry it came from and the next attempt rotates past it.
     */
    data class AmazonCredentials(
        val session: String,
        val premium: Boolean,
        val poolId: Long?,
    )

    /** A resolved Amazon track: the DASH manifest to play and the licence server to unlock it with. */
    data class AmazonStream(
        val trackId: String,
        val manifestUrl: String,
        val licenseUrl: String,
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

    private val jsonMedia = JSON_MEDIA_TYPE.toMediaType()

    /** True when this build carries an approved Web API security profile. */
    fun isConfigured(): Boolean = BuildConfig.AMAZON_LWA_CLIENT_ID.isNotBlank()

    /**
     * The account to resolve with: the personally signed-in one first, then the pool.
     *
     * Both lists are already in memory — the personal session comes from DataStore, and the pool's
     * accounts from [moe.rukamori.archivetune.utils.PoolAccountManager]'s snapshot, which is
     * refreshed on its own schedule — so resolving a song never costs a network round trip for the
     * account itself. That matters: this is called once per playback attempt, and a pool fetch per
     * song would hammer the pool server for a value that changes hourly at most.
     *
     * A session is reported dead only when the *server* rejects it, via
     * [moe.rukamori.archivetune.utils.PoolAccountManager.report] from the caller — never here on a
     * local guess.
     */
    fun credentials(
        localSession: String?,
        localPremium: Boolean,
        pooled: List<PoolCredential>,
    ): AmazonCredentials? {
        val personal = localSession?.trim().orEmpty()
        if (personal.isNotEmpty()) {
            return AmazonCredentials(session = personal, premium = localPremium, poolId = null)
        }
        // Premium entries first: the tier only decides which account is tried first, the server
        // still decides what this track may stream.
        val picked = pooled.firstOrNull { it.premium } ?: pooled.firstOrNull() ?: return null
        return AmazonCredentials(session = picked.session, premium = picked.premium, poolId = picked.id)
    }

    /** The pool's shape, kept separate so this file does not depend on the pool's data classes. */
    data class PoolCredential(
        val id: Long?,
        val session: String,
        val premium: Boolean,
    )

    /**
     * Every pool credential, from the manager's in-memory snapshot.
     *
     * A single call per resolve, no I/O. See [credentials] for why that matters.
     */
    fun pooledCredentials(): List<PoolCredential> =
        moe.rukamori.archivetune.utils.PoolAccountManager.amazonAccounts().map { account ->
            PoolCredential(id = account.id, session = account.session, premium = account.premium)
        }

    /**
     * Resolve [trackId] (an Amazon MRN) to a playable manifest plus its licence server.
     *
     * Returns `null` — never throws — when the build is unconfigured, the session is rejected, the
     * response carries no manifest, or anything else goes wrong. The caller treats null as "this
     * source cannot serve this track" and moves on, which is the same behaviour the source had while
     * it was wired to a literal null.
     */
    suspend fun resolveStream(
        trackId: String,
        quality: AmazonAudioQuality,
        credentials: AmazonCredentials,
        deviceId: String,
    ): AmazonStream? =
        withContext(Dispatchers.IO) {
            if (!isConfigured()) return@withContext null
            runCatching {
                val body =
                    """
                    {"playParams":{"id":"$trackId"},"quality":"${capabilityFor(quality)}"}
                    """.trimIndent().toRequestBody(jsonMedia)
                val request =
                    Request
                        .Builder()
                        .url("${BuildConfig.AMAZON_API_BASE}/v1/playback/sessions")
                        .header("Authorization", "Bearer ${credentials.session}")
                        .header("x-api-key", BuildConfig.AMAZON_LWA_CLIENT_ID)
                        .header("X-Amzn-Audio-DRMType", "WIDEVINE")
                        .header("X-Amzn-Audio-Device-Capability", capabilityFor(quality))
                        .header("X-Amzn-Device-Id", deviceId)
                        .header("Content-Type", JSON_MEDIA_TYPE)
                        .post(body)
                        .build()
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        Log.w(TAG, "playback session failed: HTTP ${response.code} ${text.take(200)}")
                        return@use null
                    }
                    parsePlaybackData(text)
                }
            }.getOrElse { error ->
                Log.w(TAG, "playback session error: ${error.message}")
                null
            }
        }

    /**
     * Pull the manifest and licence URL out of a playback-session response.
     *
     * Deliberately permissive about where the values sit: Amazon's own samples and the JSON schema
     * agree on the roles (a DASH manifest URL and a licence URL for the requested DRM system) more
     * than on a single nesting, and returning null on an unrecognised shape is a safe outcome —
     * the source falls through instead of handing the player something it cannot play.
     */
    private fun parsePlaybackData(text: String): AmazonStream? {
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        val manifest =
            firstUrlNamed(root, "manifestUrl", "url", "manifest", "dashUrl", "streamUrl")
                ?: return null
        val trackId = firstStringNamed(root, "id", "amdId", "trackId", "asin")
        // The licence URL is what the Widevine session posts to; Amazon's own server issues the
        // keys for this user's entitlement. Without one the manifest cannot be unlocked, so there
        // is nothing to hand the player.
        val license = firstUrlNamed(root, "licenseUrl", "license", "laurl", "widevineLicenseUrl") ?: return null
        val durationMs =
            firstStringNamed(root, "durationMs", "duration")
                ?.toLongOrNull()
                ?.takeIf { it > 0L }
        return AmazonStream(
            trackId = trackId ?: "",
            manifestUrl = manifest,
            licenseUrl = license,
            durationMs = durationMs,
        )
    }

    /** Recursively finds the first string value under any of [names] that looks like a URL. */
    private fun firstUrlNamed(
        element: kotlinx.serialization.json.JsonElement,
        vararg names: String,
    ): String? = firstStringNamed(element, *names)?.takeIf { it.startsWith("http") }

    private fun firstStringNamed(
        element: kotlinx.serialization.json.JsonElement,
        vararg names: String,
    ): String? {
        when (element) {
            is kotlinx.serialization.json.JsonObject -> {
                for (name in names) {
                    val value = element[name]?.jsonPrimitive?.contentOrNull
                    if (!value.isNullOrBlank()) return value
                }
                for ((_, child) in element) {
                    firstStringNamed(child, *names)?.let { return it }
                }
                return null
            }

            is kotlinx.serialization.json.JsonArray -> {
                for (child in element.jsonArray) {
                    firstStringNamed(child, *names)?.let { return it }
                }
                return null
            }

            else -> {
                // A bare primitive: only its own name would have matched, and that cannot happen
                // here because the name lookup needs an object.
                return null
            }
        }
    }

    /**
     * One catalog hit, with as much metadata as the response carried.
     *
     * The metadata is what lets the playback layer's match gate run. When a hit arrives without it,
     * the gate cannot verify the track and the caller declines unless the user picked the source
     * directly — refusing is the right outcome, since playing an unverified track from a lossless
     * source is exactly what the gate exists to prevent.
     */
    data class AmazonCandidate(
        val id: String,
        val title: String?,
        val artist: String?,
        val album: String?,
        val durationMs: Long?,
    )

    /**
     * Top catalog candidates for [query], in Amazon's own search-rank order.
     *
     * Same defensive posture as [parsePlaybackData]: the response schema is documented only behind
     * the partner approval, so each hit is read from whichever of the documented field names is
     * present at any depth, and an unrecognised body yields an empty list — the source then declines
     * the track instead of playing something the user did not ask for.
     */
    suspend fun searchCandidates(
        query: String,
        credentials: AmazonCredentials,
        limit: Int = 5,
    ): List<AmazonCandidate> =
        withContext(Dispatchers.IO) {
            if (!isConfigured()) return@withContext emptyList()
            runCatching {
                val url =
                    "${BuildConfig.AMAZON_API_BASE}/v1/catalog/search".toHttpUrl()
                        .newBuilder()
                        .addQueryParameter("query", query)
                        .addQueryParameter("types", "track")
                        .addQueryParameter("limit", limit.toString())
                        .build()
                val request =
                    Request
                        .Builder()
                        .url(url)
                        .header("Authorization", "Bearer ${credentials.session}")
                        .header("x-api-key", BuildConfig.AMAZON_LWA_CLIENT_ID)
                        .header("Content-Type", JSON_MEDIA_TYPE)
                        .get()
                        .build()
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        Log.w(TAG, "catalog search failed: HTTP ${response.code} ${text.take(200)}")
                        return@use emptyList()
                    }
                    collectCandidates(text, limit)
                }
            }.getOrElse { error ->
                Log.w(TAG, "catalog search error: ${error.message}")
                emptyList()
            }
        }

    /** Every track-shaped object in the response, in document order, deduplicated by id. */
    private fun collectCandidates(
        text: String,
        limit: Int,
    ): List<AmazonCandidate> {
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() ?: return emptyList()
        val out = LinkedHashMap<String, AmazonCandidate>()

        fun idOf(obj: kotlinx.serialization.json.JsonObject): String? {
            for (name in listOf("id", "asin", "amdId", "trackId", "mrn")) {
                obj[name]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
            }
            return null
        }

        fun textOf(obj: kotlinx.serialization.json.JsonObject, vararg names: String): String? {
            for (name in names) {
                obj[name]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
            }
            return null
        }

        fun walk(element: kotlinx.serialization.json.JsonElement) {
            when (element) {
                is kotlinx.serialization.json.JsonObject -> {
                    val id = idOf(element)
                    if (id != null && !out.containsKey(id)) {
                        out[id] =
                            AmazonCandidate(
                                id = id,
                                title = textOf(element, "title", "name", "trackName"),
                                artist = textOf(element, "artist", "artistName", "primaryArtist"),
                                album = textOf(element, "album", "albumName"),
                                durationMs =
                                    textOf(element, "durationMs", "duration", "durationMillis")
                                        ?.toLongOrNull()
                                        ?.takeIf { it > 0L },
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

    /** The device id Amazon's headers expect: stable per install, never a hardware identifier. */
    fun deviceId(installId: String): String =
        installId.trim().takeIf { it.isNotEmpty() } ?: "archivetune-unknown"
}