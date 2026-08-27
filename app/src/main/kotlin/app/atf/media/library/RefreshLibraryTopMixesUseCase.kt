/*
 * ArchiveTune (2026)
 * © ArchiveTuneFork contributors — github.com/vossgraves/ArchiveTune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package app.atf.media.library

import android.content.Context
import com.google.common.collect.ImmutableList
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import androidx.datastore.preferences.core.edit
import app.atf.media.ai.AiRateLimiter
import app.atf.media.ai.AiServiceConfig
import app.atf.media.ai.AiTextService
import app.atf.media.constants.AiApiKeyKey
import app.atf.media.constants.AiMixLastGeneratedAtKey
import app.atf.media.constants.AiApiValidationStatus
import app.atf.media.constants.AiApiValidationStatusKey
import app.atf.media.constants.AiCustomEndpointKey
import app.atf.media.constants.AiCustomModelKey
import app.atf.media.constants.AiProvider
import app.atf.media.constants.AiProviderKey
import app.atf.media.constants.AiSelectedModelKey
import app.atf.media.db.entities.Song
import app.atf.media.extensions.toEnum
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.SongItem
import app.atf.media.models.MediaMetadata
import app.atf.media.models.toMediaMetadata
import app.atf.media.repository.LibraryTopMixRepository
import app.atf.media.utils.dataStore
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import javax.inject.Inject

private const val TopMixCountLimit = 5
private const val TopMixMinRefreshIntervalMs = 10L * 60_000L
private const val TopMixCandidateLimit = 100
private const val TopMixSongsPerMix = 50
private const val TopMixPromptCandidateLimit = 80

class RefreshLibraryTopMixesUseCase
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val repository: LibraryTopMixRepository,
    ) {
        suspend operator fun invoke(): RefreshLibraryTopMixesResult {
            val config = readAiConfig()
            if (!config.canCallApi || isAiValidationFailed()) {
                return RefreshLibraryTopMixesResult.Failure(TopMixGenerationFailure.AI_NOT_CONFIGURED)
            }

            // Token-budget protector: mixes are regenerated from a large prompt, so refuse
            // regeneration while the previous result is still fresh (survives app restarts).
            val now = System.currentTimeMillis()
            val lastGeneratedAt = context.dataStore.data.first()[AiMixLastGeneratedAtKey] ?: 0L
            if (now - lastGeneratedAt in 0 until TopMixMinRefreshIntervalMs) {
                return RefreshLibraryTopMixesResult.Failure(TopMixGenerationFailure.RATE_LIMITED)
            }

            val candidates =
                repository
                    .recentSongsForTopMixes(TopMixCandidateLimit)
                    .validateWithYtm()
                    .take(TopMixPromptCandidateLimit)
            if (candidates.isEmpty()) {
                return RefreshLibraryTopMixesResult.Failure(TopMixGenerationFailure.NO_RECENT_HISTORY)
            }

            return runCatching {
                val mixes = requestAiMixes(config = config, candidates = candidates)
                if (mixes.isEmpty()) {
                    RefreshLibraryTopMixesResult.Failure(TopMixGenerationFailure.NO_VALID_MIXES)
                } else {
                    repository.replaceTopMixes(mixes)
                    context.dataStore.edit { it[AiMixLastGeneratedAtKey] = now }
                    RefreshLibraryTopMixesResult.Success
                }
            }.getOrElse { throwable ->
                RefreshLibraryTopMixesResult.Failure(
                    reason = TopMixGenerationFailure.AI_REQUEST_FAILED,
                    cause = throwable,
                )
            }
        }

        private suspend fun readAiConfig(): AiServiceConfig {
            val prefs = context.dataStore.data.first()
            val provider = prefs[AiProviderKey].toEnum(AiProvider.NONE)
            return AiServiceConfig(
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
        }

        private suspend fun isAiValidationFailed(): Boolean =
            context.dataStore.data
                .first()[AiApiValidationStatusKey]
                .toEnum(AiApiValidationStatus.UNKNOWN) == AiApiValidationStatus.FAILED

        private suspend fun requestAiMixes(
            config: AiServiceConfig,
            candidates: List<ValidatedTopMixSong>,
        ): List<GeneratedLibraryTopMix> {
            val candidateById = candidates.associateBy { it.id }
            val candidatePayload =
                JSONArray().apply {
                    candidates.forEachIndexed { index, candidate ->
                        put(
                            JSONObject()
                                .put("id", candidate.id)
                                .put("title", candidate.title)
                                .put("artists", candidate.artists.joinToString(", "))
                                .put("album", candidate.albumName.orEmpty())
                                .put("recentRank", index + 1),
                        )
                    }
                }
            val response =
                AiRateLimiter.withLimit(AiRateLimiter.Feature.AI_MIX) {
                    AiTextService.complete(
                        config = config,
                    systemPrompt =
                        """
                        You are a music curator for ArchiveTune.
                        Analyze the provided candidate list of songs (representing the user's recent listening history), focusing on artists, titles, and album names to infer genres, styles, eras, and moods.
                        Build up to $TopMixCountLimit personal mixes based on this history.
                        Each mix should have a distinct musical identity, style, mood, or theme.
                        While some overlap of songs between different mixes is fine and expected for your favorite tracks, the mixes should be substantially different from one another.
                        Select at most $TopMixSongsPerMix songs per mix, avoid duplicate songs inside a single mix, and prioritize transition flow and genre coherence.
                        Do not force every candidate song to be used if it doesn't fit any theme. Prioritize quality over quantity.
                        
                        For each mix, in addition to selecting relevant songs from the candidates, you MUST recommend 3 to 5 top tracks from similar artists of the same genre for each selected candidate song in this mix (e.g. if a mix contains 3 candidate songs, recommend 9 to 15 similar songs; if it contains only 1 candidate, recommend 3 to 5 similar songs). The total songs (candidates + recommended) in each mix must not exceed $TopMixSongsPerMix. Make sure the recommendations are from similar artists of the same genre that are not in the candidates list.
                        
                        Return JSON only matching this schema: {"mixes":[{"title":"Descriptive Mix Title","description":"Vibrant and appealing description of the vibe and genre","songIds":["id"],"recommendations":[{"title":"Song Title","artist":"Artist Name"}]}]}.
                        Every title must contain the word "Mix" (e.g. "90s Grunge Mix", "Late Night Vibes Mix", "Synthwave Drive Mix").
                        """.trimIndent(),
                        userPrompt =
                            JSONObject()
                                .put("basis", "recent listening history")
                                .put("maxMixes", TopMixCountLimit)
                                .put("maxSongsPerMix", TopMixSongsPerMix)
                                .put("candidates", candidatePayload)
                                .toString(),
                        temperature = 0.35,
                        maxTokens = 4096,
                    )
                }
            return parseGeneratedMixes(
                response = response,
                candidateById = candidateById,
            )
        }

        private suspend fun parseGeneratedMixes(
            response: String,
            candidateById: Map<String, ValidatedTopMixSong>,
        ): List<GeneratedLibraryTopMix> {
            val json = JSONObject(response.substringAfter('{').substringBeforeLast('}').let { "{$it}" })
            val mixes = json.optJSONArray("mixes") ?: JSONArray()

            val recQueries =
                buildList {
                    for (index in 0 until mixes.length()) {
                        val mixJson = mixes.optJSONObject(index) ?: continue
                        val recs = mixJson.optJSONArray("recommendations") ?: JSONArray()
                        for (r in 0 until recs.length()) {
                            val recJson = recs.optJSONObject(r) ?: continue
                            val title = recJson.optString("title").trim()
                            val artist = recJson.optString("artist").trim()
                            if (title.isNotEmpty() && artist.isNotEmpty()) {
                                add(title to artist)
                            }
                        }
                    }
                }.distinct()

            val resolvedRecommendations =
                kotlinx.coroutines.coroutineScope {
                    recQueries
                        .map { (title, artist) ->
                            async(kotlinx.coroutines.Dispatchers.IO) {
                                val query = "$artist $title"
                                val songs =
                                    YouTube
                                        .search(query, YouTube.SearchFilter.FILTER_SONG)
                                        .getOrNull()
                                        ?.items
                                        .orEmpty()
                                        .filterIsInstance<SongItem>()

                                val match =
                                    songs.firstOrNull { song ->
                                        song.title.contains(title, ignoreCase = true) &&
                                            song.artists.any { it.name.contains(artist, ignoreCase = true) }
                                    } ?: songs.firstOrNull()

                                if (match != null) {
                                    (title to artist) to ValidatedTopMixSong(match)
                                } else {
                                    null
                                }
                            }
                        }.awaitAll()
                        .filterNotNull()
                        .toMap()
                }

            return buildList {
                for (index in 0 until mixes.length()) {
                    if (size >= TopMixCountLimit) break
                    val mixJson = mixes.optJSONObject(index) ?: continue

                    val candidatesList =
                        mixJson
                            .optJSONArray("songIds")
                            .toStringList()
                            .mapNotNull(candidateById::get)

                    val recommendedList =
                        buildList {
                            val recs = mixJson.optJSONArray("recommendations") ?: JSONArray()
                            for (r in 0 until recs.length()) {
                                val recJson = recs.optJSONObject(r) ?: continue
                                val title = recJson.optString("title").trim()
                                val artist = recJson.optString("artist").trim()
                                val resolved = resolvedRecommendations[title to artist]
                                if (resolved != null) {
                                    add(resolved)
                                }
                            }
                        }

                    val selected =
                        (candidatesList + recommendedList)
                            .distinctBy { it.id }
                            .take(TopMixSongsPerMix)

                    if (selected.isEmpty()) continue

                    add(
                        GeneratedLibraryTopMix(
                            id = "ai_top_mix_${System.currentTimeMillis()}_$index",
                            title = mixJson.optString("title").sanitizeMixTitle(),
                            description = mixJson.optString("description").sanitizeMixDescription(),
                            tracks = ImmutableList.copyOf(selected.map { it.mediaMetadata }),
                        ),
                    )
                }
            }
        }
    }

sealed interface RefreshLibraryTopMixesResult {
    data object Success : RefreshLibraryTopMixesResult

    data class Failure(
        val reason: TopMixGenerationFailure,
        val cause: Throwable? = null,
    ) : RefreshLibraryTopMixesResult
}

enum class TopMixGenerationFailure {
    AI_NOT_CONFIGURED,
    NO_RECENT_HISTORY,
    NO_VALID_MIXES,
    AI_REQUEST_FAILED,
    RATE_LIMITED,
}

private data class ValidatedTopMixSong(
    val ytmSong: SongItem,
) {
    val id: String
        get() = ytmSong.id
    val title: String
        get() = ytmSong.title
    val artists: List<String>
        get() = ytmSong.artists.map { it.name }
    val albumName: String?
        get() = ytmSong.album?.name
    val mediaMetadata: MediaMetadata
        get() = ytmSong.toMediaMetadata()
}

private suspend fun List<Song>.validateWithYtm(): List<ValidatedTopMixSong> {
    val localSongs = distinctBy { it.id }
    return buildList {
        localSongs.forEach { song ->
            val validatedSong = song.validateWithYtm()
            if (validatedSong != null) {
                add(validatedSong)
            }
        }
    }
}

private suspend fun Song.validateWithYtm(): ValidatedTopMixSong? {
    val query = ytmValidationQuery()
    if (query.isBlank()) return null
    val songs =
        YouTube
            .search(query, YouTube.SearchFilter.FILTER_SONG)
            .getOrNull()
            ?.items
            .orEmpty()
            .filterIsInstance<SongItem>()
    val match =
        songs.firstOrNull { it.id == id }
            ?: songs.firstOrNull { it.matchesLocalIdentity(this) }
    return match?.let { songItem ->
        ValidatedTopMixSong(
            ytmSong = songItem,
        )
    }
}

private fun Song.ytmValidationQuery(): String =
    buildString {
        append(song.title)
        val artistNames = artists.joinToString(" ") { it.name }
        if (artistNames.isNotBlank()) {
            append(' ')
            append(artistNames)
        }
    }.trim()

private fun SongItem.matchesLocalIdentity(song: Song): Boolean =
    title.matchesComparableTitle(song.song.title) && artists.matchesAnyLocalArtist(song)

private fun String.matchesComparableTitle(other: String): Boolean {
    val self = normalizedForMixMatch()
    val target = other.normalizedForMixMatch()
    return self.isNotBlank() &&
        target.isNotBlank() &&
        (self == target || (self.length > 3 && target.contains(self)) || (target.length > 3 && self.contains(target)))
}

private fun List<moe.rukamori.archivetune.innertube.models.Artist>.matchesAnyLocalArtist(song: Song): Boolean {
    val remoteArtists = map { it.name.normalizedForMixMatch() }.filter { it.isNotBlank() }
    val localArtists = song.artists.map { it.name.normalizedForMixMatch() }.filter { it.isNotBlank() }
    if (remoteArtists.isEmpty() || localArtists.isEmpty()) return false
    return localArtists.any { localArtist ->
        remoteArtists.any { remoteArtist ->
            localArtist == remoteArtist ||
                (localArtist.length > 3 && remoteArtist.contains(localArtist)) ||
                (remoteArtist.length > 3 && localArtist.contains(remoteArtist))
        }
    }
}

private fun JSONArray?.toStringList(): List<String> =
    if (this == null) {
        emptyList()
    } else {
        List(length()) { index -> optString(index) }.filter { it.isNotBlank() }
    }

private fun String.sanitizeMixTitle(): String {
    val title =
        lineSequence()
            .firstOrNull()
            .orEmpty()
            .trim()
            .take(80)
            .ifBlank { "Recent Listening Mix" }
    return if (title.contains("mix", ignoreCase = true)) title else "$title Mix"
}

private fun String.sanitizeMixDescription(): String =
    lineSequence()
        .firstOrNull()
        .orEmpty()
        .trim()
        .take(120)
        .ifBlank { "A genre-focused mix based on your recent listening history." }

private fun String.normalizedForMixMatch(): String =
    lowercase(Locale.getDefault())
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")
