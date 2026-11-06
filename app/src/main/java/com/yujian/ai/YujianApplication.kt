package com.yujian.ai

import android.app.Application
import android.content.ComponentCallbacks2
import com.yujian.ai.diagnostics.StartupCrashReporter
import com.yujian.ai.ui.designsystem.lakeglass.LakeGlassAssetRepository
import com.yujian.ai.network.DebugHttpTrust

class YujianApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DebugHttpTrust.installIfDebug()
        StartupCrashReporter.install(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            LakeGlassAssetRepository.release()
        }
    }
}
