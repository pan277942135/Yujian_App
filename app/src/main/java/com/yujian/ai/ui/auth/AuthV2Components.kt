package com.yujian.ai.ui.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillNode
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardActions
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yujian.ai.R
import com.yujian.ai.ui.designsystem.color.YuJianColors
import com.yujian.ai.ui.designsystem.components.YuJianIconAction
import com.yujian.ai.ui.designsystem.components.YuJianIconActionFamily
import com.yujian.ai.ui.designsystem.components.YuJianIconActionTone
import com.yujian.ai.ui.designsystem.components.YuJianTitleOnlyTopBar
import com.yujian.ai.ui.designsystem.typography.YuJianTypography
import kotlinx.coroutines.delay

internal val AuthActiveAccent = YuJianColors.ActiveAccent
internal val AuthBorder = Color(0xFFD5DFE6)
internal val AuthError = Color(0xFFE66B61)

@Composable
internal fun AuthV2Scaffold(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    environmentFraction: Float,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF9FBFA)),
    ) {
        val heightBand = when {
            maxHeight >= 760.dp -> AuthHeightBand.STANDARD
            maxHeight >= 640.dp -> AuthHeightBand.COMPACT
            else -> AuthHeightBand.VERY_COMPACT
        }
        val environmentHeight = when (heightBand) {
            AuthHeightBand.STANDARD -> (maxHeight * environmentFraction).coerceIn(188.dp, 272.dp)
            AuthHeightBand.COMPACT -> (maxHeight * 0.31f).coerceIn(184.dp, 232.dp)
            AuthHeightBand.VERY_COMPACT -> (maxHeight * 0.27f).coerceIn(144.dp, 184.dp)
        }
        val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

        Image(
            painter = painterResource(R.drawable.account_privacy_morning_lake),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(environmentHeight),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(environmentHeight)
                .background(Color.White.copy(alpha = 0.18f)),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp)
                .offset(y = environmentHeight - 76.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xDDF9FBFA),
                            Color(0xFFF9FBFA),
                        ),
                    ),
                ),
        )

        if (!imeVisible && heightBand != AuthHeightBand.VERY_COMPACT) {
            AuthFooterGrass(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(72.dp),
                opacity = if (heightBand == AuthHeightBand.STANDARD) 0.09f else 0.06f,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 24.dp,
                    end = 24.dp,
                    top = environmentHeight - 8.dp,
                    bottom = if (imeVisible || heightBand == AuthHeightBand.VERY_COMPACT) 24.dp else 96.dp,
                ),
        ) {
            YuJianTitleOnlyTopBar(
                title = title,
                modifier = Modifier.fillMaxWidth(),
                horizontalPadding = 0.dp,
                minHeight = 0.dp,
                statusBarInset = false,
                titleStyle = YuJianTypography.pageTitle.copy(
                    color = YuJianColors.DeepLakeBlue,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            Text(
                text = subtitle,
                color = YuJianColors.MistBlueGray,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 20.dp),
            )
            content()
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
internal fun AuthV2Field(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    helperText: String? = null,
    errorText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions(),
    focusRequester: FocusRequester? = null,
    autofillType: AutofillType? = null,
    passwordVisible: Boolean? = null,
    onTogglePasswordVisibility: (() -> Unit)? = null,
) {
    val autofill = LocalAutofill.current
    val autofillTree = LocalAutofillTree.current
    val autofillNode = remember(autofillType) {
        autofillType?.let { type ->
            AutofillNode(autofillTypes = listOf(type), onFill = onValueChange)
        }
    }
    DisposableEffect(autofillTree, autofillNode) {
        autofillNode?.let { autofillTree += it }
        onDispose { autofillNode?.let { autofillTree -= it } }
    }
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(imeVisible, focused) {
        if (imeVisible && focused) {
            delay(80)
            bringIntoViewRequester.bringIntoView()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
            .padding(bottom = if (imeVisible) 24.dp else 0.dp),
    ) {
        Text(
            text = label,
            color = YuJianColors.DeepLakeBlue,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier
                .fillMaxWidth()
                .padding(top = 7.dp)
                .height(56.dp)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onGloballyPositioned { layout ->
                    autofillNode?.boundingBox = layout.boundsInWindow()
                }
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) {
                        autofillNode?.let { node -> autofill?.requestAutofillForNode(node) }
                    } else {
                        autofillNode?.let { node -> autofill?.cancelAutofillForNode(node) }
                    }
                },
            enabled = enabled,
            isError = !errorText.isNullOrBlank(),
            singleLine = true,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            supportingText = (errorText ?: helperText)?.let { message ->
                {
                    Text(
                        text = message,
                        color = if (errorText != null) AuthError else YuJianColors.MistBlueGray,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                }
            },
            textStyle = YuJianTypography.body.copy(fontSize = 15.sp, lineHeight = 20.sp),
            placeholder = {
                Text(
                    text = placeholder,
                    color = YuJianColors.MistBlueGray.copy(alpha = 0.72f),
                    fontSize = 14.sp,
                )
            },
            leadingIcon = leadingIcon?.let { icon ->
                {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = YuJianColors.MistBlueGray,
                        modifier = Modifier.size(20.dp),
                    )
                }
            },
            trailingIcon = if (passwordVisible != null && onTogglePasswordVisibility != null) {
                {
                    YuJianIconAction(
                        icon = if (passwordVisible) {
                            Icons.Rounded.VisibilityOff
                        } else {
                            Icons.Rounded.Visibility
                        },
                        contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                        onClick = onTogglePasswordVisibility,
                        family = YuJianIconActionFamily.CONTEXT,
                        tone = YuJianIconActionTone.ON_LIGHT,
                        enabled = enabled,
                    )
                }
            } else {
                null
            },
            visualTransformation = visualTransformation,
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AuthActiveAccent,
                unfocusedBorderColor = AuthBorder,
                disabledBorderColor = Color(0xFFE3E9E9),
                errorBorderColor = AuthError,
                focusedTextColor = YuJianColors.DeepLakeBlue,
                unfocusedTextColor = YuJianColors.DeepLakeBlue,
                disabledTextColor = YuJianColors.MistBlueGray,
                cursorColor = AuthActiveAccent,
                focusedContainerColor = Color.White.copy(alpha = 0.96f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.94f),
                disabledContainerColor = Color(0xFFF2F5F5),
            ),
        )
    }
}

