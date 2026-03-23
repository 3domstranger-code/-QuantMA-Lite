package com.quantma.lite.ui.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quantma.lite.R
import com.quantma.lite.data.diff.DiffEngine
import com.quantma.lite.domain.model.AgentStep
import com.quantma.lite.domain.model.AgentStepStatus
import com.quantma.lite.domain.model.PendingDelete
import com.quantma.lite.domain.model.PendingGitAction
import com.quantma.lite.domain.model.PendingWrite
import com.quantma.lite.domain.model.ToolCall
import com.quantma.lite.ui.theme.CodeBlockBackground
import com.quantma.lite.ui.theme.CodeBlockText

@Composable
fun AgentStepCard(
    step: AgentStep,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Response text (truncated if long)
            val responseText = step.response.lines()
                .filterNot { it.trimStart().startsWith("ACTION:") }
                .filterNot { it.trimStart().equals("CONTENT:", ignoreCase = true) }
                .filterNot { it.trimStart().equals("END_CONTENT", ignoreCase = true) }
                .filterNot { it.trimStart().equals("DONE", ignoreCase = true) }
                .joinToString("\n").trim()

            if (responseText.isNotEmpty()) {
                Text(
                    text = responseText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Tool call info
            if (step.toolCall != null && step.toolCall !is ToolCall.Done) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { expanded = !expanded }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = getToolIcon(step.toolCall),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = getToolLabel(step.toolCall),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )

                    when (step.status) {
                        AgentStepStatus.EXECUTING -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        }
                        AgentStepStatus.WAITING_USER -> {
                            Icon(
                                Icons.Default.HourglassEmpty,
                                contentDescription = stringResource(R.string.status_waiting),
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        else -> {
                            Icon(
                                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (expanded) stringResource(R.string.collapse) else stringResource(R.string.expand),
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Expanded tool result
                AnimatedVisibility(visible = expanded && step.toolResult != null) {
                    val result = step.toolResult
                    if (result != null && result.output != "NEEDS_APPROVAL") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CodeBlockBackground)
                                .horizontalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = result.output.take(2000),
                                modifier = Modifier.padding(8.dp),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = CodeBlockText,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WriteApprovalCard(
    pendingWrite: PendingWrite,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isNewFile = pendingWrite.oldContent.isEmpty()

    // Compute diff once when the card appears (keyed by path + content length)
    val diffLines = remember(pendingWrite.path, pendingWrite.newContent.length) {
        if (isNewFile) emptyList()
        else DiffEngine.computeDiff(pendingWrite.oldContent, pendingWrite.newContent)
    }

    // Default: show diff for existing files, full content for new files
    var showDiff by remember(pendingWrite.path) { mutableStateOf(!isNewFile) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header: icon + filename + Diff/Full toggle for existing files
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Description,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.write_file_header, pendingWrite.path.substringAfterLast('/')),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f)
                )
                // Diff / Full toggle (only for modified existing files)
                if (!isNewFile) {
                    TextButton(
                        onClick = { showDiff = true },
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.diff_view),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (showDiff) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = { showDiff = false },
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.full_view),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (!showDiff) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Content area: diff view or full file view
            if (isNewFile) {
                // New file — just show a label + full content
                Text(
                    text = stringResource(R.string.new_file_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(CodeBlockBackground)
                        .horizontalScroll(rememberScrollState())
                ) {
                    Text(
                        text = pendingWrite.newContent.take(1500),
                        modifier = Modifier.padding(8.dp),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = CodeBlockText,
                        lineHeight = 16.sp
                    )
                }
            } else if (showDiff) {
                // Diff view (colored lines)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(CodeBlockBackground)
                ) {
                    AgentDiffViewer(
                        lines = diffLines,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                // Full file view
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(CodeBlockBackground)
                        .horizontalScroll(rememberScrollState())
                ) {
                    Text(
                        text = pendingWrite.newContent.take(1500),
                        modifier = Modifier.padding(8.dp),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = CodeBlockText,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Approve / Reject buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onReject,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.reject))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onApprove) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.apply))
                }
            }
        }
    }
}

@Composable
fun DeleteApprovalCard(
    pendingDelete: PendingDelete,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header: icon + filename
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.delete_file_header, pendingDelete.path.substringAfterLast('/')),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Warning
            Text(
                text = stringResource(R.string.delete_file_warning),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(6.dp))

            // File preview
            if (pendingDelete.preview.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.delete_file_preview),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(CodeBlockBackground)
                        .horizontalScroll(rememberScrollState())
                ) {
                    Text(
                        text = pendingDelete.preview.take(800),
                        modifier = Modifier.padding(8.dp),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = CodeBlockText,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Delete / Cancel buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(onClick = onReject) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onApprove,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.delete_confirm))
                }
            }
        }
    }
}

