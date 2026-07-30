/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.googledrive

import moe.rukamori.archivetune.backup.ScheduledBackupFrequency

/**
 * Settings for Google Drive auto-sync of app backups.
 *
 * The frequency enum is re-used from [ScheduledBackupFrequency] (DAILY / WEEKLY / MONTHLY / CUSTOM)
 * so the user sees a consistent set of options in both the local scheduled-backup section and the
 * Google Drive sync section of the Backup & Restore screen.
 *
 * @param enabled Whether auto-sync to Google Drive is active. When false, the WorkManager
 *   scheduled job is cancelled and no uploads happen.
 * @param frequency How often the sync should run.
 * @param customDateEpochDay For CUSTOM frequency — the next date at which the sync should run.
 *   Stored as epoch-day (days since 1970-01-01) for locale-independent storage.
 * @param accountEmail The Google account email the user picked at sign-in. Used to look up the
 *   account via AccountManager when fetching an OAuth token for the Drive API.
 * @param remoteFolderId Google Drive folder ID where backups are uploaded. Null means upload to
 *   "My Drive" root. Resolved once at folder-pick time and persisted.
 * @param remoteFolderName Display name of [remoteFolderId] — shown in the UI so the user can
 *   tell at a glance which Drive folder backups land in.
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
    val accountEmail: String? = null,
    val remoteFolderId: String? = null,
    val remoteFolderName: String? = null,
    val overwriteExisting: Boolean = false,
    val lastSyncEpochMs: Long? = null,
    val lastSyncFailed: Boolean = false,
)
