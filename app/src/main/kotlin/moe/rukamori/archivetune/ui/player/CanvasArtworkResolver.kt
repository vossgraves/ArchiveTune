/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.canvas.AppleMusicProvider
import moe.rukamori.archivetune.canvas.SpotifyCanvasProvider
import moe.rukamori.archivetune.canvas.models.CanvasArtwork
import moe.rukamori.archivetune.canvas.models.looselyMatchesSongIdentity
import moe.rukamori.archivetune.canvas.models.matchesSongIdentity
import moe.rukamori.archivetune.telegram.isTelegramMediaId
import moe.rukamori.archivetune.utils.isLocalMediaId
import timber.log.Timber

internal suspend fun resolveCanvasArtworkForPlayback(
    mediaId: String,
    songTitleRaw: String,
    artistNameRaw: String,
    storefront: String,
    requireVertical: Boolean,
    allowNetwork: Boolean,
    albumTitle: String? = null,
    trySpotifyCanvas: Boolean = false,
): CanvasArtwork? {
    // Telegram/local files have tag-derived (often noisy) metadata — use fuzzy identity matching
    // so a real canvas isn't discarded over a "(2019)" suffix or a channel-name artist.
    val strictIdentity = !(mediaId.isTelegramMediaId() || mediaId.isLocalMediaId())
    // Fast path: try the cache with preferCachedOnly=true via getCachedOnlyFast,
    // which skips the expensive MediaExtractor probe (~50-200ms per file).
    // The probe was the dominant contributor to canvas startup latency on
    // cache hits — see CanvasArtworkPlaybackCache.getCachedOnlyFast for details.
    val cachedArtwork =
        withContext(Dispatchers.IO) {
            CanvasArtworkPlaybackCache.getCachedOnlyFast(mediaId)
                ?: CanvasArtworkPlaybackCache.get(
                    mediaId = mediaId,
                    preferCachedOnly = true,
                )
        }
    if (cachedArtwork != null) {
        val isValid =
            cachedArtwork.hasRequiredCanvasVariant(requireVertical) &&
                cachedArtwork.matchesIdentity(songTitleRaw, artistNameRaw, strictIdentity)
        if (isValid) return cachedArtwork
        withContext(Dispatchers.IO) {
            CanvasArtworkPlaybackCache.remove(mediaId)
        }
    }

    if (!allowNetwork || mediaId.isBlank()) {
        Timber.tag(CanvasArtworkLogTag).d("Skipping canvas network lookup for %s", mediaId)
        return null
    }

    return withContext(Dispatchers.IO) {
        // Spotify Canvas: when enabled and the current media is a YouTube Music video
        // (i.e. mediaId is the video ID), look up the official Spotify Canvas via the
        // mlc.kouzu.in resolver. This is a direct video-ID → canvas-URL lookup and
        // doesn't depend on tag metadata, so it's both faster and more reliable than
        // the song-title-based ArchiveTune Canvas lookup. Try it first when enabled.
        if (trySpotifyCanvas && strictIdentity) {
            val spotifyCanvas =
                runCatching {
                    SpotifyCanvasProvider.getByVideoId(mediaId)
                }.onFailure { throwable ->
                    Timber.tag(CanvasArtworkLogTag).w(throwable, "Spotify Canvas lookup failed for %s", mediaId)
                }.getOrNull()
            if (spotifyCanvas != null && spotifyCanvas.hasRequiredCanvasVariant(requireVertical)) {
                Timber.tag(CanvasArtworkLogTag).d("Spotify Canvas resolved for %s", mediaId)
                return@withContext CanvasArtworkPlaybackCache.put(mediaId, spotifyCanvas)
            }
        }

        val fetched =
            fetchCanvasArtworkForPlayback(
                songTitleRaw = songTitleRaw,
                artistNameRaw = artistNameRaw,
                storefront = storefront,
                requireVertical = requireVertical,
                strictIdentity = strictIdentity,
                albumTitle = albumTitle,
            )

        if (fetched == null) {
            Timber.tag(CanvasArtworkLogTag).d("No playable canvas resolved for %s", mediaId)
            return@withContext null
        }

        CanvasArtworkPlaybackCache.put(mediaId, fetched)
    }
}

