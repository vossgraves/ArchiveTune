/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.constants

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale

val DynamicThemeKey = booleanPreferencesKey("dynamicTheme")
val CustomThemeColorKey = stringPreferencesKey("customThemeColor")
val RandomThemeOnStartupKey = booleanPreferencesKey("randomThemeOnStartup")
val DarkModeKey = stringPreferencesKey("darkMode")
val PureBlackKey = booleanPreferencesKey("pureBlack")
val DisableAnimationsKey = booleanPreferencesKey("disableAnimations")
val ForceHighRefreshRateKey = booleanPreferencesKey("forceHighRefreshRate")
val WallpaperExtractionFailedKey = booleanPreferencesKey("wallpaperExtractionFailed")
val HideStatusBarKey = booleanPreferencesKey("hideStatusBar")

// UI scale (DPI-like) multiplier applied via a LocalDensity override in MainActivity.
// 1.0f = system default. Range clamped to [0.85f, 1.30f] in AppearanceSettings.
// Stored as a float so the slider is continuous (1% steps via 46 discrete positions).
val UiScaleFactorKey = floatPreferencesKey("uiScaleFactor")

// When true, forces the two-pane NavigationRail layout (normally reserved for
// tablet-width windows) on phone-sized windows too. Useful on landscape phones
// and on tablets where the dp breakpoint misclassifies the window.
val TabletModeEnabledKey = booleanPreferencesKey("tabletModeEnabled")
val EnableHapticFeedbackKey = booleanPreferencesKey("enableHapticFeedback")
val UseSystemFontKey = booleanPreferencesKey("useSystemFont")
val FontPreferenceKey = stringPreferencesKey("fontPreference")
val CustomFontUriKey = stringPreferencesKey("customFontUri")
val CustomFontNameKey = stringPreferencesKey("customFontName")
val DefaultOpenTabKey = stringPreferencesKey("defaultOpenTab")
val GridItemsSizeKey = stringPreferencesKey("gridItemSize")
val SliderStyleKey = stringPreferencesKey("sliderStyle")
val SwipeToSongKey = booleanPreferencesKey("SwipeToSong")
val PlayerDesignStyleKey = stringPreferencesKey("playerDesignStyle")
val ShowPlayerVolumeBarKey = booleanPreferencesKey("showPlayerVolumeBar")
val HidePlayerThumbnailKey = booleanPreferencesKey("hidePlayerThumbnail")
val ArchiveTuneCanvasKey = booleanPreferencesKey("archiveTuneCanvas")
val SpotifyCanvasKey = booleanPreferencesKey("spotifyCanvas")

/**
 * Whether an album page plays the album's looping motion artwork behind its header.
 *
 * Deliberately separate from [ArchiveTuneCanvasKey], which governs the *player's*
 * now-playing canvas: the two are different surfaces with different costs (the album
 * header loop starts as soon as a page opens, whether or not anything is playing), and
 * a user who wants one does not necessarily want the other. Default on, so albums that
 * have motion artwork animate the way they do in Apple Music.
 */
val AlbumCanvasEnabledKey = booleanPreferencesKey("albumCanvasEnabled")

/**
 * Newline-separated list of extra Spotify Canvas resolver endpoints, tried in order after
 * Spotify's own Canvas endpoint. Empty (the default) means only the built-in resolver is
 * used. See [moe.rukamori.archivetune.utils.CanvasResolverEndpoints] for why this is user
 * supplied rather than a shipped list.
 */
val CanvasResolverEndpointsKey = stringPreferencesKey("canvasResolverEndpoints")
val ThumbnailCornerRadiusKey = floatPreferencesKey("thumbnailCornerRadius")
val CropThumbnailToSquareKey = booleanPreferencesKey("cropThumbnailToSquare")

val AodThumbnailShapeKey = stringPreferencesKey("aodThumbnailShape")
val AodThumbnailSizeKey = floatPreferencesKey("aodThumbnailSize")
val AodThumbnailShapeRotationKey = intPreferencesKey("aodThumbnailShapeRotation")
val AodShowThumbnailKey = booleanPreferencesKey("aodShowThumbnail")
val AodShowArtistKey = booleanPreferencesKey("aodShowArtist")
val AodShowAlbumKey = booleanPreferencesKey("aodShowAlbum")
val AodShowProgressKey = booleanPreferencesKey("aodShowProgress")
val AodShowTimeLabelsKey = booleanPreferencesKey("aodShowTimeLabels")
val AodShowControlsKey = booleanPreferencesKey("aodShowControls")
val AodShowExitButtonKey = booleanPreferencesKey("aodShowExitButton")
val AodArtworkGlowKey = booleanPreferencesKey("aodArtworkGlow")
val AodBackgroundStyleKey = stringPreferencesKey("aodBackgroundStyle")
val AodAccentStyleKey = stringPreferencesKey("aodAccentStyle")
val AodContentPositionKey = stringPreferencesKey("aodContentPosition")
val AodTextAlignmentKey = stringPreferencesKey("aodTextAlignment")
val AodControlStyleKey = stringPreferencesKey("aodControlStyle")
val AodControlSizeKey = floatPreferencesKey("aodControlSize")

/**
 * Slider style for the AOD progress bar. Reuses the [SliderStyle] enum so the
 * same five styles (Standard / Wavy / Thick / Circular / Simple) are available
 * as for the main player. Stored separately from [SliderStyleKey] so users can
 * pick a different style for AOD without affecting the main player.
 */
val AodSliderStyleKey = stringPreferencesKey("aodSliderStyle")
val AodHorizontalPaddingKey = floatPreferencesKey("aodHorizontalPadding")
val AodVerticalSpacingKey = floatPreferencesKey("aodVerticalSpacing")
val AodTitleMaxLinesKey = intPreferencesKey("aodTitleMaxLines")
val AodAmbientIntensityKey = floatPreferencesKey("aodAmbientIntensity")
// Show a compact, dimmed lyrics line on the AOD screen while music plays.
val AodShowLyricsKey = booleanPreferencesKey("aodShowLyrics")
// When > 0, automatically enter AOD mode after this many seconds of the player sheet being
// collapsed (i.e. the user is no longer actively interacting with the player). 0 disables.
val AodAutoTimerSecondsKey = intPreferencesKey("aodAutoTimerSeconds")
// When true, AOD mode auto-triggers when the screen is about to turn off due to inactivity
// (driven via the system lock intent). Disabled by default to avoid surprises.
val AodAutoOnScreenDimKey = booleanPreferencesKey("aodAutoOnScreenDim")
// Experimental native Musixmatch provider (token.get + macro.subtitles + richsync→TTML).
// Off by default; toggle surfaces in Lyrics settings under "Experimental".
val EnableMusixmatchExperimentalKey = booleanPreferencesKey("enableMusixmatchExperimental")
val SeekExtraSeconds = booleanPreferencesKey("seekExtraSeconds")
val DisableBlurKey = booleanPreferencesKey("disableBlur")
val BlurRadiusKey = floatPreferencesKey("blurRadius")

// Backdrop blur for detail pages
val BackdropEnabledKey = booleanPreferencesKey("backdropEnabled")
val BackdropBlurAmountKey = intPreferencesKey("backdropBlurAmount")
val MiniPlayerLastAnchorKey = intPreferencesKey("miniPlayerLastAnchor")
val MiniPlayerBackgroundStyleKey = stringPreferencesKey("miniPlayerBackgroundStyle")

// ── Liquid Glass effects ──────────────────────────────────────────────────────
// Master toggle: when off, all Liquid Glass surfaces (header pills on detail
// pages, the Liquid Glass mini player background, and the Liquid Glass nav bar
// style) are unavailable / hidden / forced to their non-glass fallback.
//
// Sub-toggles:
//  - LiquidGlassNavBarEnabledKey: opt-in Liquid Glass style for the bottom
//    navigation bar (uses kyant-backdrop LayerBackdrop). Independent of the
//    existing frostedBlur / tintFrostedBlur booleans, which use a different
//    RenderEffect-based recipe.
val LiquidGlassEnabledKey = booleanPreferencesKey("liquidGlassEnabled")
val LiquidGlassNavBarEnabledKey = booleanPreferencesKey("liquidGlassNavBarEnabled")

enum class AodThumbnailShape {
    ROUNDED,
    SQUARE,
    CIRCLE,
    PILL,
    ARCH,
    SLANTED,
    DIAMOND,
    PENTAGON,
    TRIANGLE,
    HEART,
    FLOWER,
    CLOVER_4,
    COOKIE_6,
    COOKIE_9,
    SUNNY,
    SOFT_BURST,
    GHOSTISH,
    PIXEL_CIRCLE,
}

enum class AodBackgroundStyle {
    PURE_BLACK,
    SOFT_RADIAL,
    TONAL_EDGE,
    AMBIENT_GLOW,
}

enum class AodAccentStyle {
    MONOCHROME,
    THEME,
}

enum class AodContentPosition {
    TOP,
    CENTER,
    BOTTOM,
}

enum class AodTextAlignment {
    START,
    CENTER,
    END,
}

enum class AodControlStyle {
    FILLED,
    TONAL,
    MINIMAL,
}

enum class SliderStyle {
    Standard,
    Wavy,
    Thick,
    Circular,
    Simple,
}

const val SYSTEM_DEFAULT = "SYSTEM_DEFAULT"

enum class AppFontPreference {
    DEFAULT,
    SYSTEM,
    CUSTOM,
}

enum class PlaylistSuggestionSource {
    PLAYLIST_TITLE,
    PLAYLIST_CONTENT,
    BOTH,
}

