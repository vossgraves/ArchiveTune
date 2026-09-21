/*
 * ArchiveTune (2026)
 * © vossgraves — github.com/vossgraves
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import moe.rukamori.archivetune.playback.stream.NewPipeStreamUrlExtractor
import moe.rukamori.archivetune.playback.stream.StreamUrlExtractor

/**
 * Binds the cipher backend used by the injectable playback tiers. The player-response pipelines
 * reach the same object directly, because they are static call sites with no injection scope.
 */
@Module
@InstallIn(SingletonComponent::class)
object ExtractorModule {
    @Provides
    @Singleton
    fun provideStreamUrlExtractor(): StreamUrlExtractor = NewPipeStreamUrlExtractor
}
