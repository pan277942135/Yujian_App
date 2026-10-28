package com.yujian.ai.ui.fishguide

import com.yujian.ai.catches.RemoteCatch
import com.yujian.ai.knowledge.FishGuideItem
import com.yujian.ai.knowledge.FishKnowledgeAsset
import com.yujian.ai.knowledge.FishKnowledgeCard
import com.yujian.ai.knowledge.FishKnowledgeDetail

private val knowledgeCardOrder = listOf("HERO", "IDENTIFICATION", "ECO", "GEAR", "SKILL")
private val knowledgeCardLabels = mapOf(
    "HERO" to "鱼种名片",
    "IDENTIFICATION" to "辨识特征",
    "ECO" to "生态习性",
    "GEAR" to "装备建议",
    "SKILL" to "作钓要点",
)

data class FishGuideKnowledgeFact(
    val label: String,
    val value: String,
)

/** Fixed-position, factual projection for one of the five Species Detail knowledge cards. */
data class FishGuideKnowledgeCardPresentation(
    val position: Int,
    val type: String,
    val label: String,
    val title: String,
    val summary: String?,
    val imageUrl: String?,
    val imageCacheIdentity: String?,
    val imageSource: String,
    val imageStatus: String,
    val facts: List<FishGuideKnowledgeFact>,
    val available: Boolean,
) {
    val pageLabel: String get() = "${position.toString().padStart(2, '0')} / 05"
}

/** UI-only projection for the personal natural field guide. */
data class FishGuidePresentationItem(
    val source: FishGuideItem,
    val imageUrl: String?,
    val imageVersionId: Int? = null,
    val imageStatus: String = "MISSING",
    val imageCacheIdentity: String? = null,
) {
    val id: String get() = source.id
    val name: String get() = source.nameCn
    val aliases: List<String> get() = source.aliases
    val category: String get() = source.category
    val summary: String get() = source.summary
    val discovered: Boolean get() = source.discovered
    val catches: Int get() = source.catches
}

fun List<FishGuideItem>.toFishGuidePresentation(
    resolveAssetUrl: (String?) -> String?,
): List<FishGuidePresentationItem> = map { item ->
    val rawImage = item.coverHeroImage.cleanOrNull()
    val status = item.coverHeroStatus.cleanOrNull()?.uppercase() ?: "MISSING"
    val versionId = item.coverHeroVersionId?.takeIf { it > 0 }
    val eligible = status == "ACTIVE" && rawImage.isUsableImageAddress() && versionId != null
    val resolvedImage = if (eligible) resolveAssetUrl(rawImage)?.trim()?.takeIf { it.isUsableImageAddress() } else null
    val resolvedStatus = when {
        status != "ACTIVE" -> status
        rawImage == null -> "MISSING_URL"
        !rawImage.isUsableImageAddress() -> "INVALID_URL"
        versionId == null -> "MISSING_VERSION"
        resolvedImage == null -> "URL_UNRESOLVED"
        else -> "ACTIVE"
    }
    FishGuidePresentationItem(
        source = item,
        imageUrl = resolvedImage,
        imageVersionId = versionId.takeIf { resolvedImage != null },
        imageStatus = resolvedStatus,
        imageCacheIdentity = resolvedImage?.let { listOf(item.id, "COVER_HERO", versionId, it).joinToString("|") },
    )
}

fun List<FishGuideItem>.litCount(): Int = count { it.discovered }

fun List<FishGuideItem>.progressFraction(): Float =
    if (isEmpty()) 0f else (litCount().toFloat() / size).coerceIn(0f, 1f)

fun selectionIndex(items: List<FishGuideItem>, selectedId: String?): Int =
    items.indexOfFirst { it.id == selectedId }.takeIf { it >= 0 } ?: 0

enum class FishGuideDatasetShape {
    EMPTY,
    SINGLE,
    TWO,
    CAROUSEL,
}

fun List<FishGuideItem>.datasetShape(): FishGuideDatasetShape = when (size) {
    0 -> FishGuideDatasetShape.EMPTY
    1 -> FishGuideDatasetShape.SINGLE
    2 -> FishGuideDatasetShape.TWO
    else -> FishGuideDatasetShape.CAROUSEL
}