val AppLanguageKey = stringPreferencesKey("appLanguage")
val ContentLanguageKey = stringPreferencesKey("contentLanguage")
val ContentCountryKey = stringPreferencesKey("contentCountry")
// Region spoofer for YouTube Music — overrides `YouTube.locale.gl` independently of
// `ContentCountryKey`. Set from Internet Settings so users can keep their content
// language/culture but pretend to YouTube Music that they are connecting from a
// different country (e.g. to play region-locked songs). Applied AFTER
// `ContentCountryKey` in `App.initializeDeferredAsync()` so the more specific
// Internet/region setting wins. `SYSTEM_DEFAULT` means "fall back to device locale
// or `ContentCountryKey`".
val YouTubeMusicRegionKey = stringPreferencesKey("youtubeMusicRegion")
val PlaylistSuggestionSourceKey = stringPreferencesKey("playlistSuggestionSource")
val EnableKugouKey = booleanPreferencesKey("enableKugou")
val EnableLrcLibKey = booleanPreferencesKey("enableLrclib")
val EnableBetterLyricsKey = booleanPreferencesKey("enableBetterLyrics")
val EnableBetterLyricsPortatoKey = booleanPreferencesKey("enableBetterLyricsPortato")
val EnableYouLyPlusLyricsKey = booleanPreferencesKey("enableYouLyPlusLyrics")
val EnableSimpMusicLyricsKey = booleanPreferencesKey("enableSimpMusicLyrics")
val EnableMegalobizLyricsKey = booleanPreferencesKey("enableMegalobizLyrics")
val EnablePaxsenixLyricsKey = booleanPreferencesKey("enablePaxsenixLyrics")
val EnablePaxsenixAppleMusicLyricsKey = booleanPreferencesKey("enablePaxsenixAppleMusicLyrics")
val EnablePaxsenixNeteaseLyricsKey = booleanPreferencesKey("enablePaxsenixNeteaseLyrics")
val EnablePaxsenixSpotifyLyricsKey = booleanPreferencesKey("enablePaxsenixSpotifyLyrics")
val EnablePaxsenixMusixmatchLyricsKey = booleanPreferencesKey("enablePaxsenixMusixmatchLyrics")
val EnablePaxsenixYouTubeLyricsKey = booleanPreferencesKey("enablePaxsenixYouTubeLyrics")
// User-configurable PaxSenix API key. When blank, PaxsenixLyrics falls
// back to the default (no-auth) behavior. When set, it's sent as an
// Authorization: Bearer header on every Paxsenix API request.
val PaxsenixApiKeyKey = stringPreferencesKey("paxsenixApiKey")
// User-configurable PaxSenix endpoint override. When blank, the default
// endpoint is used. When set, all Paxsenix API requests go to this URL.
val PaxsenixEndpointKey = stringPreferencesKey("paxsenixEndpoint")
val EnableUnisonLyricsKey = booleanPreferencesKey("enableUnisonLyrics")
val EnableTidalLyricsKey = booleanPreferencesKey("enableTidalLyrics")
val EnableDeezerLyricsKey = booleanPreferencesKey("enableDeezerLyrics")
// When ON, lyrics lookup first queries the four word-sync-capable providers
// (BetterLyrics, BetterLyrics Portato, YouLyPlus, Unison) in parallel and uses
// whichever returns word-synced lyrics (QRC/YRC/TTML with word timings). If none
// of those four return word-synced lyrics, the lookup falls back to the normal
// priority flow across all enabled providers.
val PrioritizeWordSyncedLyricsKey = booleanPreferencesKey("prioritizeWordSyncedLyrics")
val HideExplicitKey = booleanPreferencesKey("hideExplicit")
val HideVideoKey = booleanPreferencesKey("hideVideo")
// When ON, music videos render an inline video surface in the player. Default OFF so songs
// play as plain audio (album artwork shown, no video stream is loaded) unless the user opts in.
// Distinct from HideVideoKey which filters videos out of the library/queue entirely.
val EnableVideoPlaybackKey = booleanPreferencesKey("enableVideoPlayback")
// When ON (default OFF), leaving the app while a music video is playing enters Picture-in-
// Picture mode so the video keeps playing in a small floating window. Requires video playback
// to be enabled (EnableVideoPlaybackKey) — if video playback is off, this setting has no
// effect because there is no video surface to float.
val EnablePipModeKey = booleanPreferencesKey("enablePipMode")
val AllowAgeRestrictedKey = booleanPreferencesKey("allowAgeRestricted")
enum class DownloadSource {
    /**
     * Picks the best available source per song: tries Qobuz → Tidal → Deezer
     * (lossless FLAC) and falls back to YouTube Music (lossy MP3/AAC) only
     * when none of the lossless backends can resolve the track. This is the
     * default and the recommended setting — it guarantees FLAC whenever a
     * configured lossless provider has the track, without forcing the user
     * to manually switch sources.
     */
    AUTO,

    QOBUZ,

    /**
     * Community-hosted Qobuz mirror (mlc-ytify.kouzu.in), keyed by YouTube video id
     * rather than a Qobuz catalogue id, so it needs no Source Pool account at all.
     * Mirrors [AudioSourceType.QOBUZ_BACKUP] on the playback side and is gated by the
     * same `QobuzBackupEnabledKey` toggle. Not in [DownloadSourceConfig.REQUIRES_POOL].
     */
    QOBUZ_BACKUP,
    TIDAL,

    /**
     * Deezer lookup — uses Deezer's public catalogue API to resolve a FLAC
     * stream URL. Falls back to the next source in [AUTO] order when the
     * track isn't on Deezer or the API can't resolve a full stream.
     */
    DEEZER,

    /**
     * JioSaavn lookup — uses the public JioSaavn API to resolve a 96/160/320 kbps
     * AAC stream URL. Falls back to the next source in [AUTO] order when the
     * track isn't on JioSaavn. Does NOT require a Source Pool account (the
     * public API is used directly), so it is NOT in [DownloadSourceConfig.REQUIRES_POOL].
     */
    JIOSAAVN,

    YOUTUBE_MUSIC,
}

val DownloadSourceKey = stringPreferencesKey("downloadSource")

// CSV of DownloadSource names in the user's chosen priority order. Empty/null falls back to
// DownloadSourceConfig.DEFAULT_ORDER (Qobuz, Tidal, Deezer, YouTube Music). Consumed by
// DownloadUtil to drive the per-song download source chain — replacing the old single-pick
// DownloadSourceKey. The old key is kept for migration / backup compatibility.
val DownloadSourceOrderKey = stringPreferencesKey("downloadSourceOrder")

/**
 * Helpers for the download-source priority list. Mirrors [AudioSourceConfig] in the audiosource
 * package — same CSV serialization, append-missing-on-parse semantics, and a single DEFAULT_ORDER.
 *
 * The three sources [QOBUZ], [TIDAL], [DEEZER] require a Source Pool account to be configured
 * (see `PoolAccountManager.isEnabled`). [JIOSAAVN] uses the public JioSaavn API (no pool needed).
 * [YOUTUBE_MUSIC] always works.
 */
object DownloadSourceConfig {
    val DEFAULT_ORDER: List<DownloadSource> =
        listOf(
            DownloadSource.QOBUZ,
            DownloadSource.QOBUZ_BACKUP,
            DownloadSource.TIDAL,
            DownloadSource.DEEZER,
            DownloadSource.JIOSAAVN,
            DownloadSource.YOUTUBE_MUSIC,
        )

    /** Sources that need a Source Pool account configured to be usable for downloads. */
    val REQUIRES_POOL: Set<DownloadSource> =
        setOf(DownloadSource.QOBUZ, DownloadSource.TIDAL, DownloadSource.DEEZER)

    /**
     * Cache-key prefix a source's downloaded bytes are stored under, e.g. `"qobuz_backup:"`.
     *
     * `DownloadUtil` namespaces each source's cache entries so bytes from one source can never be
     * served for another, then has to check every prefix when deciding whether a song is already
     * fully cached. Deriving the list here rather than writing it out at each of those call sites
     * is what keeps a newly added source from being silently skipped by the cache lookups — which
     * is exactly what happened to [DownloadSource.QOBUZ_BACKUP] and [DownloadSource.JIOSAAVN]: they
     * resolved and downloaded fine, but their cached bytes were invisible to the completeness check
     * and to the purge-on-failure path, so a download that had already succeeded was fetched again.
     *
     * [DownloadSource.AUTO] and [DownloadSource.YOUTUBE_MUSIC] are excluded: neither ever writes a
     * prefixed key (the YouTube fallback uses the bare media id).
     *
     * `Locale.US` matters — `TIDAL` and `JIOSAAVN` both contain `I`, which lowercases to a dotless
     * `ı` under a Turkish locale and would not match the key that was written.
     */
    val CACHE_KEY_PREFIXES: List<String> =
        DownloadSource.entries
            .filterNot { it == DownloadSource.AUTO || it == DownloadSource.YOUTUBE_MUSIC }
            .map { "${it.name.lowercase(Locale.US)}:" }

    private fun parseType(name: String): DownloadSource? =
        runCatching { DownloadSource.valueOf(name.trim().uppercase()) }.getOrNull()

    /**
     * Resolves the effective ordered list of all download sources from the stored CSV, preserving
     * the user's chosen order and slotting any missing sources in above [DownloadSource.YOUTUBE_MUSIC].
     *
     * Mirrors `AudioSourceConfig.parseOrder`, and for the same reason: appending meant that a user
     * who had touched the order picker before a source existed got that source listed *below*
     * YouTube Music, where the list reads as if it ends. Every real download source sits above
     * YouTube Music in [DEFAULT_ORDER], so inserting there is both correct and the one placement
     * that cannot disturb the order the user actually chose.
     */
    fun parseOrder(rawOrder: String?): List<DownloadSource> {
        val stored =
            rawOrder
                ?.split(',')
                ?.mapNotNull { parseType(it) }
                ?.distinct()
                .orEmpty()
        if (stored.isEmpty()) return DEFAULT_ORDER

        val missing = DEFAULT_ORDER.filterNot { it in stored }
        if (missing.isEmpty()) return stored

        val merged = mutableListOf<DownloadSource>()
        var inserted = false
        for (source in stored) {
            if (!inserted && source == DownloadSource.YOUTUBE_MUSIC) {
                merged.addAll(missing)
                inserted = true
            }
            merged.add(source)
        }
        // YouTube Music absent from the stored order means it is itself missing, and it is last in
        // DEFAULT_ORDER, so appending the block still leaves it as the final fallback.
        if (!inserted) merged.addAll(missing)
        return merged
    }

    /** Serialize an order back to the CSV form for storage. */
    fun serialize(order: List<DownloadSource>): String = order.joinToString(",") { it.name }
}

val AiContentFilterEnabledKey = booleanPreferencesKey("aiContentFilterEnabled")
val AiContentFilterIncludeModerateKey = booleanPreferencesKey("aiContentFilterIncludeModerate")
val AiContentFilterLastUpdatedKey = longPreferencesKey("aiContentFilterLastUpdated")
val AiMixLastGeneratedAtKey = longPreferencesKey("aiMixLastGeneratedAt")
val ProxyEnabledKey = booleanPreferencesKey("proxyEnabled")
val ProxyHostKey = stringPreferencesKey("proxyHost")
val ProxyPortKey = intPreferencesKey("proxyPort")
val ProxyUsernameKey = stringPreferencesKey("proxyUsername")
val ProxyPasswordKey = stringPreferencesKey("proxyPassword")
val ProxyTypeKey = stringPreferencesKey("proxyType")
val EnableDnsOverHttpsKey = booleanPreferencesKey("enableDnsOverHttps")
val DnsOverHttpsProviderKey = stringPreferencesKey("dnsOverHttpsProvider")
val TidalInstanceUrlKey = stringPreferencesKey("tidalInstanceUrl")
val StreamBypassProxyKey = booleanPreferencesKey("streamBypassProxy")
val IpRotationEnabledKey = booleanPreferencesKey("ipRotationEnabled")
val YtmSyncKey = booleanPreferencesKey("ytmSync")
val ForceSyncOnAccountSwitchKey = booleanPreferencesKey("forceSyncOnAccountSwitch")
val SelectedYtmPlaylistsKey = stringPreferencesKey("ytm_selected_playlists")
val LocalSongsMinDurationSecondsKey = intPreferencesKey("local_songs_min_duration_seconds")
val LocalSongsIncludedFoldersKey = stringSetPreferencesKey("local_songs_included_folders")
val LocalSongsExcludedFoldersKey = stringSetPreferencesKey("local_songs_excluded_folders")
val LocalSongsSortTypeKey = stringPreferencesKey("local_songs_sort_type")
val LocalSongsSortDescendingKey = booleanPreferencesKey("local_songs_sort_descending")

