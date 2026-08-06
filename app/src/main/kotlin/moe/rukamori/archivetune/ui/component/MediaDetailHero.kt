/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.component

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AppBarHeight
import moe.rukamori.archivetune.ui.utils.YtimgResizePolicy
import moe.rukamori.archivetune.ui.utils.fadingEdge
import moe.rukamori.archivetune.ui.utils.resize

@Composable
public fun MediaDetailHero(
    title: String,
    thumbnailUrl: String?,
    @DrawableRes fallbackIcon: Int,
    systemBarsTopPadding: Dp,
    isAdded: Boolean,
    @StringRes addContentDescription: Int,
    @StringRes removeContentDescription: Int,
    onShuffle: (() -> Unit)?,
    onPlay: (() -> Unit)?,
    onToggleAdd: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: AnnotatedString? = null,
    metadata: String? = null,
    description: String? = null,
    additionalPrimaryActions: (@Composable RowScope.(Color) -> Unit)? = null,
    // Optional looping animated canvas (Apple Music-style animated cover
    // art) layered on top of the static thumbnail. When both
    // `canvasPrimaryUrl` and `canvasFallbackUrl` are null/blank the canvas
    // layer is skipped entirely and the hero renders as before. Pass the
    // same URLs the song player uses (artwork.animated / artwork.videoUrl)
    // to make the album thumbnail loop the same animated visual art on the
    // album screen.
    canvasPrimaryUrl: String? = null,
    canvasFallbackUrl: String? = null,
    canvasIsPlaying: Boolean = false,
    // DEPRECATED / NO-OP: previously, when true, the big "Play" pill button
    // rendered a self-contained blurred copy of the hero artwork behind the
    // icon + "Play" text (a frosted-glass play button). The sampled artwork
    // background was removed because it read as a "misplaced image" /
    // "glitched preview" that clashed with the dark theme. The parameter is
    // kept for source compatibility with the 6 call sites that pass it, but
    // the play button now ALWAYS renders as a clean solid `contentColor`
    // pill regardless of this flag's value.
    useBlurredPlayButton: Boolean = false,
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val menuState = LocalMenuState.current
    val heroContentColor =
        if (surfaceColor.luminance() > 0.5f) {
            MaterialTheme.colorScheme.onSurface
        } else {
            Color.White
        }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = MediaDetailHeroMinHeight)
                .background(surfaceColor)
                .clipToBounds(),
    ) {
        if (thumbnailUrl != null) {
            AsyncImage(
                model =
                    thumbnailUrl.resize(
                        width = MediaDetailHeroArtworkSizePx,
                        height = MediaDetailHeroArtworkSizePx,
                        sizeBuckets = MediaDetailHeroArtworkSizeBuckets,
                        ytimgResizePolicy = YtimgResizePolicy.PreserveOriginal,
                    ),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        } else {
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(fallbackIcon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(96.dp),
                )
            }
        }

        // Looping animated canvas overlay — same component used by the song
        // player's Apple Music artwork. Stacks on top of the static
        // thumbnail so when the animated video isn't ready yet (or fails
        // to load) the user still sees the static cover. The canvas
        // fades in via its own `animateFloatAsState(tween(300))` once the
        // first frame is rendered. Only mounted when at least one of the
        // canvas URLs is non-blank — keeps the cost zero for albums that
        // don't have an animated cover.
        if (!canvasPrimaryUrl.isNullOrBlank() || !canvasFallbackUrl.isNullOrBlank()) {
            moe.rukamori.archivetune.ui.player.CanvasArtworkPlayer(
                primaryUrl = canvasPrimaryUrl,
                fallbackUrl = canvasFallbackUrl,
                isPlaying = canvasIsPlaying,
                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                modifier = Modifier.matchParentSize(),
            )
        }

        // ─── Flat gradient backdrop ──────────────────────────────────────
        // The previous iteration of the hero attempted to render a second
        // blurred copy of the thumbnail clipped to the bottom ~62% of the
        // hero, with a frosted-glass tint on top. In practice this rarely
        // read as "frosted glass" — on dense 2×2 playlist thumbnails the
        // blur was barely perceptible behind the action-button row, and
        // on bright thumbnails the frosted tint either washed the artwork
        // out (35% alpha) or hid the blur entirely (55% alpha).
        //
        // We've reverted to the original flat vertical gradient that was
        // used before the blur was introduced. This gives a clean,
        // predictable transition from sharp artwork at the top → solid
        // surfaceColor at the bottom, which is what every playlist /
        // album / artist screen in the app was originally designed
        // against. The action-button Column sits on the solid-surface
        // portion at the bottom, so button contrast is consistent
        // regardless of the thumbnail's average color.
        //
        // Layer order (bottom → top):
        //   1. Original thumbnail (full hero, sharp)
        //   2. Vertical gradient:
        //      - 0.00 → 0.18: black @ 42% → transparent (status-bar legibility)
        //      - 0.18 → 0.42: transparent (sharp artwork visible)
        //      - 0.42 → 0.72: transparent → surfaceColor @ 78% (fade into solid)
        //      - 0.72 → 1.00: surfaceColor @ 78% → surfaceColor (solid backdrop
        //        for the action-button row)
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.42f),
                            0.18f to Color.Transparent,
                            0.42f to Color.Transparent,
                            0.72f to surfaceColor.copy(alpha = 0.78f),
                            1f to surfaceColor,
                        ),
                    ),
        )

        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .widthIn(max = MediaDetailContentMaxWidth)
                    .padding(
                        start = MediaDetailHorizontalPadding,
                        top = systemBarsTopPadding + AppBarHeight + 96.dp,
                        end = MediaDetailHorizontalPadding,
                        bottom = 24.dp,
                    ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Title — use a tighter lineHeight than headlineLarge's default
            // 40sp to avoid the "weird spacing" the user reported when a
            // playlist title wraps to two lines.
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge.copy(lineHeight = 36.sp),
                color = heroContentColor,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium.copy(lineHeight = 22.sp),
                    color = heroContentColor.copy(alpha = 0.82f),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            description?.takeIf(String::isNotBlank)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                    color = heroContentColor.copy(alpha = 0.76f),
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            metadata?.takeIf(String::isNotBlank)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = heroContentColor.copy(alpha = 0.62f),
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                )
            }

            MediaDetailPrimaryActions(
                isAdded = isAdded,
                contentColor = heroContentColor,
                contrastingColor = surfaceColor,
                addContentDescription = addContentDescription,
                removeContentDescription = removeContentDescription,
                onShuffle = onShuffle,
                onPlay = onPlay,
                onToggleAdd =
                    remember(isAdded, menuState, onToggleAdd, removeContentDescription, title) {
                        onToggleAdd?.let { toggleAdd ->
                            if (isAdded) {
                                {
                                    menuState.showDialog {
                                        MediaDetailRemovalConfirmationDialog(
                                            title = title,
                                            removeContentDescription = removeContentDescription,
                                            onDismiss = menuState::dismissDialog,
                                            onConfirm = {
                                                menuState.dismissDialog()
                                                toggleAdd()
                                            },
                                        )
                                    }
                                }
                            } else {
                                toggleAdd
                            }
                        }
                    },
                additionalActions = additionalPrimaryActions,
                modifier = Modifier.padding(top = 12.dp),
                thumbnailUrl = thumbnailUrl,
                useBlurredPlayButton = useBlurredPlayButton,
            )
        }
    }
}

