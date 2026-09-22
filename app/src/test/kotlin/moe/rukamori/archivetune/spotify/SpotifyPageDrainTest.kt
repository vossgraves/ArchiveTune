/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.spotify

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import moe.rukamori.archivetune.spotify.models.SpotifyPaging
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The drain reads a Spotify list's remaining pages out of order, so what has to hold is that the
 * list it returns is the one a page-by-page read returns: same pages, same order, and the same
 * place where the list ends.
 */
class SpotifyPageDrainTest {
    private fun page(
        offset: Int,
        size: Int,
        total: Int,
    ) = SpotifyPaging(
        items = List(size) { offset + it },
        total = total,
        limit = PAGE_SIZE,
        offset = offset,
        rawItemCount = size,
    )

    @Test
    fun remainingPagesAreReadByOffsetAndKeptInReadingOrder() = runTest {
        val requested = mutableListOf<Int>()

        val remaining =
            drainSpotifyPages(page(offset = 0, size = PAGE_SIZE, total = TOTAL)) { offset ->
                requested += offset
                page(offset = offset, size = PAGE_SIZE, total = TOTAL)
            }

        assertEquals(listOf(50, 100, 150, 200), requested)
        assertEquals((PAGE_SIZE until TOTAL).toList(), remaining)
    }

    @Test
    fun aShortPageEndsTheDrainWhereAPageByPageReadEnds() = runTest {
        val requested = mutableListOf<Int>()

        val remaining =
            drainSpotifyPages(page(offset = 0, size = PAGE_SIZE, total = TOTAL)) { offset ->
                requested += offset
                page(offset = offset, size = if (offset == 100) 20 else PAGE_SIZE, total = TOTAL)
            }

        // The pages past the short one are read but never kept: a reader that followed each page's
        // successor would have stopped at 100 and seen nothing after it.
        assertEquals(listOf(50, 100, 150, 200), requested)
        assertEquals((PAGE_SIZE until 120).toList(), remaining)
    }

    @Test
    fun aListWithoutATotalIsFollowedOnePageAtATime() = runTest {
        val requested = mutableListOf<Int>()

        val remaining =
            drainSpotifyPages(page(offset = 0, size = PAGE_SIZE, total = 0)) { offset ->
                requested += offset
                page(offset = offset, size = if (offset < 100) PAGE_SIZE else 10, total = 0)
            }

        assertEquals(listOf(50, 100), requested)
        assertEquals((PAGE_SIZE until 110).toList(), remaining)
    }

    @Test
    fun aPageSizeTheEndpointDidNotHonourIsFollowedThroughItsSuccessors() = runTest {
        val requested = mutableListOf<Int>()

        // Something answered with more items than the limit asked for, so an offset cannot be
        // predicted from the page length; the successor each page reports is the only authority.
        val remaining =
            drainSpotifyPages(page(offset = 0, size = 2 * PAGE_SIZE, total = TOTAL)) { offset ->
                requested += offset
                page(offset = offset, size = PAGE_SIZE, total = TOTAL)
            }

        assertEquals(listOf(100, 150, 200), requested)
        assertEquals((2 * PAGE_SIZE until TOTAL).toList(), remaining)
    }

    @Test
    fun aSinglePageListReadsNothingMore() = runTest {
        val requested = mutableListOf<Int>()

        val remaining =
            drainSpotifyPages(page(offset = 0, size = 12, total = 12)) { offset ->
                requested += offset
                page(offset = offset, size = PAGE_SIZE, total = TOTAL)
            }

        assertEquals(emptyList<Int>(), requested)
        assertEquals(emptyList<Int>(), remaining)
    }

    @Test
    fun readsStayWithinTheConcurrencyCap() = runTest {
        var inFlight = 0
        var peak = 0

        val remaining =
            drainSpotifyPages(page(offset = 0, size = PAGE_SIZE, total = TOTAL), concurrency = 3) { offset ->
                inFlight++
                peak = maxOf(peak, inFlight)
                delay(1)
                inFlight--
                page(offset = offset, size = PAGE_SIZE, total = TOTAL)
            }

        assertEquals(3, peak)
        assertEquals((PAGE_SIZE until TOTAL).toList(), remaining)
    }

    private companion object {
        const val PAGE_SIZE = 50
        const val TOTAL = 250
    }
}
