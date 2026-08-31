/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Listen Together protobuf wire layer, ported from Metrolist (GPL-3.0) so that
 * ArchiveTune clients share rooms with Metrolist and SimpMusic clients on the
 * same public servers.
 * Metrolist Project (C) 2026 — Licensed under GPL-3.0 | See git history for contributors
 *
 * Source of truth: MetrolistGroup/metroproto → listentogether.proto. Every @ProtoNumber
 * below is that file's field number, and every constant is its string spelled exactly.
 * NOTHING here may be "improved": a renamed field or a reordered number is a client that
 * silently cannot join. The capability-handshake type strings are NOT in the .proto —
 * they come from the server itself (metroserver internal/server/protocol.go).
 */

package moe.rukamori.archivetune.together

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlinx.serialization.protobuf.ProtoBuf
import okio.Buffer
import okio.GzipSink
import okio.GzipSource
import okio.buffer

/**
 * The frame every message travels in.
 *
 * [compressed] says whether [payload] was gzipped before being placed here — the server
 * compresses anything larger than [TogetherPublicProtoCodec.COMPRESSION_THRESHOLD] bytes
 * and expects clients that advertised `supportsCompression` to do the same.
 */
@Serializable
internal data class TogetherWireEnvelope(
    @ProtoNumber(1) val type: String = "",
    @ProtoNumber(2) val payload: ByteArray = ByteArray(0),
    @ProtoNumber(3) val compressed: Boolean = false,
) {
    // ByteArray gives identity equality by default, which would make two envelopes
    // carrying the same bytes compare unequal — and the conformance test compares envelopes.
    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is TogetherWireEnvelope &&
                    type == other.type &&
                    compressed == other.compressed &&
                    payload.contentEquals(other.payload)
            )

    override fun hashCode(): Int = (type.hashCode() * 31 + payload.contentHashCode()) * 31 + compressed.hashCode()
}

@Serializable
internal data class TogetherWireTrackInfo(
    @ProtoNumber(1) val id: String = "",
    @ProtoNumber(2) val title: String = "",
    @ProtoNumber(3) val artist: String = "",
    @ProtoNumber(4) val album: String = "",
    /** Milliseconds. */
    @ProtoNumber(5) val duration: Long = 0L,
    @ProtoNumber(6) val thumbnail: String = "",
    @ProtoNumber(7) val suggestedBy: String = "",
)

@Serializable
internal data class TogetherWireUserInfo(
    @ProtoNumber(1) val userId: String = "",
    @ProtoNumber(2) val username: String = "",
    @ProtoNumber(3) val isHost: Boolean = false,
    @ProtoNumber(4) val isConnected: Boolean = false,
)

@Serializable
internal data class TogetherWireRoomState(
    @ProtoNumber(1) val roomCode: String = "",
    @ProtoNumber(2) val hostId: String = "",
    @ProtoNumber(3) val users: List<TogetherWireUserInfo> = emptyList(),
    @ProtoNumber(4) val currentTrack: TogetherWireTrackInfo? = null,
    @ProtoNumber(5) val isPlaying: Boolean = false,
    @ProtoNumber(6) val position: Long = 0L,
    @ProtoNumber(7) val lastUpdate: Long = 0L,
    @ProtoNumber(8) val volume: Float = 0f,
    @ProtoNumber(9) val queue: List<TogetherWireTrackInfo> = emptyList(),
    @ProtoNumber(10) val revision: Long = 0L,
)

// ───────────────────────────── client → server ─────────────────────────────

@Serializable
internal data class TogetherWireCreateRoomPayload(
    @ProtoNumber(1) val username: String = "",
)

@Serializable
internal data class TogetherWireJoinRoomPayload(
    @ProtoNumber(1) val roomCode: String = "",
    @ProtoNumber(2) val username: String = "",
)

@Serializable
internal data class TogetherWireApproveJoinPayload(
    @ProtoNumber(1) val userId: String = "",
)

@Serializable
internal data class TogetherWireRejectJoinPayload(
    @ProtoNumber(1) val userId: String = "",
    @ProtoNumber(2) val reason: String = "",
)

