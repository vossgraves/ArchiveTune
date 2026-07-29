/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.R

@Composable
fun buildSettingsGroups(
    navController: NavController,
    isAndroid12OrLater: Boolean,
    hasUpdate: Boolean,
    context: Context,
): List<SettingsGroup> {
    val account =
        SettingsItem(
            key = "account",
            icon = painterResource(R.drawable.account),
            title = stringResource(R.string.account),
            subtitle = stringResource(R.string.settings_account_subtitle),
            accentColor = MaterialTheme.colorScheme.primary,
            keywords = listOf("account", "profile", "youtube", "sign in", "login", "logout"),
            onClick = { navController.navigate("settings/account") },
        )
    val stats =
        SettingsItem(
            key = "stats",
            icon = painterResource(R.drawable.stats),
            title = stringResource(R.string.settings_stats_title),
            subtitle = stringResource(R.string.settings_stats_subtitle),
            accentColor = MaterialTheme.colorScheme.primary,
            keywords = listOf("stats", "statistics", "listening", "history", "top", "most played", "time"),
            onClick = { navController.navigate("stats") },
        )
    val appearance =
        SettingsItem(
            key = "appearance",
            icon = painterResource(R.drawable.palette),
            title = stringResource(R.string.appearance),
            subtitle = stringResource(R.string.settings_appearance_subtitle),
            accentColor = MaterialTheme.colorScheme.secondary,
            keywords = listOf("appearance", "theme", "dark", "light", "color", "palette", "style", "design"),
            onClick = { navController.navigate("settings/appearance") },
        )
    val playback =
        SettingsItem(
            key = "playback",
            icon = painterResource(R.drawable.music_note),
            title = stringResource(R.string.settings_playback_title),
            subtitle = stringResource(R.string.settings_playback_subtitle),
            accentColor = MaterialTheme.colorScheme.tertiary,
            keywords = listOf("playback", "player", "audio", "quality", "equalizer", "eq", "volume", "crossfade", "gapless"),
            onClick = { navController.navigate("settings/player") },
        )
    val sources =
        SettingsItem(
            key = "sources",
            icon = painterResource(R.drawable.provider_tidal),
            title = stringResource(R.string.source_settings),
            subtitle = stringResource(R.string.source_settings_subtitle),
            accentColor = MaterialTheme.colorScheme.tertiary,
            keywords = listOf("source", "music source", "youtube music", "tidal", "qobuz", "provider", "streaming"),
            onClick = { navController.navigate("settings/sources") },
        )
    val lyrics =
        SettingsItem(
            key = "lyrics",
            icon = painterResource(R.drawable.lyrics),
            title = stringResource(R.string.lyrics),
            subtitle = stringResource(R.string.settings_lyrics_subtitle),
            accentColor = MaterialTheme.colorScheme.secondary,
            keywords = listOf("lyrics", "lyric", "subtitle", "text", "sing along", "lrc"),
            onClick = { navController.navigate("settings/lyrics") },
        )
    val content =
        SettingsItem(
            key = "content",
            icon = painterResource(R.drawable.language),
            title = stringResource(R.string.content),
            subtitle = stringResource(R.string.settings_content_subtitle),
            accentColor = MaterialTheme.colorScheme.primary,
            keywords = listOf("content", "language", "locale", "country", "region", "app language", "explicit", "age restricted", "age", "mature", "video"),
            onClick = { navController.navigate("settings/content") },
        )
    val languagePacks =
        SettingsItem(
            key = "language_packs",
            icon = painterResource(R.drawable.translate),
            title = stringResource(R.string.language_packs),
            subtitle = stringResource(R.string.settings_language_packs_subtitle),
            accentColor = MaterialTheme.colorScheme.secondary,
            keywords = listOf("language pack", "translation", "translate", "localization", "i18n"),
            onClick = { navController.navigate("settings/language_packs") },
        )
    val behavior =
        SettingsItem(
            key = "behavior",
            icon = painterResource(R.drawable.swipe),
            title = stringResource(R.string.settings_behavior_title),
            subtitle = stringResource(R.string.settings_behavior_subtitle),
            accentColor = MaterialTheme.colorScheme.primary,
            keywords = listOf("behavior", "privacy", "swipe", "gesture", "history", "cache", "data"),
            onClick = { navController.navigate("settings/privacy") },
        )
    val integration =
        SettingsItem(
            key = "integration",
            icon = painterResource(R.drawable.auto_awesome),
            title = stringResource(R.string.integration),
            subtitle = stringResource(R.string.settings_integration_subtitle),
            accentColor = MaterialTheme.colorScheme.secondary,
            keywords = listOf("integration", "lastfm", "last.fm", "scrobble", "scrobbling", "discord"),
            onClick = { navController.navigate("settings/integration") },
        )
    val aiIntegration =
        SettingsItem(
            key = "ai_integration",
            icon = painterResource(R.drawable.ai),
            title = stringResource(R.string.ai_integration),
            subtitle = stringResource(R.string.ai_integration_desc),
            accentColor = MaterialTheme.colorScheme.secondary,
            keywords = listOf("ai", "artificial intelligence", "chatgpt", "openai", "gemini", "llm", "ai integration"),
            onClick = { navController.navigate("settings/ai_integration") },
        )
    val internet =
        SettingsItem(
            key = "internet",
            icon = painterResource(R.drawable.wifi_proxy),
            title = stringResource(R.string.internet),
            subtitle = stringResource(R.string.settings_internet_subtitle),
            accentColor = MaterialTheme.colorScheme.tertiary,
            keywords = listOf("internet", "proxy", "vpn", "network", "wifi", "connection", "traffic"),
            onClick = { navController.navigate("settings/internet") },
        )
    val poToken =
        SettingsItem(
            key = "po_token",
            icon = painterResource(R.drawable.token),
            title = stringResource(R.string.po_token_generation),
            subtitle = stringResource(R.string.settings_po_token_subtitle),
            accentColor = MaterialTheme.colorScheme.secondary,
            keywords = listOf("po token", "potoken", "botguard", "youtube token", "playability"),
            onClick = { navController.navigate(PO_TOKEN_ROUTE) },
        )
    val storage =
        SettingsItem(
            key = "storage",
            icon = painterResource(R.drawable.storage),
            title = stringResource(R.string.storage),
            subtitle = stringResource(R.string.settings_storage_subtitle),
            accentColor = MaterialTheme.colorScheme.primary,
            keywords = listOf("storage", "download", "cache", "disk", "space", "memory", "path", "location"),
            onClick = { navController.navigate("settings/storage") },
        )
    val backupRestore =
        SettingsItem(
            key = "backup_restore",
            icon = painterResource(R.drawable.backup),
            title = stringResource(R.string.backup_restore),
            subtitle = stringResource(R.string.settings_backup_restore_subtitle),
            accentColor = MaterialTheme.colorScheme.primary,
            keywords = listOf("backup", "restore", "export", "import", "data", "save"),
            onClick = { navController.navigate("settings/backup_restore") },
        )
    val developerOptions =
        SettingsItem(
            key = "developer_options",
            icon = painterResource(R.drawable.experiment),
            title = stringResource(R.string.settings_developer_options_title),
            subtitle = stringResource(R.string.settings_developer_options_subtitle),
            accentColor = MaterialTheme.colorScheme.tertiary,
            keywords = listOf("developer", "debug", "experimental", "advanced", "logcat", "dev"),
            onClick = { navController.navigate("settings/misc") },
        )
    val defaultLinks =
        if (isAndroid12OrLater) {
            SettingsItem(
                key = "default_links",
                icon = painterResource(R.drawable.link),
                title = stringResource(R.string.default_links),
                subtitle = stringResource(R.string.open_supported_links),
                accentColor = MaterialTheme.colorScheme.secondary,
                keywords = listOf("default links", "links", "urls", "deep link", "supported links"),
                onClick = {
                    try {
                        val intent =
                            Intent(
                                Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
                                Uri.parse("package:${context.packageName}"),
                            ).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        when (e) {
                            is ActivityNotFoundException,
                            is SecurityException,
                            -> {
                                Toast
                                    .makeText(
                                        context,
                                        R.string.open_app_settings_error,
                                        Toast.LENGTH_LONG,
                                    ).show()
                            }

                            else -> {
                                Toast
                                    .makeText(
                                        context,
                                        R.string.open_app_settings_error,
                                        Toast.LENGTH_LONG,
                                    ).show()
                            }
                        }
                    }
                },
            )
        } else {
            null
        }
    val updates =
        if (BuildConfig.UPDATER_AVAILABLE) {
            SettingsItem(
                key = "updates",
                icon = painterResource(R.drawable.update),
                title = stringResource(R.string.updates),
                keywords = listOf("update", "upgrade", "version", "new version", "release", "canary", "stable"),
                subtitle =
                    if (hasUpdate) {
                        stringResource(R.string.new_version_available)
                    } else {
                        stringResource(R.string.settings_updates_subtitle)
                    },
                showUpdateIndicator = hasUpdate,
                accentColor =
                    if (hasUpdate) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                badge = if (hasUpdate) "v${BuildConfig.VERSION_NAME}" else BuildConfig.VERSION_NAME,
                onClick = { navController.navigate("settings/update") },
            )
        } else {
            null
        }
    val about =
        SettingsItem(
            key = "about",
            icon = painterResource(R.drawable.info),
            title = stringResource(R.string.about),
            subtitle = stringResource(R.string.settings_about_subtitle),
            accentColor = MaterialTheme.colorScheme.secondary,
            keywords = listOf("about", "info", "version", "license", "credits", "contributors", "changelog"),
            onClick = { navController.navigate("settings/about") },
        )

    // Search-only rows for individual preferences inside the busiest sub-screens. They are appended
    // to their parent group so search results appear under a heading that makes sense, and are
    // filtered out entirely while the search field is empty.
    val deepEntries = buildDeepSettingsEntries(navController)

    return listOf(
        SettingsGroup(
            title = stringResource(R.string.settings),
            items = listOf(account, stats),
        ),
        SettingsGroup(
            title = stringResource(R.string.settings_section_player_content),
            items =
                listOf(appearance, playback, sources, lyrics, languagePacks, content, behavior) +
                    deepEntries.playerAndContent,
        ),
        SettingsGroup(
            title = stringResource(R.string.integration),
            items = listOf(integration, aiIntegration, internet, poToken) + deepEntries.integration,
        ),
        SettingsGroup(
            title = stringResource(R.string.storage),
            items = listOf(storage, backupRestore) + deepEntries.storage,
        ),
        SettingsGroup(
            title = stringResource(R.string.about),
            items =
                buildList {
                    add(developerOptions)
                    defaultLinks?.let(::add)
                    updates?.let(::add)
                    add(about)
                },
        ),
    )
}

