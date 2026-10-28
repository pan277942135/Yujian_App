package com.yujian.ai.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun GuestRegistrationDialog(
    onRegister: () -> Unit,
    onLater: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text("保存你的第一条鱼") },
        text = { Text("注册账号，永久保存你的鱼获记录。") },
        confirmButton = { TextButton(onClick = onRegister) { Text("立即注册") } },
        dismissButton = { TextButton(onClick = onLater) { Text("稍后") } },
    )
}
