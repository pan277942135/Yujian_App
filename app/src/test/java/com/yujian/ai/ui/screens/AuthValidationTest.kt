package com.yujian.ai.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthValidationTest {
    @Test
    fun username_matches_real_backend_contract() {
        assertTrue(isValidAuthUsername("mumu_123"))
        assertTrue(isValidAuthUsername("abc-123"))
        assertFalse(isValidAuthUsername("ab"))
        assertFalse(isValidAuthUsername("mumu 123"))
        assertFalse(isValidAuthUsername("mumu@123"))
    }

    @Test
    fun register_requires_backend_password_and_nickname_bounds() {
        assertTrue(isValidRegisterForm("mumu123", "123456", "木木"))
        assertFalse(isValidRegisterForm("mumu123", "12345", "木木"))
        assertFalse(isValidRegisterForm("mumu123", "123456", ""))
        assertFalse(isValidRegisterForm("mumu123", "123456", "x".repeat(21)))
    }
}
