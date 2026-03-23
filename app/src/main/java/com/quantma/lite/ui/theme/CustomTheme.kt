package com.quantma.lite.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * User-customizable colors that override parts of the Material3 theme.
 * Empty hex string = use default theme color.
 * Phase 12
 */
data class CustomThemeColors(
    val userBubble: Color? = null,
    val assistantBubble: Color? = null,
    val accent: Color? = null,
    val panel: Color? = null,
    val backgroundUri: String = "",
    val backgroundOpacity: Float = 0.15f
) {
    companion object {
        val Default = CustomThemeColors()

        data class ColorPreset(
            val name: String,
            val userBubble: String,
            val assistantBubble: String,
            val accent: String,
            val panel: String
        )

        val PRESETS = listOf(
            ColorPreset("Default", "", "", "", ""),
            ColorPreset("Dracula", "#FF6272A4", "#FF44475A", "#FFBD93F9", "#FF282A36"),
            ColorPreset("Monokai", "#FF75715E", "#FF3E3D32", "#FFA6E22E", "#FF272822"),
            ColorPreset("Nord", "#FF5E81AC", "#FF3B4252", "#FF88C0D0", "#FF2E3440")
        )

        fun parseHex(hex: String): Color? {
            if (hex.isBlank()) return null
            return try {
                val clean = hex.removePrefix("#")
                when (clean.length) {
                    6 -> Color(("FF$clean").toLong(16))
                    8 -> Color(clean.toLong(16))
                    else -> null
                }
            } catch (_: Exception) {
                null
            }
        }

        fun toHex(color: Color): String {
            val a = (color.alpha * 255).toInt()
            val r = (color.red * 255).toInt()
            val g = (color.green * 255).toInt()
            val b = (color.blue * 255).toInt()
            return "#%02X%02X%02X%02X".format(a, r, g, b)
        }
    }
}

val LocalCustomTheme = compositionLocalOf { CustomThemeColors.Default }
