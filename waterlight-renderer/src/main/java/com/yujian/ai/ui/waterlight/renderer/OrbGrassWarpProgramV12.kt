package com.yujian.ai.ui.waterlight.renderer

import android.content.Context
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.BufferedReader
import java.io.InputStreamReader

/** V1.2 shared-current candidate using the same four frozen V1.1 masks and path anatomy. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class OrbGrassWarpProgramV12 private constructor(
    private val shader: RuntimeShader,
    val androidRenderEffect: AndroidRenderEffect,
    private val maskSizePx: Float,
) {
    fun update(frame: com.yujian.ai.ui.waterlight.core.WaterLightFrame, s2Progress: Float, grassLight: Float, surfaceSizePx: Float) {
        val profile = OrbFluidOpticalProfileV1
        val size = surfaceSizePx.coerceAtLeast(1f)
        shader.setFloatUniform("surfaceSize", size, size)
        shader.setFloatUniform("maskSize", maskSizePx)
        shader.setFloatUniform("sceneSeconds", if (frame.reducedMotion) 0f else frame.elapsedMs.coerceAtLeast(0L) / 1000f)
        shader.setFloatUniform("s2Progress", s2Progress.coerceIn(0f, 1f))
        shader.setFloatUniform("grassLight", grassLight.coerceIn(0f, 1f))
        shader.setFloatUniform("visibleDiameterRatio", OrbGrassMotionProfileV11.VISIBLE_DIAMETER_TO_SOURCE_UV)
        shader.setFloatUniform("sharedLocalWeight", profile.GRASS_LOCAL_WEIGHT)
        shader.setFloatUniform("sharedCurrentWeight", profile.GRASS_SHARED_WEIGHT)
        shader.setFloatUniform("flutterWeight", profile.GRASS_FLUTTER_WEIGHT)
        shader.setFloatUniform("sharedAngularFrequency", profile.GRASS_SHARED_ANGULAR_FREQUENCY)
        shader.setFloatUniform("secondaryFrequencyMultiplier", profile.GRASS_SECONDARY_FREQUENCY_MULTIPLIER)
        shader.setFloatUniform("sharedXScale", profile.GRASS_SHARED_X_SCALE)
        shader.setFloatUniform("sharedYScale", profile.GRASS_SHARED_Y_SCALE)
        shader.setFloatUniform("flutterYScale", profile.GRASS_FLUTTER_Y_SCALE)
        shader.setFloatUniform("flutterPhaseScale", profile.GRASS_FLUTTER_PHASE_SCALE)
    }

    companion object {
        private const val TAG = "OrbGrassWarpV12"
        private const val SHADER_ASSET = "orb_r01b_v1_2/orb_grass_warp_v1_2.agsl"

        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        fun create(context: Context, masks: OrbR01BMaskSetV1): OrbGrassWarpProgramV12 {
            val source = context.assets.open(SHADER_ASSET).use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
            }
            val runtimeShader = RuntimeShader(source)
            masks.bind(runtimeShader)
            val effect = AndroidRenderEffect.createRuntimeShaderEffect(runtimeShader, "inputImage")
            Log.i(TAG, "loaded shared-current candidate with shared frozen R01B masks")
            return OrbGrassWarpProgramV12(runtimeShader, effect, masks.maskSizePx)
        }
    }
}
