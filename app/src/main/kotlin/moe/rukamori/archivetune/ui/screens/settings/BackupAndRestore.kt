/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.backup.ScheduledBackupFrequency
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.ui.component.DefaultDialog
import moe.rukamori.archivetune.ui.component.EnumListPreference
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.menu.AddToPlaylistDialogOnline
import moe.rukamori.archivetune.ui.menu.LoadingScreen
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.BackupCategory
import moe.rukamori.archivetune.viewmodels.BackupRestoreViewModel
import moe.rukamori.archivetune.viewmodels.GoogleDriveSyncScreenState
import moe.rukamori.archivetune.viewmodels.GoogleDriveSyncUiData
import moe.rukamori.archivetune.viewmodels.ScheduledBackupScreenState
import moe.rukamori.archivetune.viewmodels.ScheduledBackupUiData
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.layout.asPaddingValues

private val CSV_MIME_TYPES =
    arrayOf(
        "text/csv",
        "text/x-csv",
        "text/comma-separated-values",
        "text/x-comma-separated-values",
        "application/csv",
        "application/x-csv",
        "application/vnd.ms-excel",
        "text/plain",
        "text/*",
        "application/octet-stream",
    )

@Composable
fun BackupAndRestore(
    navController: NavController,
    viewModel: BackupRestoreViewModel = hiltViewModel(),
    scrollTo: String? = null,
) {
    val importedSongs = remember { mutableStateListOf<Song>() }
    var showChoosePlaylistDialogOnline by rememberSaveable { mutableStateOf(false) }
    var isProgressStarted by rememberSaveable { mutableStateOf(false) }
    var progressStatus by remember { mutableStateOf("") }
    var progressPercentage by rememberSaveable { mutableIntStateOf(0) }
    var showBackupOptionsDialog by rememberSaveable { mutableStateOf(false) }
    var showRestoreOptionsDialog by rememberSaveable { mutableStateOf(false) }
    var showRestoreValidationError by rememberSaveable { mutableStateOf(false) }
    var restoreValidationErrorMessage by remember { mutableStateOf("") }
    var pendingBackupCategories by remember { mutableStateOf(BackupCategory.entries.toSet()) }
    var pendingRestoreCategories by remember { mutableStateOf(BackupCategory.entries.toSet()) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    // Drive-folder picker UX state:
    //   - showGDriveFolderPickerHelp: shown BEFORE launching the SAF picker, to instruct the
    //     user to switch to the "Drive" provider in the picker's sidebar (if they want Drive).
    //     Mentions that Drive requires the Drive app installed, and that other cloud providers
    //     or local storage are also accepted.
    //   - showGDriveLocalFolderConfirm: shown AFTER the picker returns a local-storage URI.
    //     Asks the user to confirm they really want a local folder (since backups won't reach
    //     the cloud). "Use this folder" persists; "Pick another folder" re-opens the picker.
    var showGDriveFolderPickerHelp by rememberSaveable { mutableStateOf(false) }
    var showGDriveLocalFolderConfirm by rememberSaveable { mutableStateOf(false) }

    val backupRestoreProgress by viewModel.backupRestoreProgress.collectAsStateWithLifecycle()
    val scheduledBackupState by viewModel.scheduledBackupState.collectAsStateWithLifecycle()
    val googleDriveSyncState by viewModel.googleDriveSyncState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.backupEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.scheduledBackupEvent.collect { messageRes ->
            snackbarHostState.showSnackbar(context.getString(messageRes))
        }
    }

    LaunchedEffect(Unit) {
        viewModel.googleDriveSyncEvent.collect { messageRes ->
            snackbarHostState.showSnackbar(context.getString(messageRes))
        }
    }

    val backupLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            if (uri != null) {
                viewModel.backup(context, uri, pendingBackupCategories)
            }
        }
    val backupDirectoryLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let(viewModel::onScheduledBackupDirectorySelected)
        }
    // SAF folder picker for the cloud/local backup folder. The system OpenDocumentTree picker
    // shows the user's full document-provider tree — Google Drive (if installed), Dropbox,
    // Nextcloud, OneDrive, and any other registered cloud provider, plus local storage. The
    // user picks the exact folder backups should land in. We persist read+write URI permission
    // so the choice survives app restarts and reboots, then hand the tree URI + its display
    // name to the ViewModel.
    //
    // We accept ANY folder, not just Drive. The picker can't show Drive folders unless the
    // Google Drive app is installed (it registers the Drive DocumentsProvider) — rejecting
    // non-Drive URIs (as the previous PR #74 did) made the feature completely unusable for
    // users who'd uninstalled Drive, which was the bug report that motivated this revision.
    //
    // To prevent the original "user picked local storage by mistake" bug, we detect
    // local-storage authorities and show a confirmation dialog before persisting the pick —
    // the user has to explicitly opt in to a local folder. Cloud-provider URIs are accepted
    // immediately.
    //
    // We hold the pending URI in [pendingGDriveFolderUri] while the local-folder confirmation
    // is on screen, so we can persist it if the user confirms, or discard it if they cancel.
    var pendingGDriveFolderUri by remember { mutableStateOf<Uri?>(null) }
    val gdriveFolderPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
            if (treeUri == null) return@rememberLauncherForActivityResult
            // If the user picked a local-storage folder, confirm before persisting — this is
            // the "user picked Music by mistake" footgun the previous fix tried to prevent,
            // solved here with a soft warning instead of a hard reject.
            if (isLocalStorageTreeUri(treeUri)) {
                pendingGDriveFolderUri = treeUri
                showGDriveLocalFolderConfirm = true
                return@rememberLauncherForActivityResult
            }
            persistPickedGDriveFolder(context, treeUri, viewModel)
        }
    val restoreLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                coroutineScope.launch {
                    val result = viewModel.validateBackup(context, uri)
                    if (result.isValid) {
                        pendingRestoreCategories = result.availableCategories
                        pendingRestoreUri = uri
                        showRestoreOptionsDialog = true
                    } else {
                        restoreValidationErrorMessage = result.errorMessage ?: context.getString(R.string.restore_corrupted)
                        showRestoreValidationError = true
                    }
                }
            }
        }
    val importPlaylistFromCsv =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            coroutineScope.launch {
                val result = viewModel.importPlaylistFromCsv(context, uri)
                importedSongs.clear()
                importedSongs.addAll(result)
                if (importedSongs.isNotEmpty()) {
                    showChoosePlaylistDialogOnline = true
                }
            }
        }
    val importM3uLauncherOnline =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            coroutineScope.launch {
                val result = viewModel.loadM3UOnline(context, uri)
                importedSongs.clear()
                importedSongs.addAll(result)
                if (importedSongs.isNotEmpty()) {
                    showChoosePlaylistDialogOnline = true
                }
            }
        }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup_restore)) },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(
                            painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                },
            )
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier =
                    Modifier
                        .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom))
                        .padding(16.dp),
            )
        },
    ) { innerPadding ->
        val playerAwareBottomPadding =
            LocalPlayerAwareWindowInsets.current
                .only(WindowInsetsSides.Bottom)
                .asPaddingValues()
                .calculateBottomPadding()
        val topPadding = innerPadding.calculateTopPadding()
        val scrollState = rememberScrollState()
        val positions = rememberPreferencePositions()

        LaunchedEffect(scrollTo) { positions.scrollToKey(scrollTo, scrollState) }

        Column(
            Modifier
                .padding(top = topPadding)
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal))
                .verticalScroll(scrollState)
                .padding(bottom = playerAwareBottomPadding + SettingsDimensions.ScreenBottomPadding),
        ) {
            val scheduledBackupData =
                when (val state = scheduledBackupState) {
                    is ScheduledBackupScreenState.Success -> {
                        state.data
                    }

                    ScheduledBackupScreenState.Loading,
                    ScheduledBackupScreenState.Empty,
                    is ScheduledBackupScreenState.Error,
                    -> {
                        ScheduledBackupUiData(
                            enabled = false,
                            frequency = ScheduledBackupFrequency.WEEKLY,
                            customDateEpochDay = null,
                            customDateLabel = null,
                            directoryName = null,
                            overwriteExisting = false,
                            showCustomDatePicker = false,
                        )
                    }
                }

            ScheduledBackupSection(
                data = scheduledBackupData,
                enabled = scheduledBackupState !is ScheduledBackupScreenState.Loading,
                onEnabledChanged = viewModel::onScheduledBackupEnabledChanged,
                onFrequencySelected = viewModel::onScheduledBackupFrequencySelected,
                onDirectoryClick = { backupDirectoryLauncher.launch(null) },
                onOverwriteChanged = viewModel::onScheduledBackupOverwriteChanged,
                positions = positions,
            )

            val googleDriveData =
                when (val state = googleDriveSyncState) {
                    is GoogleDriveSyncScreenState.Success -> state.data
                    GoogleDriveSyncScreenState.Loading,
                    GoogleDriveSyncScreenState.Empty,
                    is GoogleDriveSyncScreenState.Error,
                    -> {
                        GoogleDriveSyncUiData(
                            enabled = false,
                            frequency = ScheduledBackupFrequency.WEEKLY,
                            customDateEpochDay = null,
                            customDateLabel = null,
                            remoteFolderName = null,
                            remoteFolderUri = null,
                            overwriteExisting = false,
                            showCustomDatePicker = false,
                            lastSyncLabel = null,
                            lastSyncFailed = false,
                            isSyncing = false,
                        )
                    }
                }

            GoogleDriveSyncSection(
                data = googleDriveData,
                enabled = googleDriveSyncState !is GoogleDriveSyncScreenState.Loading,
                onEnabledChanged = viewModel::onGoogleDriveSyncEnabledChanged,
                onFrequencySelected = viewModel::onGoogleDriveSyncFrequencySelected,
                onCustomDateSelected = viewModel::onGoogleDriveSyncCustomDateSelected,
                onCustomDateDismissed = viewModel::onGoogleDriveSyncCustomDateDismissed,
                onRemoteFolderClick = {
                    // Show a help dialog first so the user knows to switch to the "Drive"
                    // provider in the picker's sidebar (if they want Drive — Drive requires
                    // the Drive app to be installed). Other cloud providers or local storage
                    // are also accepted.
                    showGDriveFolderPickerHelp = true
                },
                onClearFolderClick = viewModel::onGoogleDriveSyncRemoteFolderCleared,
                onOverwriteChanged = viewModel::onGoogleDriveSyncOverwriteChanged,
                onSyncNowClick = viewModel::onGoogleDriveSyncRunNow,
                positions = positions,
            )

            if (googleDriveData.showCustomDatePicker) {
                GoogleDriveSyncDatePickerDialog(
                    selectedEpochDay = googleDriveData.customDateEpochDay,
                    onDateSelected = viewModel::onGoogleDriveSyncCustomDateSelected,
                    onDismiss = viewModel::onGoogleDriveSyncCustomDateDismissed,
                )
            }

            PreferenceGroup(
                modifier = positions.modifierFor("backup"),
                title = stringResource(R.string.internal_service),
            ) {
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.action_backup)) },
                        description = stringResource(R.string.backup_create_backup_desc),
                        icon = { Icon(painterResource(R.drawable.backup), null) },
                        onClick = { showBackupOptionsDialog = true },
                    )
                }

                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.action_restore)) },
                        description = stringResource(R.string.restore_select_backup),
                        icon = { Icon(painterResource(R.drawable.restore), null) },
                        onClick = { restoreLauncher.launch(arrayOf("application/octet-stream", "application/zip")) },
                    )
                }

                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.import_online)) },
                        description = stringResource(R.string.import_m3u_format),
                        icon = { Icon(painterResource(R.drawable.playlist_import), null) },
                        onClick = { importM3uLauncherOnline.launch(arrayOf("audio/*")) },
                    )
                }

                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.import_csv)) },
                        description = stringResource(R.string.import_csv_format),
                        icon = { Icon(painterResource(R.drawable.playlist_add), null) },
                        onClick = { importPlaylistFromCsv.launch(CSV_MIME_TYPES) },
                    )
                }
            }

        }
    }

    val scheduledBackupData = (scheduledBackupState as? ScheduledBackupScreenState.Success)?.data
    if (scheduledBackupData?.showCustomDatePicker == true) {
        ScheduledBackupDatePickerDialog(
            selectedEpochDay = scheduledBackupData.customDateEpochDay,
            onDateSelected = viewModel::onScheduledBackupCustomDateSelected,
            onDismiss = viewModel::onScheduledBackupCustomDateDismissed,
        )
    }

    if (showBackupOptionsDialog) {
        BackupOptionsDialog(
            title = stringResource(R.string.backup_options_title),
            confirmLabel = stringResource(R.string.action_backup),
            onConfirm = { categories ->
                pendingBackupCategories = categories
                showBackupOptionsDialog = false
                val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                backupLauncher.launch(
                    "${context.getString(R.string.app_name)}_${LocalDateTime.now().format(formatter)}.backup",
                )
            },
            onDismiss = { showBackupOptionsDialog = false },
        )
    }

    if (showRestoreOptionsDialog) {
        val uri = pendingRestoreUri
        if (uri != null) {
            BackupOptionsDialog(
                title = stringResource(R.string.restore_options_title),
                confirmLabel = stringResource(R.string.action_restore),
                onConfirm = { categories ->
                    pendingRestoreCategories = categories
                    showRestoreOptionsDialog = false
                    pendingRestoreUri = null
                    viewModel.restore(context, uri, categories)
                },
                onDismiss = {
                    showRestoreOptionsDialog = false
                    pendingRestoreUri = null
                },
            )
        }
    }

    if (showRestoreValidationError) {
        DefaultDialog(
            onDismiss = { showRestoreValidationError = false },
            title = { Text(stringResource(R.string.restore_failed)) },
            buttons = {
                TextButton(
                    onClick = { showRestoreValidationError = false },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        ) {
            Text(
                text = restoreValidationErrorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    // Pre-picker help dialog. Explains to the user that they MUST switch to the "Drive"
    // provider in the system picker's sidebar — otherwise they'll pick a local-storage
    // folder and the backup won't actually go to Google Drive. Tapping "Open picker"
    // launches the SAF OpenDocumentTree intent.
    if (showGDriveFolderPickerHelp) {
        AlertDialog(
            onDismissRequest = { showGDriveFolderPickerHelp = false },
            title = { Text(stringResource(R.string.google_drive_sync_pick_folder_help_title)) },
            text = {
                Text(
                    text = stringResource(R.string.google_drive_sync_pick_folder_help_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showGDriveFolderPickerHelp = false
                        gdriveFolderPickerLauncher.launch(null)
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.google_drive_sync_pick_folder_help_open))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showGDriveFolderPickerHelp = false },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    // Post-picker local-folder confirmation dialog. Shown only when the user picked a folder
    // whose authority is the local-storage DocumentsProvider (`com.android.externalstorage.documents`).
    // Cloud-provider URIs (Drive, Dropbox, Nextcloud, OneDrive, …) are accepted immediately
    // without a dialog. "Use this folder" persists the URI; "Pick another folder" re-opens the
    // picker so the user can navigate to a cloud provider.
    //
    // We hold the pending URI in [pendingGDriveFolderUri] (declared alongside the launcher)
    // so the confirm handler can persist it on user opt-in.
    if (showGDriveLocalFolderConfirm) {
        AlertDialog(
            onDismissRequest = {
                showGDriveLocalFolderConfirm = false
                pendingGDriveFolderUri = null
            },
            title = { Text(stringResource(R.string.google_drive_sync_local_folder_title)) },
            text = {
                Text(
                    text = stringResource(R.string.google_drive_sync_local_folder_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val uri = pendingGDriveFolderUri
                        showGDriveLocalFolderConfirm = false
                        pendingGDriveFolderUri = null
                        if (uri != null) persistPickedGDriveFolder(context, uri, viewModel)
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.google_drive_sync_local_folder_use_anyway))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showGDriveLocalFolderConfirm = false
                        pendingGDriveFolderUri = null
                        gdriveFolderPickerLauncher.launch(null)
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.google_drive_sync_local_folder_try_again))
                }
            },
        )
    }

    AddToPlaylistDialogOnline(
        isVisible = showChoosePlaylistDialogOnline,
        allowSyncing = false,
        songs = importedSongs,
        onDismiss = { showChoosePlaylistDialogOnline = false },
        onProgressStart = { isProgressStarted = it },
        onPercentageChange = { progressPercentage = it },
        onStatusChange = { progressStatus = it },
    )

    LaunchedEffect(progressPercentage, isProgressStarted) {
        if (isProgressStarted && progressPercentage == 99) {
            delay(10_000)
            if (progressPercentage == 99) {
                isProgressStarted = false
                progressPercentage = 0
            }
        }
    }

    LoadingScreen(
        isVisible = backupRestoreProgress != null || isProgressStarted,
        value = backupRestoreProgress?.percent ?: progressPercentage,
        title = backupRestoreProgress?.title,
        stepText = backupRestoreProgress?.step ?: progressStatus,
        indeterminate = backupRestoreProgress?.indeterminate ?: false,
    )
}

@Composable
private fun ScheduledBackupSection(
    data: ScheduledBackupUiData,
    enabled: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
    onFrequencySelected: (ScheduledBackupFrequency) -> Unit,
    onDirectoryClick: () -> Unit,
    onOverwriteChanged: (Boolean) -> Unit,
    positions: PreferencePositions,
) {
    PreferenceGroup(
        modifier = positions.modifierFor("scheduled_backup"),
        title = stringResource(R.string.scheduled_backup),
    ) {
        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.scheduled_backup_enabled)) },
                description =
                    stringResource(
                        if (data.enabled) {
                            R.string.scheduled_backup_enabled_description
                        } else {
                            R.string.scheduled_backup_disabled_description
                        },
                    ),
                icon = { Icon(painterResource(R.drawable.repeat_on), contentDescription = null) },
                checked = data.enabled,
                onCheckedChange = onEnabledChanged,
                isEnabled = enabled,
            )
        }

        item {
            EnumListPreference(
                title = { Text(stringResource(R.string.scheduled_backup_frequency)) },
                description =
                    if (data.frequency == ScheduledBackupFrequency.CUSTOM && data.customDateLabel != null) {
                        stringResource(R.string.scheduled_backup_custom_date, data.customDateLabel)
                    } else {
                        stringResource(R.string.scheduled_backup_frequency_description)
                    },
                icon = { Icon(painterResource(R.drawable.calendar_today), contentDescription = null) },
                selectedValue = data.frequency,
                valueText = { frequency -> stringResource(frequency.labelRes) },
                onValueSelected = onFrequencySelected,
                isEnabled = enabled,
            )
        }

        item {
            PreferenceEntry(
                title = { Text(stringResource(R.string.scheduled_backup_directory)) },
                description =
                    data.directoryName
                        ?: stringResource(R.string.scheduled_backup_directory_description),
                icon = { Icon(painterResource(R.drawable.snippet_folder), contentDescription = null) },
                onClick = onDirectoryClick,
                isEnabled = enabled,
            )
        }

        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.scheduled_backup_overwrite)) },
                description = stringResource(R.string.scheduled_backup_overwrite_description),
                icon = { Icon(painterResource(R.drawable.backup), contentDescription = null) },
                checked = data.overwriteExisting,
                onCheckedChange = onOverwriteChanged,
                isEnabled = enabled && data.directoryName != null,
            )
        }
    }
}

