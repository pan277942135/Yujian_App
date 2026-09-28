package com.yujian.ai.ui.recognition

import android.app.ActivityManager
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Presentation-only degradation policy. It never changes detector/classifier semantics.
 *
 * Reduce Motion follows the system animator scale. Low-performance follows Android's
 * low-RAM device classification. Tests may inject an explicit policy through
 * RecognitionProcessingScene without changing production defaults.
 */
data class RecognitionMotionPolicy(
    val reduceMotion: Boolean = false,
    val lowPerformance: Boolean = false,
)

@Composable
fun rememberRecognitionMotionPolicy(): RecognitionMotionPolicy {
    val context = LocalContext.current
    var reduceMotion by remember(context) { mutableStateOf(readReduceMotion(context)) }
    val lowPerformance = remember(context) {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        manager?.isLowRamDevice == true
    }

    DisposableEffect(context) {
        val resolver = context.contentResolver
        val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduceMotion = readReduceMotion(context)
            }
        }
        runCatching { resolver.registerContentObserver(uri, false, observer) }
        onDispose { runCatching { resolver.unregisterContentObserver(observer) } }
    }

    return remember(reduceMotion, lowPerformance) {
        RecognitionMotionPolicy(
            reduceMotion = reduceMotion,
            lowPerformance = lowPerformance,
        )
    }
}

private fun readReduceMotion(context: Context): Boolean =
    runCatching {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) <= 0f
    }.getOrDefault(false)
