/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.component

import android.os.SystemClock
import android.view.ViewConfiguration
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarArrangement
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.ShortNavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.utils.ImageBlurUtils
import moe.rukamori.archivetune.constants.DisableAnimationsKey
import moe.rukamori.archivetune.constants.FloatingNavigationBarMaxWidth
import moe.rukamori.archivetune.constants.HideNavigationBarLabelsKey
import moe.rukamori.archivetune.constants.NAVIGATION_BAR_CORNER_RADIUS_DEFAULT
import moe.rukamori.archivetune.constants.NAVIGATION_BAR_HEIGHT_DEFAULT
import moe.rukamori.archivetune.constants.NAVIGATION_BAR_LABEL_SPACING_DEFAULT
import moe.rukamori.archivetune.constants.NAVIGATION_BAR_OPACITY_DEFAULT
import moe.rukamori.archivetune.constants.NAVIGATION_BAR_TRANSPARENCY_DEFAULT
import moe.rukamori.archivetune.constants.NAVIGATION_BAR_WIDTH_DEFAULT
import moe.rukamori.archivetune.constants.NavigationBarCornerRadiusKey
import moe.rukamori.archivetune.constants.NavigationBarHeight
import moe.rukamori.archivetune.constants.NavigationBarHeightKey
import moe.rukamori.archivetune.constants.NavigationBarLabelSpacingKey
import moe.rukamori.archivetune.constants.NavigationBarMaxWidth
import moe.rukamori.archivetune.constants.NavigationBarOpacityKey
import moe.rukamori.archivetune.constants.NavigationBarStyle
import moe.rukamori.archivetune.constants.NavigationBarTransparencyKey
import moe.rukamori.archivetune.constants.NavigationBarWidthKey
import moe.rukamori.archivetune.ui.screens.Screens
import moe.rukamori.archivetune.utils.rememberPreference
import com.kyant.backdrop.backdrops.LayerBackdrop
import kotlin.math.roundToInt

/**
 * Shared handle for the frosted navigation bar: the app records its content into [layer] each
 * frame, and the bar draws that layer (offset by the recorded content's root position) behind a
 * blur so the pixels underneath show through frosted.
 */
class NavigationBarBackdrop(
    val layer: GraphicsLayer,
) {
    var contentOffsetInRoot: Offset = Offset.Zero
}

/**
 * The app-content capture used for frosted-glass surfaces (navigation bar, mini player). Null when
 * no frosted surface is enabled or the device cannot blur (below Android 12).
 */
val LocalNavigationBarBackdrop = compositionLocalOf<NavigationBarBackdrop?> { null }

private val NavigationItemsMaxWidth = 360.dp
private val NavigationItemVerticalPadding = 8.dp

// Frosted nav-bar backdrop blur radius, in px (RenderEffect works in raw pixels).
private const val FrostedNavBarBlurRadiusPx = 60f

// How much of the blurred backdrop shows through the opaque bar. The bar is always drawn on a
// fully opaque surface and the blurred content is composited on top at this alpha, so page
// brightness can only ever modulate the bar by this fraction — it reads the same over any screen,
// and if the backdrop layer has nothing under the bar the result is simply a solid bar.
private const val FrostedNavBarOverlayAlpha = 0.30f

// The tint-frosted variant uses a DARK TRANSLUCENT base (Color.Black at 55% alpha) instead of
// an opaque surface, so a higher overlay alpha lets more of the blurred app content show through
// the dark tint — the "tinted glass" look (dark, translucent, with visible frosted blur).
private const val TintFrostedNavBarOverlayAlpha = 0.45f

// The sliding pill wraps just the icon (like the stock indicator), so the label sits below it,
// outside the bubble. These are the standard Material3 active-indicator dimensions.
private val NavigationIndicatorWidth = 56.dp
private val NavigationIndicatorHeight = 32.dp

// The floating pill uses a larger, softer blob around the selected icon (label stays outside),
// tinted with the accent color like the reference bar.
private val FloatingNavigationIndicatorWidth = 64.dp
private val FloatingNavigationIndicatorHeight = 42.dp

/**
 * Forces the signature navigation-bar motion (the sliding pill + icon pop) to always run at its
 * full, intended duration, ignoring the system "Animator duration scale" developer setting. Users
 * who run the OS at 0.5x still get expressive tab-switch motion, while the in-app "disable
 * animations" toggle continues to fully bypass these animations.
 */
private object FullMotionDurationScale : MotionDurationScale {
    override val scaleFactor: Float = 1f
}

