package com.yujian.ai.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.yujian.ai.R
import com.yujian.ai.catches.RemoteCatch
import com.yujian.ai.ui.components.AssetImage
import com.yujian.ai.ui.components.RemoteImage
import com.yujian.ai.ui.designsystem.color.YuJianColors
import com.yujian.ai.ui.designsystem.radius.YuJianRadius
import com.yujian.ai.ui.designsystem.typography.YuJianTypography
import com.yujian.ai.presentation.PresentationSanitizer
import com.yujian.ai.presentation.presentationSpeciesName
import com.yujian.ai.presentation.sanitizeOptionalText
import java.util.Locale

private const val FISH_CARD_ROOT = "home_normal_v1_2/assets/fish_card"
@Composable
fun RecentFishCard(
    item: RemoteCatch,
    imageUrl: String?,
    accessToken: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .clickable(onClick = onClick),
    ) {
        AssetImage(
            "$FISH_CARD_ROOT/fish_card_shadow.png",
            Modifier.fillMaxSize().alpha(0.48f),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
        )
        Box(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 2.dp, vertical = 4.dp)
                .clip(YuJianRadius.glassCard),
        ) {
            RemoteImage(
                url = imageUrl,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayerForPhoto(1.08f, 0.22f)
                    .blur(18.dp),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                authToken = accessToken,
            )
            RemoteImage(
                url = imageUrl,
                modifier = Modifier.fillMaxSize().padding(16.dp),
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
            AssetImage(
                "$FISH_CARD_ROOT/fish_card_gradient.png",
                Modifier.fillMaxSize(),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
            )
            Column(
                Modifier.align(Alignment.BottomStart).padding(horizontal = 24.dp, vertical = 22.dp),
            ) {
                Text(
                    text = presentationSpeciesName(item.speciesName),
                    style = YuJianTypography.sectionTitle.copy(color = YuJianColors.OnDark),
                )
                displayMeasurement(item)?.let { value ->
                    Text(
                        text = value,
                        style = YuJianTypography.body.copy(color = YuJianColors.OnDark.copy(alpha = 0.94f)),
                        modifier = Modifier.padding(top = 5.dp),
                    )
                }
                formatCatchMeta(item)?.let { value ->
                    Text(
                        text = value,
                        style = YuJianTypography.caption.copy(color = YuJianColors.OnDark.copy(alpha = 0.88f)),
                        modifier = Modifier.padding(top = 5.dp),
                    )
                }
            }
        }
        AssetImage(
            "$FISH_CARD_ROOT/fish_card_outline.png",
            Modifier.fillMaxSize().alpha(0.72f),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
        )
    }
}

private fun Modifier.graphicsLayerForPhoto(scale: Float, alpha: Float): Modifier = graphicsLayer {
    scaleX = scale
    scaleY = scale
    this.alpha = alpha
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
