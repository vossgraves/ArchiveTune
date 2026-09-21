/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.spotify

import moe.rukamori.archivetune.spotify.models.SpotifyPlayHistory
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpotifyPlayHistoryWindowTest {
    private fun play(
        track: String,
        playedAt: String?,
    ) = SpotifyPlayHistory(track = SpotifyTrack(uri = "spotify:track:$track"), playedAt = playedAt)

    private fun trackUris(items: List<SpotifyPlayHistory>) = items.map { it.track?.uri }

    @Test
    fun newestCursorIsTheLatestPlayWhateverOrderRowsArriveIn() {
        val plays =
            listOf(
                play("c", "2026-09-20T10:00:00.500Z"),
                play("a", "2026-09-20T12:34:56.789Z"),
                play("b", "2026-09-20T11:00:00.000Z"),
            )

        assertEquals(1_789_907_696_789L, plays.newestPlayedAtMillis())
    }

    @Test
    fun newestCursorIsNullWithoutAParseableTimestamp() {
        assertNull(listOf(play("a", null), play("b", "not a timestamp")).newestPlayedAtMillis())
        assertNull(emptyList<SpotifyPlayHistory>().newestPlayedAtMillis())
    }

    @Test
    fun aDeltaReadMergesInFrontOfTheCachedWindowNewestFirst() {
        val cached = listOf(play("old", "2026-09-20T08:00:00Z"), play("older", "2026-09-20T07:00:00Z"))
        val delta = listOf(play("new", "2026-09-20T09:30:00Z"), play("newest", "2026-09-20T09:45:00Z"))

        val merged = mergePlayHistory(newer = delta, cached = cached)

        assertEquals(
            listOf("spotify:track:newest", "spotify:track:new", "spotify:track:old", "spotify:track:older"),
            trackUris(merged),
        )
    }

    @Test
    fun theSamePlayFromBothReadsIsKeptOnce() {
        val repeated = play("same", "2026-09-20T09:00:00Z")
        val merged = mergePlayHistory(newer = listOf(repeated), cached = listOf(repeated, play("other", "2026-09-20T08:00:00Z")))

        assertEquals(2, merged.size)
        assertEquals(repeated, merged.first())
    }

    @Test
    fun theSameTrackPlayedTwiceIsTwoPlays() {
        val merged =
            mergePlayHistory(
                newer = listOf(play("twice", "2026-09-20T09:00:00Z")),
                cached = listOf(play("twice", "2026-09-20T08:00:00Z")),
            )

        assertEquals(2, merged.size)
    }

    @Test
    fun theMergedWindowStopsAtWhatTheEndpointWouldHaveReturned() {
        // 30 cached plays, then 30 newer ones: the merge must return the newest 50, not 60.
        val cached = (1..30).map { play("c$it", "2026-09-20T00:%02d:00Z".format(it)) }
        val delta = (1..30).map { play("d$it", "2026-09-21T00:%02d:00Z".format(it)) }

        val merged = mergePlayHistory(newer = delta, cached = cached)

        assertEquals(SPOTIFY_HISTORY_WINDOW, merged.size)
        assertEquals("spotify:track:d30", merged.first().track?.uri)
        assertEquals("spotify:track:c11", merged.last().track?.uri)
    }

    @Test
    fun aPlayWithNoTimestampIsKeptButSortsLast() {
        val merged =
            mergePlayHistory(
                newer = listOf(play("stamped", "2026-09-20T09:00:00Z")),
                cached = listOf(play("undated", null)),
            )

        assertEquals(listOf("spotify:track:stamped", "spotify:track:undated"), trackUris(merged))
    }

    @Test
    fun retryAfterIsHonouredWhenItExceedsTheDocumentedWindow() {
        assertEquals(50_000L, rateLimitCooldownMillis(50))
    }

    @Test
    fun retryAfterBelowTheDocumentedWindowStillWaitsOutTheWindow() {
        assertEquals(SPOTIFY_RATE_LIMIT_FALLBACK_MS, rateLimitCooldownMillis(2))
    }

    @Test
    fun aMissingOrUnusableRetryAfterFallsBackToTheDocumentedWindow() {
        assertEquals(SPOTIFY_RATE_LIMIT_FALLBACK_MS, rateLimitCooldownMillis(null))
        assertEquals(SPOTIFY_RATE_LIMIT_FALLBACK_MS, rateLimitCooldownMillis(0))
        assertEquals(SPOTIFY_RATE_LIMIT_FALLBACK_MS, rateLimitCooldownMillis(-5))
    }

    @Test
    fun anAbsurdRetryAfterIsClamped() {
        assertEquals(SPOTIFY_RATE_LIMIT_MAX_MS, rateLimitCooldownMillis(86_400))
    }
}
