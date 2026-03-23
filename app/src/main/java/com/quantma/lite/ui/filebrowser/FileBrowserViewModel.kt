package com.quantma.lite.ui.filebrowser

import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantma.lite.data.local.preferences.SettingsDataStore
import com.quantma.lite.domain.repository.FileRepository
import com.quantma.lite.domain.repository.GitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

enum class ClipboardOp { COPY, CUT }

data class ClipboardEntry(
    val path: String,
    val operation: ClipboardOp
)

data class FileInfo(
    val name: String,
    val path: String,
    val size: Long,
    val isDirectory: Boolean,
    val lastModified: String
)

@HiltViewModel
class FileBrowserViewModel @Inject constructor(
    private val fileRepository: FileRepository,
    private val gitRepository: GitRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _rootPath = MutableStateFlow(
        Environment.getExternalStorageDirectory().absolutePath
    )

    private val _currentPath = MutableStateFlow(
        Environment.getExternalStorageDirectory().absolutePath
    )
    val currentPath: StateFlow<String> = _currentPath.asStateFlow()

    private val _files = MutableStateFlow<List<File>>(emptyList())
    val files: StateFlow<List<File>> = _files.asStateFlow()

    private val _hasStoragePermission = MutableStateFlow(false)
    val hasStoragePermission: StateFlow<Boolean> = _hasStoragePermission.asStateFlow()

    private val _isInGitRepo = MutableStateFlow(false)
    val isInGitRepo: StateFlow<Boolean> = _isInGitRepo.asStateFlow()

    private val _gitRootPath = MutableStateFlow<String?>(null)
    val gitRootPath: StateFlow<String?> = _gitRootPath.asStateFlow()

    // Clipboard state
    private val _clipboard = MutableStateFlow<ClipboardEntry?>(null)
    val clipboard: StateFlow<ClipboardEntry?> = _clipboard.asStateFlow()

    // Context menu
    private val _contextMenuFile = MutableStateFlow<File?>(null)
    val contextMenuFile: StateFlow<File?> = _contextMenuFile.asStateFlow()

    // Operation result message
    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val pathStack = mutableListOf<String>()

    // Pre-approved directories outside root
    private val allowedPaths = mutableListOf<String>()

    init {
        viewModelScope.launch {
            val workDir = settingsDataStore.agentWorkingDir.first()
            val dir = File(workDir)
            if (dir.exists() && dir.isDirectory) {
                _rootPath.value = workDir
                _currentPath.value = workDir
            }
            // Always allow Downloads access
            val downloads = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            )
            if (downloads.exists()) {
                allowedPaths.add(downloads.absolutePath)
            }
            refresh()
        }
    }

    fun setStoragePermission(granted: Boolean) {
        _hasStoragePermission.value = granted
        if (granted) refresh()
    }

    fun navigateTo(path: String) {
        val isAllowed = path.startsWith(_rootPath.value) ||
            allowedPaths.any { path.startsWith(it) }
        if (!isAllowed) return
        pathStack.add(_currentPath.value)
        _currentPath.value = path
        refresh()
    }

    fun navigateUp(): Boolean {
        val current = _currentPath.value
        val root = _rootPath.value

        // If in an allowed path (e.g. Downloads), back goes to root
        val inAllowedPath = allowedPaths.any { current.startsWith(it) }
        if (inAllowedPath) {
            val allowedRoot = allowedPaths.first { current.startsWith(it) }
            if (current == allowedRoot) {
                // At the root of allowed path, go back to main root
                pathStack.add(current)
                _currentPath.value = root
                refresh()
                return true
            }
            val parent = File(current).parentFile ?: return false
            if (parent.absolutePath.startsWith(allowedRoot)) {
                pathStack.add(current)
                _currentPath.value = parent.absolutePath
                refresh()
                return true
            }
            pathStack.add(current)
            _currentPath.value = root
            refresh()
            return true
        }

        if (current == root) return false
        val parent = File(current).parentFile ?: return false
        val target = if (parent.absolutePath.length >= root.length &&
            parent.absolutePath.startsWith(root)) {
            parent.absolutePath
        } else {
            root
        }
        if (target == current) return false
        pathStack.add(current)
        _currentPath.value = target
        refresh()
        return true
    }

    fun goBack(): Boolean {
        if (pathStack.isNotEmpty()) {
            val target = pathStack.removeAt(pathStack.lastIndex)
            val isAllowed = target.startsWith(_rootPath.value) ||
                allowedPaths.any { target.startsWith(it) }
            _currentPath.value = if (isAllowed) target else _rootPath.value
            refresh()
            return true
        }
        return false
    }

    fun refresh() {
        _files.value = fileRepository.listDirectory(_currentPath.value)
        val gitRoot = gitRepository.findGitRoot(_currentPath.value)
        _isInGitRepo.value = gitRoot != null
        _gitRootPath.value = gitRoot
    }

    // --- Context Menu ---

    fun showContextMenu(file: File) {
        _contextMenuFile.value = file
    }

    fun dismissContextMenu() {
        _contextMenuFile.value = null
    }

    // --- Clipboard Operations ---

    fun copyToClipboard(path: String) {
        _clipboard.value = ClipboardEntry(path, ClipboardOp.COPY)
        _contextMenuFile.value = null
        _snackbarMessage.value = File(path).name
    }

    fun cutToClipboard(path: String) {
        _clipboard.value = ClipboardEntry(path, ClipboardOp.CUT)
        _contextMenuFile.value = null
        _snackbarMessage.value = File(path).name
    }

    fun clearClipboard() {
        _clipboard.value = null
    }

    fun paste() {
        val entry = _clipboard.value ?: return
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                when (entry.operation) {
                    ClipboardOp.COPY -> fileRepository.copyFile(entry.path, _currentPath.value)
                    ClipboardOp.CUT -> fileRepository.moveFile(entry.path, _currentPath.value)
                }
            }
            if (success) {
                if (entry.operation == ClipboardOp.CUT) {
                    _clipboard.value = null
                }
            }
            refresh()
        }
    }

    // --- File Operations ---

    fun deleteFile(path: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                fileRepository.deleteFile(path)
            }
            _contextMenuFile.value = null
            refresh()
        }
    }

    fun rename(path: String, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                fileRepository.renameFile(path, newName.trim())
            }
            _contextMenuFile.value = null
            refresh()
        }
    }

    fun createFolder(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val path = File(_currentPath.value, name.trim()).absolutePath
                fileRepository.createDirectory(path)
            }
            refresh()
        }
    }

    fun getFileInfo(file: File): FileInfo {
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        return FileInfo(
            name = file.name,
            path = file.absolutePath,
            size = if (file.isDirectory) {
                file.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            } else file.length(),
            isDirectory = file.isDirectory,
            lastModified = dateFormat.format(Date(file.lastModified()))
        )
    }

    // --- Quick Access ---

    fun navigateToDownloads() {
        val downloads = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        )
        if (downloads.exists()) {
            navigateTo(downloads.absolutePath)
        }
    }

    fun navigateToRoot() {
        pathStack.add(_currentPath.value)
        _currentPath.value = _rootPath.value
        refresh()
    }

    // --- Git ---

    fun initGitRepo() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                gitRepository.initRepo(_currentPath.value)
            }
            refresh()
        }
    }

    fun getDisplayPath(): String {
        val root = _rootPath.value
        val current = _currentPath.value

        // Check if in Downloads
        val downloads = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        ).absolutePath
        if (current.startsWith(downloads)) {
            return "Downloads" + current.removePrefix(downloads).ifEmpty { "" }
        }

        return if (current.startsWith(root)) {
            current.removePrefix(root).ifEmpty { "/" }
        } else {
            current
        }
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }
}
