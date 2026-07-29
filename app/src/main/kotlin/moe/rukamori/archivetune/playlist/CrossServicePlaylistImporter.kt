/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playlist

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.SongItem
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Resolves a playlist URL from a foreign music service (Apple Music,
 * Amazon Music, Tidal, Deezer) into a list of `(title, artist)` pairs,
 * which are then matched against YouTube Music via [YouTube.search] to
 * produce local YouTube Music song ids.
 *
 * YouTube Music URLs are handled natively by [YouTube.playlist].
 *
 * ## Supported URL formats
 *
 *  - **YouTube Music**: `https://music.youtube.com/playlist?list=...`
 *  - **YouTube**     : `https://www.youtube.com/playlist?list=...`
 *  - **Apple Music** : `https://music.apple.com/{cc}/playlist/{slug}/pl.{id}`
 *  - **Amazon Music**: `https://music.amazon.com/{cc}/playlists/{id}`
 *  - **Tidal**       : `https://tidal.com/browse/playlist/{id}`
 *  - **Deezer**      : `https://www.deezer.com/{cc}/playlist/{id}`
 *
 * ## Rate limiting
 *
 * Each foreign track triggers one YouTube Music search. To stay friendly
 * to the InnerTube API we cap concurrency at [MAX_PARALLEL_SEARCHES] and
 * stagger the searches. Failures on individual tracks are non-fatal —
 * the importer just skips them and reports the count.
 */
object CrossServicePlaylistImporter {

    /** The track-tuple extracted from the foreign service. */
    data class ForeignTrack(
        val title: String,
        val artist: String?,
        val album: String? = null,
        val durationMs: Long? = null,
    )

    /** The resolved import — playlist name + foreign tracks to look up. */
    data class ResolvedImport(
        val source: ImportSource,
        val sourcePlaylistId: String,
        val title: String,
        val thumbnailUrl: String?,
        val tracks: List<ForeignTrack>,
    )

    enum class ImportSource(val displayName: String) {
        YOUTUBE_MUSIC("YouTube Music"),
        APPLE_MUSIC("Apple Music"),
        AMAZON_MUSIC("Amazon Music"),
        TIDAL("Tidal"),
        DEEZER("Deezer"),
        UNKNOWN("Unknown"),
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /**
     * Detects the source service from a URL. Returns [ImportSource.UNKNOWN]
     * for unrecognized URLs so the caller can show a friendly error.
     */
    fun detectSource(url: String): ImportSource {
        // Match on the parsed *host*, never on a substring of the whole URL:
        // `https://evil.example/?ref=tidal.com` contains "tidal.com" but is not
        // Tidal, and we hand this URL straight to an HTTP GET further down.
        // Parsed with OkHttp's HttpUrl — the same parser that performs the
        // request — so validation can't disagree with what actually gets fetched
        // (it also strips userinfo, so `https://tidal.com@evil.example` resolves
        // to host `evil.example` and is correctly rejected).
        val parsed = url.trim().toHttpUrlOrNull() ?: return ImportSource.UNKNOWN

        // Only https:// is accepted so a playlist link can't downgrade the fetch.
        if (parsed.scheme != "https") return ImportSource.UNKNOWN

        val host = parsed.host.lowercase().removePrefix("www.")

        fun matches(domain: String) = host == domain || host.endsWith(".$domain")

        return when {
            matches("music.youtube.com") || matches("youtube.com") -> ImportSource.YOUTUBE_MUSIC
            matches("music.apple.com") -> ImportSource.APPLE_MUSIC
            matches("music.amazon.com") -> ImportSource.AMAZON_MUSIC
            matches("tidal.com") -> ImportSource.TIDAL
            matches("deezer.com") -> ImportSource.DEEZER
            else -> ImportSource.UNKNOWN
        }
    }

