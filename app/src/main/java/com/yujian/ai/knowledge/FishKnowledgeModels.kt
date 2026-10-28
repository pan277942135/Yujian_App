package com.yujian.ai.knowledge

data class FishGuideItem(
    val id: String,
    val nameCn: String,
    val aliases: List<String> = emptyList(),
    val scientificName: String? = null,
    val category: String = "",
    val summary: String = "",
    val coverImage: String? = null,
    /** Versioned COVER_HERO response only; never inferred from legacy cover_image. */
    val coverHeroImage: String? = null,
    val coverHeroVersionId: Int? = null,
    val coverHeroStatus: String = "MISSING",
    val discovered: Boolean = false,
    val catches: Int = 0,
    val pinyin: String? = null,
    val pinyinInitials: String? = null,
    val catalogStatus: String = "ACTIVE",
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
    val coverHeroImage: String? = null,
    val coverHeroVersionId: Int? = null,
    val coverHeroStatus: String = "MISSING",
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
    val content: FishKnowledgeCardContent = FishKnowledgeCardContent(),
    val versionId: String? = null,
    val version: String? = null,
)

/** Public CMS asset metadata keyed by role. Status and provenance are retained for auditability. */
data class FishKnowledgeAsset(
    val role: String,
    val imageUrl: String,
    val version: String? = null,
    val versionId: String? = null,
    val resourceId: String? = null,
    val status: String? = null,
    val speciesId: String? = null,
    val source: String = "VERSIONED_KNOWLEDGE_ASSET",
)

data class FishKnowledgeFeature(
    val title: String,
    val text: String,
)

data class FishKnowledgeSimilar(
    val name: String,
    val difference: String,
)

data class FishKnowledgeCardContent(
    val type: String = "",
    val tag: String = "",
    val rarity: Int = 0,
    val power: Int = 0,
    val challenge: Int = 0,
    val description: String = "",
    val features: List<FishKnowledgeFeature> = emptyList(),
    val similar: List<FishKnowledgeSimilar> = emptyList(),
    val habitat: List<String> = emptyList(),
    val waterLayer: String = "",
    val season: String = "",
    val behavior: String = "",
    val diet: String = "",
    val method: String = "",
    val rod: String = "",
    val line: String = "",
    val hook: String = "",
    val bait: List<String> = emptyList(),
    val find: String = "",
    val attract: String = "",
    val action: String = "",
    val tip: String = "",
)

data class FishKnowledgeEcology(
    val habitat: List<String> = emptyList(),
    val waterLayer: String = "",
    val season: String = "",
    val behavior: String = "",
    val diet: String = "",
)

data class FishKnowledgeGear(
    val method: String = "",
    val rod: String = "",
    val line: String = "",
    val hook: String = "",
    val bait: List<String> = emptyList(),
)

data class FishKnowledgeSkill(
    val find: String = "",
    val attract: String = "",
    val action: String = "",
    val tip: String = "",
)

data class FishKnowledgeStructured(
    val displayTag: String? = null,
    val ecology: FishKnowledgeEcology = FishKnowledgeEcology(),
    val gear: FishKnowledgeGear = FishKnowledgeGear(),
    val skill: FishKnowledgeSkill = FishKnowledgeSkill(),
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
    val knowledge: FishKnowledgeStructured = FishKnowledgeStructured(),
    val dynamicAvailable: Boolean = false,
    val coverHeroImage: String? = null,
    val coverHeroVersionId: Int? = null,
    val coverHeroStatus: String = "MISSING",
    val knowledgeAssets: Map<String, FishKnowledgeAsset> = emptyMap(),
    /** True when the API returned the versioned contract, even if it returned no usable roles. */
    val knowledgeAssetsContractPresent: Boolean = false,
)

fun FishGuideItem.toFallbackDetail(): FishKnowledgeDetail {
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
            status = catalogStatus,
            coverImage = coverImage,
            coverHeroImage = coverHeroImage,
            coverHeroVersionId = coverHeroVersionId,
            coverHeroStatus = coverHeroStatus,
        ),
        cover = null,
        cards = emptyList(),
        gallery = emptyList(),
        profile = FishKnowledgeProfile(
            bodyShape = null,
            features = emptyList(),
            habitat = emptyList(),
            food = null,
            season = emptyList(),
        ),
        fishing = FishKnowledgeFishing(
            waterLayer = null,
            season = emptyList(),
            bait = emptyList(),
            method = emptyList(),
            summary = "",
        ),
        videos = emptyList(),
        similarity = emptyList(),
        coverHeroImage = coverHeroImage,
        coverHeroVersionId = coverHeroVersionId,
        coverHeroStatus = coverHeroStatus,
    )
}
