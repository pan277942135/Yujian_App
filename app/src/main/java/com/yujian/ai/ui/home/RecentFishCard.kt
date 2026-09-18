package com.yujian.ai.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yujian.ai.R
import com.yujian.ai.catches.RemoteCatch
import com.yujian.ai.ui.components.AssetImage
import com.yujian.ai.ui.components.RemoteImage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val FISH_CARD_ROOT = "home_normal_v1_2/assets/fish_card"
private val CardShape = RoundedCornerShape(28.dp)

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
                .clip(CardShape),
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
                contentDescription = "${item.speciesName} 鱼获照片",
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
                Text(item.speciesName, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                displayMeasurement(item)?.let { value ->
                    Text(value, color = Color.White.copy(alpha = 0.94f), fontSize = 15.sp, modifier = Modifier.padding(top = 5.dp))
                }
                formatCatchMeta(item)?.let { value ->
                    Text(value, color = Color.White.copy(alpha = 0.88f), fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
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
    val time = item.capturedAt.ifBlank { item.createdAt }.takeIf(String::isNotBlank)?.let(::formatRelativeTime)
    val location = item.location?.trim()?.takeIf(String::isNotBlank)
    return listOfNotNull(time, location).joinToString(" · ").takeIf(String::isNotBlank)
}

private fun formatNumber(value: Float): String = "%.2f".format(Locale.US, value).trimEnd('0').trimEnd('.')

private fun formatRelativeTime(value: String): String {
    val date = parseCatchDate(value) ?: return value.take(16).replace('T', ' ')
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply { time = date }
    val time = SimpleDateFormat("HH:mm", Locale.US).format(date)
    return when {
        now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR) -> "今天 $time"
        isYesterday(now, target) -> "昨天 $time"
        else -> SimpleDateFormat("MM月dd日 HH:mm", Locale.CHINA).format(date)
    }
}

private fun isYesterday(now: Calendar, target: Calendar): Boolean {
    val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    return yesterday.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
        yesterday.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
}

private fun parseCatchDate(value: String): Date? = runCatching {
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).parse(value)
}.getOrNull()
