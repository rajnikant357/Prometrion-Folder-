package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.FileItem
import com.example.data.model.FileType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FileListItem(
    item: FileItem,
    onClick: () -> Unit,
    onMoveToVault: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onCompress: () -> Unit,
    onExtract: () -> Unit,
    onProperties: () -> Unit,
    onShare: () -> Unit = {},
    onOpenWith: () -> Unit = {},
    onInstallApk: (() -> Unit)? = null,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy  HH:mm", Locale.getDefault()) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("file_list_item_${item.name.replace(" ", "_")}")
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent
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


            // Thumbnail / Icon
            FileThumbnail(item = item, size = 40.dp)


            Spacer(modifier = Modifier.width(14.dp))

            // File Name & Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (item.isDirectory) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val subText = if (item.isDirectory) {
                        "${item.itemCount} items"
                    } else {
                        FileItem.formatFileSize(item.size)
                    }
                    Text(
                        text = subText,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = dateFormat.format(Date(item.lastModified)),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Options Overflow Menu
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("file_options_${item.name.replace(" ", "_")}")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    if (!item.isDirectory) {
                        DropdownMenuItem(
                            text = { Text("Move to Private Vault") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onMoveToVault()
                            }
                        )
                    }

                    if (item.fileType == FileType.ARCHIVE) {
                        DropdownMenuItem(
                            text = { Text("Extract Archive") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.FolderZip,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onExtract()
                            }
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Compress to ZIP") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Archive,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onCompress()
                            }
                        )
                    }

                    if (!item.isDirectory) {
                        if (item.fileType == FileType.APK || item.extension == "apk") {
                            DropdownMenuItem(
                                text = { Text("Install App") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Android,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onInstallApk?.invoke()
                                },
                                modifier = Modifier.testTag("file_action_install_${item.name.replace(" ", "_")}")
                            )
                        }

                        DropdownMenuItem(
                            text = { Text("Open with...") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onOpenWith()
                            },
                            modifier = Modifier.testTag("file_action_open_with_${item.name.replace(" ", "_")}")
                        )

                        DropdownMenuItem(
                            text = { Text("Share") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onShare()
                            },
                            modifier = Modifier.testTag("file_action_share_${item.name.replace(" ", "_")}")
                        )
                    }

                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.DriveFileRenameOutline,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Copy") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onCopy()
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Cut") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.ContentCut,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onCut()
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Properties") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onProperties()
                        }
                    )

                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Delete",
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun FileThumbnail(item: FileItem, size: Dp) {
    if (item.fileType == FileType.IMAGE && item.file.exists()) {
        AsyncImage(
            model = item.file,
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(6.dp))
        )
    } else {
        val (icon, iconTint) = getIconAndColor(item)
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(size)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = item.fileType.name,
                    tint = iconTint,
                    modifier = Modifier.size(size * 0.55f)
                )
            }
        }
    }
}

@Composable
fun getIconAndColor(item: FileItem): Pair<ImageVector, Color> {
    return when (item.fileType) {
        FileType.FOLDER -> Pair(Icons.Default.Folder, MaterialTheme.colorScheme.primary)
        FileType.IMAGE -> Pair(Icons.Default.Image, MaterialTheme.colorScheme.onSurfaceVariant)
        FileType.VIDEO -> Pair(Icons.Default.Movie, MaterialTheme.colorScheme.onSurfaceVariant)
        FileType.AUDIO -> Pair(Icons.Default.AudioFile, MaterialTheme.colorScheme.onSurfaceVariant)
        FileType.PDF -> Pair(Icons.Default.PictureAsPdf, MaterialTheme.colorScheme.error)
        FileType.TEXT, FileType.DOCUMENT -> Pair(Icons.Default.Description, MaterialTheme.colorScheme.onSurfaceVariant)
        FileType.APK -> Pair(Icons.Default.Android, MaterialTheme.colorScheme.onSurfaceVariant)
        FileType.ARCHIVE -> Pair(Icons.Default.FolderZip, MaterialTheme.colorScheme.onSurfaceVariant)
        FileType.UNKNOWN -> Pair(Icons.Default.InsertDriveFile, MaterialTheme.colorScheme.outline)
    }
}
