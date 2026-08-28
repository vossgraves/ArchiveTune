/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.settings

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.AudioSourceType
import moe.rukamori.archivetune.deezer.DeezerAudioProvider
import moe.rukamori.archivetune.jiosaavn.SaavnService
import moe.rukamori.archivetune.qobuz.QobuzAudioProvider
import moe.rukamori.archivetune.qobuz.QobuzToken
import moe.rukamori.archivetune.tidal.TidalAccountManager
import moe.rukamori.archivetune.tidal.TidalAudioProvider
import moe.rukamori.archivetune.utils.PoolAccountManager
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Result of a "Check source" probe. The [summary] is shown in a Toast / dialog body;
 * [healthy] drives the icon (green check / red x) on the row.
 */
data class SourceCheckResult(
    val healthy: Boolean,
    val summary: String,
)

/**
 * Per-source health probe for the "Check source" row in Source Settings.
 *
 * Each source has different infrastructure, so each has its own probe:
 *  - Tidal: refresh the source pool, count Tidal accounts, verify the first
 *    premium pool token against the official Tidal API (this is the path that
 *    actually streams), and report public instances as the optional fallback.
 *  - Qobuz: refresh the pool, count Qobuz accounts (premium first), then
 *    try a real user/get call on the first pool token to verify it works.
 *  - Qobuz backup: ping https://mlc.kouzu.in/api/stream?id=<test_id> with a
 *    HEAD request (the test id is a stable, well-known music video) and verify
 *    it returns an audio content type.
 *  - Deezer: refresh the pool, count both pooled and manually signed-in credentials, then verify
 *    the one the resolver would use first against the Deezer gateway.
 *  - JioSaavn: ping the JioSaavn public search API with a canned query and
 *    verify it returns at least one result.
 *
 * All probes run off the main thread and never throw — failures are caught
 * and reported in the [SourceCheckResult.summary] so the user sees what
 * went wrong.
 */
