/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback.stream

import android.os.Looper
import androidx.annotation.WorkerThread
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.guava.future
import timber.log.Timber
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ResolveAudioStreamUseCase
    @Inject
    constructor(
        private val nativeRepository: NativeStreamRepository,
        private val ytdlnisRepository: YtdlnisStreamRepository,
    ) {
    private data class CacheKey(
        val mediaId: String,
        val quality: String,
        val networkMetered: Boolean,
        val purpose: StreamPurpose,
        val authFingerprint: String,
        val pinnedFormatId: Int?,
    )

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val cache = ConcurrentHashMap<CacheKey, ResolvedAudioStream>()
        private val inFlight = ConcurrentHashMap<CacheKey, Deferred<ResolvedAudioStream>>()

        suspend operator fun invoke(request: AudioStreamRequest): ResolvedAudioStream {
            val key = request.cacheKey()
            cache[key]?.takeIf(::isFresh)?.let { return it }
            cache.remove(key)

            val candidate =
                scope.async(start = CoroutineStart.LAZY) {
                    resolveUncached(request).also { resolved ->
                        storeResolvedStream(key, resolved)
                    }
                }
            val active = inFlight.putIfAbsent(key, candidate)
            if (active == null) {
                candidate.invokeOnCompletion { inFlight.remove(key, candidate) }
                candidate.start()
                return candidate.await()
            }
            candidate.cancel()
            return active.await()
        }

        @WorkerThread
        fun resolveBlocking(request: AudioStreamRequest): ResolvedAudioStream {
            check(Looper.myLooper() != Looper.getMainLooper())
            val timeoutSeconds =
                if (request.purpose == StreamPurpose.DOWNLOAD) {
                    DOWNLOAD_RESOLUTION_TIMEOUT_SECONDS
                } else {
                    PLAYBACK_RESOLUTION_TIMEOUT_SECONDS
                }
            val future = scope.future { invoke(request) }
            return try {
                future.get(timeoutSeconds, TimeUnit.SECONDS)
            } catch (throwable: TimeoutException) {
                future.cancel(true)
                throw SocketTimeoutException(
                    "Audio stream resolution timed out after $timeoutSeconds seconds",
                ).apply { initCause(throwable) }
            } catch (throwable: ExecutionException) {
                future.cancel(true)
                throw throwable.cause ?: throwable
            } catch (throwable: Throwable) {
                future.cancel(true)
                throw throwable
            }
        }

        fun invalidate(mediaId: String) {
            cache.keys.removeIf { it.mediaId == mediaId }
            inFlight.entries.forEach { (key, resolution) ->
                if (key.mediaId == mediaId && inFlight.remove(key, resolution)) {
                    resolution.cancel()
                }
            }
        }

        fun invalidateUrl(url: String) {
            cache.entries.removeIf { it.value.url == url }
        }

        fun peek(request: AudioStreamRequest): ResolvedAudioStream? {
            val key = request.cacheKey()
            val resolved = cache[key] ?: return null
            if (isFresh(resolved)) return resolved
            cache.remove(key, resolved)
            return null
        }

        fun clear() {
            cache.clear()
            inFlight.values.forEach { it.cancel() }
            inFlight.clear()
        }

    // Hybrid resolver: InnerTube (native, BotGuard/QuickJS) first — fast, ~30 MB, no Python.
    // Only on failure (403, age-gate, signature, timeout) does it fall back to Ytdlnis
    // (NewPipe → external yt-dlp via CompactYtDlp plugin APK, as YTDLnis does). This mirrors
    // YTDLnis's own switch (NewPipe ↔ yt-dlp) but keeps the hot path native. History can be
    // returned by both: InnerTube via YouTube.history() (browse), YTDLnis via yt-dlp watch
    // history with cookies (ytdlp_watch_history), but ArchiveTune's History uses InnerTube.
    private suspend fun resolveUncached(request: AudioStreamRequest): ResolvedAudioStream {
        val nativeFailure = try {
            return nativeRepository.resolve(request)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Timber.tag(TAG).w(t, "Native InnerTube failed for %s, trying Ytdlnis fallback", request.mediaId)
            t
        }
        return try {
            ytdlnisRepository.resolve(request)
        } catch (c: CancellationException) {
            throw c
        } catch (ytdlnisFailure: Throwable) {
            ytdlnisFailure.addSuppressed(nativeFailure)
            throw ytdlnisFailure
        }
    }

        private fun AudioStreamRequest.cacheKey(): CacheKey =
            CacheKey(
                mediaId = mediaId,
                quality = quality.name,
                networkMetered = networkMetered,
                purpose = purpose,
                authFingerprint = authState.streamCacheFingerprint,
                pinnedFormatId = pinnedFormatId,
            )

        private fun storeResolvedStream(
            key: CacheKey,
            resolved: ResolvedAudioStream,
        ) {
            cache[key] = resolved
            Timber.tag(TAG).d(
                "Resolved %s via %s",
                key.mediaId,
                resolved.source,
            )
            if (cache.size <= MAX_CACHE_ENTRIES) return
            cache.entries.removeIf { !isFresh(it.value) }
            val excess = cache.size - MAX_CACHE_ENTRIES
            if (excess > 0) {
                cache.entries
                    .sortedBy { it.value.expiresAtMs }
                    .take(excess)
                    .forEach { entry -> cache.remove(entry.key, entry.value) }
            }
        }

        private fun isFresh(stream: ResolvedAudioStream): Boolean =
            stream.expiresAtMs > System.currentTimeMillis() + STREAM_EXPIRY_SAFETY_MS

        private companion object {
            const val TAG = "AudioStreamResolver"
            const val STREAM_EXPIRY_SAFETY_MS = 60_000L
            const val MAX_CACHE_ENTRIES = 256
            const val PLAYBACK_RESOLUTION_TIMEOUT_SECONDS = 120L
            const val DOWNLOAD_RESOLUTION_TIMEOUT_SECONDS = 180L
        }
    }
