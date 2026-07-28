/*
 * Copyright © Rukamori
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.QobuzAudioQuality

/**
 * Quality tiers offered when starting a download, expressed in terms a listener recognises rather
 * than the provider's internal format ids.
 */
enum class DownloadQualityChoice(
    val qobuzQuality: QobuzAudioQuality,
) {
    /** Whatever the source offers, up to 24-bit/192 kHz. */
    MAX(QobuzAudioQuality.MAX),

    /** 24-bit, typically 96 kHz. */
    HI_RES(QobuzAudioQuality.HI_RES),

    /** CD quality: 16-bit/44.1 kHz, still lossless. */
    LOSSLESS(QobuzAudioQuality.FLAC),
    ;

    companion object {
        fun forQobuzQuality(quality: QobuzAudioQuality): DownloadQualityChoice =
            entries.first { it.qobuzQuality == quality }
    }
}

private val DownloadQualityChoice.titleRes: Int
    get() =
        when (this) {
            DownloadQualityChoice.MAX -> R.string.download_quality_max
            DownloadQualityChoice.HI_RES -> R.string.download_quality_hi_res
            DownloadQualityChoice.LOSSLESS -> R.string.download_quality_lossless
        }

private val DownloadQualityChoice.subtitleRes: Int
    get() =
        when (this) {
            DownloadQualityChoice.MAX -> R.string.download_quality_max_description
            DownloadQualityChoice.HI_RES -> R.string.download_quality_hi_res_description
            DownloadQualityChoice.LOSSLESS -> R.string.download_quality_lossless_description
        }

/**
 * Asks which quality tier to download at, pre-selecting the user's global preference.
 *
 * [onConfirm] reports the chosen tier plus whether it should become the new default, so a user who
 * does not want to be asked every time can opt out after one prompt.
 */
