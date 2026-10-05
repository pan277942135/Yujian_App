package com.yujian.ai.ui.mycatches

import com.yujian.ai.catches.RemoteCatch
import com.yujian.ai.presentation.presentationSpeciesName
import com.yujian.ai.presentation.sanitizeOptionalText
import java.text.DecimalFormat
import java.util.Locale

enum class GrowthMarkType {
    CountMilestone,
    FirstSpecies,
    Longest,
    Heaviest,
}

data class GrowthMark(
    val type: GrowthMarkType,
    val text: String,
)

/** Derives factual archive marks from the complete catch history. */
object GrowthMarkResolver {
    private val countMilestones = setOf(10, 50, 100, 500, 1000)

    fun resolve(catches: List<RemoteCatch>): Map<String, List<GrowthMark>> {
        val result = catches.associate { it.id to mutableListOf<GrowthMark>() }.toMutableMap()
        val chronological = catches
            .filter { resolveCatchTimestamp(it).isKnown }
            .sortedWith(compareBy<RemoteCatch> { resolveCatchTimestamp(it).millis ?: Long.MAX_VALUE }.thenBy { it.id })

        chronological.forEachIndexed { index, record ->
            val count = index + 1
            if (count in countMilestones) {
                result.getValue(record.id).add(GrowthMark(GrowthMarkType.CountMilestone, "第${count}条"))
            }
        }

        chronological.groupBy { it.speciesKey() }.values.forEach { records ->
            records.firstOrNull()?.let { first ->
                result.getValue(first.id).add(
                    GrowthMark(GrowthMarkType.FirstSpecies, "首条${presentationSpeciesName(first.speciesName)}"),
                )
            }
        }

        catches.groupBy { it.speciesKey() }.values.forEach { records ->
            val lengths = records.mapNotNull { it.lengthCm?.takeIf { value -> value.isFinite() && value > 0f } }
            lengths.maxOrNull()?.let { longest ->
                records.filter { it.lengthCm == longest }.forEach { record ->
                    result.getValue(record.id).add(GrowthMark(GrowthMarkType.Longest, "最长"))
                }
            }
            val weights = records.mapNotNull { it.weightKg?.takeIf { value -> value.isFinite() && value > 0f } }
            weights.maxOrNull()?.let { heaviest ->
                records.filter { it.weightKg == heaviest }.forEach { record ->
                    result.getValue(record.id).add(GrowthMark(GrowthMarkType.Heaviest, "最重"))
                }
            }
        }
        return result.mapValues { (_, marks) -> marks.toList() }
    }
}

/** The list shows at most one low-weight annotation, with the frozen priority. */
fun List<GrowthMark>.rowGrowthMark(): GrowthMark? {
    firstOrNull { it.type == GrowthMarkType.CountMilestone }?.let { return it }
    firstOrNull { it.type == GrowthMarkType.FirstSpecies }?.let { return it }
    val hasLongest = any { it.type == GrowthMarkType.Longest }
    val hasHeaviest = any { it.type == GrowthMarkType.Heaviest }
    return when {
        hasLongest && hasHeaviest -> GrowthMark(GrowthMarkType.Longest, "最长 · 最重")
        hasLongest -> GrowthMark(GrowthMarkType.Longest, "最长")
        hasHeaviest -> GrowthMark(GrowthMarkType.Heaviest, "最重")
        else -> null
    }
}

fun RemoteCatch.speciesKey(): String = speciesId.ifBlank { speciesName.trim().lowercase(Locale.ROOT) }

data class FishRecordPresentation(
    val id: String,
    val imageUrl: String?,
    val speciesName: String,
    val measurementLabel: String?,
    val location: String?,
    val dateLabel: String,
    val annotations: List<GrowthMark>,
)

fun RemoteCatch.toFishRecordPresentation(
    imageUrl: String?,
    annotations: List<GrowthMark> = emptyList(),
): FishRecordPresentation {
    val timestamp = resolveCatchTimestamp(this)
    return FishRecordPresentation(
        id = id,
        imageUrl = imageUrl,
        speciesName = presentationSpeciesName(speciesName),
        measurementLabel = formatCatchMeasurements(lengthCm, weightKg),
        location = sanitizeOptionalText(location),
        dateLabel = timestamp.dayLabel,
        annotations = annotations,
    )
}

fun formatCatchMeasurements(lengthCm: Float?, weightKg: Float?): String? {
    val parts = buildList {
        lengthCm?.let { add("${formatDecimal(it)} cm") }
        weightKg?.let { add("${formatDecimal(it)} kg") }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

private fun formatDecimal(value: Float): String =
    DecimalFormat("0.##").format(value.toDouble()).replace(',', '.')
