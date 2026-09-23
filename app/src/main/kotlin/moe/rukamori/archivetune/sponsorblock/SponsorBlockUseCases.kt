/*
 * ArchiveTune (2026)
 * © vossgraves — github.com/vossgraves
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.sponsorblock

import android.content.Context
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import moe.rukamori.archivetune.constants.SponsorBlockApiUrlKey
import moe.rukamori.archivetune.constants.SponsorBlockCategoriesKey
import moe.rukamori.archivetune.constants.SponsorBlockEnabledKey
import moe.rukamori.archivetune.utils.dataStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The settings surface of SponsorBlock, as the playback settings screen needs it.
 *
 * Segment fetching belongs to [SponsorBlockRepository]; these cases cover the three stored
 * preferences and the URL validation the editor needs, so the screen never writes a
 * preference key of its own.
 */
class ObserveSponsorBlockSettingsUseCase
    @Inject
    constructor(
        private val repository: SponsorBlockRepository,
    ) {
        // Single mapping lives in the repository; this only forwards it so the
        // entry point keeps its shape for the settings screen.
        operator fun invoke(): Flow<SponsorBlockSettings> = repository.observeSettings()
    }

class SetSponsorBlockEnabledUseCase
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        suspend operator fun invoke(enabled: Boolean) {
            context.dataStore.edit { it[SponsorBlockEnabledKey] = enabled }
        }
    }

class SetSponsorBlockCategoriesUseCase
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        suspend operator fun invoke(categories: Set<SponsorBlockCategory>) {
            context.dataStore.edit { preferences ->
                preferences[SponsorBlockCategoriesKey] = categories.map(SponsorBlockCategory::apiName).toSet()
            }
        }
    }

class SetSponsorBlockApiUrlUseCase
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        suspend operator fun invoke(apiUrl: String) {
            val normalized = requireNotNull(normalizeSponsorBlockApiUrl(apiUrl)) { "Unusable SponsorBlock API URL" }
            context.dataStore.edit { it[SponsorBlockApiUrlKey] = normalized }
        }
    }

class ValidateSponsorBlockApiUrlUseCase
    @Inject
    constructor() {
        operator fun invoke(apiUrl: String): Boolean = normalizeSponsorBlockApiUrl(apiUrl) != null
    }

/**
 * Segments to skip for [videoId], honouring the settings passed in.
 *
 * Deliberately delegates to [SponsorBlockRepository.segments]: that call already validates the
 * id, respects the enabled flag and the selected categories, merges overlaps, and answers an
 * empty list — never a failure — when the service is unreachable, so playback never blocks on it.
 */
@Singleton
class GetSponsorBlockSegmentsUseCase
    @Inject
    constructor(
        private val repository: SponsorBlockRepository,
    ) {
        suspend operator fun invoke(
            videoId: String,
            settings: SponsorBlockSettings? = null,
        ): List<SponsorBlockSegment> {
            val resolved = settings ?: repository.settings()
            return if (!resolved.enabled || resolved.categories.isEmpty()) {
                emptyList()
            } else {
                repository.segments(videoId)
            }
        }
    }
}
