package com.quantma.lite.ui.git

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.quantma.lite.R
import com.quantma.lite.domain.model.GitBranch
import com.quantma.lite.domain.model.GitDiffEntry
import com.quantma.lite.domain.model.GitLogEntry
import com.quantma.lite.domain.model.GitRemote
import com.quantma.lite.domain.model.GitStatus
import com.quantma.lite.domain.model.StashEntry
import com.quantma.lite.ui.git.components.DiffViewer
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitScreen(
    repoPath: String,
    onBack: () -> Unit,
    viewModel: GitViewModel = hiltViewModel()
) {
    val currentBranch by viewModel.currentBranch.collectAsState()
    val gitStatus by viewModel.gitStatus.collectAsState()
    val logEntries by viewModel.logEntries.collectAsState()
    val branches by viewModel.branches.collectAsState()
    val diffEntries by viewModel.diffEntries.collectAsState()
    val stashList by viewModel.stashList.collectAsState()
    val remotes by viewModel.remotes.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isCloning by viewModel.isCloning.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()
    val commitMessage by viewModel.commitMessage.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()

    var showCloneDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(repoPath) {
        viewModel.openRepo(repoPath)
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }
    LaunchedEffect(successMessage) {
        successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSuccess()
        }
    }

    // Clone dialog
    if (showCloneDialog) {
        CloneDialog(
            isCloning = isCloning,
            onClone = { url, dest -> viewModel.clone(url, dest) },
            onDismiss = { showCloneDialog = false }
        )
    }

    val repoName = File(repoPath).name

    val tabTitles = listOf(
        stringResource(R.string.tab_status),
        stringResource(R.string.tab_log, logEntries.size),
        stringResource(R.string.tab_branches),
        stringResource(R.string.tab_diff)
    )
    val selectedTabIndex = selectedTab.ordinal

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = repoName,
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (currentBranch.isNotEmpty()) {
                            Text(
                                text = "\u2387 $currentBranch",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (isLoading || isCloning) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 8.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    IconButton(onClick = { showCloneDialog = true }) {
                        Icon(Icons.Default.Cloud, contentDescription = stringResource(R.string.clone_repo))
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { viewModel.selectTab(GitTab.entries[index]) },
                        text = {
                            Text(
                                title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selectedTabIndex == index)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
            }

            when (selectedTab) {
                GitTab.STATUS -> StatusContent(
                    gitStatus = gitStatus,
                    remotes = remotes,
                    commitMessage = commitMessage,
                    isLoading = isLoading,
                    onStageAll = { viewModel.stageAll() },
                    onCommitMessageChanged = { viewModel.onCommitMessageChanged(it) },
                    onCommit = { viewModel.commit() },
                    onPull = { viewModel.pull() },
                    onPush = { viewModel.push() },
                    onFetch = { viewModel.fetch() },
                    onAddRemote = { name, url -> viewModel.addRemote(name, url) }
                )
                GitTab.LOG -> LogContent(logEntries = logEntries)
                GitTab.BRANCHES -> BranchesContent(
                    branches = branches,
                    stashList = stashList,
                    onCreateBranch = { viewModel.createBranch(it) },
                    onCheckout = { viewModel.checkoutBranch(it) },
                    onDelete = { viewModel.deleteBranch(it) },
                    onMerge = { viewModel.merge(it) },
                    onStash = { viewModel.stash() },
                    onStashPop = { viewModel.stashPop(it) }
                )
                GitTab.DIFF -> DiffContent(entries = diffEntries)
            }
        }
    }
}

// ---- Status Tab ----

