/*
 * ArchiveTune (2026)
 * © ArchiveTuneFork contributors — github.com/vossgraves/ArchiveTune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package app.atf.media.musicrecognition

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import app.atf.media.MainActivity
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MusicRecognitionTileActionActivity : ComponentActivity() {
    @Inject
    lateinit var isBackgroundRecognitionEnabled: IsBackgroundRecognitionEnabledUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            val backgroundRecognitionEnabled =
                try {
                    isBackgroundRecognitionEnabled()
                } catch (cancellationException: CancellationException) {
                    throw cancellationException
                } catch (throwable: Throwable) {
                    Timber.e(throwable, "Failed to read background recognition setting")
                    false
                }

            val destination =
                if (backgroundRecognitionEnabled) {
                    Intent(
                        this@MusicRecognitionTileActionActivity,
                        MusicRecognitionCaptureActivity::class.java,
                    )
                } else {
                    Intent(
                        this@MusicRecognitionTileActionActivity,
                        MainActivity::class.java,
                    ).apply {
                        action = ACTION_MUSIC_RECOGNITION
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                }
            startActivity(destination)
            finish()
            overridePendingTransition(0, 0)
        }
    }
}
