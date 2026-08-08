/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 License Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import moe.rukamori.archivetune.audiosource.DirectStream
import moe.rukamori.archivetune.constants.AudioSourceType
import moe.rukamori.archivetune.constants.QobuzInstancesKey
import moe.rukamori.archivetune.constants.QobuzTokensKey
import moe.rukamori.archivetune.constants.TidalAccessTokenKey
import moe.rukamori.archivetune.constants.TidalAccountFirstKey
import moe.rukamori.archivetune.constants.TidalAudioQuality
import moe.rukamori.archivetune.constants.TidalCountryCodeKey
import moe.rukamori.archivetune.constants.TidalInstancesKey
import moe.rukamori.archivetune.qobuz.QobuzAudioProvider
import moe.rukamori.archivetune.qobuz.QobuzToken
import moe.rukamori.archivetune.tidal.TidalAccountManager
import moe.rukamori.archivetune.tidal.TidalAudioProvider
import moe.rukamori.archivetune.tidal.TidalInstanceHealthManager
import moe.rukamori.archivetune.utils.PoolAccountManager
import moe.rukamori.archivetune.utils.dataStore
import timber.log.Timber
import java.io.File

/**
 * Pool-aware lossless stream resolver, used by the **download path**
 * ([DownloadUtil.resolveSourceStream] / [DownloadUtil.prewarmSongForDownload]).
 *
 * This is a stand-alone re-implementation of the resolver logic in
 * [moe.rukamori.archivetune.playback.MusicService.resolveQobuzStream] and
 * [moe.rukamori.archivetune.playback.MusicService.resolveTidalStream],
 * so downloads reach the **same community Source Pool accounts**
 * (Qobuz/Tidal subscriber tokens) that playback uses, instead of
 * calling [QobuzAudioProvider.resolve] / [TidalAudioProvider.resolve]
 * directly with only the user's own credentials.
 *
 * Without this, downloads of uncached songs silently fell through the
 * AUTO chain (Qobuz → Tidal → Deezer → YouTube Music) because the user
 * had no personal Qobuz/Tidal credentials configured, and ended up at
 * the YouTube Music fallback — which serves Opus audio inside a WebM
 * container (lossy, not jaudiotagger-readable, no embedded metadata).
 *
 * The fix: before each resolve, push the **pool-merged** token list /
 * instance list into the providers, exactly like MusicService does for
 * playback. Then [QobuzAudioProvider.resolve] / [TidalAudioProvider.resolve]
 * will pick the pool's premium accounts first and resolve a real FLAC
 * stream URL against the official Qobuz/Tidal APIs.
 *
 * All resolvers run on the calling thread (the caller is expected to be
 * on `Dispatchers.IO` already — see [DownloadUtil.downloadExecutor]).
 */
object LosslessStreamResolver {

