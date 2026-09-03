/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Surface colours for screens that can render over the Liquid Glass backdrop.
 *
 * Adapted from YumaPlayer's GlassScaffold (MuwMx/YumaPlayer, GPL-3.0, a fork of this app), with
 * the one change that matters here: that version is transparent unconditionally, so every screen
 * is glass and there is no Material 3 look left. These helpers are transparent ONLY while the
 * Liquid Glass preference is on and return the ordinary Material 3 surface otherwise — Material 3
 * stays the default and glass is the opt-in, which is the inverse of the fork's choice.
 *
 * Deliberately colour helpers rather than a Scaffold wrapper: every settings screen already builds
 * its own Scaffold with its own insets, top bar and scroll behaviour, so a wrapper would force each
 * one to be restructured to adopt glass. Two one-line substitutions per screen do the same job.
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import moe.rukamori.archivetune.constants.LiquidGlassEnabledKey
import moe.rukamori.archivetune.utils.rememberPreference

/** True while the user has opted into the Liquid Glass look. */
@Composable
fun rememberLiquidGlassEnabled(): Boolean {
    val enabled by rememberPreference(LiquidGlassEnabledKey, defaultValue = false)
    return enabled
}

/**
 * Container colour for a screen that sits over the glass backdrop: transparent when Liquid Glass
 * is on so the backdrop shows through, and the ordinary Material 3 surface when it is off.
 */
@Composable
fun glassAwareSurface(): Color =
    if (rememberLiquidGlassEnabled()) Color.Transparent else MaterialTheme.colorScheme.surface

/**
 * Large-top-app-bar colours to match [glassAwareSurface]. `scrolledContainerColor` is already
 * transparent in the Material 3 path here, so only the resting container colour changes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun glassAwareLargeTopAppBarColors(): TopAppBarColors =
    TopAppBarDefaults.largeTopAppBarColors(
        containerColor = glassAwareSurface(),
        scrolledContainerColor = Color.Transparent,
    )
