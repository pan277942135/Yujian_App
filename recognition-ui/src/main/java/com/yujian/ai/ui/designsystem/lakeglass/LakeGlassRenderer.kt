package com.yujian.ai.ui.designsystem.lakeglass

import androidx.compose.ui.graphics.RenderEffect

internal interface LakeGlassOptionalShaderProgram {
    fun update(
        viewport: LakeGlassViewportTransform,
        parameters: LakeGlassEffectParameters,
        elapsedSeconds: Float,
    )

    fun composeRenderEffect(): RenderEffect
}

/** Shared scene renderer; one viewport matrix and optional in-place AGSL effect. */
internal class LakeGlassRenderer(
    private val agslProgram: LakeGlassOptionalShaderProgram?,
) {
    fun update(
        viewport: LakeGlassViewportTransform,
        parameters: LakeGlassEffectParameters,
        elapsedSeconds: Float,
    ) {
        agslProgram?.update(viewport, parameters, elapsedSeconds)
    }

    fun renderEffect(
        apiLevel: Int,
        optedIn: Boolean,
    ): RenderEffect? = when (
        LakeGlassRenderPolicy.resolve(
            apiLevel = apiLevel,
            shaderCompiled = agslProgram != null,
            optedIn = optedIn,
        )
    ) {
        LakeGlassRenderMode.STATIC_COMPOSITE -> null
        LakeGlassRenderMode.OPTIONAL_AGSL -> agslProgram?.composeRenderEffect()
    }
}
