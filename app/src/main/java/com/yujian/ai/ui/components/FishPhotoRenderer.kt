package com.yujian.ai.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.yujian.ai.R
import com.yujian.ai.ui.designsystem.color.YuJianColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.yujian.ai.ui.imagery.FishPhotoBoundingBox
import com.yujian.ai.ui.imagery.FishPhotoCropPlan
import com.yujian.ai.ui.imagery.FishPhotoCropPlanner
import com.yujian.ai.ui.imagery.FishPhotoDisplayMode
import kotlin.math.max
import kotlin.math.roundToInt

internal data class RenderedFishPhoto(
    val bitmap: Bitmap,
    val plan: FishPhotoCropPlan,
    val cacheKey: String,
)

/** Bounded cache for display-only variants. Entries include source and crop algorithm versions. */
internal object FishPhotoDerivativeCache {
    private const val MAX_BYTES = 32 * 1024 * 1024
    private val cache = object : LruCache<String, Bitmap>(MAX_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    @Synchronized fun get(key: String): Bitmap? = cache.get(key)
    @Synchronized fun put(key: String, bitmap: Bitmap) { cache.put(key, bitmap) }
}

internal object FishPhotoRenderer {
    const val BACKDROP_PARAMETERS_VERSION = "source-blur-cover-fit-underlay-v3"
    private const val MAX_OUTPUT_EDGE_PX = 2048
    private const val BLUR_RADIUS_SOURCE_PX = 30f

    fun cacheKey(
        sourceVersion: String,
        targetWidthPx: Int,
        targetHeightPx: Int,
        boundingBox: FishPhotoBoundingBox?,
        primaryAmbiguous: Boolean,
        allowSubjectCrop: Boolean = true,
        displayConfidenceThreshold: Float = FishPhotoCropPlanner.DISPLAY_CROP_CONFIDENCE_THRESHOLD,
        sourceWidthPx: Int? = null,
        sourceHeightPx: Int? = null,
    ): String = buildString {
        append(sourceVersion).append('|')
        append("source=").append(sourceWidthPx ?: 0).append('x').append(sourceHeightPx ?: 0).append('|')
        append(targetWidthPx).append('x').append(targetHeightPx).append('|')
        append("aspect=").append(targetWidthPx.toFloat() / targetHeightPx.coerceAtLeast(1)).append('|')
        append(FishPhotoCropPlanner.ALGORITHM_VERSION).append('|')
        append(FishPhotoCropPlanner.BBOX_COORDINATE_VERSION).append('|')
        append(BACKDROP_PARAMETERS_VERSION).append('|')
        append("ambiguous=").append(primaryAmbiguous).append('|')
        append("crop=").append(allowSubjectCrop).append('|')
        append("confidenceThreshold=").append(displayConfidenceThreshold).append('|')
        append(boundingBox?.version ?: "no-bbox").append('|')
        append(boundingBox?.coordinateSpace?.name ?: "none").append('|')
        append(boundingBox?.exifOrientation?.value ?: 1).append('|')
        append(boundingBox?.confidence ?: -1f).append('|')
        boundingBox?.box?.let { append(it.x1).append(',').append(it.y1).append(',').append(it.x2).append(',').append(it.y2) }
    }

