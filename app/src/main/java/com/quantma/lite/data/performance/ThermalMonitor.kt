package com.quantma.lite.data.performance

import android.content.Context
import android.os.Build
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitors device thermal status via PowerManager.
 * Polls every 5 seconds on Android 10+ (API 29+).
 * Phase 7 (v1.6.0)
 *
 * Thermal status values (PowerManager.THERMAL_STATUS_*):
 * 0 = NONE (normal), 1 = LIGHT, 2 = MODERATE, 3 = SEVERE, 4 = CRITICAL
 */
@Singleton
class ThermalMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _thermalStatus = MutableStateFlow(0)
    val thermalStatus: StateFlow<Int> = _thermalStatus.asStateFlow()

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            scope.launch {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                while (true) {
                    _thermalStatus.value = powerManager.currentThermalStatus
                    delay(5_000)
                }
            }
        }
    }

    /** Returns a user-readable label for the current thermal status. */
    fun statusLabel(status: Int): String = when (status) {
        0    -> "Normal"
        1    -> "Warm"
        2    -> "Hot"
        3    -> "Very Hot"
        4, 5 -> "Critical"
        else -> "Unknown"
    }
}
