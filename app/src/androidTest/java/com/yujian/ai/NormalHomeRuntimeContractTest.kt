package com.yujian.ai

import android.graphics.BitmapFactory
import java.security.MessageDigest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NormalHomeRuntimeContractTest {
    @Test
    fun normalHomeRuntimeAssetsArePackagedIndependently() {
        val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
        val root = "normal_home_runtime_v1"
        val manifest = assets.open("$root/config/runtime_manifest.json")
            .bufferedReader().use { JSONObject(it.readText()) }

        assertEquals(root, manifest.getString("asset_root"))
        assertEquals("NORMAL_HOME_RUNTIME_V1.1", manifest.getString("asset_revision"))
        assertEquals(
            "6ab9d3348b4a9a7e77ddca3a06235b4991798a309bd3512cc6fb9ea7aeb1d377",
            manifest.getString("frozen_authority_sha256"),
        )
        val provenance = manifest.getJSONObject("background_provenance")
        assertEquals(
            "design/system/backgrounds/morning_lake_v1/assets/Morning_Lake_Master_V1.png",
            provenance.getString("source_path"),
        )
        assertEquals("5fba741088ea186e898cd3bee5777e35978436f427492e6e6122528ef6aa91d7", provenance.getString("source_sha256"))
        assertEquals("5fba741088ea186e898cd3bee5777e35978436f427492e6e6122528ef6aa91d7", provenance.getString("runtime_sha256"))
        val backgroundOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true; inScaled = false }
        assets.open("$root/static/scene_base.png").use { BitmapFactory.decodeStream(it, null, backgroundOptions) }
        assertEquals(941, backgroundOptions.outWidth)
        assertEquals(1672, backgroundOptions.outHeight)
        val runtimeHash = assets.open("$root/static/scene_base.png").use { stream ->
            MessageDigest.getInstance("SHA-256").digest(stream.readBytes()).joinToString("") { "%02x".format(it) }
        }
        assertEquals(provenance.getString("runtime_sha256"), runtimeHash)

        listOf(
            "static/scene_base.png",
            "camera/camera_button_base.png",
            "camera/camera_gold_rim_mask.png",
            "camera/camera_breath_glow.png",
            "avatar/guest_avatar.png",
            "fish_card/fish_card_gradient.png",
            "fish_card/fish_card_outline.png",
            "fish_card/fish_card_shadow.png",
        ).forEach { relative ->
            assets.open("$root/$relative").use { stream ->
                assertNotNull("Cannot decode $relative", BitmapFactory.decodeStream(stream))
            }
        }
    }
}
