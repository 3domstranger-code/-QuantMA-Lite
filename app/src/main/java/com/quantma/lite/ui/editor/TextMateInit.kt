package com.quantma.lite.ui.editor

import android.content.Context
import timber.log.Timber
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import org.eclipse.tm4e.core.registry.IThemeSource

object TextMateInit {

    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        try {
            // Register asset file provider
            FileProviderRegistry.getInstance().addFileProvider(
                AssetsFileResolver(context.assets)
            )

            // Load themes
            loadThemes()

            // Load grammars
            GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")

            initialized = true
            Timber.i("TextMate initialized successfully")
        } catch (e: Exception) {
            Timber.e(e, "TextMate initialization failed")
        }
    }

    private fun loadThemes() {
        val themeRegistry = ThemeRegistry.getInstance()

        // Dark theme (Darcula)
        try {
            val darkPath = "textmate/darcula.json"
            themeRegistry.loadTheme(
                ThemeModel(
                    IThemeSource.fromInputStream(
                        FileProviderRegistry.getInstance().tryGetInputStream(darkPath),
                        darkPath,
                        null
                    ),
                    "darcula"
                ).apply { isDark = true }
            )
        } catch (e: Exception) {
            Timber.w(e, "Failed to load darcula theme")
        }

        // Light theme (QuietLight)
        try {
            val lightPath = "textmate/quietlight.json"
            themeRegistry.loadTheme(
                ThemeModel(
                    IThemeSource.fromInputStream(
                        FileProviderRegistry.getInstance().tryGetInputStream(lightPath),
                        lightPath,
                        null
                    ),
                    "quietlight"
                ).apply { isDark = false }
            )
        } catch (e: Exception) {
            Timber.w(e, "Failed to load quietlight theme")
        }

        // Set dark as default (matches Material dark theme)
        themeRegistry.setTheme("darcula")
    }
}
