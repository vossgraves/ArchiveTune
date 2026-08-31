/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.together

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Conformance tests for the Metrolist protobuf codec, ported from SimpMusic's
 * MessageCodecTest (itself ported from Metrolist, GPL-3.0).
 *
 * These are not unit tests: they exist to catch the day this codec stops producing the
 * bytes the Metrolist servers and clients expect. `envelopeFieldNumbersMatchTheProtoSchema`
 * is the load-bearing one — it pins the assumption the whole port rests on, that
 * kotlinx-serialization-protobuf and protoc agree on the wire.
 */
@OptIn(ExperimentalSerializationApi::class)
class TogetherPublicCodecTest {
    private val codec = TogetherPublicProtoCodec(compressionEnabled = true)
    private val proto = ProtoBuf { encodeDefaults = true }

    @Test
    fun playbackTimingFieldsSurviveARoundTrip() {
        val action =
            TogetherPublicPlaybackActionPayload(
                action = TogetherPublicPlaybackActions.PLAY,
                trackId = "track",
                position = 1_234L,
                serverTime = 9_000L,
            )

        val (type, payload) =
            codec.decode(
                codec.encode(TogetherPublicMessageTypes.PLAYBACK_ACTION, action.toWireAction()),
            )
        val decoded = codec.decodePayload(TogetherPublicMessageTypes.SYNC_PLAYBACK, payload)

        assertTrue(decoded is TogetherWirePlaybackAction)
        decoded as TogetherWirePlaybackAction
        assertEquals(TogetherPublicMessageTypes.PLAYBACK_ACTION, type)
        assertEquals(action.action, decoded.action)
        assertEquals(action.trackId, decoded.trackId)
        assertEquals(action.position, decoded.position)
        assertEquals(action.serverTime, decoded.serverTime)
    }

    @Test
    fun timestampedPingIsEncodedAndPongIsDecoded() {
        val ping = TogetherWirePingPayload(clientTime = 1_000L, sequence = 3L)
        val (_, pingBytes) = codec.decode(codec.encode(TogetherPublicMessageTypes.PING, ping))
        val encodedPing = proto.decodeFromByteArray(TogetherWirePingPayload.serializer(), pingBytes)
        assertEquals(1_000L, encodedPing.clientTime)
        assertEquals(3L, encodedPing.sequence)

        // Built the way a server builds it — envelope first, payload inside — rather than
        // through our own encode(), so the decode path is exercised against bytes we did
        // not shape.
        val pong =
            TogetherWirePongPayload(
                clientTime = 1_000L,
                serverReceiveTime = 10_000L,
                serverSendTime = 10_001L,
                sequence = 3L,
            )
        val envelope =
            proto.encodeToByteArray(
                TogetherWireEnvelope.serializer(),
                TogetherWireEnvelope(
                    type = TogetherPublicMessageTypes.PONG,
                    payload = proto.encodeToByteArray(TogetherWirePongPayload.serializer(), pong),
                ),
            )

        val (type, pongBytes) = codec.decode(envelope)
        val decoded = codec.decodePayload(type, pongBytes)

        assertTrue(decoded is TogetherWirePongPayload)
        decoded as TogetherWirePongPayload
        assertEquals(pong, decoded)
        assertTrue(decoded.serverSendTime >= decoded.serverReceiveTime)
    }

    @Test
    fun aPayloadOverTheThresholdIsCompressedAndStillRoundTrips() {
        // A queue is the one message that reliably exceeds the threshold, and the only
        // place compression is ever exercised in practice.
        val queue =
            List(40) {
                TogetherWireTrackInfo(
                    id = "video$it",
                    title = "Track number $it",
                    artist = "Some artist",
                    duration = 180_000L,
                )
            }
        val action = TogetherWirePlaybackAction(action = TogetherPublicPlaybackActions.SYNC_QUEUE, queue = queue)

        val frame = codec.encode(TogetherPublicMessageTypes.PLAYBACK_ACTION, action)
        val envelope = proto.decodeFromByteArray(TogetherWireEnvelope.serializer(), frame)
        assertTrue("a queue of 40 tracks should be past the compression threshold", envelope.compressed)

        val (_, payload) = codec.decode(frame)
        val decoded = codec.decodePayload(TogetherPublicMessageTypes.PLAYBACK_ACTION, payload)
        assertTrue(decoded is TogetherWirePlaybackAction)
        decoded as TogetherWirePlaybackAction
        assertEquals(queue, decoded.queue)
    }

