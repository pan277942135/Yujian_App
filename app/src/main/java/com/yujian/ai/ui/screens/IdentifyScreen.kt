package com.yujian.ai.ui.screens

import android.Manifest
import android.app.Activity
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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.yujian.ai.ui.identify.RecognitionContentScaleMode
import com.yujian.ai.ui.identify.RecognitionSourcePhoto
import com.yujian.ai.ui.identify.calculateRecognitionImageTransform
import kotlinx.coroutines.launch

private const val CameraPermissionHint = "相机权限未开启，你仍然可以从相册选择照片"
private const val CameraUnavailableHint = "相机暂不可用，请重试或从相册选择照片"
private const val CameraStartFailureHint = "无法启动拍照，请重试或从相册选择照片"
private const val CameraCaptureFailureHint = "没有完成拍照，请重试或从相册选择照片"
private const val CameraImageFailureHint = "拍照文件无法处理，请重新拍摄"
private const val GalleryUnavailableHint = "暂时无法打开相册，请重试"
private const val GalleryImageFailureHint = "照片读取失败，请重新选择"

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
    var cameraReady by remember { mutableStateOf(false) }
    var cameraRetry by remember { mutableStateOf(0) }
    // This local handoff is set before navigation, so CameraX and its controls
    // cannot share a frame with the Recognition processing scene.
    var handoffImage by remember { mutableStateOf<SelectedImage?>(null) }

    val cameraController = remember(context) {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
        }
    }

    DisposableEffect(view) {
        val activity = view.context as? Activity
        if (activity == null) {
            onDispose { }
        } else {
            val controller = WindowCompat.getInsetsController(activity.window, view)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
            onDispose {
                controller.isAppearanceLightStatusBars = true
                controller.isAppearanceLightNavigationBars = true
            }
        }
    }

    DisposableEffect(cameraController, lifecycleOwner, hasCameraPermission, cameraRetry) {
        if (hasCameraPermission) {
            runCatching { cameraController.bindToLifecycle(lifecycleOwner) }
                .onSuccess {
                    cameraReady = true
                    if (error == CameraUnavailableHint) error = null
                }
                .onFailure {
                    cameraReady = false
                    error = CameraUnavailableHint
                }
        } else cameraReady = false
        onDispose { cameraController.unbind() }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            error = CameraPermissionHint
        } else {
            error = null
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) {
            loading = false
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            error = null
            runCatching {
                RecognitionImageStore.normalize(context, uri, "gallery")
            }.onSuccess { selected ->
                // Commit the new normalized bitmap before navigation. The
                // camera/previous selection must never leak into the first
                // Recognition frame.
                handoffImage = selected
                onImageReady(selected)
            }.onFailure {
                error = GalleryImageFailureHint
            }
            loading = false
        }
    }

    fun openGallery() {
        if (!loading) {
            loading = true
            error = null
            runCatching { galleryLauncher.launch("image/*") }
                .onFailure {
                    loading = false
                    error = GalleryUnavailableHint
                }
        }
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
        if (!cameraReady) {
            error = CameraUnavailableHint
            cameraRetry += 1
            return
        }
        error = null
        val targetResult = runCatching { RecognitionImageStore.createCameraTarget(context) }
        if (targetResult.isFailure) {
            error = CameraStartFailureHint
            return
        }
        val target = targetResult.getOrThrow()
        loading = true
        try {
            val output = ImageCapture.OutputFileOptions.Builder(target.file).build()
            cameraController.takePicture(
                output,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                        scope.launch {
                            val normalized = runCatching {
                                RecognitionImageStore.normalizeCameraFile(context, target.file)
                            }
                            target.file.delete()
                            normalized.onSuccess { selected ->
                                handoffImage = selected
                                onImageReady(selected)
                            }.onFailure {
                                error = CameraImageFailureHint
                            }
                            loading = false
                        }
                    }

                    @Suppress("UNUSED_PARAMETER")
                    override fun onError(exception: ImageCaptureException) {
                        target.file.delete()
                        loading = false
                        error = CameraCaptureFailureHint
                    }
                },
            )
        } catch (_: Exception) {
            target.file.delete()
            loading = false
            error = CameraStartFailureHint
        }
    }

    LaunchedEffect(autoOpenGallery) {
        if (autoOpenGallery) openGallery()
        else if (!hasCameraPermission && !permissionRequested) {
            permissionRequested = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val displayedPhoto = handoffImage
        if (displayedPhoto != null) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val transform = remember(displayedPhoto.imageId, maxWidth, maxHeight, density) {
                    calculateRecognitionImageTransform(
                        containerWidth = with(density) { maxWidth.toPx() },
                        containerHeight = with(density) { maxHeight.toPx() },
                        imageWidth = displayedPhoto.bitmap.width,
                        imageHeight = displayedPhoto.bitmap.height,
                        contentScaleMode = RecognitionContentScaleMode.CROP,
                    )
                }
                RecognitionSourcePhoto(
                    bitmap = displayedPhoto.bitmap.asImageBitmap(),
                    transform = transform,
                    contentDescription = "刚拍下的鱼获",
                )
            }
        } else if (loading) {
            // A neutral handoff frame prevents a stale CameraX frame or the
            // previous SelectedImage from flashing while the new gallery
            // image is normalized.
            Box(Modifier.fillMaxSize().background(Color.Black))
        } else if (hasCameraPermission && cameraReady) {
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

        if (handoffImage == null && !loading) {
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
        }

        if (handoffImage == null && !loading && !autoOpenGallery) {
            if (!hasCameraPermission) {
                Button(
                    onClick = {
                        permissionRequested = true
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    modifier = Modifier.align(Alignment.Center),
                ) { Text("开启相机") }
            } else if (!cameraReady) {
                Button(
                    onClick = {
                        error = null
                        cameraRetry += 1
                    },
                    modifier = Modifier.align(Alignment.Center),
                ) { Text("重试打开相机") }
            }
        }

        error?.takeIf { handoffImage == null && !loading }?.let {
            Text(
                text = it,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = safeInsets.calculateBottomPadding() + 132.dp),
            )
        }

        if (handoffImage == null && !loading) Row(
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
