/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.di

import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheSpan
import java.io.File
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LazyCacheTest {
    @Test(timeout = 10_000)
    fun waitingReaderDoesNotPreventWriterCommit() = checkWriterCanProgress("commitFile")

    @Test(timeout = 10_000)
    fun waitingReaderDoesNotPreventHoleRelease() = checkWriterCanProgress("releaseHoleSpan")

    private fun checkWriterCanProgress(writerOperation: String) {
        val readerWaiting = CountDownLatch(1)
        val writerFinished = CountDownLatch(1)
        val hole = CacheSpan("track", 0L, 128L)
        val cache = LazyCache {
            fakeCache { method ->
                when (method) {
                    "startReadWrite" -> {
                        readerWaiting.countDown()
                        check(writerFinished.await(5, TimeUnit.SECONDS)) { "Writer was blocked by the reader" }
                        hole
                    }
                    writerOperation -> {
                        writerFinished.countDown()
                        null
                    }
                    else -> error("Unexpected cache operation: $method")
                }
            }
        }
        val workers = Executors.newFixedThreadPool(2)
        try {
            val reader = workers.submit<CacheSpan> { cache.startReadWrite("track", 0L, 128L) }
            assertTrue(readerWaiting.await(2, TimeUnit.SECONDS))
            val writer = workers.submit {
                if (writerOperation == "commitFile") cache.commitFile(File("fragment"), 128L)
                else cache.releaseHoleSpan(hole)
            }
            writer.get(2, TimeUnit.SECONDS)
            assertEquals(hole, reader.get(2, TimeUnit.SECONDS))
        } finally {
            workers.shutdownNow()
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test(timeout = 10_000)
    fun concurrentAccessCreatesOnlyOneDelegate() {
        val creations = AtomicInteger()
        val cache = LazyCache {
            creations.incrementAndGet()
            fakeCache { if (it == "getCacheSpace") 42L else null }
        }
        val workers = Executors.newFixedThreadPool(4)
        try {
            val readers = List(20) { workers.submit<Long> { cache.cacheSpace } }
            readers.forEach { assertEquals(42L, it.get(2, TimeUnit.SECONDS)) }
            assertEquals(1, creations.get())
        } finally {
            workers.shutdownNow()
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun releasingUnusedCacheNeverCreatesIt() {
        val cache = LazyCache { error("Release must not initialize a cache") }
        cache.release()
        cache.release()
        assertThrows(IllegalStateException::class.java) { cache.cacheSpace }
    }

    @Test
    fun releaseIsTerminalAndIdempotent() {
        val creations = AtomicInteger()
        val releases = AtomicInteger()
        val cache = LazyCache {
            creations.incrementAndGet()
            fakeCache {
                when (it) {
                    "getCacheSpace" -> 1L
                    "release" -> { releases.incrementAndGet(); null }
                    else -> error("Unexpected cache operation: $it")
                }
            }
        }
        assertEquals(1L, cache.cacheSpace)
        cache.release()
        cache.release()
        assertThrows(IllegalStateException::class.java) { cache.cacheSpace }
        assertEquals(1, creations.get())
        assertEquals(1, releases.get())
    }

    private fun fakeCache(call: (String) -> Any?): Cache =
        Proxy.newProxyInstance(Cache::class.java.classLoader, arrayOf(Cache::class.java)) { _, method, _ ->
            call(method.name)
        } as Cache
}
