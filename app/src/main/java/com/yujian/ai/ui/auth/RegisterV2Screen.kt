package com.yujian.ai.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yujian.ai.ui.screens.isValidRegisterForm

@Composable
fun RegisterV2Screen(
    loading: Boolean,
    error: String?,
    onRegister: (username: String, password: String, nickname: String) -> Unit,
    onBackToLogin: () -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val safe = WindowInsets.safeDrawing.asPaddingValues()
    val canSubmit = isValidRegisterForm(username, password, nickname) && !loading

    Box(Modifier.fillMaxSize()) {
        AuthContentBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 24.dp,
                    end = 24.dp,
                    top = safe.calculateTopPadding() + 42.dp,
                    bottom = safe.calculateBottomPadding() + 20.dp,
                ),
        ) {
            Text(
                text = "渔见",
                color = AuthDeepLake,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.0.sp,
            )
            Text(
                text = "拍照收藏每次渔获",
                color = AuthSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 2.dp),
            )

            Spacer(Modifier.height(84.dp))

            Text(
                text = "创建账号",
                color = AuthDeepLake,
                fontSize = 27.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "用一个账号，留住你的钓鱼轨迹",
                color = AuthSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 7.dp, bottom = 22.dp),
            )

            AuthFieldLabel("账号")
            OutlinedTextField(
                value = username,
                onValueChange = { username = it.take(32) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 7.dp)
                    .height(56.dp)
                    .testTag("register_username"),
                singleLine = true,
                leadingIcon = {
                    Icon(
                        Icons.Rounded.PersonOutline,
                        contentDescription = null,
                        tint = AuthSecondary,
                        modifier = Modifier.size(21.dp),
                    )
                },
                placeholder = { Text("3–32 位字母、数字、_ 或 -", color = AuthSecondary.copy(alpha = 0.72f)) },
                shape = RoundedCornerShape(18.dp),
                colors = authFieldColors(),
            )

            AuthFieldLabel("密码", Modifier.padding(top = 15.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it.take(72) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 7.dp)
                    .height(56.dp)
                    .testTag("register_password"),
                singleLine = true,
                leadingIcon = {
                    Icon(
                        Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = AuthSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                },
                placeholder = { Text("至少 6 位", color = AuthSecondary.copy(alpha = 0.72f)) },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                            tint = AuthSecondary,
                        )
                    }
                },
                shape = RoundedCornerShape(18.dp),
                colors = authFieldColors(),
            )

            AuthFieldLabel("昵称", Modifier.padding(top = 15.dp))
            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it.take(20) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 7.dp)
                    .height(56.dp)
                    .testTag("register_nickname"),
                singleLine = true,
                leadingIcon = {
                    Icon(
                        Icons.Rounded.PersonOutline,
                        contentDescription = null,
                        tint = AuthSecondary,
                        modifier = Modifier.size(21.dp),
                    )
                },
                placeholder = { Text("1–20 个字符", color = AuthSecondary.copy(alpha = 0.72f)) },
                shape = RoundedCornerShape(18.dp),
                colors = authFieldColors(),
            )

            if (!error.isNullOrBlank()) {
                Text(
                    text = error,
                    color = AuthErrorRed,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                )
            }

            Button(
                onClick = { onRegister(username.trim(), password, nickname.trim()) },
                enabled = canSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (error.isNullOrBlank()) 22.dp else 6.dp)
                    .height(56.dp)
                    .testTag("register_submit"),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AuthLakeTeal,
                    contentColor = Color.White,
                    disabledContainerColor = AuthLakeTeal.copy(alpha = 0.34f),
                    disabledContentColor = Color.White.copy(alpha = 0.74f),
                ),
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("注册并登录", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 13.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("已有账号？", color = AuthSecondary, fontSize = 13.sp)
                TextButton(onClick = onBackToLogin, enabled = !loading) {
                    Text("去登录", color = AuthActiveAccent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun AuthFieldLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = AuthDeepLake,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier,
    )
}
