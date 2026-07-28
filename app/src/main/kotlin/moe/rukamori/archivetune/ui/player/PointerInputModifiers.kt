/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Reports every touch that lands anywhere inside this node — including on scrolling children — and
 * never consumes anything, so the children behave exactly as if this were not here.
 *
 * Apply this to a PARENT, not to an overlay sibling. A full-screen sibling stacked on top wins hit
 * testing and starves the siblings beneath it even when it consumes nothing, which would stop the
 * lyrics list from scrolling. A parent is in the hit path of all of its children, and the Initial
 * pass runs before children see the event, so this observes without ever competing for the gesture.
 */
@Composable
internal fun Modifier.observeAnyPointerDown(
    enabled: Boolean,
    onDown: () -> Unit,
): Modifier {
    // pointerInput is keyed on Unit so a fresh lambda from the caller cannot restart the handler on
    // every recomposition; rememberUpdatedState keeps the callback current without being a key.
    // Toggling `enabled` is handled by swapping the modifier branch below, which tears the handler
    // down and rebuilds it, so the key does not need to track it.
    val currentOnDown by rememberUpdatedState(onDown)
    return if (!enabled) {
        this
    } else {
        pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.any { it.changedToDownIgnoreConsumed() }) {
                        currentOnDown()
                    }
                }
            }
        }
    }
}

internal fun Modifier.consumeUnhandledPointerInput(): Modifier =
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                event.changes.forEach { pointerInputChange ->
                    if (!pointerInputChange.isConsumed) {
                        pointerInputChange.consume()
                    }
                }
            }
        }
    }
