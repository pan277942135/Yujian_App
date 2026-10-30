package com.yujian.ai.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ZoomState
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.Animatable
import com.yujian.ai.ui.home.CaptureTransitionLightField
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import kotlinx.coroutines.delay
import com.yujian.ai.ui.home.CaptureNavigationTransition
import kotlin.math.roundToInt
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import android.graphics.BitmapFactory
import android.widget.Toast
import com.yujian.ai.media.TestGallerySeeder
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Observer
import com.yujian.ai.R
import com.yujian.ai.media.RecognitionImageStore
import com.yujian.ai.model.SelectedImage
import com.yujian.ai.ui.identify.RecognitionCameraCaptureContract
import com.yujian.ai.ui.identify.RecognitionFirstCaptureGuideOverlay
import com.yujian.ai.ui.identify.RecognitionFirstCaptureGuidePage
import com.yujian.ai.ui.identify.RecognitionFirstCaptureGuideStore
import com.yujian.ai.ui.identify.RecognitionCameraControlsGeometry
import com.yujian.ai.ui.identify.RecognitionCameraCaptureOutput
import com.yujian.ai.ui.identify.RecognitionCameraCaptureState
import com.yujian.ai.ui.identify.RecognitionContentScaleMode
import com.yujian.ai.ui.identify.RecognitionSourcePhoto
import com.yujian.ai.ui.identify.calculateRecognitionImageTransform
import com.yujian.ai.ui.identify.shouldShowRecognitionFirstCaptureGuide
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import java.util.Locale

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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentifyScreen(
    image: SelectedImage?,
    autoOpenGallery: Boolean = false,
    captureResetToken: Int = 0,
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
    var zoomRatio by remember { mutableStateOf(1f) }
    val captureHandoffActive = CaptureNavigationTransition.active.value && !autoOpenGallery
    var captureTarget by remember { mutableStateOf(Offset.Unspecified) }
    var captureRootOrigin by remember { mutableStateOf(Offset.Zero) }
    var captureHandoffArrived by remember { mutableStateOf(false) }
    val captureProgress = remember { Animatable(0f) }
    val orbit = rememberInfiniteTransition(label = "capture-brand-orbit")
    val orbitDegrees by orbit.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "capture-orbit-degrees",
    )
    LaunchedEffect(captureHandoffActive, captureTarget) {
        if (captureHandoffActive && captureTarget.x.isFinite()) {
            captureProgress.animateTo(1f, tween(620, easing = LinearEasing))
            captureHandoffArrived = true
            CaptureNavigationTransition.finish()
        }
    }
    var firstCaptureGuideVisible by rememberSaveable(autoOpenGallery, captureResetToken) {
        mutableStateOf(false)
    }
    var firstCaptureGuidePresented by rememberSaveable(autoOpenGallery, captureResetToken) {
        mutableStateOf(false)
    }
    var firstCaptureGuidePage by rememberSaveable(autoOpenGallery, captureResetToken) {
        mutableStateOf(RecognitionFirstCaptureGuidePage.P01)
    }
    var galleryVisitedThisSession by rememberSaveable(autoOpenGallery, captureResetToken) {
        mutableStateOf(autoOpenGallery)
    }
    val firstCaptureGuideStore = remember(context) { RecognitionFirstCaptureGuideStore(context) }
    var galleryAutoOpenPending by remember(autoOpenGallery, captureResetToken) {
        mutableStateOf(autoOpenGallery)
    }
    var captureState by remember { mutableStateOf(RecognitionCameraCaptureState.INITIALIZING) }
    var cameraRetry by remember { mutableStateOf(0) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var showTestSamplesSheet by remember { mutableStateOf(false) }
    var torchEnabled by remember { mutableStateOf(false) }
    // This local handoff is set before navigation, so CameraX and its controls
    // cannot share a frame with the Recognition processing scene.
    var handoffImage by remember { mutableStateOf<SelectedImage?>(null) }

    val cameraController = remember(context) {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            setPinchToZoomEnabled(true)
        }
    }

    DisposableEffect(cameraController, lifecycleOwner) {
        val observer = Observer<ZoomState> { state ->
            zoomRatio = state?.zoomRatio ?: 1f
        }
        cameraController.zoomState.observe(lifecycleOwner, observer)
        onDispose { cameraController.zoomState.removeObserver(observer) }
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
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        galleryAutoOpenPending = false
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
            galleryVisitedThisSession = true
            galleryBusy = true
            error = null
            runCatching {
                galleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            }
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

    LaunchedEffect(captureResetToken) {
        // Re-entering Capture after a result must restore a live camera preview,
        // never the last photo that was handed to Processing.
        handoffImage = null
        galleryBusy = false
        error = null
        galleryAutoOpenPending = autoOpenGallery
    }

    LaunchedEffect(autoOpenGallery, captureResetToken) {
        if (autoOpenGallery) openGallery()
        else if (!hasCameraPermission && !permissionRequested) {
            permissionRequested = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(
        autoOpenGallery,
        galleryVisitedThisSession,
        hasCameraPermission,
        cameraBound,
        imageCaptureReady,
        previewStreaming,
        galleryAutoOpenPending,
    ) {
        if (firstCaptureGuidePresented || galleryAutoOpenPending || !cameraCanCapture()) {
            return@LaunchedEffect
        }
        firstCaptureGuideVisible = shouldShowRecognitionFirstCaptureGuide(
            cameraPermissionGranted = hasCameraPermission,
            previewReady = cameraCanCapture(),
            galleryPickerPending = galleryAutoOpenPending || autoOpenGallery,
            guideCompleted = firstCaptureGuideStore.isCompleted(),
            galleryVisitedThisSession = galleryVisitedThisSession || autoOpenGallery,
        )
        firstCaptureGuidePresented = true
    }

    BackHandler(enabled = captureState == RecognitionCameraCaptureState.CAPTURING) {
        // Keep the bound CameraX surface alive until the request has a terminal result.
    }

    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned {
        captureRootOrigin = it.boundsInWindow().topLeft
    }) {
        CaptureFrostedBackground(Modifier.fillMaxSize())
        val layoutDirection = LocalLayoutDirection.current
        val captureControlsPlacement = remember(maxWidth, safeInsets, layoutDirection) {
            RecognitionCameraControlsGeometry.resolve(
                viewportWidthDp = maxWidth.value,
                galleryWidthDp = 54f,
                rightSafeInsetDp = safeInsets.calculateRightPadding(layoutDirection).value,
                bottomSafeInsetDp = safeInsets.calculateBottomPadding().value,
            )
        }

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
            // Keep the shared frosted base visible while the new gallery image is normalized.
        } else if (hasCameraPermission) {
            AndroidView(
                factory = { context ->
                    PreviewView(context).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        controller = cameraController
                    }.also { previewView = it }
                },
                modifier = Modifier.fillMaxSize().alpha(if (previewStreaming) 1f else 0f),
                update = {
                    previewView = it
                    it.controller = cameraController
                },
            )
        }

        if (handoffImage == null && !galleryBusy && captureState != RecognitionCameraCaptureState.CAPTURING && !firstCaptureGuideVisible) {
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
            Column(
                Modifier.align(Alignment.TopCenter).padding(top = safeInsets.calculateTopPadding() + 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("拍照识鱼", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(7.dp))
                Text("请将整条鱼放在取景框中央", color = Color.White.copy(alpha = .82f), fontSize = 14.sp)
            }
            Canvas(Modifier.align(Alignment.Center).offset(y = -(maxHeight * .17f)).fillMaxWidth(.88f).height(maxHeight * .36f)) {
                val line = Path()
                val x = size.width; val y = size.height
                val arm = 48.dp.toPx(); val radius = 18.dp.toPx()
                line.moveTo(arm, 0f); line.lineTo(radius, 0f)
                line.quadraticTo(0f, 0f, 0f, radius); line.lineTo(0f, arm)
                line.moveTo(x - arm, 0f); line.lineTo(x - radius, 0f)
                line.quadraticTo(x, 0f, x, radius); line.lineTo(x, arm)
                line.moveTo(0f, y - arm); line.lineTo(0f, y - radius)
                line.quadraticTo(0f, y, radius, y); line.lineTo(arm, y)
                line.moveTo(x, y - arm); line.lineTo(x, y - radius)
                line.quadraticTo(x, y, x - radius, y); line.lineTo(x - arm, y)
                drawPath(line, Color(0xFFD8B36A).copy(alpha = .13f), style = Stroke(10.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
                drawPath(line, Color.White.copy(alpha = .95f), style = Stroke(2.8.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
            }
            Row(
                Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = safeInsets.calculateBottomPadding() + 218.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .border(1.dp, Color.White.copy(alpha = .24f), RoundedCornerShape(28.dp))
                    .background(Color.Black.copy(alpha = .32f))
                    .padding(horizontal = 11.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.ZoomIn, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(4.dp))
                Text(String.format(Locale.US, "%.1fx", zoomRatio), color = Color.White, fontSize = 14.sp)
            }
            Text(
                "可双指缩放，尽量拍清鱼头、鱼尾与鱼身轮廓",
                color = Color.White.copy(alpha = .86f), fontSize = 12.sp,
                modifier = Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = safeInsets.calculateBottomPadding() + 190.dp),
            )
        }

        if (handoffImage == null && !galleryBusy && captureState != RecognitionCameraCaptureState.CAPTURING && !galleryAutoOpenPending && !firstCaptureGuideVisible) {
            if (!hasCameraPermission) {
                Button(
                    onClick = {
                        permissionRequested = true
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    modifier = Modifier.align(Alignment.Center),
                ) { Text("开启相机") }
            } else if (captureState == RecognitionCameraCaptureState.ERROR) {
                Button(
                    onClick = {
                        error = null
                        cameraRetry += 1
                    },
                    modifier = Modifier.align(Alignment.Center),
                ) { Text("重试打开相机") }
            }
        }

        error?.takeIf { handoffImage == null && !galleryBusy && captureState != RecognitionCameraCaptureState.CAPTURING && !firstCaptureGuideVisible }?.let {
            Text(
                text = it,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = safeInsets.calculateBottomPadding() + 132.dp),
            )
        }

        if (handoffImage == null && !galleryBusy && captureState != RecognitionCameraCaptureState.CAPTURING && !firstCaptureGuideVisible) {
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .height(145.dp + safeInsets.calculateBottomPadding())
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xD93C464C), Color(0xF3121619))))
                    .border(1.dp, Color.White.copy(alpha = .28f), RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = captureControlsPlacement.bottomInsetDp.dp)
                    .size(76.dp)
                    .clickable(
                        enabled = cameraCanCapture() &&
                            RecognitionCameraCaptureContract.canStartCapture(captureState),
                        role = Role.Button,
                        onClick = ::capture,
                    )
                    .testTag("recognition-camera-shutter")
                    .onGloballyPositioned { captureTarget = it.boundsInWindow().center }
                    .alpha(if (captureHandoffActive) 0f else 1f)
                    .semantics { contentDescription = "拍摄" },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.capture_button_main_v1),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(
                        start = 27.dp,
                        bottom = captureControlsPlacement.bottomInsetDp.dp,
                    )
                    .size(66.dp)
                    .clickable(role = Role.Button, onClick = ::openGallery)
                    .testTag("recognition-camera-gallery")
                    .semantics { contentDescription = "从相册选择" },
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.home_morning_bg),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(46.dp).clip(RoundedCornerShape(9.dp))
                            .border(1.dp, Color.White.copy(alpha = .6f), RoundedCornerShape(9.dp)),
                    )
                    Text(
                        text = "相册",
                        color = Color.White,
                        fontSize = 12.sp,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = 27.dp,
                        bottom = captureControlsPlacement.bottomInsetDp.dp,
                    )
                    .size(66.dp)
                    .clickable(role = Role.Button) {
                        val next = !torchEnabled
                        cameraController.enableTorch(next)
                        torchEnabled = next
                    }
                    .testTag("recognition-camera-flash")
                    .semantics { contentDescription = "闪光灯" },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(46.dp).clip(CircleShape)
                        .border(1.dp, Color.White.copy(alpha = .48f), CircleShape)
                        .background(Color.White.copy(alpha = .08f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.FlashOn,
                            contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                    Text("闪光灯", color = Color.White, fontSize = 12.sp)
                }
            }
        }

        if (showTestSamplesSheet) {
            ModalBottomSheet(
                onDismissRequest = { showTestSamplesSheet = false },
                containerColor = Color(0xFF1E282D),
                contentColor = Color.White,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "测试鱼获图片",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Button(
                            onClick = {
                                scope.launch {
                                    TestGallerySeeder.seedTestImagesIfNeeded(context)
                                    Toast.makeText(context, "已同步到模拟器系统相册！可在相册中直接查看", Toast.LENGTH_SHORT).show()
                                }
                            },
                        ) {
                            Text("同步至相册", fontSize = 12.sp)
                        }
                    }
                    Text(
                        text = "点击以下图片直接测试识别，或点击右下角从系统相册选取：",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp),
                    ) {
                        items(TestGallerySeeder.SAMPLE_ITEMS) { item ->
                            val thumbBitmap = remember(item.assetName) {
                                runCatching {
                                    context.assets.open("test_catches/${item.assetName}").use {
                                        BitmapFactory.decodeStream(it)
                                    }
                                }.getOrNull()
                            }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        showTestSamplesSheet = false
                                        scope.launch {
                                            error = null
                                            runCatching {
                                                RecognitionImageStore.normalizeAsset(
                                                    context,
                                                    "test_catches/${item.assetName}",
                                                    "test_sample",
                                                )
                                            }.onSuccess { selected ->
                                                handoffImage = selected
                                                onImageReady(selected)
                                            }.onFailure {
                                                error = "测试照片读取失败，请重试"
                                            }
                                        }
                                    },
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF28363D)),
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    if (thumbBitmap != null) {
                                        Image(
                                            bitmap = thumbBitmap.asImageBitmap(),
                                            contentDescription = item.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(110.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        if (captureHandoffActive && captureTarget.x.isFinite() && handoffImage == null) {
            val start = CaptureNavigationTransition.homeCenter
                .takeIf { it.x.isFinite() } ?: captureTarget
            val t = (captureProgress.value / .68f).coerceIn(0f, 1f)
            val eased = t * t * (3f - 2f * t)
            val center = Offset(
                start.x + (captureTarget.x - start.x) * eased - captureRootOrigin.x,
                start.y + (captureTarget.y - start.y) * eased - captureRootOrigin.y - 18.dp.value * 2f * t * (1f - t),
            )
            val buttonPx = with(LocalDensity.current) { 76.dp.toPx() }
            Box(Modifier.offset {
                IntOffset((center.x - buttonPx).roundToInt(), (center.y - buttonPx).roundToInt())
            }.size(152.dp), contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(R.drawable.capture_button_main_v1),
                    contentDescription = null,
                    modifier = Modifier.size(76.dp).alpha(((1f - captureProgress.value) * 3f).coerceIn(0f, 1f)),
                    contentScale = ContentScale.Fit,
                )
                CaptureTransitionLightField(captureProgress.value, Modifier.fillMaxSize())
            }
        }
        if (firstCaptureGuideVisible) {
            RecognitionFirstCaptureGuideOverlay(
                initialPage = firstCaptureGuidePage,
                onComplete = {
                    if (firstCaptureGuideStore.markCompleted()) firstCaptureGuideVisible = false
                },
                onDismiss = { firstCaptureGuideVisible = false },
                onPageChanged = { firstCaptureGuidePage = it },
            )
        }
    }
}
