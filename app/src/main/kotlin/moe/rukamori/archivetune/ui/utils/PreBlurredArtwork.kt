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
import coil3.request.allowHardware
import coil3.size.Precision
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.utils.ImageBlurUtils

/**
 * Compose helper that resolves an image URL into a pre-blurred [Bitmap] for use on Android versions
 * below S (API 31) where Compose's `Modifier.blur` is silently a no-op.
 *
 * On Android 12+ this returns `null` because the platform `RenderEffect`-backed `Modifier.blur`
 * is hardware-accelerated and produces a better-quality result than a CPU stack-blur ever will —
 * callers should fall back to `Modifier.blur(radiusDp)` in that case.
 *
 * On Android < 12 the source bitmap is fetched via the shared Coil [ImageLoader] (so it benefits
 * from the disk + memory cache), copied to `ARGB_8888` if necessary, and then run through
 * [ImageBlurUtils.blur] with the equivalent pixel radius. The result is cached per (url, radius)
 * in Compose state — recomposition does not re-blur unless the inputs change.
 *
 * The blur is dispatched on [Dispatchers.IO] so the UI thread never blocks. While the blur is
 * in-flight the returned state is `null`, which callers can use to render a solid-color placeholder
 * (typically the artwork tint) so the layout doesn't pop.
 *
 * @param imageUrl The source URL (or null — returns null immediately).
 * @param radiusDp The intended blur radius in dp. Converted to a pixel radius at the device's
 *   density. Cap at 48px internally because stack-blur cost grows quadratically and beyond ~48px
 *   the downscale-then-blur path in [ImageBlurUtils.blur] already produces a good result.
 * @param maxDimensionPx Maximum dimension of the source bitmap to load — large artwork (e.g.
 *   2000x2000) is wasteful for a blurred background. Defaults to 720 because anything bigger
 *   is indistinguishable from 720 once blurred.
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
            // Force Coil to decode into a software bitmap (ARGB_8888) instead of HARDWARE.
            // On Android < 12 we run a CPU stack-blur over the pixels (ImageBlurUtils.blur
            // calls getPixels / setPixels), and hardware bitmaps back those calls with an
            // "Software rendering doesn't support hardware bitmaps" IllegalArgumentException
            // the moment we try to read or copy the pixels onto a software canvas — which is
            // exactly what the old Canvas.drawBitmap copy path did, crashing pre-S devices
            // like the motorola one power (SDK 29) whenever the Apple Music player style
            // opened. allowHardware(false) makes Coil decode straight to ARGB_8888, so the
            // subsequent Bitmap.copy(ARGB_8888) in the defensive `when` is a no-op and the
            // stack-blur runs cleanly on a software pixel buffer.
            .allowHardware(false)
            .build()
    val result = imageLoader.execute(request)
    if (result !is SuccessResult) return@withContext null
    val source = result.image.toBitmap()
    // Defensive: even with bitmapConfig(ARGB_8888), some decoders (e.g. GIF) can still hand
    // back a non-ARGB_8888 bitmap. Coerce to ARGB_8888 without ever drawing a hardware bitmap
    // onto a software canvas — use Bitmap.copy, which is safe for any source config.
    val mutable =
        when (source.config) {
            Bitmap.Config.ARGB_8888 -> source
            Bitmap.Config.HARDWARE -> {
                // Bitmap.copy from HARDWARE → ARGB_8888 uses an internal PixelCopy path on
                // API 26+ and does NOT route through Canvas.drawBitmap, so it can't throw
                // the "software rendering doesn't support hardware bitmaps" exception that
                // the previous Canvas-based copy did.
                source.copy(Bitmap.Config.ARGB_8888, true) ?: return@withContext null
            }
            else -> source.copy(Bitmap.Config.ARGB_8888, true) ?: source
        }
    val density = context.resources.displayMetrics.density
    val radiusPx = (radiusDp.value * density).coerceIn(1f, 48f)
    ImageBlurUtils.blur(mutable, radiusPx)
}