@Composable
fun GitActionApprovalCard(
    pendingGitAction: PendingGitAction,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isCommit = pendingGitAction.action == "commit"
    val containerColor = if (isCommit)
        MaterialTheme.colorScheme.tertiaryContainer
    else
        MaterialTheme.colorScheme.secondaryContainer

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    if (isCommit) Icons.Default.Check else Icons.Default.Code,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = pendingGitAction.description,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Preview content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(CodeBlockBackground)
            ) {
                Text(
                    text = pendingGitAction.preview,
                    modifier = Modifier.padding(8.dp),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = CodeBlockText,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Approve / Cancel buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(onClick = onReject) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onApprove) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isCommit) "Commit" else "Push")
                }
            }
        }
    }
}

private fun getToolIcon(toolCall: ToolCall): ImageVector {
    return when (toolCall) {
        is ToolCall.ReadFile -> Icons.Default.Description
        is ToolCall.ListFiles -> Icons.Default.Folder
        is ToolCall.WriteFile -> Icons.Default.Code
        is ToolCall.GitStatus -> Icons.Default.Code
        is ToolCall.GitDiff -> Icons.Default.Code
        is ToolCall.GitBranch -> Icons.Default.Code
        is ToolCall.GitClone -> Icons.Default.Code
        is ToolCall.GitPull -> Icons.Default.Code
        is ToolCall.GitPush -> Icons.Default.Code
        is ToolCall.GitCommit -> Icons.Default.Check
        // Phase 8 (v1.8.0)
        is ToolCall.RunCommand -> Icons.Default.Terminal
        is ToolCall.SearchFiles -> Icons.Default.Search
        is ToolCall.DeleteFile -> Icons.Default.Delete
        // Branch / Merge / Stash
        is ToolCall.GitCreateBranch -> Icons.Default.Code
        is ToolCall.GitCheckout -> Icons.Default.Code
        is ToolCall.GitDeleteBranch -> Icons.Default.Delete
        is ToolCall.GitMerge -> Icons.Default.Code
        is ToolCall.GitStashSave -> Icons.Default.Code
        is ToolCall.GitStashPop -> Icons.Default.Code
        is ToolCall.GitStashList -> Icons.Default.Code
        is ToolCall.Done -> Icons.Default.Check
    }
}

private fun getToolLabel(toolCall: ToolCall): String {
    return when (toolCall) {
        is ToolCall.ReadFile -> "read_file ${toolCall.path.substringAfterLast('/')}"
        is ToolCall.ListFiles -> "list_files ${toolCall.path.substringAfterLast('/')}"
        is ToolCall.WriteFile -> "write_file ${toolCall.path.substringAfterLast('/')}"
        is ToolCall.GitStatus -> "git_status ${toolCall.path.substringAfterLast('/')}"
        is ToolCall.GitDiff -> "git_diff ${toolCall.path.substringAfterLast('/')}"
        is ToolCall.GitBranch -> "git_branch ${toolCall.path.substringAfterLast('/')}"
        is ToolCall.GitClone -> "git_clone ${toolCall.url}"
        is ToolCall.GitPull -> "git_pull ${toolCall.path.substringAfterLast('/')}"
        is ToolCall.GitPush -> "git_push ${toolCall.path.substringAfterLast('/')}"
        is ToolCall.GitCommit -> "git_commit ${toolCall.path.substringAfterLast('/')}"
        // Phase 8 (v1.8.0)
        is ToolCall.RunCommand -> "run_command ${toolCall.command.take(40)}"
        is ToolCall.SearchFiles -> "search_files '${toolCall.pattern}'"
        is ToolCall.DeleteFile -> "delete_file ${toolCall.path.substringAfterLast('/')}"
        // Branch / Merge / Stash
        is ToolCall.GitCreateBranch -> "git_create_branch ${toolCall.name}"
        is ToolCall.GitCheckout -> "git_checkout ${toolCall.name}"
        is ToolCall.GitDeleteBranch -> "git_delete_branch ${toolCall.name}"
        is ToolCall.GitMerge -> "git_merge ${toolCall.branchName}"
        is ToolCall.GitStashSave -> "git_stash save"
        is ToolCall.GitStashPop -> "git_stash pop"
        is ToolCall.GitStashList -> "git_stash list"
        is ToolCall.Done -> "Done"
    }
}
