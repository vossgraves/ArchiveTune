/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Public Listen Together WebSocket client, speaking the Metrolist protobuf protocol.
 * Ported from Metrolist / SimpMusic (GPL-3.0) and adapted to ArchiveTune's event surface.
 * Metrolist Project (C) 2026 — Licensed under GPL-3.0 | See git history for contributors
 *
 * Wire format (TogetherPublicProto.kt): every frame is a protobuf Envelope over a binary
 * WebSocket message; the payload inside is protobuf, gzipped above 100 bytes. The FIRST
 * frame on any connection must be client_capabilities — the server answers
 * `unsupported_client` otherwise, no matter how correct everything else is.
 */

package moe.rukamori.archivetune.together

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.constants.TogetherPublicIsHostKey
import moe.rukamori.archivetune.constants.TogetherPublicRoomCodeKey
import moe.rukamori.archivetune.constants.TogetherPublicSessionTokenKey
import moe.rukamori.archivetune.utils.getAsync
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.random.Random

internal sealed class TogetherPublicEvent {
    data class RoomCreated(
        val roomCode: String,
        val userId: String,
    ) : TogetherPublicEvent()

    data class JoinApproved(
        val roomCode: String,
        val userId: String,
        val state: TogetherRoomState,
    ) : TogetherPublicEvent()

    data class JoinRejected(
        val reason: String,
    ) : TogetherPublicEvent()

    data class JoinRequested(
        val userId: String,
        val username: String,
    ) : TogetherPublicEvent()

    data class UserJoined(
        val userId: String,
        val username: String,
    ) : TogetherPublicEvent()

    data class UserLeft(
        val userId: String,
        val username: String,
    ) : TogetherPublicEvent()

    data class UserReconnected(
        val userId: String,
        val username: String,
    ) : TogetherPublicEvent()

    data class UserDisconnected(
        val userId: String,
        val username: String,
    ) : TogetherPublicEvent()

    data class SyncState(
        val state: TogetherPublicSyncStatePayload,
    ) : TogetherPublicEvent()

    data object SyncRequested : TogetherPublicEvent()

    data class SyncPlayback(
        val action: TogetherPublicPlaybackActionPayload,
    ) : TogetherPublicEvent()

    data class HostChanged(
        val roomCode: String,
        val newHostId: String,
        val newHostName: String,
    ) : TogetherPublicEvent()

    data class Kicked(
        val reason: String,
    ) : TogetherPublicEvent()

    data class Reconnected(
        val roomCode: String,
        val userId: String,
        val state: TogetherRoomState,
        val isHost: Boolean,
    ) : TogetherPublicEvent()

    /**
     * A guest asked to add a track. On Metrolist servers guests cannot enqueue directly
     * (`not_host`); they suggest and the host approves. MusicService auto-approves so the
     * guest experience is unchanged.
     */
    data class SuggestionReceived(
        val suggestionId: String,
        val fromUserId: String,
        val fromUsername: String,
        val trackInfo: TogetherPublicTrackInfo?,
    ) : TogetherPublicEvent()

    data class Error(
        val message: String,
        val recoverable: Boolean,
    ) : TogetherPublicEvent()

    data object Disconnected : TogetherPublicEvent()
}

/**
 * WebSocket client for the public Listen Together servers (Metrolist protocol, protobuf
 * over binary frames).
 *
 * Text frames are ignored: the server writes BinaryMessage exclusively.
 */