val TogetherDisplayNameKey = stringPreferencesKey("together_display_name")
val TogetherClientIdKey = stringPreferencesKey("together_client_id")
val TogetherDefaultPortKey = intPreferencesKey("together_default_port")
val TogetherAllowGuestsToAddTracksKey = booleanPreferencesKey("together_allow_guests_add_tracks")
val TogetherAllowGuestsToControlPlaybackKey = booleanPreferencesKey("together_allow_guests_control_playback")
val TogetherRequireHostApprovalToJoinKey = booleanPreferencesKey("together_require_host_approval_to_join")
val TogetherLastJoinLinkKey = stringPreferencesKey("together_last_join_link")
val TogetherWelcomeShownKey = booleanPreferencesKey("together_welcome_shown")

// ListenBrainz scrobbling
val ListenBrainzEnabledKey = booleanPreferencesKey("listenbrainz_enabled")
val ListenBrainzTokenKey = stringPreferencesKey("listenbrainz_token")

val AiProviderKey = stringPreferencesKey("ai_provider")
val AiCustomEndpointKey = stringPreferencesKey("ai_custom_endpoint")
val AiApiKeyKey = stringPreferencesKey("ai_api_key")
val AiApiValidationStatusKey = stringPreferencesKey("ai_api_validation_status")
val AiSelectedModelKey = stringPreferencesKey("ai_selected_model")
val AiCustomModelKey = stringPreferencesKey("ai_custom_model")

// --- DeepL / OpenRouter / Mistral translation providers (ported from vivi-music) ---
// DeepL: API key with `:fx` suffix routes through api-free.deepl.com; otherwise api.deepl.com.
val DeeplApiKeyKey = stringPreferencesKey("deeplApiKey")
// "default" / "more" / "less" — controls DeepL's formality parameter.
val DeeplFormalityKey = stringPreferencesKey("deeplFormality")

// OpenRouter: chat-completions style translation. User can override base URL and model.
val OpenRouterApiKeyKey = stringPreferencesKey("openRouterApiKey")
val OpenRouterBaseUrlKey = stringPreferencesKey("openRouterBaseUrl")
val OpenRouterModelKey = stringPreferencesKey("openRouterModel")

// Translation mode for OpenRouter: "translate" (full translation) or "romanize" (transliteration).
val TranslateModeKey = stringPreferencesKey("translateMode")
// Target language code for translation (e.g. "en", "zh-CN", "ja").
val TranslateLanguageKey = stringPreferencesKey("translateLanguage")
// Optional source language hint (null = auto-detect).
val TranslateSourceLanguageKey = stringPreferencesKey("translateSourceLanguage")

// Hides the AI-generated "Top mixes" section in the library Mix tab (and stops auto-generation).
val HideAiMixKey = booleanPreferencesKey("hide_ai_mix")

// Whether the user pressed the pause button on the Debug Logs screen. Persisted so the pause
// state survives screen navigation AND process restarts — previously the flag lived in a
// HiltViewModel's MutableStateFlow, which was destroyed on screen exit, so the user's pause
// was silently dropped every time they navigated away.
val LogcatPausedKey = booleanPreferencesKey("logcatPaused")

// When on, any foreign-language lyrics that have an AI provider configured will be translated
// to the user's preferred language automatically on lyrics load — no manual tap through the
// translate dialog required. The "Translation saved" toast is also suppressed in this mode so
// background translations don't fire a notification every time a new song starts.
val AutoTranslateLyricsKey = booleanPreferencesKey("autoTranslateLyrics")

// Set of uppercase language codes (e.g. "JAPANESE", "KOREAN", "CHINESE_SIMPLIFIED") that should NOT
// be auto-translated even when [AutoTranslateLyricsKey] is on. The user picks them via the
// multi-select dialog in AiIntegrationSettings. Codes match `TranslatorLang.code` in
// assets/translator_languages.json — note that there is no plain "CHINESE" there, which is why the
// comparison goes through `LyricsUtils.matchesExcludedLanguage` rather than a direct set lookup.
val AutoTranslateExcludedLanguagesKey = stringSetPreferencesKey("autoTranslateExcludedLanguages")

// ── AI romanisation ──
// Master switch. When on, the configured AI provider supplies the romanisation shown above each
// lyric line, and the built-in engines (Kuromoji for Japanese, the hand-written Korean/Hindi tables,
// ICU for everything else) stop running entirely — see `LyricsRomanizationPreferences.aiHandled`.
// Two engines at once would mix romanisation schemes within one song, and the whole reason to reach
// for a model is that its output is better than the tables'.
val AiRomanizeLyricsKey = booleanPreferencesKey("aiRomanizeLyrics")

// Fetch the romanisation as soon as lyrics are shown, rather than waiting for the user to ask via
// Lyrics menu → Romanise with AI. Separate from [AiRomanizeLyricsKey] because these are billed
// network calls: switching the feature on should not commit the user to one request per track.
val AutoAiRomanizeLyricsKey = booleanPreferencesKey("autoAiRomanizeLyrics")

// Uppercase language codes (e.g. "JAPANESE", "KOREAN") to leave alone even when AI romanisation is
// on — for scripts the user already reads. Same code space and same multi-select dialog as
// [AutoTranslateExcludedLanguagesKey]; codes match `TranslatorLang.code` in
// assets/translator_languages.json.
val AiRomanizeExcludedLanguagesKey = stringSetPreferencesKey("aiRomanizeExcludedLanguages")

// Set by the "Never show again" pill on the startup update popup. Stores the
// "<versionName>|<versionCode>" of the version the user suppressed the popup for, so we
// can detect when they've upgraded and re-enable the popup for the next new release.
// Empty string means "not suppressed".
val NeverShowUpdatePopupKey = stringPreferencesKey("neverShowUpdatePopupVersion")

val HideLikedSongsCardKey = booleanPreferencesKey("hide_liked_songs_card")
val HideOfflineCardKey = booleanPreferencesKey("hide_offline_card")
val HideCachedCardKey = booleanPreferencesKey("hide_cached_card")
val HideLocalFilesCardKey = booleanPreferencesKey("hide_local_files_card")
val HideTop50CardKey = booleanPreferencesKey("hide_top50_card")

enum class AiProvider {
    CHATGPT,
    GEMINI,
    OPENROUTER,
    CUSTOM,
    DEEPL,
    MISTRAL,
    NONE,
}

enum class AiApiValidationStatus {
    UNKNOWN,
    SUCCESS,
    FAILED,
}

// Last.fm scrobbling
val LastFMSessionKey = stringPreferencesKey("lastfmSession")
val LastFMUsernameKey = stringPreferencesKey("lastfmUsername")
val LastFMProviderKey = stringPreferencesKey("lastfmProvider")
val LastFMCustomEndpointKey = stringPreferencesKey("lastfmCustomEndpoint")
val LastFMApiKeyOverrideKey = stringPreferencesKey("lastfmApiKeyOverride")
val LastFMSecretOverrideKey = stringPreferencesKey("lastfmSecretOverride")
val LibreFMApiKeyOverrideKey = stringPreferencesKey("librefmApiKeyOverride")
val LibreFMSecretOverrideKey = stringPreferencesKey("librefmSecretOverride")
val CustomScrobbleApiKeyOverrideKey = stringPreferencesKey("customScrobbleApiKeyOverride")
val CustomScrobbleSecretOverrideKey = stringPreferencesKey("customScrobbleSecretOverride")
val LastFMCredentialsMigratedKey = booleanPreferencesKey("lastfmCredentialsMigrated")
val EnableLastFMScrobblingKey = booleanPreferencesKey("lastfmScrobblingEnable")
val LastFMUseNowPlaying = booleanPreferencesKey("lastfmUseNowPlaying")
val ScrobbleDelayPercentKey = floatPreferencesKey("scrobbleDelayPercent")
val ScrobbleMinSongDurationKey = intPreferencesKey("scrobbleMinSongDuration")
val ScrobbleDelaySecondsKey = intPreferencesKey("scrobbleDelaySeconds")

enum class LastFmProvider {
    LASTFM,
    LIBREFM,
    CUSTOM,
}

val AudioQualityKey = stringPreferencesKey("audioQuality")

val NetworkMeteredKey = booleanPreferencesKey("networkMetered")
val LowDataModeKey = NetworkMeteredKey

enum class AudioQuality {
    AUTO,
    HIGH,
    HIGHEST,
    LOW,
}

val PlayerStreamClientKey = stringPreferencesKey("playerStreamClient")
val AutoChoosePlaybackClientKey = booleanPreferencesKey("autoChoosePlaybackClient")

enum class PlayerStreamClient {
    ANDROID_VR,
    WEB_REMIX,
    HI_RES_LOSSLESS,
    IOS,
    TVHTML5,
    ANDROID_MUSIC,
}

val PersistentQueueKey = booleanPreferencesKey("persistentQueue")
val PermanentShuffleKey = booleanPreferencesKey("permanentShuffle")
val SkipSilenceKey = booleanPreferencesKey("skipSilence")
val AudioNormalizationKey = booleanPreferencesKey("audioNormalization")
val AudioOffload = booleanPreferencesKey("audioOffload")
val CrossfadeEnabledKey = booleanPreferencesKey("crossfadeEnabled")
val CrossfadeDurationKey = floatPreferencesKey("crossfadeDuration")
val CrossfadeGaplessKey = booleanPreferencesKey("crossfadeGapless")
val AutoLoadMoreKey = booleanPreferencesKey("autoLoadMore")
val AutoDownloadOnLikeKey = booleanPreferencesKey("autoDownloadOnLike")
val AutoSkipNextOnErrorKey = booleanPreferencesKey("autoSkipNextOnError")
val PauseOnDeviceMuteKey = booleanPreferencesKey("pauseOnDeviceMute")
val DeviceMutePlaybackRecoveryVolumeKey = intPreferencesKey("deviceMutePlaybackRecoveryVolume")
val AutoStartOnBluetoothKey = booleanPreferencesKey("autoStartOnBluetooth")
val StopMusicOnTaskClearKey = booleanPreferencesKey("stopMusicOnTaskClear")
val WakelockKey = booleanPreferencesKey("wakelock")
val ArtistSeparatorsKey = stringPreferencesKey("artistSeparators")
val ExternalDownloaderEnabledKey = booleanPreferencesKey("externalDownloaderEnabled")
val ExternalDownloaderPackageKey = stringPreferencesKey("externalDownloaderPackage")
val PlaylistTagsFilterKey = stringPreferencesKey("playlistTagsFilter")
val ShowHomeCategoryChipsKey = booleanPreferencesKey("showHomeCategoryChips")
val ShowTagsInLibraryKey = booleanPreferencesKey("showTagsInLibrary")

