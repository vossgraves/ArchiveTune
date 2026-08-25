/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.spotify

import android.content.Context
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.SpotifyAccessTokenExpiresAtKey
import moe.rukamori.archivetune.constants.SpotifyAccessTokenKey
import moe.rukamori.archivetune.constants.SpotifyAccountAvatarUrlKey
import moe.rukamori.archivetune.constants.SpotifyAccountNameKey
import moe.rukamori.archivetune.constants.SpotifyLibraryPlaylistsCacheKey
import moe.rukamori.archivetune.constants.SpotifySpDcKey
import moe.rukamori.archivetune.constants.SpotifySpKeyKey
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

        private val tokenRefreshMutex = Mutex()
        private data class CachedSearch(
            val result: SpotifySearchResult,
            val expiresAtMs: Long,
        )

        private data class CachedMetadata(
            val metadata: MediaMetadata,
            val expiresAtMs: Long,
        )

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
                    if (page.items.isEmpty()) break
                    val pageTracks = page.items.mapNotNull { it.track?.takeUnless(SpotifyTrack::isLocal) }
                    tracks += pageTracks
                    offset += page.items.size
                    if (offset >= page.total || page.items.size < limit) break
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
         * Returns a usable Spotify access token, minting one from the stored `sp_dc`
         * cookie when the cached token is missing or expired, or null when the user has
         * not connected a Spotify account.
         *
         * Exists because features outside the Spotify library screens need the session
         * too. `SpotifyCanvasProvider` read the `Spotify.accessToken` global directly,
         * which is only populated as a side effect of some *earlier* Spotify library
         * call — so on a fresh launch the official Canvas endpoint was skipped for want
         * of a token even though the user was connected, and canvas silently fell through
         * to the (empty) community resolver. Going through the repository reuses the same
         * mutex, DataStore cache and refresh logic as every other Spotify call.
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
                playlists +=
                    page.items.map { playlist ->
                        if (playlist.tracks?.total != null) {
                            playlist
                        } else {
                            playlistTrackCount(playlist.id)
                                ?.let { playlist.copy(tracks = SpotifyPlaylistTracksRef(total = it)) }
                                ?: playlist
                        }
                    }
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
            private const val METADATA_MATCH_THRESHOLD = 0.58
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
