/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.googledrive

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.backup.BackupArchiveCategory
import moe.rukamori.archivetune.backup.CreateBackupUseCase
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Uploads backup files to a cloud folder chosen by the user via the Android Storage Access
 * Framework (SAF).
 *
 * ## Why SAF instead of the Drive REST API?
 *
 * An earlier implementation used `AccountManager.getAuthToken` to obtain an OAuth2 token for the
 * `drive.file` scope and then talked to the Drive REST v3 API directly. That approach failed with
 * `AuthenticatorException: UnregisteredOnApiConsole` because the app's package name + SHA-1
 * signing key was not registered on Google Cloud Console for that OAuth client — and for an
 * open-source app that end users build themselves, it never can be (every fork has a different
 * signing key).
 *
 * SAF sidesteps the entire problem:
 *   - The user picks a folder through the system `OpenDocumentTree` picker. The Drive app (and
 *     Nextcloud, Dropbox, etc.) exposes its folder tree as a DocumentsProvider, so the picker
 *     shows the user's actual Google Drive folder hierarchy.
 *   - The returned tree URI is persisted via `ContentResolver.takePersistableUriPermission`, so
 *     it survives app restarts and reboots.
 *   - Each sync writes the backup file directly into that folder via the framework
 *     [DocumentsContract] / `ContentResolver.openOutputStream` APIs — no OAuth token, no Cloud
 *     Console registration, no extra dependencies. The cloud provider handles authentication
 *     and uploading to its backend transparently.
 *
 * ## Upload flow
 *
 *   1. [uploadBackup] builds a local backup .zip into `cacheDir/gdrive_backup/` via
 *      [CreateBackupUseCase] (writing to a FileProvider-backed temp file).
 *   2. It resolves the persisted tree URI from [GoogleDriveSyncSettings.remoteFolderUri] and
 *      derives the folder's document URI via [DocumentsContract.buildDocumentUriUsingTree].
 *   3. If [GoogleDriveSyncSettings.overwriteExisting] is true and a child document with the
 *      target name already exists in the folder, that document is deleted first (SAF providers
 *      don't reliably overwrite an existing document in place — `createDocument` would produce
 *      a "name (1)" copy instead, so we delete explicitly to get a clean replace).
 *   4. A new child document is created with [DocumentsContract.createDocument] and the temp
 *      backup bytes are streamed into its output stream.
 *   5. The temp file is deleted on success or unrecoverable failure.
 *
 * ## Why DocumentsContract and not androidx.documentfile?
 *
 * [DocumentsContract] is a framework API (available since API 19) and needs no extra gradle
 * dependency. The app already uses it elsewhere (see `CachePlaylistScreen`), so this keeps the
 * dependency footprint unchanged.
 */
