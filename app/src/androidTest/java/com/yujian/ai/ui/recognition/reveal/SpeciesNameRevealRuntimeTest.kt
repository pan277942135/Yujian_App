package com.yujian.ai.ui.recognition.reveal

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yujian.ai.ui.waterlight.core.RecognitionSceneIdentity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpeciesNameRevealRuntimeTest {
    @Test
    fun realMultiCharacterChinesePathsAreMeasuredAndActuallyDrawnBeforeNameStable() {
        val identity = RecognitionSceneIdentity("session-glyph", "attempt-glyph", "image-glyph", "sha-glyph", 3L)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 48f }
        val drawer = SpeciesNameReveal()

        listOf("黄颡鱼", "鲤鱼", "草鱼").forEachIndexed { index, name ->
            val speciesKey = "species-$index"
            val path = Path()
            paint.getTextPath(name, 0, name.length, 8f, 60f, path)
            val descriptor = RecognitionGlyphPathInspector.inspect(
                identity = identity,
                speciesKey = speciesKey,
                speciesName = name,
                path = path,
                fullTextRangeStart = 0,
                fullTextRangeEnd = name.length,
                builtCharacterCount = measuredCharacterCount(name, paint),
            )
            assertNotNull("All Chinese characters must produce a measured outline for $name", descriptor)
            val measured = requireNotNull(descriptor)
            assertTrue(measured.matches(identity, speciesKey, name))
            assertTrue(measured.expectedCharacterCount > 1)
            assertTrue(measured.builtCharacterCount == measured.expectedCharacterCount)
            assertTrue(measured.bounds.isValid)
            assertTrue(measured.contourCount > 0)
            assertTrue(measured.totalPathLengthPx > 1f)

            val pathReady = RecognitionGlyphReadiness(
                speciesResultReady = true,
                glyphPathReady = true,
                descriptor = measured,
            )
            assertTrue(RecognitionGlyphReadinessContract.isPathReady(identity, speciesKey, name, pathReady))
            assertFalse(
                "A generated Path is not a drawn Glyph",
                RecognitionGlyphReadinessContract.isRendered(identity, speciesKey, name, pathReady),
            )

            val bitmap = Bitmap.createBitmap(256, 88, Bitmap.Config.ARGB_8888)
            try {
                val result = drawer.draw(
                    canvas = Canvas(bitmap),
                    glyph = path,
                    width = bitmap.width.toFloat(),
                    height = bitmap.height.toFloat(),
                    mode = RecognitionRevealTitleMode.REVEALING,
                    formationProgress = 1f,
                    revealProgress = 1f,
                    sweepProgress = 1f,
                )
                assertNotNull("The real Glyph renderer must report its completed Canvas draw", result)
                val draw = requireNotNull(result)
                assertTrue(draw.renderedProgress >= .999f)
                assertTrue(draw.fillPathDrawn)
                assertTrue(draw.contourPathDrawn)
                assertTrue(draw.drawPassSequence > 0L)
                assertTrue("Canvas output must contain visible Glyph pixels", bitmap.containsVisiblePixel())

                val receipt = RecognitionGlyphDrawReceipt(
                    identity = measured.identity,
                    speciesKey = measured.speciesKey,
                    speciesName = measured.speciesName,
                    pathId = measured.pathId,
                    geometryVersion = measured.geometryVersion,
                    renderedProgress = draw.renderedProgress,
                    fillPathDrawn = draw.fillPathDrawn,
                    contourPathDrawn = draw.contourPathDrawn,
                    drawPassSequence = draw.drawPassSequence,
                )
                val rendered = pathReady.copy(
                    glyphRevealRendered = true,
                    nameStable = true,
                    renderReceipt = receipt,
                )
                assertTrue(RecognitionGlyphReadinessContract.isNameStableClaim(identity, speciesKey, name, rendered))
                assertFalse(
                    RecognitionGlyphReadinessContract.isPathReady(
                        identity.copy(attemptId = "previous-attempt"), speciesKey, name, pathReady,
                    ),
                )
                assertFalse(
                    RecognitionGlyphReadinessContract.isPathReady(identity, "wrong-species", name, pathReady),
                )
            } finally {
                bitmap.recycle()
            }
        }
    }

    @Test
    fun emptyPartialAndInvalidBoundsGlyphPathsAreRejected() {
        val identity = RecognitionSceneIdentity("session", "attempt", "image", "sha", 1L)
        val empty = Path()
        assertTrue(
            RecognitionGlyphPathInspector.inspect(identity, "species", "黄颡鱼", empty, 0, 3, builtCharacterCount = 3) == null,
        )

        val zeroHeight = Path().apply {
            moveTo(0f, 10f)
            lineTo(20f, 10f)
        }
        assertTrue(
            RecognitionGlyphPathInspector.inspect(identity, "species", "黄颡鱼", zeroHeight, 0, 3, builtCharacterCount = 3) == null,
        )

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 48f }
        val full = Path()
        paint.getTextPath("黄颡鱼", 0, 3, 0f, 60f, full)
        assertTrue(
            RecognitionGlyphPathInspector.inspect(identity, "species", "黄颡鱼", full, 0, 1, builtCharacterCount = 3) == null,
        )
    }

    private fun measuredCharacterCount(name: String, paint: Paint): Int {
        var offset = 0
        var measured = 0
        while (offset < name.length) {
            val nextOffset = offset + Character.charCount(Character.codePointAt(name, offset))
            val characterPath = Path()
            paint.getTextPath(name, offset, nextOffset, 8f, 60f, characterPath)
            val bounds = android.graphics.RectF()
            characterPath.computeBounds(bounds, true)
            if (!characterPath.isEmpty && bounds.width() > 1f && bounds.height() > 1f) measured++
            offset = nextOffset
        }
        return measured
    }

    private fun Bitmap.containsVisiblePixel(): Boolean {
        for (y in 0 until height step 2) {
            for (x in 0 until width step 2) {
                if (Color.alpha(getPixel(x, y)) > 0) return true
            }
        }
        return false
    }
}
