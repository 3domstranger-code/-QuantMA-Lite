package com.quantma.lite.data.inference

/**
 * Low-level JNI bridge to llama.cpp native library.
 *
 * All methods MUST be called from a single thread (llama.cpp is not thread-safe).
 * The only exception is [nativeAbort] which sets a volatile flag.
 */
object LlamaJni {

    init {
        System.loadLibrary("llama-jni")
    }

    /**
     * Initialize llama backend and load CPU backend variants.
     * @param nativeLibDir path to the app's native lib directory (for backend .so files)
     */
    external fun nativeInit(nativeLibDir: String)

    /**
     * Load a GGUF model file and create inference context.
     * @param nGpuLayers number of transformer layers to offload to GPU via Vulkan (0 = CPU only)
     * @param batchSize batch size for prompt processing (default 512)
     * @param flashAttention enable flash attention for faster long-context inference
     * @param mlock lock model weights in RAM to prevent paging
     * @return 0 on success, non-zero error code on failure
     */
    external fun nativeLoadModel(
        modelPath: String,
        nThreads: Int,
        contextSize: Int,
        nGpuLayers: Int = 0,
        batchSize: Int = 512,
        flashAttention: Boolean = false,
        mlock: Boolean = false
    ): Int

    /**
     * Get acceleration backend info string.
     * Returns e.g. "Hexagon | CPU KLEIDIAI", "Vulkan: Adreno 830 (8192 MB)", or "CPU only".
     * Must be called after nativeInit(). Phase 7 (v1.6.0), updated v2.5.x
     */
    external fun nativeGetGpuInfo(): String

    /**
     * Unload model and free all associated resources.
     */
    external fun nativeUnloadModel()

    /**
     * Set sampling parameters. Call BEFORE [nativeStartCompletion].
     * Parameters are stored globally and used when the sampler is (re)created.
     * @param temperature 0.0–2.0, default 0.7 (higher = more creative)
     * @param topP        0.0–1.0, default 0.95 (nucleus sampling)
     * @param repeatPenalty 1.0–2.0, default 1.1 (1.0 = disabled)
     * @param topK        top-K sampling (0 = disabled, default 40)
     * @param minP        min-P sampling threshold (0.0–1.0, default 0.05)
     * @param penaltyLastN tokens to consider for repeat penalty (default 64)
     */
    external fun nativeSetSamplingParams(
        temperature: Float,
        topP: Float,
        repeatPenalty: Float,
        topK: Int = 40,
        minP: Float = 0.05f,
        penaltyLastN: Int = 64
    )

    /**
     * Prepare for completion: tokenize and process the prompt.
     * Call [nativeNextToken] in a loop after this.
     * @return 0 on success, non-zero error code on failure
     */
    external fun nativeStartCompletion(prompt: String, maxTokens: Int): Int

    /**
     * Generate the next token.
     * @return token text, empty string for incomplete UTF-8, or null for end of generation
     */
    external fun nativeNextToken(): String?

    /**
     * Request cancellation of the current generation.
     * Thread-safe: can be called from any thread.
     */
    external fun nativeAbort()

    /**
     * Get llama.cpp system info string (CPU features, etc.).
     */
    external fun nativeSystemInfo(): String

    /**
     * Shut down the llama backend. Call on app termination.
     */
    external fun nativeShutdown()

    /**
     * Load a LoRA adapter .gguf file and apply it to the currently loaded model.
     * Must be called AFTER [nativeLoadModel].
     * @param path absolute path to the LoRA .gguf adapter file
     * @param scale adapter weight (0.0–1.0, default 1.0)
     * @return 0=success, 1=model not loaded, 2=adapter load failed
     * Phase 10 (v1.9.0)
     */
    external fun nativeLoadLoraAdapter(path: String, scale: Float): Int

    /**
     * Unload the current LoRA adapter and free its resources.
     * Phase 10 (v1.9.0)
     */
    external fun nativeUnloadLoraAdapter()

    /**
     * Returns the last native error message recorded during inference.
     * Populated when [nativeStartCompletion] returns error code 3 (C++ exception).
     * Use to surface the actual exception text without needing logcat/USB.
     */
    external fun nativeGetLastError(): String
}
