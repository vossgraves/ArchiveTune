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
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.ArchiveTuneCanvasKey
import moe.rukamori.archivetune.constants.AudioNormalizationKey
import moe.rukamori.archivetune.constants.AudioOffload
import moe.rukamori.archivetune.constants.AutoDownloadOnLikeKey
import moe.rukamori.archivetune.constants.AutoSkipNextOnErrorKey
import moe.rukamori.archivetune.constants.AutoStartOnBluetoothKey
import moe.rukamori.archivetune.constants.BackdropEnabledKey
import moe.rukamori.archivetune.constants.CropThumbnailToSquareKey
import moe.rukamori.archivetune.constants.CrossfadeEnabledKey
import moe.rukamori.archivetune.constants.CrossfadeGaplessKey
import moe.rukamori.archivetune.constants.DisableAnimationsKey
import moe.rukamori.archivetune.constants.DisableBlurKey
import moe.rukamori.archivetune.constants.DisableScreenshotKey
import moe.rukamori.archivetune.constants.DynamicThemeKey
import moe.rukamori.archivetune.constants.EnableDiscordRPCKey
import moe.rukamori.archivetune.constants.EnableLastFMScrobblingKey
import moe.rukamori.archivetune.constants.EnableTranslatorKey
import moe.rukamori.archivetune.constants.ExternalDownloaderEnabledKey
import moe.rukamori.archivetune.constants.ForceHighRefreshRateKey
import moe.rukamori.archivetune.constants.HideAiMixKey
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.constants.HideNavigationBarLabelsKey
import moe.rukamori.archivetune.constants.HidePlayerThumbnailKey
import moe.rukamori.archivetune.constants.HideVideoKey
import moe.rukamori.archivetune.constants.EnableHapticFeedbackKey
import moe.rukamori.archivetune.constants.ListenBrainzEnabledKey
import moe.rukamori.archivetune.constants.LowDataModeKey
import moe.rukamori.archivetune.constants.LyricsClickKey
import moe.rukamori.archivetune.constants.LyricsScrollKey
import moe.rukamori.archivetune.constants.NavigationBarFrostedBlurKey
import moe.rukamori.archivetune.constants.NetworkMeteredKey
import moe.rukamori.archivetune.constants.PauseListenHistoryKey
import moe.rukamori.archivetune.constants.PauseOnDeviceMuteKey
import moe.rukamori.archivetune.constants.PauseSearchHistoryKey
import moe.rukamori.archivetune.constants.PermanentShuffleKey
import moe.rukamori.archivetune.constants.PersistentQueueKey
import moe.rukamori.archivetune.constants.ProxyEnabledKey
import moe.rukamori.archivetune.constants.PureBlackKey
import moe.rukamori.archivetune.constants.RandomThemeOnStartupKey
import moe.rukamori.archivetune.constants.SeekExtraSeconds
import moe.rukamori.archivetune.constants.ShowHomeCategoryChipsKey
import moe.rukamori.archivetune.constants.ShowLyricsKey
import moe.rukamori.archivetune.constants.ShowLyricsPlayerControlsKey
import moe.rukamori.archivetune.constants.ShowPlayerVolumeBarKey
import moe.rukamori.archivetune.constants.ShowSpotifyPlaylistsKey
import moe.rukamori.archivetune.constants.SkipSilenceKey
import moe.rukamori.archivetune.constants.SmartTrimmerKey
import moe.rukamori.archivetune.constants.StopMusicOnTaskClearKey
import moe.rukamori.archivetune.constants.SyncPlaybackToYouTubeHistoryKey
import moe.rukamori.archivetune.constants.SwipeToSongKey
import moe.rukamori.archivetune.constants.TelegramLosslessOnlyKey
import moe.rukamori.archivetune.constants.TidalArtworkFallbackEnabledKey
import moe.rukamori.archivetune.constants.TidalEnabledKey
import moe.rukamori.archivetune.constants.TranslateLyricsKey
import moe.rukamori.archivetune.constants.UseLyricsV2Key
import moe.rukamori.archivetune.constants.UseSystemFontKey
import moe.rukamori.archivetune.constants.WakelockKey
import moe.rukamori.archivetune.utils.rememberPreference

