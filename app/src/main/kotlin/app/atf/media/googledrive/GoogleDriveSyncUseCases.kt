/*
 * ArchiveTune (2026)
 * © ArchiveTuneFork contributors — github.com/vossgraves/ArchiveTune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package app.atf.media.googledrive

import kotlinx.coroutines.flow.Flow
import app.atf.media.backup.ScheduledBackupFrequency
import javax.inject.Inject

/**
 * Use cases for Google Drive sync — mirrors the structure of
 * [app.atf.media.backup.ObserveScheduledBackupSettingsUseCase] /
 * [app.atf.media.backup.UpdateScheduledBackupUseCase].
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

        suspend fun setRemoteFolder(uri: String?, name: String?): GoogleDriveSyncSettings =
            repository.updateRemoteFolder(uri, name)

        suspend fun setOverwrite(overwrite: Boolean): GoogleDriveSyncSettings =
            repository.updateOverwrite(overwrite)

        /**
         * Clears the picked folder and disables auto-sync. Cancels the scheduled WorkManager job
         * since sync can't run without a target folder.
         */
        suspend fun clearRemoteFolder(): GoogleDriveSyncSettings =
            repository.clearRemoteFolder().also { scheduler.cancel() }

        fun runNow() = scheduler.runNow()
    }
