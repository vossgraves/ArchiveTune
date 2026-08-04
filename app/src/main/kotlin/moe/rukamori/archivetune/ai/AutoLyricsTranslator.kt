/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ai

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.constants.AiApiKeyKey
import moe.rukamori.archivetune.constants.AiCustomEndpointKey
import moe.rukamori.archivetune.constants.AiCustomModelKey
import moe.rukamori.archivetune.constants.AiProvider
import moe.rukamori.archivetune.constants.AiProviderKey
import moe.rukamori.archivetune.constants.AiSelectedModelKey
import moe.rukamori.archivetune.constants.AutoTranslateLyricsKey
import moe.rukamori.archivetune.constants.TranslatorTargetLangKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.LyricsEntity
import moe.rukamori.archivetune.extensions.toEnum
import moe.rukamori.archivetune.lyrics.LyricsUtils
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.utils.dataStore
import javax.inject.Inject

class AutoLyricsTranslator
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val database: MusicDatabase,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private var job: Job? = null

        fun onLyricsFetched(mediaMetadata: MediaMetadata, lyricsText: String?) {
            job?.cancel()
            if (lyricsText.isNullOrBlank()) return
            if (lyricsText == LyricsEntity.LYRICS_NOT_FOUND) return
            job =
                scope.launch {
                    try {
                        val prefs = context.dataStore.data.first()
                        val enabled = prefs[AutoTranslateLyricsKey] ?: false
                        if (!enabled) return@launch
                        val provider = prefs[AiProviderKey].toEnum(AiProvider.NONE)
                        if (provider == AiProvider.NONE) return@launch
                        if (prefs[AiApiKeyKey].isNullOrBlank() && provider != AiProvider.CUSTOM) return@launch

                        val existing = database.lyrics(mediaMetadata.id).first()
                        if (existing != null && existing.source == LyricsEntity.Source.AI_TRANSLATION.value) return@launch

                        val targetLanguage = prefs[TranslatorTargetLangKey].orEmpty()
                        if (!LyricsUtils.shouldAutoTranslate(lyricsText, targetLanguage)) return@launch

                        val config =
                            AiServiceConfig(
                                provider = provider,
                                apiKey = prefs[AiApiKeyKey].orEmpty(),
                                customEndpoint = prefs[AiCustomEndpointKey].orEmpty(),
                                model =
                                    if (provider == AiProvider.CUSTOM) {
                                        prefs[AiCustomModelKey].orEmpty()
                                    } else {
                                        prefs[AiSelectedModelKey].orEmpty()
                                    },
                            )
                        val translated =
                            AiLyricsTranslator().translate(
                                config = config,
                                lyrics = lyricsText,
                                targetLanguage = targetLanguage,
                            )
                        if (translated.isBlank() || translated == lyricsText) return@launch
                        database.query {
                            replaceLyrics(
                                id = mediaMetadata.id,
                                lyrics = translated,
                                source = LyricsEntity.Source.AI_TRANSLATION.value,
                            )
                        }
                        Log.d(TAG, "Auto-translated lyrics for: ${mediaMetadata.title}")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Auto-translation failed for ${mediaMetadata.title}: ${e.message}")
                    }
                }
        }

        fun cancel() {
            job?.cancel()
            job = null
        }

        fun destroy() {
            cancel()
            scope.coroutineContext[Job]?.cancel()
        }

        companion object {
            private const val TAG = "AutoLyricsTranslator"
        }
    }
