@file:Suppress("unused")

package com.quantma.lite.sas

/**
 * ╔═══════════════════════════════════════════════════════════════════════════╗
 * ║           SECURE AGENT SHELL (SAS) — INTEGRATION GUIDE                  ║
 * ╚═══════════════════════════════════════════════════════════════════════════╝
 *
 * This file is a reference guide for integrating the SAS module into your
 * Android application. It contains compilable code examples that demonstrate
 * the key integration patterns.
 *
 * ## Module Overview
 *
 * ```
 * ┌──────────────┐     ┌──────────────┐     ┌──────────────┐
 * │   LLM Brain  │────►│  SasBridge   │────►│ShellExecutor │
 * │ (llama.cpp)  │◄────│  (StateFlow) │◄────│ (Sandboxed)  │
 * └──────────────┘     └──────┬───────┘     └──────┬───────┘
 *                             │                    │
 *                     ┌───────▼───────┐    ┌───────▼───────┐
 *                     │  Compose UI   │    │PathSanitizer  │
 *                     │ (Terminal)    │    │ DiffEngine    │
 *                     └───────────────┘    │ FileHistory   │
 *                                          └───────────────┘
 * ```
 *
 * ## Files
 *
 * | File                   | Purpose                                        |
 * |------------------------|------------------------------------------------|
 * | SasModels.kt           | Domain models: ToolType, ToolCommand, etc.     |
 * | PathSanitizer.kt       | Security: path validation, extension whitelist |
 * | DiffEngine.kt          | Myers diff: unified text + HTML output         |
 * | CommandParser.kt       | Dual protocol: JSON + ACTION text fallback     |
 * | ShellExecutor.kt       | Tool execution (22 tools) within sandbox       |
 * | FileHistoryManager.kt  | Undo/redo via file snapshots                   |
 * | SasBridge.kt           | Orchestrator: StateFlow, agent loop             |
 * | AgentSystemPrompt.kt   | LLM system prompt builder                      |
 * | CodeValidator.kt       | Pre-write validation + auto-fix                |
 * | HookManager.kt         | Pre/post-command hook system                   |
 * | CodeQualityTools.kt    | Lint, format, count lines, find TODOs          |
 * | ExtendedGitTools.kt    | Detailed status, auto-commit, safe push, stash |
 */
object IntegrationGuide {

