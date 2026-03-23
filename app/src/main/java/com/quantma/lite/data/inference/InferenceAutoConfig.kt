package com.quantma.lite.data.inference

import timber.log.Timber

/**
 * Smart auto-configuration engine for inference parameters.
 *
 * Computes optimal values based on device capabilities and model size.
 * Each parameter can be overridden by the user (manual mode).
 */
object InferenceAutoConfig {

    /**
     * Complete set of resolved inference parameters.
     * Contains both model-loading and sampling parameters.
     */
    data class ResolvedParams(
        // Model loading
        val nThreads: Int,
        val contextSize: Int,
        val batchSize: Int,
        val nGpuLayers: Int,
        val flashAttention: Boolean,
        val mlock: Boolean,
        // Sampling
        val temperature: Float,
        val topP: Float,
        val topK: Int,
        val minP: Float,
        val repeatPenalty: Float,
        val penaltyLastN: Int
    )

    // ---- Thread count ----

    /**
     * Auto-detect optimal thread count.
     *
     * Strategy:
     * - Use performance (big) cores only — little cores slow down inference
     * - Leave 1 core free for Android UI thread
     * - Minimum 2 threads for reasonable performance
     * - Maximum 8 threads (diminishing returns beyond this)
     */
    fun autoThreads(cpu: DeviceCapabilities.CpuInfo): Int {
        val optimal = when {
            // If we can distinguish big/little, use big cores minus 1
            cpu.bigCores > 2 -> cpu.bigCores - 1
            // Fallback: total cores minus 2 (leave room for UI + system)
            cpu.totalCores >= 8 -> cpu.totalCores - 2
            cpu.totalCores >= 6 -> cpu.totalCores - 2
            cpu.totalCores >= 4 -> cpu.totalCores - 1
            else -> cpu.totalCores
        }
        return optimal.coerceIn(2, 8).also {
            Timber.i("Auto threads: $it (totalCores=${cpu.totalCores}, bigCores=${cpu.bigCores})")
        }
    }

    // ---- Context size ----

    /**
     * Auto-detect optimal context size based on available RAM and model size.
     *
     * Strategy:
     * - KV cache memory ≈ 2 * n_layers * d_model * 2(K+V) * ctx_size * sizeof(f16)
     * - For typical 1-4B models on mobile: ~1MB per 1024 ctx tokens
     * - Keep context within available memory budget
     * - Minimum 1024, max 8192 for mobile
     */
    fun autoContextSize(memory: DeviceCapabilities.MemoryInfo, modelSizeMb: Long): Int {
        // Available RAM budget for KV cache (30% of free RAM, minus model size)
        val kvBudgetMb = (memory.availableRamMb * 0.3 - modelSizeMb * 0.1).toLong()

        val ctx = when {
            kvBudgetMb >= 2048 -> 8192   // 16+ GB free RAM, large budget
            kvBudgetMb >= 1024 -> 4096   // 8+ GB free RAM
            kvBudgetMb >= 512 -> 2048    // 4+ GB free RAM
            kvBudgetMb >= 256 -> 1024    // Tight memory
            else -> 512                   // Very tight
        }

        return ctx.also {
            Timber.i("Auto context: $it (availRAM=${memory.availableRamMb}MB, model=${modelSizeMb}MB, kvBudget=${kvBudgetMb}MB)")
        }
    }

    // ---- Batch size ----

    /**
     * Auto-detect optimal batch size for prompt processing.
     *
     * Strategy:
     * - Larger batch = faster prompt processing, more memory
     * - Scale with available RAM
     * - Match with context size (no point having batch > ctx)
     */
    fun autoBatchSize(memory: DeviceCapabilities.MemoryInfo, contextSize: Int): Int {
        val batch = when {
            memory.availableRamMb >= 8192 -> 2048
            memory.availableRamMb >= 4096 -> 1024
            memory.availableRamMb >= 2048 -> 512
            else -> 256
        }
        // Batch should not exceed context size
        return batch.coerceAtMost(contextSize).also {
            Timber.i("Auto batch size: $it (availRAM=${memory.availableRamMb}MB)")
        }
    }

    // ---- GPU layers ----

    /**
     * Auto-detect GPU layer count.
     *
     * Strategy:
     * - If Vulkan or Hexagon available: offload as many layers as GPU VRAM allows
     * - For mobile GPUs with shared memory: be conservative (50% of layers)
     * - 0 if no GPU backend available
     * - 99 = "all layers" convention in llama.cpp
     */
    fun autoGpuLayers(gpuBackend: String, modelSizeMb: Long): Int {
        if (gpuBackend == "CPU only" || gpuBackend.isEmpty()) return 0

        val layers = when {
            // Hexagon DSP: offload everything (it manages its own memory)
            gpuBackend.contains("Hexagon", ignoreCase = true) -> 99
            // Vulkan with VRAM info
            gpuBackend.contains("Vulkan", ignoreCase = true) -> {
                // Parse VRAM from backend string like "Vulkan: Adreno 830 (8192 MB)"
                val vramMb = Regex("\\((\\d+)\\s*MB\\)").find(gpuBackend)
                    ?.groupValues?.get(1)?.toLongOrNull() ?: 0

                when {
                    vramMb == 0L -> 99     // Shared memory GPUs — try all, llama.cpp will handle
                    modelSizeMb <= vramMb * 0.7 -> 99  // Model fits in 70% VRAM
                    modelSizeMb <= vramMb -> 32         // Tight fit: partial offload
                    else -> 16                           // Model larger than VRAM
                }
            }
            else -> 0
        }

        return layers.also {
            Timber.i("Auto GPU layers: $it (backend=$gpuBackend, model=${modelSizeMb}MB)")
        }
    }

