/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Qobuz proxy source management for the Integration section: enable toggle, lossless quality,
 * and manual proxy-instance management. Instances are never auto-fetched — the user taps
 * "Test instances" to probe them on demand, each showing an "online — <ping>" (full),
 * "deprecated — <ping>" (preview/unsubscribed) or "connection failed" label. Mirrors TidalSettings.
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.QobuzAudioQuality
import moe.rukamori.archivetune.constants.QobuzAudioQualityKey
import moe.rukamori.archivetune.constants.QobuzEnabledKey
import moe.rukamori.archivetune.constants.QobuzInstancesKey
import moe.rukamori.archivetune.constants.QobuzLastProbeTrackKey
import moe.rukamori.archivetune.constants.QobuzTokensKey
import moe.rukamori.archivetune.constants.toFormatId
import moe.rukamori.archivetune.qobuz.QobuzAudioProvider
import moe.rukamori.archivetune.qobuz.QobuzToken
import moe.rukamori.archivetune.qobuz.SourceInputParsing
import moe.rukamori.archivetune.tidal.TidalAudioProvider
import moe.rukamori.archivetune.ui.component.DefaultDialog
import moe.rukamori.archivetune.ui.component.EnumListPreference
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.component.TextFieldDialog
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import androidx.compose.foundation.layout.asPaddingValues

/**
 * Process-lived cache of the last on-demand health-check results so that the checked status (and
 * measured ping) survives leaving and returning to the screen. It is intentionally NOT persisted to
 * disk — it is cleared only when the process dies or the user runs another check (which overwrites
 * the entries) or removes/reset the instance/token. Snapshot state maps so Compose recomposes when
 * a check updates them.
 */
private object QobuzHealthUiCache {
    val instanceHealth = mutableStateMapOf<String, TidalAudioProvider.InstanceHealth>()
    val instanceLatency = mutableStateMapOf<String, Long>()
    val tokenHealth = mutableStateMapOf<String, TidalAudioProvider.InstanceHealth>()
    val tokenLatency = mutableStateMapOf<String, Long>()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QobuzSettings(navController: NavController, scrollTo: String? = null) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val (qobuzEnabled, onQobuzEnabledChange) = rememberPreference(QobuzEnabledKey, false)
    val (audioQuality, onAudioQualityChange) =
        rememberEnumPreference(QobuzAudioQualityKey, QobuzAudioQuality.FLAC)
    val probeTrack by rememberPreference(QobuzLastProbeTrackKey, "")

    // Instances stored as a newline-separated string. No bundled defaults: blank == disabled.
    val (storedInstances, onStoredInstancesChange) = rememberPreference(QobuzInstancesKey, "")
    val effectiveInstances =
        remember(storedInstances) {
            storedInstances
                .split('\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }

    fun persistInstances(list: List<String>) {
        onStoredInstancesChange(list.distinct().joinToString("\n"))
    }

    // baseUrl -> scan status (null while untested) and last measured latency (ms). Backed by a
    // process-lived cache so a completed check persists when navigating away and back.
    val healthStatus = QobuzHealthUiCache.instanceHealth
    val healthLatency = QobuzHealthUiCache.instanceLatency
    var testingInstances by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showBulkDialog by remember { mutableStateOf(false) }
    var detailInstance by remember { mutableStateOf<String?>(null) }

    // Direct-API tokens, stored as a JSON list. Tried before proxy instances during resolution.
    val (storedTokens, onStoredTokensChange) = rememberPreference(QobuzTokensKey, "")
    val tokens = remember(storedTokens) { QobuzToken.listFromJson(storedTokens) }
    fun persistTokens(list: List<QobuzToken>) {
        val deduped = list.distinctBy { it.token }
        onStoredTokensChange(QobuzToken.listToJson(deduped))
    }
    // token id -> health status + last measured ping (ms), process-lived so it survives navigation.
    val tokenHealth = QobuzHealthUiCache.tokenHealth
    val tokenLatency = QobuzHealthUiCache.tokenLatency
    var testingTokens by remember { mutableStateOf(false) }
    var showAddTokensDialog by remember { mutableStateOf(false) }
    var detailToken by remember { mutableStateOf<QobuzToken?>(null) }
    var showTokenManagement by remember { mutableStateOf(false) }
    var showInstanceManagement by remember { mutableStateOf(false) }
    // Token id whose "deprecated" info popup is open (preview-only / no premium explanation).
    var previewInfoTokenId by remember { mutableStateOf<String?>(null) }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun labelFor(
        status: TidalAudioProvider.InstanceHealth,
        latencyMs: Long?,
    ): String =
        when (status) {
            TidalAudioProvider.InstanceHealth.HEALTHY ->
                context.getString(R.string.tidal_instance_healthy, (latencyMs ?: 0L).toInt())
            TidalAudioProvider.InstanceHealth.PREVIEW_ONLY ->
                context.getString(R.string.tidal_instance_preview_only, (latencyMs ?: 0L).toInt())
            TidalAudioProvider.InstanceHealth.UNREACHABLE ->
                context.getString(R.string.tidal_instance_unreachable)
        }

    // Manual, on-demand probe of every configured instance (reachability AND full-vs-preview),
    // done inline via the provider (no separate health manager). Runs one instance at a time.
    fun runInstanceTest() {
        if (testingInstances) return
        testingInstances = true
        val formatId = audioQuality.toFormatId()
        val probe = probeTrack
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                effectiveInstances.forEach { instance ->
                    val start = System.currentTimeMillis()
                    val status = QobuzAudioProvider.verifyInstance(instance, probe, formatId)
                    val latency = System.currentTimeMillis() - start
                    QobuzAudioProvider.applyHealthResult(
                        instance,
                        healthy = status == TidalAudioProvider.InstanceHealth.HEALTHY,
                    )
                    withContext(Dispatchers.Main) {
                        healthStatus[instance] = status
                        if (status != TidalAudioProvider.InstanceHealth.UNREACHABLE) {
                            healthLatency[instance] = latency
                        }
                    }
                }
            }
            testingInstances = false
        }
    }