/**
 * When `true`, the Home feed collapses to a focused subset:
 *   - Jump-back-in hero (always rendered, regardless of this toggle)
 *   - Recently Played
 *   - Keep Listening
 *   - Live Performances (the "Live performance"-titled remote shelf)
 *
 * All other home sections (category chips, remote/local quick picks,
 * speed dial, account playlists, forgotten favorites, similar
 * recommendations, and the rest of the remote homePage sections such
 * as "Fresh finds" / "Old favourites") are hidden.
 *
 * Default is `false` so a fresh install shows the full home feed
 * (matches upstream rukamori/ArchiveTune).
 */
val MinimalHomeModeKey = booleanPreferencesKey("minimalHomeMode")

val EqualizerEnabledKey = booleanPreferencesKey("equalizerEnabled")
val EqualizerControlModeKey = stringPreferencesKey("equalizerControlMode")
val EqualizerBandLevelsMbKey = stringPreferencesKey("equalizerBandLevelsMb")
val EqualizerAutoHeadroomEnabledKey = booleanPreferencesKey("equalizerAutoHeadroomEnabled")
val EqualizerOutputGainEnabledKey = booleanPreferencesKey("equalizerOutputGainEnabled")
val EqualizerOutputGainMbKey = intPreferencesKey("equalizerOutputGainMb")
val EqualizerBassBoostEnabledKey = booleanPreferencesKey("equalizerBassBoostEnabled")
val EqualizerBassBoostStrengthKey = intPreferencesKey("equalizerBassBoostStrength")
val EqualizerVirtualizerEnabledKey = booleanPreferencesKey("equalizerVirtualizerEnabled")
val EqualizerVirtualizerStrengthKey = intPreferencesKey("equalizerVirtualizerStrength")
val EqualizerSelectedProfileIdKey = stringPreferencesKey("equalizerSelectedProfileId")
val EqualizerCustomProfilesJsonKey = stringPreferencesKey("equalizerCustomProfilesJson")

val MaxImageCacheSizeKey = intPreferencesKey("maxImageCacheSize")
val SmartTrimmerKey = booleanPreferencesKey("smartTrimmer")
val MaxSongCacheSizeKey = intPreferencesKey("maxSongCacheSize")
val MaxCanvasCacheSizeKey = intPreferencesKey("maxCanvasCacheSize")
val StorageFolderIdKey = stringPreferencesKey("storageFolderId")
val StorageFolderTreeUriKey = stringPreferencesKey("storageFolderTreeUri")
val StorageFolderPathKey = stringPreferencesKey("storageFolderPath")
val StorageFolderDisplayNameKey = stringPreferencesKey("storageFolderDisplayName")

val PauseListenHistoryKey = booleanPreferencesKey("pauseListenHistory")
val PauseSearchHistoryKey = booleanPreferencesKey("pauseSearchHistory")

// When true (default), plays are reported to the logged-in YouTube account's listen history even
// when the audio was streamed from another source (Tidal/Qobuz). History is keyed off the YouTube
// video id, so it stays source-independent; this toggle lets the user opt out of the remote report
// without affecting the local play-count/history DB (governed by PauseListenHistoryKey).
val SyncPlaybackToYouTubeHistoryKey = booleanPreferencesKey("syncPlaybackToYouTubeHistory")
val DisableScreenshotKey = booleanPreferencesKey("disableScreenshot")

// Integration screen: account cards. YouTube is always shown; Last.fm and Discord
// cards can be pinned to the top of the Integration screen by the user.
val PinLastFmCardKey = booleanPreferencesKey("pinLastFmCard")
val PinDiscordCardKey = booleanPreferencesKey("pinDiscordCard")

// Last.fm dashboard: prefer YouTube hq720 thumbnails over the Last.fm image
// array. When enabled, the dashboard skips bestArtwork(track.image) (which
// can return non-square / brown-matted images from Last.fm's catalogue) and
// goes straight to resolveCatalogueCover, which starts with YouTube hq720
// (clean 16:9, no baked-in bars). Useful for users whose Last.fm catalogue
// has many low-quality or padded artwork images.
val LastFmPreferYtThumbnailsKey = booleanPreferencesKey("lastfmPreferYtThumbnails")

val DiscordTokenKey = stringPreferencesKey("discordToken")
val DiscordRefreshTokenKey = stringPreferencesKey("discordRefreshToken")
val DiscordTokenExpiresAtKey = longPreferencesKey("discordTokenExpiresAt")
val DiscordInfoDismissedKey = booleanPreferencesKey("discordInfoDismissed")
val DiscordUsernameKey = stringPreferencesKey("discordUsername")
val DiscordNameKey = stringPreferencesKey("discordName")
val DiscordAvatarUrlKey = stringPreferencesKey("discordAvatarUrl")
val EnableDiscordRPCKey = booleanPreferencesKey("discordRPCEnable")

// Discord activity customization keys
val DiscordActivityNameKey = stringPreferencesKey("discordActivityName")
val DiscordActivityDetailsKey = stringPreferencesKey("discordActivityDetails")
val DiscordActivityStateKey = stringPreferencesKey("discordActivityState")

// Custom button labels and urls for Discord activity buttons
val DiscordActivityButton1LabelKey = stringPreferencesKey("discordActivityButton1Label")
val DiscordActivityButton1UrlSourceKey = stringPreferencesKey("discordActivityButton1UrlSource")
val DiscordActivityButton1CustomUrlKey = stringPreferencesKey("discordActivityButton1CustomUrl")
val DiscordActivityButton2LabelKey = stringPreferencesKey("discordActivityButton2Label")
val DiscordActivityButton2UrlSourceKey = stringPreferencesKey("discordActivityButton2UrlSource")
val DiscordActivityButton2CustomUrlKey = stringPreferencesKey("discordActivityButton2CustomUrl")
val DiscordActivityButton1EnabledKey = booleanPreferencesKey("discordActivityButton1Enabled")
val DiscordActivityButton2EnabledKey = booleanPreferencesKey("discordActivityButton2Enabled")
val DiscordShowWhenPausedKey = booleanPreferencesKey("discordShowWhenPaused")

// Activity type for Discord presence (PLAYING, STREAMING, LISTENING, WATCHING, COMPETING)
val DiscordActivityTypeKey = stringPreferencesKey("discordActivityType")
val DiscordPresenceStatusKey = stringPreferencesKey("discordPresenceStatus") // "ONLINE", "IDLE", "DND", "INVISIBLE"

// Discord image selection keys
// Values for type keys: "thumbnail", "artist", "appicon", "custom"
val DiscordLargeImageTypeKey = stringPreferencesKey("discordLargeImageType")
val DiscordLargeTextSourceKey = stringPreferencesKey("discordLargeTextSource")
val DiscordLargeTextCustomKey = stringPreferencesKey("discordLargeTextCustom")
val DiscordLargeImageCustomUrlKey = stringPreferencesKey("discordLargeImageCustomUrl")
val DiscordSmallImageTypeKey = stringPreferencesKey("discordSmallImageType")
val DiscordSmallImageCustomUrlKey = stringPreferencesKey("discordSmallImageCustomUrl")

// Activity platform (discord client platform) selection
val DiscordActivityPlatformKey = stringPreferencesKey("discordActivityPlatform")

val TranslatorContextsKey = stringPreferencesKey("translatorContexts")
val TranslatorTargetLangKey = stringPreferencesKey("translatorTargetLang")
val EnableTranslatorKey = booleanPreferencesKey("enableTranslator")

val ChipSortTypeKey = stringPreferencesKey("chipSortType")
val SongSortTypeKey = stringPreferencesKey("songSortType")
val SongSortDescendingKey = booleanPreferencesKey("songSortDescending")
val PlaylistSongSortTypeKey = stringPreferencesKey("playlistSongSortType")
val PlaylistSongSortDescendingKey = booleanPreferencesKey("playlistSongSortDescending")
val AutoPlaylistSongSortTypeKey = stringPreferencesKey("autoPlaylistSongSortType")
val AutoPlaylistSongSortDescendingKey = booleanPreferencesKey("autoPlaylistSongSortDescending")
val ArtistSortTypeKey = stringPreferencesKey("artistSortType")
val ArtistSortDescendingKey = booleanPreferencesKey("artistSortDescending")
val AlbumSortTypeKey = stringPreferencesKey("albumSortType")
val AlbumSortDescendingKey = booleanPreferencesKey("albumSortDescending")
val PlaylistSortTypeKey = stringPreferencesKey("playlistSortType")
val PlaylistSortDescendingKey = booleanPreferencesKey("playlistSortDescending")
val ArtistSongSortTypeKey = stringPreferencesKey("artistSongSortType")
val ArtistSongSortDescendingKey = booleanPreferencesKey("artistSongSortDescending")
val MixSortTypeKey = stringPreferencesKey("mixSortType")
val MixSortDescendingKey = booleanPreferencesKey("albumSortDescending")

val SongFilterKey = stringPreferencesKey("songFilter")
val ArtistFilterKey = stringPreferencesKey("artistFilter")
val AlbumFilterKey = stringPreferencesKey("albumFilter")

val LastLikeSongSyncKey = longPreferencesKey("last_like_song_sync")
val LastLibSongSyncKey = longPreferencesKey("last_library_song_sync")
val LastAlbumSyncKey = longPreferencesKey("last_album_sync")
val LastArtistSyncKey = longPreferencesKey("last_artist_sync")
val LastPlaylistSyncKey = longPreferencesKey("last_playlist_sync")

val ArtistViewTypeKey = stringPreferencesKey("artistViewType")
val AlbumViewTypeKey = stringPreferencesKey("albumViewType")
val PlaylistViewTypeKey = stringPreferencesKey("playlistViewType")

val PlaylistEditLockKey = booleanPreferencesKey("playlistEditLock")
val QuickPicksKey = stringPreferencesKey("discover")

