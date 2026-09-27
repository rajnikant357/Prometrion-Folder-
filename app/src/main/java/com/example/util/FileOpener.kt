package com.example.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

data class FileFormatInfo(
    val extension: String,
    val mimeType: String,
    val categoryName: String,
    val description: String,
    val canPreviewInternally: Boolean,
    val isApk: Boolean = false,
    val isPdf: Boolean = false
)

data class ApkInfo(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdkVersion: Int,
    val targetSdkVersion: Int,
    val icon: Drawable?,
    val permissions: List<String>,
    val fileSize: Long,
    val isAlreadyInstalled: Boolean,
    val installedVersionName: String?
)

data class AppTarget(
    val appName: String,
    val packageName: String,
    val activityName: String,
    val icon: Drawable?
)

object FileOpener {

    /**
     * Resolves exact MIME type and format metadata for any given file.
     */
    fun identifyFormat(file: File): FileFormatInfo {
        val ext = file.extension.lowercase()
        return when (ext) {
            "pdf" -> FileFormatInfo(
                extension = "pdf",
                mimeType = "application/pdf",
                categoryName = "PDF Document",
                description = "Adobe Portable Document Format",
                canPreviewInternally = true,
                isPdf = true
            )
            "apk" -> FileFormatInfo(
                extension = "apk",
                mimeType = "application/vnd.android.package-archive",
                categoryName = "Android Package",
                description = "Android Application Installation Package",
                canPreviewInternally = true,
                isApk = true
            )
            "xapk", "apks" -> FileFormatInfo(
                extension = ext,
                mimeType = "application/vnd.android.package-archive",
                categoryName = "Split Android Package",
                description = "Modular Android Package bundle",
                canPreviewInternally = true,
                isApk = true
            )
            // Images
            "jpg", "jpeg" -> FileFormatInfo(ext, "image/jpeg", "JPEG Image", "Joint Photographic Experts Group", true)
            "png" -> FileFormatInfo(ext, "image/png", "PNG Image", "Portable Network Graphics", true)
            "webp" -> FileFormatInfo(ext, "image/webp", "WebP Image", "Google WebP Picture", true)
            "gif" -> FileFormatInfo(ext, "image/gif", "GIF Animation", "Graphics Interchange Format", true)
            "svg" -> FileFormatInfo(ext, "image/svg+xml", "Vector Graphic", "Scalable Vector Graphics", true)
            "bmp" -> FileFormatInfo(ext, "image/bmp", "Bitmap Image", "Windows Bitmap", true)
            "heic", "heif" -> FileFormatInfo(ext, "image/heic", "High Efficiency Image", "HEIF / HEIC container", true)
            // Audio
            "mp3" -> FileFormatInfo(ext, "audio/mpeg", "MP3 Audio", "MPEG Audio Layer III", true)
            "m4a", "aac" -> FileFormatInfo(ext, "audio/mp4", "M4A Audio", "Advanced Audio Coding", true)
            "wav" -> FileFormatInfo(ext, "audio/wav", "WAV Audio", "Waveform Audio File Format", true)
            "ogg", "opus" -> FileFormatInfo(ext, "audio/ogg", "Ogg Audio", "Ogg Vorbis / Opus Stream", true)
            "flac" -> FileFormatInfo(ext, "audio/flac", "FLAC Audio", "Free Lossless Audio Codec", true)
            // Video
            "mp4" -> FileFormatInfo(ext, "video/mp4", "MP4 Video", "MPEG-4 Part 14", true)
            "mkv" -> FileFormatInfo(ext, "video/x-matroska", "Matroska Video", "MKV Multimedia Container", true)
            "webm" -> FileFormatInfo(ext, "video/webm", "WebM Video", "WebM Open Media Project", true)
            "avi" -> FileFormatInfo(ext, "video/x-msvideo", "AVI Video", "Audio Video Interleave", true)
            "mov" -> FileFormatInfo(ext, "video/quicktime", "QuickTime Video", "Apple QuickTime Movie", true)
            "3gp" -> FileFormatInfo(ext, "video/3gpp", "3GP Video", "3GPP Mobile Video", true)
            // Documents & Office
            "doc" -> FileFormatInfo(ext, "application/msword", "Word Document", "Microsoft Word 97-2003", false)
            "docx" -> FileFormatInfo(
                ext,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "Word Document (DOCX)",
                "Office Open XML Document",
                false
            )
            "xls" -> FileFormatInfo(ext, "application/vnd.ms-excel", "Excel Spreadsheet", "Microsoft Excel 97-2003", false)
            "xlsx" -> FileFormatInfo(
                ext,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "Excel Spreadsheet (XLSX)",
                "Office Open XML Spreadsheet",
                false
            )
            "ppt" -> FileFormatInfo(ext, "application/vnd.ms-powerpoint", "PowerPoint Presentation", "Microsoft PowerPoint 97-2003", false)
            "pptx" -> FileFormatInfo(
                ext,
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "PowerPoint Presentation (PPTX)",
                "Office Open XML Presentation",
                false
            )
            "epub" -> FileFormatInfo(ext, "application/epub+zip", "EPUB eBook", "Electronic Publication", false)
            // Archives
            "zip" -> FileFormatInfo(ext, "application/zip", "ZIP Archive", "Standard compressed archive", true)
            "tar" -> FileFormatInfo(ext, "application/x-tar", "TAR Archive", "Tape Archive", true)
            "gz" -> FileFormatInfo(ext, "application/gzip", "GZIP Archive", "GNU Zip compressed file", true)
            "rar" -> FileFormatInfo(ext, "application/x-rar-compressed", "RAR Archive", "Roshal Archive", false)
            "7z" -> FileFormatInfo(ext, "application/x-7z-compressed", "7-Zip Archive", "7-Zip compressed file", false)
            // Text & Code
            "txt" -> FileFormatInfo(ext, "text/plain", "Plain Text", "Raw unformatted text", true)
            "json" -> FileFormatInfo(ext, "application/json", "JSON Data", "JavaScript Object Notation", true)
            "xml" -> FileFormatInfo(ext, "application/xml", "XML Document", "Extensible Markup Language", true)
            "html", "htm" -> FileFormatInfo(ext, "text/html", "HTML Document", "HyperText Markup Language", true)
            "md", "markdown" -> FileFormatInfo(ext, "text/markdown", "Markdown File", "Formatted text markup", true)
            "csv" -> FileFormatInfo(ext, "text/csv", "CSV Spreadsheet", "Comma-Separated Values", true)
            "log" -> FileFormatInfo(ext, "text/plain", "Log File", "System/App log records", true)
            "kt", "java" -> FileFormatInfo(ext, "text/x-kotlin", "Source Code", "Application source code", true)
            "py", "js", "ts", "css", "sql", "sh" -> FileFormatInfo(ext, "text/plain", "Source Code / Script", "Code script file", true)
            else -> {
                val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
                FileFormatInfo(
                    extension = if (ext.isEmpty()) "bin" else ext,
                    mimeType = mime,
                    categoryName = if (ext.isEmpty()) "Binary Data" else "${ext.uppercase()} File",
                    description = "Generic file format ($mime)",
                    canPreviewInternally = false
                )
            }
        }
    }

