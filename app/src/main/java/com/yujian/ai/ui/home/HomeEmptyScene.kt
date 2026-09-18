package com.yujian.ai.ui.home

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewTreeLifecycleOwner
import com.yujian.ai.ui.components.AssetImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset
import org.json.JSONObject
import kotlin.math.max
import kotlin.random.Random

private const val ASSET_ROOT = "home_empty_v1_2"
private const val BACKGROUND = "$ASSET_ROOT/assets/background/home_empty_bg_no_bobber.webp"
private const val BOBBER = "$ASSET_ROOT/assets/bobber/bobber_real.png"
private const val REFLECTION = "$ASSET_ROOT/assets/bobber/bobber_reflection.png"
private const val WATER_SHADOW = "$ASSET_ROOT/assets/bobber/bobber_water_shadow.png"
private const val RIPPLE_INNER = "$ASSET_ROOT/assets/ripple/ripple_inner.png"
private const val RIPPLE_OUTER = "$ASSET_ROOT/assets/ripple/ripple_outer.png"

private const val REFERENCE_WIDTH_PX = 1080f
private const val REFERENCE_HEIGHT_PX = 1920f

internal data class HomeCropTransform(
    val scale: Float,
    val renderedWidthPx: Float,
    val renderedHeightPx: Float,
    val cropLeftPx: Float,
    val cropTopPx: Float,
)

internal fun calculateHomeCropTransform(
    containerWidthPx: Float,
    containerHeightPx: Float,
    sourceWidthPx: Float,
    sourceHeightPx: Float,
): HomeCropTransform {
    val scale = max(containerWidthPx / sourceWidthPx, containerHeightPx / sourceHeightPx)
    val renderedWidth = sourceWidthPx * scale
    val renderedHeight = sourceHeightPx * scale
    return HomeCropTransform(
        scale = scale,
        renderedWidthPx = renderedWidth,
        renderedHeightPx = renderedHeight,
        cropLeftPx = (containerWidthPx - renderedWidth) / 2f,
        cropTopPx = (containerHeightPx - renderedHeight) / 2f,
    )
}

internal fun mapHomeNormalizedAnchor(
    transform: HomeCropTransform,
    normalizedX: Float,
    normalizedY: Float,
): Offset = Offset(
    x = transform.cropLeftPx + normalizedX * transform.renderedWidthPx,
    y = transform.cropTopPx + normalizedY * transform.renderedHeightPx,
)

private data class HomeEmptyDesignConfig(
    val backgroundWidthPx: Float = REFERENCE_WIDTH_PX,
    val backgroundHeightPx: Float = REFERENCE_HEIGHT_PX,
    val bobberX: Float = 0.708f,
    val bobberY: Float = 0.545f,
    val idleDurationMs: Int = 3600,
    val biteMinMs: Long = 4000L,
    val biteMaxMs: Long = 8000L,
    val biteDurationMs: Int = 760,
    val rippleDurationMs: Int = 1200,
    val cameraDurationMs: Int = 3000,
)

private fun JSONObject.floatAt(vararg keys: String, fallback: Float): Float {
    var current: JSONObject = this
    keys.dropLast(1).forEach { key -> current = current.optJSONObject(key) ?: return fallback }
    return current.optDouble(keys.last(), fallback.toDouble()).toFloat()
}

private fun JSONObject.intAt(vararg keys: String, fallback: Int): Int {
    var current: JSONObject = this
    keys.dropLast(1).forEach { key -> current = current.optJSONObject(key) ?: return fallback }
    return current.optInt(keys.last(), fallback)
}

