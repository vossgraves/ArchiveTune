/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.lastfm.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Top-level response returned by Last.fm's `user.getInfo` method.
 *
 * The actual profile is nested under `user`. Keeping that envelope is
 * important: decoding the response directly into [UserInfo] silently drops
 * the `user` property (because the client ignores unknown keys), leaving all
 * of the optional profile values null and displaying a zero scrobble count.
 */
@Serializable
data class UserInfoResponse(
    val user: UserInfo,
)

/**
 * Subset of the profile nested in a `user.getInfo` response — only the fields
 * surfaced on the in-app dashboard.
 *
 * See https://www.last.fm/api/show/user.getInfo
 */
@Serializable
data class UserInfo(
    // Last.fm occasionally omits `name` from the user.getInfo response
    // (rate-limited / partial responses). Make it optional so the entire
    // response doesn't fail to deserialize — callers already handle null
    // via the `?: "—"` fallback on the dashboard.
    val name: String? = null,
    val realname: String? = null,
    val url: String? = null,
    val image: List<UserImage>? = null,
    val country: String? = null,
    val age: Int? = null,
    val gender: String? = null,
    val subscriber: Int? = null,
    @SerialName("playcount") private val _playcount: String? = null,
    val playlists: Int? = null,
    val registered: UserRegistered? = null,
) {
    val playcount: Long? get() = _playcount?.toLongOrNull()
}

@Serializable
data class UserImage(
    @SerialName("#text") val text: String,
    val size: String? = null,
)

@Serializable
data class UserRegistered(
    val unixtime: String? = null,
    @SerialName("#text") val text: Int? = null,
)

/**
 * Wrapper for the `user.getRecentTracks` response.
 */
@Serializable
data class RecentTracksResponse(
    val recenttracks: RecentTracks,
)

@Serializable
data class RecentTracks(
    val track: List<RecentTrack> = emptyList(),
    @SerialName("@attr") val attr: RecentTracksAttr? = null,
)

@Serializable
data class RecentTracksAttr(
    val user: String? = null,
    val page: String? = null,
    val perPage: String? = null,
    val totalPages: String? = null,
    val total: String? = null,
)

@Serializable
data class RecentTrack(
    val artist: RecentTrackArtist? = null,
    val name: String? = null,
    val album: RecentTrackAlbum? = null,
    val url: String? = null,
    val date: RecentTrackDate? = null,
    val image: List<UserImage>? = null,
    @SerialName("@attr") val attr: RecentTrackAttr? = null,
) {
    val isNowPlaying: Boolean get() = attr?.nowplaying == "true"
}

@Serializable
data class RecentTrackAttr(
    val nowplaying: String? = null,
)

@Serializable
data class RecentTrackArtist(
    @SerialName("#text") val text: String? = null,
    val mbid: String? = null,
)

@Serializable
data class RecentTrackAlbum(
    @SerialName("#text") val text: String? = null,
    val mbid: String? = null,
)

@Serializable
data class RecentTrackDate(
    val uts: String? = null,
    @SerialName("#text") val text: String? = null,
)

/**
 * Wrapper for the `user.getTopTracks` response.
 */
@Serializable
data class TopTracksResponse(
    val toptracks: TopTracks,
)

@Serializable
data class TopTracks(
    val track: List<TopTrack> = emptyList(),
    @SerialName("@attr") val attr: TopTracksAttr? = null,
)

@Serializable
data class TopTracksAttr(
    val user: String? = null,
    val page: String? = null,
    val perPage: String? = null,
    val totalPages: String? = null,
    val total: String? = null,
)

@Serializable
data class TopTrack(
    val name: String? = null,
    @SerialName("playcount") private val _playcount: String? = null,
    val artist: RecentTrackArtist? = null,
    val url: String? = null,
    val image: List<UserImage>? = null,
    @SerialName("@attr") val attr: TopTrackAttr? = null,
) {
    val playcount: Int? get() = _playcount?.toIntOrNull()
    val rank: String? get() = attr?.rank
}

@Serializable
data class TopTrackAttr(
    val rank: String? = null,
)

// ── user.getTopArtists ──────────────────────────────────────────────────────

@Serializable
data class TopArtistsResponse(
    val topartists: TopArtists,
)

@Serializable
data class TopArtists(
    val artist: List<TopArtist> = emptyList(),
    @SerialName("@attr") val attr: TopArtistsAttr? = null,
)

@Serializable
data class TopArtistsAttr(
    val user: String? = null,
    val page: String? = null,
    val perPage: String? = null,
    val totalPages: String? = null,
    val total: String? = null,
)

@Serializable
data class TopArtist(
    val name: String? = null,
    @SerialName("playcount") private val _playcount: String? = null,
    val url: String? = null,
    val image: List<UserImage>? = null,
    @SerialName("@attr") val attr: TopTrackAttr? = null,
) {
    val playcount: Int? get() = _playcount?.toIntOrNull()
}

// ── user.getTopAlbums ──────────────────────────────────────────────────────

@Serializable
data class TopAlbumsResponse(
    val topalbums: TopAlbums,
)

@Serializable
data class TopAlbums(
    val album: List<TopAlbum> = emptyList(),
    @SerialName("@attr") val attr: TopAlbumsAttr? = null,
)

@Serializable
data class TopAlbumsAttr(
    val user: String? = null,
    val page: String? = null,
    val perPage: String? = null,
    val totalPages: String? = null,
    val total: String? = null,
)

@Serializable
data class TopAlbum(
    val name: String? = null,
    @SerialName("playcount") private val _playcount: String? = null,
    val artist: RecentTrackArtist? = null,
    val url: String? = null,
    val image: List<UserImage>? = null,
    @SerialName("@attr") val attr: TopTrackAttr? = null,
) {
    val playcount: Int? get() = _playcount?.toIntOrNull()
}

/**
 * Fallback raw JSON element — used when we want to surface a parse
 * failure to the UI without losing the whole dashboard.
 */
@Serializable
data class RawJson(
    val raw: JsonElement? = null,
)
