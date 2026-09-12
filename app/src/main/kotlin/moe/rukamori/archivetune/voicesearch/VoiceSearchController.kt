/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.voicesearch

import android.content.Context
import kotlinx.coroutines.flow.StateFlow

/** Cross-flavor voice search abstraction. */
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
