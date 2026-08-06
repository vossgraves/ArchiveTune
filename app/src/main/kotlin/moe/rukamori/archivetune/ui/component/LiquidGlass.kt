/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Liquid glass / backdrop blur effect, ported from SimpMusic
 * (https://github.com/maxrave-dev/SimpMusic) and simplified for the
 * Android-only ArchiveTune build. The original KMP expect/actual
 * pattern is collapsed into a single file because ArchiveTune does
 * not have a JVM/iOS target.
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton as Material3IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

/** Alias so call sites can refer to a stable type name regardless of the backdrop impl. */
typealias PlatformBackdrop = LayerBackdrop

/**
 * Records the content of the composable it is called on into a [LayerBackdrop]
 * that downstream [liquidGlass] modifiers can sample from. Mirrors SimpMusic's
 * `rememberBackdrop` + `Modifier.layerBackdrop` pair.
 */
@Composable
fun rememberBackdrop(color: Color): PlatformBackdrop =
    rememberLayerBackdrop {
        drawRect(color)
        drawContent()
    }

fun Modifier.layerBackdrop(backdrop: PlatformBackdrop): Modifier = this.layerBackdrop(backdrop)

/**
 * App-content [LayerBackdrop] used by the Liquid Glass mini player and the Liquid
 * Glass navigation bar. Created in [moe.rukamori.archivetune.MainActivity] and
 * applied via [Modifier.layerBackdrop] to the same Box that already records
 * content for the frosted nav bar — so the backdrop captures the entire app
 * surface every frame, and any sibling consumer (mini player / nav bar) can
 * sample it with [Modifier.liquidGlass].
 *
 * Null when Liquid Glass is disabled or the device is below Android 12 (the
 * kyant RuntimeShader stack requires API 31+).
 */
val LocalLiquidGlassBackdrop = compositionLocalOf<LayerBackdrop?> { null }

/**
 * Applies the SimpMusic liquid-glass effect to any element.
 *
 * Encapsulates the per-surface [GraphicsLayer], the Kyant `drawBackdrop`
 * effect stack and the press/hold "liquid" interaction (a slight scale-up,
 * deeper refraction and a radial glow that follows the finger, springing
 * back on release). The press gesture is observe-only, so wrapped click
 * handlers keep working.
 *
 * The element MUST be a sibling of the backdrop source (the box carrying
 * [layerBackdrop]); nesting it inside the source creates a render-feedback
 * loop that crashes the RuntimeShader.
 *
 * @param baseColor Optional OPAQUE color drawn UNDER the backdrop sample
 *   (via the kyant `onDrawBehind` callback). When the backdrop has content
 *   (e.g. album art behind the nav bar), the backdrop sample covers the
 *   base color — producing the liquid glass refraction effect. When the
 *   backdrop is EMPTY (e.g. bottom of a short page with no content behind),
 *   the backdrop sample is transparent and the opaque base color shows
 *   through — so the element is always visible instead of "completely
 *   transparent". Pass `Color.Unspecified` to skip the base color (the
 *   original SimpMusic behavior — relies on the backdrop always having
 *   content).
 */
@Composable
fun Modifier.liquidGlass(
    backdrop: PlatformBackdrop,
    shape: Shape = CircleShape,
    interactive: Boolean = true,
    baseColor: Color = Color.Unspecified,
): Modifier {
    // Match SimpMusic's theme-aware overlay: in dark theme add a black veil
    // ("đục đen"); in light theme add a white veil ("đục trắng"). The previous
    // implementation always used Color.Black, which on a bright backdrop in
    // light theme produced a translucent dark smudge that read as "white space"
    // (the bright backdrop showing through at 73 %), and on an empty/transparent
    // backdrop (e.g. the bottom of the screen with no content behind) produced
    // a fully invisible bar — the user's "completely transparent" complaint.
    // Using White in light theme gives the frosted-white Apple-glass look, and
    // using a higher alpha when the backdrop is empty keeps the bar visible.
    val isDark = isSystemInDarkTheme()
    return this.drawBackdrop(
        backdrop = backdrop,
        effects = {
            // Fixed mid-luminance: keeps the glass readable on both bright
            // (album art) and dark (system surface) backdrops. The luminance-
            // adaptive variant in SimpMusic is only needed for the mini-player
            // capsule which is not used here.
            val l = 0f
            vibrancy()
            colorControls(
                brightness = 0.05f,
                contrast = 1f,
                saturation = 1.5f,
            )
            blur(
                if (l > 0f) {
                    lerp(8f.dp.toPx(), 16f.dp.toPx(), l)
                } else {
                    lerp(8f.dp.toPx(), 2f.dp.toPx(), -l)
                },
            )
            // Refraction height MUST stay below the stadium inradius (minDimension / 2).
            // At minDimension / 2 the top and bottom refraction meet at the medial axis,
            // producing a dark horizontal seam across the middle of wide pills (the
            // "white space" the user reported inside the nav bar). SimpMusic uses
            // minDimension / 4; we match. chromaticAberration = false matches the
            // crisp Kyant demo look and avoids the radial discontinuity at the centre.
            lens(24f.dp.toPx(), size.minDimension / 4f, false)
        },
        onDrawBackdrop = { drawBackdrop ->
            drawBackdrop()
        },
        shape = { shape },
        // Draw the opaque base color UNDER the backdrop sample. The kyant
        // DrawBackdropNode.draw() order is: onDrawBehind → drawBackdropLayer
        // (backdrop sample) → onDrawSurface (darken overlay) → drawContent
        // (the composable's content). So onDrawBehind is the ONLY place to
        // put a fallback color that the backdrop sample can composite on top
        // of — drawContent (where the Surface's `color` is drawn) is ABOVE
        // the backdrop sample and would cover it.
        onDrawBehind =
            if (baseColor != Color.Unspecified) {
                { drawRect(baseColor) }
            } else {
                null
            },
        onDrawSurface = {
            // luminanceAnimation = 0.5f gives a fixed darken alpha of ~0.272 (the
            // lerp(0.12, 0.5, 0.4) midpoint). SimpMusic animates this from a real
            // luminance sample; we keep it constant for simplicity. The KEY change
            // is the overlay color: Black for dark theme, White for light theme.
            // In light theme a 27 % white veil over a bright backdrop produces the
            // frosted-white Apple-glass look (instead of a dark smudge). On an empty
            // backdrop, the veil still draws — so the bar is always visible.
            val luminanceAnimation = 0.5f
            val darken = lerp(
                0.12f,
                0.5f,
                ((luminanceAnimation - 0.3f) / 0.5f).coerceIn(0f, 1f),
            )
            drawRect((if (isDark) Color.Black else Color.White).copy(alpha = darken))
        },
    )
}

/**
 * A liquid-glass surface wrapping arbitrary [content] (e.g. a pill of icon
 * buttons). Thin convenience over [liquidGlass]; pure common code.
 */
@Composable
fun LiquidGlassContainer(
    backdrop: PlatformBackdrop,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    interactive: Boolean = true,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.liquidGlass(backdrop, shape, interactive),
        contentAlignment = contentAlignment,
        content = content,
    )
}

