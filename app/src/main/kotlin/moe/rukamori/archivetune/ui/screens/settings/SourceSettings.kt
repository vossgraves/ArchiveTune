/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.audiosource.AudioSourceConfig
import moe.rukamori.archivetune.constants.AudioSourceOrderKey
import moe.rukamori.archivetune.constants.AudioSourceType
import moe.rukamori.archivetune.constants.DeezerArlKey
import moe.rukamori.archivetune.constants.DeezerAudioQuality
import moe.rukamori.archivetune.constants.DeezerAudioQualityKey
import moe.rukamori.archivetune.constants.DeezerEnabledKey
import moe.rukamori.archivetune.constants.DefaultMetadataSourceKey
import moe.rukamori.archivetune.constants.DefaultSearchSourceKey
import moe.rukamori.archivetune.constants.InnerTubeCookieKey
import moe.rukamori.archivetune.constants.JioSaavnEnabledKey
import moe.rukamori.archivetune.constants.MetadataSource
import moe.rukamori.archivetune.constants.QobuzAudioQuality
import moe.rukamori.archivetune.constants.QobuzAudioQualityKey
import moe.rukamori.archivetune.constants.QobuzBackupEnabledKey
import moe.rukamori.archivetune.constants.QobuzEnabledKey
import moe.rukamori.archivetune.constants.QobuzTokensKey
import moe.rukamori.archivetune.constants.SearchProvider
import androidx.datastore.preferences.core.edit
import moe.rukamori.archivetune.constants.TidalAccessTokenKey
import moe.rukamori.archivetune.constants.TidalAudioQuality
import moe.rukamori.archivetune.constants.TidalAudioQualityKey
import moe.rukamori.archivetune.constants.TidalEnabledKey
import moe.rukamori.archivetune.constants.TidalNeedsReloginKey
import moe.rukamori.archivetune.constants.TidalRefreshTokenKey
import moe.rukamori.archivetune.constants.TidalTokenExpiryKey
import moe.rukamori.archivetune.innertube.utils.hasYouTubeLoginCookie
import moe.rukamori.archivetune.tidal.TidalAccountManager
import moe.rukamori.archivetune.tidal.TidalInstanceHealthManager
import moe.rukamori.archivetune.ui.component.EnumListPreference
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.screens.buildLoginRoute
import moe.rukamori.archivetune.ui.screens.settings.PO_TOKEN_ROUTE
import moe.rukamori.archivetune.ui.utils.appBarScrollBehavior
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.PoolAccountManager
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

fun maskCredential(value: String): String = if (value.length <= 4) "*".repeat(value.length) else "*".repeat(value.length - 4) + value.takeLast(4)

private fun AudioSourceType.displayName(context: android.content.Context): String = when (this) {
    AudioSourceType.TIDAL -> context.getString(R.string.source_tidal)
    AudioSourceType.QOBUZ -> context.getString(R.string.source_qobuz)
    AudioSourceType.QOBUZ_BACKUP -> context.getString(R.string.source_qobuz_backup)
    AudioSourceType.DEEZER -> context.getString(R.string.source_deezer)
    AudioSourceType.JIOSAAVN -> context.getString(R.string.source_jiosaavn)
    AudioSourceType.YOUTUBE -> context.getString(R.string.source_youtube)
}

