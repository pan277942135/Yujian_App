package com.yujian.ai.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.yujian.ai.R

internal val AuthLakeTeal = Color(0xFF0F7A78)
internal val AuthActiveAccent = Color(0xFF168B88)
internal val AuthDeepLake = Color(0xFF12364A)
internal val AuthSecondary = Color(0xFF74879A)
internal val AuthBorder = Color(0xFFD5DFE6)
internal val AuthErrorRed = Color(0xFFB24A3A)

private const val BgContentSaturation = 0.92f
private const val BgContentMistAlpha = 0.15f

/**
 * YuJian Background System V1 / BG_CONTENT.
 *
 * Source: Morning_Lake_Master_V1 (no-sun clean plate).
 * Allowed treatment only: mist veil + lower saturation/contrast + slightly higher luminance.
 * No blur, no scene redesign, no page-local alternate lake asset.
 */
@Composable
internal fun AuthContentBackground(
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.yujian_morning_lake_master_v1),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            colorFilter = ColorFilter.colorMatrix(
                ColorMatrix().apply { setToSaturation(BgContentSaturation) },
            ),
        )
        // Uniform MistWhite veil lowers effective contrast and raises luminance without
        // introducing a second gradient/world. This is the BG_CONTENT treatment.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF7FAFB).copy(alpha = BgContentMistAlpha)),
        )
    }
}

@Composable
internal fun authFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AuthActiveAccent,
    unfocusedBorderColor = AuthBorder,
    disabledBorderColor = AuthBorder.copy(alpha = 0.6f),
    focusedTextColor = AuthDeepLake,
    unfocusedTextColor = AuthDeepLake,
    cursorColor = AuthActiveAccent,
    focusedContainerColor = Color.White.copy(alpha = 0.78f),
    unfocusedContainerColor = Color.White.copy(alpha = 0.74f),
)
