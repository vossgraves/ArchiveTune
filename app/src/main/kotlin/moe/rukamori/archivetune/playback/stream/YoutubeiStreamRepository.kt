/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 * Portions © vossgraves — github.com/vossgraves
 */

package moe.rukamori.archivetune.playback.stream

import android.content.ComponentCallbacks2
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fork-native stand-in for upstream's youtubei-backed resolver.
 *
 * Upstream v15.0.0 resolves here through `morideobfuscator.youtubei.YoutubeiResolver`
 * (session pool + QuickJS player runtimes). The fork's pinned morideobfuscator submodule
 * (`4262067`) ships only the `ytdlp` package — there is no `youtubei` package — so the
 * upstream implementation cannot compile as-is. This class keeps the upstream surface
 * ([preWarm]/[invalidateSessions]/[trimMemory]) so
 * [moe.rukamori.archivetune.App.onTrimMemory] and future wiring can target it, while
 * actual resolution delegates to the fork's [NativeStreamRepository] (InnerTube,
 * BotGuard/QuickJS, ~30 MB, no Python).
 *
 * No eager runtime is created here — per the 15.0.0 memory trims, player runtimes stay
 * lazy (upstream `YoutubeiQuickJsWorker: avoid eager player OOM`, `bcd6484d8`).
 */
@Singleton
class YoutubeiStreamRepository
    @Inject
    constructor(
        private val nativeRepository: NativeStreamRepository,
        private val resolveAudioStream: ResolveAudioStreamUseCase,
    ) : AudioStreamRepository {
        override suspend fun resolve(request: AudioStreamRequest): ResolvedAudioStream =
            resolve(
                request = request,
                priority =
                    when (request.purpose) {
                        StreamPurpose.PLAYBACK -> StreamResolutionPriority.FOREGROUND
                        StreamPurpose.DOWNLOAD -> StreamResolutionPriority.BACKGROUND
                    },
            )

        internal suspend fun resolve(
            request: AudioStreamRequest,
            priority: StreamResolutionPriority,
        ): ResolvedAudioStream {
            // Priority is accepted for API parity with upstream (foreground preempts background
            // preloads there). The fork's resolver has no priority lanes, so the request
            // resolves identically either way; the parameter keeps call sites portable for
            // when a priority-aware resolver lands.
            return nativeRepository.resolve(request)
        }

        suspend fun preWarm() {
            // Intentionally a no-op: upstream pre-warms the YoutubeiResolver session pool here.
            // The fork has no such pool, and the 15.0.0 trims forbid eager runtime creation.
        }

        suspend fun invalidateSessions() {
            // Upstream drops youtubei sessions here (auth fingerprint rotation, logout).
            // Fork equivalent: drop cached streams keyed on the old fingerprint so the next
            // resolve re-authenticates, but leave in-flight resolutions alone — cancelling
            // them would hand the player a CancellationException mid-handoff.
            resolveAudioStream.clearCache()
        }

        fun trimMemory(level: Int) {
            // Single threshold lives in the use case (owner of the cache); this
            // only forwards. See ResolveAudioStreamUseCase.trimMemory for why the
            // gate is BACKGROUND, not UI_HIDDEN.
            resolveAudioStream.trimMemory(level)
        }
    }

internal enum class StreamResolutionPriority {
    FOREGROUND,
    BACKGROUND,
}
