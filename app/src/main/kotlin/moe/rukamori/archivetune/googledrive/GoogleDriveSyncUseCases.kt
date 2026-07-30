/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.googledrive

import kotlinx.coroutines.flow.Flow
import moe.rukamori.archivetune.backup.ScheduledBackupFrequency
import javax.inject.Inject

/**
 * Use cases for Google Drive sync — mirrors the structure of
 * [moe.rukamori.archivetune.backup.ObserveScheduledBackupSettingsUseCase] /
 * [moe.rukamori.archivetune.backup.UpdateScheduledBackupUseCase].
 *
 * The `Update` use case reschedules the WorkManager job after every settings change
 * (except for `setOverwrite` and `setRemoteFolder`, which don't affect when the next
 * sync runs) — this matches the local scheduled-backup behavior.
 */
class ObserveGoogleDriveSyncSettingsUseCase
    @Inject
    constructor(
        private val repository: GoogleDriveSyncRepository,
    ) {
        operator fun invoke(): Flow<GoogleDriveSyncSettings?> = repository.observeSettings()
    }

class UpdateGoogleDriveSyncUseCase
    @Inject
    constructor(
        private val repository: GoogleDriveSyncRepository,
        private val scheduler: GoogleDriveSyncScheduler,
    ) {
        suspend fun setEnabled(enabled: Boolean): GoogleDriveSyncSettings =
            repository.updateEnabled(enabled).also(scheduler::replace)

        suspend fun setFrequency(frequency: ScheduledBackupFrequency): GoogleDriveSyncSettings =
            repository.updateFrequency(frequency).also(scheduler::replace)

        suspend fun setCustomDate(epochDay: Long): GoogleDriveSyncSettings =
            repository.updateCustomDate(epochDay).also(scheduler::replace)

        suspend fun setAccount(email: String): GoogleDriveSyncSettings =
            repository.updateAccount(email).also(scheduler::replace)

        suspend fun setRemoteFolder(id: String?, name: String?): GoogleDriveSyncSettings =
            repository.updateRemoteFolder(id, name)

        suspend fun setOverwrite(overwrite: Boolean): GoogleDriveSyncSettings =
            repository.updateOverwrite(overwrite)

        suspend fun clearAccount(): GoogleDriveSyncSettings =
            repository.clearAccount().also { scheduler.cancel() }

        fun runNow() = scheduler.runNow()
    }
