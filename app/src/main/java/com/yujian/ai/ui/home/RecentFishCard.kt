package com.yujian.ai.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yujian.ai.R
import com.yujian.ai.catches.RemoteCatch
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
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { scaleX = 1.08f; scaleY = 1.08f; alpha = 0.22f }
                    .blur(18.dp),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                authToken = accessToken,
            )
            RemoteImage(
                url = imageUrl,
                modifier = Modifier.fillMaxSize(),
                contentDescription = "${presentationSpeciesName(item.speciesName)} 鱼获照片",
                contentScale = ContentScale.Fit,
                authToken = accessToken,
            ) {
                Image(
                    painter = painterResource(R.drawable.image_error_v12),
                    contentDescription = "图片加载失败",
                    modifier = Modifier.size(44.dp),
                )
            }
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
    )
    displayMeasurement(item)?.let { value ->
        Text(
            text = value,
            style = YuJianTypography.body.copy(
                color = YuJianColors.OnDark.copy(alpha = 0.94f),
                fontSize = 22.sp,
                lineHeight = 28.sp,
            ),
            modifier = Modifier.padding(top = 5.dp),
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
            modifier = Modifier.padding(top = 5.dp),
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