    /**
     * Resolves a Qobuz stream URL for [mediaId] using the user's own
     * Qobuz tokens + community Source Pool accounts.
     *
     * Mirrors [MusicService.resolveQobuzStream] — merges user tokens +
     * pool tokens, merges user instances + discovered instances, pushes
     * them into [QobuzAudioProvider], then calls [QobuzAudioProvider.resolve].
     *
     * Returns null when:
     *  - No tokens and no instances are configured (user has nothing +
     *    pool is disabled/empty).
     *  - The provider can't find a matching track / can't get a stream URL.
     */
    fun resolveQobuz(
        context: Context,
        mediaId: String,
        title: String,
        artists: List<String>,
        album: String?,
        durationMs: Long?,
        formatId: Int,
    ): DirectStream? {
        val userInstances = parseMultiline(context, QobuzInstancesKey)
        val discoveredInstances = runCatching { QobuzAudioProvider.discoverInstances() }
            .getOrDefault(emptyList())
        val mergedInstances = LinkedHashSet<String>().apply {
            addAll(userInstances)
            addAll(discoveredInstances)
        }.toList()

        val userTokens = QobuzToken.listFromJson(readString(context, QobuzTokensKey))
        val poolTokens = PoolAccountManager.qobuzAccounts().map {
            QobuzToken(
                token = it.token,
                appId = it.appId,
                appSecret = it.appSecret,
                label = "Source Pool",
                subscription = if (it.premium) "premium" else "",
            )
        }
        val mergedTokens = (userTokens + poolTokens).distinctBy { it.token }

        if (mergedInstances.isEmpty() && mergedTokens.isEmpty()) {
            Timber.tag("LosslessResolver").d("Qobuz skip: no tokens or instances configured")
            return null
        }

        QobuzAudioProvider.setTokens(mergedTokens)
        QobuzAudioProvider.setInstances(mergedInstances)

        return runCatching {
            runBlocking(Dispatchers.IO) {
                QobuzAudioProvider.resolve(
                    query = QobuzAudioProvider.Query(
                        mediaId = mediaId,
                        title = title,
                        artists = artists,
                        album = album,
                        durationMs = durationMs,
                    ),
                    formatId = formatId,
                )
            }
        }.onFailure { error ->
            Timber.tag("LosslessResolver").w(error, "Qobuz resolve failed for %s", mediaId)
        }.getOrNull()
    }

