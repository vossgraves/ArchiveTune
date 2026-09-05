/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.di

import android.content.Context
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.ContentMetadataMutations
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import moe.rukamori.archivetune.constants.MaxSongCacheSizeKey
import moe.rukamori.archivetune.db.InternalDatabase
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.storage.StorageFolderKind
import moe.rukamori.archivetune.storage.StorageLocationRepository
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import java.io.File
import java.util.NavigableSet
import java.util.TreeSet
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PlayerCache

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DownloadCache

/**
 * EntryPoint for accessing the [PlayerCache]-qualified [Cache] from
 * non-Hilt-injected call sites (e.g. Compose composables like
 * [moe.rukamori.archivetune.ui.player.CanvasArtworkPlayer]).
 *
 * Used to wrap the canvas ExoPlayer's [androidx.media3.datasource.DataSource]
 * factory with a [androidx.media3.datasource.cache.CacheDataSource] so that
 * canvas video segments are cached across ExoPlayer re-creations (morph
 * transitions in the Apple Music player). This eliminates the multi-second
 * "static artwork flash" while a brand-new ExoPlayer re-fetches the canvas
 * after the COVER branch is re-entered.
 */
@dagger.hilt.EntryPoint
@InstallIn(SingletonComponent::class)
interface CanvasCacheEntryPoint {
    @PlayerCache
    fun playerCache(): Cache
}

internal class LazyCache(
    private val create: () -> Cache,
) : Cache {
    private val lock = Any()
    private var cache: Cache? = null
    private var released = false

    private fun delegate(): Cache =
        synchronized(lock) {
            check(!released) { "Cache has been released" }
            cache ?: create().also { cache = it }
        }

    // Media3 releases its own monitor while waiting for a hole span. An outer
    // monitor here would prevent the writer from committing or releasing that span.
    private inline fun <T> withDelegate(block: (Cache) -> T): T = block(delegate())

    override fun addListener(
        key: String,
        listener: Cache.Listener,
    ) = withDelegate { cache -> cache.addListener(key, listener) }

    override fun removeListener(
        key: String,
        listener: Cache.Listener,
    ) {
        val existing = synchronized(lock) { cache } ?: return
        existing.removeListener(key, listener)
    }

    override fun getCachedSpans(key: String): NavigableSet<CacheSpan> = withDelegate { cache -> cache.getCachedSpans(key) }

    override fun getKeys(): NavigableSet<String> = withDelegate { cache -> TreeSet(cache.keys) }

    override fun getCacheSpace(): Long = withDelegate { cache -> cache.cacheSpace }

    override fun getUid(): Long = withDelegate { cache -> cache.uid }

    override fun getCachedLength(
        key: String,
        position: Long,
        length: Long,
    ): Long = withDelegate { cache -> cache.getCachedLength(key, position, length) }

    override fun getCachedBytes(
        key: String,
        position: Long,
        length: Long,
    ): Long = withDelegate { cache -> cache.getCachedBytes(key, position, length) }

    override fun applyContentMetadataMutations(
        key: String,
        mutations: ContentMetadataMutations,
    ) = withDelegate { cache -> cache.applyContentMetadataMutations(key, mutations) }

    override fun getContentMetadata(key: String): ContentMetadata = withDelegate { cache -> cache.getContentMetadata(key) }

    override fun startReadWrite(
        key: String,
        position: Long,
        length: Long,
    ): CacheSpan = withDelegate { cache -> cache.startReadWrite(key, position, length) }

    override fun startReadWriteNonBlocking(
        key: String,
        position: Long,
        length: Long,
    ): CacheSpan? = withDelegate { cache -> cache.startReadWriteNonBlocking(key, position, length) }

    override fun startFile(
        key: String,
        position: Long,
        maxLength: Long,
    ): File = withDelegate { cache -> cache.startFile(key, position, maxLength) }

    override fun commitFile(
        file: File,
        length: Long,
    ) = withDelegate { cache -> cache.commitFile(file, length) }

    override fun releaseHoleSpan(holeSpan: CacheSpan) = withDelegate { cache -> cache.releaseHoleSpan(holeSpan) }

    override fun removeSpan(span: CacheSpan) = withDelegate { cache -> cache.removeSpan(span) }

    override fun removeResource(key: String) = withDelegate { cache -> cache.removeResource(key) }

    override fun isCached(
        key: String,
        position: Long,
        length: Long,
    ): Boolean = withDelegate { cache -> cache.isCached(key, position, length) }

    override fun release() {
        val toRelease = synchronized(lock) {
            if (released) return
            // Storage migration releases these process-scoped caches before moving
            // their directories and restarting. Never reopen the old directory.
            released = true
            cache.also { cache = null }
        }
        toRelease?.release()
    }
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Singleton
    @Provides
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): MusicDatabase = InternalDatabase.newInstance(context)

    @Singleton
    @Provides
    fun provideDatabaseProvider(
        @ApplicationContext context: Context,
    ): DatabaseProvider = StandaloneDatabaseProvider(context)

    @Singleton
    @Provides
    @PlayerCache
    fun providePlayerCache(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider,
    ): Cache =
        LazyCache {
            val cacheSize = context.dataStore.get(MaxSongCacheSizeKey, 1024)
            val evictor =
                when (cacheSize) {
                    -1 -> NoOpCacheEvictor()
                    else -> LeastRecentlyUsedCacheEvictor(cacheSizeMegabytesToBytes(cacheSize))
                }
            SimpleCache(
                StorageLocationRepository.cacheDirectory(context, StorageFolderKind.SONG_CACHE),
                evictor,
                databaseProvider,
            )
        }

    @Singleton
    @Provides
    @DownloadCache
    fun provideDownloadCache(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider,
    ): Cache =
        LazyCache {
            SimpleCache(
                StorageLocationRepository.cacheDirectory(context, StorageFolderKind.DOWNLOADS),
                NoOpCacheEvictor(),
                databaseProvider,
            )
        }
}

private const val CacheSizeBytesPerMegabyte = 1024L * 1024L

private fun cacheSizeMegabytesToBytes(sizeMegabytes: Int): Long = sizeMegabytes.toLong().coerceAtLeast(0L) * CacheSizeBytesPerMegabyte
