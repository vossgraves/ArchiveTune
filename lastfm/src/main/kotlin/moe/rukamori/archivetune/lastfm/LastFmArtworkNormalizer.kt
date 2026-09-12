/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.lastfm

import moe.rukamori.archivetune.lastfm.models.UserImage

/**
 * Port of LastWave-native's [com.lastwave.app.data.artwork.ArtworkNormalizer] — a faithful copy of
 * the image-selection helpers LastWave uses at every Last.fm image-array read site.
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
