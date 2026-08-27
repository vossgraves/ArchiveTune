/*
 * ArchiveTune (2026)
 * © ArchiveTuneFork contributors — github.com/vossgraves/ArchiveTune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package app.atf.media.backup

import android.net.Uri

enum class ScheduledBackupFrequency {
    DAILY,
    WEEKLY,
    MONTHLY,
    CUSTOM,
}

data class ScheduledBackupSettings(
    val enabled: Boolean = false,
    val frequency: ScheduledBackupFrequency = ScheduledBackupFrequency.WEEKLY,
    val customDateEpochDay: Long? = null,
    val directoryUri: Uri? = null,
    val directoryName: String? = null,
    val overwriteExisting: Boolean = false,
)