@Composable
fun FloatingNavigationToolbar(
    items: List<Screens>,
    pureBlack: Boolean,
    modifier: Modifier = Modifier,
    isPairedWithMiniPlayer: Boolean = false,
    style: NavigationBarStyle = NavigationBarStyle.DEFAULT,
    frostedBlur: Boolean = false,
    tintFrostedBlur: Boolean = false,
    frostedBackdrop: NavigationBarBackdrop? = null,
    liquidGlass: Boolean = false,
    liquidGlassBackdrop: LayerBackdrop? = null,
    isSelected: (Screens) -> Boolean,
    onItemClick: (Screens, Boolean) -> Unit,
    onSearchItemDoubleClick: (() -> Unit)? = null,
) {
    val isFloating = style == NavigationBarStyle.FLOATING
    // Navigation bar customization. Read directly here so the toolbar picks up the user's
    // tuning without the call site needing to thread 6 extra params.
    val (navBarWidthFraction) =
        rememberPreference(NavigationBarWidthKey, defaultValue = NAVIGATION_BAR_WIDTH_DEFAULT)
    val (navBarHeightMultiplier) =
        rememberPreference(NavigationBarHeightKey, defaultValue = NAVIGATION_BAR_HEIGHT_DEFAULT)
    val (navBarOpacity) =
        rememberPreference(NavigationBarOpacityKey, defaultValue = NAVIGATION_BAR_OPACITY_DEFAULT)
    val (navBarTransparency) =
        rememberPreference(NavigationBarTransparencyKey, defaultValue = NAVIGATION_BAR_TRANSPARENCY_DEFAULT)
    val (navBarLabelSpacing) =
        rememberPreference(NavigationBarLabelSpacingKey, defaultValue = NAVIGATION_BAR_LABEL_SPACING_DEFAULT)
    val (navBarCornerRadius) =
        rememberPreference(NavigationBarCornerRadiusKey, defaultValue = NAVIGATION_BAR_CORNER_RADIUS_DEFAULT)
    val resolvedBarHeight = NavigationBarHeight * navBarHeightMultiplier
    val navigationShape =
        remember(isPairedWithMiniPlayer, isFloating, navBarCornerRadius) {
            when {
                // A detached pill keeps the user-configurable corner radius (default 28 dp).
                isFloating -> RoundedCornerShape(navBarCornerRadius.dp)
                isPairedWithMiniPlayer ->
                    RoundedCornerShape(
                        topStart = 12.dp,
                        topEnd = 12.dp,
                        bottomStart = navBarCornerRadius.dp,
                        bottomEnd = navBarCornerRadius.dp,
                    )
                else -> null
            }
        } ?: MaterialTheme.shapes.extraLarge
    // True backdrop blur on Android 12+ uses RenderEffect (hardware-accelerated, every frame).
    // Below API 31 the frosted effect is disabled entirely: the pre-S CPU-blurred-bitmap fallback
    // produced visible glitches and tearing on older devices, so we degrade to a plain solid bar.
    // The Settings screen surfaces a "not supported on Android versions below 12" warning under
    // the toggle when running on pre-S.
    val isPreS = Build.VERSION.SDK_INT < Build.VERSION_CODES.S
    // Either frosted variant enables backdrop blur. The tint variant additionally tints the
    // bar surface with the accent (primary) color so the frost reads as a colored glass.
    val anyFrosted = frostedBlur || tintFrostedBlur
    val canBlurBackdrop = anyFrosted && frostedBackdrop != null && !isPreS
    // Liquid Glass nav bar: requires the master toggle on, the kyant LayerBackdrop
    // available (Android 12+), and not in pure-black mode (the liquid glass effect
    // needs a translucent surface to show the backdrop through).
    val canLiquidGlass = liquidGlass && liquidGlassBackdrop != null && !isPreS && !pureBlack
    val navigationContainerColor =
        if (pureBlack) {
            Color.Black
        } else if (canLiquidGlass) {
            // Liquid Glass: the surface must be transparent so the kyant
            // drawBackdrop sample (the app content with vibrancy/blur/lens)
            // is visible. The opaque fallback base color is drawn UNDER the
            // backdrop sample via the `liquidGlass(baseColor = ...)` modifier
            // (using the kyant `onDrawBehind` callback) — see the modifier
            // chain below. This mirrors how the FROSTED variant handles the
            // empty-backdrop case: an always-opaque surface with the blurred
            // content composited on top.
            Color.Transparent
        } else {
            // Apply user-configured opacity (always) and transparency (only when no frosted
            // variant is active — when frosted blur is on, the frost overlay already provides
            // the see-through effect and adding transparency here would double-count).
            val baseColor =
                if (tintFrostedBlur) {
                    // "Tinted glass": a DARK base with LOW opacity, NOT a solid
                    // primary color overlay. The previous implementation used
                    // `primary` as the base, which made the bar an opaque bright
                    // color with a thin frosted veil — impossible to read icons
                    // against. The user explicitly asked for "dark and low
                    // opacity like tinted glass, not just a color overlay".
                    //
                    // Color.Black at 55% alpha gives a dark tinted-glass base:
                    //   - Dark enough that white icons/labels are clearly visible
                    //     on top (contrast ratio > 4.5:1).
                    //   - Translucent enough that the frosted blur overlay
                    //     (composited on top at 40% alpha — see below) shows the
                    //     app content through the bar — the "tinted glass" look.
                    //   - NOT a bright primary color that would wash out the icons.
                    //
                    // A subtle primary tint is still applied via the frosted
                    // overlay itself (which samples the app content), so the bar
                    // reads as colored glass when there's colored content behind
                    // it, and as neutral dark glass when there isn't.
                    Color.Black.copy(alpha = 0.55f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                }
            val effectiveAlpha =
                navBarOpacity * (if (anyFrosted) 1f else (1f - navBarTransparency))
            baseColor.copy(alpha = effectiveAlpha.coerceIn(0.05f, 1f))
        }
    val motionScheme = MaterialTheme.motionScheme
    val (disableAnimations) = rememberPreference(DisableAnimationsKey, defaultValue = false)
    val (hideNavigationLabels) = rememberPreference(HideNavigationBarLabelsKey, defaultValue = false)
    val density = LocalDensity.current

    // Color of the custom sliding pill that sits behind the selected item's icon. The floating
    // pill uses a translucent accent blob with accent-tinted icon/label (reference-bar look); the
    // docked bar keeps the stock secondary-container treatment. For the tint-frosted variant,
    // the bar surface IS the primary color, so the indicator uses onPrimary (inverted) so the
    // selected icon stands out against the colored bar.
    val indicatorColor =
        when {
            // Liquid Glass: the pill sits on top of the blurred app-content
            // backdrop, so secondaryContainer (the default) would blend in and
            // be hard to see. Use a primary-tinted blob at a slightly higher
            // alpha so the selected item stands out — matching the floating
            // variant's visibility. The pill now also wraps the label (see
            // [liquidGlassPillWidth/Height]), so the entire selected item
            // (icon + label) is clearly highlighted. NOTE: for the Liquid
            // Glass variant, the pill is actually rendered as a liquid-glass
            // surface (see the pill Box below), so this color is only used as
            // a fallback if the liquidGlass modifier is somehow unavailable.
            canLiquidGlass -> MaterialTheme.colorScheme.primary.copy(alpha = 0.32f)
            // Tint-frosted: the bar is now a DARK tinted glass (Color.Black at
            // 55% alpha), so the pill uses a translucent white blob — the
            // selected icon stands out against the dark bar without being a
            // harsh solid white circle.
            tintFrostedBlur && !isFloating -> Color.White.copy(alpha = 0.18f)
            isFloating -> MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
            pureBlack -> Color.White.copy(alpha = 0.16f)
            else -> MaterialTheme.colorScheme.secondaryContainer
        }
    val indicatorWidth = if (isFloating) FloatingNavigationIndicatorWidth else NavigationIndicatorWidth
    val indicatorHeight = if (isFloating) FloatingNavigationIndicatorHeight else NavigationIndicatorHeight

    // The built-in per-item indicator just fades in place; hide it so our single pill can slide
    // between items instead. On pure-black we also pin the icon/label colors for contrast.
    // For the tint-frosted variant, the bar surface is the primary color, so icons/labels use
    // onPrimary for contrast (selected = full opacity, unselected = 0.85 opacity for legibility).
    val itemColors =
        when {
            isFloating ->
                ShortNavigationBarItemDefaults.colors(
                    selectedIndicatorColor = Color.Transparent,
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor =
                        if (pureBlack) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor =
                        if (pureBlack) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            pureBlack ->
                ShortNavigationBarItemDefaults.colors(
                    selectedIndicatorColor = Color.Transparent,
                    selectedIconColor = Color.White,
                    selectedTextColor = Color.White,
                    unselectedIconColor = Color.White.copy(alpha = 0.6f),
                    unselectedTextColor = Color.White.copy(alpha = 0.6f),
                )
            tintFrostedBlur ->
                ShortNavigationBarItemDefaults.colors(
                    selectedIndicatorColor = Color.Transparent,
                    // The tint-frosted bar is now a DARK tinted glass (Color.Black
                    // at 55% alpha), so icons/labels use White for contrast —
                    // matching the pure-black variant's contrast model. The
                    // previous onPrimary was unreadable because it assumed the
                    // bar was a bright primary color.
                    selectedIconColor = Color.White,
                    selectedTextColor = Color.White,
                    // 0.7 alpha keeps unselected icons legible but de-emphasized
                    // against the dark tinted glass.
                    unselectedIconColor = Color.White.copy(alpha = 0.7f),
                    unselectedTextColor = Color.White.copy(alpha = 0.7f),
                )
            else -> ShortNavigationBarItemDefaults.colors(selectedIndicatorColor = Color.Transparent)
        }

    val selectedIndex = items.indexOfFirst { isSelected(it) }

    // Measured center of each item's icon (root-space) and the row container's top-left, so the pill
    // can slide to the exact icon position regardless of layout/insets. Only the icon is tracked so
    // the bubble hugs the icon and leaves the text label outside of it.
    val iconCenters = remember { mutableStateMapOf<Int, Offset>() }
    // For the Liquid Glass variant, the pill wraps BOTH the icon and the label (the
    // user reported the selected icon was hard to see against the glass backdrop
    // because only the icon was highlighted, not the label). We track each item's
    // full bounds (icon + spacing + label) so the Liquid Glass pill can size to
    // cover both. The default variant keeps the icon-only pill (unchanged).
    val itemBounds = remember { mutableStateMapOf<Int, Rect>() }
    var containerPos by remember { mutableStateOf(Offset.Zero) }

    val indicatorX = remember { Animatable(0f) }
    var indicatorY by remember { mutableStateOf(0f) }
    var indicatorPlaced by remember { mutableStateOf(false) }
    // Liquid Glass pill size, computed from the selected item's full bounds
    // (icon + label). Zero until the first item is measured.
    var liquidGlassPillWidth by remember { mutableStateOf(0.dp) }
    var liquidGlassPillHeight by remember { mutableStateOf(0.dp) }

    val selectedCenter = if (selectedIndex >= 0) iconCenters[selectedIndex] else null
    val selectedItemBounds = if (selectedIndex >= 0) itemBounds[selectedIndex] else null
    LaunchedEffect(selectedIndex, selectedCenter, selectedItemBounds, containerPos, disableAnimations, indicatorWidth, indicatorHeight, canLiquidGlass) {
        if (canLiquidGlass) {
            // Liquid Glass: pill wraps the full item (icon + label). Size and
            // position are derived from the item's measured bounds, with a small
            // inset so the pill has breathing room inside the item.
            val bounds = selectedItemBounds ?: return@LaunchedEffect
            val pad = 4.dp
            val itemWidthDp = with(density) { bounds.width.toDp() }
            val itemHeightDp = with(density) { bounds.height.toDp() }
            liquidGlassPillWidth = (itemWidthDp - pad * 2).coerceAtLeast(indicatorWidth)
            liquidGlassPillHeight = (itemHeightDp - pad * 2).coerceAtLeast(indicatorHeight)
            val widthPx = with(density) { liquidGlassPillWidth.toPx() }
            val heightPx = with(density) { liquidGlassPillHeight.toPx() }
            val targetX = (bounds.left - containerPos.x) + (bounds.width - widthPx) / 2f
            indicatorY = (bounds.top - containerPos.y) + (bounds.height - heightPx) / 2f
            val firstPlacement = !indicatorPlaced
            if (disableAnimations || firstPlacement) {
                indicatorX.snapTo(targetX)
                indicatorPlaced = true
            } else {
                withContext(FullMotionDurationScale) {
                    indicatorX.animateTo(
                        targetValue = targetX,
                        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
                    )
                }
            }
        } else {
            // Default / frosted / floating: pill wraps only the icon (label
            // stays outside). This is the original behavior.
            val center = selectedCenter ?: return@LaunchedEffect
            val widthPx = with(density) { indicatorWidth.toPx() }
            val heightPx = with(density) { indicatorHeight.toPx() }
            val targetX = (center.x - containerPos.x) - widthPx / 2f
            indicatorY = (center.y - containerPos.y) - heightPx / 2f
            val firstPlacement = !indicatorPlaced
            if (disableAnimations || firstPlacement) {
                indicatorX.snapTo(targetX)
                indicatorPlaced = true
            } else {
                withContext(FullMotionDurationScale) {
                    indicatorX.animateTo(
                        targetValue = targetX,
                        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
                    )
                }
            }
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)),
        contentAlignment = Alignment.Center,
    ) {
        var barPositionInRoot by remember { mutableStateOf(Offset.Zero) }
        var barSize by remember { mutableStateOf(IntSize.Zero) }
        Surface(
            modifier =
                Modifier
                    .widthIn(max = if (isFloating) FloatingNavigationBarMaxWidth else NavigationBarMaxWidth)
                    .fillMaxWidth(if (isFloating) navBarWidthFraction.coerceIn(0.5f, 1f) else 1f)
                    .height(resolvedBarHeight)
                    .onGloballyPositioned {
                        barPositionInRoot = it.positionInRoot()
                        barSize = it.size
                    }
                    .then(
                        // Liquid Glass nav bar: apply the kyant liquidGlass modifier to the
                        // Surface itself. This samples the app content (captured by
                        // Modifier.layerBackdrop in MainActivity) with vibrancy/blur/lens and
                        // draws it as the bar background, with a darken overlay for contrast.
                        // The Surface's color is already transparent (see [navigationContainerColor]),
                        // so the liquid glass backdrop shows through. The Surface's shape clips
                        // the liquid glass to the pill / docked shape.
                        //
                        // The `baseColor` parameter passes an OPAQUE surfaceContainerHigh fill
                        // that is drawn UNDER the backdrop sample (via the kyant `onDrawBehind`
                        // callback). When the backdrop has content (e.g. scrolling album art
                        // behind the bar), the backdrop sample covers the base color — producing
                        // the liquid glass refraction effect. When the backdrop is EMPTY (e.g.
                        // bottom of a short page with no content behind the bar), the backdrop
                        // sample is transparent and the opaque base color shows through — so
                        // the bar is always visible instead of "completely transparent".
                        if (canLiquidGlass && liquidGlassBackdrop != null) {
                            Modifier.liquidGlass(
                                backdrop = liquidGlassBackdrop,
                                shape = navigationShape,
                                interactive = false,
                                baseColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            )
                        } else {
                            Modifier
                        },
                    ),
            shape = navigationShape,
            color = navigationContainerColor,
            // For Liquid Glass, the kyant drawBackdrop modifier already clips its
            // GraphicsLayer to navigationShape (clip = true + shape = shape in the
            // DrawBackdropNode's layoutLayerBlock). Material3 Surface's shadow is
            // normally drawn OUTSIDE the shape, but with the drawBackdrop modifier
            // wrapping the Surface's content, the shadow is clipped INSIDE the shape,
            // producing a visible white-ish ambient shadow tint inside the pill — the
            // "white space inside navigation bar" the user reported. Setting both
            // elevations to 0 for Liquid Glass eliminates the clipped shadow; the
            // liquidGlass effect itself provides depth via the lens refraction.
            tonalElevation = if (canLiquidGlass) 0.dp else NavigationBarDefaults.Elevation,
            shadowElevation = if (canLiquidGlass) 0.dp else if (isFloating) 8.dp else NavigationBarDefaults.Elevation,
        ) {
            if (canBlurBackdrop && frostedBackdrop != null) {
                if (isPreS) {
                    // Pre-Android 12: RenderEffect is unavailable. Capture the app-content
                    // GraphicsLayer periodically (see [rememberPreSFrostedBitmap]), extract just
                    // the slice under the bar, blur it on the CPU via ImageBlurUtils, and composite
                    // the small blurred slice on top of the opaque bar at the same bounded alpha as
                    // the S+ path. The bar surface's shape already clips its content, so the
                    // overlay is correctly clipped to the pill shape. The blurred slice is already
                    // aligned to the bar's top-left (the helper extracts it from the bar's position
                    // in root), so we draw it at (0, 0) — no translate, no clip needed.
                    val blurredBitmap = rememberPreSFrostedBitmap(
                        backdrop = frostedBackdrop,
                        barPositionInRoot = barPositionInRoot,
                        barSize = barSize,
                        blurRadiusPx = FrostedNavBarBlurRadiusPx,
                    )
                    if (blurredBitmap != null) {
                        // Match the S+ path's conditional overlay alpha (see the comment below).
                        val preSOverlayAlpha = if (tintFrostedBlur) TintFrostedNavBarOverlayAlpha else FrostedNavBarOverlayAlpha
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        alpha = preSOverlayAlpha
                                        clip = true
                                    }.drawBehind {
                                        drawImage(blurredBitmap)
                                    },
                        )
                    }
                } else {
                    // Frosted glass on top of an always-opaque bar: the app content captured this frame
                    // is shifted so the region underneath lines up, blurred, and composited at a bounded
                    // alpha. Page brightness can only modulate the bar by that fraction, so the bar
                    // looks the same over every screen — and if the captured layer has nothing under
                    // the bar, the result is simply the solid bar (never see-through).
                    //
                    // The tint-frosted variant uses a HIGHER overlay alpha (0.45 vs 0.30) because
                    // its base is a dark translucent glass (Color.Black at 55% alpha) rather than
                    // an opaque surface. The higher alpha lets more of the blurred app content show
                    // through the dark tint, producing the "tinted glass" look the user asked for:
                    // dark, translucent, with visible frosted blur — not just a flat color overlay.
                    val frostedOverlayAlpha = if (tintFrostedBlur) TintFrostedNavBarOverlayAlpha else FrostedNavBarOverlayAlpha
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    renderEffect =
                                        BlurEffect(
                                            radiusX = FrostedNavBarBlurRadiusPx,
                                            radiusY = FrostedNavBarBlurRadiusPx,
                                            edgeTreatment = TileMode.Clamp,
                                        )
                                    alpha = frostedOverlayAlpha
                                    clip = true
                                }.drawBehind {
                                    val offset = frostedBackdrop.contentOffsetInRoot - barPositionInRoot
                                    translate(offset.x, offset.y) {
                                        drawLayer(frostedBackdrop.layer)
                                    }
                                },
                    )
                }
            }
            ShortNavigationBar(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent,
                contentColor =
                    when {
                        pureBlack -> Color.White
                        // Tint-frosted bar is now dark glass, so content is white.
                        tintFrostedBlur -> Color.White
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                windowInsets = WindowInsets(0, 0, 0, 0),
                arrangement = ShortNavigationBarArrangement.EqualWeight,
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .onGloballyPositioned { containerPos = it.positionInRoot() },
                    contentAlignment = Alignment.Center,
                ) {
                    // Custom sliding pill indicator, drawn behind the icons.
                    // For the Liquid Glass variant, the pill wraps BOTH the icon
                    // and the label (see [itemBounds] tracking + the LaunchedEffect
                    // above) so the selected item's full label is highlighted —
                    // not just the icon. The pill itself is a liquid-glass surface
                    // (vibrancy/blur/lens sampling the same app-content backdrop
                    // as the bar) with a primary tint composited on top, matching
                    // the SukiSU-Ultra FloatingBottomBar look: a distinct glass
                    // pill floating on the glass bar. For all other variants, the
                    // pill wraps only the icon (label stays outside it) and uses a
                    // solid `indicatorColor` fill.
                    if (selectedIndex >= 0 && indicatorPlaced) {
                        val pillWidth = if (canLiquidGlass) liquidGlassPillWidth else indicatorWidth
                        val pillHeight = if (canLiquidGlass) liquidGlassPillHeight else indicatorHeight
                        if (pillWidth > 0.dp && pillHeight > 0.dp) {
                            val pillShape = RoundedCornerShape(percent = 50)
                            // Extract the primary tint color BEFORE the modifier
                            // chain — `drawWithContent`'s lambda is NOT a
                            // @Composable scope, so `MaterialTheme.colorScheme.primary`
                            // (which IS @Composable) cannot be accessed inside it.
                            // Reading it here (in the @Composable body) and
                            // capturing it in a val makes the color available to
                            // the non-composable draw lambda.
                            val pillTintColor =
                                if (canLiquidGlass) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                } else {
                                    Color.Transparent
                                }
                            Box(
                                modifier =
                                    Modifier
                                        .align(Alignment.TopStart)
                                        .offset { IntOffset(indicatorX.value.roundToInt(), indicatorY.roundToInt()) }
                                        .width(pillWidth)
                                        .height(pillHeight)
                                        .then(
                                            if (canLiquidGlass && liquidGlassBackdrop != null) {
                                                // Liquid-glass pill: sample the app-content
                                                // backdrop with vibrancy/blur/lens (same stack
                                                // as the bar), then composite a translucent
                                                // primary tint ON TOP so the pill is visually
                                                // distinct from the bar beneath it. The tint
                                                // is drawn via `drawWithContent` AFTER the
                                                // liquidGlass effect (modifier order:
                                                // liquidGlass → drawWithContent → content),
                                                // so it sits above the backdrop sample + veil.
                                                Modifier
                                                    .liquidGlass(
                                                        backdrop = liquidGlassBackdrop,
                                                        shape = pillShape,
                                                        interactive = false,
                                                    )
                                                    .drawWithContent {
                                                        drawContent()
                                                        drawRect(color = pillTintColor)
                                                    }
                                            } else {
                                                Modifier
                                                    .clip(pillShape)
                                                    .background(indicatorColor)
                                            },
                                        ),
                            )
                        }
                    }

                    Row(
                        modifier =
                            Modifier
                                .widthIn(max = NavigationItemsMaxWidth)
                                .fillMaxWidth()
                                .fillMaxHeight()
                                .padding(vertical = NavigationItemVerticalPadding),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        items.forEachIndexed { index, screen ->
                            val selected = isSelected(screen)
                            // Tactile "pop": when an item becomes selected, its icon springs
                            // from 80% up past 100% and settles back, giving the bottom pill a
                            // lively bounce on every tab switch (skipped when animations are off).
                            val iconScale = remember(screen) { Animatable(1f) }
                            LaunchedEffect(selected, disableAnimations) {
                                if (disableAnimations) {
                                    iconScale.snapTo(1f)
                                } else if (selected) {
                                    iconScale.snapTo(0.8f)
                                    // Fixed motion scale keeps the pop expressive at 0.5x too.
                                    withContext(FullMotionDurationScale) {
                                        iconScale.animateTo(
                                            targetValue = 1f,
                                            animationSpec =
                                                spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessMediumLow,
                                                ),
                                        )
                                    }
                                } else {
                                    iconScale.snapTo(1f)
                                }
                            }
                            val onDoubleClick =
                                remember(screen, onSearchItemDoubleClick) {
                                    if (screen == Screens.Search) onSearchItemDoubleClick else null
                                }
                            val lastClickTime = remember(screen) { mutableLongStateOf(0L) }
                            val onClick =
                                remember(screen, selected, onItemClick, onDoubleClick) {
                                    {
                                        val currentTime = SystemClock.uptimeMillis()
                                        val isDoubleClick =
                                            onDoubleClick != null &&
                                                currentTime - lastClickTime.longValue <= ViewConfiguration.getDoubleTapTimeout()
                                        lastClickTime.longValue = if (isDoubleClick) 0L else currentTime
                                        if (isDoubleClick) {
                                            onDoubleClick?.invoke()
                                            Unit
                                        } else {
                                            onItemClick(screen, selected)
                                        }
                                    }
                                }

                            ShortNavigationBarItem(
                                selected = selected,
                                onClick = onClick,
                                colors = itemColors,
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .onGloballyPositioned { coordinates ->
                                            // Track the full item bounds (icon + spacing +
                                            // label) so the Liquid Glass pill can size to
                                            // cover both. The default variant ignores this
                                            // and uses the icon-only [iconCenters] map.
                                            val pos = coordinates.positionInRoot()
                                            itemBounds[index] =
                                                Rect(
                                                    pos.x,
                                                    pos.y,
                                                    pos.x + coordinates.size.width,
                                                    pos.y + coordinates.size.height,
                                                )
                                        },
                                icon = {
                                    // Measure the icon's own bounds so the pill hugs only the icon.
                                    Box(
                                        modifier =
                                            Modifier.onGloballyPositioned { coordinates ->
                                                val pos = coordinates.positionInRoot()
                                                iconCenters[index] =
                                                    Offset(
                                                        pos.x + coordinates.size.width / 2f,
                                                        pos.y + coordinates.size.height / 2f,
                                                    )
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Crossfade(
                                            targetState = selected,
                                            animationSpec = motionScheme.fastEffectsSpec(),
                                            label = "navigationItemIcon",
                                        ) { isSelected ->
                                            Icon(
                                                painter =
                                                    painterResource(
                                                        if (isSelected) screen.iconIdActive else screen.iconIdInactive,
                                                    ),
                                                contentDescription = null,
                                                modifier =
                                                    Modifier.graphicsLayer {
                                                        scaleX = iconScale.value
                                                        scaleY = iconScale.value
                                                    },
                                            )
                                        }
                                    }
                                },
                                label = if (hideNavigationLabels) {
                                    null
                                } else {
                                    {
                                        // User-configurable spacing between icon and label.
                                        Spacer(Modifier.height(navBarLabelSpacing.dp))
                                        Text(
                                            text = stringResource(screen.titleId),
                                            maxLines = 1,
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Pre-Android 12 (pre-S) fallback for the frosted-glass surfaces (navigation bar, mini player).
 *
 * On Android 12+ the frosted effect uses [BlurEffect] (RenderEffect, hardware-accelerated, every
 * frame). Below API 31 RenderEffect is unavailable, so the previous implementation silently fell
 * back to a plain opaque surface — the frosted setting appeared to do nothing on older devices.
 *
 * This helper restores a real frosted effect on pre-S by periodically capturing the app-content
 * [GraphicsLayer] (the same one the S+ path uses) to a [Bitmap], running it through
 * [ImageBlurUtils.blur] (a pure-CPU stack blur that needs no RenderEffect), and publishing the
 * result as an [ImageBitmap] the caller draws with the same offset/alpha as the S+ path.
 *
 * SLICE OPTIMIZATION (fixes the "weird glitchy blur" the user reported on pre-S):
 * The original implementation captured the FULL app-content layer (typically 1080x2400 px on a
 * phone), blurred the entire thing, then drew a small slice of it on the bar via translate+clip.
 * That had three problems on pre-S hardware:
 *   1. The full-screen capture + full-screen blur was slow, forcing a 200 ms update interval
 *      (5 fps) — visible "jumps" as content scrolled, reading as glitchiness.
 *   2. [ImageBlurUtils.blur] downscales any source >720 px to 720 px before stack-blurring, then
 *      upscales back. On a full-screen source the downscale factor was ~0.3, so the blurred
 *      result was extremely low-resolution and looked pixelated/muddy when upscaled.
 *   3. Allocating a full-screen ARGB_8888 bitmap (~10 MB) every frame caused heavy GC pressure,
 *      adding stutter on top of the slow blur.
 *
 * The slice path fixes all three: we capture the full layer ONCE per update (unavoidable —
 * GraphicsLayer has no region capture), but then immediately extract just the small rectangle
 * that lies under the bar (e.g. 1080x240 px) via [Bitmap.createBitmap] before blurring. The
 * slice is small enough that [ImageBlurUtils.blur]'s 720 px downscale threshold either doesn't
 * trigger or triggers at a much milder factor (~0.67 instead of ~0.3), so the blur keeps real
 * resolution. The slice is also ~10x smaller, so allocation/GC is ~10x lighter and we can push
 * the update interval down to 80 ms (~12 fps) for visibly smoother tracking. The slice is
 * extracted with [blurRadiusPx] of padding on every side so the stack blur has neighboring
 * pixels to sample at the bar's edges — without padding the blur would just clamp the bar's own
 * edge pixels and the frost would look wrong at the boundary.
 *
 * The caller receives the small blurred slice (already aligned to the bar's top-left) and draws
 * it at (0, 0) — no translate, no clip — at the same bounded alpha as the S+ path. If the bar
 * moves between slice extraction and draw, the slice shows the content that was at the bar's
 * extraction-time position; for the navigation bar (which is fixed) and the mini player (which
 * only moves during drag), this is imperceptible.
 *
 * Returns `null` while the layer has no size (before first `record { ... }`), while the bar
 * hasn't been positioned yet, or if the capture fails — the caller should keep the opaque base
 * surface in that case.
 *
 * @param backdrop The shared capture handle (layer + content offset in root).
 * @param barPositionInRoot The bar's current top-left position in root coordinates. Read fresh
 *   each capture via [rememberUpdatedState], so the slice tracks the bar as it moves.
 * @param barSize The bar's current size in pixels. Read fresh each capture.
 * @param blurRadiusPx Blur radius in raw pixels (clamped to 0.5..48 by [ImageBlurUtils.blur]).
 * @param updateIntervalMs Capture+blur throttle. Default 80 ms (~12 fps) — fast enough for
 *   smooth frosted tracking, slow enough to not tank pre-S hardware.
 */
@Composable
internal fun rememberPreSFrostedBitmap(
    backdrop: NavigationBarBackdrop?,
    barPositionInRoot: Offset,
    barSize: IntSize,
    blurRadiusPx: Float,
    updateIntervalMs: Long = 80L,
): ImageBitmap? {
    if (backdrop == null) return null
    var blurred by remember(backdrop, blurRadiusPx, updateIntervalMs) {
        mutableStateOf<ImageBitmap?>(null)
    }
    // rememberUpdatedState lets the LaunchedEffect's coroutine read the LATEST bar position/size
    // without re-launching on every layout pass (the LaunchedEffect's keys are intentionally
    // coarse — backdrop/blurRadius/interval — so the capture loop isn't torn down and rebuilt
    // every time the bar moves).
    val barPositionState = rememberUpdatedState(barPositionInRoot)
    val barSizeState = rememberUpdatedState(barSize)

    LaunchedEffect(backdrop, blurRadiusPx, updateIntervalMs) {
        while (isActive) {
            val layer = backdrop.layer
            val layerW = layer.size.width
            val layerH = layer.size.height
            if (layerW > 0 && layerH > 0) {
                try {
                    val next = withContext(Dispatchers.Default) {
                        // Read the latest bar geometry. These States are updated by the caller's
                        // onGloballyPositioned, which fires on the main thread during layout.
                        val pos = barPositionState.value
                        val size = barSizeState.value
                        if (size.width <= 0 || size.height <= 0) return@withContext null

                        // The slice is the bar's bounds expressed in the LAYER's coordinate
                        // system (layer's top-left is contentOffsetInRoot in root coords).
                        val contentOffset = backdrop.contentOffsetInRoot
                        val rawX = (pos.x - contentOffset.x).toInt()
                        val rawY = (pos.y - contentOffset.y).toInt()

                        // Padding around the slice so the stack blur has neighboring pixels to
                        // sample at the bar's edges. Without this, the blur clamps the bar's own
                        // edge pixels and the frost looks wrong at the boundary.
                        val pad = blurRadiusPx.toInt().coerceIn(8, 64)

                        // Clamp the padded slice to the layer's bounds. If the bar is partially
                        // off-layer (e.g. floating bar that overshoots), we still capture what we
                        // can — the missing pixels are filled with the layer's edge color via
                        // stack blur's edge clamping, which is acceptable.
                        val paddedX = rawX - pad
                        val paddedY = rawY - pad
                        val paddedW = size.width + 2 * pad
                        val paddedH = size.height + 2 * pad
                        val clampedX = paddedX.coerceIn(0, layerW - 1)
                        val clampedY = paddedY.coerceIn(0, layerH - 1)
                        val clampedRight = (paddedX + paddedW).coerceIn(1, layerW)
                        val clampedBottom = (paddedY + paddedH).coerceIn(1, layerH)
                        val clampedW = clampedRight - clampedX
                        val clampedH = clampedBottom - clampedY
                        if (clampedW <= 0 || clampedH <= 0) return@withContext null

                        // Capture the full layer to a bitmap. This is the expensive part (GPU→CPU
                        // readback) but is unavoidable — GraphicsLayer has no region capture API.
                        val imageBitmap = layer.toImageBitmap()
                        val fullBitmap = imageBitmap.asAndroidBitmap()

                        // Extract just the small slice under the bar (with padding). This is a
                        // cheap pixel copy; the slice is ~10x smaller than the full bitmap.
                        val sliceBitmap = Bitmap.createBitmap(
                            fullBitmap,
                            clampedX,
                            clampedY,
                            clampedW,
                            clampedH,
                        )

                        // Blur the small slice directly. Because the slice is small, the 720 px
                        // downscale threshold in ImageBlurUtils either doesn't trigger or triggers
                        // at a mild factor, so the blur keeps real resolution (no muddy upscale).
                        val blurredSlice = ImageBlurUtils.blur(sliceBitmap, blurRadiusPx)

                        // Crop the bar's actual bounds out of the (padded) blurred slice. The
                        // slice's top-left corresponds to layer coords (clampedX, clampedY), so
                        // the bar's top-left in the slice is at (rawX - clampedX, rawY - clampedY).
                        // In the common case (no edge clipping) this is (pad, pad). If the bar
                        // was near the layer's edge and padding was clipped, the offset is
                        // smaller but never negative (clampedX <= rawX because the bar is inside
                        // the layer, so rawX - clampedX >= 0).
                        val barXInSlice = (rawX - clampedX).coerceIn(0, blurredSlice.width - 1)
                        val barYInSlice = (rawY - clampedY).coerceIn(0, blurredSlice.height - 1)
                        val barW = size.width.coerceAtMost(blurredSlice.width - barXInSlice)
                        val barH = size.height.coerceAtMost(blurredSlice.height - barYInSlice)
                        if (barW <= 0 || barH <= 0) {
                            // Degenerate slice — return as-is and let the caller clip.
                            blurredSlice.asImageBitmap()
                        } else if (barXInSlice == 0 && barYInSlice == 0 &&
                            blurredSlice.width == size.width && blurredSlice.height == size.height
                        ) {
                            // No padding was added (pad == 0, impossible here since pad >= 8) or
                            // slice is already exactly the bar size — skip the extra allocation.
                            blurredSlice.asImageBitmap()
                        } else {
                            Bitmap.createBitmap(blurredSlice, barXInSlice, barYInSlice, barW, barH)
                                .asImageBitmap()
                        }
                    }
                    if (next != null) blurred = next
                } catch (_: Throwable) {
                    // Capture or blur failed (e.g. OOM, native crash on some devices) — keep the
                    // previous frame; the opaque base surface is still visible underneath.
                }
            }
            delay(updateIntervalMs)
        }
    }
    return blurred
}
