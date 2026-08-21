/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * Provides a [Modifier] that registers the global y-position of a composable
 * under the given [key]. Multiple keys can be tracked by calling [modifierFor]
 * for each target item.
 *
 * Usage in a sub-settings page:
 * ```kotlin
 * fun SomeSettings(navController: NavController, scrollTo: String? = null) {
 *   val positions = rememberPreferencePositions()
 *   Column(Modifier.verticalScroll(scrollState)) {
 *     PreferenceGroup(
 *       modifier = positions.modifierFor("dynamic_theme"),
 *       title = "Theme"
 *     ) { ... }
 *   }
 *   LaunchedEffect(scrollTo) { positions.scrollToKey(scrollTo, scrollState) }
 * }
 * ```
 */
@Composable
internal fun rememberPreferencePositions(): PreferencePositions {
    val positions = remember { mutableMapOf<String, Int>() }
    return remember { PreferencePositions(positions) }
}

internal class PreferencePositions(private val positions: MutableMap<String, Int>) {
    /** Returns a [Modifier] that records the composable's y-position under [key]. */
    fun modifierFor(key: String): Modifier =
        Modifier.onGloballyPositioned { coordinates ->
            positions[key] = coordinates.positionInRoot().y.toInt()
        }

    /** Scrolls the [scrollState] to the position registered for [key]. */
    suspend fun scrollToKey(key: String?, scrollState: androidx.compose.foundation.ScrollState) {
        if (key.isNullOrBlank()) return
        repeat(20) {
            val targetY = positions[key]
            if (targetY != null) {
                scrollState.animateScrollTo(value = targetY.coerceAtLeast(0))
                return
            }
            kotlinx.coroutines.delay(100L)
        }
    }
}
