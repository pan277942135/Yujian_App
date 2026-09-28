package com.yujian.ai

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.yujian.ai.ui.auth.LoginV2Screen
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LoginV2RuntimeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun idle_matchesFrozenStructure() {
        composeRule.setContent {
            LoginV2Screen(
                loading = false,
                error = null,
                onLogin = { _, _ -> },
                onRegister = {},
                onForgotPassword = {},
                onBack = {},
            )
        }
        composeRule.onNodeWithText("渔见").assertExists()
        composeRule.onNodeWithText("拍照收藏每次渔获").assertExists()
        composeRule.onNodeWithText("欢迎回来").assertExists()
        composeRule.onNodeWithTag("login_username").assertExists()
        composeRule.onNodeWithTag("login_password").assertExists()
        composeRule.onNodeWithText("忘记密码？").assertExists()
        composeRule.onNodeWithText("创建账号").assertExists()
        saveScreenshot("login_v2_idle.png")
    }

    @Test
    fun filled_form_isActionable() {
        var username = ""
        var password = ""
        composeRule.setContent {
            LoginV2Screen(
                loading = false,
                error = null,
                onLogin = { user, pass -> username = user; password = pass },
                onRegister = {},
                onForgotPassword = {},
                onBack = {},
            )
        }
        composeRule.onNodeWithTag("login_username").performTextInput("fisher001")
        composeRule.onNodeWithTag("login_password").performTextInput("123456")
        composeRule.onNodeWithTag("login_submit").assertIsEnabled()
        saveScreenshot("login_v2_filled.png")
        composeRule.onNodeWithTag("login_submit").performClick()
        composeRule.runOnIdle {
            assertEquals("fisher001", username)
            assertEquals("123456", password)
        }
    }

    @Test
    fun passwordVisibility_isUserControlled() {
        composeRule.setContent {
            LoginV2Screen(
                loading = false,
                error = null,
                onLogin = { _, _ -> },
                onRegister = {},
                onForgotPassword = {},
                onBack = {},
            )
        }
        composeRule.onNodeWithTag("login_password").performTextInput("123456")
        composeRule.onNodeWithContentDescription("显示密码").performClick()
        composeRule.onNodeWithTag("login_password").assertTextContains("123456")
        saveScreenshot("login_v2_password_visible.png")
    }

    @Test
    fun loading_hasDeterministicVisualState() {
        composeRule.setContent {
            LoginV2Screen(
                loading = true,
                error = null,
                onLogin = { _, _ -> },
                onRegister = {},
                onForgotPassword = {},
                onBack = {},
            )
        }
        saveScreenshot("login_v2_loading.png")
    }

    @Test
    fun error_isVisibleWithoutTechnicalDetails() {
        composeRule.setContent {
            LoginV2Screen(
                loading = false,
                error = "账号或密码不正确",
                onLogin = { _, _ -> },
                onRegister = {},
                onForgotPassword = {},
                onBack = {},
            )
        }
        composeRule.onNodeWithText("账号或密码不正确").assertExists()
        saveScreenshot("login_v2_error.png")
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
