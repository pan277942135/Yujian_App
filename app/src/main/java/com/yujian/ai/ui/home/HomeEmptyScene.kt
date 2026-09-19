package com.yujian.ai.ui.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.max
import kotlin.random.Random
import androidx.compose.ui.geometry.Offset

private const val ASSET_ROOT = "home_empty_v1_3"
private const val BACKGROUND = ASSET_ROOT + "/assets/background/home_empty_bg_no_bobber.webp"
private const val BOBBER = ASSET_ROOT + "/assets/bobber/bobber_real_v13.png"
private const val REFLECTION = ASSET_ROOT + "/assets/bobber/bobber_reflection_v13.png"
private const val WATER_SHADOW = ASSET_ROOT + "/assets/bobber/bobber_water_shadow_v13.png"
private const val RIPPLE_INNER = ASSET_ROOT + "/assets/ripple/ripple_inner_v13.png"
private const val RIPPLE_OUTER = ASSET_ROOT + "/assets/ripple/ripple_outer_v13.png"

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
    val bobberX: Float = 0.733333f,
    val bobberY: Float = 0.545833f,
    val bodyWidthRefPx: Float = 16f,
    val bodyHeightRefPx: Float = 56f,
    val bodyContactX: Float = 0.5f,
    val bodyContactY: Float = 0.94f,
    val reflectionWidthRefPx: Float = 14f,
    val reflectionHeightRefPx: Float = 44f,
    val reflectionTopOffsetRefPx: Float = 2f,
    val reflectionBaseAlpha: Float = 0.26f,
    val shadowWidthRefPx: Float = 52f,
    val shadowHeightRefPx: Float = 14f,
    val shadowOffsetXRefPx: Float = 0f,
    val shadowOffsetYRefPx: Float = 1f,
    val shadowBaseAlpha: Float = 0.55f,
    val idleDurationMs: Int = 4200,
    val biteMinMs: Long = 4500L,
    val biteMaxMs: Long = 8500L,
    val biteDurationMs: Int = 720,
    val sinkBodyFraction: Float = 0.075f,
    val rotationPeak: Float = 0.55f,
    val rippleDurationMs: Int = 1100,
    val rippleInnerWidthRefPx: Float = 72f,
    val rippleInnerHeightRefPx: Float = 22f,
    val rippleInnerStartScale: Float = 0.9f,
    val rippleInnerEndScale: Float = 1.1f,
    val rippleInnerStartAlpha: Float = 0.22f,
    val rippleOuterWidthRefPx: Float = 116f,
    val rippleOuterHeightRefPx: Float = 34f,
    val rippleOuterStartScale: Float = 0.92f,
    val rippleOuterEndScale: Float = 1.14f,
    val rippleOuterStartAlpha: Float = 0.14f,
)

private fun JSONObject.readObject(vararg keys: String): JSONObject? {
    var current: JSONObject = this
    keys.dropLast(1).forEach { key ->
        current = current.optJSONObject(key) ?: return null
    }
    return current.optJSONObject(keys.last())
}

private fun readJson(context: Context, path: String): JSONObject? = runCatching {
    context.assets.open(path).bufferedReader().use { JSONObject(it.readText()) }
}.getOrNull()

