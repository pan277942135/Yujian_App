package com.yujian.ai.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileEditingContractsTest {
    @Test
    fun nicknameValidationUsesTrimmedUnicodeCodePoints() {
        assertEquals("昵称不能为空", validateNicknameDraft("  \n").error)
        assertEquals("昵称不能超过 20 个字符", validateNicknameDraft("🙂".repeat(21)).error)
        val one = validateNicknameDraft("  🙂  ")
        assertEquals("🙂", one.normalized)
        assertEquals(1, one.codePointCount)
        assertTrue(one.isValid)
        assertTrue(validateNicknameDraft("🙂".repeat(20)).isValid)
    }

    @Test
    fun saveRequiresAValidEffectiveDraftOrPendingAvatar() {
        val unchanged = validateNicknameDraft("same")
        assertFalse(isProfileSaveEnabled("same", unchanged, hasPendingAvatar = false, saving = false))
        assertTrue(isProfileSaveEnabled("same", unchanged, hasPendingAvatar = true, saving = false))
        assertFalse(isProfileSaveEnabled("same", validateNicknameDraft(""), hasPendingAvatar = true, saving = false))
        assertFalse(isProfileSaveEnabled("old", validateNicknameDraft("new"), hasPendingAvatar = false, saving = true))
        assertTrue(isProfileSaveEnabled("old", validateNicknameDraft("new"), hasPendingAvatar = false, saving = false))
    }

    @Test
    fun cropTransformClampsScaleAndKeepsImageCoveringViewport() {
        val maxed = updateAvatarCropTransform(
            current = AvatarCropTransform(scale = 2.9f, offsetX = 20f, offsetY = 0f),
            centroidX = 150f,
            centroidY = 150f,
            panX = 500f,
            panY = 500f,
            zoomChange = 2f,
            bitmapWidth = 1200,
            bitmapHeight = 800,
            viewportWidth = 300f,
            viewportHeight = 300f,
        )
        assertEquals(3f, maxed.scale)
        val rect = avatarCropSourceRect(1200, 800, 300f, 300f, maxed)
        assertTrue(rect.side > 0)
        assertTrue(rect.left >= 0 && rect.top >= 0)
        assertTrue(rect.left + rect.side <= 1200)
        assertTrue(rect.top + rect.side <= 800)
    }

    @Test
    fun portraitAndSquareImagesProduceBoundedSquareCrop() {
        val portrait = avatarCropSourceRect(800, 1600, 300f, 300f, AvatarCropTransform())
        val square = avatarCropSourceRect(1000, 1000, 300f, 300f, AvatarCropTransform())
        assertEquals(800, portrait.side)
        assertEquals(0, portrait.top)
        assertEquals(1000, square.side)
    }
}
