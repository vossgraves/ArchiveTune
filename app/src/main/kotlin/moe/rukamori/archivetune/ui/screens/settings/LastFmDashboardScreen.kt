/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.RepeatableSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Queue
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.DarkModeKey
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.lastfm.CatalogueCoverProvider
import moe.rukamori.archivetune.lastfm.LastFM
import moe.rukamori.archivetune.lastfm.LastFmArtworkNormalizer
import moe.rukamori.archivetune.lastfm.models.RecentTrack
import moe.rukamori.archivetune.lastfm.models.TopAlbum
import moe.rukamori.archivetune.lastfm.models.TopAlbumsResponse
import moe.rukamori.archivetune.lastfm.models.TopArtist
import moe.rukamori.archivetune.lastfm.models.TopArtistsResponse
import moe.rukamori.archivetune.lastfm.models.TopTrack
import moe.rukamori.archivetune.lastfm.models.TopTracksResponse
import moe.rukamori.archivetune.lastfm.models.UserImage
import moe.rukamori.archivetune.lastfm.models.UserInfo
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.scrobbling.LastFmSettingsRepository
import moe.rukamori.archivetune.telegram.TelegramCoverProvider
import moe.rukamori.archivetune.ui.component.IconButton as AppIconButton
import moe.rukamori.archivetune.ui.menu.AddToPlaylistDialog
import moe.rukamori.archivetune.ui.screens.Screens
import moe.rukamori.archivetune.ui.utils.YTThumbQuality
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.ui.utils.buildYTThumbnailUrl
import moe.rukamori.archivetune.utils.rememberEnumPreference
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

private val DashboardAccentColor = Color(0xFFBE123C)

@Composable
private fun isDashboardDarkTheme(): Boolean {
    val darkMode by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
    return if (darkMode == DarkMode.AUTO) isSystemInDarkTheme() else darkMode == DarkMode.ON
}

@Composable
private fun dashboardCardColor(): Color =
    if (isDashboardDarkTheme()) Color(0xFF1F1416) else Color(0xFFFFF5F5)

@Composable
private fun dashboardIconBackgroundColor(): Color =
    if (isDashboardDarkTheme()) Color(0xFF3A1F23) else Color(0xFFFFE4E6)

private enum class LastFmFilter { RECENT, TOP_TRACKS, TOP_ARTISTS, TOP_ALBUMS }

/**
 * Process-wide cache of YouTube search results keyed by "<title>::<artist>".
 * Last.fm tracks don't carry YouTube video IDs, so every playback action on a
 * Last.fm track needs to round-trip through a YouTube song search first. We
 * cache the (nullable) result per (title, artist) tuple so that re-opening the
 * overflow sheet on the same track doesn't re-search, and so that switching
 * between actions (Start Mix → Play next → Add to queue) on the same track
 * reuses the same resolved SongItem. Cache is process-scoped: cleared on app
 * restart, but never grows unboundedly (the keyspace is bounded by the user's
 * listening history, which is itself bounded by the dashboard's track list).
 */
private val ytSearchCache = ConcurrentHashMap<String, SongItem?>()

/**
 * Searches YouTube Music for a Last.fm track by "<title> <artist>" query and
 * returns the first SongItem result, or null if no match. Results are cached
 * in [ytSearchCache] to avoid re-searching for the same track.
 *
 * Mirrors the onPlayFromSource pattern in PlayerMenu.kt: non-YT track
 * metadata has no YouTube-side video id, so to play / queue / mix a Last.fm
 * track we need to resolve it through YouTube Music search first.
 */
private suspend fun searchYtForLastFmTrack(title: String, artist: String?): SongItem? {
    if (title.isBlank()) return null
    val cacheKey = "${title.trim().lowercase()}::${artist?.trim()?.lowercase().orEmpty()}"
    ytSearchCache[cacheKey]?.let { return it }
    val term = listOfNotNull(artist?.takeIf(String::isNotBlank), title).joinToString(" ")
    val result = YouTube.search(term, YouTube.SearchFilter.FILTER_SONG).getOrNull()
    val first = result?.items?.firstOrNull { it is SongItem } as? SongItem
    ytSearchCache[cacheKey] = first
    return first
}

/**
 * Lightweight track reference carried across the bottom-sheet boundary. Both
 * [RecentTrack] and [TopTrack] collapse to the same shape once they reach the
 * overflow sheet — the only fields the sheet cares about are title, artist,
 * the Last.fm web URL (for "Open in Last.fm"), the Last.fm image array (for
 * the album thumbnail), an optional play count (for top tracks), and the
 * now-playing flag (for recents). Wrapping both model types in a single
 * data class keeps the sheet's API stable regardless of which list the user
 * opened it from.
 */
private data class LastFmTrackRef(
    val title: String,
    val artist: String?,
    val url: String?,
    val image: List<UserImage>?,
    val playCount: Int?,
    val isNowPlaying: Boolean,
) {
    fun artworkKey(): String = "${title.trim().lowercase()}::${artist.orEmpty().trim().lowercase()}"
}

private fun RecentTrack.toRef(): LastFmTrackRef = LastFmTrackRef(
    title = name.orEmpty(),
    artist = artist?.text,
    url = url,
    image = image,
    playCount = null,
    isNowPlaying = isNowPlaying,
)

