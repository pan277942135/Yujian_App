package com.yujian.ai.ui.designsystem.lakeglass

import org.junit.Assert.assertEquals
import org.junit.Test

class LakeGlassRenderPolicyContractTest {
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
}
