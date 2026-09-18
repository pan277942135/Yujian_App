package com.yujian.ai.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yujian.ai.R
import com.yujian.ai.catches.RemoteCatch
import com.yujian.ai.ui.components.AssetImage
import com.yujian.ai.ui.components.FishIllustration
import com.yujian.ai.ui.components.RemoteImage

private val Ink = Color(0xFF18324A)

@Composable
fun RecentFishCard(
    item: RemoteCatch,
    imageUrl: String?,
    accessToken: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(350.dp)
            .padding(horizontal = 30.dp)
            .clickable(onClick = onClick),
    ) {
        AssetImage(
            "home_normal/fish_card/fish_card_shadow.png",
            Modifier.fillMaxSize().alpha(0.52f),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
        )
        Box(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 2.dp, vertical = 5.dp)
                .clip(RoundedCornerShape(28.dp)),
        ) {
            // Safe crop rule: a soft, enlarged background fills the card;
            // the actual photo stays fit-centered so the fish is not cut off.
            RemoteImage(
                imageUrl,
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { scaleX = 1.14f; scaleY = 1.14f; alpha = 0.34f }
                    .blur(22.dp),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                authToken = accessToken,
            )
            RemoteImage(
                imageUrl,
                Modifier.fillMaxSize().padding(16.dp),
                contentDescription = "${item.speciesName} 鱼获照片",
                contentScale = ContentScale.Fit,
                authToken = accessToken,
            ) {
                Image(
                    painterResource(R.drawable.fish_subject),
                    "鱼获主体",
                    Modifier.fillMaxSize().padding(24.dp),
                    contentScale = ContentScale.Fit,
                )
            }
            AssetImage(
                "home_normal/fish_card/fish_card_gradient.png",
                Modifier.fillMaxSize(),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 22.dp),
            ) {
                Text(item.speciesName.ifBlank { "未命名鱼获" }, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    item.capturedAt.ifBlank { item.createdAt.take(16) }.replace('T', ' '),
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
        }
        AssetImage(
            "home_normal/fish_card/fish_card_mask.png",
            Modifier.fillMaxSize().alpha(0.08f),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
        )
        AssetImage(
            "home_normal/fish_card/fish_card_outline.png",
            Modifier.fillMaxSize().alpha(0.72f),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
        )
    }
}
