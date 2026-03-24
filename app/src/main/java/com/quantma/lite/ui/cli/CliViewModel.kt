package com.quantma.lite.ui.cli

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantma.lite.BuildConfig
import com.quantma.lite.data.agent.ToolExecutor
import com.quantma.lite.data.inference.InferenceState
import com.quantma.lite.data.inference.LlamaInference
import com.quantma.lite.data.local.preferences.SettingsDataStore
import com.quantma.lite.data.performance.MemoryMonitor
import com.quantma.lite.data.performance.ThermalMonitor
import com.quantma.lite.domain.model.ToolCall
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class CliLineType { COMMAND, OUTPUT, ERROR, INFO }

data class CliLine(
    val text: String,
    val type: CliLineType
)

@HiltViewModel
class CliViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val llamaInference: LlamaInference,
    private val toolExecutor: ToolExecutor,
    private val memoryMonitor: MemoryMonitor,
    private val thermalMonitor: ThermalMonitor
) : ViewModel() {

    private val _lines = MutableStateFlow<List<CliLine>>(emptyList())
    val lines: StateFlow<List<CliLine>> = _lines.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    init {
        appendInfo("QuantMA Lite CLI — type /help for available commands")
    }

    fun processCommand(input: String) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return

        appendLine("> $trimmed", CliLineType.COMMAND)

        viewModelScope.launch {
            _isProcessing.value = true
            try {
                handleCommand(trimmed)
            } finally {
                _isProcessing.value = false
            }
        }
    }

    private suspend fun handleCommand(input: String) {
        val parts = input.split(" ", limit = 3)
        val cmd = parts[0].lowercase()

        when (cmd) {
            "/help"     -> showHelp()
            "/clear"    -> clearTerminal()
            "/models"   -> listModels()
            "/model"    -> setModel(parts.getOrNull(1))
            "/status"   -> showStatus()
            "/tools"    -> listTools()
            "/run"      -> runTool(parts.getOrNull(1), parts.getOrNull(2))
            "/chat"     -> {
                val message = input.removePrefix("/chat").trim()
                if (message.isBlank()) {
                    appendError("Usage: /chat <message>")
                } else {
                    runChat(message)
                }
            }
            "/settings" -> showSettings()
            "/version"  -> showVersion()
            "/write", "/git" -> appendError("Command not available in QuantMA Lite.")
            else -> appendError("Unknown command: $cmd. Type /help for available commands.")
        }
    }

    private fun showHelp() {
        appendOutput(
            """
QuantMA CLI — Available commands:
  /help               — show this help
  /clear              — clear terminal
  /models             — list available model files
  /model <name>       — set active model
  /status             — model status, RAM, thermal, backend
  /tools              — list available agent tools
  /run <tool> <args>  — execute a tool (e.g. /run read_file /path/file)
  /chat <message>     — send message to AI (single inference)
  /settings           — show current settings summary
  /version            — show app version
            """.trimIndent()
        )
    }

    private fun clearTerminal() {
        _lines.value = emptyList()
        appendInfo("Terminal cleared.")
    }

    private suspend fun listModels() {
        val modelPath = settingsDataStore.modelPath.first()
        val workingDir = settingsDataStore.agentWorkingDir.first()

        appendOutput("Searching for model files…")

        val searchDirs = buildList {
            if (modelPath.isNotBlank()) {
                val f = File(modelPath)
                if (f.isFile) add(f.parentFile)
                else if (f.isDirectory) add(f)
            }
            if (workingDir.isNotBlank()) add(File(workingDir))
            add(File("/sdcard/Download"))
            add(File("/sdcard/Models"))
        }.filterNotNull().filter { it.exists() && it.isDirectory }.distinctBy { it.absolutePath }

        val found = mutableListOf<File>()
        for (dir in searchDirs) {
            dir.walkTopDown().maxDepth(2).filter { it.isFile && it.name.endsWith(".gguf") }.forEach {
                found.add(it)
            }
        }

        if (found.isEmpty()) {
            appendOutput("No .gguf model files found.")
            appendInfo("Hint: copy a .gguf file to /sdcard/Download or set model path in Settings.")
        } else {
            appendOutput("Found ${found.size} model(s):")
            found.forEach { f ->
                val sizeMb = f.length() / (1024 * 1024)
                val active = if (f.absolutePath == modelPath) " [active]" else ""
                appendOutput("  ${f.name} (${sizeMb} MB)$active")
            }
        }
    }

    private suspend fun setModel(name: String?) {
        if (name.isNullOrBlank()) {
            appendError("Usage: /model <filename>")
            return
        }
        val workingDir = settingsDataStore.agentWorkingDir.first()
        val searchDirs = listOf(
            File("/sdcard/Download"),
            File("/sdcard/Models"),
            if (workingDir.isNotBlank()) File(workingDir) else null
        ).filterNotNull().filter { it.exists() }

        val match = searchDirs.flatMap { dir ->
            dir.walkTopDown().maxDepth(2).filter {
                it.isFile && (it.name == name || it.name == "$name.gguf")
            }.toList()
        }.firstOrNull()

        if (match == null) {
            appendError("Model not found: $name")
            appendInfo("Use /models to list available models.")
        } else {
            settingsDataStore.setModelPath(match.absolutePath)
            appendOutput("Active model set to: ${match.name}")
            appendInfo("Reload via Chat screen to use the new model.")
        }
    }

    private suspend fun showStatus() {
        val modelPath   = settingsDataStore.modelPath.first()
        val nThreads    = settingsDataStore.nThreads.first()
        val contextSize = settingsDataStore.contextSize.first()
        val workingDir  = settingsDataStore.agentWorkingDir.first()
        val useGpu      = settingsDataStore.useGpu.first()
        val gpuLayers   = settingsDataStore.gpuLayers.first()

        val inferenceState = llamaInference.state.value
        val backendInfo    = llamaInference.backendInfo.value

        val stateStr = when (inferenceState) {
            InferenceState.Uninitialized -> "Uninitialized"
            InferenceState.Initializing  -> "Initializing"
            InferenceState.Ready         -> "Ready (no model loaded)"
            InferenceState.Loading       -> "Loading model…"
            InferenceState.Loaded        -> "Loaded"
            InferenceState.Generating    -> "Generating"
            is InferenceState.Error      -> "Error: ${inferenceState.message}"
        }

        val ramAvail  = memoryMonitor.getAvailableRamMb()
        val ramTotal  = memoryMonitor.getTotalRamMb()
        val thermal   = thermalMonitor.thermalStatus.value
        val thermalStr = when (thermal) {
            0 -> "Normal"
            1 -> "Warm"
            2 -> "Hot"
            3 -> "Very Hot"
            else -> "Critical"
        }

        appendOutput(
            """
--- Status ---
Model:        ${if (modelPath.isBlank()) "(none)" else File(modelPath).name}
Model path:   ${modelPath.ifBlank { "(not set)" }}
State:        $stateStr
Backend:      $backendInfo
GPU:          ${if (useGpu) "ON ($gpuLayers layers)" else "OFF (CPU only)"}
Threads:      $nThreads
Context:      $contextSize tokens
Working dir:  ${workingDir.ifBlank { "(not set)" }}
RAM:          $ramAvail MB avail / $ramTotal MB total
Thermal:      $thermalStr
            """.trimIndent()
        )
    }

    private fun listTools() {
        appendOutput(
            """
Available agent tools (QuantMA Lite):
  read_file    — read a file by path
  list_files   — list files in a directory
  search_files — search text across project files
  git_status   — show git status
  git_diff     — show git diff
            """.trimIndent()
        )
    }

    private suspend fun runTool(toolName: String?, args: String?) {
        if (toolName.isNullOrBlank()) {
            appendError("Usage: /run <tool> [args]")
            appendInfo("Use /tools to list available tools.")
            return
        }
        val workingDir = settingsDataStore.agentWorkingDir.first()
        if (workingDir.isBlank()) {
            appendError("Working directory not set. Configure it in Settings.")
            return
        }
        appendOutput("Running tool: $toolName")
        try {
            val toolCall: ToolCall = when (toolName.lowercase()) {
                "read_file" -> {
                    val path = args?.trim() ?: run {
                        appendError("Usage: /run read_file <path>"); return
                    }
                    ToolCall.ReadFile(path)
                }
                "list_files"   -> ToolCall.ListFiles(args?.trim() ?: workingDir)
                "git_status"   -> ToolCall.GitStatus(workingDir)
                "git_diff"     -> ToolCall.GitDiff(workingDir)
                "search_files" -> {
                    val query = args?.trim() ?: run {
                        appendError("Usage: /run search_files <query>"); return
                    }
                    ToolCall.SearchFiles(query, workingDir)
                }
                else -> {
                    appendError("Tool not directly runnable via CLI: $toolName")
                    appendInfo("Runnable: read_file, list_files, git_status, git_diff, search_files")
                    return
                }
            }
            val result = toolExecutor.execute(toolCall, workingDir)
            val out = result.output.take(2000)
            appendOutput(out)
            if (result.output.length > 2000) appendInfo("… (output truncated to 2000 chars)")
        } catch (e: Exception) {
            appendError("Tool execution failed: ${e.message}")
        }
    }

    private suspend fun runChat(message: String) {
        val modelPath = settingsDataStore.modelPath.first()
        if (modelPath.isBlank()) {
            appendError("No model configured. Set a model in Settings or use /model <name>.")
            return
        }
        val state = llamaInference.state.value
        if (state != InferenceState.Loaded && state != InferenceState.Generating) {
            appendError("Model is not loaded. Load a model first via the Chat screen.")
            return
        }
        appendOutput("AI > …")
        val sb = StringBuilder()
        try {
            llamaInference.generateCompletion(
                prompt = message,
                maxTokens = 512,
                temperature = 0.7f,
                topP = 0.9f,
                repeatPenalty = 1.1f
            ).collect { token ->
                sb.append(token)
                val currentLines = _lines.value.toMutableList()
                if (currentLines.isNotEmpty() && currentLines.last().type == CliLineType.OUTPUT) {
                    currentLines[currentLines.size - 1] = CliLine("AI > $sb", CliLineType.OUTPUT)
                    _lines.value = currentLines
                }
            }
        } catch (e: Exception) {
            appendError("Chat failed: ${e.message}")
        }
    }

    private suspend fun showSettings() {
        val modelPath   = settingsDataStore.modelPath.first()
        val nThreads    = settingsDataStore.nThreads.first()
        val contextSize = settingsDataStore.contextSize.first()
        val workingDir  = settingsDataStore.agentWorkingDir.first()
        val temperature = settingsDataStore.temperature.first()
        val topP        = settingsDataStore.topP.first()
        val useGpu      = settingsDataStore.useGpu.first()
        val gpuLayers   = settingsDataStore.gpuLayers.first()

        appendOutput(
            """
--- Settings ---
Model path:   ${modelPath.ifBlank { "(not set)" }}
Threads:      $nThreads
Context size: $contextSize tokens
Working dir:  ${workingDir.ifBlank { "(not set)" }}
Temperature:  $temperature
Top-P:        $topP
GPU enabled:  $useGpu
GPU layers:   $gpuLayers
            """.trimIndent()
        )
    }

    private fun showVersion() {
        appendOutput("QuantMA Lite v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})")
        appendInfo("Local AI agent — runs fully offline via llama.cpp")
    }

    // --- helpers ---

    private fun appendOutput(text: String) {
        _lines.value = _lines.value + CliLine(text, CliLineType.OUTPUT)
    }

    private fun appendError(text: String) {
        _lines.value = _lines.value + CliLine(text, CliLineType.ERROR)
    }

    private fun appendInfo(text: String) {
        _lines.value = _lines.value + CliLine(text, CliLineType.INFO)
    }

    private fun appendLine(text: String, type: CliLineType) {
        _lines.value = _lines.value + CliLine(text, type)
    }
}
