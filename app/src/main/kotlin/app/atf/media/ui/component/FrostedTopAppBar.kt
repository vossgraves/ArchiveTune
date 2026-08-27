/*
 * ArchiveTune (2026)
 * © ArchiveTuneFork contributors — github.com/vossgraves/ArchiveTune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package app.atf.media.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.atf.media.R

/**
 * A [TopAppBar] variant with a fully transparent container and the title / navigation icon /
 * actions each wrapped in a [FrostedHeaderPill] so the header content stays legible against
 * any scrolling background (album art, gradient, etc.) without needing a solid bar.
 *
 * The pills use the shared [LocalNavigationBarBackdrop] GraphicsLayer for real backdrop blur
 * on Android 12+, and degrade to a semi-transparent `surfaceContainer` on pre-S or when no
 * backdrop is available.
 *
 * Usage: drop-in replacement for a standard `TopAppBar` that has a title, a back arrow,
 * and optional actions. For screens that need a more custom title (e.g. with an avatar or
 * animated content), use [FrostedHeaderPill] directly.
 */

/** Primary variant: takes a string resource for the title. */
@Composable
fun FrostedTopAppBar(
    titleRes: Int,
    onBack: () -> Unit,
    onBackLongClick: () -> Unit = {},
    actions: (@Composable () -> Unit)? = null,
) {
    FrostedTopAppBar(
        title = { Text(stringResource(titleRes)) },
        onBack = onBack,
        onBackLongClick = onBackLongClick,
        actions = actions,
    )
}

/** Flexible variant: takes a composable title (for custom title content). */
@Composable
fun FrostedTopAppBar(
    title: @Composable () -> Unit,
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
                title()
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

/**
 * Large variant: wraps a [LargeFlexibleTopAppBar] with frosted pills around the title, nav icon,
 * and actions. Use for screens that have a hero header which collapses on scroll.
 */
@Composable
fun LargeFrostedTopAppBar(
    titleRes: Int,
    onBack: () -> Unit,
    onBackLongClick: () -> Unit = {},
    actions: (@Composable () -> Unit)? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    LargeFlexibleTopAppBar(
        colors = TopAppBarDefaults.largeTopAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
        ),
        title = {
            FrostedHeaderPill {
                Text(
                    text = stringResource(titleRes),
                    fontWeight = FontWeight.Bold,
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
        scrollBehavior = scrollBehavior,
    )
}

/** Large variant with composable title (for custom title content). */
@Composable
fun LargeFrostedTopAppBar(
    title: @Composable () -> Unit,
    onBack: () -> Unit,
    onBackLongClick: () -> Unit = {},
    actions: (@Composable () -> Unit)? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    LargeFlexibleTopAppBar(
        colors = TopAppBarDefaults.largeTopAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
        ),
        title = {
            FrostedHeaderPill {
                title()
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
        scrollBehavior = scrollBehavior,
    )
}
