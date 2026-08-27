/*
 * ArchiveTune (2026)
 * © ArchiveTuneFork contributors — github.com/vossgraves/ArchiveTune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package app.atf.media.ui.screens.settings

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
import app.atf.media.LocalPlayerAwareWindowInsets
import app.atf.media.R
import app.atf.media.constants.DeezerArlKey
import app.atf.media.constants.ListenBrainzEnabledKey
import app.atf.media.constants.ListenBrainzTokenKey
import app.atf.media.constants.ManualSourceLoginEnabledKey
import app.atf.media.constants.QobuzTokensKey
import app.atf.media.constants.ShowSpotifyPlaylistsKey
import app.atf.media.constants.TidalAccessTokenKey
import app.atf.media.spotify.SpotifyAccountViewModel
import app.atf.media.ui.component.IconButton
import app.atf.media.ui.component.InfoLabel
import app.atf.media.ui.component.PreferenceEntry
import app.atf.media.ui.component.PreferenceGroup
import app.atf.media.ui.component.SwitchPreference
import app.atf.media.ui.component.TextFieldDialog
import app.atf.media.ui.menu.CrossServiceImportPlaylistDialog
import app.atf.media.ui.utils.backToMain
import app.atf.media.utils.rememberPreference
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
    // …but a source the user has *already* signed into must stay reachable regardless, otherwise
    // turning the toggle back off strands the account with no way to view or sign out of it — and
    // "Check source" would keep pointing at a screen that is no longer in the list.
    val (deezerArl, _) = rememberPreference(DeezerArlKey, "")
    val (tidalAccessToken, _) = rememberPreference(TidalAccessTokenKey, "")
    val (qobuzTokens, _) = rememberPreference(QobuzTokensKey, "")
    val showDeezerRow = manualSourceLogin || deezerArl.isNotBlank()
    val showTidalRow = manualSourceLogin || tidalAccessToken.isNotBlank()
    val showQobuzRow = manualSourceLogin || qobuzTokens.isNotBlank()

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
                // Chained before verticalScroll so it measures the viewport, not the scrolling content.
                .then(positions.containerModifier())
                .verticalScroll(scrollState)
                .padding(bottom = playerAwareBottomPadding + SettingsDimensions.ScreenBottomPadding),
        ) {
            // Apple Music first: login-only entry (quality + tokens live on its page,
            // playback quality is configured in Sources).
            PreferenceGroup(
                modifier = positions.modifierFor("applemusic_login"),
                title = stringResource(R.string.applemusic_settings),
            ) {
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.applemusic_sign_in_web)) },
                        description = stringResource(R.string.applemusic_settings),
                        icon = { Icon(painterResource(R.drawable.ic_music), null) },
                        onClick = { navController.navigate("settings/applemusic") },
                    )
                }
            }

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
                        modifier = positions.modifierFor("discord_account"),
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
                item(visible = showTidalRow) {
                    PreferenceEntry(
                        modifier = positions.modifierFor("tidal"),
                        title = { Text(stringResource(R.string.tidal_integration)) },
                        description = stringResource(R.string.tidal_integration_description),
                        icon = { Icon(painterResource(R.drawable.provider_tidal), null) },
                        onClick = {
                            navController.navigate("settings/tidal")
                        },
                    )
                }

                item(visible = showQobuzRow) {
                    PreferenceEntry(
                        modifier = positions.modifierFor("qobuz"),
                        title = { Text(stringResource(R.string.qobuz_integration)) },
                        description = stringResource(R.string.qobuz_integration_description),
                        icon = { Icon(painterResource(R.drawable.provider_qobuz), null) },
                        onClick = {
                            navController.navigate("settings/qobuz")
                        },
                    )
                }

                item(visible = showDeezerRow) {
                    PreferenceEntry(
                        modifier = positions.modifierFor("deezer"),
                        title = { Text(stringResource(R.string.deezer_integration)) },
                        description = stringResource(R.string.deezer_integration_description),
                        icon = { Icon(painterResource(R.drawable.provider_deezer), null) },
                        onClick = {
                            navController.navigate("settings/deezer")
                        },
                    )
                }

                item {
                    PreferenceEntry(
                        modifier = positions.modifierFor("telegram"),
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
                // Also carries "spotify": Spotify is the only account in this group, so a search
                // hit on it scrolls here. Chaining is safe — modifierFor only records a position.
                modifier =
                    positions
                        .modifierFor("external_sources")
                        .then(positions.modifierFor("spotify")),
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
                        modifier = positions.modifierFor("lastfm_account"),
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
                        modifier = positions.modifierFor("listenbrainz_token"),
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