@Composable
private fun StatusContent(
    gitStatus: GitStatus,
    remotes: List<GitRemote>,
    commitMessage: String,
    isLoading: Boolean,
    onStageAll: () -> Unit,
    onCommitMessageChanged: (String) -> Unit,
    onCommit: () -> Unit,
    onPull: () -> Unit,
    onPush: () -> Unit,
    onFetch: () -> Unit,
    onAddRemote: (String, String) -> Unit
) {
    var showAddRemoteDialog by remember { mutableStateOf(false) }

    if (showAddRemoteDialog) {
        AddRemoteDialog(
            onAdd = { name, url -> onAddRemote(name, url); showAddRemoteDialog = false },
            onDismiss = { showAddRemoteDialog = false }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Remote operations
        item {
            Text(
                text = stringResource(R.string.remotes),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onPull,
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.pull))
                }
                OutlinedButton(
                    onClick = onPush,
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.push))
                }
                OutlinedButton(
                    onClick = onFetch,
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.fetch))
                }
            }
        }

        // Remote list
        if (remotes.isNotEmpty()) {
            items(remotes, key = { "remote_${it.name}" }) { remote ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = remote.name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(80.dp)
                    )
                    Text(
                        text = remote.url,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            item {
                Text(
                    text = stringResource(R.string.no_remotes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            TextButton(onClick = { showAddRemoteDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.add_remote))
            }
        }

        item { HorizontalDivider() }

        if (gitStatus.isClean) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.clean_tree),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }

        if (gitStatus.stagedCount > 0) {
            item {
                Text(
                    text = stringResource(R.string.staged_changes, gitStatus.stagedCount),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            val stagedFiles = buildList {
                gitStatus.added.forEach { add(FileStatusItem("A", it, Color(0xFF4CAF50))) }
                gitStatus.changed.forEach { add(FileStatusItem("M", it, Color(0xFFFFC107))) }
                gitStatus.removed.forEach { add(FileStatusItem("D", it, Color(0xFFF44336))) }
            }
            items(stagedFiles, key = { "staged_${it.path}" }) { item ->
                FileStatusRow(item)
            }
        }

        if (gitStatus.unstagedCount > 0) {
            item {
                Text(
                    text = stringResource(R.string.unstaged_changes, gitStatus.unstagedCount),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            val unstagedFiles = buildList {
                gitStatus.modified.forEach { add(FileStatusItem("M", it, Color(0xFFFFC107))) }
                gitStatus.missing.forEach { add(FileStatusItem("D", it, Color(0xFFF44336))) }
                gitStatus.untracked.forEach { add(FileStatusItem("?", it, Color(0xFF9E9E9E))) }
            }
            items(unstagedFiles, key = { "unstaged_${it.path}" }) { item ->
                FileStatusRow(item)
            }

            item {
                Button(
                    onClick = onStageAll,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.stage_all))
                }
            }
        }

        if (gitStatus.conflicting.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.conflicts, gitStatus.conflicting.size),
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(0xFFF44336)
                )
            }
            items(gitStatus.conflicting.toList(), key = { "conflict_$it" }) { path ->
                FileStatusRow(FileStatusItem("C", path, Color(0xFFF44336)))
            }
        }

        if (gitStatus.stagedCount > 0) {
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }
            item {
                Text(
                    text = stringResource(R.string.commit),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            item {
                OutlinedTextField(
                    value = commitMessage,
                    onValueChange = onCommitMessageChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.commit_message_label)) },
                    placeholder = { Text(stringResource(R.string.commit_message_placeholder)) },
                    maxLines = 3,
                    singleLine = false
                )
            }
            item {
                Button(
                    onClick = onCommit,
                    enabled = !isLoading && commitMessage.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.commit_files, gitStatus.stagedCount, if (gitStatus.stagedCount != 1) "s" else ""))
                }
            }
        }
    }
}

// ---- Log Tab ----

@Composable
private fun LogContent(logEntries: List<GitLogEntry>) {
    if (logEntries.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.HelpOutline,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.no_commits),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(logEntries, key = { it.hash }) { entry ->
                CommitLogItem(entry)
            }
        }
    }
}