    /**
     * Inspects an APK package file and extracts application name, icon, version and permissions.
     */
    fun getApkInfo(context: Context, file: File): ApkInfo? {
        if (!file.exists() || !file.name.lowercase().endsWith(".apk")) return null
        return try {
            val pm = context.packageManager
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_PERMISSIONS
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_PERMISSIONS
            }
            val packageInfo: PackageInfo? = pm.getPackageArchiveInfo(file.absolutePath, flags)
            if (packageInfo != null) {
                val appInfo = packageInfo.applicationInfo
                appInfo?.sourceDir = file.absolutePath
                appInfo?.publicSourceDir = file.absolutePath

                val appName = if (appInfo != null) {
                    try {
                        pm.getApplicationLabel(appInfo).toString()
                    } catch (e: Exception) {
                        file.nameWithoutExtension
                    }
                } else {
                    file.nameWithoutExtension
                }

                val icon = if (appInfo != null) {
                    try {
                        pm.getApplicationIcon(appInfo)
                    } catch (e: Exception) {
                        null
                    }
                } else {
                    null
                }

                val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode.toLong()
                }

                val targetSdk = appInfo?.targetSdkVersion ?: 0
                val minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    appInfo?.minSdkVersion ?: 14
                } else {
                    14
                }

                // Check if this package is already installed on the user's system
                var isInstalled = false
                var installedVersion: String? = null
                try {
                    val installedInfo = pm.getPackageInfo(packageInfo.packageName, 0)
                    isInstalled = true
                    installedVersion = installedInfo.versionName
                } catch (e: PackageManager.NameNotFoundException) {
                    isInstalled = false
                }

                ApkInfo(
                    appName = if (appName.isBlank()) file.nameWithoutExtension else appName,
                    packageName = packageInfo.packageName,
                    versionName = packageInfo.versionName ?: "1.0",
                    versionCode = versionCode,
                    minSdkVersion = minSdk,
                    targetSdkVersion = targetSdk,
                    icon = icon,
                    permissions = packageInfo.requestedPermissions?.toList() ?: emptyList(),
                    fileSize = file.length(),
                    isAlreadyInstalled = isInstalled,
                    installedVersionName = installedVersion
                )
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Checks whether the app has permission to install packages from unknown sources.
     */
    fun canInstallApks(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Navigates the user to System Settings to grant "Install Unknown Apps" permission for Folder.
     */
    fun openUnknownAppsSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                val fallbackIntent = Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            }
        }
    }

    /**
     * Launches Android Package Installer for an APK file.
     */
    fun installApk(context: Context, file: File) {
        if (!file.exists()) {
            Toast.makeText(context, "APK file not found", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(installIntent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(
                context,
                "Unable to start package installer: ${e.localizedMessage ?: "Unknown error"}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Returns a list of installed apps on the device that can open this specific file format.
     */
    fun getCompatibleApps(context: Context, file: File): List<AppTarget> {
        if (!file.exists() || file.isDirectory) return emptyList()

        return try {
            val formatInfo = identifyFormat(file)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, formatInfo.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val pm = context.packageManager
            val matches = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)

            matches.mapNotNull { resolveInfo ->
                val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
                val appLabel = resolveInfo.loadLabel(pm).toString()
                val icon = resolveInfo.loadIcon(pm)

                AppTarget(
                    appName = appLabel,
                    packageName = activityInfo.packageName,
                    activityName = activityInfo.name,
                    icon = icon
                )
            }.distinctBy { it.packageName }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Opens a file using either an explicitly chosen app or Android's system chooser with exact MIME type.
     */
    fun openFile(
        context: Context,
        file: File,
        targetApp: AppTarget? = null,
        forceMimeType: String? = null
    ) {
        if (!file.exists()) {
            Toast.makeText(context, "File does not exist", Toast.LENGTH_SHORT).show()
            return
        }

        val formatInfo = identifyFormat(file)
        val mime = forceMimeType ?: formatInfo.mimeType

        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (targetApp != null) {
                    component = ComponentName(targetApp.packageName, targetApp.activityName)
                }
            }

            if (targetApp != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } else {
                val chooser = Intent.createChooser(intent, "Open ${file.name} with")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(
                context,
                "No app found to open this file format (${formatInfo.categoryName})",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
