/*
 * ArchiveTune (2026)
 * © ArchiveTuneFork contributors — github.com/vossgraves/ArchiveTune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package app.atf.media.musixmatch

import app.atf.media.musixmatch.models.RichSyncLine
import app.atf.media.musixmatch.models.RichSyncWord
import java.util.Locale

/**
 * Converts Musixmatch richsync lines into a TTML document that ArchiveTune's
 * existing TTMLParser understands. Each line becomes a `<p begin end>` and each
 * word inside becomes a `<span begin end>`.
 *
 * All times are stored as Double seconds, then formatted as `<n.nnn>s` using
 * Locale.US to avoid locale-dependent decimal separators. The regression from
 * Spicetify PR #2254 (negative word duration when next-word offset was kept in
 * seconds while current was in ms) is avoided by computing word end as
 * `lineStart + nextWord.offset` (both in seconds) and falling back to `lineEnd`
 * for the last word.
 *
 * Spacing tokens (words whose text is just whitespace) are preserved as
 * `<span>` so the round-trip through TTMLParser stays lossless — the parser
 * already trims empty words when building the word list.
 */
internal object MusixmatchTtml {
    private const val XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"

    fun richSyncToTtml(lines: List<RichSyncLine>): String {
        if (lines.isEmpty()) return ""

        val builder = StringBuilder()
        builder.append(XML_HEADER)
        builder.append('\n')
        builder.append("<tt xmlns=\"http://www.w3.org/ns/ttml\">\n")
        builder.append("  <body>\n")
        builder.append("    <div>\n")

        for (line in lines) {
            appendLine(builder, line)
        }

        builder.append("    </div>\n")
        builder.append("  </body>\n")
        builder.append("</tt>\n")
        return builder.toString()
    }

    private fun appendLine(builder: StringBuilder, line: RichSyncLine) {
        val lineStart = line.startTime
        val lineEnd = line.endTime
        if (lineEnd < lineStart) return

        val filtered = line.words.filter { it.text.isNotEmpty() }
        if (filtered.isEmpty()) {
            val safeText = escapeXml(line.text.orEmpty().ifBlank { "" })
            if (safeText.isBlank()) return
            builder.append("      <p begin=\"")
            builder.append(formatTime(lineStart))
            builder.append("\" end=\"")
            builder.append(formatTime(lineEnd))
            builder.append("\">")
            builder.append(safeText)
            builder.append("</p>\n")
            return
        }

        builder.append("      <p begin=\"")
        builder.append(formatTime(lineStart))
        builder.append("\" end=\"")
        builder.append(formatTime(lineEnd))
        builder.append("\">")
        for (i in filtered.indices) {
            val word = filtered[i]
            val wordStart = lineStart + word.offset
            val wordEnd: Double =
                if (i < filtered.lastIndex) {
                    lineStart + filtered[i + 1].offset
                } else {
                    lineEnd
                }
            val safeEnd = wordEnd.coerceAtLeast(wordStart)
            builder.append("<span begin=\"")
            builder.append(formatTime(wordStart))
            builder.append("\" end=\"")
            builder.append(formatTime(safeEnd))
            builder.append("\">")
            // Musixmatch word `c` may already include a trailing space (e.g. "I ", "love ").
            // We preserve it verbatim because the downstream TTMLParser trims each span's
            // text and only re-inserts a space when it sees a whitespace TEXT_NODE between
            // adjacent spans. Emitting that separator here keeps words from rendering
            // glued together ("Ilove" → "I love").
            builder.append(escapeXml(word.text))
            builder.append("</span>")
            if (i < filtered.lastIndex) {
                builder.append(' ')
            }
        }
        builder.append("</p>\n")
    }

    private fun formatTime(seconds: Double): String =
        String.format(Locale.US, "%.3fs", seconds.coerceAtLeast(0.0))

    private fun escapeXml(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
}
