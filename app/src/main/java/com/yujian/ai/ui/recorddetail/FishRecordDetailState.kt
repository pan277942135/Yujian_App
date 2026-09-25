package com.yujian.ai.ui.recorddetail

import com.yujian.ai.catches.RemoteCatch

const val FishRecordDetailRoute = "catch/{catchId}"

/** UI state for the permanent memory archive of one catch. */
sealed interface FishRecordDetailUiState {
    data object Loading : FishRecordDetailUiState
    data class Success(val record: RemoteCatch) : FishRecordDetailUiState
    data object Empty : FishRecordDetailUiState
    data class Error(val message: String) : FishRecordDetailUiState
}