val NewsLastReadTimestampKey = longPreferencesKey("news_last_read_timestamp")
val SpeedDialSongIdsKey = stringPreferencesKey("speedDialSongIds")
val PreferredLyricsProviderKey = stringPreferencesKey("lyricsProvider")
val LyricsProviderOrderKey = stringPreferencesKey("lyricsProviderOrder")
val ArtworkProviderOrderKey = stringPreferencesKey("artworkProviderOrder")
val QueueEditLockKey = booleanPreferencesKey("queueEditLock")

enum class LibraryViewType {
    LIST,
    GRID,
    ;

    fun toggle() =
        when (this) {
            LIST -> GRID
            GRID -> LIST
        }
}

enum class QuickPicksDisplayMode {
    CARD,
    LIST,
}

val QuickPicksDisplayModeKey = stringPreferencesKey("quickPicksDisplayMode")

enum class SongFilter {
    LIBRARY,
    LIKED,
    DOWNLOADED,
}

enum class ArtistFilter {
    LIBRARY,
    LIKED,
}

enum class AlbumFilter {
    LIBRARY,
    LIKED,
    DOWNLOADED,
    DOWNLOADED_FULL,
}

enum class SongSortType {
    CREATE_DATE,
    NAME,
    ARTIST,
    PLAY_TIME,
}

enum class PlaylistSongSortType {
    CUSTOM,
    CREATE_DATE,
    NAME,
    ARTIST,
    PLAY_TIME,
}

enum class AutoPlaylistSongSortType {
    CREATE_DATE,
    NAME,
    ARTIST,
    PLAY_TIME,
}

enum class ArtistSortType {
    CREATE_DATE,
    NAME,
    SONG_COUNT,
    PLAY_TIME,
}

enum class ArtistSongSortType {
    CREATE_DATE,
    NAME,
    PLAY_TIME,
}

enum class AlbumSortType {
    CREATE_DATE,
    NAME,
    ARTIST,
    YEAR,
    SONG_COUNT,
    LENGTH,
    PLAY_TIME,
}

enum class PlaylistSortType {
    CREATE_DATE,
    NAME,
    SONG_COUNT,
    LAST_UPDATED,
    CUSTOM,
}

enum class MixSortType {
    CREATE_DATE,
    NAME,
    LAST_UPDATED,
}

enum class GridItemSize {
    BIG,
    SMALL,
}

enum class MyTopFilter {
    ALL_TIME,
    DAY,
    WEEK,
    MONTH,
    YEAR,
    ;

    fun toTimeMillis(): Long =
        when (this) {
            DAY -> {
                LocalDateTime
                    .now()
                    .minusDays(1)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()
            }

            WEEK -> {
                LocalDateTime
                    .now()
                    .minusWeeks(1)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()
            }

            MONTH -> {
                LocalDateTime
                    .now()
                    .minusMonths(1)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()
            }

            YEAR -> {
                LocalDateTime
                    .now()
                    .minusMonths(12)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()
            }

            ALL_TIME -> {
                0
            }
        }
}

enum class QuickPicks {
    QUICK_PICKS,
    LAST_LISTEN,
    DONT_SHOW,
}

enum class PreferredLyricsProvider {
    BETTER_LYRICS,
    BETTER_LYRICS_PORTATO,
    YOULY_PLUS,
    LRCLIB,
    KUGOU,
    MEGALOBIZ,
    SIMPMUSIC,
    UNISON,
    PAXSENIX_APPLE_MUSIC,
    APPLE_MUSIC,
    PAXSENIX_NETEASE,
    PAXSENIX_SPOTIFY,
    PAXSENIX_MUSIXMATCH,
    PAXSENIX_YOUTUBE,
    TIDAL,
    DEEZER,
    MUSIXMATCH_EXPERIMENTAL,
}

val DefaultLyricsProviderOrder =
    listOf(
        PreferredLyricsProvider.BETTER_LYRICS,
        PreferredLyricsProvider.BETTER_LYRICS_PORTATO,
        PreferredLyricsProvider.YOULY_PLUS,
        PreferredLyricsProvider.LRCLIB,
        PreferredLyricsProvider.KUGOU,
        PreferredLyricsProvider.MEGALOBIZ,
        PreferredLyricsProvider.SIMPMUSIC,
        PreferredLyricsProvider.UNISON,
        PreferredLyricsProvider.PAXSENIX_APPLE_MUSIC,
        PreferredLyricsProvider.APPLE_MUSIC,
        PreferredLyricsProvider.PAXSENIX_NETEASE,
        PreferredLyricsProvider.PAXSENIX_SPOTIFY,
        PreferredLyricsProvider.PAXSENIX_MUSIXMATCH,
        PreferredLyricsProvider.PAXSENIX_YOUTUBE,
        PreferredLyricsProvider.TIDAL,
        PreferredLyricsProvider.DEEZER,
        PreferredLyricsProvider.MUSIXMATCH_EXPERIMENTAL,
    )

fun deserializeLyricsProviderOrder(orderStr: String?): List<PreferredLyricsProvider> {
    if (orderStr.isNullOrBlank()) return DefaultLyricsProviderOrder

    val parsed =
        orderStr
            .split(",")
            .mapNotNull { name ->
                PreferredLyricsProvider.entries.find { it.name == name.trim() }
            }.distinct()

    val normalized =
        when {
            parsed.take(3) ==
                listOf(
                    PreferredLyricsProvider.LRCLIB,
                    PreferredLyricsProvider.KUGOU,
                    PreferredLyricsProvider.BETTER_LYRICS,
                )
            -> listOf(PreferredLyricsProvider.BETTER_LYRICS) + parsed.filterNot { it == PreferredLyricsProvider.BETTER_LYRICS }

            else -> parsed
        }

    val missing = DefaultLyricsProviderOrder.filterNot { it in normalized }
    return normalized + missing
}

/**
 * Artwork providers that can be prioritised by the user. The order in this enum is NOT the
 * priority — the user-configured order (stored in [ArtworkProviderOrderKey]) determines which
 * provider is tried first when resolving artwork for a song. If the top-priority provider has
 * no artwork for the current song, the resolver falls back to the next provider in the list.
 *
 * - [LOCAL_EMBEDDED]: artwork extracted from a local file. Always wins for local media
 *   regardless of priority order (a local file's embedded cover is the authoritative source).
 * - [ORIGINAL_METADATA]: artwork URL that arrived with the original media metadata
 *   (YouTube/innertube/DB `thumbnailUrl`). This is the default artwork for streaming songs.
 * - [TIDAL]: artwork fetched from Tidal as a fallback when no original artwork exists.
 * - [SPOTIFY_CANVAS]: Spotify Canvas video artwork (looping video, fetched via the
 *   `mlc.kouzu.in` canvas API).
 * - [ARCHIVETUNE_CANVAS]: Apple Music motion artwork (animated cover art fetched
 *   from Apple's MusicKit/AMP API).
 */
enum class PreferredArtworkProvider {
    LOCAL_EMBEDDED,
    ORIGINAL_METADATA,
    TIDAL,
    SPOTIFY_CANVAS,
    ARCHIVETUNE_CANVAS,
}

val DefaultArtworkProviderOrder =
    listOf(
        PreferredArtworkProvider.LOCAL_EMBEDDED,
        PreferredArtworkProvider.ORIGINAL_METADATA,
        PreferredArtworkProvider.TIDAL,
        PreferredArtworkProvider.SPOTIFY_CANVAS,
        PreferredArtworkProvider.ARCHIVETUNE_CANVAS,
    )

fun deserializeArtworkProviderOrder(orderStr: String?): List<PreferredArtworkProvider> {
    if (orderStr.isNullOrBlank()) return DefaultArtworkProviderOrder

    val parsed =
        orderStr
            .split(",")
            .mapNotNull { name ->
                PreferredArtworkProvider.entries.find { it.name == name.trim() }
            }.distinct()

    val missing = DefaultArtworkProviderOrder.filterNot { it in parsed }
    return parsed + missing
}

enum class PlayerButtonsStyle {
    DEFAULT,
    SECONDARY,
}

/** Home screen layout style. DEFAULT = this fork's feed; RUKAMORI = upstream rukamori layout. */
enum class HomeScreenStyle {
    DEFAULT,
    RUKAMORI,

    /**
     * Spotify's own home feed in place of the YouTube one. Only reachable with a Spotify
     * session (SpotifySpDcKey): the route falls back to DEFAULT when that is blank, so picking
     * this without signing in cannot leave the user on a permanently empty home.
     */
    SPOTIFY,
}

val HomeScreenStyleKey = stringPreferencesKey("homeScreenStyle")

enum class PlayerDesignStyle {
    V1,
    V2,
    V3,
    V4,
    V5,
    V6,
    V7,
    V8,
    V9,
    APPLE_MUSIC,
    V10,
}

enum class PlayerBackgroundStyle {
    DEFAULT,
    GRADIENT,
    CUSTOM,
    BLUR,
    COLORING,
    BLUR_GRADIENT,
    GLOW,
    GLOW_ANIMATED,
}

enum class LyricsBackgroundStyle {
    DEFAULT,
    FOLLOW_THEME,
    COLORING,
    MOVING_BLUR,
    CUSTOM;

    fun resolveFor(playerBackgroundStyle: PlayerBackgroundStyle): LyricsBackgroundStyle =
        when {
            playerBackgroundStyle == PlayerBackgroundStyle.CUSTOM -> CUSTOM
            this == CUSTOM -> DEFAULT
            else -> this
        }
}

enum class MiniPlayerBackgroundStyle {
    THEME,
    GRADIENT,
    GLOW,
    FROSTED,
    LIQUID_GLASS,
}

// Bottom navigation bar look: DEFAULT keeps the docked full-width bar; FLOATING detaches it into
// a pill with larger margins that never pairs with the mini player.
enum class NavigationBarStyle {
    DEFAULT,
    FLOATING,
}

val NavigationBarStyleKey = stringPreferencesKey("navigationBarStyle")

// Draws a frosted (blurred app content) backdrop behind the navigation bar. True backdrop blur on
// Android 12+; a translucent surface fallback below that.
val NavigationBarFrostedBlurKey = booleanPreferencesKey("navigationBarFrostedBlur")

// Tinted variant of frosted blur: the same backdrop blur as [NavigationBarFrostedBlurKey], but the
// bar surface is tinted with the accent (primary) color instead of the neutral surface container.
// Mutually exclusive with [NavigationBarFrostedBlurKey] — turning one on turns the other off.
val NavigationBarTintFrostedBlurKey = booleanPreferencesKey("navigationBarTintFrostedBlur")
val HideNavigationBarLabelsKey = booleanPreferencesKey("hideNavigationBarLabels")

