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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.example.ui.components.CompressDialog
import com.example.ui.components.DeleteConfirmationDialog
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
fun SearchScreen(
    viewModel: FileManagerViewModel,
    onNavigateBack: () -> Unit,
    onOpenFile: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val query by viewModel.searchQuery.collectAsState()
    val categoryFilter by viewModel.searchCategoryFilter.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()

    var selectedFileForOpenWith by remember { mutableStateOf<File?>(null) }
    var selectedFileForRename by remember { mutableStateOf<File?>(null) }
    var selectedFileForDelete by remember { mutableStateOf<File?>(null) }
    var selectedFileForProperties by remember { mutableStateOf<File?>(null) }
    var selectedFileForCompress by remember { mutableStateOf<File?>(null) }
    var selectedFileForVault by remember { mutableStateOf<File?>(null) }

    BackHandler { onNavigateBack() }

    val filterCategories = remember {
        listOf(
            null to "All",
            FileCategory.IMAGES to "Images",
            FileCategory.VIDEOS to "Videos",
            FileCategory.AUDIO to "Music",
            FileCategory.DOCUMENTS to "Docs",
            FileCategory.APKS to "APKs",
            FileCategory.ARCHIVES to "Archives"
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { viewModel.onSearchQueryChanged(it) },
                        placeholder = { Text("Search files offline...") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                }
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("search_text_input")
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("search_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Category Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filterCategories.forEach { (cat, label) ->
                    val isSelected = categoryFilter == cat
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.onSearchCategoryFilterChanged(cat) },
                        label = { Text(label) },
                        shape = RoundedCornerShape(8.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("search_filter_$label")
                    )
                }
            }

            if (isSearching) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else if (query.isBlank() && categoryFilter == null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Search across all local folders",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Try advanced syntax queries:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val suggestions = listOf("type:pdf", "type:image", "type:video", "type:doc", "size:>100MB", "size:<10MB", "modified:today")
                        suggestions.forEach { sugg ->
                            AssistChip(
                                onClick = { viewModel.onSearchQueryChanged(sugg) },
                                label = { Text(sugg) }
                            )
                        }
                    }
                }
            } else if (searchResults.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No matching files found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Text(
                    text = "${searchResults.size} results found",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(searchResults) { item ->
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
                viewModel.moveFileToTrash(file)
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