private fun TopTrack.toRef(): LastFmTrackRef = LastFmTrackRef(
    title = name.orEmpty(),
    artist = artist?.text,
    url = url,
    image = image,
    playCount = playcount,
    isNowPlaying = false,
)

@Composable
fun LastFmDashboardScreen(
    navController: NavController,
    repository: LastFmSettingsRepository = hiltViewModel<LastFmDashboardViewModel>().repository,
) {
    val settings by repository.observeSettings().collectAsStateWithLifecycle(initialValue = null)
    val current = settings
    val isLoggedIn = current?.isLoggedIn == true

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current

    var userInfo by remember { mutableStateOf<Result<UserInfo>?>(null) }
    var recentTracks by remember { mutableStateOf<Result<List<RecentTrack>>?>(null) }
    // Full TopX*Response wrappers are kept (rather than unwrapping to bare
    // lists at fetch time) so the dashboard can read both the page list
    // (.toptracks.track / .topartists.artist / .topalbums.album) AND the
    // canonical count of unique items ever scrobbled (.toptracks.attr.total /
    // .topartists.attr.total / .topalbums.attr.total) from a single piece of
    // state. The previous implementation unwrapped immediately and lost the
    // .attr.total, so the hero stat pills had to fall back to the page size
    // (always 20). Storing the wrapper keeps the canonical counts available
    // for the stat pills while still letting the track list call sites unwrap
    // once at the read site.
    var topTracks by remember { mutableStateOf<Result<TopTracksResponse>?>(null) }
    var topArtists by remember { mutableStateOf<Result<TopArtistsResponse>?>(null) }
    var topAlbums by remember { mutableStateOf<Result<TopAlbumsResponse>?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf(LastFmFilter.RECENT) }
    var overflowTrack by remember { mutableStateOf<LastFmTrackRef?>(null) }
    var showAddToPlaylist by remember { mutableStateOf(false) }

    fun refresh() {
        val username = current?.username?.takeIf { it.isNotBlank() } ?: return
        if (!isLoggedIn) return
        scope.launch {
            isRefreshing = true
            try {
                current.serviceConfig.apply(sessionKey = current.sessionKey)
                // Five parallel Last.fm calls — user.getInfo for the header / hero
                // card, plus the four lists the dashboard cycles through via the
                // filter dropdown. Fetched once on refresh (and on re-login) so
                // switching filters is instant; the lists are small (limit = 20)
                // so this is a single round-trip's worth of bandwidth.
                val infoResult = LastFM.getUserInfo(username)
                val recentResult = LastFM.getRecentTracks(username, limit = 20)
                val topTracksResult = LastFM.getTopTracks(username, period = "overall", limit = 20)
                val topArtistsResult = LastFM.getTopArtists(username, period = "overall", limit = 20)
                val topAlbumsResult = LastFM.getTopAlbums(username, period = "overall", limit = 20)
                withContext(Dispatchers.Default) {
                    userInfo = infoResult
                    recentTracks = recentResult.map { it.recenttracks.track }
                    // Keep the wrappers intact (see the var declaration comment
                    // for why) — the read site unwraps to the page list, and the
                    // hero stat pills read .toptracks.attr.total etc. directly
                    // off the same state. (Previous implementation unwrapped
                    // here, which dropped the attr.)
                    topTracks = topTracksResult
                    topArtists = topArtistsResult
                    topAlbums = topAlbumsResult
                }
            } finally {
                isRefreshing = false
            }
        }
    }

    LaunchedEffect(isLoggedIn, current?.username) {
        if (isLoggedIn) refresh()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LastFmDashboardHeader(
                userInfo = userInfo,
                isRefreshing = isRefreshing,
                onRefresh = { if (!isRefreshing) refresh() },
                onBack = navController::navigateUp,
                onBackLong = navController::backToMain,
                onExplore = { navController.navigate(Screens.MoodAndGenres.route) },
                onSearch = { navController.navigate(Screens.Search.route) },
                onAvatar = { navController.navigate(Screens.MoodAndGenres.route) },
            )
        },
    ) { innerPadding ->
        if (current == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (!isLoggedIn) {
            NotSignedIn(
                onSignIn = { navController.navigate("settings/lastfm") },
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
            return@Scaffold
        }

        val recent = remember(recentTracks) {
            recentTracks?.getOrNull().orEmpty().dedupeNowPlayingEchoes()
        }
        // Unwrap the page list off the stored wrapper (state still holds the
        // wrapper so the hero stat pills can read .attr.total off the same
        // source — see the var declaration comment).
        val top = topTracks?.getOrNull()?.toptracks?.track.orEmpty()
        val artists = topArtists?.getOrNull()?.topartists?.artist.orEmpty()
        val albums = topAlbums?.getOrNull()?.topalbums?.album.orEmpty()
        val recentArtworkByTrack = remember(recent) { recent.associateArtworkByTrack() }

        val seedMap = remember(allTracksForArtworkSeedKey(recent, top)) {
            val snapshot = HashMap<String, String>()
            for (lookup in buildAllArtworkLookups(recent, top)) {
                CachedArtworkStore.get(lookup.key)?.let { snapshot[lookup.key] = it }
            }
            snapshot
        }
        var catalogueArtworkByTrack by remember { mutableStateOf<Map<String, String>>(seedMap) }

        val allTracksForArtwork = remember(recent, top) {
            buildAllArtworkLookups(recent, top)
        }

        LaunchedEffect(allTracksForArtwork) {
            if (allTracksForArtwork.isEmpty()) return@LaunchedEffect
            val snapshot = HashMap<String, String>(catalogueArtworkByTrack)
            val chunks = allTracksForArtwork.chunked(LASTFM_ARTWORK_CONCURRENCY)
            for (chunk in chunks) {
                val toResolve = chunk.filter { lookup -> snapshot[lookup.key].isNullOrBlank() }
                if (toResolve.isEmpty()) continue
                val resolved = withContext(Dispatchers.IO) {
                    toResolve
                        .map { lookup ->
                            async(Dispatchers.IO) {
                                val url = resolveCatalogueCover(lookup)
                                if (url != null) {
                                    CachedArtworkStore.put(lookup.key, url)
                                    lookup.key to url
                                } else {
                                    null
                                }
                            }
                        }
                        .awaitAll()
                        .filterNotNull()
                }
                if (resolved.isEmpty()) continue
                resolved.forEach { (k, u) -> snapshot[k] = u }
                catalogueArtworkByTrack = snapshot.toMap()
            }
        }

        val playerAwareInsets = LocalPlayerAwareWindowInsets.current
        val density = LocalDensity.current
        val bottomInsetDp = with(density) { playerAwareInsets.getBottom(density).toDp() }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    playerAwareInsets.only(WindowInsetsSides.Horizontal),
                ),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = bottomInsetDp + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "header_pills") {
                HeaderPillRow(
                    userInfo = userInfo,
                    username = current.username,
                )
            }

            item(key = "hero_card") {
                HeroStatsCard(
                    userInfo = userInfo,
                    username = current.username,
                    isRefreshing = isRefreshing,
                    trackCount = topTracks?.getOrNull()?.toptracks?.attr?.total?.toIntOrNull() ?: 0,
                    artistCount = topArtists?.getOrNull()?.topartists?.attr?.total?.toIntOrNull() ?: 0,
                    albumCount = topAlbums?.getOrNull()?.topalbums?.attr?.total?.toIntOrNull() ?: 0,
                    onRetry = ::refresh,
                    onOpenGenres = { navController.navigate(Screens.MoodAndGenres.route) },
                )
            }

            item(key = "filter_header") {
                FilterHeader(
                    selectedFilter = selectedFilter,
                    onSelect = { selectedFilter = it },
                )
            }

            when (selectedFilter) {
                LastFmFilter.RECENT -> {
                    if (recent.isEmpty() && recentTracks != null && !isRefreshing) {
                        item(key = "recent_empty") { EmptyHint(text = stringResource(R.string.lastfm_no_recent_tracks)) }
                    } else {
                        items(
                            recent,
                            key = { "recent_${it.name}_${it.date?.uts ?: it.attr?.nowplaying ?: ""}" },
                        ) { track ->
                            DashboardTrackRow(
                                track = track.toRef(),
                                fallbackArtworkUrl = recentArtworkByTrack[track.trackArtworkKey()]
                                    ?: catalogueArtworkByTrack[track.trackArtworkKey()],
                                onOverflow = { overflowTrack = track.toRef() },
                            )
                        }
                    }
                }
                LastFmFilter.TOP_TRACKS -> {
                    if (top.isEmpty() && topTracks != null && !isRefreshing) {
                        item(key = "top_empty") { EmptyHint(text = stringResource(R.string.lastfm_no_top_tracks)) }
                    } else {
                        items(
                            top.withIndex().toList(),
                            key = { "top_${it.index}_${it.value.name}" },
                        ) { (index, track) ->
                            DashboardTrackRow(
                                track = track.toRef(),
                                rank = index + 1,
                                fallbackArtworkUrl = recentArtworkByTrack[track.trackArtworkKey()]
                                    ?: catalogueArtworkByTrack[track.trackArtworkKey()],
                                onOverflow = { overflowTrack = track.toRef() },
                            )
                        }
                    }
                }
                LastFmFilter.TOP_ARTISTS -> {
                    if (artists.isEmpty() && topArtists != null && !isRefreshing) {
                        item(key = "artists_empty") { EmptyHint(text = stringResource(R.string.lastfm_no_top_tracks)) }
                    } else {
                        items(
                            artists.withIndex().toList(),
                            key = { "artist_${it.index}_${it.value.name}" },
                        ) { (index, artist) ->
                            DashboardArtistRow(
                                name = artist.name.orEmpty(),
                                playCount = artist.playcount,
                                rank = index + 1,
                                artworkUrl = bestArtwork(artist.image),
                            )
                        }
                    }
                }
                LastFmFilter.TOP_ALBUMS -> {
                    if (albums.isEmpty() && topAlbums != null && !isRefreshing) {
                        item(key = "albums_empty") { EmptyHint(text = stringResource(R.string.lastfm_no_top_tracks)) }
                    } else {
                        items(
                            albums.withIndex().toList(),
                            key = { "album_${it.index}_${it.value.name}_${it.value.artist?.text ?: ""}" },
                        ) { (index, album) ->
                            DashboardAlbumRow(
                                title = album.name.orEmpty(),
                                artist = album.artist?.text,
                                playCount = album.playcount,
                                rank = index + 1,
                                artworkUrl = bestArtwork(album.image),
                            )
                        }
                    }
                }
            }
        }

        overflowTrack?.let { track ->
            TrackOverflowSheet(
                track = track,
                onDismiss = { overflowTrack = null },
                onOpenGenres = { navController.navigate(Screens.MoodAndGenres.route) },
                onAddToPlaylist = { showAddToPlaylist = true },
            )
        }

        if (showAddToPlaylist && overflowTrack != null) {
            val track = overflowTrack!!
            AddToPlaylistDialog(
                isVisible = true,
                onGetSong = {
                    // AddToPlaylistDialog runs onGetSong on a background
                    // coroutine; searchYtForLastFmTrack already dispatches
                    // to IO internally. We return the YouTube videoId of the
                    // first SongItem match so the dialog can write it into
                    // the picked playlist's playlist_map / playlist_song
                    // join tables via the standard LocalDatabase path.
                    val song = searchYtForLastFmTrack(track.title, track.artist)
                    listOfNotNull(song?.id)
                },
                onDismiss = { showAddToPlaylist = false },
                onAddComplete = { _, _ ->
                    showAddToPlaylist = false
                    overflowTrack = null
                },
            )
        }
    }
}

