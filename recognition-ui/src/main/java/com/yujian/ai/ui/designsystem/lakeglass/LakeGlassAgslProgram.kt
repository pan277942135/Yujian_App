package com.yujian.ai.ui.designsystem.lakeglass

import android.graphics.BitmapShader
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import java.io.BufferedReader
import java.io.InputStreamReader

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class LakeGlassAgslProgram(
    private val shader: RuntimeShader,
    private val renderEffect: RenderEffect,
    private val sceneAssets: LakeGlassSceneAssets,
) : LakeGlassOptionalShaderProgram {
    override fun update(
        viewport: LakeGlassViewportTransform,
        parameters: LakeGlassEffectParameters,
        elapsedSeconds: Float,
    ) {
        val bounded = parameters.bounded()
        shader.setFloatUniform("surfaceSize", viewport.viewportWidth, viewport.viewportHeight)
        shader.setFloatUniform("sceneScale", viewport.scale)
        shader.setFloatUniform("cropOffset", viewport.offsetX, viewport.offsetY)
        shader.setFloatUniform("elapsedSeconds", elapsedSeconds)
        shader.setFloatUniform("causticsEnabled", bounded.causticsEnabled.asFloat())
        shader.setFloatUniform("causticsAmount", bounded.causticsAmount)
        shader.setFloatUniform("sunlightEnabled", bounded.sunlightEnabled.asFloat())
        shader.setFloatUniform("sunlightAmount", bounded.sunlightAmount)
        shader.setFloatUniform("dropletsEnabled", bounded.dropletsEnabled.asFloat())
        shader.setFloatUniform("dropletAmount", bounded.dropletAmount)
        shader.setFloatUniform("drop01Enabled", bounded.drop01Enabled.asFloat())
        shader.setFloatUniform("drop02Enabled", bounded.drop02Enabled.asFloat())
        shader.setFloatUniform("drop03Enabled", bounded.drop03Enabled.asFloat())
        shader.setFloatUniform("drop01Bounds", sceneAssets.manifest.dropletLayers[0].pixelBounds)
        shader.setFloatUniform("drop02Bounds", sceneAssets.manifest.dropletLayers[1].pixelBounds)
        shader.setFloatUniform("drop03Bounds", sceneAssets.manifest.dropletLayers[2].pixelBounds)
        shader.setFloatUniform("drop01Anchor", sceneAssets.manifest.dropletLayers[0].anchorPx[0], sceneAssets.manifest.dropletLayers[0].anchorPx[1])
        shader.setFloatUniform("drop02Anchor", sceneAssets.manifest.dropletLayers[1].anchorPx[0], sceneAssets.manifest.dropletLayers[1].anchorPx[1])
        shader.setFloatUniform("drop03Anchor", sceneAssets.manifest.dropletLayers[2].anchorPx[0], sceneAssets.manifest.dropletLayers[2].anchorPx[1])
        shader.setFloatUniform("sunlightAnchor", sceneAssets.manifest.sunlightLayer.anchorPx[0], sceneAssets.manifest.sunlightLayer.anchorPx[1])
    }

    override fun composeRenderEffect(): RenderEffect = renderEffect

    companion object {
        const val SHADER_ASSET = "lake_glass_v2/shaders/lake_glass_v2.agsl"

        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        fun create(context: android.content.Context, assets: LakeGlassSceneAssets): LakeGlassAgslProgram {
            require(assets.opticalMaskBitmap != null) { "API 33 shader requires a verified optical mask" }
            require(assets.manifest.api33EffectsAreOptional)
            require(assets.manifest.dropletLayers.size == 3)
            val source = context.assets.open(SHADER_ASSET).use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
            }
            val shader = RuntimeShader(source)
            val maskShader = BitmapShader(
                assets.opticalMaskBitmap,
                Shader.TileMode.CLAMP,
                Shader.TileMode.CLAMP,
            )
            shader.setInputShader("opticalMasks", maskShader)
            val effect = AndroidRenderEffect.createRuntimeShaderEffect(shader, "inputImage")
                .asComposeRenderEffect()
            return LakeGlassAgslProgram(shader, effect, assets)
        }
    }
}

private fun Boolean.asFloat(): Float = if (this) 1f else 0f
