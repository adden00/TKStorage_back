package com.adden00.tk_storage_back.config

import com.adden00.tk_storage_back.dto.ErrorResponse
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

private const val MIN_VERSION_CODE = 2

@Component
class VersionCheckFilter(private val objectMapper: ObjectMapper) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest) =
        request.requestURI == "/health" || request.method == "OPTIONS"

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain
    ) {
        val version = request.getHeader("X-App-Version-Code")?.toIntOrNull() ?: 0
        if (version < MIN_VERSION_CODE) {
            response.status = HttpServletResponse.SC_FORBIDDEN
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            objectMapper.writeValue(response.writer, ErrorResponse(message = "Пожалуйста обновите приложение!"))
            return
        }
        chain.doFilter(request, response)
    }
}