    /**
     * Fetches the foreign playlist metadata + tracklist. For YouTube Music
     * this delegates to the existing [YouTube.playlist] API and returns
     * tracks whose ids are already YouTube Music song ids (so callers can
     * skip the [resolveToYouTubeMusic] step). For all other services the
     * tracks are `(title, artist)` tuples that need to be looked up.
     */
    suspend fun fetchPlaylist(url: String): Result<ResolvedImport> = withContext(Dispatchers.IO) {
        runCatching {
            val source = detectSource(url)
            when (source) {
                ImportSource.YOUTUBE_MUSIC -> {
                    val playlistId = extractQuery(url, "list")
                        ?: error("Missing 'list' query parameter in YouTube URL")
                    val page = YouTube.playlist(playlistId).getOrNull()
                        ?: error("Could not fetch YouTube playlist (it may be private)")
                    ResolvedImport(
                        source = source,
                        sourcePlaylistId = playlistId,
                        title = page.playlist.title,
                        thumbnailUrl = page.playlist.thumbnail,
                        tracks = page.songs.map {
                            ForeignTrack(
                                title = it.title,
                                artist = it.artists.firstOrNull()?.name,
                                album = it.album?.name,
                                durationMs = it.duration?.toLong()?.times(1000L),
                            )
                        },
                    )
                }
                ImportSource.APPLE_MUSIC -> fetchAppleMusicPlaylist(url)
                ImportSource.AMAZON_MUSIC -> fetchAmazonMusicPlaylist(url)
                ImportSource.TIDAL -> fetchTidalPlaylist(url)
                ImportSource.DEEZER -> fetchDeezerPlaylist(url)
                ImportSource.UNKNOWN -> error("Unrecognized URL — supported: YouTube Music, Apple Music, Amazon Music, Tidal, Deezer")
            }
        }
    }

