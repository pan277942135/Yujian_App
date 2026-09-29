package com.yujian.ai.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardActions
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yujian.ai.ui.designsystem.color.YuJianColors
import com.yujian.ai.ui.designsystem.components.YuJianActionButtonVariant
import com.yujian.ai.ui.designsystem.components.YuJianPrimaryButton
import com.yujian.ai.ui.designsystem.components.YuJianTextAction
import com.yujian.ai.ui.designsystem.components.YuJianTextActionRole
import com.yujian.ai.ui.screens.isValidRegisterForm
import com.yujian.ai.ui.screens.isValidAuthUsername

@Composable
@OptIn(ExperimentalComposeUiApi::class)
fun RegisterV2Screen(
    loading: Boolean,
    error: String?,
    onRegister: (username: String, password: String, nickname: String) -> Unit,
    onBackToLogin: () -> Unit,
    onFieldEdited: () -> Unit = {},
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var usernameTouched by remember { mutableStateOf(false) }
    var passwordTouched by remember { mutableStateOf(false) }
    var nicknameTouched by remember { mutableStateOf(false) }
    var submitAttempted by remember { mutableStateOf(false) }
    val usernameFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    val nicknameFocus = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val canSubmit = isValidRegisterForm(username, password, nickname)
    val submit = {
        if (canSubmit && !loading) onRegister(username.trim(), password, nickname.trim()) else submitAttempted = true
    }

    BackHandler {
        if (imeVisible) {
            keyboardController?.hide()
            focusManager.clearFocus()
        } else {
            onBackToLogin()
        }
    }

    AuthV2Scaffold(
        title = "创建账号",
        subtitle = "用一个账号，留住你的钓鱼轨迹",
        environmentFraction = 0.31f,
    ) {
        AuthV2Field(
            label = "账号",
            value = username,
            onValueChange = { username = it; usernameTouched = true; onFieldEdited() },
            placeholder = "请输入账号",
            helperText = "3–32 位字母、数字、_ 或 -",
            enabled = !loading,
            leadingIcon = Icons.Rounded.PersonOutline,
            focusRequester = usernameFocus,
            autofillType = AutofillType.NewUsername,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
            errorText = if ((usernameTouched || submitAttempted) && !isValidAuthUsername(username)) {
                "请输入 3–32 位字母、数字、_ 或 - 组成的账号"
            } else null,
            modifier = Modifier.testTag("register_username"),
        )

        Spacer(Modifier.height(9.dp))

        AuthV2Field(
            label = "密码",
            value = password,
            onValueChange = { password = it; passwordTouched = true; onFieldEdited() },
            placeholder = "请输入密码",
            helperText = "至少 6 位",
            enabled = !loading,
            leadingIcon = Icons.Rounded.Lock,
            focusRequester = passwordFocus,
            autofillType = AutofillType.NewPassword,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { nicknameFocus.requestFocus() }),
            errorText = if ((passwordTouched || submitAttempted) && password.length !in 6..72) {
                "密码长度需要为 6–72 位"
            } else null,
            visualTransformation = if (passwordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            passwordVisible = passwordVisible,
            onTogglePasswordVisibility = { passwordVisible = !passwordVisible },
            modifier = Modifier.testTag("register_password"),
        )

        Spacer(Modifier.height(9.dp))

        AuthV2Field(
            label = "昵称",
            value = nickname,
            onValueChange = { nickname = it; nicknameTouched = true; onFieldEdited() },
            placeholder = "请输入昵称",
            enabled = !loading,
            leadingIcon = Icons.Rounded.Badge,
            focusRequester = nicknameFocus,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                keyboardController?.hide()
                focusManager.clearFocus()
                submit()
            }),
            errorText = if ((nicknameTouched || submitAttempted) && nickname.trim().length !in 1..20) {
                "请输入 1–20 个字符的昵称"
            } else null,
            modifier = Modifier.testTag("register_nickname"),
        )

        AuthV2ErrorMessage(
            error = error,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
        )

        YuJianPrimaryButton(
            text = "注册并登录",
            onClick = { submit() },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("register_submit"),
            variant = YuJianActionButtonVariant.PRIMARY,
            enabled = canSubmit,
            loading = loading,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 5.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "已有账号？",
                color = YuJianColors.MistBlueGray,
                fontSize = 13.sp,
            )
            YuJianTextAction(
                text = "去登录",
                onClick = onBackToLogin,
                role = YuJianTextActionRole.STRONG,
                enabled = !loading,
            )
        }
    }
}
