/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.PoolApiKeyKey
import moe.rukamori.archivetune.utils.PoolAccountManager
import moe.rukamori.archivetune.utils.rememberPreference

/**
 * Personal Source Pool API key. The pool site issues one per account (request → admin
 * approval → Dashboard → copy); pasting it here overrides the key baked into the build, so a
 * person's own leased accounts work without a custom APK — and revoking a key only affects
 * that one person. Stored in DataStore as [PoolApiKeyKey], which [PoolAccountManager.refresh]
 * prefers over the baked key on every refresh. Saving (or clearing) forces an immediate pool
 * refresh so the change takes effect without a restart.
 */
@Composable
fun PoolApiKeySection() {
    if (!PoolAccountManager.isEnabled) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val (savedKey, onSavedKeyChange) = rememberPreference(PoolApiKeyKey, "")
    var draft by remember { mutableStateOf(savedKey) }
    var busy by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }

    fun applyKey(value: String) {
        busy = true
        onSavedKeyChange(value)
        draft = value
        scope.launch(Dispatchers.IO) {
            val ok = runCatching { PoolAccountManager.refresh(context, force = true) }.getOrDefault(false)
            withContext(Dispatchers.Main) {
                busy = false
                feedback =
                    if (value.isBlank()) {
                        context.getString(R.string.pool_api_key_cleared)
                    } else if (ok) {
                        context.getString(R.string.pool_api_key_saved_ok)
                    } else {
                        context.getString(R.string.pool_api_key_saved_no_accounts)
                    }
            }
        }
    }

    Column(Modifier.padding(vertical = 4.dp)) {
        Text(
            stringResource(R.string.pool_api_key_title),
            Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(R.string.pool_api_key_description),
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            label = { Text(stringResource(R.string.pool_api_key_label)) },
            singleLine = true,
            isError = draft.isNotBlank() && !draft.startsWith("atp_"),
            supportingText = {
                if (draft.isNotBlank() && !draft.startsWith("atp_")) {
                    Text(stringResource(R.string.pool_api_key_invalid))
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(
                onClick = { applyKey("") },
                enabled = !busy && (savedKey.isNotBlank() || draft.isNotBlank()),
            ) { Text(stringResource(R.string.pool_api_key_clear)) }
            FilledTonalButton(
                onClick = { applyKey(draft.trim()) },
                enabled = !busy && draft.trim().startsWith("atp_") && draft.trim() != savedKey,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.pool_api_key_save)) }
        }
        feedback?.let {
            Text(
                it,
                Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