private fun AudioSourceType.iconRes(): Int = when (this) {
    AudioSourceType.TIDAL -> R.drawable.provider_tidal
    AudioSourceType.QOBUZ -> R.drawable.provider_qobuz
    AudioSourceType.QOBUZ_BACKUP -> R.drawable.provider_qobuz
    AudioSourceType.DEEZER -> R.drawable.provider_deezer
    AudioSourceType.JIOSAAVN -> R.drawable.provider_jiosaavn
    AudioSourceType.YOUTUBE -> R.drawable.provider_youtube
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SourceSettings(navController: NavController, scrollTo: String? = null) {
    val scrollBehavior = appBarScrollBehavior()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val positions = rememberPreferencePositions()
    val scrollState = rememberScrollState()
    LaunchedEffect(scrollTo) { positions.scrollToKey(scrollTo, scrollState) }

    val (sourceOrderRaw, onSourceOrderChange) = rememberPreference(AudioSourceOrderKey, "")
    val (tidalEnabled, onTidalEnabledChange) = rememberPreference(TidalEnabledKey, true)
    val (qobuzEnabled, onQobuzEnabledChange) = rememberPreference(QobuzEnabledKey, false)
    val (qobuzBackupEnabled, onQobuzBackupEnabledChange) = rememberPreference(QobuzBackupEnabledKey, false)
    val (deezerEnabled, onDeezerEnabledChange) = rememberPreference(DeezerEnabledKey, false)
    val (jioSaavnEnabled, onJioSaavnEnabledChange) = rememberPreference(JioSaavnEnabledKey, false)
    val (tidalNeedsRelogin) = rememberPreference(TidalNeedsReloginKey, false)
    val (tidalToken) = rememberPreference(TidalAccessTokenKey, "")
    val (deezerArl) = rememberPreference(DeezerArlKey, "")
    val (qobuzTokens) = rememberPreference(QobuzTokensKey, "")
    val (innerTubeCookie, onInnerTubeCookieChange) = rememberPreference(InnerTubeCookieKey, "")
    val (defaultMetadataSource, onDefaultMetadataSourceChange) = rememberEnumPreference(DefaultMetadataSourceKey, MetadataSource.YOUTUBE)
    val (defaultSearchSource, onDefaultSearchSourceChange) = rememberEnumPreference(DefaultSearchSourceKey, SearchProvider.YOUTUBE)

    val sourceOrder = remember(sourceOrderRaw) { AudioSourceConfig.parseOrder(sourceOrderRaw.ifBlank { null }) }
    fun isEnabled(source: AudioSourceType): Boolean = when (source) {
        AudioSourceType.TIDAL -> tidalEnabled
        AudioSourceType.QOBUZ -> qobuzEnabled
        AudioSourceType.QOBUZ_BACKUP -> qobuzBackupEnabled
        AudioSourceType.DEEZER -> deezerEnabled
        AudioSourceType.JIOSAAVN -> jioSaavnEnabled
        AudioSourceType.YOUTUBE -> true
    }

    var showOrderSheet by rememberSaveable { mutableStateOf(false) }
    var showYouTubeSheet by rememberSaveable { mutableStateOf(false) }
    var showTidalTokenSheet by rememberSaveable { mutableStateOf(false) }
    var showPoolKeySheet by rememberSaveable { mutableStateOf(false) }
    val (poolApiKeyPref) = rememberPreference(moe.rukamori.archivetune.constants.PoolApiKeyKey, "")
    var showOverflow by remember { mutableStateOf(false) }
    var refreshingPool by remember { mutableStateOf(false) }

    val preferred = sourceOrder.firstOrNull { isEnabled(it) } ?: AudioSourceType.YOUTUBE

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.source_settings), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = navController::navigateUp, onLongClick = navController::backToMain) {
                        Icon(painterResource(R.drawable.arrow_back), null)
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showOverflow = true }) { Icon(painterResource(R.drawable.more_vert), null) }
                        DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.pool_refresh_title)) }, onClick = {
                                showOverflow = false
                                if (PoolAccountManager.isEnabled && !refreshingPool) {
                                    refreshingPool = true
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            PoolAccountManager.refresh(context, force = true)
                                            runCatching { TidalInstanceHealthManager.refresh(context, includeDiscovery = true, staggered = false) }
                                        }
                                        refreshingPool = false
                                        Toast.makeText(context, context.getString(R.string.pool_refresh_done, PoolAccountManager.tidalAccounts().size, PoolAccountManager.qobuzAccounts().size), Toast.LENGTH_LONG).show()
                                    }
                                }
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.pool_api_key_title)) }, onClick = {
                                showOverflow = false
                                showPoolKeySheet = true
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.sources_reset_priority)) }, onClick = {
                                showOverflow = false
                                onSourceOrderChange("")
                            })
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        val bottom = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
        Column(
            Modifier
                .padding(top = innerPadding.calculateTopPadding())
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal))
                .then(positions.containerModifier())
                .verticalScroll(scrollState)
                .padding(bottom = bottom + SettingsDimensions.ScreenBottomPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.sources_helper_text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            // Catalog sources
            PreferenceGroup(title = stringResource(R.string.catalog_sources)) {
                item {
                    EnumListPreference(
                        modifier = positions.modifierFor("default_metadata_source"),
                        title = { Text(stringResource(R.string.default_metadata_catalog)) },
                        description = stringResource(R.string.default_metadata_catalog_desc),
                        icon = { Icon(painterResource(R.drawable.album), null) },
                        selectedValue = defaultMetadataSource,
                        valueText = { when (it) { MetadataSource.YOUTUBE -> stringResource(R.string.metadata_source_youtube); MetadataSource.SPOTIFY -> stringResource(R.string.metadata_source_spotify) } },
                        onValueSelected = onDefaultMetadataSourceChange,
                    )
                }
                item {
                    EnumListPreference(
                        modifier = positions.modifierFor("default_search_source"),
                        title = { Text(stringResource(R.string.default_search_catalog)) },
                        description = stringResource(R.string.default_search_catalog_desc),
                        icon = { Icon(painterResource(R.drawable.provider_youtube), null) },
                        selectedValue = defaultSearchSource,
                        valueText = { when (it) { SearchProvider.YOUTUBE -> stringResource(R.string.search_source_youtube); SearchProvider.SPOTIFY -> stringResource(R.string.search_source_spotify) } },
                        onValueSelected = onDefaultSearchSourceChange,
                    )
                }
            }

            // Apple Music (login + quality; playback resolution stays account-based)
            PreferenceGroup(title = stringResource(R.string.applemusic_settings)) {
                item {
                    val (appleQuality, onAppleQualityChange) = rememberEnumPreference(
                        moe.rukamori.archivetune.constants.AppleMusicQualityKey,
                        moe.rukamori.archivetune.constants.AppleMusicQuality.AAC,
                    )
                    EnumListPreference(
                        modifier = positions.modifierFor("apple_music_quality"),
                        title = { Text(stringResource(R.string.applemusic_quality)) },
                        description = stringResource(R.string.applemusic_quality_desc),
                        icon = { Icon(painterResource(R.drawable.ic_music), null) },
                        selectedValue = appleQuality,
                        valueText = { when (it) {
                            moe.rukamori.archivetune.constants.AppleMusicQuality.AAC -> stringResource(R.string.applemusic_quality_aac)
                            moe.rukamori.archivetune.constants.AppleMusicQuality.LOSSLESS -> stringResource(R.string.applemusic_quality_lossless)
                            moe.rukamori.archivetune.constants.AppleMusicQuality.HI_RES_LOSSLESS -> stringResource(R.string.applemusic_quality_hires)
                        } },
                        onValueSelected = onAppleQualityChange,
                    )
                }
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.applemusic_sign_in_web)) },
                        description = stringResource(R.string.applemusic_settings),
                        icon = { Icon(painterResource(R.drawable.ic_music), null) },
                        onClick = { navController.navigate("settings/applemusic") },
                    )
                }
            }

            // Helper to build card data
            @Composable
            fun ServiceCard(
                source: AudioSourceType,
                enabled: Boolean,
                onEnabledChange: (Boolean) -> Unit,
                connectionSubtitle: String,
                status: String?,
                showSwitch: Boolean = true,
            ) {
                var menu by remember { mutableStateOf(false) }
                Card(
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                                Icon(painterResource(source.iconRes()), null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(source.displayName(context), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(connectionSubtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            status?.let {
                                val chipColor = when (it) {
                                    stringResource(R.string.sources_connected) -> MaterialTheme.colorScheme.primary
                                    stringResource(R.string.sources_needs_reauth) -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                AssistChip(onClick = {}, label = { Text(it, style = MaterialTheme.typography.labelSmall) }, leadingIcon = { Box(Modifier.size(8.dp).clip(CircleShape).background(chipColor)) }, colors = AssistChipDefaults.assistChipColors(), shape = RoundedCornerShape(999.dp))
                            }
                            Box {
                                IconButton(onClick = { menu = true }) { Icon(painterResource(R.drawable.more_vert), null) }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.sources_disconnect)) }, onClick = {
                                        menu = false
                                        when (source) {
                                            AudioSourceType.TIDAL -> scope.launch(Dispatchers.IO) { context.dataStore.edit { it.remove(TidalAccessTokenKey); it.remove(moe.rukamori.archivetune.constants.TidalRefreshTokenKey); it.remove(moe.rukamori.archivetune.constants.TidalTokenExpiryKey); it[TidalNeedsReloginKey] = false } }
                                            AudioSourceType.DEEZER -> onDeezerEnabledChange(false)
                                            else -> {}
                                        }
                                    })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.sources_advanced_setup)) }, onClick = {
                                        menu = false
                                        when (source) {
                                            AudioSourceType.TIDAL -> navController.navigate("settings/tidal")
                                            AudioSourceType.QOBUZ, AudioSourceType.QOBUZ_BACKUP -> navController.navigate("settings/qobuz")
                                            AudioSourceType.DEEZER -> navController.navigate("settings/deezer")
                                            AudioSourceType.JIOSAAVN -> navController.navigate("settings/jiosaavn")
                                            else -> {}
                                        }
                                    })
                                    if (source == AudioSourceType.TIDAL && tidalToken.isNotBlank()) {
                                        DropdownMenuItem(text = { Text(stringResource(R.string.copy_token_masked)) }, onClick = {
                                            menu = false
                                            copyToClipboard(context, "Tidal token", listOf(maskCredential(tidalToken)))
                                        })
                                    }
                                    if (source == AudioSourceType.TIDAL) {
                                        DropdownMenuItem(text = { Text(stringResource(R.string.sources_paste_token)) }, onClick = {
                                            menu = false
                                            showTidalTokenSheet = true
                                        })
                                    }
                                    if (source == AudioSourceType.DEEZER && deezerArl.isNotBlank()) {
                                        DropdownMenuItem(text = { Text(stringResource(R.string.copy_token_masked)) }, onClick = {
                                            menu = false
                                            copyToClipboard(context, "Deezer arl", listOf(maskCredential(deezerArl)))
                                        })
                                    }
                                    DropdownMenuItem(text = { Text(stringResource(R.string.settings_developer_options_title)) }, onClick = { menu = false; navController.navigate("settings/logcat") })
                                }
                            }
                        }
                        if (showSwitch) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text(if (enabled) stringResource(R.string.audio_source_enabled) else stringResource(R.string.audio_source_disabled), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                Switch(checked = enabled, onCheckedChange = onEnabledChange)
                            }
                        }
                    }
                }
            }

            val connectedSources = sourceOrder.filter { it != AudioSourceType.YOUTUBE && isEnabled(it) }
            val availableSources = sourceOrder.filter { it != AudioSourceType.YOUTUBE && !isEnabled(it) }

            if (connectedSources.isNotEmpty()) {
                Text(stringResource(R.string.sources_connected), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp))
                connectedSources.forEach { src ->
                    val subtitle = when (src) {
                        AudioSourceType.TIDAL -> if (tidalNeedsRelogin) stringResource(R.string.sources_needs_reauth) else if (tidalToken.isNotBlank()) stringResource(R.string.sources_connected) else stringResource(R.string.tidal_enable_description)
                        AudioSourceType.QOBUZ -> if (qobuzTokens.isNotBlank()) stringResource(R.string.sources_connected) else stringResource(R.string.qobuz_enable_description)
                        AudioSourceType.QOBUZ_BACKUP -> stringResource(R.string.qobuz_backup_enable_description)
                        AudioSourceType.DEEZER -> if (deezerArl.isNotBlank()) stringResource(R.string.sources_connected) else stringResource(R.string.deezer_enable_description)
                        AudioSourceType.JIOSAAVN -> stringResource(R.string.jiosaavn_enable_description)
                        else -> ""
                    }
                    val status = when (src) {
                        AudioSourceType.TIDAL -> if (tidalNeedsRelogin) stringResource(R.string.sources_needs_reauth) else if (tidalToken.isNotBlank() || !tidalEnabled) stringResource(R.string.sources_connected) else null
                        else -> if (isEnabled(src)) stringResource(R.string.sources_connected) else null
                    }
                    ServiceCard(src, isEnabled(src), { v ->
                        when (src) {
                            AudioSourceType.TIDAL -> onTidalEnabledChange(v)
                            AudioSourceType.QOBUZ -> onQobuzEnabledChange(v)
                            AudioSourceType.QOBUZ_BACKUP -> onQobuzBackupEnabledChange(v)
                            AudioSourceType.DEEZER -> onDeezerEnabledChange(v)
                            AudioSourceType.JIOSAAVN -> onJioSaavnEnabledChange(v)
                            else -> {}
                        }
                    }, subtitle, status)
                }
            }

            if (availableSources.isNotEmpty()) {
                Text(stringResource(R.string.sources_available), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp))
                availableSources.forEach { src ->
                    ServiceCard(src, false, { v ->
                        when (src) {
                            AudioSourceType.TIDAL -> onTidalEnabledChange(v)
                            AudioSourceType.QOBUZ -> onQobuzEnabledChange(v)
                            AudioSourceType.QOBUZ_BACKUP -> onQobuzBackupEnabledChange(v)
                            AudioSourceType.DEEZER -> onDeezerEnabledChange(v)
                            AudioSourceType.JIOSAAVN -> onJioSaavnEnabledChange(v)
                            else -> {}
                        }
                    }, when (src) {
                        AudioSourceType.TIDAL -> stringResource(R.string.tidal_enable_description)
                        AudioSourceType.QOBUZ -> stringResource(R.string.qobuz_enable_description)
                        AudioSourceType.QOBUZ_BACKUP -> stringResource(R.string.qobuz_backup_enable_description)
                        AudioSourceType.DEEZER -> stringResource(R.string.deezer_enable_description)
                        AudioSourceType.JIOSAAVN -> stringResource(R.string.jiosaavn_enable_description)
                        else -> ""
                    }, null)
                }
            }

            // Spotify metadata card
            Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.spotify_icon), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) }
                    Column(Modifier.weight(1f)) {
                        Text("Spotify", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.sources_metadata_only), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { navController.navigate("settings/integration") }) { Text(stringResource(R.string.sources_advanced_setup)) }
                }
            }

            // YouTube fallback card
            Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.provider_youtube), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) }
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.source_youtube), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.sources_youtube_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        AssistChip(onClick = {}, label = { Text(stringResource(R.string.sources_always_available), style = MaterialTheme.typography.labelSmall) }, shape = RoundedCornerShape(999.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { showYouTubeSheet = true }) { Text(stringResource(R.string.sources_advanced_setup)) }
                        TextButton(onClick = { navController.navigate(PO_TOKEN_ROUTE) }) { Text(stringResource(R.string.po_token_generation)) }
                    }
                }
            }

            // Telegram note card
            Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.provider_telegram), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) }
                    Column(Modifier.weight(1f)) {
                        Text("Telegram", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.sources_telegram_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { navController.navigate("settings/telegram") }) { Text(stringResource(R.string.sources_advanced_setup)) }
                }
            }

            // Source priority row
            Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), modifier = Modifier.fillMaxWidth()) {
                PreferenceEntry(
                    title = { Text(stringResource(R.string.preferred_sources)) },
                    description = sourceOrder.joinToString(" → ") { it.displayName(context) },
                    icon = { Icon(painterResource(R.drawable.tune), null) },
                    onClick = { showOrderSheet = true },
                )
            }

            Text(stringResource(R.string.sources_priority_footer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }

    if (showOrderSheet) {
        SourceOrderBottomSheet(
            initialOrder = sourceOrder,
            isEnabled = ::isEnabled,
            onDismiss = { showOrderSheet = false },
            onConfirm = { newOrder -> onSourceOrderChange(newOrder.joinToString(",") { it.name }); showOrderSheet = false },
        )
    }
    if (showYouTubeSheet) {
        YouTubeAdvancedSheet(
            currentCookie = innerTubeCookie,
            onSave = { v -> onInnerTubeCookieChange(v); showYouTubeSheet = false },
            onDismiss = { showYouTubeSheet = false },
            onBrowser = { navController.navigate(buildLoginRoute()) },
        )
    }
    if (showTidalTokenSheet) {
        TidalTokenSheet(
            onSave = { refreshToken ->
                showTidalTokenSheet = false
                scope.launch(Dispatchers.IO) {
                    context.dataStore.edit {
                        it[TidalRefreshTokenKey] = refreshToken
                        it[TidalTokenExpiryKey] = 0L
                        it[TidalNeedsReloginKey] = false
                        it[moe.rukamori.archivetune.constants.TidalAuthFlowKey] = moe.rukamori.archivetune.tidal.TidalAccountManager.FLOW_OAUTH
                    }
                    val working =
                        TidalAccountManager.refreshAccessToken(refreshToken, TidalAccountManager.FLOW_OAUTH)
                            ?: TidalAccountManager.refreshAccessToken(refreshToken, TidalAccountManager.FLOW_PKCE)
                    val resultMessage: Int =
                        if (working != null) {
                            context.dataStore.edit { prefs ->
                                prefs[TidalAccessTokenKey] = working.accessToken
                                prefs[TidalTokenExpiryKey] = working.expiresAtMillis
                                prefs[TidalRefreshTokenKey] = working.refreshToken ?: refreshToken
                                working.userId?.let { prefs[moe.rukamori.archivetune.constants.TidalUserIdKey] = it }
                                working.countryCode?.let { prefs[moe.rukamori.archivetune.constants.TidalCountryCodeKey] = it }
                                prefs[TidalNeedsReloginKey] = false
                            }
                            R.string.sources_tidal_token_verified
                        } else {
                            R.string.sources_tidal_token_failed
                        }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(resultMessage), Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDismiss = { showTidalTokenSheet = false },
        )
    }
    if (showPoolKeySheet) {
        PoolApiKeySheet(
            currentKey = poolApiKeyPref,
            onSave = { key ->
                scope.launch(Dispatchers.IO) {
                    context.dataStore.edit { it[moe.rukamori.archivetune.constants.PoolApiKeyKey] = key.trim() }
                }
                showPoolKeySheet = false
            },
            onDismiss = { showPoolKeySheet = false },
        )
    }
}

@Composable
private fun SourceOrderBottomSheet(
    initialOrder: List<AudioSourceType>,
    isEnabled: (AudioSourceType) -> Boolean,
    onDismiss: () -> Unit,
    onConfirm: (List<AudioSourceType>) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sources = remember { mutableStateListOf(*initialOrder.toTypedArray()) }
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val src = sources[from.index]
        if (src == AudioSourceType.YOUTUBE) return@rememberReorderableLazyListState
        val dest = sources[to.index]
        if (dest == AudioSourceType.YOUTUBE && to.index == sources.lastIndex) return@rememberReorderableLazyListState
        if (!isEnabled(src)) return@rememberReorderableLazyListState
        val item = sources.removeAt(from.index)
        sources.add(to.index, item)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.padding(16.dp).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.set_source_priority), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.size(12.dp))
            LazyColumn(state = lazyListState, modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                itemsIndexed(sources, key = { _, it -> it.name }) { index, source ->
                    ReorderableItem(reorderableState, key = source.name) {
                        val enabled = isEnabled(source)
                        val isYouTube = source == AudioSourceType.YOUTUBE
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = if (!enabled) MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f) else if (index == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh),
                            modifier = Modifier.fillMaxWidth().padding(bottom = if (index < sources.lastIndex) 8.dp else 0.dp),
                        ) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("${index + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Icon(painterResource(source.iconRes()), null, Modifier.size(20.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(source.displayName(LocalContext.current), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    if (!enabled) Text(stringResource(R.string.audio_source_disabled), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (isYouTube) Text(stringResource(R.string.sources_always_available), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (enabled && !isYouTube) Icon(painterResource(R.drawable.drag_handle), null, Modifier.size(20.dp).draggableHandle())
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(stringResource(android.R.string.cancel)) }
                FilledTonalButton(onClick = { onConfirm(sources.toList()) }, modifier = Modifier.weight(1f)) { Text(stringResource(android.R.string.ok)) }
            }
        }
    }
}

