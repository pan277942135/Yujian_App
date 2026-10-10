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

private const val NORMAL_HOME_RUNTIME_ROOT = "normal_home_runtime_v1"

internal data class NormalHomeRuntimeAssets(
    val sceneBase: Bitmap,
    val guestAvatar: Bitmap,
    val fishCardGradient: Bitmap,
    val fishCardOutline: Bitmap,
    val fishCardShadow: Bitmap,
    override val cameraBase: Bitmap,
    override val cameraGoldRim: Bitmap,
    override val cameraBreathGlow: Bitmap,
) : HomeCameraRasterAssets

private fun decodeNormalHomeAsset(context: Context, path: String): Bitmap {
    val options = BitmapFactory.Options().apply {
        inScaled = false
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return context.assets.open("$NORMAL_HOME_RUNTIME_ROOT/$path").use { stream ->
        BitmapFactory.decodeStream(stream, null, options)
            ?: error("Unable to decode Normal Home runtime asset: $path")
    }
}

private fun loadNormalHomeRuntimeAssets(context: Context): NormalHomeRuntimeAssets =
    NormalHomeRuntimeAssets(
        sceneBase = decodeNormalHomeAsset(context, "static/scene_base.png"),
        guestAvatar = decodeNormalHomeAsset(context, "avatar/guest_avatar.png"),
        fishCardGradient = decodeNormalHomeAsset(context, "fish_card/fish_card_gradient.png"),
        fishCardOutline = decodeNormalHomeAsset(context, "fish_card/fish_card_outline.png"),
        fishCardShadow = decodeNormalHomeAsset(context, "fish_card/fish_card_shadow.png"),
        cameraBase = decodeNormalHomeAsset(context, "camera/camera_button_base.png"),
        cameraGoldRim = decodeNormalHomeAsset(context, "camera/camera_gold_rim_mask.png"),
        cameraBreathGlow = decodeNormalHomeAsset(context, "camera/camera_breath_glow.png"),
    )

@Composable
internal fun rememberNormalHomeRuntimeAssets(
    enabled: Boolean = true,
): NormalHomeRuntimeAssets? {
    val context = LocalContext.current.applicationContext
    var assets by remember { mutableStateOf<NormalHomeRuntimeAssets?>(null) }
    LaunchedEffect(context, enabled) {
        if (!enabled) {
            assets = null
            return@LaunchedEffect
        }
        assets = withContext(Dispatchers.IO) { loadNormalHomeRuntimeAssets(context) }
    }
    return assets
}
