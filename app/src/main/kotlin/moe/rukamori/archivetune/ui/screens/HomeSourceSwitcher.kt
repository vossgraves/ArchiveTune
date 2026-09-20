/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * The Home tab's pages and the controls that pick between them. Which pages exist is
 * [ActiveHomeSourcesKey] (chosen in Appearance); which one is showing is [HomeSourceKey]. A page
 * whose prerequisite disappears (a Spotify session going away) is filtered out of the resolved set
 * rather than erased from the setting, so signing back in restores the user's choice.
 */

package moe.rukamori.archivetune.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.ActiveHomeSourcesKey
import moe.rukamori.archivetune.constants.HomeSource
import moe.rukamori.archivetune.constants.HomeSourceKey
import moe.rukamori.archivetune.constants.SpotifySpDcKey
import moe.rukamori.archivetune.ui.component.ProfileMenuDialog
import moe.rukamori.archivetune.ui.component.ProfileMenuItem
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference

/**
 * True when the Spotify page is usable at all. Everything Spotify-page-shaped hangs off this: with
 * no session there is no Spotify home to offer, and an entry with one destination is just clutter.
 */
@Composable
fun rememberHomeSourceAvailable(): Boolean {
    val spDc by rememberPreference(SpotifySpDcKey, defaultValue = "")
    return spDc.isNotBlank()
}

/** Whether [this] page can be shown right now. YouTube is the app's own home and always can. */
private fun HomeSource.isAvailable(spotifyAvailable: Boolean): Boolean =
    when (this) {
        HomeSource.YOUTUBE -> true
        HomeSource.SPOTIFY -> spotifyAvailable
    }

/** Every page the app could offer, in the order the switcher lists them. */
val HomeSourceCandidates: List<HomeSource> = listOf(HomeSource.YOUTUBE, HomeSource.SPOTIFY)

/** Reads [ActiveHomeSourcesKey]; unknown or duplicate names are dropped, order is preserved. */
internal fun parseHomeSources(raw: String): List<HomeSource> =
    raw
        .split(',')
        .mapNotNull { name -> HomeSource.entries.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) } }
        .distinct()

/**
 * The Home pages the user has made available, resolved against availability and never empty.
 *
 * An unset [ActiveHomeSourcesKey] means "not configured" and keeps the historical behaviour:
 * YouTube alone, plus Spotify the moment a session exists. YouTube is always in the result — it is
 * the only page that needs nothing.
 */
@Composable
fun rememberActiveHomeSources(): List<HomeSource> {
    val (raw) = rememberPreference(ActiveHomeSourcesKey, "")
    val spotifyAvailable = rememberHomeSourceAvailable()
    val stored = remember(raw) { parseHomeSources(raw) }
    val base =
        if (stored.isEmpty()) {
            buildList {
                add(HomeSource.YOUTUBE)
                if (spotifyAvailable) add(HomeSource.SPOTIFY)
            }
        } else {
            stored
        }
    return base.filter { it.isAvailable(spotifyAvailable) }.ifEmpty { listOf(HomeSource.YOUTUBE) }
}

/**
 * The Home tab's current page, already resolved against the active set — so callers never have to
 * repeat the availability checks, and a stored choice survives a temporary sign-out instead of
 * being rewritten behind the user's back.
 */
@Composable
fun rememberHomeSource(): HomeSource {
    val stored by rememberEnumPreference(HomeSourceKey, defaultValue = HomeSource.YOUTUBE)
    val actives = rememberActiveHomeSources()
    return if (stored in actives) stored else actives.first()
}

/** Matches the account avatar this sits beside, so the two read as one pair of controls. */
private val ToggleIconSize = 20.dp

/**
 * The Home tab's source control, sized for the top app bar and meant to sit immediately left of the
 * account avatar.
 *
 * Two active pages keep the plain toggle (the icon shows where a tap lands). Three or more open the
 * switcher dialog instead, styled like the account menu, with the current page marked — a toggle
 * cannot express a three-way choice, and cycling through pages on every tap is worse than a menu.
 */
