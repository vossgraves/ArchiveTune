/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.constants.UpdateChannel
import moe.rukamori.archivetune.defaultUpdateChannel
import moe.rukamori.archivetune.musicrecognition.MusicRecognitionRoute
import moe.rukamori.archivetune.musicrecognition.MusicRecognitionDetailsRoute
import moe.rukamori.archivetune.ui.screens.BrowseScreen
import moe.rukamori.archivetune.ui.screens.artist.ArtistAlbumsScreen
import moe.rukamori.archivetune.ui.screens.artist.ArtistItemsScreen
import moe.rukamori.archivetune.ui.screens.artist.ArtistScreen
import moe.rukamori.archivetune.ui.screens.artist.ArtistSongsScreen
import moe.rukamori.archivetune.ui.screens.library.LibraryScreen
import moe.rukamori.archivetune.ui.screens.library.LocalSongScreen
import moe.rukamori.archivetune.ui.screens.musicrecognition.MusicRecognitionScreen
import moe.rukamori.archivetune.ui.screens.musicrecognition.MusicRecognitionDetailsScreen
import moe.rukamori.archivetune.ui.screens.playlist.AutoPlaylistScreen
import moe.rukamori.archivetune.ui.screens.playlist.CachePlaylistScreen
import moe.rukamori.archivetune.ui.screens.playlist.LocalPlaylistScreen
import moe.rukamori.archivetune.ui.screens.playlist.OnlinePlaylistScreen
import moe.rukamori.archivetune.ui.screens.playlist.SpotifyPlaylistScreen
import moe.rukamori.archivetune.ui.screens.playlist.TopPlaylistScreen
import moe.rukamori.archivetune.ui.screens.search.OnlineSearchResult
import moe.rukamori.archivetune.ui.screens.search.OnlineSearchResultArgument
import moe.rukamori.archivetune.ui.screens.search.OnlineSearchResultRoute
import moe.rukamori.archivetune.ui.screens.search.OnlineSearchResultRoutePrefix
import moe.rukamori.archivetune.ui.screens.search.SearchScreen
import moe.rukamori.archivetune.ui.screens.settings.AboutScreen
import moe.rukamori.archivetune.ui.screens.settings.AccountSettings
import moe.rukamori.archivetune.ui.screens.settings.AiIntegrationSettings
import moe.rukamori.archivetune.ui.screens.settings.AodCustomizedScreen
import moe.rukamori.archivetune.ui.screens.settings.AppearanceExtrasSettings
import moe.rukamori.archivetune.ui.screens.settings.NavigationBarSettings
import moe.rukamori.archivetune.ui.screens.settings.AppearanceSettings
import moe.rukamori.archivetune.ui.screens.settings.BackupAndRestore
import moe.rukamori.archivetune.ui.screens.settings.ChangelogScreen
import moe.rukamori.archivetune.ui.screens.settings.ChiperSettings
import moe.rukamori.archivetune.ui.screens.settings.ContentSettings
import moe.rukamori.archivetune.ui.screens.settings.CustomizeBackground
import moe.rukamori.archivetune.ui.screens.settings.DebugSettings
import moe.rukamori.archivetune.ui.screens.settings.DiscordSettings
import moe.rukamori.archivetune.ui.screens.settings.ExportDownloadedSongsScreen
import moe.rukamori.archivetune.ui.screens.settings.HiddenPlaylistsScreen
import moe.rukamori.archivetune.ui.screens.settings.IconScreen
import moe.rukamori.archivetune.ui.screens.settings.IntegrationScreen
import moe.rukamori.archivetune.ui.screens.settings.InternetSettings
import moe.rukamori.archivetune.ui.screens.settings.TidalSettings
import moe.rukamori.archivetune.ui.screens.settings.QobuzSettings
import moe.rukamori.archivetune.ui.screens.settings.DeezerSettings
import moe.rukamori.archivetune.ui.screens.settings.JioSettings
import moe.rukamori.archivetune.ui.screens.settings.TidalLoginScreen
import moe.rukamori.archivetune.ui.screens.settings.TIDAL_LOGIN_ROUTE
import moe.rukamori.archivetune.ui.screens.settings.QobuzLoginScreen
import moe.rukamori.archivetune.ui.screens.settings.QOBUZ_LOGIN_ROUTE
import moe.rukamori.archivetune.ui.screens.settings.DeezerLoginScreen
import moe.rukamori.archivetune.ui.screens.settings.DEEZER_LOGIN_ROUTE
import moe.rukamori.archivetune.ui.screens.settings.TELEGRAM_LOGIN_ROUTE
import moe.rukamori.archivetune.ui.screens.settings.TelegramLoginScreen
import moe.rukamori.archivetune.ui.screens.settings.TelegramSettings
import moe.rukamori.archivetune.ui.screens.settings.LastFMSettings
import moe.rukamori.archivetune.ui.screens.settings.LastFmDashboardScreen
import moe.rukamori.archivetune.ui.screens.settings.LanguagePackSettings
import moe.rukamori.archivetune.ui.screens.settings.LogcatScreen
import moe.rukamori.archivetune.ui.screens.settings.LyricsAnimationSettings
import moe.rukamori.archivetune.ui.screens.settings.LyricsSettings
import moe.rukamori.archivetune.ui.screens.settings.LyricsProvidersSettings
import moe.rukamori.archivetune.ui.screens.settings.LyricsRomanisationSettings
import moe.rukamori.archivetune.ui.screens.settings.MusicTogetherScreen
import moe.rukamori.archivetune.ui.screens.settings.PO_TOKEN_ROUTE
import moe.rukamori.archivetune.ui.screens.settings.PalettePickerScreen
import moe.rukamori.archivetune.ui.screens.settings.PlayerSettings
import moe.rukamori.archivetune.ui.screens.settings.PoTokenScreen
import moe.rukamori.archivetune.ui.screens.settings.PrivacySettings
import moe.rukamori.archivetune.ui.screens.settings.SettingsScreen
import moe.rukamori.archivetune.ui.screens.settings.SourceSettings
import moe.rukamori.archivetune.ui.screens.settings.StorageSettings
import moe.rukamori.archivetune.ui.screens.settings.DownloadsSettings
import moe.rukamori.archivetune.ui.screens.settings.ThemeCreatorScreen
import moe.rukamori.archivetune.ui.screens.settings.UpdateScreen
import moe.rukamori.archivetune.viewmodels.OnlineSearchSort