@Composable
private fun ScheduledBackupDatePickerDialog(
    selectedEpochDay: Long?,
    onDateSelected: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val todayEpochDay = remember { LocalDate.now().toEpochDay() }
    val initialEpochDay = selectedEpochDay?.coerceAtLeast(todayEpochDay) ?: todayEpochDay + 1
    val datePickerState =
        rememberDatePickerState(
            initialSelectedDateMillis =
                LocalDate
                    .ofEpochDay(initialEpochDay)
                    .atStartOfDay(ZoneOffset.UTC)
                    .toInstant()
                    .toEpochMilli(),
            selectableDates =
                remember(todayEpochDay) {
                    object : androidx.compose.material3.SelectableDates {
                        override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                            Instant
                                .ofEpochMilli(utcTimeMillis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                                .toEpochDay() >= todayEpochDay
                    }
                },
        )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val selectedMillis = datePickerState.selectedDateMillis ?: return@TextButton
                    val epochDay =
                        Instant
                            .ofEpochMilli(selectedMillis)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                            .toEpochDay()
                    onDateSelected(epochDay)
                },
                enabled = datePickerState.selectedDateMillis != null,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    ) {
        DatePicker(
            state = datePickerState,
            modifier = Modifier.verticalScroll(rememberScrollState()),
            title = { Text(stringResource(R.string.scheduled_backup_custom_title)) },
            showModeToggle = false,
        )
    }
}

