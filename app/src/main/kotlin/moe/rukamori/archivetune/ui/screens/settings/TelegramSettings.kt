/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Telegram channel streaming settings: the user's own API credentials (my.telegram.org),
 * account sign-in state, and the channel browser entry point.
 */

package moe.rukamori.archivetune.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.TelegramAccountNameKey
import moe.rukamori.archivetune.constants.TelegramAccountPhoneKey
import moe.rukamori.archivetune.constants.TelegramApiHashKey
import moe.rukamori.archivetune.constants.TelegramApiIdKey
import moe.rukamori.archivetune.constants.TelegramLosslessOnlyKey
import moe.rukamori.archivetune.telegram.TelegramAuthState
import moe.rukamori.archivetune.telegram.TelegramClient
import moe.rukamori.archivetune.ui.component.DefaultDialog
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.InfoLabel
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.component.TextFieldDialog
import moe.rukamori.archivetune.ui.screens.TELEGRAM_BROWSE_ROUTE
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramSettings(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val (apiId, onApiIdChange) = rememberPreference(TelegramApiIdKey, "")
    val (apiHash, onApiHashChange) = rememberPreference(TelegramApiHashKey, "")
    val (accountName, onAccountNameChange) = rememberPreference(TelegramAccountNameKey, "")
    val (accountPhone, onAccountPhoneChange) = rememberPreference(TelegramAccountPhoneKey, "")
    val (losslessOnly, onLosslessOnlyChange) = rememberPreference(TelegramLosslessOnlyKey, true)

    val authState by TelegramClient.authState.collectAsState()
    val hasCredentials = apiId.trim().toIntOrNull() != null && apiHash.isNotBlank()
    val isReady = authState is TelegramAuthState.Ready

    var showApiIdDialog by remember { mutableStateOf(false) }
    var showApiHashDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(hasCredentials) {
        if (hasCredentials) {
            TelegramClient.ensureStarted(context)
        }
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

    if (showApiIdDialog) {
        TextFieldDialog(
            title = { Text(stringResource(R.string.telegram_api_id)) },
            initialTextFieldValue = TextFieldValue(apiId),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isInputValid = { it.trim().toIntOrNull() != null },
            onDone = { onApiIdChange(it.trim()) },
            onDismiss = { showApiIdDialog = false },
        )
    }

    if (showApiHashDialog) {
        TextFieldDialog(
            title = { Text(stringResource(R.string.telegram_api_hash)) },
            initialTextFieldValue = TextFieldValue(apiHash),
            isInputValid = { it.trim().isNotEmpty() },
            onDone = { onApiHashChange(it.trim()) },
            onDismiss = { showApiHashDialog = false },
        )
    }

    if (showLogoutDialog) {
        DefaultDialog(
            onDismiss = { showLogoutDialog = false },
            content = {
                Text(stringResource(R.string.telegram_logout_confirm))
            },
            buttons = {
                androidx.compose.material3.TextButton(onClick = { showLogoutDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
                androidx.compose.material3.TextButton(
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
        Column(
            Modifier
                .padding(top = innerPadding.calculateTopPadding())
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                ).verticalScroll(rememberScrollState()),
        ) {
            PreferenceGroup(title = stringResource(R.string.telegram_api_credentials)) {
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.telegram_api_id)) },
                        description = if (apiId.isBlank()) null else apiId,
                        icon = { Icon(painterResource(R.drawable.token), contentDescription = null) },
                        onClick = { showApiIdDialog = true },
                    )
                }
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.telegram_api_hash)) },
                        description = if (apiHash.isBlank()) null else "••••••••",
                        icon = { Icon(painterResource(R.drawable.token), contentDescription = null) },
                        onClick = { showApiHashDialog = true },
                    )
                }
                item {
                    InfoLabel(stringResource(R.string.telegram_api_credentials_info))
                }
            }

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
                            description =
                                if (hasCredentials) {
                                    null
                                } else {
                                    stringResource(R.string.telegram_credentials_required)
                                },
                            icon = { Icon(painterResource(R.drawable.provider_telegram), contentDescription = null) },
                            isEnabled = hasCredentials,
                            onClick = {
                                if (TelegramClient.ensureStarted(context)) {
                                    navController.navigate(TELEGRAM_LOGIN_ROUTE)
                                } else {
                                    Toast
                                        .makeText(
                                            context,
                                            R.string.telegram_credentials_required,
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                }
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
        }
    }
}