@Composable
private fun SearchResultSwitch(
    key: androidx.datastore.preferences.core.Preferences.Key<Boolean>,
    defaultValue: Boolean,
) {
    val (checked, onCheckedChange) = rememberPreference(key, defaultValue)
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
    )
}

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
            keywords = listOf("account", "profile", "youtube", "sign in", "login", "logout", "token", "hidden", "playlist", "channels", "switch account"),
            onClick = { navController.navigate("settings/account") },
            children = listOf(
                SettingsChild("Account switcher", "account_switcher", listOf("account switcher", "switch account", "multiple accounts", "saved accounts", "account channels")),
                SettingsChild("Hidden playlists", "hidden_playlists", listOf("hidden", "hidden playlists", "hide playlist", "hidden music", "private playlist")),
                SettingsChild("Tap to show tokens", "tap_to_show_tokens", listOf("token", "tokens", "show token", "po token", "innertube", "visitor data", "datasync", "credentials", "advanced login")),
                SettingsChild("Save current account", "save_current_account", listOf("save account", "remember account", "persist account")),
                SettingsChild("Logout", "account_logout", listOf("logout", "log out", "sign out", "disconnect")),
            ),
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
            children = listOf(
                SettingsChild("Dynamic theme", "dynamic_theme", listOf("dynamic theme", "material you", "dynamic color")) { SearchResultSwitch(DynamicThemeKey, false) },
                SettingsChild("Random theme on startup", "random_theme_on_startup", listOf("random theme", "random color", "shuffle theme")) { SearchResultSwitch(RandomThemeOnStartupKey, false) },
                SettingsChild("Dark theme", "dark_theme", listOf("dark", "dark theme", "night", "amoled")),
                SettingsChild("Pure black", "pure_black", listOf("pure black", "amoled", "oled", "black background")) { SearchResultSwitch(PureBlackKey, false) },
                SettingsChild("Color palette", "color_palette", listOf("color palette", "accent color", "theme color", "color")),
                SettingsChild("Color source", "color_source", listOf("color source", "color", "dynamic color", "material you")),
                SettingsChild("App icon", "app_icon", listOf("icon", "app icon", "icon pack", "launcher icon")),
                SettingsChild("Disable blur", "disable_blur", listOf("blur", "disable blur", "no blur", "performance")) { SearchResultSwitch(DisableBlurKey, false) },
                SettingsChild("Blur intensity", "blur_intensity", listOf("blur intensity", "blur amount", "blur level", "blur radius")),
                SettingsChild("Backdrop blur", "backdrop_blur", listOf("backdrop", "backdrop blur", "background blur", "frosted")) { SearchResultSwitch(BackdropEnabledKey, false) },
                SettingsChild("Font preference", "font_preference", listOf("font", "font style", "typography")),
                SettingsChild("Use system font", "use_system_font", listOf("system font", "default font", "roboto")) { SearchResultSwitch(UseSystemFontKey, false) },
                SettingsChild("Thumbnail corner radius", "thumbnail_corner_radius", listOf("thumbnail corner", "corner radius", "rounded thumbnail", "thumbnail shape")),
                SettingsChild("Crop thumbnail to square", "crop_thumbnail_to_square", listOf("crop thumbnail", "square thumbnail", "thumbnail crop")) { SearchResultSwitch(CropThumbnailToSquareKey, false) },
                SettingsChild("Player design style", "player_design_style", listOf("player design", "player layout", "player style")),
                SettingsChild("Player background style", "player_background_style", listOf("player background", "player bg", "background style")),
                SettingsChild("Lyrics background style", "lyrics_background_style", listOf("lyrics background", "lyrics bg")),
                SettingsChild("Mini player background style", "mini_player_background_style", listOf("mini player", "mini player background")),
                SettingsChild("Player buttons style", "player_buttons_style", listOf("player buttons", "button style", "controls style")),
                SettingsChild("Player slider style", "player_slider_style", listOf("player slider", "slider style", "progress bar")),
                SettingsChild("Show player volume bar", "show_player_volume_bar", listOf("volume bar", "player volume", "volume slider")) { SearchResultSwitch(ShowPlayerVolumeBarKey, false) },
                SettingsChild("Hide player thumbnail", "hide_player_thumbnail", listOf("hide thumbnail", "player thumbnail", "hide artwork")) { SearchResultSwitch(HidePlayerThumbnailKey, false) },
                SettingsChild("Swipe to song", "swipe_to_song", listOf("swipe to song", "swipe next", "swipe track")) { SearchResultSwitch(SwipeToSongKey, false) },
                SettingsChild("Swipe sensitivity", "swipe_sensitivity", listOf("swipe", "gesture", "sensitivity")),
                SettingsChild("Disable animations", "disable_animations", listOf("animation", "disable animations", "no animations", "performance")) { SearchResultSwitch(DisableAnimationsKey, false) },
                SettingsChild("Force high refresh rate", "force_high_refresh_rate", listOf("refresh rate", "high refresh", "120hz", "90hz", "smooth")) { SearchResultSwitch(ForceHighRefreshRateKey, false) },
                SettingsChild("Navigation bar style", "navigation_bar_style", listOf("navigation bar", "nav bar", "bottom bar")),
                SettingsChild("Frosted navigation bar", "frosted_nav_bar", listOf("frosted nav", "frosted navigation", "frosted blur")) { SearchResultSwitch(NavigationBarFrostedBlurKey, false) },
                SettingsChild("Hide labels in navigation bar", "hide_navigation_bar_labels", listOf("hide labels", "navigation labels", "nav labels", "icons only")) { SearchResultSwitch(HideNavigationBarLabelsKey, false) },
                SettingsChild("Default open tab", "default_open_tab", listOf("default tab", "home tab", "start page", "open tab")),
                SettingsChild("Grid layout", "grid_layout", listOf("grid", "layout", "list view", "artist grid")),
                SettingsChild("Show home category chips", "show_home_category_chips", listOf("home chips", "category chips", "home category", "chips")) { SearchResultSwitch(ShowHomeCategoryChipsKey, false) },
                SettingsChild("Language", "app_language", listOf("language", "app language", "locale")),
            ),
        )
    val playback =
        SettingsItem(
            key = "playback",
            icon = painterResource(R.drawable.music_note),
            title = stringResource(R.string.settings_playback_title),
            subtitle = stringResource(R.string.settings_playback_subtitle),
            accentColor = MaterialTheme.colorScheme.tertiary,
            keywords = listOf("playback", "player", "audio", "quality", "equalizer", "eq", "volume", "crossfade", "gapless", "flac", "lossless", "hi-res", "sample rate", "bitrate"),
            onClick = { navController.navigate("settings/player") },
            children = listOf(
                SettingsChild("Low data mode", "low_data_mode", listOf("data", "data saver", "low quality", "data mode")) { SearchResultSwitch(LowDataModeKey, true) },
                SettingsChild("History duration", "history_duration", listOf("history", "duration", "recent", "queue length")),
                SettingsChild("Crossfade", "crossfade", listOf("crossfade", "fade", "transition", "mix", "blend")) { SearchResultSwitch(CrossfadeEnabledKey, false) },
                SettingsChild("Crossfade gapless", "crossfade_gapless", listOf("crossfade gapless", "gapless crossfade", "seamless crossfade")) { SearchResultSwitch(CrossfadeGaplessKey, true) },
                SettingsChild("Skip silence", "skip_silence", listOf("silence", "skip silence", "blank", "quiet")) { SearchResultSwitch(SkipSilenceKey, false) },
                SettingsChild("Audio normalization", "audio_normalization", listOf("normalization", "loudness", "normalize", "volume level")) { SearchResultSwitch(AudioNormalizationKey, true) },
                SettingsChild("Audio offload", "audio_offload", listOf("offload", "audio offload", "hardware decoder")) { SearchResultSwitch(AudioOffload, false) },
                SettingsChild("Seek seconds add-up", "seek_seconds", listOf("seek", "skip", "forward", "rewind", "seconds")) { SearchResultSwitch(SeekExtraSeconds, false) },
                SettingsChild("Pause on device mute", "pause_mute", listOf("mute", "pause mute", "headphone", "silence detect")) { SearchResultSwitch(PauseOnDeviceMuteKey, false) },
                SettingsChild("Device mute recovery volume", "device_mute_recovery_volume", listOf("recovery volume", "mute recovery", "volume restore")),
                SettingsChild("Auto start on Bluetooth", "bluetooth_auto_start", listOf("bluetooth", "auto start", "auto play", "connect")) { SearchResultSwitch(AutoStartOnBluetoothKey, false) },
                SettingsChild("ArchiveTune Canvas", "archive_tune_canvas", listOf("canvas", "animated artwork", "motion artwork", "live artwork")) { SearchResultSwitch(ArchiveTuneCanvasKey, true) },
                SettingsChild("Tidal artwork fallback", "tidal_artwork_fallback", listOf("tidal artwork", "artwork fallback", "tidal cover", "hi-res artwork")) { SearchResultSwitch(TidalArtworkFallbackEnabledKey, true) },
                SettingsChild("Persistent queue", "persistent_queue", listOf("queue", "persistent", "save queue", "resume")) { SearchResultSwitch(PersistentQueueKey, true) },
                SettingsChild("Permanent shuffle", "permanent_shuffle", listOf("shuffle", "random", "permanent")) { SearchResultSwitch(PermanentShuffleKey, false) },
                SettingsChild("Auto download on like", "auto_download_like", listOf("auto download", "like", "download liked")) { SearchResultSwitch(AutoDownloadOnLikeKey, false) },
                SettingsChild("Download source", "download_source", listOf("download source", "qobuz download", "tidal download", "youtube music download")),
                SettingsChild("Auto skip on error", "auto_skip_error", listOf("skip", "error", "auto skip", "failed")) { SearchResultSwitch(AutoSkipNextOnErrorKey, false) },
                SettingsChild("Stop music on task clear", "stop_task_clear", listOf("stop", "task clear", "background", "close app")) { SearchResultSwitch(StopMusicOnTaskClearKey, false) },
                SettingsChild("Wakelock", "wakelock", listOf("wakelock", "wake lock", "keep awake", "cpu")) { SearchResultSwitch(WakelockKey, false) },
                SettingsChild("Artist separators", "artist_separators", listOf("artist", "separator", "split", "featuring")),
                SettingsChild("Manage playlist tags", "manage_playlist_tags", listOf("playlist tags", "tag management", "organize playlists")),
                SettingsChild("External downloader", "external_downloader", listOf("external downloader", "download app", "custom downloader")),
                SettingsChild("External downloader package", "external_downloader_package", listOf("downloader package", "downloader app name")),
            ),
        )
    val sources =
        SettingsItem(
            key = "sources",
            icon = painterResource(R.drawable.provider_tidal),
            title = stringResource(R.string.source_settings),
            subtitle = stringResource(R.string.source_settings_subtitle),
            accentColor = MaterialTheme.colorScheme.tertiary,
            keywords = listOf("source", "music source", "youtube music", "tidal", "qobuz", "provider", "streaming", "telegram", "telegram channel", "flac", "lossless", "private channel"),
            onClick = { navController.navigate("settings/sources") },
            children = listOf(
                SettingsChild("YouTube Music", "youtube_music", listOf("youtube", "youtube music", "yt music")),
                SettingsChild("Qobuz", "qobuz", listOf("qobuz", "hires", "hi-res", "flac", "lossless", "cd quality")),
                SettingsChild("Tidal", "tidal", listOf("tidal", "lossless", "hifi", "master", "mq")),
            ),
        )
    val lyrics =
        SettingsItem(
            key = "lyrics",
            icon = painterResource(R.drawable.lyrics),
            title = stringResource(R.string.lyrics),
            subtitle = stringResource(R.string.settings_lyrics_subtitle),
            accentColor = MaterialTheme.colorScheme.secondary,
            keywords = listOf("lyrics", "lyric", "subtitle", "text", "sing along", "lrc", "translation", "romanize", "karaoke"),
            onClick = { navController.navigate("settings/lyrics") },
            children = listOf(
                SettingsChild("Lyrics provider", "lyrics_provider", listOf("lyrics provider", "source", "lrclib", "kugou", "netease", "musixmatch", "paxsenix", "betterlyrics", "portato", "youlyplus", "unison", "simpmusic", "megalobiz")),
                SettingsChild("Lyrics mode", "lyrics_mode", listOf("lyrics mode", "lyrics style", "lyrics display mode", "karaoke mode")),
                SettingsChild("Show lyrics", "show_lyrics", listOf("show lyrics", "display lyrics", "lyrics toggle", "lyrics show")) { SearchResultSwitch(ShowLyricsKey, false) },
                SettingsChild("Use lyrics V2", "use_lyrics_v2", listOf("lyrics v2", "new lyrics", "lyrics engine")) { SearchResultSwitch(UseLyricsV2Key, true) },
                SettingsChild("Translate lyrics", "translate_lyrics", listOf("translate", "translation", "lyrics translation")) { SearchResultSwitch(TranslateLyricsKey, false) },
                SettingsChild("Enable translator", "enable_translator", listOf("translator", "translation engine", "lyrics translator")) { SearchResultSwitch(EnableTranslatorKey, false) },
                SettingsChild("Lyrics font size", "lyrics_font_size", listOf("font size", "lyrics size", "text size", "lyrics text size")),
                SettingsChild("Lyrics line spacing", "lyrics_line_spacing", listOf("line spacing", "lyrics spacing", "lyrics line gap", "lyrics padding")),
                SettingsChild("Lyrics animations", "lyrics_animations", listOf("animation", "animated lyrics", "lyrics effect")),
                SettingsChild("Lyrics animation style", "lyrics_animation_style", listOf("animation style", "lyrics animation", "lyrics motion", "lyrics transition")),
                SettingsChild("Lyrics line blur", "lyrics_line_blur", listOf("lyrics blur", "line blur", "focus blur")),
                SettingsChild("Lyrics romanize Japanese", "lyrics_romanize_japanese", listOf("romanize", "japanese", "romaji", "furigana")),
                SettingsChild("Lyrics romanize Korean", "lyrics_romanize_korean", listOf("romanize", "korean", "romanization")),
                SettingsChild("Lyrics romanize Chinese", "lyrics_romanize_chinese", listOf("romanize", "chinese", "pinyin")),
                SettingsChild("Lyrics romanize Hindi", "lyrics_romanize_hindi", listOf("romanize", "hindi", "devanagari")),
                SettingsChild("Lyrics romanize other languages", "lyrics_romanize_other", listOf("romanize", "other languages", "arabic", "thai", "cyrillic")),
                SettingsChild("Lyrics click to seek", "lyrics_click", listOf("click lyrics", "tap lyrics", "seek lyrics")) { SearchResultSwitch(LyricsClickKey, false) },
                SettingsChild("Lyrics auto-scroll", "lyrics_scroll", listOf("scroll", "auto scroll", "lyrics scroll")) { SearchResultSwitch(LyricsScrollKey, true) },
                SettingsChild("Show lyrics player controls", "show_lyrics_player_controls", listOf("player controls", "lyrics controls")) { SearchResultSwitch(ShowLyricsPlayerControlsKey, true) },
                SettingsChild("Preload queue lyrics", "preload_queue_lyrics", listOf("preload", "preload lyrics", "queue lyrics")),
                SettingsChild("Queue lyrics preload count", "queue_lyrics_preload_count", listOf("preload count", "queue lyrics count", "preload amount", "preload size")),
                SettingsChild("Lyrics background style", "lyrics_background_style", listOf("lyrics background", "lyrics bg")),
                SettingsChild("BetterLyrics", "betterlyrics", listOf("betterlyrics", "better lyrics", "better lyrics provider")),
                SettingsChild("BetterLyrics Portato", "betterlyrics_portato", listOf("portato", "betterlyrics portato", "portato lyrics")),
                SettingsChild("YouLyPlus Lyrics", "youlyplus_lyrics", listOf("youlyplus", "youlyplus lyrics", "youly plus")),
                SettingsChild("LRCLIB", "lrclib", listOf("lrclib", "lrclib lyrics", "lrclib provider")),
                SettingsChild("Kugou Lyrics", "kugou", listOf("kugou", "kugou lyrics", "kugou provider", "kugou music")),
                SettingsChild("Unison Lyrics", "unison_lyrics", listOf("unison", "unison lyrics", "unison provider")),
                SettingsChild("Simpmusic Lyrics", "simpmusic_lyrics", listOf("simpmusic", "simpmusic lyrics", "simpmusic provider")),
                SettingsChild("Megalobiz Lyrics", "megalobiz_lyrics", listOf("megalobiz", "megalobiz lyrics", "megalobiz provider")),
                SettingsChild("Paxsenix Lyrics", "paxsenix_lyrics", listOf("paxsenix", "paxsenix lyrics", "paxsenix provider")),
                SettingsChild("Paxsenix Stats", "paxsenix_stats", listOf("paxsenix stats", "paxsenix statistics", "paxsenix analytics")),
                SettingsChild("First lyrics provider", "first_lyrics_provider", listOf("first lyrics", "lyrics priority", "primary lyrics provider", "lyrics order")),
            ),
        )
    val content =
        SettingsItem(
            key = "content",
            icon = painterResource(R.drawable.language),
            title = stringResource(R.string.content),
            subtitle = stringResource(R.string.settings_content_subtitle),
            accentColor = MaterialTheme.colorScheme.primary,
            keywords = listOf("content", "language", "locale", "country", "region", "app language", "explicit", "age restricted", "age", "mature", "video", "progressive", "quick picks"),
            onClick = { navController.navigate("settings/content") },
            children = listOf(
                SettingsChild("Content language", "content_language", listOf("language", "content language", "locale", "country")),
                SettingsChild("Content country", "content_country", listOf("country", "region", "content country")),
                SettingsChild("Hide explicit", "hide_explicit", listOf("explicit", "age", "mature", "age restricted", "clean")) { SearchResultSwitch(HideExplicitKey, false) },
                SettingsChild("Hide video", "hide_video", listOf("video", "hide video", "music video", "mv")) { SearchResultSwitch(HideVideoKey, false) },
                SettingsChild("Enable video", "enable_video", listOf("video", "music video", "mv", "enable video")),
                SettingsChild("Quick picks", "quick_picks", listOf("quick picks", "quick mix", "smart mix", "recommendations")),
                SettingsChild("Progressive playback", "progressive_playback", listOf("progressive", "gapless", "seamless")),
            ),
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
            keywords = listOf("behavior", "privacy", "swipe", "gesture", "history", "cache", "data", "screenshot", "haptic", "vibrate"),
            onClick = { navController.navigate("settings/privacy") },
            children = listOf(
                SettingsChild("Pause listen history", "pause_listen_history", listOf("pause listen", "stop history", "private listening")) { SearchResultSwitch(PauseListenHistoryKey, false) },
                SettingsChild("Clear listen history", "clear_listen_history", listOf("clear history", "delete history", "reset history")),
                SettingsChild("Pause search history", "pause_search_history", listOf("pause search", "stop search history", "private search")) { SearchResultSwitch(PauseSearchHistoryKey, false) },
                SettingsChild("Clear search history", "clear_search_history", listOf("clear search", "delete search", "reset search")),
                SettingsChild("Sync playback to YouTube history", "sync_yt_history", listOf("youtube history", "sync history", "playback history")) { SearchResultSwitch(SyncPlaybackToYouTubeHistoryKey, false) },
                SettingsChild("Haptics", "haptics", listOf("haptic", "vibration", "haptic feedback", "vibrate")) { SearchResultSwitch(EnableHapticFeedbackKey, true) },
                SettingsChild("Disable screenshot", "disable_screenshot", listOf("screenshot", "screen capture", "privacy", "no screenshot")) { SearchResultSwitch(DisableScreenshotKey, false) },
                SettingsChild("Network metered", "network_metered", listOf("metered", "mobile data", "cellular", "data saver")) { SearchResultSwitch(NetworkMeteredKey, false) },
                SettingsChild("Show tags in library", "show_tags_in_library", listOf("tags", "library tags", "show tags")),
            ),
        )
    val integration =
        SettingsItem(
            key = "integration",
            icon = painterResource(R.drawable.auto_awesome),
            title = stringResource(R.string.integration),
            subtitle = stringResource(R.string.settings_integration_subtitle),
            accentColor = MaterialTheme.colorScheme.secondary,
            keywords = listOf("integration", "lastfm", "last.fm", "libre.fm", "scrobble", "scrobbling", "discord", "listenbrainz", "spotify"),
            onClick = { navController.navigate("settings/integration") },
            children = listOf(
                SettingsChild("Last.fm scrobbling", "lastfm_scrobbling", listOf("lastfm", "last.fm", "libre.fm", "scrobble", "scrobbling", "listens")) { SearchResultSwitch(EnableLastFMScrobblingKey, false) },
                SettingsChild("Last.fm account", "lastfm_account", listOf("lastfm account", "lastfm login", "lastfm session", "lastfm username")),
                SettingsChild("Last.fm options", "lastfm_options", listOf("lastfm options", "lastfm settings", "scrobble toggle", "now playing")),
                SettingsChild("Last.fm scrobbling configuration", "lastfm_scrobbling_config", listOf("scrobble config", "scrobble configuration", "scrobble threshold", "scrobble percentage")),
                SettingsChild("Discord rich presence", "discord_presence", listOf("discord", "rich presence", "rpc", "status", "now playing")) { SearchResultSwitch(EnableDiscordRPCKey, false) },
                SettingsChild("Discord account", "discord_account", listOf("discord account", "discord login", "discord token", "discord authorization")),
                SettingsChild("Discord options", "discord_options", listOf("discord options", "discord refresh", "refresh discord")),
                SettingsChild("Discord connection settings", "discord_connection", listOf("discord connection", "activity status", "platform status", "discord platform")),
                SettingsChild("Discord activity content", "discord_activity", listOf("discord activity", "activity name", "activity details", "activity state", "activity type", "discord show when paused")),
                SettingsChild("Discord image options", "discord_images", listOf("discord image", "large image", "large text", "discord artwork", "discord cover")),
                SettingsChild("ListenBrainz", "listenbrainz", listOf("listenbrainz", "listen brainz", "scrobble")) { SearchResultSwitch(ListenBrainzEnabledKey, false) },
                SettingsChild("ListenBrainz token", "listenbrainz_token", listOf("listenbrainz token", "listenbrainz api key", "listenbrainz credential")),
                SettingsChild("Spotify", "spotify", listOf("spotify", "spotify connect", "spotify playlists")) { SearchResultSwitch(ShowSpotifyPlaylistsKey, false) },
                SettingsChild("Tidal", "tidal", listOf("tidal", "hifi", "master", "mqa", "lossless", "flac")) { SearchResultSwitch(TidalEnabledKey, false) },
                SettingsChild("Tidal account", "tidal_account", listOf("tidal account", "tidal login", "tidal token", "tidal session")),
                SettingsChild("Tidal instances", "tidal_instances", listOf("tidal instance", "tidal server", "tidal url", "tidal endpoint")),
                SettingsChild("Qobuz", "qobuz", listOf("qobuz", "hires", "hi-res", "flac", "lossless", "cd quality")),
                SettingsChild("Qobuz account", "qobuz_account", listOf("qobuz account", "qobuz login", "qobuz email", "qobuz session")),
                SettingsChild("Qobuz tokens", "qobuz_tokens", listOf("qobuz token", "qobuz app secret", "qobuz credential")),
                SettingsChild("Qobuz instances", "qobuz_instances", listOf("qobuz instance", "qobuz server", "qobuz url", "qobuz endpoint")),
                SettingsChild("Deezer", "deezer", listOf("deezer", "deezer login", "deezer premium", "deezer account", "deezer session")),
                SettingsChild("Telegram", "telegram", listOf("telegram", "telegram channel", "channel sync", "telegram music", "telegram bot")),
                SettingsChild("Telegram login", "telegram_login", listOf("telegram login", "telegram session", "telegram account", "sign in telegram")),
                SettingsChild("Telegram browse channels", "telegram_browse_channels", listOf("browse channels", "channels", "telegram channels", "music channels")),
                SettingsChild("Telegram lossless only", "telegram_lossless_only", listOf("lossless", "flac", "lossless only", "high quality")) { SearchResultSwitch(TelegramLosslessOnlyKey, false) },
                SettingsChild("Telegram logout", "telegram_logout", listOf("logout", "log out", "sign out", "disconnect telegram")),
                SettingsChild("Import playlist from another service", "cross_service_import", listOf("import", "import playlist", "cross service", "youtube music import", "apple music import", "amazon music import", "tidal import", "deezer import", "playlist url", "import url", "import from url", "playlist from url")),
            ),
        )
    val aiIntegration =
        SettingsItem(
            key = "ai_integration",
            icon = painterResource(R.drawable.ai),
            title = stringResource(R.string.ai_integration),
            subtitle = stringResource(R.string.ai_integration_desc),
            accentColor = MaterialTheme.colorScheme.secondary,
            keywords = listOf("ai", "artificial intelligence", "chatgpt", "openai", "gemini", "llm", "ai integration", "mix", "smart mix"),
            onClick = { navController.navigate("settings/ai_integration") },
            children = listOf(
                SettingsChild("AI provider", "ai_provider", listOf("ai provider", "provider", "openai", "gemini", "claude", "anthropic", "model provider")),
                SettingsChild("Custom endpoint", "ai_custom_endpoint", listOf("custom endpoint", "endpoint", "base url", "api url", "custom api")),
                SettingsChild("AI API key", "ai_api_key", listOf("api key", "key", "secret", "ai key", "token")),
                SettingsChild("AI model", "ai_model", listOf("model", "ai model", "gpt", "gemini model", "claude model")),
                SettingsChild("Test API", "ai_test_api", listOf("test", "test api", "verify", "test connection", "ai test")),
                SettingsChild("Hide AI mix", "hide_ai_mix", listOf("hide ai", "ai mix", "smart mix", "hide mix")) { SearchResultSwitch(HideAiMixKey, false) },
            ),
        )
    val internet =
        SettingsItem(
            key = "internet",
            icon = painterResource(R.drawable.wifi_proxy),
            title = stringResource(R.string.internet),
            subtitle = stringResource(R.string.settings_internet_subtitle),
            accentColor = MaterialTheme.colorScheme.tertiary,
            keywords = listOf("internet", "proxy", "vpn", "network", "wifi", "connection", "traffic", "tor", "dns", "dns over https", "region", "country", "spoof", "geobypass", "geo bypass"),
            onClick = { navController.navigate("settings/internet") },
            children = listOf(
                SettingsChild("YouTube Music region", "yt_music_region", listOf("region", "country", "gl", "geo", "location", "spoof", "youtube region", "youtube music country", "geobypass", "geo bypass")),
                SettingsChild("Proxy", "proxy_settings", listOf("proxy", "http proxy", "socks", "vpn")) { SearchResultSwitch(ProxyEnabledKey, false) },
                SettingsChild("Proxy host", "proxy_host", listOf("proxy host", "proxy address", "proxy server")),
                SettingsChild("Proxy port", "proxy_port", listOf("proxy port", "port", "proxy port number")),
                SettingsChild("Proxy type", "proxy_type", listOf("proxy type", "socks5", "http proxy type", "proxy protocol")),
                SettingsChild("Proxy username", "proxy_username", listOf("proxy username", "proxy auth", "proxy credentials", "proxy login")),
                SettingsChild("Proxy password", "proxy_password", listOf("proxy password", "proxy auth", "proxy credentials", "proxy secret")),
                SettingsChild("Bypass proxy for streams", "stream_bypass_proxy", listOf("bypass proxy", "stream proxy", "stream bypass", "skip proxy for streams")),
                SettingsChild("Test proxy connection", "test_proxy", listOf("test proxy", "verify proxy", "check proxy", "proxy test")),
                SettingsChild("DNS over HTTPS", "dns_over_https", listOf("dns", "dns over https", "doh", "encrypted dns", "secure dns")),
                SettingsChild("DNS provider", "dns_provider", listOf("dns provider", "dns server", "dns resolver", "dns service")),
                SettingsChild("DNS custom URL", "dns_custom_url", listOf("dns custom url", "custom dns", "dns endpoint", "dns url")),
                SettingsChild("IP rotation", "ip_rotation", listOf("ip rotation", "rotate ip", "ip pool", "ip cycling")),
                SettingsChild("Enable tor", "enable_tor", listOf("tor", "onion", "anonymous", "onion routing", "tor network")),
                SettingsChild("Download speed limit", "download_speed_limit", listOf("speed", "limit", "throttle", "bandwidth", "download speed", "download limit", "speed cap")),
            ),
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
            keywords = listOf("storage", "download", "cache", "disk", "space", "memory", "path", "location", "export", "export songs", "local storage", "save songs"),
            onClick = { navController.navigate("settings/storage") },
            children = listOf(
                SettingsChild("Downloaded songs", "downloaded_songs", listOf("downloaded", "offline songs", "saved songs")),
                SettingsChild("Clear all downloads", "clear_all_downloads", listOf("clear downloads", "delete downloads", "remove downloads")),
                SettingsChild("Export downloaded songs", "export_downloaded_songs", listOf("export", "export songs", "save songs", "local storage", "file")),
                SettingsChild("Song cache size", "song_cache_size", listOf("cache size", "song cache", "memory", "download cache")),
                SettingsChild("Clear song cache", "clear_song_cache", listOf("clear song cache", "delete song cache", "wipe song cache")),
                SettingsChild("Image cache size", "image_cache_size", listOf("image cache", "thumbnail cache", "artwork cache")),
                SettingsChild("Clear image cache", "clear_image_cache", listOf("clear image cache", "delete image cache", "wipe image cache")),
                SettingsChild("Canvas cache", "canvas_cache", listOf("canvas cache", "motion artwork cache", "animated artwork storage")),
                SettingsChild("Clear canvas cache", "clear_canvas_cache", listOf("clear canvas cache", "delete canvas cache", "wipe canvas cache")),
                SettingsChild("Storage folder", "storage_folder", listOf("storage path", "storage location", "storage directory")),
                SettingsChild("Download location", "download_location", listOf("download path", "location", "folder", "directory", "save to")),
                SettingsChild("Smart trimmer", "smart_trimmer", listOf("smart trimmer", "trim cache", "auto clean cache")) { SearchResultSwitch(SmartTrimmerKey, false) },
                SettingsChild("External downloader", "external_downloader", listOf("external downloader", "download app", "custom downloader")) { SearchResultSwitch(ExternalDownloaderEnabledKey, false) },
                SettingsChild("External downloader package", "external_downloader_package", listOf("downloader package", "downloader app name")),
            ),
        )
    val backupRestore =
        SettingsItem(
            key = "backup_restore",
            icon = painterResource(R.drawable.backup),
            title = stringResource(R.string.backup_restore),
            subtitle = stringResource(R.string.settings_backup_restore_subtitle),
            accentColor = MaterialTheme.colorScheme.primary,
            keywords = listOf("backup", "restore", "export", "import", "data", "save", "scheduled", "spotify", "playlist", "csv", "m3u"),
            onClick = { navController.navigate("settings/backup_restore") },
            children = listOf(
                SettingsChild("Scheduled backup", "scheduled_backup", listOf("scheduled backup", "auto backup", "schedule", "automatic backup", "backup schedule", "periodic backup")),
                SettingsChild("Scheduled backup frequency", "scheduled_backup_frequency", listOf("backup frequency", "schedule frequency", "backup interval")),
                SettingsChild("Scheduled backup directory", "scheduled_backup_directory", listOf("backup directory", "backup folder", "backup location")),
                SettingsChild("Scheduled backup overwrite", "scheduled_backup_overwrite", listOf("overwrite backup", "replace backup")),
                SettingsChild("Backup", "backup", listOf("backup", "save data", "export backup")),
                SettingsChild("Restore", "restore", listOf("restore", "import", "recover")),
                SettingsChild("Import online (m3u)", "import_online", listOf("import online", "m3u", "playlist import")),
                SettingsChild("Import CSV", "import_csv", listOf("import csv", "csv", "playlist csv")),
                SettingsChild("Spotify", "spotify_backup", listOf("spotify", "spotify connect", "spotify playlists")) { SearchResultSwitch(ShowSpotifyPlaylistsKey, false) },
            ),
        )
    val developerOptions =
        SettingsItem(
            key = "developer_options",
            icon = painterResource(R.drawable.experiment),
            title = stringResource(R.string.settings_developer_options_title),
            subtitle = stringResource(R.string.settings_developer_options_subtitle),
            accentColor = MaterialTheme.colorScheme.tertiary,
            keywords = listOf("developer", "debug", "experimental", "advanced", "logcat", "dev", "manual source", "changelog", "update"),
            onClick = { navController.navigate("settings/misc") },
            children = listOf(
                SettingsChild("Logcat", "logcat", listOf("logcat", "log", "debug log")),
                SettingsChild("Changelog", "changelog", listOf("changelog", "changes", "release notes", "what's new")),
                SettingsChild("Update channel", "update_channel", listOf("update channel", "canary", "stable", "beta")),
                SettingsChild("Enable update notification", "enable_update_notification", listOf("update notification", "notify update", "update alert")),
                SettingsChild("Manual source login", "manual_source_login", listOf("manual source login", "manual login", "dev source login")),
                SettingsChild("YTM sync", "ytm_sync", listOf("ytm sync", "youtube music sync", "sync library")),
                SettingsChild("Force sync on account switch", "force_sync_account_switch", listOf("force sync", "account switch sync", "sync on switch")),
            ),
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
            children = listOf(
                SettingsChild("Version", "about_version", listOf("version", "build")),
                SettingsChild("Changelog", "about_changelog", listOf("changelog", "changes", "release notes", "what's new")),
                SettingsChild("License", "about_license", listOf("license", "gpl", "open source")),
            ),
        )

    return listOf(
        SettingsGroup(
            title = stringResource(R.string.settings),
            items = listOf(account, stats),
        ),
        SettingsGroup(
            title = stringResource(R.string.settings_section_player_content),
            items = listOf(appearance, playback, sources, lyrics, languagePacks, content, behavior),
        ),
        SettingsGroup(
            title = stringResource(R.string.integration),
            // Discord / Last.fm / Tidal / Qobuz / Telegram are intentionally NOT
            // top-level items here — they live as children of `integration` (and
            // also under their respective `sources` / `integration` screens).
            // Surfacing them as separate rows on the main settings page was
            // redundant noise per user feedback.
            items = listOf(integration, aiIntegration, internet, poToken),
        ),
        SettingsGroup(
            title = stringResource(R.string.storage),
            items = listOf(storage, backupRestore),
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