    /**
     * Resolves a list of [ForeignTrack]s to YouTube Music song ids via
     * [YouTube.search]. Returns the ids (in the same order as the input
     * where possible) — tracks that can't be matched are skipped.
     *
     * @param onProgress optional callback invoked with (resolved, total)
     *        after each track resolves. Lets the UI show a live counter.
     */
    suspend fun resolveToYouTubeMusic(
        tracks: List<ForeignTrack>,
        onProgress: ((Int, Int) -> Unit)? = null,
    ): List<String> = coroutineScope {
        val total = tracks.size
        if (total == 0) return@coroutineScope emptyList()
        val results = mutableListOf<String>()
        var completed = 0

        // Process in bounded-concurrency batches so we don't hammer
        // InnerTube with 100+ parallel searches for a 100-track playlist.
        tracks.chunked(MAX_PARALLEL_SEARCHES).forEach { batch ->
            val resolved = batch.map { track ->
                async {
                    val term = listOfNotNull(track.artist?.takeIf(String::isNotBlank), track.title)
                        .joinToString(" ")
                        .ifBlank { return@async null }
                    val search = YouTube.search(term, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                    val first = search?.items?.firstOrNull { it is SongItem } as? SongItem
                    first?.id
                }
            }.awaitAll()
            resolved.filterNotNull().forEach { results.add(it) }
            completed += batch.size
            onProgress?.invoke(completed, total)
        }
        results
    }

    // ─── Apple Music ──────────────────────────────────────────────────────
    // URL pattern: https://music.apple.com/{cc}/playlist/{slug}/pl.{u}-{id}
    // Apple Music's public page embeds a `<script name="schema:music-playlist"
    // type="application/json">` block containing the full track list with
    // name + artistName. We extract from that. If the schema block isn't
    // present (older layouts), we fall back to scraping the JSON-LD
    // MusicRecording entries.
    private suspend fun fetchAppleMusicPlaylist(url: String): ResolvedImport {
        val html = fetchText(url)
        val id = extractAppleMusicPlaylistId(url) ?: url
        val title = extractAppleMusicTitle(html)
        val tracks = parseAppleMusicTracks(html)
        return ResolvedImport(
            source = ImportSource.APPLE_MUSIC,
            sourcePlaylistId = id,
            title = title,
            thumbnailUrl = null,
            tracks = tracks,
        )
    }

    private fun extractAppleMusicPlaylistId(url: String): String? {
        val m = Pattern.compile("playlist/[^/]+/pl\\.([a-zA-Z0-9.\\-]+)").matcher(url)
        return if (m.find()) "pl.${m.group(1)}" else null
    }

    private fun extractAppleMusicTitle(html: String): String {
        // Try the Open Graph og:title first, then the schema:name field,
        // then the JSON-LD name field.
        val og = Pattern.compile("<meta[^>]+property=\"og:title\"[^>]+content=\"([^\"]+)\"").matcher(html)
        if (og.find()) return unescapeJson(og.group(1))
        val schema = Pattern.compile("\"@type\":\"MusicPlaylist\"[^}]*?\"name\":\"([^\"]+)\"").matcher(html)
        if (schema.find()) return unescapeJson(schema.group(1))
        val ld = Pattern.compile("\\{\"@type\":\"MusicPlaylist\",\"name\":\"([^\"]+)\"").matcher(html)
        if (ld.find()) return unescapeJson(ld.group(1))
        return "Apple Music Playlist"
    }

    private fun parseAppleMusicTracks(html: String): List<ForeignTrack> {
        val tracks = mutableListOf<ForeignTrack>()

        // Strategy 1: the schema:music-playlist JSON blob — most reliable.
        // Each track looks like {"name":"...","artistName":"..."}.
        val schemaRegex = Pattern.compile("\\{\"name\":\"([^\"]{2,200})\",\"artistName\":\"([^\"]{2,200})\"")
        val sm = schemaRegex.matcher(html)
        while (sm.find()) {
            tracks.add(ForeignTrack(
                title = unescapeJson(sm.group(1)),
                artist = unescapeJson(sm.group(2)),
            ))
        }
        if (tracks.isNotEmpty()) {
            return tracks.distinctBy { it.title to it.artist }
        }

        // Strategy 2: JSON-LD MusicRecording entries.
        val itemRegex = Pattern.compile("\\{\"@type\":\"MusicRecording\",\"name\":\"([^\"]+)\"[^}]*?(?:\"byArtist\":\\{\"@type\":\"MusicGroup\",\"name\":\"([^\"]+)\"\\})?")
        val m = itemRegex.matcher(html)
        while (m.find()) {
            val title = unescapeJson(m.group(1) ?: continue)
            val artist = m.group(2)?.let { unescapeJson(it) }
            tracks.add(ForeignTrack(title = title, artist = artist))
        }
        // Fallback: scrape from the simpler "track-list" serialization
        // Apple Music sometimes uses for short playlists.
        if (tracks.isEmpty()) {
            val fallback = Pattern.compile("\"name\":\"([^\"]{2,80})\"[^}]{0,400}?\"artistName\":\"([^\"]+)\"")
            val fm = fallback.matcher(html)
            while (fm.find()) {
                tracks.add(ForeignTrack(
                    title = unescapeJson(fm.group(1)),
                    artist = unescapeJson(fm.group(2)),
                ))
            }
        }
        return tracks.distinctBy { it.title to it.artist }
    }

    // ─── Amazon Music ─────────────────────────────────────────────────────
    // URL pattern: https://music.amazon.com/{cc}/playlists/{id}
    // Amazon Music's page is JS-rendered, but the initial HTML includes a
    // `<script>` with a `musicPluginProps` JSON blob containing the track
    // list. We extract that.
    private suspend fun fetchAmazonMusicPlaylist(url: String): ResolvedImport {
        val html = fetchText(url)
        val id = extractAmazonPlaylistId(url) ?: url
        val title = extractAmazonMusicTitle(html)
        val tracks = parseAmazonMusicTracks(html)
        return ResolvedImport(
            source = ImportSource.AMAZON_MUSIC,
            sourcePlaylistId = id,
            title = title,
            thumbnailUrl = null,
            tracks = tracks,
        )
    }

    private fun extractAmazonPlaylistId(url: String): String? {
        val m = Pattern.compile("playlists/([A-Z0-9]+)").matcher(url)
        return if (m.find()) m.group(1) else null
    }

    private fun extractAmazonMusicTitle(html: String): String {
        val og = Pattern.compile("<meta[^>]+property=\"og:title\"[^>]+content=\"([^\"]+)\"").matcher(html)
        if (og.find()) return unescapeJson(og.group(1))
        val title = Pattern.compile("<title>([^<]+)</title>").matcher(html)
        if (title.find()) {
            val raw = title.group(1).trim()
            // Amazon Music titles look like "Playlist Name | Amazon Music"
            return raw.substringBefore(" |").ifBlank { raw }
        }
        return "Amazon Music Playlist"
    }

    private fun parseAmazonMusicTracks(html: String): List<ForeignTrack> {
        val tracks = mutableListOf<ForeignTrack>()
        // Amazon Music embeds track data as `{"title":"...","artist":"..."}`
        // inside the musicPluginProps blob. The exact key names have varied
        // across redesigns so we keep the regex tolerant.
        val regex = Pattern.compile("\\{\"title\":\"([^\"]{2,120})\",\"artist\":\"([^\"]+)\"")
        val m = regex.matcher(html)
        while (m.find()) {
            tracks.add(ForeignTrack(
                title = unescapeJson(m.group(1)),
                artist = unescapeJson(m.group(2)),
            ))
        }
        // Fallback: `{"trackName":"...","artistName":"..."}`
        if (tracks.isEmpty()) {
            val alt = Pattern.compile("\\{\"trackName\":\"([^\"]{2,120})\",\"artistName\":\"([^\"]+)\"")
            val am = alt.matcher(html)
            while (am.find()) {
                tracks.add(ForeignTrack(
                    title = unescapeJson(am.group(1)),
                    artist = unescapeJson(am.group(2)),
                ))
            }
        }
        return tracks.distinctBy { it.title to it.artist }
    }

    // ─── Tidal ────────────────────────────────────────────────────────────
    // URL pattern: https://tidal.com/browse/playlist/{uuid}
    // Tidal's public page embeds a Next.js __NEXT_DATA__ JSON blob that
    // includes the track list. We extract from there.
    private suspend fun fetchTidalPlaylist(url: String): ResolvedImport {
        val html = fetchText(url)
        val id = extractTidalPlaylistId(url) ?: url
        val title = extractTidalTitle(html)
        val tracks = parseTidalTracks(html)
        return ResolvedImport(
            source = ImportSource.TIDAL,
            sourcePlaylistId = id,
            title = title,
            thumbnailUrl = null,
            tracks = tracks,
        )
    }

    private fun extractTidalPlaylistId(url: String): String? {
        // Tidal ids are UUIDs.
        val m = Pattern.compile("playlist/([a-f0-9\\-]{20,40})").matcher(url)
        return if (m.find()) m.group(1) else null
    }

    private fun extractTidalTitle(html: String): String {
        val og = Pattern.compile("<meta[^>]+property=\"og:title\"[^>]+content=\"([^\"]+)\"").matcher(html)
        if (og.find()) return unescapeJson(og.group(1))
        val title = Pattern.compile("<title>([^<]+)</title>").matcher(html)
        if (title.find()) {
            val raw = title.group(1).trim()
            return raw.substringBefore(" |").ifBlank { raw }
        }
        return "Tidal Playlist"
    }

    private fun parseTidalTracks(html: String): List<ForeignTrack> {
        val tracks = mutableListOf<ForeignTrack>()
        // Tidal embeds tracks as `{"title":"...","artists":[{"name":"..."}]}`
        val regex = Pattern.compile("\\{\"title\":\"([^\"]{2,120})\",\"artists\":\\[\\{\"name\":\"([^\"]+)\"")
        val m = regex.matcher(html)
        while (m.find()) {
            tracks.add(ForeignTrack(
                title = unescapeJson(m.group(1)),
                artist = unescapeJson(m.group(2)),
            ))
        }
        // Fallback: search for `{"name":"...","artist":"..."}` pairs (older
        // Tidal serializations).
        if (tracks.isEmpty()) {
            val alt = Pattern.compile("\\{\"name\":\"([^\"]{2,120})\",\"artist\":\"([^\"]+)\"")
            val am = alt.matcher(html)
            while (am.find()) {
                tracks.add(ForeignTrack(
                    title = unescapeJson(am.group(1)),
                    artist = unescapeJson(am.group(2)),
                ))
            }
        }
        return tracks.distinctBy { it.title to it.artist }
    }

    // ─── Deezer ───────────────────────────────────────────────────────────
    // URL pattern: https://www.deezer.com/{cc}/playlist/{numeric-id}
    // Deezer exposes a public JSON API: `https://api.deezer.com/playlist/{id}`.
    // Returns `{title, picture_xl, tracks: {data: [{title, artist: {name}}]}}`.
    private suspend fun fetchDeezerPlaylist(url: String): ResolvedImport {
        val id = extractDeezerPlaylistId(url) ?: error("Couldn't extract Deezer playlist id from URL")
        val json = fetchText("https://api.deezer.com/playlist/$id")
        val root = JSONObject(json)
        val title = root.optString("title").ifBlank { "Deezer Playlist" }
        val thumb = root.optString("picture_xl").ifBlank { null }
        val tracksArr = root.optJSONObject("tracks")?.optJSONArray("data") ?: return ResolvedImport(
            source = ImportSource.DEEZER,
            sourcePlaylistId = id,
            title = title,
            thumbnailUrl = thumb,
            tracks = emptyList(),
        )
        val tracks = (0 until tracksArr.length()).mapNotNull { i ->
            val obj = tracksArr.optJSONObject(i) ?: return@mapNotNull null
            val t = obj.optString("title").ifBlank { return@mapNotNull null }
            val a = obj.optJSONObject("artist")?.optString("name")?.ifBlank { null }
            ForeignTrack(title = t, artist = a)
        }
        return ResolvedImport(
            source = ImportSource.DEEZER,
            sourcePlaylistId = id,
            title = title,
            thumbnailUrl = thumb,
            tracks = tracks,
        )
    }

    private fun extractDeezerPlaylistId(url: String): String? {
        val m = Pattern.compile("playlist/(\\d+)").matcher(url)
        return if (m.find()) m.group(1) else null
    }

    // ─── Shared helpers ───────────────────────────────────────────────────
    private suspend fun fetchText(url: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; ArchiveTune) AppleWebKit/537.36")
            .header("Accept", "text/html,application/json,application/xhtml+xml")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) error("HTTP ${res.code} fetching $url")
            // Redirects are followed, so re-validate where we actually landed:
            // a trusted host must not be able to bounce us to an arbitrary one.
            if (detectSource(res.request.url.toString()) == ImportSource.UNKNOWN) {
                error("Playlist URL redirected to an untrusted host")
            }
            // Playlist pages are HTML/JSON; cap the body so a hostile or
            // mis-routed response can't stream until the app runs out of memory.
            // peekBody reads at most MAX_RESPONSE_BYTES and truncates beyond it.
            res.peekBody(MAX_RESPONSE_BYTES).string()
                .ifBlank { error("Empty response from $url") }
        }
    }

    private fun extractQuery(url: String, key: String): String? {
        val m = Pattern.compile("[?&]$key=([^&]+)").matcher(url)
        return if (m.find()) java.net.URLDecoder.decode(m.group(1), "UTF-8") else null
    }

    private fun extractFromTag(html: String, tag: String): String? {
        val idx = html.indexOf(tag)
        if (idx < 0) return null
        val after = html.substring(idx + tag.length)
        return after
    }

    private fun unescapeJson(s: String): String =
        s.replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\n", " ")
            .replace("\\/", "/")
            .replace("&amp;", "&")
            .replace("&#x27;", "'")
            .replace("&#39;", "'")
            .replace("&quot;", "\"")
            .replace("&#x2F;", "/")

    private const val MAX_PARALLEL_SEARCHES = 6

    /** Upper bound on a fetched playlist page. Real pages are well under 8 MB. */
    private const val MAX_RESPONSE_BYTES = 8L * 1024 * 1024
}
