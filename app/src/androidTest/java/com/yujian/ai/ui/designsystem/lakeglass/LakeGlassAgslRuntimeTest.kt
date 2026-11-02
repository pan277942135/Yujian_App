package com.yujian.ai.ui.designsystem.lakeglass

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LakeGlassAgslRuntimeTest {
    @Test
    @RequiresApi(33)
    fun api33CompilesAndConfiguresAllThreeFixedDroplets() {
        assumeTrue("AGSL requires Android API 33+", Build.VERSION.SDK_INT >= 33)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val assets = runBlocking {
            LakeGlassAssetRepository.load(
                context = context,
                viewportWidthPx = 1080,
                viewportHeightPx = 2400,
                includeOpticalMask = true,
            )
        }
        assertEquals(listOf(LakeGlassSceneManifest.STATIC_LAYER_ID), assets.manifest.visibleRuntimeStack)
        assertEquals(3, assets.manifest.dropletLayers.size)
        assets.manifest.dropletLayers.forEach { layer ->
            assertEquals("SHADER_INPUTS_PROVISIONAL", layer.separationStatus)
            assertEquals(4, layer.pixelBounds.size)
            assertEquals(2, layer.anchorPx.size)
        }
        assertEquals(70f, assets.manifest.dropletLayers[0].anchorPx[0], 0f)
        assertEquals(713f, assets.manifest.dropletLayers[0].anchorPx[1], 0f)
        assertEquals(1361f, assets.manifest.sunlightLayer.anchorPx[0], 0f)
        assertEquals(109f, assets.manifest.sunlightLayer.anchorPx[1], 0f)
        val program = LakeGlassAgslProgram.create(context, assets)
        val parameters = LakeGlassEffectParameters.STATIC_DEFAULT.copy(
            causticsEnabled = true,
            sunlightEnabled = true,
            dropletsEnabled = true,
        )
        program.update(
            viewport = LakeGlassViewportTransform.centerCrop(1080f, 2400f),
            parameters = parameters,
            elapsedSeconds = 12.5f,
        )
        assertNotNull(program.composeRenderEffect())
    }
}
