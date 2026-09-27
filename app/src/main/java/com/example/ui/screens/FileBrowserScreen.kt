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
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.example.data.model.SortBy
import com.example.ui.components.BreadcrumbBar
import com.example.ui.components.CompressDialog
import com.example.ui.components.CreateFolderDialog
import com.example.ui.components.DeleteConfirmationDialog
import com.example.ui.components.FileGridItem
import com.example.ui.components.FileListItem
import com.example.ui.components.FilePropertiesDialog
import com.example.ui.components.MoveToVaultDialog
import com.example.ui.components.OpenWithDialog
import com.example.ui.components.RenameDialog
import com.example.ui.viewmodel.FileManagerViewModel
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
    val clipboard by viewModel.clipboardFile.collectAsState()

    var showSortMenu by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }

    var selectedFileForRename by remember { mutableStateOf<File?>(null) }
    var selectedFileForDelete by remember { mutableStateOf<File?>(null) }
    var selectedFileForVault by remember { mutableStateOf<File?>(null) }
    var selectedFileForCompress by remember { mutableStateOf<File?>(null) }
    var selectedFileForProperties by remember { mutableStateOf<File?>(null) }
    var selectedFileForOpenWith by remember { mutableStateOf<File?>(null) }

    val rootDir = remember { viewModel.repository.getInternalStorageRoot() }

    Scaffold(
        topBar = {
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

                    IconButton(
                        onClick = { showCreateFolderDialog = true },
                        modifier = Modifier.testTag("browser_new_folder_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CreateNewFolder,
                            contentDescription = "New folder",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
        },
        bottomBar = {
            clipboard?.let { (file, isCut) ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    shadowElevation = 4.dp
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
                                text = "${if (isCut) "Moving" else "Copying"}: ${file.name}",
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
                                onClick = { viewModel.pasteClipboard(currentDir) },
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
                            IconButton(onClick = { viewModel.setClipboard(file, isCut = false) }) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel")
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
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
                    items(items) { item ->
                        FileGridItem(
                            item = item,
                            onClick = {
                                if (item.isDirectory) {
                                    viewModel.navigateToDirectory(item.file)
                                } else {
                                    viewModel.recordFileAccess(item.file)
                                    onOpenFile(item.file)
                                }
                            }
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(items) { item ->
                        FileListItem(
                            item = item,
                            onClick = {
                                if (item.isDirectory) {
                                    viewModel.navigateToDirectory(item.file)
                                } else {
                                    viewModel.recordFileAccess(item.file)
                                    onOpenFile(item.file)
                                }
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
    }

    // Dialogs
    selectedFileForOpenWith?.let { file ->
        OpenWithDialog(
            file = file,
            onDismiss = { selectedFileForOpenWith = null }
        )
    }

    if (showCreateFolderDialog) {
        CreateFolderDialog(
            onDismiss = { showCreateFolderDialog = false },
            onConfirm = { name ->
                showCreateFolderDialog = false
                viewModel.createNewFolder(name)
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
                viewModel.deleteFile(file)
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
