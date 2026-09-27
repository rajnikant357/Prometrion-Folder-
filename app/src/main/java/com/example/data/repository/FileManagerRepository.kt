package com.example.data.repository

import android.content.Context
import android.os.Environment
import android.os.StatFs
import com.example.data.local.AppDatabase
import com.example.data.local.FavoriteEntity
import com.example.data.local.RecentEntity
import com.example.data.local.TrashEntity
import com.example.data.local.VaultEntity
import com.example.data.model.DuplicateGroup
import com.example.data.model.FileCategory
import com.example.data.model.FileItem
import com.example.data.model.FileType
import com.example.data.model.SortBy
import com.example.data.model.SortOption
import com.example.data.model.SortOrder
import com.example.data.model.StorageInfo
import com.example.data.security.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class FileManagerRepository(
    private val context: Context,
    private val database: AppDatabase,
    val cryptoManager: CryptoManager
) {
    private val vaultDao = database.vaultDao()
    private val favoriteDao = database.favoriteDao()
    private val recentDao = database.recentDao()
    private val trashDao = database.trashDao()

    val vaultItems: Flow<List<VaultEntity>> = vaultDao.getAllVaultItems()
    val favoriteItems: Flow<List<FavoriteEntity>> = favoriteDao.getAllFavorites()
    val recentItems: Flow<List<RecentEntity>> = recentDao.getRecentFiles()
    val trashItems: Flow<List<TrashEntity>> = trashDao.getAllTrash()

    fun getInternalStorageRoot(): File {
        return Environment.getExternalStorageDirectory()
    }

    suspend fun getStorageVolumes(): List<StorageInfo> = withContext(Dispatchers.IO) {
        val list = mutableListOf<StorageInfo>()

        // 1. Internal Storage
        val internalFile = Environment.getExternalStorageDirectory()
        if (internalFile.exists()) {
            try {
                val stat = StatFs(internalFile.path)
                val blockSize = stat.blockSizeLong
                val totalBlocks = stat.blockCountLong
                val availableBlocks = stat.availableBlocksLong

                val totalBytes = totalBlocks * blockSize
                val freeBytes = availableBlocks * blockSize
                val usedBytes = (totalBytes - freeBytes).coerceAtLeast(0L)

                list.add(
                    StorageInfo(
                        name = "Internal Storage",
                        path = internalFile.absolutePath,
                        totalBytes = totalBytes,
                        freeBytes = freeBytes,
                        usedBytes = usedBytes,
                        isRemovable = false
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. SD Card / Secondary storage if present
        try {
            val externalDirs = context.getExternalFilesDirs(null)
            for (dir in externalDirs) {
                if (dir != null && Environment.isExternalStorageRemovable(dir)) {
                    val root = getVolumeRoot(dir)
                    if (root != null && root.canRead()) {
                        val stat = StatFs(root.path)
                        val blockSize = stat.blockSizeLong
                        val totalBytes = stat.blockCountLong * blockSize
                        val freeBytes = stat.availableBlocksLong * blockSize
                        val usedBytes = (totalBytes - freeBytes).coerceAtLeast(0L)

                        list.add(
                            StorageInfo(
                                name = "SD Card",
                                path = root.absolutePath,
                                totalBytes = totalBytes,
                                freeBytes = freeBytes,
                                usedBytes = usedBytes,
                                isRemovable = true
                            )
                        )
                        break
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        list
    }

    private fun getVolumeRoot(file: File): File? {
        var current: File? = file
        while (current != null) {
            val parent = current.parentFile
            if (parent != null && parent.name == "storage") {
                return current
            }
            current = parent
        }
        return null
    }

    suspend fun getDirectoryContents(
        directory: File,
        sortOption: SortOption = SortOption(),
        showHidden: Boolean = false
    ): List<FileItem> = withContext(Dispatchers.IO) {
        if (!directory.exists() || !directory.isDirectory) return@withContext emptyList()

        val files = directory.listFiles() ?: return@withContext emptyList()
        val filteredFiles: List<File> = if (showHidden) files.toList() else files.filter { !it.name.startsWith(".") }
        val items = filteredFiles.map { file ->

            val itemCount = if (file.isDirectory) (file.listFiles()?.count { showHidden || !it.name.startsWith(".") } ?: 0) else 0
            FileItem(
                file = file,
                itemCount = itemCount
            )
        }

        sortFileList(items, sortOption)
    }


    private fun sortFileList(items: List<FileItem>, sortOption: SortOption): List<FileItem> {
        val comparator = when (sortOption.sortBy) {
            SortBy.NAME -> compareBy<FileItem> { it.name.lowercase() }
            SortBy.DATE -> compareBy<FileItem> { it.lastModified }
            SortBy.SIZE -> compareBy<FileItem> { it.size }
            SortBy.TYPE -> compareBy<FileItem> { it.fileType.name }
        }

        val orderedComparator = if (sortOption.sortOrder == SortOrder.DESCENDING) {
            comparator.reversed()
        } else {
            comparator
        }

        // Directories first
        return items.sortedWith(
            compareBy<FileItem> { !it.isDirectory }.then(orderedComparator)
        )
    }

    suspend fun getFilesForCategory(category: FileCategory): List<FileItem> = withContext(Dispatchers.IO) {
        val root = getInternalStorageRoot()
        val results = mutableListOf<FileItem>()

        when (category) {
            FileCategory.CAMERA -> {
                scanDirForFiles(File(root, "DCIM/Camera"), results)
                scanDirForFiles(File(root, "DCIM/100MEDIA"), results)
            }
            FileCategory.SCREENSHOTS -> {
                scanDirForFiles(File(root, "Pictures/Screenshots"), results)
                scanDirForFiles(File(root, "DCIM/Screenshots"), results)
            }
            FileCategory.DOWNLOADS -> {
                scanDirForFiles(File(root, Environment.DIRECTORY_DOWNLOADS), results, recursive = false)
            }
            FileCategory.BLUETOOTH -> {
                scanDirForFiles(File(root, "Bluetooth"), results)
                scanDirForFiles(File(root, "Download/Bluetooth"), results)
            }
            FileCategory.WHATSAPP -> {
                val waNew = File(root, "Android/media/com.whatsapp/WhatsApp/Media")
                val waOld = File(root, "WhatsApp/Media")
                if (waNew.exists()) scanDirForFiles(waNew, results, maxDepth = 3)
                if (waOld.exists()) scanDirForFiles(waOld, results, maxDepth = 3)
            }
            FileCategory.IMAGES -> {
                scanForType(root, setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "svg"), results)
            }
            FileCategory.VIDEOS -> {
                scanForType(root, setOf("mp4", "mkv", "avi", "mov", "webm", "3gp", "flv", "m4v"), results)
            }
            FileCategory.AUDIO -> {
                scanForType(root, setOf("mp3", "wav", "ogg", "m4a", "aac", "flac", "opus", "wma"), results)
            }
            FileCategory.DOCUMENTS -> {
                scanForType(
                    root,
                    setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "rtf", "csv", "epub"),
                    results
                )
            }
            FileCategory.APKS -> {
                scanForType(root, setOf("apk", "xapk", "apks"), results)
            }
            FileCategory.ARCHIVES -> {
                scanForType(root, setOf("zip", "rar", "7z", "tar", "gz", "bz2"), results)
            }
            FileCategory.VAULT -> {
                // Vault handled separately via Room Flow
            }
        }

        results.sortedByDescending { it.lastModified }
    }

    private fun scanDirForFiles(
        dir: File,
        results: MutableList<FileItem>,
        recursive: Boolean = true,
        maxDepth: Int = 4,
        currentDepth: Int = 0
    ) {
        if (!dir.exists() || !dir.isDirectory || currentDepth > maxDepth) return
        val list = dir.listFiles() ?: return
        for (f in list) {
            if (f.name.startsWith(".")) continue
            if (f.isFile) {
                results.add(FileItem(f))
            } else if (recursive && f.isDirectory) {
                scanDirForFiles(f, results, recursive, maxDepth, currentDepth + 1)
            }
        }
    }

    private fun scanForType(
        dir: File,
        extensions: Set<String>,
        results: MutableList<FileItem>,
        maxDepth: Int = 4,
        currentDepth: Int = 0
    ) {
        if (!dir.exists() || !dir.isDirectory || currentDepth > maxDepth) return
        val files = dir.listFiles() ?: return
        for (f in files) {
            val name = f.name
            if (name.startsWith(".")) continue
            // Skip system Android app data to avoid performance lags
            if (currentDepth == 1 && name.equals("Android", ignoreCase = true)) continue

            if (f.isFile) {
                val ext = f.extension.lowercase()
                if (extensions.contains(ext)) {
                    results.add(FileItem(f))
                }
            } else if (f.isDirectory) {
                scanForType(f, extensions, results, maxDepth, currentDepth + 1)
            }
        }
    }

    suspend fun searchFiles(
        query: String,
        categoryFilter: FileCategory? = null,
        minSizeBytes: Long? = null,
        maxSizeBytes: Long? = null,
        showHidden: Boolean = false
    ): List<FileItem> = withContext(Dispatchers.IO) {
        val root = getInternalStorageRoot()
        val matching = mutableListOf<FileItem>()
        var cleanQuery = query.trim()

        // Parse query syntax: type:pdf, size:>10MB, etc.
        var parsedCategory = categoryFilter
        var parsedMinSize = minSizeBytes
        var parsedMaxSize = maxSizeBytes
        var dateFilter: String? = null

        val tokens = cleanQuery.split("\\s+".toRegex())
        val nameWords = mutableListOf<String>()

        for (token in tokens) {
            val lowerToken = token.lowercase()
            when {
                lowerToken.startsWith("type:") -> {
                    val typeStr = lowerToken.substringAfter("type:")
                    parsedCategory = when (typeStr) {
                        "image", "images", "photo", "photos" -> FileCategory.IMAGES
                        "video", "videos", "movie" -> FileCategory.VIDEOS
                        "audio", "music", "song" -> FileCategory.AUDIO
                        "doc", "docs", "document", "documents", "pdf" -> FileCategory.DOCUMENTS
                        "apk", "apks", "app" -> FileCategory.APKS
                        "archive", "zip", "rar" -> FileCategory.ARCHIVES
                        else -> parsedCategory
                    }
                }
                lowerToken.startsWith("size:>") -> {
                    val sizeStr = lowerToken.substringAfter("size:>")
                    parsedMinSize = parseSizeStrToBytes(sizeStr)
                }
                lowerToken.startsWith("size:<") -> {
                    val sizeStr = lowerToken.substringAfter("size:<")
                    parsedMaxSize = parseSizeStrToBytes(sizeStr)
                }
                lowerToken.startsWith("name:") -> {
                    nameWords.add(lowerToken.substringAfter("name:"))
                }
                lowerToken.startsWith("modified:") -> {
                    dateFilter = lowerToken.substringAfter("modified:")
                }
                else -> {
                    if (token.isNotEmpty()) nameWords.add(lowerToken)
                }
            }
        }

        val searchName = nameWords.joinToString(" ")
        val now = System.currentTimeMillis()
        val oneDayMs = 24 * 60 * 60 * 1000L
        val sevenDaysMs = 7 * oneDayMs

        fun searchRecursive(dir: File, depth: Int = 0) {
            if (!dir.exists() || depth > 5) return
            val files = dir.listFiles() ?: return
            for (f in files) {
                if (!showHidden && f.name.startsWith(".")) continue
                if (depth == 1 && f.name.equals("Android", ignoreCase = true)) continue
                if (f.name == "encrypted_vault" || f.name == "trash_bin" || f.name.startsWith("enc_")) continue

                val matchesName = searchName.isEmpty() || f.name.lowercase().contains(searchName)
                val matchesSize = (parsedMinSize == null || f.length() >= parsedMinSize) &&
                        (parsedMaxSize == null || f.length() <= parsedMaxSize)

                val matchesDate = when (dateFilter) {
                    "today" -> (now - f.lastModified()) < oneDayMs
                    "yesterday" -> (now - f.lastModified()) in oneDayMs..(2 * oneDayMs)
                    "this_week" -> (now - f.lastModified()) < sevenDaysMs
                    else -> true
                }

                if (matchesName && matchesSize && matchesDate) {
                    val item = FileItem(f)
                    val matchesCategory = when (parsedCategory) {
                        null -> true
                        FileCategory.IMAGES -> item.fileType == FileType.IMAGE
                        FileCategory.VIDEOS -> item.fileType == FileType.VIDEO
                        FileCategory.AUDIO -> item.fileType == FileType.AUDIO
                        FileCategory.DOCUMENTS -> item.fileType == FileType.DOCUMENT || item.fileType == FileType.PDF || item.fileType == FileType.TEXT
                        FileCategory.APKS -> item.fileType == FileType.APK
                        FileCategory.ARCHIVES -> item.fileType == FileType.ARCHIVE
                        else -> true
                    }
                    if (matchesCategory) {
                        matching.add(item)
                    }
                }

                if (f.isDirectory) {
                    searchRecursive(f, depth + 1)
                }
            }
        }

        searchRecursive(root)
        matching.sortedByDescending { it.lastModified }
    }

    private fun parseSizeStrToBytes(str: String): Long {
        return try {
            val numStr = str.filter { it.isDigit() || it == '.' }
            val unitStr = str.filter { it.isLetter() }.uppercase()
            val num = numStr.toDoubleOrNull() ?: return 0L
            when {
                unitStr.startsWith("G") -> (num * 1024 * 1024 * 1024).toLong()
                unitStr.startsWith("M") -> (num * 1024 * 1024).toLong()
                unitStr.startsWith("K") -> (num * 1024).toLong()
                else -> num.toLong()
            }
        } catch (e: Exception) {
            0L
        }
    }

    suspend fun createFolder(parent: File, folderName: String): Boolean = withContext(Dispatchers.IO) {
        val target = File(parent, folderName.trim())
        if (!target.exists()) target.mkdirs() else false
    }

    suspend fun createFile(parent: File, fileName: String, templateContent: String = ""): Boolean = withContext(Dispatchers.IO) {
        try {
            val target = File(parent, fileName.trim())
            if (target.exists()) return@withContext false
            target.parentFile?.mkdirs()
            target.writeText(templateContent)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun renameFile(file: File, newName: String): Boolean = withContext(Dispatchers.IO) {
        val target = File(file.parentFile, newName.trim())
        if (target.exists()) return@withContext false
        val renamed = file.renameTo(target)
        if (renamed) {
            recentDao.removeRecent(file.absolutePath)
            favoriteDao.removeFavorite(file.absolutePath)
        }
        renamed
    }

    suspend fun deleteFile(file: File): Boolean = withContext(Dispatchers.IO) {
        val deleted = if (file.isDirectory) {
            file.deleteRecursively()
        } else {
            file.delete()
        }
        if (deleted) {
            recentDao.removeRecent(file.absolutePath)
            favoriteDao.removeFavorite(file.absolutePath)
        }
        deleted
    }

    suspend fun copyFile(source: File, destinationDir: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val target = File(destinationDir, source.name)
            if (source.isDirectory) {
                source.copyRecursively(target, overwrite = true)
            } else {
                source.copyTo(target, overwrite = true)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun moveFile(source: File, destinationDir: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val target = File(destinationDir, source.name)
            if (source.renameTo(target)) {
                true
            } else {
                val copied = copyFile(source, destinationDir)
                if (copied) {
                    deleteFile(source)
                } else false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun calculateDirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        if (!dir.isDirectory) return dir.length()
        var size = 0L
        val stack = ArrayDeque<File>()
        stack.add(dir)
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            val files = current.listFiles() ?: continue
            for (f in files) {
                if (f.isDirectory) {
                    stack.add(f)
                } else {
                    size += f.length()
                }
            }
        }
        return size
    }

    private fun copyWithByteTracking(source: File, destDir: File, onBytesCopied: (Long) -> Unit): Boolean {
        return try {
            val target = File(destDir, source.name)
            if (source.isDirectory) {
                if (!target.exists()) target.mkdirs()
                val children = source.listFiles() ?: return true
                for (child in children) {
                    copyWithByteTracking(child, target, onBytesCopied)
                }
                true
            } else {
                FileInputStream(source).use { input ->
                    FileOutputStream(target).use { output ->
                        val buffer = ByteArray(65536)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            onBytesCopied(bytesRead.toLong())
                        }
                    }
                }
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun copyFilesBatch(
        sources: List<File>,
        destinationDir: File,
        onProgress: (processedFiles: Int, totalFiles: Int, processedBytes: Long, totalBytes: Long, currentFile: String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        var totalBytes = 0L
        for (f in sources) {
            totalBytes += if (f.isDirectory) calculateDirSize(f) else f.length()
        }
        var processedBytes = 0L
        var processedFiles = 0
        val totalFiles = sources.size

        for (source in sources) {
            onProgress(processedFiles, totalFiles, processedBytes, totalBytes, source.name)
            val success = copyWithByteTracking(source, destinationDir) { bytesCopied ->
                processedBytes += bytesCopied
                onProgress(processedFiles, totalFiles, processedBytes, totalBytes, source.name)
            }
            if (!success) return@withContext false
            processedFiles++
            onProgress(processedFiles, totalFiles, processedBytes, totalBytes, source.name)
        }
        true
    }

    suspend fun moveFilesBatch(
        sources: List<File>,
        destinationDir: File,
        onProgress: (processedFiles: Int, totalFiles: Int, processedBytes: Long, totalBytes: Long, currentFile: String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        var totalBytes = 0L
        for (f in sources) {
            totalBytes += if (f.isDirectory) calculateDirSize(f) else f.length()
        }
        var processedBytes = 0L
        var processedFiles = 0
        val totalFiles = sources.size

        for (source in sources) {
            onProgress(processedFiles, totalFiles, processedBytes, totalBytes, source.name)
            val target = File(destinationDir, source.name)
            if (source.renameTo(target)) {
                val size = if (target.isDirectory) calculateDirSize(target) else target.length()
                processedBytes += size
            } else {
                // Cross volume fallback
                val copied = copyWithByteTracking(source, destinationDir) { bytesCopied ->
                    processedBytes += bytesCopied
                    onProgress(processedFiles, totalFiles, processedBytes, totalBytes, source.name)
                }
                if (copied) {
                    deleteFile(source)
                } else return@withContext false
            }
            processedFiles++
            onProgress(processedFiles, totalFiles, processedBytes, totalBytes, source.name)
        }
        true
    }

    fun getTrashDir(): File {
        val dir = File(context.filesDir, "trash_bin")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    suspend fun moveToTrash(file: File): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!file.exists()) return@withContext false
            val trashDir = getTrashDir()
            val trashFileName = "${System.currentTimeMillis()}_${file.name}"
            val targetFile = File(trashDir, trashFileName)

            val moved = if (file.renameTo(targetFile)) {
                true
            } else {
                if (file.isDirectory) {
                    file.copyRecursively(targetFile, overwrite = true) && file.deleteRecursively()
                } else {
                    file.copyTo(targetFile, overwrite = true)
                    file.delete()
                }
            }

            if (moved) {
                val entity = TrashEntity(
                    trashFileName = trashFileName,
                    originalFileName = file.name,
                    originalPath = file.absolutePath,
                    size = if (targetFile.isDirectory) calculateDirSize(targetFile) else targetFile.length(),
                    isDirectory = targetFile.isDirectory,
                    deletedAt = System.currentTimeMillis()
                )
                trashDao.insert(entity)
                recentDao.removeRecent(file.absolutePath)
                favoriteDao.removeFavorite(file.absolutePath)
                true
            } else false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun restoreFromTrash(item: TrashEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            val trashFile = File(getTrashDir(), item.trashFileName)
            if (!trashFile.exists()) {
                trashDao.delete(item)
                return@withContext false
            }

            var destFile = File(item.originalPath)
            if (destFile.exists()) {
                val parent = destFile.parentFile ?: getInternalStorageRoot()
                val nameWithoutExt = destFile.nameWithoutExtension
                val ext = if (destFile.extension.isNotEmpty()) ".${destFile.extension}" else ""
                destFile = File(parent, "${nameWithoutExt}_restored$ext")
            } else {
                destFile.parentFile?.mkdirs()
            }

            val restored = if (trashFile.renameTo(destFile)) {
                true
            } else {
                if (trashFile.isDirectory) {
                    trashFile.copyRecursively(destFile, overwrite = true) && trashFile.deleteRecursively()
                } else {
                    trashFile.copyTo(destFile, overwrite = true)
                    trashFile.delete()
                }
            }

            if (restored) {
                trashDao.delete(item)
                true
            } else false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun deletePermanentlyFromTrash(item: TrashEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            val trashFile = File(getTrashDir(), item.trashFileName)
            if (trashFile.exists()) {
                if (trashFile.isDirectory) trashFile.deleteRecursively() else trashFile.delete()
            }
            trashDao.delete(item)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun emptyTrash(): Boolean = withContext(Dispatchers.IO) {
        try {
            val trashDir = getTrashDir()
            trashDir.listFiles()?.forEach {
                if (it.isDirectory) it.deleteRecursively() else it.delete()
            }
            trashDao.clearAll()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun findDuplicateFiles(
        minSizeBytes: Long = 1024L
    ): List<DuplicateGroup> = withContext(Dispatchers.IO) {
        val root = getInternalStorageRoot()
        val sizeMap = mutableMapOf<Long, MutableList<File>>()

        fun scanForSizes(dir: File, depth: Int = 0) {
            if (!dir.exists() || depth > 5) return
            val files = dir.listFiles() ?: return
            for (f in files) {
                if (f.name.startsWith(".")) continue
                if (depth == 1 && f.name.equals("Android", ignoreCase = true)) continue
                if (f.isFile) {
                    val len = f.length()
                    if (len >= minSizeBytes) {
                        sizeMap.getOrPut(len) { mutableListOf() }.add(f)
                    }
                } else if (f.isDirectory) {
                    scanForSizes(f, depth + 1)
                }
            }
        }
        scanForSizes(root)

        val sizeCandidates = sizeMap.filter { it.value.size >= 2 }
        val duplicateGroups = mutableListOf<DuplicateGroup>()

        for ((size, candidateFiles) in sizeCandidates) {
            val hashMap = mutableMapOf<String, MutableList<File>>()
            for (file in candidateFiles) {
                val hash = calculateChecksum(file, "SHA-256")
                if (hash != "Checksum unavailable") {
                    hashMap.getOrPut(hash) { mutableListOf() }.add(file)
                }
            }
            for ((hash, filesWithSameHash) in hashMap) {
                if (filesWithSameHash.size >= 2) {
                    duplicateGroups.add(
                        DuplicateGroup(
                            fileSize = size,
                            hash = hash,
                            files = filesWithSameHash
                        )
                    )
                }
            }
        }

        duplicateGroups.sortedByDescending { it.wasteSize }
    }


    suspend fun compressToZip(files: List<File>, zipFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
                for (file in files) {
                    zipFileOrDir(file, file.name, zos)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun zipFileOrDir(file: File, pathInZip: String, zos: ZipOutputStream) {
        if (file.isDirectory) {
            val children = file.listFiles() ?: return
            for (child in children) {
                zipFileOrDir(child, "$pathInZip/${child.name}", zos)
            }
        } else {
            val entry = ZipEntry(pathInZip)
            zos.putNextEntry(entry)
            FileInputStream(file).use { fis ->
                fis.copyTo(zos)
            }
            zos.closeEntry()
        }
    }

    suspend fun extractZip(zipFile: File, destinationDir: File): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!destinationDir.exists()) destinationDir.mkdirs()
            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                val buffer = ByteArray(4096)
                while (entry != null) {
                    val newFile = File(destinationDir, entry.name)
                    // Guard against Zip Slip vulnerability
                    if (!newFile.canonicalPath.startsWith(destinationDir.canonicalPath)) {
                        entry = zis.nextEntry
                        continue
                    }
                    if (entry.isDirectory) {
                        newFile.mkdirs()
                    } else {
                        newFile.parentFile?.mkdirs()
                        FileOutputStream(newFile).use { fos ->
                            var len: Int
                            while (zis.read(buffer).also { len = it } > 0) {
                                fos.write(buffer, 0, len)
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun getZipEntries(zipFile: File): List<String> = withContext(Dispatchers.IO) {
        val entries = mutableListOf<String>()
        try {
            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    entries.add(entry.name + if (entry.isDirectory) "/" else " (${FileItem.formatFileSize(entry.size)})")
                    entry = zis.nextEntry
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        entries
    }

    suspend fun calculateChecksum(file: File, algorithm: String = "SHA-256"): String = withContext(Dispatchers.IO) {
        try {
            val digest = MessageDigest.getInstance(algorithm)
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(8192)
                var read: Int
                while (fis.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            "Checksum unavailable"
        }
    }

    suspend fun moveToVault(sourceFile: File, pin: String, deleteOriginal: Boolean = true): Boolean = withContext(Dispatchers.IO) {
        val encResult = cryptoManager.encryptFile(sourceFile, pin) ?: return@withContext false
        val vaultEntity = VaultEntity(
            vaultFileName = encResult.encryptedFile.name,
            originalFileName = sourceFile.name,
            originalPath = sourceFile.absolutePath,
            originalSize = encResult.originalSize,
            mimeType = FileItem.determineFileType(sourceFile).name,
            saltHex = encResult.saltHex,
            ivHex = encResult.ivHex,
            category = FileItem.determineFileType(sourceFile).name
        )
        vaultDao.insert(vaultEntity)
        if (deleteOriginal) {
            sourceFile.delete()
        }
        recentDao.removeRecent(sourceFile.absolutePath)
        favoriteDao.removeFavorite(sourceFile.absolutePath)
        true
    }

    suspend fun exportFromVault(vaultItem: VaultEntity, pin: String, destinationDir: File): Boolean = withContext(Dispatchers.IO) {
        val encryptedFile = File(cryptoManager.getVaultDir(), vaultItem.vaultFileName)
        val targetFile = File(destinationDir, vaultItem.originalFileName)
        val success = cryptoManager.decryptToFile(
            encryptedFile = encryptedFile,
            destinationFile = targetFile,
            pin = pin,
            saltHex = vaultItem.saltHex,
            ivHex = vaultItem.ivHex
        )
        if (success) {
            cryptoManager.deleteEncryptedFile(vaultItem.vaultFileName)
            vaultDao.delete(vaultItem)
        }
        success
    }

    suspend fun deleteVaultItem(vaultItem: VaultEntity): Boolean = withContext(Dispatchers.IO) {
        cryptoManager.deleteEncryptedFile(vaultItem.vaultFileName)
        vaultDao.delete(vaultItem)
        true
    }

    suspend fun toggleFavorite(file: File): Boolean = withContext(Dispatchers.IO) {
        if (file.absolutePath.contains("encrypted_vault") ||
            file.absolutePath.contains("trash_bin") ||
            file.name.startsWith("enc_")) {
            return@withContext false
        }
        val path = file.absolutePath
        val isFav = favoriteDao.isFavoriteDirect(path)
        if (isFav) {
            favoriteDao.removeFavorite(path)
            false
        } else {
            val entity = FavoriteEntity(path, file.name, file.isDirectory)
            favoriteDao.addFavorite(entity)
            true
        }
    }

    suspend fun removeFavorite(path: String) = withContext(Dispatchers.IO) {
        favoriteDao.removeFavorite(path)
    }

    suspend fun recordRecent(file: File) = withContext(Dispatchers.IO) {
        if (!file.exists() ||
            file.absolutePath.contains("encrypted_vault") ||
            file.absolutePath.contains("trash_bin") ||
            file.name.startsWith("enc_")) {
            return@withContext
        }
        val entity = RecentEntity(
            path = file.absolutePath,
            name = file.name,
            size = file.length(),
            isDirectory = file.isDirectory,
            fileType = FileItem.determineFileType(file).name
        )
        recentDao.recordAccess(entity)
    }

    suspend fun findLargeFiles(minSizeBytes: Long = 20 * 1024 * 1024L): List<FileItem> = withContext(Dispatchers.IO) {
        val root = getInternalStorageRoot()
        val large = mutableListOf<FileItem>()
        fun scan(dir: File, depth: Int = 0) {
            if (!dir.exists() || depth > 5) return
            val files = dir.listFiles() ?: return
            for (f in files) {
                if (f.name.startsWith(".")) continue
                if (depth == 1 && f.name.equals("Android", ignoreCase = true)) continue
                if (f.isFile && f.length() >= minSizeBytes) {
                    large.add(FileItem(f))
                } else if (f.isDirectory) {
                    scan(f, depth + 1)
                }
            }
        }
        scan(root)
        large.sortedByDescending { it.size }
    }

    suspend fun findJunkFiles(): List<FileItem> = withContext(Dispatchers.IO) {
        val root = getInternalStorageRoot()
        val junk = mutableListOf<FileItem>()
        fun scan(dir: File, depth: Int = 0) {
            if (!dir.exists() || depth > 4) return
            val files = dir.listFiles() ?: return
            for (f in files) {
                if (f.isFile) {
                    val ext = f.extension.lowercase()
                    val isJunkExt = ext in setOf("tmp", "temp", "log", "bak", "cache")
                    val isZeroByte = f.length() == 0L && (ext.isNotEmpty() || f.name.startsWith("."))
                    if (isJunkExt || isZeroByte) {
                        junk.add(FileItem(f))
                    }
                } else if (f.isDirectory) {
                    val children = f.listFiles()
                    if (children != null && children.isEmpty() && !f.name.startsWith(".")) {
                        junk.add(FileItem(f))
                    } else {
                        scan(f, depth + 1)
                    }
                }
            }
        }
        scan(root)
        junk
    }

    suspend fun initializeSampleFilesIfEmpty() = withContext(Dispatchers.IO) {
        val root = getInternalStorageRoot()
        val docsDir = File(root, "Documents")
        val downloadsDir = File(root, "Download")
        val notesDir = File(docsDir, "Confidential Notes")
        val picturesDir = File(root, "Pictures/Screenshots")

        docsDir.mkdirs()
        downloadsDir.mkdirs()
        notesDir.mkdirs()
        picturesDir.mkdirs()

        val sampleDoc = File(docsDir, "Welcome_to_VaultFiles.txt")
        if (!sampleDoc.exists()) {
            sampleDoc.writeText(
                """
                === Welcome to VaultFiles ===
                100% Offline File Manager with Zero-Knowledge Encrypted Vault.

                Features:
                • AES-256-GCM Military-grade zero-knowledge encryption
                • No cloud syncing, no data tracking, 100% private
                • Cross-platform previewer for images, videos, audio, text & code
                • ZIP Archive viewer and instant compression
                • Fast local search engine with category filters
                • Storage breakdown & junk cleaner

                Your data is stored exclusively on this device.
                """.trimIndent()
            )
        }

        val confidentialNote = File(notesDir, "Financial_Portfolio_2026.txt")
        if (!confidentialNote.exists()) {
            confidentialNote.writeText(
                """
                [CONFIDENTIAL DOCUMENT - MOVE TO ENCRYPTED VAULT]
                Account Portfolio Summary:
                • Emergency Fund: Verified Offline
                • Asset Allocations: 40% Index, 25% Real Estate, 20% Bonds, 15% Reserves
                • Zero-Knowledge Policy: Protected by AES-256-GCM.
                """.trimIndent()
            )
        }

        val sampleCode = File(docsDir, "DataContract.json")
        if (!sampleCode.exists()) {
            sampleCode.writeText(
                """
                {
                  "app": "VaultFiles",
                  "version": "1.0",
                  "offline": true,
                  "encryption": "AES-256-GCM",
                  "keyDerivation": "PBKDF2WithHmacSHA256",
                  "categories": [
                    "Images", "Videos", "Music", "Documents", "APKs", "Archives", "Downloads"
                  ]
                }
                """.trimIndent()
            )
        }
    }
}
