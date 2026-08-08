/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.screens.settings

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.HideCachedCardKey
import moe.rukamori.archivetune.constants.HideLikedSongsCardKey
import moe.rukamori.archivetune.constants.HideLocalFilesCardKey
import moe.rukamori.archivetune.constants.HideOfflineCardKey
import moe.rukamori.archivetune.constants.HideTop50CardKey
import moe.rukamori.archivetune.constants.ShowHomeCategoryChipsKey
import moe.rukamori.archivetune.constants.ShowTagsInLibraryKey
import moe.rukamori.archivetune.ui.component.FrostedTopAppBar
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.rememberPreference

@Composable
fun AppearanceExtrasSettings(navController: NavController) {
    val (showHomeCategoryChips, onShowHomeCategoryChipsChange) =
        rememberPreference(ShowHomeCategoryChipsKey, defaultValue = false)
    val (showTagsInLibrary, onShowTagsInLibraryChange) =
        rememberPreference(ShowTagsInLibraryKey, defaultValue = false)
    val (hideLikedSongsCard, onHideLikedSongsCardChange) =
        rememberPreference(HideLikedSongsCardKey, defaultValue = false)
    val (hideOfflineCard, onHideOfflineCardChange) =
        rememberPreference(HideOfflineCardKey, defaultValue = false)
    val (hideCachedCard, onHideCachedCardChange) =
        rememberPreference(HideCachedCardKey, defaultValue = false)
    val (hideLocalFilesCard, onHideLocalFilesCardChange) =
        rememberPreference(HideLocalFilesCardKey, defaultValue = false)
    val (hideTop50Card, onHideTop50CardChange) =
        rememberPreference(HideTop50CardKey, defaultValue = false)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            FrostedTopAppBar(
                titleRes = R.string.extras,
                onBack = navController::navigateUp,
                onBackLongClick = navController::backToMain,
            )
        },
    ) { innerPadding ->
        val topPadding = innerPadding.calculateTopPadding()
        val scrollState = rememberScrollState()

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
            PreferenceGroup(title = stringResource(R.string.extras)) {
                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.show_home_category_chips)) },
                        description = stringResource(R.string.show_home_category_chips_desc),
                        icon = { Icon(painterResource(R.drawable.home_outlined), null) },
                        checked = showHomeCategoryChips,
                        onCheckedChange = onShowHomeCategoryChipsChange,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.show_tags_in_library)) },
                        description = stringResource(R.string.show_tags_in_library_desc),
                        icon = { Icon(painterResource(R.drawable.filter_alt), null) },
                        checked = showTagsInLibrary,
                        onCheckedChange = onShowTagsInLibraryChange,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.hide_liked_songs_card)) },
                        description = stringResource(R.string.hide_liked_songs_card_desc),
                        icon = { Icon(painterResource(R.drawable.favorite), null) },
                        checked = hideLikedSongsCard,
                        onCheckedChange = onHideLikedSongsCardChange,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.hide_offline_card)) },
                        description = stringResource(R.string.hide_offline_card_desc),
                        icon = { Icon(painterResource(R.drawable.offline), null) },
                        checked = hideOfflineCard,
                        onCheckedChange = onHideOfflineCardChange,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.hide_cached_card)) },
                        description = stringResource(R.string.hide_cached_card_desc),
                        icon = { Icon(painterResource(R.drawable.cached), null) },
                        checked = hideCachedCard,
                        onCheckedChange = onHideCachedCardChange,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.hide_local_files_card)) },
                        description = stringResource(R.string.hide_local_files_card_desc),
                        icon = { Icon(painterResource(R.drawable.snippet_folder), null) },
                        checked = hideLocalFilesCard,
                        onCheckedChange = onHideLocalFilesCardChange,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.hide_top50_card)) },
                        description = stringResource(R.string.hide_top50_card_desc),
                        icon = { Icon(painterResource(R.drawable.trending_up), null) },
                        checked = hideTop50Card,
                        onCheckedChange = onHideTop50CardChange,
                    )
                }
            }
        }
    }
}
