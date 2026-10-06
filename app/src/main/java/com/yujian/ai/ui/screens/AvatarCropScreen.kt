package com.yujian.ai.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.exifinterface.media.ExifInterface
import com.yujian.ai.ui.designsystem.color.YuJianColors
import com.yujian.ai.ui.designsystem.components.YuJianActionButtonVariant
import com.yujian.ai.ui.designsystem.components.YuJianPrimaryButton
import com.yujian.ai.ui.designsystem.typography.YuJianTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

class AvatarImageException(message: String, cause: Throwable? = null) : IOException(message, cause)

suspend fun decodeAvatarCropSource(context: Context, uri: Uri): Bitmap = withContext(Dispatchers.IO) {
    val sourceDir = File(context.cacheDir, "avatar_sources").apply { mkdirs() }
    val sourceFile = File(sourceDir, "source_${UUID.randomUUID()}.img")
    try {
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                sourceFile.outputStream().use { output -> input.copyTo(output) }
            } ?: throw AvatarImageException("无法读取这张图片，请重新选择")
        } catch (error: AvatarImageException) {
            throw error
        } catch (error: Exception) {
            throw AvatarImageException("无法读取这张图片，请重新选择", error)
        }
        if (sourceFile.length() <= 0L) throw AvatarImageException("无法读取这张图片，请重新选择")

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(sourceFile.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw AvatarImageException("暂不支持这种图片格式")
        }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
        val decoded = BitmapFactory.decodeFile(
            sourceFile.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: throw AvatarImageException("暂不支持这种图片格式")
        val orientation = runCatching {
            ExifInterface(sourceFile).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val normalizedMatrix = exifNormalizationMatrix(orientation)
        if (normalizedMatrix == null) {
            decoded
        } else {
            Bitmap.createBitmap(
                decoded,
                0,
                0,
                decoded.width,
                decoded.height,
                normalizedMatrix,
                true,
            ).also { decoded.recycle() }
        }
    } finally {
        sourceFile.delete()
    }
}

private fun exifNormalizationMatrix(orientation: Int): Matrix? = when (orientation) {
    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> Matrix().apply { setScale(-1f, 1f) }
    ExifInterface.ORIENTATION_ROTATE_180 -> Matrix().apply { setRotate(180f) }
    ExifInterface.ORIENTATION_FLIP_VERTICAL -> Matrix().apply { setScale(1f, -1f) }
    ExifInterface.ORIENTATION_TRANSPOSE -> Matrix().apply { setRotate(90f); postScale(-1f, 1f) }
    ExifInterface.ORIENTATION_ROTATE_90 -> Matrix().apply { setRotate(90f) }
    ExifInterface.ORIENTATION_TRANSVERSE -> Matrix().apply { setRotate(-90f); postScale(-1f, 1f) }
    ExifInterface.ORIENTATION_ROTATE_270 -> Matrix().apply { setRotate(-90f) }
    else -> null
}

fun writeAvatarCrop(context: Context, source: Bitmap, crop: AvatarCropSourceRect): Uri {
    require(crop.side > 0)
    require(crop.left >= 0 && crop.top >= 0)
    require(crop.left + crop.side <= source.width && crop.top + crop.side <= source.height)
    val cropped = Bitmap.createBitmap(source, crop.left, crop.top, crop.side, crop.side)
    val outputSide = minOf(1024, cropped.width)
    val output = if (outputSide == cropped.width) cropped else {
        Bitmap.createScaledBitmap(cropped, outputSide, outputSide, true).also { cropped.recycle() }
    }
    val target = File(context.cacheDir, "avatar_pending_${UUID.randomUUID()}.jpg")
    try {
        target.outputStream().buffered().use { stream ->
            if (!output.compress(Bitmap.CompressFormat.JPEG, 92, stream)) {
                throw AvatarImageException("无法保存这张图片，请重新选择")
            }
        }
        return Uri.fromFile(target)
    } catch (error: Exception) {
        target.delete()
        if (error is AvatarImageException) throw error
        throw AvatarImageException("无法保存这张图片，请重新选择", error)
    } finally {
        output.recycle()
    }
}

fun deletePendingAvatar(uri: Uri?) {
    if (uri?.scheme == "file") runCatching { uri.path?.let { File(it).delete() } }
}

