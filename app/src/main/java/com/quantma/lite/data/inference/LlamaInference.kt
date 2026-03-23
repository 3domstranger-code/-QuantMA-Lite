package com.quantma.lite.data.inference

import timber.log.Timber
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-level wrapper around llama.cpp JNI.
 *
 * Ensures single-threaded access via [llamaDispatcher] and provides
 * Flow-based token streaming.
 */
@Singleton
class LlamaInference @Inject constructor() {

    companion object {
        private const val DEFAULT_MAX_TOKENS = 1024
    }

    // Single-threaded dispatcher — llama.cpp is NOT thread-safe
    private val llamaDispatcher = Dispatchers.IO.limitedParallelism(1)

    private val _state = MutableStateFlow<InferenceState>(InferenceState.Uninitialized)
    val state: StateFlow<InferenceState> = _state.asStateFlow()

    private val _backendInfo = MutableStateFlow("CPU only")
    val backendInfo: StateFlow<String> = _backendInfo.asStateFlow()

    private var isModelLoaded = false

    /**
     * Initialize llama backend. Must be called once before any other operation.
     * @param nativeLibDir path to app's native lib directory
     */
    suspend fun initialize(nativeLibDir: String) = withContext(llamaDispatcher) {
        try {
            _state.value = InferenceState.Initializing
            LlamaJni.nativeInit(nativeLibDir)
            val systemInfo = LlamaJni.nativeSystemInfo()
            Timber.i("System info: $systemInfo")
            val backendInfo = LlamaJni.nativeGetGpuInfo()
            Timber.i("Backend info: $backendInfo")
            _backendInfo.value = backendInfo
            _state.value = InferenceState.Ready
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize: ${e.message}")
            _state.value = InferenceState.Error("Init failed: ${e.message}")
        }
    }

    /**
     * Load a GGUF model from the given file path.
     * @param nGpuLayers number of transformer layers to offload to Vulkan GPU (0 = CPU only)
     * @param batchSize batch size for prompt processing (default 512)
     * @param flashAttention enable flash attention for faster long-context inference
     * @param mlock lock model weights in RAM to prevent paging
     */
    suspend fun loadModel(
        modelPath: String,
        nThreads: Int,
        contextSize: Int,
        nGpuLayers: Int = 0,
        batchSize: Int = 512,
        flashAttention: Boolean = false,
        mlock: Boolean = false
    ): Result<Unit> = withContext(llamaDispatcher) {
        try {
            // Unload previous model if any
            if (isModelLoaded) {
                LlamaJni.nativeUnloadModel()
                isModelLoaded = false
            }

            _state.value = InferenceState.Loading
            Timber.i("Loading model: $modelPath (threads=$nThreads, ctx=$contextSize, " +
                    "gpu=$nGpuLayers, batch=$batchSize, flash=$flashAttention, mlock=$mlock)")

            val result = LlamaJni.nativeLoadModel(
                modelPath, nThreads, contextSize, nGpuLayers,
                batchSize, flashAttention, mlock
            )
            if (result == 0) {
                isModelLoaded = true
                _state.value = InferenceState.Loaded
                Timber.i("Model loaded successfully!")
                Result.success(Unit)
            } else {
                _state.value = InferenceState.Error("Load failed (code: $result)")
                Timber.e("Model load failed with code: $result")
                Result.failure(RuntimeException("Model load failed: $result"))
            }
        } catch (e: Exception) {
            _state.value = InferenceState.Error("Load error: ${e.message}")
            Timber.e(e, "Model load exception")
            Result.failure(e)
        }
    }

