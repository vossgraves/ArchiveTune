/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.HomeSource
import moe.rukamori.archivetune.constants.HomeSourceKey
import moe.rukamori.archivetune.constants.SpotifySpDcKey
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference

/**
 * True when the Home tab has two pages to offer. The toggle and the Spotify page both hang off
 * this: with no Spotify session there is only the YouTube home, and a toggle with one destination
 * is just clutter.
 */
@Composable
fun rememberHomeSourceAvailable(): Boolean {
    val spDc by rememberPreference(SpotifySpDcKey, defaultValue = "")
    return spDc.isNotBlank()
}

/**
 * The Home tab's current page, already resolved against whether Spotify is actually usable — so
 * callers never have to repeat the signed-out check, and a stored SPOTIFY choice survives a
 * temporary sign-out instead of being rewritten to YOUTUBE behind the user's back.
 */
@Composable
fun rememberHomeSource(): HomeSource {
    val stored by rememberEnumPreference(HomeSourceKey, defaultValue = HomeSource.YOUTUBE)
    return if (stored == HomeSource.SPOTIFY && !rememberHomeSourceAvailable()) HomeSource.YOUTUBE else stored
}

/** Matches the account avatar this sits beside, so the two read as one pair of controls. */
private val ToggleIconSize = 20.dp

/**
 * The Home tab's source toggle, sized for the top app bar and meant to sit immediately left of the
 * account avatar.
 *
 * It shows the logo of the page it will take you TO, not the one you are on. The icon is a
 * destination the way a "switch account" chip is: labelling it with the current source would make
 * a button that looks like a status readout, and the user already knows which page they are
 * looking at.
 *
 * Renders nothing without a Spotify session — [rememberHomeSourceAvailable] — so signed-out users
 * get the plain avatar rather than a control with one destination.
 */
@Composable
fun HomeSourceToggleButton(modifier: Modifier = Modifier) {
    if (!rememberHomeSourceAvailable()) return

    var source by rememberEnumPreference(HomeSourceKey, defaultValue = HomeSource.YOUTUBE)
    val target = if (source == HomeSource.SPOTIFY) HomeSource.YOUTUBE else HomeSource.SPOTIFY

    IconButton(
        onClick = { source = target },
        modifier = modifier,
    ) {
        Icon(
            painter = painterResource(target.iconResId()),
            contentDescription =
                stringResource(R.string.home_source_switch_to, stringResource(target.labelResId())),
            // Sized explicitly: spotify_icon is a 1438x1425 PNG, and an Icon with no size
            // constraint draws its painter at intrinsic size — roughly 520dp.
            modifier = Modifier.size(ToggleIconSize),
        )
    }
}

private fun HomeSource.labelResId(): Int =
    when (this) {
        HomeSource.YOUTUBE -> R.string.home_source_youtube
        HomeSource.SPOTIFY -> R.string.home_source_spotify
    }

private fun HomeSource.iconResId(): Int =
    when (this) {
        HomeSource.YOUTUBE -> R.drawable.ic_music
        HomeSource.SPOTIFY -> R.drawable.spotify_icon
    }

/**
 * Sends the Home tab back to the YouTube page. The Spotify page offers this when its session turns
 * out to be dead: the stored sp_dc is non-blank (or the page would not be showing) but Spotify
 * rejects it, and without a way out the user is stuck on an error with a button that does nothing.
 */
@Composable
fun rememberSwitchToYouTube(): () -> Unit {
    var source by rememberEnumPreference(HomeSourceKey, defaultValue = HomeSource.YOUTUBE)
    return { source = HomeSource.YOUTUBE }
}
