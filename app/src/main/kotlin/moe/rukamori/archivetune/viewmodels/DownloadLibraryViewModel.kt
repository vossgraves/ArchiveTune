/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.downloads.DownloadEntryUiModel
import moe.rukamori.archivetune.downloads.DownloadLibraryUiModel
import moe.rukamori.archivetune.downloads.DownloadSectionUiModel
import moe.rukamori.archivetune.downloads.ManageDownloadsUseCase
import javax.inject.Inject
import java.util.concurrent.ConcurrentHashMap

enum class DownloadLibraryTab {
    DOWNLOADED,
    PROGRESS,
}

sealed interface DownloadLibraryScreenState {
    data class Loading(
        val selectedTab: DownloadLibraryTab,
    ) : DownloadLibraryScreenState

    data class Success(
        val selectedTab: DownloadLibraryTab,
        val library: DownloadLibraryUiModel,
    ) : DownloadLibraryScreenState

    data class Empty(
        val selectedTab: DownloadLibraryTab,
    ) : DownloadLibraryScreenState

    data class Error(
        val selectedTab: DownloadLibraryTab,
        @StringRes val messageRes: Int,
    ) : DownloadLibraryScreenState
}

@HiltViewModel
class DownloadLibraryViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val manageDownloads: ManageDownloadsUseCase,
    ) : ViewModel() {
        private val selectedTab =
            MutableStateFlow(
                if (savedStateHandle.get<String>("tab") == PROGRESS_TAB_ARGUMENT) {
                    DownloadLibraryTab.PROGRESS
                } else {
                    DownloadLibraryTab.DOWNLOADED
                },
            )
        private val actionJobs = ConcurrentHashMap<String, Job>()
        private val eventChannel = Channel<Int>(Channel.BUFFERED)
        val events = eventChannel.receiveAsFlow()

        val screenState =
            combine(
                manageDownloads
                    .observe()
                    .map<DownloadLibraryUiModel, DownloadLibraryResult> { DownloadLibraryResult.Data(it) }
                    .catch {
                        emit(DownloadLibraryResult.Failure)
                    },
                selectedTab,
            ) { result, tab ->
                when (result) {
                    is DownloadLibraryResult.Data -> {
                        if (result.library.isEmpty) {
                            DownloadLibraryScreenState.Empty(tab)
                        } else {
                            DownloadLibraryScreenState.Success(tab, result.library)
                        }
                    }

                    DownloadLibraryResult.Failure -> {
                        DownloadLibraryScreenState.Error(tab, R.string.downloads_load_failed)
                    }
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = DownloadLibraryScreenState.Loading(selectedTab.value),
            )

        fun selectTab(tab: DownloadLibraryTab) {
            selectedTab.value = tab
        }

        fun pause(entry: DownloadEntryUiModel) = runAction(entry.id) { manageDownloads.pause(entry.songIds) }

        fun resume(entry: DownloadEntryUiModel) = runAction(entry.id) { manageDownloads.resume(entry.songIds) }

        fun remove(entry: DownloadEntryUiModel) = runAction(entry.id) { manageDownloads.remove(entry.songIds) }

        fun pause(section: DownloadSectionUiModel) =
            runAction("section:${section.mediaType}") { manageDownloads.pause(section.songIds) }

        fun resume(section: DownloadSectionUiModel) =
            runAction("section:${section.mediaType}") { manageDownloads.resume(section.songIds) }

        fun remove(section: DownloadSectionUiModel) =
            runAction("section:${section.mediaType}") { manageDownloads.remove(section.songIds) }

        private fun runAction(
            key: String,
            action: () -> Unit,
        ) {
            actionJobs.remove(key)?.cancel()
            val job =
                viewModelScope.launch(Dispatchers.IO) {
                    runCatching(action)
                        .onFailure { eventChannel.send(R.string.download_action_failed) }
                }
            actionJobs[key] = job
            job.invokeOnCompletion { actionJobs.remove(key, job) }
        }

        private sealed interface DownloadLibraryResult {
            data class Data(val library: DownloadLibraryUiModel) : DownloadLibraryResult

            data object Failure : DownloadLibraryResult
        }

        private companion object {
            const val PROGRESS_TAB_ARGUMENT = "progress"
        }
    }
