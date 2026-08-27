/*
 * ArchiveTune (2026)
 * © ArchiveTuneFork contributors — github.com/vossgraves/ArchiveTune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package app.atf.media.musixmatch.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

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

/**
 * Musixmatch's macro.subtitles endpoint occasionally returns an empty JSON array `[]`
 * (instead of an object) for a sub-call's `body` when that sub-call has no data — e.g.
 * `track.lyrics.get.message.body` becomes `[]` when no plain-lyrics result exists.
 *
 * Without sanitization, kotlinx.serialization fails the whole macro parse with
 * `Expected start of the object '{', but had '[' instead at path:
 * $.message.body.macro_calls.track.lyrics.get.message.body`, which then drops the
 * subtitle + richsync results that *were* returned in the same response.
 *
 * The fix: parse the raw JSON tree, walk every object's `body` field and coerce
 * non-object values (arrays, primitives) to null, then structural-decode the
 * sanitized tree into [MacroSubtitlesResponse]. Object bodies are passed through
 * untouched, and the rest of the tree is preserved as-is.
 */
internal fun decodeMacroSubtitlesResponse(
    json: Json,
    body: String,
): MacroSubtitlesResponse? {
    val rawElement =
        runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return null
    val sanitized = sanitizeBodyArrays(rawElement)
    return runCatching { json.decodeFromJsonElement<MacroSubtitlesResponse>(sanitized) }.getOrNull()
}

private fun sanitizeBodyArrays(element: JsonElement): JsonElement =
    when (element) {
        is JsonObject -> {
            val newMap = element.entries.associate { (k, v) ->
                if (k == "body" && v !is JsonObject && v !is JsonNull) {
                    k to JsonNull
                } else {
                    k to sanitizeBodyArrays(v)
                }
            }
            JsonObject(newMap)
        }
        is JsonArray -> JsonArray(element.map(::sanitizeBodyArrays))
        else -> element
    }