@Serializable
internal data class TogetherWirePlaybackAction(
    @ProtoNumber(1) val action: String = "",
    @ProtoNumber(2) val trackId: String = "",
    /** Milliseconds. */
    @ProtoNumber(3) val position: Long = 0L,
    @ProtoNumber(4) val trackInfo: TogetherWireTrackInfo? = null,
    @ProtoNumber(5) val insertNext: Boolean = false,
    @ProtoNumber(6) val queue: List<TogetherWireTrackInfo> = emptyList(),
    @ProtoNumber(7) val queueTitle: String = "",
    @ProtoNumber(8) val volume: Float = 0f,
    @ProtoNumber(9) val serverTime: Long = 0L,
    @ProtoNumber(10) val revision: Long = 0L,
    @ProtoNumber(11) val capturedAtServerTime: Long = 0L,
)

@Serializable
internal data class TogetherWirePingPayload(
    @ProtoNumber(1) val clientTime: Long = 0L,
    @ProtoNumber(2) val sequence: Long = 0L,
)

@Serializable
internal data class TogetherWireBufferReadyPayload(
    @ProtoNumber(1) val trackId: String = "",
)

@Serializable
internal data class TogetherWireKickUserPayload(
    @ProtoNumber(1) val userId: String = "",
    @ProtoNumber(2) val reason: String = "",
)

@Serializable
internal data class TogetherWireTransferHostPayload(
    @ProtoNumber(1) val newHostId: String = "",
)

@Serializable
internal data class TogetherWireSuggestTrackPayload(
    @ProtoNumber(1) val trackInfo: TogetherWireTrackInfo? = null,
)

@Serializable
internal data class TogetherWireApproveSuggestionPayload(
    @ProtoNumber(1) val suggestionId: String = "",
)

@Serializable
internal data class TogetherWireRejectSuggestionPayload(
    @ProtoNumber(1) val suggestionId: String = "",
    @ProtoNumber(2) val reason: String = "",
)

@Serializable
internal data class TogetherWireReconnectPayload(
    @ProtoNumber(1) val sessionToken: String = "",
)

// ───────────────────────────── server → client ─────────────────────────────

@Serializable
internal data class TogetherWireRoomCreatedPayload(
    @ProtoNumber(1) val roomCode: String = "",
    @ProtoNumber(2) val userId: String = "",
    @ProtoNumber(3) val sessionToken: String = "",
)

@Serializable
internal data class TogetherWireJoinRequestPayload(
    @ProtoNumber(1) val userId: String = "",
    @ProtoNumber(2) val username: String = "",
)

@Serializable
internal data class TogetherWireJoinApprovedPayload(
    @ProtoNumber(1) val roomCode: String = "",
    @ProtoNumber(2) val userId: String = "",
    @ProtoNumber(3) val sessionToken: String = "",
    @ProtoNumber(4) val state: TogetherWireRoomState? = null,
)

@Serializable
internal data class TogetherWireJoinRejectedPayload(
    @ProtoNumber(1) val reason: String = "",
)

@Serializable
internal data class TogetherWireUserJoinedPayload(
    @ProtoNumber(1) val userId: String = "",
    @ProtoNumber(2) val username: String = "",
)

@Serializable
internal data class TogetherWireUserLeftPayload(
    @ProtoNumber(1) val userId: String = "",
    @ProtoNumber(2) val username: String = "",
)

@Serializable
internal data class TogetherWireBufferWaitPayload(
    @ProtoNumber(1) val trackId: String = "",
    @ProtoNumber(2) val waitingFor: List<String> = emptyList(),
)

@Serializable
internal data class TogetherWireBufferCompletePayload(
    @ProtoNumber(1) val trackId: String = "",
)

@Serializable
internal data class TogetherWireErrorPayload(
    @ProtoNumber(1) val code: String = "",
    @ProtoNumber(2) val message: String = "",
)

@Serializable
internal data class TogetherWireHostChangedPayload(
    @ProtoNumber(1) val newHostId: String = "",
    @ProtoNumber(2) val newHostName: String = "",
)

@Serializable
internal data class TogetherWireKickedPayload(
    @ProtoNumber(1) val reason: String = "",
)