private val ScheduledBackupFrequency.labelRes: Int
    get() =
        when (this) {
            ScheduledBackupFrequency.DAILY -> R.string.scheduled_backup_daily
            ScheduledBackupFrequency.WEEKLY -> R.string.scheduled_backup_weekly
            ScheduledBackupFrequency.MONTHLY -> R.string.scheduled_backup_monthly
            ScheduledBackupFrequency.CUSTOM -> R.string.scheduled_backup_custom
        }

/**
 * Google Drive sync section — mirrors [ScheduledBackupSection] in structure.
 *
 * Layout:
 *   - "Drive folder" entry — opens the SAF folder picker (OpenDocumentTree). Shows the picked
 *     folder name plus the detected provider (e.g. "Music · Google Drive", or
 *     "Music · Local storage (local — not cloud)" for local-storage picks). Any folder is
 *     accepted — Drive, Dropbox, Nextcloud, OneDrive, or local storage. Local-storage picks
 *     trigger a confirmation dialog before being persisted so the user is aware backups won't
 *     reach the cloud.
 *   - "Clear folder" entry — appears only once a folder is picked. Releases the persisted URI
 *     permission (via the ViewModel) and disables auto-sync.
 *   - "Enable Google Drive sync" switch (disabled until a folder is picked).
 *   - "Backup schedule" enum list — DAILY / WEEKLY / MONTHLY / CUSTOM (date picker).
 *   - "Overwrite existing Drive backup" switch.
 *   - "Sync now" entry — triggers an immediate one-shot upload to the picked folder.
 *   - "Last synced: …" footer (or "Last sync failed — will retry automatically" on failure).
 *
 * The frequency selector, overwrite switch, enable toggle, and sync-now entry are all gated on
 * `folderConfigured` (= any folder has been picked) — we don't restrict to Drive because the
 * SAF picker can't show Drive folders unless the Drive app is installed, and rejecting non-Drive
 * picks made the feature unusable for users who'd uninstalled Drive.
 */
