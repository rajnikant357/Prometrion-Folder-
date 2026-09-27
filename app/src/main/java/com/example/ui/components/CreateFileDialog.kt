package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class FileTypeOption(
    val id: String,
    val label: String,
    val extension: String,
    val icon: ImageVector,
    val template: String = ""
)

@Composable
fun CreateFileDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, fileType: String, content: String) -> Unit
) {
    val options = remember {
        listOf(
            FileTypeOption("folder", "Folder", "", Icons.Default.Folder),
            FileTypeOption("txt", "Text File (.txt)", ".txt", Icons.Default.Notes),
            FileTypeOption("md", "Markdown (.md)", ".md", Icons.Default.Description, "# New Document\n\nCreated with Folder.\n"),
            FileTypeOption("json", "JSON (.json)", ".json", Icons.Default.Code, "{\n  \"created\": true\n}"),
            FileTypeOption("csv", "CSV Table (.csv)", ".csv", Icons.Default.TableChart, "id,name,date\n1,Sample,2026-09-27\n")
        )
    }

    var selectedOption by remember { mutableStateOf(options[0]) }
    var fileName by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CreateNewFolder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create New")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Select type:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    options.forEach { opt ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedOption = opt
                                    errorMessage = null
                                },
                            shape = RoundedCornerShape(8.dp),
                            color = if (selectedOption.id == opt.id)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedOption.id == opt.id,
                                    onClick = {
                                        selectedOption = opt
                                        errorMessage = null
                                    }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = opt.icon,
                                    contentDescription = null,
                                    tint = if (selectedOption.id == opt.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = opt.label,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (selectedOption.id == opt.id) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = fileName,
                    onValueChange = {
                        fileName = it
                        errorMessage = null
                    },
                    label = { Text("Name ${if (selectedOption.extension.isNotEmpty()) "(${selectedOption.extension})" else ""}") },
                    singleLine = true,
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { { Text(it) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_file_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val clean = fileName.trim()
                    if (clean.isBlank()) {
                        errorMessage = "Name cannot be empty"
                        return@Button
                    }
                    if (clean.contains("/") || clean.contains("\\")) {
                        errorMessage = "Invalid characters in name"
                        return@Button
                    }
                    onConfirm(clean, selectedOption.id, selectedOption.template)
                },
                modifier = Modifier.testTag("create_file_confirm_button")
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
