/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.googledrive

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.backup.BackupArchiveCategory
import moe.rukamori.archivetune.backup.CreateBackupUseCase
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Talks to the Google Drive REST API v3 to upload backup files.
 *
 * Why REST + AccountManager instead of the Google Drive Android API or the google-api-client?
 *  - The Drive Android API was deprecated by Google in 2024.
 *  - The google-api-client + google-api-services-drive JARs add ~6 MB to the APK and require
 *    ProGuard keep rules. They're overkill for a "upload a single file" use case.
 *  - AccountManager is built into Android, works on both GMS and FOSS builds, and lets us
 *    request an OAuth2 token for the `drive.file` scope without shipping any extra dependencies.
 *
 * The flow:
 *   1. User picks a Google account via AccountManager.getAccountsByType("com.google")
 *      (requires GET_ACCOUNTS permission on API ≤ 22).
 *   2. When a sync is due, [uploadBackup] asks AccountManager for an OAuth2 token for the
 *      `https://www.googleapis.com/auth/drive.file` scope.
 *   3. We build a local backup .zip via [CreateBackupUseCase] into `cacheDir/gdrive_backup/`.
 *   4. We list existing Drive files in the target folder with a `q=name='AppName.backup'`
 *      query — if found and overwrite is requested, we PATCH the existing file; otherwise
 *      we POST a new multipart upload.
 *   5. The local temp file is deleted after upload (success or failure).
 *
 * The `drive.file` scope is the least permissive Drive scope — it only allows access to files
 * created or opened by this app, which is exactly what we need for backup files.
 */
