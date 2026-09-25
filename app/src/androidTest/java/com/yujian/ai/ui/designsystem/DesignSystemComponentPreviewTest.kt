package com.yujian.ai.ui.designsystem

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.yujian.ai.ui.designsystem.components.YuJianCaptureButton
import com.yujian.ai.ui.designsystem.components.YuJianGlassCard
import com.yujian.ai.ui.designsystem.components.YuJianHeroCard
import com.yujian.ai.ui.designsystem.components.YuJianHeroVariant
import com.yujian.ai.ui.theme.YujianTheme
import org.junit.Rule
import org.junit.Test

class DesignSystemComponentPreviewTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun cameraButtonRenders() {
        composeRule.setContent {
            YujianTheme { YuJianCaptureButton(onClick = {}) }
        }

        composeRule.onNodeWithContentDescription("开始识鱼").assertExists()
    }

    @Test
    fun glassCardRenders() {
        composeRule.setContent {
            YujianTheme {
                YuJianGlassCard { Text("Glass Card Preview") }
            }
        }

        composeRule.onNodeWithText("Glass Card Preview").assertExists()
    }

    @Test
    fun heroCardRenders() {
        composeRule.setContent {
            YujianTheme {
                YuJianHeroCard(
                    title = "草鱼",
                    metadata = listOf("42.8 cm", "千岛湖"),
                    variant = YuJianHeroVariant.HOME,
                )
            }
        }

        composeRule.onNodeWithText("草鱼").assertExists()
    }
}