    /**
     * Generate completion tokens as a Flow.
     * Each emission is one or more characters (token piece).
     * Flow completes on EOG or abort. Cancel the collector to stop.
     */
    fun generateCompletion(
        prompt: String,
        maxTokens: Int = DEFAULT_MAX_TOKENS,
        temperature: Float = 0.7f,
        topP: Float = 0.95f,
        repeatPenalty: Float = 1.1f,
        topK: Int = 40,
        minP: Float = 0.05f,
        penaltyLastN: Int = 64
    ): Flow<String> = flow {
        if (!isModelLoaded) {
            throw IllegalStateException("Model not loaded")
        }

        _state.value = InferenceState.Generating

        // Apply sampling params before starting completion
        LlamaJni.nativeSetSamplingParams(temperature, topP, repeatPenalty, topK, minP, penaltyLastN)

        val startResult = LlamaJni.nativeStartCompletion(prompt, maxTokens)
        if (startResult != 0) {
            _state.value = InferenceState.Loaded
            val detail = when (startResult) {
                2 -> "KV cache overflow — clear the chat or reduce context size"
                3 -> "native crash — ${LlamaJni.nativeGetLastError()}"
                else -> "code: $startResult"
            }
            throw RuntimeException("Start completion failed ($detail)")
        }

        var tokenCount = 0
        try {
            while (tokenCount < maxTokens) {
                val token = LlamaJni.nativeNextToken() ?: break
                if (token.isNotEmpty()) {
                    emit(token)
                }
                tokenCount++
            }
        } catch (e: CancellationException) {
            LlamaJni.nativeAbort()
            throw e
        } finally {
            if (_state.value == InferenceState.Generating) {
                _state.value = InferenceState.Loaded
            }
            Timber.i("Generation done: $tokenCount tokens")
        }
    }.flowOn(llamaDispatcher).cancellable()

    /**
     * Abort current generation. Thread-safe.
     */
    fun abort() {
        LlamaJni.nativeAbort()
    }

    /**
     * Load a LoRA adapter over the currently loaded model.
     * Must be called after [loadModel].
     * Phase 10 (v1.9.0)
     */
    suspend fun loadLoraAdapter(path: String, scale: Float): Result<Unit> = withContext(llamaDispatcher) {
        try {
            Timber.i("Loading LoRA adapter: $path (scale=$scale)")
            val result = LlamaJni.nativeLoadLoraAdapter(path, scale)
            when (result) {
                0 -> {
                    Timber.i("LoRA adapter loaded successfully")
                    Result.success(Unit)
                }
                1 -> Result.failure(IllegalStateException("Cannot load LoRA: model not loaded"))
                2 -> Result.failure(RuntimeException("Failed to load LoRA adapter file: $path"))
                else -> Result.failure(RuntimeException("LoRA load failed (code=$result)"))
            }
        } catch (e: Exception) {
            Timber.e(e, "LoRA adapter load exception")
            Result.failure(e)
        }
    }

    /**
     * Unload the current LoRA adapter.
     * Phase 10 (v1.9.0)
     */
    suspend fun unloadLoraAdapter() = withContext(llamaDispatcher) {
        try {
            LlamaJni.nativeUnloadLoraAdapter()
            Timber.i("LoRA adapter unloaded")
        } catch (e: Exception) {
            Timber.e(e, "LoRA adapter unload exception")
        }
    }

    /**
     * Unload the current model.
     */
    suspend fun unloadModel() = withContext(llamaDispatcher) {
        if (isModelLoaded) {
            LlamaJni.nativeUnloadModel()
            isModelLoaded = false
            _state.value = InferenceState.Ready
            Timber.i("Model unloaded")
        }
    }

    /**
     * Shut down the entire llama backend.
     */
    suspend fun shutdown() = withContext(llamaDispatcher) {
        if (isModelLoaded) {
            LlamaJni.nativeUnloadModel()
            isModelLoaded = false
        }
        LlamaJni.nativeShutdown()
        _state.value = InferenceState.Uninitialized
    }
}

/**
 * States for the inference engine lifecycle.
 */
sealed interface InferenceState {
    data object Uninitialized : InferenceState
    data object Initializing : InferenceState
    data object Ready : InferenceState       // Backend init done, no model loaded
    data object Loading : InferenceState     // Model is being loaded
    data object Loaded : InferenceState      // Model loaded, ready for generation
    data object Generating : InferenceState  // Currently generating tokens
    data class Error(val message: String) : InferenceState
}
