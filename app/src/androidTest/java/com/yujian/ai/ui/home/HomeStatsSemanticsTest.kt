package com.yujian.ai.ui.home

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.yujian.ai.catches.CatchStatistics
import com.yujian.ai.catches.RemoteCatch
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeStatsSemanticsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun recordDaysHasNoClickSemanticsWhileSpeciesAndCatchStatsNavigate() {
        var speciesClicks = 0
        var catchClicks = 0
        compose.setContent {
            HomeStats(
                statistics = CatchStatistics(totalCatches = 3, speciesCount = 2),
                catches = listOf(record("one"), record("two")),
                onSpeciesClick = { speciesClicks++ },
                onCatchesClick = { catchClicks++ },
            )
        }

        compose.onNodeWithText("记录天数").assertHasNoClickAction()
        compose.onNodeWithText("鱼种").assertHasClickAction().performClick()
        compose.onNodeWithText("鱼获").assertHasClickAction().performClick()
        compose.runOnIdle {
            assertEquals(1, speciesClicks)
            assertEquals(1, catchClicks)
        }
    }

    private fun record(id: String) = RemoteCatch(
        id = id,
        imageUrl = "/$id.jpg",
        speciesId = "fish-$id",
        speciesName = "草鱼",
        confidence = 0.9f,
        modelVersion = "test",
        capturedAt = "2026-09-25T18:20:00+08:00",
        createdAt = "2026-09-25T18:20:00+08:00",
    )
}
