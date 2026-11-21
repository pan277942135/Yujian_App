package com.yujian.ai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import com.yujian.ai.ui.adaptive.SafeDrawingInsetsDp
import com.yujian.ai.ui.designsystem.components.YuJianBackCenterTitleTopBar
import com.yujian.ai.ui.identify.NormalizedSourceRect
import com.yujian.ai.ui.imagery.FishPhotoBoundingBox
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealFrame
import com.yujian.ai.ui.recognition.reveal.RecognitionRevealWindowRect
import com.yujian.ai.ui.recognition.result.RecognitionResultGeometryResolver
import com.yujian.ai.ui.theme.MutedInk

/** Immutable inputs for the result page's visual tree. State ownership remains with the screen owner. */
data class RecognitionResultScreenContentModel(
    val title: String,
    val backEnabled: Boolean,
    val safeInsets: SafeDrawingInsetsDp,
    val resultPageScrollable: Boolean,
    val resultContentAlpha: Float,
    val heroBitmap: ImageBitmap?,
    val heroBoundingBox: FishPhotoBoundingBox?,
    val heroPrimaryAmbiguous: Boolean,
    val heroWidthDp: Int,
    val heroHeightDp: Int,
    val heroEvidenceFirst: Boolean,
    val revealFrameState: androidx.compose.runtime.State<RecognitionRevealFrame>,
    val sharedPhotoGeometryReady: Boolean,
    val holdPhotoUntilReady: Boolean,
    val decisionContent: RecognitionResultDecisionContentModel,
    val opticalLayer: RecognitionResultOpticalLayerModel,
    val evidenceExportEnabled: Boolean,
    val evidenceExportStatus: String,
    val evidenceExportError: String?,
)

data class RecognitionResultScreenContentActions(
    val onBack: () -> Unit,
    val onHeroSnapshot: (RecognitionRevealWindowRect, NormalizedSourceRect?, Boolean) -> Unit,
    val decisionContent: RecognitionResultDecisionContentActions,
    val opticalLayer: RecognitionResultOpticalLayerActions,
    val onExportDiagnostics: () -> Unit,
)

/**
 * Presentation-only result page. Handoff decisions, event ownership, save state and evidence
 * receipts stay in the result screen runtime; this module owns the nested Compose layout tree.
 */
@Composable
fun RecognitionResultScreenContent(
    model: RecognitionResultScreenContentModel,
    actions: RecognitionResultScreenContentActions,
) {
    BgContentSurface()
    Column(Modifier.fillMaxSize()) {
        YuJianBackCenterTitleTopBar(
            title = model.title,
            onBack = actions.onBack,
            backEnabled = model.backEnabled,
        )
        Column(
            Modifier.weight(1f)
                .graphicsLayer(alpha = model.resultContentAlpha)
                .widthIn(max = RecognitionResultGeometryResolver.MAX_CONTENT_WIDTH_DP.dp)
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally)
                .padding(start = model.safeInsets.start, end = model.safeInsets.end)
                .then(if (model.resultPageScrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(bottom = 20.dp + model.safeInsets.bottom),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))
            model.heroBitmap?.let { bitmap ->
                ResultHeroViewport(
                    bitmap = bitmap,
                    bbox = model.heroBoundingBox,
                    primaryAmbiguous = model.heroPrimaryAmbiguous,
                    widthDp = model.heroWidthDp,
                    heightDp = model.heroHeightDp,
                    evidenceFirst = model.heroEvidenceFirst,
                    revealFrameState = model.revealFrameState,
                    sharedPhotoGeometryReady = model.sharedPhotoGeometryReady,
                    holdPhotoUntilReady = model.holdPhotoUntilReady,
                    onHeroSnapshot = actions.onHeroSnapshot,
                )
            }
            Spacer(Modifier.height(12.dp))
            RecognitionResultDecisionContent(
                model = model.decisionContent,
                actions = actions.decisionContent,
            )
            if (model.evidenceExportEnabled) {
                EvidenceExportControls(
                    status = model.evidenceExportStatus,
                    error = model.evidenceExportError,
                    onExport = actions.onExportDiagnostics,
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
    RecognitionResultOpticalLayer(
        model = model.opticalLayer,
        actions = actions.opticalLayer,
    )
}

@Composable
private fun ColumnScope.EvidenceExportControls(
    status: String,
    error: String?,
    onExport: () -> Unit,
) {
    androidx.compose.material3.TextButton(
        enabled = status != "EXPORTING",
        onClick = onExport,
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .defaultMinSize(minHeight = 48.dp)
            .testTag("result_export_diagnostics_button"),
    ) {
        androidx.compose.material3.Text(
            if (status == "EXPORTING") "正在导出本次识鱼诊断包…" else "导出本次识鱼诊断包",
        )
    }
    androidx.compose.material3.Text(
        text = when {
            error != null -> "诊断包导出失败：$error（可点击重试）"
            status == "READY" -> "诊断包状态：待导出"
            status == "EXPORTING" -> "诊断包状态：正在冻结快照并打包导出…"
            else -> "诊断包状态：$status"
        },
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .testTag("result_export_diagnostics_status"),
        color = MutedInk,
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
    )
}
