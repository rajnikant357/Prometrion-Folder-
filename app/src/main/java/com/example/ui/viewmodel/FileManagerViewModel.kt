package com.example.ui.viewmodel

import android.app.Application
import android.os.Build
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.FavoriteEntity
import com.example.data.local.RecentEntity
import com.example.data.local.TrashEntity
import com.example.data.local.VaultEntity
import com.example.data.model.BatchClipboard
import com.example.data.model.DuplicateGroup
import com.example.data.model.FileCategory
import com.example.data.model.FileItem
import com.example.data.model.FileOperationProgress
import com.example.data.model.FileOperationStatus
import com.example.data.model.FileOperationType
import com.example.data.model.SortBy
import com.example.data.model.SortOption
import com.example.data.model.SortOrder
import com.example.data.model.StorageInfo
import com.example.data.repository.FileManagerRepository
import com.example.data.security.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class VaultState(
    val isPinSetup: Boolean = false,
    val isUnlocked: Boolean = false,
    val unlockedPin: String? = null,
    val items: List<VaultEntity> = emptyList(),
    val error: String? = null
)

data class StorageAnalyzerState(
    val isLoading: Boolean = false,
    val largeFiles: List<FileItem> = emptyList(),
    val junkFiles: List<FileItem> = emptyList(),
    val totalJunkSize: Long = 0L,
    val cleanedMessage: String? = null
)

enum class AppThemeMode {
    SYSTEM,
    DARK,
    LIGHT
}

class FileManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val cryptoManager = CryptoManager(application)
    val repository = FileManagerRepository(application, database, cryptoManager)

    private fun isPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    // Permissions
    private val _hasStoragePermission = MutableStateFlow(isPermissionGranted())
    val hasStoragePermission: StateFlow<Boolean> = _hasStoragePermission.asStateFlow()

    // Theme
    private val _themeMode = MutableStateFlow(AppThemeMode.DARK)
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    // Storage volumes
    private val _storageVolumes = MutableStateFlow<List<StorageInfo>>(emptyList())
    val storageVolumes: StateFlow<List<StorageInfo>> = _storageVolumes.asStateFlow()

    // Current Browsing Directory
    private val _currentDirectory = MutableStateFlow(repository.getInternalStorageRoot())
    val currentDirectory: StateFlow<File> = _currentDirectory.asStateFlow()

    private val _directoryItems = MutableStateFlow<List<FileItem>>(emptyList())
    val directoryItems: StateFlow<List<FileItem>> = _directoryItems.asStateFlow()

    private val _isLoadingDirectory = MutableStateFlow(false)
    val isLoadingDirectory: StateFlow<Boolean> = _isLoadingDirectory.asStateFlow()

    // Sort & View Options
    private val _sortOption = MutableStateFlow(SortOption(SortBy.NAME, SortOrder.ASCENDING))
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    private val _isGridView = MutableStateFlow(false)
    val isGridView: StateFlow<Boolean> = _isGridView.asStateFlow()

    // Category Screen
    private val _selectedCategory = MutableStateFlow<FileCategory?>(null)
    val selectedCategory: StateFlow<FileCategory?> = _selectedCategory.asStateFlow()

    private val _categoryItems = MutableStateFlow<List<FileItem>>(emptyList())
    val categoryItems: StateFlow<List<FileItem>> = _categoryItems.asStateFlow()

    private val _isLoadingCategory = MutableStateFlow(false)
    val isLoadingCategory: StateFlow<Boolean> = _isLoadingCategory.asStateFlow()

    // Search
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchCategoryFilter = MutableStateFlow<FileCategory?>(null)
    val searchCategoryFilter: StateFlow<FileCategory?> = _searchCategoryFilter.asStateFlow()

    private val _searchResults = MutableStateFlow<List<FileItem>>(emptyList())
    val searchResults: StateFlow<List<FileItem>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    // Vault
    private val _vaultState = MutableStateFlow(
        VaultState(
            isPinSetup = cryptoManager.isVaultInitialized(),
            isUnlocked = false
        )
    )
    val vaultState: StateFlow<VaultState> = _vaultState.asStateFlow()

    val vaultDbItems: StateFlow<List<VaultEntity>> = repository.vaultItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favorites: StateFlow<List<FavoriteEntity>> = repository.favoriteItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentFiles: StateFlow<List<RecentEntity>> = repository.recentItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trashItems: StateFlow<List<TrashEntity>> = repository.trashItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Hidden files toggle
    private val _showHiddenFiles = MutableStateFlow(false)
    val showHiddenFiles: StateFlow<Boolean> = _showHiddenFiles.asStateFlow()

    // Multi-Selection Mode
    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    private val _selectedFiles = MutableStateFlow<Set<File>>(emptySet())
    val selectedFiles: StateFlow<Set<File>> = _selectedFiles.asStateFlow()

    // Batch Clipboard (Copy / Move)
    private val _batchClipboard = MutableStateFlow<BatchClipboard?>(null)
    val batchClipboard: StateFlow<BatchClipboard?> = _batchClipboard.asStateFlow()

    // Active File Operation Progress
    private val _currentOperation = MutableStateFlow<FileOperationProgress?>(null)
    val currentOperation: StateFlow<FileOperationProgress?> = _currentOperation.asStateFlow()

    // Navigation History (Back & Forward Stacks)
    private val _backStack = mutableListOf<File>()
    private val _forwardStack = mutableListOf<File>()
    private val _canGoForward = MutableStateFlow(false)
    val canGoForward: StateFlow<Boolean> = _canGoForward.asStateFlow()

    // Duplicate File Groups
    private val _duplicateGroups = MutableStateFlow<List<DuplicateGroup>>(emptyList())
    val duplicateGroups: StateFlow<List<DuplicateGroup>> = _duplicateGroups.asStateFlow()

    private val _isScanningDuplicates = MutableStateFlow(false)
    val isScanningDuplicates: StateFlow<Boolean> = _isScanningDuplicates.asStateFlow()

    // Storage Analyzer
    private val _analyzerState = MutableStateFlow(StorageAnalyzerState())
    val analyzerState: StateFlow<StorageAnalyzerState> = _analyzerState.asStateFlow()

    // UI Feedback messages
    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    // Clipboard for Copy / Cut (legacy single)
    private val _clipboardFile = MutableStateFlow<Pair<File, Boolean>?>(null) // File, isCut
    val clipboardFile: StateFlow<Pair<File, Boolean>?> = _clipboardFile.asStateFlow()


    init {
        refreshStorageVolumes()
        refreshDirectory()
        checkAndInitSamples()
    }

    fun setThemeMode(mode: AppThemeMode) {
        _themeMode.value = mode
    }

    fun toggleThemeMode() {
        _themeMode.value = when (_themeMode.value) {
            AppThemeMode.DARK -> AppThemeMode.LIGHT
            AppThemeMode.LIGHT -> AppThemeMode.SYSTEM
            AppThemeMode.SYSTEM -> AppThemeMode.DARK
        }
    }

    fun checkStoragePermission(): Boolean {
        val granted = isPermissionGranted()
        _hasStoragePermission.value = granted
        return granted
    }

    fun updatePermissionStatus(granted: Boolean) {
        _hasStoragePermission.value = granted
        if (granted) {
            refreshStorageVolumes()
            refreshDirectory()
        }
    }

    private fun checkAndInitSamples() {
        viewModelScope.launch {
            repository.initializeSampleFilesIfEmpty()
            refreshDirectory()
        }
    }

    fun refreshStorageVolumes() {
        viewModelScope.launch {
            val volumes = repository.getStorageVolumes()
            _storageVolumes.value = volumes
        }
    }

    fun navigateToDirectory(directory: File, addToHistory: Boolean = true) {
        if (directory.absolutePath == _currentDirectory.value.absolutePath) return
        if (addToHistory) {
            _backStack.add(_currentDirectory.value)
            _forwardStack.clear()
            _canGoForward.value = false
        }
        _currentDirectory.value = directory
        clearSelection()
        refreshDirectory()
    }

    fun navigateToParent(): Boolean {
        if (_isSelectionMode.value) {
            clearSelection()
            return true
        }
        val current = _currentDirectory.value
        val parent = current.parentFile
        val root = repository.getInternalStorageRoot().parentFile
        if (parent != null && parent.exists() && parent != root?.parentFile) {
            _forwardStack.add(current)
            _canGoForward.value = true
            _currentDirectory.value = parent
            clearSelection()
            refreshDirectory()
            return true
        }
        return false
    }

    fun navigateForward(): Boolean {
        if (_forwardStack.isNotEmpty()) {
            val next = _forwardStack.removeLast()
            _canGoForward.value = _forwardStack.isNotEmpty()
            _backStack.add(_currentDirectory.value)
            _currentDirectory.value = next
            clearSelection()
            refreshDirectory()
            return true
        }
        return false
    }

    fun toggleShowHiddenFiles() {
        _showHiddenFiles.value = !_showHiddenFiles.value
        emitMessage(if (_showHiddenFiles.value) "Showing hidden files" else "Hidden files hidden")
        refreshDirectory()
    }

    fun refreshDirectory() {
        viewModelScope.launch {
            _isLoadingDirectory.value = true
            val items = repository.getDirectoryContents(
                _currentDirectory.value,
                _sortOption.value,
                showHidden = _showHiddenFiles.value
            )
            _directoryItems.value = items
            _isLoadingDirectory.value = false
        }
    }

    fun setSortOption(sortBy: SortBy) {
        val current = _sortOption.value
        val newOrder = if (current.sortBy == sortBy) {
            if (current.sortOrder == SortOrder.ASCENDING) SortOrder.DESCENDING else SortOrder.ASCENDING
        } else {
            SortOrder.ASCENDING
        }
        _sortOption.value = SortOption(sortBy, newOrder)
        refreshDirectory()
    }

    fun toggleGridView() {
        _isGridView.value = !_isGridView.value
    }


    fun selectCategory(category: FileCategory?) {
        _selectedCategory.value = category
        if (category != null) {
            loadCategoryFiles(category)
        }
    }

    fun loadCategoryFiles(category: FileCategory) {
        viewModelScope.launch {
            _isLoadingCategory.value = true
            val items = repository.getFilesForCategory(category)
            _categoryItems.value = items
            _isLoadingCategory.value = false
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        executeSearch(query, _searchCategoryFilter.value)
    }

    fun onSearchCategoryFilterChanged(category: FileCategory?) {
        _searchCategoryFilter.value = category
        executeSearch(_searchQuery.value, category)
    }

    private fun executeSearch(query: String, category: FileCategory?) {
        if (query.isBlank() && category == null) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _isSearching.value = true
            val results = repository.searchFiles(
                query = query,
                categoryFilter = category,
                showHidden = _showHiddenFiles.value
            )
            _searchResults.value = results
            _isSearching.value = false
        }

    }

    // Vault Operations
    fun setupVaultPin(pin: String): Boolean {
        val success = cryptoManager.setupPin(pin)
        if (success) {
            _vaultState.update {
                it.copy(
                    isPinSetup = true,
                    isUnlocked = true,
                    unlockedPin = pin,
                    error = null
                )
            }
            emitMessage("Encrypted Vault setup successfully")
        } else {
            _vaultState.update { it.copy(error = "PIN must be at least 4 digits") }
        }
        return success
    }

    fun unlockVault(pin: String): Boolean {
        val verified = cryptoManager.verifyPin(pin)
        if (verified) {
            _vaultState.update {
                it.copy(
                    isUnlocked = true,
                    unlockedPin = pin,
                    error = null
                )
            }
            emitMessage("Vault unlocked")
        } else {
            _vaultState.update { it.copy(error = "Incorrect PIN. Access denied.") }
        }
        return verified
    }

    fun lockVault() {
        _vaultState.update {
            it.copy(
                isUnlocked = false,
                unlockedPin = null,
                error = null
            )
        }
        emitMessage("Vault locked")
    }

    fun moveFileToVault(file: File, deleteOriginal: Boolean = true) {
        val pin = _vaultState.value.unlockedPin
        if (pin == null) {
            emitMessage("Unlock vault to encrypt files")
            return
        }
        viewModelScope.launch {
            val success = repository.moveToVault(file, pin, deleteOriginal)
            if (success) {
                emitMessage("File securely encrypted in Vault")
                refreshDirectory()
                _selectedCategory.value?.let { loadCategoryFiles(it) }
            } else {
                emitMessage("Failed to encrypt file")
            }
        }
    }

    fun exportFromVault(vaultItem: VaultEntity, destinationDir: File) {
        val pin = _vaultState.value.unlockedPin
        if (pin == null) {
            emitMessage("Unlock vault to export files")
            return
        }
        viewModelScope.launch {
            val success = repository.exportFromVault(vaultItem, pin, destinationDir)
            if (success) {
                emitMessage("File decrypted and exported to ${destinationDir.name}")
                refreshDirectory()
            } else {
                emitMessage("Failed to decrypt file")
            }
        }
    }

    fun deleteFromVault(vaultItem: VaultEntity) {
        viewModelScope.launch {
            repository.deleteVaultItem(vaultItem)
            emitMessage("Deleted permanently from Vault")
        }
    }

    // Standard & Batch File Operations
    fun toggleFileSelection(file: File) {
        val current = _selectedFiles.value.toMutableSet()
        if (current.contains(file)) {
            current.remove(file)
            if (current.isEmpty()) {
                _isSelectionMode.value = false
            }
        } else {
            current.add(file)
            _isSelectionMode.value = true
        }
        _selectedFiles.value = current
    }

    fun selectAll() {
        val files = _directoryItems.value.map { it.file }.toSet()
        _selectedFiles.value = files
        _isSelectionMode.value = files.isNotEmpty()
    }

    fun deselectAll() {
        _selectedFiles.value = emptySet()
        _isSelectionMode.value = false
    }

    fun clearSelection() {
        _selectedFiles.value = emptySet()
        _isSelectionMode.value = false
    }

    fun copySelectedFiles() {
        val files = _selectedFiles.value.toList()
        if (files.isEmpty()) return
        _batchClipboard.value = BatchClipboard(files, isCut = false)
        clearSelection()
        emitMessage("Copied ${files.size} items to clipboard")
    }

    fun cutSelectedFiles() {
        val files = _selectedFiles.value.toList()
        if (files.isEmpty()) return
        _batchClipboard.value = BatchClipboard(files, isCut = true)
        clearSelection()
        emitMessage("Cut ${files.size} items to clipboard")
    }

    fun clearBatchClipboard() {
        _batchClipboard.value = null
    }

    fun pasteBatchClipboard(targetDir: File = _currentDirectory.value) {
        val clip = _batchClipboard.value ?: return
        val isCut = clip.isCut
        val sources = clip.files
        val totalFiles = sources.size
        val operationId = System.currentTimeMillis().toString()

        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            var lastTime = startTime
            var lastBytes = 0L

            _currentOperation.value = FileOperationProgress(
                operationId = operationId,
                title = if (isCut) "Moving $totalFiles files..." else "Copying $totalFiles files...",
                type = if (isCut) FileOperationType.MOVE else FileOperationType.COPY,
                totalFiles = totalFiles,
                processedFiles = 0,
                totalBytes = clip.totalSize,
                processedBytes = 0L,
                status = FileOperationStatus.RUNNING
            )

            val success = if (isCut) {
                repository.moveFilesBatch(sources, targetDir) { pFiles, tFiles, pBytes, tBytes, currentName ->
                    val now = System.currentTimeMillis()
                    val timeDeltaSec = (now - lastTime) / 1000.0
                    val speed = if (timeDeltaSec > 0.5) {
                        val bytesDelta = pBytes - lastBytes
                        lastTime = now
                        lastBytes = pBytes
                        (bytesDelta / timeDeltaSec).toLong()
                    } else _currentOperation.value?.speedBytesPerSec ?: 0L

                    val remainingBytes = (tBytes - pBytes).coerceAtLeast(0L)
                    val eta = if (speed > 0) remainingBytes / speed else -1L

                    _currentOperation.value = FileOperationProgress(
                        operationId = operationId,
                        title = "Moving $tFiles files to ${targetDir.name}...",
                        type = FileOperationType.MOVE,
                        totalFiles = tFiles,
                        processedFiles = pFiles,
                        totalBytes = tBytes,
                        processedBytes = pBytes,
                        speedBytesPerSec = speed,
                        estimatedRemainingSeconds = eta,
                        currentFileName = currentName,
                        status = FileOperationStatus.RUNNING
                    )
                }
            } else {
                repository.copyFilesBatch(sources, targetDir) { pFiles, tFiles, pBytes, tBytes, currentName ->
                    val now = System.currentTimeMillis()
                    val timeDeltaSec = (now - lastTime) / 1000.0
                    val speed = if (timeDeltaSec > 0.5) {
                        val bytesDelta = pBytes - lastBytes
                        lastTime = now
                        lastBytes = pBytes
                        (bytesDelta / timeDeltaSec).toLong()
                    } else _currentOperation.value?.speedBytesPerSec ?: 0L

                    val remainingBytes = (tBytes - pBytes).coerceAtLeast(0L)
                    val eta = if (speed > 0) remainingBytes / speed else -1L

                    _currentOperation.value = FileOperationProgress(
                        operationId = operationId,
                        title = "Copying $tFiles files to ${targetDir.name}...",
                        type = FileOperationType.COPY,
                        totalFiles = tFiles,
                        processedFiles = pFiles,
                        totalBytes = tBytes,
                        processedBytes = pBytes,
                        speedBytesPerSec = speed,
                        estimatedRemainingSeconds = eta,
                        currentFileName = currentName,
                        status = FileOperationStatus.RUNNING
                    )
                }
            }

            if (success) {
                _currentOperation.value = _currentOperation.value?.copy(
                    status = FileOperationStatus.COMPLETED,
                    processedFiles = totalFiles,
                    processedBytes = clip.totalSize
                )
                emitMessage(if (isCut) "Moved $totalFiles files to ${targetDir.name}" else "Copied $totalFiles files to ${targetDir.name}")
                if (isCut) {
                    _batchClipboard.value = null
                }
            } else {
                _currentOperation.value = _currentOperation.value?.copy(
                    status = FileOperationStatus.FAILED,
                    errorMessage = "Error during transfer"
                )
                emitMessage("Failed to transfer files")
            }

            kotlinx.coroutines.delay(1000)
            _currentOperation.value = null
            refreshDirectory()
            refreshStorageVolumes()
        }
    }

    fun dismissCurrentOperation() {
        _currentOperation.value = null
    }

    // Trash Operations
    fun moveSelectedToTrash() {
        val files = _selectedFiles.value.toList()
        if (files.isEmpty()) return
        viewModelScope.launch {
            var moved = 0
            for (f in files) {
                if (repository.moveToTrash(f)) moved++
            }
            clearSelection()
            emitMessage("Moved $moved items to Trash")
            refreshDirectory()
            refreshStorageVolumes()
        }
    }

    fun moveFileToTrash(file: File) {
        viewModelScope.launch {
            val success = repository.moveToTrash(file)
            if (success) {
                emitMessage("Moved to Trash: ${file.name}")
                refreshDirectory()
                refreshStorageVolumes()
            } else {
                emitMessage("Failed to move to Trash")
            }
        }
    }

    fun restoreTrashItem(item: TrashEntity) {
        viewModelScope.launch {
            val success = repository.restoreFromTrash(item)
            if (success) {
                emitMessage("Restored: ${item.originalFileName}")
                refreshDirectory()
                refreshStorageVolumes()
            } else {
                emitMessage("Failed to restore item")
            }
        }
    }

    fun deleteTrashItemPermanently(item: TrashEntity) {
        viewModelScope.launch {
            val success = repository.deletePermanentlyFromTrash(item)
            if (success) {
                emitMessage("Permanently deleted: ${item.originalFileName}")
            } else {
                emitMessage("Failed to delete item")
            }
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            val success = repository.emptyTrash()
            if (success) {
                emitMessage("Trash emptied successfully")
                refreshStorageVolumes()
            } else {
                emitMessage("Failed to empty Trash")
            }
        }
    }

    // Duplicate File Detection
    fun scanForDuplicates() {
        viewModelScope.launch {
            _isScanningDuplicates.value = true
            val duplicates = repository.findDuplicateFiles()
            _duplicateGroups.value = duplicates
            _isScanningDuplicates.value = false
            emitMessage("Scan completed: Found ${duplicates.size} duplicate groups")
        }
    }

    fun deleteDuplicateFile(file: File) {
        viewModelScope.launch {
            val success = repository.moveToTrash(file)
            if (success) {
                emitMessage("Duplicate moved to Trash: ${file.name}")
                val updated = _duplicateGroups.value.mapNotNull { group ->
                    val remaining = group.files.filter { it.absolutePath != file.absolutePath }
                    if (remaining.size >= 2) group.copy(files = remaining) else null
                }
                _duplicateGroups.value = updated
                refreshDirectory()
                refreshStorageVolumes()
            } else {
                emitMessage("Failed to delete duplicate file")
            }
        }
    }

    // Creation operations (Folder, Text, Markdown, JSON, CSV)
    fun createNewFile(name: String, fileType: String, content: String = "") {
        viewModelScope.launch {
            val trimmedName = name.trim()
            val fullName = when {
                trimmedName.contains(".") -> trimmedName
                fileType.equals("markdown", ignoreCase = true) || fileType.equals("md", ignoreCase = true) -> "$trimmedName.md"
                fileType.equals("json", ignoreCase = true) -> "$trimmedName.json"
                fileType.equals("csv", ignoreCase = true) -> "$trimmedName.csv"
                fileType.equals("folder", ignoreCase = true) -> trimmedName
                else -> "$trimmedName.txt"
            }
            val success = if (fileType.equals("folder", ignoreCase = true)) {
                repository.createFolder(_currentDirectory.value, fullName)
            } else {
                repository.createFile(_currentDirectory.value, fullName, content)
            }
            if (success) {
                emitMessage("Created: $fullName")
                refreshDirectory()
            } else {
                emitMessage("Failed to create $fullName (name already exists or invalid)")
            }
        }
    }

    fun createNewFolder(name: String) {
        createNewFile(name, "folder")
    }

    fun renameFile(file: File, newName: String) {
        viewModelScope.launch {
            val success = repository.renameFile(file, newName)
            if (success) {
                emitMessage("Renamed to $newName")
                refreshDirectory()
                _selectedCategory.value?.let { loadCategoryFiles(it) }
            } else {
                emitMessage("Failed to rename file")
            }
        }
    }

    fun deleteFile(file: File) {
        moveFileToTrash(file)
    }

    fun setClipboard(file: File, isCut: Boolean) {
        _clipboardFile.value = Pair(file, isCut)
        _batchClipboard.value = BatchClipboard(listOf(file), isCut)
        emitMessage(if (isCut) "Cut: ${file.name}" else "Copied: ${file.name}")
    }

    fun pasteClipboard(targetDir: File = _currentDirectory.value) {
        pasteBatchClipboard(targetDir)
    }


    fun compressFiles(files: List<File>, zipName: String) {
        viewModelScope.launch {
            val targetZip = File(_currentDirectory.value, if (zipName.endsWith(".zip")) zipName else "$zipName.zip")
            val success = repository.compressToZip(files, targetZip)
            if (success) {
                emitMessage("Compressed archive created: ${targetZip.name}")
                refreshDirectory()
            } else {
                emitMessage("Failed to compress files")
            }
        }
    }

    fun extractArchive(zipFile: File) {
        viewModelScope.launch {
            val destFolder = File(zipFile.parentFile, zipFile.nameWithoutExtension)
            val success = repository.extractZip(zipFile, destFolder)
            if (success) {
                emitMessage("Extracted to: ${destFolder.name}")
                refreshDirectory()
            } else {
                emitMessage("Failed to extract archive")
            }
        }
    }

    fun recordFileAccess(file: File) {
        viewModelScope.launch {
            repository.recordRecent(file)
        }
    }

    fun toggleFavorite(file: File) {
        viewModelScope.launch {
            repository.toggleFavorite(file)
            emitMessage("Added to favorites: ${file.name}")
        }
    }

    // Storage Analyzer
    fun runStorageAnalyzer() {
        viewModelScope.launch {
            _analyzerState.update { it.copy(isLoading = true, cleanedMessage = null) }
            val large = repository.findLargeFiles()
            val junk = repository.findJunkFiles()
            val totalJunk = junk.sumOf { it.size }
            _analyzerState.update {
                it.copy(
                    isLoading = false,
                    largeFiles = large,
                    junkFiles = junk,
                    totalJunkSize = totalJunk
                )
            }
        }
    }

    fun cleanJunkFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            val junk = _analyzerState.value.junkFiles
            var cleanedCount = 0
            for (item in junk) {
                if (repository.deleteFile(item.file)) {
                    cleanedCount++
                }
            }
            val msg = "Cleaned $cleanedCount junk files and freed space!"
            _analyzerState.update {
                it.copy(
                    junkFiles = emptyList(),
                    totalJunkSize = 0L,
                    cleanedMessage = msg
                )
            }
            emitMessage(msg)
            refreshStorageVolumes()
            refreshDirectory()
        }
    }

    fun emitMessage(msg: String) {
        viewModelScope.launch {
            _snackbarMessage.emit(msg)
        }
    }
}
