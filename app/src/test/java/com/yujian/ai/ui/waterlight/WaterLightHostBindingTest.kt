package com.yujian.ai.ui.waterlight

import com.yujian.ai.ui.waterlight.core.RecognitionSceneCoordinator
import com.yujian.ai.ui.waterlight.core.RecognitionSceneIdentity
import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import com.yujian.ai.ui.waterlight.core.ScenePlaybackMode
import com.yujian.ai.ui.waterlight.core.UnifiedWaterLightEngine
import com.yujian.ai.ui.waterlight.core.WaterLightFrame
import com.yujian.ai.ui.waterlight.core.WaterLightHost
import com.yujian.ai.ui.waterlight.core.WaterLightHostBinding
import com.yujian.ai.ui.waterlight.core.WaterLightHostGeometry
import com.yujian.ai.ui.waterlight.core.WaterLightMaterialProfile
import com.yujian.ai.ui.waterlight.core.WaterLightPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WaterLightHostBindingTest {
    @Test fun hostRequiresStableSessionAttemptImagePhotoAndValidOwnGeometry() {
        val identity = identity()
        val frame = frame(identity)
        WaterLightHost.entries.forEach { host ->
            val engine = UnifiedWaterLightEngine().apply { activate(frame) }
            val binding = binding(host, engine, identity)
            val rect = rect(host)
            assertTrue(host.name, binding.accepts(frame, rect))
            assertTrue(host.name, binding.rejectionReason(frame, rect) == null)
            assertRejectedWithoutFallback(binding, frame.copy(sessionId = "old"), rect, "SESSION_IDENTITY_MISMATCH")
            assertRejectedWithoutFallback(binding, frame.copy(attemptId = "old"), rect, "ATTEMPT_IDENTITY_MISMATCH")
            assertRejectedWithoutFallback(binding, frame.copy(imageId = "old"), rect, "IMAGE_IDENTITY_MISMATCH")
            assertRejectedWithoutFallback(binding, frame.copy(photoSha256 = "b".repeat(64)), rect, "PHOTO_SHA256_MISMATCH")
            assertRejectedWithoutFallback(binding, frame.copy(photoContentVersion = 9L), rect, "PHOTO_CONTENT_VERSION_MISMATCH")
            assertRejectedWithoutFallback(binding, frame, rect.copy(version = 3L), "GEOMETRY_VERSION_MISMATCH")
            assertRejectedWithoutFallback(binding, frame, rect.copy(rightPx = 19f), "INVALID_ORB_BOUNDS")
            assertRejectedWithoutFallback(
                binding, frame, rect.copy(host = WaterLightHost.entries.first { it != host }), "HOST_MISMATCH",
            )

            val wrongMaterial = binding(
                host, engine, identity,
                WaterLightMaterialProfile.ORB_LEGACY.copy(host = WaterLightHost.entries.first { it != host }),
            )
            assertEquals("MATERIAL_HOST_MISMATCH", wrongMaterial.rejectionReason(frame, rect))
            assertTrue("valid identity + valid geometry may retain source core for renderer failure",
                wrongMaterial.canUseSourceCoreFallback(frame, rect))
            engine.release()
            assertEquals("ENGINE_FRAME_MISMATCH", binding.rejectionReason(frame, rect))
            assertTrue("released renderer with the same valid session may retain source core",
                binding.canUseSourceCoreFallback(frame, rect))
        }
    }

    @Test fun invalidSceneOverrideAndInvalidGeometryCannotUseRawCoreFallback() {
        val identity = identity()
        val frame = frame(identity)
        val engine = UnifiedWaterLightEngine().apply { activate(frame) }
        val binding = binding(WaterLightHost.ORB, engine, identity)
        val rect = rect(WaterLightHost.ORB)
        val wrongOverride = frame.copy(sceneFrameOverride = frame.sceneFrameOverride!!.copy(
            identity = identity.copy(imageId = "other-image"),
        ))
        assertEquals("SCENE_FRAME_IDENTITY_MISMATCH", binding.rejectionReason(wrongOverride, rect))
        assertFalse(binding.canUseSourceCoreFallback(wrongOverride, rect))

        val wrongOverrideGeometry = frame.copy(sceneFrameOverride = frame.sceneFrameOverride!!.copy(geometryVersion = 3L))
        assertEquals("SCENE_FRAME_GEOMETRY_MISMATCH", binding.rejectionReason(wrongOverrideGeometry, rect))
        assertFalse(binding.canUseSourceCoreFallback(wrongOverrideGeometry, rect))

        val badGeometry = rect.copy(leftPx = Float.NaN)
        assertEquals("INVALID_ORB_BOUNDS", binding.rejectionReason(frame, badGeometry))
        assertFalse(binding.canUseSourceCoreFallback(frame, badGeometry))
        engine.release()
    }

    private fun assertRejectedWithoutFallback(
        binding: WaterLightHostBinding,
        frame: WaterLightFrame,
        rect: WaterLightHostGeometry,
        reason: String,
    ) {
        assertFalse(reason, binding.accepts(frame, rect))
        assertEquals(reason, binding.rejectionReason(frame, rect))
        assertFalse("$reason must never route through RAW_CORE", binding.canUseSourceCoreFallback(frame, rect))
    }

    private fun binding(
        host: WaterLightHost,
        engine: UnifiedWaterLightEngine,
        identity: RecognitionSceneIdentity,
        material: WaterLightMaterialProfile = WaterLightMaterialProfile.ORB_LEGACY.copy(host = host),
    ) = WaterLightHostBinding(host, material, engine, identity)

    private fun rect(host: WaterLightHost) = WaterLightHostGeometry(host, 4L, 20f, 30f, 180f, 190f)

    private fun identity() = RecognitionSceneIdentity("session", "attempt", "image", "a".repeat(64), 1L)

    private fun frame(identity: RecognitionSceneIdentity): WaterLightFrame {
        val scene = RecognitionSceneCoordinator.frameForStage(
            identity = identity,
            sceneElapsedMs = 950L,
            opticalReferenceElapsedMs = 950L,
            geometryVersion = 4L,
            stage = RecognitionSceneStage.ORB_RELEASE,
            stageElapsedMs = 300L,
            stageProgress = .5f,
            playbackMode = ScenePlaybackMode.AUTO,
            monotonicTimestampMs = 950L,
        )
        return WaterLightFrame(
            identity.sessionId, identity.photoSha256, 950L, 950L, WaterLightPhase.PROCESSING,
            0f, false, 4L, identity.attemptId, identity.imageId, identity.photoContentVersion,
            sceneFrameOverride = scene,
        )
    }
}
