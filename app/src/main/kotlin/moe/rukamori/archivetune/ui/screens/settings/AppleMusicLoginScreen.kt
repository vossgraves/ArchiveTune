/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.settings

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import java.util.concurrent.atomic.AtomicBoolean
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.AuthWebViewScreen

const val APPLE_MUSIC_LOGIN_ROUTE = "settings/applemusic/login"

private const val LOGIN_URL = "https://music.apple.com/login"
private const val COOKIE_ORIGIN = "https://music.apple.com"

/**
 * Browser sign-in for Apple Music. The web session cookie proves the account is
 * live; the Music User Token itself is pasted on the Apple Music settings page
 * because MusicKit JS keeps it out of the cookie jar. See [AppleMusicSettings].
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AppleMusicLoginScreen(navController: NavController) {
    // The cookie appears while the page is still navigating, so onPageFinished can fire
    // several more times with it present. Guard so verification kicks off once.
    val handled = remember { AtomicBoolean(false) }

    fun readSessionCookie(): Boolean =
        CookieManager
            .getInstance()
            .getCookie(COOKIE_ORIGIN)
            ?.split(';')
            ?.any { it.trim().startsWith("its.pod=", ignoreCase = true) || it.trim().startsWith("pxro=", ignoreCase = true) }
            ?: false

    AuthWebViewScreen(
        navController = navController,
        title = stringResource(R.string.applemusic_login),
        subtitle = stringResource(R.string.applemusic_login_subtitle),
        factory = { ctx ->
            WebView(ctx).apply {
                webViewClient =
                    object : WebViewClient() {
                        override fun onPageFinished(
                            view: WebView,
                            url: String?,
                        ) {
                            if (!readSessionCookie()) return
                            if (!handled.compareAndSet(false, true)) return
                            navController.navigateUp()
                        }
                    }
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                loadUrl(LOGIN_URL)
            }
        },
    )
}
