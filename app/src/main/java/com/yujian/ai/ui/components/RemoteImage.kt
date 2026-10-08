package com.yujian.ai.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max
import kotlin.math.roundToInt

private const val AmbientMaxSidePx = 480
private const val BitmapCacheSizeKb = 24 * 1024

private data class LoadedHeroMedia(
    val foreground: Bitmap,
    val ambient: Bitmap,
    val letterboxConfidence: LetterboxConfidence,
)

private object RemoteBitmapCache {
    private val cache = object : LruCache<String, LoadedHeroMedia>(BitmapCacheSizeKb) {
        override fun sizeOf(key: String, value: LoadedHeroMedia): Int =
            max(1, (value.foreground.byteCount + value.ambient.byteCount) / 1024)
    }

    fun get(key: String): LoadedHeroMedia? = synchronized(cache) { cache.get(key) }

    fun put(key: String, value: LoadedHeroMedia) {
        synchronized(cache) { cache.put(key, value) }
    }
}

@Composable
fun RemoteImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    authToken: String? = null,
    placeholder: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().background(Color.Transparent))
    },
    reloadToken: Int = 0,
    onLoadResult: ((Boolean) -> Unit)? = null,
    preservePortraitWithFitBackdrop: Boolean = false,
    colorFilter: ColorFilter? = null,
    trimVerifiedLetterbox: Boolean = false,
    adaptiveHeroVariant: CatchHeroVariant? = null,
) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val viewportWidthPx = with(density) { toFinitePxOrZero(maxWidth) }
        val viewportHeightPx = with(density) { toFinitePxOrZero(maxHeight) }
        val shouldTrimVerifiedLetterbox = trimVerifiedLetterbox || adaptiveHeroVariant != null
        val bitmapState = remember(url, authToken, reloadToken, viewportWidthPx, viewportHeightPx, shouldTrimVerifiedLetterbox) {
            mutableStateOf<LoadedHeroMedia?>(null)
        }
        val latestOnLoadResult = rememberUpdatedState(onLoadResult)

        LaunchedEffect(url, authToken, reloadToken, viewportWidthPx, viewportHeightPx, shouldTrimVerifiedLetterbox) {
            if (url.isNullOrBlank()) {
                bitmapState.value = null
                return@LaunchedEffect
            }
            val loaded = withContext(Dispatchers.IO) {
                loadMedia(
                    url = url,
                    authToken = authToken,
                    targetWidthPx = viewportWidthPx,
                    targetHeightPx = viewportHeightPx,
                    reloadToken = reloadToken,
                    trimVerifiedLetterbox = shouldTrimVerifiedLetterbox,
                )
            }
            bitmapState.value = loaded
            latestOnLoadResult.value?.invoke(loaded != null)
        }

        val media = bitmapState.value
        if (media == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { placeholder() }
        } else if (adaptiveHeroVariant != null) {
            AdaptiveCatchHeroImage(
                media = media,
                variant = adaptiveHeroVariant,
                viewportWidthPx = viewportWidthPx,
                viewportHeightPx = viewportHeightPx,
                density = density.density,
                contentDescription = contentDescription,
                colorFilter = colorFilter,
            )
        } else {
            val bitmap = media.foreground
            val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
            if (preservePortraitWithFitBackdrop && bitmap.height > bitmap.width) {
                Box(Modifier.fillMaxSize()) {
                    Image(
                        bitmap = remember(media.ambient) { media.ambient.asImageBitmap() },
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().blur(22.dp).alpha(0.48f),
                        contentScale = ContentScale.Crop,
                    )
                    Box(Modifier.fillMaxSize().background(Color(0xFF16242C).copy(alpha = 0.12f)))
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = contentDescription,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        colorFilter = colorFilter,
                    )
                }
            } else {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = contentScale,
                    colorFilter = colorFilter,
                )
            }
        }
    }
}

@Composable
private fun AdaptiveCatchHeroImage(
    media: LoadedHeroMedia,
    variant: CatchHeroVariant,
    viewportWidthPx: Int,
    viewportHeightPx: Int,
    density: Float,
    contentDescription: String?,
    colorFilter: ColorFilter?,
) {
    val plan = remember(media.foreground, variant, viewportWidthPx, viewportHeightPx, density, media.letterboxConfidence) {
        CatchHeroMediaPlanner.plan(
            CatchHeroMediaRequest(
                sourceWidthPx = media.foreground.width,
                sourceHeightPx = media.foreground.height,
                viewportWidthPx = viewportWidthPx,
                viewportHeightPx = viewportHeightPx,
                variant = variant,
                trustedFishRect = null,
                trustedFishBox = false,
                letterboxConfidence = media.letterboxConfidence,
                density = density,
            ),
        )
    }
    val presentationBitmap = remember(media.foreground, plan.sourceCropRectPx) {
        val crop = plan.sourceCropRectPx
        if (crop == null || crop.left < 0 || crop.top < 0 ||
            crop.right > media.foreground.width || crop.bottom > media.foreground.height ||
            crop.width <= 0 || crop.height <= 0
        ) {
            media.foreground
        } else {
            runCatching { Bitmap.createBitmap(media.foreground, crop.left, crop.top, crop.width, crop.height) }
                .getOrDefault(media.foreground)
        }
    }
    val foregroundImage = remember(presentationBitmap) { presentationBitmap.asImageBitmap() }
    val ambientImage = remember(media.ambient) { media.ambient.asImageBitmap() }

    Box(Modifier.fillMaxSize()) {
        if (plan.backgroundPolicy == CatchHeroBackgroundPolicy.SAME_SOURCE_AMBIENT) {
            Image(
                bitmap = ambientImage,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().blur(18.dp).alpha(0.38f),
                contentScale = ContentScale.Crop,
            )
            Box(Modifier.fillMaxSize().background(Color(0xFF16242C).copy(alpha = 0.10f)))
        }
        Image(
            bitmap = foregroundImage,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize().testTag("catch-hero-mode-${plan.mode.name}"),
            contentScale = if (plan.foregroundFit) ContentScale.Fit else ContentScale.Crop,
            colorFilter = colorFilter,
        )
    }
}

