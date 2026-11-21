package com.yujian.ai.ui.recognition.reveal

import com.yujian.ai.ai.NormalizedFishBox
import com.yujian.ai.ai.subject.FishSubjectQuality
import com.yujian.ai.ai.subject.SubjectStatus
import com.yujian.ai.ui.identify.DisplayPoint
import com.yujian.ai.ui.identify.calculateRecognitionImageTransform
import com.yujian.ai.ui.recognition.RecognitionContourSegment
import com.yujian.ai.ui.waterlight.core.RecognitionFishPathSnapshot
import com.yujian.ai.ui.waterlight.core.WaterLightPathGeometry
import com.yujian.ai.ui.waterlight.core.WaterLightPathGeometryFactory
import com.yujian.ai.ui.waterlight.core.WaterLightPathPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionFishPathSnapshotBindingTest {
    private val maskSha = "a".repeat(64)
    private val photoSha = "b".repeat(64)
    private val contact = DisplayPoint(.62f, .43f)
    private val geometryVersion = 19L
    private val subject = RecognitionSubjectGeometrySnapshot(
        imageId = IMAGE,
        recognitionSessionId = SESSION,
        recognitionAttemptId = ATTEMPT,
        photoSha256 = photoSha,
        sourceWidthPx = WIDTH,
        sourceHeightPx = HEIGHT,
        detectorBox = NormalizedFishBox(.25f, .2f, .8f, .75f),
        detectorConfidence = .96f,
        subjectBox = com.yujian.ai.ui.identify.NormalizedSourceRect(.2f, .15f, .85f, .8f),
        contour = listOf(RecognitionContourSegment(.1f, .1f, .1f, .1f, closesContour = true)),
        quality = FishSubjectQuality.GOOD,
        maskSize = 256,
        expectedMaskSize = 256,
        maskVersion = 7L,
        maskSha256 = maskSha,
    )
    private val photo = RecognitionProcessingPhotoSnapshot(
        imageId = IMAGE,
        viewportBoundsInWindow = RecognitionRevealWindowRect(0f, 0f, 1080f, 1920f),
        sourceToViewport = calculateRecognitionImageTransform(1080f, 1920f, WIDTH, HEIGHT),
        contactPointNormalizedSource = contact,
        contactPointMaskSha256 = maskSha,
        contactPointMaskVersion = 7L,
        contactPointGeometryVersion = geometryVersion,
    )
    private val path = pathSnapshot()

    @Test
    fun subjectPhotoPathCallbackOrdersJoinToTheSameFrozenPath() {
        val orders = listOf(
            listOf("SUBJECT", "PHOTO", "PATH"),
            listOf("PHOTO", "PATH", "SUBJECT"),
            listOf("PATH", "SUBJECT", "PHOTO"),
        )
        val outcomes = orders.map(::runOrder)
        assertEquals(listOf(path, path, path), outcomes)
    }

    @Test
    fun pathWaitsForMissingInputsAndRejectsOldMaskPhotoOrAttemptIdentity() {
        assertEquals(
            RecognitionFishPathSnapshotBindOutcome.WAITING_FOR_INPUTS,
            evaluate(path = path, subject = null, photo = photo),
        )
        assertEquals(
            RecognitionFishPathSnapshotBindOutcome.IDENTITY_MISMATCH,
            evaluate(subject = subject.copy(maskSha256 = "c".repeat(64))),
        )
        assertEquals(
            RecognitionFishPathSnapshotBindOutcome.IDENTITY_MISMATCH,
            evaluate(photo = photo.copy(contactPointGeometryVersion = geometryVersion + 1)),
        )
        assertEquals(
            RecognitionFishPathSnapshotBindOutcome.IDENTITY_MISMATCH,
            evaluate(path = path.copy(attemptId = "old-attempt")),
        )
        val movedEnd = path.fishPathGeometry.end.copy(x = path.fishPathGeometry.end.x + 3f)
        val wrongEndpoint = path.copy(
            fishPathGeometry = path.fishPathGeometry.copy(end = movedEnd),
            returnPathSegments = listOf(
                geometry(movedEnd.x, movedEnd.y, path.perimeterBranch.end.x, path.perimeterBranch.end.y),
                path.returnPathSegments[1],
                path.returnPathSegments[2],
            ),
        )
        assertTrue("fixture remains a structurally continuous but P-star-mismatched route", wrongEndpoint.isValid())
        assertEquals(
            RecognitionFishPathSnapshotBindOutcome.IDENTITY_MISMATCH,
            evaluate(path = wrongEndpoint),
        )
    }

    @Test
    fun nonPositiveGeometryCannotBindAndDuplicateCallbackIsIdempotentlyAcceptable() {
        assertEquals(
            RecognitionFishPathSnapshotBindOutcome.IDENTITY_MISMATCH,
            evaluate(path = path.copy(geometryVersion = 0L)),
        )
        assertEquals(RecognitionFishPathSnapshotBindOutcome.ACCEPTED, evaluate())
        assertEquals(RecognitionFishPathSnapshotBindOutcome.ACCEPTED, evaluate())
    }

    private fun runOrder(order: List<String>): RecognitionFishPathSnapshot {
        var subjectReady = false
        var photoReady = false
        var pendingPath: RecognitionFishPathSnapshot? = null
        var acceptedPath: RecognitionFishPathSnapshot? = null
        order.forEach { callback ->
            when (callback) {
                "SUBJECT" -> subjectReady = true
                "PHOTO" -> photoReady = true
                "PATH" -> pendingPath = path
            }
            if (acceptedPath == null && pendingPath != null) {
                when (evaluate(
                    path = pendingPath,
                    subject = subject.takeIf { subjectReady },
                    photo = photo.takeIf { photoReady },
                    subjectStatus = if (subjectReady) SubjectStatus.READY else SubjectStatus.PROCESSING,
                )) {
                    RecognitionFishPathSnapshotBindOutcome.WAITING_FOR_INPUTS -> Unit
                    RecognitionFishPathSnapshotBindOutcome.ACCEPTED -> {
                        acceptedPath = pendingPath
                        pendingPath = null
                    }
                    RecognitionFishPathSnapshotBindOutcome.IDENTITY_MISMATCH -> error("valid callback order mismatched")
                }
            }
        }
        assertTrue("valid callback order must leave no stale pending path", pendingPath == null)
        assertFalse("path identity must be frozen only once", acceptedPath != null && pendingPath != null)
        return requireNotNull(acceptedPath)
    }

    private fun evaluate(
        path: RecognitionFishPathSnapshot? = this.path,
        subject: RecognitionSubjectGeometrySnapshot? = this.subject,
        photo: RecognitionProcessingPhotoSnapshot? = this.photo,
        subjectStatus: SubjectStatus = SubjectStatus.READY,
    ) = RecognitionFishPathSnapshotBinding.evaluate(
        path = path,
        subjectStatus = subjectStatus,
        subject = subject,
        photo = photo,
        sessionId = SESSION,
        attemptId = ATTEMPT,
        imageId = IMAGE,
        photoSha256 = photoSha,
        photoContentVersion = 42L,
    )

    private fun pathSnapshot(): RecognitionFishPathSnapshot {
        val release = geometry(10f, 20f, 30f, 40f)
        val arrival = geometry(30f, 40f, 70f, 80f)
        val pStarViewport = photo.sourceToViewport.mapNormalized(contact.x, contact.y)
        val fish = geometry(70f, 80f, pStarViewport.x, pStarViewport.y)
        val returnSegments = listOf(
            geometry(pStarViewport.x, pStarViewport.y, 70f, 80f),
            geometry(70f, 80f, 30f, 40f),
            geometry(30f, 40f, 10f, 20f),
        )
        return RecognitionFishPathSnapshot(
            sessionId = SESSION,
            attemptId = ATTEMPT,
            imageId = IMAGE,
            photoSha256 = photoSha,
            photoContentVersion = 42L,
            maskSha256 = maskSha,
            maskVersion = 7L,
            geometryVersion = geometryVersion,
            pStarNormalizedSource = WaterLightPathPoint(contact.x, contact.y),
            fishPathId = "waterlight:$ATTEMPT:fish",
            fishPathGeometry = fish,
            perimeterBranch = arrival,
            orbReleasePath = release,
            returnPathId = "waterlight:$ATTEMPT:fish",
            returnPathSegments = returnSegments,
            sourceCoordinateSpace = "RECOGNITION_VIEWPORT_PX",
            creationTimestampMonotonicMs = 600L,
            creationEventSequence = 12L,
        )
    }

    private fun geometry(sx: Float, sy: Float, ex: Float, ey: Float): WaterLightPathGeometry =
        requireNotNull(
            WaterLightPathGeometryFactory.between(
                WaterLightPathPoint(sx, sy),
                WaterLightPathPoint(ex, ey),
                seed = 23,
                bendFraction = .04f,
            ),
        )

    private companion object {
        const val SESSION = "session-26kf"
        const val ATTEMPT = "attempt-26kf"
        const val IMAGE = "image-26kf"
        const val WIDTH = 1200
        const val HEIGHT = 800
    }
}
