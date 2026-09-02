package com.yujian.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import com.yujian.ai.ui.theme.DeepInk
import com.yujian.ai.ui.theme.MutedInk
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WarmBackground)
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(horizontal = 24.dp, vertical = 56.dp)),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("渔见", color = DeepInk, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text("登录后保存你的每一次 AI 鱼获", color = MutedInk, fontSize = 14.sp)
        OutlinedTextField(
            value = username,
            onValueChange = { username = it.take(64) },
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
            singleLine = true,
            label = { Text("账号") },
            shape = RoundedCornerShape(16.dp),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it.take(128) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("密码") },
            visualTransformation = PasswordVisualTransformation(),
            shape = RoundedCornerShape(16.dp),
        )
        if (!error.isNullOrBlank()) {
            Text(error, color = Color(0xFFB42318), fontSize = 12.sp)
        }
        Button(
            onClick = { onLogin(username, password) },
            enabled = !loading && username.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = WaterTeal),
        ) {
            if (loading) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.padding(end = 8.dp))
            Text(if (loading) "登录中…" else "登录", fontSize = 16.sp)
        }
        TextButton(onClick = onRegister, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
            Text("还没有账号？去注册", color = WaterTeal)
        }
    }
}
