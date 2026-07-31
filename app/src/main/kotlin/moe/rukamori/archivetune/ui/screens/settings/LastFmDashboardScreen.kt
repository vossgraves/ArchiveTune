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

@Composable
private fun dashboardCardColor(): Color =
    if (isSystemInDarkTheme()) Color(0xFF1F1416) else Color(0xFFFFF5F5)

@Composable
private fun dashboardIconBackgroundColor(): Color =
    if (isSystemInDarkTheme()) Color(0xFF3A1F23) else Color(0xFFFFE4E6)

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
                    playerAwareInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                ),
            contentPadding =
                PaddingValues(
                    top = innerPadding.calculateTopPadding(),

                    

                    
                    bottom = bottomInsetDp + 16.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            
            item(key = "user_card") {
                UserCard(
                    userInfo = userInfo,
                    username = current.username,
                    isRefreshing = isRefreshing,
                    onRetry = ::refresh,
                )
            }

            item(key = "tab_selector") {
                LastFmTabSelector(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    recentCount = recent.size,
                    topCount = top.size,
                )
            }

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
private fun UserCard(
    userInfo: Result<UserInfo>?,
    username: String,
    isRefreshing: Boolean,
    onRetry: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        color = dashboardCardColor(),
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
    return TelegramCoverProvider.coverUrl(title, artist)
        ?: CatalogueCoverProvider.resolveCoverUrl(title, artist)
        ?: resolveYtThumbnail(title, artist)
}

private suspend fun resolveYtThumbnail(title: String, artist: String?): String? {
    if (title.isBlank()) return null
    val term = listOfNotNull(artist?.takeIf(String::isNotBlank), title).joinToString(" ")

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

            if (track.isNowPlaying) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = dashboardIconBackgroundColor(),
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