internal class TogetherPublicClient(
    private val externalScope: CoroutineScope,
    private val serverUrl: String,
    private val dataStore: DataStore<Preferences>,
    private val username: String,
) {
    var onEvent: ((TogetherPublicEvent) -> Unit)? = null

    private val codec = TogetherPublicProtoCodec()

    // Volatile: assigned from the connect coroutine but read by OkHttp listener threads;
    // onOpen can fire before connect()'s own assignment lands, so it re-assigns first.
    @Volatile
    private var webSocket: WebSocket? = null
    private var pingJob: Job? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempts = 0
    private var manuallyClosed = false
    private var sessionToken: String? = null
    private var roomCode: String? = null
    private var isHost = false
    private var pendingAction: TogetherPublicPendingAction? = null
    private var pingSequence = 0L

    private val httpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

    init {
        externalScope.launch {
            sessionToken = dataStore.getAsync(TogetherPublicSessionTokenKey)?.takeIf { it.isNotBlank() }
            roomCode = dataStore.getAsync(TogetherPublicRoomCodeKey)?.takeIf { it.isNotBlank() }
            isHost = dataStore.getAsync(TogetherPublicIsHostKey, false)
        }
    }

    fun connect() {
        if (manuallyClosed) return
        externalScope.launch {
            val webSocket =
                httpClient.newWebSocket(
                    Request.Builder().url(serverUrl).build(),
                    listener,
                )
            this@TogetherPublicClient.webSocket = webSocket
        }
    }

    fun createRoom() {
        pendingAction = TogetherPublicPendingAction.CreateRoom(username)
        connect()
    }

    fun joinRoom(code: String) {
        pendingAction = TogetherPublicPendingAction.JoinRoom(code, username)
        connect()
    }

    fun disconnect() {
        manuallyClosed = true
        reconnectJob?.cancel()
        pingJob?.cancel()
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        if (sessionToken != null) {
            externalScope.launch {
                dataStore.edit { prefs ->
                    prefs.remove(TogetherPublicSessionTokenKey)
                    prefs.remove(TogetherPublicRoomCodeKey)
                    prefs.remove(TogetherPublicIsHostKey)
                }
            }
        }
        sessionToken = null
        roomCode = null
        pendingAction = null
    }

    fun leaveRoom() {
        send(TogetherPublicMessageTypes.LEAVE_ROOM, null)
        externalScope.launch {
            dataStore.edit { prefs ->
                prefs.remove(TogetherPublicSessionTokenKey)
                prefs.remove(TogetherPublicRoomCodeKey)
                prefs.remove(TogetherPublicIsHostKey)
            }
        }
        sessionToken = null
        roomCode = null
    }

    fun approveJoin(userId: String) {
        send(
            TogetherPublicMessageTypes.APPROVE_JOIN,
            TogetherWireApproveJoinPayload(userId = userId),
        )
    }

    fun rejectJoin(
        userId: String,
        reason: String? = null,
    ) {
        send(
            TogetherPublicMessageTypes.REJECT_JOIN,
            TogetherWireRejectJoinPayload(userId = userId, reason = reason.orEmpty()),
        )
    }

    fun sendPlaybackAction(action: TogetherPublicPlaybackActionPayload) {
        send(TogetherPublicMessageTypes.PLAYBACK_ACTION, action.toWireAction())
    }

    fun sendBufferReady(trackId: String) {
        send(
            TogetherPublicMessageTypes.BUFFER_READY,
            TogetherWireBufferReadyPayload(trackId = trackId),
        )
    }

    fun requestSync() {
        send(TogetherPublicMessageTypes.REQUEST_SYNC, null)
    }

    fun kickUser(
        userId: String,
        reason: String? = null,
    ) {
        send(
            TogetherPublicMessageTypes.KICK_USER,
            TogetherWireKickUserPayload(userId = userId, reason = reason.orEmpty()),
        )
    }

    fun transferHost(newHostId: String) {
        send(
            TogetherPublicMessageTypes.TRANSFER_HOST,
            TogetherWireTransferHostPayload(newHostId = newHostId),
        )
    }

    /** How a guest adds a track on Metrolist servers — direct queue_add is host-only. */
    fun suggestTrack(trackInfo: TogetherPublicTrackInfo) {
        send(
            TogetherPublicMessageTypes.SUGGEST_TRACK,
            TogetherWireSuggestTrackPayload(trackInfo = trackInfo.toWireTrack()),
        )
    }

    fun approveSuggestion(suggestionId: String) {
        send(
            TogetherPublicMessageTypes.APPROVE_SUGGESTION,
            TogetherWireApproveSuggestionPayload(suggestionId = suggestionId),
        )
    }

    fun rejectSuggestion(
        suggestionId: String,
        reason: String? = null,
    ) {
        send(
            TogetherPublicMessageTypes.REJECT_SUGGESTION,
            TogetherWireRejectSuggestionPayload(suggestionId = suggestionId, reason = reason.orEmpty()),
        )
    }

    private fun send(
        type: String,
        payload: Any?,
    ) {
        val socket = webSocket ?: return
        val frame = runCatching { codec.encode(type, payload) }.getOrNull() ?: return
        socket.send(ByteString.of(*frame))
    }

    private val listener =
        object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                reconnectAttempts = 0
                this@TogetherPublicClient.webSocket = webSocket
                // The mandatory handshake. Must be the first frame on the socket, or the
                // server answers `unsupported_client` and never lets us into a room.
                send(
                    TogetherPublicMessageTypes.CLIENT_CAPABILITIES,
                    TogetherWireClientCapabilities(
                        supportsProtobuf = true,
                        supportsCompression = true,
                        clientVersion = CLIENT_VERSION,
                    ),
                )
                startPingJob()
                val token = sessionToken
                if (token != null) {
                    send(
                        TogetherPublicMessageTypes.RECONNECT,
                        TogetherWireReconnectPayload(sessionToken = token),
                    )
                    // Retained: if the token turns out to be expired, the session-expired
                    // path replays the user's original create/join request instead of
                    // dead-ending the session.
                } else {
                    executePendingAction()
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                // The Metrolist server writes BinaryMessage exclusively; ignore text.
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                handleBinaryMessage(bytes.toByteArray())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                handleConnectionLost()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                handleConnectionLost(t)
            }
        }

    private fun executePendingAction() {
        val action = pendingAction ?: return
        pendingAction = null
        when (action) {
            is TogetherPublicPendingAction.CreateRoom -> {
                send(
                    TogetherPublicMessageTypes.CREATE_ROOM,
                    TogetherWireCreateRoomPayload(username = action.username),
                )
            }

            is TogetherPublicPendingAction.JoinRoom -> {
                send(
                    TogetherPublicMessageTypes.JOIN_ROOM,
                    TogetherWireJoinRoomPayload(
                        roomCode = action.roomCode.uppercase(),
                        username = action.username,
                    ),
                )
            }
        }
    }

    private fun handleBinaryMessage(data: ByteArray) {
        val (type, payloadBytes) =
            runCatching { codec.decode(data) }.getOrElse { return }
        val payload =
            if (payloadBytes.isEmpty()) {
                null
            } else {
                codec.decodePayload(type, payloadBytes)
            }
        when (type) {
            TogetherPublicMessageTypes.SERVER_CAPABILITIES -> {
                // Handshake acknowledged. A server that advertised no compression would
                // need a codec swap; the public Metrolist server always supports it.
            }

            TogetherPublicMessageTypes.ROOM_CREATED -> {
                val payload = payload as? TogetherWireRoomCreatedPayload ?: return
                sessionToken = payload.sessionToken
                roomCode = payload.roomCode
                isHost = true
                persistSession(payload.sessionToken, payload.roomCode, isHost = true)
                onEvent?.invoke(TogetherPublicEvent.RoomCreated(payload.roomCode, payload.userId))
            }

            TogetherPublicMessageTypes.JOIN_APPROVED -> {
                val payload = payload as? TogetherWireJoinApprovedPayload ?: return
                val state = payload.state ?: return
                sessionToken = payload.sessionToken
                roomCode = payload.roomCode
                isHost = false
                persistSession(payload.sessionToken, payload.roomCode, isHost = false)
                onEvent?.invoke(
                    TogetherPublicEvent.JoinApproved(
                        roomCode = payload.roomCode,
                        userId = payload.userId,
                        state = state.toAppState().toTogetherRoomState(sessionId = payload.roomCode),
                    ),
                )
            }

            TogetherPublicMessageTypes.JOIN_REJECTED -> {
                val payload = payload as? TogetherWireJoinRejectedPayload
                onEvent?.invoke(TogetherPublicEvent.JoinRejected(payload?.reason?.ifBlank { null } ?: "rejected"))
            }

            TogetherPublicMessageTypes.JOIN_REQUEST -> {
                val payload = payload as? TogetherWireJoinRequestPayload ?: return
                onEvent?.invoke(TogetherPublicEvent.JoinRequested(payload.userId, payload.username))
            }

            TogetherPublicMessageTypes.USER_JOINED -> {
                val payload = payload as? TogetherWireUserJoinedPayload ?: return
                onEvent?.invoke(TogetherPublicEvent.UserJoined(payload.userId, payload.username))
            }

            TogetherPublicMessageTypes.USER_LEFT -> {
                val payload = payload as? TogetherWireUserLeftPayload ?: return
                onEvent?.invoke(TogetherPublicEvent.UserLeft(payload.userId, payload.username))
            }

            TogetherPublicMessageTypes.USER_RECONNECTED -> {
                val payload = payload as? TogetherWireUserReconnectedPayload ?: return
                onEvent?.invoke(TogetherPublicEvent.UserReconnected(payload.userId, payload.username))
            }

            TogetherPublicMessageTypes.USER_DISCONNECTED -> {
                val payload = payload as? TogetherWireUserDisconnectedPayload ?: return
                onEvent?.invoke(TogetherPublicEvent.UserDisconnected(payload.userId, payload.username))
            }

            TogetherPublicMessageTypes.SYNC_STATE -> {
                val payload = payload as? TogetherWireSyncStatePayload ?: return
                onEvent?.invoke(TogetherPublicEvent.SyncState(payload.toAppSyncState()))
            }

            TogetherPublicMessageTypes.REQUEST_SYNC -> {
                onEvent?.invoke(TogetherPublicEvent.SyncRequested)
            }

            TogetherPublicMessageTypes.SYNC_PLAYBACK -> {
                val payload = payload as? TogetherWirePlaybackAction ?: return
                onEvent?.invoke(TogetherPublicEvent.SyncPlayback(payload.toAppAction()))
            }

            TogetherPublicMessageTypes.HOST_CHANGED -> {
                val payload = payload as? TogetherWireHostChangedPayload ?: return
                onEvent?.invoke(
                    TogetherPublicEvent.HostChanged(
                        roomCode ?: "",
                        payload.newHostId,
                        payload.newHostName,
                    ),
                )
            }

            TogetherPublicMessageTypes.KICKED -> {
                val payload = payload as? TogetherWireKickedPayload
                clearSession()
                onEvent?.invoke(TogetherPublicEvent.Kicked(payload?.reason?.ifBlank { null } ?: "kicked"))
            }

            TogetherPublicMessageTypes.RECONNECTED -> {
                val payload = payload as? TogetherWireReconnectedPayload ?: return
                val state = payload.state ?: return
                pendingAction = null
                isHost = payload.isHost
                roomCode = payload.roomCode
                if (sessionToken != null) {
                    persistSession(sessionToken ?: "", payload.roomCode, payload.isHost)
                }
                onEvent?.invoke(
                    TogetherPublicEvent.Reconnected(
                        roomCode = payload.roomCode,
                        userId = payload.userId,
                        state = state.toAppState().toTogetherRoomState(sessionId = payload.roomCode),
                        isHost = payload.isHost,
                    ),
                )
            }

            TogetherPublicMessageTypes.ERROR -> {
                handleError(payload as? TogetherWireErrorPayload)
            }

            TogetherPublicMessageTypes.PONG -> {
                // Keep-alive acknowledged; nothing to do.
            }

            TogetherPublicMessageTypes.SUGGESTION_RECEIVED -> {
                val payload = payload as? TogetherWireSuggestionReceivedPayload ?: return
                onEvent?.invoke(
                    TogetherPublicEvent.SuggestionReceived(
                        suggestionId = payload.suggestionId,
                        fromUserId = payload.fromUserId,
                        fromUsername = payload.fromUsername,
                        trackInfo = payload.trackInfo?.toAppTrack(),
                    ),
                )
            }

            TogetherPublicMessageTypes.SUGGESTION_APPROVED,
            TogetherPublicMessageTypes.SUGGESTION_REJECTED,
            TogetherPublicMessageTypes.BUFFER_WAIT,
            TogetherPublicMessageTypes.BUFFER_COMPLETE,
            -> {
                // Addressed to a guest flow ArchiveTune does not drive itself; the
                // resulting sync_playback follow-ups arrive as normal events.
            }

            else -> {
                // Unknown message types are ignored for forward compatibility — a newer
                // Metrolist client in the room must not be able to kill this one.
            }
        }
    }

    private fun handleError(payload: TogetherWireErrorPayload?) {
        val code = payload?.code.orEmpty()
        val message = payload?.message?.ifBlank { null } ?: "server error"
        when (code) {
            // A guest attempting playback control on a server where only the host may.
            // Expected noise, not a connection problem: the tap simply has no effect.
            "not_host",

            // Transient races against the host's own broadcasts; the next sync corrects.
            "stale_track",

            // These arrive as errors but ARE the join answer on this protocol.
            "room_not_found", "invalid_room_code", "missing_room_code",
            "missing_username", "invalid_username", "room_full", "too_many_pending",
            "room_invalid", "already_in_room", "already_pending",
            -> {
                if (code in JOIN_FAILURE_CODES) {
                    onEvent?.invoke(TogetherPublicEvent.JoinRejected(message))
                }
            }

            "session_not_found", "session_expired", "missing_session_token" -> handleSessionExpired(message)

            "unsupported_client", "capabilities_too_late", "capabilities_already_set" -> {
                // A protocol-level mismatch no retry can fix on this connection.
                onEvent?.invoke(TogetherPublicEvent.Error(message, recoverable = false))
            }

            else -> {
                onEvent?.invoke(TogetherPublicEvent.Error(message, recoverable = true))
            }
        }
    }

    /**
     * The stored session token no longer names a live session (expired past the server's
     * 15-minute reconnect grace, or the room is gone). Forget it and either replay the
     * user's original create/join request or surface the loss.
     */
    private fun handleSessionExpired(message: String) {
        val hadPendingAction = pendingAction != null
        clearSession()
        if (hadPendingAction) {
            // Close so handleConnectionLost schedules a reconnect that replays the
            // pending create/join on a clean session.
            webSocket?.close(1000, "Session expired")
        } else {
            onEvent?.invoke(TogetherPublicEvent.Error(message, recoverable = true))
            webSocket?.close(1000, "Session expired")
        }
    }

    private fun persistSession(
        token: String,
        room: String,
        isHost: Boolean,
    ) {
        externalScope.launch {
            dataStore.edit { prefs ->
                prefs[TogetherPublicSessionTokenKey] = token
                prefs[TogetherPublicRoomCodeKey] = room
                prefs[TogetherPublicIsHostKey] = isHost
            }
        }
    }

    private fun clearSession() {
        sessionToken = null
        roomCode = null
        externalScope.launch {
            dataStore.edit { prefs ->
                prefs.remove(TogetherPublicSessionTokenKey)
                prefs.remove(TogetherPublicRoomCodeKey)
                prefs.remove(TogetherPublicIsHostKey)
            }
        }
    }

    private fun startPingJob() {
        pingJob?.cancel()
        pingJob =
            externalScope.launch {
                while (isActive) {
                    delay(PING_INTERVAL_MS)
                    pingSequence += 1L
                    send(
                        TogetherPublicMessageTypes.PING,
                        TogetherWirePingPayload(
                            clientTime = System.currentTimeMillis(),
                            sequence = pingSequence,
                        ),
                    )
                }
            }
    }

    private fun handleConnectionLost(t: Throwable? = null) {
        pingJob?.cancel()
        if (manuallyClosed) {
            onEvent?.invoke(TogetherPublicEvent.Disconnected)
            return
        }
        if (sessionToken == null && roomCode == null && pendingAction == null) {
            onEvent?.invoke(TogetherPublicEvent.Disconnected)
            return
        }
        onEvent?.invoke(
            TogetherPublicEvent.Error(
                message = t?.message ?: "Disconnected",
                recoverable = true,
            ),
        )
        scheduleReconnect()
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob =
            externalScope.launch {
                val attempt = reconnectAttempts++
                val baseDelayMs =
                    if (attempt == 0) {
                        500L
                    } else {
                        (500L shl attempt.coerceAtMost(8)).coerceAtMost(2 * 60 * 1000L)
                    }
                val jitterMs = abs(Random.nextLong(baseDelayMs / 4L))
                val delayMs = (baseDelayMs + jitterMs).coerceAtMost(2 * 60 * 1000L)
                delay(delayMs)
                if (!manuallyClosed && isActive) {
                    connect()
                }
            }
    }

    private sealed class TogetherPublicPendingAction {
        data class CreateRoom(
            val username: String,
        ) : TogetherPublicPendingAction()

        data class JoinRoom(
            val roomCode: String,
            val username: String,
        ) : TogetherPublicPendingAction()
    }

    private companion object {
        const val CLIENT_VERSION = "ArchiveTune"

        /**
         * The server drops a connection that stays silent past its 60s read deadline;
         * 15s of application-level pings (SimpMusic's interval) keeps the room alive even
         * when OkHttp's own 30s protocol pings are stripped by a middlebox.
         */
        const val PING_INTERVAL_MS = 15_000L

        /**
         * Error codes that answer a join attempt. Delivered as `error` frames on this
         * protocol but semantically the same as vivi's join_rejected.
         */
        val JOIN_FAILURE_CODES =
            setOf(
                "room_not_found",
                "invalid_room_code",
                "missing_room_code",
                "missing_username",
                "invalid_username",
                "room_full",
                "too_many_pending",
                "room_invalid",
            )
    }
}