private fun Density.toFinitePxOrZero(value: Dp): Int =
    if (value == Dp.Infinity || !value.value.isFinite()) 0 else value.roundToPx().coerceAtLeast(0)

private fun loadMedia(
    url: String,
    authToken: String?,
    targetWidthPx: Int,
    targetHeightPx: Int,
    reloadToken: Int,
    trimVerifiedLetterbox: Boolean,
): LoadedHeroMedia? = runCatching {
    val cacheKey = "$url|${authToken?.hashCode() ?: 0}|${targetWidthPx}x$targetHeightPx|$reloadToken|$trimVerifiedLetterbox"
    RemoteBitmapCache.get(cacheKey)?.let { return it }

    val bytes = readImageBytes(url, authToken) ?: return null
    val orientation = runCatching {
        ExifOrientation.fromExif(
            ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            ),
        )
    }.getOrDefault(ExifOrientation.NORMAL)

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val (orientedWidth, orientedHeight) = orientation.orientedSize(bounds.outWidth, bounds.outHeight)
    val targetScale = if (targetWidthPx <= 0 || targetHeightPx <= 0) 0.0 else minOf(
        targetWidthPx.toDouble() / orientedWidth,
        targetHeightPx.toDouble() / orientedHeight,
    )
    val orientedTargetWidth = (orientedWidth * targetScale).toInt().coerceAtLeast(1)
    val orientedTargetHeight = (orientedHeight * targetScale).toInt().coerceAtLeast(1)
    val rawTargetWidth = if (orientedWidth == bounds.outWidth) orientedTargetWidth else orientedTargetHeight
    val rawTargetHeight = if (orientedHeight == bounds.outHeight) orientedTargetHeight else orientedTargetWidth
    val options = BitmapFactory.Options().apply {
        inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, rawTargetWidth, rawTargetHeight)
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
    val oriented = applyExifOrientation(decoded, orientation)
    val analysis = if (trimVerifiedLetterbox) analyzeLetterbox(oriented) else LetterboxResult.NONE
    val foreground = if (trimVerifiedLetterbox && analysis.hasInsets) {
        trimLetterbox(oriented, analysis.insets)
    } else {
        oriented
    }
    val ambient = createAmbientBitmap(foreground)
    LoadedHeroMedia(
        foreground = foreground,
        ambient = ambient,
        letterboxConfidence = if (analysis.hasInsets) LetterboxConfidence.VERIFIED else LetterboxConfidence.NONE,
    ).also { RemoteBitmapCache.put(cacheKey, it) }
}.getOrNull()

private fun readImageBytes(url: String, authToken: String?): ByteArray? = runCatching {
    if (url.startsWith("file://")) {
        return@runCatching File(Uri.parse(url).path.orEmpty()).takeIf(File::isFile)?.readBytes()
    }
    if (url.startsWith("/")) {
        return@runCatching File(url).takeIf(File::isFile)?.readBytes()
    }
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 8_000
        readTimeout = 12_000
        instanceFollowRedirects = true
        authToken?.takeIf(String::isNotBlank)?.let { setRequestProperty("Authorization", "Bearer $it") }
    }
    try {
        if (connection.responseCode !in 200..299) return null
        connection.inputStream.use { it.readBytes() }
    } finally {
        connection.disconnect()
    }
}.getOrNull()

private fun calculateSampleSize(width: Int, height: Int, targetWidth: Int, targetHeight: Int): Int {
    if (targetWidth <= 0 || targetHeight <= 0) return 1
    var sample = 1
    while (width / (sample * 2) >= targetWidth && height / (sample * 2) >= targetHeight) {
        sample *= 2
    }
    return sample
}

