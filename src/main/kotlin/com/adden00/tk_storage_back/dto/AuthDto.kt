package com.adden00.tk_storage_back.dto

data class LoginRequest(
    val keyholderName: String,
    val key: String,
)

data class LoginResponse(
    val success: Boolean,
    val token: String? = null,
    val keyholderName: String? = null,
    val message: String? = null,
)
