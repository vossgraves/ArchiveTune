/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * WebView-based Qobuz sign-in. Mirrors the Tidal login: after the user signs in on
 * play.qobuz.com, a JS hook captures the X-User-Auth-Token, X-App-Id, and scrapes the
 * app_secret (32-char hex) from the loaded bundle scripts. When all three are found the
 * session is saved automatically with no manual input. If the secret cannot be scraped
 * a fallback dialog lets the user paste it manually.
 */

package moe.rukamori.archivetune.ui.screens.settings

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.QobuzTokensKey
import moe.rukamori.archivetune.qobuz.QobuzToken
import moe.rukamori.archivetune.ui.component.AuthWebViewScreen
import moe.rukamori.archivetune.ui.component.TextFieldDialog
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.resetAuthWebViewSession
import java.util.concurrent.atomic.AtomicBoolean

const val QOBUZ_LOGIN_ROUTE = "settings/qobuz/login"

private const val QOBUZ_WEB_PLAYER_URL = "https://play.qobuz.com/login"

// Hooks fetch()/XHR to capture the auth token + app_id headers, then scrapes loaded bundle
// scripts for the 32-char hex app_secret. Calls onCredentials(token, appId) as soon as the
// headers are seen, and onSecret(secret) once a valid candidate is found in a bundle script.
// Both run once per page load (guarded by __atQobuzHook).
private val QOBUZ_HOOK_JS =
    """
    javascript:(function(){
      if(window.__atQobuzHook)return;window.__atQobuzHook=1;
      var tok=null,app=null;
      function pushCreds(){try{if(tok&&app){QobuzAuth.onCredentials(tok,app);}}catch(e){}}
      function scanHeaders(h){try{if(!h)return;
        var t=h.get?h.get('X-User-Auth-Token'):(h['X-User-Auth-Token']||h['x-user-auth-token']);
        var a=h.get?h.get('X-App-Id'):(h['X-App-Id']||h['x-app-id']);
        if(t&&t.length>20){tok=t;} if(a&&a.length>3){app=a;} pushCreds();
      }catch(e){}}
      try{var of=window.fetch;if(of){window.fetch=function(){try{var a=arguments[1];if(a&&a.headers){scanHeaders(a.headers);}}catch(e){}return of.apply(this,arguments);};}}catch(e){}
      try{var os=XMLHttpRequest.prototype.setRequestHeader;XMLHttpRequest.prototype.setRequestHeader=function(k,v){try{var kk=String(k).toLowerCase();if(kk==='x-user-auth-token'&&v&&v.length>20){tok=v;}if(kk==='x-app-id'&&v){app=v;}pushCreds();}catch(e){}return os.apply(this,arguments);};}catch(e){}
      // Scrape the app_secret from bundle scripts: it is a 32-char lowercase hex string that
      // appears as a standalone value (surrounded by quotes or punctuation) in the JS bundle.
      try{
        var scripts=document.querySelectorAll('script[src]');
        var scraped=false;
        function scanBundle(js){
          if(scraped)return;
          var matches=js.match(/[^a-fA-F0-9]([a-f0-9]{32})[^a-fA-F0-9]/g);
          if(!matches)return;
          var seen={};
          for(var i=0;i<matches.length;i++){
            var m=matches[i].replace(/[^a-f0-9]/g,'');
            if(m.length===32&&!seen[m]){seen[m]=1;
              // Skip known non-secret patterns (md5 of empty string, common constants).
              if(m==='d41d8cd98f00b204e9800998ecf8427e')continue;
              scraped=true;
              try{QobuzAuth.onSecret(m);}catch(e){}
              return;
            }
          }
        }
        for(var i=0;i<scripts.length;i++){
          (function(src){
            fetch(src).then(function(r){return r.text();}).then(scanBundle).catch(function(){});
          })(scripts[i].src);
        }
      }catch(e){}
    })()
    """.trimIndent()

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun QobuzLoginScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialHandled = remember { AtomicBoolean(false) }

    // Captured headers from the web player.
    var captured by remember { mutableStateOf<Pair<String, String>?>(null) }
    // App secret scraped from the bundle — null until found.
    var scrapedSecret by remember { mutableStateOf<String?>(null) }
    // Whether to show the manual-paste fallback dialog.
    var showSecretDialog by remember { mutableStateOf(false) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    fun saveToken(token: String, appId: String, appSecret: String) {
        scope.launch {
            context.dataStore.edit { prefs ->
                val existing = QobuzToken.listFromJson(prefs[QobuzTokensKey])
                val merged =
                    (existing.filterNot { it.token == token }) +
                        QobuzToken(token = token, appId = appId, appSecret = appSecret, label = "Web login")
                prefs[QobuzTokensKey] = QobuzToken.listToJson(merged)
            }
            toast(context.getString(R.string.qobuz_login_success))
            navController.navigateUp()
        }
    }

    // When credentials arrive, check if we already have the scraped secret and save immediately;
    // otherwise hold and wait (or fall through to the dialog after a short window).
    fun onCredentialsReceived(token: String, appId: String) {
        val secret = scrapedSecret
        if (secret != null) {
            saveToken(token, appId, secret)
        } else {
            // Hold the credentials; the secret callback will fire saveToken when it arrives.
            // If the user navigates away from the login page before the secret arrives we fall
            // back to the manual dialog.
            captured = token to appId
        }
    }

    // When the bundle secret arrives, save immediately if we already have credentials.
    fun onSecretReceived(secret: String) {
        scrapedSecret = secret
        val (token, appId) = captured ?: return
        // Secret arrived after credentials — save now, no dialog needed.
        showSecretDialog = false
        saveToken(token, appId, secret)
    }

    // Fallback manual-paste dialog — shown only when scraping failed.
    if (showSecretDialog) {
        captured?.let { (token, appId) ->
            TextFieldDialog(
                icon = { Icon(painterResource(R.drawable.token), null) },
                title = { Text(stringResource(R.string.qobuz_app_secret_title)) },
                placeholder = { Text(stringResource(R.string.qobuz_app_secret_hint)) },
                isInputValid = { it.trim().length >= 16 },
                onDone = { secret -> saveToken(token, appId, secret.trim()) },
                onDismiss = {
                    showSecretDialog = false
                    credentialHandled.set(false)
                },
            )
        }
    }

    AuthWebViewScreen(
        navController = navController,
        title = stringResource(R.string.qobuz_login),
        subtitle = stringResource(R.string.auth_webview_qobuz_subtitle),
        factory = { ctx ->
            WebView(ctx).apply {
                webViewClient =
                    object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) {
                            if (url?.contains("qobuz.com", ignoreCase = true) == true) {
                                view.loadUrl(QOBUZ_HOOK_JS)
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
                        fun onCredentials(token: String?, appId: String?) {
                            if (token.isNullOrBlank() || appId.isNullOrBlank()) return
                            if (!credentialHandled.compareAndSet(false, true)) return
                            scope.launch { onCredentialsReceived(token, appId) }
                        }

                        @JavascriptInterface
                        fun onSecret(secret: String?) {
                            if (secret.isNullOrBlank() || secret.length != 32) return
                            scope.launch { onSecretReceived(secret) }
                        }
                    },
                    "QobuzAuth",
                )
                resetAuthWebViewSession(ctx, this, clearCookies = true) {
                    loadUrl(QOBUZ_WEB_PLAYER_URL)
                }
            }
        },
    )

    // After credentials are held for a bit without a secret arriving, surface the fallback dialog.
    // We use a LaunchedEffect with a 4-second timeout rather than making the user wait forever.
    captured?.let {
        androidx.compose.runtime.LaunchedEffect(it) {
            kotlinx.coroutines.delay(4_000)
            if (scrapedSecret == null && !showSecretDialog) {
                showSecretDialog = true
            }
        }
    }
}
