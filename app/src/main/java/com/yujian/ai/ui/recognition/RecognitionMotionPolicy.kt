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

enum class RecognitionQualityLevel {
    FULL,
    BALANCED,
    LITE,
}

enum class RecognitionDegradationLevel {
    D0,
    D1,
    D2,
    D3,
    D4,
}

enum class RecognitionFishFocusLevel {
    A,
    B,
    C,
}

/**
 * Presentation-only adaptation policy. It never changes detector, crop,
 * classifier, confidence, or result semantics.
 *
 * lowPerformance is kept as a compatibility input for tests/callers. When true
 * and no explicit degradation level is supplied it maps to D2: LITE Edge Field
 * + Fish Focus A, preserving the frozen rule that ambient detail degrades
 * before semantic fish focus.
 */
data class RecognitionMotionPolicy(
    val reduceMotion: Boolean = false,
    val lowPerformance: Boolean = false,
    val degradationLevel: RecognitionDegradationLevel =
        if (lowPerformance) RecognitionDegradationLevel.D2 else RecognitionDegradationLevel.D0,
) {
    val qualityLevel: RecognitionQualityLevel
        get() = when (degradationLevel) {
            RecognitionDegradationLevel.D0 -> RecognitionQualityLevel.FULL
            RecognitionDegradationLevel.D1 -> RecognitionQualityLevel.BALANCED
            RecognitionDegradationLevel.D2,
            RecognitionDegradationLevel.D3,
            RecognitionDegradationLevel.D4 -> RecognitionQualityLevel.LITE
        }

    val fishFocusLevel: RecognitionFishFocusLevel
        get() = when (degradationLevel) {
            RecognitionDegradationLevel.D0,
            RecognitionDegradationLevel.D1,
            RecognitionDegradationLevel.D2 -> RecognitionFishFocusLevel.A
            RecognitionDegradationLevel.D3 -> RecognitionFishFocusLevel.B
            RecognitionDegradationLevel.D4 -> RecognitionFishFocusLevel.C
        }
}

@Composable
fun rememberRecognitionMotionPolicy(): RecognitionMotionPolicy {
    val context = LocalContext.current
    var reduceMotion by remember(context) { mutableStateOf(readReduceMotion(context)) }
    val lowPerformance = remember(context) {
        val manager =
            context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
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
        onDispose {
            runCatching { resolver.unregisterContentObserver(observer) }
        }
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
