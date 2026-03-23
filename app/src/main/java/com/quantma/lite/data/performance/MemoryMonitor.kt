package com.quantma.lite.data.performance

import android.app.ActivityManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Queries device RAM via ActivityManager.
 * Used for pre-load warnings and ModelStatusIndicator display.
 * Phase 7 (v1.6.0)
 */
@Singleton
class MemoryMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val activityManager =
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    private fun memoryInfo(): ActivityManager.MemoryInfo {
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)
        return info
    }

    /** Available RAM in megabytes. */
    fun getAvailableRamMb(): Long = memoryInfo().availMem / (1024L * 1024L)

    /** Total RAM in megabytes. */
    fun getTotalRamMb(): Long = memoryInfo().totalMem / (1024L * 1024L)

    /** True if the system considers memory critically low. */
    fun isLowMemory(): Boolean = memoryInfo().lowMemory

    /**
     * Returns true if loading a model of [modelSizeMb] MB is risky
     * (model requires > 80 % of currently available RAM).
     */
    fun isModelLoadRisky(modelSizeMb: Long): Boolean =
        modelSizeMb > getAvailableRamMb() * 0.8
}
