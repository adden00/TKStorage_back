package com.adden00.tk_storage_back.domain

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "auth_sessions")
data class AuthSession(
    @Id val mongoId: String? = null,
    @Indexed(unique = true) val tokenHash: String,
    val keyholderName: String,
    val keyHash: String,
    val createdAt: Instant = Instant.now(),
)
