/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.lyrics

import android.content.Context
import android.util.Log
import android.util.LruCache
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import moe.rukamori.archivetune.constants.LyricsProviderOrderKey
import moe.rukamori.archivetune.constants.PreferredLyricsProvider
import moe.rukamori.archivetune.constants.deserializeLyricsProviderOrder
import moe.rukamori.archivetune.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.telegram.isTelegramMediaId
import moe.rukamori.archivetune.utils.GlobalLog
import moe.rukamori.archivetune.utils.isLocalMediaId
import moe.rukamori.archivetune.utils.NetworkConnectivityObserver
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.reportException
import javax.inject.Inject

class LyricsHelper
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val networkConnectivity: NetworkConnectivityObserver,
    ) {
        private val baseProviders =
            listOf(
                BetterLyricsProvider,
                BetterLyricsPortatoProvider,
                YouLyPlusLyricsProvider,
                LrcLibLyricsProvider,
                KuGouLyricsProvider,
                MegalobizLyricsProvider,
                SimpMusicLyricsProvider,
                UnisonLyricsProvider,
                PaxsenixAppleMusicLyricsProvider,
                PaxsenixNeteaseLyricsProvider,
                PaxsenixSpotifyLyricsProvider,
                PaxsenixMusixmatchLyricsProvider,
                PaxsenixYouTubeLyricsProvider,
                YouTubeSubtitleLyricsProvider,
                YouTubeLyricsProvider,

                
                MusixmatchExperimentalLyricsProvider,
            )

        private val cache = LruCache<String, List<LyricsResult>>(MAX_CACHE_SIZE)
        private val singleLyricsCache = LruCache<String, LyricsResult>(MAX_CACHE_SIZE)

        suspend fun getLyrics(
            mediaMetadata: MediaMetadata,
            preferredProviderOnly: Boolean = false,
            forceRefresh: Boolean = false,
        ): String = getLyricsWithProvider(
            mediaMetadata = mediaMetadata,
            preferredProviderOnly = preferredProviderOnly,
            forceRefresh = forceRefresh,
        ).lyrics

        suspend fun getLyricsWithProvider(
            mediaMetadata: MediaMetadata,
            preferredProviderOnly: Boolean = false,
            forceRefresh: Boolean = false,
        ): LyricsResult {
            val cacheKey = mediaMetadata.lyricsCacheKey
            if (forceRefresh) {
                invalidateCache(cacheKey)
            } else {
                singleLyricsCache.get(cacheKey)?.let { cached ->
                    GlobalLog.append(Log.DEBUG, "LyricsHelper", "Found lyrics in cache for ${mediaMetadata.title}")
                    return cached
                }

                val cached = cache.get(cacheKey)?.firstOrNull()
                if (cached != null) {
                    GlobalLog.append(Log.DEBUG, "LyricsHelper", "Found lyrics in cache for ${mediaMetadata.title}")
                    return cached
                }
            }

            GlobalLog.append(
                Log.DEBUG,
                "LyricsHelper",
                "Fetching lyrics for ${mediaMetadata.title} (Artist: ${mediaMetadata.artists.joinToString {
                    it.name
                }}, Album: ${mediaMetadata.album?.title})",
            )

            val isNetworkAvailable =
                try {
                    networkConnectivity.isCurrentlyConnected()
                } catch (e: Exception) {
                    true
                }

            if (!isNetworkAvailable) {
                GlobalLog.append(Log.WARN, "LyricsHelper", "Network unavailable, aborting lyrics fetch")
                return LyricsResult(providerName = "", lyrics = LYRICS_NOT_FOUND)
            }

            val ordered =
                orderedProviders()
                    .filter { it.isEnabled(context) }
                    .filter { supportsMediaId(it, mediaMetadata.id) }
            val providers = if (preferredProviderOnly) ordered.take(1) else ordered
            // Single parallel fetch — the previous implementation called fetchPriorityLyricsResult
            // twice (once for the provider name, once for the lyrics body), which doubled the
            // worst-case latency because each call waited for the slowest provider. Resolve once
            // and reuse the result.
            val result = fetchPriorityLyricsResult(providers, mediaMetadata)
            if (isMeaningfulLyrics(result.lyrics)) {
                singleLyricsCache.put(cacheKey, result)
            }

            return result
        }

        suspend fun getAllLyrics(
            mediaId: String,
            songTitle: String,
            songArtists: String,
            songAlbum: String?,
            duration: Int,
            forceRefresh: Boolean = false,
            callback: (LyricsResult) -> Unit,
        ) {
            val cacheKey = lyricsCacheKey(songTitle, songArtists)
            if (forceRefresh) {
                invalidateCache(cacheKey)
            } else {
                cache.get(cacheKey)?.let { results ->
                    results.forEach(callback)
                    return
                }
            }

            val isNetworkAvailable =
                try {
                    networkConnectivity.isCurrentlyConnected()
                } catch (e: Exception) {
                    true
                }

            if (!isNetworkAvailable) {
                return
            }

            val allResult = mutableListOf<LyricsResult>()
            val providers = orderedProviders()
            withContext(Dispatchers.IO) {
                providers.forEach { provider ->
                    if (!provider.isEnabled(context)) return@forEach

                    try {
                        provider.getAllLyrics(mediaId, songTitle, songArtists, songAlbum, duration) lyricsCallback@{ lyrics ->
                            val normalizedLyrics = LyricsUtils.lyricsOrNotFound(lyrics)
                            if (normalizedLyrics == LYRICS_NOT_FOUND) return@lyricsCallback
                            val result = LyricsResult(provider.name, normalizedLyrics)
                            allResult += result
                            callback(result)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        reportException(e)
                    }
                }
            }
            cache.put(cacheKey, allResult.toList())
        }

        /**
         * Streams provider results as they arrive and returns the best acceptable lyrics payload
         * while still respecting the user-configured provider priority order.
         *
         * Strategy:
         *  - All enabled providers run in parallel on `Dispatchers.IO` (so a slow provider can't
         *    block a fast one — keeps the panel load latency low).
         *  - Results are collected as they arrive. Each result is classified as word-synced,
         *    line-synced, or plain (unsynced).
         *  - When a synced result arrives, a short "grace period" starts (or continues). During
         *    the grace period we keep collecting so higher-priority providers get a chance to
         *    respond — this is what restores the priority order: a fast low-priority line-synced
         *    result no longer preempts a slightly slower top-priority word-synced result.
         *  - We early-exit when:
         *      * the top-priority provider returns a synced result (priority 0 — can't be beaten), or
         *      * all providers have completed (no point waiting for the grace period), or
         *      * the grace period has elapsed since the first synced result arrived.
         *  - At decision time we pick the best result by (sync tier: word > line > plain, then by
         *    priority index — lower index wins).
         *  - `LYRICS_NOT_FOUND` is returned only if every provider failed/returned nothing.
         *
         * Grace period is short (1.2s) so the perceived load latency stays low — typical provider
         * latency spreads (LRCLIB ~100ms, Musixmatch ~3–15s) mean the top-priority provider either
         * responds within the grace period or wasn't going to win on speed anyway.
         */
        private suspend fun fetchPriorityLyricsResult(
            providers: List<LyricsProvider>,
            mediaMetadata: MediaMetadata,
        ): LyricsResult {
            if (providers.isEmpty()) return LyricsResult(providerName = "", lyrics = LYRICS_NOT_FOUND)

            val artist = mediaMetadata.artists.joinToString { it.name }

            return coroutineScope {
                // (priorityIndex, providerName, lyrics) — priorityIndex is the position in the
                // user-ordered provider list (0 = highest priority).
                data class ProviderResult(val priority: Int, val providerName: String, val lyrics: String)

                // UNLIMITED so `trySend` never blocks a provider coroutine; we close the channel
                // ourselves once we have a winner.
                val channel = Channel<ProviderResult>(Channel.UNLIMITED)
                val providerJobs: List<Job> =
                    providers.mapIndexed { index, provider ->
                        launch(Dispatchers.IO) {
                            val lyrics = fetchProviderLyrics(provider, mediaMetadata, artist)
                            if (lyrics != null) {
                                channel.trySend(ProviderResult(index, provider.name, lyrics))
                            }
                        }
                    }
                val collectorJob =
                    launch {
                        try {
                            providerJobs.joinAll()
                        } finally {
                            channel.close()
                        }
                    }

                try {
                    val collected = mutableListOf<ProviderResult>()
                    // Hard ceiling on total wait — if no winner emerges after this, return the best
                    // we have so a single hung provider can't pin the panel for its full timeout.
                    val hardTimeoutMs = 8_000L
                    // Grace window after the first synced result arrives — gives higher-priority
                    // providers a chance to respond before we commit to a lower-priority winner.
                    val gracePeriodMs = 1_200L

                    val startedAtMs = System.currentTimeMillis()
                    var firstSyncedAtMs: Long? = null

                    fun isSynced(result: ProviderResult): Boolean =
                        LyricsUtils.hasWordSyncedLyrics(result.lyrics) ||
                            LyricsUtils.isLineSyncedLrc(result.lyrics)

                    // Best result so far: prefer word-synced > line-synced > plain, then by
                    // priority index (lower = higher priority).
                    fun pickBest(): ProviderResult? =
                        collected
                            .filter { isSynced(it) }
                            .sortedWith(
                                compareByDescending<ProviderResult> {
                                    if (LyricsUtils.hasWordSyncedLyrics(it.lyrics)) 2 else 1
                                }.thenBy { it.priority },
                            ).firstOrNull()
                            ?: collected.minByOrNull { it.priority }

                    fun allProvidersDone(): Boolean = providerJobs.all { !it.isActive }

                    while (true) {
                        val elapsedMs = System.currentTimeMillis() - startedAtMs
                        val remainingMs = hardTimeoutMs - elapsedMs
                        if (remainingMs <= 0) break

                        val nextResult = withTimeoutOrNull(remainingMs) { channel.receiveCatching().getOrNull() }
                        if (nextResult == null) {
                            // Channel closed (all providers done) or hard timeout — exit and pick best.
                            break
                        }

                        collected.add(nextResult)

                        if (!isSynced(nextResult)) continue

                        if (firstSyncedAtMs == null) {
                            firstSyncedAtMs = System.currentTimeMillis()
                        }

                        val best = pickBest() ?: continue

                        // Early-exit case 1: top-priority provider returned a synced result.
                        // Nothing can beat it (no higher priority exists), so commit immediately.
                        if (best.priority == 0 && LyricsUtils.hasWordSyncedLyrics(best.lyrics)) {
                            return@coroutineScope LyricsResult(providerName = best.providerName, lyrics = best.lyrics)
                        }
                        // Note: we deliberately do NOT early-exit on priority-0 line-synced,
                        // because a slightly later word-synced result from any provider would
                        // be strictly better — the grace period lets that arrive.

                        // Early-exit case 2: all providers have completed — no point waiting.
                        if (allProvidersDone()) {
                            return@coroutineScope LyricsResult(providerName = best.providerName, lyrics = best.lyrics)
                        }

                        // Early-exit case 3: grace period has elapsed since the first synced
                        // result arrived. Commit the best we have.
                        val firstSyncedAt = firstSyncedAtMs
                        if (firstSyncedAt != null) {
                            val sinceFirstSyncedMs = System.currentTimeMillis() - firstSyncedAt
                            if (sinceFirstSyncedMs >= gracePeriodMs) {
                                return@coroutineScope LyricsResult(providerName = best.providerName, lyrics = best.lyrics)
                            }
                        }
                        // Otherwise loop back to keep collecting — higher-priority providers
                        // might still return a better result before the grace period expires.
                    }

                    // All providers completed or hard timeout — pick the best result.
                    pickBest()?.let { best ->
                        return@coroutineScope LyricsResult(providerName = best.providerName, lyrics = best.lyrics)
                    }
                    LyricsResult(providerName = "", lyrics = LYRICS_NOT_FOUND)
                } finally {
                    collectorJob.cancel()
                    providerJobs.forEach { it.cancel() }
                }
            }
        }

        private suspend fun fetchProviderLyrics(
            provider: LyricsProvider,
            mediaMetadata: MediaMetadata,
            artist: String,
        ): String? =
            try {
                provider
                    .getLyrics(
                        mediaMetadata.id,
                        mediaMetadata.title,
                        artist,
                        mediaMetadata.album?.title,
                        mediaMetadata.duration,
                    ).fold(
                        onSuccess = { lyrics ->
                            LyricsUtils.lyricsOrNotFound(lyrics).takeIf { it != LYRICS_NOT_FOUND }
                        },
                        onFailure = {
                            reportException(it)
                            null
                        },
                    )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportException(e)
                null
            }

        private suspend fun orderedProviders(): List<LyricsProvider> {
            val orderStr = context.dataStore.data.first()[LyricsProviderOrderKey]
            val orderedEnums = deserializeLyricsProviderOrder(orderStr)
            val providerMap: Map<PreferredLyricsProvider, LyricsProvider> =
                mapOf(
                    PreferredLyricsProvider.LRCLIB to LrcLibLyricsProvider,
                    PreferredLyricsProvider.KUGOU to KuGouLyricsProvider,
                    PreferredLyricsProvider.MEGALOBIZ to MegalobizLyricsProvider,
                    PreferredLyricsProvider.BETTER_LYRICS to BetterLyricsProvider,
                    PreferredLyricsProvider.BETTER_LYRICS_PORTATO to BetterLyricsPortatoProvider,
                    PreferredLyricsProvider.YOULY_PLUS to YouLyPlusLyricsProvider,
                    PreferredLyricsProvider.SIMPMUSIC to SimpMusicLyricsProvider,
                    PreferredLyricsProvider.PAXSENIX_APPLE_MUSIC to PaxsenixAppleMusicLyricsProvider,
                    PreferredLyricsProvider.PAXSENIX_NETEASE to PaxsenixNeteaseLyricsProvider,
                    PreferredLyricsProvider.PAXSENIX_SPOTIFY to PaxsenixSpotifyLyricsProvider,
                    PreferredLyricsProvider.PAXSENIX_MUSIXMATCH to PaxsenixMusixmatchLyricsProvider,
                    PreferredLyricsProvider.PAXSENIX_YOUTUBE to PaxsenixYouTubeLyricsProvider,
                    PreferredLyricsProvider.UNISON to UnisonLyricsProvider,
                    PreferredLyricsProvider.MUSIXMATCH_EXPERIMENTAL to MusixmatchExperimentalLyricsProvider,
                )
            val userOrdered = orderedEnums.mapNotNull { providerMap[it] }
            val rest = baseProviders.filterNot { it in userOrdered }
            return userOrdered + rest
        }

        private fun isMeaningfulLyrics(lyrics: String): Boolean = LyricsUtils.hasMeaningfulLyricsContent(lyrics)

        private fun supportsMediaId(
            provider: LyricsProvider,
            mediaId: String,
        ): Boolean {
            val isNonYouTubeId = mediaId.isTelegramMediaId() || mediaId.isLocalMediaId()
            if (!isNonYouTubeId) return true
            return provider !is SimpMusicLyricsProvider &&
                provider !is YouTubeLyricsProvider &&
                provider !is YouTubeSubtitleLyricsProvider
        }

        fun clearCache() {
            cache.evictAll()
            singleLyricsCache.evictAll()
        }

        private fun invalidateCache(cacheKey: String) {
            cache.remove(cacheKey)
            singleLyricsCache.remove(cacheKey)
        }

        private val MediaMetadata.lyricsCacheKey: String
            get() =
                lyricsCacheKey(
                    title = title,
                    artists = artists.joinToString { it.name },
                )

        private fun lyricsCacheKey(
            title: String,
            artists: String,
        ): String = "$artists-$title".replace(" ", "")

        companion object {
            private const val MAX_CACHE_SIZE = 16
        }
    }

data class LyricsResult(
    val providerName: String,
    val lyrics: String,
)
