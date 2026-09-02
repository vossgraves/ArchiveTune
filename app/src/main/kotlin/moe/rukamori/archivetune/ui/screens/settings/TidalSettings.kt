/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Tidal account login/logout for the Integration section, plus manual HiFi instance
 * management. Instances are never auto-fetched: the user taps "Test instances" to probe
 * them on demand, and each result shows an "online — <ping>" (full/premium) or
 * "deprecated — <ping>" (preview-only/non-premium) label.
 * Ported from MetroFuse (github.com/956tris/MetroFuse) under GPL-3.0.
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
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.TidalAccessTokenKey
import moe.rukamori.archivetune.constants.TidalAccountNameKey
import moe.rukamori.archivetune.constants.TidalAuthFlowKey
import moe.rukamori.archivetune.constants.TidalCountryCodeKey
import moe.rukamori.archivetune.constants.TidalInstancesKey
import moe.rukamori.archivetune.constants.TidalNeedsReloginKey
import moe.rukamori.archivetune.constants.TidalRefreshTokenKey
import moe.rukamori.archivetune.constants.TidalSubscriptionKey
import moe.rukamori.archivetune.constants.TidalSubscriptionStatus
import moe.rukamori.archivetune.constants.TidalTokenExpiryKey
import moe.rukamori.archivetune.constants.TidalUserIdKey
import moe.rukamori.archivetune.qobuz.SourceInputParsing
import moe.rukamori.archivetune.tidal.TidalAccountManager
import moe.rukamori.archivetune.tidal.TidalAudioProvider
import moe.rukamori.archivetune.tidal.TidalInstanceHealthManager
import moe.rukamori.archivetune.ui.component.DefaultDialog
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.TextFieldDialog
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.rememberPreference
import androidx.compose.foundation.layout.asPaddingValues

/**
 * Process-lived cache of the last instance health-check results so the checked status (and ping)
 * survives leaving and returning to the screen, until another check overwrites it. Not persisted to
 * disk.
 */
private object TidalHealthUiCache {
    val instanceHealth = mutableStateMapOf<String, TidalAudioProvider.InstanceHealth>()
    val instanceLatency = mutableStateMapOf<String, Long>()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TidalSettings(navController: NavController, scrollTo: String? = null) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val accessToken by rememberPreference(TidalAccessTokenKey, "")
    val accountName by rememberPreference(TidalAccountNameKey, "")
    val subscriptionRaw by rememberPreference(TidalSubscriptionKey, TidalSubscriptionStatus.UNKNOWN.name)
    val needsRelogin by rememberPreference(TidalNeedsReloginKey, false)
    val countryCode by rememberPreference(TidalCountryCodeKey, "")
    val userId by rememberPreference(TidalUserIdKey, 0L)

    val subscription =
        remember(subscriptionRaw) {
            runCatching { TidalSubscriptionStatus.valueOf(subscriptionRaw) }
                .getOrDefault(TidalSubscriptionStatus.UNKNOWN)
        }
    val accountConfigured = accessToken.isNotBlank()

    // ----- HiFi instance management (moved here from Streaming sources) -----
    // Instances stored as a newline-separated string; blank means "use built-in defaults".
    val (storedInstances, onStoredInstancesChange) = rememberPreference(TidalInstancesKey, "")

