package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.VaultEntity
import com.example.data.model.FileItem
import com.example.ui.viewmodel.FileManagerViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class VaultCategory(val displayName: String, val icon: ImageVector) {
    ALL("All Files", Icons.Default.Folder),
    IMAGES("Photos", Icons.Default.Image),
    VIDEOS("Videos", Icons.Default.Movie),
    PDFS("PDFs", Icons.Default.PictureAsPdf),
    DOCS("Documents", Icons.Default.Description)
}

fun VaultEntity.matchesCategory(category: VaultCategory): Boolean {
    val ext = File(originalFileName).extension.lowercase()
    return when (category) {
        VaultCategory.ALL -> true
        VaultCategory.IMAGES -> ext in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "svg") || this.category == "IMAGE"
        VaultCategory.VIDEOS -> ext in listOf("mp4", "mkv", "avi", "mov", "webm", "3gp", "flv", "m4v") || this.category == "VIDEO"
        VaultCategory.PDFS -> ext == "pdf" || this.category == "PDF"
        VaultCategory.DOCS -> ext in listOf("txt", "md", "json", "xml", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "rtf", "odt", "csv", "log") || this.category in listOf("DOCUMENT", "TEXT")
    }
}

fun getVaultTypeIcon(item: VaultEntity): Pair<ImageVector, Color> {
    val ext = File(item.originalFileName).extension.lowercase()
    return when {
        ext in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "svg") || item.category == "IMAGE" ->
            Pair(Icons.Default.Image, Color(0xFF2563EB))
        ext in listOf("mp4", "mkv", "avi", "mov", "webm", "3gp", "flv", "m4v") || item.category == "VIDEO" ->
            Pair(Icons.Default.Movie, Color(0xFF7C3AED))
        ext == "pdf" || item.category == "PDF" ->
            Pair(Icons.Default.PictureAsPdf, Color(0xFFDC2626))
        ext in listOf("mp3", "wav", "ogg", "m4a", "aac", "flac") || item.category == "AUDIO" ->
            Pair(Icons.Default.AudioFile, Color(0xFF059669))
        ext in listOf("zip", "rar", "7z", "tar", "gz") || item.category == "ARCHIVE" ->
            Pair(Icons.Default.FolderZip, Color(0xFFD97706))
        else ->
            Pair(Icons.Default.Description, Color(0xFF4B5563))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    viewModel: FileManagerViewModel,
    onNavigateBack: () -> Unit,
    onOpenFile: (File, VaultEntity) -> Unit,
    onNavigateToBrowseForEncryption: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val vaultState by viewModel.vaultState.collectAsState()
    val vaultItems by viewModel.vaultDbItems.collectAsState()

    var pinInput by remember { mutableStateOf("") }
    var confirmPinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    var selectedItemForUnlock by remember { mutableStateOf<VaultEntity?>(null) }
    var restoreToOriginalLocation by remember { mutableStateOf(true) }
    var selectedItemForExport by remember { mutableStateOf<VaultEntity?>(null) }
    var selectedItemForDelete by remember { mutableStateOf<VaultEntity?>(null) }
    var selectedItemForProperties by remember { mutableStateOf<VaultEntity?>(null) }

    var selectedVaultItemIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showUnlockAllDialog by remember { mutableStateOf(false) }
    var showUnlockSelectedDialog by remember { mutableStateOf(false) }
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }

    var selectedCategory by remember { mutableStateOf(VaultCategory.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var isGridView by remember { mutableStateOf(true) }

    BackHandler {
        if (selectedVaultItemIds.isNotEmpty()) {
            selectedVaultItemIds = emptySet()
        } else if (isSearchActive) {
            isSearchActive = false
            searchQuery = ""
        } else {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            if (isSearchActive && vaultState.isUnlocked && selectedVaultItemIds.isEmpty()) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                isSearchActive = false
                                searchQuery = ""
                            },
                            modifier = Modifier.testTag("vault_close_search_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Close Search",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    title = {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search vault files...") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("vault_search_text_field")
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            } else {
                TopAppBar(
                    title = {
                        if (selectedVaultItemIds.isNotEmpty()) {
                            Text(
                                text = "${selectedVaultItemIds.size} selected",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LockOpen,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Private Vault",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        if (selectedVaultItemIds.isNotEmpty()) {
                            IconButton(
                                onClick = { selectedVaultItemIds = emptySet() },
                                modifier = Modifier.testTag("vault_clear_selection_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear Selection",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        } else {
                            IconButton(
                                onClick = onNavigateBack,
                                modifier = Modifier.testTag("vault_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    },
                    actions = {
                        if (vaultState.isUnlocked) {
                            if (selectedVaultItemIds.isNotEmpty()) {
                                IconButton(
                                    onClick = { showUnlockSelectedDialog = true },
                                    modifier = Modifier.testTag("vault_batch_unlock_action")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LockOpen,
                                        contentDescription = "Unlock & Unhide Selected",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(
                                    onClick = { showDeleteSelectedDialog = true },
                                    modifier = Modifier.testTag("vault_batch_delete_action")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Selected",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            } else {
                                // Search Icon Button
                                IconButton(
                                    onClick = { isSearchActive = true },
                                    modifier = Modifier.testTag("vault_search_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Toggle Grid / List
                                IconButton(
                                    onClick = { isGridView = !isGridView },
                                    modifier = Modifier.testTag("vault_toggle_grid_button")
                                ) {
                                    Icon(
                                        imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                                        contentDescription = if (isGridView) "List View" else "Grid View",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Compact Unlock All Button moved up
                                if (vaultItems.isNotEmpty()) {
                                    IconButton(
                                        onClick = { showUnlockAllDialog = true },
                                        modifier = Modifier.testTag("vault_unlock_all_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LockOpen,
                                            contentDescription = "Unlock All Files",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                // Compact Add File Button moved up
                                IconButton(
                                    onClick = onNavigateToBrowseForEncryption,
                                    modifier = Modifier.testTag("vault_add_file_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add File",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                // 1. Initial Setup PIN
                !vaultState.isPinSetup -> {
                    VaultSetupContent(
                        pin = pinInput,
                        onPinChange = { pinInput = it },
                        confirmPin = confirmPinInput,
                        onConfirmPinChange = { confirmPinInput = it },
                        errorMessage = pinError ?: vaultState.error,
                        onSetupPin = {
                            if (pinInput.length < 4) {
                                pinError = "PIN must be at least 4 digits"
                            } else if (pinInput != confirmPinInput) {
                                pinError = "PINs do not match"
                            } else {
                                pinError = null
                                viewModel.setupVaultPin(pinInput)
                            }
                        }
                    )
                }

                // 2. Vault Locked - Enter PIN
                !vaultState.isUnlocked -> {
                    VaultUnlockContent(
                        pin = pinInput,
                        onPinChange = {
                            pinInput = it
                            pinError = null
                        },
                        errorMessage = pinError ?: vaultState.error,
                        onUnlock = {
                            if (pinInput.isBlank()) {
                                pinError = "Please enter your PIN"
                            } else {
                                val success = viewModel.unlockVault(pinInput)
                                if (success) {
                                    pinInput = ""
                                }
                            }
                        }
                    )
                }

                // 3. Vault Unlocked - Show Encrypted Files
                else -> {
                    VaultUnlockedDashboard(
                        items = vaultItems,
                        selectedCategory = selectedCategory,
                        onSelectCategory = { selectedCategory = it },
                        searchQuery = searchQuery,
                        isGridView = isGridView,
                        selectedItemIds = selectedVaultItemIds,
                        onToggleSelect = { id ->
                            selectedVaultItemIds = if (selectedVaultItemIds.contains(id)) {
                                selectedVaultItemIds - id
                            } else {
                                selectedVaultItemIds + id
                            }
                        },
                        onSelectAll = {
                            selectedVaultItemIds = vaultItems.map { it.id }.toSet()
                        },
                        onClearSelection = {
                            selectedVaultItemIds = emptySet()
                        },
                        onEncryptNew = onNavigateToBrowseForEncryption,
                        onPreview = { item ->
                            val pin = vaultState.unlockedPin ?: return@VaultUnlockedDashboard
                            val encFile = File(viewModel.repository.cryptoManager.getVaultDir(), item.vaultFileName)
                            val tempDir = File(context.cacheDir, "vault_preview")
                            tempDir.mkdirs()
                            val tempFile = File(tempDir, item.originalFileName)
                            val success = viewModel.repository.cryptoManager.decryptToFile(
                                encryptedFile = encFile,
                                destinationFile = tempFile,
                                pin = pin,
                                saltHex = item.saltHex,
                                ivHex = item.ivHex
                            )
                            if (success) {
                                onOpenFile(tempFile, item)
                            } else {
                                viewModel.emitMessage("Failed to decrypt for preview")
                            }
                        },
                        onUnlockItem = { item ->
                            selectedItemForUnlock = item
                            restoreToOriginalLocation = true
                        },
                        onExport = { item -> selectedItemForExport = item },
                        onDelete = { item -> selectedItemForDelete = item },
                        onProperties = { item -> selectedItemForProperties = item }
                    )
                }
            }
        }
    }

    // Single item Unlock / Unhide confirmation dialog
    selectedItemForUnlock?.let { item ->
        val origPathDisplay = File(item.originalPath).parent ?: "Original location"

        AlertDialog(
            onDismissRequest = { selectedItemForUnlock = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.LockOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "Unlock & Unhide File",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Decrypt \"${item.originalFileName}\" and unhide it back to storage?",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { restoreToOriginalLocation = true }
                            ) {
                                RadioButton(
                                    selected = restoreToOriginalLocation,
                                    onClick = { restoreToOriginalLocation = true },
                                    modifier = Modifier.testTag("vault_unlock_radio_original")
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Restore to Original Folder",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Text(
                                        text = origPathDisplay,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { restoreToOriginalLocation = false }
                            ) {
                                RadioButton(
                                    selected = !restoreToOriginalLocation,
                                    onClick = { restoreToOriginalLocation = false },
                                    modifier = Modifier.testTag("vault_unlock_radio_downloads")
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Restore to Downloads Folder",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Text(
                                        text = "Internal Storage / Download",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.unlockAndRestoreFromVault(
                            vaultItem = item,
                            restoreToOriginal = restoreToOriginalLocation
                        )
                        selectedItemForUnlock = null
                    },
                    modifier = Modifier.testTag("vault_confirm_unlock_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.LockOpen,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Unlock & Unhide")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedItemForUnlock = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Unlock All confirmation dialog
    if (showUnlockAllDialog) {
        AlertDialog(
            onDismissRequest = { showUnlockAllDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.LockOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = { Text("Unlock & Unhide All Files") },
            text = {
                Text("Are you sure you want to unlock and decrypt all ${vaultItems.size} files back to their original storage locations?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.unlockAndRestoreBatch(vaultItems, restoreToOriginal = true)
                        showUnlockAllDialog = false
                    },
                    modifier = Modifier.testTag("vault_confirm_unlock_all_button")
                ) {
                    Text("Unlock & Unhide All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnlockAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Unlock Selected confirmation dialog
    if (showUnlockSelectedDialog) {
        val selectedEntities = vaultItems.filter { selectedVaultItemIds.contains(it.id) }
        AlertDialog(
            onDismissRequest = { showUnlockSelectedDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.LockOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = { Text("Unlock & Unhide Selected") },
            text = {
                Text("Unlock and restore ${selectedEntities.size} selected file(s) back to their storage locations?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.unlockAndRestoreBatch(selectedEntities, restoreToOriginal = true)
                        selectedVaultItemIds = emptySet()
                        showUnlockSelectedDialog = false
                    },
                    modifier = Modifier.testTag("vault_confirm_unlock_selected_button")
                ) {
                    Text("Unlock Selected (${selectedEntities.size})")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnlockSelectedDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Selected confirmation dialog
    if (showDeleteSelectedDialog) {
        val selectedEntities = vaultItems.filter { selectedVaultItemIds.contains(it.id) }
        AlertDialog(
            onDismissRequest = { showDeleteSelectedDialog = false },
            title = { Text("Delete Selected Encrypted Files?") },
            text = {
                Text("Are you sure you want to permanently delete ${selectedEntities.size} file(s) from your vault? This cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedEntities.forEach { viewModel.deleteFromVault(it) }
                        selectedVaultItemIds = emptySet()
                        showDeleteSelectedDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("vault_confirm_delete_selected_button")
                ) {
                    Text("Delete (${selectedEntities.size})")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectedDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Export confirmation
    selectedItemForExport?.let { item ->
        AlertDialog(
            onDismissRequest = { selectedItemForExport = null },
            title = { Text("Export & Decrypt File") },
            text = {
                Text("Decrypt \"${item.originalFileName}\" and export it back to Internal Storage / Download folder?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val downloadDir = File(viewModel.repository.getInternalStorageRoot(), "Download")
                        viewModel.exportFromVault(item, downloadDir)
                        selectedItemForExport = null
                    }
                ) {
                    Text("Export & Decrypt")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedItemForExport = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete confirmation
    selectedItemForDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { selectedItemForDelete = null },
            title = { Text("Delete Encrypted File?") },
            text = {
                Text("Are you sure you want to permanently delete \"${item.originalFileName}\" from your encrypted vault?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteFromVault(item)
                        selectedItemForDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedItemForDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Properties dialog
    selectedItemForProperties?.let { item ->
        val propDateFormat = remember { SimpleDateFormat("MMM d, yyyy  HH:mm", Locale.getDefault()) }
        AlertDialog(
            onDismissRequest = { selectedItemForProperties = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = { Text("File Properties") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column {
                        Text("File Name", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(item.originalFileName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                    }
                    Column {
                        Text("Original Path", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(item.originalPath, style = MaterialTheme.typography.bodySmall)
                    }
                    Column {
                        Text("Size", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(FileItem.formatFileSize(item.originalSize), style = MaterialTheme.typography.bodyMedium)
                    }
                    Column {
                        Text("Encrypted On", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(propDateFormat.format(Date(item.encryptedAt)), style = MaterialTheme.typography.bodyMedium)
                    }
                    Column {
                        Text("Protection", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("AES-256-GCM Zero-Knowledge Encryption", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedItemForProperties = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun VaultUnlockedDashboard(
    items: List<VaultEntity>,
    selectedCategory: VaultCategory,
    onSelectCategory: (VaultCategory) -> Unit,
    searchQuery: String,
    isGridView: Boolean,
    selectedItemIds: Set<Long>,
    onToggleSelect: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onEncryptNew: () -> Unit,
    onPreview: (VaultEntity) -> Unit,
    onUnlockItem: (VaultEntity) -> Unit,
    onExport: (VaultEntity) -> Unit,
    onDelete: (VaultEntity) -> Unit,
    onProperties: (VaultEntity) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    val filteredByCategory = remember(items, selectedCategory) {
        items.filter { it.matchesCategory(selectedCategory) }
    }

    val displayedItems = remember(filteredByCategory, searchQuery) {
        if (searchQuery.isBlank()) {
            filteredByCategory
        } else {
            val q = searchQuery.trim()
            filteredByCategory.filter { it.originalFileName.contains(q, ignoreCase = true) }
        }
    }

    val totalCategorySize = remember(displayedItems) {
        displayedItems.sumOf { it.originalSize }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Categories Filter Chips Row (Images, Videos, PDFs, Docs, All Files)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VaultCategory.values().forEach { cat ->
                val count = remember(items, cat) {
                    items.count { it.matchesCategory(cat) }
                }
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { onSelectCategory(cat) },
                    label = {
                        Text(
                            text = if (count > 0) "${cat.displayName} ($count)" else cat.displayName,
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = cat.icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.testTag("vault_filter_chip_${cat.name.lowercase()}")
                )
            }
        }

        // Subtitle & Multi-Select Summary Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (selectedItemIds.isNotEmpty()) {
                    "${selectedItemIds.size} of ${items.size} selected"
                } else {
                    "${displayedItems.size} files • ${FileItem.formatFileSize(totalCategorySize)}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (selectedItemIds.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = onSelectAll) {
                        Text("Select All", style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = onClearSelection) {
                        Text("Clear", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // Content Area (Grid or List view)
        if (displayedItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = selectedCategory.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (searchQuery.isNotEmpty()) {
                            "No files match \"$searchQuery\""
                        } else if (items.isEmpty()) {
                            "Your encrypted vault is empty"
                        } else {
                            "No ${selectedCategory.displayName.lowercase()} in vault"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap '+' in top bar to encrypt and add files",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else if (isGridView) {
            // Photos/Videos/Files Grid View exactly like normal files section
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 105.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(displayedItems, key = { it.id }) { item ->
                    VaultGridItem(
                        item = item,
                        onClick = {
                            if (selectedItemIds.isNotEmpty()) {
                                onToggleSelect(item.id)
                            } else {
                                onPreview(item)
                            }
                        },
                        onLongClick = { onToggleSelect(item.id) },
                        onPreview = { onPreview(item) },
                        onUnlock = { onUnlockItem(item) },
                        onExport = { onExport(item) },
                        onDelete = { onDelete(item) },
                        onProperties = { onProperties(item) },
                        isSelected = selectedItemIds.contains(item.id),
                        isSelectionMode = selectedItemIds.isNotEmpty()
                    )
                }
            }
        } else {
            // List View exactly like normal files section
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                items(displayedItems, key = { it.id }) { item ->
                    VaultListItem(
                        item = item,
                        dateFormat = dateFormat,
                        onClick = {
                            if (selectedItemIds.isNotEmpty()) {
                                onToggleSelect(item.id)
                            } else {
                                onPreview(item)
                            }
                        },
                        onLongClick = { onToggleSelect(item.id) },
                        onPreview = { onPreview(item) },
                        onUnlock = { onUnlockItem(item) },
                        onExport = { onExport(item) },
                        onDelete = { onDelete(item) },
                        onProperties = { onProperties(item) },
                        isSelected = selectedItemIds.contains(item.id),
                        isSelectionMode = selectedItemIds.isNotEmpty()
                    )
                }
            }
        }
    }
}

@Composable
fun VaultItemDropdownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onPreview: () -> Unit,
    onUnlock: () -> Unit,
    onExport: () -> Unit,
    onProperties: () -> Unit,
    onDelete: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        DropdownMenuItem(
            text = { Text("Unlock & Unhide") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.LockOpen,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            onClick = {
                onDismiss()
                onUnlock()
            }
        )

        DropdownMenuItem(
            text = { Text("View") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            },
            onClick = {
                onDismiss()
                onPreview()
            }
        )

        DropdownMenuItem(
            text = { Text("Export & Decrypt") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.FileDownload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            },
            onClick = {
                onDismiss()
                onExport()
            }
        )

        DropdownMenuItem(
            text = { Text("Properties") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            },
            onClick = {
                onDismiss()
                onProperties()
            }
        )

        DropdownMenuItem(
            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            },
            onClick = {
                onDismiss()
                onDelete()
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VaultListItem(
    item: VaultEntity,
    dateFormat: SimpleDateFormat,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPreview: () -> Unit,
    onUnlock: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onProperties: () -> Unit,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("vault_item_${item.originalFileName.replace(" ", "_")}")
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }

            // Thumbnail / Icon with small unhide/unlock badge (LockOpen)
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                val (typeIcon, typeTint) = getVaultTypeIcon(item)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = typeIcon,
                            contentDescription = null,
                            tint = typeTint,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Unhide/unlock badge
                Surface(
                    shape = RoundedCornerShape(3.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .size(14.dp)
                        .align(Alignment.BottomEnd)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.LockOpen,
                            contentDescription = "Locked Item",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // File Name & Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.originalFileName,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = FileItem.formatFileSize(item.originalSize),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = dateFormat.format(Date(item.encryptedAt)),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 3 Dots Menu Button
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("vault_options_${item.originalFileName.replace(" ", "_")}")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                VaultItemDropdownMenu(
                    expanded = menuExpanded,
                    onDismiss = { menuExpanded = false },
                    onPreview = onPreview,
                    onUnlock = onUnlock,
                    onExport = onExport,
                    onProperties = onProperties,
                    onDelete = onDelete
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VaultGridItem(
    item: VaultEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPreview: () -> Unit,
    onUnlock: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onProperties: () -> Unit,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("vault_grid_item_${item.originalFileName.replace(" ", "_")}")
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            if (isSelected) 2.dp else 1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(contentAlignment = Alignment.Center) {
                    val (typeIcon, typeTint) = getVaultTypeIcon(item)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = typeIcon,
                                contentDescription = null,
                                tint = typeTint,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    // Unlock / unhide badge (LockOpen) on thumbnail
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .size(16.dp)
                            .align(Alignment.BottomEnd)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.LockOpen,
                                contentDescription = "Locked Item",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }

                    if (isSelectionMode) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onClick() },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = item.originalFileName,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = FileItem.formatFileSize(item.originalSize),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 3 Dots Menu Button at Top-End of the Grid Box
            if (!isSelectionMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                ) {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("vault_options_grid_${item.originalFileName.replace(" ", "_")}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    VaultItemDropdownMenu(
                        expanded = menuExpanded,
                        onDismiss = { menuExpanded = false },
                        onPreview = onPreview,
                        onUnlock = onUnlock,
                        onExport = onExport,
                        onProperties = onProperties,
                        onDelete = onDelete
                    )
                }
            }
        }
    }
}

@Composable
private fun VaultSetupContent(
    pin: String,
    onPinChange: (String) -> Unit,
    confirmPin: String,
    onConfirmPinChange: (String) -> Unit,
    errorMessage: String?,
    onSetupPin: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Create Vault PIN",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Set a 4-digit PIN for your zero-knowledge encrypted vault. All files are encrypted using AES-256-GCM and cannot be recovered without this PIN.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = onPinChange,
            label = { Text("Enter 4-digit PIN") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("vault_setup_pin_field")
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = confirmPin,
            onValueChange = onConfirmPinChange,
            label = { Text("Confirm PIN") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("vault_setup_confirm_pin_field")
        )

        errorMessage?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onSetupPin,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("vault_setup_button")
        ) {
            Text("Set PIN & Initialize Vault")
        }
    }
}

@Composable
private fun VaultUnlockContent(
    pin: String,
    onPinChange: (String) -> Unit,
    errorMessage: String?,
    onUnlock: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Vault Locked",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Enter your secret PIN to access encrypted documents",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = onPinChange,
            label = { Text("Vault PIN") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("vault_unlock_pin_field")
        )

        errorMessage?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onUnlock,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("vault_unlock_button")
        ) {
            Text("Unlock Vault")
        }
    }
}
