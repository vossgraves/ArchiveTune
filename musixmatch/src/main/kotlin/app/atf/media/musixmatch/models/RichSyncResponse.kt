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
data class RichSyncResponse(
    val message: RichSyncMessage = RichSyncMessage(),
)

@Serializable
data class RichSyncMessage(
    val header: MusixmatchHeader = MusixmatchHeader(),
    val body: RichSyncBody = RichSyncBody(),
)

@Serializable
data class RichSyncBody(
    val richsync: RichSync? = null,
)

@Serializable
data class RichSync(
    @SerialName("richsync_body")
    val richsyncBody: String? = null,
)

@Serializable
data class RichSyncLine(
    @SerialName("ts")
    val startTime: Double = 0.0,
    @SerialName("te")
    val endTime: Double = 0.0,
    @SerialName("l")
    val words: List<RichSyncWord> = emptyList(),
    @SerialName("x")
    val text: String? = null,
)

@Serializable
data class RichSyncWord(
    @SerialName("c")
    val text: String = "",
    @SerialName("o")
    val offset: Double = 0.0,
)
