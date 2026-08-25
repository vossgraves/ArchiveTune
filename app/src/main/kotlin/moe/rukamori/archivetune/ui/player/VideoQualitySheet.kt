/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.player

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R

/**
 * Video-quality picker, presented as a bottom sheet that slides up from the bottom of the screen.
 *
 * Replaces the [androidx.compose.material3.DropdownMenu] the quality button used to anchor. The
 * dropdown had two problems in the fullscreen overlay: it opened as a small popup pinned under the
 * button in the top-right corner (awkward to reach one-handed in landscape) and it listed every
 * raw resolution with no notion of intent, so "just give me the best" and "don't eat my data" both
 * required knowing which number to pick.
 *
 * The sheet has two pages:
 *  - the **main page** offers the three intents — Auto, Data saver, High quality — plus a row that
 *    opens Advanced,
 *  - the **Advanced page** lists every resolution this device can decode, so an exact pick
 *    (144p … 4320p) is still one tap away.
 *
 * @param preferredHeight current choice, encoded per [VideoQualityPreference].
 * @param availableHeights resolutions YouTube offered for this video that the device can decode,
 *   ascending. Drives the Advanced page.
 * @param selectedHeight the resolution actually playing, shown as a subtitle so the modes report
 *   what they resolved to.
 */
@Composable
internal fun VideoQualitySheet(
    preferredHeight: Int?,
    availableHeights: List<Int>,
    selectedHeight: Int?,
    onPreferredHeightChange: (Int?) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        VideoQualitySheetContent(
            preferredHeight = preferredHeight,
            availableHeights = availableHeights,
            selectedHeight = selectedHeight,
            onSelect = { choice ->
                onPreferredHeightChange(choice)
                onDismissRequest()
            },
        )
    }
}

/**
 * Body of [VideoQualitySheet]. Split out so the two pages can swap in place without the sheet
 * itself being torn down and re-animated.
 */
@Composable
private fun VideoQualitySheetContent(
    preferredHeight: Int?,
    availableHeights: List<Int>,
    selectedHeight: Int?,
    onSelect: (Int?) -> Unit,
) {
    var advancedOpen by remember { mutableStateOf(VideoQualityPreference.isExactHeight(preferredHeight)) }

    AnimatedContent(
        targetState = advancedOpen,
        transitionSpec = {
            // Slide the way the navigation runs: forward into Advanced, backward out of it.
            val direction = if (targetState) 1 else -1
            (
                slideInHorizontally(tween(220)) { width -> direction * width / 3 } +
                    fadeIn(tween(220))
            ) togetherWith (
                slideOutHorizontally(tween(220)) { width -> -direction * width / 3 } +
                    fadeOut(tween(160))
            )
        },
        label = "video-quality-sheet-page",
    ) { showAdvanced ->
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp),
        ) {
            if (showAdvanced) {
                AdvancedQualityPage(
                    preferredHeight = preferredHeight,
                    availableHeights = availableHeights,
                    onBack = { advancedOpen = false },
                    onSelect = onSelect,
                )
            } else {
                MainQualityPage(
                    preferredHeight = preferredHeight,
                    selectedHeight = selectedHeight,
                    onSelect = onSelect,
                    onOpenAdvanced = { advancedOpen = true },
                )
            }
        }
    }
}

