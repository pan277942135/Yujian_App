package com.yujian.ai.media

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object TestGallerySeeder {
    private const val TAG = "TestGallerySeeder"
    private const val PREFS_NAME = "test_gallery_seeder_prefs"
    private const val SEEDED_KEY = "seeded_catches_v3"

    data class TestSampleItem(
        val assetName: String,
        val displayName: String,
        val title: String,
    )

    val SAMPLE_ITEMS = listOf(
        TestSampleItem("test_fish_01_catfish.jpg", "黄颡鱼/鲶鱼样本1.jpg", "鲶鱼/黄颡鱼"),
        TestSampleItem("test_fish_02_yellow_catfish.jpg", "黄颡鱼样本2.jpg", "黄颡鱼"),
        TestSampleItem("test_fish_03_mandarin_fish.jpg", "鳜鱼/桂鱼样本1.jpg", "翘嘴鳜/桂鱼"),
        TestSampleItem("test_fish_04_mandarin_perch.jpg", "鳜鱼样本2.jpg", "鳜鱼/花鲫"),
    )

    /**
     * Seeds the bundled test images into the device/emulator's system Gallery (MediaStore).
     * Once seeded, the images immediately appear in the system Photo Picker and Gallery apps.
     */
    suspend fun seedTestImagesIfNeeded(context: Context): Int = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(SEEDED_KEY, false)) {
            return@withContext 0
        }

        var insertedCount = 0
        try {
            val assetManager = context.assets
            val resolver = context.contentResolver

            for (item in SAMPLE_ITEMS) {
                val fileName = item.displayName
                val assetPath = "test_catches/${item.assetName}"

                // Verify asset exists
                val assetExists = runCatching {
                    assetManager.open(assetPath).use { it.available() }
                }.isSuccess
                if (!assetExists) continue

                // Check if already in MediaStore by DISPLAY_NAME
                val exists = runCatching {
                    val projection = arrayOf(MediaStore.Images.Media._ID)
                    val selection = "${MediaStore.Images.Media.DISPLAY_NAME} = ?"
                    resolver.query(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        projection,
                        selection,
                        arrayOf(fileName),
                        null,
                    )?.use { it.moveToFirst() } ?: false
                }.getOrDefault(false)

                if (exists) {
                    insertedCount++
                    continue
                }

                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/YuJian")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    } else {
                        val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                        picturesDir.mkdirs()
                        val targetFile = File(picturesDir, fileName)
                        put(MediaStore.Images.Media.DATA, targetFile.absolutePath)
                    }
                }

                val itemUri: Uri? = runCatching {
                    resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                }.getOrNull()

                if (itemUri != null) {
                    val writeOk = runCatching {
                        resolver.openOutputStream(itemUri)?.use { out ->
                            assetManager.open(assetPath).use { input ->
                                input.copyTo(out)
                            }
                        }
                    }.isSuccess

                    if (writeOk) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            values.clear()
                            values.put(MediaStore.Images.Media.IS_PENDING, 0)
                            resolver.update(itemUri, values, null, null)
                        } else {
                            val path = values.getAsString(MediaStore.Images.Media.DATA)
                            if (path != null) {
                                MediaScannerConnection.scanFile(context, arrayOf(path), arrayOf("image/jpeg"), null)
                            }
                        }
                        insertedCount++
                        Log.i(TAG, "Successfully seeded $fileName to gallery: $itemUri")
                    }
                }
            }

            prefs.edit().putBoolean(SEEDED_KEY, true).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed seeding test images to gallery", e)
        }
        insertedCount
    }
}
