package com.yujian.ai.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yujian.ai.R
import com.yujian.ai.catches.RemoteCatch
import com.yujian.ai.ui.components.CatchHeroVariant
import com.yujian.ai.ui.components.RemoteImage
import com.yujian.ai.ui.designsystem.color.YuJianColors
import com.yujian.ai.ui.designsystem.components.YuJianHeroCard
import com.yujian.ai.ui.designsystem.components.YuJianHeroDecoration
import com.yujian.ai.ui.designsystem.components.YuJianHeroVariant
import com.yujian.ai.ui.designsystem.typography.YuJianTypography
import com.yujian.ai.presentation.PresentationSanitizer
import com.yujian.ai.presentation.presentationSpeciesName
import com.yujian.ai.presentation.sanitizeOptionalText
import java.util.Locale

/** Maps a Home catch record into the shared Hero family; frame, treatment and interaction live there. */
@Composable
internal fun RecentFishCard(
    item: RemoteCatch,
    imageUrl: String?,
    accessToken: String,
    onClick: () -> Unit,
    cardHeight: Dp,
    runtimeAssets: NormalHomeRuntimeAssets? = null,
) {
    YuJianHeroCard(
        title = presentationSpeciesName(item.speciesName),
        metadata = emptyList(),
        variant = YuJianHeroVariant.HOME,
        modifier = Modifier.fillMaxSize(),
        heightOverride = cardHeight,
        contentInsetHorizontal = 2.dp,
        contentInsetVertical = 4.dp,
        footerPadding = 12.dp,
        onClick = onClick,
        semanticsTag = "normal-home-catch-card-${item.id}",
        decoration = YuJianHeroDecoration(
            shadow = runtimeAssets?.fishCardShadow?.asImageBitmap(),
            gradient = runtimeAssets?.fishCardGradient?.asImageBitmap(),
            outline = runtimeAssets?.fishCardOutline?.asImageBitmap(),
        ),
        footerContent = {
            HomeCatchFooter(item)
        },
        media = {
            RemoteImage(
                url = imageUrl,
                modifier = Modifier.fillMaxSize().testTag("normal-home-catch-media-${item.id}"),
                contentDescription = "${presentationSpeciesName(item.speciesName)} 鱼获照片",
                contentScale = ContentScale.Fit,
                authToken = accessToken,
                adaptiveHeroVariant = CatchHeroVariant.HOME,
                placeholder = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(YuJianColors.MistBlueGray.copy(alpha = 0.24f))
                            .testTag("normal-home-media-fallback"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.image_error_v12),
                            contentDescription = null,
                            modifier = Modifier.size(44.dp).alpha(0.55f),
                        )
                    }
                },
            )
        },
    )
}

@Composable
private fun ColumnScope.HomeCatchFooter(item: RemoteCatch) {
    Text(
        text = presentationSpeciesName(item.speciesName),
        style = YuJianTypography.sectionTitle.copy(
            color = YuJianColors.OnDark,
            fontSize = 32.sp,
            lineHeight = 38.sp,
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.testTag("normal-home-catch-species-${item.id}"),
    )
    displayMeasurement(item)?.let { value ->
        Text(
            text = value,
            style = YuJianTypography.body.copy(
                color = YuJianColors.OnDark.copy(alpha = 0.94f),
                fontSize = 22.sp,
                lineHeight = 28.sp,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 5.dp).testTag("normal-home-catch-measurement-${item.id}"),
        )
    }
    formatCatchMeta(item)?.let { value ->
        Text(
            text = value,
            style = YuJianTypography.caption.copy(
                color = YuJianColors.OnDark.copy(alpha = 0.88f),
                fontSize = 16.sp,
                lineHeight = 22.sp,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(top = 5.dp)
                .testTag("normal-home-catch-meta-${item.id}"),
        )
    }
}

private fun displayMeasurement(item: RemoteCatch): String? = buildList {
    item.lengthCm?.takeIf { it > 0f }?.let { add("${formatNumber(it)} cm") }
    item.weightKg?.takeIf { it > 0f }?.let { add("${formatNumber(it)} kg") }
}.takeIf(List<String>::isNotEmpty)?.joinToString(" · ")

private fun formatCatchMeta(item: RemoteCatch): String? {
    val time = PresentationSanitizer.formatHomeTimestamp(item.capturedAt, item.createdAt)
    val location = sanitizeOptionalText(item.location)
    return listOfNotNull(time, location).joinToString(" · ").takeIf(String::isNotBlank)
}

private fun formatNumber(value: Float): String = "%.2f".format(Locale.US, value).trimEnd('0').trimEnd('.')
