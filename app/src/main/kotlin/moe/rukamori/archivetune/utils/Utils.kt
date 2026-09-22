/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 * Portions © vossgraves — github.com/vossgraves
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

/**
 * The storefront to ask Apple Music or Spotify with when no account-bound one is known: the device
 * locale's two-letter region, lowercased, falling back to `us` for a language-only locale or one the
 * catalogue has no entry for. Apple Music takes it as a catalogue path segment, so it has to be a
 * bare region code rather than a full locale tag.
 */
fun defaultStorefront(): String {
    val country = Locale.getDefault().country
    return if (country.length == 2) country.lowercase(Locale.ROOT) else "us"
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
