/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * A frosted-glass-looking pill that wraps header content (title text, icon buttons) so the header
 * can be transparent while the content inside it stays legible against any background.
 */
@Composable
fun FrostedHeaderPill(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val baseColor = MaterialTheme.colorScheme.surfaceContainer
    Surface(
        modifier = modifier.clip(RoundedCornerShape(percent = 50)),
        shape = RoundedCornerShape(percent = 50),
        color = baseColor.copy(alpha = 0.55f),
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            content()
        }
    }
}
