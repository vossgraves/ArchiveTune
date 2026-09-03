/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.audiosource

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IsrcResolverTest {

    @Test
    fun normalizeIsrc_validFormats() {
        assertEquals("USUM72401994", IsrcResolver.normalizeIsrc("USUM72401994"))
        assertEquals("USUM72401994", IsrcResolver.normalizeIsrc("US-UM7-24-01994"))
        assertEquals("JPP302300157", IsrcResolver.normalizeIsrc("jpp302300157"))
        assertEquals("INS172203702", IsrcResolver.normalizeIsrc("  INS172203702  "))
    }

    @Test
    fun normalizeIsrc_invalidFormats() {
        assertNull(IsrcResolver.normalizeIsrc(null))
        assertNull(IsrcResolver.normalizeIsrc(""))
        assertNull(IsrcResolver.normalizeIsrc("INVALID"))
        assertNull(IsrcResolver.normalizeIsrc("123456789012"))
    }

    @Test
    fun sanityGate_acceptsIdenticalTrack() {
        val passed = IsrcResolver.verifySanityGate(
            wantedTitle = "BIRDS OF A FEATHER",
            wantedArtists = listOf("Billie Eilish"),
            wantedDurationMs = 193_000L,
            candidateTitle = "BIRDS OF A FEATHER",
            candidateArtist = "Billie Eilish",
            candidateDurationMs = 193_500L,
        )
        assertTrue(passed)
    }

    @Test
    fun sanityGate_acceptsWithinFiveSecondsTolerance() {
        val passed = IsrcResolver.verifySanityGate(
            wantedTitle = "Die With A Smile",
            wantedArtists = listOf("Lady Gaga", "Bruno Mars"),
            wantedDurationMs = 251_000L,
            candidateTitle = "Die With A Smile",
            candidateArtist = "Lady Gaga & Bruno Mars",
            candidateDurationMs = 254_500L, // +3.5s
        )
        assertTrue(passed)
    }

    @Test
    fun sanityGate_rejectsExceedingFiveSecondsTolerance() {
        val passed = IsrcResolver.verifySanityGate(
            wantedTitle = "Die With A Smile",
            wantedArtists = listOf("Lady Gaga", "Bruno Mars"),
            wantedDurationMs = 251_000L,
            candidateTitle = "Die With A Smile",
            candidateArtist = "Lady Gaga & Bruno Mars",
            candidateDurationMs = 259_000L, // +8s -> FAIL
        )
        assertFalse(passed)
    }

    @Test
    fun sanityGate_rejectsVersionMismatch_liveVsStudio() {
        val passed = IsrcResolver.verifySanityGate(
            wantedTitle = "Hotel California",
            wantedArtists = listOf("Eagles"),
            wantedDurationMs = 391_000L,
            candidateTitle = "Hotel California - Live",
            candidateArtist = "Eagles",
            candidateDurationMs = 391_000L,
        )
        assertFalse(passed)
    }

    @Test
    fun sanityGate_rejectsVersionMismatch_remixVsStudio() {
        val passed = IsrcResolver.verifySanityGate(
            wantedTitle = "Levitating",
            wantedArtists = listOf("Dua Lipa"),
            wantedDurationMs = 203_000L,
            candidateTitle = "Levitating (The Blessed Madonna Remix)",
            candidateArtist = "Dua Lipa",
            candidateDurationMs = 203_000L,
        )
        assertFalse(passed)
    }

    @Test
    fun sanityGate_rejectsTributeAndKaraokeBlacklist() {
        val passed = IsrcResolver.verifySanityGate(
            wantedTitle = "Shape of You",
            wantedArtists = listOf("Ed Sheeran"),
            wantedDurationMs = 233_000L,
            candidateTitle = "Shape of You",
            candidateArtist = "The Acoustic Tribute Band",
            candidateDurationMs = 233_000L,
        )
        assertFalse(passed)
    }

    @Test
    fun sanityGate_rejectsUnrelatedArtist() {
        val passed = IsrcResolver.verifySanityGate(
            wantedTitle = "Stay",
            wantedArtists = listOf("The Kid LAROI", "Justin Bieber"),
            wantedDurationMs = 141_000L,
            candidateTitle = "Stay",
            candidateArtist = "Rihanna",
            candidateDurationMs = 141_000L,
        )
        assertFalse(passed)
    }

    @Test
    fun sanityGate_acceptsNonLatinArtists() {
        val passedJapanese = IsrcResolver.verifySanityGate(
            wantedTitle = "KICK BACK",
            wantedArtists = listOf("Kenshi Yonezu"),
            wantedDurationMs = 193_000L,
            candidateTitle = "KICK BACK",
            candidateArtist = "Kenshi Yonezu",
            candidateDurationMs = 193_000L,
        )
        assertTrue(passedJapanese)

        val passedKorean = IsrcResolver.verifySanityGate(
            wantedTitle = "Hype Boy",
            wantedArtists = listOf("NewJeans"),
            wantedDurationMs = 179_000L,
            candidateTitle = "Hype Boy",
            candidateArtist = "NewJeans",
            candidateDurationMs = 179_000L,
        )
        assertTrue(passedKorean)
    }
}