@Serializable
internal data class TogetherWireSyncStatePayload(
    @ProtoNumber(1) val currentTrack: TogetherWireTrackInfo? = null,
    @ProtoNumber(2) val isPlaying: Boolean = false,
    @ProtoNumber(3) val position: Long = 0L,
    @ProtoNumber(4) val lastUpdate: Long = 0L,
    @ProtoNumber(5) val queue: List<TogetherWireTrackInfo> = emptyList(),
    @ProtoNumber(6) val volume: Float = 0f,
    @ProtoNumber(7) val revision: Long = 0L,
)

@Serializable
internal data class TogetherWirePongPayload(
    @ProtoNumber(1) val clientTime: Long = 0L,
    @ProtoNumber(2) val serverReceiveTime: Long = 0L,
    @ProtoNumber(3) val serverSendTime: Long = 0L,
    @ProtoNumber(4) val sequence: Long = 0L,
)

@Serializable
internal data class TogetherWireReconnectedPayload(
    @ProtoNumber(1) val roomCode: String = "",
    @ProtoNumber(2) val userId: String = "",
    @ProtoNumber(3) val state: TogetherWireRoomState? = null,
    @ProtoNumber(4) val isHost: Boolean = false,
)

@Serializable
internal data class TogetherWireUserReconnectedPayload(
    @ProtoNumber(1) val userId: String = "",
    @ProtoNumber(2) val username: String = "",
)

@Serializable
internal data class TogetherWireUserDisconnectedPayload(
    @ProtoNumber(1) val userId: String = "",
    @ProtoNumber(2) val username: String = "",
)

@Serializable
internal data class TogetherWireSuggestionReceivedPayload(
    @ProtoNumber(1) val suggestionId: String = "",
    @ProtoNumber(2) val fromUserId: String = "",
    @ProtoNumber(3) val fromUsername: String = "",
    @ProtoNumber(4) val trackInfo: TogetherWireTrackInfo? = null,
)

@Serializable
internal data class TogetherWireSuggestionApprovedPayload(
    @ProtoNumber(1) val suggestionId: String = "",
    @ProtoNumber(2) val trackInfo: TogetherWireTrackInfo? = null,
)

@Serializable
internal data class TogetherWireSuggestionRejectedPayload(
    @ProtoNumber(1) val suggestionId: String = "",
    @ProtoNumber(2) val reason: String = "",
)

// ───────────────────────── capability negotiation ─────────────────────────

/**
 * Sent first, before anything else. The server answers `unsupported_client` unless
 * `supportsProtobuf` is true, so there is no room for an honest "false" here.
 */
@Serializable
internal data class TogetherWireClientCapabilities(
    @ProtoNumber(1) val supportsProtobuf: Boolean = false,
    @ProtoNumber(2) val supportsCompression: Boolean = false,
    @ProtoNumber(3) val clientVersion: String = "",
)

@Serializable
internal data class TogetherWireServerCapabilities(
    @ProtoNumber(1) val supportsProtobuf: Boolean = false,
    @ProtoNumber(2) val supportsCompression: Boolean = false,
    @ProtoNumber(3) val serverVersion: String = "",
)

/**
 * Encodes and decodes the binary frames the Metrolist servers speak.
 *
 * Ported from SimpMusic / Metrolist (GPL-3.0). `encodeDefaults = false` is not a
 * preference — it is what proto3 means: a field holding its default value is absent
 * from the wire, which is what protoc and Go's proto.Marshal both emit. With `true`,
 * encoding a payload carrying a null message field (`trackInfo` on every play, pause
 * and seek) throws instead of sending.
 */