    @Test
    fun anUnknownMessageTypeDecodesToNullRatherThanThrowing() {
        // Metrolist may ship a message type before we do. A client that dies on an
        // unrecognised frame cannot share a room with a newer one.
        val frame = codec.encode("some_type_from_a_future_release", TogetherWirePingPayload(clientTime = 1L, sequence = 1L))
        val (type, payload) = codec.decode(frame)
        assertEquals("some_type_from_a_future_release", type)
        assertNull(codec.decodePayload(type, payload))
    }

    @Test
    fun chatFromAMetrolistPeerDoesNotBreakTheStream() {
        // ArchiveTune draws no chat UI, but a Metrolist user in the room WILL send it.
        val frame = codec.encode("chat", null)
        val (type, _) = codec.decode(frame)
        assertEquals("chat", type)
        assertNull(codec.decodePayload(type, ByteArray(0)))
    }

    @Test
    fun envelopeFieldNumbersMatchTheProtoSchema() {
        // Field numbers ARE the contract. This catches a reorder that would otherwise
        // only show up as "cannot join a room".
        val envelope =
            TogetherWireEnvelope(type = "ping", payload = byteArrayOf(1, 2, 3), compressed = true)
        val bytes = proto.encodeToByteArray(TogetherWireEnvelope.serializer(), envelope)
        val back = proto.decodeFromByteArray(TogetherWireEnvelope.serializer(), bytes)
        assertEquals(envelope, back)

        // field 1 (string) is tag 0x0A, field 2 (bytes) is tag 0x12, field 3 (bool) is tag 0x18
        assertEquals(0x0A.toByte(), bytes[0])
        assertTrue(bytes.contains(0x12.toByte()))
        assertTrue(bytes.contains(0x18.toByte()))
    }

    @Test
    fun trackInfoSurvivesNestingInsideAPlaybackAction() {
        val track =
            TogetherWireTrackInfo(
                id = "abc",
                title = "T",
                artist = "A",
                album = "Al",
                duration = 1000L,
                thumbnail = "u",
            )
        val action =
            TogetherWirePlaybackAction(
                action = TogetherPublicPlaybackActions.CHANGE_TRACK,
                trackInfo = track,
            )
        val (_, payload) = codec.decode(codec.encode(TogetherPublicMessageTypes.PLAYBACK_ACTION, action))
        val decoded = codec.decodePayload(TogetherPublicMessageTypes.PLAYBACK_ACTION, payload)

        assertTrue(decoded is TogetherWirePlaybackAction)
        decoded as TogetherWirePlaybackAction
        assertNotNull(decoded.trackInfo)
        assertEquals(track, decoded.trackInfo)
    }

    /**
     * The handshake is the one exchange whose type strings are NOT in `listentogether.proto`
     * — the schema defines the two messages but never names the envelope type that carries
     * them. These literals therefore come from the server: metroserver
     * `internal/server/protocol.go`. Getting either wrong fails in the worst possible way:
     * the socket opens, the frame is sent, the server answers `unknown_message_type` — so
     * the connection looks alive while the handshake never completes and no room is ever
     * joined. Pinning the literals is what turns that into a test failure instead.
     */
    @Test
    fun capabilityHandshakeUsesTheServersOwnTypeNames() {
        assertEquals("client_capabilities", TogetherPublicMessageTypes.CLIENT_CAPABILITIES)
        assertEquals("server_capabilities", TogetherPublicMessageTypes.SERVER_CAPABILITIES)

        val (sentType, sentPayload) =
            codec.decode(
                codec.encode(
                    TogetherPublicMessageTypes.CLIENT_CAPABILITIES,
                    TogetherWireClientCapabilities(
                        supportsProtobuf = true,
                        supportsCompression = true,
                        clientVersion = "test",
                    ),
                ),
            )
        assertEquals(TogetherPublicMessageTypes.CLIENT_CAPABILITIES, sentType)
        val sent = proto.decodeFromByteArray(TogetherWireClientCapabilities.serializer(), sentPayload)
        // The server rejects a client that claims false, with `unsupported_client`.
        assertTrue(sent.supportsProtobuf)
        assertEquals("test", sent.clientVersion)

        // Built the way the server builds it: the client never encodes a
        // ServerCapabilities, so this half only ever arrives through decodePayload.
        val answer =
            proto.encodeToByteArray(
                TogetherWireEnvelope.serializer(),
                TogetherWireEnvelope(
                    type = TogetherPublicMessageTypes.SERVER_CAPABILITIES,
                    payload =
                        proto.encodeToByteArray(
                            TogetherWireServerCapabilities.serializer(),
                            TogetherWireServerCapabilities(
                                supportsProtobuf = true,
                                supportsCompression = true,
                                serverVersion = "1",
                            ),
                        ),
                ),
            )
        val (answerType, answerPayload) = codec.decode(answer)
        val caps = codec.decodePayload(answerType, answerPayload)

        assertTrue(caps is TogetherWireServerCapabilities)
        caps as TogetherWireServerCapabilities
        assertEquals("1", caps.serverVersion)
        assertTrue(caps.supportsCompression)
    }

