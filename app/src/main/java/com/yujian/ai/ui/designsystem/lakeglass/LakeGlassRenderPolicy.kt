package com.yujian.ai.ui.designsystem.lakeglass

internal enum class LakeGlassRenderMode { STATIC_COMPOSITE, OPTIONAL_AGSL }

internal object LakeGlassRenderPolicy {
    fun resolve(apiLevel: Int, shaderCompiled: Boolean, optedIn: Boolean): LakeGlassRenderMode =
        if (apiLevel >= 33 && shaderCompiled && optedIn) {
            LakeGlassRenderMode.OPTIONAL_AGSL
        } else {
            LakeGlassRenderMode.STATIC_COMPOSITE
        }
}

/** Effects are intentionally opt-in until exact-device visual validation. */
internal data class LakeGlassEffectParameters(
    val causticsEnabled: Boolean = false,
    val causticsAmount: Float = 0.014f,
    val sunlightEnabled: Boolean = false,
    val sunlightAmount: Float = 0.007f,
    val dropletsEnabled: Boolean = false,
    val dropletAmount: Float = 6f,
    val drop01Enabled: Boolean = true,
    val drop02Enabled: Boolean = true,
    val drop03Enabled: Boolean = true,
    val motionEnabled: Boolean = false,
) {
    fun bounded(): LakeGlassEffectParameters = copy(
        causticsAmount = causticsAmount.coerceIn(0f, 0.035f),
        sunlightAmount = sunlightAmount.coerceIn(0f, 0.018f),
        dropletAmount = dropletAmount.coerceIn(0f, 12f),
    )

    val anyEffectEnabled: Boolean
        get() = causticsEnabled || sunlightEnabled || dropletsEnabled

    companion object {
        val STATIC_DEFAULT = LakeGlassEffectParameters()
    }
}
