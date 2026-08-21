/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Public Listen Together WebSocket client — ported from vivimusic
 * (C) 2026 Vividh / vivimusic Project, GPL-3.0. JSON-only subset.
 * https://github.com/vividhq/vivimusic (listen-together/ListenTogetherClient.kt)
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
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

    data class Error(
        val message: String,
        val recoverable: Boolean,
    ) : TogetherPublicEvent()

    data object Disconnected : TogetherPublicEvent()
}

/**
 * WebSocket client for the public Listen Together servers (vivi protocol, JSON only).
 * Binary frames are rejected: the server would be speaking protobuf, which we do not support.
 */
internal class TogetherPublicClient(
    private val externalScope: CoroutineScope,
    private val serverUrl: String,
    private val dataStore: DataStore<Preferences>,
    private val username: String,
) {
    var onEvent: ((TogetherPublicEvent) -> Unit)? = null

    private var webSocket: WebSocket? = null
    private var pingJob: Job? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempts = 0
    private var manuallyClosed = false
    private var sessionToken: String? = null
    private var roomCode: String? = null
    private var isHost = false
    private var pendingAction: TogetherPublicPendingAction? = null

    private val httpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

    private val json: Json = TogetherPublicJson

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
        sendNull(TogetherPublicMessageTypes.LEAVE_ROOM)
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
            TogetherPublicApproveJoinPayload(userId = userId),
        )
    }

    fun rejectJoin(
        userId: String,
        reason: String? = null,
    ) {
        send(
            TogetherPublicMessageTypes.REJECT_JOIN,
            TogetherPublicRejectJoinPayload(userId = userId, reason = reason),
        )
    }

    fun sendPlaybackAction(action: TogetherPublicPlaybackActionPayload) {
        send(
            TogetherPublicMessageTypes.PLAYBACK_ACTION,
            action,
        )
    }

    fun sendBufferReady(trackId: String) {
        send(
            TogetherPublicMessageTypes.BUFFER_READY,
            TogetherPublicBufferReadyPayload(trackId = trackId),
        )
    }

    fun requestSync() {
        sendNull(TogetherPublicMessageTypes.REQUEST_SYNC)
    }

    fun kickUser(
        userId: String,
        reason: String? = null,
    ) {
        send(
            TogetherPublicMessageTypes.KICK_USER,
            TogetherPublicKickUserPayload(userId = userId, reason = reason),
        )
    }

    fun transferHost(newHostId: String) {
        send(
            TogetherPublicMessageTypes.TRANSFER_HOST,
            TogetherPublicTransferHostPayload(newHostId = newHostId),
        )
    }

    private fun sendNull(type: String) {
        val message = TogetherPublicMessage(type = type, payload = null)
        val text = json.encodeToString(message)
        webSocket?.send(text)
    }

    private inline fun <reified T> send(
        type: String,
        payload: T,
    ) {
        val message =
            TogetherPublicMessage(
                type = type,
                payload = json.encodeToJsonElement(payload),
            )
        val text = json.encodeToString(message)
        webSocket?.send(text)
    }

    private val listener =
        object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                reconnectAttempts = 0
                startPingJob()
                val token = sessionToken
                if (token != null) {
                    send(
                        TogetherPublicMessageTypes.RECONNECT,
                        TogetherPublicReconnectPayload(sessionToken = token),
                    )
                } else {
                    executePendingAction()
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleTextMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                onEvent?.invoke(
                    TogetherPublicEvent.Error(
                        message = "Unsupported server protocol (binary frames)",
                        recoverable = false,
                    ),
                )
                webSocket.close(1003, "Protobuf unsupported")
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
                    TogetherPublicCreateRoomPayload(username = action.username),
                )
            }

            is TogetherPublicPendingAction.JoinRoom -> {
                send(
                    TogetherPublicMessageTypes.JOIN_ROOM,
                    TogetherPublicJoinRoomPayload(roomCode = action.roomCode.uppercase(), username = action.username),
                )
            }
        }
    }

    private fun handleTextMessage(text: String) {
        val message =
            runCatching { json.decodeFromString<TogetherPublicMessage>(text) }
                .getOrElse { t ->
                    onEvent?.invoke(TogetherPublicEvent.Error("Failed to decode message", true))
                    return
                }
        when (message.type) {
            TogetherPublicMessageTypes.ROOM_CREATED -> {
                val payload = message.payload<TogetherPublicRoomCreatedPayload>() ?: return
                sessionToken = payload.sessionToken
                roomCode = payload.roomCode
                isHost = true
                externalScope.launch {
                    dataStore.edit { prefs ->
                        prefs[TogetherPublicSessionTokenKey] = payload.sessionToken
                        prefs[TogetherPublicRoomCodeKey] = payload.roomCode
                        prefs[TogetherPublicIsHostKey] = true
                    }
                }
                onEvent?.invoke(TogetherPublicEvent.RoomCreated(payload.roomCode, payload.userId))
            }

            TogetherPublicMessageTypes.JOIN_APPROVED -> {
                val payload = message.payload<TogetherPublicJoinApprovedPayload>() ?: return
                sessionToken = payload.sessionToken
                roomCode = payload.roomCode
                isHost = false
                externalScope.launch {
                    dataStore.edit { prefs ->
                        prefs[TogetherPublicSessionTokenKey] = payload.sessionToken
                        prefs[TogetherPublicRoomCodeKey] = payload.roomCode
                        prefs[TogetherPublicIsHostKey] = false
                    }
                }
                onEvent?.invoke(
                    TogetherPublicEvent.JoinApproved(
                        roomCode = payload.roomCode,
                        userId = payload.userId,
                        state = payload.state.toTogetherRoomState(sessionId = payload.roomCode),
                    ),
                )
            }

            TogetherPublicMessageTypes.JOIN_REJECTED -> {
                val payload = message.payload<TogetherPublicJoinRejectedPayload>()
                onEvent?.invoke(TogetherPublicEvent.JoinRejected(payload?.reason ?: "rejected"))
            }

            TogetherPublicMessageTypes.JOIN_REQUEST -> {
                val payload = message.payload<TogetherPublicJoinRequestPayload>() ?: return
                onEvent?.invoke(TogetherPublicEvent.JoinRequested(payload.userId, payload.username))
            }

            TogetherPublicMessageTypes.USER_JOINED -> {
                val payload = message.payload<TogetherPublicUserJoinedPayload>() ?: return
                onEvent?.invoke(TogetherPublicEvent.UserJoined(payload.userId, payload.username))
            }

            TogetherPublicMessageTypes.USER_LEFT -> {
                val payload = message.payload<TogetherPublicUserLeftPayload>() ?: return
                onEvent?.invoke(TogetherPublicEvent.UserLeft(payload.userId, payload.username))
            }

            TogetherPublicMessageTypes.USER_RECONNECTED -> {
                val payload = message.payload<TogetherPublicUserReconnectedPayload>() ?: return
                onEvent?.invoke(TogetherPublicEvent.UserReconnected(payload.userId, payload.username))
            }

            TogetherPublicMessageTypes.USER_DISCONNECTED -> {
                val payload = message.payload<TogetherPublicUserDisconnectedPayload>() ?: return
                onEvent?.invoke(TogetherPublicEvent.UserDisconnected(payload.userId, payload.username))
            }

            TogetherPublicMessageTypes.SYNC_STATE -> {
                val payload = message.payload<TogetherPublicSyncStatePayload>() ?: return
                onEvent?.invoke(TogetherPublicEvent.SyncState(payload))
            }

            TogetherPublicMessageTypes.REQUEST_SYNC -> {
                onEvent?.invoke(TogetherPublicEvent.SyncRequested)
            }

            TogetherPublicMessageTypes.SYNC_PLAYBACK -> {
                val payload = message.payload<TogetherPublicPlaybackActionPayload>() ?: return
                onEvent?.invoke(TogetherPublicEvent.SyncPlayback(payload))
            }

            TogetherPublicMessageTypes.HOST_CHANGED -> {
                val payload = message.payload<TogetherPublicHostChangedPayload>() ?: return
                onEvent?.invoke(TogetherPublicEvent.HostChanged(roomCode ?: "", payload.newHostId, payload.newHostName))
            }

            TogetherPublicMessageTypes.KICKED -> {
                val payload = message.payload<TogetherPublicKickedPayload>()
                onEvent?.invoke(TogetherPublicEvent.Kicked(payload?.reason ?: "kicked"))
            }

            TogetherPublicMessageTypes.RECONNECTED -> {
                val payload = message.payload<TogetherPublicReconnectedPayload>() ?: return
                onEvent?.invoke(
                    TogetherPublicEvent.Reconnected(
                        roomCode = payload.roomCode,
                        userId = payload.userId,
                        state = payload.state.toTogetherRoomState(sessionId = payload.roomCode),
                        isHost = payload.isHost,
                    ),
                )
            }

            TogetherPublicMessageTypes.ERROR -> {
                val payload = message.payload<TogetherPublicErrorPayload>()
                onEvent?.invoke(TogetherPublicEvent.Error(payload?.message ?: "server error", true))
            }

            TogetherPublicMessageTypes.PONG -> {
                // Keep-alive acknowledged; nothing to do.
            }

            else -> {
                // Unknown message types are ignored for forward compatibility.
            }
        }
    }

    private inline fun <reified T> TogetherPublicMessage.payload(): T? =
        payload?.let { element ->
            try {
                json.decodeFromJsonElement(element)
            } catch (e: Exception) {
                null
            }
        }

    private fun startPingJob() {
        pingJob?.cancel()
        pingJob =
            externalScope.launch {
                while (isActive) {
                    delay(25_000L)
                    sendNull(TogetherPublicMessageTypes.PING)
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
}
