/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.svg

import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import moe.rukamori.archivetune.ui.theme.palette.TonalPalettes

object SVGString

// Hoisted file-level Regex patterns: parseDynamicColor is called per SVG
// icon invalidation, and the inner split regex was being recompiled once
// per fill-attribute match in each SVG string (could be dozens for an
// icon-heavy SVG). Compiling once at class-load avoids that per-call
// allocation. Same patterns, same replacement semantics.
private val SVG_FILL_ATTR_REGEX = Regex("fill=\"(.+?)\"")
private val SVG_SCHEME_TONE_SPLIT_REGEX = Regex("(?<=\\d)(?=\\D)|(?=\\d)(?<=\\D)")

fun String.parseDynamicColor(
    tonalPalettes: TonalPalettes,
    isDarkTheme: Boolean,
): String =
    replace(SVG_FILL_ATTR_REGEX) {
        val value = it.groupValues[1]
        Log.i("RLog", "parseDynamicColor: $value")
        if (value.startsWith("#")) return@replace it.value
        try {
            val (scheme, tone) = value.split(SVG_SCHEME_TONE_SPLIT_REGEX)
            val argb =
                when (scheme) {
                    "p" -> tonalPalettes.primary[tone.toInt().autoToDarkTone(isDarkTheme)]
                    "s" -> tonalPalettes.secondary[tone.toInt().autoToDarkTone(isDarkTheme)]
                    "t" -> tonalPalettes.tertiary[tone.toInt().autoToDarkTone(isDarkTheme)]
                    "n" -> tonalPalettes.neutral[tone.toInt().autoToDarkTone(isDarkTheme)]
                    "nv" -> tonalPalettes.neutralVariant[tone.toInt().autoToDarkTone(isDarkTheme)]
                    "e" -> tonalPalettes.error[tone.toInt().autoToDarkTone(isDarkTheme)]
                    else -> Color.Transparent
                }?.toArgb() ?: 0xFFFFFF
            "fill=\"${String.format("#%06X", 0xFFFFFF and argb)}\""
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("RLog", "parseDynamicColor: ${e.message}")
            it.value
        }
    }

internal fun Int.autoToDarkTone(isDarkTheme: Boolean): Int =
    if (!isDarkTheme) {
        this
    } else {
        when (this) {
            10 -> 99
            20 -> 95
            25 -> 90
            30 -> 90
            40 -> 80
            50 -> 60
            60 -> 50
            70 -> 40
            80 -> 40
            90 -> 30
            95 -> 20
            98 -> 10
            99 -> 10
            100 -> 20
            else -> this
        }
    }