/**
 * Builds a search-only result that points at a single preference buried inside a sub-screen.
 *
 * Selecting it navigates to [screen] and asks that screen to scroll the [anchor] preference into
 * view and flash it, so "crossfade" lands on the crossfade toggle rather than merely on the screen
 * that happens to contain it. These entries are hidden unless the user is searching -- see
 * [SettingsItem.deepOnly].
 */
@Composable
private fun deepEntry(
    navController: NavController,
    screen: String,
    anchor: String,
    icon: Painter,
    title: String,
    parentTitle: String,
    accentColor: Color,
    keywords: List<String>,
) = SettingsItem(
    // Namespaced so a deep entry can never collide with the top-level key of the same name.
    key = "deep:$screen:$anchor",
    icon = icon,
    title = title,
    subtitle = stringResource(R.string.settings_search_located_in, parentTitle),
    accentColor = accentColor,
    keywords = keywords,
    deepOnly = true,
    onClick = {
        SettingsAnchorRequest.request(screen, anchor)
        navController.navigate(screen)
    },
)

/**
 * Individual preferences from the anchor-capable settings screens, exposed to search.
 *
 * Deliberately not exhaustive: these are the preferences users actually hunt for. Coverage is
 * bounded by the anchors declared in [SettingsAnchors], and every anchor here must be applied to a
 * real preference with `anchors.anchor(...)` or the row will scroll nowhere. The `screen` must also
 * be a route registered in the nav graph, since selecting a result navigates to it directly.
 */