// ── Navigation bar dimension customization ──────────────────────────────────
// Advanced tuning knobs for the FLOATING nav bar style (and corner radius for
// DEFAULT). Defaults preserve the pre-existing look.
val NavigationBarWidthKey = floatPreferencesKey("navigationBarWidth")
const val NAVIGATION_BAR_WIDTH_DEFAULT = 0.8f // fraction of screen width when FLOATING

val NavigationBarHeightKey = floatPreferencesKey("navigationBarHeight")
const val NAVIGATION_BAR_HEIGHT_DEFAULT = 1.0f // multiplier on NavigationBarHeight

val NavigationBarOpacityKey = floatPreferencesKey("navigationBarOpacity")
const val NAVIGATION_BAR_OPACITY_DEFAULT = 1.0f // 1 = opaque, <1 = translucent

val NavigationBarTransparencyKey = floatPreferencesKey("navigationBarTransparency")
const val NAVIGATION_BAR_TRANSPARENCY_DEFAULT = 0.0f // 0 = solid, >0 = see-through (only when frosted blur is off)

val NavigationBarLabelSpacingKey = floatPreferencesKey("navigationBarLabelSpacing")
const val NAVIGATION_BAR_LABEL_SPACING_DEFAULT = 4f // dp between icon and label

val NavigationBarCornerRadiusKey = floatPreferencesKey("navigationBarCornerRadius")
const val NAVIGATION_BAR_CORNER_RADIUS_DEFAULT = 28f // dp

// App-wide scrollbar toggle. When false, all LazyColumn / LazyGrid /
// ScrollState scrollbars in the app are suppressed. (Slider thumb tracks and
// player position bars are not affected — those are not "scrollbars".)
val HideScrollbarKey = booleanPreferencesKey("hideScrollbar")

// Keys for customized background
val PlayerCustomImageUriKey = stringPreferencesKey("playerCustomImageUri")
val PlayerCustomBlurKey = floatPreferencesKey("playerCustomBlur")
val PlayerCustomContrastKey = floatPreferencesKey("playerCustomContrast")
val PlayerCustomBrightnessKey = floatPreferencesKey("playerCustomBrightness")

val LyricsAnimationStyleKey = stringPreferencesKey("lyricsAnimationStyle")

enum class LyricsAnimationStyle {
    NONE,
    FADE,
    GLOW,
    SLIDE,
    KARAOKE,
    APPLE,
}

val LyricsTextSizeKey = floatPreferencesKey("lyricsTextSize")
val LyricsLineSpacingKey = floatPreferencesKey("lyricsLineSpacing")
val LyricsLineBlurKey = booleanPreferencesKey("lyricsLineBlur")
val ShowLyricsPlayerControlsKey = booleanPreferencesKey("showLyricsPlayerControls")
val AutoHideLyricsPlayerControlsKey = booleanPreferencesKey("autoHideLyricsPlayerControls")

val TopSize = stringPreferencesKey("topSize")

const val HISTORY_DURATION_DEFAULT = 30
const val HISTORY_DURATION_MIN = 1
const val HISTORY_DURATION_MAX = 60
val HISTORY_DURATION_RANGE = HISTORY_DURATION_MIN.toFloat()..HISTORY_DURATION_MAX.toFloat()
val HISTORY_DURATION_LEGACY_FLOAT_KEY = floatPreferencesKey("historyDuration")
val HistoryDuration = intPreferencesKey("historyDuration")

val PlayerButtonsStyleKey = stringPreferencesKey("player_buttons_style")
val PlayerBackgroundStyleKey = stringPreferencesKey("playerBackgroundStyle")
val LyricsBackgroundStyleKey = stringPreferencesKey("lyricsBackgroundStyle")
val ShowLyricsKey = booleanPreferencesKey("showLyrics")
val LyricsTextPositionKey = stringPreferencesKey("lyricsTextPosition")
val LyricsClickKey = booleanPreferencesKey("lyricsClick")
val LyricsScrollKey = booleanPreferencesKey("lyricsScrollKey")
val LyricsRomanizeJapaneseKey = booleanPreferencesKey("lyricsRomanizeJapanese")
val LyricsRomanizeKoreanKey = booleanPreferencesKey("lyricsRomanizeKorean")
val LyricsRomanizeChineseKey = booleanPreferencesKey("lyricsRomanizeChinese")
val LyricsRomanizeHindiKey = booleanPreferencesKey("lyricsRomanizeHindi")
val LyricsRomanizeOtherLanguagesKey = booleanPreferencesKey("lyricsRomanizeOtherLanguages")
val TranslateLyricsKey = booleanPreferencesKey("translateLyrics")
val UseLyricsV2Key = booleanPreferencesKey("useLyricsV2")
val LyricsModeKey = stringPreferencesKey("lyricsMode")

enum class LyricsMode {
    V2,
    ENHANCED,
    SPOTIFY,
}

val LyricsV2BounceFactorKey = floatPreferencesKey("lyricsV2BounceFactor")
val LyricsV2GlowFactorKey = floatPreferencesKey("lyricsV2GlowFactor")
val LyricsV2FillTransitionWidthKey = floatPreferencesKey("lyricsV2FillTransitionWidth")
val LyricsV2LrcBounceEnabledKey = booleanPreferencesKey("lyricsV2LrcBounceEnabled")

// Queue lyrics pre-load settings
val PreloadQueueLyricsEnabledKey = booleanPreferencesKey("preload_queue_lyrics_enabled")
val QueueLyricsPreloadCountKey = intPreferencesKey("queue_lyrics_preload_count")

val PlayerVolumeKey = floatPreferencesKey("playerVolume")
val RepeatModeKey = intPreferencesKey("repeatMode")

val SearchSourceKey = stringPreferencesKey("searchSource")
val SwipeThumbnailKey = booleanPreferencesKey("swipeThumbnail")
val SwipeSensitivityKey = floatPreferencesKey("swipeSensitivity")
// Catalog providers are independent from the local-vs-online search scope above. Spotify is a
// metadata/search provider here; it is intentionally not an AudioSourceType because playback is
// resolved by the existing audio-source chain.
val DefaultMetadataSourceKey = stringPreferencesKey("defaultMetadataSource")
val DefaultSearchSourceKey = stringPreferencesKey("defaultSearchSource")

enum class MetadataSource {
    YOUTUBE,
    SPOTIFY,
}

enum class SearchProvider {
    YOUTUBE,
    SPOTIFY,
}

enum class SearchSource {
    LOCAL,
    ONLINE,
    ;

    fun toggle() =
        when (this) {
            LOCAL -> ONLINE
            ONLINE -> LOCAL
        }
}

val VisitorDataKey = stringPreferencesKey("visitorData")
val DataSyncIdKey = stringPreferencesKey("dataSyncId")
val InnerTubeCookieKey = stringPreferencesKey("innerTubeCookie")

/**
 * OAuth2 credentials for the YouTube VR device-code flow, the signed-in path that does not use a
 * cookie. The access token is short-lived (~1h) and is what reaches InnerTube as a Bearer; the
 * refresh token is long-lived and never leaves the app.
 */
val InnerTubeOAuthTokenKey = stringPreferencesKey("innerTubeOAuthToken")
val InnerTubeOAuthRefreshTokenKey = stringPreferencesKey("innerTubeOAuthRefreshToken")

/** Epoch millis at which [InnerTubeOAuthTokenKey] expires, so a refresh happens before a 401. */
val InnerTubeOAuthExpiresAtKey = longPreferencesKey("innerTubeOAuthExpiresAt")
val PoTokenKey = stringPreferencesKey("poToken")
val AccountNameKey = stringPreferencesKey("accountName")
val AccountEmailKey = stringPreferencesKey("accountEmail")
val AccountChannelHandleKey = stringPreferencesKey("accountChannelHandle")

// Avatar that goes with AccountNameKey. Persisted for the same reason the name is: the visible
// account identity has to survive a cold start (including the one the region picker triggers)
// without waiting on, or depending on, a live accountMenu call.
val AccountImageUrlKey = stringPreferencesKey("accountImageUrl")
val SavedAccountsKey = stringPreferencesKey("savedAccounts")
val UseLoginForBrowse = booleanPreferencesKey("useLoginForBrowse")
val SpotifySpDcKey = stringPreferencesKey("spotify_sp_dc")
val SpotifySpKeyKey = stringPreferencesKey("spotify_sp_key")
val SpotifyAccessTokenKey = stringPreferencesKey("spotify_access_token")
val SpotifyAccessTokenExpiresAtKey = longPreferencesKey("spotify_access_token_expires_at")
val SpotifyAccountNameKey = stringPreferencesKey("spotify_account_name")
val SpotifyAccountAvatarUrlKey = stringPreferencesKey("spotify_account_avatar_url")
val ShowSpotifyPlaylistsKey = booleanPreferencesKey("show_spotify_playlists")
val SpotifyLibraryPlaylistsCacheKey = stringPreferencesKey("spotify_library_playlists_cache")

/**
 * Set of item IDs (song/album/artist) that the user has hidden from the
 * "Keep Listening" section on the home page. When the user long-presses an
 * item in Keep Listening and selects "Hide from home," the item's ID is
 * added here and filtered out of the keepListening flow.
 */
val HiddenHomeItemsKey = stringSetPreferencesKey("hidden_home_items")

// Tidal music source integration (ported from MetroFuse)
val TidalCookieKey = stringPreferencesKey("tidalCookie")
val TidalEnabledKey = booleanPreferencesKey("tidalEnabled")
val TidalAudioQualityKey = stringPreferencesKey("tidalAudioQuality")
val TidalArtworkFallbackEnabledKey = booleanPreferencesKey("tidalArtworkFallbackEnabled")
val TidalAnimatedCoversEnabledKey = booleanPreferencesKey("tidalAnimatedCoversEnabled")
val TidalAccountNameKey = stringPreferencesKey("tidal_account_name")

/** Per-user read key for the community Source Pool (created on the site's /dashboard).
 *  When set, overrides the CI-baked BuildConfig.SOURCE_PROVIDER_KEY as the Bearer token. */
val PoolApiKeyKey = stringPreferencesKey("poolApiKey")

// Newline-separated community "paste list" URLs (rentry/gist pages tabulating shared
// ARLs/tokens). Parsed by PasteListPoolSource and merged into the pool account caches
// with id=null so playback reports are never sent for them. Opt-in: empty by default.
val PasteListUrlsKey = stringPreferencesKey("pasteListUrls")

// When ON (default), synced lyrics render in place of the player artwork (BitChord-style
// inline lyrics on the player screen). The lyrics button still opens the full lyrics page.
val ShowLyricsOnPlayerKey = booleanPreferencesKey("showLyricsOnPlayer")

// Newline-separated list of user-configured HiFi/QQDL instance base URLs. Empty = use defaults.
val TidalInstancesKey = stringPreferencesKey("tidalInstances")

