package com.yujian.ai.ui.designsystem.lakeglass

import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import androidx.compose.runtime.withFrameNanos

/** One shared static-first Lake Glass background host for theme surfaces. */
@Composable
internal fun LakeGlassBackgroundHost(
    modifier: Modifier = Modifier.fillMaxSize(),
    effectParameters: LakeGlassEffectParameters = LakeGlassEffectParameters.STATIC_DEFAULT,
    allowOptionalAgsl: Boolean = effectParameters.anyEffectEnabled,
    onFirstFrame: ((decodeElapsedMs: Long, firstDisplayElapsedMs: Long, bitmapBytes: Int) -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val context = LocalContext.current.applicationContext
    val density = LocalDensity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val firstFrameCallback by rememberUpdatedState(onFirstFrame)
    val includeMask = Build.VERSION.SDK_INT >= 33 && allowOptionalAgsl
    var frameTick by remember { mutableIntStateOf(0) }
    val ambientController = remember { AmbientController() }
    val startedAtNanos = remember { SystemClock.elapsedRealtimeNanos() }

    BoxWithConstraints(
        modifier = modifier
            .background(Color(0xFF79A9A3)),
    ) {
        val viewportSize = with(density) {
            IntSize(
                width = maxWidth.roundToPx().coerceAtLeast(1),
                height = maxHeight.roundToPx().coerceAtLeast(1),
            )
        }
        val viewport = remember(viewportSize) {
            LakeGlassViewportTransform.centerCrop(
                width = viewportSize.width.toFloat(),
                height = viewportSize.height.toFloat(),
            )
        }
        var assets by remember(viewportSize) {
            mutableStateOf(
                LakeGlassAssetRepository.peek(
                    viewportWidthPx = viewportSize.width,
                    viewportHeightPx = viewportSize.height,
                    // Keep a previously decoded base visible while an optional mask loads.
                    includeOpticalMask = false,
                ),
            )
        }
        var shaderProgram by remember(viewportSize, includeMask) {
            mutableStateOf<LakeGlassOptionalShaderProgram?>(null)
        }

        LaunchedEffect(context, viewportSize, includeMask) {
            val loadedAssets = runCatching {
                LakeGlassAssetRepository.load(
                    context = context,
                    viewportWidthPx = viewportSize.width,
                    viewportHeightPx = viewportSize.height,
                    includeOpticalMask = includeMask,
                )
            }.getOrNull()
            assets = loadedAssets
            shaderProgram = null
            if (Build.VERSION.SDK_INT >= 33 && includeMask && loadedAssets?.opticalMaskBitmap != null) {
                shaderProgram = runCatching {
                    withContext(Dispatchers.Default) {
                        createApi33LakeGlassProgram(context, loadedAssets)
                    }
                }.getOrNull()
            }
        }

        val renderer = remember(shaderProgram) { LakeGlassRenderer(shaderProgram) }
        val apiLevel = Build.VERSION.SDK_INT
        val selectedRenderEffect = renderer.renderEffect(apiLevel, allowOptionalAgsl)

        DisposableEffect(lifecycleOwner.lifecycle, effectParameters.motionEnabled, selectedRenderEffect) {
            if (!effectParameters.motionEnabled || selectedRenderEffect == null) return@DisposableEffect onDispose { }
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> ambientController.pause(System.nanoTime())
                    Lifecycle.Event.ON_START -> ambientController.resume(System.nanoTime())
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                ambientController.resume(System.nanoTime())
            }
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        LaunchedEffect(effectParameters.motionEnabled, selectedRenderEffect, lifecycleOwner.lifecycle) {
            if (!effectParameters.motionEnabled || selectedRenderEffect == null) return@LaunchedEffect
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    withFrameNanos {
                        val elapsed = ambientController.elapsedSeconds(
                            frameTimeNanos = System.nanoTime(),
                            motionEnabled = effectParameters.motionEnabled,
                        )
                        renderer.update(viewport, effectParameters, elapsed)
                        frameTick += 1
                    }
                }
            }
        }

        val loaded = assets
        LaunchedEffect(loaded?.cacheKey) {
            if (loaded == null) return@LaunchedEffect
            androidx.compose.runtime.withFrameNanos { }
            firstFrameCallback?.invoke(
                loaded.decodeElapsedMs,
                (SystemClock.elapsedRealtimeNanos() - startedAtNanos) / 1_000_000L,
                loaded.baseBitmap.allocationByteCount,
            )
        }
        if (loaded != null) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .graphicsLayer { renderEffect = selectedRenderEffect }
                    .testTag("lake-glass-background"),
            ) {
                // Captured state invalidates only this background draw when motion is enabled.
                frameTick
                renderer.update(
                    viewport = viewport,
                    parameters = effectParameters,
                    elapsedSeconds = if (effectParameters.motionEnabled) {
                        ambientController.elapsedSeconds(System.nanoTime(), true)
                    } else 0f,
                )
                val bitmap = loaded.baseBitmap
                val dest = RectF(
                    viewport.offsetX,
                    viewport.offsetY,
                    viewport.offsetX + LakeGlassViewportTransform.MASTER_WIDTH * viewport.scale,
                    viewport.offsetY + LakeGlassViewportTransform.MASTER_HEIGHT * viewport.scale,
                )
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawBitmap(
                        bitmap,
                        null,
                        dest,
                        StaticBitmapPaint,
                    )
                }
            }
        }
        content()
    }
}

private val StaticBitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)

@androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun createApi33LakeGlassProgram(
    context: android.content.Context,
    assets: LakeGlassSceneAssets,
): LakeGlassOptionalShaderProgram = LakeGlassAgslProgram.create(context, assets)
