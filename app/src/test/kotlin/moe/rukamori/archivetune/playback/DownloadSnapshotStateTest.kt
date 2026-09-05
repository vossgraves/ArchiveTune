/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadSnapshotStateTest {
    @Test
    fun newerListenerEventWinsOverInitialSnapshot() {
        val state = DownloadSnapshotState<String>()
        state.put("song", "completed")
        state.initialize(mapOf("song" to "downloading", "older" to "completed"))
        assertEquals(mapOf("song" to "completed", "older" to "completed"), state.flow.value)
    }

    @Test
    fun removedDownloadCannotBeResurrectedBySnapshot() {
        val state = DownloadSnapshotState<String>()
        state.remove("song")
        state.initialize(mapOf("song" to "completed"))
        assertEquals(emptyMap<String, String>(), state.flow.value)
    }

    @Test
    fun removeThenReaddKeepsLatestEvent() {
        val state = DownloadSnapshotState<String>()
        state.remove("song")
        state.put("song", "queued again")
        state.initialize(mapOf("song" to "old completion"))
        assertEquals(mapOf("song" to "queued again"), state.flow.value)
    }

    @Test
    fun changeThenRemoveBeforeSnapshotStaysRemoved() {
        val state = DownloadSnapshotState<String>()
        state.put("song", "failed")
        state.remove("song")
        state.initialize(mapOf("song" to "downloading"))
        assertEquals(emptyMap<String, String>(), state.flow.value)
    }

    @Test
    fun eventsAfterInitializationRemainAuthoritative() {
        val state = DownloadSnapshotState<String>()
        state.initialize(mapOf("song" to "queued"))
        state.put("song", "completed")
        state.initialize(mapOf("song" to "queued"))
        assertEquals(mapOf("song" to "completed"), state.flow.value)
        state.remove("song")
        assertEquals(emptyMap<String, String>(), state.flow.value)
    }
}
