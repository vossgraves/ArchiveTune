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
 * True when the Apple Music Experience is on.
 *
 * A read helper rather than the raw preference so the call sites cannot disagree about the key or
 * the default.
 */
@Composable
fun rememberLibraryStyle(): Pair<LibraryStyle, (LibraryStyle) -> Unit> {
    // The experience switch is [AppleMusicExperienceKey]; the style is the library-screen half of
    // it. Both turn the Apple Music surfaces on, so the two are kept in sync on every write: anyone
    // whose data predates the style seeds from the switch below, and the switch stays authoritative
    // when the style is what moved.
    val (legacyEnabled, setLegacyEnabled) =
        rememberPreference(AppleMusicExperienceKey, defaultValue = false)
    val (style, setStyleValue) =
        rememberEnumPreference(
            LibraryStyleKey,
            defaultValue = if (legacyEnabled) LibraryStyle.APPLE_MUSIC else LibraryStyle.DEFAULT,
        )
    val (playerStyle, setPlayerStyle) = rememberEnumPreference(PlayerDesignStyleKey, PlayerDesignStyle.Default)
    val (styleBefore, setStyleBefore) = rememberPreference(StyleBeforeAppleMusicKey, defaultValue = "")

    val setStyle: (LibraryStyle) -> Unit = { newStyle ->
        setStyleValue(newStyle)
        setLegacyEnabled(newStyle == LibraryStyle.APPLE_MUSIC)
        if (newStyle == LibraryStyle.APPLE_MUSIC) {
            // Only record a style we could actually give back. Switching on while already on the
            // Apple Music style would otherwise store APPLE_MUSIC as the thing to restore, and
            // switching off would then "restore" the style the user was trying to leave.
            if (playerStyle != PlayerDesignStyle.APPLE_MUSIC) {
                setStyleBefore(playerStyle.name)
            }
            setPlayerStyle(PlayerDesignStyle.APPLE_MUSIC)
        } else if (playerStyle == PlayerDesignStyle.APPLE_MUSIC) {
            // Only restore while the experience still owns the style. A style picked by hand in
            // the meantime is newer than ours and wins.
            setPlayerStyle(styleBefore.toEnum(PlayerDesignStyle.Default))
        }
    }
    return style to setStyle
}

/** The master Apple Music Experience switch, without the library style folded in. */
@Composable
fun rememberForcedAppleMusicExperience(): Boolean {
    val (enabled) = rememberPreference(AppleMusicExperienceKey, defaultValue = false)
    return enabled
}

/**
 * True when the Apple Music Experience is on.
 *
 * Either entry point counts: the switch, or a library style that was set to Apple Music on its own.
 * Reading only the style is what made the switch appear dead — turning it on moved nothing, because
 * everything downstream asked the style instead.
 */
@Composable
fun rememberAppleMusicExperience(): Boolean =
    rememberForcedAppleMusicExperience() || rememberLibraryStyle().first == LibraryStyle.APPLE_MUSIC

/**
 * Sets the Apple Music Experience, and moves the player design style with it.
 *
 * The experience owns the player style while it is on, so every way of setting it has to carry the
 * same coupling. Writing [AppleMusicExperienceKey] on its own — which the settings-search switch
 * did — left the switch reading "on" with the player still on the old style, and no record of the
 * style to give back.
 */
@Composable
fun rememberAppleMusicExperienceToggle(): (Boolean) -> Unit {
    val (_, setStyle) = rememberLibraryStyle()
    return { enabled -> setStyle(if (enabled) LibraryStyle.APPLE_MUSIC else LibraryStyle.DEFAULT) }
}
