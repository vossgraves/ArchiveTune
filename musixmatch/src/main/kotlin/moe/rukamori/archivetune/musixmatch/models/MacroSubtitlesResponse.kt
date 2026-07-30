/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.musixmatch.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MacroSubtitlesResponse(
    val message: MacroMessage = MacroMessage(),
)

@Serializable
data class MacroMessage(
    val header: MusixmatchHeader = MusixmatchHeader(),
    val body: MacroBody = MacroBody(),
)

@Serializable
data class MacroBody(
    @SerialName("macro_calls")
    val macroCalls: MacroCalls = MacroCalls(),
)

@Serializable
data class MacroCalls(
    @SerialName("matcher.track.get")
    val matcherTrackGet: WrappedCall<MatcherTrackBody>? = null,

    @SerialName("track.lyrics.get")
    val trackLyricsGet: WrappedCall<TrackLyricsBody>? = null,

    @SerialName("track.subtitles.get")
    val trackSubtitlesGet: WrappedCall<TrackSubtitlesBody>? = null,
)

@Serializable
data class WrappedCall<T>(
    val message: WrappedMessage<T> = WrappedMessage(),
)

@Serializable
data class WrappedMessage<T>(
    val header: MusixmatchHeader = MusixmatchHeader(),
    val body: T? = null,
)

@Serializable
data class MatcherTrackBody(
    val track: MatcherTrack? = null,
)

@Serializable
data class MatcherTrack(
    @SerialName("track_id")
    val trackId: Long? = null,
    @SerialName("commontrack_id")
    val commontrackId: Long? = null,
    @SerialName("track_length")
    val trackLength: Int? = null,
    @SerialName("has_richsync")
    val hasRichSync: Int? = null,
    val instrumental: Int? = null,
    @SerialName("has_subtitles")
    val hasSubtitles: Int? = null,
    @SerialName("has_lyrics")
    val hasLyrics: Int? = null,
    @SerialName("track_name")
    val trackName: String? = null,
    @SerialName("artist_name")
    val artistName: String? = null,
)

@Serializable
data class TrackLyricsBody(
    val lyrics: TrackLyrics? = null,
)

@Serializable
data class TrackLyrics(
    @SerialName("lyrics_body")
    val lyricsBody: String? = null,
    @SerialName("instrumental")
    val instrumental: Int? = null,
)

@Serializable
data class TrackSubtitlesBody(
    val subtitleList: SubtitleList? = null,
)

@Serializable
data class SubtitleList(
    val subtitleList: List<SubtitleWrapper> = emptyList(),
)

@Serializable
data class SubtitleWrapper(
    val subtitle: Subtitle? = null,
)

@Serializable
data class Subtitle(
    @SerialName("subtitle_body")
    val subtitleBody: String? = null,
)
