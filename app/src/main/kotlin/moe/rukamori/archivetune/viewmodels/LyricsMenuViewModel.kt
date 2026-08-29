/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.common.collect.ImmutableList
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ai.AiLyricsTranslator
import moe.rukamori.archivetune.ai.AiServiceConfig
import moe.rukamori.archivetune.constants.AiApiKeyKey
import moe.rukamori.archivetune.constants.AiApiValidationStatus
import moe.rukamori.archivetune.constants.AiApiValidationStatusKey
import moe.rukamori.archivetune.constants.AiCustomEndpointKey
import moe.rukamori.archivetune.constants.AiCustomModelKey
import moe.rukamori.archivetune.constants.AiProvider
import moe.rukamori.archivetune.constants.AiProviderKey
import moe.rukamori.archivetune.constants.AiSelectedModelKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.LyricsEntity
import moe.rukamori.archivetune.extensions.toEnum
import moe.rukamori.archivetune.lyrics.LyricsHelper
import moe.rukamori.archivetune.lyrics.LyricsResult
import moe.rukamori.archivetune.lyrics.LyricsUtils
import moe.rukamori.archivetune.lyrics.LyricsUtils.displayLyricsText
import moe.rukamori.archivetune.lyrics.LyricsUtils.isLineSyncedLrc
import moe.rukamori.archivetune.lyrics.LyricsUtils.isTtml
import moe.rukamori.archivetune.constants.AutoTranslateExcludedLanguagesKey
import moe.rukamori.archivetune.constants.AutoTranslateLyricsKey
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.utils.NetworkConnectivityObserver
import moe.rukamori.archivetune.utils.dataStore
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

sealed interface LyricsSearchScreenState {
    data object Loading : LyricsSearchScreenState

    @Immutable
    data class Success(
        val results: ImmutableList<LyricsSearchResultUiModel>,
        val isSearching: Boolean,
    ) : LyricsSearchScreenState

    data object Empty : LyricsSearchScreenState

    @Immutable
    data class Error(
        @StringRes val messageResId: Int,
    ) : LyricsSearchScreenState
}

@Immutable
data class LyricsSearchResultUiModel(
    val id: String,
    val providerName: String,
    val lyrics: String,
    val preview: String,
    val lineCount: Int,
    val characterCount: Int,
    val isLineSynced: Boolean,
    val isWordSynced: Boolean,
)

data class LyricsTranslationUndoSnapshot(
    val mediaId: String,
    val lyrics: String,
    val source: String,
    val providerName: String,
)

