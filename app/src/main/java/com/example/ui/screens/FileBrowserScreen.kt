package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.FileItem
import com.example.data.model.SortBy
import com.example.ui.components.BreadcrumbBar
import com.example.ui.components.CompressDialog
import com.example.ui.components.CreateFileDialog
import com.example.ui.components.DeleteConfirmationDialog
import com.example.ui.components.FileGridItem
import com.example.ui.components.FileListItem
import com.example.ui.components.FilePropertiesDialog
import com.example.ui.components.MoveToVaultDialog
import com.example.ui.components.OpenWithDialog
import com.example.ui.components.RenameDialog
import com.example.ui.viewmodel.FileManagerViewModel
import androidx.activity.compose.BackHandler
import com.example.util.FileOpener
import com.example.util.FileShareUtils
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    viewModel: FileManagerViewModel,
    onNavigateBack: () -> Unit,
    onOpenFile: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentDir by viewModel.currentDirectory.collectAsState()
    val items by viewModel.directoryItems.collectAsState()
    val isLoading by viewModel.isLoadingDirectory.collectAsState()
    val isGrid by viewModel.isGridView.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val batchClipboard by viewModel.batchClipboard.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedFiles by viewModel.selectedFiles.collectAsState()

    BackHandler {
        if (isSelectionMode) {
            viewModel.clearSelection()
        } else if (viewModel.navigateToParent()) {
            // Successfully navigated to parent directory
        } else {
            onNavigateBack()
        }
    }
    val canGoForward by viewModel.canGoForward.collectAsState()
    val showHidden by viewModel.showHiddenFiles.collectAsState()
    val currentOperation by viewModel.currentOperation.collectAsState()

    var showSortMenu by remember { mutableStateOf(false) }
    var showCreateFileDialog by remember { mutableStateOf(false) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }

    var selectedFileForRename by remember { mutableStateOf<File?>(null) }
    var selectedFileForDelete by remember { mutableStateOf<File?>(null) }
    var selectedFileForVault by remember { mutableStateOf<File?>(null) }
    var selectedFileForCompress by remember { mutableStateOf<File?>(null) }
    var selectedFileForProperties by remember { mutableStateOf<File?>(null) }
    var selectedFileForOpenWith by remember { mutableStateOf<File?>(null) }

    val rootDir = remember { viewModel.repository.getInternalStorageRoot() }

    val totalSelectedSize = remember(selectedFiles) {
        selectedFiles.sumOf { if (it.isDirectory) 0L else it.length() }
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                // Selection Mode TopAppBar
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "${selectedFiles.size} selected",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            if (totalSelectedSize > 0) {
                                Text(
                                    text = FileItem.formatFileSize(totalSelectedSize),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { viewModel.clearSelection() },
                            modifier = Modifier.testTag("exit_selection_button")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Exit selection")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                if (selectedFiles.size == items.size) {
                                    viewModel.deselectAll()
                                } else {
                                    viewModel.selectAll()
                                }
                            },
                            modifier = Modifier.testTag("select_all_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (selectedFiles.size == items.size && items.isNotEmpty())
                                    Icons.Default.Deselect
                                else Icons.Default.SelectAll,
                                contentDescription = "Toggle Select All"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            } else {
                // Normal Mode TopAppBar
                TopAppBar(
                    title = {
                        Text(
                            text = if (currentDir.absolutePath == rootDir.absolutePath) "Internal Storage" else currentDir.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                if (!viewModel.navigateToParent()) {
                                    onNavigateBack()
                                }
                            },
                            modifier = Modifier.testTag("browser_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    actions = {
                        // Forward Navigation (Browser History)
                        if (canGoForward) {
                            IconButton(
                                onClick = { viewModel.navigateForward() },
                                modifier = Modifier.testTag("browser_forward_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Forward",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // Toggle Hidden Files
                        IconButton(
                            onClick = { viewModel.toggleShowHiddenFiles() },
                            modifier = Modifier.testTag("toggle_hidden_files_button")
                        ) {
                            Icon(
                                imageVector = if (showHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (showHidden) "Hide hidden files" else "Show hidden files",
                                tint = if (showHidden) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Toggle View Mode
                        IconButton(
                            onClick = { viewModel.toggleGridView() },
                            modifier = Modifier.testTag("browser_view_mode_toggle")
                        ) {
                            Icon(
                                imageVector = if (isGrid) Icons.Default.ViewList else Icons.Default.GridView,
                                contentDescription = "Toggle view",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Sort Menu
                        Box {
                            IconButton(
                                onClick = { showSortMenu = true },
                                modifier = Modifier.testTag("browser_sort_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = "Sort",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Sort by Name") },
                                    onClick = {
                                        showSortMenu = false
                                        viewModel.setSortOption(SortBy.NAME)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Sort by Date") },
                                    onClick = {
                                        showSortMenu = false
                                        viewModel.setSortOption(SortBy.DATE)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Sort by Size") },
                                    onClick = {
                                        showSortMenu = false
                                        viewModel.setSortOption(SortBy.SIZE)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Sort by Type") },
                                    onClick = {
                                        showSortMenu = false
                                        viewModel.setSortOption(SortBy.TYPE)
                                    }
                                )
                            }
                        }

                        IconButton(onClick = { viewModel.refreshDirectory() }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                FloatingActionButton(
                    onClick = { showCreateFileDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("browser_new_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New folder or file")
                }
            }
        },
        bottomBar = {
            if (isSelectionMode) {
                // Multi-Selection Bottom Action Bar
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.copySelectedFiles() }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(20.dp))
                                Text("Copy", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        IconButton(onClick = { viewModel.cutSelectedFiles() }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.ContentCut, contentDescription = "Move", modifier = Modifier.size(20.dp))
                                Text("Move", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        IconButton(onClick = { showBatchDeleteDialog = true }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                Text("Trash", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        IconButton(onClick = {
                            val list = selectedFiles.toList()
                            if (list.size == 1) {
                                FileShareUtils.shareFile(context, list.first())
                            } else if (list.isNotEmpty()) {
                                FileShareUtils.shareMultipleFiles(context, list)
                            }
                        }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(20.dp))
                                Text("Share", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        IconButton(onClick = {
                            val targetFiles = selectedFiles.toList()
                            if (targetFiles.isNotEmpty()) {
                                viewModel.compressFiles(targetFiles, "Archive_${System.currentTimeMillis()}")
                                viewModel.clearSelection()
                            }
                        }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Archive, contentDescription = "Zip", modifier = Modifier.size(20.dp))
                                Text("Zip", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        IconButton(onClick = {
                            selectedFiles.forEach { viewModel.moveFileToVault(it) }
                            viewModel.clearSelection()
                        }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Lock, contentDescription = "Vault", modifier = Modifier.size(20.dp))
                                Text("Vault", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            } else {
                // Batch Clipboard Bar (when files are in clipboard waiting to be pasted)
                batchClipboard?.let { clip ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                        shadowElevation = 6.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${if (clip.isCut) "Moving" else "Copying"} ${clip.totalCount} items (${FileItem.formatFileSize(clip.totalSize)})",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = "Target: ${currentDir.name}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { viewModel.pasteBatchClipboard(currentDir) },
                                    modifier = Modifier.testTag("paste_clipboard_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentPaste,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Paste")
                                }
                                IconButton(onClick = { viewModel.clearBatchClipboard() }) {
                                    Icon(Icons.Default.Close, contentDescription = "Cancel")
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Breadcrumbs Navigation Bar
                BreadcrumbBar(
                    currentDir = currentDir,
                    rootDir = rootDir,
                    onNavigateTo = { dir ->
                        viewModel.navigateToDirectory(dir)
                    }
                )

                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 3.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else if (items.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "This folder is empty",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (isGrid) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 100.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(items, key = { it.path }) { item ->
                            val isSelected = selectedFiles.contains(item.file)
                            FileGridItem(
                                item = item,
                                isSelected = isSelected,
                                isSelectionMode = isSelectionMode,
                                onClick = {
                                    if (isSelectionMode) {
                                        viewModel.toggleFileSelection(item.file)
                                    } else {
                                        if (item.isDirectory) {
                                            viewModel.navigateToDirectory(item.file)
                                        } else {
                                            viewModel.recordFileAccess(item.file)
                                            onOpenFile(item.file)
                                        }
                                    }
                                },
                                onLongClick = {
                                    viewModel.toggleFileSelection(item.file)
                                }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(items, key = { it.path }) { item ->
                            val isSelected = selectedFiles.contains(item.file)
                            FileListItem(
                                item = item,
                                isSelected = isSelected,
                                isSelectionMode = isSelectionMode,
                                onClick = {
                                    if (isSelectionMode) {
                                        viewModel.toggleFileSelection(item.file)
                                    } else {
                                        if (item.isDirectory) {
                                            viewModel.navigateToDirectory(item.file)
                                        } else {
                                            viewModel.recordFileAccess(item.file)
                                            onOpenFile(item.file)
                                        }
                                    }
                                },
                                onLongClick = {
                                    viewModel.toggleFileSelection(item.file)
                                },
                                onMoveToVault = {
                                    selectedFileForVault = item.file
                                },
                                onRename = {
                                    selectedFileForRename = item.file
                                },
                                onDelete = {
                                    selectedFileForDelete = item.file
                                },
                                onCopy = {
                                    viewModel.setClipboard(item.file, isCut = false)
                                },
                                onCut = {
                                    viewModel.setClipboard(item.file, isCut = true)
                                },
                                onCompress = {
                                    selectedFileForCompress = item.file
                                },
                                onExtract = {
                                    viewModel.extractArchive(item.file)
                                },
                                onProperties = {
                                    selectedFileForProperties = item.file
                                },
                                onShare = {
                                    FileShareUtils.shareFile(context, item.file)
                                },
                                onOpenWith = {
                                    selectedFileForOpenWith = item.file
                                },
                                onInstallApk = {
                                    FileOpener.installApk(context, item.file)
                                }
                            )
                        }
                    }
                }
            }

            // Real Operation Progress Overlay Card (Transfer Progress / Speed / ETA)
            currentOperation?.let { op ->
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = op.title,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            IconButton(
                                onClick = { viewModel.dismissCurrentOperation() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LinearProgressIndicator(
                            progress = { op.progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp),
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${op.processedFiles}/${op.totalFiles} files (${FileItem.formatFileSize(op.processedBytes)}/${FileItem.formatFileSize(op.totalBytes)})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${op.formattedSpeed} • ${op.formattedRemainingTime}",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialogs
    selectedFileForOpenWith?.let { file ->
        OpenWithDialog(
            file = file,
            onDismiss = { selectedFileForOpenWith = null }
        )
    }

    if (showCreateFileDialog) {
        CreateFileDialog(
            onDismiss = { showCreateFileDialog = false },
            onConfirm = { name, fileType, content ->
                showCreateFileDialog = false
                viewModel.createNewFile(name, fileType, content)
            }
        )
    }

    selectedFileForRename?.let { file ->
        RenameDialog(
            file = file,
            onDismiss = { selectedFileForRename = null },
            onConfirm = { newName ->
                selectedFileForRename = null
                viewModel.renameFile(file, newName)
            }
        )
    }

    selectedFileForDelete?.let { file ->
        DeleteConfirmationDialog(
            file = file,
            onDismiss = { selectedFileForDelete = null },
            onConfirm = {
                selectedFileForDelete = null
                viewModel.moveFileToTrash(file)
            }
        )
    }

    if (showBatchDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteDialog = false },
            title = { Text("Move ${selectedFiles.size} items to Trash?") },
            text = { Text("Selected items (${FileItem.formatFileSize(totalSelectedSize)}) will be moved to the Recycle Bin and can be restored later.") },
            confirmButton = {
                Button(
                    onClick = {
                        showBatchDeleteDialog = false
                        viewModel.moveSelectedToTrash()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Move to Trash")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    selectedFileForVault?.let { file ->
        MoveToVaultDialog(
            file = file,
            onDismiss = { selectedFileForVault = null },
            onConfirm = { deleteOriginal ->
                selectedFileForVault = null
                viewModel.moveFileToVault(file, deleteOriginal)
            }
        )
    }

    selectedFileForCompress?.let { file ->
        CompressDialog(
            file = file,
            onDismiss = { selectedFileForCompress = null },
            onConfirm = { zipName ->
                selectedFileForCompress = null
                viewModel.compressFiles(listOf(file), zipName)
            }
        )
    }

    selectedFileForProperties?.let { file ->
        FilePropertiesDialog(
            file = file,
            repository = viewModel.repository,
            onDismiss = { selectedFileForProperties = null }
        )
    }
}
