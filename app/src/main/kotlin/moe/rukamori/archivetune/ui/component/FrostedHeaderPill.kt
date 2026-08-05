/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.component

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.utils.ImageBlurUtils

private const val FrostedHeaderBlurRadiusPx = 40f
private const val FrostedHeaderOverlayAlpha = 0.45f

/**
 * A frosted-glass pill that wraps header content (title text, icon buttons) so the header
 * can be transparent while the content inside it stays legible against any background.
 *
 * Mirrors the frosted mechanism in [FloatingNavigationToolbar]: on Android 12+ it uses
 * [BlurEffect] (hardware-accelerated, every frame) composited over the shared app-content
 * [GraphicsLayer] from [LocalNavigationBarBackdrop]. On pre-S it falls back to a periodic
 * CPU-blurred bitmap slice (same approach as the nav bar's pre-S path). If no backdrop
 * layer is available (frosted blur disabled), it degrades to a semi-transparent
 * `surfaceContainer` pill — still legible, just not blurred.
 *
 * Usage: wrap the title / actions of a `TopAppBar` (or any header) in this pill. The
 * outer `TopAppBar` should have `containerColor = Color.Transparent`.
 *
 * @param modifier Modifier for the pill's outer layout.
 * @param content The header content (text, icons) to display inside the pill.
 */
@Composable
fun FrostedHeaderPill(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val backdrop = LocalNavigationBarBackdrop.current
    val isPreS = Build.VERSION.SDK_INT < Build.VERSION_CODES.S
    val canBlur = backdrop != null && !isPreS

    val baseColor = MaterialTheme.colorScheme.surfaceContainer
    val pillColor =
        if (canBlur) {
            // When blurring, the pill surface is mostly transparent so the blurred content
            // shows through. A slight tint provides a frosted milkiness.
            baseColor.copy(alpha = FrostedHeaderOverlayAlpha)
        } else {
            // No blur available: use a higher-alpha surface so the text stays legible.
            baseColor.copy(alpha = 0.85f)
        }

    var pillPositionInRoot by remember { mutableStateOf(Offset.Zero) }
    var pillSize by remember { mutableStateOf(IntSize.Zero) }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .onGloballyPositioned {
                pillPositionInRoot = it.positionInRoot()
                pillSize = it.size
            },
        shape = RoundedCornerShape(percent = 50),
        color = pillColor,
    ) {
        Box {
            // Frosted backdrop: draw the blurred app-content slice behind the pill content.
            if (canBlur && backdrop != null) {
                if (isPreS) {
                    val blurredBitmap = rememberPreSFrostedHeaderBitmap(
                        backdrop = backdrop,
                        barPositionInRoot = pillPositionInRoot,
                        barSize = pillSize,
                        blurRadiusPx = FrostedHeaderBlurRadiusPx,
                    )
                    if (blurredBitmap != null) {
                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    alpha = FrostedHeaderOverlayAlpha
                                    clip = true
                                }
                                .drawBehind { drawImage(blurredBitmap) },
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                renderEffect = BlurEffect(
                                    radiusX = FrostedHeaderBlurRadiusPx,
                                    radiusY = FrostedHeaderBlurRadiusPx,
                                    edgeTreatment = TileMode.Clamp,
                                )
                                alpha = FrostedHeaderOverlayAlpha
                                clip = true
                            }
                            .drawBehind {
                                val offset = backdrop.contentOffsetInRoot - pillPositionInRoot
                                translate(offset.x, offset.y) {
                                    drawLayer(backdrop.layer)
                                }
                            },
                    )
                }
            }
            // Actual content on top.
            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                content()
            }
        }
    }
}

/**
 * Pre-S fallback that captures a slice of the shared backdrop layer, blurs it on the CPU,
 * and returns it for compositing. Mirrors [rememberPreSFrostedBitmap] in
 * FloatingNavigationToolbar but is duplicated here to keep the header component
 * self-contained (the nav bar version is `internal` to the component file).
 */
@Composable
private fun rememberPreSFrostedHeaderBitmap(
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
    val barPositionState = rememberUpdatedState(barPositionInRoot)
    val barSizeState = rememberUpdatedState(barSize)

    androidx.compose.runtime.LaunchedEffect(backdrop, blurRadiusPx, updateIntervalMs) {
        while (isActive) {
            val layer = backdrop.layer
            val layerW = layer.size.width
            val layerH = layer.size.height
            if (layerW > 0 && layerH > 0) {
                try {
                    val next = withContext(Dispatchers.Default) {
                        val pos = barPositionState.value
                        val size = barSizeState.value
                        if (size.width <= 0 || size.height <= 0) return@withContext null

                        val contentOffset = backdrop.contentOffsetInRoot
                        val rawX = (pos.x - contentOffset.x).toInt()
                        val rawY = (pos.y - contentOffset.y).toInt()
                        val pad = blurRadiusPx.toInt().coerceIn(8, 64)
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

                        val imageBitmap = layer.toImageBitmap()
                        val fullBitmap = imageBitmap.asAndroidBitmap()
                        val sliceBitmap = android.graphics.Bitmap.createBitmap(
                            fullBitmap, clampedX, clampedY, clampedW, clampedH,
                        )
                        val blurredSlice = ImageBlurUtils.blur(sliceBitmap, blurRadiusPx)
                        val barXInSlice = (rawX - clampedX).coerceIn(0, blurredSlice.width - 1)
                        val barYInSlice = (rawY - clampedY).coerceIn(0, blurredSlice.height - 1)
                        val barW = size.width.coerceAtMost(blurredSlice.width - barXInSlice)
                        val barH = size.height.coerceAtMost(blurredSlice.height - barYInSlice)
                        if (barW <= 0 || barH <= 0) {
                            blurredSlice.asImageBitmap()
                        } else {
                            android.graphics.Bitmap.createBitmap(
                                blurredSlice, barXInSlice, barYInSlice, barW, barH,
                            ).asImageBitmap()
                        }
                    }
                    if (next != null) blurred = next
                } catch (_: Throwable) {
                    // Keep previous frame on failure.
                }
            }
            delay(updateIntervalMs)
        }
    }
    return blurred
}
