/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback.stream

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import moe.rukamori.archivetune.innertube.PlaybackAuthState
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stub implementation of YtDlpRuntime that does not depend on Chaquopy.
 *
 * The Chaquopy Python plugin + yt-dlp pip packages were removed from the
 * build to reduce APK size (was ~45 MB, now ~30 MB). The yt-dlp stream
 * resolution layer is preserved as a no-op stub so that the existing
 * ResolveAudioStreamUseCase → YtDlpRuntime → NativeStreamRepository
 * fallback chain still compiles and works correctly — the yt-dlp path
 * simply throws immediately, and ResolveAudioStreamUseCase catches the
 * throwable and falls back to NativeStreamRepository (which uses
 * YTPlayerUtils.playerResponseForPlayback to resolve YouTube streams).
 *
 * If yt-dlp support is needed in the future, re-add the Chaquopy plugin
 * to app/build.gradle.kts and restore the full implementation from git
 * history (commit fb8b1efc5 or earlier).
 */
@Singleton
class YtDlpRuntime
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        /**
         * No-op prewarm. Always returns immediately without starting Python.
         */
        suspend fun preWarm() {
            // Intentional no-op — Python runtime not available.
        }

        /**
         * Always throws to signal that yt-dlp resolution is unavailable.
         * ResolveAudioStreamUseCase catches this and falls back to
         * NativeStreamRepository.
         */
        suspend fun resolve(
            request: AudioStreamRequest,
            authState: PlaybackAuthState,
        ): ResolvedAudioStream {
            throw YtDlpUnavailableException("yt-dlp Python runtime is not bundled in this build")
        }
    }

/**
 * Thrown when the yt-dlp runtime is not available (Chaquopy not bundled).
 */
class YtDlpUnavailableException(message: String) : Exception(message)

/**
 * Preserved for ResolveAudioStreamUseCase's catch clause.
 * Never actually thrown in this stub — kept for source compatibility.
 */
class YtDlpExtractionException(
    cause: Throwable,
) : Exception(cause.message, cause)
