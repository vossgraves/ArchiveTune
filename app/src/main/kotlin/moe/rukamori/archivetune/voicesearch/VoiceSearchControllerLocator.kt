/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.voicesearch

import android.content.Context

/**
 * Process-wide locator for the [VoiceSearchController]. The concrete impl is
 * selected per-flavor via [DefaultVoiceSearchController] (defined in the
 * `gms`/`foss` source sets) — this mirrors the existing
 * `CastPlaybackRepositoryLocator` pattern so we don't need a Hilt module.
 */
object VoiceSearchControllerLocator {
    @Volatile private var instance: VoiceSearchController? = null

    fun get(context: Context): VoiceSearchController =
        instance ?: synchronized(this) {
            // `DefaultVoiceSearchController` is defined per-flavor (gms/foss).
            // At compile time only one flavor is active so the FQN resolves
            // unambiguously to the correct implementation.
            instance ?: DefaultVoiceSearchController().also { instance = it }
        }
}
