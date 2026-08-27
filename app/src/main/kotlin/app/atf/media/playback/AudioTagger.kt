/*
 * ArchiveTune (2026)
 * © ArchiveTuneFork contributors — github.com/vossgraves/ArchiveTune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package app.atf.media.playback

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.flac.FlacTag
import org.jaudiotagger.tag.id3.ID3v22Tag
import org.jaudiotagger.tag.id3.ID3v23Tag
import org.jaudiotagger.tag.id3.ID3v24Tag
import org.jaudiotagger.tag.images.ArtworkFactory
import org.jaudiotagger.tag.reference.PictureTypes
import org.jaudiotagger.tag.mp4.Mp4Tag
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentTag
import timber.log.Timber
import java.io.File

/**
 * Writes ID3 / Vorbis-Comment / MP4 / FLAC metadata tags onto a
 * downloaded audio file using the [jaudiotagger](https://github.com/RouHim/jaudiotagger)
 * library.
 *
 * Used by [ExportDownloadedSongsScreen][app.atf.media.ui.screens.settings.ExportDownloadedSongsScreen]
 * when exporting cached songs to a SAF folder — the user picks a
 * destination, the cached spans are assembled into a single temp file,
 * [tag] is called to write title / artist / album / year / track tags
 * plus embedded artwork, and the tagged file is then copied to the SAF
 * document.
 *
 * ## Why jaudiotagger?
 *
 * jaudiotagger supports every audio container ArchiveTune can download
 * (MP3, FLAC, M4A/AAC, OGG/Opus, WAV) under a single uniform API
 * ([FieldKey] enum maps to format-specific tag keys internally). The
 * alternative — writing format-specific taggers by hand — would mean
 * ~5× the code and 5× the bug surface.
 *
 * ## Artwork handling — the Android `javax.imageio` trap
 *
 * jaudiotagger's [Artwork] / [StandardImageHandler][org.jaudiotagger.tag.images.StandardImageHandler]
 * API goes through `javax.imageio.ImageIO` for *some* operations
 * (notably [Artwork.setImageFromData] and [Artwork.setFromFile]),
 * which does not exist on Android. Calling those methods throws
 * `NoClassDefFoundError` at runtime.
 *
 * To write artwork safely on Android, we use **format-specific
 * raw-bytes APIs** that bypass `ImageIO` entirely:
 *
 *  - **FLAC** ([FlacTag]): [FlacTag.createArtworkField] takes raw
 *    `byte[]` + dimensions directly — no `ImageIO` call. We pass
 *    `0` for all dimension fields (most players ignore them or
 *    read them from the image bytes themselves).
 *  - **MP4/M4A** ([Mp4Tag]): [Mp4Tag.createArtworkField] takes raw
 *    `byte[]` only.
 *  - **MP3** ([ID3v24Tag] / [ID3v23Tag] / [ID3v22Tag]):
 *    [org.jaudiotagger.tag.id3.AbstractID3v2Tag.setField] with an
 *    [Artwork] whose `binaryData` is set but `image` is never
 *    loaded. Internally, `createField(Artwork)` reads
 *    [Artwork.getBinaryData] directly — it does NOT call
 *    [Artwork.getImage] or [Artwork.setImageFromData], so no
 *    `ImageIO` call happens.
 *  - **OGG/Opus** ([VorbisCommentTag]):
 *    [VorbisCommentTag.setArtworkField] takes raw `byte[]` +
 *    mime type, base64-encodes the data into a
 *    `METADATA_BLOCK_PICTURE`-style field. No `ImageIO`.
 *
 * If the tag type doesn't match any of the above (e.g. ASF/WMA,
 * which ArchiveTune never downloads), artwork is silently skipped
 * — the text tags are still written.
 *
 * ## Failure isolation
 *
 * Every call is wrapped in [runCatching] — if jaudiotagger throws
 * (e.g. corrupt file, unsupported format, tag-readonly), the export
 * still succeeds with the *untagged* temp file. The audio bytes are
 * never modified by [tag]; jaudiotagger only writes the tag chunk.
 */
object AudioTagger {