    val defaults = remember { TidalAudioProvider.defaultInstanceUrls }
    val effectiveInstances =
        remember(storedInstances) {
            storedInstances
                .split('\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .ifEmpty { defaults }
        }

    fun persistInstances(list: List<String>) {
        val distinct = list.distinct()
        onStoredInstancesChange(if (distinct == defaults) "" else distinct.joinToString("\n"))
    }

    // baseUrl -> scan status (null while untested) + last latency, backed by a process-lived cache so
    // a completed check persists when navigating away and back. Nothing is probed until Test is tapped.
    val healthStatus = TidalHealthUiCache.instanceHealth
    val healthLatency = TidalHealthUiCache.instanceLatency
    var testingInstances by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showBulkDialog by remember { mutableStateOf(false) }
    var detailInstance by remember { mutableStateOf<String?>(null) }
    var showAccountDetail by remember { mutableStateOf(false) }
    var showInstanceManagement by remember { mutableStateOf(false) }
    var showTokenDialog by remember { mutableStateOf(false) }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    // Removes every instance whose last scan matched [statuses], returning the count removed.
    fun removeInstancesWithStatus(statuses: Set<TidalAudioProvider.InstanceHealth>) {
        val doomed = effectiveInstances.filter { healthStatus[it] in statuses }
        if (doomed.isEmpty()) {
            toast(context.getString(R.string.source_nothing_to_do))
            return
        }
        doomed.forEach {
            healthStatus.remove(it)
            healthLatency.remove(it)
        }
        persistInstances(effectiveInstances - doomed.toSet())
        toast(context.getString(R.string.source_removed, doomed.size))
    }

    fun copyOnlineInstances() {
        val online = effectiveInstances.filter { healthStatus[it] == TidalAudioProvider.InstanceHealth.HEALTHY }
        if (online.isEmpty()) {
            toast(context.getString(R.string.source_nothing_to_do))
            return
        }
        copyToClipboard(context, "Tidal instances", online)
    }

    // Turns a scan status into its ping label:
    //  - HEALTHY (full stream, premium account) -> "online — <ping> ms"
    //  - PREVIEW_ONLY (free / non-premium account) -> "deprecated — <ping> ms"
    //  - UNREACHABLE -> "connection failed"
    fun labelFor(status: TidalAudioProvider.InstanceHealth, latencyMs: Long?): String =
        when (status) {
            TidalAudioProvider.InstanceHealth.HEALTHY ->
                context.getString(R.string.tidal_instance_healthy, (latencyMs ?: 0L).toInt())
            TidalAudioProvider.InstanceHealth.PREVIEW_ONLY ->
                context.getString(R.string.tidal_instance_preview_only, (latencyMs ?: 0L).toInt())
            TidalAudioProvider.InstanceHealth.UNREACHABLE ->
                context.getString(R.string.tidal_instance_unreachable)
        }

    fun applyRecords(records: List<TidalInstanceHealthManager.InstanceRecord>) {
        records.forEach { record ->
            healthStatus[record.url] = record.status
            record.latencyMs?.let { healthLatency[record.url] = it }
        }
    }

    // Manual, on-demand probe of every configured instance (reachability AND full-vs-preview).
    // Triggered only by the user tapping "Test instances" — never automatically.
    fun runInstanceTest() {
        if (testingInstances) return
        testingInstances = true
        coroutineScope.launch {
            val records =
                withContext(Dispatchers.IO) {
                    TidalInstanceHealthManager.refresh(context, includeDiscovery = false, staggered = false)
                }
            applyRecords(records)
            testingInstances = false
        }
    }

    // Account detail dialog — shows the logged-in account's token info.
    if (showAccountDetail) {
        DefaultDialog(
            onDismiss = { showAccountDetail = false },
            icon = { Icon(painterResource(R.drawable.token), null) },
            title = { Text(stringResource(R.string.details)) },
            contentScrollable = true,
            buttons = {
                TextButton(
                    onClick = {
                        copyToClipboard(context, "Tidal token", listOf(accessToken))
                        showAccountDetail = false
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(context.getString(R.string.copy_link).replace("link", "token"))
                }
                TextButton(
                    onClick = { showAccountDetail = false },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.close_dialog))
                }
            },
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (accountName.isNotBlank()) {
                    Text("Account", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(2.dp))
                    Text(accountName, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                }
                if (userId != 0L) {
                    Text("User ID", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(2.dp))
                    Text(userId.toString(), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                }
                if (countryCode.isNotBlank()) {
                    Text("Country", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(2.dp))
                    Text(countryCode, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                }
                Text("Access token", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(2.dp))
                Text(accessToken, style = MaterialTheme.typography.bodyMedium, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                Spacer(Modifier.height(10.dp))
                Text("Status", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(2.dp))
                Text(
                    text =
                        when {
                            needsRelogin -> stringResource(R.string.tidal_account_relogin_required)
                            accountConfigured -> stringResource(R.string.tidal_account_active)
                            else -> stringResource(R.string.tidal_instance_unknown)
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color =
                        when {
                            needsRelogin -> MaterialTheme.colorScheme.error
                            accountConfigured -> Color(0xFF4FC3F7)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
                Spacer(Modifier.height(10.dp))
                Text("Subscription", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(2.dp))
                Text(subscription.name.lowercase(), style = MaterialTheme.typography.bodyMedium)
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
                                    copyToClipboard(context, "Tidal instance", listOf(instance))
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

    if (showAddDialog) {
        TextFieldDialog(
            icon = { Icon(painterResource(R.drawable.add), null) },
            title = { Text(stringResource(R.string.tidal_add_instance)) },
            placeholder = { Text(stringResource(R.string.tidal_instance_url_hint)) },
            isInputValid = { TidalAudioProvider.normalizeInstanceUrl(it) != null },
            onDone = { raw ->
                val normalized = TidalAudioProvider.normalizeInstanceUrl(raw)
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

    if (showBulkDialog) {
        TextFieldDialog(
            icon = { Icon(painterResource(R.drawable.playlist_add), null) },
            title = { Text(stringResource(R.string.source_bulk_add)) },
            placeholder = { Text(stringResource(R.string.source_bulk_hint)) },
            singleLine = false,
            isInputValid = { it.isNotBlank() },
            onDone = { raw ->
                val parsed = SourceInputParsing.parseUrls(raw).mapNotNull { TidalAudioProvider.normalizeInstanceUrl(it) }
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

    if (showTokenDialog) {
        TextFieldDialog(
            icon = { Icon(painterResource(R.drawable.token), null) },
            title = { Text(stringResource(R.string.sources_tidal_token_title)) },
            placeholder = { Text(stringResource(R.string.sources_tidal_token_label)) },
            // A Tidal refresh token is a live credential — mask it, as the Qobuz token fields and
            // the Apple Music token sheet already do.
            masked = true,
            isInputValid = { it.trim().split('.').size == 3 },
            onDone = { rawToken ->
                val refreshToken = rawToken.trim()
                coroutineScope.launch {
                    val refreshed =
                        withContext(Dispatchers.IO) {
                            TidalAccountManager
                                .refreshAccessToken(refreshToken, TidalAccountManager.FLOW_OAUTH)
                                ?.let { it to TidalAccountManager.FLOW_OAUTH }
                                ?: TidalAccountManager
                                    .refreshAccessToken(refreshToken, TidalAccountManager.FLOW_PKCE)
                                    ?.let { it to TidalAccountManager.FLOW_PKCE }
                        }

                    if (refreshed == null) {
                        toast(context.getString(R.string.sources_tidal_token_failed))
                        return@launch
                    }

                    val (token, flow) = refreshed
                    context.dataStore.edit { prefs ->
                        prefs[TidalAccessTokenKey] = token.accessToken
                        prefs[TidalRefreshTokenKey] = token.refreshToken ?: refreshToken
                        prefs[TidalTokenExpiryKey] = token.expiresAtMillis
                        prefs[TidalAuthFlowKey] = flow
                        token.userId?.let { prefs[TidalUserIdKey] = it }
                        token.countryCode?.let { prefs[TidalCountryCodeKey] = it }
                        prefs[TidalNeedsReloginKey] = false
                    }
                    toast(context.getString(R.string.sources_tidal_token_verified))
                }
            },
            onDismiss = { showTokenDialog = false },
            extraContent = {
                Text(
                    text = stringResource(R.string.sources_tidal_token_help),
                    style = MaterialTheme.typography.bodySmall,
                )
            },
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tidal_integration)) },
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
            PreferenceGroup(
                modifier = positions.modifierFor("tidal_account"),
                title = stringResource(R.string.tidal_account),
            ) {
                if (accountConfigured) {
                    item {
                        PreferenceEntry(
                            modifier = positions.modifierFor("tidal_account_connected"),
                            title = {
                                Text(stringResource(R.string.tidal_account_connected, accountName.ifBlank { "Tidal" }))
                            },
                            description =
                                when (subscription) {
                                    TidalSubscriptionStatus.PREMIUM ->
                                        stringResource(R.string.tidal_account_premium)
                                    TidalSubscriptionStatus.FREE ->
                                        stringResource(R.string.tidal_account_free)
                                    TidalSubscriptionStatus.UNKNOWN ->
                                        stringResource(R.string.tidal_account_checking)
                                },
                            icon = {
                                Icon(
                                    painterResource(
                                        if (subscription == TidalSubscriptionStatus.FREE) {
                                            R.drawable.error
                                        } else {
                                            R.drawable.token
                                        },
                                    ),
                                    null,
                                )
                            },
                            onClick = { showAccountDetail = true },
                        )
                    }

                    if (needsRelogin) {
                        item {
                            PreferenceEntry(
                                modifier = positions.modifierFor("tidal_reconnect"),
                                title = { Text(stringResource(R.string.tidal_reconnect)) },
                                icon = { Icon(painterResource(R.drawable.error), null) },
                                onClick = { navController.navigate(TIDAL_LOGIN_ROUTE) },
                            )
                        }
                    }

                    item {
                        PreferenceEntry(
                            modifier = positions.modifierFor("tidal_disconnect"),
                            title = { Text(stringResource(R.string.tidal_disconnect)) },
                            icon = { Icon(painterResource(R.drawable.logout), null) },
                            onClick = {
                                coroutineScope.launch {
                                    context.dataStore.edit { prefs ->
                                        prefs.remove(TidalAccessTokenKey)
                                        prefs.remove(TidalRefreshTokenKey)
                                        prefs.remove(TidalTokenExpiryKey)
                                        prefs.remove(TidalAccountNameKey)
                                        prefs.remove(TidalAuthFlowKey)
                                        prefs.remove(TidalCountryCodeKey)
                                        prefs.remove(TidalUserIdKey)
                                        prefs.remove(TidalNeedsReloginKey)
                                        prefs[TidalSubscriptionKey] = TidalSubscriptionStatus.UNKNOWN.name
                                    }
                                }
                            },
                        )
                    }
                } else {
                    item {
                        PreferenceEntry(
                            modifier = positions.modifierFor("tidal_login_web"),
                            title = { Text(stringResource(R.string.tidal_login_web)) },
                            icon = { Icon(painterResource(R.drawable.token), null) },
                            onClick = { navController.navigate(TIDAL_LOGIN_ROUTE) },
                        )
                    }

                    item {
                        PreferenceEntry(
                            modifier = positions.modifierFor("tidal_paste_token"),
                            title = { Text(stringResource(R.string.sources_paste_token)) },
                            description = stringResource(R.string.sources_tidal_token_help),
                            icon = { Icon(painterResource(R.drawable.token), null) },
                            onClick = { showTokenDialog = true },
                        )
                    }
                }
            }

            PreferenceGroup(
                modifier = positions.modifierFor("tidal_instances"),
                title = stringResource(R.string.tidal_instances),
            ) {
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
                        modifier = positions.modifierFor("source_manage_instances"),
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

                item {
                    PreferenceEntry(
                        title = {
                            Text(
                                if (testingInstances) {
                                    stringResource(R.string.tidal_checking_instances)
                                } else {
                                    stringResource(R.string.tidal_check_instances)
                                },
                            )
                        },
                        icon = { Icon(painterResource(R.drawable.sync), null) },
                        isEnabled = !testingInstances,
                        onClick = { runInstanceTest() },
                    )
                }

                effectiveInstances.forEach { instance ->
                    item(visible = showInstanceManagement) {
                        val status = healthStatus[instance]
                        // Status colors: online = light blue, deprecated/preview-only = purple,
                        // failed = grey. Untested falls back to the muted default.
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
                        modifier = positions.modifierFor("tidal_add_instance"),
                        title = { Text(stringResource(R.string.tidal_add_instance)) },
                        icon = { Icon(painterResource(R.drawable.add), null) },
                        onClick = { showAddDialog = true },
                    )
                }

                item(visible = showInstanceManagement) {
                    PreferenceEntry(
                        modifier = positions.modifierFor("source_bulk_add"),
                        title = { Text(stringResource(R.string.source_bulk_add)) },
                        description = stringResource(R.string.source_bulk_hint),
                        icon = { Icon(painterResource(R.drawable.playlist_add), null) },
                        onClick = { showBulkDialog = true },
                    )
                }

                item(visible = showInstanceManagement) {
                    PreferenceEntry(
                        modifier = positions.modifierFor("source_copy_online"),
                        title = { Text(stringResource(R.string.source_copy_online)) },
                        icon = { Icon(painterResource(R.drawable.copy), null) },
                        onClick = { copyOnlineInstances() },
                    )
                }

                item(visible = showInstanceManagement) {
                    PreferenceEntry(
                        modifier = positions.modifierFor("source_remove_dead"),
                        title = { Text(stringResource(R.string.source_remove_dead)) },
                        icon = { Icon(painterResource(R.drawable.delete), null) },
                        onClick = {
                            removeInstancesWithStatus(setOf(TidalAudioProvider.InstanceHealth.UNREACHABLE))
                        },
                    )
                }

                item(visible = showInstanceManagement) {
                    PreferenceEntry(
                        modifier = positions.modifierFor("source_remove_deprecated"),
                        title = { Text(stringResource(R.string.source_remove_deprecated)) },
                        icon = { Icon(painterResource(R.drawable.delete), null) },
                        onClick = {
                            removeInstancesWithStatus(setOf(TidalAudioProvider.InstanceHealth.PREVIEW_ONLY))
                        },
                    )
                }

                item(visible = showInstanceManagement) {
                    PreferenceEntry(
                        modifier = positions.modifierFor("tidal_reset_instances"),
                        title = { Text(stringResource(R.string.tidal_reset_instances)) },
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
