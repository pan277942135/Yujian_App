package com.yujian.ai.catches

/** Normalized measurement and user text written as first-class catch metadata. */
data class CatchSaveMetadata(
    val lengthCm: Double?,
    val weightKg: Double?,
    val location: String?,
    val story: String?,
)

/** Parse result-screen strings before starting any upload or save request. */
object CatchSaveMetadataParser {
    fun parse(lengthText: String, weightText: String, locationText: String, storyText: String): CatchSaveMetadata =
        CatchSaveMetadata(
            lengthCm = parseMeasurement(lengthText, "长度"),
            weightKg = parseMeasurement(weightText, "重量"),
            location = locationText.trim().takeIf(String::isNotEmpty)?.also {
                require(it.codePointCount(0, it.length) <= 512) { "地点不能超过 512 个字符" }
            },
            story = storyText.trim().takeIf(String::isNotEmpty)?.also {
                require(it.codePointCount(0, it.length) <= 4096) { "记忆内容不能超过 4096 个字符" }
            },
        )

    private fun parseMeasurement(raw: String, label: String): Double? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val normalized = when {
            trimmed.count { it == ',' } == 1 && '.' !in trimmed -> trimmed.replace(',', '.')
            ',' in trimmed -> throw IllegalArgumentException("$label格式不正确，请使用一个小数点")
            else -> trimmed
        }
        val number = normalized.toDoubleOrNull()
            ?: throw IllegalArgumentException("$label格式不正确，请重新输入")
        require(number.isFinite() && number > 0.0 && number <= 1000.0) {
            "$label需大于 0，且不能超过 1000"
        }
        return number
    }
}