    fun render(
        source: Bitmap,
        targetWidthPx: Int,
        targetHeightPx: Int,
        sourceVersion: String,
        boundingBox: FishPhotoBoundingBox? = null,
        primaryAmbiguous: Boolean = false,
        allowSubjectCrop: Boolean = true,
        displayConfidenceThreshold: Float = FishPhotoCropPlanner.DISPLAY_CROP_CONFIDENCE_THRESHOLD,
    ): RenderedFishPhoto {
        require(source.width > 0 && source.height > 0)
        val width = targetWidthPx.coerceAtLeast(1)
        val height = targetHeightPx.coerceAtLeast(1)
        val key = cacheKey(
            sourceVersion,
            width,
            height,
            boundingBox,
            primaryAmbiguous,
            allowSubjectCrop,
            displayConfidenceThreshold,
            source.width,
            source.height,
        )
        val (outputWidth, outputHeight) = boundedOutputSize(width, height)
        val plan = FishPhotoCropPlanner.plan(
            sourceWidthPx = source.width,
            sourceHeightPx = source.height,
            targetAspectRatio = outputWidth.toFloat() / outputHeight,
            boundingBox = boundingBox,
            primaryAmbiguous = primaryAmbiguous,
            allowSubjectCrop = allowSubjectCrop,
            displayConfidenceThreshold = displayConfidenceThreshold,
        )
        Log.d(
            "FishPhotoCrop",
            "mode=${plan.mode}; reason=${plan.fallbackReason}; margin=${plan.marginLevelPercent}; " +
                "attempts=${plan.attemptedMarginLevels}; source=${source.width}x${source.height}; target=${width}x${height}",
        )
        val cached = FishPhotoDerivativeCache.get(key)
        if (cached != null) return RenderedFishPhoto(cached, plan, key)

        val output = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            isDither = true
        }
        if (plan.mode == FishPhotoDisplayMode.SUBJECT_CROP && plan.cropRect != null) {
            val rect = plan.cropRect
            canvas.drawBitmap(
                source,
                RectF(rect.left, rect.top, rect.right, rect.bottom),
                RectF(0f, 0f, outputWidth.toFloat(), outputHeight.toFloat()),
                paint,
            )
        } else {
            val blurred = blurEvidenceBackdrop(source, BLUR_RADIUS_SOURCE_PX)
            drawSameSourceBackdrop(canvas, blurred, outputWidth, outputHeight)
            val fitScale = minOf(outputWidth.toFloat() / source.width, outputHeight.toFloat() / source.height)
            val fitWidth = source.width * fitScale
            val fitHeight = source.height * fitScale
            val fitRect = RectF(
                (outputWidth - fitWidth) / 2f,
                (outputHeight - fitHeight) / 2f,
                (outputWidth + fitWidth) / 2f,
                (outputHeight + fitHeight) / 2f,
            )
            // Keep the complete source at full opacity. A blurred same-source
            // underlay inside the fitted bounds prevents transparent edges from
            // exposing a flat fill while avoiding an artificial frame.
            paint.alpha = 112
            canvas.drawBitmap(blurred, null, fitRect, paint)
            paint.alpha = 255
            canvas.drawBitmap(source, null, fitRect, paint)
            if (blurred !== source) blurred.recycle()
        }
        FishPhotoDerivativeCache.put(key, output)
        return RenderedFishPhoto(output, plan, key)
    }

    private fun drawSameSourceBackdrop(canvas: Canvas, blurred: Bitmap, width: Int, height: Int) {
        val targetAspect = width.toFloat() / height
        val sourceAspect = blurred.width.toFloat() / blurred.height
        val src = if (sourceAspect > targetAspect) {
            val cropWidth = blurred.height * targetAspect
            RectF((blurred.width - cropWidth) / 2f, 0f, (blurred.width + cropWidth) / 2f, blurred.height.toFloat())
        } else {
            val cropHeight = blurred.width / targetAspect
            RectF(0f, (blurred.height - cropHeight) / 2f, blurred.width.toFloat(), (blurred.height + cropHeight) / 2f)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { alpha = 255 }
        canvas.drawBitmap(blurred, src, RectF(0f, 0f, width.toFloat(), height.toFloat()), paint)
        // Lower contrast without introducing a flat fill color.
        canvas.drawColor(0x12000000)
    }

    fun outputAspectRatio(width: Int, height: Int): Float {
        val (outputWidth, outputHeight) = boundedOutputSize(width.coerceAtLeast(1), height.coerceAtLeast(1))
        return outputWidth.toFloat() / outputHeight
    }

    private fun boundedOutputSize(width: Int, height: Int): Pair<Int, Int> {
        val scale = minOf(1f, MAX_OUTPUT_EDGE_PX.toFloat() / max(width, height))
        return max(1, (width * scale).roundToInt()) to max(1, (height * scale).roundToInt())
    }
}

@Composable
internal fun FishPhotoErrorPlaceholder() {
    Box(
        Modifier.fillMaxSize().background(YuJianColors.MistBlueGray.copy(alpha = 0.24f)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.image_error_v12),
            contentDescription = null,
            modifier = Modifier.size(44.dp).alpha(0.58f),
        )
    }
}

