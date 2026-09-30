package com.yujian.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.yujian.ai.auth.AuthRepository
import com.yujian.ai.session.UserSession
import com.yujian.ai.ui.screens.AvatarCropSourceRect
import com.yujian.ai.ui.screens.ChangePasswordScreen
import com.yujian.ai.ui.screens.EditProfileScreen
import com.yujian.ai.ui.screens.deletePendingAvatar
import com.yujian.ai.ui.screens.writeAvatarCrop
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AccountPrivacyRuntimeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val profile = UserSession(
        accessToken = "runtime-test-token",
        userId = "account-1",
        username = "angler_2025",
        nickname = "渔见",
        avatarUrl = null,
    )

    @Test
    fun editProfileShowsReadOnlyAccountAndValidatesUnicodeNickname() {
        composeRule.setContent {
            EditProfileScreen(
                profile = profile,
                authRepository = AuthRepository(),
                onProfileUpdated = {},
                onAuthenticationExpired = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithText("编辑资料").assertIsDisplayed()
        composeRule.onNodeWithText("账号").assertIsDisplayed()
        composeRule.onNodeWithText("angler_2025").assertIsDisplayed()
        composeRule.onNodeWithText("不可修改").assertIsDisplayed()
        composeRule.onNodeWithTag("edit_profile_save").assertIsNotEnabled()
        saveScreenshot("account_profile_idle.png")

        composeRule.onNodeWithTag("edit_profile_nickname").performTextClearance()
        composeRule.onNodeWithTag("edit_profile_nickname").performTextInput("🙂".repeat(21))
        composeRule.onNodeWithText("21/20").assertExists()
        composeRule.onNodeWithText("昵称不能超过 20 个字符").assertExists()
        composeRule.onNodeWithTag("edit_profile_save").assertIsNotEnabled()
        saveScreenshot("account_profile_invalid_nickname.png")

        composeRule.onNodeWithTag("edit_profile_nickname").performTextClearance()
        composeRule.onNodeWithTag("edit_profile_nickname").performTextInput("新昵称")
        composeRule.onNodeWithTag("edit_profile_save").assertIsEnabled()
        composeRule.onNodeWithText("更换头像").performClick()
        composeRule.onNodeWithText("拍照").assertIsDisplayed()
        composeRule.onNodeWithText("从相册选择").assertIsDisplayed()
        composeRule.onNodeWithText("取消").assertIsDisplayed()
        saveScreenshot("account_avatar_source_sheet.png")
        composeRule.onNodeWithText("取消").performClick()
        composeRule.onNodeWithText("头像预览已暂存，尚未上传").assertDoesNotExist()
    }

    @Test
    fun changePasswordVisibilityIsExplicitAndAccessible() {
        composeRule.setContent {
            ChangePasswordScreen(
                authRepository = AuthRepository(),
                accessToken = profile.accessToken,
                onAuthenticationExpired = {},
                onBack = {},
            )
        }
        composeRule.onNodeWithTag("change_password_current").performTextInput("secret123")
        composeRule.onNodeWithContentDescription("显示当前密码").performClick()
        composeRule.onNodeWithTag("change_password_current").assertTextContains("secret123")
        saveScreenshot("account_change_password_visible.png")
    }

    @Test
    fun avatarCropOutputIsSquareAndNoLargerThan1024Pixels() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = Bitmap.createBitmap(1400, 1200, Bitmap.Config.ARGB_8888)
        val uri = writeAvatarCrop(context, source, AvatarCropSourceRect(left = 100, top = 50, side = 1100))
        try {
            val output = BitmapFactory.decodeFile(requireNotNull(uri.path))
            try {
                assertEquals(1024, output.width)
                assertEquals(output.width, output.height)
            } finally {
                output.recycle()
            }
        } finally {
            source.recycle()
            deletePendingAvatar(uri)
        }
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
