/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Artist
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.innertube.pages.ChartsPage
import moe.rukamori.archivetune.innertube.pages.MoodAndGenres
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class SearchDiscoveryData(
    val moodAndGenres: List<MoodAndGenres.Item>,
    val newReleaseAlbums: List<AlbumItem>,
    val chartSections: List<ChartsPage.ChartSection>,
    val suggestedSongs: List<SongItem>,
    val searchedAlbums: List<AlbumItem>,
    val suggestedArtists: List<ArtistItem>,
) {
    val isEmpty: Boolean
        get() =
            moodAndGenres.isEmpty() &&
                newReleaseAlbums.isEmpty() &&
                chartSections.isEmpty() &&
                suggestedSongs.isEmpty() &&
                searchedAlbums.isEmpty() &&
                suggestedArtists.isEmpty()
}

@Singleton
class SearchDiscoveryRepository
    @Inject
    constructor(
        private val database: MusicDatabase,
    ) {
        // ── In-memory TTL cache ────────────────────────────────────────────────────────
        //
        // Re-entering the Search tab previously re-fired 20+ HTTP requests every time,
        // making the tab feel permanently slow. Cache the last successful discovery
        // snapshot for a short window so the user sees content immediately on re-entry
        // and only pays the network cost on pull-to-refresh / cache expiry.
        private data class CachedSnapshot(
            val data: SearchDiscoveryData,
            val expiresAtMs: Long,
        )

        private val cache = ConcurrentHashMap<String, CachedSnapshot>(1)
        private val cacheMutex = Mutex()

        suspend fun loadDiscovery(forceRefresh: Boolean = false): Result<SearchDiscoveryData> =
            withContext(Dispatchers.IO) {
                if (!forceRefresh) {
                    cache[CacheKey]?.let { snapshot ->
                        if (snapshot.expiresAtMs > System.currentTimeMillis()) {
                            return@withContext Result.success(snapshot.data)
                        }
                    }
                }

                try {
                    val data = loadDiscoveryFromNetwork()
                    cache[CacheKey] =
                        CachedSnapshot(
                            data = data,
                            expiresAtMs = System.currentTimeMillis() + CACHE_TTL_MS,
                        )
                    Result.success(data)
                } catch (throwable: Throwable) {
                    if (throwable is CancellationException) throw throwable
                    // On failure, serve stale cache if we still have one rather than showing
                    // a hard error — the user is far less annoyed by slightly-old content
                    // than by an empty state.
                    cache[CacheKey]?.let { snapshot ->
                        if (snapshot.expiresAtMs > System.currentTimeMillis() - STALE_GRACE_MS) {
                            return@withContext Result.success(snapshot.data)
                        }
                    }
                    Result.failure(throwable)
                }
            }

        private suspend fun loadDiscoveryFromNetwork(): SearchDiscoveryData =
            coroutineScope {
                // ── Fan out every sub-load in parallel ─────────────────────────────────
                // Previously explore/charts used getOrThrow() — a single transient failure
                // nuked the entire discovery load. Now each sub-load returns its result
                // (or null on failure) and the UI gets partial content rather than an
                // error state.
                val explorePageDeferred =
                    async {
                        runCatching { YouTube.explore().getOrThrow() }.getOrNull()
                    }
                val chartsPageDeferred =
                    async {
                        runCatching { YouTube.getChartsPage().getOrThrow() }.getOrNull()
                    }
                val suggestedSongsDeferred = async { loadSuggestedSongs() }
                val searchedAlbumsDeferred =
                    async {
                        searchItems<AlbumItem>(
                            query = TopAlbumsQuery,
                            filter = YouTube.SearchFilter.FILTER_ALBUM,
                        )
                    }
                val suggestedArtistsDeferred = async { loadSuggestedArtists() }

                val explorePage = explorePageDeferred.await()
                val chartsPage = chartsPageDeferred.await()

                SearchDiscoveryData(
                    moodAndGenres = explorePage?.moodAndGenres.orEmpty(),
                    newReleaseAlbums = explorePage?.newReleaseAlbums.orEmpty(),
                    chartSections = chartsPage?.sections.orEmpty(),
                    suggestedSongs = suggestedSongsDeferred.await(),
                    searchedAlbums = searchedAlbumsDeferred.await(),
                    suggestedArtists = suggestedArtistsDeferred.await(),
                )
            }

        private suspend inline fun <reified T> searchItems(
            query: String,
            filter: YouTube.SearchFilter,
        ): List<T> =
            try {
                YouTube
                    .search(
                        query = query,
                        filter = filter,
                        useAccountContext = false,
                    ).getOrThrow()
                    .items
                    .filterIsInstance<T>()
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                emptyList()
            }

        private suspend fun loadSuggestedSongs(): List<SongItem> =
            coroutineScope {
                val seedSongs =
                    database
                        .mostPlayedSongs(
                            fromTimeStamp = AllHistoryTimestamp,
                            limit = MaxHistoryLookupItems,
                        ).first()
                        .filterNot { song -> song.song.isLocal }
                        .take(MaxSuggestionSeedItems)
                val seedSongIds = seedSongs.mapTo(HashSet()) { song -> song.id }

                seedSongs
                    .map { song ->
                        async {
                            loadRelatedSongs(song)
                                .ifEmpty { searchRelatedSongs(song) }
                        }
                    }.awaitAll()
                    .flatten()
                    .filterNot { song -> song.id in seedSongIds }
                    .distinctBy { song -> song.id }
                    .take(MaxSuggestedItems)
            }

        private suspend fun loadRelatedSongs(song: Song): List<SongItem> =
            try {
                val nextResult = YouTube.next(WatchEndpoint(videoId = song.id)).getOrThrow()
                val relatedSongs =
                    nextResult
                        .relatedEndpoint
                        ?.let { endpoint -> YouTube.related(endpoint).getOrNull()?.songs }
                        .orEmpty()
                (relatedSongs + nextResult.items).distinctBy { item -> item.id }
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                emptyList()
            }

        private suspend fun searchRelatedSongs(song: Song): List<SongItem> =
            searchItems(
                query =
                    buildString {
                        append(song.title)
                        song.artists
                            .firstOrNull()
                            ?.name
                            ?.takeIf(String::isNotBlank)
                            ?.let { artistName ->
                                append(' ')
                                append(artistName)
                            }
                    },
                filter = YouTube.SearchFilter.FILTER_SONG,
            )

        private suspend fun loadSuggestedArtists(): List<ArtistItem> =
            coroutineScope {
                val seedArtists =
                    database
                        .mostPlayedArtists(
                            fromTimeStamp = AllHistoryTimestamp,
                            limit = MaxHistoryLookupItems,
                        ).first()
                        .filter { artist -> artist.artist.isYouTubeArtist }
                        .take(MaxSuggestionSeedItems)
                val seedArtistIds = seedArtists.mapTo(HashSet()) { artist -> artist.id }

                seedArtists
                    .map { artist ->
                        async {
                            loadRelatedArtists(artist)
                                .ifEmpty { searchRelatedArtists(artist) }
                        }
                    }.awaitAll()
                    .flatten()
                    .filterNot { artist -> artist.id in seedArtistIds }
                    .distinctBy { artist -> artist.id }
                    .take(MaxSuggestedItems)
            }

        private suspend fun loadRelatedArtists(artist: Artist): List<ArtistItem> =
            try {
                YouTube
                    .artist(artist.id)
                    .getOrThrow()
                    .sections
                    .flatMap { section -> section.items }
                    .filterIsInstance<ArtistItem>()
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                emptyList()
            }

        private suspend fun searchRelatedArtists(artist: Artist): List<ArtistItem> =
            searchItems(
                query = artist.title,
                filter = YouTube.SearchFilter.FILTER_ARTIST,
            )

        private companion object {
            const val AllHistoryTimestamp = 0L
            const val MaxHistoryLookupItems = 36
            // Halved from 6 → 3: each seed song fires 1 next() + 1 related() call (and a
            // search fallback on failure), and each seed artist fires 1 artist() call (and
            // a search fallback). 6 seeds produced ~12-18 sequential HTTP round-trips that
            // blocked the whole Search tab on first load. 3 keeps the suggestions diverse
            // while cutting latency roughly in half.
            const val MaxSuggestionSeedItems = 3
            const val MaxSuggestedItems = 12
            const val TopAlbumsQuery = "top albums"

            const val CacheKey = "default"
            // 5-minute TTL: long enough that re-entering the Search tab a few times in a
            // session is instant, short enough that the moods/charts/suggestions stay fresh.
            const val CACHE_TTL_MS = 5L * 60 * 1000
            // Serve stale cache for up to 30 minutes after expiry on network failure —
            // better to show old content than an empty screen.
            const val STALE_GRACE_MS = 30L * 60 * 1000
        }
    }
