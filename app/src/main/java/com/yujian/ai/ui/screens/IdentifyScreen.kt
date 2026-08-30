package com.yujian.ai.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yujian.ai.ui.components.FishIllustration
import com.yujian.ai.ui.components.YujianTopBar
import com.yujian.ai.ui.theme.DeepInk
import com.yujian.ai.ui.theme.MutedInk
import com.yujian.ai.ui.theme.SoftWater
import com.yujian.ai.ui.theme.WarmBackground
import com.yujian.ai.ui.theme.WaterTeal

@Composable
fun IdentifyScreen(
    image: Bitmap?,
    onBack: () -> Unit,
    onImageSelected: (Bitmap) -> Unit,
    onStartRecognition: () -> Unit,
) {
    val context = LocalContext.current
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        bitmap?.let(onImageSelected)
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val bitmap = runCatching {
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
            bitmap?.let(onImageSelected)
        }
    }

    Column(Modifier.fillMaxSize().background(WarmBackground)) {
        YujianTopBar(title = "拍照识鱼", subtitle = "让鱼体尽量完整地进入框内", onBack = onBack)

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF0E3337)),
        ) {
            if (image != null) {
                Image(
                    bitmap = image.asImageBitmap(),
                    contentDescription = "待识别鱼获照片",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .18f)))
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    FishIllustration(size = 190.dp, bodyColor = SoftWater.copy(alpha = .86f))
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(.78f)
                    .height(270.dp)
                    .border(2.dp, Color(0xFFA7D8CE), RoundedCornerShape(24.dp)),
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(18.dp)
                    .background(Color.White.copy(alpha = .14f), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text(if (image == null) "准备取景" else "照片已选好", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            Column(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("把鱼放在框内", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("鱼体完整 · 光线充足 · 少遮挡", color = Color.White.copy(alpha = .72f), fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp))
            }
        }

        Text(
            if (image == null) "拍清楚一点，我会认得更准" else "照片准备好了，可以开始认识这条鱼",
            color = MutedInk,
            fontSize = 13.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = { galleryLauncher.launch("image/*") },
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(26.dp),
            ) {
                Icon(Icons.Rounded.Collections, contentDescription = null, tint = WaterTeal)
                Text("相册", color = DeepInk, modifier = Modifier.padding(start = 8.dp))
            }
            Button(
                onClick = { cameraLauncher.launch(null) },
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SoftWater, contentColor = WaterTeal),
            ) {
                Icon(Icons.Rounded.PhotoCamera, contentDescription = null)
                Text("拍照", modifier = Modifier.padding(start = 8.dp), fontWeight = FontWeight.SemiBold)
            }
        }

        Button(
            onClick = onStartRecognition,
            enabled = image != null,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(54.dp),
            shape = RoundedCornerShape(27.dp),
            colors = ButtonDefaults.buttonColors(containerColor = WaterTeal, disabledContainerColor = WaterTeal.copy(alpha = .28f)),
        ) {
            Box(Modifier.size(22.dp).background(Color.White.copy(alpha = .18f), CircleShape), contentAlignment = Alignment.Center) {
                Text("✦", color = Color.White, fontSize = 12.sp)
            }
            Text("开始识别", modifier = Modifier.padding(start = 10.dp), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(20.dp))
    }
}
