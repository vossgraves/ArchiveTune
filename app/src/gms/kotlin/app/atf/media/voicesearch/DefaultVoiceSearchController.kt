/*
 * ArchiveTune (2026)
 * © ArchiveTuneFork contributors — github.com/vossgraves/ArchiveTune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package app.atf.media.voicesearch

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * GMS-flavor voice search implementation.
 *
 * Uses the platform `android.speech.SpeechRecognizer` API directly. On Android
 * 12+ this API uses the on-device Google Speech Recognition service (shipped via
 * Google Play Services as part of the system), so the user does NOT need to
 * install the standalone Google app. On older Android versions the recognizer
 * falls back to whatever speech service the system provides.
 *
 * This implementation is in the `gms` source set because the `gms` flavor
 * already depends on Google Play Services (for Cast). The `foss` flavor uses
 * a no-op impl so FOSS builds don't pull in any GMS dependency.
 */
class DefaultVoiceSearchController : VoiceSearchController {
    private val _state = MutableStateFlow<VoiceSearchState>(VoiceSearchState.Idle)
    override val state: StateFlow<VoiceSearchState> = _state.asStateFlow()

    private var recognizer: SpeechRecognizer? = null

    override fun startListening(context: Context) {
        if (!hasMicPermission(context)) {
            _state.value =
                VoiceSearchState.Error("Microphone permission denied")
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _state.value =
                VoiceSearchState.Error("Speech recognition is not available on this device")
            return
        }

        // Tear down any prior recognizer before starting a new session.
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context)

        val intent =
            android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                )
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                // Prefer the device's current locale.
                val locale = java.util.Locale.getDefault()
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
                // Prefer on-device recognition when available (Android 12+ ships an
                // on-device recognizer that does NOT require the Google app).
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            }

        recognizer?.setRecognitionListener(
            object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _state.value = VoiceSearchState.Listening
                }

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    _state.value =
                        VoiceSearchState.Error(
                            speechErrorMessage(error),
                        )
                    recognizer?.destroy()
                    recognizer = null
                }

                override fun onResults(results: Bundle?) {
                    val matches =
                        results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()?.takeIf { it.isNotBlank() }
                    _state.value =
                        if (text != null) {
                            VoiceSearchState.Result(text)
                        } else {
                            VoiceSearchState.Error("No speech recognized")
                        }
                    recognizer?.destroy()
                    recognizer = null
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    // Partial results are intentionally not surfaced as state —
                    // we only commit a final result to avoid spamming the UI.
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            },
        )

        recognizer?.startListening(intent)
    }

    override fun cancel() {
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        _state.value = VoiceSearchState.Idle
    }

    private fun hasMicPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

    private fun speechErrorMessage(error: Int): String =
        when (error) {
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_CLIENT -> "Client error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech matched"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            else -> "Speech recognition failed"
        }
}
