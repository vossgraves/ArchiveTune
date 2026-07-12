/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.downloads.DownloadEntryUiModel
import moe.rukamori.archivetune.downloads.DownloadMediaType
import moe.rukamori.archivetune.downloads.DownloadSectionUiModel
import moe.rukamori.archivetune.ui.component.EmptyPlaceholder
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.ui.utils.formatFileSize
import moe.rukamori.archivetune.viewmodels.DownloadLibraryScreenState
import moe.rukamori.archivetune.viewmodels.DownloadLibraryTab
import moe.rukamori.archivetune.viewmodels.DownloadLibraryViewModel

@Composable
fun DownloadLibraryScreen(
    navController: NavController,
    viewModel: DownloadLibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.screenState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { messageRes ->
            snackbarHostState.showSnackbar(message = navController.context.getString(messageRes))
        }
    }

    DownloadLibraryScreenContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = navController::navigateUp,
        onBackToMain = navController::backToMain,
        onTabSelected = viewModel::selectTab,
        onPauseEntry = viewModel::pause,
        onResumeEntry = viewModel::resume,
        onRemoveEntry = viewModel::remove,
        onPauseSection = viewModel::pause,
        onResumeSection = viewModel::resume,
        onRemoveSection = viewModel::remove,
    )
}

@Composable
private fun DownloadLibraryScreenContent(
    state: DownloadLibraryScreenState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onBackToMain: () -> Unit,
    onTabSelected: (DownloadLibraryTab) -> Unit,
    onPauseEntry: (DownloadEntryUiModel) -> Unit,
    onResumeEntry: (DownloadEntryUiModel) -> Unit,
    onRemoveEntry: (DownloadEntryUiModel) -> Unit,
    onPauseSection: (DownloadSectionUiModel) -> Unit,
    onResumeSection: (DownloadSectionUiModel) -> Unit,
    onRemoveSection: (DownloadSectionUiModel) -> Unit,
) {
    val selectedTab = state.selectedTabOrDefault()
    val tabs = remember { DownloadLibraryTab.entries }
    val pagerState = rememberPagerState(initialPage = tabs.indexOf(selectedTab), pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    LaunchedEffect(selectedTab) {
        val targetPage = tabs.indexOf(selectedTab)
        if (pagerState.currentPage != targetPage) pagerState.animateScrollToPage(targetPage)
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page -> onTabSelected(tabs[page]) }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.downloads)) },
                    navigationIcon = {
                        moe.rukamori.archivetune.ui.component.IconButton(
                            onClick = onBack,
                            onLongClick = onBackToMain,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.arrow_back),
                                contentDescription = null,
                            )
                        }
                    },
                )
                PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                    tabs.forEachIndexed { index, tab ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = {
                                Text(
                                    text =
                                        stringResource(
                                            if (tab == DownloadLibraryTab.DOWNLOADED) {
                                                R.string.filter_downloaded
                                            } else {
                                                R.string.download_in_progress
                                            },
                                        ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0.dp),
    ) { innerPadding ->
        when (state) {
            is DownloadLibraryScreenState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    ContainedLoadingIndicator()
                }
            }

            is DownloadLibraryScreenState.Error -> {
                EmptyPlaceholder(
                    icon = R.drawable.error,
                    text = stringResource(state.messageRes),
                    modifier = Modifier.padding(innerPadding),
                )
            }

            is DownloadLibraryScreenState.Empty -> {
                DownloadPager(
                    pagerState = pagerState,
                    downloadedSections = emptyList(),
                    progressSections = emptyList(),
                    contentPadding = innerPadding,
                    onPauseEntry = onPauseEntry,
                    onResumeEntry = onResumeEntry,
                    onRemoveEntry = onRemoveEntry,
                    onPauseSection = onPauseSection,
                    onResumeSection = onResumeSection,
                    onRemoveSection = onRemoveSection,
                )
            }

            is DownloadLibraryScreenState.Success -> {
                DownloadPager(
                    pagerState = pagerState,
                    downloadedSections = state.library.downloadedSections,
                    progressSections = state.library.progressSections,
                    contentPadding = innerPadding,
                    onPauseEntry = onPauseEntry,
                    onResumeEntry = onResumeEntry,
                    onRemoveEntry = onRemoveEntry,
                    onPauseSection = onPauseSection,
                    onResumeSection = onResumeSection,
                    onRemoveSection = onRemoveSection,
                )
            }
        }
    }
}

