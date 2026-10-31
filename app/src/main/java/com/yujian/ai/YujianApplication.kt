package com.yujian.ai

import android.app.Application
import com.yujian.ai.diagnostics.StartupCrashReporter

class YujianApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        StartupCrashReporter.install(this)
    }
}
