package com.yujian.ai.ui.designsystem.lakeglass

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LakeGlassRuntimeContractTest {
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

    @Test
    fun canonicalManifestHasOnlyOneVisibleBaseAndVerifiedAssetHashes() {
        val assetRoot = listOf(
            File("src/main/assets/lake_glass_v2"),
            File("app/src/main/assets/lake_glass_v2"),
        ).firstOrNull { File(it, "assets/android_runtime/layer_manifest_v1.json").isFile }
        val root = requireNotNull(assetRoot)
        val manifestText = File(root, "assets/android_runtime/layer_manifest_v1.json").readText()
        assertTrue(manifestText.contains("\"visibleRuntimeStack\": [\n    \"LAYER_STATIC_COMPOSITE\""))
        assertTrue(manifestText.contains("\"api33EffectsAreOptional\"") || manifestText.contains("OPTIONAL_AGSL_IN_PLACE_MODULATION"))
        val static = File(root, "assets/android_runtime/api24_static/lake_glass_static_layered_v3.png")
        val fallback = File(root, "assets/android_runtime/api24_static/lake_glass_master_fallback.png")
        val mask = File(root, "assets/android_runtime/runtime_optical_masks_v1.png")
        assertTrue(static.isFile && fallback.isFile && mask.isFile)
        assertEquals("23a493146f7fbb06469e56ae18b2ed8d58b610d981169c8987319481c0df5368", sha256(static.readBytes()))
        assertEquals("d0bd07ba379491e8b93468c616468fc60d59a2dfebc11061cf4d14e9788c6e12", sha256(fallback.readBytes()))
        assertEquals("2eae796055e1417f6dc9b81ae49e4167c68796d87b976ea1f02cceca421d26b0", sha256(mask.readBytes()))
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }
}
