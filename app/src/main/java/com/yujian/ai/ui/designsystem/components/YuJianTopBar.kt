package com.yujian.ai.ui.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.yujian.ai.ui.designsystem.typography.YuJianTypography

data class YuJianTopBarAction(
    val icon: ImageVector,
    val contentDescription: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
)

@Composable
private fun topNavigationInsets(horizontalPadding: Dp, statusBarInset: Boolean): Modifier {
    val safe = WindowInsets.safeDrawing.asPaddingValues()
    val direction = LocalLayoutDirection.current
    return Modifier.padding(
        top = if (statusBarInset) safe.calculateTopPadding() else 0.dp,
        start = horizontalPadding + safe.calculateStartPadding(direction),
        end = horizontalPadding + safe.calculateEndPadding(direction),
    )
}

/** Top Navigation V1 / TITLE_ONLY. */
@Composable
fun YuJianTitleOnlyTopBar(
    title: String,
    modifier: Modifier = Modifier,
    titleStyle: TextStyle = YuJianTypography.pageTitle,
    horizontalPadding: androidx.compose.ui.unit.Dp = 16.dp,
    minHeight: androidx.compose.ui.unit.Dp = 64.dp,
    statusBarInset: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(topNavigationInsets(horizontalPadding, statusBarInset))
            .heightIn(min = minHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = titleStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Top Navigation V1 / BACK_TITLE. */
@Composable
fun YuJianBackTitleTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backEnabled: Boolean = true,
    statusBarInset: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(topNavigationInsets(8.dp, statusBarInset))
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        YuJianBackAction(
            onClick = onBack,
            enabled = backEnabled,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = YuJianTypography.sectionTitle,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Top Navigation V1 / BACK_TITLE_ACTIONS. */
@Composable
fun YuJianBackTitleActionsTopBar(
    title: String,
    onBack: () -> Unit,
    actions: List<YuJianTopBarAction>,
    modifier: Modifier = Modifier,
    backEnabled: Boolean = true,
    onMore: (() -> Unit)? = null,
    statusBarInset: Boolean = true,
) {
    require(actions.isNotEmpty()) {
        "Use YuJianBackTitleTopBar when there are no utility actions."
    }
    require(actions.size <= 2 || onMore != null) {
        "Three or more actions require onMore so the top bar can collapse to two slots."
    }

    val visibleActions = if (actions.size <= 2) {
        actions
    } else {
        listOf(
            actions.first(),
            YuJianTopBarAction(
                icon = Icons.Rounded.MoreHoriz,
                contentDescription = "更多",
                onClick = requireNotNull(onMore),
            ),
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(topNavigationInsets(8.dp, statusBarInset))
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        YuJianBackAction(
            onClick = onBack,
            enabled = backEnabled,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = YuJianTypography.sectionTitle,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            visibleActions.forEach { action ->
                YuJianIconAction(
                    icon = action.icon,
                    contentDescription = action.contentDescription,
                    onClick = action.onClick,
                    family = YuJianIconActionFamily.UTILITY,
                    tone = YuJianIconActionTone.ON_LIGHT,
                    enabled = action.enabled,
                )
            }
        }
    }
}

/**
 * Compatibility wrapper for existing call sites.
 *
 * New code must select one of the three frozen variants above explicitly.
 */
@Deprecated(
    message = "Use YuJianTitleOnlyTopBar, YuJianBackTitleTopBar, or YuJianBackTitleActionsTopBar.",
)
@Composable
fun YuJianTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    statusBarInset: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(topNavigationInsets(if (onBack == null) 16.dp else 8.dp, statusBarInset))
            .heightIn(min = if (onBack == null) 64.dp else 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            YuJianBackAction(onClick = onBack)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = title,
            style = if (onBack == null) {
                YuJianTypography.pageTitle
            } else {
                YuJianTypography.sectionTitle
            },
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = actions,
        )
    }
}
