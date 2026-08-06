/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Telegram account sign-in — a stepped "Connect Telegram" flow (phone number → login code →
 * optional 2FA password) driven by TDLib's authorization state machine. The code step supports
 * editing the phone number and resending the code (gated on TDLib's resend timeout so it never
 * fails with "code can't be resent").
 */

package moe.rukamori.archivetune.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.telegram.TelegramApiException
import moe.rukamori.archivetune.telegram.TelegramAuthState
import moe.rukamori.archivetune.telegram.TelegramClient
import moe.rukamori.archivetune.telegram.TelegramCodeType
import moe.rukamori.archivetune.telegram.composeE164
import moe.rukamori.archivetune.telegram.defaultCallingCode
import moe.rukamori.archivetune.ui.component.FrostedTopAppBar
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.utils.backToMain

const val TELEGRAM_LOGIN_ROUTE = "settings/telegram/login"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramLoginScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val authState by TelegramClient.authState.collectAsState()

    var callingCode by rememberSaveable { mutableStateOf("") }
    var nationalNumber by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var editingPhone by rememberSaveable { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        callingCode = defaultCallingCode(context)
        if (!TelegramClient.ensureStarted(context)) {
            Toast.makeText(context, R.string.telegram_unavailable, Toast.LENGTH_SHORT).show()
            navController.navigateUp()
        }
    }

    LaunchedEffect(authState) {
        busy = false
        when (authState) {
            is TelegramAuthState.Ready -> {
                Toast.makeText(context, R.string.telegram_login_success, Toast.LENGTH_SHORT).show()
                navController.navigateUp()
            }
            // A fresh code was sent (initial or after resend/edit) — leave the edit-phone override.
            is TelegramAuthState.WaitCode -> editingPhone = false
            else -> Unit
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

    val state = authState
    val onPhoneStep =
        state is TelegramAuthState.WaitPhoneNumber ||
            (state is TelegramAuthState.WaitCode && editingPhone) ||
            state is TelegramAuthState.Idle ||
            state is TelegramAuthState.Connecting
    val stepIndex =
        when {
            state is TelegramAuthState.WaitPassword -> 2
            state is TelegramAuthState.WaitCode && !editingPhone -> 1
            else -> 0
        }

    Scaffold(
        topBar = {
            FrostedTopAppBar(
                titleRes = R.string.telegram_login_title,
                onBack = navController::navigateUp,
                onBackLongClick = navController::backToMain,
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
                    .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))
            HeroHeader()
            Spacer(Modifier.height(16.dp))
            StepDots(current = stepIndex, total = 3)
            Spacer(Modifier.height(24.dp))

            if (busy) {
                SendingBanner()
                Spacer(Modifier.height(16.dp))
            }

            when {
                state is TelegramAuthState.Idle || state is TelegramAuthState.Connecting -> {
                    ConnectingCard()
                }

                onPhoneStep -> {
                    PhoneStep(
                        callingCode = callingCode,
                        onCallingCodeChange = { callingCode = it.filter(Char::isDigit) },
                        nationalNumber = nationalNumber,
                        onNationalNumberChange = { nationalNumber = it },
                        busy = busy,
                        onSend = {
                            submit {
                                TelegramClient.submitPhoneNumber(composeE164(callingCode, nationalNumber))
                            }
                        },
                    )
                }

                state is TelegramAuthState.WaitCode -> {
                    CodeStep(
                        state = state,
                        code = code,
                        onCodeChange = { code = it },
                        busy = busy,
                        onVerify = { submit { TelegramClient.submitCode(code) } },
                        onEditPhone = {
                            editingPhone = true
                            code = ""
                            errorText = null
                        },
                        onResend = { submit { TelegramClient.resendCode() } },
                    )
                }

                state is TelegramAuthState.WaitPassword -> {
                    PasswordStep(
                        state = state,
                        password = password,
                        onPasswordChange = { password = it },
                        busy = busy,
                        onContinue = { submit { TelegramClient.submitPassword(password) } },
                    )
                }

                state is TelegramAuthState.Unsupported -> {
                    Text(
                        text = stringResource(R.string.telegram_auth_unsupported, state.stateName),
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }

                else -> CircularProgressIndicator()
            }

            errorText?.let { message ->
                Spacer(Modifier.height(16.dp))
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HeroHeader() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier =
                Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.provider_telegram),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(44.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.telegram_connect_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.telegram_connect_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun StepDots(
    current: Int,
    total: Int,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(total) { index ->
            val active = index == current
            Box(
                modifier =
                    Modifier
                        .size(if (active) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (active) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        ),
            )
        }
    }
}

@Composable
private fun StepCard(
    iconRes: Int,
    title: String,
    description: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        content()
    }
}

@Composable
private fun PhoneStep(
    callingCode: String,
    onCallingCodeChange: (String) -> Unit,
    nationalNumber: String,
    onNationalNumberChange: (String) -> Unit,
    busy: Boolean,
    onSend: () -> Unit,
) {
    StepCard(
        iconRes = R.drawable.phone,
        title = stringResource(R.string.telegram_phone_step_title),
        description = stringResource(R.string.telegram_phone_step_description),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = callingCode,
                onValueChange = onCallingCodeChange,
                prefix = { Text("+") },
                label = { Text(stringResource(R.string.telegram_country_code)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.width(110.dp),
            )
            Spacer(Modifier.width(12.dp))
            OutlinedTextField(
                value = nationalNumber,
                onValueChange = onNationalNumberChange,
                label = { Text(stringResource(R.string.telegram_phone_number_short)) },
                placeholder = { Text("5551234567") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onSend,
            enabled = !busy && nationalNumber.filter(Char::isDigit).length >= 4,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(painterResource(R.drawable.send), contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.telegram_send_code))
        }
    }
}

@Composable
private fun CodeStep(
    state: TelegramAuthState.WaitCode,
    code: String,
    onCodeChange: (String) -> Unit,
    busy: Boolean,
    onVerify: () -> Unit,
    onEditPhone: () -> Unit,
    onResend: () -> Unit,
) {
    // Countdown until a resend is accepted; reset whenever TDLib reports a new WaitCode.
    var secondsLeft by remember(state) { mutableIntStateOf(if (state.canResend) state.resendTimeoutSeconds else 0) }
    LaunchedEffect(state) {
        while (secondsLeft > 0) {
            delay(1000)
            secondsLeft -= 1
        }
    }
    val resendEnabled = !busy && state.canResend && secondsLeft <= 0

    StepCard(
        iconRes = R.drawable.chat,
        title = stringResource(R.string.telegram_code_step_title),
        description =
            when (state.codeType) {
                TelegramCodeType.TELEGRAM_APP ->
                    stringResource(R.string.telegram_code_sent_app, state.phoneNumber)
                TelegramCodeType.SMS ->
                    stringResource(R.string.telegram_code_sent_sms, state.phoneNumber)
                TelegramCodeType.CALL ->
                    stringResource(R.string.telegram_code_sent_call, state.phoneNumber)
                TelegramCodeType.OTHER ->
                    stringResource(R.string.telegram_code_sent, state.phoneNumber)
            },
    ) {
        OutlinedTextField(
            value = code,
            onValueChange = onCodeChange,
            label = { Text(stringResource(R.string.telegram_code)) },
            leadingIcon = { Icon(painterResource(R.drawable.chat), contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onEditPhone, enabled = !busy) {
                Text(stringResource(R.string.telegram_edit_phone))
            }
            TextButton(onClick = onResend, enabled = resendEnabled) {
                Text(
                    if (state.canResend && secondsLeft > 0) {
                        stringResource(R.string.telegram_resend_code_in, secondsLeft)
                    } else {
                        stringResource(R.string.telegram_resend_code)
                    },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onVerify,
            enabled = !busy && code.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(painterResource(R.drawable.check), contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.telegram_verify_code))
        }
    }
}

@Composable
private fun PasswordStep(
    state: TelegramAuthState.WaitPassword,
    password: String,
    onPasswordChange: (String) -> Unit,
    busy: Boolean,
    onContinue: () -> Unit,
) {
    StepCard(
        iconRes = R.drawable.token,
        title = stringResource(R.string.telegram_password_step_title),
        description =
            state.passwordHint?.let { stringResource(R.string.telegram_password_hint_label, it) }
                ?: stringResource(R.string.telegram_password_step_description),
    ) {
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text(stringResource(R.string.telegram_password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onContinue,
            enabled = !busy && password.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.telegram_continue))
        }
    }
}

@Composable
private fun ConnectingCard() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.telegram_connecting))
    }
}

@Composable
private fun SendingBanner() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.telegram_sending_code),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = Color.Transparent,
        )
    }
}
