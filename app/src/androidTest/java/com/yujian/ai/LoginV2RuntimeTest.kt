package com.yujian.ai

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.dp
import com.yujian.ai.ui.designsystem.components.YuJianPrimaryButton
import com.yujian.ai.ui.designsystem.components.YuJianBackTitleActionsTopBar
import com.yujian.ai.ui.designsystem.components.YuJianBackTitleTopBar
import com.yujian.ai.ui.designsystem.components.YuJianIconAction
import com.yujian.ai.ui.designsystem.components.YuJianIconActionFamily
import com.yujian.ai.ui.designsystem.components.YuJianTextAction
import com.yujian.ai.ui.designsystem.components.YuJianTitleOnlyTopBar
import com.yujian.ai.ui.designsystem.components.YuJianTopBarAction
import com.yujian.ai.ui.auth.LoginV2Screen
import com.yujian.ai.ui.auth.RegisterV2Screen
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LoginV2RuntimeTest {
    private fun primaryButton(text: String) = composeRule.onNode(
        hasText(text) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button),
    )

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun awaitImeVisible() {
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            val insets = composeRule.activity.window.decorView.rootWindowInsets
                ?: return@waitUntil false
            WindowInsetsCompat.toWindowInsetsCompat(insets).isVisible(WindowInsetsCompat.Type.ime())
        }
    }

    private fun assertInsideCompactViewport(node: SemanticsNodeInteraction) {
        val viewport = composeRule.onNodeWithTag("auth_compact_viewport")
            .fetchSemanticsNode().boundsInRoot
        val bounds = node.fetchSemanticsNode().boundsInRoot
        assertTrue("node is clipped above the compact viewport", bounds.top >= viewport.top)
        assertTrue("node is clipped below the compact viewport", bounds.bottom <= viewport.bottom)
    }

    @Test
    fun idle_matchesFrozenStructure() {
        var loginCalls = 0
        composeRule.setContent {
            LoginV2Screen(
                loading = false,
                error = null,
                onLogin = { _, _ -> loginCalls += 1 },
                onRegister = {},
                onForgotPassword = {},
                onBack = {},
            )
        }
        composeRule.onNodeWithText("欢迎回来").assertExists()
        composeRule.onNodeWithText("继续记录你的每一次渔获").assertExists()
        composeRule.onNodeWithTag("login_username").assertExists()
        composeRule.onNodeWithTag("login_password").assertExists()
        composeRule.onNodeWithText("忘记密码？").assertExists()
        composeRule.onNodeWithText("创建账号").assertExists()
        primaryButton("登录").assertIsNotEnabled()
        primaryButton("登录").performTouchInput { down(center); up() }
        composeRule.runOnIdle { assertEquals(0, loginCalls) }
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
        primaryButton("登录").assertIsEnabled()
        saveScreenshot("login_v2_filled.png")
        primaryButton("登录").performClick()
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
    fun login_imeNextMovesToPassword_andDoneSubmits() {
        var submitted = false
        composeRule.setContent {
            LoginV2Screen(
                loading = false,
                error = null,
                onLogin = { _, _ -> submitted = true },
                onRegister = {},
                onForgotPassword = {},
                onBack = {},
            )
        }
        composeRule.onNodeWithTag("login_username").performTextInput("fisher003")
        composeRule.onNodeWithTag("login_username").performClick()
        composeRule.onNodeWithTag("login_username").performImeAction()
        composeRule.onNodeWithTag("login_password").assertIsFocused()
        saveScreenshot("login_v2_focus.png")
        composeRule.onNodeWithTag("login_password").performTextInput("123456")
        saveScreenshot("login_v2_keyboard.png")
        composeRule.onNodeWithTag("login_password").performImeAction()
        composeRule.runOnIdle { assert(submitted) }
    }

    @Test
    fun register_imeMovesAcrossFields_andDoneSubmits() {
        var submitted = false
        composeRule.setContent {
            RegisterV2Screen(
                loading = false,
                error = null,
                onRegister = { _, _, _ -> submitted = true },
                onBackToLogin = {},
            )
        }
        composeRule.onNodeWithTag("register_username").performTextInput("fisher004")
        composeRule.onNodeWithTag("register_username").performClick()
        composeRule.onNodeWithTag("register_username").performImeAction()
        composeRule.onNodeWithTag("register_password").assertIsFocused()
        saveScreenshot("register_v2_focus.png")
        composeRule.onNodeWithTag("register_password").performTextInput("123456")
        composeRule.onNodeWithTag("register_password").performImeAction()
        composeRule.onNodeWithTag("register_nickname").assertIsFocused()
        composeRule.onNodeWithTag("register_nickname").performTextInput("angler")
        saveScreenshot("register_v2_keyboard.png")
        composeRule.onNodeWithTag("register_nickname").performImeAction()
        composeRule.runOnIdle { assert(submitted) }
    }

    @Test
    fun login_smallScreen_keepsSubmitReachableByScrolling() {
        composeRule.setContent {
            Box(Modifier.width(360.dp).height(560.dp)) {
                LoginV2Screen(
                    loading = false,
                    error = null,
                    onLogin = { _, _ -> },
                    onRegister = {},
                    onForgotPassword = {},
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag("login_submit").performScrollTo().assertIsDisplayed()
        saveScreenshot("login_v2_small_screen.png")
    }

    @Test
    fun login_invalidUsernameWithIme_keepsErrorVisibleAndLaterActionsReachable() {
        composeRule.setContent {
            Box(Modifier.width(360.dp).height(560.dp).testTag("auth_compact_viewport")) {
                LoginV2Screen(
                    loading = false,
                    error = null,
                    onLogin = { _, _ -> },
                    onRegister = {},
                    onForgotPassword = {},
                    onBack = {},
                )
            }
        }

        val username = composeRule.onNodeWithTag("login_username")
        username.performClick()
        awaitImeVisible()
        username.performTextInput("x")
        composeRule.onNodeWithText("账号").assertIsDisplayed()
        username.assertIsFocused().assertTextContains("x").assertIsDisplayed()
        val error = composeRule.onNodeWithText("请输入 3–32 位字母、数字、_ 或 - 组成的账号")
        error.assertIsDisplayed()
        assertInsideCompactViewport(error)

        composeRule.onNodeWithTag("login_password").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("login_submit").performScrollTo().assertIsDisplayed()
        saveScreenshot("login_v2_invalid_username_ime_compact.png")
    }

    @Test
    fun register_smallScreen_keepsSubmitReachableByScrolling() {
        composeRule.setContent {
            Box(Modifier.width(360.dp).height(560.dp)) {
                RegisterV2Screen(
                    loading = false,
                    error = null,
                    onRegister = { _, _, _ -> },
                    onBackToLogin = {},
                )
            }
        }
        composeRule.onNodeWithTag("register_submit").performScrollTo().assertIsDisplayed()
        saveScreenshot("register_v2_small_screen.png")
    }

    @Test
    fun register_helpers_areVisibleAndScrollableOnCompactScreen() {
        composeRule.setContent {
            Box(Modifier.width(360.dp).height(560.dp).testTag("auth_compact_viewport")) {
                RegisterV2Screen(
                    loading = false,
                    error = null,
                    onRegister = { _, _, _ -> },
                    onBackToLogin = {},
                )
            }
        }

        val usernameHelper = composeRule.onNodeWithText("3–32 位字母、数字、_ 或 -")
        usernameHelper.assertIsDisplayed()
        assertInsideCompactViewport(usernameHelper)
        val passwordHelper = composeRule.onNodeWithText("至少 6 位")
        passwordHelper.performScrollTo().assertIsDisplayed()
        assertInsideCompactViewport(passwordHelper)
        composeRule.onNodeWithTag("register_nickname").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("register_submit").performScrollTo().assertIsDisplayed()
        saveScreenshot("register_v2_helpers_compact.png")
    }

    @Test
    fun register_invalidFieldsWithIme_showFullErrorsWithoutOverlapAndKeepSubmitReachable() {
        composeRule.setContent {
            Box(Modifier.width(360.dp).height(560.dp).testTag("auth_compact_viewport")) {
                RegisterV2Screen(
                    loading = false,
                    error = null,
                    onRegister = { _, _, _ -> },
                    onBackToLogin = {},
                )
            }
        }

        val usernameError = "请输入 3–32 位字母、数字、_ 或 - 组成的账号"
        val username = composeRule.onNodeWithTag("register_username")
        username.performClick()
        awaitImeVisible()
        username.performTextInput("x")
        composeRule.onNodeWithText("账号").assertIsDisplayed()
        username.assertIsFocused().assertTextContains("x").assertIsDisplayed()
        val usernameErrorNode = composeRule.onNodeWithText(usernameError)
        usernameErrorNode.assertIsDisplayed()
        assertInsideCompactViewport(usernameErrorNode)
        val usernameErrorBottom = usernameErrorNode.fetchSemanticsNode().boundsInRoot.bottom
        val passwordTop = composeRule.onNodeWithTag("register_password")
            .fetchSemanticsNode().boundsInRoot.top
        assertTrue("username error overlaps the password field", usernameErrorBottom < passwordTop)

        val password = composeRule.onNodeWithTag("register_password")
        password.performScrollTo().performClick().performTextInput("1")
        password.assertIsFocused().assertTextContains("1").assertIsDisplayed()
        val passwordErrorNode = composeRule.onNodeWithText("密码长度需要为 6–72 位")
        passwordErrorNode.assertIsDisplayed()
        assertInsideCompactViewport(passwordErrorNode)
        val passwordErrorBottom = passwordErrorNode.fetchSemanticsNode().boundsInRoot.bottom
        val nicknameTop = composeRule.onNodeWithTag("register_nickname")
            .fetchSemanticsNode().boundsInRoot.top
        assertTrue("password error overlaps the nickname field", passwordErrorBottom < nicknameTop)

        val nickname = composeRule.onNodeWithTag("register_nickname")
        nickname.performScrollTo().performClick().performTextInput(" ")
        composeRule.onNodeWithText("昵称").assertIsDisplayed()
        nickname.assertIsFocused().assertIsDisplayed()
        val nicknameError = composeRule.onNodeWithText("请输入 1–20 个字符的昵称")
        nicknameError.assertIsDisplayed()
        assertInsideCompactViewport(nicknameError)
        composeRule.onNodeWithTag("register_submit").performScrollTo().assertIsDisplayed()
        saveScreenshot("register_v2_invalid_fields_ime_compact.png")
    }

    @Test
    fun p0SharedComponents_renderFrozenVariantsAndButtonStates() {
        var disabledButtonClicks = 0
        composeRule.setContent {
            Column {
                YuJianTitleOnlyTopBar("标题")
                YuJianBackTitleTopBar("返回 + 标题", onBack = {})
                YuJianBackTitleActionsTopBar(
                    title = "返回 + 标题 + 工具",
                    onBack = {},
                    actions = listOf(
                        YuJianTopBarAction(
                            icon = Icons.Rounded.MoreHoriz,
                            contentDescription = "更多",
                            onClick = {},
                        ),
                    ),
                )
                YuJianTextAction("文字操作", onClick = {})
                YuJianIconAction(
                    icon = Icons.Rounded.ArrowBack,
                    contentDescription = "独立图标操作",
                    onClick = {},
                    family = YuJianIconActionFamily.UTILITY,
                )
                YuJianPrimaryButton("默认", onClick = {}, modifier = Modifier.testTag("p0_button_default"))
                YuJianPrimaryButton("按下", onClick = {}, modifier = Modifier.testTag("p0_button_pressed"))
                YuJianPrimaryButton(
                    "禁用",
                    onClick = { disabledButtonClicks += 1 },
                    modifier = Modifier.testTag("p0_button_disabled"),
                    enabled = false,
                )
                YuJianPrimaryButton(
                    "加载",
                    onClick = {},
                    modifier = Modifier.testTag("p0_button_loading"),
                    loading = true,
                )
            }
        }
        primaryButton("默认").assertIsEnabled()
        primaryButton("禁用").assertIsNotEnabled()
        primaryButton("禁用").performTouchInput { down(center); up() }
        composeRule.runOnIdle { assertEquals(0, disabledButtonClicks) }
        composeRule.onNodeWithText("文字操作").assertExists()
        composeRule.onNodeWithContentDescription("独立图标操作").assertExists()
        saveScreenshot("p0_shared_components_default.png")
        composeRule.onNodeWithTag("p0_button_pressed").performTouchInput { down(center) }
        composeRule.waitForIdle()
        saveScreenshot("p0_button_pressed.png")
        composeRule.onNodeWithTag("p0_button_pressed").performTouchInput { up() }
        saveScreenshot("p0_button_disabled.png")
        saveScreenshot("p0_button_loading.png")
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

    @Test
    fun register_idle_matchesFrozenStructure() {
        composeRule.setContent {
            RegisterV2Screen(
                loading = false,
                error = null,
                onRegister = { _, _, _ -> },
                onBackToLogin = {},
            )
        }
        composeRule.onNodeWithText("渔见").assertDoesNotExist()
        composeRule.onNodeWithText("拍照收藏每次渔获").assertDoesNotExist()
        composeRule.onNodeWithText("创建账号").assertExists()
        composeRule.onNodeWithText("用一个账号，留住你的钓鱼轨迹").assertExists()
        composeRule.onNodeWithTag("register_username").assertExists()
        composeRule.onNodeWithTag("register_password").assertExists()
        composeRule.onNodeWithTag("register_nickname").assertExists()
        composeRule.onNodeWithText("注册并登录").assertExists()
        composeRule.onNodeWithText("去登录").assertExists()
        primaryButton("注册并登录").assertIsNotEnabled()
        saveScreenshot("register_v2_idle.png")
    }

    @Test
    fun register_filled_form_isActionable() {
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
        composeRule.onNodeWithTag("register_username").performTextInput("fisher002")
        composeRule.onNodeWithTag("register_password").performTextInput("123456")
        composeRule.onNodeWithTag("register_nickname").performTextInput("angler")
        primaryButton("注册并登录").assertIsEnabled()
        saveScreenshot("register_v2_filled.png")
        primaryButton("注册并登录").performClick()
        composeRule.runOnIdle {
            assertEquals("fisher002", username)
            assertEquals("123456", password)
            assertEquals("angler", nickname)
        }
    }

    @Test
    fun register_errorAndLoading_areVisibleAndStable() {
        val loading = mutableStateOf(false)
        composeRule.setContent {
            RegisterV2Screen(
                loading = loading.value,
                error = if (loading.value) null else "账号已存在",
                onRegister = { _, _, _ -> },
                onBackToLogin = {},
            )
        }
        composeRule.onNodeWithText("账号已存在").assertExists()
        saveScreenshot("register_v2_error.png")

        composeRule.runOnIdle { loading.value = true }
        primaryButton("注册并登录").assertIsNotEnabled()
        saveScreenshot("register_v2_loading.png")
    }

    @Test
    fun formValidation_errorsAreInline() {
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
        composeRule.onNodeWithTag("login_username").performTextInput("x")
        composeRule.onNodeWithText("请输入 3–32 位字母、数字、_ 或 - 组成的账号").assertExists()
        saveScreenshot("login_v2_validation_error.png")
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
