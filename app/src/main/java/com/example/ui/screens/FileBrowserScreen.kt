package com.example.ui.screens

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.ServerEntity
import com.example.data.model.AutoRetryState
import com.example.data.model.ConnectionState
import com.example.data.model.FileBrowserItem
import com.example.data.model.FileKind
import com.example.ui.components.ConnectionErrorCard
import com.example.ui.components.ListSkeleton
import com.example.ui.components.MediaPreviewDialog
import com.example.ui.components.NoServerSelectedView
import com.example.ui.components.isAudioFile
import com.example.ui.components.isImageFile
import com.example.ui.components.isMediaFile
import com.example.ui.components.isVideoFile
import com.example.ui.theme.DietPiGreenLight
import com.example.ui.theme.DietPiGreenPrimary
import com.example.util.DownloadNotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    viewModel: com.example.ui.viewmodel.DietPiViewModel,
    activeServer: ServerEntity?,
    onOpenServerSelector: () -> Unit,
    isInitializing: Boolean,
    connectionState: ConnectionState = ConnectionState.Idle,
    onRetryConnection: () -> Unit = {},
    onEditServer: () -> Unit = {},
    isLoadingConnection: Boolean = false,
    autoRetryState: AutoRetryState? = null,
    onCancelAutoRetry: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (activeServer == null) {
        if (isInitializing) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        color = DietPiGreenPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = "Locating DietPi node...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            return
        }
        NoServerSelectedView(
            icon = Icons.Default.Folder,
            title = "No Server Selected",
            description = "Select or connect to a DietPi node to browse, upload, download, and edit files.",
            onOpenServerSelector = onOpenServerSelector,
            modifier = modifier.fillMaxSize(),
            testTag = "files_no_server_view"
        )
        return
    }

    val currentPath by viewModel.currentDirectoryPath.collectAsStateWithLifecycle()
    val directoryHistory by viewModel.directoryHistory.collectAsStateWithLifecycle()
    val items by viewModel.directoryItems.collectAsStateWithLifecycle()
    val isLoading by viewModel.isFileBrowserLoading.collectAsStateWithLifecycle()
    val errorMsg by viewModel.fileBrowserError.collectAsStateWithLifecycle()
    val showHidden by viewModel.showHiddenFiles.collectAsStateWithLifecycle()

    val canNavigateBack = remember(directoryHistory, currentPath) { viewModel.canNavigateBack() }
    val canNavigateUp = remember(currentPath) { currentPath != "/" && currentPath.isNotBlank() }

    val editingFilePath by viewModel.editingFilePath.collectAsStateWithLifecycle()
    val editingFileContent by viewModel.editingFileContent.collectAsStateWithLifecycle()
    val isEditingLoading by viewModel.isEditingFileLoading.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSavingFile.collectAsStateWithLifecycle()
    val editingError by viewModel.editingFileError.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }

    var showNewFileDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var itemToRename by remember { mutableStateOf<FileBrowserItem?>(null) }
    var itemToDelete by remember { mutableStateOf<FileBrowserItem?>(null) }
    var itemToDownload by remember { mutableStateOf<FileBrowserItem?>(null) }
    var previewMediaItem by remember { mutableStateOf<FileBrowserItem?>(null) }

    // Android 13+ Notification Permission Launcher
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Upload file launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val fileName = getFileNameFromUri(context, uri) ?: "uploaded_file"
                val bytes = readBytesFromUri(context, uri)
                if (bytes != null) {
                    snackbarHostState.showSnackbar("Uploading '$fileName'...")
                    viewModel.uploadFile(fileName, bytes) { success, err ->
                        coroutineScope.launch {
                            if (success) {
                                snackbarHostState.showSnackbar("Uploaded '$fileName' successfully")
                            } else {
                                snackbarHostState.showSnackbar("Upload failed: ${err ?: "Unknown error"}")
                            }
                        }
                    }
                } else {
                    snackbarHostState.showSnackbar("Failed to read selected file")
                }
            }
        }
    }

    // Load initial directory
    LaunchedEffect(activeServer.id) {
        viewModel.loadDirectory()
    }

    // In-App Text/Code Editor overlay
    if (editingFilePath != null) {
        InAppTextEditor(
            filePath = editingFilePath ?: "",
            initialContent = editingFileContent ?: "",
            isLoading = isEditingLoading,
            isSaving = isSaving,
            error = editingError,
            onClose = { viewModel.closeTextFile() },
            onSave = { updatedContent ->
                viewModel.saveCurrentTextFile(updatedContent) { success, err ->
                    coroutineScope.launch {
                        if (success) {
                            snackbarHostState.showSnackbar("File saved successfully")
                        } else {
                            snackbarHostState.showSnackbar("Save failed: ${err ?: "Unknown error"}")
                        }
                    }
                }
            },
            onRetry = {
                editingFilePath?.let { viewModel.openTextFile(it) }
            },
            modifier = modifier
        )
        return
    }

    val filteredItems = remember(items, searchQuery, showHidden) {
        items.filter { item ->
            val matchesHidden = showHidden || !item.isHidden
            val matchesSearch = searchQuery.isBlank() || item.name.contains(searchQuery, ignoreCase = true)
            matchesHidden && matchesSearch
        }
    }

    val hiddenCount = remember(items) { items.count { it.isHidden } }

    // Back handling: If searching, dismiss search on back
    BackHandler(enabled = searchQuery.isNotBlank() || isSearchActive) {
        searchQuery = ""
        isSearchActive = false
    }

    // Back handling: If there is a previous folder or parent folder to return to, navigate back
    BackHandler(enabled = canNavigateBack && searchQuery.isBlank() && !isSearchActive) {
        searchQuery = ""
        viewModel.navigateBack()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                // Breadcrumbs navigation bar
                PathBreadcrumbBar(
                    currentPath = currentPath,
                    onNavigate = { path ->
                        searchQuery = ""
                        viewModel.navigateToDirectory(path)
                    },
                    onNavigateBack = {
                        searchQuery = ""
                        viewModel.navigateBack()
                    },
                    canNavigateBack = canNavigateBack,
                    onNavigateUp = {
                        searchQuery = ""
                        viewModel.navigateUp()
                    },
                    canNavigateUp = canNavigateUp
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                // Actions and Search toolbar
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Left: Quick Action Chips (New Folder, New File, Upload)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SmallActionButton(
                                    icon = Icons.Default.CreateNewFolder,
                                    label = "Folder",
                                    onClick = { showNewFolderDialog = true },
                                    testTag = "btn_new_folder"
                                )

                                SmallActionButton(
                                    icon = Icons.Default.NoteAdd,
                                    label = "File",
                                    onClick = { showNewFileDialog = true },
                                    testTag = "btn_new_file"
                                )

                                SmallActionButton(
                                    icon = Icons.Default.Upload,
                                    label = "Upload",
                                    onClick = { filePickerLauncher.launch("*/*") },
                                    testTag = "btn_upload"
                                )
                            }

                            // Right: Toggle Hidden Files & Search Toggle & Refresh
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { viewModel.toggleShowHiddenFiles() },
                                    modifier = Modifier.size(36.dp).testTag("btn_toggle_hidden")
                                ) {
                                    BadgedBox(
                                        badge = {
                                            if (hiddenCount > 0 && !showHidden) {
                                                Badge(
                                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                                ) {
                                                    Text(hiddenCount.toString(), fontSize = 9.sp)
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (showHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = if (showHidden) "Hide hidden files" else "Show hidden files",
                                            tint = if (showHidden) DietPiGreenPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        isSearchActive = !isSearchActive
                                        if (!isSearchActive) searchQuery = ""
                                    },
                                    modifier = Modifier.size(36.dp).testTag("btn_toggle_search")
                                ) {
                                    Icon(
                                        imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                                        contentDescription = "Search Files",
                                        tint = if (isSearchActive) DietPiGreenPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { viewModel.loadDirectory(currentPath) },
                                    modifier = Modifier.size(36.dp).testTag("btn_refresh_files")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Refresh",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }
                        }

                        // Search Input Field (when expanded)
                        AnimatedVisibility(
                            visible = isSearchActive,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Filter files in this folder...", fontSize = 13.sp) },
                                singleLine = true,
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp)
                                    .testTag("file_search_field"),
                                textStyle = TextStyle(fontSize = 13.sp)
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (connectionState is ConnectionState.Error) {
                ConnectionErrorCard(
                    connectionState = connectionState,
                    onRetryConnection = onRetryConnection,
                    onEditServer = onEditServer,
                    isRetrying = isLoadingConnection,
                    autoRetryState = autoRetryState,
                    onCancelAutoRetry = onCancelAutoRetry,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
            when {
                isLoading && items.isEmpty() -> {
                    ListSkeleton(
                        modifier = Modifier.padding(16.dp),
                        itemCount = 7,
                        itemHeight = 62.dp,
                        testTag = "file_browser_skeleton"
                    )
                }
                errorMsg != null && items.isEmpty() && connectionState !is ConnectionState.Error -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "Failed to load directory",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = errorMsg ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Button(
                                onClick = { viewModel.loadDirectory(currentPath) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }
                filteredItems.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(54.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No files match \"$searchQuery\"" else "Folder is empty",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (hiddenCount > 0 && !showHidden) {
                            TextButton(onClick = { viewModel.toggleShowHiddenFiles() }) {
                                Text("Show $hiddenCount hidden items")
                            }
                        }
                    }
                }
                else -> {
                    val listState = rememberLazyListState()
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredItems, key = { it.path }) { item ->
                            FileItemRow(
                                item = item,
                                onClick = {
                                    if (item.isDirectory) {
                                        searchQuery = ""
                                        viewModel.navigateToDirectory(item.path)
                                    } else if (isMediaFile(item.name)) {
                                        previewMediaItem = item
                                    } else if (item.kind == FileKind.TextFile || isTextFile(item.name)) {
                                        viewModel.openTextFile(item.path)
                                    } else {
                                        // Confirm before downloading
                                        itemToDownload = item
                                    }
                                },
                                onEdit = {
                                    viewModel.openTextFile(item.path)
                                },
                                onPreview = {
                                    previewMediaItem = item
                                },
                                onRename = {
                                    itemToRename = item
                                },
                                onDelete = {
                                    itemToDelete = item
                                },
                                onDownload = {
                                    // Confirm before downloading
                                    itemToDownload = item
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

    // Media Preview Dialog (Images, Videos, Audio)
    previewMediaItem?.let { mediaItem ->
        MediaPreviewDialog(
            item = mediaItem,
            viewModel = viewModel,
            onDismiss = { previewMediaItem = null }
        )
    }

    // Dialog: Create New File
    if (showNewFileDialog) {
        InputDialog(
            title = "New File",
            label = "File Name",
            placeholder = "example.txt",
            confirmButtonText = "Create",
            onDismiss = { showNewFileDialog = false },
            onConfirm = { fileName ->
                showNewFileDialog = false
                viewModel.createFile(fileName) { success, err ->
                    coroutineScope.launch {
                        if (success) {
                            snackbarHostState.showSnackbar("Created file '$fileName'")
                            // Auto-open created text file in editor
                            val fullPath = if (currentPath.endsWith("/")) "$currentPath$fileName" else "$currentPath/$fileName"
                            viewModel.openTextFile(fullPath)
                        } else {
                            snackbarHostState.showSnackbar("Failed to create file: ${err ?: "Unknown"}")
                        }
                    }
                }
            }
        )
    }

    // Dialog: Create New Folder
    if (showNewFolderDialog) {
        InputDialog(
            title = "New Folder",
            label = "Folder Name",
            placeholder = "my-directory",
            confirmButtonText = "Create",
            onDismiss = { showNewFolderDialog = false },
            onConfirm = { folderName ->
                showNewFolderDialog = false
                viewModel.createFolder(folderName) { success, err ->
                    coroutineScope.launch {
                        if (success) {
                            snackbarHostState.showSnackbar("Created folder '$folderName'")
                        } else {
                            snackbarHostState.showSnackbar("Failed to create folder: ${err ?: "Unknown"}")
                        }
                    }
                }
            }
        )
    }

    // Dialog: Rename
    itemToRename?.let { item ->
        InputDialog(
            title = if (item.isDirectory) "Rename Folder" else "Rename File",
            label = "New Name",
            initialValue = item.name,
            confirmButtonText = "Rename",
            onDismiss = { itemToRename = null },
            onConfirm = { newName ->
                itemToRename = null
                viewModel.renameItem(item, newName) { success, err ->
                    coroutineScope.launch {
                        if (success) {
                            snackbarHostState.showSnackbar("Renamed to '$newName'")
                        } else {
                            snackbarHostState.showSnackbar("Failed to rename: ${err ?: "Unknown"}")
                        }
                    }
                }
            }
        )
    }

    // Dialog: Delete Confirmation
    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = {
                Text(if (item.isDirectory) "Delete Folder?" else "Delete File?")
            },
            text = {
                Text(
                    if (item.isDirectory)
                        "Are you sure you want to delete folder '${item.name}' and ALL its contents? This action cannot be undone."
                    else
                        "Are you sure you want to permanently delete '${item.name}'?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        itemToDelete = null
                        viewModel.deleteItem(item) { success, err ->
                            coroutineScope.launch {
                                if (success) {
                                    snackbarHostState.showSnackbar("Deleted '${item.name}'")
                                } else {
                                    snackbarHostState.showSnackbar("Failed to delete: ${err ?: "Unknown"}")
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog: Download Confirmation
    itemToDownload?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDownload = null },
            icon = {
                Icon(
                    Icons.Default.Download,
                    contentDescription = null,
                    tint = DietPiGreenPrimary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text("Download File?")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Do you want to download '${item.name}' to this device?")
                    Text(
                        text = "Size: ${item.formattedSize} • Path: ${item.path}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetItem = item
                        itemToDownload = null
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Downloading ${targetItem.name}...")
                            viewModel.downloadFile(targetItem) { success, bytes, err ->
                                if (success && bytes != null) {
                                    val savedResult = saveByteArrayToDownloads(context, targetItem.name, bytes)
                                    coroutineScope.launch {
                                        if (savedResult.success) {
                                            DownloadNotificationHelper.showDownloadNotification(
                                                context = context,
                                                fileName = targetItem.name,
                                                fileSizeFormatted = targetItem.formattedSize,
                                                fileUri = savedResult.uri
                                            )
                                            snackbarHostState.showSnackbar("Saved '${targetItem.name}' to Downloads")
                                        } else {
                                            snackbarHostState.showSnackbar("Downloaded '${targetItem.name}' (${bytes.size} bytes)")
                                        }
                                    }
                                } else {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Download failed: ${err ?: "Unknown"}")
                                    }
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DietPiGreenPrimary,
                        contentColor = Color.Black
                    )
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Download", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDownload = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// -------------------------------------------------------------
// Breadcrumbs Navigation Bar
// -------------------------------------------------------------

@Composable
fun PathBreadcrumbBar(
    currentPath: String,
    onNavigate: (String) -> Unit,
    onNavigateBack: () -> Unit,
    canNavigateBack: Boolean,
    onNavigateUp: () -> Unit,
    canNavigateUp: Boolean
) {
    val scrollState = rememberScrollState()
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    // Break down path into segments
    val segments = remember(currentPath) {
        val clean = currentPath.trim()
        if (clean.isEmpty() || clean == "/") {
            listOf(PathSegment(name = "root", fullPath = "/"))
        } else {
            val list = mutableListOf(PathSegment(name = "root", fullPath = "/"))
            val parts = clean.split("/").filter { it.isNotEmpty() }
            var accumulated = ""
            for (part in parts) {
                accumulated += "/$part"
                list.add(PathSegment(name = part, fullPath = accumulated))
            }
            list
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        // "Back" / "Previous Folder" Button
        IconButton(
            onClick = onNavigateBack,
            enabled = canNavigateBack,
            modifier = Modifier.size(36.dp).testTag("btn_navigate_back")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Previous Folder",
                tint = if (canNavigateBack) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.size(18.dp)
            )
        }

        // "Up" / "Parent Folder" Button
        IconButton(
            onClick = onNavigateUp,
            enabled = canNavigateUp,
            modifier = Modifier.size(36.dp).testTag("btn_navigate_up")
        ) {
            Icon(
                imageVector = Icons.Default.ArrowUpward,
                contentDescription = "Parent Folder",
                tint = if (canNavigateUp) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.size(18.dp)
            )
        }

        // Horizontal Breadcrumbs Scroll
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(scrollState),
            verticalAlignment = Alignment.CenterVertically
        ) {
            segments.forEachIndexed { index, seg ->
                val isLast = index == segments.size - 1

                Surface(
                    onClick = { onNavigate(seg.fullPath) },
                    shape = RoundedCornerShape(6.dp),
                    color = if (isLast) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else Color.Transparent,
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    Text(
                        text = if (index == 0) "/" else seg.name,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (isLast) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (isLast) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }

                if (!isLast) {
                    Text(
                        text = "/",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                }
            }
        }

        // Copy Path Button
        IconButton(
            onClick = {
                clipboardManager.setText(AnnotatedString(currentPath))
            },
            modifier = Modifier.size(32.dp).testTag("btn_copy_path")
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy Path",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

data class PathSegment(val name: String, val fullPath: String)

fun isTextFile(name: String): Boolean {
    val lower = name.lowercase()
    val ext = lower.substringAfterLast('.', "")
    return ext in setOf(
        "txt", "log", "conf", "cfg", "ini", "service", "env", "properties",
        "json", "yaml", "yml", "toml", "xml", "html", "htm", "css", "scss",
        "js", "ts", "mjs", "cjs", "py", "pyw", "sh", "bash", "zsh", "md", "csv",
        "c", "cpp", "h", "hpp", "java", "kt", "kts", "go", "rs", "php", "rb", "sql", "lua"
    ) || lower in setOf("dockerfile", "makefile", "dietpi.txt", "config.txt", ".bashrc", ".profile", "hosts", "fstab")
}

// -------------------------------------------------------------
// File Item Row
// -------------------------------------------------------------

@Composable
fun FileItemRow(
    item: FileBrowserItem,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDownload: () -> Unit,
    onPreview: () -> Unit = {}
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (item.isHidden) 0.25f else 0.5f)
            )
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("file_item_${item.name}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon with colored badge background
            val (icon, tint, bg) = getFileIconStyle(item)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(bg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // File Name & Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = if (item.isDirectory) FontFamily.Default else FontFamily.Monospace,
                        fontWeight = if (item.isDirectory) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    color = if (item.isHidden) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (item.isDirectory) "Directory" else item.formattedSize,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (isImageFile(item.name)) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFF472B6).copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "Image",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = Color(0xFFF472B6),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    } else if (isVideoFile(item.name)) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFF87171).copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "Video",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = Color(0xFFF87171),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    } else if (isAudioFile(item.name)) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFFB923C).copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "Audio",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = Color(0xFFFB923C),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    } else if (item.kind == FileKind.TextFile || isTextFile(item.name)) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = "Editable",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            // More Options Menu
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(32.dp).testTag("btn_menu_${item.name}")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    if (isMediaFile(item.name)) {
                        DropdownMenuItem(
                            text = { Text("Preview Media") },
                            leadingIcon = { Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp), tint = DietPiGreenLight) },
                            onClick = {
                                menuExpanded = false
                                onPreview()
                            }
                        )
                    }

                    if (item.kind == FileKind.TextFile || isTextFile(item.name)) {
                        DropdownMenuItem(
                            text = { Text("Edit File") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                menuExpanded = false
                                onEdit()
                            }
                        )
                    }

                    if (!item.isDirectory) {
                        DropdownMenuItem(
                            text = { Text("Download") },
                            leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                menuExpanded = false
                                onDownload()
                            }
                        )
                    }

                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        }
                    )

                    HorizontalDivider()

                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// In-App Text/Code Editor
// -------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InAppTextEditor(
    filePath: String,
    initialContent: String,
    isLoading: Boolean,
    isSaving: Boolean,
    error: String?,
    onClose: () -> Unit,
    onSave: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fileName = remember(filePath) { filePath.substringAfterLast('/') }
    var contentValue by remember(initialContent) {
        mutableStateOf(TextFieldValue(initialContent))
    }
    var showDiscardDialog by remember { mutableStateOf(false) }
    val isModified = contentValue.text != initialContent

    BackHandler {
        if (isModified) {
            showDiscardDialog = true
        } else {
            onClose()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = fileName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isModified) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.error)
                                )
                            }
                        }
                        Text(
                            text = filePath,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isModified) showDiscardDialog = true else onClose()
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close Editor")
                    }
                },
                actions = {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp).padding(end = 12.dp),
                            strokeWidth = 2.dp,
                            color = DietPiGreenPrimary
                        )
                    } else {
                        Button(
                            onClick = {
                                onSave(contentValue.text)
                            },
                            enabled = isModified && !isLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DietPiGreenPrimary,
                                contentColor = Color.Black
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.padding(end = 8.dp).testTag("btn_save_file")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save", fontWeight = FontWeight.Bold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFF1E1E1E)) // Dark code canvas
        ) {
            when {
                isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = DietPiGreenPrimary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Loading $fileName...",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp
                        )
                    }
                }
                error != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = error, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = onRetry) {
                            Text("Retry")
                        }
                    }
                }
                else -> {
                    OutlinedTextField(
                        value = contentValue,
                        onValueChange = {
                            contentValue = it
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .testTag("file_editor_textfield"),
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = Color(0xFFD4D4D4),
                            lineHeight = 18.sp
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color(0xFF1E1E1E),
                            unfocusedContainerColor = Color(0xFF1E1E1E),
                            cursorColor = DietPiGreenPrimary
                        )
                    )
                }
            }
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard Unsaved Changes?") },
            text = { Text("You have unsaved changes to '$fileName'. Are you sure you want to discard them?") },
            confirmButton = {
                Button(
                    onClick = {
                        showDiscardDialog = false
                        onClose()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// -------------------------------------------------------------
// Small Helpers & Dialogs
// -------------------------------------------------------------

@Composable
fun SmallActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        ),
        modifier = Modifier.testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun InputDialog(
    title: String,
    label: String,
    initialValue: String = "",
    placeholder: String = "",
    confirmButtonText: String = "OK",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }
    var hasError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        hasError = it.isBlank() || it.contains("/")
                    },
                    label = { Text(label) },
                    placeholder = { Text(placeholder) },
                    singleLine = true,
                    isError = hasError,
                    supportingText = {
                        if (hasError) {
                            Text("Name cannot be empty or contain '/'", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (text.isNotBlank() && !text.contains("/")) {
                            onConfirm(text.trim())
                        }
                    }),
                    modifier = Modifier.fillMaxWidth().testTag("input_dialog_field")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(text.trim()) },
                enabled = text.isNotBlank() && !text.contains("/"),
                colors = ButtonDefaults.buttonColors(containerColor = DietPiGreenPrimary, contentColor = Color.Black)
            ) {
                Text(confirmButtonText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

fun getFileIconStyle(item: FileBrowserItem): Triple<ImageVector, Color, Color> {
    if (item.isDirectory) {
        return Triple(
            Icons.Default.Folder,
            Color(0xFFE5A93C), // Amber/gold folder
            Color(0xFFE5A93C).copy(alpha = 0.15f)
        )
    }

    val ext = item.name.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "txt", "log", "conf", "cfg", "ini", "service", "env" -> Triple(
            Icons.Default.Description,
            Color(0xFF38BDF8), // Cyan
            Color(0xFF38BDF8).copy(alpha = 0.15f)
        )
        "sh", "bash", "py", "js", "ts", "json", "yaml", "yml", "toml", "xml", "html", "css" -> Triple(
            Icons.Default.Code,
            DietPiGreenLight, // DietPi light green
            DietPiGreenLight.copy(alpha = 0.15f)
        )
        "tar", "gz", "zip", "bz2", "xz", "7z", "deb" -> Triple(
            Icons.Default.Archive,
            Color(0xFFA78BFA), // Purple
            Color(0xFFA78BFA).copy(alpha = 0.15f)
        )
        "png", "jpg", "jpeg", "svg", "webp", "gif", "ico" -> Triple(
            Icons.Default.Image,
            Color(0xFFF472B6), // Pink
            Color(0xFFF472B6).copy(alpha = 0.15f)
        )
        "mp3", "flac", "wav", "ogg", "m4a" -> Triple(
            Icons.Default.MusicNote,
            Color(0xFFFB923C), // Orange
            Color(0xFFFB923C).copy(alpha = 0.15f)
        )
        "mp4", "mkv", "avi", "mov", "webm" -> Triple(
            Icons.Default.Movie,
            Color(0xFFF87171), // Red
            Color(0xFFF87171).copy(alpha = 0.15f)
        )
        else -> Triple(
            Icons.Default.InsertDriveFile,
            Color(0xFF94A3B8), // Slate
            Color(0xFF94A3B8).copy(alpha = 0.15f)
        )
    }
}

private fun getFileNameFromUri(context: Context, uri: Uri): String? {
    return try {
        var name: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        name = cursor.getString(index)
                    }
                }
            }
        }
        name ?: uri.path?.substringAfterLast('/')
    } catch (_: Exception) {
        null
    }
}

private fun readBytesFromUri(context: Context, uri: Uri): ByteArray? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    } catch (_: Exception) {
        null
    }
}

data class SavedDownloadResult(
    val success: Boolean,
    val uri: Uri? = null,
    val absolutePath: String? = null
)

fun saveByteArrayToDownloads(context: Context, filename: String, bytes: ByteArray): SavedDownloadResult {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/DietPi")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(bytes)
                }
                SavedDownloadResult(success = true, uri = uri)
            } else {
                SavedDownloadResult(success = false)
            }
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val subDir = File(downloadsDir, "DietPi")
            subDir.mkdirs()
            val file = File(subDir, filename)
            file.writeBytes(bytes)
            SavedDownloadResult(success = true, uri = Uri.fromFile(file), absolutePath = file.absolutePath)
        }
    } catch (_: Exception) {
        SavedDownloadResult(success = false)
    }
}
