/*
 * ArchiveTune (2026)
 * © vossgraves — github.com/vossgraves
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback.stream

import moe.rukamori.archivetune.innertube.NewPipeUtils
import moe.rukamori.archivetune.innertube.PlaybackAuthState
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.YouTubeClient
import moe.rukamori.archivetune.innertube.models.response.PlayerResponse

/**
 * [StreamUrlExtractor] backed by MetrolistExtractor's NewPipe extractor, reached through core's
 * [NewPipeUtils] (its `YoutubeJavaScriptPlayerManager` deobfuscation, the n-param transform and the
 * GVS PO-token append). This is the only place in the app that touches that core object, so which
 * artifact ends up in `org.schabi.newpipe.extractor` is decided here and in `:core`'s dependency
 * block, and nowhere else.
 */
object NewPipeStreamUrlExtractor : StreamUrlExtractor {
    override suspend fun signatureTimestamp(videoId: String): Result<Int> = NewPipeUtils.getSignatureTimestamp(videoId)

    override suspend fun streamUrl(
        format: PlayerResponse.StreamingData.Format,
        videoId: String,
        client: YouTubeClient?,
        authState: PlaybackAuthState,
    ): Result<String> = NewPipeUtils.getStreamUrl(format, videoId, client, authState)

    /**
     * For callers with no client preference of their own: no GVS PO token beyond whatever the live
     * session already carries, exactly what core's own default arguments did before this seam.
     */
    suspend fun streamUrl(
        format: PlayerResponse.StreamingData.Format,
        videoId: String,
    ): Result<String> = streamUrl(format, videoId, null, YouTube.currentPlaybackAuthState())
}
