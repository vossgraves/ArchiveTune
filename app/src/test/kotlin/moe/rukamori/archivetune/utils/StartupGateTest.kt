/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StartupGateTest {
    @Test
    fun configurationRunsWithoutAnyoneStartingIt() =
        runTest {
            var configured = false
            val gate = StartupGate { configured = true }

            gate.awaitReady()

            assertTrue("awaitReady must run configuration itself", configured)
        }

    @Test
    fun configurationRunsExactlyOnceUnderConcurrentCallers() =
        runTest {
            val runs = AtomicInteger()
            val release = CompletableDeferred<Unit>()
            val gate =
                StartupGate {
                    runs.incrementAndGet()
                    release.await()
                }

            val first = async { gate.awaitReady() }
            val second = async { gate.awaitReady() }
            runCurrent()
            release.complete(Unit)
            first.await()
            second.await()

            assertEquals(1, runs.get())
        }

    @Test
    fun serviceReadinessDoesNotWaitForAFrame() =
        runTest {
            val gate = StartupGate { }

            gate.awaitReady()

            assertEquals(Result.success(Unit), gate.result.value)
        }

    @Test
    fun optionalWorkWaitsForTheFirstFrame() =
        runTest {
            val gate = StartupGate { }
            var warmed = false

            val warmup = launch { gate.runOptional { warmed = true } }
            runCurrent()
            assertFalse("warm-up must not pre-empt the first frame", warmed)

            gate.onFirstFrame()
            warmup.join()

            assertTrue(warmed)
        }

    /** The headless-start bug in the old StartupReadiness: no Activity, so no frame, ever. */
    @Test
    fun optionalWorkStillRunsWhenNoFrameEverArrives() =
        runTest {
            val gate = StartupGate(firstFrameDeadline = 5.seconds) { }
            var warmed = false

            val warmup = launch { gate.runOptional { warmed = true } }
            runCurrent()
            assertFalse(warmed)

            advanceTimeBy(6.seconds)
            warmup.join()

            assertTrue("a headless start must not strand its warm-ups", warmed)
        }

    @Test
    fun configurationFailurePropagatesToTheCaller() =
        runTest {
            val boom = IllegalStateException("config failed")
            val gate = StartupGate { throw boom }

            val thrown = runCatching { gate.awaitReady() }.exceptionOrNull()

            assertEquals(boom, thrown)
            assertTrue(gate.result.value?.isFailure == true)
        }

    /** A sticky failure would leave playback dead for the life of the process. */
    @Test
    fun configurationIsRetriedAfterAFailure() =
        runTest {
            val attempts = AtomicInteger()
            val gate =
                StartupGate {
                    if (attempts.incrementAndGet() == 1) error("transient")
                }

            assertTrue(runCatching { gate.awaitReady() }.isFailure)
            gate.awaitReady()

            assertEquals(2, attempts.get())
            assertEquals(Result.success(Unit), gate.result.value)
        }

    @Test
    fun cancellingOneWaiterDoesNotBreakTheGate() =
        runTest {
            val release = CompletableDeferred<Unit>()
            val gate = StartupGate { release.await() }

            val cancelled = launch { gate.awaitReady() }
            runCurrent()
            cancelled.cancel()
            release.complete(Unit)

            gate.awaitReady()

            assertEquals(Result.success(Unit), gate.result.value)
        }
}
