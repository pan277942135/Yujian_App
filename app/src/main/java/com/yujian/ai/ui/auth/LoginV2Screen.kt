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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import com.yujian.ai.ui.screens.isValidAuthUsername

@Composable
@OptIn(ExperimentalComposeUiApi::class)
fun LoginV2Screen(
    loading: Boolean,
    error: String?,
    onLogin: (username: String, password: String) -> Unit,
    onRegister: () -> Unit,
    onForgotPassword: () -> Unit,
    onBack: () -> Unit,
    onFieldEdited: () -> Unit = {},
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var usernameTouched by remember { mutableStateOf(false) }
    var passwordTouched by remember { mutableStateOf(false) }
    var submitAttempted by remember { mutableStateOf(false) }
    val usernameFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val canSubmit = isValidAuthUsername(username) && password.isNotBlank()
    val submit = {
        if (canSubmit && !loading) onLogin(username.trim(), password) else submitAttempted = true
    }

    BackHandler {
        if (imeVisible) {
            keyboardController?.hide()
            focusManager.clearFocus()
        } else {
            onBack()
        }
    }

    AuthV2Scaffold(
        title = "欢迎回来",
        subtitle = "继续记录你的每一次渔获",
        environmentFraction = 0.34f,
    ) {
        AuthV2Field(
            label = "账号",
            value = username,
            onValueChange = { username = it; usernameTouched = true; onFieldEdited() },
            placeholder = "请输入账号",
            enabled = !loading,
            leadingIcon = Icons.Rounded.PersonOutline,
            focusRequester = usernameFocus,
            autofillType = AutofillType.Username,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
            errorText = if ((usernameTouched || submitAttempted) && !isValidAuthUsername(username)) {
                "请输入 3–32 位字母、数字、_ 或 - 组成的账号"
            } else null,
            modifier = Modifier.testTag("login_username"),
        )

        Spacer(Modifier.height(14.dp))

        AuthV2Field(
            label = "密码",
            value = password,
            onValueChange = { password = it; passwordTouched = true; onFieldEdited() },
            placeholder = "请输入密码",
            enabled = !loading,
            leadingIcon = Icons.Rounded.Lock,
            focusRequester = passwordFocus,
            autofillType = AutofillType.Password,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                keyboardController?.hide()
                focusManager.clearFocus()
                submit()
            }),
            errorText = if ((passwordTouched || submitAttempted) && password.isBlank()) "请输入密码" else null,
            visualTransformation = if (passwordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            passwordVisible = passwordVisible,
            onTogglePasswordVisibility = { passwordVisible = !passwordVisible },
            modifier = Modifier.testTag("login_password"),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 1.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            YuJianTextAction(
                text = "忘记密码？",
                onClick = onForgotPassword,
                role = YuJianTextActionRole.MUTED,
                enabled = !loading,
            )
        }

        AuthV2ErrorMessage(
            error = error,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
        )

        YuJianPrimaryButton(
            text = "登录",
            onClick = { submit() },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("login_submit"),
            variant = YuJianActionButtonVariant.PRIMARY,
            enabled = canSubmit,
            loading = loading,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 7.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "还没有账号？",
                color = YuJianColors.MistBlueGray,
                fontSize = 13.sp,
            )
            YuJianTextAction(
                text = "创建账号",
                onClick = onRegister,
                role = YuJianTextActionRole.STRONG,
                enabled = !loading,
            )
        }
    }
}
