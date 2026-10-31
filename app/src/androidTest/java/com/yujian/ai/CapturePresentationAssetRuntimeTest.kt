package com.yujian.ai

import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

/** Runs against the exact installed APK, so a valid filename cannot mask corrupt bytes. */
class CapturePresentationAssetRuntimeTest {
    @Test fun androidDecodesCaptureAndProcessingResourcesFromInstalledApk() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val assets = listOf(
            Triple(R.drawable.capture_button_main_v1, true, 192),
            Triple(R.drawable.ai_orb_camera_activated_v1, true, 256),
            Triple(R.drawable.camera_mint_background_v2, false, 256),
            Triple(R.drawable.processing_water_core_v1, true, 256),
            Triple(R.drawable.processing_mint_background_v1, false, 256),
        )
        for ((id, alpha, minDimension) in assets) {
            val bitmap = BitmapFactory.decodeResource(context.resources, id)
            assertNotNull("Android cannot decode resource $id", bitmap)
            requireNotNull(bitmap)
            assertTrue(bitmap.width >= minDimension && bitmap.height >= minDimension)
            if (alpha) assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)))
            bitmap.recycle()
        }
    }
}
