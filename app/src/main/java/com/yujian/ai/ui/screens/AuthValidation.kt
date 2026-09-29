package com.yujian.ai.ui.screens

private val USERNAME_PATTERN = Regex("^[A-Za-z0-9_-]{3,32}$")

fun isValidAuthUsername(value: String): Boolean = USERNAME_PATTERN.matches(value.trim())

fun isValidRegisterForm(username: String, password: String, nickname: String): Boolean =
    isValidAuthUsername(username) && password.length in 6..72 && nickname.trim().length in 1..20
