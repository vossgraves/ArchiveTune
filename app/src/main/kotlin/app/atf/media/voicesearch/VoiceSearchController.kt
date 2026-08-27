/*
 * ArchiveTune (2026)
 * © ArchiveTuneFork contributors — github.com/vossgraves/ArchiveTune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package app.atf.media.voicesearch

import android.content.Context
import kotlinx.coroutines.flow.StateFlow

/**
 * Cross-flavor voice search abstraction.
 *
 * - `gms` flavor uses `com.google.android.gms:play-services-speech` (on-device
 *   SpeechRecognizer backed by Google Play Services — does NOT require the
 *   standalone Google app to be installed).
 * - `foss` flavor uses a no-op implementation (FOSS builds have no GMS dependency).
 *
 * The UI calls [startListening] and observes [state]; when recognition completes
 * successfully the recognized text is emitted via [StateFlow] and the search bar
 * can route it through the existing play-from-voice-search pipeline.
 */
interface VoiceSearchController {
    val state: StateFlow<VoiceSearchState>

    fun startListening(context: Context)

    fun cancel()
}

sealed interface VoiceSearchState {
    data object Idle : VoiceSearchState

    data object Listening : VoiceSearchState

    data class Error(val message: String) : VoiceSearchState

    data class Result(val text: String) : VoiceSearchState
}
