/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.googledrive

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import moe.rukamori.archivetune.backup.ScheduledBackupFrequency
import moe.rukamori.archivetune.utils.dataStore
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore-backed repository for [GoogleDriveSyncSettings].
 *
 * Mirrors the pattern of [moe.rukamori.archivetune.backup.ScheduledBackupRepository]: a single
 * `observeSettings()` flow backed by DataStore Preferences, plus suspend updaters for each field.
 * All updaters serialize through [updateMutex] to prevent lost updates when multiple fields are
 * changed in rapid succession (e.g. user toggles enable + picks a frequency in quick succession).
 *
 * The settings keys are NOT portable across devices — they reference the local Google account
 * email and a Drive folder ID. They're added to `NON_PORTABLE_PREFERENCE_KEYS` so the
 * BackupArchiveRepository skips them when exporting a portable SETTINGS backup.
 */
@Singleton
class GoogleDriveSyncRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val updateMutex = Mutex()

        fun observeSettings(): Flow<GoogleDriveSyncSettings?> =
            context.dataStore.data.map { preferences ->
                if (!preferences.contains(ENABLED_KEY) && !preferences.contains(ACCOUNT_EMAIL_KEY)) {
                    return@map null
                }
                preferences.toSettings()
            }

        suspend fun getSettings(): GoogleDriveSyncSettings? =
            context.dataStore.data.first().let { preferences ->
                if (!preferences.contains(ENABLED_KEY) && !preferences.contains(ACCOUNT_EMAIL_KEY)) {
                    null
                } else {
                    preferences.toSettings()
                }
            }

        suspend fun updateEnabled(enabled: Boolean): GoogleDriveSyncSettings =
            updateMutex.withLock {
                context.dataStore.edit { it[ENABLED_KEY] = enabled }.toSettings()
            }

        suspend fun updateFrequency(frequency: ScheduledBackupFrequency): GoogleDriveSyncSettings =
            updateMutex.withLock {
                context.dataStore
                    .edit { it[FREQUENCY_KEY] = frequency.name }
                    .toSettings()
            }

        suspend fun updateCustomDate(epochDay: Long): GoogleDriveSyncSettings =
            updateMutex.withLock {
                require(epochDay >= LocalDate.now().toEpochDay()) { "Sync date cannot be in the past" }
                context.dataStore
                    .edit {
                        it[FREQUENCY_KEY] = ScheduledBackupFrequency.CUSTOM.name
                        it[CUSTOM_DATE_KEY] = epochDay
                    }.toSettings()
            }

        suspend fun updateAccount(email: String): GoogleDriveSyncSettings =
            updateMutex.withLock {
                context.dataStore.edit { it[ACCOUNT_EMAIL_KEY] = email }.toSettings()
            }

        suspend fun updateRemoteFolder(id: String?, name: String?): GoogleDriveSyncSettings =
            updateMutex.withLock {
                context.dataStore
                    .edit {
                        if (id == null) it.remove(REMOTE_FOLDER_ID_KEY) else it[REMOTE_FOLDER_ID_KEY] = id
                        if (name == null) it.remove(REMOTE_FOLDER_NAME_KEY) else it[REMOTE_FOLDER_NAME_KEY] = name
                    }.toSettings()
            }

        suspend fun updateOverwrite(overwrite: Boolean): GoogleDriveSyncSettings =
            updateMutex.withLock {
                context.dataStore.edit { it[OVERWRITE_KEY] = overwrite }.toSettings()
            }

        suspend fun recordSyncResult(success: Boolean): GoogleDriveSyncSettings =
            updateMutex.withLock {
                context.dataStore
                    .edit {
                        it[LAST_SYNC_MS_KEY] = System.currentTimeMillis()
                        it[LAST_SYNC_FAILED_KEY] = !success
                    }.toSettings()
            }

        suspend fun clearAccount(): GoogleDriveSyncSettings =
            updateMutex.withLock {
                context.dataStore
                    .edit {
                        it.remove(ACCOUNT_EMAIL_KEY)
                        it.remove(REMOTE_FOLDER_ID_KEY)
                        it.remove(REMOTE_FOLDER_NAME_KEY)
                        it[ENABLED_KEY] = false
                    }.toSettings()
            }

        private fun androidx.datastore.preferences.core.Preferences.toSettings(): GoogleDriveSyncSettings =
            GoogleDriveSyncSettings(
                enabled = this[ENABLED_KEY] ?: false,
                frequency =
                    this[FREQUENCY_KEY]
                        ?.let { stored -> ScheduledBackupFrequency.entries.firstOrNull { it.name == stored } }
                        ?: ScheduledBackupFrequency.WEEKLY,
                customDateEpochDay = this[CUSTOM_DATE_KEY],
                accountEmail = this[ACCOUNT_EMAIL_KEY],
                remoteFolderId = this[REMOTE_FOLDER_ID_KEY],
                remoteFolderName = this[REMOTE_FOLDER_NAME_KEY],
                overwriteExisting = this[OVERWRITE_KEY] ?: false,
                lastSyncEpochMs = this[LAST_SYNC_MS_KEY],
                lastSyncFailed = this[LAST_SYNC_FAILED_KEY] ?: false,
            )

        companion object {
            private val ENABLED_KEY = booleanPreferencesKey("googleDriveSyncEnabled")
            private val FREQUENCY_KEY = stringPreferencesKey("googleDriveSyncFrequency")
            private val CUSTOM_DATE_KEY = longPreferencesKey("googleDriveSyncCustomDateEpochDay")
            private val ACCOUNT_EMAIL_KEY = stringPreferencesKey("googleDriveSyncAccountEmail")
            private val REMOTE_FOLDER_ID_KEY = stringPreferencesKey("googleDriveSyncRemoteFolderId")
            private val REMOTE_FOLDER_NAME_KEY = stringPreferencesKey("googleDriveSyncRemoteFolderName")
            private val OVERWRITE_KEY = booleanPreferencesKey("googleDriveSyncOverwriteExisting")
            private val LAST_SYNC_MS_KEY = longPreferencesKey("googleDriveSyncLastSyncEpochMs")
            private val LAST_SYNC_FAILED_KEY = booleanPreferencesKey("googleDriveSyncLastSyncFailed")

            /**
             * Keys that should be excluded from portable SETTINGS backups because they reference
             * device-specific state (the local Google account email + a Drive folder ID).
             */
            val NON_PORTABLE_PREFERENCE_KEYS: Set<String> =
                setOf(
                    ENABLED_KEY.name,
                    FREQUENCY_KEY.name,
                    CUSTOM_DATE_KEY.name,
                    ACCOUNT_EMAIL_KEY.name,
                    REMOTE_FOLDER_ID_KEY.name,
                    REMOTE_FOLDER_NAME_KEY.name,
                    OVERWRITE_KEY.name,
                    LAST_SYNC_MS_KEY.name,
                    LAST_SYNC_FAILED_KEY.name,
                )
        }
    }
