package com.yujian.ai.ui.designsystem

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import com.yujian.ai.ui.theme.YujianTheme

/** Debug-only route used to inspect and capture the design-system gallery. */
class DesignSystemPreviewActivity : ComponentActivity() {
    private var section by mutableStateOf(DesignSystemPreviewSection.All)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        section = DesignSystemPreviewSection.fromArgument(intent.getStringExtra(EXTRA_SECTION))
        setContent {
            YujianTheme { DesignSystemPreview(section = section) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        section = DesignSystemPreviewSection.fromArgument(intent.getStringExtra(EXTRA_SECTION))
    }

    companion object {
        const val EXTRA_SECTION = "section"
    }
}