    /**
     * Metadata to write. All fields are optional — blank/null fields
     * are skipped so we don't overwrite existing tags with empty strings.
     */
    data class Metadata(
        val title: String? = null,
        val artist: String? = null,
        val albumArtist: String? = null,
        val album: String? = null,
        val year: Int? = null,
        val trackNumber: Int? = null,
        val trackTotal: Int? = null,
        val discNumber: Int? = null,
        val discTotal: Int? = null,
        val genre: String? = null,
        val composer: String? = null,
        val isrc: String? = null,
        val comment: String? = null,
        /**
         * Raw bytes of the embedded artwork (typically JPEG or PNG).
         * Null skips artwork embedding. The mime type is derived from
         * the [artworkMimeType] field, or guessed from the JPEG magic
         * bytes if null.
         */
        val artworkBytes: ByteArray? = null,
        val artworkMimeType: String? = null,
    )

    /**
     * Reads the existing tag (if any) from [file] and writes the
     * non-blank fields from [metadata] onto it, then persists the
     * file in place. Returns `true` on success, `false` on any error
     * (the file is left untouched on error — jaudiotagger writes to
     * a temp file and renames, so a partial write cannot corrupt
     * the source).
     *
     * Safe to call from a background thread. Not safe to call from
     * the main thread — jaudiotagger does disk I/O.
     */
    fun tag(file: File, metadata: Metadata): Boolean {
        if (!file.exists() || file.length() == 0L) return false
        return runCatching {
            val audioFile = AudioFileIO.read(file)
            // Use the explicit Java getter call instead of the synthetic
            // `tagOrCreateAndSetDefault` property access — Kotlin 2.4
            // misinterprets `audioFile.tagOrCreateAndSetDefault()` as
            // property-access + invoke(), which yields an unresolved type
            // for `tag` and cascades into "Unresolved reference 'setField'"
            // on every field-write below. Calling the Java getter directly
            // avoids the property-access synthesis and returns Tag cleanly.
            val tag = audioFile.getTagOrCreateAndSetDefault()
            metadata.title?.takeIf(String::isNotBlank)?.let { tag.setField(FieldKey.TITLE, it) }
            metadata.artist?.takeIf(String::isNotBlank)?.let { tag.setField(FieldKey.ARTIST, it) }
            metadata.albumArtist?.takeIf(String::isNotBlank)?.let { tag.setField(FieldKey.ALBUM_ARTIST, it) }
            metadata.album?.takeIf(String::isNotBlank)?.let { tag.setField(FieldKey.ALBUM, it) }
            metadata.year?.takeIf { it > 0 }?.let { tag.setField(FieldKey.YEAR, it.toString()) }
            metadata.trackNumber?.takeIf { it > 0 }?.let { tag.setField(FieldKey.TRACK, it.toString()) }
            metadata.trackTotal?.takeIf { it > 0 }?.let { tag.setField(FieldKey.TRACK_TOTAL, it.toString()) }
            metadata.discNumber?.takeIf { it > 0 }?.let { tag.setField(FieldKey.DISC_NO, it.toString()) }
            metadata.discTotal?.takeIf { it > 0 }?.let { tag.setField(FieldKey.DISC_TOTAL, it.toString()) }
            metadata.genre?.takeIf(String::isNotBlank)?.let { tag.setField(FieldKey.GENRE, it) }
            metadata.composer?.takeIf(String::isNotBlank)?.let { tag.setField(FieldKey.COMPOSER, it) }
            metadata.isrc?.takeIf(String::isNotBlank)?.let { tag.setField(FieldKey.ISRC, it) }
            metadata.comment?.takeIf(String::isNotBlank)?.let { tag.setField(FieldKey.COMMENT, it) }
            // Artwork is written via format-specific raw-bytes APIs to
            // bypass the javax.imageio ImageIO calls that don't exist
            // on Android (see class kdoc for details).
            metadata.artworkBytes?.let { writeArtworkSafely(tag, it, metadata.artworkMimeType) }
            audioFile.commit()
            true
        }.getOrElse { e ->
            Timber.w(e, "AudioTagger failed to tag %s", file.absolutePath)
            false
        }
    }

