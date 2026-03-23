package com.quantma.lite.ui.editor

import timber.log.Timber
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.quantma.lite.R
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.widget.CodeEditor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    filePath: String,
    onBack: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel()
) {
    val fileName by viewModel.fileName.collectAsState()
    val fileContent by viewModel.fileContent.collectAsState()
    val isModified by viewModel.isModified.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var editorRef by remember { mutableStateOf<CodeEditor?>(null) }
    var loadedFilePath by remember { mutableStateOf("") }

    // Load file when screen opens
    LaunchedEffect(filePath) {
        viewModel.openFile(filePath)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = if (isModified) "$fileName *" else fileName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            },
            actions = {
                // Undo
                IconButton(
                    onClick = { editorRef?.undo() },
                    enabled = editorRef?.canUndo() == true
                ) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.undo))
                }
                // Redo
                IconButton(
                    onClick = { editorRef?.redo() },
                    enabled = editorRef?.canRedo() == true
                ) {
                    Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = stringResource(R.string.redo))
                }
                // Save
                IconButton(
                    onClick = {
                        editorRef?.let { editor ->
                            viewModel.saveFile(editor.text.toString())
                        }
                    },
                    enabled = isModified && !isSaving
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(8.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Save, contentDescription = stringResource(R.string.save))
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // Sora Editor via AndroidView
            AndroidView(
                factory = { context ->
                    CodeEditor(context).apply {
                        // Basic settings
                        setTextSize(14f)
                        isLineNumberEnabled = true
                        isWordwrap = false
                        tabWidth = 4

                        // Apply TextMate theme if available
                        try {
                            val themeRegistry = ThemeRegistry.getInstance()
                            val colorScheme = TextMateColorScheme.create(themeRegistry)
                            this.colorScheme = colorScheme
                        } catch (e: Exception) {
                            Timber.w(e, "TextMate theme not available, using defaults")
                        }

                        // Listen for text changes
                        subscribeAlways(
                            io.github.rosemoe.sora.event.ContentChangeEvent::class.java
                        ) {
                            viewModel.onContentChanged(text.toString())
                        }
                    }
                },
                update = { editor ->
                    editorRef = editor

                    // Set content only once when file is first loaded (or when a different file is opened)
                    if (fileContent.isNotEmpty() && loadedFilePath != filePath) {
                        loadedFilePath = filePath
                        editor.setText(fileContent)

                        // Apply TextMate language
                        val scopeName = viewModel.getScopeNameForFile()
                        try {
                            val language = TextMateLanguage.create(scopeName, true)
                            editor.setEditorLanguage(language)
                        } catch (e: Exception) {
                            Timber.w(e, "TextMate language not found for $scopeName")
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Error snackbar
            errorMessage?.let { error ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text(stringResource(R.string.dismiss))
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ) {
                    Text(error)
                }
            }
        }

        // Bottom status bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val ext = fileName.substringAfterLast('.', "").uppercase()
            Text(
                text = ext.ifEmpty { "TXT" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "UTF-8",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.weight(1f))
            editorRef?.let { editor ->
                val cursor = editor.cursor
                Text(
                    text = stringResource(R.string.editor_position, cursor.leftLine + 1, cursor.leftColumn + 1),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            editorRef?.release()
            editorRef = null
        }
    }
}
