package com.adden00.tk_storage_back.config

import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.filter.CorsFilter

@Configuration
class WebConfig {
    @Bean
    fun corsFilter(): FilterRegistrationBean<CorsFilter> {
        val config = CorsConfiguration().apply {
            addAllowedOriginPattern("*")
            allowedMethods = listOf("GET", "POST", "PUT")
            allowedHeaders = listOf("Content-Type", "X-App-Version-Code", "Authorization")
        }
        val source = UrlBasedCorsConfigurationSource().apply { registerCorsConfiguration("/**", config) }
        return FilterRegistrationBean(CorsFilter(source)).apply { order = Ordered.HIGHEST_PRECEDENCE }
    }
}