fun formatRecordCount(catches: Int): String? = catches.takeIf { it > 0 }?.let { "$it 次记录" }

/** Saved FishRecords are matched by their final stable species ID; name fallback is for legacy rows without an ID. */
fun savedRecordsForSpecies(species: FishGuideItem, records: List<RemoteCatch>): List<RemoteCatch> =
    records.asSequence()
        .filter { it.id.isNotBlank() }
        .filter { record ->
            if (record.speciesId.isNotBlank()) {
                record.speciesId.trim().equals(species.id.trim(), ignoreCase = true)
            } else {
                record.speciesName.trim().equals(species.nameCn.trim(), ignoreCase = true)
            }
        }
        .sortedByDescending { it.capturedAt.ifBlank { it.createdAt } }
        .toList()

fun FishKnowledgeDetail.toKnowledgeCardPresentations(
    resolveAssetUrl: (String?) -> String? = { it },
): List<FishGuideKnowledgeCardPresentation> {
    val activeCards = cards.asSequence()
        .filter { it.status.equals("ACTIVE", ignoreCase = true) }
        .filter { it.speciesId.equals(species.id, ignoreCase = true) }
        .sortedWith(compareBy<FishKnowledgeCard>({ it.sortOrder }, { it.id }, { it.imageUrl }))
        .filter { normalizeKnowledgeCardType(it.cardType) in knowledgeCardOrder }
        .groupBy { normalizeKnowledgeCardType(it.cardType) }
    val useVersionedContract = knowledgeAssetsContractPresent || knowledgeAssets.isNotEmpty()
    val usedImageUrls = mutableSetOf<String>()

    return knowledgeCardOrder.mapIndexed { index, type ->
        val card = activeCards[type].orEmpty().firstOrNull()
        val facts = when (type) {
            "HERO" -> buildList {
                species.scientificName.cleanOrNull()?.let { add(FishGuideKnowledgeFact("学名", it)) }
                species.family.cleanOrNull()?.let { add(FishGuideKnowledgeFact("科", it)) }
                species.genus.cleanOrNull()?.let { add(FishGuideKnowledgeFact("属", it)) }
                species.category.cleanOrNull()?.let { add(FishGuideKnowledgeFact("类别", it)) }
            }
            "IDENTIFICATION" -> identificationFacts(card)
            "ECO" -> ecologyFacts(card)
            "GEAR" -> gearFacts(card)
            "SKILL" -> skillFacts(card)
            else -> emptyList()
        }
        val summary = when (type) {
            "HERO" -> species.summary.cleanOrNull()
                ?: card?.content?.description.cleanOrNull()
                ?: card?.description.cleanOrNull()
            else -> card?.content?.description.cleanOrNull()
                ?: card?.description.cleanOrNull()
        }
        val asset = if (useVersionedContract) {
            knowledgeAssets[type]
        } else {
            card?.let {
                FishKnowledgeAsset(
                    role = type,
                    imageUrl = it.imageUrl,
                    version = it.version,
                    versionId = it.versionId,
                    resourceId = it.id.takeIf { id -> id > 0 }?.toString(),
                    status = it.status,
                    speciesId = it.speciesId,
                    source = "LEGACY_ACTIVE_CARD",
                )
            }
        }
        val isValidAsset = asset?.isUsableFor(species.id, type, requireVersion = useVersionedContract) == true
        val resolvedImage = asset?.takeIf { isValidAsset }
            ?.let { resolveAssetUrl(it.imageUrl)?.trim()?.takeIf { url -> url.isUsableImageAddress() } }
            ?.takeIf { usedImageUrls.add(it) }
        val assetStatus = when {
            asset == null -> if (useVersionedContract) "MISSING" else activeCards[type].orEmpty().firstOrNull()?.status?.uppercase() ?: "MISSING"
            asset.status?.equals("ACTIVE", ignoreCase = true) != true -> asset.status?.uppercase() ?: "MISSING_STATUS"
            asset.speciesId?.equals(species.id, ignoreCase = true) != true -> "SPECIES_MISMATCH"
            normalizeKnowledgeCardType(asset.role) != type -> "ROLE_MISMATCH"
            !asset.imageUrl.isUsableImageAddress() -> "INVALID_URL"
            useVersionedContract && asset.versionId.cleanOrNull() == null && asset.version.cleanOrNull() == null -> "MISSING_VERSION"
            resolvedImage == null -> "URL_UNRESOLVED_OR_DUPLICATE"
            else -> "ACTIVE"
        }
        val cacheIdentity = if (resolvedImage == null || asset == null) null else listOf(
            species.id,
            type,
            asset.source,
            asset.resourceId.cleanOrNull(),
            asset.versionId.cleanOrNull(),
            asset.version.cleanOrNull(),
            resolvedImage,
        ).joinToString("|") { it.orEmpty() }
        val title = if (type == "HERO") species.nameCn else knowledgeCardLabels.getValue(type)
        FishGuideKnowledgeCardPresentation(
            position = index + 1,
            type = type,
            label = knowledgeCardLabels.getValue(type),
            title = title,
            summary = summary,
            imageUrl = resolvedImage,
            imageCacheIdentity = cacheIdentity,
            imageSource = asset?.source ?: if (useVersionedContract) "VERSIONED_KNOWLEDGE_ASSET" else "LEGACY_ACTIVE_CARD",
            imageStatus = assetStatus,
            facts = facts,
            available = resolvedImage != null,
        )
    }
}

