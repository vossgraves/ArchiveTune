/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import kotlinx.coroutines.FlowPreview
import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.LocalSettingsDialogShowing
import moe.rukamori.archivetune.ui.component.rememberSettingsDialogHostState
import moe.rukamori.archivetune.ui.utils.appBarScrollBehavior
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.Updater


private fun searchableSettingsRoute(parentKey: String, scrollKey: String?): String? {
    val route =
        when (parentKey) {
            "account" -> "settings/account"
            "appearance" -> "settings/appearance"
            "appearance_extras" -> "settings/appearance/extras"
            "aod" -> "settings/appearance/aod_customized"
            "navigation_bar" -> "settings/appearance/navigation_bar"
            "lyrics_animations" -> "settings/appearance/lyrics_animations"
            "playback" -> "settings/player"
            "ytdlp" -> "settings/player/ytdlp"
            "sources" -> "settings/sources"
            "jiosaavn" -> "settings/jiosaavn"
            "deezer" -> "settings/deezer"
            "lyrics" -> "settings/lyrics"
            "lyrics_providers" -> "settings/lyrics/providers"
            "lyrics_romanisation" -> "settings/lyrics/romanisation"
            "content" -> "settings/content"
            "behavior" -> "settings/privacy"
            "integration" -> "settings/integration"
            "internet" -> "settings/internet"
            "storage" -> "settings/storage"
            "backup_restore" -> "settings/backup_restore"
            "developer_options" -> "settings/misc"
            "logcat" -> "settings/logcat"
            "music_together" -> "settings/music_together"
            "about" -> "settings/about"
            "discord" -> "settings/discord"
            "discord_experimental" -> "settings/discord/experimental"
            "tidal" -> "settings/tidal"
            "qobuz" -> "settings/qobuz"
            "telegram" -> "settings/telegram"
            "lastfm" -> "settings/lastfm"
            "ai_integration" -> "settings/ai_integration"
            "language_packs" -> "settings/language_packs"
            "po_token" -> PO_TOKEN_ROUTE
            else -> return null
        }
    // About / developer-options screens intentionally don't participate in auto-scroll
    // (their contents are mostly static links). All other screens honor ?scrollTo=.
    val supportsScroll =
        parentKey !in
            setOf(
                "developer_options",
                "about",
                "po_token",
                "account",
                "logcat",
                "music_together",
            )
    return if (!supportsScroll || scrollKey.isNullOrBlank()) route else "$route?scrollTo=$scrollKey"
}

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    latestVersionName: String,
    onClearUpdateBadge: () -> Unit = {},
) {
    val context = LocalContext.current
    val isAndroid12OrLater = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val listState = rememberLazyListState()

    val storagePermission =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    val notificationPermission =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.POST_NOTIFICATIONS
        } else {
            null
        }

    var isStorageGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, storagePermission) == PackageManager.PERMISSION_GRANTED,
        )
    }

    var isNotificationGranted by remember {
        mutableStateOf(
            notificationPermission == null ||
                ContextCompat.checkSelfPermission(context, notificationPermission) == PackageManager.PERMISSION_GRANTED,
        )
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { result ->
            isStorageGranted = result[storagePermission] == true || isStorageGranted
            if (notificationPermission != null) {
                isNotificationGranted = result[notificationPermission] == true || isNotificationGranted
            }
        }

    var searchQuery by remember { mutableStateOf("") }
    val scrollBehavior = appBarScrollBehavior()
    val shouldShowPermissionHint = !isStorageGranted || !isNotificationGranted
    val hasUpdate =
        BuildConfig.UPDATER_AVAILABLE &&
            Updater.isUpdateAvailable(latestVersionName, BuildConfig.VERSION_NAME)
    var isUpdateDismissed by remember { mutableStateOf(false) }
    val allSettingsGroups = buildSettingsGroups(navController, isAndroid12OrLater, hasUpdate, context)
    // When searching, flatten all individual SettingsChildren across every
    // category so each matching setting is shown as a separate row.
    //
    // Per product decision: settings that ship with an inline switch control
    // (boolean toggles like Dynamic theme, Pure black, Low data mode, Crossfade,
    // Persistent queue, etc.) ARE included in search results — the switch is
    // rendered inline so the user can toggle directly from the results.
    // Switchless settings navigate to the parent screen and auto-scroll to
    // the setting's position when tapped.
    //
    // The matching itself lives in [SettingsSearch] — see that file for why
    // multi-word queries used to return nothing.
    val filteredChildResults = remember(searchQuery, allSettingsGroups) {
        if (searchQuery.isBlank()) {
            emptyList()
        } else {
            SettingsSearch.search(
                groups = allSettingsGroups,
                rawQuery = searchQuery,
                routeFor = { parentKey, scrollKey -> searchableSettingsRoute(parentKey, scrollKey) },
            )
        }
    }
    val filteredGroups = remember(allSettingsGroups) {
        allSettingsGroups.map { group ->
            group.copy(items = group.items.filterNot(SettingsItem::hidden))
        }.filter { it.items.isNotEmpty() }
    }

    // Material 3 Expressive: when any settings dialog (history duration,
    // lyrics preload count, etc.) is showing, apply a backdrop blur to
    // the entire settings screen for a "frosted glass" effect. The
    // dialog composables signal show/dismiss via LocalSettingsDialogShowing.
    val settingsDialogShowing = rememberSettingsDialogHostState()

    CompositionLocalProvider(LocalSettingsDialogShowing provides settingsDialogShowing) {
        Scaffold(
            modifier =
                Modifier
                    .fillMaxSize()
                    .then(
                        // Only blur when a dialog is showing. We use
                        // `then(if ...) instead of `Modifier.blur(...)`
                        // directly so the modifier chain is stable when
                        // no dialog is open (avoids unnecessary
                        // RenderEffect allocation on every recomposition).
                        if (settingsDialogShowing.value) {
                            Modifier.blur(10.dp)
                        } else {
                            Modifier
                        },
                    )
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = MaterialTheme.colorScheme.surface,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = stringResource(R.string.back_button_desc),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = Color.Transparent,
                    ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        // Compute the player-aware bottom inset (nav bar + mini player + safe inset) so we can
        // fold it into the LazyColumn's contentPadding. We do NOT apply it via windowInsetsPadding
        // because that would reserve space ABOVE the nav bar — content would never scroll behind
        // the floating nav bar. By putting it into contentPadding instead, the column extends to
        // the very bottom of the screen (content visibly scrolls behind the nav bar) and the last
        // items get a "minimum height" clearance so they aren't permanently hidden behind the bar.
        val playerAwareBottomPadding =
            LocalPlayerAwareWindowInsets.current
                .only(WindowInsetsSides.Bottom)
                .asPaddingValues()
                .calculateBottomPadding()
        LazyColumn(
            state = listState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(
                            WindowInsetsSides.Horizontal,
                        ),
                    ),
            contentPadding =
                PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = playerAwareBottomPadding + SettingsDimensions.ScreenBottomPadding,
                ),
        ) {
            if (hasUpdate && !isUpdateDismissed && searchQuery.isBlank()) {
                item(key = "update", contentType = "settings_banner") {
                    SettingsUpdateBanner(
                        latestVersion = latestVersionName,
                        onClick = { navController.navigate("settings/update") },
                        onDismiss = { isUpdateDismissed = true },
                        modifier =
                            Modifier
                                .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding)
                                .padding(bottom = SettingsDimensions.SectionSpacing),
                    )
                }
            }

            if (shouldShowPermissionHint && searchQuery.isBlank()) {
                item(key = "permission", contentType = "settings_banner") {
                    SettingsPermissionBanner(
                        onRequestPermission = {
                            val toRequest =
                                buildList {
                                    if (!isStorageGranted) add(storagePermission)
                                    if (!isNotificationGranted && notificationPermission != null) {
                                        add(notificationPermission)
                                    }
                                }
                            if (toRequest.isNotEmpty()) {
                                permissionLauncher.launch(toRequest.toTypedArray())
                            }
                        },
                        modifier =
                            Modifier
                                .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding)
                                .padding(bottom = SettingsDimensions.SectionSpacing),
                    )
                }
            }

            item(key = "search_bar", contentType = "search_bar") {
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.search_settings),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.search),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    ),
                    modifier = Modifier
                        .padding(horizontal = SettingsDimensions.SegmentedGroupHorizontalPadding)
                        .fillMaxWidth(),
                )
            }

            item(key = "search_spacing", contentType = "spacing") {
                Spacer(modifier = Modifier.height(SettingsDimensions.SectionSpacing))
            }

            if (searchQuery.isNotBlank() && filteredChildResults.isNotEmpty()) {
                itemsIndexed(
                    items = filteredChildResults,
                    key = { index, result -> result.parentKey + ":" + result.title + ":" + index },
                    contentType = { _, _ -> "search_result" },
                ) { _, result ->
                    SettingsSearchResultItem(
                        result = result,
                        onClick = {
                            result.parentRoute?.let(navController::navigate) ?: result.onClick()
                        },
                        modifier = Modifier.padding(
                            horizontal = SettingsDimensions.SegmentedGroupHorizontalPadding,
                            vertical = 4.dp,
                        ),
                    )
                }
            } else if (searchQuery.isNotBlank()) {
                item(key = "no_results") {
                    Text(
                        text = stringResource(R.string.no_results_found),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            horizontal = SettingsDimensions.SegmentedGroupHorizontalPadding,
                            vertical = 16.dp,
                        ),
                    )
                }
            } else {
                filteredGroups.forEachIndexed { groupIndex, group ->
                    if (groupIndex > 0) {
                        item(
                            key = "settings_group_spacing_$groupIndex",
                            contentType = "settings_group_spacing",
                        ) {
                            Spacer(modifier = Modifier.height(SettingsDimensions.SectionSpacing))
                        }
                    }

                    itemsIndexed(
                        items = group.items,
                        key = { _, item -> item.key },
                        contentType = { _, _ -> "settings_segment" },
                    ) { index, settingsItem ->
                        SettingsSegmentedItem(
                            item = settingsItem,
                            index = index,
                            count = group.items.size,
                            modifier =
                                Modifier
                                    .padding(horizontal = SettingsDimensions.SegmentedGroupHorizontalPadding)
                                    .padding(
                                        bottom =
                                            if (index < group.items.lastIndex) {
                                                SettingsDimensions.SegmentedItemGap
                                            } else {
                                                0.dp
                                            },
                                    ),
                        )
                    }
                }
            }
        }
        }
    }
}
