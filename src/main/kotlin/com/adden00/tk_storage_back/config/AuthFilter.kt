package com.adden00.tk_storage_back.config

import com.adden00.tk_storage_back.dto.ErrorResponse
import com.adden00.tk_storage_back.service.AuthService
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
@Order(2)
class AuthFilter(
    private val authService: AuthService,
    private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest) =
        request.requestURI == "/health" ||
        request.requestURI == "/auth/login" ||
        request.method == "OPTIONS"

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain
    ) {
        val session = bearerToken(request)?.let(authService::authenticate)
        if (session == null) {
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.characterEncoding = Charsets.UTF_8.name()
            objectMapper.writeValue(response.writer, ErrorResponse(message = "Войдите заново"))
            return
        }
        request.setAttribute(KEYHOLDER_NAME, session.keyholderName)
        chain.doFilter(request, response)
    }

    companion object {
        const val KEYHOLDER_NAME = "keyholderName"

        fun bearerToken(request: HttpServletRequest): String? =
            request.getHeader(HttpHeaders.AUTHORIZATION)
                ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
                ?.substring(7)?.trim()
                ?.takeIf { it.isNotEmpty() }
    }
}
