package com.yujian.ai.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.io.File
import java.security.MessageDigest
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap
import com.yujian.ai.ui.imagery.FishPhotoBoundingBox
import com.yujian.ai.ui.imagery.FishPhotoCropPlanner

/** Small process-wide cache for repeated Home pages; auth secrets are never retained in keys. */
private object RemoteBitmapMemoryCache {
    private const val MAX_BYTES = 24 * 1024 * 1024
    private val entries = object : LinkedHashMap<String, Bitmap>(8, 0.75f, true) {}
    private var bytes = 0

    @Synchronized
    fun get(key: String): Bitmap? = entries[key]

    @Synchronized
    fun put(key: String, bitmap: Bitmap) {
        val previous = entries.put(key, bitmap)
        if (previous != null) bytes -= previous.byteCount
        bytes += bitmap.byteCount
        val iterator = entries.entries.iterator()
        while (bytes > MAX_BYTES && iterator.hasNext()) {
            val evicted = iterator.next().value
            bytes -= evicted.byteCount
            iterator.remove()
            // Do not recycle: a composed Image may still hold this bitmap until it leaves composition.
        }
    }
}

/** Non-blocking, opt-in runtime observation for the NH01 instrumentation waits. */
internal object RemoteImageRuntimeProbe {
    data class Snapshot(val loadStatus: String?, val rendered: Boolean?)

    @Volatile
    private var captureEnabled = false
    private val states = ConcurrentHashMap<String, Snapshot>()

    fun beginCapture() {
        states.clear()
        captureEnabled = true
    }

    fun endCapture() {
        captureEnabled = false
    }

    fun snapshot(identity: String): Snapshot? = states[identity]

    internal fun recordLoadStatus(identity: String?, status: String) {
        if (!captureEnabled || identity.isNullOrBlank()) return
        synchronized(states) {
            val previous = states[identity]
            states[identity] = Snapshot(
                loadStatus = status,
                rendered = if (status == "LOADING") false else previous?.rendered,
            )
        }
    }

    internal fun recordRendered(identity: String?, rendered: Boolean) {
        if (!captureEnabled || identity.isNullOrBlank()) return
        synchronized(states) {
            states[identity] = Snapshot(states[identity]?.loadStatus, rendered)
        }
    }
}

