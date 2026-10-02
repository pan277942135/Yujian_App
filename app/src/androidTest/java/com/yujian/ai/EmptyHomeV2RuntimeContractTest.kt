package com.yujian.ai

import android.graphics.BitmapFactory
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
        val authority = assets.open("$root/config/authority_manifest.json").bufferedReader().use { JSONObject(it.readText()) }
        val responsive = assets.open("$root/config/responsive_mapping_contract.json").bufferedReader().use { JSONObject(it.readText()) }
        val runtime = assets.open("$root/config/runtime_manifest.json").bufferedReader().use { JSONObject(it.readText()) }
        val layers = assets.open("$root/config/layer_contract.json").bufferedReader().use { JSONObject(it.readText()) }

        assertEquals("CURRENT", authority.getString("authority_status"))
        assertEquals("V2.3", authority.getString("visual_revision"))
        assertEquals("UNIFORM_COVER", responsive.getJSONObject("groups").getJSONObject("scene_space").getString("strategy"))
        assertEquals(620, responsive.getJSONObject("groups").getJSONObject("hero_copy").getJSONObject("reference_machine_bbox_px").getInt("width"))
        assertEquals("Empty_Home_Final_Design_V2", runtime.getString("design_version"))
        assertEquals("V2.3", runtime.getString("visual_revision"))
        assertEquals("3071481ed7e58106381cdd5321267792491c21fd1a357e4362db1dad8e08e7ec", runtime.getString("approved_visual_sha256"))
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
        assertEquals("below_bobber_above_water", ripple.getString("z_order"))
        assertEquals(
            anchors.getJSONObject("bobber").getJSONArray("water_contact_reference_px").toString(),
            anchors.getJSONObject("ripple").getJSONArray("center_reference_px").toString(),
        )
        assertEquals("[560,1120]", anchors.getJSONObject("bobber").getJSONArray("water_contact_reference_px").toString())
        assertEquals("[548, 1036, 24, 122]", listOf("x", "y", "width", "height").map {
            anchors.getJSONObject("bobber").getJSONObject("bbox_reference_px").getInt(it)
        }.toString())
        assertTrue(anchors.getJSONObject("bobber").getJSONArray("water_contact_reference_px").getInt(1) in 1100..1140)
        assertEquals("[335,1180]", anchors.getJSONObject("line").getJSONArray("start_reference_px").toString())
        assertEquals("[560,1128]", anchors.getJSONObject("line").getJSONArray("end_reference_px").toString())
        assertEquals("[560,1120]", anchors.getJSONObject("ripple").getJSONArray("center_reference_px").toString())
        assertFalse(layers.getJSONArray("order").toString().contains("bobber_underwater"))
        val treatment = motion.getJSONObject("bobber_water_treatment")
        assertEquals("above_water_crop_no_submerged_copy", treatment.getString("mode"))
        assertEquals(0, treatment.getInt("underwater_visible_height_reference_px"))
        assertEquals(0.0, treatment.getDouble("underwater_alpha"), 0.0001)
        assertEquals(220, anchors.getJSONObject("cta").getInt("camera_size_reference_px"))

        assets.open("$root/camera/camera_button_base.png").use { stream ->
            val base = requireNotNull(BitmapFactory.decodeStream(stream))
            assertEquals(208, base.width)
            assertEquals(208, base.height)
            assertEquals(0, base.getPixel(0, 0).ushr(24))
            assertEquals(0, base.getPixel(base.width - 1, 0).ushr(24))
            assertEquals(0, base.getPixel(0, base.height - 1).ushr(24))
            assertEquals(0, base.getPixel(base.width - 1, base.height - 1).ushr(24))
            assertEquals(255, base.getPixel(base.width / 2, base.height / 2).ushr(24))

            val haptic = assets.open("$root/config/haptic_contract.json").bufferedReader().use { JSONObject(it.readText()) }
            assertEquals("none", haptic.getString("page_enter"))
            assertEquals("none", haptic.getString("idle"))
        }
    }
}
