package com.yujian.ai.ui.designsystem.lakeglass

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat

/** Debug-variant-only entry point for independent optical effect tuning. */
class LakeGlassShaderLabActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialMode = intent.getStringExtra("effect_mode") ?: "static"
        val viewportOnly = intent.getBooleanExtra("viewport_only", false)
        if (viewportOnly) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
        }
        setContent {
            LakeGlassShaderLabScreen(
                initialMode = initialMode,
                viewportOnly = viewportOnly,
            )
        }
    }
}