@Composable
fun RemoteImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    authToken: String? = null,
    placeholder: @Composable () -> Unit = { Box(modifier = Modifier.fillMaxSize().background(Color.Transparent)) },
    reloadToken: Int = 0,
    onLoadResult: ((Boolean) -> Unit)? = null,
    preservePortraitWithFitBackdrop: Boolean = false,
    preserveEvidenceWithFitBackdrop: Boolean = false,
    colorFilter: ColorFilter? = null,
    trimVerifiedLetterbox: Boolean = false,
    cacheIdentity: String? = null,
    smartFishPhoto: Boolean = false,
    fishBoundingBox: FishPhotoBoundingBox? = null,
    fishSubjectAmbiguous: Boolean = false,
    fishPhotoSourceVersion: String? = null,
) {
    if (smartFishPhoto) {
        SmartRemoteFishPhoto(
            url = url,
            modifier = modifier,
            contentDescription = contentDescription,
            authToken = authToken,
            placeholder = placeholder,
            reloadToken = reloadToken,
            onLoadResult = onLoadResult,
            cacheIdentity = fishPhotoSourceVersion ?: cacheIdentity,
            boundingBox = fishBoundingBox,
            primaryAmbiguous = fishSubjectAmbiguous,
        )
        return
    }
    val cacheKey = remember(url, authToken, reloadToken, cacheIdentity, preserveEvidenceWithFitBackdrop) {
        url?.takeIf(String::isNotBlank)?.let {
            val tokenDigest = authToken?.takeIf(String::isNotBlank)?.let(::sha256Hex).orEmpty()
            listOf(it, tokenDigest, reloadToken.toString(), cacheIdentity.orEmpty(), preserveEvidenceWithFitBackdrop.toString())
                .joinToString("\u0000")
        }
    }
    val bitmapState = remember(cacheKey) { mutableStateOf<Bitmap?>(null) }
    val latestOnLoadResult = rememberUpdatedState(onLoadResult)
    LaunchedEffect(cacheKey, url, authToken, preserveEvidenceWithFitBackdrop) {
        if (cacheKey == null || url.isNullOrBlank()) {
            bitmapState.value = null
            return@LaunchedEffect
        }
        val loaded = RemoteBitmapMemoryCache.get(cacheKey) ?: withContext(Dispatchers.IO) {
            loadBitmap(url, authToken, orientEvidence = preserveEvidenceWithFitBackdrop)
                ?.also { RemoteBitmapMemoryCache.put(cacheKey, it) }
        }
        bitmapState.value = loaded
        latestOnLoadResult.value?.invoke(loaded != null)
    }
    val sourceBitmap = bitmapState.value
    val bitmap = remember(sourceBitmap, trimVerifiedLetterbox) {
        if (trimVerifiedLetterbox) sourceBitmap?.let(::trimVerifiedSolidLetterbox) else sourceBitmap
    }
    val imageBitmap = remember(bitmap) { bitmap?.asImageBitmap() }
    if (imageBitmap != null) {
        if (preserveEvidenceWithFitBackdrop) {
            // The original image is both the blurred cover and the fitted evidence.
            // The opaque base keeps transparent source pixels from exposing the page behind the Hero.
            BoxWithConstraints(modifier = modifier) {
                val density = LocalDensity.current
                val coverScale = maxOf(
                    with(density) { maxWidth.toPx() } / imageBitmap.width,
                    with(density) { maxHeight.toPx() } / imageBitmap.height,
                ).coerceAtLeast(0.001f)
                val sourceBlurRadius = with(density) { 22.dp.toPx() } / coverScale
                val backdrop = remember(bitmap, sourceBlurRadius) { mutableStateOf<Bitmap?>(null) }
                LaunchedEffect(bitmap, sourceBlurRadius) {
                    backdrop.value = withContext(Dispatchers.Default) {
                        blurEvidenceBackdrop(requireNotNull(bitmap), sourceBlurRadius)
                    }
                }
                Box(Modifier.fillMaxSize().background(Color(0xFF16242C)))
                if (backdrop.value == null) {
                    // Until the soft backdrop is ready, use the same source as a full-bleed
                    // crop. This removes hard letterbox bands without stretching or cropping
                    // the fitted evidence foreground.
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().testTag("home-evidence-backdrop-crop-fallback"),
                        contentScale = ContentScale.Crop,
                    )
                }
                backdrop.value?.let { blurred ->
                    Image(
                        bitmap = blurred.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().testTag("home-evidence-backdrop-ready"),
                        contentScale = ContentScale.Crop,
                    )
                }
                Box(Modifier.fillMaxSize().background(Color(0xFF16242C).copy(alpha = 0.08f)))
                Image(
                    bitmap = imageBitmap,
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize().testTag("home-evidence-fit-foreground"),
                    contentScale = ContentScale.Fit,
                    colorFilter = colorFilter,
                )
            }
        } else if (preservePortraitWithFitBackdrop && bitmap?.let { it.height > it.width } == true) {
            Box(modifier = modifier) {
                Image(
                    bitmap = imageBitmap,
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
                modifier = modifier,
                contentScale = contentScale,
                colorFilter = colorFilter,
            )
        }
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) { placeholder() }
    }
}

