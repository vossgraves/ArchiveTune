/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.utils

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Precision
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.utils.ImageBlurUtils

/**
 * Compose helper that resolves an image URL into a pre-blurred [Bitmap] for use on Android versions
 * below S (API 31) where Compose's `Modifier.blur` is silently a no-op.
 */
@Composable
fun rememberPreBlurredBitmap(
    imageUrl: String?,
    radiusDp: Dp = 48.dp,
    maxDimensionPx: Int = 720,
): Bitmap? {
    if (imageUrl.isNullOrBlank()) return null
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return null

    val context = LocalContext.current
    val imageLoader = context.imageLoader
    var bitmap by remember(imageUrl, radiusDp, maxDimensionPx) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(imageUrl, radiusDp, maxDimensionPx) {
        bitmap = blurArtworkOffscreen(context, imageLoader, imageUrl, radiusDp, maxDimensionPx)
    }

    return bitmap
}

private suspend fun blurArtworkOffscreen(
    context: Context,
    imageLoader: coil3.ImageLoader,
    imageUrl: String,
    radiusDp: Dp,
    maxDimensionPx: Int,
): Bitmap? = withContext(Dispatchers.IO) {
    val request =
        ImageRequest
            .Builder(context)
            .data(imageUrl)
            .memoryCacheKey("$imageUrl#preblur")
            .diskCacheKey("$imageUrl#preblur")
            .size(maxDimensionPx)
            .precision(Precision.INEXACT)
            .build()
    val result = imageLoader.execute(request)
    if (result !is SuccessResult) return@withContext null
    val source = result.image.toBitmap()
    // Source may be HARDWARE config (Coil returns these on API 26+) — copy to ARGB_8888
    // before pixel-level manipulation, otherwise getPixels throws IllegalStateException.
    val mutable =
        if (source.config == Bitmap.Config.HARDWARE) {
            Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888).apply {
                val canvas = android.graphics.Canvas(this)
                canvas.drawBitmap(source, 0f, 0f, null)
            }
        } else if (source.config != Bitmap.Config.ARGB_8888) {
            source.copy(Bitmap.Config.ARGB_8888, true)
        } else {
            source
        }
    val density = context.resources.displayMetrics.density
    val radiusPx = (radiusDp.value * density).coerceIn(1f, 48f)
    ImageBlurUtils.blur(mutable, radiusPx)
}
