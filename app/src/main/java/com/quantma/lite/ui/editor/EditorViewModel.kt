package com.quantma.lite.ui.editor

import timber.log.Timber
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantma.lite.domain.repository.FileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val fileRepository: FileRepository
) : ViewModel() {

    private val _filePath = MutableStateFlow("")
    val filePath: StateFlow<String> = _filePath.asStateFlow()

    private val _fileName = MutableStateFlow("")
    val fileName: StateFlow<String> = _fileName.asStateFlow()

    private val _fileContent = MutableStateFlow("")
    val fileContent: StateFlow<String> = _fileContent.asStateFlow()

    private val _isModified = MutableStateFlow(false)
    val isModified: StateFlow<Boolean> = _isModified.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var originalContent = ""

    fun openFile(path: String) {
        viewModelScope.launch {
            try {
                val content = withContext(Dispatchers.IO) {
                    fileRepository.readFile(path)
                }
                _filePath.value = path
                _fileName.value = path.substringAfterLast('/')
                _fileContent.value = content
                originalContent = content
                _isModified.value = false
                _errorMessage.value = null
                Timber.i("Opened: ${_fileName.value} (${content.length} chars)")
            } catch (e: Exception) {
                _errorMessage.value = "Failed to open file: ${e.message}"
                Timber.e(e, "Failed to open $path")
            }
        }
    }

    fun onContentChanged(newContent: String) {
        _isModified.value = newContent != originalContent
    }

    fun saveFile(currentContent: String) {
        val path = _filePath.value
        if (path.isEmpty()) return

        viewModelScope.launch {
            _isSaving.value = true
            try {
                withContext(Dispatchers.IO) {
                    fileRepository.writeFile(path, currentContent)
                }
                originalContent = currentContent
                _fileContent.value = currentContent
                _isModified.value = false
                _errorMessage.value = null
                Timber.i("Saved: ${_fileName.value}")
            } catch (e: Exception) {
                _errorMessage.value = "Failed to save: ${e.message}"
                Timber.e(e, "Failed to save $path")
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun getScopeNameForFile(): String {
        val ext = _fileName.value.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "kt", "kts" -> "source.kotlin"
            "java" -> "source.java"
            "py" -> "source.python"
            "js", "mjs" -> "source.js"
            "ts" -> "source.ts"
            "jsx" -> "source.js.jsx"
            "tsx" -> "source.tsx"
            "c", "h" -> "source.c"
            "cpp", "hpp", "cc", "cxx" -> "source.cpp"
            "rs" -> "source.rust"
            "go" -> "source.go"
            "rb" -> "source.ruby"
            "swift" -> "source.swift"
            "html", "htm" -> "text.html.basic"
            "css" -> "source.css"
            "json" -> "source.json"
            "xml" -> "text.xml"
            "yaml", "yml" -> "source.yaml"
            "md", "markdown" -> "text.html.markdown"
            "sh", "bash", "zsh" -> "source.shell"
            "sql" -> "source.sql"
            "toml" -> "source.toml"
            "lua" -> "source.lua"
            "dart" -> "source.dart"
            "gradle" -> "source.groovy"
            "php" -> "source.php"
            else -> "source.${ext.ifEmpty { "txt" }}"
        }
    }
}
