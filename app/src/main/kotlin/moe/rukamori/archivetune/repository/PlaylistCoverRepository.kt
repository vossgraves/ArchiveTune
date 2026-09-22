/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 * Portions © vossgraves — github.com/vossgraves
 */

package moe.rukamori.archivetune.repository

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.PlaylistEntity
import moe.rukamori.archivetune.innertube.YouTube
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

@Singleton
class PlaylistCoverRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val database: MusicDatabase,
    ) {
        suspend fun getPlaylist(playlistId: String): PlaylistEntity? =
            withContext(Dispatchers.IO) {
                database.playlist(playlistId).first()?.playlist
            }

        suspend fun setLocalCover(
            playlist: PlaylistEntity,
            uri: Uri,
        ) = withContext(Dispatchers.IO) {
            // Copy the picked image into app storage rather than holding a permission on wherever
            // the user picked it from. A persisted `content://` grant survives reboots but not the
            // picker's provider being cleared, uninstalled or moved, and the cover then renders as
            // a blank square with no way to tell why — a copy cannot be revoked out from under us.
            // The permission route stays as the fallback for an image too large or too odd to copy.
            // No grant is taken for the picked image on this route, and none is dropped either: a
            // grant is per-URI rather than per-playlist, so releasing it here would strip the
            // permission a legacy row still pointing at the same image needs. A grant this row did
            // hold goes with the rest of its resources below.
            val copiedCoverUri = runCatching { copyCoverIntoAppStorage(playlist.id, uri) }.getOrNull()
            if (copiedCoverUri != null) {
                val previous = updateThumbnail(playlist.id, copiedCoverUri.toString())
                // The copy is named from the playlist id alone, so re-picking overwrites the file
                // the row already pointed at: releasing it here would delete the bytes just written
                // and leave the cover blank. The fallback route guards the same case.
                if (previous.thumbnailUrl != copiedCoverUri.toString()) {
                    releasePreviousCoverResources(previous)
                }
            } else {
                persistReadPermission(uri)
                try {
                    val previous = updateThumbnail(playlist.id, uri.toString())
                    // Re-picking the same image would otherwise tear down the grant this call just
                    // took, leaving the cover unreadable the next time the page opens.
                    if (previous.thumbnailUrl != uri.toString()) {
                        releasePreviousCoverResources(previous)
                    }
                } catch (throwable: Throwable) {
                    releaseReadPermission(uri)
                    throw throwable
                }
            }
        }

        suspend fun setRemoteCover(
            playlist: PlaylistEntity,
            uri: Uri,
        ) = withContext(Dispatchers.IO) {
            persistReadPermission(uri)
            try {
                val image = decodeUploadImage(uri)
                val remoteCoverUrl =
                    YouTube
                        .uploadCustomPlaylistCover(
                            playlistId = requireNotNull(playlist.browseId),
                            image = image,
                        ).getOrThrow()
                val previous = updateThumbnail(playlist.id, remoteCoverUrl)
                releasePreviousCoverResources(previous)
            } finally {
                releaseReadPermission(uri)
            }
        }

        suspend fun removeLocalCover(playlist: PlaylistEntity) =
            withContext(Dispatchers.IO) {
                val previous = updateThumbnail(playlist.id, null)
                releasePreviousCoverResources(previous)
            }

        suspend fun removeRemoteCover(playlist: PlaylistEntity) =
            withContext(Dispatchers.IO) {
                val remoteCoverUrl =
                    YouTube
                        .removeCustomPlaylistCover(requireNotNull(playlist.browseId))
                        .getOrThrow()
                val previous = updateThumbnail(playlist.id, remoteCoverUrl)
                releasePreviousCoverResources(previous)
            }

        /**
         * Releases what the cover of [playlist] holds once its row is gone: the copy this repository
         * wrote under [MANAGED_COVER_DIR], and a persisted read grant on the `content://` image it
         * held if it was on the permission fallback. Deleting a playlist is the one other way the
         * two stop being reachable — the row that named them goes with them — so the delete path
         * calls here; without it every deleted copy would sit in `filesDir` for the life of the
         * install, up to 2 MB at a time.
         */
        suspend fun releasePlaylistCoverResources(playlist: PlaylistEntity) =
            withContext(Dispatchers.IO) {
                releasePreviousCoverResources(playlist)
            }

        private suspend fun updateThumbnail(
            playlistId: String,
            thumbnailUrl: String?,
        ): PlaylistEntity {
            val current =
                requireNotNull(database.playlist(playlistId).first()?.playlist) {
                    "Playlist $playlistId no longer exists"
                }
            database.withTransaction {
                update(
                    current.copy(
                        thumbnailUrl = thumbnailUrl,
                        lastUpdateTime = LocalDateTime.now(),
                    ),
                )
            }
            return current
        }

        /**
         * Hands back whatever the cover being replaced was holding: a persisted read grant for the
         * `content://` image it pointed at, and the copy this repository made of it. Both are
         * per-playlist and never shared, so a cover replaced or removed is the last user of them.
         */
        private fun releasePreviousCoverResources(previous: PlaylistEntity) {
            previous.thumbnailUrl
                ?.let(Uri::parse)
                ?.let { previousUri ->
                    if (previousUri.scheme == "content") {
                        releaseReadPermission(previousUri)
                    }
                    deleteManagedCoverFile(previousUri)
                }
        }

        /**
         * Decodes the picked image the way an upload would and writes it under [MANAGED_COVER_DIR],
         * returning the `file://` uri to store as the playlist's thumbnail. Decoding first means an
         * unreadable pick fails here, before the playlist row has been pointed at a file that was
         * never written.
         */
        private fun copyCoverIntoAppStorage(
            playlistId: String,
            uri: Uri,
        ): Uri {
            val bytes = decodeUploadImage(uri)
            val dir = File(context.filesDir, MANAGED_COVER_DIR)
            dir.mkdirs()
            val fileName = playlistId.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".jpg"
            val target = File(dir, fileName)
            target.writeBytes(bytes)
            return Uri.fromFile(target)
        }

        /**
         * Deletes a cover copy this repository wrote, and nothing else: the canonical-path check
         * keeps a `file://` thumbnail from anywhere else — a user's own pick, a migrated row — out
         * of reach of a cleanup pass.
         */
        private fun deleteManagedCoverFile(uri: Uri) {
            if (uri.scheme != "file") return
            val path = uri.path ?: return
            val file = File(path)
            val managedDir = File(context.filesDir, MANAGED_COVER_DIR)
            // The whole predicate is guarded, not just the delete: `canonicalPath` walks the
            // filesystem and throws on a path it cannot resolve, which would otherwise escape a
            // cover change and fail the update it is only cleaning up after.
            val isManaged =
                runCatching {
                    file.canonicalPath.startsWith(managedDir.canonicalPath + File.separator)
                }.getOrDefault(false)
            if (!isManaged) return
            runCatching { file.delete() }
        }

        private fun persistReadPermission(uri: Uri) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }

        private fun releaseReadPermission(uri: Uri) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }

        private fun decodeUploadImage(uri: Uri): ByteArray {
            val decoded =
                context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFileDescriptor(descriptor.fileDescriptor, null, options)
                    require(options.outWidth > 0 && options.outHeight > 0) { "Selected playlist cover is not a valid image" }

                    var sampleSize = 1
                    while (
                        options.outWidth.toLong() / sampleSize *
                        (options.outHeight.toLong() / sampleSize) > MAX_DECODE_PIXELS
                    ) {
                        sampleSize *= 2
                    }

                    options.inJustDecodeBounds = false
                    options.inSampleSize = sampleSize
                    val bitmap =
                        BitmapFactory.decodeFileDescriptor(descriptor.fileDescriptor, null, options)
                            ?: throw IllegalArgumentException("Selected playlist cover could not be decoded")
                    rotateFromExif(bitmap, descriptor.fileDescriptor)
                } ?: throw IllegalArgumentException("Selected playlist cover could not be opened")

            val squareSize = min(decoded.width, decoded.height)
            val cropped =
                Bitmap.createBitmap(
                    decoded,
                    (decoded.width - squareSize) / 2,
                    (decoded.height - squareSize) / 2,
                    squareSize,
                    squareSize,
                )
            if (cropped !== decoded) decoded.recycle()

            val uploadBitmap =
                if (cropped.width > UPLOAD_DIMENSION_PX) {
                    Bitmap.createScaledBitmap(cropped, UPLOAD_DIMENSION_PX, UPLOAD_DIMENSION_PX, true).also {
                        if (it !== cropped) cropped.recycle()
                    }
                } else {
                    cropped
                }

            return try {
                compressWithinLimit(uploadBitmap)
            } finally {
                uploadBitmap.recycle()
            }
        }

        private fun rotateFromExif(
            bitmap: Bitmap,
            fileDescriptor: java.io.FileDescriptor,
        ): Bitmap {
            val rotation =
                when (readExifOrientation(fileDescriptor)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            if (rotation == 0f) return bitmap

            return Bitmap
                .createBitmap(
                    bitmap,
                    0,
                    0,
                    bitmap.width,
                    bitmap.height,
                    Matrix().apply { postRotate(rotation) },
                    true,
                ).also { rotated ->
                    if (rotated !== bitmap) bitmap.recycle()
                }
        }

        private fun readExifOrientation(fileDescriptor: java.io.FileDescriptor): Int =
            runCatching {
                ExifInterface(fileDescriptor).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        private fun compressWithinLimit(bitmap: Bitmap): ByteArray {
            val output = ByteArrayOutputStream()
            var quality = INITIAL_JPEG_QUALITY
            do {
                output.reset()
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output))
                quality -= JPEG_QUALITY_STEP
            } while (output.size() > MAX_UPLOAD_BYTES && quality >= MIN_JPEG_QUALITY)

            require(output.size() <= MAX_UPLOAD_BYTES) { "Selected playlist cover is too large" }
            return output.toByteArray()
        }

        private companion object {
            /** Where local cover copies live under `filesDir`, and nowhere else may be deleted. */
            const val MANAGED_COVER_DIR = "playlist_covers"
            const val UPLOAD_DIMENSION_PX = 1080
            const val MAX_UPLOAD_BYTES = 2 * 1024 * 1024
            const val MAX_DECODE_PIXELS = 8_000_000L
            const val INITIAL_JPEG_QUALITY = 92
            const val MIN_JPEG_QUALITY = 60
            const val JPEG_QUALITY_STEP = 8
        }
    }
