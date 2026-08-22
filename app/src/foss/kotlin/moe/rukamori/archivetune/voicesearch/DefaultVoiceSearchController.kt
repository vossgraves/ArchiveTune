/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.voicesearch

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * FOSS flavor no-op implementation. FOSS builds have no GMS dependency, so the
 * in-app mic button is disabled and surfaces a friendly error if the user taps it.
 *
 * FOSS users can still voice-search through the system MediaSession voice intent
 * (e.g. "OK Google, play X on ArchiveTune") — that flow is handled in
 * [moe.rukamori.archivetune.playback.MusicService] and does not require any
 * in-app UI.
 */
class DefaultVoiceSearchController : VoiceSearchController {
    private val _state = MutableStateFlow<VoiceSearchState>(VoiceSearchState.Idle)
    override val state: StateFlow<VoiceSearchState> = _state.asStateFlow()

    override fun startListening(context: Context) {
        _state.value =
            VoiceSearchState.Error(
                "Voice search requires the GMS build of ArchiveTune. Use the system " +
                    "voice assistant (e.g. \"OK Google, play X on ArchiveTune\") instead.",
            )
    }

    override fun cancel() {
        _state.value = VoiceSearchState.Idle
    }
}
