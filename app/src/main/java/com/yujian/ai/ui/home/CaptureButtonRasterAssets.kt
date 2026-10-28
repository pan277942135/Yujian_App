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

private const val CAPTURE_ASSET_ROOT = "normal_home_runtime_v1/camera"

/** Loads only the shared camera art; it has no avatar, Hero, or scene dependency. */
internal data class CaptureButtonRasterAssets(
    override val cameraBase: Bitmap,
    override val cameraGoldRim: Bitmap,
    override val cameraBreathGlow: Bitmap,
) : HomeCameraRasterAssets

private fun decode(context: Context, name: String): Bitmap {
    val options = BitmapFactory.Options().apply {
        inScaled = false
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return context.assets.open("$CAPTURE_ASSET_ROOT/$name").use { stream ->
        BitmapFactory.decodeStream(stream, null, options)
            ?: error("Unable to decode shared camera asset: $name")
    }
}

private fun load(context: Context) = CaptureButtonRasterAssets(
    cameraBase = decode(context, "camera_button_base.png"),
    cameraGoldRim = decode(context, "camera_gold_rim_mask.png"),
    cameraBreathGlow = decode(context, "camera_breath_glow.png"),
)

@Composable
internal fun rememberCaptureButtonRasterAssets(): CaptureButtonRasterAssets? {
    val context = LocalContext.current.applicationContext
    var assets by remember(context) { mutableStateOf<CaptureButtonRasterAssets?>(null) }
    LaunchedEffect(context) {
        assets = withContext(Dispatchers.IO) { load(context) }
    }
    return assets
}