private fun FishKnowledgeAsset.isUsableFor(speciesId: String, role: String, requireVersion: Boolean): Boolean =
    normalizeKnowledgeCardType(this.role) == role &&
        status?.equals("ACTIVE", ignoreCase = true) == true &&
        this.speciesId?.equals(speciesId, ignoreCase = true) == true &&
        imageUrl.isUsableImageAddress() &&
        (!requireVersion || versionId.cleanOrNull() != null || version.cleanOrNull() != null)

private fun String?.isUsableImageAddress(): Boolean {
    val value = this.cleanOrNull() ?: return false
    if (value.startsWith("//")) return false
    if (value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true)) return true
    if (Regex("^[A-Za-z][A-Za-z0-9+.-]*:").containsMatchIn(value)) return false
    return true
}

private fun FishKnowledgeDetail.identificationFacts(card: FishKnowledgeCard?): List<FishGuideKnowledgeFact> = buildList {
    val features = card?.content?.features.orEmpty()
        .mapNotNull { feature ->
            val title = feature.title.cleanOrNull()
            val text = feature.text.cleanOrNull()
            if (title == null && text == null) null
            else FishGuideKnowledgeFact(title ?: "辨识特征", text ?: title.orEmpty())
        }
    if (features.isNotEmpty()) {
        addAll(features)
    } else {
        profile.bodyShape.cleanOrNull()?.let { add(FishGuideKnowledgeFact("体形", it)) }
        addAll(profile.features.mapIndexedNotNull { index, feature ->
            feature.cleanOrNull()?.let { FishGuideKnowledgeFact("特征 ${index + 1}", it) }
        })
    }
    val cardSimilar = card?.content?.similar.orEmpty()
        .mapNotNull { similar ->
            val name = similar.name.cleanOrNull()
            val difference = similar.difference.cleanOrNull()
            if (name == null && difference == null) null
            else FishGuideKnowledgeFact(name ?: "相近鱼种", difference ?: name.orEmpty())
        }
    if (cardSimilar.isNotEmpty()) {
        addAll(cardSimilar)
    } else {
        addAll(similarity.mapNotNull { similar ->
            val name = similar.similarSpeciesNameCn.cleanOrNull()
            val difference = similar.difference.cleanOrNull()
            if (name == null && difference == null) null
            else FishGuideKnowledgeFact(name ?: "相近鱼种", difference ?: name.orEmpty())
        })
    }
}