internal suspend fun fetchCanvasArtworkForPlayback(
    songTitleRaw: String,
    artistNameRaw: String,
    storefront: String,
    requireVertical: Boolean,
    forceRefresh: Boolean = false,
    strictIdentity: Boolean = true,
    albumTitle: String? = null,
): CanvasArtwork? {
    val songTitle = normalizeCanvasSongTitle(songTitleRaw)
    val artistName = normalizeCanvasArtistName(artistNameRaw)
    val candidates =
        linkedSetOf(
            songTitle to artistName,
            songTitleRaw to artistName,
            songTitle to artistNameRaw,
            songTitleRaw to artistNameRaw,
        ).filter { (song, artist) ->
            song.isNotBlank() && artist.isNotBlank()
        }

    return candidates.firstNotNullOfOrNull { (song, artist) ->
        AppleMusicProvider
            .getBySongArtist(
                song = song,
                artist = artist,
                storefront = storefront,
                forceRefresh = forceRefresh,
                album = albumTitle,
            )?.takeIf { artwork ->
                artwork.matchesIdentity(songTitleRaw, artistNameRaw, strictIdentity) &&
                    artwork.hasRequiredCanvasVariant(requireVertical)
            }
    }
}

internal suspend fun refetchCanvasArtworkForPlayback(
    mediaId: String,
    songTitleRaw: String,
    artistNameRaw: String,
    storefront: String,
    requireVertical: Boolean,
    albumTitle: String? = null,
): CanvasArtwork? {
    if (mediaId.isBlank()) return null

    return withContext(Dispatchers.IO) {
        val fetched =
            fetchCanvasArtworkForPlayback(
                songTitleRaw = songTitleRaw,
                artistNameRaw = artistNameRaw,
                storefront = storefront,
                requireVertical = requireVertical,
                forceRefresh = true,
                strictIdentity = !(mediaId.isTelegramMediaId() || mediaId.isLocalMediaId()),
                albumTitle = albumTitle,
            ) ?: return@withContext null

        CanvasArtworkPlaybackCache.replace(mediaId, fetched)
    }
}

private fun CanvasArtwork.matchesIdentity(
    songTitleRaw: String,
    artistNameRaw: String,
    strict: Boolean,
): Boolean =
    if (strict) {
        matchesSongIdentity(songTitleRaw, artistNameRaw)
    } else {
        // Album-level motion artwork carries the album name in `name`, so an exact/fuzzy song
        // match may legitimately fail; accept it when the song lookup already vouched for it.
        looselyMatchesSongIdentity(songTitleRaw, artistNameRaw) || !albumName.isNullOrBlank()
    }

private fun CanvasArtwork.hasRequiredCanvasVariant(requireVertical: Boolean): Boolean =
    if (requireVertical) {
        !preferredVerticalAnimationUrl.isNullOrBlank()
    } else {
        !preferredAnimationUrl.isNullOrBlank()
    }

private const val CanvasArtworkLogTag = "CanvasArtwork"

private fun normalizeCanvasSongTitle(raw: String): String {
    val stripped =
        raw
            // Leading track numbers ("01. ", "12 - ") common in files shared on Telegram.
            .replace(Regex("^\\s*\\d{1,3}\\s*[.\\-]\\s*"), "")
            .replace(Regex("\\s*\\[[^]]*]"), "")
            .replace(
                Regex(
                    "\\s*\\((?:feat\\.?|ft\\.?|featuring|with)\\b[^)]*\\)",
                    RegexOption.IGNORE_CASE,
                ),
                "",
            ).replace(
                Regex(
                    "\\s*\\((?:official\\s*)?(?:music\\s*)?(?:video|mv|lyrics?|audio|visualizer|live|remaster(?:ed)?|version|edit|mix|remix)[^)]*\\)",
                    RegexOption.IGNORE_CASE,
                ),
                "",
            ).replace(
                Regex(
                    "\\s*-\\s*(?:official\\s*)?(?:music\\s*)?(?:video|mv|lyrics?|audio|visualizer|live|remaster(?:ed)?|version|edit|mix|remix)\\b.*$",
                    RegexOption.IGNORE_CASE,
                ),
                "",
            ).replace(Regex("\\s+"), " ")
            .trim()

    return stripped
        .trim('-')
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun normalizeCanvasArtistName(raw: String): String {
    val first =
        raw
            .split(
                Regex(
                    "(?:\\s*,\\s*|\\s*&\\s*|\\s+x\\s+|\\bfeat\\.?\\b|\\bft\\.?\\b|\\bfeaturing\\b|\\bwith\\b)",
                    RegexOption.IGNORE_CASE,
                ),
                limit = 2,
            ).firstOrNull()
            .orEmpty()

    return first.replace(Regex("\\s+"), " ").trim()
}
