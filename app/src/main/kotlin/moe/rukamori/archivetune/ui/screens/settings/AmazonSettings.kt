/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Amazon Music integration settings. Shaped like DeezerSettings — account-based, no self-hosted
 * proxy tier — reached from the Integration screen alongside Tidal/Qobuz/Deezer/Apple Music.
 *
 * IMPORTANT: this source cannot play audio yet. Amazon serves CENC-protected fragmented MP4, and
 * turning that into a decodable stream needs a decryption step this fork does not ship (see
 * AmazonEnabledKey's own comment in PreferenceKeys.kt). Signing in only gets metadata and catalogue
 * browsing, so the notice card below is not optional decoration — without it, a successful sign-in
 * here looks identical to a working source right up until the first play fails.
 */

package moe.rukamori.archivetune.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import moe.rukamori.archivetune.ui.component.SettingsTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AmazonAccountNameKey
import moe.rukamori.archivetune.constants.AmazonAccountPremiumKey
import moe.rukamori.archivetune.constants.AmazonAudioQuality
import moe.rukamori.archivetune.constants.AmazonAudioQualityKey
import moe.rukamori.archivetune.constants.AmazonInstancesKey
import moe.rukamori.archivetune.constants.AmazonSessionKey
import moe.rukamori.archivetune.ui.component.EnumListPreference
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.component.TextFieldDialog
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmazonSettings(
    navController: NavController,
    scrollTo: String? = null,
) {
    val context = LocalContext.current

    val (accountName, onAccountNameChange) = rememberPreference(AmazonAccountNameKey, "")
    val (_, onSessionChange) = rememberPreference(AmazonSessionKey, "")
    val (isPremium, onPremiumChange) = rememberPreference(AmazonAccountPremiumKey, false)
    val (audioQuality, onAudioQualityChange) =
        rememberEnumPreference(AmazonAudioQualityKey, AmazonAudioQuality.Default)
    val (storedInstances, onStoredInstancesChange) = rememberPreference(AmazonInstancesKey, "")

    val signedIn = accountName.isNotEmpty()
    val instanceCount =
        remember(storedInstances) {
            storedInstances.split('\n').map { it.trim() }.count { it.isNotEmpty() }
        }

    var showInstancesDialog by remember { mutableStateOf(false) }

    if (showInstancesDialog) {
        TextFieldDialog(
            icon = { Icon(painterResource(R.drawable.link), null) },
            title = { Text(stringResource(R.string.amazon_instances)) },
            placeholder = { Text(stringResource(R.string.amazon_instances_hint)) },
            initialTextFieldValue =
                TextFieldValue(storedInstances, selection = TextRange(storedInstances.length)),
            singleLine = false,
            isInputValid = { true },
            onDone = { raw ->
                onStoredInstancesChange(
                    raw
                        .split('\n')
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .distinct()
                        .joinToString("\n"),
                )
            },
            onDismiss = { showInstancesDialog = false },
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            SettingsTopAppBar(
                title = { Text(stringResource(R.string.source_amazon)) },
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
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal))
                // Chained before verticalScroll so it measures the viewport, not the scrolling content.
                .then(positions.containerModifier())
                .verticalScroll(scrollState)
                .padding(bottom = playerAwareBottomPadding + 16.dp),
        ) {
            AmazonPlaybackNoticeCard(
                modifier =
                    Modifier
                        .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding)
                        .padding(top = 12.dp, bottom = 4.dp),
            )

            PreferenceGroup(
                title = stringResource(R.string.source_amazon),
            ) {
                if (!signedIn) {
                    item {
                        PreferenceEntry(
                            modifier = positions.modifierFor("amazon_login"),
                            title = { Text(stringResource(R.string.amazon_login)) },
                            description = stringResource(R.string.amazon_login_description),
                            icon = { Icon(painterResource(R.drawable.login), null) },
                            onClick = { navController.navigate(AMAZON_LOGIN_ROUTE) },
                        )
                    }
                } else {
                    item {
                        PreferenceEntry(
                            modifier = positions.modifierFor("amazon_sign_out"),
                            title = { Text(stringResource(R.string.amazon_sign_out)) },
                            description = stringResource(R.string.amazon_signed_in_as, accountName),
                            icon = { Icon(painterResource(R.drawable.logout), null) },
                            onClick = {
                                // Clearing the session is what actually signs out; a collector
                                // elsewhere observes it and drops the provider's session. Name and
                                // the premium flag are display/ordering state only. AmazonEnabledKey
                                // is left alone, mirroring Deezer — a pool account can keep the
                                // source usable even after a personal sign-in ends.
                                onSessionChange("")
                                onAccountNameChange("")
                                onPremiumChange(false)
                                Toast
                                    .makeText(context, R.string.amazon_signed_out, Toast.LENGTH_SHORT)
                                    .show()
                            },
                        )
                    }
                }
            }

            PreferenceGroup(
                title = stringResource(R.string.amazon_settings_playback_group),
            ) {
                item {
                    SwitchPreference(
                        modifier = positions.modifierFor("amazon_premium"),
                        title = { Text(stringResource(R.string.amazon_premium_toggle)) },
                        description = stringResource(R.string.amazon_premium_toggle_description),
                        icon = { Icon(painterResource(R.drawable.star), null) },
                        checked = isPremium,
                        onCheckedChange = onPremiumChange,
                        isEnabled = signedIn,
                    )
                }

                item {
                    EnumListPreference(
                        modifier = positions.modifierFor("amazon_audio_quality"),
                        title = { Text(stringResource(R.string.amazon_audio_quality)) },
                        icon = { Icon(painterResource(R.drawable.graphic_eq), null) },
                        selectedValue = audioQuality,
                        onValueSelected = onAudioQualityChange,
                        isEnabled = signedIn,
                        valueText = { quality ->
                            when (quality) {
                                AmazonAudioQuality.ULTRA_HD -> stringResource(R.string.amazon_quality_ultra_hd)
                                AmazonAudioQuality.HD -> stringResource(R.string.amazon_quality_hd)
                                AmazonAudioQuality.STANDARD -> stringResource(R.string.amazon_quality_standard)
                            }
                        },
                    )
                }
            }

            PreferenceGroup(
                title = stringResource(R.string.amazon_instances),
            ) {
                item {
                    PreferenceEntry(
                        modifier = positions.modifierFor("amazon_instances"),
                        title = { Text(stringResource(R.string.amazon_instances)) },
                        description =
                            if (instanceCount > 0) {
                                stringResource(R.string.amazon_instances_count, instanceCount)
                            } else {
                                stringResource(R.string.amazon_instances_empty)
                            },
                        icon = { Icon(painterResource(R.drawable.link), null) },
                        onClick = { showInstancesDialog = true },
                    )
                }
            }
        }
    }
}

/**
 * The mandatory "this doesn't play yet" notice. Uses the error container rather than a neutral one
 * — this is a hard limitation, not a tip, and it needs to read as one at a glance from the top of
 * the screen before anyone taps sign-in expecting a working source.
 */
@Composable
private fun AmazonPlaybackNoticeCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(SettingsDimensions.BannerCardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(SettingsDimensions.BannerIconSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.lock),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(SettingsDimensions.BannerIconInnerSize),
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.amazon_playback_notice_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = stringResource(R.string.amazon_playback_notice_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                )
            }
        }
    }
}
