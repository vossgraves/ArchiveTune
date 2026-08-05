/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R

/**
 * A [TopAppBar] variant with a fully transparent container and the title / navigation icon /
 * actions each wrapped in a [FrostedHeaderPill] so the header content stays legible against
 * any scrolling background (album art, gradient, etc.) without needing a solid bar.
 *
 * The pills use the shared [LocalNavigationBarBackdrop] GraphicsLayer for real backdrop blur
 * on Android 12+, and degrade to a semi-transparent `surfaceContainer` on pre-S or when no
 * backdrop is available.
 *
 * Usage: drop-in replacement for a standard `TopAppBar` that has a title string, a back
 * arrow, and optional actions. For screens that need a more custom title (e.g. with an
 * avatar or animated content), use [FrostedHeaderPill] directly.
 *
 * @param titleRes String resource for the title.
 * @param onBack Click handler for the back arrow.
 * @param onBackLongClick Optional long-click handler for the back arrow (typically
 *   `navController::backToMain`).
 * @param actions Optional composable for action buttons. Rendered inside a frosted pill.
 */
@Composable
fun FrostedTopAppBar(
    titleRes: Int,
    onBack: () -> Unit,
    onBackLongClick: () -> Unit = {},
    actions: (@Composable () -> Unit)? = null,
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        title = {
            FrostedHeaderPill {
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                )
            }
        },
        navigationIcon = {
            FrostedHeaderPill {
                IconButton(
                    onClick = onBack,
                    onLongClick = onBackLongClick,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_back),
                        contentDescription = null,
                    )
                }
            }
        },
        actions = if (actions != null) {
            {
                FrostedHeaderPill(
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    actions()
                }
            }
        } else {
            {}
        },
    )
}
