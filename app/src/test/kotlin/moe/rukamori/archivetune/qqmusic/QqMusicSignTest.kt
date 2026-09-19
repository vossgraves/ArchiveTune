/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.qqmusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Pins the partner request signature.
 *
 * A signing bug is the one failure in the QQ path that does not look like a failure: the request
 * goes out, the server answers 200 with an error body or an empty result, and the source simply
 * falls through to the next one with nothing to show for it. Every other mistake here surfaces as an
 * exception or an obvious empty state, so this is the part worth pinning — and it is computed
 * locally from fixed inputs, so the test needs no network and no credentials.
 */
class QqMusicSignTest {
    private val appId = "1234567890"
    private val appKey = "partner-app-key"

    /**
     * The canonical string is `app_id`, `timestamp`, then every request parameter in sorted-key
     * order — so two calls with the same parameters in a different insertion order must sign the
     * same. That is the property a server on the other end depends on, and the one a map-ordering
     * regression would break.
     */
    @Test
    fun parameterOrderDoesNotChangeTheSignature() {
        val forward =
            QqMusicProvider.sign(
                appId = appId,
                appKey = appKey,
                timestampSeconds = 1_700_000_000L,
                params = linkedMapOf("mid" to "0039MnYb0qxYhV", "quality" to "HIGH"),
            )
        val reversed =
            QqMusicProvider.sign(
                appId = appId,
                appKey = appKey,
                timestampSeconds = 1_700_000_000L,
                params = linkedMapOf("quality" to "HIGH", "mid" to "0039MnYb0qxYhV"),
            )

        assertEquals(forward, reversed)
    }

    /** The app key is the secret in the HMAC; a different key must not produce the same signature. */
    @Test
    fun theSignatureDependsOnTheAppKey() {
        val params = mapOf("keyword" to "radiohead")

        val withKey =
            QqMusicProvider.sign(appId, appKey, 1_700_000_000L, params)
        val withOtherKey =
            QqMusicProvider.sign(appId, "another-key", 1_700_000_000L, params)

        assertNotEquals(withKey, withOtherKey)
    }

    /** A replay is a different timestamp, which must be a different signature. */
    @Test
    fun theTimestampIsPartOfTheSignedMaterial() {
        val params = mapOf("keyword" to "radiohead")

        val first = QqMusicProvider.sign(appId, appKey, 1_700_000_000L, params)
        val later = QqMusicProvider.sign(appId, appKey, 1_700_000_001L, params)

        assertNotEquals(first, later)
    }

    /**
     * The exact digest for fixed inputs, so a change to the canonical layout (a different separator,
     * an omitted field, upper-case hex) is caught rather than silently shipped.
     */
    @Test
    fun signatureMatchesThePinnedDigest() {
        val signature =
            QqMusicProvider.sign(
                appId = appId,
                appKey = appKey,
                timestampSeconds = 1_700_000_000L,
                params = mapOf("keyword" to "radiohead", "num" to "5"),
            )

        assertEquals(
            "4384c5669c530c4a1312aaa254e800e1d4a7ea872bae50a27579a36d11ebdf6d",
            signature,
        )
    }
}