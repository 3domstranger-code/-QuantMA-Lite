package com.quantma.lite

import android.app.Application
import com.quantma.lite.ui.editor.TextMateInit
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class QuantMAApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))
        TextMateInit.init(this)
    }
}
