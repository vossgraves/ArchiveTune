/*
 * ArchiveTune (2026)
 * © ArchiveTuneFork contributors — github.com/vossgraves/ArchiveTune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Public Listen Together protocol — wire models ported from vivimusic
 * (C) 2026 Vividh / vivimusic Project, GPL-3.0. JSON subset only.
 * https://github.com/vividhq/vivimusic (listen-together/Protocol.kt)
 */

package app.atf.media.together

import android.os.SystemClock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import app.atf.media.utils.md5

/** Message envelope for the public Listen Together protocol. */
@Serializable
internal data class TogetherPublicMessage(
    val type: String,
    val payload: JsonElement? = null,
)

internal object TogetherPublicMessageTypes {
    // Client -> Server
    const val CREATE_ROOM = "create_room"
    const val JOIN_ROOM = "join_room"
    const val LEAVE_ROOM = "leave_room"
    const val APPROVE_JOIN = "approve_join"
    const val REJECT_JOIN = "reject_join"
    const val PLAYBACK_ACTION = "playback_action"
    const val BUFFER_READY = "buffer_ready"
    const val KICK_USER = "kick_user"
    const val TRANSFER_HOST = "transfer_host"
    const val PING = "ping"
    const val REQUEST_SYNC = "request_sync"
    const val RECONNECT = "reconnect"

    // Server -> Client
    const val ROOM_CREATED = "room_created"
    const val JOIN_REQUEST = "join_request"
    const val JOIN_APPROVED = "join_approved"
    const val JOIN_REJECTED = "join_rejected"
    const val USER_JOINED = "user_joined"
    const val USER_LEFT = "user_left"
    const val SYNC_PLAYBACK = "sync_playback"
    const val ERROR = "error"
    const val PONG = "pong"
    const val HOST_CHANGED = "host_changed"
    const val KICKED = "kicked"
    const val SYNC_STATE = "sync_state"
    const val RECONNECTED = "reconnected"
    const val USER_RECONNECTED = "user_reconnected"
    const val USER_DISCONNECTED = "user_disconnected"
}

internal object TogetherPublicPlaybackActions {
    const val PLAY = "play"
    const val PAUSE = "pause"
    const val SEEK = "seek"
    const val SKIP_NEXT = "skip_next"
    const val SKIP_PREV = "skip_prev"
    const val CHANGE_TRACK = "change_track"
    const val QUEUE_ADD = "queue_add"
    const val QUEUE_REMOVE = "queue_remove"
    const val QUEUE_CLEAR = "queue_clear"
    const val SYNC_QUEUE = "sync_queue"
}

/** JSON codec for the public protocol. Plain envelope, never the fork class discriminator. */
internal val TogetherPublicJson =
    Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

// ---------------------------------------------------------------------------
// Wire models (port of vivi Protocol.kt)
// ---------------------------------------------------------------------------

@Serializable
internal data class TogetherPublicTrackInfo(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val duration: Long, // milliseconds
    val thumbnail: String? = null,
    @SerialName("suggested_by") val suggestedBy: String? = null,
)

@Serializable
internal data class TogetherPublicUserInfo(
    @SerialName("user_id") val userId: String,
    val username: String,
    @SerialName("is_host") val isHost: Boolean,
    @SerialName("is_connected") val isConnected: Boolean = true,
)

@Serializable
internal data class TogetherPublicRoomState(
    @SerialName("room_code") val roomCode: String,
    @SerialName("host_id") val hostId: String,
    val users: List<TogetherPublicUserInfo>,
    @SerialName("current_track") val currentTrack: TogetherPublicTrackInfo? = null,
    @SerialName("is_playing") val isPlaying: Boolean,
    val position: Long, // milliseconds
    @SerialName("last_update") val lastUpdate: Long, // unix timestamp ms
    val volume: Float = 1f,
    val queue: List<TogetherPublicTrackInfo> = emptyList(),
)

@Serializable
internal data class TogetherPublicCreateRoomPayload(
    val username: String,
)

@Serializable
internal data class TogetherPublicJoinRoomPayload(
    @SerialName("room_code") val roomCode: String,
    val username: String,
)

@Serializable
internal data class TogetherPublicApproveJoinPayload(
    @SerialName("user_id") val userId: String,
)

@Serializable
internal data class TogetherPublicRejectJoinPayload(
    @SerialName("user_id") val userId: String,
    val reason: String? = null,
)

@Serializable
internal data class TogetherPublicPlaybackActionPayload(
    val action: String,
    @SerialName("track_id") val trackId: String? = null,
    val position: Long? = null, // milliseconds
    @SerialName("track_info") val trackInfo: TogetherPublicTrackInfo? = null,
    @SerialName("insert_next") val insertNext: Boolean? = null,
    val queue: List<TogetherPublicTrackInfo>? = null,
    val volume: Float? = null,
    @SerialName("server_time") val serverTime: Long? = null,
)

@Serializable
internal data class TogetherPublicBufferReadyPayload(
    @SerialName("track_id") val trackId: String,
)

@Serializable
internal data class TogetherPublicKickUserPayload(
    @SerialName("user_id") val userId: String,
    val reason: String? = null,
)

@Serializable
internal data class TogetherPublicTransferHostPayload(
    @SerialName("new_host_id") val newHostId: String,
)

@Serializable
internal data class TogetherPublicRoomCreatedPayload(
    @SerialName("room_code") val roomCode: String,
    @SerialName("user_id") val userId: String,
    @SerialName("session_token") val sessionToken: String,
)

