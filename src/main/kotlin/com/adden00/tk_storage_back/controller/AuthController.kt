package com.adden00.tk_storage_back.controller

import com.adden00.tk_storage_back.config.AuthFilter
import com.adden00.tk_storage_back.dto.LoginRequest
import com.adden00.tk_storage_back.dto.LoginResponse
import com.adden00.tk_storage_back.service.AuthService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/auth")
class AuthController(private val authService: AuthService) {

    @PostMapping("/login")
    fun login(@RequestBody body: LoginRequest): ResponseEntity<LoginResponse> {
        val session = authService.login(body.keyholderName, body.key)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(LoginResponse(success = false, message = "Ключ неверный"))
        return ResponseEntity.ok(LoginResponse(success = true, token = session.token, keyholderName = session.keyholderName))
    }

    @PostMapping("/logout")
    fun logout(request: HttpServletRequest): LoginResponse {
        AuthFilter.bearerToken(request)?.let(authService::logout)
        return LoginResponse(success = true)
    }
}
