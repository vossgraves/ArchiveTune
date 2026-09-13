/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StartupReadinessTest {
    @Test
    fun serviceReadinessDoesNotWaitForActivityFrame() = runTest {
        val readiness = StartupReadiness()
        var configured = false
        val consumer = async { readiness.awaitReady(); configured }
        runCurrent()
        assertFalse(consumer.isCompleted)
        readiness.initialize { configured = true }
        assertTrue(consumer.await())
    }

    @Test
    fun optionalWarmupsWaitForConfigurationAndFirstFrame() = runTest {
        val readiness = StartupReadiness()
        var warmed = false
        val warmup = launch { readiness.runOptional { warmed = true } }
        readiness.initialize { }
        runCurrent()
        assertFalse(warmed)
        readiness.onFirstFrame()
        warmup.join()
        assertTrue(warmed)
    }

    @Test
    fun cancellingOneWaiterDoesNotCancelSharedReadiness() = runTest {
        val readiness = StartupReadiness()
        val cancelledWaiter = launch { readiness.awaitReady() }
        runCurrent()
        cancelledWaiter.cancel()
        readiness.initialize { }
        readiness.awaitReady()
    }

    @Test
    fun concurrentInitializationRunsExactlyOnce() = runTest {
        val readiness = StartupReadiness()
        val release = CompletableDeferred<Unit>()
        var starts = 0
        val first = launch { readiness.initialize { starts++; release.await() } }
        val second = launch { readiness.initialize { starts++ } }
        runCurrent()
        assertEquals(1, starts)
        release.complete(Unit)
        first.join()
        second.join()
        assertEquals(1, starts)
    }

    @Test
    fun initializationFailureReleasesWaitersWithTheFailure() = runTest {
        val readiness = StartupReadiness()
        val failure = IllegalStateException("configuration failed")
        val waiter = async { runCatching { readiness.awaitReady() }.exceptionOrNull() }
        runCatching { readiness.initialize { throw failure } }
        assertEquals(failure, waiter.await())
    }

    @Test
    fun optionalWarmupsHaveBoundedConcurrency() = runTest {
        val readiness = StartupReadiness()
        readiness.initialize { }
        readiness.onFirstFrame()
        val release = CompletableDeferred<Unit>()
        var active = 0
        var maximum = 0
        val jobs = List(6) {
            launch {
                readiness.runOptional {
                    active++
                    maximum = maxOf(maximum, active)
                    release.await()
                    active--
                }
            }
        }
        runCurrent()
        assertEquals(2, active)
        release.complete(Unit)
        jobs.forEach { it.join() }
        assertEquals(2, maximum)
    }
}