// JSON cache of the last instance health scan: [{"url","status","latencyMs","checkedAt"}, ...].
// Persisted so working instances are remembered across launches instead of re-probed every time.
val TidalVerifiedInstancesKey = stringPreferencesKey("tidalVerifiedInstances")

// The last Tidal track id that resolved successfully. Used as a "probe" track for health checks so
// we can tell a fully-working instance apart from a reachable-but-preview-only (unsubscribed) one.
val TidalLastProbeTrackKey = stringPreferencesKey("tidalLastProbeTrack")

// Tidal account login (device/OAuth) + subscription state.
val TidalAccessTokenKey = stringPreferencesKey("tidalAccessToken")
val TidalRefreshTokenKey = stringPreferencesKey("tidalRefreshToken")
val TidalTokenExpiryKey = longPreferencesKey("tidalTokenExpiry")
val TidalSubscriptionKey = stringPreferencesKey("tidalSubscription")

// Which auth flow produced the stored session: "oauth" (device code), "pkce" (web login with a
// durable refresh token), or "webcapture" (live Bearer token grabbed from the web player, no
// refresh token). The refresh path uses this to pick the right OAuth client_id/secret.
val TidalAuthFlowKey = stringPreferencesKey("tidalAuthFlow")

// Real account country + user id captured at login, used by the account playback path instead of a
// hardcoded country. userId is also needed for the subscription-tier lookup.
val TidalCountryCodeKey = stringPreferencesKey("tidalCountryCode")
val TidalUserIdKey = longPreferencesKey("tidalUserId")

// Set when a silent token refresh fails so the UI can surface a "reconnect" prompt. Cleared on any
// successful login/refresh.
val TidalNeedsReloginKey = booleanPreferencesKey("tidalNeedsRelogin")

enum class TidalSubscriptionStatus {
    UNKNOWN,
    PREMIUM,
    FREE,
}

enum class TidalAudioQuality {
    AAC_320,
    FLAC,
    HI_RES_LOSSLESS,
}

/** Apple Music streaming quality for the account path (web ALAC pipeline). */
enum class AppleMusicQuality {
    AAC,
    LOSSLESS,
    HI_RES_LOSSLESS,
}

val AppleMusicQualityKey = stringPreferencesKey("appleMusicQuality")

/**
 * Master enable for Apple Music as a streaming source (Settings → Sources → Apple Music).
 * Default off: playback needs a Media-User-Token with an active Apple Music subscription,
 * and the resolution chain hits Apple's unofficial web-playback endpoint.
 */
val AppleMusicSourceEnabledKey = booleanPreferencesKey("appleMusicSourceEnabled")

val TidalAudioQualityOptions =
    listOf(
        TidalAudioQuality.AAC_320,
        TidalAudioQuality.FLAC,
        TidalAudioQuality.HI_RES_LOSSLESS,
    )

// ---------------------------------------------------------------------------
// Qobuz source (user-provided Qobuz-DL proxy instances, e.g. squid.wtf-style)
// ---------------------------------------------------------------------------
// Streaming/playback only (like Tidal): the app never bundles endpoints — the user pastes their own
// proxy instance URLs. Each instance exposes get-music (search) + download-music (stream URL).
val QobuzEnabledKey = booleanPreferencesKey("qobuzEnabled")

// ---------------------------------------------------------------------------
// Qobuz backup server (mlc.kouzu.in). Separate from Qobuz proper — the
// backup takes a YouTube video id and returns a lossless stream, while
// regular Qobuz uses source pool tokens + community proxy instances. The
// user can toggle / reorder / per-song-pin each independently. Default OFF
// because it's an external community service that should be opt-in.
val QobuzBackupEnabledKey = booleanPreferencesKey("qobuzBackupEnabled")

// CSV of user-provided Qobuz proxy instance base URLs, highest priority first.
val QobuzInstancesKey = stringPreferencesKey("qobuzInstances")

// JSON cache of the last Qobuz instance health scan, mirroring TidalVerifiedInstancesKey.
val QobuzVerifiedInstancesKey = stringPreferencesKey("qobuzVerifiedInstances")

// ---------------------------------------------------------------------------
// Experimental: manual source sign-in
// ---------------------------------------------------------------------------
// When OFF (default) the app relies solely on the community Source Pool: users never see the
// manual Tidal/Qobuz instance & account sign-in fields. Flipping this ON in Experimental Settings
// re-exposes the manual sign-in UI for power users who want to add their own private sources.
val ManualSourceLoginEnabledKey = booleanPreferencesKey("dev_manual_source_login")

// The last Qobuz track id that resolved successfully, used as a health "probe" track so we can tell
// a fully-working instance from a reachable-but-preview-only one.
val QobuzLastProbeTrackKey = stringPreferencesKey("qobuzLastProbeTrack")

// JSON list of direct Qobuz API token entries (user_auth_token + user_id + app_id + app_secret +
// metadata). These call www.qobuz.com/api.json/0.2 directly with an MD5 request signature, so they
// need no proxy instance. Tried before proxy URLs during resolution (direct = highest fidelity).
val QobuzTokensKey = stringPreferencesKey("qobuzTokens")

// JSON health cache for the token list, mirroring QobuzVerifiedInstancesKey.
val QobuzVerifiedTokensKey = stringPreferencesKey("qobuzVerifiedTokens")

// Qobuz quality maps to the proxy/Qobuz format_id: FLAC=6 (CD 16-bit), HI_RES=7 (≤96kHz),
// MAX=27 (>96kHz). MP3 (5) is intentionally omitted — this is a lossless source.
enum class QobuzAudioQuality {
    FLAC,
    HI_RES,
    MAX,
}

val QobuzAudioQualityOptions =
    listOf(
        QobuzAudioQuality.FLAC,
        QobuzAudioQuality.HI_RES,
        QobuzAudioQuality.MAX,
    )

val QobuzAudioQualityKey = stringPreferencesKey("qobuzAudioQuality")

fun QobuzAudioQuality.toFormatId(): Int =
    when (this) {
        QobuzAudioQuality.FLAC -> 6
        QobuzAudioQuality.HI_RES -> 7
        QobuzAudioQuality.MAX -> 27
    }

// ---------------------------------------------------------------------------
// Telegram channel streaming integration
// ---------------------------------------------------------------------------
// Streams audio files (lossless-first) directly from Telegram channels via TDLib. The user logs in
// with their own Telegram account (phone + code + optional 2FA password); the app's api_id/api_hash
// are baked in at build time (BuildConfig.TELEGRAM_API_ID/HASH), so no developer credentials are
// entered in-app. The session lives in TDLib's encrypted database under filesDir; these keys only
// hold display metadata for the settings screen.
val TelegramAccountNameKey = stringPreferencesKey("telegramAccountName")
val TelegramAccountPhoneKey = stringPreferencesKey("telegramAccountPhone")

// When ON (default) the channel browser only materialises lossless files (FLAC/WAV/AIFF/APE/ALAC/…)
// into the channel's playlist; when OFF every audio message and audio-typed document is included.
val TelegramLosslessOnlyKey = booleanPreferencesKey("telegramLosslessOnly")

// ---------------------------------------------------------------------------
// Telegram bots
// ---------------------------------------------------------------------------
// Persisted list of bots the user has added (so they can be re-opened without
// re-pasting their @username each time). Stored as a JSON array; the encoder
// lives in moe.rukamori.archivetune.telegram.TelegramBots.
val TelegramBotsKey = stringPreferencesKey("telegramBots")

// When ON, songs added to a Telegram-backed playlist (LPtg…/channel-id) from a
// bot result are auto-forwarded to that channel so the user's own channel stays
// in sync with what they've collected via bots. Default ON per the feature brief.
val TelegramBotForwardToChannelKey = booleanPreferencesKey("telegramBotForwardToChannel")


// ---------------------------------------------------------------------------
// Multi-source audio framework
// ---------------------------------------------------------------------------
// A configurable set of lossless/stream sources. The user can reorder them (priority for playback
// resolution), toggle each on/off, and pick a primary "search" source. YouTube is always available
// as the final fallback and cannot be removed.

enum class AudioSourceType {
    TIDAL,
    QOBUZ,
    QOBUZ_BACKUP,
    DEEZER,
    APPLE,
    JIOSAAVN,
    YOUTUBE,
}

// CSV of AudioSourceType names, highest priority first. Empty = built-in default order.
val AudioSourceOrderKey = stringPreferencesKey("audioSourceOrder")

// Per-song "play from" overrides, encoded as "songId=SOURCE" entries joined by ';'. The chosen
// source is forced for that specific song (subject to the metadata match gate), overriding the
// global source order. Persisted here so it is included in Settings backups.
val SongSourceOverrideKey = stringPreferencesKey("songSourceOverride")
/**
 * Per-song Qobuz trackId override (CSV of `songId=qobuzTrackId;…`).
 *
 * When the user picks a specific Qobuz track from the "Play from"
 * source-search popup, we persist the Qobuz trackId here so the playback
 * resolver can download the exact track instead of re-searching by
 * title+artist (which could match a different Qobuz track — different
 * master, deluxe edition, etc.).
 *
 * Keyed by the song's existing mediaId (the YouTube video id), so changing
 * the source does NOT change the song's mediaId — the song is not
 * registered as a new entry in the playback history / "recently listened".
 */
val SongSourceQobuzTrackIdKey = stringPreferencesKey("songSourceQobuzTrackId")

/**
 * Per-song Qobuz-**backup** video-id override (CSV of `songId=youtubeVideoId;…`).
 *
 * The backup mirror is keyed by YouTube video id, and until now the resolver
 * simply reused the playing song's own media id. That works for the automatic
 * path but not for an explicit pick in the "Play from" popup: the row the user
 * chose is a *different* catalogue entry, whose id is the only way to address the
 * FLAC they asked for. Storing it here lets `resolveQobuzBackupStream` fetch that
 * exact mirror entry while the song keeps its own media id — so the queue, the
 * artwork, the title and the listening history are all untouched, and only the
 * audio changes.
 */
val SongSourceQobuzBackupVideoIdKey = stringPreferencesKey("songSourceQobuzBackupVideoId")

// The primary audio source the user prefers to search/resolve first (AudioSourceType name).
// Named distinctly from the unrelated SearchSourceKey (LOCAL/ONLINE search scope) above.
val AudioSearchSourceKey = stringPreferencesKey("audioSearchSource")

// When logged in, try the user's own Tidal account (official API) before the public instances.
val TidalAccountFirstKey = booleanPreferencesKey("tidalAccountFirst")