private fun applyExifOrientation(bitmap: Bitmap, orientation: ExifOrientation): Bitmap {
    if (orientation == ExifOrientation.NORMAL || orientation == ExifOrientation.UNKNOWN) return bitmap
    val matrix = Matrix()
    when (orientation) {
        ExifOrientation.FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
        ExifOrientation.ROTATE_180 -> matrix.setRotate(180f)
        ExifOrientation.FLIP_VERTICAL -> matrix.setScale(1f, -1f)
        ExifOrientation.TRANSPOSE -> {
            matrix.setRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifOrientation.ROTATE_90_CW -> matrix.setRotate(90f)
        ExifOrientation.TRANSVERSE -> {
            matrix.setRotate(-90f)
            matrix.postScale(-1f, 1f)
        }
        ExifOrientation.ROTATE_270_CW -> matrix.setRotate(270f)
        ExifOrientation.NORMAL, ExifOrientation.UNKNOWN -> return bitmap
    }
    return runCatching {
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
            if (it !== bitmap) bitmap.recycle()
        }
    }.getOrDefault(bitmap)
}

private data class LetterboxInsets(val left: Int = 0, val top: Int = 0, val right: Int = 0, val bottom: Int = 0)
private data class LetterboxResult(val insets: LetterboxInsets, val hasInsets: Boolean) {
    companion object { val NONE = LetterboxResult(LetterboxInsets(), false) }
}

/** A band is accepted only when it is nearly pure black across the whole edge and content resumes inside it. */
private fun analyzeLetterbox(bitmap: Bitmap): LetterboxResult {
    val insets = LetterboxInsets(
        left = verifiedBlackBand(bitmap, Edge.LEFT),
        top = verifiedBlackBand(bitmap, Edge.TOP),
        right = verifiedBlackBand(bitmap, Edge.RIGHT),
        bottom = verifiedBlackBand(bitmap, Edge.BOTTOM),
    )
    if (insets.left + insets.right < bitmap.width * 0.035f &&
        insets.top + insets.bottom < bitmap.height * 0.035f
    ) return LetterboxResult.NONE
    if (bitmap.width - insets.left - insets.right < bitmap.width * 0.45f ||
        bitmap.height - insets.top - insets.bottom < bitmap.height * 0.45f
    ) return LetterboxResult.NONE
    return LetterboxResult(insets, true)
}

private enum class Edge { LEFT, TOP, RIGHT, BOTTOM }

private fun verifiedBlackBand(bitmap: Bitmap, edge: Edge): Int {
    val horizontalEdge = edge == Edge.LEFT || edge == Edge.RIGHT
    val extent = if (horizontalEdge) bitmap.width else bitmap.height
    val crossExtent = if (horizontalEdge) bitmap.height else bitmap.width
    val maxScan = (extent * 0.35f).toInt().coerceAtLeast(1)
    val samples = 64
    var band = 0
    for (offset in 0 until maxScan) {
        val edgeCoordinate = when (edge) {
            Edge.LEFT, Edge.TOP -> offset
            Edge.RIGHT, Edge.BOTTOM -> extent - offset - 1
        }
        var blackCount = 0
        for (sample in 0 until samples) {
            val cross = (crossExtent * (0.02f + 0.96f * sample / (samples - 1))).toInt().coerceIn(0, crossExtent - 1)
            val x = if (horizontalEdge) edgeCoordinate else cross
            val y = if (horizontalEdge) cross else edgeCoordinate
            val pixel = bitmap.getPixel(x, y)
            val red = android.graphics.Color.red(pixel)
            val green = android.graphics.Color.green(pixel)
            val blue = android.graphics.Color.blue(pixel)
            if (android.graphics.Color.alpha(pixel) >= 245 && maxOf(red, green, blue) <= 10) blackCount++
        }
        if (blackCount < samples * 0.98f) break
        band++
    }

    if (band < extent * 0.03f || band >= maxScan) return 0
    val probeOffset = (band + max(2, (extent * 0.003f).toInt())).coerceAtMost(extent - 1)
    var nonBlackAfterBand = 0
    for (sample in 0 until samples) {
        val cross = (crossExtent * (0.02f + 0.96f * sample / (samples - 1))).toInt().coerceIn(0, crossExtent - 1)
        val x = if (horizontalEdge) probeOffset else cross
        val y = if (horizontalEdge) cross else probeOffset
        val pixel = bitmap.getPixel(x, y)
        if (maxOf(
                android.graphics.Color.red(pixel),
                android.graphics.Color.green(pixel),
                android.graphics.Color.blue(pixel),
            ) > 20
        ) nonBlackAfterBand++
    }
    return if (nonBlackAfterBand >= samples * 0.60f) band else 0
}

private fun trimLetterbox(bitmap: Bitmap, insets: LetterboxInsets): Bitmap = runCatching {
    Bitmap.createBitmap(
        bitmap,
        insets.left,
        insets.top,
        bitmap.width - insets.left - insets.right,
        bitmap.height - insets.top - insets.bottom,
    ).also { if (it !== bitmap) bitmap.recycle() }
}.getOrDefault(bitmap)

private fun createAmbientBitmap(source: Bitmap): Bitmap {
    val longest = max(source.width, source.height)
    if (longest <= AmbientMaxSidePx) return source
    val ratio = AmbientMaxSidePx.toFloat() / longest
    val width = (source.width * ratio).roundToInt().coerceAtLeast(1)
    val height = (source.height * ratio).roundToInt().coerceAtLeast(1)
    return runCatching { Bitmap.createScaledBitmap(source, width, height, true) }.getOrDefault(source)
}