// ── Header ────────────────────────────────────────────────────────────────────

@Composable
private fun LastFmDashboardHeader(
    userInfo: Result<UserInfo>?,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onBackLong: () -> Unit,
    onExplore: () -> Unit,
    onSearch: () -> Unit,
    onAvatar: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                )
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIconButton(
                onClick = onBack,
                onLongClick = onBackLong,
            ) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back),
                    contentDescription = stringResource(R.string.back_button_desc),
                )
            }
            Text(
                text = "Last.fm",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
            // Refresh icon rotates while a fetch is in flight, same as the
            // previous LargeFlexibleTopAppBar implementation — just moved into
            // a circular IconButton to match the LastWave-native action set
            // (explore + search + avatar). The rotation tween is preserved
            // verbatim so the spin/snap transition behaviour is unchanged.
            val rotation by animateFloatAsState(
                targetValue = if (isRefreshing) 360f else 0f,
                animationSpec = if (isRefreshing) {
                    RepeatableSpec(
                        iterations = Int.MAX_VALUE,
                        animation = tween(durationMillis = 1000),
                    )
                } else {
                    tween(durationMillis = 300)
                },
                label = "lastfm_refresh_rotation",
            )
            IconButton(
                onClick = onRefresh,
                enabled = !isRefreshing,
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.lastfm_refresh),
                    modifier = Modifier.graphicsLayer { rotationZ = rotation },
                )
            }
            IconButton(
                onClick = onExplore,
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Icon(
                    imageVector = Icons.Filled.Explore,
                    contentDescription = stringResource(R.string.mood_and_genres),
                )
            }
            IconButton(
                onClick = onSearch,
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = stringResource(R.string.search),
                )
            }
            // 40dp circular Last.fm avatar — taps navigate to mood_and_genres
            // (same target as the explore button, mirroring LastWave-native's
            // ProfileAvatar which opens the discover view). When no avatar URL
            // is available (not logged in / image array empty), falls back to
            // AccountCircle so the header still reads as a tappable profile
            // afford.
            IconButton(
                onClick = onAvatar,
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                val info = userInfo?.getOrNull()
                val avatar = info?.let { LastFmArtworkNormalizer.bestImageUrl(it.image) }
                if (!avatar.isNullOrBlank()) {
                    AsyncImage(
                        model = avatar,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
        }
    }
}

