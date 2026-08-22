/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.component

import android.os.Build
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
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
    canvasPrimaryUrl: String? = null,
    canvasFallbackUrl: String? = null,
    canvasIsPlaying: Boolean = false,
    // When false, the canvas TextureView is not rendered (the ExoPlayer is
    // kept alive but paused). Forwarded to CanvasArtworkPlayer.visible.
    // AlbumScreen passes `!lyricsFullScreen` so the canvas's Modifier.blur(72.dp)
    // RenderEffect doesn't keep re-applying every frame while the full-screen
    // lyrics overlay is open on top — freeing the GPU frame budget for the
    // 60 Hz karaoke lyrics sweep.
    canvasVisible: Boolean = true,
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

        if (!canvasPrimaryUrl.isNullOrBlank() || !canvasFallbackUrl.isNullOrBlank()) {
            moe.rukamori.archivetune.ui.player.CanvasArtworkPlayer(
                primaryUrl = canvasPrimaryUrl,
                fallbackUrl = canvasFallbackUrl,
                isPlaying = canvasIsPlaying,
                visible = canvasVisible,
                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                modifier = Modifier.matchParentSize(),
            )
        }

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
                    // Play button — always uses a solid color pill. The liquid-glass layered
                    // Box variant (smoked-glass veil + top-highlight gradient) was REMOVED at
                    // the user's request: "Remove the liquid glass effect from all play buttons
                    // in playlists or anywhere else". The same solid-color path now runs whether
                    // or not a LiquidGlassBackdrop is active upstream.
                    val playButtonHeight = ButtonDefaults.MediumContainerHeight
                    val playShape = RoundedCornerShape(percent = 50)
                    val playPadding =
                        ButtonDefaults.contentPaddingFor(playButtonHeight, hasStartIcon = true)
                    val playIconSize = ButtonDefaults.iconSizeFor(playButtonHeight)
                    val playIconSpacing = ButtonDefaults.iconSpacingFor(playButtonHeight)
                    val playTextStyle = ButtonDefaults.textStyleFor(playButtonHeight)
                    Button(
                        onClick = play,
                        shape = playShape,
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = contentColor,
                                contentColor = contrastingColor,
                            ),
                        contentPadding = playPadding,
                        modifier =
                            Modifier
                                .layoutId(MediaDetailActionLayoutId.Play)
                                .heightIn(min = playButtonHeight),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.play),
                            contentDescription = null,
                            modifier = Modifier.size(playIconSize),
                        )
                        Spacer(modifier = Modifier.width(playIconSpacing))
                        Text(
                            text = stringResource(R.string.play),
                            style = playTextStyle,
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
