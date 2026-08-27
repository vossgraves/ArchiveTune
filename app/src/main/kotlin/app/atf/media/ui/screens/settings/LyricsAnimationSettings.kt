/*
 * ArchiveTune (2026)
 * © ArchiveTuneFork contributors — github.com/vossgraves/ArchiveTune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package app.atf.media.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import app.atf.media.LocalPlayerAwareWindowInsets
import app.atf.media.R
import app.atf.media.constants.LyricsV2BounceFactorKey
import app.atf.media.constants.LyricsV2FillTransitionWidthKey
import app.atf.media.constants.LyricsV2GlowFactorKey
import app.atf.media.constants.LyricsV2LrcBounceEnabledKey
import app.atf.media.ui.component.IconButton
import app.atf.media.ui.component.PreferenceEntry
import app.atf.media.ui.component.PreferenceGroup
import app.atf.media.ui.utils.backToMain
import app.atf.media.utils.rememberPreference
import androidx.compose.foundation.layout.asPaddingValues

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsAnimationSettings(
    navController: NavController,
    scrollTo: String? = null,
) {
    val (bounceFactor, onBounceFactorChange) = rememberPreference(LyricsV2BounceFactorKey, defaultValue = 1f)
    val (glowFactor, onGlowFactorChange) = rememberPreference(LyricsV2GlowFactorKey, defaultValue = 1f)
    val (fillTransitionWidth, onFillTransitionWidthChange) = rememberPreference(LyricsV2FillTransitionWidthKey, defaultValue = 8f)
    val (lrcBounceEnabled, onLrcBounceEnabledChange) = rememberPreference(LyricsV2LrcBounceEnabledKey, defaultValue = true)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.lyrics_animation_style)) },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        val playerAwareBottomPadding =
            LocalPlayerAwareWindowInsets.current
                .only(WindowInsetsSides.Bottom)
                .asPaddingValues()
                .calculateBottomPadding()
        val topPadding = innerPadding.calculateTopPadding()
        val scrollState = rememberScrollState()
        val positions = rememberPreferencePositions()
        androidx.compose.runtime.LaunchedEffect(scrollTo) { positions.scrollToKey(scrollTo, scrollState) }

        Column(
            modifier =
                Modifier
                    .padding(top = topPadding)
                    // Chained before verticalScroll so it measures the viewport, not the scrolling content.
                    .then(positions.containerModifier())
                    .verticalScroll(scrollState)
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(
                            WindowInsetsSides.Horizontal,
                        ),
                    ).padding(bottom = playerAwareBottomPadding + SettingsDimensions.ScreenBottomPadding),
        ) {
            PreferenceGroup(title = "Animation Tuning") {
                item {
                    PreferenceEntry(
                        modifier = positions.modifierFor("lyrics_animation_style"),
                        title = { Text("Line Bounce Effect") },
                        description = "Enable bounce animation for line-synced (LRC) lyrics",
                        icon = { Icon(painterResource(R.drawable.animation), null) },
                        trailingContent = {
                            Switch(
                                checked = lrcBounceEnabled,
                                onCheckedChange = onLrcBounceEnabledChange,
                            )
                        },
                    )
                }

                item {
                    PreferenceEntry(
                        modifier = positions.modifierFor("lyrics_scale_animation"),
                        title = { Text("Bounce Amplitude") },
                        description = "Adjust the bounce effect when a word is sung (${(bounceFactor * 100).toInt()}%)",
                        icon = { Icon(painterResource(R.drawable.animation), null) },
                        content = {
                            Slider(
                                value = bounceFactor,
                                onValueChange = onBounceFactorChange,
                                valueRange = 0f..2f,
                            )
                        },
                    )
                }

                item {
                    PreferenceEntry(
                        modifier = positions.modifierFor("lyrics_glow_animation"),
                        title = { Text("Glow Intensity") },
                        description = "Adjust the glow brightness of the sung word (${(glowFactor * 100).toInt()}%)",
                        icon = { Icon(painterResource(R.drawable.lyrics), null) },
                        content = {
                            Slider(
                                value = glowFactor,
                                onValueChange = onGlowFactorChange,
                                valueRange = 0f..2f,
                            )
                        },
                    )
                }

                item {
                    PreferenceEntry(
                        modifier = positions.modifierFor("lyrics_fade_animation"),
                        title = { Text("Fill Transition Smoothness") },
                        description = "Adjust the gradient edge width of the liquid fill effect (${fillTransitionWidth.toInt()} dp)",
                        icon = { Icon(painterResource(R.drawable.lyrics), null) },
                        content = {
                            Slider(
                                value = fillTransitionWidth,
                                onValueChange = onFillTransitionWidthChange,
                                valueRange = 2f..24f,
                            )
                        },
                    )
                }
            }
        }
    }
}
