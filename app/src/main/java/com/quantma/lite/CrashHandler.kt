package com.quantma.lite

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CrashHandler(
    private val context: Context
) : Thread.UncaughtExceptionHandler {

    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val crashDir = context.getExternalFilesDir("crashes")
            if (crashDir != null) {
                if (!crashDir.exists()) crashDir.mkdirs()
                val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
                val file = File(crashDir, "crash_$timestamp.txt")
                file.writeText(buildReport(thread, throwable))
            }
        } catch (_: Exception) {
            // Don't throw from crash handler
        }
        defaultHandler?.uncaughtException(thread, throwable)
    }

    private fun buildReport(thread: Thread, throwable: Throwable): String = buildString {
        appendLine("=== QuantMA Crash Report ===")
        appendLine("Time: ${Date()}")
        appendLine("Thread: ${thread.name}")
        appendLine()
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine()
        appendLine("=== Stack Trace ===")
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        appendLine(sw.toString())
    }
}