@Singleton
class GoogleDriveClient
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val createBackupUseCase: CreateBackupUseCase,
    ) {
        /**
         * Result of an upload attempt.
         *
         *   - [Success] — the backup file was written to the picked folder.
         *   - [TransientFailure] — a recoverable error (the folder URI is temporarily
         *     unreachable, a provider-side hiccup, or an I/O blip). The caller should retry
         *     with backoff.
         *   - [PermanentFailure] — the folder URI is missing/malformed, the permission was
         *     revoked, or the provider rejected the write. The caller should not retry
         *     automatically and should surface a message prompting the user to re-pick a
         *     folder.
         */
        sealed interface UploadResult {
            data class Success(val fileName: String) : UploadResult

            data class TransientFailure(val message: String) : UploadResult

            data class PermanentFailure(val message: String) : UploadResult
        }

        /**
         * Uploads a single backup file to the user-picked cloud folder.
         *
         * @param settings The current sync settings — must have a non-null
         *   [GoogleDriveSyncSettings.remoteFolderUri] (the caller's responsibility to check
         *   before invoking).
         * @param backupFileName The user-visible name to give the uploaded file (without
         *   extension — we append `.backup` here for consistency with local backups).
         */
        suspend fun uploadBackup(settings: GoogleDriveSyncSettings, backupFileName: String): UploadResult =
            withContext(Dispatchers.IO) {
                val treeUriString = settings.remoteFolderUri
                    ?: return@withContext UploadResult.PermanentFailure("No backup folder configured")
                val treeUri =
                    try {
                        Uri.parse(treeUriString)
                    } catch (e: Exception) {
                        return@withContext UploadResult.PermanentFailure("Invalid folder URI: ${e.message}")
                    }
                // Soft log: if the configured folder isn't a Google Drive URI (e.g. it's
                // local storage, Dropbox, Nextcloud), backups will still be written to it via
                // SAF — this is by design, since the user may have intentionally picked a
                // non-Drive folder (Drive folders aren't reachable via SAF without the Drive
                // app installed). We log a warning so any future debugging knows the upload
                // didn't go to Drive. We don't reject — that would break the feature entirely
                // for users without the Drive app, which was the bug report that motivated
                // this revision.
                if (!isGoogleDriveAuthority(treeUri)) {
                    Timber.w(
                        "GoogleDriveClient.uploadBackup: folder URI authority '%s' is not Google Drive — uploading via SAF anyway (user-picked folder)",
                        treeUri.authority,
                    )
                }
                val folderDocUri =
                    try {
                        val folderDocId = DocumentsContract.getTreeDocumentId(treeUri)
                        DocumentsContract.buildDocumentUriUsingTree(treeUri, folderDocId)
                    } catch (e: Exception) {
                        return@withContext UploadResult.PermanentFailure("Could not resolve the picked folder: ${e.message}")
                    }

                val tempDir = File(context.cacheDir, "gdrive_backup").apply { mkdirs() }
                val tempFile = File(tempDir, "${System.currentTimeMillis()}_upload.backup")
                try {
                    // Stage 1: build the local backup into a temp file via the FileProvider.
                    val tempUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.FileProvider",
                        tempFile,
                    )
                    createBackupUseCase(
                        uri = tempUri,
                        categories = BackupArchiveCategory.entries.toSet(),
                    )

                    val fullFileName = "$backupFileName.backup"

                    // Stage 2: if overwriting, delete any existing child document with the same
                    // name first. SAF providers don't reliably truncate/overwrite an existing
                    // document in place — createDocument would produce a "name (1)" copy.
                    if (settings.overwriteExisting) {
                        findChildDocumentId(treeUri, folderDocUri, fullFileName)?.let { existingDocId ->
                            val existingDocUri =
                                DocumentsContract.buildDocumentUriUsingTree(treeUri, existingDocId)
                            runCatching {
                                DocumentsContract.deleteDocument(context.contentResolver, existingDocUri)
                            }.onFailure { Timber.w(it, "GoogleDriveClient: could not delete existing backup document") }
                        }
                    }

                    // Stage 3: create the destination document and stream the backup bytes into it.
                    val targetDocUri =
                        try {
                            DocumentsContract.createDocument(
                                context.contentResolver,
                                folderDocUri,
                                BACKUP_MIME_TYPE,
                                fullFileName,
                            )
                        } catch (e: Exception) {
                            Timber.w(e, "GoogleDriveClient: provider refused to create backup document")
                            null
                        } ?: return@withContext UploadResult.TransientFailure(
                            "The cloud provider refused to create the backup file",
                        )

                    val written =
                        try {
                            context.contentResolver.openOutputStream(targetDocUri, "w")?.use { out ->
                                tempFile.inputStream().use { input ->
                                    input.copyTo(out)
                                }
                                true
                            } ?: false
                        } catch (io: IOException) {
                            Timber.w(io, "GoogleDriveClient: IOException writing backup to folder")
                            // Clean up the half-written document so we don't leave a corrupt file.
                            runCatching {
                                DocumentsContract.deleteDocument(context.contentResolver, targetDocUri)
                            }
                            false
                        }
                    if (!written) {
                        return@withContext UploadResult.TransientFailure("Failed to write backup bytes to the folder")
                    }

                    UploadResult.Success(fullFileName)
                } catch (cancellation: kotlinx.coroutines.CancellationException) {
                    throw cancellation
                } catch (security: SecurityException) {
                    // The persisted URI permission was revoked (e.g. user cleared app data, or
                    // the provider withdrew access). Surface as permanent so the UI can prompt
                    // the user to re-pick a folder.
                    Timber.w(security, "GoogleDriveClient: no permission for folder URI")
                    UploadResult.PermanentFailure("Folder access was revoked — please re-pick the folder")
                } catch (e: Exception) {
                    Timber.w(e, "GoogleDriveClient.uploadBackup failed")
                    UploadResult.TransientFailure(e.message ?: "Unknown error")
                } finally {
                    runCatching { tempFile.delete() }
                }
            }

        /**
         * Queries the picked folder for a child document with the given [name]. Returns the
         * matching document ID, or null if no such child exists (or the query failed).
         */
        private fun findChildDocumentId(treeUri: Uri, folderDocUri: Uri, name: String): String? {
            val folderDocId = DocumentsContract.getDocumentId(folderDocUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, folderDocId)
            return try {
                context.contentResolver.query(
                    childrenUri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    ),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val docId = cursor.getString(0) ?: continue
                        val docName = cursor.getString(1) ?: continue
                        if (docName == name) return docId
                    }
                    null
                }
            } catch (e: Exception) {
                Timber.w(e, "GoogleDriveClient: failed to list folder children")
                null
            }
        }

        companion object {
            /** MIME type used when creating the backup document. Octet-stream is accepted by all
             *  SAF providers and preserves the bytes verbatim. */
            private const val BACKUP_MIME_TYPE = "application/octet-stream"

            /**
             * Authorities registered by the Google Drive app's DocumentsProvider. When the user
             * picks a Drive folder via the system `OpenDocumentTree` picker, the returned tree
             * URI has one of these as its authority. Kept in sync with the same set in
             * `BackupAndRestore.kt` (UI-side picker validation) — duplicated here so the worker
             * doesn't have to depend on the UI module.
             *
             *   - `com.google.android.apps.docs.storage` — the modern Drive app.
             *   - `com.google.android.apps.docs.storage.legacy` — older Drive app variants.
             */
            private val GOOGLE_DRIVE_AUTHORITIES = setOf(
                "com.google.android.apps.docs.storage",
                "com.google.android.apps.docs.storage.legacy",
            )

            /**
             * Returns true iff [uri]'s authority matches the Google Drive DocumentsProvider.
             * Used by [uploadBackup] for a soft warning log when the configured folder isn't
             * a Drive URI (e.g. the user picked a local-storage or Nextcloud folder because
             * the Drive app isn't installed). The upload proceeds anyway — this is by design,
             * since rejecting non-Drive URIs would break the feature entirely for users
             * without the Drive app.
             */
            fun isGoogleDriveAuthority(uri: Uri): Boolean {
                val authority = uri.authority ?: return false
                return authority in GOOGLE_DRIVE_AUTHORITIES
            }
        }
    }