@Composable
fun AvatarCropScreen(
    bitmap: Bitmap?,
    loading: Boolean,
    error: String?,
    processing: Boolean,
    onBack: () -> Unit,
    onReselect: () -> Unit,
    onUseAvatar: (AvatarCropSourceRect) -> Unit,
) {
    var transform by remember(bitmap) { mutableStateOf(AvatarCropTransform()) }
    var viewport by remember(bitmap) { mutableStateOf(IntSize.Zero) }
    val currentTransform by rememberUpdatedState(transform)

    AccountPrivacyPageScaffold(
        title = "调整头像",
        onBack = onBack,
        backEnabled = !processing,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = "拖动调整位置，双指缩放",
                style = YuJianTypography.body.copy(color = YuJianColors.TextSecondary),
                textAlign = TextAlign.Center,
            )
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val frameSize = (maxWidth - 8.dp).coerceAtMost(360.dp)
                Box(
                    modifier = Modifier.size(frameSize),
                    contentAlignment = Alignment.Center,
                ) {
                    if (bitmap != null) {
                        val image = remember(bitmap) { bitmap.asImageBitmap() }
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .onSizeChanged { viewport = it }
                                .pointerInput(image) {
                                    detectTransformGestures { centroid, pan, zoom, _ ->
                                        val updated = updateAvatarCropTransform(
                                            current = currentTransform,
                                            centroidX = centroid.x,
                                            centroidY = centroid.y,
                                            panX = pan.x,
                                            panY = pan.y,
                                            zoomChange = zoom,
                                            bitmapWidth = bitmap.width,
                                            bitmapHeight = bitmap.height,
                                            viewportWidth = size.width.toFloat(),
                                            viewportHeight = size.height.toFloat(),
                                        )
                                        transform = updated
                                    }
                                }
                                .semantics {
                                    contentDescription = "头像裁切区域"
                                    stateDescription = "缩放 ${String.format(Locale.ROOT, "%.1f", transform.scale)} 倍；可拖动调整位置并双指缩放"
                                    customActions = listOf(
                                        CustomAccessibilityAction("放大头像") {
                                            transform = updateAvatarCropTransform(
                                                current = transform,
                                                centroidX = viewport.width / 2f,
                                                centroidY = viewport.height / 2f,
                                                panX = 0f,
                                                panY = 0f,
                                                zoomChange = 1.15f,
                                                bitmapWidth = bitmap.width,
                                                bitmapHeight = bitmap.height,
                                                viewportWidth = viewport.width.toFloat(),
                                                viewportHeight = viewport.height.toFloat(),
                                            )
                                            true
                                        },
                                        CustomAccessibilityAction("缩小头像") {
                                            transform = updateAvatarCropTransform(
                                                current = transform,
                                                centroidX = viewport.width / 2f,
                                                centroidY = viewport.height / 2f,
                                                panX = 0f,
                                                panY = 0f,
                                                zoomChange = 1f / 1.15f,
                                                bitmapWidth = bitmap.width,
                                                bitmapHeight = bitmap.height,
                                                viewportWidth = viewport.width.toFloat(),
                                                viewportHeight = viewport.height.toFloat(),
                                            )
                                            true
                                        },
                                    )
                                }
                                .testTag("avatar_crop_canvas"),
                        ) {
                            if (viewport.width > 0 && viewport.height > 0) {
                                val baseScale = max(size.width / bitmap.width, size.height / bitmap.height)
                                val scale = baseScale * transform.scale
                                val drawWidth = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
                                val drawHeight = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
                                val left = ((size.width - drawWidth) / 2f + transform.offsetX).roundToInt()
                                val top = ((size.height - drawHeight) / 2f + transform.offsetY).roundToInt()
                                drawImage(
                                    image = image,
                                    dstOffset = IntOffset(left, top),
                                    dstSize = IntSize(drawWidth, drawHeight),
                                )
                                val mask = Path().apply {
                                    fillType = PathFillType.EvenOdd
                                    addRect(Rect(0f, 0f, size.width, size.height))
                                    addOval(Rect(Offset.Zero, size))
                                }
                                drawPath(mask, Color.Black.copy(alpha = 0.48f))
                                drawCircle(
                                    color = Color.White,
                                    radius = size.minDimension / 2f,
                                    style = Stroke(width = 2.dp.toPx()),
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (loading) CircularProgressIndicator(
                                color = YuJianColors.ActionPrimary,
                                modifier = Modifier.semantics { contentDescription = "正在读取图片" },
                            )
                            else Text(
                                text = error ?: "暂时无法读取这张图片",
                                style = YuJianTypography.body.copy(color = YuJianColors.TextSecondary),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
            if (error != null && bitmap != null) {
                Text(error, style = YuJianTypography.caption.copy(color = Color(0xFFB24A3A)))
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                YuJianPrimaryButton(
                    text = "重新选择",
                    onClick = onReselect,
                    modifier = Modifier.weight(1f).testTag("avatar_crop_reselect"),
                    enabled = !processing,
                    variant = YuJianActionButtonVariant.SECONDARY_STRONG,
                )
                YuJianPrimaryButton(
                    text = if (processing) "正在处理" else "使用此头像",
                    onClick = {
                        val width = viewport.width.toFloat()
                        val height = viewport.height.toFloat()
                        if (bitmap != null && width > 0f && height > 0f) {
                            onUseAvatar(
                                avatarCropSourceRect(
                                    bitmapWidth = bitmap.width,
                                    bitmapHeight = bitmap.height,
                                    viewportWidth = width,
                                    viewportHeight = height,
                                    transform = transform,
                                ),
                            )
                        }
                    },
                    modifier = Modifier.weight(1f).testTag("avatar_crop_confirm"),
                    enabled = bitmap != null && !loading && error == null && !processing && viewport.width > 0,
                    loading = processing,
                )
            }
        }
    }
}
