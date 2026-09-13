/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

private val startupTraceIds = java.util.concurrent.atomic.AtomicInteger()

internal inline fun <T> traceStartup(name: String, block: () -> T): T {
    android.os.Trace.beginSection(name)
    return try {
        block()
    } finally {
        android.os.Trace.endSection()
    }
}

internal suspend fun <T> traceStartupAsync(name: String, block: suspend () -> T): T {
    val cookie = startupTraceIds.incrementAndGet()
    val supportsAsyncTrace = android.os.Build.VERSION.SDK_INT >= 29
    if (supportsAsyncTrace) android.os.Trace.beginAsyncSection(name, cookie)
    return try {
        block()
    } finally {
        if (supportsAsyncTrace) android.os.Trace.endAsyncSection(name, cookie)
    }
}

class StartupReadiness {
    private val firstFrame = CompletableDeferred<Unit>()
    private val initializationMutex = Mutex()
    private val optionalWorkPermits = Semaphore(2)
    private val _result = kotlinx.coroutines.flow.MutableStateFlow<Result<Unit>?>(null)
    val result = _result.asStateFlow()

    suspend fun initialize(configure: suspend () -> Unit) {
        initializationMutex.withLock {
            val completed = _result.value
            if (completed != null) {
                completed.getOrThrow()
                return
            }
            try {
                configure()
                _result.value = Result.success(Unit)
            } catch (error: Throwable) {
                _result.value = Result.failure(error)
                throw error
            }
        }
    }

    // Service, widget and download entrypoints wait for configuration only,
    // never for an activity or a rendered frame.
    suspend fun awaitReady() = (result.value ?: result.filterNotNull().first()).getOrThrow()

    fun onFirstFrame() {
        firstFrame.complete(Unit)
    }

    suspend fun runOptional(block: suspend () -> Unit) {
        awaitReady()
        firstFrame.await()
        optionalWorkPermits.withPermit { block() }
    }
}
