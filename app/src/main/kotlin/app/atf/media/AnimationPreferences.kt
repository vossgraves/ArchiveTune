/*
 * ArchiveTune (2026)
 * © ArchiveTuneFork contributors — github.com/vossgraves/ArchiveTune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package app.atf.media

import androidx.compose.runtime.compositionLocalOf

val LocalAnimationsDisabled = compositionLocalOf { false }

/**
 * App-wide scrollbar toggle (Task 8). When `true`, all LazyColumn / LazyGrid /
 * ScrollState scrollbars in the app are suppressed. Provided by MainActivity
 * from the [HideScrollbarKey] preference; consumed by the scrollbar modifier
 * extension in [app.atf.media.ui.utils.ScrollUtils].
 */
val LocalHideScrollbar = compositionLocalOf { false }
