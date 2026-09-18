package com.yujian.ai.ui.home

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.yujian.ai.R

/** The one shared Home camera button used by Empty and Normal states. */
@Composable
fun HomeCameraButton(
    cameraScale: Float,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    Box(
        Modifier
            .size(126.dp)
            .graphicsLayer { scaleX = cameraScale; scaleY = cameraScale }
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                onClick()
            },
    ) {
        Image(painterResource(R.drawable.camera_outer_ring), null, Modifier.fillMaxSize())
        Image(painterResource(R.drawable.camera_icon), "开始识鱼", Modifier.size(52.dp).align(Alignment.Center))
    }
}
