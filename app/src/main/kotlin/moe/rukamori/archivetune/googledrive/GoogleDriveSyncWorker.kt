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
        val settings = dependencies.googleDriveRepository().getSettings() ?: return Result.success()
        if (!settings.enabled || settings.accountEmail == null) return Result.success()

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
                is GoogleDriveClient.UploadResult.NeedsManualUpload -> {
                    // OAuth refused (typically "UnregisteredOnApiConsole"). We can't auto-upload
                    // without registering the app on Google Cloud Console. Post a notification
                    // with an ACTION_SEND intent so the user can save the backup to Drive (or
                    // anywhere else) via the system share sheet.
                    postManualUploadNotification(result)
                    // Treat as a permanent failure for the auto-sync path — we don't want
                    // WorkManager to retry every 5 minutes and pile up notifications.
                    dependencies.googleDriveRepository().recordSyncResult(success = false)
                    Result.failure()
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

    /**
     * Posts a high-priority notification with a "Save to Drive" action so the user can manually
     * upload the temp backup file produced by [GoogleDriveClient.UploadResult.NeedsManualUpload].
     *
     * The notification's tap action launches the system share sheet for the temp backup file —
     * the user picks "Save to Drive" (or any other target) and Drive's own UI prompts for the
     * destination folder.
     */
    private fun postManualUploadNotification(result: GoogleDriveClient.UploadResult.NeedsManualUpload) {
        val client = EntryPointAccessors.fromApplication(
            applicationContext,
            GoogleDriveSyncWorkerEntryPoint::class.java,
        ).googleDriveClient()
        val intent = client.buildManualUploadIntent(result.tempFileUri, result.fileName).apply {
            // Launch as a new task from a notification context.
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            applicationContext,
            MANUAL_UPLOAD_REQUEST_CODE,
            android.content.Intent.createChooser(intent, result.fileName).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notificationManager = androidx.core.app.NotificationManagerCompat.from(applicationContext)
        if (!notificationManager.areNotificationsEnabled()) return

        val channel = android.app.NotificationChannel(
            MANUAL_UPLOAD_CHANNEL_ID,
            applicationContext.getString(R.string.google_drive_sync_manual_upload_channel),
            android.app.NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = applicationContext.getString(R.string.google_drive_sync_manual_upload_channel_desc)
        }
        notificationManager.createNotificationChannel(channel)

        val notification = androidx.core.app.NotificationCompat
            .Builder(applicationContext, MANUAL_UPLOAD_CHANNEL_ID)
            .setSmallIcon(R.drawable.backup)
            .setContentTitle(applicationContext.getString(R.string.google_drive_sync_manual_upload_title))
            .setContentText(applicationContext.getString(R.string.google_drive_sync_manual_upload_text))
            .setStyle(
                androidx.core.app.NotificationCompat.BigTextStyle()
                    .bigText(applicationContext.getString(R.string.google_drive_sync_manual_upload_big_text)),
            )
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        try {
            notificationManager.notify(MANUAL_UPLOAD_NOTIFICATION_ID, notification)
        } catch (security: SecurityException) {
            // Some OEMs gate notifications behind a runtime permission we may not hold.
            reportException(security)
        }
    }

    companion object {
        private val FILE_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
        private const val MANUAL_UPLOAD_CHANNEL_ID = "gdrive_manual_upload"
        private const val MANUAL_UPLOAD_NOTIFICATION_ID = 4271
        private const val MANUAL_UPLOAD_REQUEST_CODE = 4271
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
