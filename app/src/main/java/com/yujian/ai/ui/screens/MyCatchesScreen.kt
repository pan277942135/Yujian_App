package com.yujian.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yujian.ai.account.CatchStatistics
import com.yujian.ai.account.RemoteCatch
import com.yujian.ai.account.UserSession
import com.yujian.ai.ui.theme.DeepInk
import com.yujian.ai.ui.theme.MutedInk
import com.yujian.ai.ui.theme.WarmBackground
import com.yujian.ai.ui.theme.WaterTeal
import com.yujian.ai.ui.components.YujianTopBar

@Composable
fun MyCatchesScreen(
    session: UserSession?,
    statistics: CatchStatistics?,
    catches: List<RemoteCatch>,
    loading: Boolean,
    error: String?,
    resolveImageUrl: (String?) -> String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenKnowledge: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(WarmBackground),
        contentPadding = PaddingValues(bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { YujianTopBar(title = "我的鱼获", onBack = onBack) }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Text(session?.nickname ?: "渔见用户", color = DeepInk, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(
                    "累计 ${statistics?.totalCatches ?: 0} 条 · ${statistics?.speciesCount ?: 0} 种鱼",
                    color = MutedInk, fontSize = 13.sp, modifier = Modifier.padding(top = 5.dp),
                )
                val top = statistics?.topSpecies
                val recent = statistics?.recentSpecies
                if (!top.isNullOrBlank() || !recent.isNullOrBlank()) {
                    Text(
                        "最常钓：${top ?: "—"}    最近：${recent ?: "—"}",
                        color = WaterTeal, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }
        if (loading) {
            item { Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = WaterTeal) } }
        }
        if (!error.isNullOrBlank()) {
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    Text(error, color = Color(0xFFB42318), fontSize = 12.sp)
                    TextButton(onClick = onRetry) { Text("重新加载", color = WaterTeal) }
                }
            }
        }
        if (!loading && catches.isEmpty() && error.isNullOrBlank()) {
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("还没有保存鱼获", color = DeepInk, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("完成一次 AI 识鱼后，点击“保存这次鱼获”", color = MutedInk, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        items(catches, key = { it.id }) { item ->
            CatchPreviewRow(
                item = item,
                resolveImageUrl = resolveImageUrl,
                onClick = { onOpenKnowledge(item.speciesId) },
                token = session?.token,
            )
        }
    }
}
