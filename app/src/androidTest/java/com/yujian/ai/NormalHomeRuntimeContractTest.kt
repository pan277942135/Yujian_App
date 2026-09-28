package com.yujian.ai

import android.graphics.BitmapFactory
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
        assertEquals(
            "6ab9d3348b4a9a7e77ddca3a06235b4991798a309bd3512cc6fb9ea7aeb1d377",
            manifest.getString("frozen_authority_sha256"),
        )

        listOf(
            "static/scene_base.webp",
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