@Composable
private fun MediaDetailRemovalConfirmationDialog(
    title: String,
    @StringRes removeContentDescription: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    DefaultDialog(
        onDismiss = onDismiss,
        title = {
            Text(text = stringResource(removeContentDescription))
        },
        buttons = {
            TextButton(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(text = stringResource(android.R.string.cancel))
            }
            TextButton(
                onClick = onConfirm,
                shapes = ButtonDefaults.shapes(),
                colors =
                    ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
            ) {
                Text(text = stringResource(removeContentDescription))
            }
        },
    ) {
        Text(
            text = stringResource(R.string.remove_from_library_confirm, title),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
public fun MediaDetailPrimaryActions(
    isAdded: Boolean,
    contentColor: Color,
    contrastingColor: Color,
    @StringRes addContentDescription: Int,
    @StringRes removeContentDescription: Int,
    onShuffle: (() -> Unit)?,
    onPlay: (() -> Unit)?,
    onToggleAdd: (() -> Unit)?,
    modifier: Modifier = Modifier,
    additionalActions: (@Composable RowScope.(Color) -> Unit)? = null,
    // The hero artwork URL. Used for the main hero artwork above. (Previously
    // also used as the backdrop source for the liquid-glass play button — that
    // sampling has been removed; see useBlurredPlayButton below.)
    thumbnailUrl: String? = null,
    // DEPRECATED / NO-OP: the liquid-glass play button (which sampled
    // `thumbnailUrl` as a blurred background behind the play icon) has been
    // removed. The play button now ALWAYS renders as a clean solid
    // `contentColor` pill. Kept for source compatibility with the public
    // MediaDetailHero signature.
    useBlurredPlayButton: Boolean = false,
) {
    val secondaryButtonColors =
        IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = contentColor.copy(alpha = 0.16f),
            contentColor = contentColor,
            disabledContainerColor = contentColor.copy(alpha = 0.08f),
            disabledContentColor = contentColor.copy(alpha = 0.38f),
        )
    val actionScrollState = rememberScrollState()
    val actionScrollMaxValue = actionScrollState.maxValue
    val density = LocalDensity.current

    LaunchedEffect(actionScrollMaxValue) {
        if (
            actionScrollMaxValue > 0 &&
            actionScrollMaxValue != Int.MAX_VALUE &&
            actionScrollState.value == 0
        ) {
            // When the row only barely overflows the viewport (common on the
            // artist page where there are exactly 4 actions: Shuffle / Play /
            // Add / Radio), centering the scroll cuts off equal pixels on each
            // side — and because the balanced layout reserves more space on the
            // side with more actions, the rightmost action (Radio) ends up
            // flush against the right edge and gets visually clipped by the
            // fadingEdge. Bias toward the right edge in that case so Radio
            // keeps its full breathing room. For wide rows (playlists with
            // many actions), keep the centered scroll so both sides preview.
            val overflowDp = with(density) { actionScrollMaxValue.toDp() }
            val target =
                if (overflowDp < 80.dp) {
                    actionScrollMaxValue
                } else {
                    actionScrollMaxValue / 2
                }
            actionScrollState.scrollTo(target)
        }
    }

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxWidth()
                .widthIn(max = MediaDetailContentMaxWidth),
    ) {
        val actionViewportWidth = maxWidth

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fadingEdge(horizontal = MediaDetailActionEdgeFade)
                    .horizontalScroll(actionScrollState)
                    // End padding ensures the rightmost action (e.g. Radio on the
                    // artist page) always has visible breathing room even when the
                    // balanced layout reserves more space on the opposite side.
                    // Start padding mirrors it for symmetry so the auto-center
                    // scroll position doesn't bias toward one edge.
                    .padding(horizontal = MediaDetailActionHorizontalPadding),
        ) {
            MediaDetailBalancedActionLayout(
                actionRowScope = this,
                modifier = Modifier.widthIn(min = actionViewportWidth),
            ) {
                onShuffle?.let { shuffle ->
                    FilledTonalIconButton(
                        onClick = shuffle,
                        shape = CircleShape,
                        colors = secondaryButtonColors,
                        modifier =
                            Modifier
                                .layoutId(MediaDetailActionLayoutId.Shuffle)
                                .size(MediaDetailSecondaryActionSize),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.shuffle),
                            contentDescription = stringResource(R.string.shuffle),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                onPlay?.let { play ->
                    val playButtonHeight = ButtonDefaults.MediumContainerHeight
                    // The liquid-glass play button (which sampled the hero artwork via a
                    // dedicated LayerBackdrop and rendered it as a blurred multicolored
                    // background behind the play icon) has been REMOVED. The user reported
                    // the sampled artwork background looked like a "misplaced image" /
                    // "glitched preview" that clashed with the dark theme — the blurred
                    // album-art colors behind the play icon read as a broken render rather
                    // than intentional glassmorphism. The `useBlurredPlayButton` parameter
                    // is kept for source compatibility with the 6 call sites that pass it,
                    // but is now a no-op: the play button always renders as a clean solid
                    // `contentColor` pill (same as the non-liquid-glass path). The
                    // `thumbnailUrl` is still used for the main hero artwork above; only
                    // the play-button backdrop sampling is removed.
                    Button(
                        onClick = play,
                        shape = RoundedCornerShape(percent = 50),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = contentColor,
                                contentColor = contrastingColor,
                            ),
                        contentPadding = ButtonDefaults.contentPaddingFor(playButtonHeight, hasStartIcon = true),
                        modifier =
                            Modifier
                                .layoutId(MediaDetailActionLayoutId.Play)
                                .heightIn(min = playButtonHeight),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.play),
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.iconSizeFor(playButtonHeight)),
                        )
                        Spacer(modifier = Modifier.width(ButtonDefaults.iconSpacingFor(playButtonHeight)))
                        Text(
                            text = stringResource(R.string.play),
                            style = ButtonDefaults.textStyleFor(playButtonHeight),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                onToggleAdd?.let { toggleAdd ->
                    FilledTonalIconButton(
                        onClick = toggleAdd,
                        shape = CircleShape,
                        colors = secondaryButtonColors,
                        modifier =
                            Modifier
                                .layoutId(MediaDetailActionLayoutId.ToggleAdd)
                                .size(MediaDetailSecondaryActionSize),
                    ) {
                        Icon(
                            painter = painterResource(if (isAdded) R.drawable.done else R.drawable.add),
                            contentDescription =
                                stringResource(
                                    if (isAdded) removeContentDescription else addContentDescription,
                                ),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                additionalActions?.invoke(this, contentColor)
            }
        }
    }
}

@Composable
private fun MediaDetailBalancedActionLayout(
    actionRowScope: RowScope,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Layout(
        content = { content(actionRowScope) },
        modifier = modifier,
    ) { measurables, constraints ->
        val actionSpacing = MediaDetailActionSpacing.roundToPx()
        val shuffleActionIndex = measurables.indexOfFirst { it.layoutId == MediaDetailActionLayoutId.Shuffle }
        val playActionIndex = measurables.indexOfFirst { it.layoutId == MediaDetailActionLayoutId.Play }
        val toggleAddActionIndex = measurables.indexOfFirst { it.layoutId == MediaDetailActionLayoutId.ToggleAdd }
        val placeables =
            measurables.map { measurable ->
                measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
            }
        val shuffleAction = placeables.getOrNull(shuffleActionIndex)
        val playAction = placeables.getOrNull(playActionIndex)
        val toggleAddAction = placeables.getOrNull(toggleAddActionIndex)
        val otherActions =
            placeables.filterIndexed { index, _ ->
                index != shuffleActionIndex &&
                    index != playActionIndex &&
                    index != toggleAddActionIndex
            }
        val centeredContentWidth =
            placeables.sumOf { it.width } +
                actionSpacing * (placeables.size - 1).coerceAtLeast(0)
        val leftOtherActionCount = otherActions.size / 2
        val leftActions =
            buildList {
                addAll(otherActions.take(leftOtherActionCount))
                if (shuffleAction != null) {
                    add(shuffleAction)
                }
            }
        val rightActions =
            buildList {
                if (toggleAddAction != null) {
                    add(toggleAddAction)
                }
                addAll(otherActions.drop(leftOtherActionCount))
            }
        val leftActionsWidth =
            leftActions.sumOf { it.width } +
                actionSpacing * (leftActions.size - 1).coerceAtLeast(0)
        val rightActionsWidth =
            rightActions.sumOf { it.width } +
                actionSpacing * (rightActions.size - 1).coerceAtLeast(0)
        val balancedContentWidth =
            if (playAction == null) {
                centeredContentWidth
            } else {
                val sideSpacing = if (leftActions.isEmpty() && rightActions.isEmpty()) 0 else actionSpacing
                playAction.width + 2 * (maxOf(leftActionsWidth, rightActionsWidth) + sideSpacing)
            }
        val layoutWidth =
            if (constraints.hasBoundedWidth) {
                constraints.maxWidth
            } else {
                balancedContentWidth.coerceAtLeast(constraints.minWidth)
            }
        val contentHeight = placeables.maxOfOrNull { it.height } ?: 0
        val layoutHeight =
            if (constraints.hasBoundedHeight) {
                contentHeight.coerceIn(constraints.minHeight, constraints.maxHeight)
            } else {
                contentHeight.coerceAtLeast(constraints.minHeight)
            }

        layout(layoutWidth, layoutHeight) {
            if (playAction == null) {
                var actionX = (layoutWidth - centeredContentWidth) / 2
                placeables.forEach { action ->
                    action.placeRelative(
                        x = actionX,
                        y = (layoutHeight - action.height) / 2,
                    )
                    actionX += action.width + actionSpacing
                }
                return@layout
            }

            val playActionX = (layoutWidth - playAction.width) / 2
            var leftActionX = playActionX - actionSpacing - leftActionsWidth
            var rightActionX = playActionX + playAction.width + actionSpacing

            leftActions.forEach { action ->
                action.placeRelative(
                    x = leftActionX,
                    y = (layoutHeight - action.height) / 2,
                )
                leftActionX += action.width + actionSpacing
            }
            rightActions.forEach { action ->
                action.placeRelative(
                    x = rightActionX,
                    y = (layoutHeight - action.height) / 2,
                )
                rightActionX += action.width + actionSpacing
            }
            playAction.placeRelative(
                x = playActionX,
                y = (layoutHeight - playAction.height) / 2,
            )
        }
    }
}

@Composable
public fun MediaDetailAction(
    @StringRes contentDescription: Int,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isDestructive: Boolean = false,
    content: @Composable () -> Unit,
) {
    val actionDescription = stringResource(contentDescription)
    val colors =
        if (isDestructive) {
            IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.16f),
                contentColor = MaterialTheme.colorScheme.error,
                disabledContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
                disabledContentColor = MaterialTheme.colorScheme.error.copy(alpha = 0.38f),
            )
        } else {
            IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = contentColor.copy(alpha = 0.16f),
                contentColor = contentColor,
                disabledContainerColor = contentColor.copy(alpha = 0.08f),
                disabledContentColor = contentColor.copy(alpha = 0.38f),
            )
        }

    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        colors = colors,
        modifier =
            modifier
                .size(MediaDetailActionSize)
                .semantics { this.contentDescription = actionDescription },
    ) {
        content()
    }
}

