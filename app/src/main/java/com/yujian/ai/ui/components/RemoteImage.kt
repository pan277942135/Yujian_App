package com.yujian.ai.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.io.ByteArrayInputStream
import java.io.File

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
    colorFilter: ColorFilter? = null,
    trimVerifiedLetterbox: Boolean = false,
    preserveEvidenceWithFitBackdrop: Boolean = false,
    respectExifOrientation: Boolean = false,
) {
    val bitmapState = remember(url, authToken, reloadToken, respectExifOrientation) { mutableStateOf<Bitmap?>(null) }
    val latestOnLoadResult = rememberUpdatedState(onLoadResult)
    LaunchedEffect(url, authToken, reloadToken, respectExifOrientation) {
        if (url.isNullOrBlank()) {
            bitmapState.value = null
            return@LaunchedEffect
        }
        val loaded = withContext(Dispatchers.IO) { loadBitmap(url, authToken, respectExifOrientation) }
        bitmapState.value = loaded
        latestOnLoadResult.value?.invoke(loaded != null)
    }
    val sourceBitmap = bitmapState.value
    val bitmap = remember(sourceBitmap, trimVerifiedLetterbox) {
        if (trimVerifiedLetterbox) sourceBitmap?.let(::trimVerifiedSolidLetterbox) else sourceBitmap
    }
    val imageBitmap = remember(bitmap) { bitmap?.asImageBitmap() }
    if (imageBitmap != null) {
        if (preserveEvidenceWithFitBackdrop ||
            (preservePortraitWithFitBackdrop && bitmap?.let { it.height > it.width } == true)
        ) {
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

private fun loadBitmap(url: String, authToken: String?, respectExifOrientation: Boolean): Bitmap? = runCatching {
    if (url.startsWith("file://")) {
        val file = File(Uri.parse(url).path ?: return null)
        return@runCatching if (respectExifOrientation) decodeExifOrientedBitmap(file.readBytes())
        else BitmapFactory.decodeFile(file.absolutePath)
    }
    if (url.startsWith("/")) {
        val file = File(url)
        return@runCatching if (respectExifOrientation) decodeExifOrientedBitmap(file.readBytes())
        else BitmapFactory.decodeFile(file.absolutePath)
    }
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 8_000
        readTimeout = 12_000
        instanceFollowRedirects = true
        authToken?.takeIf(String::isNotBlank)?.let { setRequestProperty("Authorization", "Bearer $it") }
    }
    try {
        if (connection.responseCode !in 200..299) return null
        connection.inputStream.use { input ->
            if (respectExifOrientation) decodeExifOrientedBitmap(input.readBytes())
            else BitmapFactory.decodeStream(input)
        }
    } finally {
        connection.disconnect()
    }
}.getOrNull()

/** Applies EXIF orientation to a display bitmap only; the encoded source bytes remain untouched. */
private fun decodeExifOrientedBitmap(encoded: ByteArray): Bitmap? {
    val source = BitmapFactory.decodeByteArray(encoded, 0, encoded.size) ?: return null
    val orientation = runCatching {
        ExifInterface(ByteArrayInputStream(encoded)).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    if (orientation == ExifInterface.ORIENTATION_NORMAL ||
        orientation == ExifInterface.ORIENTATION_UNDEFINED
    ) return source

    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
            matrix.setRotate(180f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.setRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.setRotate(-90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
        else -> return source
    }
    val oriented = runCatching {
        Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }.getOrDefault(source)
    if (oriented !== source) source.recycle()
    return oriented
}

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