// ---------------------------------------------------------------------------
// Deezer source
// ---------------------------------------------------------------------------
// Unlike Tidal/Qobuz there is no self-hosted proxy tier: Deezer streams come from accounts
// authenticated with an `arl` cookie, supplied by the pool or by a manual sign-in. Defaults OFF
// because the source is inert without accounts.
val DeezerEnabledKey = booleanPreferencesKey("deezerEnabled")

// A manually captured `arl` cookie. Kept separate from the pool cache: the pool is wiped and
// rewritten on every refresh and is gated behind PoolAccountManager.isEnabled, so storing a
// user's own credential there would lose it on the next sync.
val DeezerArlKey = stringPreferencesKey("deezerArl")

// Display label for the manually signed-in account, so the settings row can say who is signed in
// without keeping the ARL itself anywhere near the UI.
val DeezerAccountNameKey = stringPreferencesKey("deezerAccountName")

// Whether the manual account reported a lossless-capable plan. Only orders resolution attempts;
// the provider still verifies the real tier per track.
val DeezerAccountPremiumKey = booleanPreferencesKey("deezerAccountPremium")

// Deezer Geo-Proxy selection to bypass regional catalogue blocking.
val DeezerProxyModeKey = stringPreferencesKey("deezerProxyMode")

enum class DeezerProxyMode(val host: String, val displayName: String) {
    AUTO("auto", "Auto (UK Fallback)"),
    UK1("uk1.proxy.murglar.app", "UK 1"),
    UK2("uk2.proxy.murglar.app", "UK 2"),
    RU1("ru1.proxy.murglar.app", "RU 1"),
    RU2("ru2.proxy.murglar.app", "RU 2"),
    MD("md.proxy.murglar.app", "MD"),
    DIRECT("direct", "No Proxy"),
}

// ---------------------------------------------------------------------------
// JioSaavn source
// ---------------------------------------------------------------------------
// Ported from vivi-music (https://github.com/vivizzz007/vivi-music) under GPL-3.0.
// JioSaavn streams are unauthenticated MP4/AAC; CDN URLs are decrypted locally via DES-ECB.
// Defaults OFF because the source requires JioSaavn's public API to be reachable.
val JioSaavnEnabledKey = booleanPreferencesKey("enableSaavnStreaming")

// Bitrate selection: 96 / 160 / 320 kbps AAC.
val SaavnAudioQualityKey = stringPreferencesKey("saavnAudioQuality")

enum class SaavnAudioQuality {
    QUALITY_320,
    QUALITY_160,
    QUALITY_96,
    ;

    fun toApiValue(): String =
        when (this) {
            QUALITY_320 -> "320kbps"
            QUALITY_160 -> "160kbps"
            QUALITY_96 -> "96kbps"
        }

    fun toLabel(): String =
        when (this) {
            QUALITY_320 -> "320 kbps"
            QUALITY_160 -> "160 kbps"
            QUALITY_96 -> "96 kbps"
        }

    companion object {
        fun fromStoredName(name: String?): SaavnAudioQuality =
            runCatching { valueOf(name?.uppercase() ?: "") }.getOrDefault(QUALITY_320)
    }
}

val WebClientPoTokenEnabledKey = booleanPreferencesKey("webClientPoTokenEnabled")
val PoTokenGvsKey = stringPreferencesKey("poTokenGvs")
val PoTokenPlayerKey = stringPreferencesKey("poTokenPlayer")
val UseVisitorDataKey = booleanPreferencesKey("useVisitorData")
val PoTokenSourceUrlKey = stringPreferencesKey("poTokenSourceUrl")

val LanguageCodeToName =
    mapOf(
        "en" to "English (US)",
        "en-GB" to "English (UK)",
        "en-NG" to "English (Nigeria)",
        "ja" to "日本語",
        "ko" to "한국어",
        "vi" to "Tiếng Việt",
        "zh" to "中文",
        "zh-CN" to "简体中文",
        "zh-TW" to "繁���中文",
        "fr" to "Français",
        "de" to "Deutsch",
        "es" to "Español",
        "pt" to "Português",
        "pt-BR" to "Português (Brasil)",
        "ru" to "Русский",
        "it" to "Italiano",
        "nl" to "Nederlands",
        "pl" to "Polski",
        "tr" to "Türkçe",
        "ar" to "العربية",
        "hi" to "हिन्दी",
        "th" to "ไทย",
        "id" to "Bahasa Indonesia",
        "ms" to "Bahasa Melayu",
        "uk" to "Українська",
        "cs" to "Čeština",
        "el" to "Ελληνικά",
        "he" to "עברית",
        "hu" to "Magyar",
        "ro" to "Română",
        "fi" to "Suomi",
        "da" to "Dansk",
        "no" to "Norsk",
        "sv" to "Svenska",
        "sk" to "Slovenčina",
        "bg" to "Български",
        "hr" to "Hrvatski",
        "sr" to "Срpsки",
        "lt" to "Lietuvių",
        "lv" to "Latviešu",
        "et" to "Eesti",
    )

val CountryCodeToName =
    mapOf(
        "JP" to "Japan",
        "KR" to "South Korea",
        "US" to "United States",
        "GB" to "United Kingdom",
        "CN" to "China",
        "TW" to "Taiwan",
        "HK" to "Hong Kong",
        "FR" to "France",
        "DE" to "Germany",
        "ES" to "Spain",
        "MX" to "Mexico",
        "BR" to "Brazil",
        "RU" to "Russia",
        "IT" to "Italy",
        "NL" to "Netherlands",
        "PL" to "Poland",
        "TR" to "Turkey",
        "AU" to "Australia",
        "CA" to "Canada",
        "IN" to "India",
        "ID" to "Indonesia",
        "TH" to "Thailand",
        "VN" to "Vietnam",
        "NG" to "Nigeria",
        "PH" to "Philippines",
        "MY" to "Malaysia",
        "SG" to "Singapore",
        "AR" to "Argentina",
        "CL" to "Chile",
        "CO" to "Colombia",
        "PE" to "Peru",
        "ZA" to "South Africa",
        "EG" to "Egypt",
        "SA" to "Saudi Arabia",
        "AE" to "United Arab Emirates",
    )

// App rating / star prompt preferences
val LaunchCountKey = intPreferencesKey("launch_count")
val OnboardingCompletedKey = booleanPreferencesKey("onboarding_completed")

// Last onboarding page the user was on. The onboarding ViewModel's page index is plain
// in-memory state; without persisting it, process death or activity recreation resets the
// flow to page 1 even though the user was mid-way through.
val OnboardingCurrentPageKey = intPreferencesKey("onboarding_current_page")
val HasPressedStarKey = booleanPreferencesKey("has_pressed_star")
val RemindAfterKey = intPreferencesKey("remind_after")

// Update settings
val EnableUpdateNotificationKey = booleanPreferencesKey("enableUpdateNotification")
val UpdateChannelKey = stringPreferencesKey("updateChannel")
val LastUpdateCheckKey = longPreferencesKey("lastUpdateCheck")
val YtDlpManualUpdateHistoryKey = stringSetPreferencesKey("ytDlpManualUpdateHistory")
val LastNotifiedVersionKey = stringPreferencesKey("lastNotifiedVersion")

val GitHubContributorsEtagKey = stringPreferencesKey("github_contributors_etag")
val GitHubContributorsJsonKey = stringPreferencesKey("github_contributors_json")
val GitHubContributorsLastCheckedAtKey = longPreferencesKey("github_contributors_last_checked_at")
val GitHubTranslationContributorsJsonKey = stringPreferencesKey("github_translation_contributors_json")
val GitHubTranslationContributorsLastCheckedAtKey = longPreferencesKey("github_translation_contributors_last_checked_at")

val GitHubReleasesEtagKey = stringPreferencesKey("github_releases_etag")
val GitHubReleasesJsonKey = stringPreferencesKey("github_releases_json")
val GitHubReleasesLastCheckedAtKey = longPreferencesKey("github_releases_last_checked_at")
val GitHubReleasesFingerprintKey = stringPreferencesKey("github_releases_fingerprint")

val CanaryReleasesEtagKey = stringPreferencesKey("daily_nightly_releases_etag")
val CanaryReleasesJsonKey = stringPreferencesKey("daily_nightly_releases_json")
val CanaryReleasesLastCheckedAtKey = longPreferencesKey("daily_nightly_releases_last_checked_at")
val CanaryReleasesFingerprintKey = stringPreferencesKey("daily_nightly_releases_fingerprint")

val TogetherPublicServerUrlKey = stringPreferencesKey("together_public_server_url")
val TogetherPublicSessionTokenKey = stringPreferencesKey("together_public_session_token")
val TogetherPublicRoomCodeKey = stringPreferencesKey("together_public_room_code")
val TogetherPublicIsHostKey = booleanPreferencesKey("together_public_is_host")

enum class UpdateChannel {
    STABLE,
    CANARY,
    ;

    companion object {
        fun fromStoredName(
            value: String?,
            defaultValue: UpdateChannel,
        ): UpdateChannel =
            when (value) {
                "NIGHTLY", "DAILY_NIGHTLY" -> CANARY
                else -> entries.firstOrNull { it.name == value } ?: defaultValue
            }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Inline / fullscreen music-video player preferences
// ──────────────────────────────────────────────────────────────────────────────

/**
 * When true, the inline + fullscreen video player renders a slowly drifting
 * blurred copy of the song thumbnail behind the video surface — mimicking
 * YouTube's "ambient mode" so the letterboxed black area glows with the
 * artwork's dominant colors instead of being pure black.
 */
val VideoAmbientModeKey = booleanPreferencesKey("videoAmbientMode")

/**
 * Playback speed applied to BOTH the audio ExoPlayer (in MusicService) and
 * the video ExoPlayer (in VideoArtworkState). Stored as a Float in
 * [0.25f, 2.0f]. 1.0 = normal speed. Surfaced via the fullscreen video
 * overlay's 3-dot overflow menu.
 */
val VideoPlaybackSpeedKey = floatPreferencesKey("videoPlaybackSpeed")

/**
 * Aspect-ratio mode for the inline + fullscreen video surface. Maps to
 * ExoPlayer's AspectRatioFrameLayout resize modes:
 *  - FIT     → RESIZE_MODE_FIT     (letterbox; whole video visible)
 *  - CROP    → RESIZE_MODE_ZOOM    (fill screen, crop edges)
 *  - STRETCH → RESIZE_MODE_FILL    (fill screen, distort if needed)
 *  - FILL    → RESIZE_MODE_FILL    (alias kept for UX parity with YT)
 */
enum class VideoAspectRatio {
    FIT,
    CROP,
    STRETCH,
    FILL,
    ;

    fun toExoResizeMode(): Int = when (this) {
        FIT -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
        CROP -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        STRETCH -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
        FILL -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
    }
}

val VideoAspectRatioKey = stringPreferencesKey("videoAspectRatio")