@OptIn(ExperimentalSerializationApi::class)
internal class TogetherPublicProtoCodec(
    private val compressionEnabled: Boolean = true,
) {
    private val proto = ProtoBuf { encodeDefaults = false }

    /** Encodes one message into a complete frame. */
    fun encode(
        msgType: String,
        payload: Any?,
    ): ByteArray {
        val payloadBytes = payload?.let { encodePayload(it) } ?: ByteArray(0)
        // Below the threshold gzip reliably makes the frame LARGER — its header alone is 10 bytes.
        val compress = compressionEnabled && payloadBytes.size > COMPRESSION_THRESHOLD
        val body = if (compress) gzip(payloadBytes) else payloadBytes
        return proto.encodeToByteArray(
            TogetherWireEnvelope.serializer(),
            TogetherWireEnvelope(type = msgType, payload = body, compressed = compress),
        )
    }

    /**
     * Unwraps a frame into its type and its still-encoded payload.
     *
     * Decompression failure returns the payload untouched rather than throwing: the flag is
     * set by the sender, and a frame we cannot inflate is more likely mislabelled than fatal.
     */
    fun decode(data: ByteArray): Pair<String, ByteArray> {
        val envelope = proto.decodeFromByteArray(TogetherWireEnvelope.serializer(), data)
        val body =
            if (envelope.compressed) {
                gunzip(envelope.payload) ?: envelope.payload
            } else {
                envelope.payload
            }
        return envelope.type to body
    }

    /**
     * Decodes a payload once its type is known.
     *
     * An unknown type returns null instead of throwing — Metrolist may add message types
     * before we do, and a client that dies on an unrecognised frame cannot share a room
     * with a newer one. `chat` and the buffer messages are unknown to us on purpose.
     */
    fun decodePayload(
        msgType: String,
        payloadBytes: ByteArray,
    ): Any? =
        when (msgType) {
            TogetherPublicMessageTypes.CREATE_ROOM -> decode(TogetherWireCreateRoomPayload.serializer(), payloadBytes)
            TogetherPublicMessageTypes.JOIN_ROOM -> decode(TogetherWireJoinRoomPayload.serializer(), payloadBytes)
            TogetherPublicMessageTypes.APPROVE_JOIN -> decode(TogetherWireApproveJoinPayload.serializer(), payloadBytes)
            TogetherPublicMessageTypes.REJECT_JOIN -> decode(TogetherWireRejectJoinPayload.serializer(), payloadBytes)
            TogetherPublicMessageTypes.PLAYBACK_ACTION, TogetherPublicMessageTypes.SYNC_PLAYBACK ->
                decode(TogetherWirePlaybackAction.serializer(), payloadBytes)

            TogetherPublicMessageTypes.BUFFER_READY -> decode(TogetherWireBufferReadyPayload.serializer(), payloadBytes)
            TogetherPublicMessageTypes.KICK_USER -> decode(TogetherWireKickUserPayload.serializer(), payloadBytes)
            TogetherPublicMessageTypes.TRANSFER_HOST -> decode(TogetherWireTransferHostPayload.serializer(), payloadBytes)
            TogetherPublicMessageTypes.PING -> decode(TogetherWirePingPayload.serializer(), payloadBytes)
            TogetherPublicMessageTypes.PONG -> decode(TogetherWirePongPayload.serializer(), payloadBytes)
            TogetherPublicMessageTypes.RECONNECT -> decode(TogetherWireReconnectPayload.serializer(), payloadBytes)
            TogetherPublicMessageTypes.SUGGEST_TRACK -> decode(TogetherWireSuggestTrackPayload.serializer(), payloadBytes)
            TogetherPublicMessageTypes.APPROVE_SUGGESTION ->
                decode(TogetherWireApproveSuggestionPayload.serializer(), payloadBytes)

            TogetherPublicMessageTypes.REJECT_SUGGESTION ->
                decode(TogetherWireRejectSuggestionPayload.serializer(), payloadBytes)

            TogetherPublicMessageTypes.ROOM_CREATED -> decode(TogetherWireRoomCreatedPayload.serializer(), payloadBytes)
            TogetherPublicMessageTypes.JOIN_REQUEST -> decode(TogetherWireJoinRequestPayload.serializer(), payloadBytes)
            TogetherPublicMessageTypes.JOIN_APPROVED -> decode(TogetherWireJoinApprovedPayload.serializer(), payloadBytes)
            TogetherPublicMessageTypes.JOIN_REJECTED -> decode(TogetherWireJoinRejectedPayload.serializer(), payloadBytes)
            TogetherPublicMessageTypes.USER_JOINED -> decode(TogetherWireUserJoinedPayload.serializer(), payloadBytes)
            TogetherPublicMessageTypes.USER_LEFT -> decode(TogetherWireUserLeftPayload.serializer(), payloadBytes)
            TogetherPublicMessageTypes.BUFFER_WAIT -> decode(TogetherWireBufferWaitPayload.serializer(), payloadBytes)
            TogetherPublicMessageTypes.BUFFER_COMPLETE -> decode(TogetherWireBufferCompletePayload.serializer(), payloadBytes)
            TogetherPublicMessageTypes.ERROR -> decode(TogetherWireErrorPayload.serializer(), payloadBytes)
            TogetherPublicMessageTypes.HOST_CHANGED -> decode(TogetherWireHostChangedPayload.serializer(), payloadBytes)
            TogetherPublicMessageTypes.KICKED -> decode(TogetherWireKickedPayload.serializer(), payloadBytes)
            TogetherPublicMessageTypes.SYNC_STATE -> decode(TogetherWireSyncStatePayload.serializer(), payloadBytes)
            TogetherPublicMessageTypes.RECONNECTED -> decode(TogetherWireReconnectedPayload.serializer(), payloadBytes)
            TogetherPublicMessageTypes.USER_RECONNECTED ->
                decode(TogetherWireUserReconnectedPayload.serializer(), payloadBytes)

            TogetherPublicMessageTypes.USER_DISCONNECTED ->
                decode(TogetherWireUserDisconnectedPayload.serializer(), payloadBytes)

            TogetherPublicMessageTypes.SUGGESTION_RECEIVED ->
                decode(TogetherWireSuggestionReceivedPayload.serializer(), payloadBytes)

            TogetherPublicMessageTypes.SUGGESTION_APPROVED ->
                decode(TogetherWireSuggestionApprovedPayload.serializer(), payloadBytes)

            TogetherPublicMessageTypes.SUGGESTION_REJECTED ->
                decode(TogetherWireSuggestionRejectedPayload.serializer(), payloadBytes)

            TogetherPublicMessageTypes.SERVER_CAPABILITIES ->
                decode(TogetherWireServerCapabilities.serializer(), payloadBytes)

            else -> null
        }

    private fun encodePayload(payload: Any): ByteArray =
        when (payload) {
            is TogetherWireCreateRoomPayload -> proto.encodeToByteArray(TogetherWireCreateRoomPayload.serializer(), payload)
            is TogetherWireJoinRoomPayload -> proto.encodeToByteArray(TogetherWireJoinRoomPayload.serializer(), payload)
            is TogetherWireApproveJoinPayload -> proto.encodeToByteArray(TogetherWireApproveJoinPayload.serializer(), payload)
            is TogetherWireRejectJoinPayload -> proto.encodeToByteArray(TogetherWireRejectJoinPayload.serializer(), payload)
            is TogetherWirePlaybackAction -> proto.encodeToByteArray(TogetherWirePlaybackAction.serializer(), payload)
            is TogetherWirePingPayload -> proto.encodeToByteArray(TogetherWirePingPayload.serializer(), payload)
            is TogetherWireBufferReadyPayload -> proto.encodeToByteArray(TogetherWireBufferReadyPayload.serializer(), payload)
            is TogetherWireKickUserPayload -> proto.encodeToByteArray(TogetherWireKickUserPayload.serializer(), payload)
            is TogetherWireTransferHostPayload -> proto.encodeToByteArray(TogetherWireTransferHostPayload.serializer(), payload)
            is TogetherWireSuggestTrackPayload -> proto.encodeToByteArray(TogetherWireSuggestTrackPayload.serializer(), payload)
            is TogetherWireApproveSuggestionPayload ->
                proto.encodeToByteArray(TogetherWireApproveSuggestionPayload.serializer(), payload)

            is TogetherWireRejectSuggestionPayload ->
                proto.encodeToByteArray(TogetherWireRejectSuggestionPayload.serializer(), payload)

            is TogetherWireReconnectPayload -> proto.encodeToByteArray(TogetherWireReconnectPayload.serializer(), payload)
            is TogetherWireClientCapabilities ->
                proto.encodeToByteArray(TogetherWireClientCapabilities.serializer(), payload)

            else -> ByteArray(0)
        }

    private fun <T> decode(
        serializer: kotlinx.serialization.DeserializationStrategy<T>,
        bytes: ByteArray,
    ): T? = runCatching { proto.decodeFromByteArray(serializer, bytes) }.getOrNull()

    private fun gzip(data: ByteArray): ByteArray {
        val sink = Buffer()
        GzipSink(sink).buffer().use { it.write(data) }
        return sink.readByteArray()
    }

    private fun gunzip(data: ByteArray): ByteArray? =
        runCatching {
            GzipSource(Buffer().apply { write(data) }).buffer().use { it.readByteArray() }
        }.getOrNull()

    companion object {
        /** The server's own threshold (codec.go): payloads above this are gzipped. */
        const val COMPRESSION_THRESHOLD = 100
    }
}