@Composable
private fun DownloadPager(
    pagerState: androidx.compose.foundation.pager.PagerState,
    downloadedSections: List<DownloadSectionUiModel>,
    progressSections: List<DownloadSectionUiModel>,
    contentPadding: PaddingValues,
    onPauseEntry: (DownloadEntryUiModel) -> Unit,
    onResumeEntry: (DownloadEntryUiModel) -> Unit,
    onRemoveEntry: (DownloadEntryUiModel) -> Unit,
    onPauseSection: (DownloadSectionUiModel) -> Unit,
    onResumeSection: (DownloadSectionUiModel) -> Unit,
    onRemoveSection: (DownloadSectionUiModel) -> Unit,
) {
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.Top,
    ) { page ->
        val inProgress = page == DownloadLibraryTab.PROGRESS.ordinal
        val sections = if (inProgress) progressSections else downloadedSections
        DownloadSections(
            sections = sections,
            inProgress = inProgress,
            contentPadding = contentPadding,
            onPauseEntry = onPauseEntry,
            onResumeEntry = onResumeEntry,
            onRemoveEntry = onRemoveEntry,
            onPauseSection = onPauseSection,
            onResumeSection = onResumeSection,
            onRemoveSection = onRemoveSection,
        )
    }
}

@Composable
private fun DownloadSections(
    sections: List<DownloadSectionUiModel>,
    inProgress: Boolean,
    contentPadding: PaddingValues,
    onPauseEntry: (DownloadEntryUiModel) -> Unit,
    onResumeEntry: (DownloadEntryUiModel) -> Unit,
    onRemoveEntry: (DownloadEntryUiModel) -> Unit,
    onPauseSection: (DownloadSectionUiModel) -> Unit,
    onResumeSection: (DownloadSectionUiModel) -> Unit,
    onRemoveSection: (DownloadSectionUiModel) -> Unit,
) {
    if (sections.isEmpty()) {
        EmptyPlaceholder(
            icon = if (inProgress) R.drawable.download else R.drawable.offline,
            text = stringResource(if (inProgress) R.string.no_downloads_in_progress else R.string.no_downloads),
            modifier = Modifier.padding(contentPadding),
        )
        return
    }

    val layoutDirection = LocalLayoutDirection.current
    val playerPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection) + 16.dp,
                top = contentPadding.calculateTopPadding() + 16.dp,
                end = contentPadding.calculateEndPadding(layoutDirection) + 16.dp,
                bottom = playerPadding.calculateBottomPadding() + 24.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        sections.forEach { section ->
            item(
                key = "header:${section.mediaType}",
                contentType = CONTENT_TYPE_SECTION_HEADER,
            ) {
                DownloadSectionHeader(
                    section = section,
                    inProgress = inProgress,
                    onPause = { onPauseSection(section) },
                    onResume = { onResumeSection(section) },
                    onRemove = { onRemoveSection(section) },
                    modifier = Modifier.animateItem(),
                )
            }
            itemsIndexed(
                items = section.entries,
                key = { _, entry -> entry.id },
                contentType = { _, _ -> CONTENT_TYPE_DOWNLOAD_ENTRY },
            ) { index, entry ->
                DownloadEntry(
                    entry = entry,
                    inProgress = inProgress,
                    first = index == 0,
                    last = index == section.entries.lastIndex,
                    onPause = { onPauseEntry(entry) },
                    onResume = { onResumeEntry(entry) },
                    onRemove = { onRemoveEntry(entry) },
                    modifier = Modifier.animateItem(),
                )
            }
            item(
                key = "spacer:${section.mediaType}",
                contentType = CONTENT_TYPE_SECTION_SPACER,
            ) {
                Spacer(Modifier.height(22.dp))
            }
        }
    }
}