@Composable
fun HomeSourceToggleButton(modifier: Modifier = Modifier) {
    val actives = rememberActiveHomeSources()
    if (actives.size < 2) return

    var source by rememberEnumPreference(HomeSourceKey, defaultValue = HomeSource.YOUTUBE)
    var switcherOpen by remember { mutableStateOf(false) }

    if (switcherOpen) {
        HomeSourceSwitcherDialog(
            active = actives,
            current = source,
            onSelect = {
                source = it
                switcherOpen = false
            },
            onDismiss = { switcherOpen = false },
        )
    }

    val showsMenu = actives.size > 2
    val target = if (showsMenu) source else actives.first { it != source }

    IconButton(
        onClick = { if (showsMenu) switcherOpen = true else source = target },
        modifier = modifier,
    ) {
        Icon(
            painter = painterResource(target.iconResId()),
            contentDescription =
                if (showsMenu) {
                    stringResource(R.string.home_screens)
                } else {
                    stringResource(R.string.home_source_switch_to, stringResource(target.labelResId()))
                },
            // Sized explicitly: spotify_icon is a 1438x1425 PNG, and an Icon with no size
            // constraint draws its painter at intrinsic size — roughly 520dp.
            modifier = Modifier.size(ToggleIconSize),
        )
    }
}

/**
 * The page switcher, drawn in the account menu's shell: one row per active page, the current one
 * badged. Selecting a row switches the Home tab and closes the dialog.
 */
@Composable
fun HomeSourceSwitcherDialog(
    active: List<HomeSource>,
    current: HomeSource,
    onSelect: (HomeSource) -> Unit,
    onDismiss: () -> Unit,
) {
    ProfileMenuDialog(
        accountName = "",
        accountImageUrl = null,
        headerTitle = stringResource(R.string.home_screens),
        onDismiss = onDismiss,
        items =
            active.map { source ->
                ProfileMenuItem(
                    icon = source.iconResId(),
                    label = stringResource(source.labelResId()),
                    showBadge = source == current,
                    onClick = { onSelect(source) },
                )
            },
    )
}

/**
 * The Appearance screen's picker for [ActiveHomeSourcesKey]: every page the app can offer, with the
 * unusable ones disabled rather than hidden so the screen explains why they are not on the list.
 */
@Composable
fun HomeScreensDialog(
    selected: List<HomeSource>,
    spotifyAvailable: Boolean,
    onConfirm: (List<HomeSource>) -> Unit,
    onDismiss: () -> Unit,
) {
    var working by remember { mutableStateOf(selected) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_screens)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                HomeSourceCandidates.forEach { source ->
                    val available = source.isAvailable(spotifyAvailable)
                    val checked = source in working
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = available) {
                                    working =
                                        if (checked) {
                                            // YouTube is the floor: it can be the only page but
                                            // never absent, since it needs no session.
                                            if (source == HomeSource.YOUTUBE) {
                                                working
                                            } else {
                                                working - source
                                            }
                                        } else {
                                            (working + source).distinct()
                                        }
                                }.padding(vertical = 4.dp),
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = null,
                            enabled = available,
                        )
                        Icon(
                            painter = painterResource(source.iconResId()),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = stringResource(source.labelResId()),
                            style = MaterialTheme.typography.bodyLarge,
                            color =
                                if (available) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(working.ifEmpty { listOf(HomeSource.YOUTUBE) }) }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

/**
 * Sends the Home tab back to the YouTube page. A page offers this when its session turns out to be
 * dead: the stored credential is non-blank (or the page would not be showing) but the service
 * rejects it, and without a way out the user is stuck on an error with a button that does nothing.
 */
@Composable
fun rememberSwitchToYouTube(): () -> Unit {
    var source by rememberEnumPreference(HomeSourceKey, defaultValue = HomeSource.YOUTUBE)
    return { source = HomeSource.YOUTUBE }
}

internal fun HomeSource.labelResId(): Int =
    when (this) {
        HomeSource.YOUTUBE -> R.string.home_source_youtube
        HomeSource.SPOTIFY -> R.string.home_source_spotify
    }

internal fun HomeSource.iconResId(): Int =
    when (this) {
        HomeSource.YOUTUBE -> R.drawable.ic_music
        HomeSource.SPOTIFY -> R.drawable.spotify_icon
    }
