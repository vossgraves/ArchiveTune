package moe.rukamori.archivetune.constants

import androidx.datastore.preferences.core.stringPreferencesKey

/*
 * Preference keys for the Deezer playback source, kept OUT of PreferenceKeys.kt on purpose so
 * they cannot collide with upstream's declarations there. The four Deezer account keys
 * (DeezerEnabledKey, DeezerArlKey, DeezerAccountNameKey, DeezerAccountPremiumKey) are already
 * declared in PreferenceKeys.kt and are deliberately not repeated here.
 *
 * The string passed to stringPreferencesKey is the on-disk DataStore name. Renaming it orphans
 * the stored setting on every existing install, so treat it as permanent.
 */

// Deezer serves three tiers. FLAC needs a lossless plan, so the provider walks down from the
// requested tier and a free account silently lands on MP3 rather than failing the track.
enum class DeezerAudioQuality {
    FLAC,
    MP3_320,
    MP3_128,
}

val DeezerAudioQualityOptions =
    listOf(
        DeezerAudioQuality.FLAC,
        DeezerAudioQuality.MP3_320,
        DeezerAudioQuality.MP3_128,
    )

val DeezerAudioQualityKey = stringPreferencesKey("deezerAudioQuality")

/** The `format` string Deezer's media endpoint expects for each tier. */
fun DeezerAudioQuality.toFormatName(): String =
    when (this) {
        DeezerAudioQuality.FLAC -> "FLAC"
        DeezerAudioQuality.MP3_320 -> "MP3_320"
        DeezerAudioQuality.MP3_128 -> "MP3_128"
    }
