/*
 * ArchiveTune (2026)
 * Â© Rukamori â€” github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.pages.ChartsPage
import moe.rukamori.archivetune.innertube.pages.MoodAndGenres
import javax.inject.Inject
import javax.inject.Singleton

data class SearchDiscoveryData(
    val moodAndGenres: List<MoodAndGenres.Item>,
    val newReleaseAlbums: List<AlbumItem>,
    val chartSections: List<ChartsPage.ChartSection>,
    val searchedSongs: List<SongItem>,
    val searchedAlbums: List<AlbumItem>,
    val searchedArtists: List<ArtistItem>,
)

@Singleton
class SearchDiscoveryRepository
    @Inject
    constructor() {
        suspend fun loadDiscovery(): Result<SearchDiscoveryData> =
            withContext(Dispatchers.IO) {
                try {
                    coroutineScope {
                        val explorePageDeferred = async { YouTube.explore().getOrThrow() }
                        val chartsPageDeferred = async { YouTube.getChartsPage().getOrThrow() }
                        val searchedSongsDeferred =
                            async {
                                searchItems<SongItem>(
                                    query = TrendingSongsQuery,
                                    filter = YouTube.SearchFilter.FILTER_SONG,
                                )
                            }
                        val searchedAlbumsDeferred =
                            async {
                                searchItems<AlbumItem>(
                                    query = TopAlbumsQuery,
                                    filter = YouTube.SearchFilter.FILTER_ALBUM,
                                )
                            }
                        val searchedArtistsDeferred =
                            async {
                                searchItems<ArtistItem>(
                                    query = TopArtistsQuery,
                                    filter = YouTube.SearchFilter.FILTER_ARTIST,
                                )
                            }

                        val explorePage = explorePageDeferred.await()
                        val chartsPage = chartsPageDeferred.await()

                        Result.success(
                            SearchDiscoveryData(
                                moodAndGenres = explorePage.moodAndGenres,
                                newReleaseAlbums = explorePage.newReleaseAlbums,
                                chartSections = chartsPage.sections,
                                searchedSongs = searchedSongsDeferred.await(),
                                searchedAlbums = searchedAlbumsDeferred.await(),
                                searchedArtists = searchedArtistsDeferred.await(),
                            ),
                        )
                    }
                } catch (throwable: Throwable) {
                    if (throwable is CancellationException) throw throwable
                    Result.failure(throwable)
                }
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

        private companion object {
            const val TrendingSongsQuery = "trending songs"
            const val TopAlbumsQuery = "top albums"
            const val TopArtistsQuery = "top artists"
        }
    }
