package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.example.data.model.FileCategory
import com.example.data.model.FileItem
import com.example.ui.components.CompressDialog
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
fun CategoryScreen(
    category: FileCategory,
    viewModel: FileManagerViewModel,
    onNavigateBack: () -> Unit,
    onOpenFile: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val items by viewModel.categoryItems.collectAsState()
    val isLoading by viewModel.isLoadingCategory.collectAsState()
    var isGrid by remember {
        mutableStateOf(category == FileCategory.IMAGES || category == FileCategory.CAMERA || category == FileCategory.SCREENSHOTS)
    }

    var selectedFileForRename by remember { mutableStateOf<File?>(null) }
    var selectedFileForDelete by remember { mutableStateOf<File?>(null) }
    var selectedFileForVault by remember { mutableStateOf<File?>(null) }
    var selectedFileForCompress by remember { mutableStateOf<File?>(null) }
    var selectedFileForProperties by remember { mutableStateOf<File?>(null) }
    var selectedFileForOpenWith by remember { mutableStateOf<File?>(null) }
    var selectedDateFilter by remember { mutableStateOf("all") }

    BackHandler { onNavigateBack() }

    val now = remember { System.currentTimeMillis() }
    val oneDayMs = 24 * 60 * 60 * 1000L
    val sevenDaysMs = 7 * oneDayMs

    val filteredItems = remember(items, selectedDateFilter) {
        when (selectedDateFilter) {
            "today" -> items.filter { (now - it.lastModified) < oneDayMs }
            "yesterday" -> items.filter { (now - it.lastModified) in oneDayMs..(2 * oneDayMs) }
            "this_week" -> items.filter { (now - it.lastModified) < sevenDaysMs }
            "older" -> items.filter { (now - it.lastModified) >= sevenDaysMs }
            else -> items
        }
    }

    val totalSize = remember(filteredItems) { filteredItems.sumOf { it.size } }

    val dateFilters = remember {
        listOf("all" to "All", "today" to "Today", "yesterday" to "Yesterday", "this_week" to "This Week", "older" to "Older")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = category.displayName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "${filteredItems.size} files • ${FileItem.formatFileSize(totalSize)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("category_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { isGrid = !isGrid }) {
                        Icon(
                            imageVector = if (isGrid) Icons.Default.ViewList else Icons.Default.GridView,
                            contentDescription = "Toggle View",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { viewModel.loadCategoryFiles(category) }) {
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
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Date Filter Chips
            if (items.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    dateFilters.forEach { (filterKey, filterLabel) ->
                        FilterChip(
                            selected = selectedDateFilter == filterKey,
                            onClick = { selectedDateFilter = filterKey },
                            label = { Text(filterLabel) },
                            shape = RoundedCornerShape(8.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 3.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else if (filteredItems.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.InsertDriveFile,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (items.isEmpty()) "No ${category.displayName.lowercase()} found" else "No items match date filter",
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
                        items(filteredItems) { item ->
                            FileGridItem(
                                item = item,
                                onClick = {
                                    viewModel.recordFileAccess(item.file)
                                    onOpenFile(item.file)
                                }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(filteredItems) { item ->
                            FileListItem(
                                item = item,
                                onClick = {
                                    viewModel.recordFileAccess(item.file)
                                    onOpenFile(item.file)
                                },
                                onMoveToVault = { selectedFileForVault = item.file },
                                onRename = { selectedFileForRename = item.file },
                                onDelete = { selectedFileForDelete = item.file },
                                onCopy = { viewModel.setClipboard(item.file, isCut = false) },
                                onCut = { viewModel.setClipboard(item.file, isCut = true) },
                                onCompress = { selectedFileForCompress = item.file },
                                onExtract = { viewModel.extractArchive(item.file) },
                                onProperties = { selectedFileForProperties = item.file },
                                onShare = { FileShareUtils.shareFile(context, item.file) },
                                onOpenWith = { selectedFileForOpenWith = item.file },
                                onInstallApk = { FileOpener.installApk(context, item.file) }
                            )
                        }
                    }
                }
            }
        }
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

    selectedFileForOpenWith?.let { file ->
        OpenWithDialog(
            file = file,
            onDismiss = { selectedFileForOpenWith = null }
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

    selectedFileForProperties?.let { file ->
        FilePropertiesDialog(
            file = file,
            repository = viewModel.repository,
            onDismiss = { selectedFileForProperties = null }
        )
    }
}
