/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AutoDownloadOnLikeKey
import moe.rukamori.archivetune.constants.DownloadSource
import moe.rukamori.archivetune.constants.DownloadSourceConfig
import moe.rukamori.archivetune.constants.DownloadSourceOrderKey
import moe.rukamori.archivetune.constants.ExternalDownloaderEnabledKey
import moe.rukamori.archivetune.constants.ExternalDownloaderPackageKey
import moe.rukamori.archivetune.ui.component.ActionPromptDialog
import moe.rukamori.archivetune.ui.component.DefaultDialog
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.component.TextFieldDialog
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.PoolAccountManager
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.StorageSettingsViewModel
import androidx.compose.foundation.layout.asPaddingValues
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun DownloadsSettings(
    navController: NavController,
    scrollTo: String? = null,
    viewModel: StorageSettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val (autoDownloadOnLike, onAutoDownloadOnLikeChange) =
        rememberPreference(AutoDownloadOnLikeKey, defaultValue = false)
    val (externalDownloaderEnabled, onExternalDownloaderEnabledChange) =
        rememberPreference(ExternalDownloaderEnabledKey, defaultValue = false)
    val (externalDownloaderPackage, onExternalDownloaderPackageChange) =
        rememberPreference(ExternalDownloaderPackageKey, defaultValue = "")

    var showExternalDownloaderPackageDialog by remember { mutableStateOf(false) }
    var clearDownloads by remember { mutableStateOf(false) }

    if (showExternalDownloaderPackageDialog) {
        TextFieldDialog(
            initialTextFieldValue =
                androidx.compose.ui.text.input
                    .TextFieldValue(externalDownloaderPackage),
            onDone = { pkg ->
                onExternalDownloaderPackageChange(pkg)
                showExternalDownloaderPackageDialog = false
            },
            onDismiss = { showExternalDownloaderPackageDialog = false },
            singleLine = true,
            maxLines = 1,
        )
    }

    if (clearDownloads) {
        ActionPromptDialog(
            title = stringResource(R.string.clear_all_downloads),
            onDismiss = { clearDownloads = false },
            onConfirm = {
                viewModel.clearDownloads()
                clearDownloads = false
            },
            onCancel = { clearDownloads = false },
            content = {
                Text(text = stringResource(R.string.clear_downloads_dialog))
            },
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.downloads)) },
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
                .padding(bottom = playerAwareBottomPadding + SettingsDimensions.ScreenBottomPadding),
        ) {
            PreferenceGroup(
                modifier = positions.modifierFor("downloaded_songs"),
                title = stringResource(R.string.downloaded_songs),
            ) {
                item {
                    PreferenceEntry(
                        modifier = positions.modifierFor("clear_all_downloads"),
                        title = { Text(stringResource(R.string.clear_all_downloads)) },
                        description = stringResource(R.string.clear_downloads_dialog),
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_download),
                                contentDescription = null,
                            )
                        },
                        onClick = { clearDownloads = true },
                    )
                }
                item {
                    PreferenceEntry(
                        modifier = positions.modifierFor("export_downloaded_songs"),
                        title = { Text(stringResource(R.string.export_downloaded_songs)) },
                        description = stringResource(R.string.export_downloaded_songs_description),
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.send),
                                contentDescription = null,
                            )
                        },
                        onClick = { navController.navigate("settings/storage/export_songs") },
                    )
                }
            }

            PreferenceGroup(
                modifier = positions.modifierFor("auto_download_like"),
                title = stringResource(R.string.downloads),
            ) {
                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.auto_download_on_like)) },
                        description = stringResource(R.string.auto_download_on_like_desc),
                        icon = { Icon(painterResource(R.drawable.download), null) },
                        checked = autoDownloadOnLike,
                        onCheckedChange = onAutoDownloadOnLikeChange,
                    )
                }

                item {
                    PreferenceEntry(
                        modifier = positions.modifierFor("download_source"),
                        title = { Text(stringResource(R.string.manage_source_priority)) },
                        description = stringResource(R.string.sources_priority_footer),
                        icon = { Icon(painterResource(R.drawable.tune), null) },
                        onClick = { navController.navigate("settings/sources?scrollTo=preferred_sources") },
                    )
                }
            }

            PreferenceGroup(
                modifier = positions.modifierFor("external_downloader"),
                title = stringResource(R.string.external_downloader),
            ) {
                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.external_downloader)) },
                        description = stringResource(R.string.external_downloader_desc),
                        icon = { Icon(painterResource(R.drawable.download), null) },
                        checked = externalDownloaderEnabled,
                        onCheckedChange = onExternalDownloaderEnabledChange,
                    )
                }

                item {
                    PreferenceEntry(
                        modifier = positions.modifierFor("external_downloader_package"),
                        title = { Text(stringResource(R.string.external_downloader_package)) },
                        description = externalDownloaderPackage.ifEmpty { stringResource(R.string.external_downloader_package_desc) },
                        icon = { Icon(painterResource(R.drawable.integration), null) },
                        onClick = { showExternalDownloaderPackageDialog = true },
                        isEnabled = externalDownloaderEnabled,
                    )
                }
            }
        }
    }
}


