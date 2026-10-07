package com.yujian.ai.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.BackHandler
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
import androidx.lifecycle.Observer
import com.yujian.ai.R
import com.yujian.ai.media.RecognitionImageStore
import com.yujian.ai.model.SelectedImage
import com.yujian.ai.ui.home.HomeCameraButton
import com.yujian.ai.ui.identify.RecognitionCameraCaptureContract
import com.yujian.ai.ui.identify.RecognitionCameraCaptureOutput
import com.yujian.ai.ui.identify.RecognitionCameraCaptureState
import com.yujian.ai.ui.identify.RecognitionContentScaleMode
import com.yujian.ai.ui.identify.RecognitionSourcePhoto
import com.yujian.ai.ui.identify.calculateRecognitionImageTransform
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

private const val CameraPermissionHint = "相机权限未开启，你仍然可以从相册选择照片"
private const val CameraUnavailableHint = "相机暂不可用，请重试或从相册选择照片"
private const val CameraStartFailureHint = "无法启动拍照，请重试或从相册选择照片"
private const val CameraCaptureFailureHint = "没有完成拍照，请重试或从相册选择照片"
private const val CameraImageFailureHint = "拍照文件无法处理，请重新拍摄"
private const val GalleryUnavailableHint = "暂时无法打开相册，请重试"
private const val GalleryImageFailureHint = "照片读取失败，请重新选择"
private const val RecognitionCameraCaptureTag = "RecognitionCameraCapture"

private fun diagnosticValue(value: Any?): String =
    value?.toString()?.replace(Regex("\\s+"), " ") ?: "none"

