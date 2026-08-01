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
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.supervisorScope
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
            val providers = orderedProviders().filter { it.isEnabled(context) }

            // Fan out all enabled providers in parallel. The previous implementation
            // iterated providers sequentially with `forEach`, which meant the search
            // dialog stayed on "Searching providers…" until every provider returned in
            // order — a single slow provider (Musixmatch can take 10–15s) held back
            // results from faster ones (LRCLIB ~100ms). Running them concurrently lets
            // results stream into the UI as each provider finishes.
            //
            // Each provider call is wrapped in a per-provider timeout so a hung
            // provider can't pin the search dialog indefinitely. Failures and timeouts
            // are reported but never propagated — the dialog just shows fewer results.
            withContext(Dispatchers.IO) {
                supervisorScope {
                    providers.map { provider ->
                        async {
                            try {
                                withTimeoutOrNull(PROVIDER_TIMEOUT_MS) {
                                    provider.getAllLyrics(mediaId, songTitle, songArtists, songAlbum, duration) lyricsCallback@{ lyrics ->
                                        val normalizedLyrics = LyricsUtils.lyricsOrNotFound(lyrics)
                                        if (normalizedLyrics == LYRICS_NOT_FOUND) return@lyricsCallback
                                        val result = LyricsResult(provider.name, normalizedLyrics)
                                        synchronized(allResult) {
                                            allResult += result
                                        }
                                        callback(result)
                                    }
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                reportException(e)
                            }
                        }
                    }.forEach { it.await() }
                }
            }
            cache.put(cacheKey, allResult.toList())
        }

        /**
         * Resolves lyrics from all providers in parallel and returns the best result by
         * (sync tier: word > line > plain) then by provider priority (lower index wins).
         *
         * This is the original priority-respecting implementation. The previous
         * "streaming first-result-wins" approach (commit 9975a15ac) was faster but
         * silently broke priority — a fast low-priority provider's line-synced lyrics
         * would preempt a slightly slower top-priority provider's word-synced lyrics
         * during the grace window, because the grace period wasn't long enough to cover
         * the typical 10–15s Musixmatch latency.
         *
         * Speed: each provider call is wrapped in [withTimeoutOrNull] so a single hung
         * provider can't pin the panel for its full 15–20s timeout. Providers that
         * exceed [PROVIDER_TIMEOUT_MS] are simply dropped from the ranking — they
         * contribute nothing to the result. The hard ceiling on panel load latency is
         * therefore min(provider timeout, slowest responsive provider's response time),
         * which in practice is the provider timeout (~8s) since at least one provider
         * usually responds within a few seconds.
         */
        private suspend fun fetchPriorityLyricsResult(
            providers: List<LyricsProvider>,
            mediaMetadata: MediaMetadata,
        ): LyricsResult {
            if (providers.isEmpty()) return LyricsResult(providerName = "", lyrics = LYRICS_NOT_FOUND)

            val artist = mediaMetadata.artists.joinToString { it.name }
            val results =
                supervisorScope {
                    providers
                        .map { provider ->
                            async(Dispatchers.IO) {
                                val lyrics =
                                    withTimeoutOrNull(PROVIDER_TIMEOUT_MS) {
                                        fetchProviderLyrics(provider, mediaMetadata, artist)
                                    }
                                if (lyrics == null) null else provider.name to lyrics
                            }
                        }.mapNotNull { it.await() }
                }

            if (results.isEmpty()) return LyricsResult(providerName = "", lyrics = LYRICS_NOT_FOUND)

            // Ranking: word-synced > line-synced > plain. `firstOrNull` walks the
            // results in provider-priority order (because `providers` is ordered), so
            // when multiple providers return the same tier the higher-priority one
            // wins — this is what restores the priority order the streaming
            // implementation broke.
            val wordSynced = results.firstOrNull { LyricsUtils.hasWordSyncedLyrics(it.second) }
            if (wordSynced != null) return LyricsResult(providerName = wordSynced.first, lyrics = wordSynced.second)

            val lineSynced = results.firstOrNull { LyricsUtils.isLineSyncedLrc(it.second) }
            if (lineSynced != null) return LyricsResult(providerName = lineSynced.first, lyrics = lineSynced.second)

            val first = results.first()
            return LyricsResult(providerName = first.first, lyrics = first.second)
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

            // Per-provider hard timeout. Provider calls that exceed this are cancelled
            // and dropped from ranking. Tuned to be long enough for typical provider
            // latency (~3–5s for Musixmatch under good conditions) but short enough
            // that a hung provider can't pin the lyrics panel.
            private const val PROVIDER_TIMEOUT_MS = 8_000L
        }
    }

data class LyricsResult(
    val providerName: String,
    val lyrics: String,
)
