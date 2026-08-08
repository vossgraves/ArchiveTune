/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * JioSaavn integration settings. Ported from vivi-music
 * (https://github.com/vivizzz007/vivi-music) under GPL-3.0.
 *
 * Reached from Sources → JioSaavn → Open settings. Hosts the master toggle and
 * bitrate picker (96 / 160 / 320 kbps AAC). JioSaavn is unauthenticated: streams
 * come from the public JioSaavn API, with the encrypted_media_url decrypted
 * locally via DES-ECB.
 */

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.JioSaavnEnabledKey
import moe.rukamori.archivetune.constants.SaavnAudioQuality
import moe.rukamori.archivetune.constants.SaavnAudioQualityKey
import moe.rukamori.archivetune.ui.component.EnumListPreference
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JioSettings(navController: NavController) {
    val (saavnEnabled, onSaavnEnabledChange) = rememberPreference(JioSaavnEnabledKey, false)
    val (saavnQuality, onSaavnQualityChange) =
        rememberEnumPreference(SaavnAudioQualityKey, SaavnAudioQuality.QUALITY_320)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.jiosaavn_settings)) },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
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

        Column(
            Modifier
                .padding(top = topPadding)
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal))
                .verticalScroll(scrollState)
                .padding(bottom = playerAwareBottomPadding + 16.dp),
        ) {
            // Description block
            PreferenceGroup(title = stringResource(R.string.jiosaavn_integration)) {
                item {
                    Text(
                        text = stringResource(R.string.jiosaavn_beta_info),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.jiosaavn_enable)) },
                        description = stringResource(R.string.jiosaavn_enable_description),
                        icon = { Icon(painterResource(R.drawable.play), null) },
                        checked = saavnEnabled,
                        onCheckedChange = onSaavnEnabledChange,
                    )
                }

                item {
                    EnumListPreference(
                        title = { Text(stringResource(R.string.jiosaavn_audio_quality)) },
                        icon = { Icon(painterResource(R.drawable.play), null) },
                        selectedValue = saavnQuality,
                        onValueSelected = onSaavnQualityChange,
                        isEnabled = saavnEnabled,
                        valueText = { quality -> quality.toLabel() },
                    )
                }
            }

            // Attribution (required by vivi-music's GPL-3.0 porting guidelines).
            PreferenceGroup(title = stringResource(R.string.jiosaavn_credit_title)) {
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.jiosaavn_credit)) },
                        description = stringResource(R.string.jiosaavn_credit_description),
                        icon = { Icon(painterResource(R.drawable.info), null) },
                        onClick = {},
                    )
                }
            }
        }
    }
}
