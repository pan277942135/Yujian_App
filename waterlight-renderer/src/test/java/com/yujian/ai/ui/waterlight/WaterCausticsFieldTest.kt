package com.yujian.ai.ui.waterlight

import com.yujian.ai.ui.waterlight.renderer.WaterCausticsField
import org.junit.Assert.assertEquals
import org.junit.Test

/** Reference values from pinned src/caustic-field.ts executed in Node at de856eec. */
class WaterCausticsFieldTest {
    @Test fun pinnedUpstreamNumericalParity() {
        val f = WaterCausticsField
        val rotated = f.rotate(.37, -.91, 1.27)
        assertEquals(.9787657015644379, rotated.first, 1e-12)
        assertEquals(.08377172220429607, rotated.second, 1e-12)
        assertEquals(.10006876121299792, f.octave(.37, -.91, 2.4, 1.0), 1e-12)
        assertEquals(.14501584157371328, f.field(.37, -.91, 2.4), 1e-12)
        val sample = f.sampleCaustic(26.5, 61.5, 220.0, 140.0, 2.4, 1.1, .75,
            WaterCausticsField.Rgb(.95, .80, .60), WaterCausticsField.Rgb(.12, .32, .35), .08)
        assertEquals(.7232812197092454, sample.red, 1e-12)
        assertEquals(.6688855246511299, sample.green, 1e-12)
        assertEquals(.5317112107557969, sample.blue, 1e-12)
        assertEquals(.6992718062557556, sample.alpha, 1e-12)
    }
}
