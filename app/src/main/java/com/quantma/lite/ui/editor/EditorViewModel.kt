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

private const val MAX_UNDO = 100

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

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private val _language = MutableStateFlow(CodeLanguage.PLAIN)
    val language: StateFlow<CodeLanguage> = _language.asStateFlow()

    private var originalContent = ""

    // Undo/redo stacks hold snapshots of text content
    private val undoStack = ArrayDeque<String>()
    private val redoStack = ArrayDeque<String>()

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
                _language.value = SyntaxHighlighter.getLanguage(_fileName.value)
                undoStack.clear()
                redoStack.clear()
                updateUndoRedoState()
                Timber.i("Opened: ${_fileName.value} (${content.length} chars, lang=${_language.value})")
            } catch (e: Exception) {
                _errorMessage.value = "Failed to open file: ${e.message}"
                Timber.e(e, "Failed to open $path")
            }
        }
    }

    /** Called whenever the editor text changes. Pushes snapshot for undo. */
    fun onContentChanged(newContent: String) {
        val previous = _fileContent.value
        if (newContent == previous) return

        // Push previous state to undo stack
        undoStack.addLast(previous)
        if (undoStack.size > MAX_UNDO) undoStack.removeFirst()
        redoStack.clear()

        _fileContent.value = newContent
        _isModified.value = newContent != originalContent
        updateUndoRedoState()
    }

    fun undo(): String? {
        if (undoStack.isEmpty()) return null
        val current = _fileContent.value
        redoStack.addLast(current)
        val prev = undoStack.removeLast()
        _fileContent.value = prev
        _isModified.value = prev != originalContent
        updateUndoRedoState()
        return prev
    }

    fun redo(): String? {
        if (redoStack.isEmpty()) return null
        val current = _fileContent.value
        undoStack.addLast(current)
        val next = redoStack.removeLast()
        _fileContent.value = next
        _isModified.value = next != originalContent
        updateUndoRedoState()
        return next
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

    private fun updateUndoRedoState() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }
}
