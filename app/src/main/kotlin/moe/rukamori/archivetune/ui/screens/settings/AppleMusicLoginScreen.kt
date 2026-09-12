/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.settings

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AppleMusicDevTokenKey
import moe.rukamori.archivetune.constants.AppleMusicMediaUserTokenKey
import moe.rukamori.archivetune.ui.component.AuthWebViewScreen
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.resetAuthWebViewSession
import java.util.concurrent.atomic.AtomicBoolean

const val APPLE_MUSIC_LOGIN_ROUTE = "settings/applemusic/login"

private const val LOGIN_URL = "https://music.apple.com/login"

/**
 * Reads both Apple Music tokens out of the signed-in web player.
 *
 * A cookie probe cannot do this: MusicKit keeps the Music User Token out of the cookie jar, which
 * is why this screen previously could not tell that sign-in had finished and left the token to be
 * pasted by hand. Both tokens are however readable from the live MusicKit instance, and the
 * user token is additionally mirrored into localStorage under a `media-user-token` key, so the
 * scan covers the case where the instance is not reachable on the current page.
 *
 * Polls because MusicKit initialises asynchronously and the user token only appears after the
 * Apple ID flow completes — there is no event to hook. Gives up after ~2 minutes so a page that
 * never signs in does not poll for the lifetime of the screen.
 */
private const val APPLE_HOOK_JS = """
javascript:(function () {
  if (window.__atAppleHook) return;
  window.__atAppleHook = true;
  var tries = 0;
  function readStoredUserToken() {
    try {
      for (var i = 0; i < localStorage.length; i++) {
        var k = localStorage.key(i);
        if (k && k.toLowerCase().indexOf('media-user-token') !== -1) {
          var v = localStorage.getItem(k);
          if (v && v.indexOf('0.') === 0) return v;
        }
      }
    } catch (e) {}
    return null;
  }
  var timer = setInterval(function () {
    tries++;
    if (tries > 240) { clearInterval(timer); return; }
    var dev = null, usr = null;
    try {
      var mk = window.MusicKit && window.MusicKit.getInstance && window.MusicKit.getInstance();
      if (mk) { dev = mk.developerToken || null; usr = mk.musicUserToken || null; }
    } catch (e) {}
    if (!usr) usr = readStoredUserToken();
    if (usr) {
      clearInterval(timer);
      try { AppleAuth.onTokens(usr, dev); } catch (e) {}
    }
  }, 500);
})()
"""

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AppleMusicLoginScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // The poll fires repeatedly; only the first delivery may save and navigate.
    val handled = remember { AtomicBoolean(false) }

    AuthWebViewScreen(
        navController = navController,
        title = stringResource(R.string.applemusic_login),
        subtitle = stringResource(R.string.applemusic_login_subtitle),
        factory = { ctx ->
            WebView(ctx).apply {
                webViewClient =
                    object : android.webkit.WebViewClient() {
                        override fun onPageFinished(
                            view: WebView,
                            url: String?,
                        ) {
                            if (url?.contains("music.apple.com", ignoreCase = true) == true) {
                                view.loadUrl(APPLE_HOOK_JS)
                            }
                        }
                    }
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                }
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun onTokens(
                            userToken: String?,
                            devToken: String?,
                        ) {
                            // The user token is what gates sign-in; the developer token is a bonus
                            // when MusicKit was reachable, and the settings page can still scrape
                            // its own if this page could not supply one.
                            val user = userToken?.trim().orEmpty()
                            if (!user.startsWith("0.")) return
                            if (!handled.compareAndSet(false, true)) return
                            val dev = devToken?.trim().orEmpty()
                            scope.launch {
                                context.dataStore.edit { prefs ->
                                    prefs[AppleMusicMediaUserTokenKey] = user
                                    if (dev.isNotBlank()) prefs[AppleMusicDevTokenKey] = dev
                                }
                                Toast
                                    .makeText(context, R.string.applemusic_login_success, Toast.LENGTH_SHORT)
                                    .show()
                                navController.navigateUp()
                            }
                        }
                    },
                    "AppleAuth",
                )
                resetAuthWebViewSession(ctx, this, clearCookies = true) {
                    loadUrl(LOGIN_URL)
                }
            }
        },
    )
}