@Composable
internal fun AuthV2ErrorMessage(
    error: String?,
    modifier: Modifier = Modifier,
) {
    if (!error.isNullOrBlank()) {
        Text(
            text = error,
            color = AuthError,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            modifier = modifier.fillMaxWidth(),
        )
    }
}

private enum class AuthHeightBand { STANDARD, COMPACT, VERY_COMPACT }

@Composable
private fun AuthFooterGrass(modifier: Modifier = Modifier, opacity: Float) {
    Canvas(modifier = modifier) {
        val grass = YuJianColors.MistBlueGray.copy(alpha = opacity)
        val grassLight = YuJianColors.MistBlueGray.copy(alpha = opacity * 0.62f)
        val rock = YuJianColors.DeepLakeBlue.copy(alpha = opacity * 0.45f)
        val w = size.width
        val h = size.height

        fun blade(x0: Float, x1: Float, y1: Float, light: Boolean = false) {
            drawLine(
                color = if (light) grassLight else grass,
                start = Offset(w * x0, h),
                end = Offset(w * x1, h * y1),
                strokeWidth = 2f,
                cap = StrokeCap.Round,
            )
        }

        blade(0.02f, 0.035f, 0.34f)
        blade(0.05f, 0.075f, 0.17f)
        blade(0.08f, 0.09f, 0.42f, true)
        blade(0.12f, 0.15f, 0.26f)
        blade(0.17f, 0.19f, 0.50f, true)
        blade(0.83f, 0.81f, 0.42f, true)
        blade(0.88f, 0.86f, 0.20f)
        blade(0.92f, 0.90f, 0.34f)
        blade(0.96f, 0.94f, 0.15f)
        blade(0.99f, 0.975f, 0.40f, true)

        drawOval(
            color = rock,
            topLeft = Offset(w * 0.08f, h * 0.78f),
            size = androidx.compose.ui.geometry.Size(w * 0.12f, h * 0.20f),
        )
        drawOval(
            color = rock.copy(alpha = 0.75f),
            topLeft = Offset(w * 0.80f, h * 0.83f),
            size = androidx.compose.ui.geometry.Size(w * 0.10f, h * 0.15f),
        )
    }
}
