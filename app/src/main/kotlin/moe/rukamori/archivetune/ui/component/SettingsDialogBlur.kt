/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Tracks whether any settings dialog is currently showing, so the parent SettingsScreen (and other
 * opt-in screens) can apply a backdrop blur behind the dialog for the Material 3 Expressive
 * "frosted glass" look.
 */
val LocalSettingsDialogShowing: ProvidableCompositionLocal<MutableState<Boolean>> =
    compositionLocalOf { mutableStateOf(false) }

/**
 * Convenience helper that creates a fresh [MutableState] for tracking dialog visibility. The
 * SettingsScreen (or any other screen that opts into backdrop-blur) should call this once and
 * provide the returned state to its subtree via [LocalSettingsDialogShowing].
 */
@Composable
fun rememberSettingsDialogHostState(): MutableState<Boolean> = remember { mutableStateOf(false) }
