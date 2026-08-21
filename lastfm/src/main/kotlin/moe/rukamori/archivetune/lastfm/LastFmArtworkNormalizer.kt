/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.lastfm

import moe.rukamori.archivetune.lastfm.models.UserImage

/**
 * Port of LastWave-native's [com.lastwave.app.data.artwork.ArtworkNormalizer] — a faithful
 * copy of the image-selection helpers LastWave uses at every Last.fm image-array read site.
 *
 * Two responsibilities, identical to the LastWave original:
 *
 *  1. **Placeholder filter** — Last.fm serves a real, non-blank URL for its own gray
 *     "no artwork" placeholder graphic. That URL contains the constant hash below; a blank
 *     check alone is not enough to tell real art from a placeholder, so any URL containing
 *     the hash is rejected by [isRealImage] before it is trusted.
 *
 *  2. **Size priority** — Last.fm image arrays are unordered w.r.t. size, so [bestImageUrl]
 *     picks the best quality by walking the canonical priority order
 *     `extralarge > large > medium > (any remaining real image)`, exactly matching the
 *     repeated `find(extralarge) || find(large) || find(medium) || find(any)` chain used
 *     throughout LastWave-native's home screen.
 *
 * Kept as a free-standing object, not a class, because the LastWave original has no state
 * here — just filtering.
 */
object LastFmArtworkNormalizer {
    /** The exact hash Last.fm embeds in its own gray "no artwork" placeholder image. */
    private const val LASTFM_NO_ART_HASH = "2a96cbd8b46e442fc41c2b86b821562f"

    /**
     * True iff [url] is a real, non-placeholder Last.fm image URL. A blank URL or one
     * containing the placeholder hash returns false.
     */
    fun isRealImage(url: String?): Boolean =
        !url.isNullOrBlank() && !url.contains(LASTFM_NO_ART_HASH)

    /**
     * `extralarge > large > medium > (any remaining real image)` — the exact priority order
     * LastWave-native uses at every image-array read site. Filters out the Last.fm
     * placeholder via [isRealImage] at every step.
     */
    fun bestImageUrl(images: List<UserImage>?): String? {
        if (images.isNullOrEmpty()) return null
        val bySize = { size: String ->
            images.firstOrNull { it.size.equals(size, ignoreCase = true) && isRealImage(it.text) }?.text
        }
        return bySize("extralarge")
            ?: bySize("large")
            ?: bySize("medium")
            ?: bySize("small")
            ?: images.firstOrNull { isRealImage(it.text) }?.text
    }
}
