/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import android.content.Context
import android.content.res.Configuration
import android.util.Log
import kotlinx.coroutines.CancellationException
import java.util.Locale

fun reportException(throwable: Throwable) {
    // CancellationException is the coroutine framework's signal that a job was cancelled (e.g. by
    // `withTimeoutOrNull`). It is NOT an error — it's the normal way coroutines unwind when their
    // parent scope is cancelled.
    if (throwable is CancellationException) return

    // Use android.util.Log instead of throwable.printStackTrace(). printStackTrace() writes to
    // System.err, which Android redirects to logcat as `W/System.err` one line at a time.
    Log.w("ArchiveTune", "reportException", throwable)
}

@Suppress("DEPRECATION")
fun setAppLocale(
    context: Context,
    locale: Locale,
) {
    val config = Configuration(context.resources.configuration)
    config.setLocale(locale)
    context.resources.updateConfiguration(config, context.resources.displayMetrics)
}