@Composable
private fun CommitLogItem(entry: GitLogEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.shortHash,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatTimestamp(entry.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = entry.message.lines().first(),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = entry.authorName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---- Branches Tab ----

@Composable
private fun BranchesContent(
    branches: List<GitBranch>,
    stashList: List<StashEntry>,
    onCreateBranch: (String) -> Unit,
    onCheckout: (String) -> Unit,
    onDelete: (String) -> Unit,
    onMerge: (String) -> Unit,
    onStash: () -> Unit,
    onStashPop: (Int) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var newBranchName by remember { mutableStateOf("") }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false; newBranchName = "" },
            title = { Text(stringResource(R.string.create_branch)) },
            text = {
                OutlinedTextField(
                    value = newBranchName,
                    onValueChange = { newBranchName = it },
                    label = { Text(stringResource(R.string.branch_name_hint)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newBranchName.isNotBlank()) {
                            onCreateBranch(newBranchName.trim())
                            showCreateDialog = false
                            newBranchName = ""
                        }
                    }
                ) { Text(stringResource(R.string.create_branch)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false; newBranchName = "" }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // New Branch button
        item {
            Button(
                onClick = { showCreateDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.create_branch))
            }
        }

        if (branches.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_branches),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(branches, key = { it.name }) { branch ->
                BranchItem(
                    branch = branch,
                    onCheckout = { onCheckout(branch.name) },
                    onDelete = { onDelete(branch.name) },
                    onMerge = { onMerge(branch.name) }
                )
            }
        }

        // Stash section
        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                text = "Stash",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onStash,
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.stash_changes)) }

                if (stashList.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { onStashPop(0) },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.stash_pop)) }
                }
            }
        }

        if (stashList.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.no_stashes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(stashList, key = { "stash_${it.index}" }) { stash ->
                StashItem(stash = stash, onPop = { onStashPop(stash.index) })
            }
        }
    }
}

@Composable
private fun BranchItem(
    branch: GitBranch,
    onCheckout: () -> Unit,
    onDelete: () -> Unit,
    onMerge: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (branch.isCurrent)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = branch.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                if (branch.isCurrent) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text(stringResource(R.string.current_branch_label), style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
            if (!branch.isCurrent) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onCheckout,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(stringResource(R.string.checkout_branch), style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = onMerge,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(stringResource(R.string.merge_branch), style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete_branch),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StashItem(stash: StashEntry, onPop: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "stash@{${stash.index}}",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(100.dp)
        )
        Text(
            text = stash.message,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onPop) {
            Text(stringResource(R.string.stash_pop), style = MaterialTheme.typography.labelSmall)
        }
    }
}

// ---- Diff Tab ----

@Composable
private fun DiffContent(entries: List<GitDiffEntry>) {
    DiffViewer(
        entries = entries,
        modifier = Modifier.fillMaxSize()
    )
}

// ---- Phase 4 Dialogs ----

@Composable
private fun CloneDialog(
    isCloning: Boolean,
    onClone: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var url by remember { mutableStateOf("") }
    var dest by remember { mutableStateOf("/storage/emulated/0/") }

    AlertDialog(
        onDismissRequest = { if (!isCloning) onDismiss() },
        title = { Text(stringResource(R.string.clone_repo)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.clone_url_hint)) },
                    placeholder = { Text("owner/repo  or  https://github.com/owner/repo",
                        style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dest,
                    onValueChange = { dest = it },
                    label = { Text(stringResource(R.string.clone_dest_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (isCloning) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.cloning), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (url.isNotBlank() && dest.isNotBlank()) onClone(url.trim(), dest.trim()) },
                enabled = !isCloning && url.isNotBlank() && dest.isNotBlank()
            ) { Text(stringResource(R.string.clone_start)) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isCloning
            ) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun AddRemoteDialog(
    onAdd: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_remote)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.remote_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.remote_url_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank() && url.isNotBlank()) onAdd(name.trim(), url.trim()) },
                enabled = name.isNotBlank() && url.isNotBlank()
            ) { Text(stringResource(R.string.add_remote)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

// ---- Shared helpers ----

@Composable
private fun FileStatusRow(item: FileStatusItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = item.prefix,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = item.color,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = item.path,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private data class FileStatusItem(
    val prefix: String,
    val path: String,
    val color: Color
)

@Composable
private fun formatTimestamp(millis: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - millis
    return when {
        diff < 60_000 -> stringResource(R.string.time_just_now)
        diff < 3_600_000 -> stringResource(R.string.time_m_ago, (diff / 60_000).toInt())
        diff < 86_400_000 -> stringResource(R.string.time_h_ago, (diff / 3_600_000).toInt())
        diff < 604_800_000 -> stringResource(R.string.time_d_ago, (diff / 86_400_000).toInt())
        else -> SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(millis))
    }
}
