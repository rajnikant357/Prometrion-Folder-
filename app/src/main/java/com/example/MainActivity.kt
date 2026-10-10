package com.example

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.data.model.FileCategory
import com.example.data.local.VaultEntity
import com.example.ui.screens.AboutScreen
import com.example.ui.screens.CategoryScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DuplicatesScreen
import com.example.ui.screens.FileBrowserScreen
import com.example.ui.screens.FilePreviewScreen
import com.example.ui.screens.PermissionsScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.StorageAnalyzerScreen
import com.example.ui.screens.TrashScreen
import com.example.ui.screens.VaultScreen
import com.example.ui.theme.VaultFilesTheme
import com.example.ui.viewmodel.AppThemeMode
import com.example.ui.viewmodel.FileManagerViewModel
import kotlinx.coroutines.flow.collectLatest
import java.io.File

enum class Screen {
    PERMISSIONS,
    DASHBOARD,
    FILE_BROWSER,
    CATEGORY,
    SEARCH,
    VAULT,
    PREVIEW,
    ANALYZER,
    TRASH,
    DUPLICATES,
    ABOUT
}


class MainActivity : ComponentActivity() {

    private val viewModel: FileManagerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val isDark = when (themeMode) {
                AppThemeMode.DARK -> true
                AppThemeMode.LIGHT -> false
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            VaultFilesTheme(darkTheme = isDark) {
                VaultFilesApp(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val granted = viewModel.checkStoragePermission()
        viewModel.updatePermissionStatus(granted)
    }
}

@Composable
fun VaultFilesApp(viewModel: FileManagerViewModel) {
    val hasStoragePermission by viewModel.hasStoragePermission.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var currentScreen by remember { mutableStateOf(Screen.DASHBOARD) }
    var returnScreenForPreview by remember { mutableStateOf(Screen.DASHBOARD) }
    var returnScreenForBrowser by remember { mutableStateOf(Screen.DASHBOARD) }
    var previewFile by remember { mutableStateOf<File?>(null) }
    var previewVaultEntity by remember { mutableStateOf<VaultEntity?>(null) }
    var activeCategory by remember { mutableStateOf<FileCategory?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.snackbarMessage.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        val modifier = Modifier.padding(innerPadding)

        if (!hasStoragePermission) {
            PermissionsScreen(
                onPermissionGranted = {
                    viewModel.updatePermissionStatus(true)
                    currentScreen = Screen.DASHBOARD
                },
                modifier = modifier
            )
        } else {
            when (currentScreen) {
                Screen.DASHBOARD -> {
                    val activity = LocalContext.current as? Activity
                    BackHandler {
                        activity?.finish()
                    }
                    DashboardScreen(
                        viewModel = viewModel,
                        onNavigateToCategory = { cat ->
                            activeCategory = cat
                            viewModel.selectCategory(cat)
                            currentScreen = Screen.CATEGORY
                        },
                        onNavigateToFolder = { folder ->
                            returnScreenForBrowser = Screen.DASHBOARD
                            viewModel.navigateToDirectory(folder)
                            currentScreen = Screen.FILE_BROWSER
                        },
                        onNavigateToSearch = { currentScreen = Screen.SEARCH },
                        onNavigateToVault = { currentScreen = Screen.VAULT },
                        onNavigateToAnalyzer = { currentScreen = Screen.ANALYZER },
                        onNavigateToTrash = { currentScreen = Screen.TRASH },
                        onNavigateToDuplicates = { currentScreen = Screen.DUPLICATES },
                        onNavigateToAbout = { currentScreen = Screen.ABOUT },
                        onOpenFile = { file ->
                            returnScreenForPreview = Screen.DASHBOARD
                            previewFile = file
                            previewVaultEntity = null
                            currentScreen = Screen.PREVIEW
                        }
                    )
                }


                Screen.FILE_BROWSER -> {
                    FileBrowserScreen(
                        viewModel = viewModel,
                        onNavigateBack = { currentScreen = returnScreenForBrowser },
                        onOpenFile = { file ->
                            returnScreenForPreview = Screen.FILE_BROWSER
                            previewFile = file
                            previewVaultEntity = null
                            currentScreen = Screen.PREVIEW
                        }
                    )
                }

                Screen.CATEGORY -> {
                    activeCategory?.let { cat ->
                        CategoryScreen(
                            category = cat,
                            viewModel = viewModel,
                            onNavigateBack = { currentScreen = Screen.DASHBOARD },
                            onOpenFile = { file ->
                                returnScreenForPreview = Screen.CATEGORY
                                previewFile = file
                                previewVaultEntity = null
                                currentScreen = Screen.PREVIEW
                            }
                        )
                    } ?: run {
                        currentScreen = Screen.DASHBOARD
                    }
                }

                Screen.SEARCH -> {
                    SearchScreen(
                        viewModel = viewModel,
                        onNavigateBack = { currentScreen = Screen.DASHBOARD },
                        onOpenFile = { file ->
                            returnScreenForPreview = Screen.SEARCH
                            previewFile = file
                            previewVaultEntity = null
                            currentScreen = Screen.PREVIEW
                        }
                    )
                }

                Screen.VAULT -> {
                    VaultScreen(
                        viewModel = viewModel,
                        onNavigateBack = { currentScreen = Screen.DASHBOARD },
                        onOpenFile = { file, entity ->
                            returnScreenForPreview = Screen.VAULT
                            previewFile = file
                            previewVaultEntity = entity
                            currentScreen = Screen.PREVIEW
                        },
                        onNavigateToBrowseForEncryption = {
                            returnScreenForBrowser = Screen.VAULT
                            currentScreen = Screen.FILE_BROWSER
                        }
                    )
                }

                Screen.PREVIEW -> {
                    BackHandler {
                        currentScreen = returnScreenForPreview
                        previewVaultEntity = null
                    }
                    previewFile?.let { file ->
                        val isVault = returnScreenForPreview == Screen.VAULT || previewVaultEntity != null
                        FilePreviewScreen(
                            file = file,
                            repository = viewModel.repository,
                            onNavigateBack = {
                                currentScreen = returnScreenForPreview
                                previewVaultEntity = null
                            },
                            onMoveToVault = { f ->
                                viewModel.moveFileToVault(f)
                                currentScreen = returnScreenForPreview
                                previewVaultEntity = null
                            },
                            isVaultItem = isVault,
                            vaultItem = previewVaultEntity,
                            onUnlockVaultItem = { restoreToOriginal ->
                                previewVaultEntity?.let { entity ->
                                    viewModel.unlockAndRestoreFromVault(entity, restoreToOriginal = restoreToOriginal)
                                }
                                try { file.delete() } catch (_: Exception) {}
                                currentScreen = returnScreenForPreview
                                previewVaultEntity = null
                            },
                            onDeleteVaultItem = {
                                previewVaultEntity?.let { entity ->
                                    viewModel.deleteFromVault(entity)
                                }
                                try { file.delete() } catch (_: Exception) {}
                                currentScreen = returnScreenForPreview
                                previewVaultEntity = null
                            }
                        )
                    } ?: run {
                        previewVaultEntity = null
                        currentScreen = Screen.DASHBOARD
                    }
                }

                Screen.ANALYZER -> {
                    StorageAnalyzerScreen(
                        viewModel = viewModel,
                        onNavigateBack = { currentScreen = Screen.DASHBOARD },
                        onOpenFile = { file ->
                            returnScreenForPreview = Screen.ANALYZER
                            previewFile = file
                            previewVaultEntity = null
                            currentScreen = Screen.PREVIEW
                        }
                    )
                }

                Screen.TRASH -> {
                    TrashScreen(
                        viewModel = viewModel,
                        onNavigateBack = { currentScreen = Screen.DASHBOARD }
                    )
                }

                Screen.DUPLICATES -> {
                    DuplicatesScreen(
                        viewModel = viewModel,
                        onNavigateBack = { currentScreen = Screen.DASHBOARD },
                        onOpenFile = { file ->
                            returnScreenForPreview = Screen.DUPLICATES
                            previewFile = file
                            previewVaultEntity = null
                            currentScreen = Screen.PREVIEW
                        }
                    )
                }

                Screen.PERMISSIONS -> {

                    PermissionsScreen(
                        onPermissionGranted = {
                            viewModel.updatePermissionStatus(true)
                            currentScreen = Screen.DASHBOARD
                        },
                        modifier = modifier
                    )
                }

                Screen.ABOUT -> {
                    AboutScreen(
                        onNavigateBack = { currentScreen = Screen.DASHBOARD }
                    )
                }
            }
        }
    }
}
