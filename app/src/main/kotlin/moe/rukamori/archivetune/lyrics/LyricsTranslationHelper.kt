/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Lyrics translation dispatcher. Ported from vivi-music
 * (https://github.com/vivizzz007/vivi-music) under GPL-3.0.
 *
 * Routes translation/romanization requests to the configured AI provider:
 *   - DeepL (batched line-by-line, free or pro tier based on key suffix)
 *   - OpenRouter (OpenAI-compatible chat completions; user-supplied base URL + model)
 *   - Mistral (chat completions on api.mistral.ai)
 *
 * Each provider returns a list of translated lines aligned to the input. When the
 * configured provider fails, the helper returns null so the caller can fall back to
 * displaying the original lyrics.
 */

package moe.rukamori.archivetune.lyrics

import android.content.Context
import moe.rukamori.archivetune.api.DeepLService
import moe.rukamori.archivetune.api.MistralService
import moe.rukamori.archivetune.api.OpenRouterService
import moe.rukamori.archivetune.constants.AiApiKeyKey
import moe.rukamori.archivetune.constants.AiCustomEndpointKey
import moe.rukamori.archivetune.constants.AiCustomModelKey
import moe.rukamori.archivetune.constants.AiProvider
import moe.rukamori.archivetune.constants.AiProviderKey
import moe.rukamori.archivetune.constants.AiSelectedModelKey
import moe.rukamori.archivetune.constants.DeeplApiKeyKey
import moe.rukamori.archivetune.constants.DeeplFormalityKey
import moe.rukamori.archivetune.constants.OpenRouterApiKeyKey
import moe.rukamori.archivetune.constants.OpenRouterBaseUrlKey
import moe.rukamori.archivetune.constants.OpenRouterModelKey
import moe.rukamori.archivetune.constants.TranslateModeKey
import moe.rukamori.archivetune.constants.TranslateSourceLanguageKey
import moe.rukamori.archivetune.utils.dataStore

object LyricsTranslationHelper {
    /**
     * Translate [text] into [targetLanguage] using the configured AI provider.
     *
     * @param mode "translate" or "romanize"
     * @return the translated text on success, null on failure or when no provider is configured.
     */
    suspend fun translate(
        context: Context,
        text: String,
        targetLanguage: String,
        mode: String = "translate",
        sourceLanguage: String? = null,
    ): String? {
        if (text.isBlank()) return null
        val providerName = context.dataStore.get(AiProviderKey, AiProvider.NONE.name)
        val provider = AiProvider.entries.firstOrNull { it.name == providerName } ?: AiProvider.NONE
        val effectiveSource = sourceLanguage ?: context.dataStore.get(TranslateSourceLanguageKey, "").ifBlank { null }
        val effectiveMode = mode.ifBlank { context.dataStore.get(TranslateModeKey, "translate") }

        return when (provider) {
            AiProvider.DEEPL -> {
                val apiKey = context.dataStore.get(DeeplApiKeyKey, "")
                if (apiKey.isBlank()) return null
                val formality = context.dataStore.get(DeeplFormalityKey, "default")
                DeepLService.translate(text, targetLanguage, apiKey, formality)
                    .getOrNull()?.joinToString("\n")
            }
            AiProvider.OPENROUTER -> {
                val apiKey = context.dataStore.get(OpenRouterApiKeyKey, "")
                if (apiKey.isBlank()) return null
                val baseUrl = context.dataStore.get(OpenRouterBaseUrlKey, "")
                val model = context.dataStore.get(OpenRouterModelKey, "openai/gpt-4o-mini")
                OpenRouterService.translate(text, targetLanguage, apiKey, baseUrl, model, effectiveMode, sourceLanguage = effectiveSource)
                    .getOrNull()?.joinToString("\n")
            }
            AiProvider.MISTRAL -> {
                val apiKey = context.dataStore.get(AiApiKeyKey, "")
                if (apiKey.isBlank()) return null
                val model = context.dataStore.get(AiSelectedModelKey, "mistral-small-latest").ifBlank { "mistral-small-latest" }
                MistralService.translate(text, targetLanguage, apiKey, model, effectiveMode, sourceLanguage = effectiveSource)
                    .getOrNull()?.joinToString("\n")
            }
            AiProvider.CHATGPT, AiProvider.GEMINI, AiProvider.CUSTOM -> {
                // Existing chat-based providers route through AiSelectedModelKey / AiCustomModelKey.
                // For OpenAI-compatible chat translation, route via OpenRouterService using the user's
                // custom endpoint + API key + model so the same line-alignment logic is reused.
                val apiKey = context.dataStore.get(AiApiKeyKey, "")
                if (apiKey.isBlank()) return null
                val baseUrl = context.dataStore.get(AiCustomEndpointKey, "")
                val model =
                    context.dataStore.get(AiSelectedModelKey, "")
                        .ifBlank { context.dataStore.get(AiCustomModelKey, "gpt-4o-mini") }
                OpenRouterService.translate(text, targetLanguage, apiKey, baseUrl, model, effectiveMode, sourceLanguage = effectiveSource)
                    .getOrNull()?.joinToString("\n")
            }
            AiProvider.NONE -> null
        }
    }
}
