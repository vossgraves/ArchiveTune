/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.screens.settings

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.HideNavigationBarLabelsKey
import moe.rukamori.archivetune.constants.NAVIGATION_BAR_CORNER_RADIUS_DEFAULT
import moe.rukamori.archivetune.constants.NAVIGATION_BAR_HEIGHT_DEFAULT
import moe.rukamori.archivetune.constants.NAVIGATION_BAR_LABEL_SPACING_DEFAULT
import moe.rukamori.archivetune.constants.NAVIGATION_BAR_OPACITY_DEFAULT
import moe.rukamori.archivetune.constants.NAVIGATION_BAR_TRANSPARENCY_DEFAULT
import moe.rukamori.archivetune.constants.NAVIGATION_BAR_WIDTH_DEFAULT
import moe.rukamori.archivetune.constants.NavigationBarCornerRadiusKey
import moe.rukamori.archivetune.constants.NavigationBarFrostedBlurKey
import moe.rukamori.archivetune.constants.NavigationBarHeightKey
import moe.rukamori.archivetune.constants.NavigationBarLabelSpacingKey
import moe.rukamori.archivetune.constants.NavigationBarOpacityKey
import moe.rukamori.archivetune.constants.NavigationBarStyle
import moe.rukamori.archivetune.constants.NavigationBarStyleKey
import moe.rukamori.archivetune.constants.NavigationBarTransparencyKey
import moe.rukamori.archivetune.constants.NavigationBarWidthKey
import moe.rukamori.archivetune.ui.component.DefaultDialog
import moe.rukamori.archivetune.ui.component.EnumListPreference
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import kotlin.math.roundToInt

