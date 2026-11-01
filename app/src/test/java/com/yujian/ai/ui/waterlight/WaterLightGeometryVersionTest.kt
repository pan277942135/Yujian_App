package com.yujian.ai.ui.waterlight

import com.yujian.ai.ui.identify.calculateRecognitionImageTransform
import com.yujian.ai.ui.waterlight.core.WaterLightGeometryVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WaterLightGeometryVersionTest {
    @Test fun viewportAndSourceCropChangeGeometryWhileContentVersionDoesNot() {
        val portrait = calculateRecognitionImageTransform(390f, 844f, 3024, 4032)
        val same = calculateRecognitionImageTransform(390f, 844f, 3024, 4032)
        val landscape = calculateRecognitionImageTransform(844f, 390f, 3024, 4032)
        assertEquals(WaterLightGeometryVersion.of(portrait), WaterLightGeometryVersion.of(same))
        assertNotEquals(WaterLightGeometryVersion.of(portrait), WaterLightGeometryVersion.of(landscape))
        assertNotEquals(WaterLightGeometryVersion.of(portrait),
            WaterLightGeometryVersion.withHostBounds(WaterLightGeometryVersion.of(portrait), 0f, 0f, 390f, 844f))
    }
}