    fun runTokenTest() {
        if (testingTokens) return
        testingTokens = true
        val formatId = audioQuality.toFormatId()
        val probe = probeTrack
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                tokens.forEach { token ->
                    val start = System.currentTimeMillis()
                    val status = QobuzAudioProvider.verifyToken(token, probe, formatId)
                    val latency = System.currentTimeMillis() - start
                    withContext(Dispatchers.Main) {
                        tokenHealth[token.id] = status
                        if (status != TidalAudioProvider.InstanceHealth.UNREACHABLE) {
                            tokenLatency[token.id] = latency
                        } else {
                            tokenLatency.remove(token.id)
                        }
                    }
                }
            }
            testingTokens = false
        }
    }

    // Removes every instance whose last scan matched [statuses], returning the count removed.
    fun removeInstancesWithStatus(statuses: Set<TidalAudioProvider.InstanceHealth>): Int {
        val doomed = effectiveInstances.filter { healthStatus[it] in statuses }
        if (doomed.isEmpty()) {
            toast(context.getString(R.string.source_nothing_to_do))
            return 0
        }
        doomed.forEach {
            healthStatus.remove(it)
            healthLatency.remove(it)
        }
        persistInstances(effectiveInstances - doomed.toSet())
        toast(context.getString(R.string.source_removed, doomed.size))
        return doomed.size
    }

    fun copyOnlineInstances() {
        val online = effectiveInstances.filter { healthStatus[it] == TidalAudioProvider.InstanceHealth.HEALTHY }
        if (online.isEmpty()) {
            toast(context.getString(R.string.source_nothing_to_do))
            return
        }
        copyToClipboard(context, "Qobuz instances", online)
    }

    // "Deprecated" explanation popup: why a reachable token is limited (preview-only / no premium).
    previewInfoTokenId?.let {
        DefaultDialog(
            onDismiss = { previewInfoTokenId = null },
            icon = { Icon(painterResource(R.drawable.info), null) },
            title = { Text(stringResource(R.string.qobuz_status_deprecated_info_title)) },
            buttons = {
                TextButton(
                    onClick = { previewInfoTokenId = null },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.close_dialog))
                }
            },
        ) {
            Text(
                text = stringResource(R.string.qobuz_status_deprecated_info),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    // Token detail dialog — manually entered credentials are shown in full and editable.
    detailToken?.let { token ->
        var editLabel by remember(token) { mutableStateOf(token.label) }
        var editUserId by remember(token) { mutableStateOf(token.userId) }
        var editToken by remember(token) { mutableStateOf(token.token) }
        var editAppId by remember(token) { mutableStateOf(token.appId) }
        var editAppSecret by remember(token) { mutableStateOf(token.appSecret) }

        val status = tokenHealth[token.id]
        val statusColor =
            when (status) {
                TidalAudioProvider.InstanceHealth.HEALTHY -> Color(0xFF4FC3F7)
                TidalAudioProvider.InstanceHealth.PREVIEW_ONLY -> Color(0xFFB388FF)
                TidalAudioProvider.InstanceHealth.UNREACHABLE -> MaterialTheme.colorScheme.error
                null -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        val statusLabel =
            if (status != null) {
                labelFor(status, tokenLatency[token.id])
            } else {
                stringResource(R.string.qobuz_token_status_unknown)
            }

        DefaultDialog(
            onDismiss = { detailToken = null },
            icon = { Icon(painterResource(R.drawable.token), null) },
            title = { Text(stringResource(R.string.details)) },
            contentScrollable = true,
            buttons = {
                TextButton(
                    onClick = {
                        val updated =
                            token.copy(
                                label = editLabel.trim(),
                                userId = editUserId.trim(),
                                token = editToken.trim(),
                                appId = editAppId.trim(),
                                appSecret = editAppSecret.trim(),
                            )
                        if (updated.token.isBlank() || updated.appId.isBlank() || updated.appSecret.isBlank()) {
                            toast(context.getString(R.string.qobuz_tokens_none_parsed))
                        } else {
                            persistTokens(tokens.map { if (it == token) updated else it })
                            if (updated.id != token.id) {
                                tokenHealth.remove(token.id)
                            }
                            detailToken = null
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(android.R.string.ok))
                }
                TextButton(
                    onClick = {
                        copyToClipboard(context, "Qobuz token", listOf(token.token))
                        detailToken = null
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(context.getString(R.string.copy_link).replace("link", "token"))
                }
                TextButton(
                    onClick = { detailToken = null },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.close_dialog))
                }
            },
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = editLabel,
                    onValueChange = { editLabel = it },
                    label = { Text("Account") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = editUserId,
                    onValueChange = { editUserId = it },
                    label = { Text("User ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = editToken,
                    onValueChange = { editToken = it },
                    label = { Text("Token (auth)") },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = editAppId,
                    onValueChange = { editAppId = it },
                    label = { Text("App ID") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = editAppSecret,
                    onValueChange = { editAppSecret = it },
                    label = { Text("App Secret") },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text("Status", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(2.dp))
                Text(statusLabel, style = MaterialTheme.typography.bodyMedium, color = statusColor)
                Spacer(Modifier.height(10.dp))
                Text("Subscription", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = token.subscription.ifBlank { stringResource(R.string.qobuz_token_status_unknown) },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    // Instance detail popup — same style as the lyrics search result dialog.
    detailInstance?.let { instance ->
        Dialog(
            onDismissRequest = { detailInstance = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .navigationBarsPadding(),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 560.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = AlertDialogDefaults.TonalElevation,
                ) {
                    Column(modifier = Modifier
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState())) {
                        Icon(
                            painter = painterResource(R.drawable.link),
                            contentDescription = null,
                            tint = AlertDialogDefaults.iconContentColor,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.details),
                            style = MaterialTheme.typography.headlineSmall,
                            color = AlertDialogDefaults.titleContentColor,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("Instance URL", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text(instance, style = MaterialTheme.typography.bodyMedium)
                        val status = healthStatus[instance]
                        if (status != null) {
                            Spacer(Modifier.height(12.dp))
                            Text("Status", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(2.dp))
                            Text(labelFor(status, healthLatency[instance]), style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(
                                onClick = {
                                    copyToClipboard(context, "Qobuz instance", listOf(instance))
                                    detailInstance = null
                                },
                                shapes = ButtonDefaults.shapes(),
                            ) {
                                Text(context.getString(R.string.copy_link).replace("link", "URL"))
                            }
                            TextButton(
                                onClick = { detailInstance = null },
                                shapes = ButtonDefaults.shapes(),
                            ) {
                                Text(stringResource(R.string.close_dialog))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showBulkDialog) {
        TextFieldDialog(
            icon = { Icon(painterResource(R.drawable.playlist_add), null) },
            title = { Text(stringResource(R.string.source_bulk_add)) },
            placeholder = { Text(stringResource(R.string.source_bulk_hint)) },
            singleLine = false,
            isInputValid = { it.isNotBlank() },
            onDone = { raw ->
                val parsed = SourceInputParsing.parseUrls(raw).mapNotNull { QobuzAudioProvider.normalizeInstanceUrl(it) }
                val added = parsed.filterNot { effectiveInstances.contains(it) }
                if (added.isEmpty()) {
                    toast(context.getString(R.string.source_bulk_none))
                } else {
                    persistInstances(effectiveInstances + added)
                    toast(context.getString(R.string.source_bulk_added, added.size))
                }
            },
            onDismiss = { showBulkDialog = false },
        )
    }

    if (showAddTokensDialog) {
        TextFieldDialog(
            icon = { Icon(painterResource(R.drawable.token), null) },
            title = { Text(stringResource(R.string.qobuz_add_tokens)) },
            placeholder = { Text(stringResource(R.string.qobuz_add_tokens_hint)) },
            singleLine = false,
            isInputValid = { it.isNotBlank() },
            onDone = { raw ->
                val parsed = SourceInputParsing.parseQobuzTokens(raw)
                if (parsed.isEmpty()) {
                    toast(context.getString(R.string.qobuz_tokens_none_parsed))
                } else {
                    persistTokens(tokens + parsed)
                    toast(context.getString(R.string.qobuz_tokens_parsed, parsed.size))
                }
            },
            onDismiss = { showAddTokensDialog = false },
        )
    }

    if (showAddDialog) {
        TextFieldDialog(
            icon = { Icon(painterResource(R.drawable.add), null) },
            title = { Text(stringResource(R.string.qobuz_add_instance)) },
            placeholder = { Text(stringResource(R.string.tidal_instance_url_hint)) },
            isInputValid = { QobuzAudioProvider.normalizeInstanceUrl(it) != null },
            onDone = { raw ->
                val normalized = QobuzAudioProvider.normalizeInstanceUrl(raw)
                when {
                    normalized == null -> toast(context.getString(R.string.tidal_instance_invalid_url))
                    effectiveInstances.contains(normalized) ->
                        toast(context.getString(R.string.tidal_instance_duplicate))
                    else -> persistInstances(effectiveInstances + normalized)
                }
            },
            onDismiss = { showAddDialog = false },
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.qobuz_integration)) },
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
                .verticalScroll(scrollState)
                .padding(bottom = playerAwareBottomPadding + SettingsDimensions.ScreenBottomPadding),
        ) {
            PreferenceGroup(
                modifier = positions.modifierFor("qobuz_account"),
                title = stringResource(R.string.qobuz_integration),
            ) {
                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.qobuz_enable)) },
                        description = stringResource(R.string.qobuz_enable_description),
                        icon = { Icon(painterResource(R.drawable.provider_qobuz), null) },
                        checked = qobuzEnabled,
                        onCheckedChange = onQobuzEnabledChange,
                    )
                }

                item {
                    EnumListPreference(
                        title = { Text(stringResource(R.string.qobuz_audio_quality)) },
                        icon = { Icon(painterResource(R.drawable.graphic_eq), null) },
                        selectedValue = audioQuality,
                        onValueSelected = onAudioQualityChange,
                        isEnabled = qobuzEnabled,
                        valueText = { quality ->
                            when (quality) {
                                QobuzAudioQuality.FLAC -> stringResource(R.string.qobuz_quality_flac)
                                QobuzAudioQuality.HI_RES -> stringResource(R.string.qobuz_quality_hires)
                                QobuzAudioQuality.MAX -> stringResource(R.string.qobuz_quality_max)
                            }
                        },
                    )
                }
            }

            PreferenceGroup(
                modifier = positions.modifierFor("qobuz_tokens"),
                title = stringResource(R.string.qobuz_tokens),
            ) {
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.qobuz_login_web)) },
                        icon = { Icon(painterResource(R.drawable.provider_qobuz), null) },
                        onClick = { navController.navigate(QOBUZ_LOGIN_ROUTE) },
                    )
                }

                item(visible = showTokenManagement) {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.qobuz_add_tokens)) },
                        icon = { Icon(painterResource(R.drawable.token), null) },
                        onClick = { showAddTokensDialog = true },
                    )
                }

                if (tokens.isNotEmpty()) {
                    item {
                        PreferenceEntry(
                            title = {
                                Text(
                                    if (testingTokens) {
                                        stringResource(R.string.qobuz_checking_tokens)
                                    } else {
                                        stringResource(R.string.qobuz_check_tokens)
                                    },
                                )
                            },
                            icon = { Icon(painterResource(R.drawable.sync), null) },
                            isEnabled = !testingTokens,
                            onClick = { runTokenTest() },
                        )
                    }
                }

                item {
                    val onlineCount = tokens.count {
                        tokenHealth[it.id] == TidalAudioProvider.InstanceHealth.HEALTHY
                    }
                    val deprecatedCount = tokens.count {
                        tokenHealth[it.id] == TidalAudioProvider.InstanceHealth.PREVIEW_ONLY
                    }
                    val failedCount = tokens.count {
                        tokenHealth[it.id] == TidalAudioProvider.InstanceHealth.UNREACHABLE
                    }
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.qobuz_manage_accounts)) },
                        description = stringResource(
                            R.string.source_health_summary,
                            tokens.size,
                            onlineCount,
                            deprecatedCount,
                            failedCount,
                        ),
                        icon = { Icon(painterResource(R.drawable.tune), null) },
                        onClick = { showTokenManagement = !showTokenManagement },
                        trailingContent = {
                            Icon(
                                painterResource(
                                    if (showTokenManagement) R.drawable.expand_less
                                    else R.drawable.expand_more,
                                ),
                                contentDescription = null,
                            )
                        },
                    )
                }

                tokens.forEach { token ->
                    item(visible = showTokenManagement) {
                        val status = tokenHealth[token.id]
                        val statusColor =
                            when (status) {
                                TidalAudioProvider.InstanceHealth.HEALTHY -> Color(0xFF4FC3F7)
                                TidalAudioProvider.InstanceHealth.PREVIEW_ONLY -> Color(0xFFB388FF)
                                TidalAudioProvider.InstanceHealth.UNREACHABLE -> Color(0xFF9E9E9E)
                                null -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        // Same "online — Xms / deprecated — Xms / not reachable" wording as the
                        // instance rows; "deprecated" gets an info icon explaining preview-only.
                        val statusLabel =
                            if (status != null) {
                                labelFor(status, tokenLatency[token.id])
                            } else {
                                stringResource(R.string.qobuz_token_status_unknown)
                            }
                        val displayName = token.label.ifBlank { token.userId.ifBlank { "Qobuz account" } }
                        PreferenceEntry(
                            title = {
                                Column {
                                    Text(stringResource(R.string.qobuz_token_subtitle, displayName, token.id))
                                    Text(
                                        text = statusLabel,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = statusColor,
                                    )
                                }
                            },
                            icon = { Icon(painterResource(R.drawable.token), null) },
                            onClick = { detailToken = token },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (status == TidalAudioProvider.InstanceHealth.PREVIEW_ONLY) {
                                        IconButton(
                                            onClick = { previewInfoTokenId = token.id },
                                            onLongClick = {},
                                        ) {
                                            Icon(
                                                painterResource(R.drawable.info),
                                                contentDescription = stringResource(R.string.qobuz_status_deprecated_info_title),
                                                tint = Color(0xFFB388FF),
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            tokenHealth.remove(token.id)
                                            tokenLatency.remove(token.id)
                                            persistTokens(tokens - token)
                                        },
                                        onLongClick = {},
                                    ) {
                                        Icon(painterResource(R.drawable.delete), null)
                                    }
                                }
                            },
                        )
                    }
                }

                if (tokens.isNotEmpty()) {
                    item(visible = showTokenManagement) {
                        PreferenceEntry(
                            title = { Text(stringResource(R.string.qobuz_reset_tokens)) },
                            icon = { Icon(painterResource(R.drawable.close), null) },
                            onClick = {
                                tokenHealth.clear()
                                tokenLatency.clear()
                                onStoredTokensChange("")
                            },
                        )
                    }
                }
            }

            PreferenceGroup(
                modifier = positions.modifierFor("qobuz_instances"),
                title = stringResource(R.string.qobuz_instances),
            ) {
                item {
                    PreferenceEntry(
                        title = {
                            Text(
                                if (testingInstances) {
                                    stringResource(R.string.qobuz_checking_instances)
                                } else {
                                    stringResource(R.string.qobuz_check_instances)
                                },
                            )
                        },
                        icon = { Icon(painterResource(R.drawable.sync), null) },
                        isEnabled = !testingInstances && effectiveInstances.isNotEmpty(),
                        onClick = { runInstanceTest() },
                    )
                }

                item {
                    val onlineCount = effectiveInstances.count {
                        healthStatus[it] == TidalAudioProvider.InstanceHealth.HEALTHY
                    }
                    val deprecatedCount = effectiveInstances.count {
                        healthStatus[it] == TidalAudioProvider.InstanceHealth.PREVIEW_ONLY
                    }
                    val failedCount = effectiveInstances.count {
                        healthStatus[it] == TidalAudioProvider.InstanceHealth.UNREACHABLE
                    }
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.source_manage_instances)) },
                        description = stringResource(
                            R.string.source_health_summary,
                            effectiveInstances.size,
                            onlineCount,
                            deprecatedCount,
                            failedCount,
                        ),
                        icon = { Icon(painterResource(R.drawable.tune), null) },
                        onClick = { showInstanceManagement = !showInstanceManagement },
                        trailingContent = {
                            Icon(
                                painterResource(
                                    if (showInstanceManagement) R.drawable.expand_less
                                    else R.drawable.expand_more,
                                ),
                                contentDescription = null,
                            )
                        },
                    )
                }

                effectiveInstances.forEach { instance ->
                    item(visible = showInstanceManagement) {
                        val status = healthStatus[instance]
                        // online = light blue, deprecated/preview-only = purple, failed = grey.
                        val statusColor =
                            when (status) {
                                TidalAudioProvider.InstanceHealth.HEALTHY -> Color(0xFF4FC3F7)
                                TidalAudioProvider.InstanceHealth.PREVIEW_ONLY -> Color(0xFFB388FF)
                                TidalAudioProvider.InstanceHealth.UNREACHABLE -> Color(0xFF9E9E9E)
                                null -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        val statusLabel =
                            if (status != null) {
                                labelFor(status, healthLatency[instance])
                            } else {
                                stringResource(R.string.tidal_instance_unknown)
                            }
                        PreferenceEntry(
                            title = {
                                Column {
                                    Text(instance)
                                    Text(
                                        text = statusLabel,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = statusColor,
                                    )
                                }
                            },
                            icon = { Icon(painterResource(R.drawable.link), null) },
                            onClick = { detailInstance = instance },
                            trailingContent = {
                                IconButton(
                                    onClick = {
                                        val remaining = effectiveInstances - instance
                                        healthStatus.remove(instance)
                                        healthLatency.remove(instance)
                                        persistInstances(remaining)
                                    },
                                    onLongClick = {},
                                ) {
                                    Icon(painterResource(R.drawable.delete), null)
                                }
                            },
                        )
                    }
                }

                item(visible = showInstanceManagement) {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.qobuz_add_instance)) },
                        icon = { Icon(painterResource(R.drawable.add), null) },
                        onClick = { showAddDialog = true },
                    )
                }

                item(visible = showInstanceManagement) {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.source_bulk_add)) },
                        description = stringResource(R.string.source_bulk_hint),
                        icon = { Icon(painterResource(R.drawable.playlist_add), null) },
                        onClick = { showBulkDialog = true },
                    )
                }

                if (effectiveInstances.isNotEmpty()) {
                    item(visible = showInstanceManagement) {
                        PreferenceEntry(
                            title = { Text(stringResource(R.string.source_copy_online)) },
                            icon = { Icon(painterResource(R.drawable.copy), null) },
                            onClick = { copyOnlineInstances() },
                        )
                    }

                    item(visible = showInstanceManagement) {
                        PreferenceEntry(
                            title = { Text(stringResource(R.string.source_remove_dead)) },
                            icon = { Icon(painterResource(R.drawable.delete), null) },
                            onClick = {
                                removeInstancesWithStatus(setOf(TidalAudioProvider.InstanceHealth.UNREACHABLE))
                            },
                        )
                    }

                    item(visible = showInstanceManagement) {
                        PreferenceEntry(
                            title = { Text(stringResource(R.string.source_remove_deprecated)) },
                            icon = { Icon(painterResource(R.drawable.delete), null) },
                            onClick = {
                                removeInstancesWithStatus(setOf(TidalAudioProvider.InstanceHealth.PREVIEW_ONLY))
                            },
                        )
                    }
                }

                item(visible = showInstanceManagement) {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.qobuz_reset_instances)) },
                        icon = { Icon(painterResource(R.drawable.close), null) },
                        onClick = {
                            healthStatus.clear()
                            healthLatency.clear()
                            onStoredInstancesChange("")
                        },
                    )
                }
            }
        }
    }
}