// ---------------------------------------------------------------------------
// Translation between the app-facing session models and the protobuf wire models
// ---------------------------------------------------------------------------

internal fun TogetherPublicTrackInfo.toWireTrack(): TogetherWireTrackInfo =
    TogetherWireTrackInfo(
        id = id,
        title = title,
        artist = artist,
        album = album.orEmpty(),
        duration = duration,
        thumbnail = thumbnail.orEmpty(),
        suggestedBy = suggestedBy.orEmpty(),
    )

internal fun TogetherWireTrackInfo.toAppTrack(): TogetherPublicTrackInfo =
    TogetherPublicTrackInfo(
        id = id,
        title = title,
        artist = artist,
        album = album.ifBlank { null },
        duration = duration,
        thumbnail = thumbnail.ifBlank { null },
        suggestedBy = suggestedBy.ifBlank { null },
    )

internal fun TogetherPublicPlaybackActionPayload.toWireAction(): TogetherWirePlaybackAction =
    TogetherWirePlaybackAction(
        action = action,
        trackId = trackId.orEmpty(),
        position = position ?: 0L,
        trackInfo = trackInfo?.toWireTrack(),
        insertNext = insertNext ?: false,
        queue = queue.orEmpty().map { it.toWireTrack() },
        volume = volume ?: 0f,
        serverTime = serverTime ?: 0L,
    )

