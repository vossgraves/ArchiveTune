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
 * Reveals one preference on a settings sub-page: scrolls it into view and flashes a tint over
 * it. Drives the `?scrollTo=<key>` deep links that settings search hands out.
 *
 * ## Usage
 *
 * ```kotlin
 * fun SomeSettings(navController: NavController, scrollTo: String? = null) {
 *     val positions = rememberPreferencePositions()
 *     val scrollState = rememberScrollState()
 *     Column(
 *         Modifier
 *             .then(positions.containerModifier())   // must be OUTSIDE verticalScroll
 *             .verticalScroll(scrollState),
 *     ) {
 *         PreferenceGroup(modifier = positions.modifierFor("dynamic_theme"), title = "Theme") { … }
 *     }
 *     LaunchedEffect(scrollTo) { positions.scrollToKey(scrollTo, scrollState) }
 * }
 * ```
 *
 * ## Why the container modifier is required
 *
 * [modifierFor] can only measure a row in *root* coordinates — preference rows sit two or three
 * layout levels deep (scrolling Column > PreferenceGroup's Column > inner Column), so a
 * parent-relative offset would be measured against the wrong box. A root y-position, however,
 * includes everything above the list: status bar, top app bar, and content padding. The old
 * implementation scrolled straight to that number, so every jump overshot the target by the
 * height of the app bar — the requested row ended up above the viewport and the row *below* it
 * was what the user saw.
 *
 * Registering the viewport's own root y-position closes that gap: the distance to travel is
 * `rowTop - viewportTop`, which is a pure delta and needs no knowledge of the current scroll
 * offset. [containerModifier] must therefore be chained *before* `verticalScroll`, where it
 * measures the viewport instead of the content that slides inside it.
 *
 * When a screen forgets the container modifier the viewport top is treated as 0, which
 * reproduces the old overshooting behaviour rather than failing outright.
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
     * Marks a preference as the row identified by [keys]: records where it is and draws the
     * reveal tint when one of them is the one being revealed.
     *
     * More than one key is accepted because the search index and the screens disagree in places:
     * the same row is listed under a legacy key on a parent page and under its own key on the
     * sub-page that actually owns it (`lrclib` vs `enable_lrclib`). Registering both aliases on
     * the one row is cheaper and less error-prone than renaming index entries users may already
     * be searching for.
     *
     * The tint is drawn *over* the row rather than behind it, because preference rows are Cards
     * with an opaque container colour — anything painted behind them is covered up.
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
     *
     * Waits for the row to be measured first: a deep link composes the screen and starts this
     * effect in the same frame, before any [modifierFor] callback has run. Rows that are never
     * registered (a search result whose target has no anchor yet) leave the screen where it
     * opened, which is the same place it would have been without the deep link.
     *
     * The scroll runs as a short convergence loop rather than a single jump because scrolling
     * can change the layout it was aimed at — groups that size themselves against the viewport,
     * and rows whose content only measures once visible. Each pass re-reads the row's freshly
     * measured position, so a second pass corrects whatever the first one disturbed.
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
     *
     * Two reasons a row can be unmeasured. In a `verticalScroll` column every row is composed
     * whether or not it is visible, so it only needs a few frames to lay out — the poll loop
     * covers that. A `LazyColumn` screen, though, never composes rows that are far below the
     * viewport, so waiting alone would time out on exactly the deep targets that need scrolling
     * the most. Stepping down the list a viewport at a time brings those into composition; the
     * walk stops as soon as the row registers, leaving the convergence loop to land it precisely.
     *
     * A walk that finds nothing rewinds itself, so an unanchored key leaves the screen at the
     * top rather than parked at the bottom of a list the user never asked to scroll.
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
