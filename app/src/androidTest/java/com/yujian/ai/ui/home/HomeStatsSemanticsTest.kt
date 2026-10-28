package com.yujian.ai.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.Modifier
import com.yujian.ai.catches.CatchStatistics
import com.yujian.ai.catches.RemoteCatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun unresolvedStatsUseDashesAndExposeNoArchiveNavigation() {
        compose.setContent {
            HomeStats(
                statistics = CatchStatistics(),
                catches = emptyList(),
                onSpeciesClick = {},
                onCatchesClick = {},
                isResolving = true,
            )
        }

        assertEquals(3, compose.onAllNodesWithText("—").fetchSemanticsNodes().size)
        compose.onNodeWithText("鱼种").assertHasNoClickAction()
        compose.onNodeWithText("鱼获").assertHasNoClickAction()
        compose.onNodeWithText("记录天数").assertHasNoClickAction()
    }

    @Test
    fun requiredStatsStayInsideTheirContentSizedRowsAcrossWidthsAndLargeText() {
        val profiles = listOf(
            Triple(320, 640, 1f),
            Triple(320, 640, 1.3f),
            Triple(360, 780, 1f),
            Triple(360, 780, 1.3f),
            Triple(393, 852, 1f),
            Triple(393, 852, 1.3f),
            Triple(411, 891, 1f),
            Triple(411, 891, 1.3f),
        )
        compose.setContent {
            val density = LocalDensity.current.density
            Column(Modifier.verticalScroll(rememberScrollState())) {
                profiles.forEachIndexed { index, (widthDp, _, fontScale) ->
                    val tag = "home-stats-$widthDp-$fontScale"
                    Box(Modifier.width(widthDp.dp).testTag(tag)) {
                        CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                            HomeStats(
                                statistics = CatchStatistics(totalCatches = index + 11, speciesCount = index + 1),
                                catches = emptyList(),
                                onSpeciesClick = {},
                                onCatchesClick = {},
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        profiles.forEachIndexed { index, (widthDp, _, fontScale) ->
            val tag = "home-stats-$widthDp-$fontScale"
            compose.onNodeWithTag(tag).performScrollTo()
            val parentBounds = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            listOf("鱼种", "鱼获", "记录天数", (index + 1).toString(), (index + 11).toString(), "0").forEach { text ->
                val contained = compose.onAllNodesWithText(text).fetchSemanticsNodes().any { node ->
                    val bounds = node.boundsInRoot
                    bounds.left >= parentBounds.left - 1f &&
                        bounds.right <= parentBounds.right + 1f &&
                        bounds.top >= parentBounds.top - 1f &&
                        bounds.bottom <= parentBounds.bottom + 1f
                }
                assertTrue("$text clipped at ${widthDp}dp / fontScale $fontScale", contained)
            }
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
