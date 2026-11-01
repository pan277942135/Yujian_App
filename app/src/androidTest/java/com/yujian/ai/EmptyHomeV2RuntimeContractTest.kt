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
    fun packagedRuntimePreservesFrozenAuthorityAndRecordsW5CandidateSeparately() {
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
        val candidate = assets.open("$root/config/w5_runtime_candidate_geometry.json").bufferedReader().use { JSONObject(it.readText()) }

        assertEquals("CURRENT", authority.getString("authority_status"))
        assertEquals("V2.2", authority.getString("visual_revision"))
        assertEquals("UNIFORM_COVER_COMPOSITION_AWARE", responsive.getJSONObject("groups").getJSONObject("scene_space").getString("strategy"))
        assertEquals(620, responsive.getJSONObject("groups").getJSONObject("hero_copy").getJSONObject("reference_machine_bbox_px").getInt("width"))
        assertEquals("Empty_Home_Final_Design_V2", runtime.getString("design_version"))
        assertEquals("V2.2", runtime.getString("visual_revision"))
        assertEquals("3071481ed7e58106381cdd5321267792491c21fd1a357e4362db1dad8e08e7ec", runtime.getString("approved_visual_sha256"))
        assertEquals(1080, runtime.getJSONArray("reference_canvas").getInt(0))
        assertEquals(1920, runtime.getJSONArray("reference_canvas").getInt(1))
        val bobber = motion.getJSONObject("bobber")
        assertEquals("y", bobber.getString("axis"))
        assertEquals("periodic_events_with_quiet_intervals", bobber.getString("cycle_type"))
        assertEquals(36000, bobber.getInt("event_cycle_duration_ms"))
        assertEquals("[1500,6800,13600,22400,28200]", bobber.getJSONArray("event_start_offsets_ms").toString())
        assertEquals(1400, bobber.getInt("motion_duration_ms"))
        assertEquals(4, bobber.getInt("max_initial_dip_reference_px"))
        assertEquals(0, bobber.getInt("rotation_deg"))
        assertFalse(bobber.getBoolean("scale_animation"))
        assertEquals("DISABLED_PENDING_D01", bobber.getString("rotation_status"))
        val ripple = motion.getJSONObject("ripple")
        assertEquals(3, ripple.getInt("count"))
        assertEquals(3600, ripple.getInt("duration_ms"))
        assertEquals(3, ripple.getJSONArray("wave_layers").length())
        assertEquals(36000, ripple.getInt("event_cycle_duration_ms"))
        assertEquals(0, ripple.getInt("phase_offset_ms"))
        assertEquals(5, ripple.getJSONArray("event_start_offsets_ms").length())
        assertEquals("periodic_bobber_motion_event", ripple.getString("trigger"))
        assertEquals("below_bobber_above_water", ripple.getString("z_order"))
        val contact = anchors.getJSONObject("bobber").getJSONArray("water_contact_reference_px")
        val rippleCenter = anchors.getJSONObject("ripple").getJSONArray("center_reference_px")
        assertEquals(1, rippleCenter.getInt(0) - contact.getInt(0))
        assertEquals(-3, rippleCenter.getInt(1) - contact.getInt(1))
        assertEquals("[561,1323]", contact.toString())
        assertEquals("[550, 1250, 24, 78]", listOf("x", "y", "width", "height").map {
            anchors.getJSONObject("bobber").getJSONObject("bbox_reference_px").getInt(it)
        }.toString())
        assertEquals("[337,1184]", anchors.getJSONObject("line").getJSONArray("start_reference_px").toString())
        assertEquals("[560,1326]", anchors.getJSONObject("line").getJSONArray("end_reference_px").toString())
        assertEquals("[562,1320]", anchors.getJSONObject("ripple").getJSONArray("center_reference_px").toString())
        assertEquals("USER_DIRECTED_CANDIDATE_DELTA_NOT_FROZEN", candidate.getString("status"))
        val candidateBobber = candidate.getJSONObject("bobber")
        assertEquals("[531,1161]", candidateBobber.getJSONArray("center_reference_px").toString())
        assertEquals("[531,1195]", candidateBobber.getJSONArray("water_contact_reference_px").toString())
        assertEquals("{\"x\":519,\"y\":1122,\"width\":24,\"height\":78}", candidateBobber.getJSONObject("bbox_reference_px").toString())
        assertEquals("[531,1195]", candidate.getJSONObject("ripple").getJSONArray("center_reference_px").toString())
        assertEquals("[[390,1184],[531,1086]]", candidate.getJSONObject("line").getJSONArray("control_points_reference_px").toString())
        assertEquals("[531,1195]", candidate.getJSONObject("line").getJSONArray("end_reference_px").toString())
        assertFalse(layers.getJSONArray("order").toString().contains("bobber_underwater"))
        val treatment = motion.getJSONObject("bobber_water_treatment")
        assertEquals("single_body_plus_separate_faded_reflection", treatment.getString("mode"))
        assertEquals(0, treatment.getInt("underwater_visible_height_reference_px"))
        assertEquals(0.0, treatment.getDouble("underwater_alpha"), 0.0001)
        assertEquals(0.32, treatment.getDouble("reflection_max_alpha"), 0.0001)
        assertTrue(files.contains("dynamic"))
        val dynamicFiles = assets.list("$root/dynamic")?.toSet().orEmpty()
        assertTrue(dynamicFiles.containsAll(setOf("rod.png", "bobber.png", "bobber_reflection.png")))
        assertEquals(1, layers.getJSONObject("rules").getInt("bobber_body_render_count"))
        assertEquals(3, layers.getJSONObject("rules").getInt("ripple_count"))
        assertEquals(1, layers.getJSONObject("rules").getInt("ripple_group_count"))
        assertEquals(1, layers.getJSONObject("rules").getInt("contact_ring_count"))
        assertEquals(3600, layers.getJSONObject("rules").getInt("ripple_burst_duration_ms"))
        assertEquals("UNCHANGED", layers.getJSONObject("runtime_candidate_geometry").getString("canonical_anchor_contract"))
        assertEquals("[531,1161]", layers.getJSONObject("runtime_candidate_geometry").getJSONArray("candidate_bobber_center_reference_px").toString())
        assertTrue(layers.getJSONObject("rules").getBoolean("reflection_is_separate_asset"))
        assertEquals("single_pale_stroke_without_outline", layers.getJSONObject("rules").getString("line_stroke_model"))
        val fishingBounds = responsive.getJSONObject("groups").getJSONObject("scene_space").getJSONObject("fishing_protected_bounds_reference_px")
        assertEquals(0, fishingBounds.getInt("left"))
        assertEquals(660, fishingBounds.getInt("right"))
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
            val automaticNibble = haptic.getJSONObject("automatic_bobber_nibble")
            assertTrue(automaticNibble.getBoolean("enabled"))
            assertEquals("periodic_bobber_motion_event_start", automaticNibble.getString("trigger"))
            assertEquals(36000, automaticNibble.getInt("event_cycle_duration_ms"))
            assertEquals("one_per_motion_event_id", automaticNibble.getString("dedupe"))
            assertEquals("never_haptic", automaticNibble.getString("initial_render"))
            assertEquals(120, automaticNibble.getInt("max_start_latency_ms"))
            assertEquals(5, automaticNibble.getJSONArray("event_start_offsets_ms").length())
            assertTrue(automaticNibble.getBoolean("requires_lifecycle_resumed"))
            assertTrue(automaticNibble.getBoolean("requires_motion_enabled"))
            val waveform = automaticNibble.getJSONObject("waveform")
            assertEquals("[0,14,45,18]", waveform.getJSONArray("timings_ms").toString())
            assertEquals("[0,90,0,130]", waveform.getJSONArray("amplitudes").toString())
        }
    }
}
