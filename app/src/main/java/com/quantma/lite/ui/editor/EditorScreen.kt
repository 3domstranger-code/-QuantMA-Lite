package com.quantma.lite.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.quantma.lite.R

private val EditorBg        = Color(0xFF1E1F29)
private val LineNumBg       = Color(0xFF191A24)
private val LineNumColor    = Color(0xFF6272A4)
private val CursorColor     = Color(0xFFF8F8F2)
private val EditorTextColor = Color(0xFFF8F8F2)
private val StatusBarBg     = Color(0xFF14151F)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    filePath: String,
    onBack: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel()
) {
    val fileName    by viewModel.fileName.collectAsState()
    val fileContent by viewModel.fileContent.collectAsState()
    val isModified  by viewModel.isModified.collectAsState()
    val isSaving    by viewModel.isSaving.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val canUndo     by viewModel.canUndo.collectAsState()
    val canRedo     by viewModel.canRedo.collectAsState()
    val language    by viewModel.language.collectAsState()

    // Local TextFieldValue tracks cursor; content sync comes from ViewModel
    var textValue by remember { mutableStateOf(TextFieldValue("")) }
    var initialised by remember { mutableStateOf(false) }

    // Sync when ViewModel content changes (load, undo, redo)
    LaunchedEffect(fileContent) {
        if (!initialised || textValue.text != fileContent) {
            textValue = TextFieldValue(fileContent)
            initialised = true
        }
    }

    LaunchedEffect(filePath) {
        viewModel.openFile(filePath)
    }

    // Highlighted text (recomputed on each keystroke — fast for files <200 KB)
    val highlighted: AnnotatedString = remember(textValue.text, language) {
        SyntaxHighlighter.highlight(language, textValue.text)
    }

    val vertScroll  = rememberScrollState()
    val horizScroll = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Top App Bar ────────────────────────────────────────────────────────
        TopAppBar(
            title = {
                Text(
                    text = if (isModified) "● $fileName" else fileName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace)
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            },
            actions = {
                IconButton(onClick = {
                    val restored = viewModel.undo()
                    if (restored != null) textValue = TextFieldValue(restored)
                }, enabled = canUndo) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.undo))
                }
                IconButton(onClick = {
                    val restored = viewModel.redo()
                    if (restored != null) textValue = TextFieldValue(restored)
                }, enabled = canRedo) {
                    Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = stringResource(R.string.redo))
                }
                IconButton(
                    onClick = { viewModel.saveFile(textValue.text) },
                    enabled = isModified && !isSaving
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Save, contentDescription = stringResource(R.string.save))
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = LineNumBg)
        )

        // ── Editor body ────────────────────────────────────────────────────────
        Box(modifier = Modifier.weight(1f).fillMaxWidth().background(EditorBg)) {

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(vertScroll)
            ) {
                // Line numbers
                val lineCount = maxOf(1, textValue.text.count { it == '\n' } + 1)
                Column(
                    modifier = Modifier
                        .background(LineNumBg)
                        .padding(start = 8.dp, end = 6.dp, top = 8.dp, bottom = 8.dp)
                        .width(IntrinsicSize.Max)
                        .widthIn(min = 32.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    repeat(lineCount) { i ->
                        Text(
                            text = "${i + 1}",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                color = LineNumColor,
                                lineHeight = 20.sp
                            )
                        )
                    }
                }

                // Vertical divider
                Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(LineNumBg))

                // Code area — horizontal scroll wrapping BasicTextField
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(horizScroll)
                        .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                ) {
                    BasicTextField(
                        value = textValue,
                        onValueChange = { newValue ->
                            if (newValue.text != textValue.text) {
                                viewModel.onContentChanged(newValue.text)
                            }
                            textValue = newValue
                        },
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = EditorTextColor,
                            lineHeight = 20.sp
                        ),
                        cursorBrush = SolidColor(CursorColor),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        // Apply syntax highlighting as visual transformation
                        visualTransformation = remember(highlighted) {
                            HighlightTransformation(highlighted)
                        },
                        modifier = Modifier.fillMaxWidth().widthIn(min = 600.dp)
                    )
                }
            }

            // Error snackbar
            errorMessage?.let { error ->
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text(stringResource(R.string.dismiss))
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ) { Text(error) }
            }
        }

        // ── Status bar ─────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(StatusBarBg)
                .padding(horizontal = 12.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = language.name,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    color = LineNumColor
                )
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "UTF-8",
                style = MaterialTheme.typography.labelSmall.copy(color = LineNumColor)
            )
            Spacer(modifier = Modifier.weight(1f))
            // Cursor position
            val cursorOffset = textValue.selection.start
            val textBefore = textValue.text.take(cursorOffset)
            val line = textBefore.count { it == '\n' } + 1
            val col  = cursorOffset - (textBefore.lastIndexOf('\n') + 1) + 1
            Text(
                text = stringResource(R.string.editor_position, line, col),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    color = LineNumColor
                )
            )
        }
    }
}

/**
 * Applies pre-computed [AnnotatedString] to BasicTextField as visual transformation.
 * The annotated string must have the same character count as the raw text.
 */
private class HighlightTransformation(
    private val highlighted: AnnotatedString
) : VisualTransformation {
    override fun filter(text: AnnotatedString): androidx.compose.ui.text.input.TransformedText {
        // Ensure length match (safety guard)
        val out = if (highlighted.length == text.length) highlighted else text
        return androidx.compose.ui.text.input.TransformedText(
            out,
            androidx.compose.ui.text.input.OffsetMapping.Identity
        )
    }
}
