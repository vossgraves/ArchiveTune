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

/** Classifies [PlaybackException]s that represent recoverable MediaCodec decoder-state faults. */
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
