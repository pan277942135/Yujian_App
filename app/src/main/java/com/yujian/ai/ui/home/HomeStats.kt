package com.yujian.ai.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yujian.ai.catches.CatchStatistics
import com.yujian.ai.catches.RemoteCatch

private val Ink = Color(0xFF18324A)
private val Muted = Color(0xCC18324A)
private val Glass = Color(0x45FFFFFF)

@Composable
fun HomeStats(
    statistics: CatchStatistics,
    catches: List<RemoteCatch>,
    onSpeciesClick: () -> Unit,
    onCatchesClick: () -> Unit,
    onRecordDaysClick: () -> Unit,
) {
    val days = remember(catches) {
        catches.map { it.createdAt.take(10) }.filter(String::isNotBlank).distinct().size
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 18.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Glass)
            .padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        HomeStat("${statistics.speciesCount}", "鱼种", onSpeciesClick)
        HomeStat("${statistics.totalCatches}", "鱼获", onCatchesClick)
        HomeStat("$days", "记录天数", onRecordDaysClick)
    }
}

@Composable
private fun HomeStat(value: String, label: String, onClick: () -> Unit) {
    Column(
        Modifier.width(96.dp).clickable(onClick = onClick).padding(vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = Ink, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
    }
}