@Composable
private fun GoogleDriveSyncSection(
    data: GoogleDriveSyncUiData,
    enabled: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
    onFrequencySelected: (ScheduledBackupFrequency) -> Unit,
    onCustomDateSelected: (Long) -> Unit,
    onCustomDateDismissed: () -> Unit,
    onRemoteFolderClick: () -> Unit,
    onClearFolderClick: () -> Unit,
    onOverwriteChanged: (Boolean) -> Unit,
    onSyncNowClick: () -> Unit,
    positions: PreferencePositions,
) {
    // Any picked folder is valid — the previous fix (PR #74) gated sync actions on the URI
    // being a Google Drive URI, which broke the feature entirely for users who'd uninstalled
    // the Drive app (the picker can't show Drive folders without the Drive app installed).
    //
    // We now accept any folder: Drive, Dropbox, Nextcloud, OneDrive, or local storage. The
    // picker callback shows a confirmation dialog for local-storage picks so the user is
    // aware backups won't reach the cloud. Cloud-provider picks are accepted immediately.
    //
    // For display in the folder row, we detect the provider from the URI authority and
    // show the friendly name next to the folder name (e.g. "Music · Google Drive"). For
    // local folders we append a "(local — not cloud)" suffix so the user always knows.
    val folderConfigured = data.remoteFolderName != null
    val providerLabel = data.remoteFolderUri?.let { providerLabelForUri(it) }
    PreferenceGroup(
        modifier = positions.modifierFor("google_drive_sync"),
        title = stringResource(R.string.google_drive_sync),
    ) {
        // Cloud-storage-app disclaimer. The SAF picker can only show cloud folders (Google
        // Drive, Dropbox, Nextcloud, OneDrive) when the corresponding app is installed on
        // the device. Without any cloud app, the picker falls back to local storage only
        // and backups stay on-device — which the user might not realise until they try to
        // restore after a reinstall. Surface this up front rather than burying it in the
        // folder-pick help dialog.
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.info),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.google_drive_sync_cloud_app_disclaimer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
        item {
            PreferenceEntry(
                title = { Text(stringResource(R.string.google_drive_sync_remote_folder)) },
                description =
                    when {
                        data.remoteFolderName != null && providerLabel != null ->
                            "${data.remoteFolderName} · $providerLabel"
                        data.remoteFolderName != null -> data.remoteFolderName
                        else -> stringResource(R.string.google_drive_sync_remote_folder_not_set)
                    },
                icon = { Icon(painterResource(R.drawable.snippet_folder), contentDescription = null) },
                onClick = onRemoteFolderClick,
                isEnabled = enabled,
            )
        }

        if (folderConfigured) {
            item {
                PreferenceEntry(
                    title = { Text(stringResource(R.string.google_drive_sync_clear_folder)) },
                    description = stringResource(R.string.google_drive_sync_clear_folder_description),
                    icon = { Icon(painterResource(R.drawable.close), contentDescription = null) },
                    onClick = onClearFolderClick,
                    isEnabled = enabled,
                )
            }
        }

        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.google_drive_sync_enabled)) },
                description =
                    stringResource(
                        if (data.enabled) {
                            R.string.google_drive_sync_enabled_description_on
                        } else {
                            R.string.google_drive_sync_enabled_description_off
                        },
                    ),
                icon = { Icon(painterResource(R.drawable.sync), contentDescription = null) },
                checked = data.enabled,
                onCheckedChange = onEnabledChanged,
                isEnabled = enabled && folderConfigured,
            )
        }

        item {
            EnumListPreference(
                title = { Text(stringResource(R.string.scheduled_backup_frequency)) },
                description =
                    if (data.frequency == ScheduledBackupFrequency.CUSTOM && data.customDateLabel != null) {
                        stringResource(R.string.scheduled_backup_custom_date, data.customDateLabel)
                    } else {
                        stringResource(R.string.google_drive_sync_remote_folder_description)
                    },
                icon = { Icon(painterResource(R.drawable.calendar_today), contentDescription = null) },
                selectedValue = data.frequency,
                valueText = { frequency -> stringResource(frequency.labelRes) },
                onValueSelected = onFrequencySelected,
                isEnabled = enabled && folderConfigured,
            )
        }

        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.google_drive_sync_overwrite)) },
                description = stringResource(R.string.google_drive_sync_overwrite_description),
                icon = { Icon(painterResource(R.drawable.backup), contentDescription = null) },
                checked = data.overwriteExisting,
                onCheckedChange = onOverwriteChanged,
                isEnabled = enabled && folderConfigured,
            )
        }

        item {
            PreferenceEntry(
                title = { Text(stringResource(R.string.google_drive_sync_run_now)) },
                description =
                    when {
                        data.isSyncing -> stringResource(R.string.google_drive_sync_running)
                        data.lastSyncFailed -> stringResource(R.string.google_drive_sync_last_sync_failed)
                        data.lastSyncLabel != null ->
                            stringResource(R.string.google_drive_sync_last_synced, data.lastSyncLabel)
                        else -> stringResource(R.string.google_drive_sync_never_synced)
                    },
                icon = { Icon(painterResource(R.drawable.sync), contentDescription = null) },
                onClick = onSyncNowClick,
                isEnabled = enabled && folderConfigured && !data.isSyncing,
            )
        }
    }
}

