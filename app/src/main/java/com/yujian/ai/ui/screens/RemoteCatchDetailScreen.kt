package com.yujian.ai.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yujian.ai.R
import com.yujian.ai.catches.BsideStatus
import com.yujian.ai.catches.RemoteCatch
import com.yujian.ai.ui.components.RemoteImage
import kotlinx.coroutines.delay

private val Ink = Color(0xFF18324A)
private val Muted = Color(0xCC18324A)
private val BsideBorder = Color(0xFF9DBAAF)

@Composable
fun RemoteCatchDetailScreen(
    catch: RemoteCatch,
    imageUrl: String?,
    bsideUrl: String?,
    accessToken: String,
    onBack: () -> Unit,
    onBsideAction: (() -> Unit)? = null,
    onPollBside: (suspend () -> Boolean)? = null,
) {
    var flipped by remember(catch.id, bsideUrl) { mutableStateOf(false) }
    var pollTimedOut by remember(catch.id) { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = tween(durationMillis = 700),
        label = "fish-bside-card-flip",
    )

    LaunchedEffect(catch.id, catch.bsideStatus, bsideUrl) {
        if (catch.bsideStatus == BsideStatus.READY && !bsideUrl.isNullOrBlank()) {
            delay(500)
            flipped = true
        } else if (catch.bsideStatus != BsideStatus.READY) {
            flipped = false
        }
    }
    LaunchedEffect(catch.id, catch.bsideStatus) {
        if (catch.bsideStatus != BsideStatus.GENERATING || onPollBside == null) return@LaunchedEffect
        pollTimedOut = false
        repeat(120) {
            delay(5_000)
            if (onPollBside()) return@LaunchedEffect
        }
        pollTimedOut = true
    }

    Column(
        Modifier.fillMaxSize().background(Color(0xFFF5F1E8))
            .padding(WindowInsets.safeDrawing.asPaddingValues()).padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = Ink, fontSize = 32.sp, modifier = Modifier.clickable(onClick = onBack).padding(horizontal = 8.dp))
            Text("鱼获详情", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        }
        Box(
            Modifier.fillMaxWidth().weight(1f).padding(vertical = 18.dp).clip(RoundedCornerShape(28.dp))
                .background(Color.White.copy(alpha = 0.24f)),
            contentAlignment = Alignment.Center,
        ) {
            RemoteImage(
                url = imageUrl, authToken = accessToken,
                modifier = Modifier.fillMaxSize().padding(10.dp).graphicsLayer { rotationY = rotation; cameraDistance = 12f * density },
                contentDescription = "${catch.speciesName} 鱼获照片", contentScale = ContentScale.Fit,
            ) { Image(painter = painterResource(R.drawable.image_error_v12), contentDescription = "图片加载失败", modifier = Modifier.size(56.dp)) }
            if (!bsideUrl.isNullOrBlank()) {
                RemoteImage(
                    url = bsideUrl, authToken = accessToken,
                    modifier = Modifier.fillMaxSize().padding(10.dp).graphicsLayer { rotationY = rotation - 180f; cameraDistance = 12f * density },
                    contentDescription = "${catch.speciesName} AI 渔境卡", contentScale = ContentScale.Fit,
                )
            }
        }
        Text(catch.speciesName, color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Medium)
        detailMeasurement(catch)?.let { Text(it, color = Ink, fontSize = 16.sp, modifier = Modifier.padding(top = 6.dp)) }
        detailMeta(catch)?.let { Text(it, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
        when (catch.bsideStatus) {
            BsideStatus.NONE, BsideStatus.FAILED -> if (onBsideAction != null) {
                OutlinedButton(onClick = onBsideAction, colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink), border = BorderStroke(1.dp, BsideBorder), modifier = Modifier.padding(top = 14.dp)) {
                    Text(if (catch.bsideStatus == BsideStatus.FAILED) "重新生成" else "提取渔获", fontSize = 13.sp)
                }
            }
            BsideStatus.GENERATING -> {
                Text("正在生成你的渔获卡...", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp))
                if (pollTimedOut) Text("生成时间较长，请稍后查看", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
            BsideStatus.READY -> Text("AI 渔境卡已生成", color = Color(0xFF377765), fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp))
        }
    }
}

private fun detailMeasurement(catch: RemoteCatch): String? = listOfNotNull(
    catch.lengthCm?.takeIf { it > 0f }?.let { "${formatDetailNumber(it)} cm" },
    catch.weightKg?.takeIf { it > 0f }?.let { "${formatDetailNumber(it)} kg" },
).joinToString(" · ").takeIf(String::isNotBlank)

private fun detailMeta(catch: RemoteCatch): String? = listOfNotNull(
    catch.capturedAt.ifBlank { catch.createdAt }.takeIf(String::isNotBlank),
    catch.location?.takeIf(String::isNotBlank),
).joinToString(" · ").takeIf(String::isNotBlank)

private fun formatDetailNumber(value: Float): String = "%.2f".format(java.util.Locale.US, value).trimEnd('0').trimEnd('.')
