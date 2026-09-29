package com.yujian.ai

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.yujian.ai.ui.auth.RegisterV2Screen
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RegisterV2RuntimeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun idle_hasSharedAuthStructure() {
        composeRule.setContent {
            RegisterV2Screen(
                loading = false,
                error = null,
                onRegister = { _, _, _ -> },
                onBackToLogin = {},
            )
        }

        composeRule.onNodeWithText("渔见").assertExists()
        composeRule.onNodeWithText("拍照收藏每次渔获").assertExists()
        composeRule.onNodeWithText("创建账号").assertExists()
        composeRule.onNodeWithTag("register_username").assertExists()
        composeRule.onNodeWithTag("register_password").assertExists()
        composeRule.onNodeWithTag("register_nickname").assertExists()
        composeRule.onNodeWithText("注册并登录").assertExists()
        composeRule.onNodeWithText("去登录").assertExists()
        saveScreenshot("register_v2_idle.png")
    }

    @Test
    fun validForm_isActionable() {
        var username = ""
        var password = ""
        var nickname = ""

        composeRule.setContent {
            RegisterV2Screen(
                loading = false,
                error = null,
                onRegister = { user, pass, name ->
                    username = user
                    password = pass
                    nickname = name
                },
                onBackToLogin = {},
            )
        }

        composeRule.onNodeWithTag("register_username").performTextInput("fisher001")
        composeRule.onNodeWithTag("register_password").performTextInput("123456")
        composeRule.onNodeWithTag("register_nickname").performTextInput("小渔")
        composeRule.onNodeWithTag("register_submit").assertIsEnabled()
        saveScreenshot("register_v2_filled.png")
        composeRule.onNodeWithTag("register_submit").performClick()

        composeRule.runOnIdle {
            assertEquals("fisher001", username)
            assertEquals("123456", password)
            assertEquals("小渔", nickname)
        }
    }

    @Test
    fun passwordVisibility_isUserControlled() {
        composeRule.setContent {
            RegisterV2Screen(
                loading = false,
                error = null,
                onRegister = { _, _, _ -> },
                onBackToLogin = {},
            )
        }

        composeRule.onNodeWithTag("register_password").performTextInput("123456")
        composeRule.onNodeWithContentDescription("显示密码").performClick()
        saveScreenshot("register_v2_password_visible.png")
    }

    @Test
    fun error_isVisible() {
        composeRule.setContent {
            RegisterV2Screen(
                loading = false,
                error = "账号已存在",
                onRegister = { _, _, _ -> },
                onBackToLogin = {},
            )
        }

        composeRule.onNodeWithText("账号已存在").assertExists()
        saveScreenshot("register_v2_error.png")
    }

    private fun saveScreenshot(name: String) {
        composeRule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val root = File(instrumentation.targetContext.getExternalFilesDir(null), "register_v2")
        check(root.exists() || root.mkdirs())
        FileOutputStream(File(root, name)).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        bitmap.recycle()
    }
}
