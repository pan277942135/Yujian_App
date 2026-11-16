package com.yujian.ai.ui.waterlight

import com.yujian.ai.ui.waterlight.core.RecognitionSceneStage
import com.yujian.ai.ui.waterlight.renderer.OrbCoreRotationFieldV1
import com.yujian.ai.ui.waterlight.renderer.OrbFilamentRole
import com.yujian.ai.ui.waterlight.renderer.OrbFilamentPathMathV1
import com.yujian.ai.ui.waterlight.renderer.OrbResidentFilamentLifecycle
import com.yujian.ai.ui.waterlight.renderer.OrbResidentFilamentMotionV1
import com.yujian.ai.ui.waterlight.renderer.OrbResidentTransmissionFieldV1
import com.yujian.ai.ui.waterlight.renderer.OrbRuntimeProfileV1
import com.yujian.ai.ui.waterlight.renderer.OrbStage02VisualProfile
import com.yujian.ai.ui.waterlight.renderer.usesProductionOptics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrbResidentSystemsV1Test {
    @Test fun onlyTheFrozenFiveR06PathsAreResident() {
        assertEquals(setOf("G01", "G02", "A01", "A02", "C01"), OrbResidentFilamentMotionV1.PATH_IDS)
        OrbResidentFilamentMotionV1.PATH_IDS.forEach {
            assertEquals(OrbFilamentRole.RESIDENT_AMBIENT, OrbResidentFilamentMotionV1.role(it))
        }
        assertEquals(OrbFilamentRole.TASK, OrbResidentFilamentMotionV1.role("waterlight:attempt:path"))
    }

    @Test fun taskReleaseAndReturnDoNotChangeResidentLifecycleOrAlpha() {
        val releaseEarly = OrbResidentFilamentMotionV1.lifecycle(RecognitionSceneStage.ORB_RELEASE, .1f, false)
        val releaseLate = OrbResidentFilamentMotionV1.lifecycle(RecognitionSceneStage.ORB_RELEASE, .9f, false)
        val returning = OrbResidentFilamentMotionV1.lifecycle(RecognitionSceneStage.ORB_RETURN, .7f, false)
        assertEquals(OrbResidentFilamentLifecycle.ACTIVE, releaseEarly.lifecycle)
        assertEquals(releaseEarly, releaseLate)
        assertEquals(releaseEarly, returning)
        assertEquals(1f, returning.effectiveAlpha, 0f)

        val dissolve = OrbResidentFilamentMotionV1.lifecycle(RecognitionSceneStage.SPECIES_NAME_REVEAL, .5f, true)
        assertEquals(OrbResidentFilamentLifecycle.DISSOLVE, dissolve.lifecycle)
        assertTrue(dissolve.effectiveAlpha in 0f..1f)
    }

    @Test fun residentGeometryRotatesOnSceneTimeAndReducedMotionFreezesIt() {
        val spec = OrbRuntimeProfileV1.paths.single { it.id == "G01" }
        val still = OrbFilamentPathMathV1.newLut().also {
            OrbFilamentPathMathV1.fillLut(spec, 0L, it)
            OrbResidentFilamentMotionV1.transformLut(spec, it, 0L, reducedMotion = false)
        }
        val rotated = OrbFilamentPathMathV1.newLut().also {
            OrbFilamentPathMathV1.fillLut(spec, 0L, it)
            OrbResidentFilamentMotionV1.transformLut(spec, it, 1_000L, reducedMotion = false)
        }
        assertTrue(still.x.indices.any { index ->
            kotlin.math.abs(still.x[index] - rotated.x[index]) > .001f ||
                kotlin.math.abs(still.y[index] - rotated.y[index]) > .001f
        })

        val reducedAtZero = OrbFilamentPathMathV1.newLut().also {
            OrbFilamentPathMathV1.fillLut(spec, 0L, it)
            OrbResidentFilamentMotionV1.transformLut(spec, it, 0L, reducedMotion = true)
        }
        val reducedLater = OrbFilamentPathMathV1.newLut().also {
            OrbFilamentPathMathV1.fillLut(spec, 0L, it)
            OrbResidentFilamentMotionV1.transformLut(spec, it, 1_000L, reducedMotion = true)
        }
        assertTrue(reducedAtZero.x.contentEquals(reducedLater.x))
        assertTrue(reducedAtZero.y.contentEquals(reducedLater.y))
    }

    @Test fun coreRotationIsSceneClockDrivenRadialAndRadiusPreserving() {
        val center = OrbCoreRotationFieldV1.sample(1_000L, 0f, reducedMotion = false)
        val later = OrbCoreRotationFieldV1.sample(2_000L, 0f, reducedMotion = false)
        val edge = OrbCoreRotationFieldV1.sample(2_000L, 1f, reducedMotion = false)
        val frozen = OrbCoreRotationFieldV1.sample(2_000L, .4f, reducedMotion = true)
        assertTrue(center.phaseRadians > 0f)
        assertTrue(kotlin.math.abs(center.phaseRadians - later.phaseRadians) > .001f)
        assertEquals(0f, edge.angularVelocityRadiansPerSecond, .00001f)
        assertEquals(0f, frozen.phaseRadians, 0f)
        assertEquals(0f, frozen.angularVelocityRadiansPerSecond, 0f)

        val beforeRadius = kotlin.math.hypot(.68f - .5f, .42f - .5f)
        val moved = OrbCoreRotationFieldV1.inverseSample(.68f, .42f, 1_000L, reducedMotion = false)
        val afterRadius = kotlin.math.hypot(moved.first - .5f, moved.second - .5f)
        assertEquals(beforeRadius, afterRadius, .00001f)
        assertTrue(kotlin.math.abs(.68f - moved.first) > .001f)
    }

    @Test fun debugAndReleaseResolveTheSameProductionOpticsProfile() {
        assertEquals(OrbStage02VisualProfile.PRODUCTION_V1, OrbStage02VisualProfile.currentBuildDefault())
        assertTrue(OrbStage02VisualProfile.PRODUCTION_V1.usesProductionOptics())
        val active = OrbResidentFilamentMotionV1.lifecycle(RecognitionSceneStage.ORB_RELEASE, .4f, false)
        assertTrue(OrbResidentTransmissionFieldV1.enabled(active, coreVisible = true))
        assertFalse(OrbResidentTransmissionFieldV1.enabled(active, coreVisible = false))
        assertTrue(OrbResidentTransmissionFieldV1.REAR_PATH_IDS.all { it in OrbResidentFilamentMotionV1.PATH_IDS })
    }
}
