/*
 * ArchiveTune (2026)
 * © ArchiveTuneFork contributors — github.com/vossgraves/ArchiveTune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package app.atf.media.canvas.models

import java.text.Normalizer
import java.util.Locale

fun CanvasArtwork.matchesSongIdentity(
    song: String,
    artist: String,
): Boolean {
    val requestedSong = song.toCanvasSongIdentity()
    val requestedArtist = artist.toCanvasPrimaryArtistIdentity()
    val resolvedSong = name?.toCanvasSongIdentity().orEmpty()
    val resolvedArtist = this.artist?.toCanvasPrimaryArtistIdentity().orEmpty()

    return requestedSong.isNotEmpty() &&
        requestedArtist.isNotEmpty() &&
        resolvedSong == requestedSong &&
        resolvedArtist == requestedArtist
}

/**
 * Fuzzy variant of [matchesSongIdentity] for sources with unreliable tags (e.g. files streamed
 * from Telegram channels, whose "artist" may be a channel name and whose title carries extra
 * qualifiers). Accepts containment either way on the normalized song title, and requires the
 * artists to be equal, contain each other, or the requested artist to be unusable (blank).
 */
fun CanvasArtwork.looselyMatchesSongIdentity(
    song: String,
    artist: String,
): Boolean {
    val requestedSong = song.toCanvasSongIdentity()
    val resolvedSong = name?.toCanvasSongIdentity().orEmpty()
    if (requestedSong.isEmpty() || resolvedSong.isEmpty()) return false
    val songMatches =
        resolvedSong == requestedSong ||
            resolvedSong.contains(requestedSong) ||
            requestedSong.contains(resolvedSong)
    if (!songMatches) return false

    val requestedArtist = artist.toCanvasPrimaryArtistIdentity()
    if (requestedArtist.isEmpty()) return true
    val resolvedArtist = this.artist?.toCanvasPrimaryArtistIdentity().orEmpty()
    return resolvedArtist == requestedArtist ||
        resolvedArtist.contains(requestedArtist) ||
        requestedArtist.contains(resolvedArtist)
}

/** Whether this artwork's album matches the requested album title (normalized containment). */
fun CanvasArtwork.matchesAlbumIdentity(album: String): Boolean {
    val requested = album.toCanvasSongIdentity()
    val resolved = albumName?.toCanvasSongIdentity().orEmpty()
    if (requested.isEmpty() || resolved.isEmpty()) return false
    return resolved == requested || resolved.contains(requested) || requested.contains(resolved)
}

private fun String.toCanvasSongIdentity(): String =
    replace(CanvasFeaturingQualifierRegex, "")
        .replace(CanvasPresentationQualifierRegex, "")
        .toCanvasIdentity()

private fun String.toCanvasPrimaryArtistIdentity(): String =
    split(CanvasArtistSeparatorRegex, limit = 2)
        .firstOrNull()
        .orEmpty()
        .toCanvasIdentity()

private fun String.toCanvasIdentity(): String =
    Normalizer
        .normalize(this, Normalizer.Form.NFKD)
        .replace(CanvasCombiningMarkRegex, "")
        .lowercase(Locale.ROOT)
        .replace(CanvasIdentitySeparatorRegex, " ")
        .trim()

private val CanvasFeaturingQualifierRegex =
    Regex(
        """\s*[\[(](?:feat\.?|ft\.?|featuring|with)\s+[^\])]*[\])]""",
        RegexOption.IGNORE_CASE,
    )

private val CanvasPresentationQualifierRegex =
    Regex(
        """\s*(?:[-–—:]\s*)?[\[(]?(?:official\s+)?(?:music\s+)?(?:lyric\s+video|video|audio|lyrics?|visualizer|visualiser)[\])]?\s*$""",
        RegexOption.IGNORE_CASE,
    )

private val CanvasArtistSeparatorRegex =
    Regex(
        """(?:\s*,\s*|\s*&\s*|\s+x\s+|\s+(?:feat\.?|ft\.?|featuring|with)\s+)""",
        RegexOption.IGNORE_CASE,
    )

private val CanvasCombiningMarkRegex = Regex("\\p{M}+")
private val CanvasIdentitySeparatorRegex = Regex("[^\\p{L}\\p{N}]+")