@Composable
private fun GoogleDriveSyncDatePickerDialog(
    selectedEpochDay: Long?,
    onDateSelected: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val todayEpochDay = remember { LocalDate.now().toEpochDay() }
    val initialEpochDay = selectedEpochDay?.coerceAtLeast(todayEpochDay) ?: todayEpochDay + 1
    val datePickerState =
        rememberDatePickerState(
            initialSelectedDateMillis =
                LocalDate
                    .ofEpochDay(initialEpochDay)
                    .atStartOfDay(ZoneOffset.UTC)
                    .toInstant()
                    .toEpochMilli(),
            selectableDates =
                remember(todayEpochDay) {
                    object : androidx.compose.material3.SelectableDates {
                        override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                            Instant
                                .ofEpochMilli(utcTimeMillis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                                .toEpochDay() >= todayEpochDay
                    }
                },
        )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val selectedMillis = datePickerState.selectedDateMillis ?: return@TextButton
                    val epochDay =
                        Instant
                            .ofEpochMilli(selectedMillis)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                            .toEpochDay()
                    onDateSelected(epochDay)
                },
                enabled = datePickerState.selectedDateMillis != null,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    ) {
        DatePicker(
            state = datePickerState,
            modifier = Modifier.verticalScroll(rememberScrollState()),
            title = { Text(stringResource(R.string.scheduled_backup_custom_title)) },
            showModeToggle = false,
        )
    }
}

