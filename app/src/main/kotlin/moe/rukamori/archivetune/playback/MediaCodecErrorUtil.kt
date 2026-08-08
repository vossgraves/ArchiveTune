/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import android.media.MediaCodec
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.mediacodec.MediaCodecDecoderException
import androidx.media3.exoplayer.mediacodec.MediaCodecRenderer

/**
 * Classifies [PlaybackException]s that represent recoverable MediaCodec decoder-state faults.
 *
 * These surface as `PlaybackException` with errorCode == ERROR_CODE_DECODING_FAILED (4003)
 * or ERROR_CODE_DECODER_INIT_FAILED, wrapping a chain that includes any of:
 *  - `androidx.media3.exoplayer.mediacodec.MediaCodecDecoderException`
 *    ("Decoder failed: c2.mtk.alac.decoder" etc.) — runtime codec fault.
 *  - `androidx.media3.exoplayer.mediacodec.MediaCodecRenderer.DecoderInitializationException`
 *    — codec failed to initialize.
 *  - `android.media.MediaCodec.CodecException` — the underlying platform codec exception,
 *    notably "Error 0x80000000" (undefined codec error) on MediaTek's ALAC decoder under
 *    memory pressure.
 *  - `IllegalStateException` with the "queueInputBuffer() is valid only at Executing
 *    states; currently at Released state" message — renderer races against an
 *    already-released codec.
 *
 * The codec itself is recoverable — the player just needs to be re-prepared so a fresh
 * codec instance is instantiated. [MusicService.onPlayerError] handles the recovery
 * transparently, so callers that just need to decide whether to surface the error to the
 * UI should consult this function to avoid flashing a dialog during auto-recovery.
 *
 * We match by CLASS TYPE first (most reliable), then by message substring as a fallback
 * so we still catch OEM-specific exception subclasses that don't inherit from the
 * expected media3 classes.
 */
internal fun isRecoverableMediaCodecStateError(error: PlaybackException): Boolean {
    // Fast path: decoding-failed error code with no deeper classification still applies.
    val isDecodingErrorCode =
        error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED

    val causeChain = generateSequence<Throwable>(error) { it.cause }
    // Match by class type.
    val hasCodecExceptionClass = causeChain.any { throwable ->
        throwable is MediaCodec.CodecException ||
            throwable is MediaCodecDecoderException ||
            throwable is MediaCodecRenderer.DecoderInitializationException
    }
    // Match by message — covers OEM subclasses and stripped cause messages.
    val hasCodecStateMessage = causeChain.any { throwable ->
        val message = throwable.message.orEmpty()
        (message.contains("queueInputBuffer", ignoreCase = true) &&
            message.contains("Executing states", ignoreCase = true)) ||
            message.contains("currently at Released state", ignoreCase = true) ||
            message.contains("codec is in state", ignoreCase = true) ||
            // "Decoder failed: <codec-name>" — the MediaCodecDecoderException signature
            // for runtime codec faults. Codec names like c2.mtk.alac.decoder match here.
            (message.contains("Decoder failed", ignoreCase = true) &&
                message.contains("decoder", ignoreCase = true)) ||
            // Generic undefined MediaCodec error (high bit set). Seen on MediaTek's
            // c2.mtk.alac.decoder when the OS reclaims the codec under memory pressure.
            message.contains("0x80000000", ignoreCase = true) ||
            // ALAC-specific decoder-name references in any cause message.
            message.contains("alac.decoder", ignoreCase = true) ||
            message.contains("c2.mtk.alac", ignoreCase = true)
    }

    return (isDecodingErrorCode && (hasCodecExceptionClass || hasCodecStateMessage)) ||
        hasCodecExceptionClass ||
        hasCodecStateMessage
}
