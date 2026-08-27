/*
 * ArchiveTune (2026)
 * © ArchiveTuneFork contributors — github.com/vossgraves/ArchiveTune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package app.atf.media.ui.theme

import androidx.compose.ui.graphics.Color
import app.atf.media.playback.artwork.PlayerPaletteCacheKey

/**
 * Shared player palette cache keyed by the actual artwork identity (mediaId + provider +
 * artwork identity + background mode + theme). A changed artwork URL under the same mediaId
 * therefore produces a fresh entry instead of reusing a palette extracted from another image.
 *
 * Only successful extractions are stored; failures are never cached so a temporary network
 * problem cannot permanently poison the palette.
 */
object PlayerPaletteCache {
    private const val MAX_ENTRIES = 48

    private val cache =
        object : LinkedHashMap<PlayerPaletteCacheKey, List<Color>>(16, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<PlayerPaletteCacheKey, List<Color>>?,
            ): Boolean = size > MAX_ENTRIES
        }

    @Synchronized
    fun get(key: PlayerPaletteCacheKey): List<Color>? = cache[key]

    @Synchronized
    fun put(
        key: PlayerPaletteCacheKey,
        colors: List<Color>,
    ) {
        cache[key] = colors
    }

    @Synchronized
    fun clear() = cache.clear()
}