private fun loadHomeEmptyDesignConfig(context: Context): HomeEmptyDesignConfig {
    fun read(path: String): JSONObject? = runCatching {
        context.assets.open(path).bufferedReader().use { JSONObject(it.readText()) }
    }.getOrNull()

    val focal = read("$ASSET_ROOT/config/focal_points.json")
    val motion = read("$ASSET_ROOT/config/bobber_motion.json")
    val ripple = read("$ASSET_ROOT/config/ripple_motion.json")
    val camera = read("$ASSET_ROOT/config/camera_breath.json")
    val canvas = focal?.optJSONObject("reference_canvas")
    val anchor = focal?.optJSONObject("focal_points")?.optJSONObject("bobber_anchor")
    val randomInterval = motion?.optJSONObject("bite")?.optJSONArray("interval_ms_random")
    val biteSequence = motion?.optJSONObject("bite")?.optJSONArray("sequence")
    val lastBite = biteSequence?.optJSONObject(biteSequence.length() - 1)?.optInt("t_ms", 760) ?: 760
    return HomeEmptyDesignConfig(
        backgroundWidthPx = canvas?.optDouble("w", REFERENCE_WIDTH_PX.toDouble())?.toFloat() ?: REFERENCE_WIDTH_PX,
        backgroundHeightPx = canvas?.optDouble("h", REFERENCE_HEIGHT_PX.toDouble())?.toFloat() ?: REFERENCE_HEIGHT_PX,
        bobberX = anchor?.optDouble("x", 0.708)?.toFloat() ?: 0.708f,
        bobberY = anchor?.optDouble("y", 0.545)?.toFloat() ?: 0.545f,
        idleDurationMs = motion?.optJSONObject("idle")?.optInt("duration_ms", 3600) ?: 3600,
        biteMinMs = randomInterval?.optLong(0, 4000L) ?: 4000L,
        biteMaxMs = randomInterval?.optLong(1, 8000L) ?: 8000L,
        biteDurationMs = lastBite,
        rippleDurationMs = ripple?.optInt("duration_ms", 1200) ?: 1200,
        cameraDurationMs = camera?.optInt("duration_ms", 3000) ?: 3000,
    )
}

@Composable
private fun rememberHomeEmptyDesignConfig(): HomeEmptyDesignConfig {
    val context = LocalContext.current.applicationContext
    var config by remember { mutableStateOf(HomeEmptyDesignConfig()) }
    LaunchedEffect(Unit) {
        config = withContext(Dispatchers.IO) { loadHomeEmptyDesignConfig(context) }
    }
    return config
}

@Composable
internal fun rememberHomeMotionRunning(): Boolean {
    val view = LocalView.current
    val context = LocalContext.current
    var lifecycleStarted by remember { mutableStateOf(true) }
    DisposableEffect(view) {
        val lifecycle = ViewTreeLifecycleOwner.get(view)?.lifecycle
        if (lifecycle == null) {
            onDispose { }
        } else {
            lifecycleStarted = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            val observer = LifecycleEventObserver { _, event ->
                lifecycleStarted = when (event) {
                    Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> true
                    Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_PAUSE -> false
                    else -> lifecycleStarted
                }
            }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer) }
        }
    }
    val reduceMotion = remember(context) {
        runCatching {
            val resolver = context.contentResolver
            Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) <= 0f ||
                Settings.Global.getFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f) <= 0f
        }.getOrDefault(false)
    }
    return lifecycleStarted && !reduceMotion
}

private data class BobberMotion(
    val translationY: Float,
    val rotation: Float,
    val ripplePulse: Int,
)

@Composable
private fun rememberBobberMotion(config: HomeEmptyDesignConfig, running: Boolean): BobberMotion {
    val idleY = remember { Animatable(0f) }
    val idleRotation = remember { Animatable(0f) }
    val biteY = remember { Animatable(0f) }
    val biteRotation = remember { Animatable(0f) }
    var biteId by remember { mutableIntStateOf(0) }

    LaunchedEffect(running, config.idleDurationMs) {
        if (!running) {
            idleY.snapTo(0f)
            idleRotation.snapTo(0f)
            return@LaunchedEffect
        }
        while (isActive) {
            idleY.animateTo(0f, keyframes {
                durationMillis = config.idleDurationMs
                -1f at config.idleDurationMs / 4
                1f at config.idleDurationMs * 3 / 4
            })
            idleRotation.animateTo(0f, keyframes {
                durationMillis = config.idleDurationMs
                -0.4f at config.idleDurationMs / 4
                0.3f at config.idleDurationMs / 2
                -0.2f at config.idleDurationMs * 3 / 4
            })
        }
    }

    LaunchedEffect(running, config.biteMinMs, config.biteMaxMs) {
        if (!running) {
            biteY.snapTo(0f)
            biteRotation.snapTo(0f)
            return@LaunchedEffect
        }
        while (isActive) {
            delay(Random.nextLong(config.biteMinMs, config.biteMaxMs + 1L))
            biteId += 1
        }
    }

    LaunchedEffect(biteId, running, config.biteDurationMs) {
        if (!running || biteId == 0) {
            biteY.snapTo(0f)
            biteRotation.snapTo(0f)
            return@LaunchedEffect
        }
        biteY.animateTo(0f, keyframes {
            durationMillis = config.biteDurationMs
            4f at 140
            1f at 320
        })
        biteRotation.animateTo(0f, keyframes {
            durationMillis = config.biteDurationMs
            0.8f at 140
            -0.4f at 320
        })
    }

    return BobberMotion(
        translationY = idleY.value + biteY.value,
        rotation = idleRotation.value + biteRotation.value,
        ripplePulse = biteId,
    )
}

