package com.example.data.repository

import android.content.Context
import android.os.Environment
import android.os.StatFs
import com.example.data.local.AppDatabase
import com.example.data.local.FavoriteEntity
import com.example.data.local.RecentEntity
import com.example.data.local.VaultEntity
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

    val vaultItems: Flow<List<VaultEntity>> = vaultDao.getAllVaultItems()
    val favoriteItems: Flow<List<FavoriteEntity>> = favoriteDao.getAllFavorites()
    val recentItems: Flow<List<RecentEntity>> = recentDao.getRecentFiles()

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
        sortOption: SortOption = SortOption()
    ): List<FileItem> = withContext(Dispatchers.IO) {
        if (!directory.exists() || !directory.isDirectory) return@withContext emptyList()

        val files = directory.listFiles() ?: return@withContext emptyList()
        val items = files.map { file ->
            val itemCount = if (file.isDirectory) (file.listFiles()?.size ?: 0) else 0
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
        maxSizeBytes: Long? = null
    ): List<FileItem> = withContext(Dispatchers.IO) {
        val root = getInternalStorageRoot()
        val matching = mutableListOf<FileItem>()
        val qLower = query.lowercase().trim()

        fun searchRecursive(dir: File, depth: Int = 0) {
            if (!dir.exists() || depth > 5) return
            val files = dir.listFiles() ?: return
            for (f in files) {
                if (f.name.startsWith(".")) continue
                if (depth == 1 && f.name.equals("Android", ignoreCase = true)) continue

                val matchesName = qLower.isEmpty() || f.name.lowercase().contains(qLower)
                val matchesSize = (minSizeBytes == null || f.length() >= minSizeBytes) &&
                        (maxSizeBytes == null || f.length() <= maxSizeBytes)

                if (matchesName && matchesSize) {
                    val item = FileItem(f)
                    val matchesCategory = when (categoryFilter) {
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

    suspend fun createFolder(parent: File, folderName: String): Boolean = withContext(Dispatchers.IO) {
        val target = File(parent, folderName)
        if (!target.exists()) target.mkdirs() else false
    }

    suspend fun renameFile(file: File, newName: String): Boolean = withContext(Dispatchers.IO) {
        val target = File(file.parentFile, newName)
        if (target.exists()) return@withContext false
        file.renameTo(target)
    }

    suspend fun deleteFile(file: File): Boolean = withContext(Dispatchers.IO) {
        if (file.isDirectory) {
            file.deleteRecursively()
        } else {
            file.delete()
        }
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
        val path = file.absolutePath
        val isFav = favoriteDao.isFavorite(path)
        // Check current
        val entity = FavoriteEntity(path, file.name, file.isDirectory)
        favoriteDao.addFavorite(entity)
        true
    }

    suspend fun removeFavorite(path: String) = withContext(Dispatchers.IO) {
        favoriteDao.removeFavorite(path)
    }

    suspend fun recordRecent(file: File) = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext
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
