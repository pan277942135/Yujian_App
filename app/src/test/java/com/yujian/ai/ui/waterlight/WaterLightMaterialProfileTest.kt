package com.yujian.ai.ui.waterlight

import com.yujian.ai.ui.waterlight.core.WaterLightHost
import com.yujian.ai.ui.waterlight.core.WaterLightMaterialProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test

class WaterLightMaterialProfileTest {
    @Test fun eachHostHasAnExplicitRoleProfileAndPerimeterAvoidsRejectedEffect01Palette() {
        val profiles = WaterLightHost.entries.map(WaterLightMaterialProfile::forHost)
        assertEquals(WaterLightHost.entries.toSet(), profiles.map { it.host }.toSet())
        assertSame(WaterLightMaterialProfile.ORB_LEGACY, WaterLightMaterialProfile.forHost(WaterLightHost.ORB))
        val perimeter = WaterLightMaterialProfile.PERIMETER
        assertEquals(0xFF2E8B83L, perimeter.primaryColor)
        assertEquals(0xFFD8B36AL, perimeter.secondaryColor)
        assertEquals(0xFFDDFBFF, perimeter.highlightColor)
        assertNotEquals(0xFF0B6E69L, perimeter.primaryColor)
        assertNotEquals(0xFF4DD0C7L, perimeter.secondaryColor)
    }

    @Test fun profilesKeepFiniteBoundedOpacityAndMotionValues() {
        WaterLightHost.entries.map(WaterLightMaterialProfile::forHost).forEach { profile ->
            assertEquals(true, profile.opacity.isFinite() && profile.opacity in 0f..1f)
            assertEquals(true, profile.motionScale.isFinite() && profile.motionScale >= 0f)
            assertEquals(true, profile.coverage.isFinite() && profile.coverage in 0f..1f)
        }
    }
}
