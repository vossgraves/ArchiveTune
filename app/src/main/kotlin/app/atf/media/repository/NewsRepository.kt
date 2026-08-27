/*
 * ArchiveTune (2026)
 * © ArchiveTuneFork contributors — github.com/vossgraves/ArchiveTune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package app.atf.media.repository

import app.atf.media.models.NewsItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NewsRepository
    @Inject
    constructor() {
        @Volatile private var metadataCache: List<NewsItem>? = null

        suspend fun fetchNews(): List<NewsItem> = emptyList()

        suspend fun fetchNewsContent(id: String): String = ""

        fun getCachedItem(id: String): NewsItem? = metadataCache?.find { it.id == id }
    }