// ── Header pill row ──────────────────────────────────────────────────────────

/**
 * The free-floating row of pills below the header — a rounded username pill
 * on the left, a scrobble-count pill on the right. Ported from
 * LastWave-native's HeaderRow (the username half) plus the existing
 * lastfm_playcount scrobble total (the right half, where LastWave-native
 * shows a live listen timer — Last.fm doesn't expose one, so we show the
 * lifetime scrobble count instead, which is the most Last.fm-equivalent
 * single-number stat).
 */
@Composable
private fun HeaderPillRow(
    userInfo: Result<UserInfo>?,
    username: String,
) {
    val info = userInfo?.getOrNull()
    val scrobbles = info?.playcount ?: 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.AccountCircle,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = username.ifBlank { "—" },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.stats),
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = DashboardAccentColor,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = formatCount(scrobbles.toLong()),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

// ── Hero stats card ──────────────────────────────────────────────────────────

/**
 * Large rounded stats card replacing the previous flat UserCard. Ported
 * from LastWave-native's StatsCard composable (HomeScreen.kt lines 524-599):
 *   - surfaceContainerHigh outer card
 *   - primaryContainer inner hero area with the formatted scrobbles count
 *   - a 46dp primary Surface with a forward-arrow IconButton on the right
 *     of the hero (opens mood_and_genres — LastWave-native's "Genres" target)
 *   - three StatPills below for Tracks / Artists / Albums
 *
 * Below the card: a compact avatar + username + realname row (LastWave-native
 * puts this in the header; ArchiveTune's dashboard keeps it in the body to
 * stay consistent with the previous design and to keep the header compact).
 */
