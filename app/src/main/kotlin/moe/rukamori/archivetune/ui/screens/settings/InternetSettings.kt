/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.settings

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.*
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.ui.component.*
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.ProxyUtils
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Proxy
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess
import androidx.compose.foundation.layout.asPaddingValues

@Composable
fun InternetWarningBox(modifier: Modifier = Modifier) {
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(SettingsDimensions.BannerCardCornerRadius),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(SettingsDimensions.BannerIconSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.error),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(SettingsDimensions.BannerIconInnerSize),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.internet_warning_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = stringResource(R.string.internet_warning_doh),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                )
                Text(
                    text = stringResource(R.string.internet_warning_proxy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun InternetSettings(navController: NavController, scrollTo: String? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val (dnsOverHttpsEnabled, onDnsOverHttpsEnabledChange) = rememberPreference(key = EnableDnsOverHttpsKey, defaultValue = false)
    val (dnsProvider, onDnsProviderChange) = rememberPreference(key = DnsOverHttpsProviderKey, defaultValue = "Cloudflare")
    val (customDnsUrl, onCustomDnsUrlChange) = rememberPreference(key = stringPreferencesKey("customDnsUrl"), defaultValue = "https://")
    val (proxyEnabled, onProxyEnabledChange) = rememberPreference(key = ProxyEnabledKey, defaultValue = false)
    val (proxyType, onProxyTypeChange) = rememberEnumPreference(key = ProxyTypeKey, defaultValue = Proxy.Type.HTTP)
    val (proxyHost, onProxyHostChange) = rememberPreference(key = ProxyHostKey, defaultValue = "")
    val (proxyPort, onProxyPortChange) = rememberPreference(key = ProxyPortKey, defaultValue = 8080)
    val (proxyUsername, onProxyUsernameChange) = rememberPreference(key = ProxyUsernameKey, defaultValue = "")
    val (proxyPassword, onProxyPasswordChange) = rememberPreference(key = ProxyPasswordKey, defaultValue = "")
    val (streamBypassProxy, onStreamBypassProxyChange) = rememberPreference(key = StreamBypassProxyKey, defaultValue = false)

    val (ipRotationEnabled, onIpRotationEnabledChange) = rememberPreference(key = IpRotationEnabledKey, defaultValue = false)
    var loadingIpRotation by remember { mutableStateOf(false) }
    var refreshingIpRotation by remember { mutableStateOf(false) }
    val activeProxyCount by YouTube.ipRotationActiveCount.collectAsStateWithLifecycle()

    val (ytMusicRegion, onYtMusicRegionChange) =
        rememberPreference(key = YouTubeMusicRegionKey, defaultValue = SYSTEM_DEFAULT)
    val ytRegionValues = remember { listOf(SYSTEM_DEFAULT) + CountryCodeToName.keys.toList() }

    var testingProxy by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }

    val dnsProviders = remember { listOf("Cloudflare", "Google", "AdGuard", "Quad9", "Custom") }
    val proxyTypes = remember { listOf(Proxy.Type.HTTP, Proxy.Type.SOCKS) }
    val ipRotationDescription =
        when {
            loadingIpRotation -> stringResource(R.string.ip_rotation_loading)
            refreshingIpRotation -> stringResource(R.string.ip_rotation_refreshing)
            ipRotationEnabled -> stringResource(R.string.ip_rotation_active_proxies, activeProxyCount)
            else -> stringResource(R.string.ip_rotation_desc)
        }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.internet)) },
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
                .verticalScroll(scrollState)
                .padding(bottom = playerAwareBottomPadding + SettingsDimensions.ScreenBottomPadding),
        ) {
            InternetWarningBox()

            PreferenceGroup(
                modifier = positions.modifierFor("yt_music_region"),
                title = stringResource(R.string.youtube_music_region),
            ) {
                item {
                    ListPreference(
                        title = { Text(stringResource(R.string.youtube_music_region)) },
                        description = stringResource(R.string.youtube_music_region_desc),
                        icon = { Icon(painterResource(R.drawable.location_on), null) },
                        selectedValue = ytMusicRegion,
                        values = ytRegionValues,
                        valueText = { code ->
                            if (code == SYSTEM_DEFAULT) {
                                stringResource(R.string.system_default)
                            } else {
                                CountryCodeToName.getOrElse(code) { code }
                            }
                        },
                        onValueSelected = { newValue ->
                            // Apply immediately — `gl` is in the JSON body of every YT Music
                            // request (browse/search/player/next/suggestions/queue), so the
                            // next call will use the new region. `SYSTEM_DEFAULT` falls back
                            // to the device locale (or, if that's not in CountryCodeToName,
                            // to "US"), matching the App.kt startup logic.
                            //
                            // We also clear visitorData: that token is minted by YouTube with
                            // an implicit region baked in (derived from the IP/locale at the
                            // time of the sw.js_data scrape). Without rotation, YouTube can
                            // keep serving content pinned to the *old* region for personalized
                            // endpoints like FEmusic_home, even though context.client.gl says
                            // otherwise. Setting it to null forces the next request to re-fetch
                            // a fresh, region-pinned token.
                            //
                            // The regionSpooferActive flag forces region-sensitive endpoints
                            // (home, search, charts, trending, new releases, moods & genres,
                            // explore) to go ANONYMOUS — no cookie, no dataSyncId, no
                            // visitorData in the body or X-Goog-Visitor-Id header. Without
                            // this, a logged-in user's account region overrides `gl` and
                            // YouTube keeps serving content from the account's home country,
                            // defeating the spoofer. Login-required endpoints (library,
                            // playlists, history) are unaffected and keep using the session.
                            //
                            // The locale assignment emits YouTube.localeChanges, which
                            // HomeViewModel collects to trigger an immediate home-feed refresh
                            // (no manual pull-to-refresh required).
                            //
                            // === App restart ===
                            // The user explicitly requested that changing the region
                            // automatically restarts the app. The reason: even though the
                            // in-process YouTube state is updated synchronously above, a
                            // bunch of long-lived caches and view-models (HomeViewModel's
                            // queued home-feed refresh, BrowseViewModel's mood/genre chips,
                            // SearchDiscoveryViewModel's suggestion cache, the Coil image
                            // cache for region-pinned thumbnails, the queued
                            // `YouTube.localeChanges` collection, etc.) hold snapshots from
                            // the *old* region and only refresh on the next manual pull.
                            // Restarting the process guarantees every region-sensitive
                            // subsystem comes back cold against the new region — which is
                            // exactly what users expect when they pick "Japan" in the
                            // region picker: a Japan home feed, Japan search, Japan recs,
                            // all at once, with no stale US/EU content lingering in any
                            // tab.
                            val deviceLocale = Locale.getDefault()
                            val resolvedGl =
                                newValue.takeIf { it != SYSTEM_DEFAULT }
                                    ?: deviceLocale.country.takeIf { it in CountryCodeToName }
                                    ?: "US"
                            val spooferActive = newValue != SYSTEM_DEFAULT
                            YouTube.regionSpooferActive = spooferActive
                            YouTube.visitorData = null
                            YouTube.locale = YouTube.locale.copy(gl = resolvedGl)
                            onYtMusicRegionChange(newValue)

                            // Schedule a process restart on a background coroutine so the
                            // preference write (above) and the Toast land before we kill
                            // the process. 400ms is enough for DataStore to flush and for
                            // the Toast to animate in.
                            scope.launch {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.youtube_music_region_restarting),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                                delay(400)
                                withContext(Dispatchers.IO) {
                                    val launchIntent =
                                        context.packageManager.getLaunchIntentForPackage(context.packageName)
                                    if (launchIntent != null) {
                                        launchIntent.addFlags(
                                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                                Intent.FLAG_ACTIVITY_CLEAR_TASK,
                                        )
                                        context.startActivity(launchIntent)
                                    }
                                    exitProcess(0)
                                }
                            }
                        },
                    )
                }
            }

            PreferenceGroup(
                modifier = positions.modifierFor("enable_tor"),
                title = stringResource(R.string.dns_over_https),
            ) {
                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.dns_over_https)) },
                        description = stringResource(R.string.dns_over_https_desc),
                        icon = { Icon(painterResource(R.drawable.security), null) },
                        checked = dnsOverHttpsEnabled,
                        onCheckedChange = onDnsOverHttpsEnabledChange,
                    )
                }

                item(visible = dnsOverHttpsEnabled) {
                    ListPreference(
                        title = { Text(stringResource(R.string.dns_provider)) },
                        icon = { Icon(painterResource(R.drawable.website), null) },
                        selectedValue = dnsProvider,
                        values = dnsProviders,
                        valueText = { it },
                        onValueSelected = onDnsProviderChange,
                    )
                }

                item(visible = dnsOverHttpsEnabled && dnsProvider == "Custom") {
                    EditTextPreference(
                        title = { Text(stringResource(R.string.dns_custom_url)) },
                        value = customDnsUrl,
                        onValueChange = onCustomDnsUrlChange,
                    )
                }
            }

            PreferenceGroup(
                modifier = positions.modifierFor("proxy_settings"),
                title = stringResource(R.string.proxy),
            ) {
                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.enable_proxy)) },
                        icon = { Icon(painterResource(R.drawable.wifi_proxy), null) },
                        checked = proxyEnabled,
                        onCheckedChange = {
                            onProxyEnabledChange(it)
                            ProxyUtils.applyYouTubeProxy(it, proxyType, proxyHost, proxyPort, proxyUsername, proxyPassword)
                        },
                    )
                }

                item(visible = proxyEnabled) {
                    ListPreference(
                        title = { Text(stringResource(R.string.proxy_type)) },
                        selectedValue = proxyType,
                        values = proxyTypes,
                        valueText = { it.name },
                        onValueSelected = {
                            onProxyTypeChange(it)
                            ProxyUtils.applyYouTubeProxy(proxyEnabled, it, proxyHost, proxyPort, proxyUsername, proxyPassword)
                        },
                    )
                }

                item(visible = proxyEnabled) {
                    EditTextPreference(
                        title = { Text(stringResource(R.string.proxy_host)) },
                        value = proxyHost,
                        onValueChange = {
                            onProxyHostChange(it)
                            ProxyUtils.applyYouTubeProxy(proxyEnabled, proxyType, it, proxyPort, proxyUsername, proxyPassword)
                        },
                    )
                }

                item(visible = proxyEnabled) {
                    NumberEditTextPreference(
                        title = { Text(stringResource(R.string.proxy_port)) },
                        value = proxyPort,
                        onValueChange = {
                            onProxyPortChange(it)
                            ProxyUtils.applyYouTubeProxy(proxyEnabled, proxyType, proxyHost, it, proxyUsername, proxyPassword)
                        },
                        isInputValid = { it.toIntOrNull() in 1..65535 },
                    )
                }
            }

            if (proxyEnabled) {
                PreferenceGroup(title = stringResource(R.string.proxy_auth)) {
                    item {
                        EditTextPreference(
                            title = { Text(stringResource(R.string.proxy_username)) },
                            value = proxyUsername,
                            onValueChange = {
                                onProxyUsernameChange(it)
                                ProxyUtils.applyYouTubeProxy(proxyEnabled, proxyType, proxyHost, proxyPort, it, proxyPassword)
                            },
                        )
                    }

                    item {
                        EditTextPreference(
                            title = { Text(stringResource(R.string.proxy_password)) },
                            value = proxyPassword,
                            onValueChange = {
                                onProxyPasswordChange(it)
                                ProxyUtils.applyYouTubeProxy(proxyEnabled, proxyType, proxyHost, proxyPort, proxyUsername, it)
                            },
                        )
                    }

                    item {
                        SwitchPreference(
                            title = { Text(stringResource(R.string.stream_bypass_proxy)) },
                            description = stringResource(R.string.stream_bypass_proxy_desc),
                            icon = { Icon(painterResource(R.drawable.wifi_proxy), null) },
                            checked = streamBypassProxy,
                            onCheckedChange = {
                                onStreamBypassProxyChange(it)
                                YouTube.streamBypassProxy = it
                            },
                        )
                    }

                    item {
                        PreferenceEntry(
                            title = { Text(stringResource(R.string.test_proxy_connection)) },
                            icon = { Icon(painterResource(R.drawable.check), null) },
                            onClick = {
                                if (testingProxy) return@PreferenceEntry
                                scope.launch(Dispatchers.IO) {
                                    testingProxy = true
                                    try {
                                        val proxy = ProxyUtils.createProxyOrNull(proxyType, proxyHost, proxyPort)
                                        if (proxy == null) {
                                            testResult =
                                                context.getString(
                                                    R.string.proxy_connection_failed,
                                                    context.getString(R.string.proxy_connection_invalid_configuration),
                                                )
                                            return@launch
                                        }
                                        val clientBuilder =
                                            OkHttpClient
                                                .Builder()
                                                .proxy(proxy)
                                                .connectTimeout(10, TimeUnit.SECONDS)
                                                .readTimeout(10, TimeUnit.SECONDS)

                                        if (proxyUsername.isNotBlank() && proxyPassword.isNotBlank()) {
                                            clientBuilder.proxyAuthenticator { _, response ->
                                                val credential = okhttp3.Credentials.basic(proxyUsername, proxyPassword)
                                                response.request
                                                    .newBuilder()
                                                    .header("Proxy-Authorization", credential)
                                                    .build()
                                            }
                                        }

                                        val client = clientBuilder.build()
                                        val request =
                                            Request
                                                .Builder()
                                                .url("https://music.youtube.com/generate_204")
                                                .build()
                                        client.newCall(request).execute().use { response ->
                                            testResult =
                                                if (response.isSuccessful || response.code == 204) {
                                                    context.getString(R.string.proxy_connection_success)
                                                } else {
                                                    context.getString(R.string.proxy_connection_failed, "HTTP ${response.code}")
                                                }
                                        }
                                    } catch (e: Exception) {
                                        testResult =
                                            context.getString(
                                                R.string.proxy_connection_failed,
                                                e.message ?: context.getString(R.string.error_unknown),
                                            )
                                    } finally {
                                        testingProxy = false
                                    }
                                }
                            },
                        )
                    }
                }
            }

            PreferenceGroup(
                modifier = positions.modifierFor("download_speed_limit"),
                title = stringResource(R.string.ip_rotation),
            ) {
                item {
                    IpRotationPreference(
                        title = { Text(stringResource(R.string.ip_rotation)) },
                        description = ipRotationDescription,
                        icon = { Icon(painterResource(R.drawable.wifi_proxy), null) },
                        checked = ipRotationEnabled,
                        isBusy = loadingIpRotation || refreshingIpRotation,
                        onCheckedChange = { enabled ->
                            onIpRotationEnabledChange(enabled)
                            if (enabled) {
                                scope.launch {
                                    loadingIpRotation = true
                                    try {
                                        YouTube.enableIpRotation()
                                    } catch (_: Exception) {
                                        onIpRotationEnabledChange(false)
                                    } finally {
                                        loadingIpRotation = false
                                    }
                                }
                            } else {
                                YouTube.disableIpRotation()
                            }
                        },
                        onRefresh = refreshIp@{
                            if (loadingIpRotation || refreshingIpRotation) return@refreshIp
                            scope.launch {
                                refreshingIpRotation = true
                                try {
                                    YouTube.refreshIpRotation()
                                } catch (_: Exception) {
                                } finally {
                                    refreshingIpRotation = false
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    if (testingProxy) {
        DefaultDialog(
            onDismiss = { },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularWavyProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.testing_proxy_connection))
        }
    }

    if (testResult != null) {
        ActionPromptDialog(
            title = stringResource(R.string.test_proxy_connection),
            onDismiss = { testResult = null },
            onConfirm = { testResult = null },
            content = {
                Text(testResult!!)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun IpRotationPreference(
    title: @Composable () -> Unit,
    description: String,
    icon: @Composable () -> Unit,
    checked: Boolean,
    isBusy: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
) {
    PreferenceEntry(
        title = title,
        description = description,
        icon = icon,
        trailingContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (checked) {
                    if (isBusy) {
                        CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        FilledTonalIconButton(onClick = onRefresh) {
                            Icon(
                                painterResource(R.drawable.sync),
                                contentDescription = stringResource(R.string.ip_rotation_refresh),
                            )
                        }
                    }
                }
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    enabled = !isBusy,
                    thumbContent = {
                        AnimatedContent(
                            targetState = checked,
                            transitionSpec = {
                                fadeIn(tween(100)) togetherWith fadeOut(tween(100))
                            },
                            label = "ipRotationSwitchThumbIcon",
                        ) { isChecked ->
                            Icon(
                                painter =
                                    painterResource(
                                        id = if (isChecked) R.drawable.check else R.drawable.close,
                                    ),
                                contentDescription = null,
                                modifier = Modifier.size(SwitchDefaults.IconSize),
                            )
                        }
                    },
                    colors =
                        SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            checkedIconColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                            uncheckedIconColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                )
            }
        },
        onClick =
            if (isBusy) {
                null
            } else {
                { onCheckedChange(!checked) }
            },
    )
}
