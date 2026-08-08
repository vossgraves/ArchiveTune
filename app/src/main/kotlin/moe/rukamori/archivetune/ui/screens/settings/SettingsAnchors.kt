/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.foundation.layout.WindowInsets
import android.os.SystemClock
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Deep links from settings search to an individual preference on a sub-screen.
 *
 * The anchor is handed over through this object rather than a navigation argument on purpose.
 * Adding an optional query arg would mean changing the route strings of screens that a lot of
 * existing `navigate("settings/player")` calls already point at, and a mismatch there fails at
 * runtime rather than at compile time. A highlight is also purely transient: it should not
 * survive process death and be replayed when the user returns to the screen days later, which
 * is exactly what a saved nav argument would do.
 *
 * Main thread only, which is where navigation and composition both run.
 */
object SettingsAnchorRequest {
    /**
     * How long a request stays claimable. Some settings screens bail out of composition on their
     * first pass (StorageSettings returns early until the player service and image cache are
     * available) and compose again a moment later. Clearing on the first read would throw the
     * request away during that discarded pass, so a claim is instead remembered for a short
     * window and re-served to the same screen until it sticks. Long enough to survive a service
     * connection, far too short to replay when the user opens the screen again later.
     */
    private const val CLAIM_WINDOW_MS = 1_500L

    private var pendingScreen: String? = null
    private var pendingAnchor: String? = null

    /**
     * When the pending request was first served, or null while it is still unclaimed.
     *
     * Deliberately nullable rather than using 0 as "unclaimed": uptimeMillis legitimately returns
     * small values just after boot, and a 0 reading would make the claim never register and the
     * request never expire.
     */
    private var claimedAtMs: Long? = null

    /**
     * Monotonic time source. Overridable only so the claim window is testable on a plain JVM,
     * where android.os.SystemClock is not implemented and throws. Production never reassigns it.
     */
    internal var elapsedMs: () -> Long = { SystemClock.uptimeMillis() }

    /** Drops any pending request. Test-only; production requests are cleared by [consume]. */
    internal fun reset() {
        pendingScreen = null
        pendingAnchor = null
        claimedAtMs = null
    }

    /** Ask [screen] to scroll to and highlight [anchor] the next time it composes. */
    fun request(screen: String, anchor: String) {
        pendingScreen = screen
        pendingAnchor = anchor
        claimedAtMs = null
    }

    /**
     * Returns the anchor requested for [screen].
     *
     * Returns null when the pending request was aimed at a different screen, so a stale request
     * can never highlight a row on a screen it was not meant for.
     */
    fun consume(screen: String): String? {
        if (pendingScreen != screen) return null
        val now = elapsedMs()
        val claimedAt = claimedAtMs
        if (claimedAt == null) {
            claimedAtMs = now
            return pendingAnchor
        }
        if (now - claimedAt > CLAIM_WINDOW_MS) {
            reset()
            return null
        }
        return pendingAnchor
    }
}

/**
 * Route strings of the settings screens that support deep anchors.
 *
 * Only screens whose content is a `Column` with a single `verticalScroll` can appear here: the
 * anchor maths resolves a row's offset against one [ScrollState]. Screens built on a LazyColumn
 * (account, Discord) never measure off-screen rows at all, so they would need a different
 * mechanism entirely rather than an entry in this list.
 */
object SettingsAnchorScreens {
    const val PLAYER = "settings/player"
    const val APPEARANCE = "settings/appearance"
    const val STORAGE = "settings/storage"
    const val CONTENT = "settings/content"
    const val PRIVACY = "settings/privacy"
    const val INTERNET = "settings/internet"
    const val LYRICS = "settings/lyrics"
    const val BACKUP = "settings/backup_restore"
    const val INTEGRATION = "settings/integration"
}

/** Stable ids for individually searchable preferences. Referenced by the deep search index. */
object SettingsAnchors {
    // Player
    const val CROSSFADE = "crossfade"
    const val GAPLESS = "gapless"
    const val SKIP_SILENCE = "skip_silence"
    const val AUDIO_NORMALIZATION = "audio_normalization"
    const val PERSISTENT_QUEUE = "persistent_queue"
    const val EXTERNAL_DOWNLOADER = "external_downloader"

    // Appearance
    const val DYNAMIC_THEME = "dynamic_theme"
    const val DARK_THEME = "dark_theme"
    const val PURE_BLACK = "pure_black"
    const val APP_ICON = "app_icon"
    const val FONT = "font"
    const val HIGH_REFRESH_RATE = "high_refresh_rate"

    // Storage
    const val EXPORT_DOWNLOADS = "export_downloads"
    const val EXPORT_DOWNLOADS_PICK = "export_downloads_pick"
    const val CLEAR_DOWNLOADS = "clear_downloads"
    const val SONG_CACHE_SIZE = "song_cache_size"
    const val CLEAR_SONG_CACHE = "clear_song_cache"
    const val IMAGE_CACHE_SIZE = "image_cache_size"
    const val SMART_TRIMMER = "smart_trimmer"