@Singleton
class GoogleDriveClient
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val createBackupUseCase: CreateBackupUseCase,
    ) {
        private val httpClient =
            OkHttpClient
                .Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .build()

        /**
         * Lists Google accounts registered on the device.
         *
         * On Android 6.0+ the GET_ACCOUNTS permission is runtime-guarded; the caller must hold
         * it before invoking this. Returns an empty list if no Google accounts are present.
         */
        fun listGoogleAccounts(): List<Account> =
            try {
                AccountManager.get(context).getAccountsByType("com.google").toList()
            } catch (security: SecurityException) {
                Timber.w(security, "GET_ACCOUNTS permission not granted")
                emptyList()
            }

        /**
         * Returns the human-readable names of all Google accounts on the device.
         * Used by the account-picker dialog in the UI.
         */
        fun listGoogleAccountNames(): List<String> = listGoogleAccounts().map { it.name }

        /**
         * Fetches an OAuth2 access token for [accountEmail] scoped to `drive.file`.
         *
         * Blocks the calling thread — callers MUST be on a background thread. AccountManager
         * may show a system " granting permission" dialog on first run.
         *
         * Returns null if the user denies the permission grant or the account is no longer
         * present on the device.
         */
        @Suppress("DEPRECATION")
        private fun fetchAuthToken(accountEmail: String): String? {
            val account =
                AccountManager.get(context).getAccountsByType("com.google").firstOrNull {
                    it.name == accountEmail
                } ?: return null
            // AccountManager.getAuthToken blocks the calling thread and may surface a
            // permission-grant dialog if the user hasn't yet approved this app for the
            // requested scope. We pass null for the notify AuthenticatorException callback
            // (we're not on the main thread) and rely on the system to show a notification
            // instead if the user needs to take action.
            val result =
                AccountManager
                    .get(context)
                    .getAuthToken(
                        account,
                        "oauth2:https://www.googleapis.com/auth/drive.file",
                        null,
                        false,
                        null,
                        null,
                    )
            return result?.getResult()?.getString(AccountManager.KEY_AUTHTOKEN)
        }

        /**
         * Invalidates the cached OAuth token for [token] so a subsequent [fetchAuthToken] call
         * re-fetches from the system. Called when the Drive API returns 401 — typically because
         * the user revoked the permission via the Google account security page.
         */
        private fun invalidateAuthToken(token: String) {
            AccountManager.get(context).invalidateAuthToken("com.google", token)
        }

        /**
         * Uploads a single backup file to Google Drive under the configured folder.
         *
         * Steps:
         *   1. Fetch OAuth token for the configured account.
         *   2. Create a temp .backup file locally via [CreateBackupUseCase].
         *   3. If [settings].overwriteExisting and a file with the same name already exists in
         *      the target folder, PATCH the existing file's content. Otherwise, POST a new
         *      multipart upload.
         *   4. Delete the temp file.
         *
         * Returns the Drive file ID on success, or null on failure (failure details are logged).
         *
         * @param settings The current Google Drive sync settings — must have a non-null
         *   `accountEmail` (caller's responsibility to check before invoking).
         * @param backupFileName The user-visible name to give the uploaded Drive file (without
         *   extension — we append `.backup` here for consistency with local backups).
         */
        suspend fun uploadBackup(settings: GoogleDriveSyncSettings, backupFileName: String): String? =
            withContext(Dispatchers.IO) {
                val accountEmail = settings.accountEmail ?: return@withContext null
                val tempDir = File(context.cacheDir, "gdrive_backup").apply { mkdirs() }
                val tempFile = File(tempDir, "${System.currentTimeMillis()}_upload.backup")
                try {
                    // Stage 1: build the local backup into a temp file. We use a content://
                    // URI pointing at the temp file so CreateBackupUseCase (which expects a
                    // SAF Uri) can write to it via ContentResolver. The FileProvider
                    // registered in AndroidManifest handles the URI exposure.
                    val tempUri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.FileProvider",
                        tempFile,
                    )
                    createBackupUseCase(
                        uri = tempUri,
                        categories = BackupArchiveCategory.entries.toSet(),
                    )

                    // Stage 2: fetch an OAuth token and upload.
                    val token = fetchAuthToken(accountEmail) ?: run {
                        Timber.w("GoogleDriveClient: failed to obtain OAuth token for %s", accountEmail)
                        return@withContext null
                    }
                    val fullFileName = "$backupFileName.backup"
                    val folderId = settings.remoteFolderId // null = root of My Drive

                    val existingFileId =
                        if (settings.overwriteExisting) {
                            findFileByName(token, fullFileName, folderId)
                        } else {
                            null
                        }

                    val fileId =
                        if (existingFileId != null) {
                            patchFileContent(token, existingFileId, tempFile)
                        } else {
                            uploadNewFile(token, fullFileName, folderId, tempFile)
                        }

                    if (fileId == null) {
                        // Token may have been revoked — try one more time with a fresh token.
                        invalidateAuthToken(token)
                        val freshToken = fetchAuthToken(accountEmail) ?: return@withContext null
                        val retryFileId =
                            if (existingFileId != null) {
                                patchFileContent(freshToken, existingFileId, tempFile)
                            } else {
                                uploadNewFile(freshToken, fullFileName, folderId, tempFile)
                            }
                        retryFileId
                    } else {
                        fileId
                    }
                } catch (cancellation: kotlinx.coroutines.CancellationException) {
                    throw cancellation
                } catch (e: Exception) {
                    Timber.w(e, "GoogleDriveClient.uploadBackup failed")
                    null
                } finally {
                    runCatching { tempFile.delete() }
                }
            }

        /**
         * Queries Drive for a file with the given [name] under [folderId] (or root if null).
         * Returns the first matching file ID, or null if none exists.
         *
         * Drive's `files.list` endpoint accepts a `q` parameter with a query like:
         *   `name = 'AppName.backup' and trashed = false and 'root' in parents`
         */
        private fun findFileByName(token: String, name: String, folderId: String?): String? {
            val escapedName = name.replace("'", "\\'")
            val parentClause =
                if (folderId == null) "'root' in parents" else "'$folderId' in parents"
            val query = "name = '$escapedName' and trashed = false and $parentClause"
            val url =
                "https://www.googleapis.com/drive/v3/files?q=" +
                    URLEncoder.encode(query, "UTF-8") +
                    "&fields=files(id,name)&pageSize=1"
            val request =
                Request
                    .Builder()
                    .url(url)
                    .header("Authorization", "Bearer $token")
                    .get()
                    .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.w("Drive files.list failed: %d %s", response.code, response.message)
                    return null
                }
                val body = response.body?.string().orEmpty()
                val files = JSONObject(body).optJSONArray("files") ?: return null
                if (files.length() == 0) return null
                return files.optJSONObject(0)?.optString("id")?.takeIf { it.isNotBlank() }
            }
        }

        /**
         * Uploads a new file via Drive's multipart upload endpoint.
         *
         * The multipart body has two parts:
         *   1. `application/json; charset=UTF-8` — the file metadata (name, parents).
         *   2. `application/octet-stream` — the raw file content.
         *
         * Returns the new file ID from the response, or null on failure.
         */
        private fun uploadNewFile(token: String, name: String, folderId: String?, file: File): String? {
            val metadata =
                JSONObject().apply {
                    put("name", name)
                    if (folderId != null) {
                        val parents = JSONArray()
                        parents.put(folderId)
                        put("parents", parents)
                    }
                }
            val metadataBody =
                metadata
                    .toString()
                    .toRequestBody("application/json; charset=UTF-8".toMediaType())
            val fileBody = file.asRequestBody("application/octet-stream".toMediaType())
            val multipart =
                MultipartBody
                    .Builder()
                    .setType(MultipartBody.RELATED)
                    .addPart(metadataBody)
                    .addPart(fileBody)
                    .build()
            val request =
                Request
                    .Builder()
                    .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id")
                    .header("Authorization", "Bearer $token")
                    .post(multipart)
                    .build()
            return executeForFileId(request)
        }

        /**
         * Updates an existing file's content via Drive's PATCH media-upload endpoint.
         * The URL path includes the existing file ID so Drive knows which file to replace.
         */
        private fun patchFileContent(token: String, fileId: String, file: File): String? {
            val fileBody = file.asRequestBody("application/octet-stream".toMediaType())
            val request =
                Request
                    .Builder()
                    .url("https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media&fields=id")
                    .header("Authorization", "Bearer $token")
                    .patch(fileBody)
                    .build()
            return executeForFileId(request)
        }

        private fun executeForFileId(request: Request): String? {
            return try {
                httpClient.newCall(request).execute().use { response: Response ->
                    if (!response.isSuccessful) {
                        Timber.w("Drive API call failed: %d %s", response.code, response.message)
                        return null
                    }
                    JSONObject(response.body?.string().orEmpty()).optString("id").takeIf { it.isNotBlank() }
                }
            } catch (io: IOException) {
                Timber.w(io, "Drive API call threw IOException")
                null
            }
        }
    }
