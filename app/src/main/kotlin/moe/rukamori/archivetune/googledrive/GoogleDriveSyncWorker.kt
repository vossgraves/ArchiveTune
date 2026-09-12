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

/** WorkManager worker that performs a cloud backup sync via SAF. */
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
        val settings = dependencies.googleDriveRepository().getSettings() ?: return Result.success()
        if (!settings.enabled || settings.remoteFolderUri == null) return Result.success()

        return try {
            val fileName =
                if (settings.overwriteExisting) {
                    applicationContext.getString(R.string.app_name)
                } else {
                    val timestamp = LocalDateTime.now().format(FILE_TIMESTAMP_FORMATTER)
                    "${applicationContext.getString(R.string.app_name)}_$timestamp"
                }
            when (val result = dependencies.googleDriveClient().uploadBackup(settings, fileName)) {
                is GoogleDriveClient.UploadResult.Success -> {
                    dependencies.googleDriveRepository().recordSyncResult(success = true)
                    dependencies.googleDriveScheduler().appendNext(
                        settings.copy(lastSyncEpochMs = System.currentTimeMillis(), lastSyncFailed = false),
                    )
                    Result.success()
                }
                is GoogleDriveClient.UploadResult.TransientFailure -> {
                    dependencies.googleDriveRepository().recordSyncResult(success = false)
                    Result.retry()
                }
                is GoogleDriveClient.UploadResult.PermanentFailure -> {
                    reportException(IllegalStateException(result.message))
                    dependencies.googleDriveRepository().recordSyncResult(success = false)
                    Result.failure()
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (security: SecurityException) {
            reportException(security)
            dependencies.googleDriveRepository().recordSyncResult(success = false)
            Result.failure()
        } catch (exception: Exception) {
            reportException(exception)
            dependencies.googleDriveRepository().recordSyncResult(success = false)
            Result.retry()
        }
    }

    companion object {
        private val FILE_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
    }
}

/**
 * Hilt entry point used by [GoogleDriveSyncWorker] to access the Drive sync dependencies without a
 * Hilt-injected constructor (WorkManager instantiates workers via its own factory).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface GoogleDriveSyncWorkerEntryPoint {
    fun googleDriveRepository(): GoogleDriveSyncRepository

    fun googleDriveScheduler(): GoogleDriveSyncScheduler

    fun googleDriveClient(): GoogleDriveClient
}
