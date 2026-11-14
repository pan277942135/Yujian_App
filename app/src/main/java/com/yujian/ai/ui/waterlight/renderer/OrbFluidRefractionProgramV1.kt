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
import com.yujian.ai.ui.waterlight.core.WaterLightFrame
import java.io.BufferedReader
import java.io.InputStreamReader

/** One composition-lifetime decode shared by both candidate shaders. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class OrbR01BMaskSetV1 private constructor(private val bitmaps: List<Bitmap>) : AutoCloseable {
    val maskSizePx: Float get() = bitmaps.first().width.toFloat()

    fun bind(shader: RuntimeShader) {
        bitmaps.forEachIndexed { index, bitmap ->
            shader.setInputShader(
                listOf("maskBackB1", "maskBackB2", "maskFrontF1", "maskFrontF2")[index],
                BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP),
            )
        }
    }

    override fun close() {
        bitmaps.forEach { if (!it.isRecycled) it.recycle() }
    }

    companion object {
        fun create(context: Context): OrbR01BMaskSetV1 {
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
                return OrbR01BMaskSetV1(decoded.toList())
            } catch (failure: Throwable) {
                decoded.forEach { if (!it.isRecycled) it.recycle() }
                throw failure
            }
        }
    }
}

/** Low-frequency liquid flow and alpha-edge refraction on the existing Orb image. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class OrbFluidRefractionProgramV1 private constructor(
    private val shader: RuntimeShader,
    val androidRenderEffect: AndroidRenderEffect,
    private val maskSizePx: Float,
) {
    fun update(frame: WaterLightFrame, surfaceSizePx: Float) {
        val size = surfaceSizePx.coerceAtLeast(1f)
        val profile = OrbFluidOpticalProfileV1
        shader.setFloatUniform("surfaceSize", size, size)
        shader.setFloatUniform("maskSize", maskSizePx)
        shader.setFloatUniform("sceneSeconds", if (frame.reducedMotion) 0f else frame.elapsedMs.coerceAtLeast(0L) / 1000f)
        shader.setFloatUniform("visibleDiameterRatio", OrbGrassMotionProfileV11.VISIBLE_DIAMETER_TO_SOURCE_UV)
        shader.setFloatUniform("flowScaleA", profile.FLOW_SCALE_A)
        shader.setFloatUniform("flowScaleB", profile.FLOW_SCALE_B)
        shader.setFloatUniform("flowDomainWarp", profile.FLOW_DOMAIN_WARP)
        shader.setFloatUniform("flowMaxDisplacementD", profile.FLOW_MAX_DISPLACEMENT_D)
        shader.setFloatUniform("flowTimeRateA", profile.FLOW_TIME_RATE_A)
        shader.setFloatUniform("flowTimeRateB", profile.FLOW_TIME_RATE_B)
        shader.setFloatUniform("refractionMaxDisplacementD", profile.REFRACTION_MAX_DISPLACEMENT_D)
        shader.setFloatUniform("refractionEdgeBandD", profile.REFRACTION_EDGE_BAND_D)
        shader.setFloatUniform("refractionNormalStrength", profile.REFRACTION_NORMAL_STRENGTH)
        shader.setFloatUniform("waterMixMax", profile.WATER_MIX_MAX)
        shader.setFloatUniform("edgeRefractionMixMax", profile.EDGE_REFRACTION_MIX_MAX)
        shader.setFloatUniform("totalSampleMixMax", profile.TOTAL_SAMPLE_MIX_MAX)
    }

    companion object {
        private const val TAG = "OrbFluidRefractionV1"
        private const val ASSET = "orb_fluid_v1/orb_fluid_refraction_v1.agsl"
        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        fun create(context: Context, masks: OrbR01BMaskSetV1): OrbFluidRefractionProgramV1 {
            OrbFluidOpticalProfileV1.validateBudgets()
            val source = context.assets.open(ASSET).use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
            }
            val runtimeShader = RuntimeShader(source)
            masks.bind(runtimeShader)
            val effect = AndroidRenderEffect.createRuntimeShaderEffect(runtimeShader, "inputImage")
            Log.i(TAG, "loaded fluid refraction with shared frozen R01B masks")
            return OrbFluidRefractionProgramV1(runtimeShader, effect, masks.maskSizePx)
        }
    }
}

/** Correct inner-to-outer chain: liquid refraction runs first; V1.2 leaf warp runs second. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class OrbFluidGrassOpticalStackV1 private constructor(
    private val masks: OrbR01BMaskSetV1,
    private val fluid: OrbFluidRefractionProgramV1,
    private val grass: OrbGrassWarpProgramV12,
    val renderEffect: RenderEffect,
) : AutoCloseable {
    fun update(frame: WaterLightFrame, s2Progress: Float, grassLight: Float, surfaceSizePx: Float) {
        fluid.update(frame, surfaceSizePx)
        grass.update(frame, s2Progress, grassLight, surfaceSizePx)
    }

    override fun close() {
        masks.close()
    }

    companion object {
        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        fun create(context: Context): OrbFluidGrassOpticalStackV1 {
            val masks = OrbR01BMaskSetV1.create(context)
            try {
                val createdGrass = OrbGrassWarpProgramV12.create(context, masks)
                val createdFluid = OrbFluidRefractionProgramV1.create(context, masks)
                val chained = AndroidRenderEffect.createChainEffect(
                    createdGrass.androidRenderEffect,
                    createdFluid.androidRenderEffect,
                ).asComposeRenderEffect()
                return OrbFluidGrassOpticalStackV1(masks, createdFluid, createdGrass, chained)
            } catch (failure: Throwable) {
                masks.close()
                throw failure
            }
        }
    }
}

/** Candidate stack with the frozen V1.1 grass shader as an all-or-nothing fallback. */
internal enum class OrbShaderRoute {
    FLUID_GRASS_V1_2_CANDIDATE,
    GRASS_V1_1_LEGACY,
    UNAVAILABLE,
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class OrbCoreImageOpticalEffect private constructor(
    val renderEffect: RenderEffect?,
    val route: OrbShaderRoute,
    private val candidate: OrbFluidGrassOpticalStackV1?,
    private val fallback: OrbGrassWarpProgramV11?,
) : AutoCloseable {
    fun update(frame: WaterLightFrame, s2Progress: Float, grassLight: Float, surfaceSizePx: Float) {
        candidate?.update(frame, s2Progress, grassLight, surfaceSizePx)
        if (candidate == null) {
            fallback?.update(
                if (frame.reducedMotion) 0L else frame.elapsedMs,
                s2Progress,
                grassLight,
                surfaceSizePx,
            )
        }
    }

    override fun close() {
        candidate?.close()
        fallback?.close()
    }

    companion object {
        private const val TAG = "OrbCoreOpticalEffect"

        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        fun create(context: Context, preferLegacy: Boolean): OrbCoreImageOpticalEffect {
            if (preferLegacy) {
                Log.i(TAG, "low-performance route: retaining frozen V1.1 grass warp")
                return createLegacy(context)
            }
            val candidate = runCatching { OrbFluidGrassOpticalStackV1.create(context) }
            if (candidate.isSuccess) {
                val stack = candidate.getOrThrow()
                return OrbCoreImageOpticalEffect(
                    stack.renderEffect,
                    OrbShaderRoute.FLUID_GRASS_V1_2_CANDIDATE,
                    stack,
                    null,
                )
            }
            Log.w(TAG, "candidate fluid/V1.2 stack unavailable; using frozen V1.1 grass warp", candidate.exceptionOrNull())
            return createLegacy(context)
        }

        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        private fun createLegacy(context: Context): OrbCoreImageOpticalEffect {
            val fallback = runCatching { OrbGrassWarpProgramV11.create(context) }
                .onFailure { Log.w(TAG, "R01B AGSL unavailable; source core and clipped Canvas fallback active", it) }
                .getOrNull()
            return OrbCoreImageOpticalEffect(
                fallback?.renderEffect,
                if (fallback?.renderEffect != null) OrbShaderRoute.GRASS_V1_1_LEGACY else OrbShaderRoute.UNAVAILABLE,
                null,
                fallback,
            )
        }
    }
}
