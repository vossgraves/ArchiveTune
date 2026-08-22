/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp

val LocalMenuState = compositionLocalOf { MenuState() }

@Stable
class MenuState(
    isVisible: Boolean = false,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    var isVisible by mutableStateOf(isVisible)
    var content by mutableStateOf(content)
    internal var dialogContent by mutableStateOf<(@Composable () -> Unit)?>(null)
        private set

    @OptIn(ExperimentalMaterial3Api::class)
    fun show(content: @Composable ColumnScope.() -> Unit) {
        dialogContent = null
        isVisible = true
        this.content = content
    }

    @OptIn(ExperimentalMaterial3Api::class)
    fun dismiss() {
        isVisible = false
    }

    fun showDialog(content: @Composable () -> Unit) {
        isVisible = false
        dialogContent = content
    }

    fun dismissDialog() {
        dialogContent = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheetMenu(
    modifier: Modifier = Modifier,
    state: MenuState,
    background: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    val focusManager = LocalFocusManager.current

    state.dialogContent?.invoke()

    if (state.isVisible) {
        ModalBottomSheet(
            onDismissRequest = {
                focusManager.clearFocus()
                state.isVisible = false
            },
            // Match ProfileMenuDialog — 28dp top corners + surfaceContainerHigh
            // tonal container.
            shape = ProfilePopupDefaults.SheetShape,
            containerColor = background,
            contentColor = MaterialTheme.colorScheme.onSurface,
            dragHandle = {
                // Capsule-style drag handle — taller pill (32×4) with rounded
                // ends instead of a flat 2dp-cornered rectangle, matching the
                // rounded-capsule motif used throughout the redesigned popups.
                Box(
                    modifier =
                        Modifier
                            .padding(vertical = 12.dp)
                            .size(width = 32.dp, height = 4.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
                )
            },
            modifier = modifier.fillMaxHeight(),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth(),
                // NOTE: previously had `padding(horizontal = 20.dp)` here, which
                // left 20dp gutters on both sides of the menu content and made
                // the bottom sheet look like a narrow centered column. Removed
                // per user request — the menu now opens full-screen width by
                // default. The MenuSurfaceSection cards inside still have
                // their own rounded corners (MaterialTheme.shapes.extraLarge)
                // and look fine edge-to-edge.
            ) {
                state.content(this)
            }
        }
    }
}
