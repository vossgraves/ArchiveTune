/*
 * ArchiveTune (2026)
 * © vossgraves — github.com/vossgraves
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback.stream

import moe.rukamori.archivetune.innertube.PlaybackAuthState
import moe.rukamori.archivetune.innertube.models.YouTubeClient
import moe.rukamori.archivetune.innertube.models.response.PlayerResponse

/**
 * The cipher step of the playback pipelines: mint a playable URL for a [format] the caller has
 * already chosen, and report the signature timestamp a player request should carry.
 *
 * This is a backend seam, not a stream resolver. [AudioStreamRepository] answers "play this media id
 * at this quality" and owns tier order; this answers "this exact format, one URL", which is what the
 * player-response pipelines ([moe.rukamori.archivetune.utils.YTPlayerUtils],
 * [moe.rukamori.archivetune.echo.EchoStreamResolver] and the video artwork surface) need while they
 * are still choosing a client and a format. Keeping the two apart is what lets the backend be
 * replaced in one binding instead of at every call site — [NewPipeStreamUrlExtractor] is the
 * current one.
 */
interface StreamUrlExtractor {
    /** The JavaScript player's signature timestamp for [videoId], or a failure if it cannot be read. */
    suspend fun signatureTimestamp(videoId: String): Result<Int>

    /**
     * Resolves [format] to a URL the player can fetch. Implementations apply the n-param
     * (throttling) transform and append any GVS PO token for [client], so the result is usable
     * as-is. Callers pass the [authState] they resolved the player response with, so the token
     * matches that request.
     */
    suspend fun streamUrl(
        format: PlayerResponse.StreamingData.Format,
        videoId: String,
        client: YouTubeClient?,
        authState: PlaybackAuthState,
    ): Result<String>
}