/** Auto / Data saver / High quality, plus the row that opens the Advanced page. */
@Composable
private fun MainQualityPage(
    preferredHeight: Int?,
    selectedHeight: Int?,
    onSelect: (Int?) -> Unit,
    onOpenAdvanced: () -> Unit,
) {
    // The resolved resolution is only worth showing next to the mode that produced it — repeating
    // "Playing at 1080p" under all three rows would read as if all three were active.
    val playingLabel = selectedHeight?.let { stringResource(R.string.video_quality_current, formatHeightLabel(it)) }

    SheetTitle(stringResource(R.string.video_quality))

    QualityRow(
        title = stringResource(R.string.video_quality_mode_auto),
        subtitle =
            if (preferredHeight == null && playingLabel != null) {
                playingLabel
            } else {
                stringResource(R.string.video_quality_mode_auto_desc)
            },
        selected = preferredHeight == null,
        onClick = { onSelect(null) },
    )
    QualityRow(
        title = stringResource(R.string.video_quality_mode_data_saver),
        subtitle =
            if (preferredHeight == VideoQualityPreference.DATA_SAVER && playingLabel != null) {
                playingLabel
            } else {
                stringResource(R.string.video_quality_mode_data_saver_desc)
            },
        selected = preferredHeight == VideoQualityPreference.DATA_SAVER,
        onClick = { onSelect(VideoQualityPreference.DATA_SAVER) },
    )
    QualityRow(
        title = stringResource(R.string.video_quality_mode_high),
        subtitle =
            if (preferredHeight == VideoQualityPreference.HIGH_QUALITY && playingLabel != null) {
                playingLabel
            } else {
                stringResource(R.string.video_quality_mode_high_desc)
            },
        selected = preferredHeight == VideoQualityPreference.HIGH_QUALITY,
        onClick = { onSelect(VideoQualityPreference.HIGH_QUALITY) },
    )

    HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))

    val exactHeight = preferredHeight?.takeIf { VideoQualityPreference.isExactHeight(it) }
    QualityRow(
        title = stringResource(R.string.video_quality_advanced),
        subtitle =
            if (exactHeight != null) {
                formatHeightLabel(exactHeight)
            } else {
                stringResource(R.string.video_quality_advanced_desc)
            },
        selected = exactHeight != null,
        onClick = onOpenAdvanced,
        // A chevron rather than a checkmark: this row navigates, it does not itself apply a
        // quality. The `selected` tint still marks it when an exact height is in force.
        trailingIcon = R.drawable.navigate_next,
    )
}

/** Every resolution the device can decode for this video, tallest first. */
@Composable
private fun AdvancedQualityPage(
    preferredHeight: Int?,
    availableHeights: List<Int>,
    onBack: () -> Unit,
    onSelect: (Int?) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(R.drawable.arrow_back),
                contentDescription = stringResource(R.string.video_quality_back),
            )
        }
        Text(
            text = stringResource(R.string.video_quality_advanced_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }

    if (availableHeights.isEmpty()) {
        // Reached only if the device's decoders reject every format YouTube listed. The mode rows
        // on the main page still work (they clamp to whatever plays), so say why the list is empty
        // rather than showing a blank sheet.
        Text(
            text = stringResource(R.string.video_quality_unavailable_on_device),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )
    } else {
        // Capped height + scroll: an 8K video offers ~11 resolutions, which is taller than a
        // landscape sheet can show.
        Column(
            modifier =
                Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
        ) {
            availableHeights.sortedDescending().forEach { height ->
                QualityRow(
                    title = formatHeightLabel(height),
                    subtitle = null,
                    selected = preferredHeight == height,
                    onClick = { onSelect(height) },
                )
            }
        }
    }
}

@Composable
private fun SheetTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 12.dp),
    )
}

/**
 * One selectable row: title, optional subtitle, and a trailing checkmark (or [trailingIcon]) when
 * this row is the active choice.
 */
@Composable
private fun QualityRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
    @DrawableRes trailingIcon: Int? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color =
                    if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        when {
            trailingIcon != null ->
                Icon(
                    painter = painterResource(trailingIcon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )

            selected ->
                Icon(
                    painter = painterResource(R.drawable.check),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
        }
    }
}

/**
 * Short label for the quality button in the control pill, so the pill itself reports the current
 * choice instead of only opening the sheet.
 *
 * Modes report the resolution they resolved to when it is known ("Auto" alone tells the user
 * nothing about what they are actually watching); an exact pick reports its own height.
 */
@Composable
internal fun videoQualityPillLabel(
    preferredHeight: Int?,
    selectedHeight: Int?,
): String =
    when {
        VideoQualityPreference.isExactHeight(preferredHeight) -> "${preferredHeight}p"
        selectedHeight != null -> "${selectedHeight}p"
        preferredHeight == VideoQualityPreference.DATA_SAVER ->
            stringResource(R.string.video_quality_mode_data_saver)

        preferredHeight == VideoQualityPreference.HIGH_QUALITY ->
            stringResource(R.string.video_quality_mode_high)

        else -> stringResource(R.string.video_quality_mode_auto)
    }