    /**
     * Writes [bytes] as embedded artwork onto [tag], using the
     * format-specific raw-bytes API that bypasses `javax.imageio.ImageIO`
     * (which doesn't exist on Android).
     *
     * The format is detected by checking the runtime type of [tag]:
     *
     *  - [FlacTag] → [FlacTag.createArtworkField] (raw bytes + dimensions)
     *  - [Mp4Tag] → [Mp4Tag.createArtworkField] (raw bytes only)
     *  - [ID3v24Tag] / [ID3v23Tag] / [ID3v22Tag] → `setField(Artwork)`
     *    with binary data set (the ID3 implementation reads
     *    `getBinaryData()` directly — no `ImageIO` call)
     *  - [VorbisCommentTag] → [VorbisCommentTag.setArtworkField]
     *    (raw bytes + mime type, base64-encoded)
     *
     * For any other tag type (e.g. ASF/WMA, which ArchiveTune never
     * downloads), artwork is silently skipped.
     */
    private fun writeArtworkSafely(tag: Tag, bytes: ByteArray, mimeType: String?) {
        val resolvedMime = mimeType?.takeIf(String::isNotBlank) ?: guessImageMimeType(bytes)
        runCatching {
            when (tag) {
                is FlacTag -> {
                    val field = tag.createArtworkField(
                        /* imageData = */ bytes,
                        /* pictureType = */ PictureTypes.DEFAULT_ID,
                        /* mimeType = */ resolvedMime,
                        /* description = */ "",
                        /* width = */ 0,
                        /* height = */ 0,
                        /* colourDepth = */ 0,
                        /* indexedColouredCount = */ 0,
                    )
                    tag.setField(field)
                }
                is Mp4Tag -> {
                    val field = tag.createArtworkField(bytes)
                    tag.setField(field)
                }
                is ID3v24Tag, is ID3v23Tag, is ID3v22Tag -> {
                    // createField(Artwork) on AbstractID3v2Tag reads
                    // artwork.getBinaryData() directly and writes it into
                    // the APIC frame — it does NOT call artwork.getImage()
                    // or artwork.setImageFromData(), so javax.imageio is
                    // never touched. Safe on Android.
                    val artwork = ArtworkFactory.getNew().apply {
                        setBinaryData(bytes)
                        setMimeType(resolvedMime)
                        setPictureType(PictureTypes.DEFAULT_ID)
                        setDescription("")
                    }
                    tag.setField(artwork)
                }
                is VorbisCommentTag -> {
                    tag.setArtworkField(bytes, resolvedMime)
                }
                else -> {
                    Timber.w("AudioTagger: skipping artwork for unsupported tag type %s", tag.javaClass.name)
                }
            }
        }.onFailure { e ->
            Timber.w(e, "AudioTagger: failed to write artwork (%d bytes, %s)", bytes.size, resolvedMime)
        }
    }

    /**
     * Guesses the image MIME type from the magic bytes of [bytes].
     * Falls back to "image/jpeg" if unknown (most common case for
     * album covers from Qobuz/Tidal/iTunes/Deezer).
     */
    private fun guessImageMimeType(bytes: ByteArray): String {
        if (bytes.size < 4) return "image/jpeg"
        return when {
            // JPEG: FF D8 FF
            (bytes[0].toInt() and 0xFF) == 0xFF &&
                (bytes[1].toInt() and 0xFF) == 0xD8 &&
                (bytes[2].toInt() and 0xFF) == 0xFF -> "image/jpeg"
            // PNG: 89 50 4E 47
            (bytes[0].toInt() and 0xFF) == 0x89 &&
                bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() &&
                bytes[3] == 0x47.toByte() -> "image/png"
            // WebP: "RIFF"...."WEBP"
            bytes.size >= 12 &&
                bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() &&
                bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte() &&
                bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte() &&
                bytes[10] == 0x42.toByte() && bytes[11] == 0x50.toByte() -> "image/webp"
            else -> "image/jpeg"
        }
    }
}
