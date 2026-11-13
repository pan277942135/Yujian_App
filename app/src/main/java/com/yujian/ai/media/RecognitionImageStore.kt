package com.yujian.ai.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.yujian.ai.model.SelectedImage
import com.yujian.ai.ui.components.applyExifOrientation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.security.MessageDigest
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

object RecognitionImageStore {
    data class CameraTarget(val file: File, val uri: Uri)

    data class CameraNormalizationResult(
        val selectedImage: SelectedImage,
        val rotationDegrees: Int,
        val decodedWidth: Int,
        val decodedHeight: Int,
    )

    fun createCameraTarget(context: Context): CameraTarget {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        require(dir.isDirectory && dir.canWrite()) { "相机临时目录不可写" }
        val file = File(dir, "capture_${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return CameraTarget(file, uri)
    }

    suspend fun normalize(context: Context, uri: Uri, source: String): SelectedImage = withContext(Dispatchers.IO) {
        // Copy the provider URI into app-private cache once. This avoids relying on
        // the photo provider granting multiple readable streams and also makes
        // Google Photos / cloud-backed content URIs behave like local files.
        val tempDir = File(context.cacheDir, "gallery_imports").apply { mkdirs() }
        val tempFile = File(tempDir, "import_${UUID.randomUUID()}.img")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            } ?: error("无法读取照片")
            require(tempFile.length() > 0L) { "照片内容为空，请重新选择" }
            normalizeLocalFile(context, tempFile, source, "照片格式不受支持")
        } finally {
            tempFile.delete()
        }
    }

    suspend fun normalizeAsset(context: Context, assetPath: String, source: String = "test_sample"): SelectedImage = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "asset_imports").apply { mkdirs() }
        val tempFile = File(tempDir, "asset_${UUID.randomUUID()}.jpg")
        try {
            context.assets.open(assetPath).use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
            normalizeLocalFile(context, tempFile, source, "测试照片格式不受支持")
        } finally {
            tempFile.delete()
        }
    }

    suspend fun normalizeCameraFile(context: Context, file: File): SelectedImage = withContext(Dispatchers.IO) {
        normalizeCameraFileWithDetails(context, file).selectedImage
    }

    suspend fun normalizeCameraFileWithDetails(
        context: Context,
        file: File,
        previewAspectRatio: Float? = null,
        captureTimestampEpochMs: Long? = null,
    ): CameraNormalizationResult = withContext(Dispatchers.IO) {
        require(file.exists() && file.length() > 0L) { "没有读取到拍照内容，请重新拍摄" }

        val exif = runCatching { ExifInterface(file) }.getOrNull()
        val rotation = exif?.rotationDegrees ?: 0
        val orientation = exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            ?: ExifInterface.ORIENTATION_NORMAL
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "拍照文件无法解析，请重新拍摄" }

        var sample = 1
        val maxDimension = maxOf(bounds.outWidth, bounds.outHeight)
        while (maxDimension / sample > 2048) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, options)
            ?: error("拍照文件无法解析，请重新拍摄")
        val importedAt = System.currentTimeMillis()
        val exifCaptureTime = captureTimeFromExif(exif, importedAt)
        val selected = persistNormalized(
            context, decoded, orientation, rotation, "camera", previewAspectRatio,
            captureTimestampEpochMs = captureTimestampEpochMs ?: exifCaptureTime.first,
            captureTimestampSource = if (captureTimestampEpochMs != null) "CAMERA_CAPTURE_CALLBACK" else exifCaptureTime.second,
        )
        CameraNormalizationResult(
            selectedImage = selected,
            rotationDegrees = rotation,
            decodedWidth = selected.bitmap.width,
            decodedHeight = selected.bitmap.height,
        )
    }

    private fun normalizeLocalFile(
        context: Context,
        file: File,
        source: String,
        invalidMessage: String,
    ): SelectedImage {
        val exif = runCatching { ExifInterface(file) }.getOrNull()
        val rotation = exif?.rotationDegrees ?: 0
        val orientation = exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            ?: ExifInterface.ORIENTATION_NORMAL

        // BitmapFactory intentionally returns null when inJustDecodeBounds=true;
        // valid decoding is determined from outWidth/outHeight instead.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { invalidMessage }

        var sample = 1
        val maxDimension = maxOf(bounds.outWidth, bounds.outHeight)
        while (maxDimension / sample > 2048) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, options)
            ?: error(invalidMessage)
        val captureTime = captureTimeFromExif(exif, System.currentTimeMillis())
        return persistNormalized(
            context, decoded, orientation, rotation, source,
            captureTimestampEpochMs = captureTime.first,
            captureTimestampSource = captureTime.second,
        )
    }

    private fun persistNormalized(
        context: Context,
        decoded: Bitmap,
        orientation: Int,
        rotation: Int,
        source: String,
        previewAspectRatio: Float? = null,
        captureTimestampEpochMs: Long,
        captureTimestampSource: String,
    ): SelectedImage {
        // Normalize all eight EXIF orientations, including mirrors, before any
        // detector, segmenter, crop or UI consumes this canonical bitmap.
        val oriented = applyExifOrientation(decoded, orientation)

        // CameraX's controller aligns Preview and ImageCapture using the attached
        // PreviewView ViewPort. The file output may still contain the full sensor
        // buffer: apply the visible FILL_CENTER rectangle after EXIF orientation.
        // Gallery input deliberately never receives this camera-only crop.
        val visible = if (previewAspectRatio != null && previewAspectRatio.isFinite() && previewAspectRatio > 0f) {
            val sourceRatio = oriented.width.toFloat() / oriented.height
            if (sourceRatio > previewAspectRatio) {
                val cropWidth = (oriented.height * previewAspectRatio).toInt().coerceIn(1, oriented.width)
                Bitmap.createBitmap(oriented, (oriented.width - cropWidth) / 2, 0, cropWidth, oriented.height)
            } else {
                val cropHeight = (oriented.width / previewAspectRatio).toInt().coerceIn(1, oriented.height)
                Bitmap.createBitmap(oriented, 0, (oriented.height - cropHeight) / 2, oriented.width, cropHeight)
            }
        } else oriented
        if (visible !== oriented) oriented.recycle()

        val dir = File(context.filesDir, "recognition_inputs").apply { mkdirs() }
        val normalized = File(dir, "input_${UUID.randomUUID()}.jpg")
        normalized.outputStream().use { output ->
            check(visible.compress(Bitmap.CompressFormat.JPEG, 92, output)) { "无法保存识别照片" }
        }
        check(normalized.exists() && normalized.length() > 0L) { "无法保存识别照片" }
        val sha256 = MessageDigest.getInstance("SHA-256").digest(normalized.readBytes())
            .joinToString("") { "%02x".format(it) }
        return SelectedImage(
            filePath = normalized.absolutePath,
            bitmap = visible,
            source = source,
            sourceOrientationDegrees = rotation,
            sourceExifOrientation = orientation,
            photoSha256 = sha256,
            captureTimestampEpochMs = captureTimestampEpochMs,
            captureTimestampSource = captureTimestampSource,
        )
    }

    private fun captureTimeFromExif(exif: ExifInterface?, fallbackEpochMs: Long): Pair<Long, String> {
        val raw = exif?.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?.takeIf(String::isNotBlank) ?: return fallbackEpochMs to "SYSTEM_INGESTION"
        val parser = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).apply { isLenient = false }
        val offset = exif?.getAttribute("OffsetTimeOriginal")
            ?.takeIf { it.matches(Regex("^[+-]\\d{2}:\\d{2}$")) }
        if (offset != null) {
            val sign = if (offset[0] == '-') -1 else 1
            val hours = offset.substring(1, 3).toIntOrNull() ?: return fallbackEpochMs to "SYSTEM_INGESTION"
            val minutes = offset.substring(4, 6).toIntOrNull() ?: return fallbackEpochMs to "SYSTEM_INGESTION"
            if (hours > 23 || minutes > 59) return fallbackEpochMs to "SYSTEM_INGESTION"
            val offsetMinutes = sign * (hours * 60 + minutes)
            val zoneId = "GMT${if (offsetMinutes >= 0) "+" else "-"}${String.format(Locale.US, "%02d:%02d", kotlin.math.abs(offsetMinutes) / 60, kotlin.math.abs(offsetMinutes) % 60)}"
            parser.timeZone = TimeZone.getTimeZone(zoneId)
        } else {
            // EXIF stores local wall time when OffsetTimeOriginal is absent. Bind it to
            // the device's configured zone at import and retain its source explicitly.
            parser.timeZone = TimeZone.getDefault()
        }
        val epochMs = runCatching { parser.parse(raw)?.time }.getOrNull()
            ?.takeIf { it > 0L } ?: return fallbackEpochMs to "SYSTEM_INGESTION"
        return epochMs to if (offset == null) "EXIF_DATETIME_ORIGINAL_DEVICE_ZONE" else "EXIF_DATETIME_ORIGINAL_OFFSET"
    }
}
