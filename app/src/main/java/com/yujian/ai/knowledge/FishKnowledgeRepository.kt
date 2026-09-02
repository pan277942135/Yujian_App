package com.yujian.ai.knowledge

import android.net.Uri
import com.yujian.ai.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class FishKnowledgeRepository(
    private val baseUrl: String = BuildConfig.FISH_KNOWLEDGE_BASE_URL,
) {
    suspend fun listSpecies(): List<FishGuideItem> = withContext(Dispatchers.IO) {
        val array = JSONArray(get("/api/v1/fish/species"))
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            FishGuideItem(
                id = item.optString("id"),
                nameCn = item.optString("name_cn"),
                summary = item.optString("summary"),
                category = item.optString("category"),
                coverImage = item.optString("cover_image").ifBlank { null },
            )
        }.filter { it.id.isNotBlank() && it.nameCn.isNotBlank() }
    }

    suspend fun getDetail(speciesId: String): FishKnowledgeDetail = withContext(Dispatchers.IO) {
        parseDetailJson(get("/api/v1/fish/species/${Uri.encode(speciesId)}/detail"))
    }

    /** Deterministic parser entry used by the Android contract test and previews. */
    fun parseDetailJson(json: String): FishKnowledgeDetail = parseDetail(JSONObject(json))

    fun resolveAssetUrl(asset: String?): String? {
        val value = asset?.trim().orEmpty()
        if (value.isBlank()) return null
        if (value.startsWith("https://") || value.startsWith("http://")) return value
        val base = baseUrl.trimEnd('/')
        if (base.isBlank()) return null
        return if (value.startsWith('/')) "$base$value" else "$base/$value"
    }

    fun isConfigured(): Boolean = baseUrl.isNotBlank()

    private fun get(path: String): String {
        val root = baseUrl.trimEnd('/')
        if (root.isBlank()) throw IOException("Fish Knowledge API 未配置")
        val connection = (URL(root + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
        }
        return try {
            val body = if (connection.responseCode in 200..299) {
                connection.inputStream.use { it.readBytes() }
            } else {
                connection.errorStream?.use { it.readBytes() } ?: ByteArray(0)
            }
            val text = body.toString(Charsets.UTF_8)
            if (connection.responseCode !in 200..299) {
                throw IOException("Fish Knowledge API ${connection.responseCode}: $text")
            }
            text
        } finally {
            connection.disconnect()
        }
    }

    private fun parseDetail(root: JSONObject): FishKnowledgeDetail {
        val speciesJson = root.optJSONObject("species") ?: JSONObject()
        val profileJson = root.optJSONObject("profile") ?: JSONObject()
        val fishingJson = root.optJSONObject("fishing") ?: JSONObject()
        val galleryJson = root.optJSONObject("gallery") ?: JSONObject()
        val coverJson = root.optJSONObject("cover")
        return FishKnowledgeDetail(
            species = FishKnowledgeSpecies(
                id = speciesJson.optString("id"),
                nameCn = speciesJson.optString("name_cn"),
                aliases = stringList(speciesJson.optJSONArray("alias")),
                scientificName = speciesJson.optString("scientific_name").ifBlank { null },
                category = speciesJson.optString("category"),
                family = speciesJson.optString("family").ifBlank { null },
                genus = speciesJson.optString("genus").ifBlank { null },
                summary = speciesJson.optString("summary"),
                status = speciesJson.optString("status"),
                coverImage = speciesJson.optString("cover_image").ifBlank { null },
            ),
            cover = coverJson?.takeIf { it.length() > 0 }?.let {
                FishKnowledgeCover(
                    imageUrl = it.optString("image_url"),
                    style = it.optString("style"),
                    title = it.optString("title"),
                    status = it.optString("status"),
                )
            },
            cards = parseCards(root.optJSONArray("cards")),
            gallery = parseGallery(galleryJson.optJSONArray("images")),
            profile = FishKnowledgeProfile(
                bodyShape = profileJson.optString("body_shape").ifBlank { null },
                features = stringList(profileJson.optJSONArray("features")),
                habitat = stringList(profileJson.optJSONArray("habitat")),
                food = profileJson.optString("food").ifBlank { null },
                season = stringList(profileJson.optJSONArray("season")),
            ),
            fishing = FishKnowledgeFishing(
                waterLayer = fishingJson.optString("water_layer").ifBlank { null },
                season = stringList(fishingJson.optJSONArray("season")),
                bait = stringList(fishingJson.optJSONArray("bait")),
                method = stringList(fishingJson.optJSONArray("method")),
                summary = fishingJson.optString("summary"),
            ),
            videos = parseVideos(root.optJSONArray("videos")),
            similarity = parseSimilarity(root.optJSONArray("similarity")),
            knowledge = parseKnowledge(root.optJSONObject("knowledge")),
            dynamicAvailable = root.optJSONObject("dynamic")?.length()?.let { it > 0 } ?: false,
        )
    }

    private fun parseCards(array: JSONArray?): List<FishKnowledgeCard> = (0 until (array?.length() ?: 0)).map { index ->
        val item = array!!.getJSONObject(index)
        val cardType = item.optString("card_type").ifBlank { item.optString("type") }
        FishKnowledgeCard(
            id = item.optInt("id"),
            speciesId = item.optString("species_id"),
            cardType = normalizeCardType(cardType),
            title = item.optString("title"),
            imageUrl = item.optString("image_url"),
            description = item.optString("description"),
            content = parseCardContent(item.optJSONObject("content"), cardType),
            sortOrder = item.optInt("sort_order"),
            status = item.optString("status"),
        )
    }.sortedBy { it.sortOrder }

    private fun parseCardContent(content: JSONObject?, cardType: String): FishKnowledgeCardContent {
        val item = content ?: JSONObject()
        return FishKnowledgeCardContent(
            type = normalizeCardType(item.optString("type").ifBlank { cardType }),
            tag = item.optString("tag"),
            rarity = item.optInt("rarity"),
            power = item.optInt("power"),
            challenge = item.optInt("challenge"),
            description = item.optString("description"),
            features = parseFeatures(item.optJSONArray("features")),
            similar = parseSimilar(item.optJSONArray("similar")),
            habitat = stringList(item.optJSONArray("habitat")),
            waterLayer = item.optString("water_layer"),
            season = item.optString("season"),
            behavior = item.optString("behavior"),
            diet = item.optString("diet"),
            method = item.optString("method"),
            rod = item.optString("rod"),
            line = item.optString("line"),
            hook = item.optString("hook"),
            bait = stringList(item.optJSONArray("bait")),
            find = item.optString("find"),
            attract = item.optString("attract"),
            action = item.optString("action"),
            tip = item.optString("tip"),
        )
    }

    private fun parseFeatures(array: JSONArray?): List<FishKnowledgeFeature> = (0 until (array?.length() ?: 0)).mapNotNull { index ->
        val item = array?.optJSONObject(index) ?: return@mapNotNull null
        FishKnowledgeFeature(item.optString("title"), item.optString("text"))
    }.filter { it.title.isNotBlank() || it.text.isNotBlank() }

    private fun parseSimilar(array: JSONArray?): List<FishKnowledgeSimilar> = (0 until (array?.length() ?: 0)).mapNotNull { index ->
        val item = array?.optJSONObject(index) ?: return@mapNotNull null
        FishKnowledgeSimilar(item.optString("name"), item.optString("difference"))
    }.filter { it.name.isNotBlank() || it.difference.isNotBlank() }

    private fun parseKnowledge(item: JSONObject?): FishKnowledgeStructured {
        val root = item ?: return FishKnowledgeStructured()
        val ecology = root.optJSONObject("ecology") ?: JSONObject()
        val gear = root.optJSONObject("gear") ?: JSONObject()
        val skill = root.optJSONObject("skill") ?: JSONObject()
        return FishKnowledgeStructured(
            displayTag = root.optString("display_tag").ifBlank { null },
            ecology = FishKnowledgeEcology(
                habitat = stringList(ecology.optJSONArray("habitat")),
                waterLayer = ecology.optString("water_layer"),
                season = ecology.optString("season"),
                behavior = ecology.optString("behavior"),
                diet = ecology.optString("diet"),
            ),
            gear = FishKnowledgeGear(
                method = gear.optString("method"),
                rod = gear.optString("rod"),
                line = gear.optString("line"),
                hook = gear.optString("hook"),
                bait = stringList(gear.optJSONArray("bait")),
            ),
            skill = FishKnowledgeSkill(
                find = skill.optString("find"),
                attract = skill.optString("attract"),
                action = skill.optString("action"),
                tip = skill.optString("tip"),
            ),
        )
    }

    private fun parseGallery(array: JSONArray?): List<FishKnowledgeGalleryImage> = (0 until (array?.length() ?: 0)).map { index ->
        val item = array!!.getJSONObject(index)
        FishKnowledgeGalleryImage(
            id = item.optInt("id"),
            type = item.optString("type"),
            url = item.optString("url"),
            title = item.optString("title").ifBlank { null },
            order = item.optInt("order"),
        )
    }.sortedBy { it.order }

    private fun parseVideos(array: JSONArray?): List<FishKnowledgeVideo> = (0 until (array?.length() ?: 0)).map { index ->
        val item = array!!.getJSONObject(index)
        FishKnowledgeVideo(
            id = item.optInt("id"),
            title = item.optString("title"),
            type = item.optString("type"),
            coverUrl = item.optString("cover_url").ifBlank { null },
            videoUrl = item.optString("video_url"),
            duration = item.optInt("duration"),
            tags = stringList(item.optJSONArray("tags")),
        )
    }.filter { it.videoUrl.isNotBlank() }

    private fun parseSimilarity(array: JSONArray?): List<FishKnowledgeSimilarity> = (0 until (array?.length() ?: 0)).map { index ->
        val item = array!!.getJSONObject(index)
        FishKnowledgeSimilarity(
            similarSpeciesId = item.optString("similar_species_id"),
            similarSpeciesNameCn = item.optString("similar_species_name_cn"),
            difference = item.optString("difference"),
        )
    }

    private fun stringList(array: JSONArray?): List<String> = (0 until (array?.length() ?: 0))
        .mapNotNull { index -> array?.optString(index)?.trim()?.takeIf { it.isNotBlank() } }

    private fun normalizeCardType(value: String): String = when (value.trim().uppercase()) {
        "ECOLOGY" -> "ECO"
        "FISHING" -> "SKILL"
        "RECORD" -> "GEAR"
        else -> value.trim().uppercase()
    }
}
