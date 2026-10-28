package com.yujian.ai.ui.recorddetail

enum class FishMemoryActionsArrangement {
    ROW,
    STACKED,
}

object FishMemoryActionLayout {
    private const val BASE_ROW_MIN_WIDTH_DP = 280f

    fun resolve(availableWidthDp: Float, fontScale: Float): FishMemoryActionsArrangement {
        val scaledMinimum = BASE_ROW_MIN_WIDTH_DP * fontScale.coerceAtLeast(1f)
        return if (availableWidthDp >= scaledMinimum) {
            FishMemoryActionsArrangement.ROW
        } else {
            FishMemoryActionsArrangement.STACKED
        }
    }
}

object FishMemoryEmptyStateCopy {
    const val title = "鱼获记忆"
    const val hint = "还没有留下影像"
    const val titleLine = "留下这次鱼获的画面"
    const val helper = "照片和视频，会让这一刻更完整。"
    const val addMedia = "添加照片/视频"
    const val continuePhoto = "继续拍照"
    const val recordVideo = "录制视频"

    val actionLabels: List<String>
        get() = listOf(addMedia, continuePhoto, recordVideo)
}
