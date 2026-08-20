/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.lastfm

/**
 * ArchiveTune's baked-in Last.fm API application credentials.
 *
 * Ported from LastWave-native's `LastFmAppCredentials` so that signing in via the web auth
 * flow doesn't require the user to find or paste an API key/secret — the app-level key
 * identifies the APPLICATION to Last.fm (not the individual person signing in); each
 * person's identity comes from the session key they get during their own sign-in
 * (see LastFM.getSession).
 *
 * These are the same credentials LastWave-native uses, registered at last.fm/api/account/create
 * under the app name "LastWave". Using them here means users signing into ArchiveTune get
 * the same one-tap sign-in experience LastWave users get.
 */
object LastFmAppCredentials {
    const val API_KEY = "e2c8e7a67eaeb0fe5a71ee539a34641a"
    const val API_SECRET = "94b5c6aa634e459defedbf8180625e8a"

    /** Custom-scheme callback URI registered with Last.fm. The WebView intercepts this. */
    const val AUTH_CALLBACK_URI = "archivetune://lastfm-auth-callback"

    /**
     * Builds the Last.fm web auth URL. The user approves the app in the browser / WebView,
     * then Last.fm redirects to [AUTH_CALLBACK_URI] with a `token` query parameter.
     */
    fun authUrl(): String =
        android.net.Uri
            .parse("https://www.last.fm/api/auth/")
            .buildUpon()
            .appendQueryParameter("api_key", API_KEY)
            .appendQueryParameter("cb", AUTH_CALLBACK_URI)
            .build()
            .toString()
}
