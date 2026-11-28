package com.yujian.ai

import android.app.Application
import android.content.ComponentCallbacks2
import android.os.SystemClock
import com.yujian.ai.ai.subject.SlimSamRuntimeManager
import com.yujian.ai.diagnostics.StartupCrashReporter
import com.yujian.ai.ui.designsystem.lakeglass.releaseLakeGlassAssetCache
import com.yujian.ai.network.DebugHttpTrust

class YujianApplication : Application() {
    lateinit var slimSamRuntimeManager: SlimSamRuntimeManager
        private set

    override fun onCreate() {
        val appCreateStartedAtNs = SystemClock.elapsedRealtimeNanos()
        super.onCreate()
        DebugHttpTrust.installIfDebug()
        StartupCrashReporter.install(this)
        slimSamRuntimeManager = SlimSamRuntimeManager(this)
        slimSamRuntimeManager.startPreload(appCreateStartedAtNs)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            releaseLakeGlassAssetCache()
        }
        if (::slimSamRuntimeManager.isInitialized) slimSamRuntimeManager.onTrimMemory(level)
    }
}