@Composable
public fun MediaDetailIconAction(
    @DrawableRes icon: Int,
    @StringRes contentDescription: Int,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isDestructive: Boolean = false,
) {
    MediaDetailAction(
        contentDescription = contentDescription,
        contentColor = contentColor,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        isDestructive = isDestructive,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
        )
    }
}

private const val MediaDetailHeroArtworkSizePx = 1200
private val MediaDetailHeroArtworkSizeBuckets = listOf(MediaDetailHeroArtworkSizePx)
private val MediaDetailHeroMinHeight = 560.dp
private val MediaDetailHorizontalPadding = 24.dp
private val MediaDetailContentMaxWidth = 720.dp
private val MediaDetailActionSpacing = 12.dp
// Reduced from 20.dp — the previous fade was aggressive enough to make the
// rightmost action (Radio on the artist page) look partially cut off even
// when it was technically within the viewport. 8.dp preserves the visual
// cue that more actions are scrollable without obscuring the edge icon.
private val MediaDetailActionEdgeFade = 8.dp
// Horizontal padding inside the scrollable Row so the first and last actions
// have visible margin from the screen edge. Without this the balanced layout
// can place the rightmost action flush against the viewport boundary.
private val MediaDetailActionHorizontalPadding = 12.dp
private val MediaDetailSecondaryActionSize = 52.dp
private val MediaDetailActionSize = 48.dp

private enum class MediaDetailActionLayoutId {
    Shuffle,
    Play,
    ToggleAdd,
}
