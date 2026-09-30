package com.yujian.ai.ui.mycatches

enum class CatchTimeRange(val label: String) {
    All("不限"),
    ThisMonth("本月"),
    Last3Months("近3个月"),
    ThisYear("今年"),
    Custom("自定义"),
}

enum class CatchLengthRange(val label: String, val min: Float?, val max: Float?) {
    All("不限", null, null),
    Under20("<20 cm", null, 20f),
    From20To40("20–40 cm", 20f, 40f),
    From40To60("40–60 cm", 40f, 60f),
    AtLeast60("≥60 cm", 60f, null),
}

enum class CatchWeightRange(val label: String, val min: Float?, val max: Float?) {
    All("不限", null, null),
    UnderHalf("<0.5 kg", null, 0.5f),
    FromHalfToOne("0.5–1 kg", 0.5f, 1f),
    FromOneToThree("1–3 kg", 1f, 3f),
    AtLeastThree("≥3 kg", 3f, null),
}

enum class SpecialCatchMark(val label: String) {
    FirstSpecies("首条"),
    Longest("最长"),
    Heaviest("最重"),
    Milestone("里程碑"),
}

data class MyCatchesFilterState(
    val speciesIds: Set<String> = emptySet(),
    val timeRange: CatchTimeRange = CatchTimeRange.All,
    val customStartDate: String? = null,
    val customEndDate: String? = null,
    val lengthRange: CatchLengthRange = CatchLengthRange.All,
    val weightRange: CatchWeightRange = CatchWeightRange.All,
    val customLengthMinCm: Float? = null,
    val customLengthMaxCm: Float? = null,
    val customWeightMinKg: Float? = null,
    val customWeightMaxKg: Float? = null,
    val specialMarks: Set<SpecialCatchMark> = emptySet(),
) {
    val isActive: Boolean
        get() = speciesIds.isNotEmpty() || timeRange != CatchTimeRange.All ||
            lengthRange != CatchLengthRange.All || weightRange != CatchWeightRange.All ||
            customLengthMinCm != null || customLengthMaxCm != null || customWeightMinKg != null || customWeightMaxKg != null ||
            specialMarks.isNotEmpty()

    fun cleared(): MyCatchesFilterState = MyCatchesFilterState()
}

enum class MyCatchesEmptyState {
    Archive,
    Search,
    Filter,
    None,
}

fun resolveMyCatchesEmptyState(
    rawCount: Int,
    query: String,
    filter: MyCatchesFilterState,
    resultCount: Int,
    loading: Boolean = false,
    hasError: Boolean = false,
): MyCatchesEmptyState = when {
    loading || hasError -> MyCatchesEmptyState.None
    rawCount == 0 -> MyCatchesEmptyState.Archive
    resultCount == 0 && query.trim().isNotEmpty() -> MyCatchesEmptyState.Search
    resultCount == 0 && filter.isActive -> MyCatchesEmptyState.Filter
    else -> MyCatchesEmptyState.None
}
