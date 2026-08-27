/*
 * ArchiveTune (2026)
 * © ArchiveTuneFork contributors — github.com/vossgraves/ArchiveTune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package app.atf.media.googledrive

import app.atf.media.backup.ScheduledBackupFrequency

/**
 * Settings for Google Drive auto-sync of app backups.
 *
 * The frequency enum is re-used from [ScheduledBackupFrequency] (DAILY / WEEKLY / MONTHLY / CUSTOM)
 * so the user sees a consistent set of options in both the local scheduled-backup section and the
 * Google Drive sync section of the Backup & Restore screen.
 *
 * ## Implementation note — SAF, not OAuth
 *
 * Backups are uploaded via the Android Storage Access Framework (SAF): the user picks a Drive
 * folder through the system `OpenDocumentTree` picker, the app persists the returned tree URI
 * (`takePersistableUriPermission`), and each sync writes the backup file directly into that folder
 * via `ContentResolver.openOutputStream`.
 *
 * This replaced an earlier AccountManager + Drive REST API approach that failed with
 * `AuthenticatorException: UnregisteredOnApiConsole` because the app's package + SHA-1 was not
 * registered on Google Cloud Console for the `drive.file` OAuth scope. SAF needs no OAuth, no
 * Cloud Console registration, and no extra dependencies — the Drive app (and any other cloud
 * provider that exposes a DocumentsProvider) handles authentication itself.
 *
 * @param enabled Whether auto-sync is active. When false, the WorkManager scheduled job is
 *   cancelled and no uploads happen.
 * @param frequency How often the sync should run.
 * @param customDateEpochDay For CUSTOM frequency — the next date at which the sync should run.
 *   Stored as epoch-day (days since 1970-01-01) for locale-independent storage.
 * @param remoteFolderUri The persisted SAF tree URI (as a string) of the folder backups are
 *   written to. Null means no folder has been picked yet — sync is disabled until one is chosen.
 * @param remoteFolderName Display name of [remoteFolderUri] — shown in the UI so the user can
 *   tell at a glance which folder backups land in. Derived from `DocumentFile.name`.
 * @param overwriteExisting When true, each sync overwrites the previous backup file (matched by
 *   name) instead of creating a timestamped new file.
 * @param lastSyncEpochMs Epoch-millis of the last successful sync, or null if never synced.
 *   Shown in the UI as "Last synced: …".
 * @param lastSyncFailed True if the last sync attempt failed. Used to render a warning in the UI.
 */
data class GoogleDriveSyncSettings(
    val enabled: Boolean = false,
    val frequency: ScheduledBackupFrequency = ScheduledBackupFrequency.WEEKLY,
    val customDateEpochDay: Long? = null,
    val remoteFolderUri: String? = null,
    val remoteFolderName: String? = null,
    val overwriteExisting: Boolean = false,
    val lastSyncEpochMs: Long? = null,
    val lastSyncFailed: Boolean = false,
)
