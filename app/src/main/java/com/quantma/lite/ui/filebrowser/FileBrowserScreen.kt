package com.quantma.lite.ui.filebrowser

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.quantma.lite.R
import com.quantma.lite.ui.theme.AccentBlue
import com.quantma.lite.ui.theme.AccentGreen
import com.quantma.lite.ui.theme.AccentYellow
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    viewModel: FileBrowserViewModel = hiltViewModel(),
    onFileSelected: (String) -> Unit = {},
    onOpenGit: (String) -> Unit = {}
) {
    val currentPath by viewModel.currentPath.collectAsState()
    val files by viewModel.files.collectAsState()
    val hasPermission by viewModel.hasStoragePermission.collectAsState()
    val isInGitRepo by viewModel.isInGitRepo.collectAsState()
    val gitRootPath by viewModel.gitRootPath.collectAsState()
    val clipboard by viewModel.clipboard.collectAsState()
    val contextMenuFile by viewModel.contextMenuFile.collectAsState()

    // Dialog states — all remember calls at top level (Compose rules)
    var showDeleteDialog by remember { mutableStateOf<File?>(null) }
    var showRenameDialog by remember { mutableStateOf<File?>(null) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf<File?>(null) }
    var renameText by remember { mutableStateOf("") }
    var folderNameText by remember { mutableStateOf("") }

    // Sync rename text when target file changes
    LaunchedEffect(showRenameDialog) {
        renameText = showRenameDialog?.name ?: ""
    }
    // Clear folder name when dialog opens
    LaunchedEffect(showNewFolderDialog) {
        if (showNewFolderDialog) folderNameText = ""
    }

    // Check permission on launch
    LaunchedEffect(Unit) {
        viewModel.setStoragePermission(Environment.isExternalStorageManager())
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.setStoragePermission(Environment.isExternalStorageManager())
    }

    // ── Dialogs ──

    // Delete confirmation
    if (showDeleteDialog != null) {
        val file = showDeleteDialog!!
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text(stringResource(R.string.fb_delete_confirm, file.name)) },
            text = {
                if (file.isDirectory) {
                    Text(stringResource(R.string.fb_delete_folder_warn))
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.deleteFile(file.absolutePath)
                    showDeleteDialog = null
                }) { Text(stringResource(R.string.fb_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text(stringResource(R.string.fb_cancel))
                }
            }
        )
    }

    // Rename dialog
    if (showRenameDialog != null) {
        val file = showRenameDialog!!
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            title = { Text(stringResource(R.string.fb_rename_title)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text(stringResource(R.string.fb_new_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.rename(file.absolutePath, renameText)
                        showRenameDialog = null
                    },
                    enabled = renameText.isNotBlank() && renameText != file.name
                ) { Text(stringResource(R.string.fb_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = null }) {
                    Text(stringResource(R.string.fb_cancel))
                }
            }
        )
    }

    // New folder dialog
    if (showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text(stringResource(R.string.fb_new_folder_title)) },
            text = {
                OutlinedTextField(
                    value = folderNameText,
                    onValueChange = { folderNameText = it },
                    label = { Text(stringResource(R.string.fb_folder_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createFolder(folderNameText)
                        showNewFolderDialog = false
                    },
                    enabled = folderNameText.isNotBlank()
                ) { Text(stringResource(R.string.fb_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) {
                    Text(stringResource(R.string.fb_cancel))
                }
            }
        )
    }

    // Info dialog
    showInfoDialog?.let { file ->
        val info = remember(file) { viewModel.getFileInfo(file) }
        val sizeText = if (info.size >= 1024 * 1024) {
            "%.1f MB".format(info.size / (1024.0 * 1024.0))
        } else {
            "%.1f KB".format(info.size / 1024.0)
        }
        AlertDialog(
            onDismissRequest = { showInfoDialog = null },
            title = { Text(info.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.fb_size_info, sizeText))
                    Text(stringResource(R.string.fb_modified_info, info.lastModified))
                    Text(
                        stringResource(R.string.fb_path_info, info.path),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = null }) {
                    Text("OK")
                }
            }
        )
    }

    // ── Context Menu BottomSheet ──
    contextMenuFile?.let { file ->
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { viewModel.dismissContextMenu() },
            sheetState = sheetState
        ) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                // File header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = getFileIcon(file),
                        contentDescription = null,
                        tint = getFileIconTint(file),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = file.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!file.isDirectory) {
                            val sizeMb = file.length() / (1024.0 * 1024.0)
                            Text(
                                text = if (sizeMb >= 1.0) "%.1f MB".format(sizeMb)
                                else "%.1f KB".format(file.length() / 1024.0),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Actions
                ContextMenuItem(
                    icon = Icons.Default.ContentCopy,
                    label = stringResource(R.string.fb_copy),
                    onClick = { viewModel.copyToClipboard(file.absolutePath) }
                )
                ContextMenuItem(
                    icon = Icons.Default.ContentCut,
                    label = stringResource(R.string.fb_cut),
                    onClick = { viewModel.cutToClipboard(file.absolutePath) }
                )
                ContextMenuItem(
                    icon = Icons.Default.DriveFileRenameOutline,
                    label = stringResource(R.string.fb_rename),
                    onClick = {
                        viewModel.dismissContextMenu()
                        showRenameDialog = file
                    }
                )
                ContextMenuItem(
                    icon = Icons.Default.Delete,
                    label = stringResource(R.string.fb_delete),
                    onClick = {
                        viewModel.dismissContextMenu()
                        showDeleteDialog = file
                    },
                    tint = MaterialTheme.colorScheme.error
                )
                ContextMenuItem(
                    icon = Icons.Default.Info,
                    label = stringResource(R.string.fb_file_info),
                    onClick = {
                        viewModel.dismissContextMenu()
                        showInfoDialog = file
                    }
                )
            }
        }
    }

    // ── Main Layout ──
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        stringResource(R.string.files_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = viewModel.getDisplayPath(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = { viewModel.navigateUp() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.up)
                    )
                }
            },
            actions = {
                IconButton(onClick = { showNewFolderDialog = true }) {
                    Icon(
                        Icons.Default.CreateNewFolder,
                        contentDescription = stringResource(R.string.fb_create_folder),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = { viewModel.refresh() }) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.refresh),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        if (!hasPermission) {
            // Permission request screen
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.permission_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.permission_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:com.quantma.lite")
                        )
                        permissionLauncher.launch(intent)
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.grant_permission))
                }
            }
        } else {
            // Quick Access Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = { viewModel.navigateToDownloads() },
                    label = { Text(stringResource(R.string.fb_downloads)) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
                AssistChip(
                    onClick = { viewModel.navigateToRoot() },
                    label = { Text(stringResource(R.string.fb_home)) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Home,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )

                // Clipboard indicator
                clipboard?.let { entry ->
                    val clipName = File(entry.path).name
                    val label = when (entry.operation) {
                        ClipboardOp.COPY -> clipName
                        ClipboardOp.CUT -> clipName
                    }
                    AssistChip(
                        onClick = { viewModel.clearClipboard() },
                        label = {
                            Text(
                                label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = {
                            Icon(
                                if (entry.operation == ClipboardOp.COPY)
                                    Icons.Default.ContentCopy
                                else Icons.Default.ContentCut,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                if (files.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.empty_folder),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(files, key = { _, f -> f.absolutePath }) { index, file ->
                            FileItem(
                                file = file,
                                isEven = index % 2 == 0,
                                onClick = {
                                    if (file.isDirectory) {
                                        viewModel.navigateTo(file.absolutePath)
                                    } else {
                                        onFileSelected(file.absolutePath)
                                    }
                                },
                                onLongClick = { viewModel.showContextMenu(file) }
                            )
                        }
                    }
                }

                // Paste FAB
                if (clipboard != null) {
                    FloatingActionButton(
                        onClick = { viewModel.paste() },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Icon(
                            Icons.Default.ContentPaste,
                            contentDescription = stringResource(R.string.fb_paste),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Git status bar
            Surface(
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isInGitRepo) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Code,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Git",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        FilledTonalButton(
                            onClick = { gitRootPath?.let { onOpenGit(it) } },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                stringResource(R.string.open_git),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.not_git_repo),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = { viewModel.initGitRepo() },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                Icons.Default.CreateNewFolder,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                stringResource(R.string.init_git_repo),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Context Menu Item ──

@Composable
private fun ContextMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = tint
        )
    }
}

// ── File Item ──

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileItem(
    file: File,
    isEven: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val bgColor = if (isEven) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = getFileIcon(file),
            contentDescription = null,
            tint = getFileIconTint(file),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!file.isDirectory) {
                val sizeMb = file.length() / (1024.0 * 1024.0)
                val sizeText = if (sizeMb >= 1.0) {
                    "%.1f MB".format(sizeMb)
                } else {
                    "%.1f KB".format(file.length() / 1024.0)
                }
                Text(
                    text = sizeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── File Icons ──

@Composable
private fun getFileIconTint(file: File): androidx.compose.ui.graphics.Color {
    if (file.isDirectory) return MaterialTheme.colorScheme.primary
    return when (file.extension.lowercase()) {
        "kt", "kts", "java", "py", "js", "ts", "jsx", "tsx",
        "c", "cpp", "h", "hpp", "rs", "go", "rb", "swift",
        "dart", "lua", "sh", "bash", "zsh", "pl", "r",
        "cs", "scala", "php", "vue", "svelte" -> AccentBlue
        "json", "xml", "yaml", "yml", "toml", "ini", "cfg",
        "properties", "gradle", "cmake", "makefile" -> AccentYellow
        "png", "jpg", "jpeg", "gif", "bmp", "webp", "svg", "ico" -> AccentGreen
        "gguf" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

private fun getFileIcon(file: File): ImageVector {
    if (file.isDirectory) return Icons.Default.Folder
    return when (file.extension.lowercase()) {
        "kt", "kts", "java", "py", "js", "ts", "jsx", "tsx",
        "c", "cpp", "h", "hpp", "rs", "go", "rb", "swift",
        "dart", "lua", "sh", "bash", "zsh", "pl", "r",
        "cs", "scala", "php", "vue", "svelte" ->
            Icons.Default.Code
        "json", "xml", "yaml", "yml", "toml", "ini", "cfg",
        "properties", "gradle", "cmake", "makefile" ->
            Icons.Default.Description
        "png", "jpg", "jpeg", "gif", "bmp", "webp", "svg", "ico" ->
            Icons.Default.Image
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }
}
