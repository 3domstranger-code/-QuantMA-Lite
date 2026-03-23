package com.quantma.lite.sas

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Secure Agent Shell — Bridge.
 *
 * The orchestration layer between UI/ViewModel and the [ShellExecutor].
 * Manages the full agent conversation loop, state transitions, and user approval flow.
 *
 * Key responsibilities:
 * - **Auto-detection**: Determines if LLM output is a JSON command, ACTION command, or plain text
 * - **Mixed mode**: Handles LLM output that contains both text and JSON (text → chat, JSON → execute)
 * - **Conversation loop**: Runs the full prompt → generate → parse → execute → feedback cycle
 * - **StateFlow**: Exposes [BridgeState] for reactive UI updates
 * - **Approval flow**: Manages CompletableDeferred for NeedsApproval commands
 *
 * Usage:
 * ```kotlin
 * val bridge = SasBridge(executor, parser, promptBuilder)
 * // Observe state
 * bridge.state.collect { state -> updateUI(state) }
 * // Run agent loop
 * bridge.runAgentLoop("Create a file") { prompt -> llm.generate(prompt) }
 * ```
 */
// NOTE: Core agent routing delegated to native AgentDecisionEngine via JNI.
// This class handles only the UI bridge layer. See native/agent_engine.cpp for
// the actual MCTS-based strategy selection and tool chain optimization.
class SasBridge(
    private val executor: ShellExecutor,
    private val parser: CommandParser,
    private val config: SasConfig,
    private val maxRounds: Int = DEFAULT_MAX_ROUNDS
) {
    private val _state = MutableStateFlow<BridgeState>(BridgeState.Idle)
    val state: StateFlow<BridgeState> = _state.asStateFlow()

    private val _rounds = mutableListOf<AgentRound>()
    val rounds: List<AgentRound> get() = _rounds.toList()

    private var pendingApproval: CompletableDeferred<Boolean>? = null

    /**
     * Process raw LLM output: detect if it's a command or plain text.
     *
     * @param llmOutput Raw text from the LLM
     * @return [ParsedLlmOutput] with separated text and command parts
     */
    fun processLlmOutput(llmOutput: String): ParsedLlmOutput {
        val trimmed = llmOutput.trim()

        // Try to parse as a command
        val command = parser.parse(trimmed)

        if (command != null) {
            // Check if there's text before/after the JSON
            val textParts = extractTextAroundCommand(trimmed)

            return ParsedLlmOutput(
                textParts = textParts,
                command = command,
                isDone = command.tool == ToolType.DONE
            )
        }

        // Check for DONE keyword in plain text
        if (trimmed.contains("DONE", ignoreCase = false) && trimmed.lines().any { it.trim() == "DONE" }) {
            val textWithoutDone = trimmed.lines()
                .filter { it.trim() != "DONE" }
                .joinToString("\n")
                .trim()

            return ParsedLlmOutput(
                textParts = if (textWithoutDone.isNotEmpty()) listOf(textWithoutDone) else emptyList(),
                command = ToolCommand(tool = ToolType.DONE),
                isDone = true
            )
        }

        // Pure text — no command detected
        return ParsedLlmOutput(
            textParts = listOf(trimmed),
            isDone = false
        )
    }

    /**
     * Execute a single parsed command through the executor.
     * Updates [state] during execution.
     *
     * @param command The [ToolCommand] to execute
     * @return [ToolResponse] from the executor
     */
    suspend fun executeCommand(command: ToolCommand): ToolResponse {
        _state.value = BridgeState.Processing(command)

        val response = executor.execute(command)

        when (response) {
            is ToolResponse.NeedsApproval -> {
                _state.value = BridgeState.WaitingApproval(
                    command = response.command,
                    description = response.description,
                    preview = response.preview
                )
                // Wait for user approval
                val approved = waitForApproval()
                return if (approved) {
                    _state.value = BridgeState.Processing(command, "Applying changes...")
                    val result = executor.executeApproved(response.command)
                    _state.value = BridgeState.Idle
                    result
                } else {
                    _state.value = BridgeState.Idle
                    ToolResponse.Error("REJECTED", "User rejected: ${response.description}")
                }
            }
            is ToolResponse.Error -> {
                _state.value = BridgeState.Error(
                    code = response.code,
                    message = response.message,
                    retryCommand = command
                )
                return response
            }
            is ToolResponse.Success -> {
                _state.value = BridgeState.Idle
                return response
            }
        }
    }

    /**
     * Run the full agent conversation loop.
     *
     * The loop:
     * 1. Build prompt with system instructions + user message + round history
     * 2. Call [generateFn] to get LLM output
     * 3. Parse output (detect command vs text)
     * 4. Execute command if found
     * 5. Record round
     * 6. Repeat until DONE or max rounds reached
     *
     * @param userMessage The user's original request
     * @param generateFn Suspend function that takes a prompt string and returns the LLM's full output.
     *                   For streaming LLMs, this should collect the full stream internally.
     * @param onText Callback for text portions of LLM output (for displaying in chat)
     * @param onRound Callback after each round completes (for updating UI)
     * @return List of all [AgentRound]s from this conversation
     */
    suspend fun runAgentLoop(
        userMessage: String,
        generateFn: suspend (prompt: String) -> String,
        onText: (String) -> Unit = {},
        onRound: (AgentRound) -> Unit = {}
    ): List<AgentRound> {
        _rounds.clear()

        for (roundNum in 1..maxRounds) {
            _state.value = BridgeState.AgentRunning(
                currentRound = roundNum,
                maxRounds = maxRounds,
                lastToolName = _rounds.lastOrNull()?.command?.tool?.toolName
            )

            // Build prompt with history
            val prompt = buildPromptWithHistory(userMessage)

            // Generate LLM output
            val llmOutput = try {
                generateFn(prompt)
            } catch (e: Exception) {
                val errorRound = AgentRound(
                    roundNumber = roundNum,
                    llmOutput = "",
                    response = ToolResponse.Error("GENERATION_ERROR", "LLM generation failed: ${e.message}")
                )
                _rounds.add(errorRound)
                onRound(errorRound)
                _state.value = BridgeState.Error("GENERATION_ERROR", e.message ?: "Generation failed")
                break
            }

            // Parse the output
            val parsed = processLlmOutput(llmOutput)

            // Show text to user if present
            if (parsed.hasText) {
                onText(parsed.displayText)
            }

            // Check for DONE
            if (parsed.isDone && !parsed.hasCommand) {
                val doneRound = AgentRound(
                    roundNumber = roundNum,
                    llmOutput = llmOutput,
                    thought = parsed.command?.thought
                )
                _rounds.add(doneRound)
                onRound(doneRound)
                break
            }

            // Execute command if found
            val command = parsed.command
            val response = if (command != null && command.tool != ToolType.DONE) {
                executeCommand(command)
            } else {
                // No command found — LLM is just talking, not using tools
                // This shouldn't continue the loop indefinitely
                null
            }

            // Record round
            val round = AgentRound(
                roundNumber = roundNum,
                llmOutput = llmOutput,
                thought = command?.thought,
                command = command,
                response = response
            )
            _rounds.add(round)
            onRound(round)

            // If DONE command was part of the output, stop
            if (parsed.isDone) break

            // If no command was found (pure text), stop the loop
            if (command == null) break

            // If execution failed, stop the loop
            if (response is ToolResponse.Error) break
        }

        _state.value = BridgeState.Idle
        return _rounds.toList()
    }

    /**
     * Approve the pending command (called from UI).
     */
    fun approveCommand() {
        pendingApproval?.complete(true)
        pendingApproval = null
    }

    /**
     * Reject the pending command (called from UI).
     */
    fun rejectCommand() {
        pendingApproval?.complete(false)
        pendingApproval = null
    }

    /**
     * Retry the last failed command.
     */
    suspend fun retryLastFailed(): ToolResponse? {
        val lastRound = _rounds.lastOrNull() ?: return null
        val command = lastRound.command ?: return null
        if (lastRound.response !is ToolResponse.Error) return null
        return executeCommand(command)
    }

    /**
     * Undo the last file modification.
     *
     * @param relativePath Path of the file to undo
     */
    fun undoLastChange(relativePath: String): ToolResponse {
        return executor.undoFileChange(relativePath)
    }

    /**
     * Redo a previously undone change.
     */
    fun redoLastChange(relativePath: String): ToolResponse {
        return executor.redoFileChange(relativePath)
    }

    /**
     * Reset the bridge state. Clears rounds and pending approvals.
     */
    fun reset() {
        _rounds.clear()
        pendingApproval?.complete(false)
        pendingApproval = null
        _state.value = BridgeState.Idle
    }

    // ─── Private Helpers ────────────────────────────────────────────────────

    /**
     * Wait for user approval via CompletableDeferred.
     */
    private suspend fun waitForApproval(): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        pendingApproval = deferred
        return deferred.await()
    }

    /**
     * Build prompt with system instructions and round history.
     */
    private fun buildPromptWithHistory(userMessage: String): String {
        val systemPrompt = AgentSystemPrompt.buildSystemPrompt(config.workingDir)

        val sb = StringBuilder()
        sb.appendLine(systemPrompt)
        sb.appendLine()

        sb.appendLine("User request: $userMessage")

        // Append round history (last N rounds to fit budget)
        val historyRounds = if (_rounds.size > 6) _rounds.takeLast(6) else _rounds
        for (round in historyRounds) {
            sb.appendLine()
            sb.appendLine("Assistant: ${round.llmOutput}")
            if (round.response != null) {
                val toolName = round.command?.tool?.toolName ?: "unknown"
                sb.appendLine(AgentSystemPrompt.buildToolResultPrompt(toolName, round.response))
            }
        }

        sb.appendLine()
        sb.appendLine("Assistant:")

        return sb.toString()
    }

    /**
     * Extract text portions from LLM output that appear before/after a JSON command.
     * Handles mixed mode where LLM writes commentary around the JSON.
     */
    private fun extractTextAroundCommand(text: String): List<String> {
        val jsonStart = text.indexOf('{')
        val jsonEnd = text.lastIndexOf('}')

        if (jsonStart == -1 || jsonEnd == -1) return emptyList()

        val textParts = mutableListOf<String>()

        // Text before JSON
        val before = text.substring(0, jsonStart).trim()
        if (before.isNotEmpty() && !before.startsWith("ACTION:", ignoreCase = true)) {
            textParts.add(before)
        }

        // Text after JSON
        val after = text.substring(jsonEnd + 1).trim()
        if (after.isNotEmpty()) {
            textParts.add(after)
        }

        return textParts
    }

    companion object {
        const val DEFAULT_MAX_ROUNDS = 10
    }
}
