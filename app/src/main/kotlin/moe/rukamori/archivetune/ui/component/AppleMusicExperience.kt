/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.runtime.Composable
import moe.rukamori.archivetune.constants.AppleMusicExperienceKey
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
fun rememberAppleMusicExperience(): Boolean {
    val (enabled) = rememberPreference(AppleMusicExperienceKey, defaultValue = false)
    return enabled
}

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
    val (_, setEnabled) = rememberPreference(AppleMusicExperienceKey, defaultValue = false)
    val (style, setStyle) = rememberEnumPreference(PlayerDesignStyleKey, PlayerDesignStyle.V4)
    val (styleBefore, setStyleBefore) = rememberPreference(StyleBeforeAppleMusicKey, defaultValue = "")

    return { enabled ->
        setEnabled(enabled)
        if (enabled) {
            // Only record a style we could actually give back. Switching on while already on the
            // Apple Music style would otherwise store APPLE_MUSIC as the thing to restore, and
            // switching off would then "restore" the style the user was trying to leave.
            if (style != PlayerDesignStyle.APPLE_MUSIC) {
                setStyleBefore(style.name)
            }
            setStyle(PlayerDesignStyle.APPLE_MUSIC)
        } else if (style == PlayerDesignStyle.APPLE_MUSIC) {
            // Only restore while the experience still owns the style. A style picked by hand in the
            // meantime is newer than ours and wins.
            setStyle(styleBefore.toEnum(PlayerDesignStyle.V4))
        }
    }
}
