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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.10f),
                            Color.White.copy(alpha = 0.20f),
                            Color(0xFFF6F3EA).copy(alpha = 0.26f),
                        ),
                    ),
                ),
        )

        IconButton(
            onClick = onBack,
            enabled = !loading,
            modifier = Modifier
                .padding(start = 12.dp, top = safe.calculateTopPadding() + 4.dp)
                .align(Alignment.TopStart),
        ) {
            Icon(Icons.Rounded.ArrowBack, contentDescription = "返回", tint = DeepLake)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 24.dp,
                    end = 24.dp,
                    top = safe.calculateTopPadding() + 92.dp,
                    bottom = safe.calculateBottomPadding() + 22.dp,
                ),
            verticalArrangement = Arrangement.Top,
        ) {
            Text(
                text = "渔见",
                color = DeepLake,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp,
            )
            Text(
                text = "登录渔见",
                color = DeepLake,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 22.dp),
            )
            Text(
                text = "保存每一次真实鱼获",
                color = Secondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 7.dp),
            )

            Surface(
                color = Color.White.copy(alpha = 0.92f),
                shape = RoundedCornerShape(20.dp),
                tonalElevation = 0.dp,
                shadowElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it.take(32) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        singleLine = true,
                        placeholder = { Text("账号", color = Secondary) },
                        shape = RoundedCornerShape(18.dp),
                        colors = loginFieldColors(),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it.take(72) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        singleLine = true,
                        placeholder = { Text("密码", color = Secondary) },
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

                    if (!error.isNullOrBlank()) {
                        Text(
                            text = error,
                            color = ErrorRed,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.62f))
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                        )
                    }

                    Button(
                        onClick = { onLogin(username.trim(), password) },
                        enabled = canSubmit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LakeTeal,
                            contentColor = Color.White,
                            disabledContainerColor = LakeTeal.copy(alpha = 0.35f),
                            disabledContentColor = Color.White.copy(alpha = 0.72f),
                        ),
                    ) {
                        if (loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("登录", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onForgotPassword, enabled = !loading) {
                            Text("忘记密码？", color = ActiveAccent, fontSize = 13.sp)
                        }
                        TextButton(onClick = onRegister, enabled = !loading) {
                            Text("创建账号", color = ActiveAccent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))
            Text(
                text = "继续即表示你同意渔见的用户协议与隐私政策",
                color = Secondary.copy(alpha = 0.80f),
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
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
    focusedContainerColor = Color.White.copy(alpha = 0.76f),
    unfocusedContainerColor = Color.White.copy(alpha = 0.70f),
)