@Composable
private fun YouTubeAdvancedSheet(
    currentCookie: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    onBrowser: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var expanded by rememberSaveable { mutableStateOf(false) }
    var cookie by rememberSaveable { mutableStateOf(currentCookie) }
    var hidden by rememberSaveable { mutableStateOf(true) }
    val valid = hasYouTubeLoginCookie(cookie)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.padding(16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.source_youtube), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            FilledTonalButton(onClick = onBrowser, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.youtube_continue_browser)) }
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.youtube_advanced_sign_in)) }
            if (expanded) {
                OutlinedTextField(
                    value = cookie,
                    onValueChange = { cookie = it },
                    label = { Text("YouTube cookie") },
                    placeholder = { Text(maskCredential(cookie).takeIf { it.isNotEmpty() } ?: "Paste session cookie") },
                    visualTransformation = if (hidden) PasswordVisualTransformation() else VisualTransformation.None,
                    trailingIcon = { IconButton(onClick = { hidden = !hidden }) { Icon(painterResource(R.drawable.visibility_off), null) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = cookie.isNotBlank() && !valid,
                    supportingText = { if (cookie.isNotBlank() && !valid) Text(stringResource(R.string.youtube_session_invalid)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                FilledTonalButton(onClick = { onSave(cookie) }, enabled = valid, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.youtube_session_save)) }
            }
        }
    }
}

