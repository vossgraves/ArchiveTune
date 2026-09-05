/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeRequestGateTest {
    @Test
    fun delayedOldResponseCannotOverwriteANewerRefresh() = runTest {
        val requests = HomeRequestGate()
        var visible = "cached"
        val releaseOld = CompletableDeferred<Unit>()
        val old = requests.current()
        val work = launch {
            releaseOld.await()
            requests.commit(old) { visible = "old account" }
        }
        runCurrent()
        val current = requests.next()
        assertTrue(requests.commit(current) { visible = "new account" })
        releaseOld.complete(Unit)
        work.join()
        assertEquals("new account", visible)
    }

    @Test
    fun switchingChipInvalidatesPaginationWithoutClearingVisibleContent() {
        val requests = HomeRequestGate()
        val pagination = requests.current()
        val chip = requests.next()
        var visible = "cached"
        assertFalse(requests.commit(pagination) { visible = "old continuation" })
        assertEquals("cached", visible)
        assertTrue(requests.commit(chip) { visible = "selected chip" })
        assertEquals("selected chip", visible)
    }
}
