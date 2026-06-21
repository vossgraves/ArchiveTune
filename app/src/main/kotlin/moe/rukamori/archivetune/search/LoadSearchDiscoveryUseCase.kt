/*
 * ArchiveTune (2026)
 * Â© Rukamori â€” github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.search

import androidx.compose.runtime.Immutable
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.pages.MoodAndGenres
import moe.rukamori.archivetune.repository.SearchDiscoveryRepository
import javax.inject.Inject

class LoadSearchDiscoveryUseCase
    @Inject
    constructor(
        private val repository: SearchDiscoveryRepository,
    ) {
        suspend operator fun invoke(): Result<SearchDiscoveryUiModel> =
            repository.loadDiscovery().map { data ->
                val chartItems = data.chartSections.flatMap { section -> section.items }

                SearchDiscoveryUiModel(
                    moodAndGenres = data.moodAndGenres,
                    trendingSongs =
                        (
                            chartItems.filterIsInstance<SongItem>() +
                                data.searchedSongs
                        ).distinctBy { item -> item.id }.take(MaxTrendingItems),
                    trendingAlbums =
                        (
                            chartItems.filterIsInstance<AlbumItem>() +
                                data.newReleaseAlbums +
                                data.searchedAlbums
                        ).distinctBy { item -> item.id }.take(MaxTrendingItems),
                    trendingArtists =
                        (
                            chartItems.filterIsInstance<ArtistItem>() +
                                data.searchedArtists
                        ).distinctBy { item -> item.id }.take(MaxTrendingItems),
                )
            }

        private companion object {
            const val MaxTrendingItems = 12
        }
    }

@Immutable
data class SearchDiscoveryUiModel(
    val moodAndGenres: List<MoodAndGenres.Item>,
    val trendingSongs: List<SongItem>,
    val trendingAlbums: List<AlbumItem>,
    val trendingArtists: List<ArtistItem>,
) {
    val isEmpty: Boolean
        get() = moodAndGenres.isEmpty() && trendingSongs.isEmpty() && trendingAlbums.isEmpty() && trendingArtists.isEmpty()
}