@Composable
internal fun HomeEmptyScene(modifier: Modifier = Modifier) {
    val config = rememberHomeEmptyDesignConfig()
    val running = rememberHomeMotionRunning()
    val motion = rememberBobberMotion(config, running)
    val density = LocalDensity.current

    BoxWithConstraints(modifier.clipToBounds()) {
        AssetImage(
            BACKGROUND,
            Modifier.fillMaxSize(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
        )

        val containerWidthPx = with(density) { maxWidth.toPx() }
        val containerHeightPx = with(density) { maxHeight.toPx() }
        val transform = calculateHomeCropTransform(
            containerWidthPx = containerWidthPx,
            containerHeightPx = containerHeightPx,
            sourceWidthPx = config.backgroundWidthPx,
            sourceHeightPx = config.backgroundHeightPx,
        )
        val anchorPx = mapHomeNormalizedAnchor(transform, config.bobberX, config.bobberY)
        val anchorX = with(density) { anchorPx.x.toDp() }
        val anchorY = with(density) { anchorPx.y.toDp() }
        val rippleSize = 108.dp
        val shadowWidth = 92.dp
        val shadowHeight = 34.dp
        val reflectionWidth = 28.dp
        val reflectionHeight = 58.dp
        val bobberWidth = 36.dp
        val bobberHeight = bobberWidth * (800f / 220f)
        val bobberTop = anchorY - bobberHeight * 0.74f
        val bobberTranslationPx = with(density) { motion.translationY.dp.toPx() }

        HomeBiteRipple(
            pulseId = motion.ripplePulse,
            durationMs = config.rippleDurationMs,
            x = anchorX,
            y = anchorY,
            size = rippleSize,
        )
        AssetImage(
            WATER_SHADOW,
            Modifier
                .offset(x = anchorX - shadowWidth / 2, y = anchorY - shadowHeight / 2)
                .size(shadowWidth, shadowHeight),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
        )
        AssetImage(
            REFLECTION,
            Modifier
                .offset(x = anchorX - reflectionWidth / 2, y = anchorY - 2.dp)
                .size(reflectionWidth, reflectionHeight),
            contentDescription = null,
            contentScale = ContentScale.Fit,
        )
        AssetImage(
            BOBBER,
            Modifier
                .offset(x = anchorX - bobberWidth / 2, y = bobberTop)
                .size(bobberWidth, bobberHeight)
                .graphicsLayer {
                    translationY = bobberTranslationPx
                    rotationZ = motion.rotation
                },
            contentDescription = "湖面鱼漂",
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun HomeBiteRipple(
    pulseId: Int,
    durationMs: Int,
    x: Dp,
    y: Dp,
    size: Dp,
) {
    var visible by remember { mutableStateOf(false) }
    val innerScale = remember { Animatable(0.88f) }
    val outerScale = remember { Animatable(0.92f) }
    val innerAlpha = remember { Animatable(0f) }
    val outerAlpha = remember { Animatable(0f) }

    LaunchedEffect(pulseId) {
        if (pulseId == 0) return@LaunchedEffect
        visible = true
        innerScale.snapTo(0.88f)
        outerScale.snapTo(0.92f)
        innerAlpha.snapTo(0.28f)
        outerAlpha.snapTo(0.18f)
        coroutineScope {
            launch { innerScale.animateTo(1.05f, tween(durationMs, easing = EaseOutCubic)) }
            launch { outerScale.animateTo(1.12f, tween(durationMs, easing = EaseOutCubic)) }
            launch { innerAlpha.animateTo(0f, tween(durationMs, easing = EaseOutCubic)) }
            launch { outerAlpha.animateTo(0f, tween(durationMs, easing = EaseOutCubic)) }
        }
        visible = false
    }

    if (visible) {
        AssetImage(
            RIPPLE_OUTER,
            Modifier
                .offset(x = x - size / 2, y = y - size / 2)
                .size(size)
                .graphicsLayer { scaleX = outerScale.value; scaleY = outerScale.value; alpha = outerAlpha.value },
            contentDescription = null,
            contentScale = ContentScale.Fit,
        )
        AssetImage(
            RIPPLE_INNER,
            Modifier
                .offset(x = x - size / 2, y = y - size / 2)
                .size(size)
                .graphicsLayer { scaleX = innerScale.value; scaleY = innerScale.value; alpha = innerAlpha.value },
            contentDescription = null,
            contentScale = ContentScale.Fit,
        )
    }
}