private fun loadHomeEmptyDesignConfig(context: Context): HomeEmptyDesignConfig {
    val geometry = readJson(context, ASSET_ROOT + "/config/bobber_geometry.json")
    val ripple = readJson(context, ASSET_ROOT + "/config/ripple_geometry.json")
    val motion = readJson(context, ASSET_ROOT + "/config/bobber_motion_v13.json")

    val canvas = geometry?.optJSONObject("reference_canvas_px")
    val anchor = geometry?.optJSONObject("water_contact_anchor_normalized")
    val body = geometry?.optJSONObject("body")
    val bodySize = body?.optJSONObject("display_ref_px")
    val bodyContact = body?.optJSONObject("asset_water_contact")
    val reflection = geometry?.optJSONObject("reflection")
    val reflectionSize = reflection?.optJSONObject("display_ref_px")
    val shadow = geometry?.optJSONObject("water_shadow")
    val shadowSize = shadow?.optJSONObject("display_ref_px")
    val shadowOffset = shadow?.optJSONObject("center_offset_ref_px")
    val idle = motion?.optJSONObject("idle")
    val bite = motion?.optJSONObject("bite")
    val biteInterval = bite?.optJSONArray("interval_ms_random")
    val biteSequence = bite?.optJSONArray("sequence_ms")
    val inner = ripple?.optJSONObject("inner")
    val innerSize = inner?.optJSONObject("display_ref_px")
    val outer = ripple?.optJSONObject("outer")
    val outerSize = outer?.optJSONObject("display_ref_px")

    return HomeEmptyDesignConfig(
        backgroundWidthPx = canvas?.optDouble("w", REFERENCE_WIDTH_PX.toDouble())?.toFloat()
            ?: REFERENCE_WIDTH_PX,
        backgroundHeightPx = canvas?.optDouble("h", REFERENCE_HEIGHT_PX.toDouble())?.toFloat()
            ?: REFERENCE_HEIGHT_PX,
        bobberX = anchor?.optDouble("x", 0.733333)?.toFloat() ?: 0.733333f,
        bobberY = anchor?.optDouble("y", 0.545833)?.toFloat() ?: 0.545833f,
        bodyWidthRefPx = bodySize?.optDouble("w", 16.0)?.toFloat() ?: 16f,
        bodyHeightRefPx = bodySize?.optDouble("h", 56.0)?.toFloat() ?: 56f,
        bodyContactX = bodyContact?.optDouble("x", 0.5)?.toFloat() ?: 0.5f,
        bodyContactY = bodyContact?.optDouble("y", 0.94)?.toFloat() ?: 0.94f,
        reflectionWidthRefPx = reflectionSize?.optDouble("w", 14.0)?.toFloat() ?: 14f,
        reflectionHeightRefPx = reflectionSize?.optDouble("h", 44.0)?.toFloat() ?: 44f,
        reflectionTopOffsetRefPx = reflection?.optDouble("top_offset_ref_px", 2.0)?.toFloat() ?: 2f,
        reflectionBaseAlpha = reflection?.optDouble("base_alpha", 0.26)?.toFloat() ?: 0.26f,
        shadowWidthRefPx = shadowSize?.optDouble("w", 52.0)?.toFloat() ?: 52f,
        shadowHeightRefPx = shadowSize?.optDouble("h", 14.0)?.toFloat() ?: 14f,
        shadowOffsetXRefPx = shadowOffset?.optDouble("x", 0.0)?.toFloat() ?: 0f,
        shadowOffsetYRefPx = shadowOffset?.optDouble("y", 1.0)?.toFloat() ?: 1f,
        shadowBaseAlpha = shadow?.optDouble("base_alpha", 0.55)?.toFloat() ?: 0.55f,
        idleDurationMs = idle?.optInt("duration_ms", 4200) ?: 4200,
        biteMinMs = biteInterval?.optLong(0, 4500L) ?: 4500L,
        biteMaxMs = biteInterval?.optLong(1, 8500L) ?: 8500L,
        biteDurationMs = biteSequence?.optInt(biteSequence.length() - 1, 720) ?: 720,
        sinkBodyFraction = bite?.optDouble("sink_body_fraction", 0.075)?.toFloat() ?: 0.075f,
        rotationPeak = bite?.optDouble("rotation_peak_deg", 0.55)?.toFloat() ?: 0.55f,
        rippleDurationMs = ripple?.optInt("duration_ms", 1100) ?: 1100,
        rippleInnerWidthRefPx = innerSize?.optDouble("w", 72.0)?.toFloat() ?: 72f,
        rippleInnerHeightRefPx = innerSize?.optDouble("h", 22.0)?.toFloat() ?: 22f,
        rippleInnerStartScale = inner?.optDouble("start_scale", 0.9)?.toFloat() ?: 0.9f,
        rippleInnerEndScale = inner?.optDouble("end_scale", 1.1)?.toFloat() ?: 1.1f,
        rippleInnerStartAlpha = inner?.optDouble("start_alpha", 0.22)?.toFloat() ?: 0.22f,
        rippleOuterWidthRefPx = outerSize?.optDouble("w", 116.0)?.toFloat() ?: 116f,
        rippleOuterHeightRefPx = outerSize?.optDouble("h", 34.0)?.toFloat() ?: 34f,
        rippleOuterStartScale = outer?.optDouble("start_scale", 0.92)?.toFloat() ?: 0.92f,
        rippleOuterEndScale = outer?.optDouble("end_scale", 1.14)?.toFloat() ?: 1.14f,
        rippleOuterStartAlpha = outer?.optDouble("start_alpha", 0.14)?.toFloat() ?: 0.14f,
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

internal data class HomeMotionState(
    val running: Boolean,
    val reduceMotion: Boolean,
)

@Composable
internal fun rememberHomeMotionState(): HomeMotionState {
    val view = LocalView.current
    val context = LocalContext.current
    var lifecycleStarted by remember { mutableStateOf(true) }

    DisposableEffect(view) {
        val lifecycle = view.findViewTreeLifecycleOwner()?.lifecycle
        if (lifecycle == null) {
            onDispose { }
        } else {
            lifecycleStarted = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> lifecycleStarted = true
                    Lifecycle.Event.ON_STOP -> lifecycleStarted = false
                    else -> Unit
                }
            }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer) }
        }
    }

    val reduceMotion = remember(context) {
        runCatching {
            val resolver = context.contentResolver
            Settings.Global.getFloat(
                resolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) <= 0f || Settings.Global.getFloat(
                resolver,
                Settings.Global.TRANSITION_ANIMATION_SCALE,
                1f,
            ) <= 0f
        }.getOrDefault(false)
    }
    return HomeMotionState(
        running = lifecycleStarted && !reduceMotion,
        reduceMotion = reduceMotion,
    )
}

