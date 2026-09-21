/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.browse

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.YTItem
import javax.inject.Inject
import javax.inject.Singleton

// rukamori/core declares this as innertube.models.PODCAST_LIBRARY_BROWSE_ID; the core revision this
// fork compiles against (vossgraves/core 4d8158fbd5a55e8de9f56578a65322f575c91ce7) does not carry it.
internal const val PODCAST_LIBRARY_BROWSE_ID = "FEmusic_library_non_music_audio_list"

data class BrowsePage(
    val title: String?,
    val items: List<YTItem>,
    val continuation: String?,
)

@Singleton
class BrowseRepository
    @Inject
    constructor() {
        suspend fun load(browseId: String): Result<BrowsePage> =
            withContext(Dispatchers.IO) {
                val result =
                    if (browseId == PODCAST_LIBRARY_BROWSE_ID) {
                        YouTube.library(browseId).map { page ->
                            BrowsePage(
                                // Our LibraryPage carries no title; the title falls back to
                                // R.string.your_shows through BrowseUiState.fallbackTitleResId.
                                title = null,
                                items = page.items,
                                continuation = page.continuation,
                            )
                        }
                    } else {
                        YouTube.browse(browseId, params = null).map { page ->
                            BrowsePage(
                                title = page.title,
                                items = page.items.flatMap { section -> section.items },
                                continuation = null,
                            )
                        }
                    }
                result.rethrowCancellation()
                result
            }

        suspend fun loadContinuation(continuation: String): Result<BrowsePage> =
            withContext(Dispatchers.IO) {
                val result =
                    YouTube.libraryContinuation(continuation).map { page ->
                        BrowsePage(
                            title = null,
                            items = page.items,
                            continuation = page.continuation,
                        )
                    }
                result.rethrowCancellation()
                result
            }
    }

private fun Result<*>.rethrowCancellation() {
    val failure = exceptionOrNull()
    if (failure is CancellationException) throw failure
}