    // ---- Flash attention ----

    /**
     * Auto-detect whether to enable flash attention.
     *
     * Strategy:
     * - Enable for contexts >= 2048 (significant benefit)
     * - Safe to enable on all modern ARM64 devices
     */
    fun autoFlashAttention(contextSize: Int): Boolean {
        return (contextSize >= 2048).also {
            Timber.i("Auto flash attention: $it (ctx=$contextSize)")
        }
    }

    // ---- Mlock ----

    /**
     * Auto-detect whether to lock model in RAM.
     *
     * Strategy:
     * - Enable if total RAM >= 8GB and model uses < 50% of available RAM
     * - Prevents Android from swapping model pages to disk
     */
    fun autoMlock(memory: DeviceCapabilities.MemoryInfo, modelSizeMb: Long): Boolean {
        val enable = memory.totalRamMb >= 8192 && modelSizeMb < memory.availableRamMb / 2
        return enable.also {
            Timber.i("Auto mlock: $it (totalRAM=${memory.totalRamMb}MB, model=${modelSizeMb}MB)")
        }
    }

    // ---- Sampling defaults ----

    /** Balanced temperature for code generation */
    fun autoTemperature(): Float = 0.7f

    /** Standard nucleus sampling */
    fun autoTopP(): Float = 0.95f

    /**
     * Auto top-K based on use case.
     * 40 is a good balance between quality and diversity.
     */
    fun autoTopK(): Int = 40

    /**
     * Min-P: modern alternative to top-K/top-P.
     * 0.05 filters out very unlikely tokens while keeping diversity.
     */
    fun autoMinP(): Float = 0.05f

    /** Standard repeat penalty */
    fun autoRepeatPenalty(): Float = 1.1f

    /**
     * Penalty window: how many recent tokens to penalize for repeats.
     * 64 covers typical repetition patterns without being too aggressive.
     */
    fun autoPenaltyLastN(): Int = 64

    // ---- Full resolution ----

    /**
     * Resolve all parameters: use auto value if user value is null (auto mode),
     * otherwise use user's manual override.
     */
    fun resolve(
        profile: DeviceCapabilities.DeviceProfile,
        modelSizeMb: Long,
        // Nullable = auto mode for that param; non-null = manual override
        manualThreads: Int? = null,
        manualContextSize: Int? = null,
        manualBatchSize: Int? = null,
        manualGpuLayers: Int? = null,
        manualFlashAttention: Boolean? = null,
        manualMlock: Boolean? = null,
        manualTemperature: Float? = null,
        manualTopP: Float? = null,
        manualTopK: Int? = null,
        manualMinP: Float? = null,
        manualRepeatPenalty: Float? = null,
        manualPenaltyLastN: Int? = null
    ): ResolvedParams {
        val threads = manualThreads ?: autoThreads(profile.cpu)
        val contextSize = manualContextSize ?: autoContextSize(profile.memory, modelSizeMb)
        val batchSize = manualBatchSize ?: autoBatchSize(profile.memory, contextSize)
        val gpuLayers = manualGpuLayers ?: autoGpuLayers(profile.gpuBackend, modelSizeMb)
        val flashAttn = manualFlashAttention ?: autoFlashAttention(contextSize)
        val mlock = manualMlock ?: autoMlock(profile.memory, modelSizeMb)

        return ResolvedParams(
            nThreads = threads,
            contextSize = contextSize,
            batchSize = batchSize,
            nGpuLayers = gpuLayers,
            flashAttention = flashAttn,
            mlock = mlock,
            temperature = manualTemperature ?: autoTemperature(),
            topP = manualTopP ?: autoTopP(),
            topK = manualTopK ?: autoTopK(),
            minP = manualMinP ?: autoMinP(),
            repeatPenalty = manualRepeatPenalty ?: autoRepeatPenalty(),
            penaltyLastN = manualPenaltyLastN ?: autoPenaltyLastN()
        ).also {
            Timber.i("Resolved params: threads=${it.nThreads}, ctx=${it.contextSize}, " +
                    "batch=${it.batchSize}, gpu=${it.nGpuLayers}, flash=${it.flashAttention}, " +
                    "mlock=${it.mlock}, temp=${it.temperature}, topP=${it.topP}, topK=${it.topK}, " +
                    "minP=${it.minP}, repeat=${it.repeatPenalty}, penLastN=${it.penaltyLastN}")
        }
    }
}
