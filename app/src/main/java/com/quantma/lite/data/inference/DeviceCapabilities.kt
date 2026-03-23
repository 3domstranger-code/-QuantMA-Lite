package com.quantma.lite.data.inference

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import timber.log.Timber
import java.io.File

/**
 * Detects device hardware capabilities for smart auto-configuration.
 *
 * Reads CPU topology, RAM, and GPU info to determine optimal inference parameters.
 */
object DeviceCapabilities {

    /** CPU core info parsed from /proc/cpuinfo and sysfs */
    data class CpuInfo(
        val totalCores: Int,
        val bigCores: Int,           // Performance cores (high freq)
        val littleCores: Int,        // Efficiency cores (low freq)
        val maxFreqMhz: Int,         // Max freq across all cores
        val hasDotProd: Boolean,     // ARM dot product instructions
        val hasSve: Boolean,         // ARM SVE (Scalable Vector Extension)
        val hasI8mm: Boolean         // ARM i8mm (Int8 matrix multiply)
    )

    data class MemoryInfo(
        val totalRamMb: Long,
        val availableRamMb: Long
    )

    data class DeviceProfile(
        val cpu: CpuInfo,
        val memory: MemoryInfo,
        val gpuBackend: String,      // from LlamaJni.nativeGetGpuInfo()
        val socName: String,         // e.g. "Snapdragon 8 Gen 3", "Tensor G4"
        val androidSdk: Int
    )

    /**
     * Read CPU core frequencies from sysfs to classify big/little cores.
     */
    fun detectCpu(): CpuInfo {
        val totalCores = Runtime.getRuntime().availableProcessors()
        val frequencies = mutableListOf<Int>()

        // Read max freq for each core from sysfs
        for (i in 0 until totalCores) {
            val freqFile = File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")
            val freq = try {
                if (freqFile.exists()) freqFile.readText().trim().toIntOrNull()?.div(1000) ?: 0
                else 0
            } catch (_: Exception) { 0 }
            frequencies.add(freq)
        }

        // Classify: cores above median frequency are "big"
        val maxFreq = frequencies.maxOrNull() ?: 0
        val threshold = if (maxFreq > 0) (maxFreq * 0.7).toInt() else 0
        val bigCores = frequencies.count { it >= threshold }
        val littleCores = totalCores - bigCores

        // Check CPU features from /proc/cpuinfo
        val cpuinfoFeatures = try {
            File("/proc/cpuinfo").readText()
        } catch (_: Exception) { "" }

        val hasDotProd = cpuinfoFeatures.contains("asimddp") || cpuinfoFeatures.contains("dotprod")
        val hasSve = cpuinfoFeatures.contains("sve")
        val hasI8mm = cpuinfoFeatures.contains("i8mm")

        Timber.i("CPU: $totalCores cores ($bigCores big + $littleCores little), maxFreq=${maxFreq}MHz, " +
                "dotprod=$hasDotProd, sve=$hasSve, i8mm=$hasI8mm")

        return CpuInfo(
            totalCores = totalCores,
            bigCores = bigCores,
            littleCores = littleCores,
            maxFreqMhz = maxFreq,
            hasDotProd = hasDotProd,
            hasSve = hasSve,
            hasI8mm = hasI8mm
        )
    }

    /**
     * Get total and available RAM from ActivityManager.
     */
    fun detectMemory(context: Context): MemoryInfo {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)

        val totalMb = memInfo.totalMem / (1024 * 1024)
        val availMb = memInfo.availMem / (1024 * 1024)

        Timber.i("Memory: total=${totalMb}MB, available=${availMb}MB")
        return MemoryInfo(totalRamMb = totalMb, availableRamMb = availMb)
    }

    /**
     * Attempt to detect SoC name from Build properties.
     */
    fun detectSocName(): String {
        val board = Build.BOARD ?: ""
        val hardware = Build.HARDWARE ?: ""
        val soc = Build.SOC_MODEL ?: ""
        val manufacturer = Build.SOC_MANUFACTURER ?: ""

        val name = when {
            soc.isNotEmpty() && manufacturer.isNotEmpty() -> "$manufacturer $soc"
            soc.isNotEmpty() -> soc
            hardware.isNotEmpty() -> hardware
            else -> board
        }
        Timber.i("SoC: $name (board=$board, hardware=$hardware)")
        return name
    }

    /**
     * Build full device profile. Call after LlamaJni.nativeInit().
     */
    fun detectProfile(context: Context, gpuBackend: String = "CPU only"): DeviceProfile {
        return DeviceProfile(
            cpu = detectCpu(),
            memory = detectMemory(context),
            gpuBackend = gpuBackend,
            socName = detectSocName(),
            androidSdk = Build.VERSION.SDK_INT
        )
    }

    /**
     * Get model file size in MB for auto-config decisions.
     */
    fun getModelSizeMb(modelPath: String): Long {
        return try {
            File(modelPath).length() / (1024 * 1024)
        } catch (_: Exception) { 0L }
    }
}
