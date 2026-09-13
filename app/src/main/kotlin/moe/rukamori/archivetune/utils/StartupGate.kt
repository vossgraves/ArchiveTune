/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import android.os.Build
import android.os.Trace
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

private val startupTraceIds = AtomicInteger()

/** Wraps a startup step in a systrace section, so `perfetto` can attribute cold-launch time. */
internal inline fun <T> traceStartup(
    name: String,
    block: () -> T,
): T {
    Trace.beginSection(name)
    return try {
        block()
    } finally {
        Trace.endSection()
    }
}

/** The suspending form. Async sections need API 29; below that the work still runs, untraced. */
internal suspend fun <T> traceStartupAsync(
    name: String,
    block: suspend () -> T,
): T {
    val cookie = startupTraceIds.incrementAndGet()
    val traceable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    if (traceable) Trace.beginAsyncSection(name, cookie)
    return try {
        block()
    } finally {
        if (traceable) Trace.endAsyncSection(name, cookie)
    }
}

/**
 * The cold-launch gate: configuration must land before anything reads a preference, and warm-up
 * work must not compete with the frame that puts the app on screen.
 *
 * Replaces the earlier `StartupReadiness`, which had the same shape and two ways to hang forever.
 * Both are fixed here by construction rather than by remembering to avoid them:
 *
 *  - **Nobody has to remember to start it.** The old one had a separate `initialize(configure)` that
 *    some caller had to invoke; every `awaitReady()` reached before that call blocked forever. With
 *    the work needed at 19 call sites across five files, one path arriving early was a launch that
 *    never finished, and on a phone the only cure is a reinstall. This takes [configure] at
 *    construction and the first caller to need it runs it, so "forgot to initialise" cannot happen.
 *
 *  - **A headless start still runs its warm-ups.** The old `runOptional` waited on a first frame
 *    unconditionally. A process started with no Activity — a Bluetooth or headset button, a widget
 *    tap, Android Auto, a scheduled download — never draws one, so every optional block waited for
 *    an event that was never coming and leaked its coroutine. Here the frame wait is bounded: the
 *    frame short-circuits it, and [firstFrameDeadline] releases it when no frame is on the way.
 *
 * Failure is retried rather than latched. A sticky failure meant one bad read at launch left
 * playback dead for the life of the process; the mutex already serialises callers, so the next one
 * simply tries again.
 *
 * @param firstFrameDeadline how long optional work waits for the first frame before going anyway.
 * @param configure the one-time startup configuration. Runs at most once to success.
 */
class StartupGate(
    private val firstFrameDeadline: Duration = DEFAULT_FIRST_FRAME_DEADLINE,
    private val configure: suspend () -> Unit,
) {
    private val configureMutex = Mutex()
    private val firstFrame = CompletableDeferred<Unit>()
    private val optionalWorkPermits = Semaphore(OPTIONAL_WORK_CONCURRENCY)

    private val _result = MutableStateFlow<Result<Unit>?>(null)

    /** Null until configuration has been attempted. Drives the splash/error state in the UI. */
    val result: StateFlow<Result<Unit>?> = _result.asStateFlow()

    /**
     * Suspends until configuration has succeeded, running it if no one else has.
     *
     * Waits for configuration only, never for an Activity or a frame — the playback service, the
     * widget and the download queue all reach this on paths with no UI at all.
     */
    suspend fun awaitReady() {
        if (_result.value?.isSuccess == true) return
        configureMutex.withLock {
            if (_result.value?.isSuccess == true) return
            val outcome = runCatching { traceStartupAsync("StartupGate.configure", configure) }
            _result.value = outcome
            outcome.getOrThrow()
        }
    }

    /** Called once the first frame is on screen. Extra calls are ignored. */
    fun onFirstFrame() {
        firstFrame.complete(Unit)
    }

    /**
     * Runs warm-up work that should not compete with the first frame.
     *
     * Bounded to [OPTIONAL_WORK_CONCURRENCY] at a time: these are prefetches and cache warms, and
     * letting all of them go at once on a cold start is how warm-up work ends up costing more than
     * it saves.
     */
    suspend fun runOptional(block: suspend () -> Unit) {
        awaitReady()
        awaitFirstFrameOrDeadline()
        optionalWorkPermits.withPermit { block() }
    }

    private suspend fun awaitFirstFrameOrDeadline() {
        if (firstFrame.isCompleted) return
        withTimeoutOrNull(firstFrameDeadline) { firstFrame.await() }
    }

    companion object {
        /**
         * Long enough that a slow cold start still gets its frame first, short enough that a
         * headless start is not sitting on its warm-ups for a noticeable part of a song.
         */
        val DEFAULT_FIRST_FRAME_DEADLINE: Duration = 5.seconds

        /** Two at a time: enough to overlap IO waits, few enough to leave the UI a core. */
        const val OPTIONAL_WORK_CONCURRENCY = 2
    }
}