private fun FishKnowledgeDetail.ecologyFacts(card: FishKnowledgeCard?): List<FishGuideKnowledgeFact> {
    val content = card?.content
    val ecology = knowledge.ecology
    val habitat = content?.habitat.orEmpty().cleanKnowledgeValues()
        .ifEmpty { ecology.habitat.cleanKnowledgeValues() }
        .ifEmpty { profile.habitat.cleanKnowledgeValues() }
    val season = content?.season.cleanOrNull() ?: ecology.season.cleanOrNull()
        ?: profile.season.cleanKnowledgeValues().joinToString("、").cleanOrNull()
        ?: fishing.season.cleanKnowledgeValues().joinToString("、").cleanOrNull()
    return buildList {
        habitat.takeIf { it.isNotEmpty() }?.let { add(FishGuideKnowledgeFact("常见水域", it.joinToString("、"))) }
        (content?.waterLayer.cleanOrNull() ?: ecology.waterLayer.cleanOrNull() ?: fishing.waterLayer.cleanOrNull())
            ?.let { add(FishGuideKnowledgeFact("活动水层", it)) }
        season?.let { add(FishGuideKnowledgeFact("活跃季节", it)) }
        (content?.behavior.cleanOrNull() ?: ecology.behavior.cleanOrNull())?.let { add(FishGuideKnowledgeFact("活动习性", it)) }
        (content?.diet.cleanOrNull() ?: ecology.diet.cleanOrNull() ?: profile.food.cleanOrNull())
            ?.let { add(FishGuideKnowledgeFact("食性", it)) }
    }
}

private fun FishKnowledgeDetail.gearFacts(card: FishKnowledgeCard?): List<FishGuideKnowledgeFact> {
    val content = card?.content
    val gear = knowledge.gear
    val method = content?.method.cleanOrNull() ?: gear.method.cleanOrNull()
        ?: fishing.method.cleanKnowledgeValues().joinToString("、").cleanOrNull()
    val bait = content?.bait.orEmpty().cleanKnowledgeValues()
        .ifEmpty { gear.bait.cleanKnowledgeValues() }
        .ifEmpty { fishing.bait.cleanKnowledgeValues() }
    return buildList {
        method?.let { add(FishGuideKnowledgeFact("常用钓法", it)) }
        (content?.rod.cleanOrNull() ?: gear.rod.cleanOrNull())?.let { add(FishGuideKnowledgeFact("鱼竿", it)) }
        (content?.line.cleanOrNull() ?: gear.line.cleanOrNull())?.let { add(FishGuideKnowledgeFact("线组", it)) }
        (content?.hook.cleanOrNull() ?: gear.hook.cleanOrNull())?.let { add(FishGuideKnowledgeFact("钩型", it)) }
        bait.filter(String::isNotBlank).takeIf { it.isNotEmpty() }?.let { add(FishGuideKnowledgeFact("饵料", it.joinToString("、"))) }
    }
}

private fun FishKnowledgeDetail.skillFacts(card: FishKnowledgeCard?): List<FishGuideKnowledgeFact> {
    val content = card?.content
    val skill = knowledge.skill
    return buildList {
        (content?.find.cleanOrNull() ?: skill.find.cleanOrNull())?.let { add(FishGuideKnowledgeFact("找鱼", it)) }
        (content?.attract.cleanOrNull() ?: skill.attract.cleanOrNull())?.let { add(FishGuideKnowledgeFact("诱鱼", it)) }
        (content?.action.cleanOrNull() ?: skill.action.cleanOrNull())?.let { add(FishGuideKnowledgeFact("应对", it)) }
        (content?.tip.cleanOrNull() ?: skill.tip.cleanOrNull())?.let { add(FishGuideKnowledgeFact("提醒", it)) }
    }
}

private fun normalizeKnowledgeCardType(value: String): String = when (value.trim().uppercase()) {
    "ECOLOGY" -> "ECO"
    "FISHING" -> "SKILL"
    else -> value.trim().uppercase()
}

private fun List<String>.cleanKnowledgeValues(): List<String> = mapNotNull { it.cleanOrNull() }

/** Structured backend serialization is data, not copy, even when it arrives in a text field. */
private fun String?.cleanOrNull(): String? = this?.trim()?.takeIf { value ->
    value.isNotEmpty() && !value.startsWith('{') && !value.startsWith('[')
}
