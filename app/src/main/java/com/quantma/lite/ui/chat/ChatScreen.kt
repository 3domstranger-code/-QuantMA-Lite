package com.quantma.lite.ui.chat

import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.SmartToy
import androidx.navigation.NavBackStackEntry
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Surface
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.quantma.lite.R
import com.quantma.lite.domain.model.ChatMessage
import com.quantma.lite.domain.model.Role
import com.quantma.lite.ui.chat.components.AgentStepCard
import com.quantma.lite.ui.chat.components.DeleteApprovalCard
import com.quantma.lite.ui.chat.components.GitActionApprovalCard
import com.quantma.lite.ui.chat.components.InputBar
import com.quantma.lite.ui.chat.components.MessageBubble
import com.quantma.lite.ui.chat.components.ModelStatusIndicator
import com.quantma.lite.ui.chat.components.SessionDrawerContent
import com.quantma.lite.ui.chat.components.WriteApprovalCard
import com.quantma.lite.ui.theme.LocalCustomTheme
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    onNavigateToCli: (() -> Unit)? = null,
    onNavigateToFileBrowser: (() -> Unit)? = null,
    navBackStackEntry: NavBackStackEntry? = null
) {
    val messages by viewModel.messages.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filteredMessages by viewModel.filteredMessages.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val modelStatus by viewModel.modelStatus.collectAsState()
    val streamingContent by viewModel.streamingContent.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val tokensPerSecond by viewModel.tokensPerSecond.collectAsState()
    val isAgentMode by viewModel.isAgentMode.collectAsState()
    val agentSteps by viewModel.agentSteps.collectAsState()
    val pendingWrite by viewModel.pendingWrite.collectAsState()
    val pendingDelete by viewModel.pendingDelete.collectAsState()
    val pendingGitAction by viewModel.pendingGitAction.collectAsState()
    val currentSessionId by viewModel.currentSessionId.collectAsState()
    val activeSessions by viewModel.activeSessions.collectAsState()
    val archivedSessions by viewModel.archivedSessions.collectAsState()
    val thermalStatus by viewModel.thermalStatus.collectAsState()
    val ramAvailMb by viewModel.ramAvailMb.collectAsState()
    val ramTotalMb by viewModel.ramTotalMb.collectAsState()
    val lastGenTps by viewModel.lastGenTps.collectAsState()
    val backendInfo by viewModel.backendInfo.collectAsState()
    val sessionTokensTotal by viewModel.sessionTokensTotal.collectAsState()
    val codeBlockFontSize by viewModel.codeBlockFontSize.collectAsState()
    val cpuLoad by viewModel.cpuLoad.collectAsState()
    val gpuLoad by viewModel.gpuLoad.collectAsState()
    val detectedQuestions by viewModel.detectedQuestions.collectAsState()

    val attachedFilePath by viewModel.attachedFilePath.collectAsState()

    // Receive file path from FileBrowserScreen via savedStateHandle
    LaunchedEffect(navBackStackEntry) {
        navBackStackEntry?.savedStateHandle
            ?.getStateFlow<String?>("selected_file", null)
            ?.collect { path ->
                if (path != null) {
                    viewModel.attachFileToChat(path)
                    navBackStackEntry.savedStateHandle["selected_file"] = null
                }
            }
    }

    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val context = LocalContext.current

    // Search state (Phase 1 v1.0.0)
    var searchActive by rememberSaveable { mutableStateOf(false) }
    val displayedMessages = if (searchActive) filteredMessages else messages

    // Export state
    var showExportDialog by remember { mutableStateOf(false) }
    var exportSnackbarMsg by remember { mutableStateOf<String?>(null) }

    // Multi-select state
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    val selectionMode = selectedIds.isNotEmpty()

    // ---- Smart auto-scroll: respect user scroll position ----
    val isAtBottom by remember {
        derivedStateOf {
            val layout = listState.layoutInfo
            val totalItems = layout.totalItemsCount
            if (totalItems == 0) return@derivedStateOf true
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= totalItems - 2
        }
    }
    var autoScrollEnabled by rememberSaveable { mutableStateOf(true) }
    var chatTopOffsetDp by rememberSaveable { mutableFloatStateOf(0f) }
    val density = LocalDensity.current

    LaunchedEffect(isAtBottom) {
        if (isAtBottom) autoScrollEnabled = true
    }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress && !isAtBottom) {
            autoScrollEnabled = false
        }
    }

    LaunchedEffect(isGenerating) {
        if (isGenerating) autoScrollEnabled = true
    }

    val scrollTrigger = displayedMessages.size + agentSteps.size + streamingContent.length +
        (if (pendingWrite != null) 1 else 0) + (if (pendingDelete != null) 1 else 0) +
        (if (pendingGitAction != null) 1 else 0)
    LaunchedEffect(scrollTrigger) {
        if (!searchActive && autoScrollEnabled) {
            val lastIndex = listState.layoutInfo.totalItemsCount - 1
            if (lastIndex >= 0) {
                kotlinx.coroutines.delay(50)
                listState.animateScrollToItem(lastIndex)
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !isGenerating,
        drawerContent = {
            ModalDrawerSheet {
                SessionDrawerContent(
                    activeSessions = activeSessions,
                    archivedSessions = archivedSessions,
                    currentSessionId = currentSessionId,
                    onSessionClick = { id ->
                        viewModel.selectSession(id)
                        scope.launch { drawerState.close() }
                    },
                    onNewSession = {
                        viewModel.createNewSession()
                        scope.launch { drawerState.close() }
                    },
                    onArchiveSession = { id -> viewModel.archiveSession(id) },
                    onUnarchiveSession = { id -> viewModel.unarchiveSession(id) },
                    onDeleteSession = { id -> viewModel.deleteSession(id) }
                )
            }
        }
    ) {
        Column(modifier = Modifier.fillMaxSize().imePadding()) {
            if (selectionMode) {
                // Selection mode toolbar
                TopAppBar(
                    title = { Text("${selectedIds.size} выбрано") },
                    navigationIcon = {
                        // Cancel selection
                        IconButton(onClick = { selectedIds = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = "Отмена")
                        }
                    },
                    actions = {
                        // Select all
                        IconButton(onClick = {
                            selectedIds = displayedMessages.filter { it.id > 0 }.map { it.id }.toSet()
                        }) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Выбрать все")
                        }
                        // Delete selected
                        IconButton(onClick = {
                            viewModel.deleteMessages(selectedIds)
                            selectedIds = emptySet()
                        }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Удалить выбранные",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                )
            } else {
                // Compact top bar — no app title, just essential controls
                var statusPanelExpanded by rememberSaveable { mutableStateOf(false) }

                TopAppBar(
                    title = {
                        // Compact inline status instead of title
                        ModelStatusIndicator(
                            status = modelStatus,
                            tokensPerSecond = tokensPerSecond,
                            lastGenTps = lastGenTps,
                            thermalStatus = thermalStatus,
                            availRamMb = ramAvailMb,
                            totalRamMb = ramTotalMb,
                            backendInfo = backendInfo,
                            sessionTokensTotal = sessionTokensTotal,
                            cpuLoad = cpuLoad,
                            gpuLoad = gpuLoad
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.sessions_nav))
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.toggleAgentMode() },
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = if (isAgentMode)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Icon(Icons.Default.SmartToy, contentDescription = stringResource(R.string.agent_mode))
                        }
                        // File attach
                        if (onNavigateToFileBrowser != null) {
                            IconButton(onClick = onNavigateToFileBrowser) {
                                Icon(Icons.Default.AttachFile, contentDescription = "Attach file")
                            }
                        }
                        // CLI Terminal
                        if (onNavigateToCli != null) {
                            IconButton(onClick = onNavigateToCli) {
                                Icon(Icons.Default.Code, contentDescription = "CLI")
                            }
                        }
                        // Toggle expanded status panel
                        IconButton(onClick = { statusPanelExpanded = !statusPanelExpanded }) {
                            Icon(
                                if (statusPanelExpanded) Icons.Default.KeyboardArrowUp
                                else Icons.Default.KeyboardArrowDown,
                                contentDescription = "Панель статуса"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                // Expandable status panel (~1/3 of screen)
                androidx.compose.animation.AnimatedVisibility(
                    visible = statusPanelExpanded,
                    enter = androidx.compose.animation.expandVertically(
                        animationSpec = androidx.compose.animation.core.tween(300)
                    ),
                    exit = androidx.compose.animation.shrinkVertically(
                        animationSpec = androidx.compose.animation.core.tween(300)
                    )
                ) {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        // Row 1: Search + Export + More actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly
                        ) {
                            IconButton(onClick = {
                                searchActive = !searchActive
                                if (!searchActive) viewModel.clearSearch()
                            }) {
                                Icon(
                                    if (searchActive) Icons.Default.Close else Icons.Default.Search,
                                    contentDescription = stringResource(
                                        if (searchActive) R.string.search_close else R.string.search_open
                                    )
                                )
                            }
                            IconButton(onClick = { showExportDialog = true }) {
                                Icon(Icons.Default.Share, contentDescription = "Экспорт")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Row 2: Detailed system metrics
                        val thermalLabel = when (thermalStatus) {
                            0 -> "Норма"
                            1 -> "Тепло"
                            2 -> "Горячо"
                            3 -> "Очень горячо"
                            else -> "Критично"
                        }
                        val thermalColor = when (thermalStatus) {
                            0 -> Color.Green
                            1 -> Color(0xFFFFD700)
                            2 -> Color(0xFFFFA500)
                            3 -> Color(0xFFFF4500)
                            else -> Color.Red
                        }

                        // RAM
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Memory, contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("RAM: ", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (ramTotalMb > 0) {
                                val usedGb = (ramTotalMb - ramAvailMb) / 1024f
                                val totalGb = ramTotalMb / 1024f
                                Text("%.1f / %.1f GB".format(usedGb, totalGb),
                                    style = MaterialTheme.typography.bodySmall)
                                Spacer(modifier = Modifier.width(8.dp))
                                LinearProgressIndicator(
                                    progress = { ((ramTotalMb - ramAvailMb).toFloat() / ramTotalMb) },
                                    modifier = Modifier.weight(1f).height(6.dp),
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            } else {
                                Text("—", style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // CPU Load
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("CPU", style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.width(32.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            val cpuPct = cpuLoad.coerceIn(0f, 100f)
                            Text("${cpuPct.toInt()}%", style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.width(36.dp))
                            LinearProgressIndicator(
                                progress = { cpuPct / 100f },
                                modifier = Modifier.weight(1f).height(6.dp),
                                color = when {
                                    cpuPct > 90f -> Color.Red
                                    cpuPct > 70f -> Color(0xFFFFA500)
                                    else -> MaterialTheme.colorScheme.primary
                                },
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // GPU Load
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("GPU", style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.width(32.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            val gpuPct = gpuLoad.coerceIn(0f, 100f)
                            Text(
                                text = if (gpuPct < 0f) "N/A" else "${gpuPct.toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.width(36.dp)
                            )
                            LinearProgressIndicator(
                                progress = { if (gpuPct < 0f) 0f else gpuPct / 100f },
                                modifier = Modifier.weight(1f).height(6.dp),
                                color = when {
                                    gpuPct > 90f -> Color.Red
                                    gpuPct > 70f -> Color(0xFFFFA500)
                                    else -> Color(0xFF4FC3F7)
                                },
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Thermal + Backend + Tokens
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Thermostat, contentDescription = null,
                                    modifier = Modifier.size(14.dp), tint = thermalColor)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(thermalLabel, style = MaterialTheme.typography.bodySmall,
                                    color = thermalColor)
                            }
                            Text(backendInfo, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (sessionTokensTotal > 0) {
                                Text("${sessionTokensTotal} tokens",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // Search bar (Phase 1 v1.0.0)
            if (searchActive) {
                TextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    placeholder = { Text(stringResource(R.string.search_placeholder)) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.clearSearch() }) {
                                Icon(Icons.Default.Close, contentDescription = null)
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {}),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
            }

            val customTheme = LocalCustomTheme.current

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // Consume taps on empty space so SelectionContainer clears text selection
                    .pointerInput(Unit) { detectTapGestures { } }
            ) {
                // Custom background image
                if (customTheme.backgroundUri.isNotBlank()) {
                    val bgBitmap = remember(customTheme.backgroundUri) {
                        try {
                            val stream = context.contentResolver.openInputStream(
                                Uri.parse(customTheme.backgroundUri)
                            )
                            stream?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
                        } catch (e: Exception) { Timber.w(e, "Failed to load background image"); null }
                    }
                    bgBitmap?.let { bmp ->
                        Image(
                            bitmap = bmp,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .matchParentSize()
                                .alpha(customTheme.backgroundOpacity)
                        )
                    }
                }
                // Chat drag handle — drag top edge to reposition messages area vertically
                if (messages.isNotEmpty() || isGenerating) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(20.dp)
                            .align(Alignment.TopCenter)
                            .pointerInput(Unit) {
                                detectVerticalDragGestures { change, dragAmount ->
                                    change.consume()
                                    chatTopOffsetDp = (chatTopOffsetDp + dragAmount / density.density)
                                        .coerceIn(0f, 300f)
                                }
                            }
                            .pointerInput(Unit) {
                                detectTapGestures(onDoubleTap = { chatTopOffsetDp = 0f })
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier.size(width = 36.dp, height = 4.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                .copy(alpha = if (chatTopOffsetDp > 4f) 0.5f else 0.2f)
                        ) {}
                    }
                }

                if (messages.isEmpty() && !isGenerating) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.welcome_title),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isAgentMode) stringResource(R.string.welcome_agent_subtitle)
                                   else stringResource(R.string.welcome_subtitle),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isAgentMode) stringResource(R.string.welcome_agent_hint)
                                   else stringResource(R.string.welcome_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = chatTopOffsetDp.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        items(displayedMessages, key = { it.id }) { message ->
                            if (message.role == Role.ASSISTANT && message.content.isEmpty() && isGenerating) {
                                // Don't render the empty placeholder
                            } else {
                                MessageBubble(
                                    message = message,
                                    onDelete = if (!selectionMode) { { viewModel.deleteMessage(it) } } else null,
                                    onRetry = if (!selectionMode) { { viewModel.retryLastUserMessage(it) } } else null,
                                    isSelected = selectedIds.contains(message.id),
                                    selectionMode = selectionMode,
                                    onToggleSelect = { id ->
                                        selectedIds = if (selectedIds.contains(id))
                                            selectedIds - id
                                        else
                                            selectedIds + id
                                    },
                                    codeBlockFontSize = codeBlockFontSize
                                )
                            }
                        }

                            if (isAgentMode && agentSteps.isNotEmpty()) {
                                items(agentSteps.size, key = { "agent_step_$it" }) { index ->
                                    DisableSelection {
                                        AgentStepCard(step = agentSteps[index])
                                    }
                                }
                            }

                            pendingWrite?.let { pw ->
                                item(key = "pending_write") {
                                    DisableSelection {
                                        WriteApprovalCard(
                                            pendingWrite = pw,
                                            onApprove = { viewModel.approveWrite() },
                                            onReject = { viewModel.rejectWrite() }
                                        )
                                    }
                                }
                            }

                            pendingDelete?.let { pd ->
                                item(key = "pending_delete") {
                                    DisableSelection {
                                        DeleteApprovalCard(
                                            pendingDelete = pd,
                                            onApprove = { viewModel.approveDelete() },
                                            onReject = { viewModel.rejectDelete() }
                                        )
                                    }
                                }
                            }

                            pendingGitAction?.let { ga ->
                                item(key = "pending_git") {
                                    DisableSelection {
                                        GitActionApprovalCard(
                                            pendingGitAction = ga,
                                            onApprove = { viewModel.approveGitAction() },
                                            onReject = { viewModel.rejectGitAction() }
                                        )
                                    }
                                }
                            }

                            // Show streaming bubble only when DB placeholder hasn't been
                            // updated yet. Once updateMessageContent() is called, the last
                            // message in displayedMessages will equal streamingContent —
                            // at that point we hide the streaming bubble to avoid duplicates.
                            val lastMsgContent = displayedMessages.lastOrNull()?.content
                            if (isGenerating && streamingContent.isNotEmpty()
                                && lastMsgContent != streamingContent) {
                                item(key = "streaming") {
                                    MessageBubble(
                                        message = ChatMessage(
                                            id = -1,
                                            sessionId = 0,
                                            role = Role.ASSISTANT,
                                            content = streamingContent
                                        )
                                    )
                                }
                            } else if (isGenerating && pendingWrite == null && pendingDelete == null && pendingGitAction == null) {
                                item(key = "thinking") {
                                    DisableSelection {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = if (isAgentMode) stringResource(R.string.agent_thinking)
                                                       else stringResource(R.string.thinking),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                }

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

                exportSnackbarMsg?.let { msg ->
                    Snackbar(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                        action = {
                            TextButton(onClick = { exportSnackbarMsg = null }) {
                                Text(stringResource(R.string.dismiss))
                            }
                        }
                    ) { Text(msg) }
                }
            }

            // Export dialog
            if (showExportDialog) {
                AlertDialog(
                    onDismissRequest = { showExportDialog = false },
                    title = { Text("Экспорт чата") },
                    text = { Text("Выберите формат для сохранения в Downloads") },
                    confirmButton = {
                        TextButton(onClick = {
                            showExportDialog = false
                            val (filename, content) = viewModel.buildExportContent(ChatViewModel.ExportFormat.MARKDOWN)
                            exportSnackbarMsg = saveToDownloads(context, filename, content)
                        }) { Text("Markdown") }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showExportDialog = false
                            val (filename, content) = viewModel.buildExportContent(ChatViewModel.ExportFormat.JSON)
                            exportSnackbarMsg = saveToDownloads(context, filename, content)
                        }) { Text("JSON") }
                    }
                )
            }

            // Question chips: shown after generation when model asks a question or offers options
            if (detectedQuestions.isNotEmpty() && !isGenerating) {
                QuestionChipsRow(
                    questions = detectedQuestions,
                    onQuestionSelected = { viewModel.submitDetectedQuestion(it) },
                    onDismiss = { viewModel.dismissDetectedQuestions() }
                )
            }

            // Attached file chip
            attachedFilePath?.let { path ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.AttachFile,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = java.io.File(path).name,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    IconButton(onClick = { viewModel.clearAttachedFile() }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                    }
                }
            }

            InputBar(
                onSend = { viewModel.sendMessage(it) },
                onStop = { viewModel.stopGeneration() },
                isGenerating = isGenerating,
                isAgentMode = isAgentMode,
                enabled = !isGenerating,
            )
        }
    }
}

/**
 * Horizontal scrollable row of suggestion chips extracted from the last assistant response.
 * Tapping a chip submits it as a new user message.
 */
@Composable
private fun QuestionChipsRow(
    questions: List<String>,
    onQuestionSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.question_chips_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.question_chips_dismiss),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp)
        ) {
            items(questions.size) { i ->
                androidx.compose.material3.SuggestionChip(
                    onClick = { onQuestionSelected(questions[i]) },
                    label = {
                        Text(
                            text = questions[i],
                            maxLines = 2,
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    modifier = Modifier.widthIn(max = 240.dp)
                )
            }
        }
    }
}

/** Save [content] to Downloads/[filename] via MediaStore. Returns a status message. */
private fun saveToDownloads(context: Context, filename: String, content: String): String {
    return try {
        val mimeType = if (filename.endsWith(".json")) "application/json" else "text/markdown"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, filename)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        } else null

        if (uri != null) {
            resolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            "Сохранено: Downloads/$filename"
        } else {
            "Ошибка сохранения"
        }
    } catch (e: Exception) {
        "Ошибка: ${e.message}"
    }
}
