package moe.rukamori.archivetune.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

/**
 * Apple serves no contract for either half of the web player token lookup — the page's script tag,
 * or where the token sits inside the bundle it names. Both are pinned here against the shapes that
 * have actually been seen, because each break is silent: a missed bundle name reports "no JS bundle
 * URL", and a mis-selected JWT is accepted as a token with no readable expiry and then refunded on
 * every request as already expired.
 */
class AppleWebPlayTokenTest {
    private val nowSec = 1_800_000_000L

    @Test
    fun `the entry chunk is read from the markup apple serves now`() {
        val html =
            """
            <!doctype html><html><head>
            <script type="module" crossorigin src="/assets/index~8bc3c631ba.js"></script>
            <script nomodule crossorigin id="vite-legacy-entry" data-src="/assets/index-legacy~17528a465c.js"></script>
            </head></html>
            """.trimIndent()

        assertEquals(
            listOf(
                "https://music.apple.com/assets/index~8bc3c631ba.js",
                "https://music.apple.com/assets/index-legacy~17528a465c.js",
            ),
            AppleWebPlayToken.bundleUrls(html),
        )
    }

    @Test
    fun `the older hyphenated entry chunk is still read`() {
        val html = """<script type="module" src="/assets/index-4f2a91bc.js"></script>"""

        assertEquals(listOf("https://music.apple.com/assets/index-4f2a91bc.js"), AppleWebPlayToken.bundleUrls(html))
    }

    @Test
    fun `a page with no script reference yields no bundle`() {
        assertEquals(emptyList<String>(), AppleWebPlayToken.bundleUrls("<html><body>Nothing here</body></html>"))
    }

    @Test
    fun `the token is taken from a bundle that carries several`() {
        val bundle =
            """const a="${jwt("M62YD85FTQ", nowSec + 86_400)}",b="${jwt("5IKPP2IECQ", nowSec + 86_400)}",""" +
                """c="${jwt("AMPWebPlay", nowSec + 5_000_000)}";"""

        assertEquals(jwt("AMPWebPlay", nowSec + 5_000_000), AppleWebPlayToken.select(bundle, nowSec))
    }

    @Test
    fun `a bundle holding only other services' tokens yields nothing`() {
        val bundle = """const a="${jwt("M62YD85FTQ", nowSec + 86_400)}",b="${jwt("5IKPP2IECQ", nowSec + 86_400)}";"""

        assertNull(AppleWebPlayToken.select(bundle, nowSec))
    }

    @Test
    fun `a jwt shaped string with no readable expiry is not a token`() {
        // This is what minted an "already expired" token: a JWT-shaped literal whose payload is
        // not a claim set, so there is no `exp` to compare against.
        val bundle = """const a="eyJhbGciOiJub25lIn0.eyJub3RfYV9jbGFpbV9zZXQiOjF9.c2lnbmF0dXJl";"""

        assertNull(AppleWebPlayToken.select(bundle, nowSec))
        assertEquals(0L, AppleWebPlayToken.expSec("eyJhbGciOiJub25lIn0.eyJub3RfYV9jbGFpbV9zZXQiOjF9.c2lnbmF0dXJl"))
    }

    @Test
    fun `an expired web player token is rejected`() {
        val expired = jwt("AMPWebPlay", nowSec - 1)

        assertEquals("AMPWebPlay", AppleWebPlayToken.issuer(expired))
        assertEquals(nowSec - 1, AppleWebPlayToken.expSec(expired))
        assertNull(AppleWebPlayToken.select("""const a="$expired";""", nowSec))
    }

    @Test
    fun `a landing page carrying no token falls through to its bundle`() {
        val html = """<script type="module" src="/assets/index~8bc3c631ba.js"></script>"""

        assertNull(AppleWebPlayToken.select(html, nowSec))
        assertEquals(listOf("https://music.apple.com/assets/index~8bc3c631ba.js"), AppleWebPlayToken.bundleUrls(html))
    }

    private fun jwt(
        issuer: String,
        expSec: Long,
    ): String =
        listOf(
            base64Url("""{"alg":"ES256","kid":"WebPlayKid"}"""),
            base64Url("""{"iss":"$issuer","iat":${expSec - 5_000_000},"exp":$expSec}"""),
            "c2lnbmF0dXJlLWJ5dGVz",
        ).joinToString(".")

    private fun base64Url(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray())
}
