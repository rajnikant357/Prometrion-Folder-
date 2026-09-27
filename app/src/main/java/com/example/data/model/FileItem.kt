package com.example.data.model

import java.io.File

enum class FileCategory(val displayName: String, val description: String) {
    IMAGES("Images", "Photos, wallpapers, screenshots"),
    VIDEOS("Videos", "Movies, clips, recordings"),
    AUDIO("Music", "Songs, recordings, podcasts"),
    DOCUMENTS("Documents", "PDF, Word, Excel, text files"),
    APKS("APKs", "Android application packages"),
    DOWNLOADS("Downloads", "Browser and app downloads"),
    BLUETOOTH("Bluetooth", "Received Bluetooth files"),
    WHATSAPP("WhatsApp", "WhatsApp media, docs and audio"),
    ARCHIVES("Archives", "ZIP, RAR, 7Z, TAR compressed files"),
    CAMERA("Camera", "Photos and videos taken by camera"),
    SCREENSHOTS("Screenshots", "Captured device screenshots"),
    VAULT("Vault", "Zero-knowledge encrypted documents")
}

enum class FileType {
    FOLDER,
    IMAGE,
    VIDEO,
    AUDIO,
    DOCUMENT,
    PDF,
    TEXT,
    ARCHIVE,
    APK,
    UNKNOWN
}

enum class SortBy {
    NAME,
    DATE,
    SIZE,
    TYPE
}

enum class SortOrder {
    ASCENDING,
    DESCENDING
}

data class SortOption(
    val sortBy: SortBy = SortBy.NAME,
    val sortOrder: SortOrder = SortOrder.ASCENDING
)

data class StorageInfo(
    val name: String,
    val path: String,
    val totalBytes: Long,
    val freeBytes: Long,
    val usedBytes: Long,
    val isRemovable: Boolean = false
) {
    val usedPercentage: Float
        get() = if (totalBytes > 0) (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
}

data class FileItem(
    val file: File,
    val name: String = file.name,
    val path: String = file.absolutePath,
    val isDirectory: Boolean = file.isDirectory,
    val size: Long = if (file.isDirectory) 0L else file.length(),
    val lastModified: Long = file.lastModified(),
    val extension: String = file.extension.lowercase(),
    val fileType: FileType = determineFileType(file),
    val itemCount: Int = 0, // for directories
    val isFavorite: Boolean = false,
    val isEncrypted: Boolean = false
) {
    companion object {
        fun determineFileType(file: File): FileType {
            if (file.isDirectory) return FileType.FOLDER
            val ext = file.extension.lowercase()
            return when (ext) {
                "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "svg" -> FileType.IMAGE
                "mp4", "mkv", "avi", "mov", "webm", "3gp", "flv", "m4v" -> FileType.VIDEO
                "mp3", "wav", "ogg", "m4a", "aac", "flac", "opus", "wma" -> FileType.AUDIO
                "pdf" -> FileType.PDF
                "txt", "md", "json", "xml", "html", "css", "js", "kt", "java", "csv", "log", "py" -> FileType.TEXT
                "doc", "docx", "xls", "xlsx", "ppt", "pptx", "rtf", "odt" -> FileType.DOCUMENT
                "zip", "rar", "7z", "tar", "gz", "bz2" -> FileType.ARCHIVE
                "apk", "xapk", "apks" -> FileType.APK
                else -> FileType.UNKNOWN
            }
        }

        fun formatFileSize(sizeInBytes: Long): String {
            if (sizeInBytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(sizeInBytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
            val formatted = sizeInBytes / Math.pow(1024.0, digitGroups.toDouble())
            return String.format(java.util.Locale.US, "%.1f %s", formatted, units[digitGroups])
        }
    }
}