@Composable
private fun HeroStatsCard(
    userInfo: Result<UserInfo>?,
    username: String,
    isRefreshing: Boolean,
    trackCount: Int,
    artistCount: Int,
    albumCount: Int,
    onRetry: () -> Unit,
    onOpenGenres: () -> Unit,
) {
    when {
        userInfo == null && isRefreshing -> {
            Box(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = DashboardAccentColor) }
        }
        userInfo?.isSuccess == true -> {
            val info = userInfo.getOrNull()!!
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 2.dp,
                    shadowElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 16.dp),
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.align(Alignment.Center),
                                ) {
                                    Text(
                                        text = formatCount((info.playcount ?: 0).toLong()),
                                        style = MaterialTheme.typography.displaySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                    Text(
                                        text = stringResource(R.string.lastfm_scrobbles),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .size(46.dp)
                                        .align(Alignment.CenterEnd),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        IconButton(onClick = onOpenGenres) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                                contentDescription = stringResource(R.string.mood_and_genres),
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            StatPill(
                                label = stringResource(R.string.lastfm_filter_top_tracks),
                                value = formatCount(trackCount.toLong()),
                                modifier = Modifier.weight(1f),
                            )
                            StatPill(
                                label = stringResource(R.string.lastfm_filter_top_artists),
                                value = formatCount(artistCount.toLong()),
                                modifier = Modifier.weight(1f),
                            )
                            StatPill(
                                label = stringResource(R.string.lastfm_filter_top_albums),
                                value = formatCount(albumCount.toLong()),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Compact avatar + username + realname row.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val avatar = LastFmArtworkNormalizer.bestImageUrl(info.image)
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = dashboardIconBackgroundColor(),
                    ) {
                        if (!avatar.isNullOrBlank()) {
                            AsyncImage(
                                model = avatar,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(R.drawable.account),
                                    contentDescription = null,
                                    tint = DashboardAccentColor,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = info.realname?.takeIf { it.isNotBlank() } ?: info.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "@${info.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        else -> {
            // Fallback card shown when user.getInfo failed. Tapping retries.
            Surface(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(20.dp),
                color = dashboardCardColor(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        color = dashboardIconBackgroundColor(),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.account),
                                contentDescription = null,
                                tint = DashboardAccentColor,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    Spacer(Modifier.size(12.dp))
                    Text(
                        text = "@$username",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

/**
 * Formats a count with thousands separators (e.g., 6328 → "6,328").
 * Ported from LastWave-native's formatCount helper.
 */
private fun formatCount(count: Long): String = "%,d".format(count)

/**
 * Small stat pill used in the hero stats card. Ported from LastWave-native's StatPill.
 */
@Composable
private fun StatPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
    ) {
        Column(
            Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Filter header ─────────────────────────────────────────────────────────────

/**
 * "List" title + a sort pill on the right that opens a [DropdownMenu] for
 * picking the filter mode (Recent / Top Tracks / Top Artists / Top Albums).
 * Ported from LastWave-native's MixHeader.
 */
@Composable
private fun FilterHeader(
    selectedFilter: LastFmFilter,
    onSelect: (LastFmFilter) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.lastfm_list),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterVertically),
        )
        Box {
            Surface(
                onClick = { menuOpen = true },
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                tonalElevation = 1.dp,
                modifier = Modifier.heightIn(min = 34.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = iconForFilter(selectedFilter),
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = labelForFilter(selectedFilter),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Filled.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                shape = RoundedCornerShape(24.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 3.dp,
                shadowElevation = 3.dp,
                modifier = Modifier.padding(vertical = 4.dp),
            ) {
                FilterOption(
                    icon = Icons.Filled.Refresh,
                    label = stringResource(R.string.lastfm_filter_recent),
                    active = selectedFilter == LastFmFilter.RECENT,
                    onClick = { onSelect(LastFmFilter.RECENT); menuOpen = false },
                )
                FilterOption(
                    icon = Icons.Filled.MusicNote,
                    label = stringResource(R.string.lastfm_filter_top_tracks),
                    active = selectedFilter == LastFmFilter.TOP_TRACKS,
                    onClick = { onSelect(LastFmFilter.TOP_TRACKS); menuOpen = false },
                )
                FilterOption(
                    icon = Icons.Filled.Person,
                    label = stringResource(R.string.lastfm_filter_top_artists),
                    active = selectedFilter == LastFmFilter.TOP_ARTISTS,
                    onClick = { onSelect(LastFmFilter.TOP_ARTISTS); menuOpen = false },
                )
                FilterOption(
                    icon = Icons.Filled.Album,
                    label = stringResource(R.string.lastfm_filter_top_albums),
                    active = selectedFilter == LastFmFilter.TOP_ALBUMS,
                    onClick = { onSelect(LastFmFilter.TOP_ALBUMS); menuOpen = false },
                )
            }
        }
    }
}

private fun iconForFilter(filter: LastFmFilter) = when (filter) {
    LastFmFilter.RECENT -> Icons.Filled.Refresh
    LastFmFilter.TOP_TRACKS -> Icons.Filled.MusicNote
    LastFmFilter.TOP_ARTISTS -> Icons.Filled.Person
    LastFmFilter.TOP_ALBUMS -> Icons.Filled.Album
}

@Composable
private fun labelForFilter(filter: LastFmFilter) = when (filter) {
    LastFmFilter.RECENT -> stringResource(R.string.lastfm_filter_recent)
    LastFmFilter.TOP_TRACKS -> stringResource(R.string.lastfm_filter_top_tracks)
    LastFmFilter.TOP_ARTISTS -> stringResource(R.string.lastfm_filter_top_artists)
    LastFmFilter.TOP_ALBUMS -> stringResource(R.string.lastfm_filter_top_albums)
}

@Composable
private fun FilterOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon = {
            if (active) {
                Icon(
                    painter = painterResource(R.drawable.check),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
        onClick = onClick,
        modifier = if (active) {
            Modifier
                .padding(horizontal = 6.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
        } else {
            Modifier.padding(horizontal = 6.dp)
        },
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
    )
}

// ── Track / artist / album rows ───────────────────────────────────────────────

/**
 * Track row ported from LastWave-native's TrackRow (HomeScreen.kt lines
 * 820-934). Differences:
 *   - Album artwork comes from [bestArtwork] / [resolveCatalogueCover]
 *     instead of LastWave-native's ArtworkImage (the catalog/cover
 *     resolution pipeline is what the existing ArchiveTune dashboard
 *     already had).
 *   - The now-playing pulse pill is the existing 1.0→1.06 / 1200ms /
 *     LinearEasing / Reverse animation, copied verbatim.
 *   - The play-count badge is a pill (e.g. "2×", "3×") — formatted
 *     inline as "${count}×" rather than via the existing plurals
 *     string, to match LastWave-native's visual.
 *   - Tapping the three-dot MoreHoriz button opens the [TrackOverflowSheet]
 *     by setting the [LastFmDashboardScreen]'s overflowTrack state — the
 *     sheet itself is rendered at the screen level so it can outlive the
 *     specific row that triggered it.
 */
@Composable
private fun DashboardTrackRow(
    track: LastFmTrackRef,
    rank: Int? = null,
    fallbackArtworkUrl: String? = null,
    onOverflow: () -> Unit,
) {
    val artworkUrl = bestArtwork(track.image) ?: fallbackArtworkUrl
    val isNowPlaying = track.isNowPlaying
    val secondaryTextColor =
        if (isNowPlaying) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
        else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = if (isNowPlaying) RoundedCornerShape(22.dp) else RoundedCornerShape(18.dp),
        color = if (isNowPlaying) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        tonalElevation = if (isNowPlaying) 1.dp else 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(vertical = 6.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                if (!artworkUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = artworkUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            if (rank != null) {
                Surface(
                    modifier = Modifier.size(28.dp),
                    shape = CircleShape,
                    color = dashboardIconBackgroundColor(),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = rank.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = DashboardAccentColor,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isNowPlaying) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = track.artist.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            if (isNowPlaying) {
                val infiniteTransition = rememberInfiniteTransition(label = "lastfm_now_playing_pulse")
                val pulseScale by infiniteTransition.animateFloat(
                    initialValue = 1.0f,
                    targetValue = 1.06f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "lastfm_pulse_scale",
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary,
                    tonalElevation = 4.dp,
                    shadowElevation = 2.dp,
                    modifier = Modifier.graphicsLayer {
                        scaleX = pulseScale
                        scaleY = pulseScale
                    },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Spacer(Modifier.size(6.dp).background(MaterialTheme.colorScheme.onPrimary, CircleShape))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.lastfm_now_playing),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            } else if (track.playCount != null && track.playCount > 0) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Text(
                        text = "${track.playCount}×",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            IconButton(onClick = onOverflow) {
                Icon(
                    imageVector = Icons.Filled.MoreHoriz,
                    contentDescription = "More",
                )
            }
        }
    }
}

/**
 * Simplified row for the Top Artists filter — name + playcount badge,
 * no track-specific actions (no overflow menu, since the bottom sheet's
 * actions are all track-focused: Start Mix / Play / Add to queue all
 * need a YouTube song id that an artist row can't supply).
 */
@Composable
private fun DashboardArtistRow(
    name: String,
    playCount: Int?,
    rank: Int,
    artworkUrl: String?,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(vertical = 6.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                if (!artworkUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = artworkUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Surface(
                modifier = Modifier.size(28.dp),
                shape = CircleShape,
                color = dashboardIconBackgroundColor(),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = rank.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = DashboardAccentColor,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = playCount?.let { stringResource(R.string.lastfm_playcount, it) }
                        ?: stringResource(R.string.lastfm_filter_top_artists),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            if (playCount != null && playCount > 0) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Text(
                        text = "${playCount}×",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

/**
 * Simplified row for the Top Albums filter — album name + artist + playcount
 * badge. Like the artist row, no overflow menu (the sheet is track-focused).
 */
@Composable
private fun DashboardAlbumRow(
    title: String,
    artist: String?,
    playCount: Int?,
    rank: Int,
    artworkUrl: String?,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(vertical = 6.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                if (!artworkUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = artworkUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Surface(
                modifier = Modifier.size(28.dp),
                shape = CircleShape,
                color = dashboardIconBackgroundColor(),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = rank.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = DashboardAccentColor,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = artist.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            if (playCount != null && playCount > 0) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Text(
                        text = "${playCount}×",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

// ── Track overflow bottom sheet ───────────────────────────────────────────────

/**
 * ModalBottomSheet shown when the user taps the three-dot overflow button on
 * a [DashboardTrackRow]. Renders, top to bottom:
 *   1. "Start Mix with this Song" banner (shuffle icon, primaryContainer bg)
 *      → searches YouTube for a matching song and seeds a radio queue
 *   2. "Genre: Unknown" → taps open mood_and_genres (we have no genre
 *      detection for Last.fm tracks, so always shows "Unknown")
 *   3. "Play in ArchiveTune" → searches YouTube, plays via YouTube radio
 *   4. "Play next" → searches YouTube, playerConnection.playNext
 *   5. "Add to queue" → searches YouTube, playerConnection.addToQueue
 *   6. "Add to playlist" → shows AddToPlaylistDialog with onGetSong doing
 *      the YouTube search and returning the song's video id
 *   7. "Open in Last.fm" → opens track.url in the browser
 *   8. "Copy Song" → copies "Artist - Title" to the clipboard
 *
 * For items 1, 3, 4, 5, 6 a YouTube search is needed to convert the Last.fm
 * track (title + artist, no YT video id) to a SongItem. Search results are
 * cached in [ytSearchCache] to avoid re-searching across actions on the same
 * track. While a search is in flight, the tapped action's leading icon is
 * swapped for a small CircularProgressIndicator and other actions are
 * disabled.
 */
@Composable
private fun TrackOverflowSheet(
    track: LastFmTrackRef,
    onDismiss: () -> Unit,
    onOpenGenres: () -> Unit,
    onAddToPlaylist: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    var loadingAction by remember { mutableStateOf<String?>(null) }

    fun runWithYtSearch(action: String, onFound: (SongItem) -> Unit) {
        if (loadingAction != null) return
        scope.launch {
            loadingAction = action
            try {
                val song = withContext(Dispatchers.IO) {
                    searchYtForLastFmTrack(track.title, track.artist)
                }
                if (song == null) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.lastfm_no_yt_match),
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    onFound(song)
                }
            } finally {
                loadingAction = null
                onDismiss()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        // ── 1. Start Mix banner ─────────────────────────────────────────
        // The banner is intentionally a clickable Surface (not a ListItem)
        // so it reads as a primary CTA — same visual hierarchy as
        // LastWave-native's hero "Start radio" banner.
        Surface(
            onClick = {
                runWithYtSearch("mix") { song ->
                    playerConnection?.playQueue(YouTubeQueue.radio(song.toMediaMetadata()))
                }
            },
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (loadingAction == "mix") {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Shuffle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.lastfm_start_mix),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = listOfNotNull(track.artist, track.title).joinToString(" - "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── 2. Genre ────────────────────────────────────────────────────
        ListItem(
            headlineContent = { Text(stringResource(R.string.lastfm_genre)) },
            supportingContent = { Text(stringResource(R.string.lastfm_unknown_genre)) },
            leadingContent = { Icon(Icons.Filled.Explore, contentDescription = null) },
            modifier = Modifier.clickable {
                onDismiss()
                onOpenGenres()
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        // ── 3. Play in ArchiveTune ─────────────────────────────────────
        OverflowActionItem(
            label = stringResource(R.string.lastfm_play_in_archivetune),
            icon = Icons.Filled.PlayArrow,
            loading = loadingAction == "play",
            enabled = loadingAction == null,
            onClick = {
                runWithYtSearch("play") { song ->
                    playerConnection?.playQueue(YouTubeQueue.radio(song.toMediaMetadata()))
                }
            },
        )

        // ── 4. Play next ───────────────────────────────────────────────
        OverflowActionItem(
            label = stringResource(R.string.lastfm_play_next),
            icon = Icons.Filled.PlayArrow,
            loading = loadingAction == "playNext",
            enabled = loadingAction == null,
            onClick = {
                runWithYtSearch("playNext") { song ->
                    playerConnection?.playNext(song.toMediaItem())
                }
            },
        )

        // ── 5. Add to queue ─────────────────────────────────────────────
        OverflowActionItem(
            label = stringResource(R.string.lastfm_add_to_queue),
            icon = Icons.Filled.Queue,
            loading = loadingAction == "addToQueue",
            enabled = loadingAction == null,
            onClick = {
                runWithYtSearch("addToQueue") { song ->
                    playerConnection?.addToQueue(song.toMediaItem())
                }
            },
        )

        // ── 6. Add to playlist ─────────────────────────────────────────
        OverflowActionItem(
            label = stringResource(R.string.lastfm_add_to_playlist),
            icon = Icons.Filled.PlaylistAdd,
            loading = false,
            enabled = loadingAction == null,
            onClick = {
                onDismiss()
                onAddToPlaylist()
            },
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        // ── 7. Open in Last.fm ─────────────────────────────────────────
        val lastFmUrl = track.url?.takeIf(String::isNotBlank)
        OverflowActionItem(
            label = stringResource(R.string.lastfm_open_in_lastfm),
            icon = Icons.Filled.OpenInBrowser,
            loading = false,
            enabled = loadingAction == null && !lastFmUrl.isNullOrBlank(),
            onClick = {
                onDismiss()
                lastFmUrl?.let {
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(it))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            },
        )

        // ── 8. Copy Song ───────────────────────────────────────────────
        OverflowActionItem(
            label = stringResource(R.string.lastfm_copy_song),
            icon = Icons.Filled.ContentCopy,
            loading = false,
            enabled = loadingAction == null,
            onClick = {
                val label = listOfNotNull(track.artist, track.title)
                    .joinToString(" - ")
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Last.fm Track", label))
                Toast.makeText(
                    context,
                    context.getString(R.string.lastfm_song_copied),
                    Toast.LENGTH_SHORT,
                ).show()
                onDismiss()
            },
        )

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun OverflowActionItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(icon, contentDescription = null)
            }
        },
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        ),
    )
}

// ── Sign-in / empty states ────────────────────────────────────────────────────

@Composable
private fun NotSignedIn(
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            modifier = Modifier.size(72.dp),
            shape = CircleShape,
            color = dashboardIconBackgroundColor(),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.stats),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = DashboardAccentColor,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.lastfm_sign_in_required),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.lastfm_sign_in_required_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        FilledTonalButton(onClick = onSignIn) {
            Text(stringResource(R.string.lastfm_sign_in_button))
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

// ── Artwork resolution (kept from previous implementation) ────────────────────

private fun bestArtwork(images: List<UserImage>?): String? =
    LastFmArtworkNormalizer.bestImageUrl(images)

private fun RecentTrack.trackArtworkKey(): String = "${name.orEmpty().trim().lowercase()}::${artist?.text.orEmpty().trim().lowercase()}"

private fun TopTrack.trackArtworkKey(): String = "${name.orEmpty().trim().lowercase()}::${artist?.text.orEmpty().trim().lowercase()}"

private fun List<RecentTrack>.associateArtworkByTrack(): Map<String, String> =
    mapNotNull { track -> bestArtwork(track.image)?.let { track.trackArtworkKey() to it } }.toMap()

private data class ArtworkLookup(
    val key: String,
    val title: String,
    val artist: String?,
)

private object CachedArtworkStore {
    private const val MAX_ENTRIES = 256
    private val map = object : LinkedHashMap<String, String>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
            return size > MAX_ENTRIES
        }
    }

    @Synchronized
    fun get(key: String): String? = map[key]

    @Synchronized
    fun put(key: String, url: String) {
        map[key] = url
    }
}

private const val LASTFM_ARTWORK_CONCURRENCY = 12

private fun buildAllArtworkLookups(
    recent: List<RecentTrack>,
    top: List<TopTrack>,
): List<ArtworkLookup> {
    val keys = mutableSetOf<String>()
    val combined = mutableListOf<ArtworkLookup>()
    top.forEach { t ->
        val k = t.trackArtworkKey()
        if (keys.add(k)) combined.add(ArtworkLookup(k, t.name.orEmpty(), t.artist?.text))
    }
    recent.forEach { t ->
        val k = t.trackArtworkKey()
        if (keys.add(k)) combined.add(ArtworkLookup(k, t.name.orEmpty(), t.artist?.text))
    }
    return combined
}

private fun allTracksForArtworkSeedKey(
    recent: List<RecentTrack>,
    top: List<TopTrack>,
): String {
    val builder = StringBuilder()
    top.forEach { builder.append(it.trackArtworkKey()).append('|') }
    builder.append('#')
    recent.forEach { builder.append(it.trackArtworkKey()).append('|') }
    return builder.toString()
}

private suspend fun resolveCatalogueCover(lookup: ArtworkLookup): String? {
    if (lookup.title.isBlank()) return null
    val title = lookup.title
    val artist = lookup.artist
    return resolveYtThumbnail(title, artist)
        ?: TelegramCoverProvider.coverUrl(title, artist)
        ?: CatalogueCoverProvider.resolveCoverUrl(title, artist)
}

private suspend fun resolveYtThumbnail(title: String, artist: String?): String? {
    if (title.isBlank()) return null
    val term = listOfNotNull(artist?.takeIf(String::isNotBlank), title).joinToString(" ")

    val searchResult =
        YouTube.search(term, YouTube.SearchFilter.FILTER_SONG).getOrNull()
            ?: return null
    val first = searchResult.items.firstOrNull { it is SongItem } as? SongItem ?: return null
    val videoId = first.id
    return if (videoId.length == 11) {
        buildYTThumbnailUrl(videoId, YTThumbQuality.HQ)
    } else {
        first.thumbnail.takeIf(String::isNotBlank)
    }
}

private fun List<RecentTrack>.dedupeNowPlayingEchoes(): List<RecentTrack> {
    val nowPlayingKeys = filter { it.isNowPlaying }.mapTo(mutableSetOf()) { it.trackArtworkKey() }
    val emittedNowPlaying = mutableSetOf<String>()
    return filter { track ->
        val key = track.trackArtworkKey()
        when {
            track.isNowPlaying -> emittedNowPlaying.add(key)
            key in nowPlayingKeys -> false
            else -> true
        }
    }
}

// ── View model ────────────────────────────────────────────────────────────────

@HiltViewModel
class LastFmDashboardViewModel
    @Inject
    constructor(
        val repository: LastFmSettingsRepository,
    ) : ViewModel()
