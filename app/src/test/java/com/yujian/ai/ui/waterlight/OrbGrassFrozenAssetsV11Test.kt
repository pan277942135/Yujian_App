package com.yujian.ai.ui.waterlight

import com.yujian.ai.ui.waterlight.renderer.OrbGrassMotionProfileV11
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import javax.imageio.ImageIO

class OrbGrassFrozenAssetsV11Test {
    private val expected = linkedMapOf(
        "ORB_GRASS_DERIVED_MASK_PATH_V1_1.json" to "f16a55c960b39f499fa8e3283fbe56dc3d53b0671f65e7f183caf7ecafbd313b",
        "ORB_GRASS_DERIVED_PATHS_V1_1.svg" to "f0cfadf977b10fa122f61bfbc55a8d0368dd859dd94173468d5a9dae36178440",
        "orb_grass_back_b1_mask_v1_1.png" to "d172b189061cf0e7811d249719202b180ad18ee0c48caca591f5477dac143c43",
        "orb_grass_back_b2_mask_v1_1.png" to "3f03448815d2a34c777ca27ae3732f958286b54ca606280b9c8c0f78f55a4d22",
        "orb_grass_front_f1_mask_v1_1.png" to "52b9885939a1fbb02717c3832e74acbeb02949732022b9145e97423c028515d0",
        "orb_grass_front_f2_mask_v1_1.png" to "9a6708996207d3b855d17a8d0010b1572133142c3d7951f8b0687786c31a5fee",
        "orb_grass_back_combined_mask_v1_1.png" to "38921cbe9ee5de3b3d0964366af05a334ce1beb13fd035cc4fe13629a3560142",
        "orb_grass_front_combined_mask_v1_1.png" to "1c46d4a1f6c045c17dc1f48e3c5889a22a4fb581e216874963904094b6d8551d",
    )

    @Test fun allSixMasksAndTheSourceDerivedPathFilesMatchTheFrozenHashes() {
        val root = moduleFile("src/main/assets/orb_r01b_v1_1")
        expected.forEach { (name, hash) ->
            val file = File(root, name)
            assertTrue("missing frozen resource $name", file.isFile)
            assertEquals(name, hash, sha256(file))
        }
        val manifest = File(root, "SHA256SUMS.txt").readLines().filter(String::isNotBlank)
        assertEquals(expected.size, manifest.size)
        assertEquals(expected.size, manifest.map { it.substringAfter("  ") }.toSet().size)
    }

    @Test fun masksAreComplete1254GrayscaleSourceDerivedFiles() {
        val root = moduleFile("src/main/assets/orb_r01b_v1_1")
        val maskNames = expected.keys.filter { it.endsWith(".png") }
        assertEquals(6, maskNames.size)
        maskNames.forEach { name ->
            val image = ImageIO.read(File(root, name))
            assertEquals("$name width", 1254, image.width)
            assertEquals("$name height", 1254, image.height)
            assertEquals("$name must remain grayscale", 1, image.raster.numBands)
        }
        val mapping = File(root, "orb_grass_runtime_mapping_v1_1.json").readText()
        assertTrue(mapping.contains("\"maxMaskOutsideSourceAlphaPx\": 0"))
        assertTrue(mapping.contains("\"crop\": \"none\""))
        assertTrue(mapping.contains("\"u_runtime=u_source\""))
    }

    @Test fun sourceAndAndroidRuntimeCoreArePinnedAndTheOrbDrawsOneBakedTexture() {
        val runtime = moduleFile("src/main/res/drawable-nodpi/processing_water_core_v1.webp")
        assertEquals("daa85a1fc13fac4a7ca562959c2d19172ee0f1e94277c3eeb32ef1f09a1a7b09", sha256(runtime))
        val mapping = File(moduleFile("src/main/assets/orb_r01b_v1_1"), "orb_grass_runtime_mapping_v1_1.json").readText()
        assertTrue(mapping.contains("2773e4628ff6132605314693742488542c3e70fd04b581d8825dd13cea0067ee"))
        assertTrue(mapping.contains("daa85a1fc13fac4a7ca562959c2d19172ee0f1e94277c3eeb32ef1f09a1a7b09"))

        val core = moduleFile("src/main/java/com/yujian/ai/ui/designsystem/components/aiorb/AIOrbCore.kt").readText()
        assertEquals(1, Regex("painterResource\\(R\\.drawable\\.processing_water_core_v1\\)").findAll(core).count())
        assertFalse("The old synthetic grass sheet would duplicate baked leaves", core.contains("WaterGrassField"))
        assertTrue(core.contains("coreOpticalEffect?.renderEffect?.let { renderEffect = it }"))
        val effects = moduleFile("src/main/java/com/yujian/ai/ui/waterlight/renderer/OrbFluidRefractionProgramV1.kt").readText()
        assertTrue(effects.contains("createChainEffect("))
        assertTrue(effects.contains("grass.androidRenderEffect,"))
        assertTrue(effects.contains("fluid.androidRenderEffect,"))
        assertTrue(effects.contains("OrbGrassWarpProgramV11.create(context)"))
        val shader = File(moduleFile("src/main/assets/orb_r01b_v1_1"), "orb_grass_warp_v1_1.agsl").readText()
        assertTrue(shader.contains("inputImage.eval(coord)"))
        assertTrue(shader.contains("inputImage.eval(displacedCoord)"))
        assertTrue(shader.contains("pow(smoothstep(0.10, 1.0, s), 1.6)"))
        assertFalse(shader.contains("R01_grass_back.svg"))
        assertFalse(shader.contains("R01_grass_front.svg"))
    }

    @Test fun theRuntimeMappingKeepsFullSourceUvAtCompactNormalAndCameraSizes() {
        assertEquals(384f / 1254f, OrbGrassMotionProfileV11.SOURCE_TO_RUNTIME_SCALE, .000001f)
        for (diameterDp in listOf(68f, 80f, 74f)) {
            val imageSideDp = diameterDp / .84f
            for (leaf in OrbGrassMotionProfileV11.leaves) {
                val rootPx = leaf.rootU * imageSideDp
                val tipPx = leaf.tipU * imageSideDp
                assertTrue(rootPx >= 0f && rootPx <= imageSideDp)
                assertTrue(tipPx >= 0f && tipPx <= imageSideDp)
                assertTrue(leaf.amplitudeD * diameterDp <= .03f * diameterDp + .0001f)
            }
        }
    }

    @Test fun debugAndReleaseResolveTheSameCandidateRenderer() {
        val core = moduleFile("src/main/java/com/yujian/ai/ui/designsystem/components/aiorb/AIOrbCore.kt").readText()
        val host = moduleFile("src/main/java/com/yujian/ai/ui/recognition/ProcessingWaterOrb.kt").readText()
        assertTrue(core.contains("orbRendererMode: OrbFilamentRendererMode = OrbFilamentRendererMode.ORB_V1_CANDIDATE"))
        assertTrue(host.contains("rendererMode ?: OrbFilamentRendererMode.ORB_V1_CANDIDATE"))
        assertFalse(core.contains("BuildConfig.DEBUG"))
        assertFalse(host.contains("BuildConfig.DEBUG"))
    }

    private fun moduleFile(relative: String): File {
        val candidate = listOf(File(relative), File("app/$relative"), File("../$relative"), File("../app/$relative"))
            .firstOrNull(File::exists)
        return requireNotNull(candidate) { "Could not resolve repository file $relative from ${System.getProperty("user.dir")}" }
    }

    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
        .joinToString("") { "%02x".format(it) }
}