@Composable
private fun IconBubble(
    icon: Painter,
    containerColor: Color,
    contentColor: Color,
    size: androidx.compose.ui.unit.Dp,
) {
    Box(
        modifier =
            Modifier
                .size(size)
                .clip(MaterialTheme.shapes.large)
                .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(size * 0.48f),
        )
    }
}

@Composable
private fun BackupOptionsDialog(
    title: String,
    confirmLabel: String,
    onConfirm: (Set<BackupCategory>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(BackupCategory.entries.toSet()) }

    DefaultDialog(
        onDismiss = onDismiss,
        title = { Text(title) },
        buttons = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(android.R.string.cancel))
            }
            TextButton(
                onClick = { onConfirm(selected) },
                shapes = ButtonDefaults.shapes(),
                enabled = selected.isNotEmpty(),
            ) {
                Text(confirmLabel)
            }
        },
    ) {
        Spacer(Modifier.height(8.dp))
        BackupCategory.entries.forEach { category ->
            val isChecked = category in selected
            val labelRes =
                when (category) {
                    BackupCategory.LIBRARY -> R.string.backup_category_library
                    BackupCategory.ACCOUNT -> R.string.backup_category_account
                    BackupCategory.SETTINGS -> R.string.backup_category_settings
                }
            val descRes =
                when (category) {
                    BackupCategory.LIBRARY -> R.string.backup_category_library_desc
                    BackupCategory.ACCOUNT -> R.string.backup_category_account_desc
                    BackupCategory.SETTINGS -> R.string.backup_category_settings_desc
                }
            val iconRes =
                when (category) {
                    BackupCategory.LIBRARY -> R.drawable.library_music
                    BackupCategory.ACCOUNT -> R.drawable.account
                    BackupCategory.SETTINGS -> R.drawable.settings
                }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = Color.Transparent,
                onClick = {
                    selected = if (isChecked) selected - category else selected + category
                },
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 72.dp)
                            .padding(horizontal = 4.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IconBubble(
                        icon = painterResource(iconRes),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        size = 40.dp,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = stringResource(labelRes),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(descRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Checkbox(
                        checked = isChecked,
                        onCheckedChange = { checked ->
                            selected = if (checked) selected + category else selected - category
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

/**
 * Resolves the human-readable display name of a SAF tree URI's root folder by querying its
 * [DocumentsContract.Document.COLUMN_DISPLAY_NAME]. Used right after the user picks a folder
 * via `OpenDocumentTree` so the UI can show which folder was chosen.
 *
 * Returns the empty string if the name can't be resolved (the caller falls back to a default
 * label in that case). Runs a synchronous ContentResolver query — only call from a launcher
 * callback or a background thread, never from the main recomposition path.
 */
private fun resolveFolderDisplayName(context: android.content.Context, treeUri: Uri): String {
    return try {
        val folderDocId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
        val folderDocUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, folderDocId)
        context.contentResolver.query(
            folderDocUri,
            arrayOf(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
        } ?: ""
    } catch (e: Exception) {
        ""
    }
}

/**
 * Mapping from a SAF tree URI's authority to a friendly provider label. Used to display
 * "Music · Google Drive" (or Dropbox, Nextcloud, OneDrive, Local storage) in the folder row
 * so the user knows at a glance where backups will land.
 *
 * Keep this list in sync with [LOCAL_STORAGE_AUTHORITIES] — local-storage authorities get
 * the "Local storage" label AND a "(local — not cloud)" suffix in the UI to make it obvious
 * backups won't reach the cloud.
 */
private val PROVIDER_LABELS: Map<String, Int> = mapOf(
    "com.google.android.apps.docs.storage" to R.string.google_drive_sync_provider_drive,
    "com.google.android.apps.docs.storage.legacy" to R.string.google_drive_sync_provider_drive,
    "com.dropbox.android.providers.DocumentsProvider" to R.string.google_drive_sync_provider_dropbox,
    "org.nextcloud.documents" to R.string.google_drive_sync_provider_nextcloud,
    "org.nextcloud.android.files.documentsStorageAccess" to R.string.google_drive_sync_provider_nextcloud,
    "com.microsoft.skydrive.content.SkyDriveProvider" to R.string.google_drive_sync_provider_onedrive,
    "com.onedrive.android.content.OneDriveDocumentsProvider" to R.string.google_drive_sync_provider_onedrive,
)

/**
 * Authorities registered by Android's local-storage DocumentsProvider (the "Files" app on
 * most ROMs). When the user picks a folder whose URI has one of these authorities, backups
 * will be written to local storage, not the cloud — we show a confirmation dialog before
 * persisting such picks so the user is aware.
 */
private val LOCAL_STORAGE_AUTHORITIES = setOf(
    "com.android.externalstorage.documents",
)

/**
 * Returns true iff [uri] is a SAF tree URI pointing at local storage (not a cloud provider).
 * Used to gate the local-folder confirmation dialog after the picker returns.
 */
private fun isLocalStorageTreeUri(uri: Uri): Boolean {
    val authority = uri.authority ?: return false
    return authority in LOCAL_STORAGE_AUTHORITIES
}

/**
 * Returns a friendly provider label string for the given SAF tree URI string, or null if the
 * URI can't be parsed or the provider isn't recognized. Recognized providers: Google Drive,
 * Dropbox, Nextcloud, OneDrive, Local storage. Unrecognized cloud providers get a generic
 * "Cloud folder" label so the user at least knows it's not local.
 *
 * Compose-side lookup — called from a composable, so it uses [stringResource] to resolve the
 * label. Returns null if the URI is malformed (the caller falls back to showing just the
 * folder name).
 */
@Composable
private fun providerLabelForUri(uriString: String): String? {
    val authority = runCatching { Uri.parse(uriString).authority }.getOrNull() ?: return null
    val labelRes = PROVIDER_LABELS[authority]
    return when {
        labelRes != null -> stringResource(labelRes)
        authority in LOCAL_STORAGE_AUTHORITIES -> {
            // Explicitly call out that this is local, not cloud — so the user doesn't
            // see "Local storage" in the folder row and assume backups are reaching Drive.
            stringResource(R.string.google_drive_sync_provider_local) +
                " " + stringResource(R.string.google_drive_sync_provider_suffix_local)
        }
        // Unrecognized authority that isn't local storage — treat as an unknown cloud
        // provider. Better to say "Cloud folder" than to mislabel it as local.
        else -> stringResource(R.string.google_drive_sync_provider_unknown)
    }
}

/**
 * Persists the picked SAF folder tree URI (so the WorkManager worker can write to it later,
 * even after the app process is killed), derives a display name from the URI, and hands both
 * to the ViewModel.
 *
 * Extracted as a top-level helper so both the launcher callback and the local-folder
 * confirmation dialog's "Use this folder" button can call it without duplicating logic.
 */
private fun persistPickedGDriveFolder(
    context: android.content.Context,
    treeUri: Uri,
    viewModel: BackupRestoreViewModel,
) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
    }
    val folderName = resolveFolderDisplayName(context, treeUri)
    viewModel.onGoogleDriveSyncRemoteFolderSelected(
        uri = treeUri.toString(),
        name = folderName.ifBlank { context.getString(R.string.google_drive_sync_remote_folder_default_name) },
    )
}
