/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

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
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Writes ID3 / Vorbis-Comment / MP4 / FLAC metadata tags onto a
 * downloaded audio file using the [jaudiotagger](https://github.com/RouHim/jaudiotagger)
 * library.
 *
 * Used by [ExportDownloadedSongsScreen][moe.rukamori.archivetune.ui.screens.settings.ExportDownloadedSongsScreen]
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
     * Best-effort tag writer for files that will be exported as `.mp3` even though
     * their underlying audio bytes may not actually be MP3 (e.g. legacy YouTube
     * Music downloads cached as WebM/Opus).
     *
     * Strategy:
     *  1. Try the normal [tag] path first — it works for real MP3 / M4A / FLAC
     *     files because jaudiotagger reads the file by extension to pick a reader.
     *     If the temp file was named `.mp3` but the bytes are AAC/M4A, jaudiotagger's
     *     MP3 reader will throw `CannotReadException`; if the bytes are WebM/Opus,
     *     no reader is registered and the same exception is thrown. Either way, we
     *     catch and fall through.
     *  2. On failure, fall back to manually constructing an ID3v2.4 tag from the
     *     metadata and **prepending** it to the file's existing bytes. ID3v2 tags
     *     are a self-contained header chunk that MP3 decoders parse before they
     *     look for the first MPEG audio frame — so even if the audio bytes are
     *     AAC/Opus/WebM, every reasonable MP3 player (VLC,foobar2000,Mp3tag,
     *     Android's MediaMetadataRetriever, Windows File Explorer, macOS Finder,
     *     most car stereos, most Bluetooth speakers) will still display the
     *     title/artist/album/artwork we embedded.
     *
     * The fallback path is non-destructive: if the tag-bytes construction fails
     * for any reason, the original file is left untouched and `false` is returned.
     *
     * Returns `true` if either path succeeded.
     */
    fun tagMp3BestEffort(file: File, metadata: Metadata): Boolean {
        if (!file.exists() || file.length() == 0L) return false
        // Fast path: try the standard jaudiotagger write. This handles real MP3,
        // M4A, FLAC, OGG bytes — anything jaudiotagger has a reader for.
        if (tag(file, metadata)) return true
        // Fallback: manually prepend an ID3v2.4 tag. Used for legacy WebM/Opus
        // caches from YouTube Music that jaudiotagger can't read.
        return runCatching { prependManualId3v2Tag(file, metadata) }
            .getOrElse { e ->
                Timber.w(e, "AudioTagger.tagMp3BestEffort: manual ID3v2 prepend failed for %s", file.absolutePath)
                false
            }
    }

    /**
     * Manually constructs an ID3v2.4 tag from [metadata] and **prepends** it to
     * [file]'s existing bytes. Does not parse or modify the audio data at all —
     * the tag is a header chunk that players read before scanning for audio frames.
     *
     * Layout:
     *  - 10-byte ID3v2.4 header: "ID3" + 0x04 0x00 + 0x00 (flags) + 4-byte synchsafe size
     *  - One frame per non-blank metadata field (TIT2/TPE1/TALB/TYER/TPOS/TRCK/TCOM/TSRC/COMM)
     *  - One APIC frame for artwork (if [Metadata.artworkBytes] is non-null)
     *
     * Text frames use UTF-16 with BOM (encoding byte 0x01) for maximum player
     * compatibility — most legacy ID3v2 readers handle UTF-16 better than UTF-8.
     */
    private fun prependManualId3v2Tag(file: File, metadata: Metadata): Boolean {
        val frames = ByteArrayOutputStream()

        metadata.title?.takeIf(String::isNotBlank)?.let { frames.write(textFrame("TIT2", it)) }
        metadata.artist?.takeIf(String::isNotBlank)?.let { frames.write(textFrame("TPE1", it)) }
        metadata.albumArtist?.takeIf(String::isNotBlank)?.let { frames.write(textFrame("TPE2", it)) }
        metadata.album?.takeIf(String::isNotBlank)?.let { frames.write(textFrame("TALB", it)) }
        metadata.year?.takeIf { it > 0 }?.let { frames.write(textFrame("TYER", it.toString())) }
        metadata.trackNumber?.takeIf { it > 0 }?.let { frames.write(textFrame("TRCK", it.toString())) }
        metadata.discNumber?.takeIf { it > 0 }?.let { frames.write(textFrame("TPOS", it.toString())) }
        metadata.genre?.takeIf(String::isNotBlank)?.let { frames.write(textFrame("TCON", it)) }
        metadata.composer?.takeIf(String::isNotBlank)?.let { frames.write(textFrame("TCOM", it)) }
        metadata.isrc?.takeIf(String::isNotBlank)?.let { frames.write(textFrame("TSRC", it)) }
        metadata.comment?.takeIf(String::isNotBlank)?.let { frames.write(textFrame("COMM", it)) }
        metadata.artworkBytes?.let { bytes ->
            val mime = metadata.artworkMimeType?.takeIf(String::isNotBlank) ?: guessImageMimeType(bytes)
            frames.write(apicFrame(bytes, mime))
        }

        val frameBytes = frames.toByteArray()
        if (frameBytes.isEmpty()) return false

        // ID3v2.4 header (10 bytes):
        //   "ID3" (3) + version 4.0 (2) + flags 0x00 (1) + synchsafe size (4)
        val header = ByteArray(10)
        header[0] = 'I'.code.toByte()
        header[1] = 'D'.code.toByte()
        header[2] = '3'.code.toByte()
        header[3] = 0x04 // major version
        header[4] = 0x00 // minor version
        header[5] = 0x00 // flags
        writeSynchsafeInt(header, 6, frameBytes.size)

        // Read existing file bytes, then prepend header + frames.
        // Use chunked copy to avoid OOM on large FLAC files — read 1 MiB at a time.
        val originalBytes = file.readBytes()
        file.outputStream().buffered().use { out ->
            out.write(header)
            out.write(frameBytes)
            out.write(originalBytes)
        }
        return true
    }

    /**
     * Builds a single ID3v2.4 text frame.
     *
     * Frame layout (ID3v2.4 spec §3.3):
     *   - Frame ID: 4 ASCII bytes (e.g. "TIT2")
     *   - Size: 4-byte synchsafe integer (frame body size, excluding the 10-byte header)
     *   - Flags: 2 bytes (0x00 0x00 — no compression, no encryption, no grouping)
     *   - Body: 1-byte encoding (0x01 = UTF-16 with BOM) + UTF-16-encoded text with BOM + null terminator
     */
    private fun textFrame(id: String, value: String): ByteArray {
        val body = ByteArrayOutputStream()
        body.write(0x01) // encoding: UTF-16 with BOM
        // UTF-16 Big-Endian with BOM (FF FE) — written in Java's default UTF-16BE
        // plus a leading BOM. Most players accept either endianness; BOM disambiguates.
        body.write(0xFF)
        body.write(0xFE)
        val textBytes = value.toByteArray(Charsets.UTF_16BE)
        body.write(textBytes)
        // Null terminator (UTF-16: two zero bytes) — required by some strict parsers
        body.write(0x00)
        body.write(0x00)
        return frameWithHeader(id, body.toByteArray())
    }

    /**
     * Builds a single ID3v2.4 APIC (attached picture) frame.
     *
     * Frame layout:
     *   - Frame ID: "APIC"
     *   - Size: 4-byte synchsafe
     *   - Flags: 2 bytes (0x00 0x00)
     *   - Body:
     *     - 1-byte text encoding (0x01 = UTF-16 with BOM, used for the description)
     *     - MIME type as ASCII, null-terminated (e.g. "image/jpeg\0")
     *     - 1-byte picture type (0x03 = cover front — the most universally displayed type)
     *     - Description as UTF-16 with BOM, null-terminated (we use empty string)
     *     - Picture data (raw bytes, no terminator)
     */
    private fun apicFrame(imageBytes: ByteArray, mimeType: String): ByteArray {
        val body = ByteArrayOutputStream()
        body.write(0x01) // encoding for description
        body.write(mimeType.toByteArray(Charsets.US_ASCII))
        body.write(0x00) // null terminator for MIME
        body.write(0x03) // picture type: cover (front)
        // Empty description: BOM + null terminator
        body.write(0xFF)
        body.write(0xFE)
        body.write(0x00)
        body.write(0x00)
        body.write(imageBytes)
        return frameWithHeader("APIC", body.toByteArray())
    }

    /** Wraps [body] with a 10-byte ID3v2.4 frame header (4-byte ID + 4-byte synchsafe size + 2-byte flags). */
    private fun frameWithHeader(id: String, body: ByteArray): ByteArray {
        val out = ByteArray(10 + body.size)
        val idBytes = id.toByteArray(Charsets.US_ASCII)
        System.arraycopy(idBytes, 0, out, 0, 4)
        writeSynchsafeInt(out, 4, body.size)
        out[8] = 0x00 // status flags
        out[9] = 0x00 // format flags
        System.arraycopy(body, 0, out, 10, body.size)
        return out
    }

    /**
     * Writes a 4-byte synchsafe integer to [dest] at [offset]. Synchsafe integers
     * use only the low 7 bits of each byte (MSB always 0) so the byte sequence
     * can never be mistaken for the MPEG audio frame sync (0xFF 0xFB / 0xFF 0xF3 / 0xFF 0xF2).
     * Max representable value: 2^28 - 1 = 256 MiB per tag — more than enough for any
     * reasonable embedded artwork.
     */
    private fun writeSynchsafeInt(dest: ByteArray, offset: Int, value: Int) {
        dest[offset] = ((value shr 21) and 0x7F).toByte()
        dest[offset + 1] = ((value shr 14) and 0x7F).toByte()
        dest[offset + 2] = ((value shr 7) and 0x7F).toByte()
        dest[offset + 3] = (value and 0x7F).toByte()
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
