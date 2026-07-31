package moe.rukamori.archivetune.constants

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/*
 * Preference keys for this fork's own features, kept OUT of PreferenceKeys.kt on purpose.
 *
 * The mirror workflow replaces every file upstream also ships, so a key appended to
 * PreferenceKeys.kt is silently dropped by the next sync and the fork code referencing it stops
 * compiling. That already happened once at 21247770f. This file is listed in .mirror-keep, so it
 * survives.
 *
 * Only keys used by fork-only code belong here. Anything upstream also declares must NOT be
 * repeated -- two declarations of the same name is a Kotlin redeclaration error. The four Deezer
 * keys are a case in point: upstream merged this fork's Deezer support and now declares
 * DeezerEnabledKey, DeezerArlKey, DeezerAccountNameKey and DeezerAccountPremiumKey themselves, so
 * they stay in their file and are deliberately absent here.
 *
 * The string passed to booleanPreferencesKey is the on-disk DataStore name. Renaming one orphans
 * the stored setting on every existing install, so treat these as permanent.
 */

/** Whether a lossless download should be tagged with metadata and artwork after it completes. */
val LosslessDownloadTagKey = booleanPreferencesKey("losslessDownloadTag")

// Deezer serves three tiers. FLAC needs a lossless plan, so the provider walks down from the
// requested tier and a free account silently lands on MP3 rather than failing the track.
//
// Unlike the four account keys above, upstream never declared these -- their DeezerSettings is a
// login link with no quality picker -- so they live here where the mirror cannot drop them.
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
