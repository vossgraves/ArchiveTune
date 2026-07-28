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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.LocalSettingsDialogShowing
import moe.rukamori.archivetune.ui.component.rememberSettingsDialogHostState
import moe.rukamori.archivetune.ui.utils.appBarScrollBehavior
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.Updater

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
    val filteredGroups = remember(searchQuery, allSettingsGroups) {
        if (searchQuery.isBlank()) {
            // Deep entries point at a single preference inside a sub-screen and only make sense as
            // search results, so they are dropped from the resting list.
            allSettingsGroups
                .map { group -> group.copy(items = group.items.filterNot { it.deepOnly }) }
                .filter { it.items.isNotEmpty() }
        } else {
            val query = searchQuery.trim().lowercase()
            allSettingsGroups.map { group ->
                val filteredItems = group.items.filter { item ->
                    item.title.lowercase().contains(query) ||
                        item.subtitle?.lowercase()?.contains(query) == true ||
                        item.keywords.any { keyword -> keyword.lowercase().contains(query) }
                }
                group.copy(items = filteredItems, showWhenFiltered = filteredItems.isNotEmpty())
            }.filter { it.items.isNotEmpty() }
        }
    }

    // Auto-scroll to the first matching item when search query changes.
    // We debounce slightly so rapid typing doesn't cause excessive scrolls.
    // Note: filteredGroups is NOT a LaunchedEffect key — if it were, every
    // keystroke would cancel the debounce timer before it fires.
    LaunchedEffect(listState) {
        snapshotFlow { searchQuery }
            .debounce(200L)
            .distinctUntilChanged()
            .collect { query ->
                if (query.isBlank()) return@collect
                // Snapshot the current filtered groups inside the flow so
                // we always scroll to the latest match.
                val groups = filteredGroups
                val firstItemKey = groups
                    .firstOrNull()?.items?.firstOrNull()?.key ?: return@collect
                // When searching, the banners are hidden, so the LazyColumn layout is:
                //   0 = search_bar, 1 = search_spacing, then group items.
                var targetIndex = 2
                var found = false
                for (group in groups) {
                    if (group.items.any { it.key == firstItemKey }) {
                        found = true
                        break
                    }
                    // Each non-first group has a spacer item before its items.
                    targetIndex += 1 + group.items.size
                }
                if (found) {
                    listState.animateScrollToItem(targetIndex)
                }
            }
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
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                        ),
                    ),
            contentPadding =
                PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = SettingsDimensions.ScreenBottomPadding,
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
