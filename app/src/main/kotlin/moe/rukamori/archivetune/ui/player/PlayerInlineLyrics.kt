/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import moe.rukamori.archivetune.lyrics.LyricsEntry
import moe.rukamori.archivetune.lyrics.LyricsUtils
import moe.rukamori.archivetune.lyrics.LyricsUtils.findCurrentLineIndex
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.component.SpotifyWord

/**
 * The current song's lyrics parsed into timed lines, or an empty list when there are none or
 * they are unsynced — the pane needs timings, so plain-text lyrics do not qualify.
 *
 * Shared by every player style that shows lyrics in place of the artwork (see [PlayerInlineLyrics]),
 * so the "which lyric formats count as synced" decision is made once rather than per style.
 */
@Composable
fun rememberInlineLyricLines(playerConnection: PlayerConnection): List<LyricsEntry> {
    val entity by playerConnection.currentLyrics.collectAsStateWithLifecycle(initialValue = null)
    val text = entity?.lyrics?.trim()?.takeIf { it.isNotBlank() }
    return remember(text) {
        when {
            text == null -> emptyList()
            LyricsUtils.isTtml(text) ->
                LyricsUtils.parseTtml(text, playerConnection.player.duration.takeIf { it > 0 }?.toInt())
            LyricsUtils.isLineSyncedLrc(text) -> LyricsUtils.parseLyrics(text)
            else -> emptyList()
        }
    }
}

/**
 * BitChord-style lyrics pane that lives ON the expanded player: rendered in place of the
 * artwork while synced lyrics are available. Shows the current line word-synced (the same
 * pill sweep as the Spotify lyrics mode) with the following lines dimmed below; tapping the
 * pane opens the full lyrics page.
 *
 * Deliberately lightweight: three visible lines, no scrolling (the player sheet owns vertical
 * drags), no selection, no romanisation — the full lyrics page owns those. [lines] comes
 * pre-parsed from the caller so the pane only re-parses when the song changes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerInlineLyrics(
    lines: List<LyricsEntry>,
    positionProvider: () -> Long,
    isPlaying: Boolean,
    textColor: Color,
    artworkUrl: String?,
    modifier: Modifier = Modifier,
    onOpenLyrics: (() -> Unit)? = null,
) {
    if (lines.isEmpty()) return

    // Poll the player position — 50ms while playing keeps the word sweep smooth, a slow
    // 250ms tick is enough while paused.
    var position by remember { mutableLongStateOf(positionProvider()) }
    LaunchedEffect(isPlaying) {
        while (true) {
            position = positionProvider()
            delay(if (isPlaying) 50L else 250L)
        }
    }

    val currentLineIndex = findCurrentLineIndex(lines, position)
    // Before the first line (index -1) we still show the opening lines, dimmed.
    val startIndex = currentLineIndex.coerceAtLeast(0)
    val endIndex = (startIndex + 3).coerceAtMost(lines.size)

    Box(
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable(enabled = onOpenLyrics != null) { onOpenLyrics?.invoke() },
    ) {
        // Blurred-ish artwork backdrop so the pane keeps a sense of the song it replaced.
        if (artworkUrl != null) {
            AsyncImage(
                model = artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(0.9f),
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                                1f to MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                            ),
                        ),
            )
        }

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterStart)
                    .padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            for (index in startIndex until endIndex) {
                val entry = lines[index]
                if (entry.text.isBlank()) continue
                if (index > startIndex) Spacer(modifier = Modifier.padding(bottom = 10.dp))
                val isActive = index == currentLineIndex
                val lineColor = if (isActive) textColor else textColor.copy(alpha = 0.42f)
                val words = entry.words
                if (isActive && !words.isNullOrEmpty()) {
                    FlowRow(modifier = Modifier.fillMaxWidth()) {
                        words.forEach { word ->
                            if (word.text.isBlank()) return@forEach
                            SpotifyWord(
                                word = word,
                                isLineActive = true,
                                pillVisible = true,
                                currentPositionMs = position,
                                textColor = lineColor,
                                inactiveAlpha = 0.45f,
                                fontSize = 18f,
                                isBackground = word.isBackground,
                                lyricsFontFamily = null,
                                isRtl = false,
                            )
                        }
                    }
                } else {
                    Text(
                        text = entry.text,
                        fontSize = if (isActive) 19.sp else 16.sp,
                        lineHeight = if (isActive) 26.sp else 22.sp,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                        color = lineColor,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
