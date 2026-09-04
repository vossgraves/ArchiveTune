/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.LibrarySource
import moe.rukamori.archivetune.constants.LibrarySourceKey
import moe.rukamori.archivetune.constants.ShowSpotifyPlaylistsKey
import moe.rukamori.archivetune.constants.SpotifySpDcKey
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference

/**
 * True when the Library has a second service to offer: the user is signed in to Spotify AND has
 * turned Spotify content on in Integration settings.
 *
 * Both halves matter. A signed-in user who has not opted in keeps the Library exactly as it was,
 * and the same preference already gates what Android Auto browses, so the two agree.
 */
@Composable
fun rememberLibrarySourceAvailable(): Boolean {
    val spDc by rememberPreference(SpotifySpDcKey, defaultValue = "")
    val showSpotify by rememberPreference(ShowSpotifyPlaylistsKey, defaultValue = false)
    return spDc.isNotBlank() && showSpotify
}

/**
 * The Library's current source, already resolved against whether Spotify is usable — so callers
 * never repeat the check, and a stored SPOTIFY choice survives a temporary sign-out rather than
 * being rewritten behind the user's back.
 */
@Composable
fun rememberLibrarySource(): LibrarySource {
    val stored by rememberEnumPreference(LibrarySourceKey, defaultValue = LibrarySource.YTM)
    return if (stored == LibrarySource.SPOTIFY && !rememberLibrarySourceAvailable()) {
        LibrarySource.YTM
    } else {
        stored
    }
}

/**
 * The YTM / Spotify pill pair, sat above a Library section's own content.
 *
 * Uses [ExpressiveTabChip], the same chip the Library's section tabs are built from, so the two
 * rows read as one control surface instead of two different ideas about what a pill is. Renders
 * nothing when there is no second service — see [rememberLibrarySourceAvailable].
 */
@Composable
fun LibrarySourcePills(
    modifier: Modifier = Modifier,
    // 24dp matches every other Library section's gutter. A caller already inside a padded
    // container — the Artists grid insets its own contents — passes 0 so the pills line up with
    // the sections that are not.
    horizontalPadding: Dp = 24.dp,
) {
    if (!rememberLibrarySourceAvailable()) return

    var source by rememberEnumPreference(LibrarySourceKey, defaultValue = LibrarySource.YTM)

    Row(
        modifier = modifier.fillMaxWidth().padding(PaddingValues(horizontal = horizontalPadding, vertical = 4.dp)),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExpressiveTabChip(
            label = stringResource(R.string.library_source_ytm),
            iconRes = R.drawable.ic_music,
            selected = source == LibrarySource.YTM,
            onClick = { source = LibrarySource.YTM },
        )
        ExpressiveTabChip(
            label = stringResource(R.string.home_source_spotify),
            iconRes = R.drawable.spotify_icon,
            selected = source == LibrarySource.SPOTIFY,
            onClick = { source = LibrarySource.SPOTIFY },
        )
    }
}
