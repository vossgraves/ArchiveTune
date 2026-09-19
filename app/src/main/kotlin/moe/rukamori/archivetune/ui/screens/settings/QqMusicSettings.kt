/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * QQ Music integration settings, reached from the Integration screen alongside the other sources.
 *
 * The notice card below is not decoration: QQ Music has no public personal-developer playback API,
 * so this source only works for a build that carries Tencent partner credentials, and a build that
 * does not would otherwise look identical to a broken one. What the source never does — scraping the
 * web endpoints, minting vkeys, decrypting `mflac`/`mgg`, or working around the provider's own
 * quality locks — is spelled out in QqMusicProvider and in the notice, so nobody has to guess why a
 * track fell through.
 */

package moe.rukamori.archivetune.ui.screens.settings

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.audiosource.AudioSourceConfig
import moe.rukamori.archivetune.constants.AudioSourceType
import moe.rukamori.archivetune.constants.AudioSourceOrderKey
import moe.rukamori.archivetune.constants.QqAudioQuality
import moe.rukamori.archivetune.constants.QqMusicAudioQualityKey
import moe.rukamori.archivetune.constants.QqMusicEnabledKey
import moe.rukamori.archivetune.qqmusic.QqMusicProvider
import moe.rukamori.archivetune.ui.component.EnumListPreference
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SettingsTopAppBar
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QqMusicSettings(
    navController: NavController,
    scrollTo: String? = null,
) {
    val (enabled, onEnabledChangeRaw) = rememberPreference(QqMusicEnabledKey, false)
    val (quality, onQualityChange) =
        rememberEnumPreference(QqMusicAudioQualityKey, QqAudioQuality.Default)
    val (orderRaw, onOrderChange) = rememberPreference(AudioSourceOrderKey, "")

    // Turning the source on has to write it into the order as well. QQ is deliberately not a member
    // of AudioSourceConfig.DEFAULT_ORDER, and the order picker can only reorder what it is given, so
    // without this the toggle would flip a preference the resolver never reads and playback would
    // stay on YouTube with no indication why.
    val onEnabledChange: (Boolean) -> Unit = { next ->
        onEnabledChangeRaw(next)
        if (next) {
            onOrderChange(AudioSourceConfig.withSourceAdded(orderRaw, AudioSourceType.QQ))
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            SettingsTopAppBar(
                title = { Text(stringResource(R.string.source_qq_music)) },
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
                .then(positions.containerModifier())
                .verticalScroll(scrollState)
                .padding(bottom = playerAwareBottomPadding + 16.dp),
        ) {
            QqMusicNoticeCard(
                modifier =
                    Modifier
                        .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding)
                        .padding(top = 12.dp, bottom = 4.dp),
            )

            PreferenceGroup(title = stringResource(R.string.source_qq_music)) {
                item {
                    SwitchPreference(
                        modifier = positions.modifierFor("qq_music_enabled"),
                        title = { Text(stringResource(R.string.qq_music_enabled)) },
                        description = stringResource(R.string.qq_music_enabled_desc),
                        icon = { Icon(painterResource(R.drawable.ic_music), null) },
                        checked = enabled,
                        onCheckedChange = onEnabledChange,
                    )
                }

                item {
                    EnumListPreference(
                        modifier = positions.modifierFor("qq_music_quality"),
                        title = { Text(stringResource(R.string.qq_music_quality)) },
                        icon = { Icon(painterResource(R.drawable.equalizer), null) },
                        selectedValue = quality,
                        valueText = {
                            when (it) {
                                QqAudioQuality.LOSSLESS -> stringResource(R.string.qq_quality_lossless)
                                QqAudioQuality.HIGH -> stringResource(R.string.qq_quality_high)
                                QqAudioQuality.STANDARD -> stringResource(R.string.qq_quality_standard)
                            }
                        },
                        onValueSelected = onQualityChange,
                    )
                }

                item {
                    PreferenceEntry(
                        modifier = positions.modifierFor("qq_music_credentials"),
                        title = {
                            Text(
                                stringResource(
                                    if (QqMusicProvider.isConfigured()) {
                                        R.string.qq_music_credentials_ready
                                    } else {
                                        R.string.qq_music_credentials_missing
                                    },
                                ),
                            )
                        },
                        icon = { Icon(painterResource(R.drawable.lock), null) },
                        onClick = {},
                    )
                }
            }
        }
    }
}

/**
 * The mandatory "this needs a partnership" notice. Uses the error container rather than a neutral
 * one: without partner credentials the source cannot resolve anything at all, which is a hard
 * limitation rather than a tip.
 */
@Composable
private fun QqMusicNoticeCard(modifier: Modifier = Modifier) {
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
                    text = stringResource(R.string.qq_music_notice_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = stringResource(R.string.qq_music_notice_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                )
            }
        }
    }
}