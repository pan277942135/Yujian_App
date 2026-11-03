package com.yujian.ai.ui.designsystem.lakeglass

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Log
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class LakeGlassSceneAssets(
    val manifest: LakeGlassSceneManifest,
    val baseBitmap: Bitmap,
    val opticalMaskBitmap: Bitmap?,
    val usingMasterFallback: Boolean,
    val decodeElapsedMs: Long,
    val cacheKey: String,
)

/** One process cache for every theme-background route. Decoding and SHA checks stay off the UI thread. */
internal object LakeGlassAssetRepository {
    private const val TAG = "LakeGlassAssets"
    private val mutex = Mutex()
    @Volatile private var cached: LakeGlassSceneAssets? = null

    fun peek(
        viewportWidthPx: Int,
        viewportHeightPx: Int,
        includeOpticalMask: Boolean,
    ): LakeGlassSceneAssets? = cached?.takeIf { current ->
        current.baseBitmap.width >= viewportWidthPx &&
            current.baseBitmap.height >= viewportHeightPx &&
            (!includeOpticalMask || current.opticalMaskBitmap != null)
    }

    suspend fun load(
        context: Context,
        viewportWidthPx: Int,
        viewportHeightPx: Int,
        includeOpticalMask: Boolean,
    ): LakeGlassSceneAssets = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        mutex.withLock {
            val current = cached
            val sufficient = current != null && current.baseBitmap.width >= viewportWidthPx &&
                current.baseBitmap.height >= viewportHeightPx &&
                (!includeOpticalMask || current.opticalMaskBitmap != null)
            if (sufficient) return@withLock requireNotNull(current)

            val startedAt = SystemClock.elapsedRealtime()
            val manifest = current?.manifest ?: run {
                val manifestText = appContext.assets.open(LakeGlassSceneManifest.ASSET_PATH)
                    .bufferedReader(Charsets.UTF_8).use { it.readText() }
                LakeGlassSceneManifest.parse(manifestText)
            }
            val sampleSize = decodeSampleSize(
                manifest.masterWidth,
                manifest.masterHeight,
                viewportWidthPx.coerceAtLeast(1),
                viewportHeightPx.coerceAtLeast(1),
            )

            val baseStillFits = current != null && current.baseBitmap.width >= viewportWidthPx &&
                current.baseBitmap.height >= viewportHeightPx
            val (baseBitmap, usingFallback) = if (baseStillFits) {
                current!!.baseBitmap to current.usingMasterFallback
            } else {
                val primary = decodeLayer(
                    context = appContext,
                    layer = manifest.staticLayer,
                    sampleSize = sampleSize,
                )
                if (primary != null) {
                    primary to false
                } else {
                    val fallback = requireNotNull(decodeLayer(appContext, manifest.fallbackLayer, sampleSize)) {
                        "Both static composite and Master fallback failed SHA/decode validation"
                    }
                    fallback to true
                }
            }
            require(baseBitmap.width > 0 && baseBitmap.height > 0)

            val maskBitmap = current?.opticalMaskBitmap ?: if (includeOpticalMask) {
                decodeLayer(appContext, manifest.opticalMaskLayer, sampleSize = 1)
            } else null
            if (includeOpticalMask && maskBitmap == null) {
                Log.w(TAG, "Optical mask failed verification; selecting static-only mode")
            }
            val elapsed = SystemClock.elapsedRealtime() - startedAt
            val result = LakeGlassSceneAssets(
                manifest = manifest,
                baseBitmap = baseBitmap,
                opticalMaskBitmap = maskBitmap,
                usingMasterFallback = usingFallback,
                decodeElapsedMs = elapsed,
                cacheKey = "${baseBitmap.width}x${baseBitmap.height}:${manifest.staticLayer.assetSha256}",
            )
            cached = result
            Log.i(
                TAG,
                "Scene decoded in ${elapsed}ms; bitmap=${baseBitmap.width}x${baseBitmap.height}, " +
                    "bytes=${baseBitmap.allocationByteCount}, mask=${maskBitmap?.allocationByteCount ?: 0}, " +
                    "fallback=$usingFallback",
            )
            result
        }
    }

    fun release() {
        // Drop the process-owned cache reference. Active Compose hosts may still be drawing
        // these immutable bitmaps; they are reclaimed when those owners are disposed.
        cached = null
    }

    private fun decodeLayer(
        context: Context,
        layer: LakeGlassManifestLayer,
        sampleSize: Int,
    ): Bitmap? = runCatching {
        val path = assetPath(layer.assetPath)
        val bytes = context.assets.open(path).use { it.readBytes() }
        require(sha256(bytes) == layer.assetSha256) { "SHA-256 mismatch for ${layer.layerId}" }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inScaled = false
            inDither = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)) {
            "Unable to decode ${layer.layerId}"
        }
    }.onFailure { error ->
        Log.e(TAG, "${layer.layerId} unavailable; static fallback policy applies", error)
    }.getOrNull()

    private fun decodeSampleSize(sourceWidth: Int, sourceHeight: Int, targetWidth: Int, targetHeight: Int): Int {
        var sample = 1
        while (sourceWidth / (sample * 2) >= targetWidth && sourceHeight / (sample * 2) >= targetHeight) {
            sample *= 2
        }
        return sample
    }

    private fun assetPath(path: String): String {
        require(path.startsWith("assets/")) { "Manifest path must stay within its asset pack: $path" }
        return "lake_glass_v2/$path"
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte) }
}
