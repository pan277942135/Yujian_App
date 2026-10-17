package com.yujian.ai.ui.identify

internal enum class RecognitionGuideFishOrientation {
    HORIZONTAL,
}

internal enum class RecognitionGuideRuntimeAssetStatus {
    AVAILABLE,
    ASSET_SOURCE_BLOCKED,
}

internal enum class RecognitionFirstCaptureGuidePage(val index: Int) {
    P01(0),
    P02(1),
    ;

    companion object {
        fun fromIndex(index: Int): RecognitionFirstCaptureGuidePage =
            entries.getOrElse(index.coerceIn(0, entries.lastIndex)) { P01 }
    }
}

internal enum class RecognitionFirstCaptureGuideIntent {
    NEXT,
    PREVIOUS,
    SKIP,
    START,
    BACK,
    CLOSE,
    OUTSIDE_TAP,
}

internal data class RecognitionFirstCaptureGuideState(
    val currentPage: RecognitionFirstCaptureGuidePage = RecognitionFirstCaptureGuidePage.P01,
    val visible: Boolean = true,
    val completed: Boolean = false,
)

internal data class RecognitionFirstCaptureGuideErrorExample(
    val label: String,
    val orientation: RecognitionGuideFishOrientation,
)

internal data class RecognitionFirstCaptureGuidePageSpec(
    val page: RecognitionFirstCaptureGuidePage,
    val phoneOrientationLabel: String,
    val fishOrientation: RecognitionGuideFishOrientation,
    val title: String,
    val subtitle: String,
    val primaryActionLabel: String,
    val heroAsset: String?,
    val errorAssets: List<String?>,
    val runtimeAssetStatus: RecognitionGuideRuntimeAssetStatus,
    val errors: List<RecognitionFirstCaptureGuideErrorExample>,
)

private val captureGuideErrorLabels = listOf("鱼头不在画面", "鱼体过小", "遮挡过多")

internal fun recognitionFirstCaptureGuidePageSpec(
    page: RecognitionFirstCaptureGuidePage,
): RecognitionFirstCaptureGuidePageSpec {
    val fishOrientation = RecognitionGuideFishOrientation.HORIZONTAL
    val phoneOrientationLabel = when (page) {
        RecognitionFirstCaptureGuidePage.P01 -> "竖向"
        RecognitionFirstCaptureGuidePage.P02 -> "横向"
    }
    val title = when (page) {
        RecognitionFirstCaptureGuidePage.P01 -> "竖屏拍摄示意"
        RecognitionFirstCaptureGuidePage.P02 -> "横屏拍摄示意"
    }
    val fishPose = "横向"
    val subtitle = "手机保持$phoneOrientationLabel，整条鱼${fishPose}完整放入取景范围"
    return RecognitionFirstCaptureGuidePageSpec(
        page = page,
        phoneOrientationLabel = phoneOrientationLabel,
        fishOrientation = fishOrientation,
        title = title,
        subtitle = subtitle,
        primaryActionLabel = if (page == RecognitionFirstCaptureGuidePage.P02) "开始拍摄" else "下一张",
        heroAsset = null,
        errorAssets = listOf(null, null, null),
        runtimeAssetStatus = RecognitionGuideRuntimeAssetStatus.ASSET_SOURCE_BLOCKED,
        errors = captureGuideErrorLabels.map { label ->
            RecognitionFirstCaptureGuideErrorExample(label = label, orientation = fishOrientation)
        },
    )
}

internal fun reduceRecognitionFirstCaptureGuideState(
    state: RecognitionFirstCaptureGuideState,
    intent: RecognitionFirstCaptureGuideIntent,
): RecognitionFirstCaptureGuideState {
    if (!state.visible) return state
    return when (intent) {
        RecognitionFirstCaptureGuideIntent.NEXT -> {
            val nextPage = RecognitionFirstCaptureGuidePage.fromIndex(state.currentPage.index + 1)
            state.copy(currentPage = nextPage)
        }
        RecognitionFirstCaptureGuideIntent.PREVIOUS -> {
            val previousPage = RecognitionFirstCaptureGuidePage.fromIndex(state.currentPage.index - 1)
            state.copy(currentPage = previousPage)
        }
        RecognitionFirstCaptureGuideIntent.SKIP -> state.copy(visible = false, completed = true)
        RecognitionFirstCaptureGuideIntent.START -> if (state.currentPage == RecognitionFirstCaptureGuidePage.P02) {
            state.copy(visible = false, completed = true)
        } else {
            state
        }
        RecognitionFirstCaptureGuideIntent.BACK,
        RecognitionFirstCaptureGuideIntent.CLOSE,
        RecognitionFirstCaptureGuideIntent.OUTSIDE_TAP -> state.copy(visible = false)
    }
}

internal fun shouldShowRecognitionFirstCaptureGuide(
    cameraPermissionGranted: Boolean,
    previewReady: Boolean,
    galleryPickerPending: Boolean,
    guideCompleted: Boolean,
    galleryVisitedThisSession: Boolean = false,
): Boolean = cameraPermissionGranted && previewReady && !galleryPickerPending &&
    !galleryVisitedThisSession && !guideCompleted
