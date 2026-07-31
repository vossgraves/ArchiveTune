/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.animation.core.RepeatableSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBarDefaults
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
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.lastfm.LastFM
import moe.rukamori.archivetune.lastfm.models.RecentTrack
import moe.rukamori.archivetune.lastfm.models.TopTrack
import moe.rukamori.archivetune.lastfm.models.UserInfo
import moe.rukamori.archivetune.scrobbling.LastFmSettingsRepository
import moe.rukamori.archivetune.telegram.TelegramCoverProvider
import moe.rukamori.archivetune.lastfm.CatalogueCoverProvider
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.ui.component.IconButton as AppIconButton
import moe.rukamori.archivetune.ui.utils.backToMain
import javax.inject.Inject

private val DashboardAccentColor = Color(0xFFBE123C)
private val DashboardCardBackground = Color(0xFFFFF5F5)
private val DashboardIconBackground = Color(0xFFFFE4E6)

private enum class LastFmTab { RECENTS, TOP_PLAYED }

@OptIn(ExperimentalMaterial3Api::class)
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

    var userInfo by remember { mutableStateOf<Result<UserInfo>?>(null) }
    var recentTracks by remember { mutableStateOf<Result<List<RecentTrack>>?>(null) }
    var topTracks by remember { mutableStateOf<Result<List<TopTrack>>?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(LastFmTab.RECENTS) }

    fun refresh() {
        val username = current?.username?.takeIf { it.isNotBlank() } ?: return
        if (!isLoggedIn) return
        scope.launch {
            isRefreshing = true
            try {
                current.serviceConfig.apply(sessionKey = current.sessionKey)
                val infoResult = LastFM.getUserInfo(username)
                val recentResult = LastFM.getRecentTracks(username, limit = 20)
                val topResult = LastFM.getTopTracks(username, period = "overall", limit = 20)
                withContext(Dispatchers.Default) {
                    userInfo = infoResult
                    recentTracks = recentResult.map { it.recenttracks.track }
                    topTracks = topResult.map { it.toptracks.track }
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
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.lastfm_dashboard),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    AppIconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = stringResource(R.string.back_button_desc),
                        )
                    }
                },
                actions = {
                    if (isLoggedIn) {
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
                            label = "refresh_rotation",
                        )
                        IconButton(
                            onClick = { if (!isRefreshing) refresh() },
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
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
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
        val top = topTracks?.getOrNull().orEmpty()
        val recentArtworkByTrack = remember(recent) { recent.associateArtworkByTrack() }

        // Seed the live map from the process-wide in-memory cache so that
        // navigating away from and back to the dashboard doesn't trigger
        // another round of network resolutions for tracks we've already
        // resolved this session.
        val seedMap = remember(allTracksForArtworkSeedKey(recent, top)) {
            val snapshot = HashMap<String, String>()
            for (lookup in buildAllArtworkLookups(recent, top)) {
                CachedArtworkStore.get(lookup.key)?.let { snapshot[lookup.key] = it }
            }
            snapshot
        }
        var catalogueArtworkByTrack by remember { mutableStateOf<Map<String, String>>(seedMap) }

        // Combine recent + top tracks into a single de-duplicated list so we can
        // resolve covers for both tabs in one LaunchedEffect pass. Without this,
        // the Recents tab would fall through to the placeholder icon whenever
        // Last.fm didn't return an image (which is most of the time).
        val allTracksForArtwork = remember(recent, top) {
            buildAllArtworkLookups(recent, top)
        }

        LaunchedEffect(allTracksForArtwork) {
            if (allTracksForArtwork.isEmpty()) return@LaunchedEffect
            // Resolve covers concurrently with a bounded parallelism of 12.
            // We stream resolved URLs into the live state map as soon as each
            // chunk finishes so the user sees thumbnails appearing in waves
            // instead of waiting for the whole batch to complete.
            //
            // Per-IP rate limits on iTunes / Deezer are well above 12 rps,
            // so we don't need additional throttling.
            val snapshot = HashMap<String, String>(catalogueArtworkByTrack)
            val chunks = allTracksForArtwork.chunked(LASTFM_ARTWORK_CONCURRENCY)
            for (chunk in chunks) {
                // Skip lookups we've already resolved this session — saves
                // network calls when the user pulls-to-refresh and only
                // a few new tracks came in.
                val toResolve = chunk.filter { lookup -> snapshot[lookup.key].isNullOrBlank() }
                if (toResolve.isEmpty()) continue
                val resolved = withContext(Dispatchers.IO) {
                    toResolve.map { lookup ->
                        async(Dispatchers.IO) {
                            val url = resolveCatalogueCover(lookup)
                            if (url != null) {
                                CachedArtworkStore.put(lookup.key, url)
                                lookup.key to url
                            } else {
                                null
                            }
                        }
                    }.awaitAll().filterNotNull()
                }
                if (resolved.isEmpty()) continue
                resolved.forEach { (k, u) -> snapshot[k] = u }
                // Publish a new map so Compose sees a new reference and
                // recomposes the rows that now have artwork. Mutating the
                // existing map wouldn't trigger recomposition.
                catalogueArtworkByTrack = snapshot.toMap()
            }
        }

        val playerAwareInsets = LocalPlayerAwareWindowInsets.current
        val density = LocalDensity.current
        // Bottom inset (mini player + nav bar) in dp — computed directly from
        // WindowInsets.getBottom(Density) because `WindowInsets.calculateBottomPadding()`
        // is a @Composable extension that requires its own import path which
        // isn't always resolvable across Compose versions. Going through
        // Density.toDp() is the stable API.
        val bottomInsetDp = with(density) { playerAwareInsets.getBottom(density).toDp() }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    playerAwareInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                ),
            contentPadding =
                PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    // Reserve space for the mini player + nav bar so the last
                    // dashboard card isn't overlapped. The window-insets padding
                    // handles the horizontal + bottom system bars; we add the
                    // mini-player height explicitly here (via getBottom which
                    // already folds in MiniPlayerHeight when the player isn't
                    // dismissed) plus a small visual breathing margin.
                    bottom = bottomInsetDp + 16.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // User profile card — history-style with accent tint
            item(key = "user_card") {
                UserCard(
                    userInfo = userInfo,
                    username = current.username,
                    isRefreshing = isRefreshing,
                    onRetry = ::refresh,
                )
            }

            // Tab selector — Recents / Top Played (like History Local/Remote pills)
            item(key = "tab_selector") {
                LastFmTabSelector(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    recentCount = recent.size,
                    topCount = top.size,
                )
            }

            // Tab content
            when (selectedTab) {
                LastFmTab.RECENTS -> {
                    if (recent.isEmpty() && recentTracks != null && !isRefreshing) {
                        item(key = "recent_empty") { EmptyHint(text = stringResource(R.string.lastfm_no_recent_tracks)) }
                    } else {
                        items(recent, key = { "recent_${it.name}_${it.date?.uts ?: it.attr?.nowplaying ?: ""}" }) { track ->
                            DashboardTrackCard(
                                track = track,
                                fallbackArtworkUrl = recentArtworkByTrack[track.trackArtworkKey()] ?: catalogueArtworkByTrack[track.trackArtworkKey()],
                            )
                        }
                    }
                }
                LastFmTab.TOP_PLAYED -> {
                    if (top.isEmpty() && topTracks != null && !isRefreshing) {
                        item(key = "top_empty") { EmptyHint(text = stringResource(R.string.lastfm_no_top_tracks)) }
                    } else {
                        items(top.withIndex().toList(), key = { "top_${it.index}_${it.value.name}" }) { (index, track) ->
                            DashboardTrackCard(
                                track = track,
                                rank = index + 1,
                                playCount = track.playcount,
                                fallbackArtworkUrl = recentArtworkByTrack[track.trackArtworkKey()] ?: catalogueArtworkByTrack[track.trackArtworkKey()],
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Pill-style tab selector matching the History page's Local/Remote ToggleButtons.
 */
@Composable
private fun LastFmTabSelector(
    selectedTab: LastFmTab,
    onTabSelected: (LastFmTab) -> Unit,
    recentCount: Int,
    topCount: Int,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        val tabs = LastFmTab.entries
        tabs.forEachIndexed { index, tab ->
            val checked = tab == selectedTab
            ToggleButton(
                checked = checked,
                onCheckedChange = {
                    if (!checked) onTabSelected(tab)
                },
                modifier =
                    Modifier
                        .weight(1f)
                        .height(48.dp),
                shapes =
                    when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        tabs.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                colors =
                    ToggleButtonDefaults.toggleButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
            ) {
                Text(
                    text =
                        when (tab) {
                            LastFmTab.RECENTS -> stringResource(R.string.lastfm_recent_tracks)
                            LastFmTab.TOP_PLAYED -> stringResource(R.string.lastfm_top_tracks)
                        },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}

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
            color = DashboardIconBackground,
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
private fun UserCard(
    userInfo: Result<UserInfo>?,
    username: String,
    isRefreshing: Boolean,
    onRetry: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        color = DashboardCardBackground,
    ) {
        when {
            userInfo == null && isRefreshing -> {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalArrangement = Arrangement.Center,
                ) { CircularProgressIndicator(color = DashboardAccentColor) }
            }
            userInfo?.isSuccess == true -> {
                val info = userInfo.getOrNull()!!
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val avatar = info.image?.lastOrNull { it.text.isNotBlank() }?.text
                    Surface(
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape,
                        color = DashboardIconBackground,
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
                                )
                            }
                        }
                    }
                    Spacer(Modifier.size(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = info.realname?.takeIf { it.isNotBlank() } ?: info.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "@${info.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(8.dp))
                        info.playcount?.let { count ->
                            Text(
                                text = stringResource(R.string.lastfm_playcount, count),
                                style = MaterialTheme.typography.bodySmall,
                                color = DashboardAccentColor,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
            else -> {
                // Error or not loaded — show placeholder
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        color = DashboardIconBackground,
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
 * Section header styled like the History page: filled dot + title on the left,
 * count on the right.
 */
@Composable
private fun DashboardSectionHeader(
    text: String,
    count: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(8.dp),
            shape = CircleShape,
            color = DashboardAccentColor,
        ) {}
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "$count ${stringResource(R.string.songs)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

private fun bestArtwork(images: List<moe.rukamori.archivetune.lastfm.models.UserImage>?): String? =
    images
        ?.filter { it.text.isNotBlank() }
        ?.lastOrNull()
        ?.text

private fun RecentTrack.trackArtworkKey(): String = "${name.orEmpty().trim().lowercase()}::${artist?.text.orEmpty().trim().lowercase()}"

private fun TopTrack.trackArtworkKey(): String = "${name.orEmpty().trim().lowercase()}::${artist?.text.orEmpty().trim().lowercase()}"

private fun List<RecentTrack>.associateArtworkByTrack(): Map<String, String> =
    mapNotNull { track -> bestArtwork(track.image)?.let { track.trackArtworkKey() to it } }.toMap()

/**
 * Lightweight lookup key for the catalogue artwork resolution pass — avoids
 * pulling TopTrack / RecentTrack into the artwork coroutine scope.
 */
private data class ArtworkLookup(
    val key: String,
    val title: String,
    val artist: String?,
)

/**
 * Process-wide LRU cache of resolved Last.fm dashboard artwork URLs. Keyed by
 * `title::artist` (the same key used by the [ArtworkLookup]). Capped at 256
 * entries — enough for the typical Last.fm dashboard page (50 recents + 50
 * top tracks) plus a few prior sessions' worth of resolved tracks.
 *
 * This is intentionally process-lived (not persisted to disk) because cover
 * URLs from third-party catalogues can expire or change, and re-resolving
 * once per app session is cheap (under a minute for 100 tracks at 12
 * parallel requests).
 */
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

/**
 * Builds the combined list of artwork lookups (top + recent, deduped) used
 * to seed the LaunchedEffect that resolves catalogue covers.
 */
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

/**
 * Stable seed key derived from the recent + top tracks lists so the seed map
 * only recomputes when the actual track set changes (not on every recomposition).
 */
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

/**
 * Full fallback chain for resolving a track's cover URL when Last.fm doesn't
 * return an image. Tries, in order:
 *  1. TelegramCoverProvider (only useful if the user has Telegram configured)
 *  2. iTunes catalogue (free, great for western pop / rock / K-pop with
 *     international distribution)
 *  3. Deezer catalogue (free, strong European / Asian coverage; often has
 *     covers iTunes lacks)
 *  4. YouTube Music search (last-resort fallback so anime / indie tracks that
 *     aren't in either catalogue still get a thumbnail)
 *
 * Returns null on total failure — caller falls through to the placeholder icon.
 */
private suspend fun resolveCatalogueCover(lookup: ArtworkLookup): String? {
    if (lookup.title.isBlank()) return null
    val title = lookup.title
    val artist = lookup.artist
    return TelegramCoverProvider.coverUrl(title, artist)
        ?: CatalogueCoverProvider.resolveCoverUrl(title, artist)
        ?: resolveYtThumbnail(title, artist)
}

/**
 * Resolves a thumbnail URL for [title]/[artist] by searching YouTube Music and
 * taking the first song result's thumbnail. Used as a last-resort fallback when
 * Last.fm and iTunes both come up empty (common for anime/Japanese/indie tracks).
 * Returns null on any failure — the caller falls through to a placeholder icon.
 */
private suspend fun resolveYtThumbnail(title: String, artist: String?): String? {
    if (title.isBlank()) return null
    val term = listOfNotNull(artist?.takeIf(String::isNotBlank), title).joinToString(" ")
    // YouTube.search already returns Result<SearchResult>; don't wrap in runCatching
    // (that would produce Result<Result<SearchResult>> and break type inference).
    val searchResult =
        YouTube.search(term, YouTube.SearchFilter.FILTER_SONG).getOrNull()
            ?: return null
    val first = searchResult.items.firstOrNull { it is SongItem } as? SongItem ?: return null
    return first.thumbnail.takeIf(String::isNotBlank)
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

/**
 * A card-style track row matching the History page design.
 * Uses a rounded card with larger album art (56 dp), bold title,
 * and metadata row with artist and optional play count.
 */
@Composable
private fun DashboardTrackCard(
    track: RecentTrack,
    rank: Int? = null,
    playCount: Int? = null,
    fallbackArtworkUrl: String? = null,
) {
    val artworkUrl = bestArtwork(track.image) ?: fallbackArtworkUrl

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Album art thumbnail
            Surface(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
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
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            // Rank badge (for top tracks)
            if (rank != null) {
                Surface(
                    modifier = Modifier.size(28.dp),
                    shape = CircleShape,
                    color = DashboardIconBackground,
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

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.name.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = track.artist?.text.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Now playing badge or play count
            if (track.isNowPlaying) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = DashboardIconBackground,
                ) {
                    Text(
                        text = stringResource(R.string.lastfm_now_playing),
                        style = MaterialTheme.typography.labelSmall,
                        color = DashboardAccentColor,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            } else if (playCount != null) {
                Text(
                    text = stringResource(R.string.lastfm_playcount_short, playCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Wrapper for TopTrack to match the DashboardTrackCard interface.
 */
@Composable
private fun DashboardTrackCard(
    track: TopTrack,
    rank: Int? = null,
    playCount: Int? = null,
    fallbackArtworkUrl: String? = null,
) {
    val artworkUrl = bestArtwork(track.image) ?: fallbackArtworkUrl

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
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
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
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
                    color = DashboardIconBackground,
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

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.name.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = track.artist?.text.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (playCount != null) {
                Text(
                    text = stringResource(R.string.lastfm_playcount_short, playCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@HiltViewModel
class LastFmDashboardViewModel
    @Inject
    constructor(
        val repository: LastFmSettingsRepository,
    ) : ViewModel()
