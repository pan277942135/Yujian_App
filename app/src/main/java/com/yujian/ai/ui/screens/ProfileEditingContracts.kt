package com.yujian.ai.ui.screens

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

data class AvatarCropTransform(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
)

data class AvatarCropSourceRect(
    val left: Int,
    val top: Int,
    val side: Int,
)

data class NicknameDraftValidation(
    val normalized: String,
    val codePointCount: Int,
    val error: String?,
) {
    val isValid: Boolean get() = error == null
}

fun validateNicknameDraft(value: String): NicknameDraftValidation {
    val normalized = value.trim()
    val count = normalized.codePointCount(0, normalized.length)
    val error = when {
        count == 0 -> "昵称不能为空"
        count > 20 -> "昵称不能超过 20 个字符"
        else -> null
    }
    return NicknameDraftValidation(normalized, count, error)
}

fun isProfileSaveEnabled(
    serverNickname: String,
    draft: NicknameDraftValidation,
    hasPendingAvatar: Boolean,
    saving: Boolean,
): Boolean = !saving && draft.isValid &&
    (draft.normalized != serverNickname.trim() || hasPendingAvatar)

fun updateAvatarCropTransform(
    current: AvatarCropTransform,
    centroidX: Float,
    centroidY: Float,
    panX: Float,
    panY: Float,
    zoomChange: Float,
    bitmapWidth: Int,
    bitmapHeight: Int,
    viewportWidth: Float,
    viewportHeight: Float,
): AvatarCropTransform {
    if (bitmapWidth <= 0 || bitmapHeight <= 0 || viewportWidth <= 0f || viewportHeight <= 0f) return current
    val nextScale = (current.scale * zoomChange).coerceIn(1f, 3f)
    val factor = nextScale / current.scale
    val centerX = viewportWidth / 2f
    val centerY = viewportHeight / 2f
    val focusX = centroidX - centerX
    val focusY = centroidY - centerY
    val proposedX = focusX * (1f - factor) + current.offsetX * factor + panX
    val proposedY = focusY * (1f - factor) + current.offsetY * factor + panY
    val baseScale = max(viewportWidth / bitmapWidth, viewportHeight / bitmapHeight)
    val drawnWidth = bitmapWidth * baseScale * nextScale
    val drawnHeight = bitmapHeight * baseScale * nextScale
    val maxOffsetX = ((drawnWidth - viewportWidth) / 2f).coerceAtLeast(0f)
    val maxOffsetY = ((drawnHeight - viewportHeight) / 2f).coerceAtLeast(0f)
    return AvatarCropTransform(
        scale = nextScale,
        offsetX = proposedX.coerceIn(-maxOffsetX, maxOffsetX),
        offsetY = proposedY.coerceIn(-maxOffsetY, maxOffsetY),
    )
}

fun avatarCropSourceRect(
    bitmapWidth: Int,
    bitmapHeight: Int,
    viewportWidth: Float,
    viewportHeight: Float,
    transform: AvatarCropTransform,
): AvatarCropSourceRect {
    require(bitmapWidth > 0 && bitmapHeight > 0)
    require(viewportWidth > 0f && viewportHeight > 0f)
    val scale = max(viewportWidth / bitmapWidth, viewportHeight / bitmapHeight) * transform.scale.coerceIn(1f, 3f)
    val drawnWidth = bitmapWidth * scale
    val drawnHeight = bitmapHeight * scale
    val left = (viewportWidth - drawnWidth) / 2f + transform.offsetX
    val top = (viewportHeight - drawnHeight) / 2f + transform.offsetY
    val side = ceil(minOf(viewportWidth, viewportHeight) / scale).toInt()
        .coerceAtMost(minOf(bitmapWidth, bitmapHeight))
        .coerceAtLeast(1)
    val sourceLeft = floor(-left / scale).toInt().coerceIn(0, bitmapWidth - side)
    val sourceTop = floor(-top / scale).toInt().coerceIn(0, bitmapHeight - side)
    return AvatarCropSourceRect(sourceLeft, sourceTop, side)
}
