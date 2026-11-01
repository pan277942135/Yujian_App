package com.yujian.ai.ui.waterlight

import com.yujian.ai.ui.waterlight.core.WaterLightHost
import com.yujian.ai.ui.waterlight.core.WaterLightMaterialProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WaterLightMaterialProfileTest {
    @Test fun eachHostHasItsOwnBoundedMaterial() {
        val profiles = WaterLightHost.entries.map(WaterLightMaterialProfile::forHost)
        assertEquals(WaterLightHost.entries.toList(), profiles.map { it.host })
        profiles.forEach { profile ->
            assert(profile.opacity in 0f..1f)
            assert(profile.coreAlpha in 0f..1f)
            assert(profile.glowAlpha in 0f..1f)
            assert(profile.coverage in 0f..1f)
        }
        assertNotEquals(WaterLightMaterialProfile.ORB_LEGACY.primaryColor,
            WaterLightMaterialProfile.PERIMETER.primaryColor)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidAlphaIsRejected() {
        WaterLightMaterialProfile.PATH.copy(glowAlpha = 1.1f)
    }
}
