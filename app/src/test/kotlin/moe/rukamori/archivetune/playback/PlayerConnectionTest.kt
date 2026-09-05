/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import android.content.Context
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.FormatEntity
import moe.rukamori.archivetune.db.entities.LyricsEntity
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.models.MediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerConnectionTest {
    @Test
    fun repeatedDisposeAndRebindDoesNotRetainPromotionCollectorsOrCancelParent() = runTest {
        val fixture = Fixture()
        val parent = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        try {
            repeat(10) {
                val connection = fixture.connect(parent)
                runCurrent()
                assertEquals(1, fixture.promotions.subscriptionCount.value)
                connection.dispose()
                connection.dispose()
                runCurrent()
                parent.coroutineContext[Job]!!.children.toList().joinAll()
                assertEquals(0, fixture.promotions.subscriptionCount.value)
                verify(fixture.player, times(1)).removeListener(connection)
                val promoted = idlePlayer()
                fixture.promotions.value = promoted
                runCurrent()
                verify(promoted, never()).addListener(connection)
                fixture.promotions.value = fixture.player
            }
            assertTrue(parent.coroutineContext[Job]!!.isActive)
        } finally {
            parent.cancel()
        }
    }

    @Test
    fun multipleUiConsumersShareOneMetadataQueryPerTypeAndDisposeCancelsThem() = runTest {
        val fixture = Fixture()
        val parent = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val connection = fixture.connect(parent)
        try {
            repeat(2) {
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { connection.currentSong.collect {} }
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { connection.currentLyrics.collect {} }
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { connection.currentFormat.collect {} }
            }
            runCurrent()
            assertEquals(1, fixture.songs.subscriptionCount.value)
            assertEquals(1, fixture.lyrics.subscriptionCount.value)
            assertEquals(1, fixture.formats.subscriptionCount.value)
            verify(fixture.database, times(1)).song(null)
            verify(fixture.database, times(1)).lyrics(null)
            verify(fixture.database, times(1)).format(null)
            connection.dispose()
            runCurrent()
            parent.coroutineContext[Job]!!.children.toList().joinAll()
            assertEquals(0, fixture.songs.subscriptionCount.value)
            assertEquals(0, fixture.lyrics.subscriptionCount.value)
            assertEquals(0, fixture.formats.subscriptionCount.value)
        } finally {
            connection.dispose()
            parent.cancel()
        }
    }

    private class Fixture {
        val player = idlePlayer()
        val promotions = MutableStateFlow<Player?>(player)
        val songs = MutableStateFlow<Song?>(null)
        val lyrics = MutableStateFlow<LyricsEntity?>(null)
        val formats = MutableStateFlow<FormatEntity?>(null)
        val database = mock(MusicDatabase::class.java)
        private val context = mock(Context::class.java)
        private val service = mock(MusicService::class.java)
        private val binder = mock(MusicService.MusicBinder::class.java)

        init {
            `when`(binder.service).thenReturn(service)
            `when`(service.player).thenReturn(player)
            `when`(service.playerFlow).thenReturn(promotions)
            `when`(service.currentMediaMetadata).thenReturn(MutableStateFlow<MediaMetadata?>(null))
            `when`(service.waitingForNetworkConnection).thenReturn(MutableStateFlow(false))
            `when`(service.queueRestoreCompleted).thenReturn(MutableStateFlow(true))
            `when`(database.song(null)).thenReturn(songs)
            `when`(database.lyrics(null)).thenReturn(lyrics)
            `when`(database.format(null)).thenReturn(formats)
        }

        fun connect(scope: CoroutineScope) = PlayerConnection(context, binder, database, scope)
    }

    private companion object {
        fun idlePlayer(): Player = mock(Player::class.java).also { player ->
            `when`(player.currentTimeline).thenReturn(Timeline.EMPTY)
            `when`(player.playbackParameters).thenReturn(PlaybackParameters.DEFAULT)
            `when`(player.playbackState).thenReturn(Player.STATE_IDLE)
        }
    }
}