@Composable
fun NavigationBarSettings(navController: NavController, scrollTo: String? = null) {
    val (navigationBarStyle, onNavigationBarStyleChange) =
        rememberEnumPreference(
            NavigationBarStyleKey,
            defaultValue = NavigationBarStyle.DEFAULT,
        )
    val (navigationBarFrostedBlur, onNavigationBarFrostedBlurChange) =
        rememberPreference(NavigationBarFrostedBlurKey, defaultValue = false)
    val (hideNavigationBarLabels, onHideNavigationBarLabelsChange) =
        rememberPreference(HideNavigationBarLabelsKey, defaultValue = false)

    // Customization sliders (Task 6). Defaults are the constants defined alongside
    // their preference keys so the pre-existing look is preserved.
    val (navigationBarWidth, onNavigationBarWidthChange) =
        rememberPreference(NavigationBarWidthKey, defaultValue = NAVIGATION_BAR_WIDTH_DEFAULT)
    val (navigationBarHeight, onNavigationBarHeightChange) =
        rememberPreference(NavigationBarHeightKey, defaultValue = NAVIGATION_BAR_HEIGHT_DEFAULT)
    val (navigationBarOpacity, onNavigationBarOpacityChange) =
        rememberPreference(NavigationBarOpacityKey, defaultValue = NAVIGATION_BAR_OPACITY_DEFAULT)
    val (navigationBarTransparency, onNavigationBarTransparencyChange) =
        rememberPreference(
            NavigationBarTransparencyKey,
            defaultValue = NAVIGATION_BAR_TRANSPARENCY_DEFAULT,
        )
    val (navigationBarLabelSpacing, onNavigationBarLabelSpacingChange) =
        rememberPreference(
            NavigationBarLabelSpacingKey,
            defaultValue = NAVIGATION_BAR_LABEL_SPACING_DEFAULT,
        )
    val (navigationBarCornerRadius, onNavigationBarCornerRadiusChange) =
        rememberPreference(
            NavigationBarCornerRadiusKey,
            defaultValue = NAVIGATION_BAR_CORNER_RADIUS_DEFAULT,
        )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.navigation_bar_settings_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(
                            painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        val topPadding = innerPadding.calculateTopPadding()
        val scrollState = rememberScrollState()
        val positions = rememberPreferencePositions()

        LaunchedEffect(scrollTo) { positions.scrollToKey(scrollTo, scrollState) }

        Column(
            Modifier
                .padding(top = topPadding)
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                ).verticalScroll(scrollState)
                .padding(bottom = SettingsDimensions.ScreenBottomPadding),
        ) {
            PreferenceGroup(title = stringResource(R.string.general)) {
                item {
                    Column(modifier = positions.modifierFor("navigation_bar_style")) {
                        EnumListPreference(
                            title = { Text(stringResource(R.string.navigation_bar_style)) },
                            icon = { Icon(painterResource(R.drawable.nav_bar), null) },
                            selectedValue = navigationBarStyle,
                            onValueSelected = onNavigationBarStyleChange,
                            valueText = {
                                when (it) {
                                    NavigationBarStyle.DEFAULT ->
                                        stringResource(R.string.navigation_bar_style_default)
                                    NavigationBarStyle.FLOATING ->
                                        stringResource(R.string.navigation_bar_style_floating)
                                }
                            },
                        )
                    }
                }

                item {
                    Column {
                        SwitchPreference(
                            title = { Text(stringResource(R.string.navigation_bar_frosted_blur)) },
                            description = stringResource(R.string.navigation_bar_frosted_blur_desc),
                            icon = { Icon(painterResource(R.drawable.blur_on), null) },
                            checked = navigationBarFrostedBlur,
                            onCheckedChange = onNavigationBarFrostedBlurChange,
                        )
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && navigationBarFrostedBlur) {
                            Text(
                                text = stringResource(R.string.navigation_bar_frosted_blur_unsupported),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 56.dp, top = 4.dp, end = 16.dp),
                            )
                        }
                    }
                }

                item {
                    SwitchPreference(
                        modifier = positions.modifierFor("hide_navigation_bar_labels"),
                        title = { Text(stringResource(R.string.hide_navigation_bar_labels)) },
                        description = stringResource(R.string.hide_navigation_bar_labels_desc),
                        icon = { Icon(painterResource(R.drawable.nav_bar), null) },
                        checked = hideNavigationBarLabels,
                        onCheckedChange = onHideNavigationBarLabelsChange,
                    )
                }
            }

            // Customization sliders: only meaningfully affect the FLOATING style (and the
            // corner radius for DEFAULT). They are shown unconditionally so the user can
            // pre-configure the floating look before switching to it.
            PreferenceGroup(title = stringResource(R.string.navigation_bar_dimensions)) {
                item {
                    SliderPreferenceRow(
                        title = stringResource(R.string.navigation_bar_width),
                        description = stringResource(R.string.navigation_bar_width_desc),
                        iconRes = R.drawable.tune,
                        value = navigationBarWidth,
                        onValueChange = onNavigationBarWidthChange,
                        range = 0.5f..1.0f,
                        valueLabel = { "${(it * 100).roundToInt()}%" },
                    )
                }

                item {
                    SliderPreferenceRow(
                        title = stringResource(R.string.navigation_bar_height),
                        description = stringResource(R.string.navigation_bar_height_desc),
                        iconRes = R.drawable.tune,
                        value = navigationBarHeight,
                        onValueChange = onNavigationBarHeightChange,
                        range = 0.8f..1.4f,
                        valueLabel = { "${(it * 100).roundToInt()}%" },
                    )
                }

                item {
                    SliderPreferenceRow(
                        title = stringResource(R.string.navigation_bar_opacity),
                        description = stringResource(R.string.navigation_bar_opacity_desc),
                        iconRes = R.drawable.tune,
                        value = navigationBarOpacity,
                        onValueChange = onNavigationBarOpacityChange,
                        range = 0.2f..1.0f,
                        valueLabel = { "${(it * 100).roundToInt()}%" },
                    )
                }

                item {
                    SliderPreferenceRow(
                        title = stringResource(R.string.navigation_bar_transparency),
                        description = stringResource(R.string.navigation_bar_transparency_desc),
                        iconRes = R.drawable.tune,
                        value = navigationBarTransparency,
                        onValueChange = onNavigationBarTransparencyChange,
                        range = 0.0f..0.95f,
                        valueLabel = { "${(it * 100).roundToInt()}%" },
                    )
                }

                item {
                    SliderPreferenceRow(
                        title = stringResource(R.string.navigation_bar_label_spacing),
                        description = stringResource(R.string.navigation_bar_label_spacing_desc),
                        iconRes = R.drawable.tune,
                        value = navigationBarLabelSpacing,
                        onValueChange = onNavigationBarLabelSpacingChange,
                        range = 0f..16f,
                        valueLabel = { "${it.roundToInt()} dp" },
                    )
                }

                item {
                    SliderPreferenceRow(
                        title = stringResource(R.string.navigation_bar_corner_radius),
                        description = stringResource(R.string.navigation_bar_corner_radius_desc),
                        iconRes = R.drawable.tune,
                        value = navigationBarCornerRadius,
                        onValueChange = onNavigationBarCornerRadiusChange,
                        range = 0f..48f,
                        valueLabel = { "${it.roundToInt()} dp" },
                    )
                }
            }
        }
    }
}

/**
 * A preference row that opens a slider dialog when tapped. Mirrors the swipe-sensitivity
 * UX used in PlayerSettings / AppearanceSettings so all float-valued tuning knobs share
 * the same interaction model.
 */
@Composable
private fun SliderPreferenceRow(
    title: String,
    description: String,
    iconRes: Int,
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    valueLabel: (Float) -> String,
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }

    if (showDialog) {
        var tempValue by remember { mutableFloatStateOf(value) }

        DefaultDialog(
            onDismiss = {
                tempValue = value
                showDialog = false
            },
            buttons = {
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    onClick = {
                        tempValue = value
                        showDialog = false
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
                TextButton(
                    onClick = {
                        onValueChange(tempValue)
                        showDialog = false
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp),
                )

                Text(
                    text = valueLabel(tempValue),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 16.dp),
                )

                Slider(
                    value = tempValue,
                    onValueChange = { tempValue = it },
                    valueRange = range,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (description.isNotBlank()) {
                    Spacer(modifier = Modifier.padding(top = 12.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    PreferenceEntry(
        title = { Text(title) },
        description = valueLabel(value),
        icon = { Icon(painterResource(iconRes), null) },
        onClick = { showDialog = true },
    )
}
