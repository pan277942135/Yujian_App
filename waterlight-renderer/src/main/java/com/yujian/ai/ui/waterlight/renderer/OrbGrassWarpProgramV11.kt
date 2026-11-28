package com.yujian.ai.ui.waterlight.renderer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import java.io.BufferedReader
import java.io.InputStreamReader

/** One in-place effect on the existing Core Image layer; masks are immutable inputs, never leaves. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class OrbGrassWarpProgramV11 private constructor(
    private val shader: RuntimeShader,
    val renderEffect: RenderEffect,
    private val maskBitmaps: List<Bitmap>,
) : AutoCloseable {
    fun update(sceneElapsedMs: Long, s2Progress: Float, grassLight: Float, surfaceSizePx: Float) {
        val size = surfaceSizePx.coerceAtLeast(1f)
        shader.setFloatUniform("surfaceSize", size, size)
        shader.setFloatUniform("maskSize", maskBitmaps.first().width.toFloat())
        shader.setFloatUniform("sceneSeconds", sceneElapsedMs.coerceAtLeast(0L) / 1000f)
        shader.setFloatUniform("s2Progress", s2Progress.coerceIn(0f, 1f))
        shader.setFloatUniform("grassLight", grassLight.coerceIn(0f, 1f))
        shader.setFloatUniform("visibleDiameterRatio", OrbGrassMotionProfileV11.VISIBLE_DIAMETER_TO_SOURCE_UV)
    }

    override fun close() {
        maskBitmaps.forEach { if (!it.isRecycled) it.recycle() }
    }

    companion object {
        private const val TAG = "OrbGrassWarpV11"

        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        fun create(context: Context): OrbGrassWarpProgramV11 {
            val decoded = mutableListOf<Bitmap>()
            try {
                OrbGrassMotionProfileV11.leaves.forEach { leaf ->
                    val bitmap = context.assets.open(OrbGrassMotionProfileV11.ASSET_DIR + leaf.maskAsset).use { stream ->
                        BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply {
                            inScaled = false
                            inSampleSize = 3
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                        })
                    } ?: error("Could not decode ${leaf.maskAsset}")
                    require(bitmap.width > 0 && bitmap.height > 0 && bitmap.width == bitmap.height) {
                        "Invalid mask dimensions for ${leaf.maskAsset}"
                    }
                    decoded += bitmap
                }
                val shaderSource = context.assets.open(OrbGrassMotionProfileV11.AGSL_ASSET).use { input ->
                    BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
                }
                val runtimeShader = RuntimeShader(shaderSource)
                decoded.forEachIndexed { index, bitmap ->
                    runtimeShader.setInputShader(
                        listOf("maskBackB1", "maskBackB2", "maskFrontF1", "maskFrontF2")[index],
                        BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP),
                    )
                }
                val effect = AndroidRenderEffect.createRuntimeShaderEffect(runtimeShader, "inputImage")
                    .asComposeRenderEffect()
                Log.i(TAG, "loaded source-derived R01B masks=${decoded.size} sample=${decoded.first().width}px")
                return OrbGrassWarpProgramV11(runtimeShader, effect, decoded.toList())
            } catch (failure: Throwable) {
                decoded.forEach { if (!it.isRecycled) it.recycle() }
                throw failure
            }
        }
    }
}
