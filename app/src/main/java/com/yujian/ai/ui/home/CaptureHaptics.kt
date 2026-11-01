package com.yujian.ai.ui.home

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View

internal fun captureClickHaptic(context: Context, view: View) {
    if (Settings.System.getInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 0) return
    val vibrator = if (Build.VERSION.SDK_INT >= 31) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    if (vibrator?.hasVibrator() == true && Build.VERSION.SDK_INT >= 26) {
        vibrator.vibrate(VibrationEffect.createOneShot(14, 110))
    } else {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }
}

/**
 * One subtle double-pulse at the start of an actual periodic bobber/ripple event.
 * The caller must gate this on the resumed lifecycle and enabled motion state.
 */
internal fun bobberNibbleHaptic(context: Context, view: View) {
    if (Settings.System.getInt(
            context.contentResolver,
            Settings.System.HAPTIC_FEEDBACK_ENABLED,
            1,
        ) == 0
    ) return

    val vibrator = if (Build.VERSION.SDK_INT >= 31) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    if (vibrator?.hasVibrator() == true && Build.VERSION.SDK_INT >= 26) {
        try {
            if (vibrator.hasAmplitudeControl()) {
                vibrator.vibrate(
                    VibrationEffect.createWaveform(
                        longArrayOf(0, 14, 45, 18),
                        intArrayOf(0, 90, 0, 130),
                        -1,
                    ),
                )
            } else {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE),
                )
            }
        } catch (_: RuntimeException) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    } else {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
}
