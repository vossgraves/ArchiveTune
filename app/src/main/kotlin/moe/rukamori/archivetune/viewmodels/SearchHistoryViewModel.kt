/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.SearchHistory
import javax.inject.Inject

/** Exposes the persistent `search_history` table to the redesigned Search screen. */
@HiltViewModel
class SearchHistoryViewModel
    @Inject
    constructor(
        private val database: MusicDatabase,
    ) : ViewModel() {
        val recentSearches: StateFlow<List<SearchHistory>> =
            database
                .searchHistory()
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000L),
                    initialValue = emptyList(),
                )

        fun delete(history: SearchHistory) {
            viewModelScope.launch {
                database.query { delete(history) }
            }
        }

        fun clearAll() {
            viewModelScope.launch {
                database.query { clearSearchHistory() }
            }
        }
    }
