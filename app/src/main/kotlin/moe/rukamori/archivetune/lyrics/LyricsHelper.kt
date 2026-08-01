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
         * Streams provider results as they arrive and returns the first acceptable lyrics payload,
         * rather than waiting for every provider to complete (the previous implementation joined
         * every `async` before ranking, which meant a single slow provider could hold up the panel
         * for its full 15–20s timeout).
         *
         * Strategy:
         *  - All enabled providers run in parallel on `Dispatchers.IO`.
         *  - The first `word-synced` or `line-synced` result wins and is returned immediately —
         *    pending providers are cancelled. Returning the first synced result lets a fast
         *    provider (e.g. LRCLIB ~100ms) win over a slow one (e.g. Musixmatch token refresh
         *    ~3–15s) without making the user wait for the better-quality-but-slow result.
         *  - Plain (unsynced) lyrics are kept as a fallback; if no synced result arrives, the
         *    first plain result is returned after every provider finishes.
         *  - `LYRICS_NOT_FOUND` is returned only if every provider failed/returned nothing.
         */
        private suspend fun fetchPriorityLyricsResult(
            providers: List<LyricsProvider>,
            mediaMetadata: MediaMetadata,
        ): LyricsResult {
            if (providers.isEmpty()) return LyricsResult(providerName = "", lyrics = LYRICS_NOT_FOUND)

            val artist = mediaMetadata.artists.joinToString { it.name }

            return coroutineScope {
                // UNLIMITED so `trySend` never blocks a provider coroutine; we close the channel
                // ourselves once we have a winner.
                val channel = Channel<Pair<String, String>>(Channel.UNLIMITED)
                val providerJobs: List<Job> =
                    providers.map { provider ->
                        launch(Dispatchers.IO) {
                            val lyrics = fetchProviderLyrics(provider, mediaMetadata, artist)
                            if (lyrics != null) {
                                channel.trySend(provider.name to lyrics)
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
                    var fallback: Pair<String, String>? = null
                    for ((providerName, lyrics) in channel) {
                        // Word-synced is the best we can get — return immediately and cancel the
                        // remaining providers so we don't keep their network requests alive.
                        if (LyricsUtils.hasWordSyncedLyrics(lyrics)) {
                            return@coroutineScope LyricsResult(providerName = providerName, lyrics = lyrics)
                        }
                        // Line-synced is good enough for an instant-load UX — return immediately.
                        if (LyricsUtils.isLineSyncedLrc(lyrics)) {
                            return@coroutineScope LyricsResult(providerName = providerName, lyrics = lyrics)
                        }
                        // Plain (unsynced) lyrics — keep the first one as a fallback while we keep
                        // waiting for a synced variant.
                        if (fallback == null) {
                            fallback = providerName to lyrics
                        }
                    }
                    // Channel closed = every provider finished without producing a synced result.
                    // Return the first plain result we saw, if any.
                    fallback?.let { (name, lyrics) ->
                        return@coroutineScope LyricsResult(providerName = name, lyrics = lyrics)
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
