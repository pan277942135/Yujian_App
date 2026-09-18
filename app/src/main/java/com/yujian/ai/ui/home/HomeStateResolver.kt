package com.yujian.ai.ui.home

import com.yujian.ai.catches.CatchStatistics
import com.yujian.ai.catches.RemoteCatch

enum class HomeState { EMPTY, NORMAL }

/** Home is content-driven: authentication never decides which state is shown. */
fun resolveHomeState(statistics: CatchStatistics, catches: List<RemoteCatch>): HomeState =
    if (statistics.totalCatches > 0 || catches.isNotEmpty()) HomeState.NORMAL else HomeState.EMPTY
