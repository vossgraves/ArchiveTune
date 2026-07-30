/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.settings

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.backup.ScheduledBackupFrequency
import moe.rukamori.archivetune.constants.ShowSpotifyPlaylistsKey
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.spotify.SpotifyAccountUiState
import moe.rukamori.archivetune.spotify.SpotifyAccountViewModel
import moe.rukamori.archivetune.spotify.SpotifyAuth
import moe.rukamori.archivetune.ui.component.DefaultDialog
import moe.rukamori.archivetune.ui.component.EnumListPreference
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.PreferenceGroupScope
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.menu.AddToPlaylistDialogOnline
import moe.rukamori.archivetune.ui.menu.LoadingScreen
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.utils.resetAuthWebViewSession
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

private val SpotifyAccountIconSize = 44.dp
private const val SpotifyLoginUserAgent =
    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

@Composable
fun BackupAndRestore(
    navController: NavController,
    viewModel: BackupRestoreViewModel = hiltViewModel(),
    spotifyAccountViewModel: SpotifyAccountViewModel = hiltViewModel(),
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
    var showSpotifyLogin by rememberSaveable { mutableStateOf(false) }
    var pendingBackupCategories by remember { mutableStateOf(BackupCategory.entries.toSet()) }
    var pendingRestoreCategories by remember { mutableStateOf(BackupCategory.entries.toSet()) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    // Drive-folder picker UX state:
    //   - showGDriveFolderPickerHelp: shown BEFORE launching the SAF picker, to instruct the user
    //     to switch to the "Drive" provider in the picker's sidebar. Without this hint, users
    //     frequently pick a local folder and are then confused that backups land on local storage.
    //   - showGDriveNotDriveFolderError: shown AFTER the picker returns a non-Drive URI. Rejects
    //     the pick so the previous (valid-or-null) folder stays configured.
    //   - showGDriveDriveNotInstalledError: shown when the user taps "Drive folder" but no Drive
    //     DocumentsProvider is registered (Google Drive app not installed on the device).
    var showGDriveFolderPickerHelp by rememberSaveable { mutableStateOf(false) }
    var showGDriveNotDriveFolderError by rememberSaveable { mutableStateOf(false) }
    var showGDriveDriveNotInstalledError by rememberSaveable { mutableStateOf(false) }

    val backupRestoreProgress by viewModel.backupRestoreProgress.collectAsStateWithLifecycle()
    val scheduledBackupState by viewModel.scheduledBackupState.collectAsStateWithLifecycle()
    val googleDriveSyncState by viewModel.googleDriveSyncState.collectAsStateWithLifecycle()
    val spotifyState by spotifyAccountViewModel.uiState.collectAsStateWithLifecycle()
    val (showSpotifyPlaylists, onShowSpotifyPlaylistsChange) = rememberPreference(ShowSpotifyPlaylistsKey, false)
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
    // SAF folder picker for Google Drive sync. The system OpenDocumentTree picker shows the
    // user's full document-provider tree — including Google Drive (if the Drive app is
    // installed), Nextcloud, and any other registered cloud provider — so the user can pick
    // the exact folder backups should land in. We persist read+write URI permission so the
    // choice survives app restarts and reboots, then hand the tree URI + its display name to
    // the ViewModel. This replaced an AccountManager OAuth + Drive REST API approach that
    // failed with `UnregisteredOnApiConsole` for any non-Cloud-Console-registered build.
    //
    // IMPORTANT: We reject any URI whose authority isn't the Google Drive DocumentsProvider.
    // Without this check, users frequently pick a local-storage folder (the picker's default
    // view) and then report that backups land on local storage instead of Drive — see the
    // attached screenshots in the user's bug report. Rejecting non-Drive URIs forces the user
    // back to the picker with a clear error so they can navigate to Drive in the sidebar.
    val gdriveFolderPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
            if (treeUri == null) return@rememberLauncherForActivityResult
            // Reject non-Drive folders so backups don't silently land on local storage.
            // The picker's permission grant is temporary and expires when the process dies,
            // so there's nothing to release here — we just don't persist it.
            if (!isGoogleDriveTreeUri(treeUri)) {
                showGDriveNotDriveFolderError = true
                return@rememberLauncherForActivityResult
            }
            // Persist access so the WorkManager worker can write to this folder later, even
            // after the app process is killed.
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            // Derive a display name from the picked tree URI so the UI can show which folder
            // was chosen. We query the folder's document URI for its COLUMN_DISPLAY_NAME via
            // the framework DocumentsContract (no extra dependency needed).
            val folderName = resolveFolderDisplayName(context, treeUri)
            viewModel.onGoogleDriveSyncRemoteFolderSelected(
                uri = treeUri.toString(),
                name = folderName.ifBlank { context.getString(R.string.google_drive_sync_remote_folder_default_name) },
            )
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

    LaunchedEffect(spotifyState.isAuthenticated) {
        if (spotifyState.isAuthenticated) {
            showSpotifyLogin = false
        }
    }
    Scaffold(
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
        val topPadding = innerPadding.calculateTopPadding()
        val scrollState = rememberScrollState()
        val positions = rememberPreferencePositions()

        LaunchedEffect(scrollTo) { positions.scrollToKey(scrollTo, scrollState) }

        Column(
            Modifier
                .padding(top = topPadding)
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .verticalScroll(scrollState)
                .padding(bottom = SettingsDimensions.ScreenBottomPadding),
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
                    // Don't launch the SAF picker directly — first show a help dialog that tells
                    // the user to switch to the "Drive" provider in the picker's sidebar. Without
                    // this hint, users almost always pick a local folder and then complain that
                    // backups land on local storage.
                    if (isGoogleDriveInstalled(context)) {
                        showGDriveFolderPickerHelp = true
                    } else {
                        showGDriveDriveNotInstalledError = true
                    }
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

            PreferenceGroup(
                modifier = positions.modifierFor("restore"),
                title = stringResource(R.string.external_service),
            ) {
                spotifyAccountPreferences(
                    state = spotifyState,
                    showPlaylists = showSpotifyPlaylists,
                    onConnectClick = { showSpotifyLogin = true },
                    onShowPlaylistsChange = onShowSpotifyPlaylistsChange,
                    onReloadClick = spotifyAccountViewModel::reloadPlaylists,
                    onLogoutClick = {
                        spotifyAccountViewModel.logout()
                    },
                )
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

    // Post-picker rejection dialog. Shown when the user picked a folder whose authority isn't
    // Google Drive's DocumentsProvider — i.e. they picked a local-storage, Nextcloud, or
    // other-provider folder. "Try again" re-opens the picker so they can navigate to Drive.
    if (showGDriveNotDriveFolderError) {
        AlertDialog(
            onDismissRequest = { showGDriveNotDriveFolderError = false },
            title = { Text(stringResource(R.string.google_drive_sync_not_drive_folder_title)) },
            text = {
                Text(
                    text = stringResource(R.string.google_drive_sync_not_drive_folder_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showGDriveNotDriveFolderError = false
                        gdriveFolderPickerLauncher.launch(null)
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.google_drive_sync_not_drive_folder_try_again))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showGDriveNotDriveFolderError = false },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    // Drive-not-installed dialog. Shown when the user taps "Drive folder" but no Google Drive
    // DocumentsProvider is registered on the device (the Google Drive app isn't installed).
    // Without the Drive app, the SAF picker can't show Drive folders, so the feature is
    // unusable — surface a clear error instead of silently letting the user pick a local folder.
    if (showGDriveDriveNotInstalledError) {
        AlertDialog(
            onDismissRequest = { showGDriveDriveNotInstalledError = false },
            title = { Text(stringResource(R.string.google_drive_sync_drive_not_installed_title)) },
            text = {
                Text(
                    text = stringResource(R.string.google_drive_sync_drive_not_installed_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { showGDriveDriveNotInstalledError = false },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )
    }

    if (showSpotifyLogin) {
        SpotifyLoginSheet(
            onDismiss = { showSpotifyLogin = false },
            onCookiesCaptured = { spDc, spKey ->
                showSpotifyLogin = false
                spotifyAccountViewModel.connectWithCookies(spDc = spDc, spKey = spKey)
            },
        )
    }

    spotifyState.errorMessage?.let { error ->
        SpotifyErrorDialog(
            message = error,
            onDismiss = spotifyAccountViewModel::dismissError,
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
 *     folder name, "Not set — tap to pick a Drive folder" if no folder has been chosen yet,
 *     or "Not a Google Drive folder — tap to re-pick" if the existing pick's URI authority
 *     doesn't match the Google Drive DocumentsProvider (e.g. a pre-fix user picked a local
 *     folder by mistake). This is the primary setup action: picking a folder is what enables
 *     the rest of the section.
 *   - "Clear folder" entry — appears only once a folder is picked. Releases the persisted URI
 *     permission (via the ViewModel) and disables auto-sync.
 *   - "Enable Google Drive sync" switch (disabled until a valid Drive folder is picked).
 *   - "Backup schedule" enum list — DAILY / WEEKLY / MONTHLY / CUSTOM (date picker).
 *   - "Overwrite existing Drive backup" switch.
 *   - "Sync now" entry — triggers an immediate one-shot upload to the picked folder.
 *   - "Last synced: …" footer (or "Last sync failed — will retry automatically" on failure).
 *
 * The frequency selector, overwrite switch, enable toggle, and sync-now entry are all gated on
 * `folderEffective` (= folder picked AND URI is a Drive URI) — you can't sync to a folder you
 * haven't picked yet, and you can't sync to a non-Drive folder either (the upload would silently
 * land on local storage).
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
    // A folder is "configured" if a name was picked, but it's only "valid" if the underlying
    // URI is a Google Drive tree URI. Pre-fix users (and users who somehow bypassed the picker
    // validation) may have a non-Drive URI saved — we surface a warning in the folder row and
    // disable sync actions so they're forced to re-pick before anything uploads to the wrong
    // place.
    val folderConfigured = data.remoteFolderName != null
    val folderUriValid = data.remoteFolderUri?.let { isGoogleDriveTreeUri(Uri.parse(it)) } ?: false
    val folderEffective = folderConfigured && folderUriValid
    PreferenceGroup(
        modifier = positions.modifierFor("google_drive_sync"),
        title = stringResource(R.string.google_drive_sync),
    ) {
        item {
            PreferenceEntry(
                title = { Text(stringResource(R.string.google_drive_sync_remote_folder)) },
                description =
                    when {
                        // Existing pick but URI doesn't point at Drive — surface a warning so
                        // the user knows to re-pick (otherwise sync would upload to local
                        // storage and they'd be confused, exactly as in the bug report).
                        folderConfigured && !folderUriValid ->
                            stringResource(R.string.google_drive_sync_invalid_folder_warning)
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
                isEnabled = enabled && folderEffective,
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
                isEnabled = enabled && folderEffective,
            )
        }

        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.google_drive_sync_overwrite)) },
                description = stringResource(R.string.google_drive_sync_overwrite_description),
                icon = { Icon(painterResource(R.drawable.backup), contentDescription = null) },
                checked = data.overwriteExisting,
                onCheckedChange = onOverwriteChanged,
                isEnabled = enabled && folderEffective,
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
                isEnabled = enabled && folderEffective && !data.isSyncing,
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

private fun PreferenceGroupScope.spotifyAccountPreferences(
    state: SpotifyAccountUiState,
    showPlaylists: Boolean,
    onConnectClick: () -> Unit,
    onShowPlaylistsChange: (Boolean) -> Unit,
    onReloadClick: () -> Unit,
    onLogoutClick: () -> Unit,
) {
    if (!state.isAuthenticated) {
        item {
            PreferenceEntry(
                title = { Text(stringResource(R.string.spotify_connect)) },
                description = stringResource(R.string.spotify_not_connected),
                icon = { Icon(painterResource(R.drawable.spotify_icon), null) },
                trailingContent = {
                    AnimatedVisibility(visible = state.isLoading) {
                        CircularWavyProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                onClick = onConnectClick,
                isEnabled = !state.isLoading,
            )
        }
        return
    }

    item {
        PreferenceEntry(
            title = {
                Text(
                    text =
                        if (state.accountName.isNotBlank()) {
                            stringResource(R.string.spotify_connected_as, state.accountName)
                        } else {
                            stringResource(R.string.spotify_account)
                        },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            description =
                when {
                    state.isLoading -> stringResource(R.string.spotify_loading_library)
                    state.playlistCount > 0 -> stringResource(R.string.spotify_available_count, state.playlistCount)
                    else -> stringResource(R.string.spotify_no_sources)
                },
            icon = { SpotifyAccountIcon(avatarUrl = state.accountAvatarUrl) },
            trailingContent = {
                AnimatedVisibility(visible = state.isLoading) {
                    CircularWavyProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            isEnabled = false,
        )
    }

    item {
        SwitchPreference(
            title = { Text(stringResource(R.string.spotify_show_playlist)) },
            description = stringResource(R.string.spotify_show_playlist_desc),
            icon = { Icon(painterResource(R.drawable.spotify_icon), null) },
            checked = showPlaylists,
            onCheckedChange = onShowPlaylistsChange,
            isEnabled = !state.isLoading,
        )
    }

    item {
        PreferenceEntry(
            title = { Text(stringResource(R.string.spotify_reload_playlist)) },
            description = stringResource(R.string.spotify_reload_playlist_desc),
            icon = { Icon(painterResource(R.drawable.sync), null) },
            onClick = onReloadClick,
            isEnabled = !state.isLoading,
        )
    }

    item {
        PreferenceEntry(
            title = { Text(stringResource(R.string.action_logout)) },
            icon = { Icon(painterResource(R.drawable.logout), null) },
            onClick = onLogoutClick,
            isEnabled = !state.isLoading,
        )
    }
}

@Composable
private fun SpotifyAccountIcon(avatarUrl: String?) {
    val context = LocalContext.current
    val requestSize = with(LocalDensity.current) { SpotifyAccountIconSize.roundToPx() }
    val accountIcon = painterResource(R.drawable.spotify_icon)
    val imageRequest =
        remember(context, avatarUrl, requestSize) {
            avatarUrl
                ?.takeIf(String::isNotBlank)
                ?.let {
                    ImageRequest
                        .Builder(context)
                        .data(it)
                        .size(requestSize)
                        .build()
                }
        }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (imageRequest != null) {
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                placeholder = accountIcon,
                error = accountIcon,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                painter = accountIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SpotifyLoginSheet(
    onDismiss: () -> Unit,
    onCookiesCaptured: (spDc: String, spKey: String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var webView by remember { mutableStateOf<WebView?>(null) }
    var mainWebView by remember { mutableStateOf<WebView?>(null) }
    var captured by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            webView?.destroySpotifyLoginWebView()
            mainWebView?.takeIf { it !== webView }?.destroySpotifyLoginWebView()
            webView = null
            mainWebView = null
        }
    }

    BackHandler(enabled = webView != null) {
        val activeWebView = webView
        val rootWebView = mainWebView
        when {
            activeWebView?.canGoBack() == true -> {
                activeWebView.goBack()
            }

            activeWebView != null && rootWebView != null && activeWebView !== rootWebView -> {
                activeWebView.destroySpotifyLoginWebView()
                webView = rootWebView
            }

            else -> {
                onDismiss()
            }
        }
    }

    ModalBottomSheet(
        modifier = Modifier.fillMaxHeight(),
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.spotify_login_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.spotify_waiting_for_login),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AndroidView(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(MaterialTheme.shapes.large),
                factory = { context ->
                    val container = FrameLayout(context)
                    val spotifyWebView =
                        WebView(context).apply {
                            val cookieManager = CookieManager.getInstance()
                            cookieManager.setAcceptCookie(true)
                            cookieManager.setAcceptThirdPartyCookies(this, true)
                            configureSpotifyLoginWebView()

                            fun captureCookies(url: String?): Boolean {
                                if (captured) return true
                                val cookies = readSpotifyCookies(cookieManager, url)
                                val spDc = cookies["sp_dc"].orEmpty()
                                if (spDc.isBlank()) return false
                                captured = true
                                cookieManager.flush()
                                onCookiesCaptured(spDc, cookies["sp_key"].orEmpty())
                                return true
                            }

                            webViewClient =
                                object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView,
                                        request: WebResourceRequest,
                                    ): Boolean =
                                        shouldOverrideSpotifyLoginUrl(
                                            view = view,
                                            url = request.url?.toString(),
                                            captureCookies = { url -> captureCookies(url) },
                                        )

                                    @Deprecated("Deprecated in Java")
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView,
                                        url: String?,
                                    ): Boolean =
                                        shouldOverrideSpotifyLoginUrl(
                                            view = view,
                                            url = url,
                                            captureCookies = { targetUrl -> captureCookies(targetUrl) },
                                        )

                                    override fun onPageStarted(
                                        view: WebView,
                                        url: String?,
                                        favicon: android.graphics.Bitmap?,
                                    ) {
                                        captureCookies(url)
                                    }

                                    override fun onPageFinished(
                                        view: WebView,
                                        url: String?,
                                    ) {
                                        captureCookies(url)
                                    }
                                }
                            webChromeClient =
                                SpotifyLoginWebChromeClient(
                                    container = container,
                                    parentWebView = this,
                                    captureCookies = { url -> captureCookies(url) },
                                    onActiveWebViewChanged = { activeWebView -> webView = activeWebView },
                                )
                            webView = this
                            mainWebView = this
                            resetAuthWebViewSession(context, this) {
                                loadUrl(SpotifyAuth.LOGIN_URL)
                            }
                        }
                    container.addView(
                        spotifyWebView,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        ),
                    )
                    container
                },
                update = {
                    webView = webView ?: mainWebView
                },
            )
        }
    }
}

private fun WebView.destroySpotifyLoginWebView() {
    stopLoading()
    loadUrl("about:blank")
    (parent as? ViewGroup)?.removeView(this)
    destroy()
}

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.configureSpotifyLoginWebView() {
    settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        javaScriptCanOpenWindowsAutomatically = true
        setSupportMultipleWindows(true)
        setSupportZoom(true)
        builtInZoomControls = true
        displayZoomControls = false
        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        userAgentString = SpotifyLoginUserAgent
    }
}

private class SpotifyLoginWebChromeClient(
    private val container: FrameLayout,
    private val parentWebView: WebView,
    private val captureCookies: (String?) -> Boolean,
    private val onActiveWebViewChanged: (WebView) -> Unit,
) : WebChromeClient() {
    override fun onCreateWindow(
        view: WebView,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message,
    ): Boolean {
        closePopupWebViews()

        val popupWebView =
            WebView(view.context).apply {
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(this, true)
                configureSpotifyLoginWebView()
                webViewClient =
                    object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ): Boolean =
                            shouldOverrideSpotifyLoginUrl(
                                view = view,
                                url = request.url?.toString(),
                                captureCookies = captureCookies,
                            )

                        @Deprecated("Deprecated in Java")
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            url: String?,
                        ): Boolean =
                            shouldOverrideSpotifyLoginUrl(
                                view = view,
                                url = url,
                                captureCookies = captureCookies,
                            )

                        override fun onPageStarted(
                            view: WebView,
                            url: String?,
                            favicon: android.graphics.Bitmap?,
                        ) {
                            captureCookies(url)
                        }

                        override fun onPageFinished(
                            view: WebView,
                            url: String?,
                        ) {
                            captureCookies(url)
                        }
                    }
            }

        val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
        container.addView(
            popupWebView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        popupWebView.bringToFront()
        popupWebView.requestFocus()
        onActiveWebViewChanged(popupWebView)
        transport.webView = popupWebView
        resultMsg.sendToTarget()
        return true
    }

    override fun onCloseWindow(window: WebView) {
        window.destroySpotifyLoginWebView()
        onActiveWebViewChanged(parentWebView)
    }

    private fun closePopupWebViews() {
        for (index in container.childCount - 1 downTo 0) {
            val child = container.getChildAt(index) as? WebView ?: continue
            if (child !== parentWebView) {
                child.destroySpotifyLoginWebView()
            }
        }
        onActiveWebViewChanged(parentWebView)
    }
}

private fun shouldOverrideSpotifyLoginUrl(
    view: WebView,
    url: String?,
    captureCookies: (String?) -> Boolean,
): Boolean {
    if (captureCookies(url)) return true

    val targetUrl = url?.takeIf(String::isNotBlank) ?: return false
    if (targetUrl.isWebViewLoadableUrl()) return false

    targetUrl.intentBrowserFallbackUrl()?.let { fallbackUrl -> view.loadUrl(fallbackUrl) }
    return true
}

private fun String.isWebViewLoadableUrl(): Boolean {
    val scheme = runCatching { Uri.parse(this).scheme?.lowercase() }.getOrNull()
    return scheme == "http" ||
        scheme == "https" ||
        scheme == "javascript" ||
        scheme == "data" ||
        scheme == "blob"
}

private fun String.intentBrowserFallbackUrl(): String? =
    runCatching { Intent.parseUri(this, Intent.URI_INTENT_SCHEME) }
        .getOrNull()
        ?.getStringExtra("browser_fallback_url")
        ?.takeIf { it.isWebViewLoadableUrl() }

private fun readSpotifyCookies(
    cookieManager: CookieManager,
    currentUrl: String?,
): Map<String, String> {
    val urls =
        linkedSetOf(
            "https://open.spotify.com",
            "https://accounts.spotify.com",
            "https://spotify.com",
        )
    currentUrl?.toSpotifyCookieOrigin()?.let(urls::add)
    val cookies = linkedMapOf<String, String>()
    cookieManager.flush()
    urls.forEach { url ->
        cookieManager
            .getCookie(url)
            ?.split(";")
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            ?.forEach { part ->
                val separator = part.indexOf('=')
                if (separator <= 0) return@forEach
                val key = part.substring(0, separator).trim()
                val value = part.substring(separator + 1).trim()
                if (key.isNotBlank()) {
                    cookies[key] = value
                }
            }
    }
    return cookies
}

private fun String.toSpotifyCookieOrigin(): String? {
    val uri = runCatching { Uri.parse(this) }.getOrNull() ?: return null
    val host = uri.host?.lowercase() ?: return null
    if (host != "spotify.com" && !host.endsWith(".spotify.com")) return null
    val scheme =
        uri.scheme
            ?.takeIf { it.equals("https", ignoreCase = true) || it.equals("http", ignoreCase = true) }
            ?: "https"
    return "$scheme://$host"
}

@Composable
private fun SpotifyErrorDialog(
    message: String,
    onDismiss: () -> Unit,
) {
    DefaultDialog(
        onDismiss = onDismiss,
        title = { Text(stringResource(R.string.import_failed)) },
        buttons = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(android.R.string.ok))
            }
        },
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
 * Authorities registered by the Google Drive app's DocumentsProvider. When the user picks a
 * Drive folder via the system `OpenDocumentTree` picker, the returned tree URI has one of
 * these as its authority. Any other authority (e.g. `com.android.externalstorage.documents`
 * for local storage, `org.nextcloud.documents` for Nextcloud) means the user picked a
 * non-Drive folder — we reject those so backups don't silently land somewhere the user
 * didn't intend.
 *
 *   - `com.google.android.apps.docs.storage` — the modern Drive app.
 *   - `com.google.android.apps.docs.storage.legacy` — older Drive app variants still seen
 *     on some OEM ROMs.
 */
private val GOOGLE_DRIVE_AUTHORITIES = setOf(
    "com.google.android.apps.docs.storage",
    "com.google.android.apps.docs.storage.legacy",
)

/**
 * Returns true iff [uri] is a SAF tree URI whose authority matches the Google Drive
 * DocumentsProvider. Used to validate the result of `OpenDocumentTree` so we can reject
 * non-Drive picks before persisting them.
 */
private fun isGoogleDriveTreeUri(uri: Uri): Boolean {
    val authority = uri.authority ?: return false
    return authority in GOOGLE_DRIVE_AUTHORITIES
}

/**
 * Returns true iff the Google Drive app is installed and registered as a DocumentsProvider
 * on this device. We probe via [PackageManager.resolveContentProvider] (cheap, no IPC)
 * rather than `getPackageInfo` so we don't need to know Drive's exact package name (which
 * has changed historically) and so we accept any fork that registers the same authority.
 *
 * If Drive isn't installed, the SAF picker has no Drive root to navigate to, so the entire
 * Drive-sync feature is unusable — we surface a clear error to the user instead of letting
 * them pick a local folder by mistake.
 */
private fun isGoogleDriveInstalled(context: android.content.Context): Boolean {
    val resolveInfo = context.packageManager
        .resolveContentProvider("com.google.android.apps.docs.storage", 0)
    return resolveInfo != null
}
