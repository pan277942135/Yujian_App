package com.yujian.ai.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChangePasswordValidationTest {
    @Test
    fun rejects_empty_current_short_new_mismatch_and_reuse() {
        assertEquals("请输入当前密码", validateChangePassword("", "123456", "123456"))
        assertEquals("新密码至少 6 位", validateChangePassword("old", "12345", "12345"))
        assertEquals("两次新密码不一致", validateChangePassword("old", "123456", "654321"))
        assertEquals("新密码不能与当前密码相同", validateChangePassword("123456", "123456", "123456"))
    }

    @Test
    fun accepts_valid_change() {
        assertNull(validateChangePassword("old-pass", "new-pass", "new-pass"))
    }
}
