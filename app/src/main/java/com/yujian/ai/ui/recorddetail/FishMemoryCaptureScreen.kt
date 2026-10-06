package com.yujian.ai.ui.recorddetail

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.camera.video.AudioConfig
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.yujian.ai.catches.CatchLocalOverlayStore
import com.yujian.ai.ui.designsystem.color.YuJianColors
import com.yujian.ai.ui.designsystem.components.YuJianPrimaryButton
import com.yujian.ai.ui.designsystem.radius.YuJianRadius
import java.io.File
import java.util.concurrent.Executor

enum class FishMemoryCaptureMode(val routeValue: String, val mimeType: String) {
    PHOTO("photo", "image/jpeg"),
    VIDEO("video", "video/mp4");

    companion object {
        fun fromRoute(value: String?): FishMemoryCaptureMode =
            entries.firstOrNull { it.routeValue == value } ?: PHOTO
    }
}

@Composable
fun FishMemoryCaptureScreen(
    recordId: String,
    initialMode: FishMemoryCaptureMode,
    store: CatchLocalOverlayStore,
    onBack: () -> Unit,
    onCaptured: (File, String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor: Executor = remember(context) { ContextCompat.getMainExecutor(context) }
    var mode by remember(recordId) { mutableStateOf(initialMode) }
    var cameraGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var audioGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    var cameraReady by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    val activeRecording by rememberUpdatedState(recording)
    val cameraController = remember(context) {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(CameraController.IMAGE_CAPTURE or CameraController.VIDEO_CAPTURE)
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        cameraGranted = grants[Manifest.permission.CAMERA] == true || cameraGranted
        audioGranted = grants[Manifest.permission.RECORD_AUDIO] == true || audioGranted
        if (!cameraGranted) error = "需要相机权限才能为这条鱼获拍摄影像"
    }

    LaunchedEffect(mode, cameraGranted) {
        if (!cameraGranted) {
            val requested = buildList {
                add(Manifest.permission.CAMERA)
                if (mode == FishMemoryCaptureMode.VIDEO && !audioGranted) add(Manifest.permission.RECORD_AUDIO)
            }.toTypedArray()
            permissionLauncher.launch(requested)
        }
    }

    DisposableEffect(cameraController, lifecycleOwner, cameraGranted) {
        if (cameraGranted) {
            runCatching {
                cameraController.bindToLifecycle(lifecycleOwner)
                val initialization = cameraController.getInitializationFuture()
                initialization.addListener(
                    { cameraReady = runCatching { initialization.get(); cameraController.cameraInfo != null }.getOrDefault(false) },
                    executor,
                )
            }.onFailure {
                error = "相机暂时无法启动，请重试"
            }
        }
        onDispose {
            activeRecording?.stop()
            cameraController.unbind()
            cameraReady = false
        }
    }

    val safePadding = WindowInsets.safeDrawing.asPaddingValues()
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (cameraGranted) {
            AndroidView(
                factory = { viewContext ->
                    PreviewView(viewContext).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        controller = cameraController
                    }
                },
                update = { it.controller = cameraController },
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(
            Modifier.fillMaxSize().padding(
                top = safePadding.calculateTopPadding(),
                bottom = safePadding.calculateBottomPadding(),
            ),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                Modifier.fillMaxWidth().background(Color(0x66091A28)).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onBack, enabled = !isRecording, contentPadding = PaddingValues(8.dp)) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "返回", tint = Color.White)
                }
                Text(
                    if (mode == FishMemoryCaptureMode.PHOTO) "继续拍照" else "录制视频",
                    modifier = Modifier.weight(1f),
                    color = Color.White,
                    style = com.yujian.ai.ui.designsystem.typography.YuJianTypography.sectionTitle,
                )
            }

            Column(
                Modifier.fillMaxWidth().background(Color(0x99091A28)).padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                error?.let { Text(it, color = Color.White) }
                if (!cameraGranted) {
                    Text("请允许相机权限后继续", color = Color.White)
                    YuJianPrimaryButton(text = "允许相机", onClick = {
                        permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                    })
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { mode = FishMemoryCaptureMode.PHOTO }, enabled = !isRecording) {
                            Icon(Icons.Rounded.CameraAlt, contentDescription = null)
                            Text("PHOTO", modifier = Modifier.padding(start = 6.dp))
                        }
                        OutlinedButton(onClick = {
                            mode = FishMemoryCaptureMode.VIDEO
                            if (!audioGranted) permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                        }, enabled = !isRecording) {
                            Icon(Icons.Rounded.Videocam, contentDescription = null)
                            Text("VIDEO", modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                    Text("拍摄内容会保留在这条鱼获里", color = Color.White.copy(alpha = 0.86f))
                    Box(
                        Modifier.size(76.dp).clip(CircleShape)
                            .background(if (isRecording) Color(0xFFE4564B) else YuJianColors.MistWhite)
                            .clickable(enabled = cameraReady) {
                            error = null
                            if (mode == FishMemoryCaptureMode.PHOTO) {
                                val photo = store.newCaptureFile(recordId, FishMemoryCaptureMode.PHOTO.mimeType)
                                cameraController.takePicture(
                                    androidx.camera.core.ImageCapture.OutputFileOptions.Builder(photo).build(),
                                    executor,
                                    object : androidx.camera.core.ImageCapture.OnImageSavedCallback {
                                        override fun onImageSaved(results: androidx.camera.core.ImageCapture.OutputFileResults) {
                                            onCaptured(photo, FishMemoryCaptureMode.PHOTO.mimeType)
                                        }

                                        override fun onError(exception: androidx.camera.core.ImageCaptureException) {
                                            photo.delete()
                                            error = "照片没有保存成功，请重试"
                                        }
                                    },
                                )
                            } else if (isRecording) {
                                recording?.stop()
                            } else {
                                val video = store.newCaptureFile(recordId, FishMemoryCaptureMode.VIDEO.mimeType)
                                runCatching {
                                    recording = cameraController.startRecording(
                                        FileOutputOptions.Builder(video).build(),
                                        AudioConfig.create(audioGranted),
                                        executor,
                                    ) { event ->
                                        when (event) {
                                            is VideoRecordEvent.Start -> isRecording = true
                                            is VideoRecordEvent.Finalize -> {
                                                isRecording = false
                                                recording = null
                                                if (event.hasError()) {
                                                    video.delete()
                                                    error = "视频没有保存成功，请重试"
                                                } else {
                                                    onCaptured(video, FishMemoryCaptureMode.VIDEO.mimeType)
                                                }
                                            }
                                        }
                                    }
                                }.onFailure {
                                    video.delete()
                                    error = "视频暂时无法开始录制"
                                }
                            }
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (mode == FishMemoryCaptureMode.PHOTO) {
                            Icon(Icons.Rounded.CameraAlt, contentDescription = "拍照", tint = Color(0xFF12324B), modifier = Modifier.size(32.dp))
                        } else {
                            Box(Modifier.size(if (isRecording) 28.dp else 32.dp).clip(CircleShape).background(Color(0xFFE4564B)))
                        }
                    }
                }
            }
        }
    }
}
