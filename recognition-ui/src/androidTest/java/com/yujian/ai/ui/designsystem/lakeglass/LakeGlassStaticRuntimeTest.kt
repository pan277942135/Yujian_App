package com.yujian.ai.ui.designsystem.lakeglass

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LakeGlassStaticRuntimeTest {
    @Test
    fun api24To32LoadsOnlyVerifiedStaticCompositeAndIgnoresAgslOptIn() {
        assumeTrue("Static compatibility test targets API 24–32", Build.VERSION.SDK_INT in 24..32)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val assets = runBlocking {
            LakeGlassAssetRepository.load(
                context = context,
                viewportWidthPx = 320,
                viewportHeightPx = 480,
                includeOpticalMask = false,
            )
        }

        assertEquals(listOf(LakeGlassSceneManifest.STATIC_LAYER_ID), assets.manifest.visibleRuntimeStack)
        assertFalse(assets.usingMasterFallback)
        assertNull(assets.opticalMaskBitmap)
        assertNotNull(assets.baseBitmap)
        assertEquals(
            null,
            LakeGlassRenderer(agslProgram = null).renderEffect(
                apiLevel = Build.VERSION.SDK_INT,
                optedIn = true,
            ),
        )
        assertEquals(
            LakeGlassRenderMode.STATIC_COMPOSITE,
            LakeGlassRenderPolicy.resolve(Build.VERSION.SDK_INT, shaderCompiled = true, optedIn = true),
        )
    }
}
