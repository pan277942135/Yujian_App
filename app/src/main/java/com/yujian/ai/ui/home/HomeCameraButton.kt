package com.yujian.ai.ui.home

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.yujian.ai.R
import com.yujian.ai.ui.components.AssetImage

/** The one shared Home camera button used by Empty and Normal states. */
@Composable
fun HomeCameraButton(
    onClick: () -> Unit,
) {
    val view = LocalView.current
    val transition = rememberInfiniteTransition(label = "home-camera-breath")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(3000), RepeatMode.Reverse),
        label = "camera-scale",
    )
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(tween(3000), RepeatMode.Reverse),
        label = "camera-alpha",
    )
    Box(
        Modifier
            .size(126.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                onClick()
            },
    ) {
        AssetImage(
            "home_empty/camera/camera_outer.png",
            Modifier.fillMaxSize(),
            contentDescription = null,
        )
        AssetImage(
            "home_empty/camera/camera_inner.png",
            Modifier.fillMaxSize(),
            contentDescription = null,
        )
        // camera_icon.svg is the source artwork; this vector is its Android
        // rendering so the button does not introduce a second design.
        Image(painterResource(R.drawable.camera_icon), "开始识鱼", Modifier.size(52.dp).align(Alignment.Center))
    }
}