@Composable
internal fun rememberHomeMotionRunning(): Boolean = rememberHomeMotionState().running

private data class BobberMotion(
    val idleFraction: Float,
    val idleRotation: Float,
    val biteProgress: Float,
    val biteRotation: Float,
    val reflectionScaleY: Float,
    val reflectionAlphaMultiplier: Float,
    val shadowScaleX: Float,
    val shadowAlphaMultiplier: Float,
    val ripplePulse: Int,
)

@Composable
private fun rememberBobberMotion(config: HomeEmptyDesignConfig, running: Boolean): BobberMotion {
    val idleFraction = remember { Animatable(0f) }
    val idleRotation = remember { Animatable(0f) }
    val biteProgress = remember { Animatable(0f) }
    val biteRotation = remember { Animatable(0f) }
    val reflectionScaleY = remember { Animatable(1f) }
    val reflectionAlpha = remember { Animatable(1f) }
    val shadowScaleX = remember { Animatable(1f) }
    val shadowAlpha = remember { Animatable(1f) }
    var biteId by remember { mutableIntStateOf(0) }

    LaunchedEffect(running, config.idleDurationMs) {
        if (!running) {
            idleFraction.snapTo(0f)
            idleRotation.snapTo(0f)
            return@LaunchedEffect
        }
        while (isActive) {
            coroutineScope {
                launch {
                    idleFraction.animateTo(0f, keyframes {
                        durationMillis = config.idleDurationMs
                        -0.012f at config.idleDurationMs / 4
                        0.01f at config.idleDurationMs * 3 / 4
                    })
                }
                launch {
                    idleRotation.animateTo(0f, keyframes {
                        durationMillis = config.idleDurationMs
                        -0.22f at config.idleDurationMs / 4
                        0.18f at config.idleDurationMs / 2
                        0f at config.idleDurationMs * 3 / 4
                    })
                }
            }
        }
    }

    LaunchedEffect(running, config.biteMinMs, config.biteMaxMs) {
        if (!running) return@LaunchedEffect
        while (isActive) {
            delay(Random.nextLong(config.biteMinMs, config.biteMaxMs + 1L))
            biteId += 1
        }
    }

    LaunchedEffect(biteId, running, config.biteDurationMs) {
        if (!running || biteId == 0) {
            biteProgress.snapTo(0f)
            biteRotation.snapTo(0f)
            reflectionScaleY.snapTo(1f)
            reflectionAlpha.snapTo(1f)
            shadowScaleX.snapTo(1f)
            shadowAlpha.snapTo(1f)
            return@LaunchedEffect
        }
        coroutineScope {
            launch {
                biteProgress.animateTo(0f, keyframes {
                    durationMillis = config.biteDurationMs
                    0f at 0
                    1f at 130
                    0.25f at 300
                    0f at config.biteDurationMs
                })
            }
            launch {
                biteRotation.animateTo(0f, keyframes {
                    durationMillis = config.biteDurationMs
                    0f at 0
                    config.rotationPeak at 130
                    -0.18f at 300
                    0f at config.biteDurationMs
                })
            }
            launch {
                reflectionScaleY.animateTo(1f, keyframes {
                    durationMillis = config.biteDurationMs
                    1f at 0
                    0.91f at 130
                    0.96f at 300
                    1f at config.biteDurationMs
                })
            }
            launch {
                reflectionAlpha.animateTo(1f, keyframes {
                    durationMillis = config.biteDurationMs
                    1f at 0
                    0.72f at 130
                    0.86f at 300
                    1f at config.biteDurationMs
                })
            }
            launch {
                shadowScaleX.animateTo(1f, keyframes {
                    durationMillis = config.biteDurationMs
                    1f at 0
                    1.08f at 130
                    1.03f at 300
                    1f at config.biteDurationMs
                })
            }
            launch {
                shadowAlpha.animateTo(1f, keyframes {
                    durationMillis = config.biteDurationMs
                    1f at 0
                    0.88f at 130
                    0.94f at 300
                    1f at config.biteDurationMs
                })
            }
        }
    }

    return BobberMotion(
        idleFraction = idleFraction.value,
        idleRotation = idleRotation.value,
        biteProgress = biteProgress.value,
        biteRotation = biteRotation.value,
        reflectionScaleY = reflectionScaleY.value,
        reflectionAlphaMultiplier = reflectionAlpha.value,
        shadowScaleX = shadowScaleX.value,
        shadowAlphaMultiplier = shadowAlpha.value,
        ripplePulse = biteId,
    )
}

