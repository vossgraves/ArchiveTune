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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.LyricsRomanizeChineseKey
import moe.rukamori.archivetune.constants.LyricsRomanizeHindiKey
import moe.rukamori.archivetune.constants.LyricsRomanizeJapaneseKey
import moe.rukamori.archivetune.constants.LyricsRomanizeKoreanKey
import moe.rukamori.archivetune.constants.LyricsRomanizeOtherLanguagesKey
import moe.rukamori.archivetune.lyrics.JapaneseLanguagePackManager
import moe.rukamori.archivetune.lyrics.JapaneseLanguagePackState
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.rememberPreference

/**
 * Romanisation sub-page (Task 3): houses every per-language romanisation toggle that
 * used to live inline on the Lyrics settings page. Behaviour preserved verbatim —
 * Japanese romanisation stays gated on the Japanese language pack being installed,
 * matching the original inline implementation.
 */
@Composable
fun LyricsRomanisationSettings(navController: NavController) {
    val (lyricsRomanizeJapanese, onLyricsRomanizeJapaneseChange) =
        rememberPreference(LyricsRomanizeJapaneseKey, defaultValue = false)
    val (lyricsRomanizeKorean, onLyricsRomanizeKoreanChange) =
        rememberPreference(LyricsRomanizeKoreanKey, defaultValue = true)
    val (lyricsRomanizeChinese, onLyricsRomanizeChineseChange) =
        rememberPreference(LyricsRomanizeChineseKey, defaultValue = true)
    val (lyricsRomanizeHindi, onLyricsRomanizeHindiChange) =
        rememberPreference(LyricsRomanizeHindiKey, defaultValue = true)
    val (lyricsRomanizeOtherLanguages, onLyricsRomanizeOtherLanguagesChange) =
        rememberPreference(LyricsRomanizeOtherLanguagesKey, defaultValue = true)
    val japaneseLanguagePackState by JapaneseLanguagePackManager.state.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.romanization)) },
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
        val scrollState = rememberScrollState()
        Column(
            Modifier
                .padding(top = innerPadding.calculateTopPadding())
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                )
                .verticalScroll(scrollState)
                .padding(bottom = SettingsDimensions.ScreenBottomPadding),
        ) {
            PreferenceGroup(title = stringResource(R.string.romanization)) {
                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.lyrics_romanize_japanese)) },
                        description =
                            if (japaneseLanguagePackState is JapaneseLanguagePackState.Installed) {
                                null
                            } else {
                                stringResource(R.string.language_pack_required)
                            },
                        icon = { Icon(painterResource(R.drawable.lyrics), null) },
                        checked = lyricsRomanizeJapanese,
                        onCheckedChange = onLyricsRomanizeJapaneseChange,
                        isEnabled = japaneseLanguagePackState is JapaneseLanguagePackState.Installed,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.lyrics_romanize_korean)) },
                        icon = { Icon(painterResource(R.drawable.lyrics), null) },
                        checked = lyricsRomanizeKorean,
                        onCheckedChange = onLyricsRomanizeKoreanChange,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.lyrics_romanize_chinese)) },
                        icon = { Icon(painterResource(R.drawable.lyrics), null) },
                        checked = lyricsRomanizeChinese,
                        onCheckedChange = onLyricsRomanizeChineseChange,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.lyrics_romanize_hindi)) },
                        icon = { Icon(painterResource(R.drawable.lyrics), null) },
                        checked = lyricsRomanizeHindi,
                        onCheckedChange = onLyricsRomanizeHindiChange,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.lyrics_romanize_other_languages)) },
                        icon = { Icon(painterResource(R.drawable.lyrics), null) },
                        checked = lyricsRomanizeOtherLanguages,
                        onCheckedChange = onLyricsRomanizeOtherLanguagesChange,
                    )
                }
            }
        }
    }
}
