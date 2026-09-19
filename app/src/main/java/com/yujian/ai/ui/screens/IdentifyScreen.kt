package com.yujian.ai.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yujian.ai.R
import com.yujian.ai.media.RecognitionImageStore
import com.yujian.ai.model.SelectedImage
import com.yujian.ai.ui.home.HomeCameraButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val CAPTURE_FREEZE_MS = 420L

/**
 * Camera entry for both Empty and Normal Home.
 *
 * The preview is CameraX-backed; the normalized file is the only image passed
 * into the detector/classifier pipeline. Gallery selection uses the same
 * normalization path and enters recognition automatically.
 */
@Composable
fun IdentifyScreen(
    image: SelectedImage?,
    autoOpenGallery: Boolean = false,
    onBack: () -> Unit,
    onImageReady: (SelectedImage) -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val safeInsets = WindowInsets.safeDrawing.asPaddingValues()
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionRequested by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val cameraController = remember(context) {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
        }
    }

    DisposableEffect(view) {
        val controller = WindowCompat.getInsetsController(view, view)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        onDispose {
            controller.isAppearanceLightStatusBars = true
            controller.isAppearanceLightNavigationBars = true
        }
    }

    DisposableEffect(cameraController, lifecycleOwner, hasCameraPermission) {
        if (hasCameraPermission) {
            cameraController.bindToLifecycle(lifecycleOwner)
        }
        onDispose { cameraController.unbind() }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            error = "相机权限未开启，你仍然可以从相册选择照片"
        } else {
            error = null
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null || loading) return@rememberLauncherForActivityResult
        scope.launch {
            loading = true
            error = null
            runCatching {
                RecognitionImageStore.normalize(context, uri, "gallery")
            }.onSuccess { selected ->
                delay(CAPTURE_FREEZE_MS)
                onImageReady(selected)
            }.onFailure {
                error = it.message ?: "照片读取失败，请重新选择"
            }
            loading = false
        }
    }

    fun openGallery() {
        if (!loading) galleryLauncher.launch("image/*")
    }

    fun capture() {
        if (!hasCameraPermission) {
            if (!permissionRequested) {
                permissionRequested = true
                permissionLauncher.launch(Manifest.permission.CAMERA)
            } else {
                error = "请在系统设置中开启相机权限，或改用相册选择"
            }
            return
        }
        if (loading) return
        val target = RecognitionImageStore.createCameraTarget(context)
        loading = true
        error = null
        val output = ImageCapture.OutputFileOptions.Builder(target.file).build()
        cameraController.takePicture(
            output,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                    scope.launch {
                        runCatching {
                            RecognitionImageStore.normalizeCameraFile(context, target.file)
                        }.onSuccess { selected ->
                            delay(CAPTURE_FREEZE_MS)
                            onImageReady(selected)
                        }.onFailure {
                            error = it.message ?: "拍照文件无法解析，请重新拍摄"
                        }
                        loading = false
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    loading = false
                    error = exception.message ?: "没有完成拍照，请重新拍摄"
                }
            },
        )
    }

    LaunchedEffect(autoOpenGallery) {
        if (autoOpenGallery) openGallery()
        else if (!hasCameraPermission && !permissionRequested) {
            permissionRequested = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (image != null && loading) {
            Image(
                bitmap = image.bitmap.asImageBitmap(),
                contentDescription = "刚拍下的鱼获",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        } else if (hasCameraPermission) {
            AndroidView(
                factory = { context ->
                    PreviewView(context).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        controller = cameraController
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { it.controller = cameraController },
            )
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (loading) 0.30f else 0.12f)),
        )

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = safeInsets.calculateTopPadding() + 8.dp, start = 12.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = Color.White,
            )
        }

        if (!hasCameraPermission && !autoOpenGallery) {
            Button(
                onClick = {
                    permissionRequested = true
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                },
                modifier = Modifier.align(Alignment.Center),
            ) { Text("开启相机") }
        }

        error?.let {
            Text(
                text = it,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = safeInsets.calculateBottomPadding() + 132.dp),
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = safeInsets.calculateBottomPadding() + 18.dp),
            horizontalArrangement = Arrangement.spacedBy(34.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(54.dp)
                    .clickable(onClick = ::openGallery),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.album_icon_v12),
                    contentDescription = "从相册选择",
                    modifier = Modifier.size(30.dp),
                )
            }
            HomeCameraButton(onClick = ::capture)
        }
    }
}
