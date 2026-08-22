/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Canonical design tokens for ArchiveTune popups, derived from the
 * `ProfileMenuDialog` reference. Use these to keep every dialog / popup
 * / sheet across the app visually consistent (md3e "expressive" surface
 * with extra-large 28dp rounded corners + rounded capsule rows for
 * selectable items).
 */
object ProfilePopupDefaults {
    /** Extra-large rounded-corner shape for the outer popup container. */
    val ContainerShape = RoundedCornerShape(28.dp)

    /** Top-only 28dp corners for sheets that dock to the bottom of the screen. */
    val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    /** Surface used as the popup's container color. */
    @Composable
    fun containerColor(): Color = MaterialTheme.colorScheme.surfaceContainerHigh

    /** Slightly higher tonal surface used as the background of capsule rows / dismiss button. */
    @Composable
    fun itemBackground(): Color =
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)

    @Composable
    fun dismissButtonBackground(): Color = MaterialTheme.colorScheme.surfaceContainerHighest

    val TonalElevationDp = 6.dp
    val ShadowElevationDp = 12.dp
    val MaxWidth = 360.dp
    val MaxWidthWide = 560.dp
    val DismissButtonSize = 32.dp
    val DismissIconSize = 18.dp
    val ItemIconSize = 22.dp
    val ItemSpacing = 12.dp
    val ItemPaddingHorizontal = 14.dp
    val ItemPaddingVertical = 10.dp

    /** Capsule shape used for menu item rows (matches ProfileMenuDialog). */
    val ItemShape = RoundedCornerShape(percent = 50)
}

/**
 * A rounded-capsule menu row, identical in styling to the items used by
 * `ProfileMenuDialog`. Use this in any popup that lists selectable actions
 * so the popup matches the reference profile-popup look.
 *
 * Callers should pass an [icon] composable sized at 22.dp (see
 * [ProfilePopupDefaults.ItemIconSize]).
 */
@Composable
fun ProfilePopupItemRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ProfilePopupDefaults.ItemSpacing),
        modifier = modifier
            .fillMaxWidth()
            .clip(ProfilePopupDefaults.ItemShape)
            .background(ProfilePopupDefaults.itemBackground())
            .clickable { onClick() }
            .padding(
                horizontal = ProfilePopupDefaults.ItemPaddingHorizontal,
                vertical = ProfilePopupDefaults.ItemPaddingVertical,
            ),
    ) {
        if (icon != null) {
            icon()
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            trailing()
        }
    }
}
