package com.yujian.ai

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmptyHomeV2RuntimeContractTest {
    @Test
    fun packagedRuntimeMatchesFrozenV2Contracts() {
        val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
        val root = "empty_home_runtime_v2"
        val files = assets.list(root)?.toSet().orEmpty()
        assertTrue(files.containsAll(setOf("static", "dynamic", "camera", "config")))
        val motion = assets.open("$root/config/motion_contract.json").bufferedReader().use { JSONObject(it.readText()) }
        val anchors = assets.open("$root/config/anchor_contract.json").bufferedReader().use { JSONObject(it.readText()) }
        val runtime = assets.open("$root/config/runtime_manifest.json").bufferedReader().use { JSONObject(it.readText()) }

        assertEquals("Empty_Home_Final_Design_V2", runtime.getString("design_version"))
        assertEquals(1080, runtime.getJSONArray("reference_canvas").getInt(0))
        assertEquals(1920, runtime.getJSONArray("reference_canvas").getInt(1))
        val bobber = motion.getJSONObject("bobber")
        assertEquals("y", bobber.getString("axis"))
        assertEquals(3, bobber.getInt("range_reference_px"))
        assertEquals(4600, bobber.getInt("duration_ms"))
        assertEquals(0, bobber.getInt("rotation_deg"))
        assertFalse(bobber.getBoolean("scale_animation"))
        val ripple = motion.getJSONObject("ripple")
        assertEquals(1, ripple.getInt("count"))
        assertEquals(3200, ripple.getInt("duration_ms"))
        assertEquals(1.22, ripple.getDouble("scale_to"), 0.0001)
        assertEquals(0.30, ripple.getDouble("alpha_from"), 0.0001)
        assertEquals(
            anchors.getJSONObject("bobber").getJSONArray("water_contact_reference_px").toString(),
            anchors.getJSONObject("ripple").getJSONArray("center_reference_px").toString(),
        )
    }
}
