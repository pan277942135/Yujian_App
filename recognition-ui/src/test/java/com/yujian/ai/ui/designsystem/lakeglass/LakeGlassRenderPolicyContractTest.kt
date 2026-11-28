package com.yujian.ai.ui.designsystem.lakeglass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LakeGlassRenderPolicyContractTest {
    @Test
    fun viewportUsesOneCenterCropMatrixForAllMasterAnchors() {
        val viewport = LakeGlassViewportTransform.centerCrop(320f, 480f)
        assertEquals(320f, viewport.scale * LakeGlassViewportTransform.MASTER_WIDTH, 0.001f)
        assertEquals(693.333f, viewport.scale * LakeGlassViewportTransform.MASTER_HEIGHT, 0.01f)
        assertEquals(-106.667f, viewport.offsetY, 0.01f)

        val anchor = viewport.masterToViewport(1361f, 109f)
        val restored = viewport.viewportToMaster(anchor.first, anchor.second)
        assertEquals(1361f, restored.first, 0.01f)
        assertEquals(109f, restored.second, 0.01f)
    }

    @Test
    fun requiredViewportsNeverStretchTheMaster() {
        val viewports = listOf(
            320f to 480f,
            360f to 640f,
            390f to 844f,
            430f to 932f,
        )
        viewports.forEach { (width, height) ->
            val transform = LakeGlassViewportTransform.centerCrop(width, height)
            val sceneAspect = LakeGlassViewportTransform.MASTER_WIDTH / LakeGlassViewportTransform.MASTER_HEIGHT
            val drawnAspect = (LakeGlassViewportTransform.MASTER_WIDTH * transform.scale) /
                (LakeGlassViewportTransform.MASTER_HEIGHT * transform.scale)
            assertEquals(sceneAspect, drawnAspect, 0.000001f)
        }
    }

    @Test
    fun apiPolicyKeepsLegacyAndUnverifiedDevicesOnStaticComposite() {
        assertEquals(
            LakeGlassRenderMode.STATIC_COMPOSITE,
            LakeGlassRenderPolicy.resolve(apiLevel = 24, shaderCompiled = true, optedIn = true),
        )
        assertEquals(
            LakeGlassRenderMode.STATIC_COMPOSITE,
            LakeGlassRenderPolicy.resolve(apiLevel = 32, shaderCompiled = true, optedIn = true),
        )
        assertEquals(
            LakeGlassRenderMode.STATIC_COMPOSITE,
            LakeGlassRenderPolicy.resolve(apiLevel = 33, shaderCompiled = false, optedIn = true),
        )
        assertEquals(
            LakeGlassRenderMode.STATIC_COMPOSITE,
            LakeGlassRenderPolicy.resolve(apiLevel = 33, shaderCompiled = true, optedIn = false),
        )
        assertEquals(
            LakeGlassRenderMode.OPTIONAL_AGSL,
            LakeGlassRenderPolicy.resolve(apiLevel = 33, shaderCompiled = true, optedIn = true),
        )
    }

    @Test
    fun effectParametersAreBoundedAndStaticByDefault() {
        val defaults = LakeGlassEffectParameters.STATIC_DEFAULT
        assertFalse(defaults.anyEffectEnabled)
        assertFalse(defaults.motionEnabled)
        val bounded = defaults.copy(
            causticsAmount = 2f,
            sunlightAmount = -2f,
            dropletAmount = 100f,
        ).bounded()
        assertEquals(0.035f, bounded.causticsAmount, 0f)
        assertEquals(0f, bounded.sunlightAmount, 0f)
        assertEquals(12f, bounded.dropletAmount, 0f)
    }
}
