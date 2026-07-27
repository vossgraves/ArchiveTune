/*
 * Copyright (C) 2024 Rukamori
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 */
package moe.rukamori.archivetune.download

/**
 * Identifies an audio container from its magic bytes.
 *
 * Exports used to hardcode `.mp3` / `audio/mpeg` for every file. That was wrong for most downloads —
 * YouTube audio is typically Opus or AAC in WebM/MP4, and lossless sources deliver FLAC — so exported
 * files carried an extension that contradicted their contents. Some players trust the extension and
 * fail outright; others silently mis-handle the file. Sniffing the header means the name always
 * matches the bytes.
 */
enum class AudioContainer(
    val extension: String,
    val mimeType: String,
) {
    FLAC("flac", "audio/flac"),
    MP3("mp3", "audio/mpeg"),
    MP4("m4a", "audio/mp4"),
    OGG("ogg", "audio/ogg"),
    WEBM("webm", "audio/webm"),
    WAV("wav", "audio/wav"),
    ;

    companion object {
        /**
         * Bytes to read for a probe. Comfortably covers every magic-byte check below.
         *
         * Deliberately not large enough to skip a full ID3v2 tag, which is usually several KB once
         * cover art is embedded. That only costs us the rare ID3-prefixed-MP4 case, which falls back
         * to MP3 — the correct answer for the overwhelmingly more common ID3-prefixed MP3.
         */
        const val PROBE_BYTES = 64

        /**
         * Detects the container from [header], or null when it matches nothing known.
         *
         * Callers should fall back to whatever the metadata claimed rather than guessing.
         */
        fun detect(header: ByteArray): AudioContainer? {
            if (header.size < 12) return null

            fun matches(
                offset: Int,
                vararg ascii: Char,
            ): Boolean {
                if (offset + ascii.size > header.size) return false
                return ascii.withIndex().all { (i, c) -> header[offset + i] == c.code.toByte() }
            }

            return when {
                matches(0, 'f', 'L', 'a', 'C') -> FLAC
                matches(0, 'O', 'g', 'g', 'S') -> OGG
                // RIFF....WAVE
                matches(0, 'R', 'I', 'F', 'F') && matches(8, 'W', 'A', 'V', 'E') -> WAV
                // Matroska/WebM EBML header: 1A 45 DF A3
                header[0] == 0x1A.toByte() &&
                    header[1] == 0x45.toByte() &&
                    header[2] == 0xDF.toByte() &&
                    header[3] == 0xA3.toByte() -> WEBM
                // MP4/M4A: the ftyp box starts at byte 4, after the size field.
                matches(4, 'f', 't', 'y', 'p') -> MP4
                // A leading ID3 tag can precede either MP3 frames or, rarely, an MP4 stream.
                matches(0, 'I', 'D', '3') -> resolveAfterId3(header) ?: MP3
                // Bare MPEG audio frame sync: 11 bits set.
                header[0] == 0xFF.toByte() && (header[1].toInt() and 0xE0) == 0xE0 -> MP3
                else -> null
            }
        }

        /**
         * Skips an ID3v2 tag to inspect what actually follows.
         *
         * The size is a 28-bit synchsafe integer: 7 bits per byte, high bit always clear, so the
         * length can never contain a false frame-sync pattern.
         */
        private fun resolveAfterId3(header: ByteArray): AudioContainer? {
            if (header.size < 10) return null
            val size =
                ((header[6].toInt() and 0x7F) shl 21) or
                    ((header[7].toInt() and 0x7F) shl 14) or
                    ((header[8].toInt() and 0x7F) shl 7) or
                    (header[9].toInt() and 0x7F)
            val start = 10 + size
            if (start + 8 > header.size) return null
            val isFtyp =
                header[start + 4] == 'f'.code.toByte() &&
                    header[start + 5] == 't'.code.toByte() &&
                    header[start + 6] == 'y'.code.toByte() &&
                    header[start + 7] == 'p'.code.toByte()
            return if (isFtyp) MP4 else null
        }

        /** Maps a MIME type to a sensible extension when the bytes cannot be probed. */
        fun extensionForMime(mimeType: String?): String =
            when {
                mimeType == null -> "m4a"
                mimeType.contains("flac", true) -> "flac"
                mimeType.contains("mpeg", true) || mimeType.contains("mp3", true) -> "mp3"
                mimeType.contains("ogg", true) || mimeType.contains("opus", true) -> "ogg"
                mimeType.contains("webm", true) -> "webm"
                mimeType.contains("wav", true) -> "wav"
                else -> "m4a"
            }
    }
}
