package com.quantma.lite.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val MODEL_PATH_KEY = stringPreferencesKey("model_path")
        private val N_THREADS_KEY = intPreferencesKey("n_threads")
        private val CONTEXT_SIZE_KEY = intPreferencesKey("context_size")
        private val AGENT_WORKING_DIR_KEY = stringPreferencesKey("agent_working_dir")
        private val AGENT_MAX_ROUNDS_KEY = intPreferencesKey("agent_max_rounds")
        private val CURRENT_SESSION_ID_KEY = longPreferencesKey("current_session_id")
        private val LANGUAGE_KEY = stringPreferencesKey("language")
        private val COMPRESSION_ENABLED_KEY = booleanPreferencesKey("compression_enabled")
        // Git author (Phase 4 v1.3.0)
        private val GIT_AUTHOR_NAME_KEY = stringPreferencesKey("git_author_name")
        private val GIT_AUTHOR_EMAIL_KEY = stringPreferencesKey("git_author_email")
        // Sampling params (Phase 1 v1.0.0)
        private val TEMPERATURE_KEY = floatPreferencesKey("temperature")
        private val TOP_P_KEY = floatPreferencesKey("top_p")
        private val REPEAT_PENALTY_KEY = floatPreferencesKey("repeat_penalty")
        // GPU Vulkan (Phase 7 v1.6.0)
        private val USE_GPU_KEY = booleanPreferencesKey("use_gpu")
        private val GPU_LAYERS_KEY = intPreferencesKey("gpu_layers")
        // LoRA Adapter (Phase 10 v1.9.0)
        private val LORA_ENABLED_KEY = booleanPreferencesKey("lora_enabled")
        private val LORA_PATH_KEY = stringPreferencesKey("lora_path")
        private val LORA_SCALE_KEY = floatPreferencesKey("lora_scale")
        // Onboarding (Phase 9 v1.9.1)
        private val ONBOARDING_COMPLETE_KEY = booleanPreferencesKey("onboarding_complete")
        // RAG / Knowledge Base (Phase 11 v2.0.0)
        private val RAG_ENABLED_KEY = booleanPreferencesKey("rag_enabled")
        private val RAG_INDEX_PATH_KEY = stringPreferencesKey("rag_index_path")
        // Appearance
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val CODE_FONT_SIZE_KEY = floatPreferencesKey("code_font_size")
        // Custom theme (Phase 12)
        private val CUSTOM_BG_URI_KEY = stringPreferencesKey("custom_bg_uri")
        private val CUSTOM_BG_OPACITY_KEY = floatPreferencesKey("custom_bg_opacity")
        private val CUSTOM_USER_BUBBLE_KEY = stringPreferencesKey("custom_user_bubble")
        private val CUSTOM_ASSISTANT_BUBBLE_KEY = stringPreferencesKey("custom_assistant_bubble")
        private val CUSTOM_ACCENT_KEY = stringPreferencesKey("custom_accent")
        private val CUSTOM_PANEL_KEY = stringPreferencesKey("custom_panel")
        // Model load failure tracking (crash-loop protection)
        private val MODEL_LOAD_FAIL_COUNT_KEY = intPreferencesKey("model_load_fail_count")
        // Extended colors (v2.11.0)
        private val CUSTOM_THERMAL_OK_KEY = stringPreferencesKey("custom_thermal_ok")
        private val CUSTOM_THERMAL_WARN_KEY = stringPreferencesKey("custom_thermal_warn")
        private val CUSTOM_THERMAL_HOT_KEY = stringPreferencesKey("custom_thermal_hot")
        private val CUSTOM_CPU_HIGH_KEY = stringPreferencesKey("custom_cpu_high")
        private val CUSTOM_GPU_BAR_KEY = stringPreferencesKey("custom_gpu_bar")
        private val CUSTOM_BACKEND_BADGE_KEY = stringPreferencesKey("custom_backend_badge")
        // Settings mode (v2.7.0)
        private val ADVANCED_MODE_KEY = booleanPreferencesKey("advanced_mode")

        // Auto/manual mode flags — true = auto (default), false = manual override
        private val AUTO_THREADS_KEY = booleanPreferencesKey("auto_threads")
        private val AUTO_CONTEXT_SIZE_KEY = booleanPreferencesKey("auto_context_size")
        private val AUTO_BATCH_SIZE_KEY = booleanPreferencesKey("auto_batch_size")
        private val AUTO_GPU_LAYERS_KEY = booleanPreferencesKey("auto_gpu_layers")
        private val AUTO_FLASH_ATTENTION_KEY = booleanPreferencesKey("auto_flash_attention")
        private val AUTO_MLOCK_KEY = booleanPreferencesKey("auto_mlock")
        private val AUTO_TEMPERATURE_KEY = booleanPreferencesKey("auto_temperature")
        private val AUTO_TOP_P_KEY = booleanPreferencesKey("auto_top_p")
        private val AUTO_TOP_K_KEY = booleanPreferencesKey("auto_top_k")
        private val AUTO_MIN_P_KEY = booleanPreferencesKey("auto_min_p")
        private val AUTO_REPEAT_PENALTY_KEY = booleanPreferencesKey("auto_repeat_penalty")
        private val AUTO_PENALTY_LAST_N_KEY = booleanPreferencesKey("auto_penalty_last_n")

        // New inference params
        private val BATCH_SIZE_KEY = intPreferencesKey("batch_size")
        private val FLASH_ATTENTION_KEY = booleanPreferencesKey("flash_attention")
        private val MLOCK_KEY = booleanPreferencesKey("mlock")

        // Extended sampling params
        private val TOP_K_KEY = intPreferencesKey("top_k")
        private val MIN_P_KEY = floatPreferencesKey("min_p")
        private val PENALTY_LAST_N_KEY = intPreferencesKey("penalty_last_n")
    }

    private val _modelReloadTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val modelReloadTrigger: SharedFlow<Unit> = _modelReloadTrigger.asSharedFlow()

    fun requestModelReload() {
        _modelReloadTrigger.tryEmit(Unit)
    }

    val modelPath: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[MODEL_PATH_KEY] ?: ""
    }

    val nThreads: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[N_THREADS_KEY] ?: 4
    }

    val contextSize: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[CONTEXT_SIZE_KEY] ?: 2048
    }

    suspend fun setModelPath(path: String) {
        context.dataStore.edit { prefs ->
            prefs[MODEL_PATH_KEY] = path
        }
    }

    suspend fun setNThreads(threads: Int) {
        context.dataStore.edit { prefs ->
            prefs[N_THREADS_KEY] = threads
        }
    }

    suspend fun setContextSize(size: Int) {
        context.dataStore.edit { prefs ->
            prefs[CONTEXT_SIZE_KEY] = size
        }
    }

    val agentWorkingDir: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[AGENT_WORKING_DIR_KEY] ?: "/storage/emulated/0"
    }

    val agentMaxRounds: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[AGENT_MAX_ROUNDS_KEY] ?: 5
    }

    suspend fun setAgentWorkingDir(dir: String) {
        context.dataStore.edit { prefs ->
            prefs[AGENT_WORKING_DIR_KEY] = dir
        }
    }

    suspend fun setAgentMaxRounds(rounds: Int) {
        context.dataStore.edit { prefs ->
            prefs[AGENT_MAX_ROUNDS_KEY] = rounds
        }
    }

    val currentSessionId: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[CURRENT_SESSION_ID_KEY] ?: 0L
    }

    suspend fun setCurrentSessionId(sessionId: Long) {
        context.dataStore.edit { prefs ->
            prefs[CURRENT_SESSION_ID_KEY] = sessionId
        }
    }

    /** Language: "system", "en", "ru" */
    val language: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[LANGUAGE_KEY] ?: "system"
    }

    suspend fun setLanguage(lang: String) {
        context.dataStore.edit { prefs ->
            prefs[LANGUAGE_KEY] = lang
        }
    }

    val compressionEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[COMPRESSION_ENABLED_KEY] ?: true
    }

    suspend fun setCompressionEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[COMPRESSION_ENABLED_KEY] = enabled
        }
    }

    // Git author (Phase 4 v1.3.0)
    val gitAuthorName: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[GIT_AUTHOR_NAME_KEY] ?: "QuantMA User"
    }

    val gitAuthorEmail: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[GIT_AUTHOR_EMAIL_KEY] ?: "user@codeagent.local"
    }

    suspend fun setGitAuthorName(name: String) {
        context.dataStore.edit { prefs ->
            prefs[GIT_AUTHOR_NAME_KEY] = name
        }
    }

    suspend fun setGitAuthorEmail(email: String) {
        context.dataStore.edit { prefs ->
            prefs[GIT_AUTHOR_EMAIL_KEY] = email
        }
    }

    // Sampling params (Phase 1 v1.0.0)
    val temperature: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[TEMPERATURE_KEY] ?: 0.7f
    }

    val topP: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[TOP_P_KEY] ?: 0.95f
    }

    val repeatPenalty: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[REPEAT_PENALTY_KEY] ?: 1.1f
    }

    suspend fun setTemperature(value: Float) {
        context.dataStore.edit { prefs ->
            prefs[TEMPERATURE_KEY] = value
        }
    }

    suspend fun setTopP(value: Float) {
        context.dataStore.edit { prefs ->
            prefs[TOP_P_KEY] = value
        }
    }

    suspend fun setRepeatPenalty(value: Float) {
        context.dataStore.edit { prefs ->
            prefs[REPEAT_PENALTY_KEY] = value
        }
    }

    // GPU Vulkan settings (Phase 7 v1.6.0)
    val useGpu: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[USE_GPU_KEY] ?: false
    }

    val gpuLayers: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[GPU_LAYERS_KEY] ?: 0
    }

    suspend fun setUseGpu(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[USE_GPU_KEY] = enabled
        }
    }

    suspend fun setGpuLayers(layers: Int) {
        context.dataStore.edit { prefs ->
            prefs[GPU_LAYERS_KEY] = layers
        }
    }

    // LoRA Adapter settings (Phase 10 v1.9.0)
    val loraEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[LORA_ENABLED_KEY] ?: false
    }

    val loraPath: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[LORA_PATH_KEY] ?: ""
    }

    val loraScale: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[LORA_SCALE_KEY] ?: 1.0f
    }

    suspend fun setLoraEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[LORA_ENABLED_KEY] = enabled
        }
    }

    suspend fun setLoraPath(path: String) {
        context.dataStore.edit { prefs ->
            prefs[LORA_PATH_KEY] = path
        }
    }

    suspend fun setLoraScale(scale: Float) {
        context.dataStore.edit { prefs ->
            prefs[LORA_SCALE_KEY] = scale
        }
    }

    // Onboarding (Phase 9 v1.9.1)
    val onboardingComplete: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[ONBOARDING_COMPLETE_KEY] ?: false
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[ONBOARDING_COMPLETE_KEY] = complete
        }
    }

    // RAG / Knowledge Base (Phase 11 v2.0.0)
    val ragEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[RAG_ENABLED_KEY] ?: false
    }

    val ragIndexPath: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[RAG_INDEX_PATH_KEY] ?: ""
    }

    suspend fun setRagEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[RAG_ENABLED_KEY] = enabled
        }
    }

    suspend fun setRagIndexPath(path: String) {
        context.dataStore.edit { prefs ->
            prefs[RAG_INDEX_PATH_KEY] = path
        }
    }

    // Appearance
    val themeMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[THEME_MODE_KEY] ?: "SYSTEM"
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[THEME_MODE_KEY] = mode
        }
    }

    val codeBlockFontSize: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[CODE_FONT_SIZE_KEY] ?: 13f
    }

    suspend fun setCodeBlockFontSize(size: Float) {
        context.dataStore.edit { prefs ->
            prefs[CODE_FONT_SIZE_KEY] = size
        }
    }

    // Custom theme (Phase 12)
    val customBgUri: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[CUSTOM_BG_URI_KEY] ?: ""
    }

    val customBgOpacity: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[CUSTOM_BG_OPACITY_KEY] ?: 0.15f
    }

    val customUserBubble: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[CUSTOM_USER_BUBBLE_KEY] ?: ""
    }

    val customAssistantBubble: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[CUSTOM_ASSISTANT_BUBBLE_KEY] ?: ""
    }

    val customAccent: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[CUSTOM_ACCENT_KEY] ?: ""
    }

    val customPanel: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[CUSTOM_PANEL_KEY] ?: ""
    }

    suspend fun setCustomBgUri(uri: String) {
        context.dataStore.edit { prefs -> prefs[CUSTOM_BG_URI_KEY] = uri }
    }

    suspend fun setCustomBgOpacity(opacity: Float) {
        context.dataStore.edit { prefs -> prefs[CUSTOM_BG_OPACITY_KEY] = opacity }
    }

    suspend fun setCustomUserBubble(hex: String) {
        context.dataStore.edit { prefs -> prefs[CUSTOM_USER_BUBBLE_KEY] = hex }
    }

    suspend fun setCustomAssistantBubble(hex: String) {
        context.dataStore.edit { prefs -> prefs[CUSTOM_ASSISTANT_BUBBLE_KEY] = hex }
    }

    suspend fun setCustomAccent(hex: String) {
        context.dataStore.edit { prefs -> prefs[CUSTOM_ACCENT_KEY] = hex }
    }

    suspend fun setCustomPanel(hex: String) {
        context.dataStore.edit { prefs -> prefs[CUSTOM_PANEL_KEY] = hex }
    }

    // Extended colors (v2.11.0)
    val customThermalOk: Flow<String> = context.dataStore.data.map { prefs -> prefs[CUSTOM_THERMAL_OK_KEY] ?: "" }
    val customThermalWarn: Flow<String> = context.dataStore.data.map { prefs -> prefs[CUSTOM_THERMAL_WARN_KEY] ?: "" }
    val customThermalHot: Flow<String> = context.dataStore.data.map { prefs -> prefs[CUSTOM_THERMAL_HOT_KEY] ?: "" }
    val customCpuHigh: Flow<String> = context.dataStore.data.map { prefs -> prefs[CUSTOM_CPU_HIGH_KEY] ?: "" }
    val customGpuBar: Flow<String> = context.dataStore.data.map { prefs -> prefs[CUSTOM_GPU_BAR_KEY] ?: "" }
    val customBackendBadge: Flow<String> = context.dataStore.data.map { prefs -> prefs[CUSTOM_BACKEND_BADGE_KEY] ?: "" }

    suspend fun setCustomThermalOk(hex: String) { context.dataStore.edit { prefs -> prefs[CUSTOM_THERMAL_OK_KEY] = hex } }
    suspend fun setCustomThermalWarn(hex: String) { context.dataStore.edit { prefs -> prefs[CUSTOM_THERMAL_WARN_KEY] = hex } }
    suspend fun setCustomThermalHot(hex: String) { context.dataStore.edit { prefs -> prefs[CUSTOM_THERMAL_HOT_KEY] = hex } }
    suspend fun setCustomCpuHigh(hex: String) { context.dataStore.edit { prefs -> prefs[CUSTOM_CPU_HIGH_KEY] = hex } }
    suspend fun setCustomGpuBar(hex: String) { context.dataStore.edit { prefs -> prefs[CUSTOM_GPU_BAR_KEY] = hex } }
    suspend fun setCustomBackendBadge(hex: String) { context.dataStore.edit { prefs -> prefs[CUSTOM_BACKEND_BADGE_KEY] = hex } }

    // Settings mode (v2.7.0)
    val advancedMode: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[ADVANCED_MODE_KEY] ?: false
    }

    suspend fun setAdvancedMode(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[ADVANCED_MODE_KEY] = enabled }
    }

    // ---- Auto/manual mode flags ----
    // true = auto (smart detection), false = use manual value from settings

    val autoThreads: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[AUTO_THREADS_KEY] ?: true
    }
    suspend fun setAutoThreads(auto: Boolean) {
        context.dataStore.edit { prefs -> prefs[AUTO_THREADS_KEY] = auto }
    }

    val autoContextSize: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[AUTO_CONTEXT_SIZE_KEY] ?: true
    }
    suspend fun setAutoContextSize(auto: Boolean) {
        context.dataStore.edit { prefs -> prefs[AUTO_CONTEXT_SIZE_KEY] = auto }
    }

    val autoBatchSize: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[AUTO_BATCH_SIZE_KEY] ?: true
    }
    suspend fun setAutoBatchSize(auto: Boolean) {
        context.dataStore.edit { prefs -> prefs[AUTO_BATCH_SIZE_KEY] = auto }
    }

    val autoGpuLayers: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[AUTO_GPU_LAYERS_KEY] ?: true
    }
    suspend fun setAutoGpuLayers(auto: Boolean) {
        context.dataStore.edit { prefs -> prefs[AUTO_GPU_LAYERS_KEY] = auto }
    }

    val autoFlashAttention: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[AUTO_FLASH_ATTENTION_KEY] ?: true
    }
    suspend fun setAutoFlashAttention(auto: Boolean) {
        context.dataStore.edit { prefs -> prefs[AUTO_FLASH_ATTENTION_KEY] = auto }
    }

    val autoMlock: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[AUTO_MLOCK_KEY] ?: true
    }
    suspend fun setAutoMlock(auto: Boolean) {
        context.dataStore.edit { prefs -> prefs[AUTO_MLOCK_KEY] = auto }
    }

    val autoTemperature: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[AUTO_TEMPERATURE_KEY] ?: true
    }
    suspend fun setAutoTemperature(auto: Boolean) {
        context.dataStore.edit { prefs -> prefs[AUTO_TEMPERATURE_KEY] = auto }
    }

    val autoTopP: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[AUTO_TOP_P_KEY] ?: true
    }
    suspend fun setAutoTopP(auto: Boolean) {
        context.dataStore.edit { prefs -> prefs[AUTO_TOP_P_KEY] = auto }
    }

    val autoTopK: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[AUTO_TOP_K_KEY] ?: true
    }
    suspend fun setAutoTopK(auto: Boolean) {
        context.dataStore.edit { prefs -> prefs[AUTO_TOP_K_KEY] = auto }
    }

    val autoMinP: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[AUTO_MIN_P_KEY] ?: true
    }
    suspend fun setAutoMinP(auto: Boolean) {
        context.dataStore.edit { prefs -> prefs[AUTO_MIN_P_KEY] = auto }
    }

    val autoRepeatPenalty: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[AUTO_REPEAT_PENALTY_KEY] ?: true
    }
    suspend fun setAutoRepeatPenalty(auto: Boolean) {
        context.dataStore.edit { prefs -> prefs[AUTO_REPEAT_PENALTY_KEY] = auto }
    }

    val autoPenaltyLastN: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[AUTO_PENALTY_LAST_N_KEY] ?: true
    }
    suspend fun setAutoPenaltyLastN(auto: Boolean) {
        context.dataStore.edit { prefs -> prefs[AUTO_PENALTY_LAST_N_KEY] = auto }
    }

    // ---- New inference parameters ----

    val batchSize: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[BATCH_SIZE_KEY] ?: 512
    }
    suspend fun setBatchSize(size: Int) {
        context.dataStore.edit { prefs -> prefs[BATCH_SIZE_KEY] = size }
    }

    val flashAttention: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[FLASH_ATTENTION_KEY] ?: false
    }
    suspend fun setFlashAttention(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[FLASH_ATTENTION_KEY] = enabled }
    }

    val mlock: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[MLOCK_KEY] ?: false
    }
    suspend fun setMlock(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[MLOCK_KEY] = enabled }
    }

    // ---- Extended sampling parameters ----

    val topK: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[TOP_K_KEY] ?: 40
    }
    suspend fun setTopK(value: Int) {
        context.dataStore.edit { prefs -> prefs[TOP_K_KEY] = value }
    }

    val minP: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[MIN_P_KEY] ?: 0.05f
    }
    suspend fun setMinP(value: Float) {
        context.dataStore.edit { prefs -> prefs[MIN_P_KEY] = value }
    }

    val penaltyLastN: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[PENALTY_LAST_N_KEY] ?: 64
    }
    suspend fun setPenaltyLastN(value: Int) {
        context.dataStore.edit { prefs -> prefs[PENALTY_LAST_N_KEY] = value }
    }
}
