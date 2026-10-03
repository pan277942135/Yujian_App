package com.yujian.ai.ui.home

import com.yujian.ai.catches.CatchStatistics
import com.yujian.ai.catches.RemoteCatch

/** Keeps the last resolved Home snapshot visible while the same archive refreshes. */
internal data class CatchArchiveState(
    val catches: List<RemoteCatch> = emptyList(),
    val statistics: CatchStatistics = CatchStatistics(),
    val loading: Boolean = false,
    val error: String? = null,
    val resolved: Boolean = false,
    val ownerKey: String? = null,
) {
    fun beginLoad(nextOwnerKey: String): CatchArchiveState =
        if (ownerKey == nextOwnerKey) {
            copy(loading = true, error = null)
        } else {
            CatchArchiveState(loading = true, ownerKey = nextOwnerKey)
        }

    fun failLoad(message: String): CatchArchiveState = copy(loading = false, error = message)
}
