/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.settings

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.AuthWebViewScreen

const val APPLE_MUSIC_LOGIN_ROUTE = "settings/applemusic/login"

private const val LOGIN_URL = "https://music.apple.com/login"

/**
 * Browser sign-in for Apple Music. The web session cookie proves the account is
 * live; the Music User Token itself is pasted on the Apple Music settings page
 * because MusicKit JS keeps it out of the cookie jar. See [AppleMusicSettings].
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AppleMusicLoginScreen(navController: NavController) {
    AuthWebViewScreen(
        navController = navController,
        title = stringResource(R.string.applemusic_login),
        subtitle = stringResource(R.string.applemusic_login_subtitle),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                loadUrl(LOGIN_URL)
            }
        },
    )
}
