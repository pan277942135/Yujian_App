package com.yujian.ai.ui.fishguide

import com.yujian.ai.knowledge.FishGuideItem

/** UI-only projection for the personal natural field guide. */
data class FishGuidePresentationItem(
    val source: FishGuideItem,
    val imageUrl: String?,
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
    FishGuidePresentationItem(item, resolveAssetUrl(item.coverImage))
}

fun List<FishGuideItem>.filterFishGuide(query: String): List<FishGuideItem> {
    val normalized = query.trim()
    if (normalized.isBlank()) return this
    return filter { item ->
        item.nameCn.contains(normalized, ignoreCase = true) ||
            item.aliases.any { alias -> alias.contains(normalized, ignoreCase = true) }
    }
}

fun List<FishGuideItem>.litCount(): Int = count { it.discovered }

fun List<FishGuideItem>.progressFraction(): Float =
    if (isEmpty()) 0f else (litCount().toFloat() / size).coerceIn(0f, 1f)

fun selectionIndex(items: List<FishGuideItem>, selectedId: String?): Int =
    items.indexOfFirst { it.id == selectedId }.takeIf { it >= 0 } ?: 0

fun selectionIdAfterFilter(items: List<FishGuideItem>, selectedId: String?): String? =
    items.firstOrNull { it.id == selectedId }?.id ?: items.firstOrNull()?.id

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
