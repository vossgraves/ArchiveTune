/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.HomeSource
import moe.rukamori.archivetune.constants.HomeSourceKey
import moe.rukamori.archivetune.constants.SpotifySpDcKey
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference

/**
 * True when the Home tab has two pages to offer. The switcher and the Spotify page both hang off
 * this: with no Spotify session there is only the YouTube home, and a switcher with one option is
 * just clutter.
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

/** Matches the 18dp Material 3 uses inside a segmented button's label. */
private val IconSize = 18.dp

/**
 * Switches the Home tab between the YouTube and Spotify pages.
 *
 * Rendered as the first row inside each home's own scrolling content rather than as a bar above
 * both: each screen already owns its Scaffold, insets and scroll behaviour, and hoisting the
 * switcher above them would mean restructuring both to hand that back.
 */
@Composable
fun HomeSourceSwitcher(modifier: Modifier = Modifier) {
    if (!rememberHomeSourceAvailable()) return

    var source by rememberEnumPreference(HomeSourceKey, defaultValue = HomeSource.YOUTUBE)
    val options = HomeSource.entries

    SingleChoiceSegmentedButtonRow(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .padding(vertical = 8.dp),
    ) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = source == option,
                onClick = { source = option },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                icon = {},
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(option.iconResId()),
                            contentDescription = null,
                            // Sized explicitly: spotify_icon is a 1438x1425 PNG, and an Icon with
                            // no size constraint draws its painter at intrinsic size — roughly
                            // 520dp, which swallowed the row and squeezed the label into a
                            // one-character-wide column.
                            modifier = Modifier.size(IconSize),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(option.labelResId()),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
            )
        }
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
