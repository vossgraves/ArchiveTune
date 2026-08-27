/*
 * ArchiveTune (2026)
 * © ArchiveTuneFork contributors — github.com/vossgraves/ArchiveTune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package app.atf.media.musicrecognition

import android.util.Base64
import androidx.navigation.NavHostController
import kotlinx.serialization.json.Json

const val MusicRecognitionRoute = "music_recognition"
const val ACTION_MUSIC_RECOGNITION = "app.atf.media.action.MUSIC_RECOGNITION"
const val MusicRecognitionAutoStartRequestKey = "music_recognition_auto_start_request"
const val MusicRecognitionDetailsRoute = "music_recognition/details/{encodedTrack}"

fun NavHostController.openMusicRecognition(autoStartRequestId: Long = System.currentTimeMillis()) {
    val currentRoute = currentDestination?.route
    if (currentRoute != MusicRecognitionRoute && !popBackStack(MusicRecognitionRoute, inclusive = false)) {
        navigate(MusicRecognitionRoute) {
            launchSingleTop = true
        }
    }

    getBackStackEntry(MusicRecognitionRoute).savedStateHandle[MusicRecognitionAutoStartRequestKey] =
        autoStartRequestId
}

fun NavHostController.navigateToMusicRecognitionDetails(track: RecognizedTrack) {
    val jsonString = Json.encodeToString(RecognizedTrack.serializer(), track)
    val encodedTrack = Base64.encodeToString(
        jsonString.toByteArray(Charsets.UTF_8),
        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
    )
    navigate("music_recognition/details/$encodedTrack") {
        launchSingleTop = true
    }
}

fun decodeRecognizedTrack(encodedTrack: String): RecognizedTrack {
    val decodedBytes = Base64.decode(encodedTrack, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    val jsonString = String(decodedBytes, Charsets.UTF_8)
    return Json.decodeFromString(RecognizedTrack.serializer(), jsonString)
}