/**
 * Wire → app, with the one semantic repair the Metrolist server forces: the server's room
 * queue holds UPCOMING tracks only (the current track is stripped by sanitizeUpcomingQueue),
 * so a queue that does not contain the action's current track is not a queue that lost its
 * head — it is the server's normal shape, and the head must be prepended back before the
 * app's index arithmetic can point at the right row.
 */
internal fun TogetherWirePlaybackAction.toAppAction(): TogetherPublicPlaybackActionPayload {
    val currentTrack = trackInfo
    val appQueue =
        if (currentTrack != null && queue.none { it.id == currentTrack.id }) {
            listOf(currentTrack) + queue
        } else {
            queue
        }
    return TogetherPublicPlaybackActionPayload(
        action = action,
        trackId = trackId.ifBlank { null },
        position = position,
        trackInfo = trackInfo?.toAppTrack(),
        insertNext = insertNext,
        queue = appQueue.map { it.toAppTrack() },
        volume = volume.takeIf { it != 0f },
        serverTime = serverTime.takeIf { it != 0L },
    )
}

internal fun TogetherWireRoomState.toAppState(): TogetherPublicRoomState =
    TogetherPublicRoomState(
        roomCode = roomCode,
        hostId = hostId,
        users =
            users.map { user ->
                TogetherPublicUserInfo(
                    userId = user.userId,
                    username = user.username,
                    isHost = user.isHost,
                    isConnected = user.isConnected,
                )
            },
        currentTrack = currentTrack?.toAppTrack(),
        isPlaying = isPlaying,
        position = position,
        lastUpdate = lastUpdate,
        volume = volume,
        queue = queue.map { it.toAppTrack() },
    )

internal fun TogetherWireSyncStatePayload.toAppSyncState(): TogetherPublicSyncStatePayload =
    TogetherPublicSyncStatePayload(
        currentTrack = currentTrack?.toAppTrack(),
        isPlaying = isPlaying,
        position = position,
        lastUpdate = lastUpdate,
        queue = queue.map { it.toAppTrack() },
        volume = volume.takeIf { it != 0f },
    )
