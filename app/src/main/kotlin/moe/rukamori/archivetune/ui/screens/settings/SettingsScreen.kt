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
            "playback" -> "settings/player"
            "sources" -> "settings/sources"
            "lyrics" -> "settings/lyrics"
            "content" -> "settings/content"
            "behavior" -> "settings/privacy"
            "integration" -> "settings/integration"
            "internet" -> "settings/internet"
            "storage" -> "settings/storage"
            "backup_restore" -> "settings/backup_restore"
            "developer_options" -> "settings/misc"
            "about" -> "settings/about"
            "discord" -> "settings/discord"
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
    val supportsScroll = parentKey !in setOf("developer_options", "about", "po_token", "account")
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
    val filteredChildResults = remember(searchQuery, allSettingsGroups) {
        if (searchQuery.isBlank()) emptyList()
        else {
            // Normalize the query: keep the raw trimmed string for substring
            // matching, and also split on whitespace for per-word matching.
            //
            // MATCHING RULES (in priority order):
            //   (a) The full query string is a substring of the title, any
            //       keyword, or any parent token. Handles "scheduled backup"
            //       verbatim against the "Scheduled backup" title.
            //   (b) Each query word appears as a substring of AT LEAST ONE
            //       token across the COMBINED set {title, keywords, parent
            //       tokens}. Words do NOT all have to live in the same field.
            //       This is what makes "low data mode", "scheduled backup
            //       frequency", "discord rich presence activity", and other
            //       2-, 3-, 4-word queries return results even when each word
            //       lives in a different field (title vs keyword vs subtitle).
            //   (c) Phrase fallback: if the joined query (with single spaces)
            //       is a substring of the joined "all tokens" string, accept.
            //       This catches typos and word-order differences like
            //       "backup scheduled" matching "Scheduled backup".
            val rawQuery = searchQuery.trim().lowercase()
            val queryWords =
                rawQuery.split("\\s+".toRegex())
                    .filter { it.isNotBlank() }
            if (queryWords.isEmpty()) emptyList()
            else {
                data class ScoredResult(
                    val item: SearchResultItem,
                    val score: Int, // higher = better match
                )

                val scored =
                    allSettingsGroups.flatMap { group ->
                        group.items.flatMap { item ->
                            val parentTokens =
                                buildList {
                                    add(item.title.lowercase())
                                    item.subtitle?.lowercase()?.let(::add)
                                    addAll(item.keywords.map { it.lowercase() })
                                }
                            // Pre-join parent tokens once per parent for the phrase check.
                            val parentJoined = parentTokens.joinToString(" ")
                            // Pre-split parent tokens into individual words once so
                            // the per-word prefix matcher (handles plural/singular and
                            // partial-word queries like "config" matching "configuration")
                            // doesn't re-split on every query word.
                            val parentWords = parentTokens.flatMap { it.split(Regex("[\\s_\\-/.]+")) }.filter { it.isNotBlank() }

                            val childResults =
                                item.children.mapNotNull { child ->
                                    val titleLower = child.title.lowercase()
                                    val keywordTokens = child.keywords.map { it.lowercase() }
                                    // The scrollKey (e.g. "scrobble_threshold") is itself a
                                    // strong search signal — users may type the underscored
                                    // form when looking for a specific setting. Normalize
                                    // underscores/dashes to spaces so it tokenizes cleanly.
                                    val scrollKeyTokens = buildList {
                                        add(child.scrollKey.lowercase())
                                        addAll(child.scrollKey.lowercase().split("_", "-", ".").filter { it.isNotBlank() })
                                    }

                                    // Combined token bucket for per-word matching.
                                    // Each query word only needs to match ANY
                                    // token in this combined set — they don't
                                    // all have to be in the same field.
                                    val combinedTokens = buildList {
                                        add(titleLower)
                                        addAll(keywordTokens)
                                        addAll(scrollKeyTokens)
                                        addAll(parentTokens)
                                    }
                                    // Pre-split into individual words for the prefix matcher.
                                    val combinedWords = combinedTokens
                                        .flatMap { it.split(Regex("[\\s_\\-/.]+")) }
                                        .filter { it.isNotBlank() }

                                    // (a) full-query substring match in any single field
                                    val titleSubstr = titleLower.contains(rawQuery)
                                    val keywordSubstr = keywordTokens.any { it.contains(rawQuery) }
                                    val scrollKeySubstr = scrollKeyTokens.any { it.contains(rawQuery) }
                                    val parentSubstr = parentTokens.any { it.contains(rawQuery) }

                                    // (b) per-word: every query word appears as a
                                    //     substring of at least one combined token.
                                    //     PLUS a prefix-of-word match so plurals /
                                    //     partial-word queries (e.g. "config" matching
                                    //     "configuration", "images" matching "image")
                                    //     still hit. This is the catch-all that makes
                                    //     3- and 4-word queries return results even when
                                    //     each word lives in a different field.
                                    val allWordsMatchAnyField =
                                        queryWords.all { q ->
                                            titleLower.contains(q) ||
                                                keywordTokens.any { it.contains(q) } ||
                                                scrollKeyTokens.any { it.contains(q) } ||
                                                parentTokens.any { it.contains(q) } ||
                                                combinedWords.any { word -> word.startsWith(q) || q.startsWith(word) }
                                        }

                                    // (c) phrase fallback — joined query against
                                    //     joined tokens (catches word-order swaps)
                                    val combinedJoined = combinedTokens.joinToString(" ")
                                    val phraseMatch = combinedJoined.contains(rawQuery)

                                    // (b-legacy) all-words-in-same-field match — kept
                                    // for scoring only (a higher score signal than the
                                    // per-word across-fields match).
                                    val titleAllWords =
                                        queryWords.all { q -> titleLower.contains(q) }
                                    val keywordAllWords =
                                        queryWords.all { q -> keywordTokens.any { it.contains(q) } }
                                    val parentAllWords =
                                        queryWords.all { q -> parentTokens.any { it.contains(q) } }

                                    val matches =
                                        titleSubstr || keywordSubstr || scrollKeySubstr ||
                                            parentSubstr || allWordsMatchAnyField ||
                                            phraseMatch || titleAllWords ||
                                            keywordAllWords || parentAllWords

                                    if (!matches) null else {
                                        // Relevance scoring — higher is better.
                                        //   1000 = exact title match (case-insensitive)
                                        //   900  = title starts with the full query
                                        //   800  = title contains the full query as a substring
                                        //   700  = any keyword equals the full query
                                        //   600  = any keyword contains the full query
                                        //   550  = all query words are in the title (any order)
                                        //   500  = phrase match against combined tokens
                                        //   450  = parent token contains the full query
                                        //   400  = all query words are in the keywords (same field)
                                        //   350  = all query words are in the parent tokens (same field)
                                        //   300  = every query word matches some token across fields
                                        //   100  = partial match (shouldn't happen given above)
                                        val score = when {
                                            titleLower == rawQuery -> 1000
                                            titleLower.startsWith(rawQuery) -> 900
                                            titleSubstr -> 800
                                            keywordTokens.any { it == rawQuery } -> 700
                                            keywordSubstr -> 600
                                            titleAllWords -> 550
                                            phraseMatch -> 500
                                            parentSubstr -> 450
                                            keywordAllWords -> 400
                                            parentAllWords -> 350
                                            allWordsMatchAnyField -> 300
                                            else -> 100
                                        }
                                        ScoredResult(
                                            item = SearchResultItem(
                                                title = child.title,
                                                parentTitle = item.title,
                                                parentIcon = item.icon,
                                                parentKey = item.key,
                                                parentAccentColor = item.accentColor,
                                                parentRoute = searchableSettingsRoute(item.key, child.scrollKey),
                                                scrollKey = child.scrollKey,
                                                onClick = item.onClick,
                                                switchControl = child.switchControl,
                                            ),
                                            score = score,
                                        )
                                    }
                                }

                            if (childResults.isNotEmpty()) {
                                childResults
                            } else {
                                // If the parent matches but has no matching
                                // children, show the parent itself as a single
                                // result so top-level items (e.g. "Statistics",
                                // "Language packs", "PO Token") remain searchable
                                // even when they have no children to match against.
                                val parentSubstr = parentTokens.any { it.contains(rawQuery) }
                                val parentAllWords =
                                    queryWords.all { q ->
                                        parentTokens.any { it.contains(q) } ||
                                            parentWords.any { word -> word.startsWith(q) || q.startsWith(word) }
                                    }
                                val parentPhraseMatch = parentJoined.contains(rawQuery)
                                if (parentSubstr || parentAllWords || parentPhraseMatch) {
                                    val score = when {
                                        item.title.lowercase() == rawQuery -> 1000
                                        item.title.lowercase().startsWith(rawQuery) -> 900
                                        parentSubstr -> 400
                                        parentPhraseMatch -> 300
                                        else -> 200
                                    }
                                    listOf(
                                        ScoredResult(
                                            item = SearchResultItem(
                                                title = item.title,
                                                parentTitle = item.subtitle ?: "",
                                                parentIcon = item.icon,
                                                parentKey = item.key,
                                                parentAccentColor = item.accentColor,
                                                parentRoute = null,
                                                scrollKey = null,
                                                onClick = item.onClick,
                                            ),
                                            score = score,
                                        ),
                                    )
                                } else {
                                    emptyList()
                                }
                            }
                        }
                    }

                // Sort by score descending so the best match is at the top.
                // Stable sort preserves screen-order within a score tier.
                scored.sortedByDescending { it.score }.map { it.item }
            }
        }
    }
    val filteredGroups = remember(searchQuery, allSettingsGroups) {
        if (searchQuery.isBlank()) {
            // Hide items flagged `hidden = true` from the main settings page rendering,
            // but keep them in `allSettingsGroups` so their children stay searchable.
            allSettingsGroups.map { group ->
                group.copy(items = group.items.filterNot { it.hidden })
            }.filter { it.items.isNotEmpty() }
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
