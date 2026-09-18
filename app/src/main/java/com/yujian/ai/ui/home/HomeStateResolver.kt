package com.yujian.ai.ui.home

import com.yujian.ai.catches.RemoteCatch

enum class HomeState { EMPTY, NORMAL }

/** Home is content-driven: authentication never decides which state is shown. */
fun resolveHomeState(fishRecords: List<RemoteCatch>): HomeState =
    if (fishRecords.isEmpty()) HomeState.EMPTY else HomeState.NORMAL
