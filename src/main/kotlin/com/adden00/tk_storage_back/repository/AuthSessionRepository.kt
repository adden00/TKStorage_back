package com.adden00.tk_storage_back.repository

import com.adden00.tk_storage_back.domain.AuthSession
import org.springframework.data.mongodb.repository.MongoRepository

interface AuthSessionRepository : MongoRepository<AuthSession, String> {
    fun findByTokenHash(tokenHash: String): AuthSession?

    fun deleteByTokenHash(tokenHash: String)
}
