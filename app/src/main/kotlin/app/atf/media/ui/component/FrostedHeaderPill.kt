/*
 * ArchiveTune (2026)
 * © ArchiveTuneFork contributors — github.com/vossgraves/ArchiveTune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package app.atf.media.ui.component

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
 * A frosted-glass-looking pill that wraps header content (title text, icon buttons) so the
 * header can be transparent while the content inside it stays legible against any background.
 *
 * The pill renders as a semi-transparent `surfaceContainer` surface. Earlier iterations tried to
 * composite a real backdrop blur over [LocalNavigationBarBackdrop], but the pill lives
 * INSIDE the NavHost content that is recorded into that same backdrop layer every frame
 * (`drawWithContent` in `MainActivity`), so drawing `backdrop.layer` from inside the pill is
 * re-entrant and crashes the app — both the S+ `drawLayer` path and the pre-S bitmap capture
 * read a layer that is currently being recorded. Degrading to a plain surface is the same
 * fallback those paths already used when no backdrop was available, and matches how the pill
 * behaves in rail layouts where the backdrop is null.
 *
 * The alpha is tuned low enough that the scrolling content shows through (so the header reads
 * as "transparent" rather than a solid bar) while still keeping the title/icons legible against
 * busy backgrounds like album art.
 *
 * Usage: wrap the title / actions of a `TopAppBar` (or any header) in this pill. The
 * outer `TopAppBar` should have `containerColor = Color.Transparent`.
 *
 * @param modifier Modifier for the pill's outer layout.
 * @param content The header content (text, icons) to display inside the pill.
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
