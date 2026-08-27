/*
 * ArchiveTune (2026)
 * © ArchiveTuneFork contributors — github.com/vossgraves/ArchiveTune
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

package app.atf.media.lyrics

import android.content.Context
import app.atf.media.api.DeepLService
import app.atf.media.api.MistralService
import app.atf.media.api.OpenRouterService
import app.atf.media.constants.AiApiKeyKey
import app.atf.media.constants.AiCustomEndpointKey
import app.atf.media.constants.AiCustomModelKey
import app.atf.media.constants.AiProvider
import app.atf.media.constants.AiProviderKey
import app.atf.media.constants.AiSelectedModelKey
import app.atf.media.constants.DeeplApiKeyKey
import app.atf.media.constants.DeeplFormalityKey
import app.atf.media.constants.OpenRouterApiKeyKey
import app.atf.media.constants.OpenRouterBaseUrlKey
import app.atf.media.constants.OpenRouterModelKey
import app.atf.media.constants.TranslateModeKey
import app.atf.media.constants.TranslateSourceLanguageKey
import app.atf.media.utils.dataStore
import app.atf.media.utils.get

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
