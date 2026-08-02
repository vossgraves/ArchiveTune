/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R

/**
 * The Song | Video segmented pill shown at the top of every player style when
 * [AllowVideoSwitchKey] is enabled. Mirrors the YouTube Music Song/Video
 * toggle: tapping "Song" shows the album artwork + audio controls, tapping
 * "Video" replaces the artwork with an inline YouTube IFrame player.
 *
 * The two halves of the pill are visually distinct: the active half is filled
 * with the theme's primary color, the inactive half is translucent.
 *
 * @param isVideoMode true when "Video" is currently selected.
 * @param isResolving true when the videoId is being looked up (for local /
 *   Telegram songs that need a YouTube search). Shows a spinner instead of
 *   the "Video" label so the user knows something is happening.
 * @param onSongSelected invoked when the user taps "Song".
 * @param onVideoSelected invoked when the user taps "Video".
 */
@Composable
fun SongVideoTogglePill(
    isVideoMode: Boolean,
    isResolving: Boolean = false,
    onSongSelected: () -> Unit,
    onVideoSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val songLabel = stringResource(R.string.action_song)
    val videoLabel = stringResource(R.string.action_video)
    val pillShape = RoundedCornerShape(50)
    val activeColor = MaterialTheme.colorScheme.primary
    val onActiveColor = MaterialTheme.colorScheme.onPrimary
    val inactiveColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    val onInactiveColor = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier =
            modifier
                .clip(pillShape)
                .background(inactiveColor)
                .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PillHalf(
            label = songLabel,
            isActive = !isVideoMode,
            activeColor = activeColor,
            onActiveColor = onActiveColor,
            onInactiveColor = onInactiveColor,
            onClick = onSongSelected,
        )
        PillHalf(
            label = videoLabel,
            isActive = isVideoMode,
            activeColor = activeColor,
            onActiveColor = onActiveColor,
            onInactiveColor = onInactiveColor,
            leading = {
                if (isResolving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = if (isVideoMode) onActiveColor else onInactiveColor,
                    )
                }
            },
            onClick = onVideoSelected,
        )
    }
}

@Composable
private fun PillHalf(
    label: String,
    isActive: Boolean,
    activeColor: Color,
    onActiveColor: Color,
    onInactiveColor: Color,
    onClick: () -> Unit,
    leading: (@Composable () -> Unit)? = null,
) {
    val bg = if (isActive) activeColor else Color.Transparent
    val fg = if (isActive) onActiveColor else onInactiveColor

    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(50))
                .background(bg)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Text(
            text = label,
            color = fg,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
