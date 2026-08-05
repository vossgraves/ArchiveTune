/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune

import androidx.compose.runtime.compositionLocalOf

val LocalAnimationsDisabled = compositionLocalOf { false }

/**
 * App-wide scrollbar toggle (Task 8). When `true`, all LazyColumn / LazyGrid /
 * ScrollState scrollbars in the app are suppressed. Provided by MainActivity
 * from the [HideScrollbarKey] preference; consumed by the scrollbar modifier
 * extension in [moe.rukamori.archivetune.ui.utils.ScrollUtils].
 */
val LocalHideScrollbar = compositionLocalOf { false }