@Composable
private fun SmartRemoteFishPhoto(
    url: String?,
    modifier: Modifier,
    contentDescription: String?,
    authToken: String?,
    placeholder: @Composable () -> Unit,
    reloadToken: Int,
    onLoadResult: ((Boolean) -> Unit)?,
    cacheIdentity: String?,
    boundingBox: FishPhotoBoundingBox?,
    primaryAmbiguous: Boolean,
) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val targetWidth = with(density) { maxWidth.toPx() }.takeIf { it.isFinite() }?.toInt()?.coerceAtLeast(1) ?: 480
        val targetHeight = with(density) { maxHeight.toPx() }.takeIf { it.isFinite() }?.toInt()?.coerceAtLeast(1) ?: 480
        val tokenDigest = remember(authToken) { authToken?.takeIf(String::isNotBlank)?.let(::sha256Hex).orEmpty() }
        val sourceVersion = listOf(
            cacheIdentity?.takeIf(String::isNotBlank).orEmpty(),
            url.orEmpty(),
            reloadToken.toString(),
            tokenDigest,
        )
            .filter(String::isNotBlank).joinToString("|")
        val decodeWidth = (((targetWidth + 63) / 64) * 64).coerceAtLeast(64)
        val decodeHeight = (((targetHeight + 63) / 64) * 64).coerceAtLeast(64)
        val decodeBucket = "${decodeWidth}x${decodeHeight}"
        val sourceKey = remember(url, tokenDigest, reloadToken, sourceVersion, decodeBucket) {
            url?.takeIf(String::isNotBlank)?.let {
                "fish-source-v1-${sha256Hex(listOf(sourceVersion, it, tokenDigest, reloadToken, decodeBucket).joinToString("\u0000"))}"
            }
        }
        val derivativeSourceVersion = remember(sourceVersion) { sha256Hex(sourceVersion) }
        val sourceState = remember(sourceVersion) {
            mutableStateOf(sourceKey?.let(RemoteBitmapMemoryCache::get))
        }
        val latestOnLoadResult = rememberUpdatedState(onLoadResult)
        LaunchedEffect(sourceKey, url, authToken, targetWidth, targetHeight) {
            if (sourceKey == null || url.isNullOrBlank()) {
                sourceState.value = null
                latestOnLoadResult.value?.invoke(false)
                return@LaunchedEffect
            }
            val cachedSource = RemoteBitmapMemoryCache.get(sourceKey)
            val source = cachedSource ?: withContext(Dispatchers.IO) {
                loadSampledOrientedBitmap(url, authToken, decodeWidth, decodeHeight)
                    ?.also { RemoteBitmapMemoryCache.put(sourceKey, it) }
            }
            if (source == null) {
                android.util.Log.w("FishPhotoCrop", "image_decode_failed")
                latestOnLoadResult.value?.invoke(sourceState.value != null)
            } else {
                sourceState.value = source
            }
        }

        val source = sourceState.value.takeIf { sourceKey != null }
        val renderKey = remember(sourceKey, source, targetWidth, targetHeight, boundingBox, primaryAmbiguous) {
            source?.let {
                FishPhotoRenderer.cacheKey(
                    sourceVersion = derivativeSourceVersion,
                    targetWidthPx = targetWidth,
                    targetHeightPx = targetHeight,
                    boundingBox = boundingBox,
                    primaryAmbiguous = primaryAmbiguous,
                    sourceWidthPx = it.width,
                    sourceHeightPx = it.height,
                )
            }
        }
        val targetAspect = targetWidth.toFloat() / targetHeight.coerceAtLeast(1)
        val renderedState = remember(sourceVersion, boundingBox, primaryAmbiguous, targetAspect) {
            mutableStateOf<RenderedFishPhoto?>(null)
        }
        LaunchedEffect(source, renderKey, targetWidth, targetHeight, boundingBox, primaryAmbiguous) {
            if (source == null || renderKey == null) {
                renderedState.value = null
                return@LaunchedEffect
            }
            val existing = FishPhotoDerivativeCache.get(renderKey)
            val rendered = existing?.let {
                val plan = FishPhotoCropPlanner.plan(
                    source.width, source.height, FishPhotoRenderer.outputAspectRatio(targetWidth, targetHeight),
                    boundingBox, primaryAmbiguous,
                )
                RenderedFishPhoto(it, plan, renderKey)
            } ?: runCatching {
                withContext(Dispatchers.Default) {
                    FishPhotoRenderer.render(
                        source = source,
                        targetWidthPx = targetWidth,
                        targetHeightPx = targetHeight,
                        sourceVersion = derivativeSourceVersion,
                        boundingBox = boundingBox,
                        primaryAmbiguous = primaryAmbiguous,
                    )
                }
            }.onFailure { android.util.Log.w("FishPhotoCrop", "image_render_failed", it) }.getOrNull()
            renderedState.value = rendered
            latestOnLoadResult.value?.invoke(rendered != null)
        }

        val cachedRender = if (source != null && renderKey != null) {
            FishPhotoDerivativeCache.get(renderKey)?.let { bitmap ->
                RenderedFishPhoto(
                    bitmap,
                    FishPhotoCropPlanner.plan(
                        source.width,
                        source.height,
                        FishPhotoRenderer.outputAspectRatio(targetWidth, targetHeight),
                        boundingBox,
                        primaryAmbiguous,
                    ),
                    renderKey,
                )
            }
        } else null
        val rendered = (cachedRender ?: renderedState.value).takeIf { sourceKey != null }
        SideEffect {
            // This is recorded after composition, so READY plus rendered=true proves
            // the actual fish-photo Image node was emitted, not only that decoding ended.
            RemoteImageRuntimeProbe.recordRendered(cacheIdentity, rendered != null)
        }
        if (rendered != null) {
            Box(Modifier.fillMaxSize().testTag("home-evidence-backdrop-ready")) {
                val imageTag = if (rendered.plan.mode == com.yujian.ai.ui.imagery.FishPhotoDisplayMode.FULL_FRAME_WITH_SOURCE_BACKDROP) {
                    "home-evidence-fit-foreground"
                } else {
                    "fish-photo-subject-crop"
                }
                Image(
                    bitmap = rendered.bitmap.asImageBitmap(),
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize().testTag(imageTag),
                contentScale = ContentScale.Crop,
                )
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { placeholder() }
        }
    }
}

