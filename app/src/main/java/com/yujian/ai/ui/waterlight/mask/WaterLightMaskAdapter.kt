package com.yujian.ai.ui.waterlight.mask

import com.yujian.ai.ai.subject.MaskGeometryMetadata
import com.yujian.ai.ui.recognition.RecognitionContourSegment

/** Convert the decoder's ROI raster contour into canonical, EXIF-normalized PhotoSpace once. */
object WaterLightMaskAdapter {
    fun toPhotoSpace(metadata: MaskGeometryMetadata, roiContour: List<RecognitionContourSegment>): List<RecognitionContourSegment> {
        val roi = metadata.roiPx
        require(metadata.canonicalWidth > 0 && metadata.canonicalHeight > 0 &&
            roi.width() > 0 && roi.height() > 0 && roi.left >= 0 && roi.top >= 0 &&
            roi.right <= metadata.canonicalWidth && roi.bottom <= metadata.canonicalHeight)
        fun x(value: Float) = (roi.left + value * roi.width()) / metadata.canonicalWidth
        fun y(value: Float) = (roi.top + value * roi.height()) / metadata.canonicalHeight
        return roiContour.map { s ->
            require(listOf(s.startX, s.startY, s.endX, s.endY, s.control1X, s.control1Y,
                s.control2X, s.control2Y).all { it.isFinite() && it in 0f..1f })
            s.copy(startX = x(s.startX), startY = y(s.startY), endX = x(s.endX), endY = y(s.endY),
                control1X = x(s.control1X), control1Y = y(s.control1Y),
                control2X = x(s.control2X), control2Y = y(s.control2Y))
        }
    }
}
