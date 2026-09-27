package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    viewModel: FileManagerViewModel,
    onNavigateBack: () -> Unit,
    onOpenFile: (File) -> Unit,
    onNavigateToBrowseForEncryption: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val vaultState by viewModel.vaultState.collectAsState()
    val vaultItems by viewModel.vaultDbItems.collectAsState()

    var pinInput by remember { mutableStateOf("") }
    var confirmPinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    var selectedItemForExport by remember { mutableStateOf<VaultEntity?>(null) }
    var selectedItemForDelete by remember { mutableStateOf<VaultEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Private Vault",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                },
                navigationIcon = {
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
                },
                actions = {
                    if (vaultState.isUnlocked) {
                        IconButton(
                            onClick = { viewModel.lockVault() },
                            modifier = Modifier.testTag("vault_lock_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Lock Vault",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
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
                                onOpenFile(tempFile)
                            } else {
                                viewModel.emitMessage("Failed to decrypt for preview")
                            }
                        },
                        onExport = { item -> selectedItemForExport = item },
                        onDelete = { item -> selectedItemForDelete = item }
                    )
                }
            }
        }
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

@Composable
private fun VaultUnlockedDashboard(
    items: List<VaultEntity>,
    onEncryptNew: () -> Unit,
    onPreview: (VaultEntity) -> Unit,
    onExport: (VaultEntity) -> Unit,
    onDelete: (VaultEntity) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Vault Header Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Encrypted Vault Active",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${items.size} documents encrypted with AES-256-GCM",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = onEncryptNew,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("vault_add_file_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add File")
                }
            }
        }

        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Your encrypted vault is empty",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Move sensitive documents here to encrypt them",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("vault_item_${item.originalFileName.replace(" ", "_")}"),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Description,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.originalFileName,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${FileItem.formatFileSize(item.originalSize)} • ${dateFormat.format(Date(item.encryptedAt))}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(
                                onClick = { onPreview(item) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("vault_preview_${item.originalFileName}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Visibility,
                                    contentDescription = "Preview",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            IconButton(
                                onClick = { onExport(item) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("vault_export_${item.originalFileName}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FileDownload,
                                    contentDescription = "Export Decrypted",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            IconButton(
                                onClick = { onDelete(item) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("vault_delete_${item.originalFileName}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
