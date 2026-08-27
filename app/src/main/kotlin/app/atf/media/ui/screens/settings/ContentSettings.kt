/*
 * ArchiveTune (2026)
 * © ArchiveTuneFork contributors — github.com/vossgraves/ArchiveTune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package app.atf.media.ui.screens.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.atf.media.LocalPlayerAwareWindowInsets
import app.atf.media.R
import app.atf.media.constants.*
import moe.rukamori.archivetune.innertube.YouTube
import app.atf.media.ui.component.EditTextPreference
import app.atf.media.ui.component.IconButton
import app.atf.media.ui.component.ListPreference
import app.atf.media.ui.component.PreferenceEntry
import app.atf.media.ui.component.PreferenceGroup
import app.atf.media.ui.component.SwitchPreference
import app.atf.media.ui.utils.backToMain
import app.atf.media.utils.rememberEnumPreference
import app.atf.media.utils.rememberPreference
import app.atf.media.utils.setAppLocale
import app.atf.media.viewmodels.AiContentFilterSettingsEffect
import app.atf.media.viewmodels.AiContentFilterSettingsState
import app.atf.media.viewmodels.ContentSettingsViewModel
import java.util.Locale

@Composable
fun ContentSettings(
    navController: NavController,
    viewModel: ContentSettingsViewModel = hiltViewModel(),
    scrollTo: String? = null,
) {
    val context = LocalContext.current
    val aiContentFilterState by viewModel.aiContentFilterState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel, context) {
        viewModel.aiContentFilterEffects.collect { effect ->
            when (effect) {
                is AiContentFilterSettingsEffect.ShowMessage -> {
                    snackbarHostState.showSnackbar(context.getString(effect.messageResId))
                }

                is AiContentFilterSettingsEffect.OpenUrl -> {
                    context.startActivity(Intent(Intent.ACTION_VIEW, effect.url.toUri()))
                }
            }
        }
    }

    // Used only before Android 13
    val (appLanguage, onAppLanguageChange) = rememberPreference(key = AppLanguageKey, defaultValue = SYSTEM_DEFAULT)

    val (contentLanguage, onContentLanguageChange) = rememberPreference(key = ContentLanguageKey, defaultValue = "system")
    val (contentCountry, onContentCountryChange) = rememberPreference(key = ContentCountryKey, defaultValue = "system")
    val (playlistSuggestionSource, onPlaylistSuggestionSourceChange) =
        rememberEnumPreference(
            key = PlaylistSuggestionSourceKey,
            defaultValue = PlaylistSuggestionSource.BOTH,
        )
    val (hideExplicit, onHideExplicitChange) = rememberPreference(key = HideExplicitKey, defaultValue = false)
    val (hideVideo, onHideVideoChange) = rememberPreference(key = HideVideoKey, defaultValue = false)
    val (allowAgeRestricted, onAllowAgeRestrictedChange) = rememberPreference(key = AllowAgeRestrictedKey, defaultValue = false)
    val (lengthTop, onLengthTopChange) = rememberPreference(key = TopSize, defaultValue = "50")
    // (Round 13) "Set quick picks" UI removed — the preference is still read
    // here so the underlying DataStore value is preserved (in case the user
    // re-enables the UI later), but the values are intentionally unused.
    @Suppress("UNUSED_VARIABLE")
    val (quickPicks, onQuickPicksChange) = rememberEnumPreference(key = QuickPicksKey, defaultValue = QuickPicks.QUICK_PICKS)

    val scrollState = rememberScrollState()
    val positions = rememberPreferencePositions()

    LaunchedEffect(scrollTo) { positions.scrollToKey(scrollTo, scrollState) }

    val playerAwareBottomPadding =
        LocalPlayerAwareWindowInsets.current
            .only(WindowInsetsSides.Bottom)
            .asPaddingValues()
            .calculateBottomPadding()

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            // Chained before verticalScroll so it measures the viewport, not the scrolling content.
            .then(positions.containerModifier())
            .verticalScroll(scrollState)
            .padding(bottom = playerAwareBottomPadding + SettingsDimensions.ScreenBottomPadding),
    ) {
        PreferenceGroup(
            modifier = positions.modifierFor("content_language"),
            title = stringResource(R.string.general),
        ) {
            item {
                ListPreference(
                    title = { Text(stringResource(R.string.content_language)) },
                    icon = { Icon(painterResource(R.drawable.language), null) },
                    selectedValue = contentLanguage,
                    values = listOf(SYSTEM_DEFAULT) + LanguageCodeToName.keys.toList(),
                    valueText = {
                        LanguageCodeToName.getOrElse(it) { stringResource(R.string.system_default) }
                    },
                    onValueSelected = { newValue ->
                        val locale = Locale.getDefault()
                        val languageTag = locale.toLanguageTag().replace("-Hant", "")

                        YouTube.locale =
                            YouTube.locale.copy(
                                hl =
                                    newValue.takeIf { it != SYSTEM_DEFAULT }
                                        ?: locale.language.takeIf { it in LanguageCodeToName }
                                        ?: languageTag.takeIf { it in LanguageCodeToName }
                                        ?: "en",
                            )

                        onContentLanguageChange(newValue)
                    },
                )
            }

            item {
                ListPreference(
                    modifier = positions.modifierFor("content_country"),
                    title = { Text(stringResource(R.string.content_country)) },
                    icon = { Icon(painterResource(R.drawable.location_on), null) },
                    selectedValue = contentCountry,
                    values = listOf(SYSTEM_DEFAULT) + CountryCodeToName.keys.toList(),
                    valueText = {
                        CountryCodeToName.getOrElse(it) { stringResource(R.string.system_default) }
                    },
                    onValueSelected = { newValue ->
                        val locale = Locale.getDefault()

                        YouTube.locale =
                            YouTube.locale.copy(
                                gl =
                                    newValue.takeIf { it != SYSTEM_DEFAULT }
                                        ?: locale.country.takeIf { it in CountryCodeToName }
                                        ?: "US",
                            )

                        onContentCountryChange(newValue)
                    },
                )
            }

            item {
                ListPreference(
                    modifier = positions.modifierFor("you_might_like_source"),
                    title = { Text(stringResource(R.string.you_might_like_source)) },
                    icon = { Icon(painterResource(R.drawable.playlist_play), null) },
                    selectedValue = playlistSuggestionSource,
                    values =
                        listOf(
                            PlaylistSuggestionSource.PLAYLIST_TITLE,
                            PlaylistSuggestionSource.PLAYLIST_CONTENT,
                            PlaylistSuggestionSource.BOTH,
                        ),
                    valueText = {
                        when (it) {
                            PlaylistSuggestionSource.PLAYLIST_TITLE -> stringResource(R.string.playlist_suggestion_source_title)
                            PlaylistSuggestionSource.PLAYLIST_CONTENT -> stringResource(R.string.playlist_suggestion_source_content)
                            PlaylistSuggestionSource.BOTH -> stringResource(R.string.playlist_suggestion_source_both)
                        }
                    },
                    onValueSelected = onPlaylistSuggestionSourceChange,
                )
            }

            item {
                SwitchPreference(
                    modifier = positions.modifierFor("hide_explicit"),
                    title = { Text(stringResource(R.string.hide_explicit)) },
                    icon = { Icon(painterResource(R.drawable.explicit), null) },
                    checked = hideExplicit,
                    onCheckedChange = onHideExplicitChange,
                )
            }

            item {
                SwitchPreference(
                    modifier = positions.modifierFor("hide_video"),
                    title = { Text(stringResource(R.string.hide_video)) },
                    icon = { Icon(painterResource(R.drawable.slow_motion_video), null) },
                    checked = hideVideo,
                    onCheckedChange = onHideVideoChange,
                )
            }

            item {
                SwitchPreference(
                    modifier = positions.modifierFor("allow_age_restricted"),
                    title = { Text(stringResource(R.string.allow_age_restricted)) },
                    description = stringResource(R.string.allow_age_restricted_summary),
                    icon = { Icon(painterResource(R.drawable.login), null) },
                    checked = allowAgeRestricted,
                    onCheckedChange = onAllowAgeRestrictedChange,
                )
            }
        }

        AiContentFilterPreferences(
            state = aiContentFilterState,
            onEnabledChange = viewModel::setAiContentFilterEnabled,
            onIncludeModerateChange = viewModel::setAiContentFilterIncludeModerate,
            onRefresh = viewModel::refreshAiContentFilter,
            onOpenSource = viewModel::openAiContentFilterSource,
            positions = positions,
        )

        PreferenceGroup(
            modifier = positions.modifierFor("app_language"),
            title = stringResource(R.string.app_language),
        ) {
            item {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.app_language)) },
                        icon = { Icon(painterResource(R.drawable.language), null) },
                        onClick = {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APP_LOCALE_SETTINGS,
                                    "package:${context.packageName}".toUri(),
                                ),
                            )
                        },
                    )
                } else {
                    ListPreference(
                        title = { Text(stringResource(R.string.app_language)) },
                        icon = { Icon(painterResource(R.drawable.language), null) },
                        selectedValue = appLanguage,
                        values = listOf(SYSTEM_DEFAULT) + LanguageCodeToName.keys.toList(),
                        valueText = {
                            LanguageCodeToName.getOrElse(it) { stringResource(R.string.system_default) }
                        },
                        onValueSelected = { langTag ->
                            val newLocale =
                                langTag
                                    .takeUnless { it == SYSTEM_DEFAULT }
                                    ?.let { Locale.forLanguageTag(it) }
                                    ?: Locale.getDefault()

                            onAppLanguageChange(langTag)
                            setAppLocale(context, newLocale)
                        },
                    )
                }
            }
        }

        PreferenceGroup(
            modifier = positions.modifierFor("quick_picks"),
            title = stringResource(R.string.misc),
        ) {
            item {
                EditTextPreference(
                    modifier = positions.modifierFor("ai_content_filter"),
                    title = { Text(stringResource(R.string.top_length)) },
                    icon = { Icon(painterResource(R.drawable.trending_up), null) },
                    value = lengthTop,
                    isInputValid = { it.toIntOrNull()?.let { num -> num > 0 } == true },
                    onValueChange = onLengthTopChange,
                )
            }
            // (Round 13) "Set quick picks" ListPreference removed per user
            // request — the quick-picks home tab is no longer configurable
            // from Content settings. The underlying QuickPicksKey preference
            // is preserved (not deleted) so any previously-saved value
            // remains intact; the setting is just no longer exposed in the UI.
        }
    }

    TopAppBar(
        title = { Text(stringResource(R.string.content)) },
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

    Box(Modifier.fillMaxSize()) {
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun AiContentFilterPreferences(
    state: AiContentFilterSettingsState,
    onEnabledChange: (Boolean) -> Unit,
    onIncludeModerateChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onOpenSource: () -> Unit,
    positions: PreferencePositions,
) {
    PreferenceGroup(title = stringResource(R.string.ai_content_filter)) {
        when (state) {
            AiContentFilterSettingsState.Loading -> {
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.ai_content_filter)) },
                        description = stringResource(R.string.loading),
                        icon = { Icon(painterResource(R.drawable.auto_awesome), null) },
                        isEnabled = false,
                    )
                }
            }

            AiContentFilterSettingsState.Empty -> {
                Unit
            }

            is AiContentFilterSettingsState.Error -> {
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.ai_content_filter)) },
                        description = stringResource(state.messageResId),
                        icon = { Icon(painterResource(R.drawable.auto_awesome), null) },
                        onClick = onRefresh,
                    )
                }
            }

            is AiContentFilterSettingsState.Success -> {
                val model = state.model
                item {
                    SwitchPreference(
                        modifier = positions.modifierFor("ai_content_filter_hide"),
                        title = { Text(stringResource(R.string.ai_content_filter_hide)) },
                        description = stringResource(R.string.ai_content_filter_hide_summary),
                        icon = { Icon(painterResource(R.drawable.auto_awesome), null) },
                        checked = model.enabled,
                        onCheckedChange = onEnabledChange,
                    )
                }
                item {
                    SwitchPreference(
                        modifier = positions.modifierFor("ai_content_filter_moderate"),
                        title = { Text(stringResource(R.string.ai_content_filter_moderate)) },
                        description = stringResource(R.string.ai_content_filter_moderate_summary),
                        icon = { Icon(painterResource(R.drawable.filter_alt), null) },
                        checked = model.includeModerateConfidence,
                        onCheckedChange = onIncludeModerateChange,
                        isEnabled = model.enabled,
                    )
                }
                item {
                    PreferenceEntry(
                        modifier = positions.modifierFor("ai_content_filter_update"),
                        title = { Text(stringResource(R.string.ai_content_filter_update)) },
                        description =
                            if (model.refreshing) {
                                stringResource(R.string.loading)
                            } else {
                                stringResource(
                                    R.string.ai_content_filter_list_counts,
                                    model.blocklistCount,
                                    model.warnlistCount,
                                )
                            },
                        icon = { Icon(painterResource(R.drawable.sync), null) },
                        onClick = onRefresh,
                        isEnabled = !model.refreshing,
                    )
                }
                item {
                    PreferenceEntry(
                        modifier = positions.modifierFor("ai_content_filter_source"),
                        title = { Text(stringResource(R.string.ai_content_filter_source)) },
                        description = stringResource(R.string.ai_content_filter_source_summary),
                        icon = { Icon(painterResource(R.drawable.info), null) },
                        onClick = onOpenSource,
                    )
                }
            }
        }
    }
}
