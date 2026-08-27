/*
 * ArchiveTune (2026)
 * © ArchiveTuneFork contributors — github.com/vossgraves/ArchiveTune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package app.atf.media.ui.screens

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import app.atf.media.BuildConfig
import app.atf.media.constants.UpdateChannel
import app.atf.media.defaultUpdateChannel
import app.atf.media.musicrecognition.MusicRecognitionRoute
import app.atf.media.musicrecognition.MusicRecognitionDetailsRoute
import app.atf.media.ui.screens.BrowseScreen
import app.atf.media.ui.screens.artist.ArtistAlbumsScreen
import app.atf.media.ui.screens.artist.ArtistItemsScreen
import app.atf.media.ui.screens.artist.ArtistScreen
import app.atf.media.ui.screens.artist.ArtistSongsScreen
import app.atf.media.ui.screens.library.LibraryScreen
import app.atf.media.ui.screens.library.LocalSongScreen
import app.atf.media.ui.screens.musicrecognition.MusicRecognitionScreen
import app.atf.media.ui.screens.musicrecognition.MusicRecognitionDetailsScreen
import app.atf.media.ui.screens.playlist.AutoPlaylistScreen
import app.atf.media.ui.screens.playlist.CachePlaylistScreen
import app.atf.media.ui.screens.playlist.LocalPlaylistScreen
import app.atf.media.ui.screens.playlist.OnlinePlaylistScreen
import app.atf.media.ui.screens.playlist.SpotifyPlaylistScreen
import app.atf.media.ui.screens.playlist.TopPlaylistScreen
import app.atf.media.ui.screens.search.OnlineSearchResult
import app.atf.media.ui.screens.search.OnlineSearchResultArgument
import app.atf.media.ui.screens.search.OnlineSearchProviderArgument
import app.atf.media.ui.screens.search.OnlineSearchResultRoute
import app.atf.media.ui.screens.search.OnlineSearchResultRoutePrefix
import app.atf.media.ui.screens.search.SearchScreen
import app.atf.media.ui.screens.settings.AboutScreen
import app.atf.media.ui.screens.settings.AccountSettings
import app.atf.media.ui.screens.settings.AiIntegrationSettings
import app.atf.media.ui.screens.settings.AodCustomizedScreen
import app.atf.media.ui.screens.settings.AppearanceExtrasSettings
import app.atf.media.ui.screens.settings.NavigationBarSettings
import app.atf.media.ui.screens.settings.AppearanceSettings
import app.atf.media.ui.screens.settings.BackupAndRestore
import app.atf.media.ui.screens.settings.ChangelogScreen
import app.atf.media.ui.screens.settings.ContentSettings
import app.atf.media.ui.screens.settings.CustomizeBackground
import app.atf.media.ui.screens.settings.DebugSettings
import app.atf.media.ui.screens.settings.DiscordSettings
import app.atf.media.ui.screens.settings.ExportDownloadedSongsScreen
import app.atf.media.ui.screens.settings.HiddenPlaylistsScreen
import app.atf.media.ui.screens.settings.IconScreen
import app.atf.media.ui.screens.settings.IntegrationScreen
import app.atf.media.ui.screens.settings.InternetSettings
import app.atf.media.ui.screens.settings.TidalSettings
import app.atf.media.ui.screens.settings.QobuzSettings
import app.atf.media.ui.screens.settings.DeezerSettings
import app.atf.media.ui.screens.settings.JioSettings
import app.atf.media.ui.screens.settings.TidalLoginScreen
import app.atf.media.ui.screens.settings.TIDAL_LOGIN_ROUTE
import app.atf.media.ui.screens.settings.QobuzLoginScreen
import app.atf.media.ui.screens.settings.QOBUZ_LOGIN_ROUTE
import app.atf.media.ui.screens.settings.AppleMusicLoginScreen
import app.atf.media.ui.screens.settings.AppleMusicSettings
import app.atf.media.ui.screens.settings.APPLE_MUSIC_LOGIN_ROUTE
import app.atf.media.ui.screens.settings.AppleMusicLoginScreen
import app.atf.media.ui.screens.settings.AppleMusicSettings
import app.atf.media.ui.screens.settings.DeezerLoginScreen
import app.atf.media.ui.screens.settings.DEEZER_LOGIN_ROUTE
import app.atf.media.ui.screens.settings.LASTFM_LOGIN_ROUTE
import app.atf.media.ui.screens.settings.LastFmLoginScreen
import app.atf.media.ui.screens.settings.LASTFM_LIBREFM_LOGIN_ROUTE
import app.atf.media.ui.screens.settings.LibreFmLoginScreen
import app.atf.media.ui.screens.settings.TELEGRAM_LOGIN_ROUTE
import app.atf.media.ui.screens.settings.TelegramLoginScreen
import app.atf.media.ui.screens.settings.TelegramSettings
import app.atf.media.ui.screens.settings.LastFMSettings
import app.atf.media.ui.screens.settings.LastFmDashboardScreen
import app.atf.media.ui.screens.settings.LanguagePackSettings
import app.atf.media.ui.screens.settings.LogcatScreen
import app.atf.media.ui.screens.settings.LyricsAnimationSettings
import app.atf.media.ui.screens.settings.LyricsSettings
import app.atf.media.ui.screens.settings.LyricsProvidersSettings
import app.atf.media.ui.screens.settings.LyricsRomanisationSettings
import app.atf.media.ui.screens.settings.MusicTogetherScreen
import app.atf.media.ui.screens.settings.PO_TOKEN_ROUTE
import app.atf.media.ui.screens.settings.PalettePickerScreen
import app.atf.media.ui.screens.settings.PlayerSettings
import app.atf.media.ui.screens.settings.PoTokenScreen
import app.atf.media.ui.screens.settings.PrivacySettings
import app.atf.media.ui.screens.settings.SettingsScreen
import app.atf.media.ui.screens.settings.SourceSettings
import app.atf.media.ui.screens.settings.StorageSettings
import app.atf.media.ui.screens.settings.DownloadsSettings
import app.atf.media.ui.screens.settings.ThemeCreatorScreen
import app.atf.media.ui.screens.settings.UpdateScreen
import app.atf.media.viewmodels.OnlineSearchSort

@OptIn(ExperimentalMaterial3Api::class)
fun NavGraphBuilder.navigationBuilder(
    navController: NavHostController,
    scrollBehavior: TopAppBarScrollBehavior,
    latestVersionName: () -> String,
    disableAnimations: Boolean = false,
    onClearUpdateBadge: () -> Unit = {},
    onSearchQuery: (String) -> Unit = {},
    onVoiceSearch: () -> Unit = {},
    homeListState: LazyListState? = null,
    searchListState: LazyListState? = null,
    homeScrollConnection: NestedScrollConnection? = null,
    searchScrollConnection: NestedScrollConnection? = null,
    onlineSearchSort: OnlineSearchSort = OnlineSearchSort.DEFAULT,
) {
    composable(Screens.Home.route) {
        HomeScreen(
            navController,
            headerScrollConnection = homeScrollConnection,
            listState = homeListState,
        )
    }
    composable(
        Screens.Library.route,
    ) {
        LibraryScreen(navController)
    }
    composable(Screens.Search.route) {
        SearchScreen(
            navController = navController,
            onSearchQuery = onSearchQuery,
            onVoiceSearch = onVoiceSearch,
            headerScrollConnection = searchScrollConnection,
            listState = searchListState,
        )
    }
    composable("local_songs") {
        LocalSongScreen(navController)
    }
    composable("history") {
        HistoryScreen(navController)
    }
    composable("stats") {
        StatsScreen(navController)
    }
    composable("news") {
        NewsScreen(navController)
    }
    composable(
        route = "view_news/{newsId}",
        arguments =
            listOf(
                navArgument("newsId") { type = NavType.StringType },
            ),
    ) {
        ViewNewsScreen(navController)
    }
    composable(
        route = "year_in_music?year={year}",
        arguments =
            listOf(
                navArgument("year") {
                    type = NavType.IntType
                    defaultValue = -1
                },
            ),
    ) { backStackEntry ->
        val selectedYear = backStackEntry.arguments?.getInt("year")?.takeIf { it > 0 }
        YearInMusicScreen(
            navController = navController,
            initialYear = selectedYear,
        )
    }
    composable(MusicRecognitionRoute) {
        MusicRecognitionScreen(navController)
    }
    composable(MusicRecognitionDetailsRoute) { backStackEntry ->
        val encodedTrack = backStackEntry.arguments?.getString("encodedTrack").orEmpty()
        MusicRecognitionDetailsScreen(navController, encodedTrack)
    }
    composable(Screens.MoodAndGenres.route) {
        MoodAndGenresScreen(navController)
    }
    composable("account") {
        AccountScreen(navController, scrollBehavior)
    }
    composable("new_release") {
        NewReleaseScreen(navController, scrollBehavior)
    }
    composable("charts_screen") {
        ChartsScreen(navController)
    }
    composable(
        route = "browse/{browseId}",
        arguments =
            listOf(
                navArgument("browseId") {
                    type = NavType.StringType
                },
            ),
    ) {
        BrowseScreen(
            navController,
            scrollBehavior,
            it.arguments?.getString("browseId"),
        )
    }
    composable(
        route = OnlineSearchResultRoute,
        arguments =
            listOf(
                navArgument(OnlineSearchResultArgument) {
                    type = NavType.StringType
                },
                navArgument(OnlineSearchProviderArgument) {
                    type = NavType.StringType
                    defaultValue = app.atf.media.constants.SearchProvider.YOUTUBE.name
                },
            ),
        enterTransition = {
            if (disableAnimations) {
                fadeIn(tween(0))
            } else {
                fadeIn(tween(250))
            }
        },
        exitTransition = {
            if (disableAnimations) {
                fadeOut(tween(0))
            } else if (targetState.destination.route?.startsWith(OnlineSearchResultRoutePrefix) == true) {
                fadeOut(tween(200))
            } else {
                fadeOut(tween(200)) + slideOutHorizontally { -it / 2 }
            }
        },
        popEnterTransition = {
            if (disableAnimations) {
                fadeIn(tween(0))
            } else if (initialState.destination.route?.startsWith(OnlineSearchResultRoutePrefix) == true) {
                fadeIn(tween(250))
            } else {
                fadeIn(tween(250)) + slideInHorizontally { -it / 2 }
            }
        },
        popExitTransition = {
            if (disableAnimations) {
                fadeOut(tween(0))
            } else {
                fadeOut(tween(200))
            }
        },
    ) {
        OnlineSearchResult(
            navController = navController,
            searchSort = onlineSearchSort,
        )
    }
    composable(
        route = "album/{albumId}",
        arguments =
            listOf(
                navArgument("albumId") {
                    type = NavType.StringType
                },
            ),
    ) {
        AlbumScreen(navController, scrollBehavior)
    }
    composable(
        route = "artist/{artistId}",
        arguments =
            listOf(
                navArgument("artistId") {
                    type = NavType.StringType
                },
            ),
    ) {
        ArtistScreen(navController, scrollBehavior)
    }
    composable(
        route = "artist/{artistId}/songs",
        arguments =
            listOf(
                navArgument("artistId") {
                    type = NavType.StringType
                },
            ),
    ) {
        ArtistSongsScreen(navController, scrollBehavior)
    }
    composable(
        route = "artist/{artistId}/albums",
        arguments =
            listOf(
                navArgument("artistId") {
                    type = NavType.StringType
                },
            ),
    ) {
        ArtistAlbumsScreen(navController, scrollBehavior)
    }
    composable(
        route = "artist/{artistId}/items?browseId={browseId}&params={params}",
        arguments =
            listOf(
                navArgument("artistId") {
                    type = NavType.StringType
                },
                navArgument("browseId") {
                    type = NavType.StringType
                    nullable = true
                },
                navArgument("params") {
                    type = NavType.StringType
                    nullable = true
                },
            ),
    ) {
        ArtistItemsScreen(navController, scrollBehavior)
    }
    composable(
        route = "online_playlist/{playlistId}",
        arguments =
            listOf(
                navArgument("playlistId") {
                    type = NavType.StringType
                },
            ),
    ) {
        OnlinePlaylistScreen(navController, scrollBehavior)
    }
    composable(
        route = "local_playlist/{playlistId}",
        arguments =
            listOf(
                navArgument("playlistId") {
                    type = NavType.StringType
                },
            ),
    ) {
        LocalPlaylistScreen(navController, scrollBehavior)
    }
    composable(
        route = "spotify_playlist/{playlistId}",
        arguments =
            listOf(
                navArgument("playlistId") {
                    type = NavType.StringType
                },
            ),
    ) {
        SpotifyPlaylistScreen(navController, scrollBehavior)
    }
    composable(
        route = "auto_playlist/{playlist}?tab={tab}",
        arguments =
            listOf(
                navArgument("playlist") {
                    type = NavType.StringType
                },
                navArgument("tab") {
                    type = NavType.StringType
                    defaultValue = "downloaded"
                },
            ),
    ) {
        AutoPlaylistScreen(navController, scrollBehavior)
    }
    composable(
        route = "cache_playlist/{playlist}",
        arguments =
            listOf(
                navArgument("playlist") {
                    type = NavType.StringType
                },
            ),
    ) {
        CachePlaylistScreen(navController, scrollBehavior)
    }
    composable(
        route = "top_playlist/{top}",
        arguments =
            listOf(
                navArgument("top") {
                    type = NavType.StringType
                },
            ),
    ) {
        TopPlaylistScreen(navController, scrollBehavior)
    }
    composable(
        route = "youtube_browse/{browseId}?params={params}",
        arguments =
            listOf(
                navArgument("browseId") {
                    type = NavType.StringType
                    nullable = true
                },
                navArgument("params") {
                    type = NavType.StringType
                    nullable = true
                },
            ),
    ) {
        YouTubeBrowseScreen(navController)
    }
    composable("settings") {
        SettingsScreen(navController, latestVersionName())
    }
    composable("settings/account") {
        AccountSettings(navController, latestVersionName())
    }
    composable("settings/hidden_playlists") {
        HiddenPlaylistsScreen(navController)
    }
    composable(
        route = "settings/appearance?scrollTo={scrollTo}",
        arguments = listOf(navArgument("scrollTo") { type = NavType.StringType; nullable = true; defaultValue = null }),
    ) {
        AppearanceSettings(navController, it.savedStateHandle["scrollTo"])
    }
    composable(
        route = "settings/appearance/extras?scrollTo={scrollTo}",
        arguments = listOf(navArgument("scrollTo") { type = NavType.StringType; nullable = true; defaultValue = null }),
    ) {
        AppearanceExtrasSettings(navController, scrollTo = it.savedStateHandle["scrollTo"])
    }
    composable(
        route = "settings/appearance/navigation_bar?scrollTo={scrollTo}",
        arguments = listOf(navArgument("scrollTo") { type = NavType.StringType; nullable = true; defaultValue = null }),
    ) {
        NavigationBarSettings(navController, scrollTo = it.savedStateHandle["scrollTo"])
    }
    composable("settings/appearance/icon") {
        IconScreen(navController)
    }
    composable(
        route = "settings/appearance/aod_customized?scrollTo={scrollTo}",
        arguments = listOf(navArgument("scrollTo") { type = NavType.StringType; nullable = true; defaultValue = null }),
    ) {
        AodCustomizedScreen(navController, scrollTo = it.savedStateHandle["scrollTo"])
    }
    composable("settings/appearance/palette_picker") {
        PalettePickerScreen(navController)
    }
    composable(
        route = "settings/appearance/lyrics_animations?scrollTo={scrollTo}",
        arguments = listOf(navArgument("scrollTo") { type = NavType.StringType; nullable = true; defaultValue = null }),
    ) {
        LyricsAnimationSettings(navController, scrollTo = it.savedStateHandle["scrollTo"])
    }
    composable("settings/appearance/theme_creator") {
        ThemeCreatorScreen(navController)
    }
    composable(
        route = "settings/content?scrollTo={scrollTo}",
        arguments = listOf(navArgument("scrollTo") { type = NavType.StringType; nullable = true; defaultValue = null }),
    ) {
        ContentSettings(navController, scrollTo = it.savedStateHandle["scrollTo"])
    }
    composable(
        route = "settings/lyrics?scrollTo={scrollTo}",
        arguments = listOf(navArgument("scrollTo") { type = NavType.StringType; nullable = true; defaultValue = null }),
    ) {
        LyricsSettings(navController, scrollTo = it.savedStateHandle["scrollTo"])
    }
    composable(
        route = "settings/lyrics/providers?scrollTo={scrollTo}",
        arguments = listOf(navArgument("scrollTo") { type = NavType.StringType; nullable = true; defaultValue = null }),
    ) {
        LyricsProvidersSettings(navController, scrollTo = it.savedStateHandle["scrollTo"])
    }
    composable(
        route = "settings/lyrics/romanisation?scrollTo={scrollTo}",
        arguments = listOf(navArgument("scrollTo") { type = NavType.StringType; nullable = true; defaultValue = null }),
    ) {
        LyricsRomanisationSettings(navController, scrollTo = it.savedStateHandle["scrollTo"])
    }
    composable("settings/language_packs") {
        LanguagePackSettings(navController)
    }
    composable(
        route = "settings/internet?scrollTo={scrollTo}",
        arguments = listOf(navArgument("scrollTo") { type = NavType.StringType; nullable = true; defaultValue = null }),
    ) {
        InternetSettings(navController, it.savedStateHandle["scrollTo"])
    }
    composable(
        route = "settings/player?scrollTo={scrollTo}",
        arguments = listOf(navArgument("scrollTo") { type = NavType.StringType; nullable = true; defaultValue = null }),
    ) {
        PlayerSettings(navController, it.savedStateHandle["scrollTo"])
    }
    composable(
        route = "settings/sources?scrollTo={scrollTo}",
        arguments = listOf(navArgument("scrollTo") { type = NavType.StringType; nullable = true; defaultValue = null }),
    ) {
        SourceSettings(navController, it.savedStateHandle["scrollTo"])
    }
    composable(
        route = "settings/storage?scrollTo={scrollTo}",
        arguments = listOf(navArgument("scrollTo") { type = NavType.StringType; nullable = true; defaultValue = null }),
    ) {
        StorageSettings(navController, scrollTo = it.savedStateHandle["scrollTo"])
    }
    composable("settings/storage/export_songs") {
        ExportDownloadedSongsScreen(navController)
    }
    composable(
        route = "settings/downloads?scrollTo={scrollTo}",
        arguments = listOf(navArgument("scrollTo") { type = NavType.StringType; nullable = true; defaultValue = null }),
    ) {
        DownloadsSettings(navController, scrollTo = it.savedStateHandle["scrollTo"])
    }
    composable(
        route = "settings/privacy?scrollTo={scrollTo}",
        arguments = listOf(navArgument("scrollTo") { type = NavType.StringType; nullable = true; defaultValue = null }),
    ) {
        PrivacySettings(navController, it.savedStateHandle["scrollTo"])
    }
    composable(
        route = "settings/backup_restore?scrollTo={scrollTo}",
        arguments = listOf(navArgument("scrollTo") { type = NavType.StringType; nullable = true; defaultValue = null }),
    ) {
        BackupAndRestore(navController, scrollTo = it.savedStateHandle["scrollTo"])
    }
    composable(
        route = "settings/discord?scrollTo={scrollTo}",
        arguments = listOf(navArgument("scrollTo") { type = NavType.StringType; nullable = true; defaultValue = null }),
    ) {
        DiscordSettings(navController, it.savedStateHandle["scrollTo"])
    }
    composable(
        route = "settings/integration?scrollTo={scrollTo}",
        arguments = listOf(navArgument("scrollTo") { type = NavType.StringType; nullable = true; defaultValue = null }),
    ) {
        IntegrationScreen(navController, it.savedStateHandle["scrollTo"])
    }
    composable(
        route = "settings/tidal?scrollTo={scrollTo}",
        arguments = listOf(navArgument("scrollTo") { type = NavType.StringType; nullable = true; defaultValue = null }),
    ) {
        TidalSettings(navController, it.savedStateHandle["scrollTo"])
    }
    composable(
        route = "settings/qobuz?scrollTo={scrollTo}",
        arguments = listOf(navArgument("scrollTo") { type = NavType.StringType; nullable = true; defaultValue = null }),
    ) {
        QobuzSettings(navController, it.savedStateHandle["scrollTo"])
    }
    composable(
        route = "settings/deezer?scrollTo={scrollTo}",
        arguments = listOf(navArgument("scrollTo") { type = NavType.StringType; nullable = true; defaultValue = null }),
    ) {
        DeezerSettings(navController, scrollTo = it.savedStateHandle["scrollTo"])
    }
    composable(
        route = "settings/jiosaavn?scrollTo={scrollTo}",
        arguments = listOf(navArgument("scrollTo") { type = NavType.StringType; nullable = true; defaultValue = null }),
    ) {
        JioSettings(navController, scrollTo = it.savedStateHandle["scrollTo"])
    }
    composable(TIDAL_LOGIN_ROUTE) {
        TidalLoginScreen(navController)
    }
    composable(QOBUZ_LOGIN_ROUTE) {
        QobuzLoginScreen(navController)
    }
    composable(DEEZER_LOGIN_ROUTE) {
        DeezerLoginScreen(navController)
    }
    composable(APPLE_MUSIC_LOGIN_ROUTE) {
        AppleMusicLoginScreen(navController)
    }
    composable(
        route = "settings/applemusic",
    ) {
        AppleMusicSettings(navController)
    }
    composable(LASTFM_LOGIN_ROUTE) {
        LastFmLoginScreen(navController)
    }
    composable(LASTFM_LIBREFM_LOGIN_ROUTE) {
        LibreFmLoginScreen(navController)
    }
    composable(
        route = "settings/telegram?scrollTo={scrollTo}",
        arguments = listOf(navArgument("scrollTo") { type = NavType.StringType; nullable = true; defaultValue = null }),
    ) {
        TelegramSettings(navController, scrollTo = it.savedStateHandle["scrollTo"])
    }
    composable(TELEGRAM_LOGIN_ROUTE) {
        TelegramLoginScreen(navController)
    }
    composable(TELEGRAM_BROWSE_ROUTE) {
        TelegramBrowseScreen(navController)
    }
    composable(TELEGRAM_BOTS_ROUTE) {
        TelegramBotsScreen(navController)
    }
    composable(
        route = "$TELEGRAM_BOT_CHAT_ROUTE_BASE/{botId}",
        arguments = listOf(navArgument("botId") { type = NavType.StringType }),
    ) { entry ->
        TelegramBotChatScreen(
            botId = entry.arguments?.getString("botId").orEmpty(),
            navController = navController,
        )
    }
    composable(
        route = "settings/ai_integration?scrollTo={scrollTo}",
        arguments = listOf(navArgument("scrollTo") { type = NavType.StringType; nullable = true; defaultValue = null }),
    ) {
        AiIntegrationSettings(navController, scrollTo = it.savedStateHandle["scrollTo"])
    }
    composable("settings/music_together") {
        MusicTogetherScreen(navController)
    }
    composable(
        route = "settings/lastfm?scrollTo={scrollTo}",
        arguments = listOf(navArgument("scrollTo") { type = NavType.StringType; nullable = true; defaultValue = null }),
    ) {
        LastFMSettings(navController, scrollTo = it.savedStateHandle["scrollTo"])
    }
    composable("lastfm_dashboard") {
        LastFmDashboardScreen(navController)
    }
    composable(
        route = "settings/discord/experimental?scrollTo={scrollTo}",
        arguments = listOf(navArgument("scrollTo") { type = NavType.StringType; nullable = true; defaultValue = null }),
    ) {
        app.atf.media.ui.screens.settings
            .DiscordExperimental(navController, scrollTo = it.savedStateHandle["scrollTo"])
    }
    composable("settings/misc") {
        DebugSettings(navController)
    }
    composable("settings/logcat") {
        LogcatScreen(navController)
    }
    if (BuildConfig.UPDATER_AVAILABLE) {
        composable("settings/update") {
            UpdateScreen(navController, onUpToDate = onClearUpdateBadge)
        }
    }
    composable(
        route = "settings/changelog?channel={channel}",
        arguments =
            listOf(
                navArgument("channel") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
    ) { backStackEntry ->
        val channelName = backStackEntry.arguments?.getString("channel")
        val channel = UpdateChannel.fromStoredName(channelName, defaultUpdateChannel)
        ChangelogScreen(navController, channel = channel)
    }
    composable("settings/about") {
        AboutScreen(navController)
    }
    composable(PO_TOKEN_ROUTE) {
        PoTokenScreen(navController)
    }
    composable("customize_background") {
        CustomizeBackground(navController)
    }
    composable(
        route = "$LOGIN_ROUTE?$LOGIN_URL_ARGUMENT={$LOGIN_URL_ARGUMENT}",
        arguments =
            listOf(
                navArgument(LOGIN_URL_ARGUMENT) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
    ) { backStackEntry ->
        LoginScreen(
            navController,
            startUrl = backStackEntry.arguments?.getString(LOGIN_URL_ARGUMENT)?.let(Uri::decode),
        )
    }
}
