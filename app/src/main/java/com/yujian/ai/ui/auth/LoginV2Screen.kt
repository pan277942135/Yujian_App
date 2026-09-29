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
import com.yujian.ai.ui.screens.isValidAuthUsername

@Composable
fun LoginV2Screen(
    loading: Boolean,
    error: String?,
    onLogin: (username: String, password: String) -> Unit,
    onRegister: () -> Unit,
    onForgotPassword: () -> Unit,
    onBack: () -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val safe = WindowInsets.safeDrawing.asPaddingValues()
    val canSubmit = isValidAuthUsername(username) && password.isNotBlank() && !loading

    Box(Modifier.fillMaxSize()) {
        AuthContentBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 24.dp,
                    end = 24.dp,
                    top = safe.calculateTopPadding() + 50.dp,
                    bottom = safe.calculateBottomPadding() + 16.dp,
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

            Spacer(Modifier.height(145.dp))

            Text(
                text = "欢迎回来",
                color = AuthDeepLake,
                fontSize = 27.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "继续记录你的每一次渔获",
                color = AuthSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 7.dp, bottom = 22.dp),
            )

            Text("账号", color = AuthDeepLake, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            OutlinedTextField(
                value = username,
                onValueChange = { username = it.take(32) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 7.dp)
                    .height(56.dp)
                    .testTag("login_username"),
                singleLine = true,
                leadingIcon = {
                    Icon(
                        Icons.Rounded.PersonOutline,
                        contentDescription = null,
                        tint = AuthSecondary,
                        modifier = Modifier.size(21.dp),
                    )
                },
                placeholder = { Text("请输入账号", color = AuthSecondary.copy(alpha = 0.72f)) },
                shape = RoundedCornerShape(18.dp),
                colors = authFieldColors(),
            )

            Text(
                text = "密码",
                color = AuthDeepLake,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 15.dp),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it.take(72) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 7.dp)
                    .height(56.dp)
                    .testTag("login_password"),
                singleLine = true,
                leadingIcon = {
                    Icon(
                        Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = AuthSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                },
                placeholder = { Text("请输入密码", color = AuthSecondary.copy(alpha = 0.72f)) },
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onForgotPassword, enabled = !loading) {
                    Text("忘记密码？", color = AuthActiveAccent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }

            if (!error.isNullOrBlank()) {
                Text(
                    text = error,
                    color = AuthErrorRed,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }

            Button(
                onClick = { onLogin(username.trim(), password) },
                enabled = canSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(56.dp)
                    .testTag("login_submit"),
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
                    Text("登录", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 13.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("还没有账号？", color = AuthSecondary, fontSize = 13.sp)
                TextButton(onClick = onRegister, enabled = !loading) {
                    Text("创建账号", color = AuthActiveAccent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