@Composable
private fun rememberAssetBitmap(path: String): Bitmap? {
    val context = LocalContext.current.applicationContext
    var bitmap by remember(path) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(path) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(path).use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
        }
    }
    return bitmap
}

@Composable
internal fun HomeEmptyScene(modifier: Modifier = Modifier) {
    val config = rememberHomeEmptyDesignConfig()
    val motionState = rememberHomeMotionState()
    val background = rememberAssetBitmap(BACKGROUND)
    val bobber = rememberAssetBitmap(BOBBER)
    val reflection = rememberAssetBitmap(REFLECTION)
    val shadow = rememberAssetBitmap(WATER_SHADOW)
    val rippleInner = rememberAssetBitmap(RIPPLE_INNER)
    val rippleOuter = rememberAssetBitmap(RIPPLE_OUTER)
    val density = LocalDensity.current
    val bobberMotion = rememberBobberMotion(config, motionState.running)

    BoxWithConstraints(modifier.clipToBounds()) {
        background?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }

        val containerWidthPx = with(density) { maxWidth.toPx() }
        val containerHeightPx = with(density) { maxHeight.toPx() }
        val transform = calculateHomeCropTransform(
            containerWidthPx = containerWidthPx,
            containerHeightPx = containerHeightPx,
            sourceWidthPx = (background?.width ?: config.backgroundWidthPx.toInt()).toFloat(),
            sourceHeightPx = (background?.height ?: config.backgroundHeightPx.toInt()).toFloat(),
        )
        val anchorPx = mapHomeNormalizedAnchor(transform, config.bobberX, config.bobberY)
        val anchorX = with(density) { anchorPx.x.toDp() }
        val anchorY = with(density) { anchorPx.y.toDp() }
        val bodyWidthPx = config.bodyWidthRefPx * transform.scale
        val bodyHeightPx = config.bodyHeightRefPx * transform.scale
        val bodyWidth = with(density) { bodyWidthPx.toDp() }
        val bodyHeight = with(density) { bodyHeightPx.toDp() }
        val reflectionWidth = with(density) { (config.reflectionWidthRefPx * transform.scale).toDp() }
        val reflectionHeight = with(density) { (config.reflectionHeightRefPx * transform.scale).toDp() }
        val shadowWidth = with(density) { (config.shadowWidthRefPx * transform.scale).toDp() }
        val shadowHeight = with(density) { (config.shadowHeightRefPx * transform.scale).toDp() }
        val innerWidth = with(density) { (config.rippleInnerWidthRefPx * transform.scale).toDp() }
        val innerHeight = with(density) { (config.rippleInnerHeightRefPx * transform.scale).toDp() }
        val outerWidth = with(density) { (config.rippleOuterWidthRefPx * transform.scale).toDp() }
        val outerHeight = with(density) { (config.rippleOuterHeightRefPx * transform.scale).toDp() }
        val bodyTop = anchorY - bodyHeight * config.bodyContactY
        val sinkPx = (bodyHeightPx * config.sinkBodyFraction).coerceIn(
            with(density) { 2.dp.toPx() },
            with(density) { 4.dp.toPx() },
        )

        HomeBiteRipple(
            pulseId = bobberMotion.ripplePulse,
            running = motionState.running,
            durationMs = config.rippleDurationMs,
            x = anchorX,
            y = anchorY,
            innerBitmap = rippleInner,
            outerBitmap = rippleOuter,
            innerWidth = innerWidth,
            innerHeight = innerHeight,
            outerWidth = outerWidth,
            outerHeight = outerHeight,
            innerStartScale = config.rippleInnerStartScale,
            innerEndScale = config.rippleInnerEndScale,
            innerStartAlpha = config.rippleInnerStartAlpha,
            outerStartScale = config.rippleOuterStartScale,
            outerEndScale = config.rippleOuterEndScale,
            outerStartAlpha = config.rippleOuterStartAlpha,
        )
        shadow?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .offset(
                        x = anchorX + with(density) {
                            (config.shadowOffsetXRefPx * transform.scale).toDp()
                        } - shadowWidth / 2,
                        y = anchorY + with(density) {
                            (config.shadowOffsetYRefPx * transform.scale).toDp()
                        } - shadowHeight / 2,
                    )
                    .size(shadowWidth, shadowHeight)
                    .graphicsLayer {
                        scaleX = bobberMotion.shadowScaleX
                        alpha = config.shadowBaseAlpha * bobberMotion.shadowAlphaMultiplier
                    },
                contentScale = ContentScale.FillBounds,
            )
        }
        reflection?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .offset(
                        x = anchorX - reflectionWidth / 2,
                        y = anchorY + with(density) {
                            (config.reflectionTopOffsetRefPx * transform.scale).toDp()
                        },
                    )
                    .size(reflectionWidth, reflectionHeight)
                    .graphicsLayer {
                        scaleY = bobberMotion.reflectionScaleY
                        alpha = config.reflectionBaseAlpha * bobberMotion.reflectionAlphaMultiplier
                    },
                contentScale = ContentScale.FillBounds,
            )
        }
        bobber?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .offset(
                        x = anchorX - bodyWidth * config.bodyContactX,
                        y = bodyTop,
                    )
                    .size(bodyWidth, bodyHeight)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(config.bodyContactX, config.bodyContactY)
                        translationY = bobberMotion.idleFraction * bodyHeightPx +
                            bobberMotion.biteProgress * sinkPx
                        rotationZ = bobberMotion.idleRotation + bobberMotion.biteRotation
                    },
                contentScale = ContentScale.FillBounds,
            )
        }
    }
}

