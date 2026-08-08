/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * WebView-based Deezer sign-in. Deezer has no OAuth flow we can use, so the credential is the `arl`
 * session cookie the site sets on a signed-in browser. Mirrors the [TidalLoginScreen] WebView
 * pattern and persists the cookie to DataStore.
 */

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.foundation.layout.WindowInsets
import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.DeezerAccountNameKey
import moe.rukamori.archivetune.constants.DeezerAccountPremiumKey
import moe.rukamori.archivetune.constants.DeezerArlKey
import moe.rukamori.archivetune.constants.DeezerEnabledKey
import moe.rukamori.archivetune.deezer.DeezerAudioProvider
import moe.rukamori.archivetune.ui.component.AuthWebViewScreen
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.resetAuthWebViewSession
import java.util.concurrent.atomic.AtomicBoolean

const val DEEZER_LOGIN_ROUTE = "settings/deezer/login"

private const val LOGIN_URL = "https://www.deezer.com/login"

/** Cookies are read for this origin; the `arl` cookie is scoped to `.deezer.com`. */
private const val COOKIE_ORIGIN = "https://www.deezer.com"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DeezerLoginScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // The cookie appears while the page is still navigating, so onPageFinished can fire several more
    // times with it present. Without this guard each one would kick off its own verification.
    val handled = remember { AtomicBoolean(false) }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    /**
     * Pulls `arl` out of the cookie jar. Read through [CookieManager] rather than `document.cookie`
     * because the cookie is HttpOnly and therefore invisible to JavaScript.
     */
    fun readArl(): String? =
        CookieManager
            .getInstance()
            .getCookie(COOKIE_ORIGIN)
            ?.split(';')
            ?.firstNotNullOfOrNull { part ->
                val (name, value) = part.split('=', limit = 2).takeIf { it.size == 2 } ?: return@firstNotNullOfOrNull null
                value.trim().takeIf { name.trim().equals("arl", ignoreCase = true) && it.isNotEmpty() }
            }

    fun finishLogin(arl: String) {
        scope.launch {
            // Verify before saving: Deezer also issues an `arl` to anonymous visitors, so its mere
            // presence does not mean anyone signed in.
            val info = withContext(Dispatchers.IO) { DeezerAudioProvider.verifyArl(arl) }
            if (info == null) {
                // Not signed in yet (or the cookie is stale) — let the user keep going rather than
                // closing the screen on them.
                handled.set(false)
                return@launch
            }
            context.dataStore.edit { prefs ->
                prefs[DeezerArlKey] = arl
                prefs[DeezerAccountNameKey] = info.name
                prefs[DeezerAccountPremiumKey] = info.lossless
                // Signing in is an explicit opt-in to the source, which defaults off; leaving it off
                // would make a successful login look like it did nothing.
                prefs[DeezerEnabledKey] = true
            }
            // Push it immediately so playback works without waiting for the App-level collector.
            DeezerAudioProvider.setManualArl(arl, info.lossless)
            toast(context.getString(R.string.deezer_login_success, info.name))
            navController.navigateUp()
        }
    }

    AuthWebViewScreen(
        navController = navController,
        title = stringResource(R.string.deezer_login),
        subtitle = stringResource(R.string.auth_webview_deezer_subtitle),
        factory = { ctx ->
            WebView(ctx).apply {
                webViewClient =
                    object : WebViewClient() {
                        override fun onPageFinished(
                            view: WebView,
                            url: String?,
                        ) {
                            // Checked on every completed navigation rather than on a single redirect
                            // URL: Deezer has no post-login redirect we control, and the cookie can
                            // land on any of several pages depending on how the account signs in.
                            val arl = readArl() ?: return
                            if (!handled.compareAndSet(false, true)) return
                            finishLogin(arl)
                        }
                    }
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                }
                // Clearing cookies first means an already-signed-in browser session cannot hand back
                // a stale ARL for an account the user is trying to switch away from.
                resetAuthWebViewSession(ctx, this, clearCookies = true) {
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    loadUrl(LOGIN_URL)
                }
            }
        },
    )
}
