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
import moe.rukamori.archivetune.constants.DeezerArlKey
import moe.rukamori.archivetune.constants.ExternalDownloaderEnabledKey
import moe.rukamori.archivetune.constants.ExternalDownloaderPackageKey
import moe.rukamori.archivetune.constants.QobuzTokensKey
import moe.rukamori.archivetune.constants.TidalAccessTokenKey
import moe.rukamori.archivetune.deezer.DeezerAudioProvider
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
    // Legacy single-pick key is kept for backup compatibility, but the UI now drives the
    // new drag-drop `DownloadSourceOrderKey` CSV. The order list is the source of truth.
    val (downloadSourceOrderRaw, onDownloadSourceOrderChange) =
        rememberPreference(DownloadSourceOrderKey, defaultValue = "")
    val downloadSourceOrder =
        remember(downloadSourceOrderRaw) {
            DownloadSourceConfig.parseOrder(downloadSourceOrderRaw)
        }
    val poolEnabled = remember { PoolAccountManager.isEnabled }
    // A user who signed in with their own credentials does not need the shared pool, so the
    // "Requires Source Pool" caption in the order dialog must not be shown for those sources —
    // otherwise the dialog greys out a source that `LosslessStreamResolver` will happily resolve.
    //
    // All three resolvers merge the user's own credential with the pool's: `resolveTidal` tries
    // `TidalAccessTokenKey` before any pool account, `resolveQobuz` merges `QobuzTokensKey` into its
    // token list, and Deezer's provider merges the manual ARL in `accounts()`. So the honest test is
    // "does this source have a credential of its own", one key per source.
    val (deezerArl) = rememberPreference(DeezerArlKey, defaultValue = "")
    val (tidalAccessToken) = rememberPreference(TidalAccessTokenKey, defaultValue = "")
    val (qobuzTokens) = rememberPreference(QobuzTokensKey, defaultValue = "")
    val sourcesWithOwnCredentials =
        remember(deezerArl, tidalAccessToken, qobuzTokens) {
            buildSet {
                // Deezer goes through the provider rather than the raw key: it also accepts an ARL
                // registered at runtime by the login screen before the key round-trips.
                if (DeezerAudioProvider.hasAccounts()) add(DownloadSource.DEEZER)
                if (tidalAccessToken.isNotBlank()) add(DownloadSource.TIDAL)
                if (qobuzTokens.isNotBlank()) add(DownloadSource.QOBUZ)
            }
        }
    val (externalDownloaderEnabled, onExternalDownloaderEnabledChange) =
        rememberPreference(ExternalDownloaderEnabledKey, defaultValue = false)
    val (externalDownloaderPackage, onExternalDownloaderPackageChange) =
        rememberPreference(ExternalDownloaderPackageKey, defaultValue = "")

    var showSourceOrderDialog by remember { mutableStateOf(false) }
    var showExternalDownloaderPackageDialog by remember { mutableStateOf(false) }
    var clearDownloads by remember { mutableStateOf(false) }

    if (showSourceOrderDialog) {
        DownloadSourceOrderDialog(
            initialOrder = downloadSourceOrder,
            poolEnabled = poolEnabled,
            sourcesWithOwnCredentials = sourcesWithOwnCredentials,
            onDismiss = { showSourceOrderDialog = false },
            onConfirm = { newOrder ->
                onDownloadSourceOrderChange(DownloadSourceConfig.serialize(newOrder))
                showSourceOrderDialog = false
            },
        )
    }

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

@Composable
private fun DownloadSourceOrderDialog(
    initialOrder: List<DownloadSource>,
    poolEnabled: Boolean,
    sourcesWithOwnCredentials: Set<DownloadSource>,
    onDismiss: () -> Unit,
    onConfirm: (List<DownloadSource>) -> Unit,
) {
    val context = LocalContext.current
    val sources = remember { mutableStateListOf(*initialOrder.toTypedArray()) }
    val lazyListState = rememberLazyListState()
    val reorderableState =
        rememberReorderableLazyListState(lazyListState) { from, to ->
            val item = sources.removeAt(from.index)
            sources.add(to.index, item)
        }

    DefaultDialog(
        onDismiss = onDismiss,
        buttons = {
            TextButton(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(android.R.string.cancel))
            }
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = { onConfirm(sources.toList()) },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
    ) {
        Column(modifier = Modifier.padding(top = 4.dp)) {
            Text(
                text = stringResource(R.string.set_source_priority),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            LazyColumn(
                state = lazyListState,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp),
            ) {
                itemsIndexed(sources, key = { _, item -> item.name }) { index, source ->
                    ReorderableItem(reorderableState, key = source.name) {
                        val isFirst = index == 0
                        val requiresPool =
                            source in DownloadSourceConfig.REQUIRES_POOL &&
                                source !in sourcesWithOwnCredentials
                        val available = !requiresPool || poolEnabled
                        val containerColor =
                            if (isFirst) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            }
                        val contentColor =
                            if (isFirst) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }

                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = if (index < sources.size - 1) 4.dp else 0.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(containerColor)
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = contentColor.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp),
                            )
                            Icon(
                                painter = painterResource(source.iconRes()),
                                contentDescription = null,
                                tint = contentColor,
                                modifier = Modifier.size(20.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = source.displayName(context),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = contentColor,
                                )
                                if (!available) {
                                    Text(
                                        text = stringResource(R.string.download_source_requires_pool),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = contentColor.copy(alpha = 0.7f),
                                    )
                                }
                            }
                            Icon(
                                painter = painterResource(R.drawable.drag_handle),
                                contentDescription = null,
                                tint = contentColor.copy(alpha = 0.6f),
                                modifier =
                                    Modifier
                                        .size(20.dp)
                                        .draggableHandle(),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun DownloadSource.displayName(context: android.content.Context): String =
    when (this) {
        DownloadSource.AUTO -> context.getString(R.string.download_source_auto)
        DownloadSource.QOBUZ -> context.getString(R.string.download_source_qobuz)
        DownloadSource.QOBUZ_BACKUP -> context.getString(R.string.source_qobuz_backup)
        DownloadSource.TIDAL -> context.getString(R.string.download_source_tidal)
        DownloadSource.DEEZER -> context.getString(R.string.download_source_deezer)
        DownloadSource.JIOSAAVN -> context.getString(R.string.download_source_jiosaavn)
        DownloadSource.YOUTUBE_MUSIC -> context.getString(R.string.download_source_youtube_music)
    }

private fun DownloadSource.displayName(): String =
    when (this) {
        DownloadSource.AUTO -> "Auto"
        DownloadSource.QOBUZ -> "Qobuz"
        DownloadSource.QOBUZ_BACKUP -> "Qobuz Backup"
        DownloadSource.TIDAL -> "Tidal"
        DownloadSource.DEEZER -> "Deezer"
        DownloadSource.JIOSAAVN -> "JioSaavn"
        DownloadSource.YOUTUBE_MUSIC -> "YouTube Music"
    }

private fun DownloadSource.iconRes(): Int =
    when (this) {
        DownloadSource.AUTO -> R.drawable.download
        DownloadSource.QOBUZ -> R.drawable.provider_qobuz
        DownloadSource.QOBUZ_BACKUP -> R.drawable.provider_qobuz
        DownloadSource.TIDAL -> R.drawable.provider_tidal
        DownloadSource.DEEZER -> R.drawable.provider_deezer
        DownloadSource.JIOSAAVN -> R.drawable.provider_jiosaavn
        DownloadSource.YOUTUBE_MUSIC -> R.drawable.play
    }
