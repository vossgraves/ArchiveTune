/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Top-level "Sources" settings screen. Hosts independent catalog-source selectors (metadata and
 * search) plus the streaming-source preference groups (priority, toggles and quality). Manual
 * account/instance sign-in still lives in Integration (gated).
 */

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.tidal.TidalInstanceHealthManager
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.TextFieldDialog
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.PoolAccountManager
import moe.rukamori.archivetune.utils.rememberPreference
import androidx.compose.foundation.layout.asPaddingValues

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceSettings(navController: NavController, scrollTo: String? = null) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.source_settings)) },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(
                            painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        val playerAwareBottomPadding =
            LocalPlayerAwareWindowInsets.current
                .only(WindowInsetsSides.Bottom)
                .asPaddingValues()
                .calculateBottomPadding()
        val topPadding = innerPadding.calculateTopPadding()
        val scrollState = rememberScrollState()
        val positions = rememberPreferencePositions()

        LaunchedEffect(scrollTo) { positions.scrollToKey(scrollTo, scrollState) }

        Column(
            Modifier
                .padding(top = topPadding)
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Horizontal,
                    ),
                )
                // Chained before verticalScroll so it measures the viewport, not the scrolling content.
                .then(positions.containerModifier())
                .verticalScroll(scrollState)
                .padding(bottom = playerAwareBottomPadding + SettingsDimensions.ScreenBottomPadding),
        ) {
            // Manual "refresh from pool" — pulls the latest shared accounts and instances on demand
            // (the app also does this automatically on startup). Only shown when a source pool is
            // configured at build time.
            PoolRefreshSection(positions)

            // Preferred-source picker, per-source enable toggles and quality. Account/instance
            // management remains in Integration (behind the manual-source-login toggle).
            PlaybackSourceSections(
                navController = navController,
                positions = positions,
            )
        }
    }
}

/**
 * Top-of-screen action that force-refreshes the shared source pool: re-fetches contributed
 * accounts (via [PoolAccountManager]) and re-discovers verified Tidal instances (via
 * [TidalInstanceHealthManager]), bypassing the normal throttle. Hidden entirely when no source
 * pool URL is baked in, since there is nothing to refresh.
 */
@Composable
private fun PoolRefreshSection(positions: PreferencePositions) {
    if (!PoolAccountManager.isEnabled) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }

    PreferenceGroup(
        modifier = positions.modifierFor("youtube_music"),
    ) {
        item {
            PreferenceEntry(
                title = {
                    Text(
                        if (refreshing) {
                            stringResource(R.string.pool_refreshing)
                        } else {
                            stringResource(R.string.pool_refresh_title)
                        },
                    )
                },
                icon = { Icon(painterResource(R.drawable.sync), null) },
                trailingContent =
                    if (refreshing) {
                        { CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) }
                    } else {
                        null
                    },
                isEnabled = !refreshing,
                onClick = {
                    if (refreshing) return@PreferenceEntry
                    refreshing = true
                    scope.launch {
                        val ok =
                            withContext(Dispatchers.IO) {
                                // Force past the 6h throttle so the tap always hits the network.
                                val accountsOk = PoolAccountManager.refresh(context, force = true)
                                // Re-discover + re-verify community Tidal instances from the pool feed.
                                runCatching {
                                    TidalInstanceHealthManager.refresh(
                                        context,
                                        includeDiscovery = true,
                                        staggered = false,
                                    )
                                }
                                accountsOk
                            }
                        val message =
                            if (ok) {
                                context.getString(
                                    R.string.pool_refresh_done,
                                    PoolAccountManager.tidalAccounts().size,
                                    PoolAccountManager.qobuzAccounts().size,
                                    PoolAccountManager.deezerAccounts().size,
                                )
                            } else {
                                context.getString(R.string.pool_refresh_failed)
                            }
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        refreshing = false
                    }
                },
            )
        }

    }
}
