package com.yujian.ai.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.core.content.ContextCompat
import com.yujian.ai.media.RecognitionImageStore
import com.yujian.ai.model.SelectedImage
import com.yujian.ai.ui.components.FishIllustration
import com.yujian.ai.ui.components.YujianTopBar
import com.yujian.ai.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun IdentifyScreen(
    image: SelectedImage?,
    onBack: () -> Unit,
    onImageSelected: (SelectedImage) -> Unit,
    onStartRecognition: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cameraTarget by remember { mutableStateOf<RecognitionImageStore.CameraTarget?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val target = cameraTarget
        if (success && target != null) {
            scope.launch {
                loading = true
                error = null
                runCatching { RecognitionImageStore.normalizeCameraFile(context, target.file) }
                    .onSuccess(onImageSelected)
                    .onFailure { error = it.message ?: "照片读取失败" }
                loading = false
            }
        } else if (!success) {
            error = "未完成拍照，请重新拍摄"
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            error = null
            val target = RecognitionImageStore.createCameraTarget(context)
            cameraTarget = target
            cameraLauncher.launch(target.uri)
        } else {
            error = "需要相机权限才能拍照识鱼，也可以直接从相册选择照片"
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            loading = true
            error = null
            runCatching { RecognitionImageStore.normalize(context, uri, "gallery") }
                .onSuccess(onImageSelected)
                .onFailure { error = it.message ?: "照片读取失败" }
            loading = false
        }
    }

    Column(Modifier.fillMaxSize().background(WarmBackground)) {
        YujianTopBar(title = "拍照识鱼", subtitle = "让鱼体尽量完整地进入框内", onBack = onBack)
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(28.dp)).background(Color(0xFF0E3337)),
        ) {
            if (image != null) {
                Image(image.bitmap.asImageBitmap(), "待识别鱼获照片", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .18f)))
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    FishIllustration(size = 190.dp, bodyColor = SoftWater.copy(alpha = .86f))
                }
            }
            Box(
                Modifier.align(Alignment.Center).fillMaxWidth(.78f).height(270.dp)
                    .border(2.dp, Color(0xFFA7D8CE), RoundedCornerShape(24.dp)),
            )
            Box(
                Modifier.align(Alignment.TopStart).padding(18.dp)
                    .background(Color.White.copy(alpha = .14f), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text(
                    text = if (loading) "正在准备照片" else if (image == null) "准备取景" else "照片已选好",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Column(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = "把鱼放在框内", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "鱼体完整 · 光线充足 · 少遮挡",
                    color = Color.White.copy(alpha = .72f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
        }
        Text(
            text = error ?: if (image == null) "拍清楚一点，我会认得更准" else "照片准备好了，可以开始认识这条鱼",
            color = if (error == null) MutedInk else MaterialTheme.colorScheme.error,
            fontSize = 13.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp),
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = { galleryLauncher.launch("image/*") },
                enabled = !loading,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(26.dp),
            ) {
                Icon(Icons.Rounded.Collections, null, tint = WaterTeal)
                Text(text = "相册", color = DeepInk, modifier = Modifier.padding(start = 8.dp))
            }
            Button(
                onClick = {
                    error = null
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        val target = RecognitionImageStore.createCameraTarget(context)
                        cameraTarget = target
                        cameraLauncher.launch(target.uri)
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                enabled = !loading,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SoftWater, contentColor = WaterTeal),
            ) {
                Icon(Icons.Rounded.PhotoCamera, null)
                Text(text = "拍照", modifier = Modifier.padding(start = 8.dp), fontWeight = FontWeight.SemiBold)
            }
        }
        Button(
            onClick = onStartRecognition,
            enabled = image != null && !loading,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(54.dp),
            shape = RoundedCornerShape(27.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = WaterTeal,
                disabledContainerColor = WaterTeal.copy(alpha = .28f),
            ),
        ) {
            Box(
                Modifier.size(22.dp).background(Color.White.copy(alpha = .18f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "✦", color = Color.White, fontSize = 12.sp)
            }
            Text(
                text = "开始识别",
                modifier = Modifier.padding(start = 10.dp),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(20.dp))
    }
}
