/*
 * ArchiveTune (2026)
 * © ArchiveTuneFork contributors — github.com/vossgraves/ArchiveTune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package app.atf.media.aicontentfilter

import androidx.compose.runtime.Immutable

@Immutable
data class AiContentFilterSettings(
    val enabled: Boolean,
    val includeModerateConfidence: Boolean,
)

@Immutable
data class AiContentFilterStatus(
    val blocklistCount: Int,
    val warnlistCount: Int,
    val lastUpdatedEpochMillis: Long,
)

data class AiContentFilterPolicy(
    val enabled: Boolean,
    val blockedChannelKeys: Set<String>,
) {
    companion object {
        val Disabled =
            AiContentFilterPolicy(
                enabled = false,
                blockedChannelKeys = emptySet(),
            )
    }
}

data class AiChannelLists(
    val blocklist: Set<String>,
    val warnlist: Set<String>,
)

sealed interface AiContentFilterRefreshResult {
    data class Success(
        val status: AiContentFilterStatus,
    ) : AiContentFilterRefreshResult

    data object Unavailable : AiContentFilterRefreshResult
}