@OptIn(ExperimentalMaterial3Api::class)
fun NavGraphBuilder.navigationBuilder(
    navController: NavHostController,
    scrollBehavior: TopAppBarScrollBehavior,
    latestVersionName: () -> String,
    disableAnimations: Boolean = false,
    onClearUpdateBadge: () -> Unit = {},
    onSearchQuery: (String) -> Unit = {},
    homeScrollConnection: NestedScrollConnection? = null,
    searchScrollConnection: NestedScrollConnection? = null,
    onlineSearchSort: OnlineSearchSort = OnlineSearchSort.DEFAULT,
) {
    composable(Screens.Home.route) {
        HomeScreen(navController, headerScrollConnection = homeScrollConnection)
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
            headerScrollConnection = searchScrollConnection,
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
    composable("settings/appearance/extras") {
        AppearanceExtrasSettings(navController)
    }
    composable("settings/appearance/navigation_bar") {
        NavigationBarSettings(navController)
    }
    composable("settings/appearance/icon") {
        IconScreen(navController)
    }
    composable("settings/appearance/aod_customized") {
        AodCustomizedScreen(navController)
    }
    composable("settings/appearance/palette_picker") {
        PalettePickerScreen(navController)
    }
    composable("settings/appearance/lyrics_animations") {
        LyricsAnimationSettings(navController)
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
    composable("settings/lyrics/providers") {
        LyricsProvidersSettings(navController)
    }
    composable("settings/lyrics/romanisation") {
        LyricsRomanisationSettings(navController)
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
    composable("settings/player/chiper") {
        ChiperSettings(navController)
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
    composable("settings/deezer") {
        DeezerSettings(navController)
    }
    composable("settings/jiosaavn") {
        JioSettings(navController)
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
    composable("settings/telegram") {
        TelegramSettings(navController)
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
    composable("settings/ai_integration") {
        AiIntegrationSettings(navController)
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
    composable("settings/discord/experimental") {
        moe.rukamori.archivetune.ui.screens.settings
            .DiscordExperimental(navController)
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
