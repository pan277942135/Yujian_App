package com.yujian.ai.ui.designsystem

import androidx.compose.ui.graphics.Color
import com.yujian.ai.ui.designsystem.color.YuJianColors
import com.yujian.ai.ui.designsystem.haptic.YuJianHaptic
import com.yujian.ai.ui.designsystem.motion.YuJianMotion
import com.yujian.ai.ui.designsystem.radius.YuJianRadius
import com.yujian.ai.ui.designsystem.spacing.YuJianSpacing
import com.yujian.ai.ui.designsystem.typography.YuJianTypography
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YuJianDesignTokensTest {
    @Test
    fun coreColorTokensExistAndKeepSemanticContrast() {
        assertEquals(Color(0xFF34556F), YuJianColors.LakeBlue)
        assertEquals(Color(0xFFD6A541), YuJianColors.MorningGold)
        assertEquals(Color(0xFF0B2D4B), YuJianColors.DeepInk)
        assertNotEquals(YuJianColors.LakeBlue, YuJianColors.MorningGold)
        assertNotEquals(YuJianColors.DeepInk, YuJianColors.MistWhite)
        assertEquals(YuJianColors.DeepInk, YuJianColors.TextPrimary)
        assertTrue(YuJianColors.GlassWhite.alpha > YuJianColors.MistWhite.alpha)
        assertTrue(YuJianColors.GlassBorder.alpha > 0f)
    }

    @Test
    fun typographySpacingAndRadiusTokensExist() {
        assertTrue(YuJianTypography.pageTitle.fontSize.value > YuJianTypography.sectionTitle.fontSize.value)
        assertTrue(YuJianTypography.heroTitle.fontSize.value > YuJianTypography.body.fontSize.value)
        assertEquals(8f, YuJianSpacing.xs.value, 0f)
        assertEquals(48f, YuJianSpacing.xxl.value, 0f)
        assertTrue(YuJianRadius.extraLarge.value > YuJianRadius.large.value)
        assertTrue(YuJianRadius.large.value > YuJianRadius.medium.value)
    }

    @Test
    fun captureMotionAndHapticTokensMatchFrozenDesign() {
        assertEquals(3_000, YuJianMotion.CaptureRimSweepFirstDelayMillis)
        assertEquals(1_400, YuJianMotion.CaptureRimSweepDurationMillis)
        assertEquals(9_000, YuJianMotion.CaptureRimSweepRepeatIntervalMillis)
        assertEquals(5_000, YuJianMotion.CaptureBreathingDurationMillis)
        assertTrue(YuJianMotion.CaptureBreathingMaxScale <= 1.015f)
        assertEquals(
            setOf(
                YuJianHaptic.Feedback.Light,
                YuJianHaptic.Feedback.Medium,
                YuJianHaptic.Feedback.Success,
            ),
            YuJianHaptic.Feedback.entries.toSet(),
        )
    }
}
