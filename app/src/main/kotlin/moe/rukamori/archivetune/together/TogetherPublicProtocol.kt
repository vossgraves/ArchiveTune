/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * App-facing session models for the public Listen Together feature. The wire layer
 * (protobuf, Metrolist-compatible) lives in TogetherPublicProto.kt; these classes are
 * what MusicService and the UI consume, translated by the client.
 */

package moe.rukamori.archivetune.together

import android.os.SystemClock
import moe.rukamori.archivetune.utils.md5

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
    const val SUGGEST_TRACK = "suggest_track"
    const val APPROVE_SUGGESTION = "approve_suggestion"
    const val REJECT_SUGGESTION = "reject_suggestion"

    /**
     * Capability negotiation. These type strings are NOT in listentogether.proto — the
     * schema defines the messages but never names the envelope type that carries them.
     * They come from the server (metroserver internal/server/protocol.go), and the
     * handshake is mandatory: a client that never sends this is answered with
     * `unsupported_client` and can join no room at all.
     */
    const val CLIENT_CAPABILITIES = "client_capabilities"

    // Server -> Client
    const val ROOM_CREATED = "room_created"
    const val JOIN_REQUEST = "join_request"
    const val JOIN_APPROVED = "join_approved"
    const val JOIN_REJECTED = "join_rejected"
    const val USER_JOINED = "user_joined"
    const val USER_LEFT = "user_left"
    const val SYNC_PLAYBACK = "sync_playback"
    const val BUFFER_WAIT = "buffer_wait"
    const val BUFFER_COMPLETE = "buffer_complete"
    const val ERROR = "error"
    const val PONG = "pong"
    const val HOST_CHANGED = "host_changed"
    const val KICKED = "kicked"
    const val SYNC_STATE = "sync_state"
    const val RECONNECTED = "reconnected"
    const val USER_RECONNECTED = "user_reconnected"
    const val USER_DISCONNECTED = "user_disconnected"
    const val SUGGESTION_RECEIVED = "suggestion_received"
    const val SUGGESTION_APPROVED = "suggestion_approved"
    const val SUGGESTION_REJECTED = "suggestion_rejected"

    /** The server's half of the handshake — see [CLIENT_CAPABILITIES]. */
    const val SERVER_CAPABILITIES = "server_capabilities"
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
    const val SET_VOLUME = "set_volume"
}

// ---------------------------------------------------------------------------
// App-facing session models (translated from the wire models by the client)
// ---------------------------------------------------------------------------

internal data class TogetherPublicTrackInfo(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val duration: Long, // milliseconds
    val thumbnail: String? = null,
    val suggestedBy: String? = null,
)

internal data class TogetherPublicUserInfo(
    val userId: String,
    val username: String,
    val isHost: Boolean,
    val isConnected: Boolean = true,
)

internal data class TogetherPublicRoomState(
    val roomCode: String,
    val hostId: String,
    val users: List<TogetherPublicUserInfo>,
    val currentTrack: TogetherPublicTrackInfo? = null,
    val isPlaying: Boolean,
    val position: Long, // milliseconds
    val lastUpdate: Long, // unix timestamp ms
    val volume: Float = 1f,
    val queue: List<TogetherPublicTrackInfo> = emptyList(),
)

internal data class TogetherPublicCreateRoomPayload(
    val username: String,
)

internal data class TogetherPublicJoinRoomPayload(
    val roomCode: String,
    val username: String,
)

internal data class TogetherPublicApproveJoinPayload(
    val userId: String,
)

internal data class TogetherPublicRejectJoinPayload(
    val userId: String,
    val reason: String? = null,
)

internal data class TogetherPublicPlaybackActionPayload(
    val action: String,
    val trackId: String? = null,
    val position: Long? = null, // milliseconds
    val trackInfo: TogetherPublicTrackInfo? = null,
    val insertNext: Boolean? = null,
    val queue: List<TogetherPublicTrackInfo>? = null,
    val volume: Float? = null,
    val serverTime: Long? = null,
)

internal data class TogetherPublicBufferReadyPayload(
    val trackId: String,
)

internal data class TogetherPublicKickUserPayload(
    val userId: String,
    val reason: String? = null,
)

internal data class TogetherPublicTransferHostPayload(
    val newHostId: String,
)

internal data class TogetherPublicRoomCreatedPayload(
    val roomCode: String,
    val userId: String,
    val sessionToken: String,
)

internal data class TogetherPublicJoinRequestPayload(
    val userId: String,
    val username: String,
)

internal data class TogetherPublicJoinApprovedPayload(
    val roomCode: String,
    val userId: String,
    val sessionToken: String,
    val state: TogetherPublicRoomState,
)

internal data class TogetherPublicJoinRejectedPayload(
    val reason: String,
)

internal data class TogetherPublicUserJoinedPayload(
    val userId: String,
    val username: String,
)

internal data class TogetherPublicUserLeftPayload(
    val userId: String,
    val username: String,
)

internal data class TogetherPublicErrorPayload(
    val code: String,
    val message: String,
)

internal data class TogetherPublicHostChangedPayload(
    val newHostId: String,
    val newHostName: String,
)

internal data class TogetherPublicKickedPayload(
    val reason: String,
)

internal data class TogetherPublicSyncStatePayload(
    val currentTrack: TogetherPublicTrackInfo?,
    val isPlaying: Boolean,
    val position: Long,
    val lastUpdate: Long,
    val queue: List<TogetherPublicTrackInfo>? = null,
    val volume: Float? = null,
)

internal data class TogetherPublicReconnectPayload(
    val sessionToken: String,
)

internal data class TogetherPublicReconnectedPayload(
    val roomCode: String,
    val userId: String,
    val state: TogetherPublicRoomState,
    val isHost: Boolean,
)

internal data class TogetherPublicUserReconnectedPayload(
    val userId: String,
    val username: String,
)

internal data class TogetherPublicUserDisconnectedPayload(
    val userId: String,
    val username: String,
)

// ---------------------------------------------------------------------------
// Translation between the public session model and the fork's TogetherRoomState
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

/**
 * Rebuilds the queue the app expects.
 *
 * The Metrolist server keeps the room's queue as UPCOMING tracks only (the current track
 * is stripped by sanitizeUpcomingQueue before a RoomState is ever sent), so a queue that
 * does not contain the current track is not a queue that lost its head — it is the
 * server's normal shape, and the head must be prepended back before the app's index
 * arithmetic can point at the right row.
 */
private fun reconstructQueue(
    queue: List<TogetherPublicTrackInfo>,
    currentTrack: TogetherPublicTrackInfo?,
): List<TogetherPublicTrackInfo> =
    if (currentTrack != null && queue.none { it.id == currentTrack.id }) {
        listOf(currentTrack) + queue
    } else {
        queue
    }

internal fun TogetherPublicRoomState.toTogetherRoomState(sessionId: String): TogetherRoomState {
    val queue = reconstructQueue(queue, currentTrack).map { it.toTogetherTrack() }
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
                // (On Metrolist servers the server itself enforces that only the host
                // sends playback actions; guests add tracks via suggest_track instead.)
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
    val queue = reconstructQueue(queue.orEmpty(), currentTrack).map { it.toTogetherTrack() }
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