    /**
     * The Metrolist server strips the current track from every queue it relays
     * (sanitizeUpcomingQueue), so the client must put the head back before the app's
     * index arithmetic can point at the right row. Without this, every guest's queue
     * is off by one and a "play song N" tap lands on song N-1.
     */
    @Test
    fun theServerQueueExcludesTheCurrentTrackAndTheClientRestoresIt() {
        val current = TogetherWireTrackInfo(id = "current", title = "Now playing", artist = "A", duration = 1L)
        val upcoming =
            listOf(
                TogetherWireTrackInfo(id = "next", title = "Up next", artist = "A", duration = 1L),
                TogetherWireTrackInfo(id = "last", title = "Then this", artist = "A", duration = 1L),
            )
        val relayed =
            TogetherWirePlaybackAction(
                action = TogetherPublicPlaybackActions.SYNC_QUEUE,
                trackId = current.id,
                trackInfo = current,
                queue = upcoming,
            )

        val appAction = relayed.toAppAction()

        assertEquals(listOf("current", "next", "last"), appAction.queue.orEmpty().map { it.id })
        assertEquals(0, appAction.queue.orEmpty().indexOfFirst { it.id == "current" })
    }

    @Test
    fun anExplicitlyClearedQueueIsNotPaddedWithTheCurrentTrack() {
        // queue_clear and an empty sync_queue must survive as empty; the repair above
        // must not resurrect a track the host deliberately removed.
        val relayed =
            TogetherWirePlaybackAction(
                action = TogetherPublicPlaybackActions.SYNC_QUEUE,
                trackId = "",
                trackInfo = null,
                queue = emptyList(),
            )

        val appAction = relayed.toAppAction()
        assertTrue(appAction.queue.orEmpty().isEmpty())
    }

    @Test
    fun appActionToWireAndBackLosesNoFieldsThatMatter() {
        val appAction =
            TogetherPublicPlaybackActionPayload(
                action = TogetherPublicPlaybackActions.CHANGE_TRACK,
                trackId = "abc",
                position = 42L,
                trackInfo =
                    TogetherPublicTrackInfo(
                        id = "abc",
                        title = "Title",
                        artist = "Artist",
                        duration = 100_000L,
                        thumbnail = "https://example.test/t.jpg",
                    ),
                insertNext = true,
                queue =
                    listOf(
                        TogetherPublicTrackInfo(id = "abc", title = "Title", artist = "Artist", duration = 100_000L),
                    ),
            )

        val (type, payload) =
            codec.decode(codec.encode(TogetherPublicMessageTypes.PLAYBACK_ACTION, appAction.toWireAction()))
        val decoded = codec.decodePayload(type, payload)

        assertTrue(decoded is TogetherWirePlaybackAction)
        decoded as TogetherWirePlaybackAction
        val roundTripped = decoded.toAppAction()
        assertEquals(appAction.action, roundTripped.action)
        assertEquals(appAction.trackId, roundTripped.trackId)
        assertEquals(appAction.position, roundTripped.position)
        assertEquals(appAction.trackInfo, roundTripped.trackInfo)
        assertEquals(appAction.insertNext, roundTripped.insertNext)
        assertEquals(appAction.queue, roundTripped.queue)
    }
}
