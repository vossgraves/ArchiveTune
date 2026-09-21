package moe.rukamori.archivetune.models

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.ObjectStreamClass
import java.time.LocalDateTime

/**
 * The queue files under `filesDir` are written with Java serialisation and read back by a later
 * build of the app, so the classes in that graph are an on-disk format. An explicit
 * `serialVersionUID` is what keeps the format stable: without one the value is computed from the
 * fields, methods and constructors, so adding a field silently renames the format and every
 * stored queue fails to load with `InvalidClassException`.
 *
 * Changing a number below is a deliberate format break — it makes every queue already on a user's
 * device unreadable, which is exactly the regression these constants exist to prevent.
 */
class PersistQueueSerialisationTest {
    @Test
    fun `every serialised class pins the on-disk format id`() {
        val pinned =
            mapOf(
                PersistQueue::class.java to 1L,
                QueueType.LIST::class.java to 1L,
                QueueType.YOUTUBE::class.java to 1L,
                QueueType.YOUTUBE_ALBUM_RADIO::class.java to 1L,
                QueueType.LOCAL_ALBUM_RADIO::class.java to 1L,
                QueueData.YouTubeData::class.java to 2L,
                QueueData.YouTubeAlbumRadioData::class.java to 1L,
                QueueData.LocalAlbumRadioData::class.java to 1L,
                MediaMetadata::class.java to 1L,
                MediaMetadata.Artist::class.java to 1L,
                MediaMetadata.Album::class.java to 1L,
                PersistPlayerState::class.java to 1L,
            )

        pinned.forEach { (type, expected) ->
            assertEquals(type.name, expected, ObjectStreamClass.lookup(type).serialVersionUID)
        }
    }

    @Test
    fun `a persisted queue survives a write and read of every queue type`() {
        val items =
            listOf(
                MediaMetadata(
                    id = "song-1",
                    title = "Song",
                    artists = listOf(MediaMetadata.Artist(id = "artist-1", name = "Artist")),
                    duration = 210,
                    album = MediaMetadata.Album(id = "album-1", title = "Album"),
                    likedDate = LocalDateTime.of(2026, 1, 2, 3, 4, 5),
                ),
            )

        val queues =
            listOf(
                PersistQueue("YouTube", items, 0, 0L, QueueType.YOUTUBE, QueueData.YouTubeData("video-1")),
                PersistQueue(
                    "Album radio",
                    items,
                    0,
                    1_500L,
                    QueueType.YOUTUBE_ALBUM_RADIO,
                    QueueData.YouTubeAlbumRadioData("playlist-1", continuation = "token"),
                ),
                PersistQueue(
                    "Local album radio",
                    items,
                    0,
                    9_000L,
                    QueueType.LOCAL_ALBUM_RADIO,
                    QueueData.LocalAlbumRadioData("album-1", startIndex = 3),
                ),
                PersistQueue("Plain", items, 0, 0L),
            )

        queues.forEach { queue ->
            assertEquals(queue, roundTrip(queue))
        }
    }

    private fun roundTrip(queue: PersistQueue): PersistQueue {
        val bytes =
            ByteArrayOutputStream().use { buffer ->
                ObjectOutputStream(buffer).use { it.writeObject(queue) }
                buffer.toByteArray()
            }
        return ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as PersistQueue }
    }
}