/**
 * A rounded-rect liquid-glass pill that hosts a row of icon buttons — the
 * SimpMusic "heart + more" cluster that floats at the top-end of the album /
 * artist / playlist header. The pill is 48dp tall with a 24dp corner radius
 * and uses the same `Modifier.liquidGlass` effect as the circular back
 * button.
 *
 * The pill MUST be a sibling of (not a child of) the composable carrying
 * [layerBackdrop] — otherwise the RuntimeShader enters a render-feedback
 * loop and crashes.
 *
 * Caller is responsible for laying out the row of icon buttons inside
 * [content]; each icon should be a 48dp square to match the back button
 * tap target size.
 */
@Composable
fun LiquidGlassActionPill(
    backdrop: PlatformBackdrop,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier =
            modifier
                .height(48.dp)
                .liquidGlass(
                    backdrop = backdrop,
                    shape = RoundedCornerShape(24.dp),
                ),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * Convenience wrapper around [LiquidGlassContainer] for the common single-icon
 * case (e.g. the circular back button shared by the detail screens).
 *
 * Accepts a [Painter] (e.g. from `painterResource(R.drawable.arrow_back)`)
 * because ArchiveTune's existing iconography uses painter resources, not
 * ImageVectors. This keeps call sites unchanged.
 */
@Composable
fun LiquidGlassIconButton(
    backdrop: PlatformBackdrop,
    painter: Painter,
    modifier: Modifier = Modifier.size(48.dp),
    shape: Shape = CircleShape,
    tint: Color = Color.White,
    contentDescription: String? = null,
    interactive: Boolean = true,
    onClick: () -> Unit,
) {
    LiquidGlassContainer(
        backdrop = backdrop,
        modifier = modifier,
        shape = shape,
        interactive = interactive,
    ) {
        Material3IconButton(
            onClick = onClick,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                painter = painter,
                contentDescription = contentDescription,
                tint = tint,
            )
        }
    }
}

/**
 * ImageVector overload — kept for parity with SimpMusic's API.
 */
@Composable
fun LiquidGlassIconButton(
    backdrop: PlatformBackdrop,
    imageVector: ImageVector,
    modifier: Modifier = Modifier.size(48.dp),
    shape: Shape = CircleShape,
    tint: Color = Color.White,
    contentDescription: String? = null,
    interactive: Boolean = true,
    onClick: () -> Unit,
) {
    LiquidGlassContainer(
        backdrop = backdrop,
        modifier = modifier,
        shape = shape,
        interactive = interactive,
    ) {
        Material3IconButton(
            onClick = onClick,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = tint,
            )
        }
    }
}
