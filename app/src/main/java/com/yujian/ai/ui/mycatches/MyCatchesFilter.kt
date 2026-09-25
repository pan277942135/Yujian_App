package com.yujian.ai.ui.mycatches

enum class CatchTimeRange(val label: String) {
    All("全部时间"),
    Last7Days("近 7 天"),
    Last30Days("近 30 天"),
    ThisYear("今年"),
}

data class MyCatchesFilterState(
    val speciesIds: Set<String> = emptySet(),
    val locations: Set<String> = emptySet(),
    val timeRange: CatchTimeRange = CatchTimeRange.All,
) {
    val isActive: Boolean
        get() = speciesIds.isNotEmpty() || locations.isNotEmpty() || timeRange != CatchTimeRange.All

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