@Composable
internal fun FishPhotoBitmapImage(
    bitmap: ImageBitmap,
    modifier: Modifier,
    contentDescription: String?,
    boundingBox: FishPhotoBoundingBox? = null,
    primaryAmbiguous: Boolean = false,
    allowSubjectCrop: Boolean = true,
    placeholder: @Composable () -> Unit = { FishPhotoErrorPlaceholder() },
) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val width = with(density) { maxWidth.toPx() }.takeIf { it.isFinite() }?.toInt()?.coerceAtLeast(1) ?: 480
        val height = with(density) { maxHeight.toPx() }.takeIf { it.isFinite() }?.toInt()?.coerceAtLeast(1) ?: 480
        val androidBitmap = remember(bitmap) { bitmap.asAndroidBitmap() }
        val sourceVersion = remember(androidBitmap) {
            "bitmap-${System.identityHashCode(androidBitmap)}-${androidBitmap.generationId}-${androidBitmap.width}x${androidBitmap.height}"
        }
        val key = remember(sourceVersion, width, height, boundingBox, primaryAmbiguous, allowSubjectCrop) {
            FishPhotoRenderer.cacheKey(
                sourceVersion,
                width,
                height,
                boundingBox,
                primaryAmbiguous,
                allowSubjectCrop,
                sourceWidthPx = androidBitmap.width,
                sourceHeightPx = androidBitmap.height,
            )
        }
        val targetAspect = width.toFloat() / height.coerceAtLeast(1)
        var rendered by remember(sourceVersion, boundingBox, primaryAmbiguous, allowSubjectCrop, targetAspect) {
            mutableStateOf(
                FishPhotoDerivativeCache.get(key)?.let { cached ->
                    RenderedFishPhoto(
                        cached,
                        FishPhotoCropPlanner.plan(
                            androidBitmap.width,
                            androidBitmap.height,
                            FishPhotoRenderer.outputAspectRatio(width, height),
                            boundingBox,
                            primaryAmbiguous,
                            allowSubjectCrop,
                        ),
                        key,
                    )
                },
            )
        }
        LaunchedEffect(key, androidBitmap, width, height, boundingBox, primaryAmbiguous, allowSubjectCrop) {
            val cached = FishPhotoDerivativeCache.get(key)
            rendered = cached?.let {
                RenderedFishPhoto(
                    it,
                    FishPhotoCropPlanner.plan(
                        androidBitmap.width,
                        androidBitmap.height,
                        FishPhotoRenderer.outputAspectRatio(width, height),
                        boundingBox,
                        primaryAmbiguous,
                        allowSubjectCrop,
                    ),
                    key,
                )
            } ?: runCatching {
                withContext(Dispatchers.Default) {
                    FishPhotoRenderer.render(
                        source = androidBitmap,
                        targetWidthPx = width,
                        targetHeightPx = height,
                        sourceVersion = sourceVersion,
                        boundingBox = boundingBox,
                        primaryAmbiguous = primaryAmbiguous,
                        allowSubjectCrop = allowSubjectCrop,
                    )
                }
            }.onFailure { Log.w("FishPhotoCrop", "bitmap_render_failed", it) }.getOrNull()
        }
        val cachedRender = remember(key, androidBitmap) {
            FishPhotoDerivativeCache.get(key)?.let { cached ->
                RenderedFishPhoto(
                    cached,
                    FishPhotoCropPlanner.plan(
                        androidBitmap.width,
                        androidBitmap.height,
                        FishPhotoRenderer.outputAspectRatio(width, height),
                        boundingBox,
                        primaryAmbiguous,
                        allowSubjectCrop,
                    ),
                    key,
                )
            }
        }
        val image = cachedRender ?: rendered
        if (image == null) {
            Box(Modifier.fillMaxSize()) { placeholder() }
        } else {
            Image(
                bitmap = image.bitmap.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize().testTag("fish-photo-${image.plan.mode.name.lowercase()}"),
                contentScale = ContentScale.Crop,
            )
        }
    }
}
