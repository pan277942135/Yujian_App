package com.yujian.ai.ui.designsystem.lakeglass

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

/** Debug-variant-only entry point for independent optical effect tuning. */
class LakeGlassShaderLabActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialMode = intent.getStringExtra("effect_mode") ?: "static"
        val viewportOnly = intent.getBooleanExtra("viewport_only", false)
        if (viewportOnly) {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
                navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            )
        }
        setContent {
            LakeGlassShaderLabScreen(
                initialMode = initialMode,
                viewportOnly = viewportOnly,
            )
        }
    }
}
