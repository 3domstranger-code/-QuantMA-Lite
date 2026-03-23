package com.quantma.lite.data.performance

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.BufferedReader
import java.io.FileReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitors CPU and GPU utilization by reading /proc/stat and Adreno/Mali sysfs nodes.
 * Polls every 3 seconds.
 * v0.11.0
 */
@Singleton
class CpuGpuMonitor @Inject constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _cpuLoad = MutableStateFlow(0f)
    val cpuLoad: StateFlow<Float> = _cpuLoad.asStateFlow()

    private val _gpuLoad = MutableStateFlow(-1f) // -1 = not available
    val gpuLoad: StateFlow<Float> = _gpuLoad.asStateFlow()

    // Previous CPU jiffy values
    private var prevTotal = 0L
    private var prevIdle = 0L

    init {
        scope.launch {
            while (true) {
                _cpuLoad.value = readCpuLoad()
                _gpuLoad.value = readGpuLoad()
                delay(1_000)
            }
        }
    }

    /**
     * Reads CPU utilization from /proc/stat (first "cpu" line).
     * Returns percentage 0–100.
     */
    private fun readCpuLoad(): Float {
        return try {
            val line = BufferedReader(FileReader("/proc/stat")).use { reader ->
                reader.readLine() // first line: "cpu  user nice system idle iowait irq softirq steal ..."
            }
            if (line == null || !line.startsWith("cpu")) return 0f

            val parts = line.trim().split("\\s+".toRegex())
            if (parts.size < 5) return 0f

            // parts[0] = "cpu", parts[1..] = user, nice, system, idle, iowait, irq, softirq, steal, guest, guest_nice
            val values = parts.drop(1).mapNotNull { it.toLongOrNull() }
            if (values.size < 4) return 0f

            val idle = values[3] + (values.getOrNull(4) ?: 0L) // idle + iowait
            val total = values.sum()

            val diffTotal = total - prevTotal
            val diffIdle = idle - prevIdle

            prevTotal = total
            prevIdle = idle

            if (diffTotal <= 0) return 0f
            ((diffTotal - diffIdle).toFloat() / diffTotal * 100f).coerceIn(0f, 100f)
        } catch (e: Exception) {
            Timber.d(e, "Failed to read CPU load")
            0f
        }
    }

    /**
     * Attempts to read GPU load from common sysfs paths.
     * Supports Qualcomm Adreno (kgsl) and ARM Mali.
     * Returns 0–100 or -1 if unavailable.
     */
    private fun readGpuLoad(): Float {
        // Qualcomm Adreno (kgsl) — gpu_busy_percentage
        val adrenoPaths = listOf(
            "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
            "/sys/class/kgsl/kgsl-3d0/devfreq/gpu_load"
        )
        for (path in adrenoPaths) {
            try {
                val content = java.io.File(path).readText().trim()
                // Format may be "85 %" or just "85"
                val num = content.replace("%", "").trim().split("\\s+".toRegex())[0]
                val pct = num.toFloatOrNull()
                if (pct != null) return pct.coerceIn(0f, 100f)
            } catch (_: Exception) { /* not available */ }
        }

        // ARM Mali — utilization
        val maliPaths = listOf(
            "/sys/devices/platform/mali.0/utilization",
            "/sys/devices/platform/gpu/utilization",
            "/sys/kernel/gpu/gpu_busy"
        )
        for (path in maliPaths) {
            try {
                val content = java.io.File(path).readText().trim()
                val num = content.split("\\s+".toRegex())[0]
                val raw = num.toFloatOrNull()
                if (raw != null) {
                    // Mali utilization is 0–256, need to normalize
                    return if (raw > 100f) (raw / 256f * 100f).coerceIn(0f, 100f)
                    else raw.coerceIn(0f, 100f)
                }
            } catch (_: Exception) { /* not available */ }
        }

        return -1f // GPU monitoring not available
    }
}
