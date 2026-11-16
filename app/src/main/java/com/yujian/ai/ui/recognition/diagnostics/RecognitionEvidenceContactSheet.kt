package com.yujian.ai.ui.recognition.diagnostics

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import java.io.File

object RecognitionEvidenceContactSheet {
    fun create(frameFiles: List<File>, output: File, columns: Int = 4): Boolean {
        val files = frameFiles.filter(File::isFile).take(64)
        if (files.isEmpty()) return false
        val columnCount = columns.coerceIn(1, 8)
        val cellWidth = 300
        val cellHeight = 210
        val rows = (files.size + columnCount - 1) / columnCount
        val sheet = Bitmap.createBitmap(columnCount * cellWidth, rows * cellHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(Color.rgb(14, 31, 37))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        files.forEachIndexed { index, file ->
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            options.inSampleSize = maxOf(1, minOf(options.outWidth / 300, options.outHeight / 180))
            options.inJustDecodeBounds = false
            val frame = BitmapFactory.decodeFile(file.absolutePath, options) ?: return@forEachIndexed
            val left = (index % columnCount) * cellWidth
            val top = (index / columnCount) * cellHeight
            val maxWidth = cellWidth - 12f
            val maxHeight = cellHeight - 30f
            val fit = minOf(maxWidth / frame.width, maxHeight / frame.height)
            val fittedWidth = frame.width * fit
            val fittedHeight = frame.height * fit
            val destination = RectF(
                left + (cellWidth - fittedWidth) * .5f,
                top + 6f + (maxHeight - fittedHeight) * .5f,
                left + (cellWidth + fittedWidth) * .5f,
                top + 6f + (maxHeight + fittedHeight) * .5f,
            )
            val source = Rect(0, 0, frame.width, frame.height)
            canvas.drawBitmap(frame, source, destination, paint)
            paint.color = Color.WHITE
            paint.textSize = 14f
            canvas.drawText(file.name.take(38), left + 8f, top + cellHeight - 7f, paint)
            frame.recycle()
        }
        output.parentFile?.mkdirs()
        val result = output.outputStream().use { sheet.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        sheet.recycle()
        return result
    }
}
