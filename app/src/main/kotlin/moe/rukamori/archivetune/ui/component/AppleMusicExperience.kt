/*
 * ArchiveTune (2026)
 * © vossgraves — github.com/vossgraves
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.runtime.Composable
import moe.rukamori.archivetune.constants.AppleMusicExperienceKey
import moe.rukamori.archivetune.constants.LibraryStyle
import moe.rukamori.archivetune.constants.LibraryStyleKey
import moe.rukamori.archivetune.constants.PlayerDesignStyle
import moe.rukamori.archivetune.constants.PlayerDesignStyleKey
import moe.rukamori.archivetune.constants.StyleBeforeAppleMusicKey
import moe.rukamori.archivetune.extensions.toEnum
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference

/**
 * The Library tab's layout style and its setter.
 *
 * A read helper rather than the raw preference so the call sites cannot disagree about the key, the
 * default or the seeding rule.
 */
@Composable
fun rememberLibraryStyle(): Pair<LibraryStyle, (LibraryStyle) -> Unit> {
    // The two keys are independent: the library style says how the Library tab lays itself out, and
    // nothing else. It used to also write the experience switch and force the player style, which
    // meant picking "Apple Music" for the library silently restyled the player, the tab bar and the
    // headers as well — and left the switch on afterwards, so every later visit to the library row
    // re-forced the lot. The switch is the one control that turns the whole presentation on.
    //
    // The switch still seeds the default for people whose data predates the style key, but it is a
    // read: choosing a style never writes it back.
    val (legacyEnabled) = rememberPreference(AppleMusicExperienceKey, defaultValue = false)
    val (style, setStyle) =
        rememberEnumPreference(
            LibraryStyleKey,
            defaultValue = if (legacyEnabled) LibraryStyle.APPLE_MUSIC else LibraryStyle.DEFAULT,
        )
    return style to setStyle
}

/** The master Apple Music Experience switch, without the library style folded in. */
@Composable
fun rememberForcedAppleMusicExperience(): Boolean {
    val (enabled) = rememberPreference(AppleMusicExperienceKey, defaultValue = false)
    return enabled
}

/**
 * True when the Apple Music presentation is on: the player design, the tab bar, the page headers and
 * the menus all ask this.
 *
 * It is the switch alone. It used to be true whenever the library style was Apple Music as well,
 * which is what made the library row force the rest of the app; a library style is a layout choice
 * for one tab, not a request to restyle everything.
 */
@Composable
fun rememberAppleMusicExperience(): Boolean = rememberForcedAppleMusicExperience()

/**
 * Sets the Apple Music Experience: the switch, the library style and the player design style move
 * together, because the switch is the control that promises the whole presentation.
 *
 * The experience owns the player style while it is on, and it records what it displaced so turning
 * it off can give that back. A style picked by hand in the meantime is newer than ours and wins.
 */
@Composable
fun rememberAppleMusicExperienceToggle(): (Boolean) -> Unit {
    val (_, setForced) = rememberPreference(AppleMusicExperienceKey, defaultValue = false)
    val (_, setStyle) = rememberLibraryStyle()
    val (playerStyle, setPlayerStyle) = rememberEnumPreference(PlayerDesignStyleKey, PlayerDesignStyle.Default)
    val (styleBefore, setStyleBefore) = rememberPreference(StyleBeforeAppleMusicKey, defaultValue = "")

    return { enabled ->
        setForced(enabled)
        setStyle(if (enabled) LibraryStyle.APPLE_MUSIC else LibraryStyle.DEFAULT)
        if (enabled) {
            // Only record a style we could actually give back. Switching on while already on the
            // Apple Music style would otherwise store APPLE_MUSIC as the thing to restore.
            if (playerStyle != PlayerDesignStyle.APPLE_MUSIC) {
                setStyleBefore(playerStyle.name)
            }
            setPlayerStyle(PlayerDesignStyle.APPLE_MUSIC)
        } else if (playerStyle == PlayerDesignStyle.APPLE_MUSIC) {
            setPlayerStyle(styleBefore.toEnum(PlayerDesignStyle.Default))
        }
    }
}
