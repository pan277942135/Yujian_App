package com.yujian.ai.ui.designsystem

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.yujian.ai.ui.designsystem.components.YuJianBackTitleTopBar
import com.yujian.ai.ui.designsystem.components.YuJianCaptureButton
import com.yujian.ai.ui.designsystem.components.YuJianGlassCard
import com.yujian.ai.ui.designsystem.components.YuJianHeroCard
import com.yujian.ai.ui.designsystem.components.YuJianHeroVariant
import com.yujian.ai.ui.designsystem.components.YuJianIconAction
import com.yujian.ai.ui.designsystem.components.YuJianPrimaryButton
import com.yujian.ai.ui.designsystem.components.YuJianTextAction
import com.yujian.ai.ui.designsystem.glass.YuJianGlassLevel
import com.yujian.ai.ui.theme.YujianTheme
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DesignSystemComponentPreviewTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun sharedActionsExposeDisabledLoadingRolesAndFrozenTargets() {
        composeRule.setContent {
            YujianTheme {
                Column {
                    YuJianPrimaryButton(text = "禁用按钮", onClick = {}, enabled = false)
                    YuJianPrimaryButton(text = "正在保存", onClick = {}, loading = true)
                    YuJianIconAction(
                        icon = Icons.Rounded.ArrowBack,
                        contentDescription = "返回",
                        onClick = {},
                    )
                    YuJianTextAction(text = "取消", onClick = {})
                    YuJianCaptureButton(onClick = {})
                    YuJianGlassCard(onClick = {}) { Text("可操作信息卡") }
                }
            }
        }

        composeRule.onNodeWithText("禁用按钮").assertIsNotEnabled()
        composeRule.onNodeWithText("正在保存").assertIsNotEnabled()
        val loadingNode = composeRule.onNodeWithText("正在保存").fetchSemanticsNode()
        assertEquals("正在加载", loadingNode.config[SemanticsProperties.StateDescription])
        val backNode = composeRule.onNodeWithContentDescription("返回").fetchSemanticsNode()
        assertEquals(Role.Button, backNode.config[SemanticsProperties.Role])
        composeRule.onNodeWithContentDescription("返回")
            .assertWidthIsAtLeast(44.dp)
        composeRule.onNodeWithText("取消").assertWidthIsAtLeast(44.dp)
        composeRule.onNodeWithContentDescription("开始识鱼").assertWidthIsAtLeast(64.dp)
        val cardNode = composeRule.onNodeWithText("可操作信息卡").fetchSemanticsNode()
        assertEquals(Role.Button, cardNode.config[SemanticsProperties.Role])
        saveScreenshot("shared_components_accessibility.png")
    }

    @Test
    fun backTitleTopBarFitsCompactWidthAtLargeFontScale() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 1.6f)) {
                YujianTheme {
                    Box(Modifier.width(320.dp)) {
                        YuJianBackTitleTopBar(
                            title = "特别长的鱼获详情标题用于验证省略行为",
                            onBack = {},
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText("特别长的鱼获详情标题用于验证省略行为").assertExists()
        composeRule.onNodeWithContentDescription("返回").assertWidthIsAtLeast(44.dp)
        saveScreenshot("shared_components_compact_large_font.png")
    }

    @Test
    fun glassCardAndHeroCardRender() {
        composeRule.setContent {
            YujianTheme {
                Column {
                    YuJianGlassCard(level = YuJianGlassLevel.Medium) { Text("Glass Card Preview") }
                    YuJianHeroCard(
                        title = "草鱼",
                        metadata = listOf("42.8 cm", "千岛湖"),
                        variant = YuJianHeroVariant.HOME,
                    )
                }
            }
        }

        composeRule.onNodeWithText("Glass Card Preview").assertExists()
        composeRule.onNodeWithText("草鱼").assertExists()
    }

    private fun saveScreenshot(name: String) {
        composeRule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val root = File(instrumentation.targetContext.getExternalFilesDir(null), "login_v2")
        check(root.exists() || root.mkdirs())
        FileOutputStream(File(root, name)).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        bitmap.recycle()
    }
}
