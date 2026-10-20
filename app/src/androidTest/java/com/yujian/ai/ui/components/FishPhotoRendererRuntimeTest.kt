package com.yujian.ai.ui.components

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yujian.ai.ai.NormalizedFishBox
import com.yujian.ai.ui.imagery.FishPhotoBoundingBox
import com.yujian.ai.ui.imagery.FishPhotoDisplayMode
import com.yujian.ai.ui.imagery.FishPhotoFallbackReason

@RunWith(AndroidJUnit4::class)
class FishPhotoRendererRuntimeTest {
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
        source.recycle()
    }
}
