package com.example.data.model

import java.io.File

enum class FileOperationType {
    COPY,
    MOVE,
    DELETE,
    TRASH,
    RESTORE,
    COMPRESS,
    EXTRACT,
    ENCRYPT,
    DECRYPT
}

enum class FileOperationStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    CANCELLED,
    FAILED,
    PARTIAL_FAILURE
}

data class FileOperationProgress(
    val operationId: String,
    val title: String,
    val type: FileOperationType,
    val totalFiles: Int,
    val processedFiles: Int,
    val totalBytes: Long,
    val processedBytes: Long,
    val speedBytesPerSec: Long = 0L,
    val estimatedRemainingSeconds: Long = -1L,
    val currentFileName: String = "",
    val status: FileOperationStatus = FileOperationStatus.RUNNING,
    val errorMessage: String? = null
) {
    val progressFraction: Float
        get() = when {
            totalBytes > 0L -> (processedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
            totalFiles > 0 -> (processedFiles.toFloat() / totalFiles.toFloat()).coerceIn(0f, 1f)
            else -> 0f
        }

    val formattedSpeed: String
        get() = if (speedBytesPerSec > 0) "${FileItem.formatFileSize(speedBytesPerSec)}/s" else "Calculating..."

    val formattedRemainingTime: String
        get() = when {
            estimatedRemainingSeconds in 0..59 -> "${estimatedRemainingSeconds}s remaining"
            estimatedRemainingSeconds >= 60 -> "${estimatedRemainingSeconds / 60}m ${estimatedRemainingSeconds % 60}s remaining"
            else -> "Estimating time..."
        }
}

data class BatchClipboard(
    val files: List<File>,
    val isCut: Boolean
) {
    val totalCount: Int get() = files.size
    val totalSize: Long get() = files.sumOf { if (it.isDirectory) 0L else it.length() }
}

data class DuplicateGroup(
    val fileSize: Long,
    val hash: String,
    val files: List<File>
) {
    val count: Int get() = files.size
    val wasteSize: Long get() = fileSize * (files.size - 1).coerceAtLeast(0)
}
