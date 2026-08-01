/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.player

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.Player.STATE_BUFFERING
import androidx.media3.common.Player.STATE_READY
import androidx.navigation.NavController
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.BlurRadiusKey
import moe.rukamori.archivetune.constants.DisableBlurKey
import moe.rukamori.archivetune.constants.EnableHapticFeedbackKey
import moe.rukamori.archivetune.constants.AutoHideLyricsPlayerControlsKey
import moe.rukamori.archivetune.constants.LyricsBackgroundStyle
import moe.rukamori.archivetune.constants.LyricsBackgroundStyleKey
import moe.rukamori.archivetune.constants.LyricsMode
import moe.rukamori.archivetune.constants.LyricsModeKey
import moe.rukamori.archivetune.constants.PlayerBackgroundStyle
import moe.rukamori.archivetune.constants.PlayerBackgroundStyleKey
import moe.rukamori.archivetune.constants.PlayerCustomBlurKey
import moe.rukamori.archivetune.constants.PlayerCustomBrightnessKey
import moe.rukamori.archivetune.constants.PlayerCustomContrastKey
import moe.rukamori.archivetune.constants.PlayerCustomImageUriKey
import moe.rukamori.archivetune.constants.ShowLyricsPlayerControlsKey
import moe.rukamori.archivetune.db.entities.LyricsEntity
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.LyricsEnhanced
import moe.rukamori.archivetune.ui.component.LyricsV2
import moe.rukamori.archivetune.ui.component.PlayerSliderTrack
import moe.rukamori.archivetune.ui.menu.LyricsMenu
import moe.rukamori.archivetune.ui.theme.PlayerColorExtractor
import moe.rukamori.archivetune.ui.theme.PlayerPaletteCache
import moe.rukamori.archivetune.playback.artwork.PlayerPaletteCacheKey
import moe.rukamori.archivetune.playback.artwork.guessArtworkProvider
import moe.rukamori.archivetune.utils.ImageBlurUtils
import moe.rukamori.archivetune.utils.makeTimeString
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import kotlin.coroutines.cancellation.CancellationException

private val AppleMusicFallbackGradient =
    listOf(
        Color(0xFF202020),
        Color(0xFF141414),
        Color(0xFF050505),
    )