    // ═══════════════════════════════════════════════════════════════════════
    // 1. BASIC SETUP — Create SAS components
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Example: Setting up SAS components manually (without DI).
     *
     * For Hilt DI, see [exampleHiltModule] below.
     */
    fun exampleBasicSetup() {
        // 1. Configure the sandbox
        val config = SasConfig(
            workingDir = "/storage/emulated/0/MyProject",
            maxPathDepth = 15,
            maxDisplayLines = 80
        )

        // 2. Create components
        val historyManager = FileHistoryManager(config.workingDir)
        val executor = ShellExecutor(
            config = config,
            gitOperations = null,         // Pass your JGit adapter here
            historyManager = historyManager
        )
        val parser = CommandParser()
        val bridge = SasBridge(
            executor = executor,
            parser = parser,
            config = config,
            maxRounds = 5
        )

        // 'bridge' is now ready to use in your ViewModel
        @Suppress("UNUSED_VARIABLE") val _bridge = bridge  // suppress unused warning in guide
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 2. HILT DEPENDENCY INJECTION
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Example: Hilt @Module for providing SAS dependencies.
     *
     * Add this to your `di/AppModule.kt`:
     *
     * ```kotlin
     * @Module
     * @InstallIn(SingletonComponent::class)
     * object SasModule {
     *
     *     @Provides
     *     @Singleton
     *     fun provideSasConfig(settingsDataStore: SettingsDataStore): SasConfig {
     *         // Read workingDir from settings synchronously (or use a default)
     *         return SasConfig(
     *             workingDir = settingsDataStore.agentWorkingDirBlocking
     *                 ?: "/storage/emulated/0"
     *         )
     *     }
     *
     *     @Provides
     *     @Singleton
     *     fun provideFileHistoryManager(config: SasConfig): FileHistoryManager {
     *         return FileHistoryManager(config.workingDir)
     *     }
     *
     *     @Provides
     *     @Singleton
     *     fun provideShellExecutor(
     *         config: SasConfig,
     *         gitRepository: GitRepository,
     *         historyManager: FileHistoryManager
     *     ): ShellExecutor {
     *         val gitOps = JGitAdapter(gitRepository)  // See section 4
     *         return ShellExecutor(config, gitOps, historyManager)
     *     }
     *
     *     @Provides
     *     @Singleton
     *     fun provideCommandParser(): CommandParser = CommandParser()
     *
     *     @Provides
     *     @Singleton
     *     fun provideSasBridge(
     *         executor: ShellExecutor,
     *         parser: CommandParser,
     *         config: SasConfig
     *     ): SasBridge = SasBridge(executor, parser, config)
     * }
     * ```
     */
    fun exampleHiltModule() { /* See KDoc above */ }

    // ═══════════════════════════════════════════════════════════════════════
    // 3. VIEWMODEL INTEGRATION — Agent Conversation Loop
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Example: Using SasBridge in a ViewModel.
     *
     * ```kotlin
     * @HiltViewModel
     * class ChatViewModel @Inject constructor(
     *     private val bridge: SasBridge,
     *     private val llamaInference: LlamaInference
     * ) : ViewModel() {
     *
     *     // Expose bridge state to Compose UI
     *     val bridgeState = bridge.state
     *
     *     // Agent rounds for terminal display
     *     val agentRounds = mutableStateListOf<AgentRound>()
     *
     *     fun onUserMessage(message: String) {
     *         viewModelScope.launch(Dispatchers.IO) {
     *             // Check if agent mode is active
     *             if (isAgentMode) {
     *                 runAgentMode(message)
     *             } else {
     *                 runChatMode(message)
     *             }
     *         }
     *     }
     *
     *     private suspend fun runAgentMode(message: String) {
     *         agentRounds.clear()
     *
     *         bridge.runAgentLoop(
     *             userMessage = message,
     *             generateFn = { prompt ->
     *                 // Collect the full LLM output
     *                 val sb = StringBuilder()
     *                 llamaInference.generateCompletion(prompt, maxTokens = 1024)
     *                     .collect { token -> sb.append(token) }
     *                 sb.toString()
     *             },
     *             onText = { text ->
     *                 // Show text in chat bubble
     *                 addAssistantMessage(text)
     *             },
     *             onRound = { round ->
     *                 // Update terminal view
     *                 agentRounds.add(round)
     *             }
     *         )
     *     }
     *
     *     // Handle approval from UI
     *     fun onApproveWrite() = bridge.approveCommand()
     *     fun onRejectWrite() = bridge.rejectCommand()
     *
     *     // Undo last file change
     *     fun onUndo(path: String) {
     *         viewModelScope.launch(Dispatchers.IO) {
     *             val result = bridge.undoLastChange(path)
     *             // Show result in UI
     *         }
     *     }
     * }
     * ```
     */
    fun exampleViewModelUsage() { /* See KDoc above */ }

    // ═══════════════════════════════════════════════════════════════════════
    // 4. GIT ADAPTER — Connecting JGit to GitOperations
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Example: Adapter from your existing GitRepository to SAS's GitOperations interface.
     *
     * ```kotlin
     * class JGitAdapter(
     *     private val gitRepository: GitRepository
     * ) : GitOperations {
     *
     *     override fun isGitRepo(path: String): Boolean =
     *         gitRepository.isGitRepo(path)
     *
     *     override fun getStatus(repoPath: String): String {
     *         val status = gitRepository.getStatus(repoPath)
     *         return buildString {
     *             appendLine("Branch: ${status.branch}")
     *             if (status.added.isNotEmpty())
     *                 appendLine("Added: ${status.added.joinToString(", ")}")
     *             if (status.modified.isNotEmpty())
     *                 appendLine("Modified: ${status.modified.joinToString(", ")}")
     *             if (status.removed.isNotEmpty())
     *                 appendLine("Removed: ${status.removed.joinToString(", ")}")
     *             if (status.untracked.isNotEmpty())
     *                 appendLine("Untracked: ${status.untracked.joinToString(", ")}")
     *         }
     *     }
     *
     *     override fun addFiles(repoPath: String, files: List<String>): String {
     *         gitRepository.addFiles(repoPath, files)
     *         return "Staged ${files.size} file(s)"
     *     }
     *
     *     override fun addAll(repoPath: String): String {
     *         gitRepository.addAll(repoPath)
     *         return "Staged all changes"
     *     }
     *
     *     override fun commit(repoPath: String, message: String): String {
     *         gitRepository.commit(repoPath, message, "Agent", "agent@local")
     *         return "Committed: $message"
     *     }
     *
     *     override fun getLog(repoPath: String, maxCount: Int): String {
     *         val entries = gitRepository.getLog(repoPath, maxCount)
     *         return entries.joinToString("\n") { entry ->
     *             "${entry.hash.take(7)} ${entry.message} (${entry.author})"
     *         }
     *     }
     *
     *     override fun getCurrentBranch(repoPath: String): String =
     *         gitRepository.getCurrentBranch(repoPath)
     * }
     * ```
     */
    fun exampleGitAdapter() { /* See KDoc above */ }

    // ═══════════════════════════════════════════════════════════════════════
    // 5. COMPOSE UI — Handling BridgeState
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Example: Compose UI that reacts to BridgeState.
     *
     * ```kotlin
     * @Composable
     * fun AgentStatusBar(viewModel: ChatViewModel) {
     *     val state by viewModel.bridgeState.collectAsState()
     *
     *     when (val s = state) {
     *         is BridgeState.Idle -> { /* Nothing to show */ }
     *
     *         is BridgeState.Processing -> {
     *             LinearProgressIndicator()
     *             Text(s.message, style = MaterialTheme.typography.bodySmall)
     *         }
     *
     *         is BridgeState.AgentRunning -> {
     *             LinearProgressIndicator(
     *                 progress = { s.currentRound.toFloat() / s.maxRounds }
     *             )
     *             Text("Round ${s.currentRound}/${s.maxRounds}")
     *         }
     *
     *         is BridgeState.WaitingApproval -> {
     *             Card(colors = CardDefaults.cardColors(
     *                 containerColor = MaterialTheme.colorScheme.secondaryContainer
     *             )) {
     *                 Column(modifier = Modifier.padding(16.dp)) {
     *                     Text("Pending Approval", fontWeight = FontWeight.Bold)
     *                     Text(s.description)
     *                     if (s.preview != null) {
     *                         // Show diff preview with DiffText composable
     *                         DiffText(s.preview)
     *                     }
     *                     Row {
     *                         Button(onClick = { viewModel.onApproveWrite() }) {
     *                             Text("Apply")
     *                         }
     *                         Spacer(Modifier.width(8.dp))
     *                         OutlinedButton(onClick = { viewModel.onRejectWrite() }) {
     *                             Text("Reject")
     *                         }
     *                     }
     *                 }
     *             }
     *         }
     *
     *         is BridgeState.Error -> {
     *             Card(colors = CardDefaults.cardColors(
     *                 containerColor = MaterialTheme.colorScheme.errorContainer
     *             )) {
     *                 Text("Error [${s.code}]: ${s.message}")
     *                 if (s.retryCommand != null) {
     *                     TextButton(onClick = { viewModel.onRetry() }) {
     *                         Text("Retry")
     *                     }
     *                 }
     *             }
     *         }
     *     }
     * }
     * ```
     */
    fun exampleComposeUI() { /* See KDoc above */ }

    // ═══════════════════════════════════════════════════════════════════════
    // 6. DIFF DISPLAY — Rendering diffs in Compose
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Example: Composable for rendering unified diff with colors.
     *
     * ```kotlin
     * @Composable
     * fun DiffText(diffOutput: String, modifier: Modifier = Modifier) {
     *     Column(modifier = modifier
     *         .background(Color(0xFF1e1e2e), RoundedCornerShape(8.dp))
     *         .padding(12.dp)
     *     ) {
     *         for (line in diffOutput.lines()) {
     *             val (bgColor, textColor) = when {
     *                 line.startsWith("+") -> Color(0x26a6e3a1) to Color(0xFFa6e3a1)
     *                 line.startsWith("-") -> Color(0x26f38ba8) to Color(0xFFf38ba8)
     *                 line.startsWith("@@") -> Color(0x1a89b4fa) to Color(0xFF89b4fa)
     *                 else -> Color.Transparent to Color(0xFF6c7086)
     *             }
     *             Text(
     *                 text = line,
     *                 color = textColor,
     *                 modifier = Modifier
     *                     .fillMaxWidth()
     *                     .background(bgColor)
     *                     .padding(horizontal = 8.dp, vertical = 1.dp),
     *                 fontFamily = FontFamily.Monospace,
     *                 fontSize = 12.sp
     *             )
     *         }
     *     }
     * }
     * ```
     *
     * Colors follow the Catppuccin Mocha palette:
     * - Green (#a6e3a1) for additions
     * - Red (#f38ba8) for deletions
     * - Blue (#89b4fa) for hunk headers
     * - Gray (#6c7086) for context lines
     */
    fun exampleDiffDisplay() { /* See KDoc above */ }

    // ═══════════════════════════════════════════════════════════════════════
    // 7. CONFIGURATION — SasConfig from Settings
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Example: Building SasConfig from your SettingsDataStore.
     *
     * ```kotlin
     * suspend fun buildSasConfigFromSettings(settings: SettingsDataStore): SasConfig {
     *     val workingDir = settings.agentWorkingDir.first()
     *         ?: "/storage/emulated/0"
     *     return SasConfig(
     *         workingDir = workingDir,
     *         maxPathDepth = 15,
     *         maxDisplayLines = 80,
     *         maxOutputChars = 3000,   // Smaller for constrained LLM context
     *         maxSearchResults = 30
     *     )
     * }
     * ```
     */
    fun exampleConfigFromSettings() { /* See KDoc above */ }

    // ═══════════════════════════════════════════════════════════════════════
    // 8. DEPENDENCIES — build.gradle.kts additions
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * The SAS module has **zero external dependencies** for its core.
     *
     * However, [SasBridge] uses `kotlinx.coroutines.flow` which is already
     * in your project via `coroutines-core` and `coroutines-android`.
     *
     * No changes to build.gradle.kts are needed if you already have:
     * ```kotlin
     * implementation(libs.coroutines.core)
     * implementation(libs.coroutines.android)
     * ```
     *
     * For HTML diff rendering in WebView (optional):
     * ```kotlin
     * implementation("androidx.webkit:webkit:1.12.1")
     * ```
     */
    fun exampleDependencies() { /* See KDoc above */ }

    // ═══════════════════════════════════════════════════════════════════════
    // 9. HOOKS — Pre/Post Command Automation (v3.1)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Example: Setting up hooks for automatic formatting and linting.
     *
     * ```kotlin
     * val hookManager = HookManager()
     *
     * // Auto-format after every file write
     * hookManager.addHook(HookConfig(
     *     hookType = HookType.POST_COMMAND,
     *     triggerTool = ToolType.WRITE_FILE,
     *     hookAction = ToolCommand(
     *         tool = ToolType.FORMAT_FILE,
     *         args = mapOf("path" to "\$TARGET_PATH")
     *     ),
     *     description = "Auto-format after write"
     * ))
     *
     * // Run lint before every git commit
     * hookManager.addHook(HookConfig(
     *     hookType = HookType.PRE_COMMAND,
     *     triggerTool = ToolType.GIT_COMMIT,
     *     hookAction = ToolCommand(
     *         tool = ToolType.LINT_FILE,
     *         args = mapOf("path" to ".")
     *     ),
     *     description = "Lint before commit"
     * ))
     *
     * // Pass to ShellExecutor
     * val executor = ShellExecutor(
     *     config = config,
     *     hookManager = hookManager
     * )
     * ```
     *
     * Placeholder `$TARGET_PATH` is resolved from the triggering command's path arg.
     * Hook failures are non-fatal — they log warnings but don't block the main command.
     */
    fun exampleHookSetup() { /* See KDoc above */ }

    // ═══════════════════════════════════════════════════════════════════════
    // 10. GUIDED MODE — Step-by-step execution for small LLMs (v3.1)
    // ═══════════════════════════════════════════════════════════════════════

    // Guided mode removed in QuantMA Lite

    // ═══════════════════════════════════════════════════════════════════════
    // 11. CODE QUALITY TOOLS — Lint, Format, Count, TODO (v3.1)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * New tools available in v3.1:
     *
     * ```kotlin
     * // Lint a file (pure regex — no external tools needed)
     * executor.execute(ToolCommand(tool = ToolType.LINT_FILE, args = mapOf("path" to "src")))
     * // Output: "src/main.kt: 3 issue(s)\n  12: [WARNING] line-length — Line exceeds 120 chars..."
     *
     * // Format a file (requires approval)
     * executor.execute(ToolCommand(tool = ToolType.FORMAT_FILE, args = mapOf("path" to "src/main.kt")))
     * // Returns NeedsApproval with diff preview
     *
     * // Count lines of code
     * executor.execute(ToolCommand(tool = ToolType.COUNT_LINES, args = mapOf("path" to "src")))
     * // Output: "Directory: src\nFiles: 12 | Total: 3456 lines (code: 2800, blank: 400, comment: 256)"
     *
     * // Find TODO comments
     * executor.execute(ToolCommand(tool = ToolType.FIND_TODO, args = mapOf("path" to "src")))
     * // Output: "Found 7 comment(s):\n── TODO (5) ──\n  src/main.kt:42: // TODO: implement..."
     * ```
     */
    fun exampleCodeQualityTools() { /* See KDoc above */ }

    // ═══════════════════════════════════════════════════════════════════════
    // 12. EXTENDED GIT — Auto-commit, Safe Push, Stash (v3.1)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Extended git tools (require GitOperations v3.1 methods):
     *
     * ```kotlin
     * // Detailed status with ahead/behind
     * executor.execute(ToolCommand(tool = ToolType.GIT_STATUS_DETAILED))
     * // Output: "Branch: main\nAhead: 2 | Behind: 0\n\nModified:..."
     *
     * // Auto-generate commit message (requires approval)
     * executor.execute(ToolCommand(tool = ToolType.GIT_COMMIT_AUTO))
     * // Returns NeedsApproval: "Git commit with message: Update main.kt, add utils.kt (+45 lines)"
     *
     * // Safe push (blocks if behind remote)
     * executor.execute(ToolCommand(tool = ToolType.GIT_PUSH_SAFE))
     * // Returns NeedsApproval: "Push 2 commit(s) to origin/main"
     * // Or error: "Local branch is 3 commit(s) behind origin/main. Pull first."
     *
     * // Stash operations
     * executor.execute(ToolCommand(tool = ToolType.GIT_STASH, args = mapOf("operation" to "save", "message" to "WIP")))
     * executor.execute(ToolCommand(tool = ToolType.GIT_STASH, args = mapOf("operation" to "pop")))
     * executor.execute(ToolCommand(tool = ToolType.GIT_STASH, args = mapOf("operation" to "list")))
     * ```
     */
    fun exampleExtendedGit() { /* See KDoc above */ }

    // ═══════════════════════════════════════════════════════════════════════
    // 13. DELETE FILE — Safe deletion with trash (v3.1)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Delete moves files to `.sas_trash/` instead of permanent deletion:
     *
     * ```kotlin
     * // Delete requires approval
     * val response = executor.execute(
     *     ToolCommand(tool = ToolType.DELETE_FILE, args = mapOf("path" to "old_file.kt"))
     * )
     * // Returns NeedsApproval: "Delete file: old_file.kt (2.3 KB) -> move to .sas_trash/"
     *
     * // After approval:
     * // File is moved to .sas_trash/old_file_1710428400000.kt
     * // Snapshot saved in FileHistoryManager for undo
     * ```
     */
    fun exampleDeleteFile() { /* See KDoc above */ }
}
