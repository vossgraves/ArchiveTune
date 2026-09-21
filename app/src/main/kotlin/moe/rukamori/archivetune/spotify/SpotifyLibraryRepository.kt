/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 * Portions © vossgraves — github.com/vossgraves
 */

package moe.rukamori.archivetune.spotify

import android.content.Context
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.SpotifyAccessTokenExpiresAtKey
import moe.rukamori.archivetune.constants.SpotifyAccessTokenKey
import moe.rukamori.archivetune.constants.SpotifyAccountAvatarUrlKey
import moe.rukamori.archivetune.constants.SpotifyAccountNameKey
import moe.rukamori.archivetune.constants.SpotifyLibraryPlaylistsCacheKey
import moe.rukamori.archivetune.constants.SpotifyRecentlyPlayedCacheFetchedAtKey
import moe.rukamori.archivetune.constants.SpotifyRecentlyPlayedCacheKey
import moe.rukamori.archivetune.constants.SpotifySpDcKey
import moe.rukamori.archivetune.constants.SpotifySpKeyKey
import moe.rukamori.archivetune.spotify.models.SpotifyPaging
import moe.rukamori.archivetune.spotify.models.SpotifyPlayHistory
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylist
import moe.rukamori.archivetune.spotify.models.SpotifyAlbum
import moe.rukamori.archivetune.spotify.models.SpotifyArtist
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylistTracksRef
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.spotify.models.SpotifySearchResult
import moe.rukamori.archivetune.utils.clearWebAuthSession
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.reportException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpotifyLibraryRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val _playlists = MutableStateFlow<List<SpotifyPlaylist>>(emptyList())
        val playlists: StateFlow<List<SpotifyPlaylist>> = _playlists.asStateFlow()

        private val _isRefreshing = MutableStateFlow(false)
        val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

        private val _errorMessage = MutableStateFlow<String?>(null)
        val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

        internal val accountChanges = context.dataStore.data
            .map { it[SpotifySpDcKey].orEmpty() }
            .distinctUntilChanged()
            .map { Unit }

        private val tokenRefreshMutex = Mutex()
        private data class CachedSearch(
            val result: SpotifySearchResult,
            val expiresAtMs: Long,
        )

        private data class CachedMetadata(
            val metadata: MediaMetadata,
            val expiresAtMs: Long,
        )

        private data class CachedRecentlyPlayed(
            val items: List<SpotifyPlayHistory>,
            val fetchedAtMs: Long,
        ) {
            fun isFresh(nowMs: Long): Boolean = nowMs - fetchedAtMs < RECENTLY_PLAYED_CACHE_TTL_MS
        }

        private val searchCache =
            object : LinkedHashMap<String, CachedSearch>(SEARCH_CACHE_MAX_SIZE, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedSearch>?): Boolean =
                    size > SEARCH_CACHE_MAX_SIZE
            }
        private val metadataCache =
            object : LinkedHashMap<String, CachedMetadata>(METADATA_CACHE_MAX_SIZE, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedMetadata>?): Boolean =
                    size > METADATA_CACHE_MAX_SIZE
            }

        @Volatile
        private var recentlyPlayedCache: CachedRecentlyPlayed? = null

        /**
         * Serialises history reads. The History and Stats screens each hold their own view model, so
         * without this a cold start fires two identical window reads milliseconds apart — the shape
         * of request that gets a 429.
         */
        private val recentlyPlayedMutex = Mutex()

        /**
         * Wall-clock instant before which the history endpoint must not be called again, from the
         * last 429. Deliberately not persisted: it only means anything to the session that hit the
         * limit, and a stored deadline would go wrong the moment the device clock moved.
         */
        @Volatile
        private var recentlyPlayedBlockedUntilMs = 0L

        /** When the window was last read in full rather than as a delta; drives [needsFullHistoryRead]. */
        @Volatile
        private var recentlyPlayedFullReadAtMs = 0L

        suspend fun restoreCachedPlaylists() {
            withContext(Dispatchers.IO) {
                if (_playlists.value.isNotEmpty()) return@withContext
                val cached =
                    context.dataStore.data
                        .first()[SpotifyLibraryPlaylistsCacheKey]
                        .orEmpty()
                if (cached.isBlank()) return@withContext
                runCatching {
                    spotifyCacheJson.decodeFromString(
                        ListSerializer(SpotifyPlaylist.serializer()),
                        cached,
                    )
                }.onSuccess { playlists ->
                    _playlists.value = playlists
                }.onFailure { error ->
                    reportException(error)
                    context.dataStore.edit { prefs ->
                        prefs.remove(SpotifyLibraryPlaylistsCacheKey)
                    }
                }
            }
        }

        /**
         * The last play history written to disk, newest first, with the fetch time it was stored
         * under — seeding the in-memory snapshot as well, so the expiry covers data that came off
         * disk rather than only data this process fetched.
         */
        suspend fun restoreCachedRecentlyPlayed(): List<SpotifyPlayHistory>? =
            withContext(Dispatchers.IO) {
                recentlyPlayedCache?.let { return@withContext it.items }
                val prefs = context.dataStore.data.first()
                val cached = prefs[SpotifyRecentlyPlayedCacheKey].orEmpty()
                if (cached.isBlank()) return@withContext null
                runCatching {
                    spotifyCacheJson.decodeFromString(
                        ListSerializer(SpotifyPlayHistory.serializer()),
                        cached,
                    )
                }.onSuccess { items ->
                    recentlyPlayedCache =
                        CachedRecentlyPlayed(
                            items = items,
                            fetchedAtMs = prefs[SpotifyRecentlyPlayedCacheFetchedAtKey] ?: 0L,
                        )
                }.onFailure { error ->
                    reportException(error)
                    context.dataStore.edit { it.remove(SpotifyRecentlyPlayedCacheKey) }
                }.getOrNull()
            }

        suspend fun restoreSession(): SpotifyAccountSession =
            withContext(Dispatchers.IO) {
                val prefs = context.dataStore.data.first()
                val token = prefs[SpotifyAccessTokenKey].orEmpty()
                val expiresAt = prefs[SpotifyAccessTokenExpiresAtKey] ?: 0L
                val accountName = prefs[SpotifyAccountNameKey].orEmpty()
                val avatarUrl = prefs[SpotifyAccountAvatarUrlKey]

                if (token.isNotBlank() && expiresAt > System.currentTimeMillis() + TOKEN_EXPIRY_GRACE_MS) {
                    Spotify.accessToken = token
                    return@withContext SpotifyAccountSession(
                        isAuthenticated = true,
                        accountName = accountName,
                        accountAvatarUrl = avatarUrl,
                    )
                }

                val spDc = prefs[SpotifySpDcKey].orEmpty()
                if (spDc.isBlank()) return@withContext SpotifyAccountSession()

                refreshAccessToken(spDc = spDc, spKey = prefs[SpotifySpKeyKey].orEmpty())
                    .fold(
                        onSuccess = {
                            val refreshed = context.dataStore.data.first()
                            SpotifyAccountSession(
                                isAuthenticated = true,
                                accountName = refreshed[SpotifyAccountNameKey].orEmpty(),
                                accountAvatarUrl = refreshed[SpotifyAccountAvatarUrlKey],
                            )
                        },
                        onFailure = {
                            if (it is CancellationException) throw it
                            reportException(it)
                            SpotifyAccountSession()
                        },
                    )
            }

        suspend fun connectWithCookies(
            spDc: String,
            spKey: String,
        ): SpotifyAccountSession =
            withContext(Dispatchers.IO) {
                var credentialsChanged = false
                context.dataStore.edit { prefs ->
                    credentialsChanged =
                        prefs[SpotifySpDcKey] != spDc || prefs[SpotifySpKeyKey].orEmpty() != spKey
                    prefs[SpotifySpDcKey] = spDc
                    prefs.remove(SpotifyLibraryPlaylistsCacheKey)
                    if (spKey.isNotBlank()) {
                        prefs[SpotifySpKeyKey] = spKey
                    } else {
                        prefs.remove(SpotifySpKeyKey)
                    }
                    if (credentialsChanged) {
                        prefs.remove(SpotifyAccessTokenKey)
                        prefs.remove(SpotifyAccessTokenExpiresAtKey)
                    }
                }
                if (credentialsChanged) {
                    Spotify.accessToken = null
                    clearCatalogCaches()
                    clearRecentlyPlayed()
                }
                _playlists.value = emptyList()
                _errorMessage.value = null
                refreshAccessToken(spDc = spDc, spKey = spKey).getOrThrow()
                val prefs = context.dataStore.data.first()
                SpotifyAccountSession(
                    isAuthenticated = true,
                    accountName = prefs[SpotifyAccountNameKey].orEmpty(),
                    accountAvatarUrl = prefs[SpotifyAccountAvatarUrlKey],
                )
            }

        suspend fun logout() {
            withContext(Dispatchers.IO) {
                context.dataStore.edit { prefs ->
                    prefs.remove(SpotifySpDcKey)
                    prefs.remove(SpotifySpKeyKey)
                    prefs.remove(SpotifyAccessTokenKey)
                    prefs.remove(SpotifyAccessTokenExpiresAtKey)
                    prefs.remove(SpotifyAccountNameKey)
                    prefs.remove(SpotifyAccountAvatarUrlKey)
                    prefs.remove(SpotifyLibraryPlaylistsCacheKey)
                }
                _playlists.value = emptyList()
                _errorMessage.value = null
                Spotify.accessToken = null
                clearCatalogCaches()
                clearRecentlyPlayed()
                runCatching { clearWebAuthSession(context) }
                    .onFailure(::reportException)
            }
        }

        suspend fun refreshPlaylists(): List<SpotifyPlaylist> =
            withContext(Dispatchers.IO) {
                _isRefreshing.value = true
                _errorMessage.value = null
                try {
                    ensureAuthenticated()
                    refreshProfile()
                    val loaded = fetchAllPlaylists()
                    _playlists.value = loaded
                    context.dataStore.edit { prefs ->
                        prefs[SpotifyLibraryPlaylistsCacheKey] =
                            spotifyCacheJson.encodeToString(
                                ListSerializer(SpotifyPlaylist.serializer()),
                                loaded,
                            )
                    }
                    loaded
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    reportException(error)
                    _errorMessage.value = error.message
                    _playlists.value
                } finally {
                    _isRefreshing.value = false
                }
            }

        /**
         * Guarantee the playlist list is populated: disk cache first, then a network fetch the
         * first time it is needed on an empty cache.
         */
        suspend fun ensurePlaylists() {
            withContext(Dispatchers.IO) {
                if (_isRefreshing.value) return@withContext
                restoreCachedPlaylists()
                if (_playlists.value.isNotEmpty()) return@withContext
                refreshPlaylists()
            }
        }

        suspend fun playlist(playlistId: String): SpotifyPlaylist =
            withContext(Dispatchers.IO) {
                ensureAuthenticated()
                spotifyCallWithTokenRetry {
                    Spotify.playlist(playlistId).getOrThrow()
                }
            }

        suspend fun album(albumId: String): SpotifyAlbum =
            withContext(Dispatchers.IO) {
                ensureAuthenticated()
                spotifyCallWithTokenRetry {
                    Spotify.album(albumId).getOrThrow()
                }
            }

        suspend fun artist(artistId: String): SpotifyArtist =
            withContext(Dispatchers.IO) {
                ensureAuthenticated()
                spotifyCallWithTokenRetry {
                    Spotify.artist(artistId).getOrThrow()
                }
            }

        suspend fun playlistTracks(playlistId: String): List<SpotifyTrack> =
            withContext(Dispatchers.IO) {
                ensureAuthenticated()
                val tracks = ArrayList<SpotifyTrack>()
                var offset = 0
                val limit = 50

                while (true) {
                    val page =
                        spotifyCallWithTokenRetry {
                            Spotify
                                .playlistTracks(
                                    playlistId = playlistId,
                                    limit = limit,
                                    offset = offset,
                                ).getOrThrow()
                        }
                    currentCoroutineContext().ensureActive()
                    tracks += page.items.mapNotNull { it.track?.takeUnless(SpotifyTrack::isLocal) }
                    offset = page.nextOffset?.takeIf { it > offset } ?: break
                }

                tracks
            }

        /**
         * Every artist the user follows, paged out. Backs the Library's Artists section on the
         * Spotify source — the same shape [likedSongs] has, and for the same reason: the Library
         * shows one list, not one page of one.
         */
        suspend fun libraryArtists(): List<SpotifyArtist> =
            withContext(Dispatchers.IO) {
                ensureAuthenticated()
                collectPages { limit, offset ->
                    spotifyCallWithTokenRetry { Spotify.myArtists(limit = limit, offset = offset).getOrThrow() }
                }
            }

        /**
         * The user's play history, most recent first — cached, delta-read and rate-limit aware.
         *
         *  - **One read per expiry.** Rows younger than [RECENTLY_PLAYED_CACHE_TTL_MS] are served
         *    as-is, and callers that arrive together wait on one in-flight read instead of racing.
         *  - **Ask only for what changed.** A read carries `after` = the newest play already held,
         *    and merges the plays since into the cached window; only a read with nothing to anchor on
         *    — or one due to heal the cursor, see [needsFullHistoryRead] — takes the whole window.
         *  - **Stale beats an error, and a wait beats an empty screen.** A failure with rows cached
         *    returns them; a 429 records its window so nothing calls again until it clears; and a
         *    429 with nothing cached and a short named window is waited out by [readRecentlyPlayed]
         *    rather than reported.
         *
         * [force] (pull-to-refresh) skips the expiry, never the gate: refreshing into an active rate
         * limit is what turns one 429 into a loop, so a forced read during a cooldown returns cache.
         */
        suspend fun recentlyPlayed(force: Boolean = false): List<SpotifyPlayHistory> =
            withContext(Dispatchers.IO) {
                recentlyPlayedMutex.withLock {
                    if (recentlyPlayedCache == null) restoreCachedRecentlyPlayed()
                    val cached = recentlyPlayedCache
                    val now = System.currentTimeMillis()
                    if (!force && cached != null && cached.isFresh(now)) {
                        return@withLock cached.items
                    }

                    val blockedForMs = recentlyPlayedBlockedUntilMs - now
                    if (blockedForMs > 0) {
                        cached?.let { return@withLock it.items }
                        throw rateLimitedException(blockedForMs)
                    }

                    ensureAuthenticated()
                    val cursorMillis = cached?.items?.newestPlayedAtMillis()
                    val fullRead = needsFullHistoryRead(cursorMillis, recentlyPlayedFullReadAtMs, now)
                    val fetched =
                        try {
                            readRecentlyPlayed(
                                afterMillis = cursorMillis.takeUnless { fullRead },
                                hasCachedRows = cached != null,
                            )
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Throwable) {
                            cached?.let { return@withLock it.items }
                            throw error
                        }

                    val merged =
                        mergePlayHistory(
                            newer = fetched,
                            cached = cached?.items.orEmpty(),
                        )
                    val fetchedAtMs = System.currentTimeMillis()
                    recentlyPlayedCache = CachedRecentlyPlayed(items = merged, fetchedAtMs = fetchedAtMs)
                    if (fullRead) recentlyPlayedFullReadAtMs = fetchedAtMs
                    recentlyPlayedBlockedUntilMs = 0L
                    context.dataStore.edit { prefs ->
                        prefs[SpotifyRecentlyPlayedCacheKey] =
                            spotifyCacheJson.encodeToString(
                                ListSerializer(SpotifyPlayHistory.serializer()),
                                merged,
                            )
                        prefs[SpotifyRecentlyPlayedCacheFetchedAtKey] = fetchedAtMs
                    }
                    merged
                }
            }

        /**
         * Whether the cached history is young enough that a screen visit need not read the endpoint.
         *
         * The section loader's own guard is "items exist, do not fetch again", which would leave a
         * history from last week on screen for as long as the process lives; this asks the
         * repository's expiry instead, and the refresh it allows goes through [recentlyPlayed], where
         * the single-flight and the rate-limit gate live.
         */
        suspend fun recentlyPlayedIsFresh(): Boolean =
            withContext(Dispatchers.IO) {
                if (recentlyPlayedCache == null) restoreCachedRecentlyPlayed()
                recentlyPlayedCache?.isFresh(System.currentTimeMillis()) ?: false
            }

        /**
         * The exception thrown when the endpoint is rate limited and there is no cached history to
         * show instead. Carries the remaining wait so callers can say something true about it.
         */
        private fun rateLimitedException(waitMs: Long): Spotify.SpotifyException {
            val waitSec = (waitMs + 999L) / 1000L
            return Spotify.SpotifyException(
                statusCode = 429,
                message = "Rate limited — Spotify asked to wait ${waitSec}s before the history endpoint may be called again",
                retryAfterSec = waitSec,
            )
        }

        /**
         * One history read, plus the single retry a `Retry-After`-bearing 429 earns.
         *
         * A 429 that names its window is an instruction, not a verdict: the plays are that many
         * seconds away, so giving up on a screen with nothing on it turns a short wait into a
         * permanent empty state. [historyRetryWaitMillis] decides when that retry is owed and how
         * long it waits; what matters here is the bound. It runs at most once — [retriesLeft] is 1 on
         * the way in and 0 on the way back — so no header value can make this spin, and a 429 with no
         * window is not retried at all. The cooldown is written before the wait, so a concurrent read
         * sees it and cannot slip in ahead of the retry.
         *
         * The wait is a [delay], so a screen that leaves cancels the read where it stands instead of
         * leaving a timer behind to fire against a token the user may already have dropped.
         */
        private suspend fun readRecentlyPlayed(
            afterMillis: Long?,
            hasCachedRows: Boolean,
            retriesLeft: Int = 1,
        ): List<SpotifyPlayHistory> =
            try {
                readRecentlyPlayedOnce(afterMillis)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (!isSpotifyRateLimitMessage(error.message)) throw error
                val retryAfterSec = (error as? Spotify.SpotifyException)?.retryAfterSec
                recentlyPlayedBlockedUntilMs =
                    maxOf(
                        recentlyPlayedBlockedUntilMs,
                        System.currentTimeMillis() + rateLimitCooldownMillis(retryAfterSec),
                    )
                val waitMs = if (retriesLeft > 0) historyRetryWaitMillis(retryAfterSec, hasCachedRows) else null
                if (waitMs == null) throw error
                delay(waitMs)
                readRecentlyPlayed(afterMillis, hasCachedRows, retriesLeft = retriesLeft - 1)
            }

        private suspend fun readRecentlyPlayedOnce(afterMillis: Long?): List<SpotifyPlayHistory> =
            spotifyCallWithTokenRetry {
                Spotify.recentlyPlayed(afterMillis = afterMillis).getOrThrow()
            }.items

        /**
         * Drops the history snapshot and its expiry, and any in-flight rate-limit gate. Called when
         * the account changes: the plays belong to the old account, and the cooldown would otherwise
         * delay the new account's first read for no reason.
         */
        private suspend fun clearRecentlyPlayed() {
            recentlyPlayedCache = null
            recentlyPlayedBlockedUntilMs = 0L
            recentlyPlayedFullReadAtMs = 0L
            context.dataStore.edit { prefs ->
                prefs.remove(SpotifyRecentlyPlayedCacheKey)
                prefs.remove(SpotifyRecentlyPlayedCacheFetchedAtKey)
            }
        }

        /** Every album the user has saved. Backs the Library's Albums section on the Spotify source. */
        suspend fun libraryAlbums(): List<SpotifyAlbum> =
            withContext(Dispatchers.IO) {
                ensureAuthenticated()
                collectPages { limit, offset ->
                    spotifyCallWithTokenRetry { Spotify.myAlbums(limit = limit, offset = offset).getOrThrow() }
                }
            }

        /**
         * Drains a paged Spotify endpoint. Stops on an empty page, on reaching the reported total,
         * or on a short page — the last of those matters because `total` is not always accurate on
         * the libraryV3 responses, and without it the loop would spin on the final page.
         */
        private suspend fun <T> collectPages(page: suspend (limit: Int, offset: Int) -> SpotifyPaging<T>): List<T> {
            val all = ArrayList<T>()
            var offset = 0
            val limit = 50
            while (true) {
                val result = page(limit, offset)
                currentCoroutineContext().ensureActive()
                all += result.items
                offset = result.nextOffset?.takeIf { it > offset } ?: break
            }
            return all
        }

        suspend fun likedSongs(): List<SpotifyTrack> =
            withContext(Dispatchers.IO) {
                ensureAuthenticated()
                val tracks = ArrayList<SpotifyTrack>()
                var offset = 0
                val limit = 50

                while (true) {
                    val page =
                        spotifyCallWithTokenRetry {
                            Spotify.likedSongs(limit = limit, offset = offset).getOrThrow()
                        }
                    currentCoroutineContext().ensureActive()
                    tracks += page.items.mapNotNull { it.track.takeUnless(SpotifyTrack::isLocal) }
                    offset = page.nextOffset?.takeIf { it > offset } ?: break
                }

                tracks
            }

        /**
         * Searches Spotify's catalog after restoring/refreshing the persisted Web Player session.
         * Results are cached briefly because the Search page and metadata enrichment can ask for
         * the same query in quick succession.
         */
        suspend fun search(
            query: String,
            types: List<String> = listOf("track", "album", "artist", "playlist"),
            limit: Int = 20,
            offset: Int = 0,
        ): SpotifySearchResult =
            withContext(Dispatchers.IO) {
                val normalizedQuery = query.trim()
                require(normalizedQuery.isNotEmpty()) { "Spotify search query is empty" }
                ensureAuthenticated()
                val cacheKey = "$normalizedQuery|${types.joinToString(",")}|$limit|$offset"
                val now = System.currentTimeMillis()
                synchronized(searchCache) {
                    searchCache[cacheKey]
                        ?.takeIf { it.expiresAtMs > now }
                        ?.let { return@withContext it.result }
                }

                val result =
                    spotifyCallWithTokenRetry {
                        Spotify
                            .search(
                                query = normalizedQuery,
                                types = types,
                                limit = limit,
                                offset = offset,
                            ).getOrThrow()
                    }
                synchronized(searchCache) {
                    searchCache[cacheKey] = CachedSearch(result, now + SEARCH_CACHE_TTL_MS)
                }
                result
            }

        /**
         * Enriches YouTube-derived metadata with the closest Spotify catalog track. The returned
         * media id remains the playable YouTube id; Spotify is only the metadata identity/source.
         */
        suspend fun enrichMetadata(metadata: MediaMetadata): MediaMetadata? =
            withContext(Dispatchers.IO) {
                if (metadata.spotifyTrackId != null) return@withContext metadata
                val cacheKey = "${metadata.id}|${metadata.title}|${metadata.artists.joinToString { it.name }}"
                val now = System.currentTimeMillis()
                synchronized(metadataCache) {
                    metadataCache[cacheKey]
                        ?.takeIf { it.expiresAtMs > now }
                        ?.let { return@withContext it.metadata }
                }

                val artist = metadata.artists.firstOrNull()?.name.orEmpty()
                val query = listOf(artist, metadata.title).filter { it.isNotBlank() }.joinToString(" ")
                if (query.isBlank()) return@withContext null
                val tracks =
                    runCatching {
                        search(query = query, types = listOf("track"), limit = 8)
                            .tracks
                            ?.items
                            .orEmpty()
                    }.getOrElse { error ->
                        if (error is CancellationException) throw error
                        return@withContext null
                    }
                val best =
                    tracks
                        .map { track ->
                            track to
                                SpotifyMapper.matchScore(
                                    spotifyTitle = track.name,
                                    spotifyArtist = track.artists.joinToString(" ") { it.name },
                                    spotifyDurationMs = track.durationMs,
                                    candidateTitle = metadata.title,
                                    candidateArtist = metadata.artists.joinToString(" ") { it.name },
                                    candidateDurationSec = metadata.duration.takeIf { it > 0 },
                                )
                        }.maxByOrNull { it.second }
                        ?.takeIf { it.second >= METADATA_MATCH_THRESHOLD }
                        ?.first ?: return@withContext null
                val enriched =
                    metadata.copy(
                        title = best.name,
                        artists =
                            best.artists.map { artistItem ->
                                MediaMetadata.Artist(
                                    id = artistItem.id,
                                    name = artistItem.name,
                                )
                            },
                        duration = if (best.durationMs > 0) best.durationMs / 1000 else metadata.duration,
                        thumbnailUrl = SpotifyMapper.getTrackThumbnail(best) ?: metadata.thumbnailUrl,
                        album =
                            best.album?.let { album ->
                                MediaMetadata.Album(id = album.id, title = album.name)
                            } ?: metadata.album,
                        explicit = metadata.explicit || best.explicit,
                        spotifyTrackId = best.id.takeIf(String::isNotBlank),
                    )
                synchronized(metadataCache) {
                    metadataCache[cacheKey] = CachedMetadata(enriched, now + METADATA_CACHE_TTL_MS)
                }
                enriched
            }

        private fun clearCatalogCaches() {
            synchronized(searchCache) { searchCache.clear() }
            synchronized(metadataCache) { metadataCache.clear() }
        }


        /**
         * Returns a usable Spotify access token, minting one from the stored `sp_dc` cookie when
         * the cached token is missing or expired, or null when the user has not connected a Spotify
         * account.
         */
        suspend fun ensureAccessToken(): String? =
            runCatching {
                ensureAuthenticated()
                Spotify.accessToken?.takeIf { it.isNotBlank() }
            }.getOrNull()

        private suspend fun ensureAuthenticated() {
            val prefs = context.dataStore.data.first()
            val token = prefs[SpotifyAccessTokenKey].orEmpty()
            val expiresAt = prefs[SpotifyAccessTokenExpiresAtKey] ?: 0L
            if (token.isNotBlank() && expiresAt > System.currentTimeMillis() + TOKEN_EXPIRY_GRACE_MS) {
                Spotify.accessToken = token
                return
            }

            val spDc = prefs[SpotifySpDcKey].orEmpty()
            if (spDc.isBlank()) {
                throw IllegalStateException(context.getString(R.string.spotify_not_connected))
            }
            refreshAccessToken(spDc = spDc, spKey = prefs[SpotifySpKeyKey].orEmpty()).getOrThrow()
        }

        private suspend fun refreshAccessToken(
            spDc: String,
            spKey: String,
            rejectedAccessToken: String? = null,
        ): Result<Unit> =
            try {
                tokenRefreshMutex.withLock {
                    val prefs = context.dataStore.data.first()
                    val storedAccessToken = prefs[SpotifyAccessTokenKey].orEmpty()
                    val storedExpiresAt = prefs[SpotifyAccessTokenExpiresAtKey] ?: 0L
                    val credentialsMatch =
                        prefs[SpotifySpDcKey].orEmpty() == spDc &&
                            prefs[SpotifySpKeyKey].orEmpty() == spKey
                    val canReuseStoredToken =
                        credentialsMatch &&
                            storedAccessToken.isNotBlank() &&
                            storedExpiresAt > System.currentTimeMillis() + TOKEN_EXPIRY_GRACE_MS &&
                            (rejectedAccessToken == null || storedAccessToken != rejectedAccessToken)

                    if (canReuseStoredToken) {
                        Spotify.accessToken = storedAccessToken
                        return@withLock Result.success(Unit)
                    }

                    val token = SpotifyAuth.fetchAccessToken(spDc = spDc, spKey = spKey).getOrThrow()
                    Spotify.accessToken = token.accessToken
                    context.dataStore.edit { prefs ->
                        prefs[SpotifyAccessTokenKey] = token.accessToken
                        prefs[SpotifyAccessTokenExpiresAtKey] = token.accessTokenExpirationTimestampMs
                    }
                    refreshProfile()
                    Result.success(Unit)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Result.failure(error)
            }

        private suspend fun refreshProfile() {
            Spotify
                .me()
                .onSuccess { user ->
                    context.dataStore.edit { prefs ->
                        prefs[SpotifyAccountNameKey] = user.displayName.orEmpty()
                        user.images
                            .firstOrNull()
                            ?.url
                            ?.let { prefs[SpotifyAccountAvatarUrlKey] = it }
                            ?: prefs.remove(SpotifyAccountAvatarUrlKey)
                    }
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                }
        }

        private suspend fun fetchAllPlaylists(): List<SpotifyPlaylist> {
            val playlists = ArrayList<SpotifyPlaylist>()
            var offset = 0
            val limit = 50

            while (true) {
                val page =
                    spotifyCallWithTokenRetry {
                        Spotify.myPlaylists(limit = limit, offset = offset).getOrThrow()
                    }
                if (page.items.isEmpty()) break
                // The libraryV3 GraphQL response often omits `tracks.totalCount` for leaf
                // playlists. Count lookups run with bounded concurrency so the wall time is
                // roughly ceil(N / 8) round trips instead of N serial ones, each of which could
                // carry its own 429 Retry-After backoff (easily 4-5s for 100 playlists). The
                // semaphore matters: without it Spotify 429s the burst and the backoff compounds
                // the wall time.
                val pageItems = page.items
                val enriched =
                    coroutineScope {
                        val semaphore = Semaphore(COUNT_FETCH_CONCURRENCY)
                        pageItems
                            .map { playlist ->
                                async(Dispatchers.IO) {
                                    if (playlist.tracks?.total != null) {
                                        playlist
                                    } else {
                                        semaphore.withPermit {
                                            playlistTrackCount(playlist.id)
                                                ?.let { playlist.copy(tracks = SpotifyPlaylistTracksRef(total = it)) }
                                                ?: playlist
                                        }
                                    }
                                }
                            }.awaitAll()
                    }
                playlists += enriched
                offset += page.items.size
                if (offset >= page.total || page.items.size < limit) break
            }

            return playlists
        }

        private suspend fun playlistTrackCount(playlistId: String): Int? =
            try {
                spotifyCallWithTokenRetry {
                    Spotify
                        .playlistTracks(
                            playlistId = playlistId,
                            limit = 1,
                            offset = 0,
                        ).getOrThrow()
                }.total
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                reportException(error)
                null
            }

        private suspend fun <T> spotifyCallWithTokenRetry(block: suspend () -> T): T =
            Spotify.accessToken.let { rejectedAccessToken ->
                runCatching { block() }
                    .getOrElse { error ->
                        if ((error as? Spotify.SpotifyException)?.statusCode != 401) throw error
                        val prefs = context.dataStore.data.first()
                        val spDc = prefs[SpotifySpDcKey].orEmpty()
                        if (spDc.isBlank()) throw error
                        refreshAccessToken(
                            spDc = spDc,
                            spKey = prefs[SpotifySpKeyKey].orEmpty(),
                            rejectedAccessToken = rejectedAccessToken,
                        ).getOrThrow()
                        block()
                    }
            }

        companion object {
            private const val TOKEN_EXPIRY_GRACE_MS = 60_000L
            private const val SEARCH_CACHE_MAX_SIZE = 64
            private const val METADATA_CACHE_MAX_SIZE = 128
            private const val SEARCH_CACHE_TTL_MS = 5 * 60 * 1000L
            private const val METADATA_CACHE_TTL_MS = 15 * 60 * 1000L
            private const val RECENTLY_PLAYED_CACHE_TTL_MS = 5 * 60 * 1000L
            private const val METADATA_MATCH_THRESHOLD = 0.58

            /**
             * In-flight parallel track-count fetches in [fetchAllPlaylists]. 8 keeps the burst
             * under Spotify's 429 threshold while cutting the wall time ~8x vs sequential.
             */
            private const val COUNT_FETCH_CONCURRENCY = 8
            private val spotifyCacheJson =
                Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                }
        }
    }

data class SpotifyAccountSession(
    val isAuthenticated: Boolean = false,
    val accountName: String = "",
    val accountAvatarUrl: String? = null,
)