@Suppress("UNUSED_PARAMETER")
@Composable
fun LyricsScreen(
    mediaMetadata: MediaMetadata,
    onBackClick: () -> Unit,
    navController: NavController,
    lyricsSyncOffset: Int,
    onLyricsSyncOffsetChange: (Int) -> Unit,
    onQueueClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    backHandlerEnabled: Boolean = true,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val player = playerConnection.player
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val view = LocalView.current

    val playbackState by playerConnection.playbackState.collectAsStateWithLifecycle()
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val deviceMusicVolumeController = rememberDeviceMusicVolumeController()
    val onVolumeChange =
        remember(deviceMusicVolumeController) {
            { volume: Float ->
                deviceMusicVolumeController.setVolumeFraction(volume)
            }
        }
    val currentLyrics by playerConnection.currentLyrics.collectAsStateWithLifecycle(initialValue = null)

    val (enableHapticFeedback) = rememberPreference(EnableHapticFeedbackKey, true)
    val lyricsMode by rememberEnumPreference(LyricsModeKey, LyricsMode.ENHANCED)
    val playerBackground by rememberEnumPreference(PlayerBackgroundStyleKey, PlayerBackgroundStyle.DEFAULT)
    val configuredLyricsBackground by rememberEnumPreference(LyricsBackgroundStyleKey, LyricsBackgroundStyle.DEFAULT)
    val lyricsBackground = configuredLyricsBackground.resolveFor(playerBackground)
    val disableBlur by rememberPreference(DisableBlurKey, false)
    val blurRadius by rememberPreference(BlurRadiusKey, 48f)
    val playerCustomImageUri by rememberPreference(PlayerCustomImageUriKey, "")
    val playerCustomBlur by rememberPreference(PlayerCustomBlurKey, 0f)
    val playerCustomContrast by rememberPreference(PlayerCustomContrastKey, 1f)
    val playerCustomBrightness by rememberPreference(PlayerCustomBrightnessKey, 1f)
    val foregroundColor =
        if (lyricsBackground == LyricsBackgroundStyle.FOLLOW_THEME) {
            MaterialTheme.colorScheme.onSurface
        } else {
            Color.White
        }
    val showPlayerControlsState =
        rememberPreference(ShowLyricsPlayerControlsKey, true)
    val showPlayerControlsEnabled by showPlayerControlsState
    val (autoHidePlayerControls, onAutoHidePlayerControlsChange) =
        rememberPreference(AutoHideLyricsPlayerControlsKey, false)
    var playerControlsExpanded by remember(mediaMetadata.id, showPlayerControlsEnabled) {
        mutableStateOf(showPlayerControlsEnabled)
    }
    var playerControlsVisibilityTick by remember(mediaMetadata.id) {
        mutableIntStateOf(0)
    }
    val autoHideDelayMs = 5_000L
    val onShowPlayerControlsChange =
        remember(showPlayerControlsState) {
            { showControls: Boolean ->
                showPlayerControlsState.value = showControls
                playerControlsExpanded = showControls
            }
        }
    val onAutoHidePlayerControlsToggle: (Boolean) -> Unit = { enabled ->
        onAutoHidePlayerControlsChange(enabled)
        if (showPlayerControlsEnabled) {
            playerControlsExpanded = true
            playerControlsVisibilityTick++
        }
    }

    fun pokePlayerControlsVisibility() {
        if (!showPlayerControlsEnabled) return
        playerControlsExpanded = true
        if (autoHidePlayerControls) {
            playerControlsVisibilityTick++
        }
    }

    LaunchedEffect(showPlayerControlsEnabled) {
        playerControlsExpanded = showPlayerControlsEnabled
    }

    LaunchedEffect(autoHidePlayerControls, showPlayerControlsEnabled, playerControlsVisibilityTick, mediaMetadata.id) {
        if (!showPlayerControlsEnabled || !autoHidePlayerControls) return@LaunchedEffect
        playerControlsExpanded = true
        kotlinx.coroutines.delay(autoHideDelayMs)
        playerControlsExpanded = false
    }

    val hapticClick =
        remember(enableHapticFeedback, view) {
            {
                if (enableHapticFeedback) {
                    view.performHapticFeedback(
                        HapticFeedbackConstants.CONTEXT_CLICK,
                        HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
                    )
                }
            }
        }
    val lyricsHelper =
        remember(context) {
            EntryPointAccessors
                .fromApplication(
                    context.applicationContext,
                    moe.rukamori.archivetune.di.LyricsHelperEntryPoint::class.java,
                ).lyricsHelper()
        }

    LaunchedEffect(mediaMetadata.id, currentLyrics?.lyrics, currentLyrics?.providerName) {

        

        

        
        
        val snapshot = currentLyrics
        val needsFetch =
            snapshot == null ||
                snapshot.lyrics == LyricsEntity.LYRICS_NOT_FOUND ||
                snapshot.providerName.isBlank()
        if (!needsFetch) return@LaunchedEffect
        try {
            val existingLyrics =
                withContext(Dispatchers.IO) {
                    database.lyrics(mediaMetadata.id).first()
                }

            val hasValidLyrics =
                existingLyrics != null &&
                    existingLyrics.lyrics != LyricsEntity.LYRICS_NOT_FOUND
            if (hasValidLyrics && existingLyrics != null && existingLyrics.providerName.isNotBlank()) {
                return@LaunchedEffect
            }

            val lyricsResult =
                withContext(Dispatchers.IO) {
                    lyricsHelper.getLyricsWithProvider(mediaMetadata)
                }
            withContext(Dispatchers.IO) {
                database.query {
                    if (hasValidLyrics) {
                        backfillLyricsProviderName(
                            id = mediaMetadata.id,
                            providerName = lyricsResult.providerName,
                        )
                    } else {
                        replaceLyricsIfAbsentOrNotFound(
                            id = mediaMetadata.id,
                            lyrics = lyricsResult.lyrics,
                            providerName = lyricsResult.providerName,
                        )
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    val positionState = remember(mediaMetadata.id) { mutableLongStateOf(0L) }
    val durationState = remember(mediaMetadata.id) { mutableLongStateOf(C.TIME_UNSET) }
    var sliderPosition by remember(mediaMetadata.id) { mutableStateOf<Long?>(null) }

    var gradientColors by remember { mutableStateOf(AppleMusicFallbackGradient) }
    var hasValidPalette by remember { mutableStateOf(false) }

    val fallbackColor = remember { Color.Black.toArgb() }
    val darkTheme = isSystemInDarkTheme()

    LaunchedEffect(mediaMetadata.id, mediaMetadata.thumbnailUrl, lyricsBackground, darkTheme) {

        

        

        
        
        kotlinx.coroutines.delay(120)
        if (lyricsBackground != LyricsBackgroundStyle.DEFAULT &&
            lyricsBackground != LyricsBackgroundStyle.COLORING &&
            lyricsBackground != LyricsBackgroundStyle.MOVING_BLUR
        ) {
            gradientColors = AppleMusicFallbackGradient
            hasValidPalette = false
            return@LaunchedEffect
        }
        val thumbnailUrl = mediaMetadata.thumbnailUrl
        if (thumbnailUrl == null) {
            if (!hasValidPalette) gradientColors = AppleMusicFallbackGradient
            return@LaunchedEffect
        }

        val cacheKey =
            PlayerPaletteCacheKey(
                mediaId = mediaMetadata.id,
                provider = guessArtworkProvider(thumbnailUrl),
                artworkIdentity = thumbnailUrl,
                backgroundMode = lyricsBackground.name,
                darkTheme = darkTheme,
            )
        PlayerPaletteCache.get(cacheKey)?.let {
            gradientColors = it
            hasValidPalette = true
            return@LaunchedEffect
        }

        val request =
            ImageRequest
                .Builder(context)
                .data(thumbnailUrl)
                .size(Size(PlayerColorExtractor.Config.IMAGE_SIZE, PlayerColorExtractor.Config.IMAGE_SIZE))
                .allowHardware(false)
                .build()

        val extractedColors =
            try {
                val result =
                    withContext(Dispatchers.IO) {
                        context.imageLoader.execute(request)
                    }
                if (result !is SuccessResult) {
                    null
                } else {
                    val bitmap = result.image?.toBitmap()
                    if (bitmap == null) {
                        null
                    } else {
                        withContext(Dispatchers.Default) {
                            val palette =
                                Palette
                                    .from(bitmap)
                                    .maximumColorCount(PlayerColorExtractor.Config.MAX_COLOR_COUNT)
                                    .resizeBitmapArea(PlayerColorExtractor.Config.BITMAP_AREA)
                                    .generate()
                            PlayerColorExtractor.extractGradientColors(
                                palette = palette,
                                fallbackColor = fallbackColor,
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }

        
        if (extractedColors != null) {
            val stillCurrent =
                mediaMetadata.thumbnailUrl == thumbnailUrl
            if (stillCurrent) {
                PlayerPaletteCache.put(cacheKey, extractedColors)
                gradientColors = extractedColors
                hasValidPalette = true
            }
        } else if (!hasValidPalette) {
            gradientColors = AppleMusicFallbackGradient
        }
    }

    LaunchedEffect(player, playbackState, mediaMetadata.id) {
        if (playbackState != STATE_READY && playbackState != STATE_BUFFERING) return@LaunchedEffect
        while (isActive) {
            positionState.longValue = player.currentPosition.coerceAtLeast(0L)
            durationState.longValue = player.duration
            delay(250)
        }
    }

    val showLyricsMenu = {
        menuState.show {
            LyricsMenu(
                lyricsProvider = { currentLyrics },
                mediaMetadataProvider = { mediaMetadata },
                lyricsSyncOffset = lyricsSyncOffset,
                onLyricsSyncOffsetChange = onLyricsSyncOffsetChange,
                showPlayerControlsState = showPlayerControlsState,
                onShowPlayerControlsChange = onShowPlayerControlsChange,
                onAutoHidePlayerControlsChange = onAutoHidePlayerControlsToggle,
                onDismiss = menuState::dismiss,
            )
        }
    }

    val isLoading = playbackState == STATE_BUFFERING || sliderPosition != null
    val orientation = LocalConfiguration.current.orientation
    val controlsVisible = showPlayerControlsEnabled
    val controlsExpanded = showPlayerControlsEnabled && (!autoHidePlayerControls || playerControlsExpanded)
    val onControlsPositionChange: (Long) -> Unit = {
        pokePlayerControlsVisibility()
        sliderPosition = it
    }
    val onControlsPositionChangeFinished: () -> Unit = {
        pokePlayerControlsVisibility()
        sliderPosition?.let { targetPosition ->
            player.seekTo(targetPosition)
            positionState.longValue = targetPosition
        }
        sliderPosition = null
    }
    val onControlsVolumeChange: (Float) -> Unit = {
        pokePlayerControlsVisibility()
        onVolumeChange(it)
    }
    val onControlsPreviousClick = {
        pokePlayerControlsVisibility()
        hapticClick()
        playerConnection.seekToPrevious()
    }
    val onControlsPlayPauseClick = {
        pokePlayerControlsVisibility()
        hapticClick()
        player.togglePlayPause()
    }
    val onControlsNextClick = {
        pokePlayerControlsVisibility()
        hapticClick()
        playerConnection.seekToNext()
    }

    BackHandler(enabled = backHandlerEnabled, onBack = onBackClick)

    Box(
        modifier =
            modifier
                .fillMaxSize(),
    ) {
        LyricsScreenBackground(
            style = lyricsBackground,
            mediaMetadata = mediaMetadata,
            gradientColors = gradientColors,
            disableBlur = disableBlur,
            blurRadius = blurRadius,
            playerCustomImageUri = playerCustomImageUri,
            playerCustomBlur = playerCustomBlur,
            playerCustomContrast = playerCustomContrast,
            playerCustomBrightness = playerCustomBrightness,
        )

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .consumeUnhandledPointerInput()
                    .pointerInput(showPlayerControlsEnabled, autoHidePlayerControls) {
                        if (!showPlayerControlsEnabled || !autoHidePlayerControls) return@pointerInput
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            pokePlayerControlsVisibility()
                        }
                    },
        )

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars),
        ) {
            AppleMusicGrabber(onClick = onBackClick)
            AppleMusicTrackHeader(
                mediaMetadata = mediaMetadata,
                foregroundColor = foregroundColor,
                onMoreClick = showLyricsMenu,
                onDismissClick = onBackClick,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
            )

            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                AnimatedContent(
                    targetState = controlsVisible,
                    transitionSpec = {
                        fadeIn(tween(180)) togetherWith fadeOut(tween(140))
                    },
                    label = "lyrics-landscape-controls",
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                ) { controlsVisible ->
                    if (controlsVisible) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 36.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppleMusicLyricsPane(
                                lyricsMode = lyricsMode,
                                foregroundColor = foregroundColor,
                                sliderPositionProvider = { sliderPosition },
                                lyricsSyncOffset = lyricsSyncOffset,
                                modifier =
                                    Modifier
                                        .weight(1.15f)
                                        .fillMaxHeight()
                                        .padding(end = 32.dp),
                            )

                            Column(
                                modifier =
                                    Modifier
                                        .weight(0.85f)
                                        .widthIn(max = 420.dp),
                                verticalArrangement = Arrangement.Center,
                            ) {
                                AppleMusicControls(
                                    positionProvider = { positionState.longValue },
                                    durationProvider = { durationState.longValue },
                                    sliderPosition = sliderPosition,
                                    controlsExpanded = controlsExpanded,
                                    isPlaying = isPlaying,
                                    isLoading = isLoading,
                                    volume = deviceMusicVolumeController.volumeFraction,
                                    onPositionChange = onControlsPositionChange,
                                    onPositionChangeFinished = onControlsPositionChangeFinished,
                                    onVolumeChange = onControlsVolumeChange,
                                    onPreviousClick = onControlsPreviousClick,
                                    onPlayPauseClick = onControlsPlayPauseClick,
                                    onNextClick = onControlsNextClick,
                                    onControlsInteraction = { pokePlayerControlsVisibility() },
                                    foregroundColor = foregroundColor,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    } else {
                        AppleMusicLyricsPane(
                            lyricsMode = lyricsMode,
                            foregroundColor = foregroundColor,
                            sliderPositionProvider = { sliderPosition },
                            lyricsSyncOffset = lyricsSyncOffset,
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 36.dp, vertical = 8.dp),
                        )
                    }
                }
            } else {
                AppleMusicLyricsPane(
                    lyricsMode = lyricsMode,
                    foregroundColor = foregroundColor,
                    sliderPositionProvider = { sliderPosition },
                    lyricsSyncOffset = lyricsSyncOffset,
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                )

                AnimatedVisibility(
                    visible = controlsVisible,
                    enter =
                        fadeIn(tween(120)) +
                            slideInVertically(tween(180)) { fullHeight -> fullHeight / 6 },
                    exit =
                        fadeOut(tween(90)) +
                            slideOutVertically(tween(140)) { fullHeight -> fullHeight / 8 },
                    label = "lyrics-player-controls",
                ) {
                    AppleMusicControls(
                        positionProvider = { positionState.longValue },
                        durationProvider = { durationState.longValue },
                        sliderPosition = sliderPosition,
                        controlsExpanded = controlsExpanded,
                        isPlaying = isPlaying,
                        isLoading = isLoading,
                        volume = deviceMusicVolumeController.volumeFraction,
                        onPositionChange = onControlsPositionChange,
                        onPositionChangeFinished = onControlsPositionChangeFinished,
                        onVolumeChange = onControlsVolumeChange,
                        onPreviousClick = onControlsPreviousClick,
                        onPlayPauseClick = onControlsPlayPauseClick,
                        onNextClick = onControlsNextClick,
                        onControlsInteraction = { pokePlayerControlsVisibility() },
                        foregroundColor = foregroundColor,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 40.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricsScreenBackground(
    style: LyricsBackgroundStyle,
    mediaMetadata: MediaMetadata,
    gradientColors: List<Color>,
    disableBlur: Boolean,
    blurRadius: Float,
    playerCustomImageUri: String,
    playerCustomBlur: Float,
    playerCustomContrast: Float,
    playerCustomBrightness: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(
                    if (style == LyricsBackgroundStyle.FOLLOW_THEME) {
                        MaterialTheme.colorScheme.surface
                    } else {
                        Color.Black
                    },
                ),
    ) {
        when (style) {
            LyricsBackgroundStyle.DEFAULT -> {
                AppleMusicBackground(
                    mediaMetadata = mediaMetadata,
                    gradientColors = gradientColors,
                )
            }

            LyricsBackgroundStyle.MOVING_BLUR -> {
                MovingBlurBackground(
                    mediaMetadata = mediaMetadata,
                    gradientColors = gradientColors,
                )
            }

            LyricsBackgroundStyle.FOLLOW_THEME -> Unit

            LyricsBackgroundStyle.COLORING,
            LyricsBackgroundStyle.CUSTOM,
            -> {
                PlayerBackground(
                    playerBackground =
                        if (style == LyricsBackgroundStyle.CUSTOM) {
                            PlayerBackgroundStyle.CUSTOM
                        } else {
                            PlayerBackgroundStyle.COLORING
                        },
                    mediaMetadata = mediaMetadata,
                    gradientColors = gradientColors,
                    disableBlur = disableBlur,
                    blurRadius = blurRadius,
                    playerCustomImageUri = playerCustomImageUri,
                    playerCustomBlur = playerCustomBlur,
                    playerCustomContrast = playerCustomContrast,
                    playerCustomBrightness = playerCustomBrightness,
                )
            }
        }
    }
}

@Composable
private fun MovingBlurBackground(
    mediaMetadata: MediaMetadata,
    gradientColors: List<Color>,
    modifier: Modifier = Modifier,
) {
    val colors = if (gradientColors.isNotEmpty()) gradientColors else AppleMusicFallbackGradient

    val backgroundBrush =
        remember(colors) {
            Brush.verticalGradient(
                listOf(
                    colors.getOrElse(0) { AppleMusicFallbackGradient[0] }.copy(alpha = 0.42f),
                    colors.getOrElse(1) { AppleMusicFallbackGradient[1] }.copy(alpha = 0.34f),
                    colors.getOrElse(2) { AppleMusicFallbackGradient[2] }.copy(alpha = 0.54f),
                ),
            )
        }
    val bottomScrim =
        remember {
            Brush.verticalGradient(
                listOf(
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.32f),
                ),
            )
        }

    val transition = rememberInfiniteTransition(label = "moving-blur-drift")
    val driftX by transition.animateFloat(
        initialValue = -120f,
        targetValue = 120f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 19_000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "moving-blur-x",
    )
    val driftY by transition.animateFloat(
        initialValue = -90f,
        targetValue = 90f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 27_000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "moving-blur-y",
    )

    val context = LocalContext.current
    val imageLoader = context.imageLoader
    val isPreS = Build.VERSION.SDK_INT < Build.VERSION_CODES.S

    // Pre-Android S can't use Modifier.blur (it requires RenderEffect, API 31+). We use sang's
    // pure-Kotlin stack-blur fallback (rukamori/ArchiveTune#924): load the thumbnail, blur it
    // once with ImageBlurUtils, render via Image. The drift animation is preserved by applying
    // it through Modifier.offset(x = driftX.dp, y = driftY.dp) — the SAME mechanism the S+ path
    // uses. Earlier attempts tried graphicsLayer { translationX = driftX.dp.toPx() } instead,
    // but on pre-S devices the graphicsLayer block did not reliably re-evaluate each animation
    // frame, so the background appeared static even though driftX was animating. Modifier.offset
    // is a layout modifier that definitely invalidates layout when its Dp arguments change, so
    // the bitmap is re-placed every frame.
    //
    // The bitmap is scaled up well beyond the screen (preSDriftScale, computed below) so that
    // at max drift the scaled+drifted drawing still covers the screen with no black bars. The
    // parent BoxWithConstraints has clipToBounds() so the oversized drawing never spills. The
    // 48dp safety margin guarantees full coverage at max drift (the S+ path doesn't need this
    // because Modifier.blur's TileMode.Clamp extends the visible content ~64dp beyond the
    // layout bounds; pre-S has no such edge extension).
    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .clipToBounds()
                .background(AppleMusicFallbackGradient.last()),
    ) {
        val preSDriftScale =
            if (isPreS) {
                val driftMaxX = 120.dp
                val driftMaxY = 90.dp
                val safetyMargin = 48.dp
                val requiredScaleX = 1f + 2f * (driftMaxX.value + safetyMargin.value) / maxWidth.value
                val requiredScaleY = 1f + 2f * (driftMaxY.value + safetyMargin.value) / maxHeight.value
                maxOf(requiredScaleX, requiredScaleY, 1.4f)
            } else {
                1.4f
            }

        AnimatedContent(
            targetState = mediaMetadata.thumbnailUrl,
            transitionSpec = { fadeIn(tween(700)) togetherWith fadeOut(tween(700)) },
            label = "lyrics-moving-blur-bg",
        ) { thumbnailUrl ->
            if (thumbnailUrl != null) {
                if (isPreS) {
                    val blurredBitmap by produceState<Bitmap?>(null, thumbnailUrl) {
                        value = withContext(Dispatchers.IO) {
                            try {
                                val request = ImageRequest.Builder(context)
                                    .data(thumbnailUrl)
                                    .allowHardware(false)
                                    .memoryCacheKey(thumbnailUrl)
                                    .diskCacheKey(thumbnailUrl)
                                    .size(Size(720, 720))
                                    .build()
                                val result = imageLoader.execute(request)
                                if (result is SuccessResult) {
                                    val bitmap = result.image.toBitmap()
                                        .copy(Bitmap.Config.ARGB_8888, true)
                                    val density = context.resources.displayMetrics.density
                                    ImageBlurUtils.blur(bitmap, 64f * density)
                                } else null
                            } catch (_: Exception) {
                                null
                            }
                        }
                    }
                    blurredBitmap?.let { bm ->
                        Image(
                            bitmap = bm.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = preSDriftScale
                                    scaleY = preSDriftScale
                                }
                                .offset(x = driftX.dp, y = driftY.dp)
                                .alpha(0.86f),
                        )
                    }
                } else {
                    AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = 1.4f
                                scaleY = 1.4f
                            }
                            .blur(64.dp)
                            .offset(x = driftX.dp, y = driftY.dp)
                            .alpha(0.86f),
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bottomScrim),
        )
    }
}

@Composable
private fun AppleMusicBackground(
    mediaMetadata: MediaMetadata,
    gradientColors: List<Color>,
    modifier: Modifier = Modifier,
) {
    val colors = if (gradientColors.isNotEmpty()) gradientColors else AppleMusicFallbackGradient
    val backgroundBrush =
        remember(colors) {
            Brush.verticalGradient(
                listOf(
                    colors.getOrElse(0) { AppleMusicFallbackGradient[0] }.copy(alpha = 0.88f),
                    colors.getOrElse(1) { AppleMusicFallbackGradient[1] }.copy(alpha = 0.76f),
                    colors.getOrElse(2) { AppleMusicFallbackGradient[2] }.copy(alpha = 0.96f),
                ),
            )
        }
    val bottomScrim =
        remember {
            Brush.verticalGradient(
                listOf(
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.28f),
                ),
            )
        }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(AppleMusicFallbackGradient.last()),
    ) {
        val context = LocalContext.current
        val imageLoader = context.imageLoader
        val isPreS = Build.VERSION.SDK_INT < Build.VERSION_CODES.S
        AnimatedContent(
            targetState = mediaMetadata.thumbnailUrl,
            transitionSpec = { fadeIn(tween(700)) togetherWith fadeOut(tween(700)) },
            label = "lyrics-apple-background",
        ) { thumbnailUrl ->
            if (thumbnailUrl != null) {
                if (isPreS) {

                    
                    
                    val blurredBitmap by produceState<Bitmap?>(null, thumbnailUrl) {
                        value = withContext(Dispatchers.IO) {
                            try {
                                val request = ImageRequest.Builder(context)
                                    .data(thumbnailUrl)
                                    .allowHardware(false)
                                    .memoryCacheKey("$thumbnailUrl#lyricsbg")
                                    .diskCacheKey("$thumbnailUrl#lyricsbg")
                                    .size(Size(720, 720))
                                    .build()
                                val result = imageLoader.execute(request)
                                if (result is SuccessResult) {
                                    val bitmap = result.image.toBitmap()
                                        .copy(Bitmap.Config.ARGB_8888, true)
                                    val density = context.resources.displayMetrics.density
                                    ImageBlurUtils.blur(bitmap, 46f * density)
                                } else null
                            } catch (_: Exception) {
                                null
                            }
                        }
                    }
                    blurredBitmap?.let { bm ->
                        Image(
                            bitmap = bm.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .alpha(0.62f),
                        )
                    }
                } else {
                    AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .blur(46.dp)
                                .alpha(0.62f),
                    )
                }
            }
        }
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(backgroundBrush),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.18f)),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(bottomScrim),
        )
    }
}