    /**
     * Resolves a Tidal stream URL for [mediaId] using:
     *
     *   1. The user's own Tidal access token (if signed in)
     *   2. Community Source Pool Tidal accounts (premium first)
     *   3. Public HiFi/QQDL instances (user-configured + pool-discovered)
     *
     * Mirrors [MusicService.resolveTidalStream]. Each pool token is tried
     * in turn via [TidalAccountManager.resolveDirectStream] (full-quality
     * FLAC via the official API), and only when all account paths fail do
     * we fall through to the public-instance path via
     * [TidalAudioProvider.resolve].
     *
     * Returns null when no path yields a stream.
     */
    fun resolveTidal(
        context: Context,
        mediaId: String,
        title: String,
        artists: List<String>,
        album: String?,
        durationMs: Long?,
        audioQuality: TidalAudioQuality,
        cacheDir: File,
    ): DirectStream? {
        val apiQuality = when (audioQuality) {
            TidalAudioQuality.HI_RES_LOSSLESS -> "HI_RES_LOSSLESS"
            TidalAudioQuality.FLAC -> "LOSSLESS"
            TidalAudioQuality.AAC_320 -> "HIGH"
        }
        val accountFirst = readBoolean(context, TidalAccountFirstKey, true)
        Timber.tag("LosslessResolver").d(
            "Tidal resolve start | quality=%s accountFirst=%s poolAccounts=%d",
            audioQuality.name, accountFirst, PoolAccountManager.tidalAccounts().size,
        )

        if (accountFirst) {
            // 1) The user's own Tidal token. We do NOT refresh here —
            //    playback's MusicService.refreshTidalToken() handles that
            //    for the signed-in user, and pool tokens are fresh from
            //    the Source Pool website's /api/sources endpoint.
            val userToken = readString(context, TidalAccessTokenKey)
            if (userToken.isNotBlank()) {
                val country = readString(context, TidalCountryCodeKey).ifBlank { "US" }
                val stream = runCatching {
                    runBlocking(Dispatchers.IO) {
                        TidalAccountManager.resolveDirectStream(
                            accessToken = userToken,
                            title = title,
                            artists = artists,
                            durationMs = durationMs,
                            audioQuality = apiQuality,
                            cacheDir = cacheDir,
                            countryCode = country,
                        )
                    }
                }.onFailure {
                    Timber.tag("LosslessResolver").w(it, "Tidal user-token resolve failed for %s", mediaId)
                }.getOrNull()
                if (stream != null) return stream
            }

            // 2) Shared premium Tidal accounts from the community Source Pool.
            //    These are real subscriber tokens, so they resolve full-quality
            //    FLAC directly via the official API — no proxy instance needed.
            for (poolAccount in PoolAccountManager.tidalAccounts()) {
                val poolCountry = poolAccount.countryCode?.trim()?.ifBlank { null } ?: "US"
                val stream = runCatching {
                    runBlocking(Dispatchers.IO) {
                        TidalAccountManager.resolveDirectStream(
                            accessToken = poolAccount.token,
                            title = title,
                            artists = artists,
                            durationMs = durationMs,
                            audioQuality = apiQuality,
                            cacheDir = cacheDir,
                            countryCode = poolCountry,
                        )
                    }
                }.onFailure {
                    Timber.tag("LosslessResolver").w(it, "Tidal pool account resolve failed for %s", mediaId)
                }.getOrNull()
                if (stream != null) {
                    Timber.tag("LosslessResolver").d(
                        "Tidal resolved via pool account (premium=%s) for %s",
                        poolAccount.premium, mediaId,
                    )
                    return stream
                }
            }
        }

        // 3) Fallback: public HiFi/QQDL instances (user-configured + pool-discovered).
        //    These proxy servers do NOT require any account — they re-stream Tidal
        //    lossless audio publicly. Quality is lower than the account path but
        //    still FLAC when the instance supports it.
        val configuredInstances = parseMultiline(context, TidalInstancesKey)
        val discoveredInstances = TidalInstanceHealthManager.healthyUrls(context)
        val mergedInstances = LinkedHashSet<String>().apply {
            addAll(configuredInstances)
            addAll(discoveredInstances)
        }.toList()
        TidalAudioProvider.setInstances(mergedInstances)

        return runCatching {
            runBlocking(Dispatchers.IO) {
                TidalAudioProvider.resolve(
                    query = TidalAudioProvider.Query(
                        mediaId = mediaId,
                        title = title,
                        artists = artists,
                        album = album,
                        isrc = null,
                        durationMs = durationMs,
                    ),
                    cacheDir = cacheDir,
                    preferAtmos = false,
                    preferLiveDash = false,
                    audioQuality = audioQuality,
                )
            }
        }.onFailure {
            Timber.tag("LosslessResolver").w(it, "Tidal public-instance resolve failed for %s", mediaId)
        }.getOrNull()?.let { resolved ->
            DirectStream(
                uri = resolved.mediaUri,
                mimeType = resolved.mimeType,
                codecs = resolved.codecs,
                contentLength = resolved.contentLength,
                label = "Tidal ${resolved.label}",
                source = AudioSourceType.TIDAL,
                matchedTitle = resolved.matchedTitle,
                matchedArtist = resolved.matchedArtist,
                matchedAlbum = resolved.matchedAlbum,
                matchedDurationMs = resolved.matchedDurationMs,
            )
        }
    }

    /**
     * Returns the cache key prefix for [source], matching
     * [MusicService.sourceCacheKey] so bytes cached here are picked up
     * by the playback resolver and vice versa.
     */
    fun cacheKeyPrefix(source: AudioSourceType): String = when (source) {
        AudioSourceType.TIDAL -> "tidal:"
        AudioSourceType.QOBUZ -> "qobuz:"
        else -> "${source.name.lowercase()}:"
    }

    // ---- helpers ----

    private fun readString(context: Context, key: androidx.datastore.preferences.core.Preferences.Key<String>): String =
        runCatching { runBlocking { context.dataStore.data.first()[key] ?: "" } }.getOrDefault("")

    private fun readBoolean(
        context: Context,
        key: androidx.datastore.preferences.core.Preferences.Key<Boolean>,
        default: Boolean,
    ): Boolean = runCatching { runBlocking { context.dataStore.data.first()[key] ?: default } }.getOrDefault(default)

    private fun parseMultiline(
        context: Context,
        key: androidx.datastore.preferences.core.Preferences.Key<String>,
    ): List<String> = readString(context, key)
        .split('\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}
