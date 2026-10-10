package com.yujian.ai.ui.recorddetail

import org.junit.Assert.assertEquals
import org.junit.Test

class FishMemoryActionLayoutTest {
    @Test
    fun standardDetailWidthKeepsActionsInOneRowAndLargerTextStacksWhenNeeded() {
        assertEquals(
            FishMemoryActionsArrangement.ROW,
            FishMemoryActionLayout.resolve(288f, fontScale = 1f),
        )
        assertEquals(
            FishMemoryActionsArrangement.STACKED,
            FishMemoryActionLayout.resolve(288f, fontScale = 1.15f),
        )
        assertEquals(
            FishMemoryActionsArrangement.STACKED,
            FishMemoryActionLayout.resolve(331f, fontScale = 1.3f),
        )
    }

    @Test
    fun actionsReturnToOneRowWhenAvailableWidthFitsScaledLabels() {
        assertEquals(
            FishMemoryActionsArrangement.ROW,
            FishMemoryActionLayout.resolve(322f, fontScale = 1.15f),
        )
    }

    @Test
    fun noUploadedMemoryCopyAndActionOrderStayFrozen() {
        assertEquals("鱼获记忆", FishMemoryEmptyStateCopy.title)
        assertEquals("还没有留下影像", FishMemoryEmptyStateCopy.hint)
        assertEquals("留下这次鱼获的画面", FishMemoryEmptyStateCopy.titleLine)
        assertEquals("照片和视频，会让这一刻更完整。", FishMemoryEmptyStateCopy.helper)
        assertEquals(listOf("添加照片/视频", "继续拍照", "录制视频"), FishMemoryEmptyStateCopy.actionLabels)
    }
}