    // Content
    const val HIDE_EXPLICIT = "hide_explicit"
    const val HIDE_VIDEO = "hide_video"
    const val ALLOW_AGE_RESTRICTED = "allow_age_restricted"
    const val APP_LANGUAGE = "app_language"

    // Privacy
    const val PAUSE_LISTEN_HISTORY = "pause_listen_history"
    const val PAUSE_SEARCH_HISTORY = "pause_search_history"
    const val HAPTICS = "haptics"
    const val DISABLE_SCREENSHOT = "disable_screenshot"

    // Internet
    const val DNS_OVER_HTTPS = "dns_over_https"
    const val PROXY = "proxy"

    // Lyrics
    const val LYRICS_MODE = "lyrics_mode"
    const val LYRICS_ANIMATION = "lyrics_animation"
    const val LYRICS_AUTO_SCROLL = "lyrics_auto_scroll"
    const val LYRICS_LINE_BLUR = "lyrics_line_blur"

    // Backup and restore
    const val BACKUP = "backup"
    const val RESTORE = "restore"

    // Integration
    const val CROSS_SERVICE_IMPORT = "cross_service_import"
}

private val HighlightCornerRadius = 18.dp
private val HighlightInset = 1.dp
private const val HighlightFadeInMs = 220
private const val HighlightHoldMs = 1100L
private const val HighlightFadeOutMs = 450

/**
 * Tracks the anchor a settings screen was asked to reveal, scrolls to it, and drives the
 * highlight fade.
 *
 * Positions are measured in root coordinates rather than parent coordinates because
 * preferences are nested two levels deep (scrolling Column > PreferenceGroup's Column >
 * inner Column), so a parent-relative offset would be measured against the wrong container.
 */
@Stable
class SettingsAnchorState internal constructor(
    internal val target: String?,
    highlightColor: Color,
    val scrollState: ScrollState,
) {
    /**
     * Kept updated from composition rather than captured once. Appearance settings is one of the
     * screens with anchors, so the theme can change while a highlight is on screen.
     */
    internal var highlightColor: Color by mutableStateOf(highlightColor)
    private var containerTop: Float? by mutableStateOf(null)
    private var anchorTop: Float? by mutableStateOf(null)

    /** Fade progress, read at draw time so a change repaints without recomposing. */
    private var highlightAlpha: Float by mutableStateOf(0f)

    /**
     * Scroll offset of the target row inside the scrolling content, or null until both the
     * container and the row have been measured. Both are latched on first measurement, so this
     * settles on one value instead of retriggering the scroll on every layout pass.
     */
    internal val scrollTarget: Int?
        get() {
            if (target == null) return null
            val container = containerTop ?: return null
            val anchor = anchorTop ?: return null
            return (scrollState.value + (anchor - container)).roundToInt().coerceAtLeast(0)
        }

    /** Apply to the scrolling container so row offsets can be resolved against it. */
    val containerModifier: Modifier
        get() =
            Modifier.onGloballyPositioned { coordinates ->
                if (target != null && containerTop == null) {
                    containerTop = coordinates.positionInRoot().y
                }
            }

    internal suspend fun runHighlight() {
        animate(0f, 1f, animationSpec = tween(HighlightFadeInMs)) { value, _ -> highlightAlpha = value }
        delay(HighlightHoldMs)
        animate(1f, 0f, animationSpec = tween(HighlightFadeOutMs)) { value, _ -> highlightAlpha = value }
    }

    /**
     * Marks a preference as the row identified by [key].
     *
     * The tint is drawn *over* the row, not behind it: preference rows are Cards with an opaque
     * container colour, so anything drawn behind them is painted over and invisible.
     */
    fun anchor(key: String): Modifier =
        Modifier
            .onGloballyPositioned { coordinates ->
                if (key == target && anchorTop == null) {
                    anchorTop = coordinates.positionInRoot().y
                }
            }.drawWithContent {
                drawContent()
                val progress = if (key == target) highlightAlpha else 0f
                if (progress <= 0f) return@drawWithContent
                val inset = HighlightInset.toPx()
                drawRoundRect(
                    color = highlightColor.copy(alpha = highlightColor.alpha * progress),
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2, size.height - inset * 2),
                    cornerRadius = CornerRadius(HighlightCornerRadius.toPx()),
                )
            }
}

/**
 * Creates the anchor state for [screen] and consumes any pending request aimed at it.
 *
 * Returns a state whose [SettingsAnchorState.scrollState] must be used for the screen's
 * `verticalScroll`, so the scroll position and the measured offsets agree.
 */
@Composable
fun rememberSettingsAnchorState(screen: String): SettingsAnchorState {
    val scrollState = rememberScrollState()
    val highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    val state =
        remember(screen) {
            SettingsAnchorState(
                target = SettingsAnchorRequest.consume(screen),
                highlightColor = highlightColor,
                scrollState = scrollState,
            )
        }
    state.highlightColor = highlightColor

    val scrollTarget = state.scrollTarget
    LaunchedEffect(state, scrollTarget) {
        if (scrollTarget == null) return@LaunchedEffect
        state.scrollState.animateScrollTo(scrollTarget)
        state.runHighlight()
    }
    return state
}
