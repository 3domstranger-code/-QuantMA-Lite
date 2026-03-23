package com.quantma.lite

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.quantma.lite.data.local.preferences.SettingsDataStore
import com.quantma.lite.ui.navigation.NavGraph
import com.quantma.lite.ui.theme.QuantMATheme
import com.quantma.lite.ui.theme.CustomThemeColors
import com.quantma.lite.ui.theme.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeModeStr by settingsDataStore.themeMode.collectAsState(initial = "SYSTEM")
            val themeMode = ThemeMode.entries.firstOrNull { it.name == themeModeStr } ?: ThemeMode.SYSTEM

            val customBgUri by settingsDataStore.customBgUri.collectAsState(initial = "")
            val customBgOpacity by settingsDataStore.customBgOpacity.collectAsState(initial = 0.15f)
            val customUserBubble by settingsDataStore.customUserBubble.collectAsState(initial = "")
            val customAssistantBubble by settingsDataStore.customAssistantBubble.collectAsState(initial = "")
            val customAccent by settingsDataStore.customAccent.collectAsState(initial = "")
            val customPanel by settingsDataStore.customPanel.collectAsState(initial = "")

            // Extended colors (v2.11.0)
            val customThermalOk by settingsDataStore.customThermalOk.collectAsState(initial = "")
            val customThermalWarn by settingsDataStore.customThermalWarn.collectAsState(initial = "")
            val customThermalHot by settingsDataStore.customThermalHot.collectAsState(initial = "")
            val customCpuHigh by settingsDataStore.customCpuHigh.collectAsState(initial = "")
            val customGpuBar by settingsDataStore.customGpuBar.collectAsState(initial = "")
            val customBackendBadge by settingsDataStore.customBackendBadge.collectAsState(initial = "")

            val customTheme = CustomThemeColors(
                userBubble = CustomThemeColors.parseHex(customUserBubble),
                assistantBubble = CustomThemeColors.parseHex(customAssistantBubble),
                accent = CustomThemeColors.parseHex(customAccent),
                panel = CustomThemeColors.parseHex(customPanel),
                backgroundUri = customBgUri,
                backgroundOpacity = customBgOpacity,
                thermalOkColor = CustomThemeColors.parseHex(customThermalOk),
                thermalWarnColor = CustomThemeColors.parseHex(customThermalWarn),
                thermalHotColor = CustomThemeColors.parseHex(customThermalHot),
                cpuHighColor = CustomThemeColors.parseHex(customCpuHigh),
                gpuBarColor = CustomThemeColors.parseHex(customGpuBar),
                backendBadgeColor = CustomThemeColors.parseHex(customBackendBadge)
            )

            QuantMATheme(themeMode = themeMode, customTheme = customTheme) {
                val startDest by settingsDataStore.onboardingComplete
                    .map { complete -> if (complete) "chat" else "onboarding" }
                    .collectAsState(initial = "chat")
                NavGraph(startDestination = startDest)
            }
        }
    }
}
