/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * Reveals one preference on a settings sub-page: scrolls it into view and flashes a tint over it.
 * Drives the `?scrollTo=<key>` deep links that settings search hands out.
 */
@Composable
fun rememberPreferencePositions(): PreferencePositions {
    val highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    val positions = remember { PreferencePositions() }
    // Re-read every composition instead of capturing once: Appearance settings is itself one of
    // the deep-linkable screens, so the theme can change while a highlight is on screen.
    positions.highlightColor = highlightColor
    return positions
}

@Stable
class PreferencePositions {
    private val positions = mutableMapOf<String, Float>()
    private var viewportTop: Float? = null
    private var viewportHeight: Float = 0f

    var highlightColor: Color = Color.Transparent

    /** Read at draw time, so a change repaints without recomposing the whole screen. */
    private var highlightedKey by mutableStateOf<String?>(null)
    private var highlightAlpha by mutableFloatStateOf(0f)

    /**
     * Records the scrolling viewport. Chain this *before* `verticalScroll` so it measures the
     * window the content scrolls inside, not the content itself.
     */
    fun containerModifier(): Modifier =
        Modifier.onGloballyPositioned { coordinates ->
            viewportTop = coordinates.positionInRoot().y
            viewportHeight = coordinates.size.height.toFloat()
        }

    /**
     * Marks a preference as the row identified by [keys]: records where it is and draws the reveal
     * tint when one of them is the one being revealed.
     */
    fun modifierFor(vararg keys: String): Modifier =
        Modifier
            .onGloballyPositioned { coordinates ->
                val y = coordinates.positionInRoot().y
                keys.forEach { key -> positions[key] = y }
            }.drawWithContent {
                drawContent()
                // Local copy: highlightedKey is a delegated property, so it cannot be smart-cast.
                val active = highlightedKey
                val progress = if (active != null && active in keys) highlightAlpha else 0f
                if (progress <= 0f) return@drawWithContent
                val inset = HighlightInset.toPx()
                drawRoundRect(
                    color = highlightColor.copy(alpha = highlightColor.alpha * progress),
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2, size.height - inset * 2),
                    cornerRadius = CornerRadius(HighlightCornerRadius.toPx()),
                )
            }

    /**
     * Scrolls [scrollState] until the row registered for [key] sits at the top of the viewport,
     * then flashes the highlight.
     */
    suspend fun scrollToKey(
        key: String?,
        scrollState: ScrollableState,
    ) {
        if (key.isNullOrBlank()) return
        if (!awaitMeasurement(key, scrollState)) return

        repeat(SCROLL_PASSES) {
            val rowTop = positions[key] ?: return@repeat
            val delta = rowTop - (viewportTop ?: 0f)
            if (abs(delta) < SETTLED_THRESHOLD_PX) return@repeat
            scrollState.animateScrollBy(delta)
        }

        highlightedKey = key
        animate(0f, 1f, animationSpec = tween(HighlightFadeInMs)) { value, _ -> highlightAlpha = value }
        delay(HighlightHoldMs)
        animate(1f, 0f, animationSpec = tween(HighlightFadeOutMs)) { value, _ -> highlightAlpha = value }
        highlightedKey = null
    }

    /**
     * Waits until the row for [key] has reported a position, returning false when it never does.
     */
    private suspend fun awaitMeasurement(
        key: String,
        scrollState: ScrollableState,
    ): Boolean {
        repeat(MEASURE_TIMEOUT_STEPS) {
            if (positions[key] != null) return true
            delay(MEASURE_POLL_MS)
        }

        val step = viewportHeight * SEARCH_STEP_FRACTION
        if (step <= 0f) return false
        var walked = 0f
        var steps = 0
        while (positions[key] == null && steps < SEARCH_STEPS && scrollState.canScrollForward) {
            walked += scrollState.animateScrollBy(step)
            // One poll interval is enough for the newly revealed rows to report positions.
            delay(MEASURE_POLL_MS)
            steps++
        }
        if (positions[key] != null) return true
        if (walked > 0f) scrollState.animateScrollBy(-walked)
        return false
    }

    private companion object {
        /** Poll interval and budget while waiting for the target row's first measurement. */
        const val MEASURE_POLL_MS = 50L
        const val MEASURE_TIMEOUT_STEPS = 16

        /** How many corrective passes to make. Two is enough in practice; three is headroom. */
        const val SCROLL_PASSES = 3

        /** Anything smaller than this is already on target — sub-pixel jitter, not a miss. */
        const val SETTLED_THRESHOLD_PX = 2f

        /** How far to walk per step, and how many steps, when hunting an uncomposed row. */
        const val SEARCH_STEP_FRACTION = 0.8f
        const val SEARCH_STEPS = 24
    }
}

private val HighlightCornerRadius = 18.dp
private val HighlightInset = 1.dp
private const val HighlightFadeInMs = 220
private const val HighlightHoldMs = 1_100L
private const val HighlightFadeOutMs = 450
