package com.yujian.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yujian.ai.media.RecognitionImageStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RecognitionImageStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun cameraAndGalleryPhotosUseTheSamePrivateNormalizedImageContract() = runBlocking {
        val inputDir = File(context.cacheDir, "camera").apply { mkdirs() }
        val cameraInput = File(inputDir, "capture-test.jpg")
        val galleryInput = File(inputDir, "gallery-test.jpg")
        val bitmap = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.rgb(28, 94, 112))
        }
        val normalizedOutputs = mutableListOf<File>()

        try {
            cameraInput.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it)) }
            galleryInput.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it)) }

            val camera = RecognitionImageStore.normalizeCameraFile(context, cameraInput)
            normalizedOutputs += File(camera.filePath)
            val galleryUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                galleryInput,
            )
            val gallery = RecognitionImageStore.normalize(context, galleryUri, "gallery")
            normalizedOutputs += File(gallery.filePath)

            assertEquals("camera", camera.source)
            assertEquals("gallery", gallery.source)
            listOf(camera, gallery).forEach { selected ->
                val output = File(selected.filePath)
                assertTrue(output.exists())
                val decoded = requireNotNull(BitmapFactory.decodeFile(output.absolutePath))
                assertEquals(64, decoded.width)
                assertEquals(48, decoded.height)
                decoded.recycle()
            }
        } finally {
            bitmap.recycle()
            cameraInput.delete()
            galleryInput.delete()
            normalizedOutputs.forEach { it.delete() }
        }
    }

    @Test
    fun sourceByteHashAndCanonicalModelPixelHashRemainDistinct() = runBlocking {
        val bitmap = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.rgb(28, 94, 112))
        }
        val lowQuality = File(context.cacheDir, "camera/identity-low-quality.jpg")
        val highQuality = File(context.cacheDir, "camera/identity-high-quality.jpg")
        val normalized = mutableListOf<File>()
        try {
            lowQuality.parentFile?.mkdirs()
            lowQuality.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 40, it)) }
            highQuality.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 96, it)) }

            val first = RecognitionImageStore.normalizeCameraFile(context, lowQuality)
            val second = RecognitionImageStore.normalizeCameraFile(context, highQuality)
            normalized += File(first.filePath)
            normalized += File(second.filePath)
            val firstIdentity = requireNotNull(first.photoIdentity)
            val secondIdentity = requireNotNull(second.photoIdentity)

            assertNotEquals(firstIdentity.sourceBytesSha256, secondIdentity.sourceBytesSha256)
            assertEquals(firstIdentity.canonicalPixelSha256, secondIdentity.canonicalPixelSha256)
            assertEquals(firstIdentity.canonicalPixelSha256, first.photoSha256)
            assertNotEquals(firstIdentity.sourceBytesSha256, firstIdentity.canonicalPixelSha256)
            assertEquals(ExifInterface.ORIENTATION_NORMAL, firstIdentity.sourceOrientation)
            assertTrue(firstIdentity.isValid())
        } finally {
            bitmap.recycle()
            lowQuality.delete()
            highQuality.delete()
            normalized.forEach { it.delete() }
        }
    }

    @Test
    fun emptyCameraOutputReturnsAnActionableCaptureMessage() = runBlocking {
        val emptyInput = File(context.cacheDir, "camera/capture-empty-test.jpg").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf())
        }
        val failure = runCatching { RecognitionImageStore.normalizeCameraFile(context, emptyInput) }
            .exceptionOrNull()

        try {
            assertEquals("没有读取到拍照内容，请重新拍摄", failure?.message)
        } finally {
            emptyInput.delete()
        }
    }

    @Test
    fun cameraTargetUriWritesToTheFileConsumedByRecognitionHandoff() {
        val target = RecognitionImageStore.createCameraTarget(context)
        try {
            context.contentResolver.openOutputStream(target.uri)?.use { output ->
                output.write(byteArrayOf(0x01, 0x02, 0x03))
            } ?: error("camera target URI is not writable")
            assertTrue(target.file.exists())
            assertTrue(target.file.length() > 0L)
            assertEquals("${context.packageName}.fileprovider", target.uri.authority)
        } finally {
            target.file.delete()
        }
    }
}
