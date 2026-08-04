/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.runtime.Composable
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
    val children: List<SettingsChild> = emptyList(),
    val onClick: () -> Unit,
    val switchControl: (@Composable () -> Unit)? = null,
    /**
     * When true, the item is excluded from the visible groups on the main settings page
     * (so no row is rendered for it) but its [children] still participate in settings
     * search. Use this when a pill has been moved into a sub-page (e.g. "Source" moved
     * into Playback) but the original search index entries should keep working — tapping
     * a child search result still navigates to the sub-page via [onClick].
     */
    val hidden: Boolean = false,
)

/**
 * Represents a single searchable setting inside a settings category.
 * When the user searches settings, each [SettingsChild] that matches is
 * shown as a separate result row and may provide an inline control such as
 * a switch for boolean preferences.
 */
@Immutable
data class SettingsChild(
    val title: String,
    val scrollKey: String,
    val keywords: List<String> = emptyList(),
    val switchControl: (@Composable () -> Unit)? = null,
)

/**
 * A flattened search result derived from a [SettingsChild].
 * Shown as an individual row in the search results list.
 */
@Immutable
data class SearchResultItem(
    val title: String,
    val parentTitle: String,
    val parentIcon: Painter,
    val parentKey: String,
    val parentAccentColor: Color = Color.Unspecified,
    val parentRoute: String?,
    val scrollKey: String?,
    val onClick: () -> Unit,
    val switchControl: (@Composable () -> Unit)? = null,
)
