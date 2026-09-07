/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.spotify

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.utils.reportException
import javax.inject.Inject

@HiltViewModel
class SpotifyAccountViewModel
    @Inject
    constructor(
        private val repository: SpotifyLibraryRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(SpotifyAccountUiState(isLoading = true))
        val uiState: StateFlow<SpotifyAccountUiState> = _uiState.asStateFlow()

        init {
            restoreSession()
            // The count follows the repository rather than being assigned at the end of a refresh:
            // the repository is a @Singleton, so a refresh started from the Library page keeps this
            // screen's count right too, and the cache restored below shows up without a network call.
            viewModelScope.launch {
                repository.playlists.collect { playlists ->
                    _uiState.update { it.copy(playlistCount = playlists.size) }
                }
            }
        }

        fun restoreSession() {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { repository.restoreSession() }
                    .onSuccess { session ->
                        _uiState.update {
                            it.copy(
                                isAuthenticated = session.isAuthenticated,
                                accountName = session.accountName,
                                accountAvatarUrl = session.accountAvatarUrl,
                                isLoading = false,
                            )
                        }
                        // Reads the playlists already on disk. Without it the page reported "0
                        // playlists" for a connected account until the user hit reload by hand:
                        // the auto-refresh that used to populate the count was removed below, and
                        // nothing took over the job of filling it in from the cache.
                        if (session.isAuthenticated) {
                            runCatching { repository.restoreCachedPlaylists() }
                                .onFailure { error ->
                                    if (error is CancellationException) throw error
                                    reportException(error)
                                }
                        }
                        // Loading-perf fix (ported from 4nx3b batch-8, 2026-08-29): do NOT
                        // auto-reloadPlaylists() here. This VM is instantiated whenever the
                        // Integrations screen opens, and the repository is a @Singleton — so the
                        // auto-refresh flipped `_isRefreshing = true` app-wide and the Spotify
                        // Playlists Library page (observing the same flow) showed its spinner
                        // for the entire multi-second fetch. The user can pull-to-refresh or tap
                        // the refresh button on that page; the cached playlist list still renders
                        // instantly on cold launch.
                    }.onFailure { error ->
                        if (error is CancellationException) throw error
                        reportException(error)
                        _uiState.update {
                            it.copy(
                                isAuthenticated = false,
                                isLoading = false,
                                errorMessage = error.message,
                            )
                        }
                    }
            }
        }

        fun connectWithCookies(
            spDc: String,
            spKey: String,
        ) {
            if (spDc.isBlank()) return
            viewModelScope.launch(Dispatchers.IO) {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                runCatching { repository.connectWithCookies(spDc = spDc, spKey = spKey) }
                    .onSuccess { session ->
                        _uiState.update {
                            it.copy(
                                isAuthenticated = true,
                                accountName = session.accountName,
                                accountAvatarUrl = session.accountAvatarUrl,
                                isLoading = false,
                            )
                        }
                        reloadPlaylists()
                    }.onFailure { error ->
                        if (error is CancellationException) throw error
                        reportException(error)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = error.message,
                            )
                        }
                    }
            }
        }

        fun reloadPlaylists() {
            if (_uiState.value.isLoading) return
            viewModelScope.launch(Dispatchers.IO) {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                repository.refreshPlaylists()
                // playlistCount is not set here: the collector in init owns it.
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = repository.errorMessage.value,
                    )
                }
            }
        }

        fun logout() {
            if (_uiState.value.isLoading) return
            viewModelScope.launch(Dispatchers.IO) {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                runCatching { repository.logout() }
                    .onSuccess {
                        _uiState.value = SpotifyAccountUiState()
                    }.onFailure { error ->
                        if (error is CancellationException) throw error
                        reportException(error)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = error.message,
                            )
                        }
                    }
            }
        }

        fun dismissError() {
            _uiState.update { it.copy(errorMessage = null) }
        }
    }

@Immutable
data class SpotifyAccountUiState(
    val isAuthenticated: Boolean = false,
    val accountName: String = "",
    val accountAvatarUrl: String? = null,
    val playlistCount: Int = 0,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)
