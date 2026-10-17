package com.yujian.ai.ui.identify

import android.content.Context

internal class RecognitionFirstCaptureGuideStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun isCompleted(): Boolean = preferences.getBoolean(COMPLETED_KEY, false)

    fun markCompleted(): Boolean = preferences.edit().putBoolean(COMPLETED_KEY, true).commit()

    companion object {
        internal const val PREFERENCES_NAME = "yujian_capture_guide_v1"
        internal const val COMPLETED_KEY = "capture_guide_v1_completed"
    }
}