@Composable
private fun AppleMusicGrabber(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val closeDescription = stringResource(R.string.close)
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(44.dp)
                .semantics { contentDescription = closeDescription }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                ),
    )
}

@Composable
private fun AppleMusicTrackHeader(
    mediaMetadata: MediaMetadata,
    foregroundColor: Color,
    onMoreClick: () -> Unit,
    onDismissClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val artistText =
        remember(mediaMetadata.id, mediaMetadata.artists) {
            mediaMetadata.artists.joinToString { it.name }
        }

    Row(
        modifier = modifier.heightIn(min = 64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(foregroundColor.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = mediaMetadata.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (mediaMetadata.thumbnailUrl == null) {
                Icon(
                    painter = painterResource(R.drawable.player_music_note),
                    contentDescription = null,
                    tint = foregroundColor.copy(alpha = 0.72f),
                    modifier = Modifier.size(26.dp),
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = mediaMetadata.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = foregroundColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = artistText,
                style = MaterialTheme.typography.bodyLarge,
                color = foregroundColor.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        AppleMusicHeaderIconButton(
            iconRes = R.drawable.player_close,
            contentDescription = stringResource(R.string.close),
            foregroundColor = foregroundColor,
            onClick = onDismissClick,
        )

        Spacer(modifier = Modifier.width(4.dp))

        AppleMusicHeaderIconButton(
            iconRes = R.drawable.player_more_horiz,
            contentDescription = stringResource(R.string.more_options),
            foregroundColor = foregroundColor,
            onClick = onMoreClick,
        )
    }
}

@Composable
private fun AppleMusicHeaderIconButton(
    iconRes: Int,
    contentDescription: String,
    foregroundColor: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(48.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false, radius = 24.dp),
                    role = Role.Button,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(foregroundColor.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = contentDescription,
                tint = foregroundColor,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun AppleMusicLyricsPane(
    lyricsMode: LyricsMode,
    foregroundColor: Color,
    sliderPositionProvider: () -> Long?,
    lyricsSyncOffset: Int,
    modifier: Modifier = Modifier,
) {
    LyricsContent(
        lyricsMode = lyricsMode,
        sliderPositionProvider = sliderPositionProvider,
        lyricsSyncOffset = lyricsSyncOffset,
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
        textColor = foregroundColor,
    )
}

@Composable
private fun AppleMusicControls(
    positionProvider: () -> Long,
    durationProvider: () -> Long,
    sliderPosition: Long?,
    controlsExpanded: Boolean,
    isPlaying: Boolean,
    isLoading: Boolean,
    volume: Float,
    onPositionChange: (Long) -> Unit,
    onPositionChangeFinished: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onPreviousClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onControlsInteraction: () -> Unit,
    foregroundColor: Color,
    modifier: Modifier = Modifier,
) {
    val position = positionProvider()
    val duration = durationProvider()
    val hasDuration = duration != C.TIME_UNSET && duration > 0L
    val safeDuration = if (hasDuration) duration else 1L
    val currentPosition = (sliderPosition ?: position).coerceIn(0L, safeDuration)
    val remainingPosition = (safeDuration - currentPosition).coerceAtLeast(0L)

    Column(
        modifier =
            modifier
                .offset(y = (-6).dp)
                .pointerInput(controlsExpanded) {
                    if (controlsExpanded) return@pointerInput
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        onControlsInteraction()
                    }
                },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppleMusicSlider(
            value = currentPosition.toFloat(),
            valueRange = 0f..safeDuration.toFloat(),
            activeColor = foregroundColor.copy(alpha = 0.94f),
            inactiveColor = foregroundColor.copy(alpha = 0.28f),
            trackHeight = 8.dp,
            onValueChange = { onPositionChange(it.toLong()) },
            onValueChangeFinished = onPositionChangeFinished,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = makeTimeString(currentPosition),
                style = MaterialTheme.typography.labelMedium,
                color = foregroundColor.copy(alpha = 0.54f),
            )
            Text(
                text = if (hasDuration) "-${makeTimeString(remainingPosition)}" else "",
                style = MaterialTheme.typography.labelMedium,
                color = foregroundColor.copy(alpha = 0.54f),
            )
        }

        AnimatedVisibility(
            visible = controlsExpanded,
            enter = fadeIn(tween(120)) + slideInVertically(tween(160)) { fullHeight -> fullHeight / 8 },
            exit = fadeOut(tween(90)) + slideOutVertically(tween(120)) { fullHeight -> fullHeight / 10 },
            label = "lyrics-expanded-player-controls",
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 15.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 26.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppleMusicTransportButton(
                        iconRes = R.drawable.player_fast_forward,
                        contentDescription = stringResource(R.string.widget_previous),
                        iconSize = 44.dp,
                        touchSize = 68.dp,
                        foregroundColor = foregroundColor,
                        mirrored = true,
                        onClick = onPreviousClick,
                    )
                    IconButton(
                        onClick = onPlayPauseClick,
                        modifier = Modifier.size(74.dp),
                    ) {
                        if (isLoading) {
                            CircularWavyProgressIndicator(
                                modifier = Modifier.size(42.dp),
                                color = foregroundColor,
                            )
                        } else {
                            Icon(
                                painter = painterResource(if (isPlaying) R.drawable.player_pause else R.drawable.player_play),
                                contentDescription =
                                    if (isPlaying) {
                                        stringResource(R.string.widget_pause)
                                    } else {
                                        stringResource(R.string.play)
                                    },
                                tint = foregroundColor,
                                modifier = Modifier.size(54.dp),
                            )
                        }
                    }
                    AppleMusicTransportButton(
                        iconRes = R.drawable.player_fast_forward,
                        contentDescription = stringResource(R.string.next),
                        iconSize = 44.dp,
                        touchSize = 68.dp,
                        foregroundColor = foregroundColor,
                        mirrored = false,
                        onClick = onNextClick,
                    )
                }

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 26.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.player_volume_min),
                        contentDescription = stringResource(R.string.minimum_volume),
                        tint = foregroundColor.copy(alpha = 0.66f),
                        modifier = Modifier.size(17.dp),
                    )
                    AppleMusicSlider(
                        value = volume.coerceIn(0f, 1f),
                        valueRange = 0f..1f,
                        activeColor = foregroundColor.copy(alpha = 0.88f),
                        inactiveColor = foregroundColor.copy(alpha = 0.24f),
                        trackHeight = 8.dp,
                        onValueChange = onVolumeChange,
                        onValueChangeFinished = {},
                        modifier =
                            Modifier
                                .weight(1f)
                                .padding(horizontal = 16.dp),
                    )
                    Icon(
                        painter = painterResource(R.drawable.player_volume_up),
                        contentDescription = stringResource(R.string.maximum_volume),
                        tint = foregroundColor.copy(alpha = 0.66f),
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AppleMusicTransportButton(
    iconRes: Int,
    contentDescription: String?,
    iconSize: Dp,
    touchSize: Dp,
    foregroundColor: Color,
    mirrored: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(touchSize),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = foregroundColor,
            modifier =
                Modifier
                    .size(iconSize)
                    .graphicsLayer { if (mirrored) scaleX = -1f },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppleMusicSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    activeColor: Color,
    inactiveColor: Color,
    trackHeight: Dp,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val safeStart = valueRange.start
    val safeEnd = valueRange.endInclusive.coerceAtLeast(safeStart + 1f)
    val safeRange = safeStart..safeEnd
    val sliderColors =
        SliderDefaults.colors(
            activeTrackColor = activeColor,
            activeTickColor = activeColor,
            thumbColor = Color.Transparent,
            inactiveTrackColor = inactiveColor,
        )

    Slider(
        value = value.coerceIn(safeRange),
        valueRange = safeRange,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        colors = sliderColors,
        thumb = { Spacer(modifier = Modifier.size(0.dp)) },
        track = { sliderState ->
            PlayerSliderTrack(
                sliderState = sliderState,
                colors = sliderColors,
                trackHeight = trackHeight,
            )
        },
        modifier = modifier.height(28.dp),
    )
}

@Composable
private fun LyricsContent(
    lyricsMode: LyricsMode,
    sliderPositionProvider: () -> Long?,
    lyricsSyncOffset: Int,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    when (lyricsMode) {
        LyricsMode.V2 -> {
            LyricsV2(
                sliderPositionProvider = sliderPositionProvider,
                lyricsSyncOffset = lyricsSyncOffset,
                modifier = modifier,
                textColorOverride = textColor,
            )
        }

        LyricsMode.ENHANCED -> {
            LyricsEnhanced(
                sliderPositionProvider = sliderPositionProvider,
                lyricsSyncOffset = lyricsSyncOffset,
                modifier = modifier,
                textColorOverride = textColor,
            )
        }
    }
}
