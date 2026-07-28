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

enum class ExportFormat(
    val extension: String,
    val mimeType: String,
) {
    /** Only genuinely available when the bytes on disk are already FLAC. */
    FLAC("flac", "audio/flac"),

    /** The usual container for AAC streams cached from YouTube. */
    M4A("m4a", "audio/mp4"),

    /** The usual container for Opus streams cached from YouTube. */
    OPUS("opus", "audio/opus"),
}

private val ExportFormat.titleRes: Int
    get() =
        when (this) {
            ExportFormat.FLAC -> R.string.export_format_flac
            ExportFormat.M4A -> R.string.export_format_m4a
            ExportFormat.OPUS -> R.string.export_format_opus
        }

/**
 * Works out which containers can honestly be produced for a track.
 *
 * The app has no audio transcoder — `jaudiotagger` writes tags, not audio — so the cached bytes can
 * only ever be rewrapped, never converted. A lossy stream can therefore not become a real FLAC, and
 * offering one would hand the user a `.flac` that is still lossy inside. Impossible targets are
 * returned disabled with a reason rather than dropped, so the limitation is visible instead of
 * looking like a missing feature.
 */
fun exportFormatOptionsFor(sourceExtension: String?): List<ExportFormatOption> {
    val normalised = sourceExtension?.lowercase()?.removePrefix(".")
    val sourceIsLossless = normalised == "flac"

    return ExportFormat.entries.map { format ->
        when {
            // Rewrapping into the container the bytes already use is always safe.
            normalised != null && format.extension == normalised ->
                ExportFormatOption(format, enabled = true)

            format == ExportFormat.FLAC && !sourceIsLossless ->
                ExportFormatOption(
                    format = format,
                    enabled = false,
                    disabledReasonRes = R.string.export_format_unavailable_lossy_source,
                )

            else ->
                ExportFormatOption(
                    format = format,
                    enabled = false,
                    disabledReasonRes = R.string.export_format_unavailable_no_transcoder,
                )
        }
    }
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
