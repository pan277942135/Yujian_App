package com.yujian.ai.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.yujian.ai.model.SelectedImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

object RecognitionImageStore {
    data class CameraTarget(val file: File, val uri: Uri)

    fun createCameraTarget(context: Context): CameraTarget {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "capture_${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return CameraTarget(file, uri)
    }

    suspend fun normalize(context: Context, uri: Uri, source: String): SelectedImage = withContext(Dispatchers.IO) {
        val rotation = runCatching {
            context.contentResolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees }
        }.getOrDefault(0)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: error("无法读取照片")
        var sample = 1
        val maxDimension = maxOf(bounds.outWidth, bounds.outHeight)
        while (maxDimension / sample > 2048) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("照片格式不受支持")
        val oriented = if (rotation == 0) decoded else Bitmap.createBitmap(
            decoded, 0, 0, decoded.width, decoded.height,
            Matrix().apply { postRotate(rotation.toFloat()) }, true,
        )

        val dir = File(context.filesDir, "recognition_inputs").apply { mkdirs() }
        val normalized = File(dir, "input_${UUID.randomUUID()}.jpg")
        normalized.outputStream().use { output ->
            check(oriented.compress(Bitmap.CompressFormat.JPEG, 92, output)) { "无法保存识别照片" }
        }
        SelectedImage(filePath = normalized.absolutePath, bitmap = oriented, source = source)
    }

    suspend fun normalizeCameraFile(context: Context, file: File): SelectedImage {
        return normalize(context, Uri.fromFile(file), "camera")
    }
}