/**
 * Deep search entries split by the group they belong under, so a storage preference surfaces below
 * the Storage heading rather than under Player.
 */
internal data class DeepSettingsEntries(
    val playerAndContent: List<SettingsItem>,
    val integration: List<SettingsItem>,
    val storage: List<SettingsItem>,
)

@Composable
internal fun buildDeepSettingsEntries(navController: NavController): DeepSettingsEntries {
    val playerIcon = painterResource(R.drawable.music_note)
    val appearanceIcon = painterResource(R.drawable.palette)
    val storageIcon = painterResource(R.drawable.storage)
    val playerTitle = stringResource(R.string.settings_playback_title)
    val appearanceTitle = stringResource(R.string.appearance)
    val storageTitle = stringResource(R.string.storage)
    val playerAccent = MaterialTheme.colorScheme.tertiary
    val appearanceAccent = MaterialTheme.colorScheme.secondary
    val storageAccent = MaterialTheme.colorScheme.primary

    // Icons/titles deliberately mirror each screen's own top-level entry, so a deep result looks
    // like the screen it lands on.
    val contentIcon = painterResource(R.drawable.language)
    val privacyIcon = painterResource(R.drawable.swipe)
    val lyricsIcon = painterResource(R.drawable.lyrics)
    val internetIcon = painterResource(R.drawable.wifi_proxy)
    val backupIcon = painterResource(R.drawable.backup)
    val importIcon = painterResource(R.drawable.playlist_import)
    val contentTitle = stringResource(R.string.content)
    val privacyTitle = stringResource(R.string.settings_behavior_title)
    val lyricsScreenTitle = stringResource(R.string.lyrics)
    val internetTitle = stringResource(R.string.internet)
    val backupParentTitle = stringResource(R.string.backup_restore)
    val integrationTitle = stringResource(R.string.integration)

    val playerAndContent = listOf(
        deepEntry(
            navController = navController,
            screen = SettingsAnchorScreens.PLAYER,
            anchor = SettingsAnchors.CROSSFADE,
            icon = playerIcon,
            title = stringResource(R.string.audio_crossfade_title),
            parentTitle = playerTitle,
            accentColor = playerAccent,
            keywords = listOf("crossfade", "fade", "blend", "overlap", "transition", "mix"),
        ),
        deepEntry(
            navController = navController,
            screen = SettingsAnchorScreens.PLAYER,
            anchor = SettingsAnchors.GAPLESS,
            icon = playerIcon,
            title = stringResource(R.string.crossfade_gapless_title),
            parentTitle = playerTitle,
            accentColor = playerAccent,
            keywords = listOf("gapless", "gap", "silence between", "continuous", "seamless"),
        ),
        deepEntry(
            navController = navController,
            screen = SettingsAnchorScreens.PLAYER,
            anchor = SettingsAnchors.SKIP_SILENCE,
            icon = playerIcon,
            title = stringResource(R.string.skip_silence),
            parentTitle = playerTitle,
            accentColor = playerAccent,
            keywords = listOf("skip silence", "silence", "quiet", "trim", "blank"),
        ),
        deepEntry(
            navController = navController,
            screen = SettingsAnchorScreens.PLAYER,
            anchor = SettingsAnchors.AUDIO_NORMALIZATION,
            icon = playerIcon,
            title = stringResource(R.string.audio_normalization),
            parentTitle = playerTitle,
            accentColor = playerAccent,
            keywords = listOf("normalization", "normalise", "loudness", "volume", "replaygain", "gain"),
        ),
        deepEntry(
            navController = navController,
            screen = SettingsAnchorScreens.PLAYER,
            anchor = SettingsAnchors.PERSISTENT_QUEUE,
            icon = playerIcon,
            title = stringResource(R.string.persistent_queue),
            parentTitle = playerTitle,
            accentColor = playerAccent,
            keywords = listOf("queue", "persistent queue", "restore queue", "resume", "remember"),
        ),
        deepEntry(
            navController = navController,
            screen = SettingsAnchorScreens.PLAYER,
            anchor = SettingsAnchors.EXTERNAL_DOWNLOADER,
            icon = playerIcon,
            title = stringResource(R.string.external_downloader),
            parentTitle = playerTitle,
            accentColor = playerAccent,
            keywords = listOf("external downloader", "downloader", "download app", "third party"),
        ),
        deepEntry(
            navController = navController,
            screen = SettingsAnchorScreens.APPEARANCE,
            anchor = SettingsAnchors.DYNAMIC_THEME,
            icon = appearanceIcon,
            title = stringResource(R.string.enable_dynamic_theme),
            parentTitle = appearanceTitle,
            accentColor = appearanceAccent,
            keywords = listOf("dynamic theme", "dynamic color", "material you", "monet", "wallpaper", "accent"),
        ),
        deepEntry(
            navController = navController,
            screen = SettingsAnchorScreens.APPEARANCE,
            anchor = SettingsAnchors.DARK_THEME,
            icon = appearanceIcon,
            title = stringResource(R.string.dark_theme),
            parentTitle = appearanceTitle,
            accentColor = appearanceAccent,
            keywords = listOf("dark theme", "dark mode", "night", "light mode", "theme"),
        ),
        deepEntry(
            navController = navController,
            screen = SettingsAnchorScreens.APPEARANCE,
            anchor = SettingsAnchors.PURE_BLACK,
            icon = appearanceIcon,
            title = stringResource(R.string.pure_black),
            parentTitle = appearanceTitle,
            accentColor = appearanceAccent,
            keywords = listOf("pure black", "amoled", "oled", "true black", "contrast"),
        ),
        deepEntry(
            navController = navController,
            screen = SettingsAnchorScreens.APPEARANCE,
            anchor = SettingsAnchors.APP_ICON,
            icon = appearanceIcon,
            title = stringResource(R.string.app_icon),
            parentTitle = appearanceTitle,
            accentColor = appearanceAccent,
            keywords = listOf("app icon", "icon", "launcher", "shortcut", "logo"),
        ),
        deepEntry(
            navController = navController,
            screen = SettingsAnchorScreens.APPEARANCE,
            anchor = SettingsAnchors.FONT,
            icon = appearanceIcon,
            title = stringResource(R.string.font_preference),
            parentTitle = appearanceTitle,
            accentColor = appearanceAccent,
            keywords = listOf("font", "typeface", "typography", "text size", "letters"),
        ),
        deepEntry(
            navController = navController,
            screen = SettingsAnchorScreens.APPEARANCE,
            anchor = SettingsAnchors.HIGH_REFRESH_RATE,
            icon = appearanceIcon,
            title = stringResource(R.string.force_high_refresh_rate),
            parentTitle = appearanceTitle,
            accentColor = appearanceAccent,
            keywords = listOf("refresh rate", "hz", "120hz", "90hz", "smooth", "frame rate", "fps"),
        ),
    )

    val contentAndPrivacy = listOf(
        deepEntry(
            navController = navController,
            screen = SettingsAnchorScreens.CONTENT,
            anchor = SettingsAnchors.HIDE_EXPLICIT,
            icon = contentIcon,
            title = stringResource(R.string.hide_explicit),
            parentTitle = contentTitle,
            accentColor = playerAccent,
            keywords = listOf("explicit", "hide explicit", "clean", "censor", "parental", "family"),
        ),
        deepEntry(
            navController = navController,
            screen = SettingsAnchorScreens.CONTENT,
            anchor = SettingsAnchors.HIDE_VIDEO,
            icon = contentIcon,
            title = stringResource(R.string.hide_video),
            parentTitle = contentTitle,
            accentColor = playerAccent,
            keywords = listOf("video", "hide video", "music video", "audio only"),
        ),
        deepEntry(
            navController = navController,
            screen = SettingsAnchorScreens.CONTENT,
            anchor = SettingsAnchors.ALLOW_AGE_RESTRICTED,
            icon = contentIcon,
            title = stringResource(R.string.allow_age_restricted),
            parentTitle = contentTitle,
            accentColor = playerAccent,
            keywords = listOf("age restricted", "age", "restricted", "18", "mature", "sign in"),
        ),
        deepEntry(
            navController = navController,
            screen = SettingsAnchorScreens.CONTENT,
            anchor = SettingsAnchors.APP_LANGUAGE,
            icon = contentIcon,
            title = stringResource(R.string.app_language),
            parentTitle = contentTitle,
            accentColor = playerAccent,
            keywords = listOf("language", "app language", "locale", "translation", "region"),
        ),
        deepEntry(
            navController = navController,
            screen = SettingsAnchorScreens.PRIVACY,
            anchor = SettingsAnchors.PAUSE_LISTEN_HISTORY,
            icon = privacyIcon,
            title = stringResource(R.string.pause_listen_history),
            parentTitle = privacyTitle,
            accentColor = playerAccent,
            keywords = listOf("listen history", "pause history", "history", "tracking", "privacy", "incognito"),
        ),
        deepEntry(
            navController = navController,
            screen = SettingsAnchorScreens.PRIVACY,
            anchor = SettingsAnchors.PAUSE_SEARCH_HISTORY,
            icon = privacyIcon,
            title = stringResource(R.string.pause_search_history),
            parentTitle = privacyTitle,
            accentColor = playerAccent,
            keywords = listOf("search history", "pause search", "recent searches", "privacy", "clear"),
        ),
        deepEntry(
            navController = navController,
            screen = SettingsAnchorScreens.PRIVACY,
            anchor = SettingsAnchors.HAPTICS,
            icon = privacyIcon,
            title = stringResource(R.string.haptics),
            parentTitle = privacyTitle,
            accentColor = playerAccent,
            keywords = listOf("haptics", "vibration", "vibrate", "feedback", "touch"),
        ),
        deepEntry(
            navController = navController,
            screen = SettingsAnchorScreens.PRIVACY,
            anchor = SettingsAnchors.DISABLE_SCREENSHOT,
            icon = privacyIcon,
            title = stringResource(R.string.disable_screenshot),
            parentTitle = privacyTitle,
            accentColor = playerAccent,
            keywords = listOf("screenshot", "disable screenshot", "screen capture", "secure", "privacy"),
        ),
        deepEntry(
            navController = navController,
            screen = SettingsAnchorScreens.LYRICS,
            anchor = SettingsAnchors.LYRICS_MODE,
            icon = lyricsIcon,
            title = stringResource(R.string.lyrics_mode),
            parentTitle = lyricsScreenTitle,
            accentColor = playerAccent,
            keywords = listOf("lyrics mode", "lyrics", "synced", "karaoke", "style"),
        ),
        deepEntry(
            navController = navController,
            screen = SettingsAnchorScreens.LYRICS,
            anchor = SettingsAnchors.LYRICS_ANIMATION,
            icon = lyricsIcon,
            title = stringResource(R.string.lyrics_animation_style),
            parentTitle = lyricsScreenTitle,
            accentColor = playerAccent,
            keywords = listOf("lyrics animation", "animation", "transition", "effect", "motion"),
        ),
        deepEntry(
            navController = navController,
            screen = SettingsAnchorScreens.LYRICS,
            anchor = SettingsAnchors.LYRICS_AUTO_SCROLL,
            icon = lyricsIcon,
            title = stringResource(R.string.lyrics_auto_scroll),
            parentTitle = lyricsScreenTitle,
            accentColor = playerAccent,
            keywords = listOf("auto scroll", "lyrics scroll", "follow", "scroll"),
        ),
        deepEntry(
            navController = navController,
            screen = SettingsAnchorScreens.LYRICS,
            anchor = SettingsAnchors.LYRICS_LINE_BLUR,
            icon = lyricsIcon,
            title = stringResource(R.string.lyrics_line_blur),
            parentTitle = lyricsScreenTitle,
            accentColor = playerAccent,
            keywords = listOf("blur", "line blur", "lyrics blur", "focus", "depth"),
        ),
    )

    val integration = listOf(
        deepEntry(
            navController = navController,
            screen = SettingsAnchorScreens.INTERNET,
            anchor = SettingsAnchors.DNS_OVER_HTTPS,
            icon = internetIcon,
            title = stringResource(R.string.dns_over_https),
            parentTitle = internetTitle,
            accentColor = storageAccent,
            keywords = listOf("dns", "doh", "dns over https", "unblock", "bypass", "network"),
        ),
        deepEntry(
            navController = navController,
            screen = SettingsAnchorScreens.INTERNET,
            anchor = SettingsAnchors.PROXY,
            icon = internetIcon,
            title = stringResource(R.string.enable_proxy),
            parentTitle = internetTitle,
            accentColor = storageAccent,
            keywords = listOf("proxy", "socks", "http proxy", "vpn", "network", "bypass"),
        ),
        deepEntry(
            navController = navController,
            screen = SettingsAnchorScreens.INTEGRATION,
            anchor = SettingsAnchors.CROSS_SERVICE_IMPORT,
            icon = importIcon,
            title = stringResource(R.string.cross_service_import_entry_title),
            parentTitle = integrationTitle,
            accentColor = storageAccent,
            // Users search by the service they are migrating FROM, so each supported one is a
            // keyword in its own right. Deliberately no "spotify": it is not a supported source,
            // and matching it would send users to an importer that rejects their URL.
            keywords = listOf(
                "import", "import playlist", "playlist import", "migrate", "transfer",
                "apple music", "amazon music", "tidal", "deezer", "url",
            ),
        ),
    )

    val storage = listOf(
        deepEntry(
            navController = navController,
            screen = SettingsAnchorScreens.STORAGE,
            anchor = SettingsAnchors.EXPORT_DOWNLOADS,
            icon = storageIcon,
            title = stringResource(R.string.export_downloaded_songs),
            parentTitle = storageTitle,
            accentColor = storageAccent,
            keywords = listOf("export", "copy songs", "save to folder", "sd card", "usb", "backup songs", "mp3"),
        ),
        deepEntry(
            navController = navController,
            screen = SettingsAnchorScreens.STORAGE,
            anchor = SettingsAnchors.CLEAR_DOWNLOADS,
            icon = storageIcon,
            title = stringResource(R.string.clear_all_downloads),
            parentTitle = storageTitle,
            accentColor = storageAccent,
            keywords = listOf("clear downloads", "delete downloads", "remove downloads", "free space"),
        ),
        deepEntry(
            navController = navController,
            screen = SettingsAnchorScreens.STORAGE,
            anchor = SettingsAnchors.SONG_CACHE_SIZE,
            icon = storageIcon,
            title = stringResource(R.string.max_song_cache_size),
            parentTitle = storageTitle,
            accentColor = storageAccent,
            keywords = listOf("song cache", "cache size", "music cache", "limit", "storage limit"),
        ),
        deepEntry(
            navController = navController,
            screen = SettingsAnchorScreens.STORAGE,
            anchor = SettingsAnchors.CLEAR_SONG_CACHE,
            icon = storageIcon,
            title = stringResource(R.string.clear_song_cache),
            parentTitle = storageTitle,
            accentColor = storageAccent,
            keywords = listOf("clear cache", "clear song cache", "empty cache", "free space"),
        ),
        deepEntry(
            navController = navController,
            screen = SettingsAnchorScreens.STORAGE,
            anchor = SettingsAnchors.IMAGE_CACHE_SIZE,
            icon = storageIcon,
            title = stringResource(R.string.max_image_cache_size),
            parentTitle = storageTitle,
            accentColor = storageAccent,
            keywords = listOf("image cache", "artwork cache", "thumbnail cache", "cover art", "cache size"),
        ),
        deepEntry(
            navController = navController,
            screen = SettingsAnchorScreens.STORAGE,
            anchor = SettingsAnchors.SMART_TRIMMER,
            icon = storageIcon,
            title = stringResource(R.string.smart_trimmer),
            parentTitle = storageTitle,
            accentColor = storageAccent,
            keywords = listOf("smart trimmer", "trimmer", "auto clean", "prune", "automatic cleanup"),
        ),
        deepEntry(
            navController = navController,
            screen = SettingsAnchorScreens.BACKUP,
            anchor = SettingsAnchors.BACKUP,
            icon = backupIcon,
            title = stringResource(R.string.action_backup),
            parentTitle = backupParentTitle,
            accentColor = storageAccent,
            keywords = listOf("backup", "export settings", "save data", "database", "migrate"),
        ),
        deepEntry(
            navController = navController,
            screen = SettingsAnchorScreens.BACKUP,
            anchor = SettingsAnchors.RESTORE,
            icon = backupIcon,
            title = stringResource(R.string.action_restore),
            parentTitle = backupParentTitle,
            accentColor = storageAccent,
            keywords = listOf("restore", "import", "recover", "load backup", "migrate"),
        ),
    )

    return DeepSettingsEntries(
        playerAndContent = playerAndContent + contentAndPrivacy,
        integration = integration,
        storage = storage,
    )
}
