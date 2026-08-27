/*
 * ArchiveTune (2026)
 * © ArchiveTuneFork contributors — github.com/vossgraves/ArchiveTune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package app.atf.media.musixmatch.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TokenResponse(
    val message: TokenMessage = TokenMessage(),
)

@Serializable
data class TokenMessage(
    val header: MusixmatchHeader = MusixmatchHeader(),
    val body: TokenBody = TokenBody(),
)

@Serializable
data class TokenBody(
    @SerialName("user_token")
    val userToken: String? = null,
)
