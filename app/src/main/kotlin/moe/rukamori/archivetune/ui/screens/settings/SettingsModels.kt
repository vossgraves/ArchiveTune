/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter

@Immutable
data class SettingsProfileState(
    val isLoading: Boolean,
    val isLoggedIn: Boolean,
    val accountName: String,
    val accountEmail: String,
    val accountImageUrl: String?,
)

@Immutable
data class SettingsGroup(
    val title: String,
    val items: List<SettingsItem>,
    val showWhenFiltered: Boolean = true,
)

@Immutable
data class SettingsItem(
    val key: String,
    val icon: Painter,
    val title: String,
    val subtitle: String? = null,
    val badge: String? = null,
    val showUpdateIndicator: Boolean = false,
    val accentColor: Color = Color.Unspecified,
    val keywords: List<String> = emptyList(),
    /**
     * True for entries that point at an individual preference inside a sub-screen rather than at
     * the sub-screen itself. These are only offered while searching: listing them on the resting
     * settings list would bury the top-level categories under dozens of near-duplicate rows.
     */
    val deepOnly: Boolean = false,
    val onClick: () -> Unit,
)