@Composable
private fun DownloadSectionHeader(
    section: DownloadSectionUiModel,
    inProgress: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(section.mediaType.titleRes()),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text =
                        if (inProgress) {
                            stringResource(
                                R.string.download_progress_summary,
                                section.percent,
                                formatFileSize(section.speedBytesPerSecond),
                            )
                        } else {
                            pluralStringResource(
                                R.plurals.n_song,
                                section.songIds.size,
                                section.songIds.size,
                            )
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!inProgress) {
                IconButton(onClick = onRemove) {
                    Icon(
                        painter = painterResource(R.drawable.delete),
                        contentDescription = stringResource(R.string.remove_download),
                    )
                }
            }
        }
        if (inProgress) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(onClick = if (section.paused) onResume else onPause) {
                    Icon(
                        painter = painterResource(if (section.paused) R.drawable.play else R.drawable.pause),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(if (section.paused) R.string.resume_download else R.string.pause_download))
                }
                TextButton(onClick = onRemove) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        }
    }
}

@Composable
private fun DownloadEntry(
    entry: DownloadEntryUiModel,
    inProgress: Boolean,
    first: Boolean,
    last: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape =
        when {
            first && last -> MaterialTheme.shapes.large
            first -> MaterialTheme.shapes.large
            last -> MaterialTheme.shapes.large
            else -> MaterialTheme.shapes.small
        }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = entry.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    entry.supportingText?.let { supportingText ->
                        Text(
                            text = supportingText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (inProgress) {
                        if (entry.failed) {
                            Text(
                                text = stringResource(R.string.download_failed_state),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = stringResource(R.string.download_progress_percent, entry.percent),
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Text(
                                text = stringResource(R.string.download_speed, formatFileSize(entry.speedBytesPerSecond)),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        LinearWavyProgressIndicator(
                            progress = { entry.progress },
                            modifier = Modifier.fillMaxWidth(),
                            amplitude = { if (entry.paused) 0f else 1f },
                        )
                    } else if (entry.totalCount > 1) {
                        Text(
                            text = pluralStringResource(R.plurals.n_song, entry.totalCount, entry.totalCount),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            leadingContent = {
                AsyncImage(
                    model = entry.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(56.dp),
                )
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (inProgress) {
                        FilledTonalIconButton(onClick = if (entry.paused) onResume else onPause) {
                            Icon(
                                painter = painterResource(if (entry.paused) R.drawable.play else R.drawable.pause),
                                contentDescription =
                                    stringResource(
                                        if (entry.paused) R.string.resume_download else R.string.pause_download,
                                    ),
                            )
                        }
                    }
                    IconButton(onClick = onRemove) {
                        Icon(
                            painter = painterResource(if (inProgress) R.drawable.close else R.drawable.delete),
                            contentDescription =
                                stringResource(if (inProgress) android.R.string.cancel else R.string.remove_download),
                        )
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        )
    }
}

private fun DownloadMediaType.titleRes(): Int =
    when (this) {
        DownloadMediaType.PLAYLIST -> R.string.playlists
        DownloadMediaType.ALBUM -> R.string.albums
        DownloadMediaType.SONG -> R.string.songs
    }

private fun DownloadLibraryScreenState.selectedTabOrDefault(): DownloadLibraryTab =
    when (this) {
        is DownloadLibraryScreenState.Loading -> selectedTab
        is DownloadLibraryScreenState.Success -> selectedTab
        is DownloadLibraryScreenState.Empty -> selectedTab
        is DownloadLibraryScreenState.Error -> selectedTab
    }

private const val CONTENT_TYPE_SECTION_HEADER = "download_section_header"
private const val CONTENT_TYPE_DOWNLOAD_ENTRY = "download_entry"
private const val CONTENT_TYPE_SECTION_SPACER = "download_section_spacer"
