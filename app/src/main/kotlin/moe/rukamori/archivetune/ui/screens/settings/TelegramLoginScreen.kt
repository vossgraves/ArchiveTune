/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Telegram account sign-in: a stepped form (phone number → login code → optional 2FA password)
 * driven directly by TDLib's authorization state machine via TelegramClient.authState.
 */

package moe.rukamori.archivetune.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.telegram.TelegramApiException
import moe.rukamori.archivetune.telegram.TelegramAuthState
import moe.rukamori.archivetune.telegram.TelegramClient
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.utils.backToMain

const val TELEGRAM_LOGIN_ROUTE = "settings/telegram/login"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramLoginScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val authState by TelegramClient.authState.collectAsState()
    var phoneNumber by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (!TelegramClient.ensureStarted(context)) {
            Toast.makeText(context, R.string.telegram_credentials_required, Toast.LENGTH_SHORT).show()
            navController.navigateUp()
        }
    }

    LaunchedEffect(authState) {
        // Every state transition invalidates any in-flight error/busy indicator.
        busy = false
        if (authState is TelegramAuthState.Ready) {
            Toast.makeText(context, R.string.telegram_login_success, Toast.LENGTH_SHORT).show()
            navController.navigateUp()
        }
    }

    fun submit(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        errorText = null
        coroutineScope.launch {
            try {
                block()
            } catch (e: Exception) {
                errorText =
                    when (e) {
                        is TelegramApiException -> e.message
                        else -> e.message ?: e.javaClass.simpleName
                    }
            } finally {
                busy = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.telegram_login)) },
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
            modifier =
                Modifier
                    .padding(top = innerPadding.calculateTopPadding())
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                        ),
                    ).verticalScroll(rememberScrollState())
                    .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (val state = authState) {
                is TelegramAuthState.Idle, is TelegramAuthState.Connecting -> {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.telegram_connecting))
                }

                is TelegramAuthState.WaitPhoneNumber -> {
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { phoneNumber = it },
                        label = { Text(stringResource(R.string.telegram_phone_number)) },
                        placeholder = { Text("+15550100") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        enabled = !busy && phoneNumber.isNotBlank(),
                        onClick = { submit { TelegramClient.submitPhoneNumber(phoneNumber) } },
                    ) {
                        Text(stringResource(R.string.telegram_continue))
                    }
                }

                is TelegramAuthState.WaitCode -> {
                    Text(
                        stringResource(R.string.telegram_code_sent, state.phoneNumber),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text(stringResource(R.string.telegram_code)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        enabled = !busy && code.isNotBlank(),
                        onClick = { submit { TelegramClient.submitCode(code) } },
                    ) {
                        Text(stringResource(R.string.telegram_continue))
                    }
                    TextButton(
                        enabled = !busy,
                        onClick = { submit { TelegramClient.resendCode() } },
                    ) {
                        Text(stringResource(R.string.telegram_resend_code))
                    }
                }

                is TelegramAuthState.WaitPassword -> {
                    state.passwordHint?.let { hint ->
                        Text(
                            stringResource(R.string.telegram_password_hint_label, hint),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.telegram_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        enabled = !busy && password.isNotEmpty(),
                        onClick = { submit { TelegramClient.submitPassword(password) } },
                    ) {
                        Text(stringResource(R.string.telegram_continue))
                    }
                }

                is TelegramAuthState.Ready, is TelegramAuthState.LoggingOut -> {
                    CircularProgressIndicator()
                }

                is TelegramAuthState.Unsupported -> {
                    Text(
                        stringResource(R.string.telegram_auth_unsupported, state.stateName),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            if (busy) {
                Spacer(Modifier.height(4.dp))
                CircularProgressIndicator()
            }

            errorText?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