@HiltViewModel
class LyricsMenuViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val lyricsHelper: LyricsHelper,
        val database: MusicDatabase,
        private val networkConnectivity: NetworkConnectivityObserver,
    ) : ViewModel() {
        private var job: Job? = null
        private var aiTranslationJob: Job? = null
        private val searchGeneration = AtomicLong(0L)
        private val _lyricsSearchState = MutableStateFlow<LyricsSearchScreenState>(LyricsSearchScreenState.Empty)
        val lyricsSearchState: StateFlow<LyricsSearchScreenState> = _lyricsSearchState.asStateFlow()
        private val _isRefetching = MutableStateFlow(false)
        val isRefetching: StateFlow<Boolean> = _isRefetching.asStateFlow()
        private val _refetchCompletionEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val refetchCompletionEvents: SharedFlow<Unit> = _refetchCompletionEvents.asSharedFlow()
        val isAiTranslating = MutableStateFlow(false)
        private val _translationUndo = MutableStateFlow<LyricsTranslationUndoSnapshot?>(null)
        val translationUndo: StateFlow<LyricsTranslationUndoSnapshot?> = _translationUndo.asStateFlow()

        /**
         * Set of media IDs for which the user has clicked "Undo Translation".
         * While a media ID is in this set, auto-translation is suppressed —
         * the user explicitly reverted the translation and does NOT want it
         * re-translated automatically. The dismissal is cleared when the user
         * manually triggers translation again via [translateLyricsWithAi].
         *
         * This is session-scoped (in-memory) rather than persisted, because
         * the user's intent is "don't auto-translate this song again for now"
         * — not "never translate this song again forever". If the app is
         * restarted, auto-translate may fire once more; the user can undo
         * again if desired.
         */
        private val _translationDismissedMediaIds = MutableStateFlow<Set<String>>(emptySet())
        val translationDismissedMediaIds: StateFlow<Set<String>> =
            _translationDismissedMediaIds.asStateFlow()

        private val _aiTranslationEvents = MutableSharedFlow<String>()
        val aiTranslationEvents: SharedFlow<String> = _aiTranslationEvents.asSharedFlow()

        private val _isNetworkAvailable = MutableStateFlow(false)
        val isNetworkAvailable: StateFlow<Boolean> = _isNetworkAvailable.asStateFlow()

        init {
            viewModelScope.launch {
                networkConnectivity.networkStatus.collect { isConnected ->
                    _isNetworkAvailable.value = isConnected
                }
            }

            _isNetworkAvailable.value =
                try {
                    networkConnectivity.isCurrentlyConnected()
                } catch (e: Exception) {
                    true
                }
        }

        fun search(
            mediaId: String,
            title: String,
            artist: String,
            album: String?,
            duration: Int,
        ) {
            val generation = searchGeneration.incrementAndGet()
            job?.cancel()
            _lyricsSearchState.value = LyricsSearchScreenState.Loading
            job =
                viewModelScope.launch(Dispatchers.IO) {
                    val resultModels = mutableListOf<LyricsSearchResultUiModel>()
                    try {
                        lyricsHelper.getAllLyrics(
                            mediaId = mediaId,
                            songTitle = title,
                            songArtists = artist,
                            songAlbum = album,
                            duration = duration,
                            forceRefresh = true,
                        ) { result ->
                            if (generation != searchGeneration.get()) return@getAllLyrics
                            val model = result.toUiModel(resultModels.size)
                            if (model.preview.isBlank()) return@getAllLyrics

                            resultModels += model
                            _lyricsSearchState.value =
                                LyricsSearchScreenState.Success(
                                    results = ImmutableList.copyOf(resultModels),
                                    isSearching = true,
                                )
                        }
                        if (generation != searchGeneration.get()) return@launch
                        _lyricsSearchState.value =
                            if (resultModels.isEmpty()) {
                                LyricsSearchScreenState.Empty
                            } else {
                                LyricsSearchScreenState.Success(
                                    results = ImmutableList.copyOf(resultModels),
                                    isSearching = false,
                                )
                            }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        if (generation == searchGeneration.get()) {
                            _lyricsSearchState.value = LyricsSearchScreenState.Error(R.string.error_unknown)
                        }
                    }
                }
        }

        fun cancelSearch() {
            searchGeneration.incrementAndGet()
            job?.cancel()
            job = null
        }

        fun resetSearchState() {
            cancelSearch()
            _lyricsSearchState.value = LyricsSearchScreenState.Empty
        }

        fun refetchLyrics(mediaMetadata: MediaMetadata) {
            if (!_isRefetching.compareAndSet(expect = false, update = true)) return

            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val result = lyricsHelper.getLyricsWithProvider(mediaMetadata, forceRefresh = true)
                    database.withTransaction {
                        replaceLyrics(
                            id = mediaMetadata.id,
                            lyrics = result.lyrics,
                            source = LyricsEntity.Source.REMOTE.value,
                            providerName = result.providerName,
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                } finally {
                    _isRefetching.value = false
                    _refetchCompletionEvents.tryEmit(Unit)
                }
            }
        }

        fun updateLyrics(
            mediaMetadata: MediaMetadata,
            lyrics: String,
            source: LyricsEntity.Source = LyricsEntity.Source.USER_EDIT,
            providerName: String = "",
        ) {
            viewModelScope.launch(Dispatchers.IO) {
                // When AI_TRANSLATION is saved without an explicit providerName
                // (e.g. legacy translator menu at LyricsMenu.kt that calls
                // `updateLyrics(mediaMetadata, lyrics, source = AI_TRANSLATION)`
                // with no providerName argument), the default empty string
                // would otherwise WIPE the original provider's attribution —
                // causing both the constant overlay header AND the in-stream
                // SyncedLine header injected by `buildSyncedLyrics(providerHeader
                // = lyricsProviderLabel)` to vanish the moment translation lands.
                //
                // Resolution order when caller passed an empty providerName
                // for an AI_TRANSLATION save:
                //   1. captureLyricsBeforeTranslation (so the undo snapshot
                //      holds the original provider — captures is a no-op if
                //      the song is already captured or already AI_TRANSLATED).
                //   2. Try the undo snapshot's providerName for THIS song.
                //   3. Fall back to whatever the existing DB row currently
                //      has (covers the retry-AI-on-already-translated case
                //      where capture returns early without updating the
                //      snapshot because the existing source is already
                //      AI_TRANSLATION — without this branch, an empty
                //      snapshot or a different song's snapshot would let the
                //      empty string through and wipe the provider attribution).
                val effectiveProviderName =
                    if (source == LyricsEntity.Source.AI_TRANSLATION && providerName.isBlank()) {
                        captureLyricsBeforeTranslation(mediaMetadata.id)
                        _translationDismissedMediaIds.value =
                            _translationDismissedMediaIds.value - mediaMetadata.id
                        val fromSnapshot =
                            _translationUndo.value
                                ?.takeIf { it.mediaId == mediaMetadata.id }
                                ?.providerName
                                .orEmpty()
                        if (fromSnapshot.isNotBlank()) {
                            fromSnapshot
                        } else {
                            database
                                .withTransaction { getLyricsById(mediaMetadata.id) }
                                ?.providerName
                                .orEmpty()
                        }
                    } else {
                        providerName
                    }
                val lyricsToSave =
                    when (source) {
                        LyricsEntity.Source.REMOTE,
                        LyricsEntity.Source.EMBEDDED,
                        LyricsEntity.Source.USER_SELECTION,
                        -> LyricsUtils.lyricsOrNotFound(lyrics)

                        LyricsEntity.Source.USER_EDIT,
                        -> lyrics

                        LyricsEntity.Source.AI_TRANSLATION ->
                            usableTranslatedLyrics(lyrics) ?: return@launch
                    }
                database.query {
                    replaceLyrics(
                        id = mediaMetadata.id,
                        lyrics = lyricsToSave,
                        source = source.value,
                        providerName = effectiveProviderName,
                    )
                }
            }
        }

        fun translateLyricsWithAi(
            mediaMetadata: MediaMetadata,
            lyrics: String,
            targetLanguage: String,
        ) {
            if (isAiTranslating.value || lyrics.isBlank()) return
            // Clear the translation dismissal for this media ID — the user
            // is explicitly requesting a translation (either manually or via
            // auto-translate), so any previous "Undo Translation" dismissal
            // is no longer relevant. This ensures that after a manual
            // translation, future auto-translate can fire again if the lyrics
            // change back to a non-translated source.
            _translationDismissedMediaIds.value =
                _translationDismissedMediaIds.value - mediaMetadata.id
            aiTranslationJob =
                viewModelScope.launch(Dispatchers.IO) {
                    isAiTranslating.value = true
                    var isAutomatic = false
                    try {
                        val prefs = context.dataStore.data.first()
                        isAutomatic = prefs[AutoTranslateLyricsKey] ?: false
                        // Second line of defence for "Don't auto translate these languages".
                        //
                        // The callers gate on this too, but the setting had already been shipped
                        // broken once precisely because enforcement lived only in the UI layer: both
                        // trigger sites omitted the argument and `shouldAutoTranslate`'s default
                        // quietly accepted it. Every automatic translation funnels through here, so
                        // checking again makes the guard structural rather than a convention new
                        // call sites have to remember.
                        //
                        // Only for automatic runs: the setting says "don't *auto* translate", so a
                        // deliberate tap on "Translate with AI" must still work on an excluded
                        // language.
                        if (isAutomatic) {
                            val excluded = prefs[AutoTranslateExcludedLanguagesKey] ?: emptySet()
                            val dominant = LyricsUtils.detectDominantLanguageCode(lyrics)
                            if (dominant != null && LyricsUtils.matchesExcludedLanguage(dominant, excluded)) {
                                Log.d(
                                    TAG,
                                    "AI translate skipped: song=${mediaMetadata.title} " +
                                        "language=$dominant is excluded from auto-translation",
                                )
                                return@launch
                            }
                        }
                        Log.d(
                            TAG,
                            "AI translate start: song=${mediaMetadata.title} automatic=$isAutomatic " +
                                "provider=${prefs[AiProviderKey]} model=${prefs[AiSelectedModelKey]}",
                        )
                        val translatedLyrics =
                            AiLyricsTranslator().translate(
                                config =
                                    AiServiceConfig(
                                        provider = prefs[AiProviderKey].toEnum(AiProvider.NONE),
                                        apiKey = prefs[AiApiKeyKey].orEmpty(),
                                        customEndpoint = prefs[AiCustomEndpointKey].orEmpty(),
                                        model =
                                            if (prefs[AiProviderKey].toEnum(AiProvider.NONE) == AiProvider.CUSTOM) {
                                                prefs[AiCustomModelKey].orEmpty()
                                            } else {
                                                prefs[AiSelectedModelKey].orEmpty()
                                            },
                                    ),
                                lyrics = lyrics,
                                targetLanguage = targetLanguage.ifBlank { "ENGLISH" },
                            )
                        val usableLyrics = usableTranslatedLyrics(translatedLyrics)
                        if (usableLyrics == null) {
                            _aiTranslationEvents.emit(context.getString(R.string.translation_failed))
                            return@launch
                        }
                        saveTranslatedLyrics(
                            mediaId = mediaMetadata.id,
                            lyrics = usableLyrics,
                        )
                        Log.d(TAG, "AI translate success: song=${mediaMetadata.title} automatic=$isAutomatic")
                        if (!isAutomatic) {
                            val msg = context.getString(R.string.translation_success)
                            _aiTranslationEvents.emit(msg)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Always log the failure — auto-translations are silent (no toast),
                        // so without this log there's no way to diagnose why auto-translate
                        // "just stops working" after a few songs. The most common causes are:
                        //  - AiRateLimitException (hourly budget hit; now fixed to refund on failure)
                        //  - IOException (stale OkHttp pool; now fixed to recreate client on failure)
                        //  - HTTP 4xx (bad API key, quota exceeded, model deprecated)
                        //  - HTTP 5xx (provider outage)
                        Log.w(
                            TAG,
                            "AI translate failed: song=${mediaMetadata.title} automatic=$isAutomatic " +
                                "error=${e.javaClass.simpleName}: ${e.message}",
                        )
                        if (!isAutomatic) {
                            val msg = context.getString(R.string.translation_failed) + ": " + (e.localizedMessage ?: e.toString())
                            _aiTranslationEvents.emit(msg)
                        }
                    } finally {
                        isAiTranslating.value = false
                        aiTranslationJob = null
                    }
                }
        }

        private fun usableTranslatedLyrics(lyrics: String): String? =
            LyricsUtils
                .normalizeLyricsText(lyrics)
                .takeIf(LyricsUtils::hasMeaningfulLyricsContent)

        fun cancelAiTranslation() {
            aiTranslationJob?.cancel()
            aiTranslationJob = null
            isAiTranslating.value = false
        }

        fun undoTranslation(mediaId: String) {
            viewModelScope.launch(Dispatchers.IO) {
                val snapshot = _translationUndo.value?.takeIf { it.mediaId == mediaId } ?: return@launch
                database.withTransaction {
                    replaceLyrics(
                        id = snapshot.mediaId,
                        lyrics = snapshot.lyrics,
                        source = snapshot.source,
                        providerName = snapshot.providerName,
                    )
                }
                _translationUndo.value = null
                // Mark this media ID as "translation dismissed" so that
                // auto-translate does NOT re-translate it. The user clicked
                // "Undo Translation" — they explicitly do not want the
                // translation back. Auto-translate will be re-enabled for
                // this song only when the user manually triggers translation
                // again (which clears the dismissal via translateLyricsWithAi).
                _translationDismissedMediaIds.value =
                    _translationDismissedMediaIds.value + mediaId
            }
        }

        private suspend fun captureLyricsBeforeTranslation(mediaId: String) {
            if (_translationUndo.value?.mediaId == mediaId) return
            val existing = database.withTransaction { getLyricsById(mediaId) } ?: return
            if (existing.source == LyricsEntity.Source.AI_TRANSLATION.value) return
            _translationUndo.value =
                LyricsTranslationUndoSnapshot(
                    mediaId = existing.id,
                    lyrics = existing.lyrics,
                    source = existing.source,
                    providerName = existing.providerName,
                )
        }

        private suspend fun saveTranslatedLyrics(mediaId: String, lyrics: String) {
            captureLyricsBeforeTranslation(mediaId)
            // Preserve the ORIGINAL provider's name so the in-lyrics-stream
            // "Lyrics from [provider]" header does not disappear the moment
            // AI translation saves a new LyricsEntity. Previously the AI
            // translation path called `replaceLyrics(id, lyrics, source=AI_TRANSLATION)`
            // without passing `providerName`, which defaulted to "" —
            // wiping the provider attribution and causing both the constant
            // overlay header AND the in-stream SyncedLine header injected by
            // `buildSyncedLyrics(providerHeader = lyricsProviderLabel)` to
            // vanish on translation.
            //
            // Resolution order for the preserved providerName:
            //   1. The undo snapshot's providerName IF it was captured for
            //      THIS mediaId (covers the common path: user is auto-
            //      translating a song that still has the original REMOTE /
            //      EMBEDDED lyrics with a providerName, capture succeeds,
            //      snapshot holds it).
            //   2. The existing DB row's providerName as a fallback. This
            //      covers the edge case where capture returned early WITHOUT
            //      updating the snapshot because the existing source was
            //      already AI_TRANSLATION (e.g. a retry after a failed AI
            //      translation that left source=AI_TRANSLATION with no
            //      translation content). In that case `_translationUndo` is
            //      either null (fresh ViewModel) or holds a DIFFERENT song's
            //      snapshot — reading from the DB gives us the right answer
            //      rather than letting an empty string through.
            val snapshotMatch = _translationUndo.value?.takeIf { it.mediaId == mediaId }
            val preservedProviderName =
                snapshotMatch?.providerName?.takeIf { it.isNotBlank() }
                    ?: database
                        .withTransaction { getLyricsById(mediaId) }
                        ?.providerName
                        .orEmpty()
            database.query {
                replaceLyrics(
                    id = mediaId,
                    lyrics = lyrics,
                    source = LyricsEntity.Source.AI_TRANSLATION.value,
                    providerName = preservedProviderName,
                )
            }
        }

        private fun LyricsResult.toUiModel(index: Int): LyricsSearchResultUiModel {
            val preview = displayLyricsText(lyrics)
            val lineCount = preview.lineSequence().count { it.isNotBlank() }
            val isTtmlLyrics = isTtml(lyrics)
            val ttmlEntries =
                if (isTtmlLyrics) {
                    runCatching { LyricsUtils.parseTtml(lyrics) }.getOrDefault(emptyList())
                } else {
                    emptyList()
                }
            // Delegate to the shared detector so the badge shown here always agrees
            // with what the "Prioritize Word Synced Lyrics" override in LyricsHelper
            // considers word-synced. Previously this only checked TTML <span> entries,
            // which missed Enhanced LRC ([mm:ss.xxx]<mm:ss.xxx>word) returned by
            // YouLyPlus's fallback endpoint — causing the badge to say "Line Synced"
            // while the override (correctly) skipped it, or vice versa.
            val isWordSynced = LyricsUtils.hasWordSyncedLyrics(lyrics)

            return LyricsSearchResultUiModel(
                id = "${providerName}_${lyrics.hashCode()}_$index",
                providerName = providerName,
                lyrics = lyrics,
                preview = preview,
                lineCount = lineCount,
                characterCount = preview.length,
                isLineSynced =
                    if (isTtmlLyrics) {
                        ttmlEntries.isNotEmpty() && !isWordSynced
                    } else {
                        isLineSyncedLrc(lyrics) && !isWordSynced
                    },
                isWordSynced = isWordSynced,
            )
        }

        companion object {
            private const val TAG = "LyricsMenuViewModel"
        }
    }
