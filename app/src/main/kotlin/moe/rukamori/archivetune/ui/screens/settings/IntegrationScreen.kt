/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.ListenBrainzEnabledKey
import moe.rukamori.archivetune.constants.ListenBrainzTokenKey
import moe.rukamori.archivetune.constants.ManualSourceLoginEnabledKey
import moe.rukamori.archivetune.constants.ShowSpotifyPlaylistsKey
import moe.rukamori.archivetune.spotify.SpotifyAccountViewModel
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.InfoLabel
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.component.TextFieldDialog
import moe.rukamori.archivetune.ui.menu.CrossServiceImportPlaylistDialog
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.rememberPreference
import androidx.compose.foundation.layout.asPaddingValues

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntegrationScreen(
    navController: NavController,
    scrollTo: String? = null,
    spotifyAccountViewModel: SpotifyAccountViewModel = hiltViewModel(),
) {
    val (listenBrainzEnabled, onListenBrainzEnabledChange) = rememberPreference(ListenBrainzEnabledKey, false)
    val (listenBrainzToken, onListenBrainzTokenChange) = rememberPreference(ListenBrainzTokenKey, "")
    // Manual Tidal/Qobuz instance & account management is an advanced flow gated behind the
    // "Manual source sign-in" experimental toggle. Off by default: the app auto-uses the community
    // source pool, so most users never need to see raw instance/token fields.
    val (manualSourceLogin, _) = rememberPreference(ManualSourceLoginEnabledKey, false)

    val spotifyState by spotifyAccountViewModel.uiState.collectAsStateWithLifecycle()
    val (showSpotifyPlaylists, onShowSpotifyPlaylistsChange) = rememberPreference(ShowSpotifyPlaylistsKey, false)
    var showSpotifyLogin by rememberSaveable { mutableStateOf(false) }

    var showListenBrainzTokenEditor = remember { mutableStateOf(false) }
    var showCrossServiceImport by remember { mutableStateOf(false) }

    LaunchedEffect(spotifyState.isAuthenticated) {
        if (spotifyState.isAuthenticated) {
            showSpotifyLogin = false
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.integration)) },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(
                            painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        val playerAwareBottomPadding =
            LocalPlayerAwareWindowInsets.current
                .only(WindowInsetsSides.Bottom)
                .asPaddingValues()
                .calculateBottomPadding()
        val topPadding = innerPadding.calculateTopPadding()
        val scrollState = rememberScrollState()
        val positions = rememberPreferencePositions()

        LaunchedEffect(scrollTo) { positions.scrollToKey(scrollTo, scrollState) }

        Column(
            Modifier
                .padding(top = topPadding)
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal))
                .verticalScroll(scrollState)
                .padding(bottom = playerAwareBottomPadding + SettingsDimensions.ScreenBottomPadding),
        ) {
            // AI integration lives at the top of the Integration page (Task 8). It used to
            // be a top-level pill on the main settings page; moving it here co-locates it
            // with the other integrations (Discord, Last.fm, Tidal, Qobuz, Telegram, …).
            PreferenceGroup(
                modifier = positions.modifierFor("ai_integration"),
                title = stringResource(R.string.ai_integration),
            ) {
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.ai_integration)) },
                        description = stringResource(R.string.ai_integration_desc),
                        icon = { Icon(painterResource(R.drawable.ai), null) },
                        onClick = { navController.navigate("settings/ai_integration") },
                    )
                }
            }

            PreferenceGroup(
                modifier = positions.modifierFor("discord_presence"),
                title = stringResource(R.string.general),
            ) {
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.discord_integration)) },
                        icon = { Icon(painterResource(R.drawable.discord), null) },
                        onClick = {
                            navController.navigate("settings/discord")
                        },
                    )
                }
            }

            // "Music Sources" groups every external streaming source together:
            // Tidal, Qobuz, Deezer, and Telegram. Tidal/Qobuz/Deezer are
            // gated behind the "Manual source sign-in" experimental toggle
            // because their instance/token flows aren't useful for most users
            // (the app auto-uses the community source pool by default).
            // Telegram is NOT gated — its TDLib client is self-contained and
            // doesn't share the manual-token flow.
            PreferenceGroup(
                modifier = positions.modifierFor("music_sources"),
                title = stringResource(R.string.music_sources),
            ) {
                if (manualSourceLogin) {
                    item {
                        PreferenceEntry(
                            title = { Text(stringResource(R.string.tidal_integration)) },
                            description = stringResource(R.string.tidal_integration_description),
                            icon = { Icon(painterResource(R.drawable.provider_tidal), null) },
                            onClick = {
                                navController.navigate("settings/tidal")
                            },
                        )
                    }

                    item {
                        PreferenceEntry(
                            title = { Text(stringResource(R.string.qobuz_integration)) },
                            description = stringResource(R.string.qobuz_integration_description),
                            icon = { Icon(painterResource(R.drawable.provider_qobuz), null) },
                            onClick = {
                                navController.navigate("settings/qobuz")
                            },
                        )
                    }

                    item {
                        PreferenceEntry(
                            title = { Text(stringResource(R.string.deezer_integration)) },
                            description = stringResource(R.string.deezer_integration_description),
                            icon = { Icon(painterResource(R.drawable.provider_deezer), null) },
                            onClick = {
                                navController.navigate("settings/deezer")
                            },
                        )
                    }
                }

                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.telegram_integration)) },
                        description = stringResource(R.string.telegram_integration_description),
                        icon = { Icon(painterResource(R.drawable.provider_telegram), null) },
                        onClick = {
                            navController.navigate("settings/telegram")
                        },
                    )
                }
            }

            // "External Sources" hosts Spotify — a read-only playlist import source, not a
            // playback source like Tidal/Qobuz/Deezer/Telegram above. Separating it from
            // "Music Sources" makes the distinction clear: Music Sources feed the player,
            // External Sources feed the Library (playlist sync, scrobbling, etc.).
            PreferenceGroup(
                modifier = positions.modifierFor("external_sources"),
                title = stringResource(R.string.external_sources),
            ) {
                spotifyAccountPreferences(
                    state = spotifyState,
                    showPlaylists = showSpotifyPlaylists,
                    onConnectClick = { showSpotifyLogin = true },
                    onShowPlaylistsChange = onShowSpotifyPlaylistsChange,
                    onReloadClick = spotifyAccountViewModel::reloadPlaylists,
                    onLogoutClick = { spotifyAccountViewModel.logout() },
                )
            }

            PreferenceGroup(
                // Also carries the "lastfm_scrobbling" anchor, which used to sit on the removed
                // Accounts group. Settings search offers a "Last.fm scrobbling" result that scrolls
                // here, so without this the result would open this screen and then sit at the top.
                // Chaining is safe: modifierFor only registers a y position per key.
                modifier =
                    positions
                        .modifierFor("listenbrainz")
                        .then(positions.modifierFor("lastfm_scrobbling")),
                title = stringResource(R.string.scrobbling),
            ) {
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.lastfm_integration)) },
                        icon = { Icon(painterResource(R.drawable.token), null) },
                        onClick = {
                            navController.navigate("settings/lastfm")
                        },
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.listenbrainz_scrobbling)) },
                        description = stringResource(R.string.listenbrainz_scrobbling_description),
                        icon = { Icon(painterResource(R.drawable.token), null) },
                        checked = listenBrainzEnabled,
                        onCheckedChange = onListenBrainzEnabledChange,
                    )
                }

                item {
                    PreferenceEntry(
                        title = {
                            Text(
                                if (listenBrainzToken.isBlank()) {
                                    stringResource(
                                        R.string.set_listenbrainz_token,
                                    )
                                } else {
                                    stringResource(R.string.edit_listenbrainz_token)
                                },
                            )
                        },
                        icon = { Icon(painterResource(R.drawable.token), null) },
                        onClick = { showListenBrainzTokenEditor.value = true },
                    )
                }
            }

            // ─── Playlist import ──────────────────────────────────────────
            // Cross-service playlist import: paste a URL from YouTube Music,
            // Apple Music, Amazon Music, Tidal or Deezer and we'll resolve
            // the tracks against YouTube Music and build a local playlist.
            // Lives here (in Integration) per product decision so all
            // cross-service features are co-located.
            PreferenceGroup(
                modifier = positions.modifierFor("cross_service_import"),
                title = stringResource(R.string.cross_service_import_playlist_title),
            ) {
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.cross_service_import_entry_title)) },
                        description = stringResource(R.string.cross_service_import_entry_desc),
                        icon = { Icon(painterResource(R.drawable.playlist_import), null) },
                        onClick = { showCrossServiceImport = true },
                    )
                }
            }
        }
    }

    if (showListenBrainzTokenEditor.value) {
        TextFieldDialog(
            initialTextFieldValue =
                androidx.compose.ui.text.input
                    .TextFieldValue(listenBrainzToken),
            onDone = { data ->
                onListenBrainzTokenChange(data)
                showListenBrainzTokenEditor.value = false
            },
            onDismiss = { showListenBrainzTokenEditor.value = false },
            singleLine = true,
            maxLines = 1,
            isInputValid = {
                it.isNotEmpty()
            },
            extraContent = {
                InfoLabel(text = stringResource(R.string.listenbrainz_scrobbling_description))
            },
        )
    }

    CrossServiceImportPlaylistDialog(
        isVisible = showCrossServiceImport,
        onDismiss = { showCrossServiceImport = false },
    )

    if (showSpotifyLogin) {
        SpotifyLoginSheet(
            onDismiss = { showSpotifyLogin = false },
            onCookiesCaptured = { spDc, spKey ->
                showSpotifyLogin = false
                spotifyAccountViewModel.connectWithCookies(spDc = spDc, spKey = spKey)
            },
        )
    }

    spotifyState.errorMessage?.let { error ->
        SpotifyErrorDialog(
            message = error,
            onDismiss = spotifyAccountViewModel::dismissError,
        )
    }
}
