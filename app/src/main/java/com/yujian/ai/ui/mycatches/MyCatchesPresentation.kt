package com.yujian.ai.ui.mycatches

import com.yujian.ai.catches.RemoteCatch
import java.text.DecimalFormat
import java.util.Locale

enum class GrowthMarkType {
    Longest,
    Heaviest,
    FirstSpecies,
    FirstLocation,
}

data class GrowthMark(
    val type: GrowthMarkType,
    val text: String,
)

/**
 * Derives only factual archive marks. It deliberately returns all applicable marks;
 * display priority is not invented here because the frozen source does not define one.
 */
object GrowthMarkResolver {
    fun resolve(catches: List<RemoteCatch>): Map<String, List<GrowthMark>> {
        val result = catches.associate { it.id to mutableListOf<GrowthMark>() }.toMutableMap()
        val chronological = catches.sortedWith(
            compareBy<RemoteCatch> { resolveCatchTimestamp(it).millis }.thenBy { it.id },
        )

        chronological.groupBy { it.speciesId.ifBlank { it.speciesName } }.values.forEach { records ->
            records.firstOrNull()?.let { first ->
                result.getValue(first.id).add(GrowthMark(GrowthMarkType.FirstSpecies, "首条${first.speciesName}"))
            }
        }
        chronological.mapNotNull { record ->
            record.location?.trim()?.takeIf(String::isNotBlank)?.let { it to record }
        }.groupBy({ it.first }, { it.second }).values.forEach { records ->
            records.firstOrNull()?.let { first ->
                result.getValue(first.id).add(GrowthMark(GrowthMarkType.FirstLocation, "首次${first.location}"))
            }
        }
        catches.maxByOrNull { it.lengthCm ?: Float.MIN_VALUE }?.takeIf { it.lengthCm != null }?.let { record ->
            result.getValue(record.id).add(GrowthMark(GrowthMarkType.Longest, "最长记录"))
        }
        catches.maxByOrNull { it.weightKg ?: Float.MIN_VALUE }?.takeIf { it.weightKg != null }?.let { record ->
            result.getValue(record.id).add(GrowthMark(GrowthMarkType.Heaviest, "最大记录"))
        }
        return result.mapValues { it.value.toList() }
    }
}

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
        speciesName = speciesName,
        measurementLabel = formatCatchMeasurements(lengthCm, weightKg),
        location = location?.trim()?.takeIf(String::isNotBlank),
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
