/*
 * ArchiveTune (2026)
 * © ArchiveTuneFork contributors — github.com/vossgraves/ArchiveTune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package app.atf.media.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.innertube.YouTube
import java.time.LocalDateTime

@Immutable
@Entity(
    tableName = "song",
    indices = [
        Index(
            value = ["albumId"],
        ),
    ],
)
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    @ColumnInfo(defaultValue = "0")
    val titleOverride: Boolean = false,
    val duration: Int = -1, // in seconds
    val thumbnailUrl: String? = null,
    val albumId: String? = null,
    val albumName: String? = null,
    @ColumnInfo(defaultValue = "0")
    val explicit: Boolean = false,
    val year: Int? = null,
    val date: LocalDateTime? = null, // ID3 tag property
    val dateModified: LocalDateTime? = null, // file property
    val liked: Boolean = false,
    val likedDate: LocalDateTime? = null,
    val totalPlayTime: Long = 0, // in milliseconds
    val inLibrary: LocalDateTime? = null,
    val dateDownload: LocalDateTime? = LocalDateTime.now(),
    @ColumnInfo(name = "isMusicVideo", defaultValue = "0")
    val isMusicVideo: Boolean = false,
    @ColumnInfo(name = "isLocal", defaultValue = "0")
    val isLocal: Boolean = false,
    // Set when the user has chosen "Don't recommend this song again" from the song overflow
    // menu. Recommendations / discovery feeds filter out songs where this is non-null, so
    // the user can permanently banish a track from auto-generated radio/mixes regardless of
    // artist. Mirrors ArtistEntity.blockedAt semantics. Cleared by re-tapping the menu item.
    val blockedAt: LocalDateTime? = null,
) {
    fun localToggleLike() =
        copy(
            liked = !liked,
            likedDate = if (!liked) LocalDateTime.now() else null,
        )

    fun toggleLike() =
        if (isLocal) {
            localToggleLike()
        } else {
            copy(
                liked = !liked,
                likedDate = if (!liked) LocalDateTime.now() else null,
                inLibrary = if (!liked) inLibrary ?: LocalDateTime.now() else inLibrary,
            ).also {
                CoroutineScope(Dispatchers.IO).launch {
                    YouTube.likeVideo(id, !liked)
                    this.cancel()
                }
            }
        }

    fun toggleLibrary() =
        copy(
            liked = if (inLibrary == null) liked else false,
            inLibrary = if (inLibrary == null) LocalDateTime.now() else null,
            likedDate = if (inLibrary == null) likedDate else null,
        )
}
