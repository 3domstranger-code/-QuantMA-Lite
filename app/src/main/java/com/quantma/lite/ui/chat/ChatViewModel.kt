package com.quantma.lite.ui.chat

import android.content.Context
import timber.log.Timber
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantma.lite.data.agent.AgentPromptFormatter
import com.quantma.lite.data.agent.GitOperationsAdapter
import com.quantma.lite.data.agent.HooksEngine
import com.quantma.lite.data.agent.RulesEngine
import com.quantma.lite.data.agent.SkillRouter
import com.quantma.lite.data.agent.ToolExecutor
import com.quantma.lite.data.agent.ToolParser
import com.quantma.lite.sas.CommandParser
import com.quantma.lite.sas.FileHistoryManager
import com.quantma.lite.sas.SasConfig
import com.quantma.lite.sas.ShellExecutor
import com.quantma.lite.sas.ToolResponse
import com.quantma.lite.sas.ToolType
import com.quantma.lite.data.inference.DialogCompressor
import com.quantma.lite.data.inference.DeviceCapabilities
import com.quantma.lite.data.inference.InferenceAutoConfig
import com.quantma.lite.data.inference.InferenceState
import com.quantma.lite.data.inference.LlamaInference
import com.quantma.lite.data.performance.CpuGpuMonitor
import com.quantma.lite.service.InferenceService
import com.quantma.lite.data.performance.MemoryMonitor
import com.quantma.lite.data.performance.ThermalMonitor
import com.quantma.lite.data.inference.PromptFormatter
import com.quantma.lite.data.local.db.AgentConfigDao
import com.quantma.lite.data.local.db.entity.AgentConfigEntity
import com.quantma.lite.data.local.preferences.SettingsDataStore
import com.quantma.lite.domain.model.AgentStep
import com.quantma.lite.domain.model.AgentStepStatus
import com.quantma.lite.domain.model.HookTrigger
import com.quantma.lite.domain.model.ChatMessage
import com.quantma.lite.domain.model.ChatSession
import com.quantma.lite.domain.model.PendingDelete
import com.quantma.lite.domain.model.PendingGitAction
import com.quantma.lite.domain.model.PendingWrite
import com.quantma.lite.domain.model.Role
import com.quantma.lite.domain.model.ToolCall
import com.quantma.lite.domain.model.ToolResult
import com.quantma.lite.domain.repository.ChatRepository
import com.quantma.lite.domain.repository.FileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val llamaInference: LlamaInference,
    private val settingsDataStore: SettingsDataStore,
    private val toolParser: ToolParser,
    private val toolExecutor: ToolExecutor,
    private val fileRepository: FileRepository,
    private val commandParser: CommandParser,
    private val gitOperationsAdapter: GitOperationsAdapter,
    private val agentConfigDao: AgentConfigDao,
    private val skillRouter: SkillRouter,
    private val rulesEngine: RulesEngine,
    private val hooksEngine: HooksEngine,
    private val thermalMonitor: ThermalMonitor,
    private val memoryMonitor: MemoryMonitor,
    private val cpuGpuMonitor: CpuGpuMonitor,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    companion object {
        private const val MAX_ACTIVE_SESSIONS = 10
    }

    /** Build a SAS ShellExecutor scoped to the given workingDir. */
    private fun buildShellExecutor(workingDir: String): ShellExecutor {
        val config = SasConfig(workingDir = workingDir)
        return ShellExecutor(
            config = config,
            gitOperations = gitOperationsAdapter,
            historyManager = FileHistoryManager(config.workingDir)
        )
    }

    // ---- Session management ----

    private val _currentSessionId = MutableStateFlow(0L)
    val currentSessionId: StateFlow<Long> = _currentSessionId.asStateFlow()

    val activeSessions = chatRepository.getActiveSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val archivedSessions = chatRepository.getArchivedSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---- Chat state ----

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    // ---- Active Agent Config (Phase 2 v1.1.0) ----
    private val activeAgentConfig: StateFlow<AgentConfigEntity?> =
        agentConfigDao.observeDefaultConfig()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Build full system prompt from AgentConfig (systemPrompt + customInstructions). */
    private fun buildSystemPrompt(config: AgentConfigEntity): String = buildString {
        append(config.systemPrompt)
        if (config.customInstructions.isNotEmpty()) {
            append("\n\n")
            append(config.customInstructions)
        }
    }

    // ---- Chat Search (Phase 1 v1.0.0) ----
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val filteredMessages: StateFlow<List<ChatMessage>> =
        _messages.combine(_searchQuery) { msgs, query ->
            if (query.isBlank()) msgs
            else msgs.filter { it.content.contains(query, ignoreCase = true) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun clearSearch() { _searchQuery.value = "" }

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _streamingContent = MutableStateFlow("")
    val streamingContent: StateFlow<String> = _streamingContent.asStateFlow()

    private val _tokensPerSecond = MutableStateFlow(0f)
    val tokensPerSecond: StateFlow<Float> = _tokensPerSecond.asStateFlow()

    // Persists after generation ends (not reset to 0)
    private val _lastGenTps = MutableStateFlow(0f)
    val lastGenTps: StateFlow<Float> = _lastGenTps.asStateFlow()

    // Cumulative tokens generated in the current session
    private val _sessionTokensTotal = MutableStateFlow(0)
    val sessionTokensTotal: StateFlow<Int> = _sessionTokensTotal.asStateFlow()

    // Code block font size from settings
    val codeBlockFontSize: StateFlow<Float> = settingsDataStore.codeBlockFontSize
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 13f)

    val modelStatus: StateFlow<ModelStatus> = llamaInference.state
        .combine(_isGenerating) { inferState, generating ->
            when {
                generating -> ModelStatus.GENERATING
                inferState is InferenceState.Loaded -> ModelStatus.READY
                inferState is InferenceState.Loading -> ModelStatus.LOADING
                inferState is InferenceState.Error -> ModelStatus.ERROR
                inferState is InferenceState.Generating -> ModelStatus.GENERATING
                else -> ModelStatus.NOT_LOADED
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ModelStatus.NOT_LOADED)

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // ---- Performance monitoring (Phase 7 v1.6.0) ----

    val backendInfo: StateFlow<String> = llamaInference.backendInfo

    val thermalStatus: StateFlow<Int> = thermalMonitor.thermalStatus

    private val _ramAvailMb = MutableStateFlow(0L)
    val ramAvailMb: StateFlow<Long> = _ramAvailMb.asStateFlow()

    private val _ramTotalMb = MutableStateFlow(0L)
    val ramTotalMb: StateFlow<Long> = _ramTotalMb.asStateFlow()

    // CPU/GPU load (v0.11.0)
    val cpuLoad: StateFlow<Float> = cpuGpuMonitor.cpuLoad
    val gpuLoad: StateFlow<Float> = cpuGpuMonitor.gpuLoad

    // ---- Detected model questions (shown as clickable chips after generation) ----
    private val _detectedQuestions = MutableStateFlow<List<String>>(emptyList())
    val detectedQuestions: StateFlow<List<String>> = _detectedQuestions.asStateFlow()

    // ---- File attachment (Block 3) ----
    private val _attachedFilePath = MutableStateFlow<String?>(null)
    val attachedFilePath: StateFlow<String?> = _attachedFilePath.asStateFlow()
    fun attachFileToChat(path: String) { _attachedFilePath.value = path }
    fun clearAttachedFile() { _attachedFilePath.value = null }

    fun dismissDetectedQuestions() { _detectedQuestions.value = emptyList() }

    fun submitDetectedQuestion(question: String) {
        dismissDetectedQuestions()
        val sessionId = _currentSessionId.value
        if (sessionId > 0L) {
            viewModelScope.launch {
                chatRepository.insertMessage(
                    ChatMessage(sessionId = sessionId, role = Role.USER, content = question)
                )
                if (_isAgentMode.value) runAgentLoop(question) else generateResponse()
            }
        }
    }

    /** Extract questions or numbered options from assistant text after generation. */
    private fun detectQuestionsInText(text: String): List<String> {
        if (text.isBlank()) return emptyList()

        // 1. Numbered options: "1. Option text\n2. Option text"
        val numberedPattern = Regex("""^\s*\d+[.)]\s+(.+)$""", RegexOption.MULTILINE)
        val numbered = numberedPattern.findAll(text).map { it.groupValues[1].trim() }.toList()
        if (numbered.size >= 2) return numbered.take(6)

        // 2. Lettered options: "a) Option\nb) Option"
        val letteredPattern = Regex("""^\s*[a-dа-г][.)]\s+(.+)$""", RegexOption.MULTILINE)
        val lettered = letteredPattern.findAll(text).map { it.groupValues[1].trim() }.toList()
        if (lettered.size >= 2) return lettered.take(6)

        // 3. Lines ending with "?" in the last 5 lines — model is asking
        val lastLines = text.trimEnd().lines().takeLast(5)
        val questions = lastLines.filter { line ->
            val t = line.trim()
            t.endsWith("?") && t.length in 10..200
        }.map { it.trim() }
        if (questions.isNotEmpty()) return questions.take(3)

        return emptyList()
    }

    private val _isAgentMode = MutableStateFlow(false)
    val isAgentMode: StateFlow<Boolean> = _isAgentMode.asStateFlow()

    private val _agentSteps = MutableStateFlow<List<AgentStep>>(emptyList())
    val agentSteps: StateFlow<List<AgentStep>> = _agentSteps.asStateFlow()

    private val _pendingWrite = MutableStateFlow<PendingWrite?>(null)
    val pendingWrite: StateFlow<PendingWrite?> = _pendingWrite.asStateFlow()

    private val _pendingDelete = MutableStateFlow<PendingDelete?>(null)
    val pendingDelete: StateFlow<PendingDelete?> = _pendingDelete.asStateFlow()

    private val _pendingGitAction = MutableStateFlow<PendingGitAction?>(null)
    val pendingGitAction: StateFlow<PendingGitAction?> = _pendingGitAction.asStateFlow()

    private var generationJob: Job? = null
    private var writeApprovalDeferred: CompletableDeferred<Boolean>? = null
    private var deleteApprovalDeferred: CompletableDeferred<Boolean>? = null
    private var gitActionDeferred: CompletableDeferred<Boolean>? = null
    private var messagesCollectionJob: Job? = null

    init {
        // Initialize llama backend
        viewModelScope.launch {
            val nativeLibDir = appContext.applicationInfo.nativeLibraryDir
            llamaInference.initialize(nativeLibDir)
            Timber.i("LlamaInference initialized, nativeLibDir=$nativeLibDir")
        }

        // Poll RAM every 5s for ModelStatusIndicator
        viewModelScope.launch {
            _ramTotalMb.value = memoryMonitor.getTotalRamMb()
            while (true) {
                _ramAvailMb.value = memoryMonitor.getAvailableRamMb()
                kotlinx.coroutines.delay(5_000)
            }
        }

        // Auto-load model when modelPath changes
        viewModelScope.launch {
            settingsDataStore.modelPath
                .distinctUntilChanged()
                .collect { path ->
                    if (path.isNotEmpty()) {
                        loadModelFromSettings(path)
                    }
                }
        }

        // Manual model reload trigger (from Settings)
        viewModelScope.launch {
            settingsDataStore.modelReloadTrigger.collect {
                val path = settingsDataStore.modelPath.first()
                if (path.isNotEmpty()) {
                    Timber.i("Manual model reload requested")
                    loadModelFromSettings(path)
                }
            }
        }

        // Seed built-in skills, rules, hooks (Phase 5 v1.4.0) — no-op if already seeded
        viewModelScope.launch {
            skillRouter.seedBuiltInSkills()
            rulesEngine.seedDefaultRules()
            hooksEngine.seedDefaultHooks()
        }

        // Restore or create session
        viewModelScope.launch {
            val savedId = settingsDataStore.currentSessionId.first()
            if (savedId > 0L) {
                val session = chatRepository.getSession(savedId)
                if (session != null) {
                    switchToSession(savedId)
                    return@launch
                }
            }
            // No saved session or it was deleted — create first session
            val id = chatRepository.createSession("New Chat")
            switchToSession(id)
        }
    }

    private fun switchToSession(sessionId: Long) {
        _currentSessionId.value = sessionId
        _agentSteps.value = emptyList()
        _sessionTokensTotal.value = 0

        // Cancel and restart message collection for new session
        messagesCollectionJob?.cancel()
        messagesCollectionJob = viewModelScope.launch {
            chatRepository.getMessagesForSession(sessionId).collect { msgs ->
                _messages.value = msgs
            }
        }

        // Persist current session
        viewModelScope.launch {
            settingsDataStore.setCurrentSessionId(sessionId)
        }
    }

    fun selectSession(sessionId: Long) {
        if (sessionId == _currentSessionId.value) return
        if (_isGenerating.value) return
        switchToSession(sessionId)
    }

    fun createNewSession() {
        if (_isGenerating.value) return
        viewModelScope.launch {
            val activeCount = chatRepository.getActiveSessionCount()
            if (activeCount >= MAX_ACTIVE_SESSIONS) {
                _errorMessage.value = "Max $MAX_ACTIVE_SESSIONS active chats. Archive or delete a chat first."
                return@launch
            }
            val id = chatRepository.createSession("New Chat")
            switchToSession(id)
        }
    }

    fun archiveSession(sessionId: Long) {
        if (_isGenerating.value) return
        viewModelScope.launch {
            chatRepository.archiveSession(sessionId)
            // If we archived the current session, switch to another
            if (sessionId == _currentSessionId.value) {
                val remaining = chatRepository.getActiveSessions().first()
                if (remaining.isNotEmpty()) {
                    switchToSession(remaining.first().id)
                } else {
                    val id = chatRepository.createSession("New Chat")
                    switchToSession(id)
                }
            }
        }
    }

    fun unarchiveSession(sessionId: Long) {
        viewModelScope.launch {
            val activeCount = chatRepository.getActiveSessionCount()
            if (activeCount >= MAX_ACTIVE_SESSIONS) {
                _errorMessage.value = "Max $MAX_ACTIVE_SESSIONS active chats. Archive or delete a chat first."
                return@launch
            }
            chatRepository.unarchiveSession(sessionId)
        }
    }

    fun deleteSession(sessionId: Long) {
        if (_isGenerating.value) return
        viewModelScope.launch {
            chatRepository.deleteSession(sessionId)
            if (sessionId == _currentSessionId.value) {
                val remaining = chatRepository.getActiveSessions().first()
                if (remaining.isNotEmpty()) {
                    switchToSession(remaining.first().id)
                } else {
                    val id = chatRepository.createSession("New Chat")
                    switchToSession(id)
                }
            }
        }
    }

    // ---- Sampling params resolution (auto/manual) ----

    /** Simple holder for resolved sampling params */
    private data class SamplingParams(
        val temperature: Float,
        val topP: Float,
        val topK: Int,
        val minP: Float,
        val repeatPenalty: Float,
        val penaltyLastN: Int
    )

    /** Resolve current sampling params respecting auto/manual mode for each. */
    private suspend fun resolveCurrentSamplingParams(): SamplingParams {
        return SamplingParams(
            temperature = if (settingsDataStore.autoTemperature.first()) InferenceAutoConfig.autoTemperature()
                          else settingsDataStore.temperature.first(),
            topP = if (settingsDataStore.autoTopP.first()) InferenceAutoConfig.autoTopP()
                   else settingsDataStore.topP.first(),
            topK = if (settingsDataStore.autoTopK.first()) InferenceAutoConfig.autoTopK()
                   else settingsDataStore.topK.first(),
            minP = if (settingsDataStore.autoMinP.first()) InferenceAutoConfig.autoMinP()
                   else settingsDataStore.minP.first(),
            repeatPenalty = if (settingsDataStore.autoRepeatPenalty.first()) InferenceAutoConfig.autoRepeatPenalty()
                            else settingsDataStore.repeatPenalty.first(),
            penaltyLastN = if (settingsDataStore.autoPenaltyLastN.first()) InferenceAutoConfig.autoPenaltyLastN()
                           else settingsDataStore.penaltyLastN.first()
        )
    }

    // ---- Model loading ----

    private suspend fun loadModelFromSettings(path: String) {
        val modelFile = File(path)
        if (!modelFile.exists()) {
            _errorMessage.value = "Model file not found: ${modelFile.name}"
            Timber.e("Model file not found: $path")
            return
        }
        if (!modelFile.canRead()) {
            _errorMessage.value = "Cannot read model file: ${modelFile.name}"
            Timber.e("Cannot read model file: $path")
            return
        }

        val fileSizeMb = modelFile.length() / (1024.0 * 1024.0)
        val modelSizeMb = DeviceCapabilities.getModelSizeMb(path)

        // Detect device capabilities for auto-config
        val gpuBackend = llamaInference.backendInfo.value
        val profile = DeviceCapabilities.detectProfile(appContext, gpuBackend)

        // Resolve each parameter: auto or manual
        val useGpu = settingsDataStore.useGpu.first()
        val resolved = InferenceAutoConfig.resolve(
            profile = profile,
            modelSizeMb = modelSizeMb,
            manualThreads = if (!settingsDataStore.autoThreads.first()) settingsDataStore.nThreads.first() else null,
            manualContextSize = if (!settingsDataStore.autoContextSize.first()) settingsDataStore.contextSize.first() else null,
            manualBatchSize = if (!settingsDataStore.autoBatchSize.first()) settingsDataStore.batchSize.first() else null,
            manualGpuLayers = if (!settingsDataStore.autoGpuLayers.first()) {
                if (useGpu) settingsDataStore.gpuLayers.first() else 0
            } else null,
            manualFlashAttention = if (!settingsDataStore.autoFlashAttention.first()) settingsDataStore.flashAttention.first() else null,
            manualMlock = if (!settingsDataStore.autoMlock.first()) settingsDataStore.mlock.first() else null
        )

        // If GPU is explicitly disabled by user, force 0 layers regardless of auto
        val gpuLayers = if (!useGpu && settingsDataStore.autoGpuLayers.first()) 0 else resolved.nGpuLayers

        Timber.i("Loading model: ${modelFile.name} (%.1f MB, threads=${resolved.nThreads}, ctx=${resolved.contextSize}, " +
                "batch=${resolved.batchSize}, gpu=$gpuLayers, flash=${resolved.flashAttention}, mlock=${resolved.mlock})".format(fileSizeMb))

        _errorMessage.value = null

        val result = llamaInference.loadModel(
            path, resolved.nThreads, resolved.contextSize, gpuLayers,
            resolved.batchSize, resolved.flashAttention, resolved.mlock
        )
        result.onFailure { e ->
            val msg = e.message ?: "Unknown error"
            val userMsg = when {
                msg.contains("out of memory", ignoreCase = true) || msg.contains("OOM") ->
                    "Out of memory loading model. Try a smaller model or reduce context size."
                msg.contains("invalid", ignoreCase = true) || msg.contains("format") ->
                    "Invalid model file. Make sure it's a valid GGUF file."
                else -> "Failed to load model: $msg"
            }
            _errorMessage.value = userMsg
            Timber.e(e, "Model load failed")
        }
        result.onSuccess {
            _errorMessage.value = null
            Timber.i("Model loaded: ${modelFile.name} (%.1f MB)".format(fileSizeMb))
            // Phase 10 (v1.9.0): Auto-load LoRA adapter if enabled
            val loraEnabled = settingsDataStore.loraEnabled.first()
            val loraPath = settingsDataStore.loraPath.first()
            val loraScale = settingsDataStore.loraScale.first()
            if (loraEnabled && loraPath.isNotEmpty() && File(loraPath).exists()) {
                llamaInference.loadLoraAdapter(loraPath, loraScale)
                    .onFailure { err -> _errorMessage.value = "LoRA load failed: ${err.message}" }
                    .onSuccess { Timber.i("LoRA loaded: $loraPath (scale=$loraScale)") }
            } else if (loraEnabled && loraPath.isNotEmpty() && !File(loraPath).exists()) {
                _errorMessage.value = "LoRA file not found: $loraPath"
                Timber.w("LoRA file does not exist: $loraPath")
            }
        }
    }

    // ---- Chat & Agent ----

    private fun handleHelpCommand() {
        val sessionId = _currentSessionId.value
        if (sessionId == 0L) return

        viewModelScope.launch {
            val userMsg = ChatMessage(
                sessionId = sessionId,
                role = Role.USER,
                content = "/help"
            )
            chatRepository.insertMessage(userMsg)

            val helpText = buildString {
                appendLine("## QuantMA Lite Help")
                appendLine()
                appendLine("### Modes")
                appendLine("- **Chat mode** — regular conversation with AI")
                appendLine("- **Agent mode** (robot icon) — AI can read/write files, run git commands")
                appendLine()
                appendLine("### What the agent can do")
                appendLine()
                appendLine("**File operations:**")
                appendLine("- Read, create, edit, delete files")
                appendLine("- Search text in files (grep)")
                appendLine("- Browse directories")
                appendLine()
                appendLine("**Git:**")
                appendLine("- status, log, diff, branch")
                appendLine("- add, commit, push, pull")
                appendLine("- create/delete/checkout branches")
                appendLine("- merge, stash")
                appendLine()
                appendLine("**Code quality:**")
                appendLine("- Lint, format, count lines")
                appendLine("- Find TODO/FIXME comments")
                appendLine()
                appendLine("### Example requests")
                appendLine("- \"Read src/Main.kt\"")
                appendLine("- \"Find all TODO comments\"")
                appendLine("- \"Create a Utils.kt with a formatDate function\"")
                appendLine("- \"Show git status\"")
                appendLine("- \"Commit all changes\"")
                appendLine("- \"Refactor UserService to use coroutines\"")
                appendLine("- \"Explain this code\"")
                appendLine()
                appendLine("### Safety")
                appendLine("- Destructive operations (write, delete, push, merge) require your approval")
                appendLine("- Deleted files go to `.sas_trash/` and can be recovered")
                appendLine("- File snapshots are saved before every write (undo available)")
                appendLine()
                appendLine("### Commands")
                appendLine("- `/help` — show this help")
            }

            val helpMsg = ChatMessage(
                sessionId = sessionId,
                role = Role.ASSISTANT,
                content = helpText
            )
            chatRepository.insertMessage(helpMsg)
        }
    }

    fun toggleAgentMode() {
        _isAgentMode.value = !_isAgentMode.value
        Timber.i("Agent mode: ${_isAgentMode.value}")
    }

    fun sendMessage(content: String) {
        if (content.isBlank()) return
        if (_isGenerating.value) return

        if (content.trim().equals("/help", ignoreCase = true)) {
            handleHelpCommand()
            return
        }

        val sessionId = _currentSessionId.value
        if (sessionId == 0L) return

        viewModelScope.launch {
            val filePath = _attachedFilePath.value
            val finalContent = if (filePath != null) {
                val file = java.io.File(filePath)
                val ext = file.extension.ifEmpty { "txt" }
                val fileContent = try { file.readText().take(8000) } catch (e: Exception) { "" }
                clearAttachedFile()
                if (fileContent.isNotEmpty())
                    "```$ext\n// File: ${file.name}\n$fileContent\n```\n\n${content.trim()}"
                else content.trim()
            } else content.trim()

            val userMessage = ChatMessage(
                sessionId = sessionId,
                role = Role.USER,
                content = finalContent
            )
            chatRepository.insertMessage(userMessage)

            // Auto-title on first user message
            val session = chatRepository.getSession(sessionId)
            if (session != null && session.title == "New Chat" && session.messageCount <= 1) {
                val title = finalContent.take(40)
                chatRepository.updateSessionTitle(sessionId, title)
            }

            val currentState = llamaInference.state.value
            if (currentState !is InferenceState.Loaded && currentState !is InferenceState.Generating) {
                val noModelMsg = ChatMessage(
                    sessionId = sessionId,
                    role = Role.ASSISTANT,
                    content = "Model not loaded. Go to Settings and select a .gguf model file.\n\n" +
                            "Place your model in:\n`/Android/data/com.quantma.lite/files/models/`"
                )
                chatRepository.insertMessage(noModelMsg)
                return@launch
            }

            if (_isAgentMode.value) {
                // Try direct file op shortcut first — CodeLlama 7B doesn't reliably
                // generate ACTION: format, so we parse the request and execute directly
                val workingDir = settingsDataStore.agentWorkingDir.first()
                val directOp = tryDirectFileOp(finalContent, workingDir)
                if (directOp != null) {
                    Timber.i("Direct file op shortcut: $directOp")
                    executeDirectFileOp(directOp, sessionId)
                } else {
                    runAgentLoop(finalContent)
                }
            } else {
                // Intercept file/git requests in Chat mode — LLM ignores system prompt for these
                if (isFileOrGitRequest(finalContent)) {
                    Timber.i("File/git request intercepted in Chat mode: ${content.take(60)}")
                    chatRepository.insertMessage(
                        ChatMessage(
                            sessionId = sessionId,
                            role = Role.ASSISTANT,
                            content = "Switch to Agent Mode (robot icon in the top bar) for file and git operations."
                        )
                    )
                } else {
                    generateResponse()
                }
            }
        }
    }

    /** Returns true if the message is asking to create/write/delete files or run git/shell commands. */
    private fun isFileOrGitRequest(text: String): Boolean {
        val t = text.lowercase()
        return listOf(
            // Russian
            "создай файл", "создать файл", "создай папку", "создать папку",
            "удали файл", "удалить файл", "запиши в файл", "запись в файл",
            "переименуй", "перемести файл", "скопируй файл",
            "сделай коммит", "закоммить", "запушь", "сделай пуш",
            "клонируй", "создай ветку", "переключись на ветку",
            // English
            "create file", "make file", "write file", "write to file",
            "delete file", "remove file", "rename file", "move file",
            "create folder", "make folder", "mkdir",
            "git commit", "git push", "git pull", "git clone",
            "git checkout", "git branch", "git merge",
            "git diff", "git log", "git add", "git stash",
            "покажи изменения", "покажи историю", "покажи коммиты",
            "grep ", "search ", "найди ", "поиск ",
            "touch ", "echo >", "cat >"
        ).any { t.contains(it) }
    }

    /**
     * Parse user message for simple file operations (create/delete).
     * Returns a ToolCall if matched, null otherwise (falls back to LLM).
     */
    private fun tryDirectFileOp(text: String, workingDir: String): ToolCall? {
        val t = text.trim()

        // "создай файл X" / "create file X" / "touch X"
        val createPatterns = listOf(
            Regex("""(?:создай|создать|сделай)\s+файл\s+(\S+)""", RegexOption.IGNORE_CASE),
            Regex("""(?:create|make|touch)\s+(?:a\s+)?file\s+(\S+)""", RegexOption.IGNORE_CASE),
        )
        for (p in createPatterns) {
            val m = p.find(t)
            if (m != null) {
                val filename = m.groupValues[1]
                // Try to extract inline content from user message (e.g. "запиши в него точно это: <content>")
                val content = extractInlineContent(t)
                return ToolCall.WriteFile(resolvePath(filename, workingDir), content)
            }
        }

        // "удали файл X" / "delete file X"
        val deletePatterns = listOf(
            Regex("""(?:удали|удалить|убери)\s+файл\s+(\S+)""", RegexOption.IGNORE_CASE),
            Regex("""(?:delete|remove|rm)\s+(?:the\s+)?file\s+(\S+)""", RegexOption.IGNORE_CASE),
        )
        for (p in deletePatterns) {
            val m = p.find(t)
            if (m != null) {
                val filename = m.groupValues[1]
                return ToolCall.DeleteFile(resolvePath(filename, workingDir))
            }
        }

        // "прочитай файл X" / "read file X" / "покажи содержимое X"
        val readPatterns = listOf(
            Regex("""(?:прочитай|прочитать|открой|покажи\s+содержимое)\s+файл[а]?\s+(\S+)""", RegexOption.IGNORE_CASE),
            Regex("""(?:read|open|show\s+contents?\s+of)\s+(?:the\s+)?file\s+(\S+)""", RegexOption.IGNORE_CASE),
            Regex("""(?:read|cat)\s+(\S+\.\w+)""", RegexOption.IGNORE_CASE),
        )
        for (p in readPatterns) {
            val m = p.find(t)
            if (m != null) {
                val filename = m.groupValues[1]
                return ToolCall.ReadFile(resolvePath(filename, workingDir))
            }
        }

        // "список файлов" / "list files" / "покажи файлы"
        val listPatterns = listOf(
            Regex("""(?:список|покажи|перечисли)\s+файлов?""", RegexOption.IGNORE_CASE),
            Regex("""(?:list|show|ls)\s+(?:all\s+)?files""", RegexOption.IGNORE_CASE),
            Regex("""^(?:ls|dir)$""", RegexOption.IGNORE_CASE),
        )
        for (p in listPatterns) {
            if (p.containsMatchIn(t)) return ToolCall.ListFiles(workingDir)
        }

        // "git статус" / "git status" / "покажи git"
        val gitStatusPatterns = listOf(
            Regex("""(?:git\s+стат[уy]с|покажи\s+git|git\s+status)""", RegexOption.IGNORE_CASE),
        )
        for (p in gitStatusPatterns) {
            if (p.containsMatchIn(t)) return ToolCall.GitStatus(workingDir)
        }

        // "git diff" / "покажи изменения" / "show changes"
        val gitDiffPatterns = listOf(
            Regex("""git\s+diff""", RegexOption.IGNORE_CASE),
            Regex("""покажи\s+изменения""", RegexOption.IGNORE_CASE),
            Regex("""show\s+changes""", RegexOption.IGNORE_CASE),
        )
        for (p in gitDiffPatterns) {
            if (p.containsMatchIn(t)) return ToolCall.GitDiff(workingDir)
        }

        // "git commit -m 'msg'" / "закоммить 'msg'" / "сделай коммит 'msg'"
        val gitCommitPatterns = listOf(
            Regex("""git\s+commit\s+(?:-m\s+)?['""](.+?)['""]""", RegexOption.IGNORE_CASE),
            Regex("""(?:закоммить|сделай\s+коммит)\s+['""](.+?)['""]""", RegexOption.IGNORE_CASE),
            Regex("""(?:закоммить|сделай\s+коммит)\s+(.+)""", RegexOption.IGNORE_CASE),
            Regex("""git\s+commit\s+(?:-m\s+)?(.+)""", RegexOption.IGNORE_CASE),
        )
        for (p in gitCommitPatterns) {
            val m = p.find(t)
            if (m != null) {
                val msg = m.groupValues[1].trim().removeSurrounding("'").removeSurrounding("\"")
                return ToolCall.GitCommit(workingDir, msg)
            }
        }

        // "git push" / "запушь" / "отправь на сервер"
        val gitPushPatterns = listOf(
            Regex("""git\s+push""", RegexOption.IGNORE_CASE),
            Regex("""запушь""", RegexOption.IGNORE_CASE),
            Regex("""сделай\s+пуш""", RegexOption.IGNORE_CASE),
            Regex("""отправь\s+на\s+сервер""", RegexOption.IGNORE_CASE),
        )
        for (p in gitPushPatterns) {
            if (p.containsMatchIn(t)) return ToolCall.GitPush(workingDir)
        }

        // "git log" / "покажи историю" / "покажи коммиты" / "show commits"
        val gitLogPatterns = listOf(
            Regex("""git\s+log""", RegexOption.IGNORE_CASE),
            Regex("""покажи\s+(?:историю|коммиты|логи?)""", RegexOption.IGNORE_CASE),
            Regex("""show\s+(?:commits?|history|log)""", RegexOption.IGNORE_CASE),
        )
        for (p in gitLogPatterns) {
            if (p.containsMatchIn(t)) return ToolCall.GitBranch(workingDir) // reuse GitBranch for log display
        }

        // "git add" / "добавь в git" / "stage all"
        val gitAddPatterns = listOf(
            Regex("""git\s+add\s*\.?""", RegexOption.IGNORE_CASE),
            Regex("""(?:добавь|добавить)\s+(?:все\s+)?в\s+git""", RegexOption.IGNORE_CASE),
            Regex("""stage\s+all""", RegexOption.IGNORE_CASE),
        )
        for (p in gitAddPatterns) {
            if (p.containsMatchIn(t)) return ToolCall.RunCommand("git_add_all")
        }

        // "grep X" / "найди X в файлах" / "search X"
        val searchPatterns = listOf(
            Regex("""grep\s+(.+)""", RegexOption.IGNORE_CASE),
            Regex("""(?:найди|поиск|search)\s+(.+?)(?:\s+в\s+файлах)?$""", RegexOption.IGNORE_CASE),
        )
        for (p in searchPatterns) {
            val m = p.find(t)
            if (m != null) {
                val query = m.groupValues[1].trim()
                return ToolCall.SearchFiles(query, workingDir)
            }
        }

        // "создай папку X" / "create folder X"
        val mkdirPatterns = listOf(
            Regex("""(?:создай|создать)\s+(?:папку|директорию|каталог)\s+(\S+)""", RegexOption.IGNORE_CASE),
            Regex("""(?:create|make)\s+(?:a\s+)?(?:folder|directory|dir)\s+(\S+)""", RegexOption.IGNORE_CASE),
            Regex("""mkdir\s+(\S+)""", RegexOption.IGNORE_CASE),
        )
        for (p in mkdirPatterns) {
            val m = p.find(t)
            if (m != null) {
                val dirname = m.groupValues[1]
                return ToolCall.RunCommand("mkdir -p ${resolvePath(dirname, workingDir)}")
            }
        }

        // "find_todo" / "grep todo" / "найди todo" — search for TODO/FIXME in project
        val findTodoPatterns = listOf(
            Regex("""^(?:find[_\s]?todo|grep\s+todo|найди\s+todo|тодо)$""", RegexOption.IGNORE_CASE),
        )
        for (p in findTodoPatterns) {
            if (p.containsMatchIn(t)) return ToolCall.SearchFiles("TODO|FIXME|HACK|XXX", workingDir)
        }

        // "count_lines" / "подсчитай строки" / "wc"
        val countLinesPatterns = listOf(
            Regex("""^(?:count[_\s]?lines?|подсчитай\s+строки|wc\s*-?l?)$""", RegexOption.IGNORE_CASE),
        )
        for (p in countLinesPatterns) {
            if (p.containsMatchIn(t)) return ToolCall.RunCommand(
                "find '$workingDir' \\( -name '*.kt' -o -name '*.java' -o -name '*.py' -o -name '*.js' -o -name '*.ts' \\) | xargs wc -l 2>/dev/null | tail -1"
            )
        }

        // "git stash [pop|list|save 'msg']"
        val gitStashListRegex = Regex("""git\s+stash\s+list""", RegexOption.IGNORE_CASE)
        val gitStashPopRegex  = Regex("""git\s+stash\s+pop""", RegexOption.IGNORE_CASE)
        val gitStashSaveRegex = Regex("""git\s+stash(?:\s+(?:save|push))?(?:\s+(.*))?$""", RegexOption.IGNORE_CASE)
        when {
            gitStashListRegex.containsMatchIn(t) ->
                return ToolCall.RunCommand("git -C '$workingDir' stash list")
            gitStashPopRegex.containsMatchIn(t) ->
                return ToolCall.RunCommand("git -C '$workingDir' stash pop")
            gitStashSaveRegex.containsMatchIn(t) -> {
                val m = gitStashSaveRegex.find(t)
                val msg = m?.groupValues?.getOrNull(1)?.trim()?.removeSurrounding("\"")?.removeSurrounding("'") ?: ""
                val cmd = if (msg.isNotEmpty()) "git -C '$workingDir' stash push -m '$msg'"
                          else "git -C '$workingDir' stash push"
                return ToolCall.RunCommand(cmd)
            }
        }

        return null
    }

    private fun resolvePath(filename: String, workingDir: String): String {
        return if (filename.startsWith("/")) filename else "$workingDir/$filename"
    }

    /**
     * Extract file content from a user message.
     * Handles: "запиши в него точно это: <content>", "содержимым: <content>",
     *          "following:\n<content>", "exactly this:\n<content>", backtick blocks
     */
    private fun extractInlineContent(text: String): String {
        // Backtick code block: ```[lang]\n<content>\n```
        val codeBlock = Regex("```(?:\\w+)?\\n([\\s\\S]*?)```").find(text)
        if (codeBlock != null) {
            val c = codeBlock.groupValues[1].trimEnd('\n')
            if (c.isNotBlank()) return c
        }
        // Inline backtick: `<content>`
        val inlineCode = Regex("`([^`\n]+)`").find(text)
        if (inlineCode != null) {
            val c = inlineCode.groupValues[1].trim()
            if (c.isNotBlank()) return c
        }
        // Keyword patterns: "это:", "содержимым:", "following:", "content:", "exactly this:"
        val keywordMatch = Regex(
            """(?:это:|содержимым:|following:|content:|exactly\s+this:|текстом:|текст:)\s*\n?(.+)""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(text)
        if (keywordMatch != null) {
            val c = keywordMatch.groupValues[1].trim()
            if (c.isNotBlank()) return c
        }
        return ""
    }

    /** Execute a direct file operation with user approval, bypassing the LLM. */
    private fun executeDirectFileOp(toolCall: ToolCall, sessionId: Long) {
        generationJob = viewModelScope.launch {
            _isGenerating.value = true
            try {
                when (toolCall) {
                    is ToolCall.WriteFile -> {
                        val oldContent = withContext(Dispatchers.IO) {
                            fileRepository.readFile(toolCall.path)
                        }
                        _pendingWrite.value = PendingWrite(
                            path = toolCall.path,
                            newContent = toolCall.content,
                            oldContent = oldContent,
                            roundIndex = 0
                        )
                        writeApprovalDeferred = CompletableDeferred()
                        val approved = writeApprovalDeferred!!.await()
                        _pendingWrite.value = null

                        val resultMsg = if (approved) {
                            withContext(Dispatchers.IO) {
                                fileRepository.writeFile(toolCall.path, toolCall.content)
                            }
                            "File created: ${toolCall.path}"
                        } else {
                            "File creation cancelled by user."
                        }
                        chatRepository.insertMessage(
                            ChatMessage(sessionId = sessionId, role = Role.ASSISTANT, content = resultMsg)
                        )
                    }
                    is ToolCall.DeleteFile -> {
                        val preview = withContext(Dispatchers.IO) {
                            fileRepository.readFile(toolCall.path)
                                .lines().take(10).joinToString("\n")
                        }
                        _pendingDelete.value = PendingDelete(
                            path = toolCall.path, preview = preview, roundIndex = 0
                        )
                        deleteApprovalDeferred = CompletableDeferred()
                        val approved = deleteApprovalDeferred!!.await()
                        _pendingDelete.value = null

                        val resultMsg = if (approved) {
                            val ok = withContext(Dispatchers.IO) {
                                fileRepository.deleteFile(toolCall.path)
                            }
                            if (ok) "File deleted: ${toolCall.path}" else "Error deleting file."
                        } else {
                            "Deletion cancelled by user."
                        }
                        chatRepository.insertMessage(
                            ChatMessage(sessionId = sessionId, role = Role.ASSISTANT, content = resultMsg)
                        )
                    }
                    is ToolCall.ReadFile -> {
                        val content = withContext(Dispatchers.IO) {
                            fileRepository.readFile(toolCall.path)
                        }
                        val msg = if (content.isNotEmpty())
                            "**${toolCall.path.substringAfterLast('/')}**:\n```\n$content\n```"
                        else
                            "File is empty: ${toolCall.path}"
                        chatRepository.insertMessage(
                            ChatMessage(sessionId = sessionId, role = Role.ASSISTANT, content = msg)
                        )
                    }
                    is ToolCall.ListFiles -> {
                        val workingDir = settingsDataStore.agentWorkingDir.first()
                        val result = withContext(Dispatchers.IO) {
                            toolExecutor.execute(toolCall, workingDir)
                        }
                        chatRepository.insertMessage(
                            ChatMessage(sessionId = sessionId, role = Role.ASSISTANT, content = result.output)
                        )
                    }
                    is ToolCall.GitStatus -> {
                        val workingDir = settingsDataStore.agentWorkingDir.first()
                        val result = withContext(Dispatchers.IO) {
                            toolExecutor.execute(toolCall, workingDir)
                        }
                        chatRepository.insertMessage(
                            ChatMessage(sessionId = sessionId, role = Role.ASSISTANT, content = result.output)
                        )
                    }
                    is ToolCall.GitDiff -> {
                        val workingDir = settingsDataStore.agentWorkingDir.first()
                        val result = withContext(Dispatchers.IO) {
                            toolExecutor.execute(toolCall, workingDir)
                        }
                        val msg = if (result.output.isBlank()) "No changes detected."
                        else "```diff\n${result.output}\n```"
                        chatRepository.insertMessage(
                            ChatMessage(sessionId = sessionId, role = Role.ASSISTANT, content = msg)
                        )
                    }
                    is ToolCall.GitCommit -> {
                        val workingDir = settingsDataStore.agentWorkingDir.first()
                        // Show approval card
                        _pendingGitAction.value = PendingGitAction(
                            action = "commit",
                            description = "Git Commit",
                            preview = toolCall.message,
                            toolCall = toolCall
                        )
                        gitActionDeferred = CompletableDeferred()
                        val approved = gitActionDeferred!!.await()
                        _pendingGitAction.value = null

                        val resultMsg = if (approved) {
                            // Stage all changes first, then commit
                            withContext(Dispatchers.IO) {
                                gitOperationsAdapter.addAll(workingDir)
                                val commitResult = gitOperationsAdapter.commit(workingDir, toolCall.message)
                                commitResult
                            }
                        } else {
                            "Commit cancelled by user."
                        }
                        chatRepository.insertMessage(
                            ChatMessage(sessionId = sessionId, role = Role.ASSISTANT, content = resultMsg)
                        )
                    }
                    is ToolCall.GitPush -> {
                        val workingDir = settingsDataStore.agentWorkingDir.first()
                        // Show approval card
                        _pendingGitAction.value = PendingGitAction(
                            action = "push",
                            description = "Git Push",
                            preview = "Push local commits to remote origin",
                            toolCall = toolCall
                        )
                        gitActionDeferred = CompletableDeferred()
                        val approved = gitActionDeferred!!.await()
                        _pendingGitAction.value = null

                        val resultMsg = if (approved) {
                            withContext(Dispatchers.IO) {
                                gitOperationsAdapter.push(workingDir, "origin", null)
                            }
                        } else {
                            "Push cancelled by user."
                        }
                        chatRepository.insertMessage(
                            ChatMessage(sessionId = sessionId, role = Role.ASSISTANT, content = resultMsg)
                        )
                    }
                    is ToolCall.GitBranch -> {
                        // Used for git log display
                        val workingDir = settingsDataStore.agentWorkingDir.first()
                        val result = withContext(Dispatchers.IO) {
                            gitOperationsAdapter.getLog(workingDir, 20)
                        }
                        val msg = "```\n$result\n```"
                        chatRepository.insertMessage(
                            ChatMessage(sessionId = sessionId, role = Role.ASSISTANT, content = msg)
                        )
                    }
                    is ToolCall.SearchFiles -> {
                        val workingDir = settingsDataStore.agentWorkingDir.first()
                        val result = withContext(Dispatchers.IO) {
                            toolExecutor.execute(toolCall, workingDir)
                        }
                        val msg = if (result.output.isBlank()) "No matches found for: ${toolCall.pattern}"
                        else "```\n${result.output}\n```"
                        chatRepository.insertMessage(
                            ChatMessage(sessionId = sessionId, role = Role.ASSISTANT, content = msg)
                        )
                    }
                    is ToolCall.RunCommand -> {
                        val workingDir = settingsDataStore.agentWorkingDir.first()
                        if (toolCall.command == "git_add_all") {
                            val result = withContext(Dispatchers.IO) {
                                gitOperationsAdapter.addAll(workingDir)
                            }
                            chatRepository.insertMessage(
                                ChatMessage(sessionId = sessionId, role = Role.ASSISTANT, content = result)
                            )
                        } else {
                            val result = withContext(Dispatchers.IO) {
                                toolExecutor.execute(toolCall, workingDir)
                            }
                            chatRepository.insertMessage(
                                ChatMessage(
                                    sessionId = sessionId, role = Role.ASSISTANT,
                                    content = if (result.success) "Done: ${result.output}" else "Error: ${result.output}"
                                )
                            )
                        }
                    }
                    is ToolCall.GitCreateBranch -> {
                        val workingDir = settingsDataStore.agentWorkingDir.first()
                        val result = withContext(Dispatchers.IO) {
                            toolExecutor.execute(toolCall, workingDir)
                        }
                        chatRepository.insertMessage(
                            ChatMessage(sessionId = sessionId, role = Role.ASSISTANT,
                                content = if (result.success) result.output else "Error: ${result.output}")
                        )
                    }
                    is ToolCall.GitCheckout -> {
                        val workingDir = settingsDataStore.agentWorkingDir.first()
                        val result = withContext(Dispatchers.IO) {
                            toolExecutor.execute(toolCall, workingDir)
                        }
                        chatRepository.insertMessage(
                            ChatMessage(sessionId = sessionId, role = Role.ASSISTANT,
                                content = if (result.success) result.output else "Error: ${result.output}")
                        )
                    }
                    is ToolCall.GitDeleteBranch -> {
                        val workingDir = settingsDataStore.agentWorkingDir.first()
                        _pendingGitAction.value = PendingGitAction(
                            action = "delete_branch",
                            description = "Delete Branch",
                            preview = "Delete branch '${toolCall.name}'",
                            toolCall = toolCall
                        )
                        gitActionDeferred = CompletableDeferred()
                        val approved = gitActionDeferred!!.await()
                        _pendingGitAction.value = null
                        val resultMsg = if (approved) {
                            withContext(Dispatchers.IO) {
                                toolExecutor.executeGitDeleteBranchApproved(workingDir, toolCall.name).output
                            }
                        } else "Branch deletion cancelled."
                        chatRepository.insertMessage(
                            ChatMessage(sessionId = sessionId, role = Role.ASSISTANT, content = resultMsg)
                        )
                    }
                    is ToolCall.GitMerge -> {
                        val workingDir = settingsDataStore.agentWorkingDir.first()
                        _pendingGitAction.value = PendingGitAction(
                            action = "merge",
                            description = "Git Merge",
                            preview = "Merge branch '${toolCall.branchName}' into current",
                            toolCall = toolCall
                        )
                        gitActionDeferred = CompletableDeferred()
                        val approved = gitActionDeferred!!.await()
                        _pendingGitAction.value = null
                        val resultMsg = if (approved) {
                            withContext(Dispatchers.IO) {
                                toolExecutor.executeGitMergeApproved(workingDir, toolCall.branchName).output
                            }
                        } else "Merge cancelled."
                        chatRepository.insertMessage(
                            ChatMessage(sessionId = sessionId, role = Role.ASSISTANT, content = resultMsg)
                        )
                    }
                    is ToolCall.GitStashSave, is ToolCall.GitStashPop, is ToolCall.GitStashList -> {
                        val workingDir = settingsDataStore.agentWorkingDir.first()
                        val result = withContext(Dispatchers.IO) {
                            toolExecutor.execute(toolCall, workingDir)
                        }
                        chatRepository.insertMessage(
                            ChatMessage(sessionId = sessionId, role = Role.ASSISTANT,
                                content = if (result.success) result.output else "Error: ${result.output}")
                        )
                    }
                    else -> {
                        // Fallback to agent loop for unsupported direct ops
                        runAgentLoop(toolCall.toString())
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Direct file op error")
                chatRepository.insertMessage(
                    ChatMessage(
                        sessionId = sessionId, role = Role.ASSISTANT,
                        content = "Error: ${e.message}"
                    )
                )
            } finally {
                _isGenerating.value = false
            }
        }
    }

    private fun startInferenceService() {
        try {
            val modelPath = kotlinx.coroutines.runBlocking {
                settingsDataStore.modelPath.first()
            }
            val modelName = modelPath.substringAfterLast('/')
                .removeSuffix(".gguf").take(24)
            appContext.startService(InferenceService.startIntent(appContext, modelName))
        } catch (e: Exception) {
            Timber.w(e, "Could not start InferenceService")
        }
    }

    private fun stopInferenceService() {
        try { appContext.startService(InferenceService.stopIntent(appContext)) }
        catch (e: Exception) { Timber.w(e, "Could not stop InferenceService") }
    }

    private fun generateResponse() {
        val sessionId = _currentSessionId.value
        generationJob = viewModelScope.launch {
            _isGenerating.value = true
            _detectedQuestions.value = emptyList()
            startInferenceService()
            _streamingContent.value = ""
            _tokensPerSecond.value = 0f

            try {
                // Auto-compress old messages if needed
                val compressionEnabled = settingsDataStore.compressionEnabled.first()
                val contextSize = settingsDataStore.contextSize.first()
                var allMessages = _messages.value

                if (compressionEnabled && allMessages.size > DialogCompressor.PRESERVE_RECENT_COUNT) {
                    val actions = DialogCompressor.planCompression(allMessages, contextSize)
                    if (actions.isNotEmpty()) {
                        Timber.i("Compressing ${actions.size} messages")
                        for (action in actions) {
                            chatRepository.compressMessage(
                                action.messageId,
                                action.compressedContent,
                                action.originalTokenCount
                            )
                        }
                        allMessages = chatRepository.getMessagesForSessionOnce(sessionId)
                    }
                }

                // Trim history — 12 turns for modern models, more context = better chat
                val trimmedMessages = PromptFormatter.trimHistory(allMessages, maxTurns = 12)

                // Detect prompt format from model filename
                val modelPath = settingsDataStore.modelPath.first()
                val modelFilename = modelPath.substringAfterLast('/')
                val format = PromptFormatter.detectFormat(modelFilename)

                // Use active AgentConfig system prompt if available (Phase 2 v1.1.0),
                // otherwise use the chat-optimized system prompt
                val agentConfig = activeAgentConfig.value
                val systemPrompt = if (agentConfig != null) {
                    buildSystemPrompt(agentConfig)
                } else {
                    PromptFormatter.CHAT_SYSTEM_PROMPT
                }
                val prompt = PromptFormatter.formatPrompt(trimmedMessages, systemPrompt, format)

                // Read sampling params — resolve auto/manual
                val samplingParams = resolveCurrentSamplingParams()

                val placeholderId = chatRepository.insertMessage(
                    ChatMessage(
                        sessionId = sessionId,
                        role = Role.ASSISTANT,
                        content = ""
                    )
                )

                val fullResponse = StringBuilder()
                var tokenCount = 0
                var firstTokenTime: Long? = null  // start timing from first generated token (excludes prompt processing)

                llamaInference.generateCompletion(
                    prompt = prompt,
                    temperature = samplingParams.temperature,
                    topP = samplingParams.topP,
                    repeatPenalty = samplingParams.repeatPenalty,
                    topK = samplingParams.topK,
                    minP = samplingParams.minP,
                    penaltyLastN = samplingParams.penaltyLastN
                ).collect { token ->
                        if (firstTokenTime == null) firstTokenTime = System.nanoTime()
                        fullResponse.append(token)
                        tokenCount++
                        _streamingContent.value = fullResponse.toString()

                        if (tokenCount % 5 == 0) {
                            val elapsed = (System.nanoTime() - firstTokenTime!!) / 1_000_000_000.0
                            if (elapsed > 0) {
                                _tokensPerSecond.value = (tokenCount / elapsed).toFloat()
                            }
                        }
                    }

                val genStartTime = firstTokenTime ?: System.nanoTime()
                val totalTime = (System.nanoTime() - genStartTime) / 1_000_000_000.0
                val finalTokS = if (totalTime > 0) tokenCount / totalTime else 0.0
                _lastGenTps.value = finalTokS.toFloat()
                _sessionTokensTotal.value += tokenCount
                Timber.i("Generation done: $tokenCount tokens in %.1fs (%.1f tok/s)".format(totalTime, finalTokS))

                val finalContent = fullResponse.toString().ifEmpty { "(empty response)" }
                chatRepository.updateMessageContent(placeholderId, finalContent)
                // Detect questions/options in the response
                _detectedQuestions.value = detectQuestionsInText(finalContent)

            } catch (e: kotlinx.coroutines.CancellationException) {
                val partial = _streamingContent.value
                if (partial.isNotEmpty()) {
                    val lastMsg = _messages.value.lastOrNull()
                    if (lastMsg != null && lastMsg.role == Role.ASSISTANT && lastMsg.content.isEmpty()) {
                        chatRepository.updateMessageContent(lastMsg.id, "$partial\n\n_(generation stopped)_")
                    }
                }
                Timber.i("Generation cancelled by user")
            } catch (e: Exception) {
                Timber.e(e, "Generation error")
                val msg = e.message ?: "Unknown error"
                val userMsg = when {
                    msg.contains("out of memory", ignoreCase = true) ->
                        "Out of memory during generation. Try reducing context size."
                    msg.contains("Model not loaded") ->
                        "Model was unloaded. Please reload in Settings."
                    else -> "Generation error: $msg"
                }
                _errorMessage.value = userMsg

                val lastMsg = _messages.value.lastOrNull()
                if (lastMsg != null && lastMsg.role == Role.ASSISTANT && lastMsg.content.isEmpty()) {
                    chatRepository.updateMessageContent(lastMsg.id, "Error: $msg")
                }
            } finally {
                _isGenerating.value = false
                _streamingContent.value = ""
                _tokensPerSecond.value = 0f
                stopInferenceService()
            }
        }
    }

    // ---- Agent Mode ----

    private fun runAgentLoop(userMessage: String) {
        val sessionId = _currentSessionId.value
        generationJob = viewModelScope.launch {
            _isGenerating.value = true
            _detectedQuestions.value = emptyList()
            startInferenceService()
            _streamingContent.value = ""
            _tokensPerSecond.value = 0f
            _agentSteps.value = emptyList()

            val workingDir = settingsDataStore.agentWorkingDir.first()
            val maxRounds = settingsDataStore.agentMaxRounds.first()
            val contextSize = settingsDataStore.contextSize.first()
            // Read sampling params once before the agent loop — resolve auto/manual
            val samplingParams = resolveCurrentSamplingParams()
            // Read active agent config system prompt (Phase 2 v1.1.0)
            val agentSystemPrompt = activeAgentConfig.value?.let { buildSystemPrompt(it) }

            // Detect prompt format from model filename
            val modelPath = settingsDataStore.modelPath.first()
            val modelFilename = modelPath.substringAfterLast('/')
            val format = PromptFormatter.detectFormat(modelFilename)

            // Phase 5 (v1.4.0): Build rules block + match skills
            val rulesBlock = rulesEngine.buildRulesBlock()
            val skillsBlock = skillRouter.matchSkills(userMessage)

            // Compose enhanced system prompt
            val enhancedSystemPrompt = buildString {
                append(agentSystemPrompt ?: "")
                if (rulesBlock.isNotEmpty()) {
                    append("\n")
                    append(rulesBlock)
                }
                if (skillsBlock.isNotEmpty()) {
                    append("\n\n")
                    append(skillsBlock)
                }
            }.ifEmpty { null }  // null = use built-in default in AgentPromptFormatter

            val completedRounds = mutableListOf<AgentStep>()
            val allResponseParts = StringBuilder()

            try {
                for (round in 0 until maxRounds) {
                    Timber.i("Agent round $round/$maxRounds")

                    val prompt = AgentPromptFormatter.formatPrompt(
                        userMessage = userMessage,
                        rounds = completedRounds,
                        workingDir = workingDir,
                        contextBudget = contextSize,
                        systemPrompt = enhancedSystemPrompt,   // includes rules + skills
                        format = format
                    )

                    val currentStep = AgentStep(
                        roundIndex = round,
                        response = "",
                        status = AgentStepStatus.GENERATING
                    )
                    _agentSteps.value = completedRounds + currentStep

                    val fullResponse = StringBuilder()
                    var tokenCount = 0
                    var firstTokenTime: Long? = null  // start timing from first generated token

                    llamaInference.generateCompletion(
                        prompt = prompt,
                        temperature = samplingParams.temperature,
                        topP = samplingParams.topP,
                        repeatPenalty = samplingParams.repeatPenalty,
                        topK = samplingParams.topK,
                        minP = samplingParams.minP,
                        penaltyLastN = samplingParams.penaltyLastN
                    ).collect { token ->
                            if (firstTokenTime == null) firstTokenTime = System.nanoTime()
                            fullResponse.append(token)
                            tokenCount++
                            _streamingContent.value = fullResponse.toString()

                            if (tokenCount % 5 == 0) {
                                val elapsed = (System.nanoTime() - firstTokenTime!!) / 1_000_000_000.0
                                if (elapsed > 0) {
                                    _tokensPerSecond.value = (tokenCount / elapsed).toFloat()
                                }
                            }
                        }

                    val genStartTime = firstTokenTime ?: System.nanoTime()
                    val roundTime = (System.nanoTime() - genStartTime) / 1_000_000_000.0
                    val roundTps = if (roundTime > 0) tokenCount / roundTime else 0.0
                    _lastGenTps.value = roundTps.toFloat()
                    _sessionTokensTotal.value += tokenCount
                    val responseText = fullResponse.toString()
                    Timber.i("Agent round $round response ($tokenCount tokens, %.1f tok/s): ${responseText.take(200)}".format(roundTps))

                    // SAS CommandParser: dual JSON+ACTION protocol, 22 tools
                    val toolCommand = commandParser.parse(responseText)
                    val toolCall = toolCommand?.toSasToolCall()
                    // Diagnostic: log raw response to both logcat and agent_debug.log
                    val dbgMsg = "ROUND=$round TOOL=${toolCommand?.tool} CONTENT_LEN=${toolCommand?.args?.get("content")?.toString()?.length ?: 0} RAW=${responseText.take(300)}"
                    Timber.i(dbgMsg)
                    withContext(Dispatchers.IO) {
                        try {
                            val debugFile = java.io.File("$workingDir/agent_debug.log")
                            val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                            debugFile.appendText("[$ts] $dbgMsg\n${"-".repeat(60)}\n")
                        } catch (e: Exception) {
                            Timber.w("agent_debug.log write failed: workingDir=$workingDir err=${e.message}")
                        }
                    }

                    if (toolCall == null || toolCall is ToolCall.Done) {
                        allResponseParts.appendLine(responseText)
                        // Do NOT add Done step to agentSteps — its text goes to DB message only.
                        // AgentStepCards are for tool-using rounds; final answer is the MessageBubble.
                        break
                    }

                    val executingStep = AgentStep(
                        roundIndex = round,
                        response = responseText,
                        toolCall = toolCall,
                        status = AgentStepStatus.EXECUTING
                    )
                    _agentSteps.value = completedRounds + executingStep

                    val toolResult: ToolResult

                    if (toolCall is ToolCall.WriteFile) {
                        val oldContent = withContext(Dispatchers.IO) {
                            fileRepository.readFile(toolCall.path)
                        }
                        val pending = PendingWrite(
                            path = toolCall.path,
                            newContent = toolCall.content,
                            oldContent = oldContent,
                            roundIndex = round
                        )

                        val waitingStep = executingStep.copy(status = AgentStepStatus.WAITING_USER)
                        _agentSteps.value = completedRounds + waitingStep

                        _pendingWrite.value = pending
                        writeApprovalDeferred = CompletableDeferred()

                        val approved = writeApprovalDeferred!!.await()
                        _pendingWrite.value = null

                        toolResult = if (approved) {
                            withContext(Dispatchers.IO) {
                                val resp = buildShellExecutor(workingDir).executeApproved(toolCommand!!)
                                when (resp) {
                                    is ToolResponse.Success -> {
                                        Timber.i("Agent: file written: ${toolCall.path}")
                                        ToolResult(toolCall, true, resp.output)
                                    }
                                    is ToolResponse.Error -> {
                                        Timber.w("Agent: write error: ${resp.message}")
                                        ToolResult(toolCall, false, resp.message)
                                    }
                                    else -> ToolResult(toolCall, false, "Unexpected response after write approval")
                                }
                            }
                        } else {
                            Timber.i("Agent: write rejected: ${toolCall.path}")
                            ToolResult(toolCall, false, "User rejected the file change.")
                        }
                    } else if (toolCall is ToolCall.DeleteFile) {
                        val resolvedPath = toolCall.path
                        val preview = withContext(Dispatchers.IO) {
                            fileRepository.readFile(resolvedPath)
                                .lines().take(10).joinToString("\n")
                        }
                        val pendingDel = PendingDelete(
                            path = resolvedPath,
                            preview = preview,
                            roundIndex = round
                        )

                        val waitingStep = executingStep.copy(status = AgentStepStatus.WAITING_USER)
                        _agentSteps.value = completedRounds + waitingStep

                        _pendingDelete.value = pendingDel
                        deleteApprovalDeferred = CompletableDeferred()

                        val approved = deleteApprovalDeferred!!.await()
                        _pendingDelete.value = null

                        toolResult = if (approved) {
                            withContext(Dispatchers.IO) {
                                val resp = buildShellExecutor(workingDir).executeApproved(toolCommand!!)
                                when (resp) {
                                    is ToolResponse.Success -> {
                                        Timber.i("Agent: file deleted: $resolvedPath")
                                        ToolResult(toolCall, true, resp.output)
                                    }
                                    is ToolResponse.Error -> {
                                        Timber.w("Agent: delete error: ${resp.message}")
                                        ToolResult(toolCall, false, resp.message)
                                    }
                                    else -> ToolResult(toolCall, false, "Unexpected response after delete approval")
                                }
                            }
                        } else {
                            Timber.i("Agent: delete rejected: $resolvedPath")
                            ToolResult(toolCall, false, "Deletion rejected by user.")
                        }
                    } else {
                        // Phase 5 (v1.4.0): Fire pre-execution hooks
                        val preTrigger = hooksEngine.getTriggerForToolCall(toolCall)
                        if (preTrigger != null) {
                            val hookCtx = hooksEngine.fireHooks(preTrigger)
                            if (hookCtx.isNotEmpty()) {
                                Timber.d("Pre-hook context: $hookCtx")
                            }
                        }

                        toolResult = withContext(Dispatchers.IO) {
                            val executor = buildShellExecutor(workingDir)
                            val resp = executor.execute(toolCommand!!)
                            when (resp) {
                                is ToolResponse.Success -> ToolResult(toolCall!!, true, resp.output)
                                is ToolResponse.Error   -> ToolResult(toolCall!!, false, "[${resp.code}] ${resp.message}")
                                is ToolResponse.NeedsApproval -> {
                                    // Auto-approve non-destructive git ops (no write/delete UI shown)
                                    val autoApprove = setOf(
                                        ToolType.GIT_ADD, ToolType.GIT_COMMIT, ToolType.GIT_COMMIT_AUTO
                                    )
                                    if (resp.command.tool in autoApprove) {
                                        val ar = executor.executeApproved(resp.command)
                                        when (ar) {
                                            is ToolResponse.Success -> ToolResult(toolCall!!, true, ar.output)
                                            is ToolResponse.Error   -> ToolResult(toolCall!!, false, ar.message)
                                            else -> ToolResult(toolCall!!, false, "Unexpected response")
                                        }
                                    } else {
                                        ToolResult(toolCall!!, false, "Approval required: ${resp.description}")
                                    }
                                }
                            }
                        }
                        Timber.i("Agent tool result: ${toolResult.output.take(200)}")

                        // Phase 5 (v1.4.0): Fire post-execution hooks
                        val postTrigger = hooksEngine.getPostTriggerForToolCall(toolCall)
                        if (postTrigger != null) {
                            val hookCtx = hooksEngine.fireHooks(postTrigger)
                            if (hookCtx.isNotEmpty()) {
                                Timber.d("Post-hook context: $hookCtx")
                            }
                        }
                    }

                    val completedStep = AgentStep(
                        roundIndex = round,
                        response = responseText,
                        toolCall = toolCall,
                        toolResult = toolResult,
                        status = AgentStepStatus.COMPLETE
                    )
                    completedRounds.add(completedStep)
                    _agentSteps.value = completedRounds.toList()
                    // Do NOT append intermediate responseText to allResponseParts —
                    // AgentStepCard already shows each round. Only the final Done
                    // response (appended above in the break branch) ends up in the DB.

                    _streamingContent.value = ""
                }

                val finalContent = allResponseParts.toString().trim().ifEmpty { "(empty agent response)" }
                chatRepository.insertMessage(
                    ChatMessage(
                        sessionId = sessionId,
                        role = Role.ASSISTANT,
                        content = finalContent
                    )
                )

            } catch (e: kotlinx.coroutines.CancellationException) {
                val partial = _streamingContent.value
                if (partial.isNotEmpty() || completedRounds.isNotEmpty()) {
                    val summary = buildAgentSummary(completedRounds, partial)
                    chatRepository.insertMessage(
                        ChatMessage(
                            sessionId = sessionId,
                            role = Role.ASSISTANT,
                            content = "$summary\n\n_(agent stopped)_"
                        )
                    )
                }
                Timber.i("Agent cancelled by user")
            } catch (e: Exception) {
                Timber.e(e, "Agent error")
                _errorMessage.value = "Agent error: ${e.message}"
                // Phase 5 (v1.4.0): Fire ON_ERROR hooks
                hooksEngine.fireHooks(HookTrigger.ON_ERROR)
                chatRepository.insertMessage(
                    ChatMessage(
                        sessionId = sessionId,
                        role = Role.ASSISTANT,
                        content = "Agent error: ${e.message}"
                    )
                )
            } finally {
                _isGenerating.value = false
                _streamingContent.value = ""
                _tokensPerSecond.value = 0f
                _pendingWrite.value = null
                writeApprovalDeferred = null
                _pendingDelete.value = null
                deleteApprovalDeferred = null
                _pendingGitAction.value = null
                gitActionDeferred = null
                stopInferenceService()
            }
        }
    }

    fun approveWrite() {
        writeApprovalDeferred?.complete(true)
    }

    fun rejectWrite() {
        writeApprovalDeferred?.complete(false)
    }

    fun approveDelete() {
        deleteApprovalDeferred?.complete(true)
    }

    fun rejectDelete() {
        deleteApprovalDeferred?.complete(false)
    }

    fun approveGitAction() {
        gitActionDeferred?.complete(true)
    }

    fun rejectGitAction() {
        gitActionDeferred?.complete(false)
    }

    fun deleteMessage(messageId: Long) {
        viewModelScope.launch {
            chatRepository.deleteMessage(messageId)
        }
    }

    fun deleteMessages(ids: Set<Long>) {
        viewModelScope.launch {
            chatRepository.deleteMessages(ids)
        }
    }

    private fun buildAgentSummary(rounds: List<AgentStep>, partial: String): String {
        val sb = StringBuilder()
        for (step in rounds) {
            sb.appendLine(step.response)
        }
        if (partial.isNotEmpty()) {
            sb.appendLine(partial)
        }
        return sb.toString().trim()
    }

    fun stopGeneration() {
        llamaInference.abort()
        generationJob?.cancel()
        generationJob = null
    }

    fun clearError() {
        _errorMessage.value = null
    }

    enum class ExportFormat { MARKDOWN, JSON }

    /**
     * Build export content for the current session.
     * Returns a Pair of (filename, content string).
     */
    fun buildExportContent(format: ExportFormat): Pair<String, String> {
        val sessionId = _currentSessionId.value
        val msgs = _messages.value
        val title = activeSessions.value
            .firstOrNull { it.id == sessionId }?.title
            ?.replace(Regex("[^A-Za-z0-9_\\-]"), "_")
            ?: "chat"
        val timestamp = System.currentTimeMillis()

        val content = when (format) {
            ExportFormat.MARKDOWN -> buildMarkdown(msgs, title)
            ExportFormat.JSON -> buildJson(msgs, title, sessionId)
        }
        val ext = if (format == ExportFormat.MARKDOWN) "md" else "json"
        return "${title}_$timestamp.$ext" to content
    }

    private fun buildMarkdown(msgs: List<ChatMessage>, title: String): String {
        val sb = StringBuilder()
        sb.appendLine("# $title\n")
        msgs.filter { !it.isCompressed }.forEach { msg ->
            val role = when (msg.role) {
                Role.USER -> "**You**"
                Role.ASSISTANT -> "**Assistant**"
                Role.SYSTEM -> "**System**"
            }
            sb.appendLine("### $role\n")
            sb.appendLine(msg.content)
            sb.appendLine()
        }
        return sb.toString()
    }

    private fun buildJson(msgs: List<ChatMessage>, title: String, sessionId: Long): String {
        val sb = StringBuilder()
        sb.appendLine("{")
        sb.appendLine("  \"session_id\": $sessionId,")
        sb.appendLine("  \"title\": ${jsonString(title)},")
        sb.appendLine("  \"exported_at\": ${System.currentTimeMillis()},")
        sb.appendLine("  \"messages\": [")
        msgs.filter { !it.isCompressed }.forEachIndexed { i, msg ->
            val comma = if (i < msgs.count { !it.isCompressed } - 1) "," else ""
            sb.appendLine("    {")
            sb.appendLine("      \"id\": ${msg.id},")
            sb.appendLine("      \"role\": ${jsonString(msg.role.name.lowercase())},")
            sb.appendLine("      \"content\": ${jsonString(msg.content)},")
            sb.appendLine("      \"timestamp\": ${msg.timestamp}")
            sb.appendLine("    }$comma")
        }
        sb.appendLine("  ]")
        sb.append("}")
        return sb.toString()
    }

    private fun jsonString(s: String) = "\"${s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")}\""

    /**
     * Delete the failed assistant message and re-run the last user message.
     * Does NOT re-insert the user message — it is already in the database.
     */
    fun retryLastUserMessage(errorMessageId: Long) {
        if (_isGenerating.value) return
        viewModelScope.launch {
            val msgs = _messages.value
            val errorIdx = msgs.indexOfFirst { it.id == errorMessageId }
            val lastUser = if (errorIdx > 0) msgs.take(errorIdx).lastOrNull { it.role == Role.USER }
                           else msgs.lastOrNull { it.role == Role.USER }
            if (lastUser == null) return@launch

            // Remove the failed assistant message
            chatRepository.deleteMessage(errorMessageId)

            // Re-run without inserting the user message again
            val content = lastUser.content
            val sessionId = _currentSessionId.value
            if (sessionId == 0L) return@launch

            if (_isAgentMode.value) {
                val workingDir = settingsDataStore.agentWorkingDir.first()
                val directOp = tryDirectFileOp(content.trim(), workingDir)
                if (directOp != null) {
                    executeDirectFileOp(directOp, sessionId)
                } else {
                    runAgentLoop(content.trim())
                }
            } else {
                generateResponse()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Don't unload model on config change — LlamaInference is @Singleton
    }
}

enum class ModelStatus {
    NOT_LOADED,
    LOADING,
    READY,
    GENERATING,
    ERROR
}

// ─── SAS ToolCommand → ToolCall mapper ──────────────────────────────────────

private fun com.quantma.lite.sas.ToolCommand.toSasToolCall(): ToolCall = when (tool) {
    ToolType.READ_FILE             -> ToolCall.ReadFile(stringArg("path") ?: "")
    ToolType.WRITE_FILE,
    ToolType.CREATE_FILE,
    ToolType.APPEND_FILE           -> ToolCall.WriteFile(stringArg("path") ?: "", stringArg("content") ?: "")
    ToolType.LIST_DIR              -> ToolCall.ListFiles(stringArg("path") ?: "")
    ToolType.GIT_STATUS,
    ToolType.GIT_STATUS_DETAILED   -> ToolCall.GitStatus(stringArg("path") ?: "")
    ToolType.GET_DIFF              -> ToolCall.GitDiff(stringArg("path") ?: "")
    ToolType.GIT_BRANCH            -> ToolCall.GitBranch(stringArg("path") ?: "")
    ToolType.GIT_COMMIT,
    ToolType.GIT_COMMIT_AUTO       -> ToolCall.GitCommit(stringArg("path") ?: "", stringArg("message") ?: "")
    ToolType.GIT_PUSH_SAFE         -> ToolCall.GitPush(stringArg("path") ?: "")
    ToolType.GIT_CREATE_BRANCH     -> ToolCall.GitCreateBranch(stringArg("path") ?: "", stringArg("name") ?: "")
    ToolType.GIT_CHECKOUT          -> ToolCall.GitCheckout(stringArg("path") ?: "", stringArg("name") ?: "")
    ToolType.GIT_DELETE_BRANCH     -> ToolCall.GitDeleteBranch(stringArg("path") ?: "", stringArg("name") ?: "")
    ToolType.GIT_MERGE             -> ToolCall.GitMerge(stringArg("path") ?: "", stringArg("branch") ?: "")
    ToolType.GIT_STASH             -> {
        val op = stringArg("operation")?.lowercase() ?: "save"
        when (op) {
            "pop" -> ToolCall.GitStashPop(stringArg("path") ?: "")
            "list" -> ToolCall.GitStashList(stringArg("path") ?: "")
            else -> ToolCall.GitStashSave(stringArg("path") ?: "", stringArg("message") ?: "")
        }
    }
    ToolType.DELETE_FILE           -> ToolCall.DeleteFile(stringArg("path") ?: "")
    ToolType.SEARCH_GREP           -> ToolCall.SearchFiles(stringArg("pattern") ?: "", stringArg("path") ?: "")
    ToolType.DONE                  -> ToolCall.Done
    else                           -> ToolCall.RunCommand(tool.toolName)
}
