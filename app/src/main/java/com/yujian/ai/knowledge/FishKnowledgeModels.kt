package com.yujian.ai.knowledge

import com.yujian.ai.model.DemoData

data class FishGuideItem(
    val id: String,
    val nameCn: String,
    val aliases: List<String> = emptyList(),
    val scientificName: String? = null,
    val category: String = "",
    val summary: String = "",
    val coverImage: String? = null,
    val discovered: Boolean = false,
    val catches: Int = 0,
)

data class FishKnowledgeSpecies(
    val id: String,
    val nameCn: String,
    val aliases: List<String>,
    val scientificName: String?,
    val category: String,
    val family: String?,
    val genus: String?,
    val summary: String,
    val status: String,
    val coverImage: String?,
)

data class FishKnowledgeCover(
    val imageUrl: String,
    val style: String,
    val title: String,
    val status: String,
)

data class FishKnowledgeCard(
    val id: Int,
    val speciesId: String,
    val cardType: String,
    val title: String,
    val imageUrl: String,
    val description: String,
    val sortOrder: Int,
    val status: String,
)

data class FishKnowledgeGalleryImage(
    val id: Int,
    val type: String,
    val url: String,
    val title: String?,
    val order: Int,
)

data class FishKnowledgeProfile(
    val bodyShape: String?,
    val features: List<String>,
    val habitat: List<String>,
    val food: String?,
    val season: List<String>,
)

data class FishKnowledgeFishing(
    val waterLayer: String?,
    val season: List<String>,
    val bait: List<String>,
    val method: List<String>,
    val summary: String,
)

data class FishKnowledgeVideo(
    val id: Int,
    val title: String,
    val type: String,
    val coverUrl: String?,
    val videoUrl: String,
    val duration: Int,
    val tags: List<String>,
)

data class FishKnowledgeSimilarity(
    val similarSpeciesId: String,
    val similarSpeciesNameCn: String,
    val difference: String,
)

data class FishKnowledgeDetail(
    val species: FishKnowledgeSpecies,
    val cover: FishKnowledgeCover?,
    val cards: List<FishKnowledgeCard>,
    val gallery: List<FishKnowledgeGalleryImage>,
    val profile: FishKnowledgeProfile,
    val fishing: FishKnowledgeFishing,
    val videos: List<FishKnowledgeVideo>,
    val similarity: List<FishKnowledgeSimilarity>,
    val dynamicAvailable: Boolean = false,
)

fun FishGuideItem.toFallbackDetail(): FishKnowledgeDetail {
    val local = DemoData.species.firstOrNull { it.key == id }
    val categoryParts = category.split(" · ", limit = 2)
    return FishKnowledgeDetail(
        species = FishKnowledgeSpecies(
            id = id,
            nameCn = nameCn,
            aliases = aliases,
            scientificName = scientificName,
            category = categoryParts.firstOrNull().orEmpty(),
            family = categoryParts.getOrNull(1),
            genus = null,
            summary = summary,
            status = "ACTIVE",
            coverImage = coverImage,
        ),
        cover = null,
        cards = emptyList(),
        gallery = emptyList(),
        profile = FishKnowledgeProfile(
            bodyShape = null,
            features = emptyList(),
            habitat = local?.habitat?.split(" · ").orEmpty(),
            food = local?.diet,
            season = local?.season?.let { listOf(it) }.orEmpty(),
        ),
        fishing = FishKnowledgeFishing(
            waterLayer = null,
            season = emptyList(),
            bait = emptyList(),
            method = emptyList(),
            summary = local?.tip.orEmpty(),
        ),
        videos = emptyList(),
        similarity = emptyList(),
    )
}
