/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Telegram channel streaming settings: account sign-in state (phone + code, no API credentials
 * to enter — they are baked into the build) and the channel browser entry point.
 */

package moe.rukamori.archivetune.ui.screens.settings

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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.TelegramAccountNameKey
import moe.rukamori.archivetune.constants.TelegramAccountPhoneKey
import moe.rukamori.archivetune.constants.TelegramLosslessOnlyKey
import moe.rukamori.archivetune.telegram.TelegramAuthState
import moe.rukamori.archivetune.telegram.TelegramClient
import moe.rukamori.archivetune.ui.component.DefaultDialog
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.screens.TELEGRAM_BOTS_ROUTE
import moe.rukamori.archivetune.ui.screens.TELEGRAM_BROWSE_ROUTE
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.rememberPreference
import androidx.compose.foundation.layout.asPaddingValues

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramSettings(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val (accountName, onAccountNameChange) = rememberPreference(TelegramAccountNameKey, "")
    val (accountPhone, onAccountPhoneChange) = rememberPreference(TelegramAccountPhoneKey, "")
    val (losslessOnly, onLosslessOnlyChange) = rememberPreference(TelegramLosslessOnlyKey, true)

    val authState by TelegramClient.authState.collectAsState()
    val isReady = authState is TelegramAuthState.Ready

    var showLogoutDialog by remember { mutableStateOf(false) }

    // Start TDLib eagerly so the session is restored (or the login step is ready) on entry.
    LaunchedEffect(Unit) {
        TelegramClient.ensureStarted(context)
    }

    LaunchedEffect(isReady) {
        if (isReady && accountName.isBlank()) {
            runCatching { TelegramClient.getMe() }
                .onSuccess { me ->
                    val name = listOfNotNull(me.firstName, me.lastName).joinToString(" ").trim()
                    if (name.isNotBlank()) onAccountNameChange(name)
                    me.phoneNumber?.takeIf(String::isNotBlank)?.let { onAccountPhoneChange("+$it") }
                }
        }
    }

    if (showLogoutDialog) {
        DefaultDialog(
            onDismiss = { showLogoutDialog = false },
            content = {
                Text(stringResource(R.string.telegram_logout_confirm))
            },
            buttons = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        coroutineScope.launch {
                            TelegramClient.logOut()
                            onAccountNameChange("")
                            onAccountPhoneChange("")
                        }
                    },
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.telegram_integration)) },
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
        Column(
            Modifier
                .padding(top = innerPadding.calculateTopPadding())
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Horizontal,
                    ),
                ).verticalScroll(rememberScrollState())
                .padding(bottom = playerAwareBottomPadding + SettingsDimensions.ScreenBottomPadding),
        ) {
            PreferenceGroup(title = stringResource(R.string.telegram_account)) {
                if (isReady) {
                    item {
                        PreferenceEntry(
                            title = {
                                Text(
                                    stringResource(
                                        R.string.telegram_logged_in_as,
                                        accountName.ifBlank { accountPhone.ifBlank { "Telegram" } },
                                    ),
                                )
                            },
                            description = accountPhone.takeIf { it.isNotBlank() && accountName.isNotBlank() },
                            icon = { Icon(painterResource(R.drawable.provider_telegram), contentDescription = null) },
                        )
                    }
                    item {
                        PreferenceEntry(
                            title = { Text(stringResource(R.string.telegram_logout)) },
                            icon = { Icon(painterResource(R.drawable.logout), contentDescription = null) },
                            onClick = { showLogoutDialog = true },
                        )
                    }
                } else {
                    item {
                        PreferenceEntry(
                            title = { Text(stringResource(R.string.telegram_login)) },
                            description = stringResource(R.string.telegram_login_summary),
                            icon = { Icon(painterResource(R.drawable.provider_telegram), contentDescription = null) },
                            onClick = {
                                TelegramClient.ensureStarted(context)
                                navController.navigate(TELEGRAM_LOGIN_ROUTE)
                            },
                        )
                    }
                }
            }

            PreferenceGroup(title = stringResource(R.string.telegram_browse_channels)) {
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.telegram_browse_channels)) },
                        description =
                            if (isReady) {
                                stringResource(R.string.telegram_browse_channels_description)
                            } else {
                                stringResource(R.string.telegram_login_required)
                            },
                        icon = { Icon(painterResource(R.drawable.search), contentDescription = null) },
                        isEnabled = isReady,
                        onClick = { navController.navigate(TELEGRAM_BROWSE_ROUTE) },
                    )
                }
                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.telegram_lossless_only)) },
                        description = stringResource(R.string.telegram_lossless_only_description),
                        icon = { Icon(painterResource(R.drawable.graphic_eq), contentDescription = null) },
                        checked = losslessOnly,
                        onCheckedChange = onLosslessOnlyChange,
                    )
                }
            }

            // Telegram bots — pill/section below the channels group. Lets the user paste a bot's
            // @username once, then re-open it from this list to send song links and get back
            // streamable / downloadable audio.
            PreferenceGroup(title = stringResource(R.string.telegram_bots_title)) {
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.telegram_bots_title)) },
                        description =
                            if (isReady) {
                                stringResource(R.string.telegram_bots_summary)
                            } else {
                                stringResource(R.string.telegram_login_required)
                            },
                        icon = { Icon(painterResource(R.drawable.provider_telegram), contentDescription = null) },
                        isEnabled = isReady,
                        onClick = { navController.navigate(TELEGRAM_BOTS_ROUTE) },
                    )
                }
            }
        }
    }
}
