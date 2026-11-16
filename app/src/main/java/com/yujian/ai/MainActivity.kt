package com.yujian.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import com.yujian.ai.diagnostics.StartupCrashReporter
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.yujian.ai.media.TestGallerySeeder
import com.yujian.ai.ui.theme.YujianTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (StartupCrashReporter.hasPendingReport(this)) {
            showStartupRecovery()
            return
        }
        Log.i("MainActivity", "YuJian MainActivity onCreate")
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.navigationBarDividerColor = Color.TRANSPARENT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        lifecycleScope.launch {
            TestGallerySeeder.seedTestImagesIfNeeded(applicationContext)
        }

        setContent {
            YujianTheme {
                YujianApp()
            }
        }
    }
    /** Android Views keep diagnostics usable when the Compose Home itself crashes. */
    private fun showStartupRecovery() {
        val scale = resources.displayMetrics.density
        fun dp(value: Int) = (value * scale).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(32), dp(24), dp(24))
            setBackgroundColor(Color.rgb(235, 246, 242))
        }
        layout.addView(TextView(this).apply {
            text = "启动诊断"
            textSize = 24f
            setTextColor(Color.rgb(18, 63, 72))
        })
        layout.addView(TextView(this).apply {
            text = "上次运行发生异常。请导出日志发回分析；鱼获数据已保留。"
            textSize = 16f
            setPadding(0, dp(16), 0, dp(16))
        })
        val report = StartupCrashReporter.report(this)
        layout.addView(Button(this).apply {
            text = "导出崩溃日志"
            setOnClickListener {
                runCatching {
                    val file = StartupCrashReporter.exportFile(this@MainActivity)
                    val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", file)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        clipData = ClipData.newRawUri("startup_crash.txt", uri)
                    }
                    startActivity(Intent.createChooser(intent, "导出崩溃日志"))
                }.onFailure { Toast.makeText(this@MainActivity, "导出失败，可使用复制日志", Toast.LENGTH_LONG).show() }
            }
        })
        layout.addView(Button(this).apply {
            text = "复制日志"
            setOnClickListener {
                getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("YuJian crash", report))
                Toast.makeText(this@MainActivity, "日志已复制", Toast.LENGTH_SHORT).show()
            }
        })
        layout.addView(Button(this).apply {
            text = "重试打开渔见"
            setOnClickListener { StartupCrashReporter.acknowledge(this@MainActivity); recreate() }
        })
        layout.addView(ScrollView(this).apply {
            addView(TextView(this@MainActivity).apply {
                text = report.take(6000)
                textSize = 12f
                setTextIsSelectable(true)
            })
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(layout)
    }

}