private fun logCapture(requestId: String, event: String, fields: String = "") {
    Log.d(
        RecognitionCameraCaptureTag,
        "request=$requestId event=$event${if (fields.isBlank()) "" else " $fields"}",
    )
}

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
    // Gallery selection/normalization may temporarily replace the preview.
    // Camera capture must not share this state: the PreviewView and controller
    // stay composed until CameraX delivers its terminal callback.
    var galleryBusy by remember { mutableStateOf(false) }
    var activeCaptureRequestId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var cameraBound by remember { mutableStateOf(false) }
    var imageCaptureReady by remember { mutableStateOf(false) }
    var previewStreaming by remember { mutableStateOf(false) }
    var captureState by remember { mutableStateOf(RecognitionCameraCaptureState.INITIALIZING) }
    var cameraRetry by remember { mutableStateOf(0) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    // This local handoff is set before navigation, so CameraX and its controls
    // cannot share a frame with the Recognition processing scene.
    var handoffImage by remember { mutableStateOf<SelectedImage?>(null) }

    val cameraController = remember(context) {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
        }
    }

    fun imageCaptureInstanceReady(): Boolean = imageCaptureReady && runCatching {
        cameraController.isImageCaptureEnabled && cameraController.cameraInfo != null
    }.getOrDefault(false)

    fun cameraCanCapture(): Boolean =
        hasCameraPermission && cameraBound && previewStreaming && imageCaptureInstanceReady()

    fun syncCaptureState() {
        if (captureState == RecognitionCameraCaptureState.CAPTURING ||
            captureState == RecognitionCameraCaptureState.SUCCESS
        ) {
            return
        }
        captureState = when {
            cameraCanCapture() && captureState == RecognitionCameraCaptureState.ERROR ->
                RecognitionCameraCaptureState.ERROR
            cameraCanCapture() -> RecognitionCameraCaptureState.READY
            else -> RecognitionCameraCaptureState.INITIALIZING
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
        cameraBound = false
        imageCaptureReady = false
        previewStreaming = false
        captureState = RecognitionCameraCaptureState.INITIALIZING
        if (hasCameraPermission) {
            val initializationFuture = cameraController.getInitializationFuture()
            initializationFuture.addListener(
                {
                    val initialized = runCatching {
                        initializationFuture.get()
                        true
                    }.getOrElse { initializationError ->
                        Log.e(
                            RecognitionCameraCaptureTag,
                            "event=camera_initialization_failed " +
                                "cause_class=${diagnosticValue(initializationError::class.simpleName)} " +
                                "cause_message=${diagnosticValue(initializationError.message)}",
                        )
                        false
                    }
                    imageCaptureReady = initialized && runCatching {
                        cameraController.isImageCaptureEnabled
                    }.getOrDefault(false)
                    if (!imageCaptureReady) {
                        captureState = RecognitionCameraCaptureState.ERROR
                        error = CameraUnavailableHint
                    } else {
                        syncCaptureState()
                    }
                },
                ContextCompat.getMainExecutor(context),
            )
            runCatching {
                cameraController.bindToLifecycle(lifecycleOwner)
                cameraBound = true
                syncCaptureState()
            }
                .onSuccess {
                    logCapture(
                        "camera-bind",
                        "camera_provider_bound",
                        "bound=true lifecycle_state=${lifecycleOwner.lifecycle.currentState.name}",
                    )
                }
                .onFailure { bindError ->
                    cameraBound = false
                    captureState = RecognitionCameraCaptureState.ERROR
                    error = CameraUnavailableHint
                    Log.e(
                        RecognitionCameraCaptureTag,
                        "event=camera_bind_failed " +
                            "cause_class=${diagnosticValue(bindError::class.simpleName)} " +
                            "cause_message=${diagnosticValue(bindError.message)}",
                    )
                }
        }
        onDispose {
            logCapture(
                activeCaptureRequestId ?: "camera-controller",
                "camera_controller_dispose",
                "camera_provider_bound=$cameraBound preview_attached=${previewView?.isAttachedToWindow == true} " +
                    "capture_state=${captureState.name} " +
                    "active_capture_request_id=${diagnosticValue(activeCaptureRequestId)} " +
                    "teardown_during_capture=${captureState == RecognitionCameraCaptureState.CAPTURING && activeCaptureRequestId != null}",
            )
            cameraController.unbind()
        }
    }

    DisposableEffect(previewView, lifecycleOwner) {
        val currentPreview = previewView
        if (currentPreview == null) {
            onDispose { }
        } else {
            fun logSurfaceState(attached: Boolean) {
                logCapture(
                    activeCaptureRequestId ?: "camera-preview",
                    "capture_surface_state",
                    "preview_attached=$attached controller_attached=${currentPreview.controller === cameraController} " +
                        "capture_state=${captureState.name} " +
                        "active_capture_request_id=${diagnosticValue(activeCaptureRequestId)} " +
                        "lifecycle_state=${lifecycleOwner.lifecycle.currentState.name}",
                )
            }
            val attachListener = object : android.view.View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: android.view.View) = logSurfaceState(true)
                override fun onViewDetachedFromWindow(view: android.view.View) = logSurfaceState(false)
            }
            currentPreview.addOnAttachStateChangeListener(attachListener)
            if (currentPreview.isAttachedToWindow) logSurfaceState(true)
            val observer = Observer<PreviewView.StreamState> { streamState ->
                previewStreaming = streamState == PreviewView.StreamState.STREAMING
                logCapture(
                    "preview-state",
                    "preview_stream_state",
                    "state=${streamState.name} camera_provider_bound=$cameraBound " +
                        "image_capture_ready=${imageCaptureInstanceReady()}",
                )
                syncCaptureState()
            }
            currentPreview.previewStreamState.observe(lifecycleOwner, observer)
            onDispose {
                currentPreview.previewStreamState.removeObserver(observer)
                currentPreview.removeOnAttachStateChangeListener(attachListener)
            }
        }
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
            galleryBusy = false
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
            galleryBusy = false
        }
    }

    fun openGallery() {
        if (!galleryBusy && captureState != RecognitionCameraCaptureState.CAPTURING) {
            galleryBusy = true
            error = null
            runCatching { galleryLauncher.launch("image/*") }
                .onFailure {
                    galleryBusy = false
                    error = GalleryUnavailableHint
                }
        }
    }

    fun capture() {
        val requestId = "cap_${UUID.randomUUID()}"
        val captureButtonEnabled = !galleryBusy && cameraCanCapture() &&
            RecognitionCameraCaptureContract.canStartCapture(captureState)
        if (!hasCameraPermission) {
            logCapture(requestId, "capture_blocked", "reason=permission_denied capture_button_enabled=false")
            if (!permissionRequested) {
                permissionRequested = true
                permissionLauncher.launch(Manifest.permission.CAMERA)
            } else {
                error = "请在系统设置中开启相机权限，或改用相册选择"
            }
            return
        }
        if (galleryBusy || captureState == RecognitionCameraCaptureState.CAPTURING) {
            logCapture(requestId, "capture_blocked", "reason=already_in_flight capture_button_enabled=false")
            return
        }
        val capturingState = RecognitionCameraCaptureContract.beginCapture(captureState)
        if (!captureButtonEnabled || capturingState == null) {
            logCapture(
                requestId,
                "capture_blocked",
                "reason=not_ready camera_provider_bound=$cameraBound " +
                    "preview_bound=$previewStreaming " +
                    "image_capture_ready=${imageCaptureInstanceReady()} " +
                    "lifecycle_state=${lifecycleOwner.lifecycle.currentState.name} " +
                    "capture_state=${captureState.name} capture_button_enabled=false",
            )
            error = CameraUnavailableHint
            return
        }
        captureState = capturingState
        error = null
        val targetResult = runCatching { RecognitionImageStore.createCameraTarget(context) }
        if (targetResult.isFailure) {
            captureState = RecognitionCameraCaptureContract.completeError()
            Log.e(
                RecognitionCameraCaptureTag,
                "request=$requestId event=target_create_failed " +
                    "cause_class=${diagnosticValue(targetResult.exceptionOrNull()?.let { it::class.simpleName })} " +
                    "cause_message=${diagnosticValue(targetResult.exceptionOrNull()?.message)}",
            )
            error = CameraStartFailureHint
            return
        }
        val target = targetResult.getOrThrow()
        activeCaptureRequestId = requestId
        logCapture(
            requestId,
            "before_capture",
            "camera_provider_bound=$cameraBound preview_bound=$previewStreaming " +
                "image_capture_instance_ready=${imageCaptureInstanceReady()} " +
                "camera_controller_alive=${runCatching { cameraController.cameraInfo != null }.getOrDefault(false)} " +
                "preview_view_attached=${previewView?.isAttachedToWindow == true} " +
                "preview_attached=${previewView?.isAttachedToWindow == true} " +
                "controller_attached=${previewView?.controller === cameraController} " +
                "lifecycle_state=${lifecycleOwner.lifecycle.currentState.name} " +
                "capture_state=${captureState.name} " +
                "capture_button_enabled=false output_target_type=file " +
                "output_file=${diagnosticValue(target.file.absolutePath)} " +
                "output_uri_type=${diagnosticValue(target.uri.scheme)} " +
                "parent_directory_exists=${target.file.parentFile?.isDirectory == true} " +
                "parent_directory_writable=${target.file.parentFile?.canWrite() == true}",
        )
        try {
            // CameraX writes directly to the app-private cache file. The
            // FileProvider URI remains available for gallery/test handoffs but
            // is not used as the still-image output target.
            val output = ImageCapture.OutputFileOptions.Builder(target.file).build()
            cameraController.takePicture(
                output,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                        logCapture(
                            requestId,
                            "terminal_callback",
                            "result=onImageSaved capture_state=${captureState.name} " +
                                "camera_provider_bound=$cameraBound " +
                                "camera_controller_alive=${runCatching { cameraController.cameraInfo != null }.getOrDefault(false)} " +
                                "preview_view_attached=${previewView?.isAttachedToWindow == true} " +
                                "preview_attached=${previewView?.isAttachedToWindow == true} " +
                                "controller_attached=${previewView?.controller === cameraController}",
                        )
                        activeCaptureRequestId = null
                        logCapture(
                            requestId,
                            "on_image_saved",
                            "reached=true saved_uri=${diagnosticValue(result.savedUri)} " +
                                "file_exists=${target.file.exists()} file_bytes=${target.file.length()} " +
                                "decode_attempted=true camera_provider_bound=$cameraBound " +
                                "camera_controller_alive=${runCatching { cameraController.cameraInfo != null }.getOrDefault(false)} " +
                                "preview_view_attached=${previewView?.isAttachedToWindow == true}",
                        )
                        scope.launch {
                            try {
                                val fileExists = target.file.exists()
                                val fileBytes = target.file.length()
                                val normalized = runCatching {
                                    check(fileExists && fileBytes > 0L) {
                                        "没有读取到拍照内容，请重新拍摄"
                                    }
                                    RecognitionImageStore.normalizeCameraFileWithDetails(context, target.file)
                                }
                                val details = normalized.getOrNull()
                                val selectedFile = details?.selectedImage?.filePath?.let(::File)
                                val outputEvidence = RecognitionCameraCaptureOutput(
                                    callbackSucceeded = true,
                                    fileExists = fileExists,
                                    fileBytes = fileBytes,
                                    decodedWidth = details?.decodedWidth ?: 0,
                                    decodedHeight = details?.decodedHeight ?: 0,
                                    selectedImageCreated = details != null &&
                                        selectedFile?.let { it.exists() && it.length() > 0L } == true,
                                )
                                captureState = RecognitionCameraCaptureContract.completeCapture(outputEvidence)
                                if (outputEvidence.isValid && details != null) {
                                    logCapture(
                                        requestId,
                                        "decode_result",
                                        "decode_success=true decoded_width=${details.decodedWidth} " +
                                            "decoded_height=${details.decodedHeight} rotation_degrees=${details.rotationDegrees} " +
                                            "selected_image_created=true",
                                    )
                                    handoffImage = details.selectedImage
                                    runCatching { onImageReady(details.selectedImage) }
                                        .onSuccess {
                                            logCapture(requestId, "recognition_navigation", "started=true")
                                        }
                                        .onFailure { navigationError ->
                                            captureState = RecognitionCameraCaptureContract.completeError()
                                            error = CameraImageFailureHint
                                            Log.e(
                                                RecognitionCameraCaptureTag,
                                                "request=$requestId event=recognition_navigation " +
                                                    "started=false cause_class=${diagnosticValue(navigationError::class.simpleName)} " +
                                                    "cause_message=${diagnosticValue(navigationError.message)}",
                                            )
                                        }
                                } else {
                                    val decodeError = normalized.exceptionOrNull()
                                    Log.e(
                                        RecognitionCameraCaptureTag,
                                        "request=$requestId event=decode_result " +
                                            "decode_attempted=true decode_success=false " +
                                            "decoded_width=0 decoded_height=0 " +
                                            "selected_image_created=false " +
                                            "cause_class=${diagnosticValue(decodeError?.let { it::class.simpleName })} " +
                                            "cause_message=${diagnosticValue(decodeError?.message)}",
                                    )
                                    error = CameraImageFailureHint
                                }
                            } finally {
                                val cleanupSucceeded = !target.file.exists() || target.file.delete()
                                logCapture(
                                    requestId,
                                    "post_capture",
                                    "temporary_file_cleanup=$cleanupSucceeded " +
                                        "camera_state_after_callback=${captureState.name} " +
                                        "camera_provider_bound=$cameraBound preview_bound=$previewStreaming",
                                )
                            }
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        val cause = exception.cause
                        logCapture(
                            requestId,
                            "terminal_callback",
                            "result=onError capture_state=${captureState.name} " +
                                "camera_provider_bound=$cameraBound preview_streaming=$previewStreaming " +
                                "image_capture_ready=${imageCaptureInstanceReady()} " +
                                "preview_attached=${previewView?.isAttachedToWindow == true} " +
                                "controller_attached=${previewView?.controller === cameraController} " +
                                "lifecycle_state=${lifecycleOwner.lifecycle.currentState.name}",
                        )
                        activeCaptureRequestId = null
                        val outputFileExists = target.file.exists()
                        val outputFileBytes = target.file.length()
                        val cleanupSucceeded = !target.file.exists() || target.file.delete()
                        val cameraReadyForRetry = cameraCanCapture()
                        captureState = RecognitionCameraCaptureContract.completeError()
                        if (!cameraReadyForRetry) cameraRetry += 1
                        Log.e(
                            RecognitionCameraCaptureTag,
                            "request=$requestId event=error " +
                                "image_capture_error=${diagnosticValue(exception.imageCaptureError)} " +
                                "message=${diagnosticValue(exception.message)} " +
                                "cause_class=${diagnosticValue(cause?.let { it::class.simpleName })} " +
                                "cause_message=${diagnosticValue(cause?.message)} " +
                                "camera_provider_bound=$cameraBound " +
                                "camera_controller_alive=${runCatching { cameraController.cameraInfo != null }.getOrDefault(false)} " +
                                "preview_view_attached=${previewView?.isAttachedToWindow == true} " +
                                "preview_attached=${previewView?.isAttachedToWindow == true} " +
                                "controller_attached=${previewView?.controller === cameraController} " +
                                "camera_ready_for_retry=$cameraReadyForRetry " +
                                "camera_rebind_requested=${!cameraReadyForRetry} " +
                                "output_file_exists=$outputFileExists output_file_bytes=$outputFileBytes " +
                                "temporary_file_cleanup=$cleanupSucceeded",
                        )
                        error = CameraCaptureFailureHint
                    }
                },
            )
        } catch (captureError: Exception) {
            val cleanupSucceeded = !target.file.exists() || target.file.delete()
            logCapture(
                requestId,
                "terminal_callback",
                "result=takePictureThrow capture_state=${captureState.name} " +
                    "preview_attached=${previewView?.isAttachedToWindow == true} " +
                    "controller_attached=${previewView?.controller === cameraController}",
            )
            activeCaptureRequestId = null
            val cameraReadyForRetry = cameraCanCapture()
            captureState = RecognitionCameraCaptureContract.completeError()
            if (!cameraReadyForRetry) cameraRetry += 1
            Log.e(
                RecognitionCameraCaptureTag,
                "request=$requestId event=take_picture_throw " +
                    "cause_class=${diagnosticValue(captureError::class.simpleName)} " +
                    "cause_message=${diagnosticValue(captureError.message)} " +
                    "camera_ready_for_retry=$cameraReadyForRetry " +
                    "camera_rebind_requested=${!cameraReadyForRetry} " +
                    "temporary_file_cleanup=$cleanupSucceeded",
            )
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

    BackHandler(enabled = captureState == RecognitionCameraCaptureState.CAPTURING) {
        // Keep the bound CameraX surface alive until the request has a terminal result.
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
        } else if (galleryBusy) {
            // A neutral handoff frame prevents a stale CameraX frame or the
            // previous SelectedImage from flashing while the new gallery
            // image is normalized.
            Box(Modifier.fillMaxSize().background(Color.Black))
        } else if (hasCameraPermission && cameraBound) {
            AndroidView(
                factory = { context ->
                    PreviewView(context).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        controller = cameraController
                    }.also { previewView = it }
                },
                modifier = Modifier.fillMaxSize(),
                update = {
                    previewView = it
                    it.controller = cameraController
                },
            )
        }

        if (handoffImage == null && !galleryBusy && captureState != RecognitionCameraCaptureState.CAPTURING) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.12f)),
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

        if (handoffImage == null && !galleryBusy && captureState != RecognitionCameraCaptureState.CAPTURING && !autoOpenGallery) {
            if (!hasCameraPermission) {
                Button(
                    onClick = {
                        permissionRequested = true
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    modifier = Modifier.align(Alignment.Center),
                ) { Text("开启相机") }
            } else if (!cameraBound || !imageCaptureReady || !previewStreaming) {
                Button(
                    onClick = {
                        error = null
                        cameraRetry += 1
                    },
                    modifier = Modifier.align(Alignment.Center),
                ) { Text("重试打开相机") }
            }
        }

        error?.takeIf { handoffImage == null && !galleryBusy && captureState != RecognitionCameraCaptureState.CAPTURING }?.let {
            Text(
                text = it,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = safeInsets.calculateBottomPadding() + 132.dp),
            )
        }

        if (handoffImage == null && !galleryBusy && captureState != RecognitionCameraCaptureState.CAPTURING) Row(
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
            HomeCameraButton(
                onClick = ::capture,
                enabled = cameraCanCapture() &&
                    RecognitionCameraCaptureContract.canStartCapture(captureState),
            )
        }
    }
}
