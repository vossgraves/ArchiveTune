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
import moe.rukamori.archivetune.jiosaavn.SaavnService
import moe.rukamori.archivetune.qobuz.QobuzAudioProvider
import moe.rukamori.archivetune.qobuz.QobuzToken
import moe.rukamori.archivetune.tidal.TidalAudioProvider
import moe.rukamori.archivetune.utils.PoolAccountManager
import okhttp3.OkHttpClient
import okhttp3.Request
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
 *  - Tidal: refresh the source pool, count Tidal accounts, then probe one
 *    Tidal instance health. Reports counts + the verdict.
 *  - Qobuz: refresh the pool, count Qobuz accounts (premium first), then
 *    try a real user/get call on the first pool token to verify it works.
 *  - Qobuz backup: ping https://mlc.kouzu.in/api/stream?id=<test_id> with a
 *    HEAD request (the test id is a stable, well-known music video) and verify
 *    it returns an audio content type.
 *  - Deezer: refresh the pool, count Deezer accounts.
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
        // Probe Tidal instance health — the manager caches the verified list.
        val healthyInstances = runCatching {
            moe.rukamori.archivetune.tidal.TidalInstanceHealthManager.healthyUrls(context).size
        }.getOrDefault(0)
        val summary = buildString {
            append("Pool accounts: ${accounts.size} ($premium premium)\n")
            append("Healthy Tidal instances: $healthyInstances")
            if (healthyInstances == 0 && accounts.isNotEmpty()) {
                append("\n\nAccounts are loaded but no Tidal instance is reachable. " +
                    "Tap 'Refresh source pool' to re-verify, or add a private Tidal instance via Integration.")
            }
        }
        return SourceCheckResult(
            healthy = accounts.isNotEmpty() && (healthyInstances > 0 || accounts.any { it.premium }),
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
        // HEAD-probe the kouzu.in endpoint with a stable YouTube id. The
        // x-request-source: muzo header is injected centrally by the
        // MusicService.mediaOkHttpClient interceptor — but here we're using
        // our own client, so add it manually.
        val url = "https://mlc.kouzu.in/api/stream?id=$KOZU_PROBE_YT_ID"
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .head()
                .header("x-request-source", "muzo")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@runCatching SourceCheckResult(
                        healthy = false,
                        summary = "mlc.kouzu.in returned HTTP ${response.code} for the probe request. " +
                            "The backup server may be down or rate-limiting your IP.",
                    )
                }
                val contentType = response.header("Content-Type")?.lowercase().orEmpty()
                val ok = contentType.startsWith("audio/") ||
                    contentType.startsWith("video/") ||
                    contentType.contains("octet-stream")
                SourceCheckResult(
                    healthy = ok,
                    summary = if (ok) {
                        "mlc.kouzu.in is reachable and returned an audio stream ($contentType). " +
                            "Qobuz backup is READY."
                    } else {
                        "mlc.kouzu.in returned an unexpected content type: $contentType. " +
                            "The backup server may be misconfigured."
                    },
                )
            }
        }.getOrElse { e ->
            SourceCheckResult(
                healthy = false,
                summary = "Failed to reach mlc.kouzu.in: ${e.message ?: e.javaClass.simpleName}",
            )
        }
    }

    private suspend fun checkDeezer(context: Context): SourceCheckResult {
        PoolAccountManager.refresh(context, force = false)
        val accounts = PoolAccountManager.deezerAccounts()
        if (accounts.isEmpty()) {
            return SourceCheckResult(
                healthy = false,
                summary = "No Deezer accounts in the source pool. Tap 'Refresh source pool' at the top, " +
                    "or add your own Deezer ARL via Integration → Manual source sign-in.",
            )
        }
        val premium = accounts.count { it.premium }
        return SourceCheckResult(
            healthy = accounts.isNotEmpty(),
            summary = "Pool accounts: ${accounts.size} ($premium premium). " +
                "Deezer source is ${if (accounts.isNotEmpty()) "READY" else "NOT ready"}.",
        )
    }

    private fun checkJioSaavn(): SourceCheckResult {
        // JioSaavn is unauthenticated — just probe the public search API with
        // a canned query and verify it returns at least one result.
        return runCatching {
            val result = kotlinx.coroutines.runBlocking {
                SaavnService.searchSongs("test query").getOrDefault(emptyList())
            }
            if (result.isEmpty()) {
                SourceCheckResult(
                    healthy = false,
                    summary = "JioSaavn search returned no results. The JioSaavn API may be down or " +
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
