/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BoundedSyncWorkersTest {
    @Test
    fun largeLibraryUsesTwoWorkersAndKeepsOriginalIndices() = runTest {
        val items = List(10_000) { it }
        val seen = mutableSetOf<Int>()
        val workers = mutableSetOf<Job>()
        var active = 0
        var maximum = 0
        forEachBoundedIndexed(items) { index, item ->
            assertEquals(index, item)
            workers += currentCoroutineContext()[Job]!!
            active++
            maximum = maxOf(maximum, active)
            yield()
            seen += item
            active--
        }
        assertEquals(items.toSet(), seen)
        assertEquals(2, workers.size)
        assertEquals(2, maximum)
    }

    @Test
    fun cancellationStopsCreatingWorkForRemainingSongs() = runTest {
        val release = CompletableDeferred<Unit>()
        var visited = 0
        val work = launch {
            forEachBoundedIndexed(List(10_000) { it }) { _, _ ->
                visited++
                release.await()
            }
        }
        runCurrent()
        assertEquals(2, visited)
        work.cancelAndJoin()
        assertEquals(2, visited)
    }

    @Test
    fun emptyLibraryDoesNotStartWorkers() = runTest {
        forEachBoundedIndexed(emptyList<Int>()) { _, _ -> error("Unexpected work") }
    }

    @Test
    fun workerFailuresPropagateToTheCaller() = runTest {
        val failure = IllegalStateException("sync failed")
        val result = runCatching {
            forEachBoundedIndexed(listOf(1, 2, 3)) { _, _ -> throw failure }
        }
        assertTrue(result.isFailure)
        assertEquals(failure, result.exceptionOrNull())
    }
}
