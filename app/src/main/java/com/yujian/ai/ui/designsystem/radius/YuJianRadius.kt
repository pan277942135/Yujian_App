package com.yujian.ai.ui.designsystem.radius

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** Compose mapping of design/system/core_visual_v1/tokens/radius_tokens.json. */
object YuJianRadius {
    val small = 16.dp
    val medium = 24.dp
    val large = 36.dp
    val extraLarge = 48.dp

    val heroCard = RoundedCornerShape(extraLarge)
    val glassCard = RoundedCornerShape(large)
    val button = RoundedCornerShape(medium)
    val pill = RoundedCornerShape(medium)
    val avatar: Shape = CircleShape
    val captureButton: Shape = CircleShape
}
