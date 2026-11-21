package com.yujian.ai.ui.designsystem.lakeglass

import org.json.JSONObject

internal data class LakeGlassManifestLayer(
    val layerId: String,
    val assetPath: String,
    val assetSha256: String,
    val separationStatus: String,
    val pixelBounds: FloatArray,
    val anchorPx: FloatArray,
    val format: String,
    val dimensionsPx: IntArray,
)

internal data class LakeGlassSceneManifest(
    val manifestId: String,
    val sourceSha256: String,
    val masterWidth: Int,
    val masterHeight: Int,
    val visibleRuntimeStack: List<String>,
    val staticLayer: LakeGlassManifestLayer,
    val fallbackLayer: LakeGlassManifestLayer,
    val causticsLayer: LakeGlassManifestLayer,
    val sunlightLayer: LakeGlassManifestLayer,
    val opticalMaskLayer: LakeGlassManifestLayer,
    val dropletLayers: List<LakeGlassManifestLayer>,
    val api33EffectsAreOptional: Boolean,
) {
    companion object {
        const val ASSET_PATH = "lake_glass_v2/assets/android_runtime/layer_manifest_v1.json"
        const val STATIC_LAYER_ID = "LAYER_STATIC_COMPOSITE"
        const val FALLBACK_LAYER_ID = "LAYER_MASTER_FALLBACK"
        const val MASK_LAYER_ID = "LAYER_CAUSTICS_MODULATION"

        fun parse(text: String): LakeGlassSceneManifest {
            val root = JSONObject(text)
            require(root.getString("manifestId") == "LAKE_GLASS_V2_ANDROID_RUNTIME_LAYER_MANIFEST_V1")
            val master = root.getJSONObject("master")
            val sourceHash = master.getString("sourceSha256")
            val masterDimensions = master.getJSONArray("dimensionsPx")
            val visible = root.getJSONArray("visibleRuntimeStack").let { array ->
                (0 until array.length()).map(array::getString)
            }
            val layers = root.getJSONArray("layers")
            val parsed = (0 until layers.length()).map { parseLayer(layers.getJSONObject(it)) }
            fun required(id: String) = requireNotNull(parsed.firstOrNull { it.layerId == id }) {
                "Missing canonical Lake Glass layer $id"
            }
            val static = required(STATIC_LAYER_ID)
            require(visible == listOf(STATIC_LAYER_ID)) { "Only the static composite may be visibly stacked" }
            require(static.separationStatus == "STATIC_COMPOSITE_ONLY")
            val droplets = parsed.filter { it.layerId.startsWith("LAYER_DROPLET_") }
                .sortedBy { it.layerId }
            require(droplets.size == 3 && droplets.all { it.separationStatus == "SHADER_INPUTS_PROVISIONAL" })
            val mask = required(MASK_LAYER_ID)
            val sunlight = required("LAYER_SUNLIGHT_MODULATION")
            val caustics = mask
            require(mask.assetSha256 == sunlight.assetSha256)
            require(droplets.all { it.assetSha256 == mask.assetSha256 })
            return LakeGlassSceneManifest(
                manifestId = root.getString("manifestId"),
                sourceSha256 = sourceHash,
                masterWidth = masterDimensions.getInt(0),
                masterHeight = masterDimensions.getInt(1),
                visibleRuntimeStack = visible,
                staticLayer = static,
                fallbackLayer = required(FALLBACK_LAYER_ID),
                causticsLayer = caustics,
                sunlightLayer = sunlight,
                opticalMaskLayer = mask,
                dropletLayers = droplets,
                api33EffectsAreOptional = root.getJSONObject("api33plus").getString("mode")
                    == "OPTIONAL_AGSL_IN_PLACE_MODULATION",
            )
        }

        private fun parseLayer(layer: JSONObject): LakeGlassManifestLayer {
            fun floatArray(name: String): FloatArray {
                val array = layer.getJSONArray(name)
                return FloatArray(array.length()) { index -> array.getDouble(index).toFloat() }
            }
            fun intArray(name: String): IntArray {
                val array = layer.getJSONArray(name)
                return IntArray(array.length()) { index -> array.getInt(index) }
            }
            return LakeGlassManifestLayer(
                layerId = layer.getString("layerId"),
                assetPath = layer.getString("assetFile"),
                assetSha256 = layer.getString("assetSha256"),
                separationStatus = layer.getString("separationStatus"),
                pixelBounds = floatArray("pixelBounds"),
                anchorPx = floatArray("anchorPx"),
                format = layer.optString("format", "PNG"),
                dimensionsPx = intArray("dimensionsPx"),
            )
        }
    }
}
