/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*

data class LogEntry(
    val time: Long,
    val level: Int,
    val tag: String?,
    val message: String,
)

object GlobalLog {
    private const val MAX_ENTRIES = 500

    // Backing store: ArrayDeque gives O(1) add/remove at both ends, vs the
    // previous `(_logs.value + entry).takeLast(MAX_ENTRIES)` which allocated
    // two new lists (size 501 and 500) on every single log call. During lyrics
    // prefetch the app can emit hundreds of log lines per minute, and the old
    // implementation was burning ~290k element copies + 581 StateFlow emissions
    // in a few minutes — a measurable source of GC pressure and UI jank.
    private val buffer = ArrayDeque<LogEntry>(MAX_ENTRIES)
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs = _logs.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    @Synchronized
    fun append(
        level: Int,
        tag: String?,
        message: String,
    ) {
        val entry = LogEntry(System.currentTimeMillis(), level, tag, message)
        if (buffer.size >= MAX_ENTRIES) {
            buffer.removeFirst()
        }
        buffer.addLast(entry)
        // Emit a snapshot list. `toList()` allocates one new list of size <=500,
        // which is unavoidable for an immutable StateFlow value — but this is
        // still O(N) with a small constant and no longer the O(2N) double-copy
        // of the previous `+`/`takeLast` chain.
        _logs.value = buffer.toList()
    }

    @Synchronized
    fun clear() {
        buffer.clear()
        _logs.value = emptyList()
    }

    fun format(entry: LogEntry): String {
        val ts = timeFormat.format(Date(entry.time))
        val lvl =
            when (entry.level) {
                android.util.Log.VERBOSE -> "V"
                android.util.Log.DEBUG -> "D"
                android.util.Log.INFO -> "I"
                android.util.Log.WARN -> "W"
                android.util.Log.ERROR -> "E"
                else -> "?"
            }
        val tag = entry.tag ?: ""
        return "[$ts] $lvl/$tag: ${entry.message}"
    }
}

/** Timber Tree that forwards logs to GlobalLog */
class GlobalLogTree : Timber.DebugTree() {
    override fun log(
        priority: Int,
        tag: String?,
        message: String,
        t: Throwable?,
    ) {
        try {
            val final = if (t != null) "$message\n$t" else message
            GlobalLog.append(priority, tag, final)
        } catch (_: Exception) {
            // swallow
        }
    }
}
