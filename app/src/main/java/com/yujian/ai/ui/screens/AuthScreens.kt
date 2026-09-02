package com.yujian.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yujian.ai.ui.theme.CardWhite
import com.yujian.ai.ui.theme.DeepInk
import com.yujian.ai.ui.theme.MutedInk
import com.yujian.ai.ui.theme.SoftWater
import com.yujian.ai.ui.theme.WarmBackground
import com.yujian.ai.ui.theme.WaterTeal

@Composable
fun LoginScreen(
    loading: Boolean,
    error: String?,
    onLogin: (username: String, password: String) -> Unit,
    onRegister: () -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    AuthLayout(title = "登录渔见", subtitle = "保存每一次真实鱼获") {
        OutlinedTextField(
            value = username, onValueChange = { username = it.take(32) },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("账号") }, shape = RoundedCornerShape(18.dp),
        )
        OutlinedTextField(
            value = password, onValueChange = { password = it.take(128) },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            label = { Text("密码") }, shape = RoundedCornerShape(18.dp),
        )
        AuthError(error)
        Button(
            onClick = { onLogin(username.trim(), password) },
            enabled = !loading && username.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(27.dp),
            colors = ButtonDefaults.buttonColors(containerColor = WaterTeal),
        ) { Text(if (loading) "登录中…" else "登录", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
        TextButton(onClick = onRegister, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
            Text("还没有账号？去注册", color = WaterTeal)
        }
    }
}

@Composable
fun RegisterScreen(
    loading: Boolean,
    error: String?,
    onRegister: (username: String, password: String, nickname: String) -> Unit,
    onBackToLogin: () -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    AuthLayout(title = "创建账号", subtitle = "用一个账号，留住你的钓鱼轨迹") {
        OutlinedTextField(
            value = username, onValueChange = { username = it.take(32) },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("账号") }, supportingText = { Text("3–32 位字母、数字、下划线或连字符") },
            shape = RoundedCornerShape(18.dp),
        )
        OutlinedTextField(
            value = password, onValueChange = { password = it.take(128) },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            label = { Text("密码") }, supportingText = { Text("至少 6 位") }, shape = RoundedCornerShape(18.dp),
        )
        OutlinedTextField(
            value = nickname, onValueChange = { nickname = it.take(32) },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("昵称") }, shape = RoundedCornerShape(18.dp),
        )
        AuthError(error)
        Button(
            onClick = { onRegister(username.trim(), password, nickname.trim()) },
            enabled = !loading && username.isNotBlank() && password.length >= 6 && nickname.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(27.dp),
            colors = ButtonDefaults.buttonColors(containerColor = WaterTeal),
        ) { Text(if (loading) "注册中…" else "注册并登录", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
        TextButton(onClick = onBackToLogin, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
            Text("已有账号？去登录", color = WaterTeal)
        }
    }
}

@Composable
private fun AuthLayout(title: String, subtitle: String, content: @Composable () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(WarmBackground),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(
                Modifier.fillMaxWidth().padding(top = 64.dp).background(CardWhite, RoundedCornerShape(30.dp)).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("渔见 AI", color = WaterTeal, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(title, color = DeepInk, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = MutedInk, fontSize = 13.sp)
                content()
            }
        }
    }
}

@Composable
private fun AuthError(error: String?) {
    if (!error.isNullOrBlank()) {
        Text(
            error,
            color = Color(0xFFB24A3A),
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth().background(SoftWater, RoundedCornerShape(12.dp)).padding(12.dp),
        )
    }
}
