package com.yujian.ai.ui.recognition.reveal

import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import com.yujian.ai.ai.ProductionRecognitionResult
import com.yujian.ai.ai.subject.FishSubjectResult
import com.yujian.ai.inference.InferenceAsset
import com.yujian.ai.model.RecognitionPrediction
import com.yujian.ai.model.SelectedImage
import com.yujian.ai.ui.waterlight.core.RecognitionSceneIdentity
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import com.yujian.ai.ui.waterlight.core.RecognitionFishPathSnapshot
import com.yujian.ai.ui.waterlight.core.WaterLightPathGeometry
import com.yujian.ai.ui.waterlight.core.WaterLightPathPoint
import com.yujian.ai.ui.waterlight.core.EnergyFeedback
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Geometry and raster readiness are separate; a measured photo may be used while its crop renders. */
object RecognitionResultGeometryReadiness {
    fun isReady(
        photoIdentityMatches: Boolean,
        transitionGeometryReady: Boolean,
        heroBoundsReady: Boolean,
        titleBoundsReady: Boolean,
        revealGeometryReady: Boolean,
    ): Boolean = photoIdentityMatches && transitionGeometryReady && heroBoundsReady &&
        titleBoundsReady && revealGeometryReady
}

/** RETURN waits for the Result Canvas receipt before the shared controller consumes its EnergyEvent. */
object RecognitionReturnEventSubmissionReadiness {
    fun isReady(
        feedback: EnergyFeedback,
        level: Float,
        hasResultSceneFrame: Boolean,
        returnCanvasReceiptAccepted: Boolean,
        returnEnergyEventAlreadyAccepted: Boolean,
    ): Boolean {
        if (!hasResultSceneFrame || !level.isFinite() || level < .999f) return false
        return feedback != EnergyFeedback.RETURN ||
            (returnCanvasReceiptAccepted && !returnEnergyEventAlreadyAccepted)
    }
}

object RecognitionGlyphPathInspector {
    fun inspect(
        identity: RecognitionSceneIdentity,
        speciesKey: String,
        speciesName: String,
        path: Path,
        fullTextRangeStart: Int,
        fullTextRangeEnd: Int,
        builtCharacterCount: Int,
    ): RecognitionGlyphPathDescriptor? {
        val expectedCharacterCount = speciesName.codePointCount(0, speciesName.length)
        if (speciesKey.isBlank() || speciesName.isBlank() || fullTextRangeStart != 0 ||
            fullTextRangeEnd != speciesName.length || expectedCharacterCount <= 0 ||
            builtCharacterCount != expectedCharacterCount || path.isEmpty
        ) return null
        val bounds = RectF()
        path.computeBounds(bounds, true)
        val glyphBounds = RecognitionGlyphBounds(bounds.left, bounds.top, bounds.right, bounds.bottom)
        if (!glyphBounds.isValid) return null

        val measure = PathMeasure(path, false)
        val contourMetrics = mutableListOf<String>()
        var totalLength = 0f
        var contourCount = 0
        do {
            val length = measure.length
            if (!length.isFinite() || length < 0f) return null
            if (length > 0f) {
                val position = FloatArray(2)
                val tangent = FloatArray(2)
                measure.getPosTan(length * .5f, position, tangent)
                if (!position[0].isFinite() || !position[1].isFinite()) return null
                totalLength += length
                contourCount++
                contourMetrics += listOf(
                    length.toBits(), position[0].toBits(), position[1].toBits(),
                    tangent[0].toBits(), tangent[1].toBits(),
                ).joinToString(":")
            }
        } while (measure.nextContour())
        if (contourCount == 0 || !totalLength.isFinite() || totalLength <= 1f) return null

        val signature = buildString {
            append(identity.sessionId).append('|').append(identity.attemptId).append('|')
            append(identity.imageId).append('|').append(identity.photoSha256.orEmpty()).append('|')
            append(speciesKey).append('|').append(speciesName).append('|')
            append(glyphBounds.left.toBits()).append('|').append(glyphBounds.top.toBits()).append('|')
            append(glyphBounds.right.toBits()).append('|').append(glyphBounds.bottom.toBits()).append('|')
            append(contourMetrics.joinToString(";"))
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(signature.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        return RecognitionGlyphPathDescriptor(
            identity = identity,
            speciesKey = speciesKey,
            speciesName = speciesName,
            pathId = "glyph:" + digest,
            geometryVersion = digest.take(15).toLong(16).coerceAtLeast(1L),
            expectedCharacterCount = expectedCharacterCount,
            builtCharacterCount = builtCharacterCount,
            contourCount = contourCount,
            totalPathLengthPx = totalLength,
            bounds = glyphBounds,
        )
    }
}

/** One immutable view of all data rendered or saved by the Result route. */
data class RecognitionResultSnapshot(
    val key: RecognitionRevealKey,
    val image: SelectedImage,
    val productionResult: ProductionRecognitionResult,
    val prediction: RecognitionPrediction,
    val subjectResult: FishSubjectResult,
    val inferenceAsset: InferenceAsset?,
    val subjectGeometry: RecognitionSubjectGeometrySnapshot?,
    val handoff: RecognitionRevealHandoff,
    val fishPathSnapshot: RecognitionFishPathSnapshot?,
    val captureTimestampEpochMs: Long,
    val captureTimestampSource: String,
    val glyphSpeciesKey: String,
    val glyphSpeciesName: String,
    val revealEligibility: RecognitionRevealEligibility,
    val handoffState: RecognitionResultHandoffState,
    val resultDataReady: Boolean,
    val resultSceneStable: Boolean,
)