@Composable
private fun HomeBiteRipple(
    pulseId: Int,
    running: Boolean,
    durationMs: Int,
    x: Dp,
    y: Dp,
    innerBitmap: Bitmap?,
    outerBitmap: Bitmap?,
    innerWidth: Dp,
    innerHeight: Dp,
    outerWidth: Dp,
    outerHeight: Dp,
    innerStartScale: Float,
    innerEndScale: Float,
    innerStartAlpha: Float,
    outerStartScale: Float,
    outerEndScale: Float,
    outerStartAlpha: Float,
) {
    var visible by remember { mutableStateOf(false) }
    val innerScale = remember { Animatable(innerStartScale) }
    val outerScale = remember { Animatable(outerStartScale) }
    val innerAlpha = remember { Animatable(0f) }
    val outerAlpha = remember { Animatable(0f) }

    LaunchedEffect(pulseId, running) {
        if (!running || pulseId == 0 || (innerBitmap == null && outerBitmap == null)) {
            visible = false
            return@LaunchedEffect
        }
        visible = true
        innerScale.snapTo(innerStartScale)
        outerScale.snapTo(outerStartScale)
        innerAlpha.snapTo(innerStartAlpha)
        outerAlpha.snapTo(outerStartAlpha)
        coroutineScope {
            launch { innerScale.animateTo(innerEndScale, tween(durationMs, easing = EaseOutCubic)) }
            launch { outerScale.animateTo(outerEndScale, tween(durationMs, easing = EaseOutCubic)) }
            launch { innerAlpha.animateTo(0f, tween(durationMs, easing = EaseOutCubic)) }
            launch { outerAlpha.animateTo(0f, tween(durationMs, easing = EaseOutCubic)) }
        }
        visible = false
    }

    if (!visible) return
    outerBitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier
                .offset(x = x - outerWidth / 2, y = y - outerHeight / 2)
                .size(outerWidth, outerHeight)
                .graphicsLayer {
                    scaleX = outerScale.value
                    scaleY = outerScale.value
                    alpha = outerAlpha.value
                },
            contentScale = ContentScale.FillBounds,
        )
    }
    innerBitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier
                .offset(x = x - innerWidth / 2, y = y - innerHeight / 2)
                .size(innerWidth, innerHeight)
                .graphicsLayer {
                    scaleX = innerScale.value
                    scaleY = innerScale.value
                    alpha = innerAlpha.value
                },
            contentScale = ContentScale.FillBounds,
        )
    }
}
