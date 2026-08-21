/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.lyrics

import android.icu.text.Transliterator
import android.text.format.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.betterlyrics.QRCParser
import moe.rukamori.archivetune.betterlyrics.TTMLParser
import moe.rukamori.archivetune.db.entities.LyricsEntity
import java.lang.Character.UnicodeScript

data class LyricsRomanizationPreferences(
    val romanizeJapanese: Boolean,
    val romanizeKorean: Boolean,
    val romanizeChinese: Boolean,
    val romanizeHindi: Boolean,
    val romanizeOther: Boolean,
) {
    val isEnabled: Boolean
        get() = romanizeJapanese || romanizeKorean || romanizeChinese || romanizeHindi || romanizeOther
}

@Suppress("RegExpRedundantEscape")
object LyricsUtils {
    val LINE_REGEX = Regex("""((\[\d{1,3}:\d{2}(?:[.:]\d{2,3})?\]\s*)+)(.*)""")
    val TIME_REGEX = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{2,3}))?\]""")
    private val WHITESPACE_REGEX = "\\s+".toRegex()
    private val ENHANCED_LRC_WORD_TIME_REGEX = Regex("""<\d{1,3}:\d{2}(?:[.:]\d{2,3})?>""")
    private val INLINE_MILLISECONDS_TIME_REGEX = Regex("""<\d{1,8}(?:,\d{1,8})?>""")
    private val YRC_LINE_REGEX = Regex("""\[(\d{1,8}),\d{1,8}\](.*)""")
    private val YRC_WORD_TIME_REGEX = Regex("""\(\d{1,8},\d{1,8}(?:,\d{1,8})?\)""")
    private val QrcTranslationLineRegex = Regex("""^\[(\d{1,8}),(\d{1,8})](.*)$""")
    private val QrcWordTimingDetectRegex = Regex("""\(\d{1,8},\d{1,8}(?:,\d{1,8})?\)""")
    // Matches the leading timestamp prefix of any LRC/QRC/YRC line. Used by
    // `hasTranslation()` to detect duplicate prefixes (which indicate the
    // translator appended a translated line under the same timestamp).
    private val SyncedLinePrefixRegex = Regex("""^(\s*(?:\[[^\]]+])+)(\s*)(.*?)(\s*)$""")
    private val TTML_SPAN_REGEX =
        Regex(
            pattern = """<span\b[^>]*>""",
            options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
    private val TTML_BEGIN_ATTRIBUTE_REGEX = Regex("""\bbegin\s*=""", RegexOption.IGNORE_CASE)
    private val TTML_END_ATTRIBUTE_REGEX = Regex("""\b(?:end|dur)\s*=""", RegexOption.IGNORE_CASE)
    private val INVISIBLE_CHARS_REGEX = Regex("""[\u200B\u200C\u200D\u2060\u00AD]""")
    private const val NBSP = '\u00A0'
    private const val GENERIC_ROMANIZATION_TRANSFORM = "Any-Latin; Latin-ASCII"
    private val OTHER_ROMANIZATION_EXCLUDED_SCRIPTS =
        setOf(
            UnicodeScript.LATIN,
            UnicodeScript.COMMON,
            UnicodeScript.INHERITED,
            UnicodeScript.HAN,
            UnicodeScript.HIRAGANA,
            UnicodeScript.KATAKANA,
            UnicodeScript.HANGUL,
            UnicodeScript.DEVANAGARI,
        )
    private val genericRomanizationTransliterator =
        ThreadLocal.withInitial {
            Transliterator.getInstance(GENERIC_ROMANIZATION_TRANSFORM)
        }

    private val KANA_ROMAJI_MAP: Map<String, String> =
        mapOf(
            // Digraphs (Yōon - combinations like kya, sho)
            "キャ" to "kya",
            "キュ" to "kyu",
            "キョ" to "kyo",
            "シャ" to "sha",
            "シュ" to "shu",
            "ショ" to "sho",
            "チャ" to "cha",
            "チュ" to "chu",
            "チョ" to "cho",
            "ニャ" to "nya",
            "ニュ" to "nyu",
            "ニョ" to "nyo",
            "ヒャ" to "hya",
            "ヒュ" to "hyu",
            "ヒョ" to "hyo",
            "ミャ" to "mya",
            "ミュ" to "myu",
            "ミョ" to "myo",
            "リャ" to "rya",
            "リュ" to "ryu",
            "リョ" to "ryo",
            "ギャ" to "gya",
            "ギュ" to "gyu",
            "ギョ" to "gyo",
            "ジャ" to "ja",
            "ジュ" to "ju",
            "ジョ" to "jo",
            "ヂャ" to "ja",
            "ヂュ" to "ju",
            "ヂョ" to "jo", // ヂ variants, also commonly 'ja', 'ju', 'jo'
            "ビャ" to "bya",
            "ビュ" to "byu",
            "ビョ" to "byo",
            "ピャ" to "pya",
            "ピュ" to "pyu",
            "ピョ" to "pyo",
            // Basic Katakana Characters
            "ア" to "a",
            "イ" to "i",
            "ウ" to "u",
            "エ" to "e",
            "オ" to "o",
            "カ" to "ka",
            "キ" to "ki",
            "ク" to "ku",
            "ケ" to "ke",
            "コ" to "ko",
            "サ" to "sa",
            "シ" to "shi",
            "ス" to "su",
            "セ" to "se",
            "ソ" to "so",
            "タ" to "ta",
            "チ" to "chi",
            "ツ" to "tsu",
            "テ" to "te",
            "ト" to "to",
            "ナ" to "na",
            "ニ" to "ni",
            "ヌ" to "nu",
            "ネ" to "ne",
            "ノ" to "no",
            "ハ" to "ha",
            "ヒ" to "hi",
            "フ" to "fu",
            "ヘ" to "he",
            "ホ" to "ho",
            "マ" to "ma",
            "ミ" to "mi",
            "ム" to "mu",
            "メ" to "me",
            "モ" to "mo",
            "ヤ" to "ya",
            "ユ" to "yu",
            "ヨ" to "yo",
            "ラ" to "ra",
            "リ" to "ri",
            "ル" to "ru",
            "レ" to "re",
            "ロ" to "ro",
            "ワ" to "wa",
            "ヲ" to "o", // ヲ is pronounced 'o'
            "ン" to "n",
            // Dakuten (voiced consonants)
            "ガ" to "ga",
            "ギ" to "gi",
            "グ" to "gu",
            "ゲ" to "ge",
            "ゴ" to "go",
            "ザ" to "za",
            "ジ" to "ji",
            "ズ" to "zu",
            "ゼ" to "ze",
            "ゾ" to "zo",
            "ダ" to "da",
            "ヂ" to "ji",
            "ヅ" to "zu",
            "デ" to "de",
            "ド" to "do", // ヂ and ヅ are often 'ji' and 'zu'
            // Handakuten (p-sounds for 'h' group) / Dakuten for 'h' group
            "バ" to "ba",
            "ビ" to "bi",
            "ブ" to "bu",
            "ベ" to "be",
            "ボ" to "bo", // Dakuten for ハ행 (ha-row)
            "パ" to "pa",
            "ピ" to "pi",
            "プ" to "pu",
            "ペ" to "pe",
            "ポ" to "po", // Handakuten for ハ행 (ha-row)
            // Chōonpu (long vowel mark) - removed as per original logic
            "ー" to "",
        )

    private val HANGUL_ROMAJA_MAP: Map<String, Map<String, String>> =
        mapOf(
            "cho" to
                mapOf(
                    "ᄀ" to "g",
                    "ᄁ" to "kk",
                    "ᄂ" to "n",
                    "ᄃ" to "d",
                    "ᄄ" to "tt",
                    "ᄅ" to "r",
                    "ᄆ" to "m",
                    "ᄇ" to "b",
                    "ᄈ" to "pp",
                    "ᄉ" to "s",
                    "ᄊ" to "ss",
                    "ᄋ" to "",
                    "ᄌ" to "j",
                    "ᄍ" to "jj",
                    "ᄎ" to "ch",
                    "ᄏ" to "k",
                    "ᄐ" to "t",
                    "ᄑ" to "p",
                    "ᄒ" to "h",
                ),
            "jung" to
                mapOf(
                    "ᅡ" to "a",
                    "ᅢ" to "ae",
                    "ᅣ" to "ya",
                    "ᅤ" to "yae",
                    "ᅥ" to "eo",
                    "ᅦ" to "e",
                    "ᅧ" to "yeo",
                    "ᅨ" to "ye",
                    "ᅩ" to "o",
                    "ᅪ" to "wa",
                    "ᅫ" to "wae",
                    "ᅬ" to "oe",
                    "ᅭ" to "yo",
                    "ᅮ" to "u",
                    "ᅯ" to "wo",
                    "ᅰ" to "we",
                    "ᅱ" to "wi",
                    "ᅲ" to "yu",
                    "ᅳ" to "eu",
                    "ᅴ" to "eui",
                    "ᅵ" to "i",
                ),
            "jong" to
                mapOf(
                    "ᆨ" to "k",
                    "ᆨᄋ" to "g",
                    "ᆨᄂ" to "ngn",
                    "ᆨᄅ" to "ngn",
                    "ᆨᄆ" to "ngm",
                    "ᆨᄒ" to "kh",
                    "ᆩ" to "kk",
                    "ᆩᄋ" to "kg",
                    "ᆩᄂ" to "ngn",
                    "ᆩᄅ" to "ngn",
                    "ᆩᄆ" to "ngm",
                    "ᆩᄒ" to "kh",
                    "ᆪ" to "k",
                    "ᆪᄋ" to "ks",
                    "ᆪᄂ" to "ngn",
                    "ᆪᄅ" to "ngn",
                    "ᆪᄆ" to "ngm",
                    "ᆪᄒ" to "kch",
                    "ᆫ" to "n",
                    "ᆫᄅ" to "ll",
                    "ᆬ" to "n",
                    "ᆬᄋ" to "nj",
                    "ᆬᄂ" to "nn",
                    "ᆬᄅ" to "nn",
                    "ᆬᄆ" to "nm",
                    "ᆬㅎ" to "nch",
                    "ᆭ" to "n",
                    "ᆭᄋ" to "nh",
                    "ᆭᄅ" to "nn",
                    "ᆮ" to "t",
                    "ᆮᄋ" to "d",
                    "ᆮᄂ" to "nn",
                    "ᆮᄅ" to "nn",
                    "ᆮᄆ" to "nm",
                    "ᆮᄒ" to "th",
                    "ᆯ" to "l",
                    "ᆯᄋ" to "r",
                    "ᆯᄂ" to "ll",
                    "ᆯᄅ" to "ll",
                    "ᆰ" to "k",
                    "ᆰᄋ" to "lg",
                    "ᆰᄂ" to "ngn",
                    "ᆰᄅ" to "ngn",
                    "ᆰᄆ" to "ngm",
                    "ᆰᄒ" to "lkh",
                    "ᆱ" to "m",
                    "ᆱᄋ" to "lm",
                    "ᆱᄂ" to "mn",
                    "ᆱᄅ" to "mn",
                    "ᆱᄆ" to "mm",
                    "ᆱᄒ" to "lmh",
                    "ᆲ" to "p",
                    "ᆲᄋ" to "lb",
                    "ᆲᄂ" to "mn",
                    "ᆲᄅ" to "mn",
                    "ᆲᄆ" to "mm",
                    "ᆲᄒ" to "lph",
                    "ᆳ" to "t",
                    "ᆳᄋ" to "ls",
                    "ᆳᄂ" to "nn",
                    "ᆳᄅ" to "nn",
                    "ᆳᄆ" to "nm",
                    "ᆳᄒ" to "lsh",
                    "ᆴ" to "t",
                    "ᆴᄋ" to "lt",
                    "ᆴᄂ" to "nn",
                    "ᆴᄅ" to "nn",
                    "ᆴᄆ" to "nm",
                    "ᆴᄒ" to "lth",
                    "ᆵ" to "p",
                    "ᆵᄋ" to "lp",
                    "ᆵᄂ" to "mn",
                    "ᆵᄅ" to "mn",
                    "ᆵᄆ" to "mm",
                    "ᆵᄒ" to "lph",
                    "ᆶ" to "l",
                    "ᆶᄋ" to "lh",
                    "ᆶᄂ" to "ll",
                    "ᆶᄅ" to "ll",
                    "ᆶᄆ" to "lm",
                    "ᆶᄒ" to "lh",
                    "ᆷ" to "m",
                    "ᆷᄅ" to "mn",
                    "ᆸ" to "p",
                    "ᆸᄋ" to "b",
                    "ᆸᄂ" to "mn",
                    "ᆸᄅ" to "mn",
                    "ᆸᄆ" to "mm",
                    "ᆸᄒ" to "ph",
                    "ᆹ" to "p",
                    "ᆹᄋ" to "ps",
                    "ᆹᄂ" to "mn",
                    "ᆹᄅ" to "mn",
                    "ᆹᄆ" to "mm",
                    "ᆹᄒ" to "psh",
                    "ᆺ" to "t",
                    "ᆺᄋ" to "s",
                    "ᆺᄂ" to "nn",
                    "ᆺᄅ" to "nn",
                    "ᆺᄆ" to "nm",
                    "ᆺᄒ" to "sh",
                    "ᆻ" to "t",
                    "ᆻᄋ" to "ss",
                    "ᆻᄂ" to "tn",
                    "ᆻᄅ" to "tn",
                    "ᆻᄆ" to "nm",
                    "ᆻᄒ" to "th",
                    "ᆼ" to "ng",
                    "ᆽ" to "t",
                    "ᆽᄋ" to "j",
                    "ᆽᄂ" to "nn",
                    "ᆽᄅ" to "nn",
                    "ᆽᄆ" to "nm",
                    "ᆽᄒ" to "ch",
                    "ᆾ" to "t",
                    "ᆾᄋ" to "ch",
                    "ᆾᄂ" to "nn",
                    "ᆾᄅ" to "nn",
                    "ᆾᄆ" to "nm",
                    "ᆾᄒ" to "ch",
                    "ᆿ" to "k",
                    "ᆿᄋ" to "k",
                    "ᆿᄂ" to "ngn",
                    "ᆿᄅ" to "ngn",
                    "ᆿᄆ" to "ngm",
                    "ᆿᄒ" to "kh",
                    "ᇀ" to "t",
                    "ᇀᄋ" to "t",
                    "ᇀᄂ" to "nn",
                    "ᇀᄅ" to "nn",
                    "ᇀᄆ" to "nm",
                    "ᇀᄒ" to "th",
                    "ᇁ" to "p",
                    "ᇁᄋ" to "p",
                    "ᇁᄂ" to "mn",
                    "ᇁᄅ" to "mn",
                    "ᇁᄆ" to "mm",
                    "ᇁᄒ" to "ph",
                    "ᇂ" to "t",
                    "ᇂᄋ" to "h",
                    "ᇂᄂ" to "nn",
                    "ᇂᄅ" to "nn",
                    "ᇂᄆ" to "mm",
                    "ᇂᄒ" to "t",
                    "ᇂᄀ" to "k",
                ),
        )

    fun isTtml(lyrics: String): Boolean {
        val trimmed = normalizeLyricsText(lyrics)
        if (!trimmed.startsWith("<")) return false

        return trimmed.contains("<tt", ignoreCase = true) ||
            trimmed.contains("http://www.w3.org/ns/ttml", ignoreCase = true)
    }

    /**
     * Returns true when [lyrics] contains at least one actual translation entry
     * produced by [moe.rukamori.archivetune.ai.AiLyricsTranslator].
     *
     * The translator marks the lyrics' source as `AI_TRANSLATION` regardless of
     * whether the AI returned anything useful — if every line came back identical
     * to the source (a common failure mode for CJK lyrics that were previously
     * mangled by the span-joining bug in `AiLyricsDocument.readTtmlLineText`),
     * the rebuild produces no `<translation>` element / no duplicate-timestamp
     * LRC lines, but the row is still stored with `source = AI_TRANSLATION`.
     *
     * Without this check, the auto-translate LaunchedEffect in LyricsScreen.kt
     * and AppleMusicPlayer.kt would skip those songs forever (the
     * `source == AI_TRANSLATION` guard returns early), so the user would never
     * get a translation even after the underlying bug is fixed. By allowing a
     * retry when `hasTranslation()` is false, previously no-op'd translations
     * get a chance to re-run with the corrected parser.
     *
     * Detection rules:
     *  - TTML: look for `<translation ... data-archivetune="translation"` (the
     *    marker `TtmlLyricsDocument.rebuild` writes).
     *  - LRC / QRC / plain: look for any timestamp prefix that appears more
     *    than once — translators append translated lines under the same prefix
     *    as the original, so a duplicate prefix means at least one translation
     *    was added.
     */
    fun hasTranslation(lyrics: String): Boolean {
        if (lyrics.isBlank()) return false
        if (isTtml(lyrics)) {
            return lyrics.contains("data-archivetune", ignoreCase = true) &&
                lyrics.contains("translation", ignoreCase = true)
        }
        val seenPrefixes = HashSet<String>()
        lyrics.lineSequence().forEach { line ->
            val match = SyncedLinePrefixRegex.matchEntire(line) ?: return@forEach
            val prefix = match.groupValues[1]
            if (!seenPrefixes.add(prefix)) return true
        }
        return false
    }

    fun shouldAutoTranslate(
        lyrics: String,
        targetLanguage: String,
        excludedLanguageCodes: Set<String> = emptySet(),
    ): Boolean {
        if (lyrics.isBlank()) return false
        val dominant = detectDominantLanguageCode(lyrics)
        // If the lyrics' dominant language is in the user's "Don't auto translate these languages"
        // exclusion set, skip translation even when auto-translate is on.
        if (dominant != null && dominant.uppercase() in excludedLanguageCodes.map { it.uppercase() }) {
            return false
        }
        val allowedScripts = allowedScriptsForLanguage(targetLanguage)
        // When the target is English (or unset, which defaults to English), we also
        // trigger on non-ASCII Latin letters (á, é, í, ó, ú, ñ, ü, ç, etc.) so that
        // Spanish/French/German/Portuguese/Italian lyrics auto-translate to English.
        // Without this, Latin-to-English pairs never fire because both share the
        // Latin script. For non-English Latin targets (e.g. Spanish→French), we
        // skip this heuristic to avoid wasteful same-script translations.
        val targetIsEnglish = isEnglishTarget(targetLanguage)
        return lyrics.asSequence().any { char ->
            if (!char.isLetter()) return@any false
            val script = UnicodeScript.of(char.code)
            script !in allowedScripts ||
                (targetIsEnglish && script == UnicodeScript.LATIN && char.code > 127)
        }
    }

    private fun isEnglishTarget(language: String): Boolean {
        val normalized = language.trim().lowercase().replace('_', '-').substringBefore('-')
        return normalized.isEmpty() || normalized == "english" || normalized == "en"
    }

    /**
     * Returns the uppercase language code (e.g. "JAPANESE", "KOREAN", "CHINESE", "HINDI",
     * "ARABIC", "RUSSIAN", "THAI", "HEBREW", "GREEK", "ARMENIAN", "GEORGIAN") that best
     * describes the dominant non-Latin script in [lyrics], or `null` if the lyrics are
     * predominantly Latin (so no exclusion can match).
     *
     * The returned codes match `TranslatorLanguage.code` in `assets/translator_languages.json`.
     */
    fun detectDominantLanguageCode(lyrics: String): String? {
        if (lyrics.isBlank()) return null
        // Tally non-Latin scripts found in the lyrics. Latin/Common/Inherited are ignored.
        val scriptCounts = HashMap<UnicodeScript, Int>()
        for (char in lyrics) {
            if (!char.isLetter()) continue
            val script = UnicodeScript.of(char.code)
            when (script) {
                UnicodeScript.LATIN,
                UnicodeScript.COMMON,
                UnicodeScript.INHERITED -> Unit
                else -> scriptCounts[script] = (scriptCounts[script] ?: 0) + 1
            }
        }
        if (scriptCounts.isEmpty()) return null
        val dominantScript = scriptCounts.maxByOrNull { it.value }!!.key
        return when (dominantScript) {
            UnicodeScript.HAN -> "CHINESE"
            UnicodeScript.HIRAGANA, UnicodeScript.KATAKANA -> "JAPANESE"
            UnicodeScript.HANGUL -> "KOREAN"
            UnicodeScript.DEVANAGARI -> "HINDI"
            UnicodeScript.ARABIC -> "ARABIC"
            UnicodeScript.CYRILLIC -> "RUSSIAN"
            UnicodeScript.THAI -> "THAI"
            UnicodeScript.HEBREW -> "HEBREW"
            UnicodeScript.GREEK -> "GREEK"
            UnicodeScript.ARMENIAN -> "ARMENIAN"
            UnicodeScript.GEORGIAN -> "GEORGIAN"
            else -> null
        }
    }

    private fun allowedScriptsForLanguage(language: String): Set<UnicodeScript> {
        val normalized = language.trim().lowercase().replace('_', '-').substringBefore('-')
        val code = when (normalized) {
            "english", "en" -> "en"
            "japanese", "ja" -> "ja"
            "korean", "ko" -> "ko"
            "chinese", "zh", "mandarin", "cmn", "cantonese", "yue" -> "zh"
            "hindi", "hi", "sanskrit", "sa", "marathi", "mr", "nepali", "ne" -> "hi"
            "arabic", "ar", "persian", "fa", "urdu", "ur" -> "ar"
            "russian", "ru", "ukrainian", "uk", "belarusian", "be", "bulgarian", "bg" -> "ru"
            "thai", "th" -> "th"
            "hebrew", "he", "yiddish", "yi" -> "he"
            "greek", "el" -> "el"
            "armenian", "hy" -> "hy"
            "georgian", "ka" -> "ka"
            else -> normalized
        }
        return when (code) {
            "ja" -> setOf(
                UnicodeScript.LATIN,
                UnicodeScript.COMMON,
                UnicodeScript.INHERITED,
                UnicodeScript.HAN,
                UnicodeScript.HIRAGANA,
                UnicodeScript.KATAKANA,
            )
            "ko" -> setOf(
                UnicodeScript.LATIN,
                UnicodeScript.COMMON,
                UnicodeScript.INHERITED,
                UnicodeScript.HANGUL,
            )
            "zh" -> setOf(
                UnicodeScript.LATIN,
                UnicodeScript.COMMON,
                UnicodeScript.INHERITED,
                UnicodeScript.HAN,
            )
            "hi" -> setOf(
                UnicodeScript.LATIN,
                UnicodeScript.COMMON,
                UnicodeScript.INHERITED,
                UnicodeScript.DEVANAGARI,
            )
            "ar" -> setOf(
                UnicodeScript.LATIN,
                UnicodeScript.COMMON,
                UnicodeScript.INHERITED,
                UnicodeScript.ARABIC,
            )
            "ru" -> setOf(
                UnicodeScript.LATIN,
                UnicodeScript.COMMON,
                UnicodeScript.INHERITED,
                UnicodeScript.CYRILLIC,
            )
            "th" -> setOf(
                UnicodeScript.LATIN,
                UnicodeScript.COMMON,
                UnicodeScript.INHERITED,
                UnicodeScript.THAI,
            )
            "he" -> setOf(
                UnicodeScript.LATIN,
                UnicodeScript.COMMON,
                UnicodeScript.INHERITED,
                UnicodeScript.HEBREW,
            )
            "el" -> setOf(
                UnicodeScript.LATIN,
                UnicodeScript.COMMON,
                UnicodeScript.INHERITED,
                UnicodeScript.GREEK,
            )
            "hy" -> setOf(
                UnicodeScript.LATIN,
                UnicodeScript.COMMON,
                UnicodeScript.INHERITED,
                UnicodeScript.ARMENIAN,
            )
            "ka" -> setOf(
                UnicodeScript.LATIN,
                UnicodeScript.COMMON,
                UnicodeScript.INHERITED,
                UnicodeScript.GEORGIAN,
            )
            else -> setOf(
                UnicodeScript.LATIN,
                UnicodeScript.COMMON,
                UnicodeScript.INHERITED,
            )
        }
    }

    fun isLineSyncedLrc(lyrics: String): Boolean =
        QRCParser.isQrc(normalizeLyricsText(lyrics)) ||
            lyrics.lineSequence().any { line ->
            val trimmedLine = line.trim()
            LINE_REGEX.matches(trimmedLine) || YRC_LINE_REGEX.matches(trimmedLine)
        }

    fun hasWordSyncedLyrics(lyrics: String): Boolean {
        val normalized = normalizeLyricsText(lyrics)
        if (QRCParser.isQrc(normalized)) return QRCParser.hasWordTimings(normalized)
        if (isTtml(normalized)) {
            return TTML_SPAN_REGEX.findAll(normalized).any { match ->
                TTML_BEGIN_ATTRIBUTE_REGEX.containsMatchIn(match.value) &&
                    TTML_END_ATTRIBUTE_REGEX.containsMatchIn(match.value)
            }
        }
        // Enhanced LRC: lines like "[00:01.234]<00:01.500>Hello <00:01.700>world"
        // where each word has its own <mm:ss.xxx> inline timestamp. This format is
        // produced by YouLyPlus's v2/lyrics/get fallback endpoint (when v1/ttml/get
        // is unavailable) and by some other providers. Without this check, those
        // word-synced lyrics would be misclassified as plain line-synced LRC and
        // the "Prioritize Word Synced Lyrics" feature would skip them.
        //
        // We require BOTH a line-level [mm:ss.xxx] tag AND an inline <mm:ss.xxx>
        // word timestamp on the same line, so that plain text containing angle
        // brackets (e.g. "<3") doesn't false-positive.
        return normalized.lineSequence().any { line ->
            LINE_REGEX.containsMatchIn(line) &&
                (ENHANCED_LRC_WORD_TIME_REGEX.containsMatchIn(line) ||
                    INLINE_MILLISECONDS_TIME_REGEX.containsMatchIn(line))
        }
    }

    fun parseTtml(
        lyrics: String,
        durationSeconds: Int? = null,
    ): List<LyricsEntry> {
        val parsedLines = TTMLParser.parseTTML(normalizeLyricsText(lyrics))
        if (parsedLines.isEmpty()) return emptyList()
        val scale = 1.0

        return parsedLines
            .map { line ->
                val words =
                    line.words
                        .filter { it.text.isNotEmpty() }
                        .map { word ->
                            WordTimestamp(
                                text = word.text,
                                startTime = word.startTime * scale,
                                endTime = word.endTime * scale,
                                isBackground = word.isBackground,
                            )
                        }.takeIf { it.isNotEmpty() }

                LyricsEntry(
                    time = (line.startTime * scale * 1000.0).toLong(),
                    text = line.text,
                    words = words,
                    agent = line.agent,
                    providerRomanizedText = line.providerRomanizedText,
                    providerRomanizedWords = line.providerRomanizedWords,
                    providerRomanizedLanguage = line.providerRomanizedLanguage,
                    providerTranslationText = line.providerTranslationText,
                )
            }.sorted()
    }

    fun parseLyrics(lyrics: String): List<LyricsEntry> {
        val normalizedLyrics = normalizeLyricsText(lyrics)
        if (QRCParser.isQrc(normalizedLyrics)) {
            val translationsByStartMs = extractQrcTranslations(normalizedLyrics)
            return QRCParser.parseQrc(normalizedLyrics).map { line ->
                val startMs = (line.startTime * 1000.0).toLong()
                LyricsEntry(
                    time = startMs,
                    text = line.text,
                    words =
                        line.words
                            .map { word ->
                                WordTimestamp(
                                    text = word.text,
                                    startTime = word.startTime,
                                    endTime = word.endTime,
                                )
                            }.takeIf { it.isNotEmpty() },
                    agent = line.agent,
                    providerTranslationText = translationsByStartMs[startMs],
                    durationMs = ((line.endTime - line.startTime) * 1000.0).toLong().coerceAtLeast(0L),
                )
            }
        }

        val lines = normalizedLyrics.lines()
        val result = mutableListOf<LyricsEntry>()

        for (line in lines) {
            val entries = parseLineSyncedLrcLine(line) ?: parseMillisecondsSyncedLine(line)
            if (entries != null) {
                result.addAll(entries)
            }
        }
        return mergeLineSyncedTranslations(result).sorted()
    }

    private fun extractQrcTranslations(lyrics: String): Map<Long, String> {
        val wordTimedStartMs = mutableSetOf<Long>()
        val translationCandidates = mutableListOf<Pair<Long, String>>()
        lyrics.lineSequence().forEach { line ->
            val trimmed = line.trim()
            val match = QrcTranslationLineRegex.matchEntire(trimmed) ?: return@forEach
            val startMs = match.groupValues[1].toLongOrNull() ?: return@forEach
            val content = match.groupValues[3]
            if (QrcWordTimingDetectRegex.containsMatchIn(content)) {
                wordTimedStartMs.add(startMs)
            } else if (content.isNotBlank()) {
                translationCandidates.add(startMs to content.trim())
            }
        }
        val translations = mutableMapOf<Long, String>()
        translationCandidates.forEach { (startMs, text) ->
            if (startMs in wordTimedStartMs && startMs !in translations) {
                translations[startMs] = text
            }
        }
        return translations
    }

    fun normalizeLyricsText(lyrics: String): String {
        val raw =
            lyrics
                .replace("\uFEFF", "")
                .replace(INVISIBLE_CHARS_REGEX, "")
                .trim { it.isWhitespace() || it == NBSP }

        val unwrapped = stripCodeFence(raw)
        val normalized =
            if (isEscapedTtml(unwrapped)) {
                unwrapped
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&quot;", "\"")
                    .replace("&#39;", "'")
                    .replace("&apos;", "'")
            } else {
                unwrapped
            }

        return normalized.trim { it.isWhitespace() || it == NBSP }
    }

    fun displayLyricsText(lyrics: String): String {
        val raw = normalizeLyricsText(lyrics)
        if (raw.isEmpty() || raw == LyricsEntity.LYRICS_NOT_FOUND) return ""

        val visibleLines =
            when {
                isTtml(raw) -> runCatching { parseTtml(raw).map { it.text } }.getOrElse { emptyList() }
                isLineSyncedLrc(raw) -> runCatching { parseLyrics(raw).map { it.text } }.getOrElse { emptyList() }
                raw.startsWith("<") -> emptyList()
                else -> raw.lines().map(::cleanInlineWordTimingText)
            }

        return visibleLines
            .map { line ->
                line
                    .replace(WHITESPACE_REGEX, " ")
                    .trim { it.isWhitespace() || it == NBSP }
            }.filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    fun hasMeaningfulLyricsContent(lyrics: String): Boolean = displayLyricsText(lyrics).isNotEmpty()

    fun lyricsOrNotFound(lyrics: String): String {
        val normalized = normalizeLyricsText(lyrics)
        return normalized.takeIf(::hasMeaningfulLyricsContent) ?: LyricsEntity.LYRICS_NOT_FOUND
    }

    private fun stripCodeFence(lyrics: String): String {
        if (!lyrics.startsWith("```")) return lyrics

        val lines = lyrics.lines()
        if (lines.size <= 1) return lyrics

        val bodyLines =
            lines.drop(1).let { remainingLines ->
                if (remainingLines.lastOrNull()?.trim() == "```") {
                    remainingLines.dropLast(1)
                } else {
                    remainingLines
                }
            }

        return bodyLines.joinToString("\n").trim { it.isWhitespace() || it == NBSP }
    }

    private fun isEscapedTtml(lyrics: String): Boolean {
        val trimmed = lyrics.trimStart()
        return trimmed.startsWith("&lt;tt", ignoreCase = true) ||
            trimmed.contains("&lt;tt", ignoreCase = true) ||
            trimmed.contains("http://www.w3.org/ns/ttml", ignoreCase = true) &&
            trimmed.contains("&lt;", ignoreCase = true)
    }

    fun insertInstrumentalBreaks(
        entries: List<LyricsEntry>,
        songDurationMs: Long = 0L,
    ): List<LyricsEntry> {
        if (entries.isEmpty()) return entries
        val result = mutableListOf<LyricsEntry>()
        insertIntroInstrumentalIfNeeded(entries, result)
        result.addAll(entries)
        insertOutroInstrumentalIfNeeded(entries, songDurationMs, result)
        return result
    }

    private const val INSTRUMENTAL_GAP_THRESHOLD_MS = 5000L
    private const val INSTRUMENTAL_INTRO_START_MS = 1000L
    private const val INSTRUMENTAL_OUTRO_VOCAL_TAIL_MS = 2500L

    private fun insertIntroInstrumentalIfNeeded(
        entries: List<LyricsEntry>,
        result: MutableList<LyricsEntry>,
    ) {
        val firstTimedVocalEntry = entries.firstOrNull { it.time >= 0L && it.text.isNotBlank() } ?: return
        val introGapMs = firstTimedVocalEntry.time - INSTRUMENTAL_INTRO_START_MS
        if (introGapMs < INSTRUMENTAL_GAP_THRESHOLD_MS) return

        result.add(
            LyricsEntry(
                time = INSTRUMENTAL_INTRO_START_MS,
                text = "",
                isInstrumental = true,
                durationMs = introGapMs,
            ),
        )
    }

    private fun insertOutroInstrumentalIfNeeded(
        entries: List<LyricsEntry>,
        songDurationMs: Long,
        result: MutableList<LyricsEntry>,
    ) {
        if (songDurationMs <= 0L) return
        val lastVocalEntry = entries.lastOrNull { it.text.isNotBlank() } ?: return
        val outroStartMs = lastVocalEntry.time + INSTRUMENTAL_OUTRO_VOCAL_TAIL_MS
        val outroDurationMs = songDurationMs - outroStartMs
        if (outroDurationMs < INSTRUMENTAL_GAP_THRESHOLD_MS) return

        result.add(
            LyricsEntry(
                time = outroStartMs,
                text = "",
                isInstrumental = true,
                durationMs = outroDurationMs,
            ),
        )
    }

    private fun parseLineSyncedLrcLine(line: String): List<LyricsEntry>? {
        if (line.isEmpty()) {
            return null
        }
        val matchResult = LINE_REGEX.matchEntire(line.trim()) ?: return null
        val times = matchResult.groupValues[1]
        val text = cleanInlineWordTimingText(matchResult.groupValues[3])
        val timeMatchResults = TIME_REGEX.findAll(times)

        return timeMatchResults
            .map { timeMatchResult ->
                val min = timeMatchResult.groupValues[1].toLong()
                val sec = timeMatchResult.groupValues[2].toLong()
                val milString = timeMatchResult.groupValues[3]
                var mil = milString.toLongOrNull() ?: 0L
                when (milString.length) {
                    1 -> mil *= 100
                    2 -> mil *= 10
                }
                val time = min * DateUtils.MINUTE_IN_MILLIS + sec * DateUtils.SECOND_IN_MILLIS + mil
                LyricsEntry(time, text)
            }.toList()
    }

    private fun parseMillisecondsSyncedLine(line: String): List<LyricsEntry>? {
        if (line.isEmpty()) {
            return null
        }
        val matchResult = YRC_LINE_REGEX.matchEntire(line.trim()) ?: return null
        val time = matchResult.groupValues[1].toLongOrNull() ?: return null
        val text = cleanInlineWordTimingText(matchResult.groupValues[2])
        return listOf(LyricsEntry(time, text))
    }

    private fun mergeLineSyncedTranslations(entries: List<LyricsEntry>): List<LyricsEntry> {
        val mergedByTime = linkedMapOf<Long, LyricsEntry>()
        entries.forEach { entry ->
            val existing = mergedByTime[entry.time]
            if (existing == null) {
                mergedByTime[entry.time] = entry
                return@forEach
            }

            val translatedText =
                entry.text
                    .replace(WHITESPACE_REGEX, " ")
                    .trim()
                    .takeIf { it.isNotEmpty() && !it.equals(existing.text.trim(), ignoreCase = true) }

            if (translatedText != null && existing.providerTranslationText == null) {
                mergedByTime[entry.time] = existing.copy(providerTranslationText = translatedText)
            }
        }
        return mergedByTime.values.toList()
    }

    private fun cleanInlineWordTimingText(text: String): String =
        text
            .replace(ENHANCED_LRC_WORD_TIME_REGEX, "")
            .replace(INLINE_MILLISECONDS_TIME_REGEX, "")
            .replace(YRC_WORD_TIME_REGEX, "")
            .replace(WHITESPACE_REGEX, " ")
            .trim { it.isWhitespace() || it == NBSP }

    fun findCurrentLineIndex(
        lines: List<LyricsEntry>,
        position: Long,
        leadMs: Long = 300L,
    ): Int {
        if (lines.isEmpty()) return -1

        // Find the last line whose start time is <= position (no lead). This is the
        // "candidate current line" — we then decide whether to advance to the next line.
        val exactTarget = position
        var low = 0
        var high = lines.lastIndex

        while (low <= high) {
            val mid = (low + high).ushr(1)
            val midTime = lines[mid].time

            if (midTime < exactTarget) {
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        val currentIdx = high.coerceIn(0, lines.lastIndex)

        // If there's no next line, the current line is the answer.
        val nextIdx = currentIdx + 1
        if (nextIdx > lines.lastIndex) return currentIdx

        val currentLine = lines[currentIdx]
        val nextLine = lines[nextIdx]

        // Fix for "next line becomes active while the previous line is still being sung" —
        // when the current line has an explicit durationMs (TTML), do not advance to the next
        // line until we're past the current line's end. The previous behaviour unconditionally
        // applied `leadMs` and could highlight N+1 up to 300ms before N finished singing,
        // which was jarring on lines with tight tail gaps.
        if (currentLine.durationMs > 0L) {
            val currentLineEndMs = currentLine.time + currentLine.durationMs
            if (position < currentLineEndMs) {
                return currentIdx
            }
        }

        // For line-synced LRC (no durationMs), the previous behaviour applied a flat 300ms lead
        // regardless of how big the gap to the next line was. That made the next line highlight
        // 300ms early even when there was a long instrumental break — and when the actual singing
        // of the next line was still slightly delayed by the singer, the highlight felt premature.
        //
        // We now only apply the lead when lines are back-to-back (gap between line starts <= 2s).
        // For longer gaps (slow ballads, instrumental interludes), we transition exactly at the
        // next line's start time — no read-ahead. This preserves smooth transitions on rapid
        // lyrics while avoiding the premature highlight on long-gap tracks.
        val gapBetweenLineStartsMs = nextLine.time - currentLine.time
        val effectiveLeadMs = if (gapBetweenLineStartsMs > 2_000L) 0L else leadMs

        return if (position + effectiveLeadMs >= nextLine.time) {
            nextIdx
        } else {
            currentIdx
        }
    }

    /**
     * Returns true when [entry] has real, per-word timing information that should drive
     * word-by-word (karaoke) animation. Returns false when the [LyricsEntry.words] list
     * is missing, empty, or contains only fake/synthetic timing patterns — namely:
     *
     *   - All word start times identical (line start sprayed onto every word).
     *   - All word end times identical (line end sprayed onto every word).
     *   - All word durations <= 0 (zero-duration spans).
     *   - Word start times that perfectly match an even linear distribution across the
     *     line (the classic "provider computed timing by dividing line duration by N"
     *     pattern) AND word durations are also near-identical (low stddev relative to
     *     mean). Real human singing has timing variation; mathematically-perfect even
     *     distribution with identical word durations is the signature of a fake.
     *
     * This catches the case where providers like Better Lyrics / YouLyPlus / similar
     * emit TTML with a `<span>` per word but the spans inherit the line's begin/end with
     * no actual per-word offsets — which previously made Lyrics.kt animate each word
     * in lockstep even though the lyric wasn't truly word-synced.
     *
     * Single-word lines (e.g. "Yeah", "Oh", "Hey") are accepted as long as the one
     * span has a real, positive duration. The multi-word heuristics below are
     * degenerate for N=1, so we short-circuit before reaching them. This matches the
     * original app's behaviour where short interjections still animate per-letter.
     */
    fun hasTrueWordSync(entry: LyricsEntry): Boolean {
        val raw = entry.words ?: return false
        val words = raw.filter { it.text.isNotBlank() }
        if (words.isEmpty()) return false
        if (words.size == 1) {
            // Single-word line: still counts as word-synced when the one span has a
            // real, positive duration. The all-starts-identical / all-ends-identical
            // / even-distribution heuristics below are meaningless for N=1, so just
            // verify the word actually spans a non-zero interval.
            val only = words.first()
            return (only.endTime - only.startTime) > 0.0
        }

        val startTimes = words.map { it.startTime }
        val endTimes = words.map { it.endTime }

        // All start times identical → no per-word timing.
        if (startTimes.distinct().size == 1) return false
        // All end times identical → no per-word timing.
        if (endTimes.distinct().size == 1) return false

        val durations = words.map { (it.endTime - it.startTime).coerceAtLeast(0.0) }
        // All durations zero → no real word spans.
        if (durations.all { it <= 0.0 }) return false

        val lineStart = startTimes.min()
        val lineEnd = endTimes.max()
        val lineDuration = lineEnd - lineStart

        // Detect perfectly even linear distribution of start times across the line.
        // This is what providers produce when they fake word sync by computing
        //   word[i].start = lineStart + i * (lineEnd - lineStart) / (N - 1)
        // We tolerate up to 50ms deviation per word; real word sync deviates more.
        if (lineDuration > 0.0 && words.size > 2) {
            val tolerance = 0.05 // 50ms
            val isEvenlyDistributed = startTimes.indices.all { i ->
                val expected = lineStart + (lineDuration * i / (words.size - 1))
                kotlin.math.abs(startTimes[i] - expected) < tolerance
            }
            if (isEvenlyDistributed) {
                // Even distribution could still be real singing that happens to be very
                // regular. Require word durations to also have meaningful variation —
                // fake providers typically give every word the same duration too.
                val positiveDurations = durations.filter { it > 0.0 }
                if (positiveDurations.size >= 3) {
                    val avg = positiveDurations.average()
                    if (avg > 0.0) {
                        val variance = positiveDurations.map { (it - avg) * (it - avg) }.average()
                        val stddev = kotlin.math.sqrt(variance)
                        if (stddev / avg < 0.1) {
                            // Durations are essentially identical AND start times are perfectly
                            // evenly spaced — this is almost certainly synthetic timing.
                            return false
                        }
                    }
                }
            }
        }

        return true
    }

    /**
     * Converts any Hiragana characters in [text] to their Katakana equivalents.
     * Hiragana and Katakana share the same Unicode ordering — every Hiragana codepoint
     * has a Katakana counterpart at offset 0x60 (e.g. あ U+3042 → ア U+30A2). This lets
     * the existing Katakana-only [KANA_ROMAJI_MAP] handle both scripts after a single
     * cheap pre-pass.
     *
     * Characters outside the Hiragana block (Katakana, Kanji, Latin, punctuation, etc.)
     * are passed through unchanged.
     */
    private fun hiraganaToKatakana(text: String): String {
        if (text.isEmpty()) return text
        val sb = StringBuilder(text.length)
        for (ch in text) {
            sb.append(
                if (ch in '\u3041'..'\u3096') {
                    (ch.code + 0x60).toChar()
                } else {
                    ch
                },
            )
        }
        return sb.toString()
    }

    /**
     * Romanizes Japanese text using Kuromoji Tokenizer and the optimized katakanaToRomaji function.
     * Runs on Dispatchers.Default for CPU-intensive work.
     *
     * Pipeline:
     *   1. Tokenize the input with Kuromoji (kanji + kana boundaries, readings).
     *   2. For each token, pick the reading if Kuromoji provides one (usually Katakana);
     *      otherwise fall back to the surface form (which may contain Hiragana).
     *   3. Convert any Hiragana in the reading to Katakana via [hiraganaToKatakana] —
     *      this is the critical fix. Previously, Hiragana characters in the surface form
     *      passed through [katakanaToRomaji] unchanged because [KANA_ROMAJI_MAP] only
     *      contains Katakana keys, so lyrics like "くさはねぇ" stayed as "くさはねぇ"
     *      instead of becoming "kusahane-".
     *   4. Run [katakanaToRomaji] on the normalized Katakana string. Sokuon (ッ) and
     *      chōonpu (ー) are handled inside that function.
     *   5. Pass the next token's Katakana-normalized reading as `nextKatakana` so
     *      sokuon at a token boundary can still geminate the next token's initial
     *      consonant.
     */
    suspend fun romanizeJapanese(text: String): String =
        withContext(Dispatchers.Default) {
            val tokenizer = JapaneseLanguagePackManager.tokenizerOrNull() ?: return@withContext text
            val tokens = tokenizer.tokenize(text)

            val romanizedTokens =
                tokens.mapIndexed { index, token ->
                    val currentReading =
                        if (token.reading.isNullOrEmpty() || token.reading == "*") {
                            token.surface
                        } else {
                            token.reading
                        }
                    // Normalize Hiragana → Katakana so KANA_ROMAJI_MAP can handle both.
                    val katakanaReading = hiraganaToKatakana(currentReading)

                    // Pass the next token's reading for sokuon handling at token boundaries.
                    // Also normalized to Katakana for consistency.
                    val nextTokenReading =
                        if (index + 1 < tokens.size) {
                            val nextReading =
                                tokens[index + 1].reading?.takeIf { it.isNotEmpty() && it != "*" }
                                    ?: tokens[index + 1].surface
                            hiraganaToKatakana(nextReading)
                        } else {
                            null
                        }
                    katakanaToRomaji(katakanaReading, nextTokenReading)
                }
            romanizedTokens.joinToString(" ")
        }

    /**
     * Converts a Katakana string to Romaji using the pre-defined [KANA_ROMAJI_MAP].
     *
     * Handles three classes of characters specially beyond the map lookup:
     *
     *   1. Yōon (拗音) — 2-character sequences like "キャ" (kya). These are matched
     *      BEFORE single-character lookups so the small y-vowel (ャ/ュ/ョ) combines
     *      with the preceding consonant instead of being treated as a standalone
     *      (and unmapped) character.
     *
     *   2. Sokuon (ッ) — gemination marker. Doubles the consonant of the NEXT
     *      character. The next character is looked up WITHIN the current string
     *      first (`katakana[i + 1]`); only if sokuon appears at the end of the
     *      string do we fall back to the first character of [nextKatakana] (the
     *      next token's reading). This fixes the previous bug where sokuon
     *      mid-token (e.g. "がっこう" → "gakkou") was silently dropped because
     *      the code only inspected the next TOKEN, not the next CHARACTER.
     *
     *   3. Chōonpu (ー) — long vowel mark. Extends the previous vowel instead of
     *      being dropped (the old map entry `"ー" to ""` lost the long-vowel
     *      information, turning "カー" (kaa) into "ka").
     *
     * @param katakana The Katakana string to convert. Hiragana should be
     *     pre-converted with [hiraganaToKatakana]; any remaining non-Katakana
     *     characters are passed through as-is.
     * @param nextKatakana Optional: the next token's Katakana reading, used only
     *     for sokuon-at-end-of-token gemination. Most tokens don't need this.
     */
    fun katakanaToRomaji(
        katakana: String?,
        nextKatakana: String? = null,
    ): String {
        if (katakana.isNullOrEmpty()) return ""

        val romajiBuilder = StringBuilder(katakana.length)
        var i = 0
        val n = katakana.length
        while (i < n) {
            var consumed = false
            // Prioritize 2-character sequences from the map (e.g., "キャ" before "キ")
            if (i + 1 < n) {
                val twoCharCandidate = katakana.substring(i, i + 2)
                val mappedTwoChar = KANA_ROMAJI_MAP[twoCharCandidate]
                if (mappedTwoChar != null) {
                    romajiBuilder.append(mappedTwoChar)
                    i += 2
                    consumed = true
                }
            }

            // Handle sokuon (ッ) — gemination. Doubles the consonant of the next
            // character. Look INSIDE the current string first; only fall back to
            // the next token's first character when sokuon is at the end of the
            // current string (rare; usually a tokenizer artifact).
            if (!consumed && katakana[i] == 'ッ') {
                val nextCharInSameString = katakana.getOrNull(i + 1)
                val nextCharToDouble = nextCharInSameString ?: nextKatakana?.getOrNull(0)
                if (nextCharToDouble != null) {
                    val nextCharRomaji =
                        KANA_ROMAJI_MAP[nextCharToDouble.toString()]
                            ?: nextCharToDouble.toString()
                    // Take the first letter (the consonant to geminate) and double it.
                    // For vowel-initial kana (あ, い, う, え, お) the first letter is the
                    // vowel itself — geminating a vowel is unusual but renders as the
                    // vowel doubled (e.g. っあ → "aa"), which matches common romaji
                    // conventions for emphatic speech.
                    val firstLetter = nextCharRomaji.firstOrNull()?.lowercase()?.trim()
                    if (firstLetter != null && firstLetter.isNotEmpty()) {
                        romajiBuilder.append(firstLetter)
                    }
                }
                i += 1 // Consume the 'ッ'
                consumed = true
            }

            // Handle chōonpu (ー) — long vowel mark. Extends the previous vowel
            // instead of being silently dropped. Maps to the same vowel as the last
            // emitted character (e.g. "カ" + "ー" → "ka" + "a" = "kaa"). If there's
            // no previous vowel (start of string, or previous char was a consonant),
            // we emit nothing — same as the old `"ー" to ""` map entry, but without
            // losing information when a vowel IS present.
            if (!consumed && katakana[i] == 'ー') {
                val lastChar = romajiBuilder.lastOrNull()
                val extension = when (lastChar) {
                    'a' -> "a"
                    'i' -> "i"
                    'u' -> "u"
                    'e' -> "e"
                    'o' -> "o"
                    else -> ""
                }
                romajiBuilder.append(extension)
                i += 1
                consumed = true
            }

            if (!consumed) {
                // If no 2-character sequence matched, try 1-character
                val oneCharCandidate = katakana[i].toString()
                val mappedOneChar = KANA_ROMAJI_MAP[oneCharCandidate]
                if (mappedOneChar != null) {
                    romajiBuilder.append(mappedOneChar)
                } else {
                    // If the character is not in Katakana map, append it as is.
                    romajiBuilder.append(oneCharCandidate)
                }
                i += 1
            }
        }
        return romajiBuilder.toString().lowercase()
    }

    suspend fun romanizeKorean(text: String): String =
        withContext(Dispatchers.Default) {
            val romajaBuilder = StringBuilder()
            var prevFinal: String? = null

            for (i in text.indices) {
                val char = text[i]

                if (char in '\uAC00'..'\uD7A3') {
                    val syllableIndex = char.code - 0xAC00

                    val choIndex = syllableIndex / (21 * 28)
                    val jungIndex = (syllableIndex % (21 * 28)) / 28
                    val jongIndex = syllableIndex % 28

                    val choChar = (0x1100 + choIndex).toChar().toString()
                    val jungChar = (0x1161 + jungIndex).toChar().toString()
                    val jongChar = if (jongIndex == 0) null else (0x11A7 + jongIndex).toChar().toString()

                    if (prevFinal != null) {
                        val contextKey = prevFinal + choChar
                        val jong =
                            HANGUL_ROMAJA_MAP["jong"]?.get(contextKey)
                                ?: HANGUL_ROMAJA_MAP["jong"]?.get(prevFinal)
                                ?: prevFinal
                        romajaBuilder.append(jong)
                    }

                    val cho = HANGUL_ROMAJA_MAP["cho"]?.get(choChar) ?: choChar
                    val jung = HANGUL_ROMAJA_MAP["jung"]?.get(jungChar) ?: jungChar
                    romajaBuilder.append(cho).append(jung)

                    prevFinal = jongChar
                } else {
                    if (prevFinal != null) {
                        val jong = HANGUL_ROMAJA_MAP["jong"]?.get(prevFinal) ?: prevFinal
                        romajaBuilder.append(jong)
                        prevFinal = null
                    }
                    romajaBuilder.append(char)
                }
            }

            if (prevFinal != null) {
                val jong = HANGUL_ROMAJA_MAP["jong"]?.get(prevFinal) ?: prevFinal
                romajaBuilder.append(jong)
            }

            romajaBuilder.toString()
        }

    // region Hindi (Devanagari) romanization
    //
    // A hand-written Devanagari→Latin mapper that produces intuitive, pronounceable
    // romanization for Hindi lyrics (e.g. "नमस्ते" → "namaste", "आदित्य" → "aaditya",
    // "क्षमा" → "kshama"). This replaces the previous ICU "Any-Latin; Latin-ASCII" path
    // which produced ISO-15919 with diacritics (e.g. "namastē") and then stripped them
    // (e.g. "namaste" — but lost length distinctions and palatal/retroflex contrasts).
    //
    // The mapper handles:
    //   - Independent vowels (अ, आ, इ, …) and their vowel signs (matras: ा, ि, ी, …)
    //   - Consonants with inherent "a" (क → "ka"), suppressed by virama (क् → "k")
    //   - Conjunct consonants (क + ् + ष → "ksh")
    //   - Anusvara (ं) → "n" before vowels/semivowels, "m" before labials, otherwise "n"
    //   - Visarga (ः) → "h"
    //   - Candrabindu (ँ) → "n" (nasalization marker, simplified)
    //   - Common special conjuncts: ज्ञ → "gyan", त्र → "tra", श्र → "shra", क्ष → "ksha"
    //   - Devanagari numerals (०-९) → 0-9
    //   - Danda (।) → "."
    private val DEVANAGARI_INDEPENDENT_VOWELS =
        mapOf(
            'अ' to "a", 'आ' to "aa", 'इ' to "i", 'ई' to "ii", 'उ' to "u", 'ऊ' to "uu",
            'ऋ' to "ri", 'ॠ' to "rii", 'ऌ' to "lri", 'ॡ' to "lrii",
            'ए' to "e", 'ऐ' to "ai", 'ओ' to "o", 'औ' to "au",
        )

    private val DEVANAGARI_MATRAS =
        mapOf(
            'ा' to "aa", 'ि' to "i", 'ी' to "ii", 'ु' to "u", 'ू' to "uu",
            'ृ' to "ri", 'ॄ' to "rii", 'े' to "e", 'ै' to "ai", 'ो' to "o", 'ौ' to "au",
            'ॅ' to "e", 'ॉ' to "o", 'ॢ' to "lri", 'ॣ' to "lrii",
        )

    private val DEVANAGARI_CONSONANTS =
        mapOf(
            'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "ng",
            'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh", 'ञ' to "ny",
            'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n",
            'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n",
            'प' to "p", 'फ' to "ph", 'ब' to "b", 'भ' to "bh", 'म' to "m",
            'य' to "y", 'र' to "r", 'ल' to "l", 'व' to "v",
            'श' to "sh", 'ष' to "sh", 'स' to "s", 'ह' to "h",
            'ळ' to "l",
            // Note: common conjuncts (क्ष, ज्ञ, त्र, श्र) are NOT single Unicode
            // codepoints — they're consonant + virama + consonant sequences, so they
            // can't be map keys here. They are handled naturally by the main loop's
            // consonant + virama + consonant flow, which produces the same result
            // (e.g. क + ् + ष → "k" + "" + "sh" + "a" = "ksha").
        )

    // Approximations for less-common letters / chillu characters (Malayalam-in-Devanagari etc.)
    private val DEVANAGARI_OTHER =
        mapOf(
            'ॐ' to "om",
            '।' to ".", '॥' to "..",
            'ऽ' to "'",  // avagraha
            'ं' to "n",  // anusvara (default; refined by context below)
            'ः' to "h",  // visarga
            'ँ' to "n",  // candrabindu (nasalization, simplified)
            '्' to "",   // virama (halant) — suppresses inherent "a"; handled by loop
        )

    private val DEVANAGARI_NUMERALS =
        mapOf(
            '०' to "0", '१' to "1", '२' to "2", '३' to "3", '४' to "4",
            '५' to "5", '६' to "6", '७' to "7", '८' to "8", '९' to "9",
        )

    private val LABIALS = setOf('प', 'फ', 'ब', 'भ', 'म')

    suspend fun romanizeHindi(text: String): String =
        withContext(Dispatchers.Default) {
            val sb = StringBuilder(text.length * 2)
            var i = 0
            val n = text.length
            while (i < n) {
                val ch = text[i]

                // Devanagari numerals
                if (DEVANAGARI_NUMERALS[ch] != null) {
                    sb.append(DEVANAGARI_NUMERALS[ch])
                    i++
                    continue
                }

                // Anusvara (ं) — context-sensitive:
                //   before labials (प फ ब भ म) → "m"
                //   otherwise → "n"
                if (ch == 'ं') {
                    val next = text.getOrNull(i + 1)
                    sb.append(if (next != null && next in LABIALS) "m" else "n")
                    i++
                    continue
                }

                // Candrabindu (ँ) — nasalization marker, simplified to "n"
                if (ch == 'ँ') {
                    sb.append("n")
                    i++
                    continue
                }

                // Visarga (ः)
                if (ch == 'ः') {
                    sb.append("h")
                    i++
                    continue
                }

                // Virama (्) — suppresses inherent "a" of preceding consonant.
                // The preceding consonant was already emitted WITHOUT inherent "a"
                // (see consonant branch below), so we just skip the virama here.
                if (ch == '्') {
                    i++
                    continue
                }

                // Matras (vowel signs) — replace the inherent "a" of the preceding
                // consonant. The preceding consonant was emitted WITHOUT "a" in
                // anticipation (see consonant branch below).
                val matra = DEVANAGARI_MATRAS[ch]
                if (matra != null) {
                    sb.append(matra)
                    i++
                    continue
                }

                // Independent vowels
                val independentVowel = DEVANAGARI_INDEPENDENT_VOWELS[ch]
                if (independentVowel != null) {
                    sb.append(independentVowel)
                    i++
                    continue
                }

                // Consonants — look ahead to decide whether to emit inherent "a":
                //   - If next char is a matra, virama, anusvara, visarga, or
                //     candrabindu, emit just the consonant base (no "a").
                //   - Otherwise emit consonant + "a" (inherent vowel).
                val consonant = DEVANAGARI_CONSONANTS[ch]
                if (consonant != null) {
                    val next = text.getOrNull(i + 1)
                    val suppressInherentA =
                        next != null && (
                            next in DEVANAGARI_MATRAS ||
                                next == '्' ||
                                next == 'ं' ||
                                next == 'ः' ||
                                next == 'ँ'
                        )
                    sb.append(consonant)
                    if (!suppressInherentA) {
                        sb.append('a')
                    }
                    i++
                    continue
                }

                // Other Devanagari signs (ॐ, ।, ॥, ऽ)
                val other = DEVANAGARI_OTHER[ch]
                if (other != null) {
                    sb.append(other)
                    i++
                    continue
                }

                // Non-Devanagari character — pass through as-is (spaces, punctuation,
                // Latin letters, etc.)
                sb.append(ch)
                i++
            }
            sb.toString()
        }
    // endregion

    /**
     * Checks if the given text contains any Japanese characters (Hiragana, Katakana, or common Kanji).
     * This function is generally efficient due to '.any' and early exit.
     * No major performance bottlenecks expected here for typical inputs.
     */
    fun isJapanese(text: String): Boolean =
        text.any { char ->
            (char in '\u3040'..'\u309F') || // Hiragana
                (char in '\u30A0'..'\u30FF') || // Katakana
                // CJK Unified Ideographs (covers most common Kanji)
                // Note: This range also includes many Chinese Hanzi.
                // Differentiating Japanese Kanji from Chinese Hanzi solely based on Unicode
                // ranges is challenging as they share many characters.
                // For more accurate Japanese detection, one might need to analyze
                // the presence of Hiragana/Katakana alongside Kanji.
                (char in '\u4E00'..'\u9FFF')
        }

    /**
     * Checks if the given text contains any Korean characters (Hangul Syllables, Jamo, etc.).
     */
    fun isKorean(text: String): Boolean =
        text.any { char ->
            (char in '\uAC00'..'\uD7A3') // Hangul Syllables
        }

    /**
     * Checks if the given text contains any Chinese characters (common Hanzi).
     * This function is generally efficient due to '.any' and early exit.
     * To improve accuracy in distinguishing between Chinese and Japanese (which shares Kanji),
     * this function now checks if the text *predominantly* consists of CJK Unified Ideographs
     * and *lacks* significant amounts of Hiragana or Katakana.
     *
     * A simple threshold is used here. More sophisticated methods (e.g., frequency analysis,
     * dictionaries, or machine learning models) would be needed for higher accuracy.
     */
    fun isChinese(text: String): Boolean {
        if (text.isEmpty()) return false

        val hanCharCount = text.count { hasScript(it, UnicodeScript.HAN) }
        if (hanCharCount == 0) return false

        val japaneseKanaCount = text.count { hasScript(it, UnicodeScript.HIRAGANA) || hasScript(it, UnicodeScript.KATAKANA) }
        val hangulCount = text.count { hasScript(it, UnicodeScript.HANGUL) }

        return japaneseKanaCount == 0 && hangulCount == 0
    }

    fun isHindi(text: String): Boolean = text.any { hasScript(it, UnicodeScript.DEVANAGARI) }

    fun hasOtherRomanizableScript(text: String): Boolean {
        return text.any { char ->
            if (!char.isLetter()) return@any false
            val script = UnicodeScript.of(char.code)
            script !in OTHER_ROMANIZATION_EXCLUDED_SCRIPTS
        }
    }

    fun shouldRomanizeLyricsLine(
        text: String,
        preferences: LyricsRomanizationPreferences,
    ): Boolean {
        if (!preferences.isEnabled || text.isBlank()) return false

        return when {
            preferences.romanizeJapanese && looksJapanese(text) -> true
            preferences.romanizeKorean && isKorean(text) -> true
            preferences.romanizeHindi && isHindi(text) -> true
            preferences.romanizeChinese && isChinese(text) -> true
            preferences.romanizeOther && hasOtherRomanizableScript(text) -> true
            else -> false
        }
    }

    fun shouldUseProvidedRomanization(
        originalText: String,
        providerRomanizedText: String?,
        providerRomanizedLanguage: String?,
        preferences: LyricsRomanizationPreferences,
    ): Boolean {
        if (!preferences.isEnabled || originalText.isBlank()) return false
        val normalized =
            providerRomanizedText
                ?.replace(WHITESPACE_REGEX, " ")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: return false
        if (normalized.equals(originalText.trim(), ignoreCase = true)) return false

        val language =
            providerRomanizedLanguage
                ?.substringBefore("-")
                ?.substringBefore("_")
                ?.lowercase()

        return when (language) {
            "ja" -> preferences.romanizeJapanese
            "ko" -> preferences.romanizeKorean
            "zh", "cmn", "yue" -> preferences.romanizeChinese
            "hi", "sa", "mr", "ne" -> preferences.romanizeHindi
            null, "" -> shouldRomanizeLyricsLine(originalText, preferences)
            else -> preferences.romanizeOther || shouldRomanizeLyricsLine(originalText, preferences)
        }
    }

    fun providedRomanizedTextForEntry(
        entry: LyricsEntry,
        preferences: LyricsRomanizationPreferences,
    ): String? =
        entry.providerRomanizedText
            ?.replace(WHITESPACE_REGEX, " ")
            ?.trim()
            ?.takeIf {
                shouldUseProvidedRomanization(
                    originalText = entry.text,
                    providerRomanizedText = it,
                    providerRomanizedLanguage = entry.providerRomanizedLanguage,
                    preferences = preferences,
                )
            }

    fun providedTranslationTextForEntry(entry: LyricsEntry): String? =
        entry.providerTranslationText
            ?.replace(WHITESPACE_REGEX, " ")
            ?.trim()
            ?.takeIf { it.isNotEmpty() && !it.equals(entry.text.trim(), ignoreCase = true) }

    fun providedRomanizedWordsForEntry(
        entry: LyricsEntry,
        expectedWordCount: Int,
        preferences: LyricsRomanizationPreferences,
    ): List<String?>? {
        if (expectedWordCount <= 0) return null
        if (providedRomanizedTextForEntry(entry, preferences) == null) return null

        val words =
            entry.providerRomanizedWords
                ?.map { word -> word.replace(WHITESPACE_REGEX, " ").trim() }
                ?.filter { it.isNotEmpty() }
                ?.takeIf { it.size == expectedWordCount }
                ?: return null

        return words
    }

    suspend fun romanizeLyricsLine(
        text: String,
        preferences: LyricsRomanizationPreferences,
    ): String? {
        if (!shouldRomanizeLyricsLine(text, preferences)) return null

        val romanized =
            when {
                preferences.romanizeJapanese && looksJapanese(text) -> romanizeJapanese(text)
                preferences.romanizeKorean && isKorean(text) -> romanizeKorean(text)
                preferences.romanizeHindi && isHindi(text) -> romanizeHindi(text)
                preferences.romanizeChinese && isChinese(text) -> romanizeWithIcu(text)
                preferences.romanizeOther && hasOtherRomanizableScript(text) -> romanizeWithIcu(text)
                else -> null
            }

        return normalizeRomanizedText(text, romanized)
    }

    suspend fun romanizeLyricsWordWithLineContext(
        word: String,
        lineText: String,
        preferences: LyricsRomanizationPreferences,
    ): String? {
        if (word.isBlank()) return null
        val romanized =
            when {
                preferences.romanizeJapanese && looksJapanese(lineText) -> romanizeJapanese(word)
                preferences.romanizeKorean && isKorean(lineText) -> romanizeKorean(word)
                preferences.romanizeHindi && isHindi(lineText) -> romanizeHindi(word)
                preferences.romanizeChinese && isChinese(lineText) -> romanizeWithIcu(word)
                preferences.romanizeOther && hasOtherRomanizableScript(lineText) -> romanizeWithIcu(word)
                else -> null
            }
        return normalizeRomanizedText(word, romanized)
    }

    /**
     * Romanizes a list of words from a single line using ONE tokenization pass for Japanese.
     *
     * This is the critical performance fix for Japanese word-synced (TTML) lyrics.
     * Previously, [romanizeLyricsWordWithLineContext] was called once per word, and each
     * call ran Kuromoji's full Viterbi morphological analysis (trie traversal + lattice
     * search over the entire IPADIC dictionary) on a single isolated word. For a typical
     * 40-line song with 6 words per line, that's **240 tokenize calls** — each with
     * non-trivial per-call overhead (dictionary loading is cached, but the Viterbi search
     * is O(text × trie depth) per call).
     *
     * This function tokenizes the FULL LINE once (40 calls instead of 240 — a **6x
     * reduction** in tokenize calls), then maps each token back to the original word
     * boundaries by character position. Words that span multiple tokens get their
     * romaji concatenated.
     *
     * Tokenizing the full line also gives **better romanization quality**: Kuromoji's
     * morphological analyzer sees full context, so compounds like "日本語" tokenize as
     * one token in context but may split into "日本" + "語" when isolated. The per-line
     * path produces more accurate readings.
     *
     * For non-Japanese text, falls back to [romanizeLyricsWordWithLineContext] per word
     * (character-by-character romanization for Korean/Hindi/Chinese is already cheap —
     * no tokenizer involved).
     */
    suspend fun romanizeWordsForLine(
        words: List<String>,
        lineText: String,
        preferences: LyricsRomanizationPreferences,
    ): List<String?> {
        if (words.isEmpty()) return emptyList()
        // Japanese: single-pass line tokenization (Nx faster than per-word).
        if (preferences.romanizeJapanese && looksJapanese(lineText)) {
            return romanizeJapaneseWordsForLine(words, lineText)
        }
        // Other languages: per-word is cheap (character-by-character), keep as-is.
        return words.map { word ->
            romanizeLyricsWordWithLineContext(word, lineText, preferences)
        }
    }

    /**
     * Japanese-specific per-line tokenization. See [romanizeWordsForLine] for the
     * rationale.
     *
     * Token-to-word mapping uses character positions: Kuromoji's [Token.getPosition]
     * returns the character offset of each token in the input line. We find each word's
     * start offset in the line (via [String.indexOf] with a running scan cursor to handle
     * repeated words), then collect all tokens whose `[start, end)` range overlaps with
     * the word's `[wordStart, wordEnd)` range. The romaji of overlapping tokens is
     * concatenated to form the word's phonetic.
     */
    private suspend fun romanizeJapaneseWordsForLine(
        words: List<String>,
        lineText: String,
    ): List<String?> = withContext(Dispatchers.Default) {
        if (words.isEmpty()) return@withContext emptyList()
        val tokenizer = JapaneseLanguagePackManager.tokenizerOrNull()
            ?: return@withContext words.map { null }

        val tokens = tokenizer.tokenize(lineText)
        if (tokens.isEmpty()) return@withContext words.map { null }

        // Pre-compute each token's [start, end) range and its romaji.
        // The next token's reading is kept for sokuon-at-boundary gemination
        // (same logic as romanizeJapanese, just vectorized).
        val tokenCount = tokens.size
        val tokenStarts = IntArray(tokenCount)
        val tokenEnds = IntArray(tokenCount)
        val tokenRomaji = ArrayList<String>(tokenCount)
        for (i in 0 until tokenCount) {
            val token = tokens[i]
            val surface = token.surface
            tokenStarts[i] = token.position
            tokenEnds[i] = token.position + surface.length

            val currentReading =
                if (token.reading.isNullOrEmpty() || token.reading == "*") {
                    surface
                } else {
                    token.reading
                }
            val katakanaReading = hiraganaToKatakana(currentReading)
            val nextTokenReading =
                if (i + 1 < tokenCount) {
                    val nr = tokens[i + 1].reading
                    val nextReading =
                        if (nr.isNullOrEmpty() || nr == "*") tokens[i + 1].surface else nr
                    hiraganaToKatakana(nextReading)
                } else {
                    null
                }
            tokenRomaji.add(katakanaToRomaji(katakanaReading, nextTokenReading))
        }

        // Walk through words and tokens in parallel (both are in text order).
        // This is O(words + tokens) — no nested loops.
        val result = ArrayList<String?>(words.size)
        var scanOffset = 0
        var tokenIdx = 0
        for (word in words) {
            val wordStart = lineText.indexOf(word, startIndex = scanOffset)
            if (wordStart < 0) {
                // Word not found in line text (malformed TTML or whitespace mismatch).
                // Skip — the word will have no phonetic, which is a graceful degradation.
                result.add(null)
                continue
            }
            val wordEnd = wordStart + word.length

            // Advance tokenIdx past tokens that end before this word starts.
            while (tokenIdx < tokenCount && tokenEnds[tokenIdx] <= wordStart) {
                tokenIdx++
            }

            // Collect all tokens that overlap [wordStart, wordEnd).
            val romajiBuilder = StringBuilder()
            var tIdx = tokenIdx
            while (tIdx < tokenCount && tokenStarts[tIdx] < wordEnd) {
                if (tokenEnds[tIdx] > wordStart) {
                    romajiBuilder.append(tokenRomaji[tIdx])
                }
                tIdx++
            }

            result.add(romajiBuilder.toString().takeIf { it.isNotEmpty() })
            scanOffset = wordEnd
        }
        result
    }

    private suspend fun romanizeWithIcu(text: String): String =
        withContext(Dispatchers.Default) {
            genericRomanizationTransliterator.get().transliterate(text)
        }

    private fun normalizeRomanizedText(
        original: String,
        romanized: String?,
    ): String? {
        val normalized =
            romanized
                ?.replace(WHITESPACE_REGEX, " ")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: return null

        return normalized.takeUnless { it.equals(original.trim(), ignoreCase = true) }
    }

    private fun looksJapanese(text: String): Boolean {
        // Fast path: kana (Hiragana/Katakana) or iteration marks always indicate
        // Japanese. This matches the previous behavior.
        if (
            text.any {
                hasScript(it, UnicodeScript.HIRAGANA) ||
                    hasScript(it, UnicodeScript.KATAKANA) ||
                    it == '々' ||
                    it == '〆' ||
                    it == 'ヶ'
            }
        ) {
            return true
        }
        // Kanji-only text: ambiguous between Japanese and Chinese. Treat it as
        // Japanese ONLY when the Kuromoji language pack is installed — otherwise
        // `romanizeJapanese` would silently return the original text unchanged
        // (a no-op), and the user would see no romanization at all. When the
        // pack isn't installed, fall through so the Chinese ICU path can attempt
        // romanization instead.
        val hasKanji = text.any { it in '\u4E00'..'\u9FFF' }
        return hasKanji && JapaneseLanguagePackManager.tokenizerOrNull() != null
    }

    private fun hasScript(
        char: Char,
        script: UnicodeScript,
    ): Boolean = char.isLetter() && UnicodeScript.of(char.code) == script
}
