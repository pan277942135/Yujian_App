package com.yujian.ai.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun RemoteImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: @Composable () -> Unit = { Box(modifier = Modifier.fillMaxSize().background(Color.Transparent)) },
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = url) {
        value = if (url.isNullOrBlank()) null else withContext(Dispatchers.IO) { loadBitmap(url) }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
        )
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) { placeholder() }
    }
}

private fun loadBitmap(url: String): Bitmap? = runCatching {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 8_000
        readTimeout = 12_000
        instanceFollowRedirects = true
    }
    try {
        if (connection.responseCode !in 200..299) return null
        connection.inputStream.use { BitmapFactory.decodeStream(it) }
    } finally {
        connection.disconnect()
    }
}.getOrNull()
