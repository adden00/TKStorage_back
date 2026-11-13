package com.adden00.tk_storage_back.service

import com.adden00.tk_storage_back.domain.AuthSession
import com.adden00.tk_storage_back.repository.AuthSessionRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

data class IssuedSession(val token: String, val keyholderName: String)

@Service
class AuthService(
    private val sessionRepository: AuthSessionRepository,
    @Value("\${app.auth.key:}") private val key: String,
) {
    private val keyHash = sha256(key)
    private val random = SecureRandom()

    init {
        if (key.isBlank()) LoggerFactory.getLogger(javaClass).warn("APP_AUTH_KEY is not set: every login will be rejected")
    }

    fun login(keyholderName: String, key: String): IssuedSession? {
        val name = keyholderName.trim()
        if (this.key.isBlank() || name.isEmpty()) return null
        if (!MessageDigest.isEqual(key.trim().toByteArray(), this.key.toByteArray())) return null
        val token = newToken()
        sessionRepository.save(AuthSession(tokenHash = sha256(token), keyholderName = name, keyHash = keyHash))
        return IssuedSession(token = token, keyholderName = name)
    }

    fun authenticate(token: String): AuthSession? =
        sessionRepository.findByTokenHash(sha256(token))?.takeIf { it.keyHash == keyHash }

    fun logout(token: String) = sessionRepository.deleteByTokenHash(sha256(token))

    private fun newToken(): String {
        val bytes = ByteArray(32).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