private fun sha256Hex(value: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    return bytes.joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

private fun loadBitmap(url: String, authToken: String?, orientEvidence: Boolean = false): Bitmap? = runCatching {
    if (orientEvidence) {
        val bytes = when {
            url.startsWith("file://") -> File(requireNotNull(Uri.parse(url).path)).readBytes()
            url.startsWith("/") -> File(url).readBytes()
            else -> {
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
            }
        }
        return@runCatching decodeOrientedEvidence(bytes)
    }
    if (url.startsWith("file://")) {
        return@runCatching BitmapFactory.decodeFile(Uri.parse(url).path)
    }
    if (url.startsWith("/")) {
        return@runCatching BitmapFactory.decodeFile(File(url).absolutePath)
    }
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 8_000
        readTimeout = 12_000
        instanceFollowRedirects = true
        authToken?.takeIf(String::isNotBlank)?.let { setRequestProperty("Authorization", "Bearer $it") }
    }
    try {
        if (connection.responseCode !in 200..299) return null
        connection.inputStream.use { BitmapFactory.decodeStream(it) }
    } finally {
        connection.disconnect()
    }
}.getOrNull()

private fun loadSampledOrientedBitmap(url: String, authToken: String?, targetWidth: Int, targetHeight: Int): Bitmap? = runCatching {
    val bytes = when {
        url.startsWith("file://") -> File(requireNotNull(Uri.parse(url).path)).readBytes()
        url.startsWith("/") -> File(url).readBytes()
        else -> {
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
        }
    }
    decodeOrientedEvidenceSampled(bytes, targetWidth, targetHeight)
}.getOrNull()

/** Crop only verified, uniform near-black source margins for presentation. The source file/bytes are never edited. */
private fun trimVerifiedSolidLetterbox(source: Bitmap): Bitmap {
    val left = solidDarkMargin(source, side = BorderSide.LEFT)
    val right = solidDarkMargin(source, side = BorderSide.RIGHT)
    val top = solidDarkMargin(source, side = BorderSide.TOP)
    val bottom = solidDarkMargin(source, side = BorderSide.BOTTOM)
    val cropLeft = left.coerceAtMost(source.width / 3)
    val cropRight = right.coerceAtMost(source.width / 3)
    val cropTop = top.coerceAtMost(source.height / 3)
    val cropBottom = bottom.coerceAtMost(source.height / 3)
    if (cropLeft + cropRight < source.width * 0.035f && cropTop + cropBottom < source.height * 0.035f) return source
    val width = source.width - cropLeft - cropRight
    val height = source.height - cropTop - cropBottom
    if (width < source.width * 0.45f || height < source.height * 0.45f) return source
    return runCatching { Bitmap.createBitmap(source, cropLeft, cropTop, width, height) }.getOrDefault(source)
}

private enum class BorderSide { LEFT, TOP, RIGHT, BOTTOM }

private fun solidDarkMargin(bitmap: Bitmap, side: BorderSide): Int {
    val horizontal = side == BorderSide.LEFT || side == BorderSide.RIGHT
    val extent = if (horizontal) bitmap.width else bitmap.height
    val crossExtent = if (horizontal) bitmap.height else bitmap.width
    val maxScan = (extent * 0.45f).toInt().coerceAtLeast(1)
    val samples = 64
    var margin = 0
    for (offset in 0 until maxScan) {
        val edge = when (side) {
            BorderSide.LEFT, BorderSide.TOP -> offset
            BorderSide.RIGHT, BorderSide.BOTTOM -> extent - offset - 1
        }
        var darkCount = 0
        for (sample in 0 until samples) {
            val cross = (crossExtent * (0.025f + 0.95f * sample / (samples - 1))).toInt().coerceIn(0, crossExtent - 1)
            val x = if (horizontal) edge else cross
            val y = if (horizontal) cross else edge
            val pixel = bitmap.getPixel(x, y)
            val alpha = android.graphics.Color.alpha(pixel)
            val red = android.graphics.Color.red(pixel)
            val green = android.graphics.Color.green(pixel)
            val blue = android.graphics.Color.blue(pixel)
            if (alpha >= 245 && maxOf(red, green, blue) <= 24 && maxOf(red, green, blue) - minOf(red, green, blue) <= 8) {
                darkCount += 1
            }
        }
        if (darkCount < (samples * 0.98f).toInt()) break
        margin += 1
    }
    return if (margin >= extent * 0.03f) margin else 0
}
