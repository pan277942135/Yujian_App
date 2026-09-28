package com.yujian.ai.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yujian.ai.R
import com.yujian.ai.ui.screens.isValidAuthUsername

private val LakeTeal = Color(0xFF0F7A78)
private val ActiveAccent = Color(0xFF168B88)
private val DeepLake = Color(0xFF12364A)
private val Secondary = Color(0xFF74879A)
private val Border = Color(0xFFD5DFE6)
private val ErrorRed = Color(0xFFB24A3A)

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
        Image(
            painter = painterResource(R.drawable.account_privacy_morning_lake),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.00f to Color.White.copy(alpha = 0.02f),
                        0.33f to Color.White.copy(alpha = 0.08f),
                        0.50f to Color(0xFFF7FAFA).copy(alpha = 0.72f),
                        0.66f to Color(0xFFF8FAFA).copy(alpha = 0.92f),
                        1.00f to Color(0xFFF9FAF8).copy(alpha = 0.98f),
                    ),
                ),
        )

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
                color = DeepLake,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.0.sp,
            )
            Text(
                text = "拍照收藏每次渔获",
                color = Secondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 2.dp),
            )

            Spacer(Modifier.height(145.dp))

            Text(
                text = "欢迎回来",
                color = DeepLake,
                fontSize = 27.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "继续记录你的每一次渔获",
                color = Secondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 7.dp, bottom = 22.dp),
            )

            Text("账号", color = DeepLake, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            OutlinedTextField(
                value = username,
                onValueChange = { username = it.take(32) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 7.dp)
                    .height(56.dp),
                singleLine = true,
                leadingIcon = {
                    Icon(
                        Icons.Rounded.PersonOutline,
                        contentDescription = null,
                        tint = Secondary,
                        modifier = Modifier.size(21.dp),
                    )
                },
                placeholder = { Text("请输入账号", color = Secondary.copy(alpha = 0.72f)) },
                shape = RoundedCornerShape(18.dp),
                colors = loginFieldColors(),
            )

            Text(
                text = "密码",
                color = DeepLake,
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
                    .height(56.dp),
                singleLine = true,
                leadingIcon = {
                    Icon(
                        Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = Secondary,
                        modifier = Modifier.size(20.dp),
                    )
                },
                placeholder = { Text("请输入密码", color = Secondary.copy(alpha = 0.72f)) },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                            tint = Secondary,
                        )
                    }
                },
                shape = RoundedCornerShape(18.dp),
                colors = loginFieldColors(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onForgotPassword, enabled = !loading) {
                    Text("忘记密码？", color = ActiveAccent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }

            if (!error.isNullOrBlank()) {
                Text(
                    text = error,
                    color = ErrorRed,
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
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LakeTeal,
                    contentColor = Color.White,
                    disabledContainerColor = LakeTeal.copy(alpha = 0.34f),
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
                Text("还没有账号？", color = Secondary, fontSize = 13.sp)
                TextButton(onClick = onRegister, enabled = !loading) {
                    Text("创建账号", color = ActiveAccent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun loginFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = ActiveAccent,
    unfocusedBorderColor = Border,
    disabledBorderColor = Border.copy(alpha = 0.6f),
    focusedTextColor = DeepLake,
    unfocusedTextColor = DeepLake,
    cursorColor = ActiveAccent,
    focusedContainerColor = Color.White.copy(alpha = 0.72f),
    unfocusedContainerColor = Color.White.copy(alpha = 0.68f),
)
