package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object FileShareUtils {

    /**
     * Resolves the MIME type of a given file based on its file extension.
     */
    fun getMimeType(file: File): String {
        val extension = file.extension.lowercase()
        if (extension.isEmpty()) return "*/*"
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
    }

    /**
     * Shares a single file using Android's Intent.ACTION_SEND via FileProvider.
     */
    fun shareFile(context: Context, file: File) {
        if (!file.exists()) {
            Toast.makeText(context, "File does not exist", Toast.LENGTH_SHORT).show()
            return
        }
        if (file.isDirectory) {
            Toast.makeText(
                context,
                "Folders cannot be shared directly. Please compress to ZIP first.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        try {
            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val mimeType = getMimeType(file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share ${file.name} via")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(
                context,
                "Could not share file: ${e.localizedMessage ?: "Unknown error"}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Shares multiple files using Android's Intent.ACTION_SEND_MULTIPLE via FileProvider.
     */
    fun shareMultipleFiles(context: Context, files: List<File>) {
        val shareableFiles = files.filter { it.exists() && !it.isDirectory }
        if (shareableFiles.isEmpty()) {
            Toast.makeText(context, "No valid files selected to share", Toast.LENGTH_SHORT).show()
            return
        }

        if (shareableFiles.size == 1) {
            shareFile(context, shareableFiles.first())
            return
        }

        try {
            val uris = ArrayList<Uri>()
            for (f in shareableFiles) {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    f
                )
                uris.add(uri)
            }

            val mimeTypes = shareableFiles.map { getMimeType(it) }.distinct()
            val resolvedMimeType = when {
                mimeTypes.size == 1 -> mimeTypes.first()
                mimeTypes.all { it.startsWith("image/") } -> "image/*"
                mimeTypes.all { it.startsWith("video/") } -> "video/*"
                mimeTypes.all { it.startsWith("audio/") } -> "audio/*"
                mimeTypes.all { it == "application/pdf" } -> "application/pdf"
                else -> "*/*"
            }

            val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = resolvedMimeType
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                putExtra(Intent.EXTRA_SUBJECT, "Sharing ${shareableFiles.size} files")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share ${shareableFiles.size} files via")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(
                context,
                "Could not share files: ${e.localizedMessage ?: "Unknown error"}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
