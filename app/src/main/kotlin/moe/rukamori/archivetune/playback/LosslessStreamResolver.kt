/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 License Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
import moe.rukamori.archivetune.deezer.DeezerAudioProvider
import moe.rukamori.archivetune.jiosaavn.SaavnService
import moe.rukamori.archivetune.qobuz.QobuzAudioProvider
import moe.rukamori.archivetune.qobuz.QobuzBackupProvider
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
        isrc: String? = null,
    ): DirectStream? {
        val resolvedIsrc = isrc ?: moe.rukamori.archivetune.audiosource.IsrcResolver.resolveBlocking(mediaId, title, artists, durationMs)
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
                poolId = it.id,
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
                        isrc = resolvedIsrc,
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
        isrc: String? = null,
    ): DirectStream? {
        val resolvedIsrc = isrc ?: moe.rukamori.archivetune.audiosource.IsrcResolver.resolveBlocking(mediaId, title, artists, durationMs)
        val apiQuality = when (audioQuality) {
            TidalAudioQuality.HI_RES_LOSSLESS -> "HI_RES_LOSSLESS"
            TidalAudioQuality.FLAC -> "LOSSLESS"
            TidalAudioQuality.AAC_320 -> "HIGH"
        }
        val accountFirst = readBoolean(context, TidalAccountFirstKey, true)
        Timber.tag("LosslessResolver").d(
            "Tidal resolve start | quality=%s accountFirst=%s poolAccounts=%d isrc=%s",
            audioQuality.name, accountFirst, PoolAccountManager.tidalAccounts().size, resolvedIsrc,
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
            val poolAccounts = PoolAccountManager.tidalAccounts()
            if (poolAccounts.isNotEmpty()) {
                val stream = runCatching {
                    runBlocking(Dispatchers.IO) {
                        coroutineScope {
                            val jobs = poolAccounts.map { poolAccount ->
                                async(Dispatchers.IO) {
                                    val poolCountry =
                                        poolAccount.countryCode?.trim()?.ifBlank { null } ?: "US"
                                    runCatching {
                                        TidalAccountManager.resolveDirectStream(
                                            accessToken = poolAccount.token,
                                            title = title,
                                            artists = artists,
                                            durationMs = durationMs,
                                            audioQuality = apiQuality,
                                            cacheDir = cacheDir,
                                            countryCode = poolCountry,
                                        )
                                    }.onFailure {
                                        if (TidalAccountManager.isUnauthorized(it)) {
                                            PoolAccountManager.report("tidal", "account", poolAccount.id, "dead")
                                        } else {
                                            Timber.tag("LosslessResolver").w(
                                                it, "Tidal pool account resolve failed for %s", mediaId,
                                            )
                                        }
                                    }.getOrNull()
                                }
                            }
                            // First non-null wins; cancel the rest.
                            var winner: DirectStream? = null
                            for (job in jobs) {
                                val result = job.await()
                                if (result != null) {
                                    winner = result
                                    jobs.forEach { other -> if (!other.isCompleted) other.cancel() }
                                    break
                                }
                            }
                            winner
                        }
                    }
                }.onFailure {
                    Timber.tag("LosslessResolver").w(it, "Tidal pool race failed for %s", mediaId)
                }.getOrNull()
                if (stream != null) {
                    Timber.tag("LosslessResolver").d(
                        "Tidal resolved via pool race for %s", mediaId,
                    )
                    return stream
                }
            }
        }

        // 3) Fallback: public HiFi/QQDL instances (user-configured + pool-discovered).
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
                        isrc = resolvedIsrc,
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
     * Resolves a **Qobuz backup** stream for [mediaId] — the community-hosted `mlc-ytify.kouzu.in`
     * mirror, keyed by YouTube video id.
     *
     * Needs no Source Pool account and no credentials at all, which makes it the one lossless
     * source that works on a fresh install. That is also why it belongs in the download priority
     * list: before it was added there, a user with no pool accounts had every lossless entry in the
     * chain return null and every download fall through to the lossy YouTube fallback, even for
     * tracks the mirror had in FLAC.
     *
     * Delegates to [QobuzBackupProvider.resolveStream], the same resolver the playback path uses,
     * so mirror selection and FLAC detection cannot drift between playing a song and downloading
     * it. The mirror serves plain HTTPS with no decryption step, so the resolved URL is directly
     * usable by the downloader's HTTP data source.
     *
     * Deliberately NOT gated on the `QobuzBackupEnabledKey` playback toggle: the download priority
     * list is the control for downloads, exactly as it is for Qobuz, Tidal and Deezer — none of
     * which consult their playback switches here either. A user who drags this source to the top of
     * the download list means it.
     *
     * @param mediaId the song's YouTube video id. Anything that is not an 11-character video id is
     *   rejected by the provider, which is the normal outcome for a local or imported track.
     */
    fun resolveQobuzBackup(mediaId: String): DirectStream? =
        runCatching {
            runBlocking(Dispatchers.IO) {
                QobuzBackupProvider.resolveStream(mediaId)?.let { resolved ->
                    DirectStream(
                        uri = resolved.uri,
                        mimeType = resolved.mimeType,
                        codecs = resolved.codecs,
                        contentLength = resolved.contentLength,
                        label = resolved.label,
                        source = AudioSourceType.QOBUZ_BACKUP,
                        // The mirror is keyed by the YouTube id we asked for, so the match is
                        // authoritative by construction — there is no catalogue search to verify.
                        trustedDirectId = true,
                        sampleRate = resolved.sampleRate,
                        bitDepth = resolved.bitDepth,
                        matchedDurationMs = resolved.durationMs,
                    )
                }
            }
        }.onFailure { error ->
            Timber.tag("LosslessResolver").w(error, "Qobuz backup resolve failed for %s", mediaId)
        }.getOrNull()

    /**
     * Resolves a **Deezer** stream for [mediaId].
     *
     * Mirrors `MusicService.resolveDeezerStream`, including the credential guard: the provider merges
     * the manually signed-in ARL with the pool's shared accounts, so this must go through
     * [DeezerAudioProvider.hasAccounts] rather than reading [PoolAccountManager] directly.
     *
     * The returned [DirectStream.uri] is a `deezer://` URI, not an HTTPS URL — the CDN bytes are
     * Blowfish-encrypted and only become audio after `DeezerDecryptingDataSource` has run over them.
     * `DownloadUtil` routes that scheme to a decrypting upstream, so what lands in the download cache
     * is a plain FLAC/MP3 file that jaudiotagger can tag. This used to be a hard-coded `null` in the
     * download chain, with a comment claiming the public Deezer API only exposes 30-second previews —
     * true of `api.deezer.com`, but stale ever since full Premium streaming was ported: playback has
     * resolved real Deezer streams through this exact provider since then, while downloads silently
     * fell through to the YouTube fallback.
     */
    fun resolveDeezer(
        mediaId: String,
        title: String,
        artists: List<String>,
        album: String?,
        durationMs: Long?,
        format: String,
        isrc: String? = null,
    ): DirectStream? {
        val resolvedIsrc = isrc ?: moe.rukamori.archivetune.audiosource.IsrcResolver.resolveBlocking(mediaId, title, artists, durationMs)
        if (!DeezerAudioProvider.hasAccounts()) {
            Timber.tag("LosslessResolver").d("Deezer skip: no manual or pooled accounts available")
            return null
        }
        return runCatching {
            runBlocking(Dispatchers.IO) {
                DeezerAudioProvider
                    .resolve(
                        query =
                            DeezerAudioProvider.Query(
                                mediaId = mediaId,
                                title = title,
                                artists = artists,
                                album = album,
                                durationMs = durationMs,
                                isrc = resolvedIsrc,
                            ),
                        format = format,
                    )?.let { resolved ->
                        DirectStream(
                            uri = resolved.uri,
                            mimeType = resolved.mimeType,
                            codecs = resolved.codecs,
                            contentLength = resolved.contentLength,
                            label = resolved.label,
                            source = AudioSourceType.DEEZER,
                            matchedTitle = resolved.matchedTitle,
                            matchedArtist = resolved.matchedArtist,
                            matchedAlbum = resolved.matchedAlbum,
                            matchedDurationMs = resolved.matchedDurationMs,
                            sampleRate = resolved.sampleRate,
                            bitDepth = resolved.bitDepth,
                        )
                    }
            }
        }.onFailure { error ->
            Timber.tag("LosslessResolver").w(error, "Deezer resolve failed for %s", mediaId)
        }.getOrNull()
    }

    /**
     * Resolves a **JioSaavn** stream for [mediaId].
     *
     * JioSaavn is unauthenticated — no account, no pool, no decryption — and returns a plain HTTPS
     * AAC URL, so the download path can use it exactly as playback does. It was previously a
     * hard-coded `null` in the download chain with a comment saying a "stable, contentLength-bearing
     * resolver" was still needed; that requirement turned out to be unnecessary. The downloader
     * derives the length from the response itself (see `PRDownloaderDataSource`), which is how the
     * YouTube fallback already works — every YouTube stream URL arrives without a declared length
     * too.
     *
     * Mirrors `MusicService.resolveJioSaavnStream`, including the candidate scoring, so a track
     * downloads as the same recording it plays as.
     */
    fun resolveJioSaavn(
        mediaId: String,
        title: String,
        artists: List<String>,
        album: String?,
        durationMs: Long?,
        qualityApiValue: String,
    ): DirectStream? {
        val artistHint = artists.firstOrNull()?.takeIf { it.isNotBlank() }.orEmpty()
        val albumHint = album?.takeIf { it.isNotBlank() }.orEmpty()
        val searchQuery =
            buildString {
                append(title)
                if (artistHint.isNotBlank()) append(' ').append(artistHint)
                if (albumHint.isNotBlank()) append(' ').append(albumHint)
            }.trim()

        return runCatching {
            runBlocking(Dispatchers.IO) {
                val results = SaavnService.searchSongs(searchQuery).getOrNull().orEmpty()
                if (results.isEmpty()) return@runBlocking null
                val wantedDurationSec = durationMs?.let { it / 1000 }
                val candidate =
                    results
                        // Pro-only tracks answer with a 30-second preview, which would silently
                        // download as a truncated file.
                        .filter { !it.isProOnly && it.downloadUrl.isNotEmpty() }
                        .minByOrNull { song ->
                            saavnMatchPenalty(
                                candidateTitle = song.name,
                                candidateArtist = song.artists.primary.firstOrNull()?.name,
                                candidateDurationSec = song.duration?.toLong(),
                                wantedTitle = title,
                                wantedArtist = artistHint,
                                wantedDurationSec = wantedDurationSec,
                            )
                        } ?: return@runBlocking null
                val streamUrl =
                    SaavnService.selectBestUrl(candidate.downloadUrl, qualityApiValue)
                        ?: return@runBlocking null
                DirectStream(
                    uri = streamUrl,
                    mimeType = "audio/mp4",
                    codecs = "mp4a.40.2",
                    contentLength = null,
                    label = "JioSaavn $qualityApiValue",
                    source = AudioSourceType.JIOSAAVN,
                    matchedTitle = candidate.name,
                    matchedArtist = candidate.artists.primary.firstOrNull()?.name,
                    matchedAlbum = candidate.album?.name,
                    matchedDurationMs = candidate.duration?.toLong()?.times(1000L),
                )
            }
        }.onFailure { error ->
            Timber.tag("LosslessResolver").w(error, "JioSaavn resolve failed for %s", mediaId)
        }.getOrNull()
    }

    /**
     * Scores how badly a JioSaavn search hit matches what we asked for — lower is better.
     *
     * Same weighting as `MusicService.resolveJioSaavnStream`: an exact title is free, a substring
     * match costs a little, anything else costs a lot; artist is weighted lower than title because
     * JioSaavn credits features inconsistently; duration is the tie-breaker that catches remixes
     * and extended edits sharing a title.
     */
    private fun saavnMatchPenalty(
        candidateTitle: String,
        candidateArtist: String?,
        candidateDurationSec: Long?,
        wantedTitle: String,
        wantedArtist: String,
        wantedDurationSec: Long?,
    ): Int {
        var penalty = 0
        val normTitle = candidateTitle.lowercase().replace(SAAVN_NORMALIZE_REGEX, "")
        val normWanted = wantedTitle.lowercase().replace(SAAVN_NORMALIZE_REGEX, "")
        penalty +=
            when {
                normTitle == normWanted -> 0
                normTitle.contains(normWanted) || normWanted.contains(normTitle) -> 1
                else -> 5
            }
        val normArtist = candidateArtist?.lowercase()?.replace(SAAVN_NORMALIZE_REGEX, "").orEmpty()
        val normWantedArtist = wantedArtist.lowercase().replace(SAAVN_NORMALIZE_REGEX, "")
        if (normWantedArtist.isNotBlank() && normArtist.isNotBlank()) {
            penalty +=
                when {
                    normArtist == normWantedArtist -> 0
                    normArtist.contains(normWantedArtist) || normWantedArtist.contains(normArtist) -> 1
                    else -> 3
                }
        }
        if (wantedDurationSec != null && candidateDurationSec != null) {
            val delta = kotlin.math.abs(candidateDurationSec - wantedDurationSec)
            penalty +=
                when {
                    delta <= 3 -> 0
                    delta <= 10 -> 2
                    else -> 6
                }
        }
        return penalty
    }

    /** Strips punctuation and whitespace so titles compare on their words alone. */
    private val SAAVN_NORMALIZE_REGEX = Regex("[^a-z0-9]")

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
