/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Deezer integration settings. Reached from the Integration screen alongside
 * Tidal/Qobuz. Hosts the entry point to the Deezer login flow, which captures
 * an `arl` cookie so the provider can resolve full Premium streams.
 */

package moe.rukamori.archivetune.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.DeezerAccountNameKey
import moe.rukamori.archivetune.constants.DeezerAccountPremiumKey
import moe.rukamori.archivetune.constants.DeezerArlKey
import moe.rukamori.archivetune.constants.DeezerProxyMode
import moe.rukamori.archivetune.constants.DeezerProxyModeKey
import moe.rukamori.archivetune.ui.component.EnumListPreference
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import androidx.compose.foundation.layout.asPaddingValues

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeezerSettings(
    navController: NavController,
    scrollTo: String? = null,
) {
    val context = LocalContext.current

    val (accountName, onAccountNameChange) = rememberPreference(DeezerAccountNameKey, "")
    val (_, onArlChange) = rememberPreference(DeezerArlKey, "")
    val (_, onPremiumChange) = rememberPreference(DeezerAccountPremiumKey, false)
    val (proxyMode, onProxyModeChange) = rememberEnumPreference(DeezerProxyModeKey, DeezerProxyMode.AUTO)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.deezer_integration)) },
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
        androidx.compose.runtime.LaunchedEffect(scrollTo) { positions.scrollToKey(scrollTo, scrollState) }

        Column(
            Modifier
                .padding(top = topPadding)
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal))
                // Chained before verticalScroll so it measures the viewport, not the scrolling content.
                .then(positions.containerModifier())
                .verticalScroll(scrollState)
                .padding(bottom = playerAwareBottomPadding + 16.dp),
        ) {
            PreferenceGroup(
                title = stringResource(R.string.deezer_integration),
            ) {
                if (accountName.isEmpty()) {
                    item {
                        PreferenceEntry(
                            modifier = positions.modifierFor("deezer_login"),
                            title = { Text(stringResource(R.string.deezer_login)) },
                            description = stringResource(R.string.deezer_login_description),
                            icon = { Icon(painterResource(R.drawable.provider_deezer), null) },
                            onClick = { navController.navigate(DEEZER_LOGIN_ROUTE) },
                        )
                    }
                } else {
                    item {
                        PreferenceEntry(
                            modifier = positions.modifierFor("deezer_sign_out"),
                            title = { Text(stringResource(R.string.deezer_sign_out)) },
                            description = stringResource(R.string.deezer_signed_in_as, accountName),
                            icon = { Icon(painterResource(R.drawable.logout), null) },
                            onClick = {
                                // Clearing the ARL is what actually signs out; App.kt's collector observes it
                                // and drops the provider's session. Name/premium are display state only.
                                onArlChange("")
                                onAccountNameChange("")
                                onPremiumChange(false)
                                Toast
                                    .makeText(context, R.string.deezer_signed_out, Toast.LENGTH_SHORT)
                                    .show()
                            },
                        )
                    }
                }
            }

            PreferenceGroup(
                title = stringResource(R.string.deezer_network_settings),
            ) {
                item {
                    EnumListPreference(
                        modifier = positions.modifierFor("deezer_proxy_mode"),
                        title = { Text(stringResource(R.string.deezer_proxy_mode)) },
                        description = stringResource(R.string.deezer_proxy_mode_description),
                        icon = { Icon(painterResource(R.drawable.wifi_proxy), null) },
                        selectedValue = proxyMode,
                        onValueSelected = onProxyModeChange,
                        valueText = { it.displayName },
                    )
                }
            }
        }
    }
}
