/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import moe.rukamori.archivetune.ui.utils.YTThumbQuality
import moe.rukamori.archivetune.ui.utils.buildYTThumbnailUrl
import timber.log.Timber

data class ThumbnailSwapState(
    val displayUrl: String?,
    val isYTReady: Boolean,
    val ytUrl: String?,
)

@Composable
fun rememberThumbnailSwapState(
    videoId: String?,
    ytmUrl: String?,
    lowDataMode: Boolean,
    isMusicVideo: Boolean = false,
): ThumbnailSwapState {
    val context = LocalContext.current
    val shouldAttemptYT = videoId != null && !lowDataMode && isMusicVideo

    var displayUrl by remember { mutableStateOf(ytmUrl) }
    var isYTReady by remember { mutableStateOf(false) }
    var ytUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(videoId, ytmUrl) {
        isYTReady = false
        ytUrl = null

        if (!shouldAttemptYT || videoId == null) {
            displayUrl = ytmUrl
            return@LaunchedEffect
        }

        // Immediately set the highest quality URL so the image loader
        // starts fetching MAXRES right away, avoiding the brief display of
        // the low-quality metadata URL while the verification loop runs.
        val maxresUrl = buildYTThumbnailUrl(videoId, YTThumbQuality.MAXRES)
        displayUrl = maxresUrl

        val imageLoader = context.imageLoader

        for (quality in YTThumbQuality.entries) {
            val url = buildYTThumbnailUrl(videoId, quality)
            try {
                val request =
                    ImageRequest
                        .Builder(context)
                        .data(url)
                        .memoryCacheKey(url)
                        .diskCacheKey(url)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .networkCachePolicy(CachePolicy.ENABLED)
                        .allowHardware(false)
                        .size(1080)
                        .build()
                val result = imageLoader.execute(request)
                if (result is SuccessResult) {
                    ytUrl = url
                    displayUrl = url
                    isYTReady = true
                    return@LaunchedEffect
                }
            } catch (e: Exception) {
                Timber.tag("ThumbnailSwap").e(e, "YT thumbnail quality=%s failed for videoId=%s", quality.value, videoId)
                continue
            }
        }
        // All YT thumbnail qualities failed — fall back to the metadata URL.
        displayUrl = ytmUrl
    }

    return ThumbnailSwapState(displayUrl, isYTReady, ytUrl)
}
