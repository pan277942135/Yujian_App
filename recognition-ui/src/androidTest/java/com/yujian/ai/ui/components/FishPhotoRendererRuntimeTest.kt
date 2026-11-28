package com.yujian.ai.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yujian.ai.ai.NormalizedFishBox
import com.yujian.ai.ui.imagery.FishPhotoBoundingBox
import com.yujian.ai.ui.imagery.FishPhotoDisplayMode
import com.yujian.ai.ui.imagery.FishPhotoFallbackReason
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class FishPhotoRendererRuntimeTest {
    @Test
    fun labelledLandscapeAndPortraitFishBitmapsRenderWithoutLosingTheirBoxes() {
        data class Fixture(
            val name: String,
            val width: Int,
            val height: Int,
            val box: com.yujian.ai.ai.NormalizedFishBox,
            val horizontalBody: Boolean,
            val targetWidth: Int,
            val targetHeight: Int,
        )

        val fixtures = listOf(
            Fixture("landscape-labelled-v1", 1600, 900, NormalizedFishBox(0.28f, 0.32f, 0.68f, 0.68f), true, 494, 586),
            Fixture("portrait-labelled-v1", 900, 1600, NormalizedFishBox(0.35f, 0.20f, 0.65f, 0.80f), false, 494, 586),
            Fixture("left-edge-labelled-v1", 1000, 1000, NormalizedFishBox(0.001f, 0.40f, 0.091f, 0.52f), true, 400, 400),
            Fixture("right-edge-labelled-v1", 1000, 1000, NormalizedFishBox(0.909f, 0.40f, 0.999f, 0.52f), true, 400, 400),
            Fixture("top-edge-labelled-v1", 1000, 1000, NormalizedFishBox(0.40f, 0.001f, 0.52f, 0.091f), false, 400, 400),
            Fixture("bottom-edge-labelled-v1", 1000, 1000, NormalizedFishBox(0.40f, 0.909f, 0.52f, 0.999f), false, 400, 400),
        )

        fixtures.forEach { fixture ->
            val source = Bitmap.createBitmap(fixture.width, fixture.height, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.rgb(212, 224, 229))
            }
            val boxPx = RectF(
                fixture.box.x1 * fixture.width,
                fixture.box.y1 * fixture.height,
                fixture.box.x2 * fixture.width,
                fixture.box.y2 * fixture.height,
            )
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.RED; style = Paint.Style.FILL }
            val canvas = Canvas(source)
            val body = if (fixture.horizontalBody) {
                RectF(boxPx.left + boxPx.width() * 0.12f, boxPx.top + boxPx.height() * 0.22f,
                    boxPx.right - boxPx.width() * 0.10f, boxPx.bottom - boxPx.height() * 0.22f)
            } else {
                RectF(boxPx.left + boxPx.width() * 0.22f, boxPx.top + boxPx.height() * 0.10f,
                    boxPx.right - boxPx.width() * 0.22f, boxPx.bottom - boxPx.height() * 0.12f)
            }
            canvas.drawOval(body, paint)
            val tail = Path().apply {
                if (fixture.horizontalBody) {
                    moveTo(body.left, body.centerY())
                    lineTo(boxPx.left, boxPx.top)
                    lineTo(boxPx.left, boxPx.bottom)
                } else {
                    moveTo(body.centerX(), body.top)
                    lineTo(boxPx.left, boxPx.top)
                    lineTo(boxPx.right, boxPx.top)
                }
                close()
            }
            canvas.drawPath(tail, paint)

            val result = FishPhotoRenderer.render(
                source = source,
                targetWidthPx = fixture.targetWidth,
                targetHeightPx = fixture.targetHeight,
                sourceVersion = fixture.name,
                boundingBox = FishPhotoBoundingBox(fixture.box, 0.93f, version = fixture.name),
            )

            assertEquals(FishPhotoDisplayMode.SUBJECT_CROP, result.plan.mode)
            assertTrue(result.plan.retainsWholeBoundingBox)
            val expectedAspect = fixture.targetWidth.toFloat() / fixture.targetHeight
            assertEquals(expectedAspect, result.plan.cropRect!!.aspectRatio, 0.0001f)
            val center = result.bitmap.getPixel(fixture.targetWidth / 2, fixture.targetHeight / 2)
            assertTrue("${fixture.name} center pixel should still be inside the fish", Color.red(center) > Color.green(center))
            saveCropEvidence(fixture.name, source, fixture.box, fixture.targetWidth, fixture.targetHeight, result)
            source.recycle()
        }
    }

    @Test
    fun realBitmapCropRetainsBoxUsesTargetAspectAndCacheIsStable() {
        val source = Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
            for (y in 280..600) for (x in 340..850) setPixel(x, y, Color.RED)
        }
        val bbox = FishPhotoBoundingBox(
            NormalizedFishBox(0.29f, 0.30f, 0.73f, 0.68f),
            confidence = 0.91f,
            version = "runtime-fixture-bbox-v1",
        )

        val first = FishPhotoRenderer.render(source, 480, 320, "runtime-fixture-source-v1", bbox)
        val second = FishPhotoRenderer.render(source, 480, 320, "runtime-fixture-source-v1", bbox)

        assertEquals(FishPhotoDisplayMode.SUBJECT_CROP, first.plan.mode)
        assertTrue(first.plan.retainsWholeBoundingBox)
        assertEquals(1.5f, first.bitmap.width.toFloat() / first.bitmap.height, 0.005f)
        assertEquals(first.cacheKey, second.cacheKey)
        assertEquals(first.bitmap.generationId, second.bitmap.generationId)
        assertEquals(Color.RED, first.bitmap.getPixel(first.bitmap.width / 2, first.bitmap.height / 2))
        source.recycle()
    }

    @Test
    fun noBoxUsesOpaqueSameSourceFallbackAtExactRatio() {
        val source = Bitmap.createBitmap(320, 640, Bitmap.Config.ARGB_8888).apply {
            for (y in 0 until height) for (x in 0 until width) {
                setPixel(x, y, Color.rgb((x * 255 / width), (y * 255 / height), 140))
            }
        }

        val result = FishPhotoRenderer.render(source, 400, 300, "runtime-no-box-source-v1")

        assertEquals(FishPhotoDisplayMode.FULL_FRAME_WITH_SOURCE_BACKDROP, result.plan.mode)
        assertEquals(4f / 3f, result.bitmap.width.toFloat() / result.bitmap.height, 0.005f)
        assertEquals(FishPhotoFallbackReason.MISSING_BBOX, result.plan.fallbackReason)
        for (y in 0 until result.bitmap.height step 17) {
            for (x in 0 until result.bitmap.width step 17) {
                assertEquals(255, Color.alpha(result.bitmap.getPixel(x, y)))
            }
        }
        assertTrue(result.bitmap.getPixel(2, result.bitmap.height / 2) != result.bitmap.getPixel(result.bitmap.width / 2, 2))
        saveCropEvidence("no-reliable-bbox-v1", source, null, 400, 300, result)
        source.recycle()
    }

    private fun saveCropEvidence(
        fixtureName: String,
        source: Bitmap,
        box: NormalizedFishBox?,
        targetWidth: Int,
        targetHeight: Int,
        result: RenderedFishPhoto,
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "fish-photo-crop-acceptance").apply { mkdirs() }
        val safeName = fixtureName.replace(Regex("[^A-Za-z0-9_-]"), "_")
        FileOutputStream(File(directory, "input-$safeName.png")).use {
            source.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        FileOutputStream(File(directory, "output-$safeName.png")).use {
            result.bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        val bbox = box?.let {
            "(${format(it.x1)},${format(it.y1)},${format(it.x2)},${format(it.y2)})"
        } ?: "none"
        val bboxPixels = box?.let {
            val left = (it.x1 * source.width).toInt()
            val top = (it.y1 * source.height).toInt()
            val right = (it.x2 * source.width).toInt()
            val bottom = (it.y2 * source.height).toInt()
            "($left,$top,$right,$bottom)"
        } ?: "none"
        val crop = result.plan.cropRect?.let {
            "(${format(it.left)},${format(it.top)},${format(it.right)},${format(it.bottom)})"
        } ?: "none"
        val record = listOf(
            "case=$fixtureName",
            "input_id=deterministic-android-test-bitmap-v1",
            "input=${source.width}x${source.height}",
            "bbox_normalized=$bbox",
            "bbox_pixels=$bboxPixels",
            "target=${targetWidth}x${targetHeight}",
            "crop_pixels=$crop",
            "margin=${result.plan.marginLevelPercent ?: "none"}",
            "attempts=${result.plan.attemptedMarginLevels.joinToString(",")}",
            "mode=${result.plan.mode}",
            "fallback=${result.plan.fallbackReason}",
            "bbox_retained=${result.plan.retainsWholeBoundingBox}",
            "output=${result.bitmap.width}x${result.bitmap.height}",
            "cache_key=${result.cacheKey}",
        ).joinToString(" ")
        val manifestName = if (box == null) "fallback-cases.txt" else "labelled-cases.txt"
        File(directory, manifestName).appendText("$record\n")
        android.util.Log.i("FishPhotoCropAcceptance", record)
    }

    private fun format(value: Float): String = String.format(Locale.US, "%.2f", value)
}
