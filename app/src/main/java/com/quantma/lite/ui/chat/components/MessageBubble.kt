package com.quantma.lite.ui.chat.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quantma.lite.R
import com.quantma.lite.domain.model.ChatMessage
import com.quantma.lite.domain.model.Role
import com.quantma.lite.ui.theme.CodeBlockBackground
import com.quantma.lite.ui.theme.CodeBlockText

private const val COLLAPSE_THRESHOLD = 10  // auto-collapse blocks longer than this
private const val PREVIEW_LINES = 5        // lines shown when collapsed

/** Prefixes that indicate an error/failure assistant message. */
private fun String.isErrorMessage() =
    startsWith("⚠️") || startsWith("Error:") || startsWith("Ошибка:") ||
    startsWith("❌") || startsWith("Failed:")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: ChatMessage,
    onDelete: ((Long) -> Unit)? = null,
    onRetry: ((Long) -> Unit)? = null,
    isSelected: Boolean = false,
    selectionMode: Boolean = false,
    onToggleSelect: ((Long) -> Unit)? = null,
    codeBlockFontSize: Float = 13f
) {
    if (message.isCompressed) {
        CompressedMessageBubble(message)
        return
    }

    val isUser = message.role == Role.USER
    val maxWidth = (LocalConfiguration.current.screenWidthDp * 0.85).dp
    var showMenu by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    val rowBg = if (isSelected)
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    else
        Color.Transparent

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isUser) {
                Box {
                    Surface(
                        modifier = Modifier
                            .widthIn(max = maxWidth)
                            .combinedClickable(
                                onClick = { if (selectionMode) onToggleSelect?.invoke(message.id) },
                                onLongClick = {
                                    if (onToggleSelect != null) onToggleSelect(message.id)
                                    else showMenu = true
                                }
                            ),
                        shape = RoundedCornerShape(
                            topStart = 20.dp, topEnd = 20.dp,
                            bottomStart = 20.dp, bottomEnd = 6.dp
                        ),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        tonalElevation = 0.dp
                    ) {
                        SelectionContainer {
                            MessageContent(
                                content = message.content,
                                isUser = true,
                                codeBlockFontSize = codeBlockFontSize,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                    if (!selectionMode) {
                        MessageContextMenu(
                            expanded = showMenu,
                            onDismiss = { showMenu = false },
                            onCopy = {
                                clipboardManager.setText(AnnotatedString(message.content))
                                showMenu = false
                            },
                            onDelete = if (onDelete != null && message.id > 0) {
                                { onDelete(message.id); showMenu = false }
                            } else null
                        )
                    }
                }
            } else {
                // Assistant bubble — left accent strip + surface
                Box {
                    Column(modifier = Modifier.widthIn(max = maxWidth)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                                .combinedClickable(
                                    onClick = { if (selectionMode) onToggleSelect?.invoke(message.id) },
                                    onLongClick = {
                                        if (onToggleSelect != null) onToggleSelect(message.id)
                                        else showMenu = true
                                    }
                                )
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                            )
                            Surface(
                                shape = RoundedCornerShape(
                                    topStart = 4.dp, topEnd = 20.dp,
                                    bottomStart = 4.dp, bottomEnd = 20.dp
                                ),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                tonalElevation = 1.dp
                            ) {
                                SelectionContainer {
                                    MessageContent(
                                        content = message.content,
                                        isUser = false,
                                        codeBlockFontSize = codeBlockFontSize,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                        }

                        // Retry button for error messages
                        if (!selectionMode && message.id > 0 && message.content.isErrorMessage()) {
                            onRetry?.let { retry ->
                                TextButton(
                                    onClick = { retry(message.id) },
                                    modifier = Modifier.padding(start = 6.dp, top = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Retry",
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "Повторить",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }

                    if (!selectionMode) {
                        MessageContextMenu(
                            expanded = showMenu,
                            onDismiss = { showMenu = false },
                            onCopy = {
                                clipboardManager.setText(AnnotatedString(message.content))
                                showMenu = false
                            },
                            onDelete = if (onDelete != null && message.id > 0) {
                                { onDelete(message.id); showMenu = false }
                            } else null
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onDelete: (() -> Unit)?
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("Копировать") },
            leadingIcon = {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
            },
            onClick = onCopy
        )
        if (onDelete != null) {
            DropdownMenuItem(
                text = { Text("Удалить", color = MaterialTheme.colorScheme.error) },
                leadingIcon = {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                },
                onClick = onDelete
            )
        }
    }
}

@Composable
private fun MessageContent(
    content: String,
    isUser: Boolean,
    codeBlockFontSize: Float = 13f,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current

    Column(modifier = modifier) {
        val parts = parseMessageContent(content)
        parts.forEach { part ->
            when (part) {
                is MessagePart.Text -> {
                    Text(
                        text = part.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isUser) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
                is MessagePart.CodeBlock -> {
                    val lines = part.content.lines()
                    val collapsible = lines.size > COLLAPSE_THRESHOLD
                    var expanded by remember { mutableStateOf(!collapsible) }
                    val displayText = if (expanded) part.content
                                      else lines.take(PREVIEW_LINES).joinToString("\n") + "\n..."

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(CodeBlockBackground)
                    ) {
                        // Header: language + collapse toggle + copy
                        DisableSelection {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CodeBlockBackground.copy(alpha = 0.8f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Left side: language label + expand/collapse indicator (clickable)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = if (collapsible) {
                                        Modifier
                                            .weight(1f)
                                            .clickable { expanded = !expanded }
                                            .padding(vertical = 4.dp)
                                    } else {
                                        Modifier.weight(1f)
                                    }
                                ) {
                                    Text(
                                        text = part.language.ifEmpty { "code" },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CodeBlockText.copy(alpha = 0.5f)
                                    )
                                    if (collapsible) {
                                        Spacer(Modifier.width(4.dp))
                                        Icon(
                                            imageVector = if (expanded) Icons.Default.KeyboardArrowUp
                                                          else Icons.Default.KeyboardArrowDown,
                                            contentDescription = if (expanded) "Свернуть" else "Развернуть",
                                            tint = CodeBlockText.copy(alpha = 0.4f),
                                            modifier = Modifier.size(12.dp)
                                        )
                                        if (!expanded) {
                                            Spacer(Modifier.width(2.dp))
                                            Text(
                                                text = "${lines.size} строк",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = CodeBlockText.copy(alpha = 0.4f)
                                            )
                                        }
                                    }
                                }
                                // Copy button
                                IconButton(
                                    onClick = { clipboardManager.setText(AnnotatedString(part.content)) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = stringResource(R.string.copy_code),
                                        tint = CodeBlockText.copy(alpha = 0.6f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                        // Scrollable highlighted code
                        Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            Text(
                                text = highlightCode(displayText, part.language),
                                modifier = Modifier.padding(
                                    start = 12.dp, end = 12.dp, bottom = 12.dp, top = 4.dp
                                ),
                                fontFamily = FontFamily.Monospace,
                                fontSize = codeBlockFontSize.sp,
                                lineHeight = (codeBlockFontSize + 5f).sp
                            )
                        }
                    }
                }
                is MessagePart.DiffBlock -> {
                    val lines = part.content.lines()
                    val collapsible = lines.size > COLLAPSE_THRESHOLD
                    var expanded by remember { mutableStateOf(!collapsible) }
                    val displayLines = if (expanded) lines else lines.take(PREVIEW_LINES)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(CodeBlockBackground)
                    ) {
                        // Header
                        DisableSelection {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CodeBlockBackground.copy(alpha = 0.8f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = if (collapsible) {
                                        Modifier
                                            .weight(1f)
                                            .clickable { expanded = !expanded }
                                            .padding(vertical = 4.dp)
                                    } else {
                                        Modifier.weight(1f)
                                    }
                                ) {
                                    Text(
                                        text = "diff",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CodeBlockText.copy(alpha = 0.5f)
                                    )
                                    if (collapsible) {
                                        Spacer(Modifier.width(4.dp))
                                        Icon(
                                            imageVector = if (expanded) Icons.Default.KeyboardArrowUp
                                                          else Icons.Default.KeyboardArrowDown,
                                            contentDescription = if (expanded) "Свернуть" else "Развернуть",
                                            tint = CodeBlockText.copy(alpha = 0.4f),
                                            modifier = Modifier.size(12.dp)
                                        )
                                        if (!expanded) {
                                            Spacer(Modifier.width(2.dp))
                                            Text(
                                                text = "${lines.size} строк",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = CodeBlockText.copy(alpha = 0.4f)
                                            )
                                        }
                                    }
                                }
                                IconButton(
                                    onClick = { clipboardManager.setText(AnnotatedString(part.content)) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = stringResource(R.string.copy_code),
                                        tint = CodeBlockText.copy(alpha = 0.6f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                        // Colored diff lines
                        Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            val annotated = buildAnnotatedString {
                                displayLines.forEach { line ->
                                    val color = when {
                                        line.startsWith("+") -> Color(0xFF4CAF50)
                                        line.startsWith("-") -> Color(0xFFE57373)
                                        line.startsWith("@@") -> Color(0xFF64B5F6)
                                        else -> CodeBlockText
                                    }
                                    withStyle(SpanStyle(color = color)) { append(line) }
                                    append("\n")
                                }
                                if (!expanded && lines.size > PREVIEW_LINES) {
                                    withStyle(SpanStyle(color = CodeBlockText.copy(alpha = 0.5f))) {
                                        append("...")
                                    }
                                }
                            }
                            Text(
                                text = annotated,
                                modifier = Modifier.padding(
                                    start = 12.dp, end = 12.dp, bottom = 12.dp, top = 4.dp
                                ),
                                fontFamily = FontFamily.Monospace,
                                fontSize = codeBlockFontSize.sp,
                                lineHeight = (codeBlockFontSize + 5f).sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompressedMessageBubble(message: ChatMessage) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 1.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = message.content,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

private sealed class MessagePart {
    data class Text(val content: String) : MessagePart()
    data class CodeBlock(val content: String, val language: String = "") : MessagePart()
    data class DiffBlock(val content: String) : MessagePart()
}

private fun parseMessageContent(content: String): List<MessagePart> {
    val parts = mutableListOf<MessagePart>()
    val codeBlockRegex = Regex("```(\\w*)\\n([\\s\\S]*?)```")

    var lastIndex = 0
    codeBlockRegex.findAll(content).forEach { match ->
        if (match.range.first > lastIndex) {
            val text = content.substring(lastIndex, match.range.first).trim()
            if (text.isNotEmpty()) parts.add(MessagePart.Text(text))
        }
        val lang = match.groupValues[1]
        val code = match.groupValues[2].trimEnd()
        if (lang.equals("diff", ignoreCase = true)) {
            parts.add(MessagePart.DiffBlock(content = code))
        } else {
            parts.add(MessagePart.CodeBlock(content = code, language = lang))
        }
        lastIndex = match.range.last + 1
    }

    if (lastIndex < content.length) {
        val text = content.substring(lastIndex).trim()
        if (text.isNotEmpty()) parts.add(MessagePart.Text(text))
    }

    if (parts.isEmpty()) parts.add(MessagePart.Text(content))

    return parts
}
