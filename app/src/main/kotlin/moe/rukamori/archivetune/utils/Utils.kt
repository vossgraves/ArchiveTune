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
import java.util.Locale

fun reportException(throwable: Throwable) {
    // Use android.util.Log instead of throwable.printStackTrace().
    //
    // printStackTrace() writes to System.err, which Android redirects to logcat
    // as `W/System.err` one line at a time. Each call:
    //   - synchronizes on System.err (blocking concurrent callers)
    //   - allocates a ByteArrayOutputStream + PrintStream + char[] buffer
    //   - formats every stack frame as a separate string + logcat call
    //
    // During lyrics prefetch this is called dozens of times per minute (once
    // per failing provider), and the synchronized I/O + allocation churn was a
    // measurable source of GC pressure and UI jank. Log.w routes through
    // Android's native logging pipeline, which is lock-free, batches the stack
    // trace into a single logcat call, and is properly filtered by log level.
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
