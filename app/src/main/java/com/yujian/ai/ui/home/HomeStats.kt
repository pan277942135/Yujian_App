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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yujian.ai.catches.CatchStatistics
import com.yujian.ai.catches.RemoteCatch
import com.yujian.ai.ui.designsystem.color.YuJianColors
import com.yujian.ai.ui.designsystem.spacing.YuJianSpacing
import com.yujian.ai.ui.designsystem.typography.YuJianTypography
import com.yujian.ai.presentation.PresentationSanitizer

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
    val validCatches = validHomeRecords(catches)
    val localSpeciesCount = validCatches
        .map { it.speciesId.ifBlank { it.speciesName } }
        .filter(String::isNotBlank)
        .distinct()
        .size
    val recordDays = validCatches
        .mapNotNull(::catchDayKey)
        .distinct()
        .size

    return HomeStatValues(
        speciesCount = statistics.speciesCount.takeIf { it > 0 } ?: localSpeciesCount,
        catchCount = statistics.totalCatches.takeIf { it > 0 } ?: validCatches.size,
        recordDays = recordDays,
    )
}

@Composable
internal fun HomeStats(
    statistics: CatchStatistics,
    catches: List<RemoteCatch>,
    onSpeciesClick: () -> Unit,
    onCatchesClick: () -> Unit,
    isResolving: Boolean = false,
    dividerHeight: Dp = YuJianSpacing.xl,
    dividerWidth: Dp = 1.dp,
    horizontalPadding: Dp = YuJianSpacing.lg,
    verticalPadding: Dp = YuJianSpacing.xs,
    typography: NormalHomeTypographyContract? = null,
) {
    val values = remember(statistics, catches) { resolveHomeStatValues(statistics, catches) }
    val density = LocalDensity.current
    val type = typography ?: remember(density.density) { normalHomeTypographyContract(1f, density.density) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HomeStat(
            Modifier.weight(1f),
            if (isResolving) "—" else values.speciesCount.toString(),
            "鱼种",
            onClick = onSpeciesClick.takeUnless { isResolving },
            semanticsTag = "normal-home-stat-species",
            verticalPadding = verticalPadding,
            typography = type,
        )
        Box(
            Modifier
                .width(dividerWidth)
                .height(dividerHeight)
                .background(YuJianColors.MistBlueGray.copy(alpha = 0.32f))
                .testTag("normal-home-stat-divider-1"),
        )
        HomeStat(
            Modifier.weight(1f),
            if (isResolving) "—" else values.catchCount.toString(),
            "鱼获",
            onClick = onCatchesClick.takeUnless { isResolving },
            semanticsTag = "normal-home-stat-catches",
            verticalPadding = verticalPadding,
            typography = type,
        )
        Box(
            Modifier
                .width(dividerWidth)
                .height(dividerHeight)
                .background(YuJianColors.MistBlueGray.copy(alpha = 0.32f))
                .testTag("normal-home-stat-divider-2"),
        )
        HomeStat(
            Modifier.weight(1f),
            if (isResolving) "—" else values.recordDays.toString(),
            "记录天数",
            onClick = null,
            semanticsTag = "normal-home-stat-record-days",
            verticalPadding = verticalPadding,
            typography = type,
        )
    }
}

@Composable
internal fun HomeStat(
    modifier: Modifier,
    value: String,
    label: String,
    onClick: (() -> Unit)?,
    semanticsTag: String,
    verticalPadding: Dp,
    typography: NormalHomeTypographyContract,
) {
    Column(
        modifier = (if (onClick == null) modifier else modifier.clickable(onClick = onClick))
            .padding(vertical = verticalPadding)
            .testTag(semanticsTag),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = YuJianTypography.dataNumber.copy(
                color = YuJianColors.TextPrimary,
                fontSize = typography.statValue.fontSize,
                lineHeight = typography.statValue.lineHeight,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("$semanticsTag-value"),
        )
        Text(
            text = label,
            style = YuJianTypography.caption.copy(
                color = YuJianColors.TextPrimary.copy(alpha = 0.78f),
                fontSize = typography.statLabel.fontSize,
                lineHeight = typography.statLabel.lineHeight,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("$semanticsTag-label"),
        )
    }
}

private fun catchDayKey(catch: RemoteCatch): String? {
    return PresentationSanitizer.dateKey(catch.capturedAt, catch.createdAt)
}
