/*
 * ArchiveTune (2026)
 * © vossgraves — github.com/vossgraves
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.canvas

/**
 * Finds the Apple Music web player JWT in the pages Apple serves.
 *
 * The token is not in the landing page's own markup: that page loads `/assets/index~<hash>.js`,
 * and the token is a string literal inside that bundle. Two properties of it have broken this app
 * before, so both are pinned here instead of being open-coded at each call site:
 *
 *  - Apple renames the entry chunk. `index-<hash>.js` became `index~<hash>.js`, and a pattern that
 *    accepts only the old separator finds no bundle at all and reports "no JS bundle URL".
 *  - A JWT-shaped string is not necessarily the token. The bundle also carries tokens for other
 *    services, and a string whose payload will not decode has no `exp` to read — accepting one
 *    mints an "already expired" token that is refreshed on every single use. Only a token issued
 *    by `AMPWebPlay` with a readable expiry still in the future is accepted; anything else means
 *    no token, which callers report rather than spending a request that can only 401.
 */
object AppleWebPlayToken {
    /** The `iss` claim of every web playback token `amp-api.music.apple.com` accepts. */
    private const val ISSUER = "AMPWebPlay"

    // A candidate has to outlive the request that prompted the refresh, not merely the clock.
    private const val MIN_LIFETIME_SEC = 60L

    // At most this many bundles are fetched before giving up: the entry chunk is normally first,
    // and each miss costs a multi-megabyte download on the user's connection.
    private const val MAX_BUNDLE_CANDIDATES = 4

    // Apple Music web player JWTs are ES256-signed with 3 base64url segments.
    private val JWT_REGEX = Regex("""eyJ[A-Za-z0-9_-]{8,}\.eyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}""")

    // The entry chunk is `index~<hash>.js`; older builds used `index-<hash>.js`, and the `nomodule`
    // copy of the same bundle is referenced through `data-src` (whose tail is still `src=`).
    private val INDEX_BUNDLE_REGEX =
        Regex("""(?:src|href)=["']([^"']*index[~\-][A-Za-z0-9._~-]+\.js)["']""")

    // Any module script, for the day the entry chunk stops being called `index…` altogether.
    private val ANY_BUNDLE_REGEX =
        Regex("""(?:src|data-src)=["']([^"']*/assets/[^"']+\.js)["']""")

    // Both claim patterns must end on the capture group, never on a quote: in a raw string, a
    // trailing `"` needs four quotes to close, and the fourth becomes part of the pattern. That
    // asked for a quote after the digits, which no claim set has, so every token — including a
    // live one — decoded to `exp = 0` and was refreshed on every single use.
    private val ISSUER_CLAIM_REGEX = Regex(""""iss"\s*:\s*"([^"]+)""")
    private val EXP_CLAIM_REGEX = Regex(""""exp"\s*:\s*(\d+)""")

    /**
     * JavaScript bundles [html] loads, entry chunk first, as absolute URLs. A bundle that yields no
     * token is harmless, so the order only decides which one is tried first.
     */
    fun bundleUrls(html: String): List<String> {
        val indexBundles = INDEX_BUNDLE_REGEX.findAll(html).map { it.groupValues[1] }
        val anyBundles = ANY_BUNDLE_REGEX.findAll(html).map { it.groupValues[1] }
        return (indexBundles + anyBundles)
            .distinct()
            .map(::absoluteUrl)
            .take(MAX_BUNDLE_CANDIDATES)
            .toList()
    }

    /**
     * The `AMPWebPlay` token in [text] that is still valid at [nowSec], or null when [text] holds
     * none. [nowSec] is supplied by the caller so this stays testable without a clock.
     */
    fun select(
        text: String,
        nowSec: Long,
    ): String? =
        JWT_REGEX
            .findAll(text)
            .map { it.value }
            .distinct()
            .firstOrNull { jwt -> issuer(jwt) == ISSUER && expSec(jwt) > nowSec + MIN_LIFETIME_SEC }

    /** The `iss` claim of [jwt], or null when it is not a JWT with a decodable payload. */
    fun issuer(jwt: String): String? = payload(jwt)?.let { ISSUER_CLAIM_REGEX.find(it)?.groupValues?.get(1) }

    /** The `exp` claim of [jwt] in epoch seconds, or 0 when it is absent or undecodable. */
    fun expSec(jwt: String): Long =
        payload(jwt)
            ?.let { EXP_CLAIM_REGEX.find(it)?.groupValues?.get(1) }
            ?.toLongOrNull()
            ?: 0L

    private fun payload(jwt: String): String? {
        val parts = jwt.split(".")
        if (parts.size != 3) return null
        return runCatching {
            val normalized = parts[1].replace('-', '+').replace('_', '/')
            val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
            String(java.util.Base64.getDecoder().decode(padded), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun absoluteUrl(rawUrl: String): String =
        when {
            rawUrl.startsWith("http") -> rawUrl
            rawUrl.startsWith("//") -> "https:$rawUrl"
            rawUrl.startsWith("/") -> "https://music.apple.com$rawUrl"
            else -> "https://music.apple.com/$rawUrl"
        }
}
