/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import moe.rukamori.archivetune.innertube.YouTube
import timber.log.Timber
import java.net.InetSocketAddress
import java.net.Proxy

/**
 * Routes WebView traffic through the same proxy the InnerTube HTTP stack uses.
 *
 * Without this, `YouTube.proxy` only covered Ktor/OkHttp, so the Google sign-in WebView connected
 * directly. In regions where YouTube is unreachable without a proxy that surfaced as
 * `net::ERR_TIMED_OUT` on `accounts.youtube.com/accounts/SetSID`, making login impossible even
 * though the rest of the app worked.
 *
 * Note: [ProxyController] has no authentication callback, so proxies requiring credentials cannot
 * be honoured here. [requiresUnsupportedAuth] lets callers surface an explanatory message instead
 * of failing opaquely.
 */
object WebViewProxyController {
    private var overrideApplied = false

    /** True when a proxy is configured with credentials, which WebView cannot supply. */
    fun requiresUnsupportedAuth(): Boolean =
        YouTube.proxy != null &&
            (!YouTube.proxyUsername.isNullOrBlank() || !YouTube.proxyPassword.isNullOrBlank())

    /**
     * Mirrors the currently configured [YouTube.proxy] onto the WebView stack. Safe to call
     * repeatedly; a no-op when no proxy is set or the device's WebView is too old to support
     * proxy overrides.
     */
    fun apply() {
        val proxy = YouTube.proxy
        if (proxy == null) {
            clear()
            return
        }
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            Timber.w("WebView proxy override unsupported; sign-in will connect directly")
            return
        }

        val address = proxy.address() as? InetSocketAddress ?: return
        val host = address.hostString ?: return
        val port = address.port.takeIf { it in 1..65535 } ?: return

        // ProxyConfig.addProxyRule only accepts the http, https and socks schemes.
        val scheme =
            when (proxy.type()) {
                Proxy.Type.HTTP -> "http"
                Proxy.Type.SOCKS -> "socks"
                else -> return
            }

        runCatching {
            // Deliberately no addDirect() fallback: that would let WebView silently connect
            // directly when the proxy is unreachable, which is the bypass this exists to close.
            // Localhost and link-local addresses are bypassed implicitly by default.
            val config =
                ProxyConfig
                    .Builder()
                    .addProxyRule("$scheme://$host:$port")
                    .bypassSimpleHostnames()
                    .build()
            ProxyController.getInstance().setProxyOverride(config, { it.run() }, {})
            overrideApplied = true
            Timber.d("[v0] WebView proxy override applied: $scheme://$host:$port")
        }.onFailure { Timber.w(it, "Failed to apply WebView proxy override") }
    }

    /** Removes any override previously installed by [apply]. */
    fun clear() {
        if (!overrideApplied) return
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) return
        runCatching {
            ProxyController.getInstance().clearProxyOverride({ it.run() }, {})
            overrideApplied = false
            Timber.d("[v0] WebView proxy override cleared")
        }.onFailure { Timber.w(it, "Failed to clear WebView proxy override") }
    }
}
