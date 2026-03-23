package com.quantma.lite.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantma.lite.data.download.DownloadState
import com.quantma.lite.data.download.ModelDownloader
import com.quantma.lite.data.local.preferences.GitCredentialsStore
import com.quantma.lite.data.local.preferences.SettingsDataStore
import com.quantma.lite.domain.repository.GitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    @ApplicationContext private val context: Context,
    private val modelDownloader: ModelDownloader,       // Phase 10 (v1.9.0)
    private val gitRepository: GitRepository,           // v1.9.2
    private val gitCredentialsStore: GitCredentialsStore  // v1.9.2
) : ViewModel() {

    // Settings mode (v2.7.0)
    val advancedMode = settingsDataStore.advancedMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    fun setAdvancedMode(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setAdvancedMode(enabled) }
    }

    val modelPath = settingsDataStore.modelPath.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ""
    )

    val nThreads = settingsDataStore.nThreads.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 4
    )

    val contextSize = settingsDataStore.contextSize.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 2048
    )

    val agentWorkingDir = settingsDataStore.agentWorkingDir.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "/storage/emulated/0"
    )

    val agentMaxRounds = settingsDataStore.agentMaxRounds.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 5
    )

    private val _availableModels = MutableStateFlow<List<File>>(emptyList())
    val availableModels: StateFlow<List<File>> = _availableModels.asStateFlow()

    private val _modelsDir = MutableStateFlow("")
    val modelsDir: StateFlow<String> = _modelsDir.asStateFlow()

    init {
        refreshModels()
    }

    /**
     * Scan getExternalFilesDir("models") for .gguf files.
     * This directory doesn't require special permissions on Android 12+.
     * Path: /Android/data/com.quantma.lite/files/models/
     */
    fun refreshModels() {
        viewModelScope.launch {
            val dir = context.getExternalFilesDir("models")
            if (dir != null) {
                // Ensure directory exists
                if (!dir.exists()) {
                    dir.mkdirs()
                }
                _modelsDir.value = dir.absolutePath
                val models = dir.listFiles()
                    ?.filter { it.isFile && it.name.endsWith(".gguf", ignoreCase = true) }
                    ?.sortedBy { it.name.lowercase() }
                    ?: emptyList()
                _availableModels.value = models
            } else {
                _modelsDir.value = ""
                _availableModels.value = emptyList()
            }
        }
    }

    fun setModelPath(path: String) {
        viewModelScope.launch {
            settingsDataStore.setModelPath(path)
        }
    }

    fun requestModelReload() {
        settingsDataStore.requestModelReload()
    }

    fun setNThreads(threads: Int) {
        viewModelScope.launch {
            settingsDataStore.setNThreads(threads)
        }
    }

    fun setContextSize(size: Int) {
        viewModelScope.launch {
            settingsDataStore.setContextSize(size)
        }
    }

    fun setAgentWorkingDir(dir: String) {
        viewModelScope.launch {
            settingsDataStore.setAgentWorkingDir(dir)
        }
    }

    fun setAgentMaxRounds(rounds: Int) {
        viewModelScope.launch {
            settingsDataStore.setAgentMaxRounds(rounds)
        }
    }

    val language = settingsDataStore.language.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "system"
    )

    fun setLanguage(lang: String) {
        viewModelScope.launch {
            settingsDataStore.setLanguage(lang)
        }
    }

    val themeMode = settingsDataStore.themeMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "SYSTEM"
    )

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            settingsDataStore.setThemeMode(mode)
        }
    }

    val codeBlockFontSize = settingsDataStore.codeBlockFontSize.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 13f
    )

    fun setCodeBlockFontSize(size: Float) {
        viewModelScope.launch {
            settingsDataStore.setCodeBlockFontSize(size)
        }
    }

    val compressionEnabled = settingsDataStore.compressionEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    fun setCompressionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setCompressionEnabled(enabled)
        }
    }

    // Sampling params (Phase 1 v1.0.0)
    val temperature = settingsDataStore.temperature.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0.7f
    )

    val topP = settingsDataStore.topP.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0.95f
    )

    val repeatPenalty = settingsDataStore.repeatPenalty.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 1.1f
    )

    fun setTemperature(value: Float) {
        viewModelScope.launch { settingsDataStore.setTemperature(value) }
    }

    fun setTopP(value: Float) {
        viewModelScope.launch { settingsDataStore.setTopP(value) }
    }

    fun setRepeatPenalty(value: Float) {
        viewModelScope.launch { settingsDataStore.setRepeatPenalty(value) }
    }

    // GPU Vulkan settings (Phase 7 v1.6.0)
    val useGpu = settingsDataStore.useGpu.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val gpuLayers = settingsDataStore.gpuLayers.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    fun setUseGpu(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setUseGpu(enabled) }
    }

    fun setGpuLayers(layers: Int) {
        viewModelScope.launch { settingsDataStore.setGpuLayers(layers) }
    }

    // ---- Auto/manual mode flags ----

    val autoThreads = settingsDataStore.autoThreads.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val autoContextSize = settingsDataStore.autoContextSize.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val autoBatchSize = settingsDataStore.autoBatchSize.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val autoGpuLayers = settingsDataStore.autoGpuLayers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val autoFlashAttention = settingsDataStore.autoFlashAttention.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val autoMlock = settingsDataStore.autoMlock.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val autoTemperature = settingsDataStore.autoTemperature.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val autoTopP = settingsDataStore.autoTopP.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val autoTopK = settingsDataStore.autoTopK.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val autoMinP = settingsDataStore.autoMinP.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val autoRepeatPenalty = settingsDataStore.autoRepeatPenalty.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val autoPenaltyLastN = settingsDataStore.autoPenaltyLastN.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setAutoThreads(auto: Boolean) { viewModelScope.launch { settingsDataStore.setAutoThreads(auto) } }
    fun setAutoContextSize(auto: Boolean) { viewModelScope.launch { settingsDataStore.setAutoContextSize(auto) } }
    fun setAutoBatchSize(auto: Boolean) { viewModelScope.launch { settingsDataStore.setAutoBatchSize(auto) } }
    fun setAutoGpuLayers(auto: Boolean) { viewModelScope.launch { settingsDataStore.setAutoGpuLayers(auto) } }
    fun setAutoFlashAttention(auto: Boolean) { viewModelScope.launch { settingsDataStore.setAutoFlashAttention(auto) } }
    fun setAutoMlock(auto: Boolean) { viewModelScope.launch { settingsDataStore.setAutoMlock(auto) } }
    fun setAutoTemperature(auto: Boolean) { viewModelScope.launch { settingsDataStore.setAutoTemperature(auto) } }
    fun setAutoTopP(auto: Boolean) { viewModelScope.launch { settingsDataStore.setAutoTopP(auto) } }
    fun setAutoTopK(auto: Boolean) { viewModelScope.launch { settingsDataStore.setAutoTopK(auto) } }
    fun setAutoMinP(auto: Boolean) { viewModelScope.launch { settingsDataStore.setAutoMinP(auto) } }
    fun setAutoRepeatPenalty(auto: Boolean) { viewModelScope.launch { settingsDataStore.setAutoRepeatPenalty(auto) } }
    fun setAutoPenaltyLastN(auto: Boolean) { viewModelScope.launch { settingsDataStore.setAutoPenaltyLastN(auto) } }

    // ---- New inference parameters ----

    val batchSize = settingsDataStore.batchSize.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 512)
    val flashAttention = settingsDataStore.flashAttention.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val mlock = settingsDataStore.mlock.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setBatchSize(size: Int) { viewModelScope.launch { settingsDataStore.setBatchSize(size) } }
    fun setFlashAttention(enabled: Boolean) { viewModelScope.launch { settingsDataStore.setFlashAttention(enabled) } }
    fun setMlock(enabled: Boolean) { viewModelScope.launch { settingsDataStore.setMlock(enabled) } }

    // ---- Extended sampling parameters ----

    val topK = settingsDataStore.topK.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 40)
    val minP = settingsDataStore.minP.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.05f)
    val penaltyLastN = settingsDataStore.penaltyLastN.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 64)

    fun setTopK(value: Int) { viewModelScope.launch { settingsDataStore.setTopK(value) } }
    fun setMinP(value: Float) { viewModelScope.launch { settingsDataStore.setMinP(value) } }
    fun setPenaltyLastN(value: Int) { viewModelScope.launch { settingsDataStore.setPenaltyLastN(value) } }

    // ---- Model Downloader (Phase 10 v1.9.0) ----

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private var downloadJob: Job? = null
    private var activeDownloadId = -1L

    fun startDownload(url: String) {
        if (url.isBlank()) return
        downloadJob = viewModelScope.launch {
            try {
                val (downloadId, filename) = modelDownloader.startDownload(url)
                activeDownloadId = downloadId
                modelDownloader.observeProgress(downloadId, filename).collect { state ->
                    _downloadState.value = state
                    if (state is DownloadState.Done) {
                        refreshModels()
                    }
                }
            } catch (e: Exception) {
                _downloadState.value = DownloadState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun cancelDownload() {
        if (activeDownloadId > 0) {
            modelDownloader.cancel(activeDownloadId)
            activeDownloadId = -1L
        }
        downloadJob?.cancel()
        downloadJob = null
        _downloadState.value = DownloadState.Idle
    }

    fun resetDownloadState() {
        _downloadState.value = DownloadState.Idle
    }

    // ---- LoRA Adapter settings (Phase 10 v1.9.0) ----

    val loraEnabled = settingsDataStore.loraEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val loraPath = settingsDataStore.loraPath.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ""
    )

    val loraScale = settingsDataStore.loraScale.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 1.0f
    )

    fun setLoraEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setLoraEnabled(enabled)
            settingsDataStore.requestModelReload()
        }
    }

    fun setLoraPath(path: String) {
        viewModelScope.launch {
            settingsDataStore.setLoraPath(path)
            // Trigger reload only if lora is enabled
            if (settingsDataStore.loraEnabled.stateIn(viewModelScope).value) {
                settingsDataStore.requestModelReload()
            }
        }
    }

    fun setLoraScale(scale: Float) {
        viewModelScope.launch {
            settingsDataStore.setLoraScale(scale)
        }
    }

    fun applyLoraScale() {
        // Called when user commits scale change (e.g. slider released)
        settingsDataStore.requestModelReload()
    }

    // ---- Clone to Working Directory (v1.9.2) ----

    sealed class CloneState {
        object Idle : CloneState()
        object Cloning : CloneState()
        data class Done(val path: String) : CloneState()
        data class Error(val message: String) : CloneState()
    }

    private val _cloneState = MutableStateFlow<CloneState>(CloneState.Idle)
    val cloneState: StateFlow<CloneState> = _cloneState.asStateFlow()

    fun cloneAndSetWorkingDir(url: String) {
        if (url.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            _cloneState.value = CloneState.Cloning
            val repoName = url.trim()
                .substringAfterLast("/")
                .removeSuffix(".git")
                .ifEmpty { "repo" }
            val destPath = "/storage/emulated/0/$repoName"
            val token = gitCredentialsStore.getToken()
            gitRepository.clone(url.trim(), destPath, token).fold(
                onSuccess = {
                    settingsDataStore.setAgentWorkingDir(destPath)
                    _cloneState.value = CloneState.Done(destPath)
                },
                onFailure = { e ->
                    _cloneState.value = CloneState.Error(e.message ?: "Clone failed")
                }
            )
        }
    }

    fun resetCloneState() {
        _cloneState.value = CloneState.Idle
    }

    // ---- Custom Theme (Phase 12) ----

    val customBgUri = settingsDataStore.customBgUri.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ""
    )

    val customBgOpacity = settingsDataStore.customBgOpacity.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0.15f
    )

    val customUserBubble = settingsDataStore.customUserBubble.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ""
    )

    val customAssistantBubble = settingsDataStore.customAssistantBubble.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ""
    )

    val customAccent = settingsDataStore.customAccent.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ""
    )

    val customPanel = settingsDataStore.customPanel.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ""
    )

    // Extended colors (v2.11.0)
    val customThermalOk = settingsDataStore.customThermalOk.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val customThermalWarn = settingsDataStore.customThermalWarn.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val customThermalHot = settingsDataStore.customThermalHot.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val customCpuHigh = settingsDataStore.customCpuHigh.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val customGpuBar = settingsDataStore.customGpuBar.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val customBackendBadge = settingsDataStore.customBackendBadge.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    fun setCustomThermalOk(hex: String) { viewModelScope.launch { settingsDataStore.setCustomThermalOk(hex) } }
    fun setCustomThermalWarn(hex: String) { viewModelScope.launch { settingsDataStore.setCustomThermalWarn(hex) } }
    fun setCustomThermalHot(hex: String) { viewModelScope.launch { settingsDataStore.setCustomThermalHot(hex) } }
    fun setCustomCpuHigh(hex: String) { viewModelScope.launch { settingsDataStore.setCustomCpuHigh(hex) } }
    fun setCustomGpuBar(hex: String) { viewModelScope.launch { settingsDataStore.setCustomGpuBar(hex) } }
    fun setCustomBackendBadge(hex: String) { viewModelScope.launch { settingsDataStore.setCustomBackendBadge(hex) } }

    fun setCustomBgUri(uri: String) {
        viewModelScope.launch { settingsDataStore.setCustomBgUri(uri) }
    }

    fun setCustomBgOpacity(opacity: Float) {
        viewModelScope.launch { settingsDataStore.setCustomBgOpacity(opacity) }
    }

    fun setCustomUserBubble(hex: String) {
        viewModelScope.launch { settingsDataStore.setCustomUserBubble(hex) }
    }

    fun setCustomAssistantBubble(hex: String) {
        viewModelScope.launch { settingsDataStore.setCustomAssistantBubble(hex) }
    }

    fun setCustomAccent(hex: String) {
        viewModelScope.launch { settingsDataStore.setCustomAccent(hex) }
    }

    fun setCustomPanel(hex: String) {
        viewModelScope.launch { settingsDataStore.setCustomPanel(hex) }
    }

    fun applyColorPreset(preset: com.quantma.lite.ui.theme.CustomThemeColors.Companion.ColorPreset) {
        viewModelScope.launch {
            settingsDataStore.setCustomUserBubble(preset.userBubble)
            settingsDataStore.setCustomAssistantBubble(preset.assistantBubble)
            settingsDataStore.setCustomAccent(preset.accent)
            settingsDataStore.setCustomPanel(preset.panel)
        }
    }

    fun resetCustomTheme() {
        viewModelScope.launch {
            settingsDataStore.setCustomBgUri("")
            settingsDataStore.setCustomBgOpacity(0.15f)
            settingsDataStore.setCustomUserBubble("")
            settingsDataStore.setCustomAssistantBubble("")
            settingsDataStore.setCustomAccent("")
            settingsDataStore.setCustomPanel("")
        }
    }
}