@Serializable
internal data class TogetherPublicJoinRequestPayload(
    @SerialName("user_id") val userId: String,
    val username: String,
)

@Serializable
internal data class TogetherPublicJoinApprovedPayload(
    @SerialName("room_code") val roomCode: String,
    @SerialName("user_id") val userId: String,
    @SerialName("session_token") val sessionToken: String,
    val state: TogetherPublicRoomState,
)

@Serializable
internal data class TogetherPublicJoinRejectedPayload(
    val reason: String,
)

@Serializable
internal data class TogetherPublicUserJoinedPayload(
    @SerialName("user_id") val userId: String,
    val username: String,
)

@Serializable
internal data class TogetherPublicUserLeftPayload(
    @SerialName("user_id") val userId: String,
    val username: String,
)

@Serializable
internal data class TogetherPublicErrorPayload(
    val code: String,
    val message: String,
)

@Serializable
internal data class TogetherPublicHostChangedPayload(
    @SerialName("new_host_id") val newHostId: String,
    @SerialName("new_host_name") val newHostName: String,
)

@Serializable
internal data class TogetherPublicKickedPayload(
    val reason: String,
)

@Serializable
internal data class TogetherPublicSyncStatePayload(
    @SerialName("current_track") val currentTrack: TogetherPublicTrackInfo?,
    @SerialName("is_playing") val isPlaying: Boolean,
    val position: Long,
    @SerialName("last_update") val lastUpdate: Long,
    val queue: List<TogetherPublicTrackInfo>? = null,
    val volume: Float? = null,
)

@Serializable
internal data class TogetherPublicReconnectPayload(
    @SerialName("session_token") val sessionToken: String,
)

@Serializable
internal data class TogetherPublicReconnectedPayload(
    @SerialName("room_code") val roomCode: String,
    @SerialName("user_id") val userId: String,
    val state: TogetherPublicRoomState,
    @SerialName("is_host") val isHost: Boolean,
)

@Serializable
internal data class TogetherPublicUserReconnectedPayload(
    @SerialName("user_id") val userId: String,
    val username: String,
)

@Serializable
internal data class TogetherPublicUserDisconnectedPayload(
    @SerialName("user_id") val userId: String,
    val username: String,
)

// ---------------------------------------------------------------------------
// Translation between the public wire model and the fork's TogetherRoomState
// ---------------------------------------------------------------------------

internal fun TogetherPublicTrackInfo.toTogetherTrack(): TogetherTrack =
    TogetherTrack(
        id = id,
        title = title,
        artists = artist.takeIf { it.isNotBlank() }?.let(::listOf).orEmpty(),
        durationSec = (duration / 1000L).toInt(),
        thumbnailUrl = thumbnail,
    )

internal fun TogetherTrack.toPublicTrackInfo(): TogetherPublicTrackInfo =
    TogetherPublicTrackInfo(
        id = id,
        title = title,
        artist = artists.firstOrNull().orEmpty(),
        duration = durationSec.coerceAtLeast(0) * 1000L,
        thumbnail = thumbnailUrl,
    )

internal fun TogetherPublicRoomState.toTogetherRoomState(sessionId: String): TogetherRoomState {
    val queue = queue.map { it.toTogetherTrack() }
    val currentTrackId = currentTrack?.id
    val currentIndex =
        currentTrackId
            ?.let { id -> queue.indexOfFirst { it.id == id } }
            ?.coerceAtLeast(0)
            ?: 0
    return TogetherRoomState(
        sessionId = sessionId,
        hostId = hostId,
        participants =
            users.map { user ->
                TogetherParticipant(
                    id = user.userId,
                    name = user.username,
                    isHost = user.isHost,
                    isPending = false,
                    isConnected = user.isConnected,
                )
            },
        settings =
            TogetherRoomSettings(
                // Public servers have no server-side settings; guests may act freely.
                allowGuestsToAddTracks = true,
                allowGuestsToControlPlayback = true,
                requireHostApprovalToJoin = false,
            ),
        queue = queue,
        queueHash = md5(queue.joinToString(separator = "|") { it.id }),
        currentIndex = currentIndex,
        isPlaying = isPlaying,
        positionMs = position.coerceAtLeast(0L),
        repeatMode = 0,
        shuffleEnabled = false,
        // No elapsed-realtime clock on the wire; receipt time keeps staleness checks sane.
        sentAtElapsedRealtimeMs = SystemClock.elapsedRealtime(),
    )
}

internal fun TogetherPublicSyncStatePayload.toTogetherRoomState(
    sessionId: String,
    hostId: String,
    participants: List<TogetherParticipant>,
): TogetherRoomState {
    val queue = queue.orEmpty().map { it.toTogetherTrack() }
    val currentTrackId = currentTrack?.id
    val currentIndex =
        currentTrackId
            ?.let { id -> queue.indexOfFirst { it.id == id } }
            ?.coerceAtLeast(0)
            ?: 0
    return TogetherRoomState(
        sessionId = sessionId,
        hostId = hostId,
        participants = participants,
        settings =
            TogetherRoomSettings(
                allowGuestsToAddTracks = true,
                allowGuestsToControlPlayback = true,
                requireHostApprovalToJoin = false,
            ),
        queue = queue,
        queueHash = md5(queue.joinToString(separator = "|") { it.id }),
        currentIndex = currentIndex,
        isPlaying = isPlaying,
        positionMs = position.coerceAtLeast(0L),
        repeatMode = 0,
        shuffleEnabled = false,
        sentAtElapsedRealtimeMs = SystemClock.elapsedRealtime(),
    )
}
