/*
 * ArchiveTune (2026)
 * © ArchiveTuneFork contributors — github.com/vossgraves/ArchiveTune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package app.atf.media.utils

import android.content.Context
import android.content.res.Configuration
import android.util.Log
import kotlinx.coroutines.CancellationException
import java.util.Locale

fun reportException(throwable: Throwable) {
    // CancellationException is the coroutine framework's signal that a job was
    // cancelled (e.g. by `withTimeoutOrNull`). It is NOT an error — it's the
    // normal way coroutines unwind when their parent scope is cancelled.
    //
    // Routing it through Log.w as if it were a real exception produces noisy
    // logcat entries like `W/ArchiveTune: r8.fpb: Timed out waiting for 8000 ms`
    // for every lyrics provider that exceeds its per-provider timeout, even
    // though the timeout is the expected code path (the provider is simply
    // dropped from ranking and the next provider takes over).
    //
    // Filter it out here as defense-in-depth: even if a downstream caller
    // accidentally routes a CancellationException to reportException (which
    // can happen when a stdlib `runCatching` block catches it — see the
    // PaxsenixLyrics `runSuspendCatching` helper for the root-cause fix), we
    // don't pollute logcat with framework-internal cancellation noise.
    if (throwable is CancellationException) return

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
