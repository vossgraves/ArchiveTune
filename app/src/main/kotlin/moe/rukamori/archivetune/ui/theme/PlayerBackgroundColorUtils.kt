/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import android.graphics.Color as AndroidColor

object PlayerBackgroundColorUtils {
    // Brightened ranges — previously [0.15, 0.58] which forced even vibrant
    // artwork into a dim, muddy hue. The new [0.35, 0.85] window lets the
    // actual dominant artwork colour shine through the blur (matching the
    // bright, airy Vivi Music aesthetic) while still keeping a sensible
    // floor so pure-black artwork doesn't blow out text contrast.
    private const val DEFAULT_MIN_BRIGHTNESS = 0.35f
    private const val DEFAULT_MAX_BRIGHTNESS = 0.85f

    /**
     * Constrains brightness for readability. Saturation is intentionally NOT clamped:
     * genuinely neutral artwork colours must stay neutral instead of being tinted.
     */
    fun ensureComfortableColor(
        color: Color,
        minBrightness: Float = DEFAULT_MIN_BRIGHTNESS,
        maxBrightness: Float = DEFAULT_MAX_BRIGHTNESS,
    ): Color {
        val hsv = color.toHsv()
        hsv[2] = hsv[2].coerceIn(minBrightness, maxBrightness)
        return hsv.toColor()
    }

    fun darkenColor(
        color: Color,
        factor: Float,
    ): Color {
        val hsv = color.toHsv()
        hsv[2] = (hsv[2] * factor).coerceAtLeast(0f)
        return hsv.toColor()
    }

    fun buildColoringStops(baseColor: Color): Array<Pair<Float, Color>> {
        // Coloring style keeps a touch more depth than BLUR/BLUR_GRADIENT because
        // it's a flat fill with no underlying image — the gradient is the only
        // visual interest. Still brighter than before (was 0.18–0.5).
        val comfortable = ensureComfortableColor(baseColor, minBrightness = 0.42f, maxBrightness = 0.72f)
        val mid = darkenColor(comfortable, 0.86f)
        val deep = darkenColor(comfortable, 0.7f)
        return arrayOf(
            0f to comfortable.copy(alpha = 0.97f),
            0.4f to mid.copy(alpha = 0.94f),
            0.75f to deep.copy(alpha = 0.92f),
            1f to deep.copy(alpha = 0.88f),
        )
    }

    fun buildBlurOverlayStops(colors: List<Color>): Array<Pair<Float, Color>> {
        if (colors.isEmpty()) {
            return defaultBlurOverlayStops()
        }
        val comfortable = colors.map(::ensureComfortableColor)
        val first = comfortable[0]
        val second = comfortable.getOrNull(1) ?: first
        val third = comfortable.getOrNull(2) ?: second
        // Alphas raised from 0.45/0.38/0.35/0.50 → 0.78/0.72/0.68/0.82 so the
        // brightened colour overlay actually obscures the dark blurred artwork
        // underneath instead of letting it desaturate the result.
        return arrayOf(
            0f to first.copy(alpha = 0.78f),
            0.4f to lerp(first, second, 0.5f).copy(alpha = 0.72f),
            0.75f to lerp(second, third, 0.55f).copy(alpha = 0.68f),
            1f to third.copy(alpha = 0.82f),
        )
    }

    fun buildBlurGradientStops(colors: List<Color>): Array<Pair<Float, Color>> {
        if (colors.isEmpty()) {
            return arrayOf(
                0f to Color.Transparent,
                1f to Color.Transparent,
            )
        }
        val comfortable = colors.map(::ensureComfortableColor)
        val first = comfortable[0]
        val second = comfortable.getOrNull(1) ?: first
        val third = comfortable.getOrNull(2) ?: second
        // ViviMusic-faithful: dramatically reduced alphas
        // (0.82/0.78/0.74/0.70/0.66 → 0.32/0.28/0.24/0.20/0.16) so the
        // actual album artwork's colours shine through the blur. The
        // previous high-alpha gradient was desaturating the artwork into a
        // muddy, monochrome wash. The new subtle gradient provides a hint
        // of palette colour while preserving the artwork's vibrancy.
        return arrayOf(
            0f to first.copy(alpha = 0.32f),
            0.2f to lerp(first, second, 0.3f).copy(alpha = 0.28f),
            0.5f to second.copy(alpha = 0.24f),
            0.8f to lerp(second, third, 0.6f).copy(alpha = 0.20f),
            1f to third.copy(alpha = 0.16f),
        )
    }

    private fun defaultBlurOverlayStops(): Array<Pair<Float, Color>> =
        arrayOf(
            0f to Color.Black.copy(alpha = 0.20f),
            1f to Color.Black.copy(alpha = 0.30f),
        )

    private fun Color.toHsv(): FloatArray {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(this.toArgb(), hsv)
        return hsv
    }

    private fun FloatArray.toColor(): Color = Color(AndroidColor.HSVToColor(this))
}
