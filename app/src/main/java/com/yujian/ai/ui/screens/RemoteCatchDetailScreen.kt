package com.yujian.ai.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yujian.ai.R
import com.yujian.ai.catches.RemoteCatch
import com.yujian.ai.ui.components.RemoteImage

private val Ink = Color(0xFF18324A)
private val Muted = Color(0xCC18324A)

@Composable
fun RemoteCatchDetailScreen(
    catch: RemoteCatch,
    imageUrl: String?,
    accessToken: String,
    onBack: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F1E8))
            .padding(WindowInsets.safeDrawing.asPaddingValues())
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "‹",
                color = Ink,
                fontSize = 32.sp,
                modifier = Modifier.clickable(onClick = onBack).padding(horizontal = 8.dp),
            )
            Text("鱼获详情", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 18.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color.White.copy(alpha = 0.24f)),
            contentAlignment = Alignment.Center,
        ) {
            RemoteImage(
                url = imageUrl,
                authToken = accessToken,
                modifier = Modifier.fillMaxSize().padding(10.dp),
                contentDescription = "${catch.speciesName} 鱼获照片",
                contentScale = ContentScale.Fit,
            ) {
                Image(
                    painter = painterResource(R.drawable.image_error_v12),
                    contentDescription = "图片加载失败",
                    modifier = Modifier.size(56.dp),
                )
            }
        }
        Text(catch.speciesName, color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Medium)
        detailMeasurement(catch)?.let { Text(it, color = Ink, fontSize = 16.sp, modifier = Modifier.padding(top = 6.dp)) }
        detailMeta(catch)?.let { Text(it, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
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
