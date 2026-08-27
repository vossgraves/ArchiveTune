/*
 * ArchiveTune (2026)
 * © ArchiveTuneFork contributors — github.com/vossgraves/ArchiveTune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package app.atf.media.googledrive

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import app.atf.media.R
import app.atf.media.utils.reportException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * WorkManager worker that performs a cloud backup sync via SAF.
 *
 * Loads settings from [GoogleDriveSyncRepository], bails out if disabled or no folder is
 * configured, then delegates to [GoogleDriveClient.uploadBackup]. After a successful upload,
 * schedules the next run via [GoogleDriveSyncScheduler.appendNext].
 *
 * Returns:
 *   - `Result.success()` after a successful upload (or when settings are disabled — nothing to do).
 *   - `Result.retry()` on a transient failure (the folder URI is temporarily unreachable, a
 *     provider-side hiccup, or an I/O blip). WorkManager backs off exponentially.
 *   - `Result.failure()` on permanent failure (folder permission revoked, URI malformed).
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
 * Hilt entry point used by [GoogleDriveSyncWorker] to access the Drive sync dependencies without
 * a Hilt-injected constructor (WorkManager instantiates workers via its own factory).
 *
 * Method names are prefixed with `googleDrive` because Hilt generates a single `SingletonC`
 * class that implements EVERY `@EntryPoint` interface installed in `SingletonComponent`. If
 * two entry points expose methods with the same name and JVM-erased signature (e.g.
 * `repository()` returning different types), the generated Java class has two methods with the
 * same name + parameter list but different return types — which the JVM rejects with
 * "Found conflicting entry point declarations". This was happening between
 * `ScheduledBackupWorkerEntryPoint` (pre-existing) and this interface, so the Google Drive
 * entry point uses unique method names to avoid the clash.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface GoogleDriveSyncWorkerEntryPoint {
    fun googleDriveRepository(): GoogleDriveSyncRepository

    fun googleDriveScheduler(): GoogleDriveSyncScheduler

    fun googleDriveClient(): GoogleDriveClient
}
