/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Tracks whether the app (specifically the activity window) currently keeps the system status bar
 * hidden — via the "Hide status bar" preference, an expanded immersive player, a bottom-sheet page,
 * and so on. Provided by MainActivity.
 *
 * Compose dialogs and Material3 bottom sheets create their own OS window, and system-bar
 * visibility follows the *focused* window. Without this, opening any dialog popup re-shows the
 * status bar for as long as the popup stays open (and the content behind it jumps down, because
 * the live status-bar inset goes from 0 to its real height). Dialogs consult this local through
 * [KeepStatusBarHiddenInDialog] to hide the bar on their own window, matching the activity.
 */
val LocalImmersiveStatusBarsHidden = compositionLocalOf { false }

/**
 * Hides the status bar on the hosting *dialog* window while the app itself keeps it hidden.
 *
 * Call once inside a `Dialog { ... }` / `ModalBottomSheet { ... }` content block. It resolves the
 * dialog's window through [DialogWindowProvider], and, when [LocalImmersiveStatusBarsHidden] is
 * true, hides the status bar on that window so the bar cannot flash back in while the popup is
 * focused. Safe no-op when the composable is not hosted in a dialog (e.g. previews or in-layout
 * overlays), or when the status bar is currently meant to be shown.
 */
@Composable
fun KeepStatusBarHiddenInDialog() {
    val hidden = LocalImmersiveStatusBarsHidden.current
    val view = LocalView.current
    DisposableEffect(view, hidden) {
        val dialogWindow = (view.parent as? DialogWindowProvider)?.window
        if (dialogWindow != null && hidden) {
            val controller =
                WindowCompat.getInsetsController(dialogWindow, dialogWindow.decorView)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.statusBars())
        }
        onDispose { }
    }
}