object SourceCheckService {
    /** Stable, well-known YouTube video id used to probe the kouzu.in backup. */
    private const val KOZU_PROBE_YT_ID = "dQw4w9WgXcQ"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Run the per-source health probe. Returns a [SourceCheckResult] — never throws.
     * [context] is needed for the source pool refresh (which reads / writes the
     * app's DataStore cache).
     */
    suspend fun check(source: AudioSourceType, context: Context): SourceCheckResult =
        withContext(Dispatchers.IO) {
            when (source) {
                AudioSourceType.TIDAL -> checkTidal(context)
                AudioSourceType.QOBUZ -> checkQobuz(context)
                AudioSourceType.QOBUZ_BACKUP -> checkQobuzBackup()
                AudioSourceType.DEEZER -> checkDeezer(context)
                AudioSourceType.JIOSAAVN -> checkJioSaavn()
                AudioSourceType.YOUTUBE -> SourceCheckResult(
                    healthy = true,
                    summary = "YouTube is always available as the fallback source.",
                )
            }
        }

    private suspend fun checkTidal(context: Context): SourceCheckResult {
        // Refresh the pool first so newly-added accounts are visible.
        PoolAccountManager.refresh(context, force = false)
        val accounts = PoolAccountManager.tidalAccounts()
        if (accounts.isEmpty()) {
            return SourceCheckResult(
                healthy = false,
                summary = "No Tidal accounts in the source pool. Tap 'Refresh source pool' at the top, " +
                    "or add your own Tidal token via Integration → Manual source sign-in.",
            )
        }
        val premium = accounts.count { it.premium }

        // Probe the path that actually streams. LosslessStreamResolver.resolveTidal tries the
        // official Tidal API with the pool's subscriber tokens FIRST and only falls back to public
        // HiFi/QQDL instances if every account fails — so a valid premium token means Tidal works
        // with zero instances. Reporting the instance count as the headline verdict (as this check
        // used to) told users Tidal was broken while it was streaming fine.
        val probeAccount = accounts.firstOrNull { it.premium } ?: accounts.first()
        val session = runCatching { TidalAccountManager.buildSessionFromBearer(probeAccount.token) }.getOrNull()
        val subscription =
            session?.userId?.let { userId ->
                runCatching { TidalAccountManager.fetchSubscription(probeAccount.token, userId) }.getOrNull()
            }
        val accountLabel =
            when {
                session == null -> "token rejected by the Tidal API (expired — refresh the source pool)"
                subscription == TidalAccountManager.Subscription.PREMIUM -> "valid (premium — lossless available)"
                subscription == TidalAccountManager.Subscription.FREE -> "valid but FREE (previews only, no lossless)"
                else -> "valid, subscription tier unknown"
            }
        val accountPathReady = session != null && subscription != TidalAccountManager.Subscription.FREE

        // Instances are the optional no-account fallback. Report them as such.
        val healthyInstances = runCatching {
            moe.rukamori.archivetune.tidal.TidalInstanceHealthManager.healthyUrls(context).size
        }.getOrDefault(0)

        val summary = buildString {
            append("Pool accounts: ${accounts.size} ($premium premium)\n")
            append("Account stream path: $accountLabel\n")
            append("Public instances (optional fallback): $healthyInstances healthy")
            if (accountPathReady) {
                append("\n\nTidal source is READY via the account path.")
                if (healthyInstances == 0) {
                    append(
                        " No public instance is reachable, but none is needed — " +
                            "the pool's subscriber token streams directly from Tidal.",
                    )
                }
            } else {
                append("\n\nTidal source is NOT ready: ")
                append(
                    if (healthyInstances > 0) {
                        "the account path failed, so playback will fall back to a public instance " +
                            "(lower quality, may serve previews)."
                    } else {
                        "the account path failed and no public instance is reachable. " +
                            "Tap 'Refresh source pool' to pull fresh tokens, or add a private " +
                            "Tidal instance via Integration."
                    },
                )
            }
        }
        return SourceCheckResult(
            healthy = accountPathReady || healthyInstances > 0,
            summary = summary,
        )
    }

    private suspend fun checkQobuz(context: Context): SourceCheckResult {
        PoolAccountManager.refresh(context, force = false)
        val accounts = PoolAccountManager.qobuzAccounts()
        if (accounts.isEmpty()) {
            return SourceCheckResult(
                healthy = false,
                summary = "No Qobuz accounts in the source pool. Tap 'Refresh source pool' at the top, " +
                    "or add your own Qobuz token (with app_id + app_secret) via Integration → Manual source sign-in.",
            )
        }
        val premium = accounts.count { it.premium }
        // Take the first pool account and try a real Qobuz API call against it
        // (user/get) — this verifies the token + app_id are valid. We can't
        // verify app_secret without signing a stream URL, but user/get proves
        // the account is alive.
        val first = accounts.first()
        val token = QobuzToken(
            token = first.token,
            appId = first.appId,
            appSecret = first.appSecret,
            label = "Source Pool",
            subscription = if (first.premium) "premium" else "",
        )
        val health = QobuzAudioProvider.verifyToken(token, probeTrackId = null, formatId = 5)
        val healthLabel = when (health) {
            moe.rukamori.archivetune.tidal.TidalAudioProvider.InstanceHealth.HEALTHY -> "healthy (premium)"
            moe.rukamori.archivetune.tidal.TidalAudioProvider.InstanceHealth.PREVIEW_ONLY -> "preview-only (no subscription)"
            moe.rukamori.archivetune.tidal.TidalAudioProvider.InstanceHealth.UNREACHABLE -> "unreachable (token invalid / app_secret mismatch)"
            else -> "unknown"
        }
        return SourceCheckResult(
            healthy = health == moe.rukamori.archivetune.tidal.TidalAudioProvider.InstanceHealth.HEALTHY,
            summary = "Pool accounts: ${accounts.size} ($premium premium)\n" +
                "First token probe: $healthLabel\n" +
                "Qobuz source is ${if (health == moe.rukamori.archivetune.tidal.TidalAudioProvider.InstanceHealth.HEALTHY) "READY" else "NOT ready — see above"}.",
        )
    }

    private fun checkQobuzBackup(): SourceCheckResult {
        // The Qobuz backup is a two-step resolver: GET the resolver endpoint to
        // get the actual stream URL on the CDN, then range-probe the CDN URL.
        // NOTE: server addresses are intentionally hidden from the summary text.
        val resolverUrl = "https://mlc-ytify.kouzu.in/api/stream?id=$KOZU_PROBE_YT_ID"
        return runCatching {
            val resolverRequest = Request.Builder()
                .url(resolverUrl)
                .get()
                .header("x-request-source", "muzo")
                .header("User-Agent", "ArchiveTune-Android")
                .header("Accept", "application/json")
                .build()
            client.newCall(resolverRequest).execute().use { resolverResponse ->
                if (!resolverResponse.isSuccessful) {
                    return@runCatching SourceCheckResult(
                        healthy = false,
                        summary = "Qobuz backup resolver returned HTTP ${resolverResponse.code}. " +
                            "The backup server may be down or rate-limiting your IP.",
                    )
                }
                val body = resolverResponse.body?.string().orEmpty()
                if (body.isBlank()) {
                    return@runCatching SourceCheckResult(
                        healthy = false,
                        summary = "Qobuz backup resolver returned an empty body.",
                    )
                }
                val root = runCatching { JSONObject(body) }.getOrNull()
                if (root == null) {
                    return@runCatching SourceCheckResult(
                        healthy = false,
                        summary = "Qobuz backup resolver returned a non-JSON response.",
                    )
                }
                // The resolver returns both a lossy `url` mirror and a `lossless`
                // FLAC mirror. Report on the lossless one first, since that is the
                // reason to use this source at all.
                val losslessUrl = root.optString("lossless").takeIf { it.isNotBlank() }
                val lossyUrl = root.optString("url").takeIf { it.isNotBlank() }
                if (losslessUrl == null && lossyUrl == null) {
                    return@runCatching SourceCheckResult(
                        healthy = false,
                        summary = "Qobuz backup resolver returned a JSON envelope with no stream URL.",
                    )
                }
                // Step 2: range-probe the resolved CDN URL.
                //
                // Deliberately a ranged GET, not HEAD: the CDN answers HEAD with
                // `405 Method Not Allowed` (`allow: GET`) for every object, so the
                // old HEAD probe always reported a scary "HEAD probe got HTTP 405"
                // even when the stream was perfectly playable. `Range: bytes=0-1`
                // downloads two bytes and returns the real Content-Type.
                val losslessProbe = losslessUrl?.let { probeCdn(it) }
                val lossyProbe = if (losslessProbe?.ok == true) null else lossyUrl?.let { probeCdn(it) }
                when {
                    losslessProbe?.ok == true ->
                        SourceCheckResult(
                            healthy = true,
                            summary = "Qobuz backup is reachable and served a lossless stream " +
                                "(${losslessProbe.contentType}${losslessProbe.sizeSuffix()}). " +
                                "Qobuz backup is READY.",
                        )

                    lossyProbe?.ok == true ->
                        SourceCheckResult(
                            healthy = true,
                            summary = "Qobuz backup is reachable but only the lossy mirror served audio " +
                                "(${lossyProbe.contentType}). The backup server has no lossless copy of the " +
                                "probe track yet — lossless will still be used for tracks that have one.",
                        )

                    else -> {
                        val failed = losslessProbe ?: lossyProbe
                        SourceCheckResult(
                            healthy = false,
                            summary = "Qobuz backup resolver returned a stream URL but the CDN served " +
                                "${failed?.describeFailure() ?: "no response"}. The backup server may be " +
                                "rebuilding its cache — try again in a few minutes.",
                        )
                    }
                }
            }
        }.getOrElse { e ->
            SourceCheckResult(
                healthy = false,
                summary = "Failed to reach Qobuz backup: ${e.message ?: e.javaClass.simpleName}",
            )
        }
    }

    /** Outcome of a two-byte ranged GET against a Qobuz-backup CDN mirror. */
    private data class CdnProbe(
        val ok: Boolean,
        val code: Int,
        val contentType: String,
        val totalBytes: Long?,
    ) {
        fun sizeSuffix(): String =
            totalBytes?.let { ", ${it / 1_000_000}MB" }.orEmpty()

        fun describeFailure(): String =
            if (code in 200..299) "an unexpected content type: $contentType" else "HTTP $code"
    }

    private fun probeCdn(url: String): CdnProbe? =
        runCatching {
            val request = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", "ArchiveTune-Android")
                .header("Range", "bytes=0-1")
                .build()
            client.newCall(request).execute().use { response ->
                val contentType = response.header("Content-Type")?.lowercase().orEmpty()
                val isAudio =
                    contentType.startsWith("audio/") ||
                        contentType.startsWith("video/") ||
                        contentType.contains("octet-stream")
                val total =
                    response
                        .header("Content-Range")
                        ?.substringAfter('/', "")
                        ?.trim()
                        ?.toLongOrNull()
                CdnProbe(
                    ok = response.isSuccessful && isAudio,
                    code = response.code,
                    contentType = contentType.ifBlank { "unknown" },
                    totalBytes = total,
                )
            }
        }.getOrNull()

    private suspend fun checkDeezer(context: Context): SourceCheckResult {
        // force = true. The whole point of tapping "Check source" is to find out whether accounts
        // can be obtained *now*, and a non-forced refresh is throttled — previously for a full 24h
        // whenever any other service had accounts cached, so the message telling the user to refresh
        // the pool was advice this very call had just declined to follow.
        PoolAccountManager.refresh(context, force = true)

        // Ask the provider, not the pool. DeezerAudioProvider.accounts() merges the manually
        // signed-in ARL (Integration → Deezer) with the pool's shared accounts; reading
        // PoolAccountManager.deezerAccounts() directly skips the manual one entirely and reported
        // "No Deezer accounts in the source pool" to users who had signed in successfully and whose
        // playback was in fact resolving. Same defect 7ede13689 fixed in MusicService's resolver.
        val availability = DeezerAudioProvider.accountAvailability()
        if (availability.total == 0) {
            return SourceCheckResult(
                healthy = false,
                summary = "No Deezer credentials available. Sign in with your own Deezer account via " +
                    "Integration → Deezer, or tap 'Refresh source pool' at the top to pick up shared accounts.",
            )
        }

        val origin =
            buildList {
                if (availability.manual) {
                    add("your own account${if (availability.manualPremium) " (premium)" else ""}")
                }
                if (availability.pooled > 0) {
                    add("${availability.pooled} pool account(s), ${availability.pooledPremium} premium")
                }
            }.joinToString(" + ")

        // Probe the credential resolve() would reach for first. A stored ARL says nothing about
        // whether Deezer still accepts it — an expired cookie looks identical until playback
        // silently falls through to the next source.
        val info = DeezerAudioProvider.verifyPreferredAccount()
        return if (info == null) {
            SourceCheckResult(
                healthy = false,
                summary = "Found $origin, but the Deezer gateway rejected the credential it would use " +
                    "first. Sign in again via Integration → Deezer, or refresh the source pool.",
            )
        } else {
            val tier = if (info.lossless) "lossless (FLAC) available" else "no lossless — 320kbps MP3 at best"
            SourceCheckResult(
                healthy = true,
                summary = "Credentials: $origin. Verified as '${info.name}' — $tier. Deezer source is READY.",
            )
        }
    }

    private fun checkJioSaavn(): SourceCheckResult {
        // JioSaavn is unauthenticated — just probe the public search API with
        // a canned query and verify it returns at least one result.
        // NOTE: server addresses are intentionally hidden from the summary text.
        return runCatching {
            val result = kotlinx.coroutines.runBlocking {
                SaavnService.searchSongs("test query").getOrDefault(emptyList())
            }
            if (result.isEmpty()) {
                SourceCheckResult(
                    healthy = false,
                    summary = "JioSaavn search returned no results. The service may be down or " +
                        "rate-limiting your IP — try again in a minute.",
                )
            } else {
                SourceCheckResult(
                    healthy = true,
                    summary = "JioSaavn is reachable and returned ${result.size} results for a probe query. " +
                        "JioSaavn source is READY.",
                )
            }
        }.getOrElse { e ->
            SourceCheckResult(
                healthy = false,
                summary = "Failed to reach JioSaavn: ${e.message ?: e.javaClass.simpleName}",
            )
        }
    }
}
