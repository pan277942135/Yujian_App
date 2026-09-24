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
import androidx.compose.ui.unit.dp
import com.yujian.ai.catches.CatchStatistics
import com.yujian.ai.catches.RemoteCatch
import com.yujian.ai.ui.designsystem.color.YuJianColors
import com.yujian.ai.ui.designsystem.spacing.YuJianSpacing
import com.yujian.ai.ui.designsystem.typography.YuJianTypography
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class HomeStatValues(
    val speciesCount: Int,
    val catchCount: Int,
    val recordDays: Int,
)

/**
 * Server totals remain authoritative when available. The local catch archive is
 * the offline/guest fallback and provides the distinct record-day calculation.
 */
internal fun resolveHomeStatValues(
    statistics: CatchStatistics,
    catches: List<RemoteCatch>,
): HomeStatValues {
    val localSpeciesCount = catches
        .map { it.speciesId.ifBlank { it.speciesName } }
        .filter(String::isNotBlank)
        .distinct()
        .size
    val recordDays = catches
        .mapNotNull(::catchDayKey)
        .distinct()
        .size

    return HomeStatValues(
        speciesCount = statistics.speciesCount.takeIf { it > 0 } ?: localSpeciesCount,
        catchCount = statistics.totalCatches.takeIf { it > 0 } ?: catches.size,
        recordDays = recordDays,
    )
}

@Composable
fun HomeStats(
    statistics: CatchStatistics,
    catches: List<RemoteCatch>,
    onSpeciesClick: () -> Unit,
    onCatchesClick: () -> Unit,
    onRecordDaysClick: () -> Unit,
) {
    val values = remember(statistics, catches) { resolveHomeStatValues(statistics, catches) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = YuJianSpacing.lg),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HomeStat(Modifier.weight(1f), values.speciesCount.toString(), "鱼种", onSpeciesClick)
        Box(
            Modifier
                .width(1.dp)
                .height(YuJianSpacing.xl)
                .background(YuJianColors.MistBlueGray.copy(alpha = 0.32f)),
        )
        HomeStat(Modifier.weight(1f), values.catchCount.toString(), "鱼获", onCatchesClick)
        Box(
            Modifier
                .width(1.dp)
                .height(YuJianSpacing.xl)
                .background(YuJianColors.MistBlueGray.copy(alpha = 0.32f)),
        )
        HomeStat(Modifier.weight(1f), values.recordDays.toString(), "记录天数", onRecordDaysClick)
    }
}

@Composable
private fun HomeStat(modifier: Modifier, value: String, label: String, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = YuJianSpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = YuJianTypography.dataNumber.copy(color = YuJianColors.TextPrimary),
        )
        Text(
            text = label,
            style = YuJianTypography.caption.copy(color = YuJianColors.TextPrimary.copy(alpha = 0.78f)),
        )
    }
}

private fun catchDayKey(catch: RemoteCatch): String? {
    val value = catch.capturedAt.ifBlank { catch.createdAt }.trim()
    if (value.isBlank()) return null
    parseDate(value)?.let { date ->
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)
    }
    return value.take(10).takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }
}

private fun parseDate(value: String): Date? = runCatching {
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).parse(value)
}.getOrNull()
