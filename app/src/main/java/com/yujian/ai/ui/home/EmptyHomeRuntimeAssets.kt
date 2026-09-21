package com.yujian.ai.ui.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ROOT = "empty_home_runtime_v1"

internal data class EmptyHomeRuntimeAssets(
    val staticScene: Bitmap,
    val cloud: Bitmap,
    val sunGlow: Bitmap,
    val bobber: Bitmap,
    val ripple: Bitmap,
    val sunBeamMask: Bitmap,
    val sunParticleMask: Bitmap,
    val cameraBase: Bitmap,
    val cameraGoldRim: Bitmap,
    val cameraBreathGlow: Bitmap,
)

private fun decodeAsset(context: Context, path: String): Bitmap {
    val options = BitmapFactory.Options().apply {
        inScaled = false
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return context.assets.open("$ROOT/$path").use { stream ->
        BitmapFactory.decodeStream(stream, null, options)
            ?: error("Unable to decode Empty Home runtime asset: $path")
    }
}

private fun loadRuntimeAssets(context: Context): EmptyHomeRuntimeAssets =
    EmptyHomeRuntimeAssets(
        staticScene = decodeAsset(context, "static_scene_cache.png"),
        cloud = decodeAsset(context, "dynamic/cloud_layer.png"),
        sunGlow = decodeAsset(context, "dynamic/sun_glow.png"),
        bobber = decodeAsset(context, "dynamic/bobber.png"),
        ripple = decodeAsset(context, "dynamic/ripple_mask.png"),
        sunBeamMask = decodeAsset(context, "dynamic/sun_beam_mask.png"),
        sunParticleMask = decodeAsset(context, "dynamic/sun_particle_mask.png"),
        cameraBase = decodeAsset(context, "camera/camera_button_base.png"),
        cameraGoldRim = decodeAsset(context, "camera/camera_gold_rim_mask.png"),
        cameraBreathGlow = decodeAsset(context, "camera/camera_breath_glow.png"),
    )

@Composable
internal fun rememberEmptyHomeRuntimeAssets(
    enabled: Boolean = true,
): EmptyHomeRuntimeAssets? {
    val context = LocalContext.current.applicationContext
    var assets by remember { mutableStateOf<EmptyHomeRuntimeAssets?>(null) }
    LaunchedEffect(context, enabled) {
        if (!enabled) {
            assets = null
            return@LaunchedEffect
        }
        assets = withContext(Dispatchers.IO) { loadRuntimeAssets(context) }
    }
    return assets
}
