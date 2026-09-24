package com.yujian.ai.ui.designsystem.haptic

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/** Shared Android haptic vocabulary for Core UI V1 interactions. */
object YuJianHaptic {
    enum class Feedback {
        Light,
        Medium,
        Success,
    }

    internal fun perform(view: View, feedback: Feedback) {
        val constant = when (feedback) {
            Feedback.Light -> HapticFeedbackConstants.KEYBOARD_TAP
            Feedback.Medium -> HapticFeedbackConstants.CONTEXT_CLICK
            Feedback.Success -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.LONG_PRESS
            }
        }
        view.performHapticFeedback(constant)
    }
}

@Stable
class YuJianHapticController internal constructor(
    private val view: View,
) {
    fun perform(feedback: YuJianHaptic.Feedback) = YuJianHaptic.perform(view, feedback)

    fun performCardClick() = perform(YuJianHaptic.Feedback.Medium)
}

@Composable
fun rememberYuJianHaptic(): YuJianHapticController {
    val view = LocalView.current
    return remember(view) { YuJianHapticController(view) }
}
