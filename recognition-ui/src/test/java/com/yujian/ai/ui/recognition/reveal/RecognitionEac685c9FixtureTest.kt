package com.yujian.ai.ui.recognition.reveal

import com.yujian.ai.ai.NormalizedFishBox
import com.yujian.ai.ai.subject.FishSubjectQuality
import com.yujian.ai.ai.subject.SubjectStatus
import com.yujian.ai.ui.identify.DisplayPoint
import com.yujian.ai.ui.identify.NormalizedSourceRect
import com.yujian.ai.ui.identify.RecognitionContentScaleMode
import com.yujian.ai.ui.identify.RecognitionImageTransform
import com.yujian.ai.ui.recognition.RecognitionContourSegment
import com.yujian.ai.ui.waterlight.core.RecognitionFishPathSnapshot
import com.yujian.ai.ui.waterlight.core.WaterLightPathGeometry
import com.yujian.ai.ui.waterlight.core.WaterLightPathPoint
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionEac685c9FixtureTest {
    @Test
    fun actualHuaweiPathStaysPendingUntilProcessingPhotoIdentityAndPStarArrive() {
        val fixture = fixture()
        val identity = fixture.getJSONObject("identity")
        val path = fixturePath(fixture)
        val mask = fixture.getJSONObject("observedMask")
        val reject = fixture.getJSONObject("bindingFailure")
        val subject = subject(
            identity = identity,
            maskSha256 = mask.getString("sha256"),
            maskVersion = mask.getLong("version"),
        )
        val incompletePhoto = photo(
            identity = identity,
            transform = fixtureTransform(path),
        )

        assertTrue("preserved device route must remain valid", path.isValid())
        assertEquals("device producer digest must replay byte-for-byte", path.snapshotSha256, path.calculateContentSha256())
        assertEquals("logged device P* must be retained", .5604575f, path.pStarNormalizedSource.x, 0f)
        assertEquals("logged device P* must be retained", .9372829f, path.pStarNormalizedSource.y, 0f)
        assertEquals("the legacy snapshot schema stays explicit", 0, path.snapshotVersion)
        assertEquals("unrecorded model version stays transparent", "NOT_RECORDED_IN_DEVICE_EVIDENCE", path.modelVersion)
        assertEquals("P* viewport is the logged route endpoint", path.fishPathGeometry.end, path.pStarViewport)
        assertEquals("logged store failure had the same mask version", mask.getLong("version"), reject.getString("expectedMaskVersion").toLong())

        assertEquals(
            "empty photo Mask/P*/geometry fields mean the join is incomplete, not a Mask mismatch",
            RecognitionFishPathSnapshotBindOutcome.WAITING_FOR_INPUTS,
            evaluate(path, subject, incompletePhoto, identity),
        )
        val maskReadyPhoto = incompletePhoto.copy(
            contactPointMaskSha256 = path.maskSha256,
            contactPointMaskVersion = path.maskVersion,
        )
        assertEquals(
            "a missing P* remains pending after the Mask identity arrives",
            RecognitionFishPathSnapshotBindOutcome.WAITING_FOR_INPUTS,
            evaluate(path, subject, maskReadyPhoto, identity),
        )
        val pStarReadyPhoto = maskReadyPhoto.copy(
            contactPointNormalizedSource = DisplayPoint(path.pStarNormalizedSource.x, path.pStarNormalizedSource.y),
        )
        assertEquals(
            "geometry version zero remains pending after Mask and source P* arrive",
            RecognitionFishPathSnapshotBindOutcome.WAITING_FOR_INPUTS,
            evaluate(path, subject, pStarReadyPhoto, identity),
        )
        assertEquals(
            "missing transform dimensions are pending rather than a geometry mismatch",
            RecognitionFishPathSnapshotBindOutcome.WAITING_FOR_INPUTS,
            evaluate(
                path,
                subject,
                pStarReadyPhoto.copy(
                    contactPointGeometryVersion = path.geometryVersion,
                    sourceToViewport = pStarReadyPhoto.sourceToViewport.copy(sourceWidthPx = 0),
                ),
                identity,
            ),
        )
        assertEquals(
            "a malformed transform remains a true geometry rejection",
            RecognitionFishPathSnapshotBindOutcome.GEOMETRY_MISMATCH,
            evaluate(
                path,
                subject,
                pStarReadyPhoto.copy(
                    contactPointGeometryVersion = path.geometryVersion,
                    sourceToViewport = pStarReadyPhoto.sourceToViewport.copy(translationX = Float.NaN),
                ),
                identity,
            ),
        )
        val pendingDiagnostic = RecognitionFishPathSnapshotBinding.diagnostic(
            path = path,
            subject = subject,
            photo = incompletePhoto,
            sessionId = identity.getString("sessionId"),
            attemptId = identity.getString("attemptId"),
            imageId = identity.getString("imageId"),
            photoSha256 = identity.getString("photoSha256"),
            photoContentVersion = identity.getLong("photoContentVersion"),
            state = RecognitionFishPathSnapshotBindingState.RECEIVED_PENDING,
            subjectStatus = SubjectStatus.READY,
            handoffFrozen = false,
        )
        val missingInputs = pendingDiagnostic.getValue("pendingInputs").split(',').toSet()
        assertTrue(missingInputs.containsAll(setOf("PHOTO_MASK_SHA256", "PHOTO_MASK_VERSION", "PHOTO_PSTAR_SOURCE", "PHOTO_GEOMETRY_VERSION")))

        val completedPhoto = incompletePhoto.copy(
            contactPointNormalizedSource = DisplayPoint(
                path.pStarNormalizedSource.x,
                path.pStarNormalizedSource.y,
            ),
            contactPointMaskSha256 = path.maskSha256,
            contactPointMaskVersion = path.maskVersion,
            contactPointGeometryVersion = path.geometryVersion,
        )
        assertEquals(
            "the same device path must bind when the missing photo-side metadata arrives",
            RecognitionFishPathSnapshotBindOutcome.ACCEPTED,
            evaluate(path, subject, completedPhoto, identity),
        )

        assertEquals(
            "a non-empty conflicting Mask SHA remains a true Mask rejection",
            RecognitionFishPathSnapshotBindOutcome.MASK_MISMATCH,
            evaluate(path, subject, completedPhoto.copy(contactPointMaskSha256 = "f".repeat(64)), identity),
        )
        assertEquals(
            "a positive but conflicting geometry version remains a true geometry rejection",
            RecognitionFishPathSnapshotBindOutcome.GEOMETRY_MISMATCH,
            evaluate(path, subject, completedPhoto.copy(contactPointGeometryVersion = path.geometryVersion + 1L), identity),
        )
        assertEquals(
            "an older attempt is rejected even if its Mask and P* agree",
            RecognitionFishPathSnapshotBindOutcome.IDENTITY_MISMATCH,
            evaluate(path.copy(attemptId = "old-attempt"), subject, completedPhoto, identity),
        )
    }

    private fun evaluate(
        path: RecognitionFishPathSnapshot,
        subject: RecognitionSubjectGeometrySnapshot,
        photo: RecognitionProcessingPhotoSnapshot,
        identity: JSONObject,
    ) = RecognitionFishPathSnapshotBinding.evaluate(
        path = path,
        subjectStatus = SubjectStatus.READY,
        subject = subject,
        photo = photo,
        sessionId = identity.getString("sessionId"),
        attemptId = identity.getString("attemptId"),
        imageId = identity.getString("imageId"),
        photoSha256 = identity.getString("photoSha256"),
        photoContentVersion = identity.getLong("photoContentVersion"),
    )

    private fun fixture(): JSONObject =
        JSONObject(requireNotNull(javaClass.getResourceAsStream("/recognition/FIXTURE_HUAWEI_EAC685C9_V1.json")) {
            "EAC685C9 source fixture is missing from unit-test resources"
        }.bufferedReader().use { it.readText() })

    private fun fixturePath(fixture: JSONObject): RecognitionFishPathSnapshot {
        val identity = fixture.getJSONObject("identity")
        val path = fixture.getJSONObject("path")
        return RecognitionFishPathSnapshot(
            sessionId = identity.getString("sessionId"),
            attemptId = identity.getString("attemptId"),
            imageId = identity.getString("imageId"),
            photoSha256 = identity.getString("photoSha256"),
            photoContentVersion = identity.getLong("photoContentVersion"),
            maskSha256 = path.getString("maskSha256"),
            maskVersion = path.getLong("maskVersion"),
            geometryVersion = path.getLong("geometryVersion"),
            pStarNormalizedSource = path.getJSONObject("pStarNormalizedSource").asPathPoint(),
            fishPathId = path.getString("pathId"),
            fishPathGeometry = path.getJSONObject("fishPathGeometry").asPathGeometry(),
            perimeterBranch = path.getJSONObject("perimeterBranch").asPathGeometry(),
            orbReleasePath = path.getJSONObject("orbReleasePath").asPathGeometry(),
            returnPathId = path.getString("returnPathId"),
            returnPathSegments = path.getJSONArray("returnPathSegments").let { segments ->
                (0 until segments.length()).map { segments.getJSONObject(it).asPathGeometry() }
            },
            sourceCoordinateSpace = path.getString("coordinateSpace"),
            creationTimestampMonotonicMs = path.getLong("creationTimestampMonotonicMs"),
            creationEventSequence = path.getLong("creationEventSequence"),
            snapshotSha256 = path.getString("snapshotSha256"),
            snapshotVersion = path.getInt("snapshotVersion"),
            modelVersion = path.getString("modelVersion"),
            pStarViewport = path.getJSONObject("pStarViewport").asPathPoint(),
        )
    }

    private fun subject(
        identity: JSONObject,
        maskSha256: String,
        maskVersion: Long,
    ) = RecognitionSubjectGeometrySnapshot(
        imageId = identity.getString("imageId"),
        recognitionSessionId = identity.getString("sessionId"),
        recognitionAttemptId = identity.getString("attemptId"),
        photoSha256 = identity.getString("photoSha256"),
        // The evidence archive omits the original photo dimensions. These dimensions belong only
        // to the synthetic transform used to exercise the source-space join in this unit test.
        sourceWidthPx = TEST_SOURCE_SIZE,
        sourceHeightPx = TEST_SOURCE_SIZE,
        detectorBox = NormalizedFishBox(.1f, .1f, .9f, .9f),
        detectorConfidence = .9f,
        subjectBox = com.yujian.ai.ui.identify.NormalizedSourceRect(.1f, .1f, .9f, .9f),
        contour = listOf(RecognitionContourSegment(.1f, .1f, .1f, .1f, closesContour = true)),
        quality = FishSubjectQuality.GOOD,
        maskSize = 1031040,
        expectedMaskSize = 1031040,
        maskVersion = maskVersion,
        maskSha256 = maskSha256,
    )

    private fun photo(
        identity: JSONObject,
        transform: RecognitionImageTransform,
    ) = RecognitionProcessingPhotoSnapshot(
        imageId = identity.getString("imageId"),
        viewportBoundsInWindow = RecognitionRevealWindowRect(0f, 0f, 1280f, 2832f),
        sourceToViewport = transform,
        // Defaults reproduce the logged incomplete Processing Photo: empty Mask SHA, -1 version,
        // null P*, and geometry version 0.
    )

    private fun fixtureTransform(path: RecognitionFishPathSnapshot): RecognitionImageTransform {
        val endpoint = path.fishPathGeometry.end
        val contact = path.pStarNormalizedSource
        val drawnWidth = endpoint.x / contact.x
        val drawnHeight = endpoint.y / contact.y
        return RecognitionImageTransform(
            sourceWidthPx = TEST_SOURCE_SIZE,
            sourceHeightPx = TEST_SOURCE_SIZE,
            viewportWidthPx = 1280f,
            viewportHeightPx = 2832f,
            contentScaleMode = RecognitionContentScaleMode.FIT,
            scale = 1f,
            translationX = 0f,
            translationY = 0f,
            drawnWidth = drawnWidth,
            drawnHeight = drawnHeight,
            visibleSourceRect = NormalizedSourceRect(0f, 0f, 1f, 1f),
        )
    }

    private fun JSONObject.asPathPoint() = WaterLightPathPoint(getDouble("x").toFloat(), getDouble("y").toFloat())

    private fun JSONObject.asPathGeometry() = WaterLightPathGeometry(
        start = getJSONObject("start").asPathPoint(),
        control1 = getJSONObject("control1").asPathPoint(),
        control2 = getJSONObject("control2").asPathPoint(),
        end = getJSONObject("end").asPathPoint(),
        coordinateSpace = getString("coordinateSpace"),
    )

    private companion object {
        const val TEST_SOURCE_SIZE = 1000
    }
}
