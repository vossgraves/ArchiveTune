/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.morideobfuscator.CipherRuntimeStatus
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.viewmodels.ChiperSettingsUiData
import moe.rukamori.archivetune.viewmodels.ChiperSettingsUiState
import moe.rukamori.archivetune.viewmodels.ChiperSettingsViewModel
import java.text.DateFormat
import java.util.Date

@Composable
fun ChiperSettings(
    navController: NavController,
    viewModel: ChiperSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.events.collect { event ->
            snackbarHostState.showSnackbar(
                message = navController.context.getString(event.messageRes),
            )
        }
    }

    ChiperSettingsContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onNavigateUp = navController::navigateUp,
        onNavigateToMain = navController::backToMain,
        onRefresh = viewModel::refresh,
    )
}

@Composable
private fun ChiperSettingsContent(
    state: ChiperSettingsUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateUp: () -> Unit,
    onNavigateToMain: () -> Unit,
    onRefresh: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mori_cipher_settings_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateUp,
                        onLongClick = onNavigateToMain,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when (state) {
            ChiperSettingsUiState.Loading -> {
                CipherLoadingState(
                    modifier =
                        Modifier
                            .padding(innerPadding)
                            .consumeWindowInsets(innerPadding)
                            .windowInsetsPadding(
                                LocalPlayerAwareWindowInsets.current.only(
                                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                                ),
                            ),
                )
            }

            ChiperSettingsUiState.Empty -> {
                CipherMessageState(
                    title = stringResource(R.string.mori_cipher_empty_title),
                    description = stringResource(R.string.mori_cipher_empty_description),
                    onRefresh = onRefresh,
                    modifier =
                        Modifier
                            .padding(innerPadding)
                            .consumeWindowInsets(innerPadding)
                            .windowInsetsPadding(
                                LocalPlayerAwareWindowInsets.current.only(
                                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                                ),
                            ),
                )
            }

            is ChiperSettingsUiState.Error -> {
                CipherMessageState(
                    title = stringResource(R.string.mori_cipher_error_title),
                    description = stringResource(state.messageRes),
                    onRefresh = onRefresh,
                    modifier =
                        Modifier
                            .padding(innerPadding)
                            .consumeWindowInsets(innerPadding)
                            .windowInsetsPadding(
                                LocalPlayerAwareWindowInsets.current.only(
                                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                                ),
                            ),
                )
            }

            is ChiperSettingsUiState.Success -> {
                CipherSettingsSuccess(
                    data = state.data,
                    onRefresh = onRefresh,
                    contentPadding =
                        PaddingValues(
                            top = innerPadding.calculateTopPadding(),
                            bottom = SettingsDimensions.ScreenBottomPadding,
                        ),
                    modifier =
                        Modifier
                            .consumeWindowInsets(innerPadding)
                            .windowInsetsPadding(
                                LocalPlayerAwareWindowInsets.current.only(
                                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                                ),
                            ),
                )
            }
        }
    }
}

@Composable
private fun CipherSettingsSuccess(
    data: ChiperSettingsUiData,
    onRefresh: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val lastUpdatedText =
        remember(data.lastUpdatedMillis) {
            DateFormat
                .getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(Date(data.lastUpdatedMillis))
        }
    val statusText =
        when (data.status) {
            CipherRuntimeStatus.READY -> stringResource(R.string.mori_cipher_status_ready)
            CipherRuntimeStatus.REFRESHING -> stringResource(R.string.mori_cipher_status_refreshing)
            CipherRuntimeStatus.DEGRADED -> stringResource(R.string.mori_cipher_status_degraded)
            CipherRuntimeStatus.UNINITIALIZED -> stringResource(R.string.mori_cipher_status_uninitialized)
        }
    val manualRefreshDescription =
        data.rateLimitCountdown?.let {
            stringResource(R.string.mori_cipher_rate_limit_countdown, it)
        } ?: stringResource(
            R.string.mori_cipher_refresh_remaining,
            data.remainingManualRefreshes,
        )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "sync_summary", contentType = "summary") {
            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.mori_cipher_next_refresh),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = data.countdown,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    LinearWavyProgressIndicator(
                        progress = { data.intervalProgress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        item(key = "runtime_details", contentType = "preference_group") {
            PreferenceGroup(title = stringResource(R.string.mori_cipher_runtime_section)) {
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.mori_cipher_status)) },
                        description = statusText,
                        icon = { Icon(painterResource(R.drawable.security), null) },
                    )
                }
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.mori_cipher_player_id)) },
                        description = data.playerId,
                        icon = { Icon(painterResource(R.drawable.integration), null) },
                    )
                }
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.mori_cipher_last_updated)) },
                        description = lastUpdatedText,
                        icon = { Icon(painterResource(R.drawable.timer), null) },
                    )
                }
            }
        }

        item(key = "refresh_controls", contentType = "preference_group") {
            PreferenceGroup(title = stringResource(R.string.mori_cipher_update_section)) {
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.mori_cipher_refresh)) },
                        description = manualRefreshDescription,
                        icon = { Icon(painterResource(R.drawable.sync), null) },
                        trailingContent = {
                            if (data.isRefreshing) {
                                LoadingIndicator(modifier = Modifier.size(32.dp))
                            }
                        },
                        isEnabled = !data.isRefreshing,
                        onClick = onRefresh,
                    )
                }
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.mori_cipher_automatic_updates)) },
                        description = stringResource(R.string.mori_cipher_automatic_updates_description),
                        icon = { Icon(painterResource(R.drawable.info), null) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CipherLoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        LoadingIndicator()
    }
}

@Composable
private fun CipherMessageState(
    title: String,
    description: String,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(SettingsDimensions.ScreenHorizontalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.security),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )
        Button(onClick = onRefresh) {
            Text(stringResource(R.string.refresh))
        }
    }
}
