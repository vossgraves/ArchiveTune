/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ai

class AiLyricsTranslator {
    suspend fun translate(
        config: AiServiceConfig,
        lyrics: String,
        targetLanguage: String,
    ): String {
        val normalizedLanguage = normalizeTargetLanguage(targetLanguage)
        // Token-budget protector: translating the same lyrics to the same language with the same
        // model is deterministic enough to reuse — repeat requests cost zero tokens.
        val cacheKey = "${config.provider}|${config.model}|$normalizedLanguage|${lyrics.length}|${lyrics.hashCode()}"
        synchronized(resultCache) { resultCache[cacheKey] }?.let { return it }

        val document = AiLyricsDocumentParser.parse(lyrics)
        if (document.segments.isEmpty()) return lyrics
        val translated = mutableMapOf<Int, String>()
        document.segments.chunkedByBudget().forEach { batch ->
            val batchTranslations =
                AiTextService.translateLines(
                    config = config,
                    targetLanguage = normalizedLanguage,
                    lines = batch.map { it.text },
                    formatName = document.formatName,
                )
            batch.forEachIndexed { index, segment ->
                translated[segment.id] = batchTranslations[index]
            }
        }
        return document.rebuild(translated).also { result ->
            synchronized(resultCache) { resultCache[cacheKey] = result }
        }
    }

    private fun List<AiLyricsSegment>.chunkedByBudget(): List<List<AiLyricsSegment>> {
        val chunks = ArrayList<List<AiLyricsSegment>>()
        val current = ArrayList<AiLyricsSegment>()
        var currentChars = 0
        forEach { segment ->
            val nextSize = currentChars + segment.text.length
            if (current.isNotEmpty() && (current.size >= MaxItemsPerBatch || nextSize > MaxCharsPerBatch)) {
                chunks.add(current.toList())
                current.clear()
                currentChars = 0
            }
            current.add(segment)
            currentChars += segment.text.length
        }
        if (current.isNotEmpty()) chunks.add(current.toList())
        return chunks
    }

    private fun normalizeTargetLanguage(language: String): String =
        language
            .ifBlank { "ENGLISH" }
            .replace('_', ' ')
            .lowercase()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

    private companion object {
        const val MaxItemsPerBatch = 80
        const val MaxCharsPerBatch = 6000
        const val MaxCachedTranslations = 8

        /** Process-wide LRU of finished translations, shared across ViewModel instances. */
        val resultCache =
            object : LinkedHashMap<String, String>(MaxCachedTranslations, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>): Boolean =
                    size > MaxCachedTranslations
            }
    }
}
