/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The block at the top of a bottom-sheet menu that says what the menu is about — the track, the
 * playlist, the album.
 *
 * Six menus had written the same `Surface(RoundedCornerShape(28.dp), surfaceContainerLow)` by hand,
 * which is six places to change whenever the menus are restyled and six chances to miss one. It is
 * one composable now, which is also what makes the Apple Music header below cost a branch instead
 * of six edits.
 */
@Composable
fun MenuHeaderCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // Apple Music's action sheets do not put the track on a raised card. The header sits directly
    // on the sheet with a hairline under it, and the actions read as a plain list below — so under
    // the Apple Music Experience the card is simply not drawn.
    if (rememberAppleMusicExperience()) {
        Column(modifier = modifier.fillMaxWidth()) {
            content()
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        return
    }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
        content = { content() },
    )
}
