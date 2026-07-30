/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.googledrive

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.utils.reportException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * WorkManager worker that performs a Google Drive backup sync.
 *
 * Loads settings from [GoogleDriveSyncRepository], bails out if disabled or unconfigured, then
 * delegates to [GoogleDriveClient.uploadBackup]. After a successful upload, schedules the next
 * run via [GoogleDriveSyncScheduler.appendNext].
 *
 * Returns:
 *   - `Result.success()` after a successful upload (or when settings are disabled — nothing to do).
 *   - `Result.retry()` on a transient failure (network blip, Drive 5xx, OAuth token expiry
 *     that the client's one-shot retry didn't fix). WorkManager backs off exponentially.
 *   - `Result.failure()` on permanent failure (account no longer present, no Drive permission).
 */
class GoogleDriveSyncWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val dependencies =
            EntryPointAccessors.fromApplication(
                applicationContext,
                GoogleDriveSyncWorkerEntryPoint::class.java,
            )
        val settings = dependencies.repository().getSettings() ?: return Result.success()
        if (!settings.enabled || settings.accountEmail == null) return Result.success()

        return try {
            val fileName =
                if (settings.overwriteExisting) {
                    applicationContext.getString(R.string.app_name)
                } else {
                    val timestamp = LocalDateTime.now().format(FILE_TIMESTAMP_FORMATTER)
                    "${applicationContext.getString(R.string.app_name)}_$timestamp"
                }
            val fileId =
                dependencies.client().uploadBackup(settings, fileName)
                    ?: return Result.retry()
            dependencies.repository().recordSyncResult(success = true)
            dependencies.scheduler().appendNext(
                settings.copy(lastSyncEpochMs = System.currentTimeMillis(), lastSyncFailed = false),
            )
            Result.success()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (security: SecurityException) {
            reportException(security)
            dependencies.repository().recordSyncResult(success = false)
            Result.failure()
        } catch (exception: Exception) {
            reportException(exception)
            dependencies.repository().recordSyncResult(success = false)
            Result.retry()
        }
    }

    companion object {
        private val FILE_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface GoogleDriveSyncWorkerEntryPoint {
    fun repository(): GoogleDriveSyncRepository

    fun scheduler(): GoogleDriveSyncScheduler

    fun client(): GoogleDriveClient
}