@Composable
fun DownloadQualityDialog(
    initialChoice: DownloadQualityChoice,
    onDismiss: () -> Unit,
    onConfirm: (choice: DownloadQualityChoice, remember: Boolean) -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf(initialChoice) }
    var rememberChoice by rememberSaveable { mutableStateOf(false) }

    ActionPromptDialog(
        title = stringResource(R.string.download_quality_title),
        onDismiss = onDismiss,
        onConfirm = {
            onConfirm(selected, rememberChoice)
            onDismiss()
        },
    ) {
        DownloadQualityChoice.entries.forEach { choice ->
            ListItem(
                headlineContent = { Text(stringResource(choice.titleRes)) },
                supportingContent = { Text(stringResource(choice.subtitleRes)) },
                leadingContent = {
                    RadioButton(
                        selected = choice == selected,
                        onClick = null,
                    )
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.clickable { selected = choice },
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { rememberChoice = !rememberChoice }
                    .padding(vertical = 4.dp),
        ) {
            Checkbox(
                checked = rememberChoice,
                onCheckedChange = { rememberChoice = it },
            )
            Text(
                text = stringResource(R.string.download_quality_remember),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** A container the app can actually write for a given track, plus why it may be unavailable. */
data class ExportFormatOption(
    val format: ExportFormat,
    val enabled: Boolean,
    /** Resource explaining why this target is unavailable; only read when [enabled] is false. */
    val disabledReasonRes: Int? = null,
)

/**
 * Every container the cache sniffer can report, so "rewrap into what it already is" is always an
 * option. Restricting this to FLAC/M4A/OPUS left the common case — YouTube Opus, which arrives in a
 * WebM container — with no selectable target at all.
 */
enum class ExportFormat(
    val extension: String,
    val isLossless: Boolean,
) {
    FLAC("flac", isLossless = true),
    WAV("wav", isLossless = true),
    M4A("m4a", isLossless = false),
    WEBM("webm", isLossless = false),
    OGG("ogg", isLossless = false),
    MP3("mp3", isLossless = false),
    ;

    companion object {
        fun forExtension(extension: String?): ExportFormat? {
            val normalised = extension?.lowercase()?.removePrefix(".") ?: return null
            return entries.firstOrNull { it.extension == normalised }
        }
    }
}

private val ExportFormat.titleRes: Int
    get() =
        when (this) {
            ExportFormat.FLAC -> R.string.export_format_flac
            ExportFormat.WAV -> R.string.export_format_wav
            ExportFormat.M4A -> R.string.export_format_m4a
            ExportFormat.WEBM -> R.string.export_format_webm
            ExportFormat.OGG -> R.string.export_format_ogg
            ExportFormat.MP3 -> R.string.export_format_mp3
        }

/**
 * Works out which containers can honestly be produced for a track.
 *
 * The app has no audio transcoder — `jaudiotagger` writes tags, not audio — so the cached bytes can
 * only ever be copied out in the container they already use. That makes the source format the only
 * real choice, which is why just one enabled row comes back.
 *
 * When the source is lossy, FLAC is still listed but disabled: users go looking for it, and saying
 * why it is impossible is more useful than omitting it and looking like a missing feature. Producing
 * one would mean writing a `.flac` that is still lossy inside.
 */
fun exportFormatOptionsFor(sourceExtension: String?): List<ExportFormatOption> {
    // Fall back to M4A to match detectCachedExtension, which assumes it when sniffing fails.
    val source = ExportFormat.forExtension(sourceExtension) ?: ExportFormat.M4A

    // The source's own container: a straight byte copy, so always genuinely available.
    val options = mutableListOf(ExportFormatOption(source, enabled = true))

    // Only worth showing a second row when the user might reasonably expect FLAC and cannot have it.
    // Listing every other container disabled would be noise: none of them are reachable either.
    if (!source.isLossless) {
        options +=
            ExportFormatOption(
                format = ExportFormat.FLAC,
                enabled = false,
                disabledReasonRes = R.string.export_format_unavailable_lossy_source,
            )
    }

    return options
}

/**
 * Lets the user pick an export container, showing impossible targets greyed out with the reason.
 *
 * [onRedownloadLossless] is offered when the source is lossy: re-downloading from a lossless
 * provider is the only honest route to a real FLAC.
 */
@Composable
fun ExportFormatDialog(
    sourceExtension: String?,
    onDismiss: () -> Unit,
    onConfirm: (ExportFormat) -> Unit,
    onRedownloadLossless: (() -> Unit)? = null,
) {
    val options = remember(sourceExtension) { exportFormatOptionsFor(sourceExtension) }
    val firstEnabled = remember(options) { options.firstOrNull { it.enabled }?.format }
    var selected by rememberSaveable(sourceExtension) { mutableStateOf(firstEnabled) }

    ActionPromptDialog(
        title = stringResource(R.string.export_format_title),
        onDismiss = onDismiss,
        onConfirm = {
            selected?.let(onConfirm)
            onDismiss()
        },
    ) {
        options.forEach { option ->
            val disabledReason = option.disabledReasonRes
            ListItem(
                headlineContent = { Text(stringResource(option.format.titleRes)) },
                supportingContent =
                    if (!option.enabled && disabledReason != null) {
                        { Text(stringResource(disabledReason)) }
                    } else {
                        null
                    },
                leadingContent = {
                    RadioButton(
                        selected = option.format == selected,
                        onClick = null,
                        enabled = option.enabled,
                    )
                },
                colors =
                    ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        headlineColor =
                            if (option.enabled) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            },
                    ),
                modifier =
                    Modifier.clickable(enabled = option.enabled) {
                        selected = option.format
                    },
            )
        }

        if (onRedownloadLossless != null && options.none { it.enabled && it.format == ExportFormat.FLAC }) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.export_format_redownload_lossless)) },
                supportingContent = {
                    Text(stringResource(R.string.export_format_redownload_lossless_description))
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                modifier =
                    Modifier.clickable {
                        onRedownloadLossless()
                        onDismiss()
                    },
            )
        }
    }
}
