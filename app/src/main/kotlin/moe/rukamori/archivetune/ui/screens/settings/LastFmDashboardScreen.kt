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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.lastfm.LastFM
import moe.rukamori.archivetune.lastfm.models.RecentTrack
import moe.rukamori.archivetune.lastfm.models.TopTrack
import moe.rukamori.archivetune.lastfm.models.UserInfo
import moe.rukamori.archivetune.scrobbling.LastFmSettingsRepository
import moe.rukamori.archivetune.ui.component.IconButton as AppIconButton
import moe.rukamori.archivetune.ui.utils.backToMain
import javax.inject.Inject

/**
 * In-app Last.fm dashboard accessible from the profile overflow menu.
 *
 * If the user is not logged in to Last.fm, the screen renders a single
 * call-to-action button that navigates to the existing Last.fm settings
 * screen for sign-in. If logged in, the dashboard fetches and displays:
 *
 *   - User profile card (avatar, name, total playcount, registered date)
 *   - Recent tracks with album art thumbnails (up to 20)
 *   - All-time top tracks with artwork thumbnails (up to 20)
 *
 * A refresh button in the top app bar allows the user to manually
 * re-fetch all stats. Errors during fetch are surfaced inline —
 * the dashboard degrades gracefully and lets the user retry.
 */
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

    fun refresh() {
        val username = current?.username?.takeIf { it.isNotBlank() } ?: return
        if (!isLoggedIn) return
        scope.launch {
            isRefreshing = true
            try {
                // Apply the runtime config so LastFM is ready to call.
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
                        // Refresh button — spins while data is being fetched.
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

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = 24.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "user_card") {
                UserCard(
                    userInfo = userInfo,
                    username = current.username,
                    isRefreshing = isRefreshing,
                    onRetry = ::refresh,
                )
            }

            item(key = "recent_header") {
                SectionHeader(text = stringResource(R.string.lastfm_recent_tracks))
            }

            val recent = recentTracks?.getOrNull().orEmpty()
            if (recent.isEmpty() && recentTracks != null && !isRefreshing) {
                item(key = "recent_empty") { EmptyHint(text = stringResource(R.string.lastfm_no_recent_tracks)) }
            } else {
                items(recent, key = { "recent_${it.name}_${it.date?.uts ?: it.attr?.nowplaying ?: ""}" }) { track ->
                    RecentTrackRow(track)
                }
            }

            item(key = "top_header") {
                SectionHeader(text = stringResource(R.string.lastfm_top_tracks))
            }

            val top = topTracks?.getOrNull().orEmpty()
            if (top.isEmpty() && topTracks != null && !isRefreshing) {
                item(key = "top_empty") { EmptyHint(text = stringResource(R.string.lastfm_no_top_tracks)) }
            } else {
                items(top.withIndex().toList(), key = { "top_${it.index}_${it.value.name}" }) { (index, track) ->
                    TopTrackRow(rank = index + 1, track = track)
                }
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
        Icon(
            painter = painterResource(R.drawable.stats),
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
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
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
    ) {
        when {
            userInfo == null && isRefreshing -> {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalArrangement = Arrangement.Center,
                ) { CircularProgressIndicator() }
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
                        color = MaterialTheme.colorScheme.primaryContainer,
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
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            userInfo?.isFailure == true -> {
                // Silently degrade — show the placeholder instead of the error card
                // so the user can still see recent tracks and top tracks below.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.account),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(12.dp))
                    Text(
                        text = "@$username",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            else -> {
                // Logged in but not yet loaded — show a quiet placeholder.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.account),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
    )
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

/**
 * Extracts the best-quality (largest) artwork URL from a list of Last.fm
 * [UserImage] entries.  Returns null when the list is empty or all URLs
 * are blank.
 */
private fun bestArtwork(images: List<moe.rukamori.archivetune.lastfm.models.UserImage>?): String? =
    images
        ?.filter { it.text.isNotBlank() }
        ?.lastOrNull()
        ?.text

/**
 * A single recent-track row with an album-art thumbnail fetched from
 * the Last.fm API image data attached to each track.
 */
@Composable
private fun RecentTrackRow(track: RecentTrack) {
    val artworkUrl = bestArtwork(track.image)

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Album art thumbnail (48 dp square with rounded corners)
        Surface(
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            if (!artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = artworkUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else {
                // Fallback placeholder when no artwork is available
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.name.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artist?.text.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (track.isNowPlaying) {
            Text(
                text = stringResource(R.string.lastfm_now_playing),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 20.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

/**
 * A single top-track row with rank badge and an artwork thumbnail
 * fetched from the Last.fm API image data attached to each track.
 */
@Composable
private fun TopTrackRow(rank: Int, track: TopTrack) {
    val artworkUrl = bestArtwork(track.image)

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Album art thumbnail (48 dp square with rounded corners)
        Surface(
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            if (!artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = artworkUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.size(12.dp))
        // Rank badge
        Surface(
            modifier = Modifier.size(28.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = rank.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        Spacer(Modifier.size(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.name.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artist?.text.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        track.playcount?.let { count ->
            Text(
                text = stringResource(R.string.lastfm_playcount_short, count),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 20.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

/**
 * Thin HiltViewModel wrapper so we can inject LastFmSettingsRepository
 * into the Composable.
 */
@HiltViewModel
class LastFmDashboardViewModel
    @Inject
    constructor(
        val repository: LastFmSettingsRepository,
    ) : ViewModel()