@Composable
private fun TidalTokenSheet(
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var token by rememberSaveable { mutableStateOf("") }
    var hidden by rememberSaveable { mutableStateOf(true) }
    val valid = token.trim().split('.').size == 3
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(
            Modifier.padding(16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.sources_tidal_token_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.sources_tidal_token_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text(stringResource(R.string.sources_tidal_token_label)) },
                visualTransformation = if (hidden) PasswordVisualTransformation() else VisualTransformation.None,
                trailingIcon = {
                    IconButton(onClick = { hidden = !hidden }) { Icon(if (hidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null) }
                },
                isError = token.isNotBlank() && !valid,
                supportingText = { if (token.isNotBlank() && !valid) Text(stringResource(R.string.sources_token_invalid)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(stringResource(android.R.string.cancel)) }
                FilledTonalButton(onClick = { onSave(token.trim()) }, enabled = valid, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.youtube_session_save))
                }
            }
        }
    }
}

@Composable
private fun PoolApiKeySheet(
    currentKey: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var key by rememberSaveable { mutableStateOf(currentKey) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(
            Modifier.padding(16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.pool_api_key_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.pool_api_key_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text(stringResource(R.string.pool_api_key_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(stringResource(android.R.string.cancel)) }
                FilledTonalButton(onClick = { onSave(key) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.youtube_session_save))
                }
            }
        }
    }
}
