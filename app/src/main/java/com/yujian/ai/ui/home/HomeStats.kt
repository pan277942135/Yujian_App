package com.yujian.ai.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yujian.ai.catches.CatchStatistics
import com.yujian.ai.catches.RemoteCatch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Ink = Color(0xFF18324A)
private val Muted = Color(0xCC18324A)
private val Divider = Color.White.copy(alpha = 0.38f)

@Composable
fun HomeStats(
    statistics: CatchStatistics,
    catches: List<RemoteCatch>,
    onSpeciesClick: () -> Unit,
    onCatchesClick: () -> Unit,
    onRecordDaysClick: () -> Unit,
) {
    val realSpeciesCount = remember(catches) {
        catches
            .map { it.speciesId.ifBlank { it.speciesName } }
            .filter(String::isNotBlank)
            .distinct()
            .size
    }
    val realCatchCount = catches.size
    val recordDays = remember(catches) {
        catches
            .mapNotNull { parseDate(it.capturedAt.ifBlank { it.createdAt }) }
            .map { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(it) }
            .distinct()
            .size
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HomeStat(Modifier.weight(1f), realSpeciesCount.toString(), "鱼种", onSpeciesClick)
        Box(Modifier.width(1.dp).height(34.dp).background(Divider))
        HomeStat(Modifier.weight(1f), realCatchCount.toString(), "鱼获", onCatchesClick)
        Box(Modifier.width(1.dp).height(34.dp).background(Divider))
        HomeStat(Modifier.weight(1f), recordDays.toString(), "记录天数", onRecordDaysClick)
    }
}

private fun parseDate(value: String): Date? = runCatching {
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).parse(value)
}.getOrNull()

@Composable
private fun HomeStat(modifier: Modifier, value: String, label: String, onClick: () -> Unit) {
    Column(
        modifier.clickable(onClick = onClick).padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = Ink, fontSize = 25.sp, fontWeight = FontWeight.Normal)
        Text(label, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
    }
}
